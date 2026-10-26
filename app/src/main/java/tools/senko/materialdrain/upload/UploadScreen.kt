package tools.senko.materialdrain.upload

import tools.senko.materialdrain.ui.theme.AppShapes
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import tools.senko.materialdrain.ui.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import tools.senko.materialdrain.ui.components.DotScrollbar
import tools.senko.materialdrain.ui.components.InfoRow
import tools.senko.materialdrain.ui.media.AudioPlayerPreview
import tools.senko.materialdrain.ui.media.FullScreenMediaPreviewDialog
import tools.senko.materialdrain.ui.media.InlineImagePreview
import tools.senko.materialdrain.ui.media.InlineTextPreview
import tools.senko.materialdrain.ui.media.InlineVideoPreview
import tools.senko.materialdrain.ui.media.rememberDecodedBitmap
import tools.senko.materialdrain.util.formatDurationMillis
import tools.senko.materialdrain.util.formatSize
import tools.senko.materialdrain.ui.preview.PreviewTheme
import tools.senko.materialdrain.ui.preview.ScreenPreviews
import tools.senko.materialdrain.ui.preview.PreviewSamples
import androidx.compose.ui.res.stringResource
import tools.senko.materialdrain.R

@Composable
fun UploadScreenContent(
    uploadViewModel: UploadViewModel,
    fabHeight: Dp,
    isFabVisible: Boolean
) {
    val uiState by uploadViewModel.uiState.collectAsState()
    val context = LocalContext.current
    UploadScreenContent(
        uiState = uiState,
        fabHeight = fabHeight,
        isFabVisible = isFabVisible,
        onFilesPicked = { uris -> uploadViewModel.onFilesSelected(uris, context) },
        onClearPickedFile = { uploadViewModel.onFileSelected(null, context) },
        onTextChange = uploadViewModel::onTextToUploadChanged,
        onRemoveQueued = uploadViewModel::removeQueuedItem,
        onClearQueued = uploadViewModel::clearQueuedItems,
        onCancel = uploadViewModel::cancelUpload,
        // Text shared from another app (see PendingOpen.Text) is on the text tab
        initialTab = if (uiState.textToUpload.isNotBlank() && uiState.pickedFile == null && uiState.queuedItems.isEmpty()) 1 else 0
    )
}

