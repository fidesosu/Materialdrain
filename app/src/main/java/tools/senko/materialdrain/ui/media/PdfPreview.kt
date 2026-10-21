package tools.senko.materialdrain.ui.media

import tools.senko.materialdrain.ui.theme.AppShapes
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import tools.senko.materialdrain.ui.icons.automirrored.filled.ArrowBack
import tools.senko.materialdrain.ui.icons.filled.PictureAsPdf
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import tools.senko.materialdrain.files.PdfPreviewState
import tools.senko.materialdrain.util.formatSize
import java.io.File
import kotlin.math.roundToInt
import tools.senko.materialdrain.ui.preview.PreviewTheme
import tools.senko.materialdrain.ui.preview.ThemePreviews
import tools.senko.materialdrain.ui.preview.ScreenPreviews
import tools.senko.materialdrain.ui.preview.PreviewSamples
import androidx.compose.ui.platform.LocalInspectionMode

/** The widest a page is drawn, and the most pixels it has: a zoomed page stays sharp without filling the memory. */
private const val MAX_PAGE_WIDTH_PX = 2560
private const val MAX_PAGE_PIXELS = 8_000_000

/** How far a PDF can be zoomed in, full screen. */
private const val MAX_PDF_ZOOM = 5f

/** The pages of an opened PDF: their shapes, and each one drawn as a picture. */
private interface PdfPages {
    /** Each page's width over its height. */
    val pageRatios: List<Float>

    /** Page [index] drawn [widthPx] wide, on white as on paper; null once it can't be any more. */
    suspend fun render(index: Int, widthPx: Int): Bitmap?

    /** The page drawn at once, without waiting: only the previews' made-up pages can (see [SamplePdfPages]). */
    fun renderNow(index: Int, widthPx: Int): Bitmap? = null
}

/**
 * A PDF opened with Android's own [PdfRenderer] (which needs the whole file on the device, see FileInfoViewModel's
 * fetchPdfPreview). It draws one page at a time, so [render] waits its turn; the sizes of the pages are read once when
 * it's opened, so the pages take their place before they're drawn.
 */
private class PdfDocument(file: File) : PdfPages {
    private val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    private val renderer = try {
        PdfRenderer(descriptor)
    } catch (e: Exception) {
        descriptor.close()
        throw e
    }
    private val lock = Mutex()
    private var closed = false

    /** Each page's width over its height; a damaged page without a width or height is taken as square. */
    override val pageRatios: List<Float> = try {
        (0 until renderer.pageCount).map { index ->
            renderer.openPage(index).use { page ->
                (page.width.toFloat() / page.height).takeIf { it.isFinite() && it > 0f }?.coerceIn(0.05f, 20f) ?: 1f
            }
        }
    } catch (e: Exception) {
        renderer.close()
        descriptor.close()
        throw e
    }

