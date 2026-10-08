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
 * Thumbnails read out of the files themselves, for hosts that stream their files over HTTP(S) (Pixeldrain, and configs
 * like it): a song's album cover, and a frame of a video. Only the parts of the file that are needed are read rather than
 * the whole of it, and the result is kept on disk. A file without one shows the fallback given, if any, or else nothing
 * (the file's own tile stays). (SMB shares make theirs on the device, see SmbContentServer.)
 *
 * A video frame is read even when the host has a thumbnail of its own: a host gives some videos only a generic picture of
 * a film (one it couldn't read, e.g. an unusual codec or its index at the end of the file), and that picture can't be
 * told apart from a real thumbnail. The host's one is the fallback, for a video the device can't read either.
 *
 * A thumbnail URL of the form `audiocover://cover?src=…&fallback=…&v=…` (a song) or `audiocover://frame?…` (a video),
 * see [audioCoverUrl] and [videoFrameUrl], loaded by [Fetcher].
 */
object MediaCovers {

    private const val TAG = "MediaCovers"
    private const val SCHEME = "audiocover"
    private const val COVER = "cover"
    private const val FRAME = "frame"

    /** The longest side of a thumbnail, in pixels. */
    private const val COVER_PX = 320

    /** How far into a video its frame is taken: past a fade in or a title card, which the first frame often is. */
    private const val FRAME_AT_FRACTION = 0.1

    /** But never further in than this, so a long video doesn't need a read from deep inside it. */
    private const val FRAME_AT_MOST_US = 30_000_000L

    /** Reading a thumbnail takes a decoder and a few requests to the host: only a couple at once. */
    private val permits = Semaphore(2)

    /**
     * The thumbnail URL of an audio file streamed from [rawUrl]: its own cover, or [fallback] (the host's thumbnail)
     * when it has none. [version] (e.g. when it was modified) keeps a changed file from showing an old cover.
     */
    fun audioCoverUrl(rawUrl: String, fallback: String?, version: String?): String = url(COVER, rawUrl, fallback, version)

    /** The thumbnail URL of a video streamed from [rawUrl]: a frame of it, or [fallback] when none can be read. See [audioCoverUrl]. */
    fun videoFrameUrl(rawUrl: String, fallback: String?, version: String?): String = url(FRAME, rawUrl, fallback, version)

    private fun url(kind: String, rawUrl: String, fallback: String?, version: String?): String =
        Uri.Builder().scheme(SCHEME).authority(kind)
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
        private val video = data.authority == FRAME

        override suspend fun fetch(): FetchResult? {
            val src = data.getQueryParameter("src") ?: return null
            val cover = coverOf(src, data.getQueryParameter("v").orEmpty())
            if (cover != null) {
                return SourceResult(ImageSource(Buffer().write(cover), context), mimeType = null, dataSource = DataSource.DISK)
            }
            // Nothing in the file: the host's own thumbnail, fetched as any other, with the login it needs. Without one,
            // the load fails, and the file's tile underneath stays (see FileIcon)
            val fallback = data.getQueryParameter("fallback") ?: throw NoCoverException()
            val fallbackOptions = options.copy(headers = HostRequestAuth.headersFor(fallback).toHeaders())
            val fetcher = imageLoader.components.newFetcher(Uri.parse(fallback), fallbackOptions, imageLoader)?.first ?: return null
            return fetcher.fetch()
        }

        /** The thumbnail of the file at [src], from the disk when it was read before; null when it has none. */
        private suspend fun coverOf(src: String, version: String): ByteArray? {
            // Songs keep the folder they always had, so the covers read before stay
            val dir = File(context.cacheDir, if (video) "video-frames" else "audio-covers")
            val key = sha1("$src\n$version")
            val cached = File(dir, "$key.jpg")
            val none = File(dir, "$key.none")
            if (cached.isFile) return cached.readBytes()
            if (none.isFile) return null
            val cover = permits.withPermit { if (video) readFrame(src) else readCover(src) }
            runCatching {
                dir.mkdirs()
                if (cover == null) {
                    none.createNewFile()
                } else {
                    // Written whole, then moved in place: a thumbnail cut short by a crash is never read back
                    val partial = File(dir, "$key.part")
                    partial.writeBytes(cover)
                    partial.renameTo(cached)
                }
            }
            return cover
        }

        /** Reads the embedded picture of [src] over the network: the media framework fetches only the ranges it needs. */
        private fun readCover(src: String): ByteArray? = withRetriever(src) { retriever ->
            retriever.embeddedPicture?.let { scaled(it) }
        }

        /**
         * Reads a frame of the video at [src] over the network, the nearest key frame to a tenth of the way in (see
         * [FRAME_AT_FRACTION]): a key frame decodes on its own, so only the ranges around it are fetched.
         */
        private fun readFrame(src: String): ByteArray? = withRetriever(src) { retriever ->
            val durationUs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()?.times(1000)
            val atUs = durationUs?.let { (it * FRAME_AT_FRACTION).toLong().coerceAtMost(FRAME_AT_MOST_US) } ?: 0L
            val frame = retriever.getScaledFrameAtTime(atUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, COVER_PX, COVER_PX)
                ?: retriever.getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)?.let { scaled(it) }
            frame?.let { jpeg(it) }
        }

        private fun withRetriever(src: String, read: (MediaMetadataRetriever) -> ByteArray?): ByteArray? {
            val retriever = MediaMetadataRetriever()
            return try {
                retriever.setDataSource(src, HostRequestAuth.headersFor(src))
                read(retriever)
            } catch (e: Exception) {
                Log.w(TAG, "nothing read from $src: ${e.message}")
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
        return jpeg(bitmap)
    }

    /** [bitmap] scaled down to [COVER_PX]. */
    private fun scaled(bitmap: Bitmap): Bitmap {
        val longest = maxOf(bitmap.width, bitmap.height)
        if (longest <= COVER_PX) return bitmap
        val scale = COVER_PX.toFloat() / longest
        val small = Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * scale).toInt().coerceAtLeast(1),
            (bitmap.height * scale).toInt().coerceAtLeast(1),
            true
        )
        bitmap.recycle()
        return small
    }

    /** [bitmap] encoded as a JPEG; the bitmap is recycled. */
    private fun jpeg(bitmap: Bitmap): ByteArray {
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
        bitmap.recycle()
        return out.toByteArray()
    }

    /** A file with no thumbnail of its own and no other to show instead. */
    private class NoCoverException : Exception("No thumbnail in this file")

    private fun sha1(text: String): String =
        MessageDigest.getInstance("SHA-1").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }
}