/** The Upload screen drawn from [uiState], what it does handed to the callbacks; see the previews at the end of the file. */
@Composable
fun UploadScreenContent(
    uiState: UploadUiState,
    fabHeight: Dp,
    isFabVisible: Boolean,
    onFilesPicked: (List<Uri>) -> Unit,
    onClearPickedFile: () -> Unit,
    onTextChange: (String) -> Unit,
    onRemoveQueued: (Int) -> Unit,
    onClearQueued: () -> Unit,
    onCancel: () -> Unit,
    /** The tab it opens on: 0 for a file, 1 for text */
    initialTab: Int = 0
) {
    var selectedTabIndex by remember { mutableIntStateOf(initialTab) }
    val tabTitles = listOf(stringResource(R.string.upload_upload_file), stringResource(R.string.upload_upload_text))


    var fullScreenPreviewUri by remember { mutableStateOf<Uri?>(null) }
    var fullScreenPreviewMimeType by remember { mutableStateOf<String?>(null) }

    if (fullScreenPreviewUri != null) {
        FullScreenMediaPreviewDialog(
            previewUri = fullScreenPreviewUri,
            previewMimeType = fullScreenPreviewMimeType,
            thumbnailUrl = null, // In UploadScreen, fullscreen video doesn't use a separate thumbnail for the player
            apiKey = null,
            onDismissRequest = {
                fullScreenPreviewUri = null
                fullScreenPreviewMimeType = null
            }
        )
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents(),
        onResult = { uris: List<Uri> ->
            onFilesPicked(uris)
            if (uris.isNotEmpty() && selectedTabIndex == 0) {
                onTextChange("")
            }
        }
    )


    Column(
        modifier = Modifier
            .fillMaxSize() // This Column will extend edge-to-edge
    ) {
        PrimaryTabRow(selectedTabIndex = selectedTabIndex) {
            tabTitles.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTabIndex == index,
                    onClick = {
                        selectedTabIndex = index
                        if (index == 0) onTextChange("")
                        else onClearPickedFile()
                    },
                    text = { Text(title) }
                )
            }
        }
        val scrollState = rememberScrollState()
        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(horizontal = 16.dp),
                    // .padding(bottom = 16.dp), // Removed original bottom padding
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(16.dp)) // Initial spacer for content

                val uploadResult = uiState.uploadResult
                if (!uiState.isLoading && uploadResult?.success == true) {
                    UploadResultCard(fileId = uploadResult.id)
                    Spacer(modifier = Modifier.height(16.dp))
                }

                when (selectedTabIndex) {
                    0 -> {
                        val hasSelection = uiState.pickedFile != null || uiState.queuedItems.isNotEmpty()
                        if (!hasSelection) {
                            UploadDropZone(
                                enabled = !uiState.isLoading,
                                onClick = { filePickerLauncher.launch("*/*") }
                            )
                        } else {
                            OutlinedButton(
                                onClick = { filePickerLauncher.launch("*/*") },
                                enabled = !uiState.isLoading,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Filled.AttachFile, contentDescription = null)
                                Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                                Text(if (uiState.queuedItems.isNotEmpty()) stringResource(R.string.upload_choose_different_files) else stringResource(R.string.upload_choose_a_different_file))
                            }
                        }
                        if (uiState.queuedItems.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            QueuedFilesCard(
                                items = uiState.queuedItems,
                                isUploading = uiState.isLoading,
                                onRemove = onRemoveQueued,
                                onClear = onClearQueued
                            )
                        }
                        uiState.pickedFile?.let { file ->
                            Spacer(modifier = Modifier.height(8.dp))
                            Card(modifier = Modifier.fillMaxWidth()){
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text(stringResource(R.string.upload_selected_file), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 8.dp))
                                    InfoRow(stringResource(R.string.settings_name), file.name ?: "N/A")
                                    file.sizeBytes?.let { s -> InfoRow(stringResource(R.string.files_size), formatSize(s)) }
                                    file.mimeType?.let { mt -> InfoRow(stringResource(R.string.files_type), mt) }
                                    // Each of these is only filled for its own type
                                    file.audioDurationMillis?.let { d -> InfoRow(stringResource(R.string.upload_duration), formatDurationMillis(d)) }
                                    file.audioBitrate?.let { b -> InfoRow(stringResource(R.string.upload_bitrate), "${b / 1000} kbps") }
                                    file.audioArtist?.let { a -> InfoRow(stringResource(R.string.upload_artist), a) }
                                    file.audioAlbum?.let { al -> InfoRow(stringResource(R.string.upload_album), al) }
                                    file.videoDurationMillis?.let { d -> InfoRow(stringResource(R.string.upload_duration), formatDurationMillis(d)) }
                                    file.pdfPageCount?.let { pc -> InfoRow(stringResource(R.string.upload_pages), pc.toString()) }
                                    file.apkPackageName?.let { pn -> InfoRow(stringResource(R.string.settings_package), pn) }
                                    file.apkVersionName?.let { vn -> InfoRow(stringResource(R.string.upload_version), vn) }
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))

                            if (file.mimeType?.startsWith("image/") == true) {
                                InlineImagePreview(
                                    imageSource = file.uri,
                                    contentDescription = stringResource(R.string.upload_selected_image_preview),
                                    apiKey = null,
                                    onFullScreenClick = {
                                        fullScreenPreviewUri = file.uri
                                        fullScreenPreviewMimeType = file.mimeType
                                    }
                                )
                            }

                            if (file.mimeType?.startsWith("video/") == true) {
                                InlineVideoPreview(
                                    thumbnailSource = file.videoThumbnail, // Upload screen uses fetched byte array for thumbnail
                                    contentDescription = stringResource(R.string.upload_selected_video_preview),
                                    apiKey = null,
                                    onFullScreenClick = {
                                        fullScreenPreviewUri = file.uri
                                        fullScreenPreviewMimeType = file.mimeType
                                    }
                                )
                            }

                            if (file.mimeType == "application/pdf" && file.pdfPageCount != null) {
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(100.dp)
                                        .padding(vertical = 8.dp),
                                    shape = AppShapes.Medium
                                ) {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Filled.PictureAsPdf, contentDescription = stringResource(R.string.upload_pdf_file), modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            }

                            if (file.mimeType == "application/vnd.android.package-archive") {
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 80.dp)
                                        .padding(vertical = 8.dp),
                                    shape = AppShapes.Medium
                                ) {
                                    Box(
                                        modifier = Modifier.fillMaxSize().padding(16.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        file.apkIcon?.let { rememberDecodedBitmap(it) }?.let { bitmap ->
                                            Image(
                                                bitmap = bitmap,
                                                contentDescription = stringResource(R.string.upload_apk_icon_preview),
                                                modifier = Modifier.size(64.dp),
                                                contentScale = ContentScale.Fit
                                            )
                                        } ?: Icon(Icons.Filled.Android, contentDescription = stringResource(R.string.upload_apk_file), modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            }

                            if (file.mimeType?.startsWith("audio/") == true) {
                                AudioPlayerPreview(
                                    audioUri = file.uri,
                                    apiKey = null,
                                    title = null,
                                    artist = file.audioArtist,
                                    album = file.audioAlbum,
                                    albumArtSource = file.audioAlbumArt,
                                    durationHintMillis = file.audioDurationMillis
                                )
                            }

                            InlineTextPreview(textContent = file.textContent)

                            file.detailsError?.let { error ->
                                Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(bottom=8.dp))
                            }
                        }
                    }
                    1 -> {
                        OutlinedTextField(
                            value = uiState.textToUpload,
                            onValueChange = onTextChange,
                            label = { Text(stringResource(R.string.upload_paste_text_here)) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = 240.dp),
                            maxLines = 12,
                            enabled = !uiState.isLoading,
                            supportingText = {
                                val size = uiState.textToUpload.toByteArray().size.toLong()
                                Text(
                                    text = if (size > 0) "${uiState.textToUpload.length} characters · ${formatSize(size)}" else stringResource(R.string.upload_uploaded_as_a_txt_file),
                                    modifier = Modifier.fillMaxWidth(),
                                    textAlign = TextAlign.End
                                )
                            }
                        )
                    }
                }

                if (uiState.isLoading) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Filled.Close, contentDescription = null)
                        Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                        Text(stringResource(R.string.upload_cancel_upload))
                    }
                }

                if (!uiState.isLoading && uploadResult == null) {
                    val effectiveSize = if (selectedTabIndex == 0) uiState.uploadTotalSizeBytes else uiState.textToUpload.toByteArray().size.toLong().takeIf { it > 0 }
                    if (effectiveSize != null && (uiState.pickedFile != null ||uiState.textToUpload.isNotBlank())) {
                        Text("${formatSize(effectiveSize)}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(bottom=8.dp), textAlign = TextAlign.Center)
                    }
                }
                // Add Spacer at the end of the scrollable content if FAB is visible
                if (isFabVisible) {
                    Spacer(Modifier.height(fabHeight + 16.dp)) // 16.dp for extra margin
                }
            }
            DotScrollbar(state = scrollState)
        }
    }
}

