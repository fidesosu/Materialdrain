package tools.senko.materialdrain.ui.media

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import coil.ImageLoader
import kotlin.math.abs
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.imageLoader
import coil.request.ImageRequest
import coil.size.Size
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds
import tools.senko.materialdrain.ui.LocalBlurredBackdrop

internal const val TAG_COIL = "CoilImageLoaderShared"
internal fun imageRequest(context: Context, data: Any, configure: ImageRequest.Builder.() -> Unit = {}): ImageRequest {
    val builder = ImageRequest.Builder(context).data(data).apply(configure)
    HostRequestAuth.headersFor(data.toString()).forEach { (name, value) -> builder.addHeader(name, value) }
    return builder.build()
}

/**
 * The thumbnail of a file as a backdrop: blurred and filling the whole area. The thumbnails of Pixeldrain are
 * generated at a fixed size, so what they show is framed differently than the full image, they must never be
 * shown as if they were the picture itself. Blurred, they are just the colours of it, which the full image can
 * fade in over without anything seeming to jump.
 */
@Composable
internal fun BlurredBackdrop(source: Any, apiKey: String?, imageLoader: ImageLoader, modifier: Modifier = Modifier) {
    // A setting of the user
    if (!LocalBlurredBackdrop.current) return
    val context = LocalContext.current
    val request = remember(source, apiKey) { imageRequest(context, source) }
    AsyncImage(
        model = request,
        contentDescription = null,
        imageLoader = imageLoader,
        contentScale = ContentScale.Crop,
        // Blurring needs Android 12, before that the thumbnail is just made faint
        modifier = modifier.then(
            if (Build.VERSION.SDK_INT >= 31) Modifier.blur(28.dp, BlurredEdgeTreatment.Rectangle) else Modifier.alpha(0.3f)
        )
    )
}

/**
 * An image which appears in two steps: the (small, so quickly loaded and mostly cached already) thumbnail is
 * shown blurred as a backdrop straight away and the full image fades in over it as soon as it has arrived.
 * A thin progress bar shows that the image is still loading.
 *
 * @param thumbnailSource the thumbnail which is used as backdrop, see [BlurredBackdrop]
 * @param maxPixels upper limit for the size the image is decoded at, so huge images don't fill up the memory
 */
@Composable
fun LayeredPreviewImage(
    fullSource: Any,
    thumbnailSource: Any?,
    apiKey: String?,
    contentDescription: String?,
    contentScale: ContentScale,
    modifier: Modifier = Modifier,
    filterQuality: FilterQuality = FilterQuality.Low,
    imageLoader: ImageLoader? = null,
    maxPixels: Int? = null,
    onFullImageState: (AsyncImagePainter.State) -> Unit = {}
) {
    val context = LocalContext.current
    val loader = imageLoader ?: context.imageLoader
    var fullState by remember(fullSource) { mutableStateOf<AsyncImagePainter.State>(AsyncImagePainter.State.Empty) }
    var thumbnailVisible by remember(fullSource) { mutableStateOf(true) }
    val fullLoaded = fullState is AsyncImagePainter.State.Success

    // The backdrop stays until the full image has finished fading in (an image can have transparent parts)
    LaunchedEffect(fullLoaded) {
        if (fullLoaded) {
            delay(300.milliseconds)
            thumbnailVisible = false
        }
    }

    // The request is built once. One which is built again on every recomposition contains a new listener each time,
    // Coil then takes it for a different request and starts loading (and fading in) again, every time the state
    // of the image changes, so that the image never gets to be visible.
    val fullRequest = remember(fullSource, apiKey, maxPixels) {
        imageRequest(context, fullSource) {
            crossfade(true)
            if (maxPixels != null) size(Size(maxPixels, maxPixels))
            listener(onError = { _, result -> Log.e(TAG_COIL, "Error loading image: $fullSource", result.throwable) })
        }
    }

    Box(modifier) {
        if (thumbnailSource != null && thumbnailVisible) {
            BlurredBackdrop(thumbnailSource, apiKey, loader, Modifier.fillMaxSize())
        }
        AsyncImage(
            model = fullRequest,
            contentDescription = contentDescription,
            imageLoader = loader,
            modifier = Modifier.fillMaxSize(),
            contentScale = contentScale,
            filterQuality = filterQuality,
            onState = { state ->
                fullState = state
                onFullImageState(state)
            }
        )
        when (fullState) {
            is AsyncImagePainter.State.Loading, AsyncImagePainter.State.Empty ->
                LinearProgressIndicator(modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth())
            is AsyncImagePainter.State.Error ->
                Icon(
                    Icons.Filled.BrokenImage,
                    contentDescription = "Error loading image",
                    modifier = Modifier.align(Alignment.Center).size(48.dp),
                    tint = MaterialTheme.colorScheme.error
                )
            else -> Unit
        }
    }
}
/** The small badge in the corner of a preview which can be opened fullscreen. */
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
            modifier = Modifier.size(28.dp),
            shape = RoundedCornerShape(4.dp),
            color = Color.Black.copy(alpha = 0.55f)
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
@Composable
fun InlineImagePreview(
    imageSource: Any?, // Can be Uri, String (URL), etc.
    contentDescription: String,
    apiKey: String?,
    modifier: Modifier = Modifier,
    thumbnailSource: Any? = null,
    filterQuality: FilterQuality = FilterQuality.Low,
    /** The space around the card; the file details leave none above it, so it starts right under the top bar. */
    outerPadding: PaddingValues = PaddingValues(vertical = 8.dp),
    onFullScreenClick: () -> Unit
) {
    if (imageSource == null) return

    Card(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .padding(outerPadding),
        shape = MediaCardShape
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            LayeredPreviewImage(
                fullSource = imageSource,
                thumbnailSource = thumbnailSource,
                apiKey = apiKey,
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                filterQuality = filterQuality,
                modifier = Modifier.fillMaxSize()
            )
            ClickablePreviewOverlay(onFullScreenClick)
        }
    }
}
/** Space around the fullscreen picture, the picture is fitted into what is left. */
internal val FullscreenImagePadding = 8.dp

