package net.kdt.pojavlaunch.ui.game

import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.dp
import net.kdt.pojavlaunch.customcontrols.ControlTestBridge
import net.kdt.pojavlaunch.ui.controls.keyDisplayName
import net.kdt.pojavlaunch.ui.theme.AmethystXTheme

/**
 * What your buttons are sending, while you are actually playing.
 *
 * <b>The same seam as the editor's test session, in its other mode.</b> That one swallows the
 * press, because there is no game behind it and the senders are native symbols that do not exist
 * in the launcher process; this one lets every press through untouched and only watches it go by.
 * One flag on [ControlTestBridge.attach] is the whole difference, which is why the bridge takes
 * one rather than always swallowing.
 *
 * <p>It answers the question the game itself cannot. In a world, a button bound to the wrong key
 * tells you by making the wrong thing happen, and you work backwards from that; here the key is
 * named as it goes. That is most useful in exactly the situation this ships alongside, which is a
 * layout being tried out for the first time.
 *
 * <p>A strip rather than a panel, drawn top-left where Minecraft's own debug text would go, with
 * no controls on it at all. Anything tappable up here would be a thing to hit by accident while
 * playing, and the way to turn it off is the control center, which is where it was turned on.
 */
class ControlDebugHost(private val view: ComposeView) : ControlTestBridge.Listener {

    private var held by mutableStateOf(listOf<Int>())
    private var showing = false

    init {
        view.setViewCompositionStrategy(
            ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed
        )
        view.setContent { AmethystXTheme { ControlDebugStrip(held) } }
    }

    fun isShowing(): Boolean = showing

    fun toggle() {
        if (showing) hide() else show()
    }

    fun show() {
        if (showing) return
        showing = true
        held = listOf()
        // false: watch, never swallow. A debug overlay that ate the presses it was reporting
        // would be the single worst bug this file could have.
        ControlTestBridge.attach(this, false)
        view.visibility = View.VISIBLE
    }

    fun hide() {
        if (!showing) return
        showing = false
        ControlTestBridge.detach(this)
        view.visibility = View.GONE
        held = listOf()
    }

    /** For the activity going away: the seam is a static and must never outlive its listener. */
    fun release() {
        ControlTestBridge.detach(this)
        showing = false
    }

    override fun onControlKey(keycode: Int, isDown: Boolean) {
        held = if (isDown) {
            if (held.contains(keycode)) held else held + keycode
        } else {
            held.filter { it != keycode }
        }
    }
}

@Composable
private fun ControlDebugStrip(held: List<Int>) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier
            .padding(start = 10.dp, top = 10.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(colors.surfaceContainer.copy(alpha = 0.82f))
            .padding(horizontal = 10.dp, vertical = 7.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (held.isEmpty()) {
            Text(
                "controls",
                style = MaterialTheme.typography.labelSmall,
                color = colors.onSurfaceVariant
            )
        } else {
            held.take(8).forEach { keycode ->
                Text(
                    keyDisplayName(keycode),
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.onPrimaryContainer,
                    maxLines = 1,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(colors.primaryContainer)
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
        }
    }
}
