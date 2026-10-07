package tools.senko.materialdrain.provider.smb

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.media.MediaDataSource
import android.media.MediaMetadataRetriever
import android.util.Log
import jcifs.smb.SmbFile
import jcifs.smb.SmbRandomAccessFile
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.Semaphore

/**
 * A tiny HTTP server on this device only (127.0.0.1), which hands out the files of SMB shares by URL. Everything in the
 * app that shows a file works from a URL (the image loader, the video and audio player), and a share has none of its
 * own; this gives each file one, so they all work for SMB unchanged:
 *
 * - `/<token>/raw/<host>/<path>`: the file itself, with ranges, so a video can be seeked without reading what's before
 * - `/<token>/thumb/<host>/<path>`: a small picture of it (an image scaled down, a frame of a video, the cover of a
 *   song), made once and kept in [cacheDir]
 *
 * Only reachable from this device, and only with [token], a random secret made fresh every time the app starts, so
 * other apps can't read the shares through it. Started on first use.
 */
object SmbContentServer {

    private const val TAG = "SmbContentServer"

    /** Where thumbnails are kept between runs; set by the app at startup. Without it they're made again each run. */
    @Volatile
    var cacheDir: File? = null

    /** The longest side of a thumbnail, in pixels: sharp in the list (56dp) and the search (40dp) on any screen. */
    private const val THUMBNAIL_PX = 320

    /** Images bigger than this aren't scaled down for a thumbnail: the whole file would have to come over the network. */
    private const val MAX_THUMBNAIL_SOURCE_BYTES = 40L * 1024 * 1024

    /** How many thumbnails are made at once: each reads from the share, and a video frame takes a decoder. */
    private val thumbnailPermits = Semaphore(3)

    private const val COPY_BUFFER_BYTES = 256 * 1024

