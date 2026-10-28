package tools.senko.materialdrain.ui.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import coil3.Extras
import coil3.ImageLoader
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetchResult
import coil3.network.httpHeaders
import coil3.request.Options
import coil3.toAndroidUri
import coil3.toUri
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import okio.Buffer
import okio.FileSystem
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

    /** The longest side of a song's cover in the players (see [large]), in pixels: sharp on the whole screen. */
    private const val LARGE_COVER_PX = 1440

    /** How far into a video its frame is taken: past a fade in or a title card, which the first frame often is. */
    private const val FRAME_AT_FRACTION = 0.1

    /** But never further in than this, so a long video doesn't need a read from deep inside it. */
    private const val FRAME_AT_MOST_US = 30_000_000L

    /**
     * The most of a video read for its thumbnail's frame. Usually a few hundred KB do (the header, the index and a key
     * frame), but a video with its index at the end of a long file, or big key frames, could take much more of the
     * host's transfer, which Pixeldrain counts; then the host's own thumbnail stands in.
     */
    private const val MAX_FRAME_READ_BYTES = 4L * 1024 * 1024

    /** Reading a thumbnail takes a decoder and a few requests to the host: only a couple at once. */
    private val permits = Semaphore(2)

    /**
     * The thumbnail URL of an audio file streamed from [rawUrl]: its own cover, or [fallback] (the host's thumbnail)
     * when it has none. [version] (e.g. when it was modified) keeps a changed file from showing an old cover.
     */
    fun audioCoverUrl(rawUrl: String, fallback: String?, version: String?): String = url(COVER, rawUrl, fallback, version)

    /** The thumbnail URL of a video streamed from [rawUrl]: a frame of it, or [fallback] when none can be read. See [audioCoverUrl]. */
    fun videoFrameUrl(rawUrl: String, fallback: String?, version: String?): String = url(FRAME, rawUrl, fallback, version)

    /**
     * [art] as big as the players show it, when it's a song's cover read out of its file ([audioCoverUrl]): the cover
     * at up to [LARGE_COVER_PX] rather than the thumbnail's [COVER_PX], which shows its pixels on a cover the width of
     * the screen. Anything else is given back as it is.
     */
    fun large(art: Any?): Any? {
        val uri = when (art) {
            is Uri -> art
            is String -> if (art.startsWith("$SCHEME://$COVER")) Uri.parse(art) else return art
            else -> return art
        }
        if (uri.scheme != SCHEME || uri.authority != COVER || uri.getQueryParameter("px") != null) return art
        return uri.buildUpon().appendQueryParameter("px", LARGE_COVER_PX.toString()).build().toString()
    }

    private fun url(kind: String, rawUrl: String, fallback: String?, version: String?): String =
        Uri.Builder().scheme(SCHEME).authority(kind)
            .appendQueryParameter("src", rawUrl)
            .apply {
                fallback?.let { appendQueryParameter("fallback", it) }
                version?.let { appendQueryParameter("v", it) }
            }
            .build().toString()

    /** Loads `audiocover://` thumbnails for Coil. Added to the app's image loader by AppContainer. */
    class Factory(private val context: Context) : Fetcher.Factory<coil3.Uri> {
        override fun create(data: coil3.Uri, options: Options, imageLoader: ImageLoader): Fetcher? =
            if (data.scheme == SCHEME) CoverFetcher(context.applicationContext, data.toAndroidUri(), options, imageLoader) else null
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
            // A cover for the players (see large) is read at its own size; a video's frame always as the thumbnail's
            val px = data.getQueryParameter("px")?.toIntOrNull()?.takeIf { !video }?.coerceIn(COVER_PX, LARGE_COVER_PX) ?: COVER_PX
            val cover = coverOf(src, data.getQueryParameter("v").orEmpty(), px)
            if (cover != null) {
                return SourceFetchResult(ImageSource(Buffer().write(cover), FileSystem.SYSTEM), mimeType = null, dataSource = DataSource.DISK)
            }
            // Nothing in the file: the host's own thumbnail, fetched as any other, with the login it needs. Without one,
            // the load fails, and the file's tile underneath stays (see FileIcon)
            val fallback = data.getQueryParameter("fallback") ?: throw NoCoverException()
            val fallbackOptions = options.copy(extras = options.extras.newBuilder().set(Extras.Key.httpHeaders, hostHeaders(fallback)).build())
            val fetcher = imageLoader.components.newFetcher(fallback.toUri(), fallbackOptions, imageLoader)?.first ?: return null
            return fetcher.fetch()
        }

        /**
         * The thumbnail of the file at [src] at up to [px], from the disk when it was read before; null when it has none.
         * One that has none is remembered as such for every size.
         */
        private suspend fun coverOf(src: String, version: String, px: Int): ByteArray? {
            // Songs keep the folder they always had, so the covers read before stay. Videos start a new one: the first
            // version also kept "no frame" for videos which merely failed to load once, and so never tried them again
            val dir = File(context.cacheDir, if (video) "video-frames-2" else "audio-covers")
            val key = sha1("$src\n$version")
            val cached = File(dir, if (px == COVER_PX) "$key.jpg" else "$key-$px.jpg")
            val none = File(dir, "$key.none")
            if (cached.isFile) return cached.readBytes()
            if (none.isFile) return null
            val result = permits.withPermit { if (video) readFrame(src) else readCover(src, px) }
            runCatching {
                dir.mkdirs()
                when (result) {
                    is Read.Found -> {
                        // Written whole, then moved in place: a thumbnail cut short by a crash is never read back
                        val partial = File(dir, "${cached.nameWithoutExtension}.part")
                        partial.writeBytes(result.bytes)
                        partial.renameTo(cached)
                    }
                    // Only a file that was read and has nothing is remembered as such; one that couldn't be read (the
                    // network, the host being slow) is tried again the next time it's shown
                    Read.Nothing -> none.createNewFile()
                    Read.Failed -> Unit
                }
            }
            return (result as? Read.Found)?.bytes
        }

        /**
         * Reads the embedded picture of [src] over the network (the media framework fetches only the ranges it needs),
         * scaled down to [px].
         */
        private fun readCover(src: String, px: Int): Read = withRetriever(src) { retriever ->
            retriever.embeddedPicture?.let { scaled(it, px) }
        }

        /**
         * Reads a frame of the video at [src] over the network, the nearest key frame to a tenth of the way in (see
         * [FRAME_AT_FRACTION]): a key frame decodes on its own, so only the ranges around it are fetched. Some videos give
         * nothing for that (a very short one, an odd index); then the key frame before it, then the very first frame. One
         * which would take more than [MAX_FRAME_READ_BYTES] of the host's transfer has none (the host's thumbnail stands in).
         */
        private fun readFrame(src: String): Read = withRetriever(src, MAX_FRAME_READ_BYTES) { retriever ->
            val durationUs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()?.times(1000)
            val atUs = durationUs?.let { (it * FRAME_AT_FRACTION).toLong().coerceAtMost(FRAME_AT_MOST_US) } ?: 0L
            val frame = retriever.getScaledFrameAtTime(atUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, COVER_PX, COVER_PX)
                ?: retriever.getScaledFrameAtTime(atUs, MediaMetadataRetriever.OPTION_PREVIOUS_SYNC, COVER_PX, COVER_PX)
                ?: retriever.getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST)?.let { scaled(it) }
                ?: runCatching { retriever.getFrameAtIndex(0) }.getOrNull()?.let { scaled(it) }
            if (frame == null) Log.w(TAG, "no frame in $src (${durationUs?.div(1000)} ms long)")
            frame?.let { jpeg(it) }
        }

        private fun withRetriever(src: String, maxBytes: Long = Long.MAX_VALUE, read: (MediaMetadataRetriever) -> ByteArray?): Read {
            val retriever = MediaMetadataRetriever()
            var source: HttpMediaDataSource? = null
            return try {
                source = retriever.setHostDataSource(src, maxBytes)
                // What's read past the most fails, which the retriever takes as there being no frame: so it's remembered
                read(retriever)?.let { Read.Found(it) } ?: Read.Nothing
            } catch (e: Exception) {
                Log.w(TAG, "nothing read from $src: ${e.message}")
                Read.Failed
            } finally {
                source?.let { Log.d(TAG, "${if (video) "frame" else "cover"} read with ${it.fetchedBytes / 1024} KB of $src") }
                retriever.release()
            }
        }
    }

    /** What reading a file's thumbnail came to: one, none in the file, or the file couldn't be read. */
    private sealed interface Read {
        class Found(val bytes: ByteArray) : Read
        data object Nothing : Read
        data object Failed : Read
    }

    /** [bytes] (a picture, e.g. a song's cover) scaled down to [px] and encoded as a JPEG. */
    internal fun scaled(bytes: ByteArray, px: Int = COVER_PX): ByteArray {
        val bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(ByteBuffer.wrap(bytes))) { decoder, info, _ ->
            val longest = maxOf(info.size.width, info.size.height)
            if (longest > px) {
                val scale = px.toFloat() / longest
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
