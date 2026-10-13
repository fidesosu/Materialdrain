package tools.senko.materialdrain.preferences

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Constraints
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okio.buffer
import okio.source
import tools.senko.materialdrain.ui.media.AnimatedImages
import java.io.File
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** A still picture is kept no bigger than this on its longest side: plenty to crop from, and the file stays small. */
private const val STILL_MAX_PX = 1536

/** An animated picture is kept as it is, frames and all, up to this size; a bigger one is kept as its first frame. */
private const val ANIMATED_MAX_BYTES = 16L * 1024 * 1024

/** The picture's colours are judged on a copy this small, which is plenty for a colour and quick. */
private const val SAMPLE_SIZE_PX = 32

/** The picture's shape: a squircle with the corners of the file thumbnails (a quarter of its size, see FileIcon). */
internal val AvatarShape = RoundedCornerShape(percent = 25)

/** The square of [width] by [height] picture that's shown, in its own pixels: from [left], [top], [side] long. */
internal data class AvatarCrop(val left: Float, val top: Float, val side: Float) {
    companion object {
        /** The biggest square in the middle of the picture. */
        fun centred(width: Int, height: Int): AvatarCrop {
            val side = min(width, height).toFloat()
            return AvatarCrop((width - side) / 2f, (height - side) / 2f, side)
        }
    }
}

/**
 * A picture file, with its size in pixels and whether it moves (a GIF or an animated WebP). [version] tells a picture
 * apart from the one before it in the same file.
 */
internal data class AvatarImage(val file: File, val width: Int, val height: Int, val animated: Boolean, val version: Long = 0L) {
    /** Square, give or take a pixel or two: nothing to crop. */
    val isSquare: Boolean get() = abs(width - height) <= max(width, height) / 100
}

/** The account's picture: the whole of it as it was picked, and the square of it that's shown. */
internal data class AvatarPicture(val image: AvatarImage, val crop: AvatarCrop)

/**
 * The account's picture. Pixeldrain has no profile pictures, so it's one picked on this device and kept only here, in
 * the app's files, per account (by its id, so a new username keeps it).
 *
 * The picture is kept whole, with the square that's shown beside it, rather than cut to that square: an animated one
 * can't be cut and saved again (Android writes no GIFs), and the square can be moved later. A picture kept by an older
 * version (already a square PNG) is shown whole.
 */
internal class AccountAvatarStore(context: Context) {
    private val directory = File(context.applicationContext.filesDir, "account_avatars")
    private val pendingDirectory = File(context.applicationContext.cacheDir, "account_avatar_pending")
    private val resolver = context.applicationContext.contentResolver

    private fun nameOf(userId: String) = userId.filter { it.isLetterOrDigit() || it == '-' }.ifEmpty { "account" }
    private fun imageOf(userId: String) = File(directory, "${nameOf(userId)}.image")
    private fun cropOf(userId: String) = File(directory, "${nameOf(userId)}.crop")
    private fun legacyOf(userId: String) = File(directory, "${nameOf(userId)}.png")

    fun load(userId: String): AvatarPicture? {
        val image = imageOf(userId).takeIf { it.exists() } ?: legacyOf(userId).takeIf { it.exists() } ?: return null
        val read = describe(image) ?: return null
        val crop = cropOf(userId).takeIf { it.exists() }?.readText()?.split(' ')?.mapNotNull { it.toFloatOrNull() }
            ?.takeIf { it.size == 3 }?.let { (left, top, side) -> AvatarCrop(left, top, side) }
        return AvatarPicture(read, crop?.let { fit(it, read) } ?: AvatarCrop.centred(read.width, read.height))
    }

    /**
     * Copies the picture at [uri] to a waiting place, to be cropped and then kept with [save]: an animated one as it is,
     * a still one made smaller when it's big. Null when it can't be read.
     */
    fun import(uri: Uri): AvatarImage? = runCatching {
        pendingDirectory.deleteRecursively()
        pendingDirectory.mkdirs()
        val target = File(pendingDirectory, "picked-${System.currentTimeMillis()}")
        val animated = resolver.openInputStream(uri)?.use { stream ->
            stream.source().buffer().let { AnimatedImages.isGif(it.peek()) || AnimatedImages.isAnimatedWebP(it.peek()) }
        } ?: return null
        val size = resolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: -1L
        if (animated && size in 0..ANIMATED_MAX_BYTES) {
            resolver.openInputStream(uri)?.use { input -> target.outputStream().use { input.copyTo(it) } } ?: return null
        } else {
            // A still picture, or the first frame of one too big to keep moving
            val bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(resolver, uri)) { decoder, info, _ ->
                val scale = STILL_MAX_PX.toFloat() / max(info.size.width, info.size.height)
                if (scale < 1f) decoder.setTargetSize((info.size.width * scale).toInt().coerceAtLeast(1), (info.size.height * scale).toInt().coerceAtLeast(1))
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
            val format = if (bitmap.hasAlpha()) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG
            target.outputStream().use { bitmap.compress(format, 92, it) }
        }
        describe(target)
    }.getOrNull()

