package tools.senko.materialdrain.ui.media

import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

/**
 * A file on a host read in blocks of [blockSize], each fetched with a range when something first reads in it, and the
 * [blocksKept] read last kept: what reads a file here and there (the media framework, another app through the file
 * picker) gets it without the whole file being fetched first. Through the app's own HTTP client, with the host's login
 * ([headers]). A host that sends the whole file instead of a range fails it, as a file of gigabytes could come.
 */
class RangedReader(
    private val client: OkHttpClient,
    private val url: String,
    private val headers: Map<String, String>,
    private val blockSize: Int,
    private val blocksKept: Int
) {
    private var size = -1L
    private val blocks = object : LinkedHashMap<Long, ByteArray>(blocksKept, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, ByteArray>) = size > blocksKept
    }

    /** The file's size, from the host's answer to the first block. */
    @Synchronized
    fun size(): Long {
        if (size < 0) block(0)
        return size
    }

    /** Reads up to [length] bytes at [position] into [buffer]; the bytes read, or -1 at the end of the file. */
    @Synchronized
    fun readAt(position: Long, buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        var done = 0
        while (done < length) {
            val at = position + done
            if (size in 0..at) break
            val index = at / blockSize
            val data = block(index)
            val inBlock = (at - index * blockSize).toInt()
            if (inBlock >= data.size) break
            val count = minOf(length - done, data.size - inBlock)
            System.arraycopy(data, inBlock, buffer, offset + done, count)
            done += count
        }
        return if (done == 0) -1 else done
    }

    private fun block(index: Long): ByteArray {
        blocks[index]?.let { return it }
        val start = index * blockSize
        val request = Request.Builder()
            .url(url)
            .apply { headers.forEach { (name, value) -> header(name, value) } }
            .header("Range", "bytes=$start-${start + blockSize - 1}")
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
    fun close() = blocks.clear()
}
