package tools.senko.materialdrain.files

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import androidx.compose.foundation.interaction.MutableInteractionSource // Added for clickable without ripple
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import coil.compose.AsyncImage
import tools.senko.materialdrain.ui.media.imageRequest
import kotlinx.coroutines.launch
import tools.senko.materialdrain.provider.api.PixeldrainRichDetails
import tools.senko.materialdrain.provider.api.StorageNode
import tools.senko.materialdrain.ui.media.AudioPlayerPreview
import tools.senko.materialdrain.ui.media.FullScreenMediaPreviewDialog
import tools.senko.materialdrain.ui.media.InlineImagePreview
import tools.senko.materialdrain.ui.media.InlineVideoPreview
import tools.senko.materialdrain.util.formatApiDateTimeString
import tools.senko.materialdrain.util.formatSize
import tools.senko.materialdrain.ui.components.InfoRow

private const val TAG_FILE_DETAILS_SCREEN = "FileDetailsScreen"

@Composable
@androidx.media3.common.util.UnstableApi
fun FileInfoDetailsCard(
    fileInfo: StorageNode,
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

    // A file without an id is a path in the filesystem, which is private: only that needs the login for previews
    val isFilesystemFile = fileInfo.ref.id == null
    val rich = fileInfo.richDetails as? PixeldrainRichDetails
    val actualThumbnailUrl = fileInfoViewModel.thumbnailFor(fileInfo)
    val rawFileApiUrl = fileInfoViewModel.rawUrlFor(fileInfo).orEmpty()
    val shareUrl = fileInfoViewModel.shareUrlFor(fileInfo)

    LaunchedEffect(fileInfo.key) {
        showPreviews = true
    }

    val previewType = fileInfo.previewMimeType()

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
        // Only the private filesystem needs the login for previews
        val previewApiKey = uiState.apiKey.takeIf { isFilesystemFile && it.isNotBlank() }
        if (showPreviews) {
            if (previewType?.startsWith("image/") == true) {
                InlineImagePreview(
                    imageSource = rawFileApiUrl,
                    thumbnailSource = actualThumbnailUrl,
                    contentDescription = "Image preview for ${fileInfo.name}",
                    apiKey = previewApiKey,
                    // This disables smooth filtering (nearest neighbor scaling), which removes the blur when scaling images.
                    filterQuality = FilterQuality.None,
                    onFullScreenClick = {
                        fullScreenPreviewUri = rawFileApiUrl.toUri()
                        fullScreenPreviewMimeType = previewType
                    }
                )
            } else if (previewType?.startsWith("video/") == true) {
                InlineVideoPreview(
                    thumbnailSource = actualThumbnailUrl,
                    contentDescription = "Video thumbnail for ${fileInfo.name}",
                    apiKey = previewApiKey,
                    onFullScreenClick = {
                        fullScreenPreviewUri = rawFileApiUrl.toUri()
                        fullScreenPreviewMimeType = previewType
                    }
                )
            } else if (previewType?.startsWith("audio/") == true) {
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
                val request = imageRequest(localContext, actualThumbnailUrl.orEmpty()) { crossfade(true) }

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
        shareUrl?.let { link ->
            InfoRow(
                label = "Link",
                value = link.removePrefix("https://"),
                isValueSelectable = true,
                onValueClick = {
                    val clip = ClipData.newPlainText("File Link", link)
                    clipboardManager.setPrimaryClip(clip)

                    coroutineScope.launch {
                        snackbarHostState.showSnackbar("Link copied to clipboard!")
                    }
                }
            )
        }

        // Only show the ID for files with one, a filesystem path is shown as the file's name is
        fileInfo.ref.id?.let { id ->
            InfoRow(
                label = "ID",
                value = id,
                isValueSelectable = true,
                onValueClick = {
                    val clip = ClipData.newPlainText("File ID", id)
                    clipboardManager.setPrimaryClip(clip)
                    coroutineScope.launch {
                        snackbarHostState.showSnackbar("ID copied to clipboard!")
                    }
                }
            )
        }
        InfoRow("Size", formatSize(fileInfo.size ?: 0L))
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
        fileInfo.createdAt?.let { InfoRow("Upload Date", formatApiDateTimeString(it)) }
        fileInfo.modifiedAt?.let { InfoRow("Last View", formatApiDateTimeString(it)) }
        rich?.views?.let { InfoRow("Views", it.toString()) }
        rich?.downloads?.let { InfoRow("Downloads", it.toString()) }
        rich?.sha256?.let {
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
