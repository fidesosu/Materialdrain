package tools.senko.materialdrain.ui.media

import android.media.MediaDataSource
import android.media.MediaMetadataRetriever
import android.util.Log
import okhttp3.OkHttpClient

private const val TAG = "HttpMediaDataSource"

/** How much of the file one request fetches. The retriever reads the header, the tags and an index: a few blocks. */
private const val BLOCK = 64 * 1024

/** The blocks kept, the ones read last; a cover or a frame needs a few at a time. */
private const val BLOCKS_KEPT = 32

/**
 * A file on a host for [MediaMetadataRetriever] (a song's tags and cover, a video's frame), read through the app's own
 * HTTP client: its connections, and the login of the host (see [HostRequestAuth]). In blocks (see [RangedReader]), as the
 * media framework fetches a URL itself. A host that sends the whole file instead of a range fails it (see
 * [setHostDataSource], which then lets the framework fetch it). It reads no more than [maxBytes] of it.
 */
class HttpMediaDataSource(client: OkHttpClient, url: String, headers: Map<String, String>, maxBytes: Long = Long.MAX_VALUE) : MediaDataSource() {

    private val reader = RangedReader(client, url, headers, BLOCK, BLOCKS_KEPT, maxBytes)

    /** How much of the file has been fetched so far. */
    val fetchedBytes: Long get() = reader.fetchedBytes

    override fun getSize(): Long = reader.size()

    override fun readAt(position: Long, buffer: ByteArray, offset: Int, length: Int): Int = reader.readAt(position, buffer, offset, length)

    override fun close() = reader.close()
}

/**
 * Opens [src] in this retriever: through the app's HTTP client (see [HttpMediaDataSource]) when it can, else the media
 * framework fetches it itself, as before, with the host's login. Through the app's client, no more than [maxBytes] is read,
 * and what's read is the source given back; null when the framework fetches it.
 */
fun MediaMetadataRetriever.setHostDataSource(src: String, maxBytes: Long = Long.MAX_VALUE): HttpMediaDataSource? {
    val headers = HostRequestAuth.headersFor(src)
    val client = HostRequestAuth.client
    if (client != null && (src.startsWith("https://") || src.startsWith("http://"))) {
        try {
            return HttpMediaDataSource(client, src, headers, maxBytes).also { setDataSource(it) }
        } catch (e: Exception) {
            Log.w(TAG, "read by the media framework instead: ${e.message}")
        }
    }
    setDataSource(src, headers)
    return null
}
