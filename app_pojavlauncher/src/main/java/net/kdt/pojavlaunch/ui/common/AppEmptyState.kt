package net.kdt.pojavlaunch.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import net.kdt.pojavlaunch.ui.theme.SlotWell

/**
 * A list with nothing in it, said properly.
 *
 * An empty state is the first thing a new profile shows and the last thing a fruitless search
 * shows, so it is designed rather than left as a sentence in a card. Three parts, always: the
 * screen's own subject glyph in an inventory slot, what happened in `titleMedium`, and what to do
 * about it in `bodySmall`. The action is optional because "add something" is the answer to an
 * empty folder and is not the answer to a search that found nothing.
 *
 * It sits in a card rather than floating on the ground, because on both screens that use it there
 * are real cards directly above it and a bare block underneath them reads as a rendering fault.
 */
@Composable
fun AppEmptyState(
    iconRes: Int,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null
) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(colors.surfaceContainer)
            .padding(horizontal = 20.dp, vertical = 34.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        SlotWell(size = 60.dp) {
            Icon(
                painterResource(iconRes),
                contentDescription = null,
                tint = colors.primary,
                modifier = Modifier.size(26.dp)
            )
        }
        Spacer(Modifier.height(16.dp))
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            color = colors.onSurface,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(6.dp))
        Text(
            body,
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        if (action != null) {
            Spacer(Modifier.height(18.dp))
            action()
        }
    }
}
