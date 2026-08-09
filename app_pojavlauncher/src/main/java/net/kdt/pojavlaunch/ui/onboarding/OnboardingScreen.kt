package net.kdt.pojavlaunch.ui.onboarding

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import net.kdt.pojavlaunch.R
import net.kdt.pojavlaunch.ui.theme.Amethyst20
import net.kdt.pojavlaunch.ui.theme.Amethyst50
import net.kdt.pojavlaunch.ui.theme.Amethyst70
import net.kdt.pojavlaunch.ui.theme.SlotWell

/**
 * The first thing anyone sees.
 *
 * Four pages: what this is, the one feature the fork exists for, what was rebuilt, and an honest
 * side-by-side against the launcher it forked from. It runs once, before the launcher itself is
 * built, so it deliberately does nothing but talk — no account, no download, no launch. Everything
 * that needs the launcher's own wiring waits until the home screen, which is where this ends.
 *
 * The comparison page is the reason the flow exists at all. Somebody arriving from Amethyst has a
 * fair question — why this one — and the answer is more convincing with the limitations left in.
 */
@Composable
fun OnboardingScreen(onFinish: () -> Unit) {
    val pages = onboardingPages()
    val pagerState = rememberPagerState(pageCount = { pages.size + 1 })
    val scope = rememberCoroutineScope()
    val last = pagerState.currentPage == pages.size

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .auroraWash()
    ) {
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {

            // Skip stays available the whole way, because an onboarding nobody can leave is a
            // wall rather than a welcome. It lands in the same place Get started does.
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
                Spacer(Modifier.weight(1f))
                AnimatedVisibility(visible = !last, enter = fadeIn(tween(200))) {
                    Text(
                        stringResource(R.string.onboarding_skip),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable(onClick = onFinish)
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f)
            ) { page ->
                if (page < pages.size) StoryPage(pages[page]) else ComparisonPage()
            }

            Dots(count = pages.size + 1, current = pagerState.currentPage)
            Spacer(Modifier.height(16.dp))

            Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 18.dp)) {
                PrimaryButton(
                    label = stringResource(
                        if (last) R.string.onboarding_start else R.string.onboarding_next
                    ),
                    modifier = Modifier.align(Alignment.Center)
                ) {
                    if (last) onFinish()
                    else scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                }
            }
        }
    }
}

/**
 * The wash behind everything.
 *
 * One slow violet pool, drifting. It is drawn in a [drawBehind] rather than as a composable so it
 * costs a redraw and never a relayout, which matters because it is animating for the whole flow.
 */
@Composable
private fun Modifier.auroraWash(): Modifier {
    val drift by rememberInfiniteTransition(label = "aurora").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(18000, easing = LinearEasing), RepeatMode.Reverse),
        label = "auroraDrift"
    )
    return drawBehind {
        drawRect(
            Brush.radialGradient(
                colors = listOf(Amethyst50.copy(alpha = 0.20f), Color.Transparent),
                center = Offset(size.width * (0.22f + drift * 0.5f), size.height * 0.14f),
                radius = size.maxDimension * 0.72f
            )
        )
    }
}

