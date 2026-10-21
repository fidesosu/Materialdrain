package tools.senko.materialdrain.ui.media

import android.net.Uri
import android.os.Build
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import tools.senko.materialdrain.ui.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import tools.senko.materialdrain.files.PdfPreviewState
import tools.senko.materialdrain.files.SourceLanguage
import tools.senko.materialdrain.files.languageFor
import tools.senko.materialdrain.ui.LocalBlurredBackdrop
import tools.senko.materialdrain.ui.LocalReduceMotion
import tools.senko.materialdrain.ui.components.CodeFullscreenContent
import tools.senko.materialdrain.ui.components.FileIcon
import tools.senko.materialdrain.ui.components.pageTransition
import tools.senko.materialdrain.ui.components.sidewaysSwipe
import tools.senko.materialdrain.ui.preview.PreviewSamples
import tools.senko.materialdrain.ui.preview.PreviewTheme
import tools.senko.materialdrain.ui.preview.ScreenPreviews
import tools.senko.materialdrain.ui.theme.DarkTheme

/** What a page of the fullscreen view shows of its file, as far as it has loaded (see [FullScreenFile]). */
sealed interface FullScreenContent {
    /** A picture or a video (by the file's type), which the page loads itself */
    data object Media : FullScreenContent

    /** A song: its card, as in the details, which plays it (with the songs of [queue] after it); [apiKey] for its player */
    data class Song(val queue: SongQueue?, val apiKey: String?) : FullScreenContent

    /**
     * A text: all of it once it's loaded ([text]), coloured for [language]; or why it isn't there ([error]), and whether
     * it can be loaded anyway ([canLoadAnyway])
     */
    data class Text(val text: String?, val language: SourceLanguage?, val error: String?, val canLoadAnyway: Boolean = false) : FullScreenContent

    /** A PDF, as far as it has loaded into the app (see PdfPreviewState) */
    data class Pdf(val state: PdfPreviewState?) : FullScreenContent

    /** Held back by the data saver until it's tapped (see DataSaver): [what] it would show */
    data class Held(val what: String) : FullScreenContent

    /** Nothing to show fullscreen: the file's icon and name */
    data object None : FullScreenContent
}

/**
 * One file as the fullscreen view shows it (see [FullScreenFileDialog]): [key] tells the files apart, [uri] is where the
 * file is, its [content] what its page shows of it.
 */
data class FullScreenFile(
    val key: Any?,
    val uri: Uri?,
    val mimeType: String?,
    val thumbnailUrl: String?,
    val title: String?,
    val content: FullScreenContent = FullScreenContent.Media
) {
    val isVideo: Boolean get() = content == FullScreenContent.Media && mimeType?.startsWith("video/") == true

    /** Whether its page has a top bar of its own with a way back (a video's, a song's, a text's, a PDF's), or needs the viewer's. */
    val hasOwnBar: Boolean get() =
        isVideo || content is FullScreenContent.Song || content is FullScreenContent.Text || content is FullScreenContent.Pdf
}

/**
 * A picture or a video over the whole screen (see [FullScreenFileDialog]), for what has only those: the Upload screen.
 * With [onSwipe], a swipe sideways goes to the file beside this one, [fileKey] telling the files apart.
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
    FullScreenFileDialog(
        file = FullScreenFile(fileKey, previewUri, previewMimeType, thumbnailUrl, title),
        apiKey = apiKey,
        onDismissRequest = onDismissRequest,
        onSwipe = onSwipe
    )
}

/**
 * A file over the whole screen, in the app's dark colours whatever theme it's in: a picture (zoomable), a video (its
 * player), a song (its card), a text (all of it, see CodeFullscreenContent) or a PDF (its pages). With [onSwipe], a swipe
 * sideways goes to the file beside this one, of the same kind (see FileGroup; it says whether there was one): the dialog
 * then shows whichever [file] it's given, which comes in like a page. What a page loads is asked for with [onLoad]: a
 * preview the data saver held back, or a PDF that's big; [onLoadAnyway] loads a text too big for the preview.
 */
