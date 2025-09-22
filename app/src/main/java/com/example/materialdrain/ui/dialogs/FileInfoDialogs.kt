package com.example.materialdrain.ui.dialogs

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import android.net.Uri
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.ImageNotSupported
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.DefaultTimeBar
import androidx.media3.ui.PlayerView
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.decode.ImageDecoderDecoder
import coil.imageLoader // Added import for Context.imageLoader extension
import coil.request.ImageRequest
import coil.target.Target
import com.example.materialdrain.network.FileInfoResponse
import com.example.materialdrain.ui.formatApiDateTimeString
import com.example.materialdrain.ui.formatSize
import com.example.materialdrain.viewmodel.DownloadStatus
import com.example.materialdrain.viewmodel.FileInfoViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun ClickablePreviewOverlay(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(12.dp)) // Clip the overlay to match card radius
            .clickable(onClick = onClick)
            .padding(8.dp),
        contentAlignment = Alignment.BottomEnd
    ) {
        Surface(
            modifier = Modifier
                .size(32.dp)
                .alpha(0.7f),
            shape = CircleShape,
            color = Color.Black.copy(alpha = 0.5f)
        ) {
            Icon(
                imageVector = Icons.Filled.Fullscreen,
                contentDescription = "View Fullscreen",
                tint = Color.White,
                modifier = Modifier.padding(4.dp)
            )
        }
    }
}

@androidx.media3.common.util.UnstableApi // Corrected: Direct annotation
@Composable
fun VideoPlayerControls(videoUri: Uri, thumbnailUrl: String?, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val playedColor = MaterialTheme.colorScheme.primary.toArgb()
    val scrubberColor = MaterialTheme.colorScheme.primary.toArgb()
    val bufferedColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f).toArgb()
    val unplayedColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f).toArgb()

    val exoPlayer = remember(videoUri) { // Keyed by videoUri to re-create if URI changes
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(videoUri))
            prepare()
            playWhenReady = false // User clicks play
        }
    }

    var artworkDrawable by remember { mutableStateOf<Drawable?>(null) }
    val coilImageLoader = context.imageLoader

    LaunchedEffect(thumbnailUrl, coilImageLoader) {
        if (thumbnailUrl != null) {
            val request = ImageRequest.Builder(context)
                .data(thumbnailUrl)
                .target(object : Target {
                    override fun onSuccess(result: Drawable) {
                        artworkDrawable = result
                    }
                    override fun onError(error: Drawable?) {
                        artworkDrawable = null // Or a default error drawable
                    }
                })
                .build()
            coilImageLoader.enqueue(request)
        } else {
            artworkDrawable = null
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            exoPlayer.release()
        }
    }

    AndroidView(
        factory = {
            PlayerView(it).apply {
                player = exoPlayer
                useController = true
                defaultArtwork = artworkDrawable

                val timeBar = findViewById<DefaultTimeBar>(androidx.media3.ui.R.id.exo_progress)
                timeBar?.setPlayedColor(playedColor)
                timeBar?.setScrubberColor(scrubberColor)
                timeBar?.setBufferedColor(bufferedColor)
                timeBar?.setUnplayedColor(unplayedColor)

                findViewById<View>(androidx.media3.ui.R.id.exo_settings)?.visibility = View.GONE
                findViewById<View>(androidx.media3.ui.R.id.exo_subtitle)?.visibility = View.GONE
                findViewById<View>(androidx.media3.ui.R.id.exo_fullscreen)?.visibility = View.GONE
                findViewById<View>(androidx.media3.ui.R.id.exo_prev)?.visibility = View.GONE
                findViewById<View>(androidx.media3.ui.R.id.exo_next)?.visibility = View.GONE
                findViewById<View>(androidx.media3.ui.R.id.exo_rew)?.visibility = View.GONE
                findViewById<View>(androidx.media3.ui.R.id.exo_ffwd)?.visibility = View.GONE
            }
        },
        update = { playerView ->
            playerView.defaultArtwork = artworkDrawable
        },
        modifier = modifier
    )
}

