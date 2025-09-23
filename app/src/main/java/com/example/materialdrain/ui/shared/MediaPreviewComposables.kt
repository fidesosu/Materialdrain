package com.example.materialdrain.ui.shared

import android.graphics.drawable.Drawable
import android.net.Uri
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.DefaultTimeBar
import androidx.media3.ui.PlayerView
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.ImageDecoderDecoder
import coil.imageLoader
import coil.request.ImageRequest
import coil.target.Target

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
        ExoPlayer.Builder(context).build().apply {
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
    thumbnailUrl: String?, // Added thumbnailUrl parameter
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
                    modifier = Modifier.clickable(enabled = false) {} // Prevent click on content from dismissing
                ) {
                    when {
                        previewMimeType?.startsWith("video/") == true -> {
                            VideoPlayerControls( // This now calls the shared composable
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
                                onError = { onDismissRequest() } // Optionally dismiss if image fails to load
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