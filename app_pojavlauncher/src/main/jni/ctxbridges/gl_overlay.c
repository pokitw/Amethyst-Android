//
// Draws the virtual mouse into recorded frames. See gl_overlay.h.
//

#include <GLES3/gl3.h>
#include <pthread.h>
#include <stdatomic.h>
#include <stdlib.h>
#include <string.h>

#include "gl_overlay.h"

#define TAG "GLOverlay"
#include <log.h>

/*
 * Written for GLSL ES 1.00, which every GLES 2 and GLES 3 context accepts. Desktop GL takes it
 * as 1.10, where precision qualifiers do not exist, hence the guard.
 */
static const char* VERTEX_SOURCE =
        "attribute vec2 aPosition;\n"
        "attribute vec2 aTexCoord;\n"
        "varying vec2 vTexCoord;\n"
        "void main() {\n"
        "  vTexCoord = aTexCoord;\n"
        "  gl_Position = vec4(aPosition, 0.0, 1.0);\n"
        "}\n";

static const char* FRAGMENT_SOURCE =
        "#ifdef GL_ES\n"
        "precision mediump float;\n"
        "#endif\n"
        "varying vec2 vTexCoord;\n"
        "uniform sampler2D uTexture;\n"
        "void main() {\n"
        "  gl_FragColor = texture2D(uTexture, vTexCoord);\n"
        "}\n";

/* Artwork handed over from Java, uploaded on the render thread when it next draws. */
static pthread_mutex_t overlay_mutex = PTHREAD_MUTEX_INITIALIZER;
static uint8_t* pending_pixels;
static int pending_width;
static int pending_height;

static atomic_bool overlay_visible;
/* Size of the pointer in game framebuffer pixels, mirroring what the on-screen view uses. */
static atomic_int overlay_width_px;
static atomic_int overlay_height_px;

/* Render thread only. */
static bool overlay_failed;
static GLuint overlay_program;
static GLuint overlay_texture;
static GLuint overlay_buffer;
static GLint overlay_position_attrib = -1;
static GLint overlay_texcoord_attrib = -1;
static GLint overlay_texture_uniform = -1;
static int overlay_texture_width;
static int overlay_texture_height;

static void (*glActiveTexture_p)(GLenum);
static void (*glAttachShader_p)(GLuint, GLuint);
static void (*glBindBuffer_p)(GLenum, GLuint);
static void (*glBindTexture_p)(GLenum, GLuint);
static void (*glBlendFunc_p)(GLenum, GLenum);
static void (*glBufferData_p)(GLenum, GLsizeiptr, const void*, GLenum);
static void (*glCompileShader_p)(GLuint);
static GLuint (*glCreateProgram_p)(void);
static GLuint (*glCreateShader_p)(GLenum);
static void (*glDeleteBuffers_p)(GLsizei, const GLuint*);
static void (*glDeleteProgram_p)(GLuint);
static void (*glDeleteShader_p)(GLuint);
static void (*glDeleteTextures_p)(GLsizei, const GLuint*);
static void (*glDisableVertexAttribArray_p)(GLuint);
static void (*glDrawArrays_p)(GLenum, GLint, GLsizei);
static void (*glEnable_p)(GLenum);
static void (*glEnableVertexAttribArray_p)(GLuint);
static void (*glGenBuffers_p)(GLsizei, GLuint*);
static void (*glGenTextures_p)(GLsizei, GLuint*);
static GLint (*glGetAttribLocation_p)(GLuint, const GLchar*);
static void (*glGetProgramiv_p)(GLuint, GLenum, GLint*);
static void (*glGetShaderInfoLog_p)(GLuint, GLsizei, GLsizei*, GLchar*);
static void (*glGetShaderiv_p)(GLuint, GLenum, GLint*);
static GLint (*glGetUniformLocation_p)(GLuint, const GLchar*);
static void (*glLinkProgram_p)(GLuint);
static void (*glShaderSource_p)(GLuint, GLsizei, const GLchar* const*, const GLint*);
static void (*glTexImage2D_p)(GLenum, GLint, GLint, GLsizei, GLsizei, GLint, GLenum, GLenum, const void*);
static void (*glTexParameteri_p)(GLenum, GLenum, GLint);
static void (*glUniform1i_p)(GLint, GLint);
static void (*glUseProgram_p)(GLuint);
static void (*glVertexAttribPointer_p)(GLuint, GLint, GLenum, GLboolean, GLsizei, const void*);
static void (*glViewport_p)(GLint, GLint, GLsizei, GLsizei);

