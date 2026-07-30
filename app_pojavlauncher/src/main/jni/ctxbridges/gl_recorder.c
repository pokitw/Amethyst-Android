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
#include <stdlib.h>
#include <time.h>

#include "gl_recorder.h"
#include "gl_overlay.h"
#include "egl_loader.h"
#include "loader_dlopen.h"
#include <environ/environ.h>

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

/* Render thread only, published while holding recorder_mutex during setup/teardown. */
static EGLSurface recorder_surface = EGL_NO_SURFACE;
static EGLContext recorder_context = EGL_NO_CONTEXT;
static int64_t recorder_next_frame_ns;
/** So a timestamping failure is reported once rather than on every frame. */
static bool recorder_timestamp_warned;

typedef int64_t EGLnsecs;
typedef void* EGLSyncHandle;

/* EGL_KHR_fence_sync / EGL_KHR_wait_sync */
#define RECORDER_SYNC_FENCE 0x30F9              /* EGL_SYNC_FENCE_KHR */
#define RECORDER_SYNC_FLUSH_COMMANDS_BIT 0x0001 /* EGL_SYNC_FLUSH_COMMANDS_BIT_KHR */
#define RECORDER_SYNC_FOREVER 0xFFFFFFFFFFFFFFFFULL

static void (*glBindFramebuffer_p)(GLenum target, GLuint framebuffer);
static void (*glColorMask_p)(GLboolean red, GLboolean green, GLboolean blue, GLboolean alpha);
static void (*glFinish_p)(void);
static EGLSyncHandle (*eglCreateSyncKHR_p)(EGLDisplay dpy, EGLenum type, const EGLint* attrib_list);
static EGLBoolean (*eglDestroySyncKHR_p)(EGLDisplay dpy, EGLSyncHandle sync);
static EGLint (*eglWaitSyncKHR_p)(EGLDisplay dpy, EGLSyncHandle sync, EGLint flags);
static EGLint (*eglClientWaitSyncKHR_p)(EGLDisplay dpy, EGLSyncHandle sync, EGLint flags,
                                        uint64_t timeout);
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

/**
 * Resolve an EGL entry point.
 * Not every EGL implementation reports the Android platform extensions through
 * eglGetProcAddress, so fall back to looking the symbol up in the very library the bridge
 * loaded. That keeps the entry point belonging to the implementation that owns our display and
 * surface, which a lookup against the system libEGL would not.
 */
static void* recorder_egl_sym(const char* name) {
    static void* egl_handle = NULL;
    void* symbol = NULL;
    if (eglGetProcAddress_p != NULL) symbol = (void*) eglGetProcAddress_p(name);
    if (symbol != NULL) return symbol;
    if (egl_handle == NULL)
        egl_handle = loader_dlopen(getenv("POJAVEXEC_EGL"), "libEGL.so", RTLD_LOCAL | RTLD_LAZY);
    if (egl_handle != NULL) symbol = dlsym(egl_handle, name);
    return symbol;
}

