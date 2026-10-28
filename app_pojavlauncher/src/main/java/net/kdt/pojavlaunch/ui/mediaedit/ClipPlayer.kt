package net.kdt.pojavlaunch.ui.mediaedit

import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.os.Build
import android.util.Log
import android.view.Surface
import android.view.TextureView
import java.io.File

/**
 * Playback for the clip editor, on the platform's own player.
 *
 * There is no ExoPlayer and no media3 in this build, and adding one would ship a whole player
 * with it because release builds do not run R8. `MediaPlayer` and a `TextureView` are already in
 * the platform, they cost nothing, and what this screen needs is the easy half of what a player
 * does: decode one local file, seek, and report where it is.
 *
 * **A `TextureView` rather than a `SurfaceView`**, because a `SurfaceView` punches a hole through
 * the window and is composited behind everything, so the timeline and the toolbar drawn over the
 * preview would be the things that disappear. A `TextureView` is an ordinary view in the
 * hierarchy and can be drawn over.
 *
 * **It owns nothing about the edit.** It plays the file as it is on disk and reports a position;
 * which parts of it the player is allowed to be in is the editor's business, and the editor acts
 * on that by seeking. Putting the skip logic in here would mean this class had to be told about
 * cuts, and then two things would decide what the output is.
 */
class ClipPlayer(
    private val onPosition: (Long) -> Unit,
    private val onReady: (Long) -> Unit,
    private val onEnded: () -> Unit,
    /** The player gave up on this file. Nothing will play until another one is opened. */
    private val onFailed: () -> Unit = {},
) {

    private var player: MediaPlayer? = null
    private var surface: Surface? = null
    private var pending: File? = null
    private var prepared = false

    /** Whether the player is running, which is not the same as whether the user asked it to. */
    val playing: Boolean
        get() = runCatching { player?.isPlaying == true }.getOrDefault(false)

    /** Video size, so the preview can be given the right shape before the first frame. */
    var videoWidth = 0
        private set
    var videoHeight = 0
        private set

    /**
     * A listener for the preview's `TextureView`.
     *
     * The surface arrives and departs on its own schedule, independently of the file being
     * chosen, so both are remembered and the player is only built once both exist.
     */
    fun listener(): TextureView.SurfaceTextureListener =
        object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(st: SurfaceTexture, w: Int, h: Int) {
                surface = Surface(st)
                player?.setSurface(surface) ?: pending?.let { open(it) }
            }

            override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, w: Int, h: Int) = Unit

            override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
                // The player is kept: the composition is rebuilt on every rotation and rebuilding
                // the decoder each time would restart the clip from the beginning.
                runCatching { player?.setSurface(null) }
                surface?.release()
                surface = null
                return true
            }

            override fun onSurfaceTextureUpdated(st: SurfaceTexture) = Unit
        }

    /** Point the player at a file. Safe to call before the surface exists. */
    fun open(file: File) {
        pending = file
        val target = surface ?: return
        release()
        prepared = false
        val created = MediaPlayer()
        player = created
        try {
            created.setDataSource(file.absolutePath)
            created.setSurface(target)
            created.setOnPreparedListener {
                prepared = true
                videoWidth = it.videoWidth
                videoHeight = it.videoHeight
                onReady(it.duration.toLong() * 1000L)
            }
            created.setOnCompletionListener { onEnded() }
            created.setOnErrorListener { _, what, extra ->
                Log.w(TAG, "player error $what/$extra on ${file.name}")
                prepared = false
                onFailed()
                true
            }
            created.prepareAsync()
        } catch (t: Throwable) {
            Log.w(TAG, "could not open ${file.name}", t)
            release()
            onFailed()
        }
    }

    /**
     * Start playing, and say whether it actually started.
     *
     * The host decides from this whether to show a pause icon and run a position ticker, and
     * the answer is genuinely no twice: before `prepareAsync` has called back, which is a real
     * window because the editor enables its transport as soon as the file is read, and forever
     * after an error, which leaves the player in a state where `start()` does nothing. Both
     * used to end with a pause icon over a frozen picture and a coroutine waking every 60ms.
     */
    fun play(): Boolean {
        if (!prepared) return false
        return runCatching {
            player?.start()
            true
        }.getOrDefault(false)
    }

    fun pause() {
        runCatching { player?.pause() }
    }

    /**
     * Move the playhead.
     *
     * Exact seeking only exists from API 26. Below it the player lands on the nearest sync
     * frame, which is the same two second grid the editor's own start handle snaps to, so the
     * preview and the rule it is showing degrade together rather than disagreeing.
     */
    fun seekTo(positionUs: Long) {
        if (!prepared) return
        val ms = (positionUs / 1000L).coerceAtLeast(0L)
        runCatching {
            val live = player ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                live.seekTo(ms, MediaPlayer.SEEK_CLOSEST)
            } else {
                @Suppress("DEPRECATION")
                live.seekTo(ms.toInt())
            }
        }
    }

    /** Where the player is now, or -1 when it cannot say. */
    fun position(): Long = runCatching {
        if (!prepared) -1L else (player?.currentPosition ?: 0).toLong() * 1000L
    }.getOrDefault(-1L)

    /** Called from the editor's ticker while playing. */
    fun pump() {
        val at = position()
        if (at >= 0L) onPosition(at)
    }

    fun release() {
        runCatching {
            player?.setOnPreparedListener(null)
            player?.setOnCompletionListener(null)
            player?.setOnErrorListener(null)
            player?.reset()
            player?.release()
        }
        player = null
        prepared = false
    }

    /** Called when the editor is finished with it entirely. */
    fun dispose() {
        release()
        surface?.release()
        surface = null
        pending = null
    }

    private companion object {
        const val TAG = "ClipPlayer"
    }
}
