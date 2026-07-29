//
// In-game screen recorder, plugged into the EGL rendering bridge.
//
// The game renders into an EGLSurface backed by the MinecraftGLSurface's ANativeWindow.
// To record, the Java side hands us the input Surface of a MediaCodec H.264 encoder; we wrap
// it into a second EGLSurface and, once per captured frame, blit the game's back buffer into
// it before the game presents it. Everything GPU-side, no readback, no extra copy on the CPU.
//
// The blit runs in a dedicated EGL context that shares objects with the game context, so none
// of the game's GL state (bound framebuffers, programs, scissor, ...) is ever disturbed.
//

#include <GLES3/gl3.h>
#include <android/native_window.h>
#include <android/native_window_jni.h>
#include <dlfcn.h>
#include <errno.h>
#include <jni.h>
#include <pthread.h>
#include <stdatomic.h>
#include <stdbool.h>
#include <stdint.h>
#include <time.h>

#include "gl_recorder.h"
#include "egl_loader.h"

#define TAG "GLRecorder"
#include <log.h>

/* How long the caller of nativeStopRecording() waits for the render thread to let go of the
 * encoder surface before giving up on an orderly teardown. */
#define RECORDER_STOP_TIMEOUT_NS 2000000000LL
/* Anything smaller than this is not a real game surface (the bridge falls back to a 1x1
 * pbuffer while the window is gone), so those frames are dropped instead of recorded. */
#define RECORDER_MIN_SURFACE_SIZE 16

enum {
    RECORDER_IDLE = 0,     /* not recording, nothing allocated */
    RECORDER_PENDING = 1,  /* start requested, EGL objects not created yet */
    RECORDER_ACTIVE = 2,   /* capturing */
    RECORDER_STOPPING = 3  /* stop requested, EGL objects not released yet */
};

static atomic_int recorder_state = RECORDER_IDLE;

static pthread_mutex_t recorder_mutex = PTHREAD_MUTEX_INITIALIZER;
static pthread_cond_t recorder_cond = PTHREAD_COND_INITIALIZER;

/* Written by the requesting thread, read by the render thread. Both under recorder_mutex. */
static ANativeWindow* recorder_window;
static int recorder_width;
static int recorder_height;
static int64_t recorder_frame_interval_ns;

/**
 * Timestamp the video track is relative to. Handed over by the Java side rather than taken
 * here, because the audio track has to be laid out against the very same origin and the first
 * frame may only get presented a while after the recording was asked for.
 */
static int64_t recorder_start_ns;

/* Render thread only, published while holding recorder_mutex during setup/teardown. */
static EGLSurface recorder_surface = EGL_NO_SURFACE;
static EGLContext recorder_context = EGL_NO_CONTEXT;
static int64_t recorder_next_frame_ns;

typedef int64_t EGLnsecs;

static void (*glBindFramebuffer_p)(GLenum target, GLuint framebuffer);
static void (*glBlitFramebuffer_p)(GLint srcX0, GLint srcY0, GLint srcX1, GLint srcY1,
                                   GLint dstX0, GLint dstY0, GLint dstX1, GLint dstY1,
                                   GLbitfield mask, GLenum filter);
static void (*glClear_p)(GLbitfield mask);
static void (*glClearColor_p)(GLfloat red, GLfloat green, GLfloat blue, GLfloat alpha);
static void (*glDisable_p)(GLenum cap);
static EGLBoolean (*eglPresentationTimeANDROID_p)(EGLDisplay dpy, EGLSurface surface, EGLnsecs time);

static int64_t recorder_now_ns(void) {
    struct timespec ts;
    clock_gettime(CLOCK_MONOTONIC, &ts);
    return (int64_t) ts.tv_sec * 1000000000LL + (int64_t) ts.tv_nsec;
}

static void* recorder_gl_sym(const char* name) {
    void* symbol = NULL;
    // Ask the EGL implementation the game is actually running on first: with Mesa/Zink or
    // MobileGlues the system libGLESv2 is not the library holding the entry points we need.
    if (eglGetProcAddress_p != NULL) symbol = (void*) eglGetProcAddress_p(name);
    if (symbol == NULL) symbol = dlsym(RTLD_DEFAULT, name);
    return symbol;
}