/**
 * Pinch to zoom, drag to pan, double tap on the picture to zoom in and out, and a tap outside the picture
 * calls [onTapOutside]. A tap outside is acted upon at once; only a tap on the picture waits for a possible second one.
 *
 * @param imageSize the size of the picture in pixels once it is known (its shape decides where it is), or null. As
 * long as it is not known everything counts as outside.
 */
@Composable
internal fun ZoomableBox(
    imageSize: androidx.compose.ui.geometry.Size?,
    imagePadding: Dp,
    onTapOutside: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var size by remember { mutableStateOf(IntSize.Zero) }
    val currentOnTapOutside by rememberUpdatedState(onTapOutside)
    val currentImageSize by rememberUpdatedState(imageSize)
    val paddingPx = with(LocalDensity.current) { imagePadding.toPx() }

    fun clamp(value: Offset, forScale: Float): Offset {
        val maxX = size.width * (forScale - 1f) / 2f
        val maxY = size.height * (forScale - 1f) / 2f
        return Offset(value.x.coerceIn(-maxX, maxX), value.y.coerceIn(-maxY, maxY))
    }

    /** Whether a point of the screen is on the picture, which is fitted in the box and then zoomed and moved. */
    fun isOnImage(point: Offset): Boolean {
        val image = currentImageSize ?: return false
        if (size == IntSize.Zero || image.width <= 0f || image.height <= 0f) return false
        val fit = minOf((size.width - 2 * paddingPx) / image.width, (size.height - 2 * paddingPx) / image.height)
        val center = Offset(size.width / 2f, size.height / 2f)
        // The zoom is around the middle of the box, undo it to get to the point on the unzoomed picture
        val unzoomed = center + (point - center - offset) / scale
        return abs(unzoomed.x - center.x) <= image.width * fit / 2f && abs(unzoomed.y - center.y) <= image.height * fit / 2f
    }

    fun toggleZoom(at: Offset) {
        if (scale > 1.05f) {
            scale = 1f
            offset = Offset.Zero
        } else {
            val target = 3f
            val center = Offset(size.width / 2f, size.height / 2f)
            scale = target
            offset = clamp((center - at) * (target - 1f), target)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { size = it }
            .pointerInput(Unit) {
                var lastTapUptime = 0L
                var lastTapPosition = Offset.Zero
                awaitEachGesture {
                    awaitFirstDown()
                    val up = waitForUpOrCancellation() ?: return@awaitEachGesture
                    if (!isOnImage(up.position)) {
                        lastTapUptime = 0L
                        currentOnTapOutside()
                        return@awaitEachGesture
                    }
                    val isDoubleTap = up.uptimeMillis - lastTapUptime <= viewConfiguration.doubleTapTimeoutMillis &&
                        (up.position - lastTapPosition).getDistance() < viewConfiguration.touchSlop * 8
                    if (isDoubleTap) {
                        lastTapUptime = 0L
                        toggleZoom(up.position)
                    } else {
                        lastTapUptime = up.uptimeMillis
                        lastTapPosition = up.position
                    }
                }
            }
            .pointerInput(Unit) {
                detectTransformGestures { centroid, pan, zoom, _ ->
                    val newScale = (scale * zoom).coerceIn(1f, 8f)
                    val fromCenter = centroid - Offset(size.width / 2f, size.height / 2f)
                    // Zooming keeps the point between the fingers where it is
                    offset = clamp((offset - fromCenter) * (newScale / scale) + fromCenter + pan, newScale)
                    scale = newScale
                }
            }
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationX = offset.x
                translationY = offset.y
            },
        content = content
    )
}