/**
 * Empty state of the file tab. It has no background of its own so it blends into the page, only a thin outline
 * marks the area. The whole area is the button (there is no separate one): it has the ripple of a button
 * and the text says so.
 */
@Composable
private fun UploadDropZone(enabled: Boolean, onClick: () -> Unit) {
    val contentAlpha = if (enabled) 1f else 0.38f
    Surface(
        onClick = onClick,
        enabled = enabled,
        color = Color.Transparent,
        shape = AppShapes.Small,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = contentAlpha)),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 220.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Filled.CloudUpload,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary.copy(alpha = contentAlpha)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                stringResource(R.string.upload_tap_to_choose_files),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary.copy(alpha = contentAlpha),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun UploadResultCard(fileId: String?) {
    // The app's texts, for those its callbacks show
    val res = LocalResources.current
    val context = LocalContext.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Filled.CheckCircle,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.upload_upload_complete), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                if (fileId != null) {
                    Text(
                        stringResource(R.string.upload_pixeldrain_com_u, fileId),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (fileId != null) {
                IconButton(onClick = { copyToClipboard(context, pixeldrainLink(fileId), res.getString(R.string.upload_link_copied_to_clipboard)) }) {
                    Icon(Icons.Filled.ContentCopy, contentDescription = stringResource(R.string.upload_copy_link), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
        }
    }
}

private fun pixeldrainLink(fileId: String) = "https://pixeldrain.com/u/$fileId"

private fun copyToClipboard(context: Context, text: String, toastMessage: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("Pixeldrain URL", text))
    Toast.makeText(context, toastMessage, Toast.LENGTH_SHORT).show()
}

@Composable
private fun QueuedFilesCard(
    items: List<UploadItem>,
    isUploading: Boolean,
    onRemove: (Int) -> Unit,
    onClear: () -> Unit
) {
    // The app's texts, for those its callbacks show
    val res = LocalResources.current
    val context = LocalContext.current
    val doneCount = items.count { it.status == UploadItemStatus.DONE }
    val totalSize = items.sumOf { it.sizeBytes ?: 0L }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(vertical = 8.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (doneCount > 0) "$doneCount of ${items.size} uploaded" else stringResource(R.string.upload_files_selected, items.size),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = formatSize(totalSize),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (doneCount > 0) {
                    TextButton(onClick = {
                        val links = items.mapNotNull { it.fileId }.joinToString("\n") { pixeldrainLink(it) }
                        copyToClipboard(context, links, res.getString(R.string.upload_links_copied_to_clipboard))
                    }) { Text(stringResource(R.string.upload_copy_links)) }
                }
                TextButton(onClick = onClear, enabled = !isUploading) { Text(stringResource(R.string.upload_clear)) }
            }
            items.forEach { item ->
                key(item.id) {
                    QueuedFileRow(item = item, isUploading = isUploading, onRemove = onRemove)
                }
            }
        }
    }
}

