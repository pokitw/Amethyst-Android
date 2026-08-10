package net.kdt.pojavlaunch.ui.mods

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import net.kdt.pojavlaunch.R
import net.kdt.pojavlaunch.modloaders.modpacks.api.ModrinthMods
import net.kdt.pojavlaunch.ui.common.LazyAppScaffold
import net.kdt.pojavlaunch.ui.settings.SectionLabel
import net.kdt.pojavlaunch.ui.settings.SettingsCard
import net.kdt.pojavlaunch.ui.theme.Amethyst20
import net.kdt.pojavlaunch.ui.theme.SlotWell
import java.util.Locale

/**
 * One mod, opened full: the page a row expands into.
 *
 * <b>A route inside the browser, not its own activity</b>, the same call the version picker made
 * inside the profile editor: Back always lands on the results, with the query and scroll intact.
 *
 * The page draws in arrival order. The header comes from the search hit, so it is on screen
 * before any request returns; the body, the gallery and the version list each fill their own
 * section as their fetch lands. The one accent element is the install button in the header,
 * which installs the best version for the profile, exactly what the row's button does; the
 * version list below is for the player who wants a different one, and its buttons are quiet.
 *
 * What this page does not claim: it renders a documented subset of Markdown (see ModMarkdown)
 * and the "Open on Modrinth" link is the honest way to everything else.
 */
