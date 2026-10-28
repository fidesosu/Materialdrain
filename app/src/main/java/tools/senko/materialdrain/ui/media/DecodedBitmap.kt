package tools.senko.materialdrain.ui.media

import android.graphics.BitmapFactory
import android.util.Log
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val TAG_DECODE = "DecodedBitmap"

/** What [rememberDecoded] has made of some bytes so far. */
sealed interface Decoded {
    /** Still being decoded: nothing to show yet, not even the placeholder. */
    data object Decoding : Decoded

    /** Not a picture the device can read: the caller's placeholder. */
    data object Failed : Decoded

    class Done(val bitmap: ImageBitmap) : Decoded
}

/**
 * [bytes] (a song's embedded cover, a video's frame, an APK's icon) as a picture, decoded away from the main thread (a
 * cover can be a big picture, which held up the frame it was shown in) and, with [maxSizePx], only as big as needed to
 * show it that big. Decoded pictures are kept (see [DecodedCache]), so the same cover shown again (the miniplayer's in
 * the compact player, a queue row) is there at once; one already kept at another size stands in while its own decodes.
 */
@Composable
fun rememberDecoded(bytes: ByteArray, maxSizePx: Int = 0): Decoded {
    val hash = remember(bytes) { "${bytes.size}:${bytes.contentHashCode()}" }
    val bucket = DecodedCache.bucketOf(maxSizePx)
    // Made again for other bytes, so another song's cover never stands in for a moment
    val decoded = remember(hash, bucket) {
        mutableStateOf(
            DecodedCache.get(hash, bucket)?.let { Decoded.Done(it) as Decoded }
                ?: DecodedCache.anyOf(hash)?.let { Decoded.Done(it) } ?: Decoded.Decoding
        )
    }
    LaunchedEffect(hash, bucket) {
        if (DecodedCache.get(hash, bucket) != null) return@LaunchedEffect
        decoded.value = withContext(Dispatchers.Default) { decode(bytes, bucket) }?.let { bitmap ->
            DecodedCache.put(hash, bucket, bitmap)
            Decoded.Done(bitmap)
        } ?: Decoded.Failed
    }
    return decoded.value
}

/**
 * [bytes] as a picture (see [rememberDecoded]); null while it's decoded, or when they aren't one the device can read, so
 * the caller shows its placeholder instead.
 */
@Composable
fun rememberDecodedBitmap(bytes: ByteArray, maxSizePx: Int = 0): ImageBitmap? =
    (rememberDecoded(bytes, maxSizePx) as? Decoded.Done)?.bitmap

/** [bytes] decoded at about [bucket] pixels on its shorter side at the least (0: as it is); null when it isn't a picture. */
private fun decode(bytes: ByteArray, bucket: Int): ImageBitmap? = try {
    val options = BitmapFactory.Options()
    if (bucket > 0) {
        options.inJustDecodeBounds = true
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        var sample = 1
        val shorter = minOf(options.outWidth, options.outHeight)
        while (shorter / (sample * 2) >= bucket) sample *= 2
        options.inJustDecodeBounds = false
        options.inSampleSize = sample
    }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)?.asImageBitmap()
} catch (e: Exception) {
    Log.w(TAG_DECODE, "Couldn't decode ${bytes.size} bytes as a picture", e)
    null
}

/**
 * The pictures decoded lately, by their bytes and size: the sizes are rounded up to a few ([BUCKETS]), so covers shown at
 * about the same size share one.
 */
private object DecodedCache {
    private val BUCKETS = intArrayOf(128, 256, 512, 1024)

    private val pictures = object : LruCache<String, ImageBitmap>(maxOf(4 * 1024 * 1024, (Runtime.getRuntime().maxMemory() / 16).toInt())) {
        override fun sizeOf(key: String, value: ImageBitmap): Int = value.width * value.height * 4
    }

    /** The size [maxSizePx] is decoded at: the smallest bucket at least as big, or as it is (0) past the biggest. */
    fun bucketOf(maxSizePx: Int): Int = if (maxSizePx <= 0) 0 else BUCKETS.firstOrNull { it >= maxSizePx } ?: 0

    fun get(hash: String, bucket: Int): ImageBitmap? = pictures.get("$hash@$bucket")

    /** Any size kept of these bytes, the biggest first: to stand in while the size asked for decodes. */
    fun anyOf(hash: String): ImageBitmap? = (listOf(0) + BUCKETS.reversed()).firstNotNullOfOrNull { get(hash, it) }

    fun put(hash: String, bucket: Int, bitmap: ImageBitmap) {
        pictures.put("$hash@$bucket", bitmap)
    }
}
