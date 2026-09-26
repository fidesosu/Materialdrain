package tools.senko.materialdrain.ui.media

import android.net.Uri
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImagePainter
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.imageLoader
import coil.size.Size
import tools.senko.materialdrain.ui.LocalBlurredBackdrop

@Composable
fun FullScreenMediaPreviewDialog(
    previewUri: Uri?,
    previewMimeType: String?,
    thumbnailUrl: String?,
    apiKey: String?,
    onDismissRequest: () -> Unit
) {
    val isVideo = previewMimeType?.startsWith("video/") == true
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(if (isVideo) Color.Black else Color.Black.copy(alpha = 0.92f)),
            contentAlignment = Alignment.Center
        ) {
            when {
                previewUri == null -> Text("Preview unavailable.", color = Color.White)
                isVideo -> FullscreenVideoPlayer(
                    videoUri = previewUri,
                    thumbnailUrl = thumbnailUrl,
                    apiKey = apiKey,
                    onBack = onDismissRequest
                )
                previewMimeType?.startsWith("image/") == true -> {
                    val context = LocalContext.current
                    // The GIF loader shares the caches of the normal one, but knows how to animate
                    val loader = remember(previewMimeType) {
                        if (previewMimeType == "image/gif") {
                            context.imageLoader.newBuilder()
                                .components {
                                    if (Build.VERSION.SDK_INT >= 28) add(ImageDecoderDecoder.Factory()) else add(GifDecoder.Factory())
                                }
                                .build()
                        } else {
                            context.imageLoader
                        }
                    }
                    // The blurred thumbnail stays behind the picture for as long as the preview is open: it fills what
                    // the fitted picture leaves free. It is not part of the zoomable area, so it doesn't move with
                    // the picture, and it gets a scrim so that the picture and the buttons stand out.
                    if (thumbnailUrl != null && LocalBlurredBackdrop.current) {
                        BlurredBackdrop(thumbnailUrl, apiKey, context.imageLoader, Modifier.fillMaxSize())
                        Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)))
                    }
                    // Double tapping the picture zooms, tapping next to it closes the preview
                    var imageSize by remember(previewUri) { mutableStateOf<androidx.compose.ui.geometry.Size?>(null) }
                    ZoomableBox(
                        imageSize = imageSize,
                        imagePadding = FullscreenImagePadding,
                        onTapOutside = onDismissRequest
                    ) {
                        LayeredPreviewImage(
                            fullSource = previewUri,
                            thumbnailSource = null,
                            apiKey = apiKey,
                            contentDescription = "Fullscreen image preview",
                            contentScale = ContentScale.Fit,
                            // Disable filtering to prevent blurring when scaling images (pixel art, zooming)
                            filterQuality = FilterQuality.None,
                            imageLoader = loader,
                            maxPixels = 3072,
                            onFullImageState = { state ->
                                if (state is AsyncImagePainter.State.Success) {
                                    val size = state.painter.intrinsicSize
                                    imageSize = size.takeIf { it.width.isFinite() && it.height.isFinite() && it.width > 0f && it.height > 0f }
                                }
                            },
                            modifier = Modifier.fillMaxSize().padding(FullscreenImagePadding)
                        )
                    }
                }
                else -> Text("Unsupported preview type for fullscreen.", color = Color.White)
            }

            if (!isVideo) {
                MediaIconButton(
                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    onClick = onDismissRequest,
                    modifier = Modifier.align(Alignment.TopStart).padding(16.dp),
                    container = Color.Black.copy(alpha = 0.5f)
                )
            }
        }
    }
}
