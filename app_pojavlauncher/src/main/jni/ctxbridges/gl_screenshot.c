//
// In-pipeline screenshots. See gl_screenshot.h.
//
// Three properties are worth stating up front, because each of them is a decision rather than an
// accident:
//
// The read happens in a context of our own that shares the game's, exactly as the recorder does,
// and NOT on the game's context. glReadPixels implicitly reads state the game owns — the pixel
// pack buffer binding, the pack alignment and row length, the read framebuffer binding — and
// saving and restoring all of it is not symmetric between ES 2 and ES 3: GL_FRAMEBUFFER_BINDING
// and GL_DRAW_FRAMEBUFFER_BINDING are the same number, while GL_READ_FRAMEBUFFER_BINDING is a
// different one. Getting that wrong does not spoil the screenshot, it spoils the game's rendering
// from then on. A context created for the purpose has all of that state at its defaults by
// construction, so there is nothing to save and nothing to get wrong. An ES 2 game context is a
// real configuration here (the "opengles2" renderer forces it), which is why this cannot simply
// assume ES 3.
//
// The context is built and destroyed per screenshot. It is worth a few milliseconds once for a
// deliberate, occasional action to avoid holding a GL context — and the driver memory behind it —
// for a whole session on the chance that another screenshot is coming.
//
// The wait for the game's drawing to land is a plain glFinish() on the game's context before the
// switch, not the fence the recorder uses. The recorder runs on every frame and cannot afford to
// stall the CPU; this runs once and the stronger, simpler guarantee is the right trade.
//

#include <GLES2/gl2.h>
#include <dlfcn.h>
#include <errno.h>
#include <jni.h>
#include <pthread.h>
#include <stdatomic.h>
#include <stdbool.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>

#include "gl_screenshot.h"
#include "egl_loader.h"

#define TAG "GLScreenshot"
#include <log.h>

/* Below this the bridge has parked the game on its 1x1 placeholder surface, so there is no frame
 * to capture yet. Same threshold the recorder uses, for the same reason. */
#define SHOT_MIN_SURFACE_SIZE 16
/* A guard against a nonsense surface size turning into a nonsense allocation, not a real limit:
 * 16k square is far past any device and still only a gigabyte. */
#define SHOT_MAX_DIMENSION 16384

enum {
    SHOT_IDLE = 0,      /* nobody is waiting */
    SHOT_REQUESTED = 1, /* the next presented frame should be captured */
    SHOT_READY = 2,     /* pixels are sitting in shot_pixels, waiting to be collected */
    SHOT_FAILED = 3     /* the render thread tried and could not */
};

static atomic_int shot_state = SHOT_IDLE;

static pthread_mutex_t shot_mutex = PTHREAD_MUTEX_INITIALIZER;
static pthread_cond_t shot_cond = PTHREAD_COND_INITIALIZER;

/* Written by the render thread, read by the collector, both under shot_mutex. */
static uint8_t* shot_pixels;
static int shot_width;
static int shot_height;

static void (*glReadPixels_p)(GLint x, GLint y, GLsizei width, GLsizei height, GLenum format,
                              GLenum type, void* pixels);
static void (*glFinish_p)(void);

/**
 * Resolve a GL entry point.
 * The renderer's own eglGetProcAddress first: with Mesa/Zink or MobileGlues the system libGLESv2
 * is not the library holding the entry points the game is actually running on.
 */
static void* shot_gl_sym(const char* name) {
    void* symbol = NULL;
    if (eglGetProcAddress_p != NULL) symbol = (void*) eglGetProcAddress_p(name);
    if (symbol == NULL) symbol = dlsym(RTLD_DEFAULT, name);
    return symbol;
}

static bool shot_resolve_gl(void) {
    if (glReadPixels_p != NULL) return true;
    glReadPixels_p = shot_gl_sym("glReadPixels");
    glFinish_p = shot_gl_sym("glFinish");
    if (glReadPixels_p == NULL) {
        LOGE("This renderer has no glReadPixels, so it cannot be captured");
        return false;
    }
    return true;
}

/** Publish a finished capture and wake whoever is waiting in nativeAwait(). Caller holds the lock. */
static void shot_publish_locked(uint8_t* pixels, int width, int height) {
    free(shot_pixels);
    shot_pixels = pixels;
    shot_width = width;
    shot_height = height;
    atomic_store_explicit(&shot_state, pixels != NULL ? SHOT_READY : SHOT_FAILED,
                          memory_order_release);
    pthread_cond_broadcast(&shot_cond);
}

/**
 * Turn a bottom-up RGBA readback into the top-down, opaque, tightly packed rows the Java side
 * expects.
 *
 * Done here rather than in Java so that every renderer and both bridges hand over exactly one
 * shape of buffer, and Bitmap.copyPixelsFromBuffer can take it without a word about strides.
 * The alpha channel is forced opaque because the game has no reason to leave anything sensible
 * in the back buffer's alpha and a PNG would honour whatever it found.
 */
