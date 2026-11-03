/*
 * The native half of the web engine Lunar Client draws its own menus with, rebuilt for Android.
 *
 * Lunar ships this library as x86 code wrapped around Ultralight, a closed web engine with no
 * Android build, and the client refuses to start when WebOSR.createEngine has no native behind
 * it. This file is that native, written from the Java interface alone: fifty methods across
 * thirteen wrapper classes, every one present.
 *
 * Stage one, which this is: every object the client asks for exists and behaves sanely, nothing
 * is rendered and no call can block. That was verified by running real Lunar Client 1.8.9 and
 * 1.21.8 on a desktop JVM with this library standing in for Ultralight: both initialise, and
 * 1.21.8 reaches its title screen (scripts/webosrsim). What it does not do yet is draw: the
 * client's own HTML menus are a transparent texture until a real engine fills it.
 *
 * Everything here is reached through JNI from inside the client's own class loader, so FindClass
 * resolves the client's classes and LWJGL's without either being on the app class path.
 */
#include <jni.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>
#include <stdio.h>

#define PKG "com/moonsworth/webosr/"
#define TRACE_ENV "AMETHYST_WEBOSR_TRACE"

typedef struct { jint tex; int w, h; jobject listener; char *url; } browser_t;
typedef struct { jint tex; int w, h; } image_t;
typedef struct { jobject logger; } engine_t;
typedef struct { char *name; jboolean persistent; } session_t;

static void trace(const char *what) {
    if (getenv(TRACE_ENV)) fprintf(stderr, "[webosr] %s\n", what);
}

static jlong get_handle(JNIEnv *env, jobject self) {
    jclass nh = (*env)->FindClass(env, PKG "NativeHandle");
    jfieldID f = (*env)->GetFieldID(env, nh, "handle", "J");
    return (*env)->GetLongField(env, self, f);
}

/* The Java constructors are private and take the handle, so construction is one call. */
static jobject make(JNIEnv *env, const char *cls, void *ptr) {
    jclass c = (*env)->FindClass(env, cls);
    if (!c) return NULL;
    jmethodID ctor = (*env)->GetMethodID(env, c, "<init>", "(J)V");
    if (!ctor) return NULL;
    return (*env)->NewObject(env, c, ctor, (jlong) (intptr_t) ptr);
}

/*
 * GL goes through LWJGL's own GL11 class rather than a system GL library. On Android the game's
 * GL is whichever renderer Amethyst loaded (gl4es, MobileGlues, Zink), and a texture name
 * created anywhere else would be a name that renderer has never heard of. Both LWJGL 2 and 3
 * spell these five methods identically, so one path serves every version the client runs.
 */
#define GL_TEXTURE_2D 0x0DE1
#define GL_TEXTURE_BINDING_2D 0x8069
#define GL_TEXTURE_MIN_FILTER 0x2801
#define GL_TEXTURE_MAG_FILTER 0x2800
#define GL_LINEAR 0x2601
#define GL_RGBA 0x1908
#define GL_UNSIGNED_BYTE 0x1401

static jclass gl11;
static jmethodID m_gen, m_del, m_bind, m_param, m_image, m_get;

static int gl_ready(JNIEnv *env) {
    if (gl11) return 1;
    jclass c = (*env)->FindClass(env, "org/lwjgl/opengl/GL11");
    if (!c) { (*env)->ExceptionClear(env); return 0; }
    m_gen = (*env)->GetStaticMethodID(env, c, "glGenTextures", "()I");
    m_del = (*env)->GetStaticMethodID(env, c, "glDeleteTextures", "(I)V");
    m_bind = (*env)->GetStaticMethodID(env, c, "glBindTexture", "(II)V");
    m_param = (*env)->GetStaticMethodID(env, c, "glTexParameteri", "(III)V");
    m_image = (*env)->GetStaticMethodID(env, c, "glTexImage2D", "(IIIIIIIILjava/nio/ByteBuffer;)V");
    m_get = (*env)->GetStaticMethodID(env, c, "glGetInteger", "(I)I");
    if ((*env)->ExceptionCheck(env) || !m_gen || !m_del || !m_bind || !m_param || !m_image || !m_get) {
        (*env)->ExceptionClear(env);
        return 0;
    }
    gl11 = (*env)->NewGlobalRef(env, c);
    return 1;
}

