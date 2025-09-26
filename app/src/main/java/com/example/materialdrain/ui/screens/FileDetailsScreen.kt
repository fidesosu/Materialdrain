package com.example.materialdrain.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ImageNotSupported
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import coil.compose.AsyncImage
import com.example.materialdrain.network.FileInfoResponse
import com.example.materialdrain.ui.formatApiDateTimeString
import com.example.materialdrain.ui.formatSize
import com.example.materialdrain.ui.shared.FullScreenMediaPreviewDialog
import com.example.materialdrain.ui.shared.InlineImagePreview
import com.example.materialdrain.ui.shared.InlineVideoPreview
import com.example.materialdrain.viewmodel.DownloadStatus
import com.example.materialdrain.viewmodel.FileInfoViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun EnterFileIdDialog(fileInfoViewModel: FileInfoViewModel) {
    val uiState by fileInfoViewModel.uiState.collectAsState()

    AlertDialog(
        onDismissRequest = { fileInfoViewModel.dismissEnterFileIdDialog() },
        title = { Text("Search File by ID") },
        text = {
            Column {
                OutlinedTextField(
                    value = uiState.fileIdInput,
                    onValueChange = { fileInfoViewModel.onFileIdInputChange(it) },
                    label = { Text("Enter File ID") },
                    singleLine = true,
                    isError = uiState.fileInfoErrorMessage?.let { it.contains("Please enter", true) || it.contains("not found", true) || it.contains("error fetching", true) } == true,
                    keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = {
                        if (uiState.fileIdInput.isNotBlank()) {
                            fileInfoViewModel.fetchFileInfoFromDialogInput()
                        }
                    })
                )
                uiState.fileInfoErrorMessage?.let {
                    if (it != "Please enter or select a File ID.") {
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (uiState.fileIdInput.isNotBlank()) {
                        fileInfoViewModel.fetchFileInfoFromDialogInput()
                    }
                },
                enabled = uiState.fileIdInput.isNotBlank() && !uiState.isLoadingFileInfo
            ) {
                if (uiState.isLoadingFileInfo) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp))
                } else {
                    Text("Search")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = { fileInfoViewModel.dismissEnterFileIdDialog() }) {
                Text("Cancel")
            }
        }
    )
}

