package tools.senko.materialdrain.preferences

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max
import kotlin.math.min

/** How big the stored picture is, a square: twice the largest size it's shown at, for sharp edges. */
private const val AVATAR_SIZE_PX = 256

/** The picture's colours are judged on a copy this small, which is plenty for a colour and quick. */
private const val SAMPLE_SIZE_PX = 32

/**
 * The account's picture. Pixeldrain has no profile pictures, so it's one picked on this device and kept only here, in
 * the app's files, per account (by its id, so a new username keeps it).
 */
internal class AccountAvatarStore(context: Context) {
    private val directory = File(context.applicationContext.filesDir, "account_avatars")
    private val resolver = context.applicationContext.contentResolver

    private fun fileOf(userId: String) = File(directory, "${userId.filter { it.isLetterOrDigit() || it == '-' }.ifEmpty { "account" }}.png")

    fun load(userId: String): Bitmap? = fileOf(userId).takeIf { it.exists() }?.let { BitmapFactory.decodeFile(it.path) }

    /** Copies the picture at [uri], cut to a square from its middle and made small. Null when it can't be read. */
    fun save(userId: String, uri: Uri): Bitmap? = runCatching {
        val decoded = ImageDecoder.decodeBitmap(ImageDecoder.createSource(resolver, uri)) { decoder, info, _ ->
            // Decoded only as big as needed: its shorter side becomes the stored size
            val scale = AVATAR_SIZE_PX.toFloat() / min(info.size.width, info.size.height)
            if (scale < 1f) decoder.setTargetSize((info.size.width * scale).toInt().coerceAtLeast(1), (info.size.height * scale).toInt().coerceAtLeast(1))
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
        val side = min(decoded.width, decoded.height)
        val square = Bitmap.createBitmap(decoded, (decoded.width - side) / 2, (decoded.height - side) / 2, side, side)
        val sized = if (side > AVATAR_SIZE_PX) Bitmap.createScaledBitmap(square, AVATAR_SIZE_PX, AVATAR_SIZE_PX, true) else square
        directory.mkdirs()
        fileOf(userId).outputStream().use { sized.compress(Bitmap.CompressFormat.PNG, 100, it) }
        sized
    }.getOrNull()

    fun remove(userId: String) {
        fileOf(userId).delete()
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

/** The picture of the account [userId] and what can be done with it; null [picture] when it has none. */
internal class AvatarState(
    val picture: ImageBitmap?,
    val accent: Color?,
    val choose: (Uri) -> Unit,
    val remove: () -> Unit
)

/** The stored picture of [userId], read off the main thread, and changing it. */
@Composable
internal fun rememberAccountAvatar(userId: String): AvatarState {
    val context = LocalContext.current
    val store = remember { AccountAvatarStore(context) }
    val scope = rememberCoroutineScope()
    var bitmap by remember(userId) { mutableStateOf<Bitmap?>(null) }
    var accent by remember(userId) { mutableStateOf<Color?>(null) }
    LaunchedEffect(userId) {
        withContext(Dispatchers.IO) { store.load(userId) }?.let { loaded ->
            bitmap = loaded
            accent = withContext(Dispatchers.Default) { accentOf(loaded) }
        }
    }
    val picture = remember(bitmap) { bitmap?.asImageBitmap() }
    return AvatarState(
        picture = picture,
        accent = accent,
        choose = { uri ->
            scope.launch {
                val saved = withContext(Dispatchers.IO) { store.save(userId, uri) } ?: return@launch
                bitmap = saved
                accent = withContext(Dispatchers.Default) { accentOf(saved) }
            }
        },
        remove = {
            scope.launch {
                withContext(Dispatchers.IO) { store.remove(userId) }
                bitmap = null
                accent = null
            }
        }
    )
}
