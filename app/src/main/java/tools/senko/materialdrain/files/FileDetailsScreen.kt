package tools.senko.materialdrain.files

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import androidx.compose.foundation.interaction.MutableInteractionSource // Added for clickable without ripple
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ImageNotSupported
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import coil.compose.AsyncImage
import coil.request.ImageRequest
import io.ktor.http.encodeURLPathPart
import kotlinx.coroutines.launch
import tools.senko.materialdrain.api.FileInfoResponse
import tools.senko.materialdrain.ui.media.AudioPlayerPreview
import tools.senko.materialdrain.ui.media.FullScreenMediaPreviewDialog
import tools.senko.materialdrain.ui.media.InlineImagePreview
import tools.senko.materialdrain.ui.media.InlineVideoPreview
import tools.senko.materialdrain.util.formatApiDateTimeString
import tools.senko.materialdrain.util.formatSize
import tools.senko.materialdrain.ui.components.InfoRow

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
            // Only the private filesystem needs the login for previews
            val previewApiKey = uiState.apiKey.takeIf { isFilesystemFile && it.isNotBlank() }
            if (fileInfo.mimeType?.startsWith("image/") == true) {
                InlineImagePreview(
                    imageSource = rawFileApiUrl,
                    thumbnailSource = actualThumbnailUrl,
                    contentDescription = "Image preview for ${fileInfo.name}",
                    apiKey = previewApiKey,
                    // This disables smooth filtering (nearest neighbor scaling), which removes the blur when scaling images.
                    filterQuality = FilterQuality.None,
                    onFullScreenClick = {
                        fullScreenPreviewUri = rawFileApiUrl.toUri()
                        fullScreenPreviewMimeType = fileInfo.mimeType
                    }
                )
            } else if (fileInfo.mimeType?.startsWith("video/") == true) {
                InlineVideoPreview(
                    thumbnailSource = actualThumbnailUrl,
                    contentDescription = "Video thumbnail for ${fileInfo.name}",
                    apiKey = previewApiKey,
                    onFullScreenClick = {
                        fullScreenPreviewUri = rawFileApiUrl.toUri()
                        fullScreenPreviewMimeType = fileInfo.mimeType
                    }
                )
            } else if (fileInfo.mimeType?.startsWith("audio/") == true) {
                AudioPlayerPreview(
                    audioUri = rawFileApiUrl.toUri(),
                    apiKey = previewApiKey,
                    title = fileInfo.name,
                    artist = null,
                    album = null,
                    albumArtSource = actualThumbnailUrl,
                    durationHintMillis = null
                )
            } else if (uiState.isLoadingTextPreview) {
                Box(modifier = Modifier.fillMaxWidth().height(100.dp).padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (uiState.textPreviewContent != null) {
                OutlinedTextField(
                    value = uiState.textPreviewContent ?: "",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Preview") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 25.dp, max = 250.dp)
                        .padding(vertical = 0.dp),
                    textStyle = MaterialTheme.typography.bodySmall
                )
            } else if (uiState.textPreviewErrorMessage != null) {
                Text(
                    uiState.textPreviewErrorMessage ?: "Error loading text preview.", 
                    color = MaterialTheme.colorScheme.error, 
                    style = MaterialTheme.typography.bodySmall, 
                    modifier = Modifier.padding(vertical=8.dp)
                )
            } 
            
            else {
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
            label = "Link",
            value = if (isFilesystemFile) {
                "pixeldrain.com/d/" + fileInfo.id.removePrefix("/")
            } else {
                "pixeldrain.com/u/" + fileInfo.id
            },
            isValueSelectable = true,
            onValueClick = {
                val link = if (isFilesystemFile) {
                    "pixeldrain.com/d/" + fileInfo.id.removePrefix("/")
                } else {
                    "pixeldrain.com/u/" + fileInfo.id
                }

                val clip = ClipData.newPlainText("File Link", link)
                clipboardManager.setPrimaryClip(clip)

                coroutineScope.launch {
                    snackbarHostState.showSnackbar("Link copied to clipboard!")
                }
            }
        )

        // Only show the ID for non-filesystem files, since filesystem "IDs" are actually paths and can be very long and not look user-friendly. (this will possibly be changed)
        if (!isFilesystemFile) {
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
        }
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