void gl_overlay_set_bitmap(const uint8_t* rgba, int width, int height) {
    if (rgba == NULL || width <= 0 || height <= 0) return;
    size_t size = (size_t) width * (size_t) height * 4u;
    uint8_t* copy = malloc(size);
    if (copy == NULL) return;
    memcpy(copy, rgba, size);

    pthread_mutex_lock(&overlay_mutex);
    free(pending_pixels);
    pending_pixels = copy;
    pending_width = width;
    pending_height = height;
    pthread_mutex_unlock(&overlay_mutex);
}

void gl_overlay_set_state(bool visible, float width_px, float height_px) {
    atomic_store_explicit(&overlay_visible, visible, memory_order_relaxed);
    if (width_px > 0 && height_px > 0) {
        atomic_store_explicit(&overlay_width_px, (int) width_px, memory_order_relaxed);
        atomic_store_explicit(&overlay_height_px, (int) height_px, memory_order_relaxed);
    }
}

static bool overlay_resolve(gl_overlay_resolver_t resolver) {
    if (glDrawArrays_p != NULL) return true;
#define RESOLVE(name) name##_p = resolver(#name); if (name##_p == NULL) return false;
    RESOLVE(glActiveTexture)
    RESOLVE(glAttachShader)
    RESOLVE(glBindBuffer)
    RESOLVE(glBindTexture)
    RESOLVE(glBlendFunc)
    RESOLVE(glBufferData)
    RESOLVE(glCompileShader)
    RESOLVE(glCreateProgram)
    RESOLVE(glCreateShader)
    RESOLVE(glDeleteBuffers)
    RESOLVE(glDeleteProgram)
    RESOLVE(glDeleteShader)
    RESOLVE(glDeleteTextures)
    RESOLVE(glDisableVertexAttribArray)
    RESOLVE(glEnable)
    RESOLVE(glEnableVertexAttribArray)
    RESOLVE(glGenBuffers)
    RESOLVE(glGenTextures)
    RESOLVE(glGetAttribLocation)
    RESOLVE(glGetProgramiv)
    RESOLVE(glGetShaderiv)
    RESOLVE(glGetUniformLocation)
    RESOLVE(glLinkProgram)
    RESOLVE(glShaderSource)
    RESOLVE(glTexImage2D)
    RESOLVE(glTexParameteri)
    RESOLVE(glUniform1i)
    RESOLVE(glUseProgram)
    RESOLVE(glVertexAttribPointer)
    RESOLVE(glViewport)
    RESOLVE(glDrawArrays)
#undef RESOLVE
    // Only used to report a compile failure, so its absence is not fatal.
    glGetShaderInfoLog_p = resolver("glGetShaderInfoLog");
    return true;
}

static GLuint overlay_compile(GLenum type, const char* source) {
    GLuint shader = glCreateShader_p(type);
    if (shader == 0) return 0;
    glShaderSource_p(shader, 1, &source, NULL);
    glCompileShader_p(shader);
    GLint compiled = 0;
    glGetShaderiv_p(shader, GL_COMPILE_STATUS, &compiled);
    if (!compiled) {
        if (glGetShaderInfoLog_p != NULL) {
            char log[512];
            glGetShaderInfoLog_p(shader, sizeof(log), NULL, log);
            LOGE("Pointer shader did not compile: %s", log);
        } else {
            LOGE("Pointer shader did not compile");
        }
        glDeleteShader_p(shader);
        return 0;
    }
    return shader;
}