@Composable
private fun QueuedFileRow(item: UploadItem, isUploading: Boolean, onRemove: (Int) -> Unit) {
    // The app's texts, for those its callbacks show
    val res = LocalResources.current
    val context = LocalContext.current
    val size = item.sizeBytes?.takeIf { it > 0 }
    val fraction = size?.let { (item.uploadedBytes.toFloat() / it).coerceIn(0f, 1f) }
    val animatedFraction by animateFloatAsState(targetValue = fraction ?: 0f, label = "queuedItemProgress")

    val subtitle = when (item.status) {
        UploadItemStatus.PENDING -> item.sizeBytes?.let { formatSize(it) } ?: stringResource(R.string.upload_unknown_size)
        UploadItemStatus.UPLOADING ->
            if (size != null) "${((fraction ?: 0f) * 100).toInt()}% · ${formatSize(item.uploadedBytes)} / ${formatSize(size)}"
            else formatSize(item.uploadedBytes)
        UploadItemStatus.DONE -> stringResource(R.string.files_uploaded) + (item.sizeBytes?.let { " · ${formatSize(it)}" } ?: "")
        UploadItemStatus.FAILED -> item.errorMessage ?: stringResource(R.string.upload_upload_failed)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(24.dp), contentAlignment = Alignment.Center) {
            when (item.status) {
                UploadItemStatus.PENDING -> Icon(Icons.Filled.Schedule, contentDescription = stringResource(R.string.upload_waiting), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                UploadItemStatus.UPLOADING ->
                    if (fraction != null) CircularProgressIndicator(progress = { animatedFraction }, modifier = Modifier.size(22.dp), strokeWidth = 3.dp)
                    else CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 3.dp)
                UploadItemStatus.DONE -> Icon(Icons.Filled.CheckCircle, contentDescription = stringResource(R.string.files_uploaded), tint = MaterialTheme.colorScheme.primary)
                UploadItemStatus.FAILED -> Icon(Icons.Filled.Error, contentDescription = stringResource(R.string.upload_failed), tint = MaterialTheme.colorScheme.error)
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(item.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = if (item.status == UploadItemStatus.FAILED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        val fileId = item.fileId
        if (item.status == UploadItemStatus.DONE && fileId != null) {
            IconButton(onClick = { copyToClipboard(context, pixeldrainLink(fileId), res.getString(R.string.upload_link_copied_to_clipboard)) }) {
                Icon(Icons.Filled.ContentCopy, contentDescription = stringResource(R.string.upload_copy_link))
            }
        } else if (!isUploading) {
            IconButton(onClick = { onRemove(item.id) }) {
                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.upload_remove_from_list))
            }
        } else {
            Spacer(Modifier.size(48.dp))
        }
    }
}

// ---- Previews ----

/** The screen with [uiState], on tab [tab], doing nothing when used. */
@Composable
private fun UploadPreview(uiState: UploadUiState, tab: Int = 0) {
    PreviewTheme {
        UploadScreenContent(
            uiState = uiState,
            fabHeight = 56.dp,
            isFabVisible = uiState.hasUploadable,
            onFilesPicked = {},
            onClearPickedFile = {},
            onTextChange = {},
            onRemoveQueued = {},
            onClearQueued = {},
            onCancel = {},
            initialTab = tab
        )
    }
}

@ScreenPreviews
@Composable
private fun UploadEmptyPreview() = UploadPreview(UploadUiState())

/** One file picked: its details and its preview (an audio file, whose player shows). */
@ScreenPreviews
@Composable
private fun UploadPickedFilePreview() = UploadPreview(
    UploadUiState(
        uploadTotalSizeBytes = PreviewSamples.song.size,
        pickedFile = PickedFile(
            uri = Uri.EMPTY,
            name = PreviewSamples.song.name,
            sizeBytes = PreviewSamples.song.size,
            mimeType = "audio/mpeg",
            audioDurationMillis = 213_000,
            audioBitrate = 320_000,
            audioArtist = "Some Artist",
            audioAlbum = "An Album (2026)"
        )
    )
)

/** Several files uploading: one done, one under way, one failed, one waiting. */
@ScreenPreviews
@Composable
private fun UploadQueuePreview() {
    val files = PreviewSamples.files.filter { !it.isDirectory }.take(4)
    UploadPreview(
        UploadUiState(
            isLoading = true,
            queuedItems = files.mapIndexed { index, file ->
                val status = UploadItemStatus.entries[listOf(2, 1, 3, 0)[index]]
                UploadItem(
                    id = index,
                    uri = Uri.EMPTY,
                    name = file.name,
                    sizeBytes = file.size,
                    mimeType = file.mimeType,
                    status = status,
                    uploadedBytes = if (status == UploadItemStatus.UPLOADING) (file.size ?: 0L) / 3 else 0L,
                    fileId = if (status == UploadItemStatus.DONE) "aBcD1234" else null,
                    errorMessage = if (status == UploadItemStatus.FAILED) "The host refused the file: it's too big." else null
                )
            }
        )
    )
}

@ScreenPreviews
@Composable
private fun UploadTextPreview() = UploadPreview(UploadUiState(textToUpload = PreviewSamples.CODE), tab = 1)

/** Uploaded: the result card with the file's link. */
@ScreenPreviews
@Composable
private fun UploadDonePreview() = UploadPreview(UploadUiState(uploadResult = UploadResult(success = true, id = "aBcD1234")))
