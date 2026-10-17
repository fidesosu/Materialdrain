package tools.senko.materialdrain.settings.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlin.math.min
import tools.senko.materialdrain.ui.preview.PreviewTheme
import tools.senko.materialdrain.ui.preview.ScreenPreviews
import java.io.File

/** How far the picture can be zoomed in: the square then shows this share of its shorter side. */
private const val MAX_ZOOM = 6f

/** The room around the square, so the picture beyond it can be seen and grabbed. */
private val FrameMargin = 24.dp

/**
 * Choosing the square of [picture] that's shown: the picture moves and zooms (drag, pinch, a double tap goes back to
 * the whole of its shorter side) under a fixed square in the account picture's shape, and the square always stays on
 * the picture. An animated picture moves here too. It starts at [picture]'s own crop.
 */
@Composable
internal fun AvatarCropper(picture: AvatarPicture, onKeep: (AvatarCrop) -> Unit, onCancel: () -> Unit) {
    val image = picture.image
    val context = LocalContext.current
    val density = LocalDensity.current
    val request = remember(image, context) {
        ImageRequest.Builder(context)
            .data(image.file)
            .memoryCacheKey("${image.file.path}@${image.version}#crop")
            // Sharp when zoomed in: the whole picture, not the size it first takes on the screen
            .size(coil.size.Size.ORIGINAL)
            .build()
    }

    Dialog(onDismissRequest = onCancel, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .safeDrawingPadding()
        ) {
            Text(
                "Crop your picture",
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 16.dp)
            )
            Text(
                "Drag to move it, pinch to zoom",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)
            )
            // The square is as big as the room for the picture allows, less a margin all round
            var areaSize by remember { mutableStateOf(IntSize.Zero) }
            val framePx = with(density) { (min(areaSize.width, areaSize.height) - 2 * FrameMargin.toPx()).coerceAtLeast(1f) }
            // At zoom 1 the picture's shorter side fills the square
            val baseScale = framePx / min(image.width, image.height)
            val baseWidth = image.width * baseScale
            val baseHeight = image.height * baseScale

            // The picture's zoom and how far its middle is from the square's, in pixels on the screen; from the crop
            val start = remember(picture, framePx) { placementOf(picture, framePx) }
            var zoom by remember(start) { mutableFloatStateOf(start.first) }
            var offset by remember(start) { mutableStateOf(start.second) }

            /** [wanted] with the square kept on the picture. */
            fun clamped(wanted: Offset, atZoom: Float): Offset {
                val roomX = ((baseWidth * atZoom - framePx) / 2f).coerceAtLeast(0f)
                val roomY = ((baseHeight * atZoom - framePx) / 2f).coerceAtLeast(0f)
                return Offset(wanted.x.coerceIn(-roomX, roomX), wanted.y.coerceIn(-roomY, roomY))
            }

            fun currentCrop() = cropOf(image, framePx, zoom, offset)

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clipToBounds()
                    .onSizeChanged { areaSize = it }
            ) {
                if (areaSize != IntSize.Zero) Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(baseWidth, baseHeight, framePx) {
                            detectTransformGestures { centroid, pan, gestureZoom, _ ->
                                val newZoom = (zoom * gestureZoom).coerceIn(1f, MAX_ZOOM)
                                // Zoomed around the fingers: the point under them stays under them
                                val fromCentre = centroid - Offset(size.width / 2f, size.height / 2f)
                                val wanted = (offset - fromCentre) * (newZoom / zoom) + fromCentre + pan
                                zoom = newZoom
                                offset = clamped(wanted, newZoom)
                            }
                        }
                        .pointerInput(Unit) {
                            detectTapGestures(onDoubleTap = { zoom = 1f; offset = Offset.Zero })
                        }
                ) {
                    AsyncImage(
                        model = request,
                        contentDescription = "The picture to crop",
                        contentScale = ContentScale.FillBounds,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .requiredSize(with(density) { baseWidth.toDp() }, with(density) { baseHeight.toDp() })
                            .graphicsLayer {
                                scaleX = zoom
                                scaleY = zoom
                                translationX = offset.x
                                translationY = offset.y
                            }
                    )
                    // Outside the square the picture is dimmed; the square has the picture's own corners, and an edge
                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                    ) {
                        val topLeft = Offset((size.width - framePx) / 2f, (size.height - framePx) / 2f)
                        val corner = CornerRadius(framePx / 4f)
                        drawRect(Color.Black.copy(alpha = 0.6f))
                        drawRoundRect(Color.Transparent, topLeft, Size(framePx, framePx), corner, blendMode = BlendMode.Clear)
                        drawRoundRect(Color.White.copy(alpha = 0.8f), topLeft, Size(framePx, framePx), corner, style = Stroke(1.dp.toPx()))
                    }
                }
            }
            Row(
                horizontalArrangement = Arrangement.End,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                TextButton(onClick = onCancel) { Text("Cancel", color = Color.White) }
                Spacer(Modifier.width(8.dp))
                Button(onClick = { onKeep(currentCrop()) }, enabled = areaSize != IntSize.Zero) { Text("Use picture") }
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}

/**
 * The square of [image] shown in a [framePx] square at [zoom], the picture's middle [offset] from the square's (in screen
 * pixels), in the picture's own pixels. The other way round from [placementOf].
 */
internal fun cropOf(image: AvatarImage, framePx: Float, zoom: Float, offset: Offset): AvatarCrop {
    val shownScale = framePx / min(image.width, image.height) * zoom
    val side = framePx / shownScale
    // The square's top left in the picture: its middle, moved back by the offset, less half the square
    val left = image.width / 2f - offset.x / shownScale - side / 2f
    val top = image.height / 2f - offset.y / shownScale - side / 2f
    return AvatarCrop(left, top, side)
}

/** The zoom and the offset of the picture's middle from the square's (in screen pixels) which show [picture]'s crop. */
internal fun placementOf(picture: AvatarPicture, framePx: Float): Pair<Float, Offset> {
    val (image, crop) = picture
    val shortest = min(image.width, image.height).toFloat()
    val zoom = (shortest / crop.side).takeIf { it.isFinite() }?.coerceIn(1f, MAX_ZOOM) ?: 1f
    val shownScale = framePx / shortest * zoom
    val offset = Offset(
        (image.width / 2f - (crop.left + crop.side / 2f)) * shownScale,
        (image.height / 2f - (crop.top + crop.side / 2f)) * shownScale
    )
    return zoom to offset
}

// ---- Previews ----

/**
 * The cropper's square and buttons over a wide picture. The picture itself is a file on the device, which a preview
 * hasn't got, so only what's around it shows.
 */
@ScreenPreviews
@Composable
private fun AvatarCropperPreview() {
    PreviewTheme {
        val image = AvatarImage(File("preview.png"), width = 1600, height = 900, animated = false)
        AvatarCropper(AvatarPicture(image, AvatarCrop.centred(image.width, image.height)), onKeep = {}, onCancel = {})
    }
}
