package com.example.materialdrain.ui.shared

import android.graphics.BitmapFactory
import android.graphics.drawable.Drawable
import android.media.MediaPlayer
import android.net.Uri
import android.util.Log
import android.view.View
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Videocam // Added
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.util.EventLogger
import androidx.media3.ui.DefaultTimeBar
import androidx.media3.ui.PlayerView
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.decode.ImageDecoderDecoder
import coil.imageLoader
import coil.request.ImageRequest
import coil.target.Target
import com.example.materialdrain.ui.formatDurationMillis
import com.example.materialdrain.util.ExoPlayerCache
import com.example.materialdrain.viewmodel.UploadUiState

private const val TAG_COIL = "CoilImageLoaderShared"

@Composable
fun ClickablePreviewOverlay(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
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

@UnstableApi
@Composable
fun VideoPlayerControls(videoUri: Uri, thumbnailUrl: String?, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val playedColor = MaterialTheme.colorScheme.primary.toArgb()
    val scrubberColor = MaterialTheme.colorScheme.primary.toArgb()
    val bufferedColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f).toArgb()
    val unplayedColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f).toArgb()

    val exoPlayer = remember(videoUri) {
        val mediaSourceFactory: MediaSource.Factory = if (videoUri.scheme == "content") {
            ProgressiveMediaSource.Factory(DefaultDataSource.Factory(context))
        } else {
            val simpleCache = ExoPlayerCache.getInstance(context)
            val cacheDataSourceFactory = CacheDataSource.Factory()
                .setCache(simpleCache)
                .setUpstreamDataSourceFactory(DefaultHttpDataSource.Factory())
                .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
            ProgressiveMediaSource.Factory(cacheDataSourceFactory)
        }

        ExoPlayer.Builder(context)
            .setMediaSourceFactory(mediaSourceFactory)
            .build().apply {
                addAnalyticsListener(EventLogger())
                setMediaItem(MediaItem.fromUri(videoUri))
                prepare()
                playWhenReady = true
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
                        artworkDrawable = null
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

@UnstableApi
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
    onDrag: (change: PointerInputChange, totalDuration: Long, progressBarWidthPx: Int) -> Unit,
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

@Composable
fun InlineImagePreview(
    uiState: UploadUiState,
    onFullScreenClick: () -> Unit
) {
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
                        ClickablePreviewOverlay {
                            onFullScreenClick()
                        }
                    }
                    else -> {}
                }
            }
        }
    }
}

@Composable
fun InlineTextPreview(
    textContent: String?
) {
    textContent?.takeIf { it.isNotBlank() }?.let {
        Text("Content Preview (4KB Max):", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top=8.dp, bottom=4.dp))
        OutlinedTextField(
            value = it,
            onValueChange = {},
            readOnly = true,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 100.dp, max = 200.dp)
                .padding(vertical = 8.dp),
            textStyle = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
fun InlineVideoPreview(
    videoThumbnail: ByteArray?,
    selectedFileUri: Uri?,
    selectedFileMimeType: String?,
    onFullScreenClick: () -> Unit
) {
    if (selectedFileUri != null && selectedFileMimeType?.startsWith("video/") == true) {
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
                videoThumbnail?.let {
                    val bitmap = remember(it) { BitmapFactory.decodeByteArray(it, 0, it.size) }
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "Video thumbnail preview",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                    ClickablePreviewOverlay {
                        onFullScreenClick()
                    }
                } ?: Icon(
                    Icons.Filled.Videocam,
                    contentDescription = "Video thumbnail placeholder",
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
