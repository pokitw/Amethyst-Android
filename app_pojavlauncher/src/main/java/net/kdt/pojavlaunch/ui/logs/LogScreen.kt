package net.kdt.pojavlaunch.ui.logs

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.kdt.pojavlaunch.R
import net.kdt.pojavlaunch.ui.common.AppBackButton
import net.kdt.pojavlaunch.ui.common.AppEmptyState
import net.kdt.pojavlaunch.ui.common.AppSearchField
import net.kdt.pojavlaunch.ui.theme.Danger70
import net.kdt.pojavlaunch.ui.theme.Warning70

/**
 * The log, in the launcher, with a search box.
 *
 * Everything the launcher could say about a failed session was already in `latestlog.txt`, and
 * the only thing offered was a share sheet: to read your own log you had to send it somewhere
 * else first, and on Android 11 and up the file cannot even be browsed to, because it lives under
 * `Android/data`. So the launcher is the only thing that can show it, and until now it did not.
 *
 * <b>This screen does not take the collapsing large title every other full screen takes.</b> A log
 * is a working surface rather than a browsing one: the controls have to stay put while you read,
 * because searching a log is a loop of typing, reading and retyping, and a title that scrolls away
 * would take the search box with it. The bar, the gutters and the back button are the shared ones.
 *
 * Lines wrap rather than scrolling sideways, deliberately. Two-axis scrolling on a phone makes a
 * log unreadable, and a wrap toggle would be a control that is wrong nine times out of ten.
 */
@Composable
fun LogScreen(
    document: LogDocument?,
    onBack: () -> Unit,
    onShare: () -> Unit,
    onCopyLine: (String) -> Unit
) {
    var query by remember { mutableStateOf("") }
    var minimum by remember { mutableStateOf(LogLevel.DEBUG) }
    // The line search sent you to, washed until it has been seen. An index rather than a line,
    // because the same text can appear a hundred times in a log and only one of them was tapped.
    var landed by remember { mutableIntStateOf(-1) }

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 8.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AppBackButton(onBack)
                Spacer(Modifier.width(4.dp))
                Text(
                    stringResource(R.string.log_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (document != null && document.present) {
                    BarButton(R.drawable.ic_x_share, R.string.main_share_logs, onShare)
                }
            }

            // Branched rather than returned out of: the three states are a whole screen each,
            // and "still reading" is a different fact from "nothing to read" (only one of them
            // has an empty state to show).
            if (document == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            } else if (!document.present || document.lines.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(20.dp), contentAlignment = Alignment.TopCenter) {
                    AppEmptyState(
                        iconRes = R.drawable.ic_x_log,
                        title = stringResource(R.string.log_empty_title),
                        body = stringResource(R.string.log_empty_body)
                    )
                }
            } else {
                val visible = remember(document, query, minimum) {
                    visibleLines(document, query, minimum)
                }

                Column(Modifier.padding(horizontal = 20.dp)) {
                    AppSearchField(
                        query = query,
                        onQueryChange = { query = it },
                        placeholder = stringResource(R.string.log_search_hint),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(10.dp))
                    // Offered only when the log actually carries levels: the format belongs to
                    // Minecraft, and a filter over a log that declares none would hide all of it
                    // while claiming to show the errors.
                    if (document.hasLevels) {
                        LevelSegments(minimum) { minimum = it }
                        Spacer(Modifier.height(8.dp))
                    }
                    // Its own row rather than beside the segments: the line carries the gesture
                    // hint as well as the number, and next to three chips on a phone there is
                    // room for neither. One line, so it can never push the log down a second.
                    Text(
                        countLabel(document, visible.size, query, minimum),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth().padding(start = 4.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                }

                Box(Modifier.weight(1f)) {
                    if (visible.isEmpty()) {
                        Text(
                            stringResource(R.string.log_no_matches),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.align(Alignment.TopCenter).padding(top = 32.dp)
                        )
                    }
                    // The truncation notice takes item zero when it shows, so a line's place in
                    // the document is one less than its place in the list. Getting this wrong sends
                    // every search result to the line above the one that was tapped.
                    val header = document.truncated && query.isEmpty()
                    val offset = if (header) 1 else 0
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp)
                    ) {
                        if (header) {
                            item(key = "truncated") {
                                Text(
                                    stringResource(
                                        R.string.log_truncated,
                                        megabytes(document.shownBytes),
                                        megabytes(document.fileBytes)
                                    ),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(bottom = 10.dp)
                                )
                            }
                        }
                        items(visible.size, key = { visible[it] }) { position ->
                            val index = visible[position]
                            LogRow(
                                line = document.lines[index],
                                query = query,
                                washed = index == landed,
                                onSeen = { if (landed == index) landed = -1 },
                                onClick = {
                                    // Only a result goes anywhere: with nothing filtering, this line
                                    // is already among its neighbours and a tap should do nothing.
                                    if (query.isNotEmpty() || minimum != LogLevel.DEBUG) {
                                        query = ""
                                        minimum = LogLevel.DEBUG
                                        landed = index
                                        // Cleared above, so the notice is back and the offset with
                                        // it, whatever it was a moment ago.
                                        val target = index + if (document.truncated) 1 else 0
                                        scope.launch { listState.scrollToItem(target) }
                                    }
                                },
                                onLongClick = { onCopyLine(document.lines[index].text) }
                            )
                        }
                    }

                    // Opening at the end is right for a log, so the button only exists for the way
                    // back from having scrolled up to look for something.
                    val awayFromEnd by remember {
                        derivedStateOf {
                            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                            listState.layoutInfo.totalItemsCount - last > 4
                        }
                    }
                    JumpToEndOverlay(
                        visible = awayFromEnd && visible.isNotEmpty(),
                        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp)
                    ) {
                        scope.launch { listState.scrollToItem(visible.size + offset) }
                    }
                }
            }
        }
    }

    // Opens at the newest lines, the way a terminal does, because the end of a log is the reason
    // anybody came. Keyed on the document so a reload lands at the end too.
    LaunchedEffect(document) {
        val count = document?.lines?.size ?: 0
        if (count > 0) listState.scrollToItem(count + 1)
    }
}