    /** Page [index] drawn [widthPx] wide (less for a huge page, see [MAX_PAGE_PIXELS]), on white as on paper; null once closed. */
    override suspend fun render(index: Int, widthPx: Int): Bitmap? = lock.withLock {
        if (closed) return@withLock null
        withContext(Dispatchers.IO) {
            renderer.openPage(index).use { page ->
                var width = widthPx.coerceIn(1, MAX_PAGE_WIDTH_PX)
                var height = (width / pageRatios[index]).roundToInt().coerceAtLeast(1)
                if (width.toLong() * height > MAX_PAGE_PIXELS) {
                    val shrink = kotlin.math.sqrt(MAX_PAGE_PIXELS.toDouble() / (width.toLong() * height))
                    width = (width * shrink).toInt().coerceAtLeast(1)
                    height = (height * shrink).toInt().coerceAtLeast(1)
                }
                Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { bitmap ->
                    bitmap.eraseColor(android.graphics.Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                }
            }
        }
    }

    /** Closed once no page is being drawn any more; only the first call closes it. */
    suspend fun close() = lock.withLock {
        if (closed) return@withLock
        closed = true
        renderer.close()
        descriptor.close()
    }
}

/**
 * What opening a PDF came to: the document, or why it can't be shown: a damaged or password-protected file, or one that
 * isn't on the device any more (the app's cache was cleared while it was shown).
 */
private sealed interface OpenedPdf {
    data object Opening : OpenedPdf
    class Open(val document: PdfPages) : OpenedPdf
    data object Unreadable : OpenedPdf
    data object Gone : OpenedPdf
}

private const val UNREADABLE_PDF = "This PDF can't be shown: it's damaged, or locked with a password."

/** [file] opened for as long as the caller is shown, and closed when it leaves. */
@Composable
private fun rememberPdf(file: File): OpenedPdf {
    // A preview (in Android Studio) has no PDF on a device to open: it shows made-up pages
    if (LocalInspectionMode.current) return remember { OpenedPdf.Open(SamplePdfPages) }
    var opened by remember(file) { mutableStateOf<OpenedPdf>(OpenedPdf.Opening) }
    DisposableEffect(file) {
        // Its own scope, not the composition's, which is gone by the time the document is closed. The document is the
        // one this effect opened, so it's closed exactly once, even when it's left before it has finished opening
        val scope = CoroutineScope(Dispatchers.IO)
        var document: PdfDocument? = null
        val opening = scope.launch {
            val result = try {
                OpenedPdf.Open(PdfDocument(file).also { document = it })
            } catch (_: Exception) {
                // Damaged, or locked with a password, which Android's renderer can't open; or not there any more
                if (file.isFile) OpenedPdf.Unreadable else OpenedPdf.Gone
            }
            withContext(Dispatchers.Main) { opened = result }
        }
        onDispose {
            scope.launch {
                opening.join()
                document?.close()
            }
        }
    }
    return opened
}

/** Page [index] of [document], drawn [widthPx] wide; the page drawn before stays while a sharper one is drawn. */
@Composable
private fun PdfPage(document: PdfPages, index: Int, widthPx: Int, modifier: Modifier = Modifier) {
    var bitmap by remember(document, index) { mutableStateOf(document.renderNow(index, widthPx)) }
    LaunchedEffect(document, index, widthPx) {
        // Zooming changes the width on every frame: only once it has settled is the page drawn again
        if (bitmap != null) delay(200)
        document.render(index, widthPx)?.let { bitmap = it }
    }
    Box(modifier = modifier.aspectRatio(document.pageRatios[index]).background(Color.White)) {
        bitmap?.let {
            Image(
                bitmap = it.asImageBitmap(),
                contentDescription = "Page ${index + 1}",
                contentScale = ContentScale.FillBounds,
                filterQuality = FilterQuality.Medium,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

/**
 * A PDF's preview in a file's details, a card like the other previews: the top of its first page, how many pages it
 * has, and a tap opens it full screen. While the PDF loads into the app it shows how far along that is; a big one waits
 * for a tap on [onLoad], which also loads it again when its copy left the cache.
 */
@Composable
fun InlinePdfPreview(state: PdfPreviewState, outerPadding: PaddingValues, onLoad: () -> Unit, onFullScreen: (File) -> Unit) {
    val reload = rememberReloadOnce()
    Card(
        modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).padding(outerPadding),
        shape = MediaCardShape
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .then(if (state is PdfPreviewState.Large) Modifier.clickable(onClickLabel = "Load the PDF", onClick = onLoad) else Modifier),
            contentAlignment = Alignment.Center
        ) {
            val bottomBar = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
            when (state) {
                is PdfPreviewState.Large -> PdfMessage("Tap to load the PDF · About ${formatSize(state.totalBytes)}")
                is PdfPreviewState.Loading -> {
                    val total = state.totalBytes?.takeIf { it > 0 }
                    PdfMessage("Loading the PDF · ${formatSize(state.loadedBytes)}" + total?.let { " of ${formatSize(it)}" }.orEmpty())
                    if (total != null) {
                        LinearProgressIndicator(progress = { (state.loadedBytes.toFloat() / total).coerceIn(0f, 1f) }, modifier = bottomBar)
                    } else {
                        LinearProgressIndicator(modifier = bottomBar)
                    }
                }
                is PdfPreviewState.Failed -> PdfMessage(state.message)
                is PdfPreviewState.Ready -> when (val opened = rememberPdf(state.file)) {
                    OpenedPdf.Opening -> LinearProgressIndicator(modifier = bottomBar)
                    OpenedPdf.Gone -> if (reload.used) {
                        PdfMessage(PDF_GONE)
                    } else {
                        LinearProgressIndicator(modifier = bottomBar)
                        LaunchedEffect(Unit) { reload.use(onLoad) }
                    }
                    OpenedPdf.Unreadable -> PdfMessage(UNREADABLE_PDF)
                    is OpenedPdf.Open -> {
                        val pages = opened.document.pageRatios.size
                        if (pages == 0) {
                            PdfMessage("This PDF has no pages.")
                        } else {
                            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                                val widthPx = with(LocalDensity.current) { maxWidth.roundToPx() }
                                // The top of the first page, as much as fits (the card cuts off the rest), the way a
                                // thumbnail is cropped
                                PdfPage(
                                    opened.document, 0, widthPx,
                                    Modifier.fillMaxWidth().wrapContentHeight(Alignment.Top, unbounded = true)
                                )
                            }
                            Text(
                                if (pages == 1) "1 page" else "$pages pages",
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.White,
                                modifier = Modifier.align(Alignment.BottomStart).padding(8.dp).labelOverPage()
                            )
                            ClickablePreviewOverlay { onFullScreen(state.file) }
                        }
                    }
                }
            }
        }
    }
}

/** Said of a PDF whose copy left the cache again right after it was loaded again (see [ReloadOnce]). */
private const val PDF_GONE = "The PDF couldn't be kept on the device. Go back and open it again."

/**
 * Loads a PDF again whose copy left the cache, but only once: if the copy is gone again at once (the cache full, the
 * storage failing), it says so rather than loading it over and over.
 */
@Stable
private class ReloadOnce {
    var used by mutableStateOf(false)
        private set

    fun use(load: () -> Unit) {
        if (used) return
        used = true
        load()
    }
}

@Composable
private fun rememberReloadOnce(): ReloadOnce = remember { ReloadOnce() }

/** A label over the pages: white text on a dark see-through chip, readable on any page. */
private fun Modifier.labelOverPage(): Modifier =
    background(MediaScrim.Button, AppShapes.Small).padding(horizontal = 8.dp, vertical = 4.dp)

@Composable
private fun PdfMessage(text: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
        Icon(Icons.Filled.PictureAsPdf, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(32.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}

/**
 * A PDF full screen (a page of the fullscreen view of a file's details, see FullScreenFileDialog), edge to edge like a
 * fullscreen picture: its pages one under the other, scrolled with a finger, zoomed with two (or a double tap), and moved
 * sideways while zoomed. Zooming lays the pages out wider rather than stretching them, so scrolling keeps up with the
 * finger and the pages are drawn again, sharp, at their new size.
 *
 * Until it's [PdfPreviewState.Ready] it says how far it has loaded into the app, or why it can't be shown. A big one is
 * loaded at once ([onLoad]), unlike in the details: being opened (or swiped to) here is asking for it. So is one whose
 * copy has left the cache.
 */
@Composable
fun PdfFullscreenContent(state: PdfPreviewState?, title: String, onBack: () -> Unit, onLoad: () -> Unit) {
    val reload = rememberReloadOnce()
    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF202124))) {
        val top = Modifier.align(Alignment.TopCenter).windowInsetsPadding(WindowInsets.safeDrawing).fillMaxWidth()
        when (state) {
            null -> LinearProgressIndicator(modifier = top)
            is PdfPreviewState.Large -> {
                FullscreenPdfMessage("Loading the PDF · About ${formatSize(state.totalBytes)}")
                LinearProgressIndicator(modifier = top)
                LaunchedEffect(Unit) { onLoad() }
            }
            is PdfPreviewState.Loading -> {
                val total = state.totalBytes?.takeIf { it > 0 }
                FullscreenPdfMessage("Loading the PDF · ${formatSize(state.loadedBytes)}" + total?.let { " of ${formatSize(it)}" }.orEmpty())
                if (total != null) {
                    LinearProgressIndicator(progress = { (state.loadedBytes.toFloat() / total).coerceIn(0f, 1f) }, modifier = top)
                } else {
                    LinearProgressIndicator(modifier = top)
                }
            }
            is PdfPreviewState.Failed -> FullscreenPdfMessage(state.message)
            is PdfPreviewState.Ready -> when (val opened = rememberPdf(state.file)) {
                OpenedPdf.Opening -> LinearProgressIndicator(modifier = top)
                // Its copy left the cache: loaded again, once
                OpenedPdf.Gone -> if (reload.used) {
                    FullscreenPdfMessage(PDF_GONE)
                } else {
                    LinearProgressIndicator(modifier = top)
                    LaunchedEffect(Unit) { reload.use(onLoad) }
                }
                OpenedPdf.Unreadable -> FullscreenPdfMessage(UNREADABLE_PDF)
                is OpenedPdf.Open -> ZoomablePages(opened.document)
            }
        }
        MediaIconButton(
            icon = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = "Back",
            onClick = onBack,
            modifier = Modifier.align(Alignment.TopStart).windowInsetsPadding(WindowInsets.safeDrawing).padding(PdfBarInset),
            size = PdfBarHeight,
            container = MediaScrim.Button
        )
        // Clear of the back button on one side and the page number on the other, centred between them
        PdfBarSlot(Modifier.align(Alignment.TopCenter).padding(horizontal = 112.dp)) {
            Text(
                title,
                color = Color.White,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.labelOverPage()
            )
        }
    }
}

/** A line about the PDF in the middle of the fullscreen view, white on its dark background. */
@Composable
private fun FullscreenPdfMessage(text: String, modifier: Modifier = Modifier) {
    Box(contentAlignment = Alignment.Center, modifier = modifier) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
            Icon(Icons.Filled.PictureAsPdf, contentDescription = null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(32.dp))
            Text(text, color = Color.White, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp))
        }
    }
}