    private val token: String = ByteArray(16).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }

    /** The share clients of the hosts in use, by host id; a host built again (its config changed) replaces its old one. */
    private val clients = ConcurrentHashMap<String, SmbClient>()

    /** Thumbnails which couldn't be made this run (not a picture after all, a broken file): not tried again. */
    private val failedThumbnails: MutableSet<String> = Collections.newSetFromMap(ConcurrentHashMap())

    private val workers = Executors.newCachedThreadPool { runnable ->
        Thread(runnable, "smb-content").apply { isDaemon = true }
    }

    @Volatile
    private var port = 0

    internal fun register(hostId: String, client: SmbClient) {
        clients[hostId] = client
    }

    /** The URL of the content of the file at [path] of host [hostId]. */
    internal fun rawUrl(hostId: String, path: String): String = url("raw", hostId, path, version = null)

    /**
     * The URL of a thumbnail of the file at [path], or null when it's not a kind that has one. [version] (e.g. the time it
     * was modified) is part of the URL and of the cached thumbnail, so a changed file gets a new one.
     */
    internal fun thumbnailUrl(hostId: String, path: String, version: String?): String? =
        if (ThumbnailKind.of(path) == null) null else url("thumb", hostId, path, version)

    private fun url(kind: String, hostId: String, path: String, version: String?): String {
        val encodedPath = SmbPaths.normalize(path).split('/').joinToString("/") { encode(it) }
        val query = version?.takeIf { it.isNotBlank() }?.let { "?v=${encode(it)}" }.orEmpty()
        return "http://127.0.0.1:${ensureStarted()}/$token/$kind/${encode(hostId)}/$encodedPath$query"
    }

    @Synchronized
    private fun ensureStarted(): Int {
        if (port != 0) return port
        val socket = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
        port = socket.localPort
        Thread({
            while (true) {
                val client = try {
                    socket.accept()
                } catch (e: IOException) {
                    Log.e(TAG, "stopped accepting", e)
                    break
                }
                workers.execute { serve(client) }
            }
        }, "smb-content-accept").apply { isDaemon = true }.start()
        return port
    }

    // --- HTTP ---

    private class Request(val method: String, val segments: List<String>, val query: Map<String, String>, val range: String?)

    private fun serve(socket: Socket) {
        try {
            socket.use {
                it.soTimeout = 30_000
                val input = BufferedInputStream(it.getInputStream())
                val output = BufferedOutputStream(it.getOutputStream(), COPY_BUFFER_BYTES)
                val request = readRequest(input) ?: return
                respond(request, output)
                output.flush()
            }
        } catch (_: IOException) {
            // The other end went away, e.g. the player seeked elsewhere and dropped this connection: nothing to do
        } catch (e: Exception) {
            Log.e(TAG, "request failed", e)
        }
    }

    private fun readRequest(input: InputStream): Request? {
        val requestLine = readLine(input) ?: return null
        val parts = requestLine.split(' ')
        if (parts.size < 2) return null
        var range: String? = null
        while (true) {
            val line = readLine(input) ?: return null
            if (line.isEmpty()) break
            val colon = line.indexOf(':')
            if (colon > 0 && line.substring(0, colon).trim().equals("Range", ignoreCase = true)) range = line.substring(colon + 1).trim()
        }
        val target = parts[1]
        val pathPart = target.substringBefore('?')
        val query = target.substringAfter('?', "").split('&').filter { it.contains('=') }
            .associate { decode(it.substringBefore('=')) to decode(it.substringAfter('=')) }
        return Request(parts[0], pathPart.split('/').filter { it.isNotEmpty() }.map { decode(it) }, query, range)
    }

    /** One line of the request, without its line break; null when the connection ends first or the line is too long. */
    private fun readLine(input: InputStream): String? {
        val line = ByteArrayOutputStream()
        while (true) {
            val byte = input.read()
            if (byte < 0) return null
            if (byte == '\n'.code) break
            if (byte != '\r'.code) line.write(byte)
            if (line.size() > 8192) return null
        }
        return line.toString(Charsets.ISO_8859_1.name())
    }

    private fun respond(request: Request, output: OutputStream) {
        if (request.method != "GET" && request.method != "HEAD") return status(output, 405, "Method Not Allowed")
        val segments = request.segments
        // token / kind / host / path...
        if (segments.size < 4 || segments[0] != token) return status(output, 403, "Forbidden")
        val client = clients[segments[2]] ?: return status(output, 404, "Not Found")
        val path = segments.drop(3).joinToString("/")
        when (segments[1]) {
            "raw" -> serveRaw(client, path, request, output)
            "thumb" -> serveThumbnail(segments[2], client, path, request, output)
            else -> status(output, 404, "Not Found")
        }
    }

    private fun serveRaw(client: SmbClient, path: String, request: Request, output: OutputStream) {
        val file = client.file(path, directory = false)
        if (!file.exists() || file.isDirectory) return status(output, 404, "Not Found")
        val size = file.length()
        val range = request.range?.let { parseRange(it, size) }
        if (request.range != null && range == null) {
            return head(output, 416, "Range Not Satisfiable", listOf("Content-Range" to "bytes */$size", "Content-Length" to "0"))
        }
        val start = range?.first ?: 0L
        val end = range?.last ?: (size - 1)
        val length = (end - start + 1).coerceAtLeast(0)
        val headers = buildList {
            add("Content-Type" to mimeTypeOf(path))
            add("Content-Length" to length.toString())
            add("Accept-Ranges" to "bytes")
            if (range != null) add("Content-Range" to "bytes $start-$end/$size")
        }
        if (range != null) head(output, 206, "Partial Content", headers) else head(output, 200, "OK", headers)
        if (request.method == "HEAD" || length == 0L) return
        SmbRandomAccessFile(file, "r").use { source ->
            source.seek(start)
            val buffer = ByteArray(COPY_BUFFER_BYTES)
            var remaining = length
            while (remaining > 0) {
                val read = source.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
                if (read < 0) break
                output.write(buffer, 0, read)
                remaining -= read
            }
        }
    }

    /** The bytes a "Range: bytes=…" header asks for, or null when it asks for none of the file (or can't be read). */
    private fun parseRange(header: String, size: Long): LongRange? {
        if (!header.startsWith("bytes=") || size == 0L) return null
        // Several ranges at once are rare (players ask for one); the first one is served
        val spec = header.removePrefix("bytes=").substringBefore(',').trim()
        val from = spec.substringBefore('-').trim()
        val to = spec.substringAfter('-').trim()
        return when {
            from.isEmpty() -> to.toLongOrNull()?.takeIf { it > 0 }?.let { suffix -> maxOf(0L, size - suffix) until size }
            else -> {
                val start = from.toLongOrNull() ?: return null
                if (start >= size) return null
                val end = to.toLongOrNull()?.coerceAtMost(size - 1) ?: (size - 1)
                if (end < start) null else start..end
            }
        }
    }

    private fun serveThumbnail(hostId: String, client: SmbClient, path: String, request: Request, output: OutputStream) {
        val kind = ThumbnailKind.of(path) ?: return status(output, 404, "Not Found")
        val key = sha1("$hostId\n$path\n${request.query["v"].orEmpty()}")
        if (key in failedThumbnails) return status(output, 404, "Not Found")
        val cached = cacheDir?.let { File(it, "$key.thumb") }
        val bytes = cached?.takeIf { it.isFile }?.readBytes() ?: run {
            val made = thumbnailPermits.acquireAndRun { makeThumbnail(kind, client.file(path, directory = false)) }
            if (made == null) {
                failedThumbnails += key
                return status(output, 404, "Not Found")
            }
            cached?.let { file ->
                runCatching {
                    file.parentFile?.mkdirs()
                    // Written whole, then moved in place: a thumbnail cut short by a crash is never read back
                    val partial = File(file.parentFile, "${file.name}.part")
                    partial.writeBytes(made)
                    partial.renameTo(file)
                }
            }
            made
        }
        head(
            output, 200, "OK",
            listOf(
                // Pictures by their own first bytes: a thumbnail is a JPEG, or a PNG when it has see-through parts
                "Content-Type" to if (bytes.size > 1 && bytes[0] == 0x89.toByte()) "image/png" else "image/jpeg",
                "Content-Length" to bytes.size.toString(),
                "Cache-Control" to "private, max-age=86400"
            )
        )
        if (request.method == "GET") output.write(bytes)
    }

    private fun status(output: OutputStream, code: Int, reason: String) =
        head(output, code, reason, listOf("Content-Length" to "0"))

    private fun head(output: OutputStream, code: Int, reason: String, headers: List<Pair<String, String>>) {
        val text = buildString {
            append("HTTP/1.1 ").append(code).append(' ').append(reason).append("\r\n")
            headers.forEach { (name, value) -> append(name).append(": ").append(value).append("\r\n") }
            append("Connection: close\r\n\r\n")
        }
        output.write(text.toByteArray(Charsets.ISO_8859_1))
    }

    // --- Thumbnails ---

    private enum class ThumbnailKind {
        IMAGE, VIDEO, AUDIO;

        companion object {
            private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "heic", "heif", "avif")
            private val VIDEO_EXTENSIONS = setOf("mp4", "m4v", "mkv", "webm", "mov", "3gp", "avi", "ts")
            private val AUDIO_EXTENSIONS = setOf("mp3", "m4a", "flac", "ogg", "opus", "aac", "wav")

            fun of(path: String): ThumbnailKind? = when (path.substringAfterLast('/').substringAfterLast('.', "").lowercase()) {
                in IMAGE_EXTENSIONS -> IMAGE
                in VIDEO_EXTENSIONS -> VIDEO
                in AUDIO_EXTENSIONS -> AUDIO
                else -> null
            }
        }
    }

    /** A thumbnail of [file], encoded; null when there's no picture to be had from it. */
    private fun makeThumbnail(kind: ThumbnailKind, file: SmbFile): ByteArray? = try {
        val bitmap = when (kind) {
            ThumbnailKind.IMAGE -> {
                val size = file.length()
                if (size > MAX_THUMBNAIL_SOURCE_BYTES) null else decodeScaled(file.inputStream.use { it.readBytes() })
            }
            ThumbnailKind.VIDEO -> withRetriever(file) { retriever ->
                // A second in, past a black first frame; a shorter video gives whatever frame it has
                retriever.getScaledFrameAtTime(1_000_000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, THUMBNAIL_PX, THUMBNAIL_PX)
                    ?: retriever.getScaledFrameAtTime(-1, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, THUMBNAIL_PX, THUMBNAIL_PX)
            }
            ThumbnailKind.AUDIO -> withRetriever(file) { retriever -> retriever.embeddedPicture?.let { decodeScaled(it) } }
        }
        bitmap?.let { encode(it) }
    } catch (e: Exception) {
        Log.w(TAG, "no thumbnail for ${file.name}: ${e.message}")
        null
    }

    /** [bytes] as an image no bigger than [THUMBNAIL_PX], turned the way its EXIF data says. */
    private fun decodeScaled(bytes: ByteArray): Bitmap =
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(ByteBuffer.wrap(bytes))) { decoder, info, _ ->
            val longest = maxOf(info.size.width, info.size.height)
            if (longest > THUMBNAIL_PX) {
                val scale = THUMBNAIL_PX.toFloat() / longest
                decoder.setTargetSize((info.size.width * scale).toInt().coerceAtLeast(1), (info.size.height * scale).toInt().coerceAtLeast(1))
            }
            // Software bitmaps: they're encoded right away, which hardware ones can't be
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }

    private fun encode(bitmap: Bitmap): ByteArray {
        val out = ByteArrayOutputStream()
        if (bitmap.hasAlpha()) bitmap.compress(Bitmap.CompressFormat.PNG, 100, out) else bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
        bitmap.recycle()
        return out.toByteArray()
    }

    /** Runs [block] with a retriever reading [file] straight from the share, only the parts it needs. */
    private fun <T> withRetriever(file: SmbFile, block: (MediaMetadataRetriever) -> T): T {
        val source = SmbMediaDataSource(file)
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(source)
            return block(retriever)
        } finally {
            retriever.release()
            source.close()
        }
    }

    // --- Helpers ---

    private fun <T> Semaphore.acquireAndRun(block: () -> T): T {
        acquire()
        try {
            return block()
        } finally {
            release()
        }
    }

    private fun sha1(text: String): String =
        MessageDigest.getInstance("SHA-1").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }

    private fun mimeTypeOf(path: String): String =
        java.net.URLConnection.guessContentTypeFromName(path.substringAfterLast('/'))
            ?: android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(path.substringAfterLast('.', "").lowercase())
            ?: "application/octet-stream"

    private fun encode(text: String): String = URLEncoder.encode(text, "UTF-8").replace("+", "%20")

    private fun decode(text: String): String = try {
        URLDecoder.decode(text.replace("+", "%2B"), "UTF-8")
    } catch (_: Exception) {
        text
    }
}

/** A file on a share as the media framework reads it: from any position, without reading what's before it. */
private class SmbMediaDataSource(file: SmbFile) : MediaDataSource() {
    private val access = SmbRandomAccessFile(file, "r")
    private val size = file.length()

    @Synchronized
    override fun readAt(position: Long, buffer: ByteArray, offset: Int, size: Int): Int {
        if (position >= this.size) return -1
        access.seek(position)
        return access.read(buffer, offset, size)
    }

    override fun getSize(): Long = size

    @Synchronized
    override fun close() = access.close()
}
