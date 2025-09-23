package com.example.materialdrain.ui.screens

import android.graphics.BitmapFactory
import android.media.MediaPlayer
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.request.ImageRequest
import com.example.materialdrain.ui.dialogs.InfoRow
import com.example.materialdrain.ui.formatDurationMillis
import com.example.materialdrain.ui.formatSize
import com.example.materialdrain.ui.shared.*
import com.example.materialdrain.viewmodel.UploadUiState
import com.example.materialdrain.viewmodel.UploadViewModel
import kotlinx.coroutines.android.awaitFrame
import kotlinx.coroutines.isActive

private const val TAG_MEDIA_PLAYER = "MediaPlayerPreview"
private const val TAG_COIL = "CoilImageLoader" // Added for Coil logging

// ClickablePreviewOverlay, VideoPlayerControls, and FullScreenMediaPreviewDialog are removed from here

@Composable
fun UploadScreenContent(uploadViewModel: UploadViewModel) {
    val uiState by uploadViewModel.uiState.collectAsState()
    val context = LocalContext.current
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val tabTitles = listOf("Upload File", "Upload Text")

    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var currentPlaybackTimeMillis by remember { mutableLongStateOf(0L) }
    var audioPreviewError by remember { mutableStateOf<String?>(null) }
    var isMediaPlayerPreparing by remember { mutableStateOf(false) }

    var isUserScrubbing by remember { mutableStateOf(false) }
    var userSeekPositionMillis by remember { mutableLongStateOf(0L) }
    var progressBarWidthPx by remember { mutableStateOf(0) }
    var playbackTimeAtDragStart by remember { mutableLongStateOf(0L) }
    var initialTouchX by remember { mutableFloatStateOf(0f) }

    var fullScreenPreviewUri by remember { mutableStateOf<Uri?>(null) }
    var fullScreenPreviewMimeType by remember { mutableStateOf<String?>(null) }

    if (fullScreenPreviewUri != null) {
        FullScreenMediaPreviewDialog( // Now calls the shared composable
            previewUri = fullScreenPreviewUri,
            previewMimeType = fullScreenPreviewMimeType,
            thumbnailUrl = null, // Passing null as UploadScreen doesn't have a thumbnail URL for its fullscreen
            onDismissRequest = {
                fullScreenPreviewUri = null
                fullScreenPreviewMimeType = null
            }
        )
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { uri: Uri? ->
            uploadViewModel.onFileSelected(uri, context)
            if (uri != null && selectedTabIndex == 0) {
                uploadViewModel.onTextToUploadChanged("")
            }
            mediaPlayer?.release()
            mediaPlayer = null
            isPlaying = false
            currentPlaybackTimeMillis = 0L
            audioPreviewError = null
            isMediaPlayerPreparing = false
            isUserScrubbing = false
        }
    )

    DisposableEffect(key1 = uiState.selectedFileUri, key2 = selectedTabIndex, key3 = context) {
        var localMediaPlayerInstance: MediaPlayer? = null
        if (selectedTabIndex == 0 && uiState.selectedFileUri != null && uiState.selectedFileMimeType?.startsWith("audio/") == true) {
            isMediaPlayerPreparing = true
            mediaPlayer?.release() 
            mediaPlayer = null
            isPlaying = false
            currentPlaybackTimeMillis = 0L
            audioPreviewError = null
            isUserScrubbing = false

            localMediaPlayerInstance = MediaPlayer()
            try {
                localMediaPlayerInstance.setDataSource(context, uiState.selectedFileUri!!)
                localMediaPlayerInstance.prepareAsync()
                localMediaPlayerInstance.setOnPreparedListener {
                    mediaPlayer = it
                    isMediaPlayerPreparing = false
                }
                localMediaPlayerInstance.setOnErrorListener { mp, what, extra ->
                    audioPreviewError = "Cannot play audio (error $what, $extra)."
                    Log.e(TAG_MEDIA_PLAYER, "MediaPlayer Error: what=$what, extra=$extra for URI: ${uiState.selectedFileUri}")
                    mp?.release()
                    if (mediaPlayer == mp) { 
                        mediaPlayer = null
                    }
                    isMediaPlayerPreparing = false
                    isPlaying = false 
                    true 
                }
                localMediaPlayerInstance.setOnCompletionListener { mp ->
                    isPlaying = false
                    val duration = uiState.audioDurationMillis ?: 0L
                    currentPlaybackTimeMillis = duration
                    Log.d(TAG_MEDIA_PLAYER, "MediaPlayer playback completed. Time set to: $duration")
                }
            } catch (e: Exception) {
                audioPreviewError = "Error setting up audio player: ${e.message}"
                Log.e(TAG_MEDIA_PLAYER, "Error setting up audio player for URI: ${uiState.selectedFileUri}", e)
                localMediaPlayerInstance.release()
                mediaPlayer = null 
                isMediaPlayerPreparing = false
                isPlaying = false
            }
        } else {
            mediaPlayer?.release()
            mediaPlayer = null
            isPlaying = false
            currentPlaybackTimeMillis = 0L
            audioPreviewError = null
            isMediaPlayerPreparing = false
            isUserScrubbing = false
        }
        onDispose {
            localMediaPlayerInstance?.release()
            mediaPlayer?.release()
            mediaPlayer = null
            isMediaPlayerPreparing = false
            isPlaying = false
            isUserScrubbing = false
        }
    }

    LaunchedEffect(isPlaying, mediaPlayer, isUserScrubbing) {
        if (isPlaying && !isUserScrubbing && mediaPlayer != null && mediaPlayer?.isPlaying == true) {
            while (isActive) {
                try {
                    if (isPlaying && !isUserScrubbing && mediaPlayer?.isPlaying == true) {
                        currentPlaybackTimeMillis = mediaPlayer?.currentPosition?.toLong() ?: currentPlaybackTimeMillis
                    }
                } catch (e: IllegalStateException) {
                    Log.w(TAG_MEDIA_PLAYER, "MediaPlayer access error during playback: ${e.message}")
                    isPlaying = false 
                    audioPreviewError = "Player error during update."
                    break 
                }
                awaitFrame() 
                if (!isPlaying || mediaPlayer?.isPlaying == false || isUserScrubbing) {
                    break
                }
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = selectedTabIndex) {
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
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(16.dp))
            when (selectedTabIndex) {
                0 -> {
                    Button(onClick = { filePickerLauncher.launch("*/*") }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Filled.AttachFile, contentDescription = "Select File")
                        Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                        Text(uiState.selectedFileName ?: "Select File")
                    }
                    uiState.selectedFileName?.let {
                        Spacer(modifier = Modifier.height(16.dp))
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
                        Spacer(modifier = Modifier.height(16.dp))

                        if (uiState.selectedFileUri != null && uiState.selectedFileMimeType?.startsWith("image/") == true) {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(16f / 9f)
                                    .padding(vertical = 8.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    val imageRequest = ImageRequest.Builder(LocalContext.current)
                                        .data(uiState.selectedFileUri)
                                        .crossfade(true)
                                        .listener(onError = { _, result ->
                                            Log.e(TAG_COIL, "Error loading image: ${uiState.selectedFileUri}", result.throwable)
                                        })
                                        .build()
                                    var imageState by remember { mutableStateOf<AsyncImagePainter.State>(AsyncImagePainter.State.Loading(null)) }

                                    AsyncImage(
                                        model = imageRequest,
                                        contentDescription = "Selected image preview",
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop,
                                        onState = { state -> imageState = state }
                                    )

                                    when (imageState) {
                                        is AsyncImagePainter.State.Loading -> CircularProgressIndicator(modifier = Modifier.size(48.dp))
                                        is AsyncImagePainter.State.Error -> Icon(Icons.Filled.BrokenImage, contentDescription = "Error loading image", modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.error)
                                        is AsyncImagePainter.State.Success -> {
                                            ClickablePreviewOverlay { // Now calls the shared composable
                                                fullScreenPreviewUri = uiState.selectedFileUri
                                                fullScreenPreviewMimeType = uiState.selectedFileMimeType
                                            }
                                        }
                                        else -> {}
                                    }
                                }
                            }
                        }

                        if (uiState.selectedFileUri != null && uiState.selectedFileMimeType?.startsWith("video/") == true) {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(16f / 9f)
                                    .padding(vertical = 8.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    uiState.videoThumbnail?.let {
                                        val bitmap = remember(it) { BitmapFactory.decodeByteArray(it, 0, it.size) }
                                        Image(
                                            bitmap = bitmap.asImageBitmap(),
                                            contentDescription = "Video thumbnail preview",
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop
                                        )
                                        ClickablePreviewOverlay { // Now calls the shared composable
                                            fullScreenPreviewUri = uiState.selectedFileUri
                                            fullScreenPreviewMimeType = uiState.selectedFileMimeType
                                        }
                                    } ?: Icon(Icons.Filled.Videocam, contentDescription = "Video thumbnail placeholder", modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
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
                                uiState = uiState,
                                mediaPlayer = mediaPlayer,
                                isPlaying = isPlaying,
                                currentPlaybackTimeMillis = currentPlaybackTimeMillis,
                                userSeekPositionMillis = userSeekPositionMillis,
                                audioPreviewError = audioPreviewError,
                                isMediaPlayerPreparing = isMediaPlayerPreparing,
                                isUserScrubbing = isUserScrubbing,
                                progressBarWidthPx = progressBarWidthPx,
                                onPlayPause = {
                                    if (isMediaPlayerPreparing) return@AudioPlayerPreview
                                    mediaPlayer?.let {
                                        if (it.isPlaying) { it.pause(); isPlaying = false }
                                        else {
                                            if(isUserScrubbing) {
                                                it.seekTo(userSeekPositionMillis.toInt())
                                                currentPlaybackTimeMillis = userSeekPositionMillis
                                            }
                                            else if (currentPlaybackTimeMillis >= (uiState.audioDurationMillis ?: Long.MAX_VALUE) - 100 && (uiState.audioDurationMillis ?: 0L) > 0) {
                                                it.seekTo(0)
                                                currentPlaybackTimeMillis = 0L
                                            }
                                            it.start()
                                            isPlaying = true
                                            audioPreviewError = null
                                        }
                                    } ?: run { if (!isMediaPlayerPreparing) audioPreviewError = "Player not ready." }
                                },
                                onSeekBarWidthChanged = { progressBarWidthPx = it },
                                onDragStart = { offset, totalDuration, currentWidthPx ->
                                    isUserScrubbing = true
                                    initialTouchX = offset.x
                                    val initialPercentage = (offset.x / currentWidthPx).coerceIn(0f, 1f)
                                    userSeekPositionMillis = (initialPercentage * totalDuration).toLong()
                                    playbackTimeAtDragStart = userSeekPositionMillis
                                },
                                onDrag = { change, totalDuration, currentWidthPx ->
                                    val currentDragX = change.position.x
                                    val dragDeltaX = currentDragX - initialTouchX
                                    val timeDeltaMillis = (dragDeltaX / currentWidthPx) * totalDuration
                                    userSeekPositionMillis = (playbackTimeAtDragStart + timeDeltaMillis).toLong().coerceIn(0L, totalDuration)
                                    change.consume()
                                },
                                onDragEnd = {
                                    mediaPlayer?.seekTo(userSeekPositionMillis.toInt())
                                    currentPlaybackTimeMillis = userSeekPositionMillis
                                    isUserScrubbing = false
                                },
                                onDragCancel = { isUserScrubbing = false }
                            )
                        }

                        uiState.selectedFileTextContent?.takeIf { it.isNotBlank() }?.let {
                            Text("Content Preview (4KB Max):", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top=8.dp, bottom=4.dp))
                            OutlinedTextField(value = it, onValueChange = {}, readOnly = true, modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 100.dp, max = 200.dp)
                                .padding(vertical = 8.dp), textStyle = MaterialTheme.typography.bodySmall)
                        }
                        if (uiState.errorMessage?.contains("preview", true) == true || uiState.errorMessage?.contains("metadata", true) == true) {
                            Text(uiState.errorMessage!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(bottom=8.dp))
                        }
                    }
                }
                1 -> {
                    OutlinedTextField(value = uiState.textToUpload, onValueChange = uploadViewModel::onTextToUploadChanged, label = { Text("Paste text here") }, modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 150.dp), maxLines = 10, enabled = !uiState.isLoading)
                }
            }

            if (!uiState.isLoading) {
                uiState.uploadResult?.let {
                    if (it.success) Text("Success! ID: ${it.id}", color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom=8.dp), textAlign = TextAlign.Center)
                } ?: run {
                    val effectiveSize = if (selectedTabIndex == 0) uiState.uploadTotalSizeBytes else uiState.textToUpload.toByteArray().size.toLong().takeIf { it > 0 }
                    if (effectiveSize != null && (uiState.selectedFileUri != null || uiState.textToUpload.isNotBlank())) {
                        Text("Ready to upload. Size: ${formatSize(effectiveSize)}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(bottom=8.dp), textAlign = TextAlign.Center)
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
        Button(onClick = uploadViewModel::upload, enabled = (uiState.selectedFileUri != null || uiState.textToUpload.isNotBlank()) && !uiState.isLoading, modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)) {
            Icon(Icons.Filled.FileUpload, contentDescription = "Upload")
            Spacer(Modifier.size(ButtonDefaults.IconSpacing))
            Text("Upload")
        }
    }
}