@Composable
private fun ZoomablePages(document: PdfPages) {
    val pages = document.pageRatios.size
    var zoom by remember { mutableFloatStateOf(1f) }
    val listState = rememberLazyListState()
    val sideways = rememberScrollState()
    val scope = rememberCoroutineScope()
    val insets = WindowInsets.safeDrawing.asPaddingValues()

    /** Zooms to [target], keeping the point [at] (on the screen) where it is. */
    fun zoomTo(target: Float, at: Offset) {
        val next = target.coerceIn(1f, MAX_PDF_ZOOM)
        val ratio = next / zoom
        if (ratio == 1f) return
        zoom = next
        // Sideways: the point's distance from the left grows with the pages
        val x = ((sideways.value + at.x) * ratio - at.x).roundToInt()
        // Down: the point's distance from the top of the page it's on grows with the page
        val y = ((listState.firstVisibleItemScrollOffset + at.y) * ratio - at.y).roundToInt()
        val index = listState.firstVisibleItemIndex
        scope.launch {
            sideways.scrollTo(x.coerceAtLeast(0))
            listState.scrollToItem(index, y.coerceAtLeast(0))
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            // Two fingers zoom, before the list can take them for a scroll; one finger is left to the list
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    do {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        if (event.changes.count { it.pressed } >= 2) {
                            val change = event.calculateZoom()
                            if (change != 1f) zoomTo(zoom * change, event.calculateCentroid())
                            event.changes.forEach { it.consume() }
                        }
                    } while (event.changes.any { it.pressed })
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(onDoubleTap = { at -> zoomTo(if (zoom > 1.05f) 1f else 2.5f, at) })
            }
    ) {
        val pageWidth = maxWidth * zoom
        val pageWidthPx = with(LocalDensity.current) { pageWidth.roundToPx() }
        Box(modifier = Modifier.fillMaxSize().horizontalScroll(sideways, enabled = zoom > 1f)) {
            LazyColumn(
                state = listState,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                // Clear of the bars at the ends, the pages themselves edge to edge
                contentPadding = PaddingValues(top = insets.calculateTopPadding() + 76.dp, bottom = insets.calculateBottomPadding() + 16.dp),
                modifier = Modifier.width(pageWidth).fillMaxHeight()
            ) {
                items(pages) { index -> PdfPage(document, index, pageWidthPx, Modifier.fillMaxWidth()) }
            }
        }
    }
    // Which page is at the top, out of how many
    val current = listState.firstVisibleItemIndex + 1
    Box(modifier = Modifier.fillMaxSize()) {
        PdfBarSlot(Modifier.align(Alignment.TopEnd).padding(end = PdfBarInset)) {
            Text("$current / $pages", color = Color.White, style = MaterialTheme.typography.labelLarge, modifier = Modifier.labelOverPage())
        }
    }
}

