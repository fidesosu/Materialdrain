package tools.senko.materialdrain.ui.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import coil.ImageLoader
import coil.decode.DataSource
import coil.decode.ImageSource
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.fetch.SourceResult
import coil.request.Options
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import okhttp3.Headers.Companion.toHeaders
import okio.Buffer
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.security.MessageDigest

/**
 * Album covers as thumbnails, for hosts that stream their files over HTTP(S) (Pixeldrain, and configs like it). The
 * cover is read out of the audio file itself, only the parts of it that hold the cover rather than the whole song, and
 * kept on disk; a song without one shows the host's own thumbnail instead. (SMB shares make theirs on the device, see
 * SmbContentServer.)
 *
 * A thumbnail URL of the form `audiocover://cover?src=…&fallback=…&v=…`, see [audioCoverUrl], loaded by [Fetcher].
 */
object AudioCovers {

    private const val TAG = "AudioCovers"
    private const val SCHEME = "audiocover"

    /** The longest side of a cover thumbnail, in pixels. */
    private const val COVER_PX = 320

    /** Reading a cover takes a decoder and a few requests to the host: only a couple at once. */
    private val permits = Semaphore(2)

    /**
     * The thumbnail URL of an audio file streamed from [rawUrl]: its own cover, or [fallback] (the host's thumbnail)
     * when it has none. [version] (e.g. when it was modified) keeps a changed file from showing an old cover.
     */
    fun audioCoverUrl(rawUrl: String, fallback: String?, version: String?): String =
        Uri.Builder().scheme(SCHEME).authority("cover")
            .appendQueryParameter("src", rawUrl)
            .apply {
                fallback?.let { appendQueryParameter("fallback", it) }
                version?.let { appendQueryParameter("v", it) }
            }
            .build().toString()

    /** Loads `audiocover://` thumbnails for Coil. Added to the app's image loader by AppContainer. */
    class Factory(private val context: Context) : Fetcher.Factory<Uri> {
        override fun create(data: Uri, options: Options, imageLoader: ImageLoader): Fetcher? =
            if (data.scheme == SCHEME) CoverFetcher(context.applicationContext, data, options, imageLoader) else null
    }

    private class CoverFetcher(
        private val context: Context,
        private val data: Uri,
        private val options: Options,
        private val imageLoader: ImageLoader
    ) : Fetcher {
        override suspend fun fetch(): FetchResult? {
            val src = data.getQueryParameter("src") ?: return null
            val cover = coverOf(src, data.getQueryParameter("v").orEmpty())
            if (cover != null) {
                return SourceResult(ImageSource(Buffer().write(cover), context), mimeType = null, dataSource = DataSource.DISK)
            }
            // No cover in the file: the host's own thumbnail, fetched as any other, with the login it needs
            val fallback = data.getQueryParameter("fallback") ?: return null
            val fallbackOptions = options.copy(headers = HostRequestAuth.headersFor(fallback).toHeaders())
            val fetcher = imageLoader.components.newFetcher(Uri.parse(fallback), fallbackOptions, imageLoader)?.first ?: return null
            return fetcher.fetch()
        }

        /** The cover of the song at [src], from the disk when it was read before; null when it has none. */
        private suspend fun coverOf(src: String, version: String): ByteArray? {
            val dir = File(context.cacheDir, "audio-covers")
            val key = sha1("$src\n$version")
            val cached = File(dir, "$key.jpg")
            val none = File(dir, "$key.none")
            if (cached.isFile) return cached.readBytes()
            if (none.isFile) return null
            val cover = permits.withPermit { readCover(src) }
            runCatching {
                dir.mkdirs()
                if (cover == null) {
                    none.createNewFile()
                } else {
                    // Written whole, then moved in place: a cover cut short by a crash is never read back
                    val partial = File(dir, "$key.part")
                    partial.writeBytes(cover)
                    partial.renameTo(cached)
                }
            }
            return cover
        }

        /** Reads the embedded picture of [src] over the network: the media framework fetches only the ranges it needs. */
        private fun readCover(src: String): ByteArray? {
            val retriever = MediaMetadataRetriever()
            return try {
                retriever.setDataSource(src, HostRequestAuth.headersFor(src))
                retriever.embeddedPicture?.let { scaled(it) }
            } catch (e: Exception) {
                Log.w(TAG, "no cover read from $src: ${e.message}")
                null
            } finally {
                retriever.release()
            }
        }
    }

    /** [bytes] scaled down to [COVER_PX] and encoded as a JPEG. */
    private fun scaled(bytes: ByteArray): ByteArray {
        val bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(ByteBuffer.wrap(bytes))) { decoder, info, _ ->
            val longest = maxOf(info.size.width, info.size.height)
            if (longest > COVER_PX) {
                val scale = COVER_PX.toFloat() / longest
                decoder.setTargetSize((info.size.width * scale).toInt().coerceAtLeast(1), (info.size.height * scale).toInt().coerceAtLeast(1))
            }
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
        bitmap.recycle()
        return out.toByteArray()
    }

    private fun sha1(text: String): String =
        MessageDigest.getInstance("SHA-1").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }
}
