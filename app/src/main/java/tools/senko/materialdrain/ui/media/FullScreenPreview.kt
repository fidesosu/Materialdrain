package tools.senko.materialdrain.ui.media

import android.net.Uri
import android.os.Build
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import tools.senko.materialdrain.ui.icons.automirrored.filled.ArrowBack
import tools.senko.materialdrain.ui.icons.filled.Fullscreen
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
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
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import coil.compose.AsyncImagePainter
import coil.imageLoader
import coil.size.Size
import tools.senko.materialdrain.ui.LocalBlurredBackdrop
import tools.senko.materialdrain.ui.LocalReduceMotion
import tools.senko.materialdrain.ui.components.FileIcon
import tools.senko.materialdrain.ui.components.pageTransition
import tools.senko.materialdrain.ui.components.sidewaysSwipe
import tools.senko.materialdrain.ui.preview.PreviewTheme
import tools.senko.materialdrain.ui.preview.ScreenPreviews

/** One file as the fullscreen preview shows it, see [FullScreenMediaPreviewDialog]. */
private data class FullScreenPage(val key: Any?, val uri: Uri?, val mimeType: String?, val thumbnailUrl: String?, val title: String?) {
    val isVideo: Boolean get() = mimeType?.startsWith("video/") == true
}

/**
 * A picture or a video over the whole screen. With [onSwipe], a swipe sideways goes to the file beside this one (it
 * says whether there was one): the dialog then shows whichever file it's given, which comes in like a page, [fileKey]
 * telling the files apart. A file that has no fullscreen view (a song, a PDF, ...) is its icon and name, to swipe past
 * or to go back to.
 */
@Composable
fun FullScreenMediaPreviewDialog(
    previewUri: Uri?,
    previewMimeType: String?,
    thumbnailUrl: String?,
    apiKey: String?,
    onDismissRequest: () -> Unit,
    /** The file's name, shown at the top of the video player */
    title: String? = null,
    fileKey: Any? = previewUri,
    onSwipe: ((toNext: Boolean) -> Boolean)? = null
) {
    val page = FullScreenPage(fileKey, previewUri, previewMimeType, thumbnailUrl, title)
    val reduceMotion = LocalReduceMotion.current
    // Which way the last swipe went, for the way the next file comes in
    var forward by remember { mutableStateOf(true) }
    Dialog(
        onDismissRequest = onDismissRequest,
        // Down to the edges of the screen, under the status and navigation bars, so the background fills all of it
        // instead of the screen behind showing through there; the buttons keep clear of the bars themselves
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        DarkDialogBars()
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .sidewaysSwipe(onSwipe != null) { toNext -> if (onSwipe?.invoke(toNext) == true) forward = toNext },
            contentAlignment = Alignment.Center
        ) {
            AnimatedContent(
                targetState = page,
                contentKey = { it.key },
                transitionSpec = { pageTransition(forward, reduceMotion) },
                label = "fullScreenFile"
            ) { shown ->
                FullScreenPageContent(shown, apiKey, onDismissRequest)
            }

            if (!page.isVideo) {
                MediaIconButton(
                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    onClick = onDismissRequest,
                    modifier = Modifier.align(Alignment.TopStart).windowInsetsPadding(WindowInsets.safeDrawing).padding(16.dp),
                    container = MediaScrim.Button
                )
            }
        }
    }
}

/** The file of one page of [FullScreenMediaPreviewDialog]: the zoomable picture, the video player, or the file's icon. */
@Composable
private fun FullScreenPageContent(page: FullScreenPage, apiKey: String?, onDismissRequest: () -> Unit) {
    val previewUri = page.uri
    val thumbnailUrl = page.thumbnailUrl
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        when {
            previewUri == null -> Text("Preview unavailable.", color = Color.White)
            page.isVideo -> FullscreenVideoPlayer(
                videoUri = previewUri,
                thumbnailUrl = thumbnailUrl,
                apiKey = apiKey,
                onBack = onDismissRequest,
                title = page.title
            )
            page.mimeType?.startsWith("image/") == true -> {
                val context = LocalContext.current
                // The app's loader animates GIFs and animated WebP itself, whatever type the host reports (AnimatedImages)
                val loader = context.imageLoader
                // The blurred thumbnail stays behind the picture for as long as the preview is open: it fills what
                // the fitted picture leaves free. It is not part of the zoomable area, so it doesn't move with
                // the picture, and it gets a scrim so that the picture and the buttons stand out.
                if (thumbnailUrl != null && LocalBlurredBackdrop.current) {
                    BlurredBackdrop(thumbnailUrl, apiKey, context.imageLoader, Modifier.fillMaxSize())
                    Box(modifier = Modifier.fillMaxSize().background(MediaScrim.Backdrop))
                }
                // Double tapping the picture zooms, tapping next to it closes the preview
                var imageSize by remember(previewUri) { mutableStateOf<androidx.compose.ui.geometry.Size?>(null) }
                ZoomableBox(
                    imageSize = imageSize,
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
                        // Edge to edge, like the video player
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
            // Swiped to from a picture or a video: what the file is, its own preview being on its details page
            else -> Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.padding(32.dp)
            ) {
                FileIcon(name = page.title.orEmpty(), isDirectory = false, thumbnailUrl = thumbnailUrl, size = 120.dp)
                page.title?.let { name ->
                    Text(name, color = Color.White, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
                }
                Text(
                    "This file has no fullscreen view. Go back to see it, or swipe on to the next one.",
                    color = Color.White.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

/**
 * The bars over a full screen dialog whose content is always dark (a picture, a video, a PDF): light icons, and no
 * lightened strip behind the navigation buttons. Called inside the dialog's content.
 */
@Composable
internal fun DarkDialogBars() {
    val view = LocalView.current
    val dialogWindow = (view.parent as? DialogWindowProvider)?.window
    SideEffect {
        dialogWindow?.let { window ->
            if (Build.VERSION.SDK_INT >= 29) window.isNavigationBarContrastEnforced = false
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = false
                isAppearanceLightNavigationBars = false
            }
        }
    }
}

// ---- Previews ----

/** A picture fullscreen, while it loads (pictures from a host don't load in a preview). */
@ScreenPreviews
@Composable
private fun FullScreenImagePreview() {
    PreviewTheme {
        FullScreenMediaPreviewDialog(
            previewUri = Uri.parse("https://example.com/beach-sunset.jpg"),
            previewMimeType = "image/jpeg",
            thumbnailUrl = null,
            apiKey = null,
            onDismissRequest = {}
        )
    }
}

/** A video fullscreen: the video player, paused (see FullscreenVideoPlayer). */
@ScreenPreviews
@Composable
private fun FullScreenVideoPreview() {
    PreviewTheme {
        FullScreenMediaPreviewDialog(previewUri = Uri.EMPTY, previewMimeType = "video/mp4", thumbnailUrl = null, apiKey = null, onDismissRequest = {})
    }
}
