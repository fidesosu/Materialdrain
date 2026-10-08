package tools.senko.materialdrain.ui.media

import android.util.Log
import coil.ImageLoader
import coil.decode.BitmapFactoryDecoder
import coil.decode.DecodeResult
import coil.decode.Decoder
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.decode.ImageSource
import coil.fetch.SourceResult
import coil.request.Options
import okio.Buffer
import okio.BufferedSource
import okio.ByteString.Companion.encodeUtf8
import kotlin.coroutines.cancellation.CancellationException

/**
 * Animated images (GIFs, animated WebP) anywhere the app shows a picture: the previews, fullscreen, the thumbnails.
 * Added to the app's image loader by AppContainer, so whichever screen loads a picture, an animated one moves.
 *
 * They're told apart by their first bytes, not by the type the host reports, which is often missing or generic.
 * Android's own decoder ([ImageDecoderDecoder]) handles nearly all of them, but turns some GIFs down (slightly broken
 * ones, which browsers still play); those are decoded by the older GIF decoder ([GifDecoder]) instead, and failing that,
 * their first frame is shown as a still picture rather than nothing.
 */
object AnimatedImages {

    private const val TAG = "AnimatedImages"

    /** Larger animated files are left to Android's decoder alone, which streams them, rather than read whole first. */
    private const val MAX_BUFFERED_BYTES = 64L * 1024 * 1024

    class Factory : Decoder.Factory {
        override fun create(result: SourceResult, options: Options, imageLoader: ImageLoader): Decoder? {
            val source = result.source.source()
            val kind = when {
                isGif(source) -> Kind.GIF
                isAnimatedWebP(source) -> Kind.WEBP
                else -> return null
            }
            return AnimatedDecoder(result, options, kind)
        }
    }

    private enum class Kind { GIF, WEBP }

    private class AnimatedDecoder(private val result: SourceResult, private val options: Options, private val kind: Kind) : Decoder {
        override suspend fun decode(): DecodeResult {
            val source = result.source.source()
            // Big files: Android's decoder only, it reads them as it goes
            if (!source.request(MAX_BUFFERED_BYTES)) {
                val bytes = source.readByteString()
                fun fresh() = ImageSource(Buffer().write(bytes), options.context)
                val decoders = buildList<Pair<String, () -> Decoder>> {
                    add("ImageDecoder" to { ImageDecoderDecoder(fresh(), options) })
                    if (kind == Kind.GIF) add("GifDecoder" to { GifDecoder(fresh(), options) })
                    add("first frame" to { BitmapFactoryDecoder(fresh(), options) })
                }
                var lastError: Exception? = null
                for ((name, decoder) in decoders) {
                    try {
                        return decoder().decode() ?: continue
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Log.w(TAG, "$name couldn't decode this ${kind.name.lowercase()} (${bytes.size} bytes): ${e.message}")
                        lastError = e
                    }
                }
                throw lastError ?: IllegalStateException("No decoder could read this image")
            }
            return ImageDecoderDecoder(result.source, options).decode()
        }
    }

    private val GIF87A = "GIF87a".encodeUtf8()
    private val GIF89A = "GIF89a".encodeUtf8()
    private val RIFF = "RIFF".encodeUtf8()
    private val WEBP = "WEBP".encodeUtf8()
    private val VP8X = "VP8X".encodeUtf8()

    /** "GIF87a" or "GIF89a" at the start. */
    fun isGif(source: BufferedSource): Boolean = source.rangeEquals(0, GIF87A) || source.rangeEquals(0, GIF89A)

    /** A WebP with the extended header (VP8X) whose animation flag is set. */
    fun isAnimatedWebP(source: BufferedSource): Boolean =
        source.rangeEquals(0, RIFF) && source.rangeEquals(8, WEBP) && source.rangeEquals(12, VP8X) &&
            source.request(21) && (source.buffer[20].toInt() and 0x02) != 0
}