static bool recorder_resolve_gl(void) {
    if (glBlitFramebuffer_p != NULL) return true; // already resolved

    glBindFramebuffer_p = recorder_gl_sym("glBindFramebuffer");
    glClear_p = recorder_gl_sym("glClear");
    glClearColor_p = recorder_gl_sym("glClearColor");
    glDisable_p = recorder_gl_sym("glDisable");
    // Core in GLES 3.0 and desktop GL 3.0; the vendor extensions cover GLES 2 drivers.
    void* blit = recorder_gl_sym("glBlitFramebuffer");
    if (blit == NULL) blit = recorder_gl_sym("glBlitFramebufferNV");
    if (blit == NULL) blit = recorder_gl_sym("glBlitFramebufferANGLE");

    if (blit == NULL || glBindFramebuffer_p == NULL || glClear_p == NULL ||
        glClearColor_p == NULL || glDisable_p == NULL) {
        LOGE("This renderer does not expose framebuffer blitting, cannot record");
        glBindFramebuffer_p = NULL;
        return false;
    }
    glBlitFramebuffer_p = blit;

    // Optional: without it MediaCodec timestamps frames as they arrive, which is good enough.
    eglPresentationTimeANDROID_p = recorder_gl_sym("eglPresentationTimeANDROID");
    if (eglPresentationTimeANDROID_p == NULL)
        LOGW("EGL_ANDROID_presentation_time is missing, video timing may be uneven");
    return true;
}

/** Drop everything we own. Render thread, with the game context current. */
static void recorder_release(EGLDisplay display) {
    if (recorder_surface != EGL_NO_SURFACE) {
        eglDestroySurface_p(display, recorder_surface);
        recorder_surface = EGL_NO_SURFACE;
    }
    if (recorder_context != EGL_NO_CONTEXT) {
        eglDestroyContext_p(display, recorder_context);
        recorder_context = EGL_NO_CONTEXT;
    }
    if (recorder_window != NULL) {
        ANativeWindow_release(recorder_window);
        recorder_window = NULL;
    }
}

/** Move to IDLE and wake up whoever is waiting in nativeStopRecording(). Caller holds the lock. */
static void recorder_finish_locked(EGLDisplay display) {
    recorder_release(display);
    atomic_store_explicit(&recorder_state, RECORDER_IDLE, memory_order_release);
    pthread_cond_broadcast(&recorder_cond);
}

/** Create the encoder-side EGL objects. Caller holds the lock. */
static bool recorder_setup_locked(EGLDisplay display, gl_render_window_t* bundle) {
    if (!recorder_resolve_gl()) return false;

    // glBlitFramebuffer needs GLES 3 / GL 3, but the game context may be an ES 2 one (the
    // "opengles2" renderer forces LIBGL_ES=2). Ask for our own ES 3 context instead: ES 2 and
    // ES 3 contexts are allowed to share, so we can record either way. If the driver refuses,
    // fall back to sharing the game's own client version.
    const EGLint attributes_es3[] = {EGL_CONTEXT_CLIENT_VERSION, 3, EGL_NONE};
    recorder_context = eglCreateContext_p(display, bundle->config, bundle->context, attributes_es3);
    if (recorder_context == EGL_NO_CONTEXT) {
        LOGW("Could not create an ES 3 recording context (%04x), retrying with ES 2", eglGetError_p());
        const EGLint attributes_es2[] = {EGL_CONTEXT_CLIENT_VERSION, 2, EGL_NONE};
        recorder_context = eglCreateContext_p(display, bundle->config, bundle->context, attributes_es2);
    }
    if (recorder_context == EGL_NO_CONTEXT) {
        LOGE("eglCreateContext() for the recorder failed: %04x", eglGetError_p());
        return false;
    }

    // The buffer geometry of a MediaCodec input surface is owned by the codec, so unlike the
    // game window we must not touch it here.
    recorder_surface = eglCreateWindowSurface_p(display, bundle->config, recorder_window, NULL);
    if (recorder_surface == EGL_NO_SURFACE) {
        LOGE("eglCreateWindowSurface() on the encoder surface failed: %04x", eglGetError_p());
        eglDestroyContext_p(display, recorder_context);
        recorder_context = EGL_NO_CONTEXT;
        return false;
    }

    recorder_next_frame_ns = recorder_now_ns();
    LOGI("Recording started, encoding at %dx%d", recorder_width, recorder_height);
    return true;
}