    /** Keeps [image] (from [import]) as the picture of [userId], showing [crop] of it. */
    fun save(userId: String, image: AvatarImage, crop: AvatarCrop): AvatarPicture? = runCatching {
        directory.mkdirs()
        val target = imageOf(userId)
        if (image.file != target) {
            target.delete()
            if (!image.file.renameTo(target)) {
                image.file.copyTo(target, overwrite = true)
                image.file.delete()
            }
            legacyOf(userId).delete()
        }
        val fitted = fit(crop, image)
        cropOf(userId).writeText("${fitted.left} ${fitted.top} ${fitted.side}")
        AvatarPicture(image.copy(file = target, version = target.lastModified()), fitted)
    }.getOrNull()

    /** Throws away a picture [import]ed but not kept. */
    fun discard(image: AvatarImage) {
        if (image.file.parentFile == pendingDirectory) image.file.delete()
    }

    fun remove(userId: String) {
        imageOf(userId).delete()
        cropOf(userId).delete()
        legacyOf(userId).delete()
    }

    /** The size of the picture in [file], and whether it moves. Null when it isn't a picture. */
    private fun describe(file: File): AvatarImage? = runCatching {
        val animated = file.source().buffer().use { AnimatedImages.isGif(it.peek()) || AnimatedImages.isAnimatedWebP(it.peek()) }
        // Only the header is read, for the size
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        AvatarImage(file, bounds.outWidth, bounds.outHeight, animated, version = file.lastModified())
    }.getOrNull()

    /** [crop] kept inside the picture, and never empty: a square can't be shown from outside it or with no size. */
    private fun fit(crop: AvatarCrop, image: AvatarImage): AvatarCrop {
        val most = min(image.width, image.height).toFloat()
        val side = crop.side.takeIf { it.isFinite() && it > 0f }?.coerceIn(1f, most) ?: most
        val left = crop.left.takeIf { it.isFinite() } ?: 0f
        val top = crop.top.takeIf { it.isFinite() } ?: 0f
        return AvatarCrop(left.coerceIn(0f, image.width - side), top.coerceIn(0f, image.height - side), side)
    }

