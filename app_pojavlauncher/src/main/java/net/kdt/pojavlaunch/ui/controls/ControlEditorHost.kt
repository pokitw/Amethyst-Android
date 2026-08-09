package net.kdt.pojavlaunch.ui.controls

import android.os.Handler
import android.os.Looper
import android.view.View
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import net.kdt.pojavlaunch.Tools
import net.kdt.pojavlaunch.customcontrols.ControlData
import net.kdt.pojavlaunch.customcontrols.ControlLayout
import net.kdt.pojavlaunch.customcontrols.buttons.ControlDrawer
import net.kdt.pojavlaunch.customcontrols.buttons.ControlInterface
import net.kdt.pojavlaunch.ui.theme.AmethystXTheme

/**
 * Drives the control editor's panel from the control layout.
 *
 * The layout is Java and knows nothing about Compose, so what it gets is open, close, and a nudge
 * when the button it is editing has moved. Everything else — which side the panel sits on, whether
 * the key picker is up, how a change reaches the button — happens in here.
 *
 * The visibility discipline is the one every `ComposeView` over a live surface in this app follows:
 * GONE unless it is showing something. The editor's surface is stretched over the whole layout, and
 * a control layout is a thing you drag buttons around on, so a view left VISIBLE and empty would
 * quietly swallow every drag. The delay before GONE is the exit animation's length.
 */
class ControlEditorHost(
    private val view: ComposeView,
    private val layout: ControlLayout
) : ControlEditorActions {

    private var state by mutableStateOf<ControlEditorState?>(null)
    /** Kept only so the panel has something to draw while it slides away. */
    private var lastState: ControlEditorState? = null
    private var onLeft by mutableStateOf(false)
    /** Which key slot the picker is filling, or -1 when it is not up. */
    private var pickingSlot by mutableStateOf(-1)

    private val handler = Handler(Looper.getMainLooper())

    init {
        view.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        view.setContent {
            AmethystXTheme {
                val editing = state
                Box(Modifier.fillMaxSize()) {
                    // Only ever drawn under the picker. The panel alone has no scrim, because the
                    // whole point of it is that the layout behind stays live while it is open.
                    AnimatedVisibility(
                        visible = pickingSlot >= 0,
                        enter = fadeIn(tween(180)),
                        exit = fadeOut(tween(160))
                    ) {
                        val interaction = remember { MutableInteractionSource() }
                        Box(
                            Modifier
                                .fillMaxSize()
                                .background(
                                    MaterialTheme.colorScheme.scrim.copy(alpha = 0.5f)
                                )
                                .clickable(interaction, indication = null) { pickingSlot = -1 }
                        )
                    }

                    AnimatedVisibility(
                        visible = editing != null,
                        enter = slideInHorizontally(tween(260, easing = FastOutSlowInEasing)) {
                            if (onLeft) -it else it
                        } + fadeIn(tween(160)),
                        exit = slideOutHorizontally(tween(220, easing = FastOutSlowInEasing)) {
                            if (onLeft) -it else it
                        } + fadeOut(tween(160))
                    ) {
                        // Held through the exit so the panel does not empty itself mid-slide.
                        val shown = editing ?: lastState
                        if (shown != null) {
                            ControlEditorPanel(shown, onLeft, this@ControlEditorHost) { slot ->
                                pickingSlot = slot
                            }
                        }
                    }

                    AnimatedVisibility(
                        visible = pickingSlot >= 0,
                        modifier = Modifier.align(Alignment.BottomCenter),
                        enter = slideInVertically(tween(280, easing = FastOutSlowInEasing)) { it } +
                                fadeIn(tween(160)),
                        exit = slideOutVertically(tween(220, easing = FastOutSlowInEasing)) { it } +
                                fadeOut(tween(160))
                    ) {
                        val slot = pickingSlot.coerceAtLeast(0)
                        val editingNow = editing
                        KeyPicker(
                            slot = slot,
                            current = editingNow?.keys?.getOrNull(slot) ?: NO_KEY,
                            onPick = { keycode ->
                                editingNow?.setKey(slot, keycode)
                                pickingSlot = -1
                            },
                            onClear = {
                                editingNow?.setKey(slot, NO_KEY)
                                pickingSlot = -1
                            },
                            onDismiss = { pickingSlot = -1 }
                        )
                    }
                }
            }
        }
    }

    fun isOpen(): Boolean = state != null

    fun open(button: ControlInterface) {
        pickingSlot = -1
        val editor = ControlEditorState(button)
        state = editor
        lastState = editor
        reposition()
        view.visibility = View.VISIBLE
    }

    /**
     * Put the panel on the side the button is not.
     *
     * Called again whenever the button is dragged, because a panel that stayed put would end up
     * under the thing being edited — which is the one place it must never be.
     */
    fun reposition() {
        val button = state?.button ?: return
        val control = button.controlView
        val centre = control.x + control.width / 2f
        onLeft = centre >= Tools.currentDisplayMetrics.widthPixels / 2f
    }

    /**
     * Close one layer.
     *
     * @return true when the panel itself closed, which is what tells the layout to drop the
     *         selection handles with it
     */
    fun dismissLayer(): Boolean {
        if (pickingSlot >= 0) {
            pickingSlot = -1
            return false
        }
        close()
        return true
    }

    fun close() {
        if (state == null) return
        pickingSlot = -1
        state = null
        handler.postDelayed({
            if (state == null) {
                view.visibility = View.GONE
                lastState = null
            }
        }, EXIT_MS)
    }

    /* ControlEditorActions — what the panel's footer asks for. */

    override fun onDuplicate() {
        state?.button?.cloneButton()
        close()
    }

    override fun onDelete() {
        state?.button?.removeButton()
        close()
    }

    override fun onAddSubButton() {
        val drawer = state?.button as? ControlDrawer ?: return
        layout.addSubButton(drawer, ControlData())
    }

    override fun onClose() {
        close()
    }

    private companion object {
        const val EXIT_MS = 240L
    }
}