static bool recorder_resolve_gl(void) {
    if (glBlitFramebuffer_p != NULL) return true; // already resolved

    glBindFramebuffer_p = recorder_gl_sym("glBindFramebuffer");
    glClear_p = recorder_gl_sym("glClear");
    glClearColor_p = recorder_gl_sym("glClearColor");
    glColorMask_p = recorder_gl_sym("glColorMask");
    glDisable_p = recorder_gl_sym("glDisable");
    glFinish_p = recorder_gl_sym("glFinish");
    // Core in GLES 3.0 and desktop GL 3.0; the vendor extensions cover GLES 2 drivers.
    void* blit = recorder_gl_sym("glBlitFramebuffer");
    if (blit == NULL) blit = recorder_gl_sym("glBlitFramebufferNV");
    if (blit == NULL) blit = recorder_gl_sym("glBlitFramebufferANGLE");

    if (blit == NULL || glBindFramebuffer_p == NULL || glClear_p == NULL ||
        glClearColor_p == NULL || glColorMask_p == NULL || glDisable_p == NULL) {
        LOGE("This renderer does not expose framebuffer blitting, cannot record");
        glBindFramebuffer_p = NULL;
        return false;
    }
    glBlitFramebuffer_p = blit;

    // We read the game's back buffer from a context of our own, and switching contexts only
    // flushes the game's commands, it does not wait for them. Without a fence the blit can race
    // ahead into the next frame and capture it half drawn or freshly cleared, which shows up as
    // bright flashes in the video. A fence makes the GPU wait for us, at no cost to the CPU.
    eglCreateSyncKHR_p = recorder_egl_sym("eglCreateSyncKHR");
    eglDestroySyncKHR_p = recorder_egl_sym("eglDestroySyncKHR");
    eglWaitSyncKHR_p = recorder_egl_sym("eglWaitSyncKHR");
    eglClientWaitSyncKHR_p = recorder_egl_sym("eglClientWaitSyncKHR");
    if (eglCreateSyncKHR_p == NULL || eglDestroySyncKHR_p == NULL ||
        (eglWaitSyncKHR_p == NULL && eglClientWaitSyncKHR_p == NULL)) {
        eglCreateSyncKHR_p = NULL;
        LOGW("EGL fence syncs are missing, falling back to glFinish() before each capture");
        if (glFinish_p == NULL) {
            LOGE("Neither fence syncs nor glFinish() are available, cannot record safely");
            glBindFramebuffer_p = NULL;
            glBlitFramebuffer_p = NULL;
            return false;
        }
    }

    /*
     * Nice to have, not required. Frames are stamped in the raw CLOCK_MONOTONIC domain, which is
     * exactly what the buffer queue falls back to when nobody sets a timestamp, so both paths end
     * up on the same clock. Setting it ourselves just pins the time to the moment the frame was
     * captured rather than the moment the encoder happened to pick the buffer up.
     *
     * MobileGlues' EGL does not report this through eglGetProcAddress, and the symbol is not in
     * the library either, so fall back to the platform libEGL: a translation layer like that is
     * passing our surface straight through to it. If the guess is wrong the call simply reports a
     * bad surface, which is checked for and costs nothing.
     */
    eglPresentationTimeANDROID_p = recorder_egl_sym("eglPresentationTimeANDROID");
    if (eglPresentationTimeANDROID_p == NULL) {
        void* system_egl = dlopen("libEGL.so", RTLD_LOCAL | RTLD_LAZY);
        if (system_egl != NULL)
            eglPresentationTimeANDROID_p = dlsym(system_egl, "eglPresentationTimeANDROID");
    }
    LOGI("Frame timestamping is %s, timestamps are CLOCK_MONOTONIC and rebased when muxed",
         eglPresentationTimeANDROID_p != NULL ? "available" : "left to the buffer queue");
    LOGI("Frame timestamping is available");
    return true;
}

