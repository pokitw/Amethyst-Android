package net.kdt.pojavlaunch.ui.game

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import net.kdt.pojavlaunch.R

/**
 * What the voice overlay is showing.
 *
 * @param listening whether the microphone is live
 * @param text the transcript so far, empty before the first word is recognised
 * @param level how loud the speaker is, 0..1
 * @param messageRes a line to show instead of a transcript — the error that stopped a dictation,
 *                   or the reason one could not start — or 0 for none
 */
data class VoiceUiState(
    val listening: Boolean = false,
    val text: String = "",
    val level: Float = 0f,
    val messageRes: Int = 0
) {
    val showing: Boolean get() = listening || messageRes != 0
}

/**
 * The dictation overlay: one small card at the bottom of the screen.
 *
 * Deliberately the least of the in-game surfaces. A player dictating is looking at the chat box
 * the words are landing in, not at this — so it says only what the chat box cannot: that the
 * microphone is live, and that it can hear them. Everything about it follows from that.
 *
 * It is bottom-centred and no wider than it needs to be, so the game stays visible around it. It
 * has no scrim, because there is nothing to dismiss. And apart from the single stop control it
 * holds nothing tappable, so touches over it fall through to the controls underneath — the same
 * property the recording pill relies on.
 *
 * The one piece of ornament is the ring around the microphone, which breathes with the input
 * level. It is not decoration: a dictation that silently hears nothing looks exactly like one that
 * is working, and this is what tells the two apart before the player has finished a sentence.
 */
@Composable
fun VoiceOverlay(state: VoiceUiState, onStop: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    // Damped, because the raw RMS from the recogniser jitters hard enough to read as a fault.
    val level by animateFloatAsState(
        targetValue = if (state.listening) state.level.coerceIn(0f, 1f) else 0f,
        animationSpec = tween(160, easing = FastOutSlowInEasing),
        label = "voiceLevel"
    )

    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
        AnimatedVisibility(
            visible = state.showing,
            enter = slideInVertically(tween(240, easing = FastOutSlowInEasing)) { it / 2 } +
                    fadeIn(tween(180)),
            exit = slideOutVertically(tween(200, easing = FastOutSlowInEasing)) { it / 2 } +
                    fadeOut(tween(160))
        ) {
            Surface(
                color = colors.surfaceContainer,
                shape = RoundedCornerShape(22.dp),
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 14.dp)
                    .widthIn(max = 520.dp)
            ) {
                Row(
                    Modifier.padding(start = 10.dp, top = 10.dp, end = 6.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    MicIndicator(listening = state.listening, level = level)
                    Spacer(Modifier.width(12.dp))
                    Column(
                        Modifier.weight(1f, fill = false),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        if (state.messageRes != 0) {
                            Text(
                                stringResource(state.messageRes),
                                style = MaterialTheme.typography.titleSmall,
                                color = colors.error
                            )
                        } else if (state.text.isEmpty()) {
                            Text(
                                stringResource(R.string.voice_listening),
                                style = MaterialTheme.typography.titleSmall,
                                color = colors.onSurface
                            )
                            Text(
                                stringResource(R.string.voice_hint),
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.onSurfaceVariant
                            )
                        } else {
                            Text(
                                state.text,
                                style = MaterialTheme.typography.titleSmall,
                                color = colors.onSurface,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    if (state.listening) {
                        Box(
                            Modifier
                                .clip(CircleShape)
                                .clickable(onClick = onStop)
                                .padding(10.dp)
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = stringResource(R.string.voice_stop),
                                tint = colors.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    } else {
                        Spacer(Modifier.width(4.dp))
                    }
                }
            }
        }
    }
}

/** The microphone, in a well that swells with the speaker's own voice. */
@Composable
private fun MicIndicator(listening: Boolean, level: Float) {
    val colors = MaterialTheme.colorScheme
    Box(contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(38.dp)
                // Scaled rather than resized: this runs every frame the level changes, and a size
                // change would relayout the whole row behind it while text is being written into
                // it. A scale is a draw-time transform and touches nothing else.
                .scale(1f + level * 0.34f)
                .clip(CircleShape)
                .background(
                    if (listening) colors.primary.copy(alpha = 0.20f)
                    else Color.Transparent
                )
        )
        Box(
            Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(colors.surfaceContainerHighest),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painterResource(R.drawable.ic_ctrl_mic),
                contentDescription = null,
                tint = if (listening) colors.primary else colors.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
