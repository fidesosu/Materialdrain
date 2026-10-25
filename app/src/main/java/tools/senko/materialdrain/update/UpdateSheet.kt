package tools.senko.materialdrain.update

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import tools.senko.materialdrain.settings.ui.openLink
import tools.senko.materialdrain.ui.preview.PreviewTheme
import tools.senko.materialdrain.ui.preview.ThemePreviews
import tools.senko.materialdrain.ui.theme.AppShapes
import tools.senko.materialdrain.util.formatSize

/** How many changes show before "Show all". */
private const val CHANGES_SHOWN = 8

/**
 * Offers [update]: what it is, and everything it brings that this build doesn't have, then Update (download and install),
 * Later, or Skip this version.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdateSheet(
    update: AvailableUpdate,
    installed: InstalledBuild,
    download: UpdateDownload,
    onUpdate: () -> Unit,
    onLater: () -> Unit,
    onSkip: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onLater, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        UpdateSheetContent(update, installed, download, onUpdate, onLater, onSkip)
    }
}

@Composable
private fun UpdateSheetContent(
    update: AvailableUpdate,
    installed: InstalledBuild,
    download: UpdateDownload,
    onUpdate: () -> Unit,
    onLater: () -> Unit,
    onSkip: () -> Unit
) {
    val context = LocalContext.current
    var showAll by rememberSaveable { mutableStateOf(false) }
    val changes = if (showAll) update.changes else update.changes.take(CHANGES_SHOWN)

    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(start = 24.dp, end = 24.dp, bottom = 24.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Materialdrain ${update.info.versionName}", style = MaterialTheme.typography.headlineSmall)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (update.isDev) "Development build" else "Stable release",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier
                        .clip(AppShapes.Small)
                        .background(MaterialTheme.colorScheme.secondaryContainer)
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                )
                if (update.info.size > 0) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        formatSize(update.info.size),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Text(
            "What's new since your version (${installed.versionName})",
            style = MaterialTheme.typography.titleMedium
        )
        if (changes.isEmpty()) {
            Text(
                "This version came without a list of its changes.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                changes.forEach { ChangeRow(it) }
            }
            if (!showAll && update.changes.size > CHANGES_SHOWN) {
                TextButton(onClick = { showAll = true }) { Text("Show all ${update.changes.size} changes") }
            }
        }

        when (download) {
            is UpdateDownload.Running -> {
                val progress = download.progress
                if (progress != null) LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                else LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            is UpdateDownload.Failed -> Text(
                download.message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error
            )
            UpdateDownload.None -> if (!installed.updatable) Text(
                "This build is signed for testing, so a release can't install over it: get it from the releases page.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            TextButton(onClick = onSkip) { Text("Skip this version") }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onLater) { Text("Later") }
            Spacer(Modifier.width(8.dp))
            Button(
                enabled = download !is UpdateDownload.Running,
                onClick = { if (installed.updatable) onUpdate() else openLink(context, "$RELEASES_URL/") }
            ) {
                Text(
                    when {
                        !installed.updatable -> "Releases"
                        download is UpdateDownload.Running -> "Downloading"
                        download is UpdateDownload.Failed -> "Try again"
                        else -> "Update"
                    }
                )
            }
        }
    }
}

/**
 * One change: the part before its first colon (most changes start with what they're about, "Wallpaper colours on Android
 * 10 and 11 too: …") in bold, the first lines of the rest; tapping shows all of it.
 */
@Composable
private fun ChangeRow(change: UpdateChange) {
    var expanded by rememberSaveable(change.time, change.text) { mutableStateOf(false) }
    val headline = change.text.substringBefore(": ", "").takeIf { it.isNotEmpty() && it.length <= 120 }
    val text = buildAnnotatedString {
        if (headline != null) {
            withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(headline) }
            append(": ")
            append(change.text.substringAfter(": "))
        } else {
            append(change.text)
        }
    }
    Row(modifier = Modifier.fillMaxWidth().clip(AppShapes.Small).clickable { expanded = !expanded }) {
        Text("•", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(8.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = if (expanded) Int.MAX_VALUE else 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.animateContentSize()
        )
    }
}

// ---- Previews ----

private val previewInstalled = InstalledBuild("1.6.2", 900_000, 1_790_000_000, updatable = true, isDev = false)

private val previewChanges = listOf(
    "Wallpaper colours on Android 10 and 11 too: where Android doesn't colour apps from the wallpaper itself (before 12), the app makes its colours from the wallpaper's main colour the way Android 12 does, and follows it when the wallpaper changes.",
    "The app lock keeps the app shut until it's unlocked: while locked, nothing of it is drawn, open dialogs and menus close, and nothing is loaded from the host.",
    "Other apps can no longer connect to the song player: only the app itself and the system's media controls can.",
    "A file picked for upload no longer shows as \"Error reading file\" when the app it comes from leaves out its name or size.",
    "An SMB file's address on the device opens only that file.",
    "While the app lock is on, transfer notifications show only \"2 transfers running\" on a locked screen.",
    "Release builds leave the log's debug lines out.",
    "The search finds files by their extension too.",
    "Folders open faster on WebDAV hosts.",
    "Long file names wrap in the details instead of being cut off."
).mapIndexed { i, text -> UpdateChange(1_792_000_000L - i * 3600, text) }

private fun previewUpdate(changes: List<UpdateChange> = previewChanges, isDev: Boolean = false) = AvailableUpdate(
    info = UpdateInfo("1.6.4", 950_000, 1_792_000_000, "Materialdrain-1.6.4.apk", "", 14_680_064, changes),
    isDev = isDev,
    apkUrl = "",
    changes = changes
)

@ThemePreviews
@Composable
private fun UpdateSheetPreview() = PreviewTheme {
    UpdateSheetContent(previewUpdate(), previewInstalled, UpdateDownload.None, {}, {}, {})
}

@ThemePreviews
@Composable
private fun UpdateSheetDownloadingPreview() = PreviewTheme {
    UpdateSheetContent(previewUpdate(previewChanges.take(3), isDev = true), previewInstalled, UpdateDownload.Running(0.4f), {}, {}, {})
}

@ThemePreviews
@Composable
private fun UpdateSheetFailedPreview() = PreviewTheme {
    UpdateSheetContent(
        previewUpdate(previewChanges.take(2)), previewInstalled,
        UpdateDownload.Failed("The downloaded file isn't the one the release describes. Try again."), {}, {}, {}
    )
}

@ThemePreviews
@Composable
private fun UpdateSheetNoChangesPreview() = PreviewTheme {
    UpdateSheetContent(previewUpdate(emptyList()), previewInstalled.copy(updatable = false), UpdateDownload.None, {}, {}, {})
}
