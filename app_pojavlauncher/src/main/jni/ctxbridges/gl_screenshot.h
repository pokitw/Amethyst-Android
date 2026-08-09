//
// In-pipeline screenshots, taken from the frame the game is about to present.
//
// The on-screen controls, the virtual cursor and the system bars are Android views sitting above
// the game's surface, so they never reach the framebuffer the renderer draws into. Capturing here
// rather than from the display is what keeps them out of the picture.
//
// Unlike the recorder, both bridges are covered. The EGL bridge reads the back buffer through a
// context of its own; OSMesa renders into a CPU buffer that is already sitting in memory when the
// frame is presented, so it is simply copied. That means a screenshot works on vulkan_zink, where
// recording cannot (see the handbook, §17).
//

#ifndef POJAVLAUNCHER_GL_SCREENSHOT_H
#define POJAVLAUNCHER_GL_SCREENSHOT_H

#include <EGL/egl.h>

#include "gl_bridge.h"

/**
 * Capture the frame that is about to be presented, if one has been asked for.
 *
 * Render thread only, with the game context current and immediately before the game's own
 * eglSwapBuffers(), because the back buffer is undefined once that returns. Costs a single
 * relaxed atomic load when nobody is waiting for a screenshot, which is almost always.
 */
void gl_screenshot_frame(EGLDisplay display, gl_render_window_t* bundle);

/**
 * The same, for the OSMesa bridge.
 *
 * Must be called while the window buffer is locked and after the glFinish() that pushes OSMesa's
 * last rendering into it — that is the only window in which those pixels exist.
 *
 * @param pixels the locked window buffer, RGBX_8888 and already top-down
 * @param width  its width in pixels
 * @param height its height in pixels
 * @param stride its stride in *pixels*, as ANativeWindow_Buffer reports it
 */
void gl_screenshot_frame_cpu(const void* pixels, int width, int height, int stride);

#endif //POJAVLAUNCHER_GL_SCREENSHOT_H