    /** The shown square of [picture] (its first frame), small, for its colour. */
    fun sample(picture: AvatarPicture): Bitmap? = runCatching {
        val (image, crop) = picture
        // Decoded small (the whole picture so that the square is about [SAMPLE_SIZE_PX] * 4), then the square cut out of it
        val scale = min(1f, SAMPLE_SIZE_PX * 4f / crop.side)
        val decoded = ImageDecoder.decodeBitmap(ImageDecoder.createSource(image.file)) { decoder, _, _ ->
            decoder.setTargetSize((image.width * scale).roundToInt().coerceAtLeast(1), (image.height * scale).roundToInt().coerceAtLeast(1))
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
        val left = (crop.left * scale).roundToInt().coerceIn(0, decoded.width - 1)
        val top = (crop.top * scale).roundToInt().coerceIn(0, decoded.height - 1)
        val side = (crop.side * scale).roundToInt().coerceIn(1, min(decoded.width - left, decoded.height - top))
        Bitmap.createBitmap(decoded, left, top, side, side)
    }.getOrNull()
}

/**
 * [picture] in a square of the size it's given, clipped to [AvatarShape]: the whole picture laid out so that its crop
 * fills the square. Drawn by the app's image loader, so an animated picture moves.
 */
@Composable
internal fun AvatarPictureView(picture: AvatarPicture, contentDescription: String?, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val (image, crop) = picture
    val request = remember(image, context) {
        ImageRequest.Builder(context)
            .data(image.file)
            // The file is replaced in place when the picture changes: the cache tells the two apart by its version
            .memoryCacheKey("${image.file.path}@${image.version}")
            .build()
    }
    Box(modifier = modifier.clip(AvatarShape)) {
        AsyncImage(
            model = request,
            contentDescription = contentDescription,
            contentScale = ContentScale.FillBounds,
            modifier = Modifier.layout { measurable, constraints ->
                val box = constraints.maxWidth.takeIf { it != Constraints.Infinity } ?: constraints.minWidth
                // The square's side becomes the box's; the crop is never empty (see AccountAvatarStore.fit)
                val scale = box / crop.side
                val placeable = measurable.measure(
                    Constraints.fixed((image.width * scale).roundToInt().coerceAtLeast(1), (image.height * scale).roundToInt().coerceAtLeast(1))
                )
                layout(box, box) { placeable.place(-(crop.left * scale).roundToInt(), -(crop.top * scale).roundToInt()) }
            }
        )
    }
}

/**
 * The colour a picture is most about: of its colourful pixels, the hue most of them share, as their average. Null for
 * a picture without much colour (grey, black and white), which then doesn't tint anything.
 */
internal fun accentOf(bitmap: Bitmap): Color? {
    val sample = Bitmap.createScaledBitmap(bitmap, SAMPLE_SIZE_PX, SAMPLE_SIZE_PX, true)
    val pixels = IntArray(SAMPLE_SIZE_PX * SAMPLE_SIZE_PX)
    sample.getPixels(pixels, 0, SAMPLE_SIZE_PX, 0, 0, SAMPLE_SIZE_PX, SAMPLE_SIZE_PX)
    // Twelve hue buckets; a pixel counts more the more colourful it is, and less near black or white
    val buckets = 12
    val weight = FloatArray(buckets)
    val red = FloatArray(buckets)
    val green = FloatArray(buckets)
    val blue = FloatArray(buckets)
    val hsv = FloatArray(3)
    var totalWeight = 0f
    pixels.forEach { pixel ->
        if ((pixel ushr 24) < 128) return@forEach
        android.graphics.Color.colorToHSV(pixel, hsv)
        val brightness = hsv[2]
        val w = hsv[1] * (1f - kotlin.math.abs(brightness - 0.6f) / 0.6f).coerceAtLeast(0f)
        if (w <= 0.05f) return@forEach
        val bucket = (hsv[0] / 360f * buckets).toInt().coerceIn(0, buckets - 1)
        weight[bucket] += w
        red[bucket] += android.graphics.Color.red(pixel) * w
        green[bucket] += android.graphics.Color.green(pixel) * w
        blue[bucket] += android.graphics.Color.blue(pixel) * w
        totalWeight += w
    }
    // Hardly any colourful pixels: a grey picture
    if (totalWeight < pixels.size * 0.05f) return null
    val top = weight.indices.maxBy { weight[it] }
    val w = weight[top]
    return Color(red = red[top] / w / 255f, green = green[top] / w / 255f, blue = blue[top] / w / 255f)
}

/**
 * [accent] as a container colour of the theme: moved a little towards the theme's own hue (so it doesn't clash with
 * it), and as light or dark as [container], so text which reads on [container] reads on it too.
 */
internal fun accentContainer(accent: Color, container: Color, primary: Color): Color {
    val harmonized = com.google.android.material.color.MaterialColors.harmonize(accent.toArgbInt(), primary.toArgbInt())
    val accentHsl = FloatArray(3).also { androidx.core.graphics.ColorUtils.colorToHSL(harmonized, it) }
    val containerHsl = FloatArray(3).also { androidx.core.graphics.ColorUtils.colorToHSL(container.toArgbInt(), it) }
    // Its hue, and a little more of its colour than the container has, at the container's lightness
    val tone = floatArrayOf(accentHsl[0], max(accentHsl[1] * 0.7f, containerHsl[1]).coerceAtMost(0.75f), containerHsl[2])
    return Color(androidx.core.graphics.ColorUtils.HSLToColor(tone))
}

private fun Color.toArgbInt(): Int = android.graphics.Color.argb(alpha, red, green, blue)

/**
 * The picture of the account and what can be done with it; null [picture] when it has none. A picked picture which isn't
 * square waits in [cropping] until its square is chosen ([keep]) or it's dropped ([cancelCrop]).
 */
internal class AvatarState(
    val picture: AvatarPicture?,
    val accent: Color?,
    val cropping: AvatarPicture?,
    val choose: (Uri) -> Unit,
    val recrop: () -> Unit,
    val keep: (AvatarCrop) -> Unit,
    val cancelCrop: () -> Unit,
    val remove: () -> Unit
)

/** The stored picture of [userId], read off the main thread, and changing it. */
@Composable
internal fun rememberAccountAvatar(userId: String): AvatarState {
    val context = LocalContext.current
    val store = remember { AccountAvatarStore(context) }
    val scope = rememberCoroutineScope()
    var picture by remember(userId) { mutableStateOf<AvatarPicture?>(null) }
    var accent by remember(userId) { mutableStateOf<Color?>(null) }
    // The picture being cropped: a new one waiting to be kept, or the kept one whose square is being moved
    var cropping by remember(userId) { mutableStateOf<AvatarPicture?>(null) }

    suspend fun show(shown: AvatarPicture?) {
        picture = shown
        accent = shown?.let { withContext(Dispatchers.Default) { store.sample(it)?.let(::accentOf) } }
    }

    suspend fun keep(image: AvatarImage, crop: AvatarCrop) {
        withContext(Dispatchers.IO) { store.save(userId, image, crop) }?.let { show(it) }
    }

    LaunchedEffect(userId) {
        show(withContext(Dispatchers.IO) { store.load(userId) })
    }
    return AvatarState(
        picture = picture,
        accent = accent,
        cropping = cropping,
        choose = { uri ->
            scope.launch {
                val image = withContext(Dispatchers.IO) { store.import(uri) } ?: return@launch
                // A square picture has nothing to crop
                if (image.isSquare) keep(image, AvatarCrop.centred(image.width, image.height))
                else cropping = AvatarPicture(image, AvatarCrop.centred(image.width, image.height))
            }
        },
        recrop = { cropping = picture },
        keep = { crop ->
            val image = cropping?.image
            cropping = null
            if (image != null) scope.launch { keep(image, crop) }
        },
        cancelCrop = {
            cropping?.let { shown -> scope.launch(Dispatchers.IO) { store.discard(shown.image) } }
            cropping = null
        },
        remove = {
            scope.launch {
                withContext(Dispatchers.IO) { store.remove(userId) }
                show(null)
            }
        }
    )
}