@androidx.media3.common.util.UnstableApi // Corrected: Direct annotation as it calls VideoPlayerControls
@Composable
fun FullScreenMediaPreviewDialog(
    previewUri: Uri?,
    previewMimeType: String?,
    thumbnailUrl: String?,
    onDismissRequest: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.8f))
                .clickable { onDismissRequest() },
            contentAlignment = Alignment.Center
        ) {
            if (previewUri != null) {
                Box(
                    modifier = Modifier.clickable(enabled = false) {}
                ) {
                    when {
                        previewMimeType?.startsWith("video/") == true -> {
                            VideoPlayerControls(
                                videoUri = previewUri,
                                thumbnailUrl = thumbnailUrl,
                                modifier = Modifier.fillMaxSize().padding(8.dp)
                            )
                        }
                        previewMimeType == "image/gif" -> {
                            val context = LocalContext.current
                            val imageLoader = remember {
                                ImageLoader.Builder(context)
                                    .components {
                                        add(ImageDecoderDecoder.Factory())
                                    }
                                    .build()
                            }
                            AsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data(previewUri)
                                    .crossfade(true)
                                    .build(),
                                imageLoader = imageLoader,
                                contentDescription = "Fullscreen GIF Preview",
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(16.dp),
                                contentScale = ContentScale.Fit
                            )
                        }
                        previewMimeType?.startsWith("image/") == true -> {
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(previewUri)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = "Fullscreen Image Preview",
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(16.dp),
                                contentScale = ContentScale.Fit,
                                onError = { onDismissRequest() }
                            )
                        }
                        else -> {
                            Text("Unsupported preview type for fullscreen.", color = Color.White)
                        }
                    }
                }
            } else {
                Text("Preview unavailable.", color = Color.White)
            }
        }
    }
}

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
fun FileInfoDetailsCard(fileInfo: FileInfoResponse, fileInfoViewModel: FileInfoViewModel, context: Context) {
    val uiState by fileInfoViewModel.uiState.collectAsState()
    val localClipboardManager = LocalContext.current.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    var fullScreenPreviewUri by remember { mutableStateOf<Uri?>(null) }
    var fullScreenPreviewMimeType by remember { mutableStateOf<String?>(null) }
    var showPreviews by remember { mutableStateOf(false) }

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

    Column( // Changed from Card to Column
        modifier = Modifier.fillMaxSize().padding(16.dp) // Changed modifier for full page
    ) {
        // Removed the Row with title and close button, title will be in TopAppBar

        Column(
            modifier = Modifier
                .weight(1f) // Takes remaining space and allows scrolling
                .verticalScroll(rememberScrollState())
        ) {
            if (showPreviews) {
                if (fileInfo.mimeType?.startsWith("image/") == true) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f)
                            .padding(vertical = 8.dp)
                            .clip(RoundedCornerShape(12.dp)), 
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            var imageState by remember { mutableStateOf<AsyncImagePainter.State>(AsyncImagePainter.State.Loading(null)) }
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(rawFileApiUrl)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = "Image preview",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop,
                                onState = { state -> imageState = state }
                            )
                            when (imageState) {
                                is AsyncImagePainter.State.Loading -> CircularProgressIndicator(modifier = Modifier.size(48.dp))
                                is AsyncImagePainter.State.Error -> Icon(Icons.Filled.BrokenImage, contentDescription = "Error loading image", modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.error)
                                is AsyncImagePainter.State.Success -> {
                                    ClickablePreviewOverlay {
                                        fullScreenPreviewUri = rawFileApiUrl.toUri()
                                        fullScreenPreviewMimeType = fileInfo.mimeType
                                    }
                                }
                                else -> {}
                            }
                        }
                    }
                } else if (fileInfo.mimeType?.startsWith("video/") == true) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f)
                            .padding(vertical = 8.dp)
                            .clip(RoundedCornerShape(12.dp)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            AsyncImage(
                                model = actualThumbnailUrl, 
                                contentDescription = "Video thumbnail",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop,
                                error = rememberVectorPainter(Icons.Filled.Videocam)
                            )
                            ClickablePreviewOverlay {
                                fullScreenPreviewUri = rawFileApiUrl.toUri()
                                fullScreenPreviewMimeType = fileInfo.mimeType
                            }
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
                    AsyncImage(
                        model = actualThumbnailUrl, 
                        contentDescription = "File thumbnail",
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 100.dp, max = 200.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .align(Alignment.CenterHorizontally)
                            .padding(vertical = 8.dp),
                        contentScale = ContentScale.Fit,
                        error = rememberVectorPainter(Icons.Filled.ImageNotSupported)
                    )
                }
            } else {
                Box(modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp).padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                    // Intentionally empty or light placeholder to prevent jank
                }
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

            Text("Actions", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 12.dp))

            Button(
                onClick = {
                    val sendIntent: Intent = Intent().apply {
                        action = Intent.ACTION_SEND
                        putExtra(Intent.EXTRA_TEXT, fileUrl)
                        type = "text/plain"
                    }
                    val shareIntent = Intent.createChooser(sendIntent, null)
                    context.startActivity(shareIntent)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Filled.Share, contentDescription = "Share Link")
                Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                Text("Share", maxLines = 1)
            }

            Spacer(Modifier.height(8.dp))

            OutlinedButton(
                onClick = {
                    val clip = ClipData.newPlainText("Pixeldrain URL", fileUrl)
                    localClipboardManager.setPrimaryClip(clip)
                    coroutineScope.launch {
                        snackbarHostState.showSnackbar("Link copied to clipboard!")
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Filled.ContentCopy, contentDescription = "Copy Link")
                Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                Text("Copy Link", maxLines = 1)
            }

            Spacer(Modifier.height(8.dp))

            val downloadButtonEnabled = downloadState == null ||
                    downloadState.status == DownloadStatus.PENDING ||
                    downloadState.status == DownloadStatus.COMPLETED ||
                    downloadState.status == DownloadStatus.FAILED

            Button(
                onClick = {
                    if (downloadState == null || downloadState.status == DownloadStatus.FAILED || downloadState.status == DownloadStatus.COMPLETED) {
                        fileInfoViewModel.initiateDownloadFile(fileInfo)
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar("Download started for ${fileInfo.name}")
                        }
                    } else if (downloadState.status == DownloadStatus.PENDING) {
                        // Optionally allow cancellation here or just rely on disabling
                         coroutineScope.launch {
                            snackbarHostState.showSnackbar("Download is already pending for ${fileInfo.name}")
                        }
                    }
                },
                enabled = downloadButtonEnabled,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Filled.Download, contentDescription = "Download")
                Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                Text(
                    when (downloadState?.status) {
                        DownloadStatus.DOWNLOADING -> "Downloading..."
                        DownloadStatus.COMPLETED -> "Download Again"
                        DownloadStatus.FAILED -> "Retry Download"
                        DownloadStatus.PENDING -> "Download Pending..."
                        else -> "Download File"
                    }
                )
            }

            downloadState?.let { state ->
                Spacer(Modifier.height(8.dp))
                when (state.status) {
                    DownloadStatus.DOWNLOADING -> {
                        LinearProgressIndicator(
                            progress = { state.progressFraction },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "${formatSize(state.downloadedBytes)} / ${state.totalBytes?.let { formatSize(it) } ?: "Unknown"}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    DownloadStatus.COMPLETED -> {
                        Text(
                            state.message ?: "Download completed successfully.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                         TextButton(onClick = { fileInfoViewModel.clearDownloadState(fileInfo.id) }) {
                            Text("Clear Status")
                        }
                    }
                    DownloadStatus.FAILED -> {
                        Text(
                            state.message ?: "Download failed.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                         TextButton(onClick = { fileInfoViewModel.clearDownloadState(fileInfo.id) }) {
                            Text("Clear Status")
                        }
                    }
                    DownloadStatus.PENDING -> {
                         Text(
                            "Download is pending...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }


            if (fileInfo.canEdit == true) {
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { fileInfoViewModel.initiateDeleteFile(fileInfo.id) },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.Delete, contentDescription = "Delete")
                    Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                    Text("Delete File", maxLines = 1) // Changed text for clarity
                }
            }
            Spacer(Modifier.height(16.dp)) // Added more space at the bottom
        }

        Box( // This Box will be at the bottom of the main Column (due to weight(1f) above)
            modifier = Modifier
                .fillMaxWidth() // Take full width for the snackbar host
                .padding(bottom = 8.dp), // Some padding from the very bottom edge
            contentAlignment = Alignment.BottomCenter // Align SnackbarHost to the bottom center
        ) {
            SnackbarHost(hostState = snackbarHostState) { data ->
                Snackbar(
                    snackbarData = data,
                    containerColor = MaterialTheme.colorScheme.inverseSurface,
                    contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                )
            }
        }
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