static void shot_flip_rgba(const uint8_t* source, uint8_t* destination, int width, int height) {
    size_t row_bytes = (size_t) width * 4;
    for (int y = 0; y < height; y++) {
        const uint8_t* in = source + (size_t) (height - 1 - y) * row_bytes;
        uint8_t* out = destination + (size_t) y * row_bytes;
        memcpy(out, in, row_bytes);
        for (int x = 3; x < (int) row_bytes; x += 4) out[x] = 0xFF;
    }
}

/**
 * Read the back buffer through a context of our own.
 * Render thread, with the game context current; leaves it current again on return.
 */
static uint8_t* shot_capture_egl(EGLDisplay display, gl_render_window_t* bundle,
                                 int* out_width, int* out_height) {
    EGLint width = 0, height = 0;
    if (!eglQuerySurface_p(display, bundle->surface, EGL_WIDTH, &width) ||
        !eglQuerySurface_p(display, bundle->surface, EGL_HEIGHT, &height)) {
        LOGE("Could not measure the game surface: %04x", eglGetError_p());
        return NULL;
    }
    if (width < SHOT_MIN_SURFACE_SIZE || height < SHOT_MIN_SURFACE_SIZE ||
        width > SHOT_MAX_DIMENSION || height > SHOT_MAX_DIMENSION) {
        LOGE("The game surface is %dx%d, which is not a frame worth capturing", width, height);
        return NULL;
    }

    size_t bytes = (size_t) width * (size_t) height * 4;
    uint8_t* raw = malloc(bytes);
    uint8_t* result = malloc(bytes);
    if (raw == NULL || result == NULL) {
        LOGE("Not enough memory for a %dx%d screenshot", width, height);
        free(raw);
        free(result);
        return NULL;
    }

    // Everything the game has drawn this frame has to have landed before another context is
    // allowed to look at the buffer. glFinish is the blunt instrument and exactly the right one
    // for a once-in-a-while capture.
    if (glFinish_p != NULL) glFinish_p();

    // ES 3 first, falling back to ES 2, matching what the recorder does: the two are allowed to
    // share, so this works whichever the game is running.
    const EGLint attributes_es3[] = {EGL_CONTEXT_CLIENT_VERSION, 3, EGL_NONE};
    EGLContext context = eglCreateContext_p(display, bundle->config, bundle->context,
                                            attributes_es3);
    if (context == EGL_NO_CONTEXT) {
        const EGLint attributes_es2[] = {EGL_CONTEXT_CLIENT_VERSION, 2, EGL_NONE};
        context = eglCreateContext_p(display, bundle->config, bundle->context, attributes_es2);
    }
    if (context == EGL_NO_CONTEXT) {
        LOGE("Could not create a context to capture with: %04x", eglGetError_p());
        free(raw);
        free(result);
        return NULL;
    }

    // The game's own surface for both draw and read. Nothing is ever drawn through it — this
    // context only reads — so the frame the game is about to present is left exactly as it was.
    bool ok = eglMakeCurrent_p(display, bundle->surface, bundle->surface, context);
    if (ok) {
        // Framebuffer 0, pack alignment 4, no pack buffer: all of it the default state of a
        // context nobody has touched, and 4-byte rows of RGBA are always 4-aligned.
        glReadPixels_p(0, 0, width, height, GL_RGBA, GL_UNSIGNED_BYTE, raw);
    } else {
        LOGE("Could not bind the capture context: %04x", eglGetError_p());
    }

    // Back to the game before anything else can go wrong, because it cannot present without it.
    if (!eglMakeCurrent_p(display, bundle->surface, bundle->surface, bundle->context)) {
        LOGE("Failed to restore the game context after a screenshot: %04x", eglGetError_p());
        ok = false;
    }
    eglDestroyContext_p(display, context);

    if (!ok) {
        free(raw);
        free(result);
        return NULL;
    }

    shot_flip_rgba(raw, result, width, height);
    free(raw);
    *out_width = width;
    *out_height = height;
    return result;
}

void gl_screenshot_frame(EGLDisplay display, gl_render_window_t* bundle) {
    if (atomic_load_explicit(&shot_state, memory_order_acquire) != SHOT_REQUESTED) return;
    if (bundle == NULL || bundle->surface == NULL) return;

    pthread_mutex_lock(&shot_mutex);
    // Re-checked under the lock: the request may have been abandoned while we were taking it.
    if (atomic_load_explicit(&shot_state, memory_order_acquire) != SHOT_REQUESTED) {
        pthread_mutex_unlock(&shot_mutex);
        return;
    }

    uint8_t* pixels = NULL;
    int width = 0, height = 0;
    if (shot_resolve_gl()) pixels = shot_capture_egl(display, bundle, &width, &height);
    shot_publish_locked(pixels, width, height);
    pthread_mutex_unlock(&shot_mutex);
}

