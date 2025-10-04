package com.example.materialdrain.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource // Added for clickable without ripple
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ImageNotSupported
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.materialdrain.network.FileInfoResponse
import com.example.materialdrain.ui.formatApiDateTimeString
import com.example.materialdrain.ui.formatSize
import com.example.materialdrain.ui.shared.FullScreenMediaPreviewDialog
import com.example.materialdrain.viewmodel.FileInfoViewModel
import io.ktor.http.encodeURLPathPart
import kotlinx.coroutines.launch

private const val TAG_FILE_DETAILS_SCREEN = "FileDetailsScreen"

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
    snackbarHostState: SnackbarHostState
) {
    val uiState by fileInfoViewModel.uiState.collectAsState()
    val localContext = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val clipboardManager = localContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

    var fullScreenPreviewUri by remember { mutableStateOf<Uri?>(null) }
    var fullScreenPreviewMimeType by remember { mutableStateOf<String?>(null) }
    var showPreviews by remember { mutableStateOf(false) }

    val isFilesystemFile = fileInfo.id.startsWith("/")

    val (actualThumbnailUrl, rawFileApiUrl) = remember(fileInfo.id, isFilesystemFile) {
        if (isFilesystemFile) {
            val encodedPath = fileInfo.id.removePrefix("/").split('/').joinToString("/") { it.encodeURLPathPart() }
            Pair(
                "https://pixeldrain.com/api/filesystem/$encodedPath?thumbnail",
                "https://pixeldrain.com/api/filesystem/$encodedPath"
            )
        } else {
            Pair(
                "https://pixeldrain.com/api/file/${fileInfo.id}/thumbnail",
                "https://pixeldrain.com/api/file/${fileInfo.id}"
            )
        }
    }

    LaunchedEffect(fileInfo.id) {
        showPreviews = true
    }

    if (fullScreenPreviewUri != null) {
        FullScreenMediaPreviewDialog(
            previewUri = fullScreenPreviewUri,
            previewMimeType = fullScreenPreviewMimeType,
            thumbnailUrl = actualThumbnailUrl,
            apiKey = uiState.apiKey,
            onDismissRequest = {
                fullScreenPreviewUri = null
                fullScreenPreviewMimeType = null
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        if (showPreviews) {
            if (fileInfo.mimeType?.startsWith("image/") == true) {
                val imageRequestBuilder = remember(rawFileApiUrl, isFilesystemFile, uiState.apiKey) {
                    val builder = ImageRequest.Builder(context).data(rawFileApiUrl).crossfade(true)
                    if (isFilesystemFile && uiState.apiKey.isNotBlank()) {
                        builder.addHeader("Cookie", "pd_auth_key=${uiState.apiKey}")
                        Log.d(TAG_FILE_DETAILS_SCREEN, "Added Cookie header for filesystem image request.")
                    }
                    builder.build()
                }
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .padding(vertical = 8.dp)
                        .clickable {
                            fullScreenPreviewUri = rawFileApiUrl.toUri()
                            fullScreenPreviewMimeType = fileInfo.mimeType
                        },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    AsyncImage(
                        model = imageRequestBuilder,
                        contentDescription = "Image preview for ${fileInfo.name}",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                        error = rememberVectorPainter(Icons.Filled.ImageNotSupported)
                    )
                }
            } else if (fileInfo.mimeType?.startsWith("video/") == true) {
                val thumbnailRequestBuilder = remember(actualThumbnailUrl, isFilesystemFile, uiState.apiKey) {
                    val builder = ImageRequest.Builder(context).data(actualThumbnailUrl).crossfade(true)
                    if (isFilesystemFile && uiState.apiKey.isNotBlank()) {
                        builder.addHeader("Cookie", "pd_auth_key=${uiState.apiKey}")
                        Log.d(TAG_FILE_DETAILS_SCREEN, "Added Cookie header for filesystem video thumbnail request.")
                    }
                    builder.build()
                }
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .padding(vertical = 8.dp)
                        .clickable {
                            fullScreenPreviewUri = rawFileApiUrl.toUri()
                            fullScreenPreviewMimeType = fileInfo.mimeType
                        },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        AsyncImage(
                            model = thumbnailRequestBuilder,
                            contentDescription = "Video thumbnail for ${fileInfo.name}",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                            error = rememberVectorPainter(Icons.Filled.ImageNotSupported)
                        )
                        Icon(
                            imageVector = Icons.Filled.PlayCircleOutline,
                            contentDescription = "Play Video",
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }
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
                val request = ImageRequest.Builder(localContext)
                    .data(actualThumbnailUrl)
                    .crossfade(true)
                    .build()

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .padding(vertical = 8.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    AsyncImage(
                        model = request,
                        contentDescription = "File thumbnail for ${fileInfo.name}",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                        error = rememberVectorPainter(Icons.Filled.ImageNotSupported)
                    )
                }
            }
        } else {
            Box(modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp).padding(vertical = 8.dp), contentAlignment = Alignment.Center) {}
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
        Text("File Details", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 8.dp))
        InfoRow(
            label = "ID",
            value = fileInfo.id,
            isValueSelectable = true,
            onValueClick = {
                val clip = ClipData.newPlainText("File ID", fileInfo.id)
                clipboardManager.setPrimaryClip(clip)
                coroutineScope.launch {
                    snackbarHostState.showSnackbar("ID copied to clipboard!")
                }
            }
        )
        InfoRow("Size", formatSize(fileInfo.size))
        fileInfo.mimeType?.let {
            InfoRow(
                label = "Type",
                value = it,
                isValueSelectable = true,
                onValueClick = {
                    val clip = ClipData.newPlainText("Mime Type", it)
                    clipboardManager.setPrimaryClip(clip)
                    coroutineScope.launch {
                        snackbarHostState.showSnackbar("Mime type copied to clipboard!")
                    }
                }
            )
        }
        InfoRow("Upload Date", formatApiDateTimeString(fileInfo.dateUpload))
        fileInfo.dateLastView?.let { InfoRow("Last View", formatApiDateTimeString(it)) }
        fileInfo.views?.let { InfoRow("Views", it.toString()) }
        fileInfo.downloads?.let { InfoRow("Downloads", it.toString()) }
        fileInfo.hashSha256?.let {
            InfoRow(
                label = "SHA256",
                value = it,
                isValueSelectable = true,
                onValueClick = {
                    val clip = ClipData.newPlainText("SHA256 Hash", it)
                    clipboardManager.setPrimaryClip(clip)
                    coroutineScope.launch {
                        snackbarHostState.showSnackbar("SHA256 hash copied to clipboard!")
                    }
                }
            )
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

        Spacer(modifier = Modifier.height(16.dp))
    }
}


@Composable
fun InfoRow(
    label: String,
    value: String,
    isValueSelectable: Boolean = false,
    onValueClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .padding(vertical = 4.dp)
            .fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "$label:",
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
            modifier = Modifier
                .defaultMinSize(minWidth = 110.dp)
                .padding(end = 8.dp)
        )
        val valueModifier = Modifier
            .weight(1f)
            .then(
                if (onValueClick != null) {
                    Modifier.clickable(
                        onClick = onValueClick,
                        indication = null, // Disable ripple
                        interactionSource = remember { MutableInteractionSource() } // Required for indication = null
                    )
                } else {
                    Modifier
                }
            )

        val valueColor = if (onValueClick != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface

        if (isValueSelectable) {
            SelectionContainer {
                Text(
                    value,
                    style = MaterialTheme.typography.bodyMedium,
                    color = valueColor,
                    modifier = valueModifier
                )
            }
        } else {
            Text(
                value,
                style = MaterialTheme.typography.bodyMedium,
                color = valueColor, // Apply color here too for non-selectable but clickable items if any in future
                modifier = valueModifier
            )
        }
    }
}