@Composable
@androidx.media3.common.util.UnstableApi
fun FileInfoDetailsCard(
    fileInfo: FileInfoResponse,
    fileInfoViewModel: FileInfoViewModel,
    context: Context,
    snackbarHostState: SnackbarHostState // Added parameter
) {
    val uiState by fileInfoViewModel.uiState.collectAsState()
    val localClipboardManager = LocalContext.current.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    // Removed local snackbarHostState, will use the one passed as parameter
    val coroutineScope = rememberCoroutineScope()

    var fullScreenPreviewUri by remember { mutableStateOf<Uri?>(null) }
    var fullScreenPreviewMimeType by remember { mutableStateOf<String?>(null) }
    var showPreviews by remember { mutableStateOf(false) }

    // FAB Menu states
    var isFabMenuExpanded by remember { mutableStateOf(false) }
    var detailsFabHeightDp by remember { mutableStateOf(0.dp) }
    val localDensity = LocalDensity.current

    val actualThumbnailUrl = "https://pixeldrain.com/api/file/${fileInfo.id}/thumbnail"
    val downloadState = uiState.activeDownloads[fileInfo.id]

    LaunchedEffect(fileInfo.id) {
        delay(100)
        showPreviews = true
    }

    if (fullScreenPreviewUri != null) {
        FullScreenMediaPreviewDialog(
            previewUri = fullScreenPreviewUri,
            previewMimeType = fullScreenPreviewMimeType,
            thumbnailUrl = actualThumbnailUrl,
            onDismissRequest = {
                fullScreenPreviewUri = null
                fullScreenPreviewMimeType = null
            }
        )
    }

    val fileUrl = "https://pixeldrain.com/u/${fileInfo.id}"
    val rawFileApiUrl = "https://pixeldrain.com/api/file/${fileInfo.id}"

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 16.dp, end = 16.dp, top = 16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            if (showPreviews) {
                if (fileInfo.mimeType?.startsWith("image/") == true) {
                    InlineImagePreview(
                        imageSource = rawFileApiUrl,
                        contentDescription = "Image preview for ${fileInfo.name}",
                        onFullScreenClick = {
                            fullScreenPreviewUri = rawFileApiUrl.toUri()
                            fullScreenPreviewMimeType = fileInfo.mimeType
                        }
                    )
                } else if (fileInfo.mimeType?.startsWith("video/") == true) {
                    InlineVideoPreview(
                        thumbnailSource = actualThumbnailUrl,
                        contentDescription = "Video thumbnail for ${fileInfo.name}",
                        onFullScreenClick = {
                            fullScreenPreviewUri = rawFileApiUrl.toUri()
                            fullScreenPreviewMimeType = fileInfo.mimeType
                        }
                    )
                } else if (uiState.isLoadingTextPreview) {
                    Box(modifier = Modifier.fillMaxWidth().height(100.dp).padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else if (uiState.textPreviewContent != null) {
                    Text("Preview:", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
                    OutlinedTextField(
                        value = uiState.textPreviewContent ?: "",
                        onValueChange = {},
                        readOnly = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 100.dp, max = 200.dp)
                            .padding(vertical = 8.dp),
                        textStyle = MaterialTheme.typography.bodySmall
                    )
                } else if (uiState.textPreviewErrorMessage != null) {
                    Text(uiState.textPreviewErrorMessage ?: "Error loading text preview.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical=8.dp))
                } else {
                    AsyncImage(
                        model = actualThumbnailUrl,
                        contentDescription = "File thumbnail for ${fileInfo.name}",
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 100.dp, max = 200.dp)
                            .align(Alignment.CenterHorizontally)
                            .padding(vertical = 8.dp),
                        contentScale = ContentScale.Fit,
                        error = rememberVectorPainter(Icons.Filled.ImageNotSupported)
                    )
                }
            } else {
                Box(modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp).padding(vertical = 8.dp), contentAlignment = Alignment.Center) {}
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
            Text("File Details", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 8.dp))
            InfoRow("ID:", fileInfo.id, true)
            InfoRow("Size:", formatSize(fileInfo.size))
            fileInfo.mimeType?.let { InfoRow("Type:", it) }
            InfoRow("Upload Date:", formatApiDateTimeString(fileInfo.dateUpload))
            fileInfo.dateLastView?.let { InfoRow("Last View:", formatApiDateTimeString(it)) }
            fileInfo.views?.let { InfoRow("Views:", it.toString()) }
            fileInfo.downloads?.let { InfoRow("Downloads:", it.toString()) }
            fileInfo.hashSha256?.let { InfoRow("SHA256:", it, true) }

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

            downloadState?.let {
                Spacer(Modifier.height(8.dp))
                when (it.status) {
                    DownloadStatus.DOWNLOADING -> {
                        LinearProgressIndicator(progress = { it.progressFraction }, modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(4.dp))
                        Text("${formatSize(it.downloadedBytes)} / ${it.totalBytes?.let { tb -> formatSize(tb) } ?: "Unknown"}", style = MaterialTheme.typography.bodySmall)
                    }
                    DownloadStatus.COMPLETED -> {
                        // SnackBar for this will be shown by App.kt using the passed snackbarHostState
                        TextButton(onClick = { fileInfoViewModel.clearDownloadState(fileInfo.id) }) { Text("Clear Status") }
                    }
                    DownloadStatus.FAILED -> {
                        // SnackBar for this will be shown by App.kt
                        TextButton(onClick = { fileInfoViewModel.clearDownloadState(fileInfo.id) }) { Text("Clear Status") }
                    }
                    DownloadStatus.PENDING -> {
                        Text("Download is pending...", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            if (fileInfo.canEdit == true) {
                Text("Admin Actions", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp, bottom = 8.dp))
                Button(
                    onClick = { fileInfoViewModel.initiateDeleteFile(fileInfo.id) },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.Delete, contentDescription = "Delete")
                    Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                    Text("Delete File", maxLines = 1)
                }
            }
            Spacer(modifier = Modifier.height(detailsFabHeightDp + 16.dp))
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 16.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (isFabMenuExpanded) {
                SmallFloatingActionButton(
                    onClick = {
                        val sendIntent: Intent = Intent().apply {
                            action = Intent.ACTION_SEND
                            putExtra(Intent.EXTRA_TEXT, fileUrl)
                            type = "text/plain"
                        }
                        context.startActivity(Intent.createChooser(sendIntent, null))
                        isFabMenuExpanded = false
                    },
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                ) { Icon(Icons.Filled.Share, "Share File") }

                SmallFloatingActionButton(
                    onClick = {
                        val clip = ClipData.newPlainText("Pixeldrain URL", fileUrl)
                        localClipboardManager.setPrimaryClip(clip)
                        coroutineScope.launch { snackbarHostState.showSnackbar("Link copied to clipboard!") }
                        isFabMenuExpanded = false
                    },
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                ) { Icon(Icons.Filled.ContentCopy, "Copy Link") }

                if (downloadState?.status != DownloadStatus.DOWNLOADING && downloadState?.status != DownloadStatus.PENDING) {
                    SmallFloatingActionButton(
                        onClick = {
                            fileInfoViewModel.initiateDownloadFile(fileInfo)
                            // The actual Snackbar for download start/status will be shown by App.kt
                            // using its own snackbarHostState which gets updates from the ViewModel.
                            // We can show a local one here if desired, but it might be redundant.
                            coroutineScope.launch { snackbarHostState.showSnackbar("Download initiated for ${fileInfo.name}") }
                            isFabMenuExpanded = false
                        },
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    ) { Icon(Icons.Filled.Download, "Download File") }
                }
            }

            FloatingActionButton(
                onClick = { isFabMenuExpanded = !isFabMenuExpanded },
                modifier = Modifier.onSizeChanged {
                    detailsFabHeightDp = with(localDensity) { it.height.toDp() }
                }
            ) {
                Icon(
                    imageVector = if (isFabMenuExpanded) Icons.Filled.Close else Icons.Filled.Menu,
                    contentDescription = if (isFabMenuExpanded) "Close FAB Menu" else "Open FAB Menu"
                )
            }
        }
        // Removed local SnackbarHost, App.kt's AppSnackbarHost will be used.
    }
}


@Composable
fun InfoRow(label: String, value: String, isValueSelectable: Boolean = false) {
    Row(
        modifier = Modifier.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier
                .defaultMinSize(minWidth = 90.dp)
                .padding(end = 8.dp)
        )
        if (isValueSelectable) {
            SelectionContainer {
                Text(
                    value,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f)
                )
            }
        } else {
            Text(
                value,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
        }
    }
}