void gl_screenshot_frame_cpu(const void* pixels, int width, int height, int stride) {
    if (atomic_load_explicit(&shot_state, memory_order_acquire) != SHOT_REQUESTED) return;
    if (pixels == NULL) return;

    pthread_mutex_lock(&shot_mutex);
    if (atomic_load_explicit(&shot_state, memory_order_acquire) != SHOT_REQUESTED) {
        pthread_mutex_unlock(&shot_mutex);
        return;
    }

    uint8_t* result = NULL;
    if (width >= SHOT_MIN_SURFACE_SIZE && height >= SHOT_MIN_SURFACE_SIZE &&
        width <= SHOT_MAX_DIMENSION && height <= SHOT_MAX_DIMENSION && stride >= width) {
        size_t row_bytes = (size_t) width * 4;
        result = malloc(row_bytes * (size_t) height);
        if (result != NULL) {
            // OSMesa is set up with OSMESA_Y_UP off, so these rows are already the way round a
            // PNG wants them; only the stride and the unused fourth byte need dealing with.
            const uint8_t* source = (const uint8_t*) pixels;
            size_t source_row = (size_t) stride * 4;
            for (int y = 0; y < height; y++) {
                uint8_t* out = result + (size_t) y * row_bytes;
                memcpy(out, source + (size_t) y * source_row, row_bytes);
                for (int x = 3; x < (int) row_bytes; x += 4) out[x] = 0xFF;
            }
        } else {
            LOGE("Not enough memory for a %dx%d screenshot", width, height);
        }
    } else {
        LOGE("The window buffer is %dx%d stride %d, which is not a frame worth capturing",
             width, height, stride);
    }

    shot_publish_locked(result, width, height);
    pthread_mutex_unlock(&shot_mutex);
}

JNIEXPORT jboolean JNICALL
Java_net_kdt_pojavlaunch_screenshot_GameScreenshot_nativeRequest(JNIEnv* env, jclass clazz) {
    (void) env; (void) clazz;
    pthread_mutex_lock(&shot_mutex);
    if (atomic_load_explicit(&shot_state, memory_order_acquire) != SHOT_IDLE) {
        pthread_mutex_unlock(&shot_mutex);
        LOGW("A screenshot is already being taken");
        return JNI_FALSE;
    }
    atomic_store_explicit(&shot_state, SHOT_REQUESTED, memory_order_release);
    pthread_mutex_unlock(&shot_mutex);
    return JNI_TRUE;
}

/**
 * Wait for the render thread to hand a frame over.
 *
 * Returns a direct ByteBuffer of tightly packed, top-down, opaque RGBA rows, valid until
 * nativeRelease(). The buffer belongs to us, so Java must not hold it past that call.
 */
JNIEXPORT jobject JNICALL
Java_net_kdt_pojavlaunch_screenshot_GameScreenshot_nativeAwait(JNIEnv* env, jclass clazz,
                                                              jintArray info, jlong timeoutMs) {
    (void) clazz;
    struct timespec deadline;
    clock_gettime(CLOCK_REALTIME, &deadline);
    deadline.tv_sec += timeoutMs / 1000;
    deadline.tv_nsec += (timeoutMs % 1000) * 1000000L;
    if (deadline.tv_nsec >= 1000000000L) {
        deadline.tv_sec += 1;
        deadline.tv_nsec -= 1000000000L;
    }

    pthread_mutex_lock(&shot_mutex);
    int state = atomic_load_explicit(&shot_state, memory_order_acquire);
    while (state == SHOT_REQUESTED) {
        // A game that has stopped presenting frames — paused, or between worlds — would never
        // reach the hook, so this has to be able to give up.
        int rc = pthread_cond_timedwait(&shot_cond, &shot_mutex, &deadline);
        // Re-read before deciding: a frame that landed in the same instant the deadline passed
        // is a frame, and throwing it away would be a screenshot lost to a coin toss.
        state = atomic_load_explicit(&shot_state, memory_order_acquire);
        if (rc == ETIMEDOUT) break;
    }

    jobject buffer = NULL;
    if (state == SHOT_READY && shot_pixels != NULL) {
        buffer = (*env)->NewDirectByteBuffer(env, shot_pixels,
                                             (jlong) shot_width * shot_height * 4);
        if (buffer != NULL && info != NULL && (*env)->GetArrayLength(env, info) >= 2) {
            jint values[2] = {shot_width, shot_height};
            (*env)->SetIntArrayRegion(env, info, 0, 2, values);
        }
    }
    pthread_mutex_unlock(&shot_mutex);
    return buffer;
}

/**
 * Give the frame back.
 *
 * Also the way out of a request that never completed, so it must be safe to call in every state —
 * which is why Java calls it from a finally.
 */
JNIEXPORT void JNICALL
Java_net_kdt_pojavlaunch_screenshot_GameScreenshot_nativeRelease(JNIEnv* env, jclass clazz) {
    (void) env; (void) clazz;
    pthread_mutex_lock(&shot_mutex);
    free(shot_pixels);
    shot_pixels = NULL;
    shot_width = 0;
    shot_height = 0;
    atomic_store_explicit(&shot_state, SHOT_IDLE, memory_order_release);
    pthread_mutex_unlock(&shot_mutex);
}