/**
 * Blit the game's back buffer into the encoder surface and present it.
 * Render thread, with the game context current; leaves it current again on return.
 */
static bool recorder_capture(EGLDisplay display, gl_render_window_t* bundle, int64_t now) {
    EGLint game_width = 0, game_height = 0;
    if (!eglQuerySurface_p(display, bundle->surface, EGL_WIDTH, &game_width) ||
        !eglQuerySurface_p(display, bundle->surface, EGL_HEIGHT, &game_height)) {
        return true; // transient, keep recording
    }
    // The bridge parks the game on a 1x1 pbuffer whenever the window is gone. Recording that
    // would just write blocks of garbage, so skip until a real surface is back.
    if (game_width < RECORDER_MIN_SURFACE_SIZE || game_height < RECORDER_MIN_SURFACE_SIZE) return true;

    // Read from the game surface, draw into the encoder surface, using our own context so that
    // the bindings we change below are invisible to the game.
    if (!eglMakeCurrent_p(display, recorder_surface, bundle->surface, recorder_context)) {
        LOGE("Could not bind the recording context: %04x", eglGetError_p());
        return false;
    }

    // Fit the frame into the fixed encoder surface without stretching it. The game surface can
    // change size mid-recording (rotation, resolution scaler), the encoder surface cannot.
    int fit_width = recorder_width;
    int fit_height = (int) ((int64_t) recorder_width * game_height / game_width);
    if (fit_height > recorder_height) {
        fit_height = recorder_height;
        fit_width = (int) ((int64_t) recorder_height * game_width / game_height);
    }
    int offset_x = (recorder_width - fit_width) / 2;
    int offset_y = (recorder_height - fit_height) / 2;

    glBindFramebuffer_p(GL_READ_FRAMEBUFFER, 0);
    glBindFramebuffer_p(GL_DRAW_FRAMEBUFFER, 0);
    glDisable_p(GL_SCISSOR_TEST); // a scissor box would clip the blit
    if (fit_width != recorder_width || fit_height != recorder_height) {
        glClearColor_p(0.0f, 0.0f, 0.0f, 1.0f);
        glClear_p(GL_COLOR_BUFFER_BIT); // letterbox bars
    }
    glBlitFramebuffer_p(0, 0, game_width, game_height,
                        offset_x, offset_y, offset_x + fit_width, offset_y + fit_height,
                        GL_COLOR_BUFFER_BIT, GL_LINEAR);

    if (eglPresentationTimeANDROID_p != NULL)
        eglPresentationTimeANDROID_p(display, recorder_surface, now - recorder_start_ns);
    eglSwapBuffers_p(display, recorder_surface); // hands the frame to the encoder

    if (!eglMakeCurrent_p(display, bundle->surface, bundle->surface, bundle->context)) {
        // The game cannot keep rendering without its context, so this one is fatal for us.
        LOGE("Failed to restore the game context after a captured frame: %04x", eglGetError_p());
        return false;
    }
    return true;
}

void gl_recorder_frame(EGLDisplay display, gl_render_window_t* bundle) {
    int state = atomic_load_explicit(&recorder_state, memory_order_acquire);
    if (state == RECORDER_IDLE) return;
    if (bundle == NULL || bundle->surface == NULL) return;

    if (state == RECORDER_ACTIVE) {
        int64_t now = recorder_now_ns();
        // Pace the capture to the target frame rate: the game may well be presenting at 60 or
        // 120 Hz and there is no point in encoding frames the output video cannot hold.
        if (now < recorder_next_frame_ns) return;
        recorder_next_frame_ns += recorder_frame_interval_ns;
        // Resynchronise instead of trying to catch up after a stall (loading screen, GC pause).
        if (recorder_next_frame_ns < now) recorder_next_frame_ns = now + recorder_frame_interval_ns;

        if (!recorder_capture(display, bundle, now)) {
            LOGE("Stopping the recording after a capture failure");
            pthread_mutex_lock(&recorder_mutex);
            recorder_finish_locked(display);
            pthread_mutex_unlock(&recorder_mutex);
            // If the failure was the rebind itself, the game is left pointing at a surface that
            // no longer exists. Now that ours is gone, give it one more chance to come back.
            if (eglGetCurrentContext_p() != bundle->context &&
                !eglMakeCurrent_p(display, bundle->surface, bundle->surface, bundle->context))
                LOGE("The game context could not be restored: %04x", eglGetError_p());
        }
        return;
    }

    // Start and stop are handled here, on the render thread, because creating and destroying
    // EGL objects needs to happen where the context lives.
    pthread_mutex_lock(&recorder_mutex);
    state = atomic_load_explicit(&recorder_state, memory_order_acquire);
    if (state == RECORDER_PENDING) {
        if (recorder_setup_locked(display, bundle)) {
            atomic_store_explicit(&recorder_state, RECORDER_ACTIVE, memory_order_release);
        } else {
            recorder_finish_locked(display);
        }
    } else if (state == RECORDER_STOPPING) {
        recorder_finish_locked(display);
        LOGI("Recording stopped");
    }
    pthread_mutex_unlock(&recorder_mutex);
}