static bool overlay_build_program(void) {
    if (overlay_program != 0) return true;
    GLuint vertex = overlay_compile(GL_VERTEX_SHADER, VERTEX_SOURCE);
    if (vertex == 0) return false;
    GLuint fragment = overlay_compile(GL_FRAGMENT_SHADER, FRAGMENT_SOURCE);
    if (fragment == 0) {
        glDeleteShader_p(vertex);
        return false;
    }

    GLuint program = glCreateProgram_p();
    if (program == 0) {
        glDeleteShader_p(vertex);
        glDeleteShader_p(fragment);
        return false;
    }
    glAttachShader_p(program, vertex);
    glAttachShader_p(program, fragment);
    glLinkProgram_p(program);
    // Attached shaders live on inside the program once it is linked.
    glDeleteShader_p(vertex);
    glDeleteShader_p(fragment);

    GLint linked = 0;
    glGetProgramiv_p(program, GL_LINK_STATUS, &linked);
    if (!linked) {
        LOGE("Pointer shader program did not link");
        glDeleteProgram_p(program);
        return false;
    }

    overlay_position_attrib = glGetAttribLocation_p(program, "aPosition");
    overlay_texcoord_attrib = glGetAttribLocation_p(program, "aTexCoord");
    overlay_texture_uniform = glGetUniformLocation_p(program, "uTexture");
    if (overlay_position_attrib < 0 || overlay_texcoord_attrib < 0) {
        LOGE("Pointer shader is missing its inputs");
        glDeleteProgram_p(program);
        return false;
    }

    glGenBuffers_p(1, &overlay_buffer);
    if (overlay_buffer == 0) {
        glDeleteProgram_p(program);
        return false;
    }
    overlay_program = program;
    return true;
}