/** One line, monospaced, with the searched text picked out inside it. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LogRow(
    line: LogLine,
    query: String,
    washed: Boolean,
    onSeen: () -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val wash by animateFloatAsState(
        targetValue = if (washed) 0.16f else 0f,
        animationSpec = tween(500, easing = FastOutSlowInEasing),
        label = "logLineWash"
    )
    // The wash is a landing light, not a selection: it fades once, and the state that drives it
    // is dropped so scrolling back later does not light the line up again.
    LaunchedEffect(washed) {
        if (washed) {
            delay(1600)
            onSeen()
        }
    }
    val colors = MaterialTheme.colorScheme
    val color = when {
        !line.own -> colors.onSurfaceVariant
        line.level == LogLevel.ERROR -> Danger70
        line.level == LogLevel.WARN -> Warning70
        else -> colors.onSurfaceVariant
    }
    Text(
        text = highlight(line.text, query, colors.primary),
        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
        color = color,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(colors.primary.copy(alpha = wash))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 6.dp, vertical = 3.dp)
    )
}

/** Every occurrence of the query inside the line, marked. */
private fun highlight(text: String, query: String, accent: Color): AnnotatedString {
    if (query.isEmpty()) return AnnotatedString(text)
    return buildAnnotatedString {
        append(text)
        var from = text.indexOf(query, ignoreCase = true)
        while (from >= 0) {
            addStyle(
                SpanStyle(color = accent, background = accent.copy(alpha = 0.18f)),
                from, from + query.length
            )
            from = text.indexOf(query, from + query.length, ignoreCase = true)
        }
    }
}

/** All, warnings and worse, errors only. Three answers, because a log has three moods. */
@Composable
private fun LevelSegments(minimum: LogLevel, onSelect: (LogLevel) -> Unit) {
    Row(
        Modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Segment(R.string.log_level_all, minimum == LogLevel.DEBUG) { onSelect(LogLevel.DEBUG) }
        Segment(R.string.log_level_warn, minimum == LogLevel.WARN) { onSelect(LogLevel.WARN) }
        Segment(R.string.log_level_error, minimum == LogLevel.ERROR) { onSelect(LogLevel.ERROR) }
    }
}

@Composable
private fun Segment(labelRes: Int, selected: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Text(
        stringResource(labelRes),
        style = MaterialTheme.typography.labelMedium,
        color = if (selected) colors.primary else colors.onSurfaceVariant,
        maxLines = 1,
        modifier = Modifier
            .clip(CircleShape)
            .background(
                if (selected) colors.primary.copy(alpha = 0.13f) else Color.Transparent
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    )
}

/**
 * The button, and its coming and going.
 *
 * Its own composable rather than an `AnimatedVisibility` written inline, and not for tidiness:
 * inline it sits inside a `Box` that is itself inside a `Column`, and with no `BoxScope` overload
 * to take, Kotlin reaches past the innermost receiver and resolves the `ColumnScope` one, which
 * then refuses to be called on an implicit receiver that is not there. Out here there is no scope
 * to reach for and the plain overload is the only candidate.
 */
@Composable
private fun JumpToEndOverlay(
    visible: Boolean,
    modifier: Modifier,
    onClick: () -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(300, easing = FastOutSlowInEasing)) +
                slideInVertically(tween(300, easing = FastOutSlowInEasing)) { it / 2 },
        exit = fadeOut(tween(140)) + slideOutVertically(tween(140)) { it / 2 },
        modifier = modifier
    ) {
        JumpToEnd(onClick)
    }
}

@Composable
private fun JumpToEnd(onClick: () -> Unit) {
    Row(
        Modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .clickable(onClick = onClick)
            .padding(start = 14.dp, end = 10.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            stringResource(R.string.log_jump_end),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.width(4.dp))
        Icon(
            Icons.Filled.KeyboardArrowDown,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun BarButton(iconRes: Int, labelRes: Int, onClick: () -> Unit) {
    Box(
        Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painterResource(iconRes),
            contentDescription = stringResource(labelRes),
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(20.dp)
        )
    }
}

/** What the numbers under the search box say, which depends on whether anything is filtering. */
@Composable
private fun countLabel(
    document: LogDocument,
    shown: Int,
    query: String,
    minimum: LogLevel
): String = when {
    query.isNotEmpty() -> stringResource(R.string.log_matches, shown)
    minimum != LogLevel.DEBUG ->
        stringResource(R.string.log_shown_lines, shown, document.lines.size)
    else -> stringResource(R.string.log_total_lines, document.lines.size)
}

private fun megabytes(bytes: Long): String =
    String.format(Locale.getDefault(), "%.1f MB", bytes / 1048576f)