@Composable
fun AudioPlayerPreview(
    uiState: UploadUiState,
    mediaPlayer: MediaPlayer?,
    isPlaying: Boolean,
    currentPlaybackTimeMillis: Long,
    userSeekPositionMillis: Long,
    audioPreviewError: String?,
    isMediaPlayerPreparing: Boolean,
    isUserScrubbing: Boolean,
    progressBarWidthPx: Int,
    onPlayPause: () -> Unit,
    onSeekBarWidthChanged: (Int) -> Unit,
    onDragStart: (offset: androidx.compose.ui.geometry.Offset, totalDuration: Long, progressBarWidthPx: Int) -> Unit,
    onDrag: (change: androidx.compose.ui.input.pointer.PointerInputChange, totalDuration: Long, progressBarWidthPx: Int) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit
) {
    Card(modifier = Modifier
        .fillMaxWidth()
        .padding(vertical = 8.dp)) {
        Column(modifier = Modifier.padding(bottom = 0.dp)) {
            Row(
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                uiState.audioAlbumArt?.let {
                    val bitmap = remember(it) { try { BitmapFactory.decodeByteArray(it, 0, it.size) } catch (_: Exception) { null } }
                    bitmap?.let {bmp -> Image(bitmap = bmp.asImageBitmap(), contentDescription = "Album Art", modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(8.dp)), contentScale = ContentScale.Crop) }
                        ?: Icon(Icons.Filled.MusicNote, contentDescription = "Album Art Placeholder", modifier = Modifier.size(64.dp))
                } ?: Icon(Icons.Filled.MusicNote, contentDescription = "Album Art Placeholder", modifier = Modifier.size(64.dp))
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    uiState.audioArtist?.let { Text(it, style = MaterialTheme.typography.titleSmall) }
                    uiState.audioAlbum?.let { Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(bottom=4.dp)) }
                    Text(
                        text = "${formatDurationMillis( (if (isUserScrubbing) userSeekPositionMillis else currentPlaybackTimeMillis).coerceAtMost(uiState.audioDurationMillis ?: 0L) )} / ${formatDurationMillis(uiState.audioDurationMillis ?: 0L)}",
                        style = MaterialTheme.typography.labelMedium
                    )
                }
                FilledTonalIconButton(
                    onClick = onPlayPause,
                    enabled = mediaPlayer != null && !isMediaPlayerPreparing,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, contentDescription = if (isPlaying) "Pause" else "Play")
                }
            }
            if (isMediaPlayerPreparing) Text("Player preparing...", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top=0.dp, start=16.dp, end=16.dp, bottom=8.dp))
            audioPreviewError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top=0.dp, start=16.dp, end=16.dp, bottom=8.dp)) }

            val totalDuration = uiState.audioDurationMillis ?: 0L
            val progress = remember(isUserScrubbing, userSeekPositionMillis, currentPlaybackTimeMillis, totalDuration) {
                val currentPos = if (isUserScrubbing) userSeekPositionMillis else currentPlaybackTimeMillis
                if (totalDuration > 0L) (currentPos.toFloat() / totalDuration.toFloat()).coerceIn(0f, 1f) else 0f
            }

            if (mediaPlayer != null || isMediaPlayerPreparing) {
                Box(
                    contentAlignment = Alignment.BottomCenter,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(24.dp)
                        .onSizeChanged { onSeekBarWidthChanged(it.width) }
                        .pointerInput(mediaPlayer, totalDuration, progressBarWidthPx) {
                            if (mediaPlayer == null || totalDuration <= 0L || progressBarWidthPx <= 0) return@pointerInput
                            detectHorizontalDragGestures(
                                onDragStart = { offset ->
                                    onDragStart(
                                        offset,
                                        totalDuration,
                                        progressBarWidthPx
                                    )
                                },
                                onHorizontalDrag = { change, _ ->
                                    onDrag(
                                        change,
                                        totalDuration,
                                        progressBarWidthPx
                                    )
                                },
                                onDragEnd = onDragEnd,
                                onDragCancel = onDragCancel
                            )
                        }
                ) {
                    LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth()) 
                }
            }
        }
    }
}
