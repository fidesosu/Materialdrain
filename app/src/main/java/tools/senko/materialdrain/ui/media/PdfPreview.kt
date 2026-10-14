package tools.senko.materialdrain.ui.media

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
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
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
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

/** The widest a page is drawn, and the most pixels it has: a zoomed page stays sharp without filling the memory. */
private const val MAX_PAGE_WIDTH_PX = 2560
private const val MAX_PAGE_PIXELS = 8_000_000

/** How far a PDF can be zoomed in, full screen. */
private const val MAX_PDF_ZOOM = 5f

/**
 * A PDF opened with Android's own [PdfRenderer] (which needs the whole file on the device, see FileInfoViewModel's
 * fetchPdfPreview). It draws one page at a time, so [render] waits its turn; the sizes of the pages are read once when
 * it's opened, so the pages take their place before they're drawn.
 */
private class PdfDocument(file: File) {
    private val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    private val renderer = try {
        PdfRenderer(descriptor)
    } catch (e: Exception) {
        descriptor.close()
        throw e
    }
    private val lock = Mutex()
    private var closed = false

    /** Each page's width over its height. */
    val pageRatios: List<Float> = (0 until renderer.pageCount).map { index ->
        renderer.openPage(index).use { page -> page.width.toFloat() / page.height.coerceAtLeast(1) }
    }

    /** Page [index] drawn [widthPx] wide (less for a huge page, see [MAX_PAGE_PIXELS]), on white as on paper; null once closed. */
    suspend fun render(index: Int, widthPx: Int): Bitmap? = lock.withLock {
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

/** What opening a PDF came to: the document, or why it can't be shown (a damaged or password-protected file). */
private sealed interface OpenedPdf {
    data object Opening : OpenedPdf
    class Open(val document: PdfDocument) : OpenedPdf
    data object Unreadable : OpenedPdf
}

/** [file] opened for as long as the caller is shown, and closed when it leaves. */
@Composable
private fun rememberPdf(file: File): OpenedPdf {
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
                // Damaged, or locked with a password, which Android's renderer can't open
                OpenedPdf.Unreadable
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
private fun PdfPage(document: PdfDocument, index: Int, widthPx: Int, modifier: Modifier = Modifier) {
    var bitmap by remember(document, index) { mutableStateOf<Bitmap?>(null) }
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
 * has, and a tap opens it full screen. While the PDF loads into the app it shows how far along that is.
 */
@Composable
fun InlinePdfPreview(state: PdfPreviewState, outerPadding: PaddingValues, onFullScreen: (File) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).padding(outerPadding),
        shape = MediaCardShape
    ) {
        Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerHigh), contentAlignment = Alignment.Center) {
            when (state) {
                is PdfPreviewState.Loading -> {
                    PdfMessage(
                        if (state.totalBytes != null) "Loading the PDF · ${formatSize(state.loadedBytes)} of ${formatSize(state.totalBytes)}"
                        else "Loading the PDF · ${formatSize(state.loadedBytes)}"
                    )
                    val total = state.totalBytes
                    if (total != null && total > 0) {
                        LinearProgressIndicator(
                            progress = { (state.loadedBytes.toFloat() / total).coerceIn(0f, 1f) },
                            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                        )
                    } else {
                        LinearProgressIndicator(modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth())
                    }
                }
                is PdfPreviewState.Failed -> PdfMessage(state.message)
                is PdfPreviewState.Ready -> when (val opened = rememberPdf(state.file)) {
                    OpenedPdf.Opening -> LinearProgressIndicator(modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth())
                    OpenedPdf.Unreadable -> PdfMessage("This PDF can't be shown: it's damaged, or locked with a password.")
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
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(8.dp)
                                    .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 6.dp, vertical = 3.dp)
                            )
                            ClickablePreviewOverlay { onFullScreen(state.file) }
                        }
                    }
                }
            }
        }
    }
}

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
 * A PDF full screen, edge to edge like a fullscreen picture: its pages one under the other, scrolled with a finger,
 * zoomed with two (or a double tap), and moved sideways while zoomed. Zooming lays the pages out wider rather than
 * stretching them, so scrolling keeps up with the finger and the pages are drawn again, sharp, at their new size.
 */
@Composable
fun FullScreenPdfDialog(file: File, title: String, onDismissRequest: () -> Unit) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        // Light icons on the dark background, and no lightened strip behind the navigation buttons (as for a picture)
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
        Box(modifier = Modifier.fillMaxSize().background(Color(0xFF202124))) {
            when (val opened = rememberPdf(file)) {
                OpenedPdf.Opening -> LinearProgressIndicator(modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth())
                OpenedPdf.Unreadable -> Text(
                    "This PDF can't be shown: it's damaged, or locked with a password.",
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.Center).padding(24.dp)
                )
                is OpenedPdf.Open -> ZoomablePages(opened.document)
            }
            MediaIconButton(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                onClick = onDismissRequest,
                modifier = Modifier.align(Alignment.TopStart).windowInsetsPadding(WindowInsets.safeDrawing).padding(16.dp),
                container = Color.Black.copy(alpha = 0.5f)
            )
            Text(
                title,
                color = Color.White,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(start = 76.dp, end = 76.dp, top = 28.dp)
                    .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
    }
}

@Composable
private fun ZoomablePages(document: PdfDocument) {
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
    Box(modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
        Text(
            "$current / $pages",
            color = Color.White,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 26.dp, end = 16.dp)
                .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                .padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}
