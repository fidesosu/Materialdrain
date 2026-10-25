package tools.senko.materialdrain.ui.media

import android.media.MediaDataSource
import android.media.MediaMetadataRetriever
import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

private const val TAG = "HttpMediaDataSource"

/** How much of the file one request fetches. The retriever reads the header, the tags and an index: a few blocks. */
private const val BLOCK = 64 * 1024

/** The blocks kept, the ones read last; a cover or a frame needs a few at a time. */
private const val BLOCKS_KEPT = 32

/**
 * A file on a host for [MediaMetadataRetriever] (a song's tags and cover, a video's frame), read through the app's own
 * HTTP client: its connections, and the login of the host (see [HostRequestAuth]). In blocks, each fetched with a range
 * when the retriever first reads in it, as the media framework fetches a URL itself. A host that sends the whole file
 * instead of a range fails it (see [setHostDataSource], which then lets the framework fetch it).
 */
class HttpMediaDataSource(
    private val client: OkHttpClient,
    private val url: String,
    private val headers: Map<String, String>
) : MediaDataSource() {

    private var size = -1L
    private val blocks = object : LinkedHashMap<Long, ByteArray>(BLOCKS_KEPT, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, ByteArray>) = size > BLOCKS_KEPT
    }

    @Synchronized
    override fun getSize(): Long {
        if (size < 0) block(0)
        return size
    }

    @Synchronized
    override fun readAt(position: Long, buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        var done = 0
        while (done < length) {
            val at = position + done
            if (size in 0..at) break
            val index = at / BLOCK
            val data = block(index)
            val inBlock = (at - index * BLOCK).toInt()
            if (inBlock >= data.size) break
            val count = minOf(length - done, data.size - inBlock)
            System.arraycopy(data, inBlock, buffer, offset + done, count)
            done += count
        }
        return if (done == 0) -1 else done
    }

    private fun block(index: Long): ByteArray {
        blocks[index]?.let { return it }
        val start = index * BLOCK
        val request = Request.Builder()
            .url(url)
            .apply { headers.forEach { (name, value) -> header(name, value) } }
            .header("Range", "bytes=$start-${start + BLOCK - 1}")
            .build()
        client.newCall(request).execute().use { response ->
            // Only a range: a whole file (200) could be a video of gigabytes
            if (response.code == 416) return ByteArray(0)
            // Not the address in the message, which goes to the log: on some hosts it opens the file (see SmbContentServer)
            if (response.code != 206) throw IOException("HTTP ${response.code} instead of a range from ${request.url.host}")
            size = response.header("Content-Range")?.substringAfter('/')?.toLongOrNull() ?: size
            return response.body.bytes().also { blocks[index] = it }
        }
    }

    @Synchronized
    override fun close() = blocks.clear()
}

/**
 * Opens [src] in this retriever: through the app's HTTP client (see [HttpMediaDataSource]) when it can, else the media
 * framework fetches it itself, as before, with the host's login.
 */
fun MediaMetadataRetriever.setHostDataSource(src: String) {
    val headers = HostRequestAuth.headersFor(src)
    val client = HostRequestAuth.client
    if (client != null && (src.startsWith("https://") || src.startsWith("http://"))) {
        try {
            setDataSource(HttpMediaDataSource(client, src, headers))
            return
        } catch (e: Exception) {
            Log.w(TAG, "read by the media framework instead: ${e.message}")
        }
    }
    setDataSource(src, headers)
}