static void gl_bind(JNIEnv *env, jint tex) {
    if (gl_ready(env)) (*env)->CallStaticVoidMethod(env, gl11, m_bind, GL_TEXTURE_2D, tex);
}

static void gl_delete(JNIEnv *env, jint tex) {
    if (tex && gl_ready(env)) (*env)->CallStaticVoidMethod(env, gl11, m_del, tex);
}

/* A real, transparent texture: Lunar binds what it is given, and texture 0 is not "nothing". */
static jint blank_texture(JNIEnv *env, int w, int h) {
    if (!gl_ready(env)) return 0;
    if (w < 1) w = 1;
    if (h < 1) h = 1;
    jint previous = (*env)->CallStaticIntMethod(env, gl11, m_get, GL_TEXTURE_BINDING_2D);
    jint t = (*env)->CallStaticIntMethod(env, gl11, m_gen);
    (*env)->CallStaticVoidMethod(env, gl11, m_bind, GL_TEXTURE_2D, t);
    (*env)->CallStaticVoidMethod(env, gl11, m_param, GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
    (*env)->CallStaticVoidMethod(env, gl11, m_param, GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
    size_t bytes = (size_t) w * h * 4;
    void *zero = calloc(bytes, 1);
    jobject buf = zero ? (*env)->NewDirectByteBuffer(env, zero, (jlong) bytes) : NULL;
    (*env)->CallStaticVoidMethod(env, gl11, m_image, GL_TEXTURE_2D, 0, GL_RGBA, w, h, 0,
            GL_RGBA, GL_UNSIGNED_BYTE, buf);
    if (buf) (*env)->DeleteLocalRef(env, buf);
    free(zero);
    (*env)->CallStaticVoidMethod(env, gl11, m_bind, GL_TEXTURE_2D, previous);
    if ((*env)->ExceptionCheck(env)) { (*env)->ExceptionClear(env); return 0; }
    return t;
}

/* ---- WebOSR ---------------------------------------------------------------------------- */

JNIEXPORT jobject JNICALL Java_com_moonsworth_webosr_wrappers_WebOSR_createEngine(
        JNIEnv *env, jclass cls, jobject logger, jobject config, jobject threads) {
    trace("createEngine");
    engine_t *e = calloc(1, sizeof *e);
    if (logger) e->logger = (*env)->NewGlobalRef(env, logger);
    return make(env, PKG "wrappers/WebEngine", e);
}

/* ---- WebEngine ------------------------------------------------------------------------- */

JNIEXPORT jobject JNICALL Java_com_moonsworth_webosr_wrappers_WebEngine_createImageSourceTextured(
        JNIEnv *env, jobject self, jstring name) {
    trace("createImageSourceTextured");
    image_t *i = calloc(1, sizeof *i);
    i->w = i->h = 1;
    i->tex = blank_texture(env, 1, 1);
    return make(env, PKG "wrappers/image/TexturedImageSource", i);
}

JNIEXPORT jobject JNICALL Java_com_moonsworth_webosr_wrappers_WebEngine_createSession(
        JNIEnv *env, jobject self, jstring name, jboolean persistent) {
    trace("createSession");
    session_t *s = calloc(1, sizeof *s);
    const char *n = name ? (*env)->GetStringUTFChars(env, name, NULL) : NULL;
    s->name = strdup(n ? n : "default");
    if (n) (*env)->ReleaseStringUTFChars(env, name, n);
    s->persistent = persistent;
    return make(env, PKG "wrappers/Session", s);
}

static jint int_field(JNIEnv *env, jobject o, const char *name) {
    jclass c = (*env)->GetObjectClass(env, o);
    jfieldID f = (*env)->GetFieldID(env, c, name, "I");
    return f ? (*env)->GetIntField(env, o, f) : 0;
}

JNIEXPORT jobject JNICALL Java_com_moonsworth_webosr_wrappers_WebEngine_createBrowser(
        JNIEnv *env, jobject self, jobject config) {
    trace("createBrowser");
    browser_t *b = calloc(1, sizeof *b);
    if (config) {
        b->w = int_field(env, config, "width");
        b->h = int_field(env, config, "height");
        jclass c = (*env)->GetObjectClass(env, config);
        jfieldID f = (*env)->GetFieldID(env, c, "eventHandler",
                "L" PKG "wrappers/Browser$Listener;");
        jobject l = f ? (*env)->GetObjectField(env, config, f) : NULL;
        if (l) b->listener = (*env)->NewGlobalRef(env, l);
    }
    b->tex = blank_texture(env, b->w, b->h);
    return make(env, PKG "wrappers/Browser", b);
}

JNIEXPORT void JNICALL Java_com_moonsworth_webosr_wrappers_WebEngine_setTimers(
        JNIEnv *env, jobject self, jobject threads) {}

JNIEXPORT jobject JNICALL Java_com_moonsworth_webosr_wrappers_WebEngine_getPlatformInterface(
        JNIEnv *env, jobject self) {
    trace("getPlatformInterface");
    return make(env, PKG "wrappers/PlatformInterface", calloc(1, 8));
}

JNIEXPORT void JNICALL Java_com_moonsworth_webosr_wrappers_WebEngine_stop(JNIEnv *env, jobject self) {}

JNIEXPORT void JNICALL Java_com_moonsworth_webosr_wrappers_WebEngine_destroy(JNIEnv *env, jobject self) {
    engine_t *e = (engine_t *) (intptr_t) get_handle(env, self);
    if (!e) return;
    if (e->logger) (*env)->DeleteGlobalRef(env, e->logger);
    free(e);
}

JNIEXPORT void JNICALL Java_com_moonsworth_webosr_wrappers_WebEngine_invokeIteration0(
        JNIEnv *env, jobject self) {}

/* ---- Session --------------------------------------------------------------------------- */

JNIEXPORT jboolean JNICALL Java_com_moonsworth_webosr_wrappers_Session_isPersistent(
        JNIEnv *env, jobject self) {
    session_t *s = (session_t *) (intptr_t) get_handle(env, self);
    return s ? s->persistent : JNI_FALSE;
}

JNIEXPORT jstring JNICALL Java_com_moonsworth_webosr_wrappers_Session_name(JNIEnv *env, jobject self) {
    session_t *s = (session_t *) (intptr_t) get_handle(env, self);
    return (*env)->NewStringUTF(env, s ? s->name : "default");
}

JNIEXPORT void JNICALL Java_com_moonsworth_webosr_wrappers_Session_destroy(JNIEnv *env, jobject self) {
    session_t *s = (session_t *) (intptr_t) get_handle(env, self);
    if (!s) return;
    free(s->name);
    free(s);
}

/* ---- Browser --------------------------------------------------------------------------- */

#define BROWSER browser_t *b = (browser_t *) (intptr_t) get_handle(env, self)

JNIEXPORT void JNICALL Java_com_moonsworth_webosr_wrappers_Browser_load(JNIEnv *env, jobject self, jstring url) {
    BROWSER;
    if (!b || !url) return;
    const char *u = (*env)->GetStringUTFChars(env, url, NULL);
    free(b->url);
    b->url = strdup(u);
    (*env)->ReleaseStringUTFChars(env, url, u);
    if (getenv(TRACE_ENV)) fprintf(stderr, "[webosr] load %s\n", b->url);
}

JNIEXPORT jstring JNICALL Java_com_moonsworth_webosr_wrappers_Browser_url(JNIEnv *env, jobject self) {
    BROWSER;
    return (*env)->NewStringUTF(env, b && b->url ? b->url : "");
}

JNIEXPORT void JNICALL Java_com_moonsworth_webosr_wrappers_Browser_next(JNIEnv *env, jobject self) {}
JNIEXPORT void JNICALL Java_com_moonsworth_webosr_wrappers_Browser_previous(JNIEnv *env, jobject self) {}
JNIEXPORT void JNICALL Java_com_moonsworth_webosr_wrappers_Browser_reload(JNIEnv *env, jobject self) {}
JNIEXPORT void JNICALL Java_com_moonsworth_webosr_wrappers_Browser_stopLoad(JNIEnv *env, jobject self) {}
JNIEXPORT jboolean JNICALL Java_com_moonsworth_webosr_wrappers_Browser_focus(JNIEnv *env, jobject self) { return JNI_FALSE; }
JNIEXPORT void JNICALL Java_com_moonsworth_webosr_wrappers_Browser_triggerFocus(JNIEnv *env, jobject self, jboolean f) {}
JNIEXPORT jboolean JNICALL Java_com_moonsworth_webosr_wrappers_Browser_inputFocus(JNIEnv *env, jobject self) { return JNI_FALSE; }

JNIEXPORT void JNICALL Java_com_moonsworth_webosr_wrappers_Browser_triggerResize(
        JNIEnv *env, jobject self, jint w, jint h) {
    BROWSER;
    if (!b || (w == b->w && h == b->h)) return;
    gl_delete(env, b->tex);
    b->w = w;
    b->h = h;
    b->tex = blank_texture(env, w, h);
}

JNIEXPORT void JNICALL Java_com_moonsworth_webosr_wrappers_Browser_triggerMouseEvent(
        JNIEnv *env, jobject self, jint button, jint action, jint x, jint y, jint mods) {}
JNIEXPORT void JNICALL Java_com_moonsworth_webosr_wrappers_Browser_triggerKeyEvent(
        JNIEnv *env, jobject self, jstring key, jint code, jstring text, jint mods, jint action, jint native) {}
JNIEXPORT void JNICALL Java_com_moonsworth_webosr_wrappers_Browser_triggerScrollEvent(
        JNIEnv *env, jobject self, jint a, jint b, jint c, jint d, jint e) {}
JNIEXPORT void JNICALL Java_com_moonsworth_webosr_wrappers_Browser_consume(JNIEnv *env, jobject self) {}

JNIEXPORT jint JNICALL Java_com_moonsworth_webosr_wrappers_Browser_texture(JNIEnv *env, jobject self) {
    BROWSER;
    return b ? b->tex : 0;
}

JNIEXPORT void JNICALL Java_com_moonsworth_webosr_wrappers_Browser_scaleFactor(JNIEnv *env, jobject self, jdouble s) {}
JNIEXPORT jint JNICALL Java_com_moonsworth_webosr_wrappers_Browser_width(JNIEnv *env, jobject self) { BROWSER; return b ? b->w : 0; }
JNIEXPORT jint JNICALL Java_com_moonsworth_webosr_wrappers_Browser_height(JNIEnv *env, jobject self) { BROWSER; return b ? b->h : 0; }

/* Nothing evaluates, so every future is already settled and holds "undefined". */
JNIEXPORT jobject JNICALL Java_com_moonsworth_webosr_wrappers_Browser_eval(JNIEnv *env, jobject self, jstring js) {
    return make(env, PKG "wrappers/NativeFuture", calloc(1, 8));
}

JNIEXPORT void JNICALL Java_com_moonsworth_webosr_wrappers_Browser_evalNoResult(JNIEnv *env, jobject self, jstring js) {}
JNIEXPORT void JNICALL Java_com_moonsworth_webosr_wrappers_Browser_bindFunction(
        JNIEnv *env, jobject self, jstring name, jobject fn) {}
JNIEXPORT void JNICALL Java_com_moonsworth_webosr_wrappers_Browser_unbindFunction(JNIEnv *env, jobject self, jstring name) {}
JNIEXPORT void JNICALL Java_com_moonsworth_webosr_wrappers_Browser_createInspector(JNIEnv *env, jobject self) {}

JNIEXPORT void JNICALL Java_com_moonsworth_webosr_wrappers_Browser_destroy(JNIEnv *env, jobject self) {
    BROWSER;
    if (!b) return;
    gl_delete(env, b->tex);
    if (b->listener) (*env)->DeleteGlobalRef(env, b->listener);
    free(b->url);
    free(b);
}

/* ---- NativeFuture ---------------------------------------------------------------------- */

JNIEXPORT jboolean JNICALL Java_com_moonsworth_webosr_wrappers_NativeFuture_isUndefined(JNIEnv *env, jobject self) { return JNI_TRUE; }
JNIEXPORT jboolean JNICALL Java_com_moonsworth_webosr_wrappers_NativeFuture_isDone(JNIEnv *env, jobject self) { return JNI_TRUE; }
JNIEXPORT jstring JNICALL Java_com_moonsworth_webosr_wrappers_NativeFuture_get0(JNIEnv *env, jobject self, jlong nanos) {
    return (*env)->NewStringUTF(env, "undefined");
}
JNIEXPORT void JNICALL Java_com_moonsworth_webosr_wrappers_NativeFuture_destroy(JNIEnv *env, jobject self) {
    free((void *) (intptr_t) get_handle(env, self));
}

/* ---- PlatformInterface ----------------------------------------------------------------- */

JNIEXPORT void JNICALL Java_com_moonsworth_webosr_wrappers_PlatformInterface_setScrollCallback(
        JNIEnv *env, jobject self, jobject cb) {}
JNIEXPORT void JNICALL Java_com_moonsworth_webosr_wrappers_PlatformInterface_setCursor0(JNIEnv *env, jobject self, jint c) {}
JNIEXPORT void JNICALL Java_com_moonsworth_webosr_wrappers_PlatformInterface_destroy(JNIEnv *env, jobject self) {
    free((void *) (intptr_t) get_handle(env, self));
}

/* ---- PromiseJS (its handle is its own field, not NativeHandle's) ----------------------- */

JNIEXPORT void JNICALL Java_com_moonsworth_webosr_wrappers_PromiseJS_resolve0(JNIEnv *env, jobject self, jstring v) {}
JNIEXPORT void JNICALL Java_com_moonsworth_webosr_wrappers_PromiseJS_resolve1(JNIEnv *env, jobject self) {}
JNIEXPORT void JNICALL Java_com_moonsworth_webosr_wrappers_PromiseJS_reject0(JNIEnv *env, jobject self, jstring v) {}
JNIEXPORT void JNICALL Java_com_moonsworth_webosr_wrappers_PromiseJS_reject1(JNIEnv *env, jobject self) {}

/* ---- Image sources --------------------------------------------------------------------- */

#define IMAGE image_t *i = (image_t *) (intptr_t) get_handle(env, self)

JNIEXPORT jboolean JNICALL Java_com_moonsworth_webosr_wrappers_image_TexturedImageSource_acquire(JNIEnv *env, jobject self) { return JNI_TRUE; }
JNIEXPORT jint JNICALL Java_com_moonsworth_webosr_wrappers_image_TexturedImageSource_texture(JNIEnv *env, jobject self) { IMAGE; return i ? i->tex : 0; }
JNIEXPORT void JNICALL Java_com_moonsworth_webosr_wrappers_image_TexturedImageSource_present(JNIEnv *env, jobject self) {}

JNIEXPORT void JNICALL Java_com_moonsworth_webosr_wrappers_image_TexturedImageSource_size(
        JNIEnv *env, jobject self, jint w, jint h) {
    IMAGE;
    if (!i || (w == i->w && h == i->h)) return;
    gl_delete(env, i->tex);
    i->w = w;
    i->h = h;
    i->tex = blank_texture(env, w, h);
}

JNIEXPORT jobject JNICALL Java_com_moonsworth_webosr_wrappers_image_TexturedImageSource_makeChild(
        JNIEnv *env, jobject self, jstring name) {
    IMAGE;
    image_t *c = calloc(1, sizeof *c);
    if (i) *c = *i;
    return make(env, PKG "wrappers/image/ChildImageSource", c);
}

JNIEXPORT void JNICALL Java_com_moonsworth_webosr_wrappers_image_TexturedImageSource_uv(
        JNIEnv *env, jobject self, jint a, jint b, jint c, jint d) {}
JNIEXPORT void JNICALL Java_com_moonsworth_webosr_wrappers_image_TexturedImageSource_bind(JNIEnv *env, jobject self) {
    IMAGE;
    if (i) gl_bind(env, i->tex);
}
JNIEXPORT void JNICALL Java_com_moonsworth_webosr_wrappers_image_TexturedImageSource_unbind(JNIEnv *env, jobject self) {
    gl_bind(env, 0);
}
JNIEXPORT void JNICALL Java_com_moonsworth_webosr_wrappers_image_TexturedImageSource_destroy(JNIEnv *env, jobject self) {
    IMAGE;
    if (!i) return;
    gl_delete(env, i->tex);
    free(i);
}

/* A child shares its parent's texture, so destroying one must not delete the other's. */
JNIEXPORT void JNICALL Java_com_moonsworth_webosr_wrappers_image_ChildImageSource_bind(JNIEnv *env, jobject self) {
    IMAGE;
    if (i) gl_bind(env, i->tex);
}
JNIEXPORT void JNICALL Java_com_moonsworth_webosr_wrappers_image_ChildImageSource_unbind(JNIEnv *env, jobject self) {
    gl_bind(env, 0);
}
JNIEXPORT void JNICALL Java_com_moonsworth_webosr_wrappers_image_ChildImageSource_uv(
        JNIEnv *env, jobject self, jint a, jint b, jint c, jint d) {}
JNIEXPORT void JNICALL Java_com_moonsworth_webosr_wrappers_image_ChildImageSource_destroy(JNIEnv *env, jobject self) {
    free((void *) (intptr_t) get_handle(env, self));
}
