package com.example.materialdrain.ui.screens

import android.content.Context
import android.graphics.BitmapFactory
import android.media.MediaPlayer
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.example.materialdrain.ui.dialogs.InfoRow // Corrected import
import com.example.materialdrain.ui.formatSize
import com.example.materialdrain.viewmodel.UploadViewModel
import com.example.materialdrain.viewmodel.UploadUiState
import kotlinx.coroutines.android.awaitFrame
import kotlinx.coroutines.isActive
import java.io.IOException
import java.util.concurrent.TimeUnit
import android.util.Log

private const val TAG_MEDIA_PLAYER = "MediaPlayerPreview" // Added for Audio Preview Logging

// Helper function to format duration in milliseconds to MM:SS or HH:MM:SS
internal fun formatDurationMillis(millis: Long): String {
    if (millis < 0) return "00:00"
    val hours = TimeUnit.MILLISECONDS.toHours(millis)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(millis) % TimeUnit.HOURS.toMinutes(1)
    val seconds = TimeUnit.MILLISECONDS.toSeconds(millis) % TimeUnit.MINUTES.toSeconds(1)
    return if (hours > 0) {
        String.format("%02d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}

@Composable
fun UploadScreenContent(uploadViewModel: UploadViewModel, onShowDialog: (String, String) -> Unit) {
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
                    if (mediaPlayer == mp) mediaPlayer = null
                    isMediaPlayerPreparing = false
                    isPlaying = false
                    true
                }
                localMediaPlayerInstance.setOnCompletionListener {
                    isPlaying = false
                    currentPlaybackTimeMillis = uiState.audioDurationMillis ?: 0L
                }
            } catch (e: Exception) {
                audioPreviewError = "Error setting up audio player."
                Log.e(TAG_MEDIA_PLAYER, "Error setting up audio player for URI: ${uiState.selectedFileUri}", e)
                localMediaPlayerInstance?.release()
                mediaPlayer = null
                isMediaPlayerPreparing = false
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
                    if (!isUserScrubbing && mediaPlayer?.isPlaying == true) { // Re-check isUserScrubbing and isPlaying
                        currentPlaybackTimeMillis = mediaPlayer?.currentPosition?.toLong() ?: currentPlaybackTimeMillis
                    }
                } catch (e: IllegalStateException) {
                    Log.w(TAG_MEDIA_PLAYER, "MediaPlayeraccess error during playback: ${e.message}")
                    isPlaying = false // Stop trying to update
                    audioPreviewError = "Player error."
                    break // Exit loop
                }
                awaitFrame() // wait for the next frame
                if (!isPlaying || mediaPlayer?.isPlaying == false || isUserScrubbing) break // Exit if state changes
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
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        if (uiState.selectedFileUri != null && uiState.selectedFileMimeType?.startsWith("image/") == true) {
                            AsyncImage(model = uiState.selectedFileUri, contentDescription = "Selected image preview", modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp)
                                .padding(vertical = 8.dp)
                                .clip(RoundedCornerShape(12.dp)), contentScale = ContentScale.Fit)
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
                                            if(isUserScrubbing) { // If scrubbing, apply scrubbed position before playing
                                                it.seekTo(userSeekPositionMillis.toInt())
                                                currentPlaybackTimeMillis = userSeekPositionMillis // Update current time to reflect seek
                                            }
                                            // If at end, restart from beginning
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
                                    // Calculate the initial percentage based on touch offset
                                    val initialPercentage = (offset.x / currentWidthPx).coerceIn(0f, 1f)
                                    userSeekPositionMillis = (initialPercentage * totalDuration).toLong()
                                    playbackTimeAtDragStart = userSeekPositionMillis // Store this to calculate drag relative to it
                                },
                                onDrag = { change, totalDuration, currentWidthPx ->
                                    val currentDragX = change.position.x
                                    val dragDeltaX = currentDragX - initialTouchX // How much finger moved from initial touch
                                    // Calculate time delta based on how much the drag position changed *relative to the bar width*
                                    val timeDeltaMillis = (dragDeltaX / currentWidthPx) * totalDuration
                                    userSeekPositionMillis = (playbackTimeAtDragStart + timeDeltaMillis).toLong().coerceIn(0L, totalDuration)
                                    change.consume()
                                },
                                onDragEnd = {
                                    mediaPlayer?.seekTo(userSeekPositionMillis.toInt())
                                    currentPlaybackTimeMillis = userSeekPositionMillis // Reflect the seek in current time
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
                1 -> { // Text Upload Tab
                    OutlinedTextField(value = uiState.textToUpload, onValueChange = uploadViewModel::onTextToUploadChanged, label = { Text("Paste text here") }, modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 150.dp), maxLines = 10, enabled = !uiState.isLoading)
                }
            }
            // REMOVED old CircularProgressIndicator and progress text from here
            if (!uiState.isLoading) { // Show success/ready messages only when not actively loading
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
        Column(modifier = Modifier.padding(bottom = 0.dp)) { // Ensure bottom padding is 0 for the card's column
            Row(
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                uiState.audioAlbumArt?.let {
                    val bitmap = remember(it) { try { BitmapFactory.decodeByteArray(it, 0, it.size) } catch (e: Exception) { null } }
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
                        // Display the correct time based on whether user is scrubbing or not
                        text = "${formatDurationMillis( (if (isUserScrubbing) userSeekPositionMillis else currentPlaybackTimeMillis).coerceAtMost(uiState.audioDurationMillis ?: 0L) )} / ${formatDurationMillis(uiState.audioDurationMillis ?: 0L)}",
                        style = MaterialTheme.typography.labelMedium
                    )
                }
                FilledTonalIconButton(
                    onClick = onPlayPause,
                    enabled = mediaPlayer != null && !isMediaPlayerPreparing, // Should be enabled once player is prepared
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, contentDescription = if (isPlaying) "Pause" else "Play")
                }
            }
            if (isMediaPlayerPreparing) Text("Player preparing...", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top=0.dp, start=16.dp, end=16.dp, bottom=8.dp))
            audioPreviewError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top=0.dp, start=16.dp, end=16.dp, bottom=8.dp)) }

            val totalDuration = uiState.audioDurationMillis ?: 0L
            // Correctly calculate progress based on whether user is scrubbing or normal playback
            val progressFraction = { 
                val currentPos = if (isUserScrubbing) userSeekPositionMillis else currentPlaybackTimeMillis
                if (totalDuration > 0L) (currentPos.toFloat() / totalDuration.toFloat()).coerceIn(0f, 1f) else 0f
            }

            // Show progress bar only if player is ready or preparing (to avoid flicker)
            if (mediaPlayer != null || isMediaPlayerPreparing) {
                Box(
                    contentAlignment = Alignment.BottomCenter, // Align progress bar to bottom of box
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(24.dp) // Given height for the touch area and progress bar
                        .onSizeChanged { onSeekBarWidthChanged(it.width) } // Get width for drag calculations
                        .pointerInput(mediaPlayer, totalDuration, progressBarWidthPx) { // Keys for re-launching pointer input
                            if (mediaPlayer == null || totalDuration <= 0L || progressBarWidthPx <= 0) return@pointerInput // Guard clause
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
                    LinearProgressIndicator(progress = progressFraction, modifier = Modifier.fillMaxWidth()) 
                }
            }
        }
    }
}