/** How far the full screen PDF's back button sits from the top and the sides, and how tall it is. */
private val PdfBarInset = 16.dp
private val PdfBarHeight = 44.dp

/**
 * A place in the full screen PDF's top bar, as high as the back button and at its height: [content] is centred in it, so
 * the title and the page number are level with the button whatever the size of their text.
 */
@Composable
private fun PdfBarSlot(modifier: Modifier, content: @Composable () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.windowInsetsPadding(WindowInsets.safeDrawing).padding(top = PdfBarInset).height(PdfBarHeight)
    ) {
        content()
    }
}

// ---- Previews ----

/**
 * The pages of the previews' PDF (see [rememberPdf]): three made-up A4 pages, a heading and lines of "text" in grey, drawn
 * at once so the preview has them in its one frame.
 */
private object SamplePdfPages : PdfPages {
    override val pageRatios = listOf(210f / 297f, 210f / 297f, 210f / 297f)

    override suspend fun render(index: Int, widthPx: Int): Bitmap? = renderNow(index, widthPx)

    override fun renderNow(index: Int, widthPx: Int): Bitmap {
        val width = widthPx.coerceIn(1, 1200)
        val height = (width / pageRatios[index]).roundToInt()
        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { bitmap ->
            bitmap.eraseColor(android.graphics.Color.WHITE)
            val canvas = android.graphics.Canvas(bitmap)
            val paint = android.graphics.Paint().apply { color = 0xFFBDBDBD.toInt() }
            val margin = width * 0.1f
            val line = width * 0.018f
            // The heading, then lines of differing lengths down the page
            canvas.drawRect(margin, margin, width * 0.6f, margin + line * 2, paint)
            var top = margin + line * 5
            var n = index
            while (top < height - margin) {
                val end = if (n % 7 == 6) width * 0.55f else width - margin
                canvas.drawRect(margin, top, end, top + line, paint)
                top += line * 2.2f
                n++
            }
        }
    }
}