/** Drop everything we own. Render thread, with the game context current. */
static void recorder_release(EGLDisplay display) {
    // The overlay's GL objects belong to the context destroyed just below, so they go with it.
    // This only clears the bookkeeping, and deliberately issues no GL calls: the recorder's
    // context is not current here, the game's is.
    gl_overlay_release();
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
    recorder_timestamp_warned = false;
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

    // Fence the game's work for this frame while its context is still current, so that our blit
    // can be made to wait for it. Without this the capture races the game and picks up frames
    // that are half drawn or already cleared for the next one.
    EGLSyncHandle fence = NULL;
    if (eglCreateSyncKHR_p != NULL) {
        fence = eglCreateSyncKHR_p(display, RECORDER_SYNC_FENCE, NULL);
        if (fence == NULL) LOGW("Could not fence the frame: %04x", eglGetError_p());
    }
    if (fence == NULL && glFinish_p != NULL) {
        glFinish_p(); // no fences available, wait it out on the CPU instead
    }

    // Read from the game surface, draw into the encoder surface, using our own context so that
    // the bindings we change below are invisible to the game.
    if (!eglMakeCurrent_p(display, recorder_surface, bundle->surface, recorder_context)) {
        LOGE("Could not bind the recording context: %04x", eglGetError_p());
        if (fence != NULL) eglDestroySyncKHR_p(display, fence);
        return false;
    }

    if (fence != NULL) {
        // Make the GPU, not the CPU, wait for the frame to actually be there.
        if (eglWaitSyncKHR_p != NULL) eglWaitSyncKHR_p(display, fence, 0);
        else eglClientWaitSyncKHR_p(display, fence, RECORDER_SYNC_FLUSH_COMMANDS_BIT,
                                    RECORDER_SYNC_FOREVER);
        eglDestroySyncKHR_p(display, fence);
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
    // Start from opaque black every frame. The encoder recycles its buffers and hands them back
    // with undefined contents, and it lays down the letterbox bars in one go.
    glColorMask_p(GL_TRUE, GL_TRUE, GL_TRUE, GL_TRUE);
    glClearColor_p(0.0f, 0.0f, 0.0f, 1.0f);
    glClear_p(GL_COLOR_BUFFER_BIT);
    // The game does not care what it leaves in the alpha channel, but the encoder does, so keep
    // the opaque alpha from the clear rather than copying the game's.
    glColorMask_p(GL_TRUE, GL_TRUE, GL_TRUE, GL_FALSE);
    glBlitFramebuffer_p(0, 0, game_width, game_height,
                        offset_x, offset_y, offset_x + fit_width, offset_y + fit_height,
                        GL_COLOR_BUFFER_BIT, GL_LINEAR);
    glColorMask_p(GL_TRUE, GL_TRUE, GL_TRUE, GL_TRUE);

    // The virtual mouse is an Android view above the game, so it is not in what we just copied.
    gl_overlay_draw(recorder_gl_sym, recorder_width, recorder_height, game_width, game_height,
                    offset_x, offset_y, fit_width, fit_height,
                    pojav_environ->cursorX, pojav_environ->cursorY);

    /*
     * Stamped with the raw clock, not an offset from the start of the recording. The buffer queue
     * uses this very clock when no timestamp is set, so leaving the domain alone means the video
     * track lands in the same place either way. The audio track is built against it too, and the
     * muxer rebases both to zero, which is what keeps the file's duration honest.
     */
    if (eglPresentationTimeANDROID_p != NULL &&
        !eglPresentationTimeANDROID_p(display, recorder_surface, now) &&
        !recorder_timestamp_warned) {
        recorder_timestamp_warned = true;
        // Not fatal: the buffer queue stamps the frame on the same clock by itself.
        LOGW("Could not timestamp the frame (%04x), leaving it to the buffer queue",
             eglGetError_p());
    }
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
                                                                   jint height, jint frameRate) {
    (void) clazz;
    if (surface == NULL || width <= 0 || height <= 0 || frameRate <= 0) return JNI_FALSE;

    pthread_mutex_lock(&recorder_mutex);
    if (atomic_load_explicit(&recorder_state, memory_order_acquire) != RECORDER_IDLE) {
        pthread_mutex_unlock(&recorder_mutex);
        LOGW("A recording is already in progress");
        return JNI_FALSE;
    }
    // Resolving entry points needs no current context, so find out now whether this renderer can
    // be recorded at all. Failing here reports the problem before the caller has set an encoder
    // and an audio capture running, instead of leaving it to discover an empty file at the end.
    if (!recorder_resolve_gl()) {
        pthread_mutex_unlock(&recorder_mutex);
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
    // The render thread picks this up on its next presented frame.
    atomic_store_explicit(&recorder_state, RECORDER_PENDING, memory_order_release);
    pthread_mutex_unlock(&recorder_mutex);
    return JNI_TRUE;
}

JNIEXPORT void JNICALL
Java_net_kdt_pojavlaunch_recorder_GameRecorder_nativeSetPointerBitmap(JNIEnv* env, jclass clazz,
                                                                     jbyteArray rgba, jint width,
                                                                     jint height) {
    (void) clazz;
    if (rgba == NULL || width <= 0 || height <= 0) return;
    jsize length = (*env)->GetArrayLength(env, rgba);
    if (length < (jsize) (width * height * 4)) return;
    jbyte* pixels = (*env)->GetByteArrayElements(env, rgba, NULL);
    if (pixels == NULL) return;
    gl_overlay_set_bitmap((const uint8_t*) pixels, width, height);
    (*env)->ReleaseByteArrayElements(env, rgba, pixels, JNI_ABORT);
}

JNIEXPORT void JNICALL
Java_net_kdt_pojavlaunch_recorder_GameRecorder_nativeSetPointerState(JNIEnv* env, jclass clazz,
                                                                    jboolean visible, jfloat width,
                                                                    jfloat height) {
    (void) env;
    (void) clazz;
    gl_overlay_set_state(visible == JNI_TRUE, width, height);
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