/** One story page: a slot-well icon, a headline, a paragraph, and its honest caveat. */
@Composable
private fun StoryPage(page: OnboardingPage) {
    // Keyed on the page so re-entering it replays the entrance rather than showing it landed.
    var shown by remember(page.titleRes) { mutableStateOf(false) }
    LaunchedEffect(page.titleRes) { shown = true }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Rise(shown, 0) {
            if (page.brand) {
                BrandMark()
            } else {
                SlotWell(size = 76.dp, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                    Icon(
                        painterResource(page.iconRes),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }
        }
        Spacer(Modifier.height(30.dp))
        Rise(shown, 1) {
            Text(
                stringResource(page.titleRes),
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
        }
        Spacer(Modifier.height(14.dp))
        Rise(shown, 2) {
            Text(
                stringResource(page.bodyRes),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 420.dp)
            )
        }
        if (page.noteRes != 0) {
            Spacer(Modifier.height(20.dp))
            Rise(shown, 3) { Caveat(stringResource(page.noteRes)) }
        }
    }
}

/**
 * The gem, breathing.
 *
 * It never takes a tint: the four cut facets are the one place in the app where the amethyst is
 * the subject rather than an accent.
 */
@Composable
private fun BrandMark() {
    val pulse by rememberInfiniteTransition(label = "gem").animateFloat(
        initialValue = 1f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(tween(2600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "gemPulse"
    )
    Box(contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(150.dp)
                .scale(pulse)
                .background(
                    Brush.radialGradient(listOf(Amethyst50.copy(alpha = 0.28f), Color.Transparent)),
                    CircleShape
                )
        )
        Icon(
            painterResource(R.drawable.ic_x_gem),
            contentDescription = null,
            tint = Color.Unspecified,
            modifier = Modifier.size(84.dp).scale(pulse)
        )
    }
}

/** A staggered rise. [index] is the position in the sequence, not a delay in milliseconds. */
@Composable
private fun Rise(shown: Boolean, index: Int, content: @Composable () -> Unit) {
    AnimatedVisibility(
        visible = shown,
        enter = fadeIn(tween(420, delayMillis = index * 90)) +
                slideInVertically(tween(420, delayMillis = index * 90, easing = FastOutSlowInEasing)) { it / 5 }
    ) { content() }
}

/** The limitation that belongs with the feature above it, in the app's own words. */
@Composable
private fun Caveat(text: String) {
    Row(
        Modifier
            .widthIn(max = 420.dp)
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painterResource(R.drawable.ic_x_info),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(17.dp)
        )
        Spacer(Modifier.width(11.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** The page indicator. The current dot stretches rather than only brightening. */
@Composable
private fun Dots(count: Int, current: Int) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (i in 0 until count) {
            val on = i == current
            val width by animateFloatAsState(
                targetValue = if (on) 22f else 7f,
                animationSpec = tween(300, easing = FastOutSlowInEasing),
                label = "dotWidth"
            )
            Box(
                Modifier
                    .padding(horizontal = 3.dp)
                    .height(7.dp)
                    .width(width.dp)
                    .clip(CircleShape)
                    .background(
                        if (on) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceContainerHighest
                    )
            )
        }
    }
}

/**
 * The one gradient in the flow.
 *
 * Everything else stays quiet so this reads as the way forward without needing to glow.
 */
@Composable
private fun PrimaryButton(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(
        modifier
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(Amethyst70, Amethyst50)))
            .clickable(onClick = onClick)
            .padding(horizontal = 44.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.titleMedium, color = Amethyst20)
    }
}

/**
 * Amethyst against Amethyst X.
 *
 * Rows tick in one at a time as the page settles. The last two rows are the ones that make the
 * page trustworthy rather than a sales sheet: they are the things this fork is worse at.
 */
@Composable
private fun ComparisonPage() {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val rows = comparisonRows()

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(12.dp))
        Text(
            stringResource(R.string.onboarding_compare_title),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.onboarding_compare_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 440.dp)
        )
        Spacer(Modifier.height(18.dp))

        Column(
            Modifier
                .widthIn(max = 480.dp)
                .clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.surfaceContainer)
        ) {
            HeaderRow()
            rows.forEachIndexed { i, row ->
                AnimatedVisibility(
                    visible = shown,
                    enter = fadeIn(tween(300, delayMillis = 90 + i * 70)) +
                            slideInVertically(
                                tween(300, delayMillis = 90 + i * 70, easing = FastOutSlowInEasing)
                            ) { it / 3 }
                ) { CompareRow(row) }
            }
        }
        Spacer(Modifier.height(14.dp))
        Text(
            stringResource(R.string.onboarding_compare_footnote),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 440.dp)
        )
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun HeaderRow() {
    Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Spacer(Modifier.weight(1f))
        Text(
            stringResource(R.string.onboarding_compare_them),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(72.dp)
        )
        Text(
            stringResource(R.string.onboarding_compare_us),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(72.dp)
        )
    }
}

@Composable
private fun CompareRow(row: ComparisonRow) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            stringResource(row.labelRes),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        Mark(row.upstream, Modifier.width(72.dp))
        Mark(row.fork, Modifier.width(72.dp))
    }
}

/**
 * One cell of the table.
 *
 * A dash rather than a cross for "does not have it": upstream not shipping a thing is an absence,
 * not a failure, and a red cross would be picking a fight the table does not need.
 */
@Composable
private fun Mark(state: Support, modifier: Modifier = Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        when (state) {
            Support.YES -> Box(
                Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(Amethyst70.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = stringResource(R.string.onboarding_compare_yes),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(14.dp)
                )
            }
            Support.NO -> Box(
                Modifier
                    .width(11.dp)
                    .height(2.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f))
            )
            Support.PARTIAL -> Text(
                stringResource(R.string.onboarding_compare_partial),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.alpha(0.9f)
            )
        }
    }
}