/** Each state of the preview in a file's details: shown (with the made-up pages), waiting for a tap, loading, failed. */
@ThemePreviews
@Composable
private fun InlinePdfPreviewPreview() {
    PreviewTheme(padding = 16.dp) {
        val padding = PaddingValues(vertical = 4.dp)
        Column {
            InlinePdfPreview(PdfPreviewState.Ready(File("sample.pdf")), padding, onLoad = {}, onFullScreen = {})
            InlinePdfPreview(PdfPreviewState.Large(24_117_248), padding, onLoad = {}, onFullScreen = {})
            InlinePdfPreview(PdfPreviewState.Loading(6_291_456, 24_117_248), padding, onLoad = {}, onFullScreen = {})
            InlinePdfPreview(PdfPreviewState.Failed(UNREADABLE_PDF), padding, onLoad = {}, onFullScreen = {})
        }
    }
}

/** Full screen, with the made-up pages: the back button, the title and the page number in one line at the top. */
@ScreenPreviews
@Composable
private fun PdfFullscreenContentPreview() {
    PreviewTheme {
        PdfFullscreenContent(PdfPreviewState.Ready(File("sample.pdf")), title = PreviewSamples.pdf.name, onBack = {}, onLoad = {})
    }
}

/** Full screen while it loads into the app. */
@ScreenPreviews
@Composable
private fun PdfFullscreenLoadingPreview() {
    PreviewTheme {
        PdfFullscreenContent(PdfPreviewState.Loading(6_291_456, 24_117_248), title = PreviewSamples.pdf.name, onBack = {}, onLoad = {})
    }
}
