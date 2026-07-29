//
// In-game screen recorder, plugged into the EGL rendering bridge.
//

#ifndef POJAVLAUNCHER_GL_RECORDER_H
#define POJAVLAUNCHER_GL_RECORDER_H

#include <EGL/egl.h>
#include "gl_bridge.h"

/**
 * Capture the frame that is about to be presented.
 * Must be called on the render thread, with the game context current and right before the
 * game's own eglSwapBuffers(), because the back buffer becomes undefined after the swap.
 * Costs a single atomic load when no recording is running.
 */
void gl_recorder_frame(EGLDisplay display, gl_render_window_t* bundle);

#endif //POJAVLAUNCHER_GL_RECORDER_H
