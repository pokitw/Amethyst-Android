//
// Draws the virtual mouse into recorded frames.
//
// The pointer is an Android view sitting above the game's surface, so it never reaches the
// framebuffer the recorder captures. This composites it back in on the encoder side, which keeps
// it out of what the player sees while putting it in the video where it is expected.
//

#ifndef POJAVLAUNCHER_GL_OVERLAY_H
#define POJAVLAUNCHER_GL_OVERLAY_H

#include <stdbool.h>
#include <stdint.h>

/** Resolves a GL entry point; supplied by the recorder so both use the same renderer's GL. */
typedef void* (*gl_overlay_resolver_t)(const char* name);

/** Takes a copy of the pointer artwork as straight-alpha RGBA. Safe to call from any thread. */
void gl_overlay_set_bitmap(const uint8_t* rgba, int width, int height);

/** Whether the pointer is on screen, and how large it is in game framebuffer pixels. */
void gl_overlay_set_state(bool visible, float width_px, float height_px);

/**
 * Draws the pointer over the frame just blitted into the encoder surface. Render thread only,
 * with the recorder's context current. Does nothing until the artwork has arrived, and disables
 * itself for the rest of the session if the GPU cannot honour it.
 *
 * The game rectangle describes where the frame landed inside the encoder surface, so the pointer
 * follows the same letterboxing.
 */
void gl_overlay_draw(gl_overlay_resolver_t resolver,
                     int surface_width, int surface_height,
                     int game_width, int game_height,
                     int offset_x, int offset_y, int fit_width, int fit_height,
                     double cursor_x, double cursor_y);

/** Drops the GL objects. Render thread only, with the recorder's context current. */
void gl_overlay_release(void);

#endif //POJAVLAUNCHER_GL_OVERLAY_H