@Composable
fun FullScreenFileDialog(
    file: FullScreenFile,
    apiKey: String?,
    onDismissRequest: () -> Unit,
    onSwipe: ((toNext: Boolean) -> Boolean)? = null,
    onLoad: () -> Unit = {},
    onLoadAnyway: () -> Unit = {}
) {
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
        DarkTheme {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .sidewaysSwipe(onSwipe != null) { toNext -> if (onSwipe?.invoke(toNext) == true) forward = toNext },
                contentAlignment = Alignment.Center
            ) {
                AnimatedContent(
                    targetState = file,
                    contentKey = { it.key },
                    transitionSpec = { pageTransition(forward, reduceMotion) },
                    label = "fullScreenFile"
                ) { shown ->
                    FullScreenPageContent(shown, apiKey, onDismissRequest, onLoad, onLoadAnyway)
                }

                if (!file.hasOwnBar) {
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
}

/** The file of one page of [FullScreenFileDialog]. */
@Composable
private fun FullScreenPageContent(page: FullScreenFile, apiKey: String?, onDismissRequest: () -> Unit, onLoad: () -> Unit, onLoadAnyway: () -> Unit) {
    val previewUri = page.uri
    val thumbnailUrl = page.thumbnailUrl
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        when (val content = page.content) {
            is FullScreenContent.Text -> CodeFullscreenContent(
                title = page.title.orEmpty(),
                content = content.text,
                language = content.language,
                onDismiss = onDismissRequest,
                message = content.error,
                onLoadAnyway = onLoadAnyway.takeIf { content.canLoadAnyway }
            )
            is FullScreenContent.Pdf -> PdfFullscreenContent(content.state, title = page.title.orEmpty(), onBack = onDismissRequest, onLoad = onLoad)
            is FullScreenContent.Song -> if (previewUri != null) SongPage(page, previewUri, content, onDismissRequest) else Unavailable()
            is FullScreenContent.Held -> HeldPage(content.what, onLoad)
            FullScreenContent.Media -> when {
                previewUri == null -> Unavailable()
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
                else -> NoFullScreenView(page)
            }
            FullScreenContent.None -> NoFullScreenView(page)
        }
    }
}

/** A song's page: the song over the whole screen, as the player over the app shows one (see AudioPlayerFullscreen). */
@Composable
private fun SongPage(page: FullScreenFile, uri: Uri, song: FullScreenContent.Song, onBack: () -> Unit) {
    AudioPlayerFullscreen(
        audioUri = uri,
        apiKey = song.apiKey,
        title = page.title,
        albumArtSource = page.thumbnailUrl,
        onBack = onBack,
        queue = song.queue
    )
}

/**
 * A page the data saver held back in the details: loaded at once ([onLoad]), as opening (or swiping to) it here is
 * asking for it; meanwhile, what's loading.
 */
@Composable
private fun HeldPage(what: String, onLoad: () -> Unit) {
    LaunchedEffect(Unit) { onLoad() }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxSize().padding(32.dp)
    ) {
        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(32.dp))
        Spacer(Modifier.height(12.dp))
        Text("Loading $what", style = MaterialTheme.typography.labelLarge, color = Color.White)
    }
}

@Composable
private fun Unavailable() {
    Text("Preview unavailable.", color = Color.White)
}

/** A file without a fullscreen view: what it is, its own preview being on its details page. */
@Composable
private fun NoFullScreenView(page: FullScreenFile) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.padding(32.dp)
    ) {
        FileIcon(name = page.title.orEmpty(), isDirectory = false, thumbnailUrl = page.thumbnailUrl, size = 120.dp)
        page.title?.let { name ->
            Text(name, color = Color.White, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        }
        Text(
            "This file has no fullscreen view. Go back to see it.",
            color = Color.White.copy(alpha = 0.7f),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * The bars over a full screen dialog whose content is always dark (a picture, a video, a PDF, the viewer of a file's
 * details): light icons, and no lightened strip behind the navigation buttons. Called inside the dialog's content.
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

/** A text swiped to: all of it, in the view's dark colours. */
@ScreenPreviews
@Composable
private fun FullScreenTextPreview() {
    PreviewTheme {
        FullScreenFileDialog(
            file = FullScreenFile(
                key = "text", uri = Uri.EMPTY, mimeType = "text/markdown", thumbnailUrl = null, title = PreviewSamples.text.name,
                content = FullScreenContent.Text(PreviewSamples.CODE.repeat(3), languageFor(PreviewSamples.text.name), error = null)
            ),
            apiKey = null,
            onDismissRequest = {}
        )
    }
}

/** A song swiped to: its card. */
@ScreenPreviews
@Composable
private fun FullScreenSongPreview() {
    PreviewTheme {
        FullScreenFileDialog(
            file = FullScreenFile(
                key = "song", uri = Uri.EMPTY, mimeType = "audio/flac", thumbnailUrl = null, title = PreviewSamples.song.name,
                content = FullScreenContent.Song(queue = null, apiKey = null)
            ),
            apiKey = null,
            onDismissRequest = {}
        )
    }
}

/** A text the data saver holds back, swiped to. */
@ScreenPreviews
@Composable
private fun FullScreenHeldPreview() {
    PreviewTheme {
        FullScreenFileDialog(
            file = FullScreenFile(
                key = "held", uri = Uri.EMPTY, mimeType = "text/plain", thumbnailUrl = null, title = "notes.txt",
                content = FullScreenContent.Held("the text")
            ),
            apiKey = null,
            onDismissRequest = {}
        )
    }
}
