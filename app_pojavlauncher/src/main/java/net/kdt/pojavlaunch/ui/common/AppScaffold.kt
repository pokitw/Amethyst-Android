package net.kdt.pojavlaunch.ui.common

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import net.kdt.pojavlaunch.R

/**
 * The shape every full screen in the launcher takes: a bar that keeps the title once the large
 * one has scrolled away, and a scrolling body on 20dp gutters.
 *
 * Settings arrived at this shape first and then Settings' search, the profile flow and the skin
 * editor all needed it, so it lives here rather than being copied four times. The bar earns its
 * keep twice over: it gives the title back after you have scrolled past it, and it is what
 * Settings' search scrolls against when it sends you to a row further down.
 *
 * @param scroll hoisted, because callers that scroll to a row of their own need the same state
 * @param onViewportTop where the scrolling area starts in root coordinates, for those same callers
 */
@Composable
fun AppScaffold(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    scroll: ScrollState = rememberScrollState(),
    barAction: (@Composable () -> Unit)? = null,
    onViewportTop: ((Float) -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val collapseDistance = with(LocalDensity.current) { 84.dp.toPx() }

    Column(
        modifier
            .fillMaxSize()
            // The launcher window lays out full-screen when it is told to ignore the notch, so
            // this asks for whatever inset has not already been applied further up.
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppBackButton(onBack)
            Spacer(Modifier.width(4.dp))
            // Read in the layer rather than in the composition, so a drag repaints the alpha
            // instead of recomposing the bar on every frame of it.
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .graphicsLayer {
                        alpha = (scroll.value / collapseDistance).coerceIn(0f, 1f)
                    }
            )
            if (barAction != null) barAction()
        }
        Box(
            Modifier
                .weight(1f)
                // Measured outside the scroll, so it stays put while the rows inside move past it.
                .onGloballyPositioned { coordinates ->
                    onViewportTop?.invoke(coordinates.positionInRoot().y)
                }
        ) {
            Column(
                Modifier
                    .verticalScroll(scroll)
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 28.dp)
            ) {
                Column(
                    Modifier.graphicsLayer {
                        alpha = 1f - (scroll.value / collapseDistance).coerceIn(0f, 1f)
                    }
                ) {
                    Text(
                        title,
                        style = MaterialTheme.typography.displaySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (subtitle != null) {
                        Spacer(Modifier.height(3.dp))
                        Text(
                            subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                content()
            }
        }
    }
}

@Composable
fun AppBackButton(onBack: () -> Unit) {
    Box(
        Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onBack),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            Icons.Filled.ArrowBack,
            contentDescription = stringResource(R.string.recordings_back),
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(20.dp)
        )
    }
}

/**
 * The launcher's one search field.
 *
 * A `BasicTextField` rather than an `OutlinedTextField`, because the shape here is a pill on a
 * tonal surface and the Material one would bring its own container and label to fight with.
 */
@Composable
fun AppSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    onSubmit: () -> Unit = {}
) {
    Row(
        modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(start = 16.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Filled.Search,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(19.dp)
        )
        Spacer(Modifier.width(12.dp))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (query.isEmpty()) {
                Text(
                    placeholder,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    color = MaterialTheme.colorScheme.onSurface
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 14.dp)
            )
        }
        Box(
            Modifier
                .size(40.dp)
                .clip(CircleShape)
                .clickable(enabled = query.isNotEmpty()) { onQueryChange("") },
            contentAlignment = Alignment.Center
        ) {
            if (query.isNotEmpty()) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = stringResource(R.string.settings_search_clear),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