/** Uploads artwork handed over since the last frame. */
static bool overlay_sync_texture(void) {
    uint8_t* pixels = NULL;
    int width = 0, height = 0;

    pthread_mutex_lock(&overlay_mutex);
    if (pending_pixels != NULL) {
        pixels = pending_pixels;
        width = pending_width;
        height = pending_height;
        pending_pixels = NULL;
    }
    pthread_mutex_unlock(&overlay_mutex);

    if (pixels != NULL) {
        if (overlay_texture == 0) glGenTextures_p(1, &overlay_texture);
        if (overlay_texture != 0) {
            glActiveTexture_p(GL_TEXTURE0);
            glBindTexture_p(GL_TEXTURE_2D, overlay_texture);
            glTexParameteri_p(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
            glTexParameteri_p(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
            glTexParameteri_p(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
            glTexParameteri_p(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
            glTexImage2D_p(GL_TEXTURE_2D, 0, GL_RGBA, width, height, 0, GL_RGBA,
                           GL_UNSIGNED_BYTE, pixels);
            overlay_texture_width = width;
            overlay_texture_height = height;
        }
        free(pixels);
    }
    return overlay_texture != 0 && overlay_texture_width > 0;
}

void gl_overlay_draw(gl_overlay_resolver_t resolver,
                     int surface_width, int surface_height,
                     int game_width, int game_height,
                     int offset_x, int offset_y, int fit_width, int fit_height,
                     double cursor_x, double cursor_y) {
    if (overlay_failed) return;
    if (!atomic_load_explicit(&overlay_visible, memory_order_relaxed)) return;
    if (surface_width <= 0 || surface_height <= 0 || game_width <= 0 || game_height <= 0) return;

    if (!overlay_resolve(resolver) || !overlay_build_program()) {
        LOGW("The pointer cannot be drawn on this renderer, recording without it");
        overlay_failed = true;
        return;
    }
    if (!overlay_sync_texture()) return; // artwork has not arrived yet

    int pointer_width = atomic_load_explicit(&overlay_width_px, memory_order_relaxed);
    int pointer_height = atomic_load_explicit(&overlay_height_px, memory_order_relaxed);
    if (pointer_width <= 0 || pointer_height <= 0) return;

    /*
     * The pointer's position and size are in game framebuffer pixels with the origin at the top
     * left, the same space the on-screen view works in. The frame was scaled into the encoder
     * surface, so the pointer follows it, and the vertical axis flips because GL counts from the
     * bottom.
     */
    double scale_x = (double) fit_width / game_width;
    double scale_y = (double) fit_height / game_height;
    double left = offset_x + cursor_x * scale_x;
    double right = offset_x + (cursor_x + pointer_width) * scale_x;
    double bottom = offset_y + (game_height - (cursor_y + pointer_height)) * scale_y;
    double top = offset_y + (game_height - cursor_y) * scale_y;

    // Entirely outside the frame, so there is nothing to draw.
    if (right <= offset_x || left >= offset_x + fit_width) return;
    if (top <= offset_y || bottom >= offset_y + fit_height) return;

    float x0 = (float) (2.0 * left / surface_width - 1.0);
    float x1 = (float) (2.0 * right / surface_width - 1.0);
    float y0 = (float) (2.0 * bottom / surface_height - 1.0);
    float y1 = (float) (2.0 * top / surface_height - 1.0);

    // Triangle strip: position then texture coordinate, with V flipped so the artwork is upright.
    const float vertices[] = {
            x0, y0, 0.0f, 1.0f,
            x1, y0, 1.0f, 1.0f,
            x0, y1, 0.0f, 0.0f,
            x1, y1, 1.0f, 0.0f,
    };

    glViewport_p(0, 0, surface_width, surface_height);
    glUseProgram_p(overlay_program);
    glActiveTexture_p(GL_TEXTURE0);
    glBindTexture_p(GL_TEXTURE_2D, overlay_texture);
    if (overlay_texture_uniform >= 0) glUniform1i_p(overlay_texture_uniform, 0);

    glBindBuffer_p(GL_ARRAY_BUFFER, overlay_buffer);
    glBufferData_p(GL_ARRAY_BUFFER, sizeof(vertices), vertices, GL_STREAM_DRAW);
    glEnableVertexAttribArray_p((GLuint) overlay_position_attrib);
    glVertexAttribPointer_p((GLuint) overlay_position_attrib, 2, GL_FLOAT, GL_FALSE,
                            4 * sizeof(float), (const void*) 0);
    glEnableVertexAttribArray_p((GLuint) overlay_texcoord_attrib);
    glVertexAttribPointer_p((GLuint) overlay_texcoord_attrib, 2, GL_FLOAT, GL_FALSE,
                            4 * sizeof(float), (const void*) (2 * sizeof(float)));

    // The artwork carries straight alpha, so the usual over operator applies.
    glEnable_p(GL_BLEND);
    glBlendFunc_p(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
    glDrawArrays_p(GL_TRIANGLE_STRIP, 0, 4);

    glDisableVertexAttribArray_p((GLuint) overlay_position_attrib);
    glDisableVertexAttribArray_p((GLuint) overlay_texcoord_attrib);
    glBindBuffer_p(GL_ARRAY_BUFFER, 0);
    glUseProgram_p(0);
}

void gl_overlay_release(void) {
    /*
     * Deliberately no GL calls here. This runs as the recorder's context is being torn down,
     * with the game's context current, and the objects below belong to the dying context, so
     * the driver frees them with it. Touching GL at this point would either do nothing useful
     * or, worse, land on the game's context.
     */
    overlay_program = 0;
    overlay_texture = 0;
    overlay_buffer = 0;
    overlay_position_attrib = -1;
    overlay_texcoord_attrib = -1;
    overlay_texture_uniform = -1;
    overlay_texture_width = 0;
    overlay_texture_height = 0;
    overlay_failed = false;

    pthread_mutex_lock(&overlay_mutex);
    free(pending_pixels);
    pending_pixels = NULL;
    pthread_mutex_unlock(&overlay_mutex);
}