@Composable
fun ModProjectScreen(
    page: ProjectPage,
    installEnabled: Boolean,
    rowIcon: androidx.compose.ui.graphics.ImageBitmap?,
    onInstallBest: () -> Unit,
    onInstallVersion: (ModrinthMods.File) -> Unit,
    onNeedGallery: (String) -> Unit,
    onBack: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val uriHandler = LocalUriHandler.current
    val project = page.project

    Surface(color = colors.background, modifier = Modifier.fillMaxSize()) {
        LazyAppScaffold(
            title = page.hit.title,
            subtitle = page.hit.author.ifEmpty { null },
            onBack = onBack
        ) {
            item(key = "header") {
                Column {
                    Spacer(Modifier.height(14.dp))
                    HeaderCard(page, rowIcon, installEnabled, onInstallBest)
                }
            }

            item(key = "links") {
                // The page link is always real, because it is built from the hit; the rest
                // appear once the project has arrived.
                val links = project?.let { linksOf(it) }.orEmpty()
                LinksRow(
                    links + (stringResource(R.string.mods_page_open) to
                            (project?.pageUrl()
                                ?: ("https://modrinth.com/mod/" + page.hit.slug.ifEmpty { page.hit.projectId }))),
                    onOpen = { runCatching { uriHandler.openUri(it) } }
                )
            }

            if (project != null && project.gallery.isNotEmpty()) {
                item(key = "gallery") {
                    GalleryStrip(project.gallery, page.gallery, onNeedGallery)
                }
            }

            when {
                project != null && project.body.isNotBlank() -> {
                    item(key = "aboutLabel") {
                        SectionLabel(stringResource(R.string.mods_page_about))
                    }
                    item(key = "about") { DescriptionCard(project.body) }
                }
                page.projectFailed -> item(key = "aboutFailed") {
                    NoticeCard(stringResource(R.string.mods_page_failed), error = true)
                }
                project == null -> item(key = "aboutLoading") { CentredSpinner() }
            }

            if (page.versionsKnown) {
                if (page.versions.isNotEmpty()) {
                    item(key = "versionsLabel") {
                        SectionLabel(stringResource(R.string.mods_page_versions))
                    }
                    // Grouped-card corners rebuilt per row, like every long list here.
                    page.versions.forEachIndexed { index, version ->
                        item(key = version.versionId) {
                            VersionRow(
                                version = version,
                                shape = groupedRowShape(index, page.versions.size),
                                installing = page.installingVersion == version.versionId,
                                enabled = installEnabled,
                                onInstall = { onInstallVersion(version) }
                            )
                        }
                    }
                } else {
                    item(key = "noVersions") {
                        NoticeCard(stringResource(R.string.mods_browse_no_version))
                    }
                }
            }

            val note = page.note
            if (note != null) {
                item(key = "note") {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        note,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (page.noteIsError) colors.error else colors.primary,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun HeaderCard(
    page: ProjectPage,
    rowIcon: androidx.compose.ui.graphics.ImageBitmap?,
    installEnabled: Boolean,
    onInstallBest: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val project = page.project
    Column(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(colors.surfaceContainer)
            .padding(15.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SlotWell(size = 54.dp) {
                if (rowIcon != null) {
                    Image(
                        BitmapPainter(rowIcon),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(46.dp).clip(RoundedCornerShape(12.dp))
                    )
                } else {
                    Icon(
                        painterResource(R.drawable.ic_x_files),
                        contentDescription = null,
                        tint = colors.onSurfaceVariant,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    page.hit.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    statsLine(page),
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            page.hit.description,
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant
        )
        Spacer(Modifier.height(14.dp))
        // The page's one accent element. Installs what the row's one-tap would: the newest
        // release that fits the profile.
        Text(
            stringResource(R.string.mods_browse_install),
            style = MaterialTheme.typography.titleSmall,
            color = Amethyst20,
            modifier = Modifier
                .clip(CircleShape)
                .background(if (installEnabled) colors.primary else colors.surfaceContainerHighest)
                .clickable(onClick = onInstallBest)
                .padding(horizontal = 22.dp, vertical = 11.dp)
        )
    }
}

@Composable
private fun statsLine(page: ProjectPage): String {
    val project = page.project
    val downloads = countLabel(project?.downloads ?: page.hit.downloads)
    val parts = mutableListOf(
        stringResource(R.string.mods_browse_downloads, downloads)
    )
    if (project != null) {
        parts += stringResource(R.string.mods_page_followers, countLabel(project.followers))
        project.licenseName?.takeIf { it.isNotEmpty() }?.let { parts += it }
    }
    return parts.joinToString(" · ")
}

private fun countLabel(count: Int): String {
    val locale = Locale.getDefault()
    return when {
        count >= 1_000_000 -> String.format(locale, "%.1fM", count / 1_000_000f)
        count >= 1_000 -> String.format(locale, "%.0fK", count / 1_000f)
        else -> String.format(locale, "%d", count)
    }
}

@Composable
private fun linksOf(project: ModrinthMods.Project): List<Pair<String, String>> {
    val links = mutableListOf<Pair<String, String>>()
    project.sourceUrl?.takeIf { it.isNotEmpty() }
        ?.let { links += stringResource(R.string.mods_page_source) to it }
    project.issuesUrl?.takeIf { it.isNotEmpty() }
        ?.let { links += stringResource(R.string.mods_page_issues) to it }
    project.wikiUrl?.takeIf { it.isNotEmpty() }
        ?.let { links += stringResource(R.string.mods_page_wiki) to it }
    project.discordUrl?.takeIf { it.isNotEmpty() }
        ?.let { links += stringResource(R.string.mods_page_discord) to it }
    return links
}

/** Quiet accent text links, the shape the home screen's footer uses. */
@Composable
private fun LinksRow(links: List<Pair<String, String>>, onOpen: (String) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        links.forEach { (label, url) ->
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onOpen(url) }
                    .padding(horizontal = 4.dp, vertical = 4.dp)
            )
        }
    }
}

@Composable
private fun GalleryStrip(
    gallery: List<ModrinthMods.GalleryImage>,
    loaded: Map<String, androidx.compose.ui.graphics.ImageBitmap>,
    onNeed: (String) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(bottom = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        gallery.forEach { image ->
            LaunchedEffect(image.url) { if (!loaded.containsKey(image.url)) onNeed(image.url) }
            val bitmap = loaded[image.url]
            Box(
                Modifier
                    .size(width = 220.dp, height = 132.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(colors.surfaceContainerHigh),
                contentAlignment = Alignment.Center
            ) {
                if (bitmap != null) {
                    Image(
                        BitmapPainter(bitmap),
                        contentDescription = image.title.ifEmpty { null },
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    CircularProgressIndicator(
                        strokeWidth = 2.dp,
                        color = colors.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun DescriptionCard(body: String) {
    val colors = MaterialTheme.colorScheme
    val accent = colors.primary
    // Parsed once per body, off the hot path: recomposition re-reads the cached blocks and a
    // scroll never re-runs the regexes.
    val parsed = remember(body) { parseMarkdown(body, accent) }
    SettingsCard {
        Column(Modifier.padding(horizontal = 15.dp, vertical = 14.dp)) {
            parsed.forEachIndexed { index, block ->
                if (index > 0) Spacer(Modifier.height(10.dp))
                when (block) {
                    is MdBlock.Heading -> Text(
                        block.text,
                        style = if (block.level <= 2) MaterialTheme.typography.titleMedium
                        else MaterialTheme.typography.titleSmall,
                        color = colors.onSurface
                    )
                    is MdBlock.Paragraph -> Text(
                        block.text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant
                    )
                    is MdBlock.Bullet -> Row {
                        Text(
                            "•",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.primary
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            block.text,
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.onSurfaceVariant
                        )
                    }
                    is MdBlock.Code -> Text(
                        block.text,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                        ),
                        color = colors.onSurface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(colors.surfaceContainerHighest)
                            .padding(10.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun VersionRow(
    version: ModrinthMods.File,
    shape: RoundedCornerShape,
    installing: Boolean,
    enabled: Boolean,
    onInstall: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.surfaceContainer)
            .padding(horizontal = 15.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                version.versionNumber.ifEmpty { version.fileName },
                style = MaterialTheme.typography.titleSmall,
                color = colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(
                versionDetails(version),
                style = MaterialTheme.typography.labelSmall,
                color = if (version.isRelease) colors.onSurfaceVariant else colors.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(10.dp))
        Box(
            Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(colors.surfaceContainerHighest)
                .clickable(enabled = !installing, onClick = onInstall),
            contentAlignment = Alignment.Center
        ) {
            if (installing) {
                CircularProgressIndicator(
                    strokeWidth = 2.dp,
                    color = colors.primary,
                    modifier = Modifier.size(16.dp)
                )
            } else {
                Icon(
                    painterResource(R.drawable.ic_x_install),
                    contentDescription = stringResource(R.string.mods_browse_install),
                    tint = if (enabled) colors.primary else colors.onSurfaceVariant,
                    modifier = Modifier.size(17.dp)
                )
            }
        }
    }
}

private fun versionDetails(version: ModrinthMods.File): String {
    val parts = mutableListOf<String>()
    if (!version.isRelease) parts += version.channel.replaceFirstChar { it.uppercaseChar() }
    if (version.size > 0) parts += sizeLabel(version.size)
    parts += version.fileName
    return parts.joinToString(" · ")
}

private fun sizeLabel(bytes: Int): String {
    val locale = Locale.getDefault()
    return when {
        bytes >= 1024 * 1024 -> String.format(locale, "%.1f MB", bytes / (1024f * 1024f))
        bytes >= 1024 -> String.format(locale, "%.0f KB", bytes / 1024f)
        else -> String.format(locale, "%d B", bytes)
    }
}

@Composable
private fun NoticeCard(text: String, error: Boolean = false) {
    SettingsCard {
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = if (error) MaterialTheme.colorScheme.error
            else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 15.dp, vertical = 14.dp)
        )
    }
}

@Composable
private fun CentredSpinner() {
    Box(Modifier.fillMaxWidth().padding(vertical = 28.dp), Alignment.Center) {
        CircularProgressIndicator(
            strokeWidth = 2.5.dp,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(26.dp)
        )
    }
}

/** The card around a section, rebuilt from each row's position, as everywhere else. */
@Composable
private fun groupedRowShape(index: Int, count: Int): RoundedCornerShape {
    val card = MaterialTheme.shapes.medium
    val flat = androidx.compose.foundation.shape.CornerSize(0.dp)
    val first = index == 0
    val last = index == count - 1
    return RoundedCornerShape(
        topStart = if (first) card.topStart else flat,
        topEnd = if (first) card.topEnd else flat,
        bottomStart = if (last) card.bottomStart else flat,
        bottomEnd = if (last) card.bottomEnd else flat
    )
}
