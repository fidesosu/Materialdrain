package tools.senko.materialdrain.upload

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.BitmapFactory
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import tools.senko.materialdrain.ui.components.InfoRow
import tools.senko.materialdrain.ui.media.AudioPlayerPreview
import tools.senko.materialdrain.ui.media.FullScreenMediaPreviewDialog
import tools.senko.materialdrain.ui.media.InlineImagePreview
import tools.senko.materialdrain.ui.media.InlineTextPreview
import tools.senko.materialdrain.ui.media.InlineVideoPreview
import tools.senko.materialdrain.util.formatDurationMillis
import tools.senko.materialdrain.util.formatSize

@Composable
fun UploadScreenContent(
    uploadViewModel: UploadViewModel,
    fabHeight: Dp,
    isFabVisible: Boolean
) {
    val uiState by uploadViewModel.uiState.collectAsState()
    val context = LocalContext.current
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val tabTitles = listOf("Upload File", "Upload Text")


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
            uploadViewModel.onFilesSelected(uris, context)
            if (uris.isNotEmpty() && selectedTabIndex == 0) {
                uploadViewModel.onTextToUploadChanged("")
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
                        if (index == 0) uploadViewModel.onTextToUploadChanged("")
                        else uploadViewModel.onFileSelected(null, context)
                    },
                    text = { Text(title) }
                )
            }
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
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
                    val hasSelection = uiState.selectedFileName != null || uiState.queuedItems.isNotEmpty()
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
                            Text(if (uiState.queuedItems.isNotEmpty()) "Choose different files" else "Choose a different file")
                        }
                    }
                    if (uiState.queuedItems.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        QueuedFilesCard(
                            items = uiState.queuedItems,
                            isUploading = uiState.isLoading,
                            onRemove = uploadViewModel::removeQueuedItem,
                            onClear = uploadViewModel::clearQueuedItems
                        )
                    }
                    uiState.selectedFileName?.let {
                        Spacer(modifier = Modifier.height(8.dp))
                        Card(modifier = Modifier.fillMaxWidth()){
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("Selected File:", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 8.dp))
                                InfoRow("Name:", uiState.selectedFileName ?: "N/A")
                                uiState.uploadTotalSizeBytes?.let { s -> InfoRow("Size:", formatSize(s)) }
                                uiState.selectedFileMimeType?.let { mt -> InfoRow("Type:", mt) }

                                if (uiState.selectedFileMimeType?.startsWith("audio/") == true) {
                                    uiState.audioDurationMillis?.let { d -> InfoRow("Duration:", formatDurationMillis(d)) }
                                    uiState.audioBitrate?.let { b -> InfoRow("Bitrate:", "${b / 1000} kbps") }
                                    uiState.audioArtist?.let { a -> InfoRow("Artist:", a) }
                                    uiState.audioAlbum?.let { al -> InfoRow("Album:", al) }
                                }

                                if (uiState.selectedFileMimeType?.startsWith("video/") == true) {
                                    uiState.videoDurationMillis?.let { d -> InfoRow("Duration:", formatDurationMillis(d)) }
                                }

                                if (uiState.selectedFileMimeType == "application/pdf") {
                                    uiState.pdfPageCount?.let { pc -> InfoRow("Pages:", pc.toString()) }
                                }

                                if (uiState.selectedFileMimeType == "application/vnd.android.package-archive") {
                                    uiState.apkPackageName?.let { pn -> InfoRow("Package:", pn) }
                                    uiState.apkVersionName?.let { vn -> InfoRow("Version:", vn) }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))

                        if (uiState.selectedFileMimeType?.startsWith("image/") == true) {
                            InlineImagePreview(
                                imageSource = uiState.selectedFileUri,
                                contentDescription = "Selected image preview",
                                apiKey = null,
                                onFullScreenClick = {
                                    fullScreenPreviewUri = uiState.selectedFileUri
                                    fullScreenPreviewMimeType = uiState.selectedFileMimeType
                                }
                            )
                        }

                        if (uiState.selectedFileMimeType?.startsWith("video/") == true) {
                            InlineVideoPreview(
                                thumbnailSource = uiState.videoThumbnail, // Upload screen uses fetched byte array for thumbnail
                                contentDescription = "Selected video preview",
                                apiKey = null,
                                onFullScreenClick = {
                                    fullScreenPreviewUri = uiState.selectedFileUri
                                    fullScreenPreviewMimeType = uiState.selectedFileMimeType
                                }
                            )
                        }

                        if (uiState.selectedFileUri != null && uiState.selectedFileMimeType == "application/pdf" && uiState.pdfPageCount != null) {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(100.dp)
                                    .padding(vertical = 8.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Filled.PictureAsPdf, contentDescription = "PDF File", modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }

                        if (uiState.selectedFileUri != null && uiState.selectedFileMimeType == "application/vnd.android.package-archive") {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 80.dp)
                                    .padding(vertical = 8.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Box(
                                    modifier = Modifier.fillMaxSize().padding(16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    uiState.apkIcon?.let {
                                        val bitmap = remember(it) { BitmapFactory.decodeByteArray(it, 0, it.size) }
                                        Image(
                                            bitmap = bitmap.asImageBitmap(),
                                            contentDescription = "APK icon preview",
                                            modifier = Modifier.size(64.dp),
                                            contentScale = ContentScale.Fit
                                        )
                                    } ?: Icon(Icons.Filled.Android, contentDescription = "APK File", modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }

                        if (uiState.selectedFileUri != null && uiState.selectedFileMimeType?.startsWith("audio/") == true) {
                            AudioPlayerPreview(
                                audioUri = uiState.selectedFileUri!!,
                                apiKey = null,
                                title = null,
                                artist = uiState.audioArtist,
                                album = uiState.audioAlbum,
                                albumArtSource = uiState.audioAlbumArt,
                                durationHintMillis = uiState.audioDurationMillis
                            )
                        }

                        InlineTextPreview(textContent = uiState.selectedFileTextContent)

                        if (uiState.errorMessage?.contains("preview", true) == true || uiState.errorMessage?.contains("metadata", true) == true) {
                            Text(uiState.errorMessage!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(bottom=8.dp))
                        }
                    }
                }
                1 -> {
                    OutlinedTextField(
                        value = uiState.textToUpload,
                        onValueChange = uploadViewModel::onTextToUploadChanged,
                        label = { Text("Paste text here") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 240.dp),
                        maxLines = 12,
                        enabled = !uiState.isLoading,
                        supportingText = {
                            val size = uiState.textToUpload.toByteArray().size.toLong()
                            Text(
                                text = if (size > 0) "${uiState.textToUpload.length} characters · ${formatSize(size)}" else "Uploaded as a .txt file",
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.End
                            )
                        }
                    )
                }
            }

            if (uiState.isLoading) {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(onClick = uploadViewModel::cancelUpload, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.Close, contentDescription = null)
                    Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                    Text("Cancel upload")
                }
            }

            if (!uiState.isLoading && uploadResult == null) {
                val effectiveSize = if (selectedTabIndex == 0) uiState.uploadTotalSizeBytes else uiState.textToUpload.toByteArray().size.toLong().takeIf { it > 0 }
                if (effectiveSize != null && (uiState.selectedFileUri != null || uiState.textToUpload.isNotBlank())) {
                    Text("Ready to upload. Size: ${formatSize(effectiveSize)}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(bottom=8.dp), textAlign = TextAlign.Center)
                }
            }
            // Add Spacer at the end of the scrollable content if FAB is visible
            if (isFabVisible) {
                Spacer(Modifier.height(fabHeight + 16.dp)) // 16.dp for extra margin
            }
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
        shape = RoundedCornerShape(8.dp),
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
                "Tap to choose files",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary.copy(alpha = contentAlpha),
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "Anywhere in this area. You can pick more than one file.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = contentAlpha),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun UploadResultCard(fileId: String?) {
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
                Text("Upload complete", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                if (fileId != null) {
                    Text(
                        "pixeldrain.com/u/$fileId",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (fileId != null) {
                IconButton(onClick = { copyToClipboard(context, pixeldrainLink(fileId), "Link copied to clipboard") }) {
                    Icon(Icons.Filled.ContentCopy, contentDescription = "Copy link", tint = MaterialTheme.colorScheme.onPrimaryContainer)
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
                        text = if (doneCount > 0) "$doneCount of ${items.size} uploaded" else "${items.size} files selected",
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
                        copyToClipboard(context, links, "Links copied to clipboard")
                    }) { Text("Copy links") }
                }
                TextButton(onClick = onClear, enabled = !isUploading) { Text("Clear") }
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
    val context = LocalContext.current
    val size = item.sizeBytes?.takeIf { it > 0 }
    val fraction = size?.let { (item.uploadedBytes.toFloat() / it).coerceIn(0f, 1f) }
    val animatedFraction by animateFloatAsState(targetValue = fraction ?: 0f, label = "queuedItemProgress")

    val subtitle = when (item.status) {
        UploadItemStatus.PENDING -> item.sizeBytes?.let { formatSize(it) } ?: "Unknown size"
        UploadItemStatus.UPLOADING ->
            if (size != null) "${((fraction ?: 0f) * 100).toInt()}% · ${formatSize(item.uploadedBytes)} / ${formatSize(size)}"
            else formatSize(item.uploadedBytes)
        UploadItemStatus.DONE -> "Uploaded" + (item.sizeBytes?.let { " · ${formatSize(it)}" } ?: "")
        UploadItemStatus.FAILED -> item.errorMessage ?: "Upload failed"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(24.dp), contentAlignment = Alignment.Center) {
            when (item.status) {
                UploadItemStatus.PENDING -> Icon(Icons.Filled.Schedule, contentDescription = "Waiting", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                UploadItemStatus.UPLOADING ->
                    if (fraction != null) CircularProgressIndicator(progress = { animatedFraction }, modifier = Modifier.size(22.dp), strokeWidth = 3.dp)
                    else CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 3.dp)
                UploadItemStatus.DONE -> Icon(Icons.Filled.CheckCircle, contentDescription = "Uploaded", tint = MaterialTheme.colorScheme.primary)
                UploadItemStatus.FAILED -> Icon(Icons.Filled.Error, contentDescription = "Failed", tint = MaterialTheme.colorScheme.error)
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
            IconButton(onClick = { copyToClipboard(context, pixeldrainLink(fileId), "Link copied to clipboard") }) {
                Icon(Icons.Filled.ContentCopy, contentDescription = "Copy link")
            }
        } else if (!isUploading) {
            IconButton(onClick = { onRemove(item.id) }) {
                Icon(Icons.Filled.Close, contentDescription = "Remove from list")
            }
        } else {
            Spacer(Modifier.size(48.dp))
        }
    }
}