JNIEXPORT jboolean JNICALL
Java_net_kdt_pojavlaunch_recorder_GameRecorder_nativeStartRecording(JNIEnv* env, jclass clazz,
                                                                   jobject surface, jint width,
                                                                   jint height, jint frameRate,
                                                                   jlong startTimeNanos) {
    (void) clazz;
    if (surface == NULL || width <= 0 || height <= 0 || frameRate <= 0) return JNI_FALSE;

    pthread_mutex_lock(&recorder_mutex);
    if (atomic_load_explicit(&recorder_state, memory_order_acquire) != RECORDER_IDLE) {
        pthread_mutex_unlock(&recorder_mutex);
        LOGW("A recording is already in progress");
        return JNI_FALSE;
    }
    recorder_window = ANativeWindow_fromSurface(env, surface);
    if (recorder_window == NULL) {
        pthread_mutex_unlock(&recorder_mutex);
        LOGE("Could not get a native window out of the encoder surface");
        return JNI_FALSE;
    }
    recorder_width = width;
    recorder_height = height;
    recorder_frame_interval_ns = 1000000000LL / frameRate;
    // System.nanoTime() is CLOCK_MONOTONIC on Android, the same clock recorder_now_ns() reads.
    recorder_start_ns = startTimeNanos;
    // The render thread picks this up on its next presented frame.
    atomic_store_explicit(&recorder_state, RECORDER_PENDING, memory_order_release);
    pthread_mutex_unlock(&recorder_mutex);
    return JNI_TRUE;
}

JNIEXPORT void JNICALL
Java_net_kdt_pojavlaunch_recorder_GameRecorder_nativeStopRecording(JNIEnv* env, jclass clazz) {
    (void) env;
    (void) clazz;
    pthread_mutex_lock(&recorder_mutex);
    if (atomic_load_explicit(&recorder_state, memory_order_acquire) == RECORDER_IDLE) {
        pthread_mutex_unlock(&recorder_mutex);
        return;
    }
    atomic_store_explicit(&recorder_state, RECORDER_STOPPING, memory_order_release);

    // Wait for the render thread to release the encoder surface, so that the caller can safely
    // signal end of stream to MediaCodec afterwards.
    struct timespec deadline;
    clock_gettime(CLOCK_REALTIME, &deadline);
    deadline.tv_sec += RECORDER_STOP_TIMEOUT_NS / 1000000000LL;
    deadline.tv_nsec += RECORDER_STOP_TIMEOUT_NS % 1000000000LL;
    if (deadline.tv_nsec >= 1000000000L) {
        deadline.tv_nsec -= 1000000000L;
        deadline.tv_sec += 1;
    }
    while (atomic_load_explicit(&recorder_state, memory_order_acquire) != RECORDER_IDLE) {
        if (pthread_cond_timedwait(&recorder_cond, &recorder_mutex, &deadline) == ETIMEDOUT) {
            // The render thread is not presenting anymore (game frozen or gone). Give the
            // recorder back to the user rather than wedging it; the EGL objects can only be
            // touched from the render thread, so they are dropped instead of destroyed.
            LOGE("Timed out waiting for the render thread, forcing the recorder back to idle");
            recorder_surface = EGL_NO_SURFACE;
            recorder_context = EGL_NO_CONTEXT;
            recorder_window = NULL;
            atomic_store_explicit(&recorder_state, RECORDER_IDLE, memory_order_release);
            break;
        }
    }
    pthread_mutex_unlock(&recorder_mutex);
}
