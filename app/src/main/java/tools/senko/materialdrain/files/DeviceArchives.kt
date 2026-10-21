package tools.senko.materialdrain.files

import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import tools.senko.materialdrain.provider.api.ApiResponse
import tools.senko.materialdrain.provider.api.ArchiveEntryDetails
import tools.senko.materialdrain.provider.api.ArchiveOps
import tools.senko.materialdrain.provider.api.ErrorCodes
import tools.senko.materialdrain.provider.api.ProviderCapability
import tools.senko.materialdrain.provider.api.ProviderError
import tools.senko.materialdrain.provider.api.StorageNode
import tools.senko.materialdrain.provider.api.StorageProvider
import tools.senko.materialdrain.provider.api.StorageRef
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.Charset
import java.util.zip.Inflater
import java.util.zip.InflaterInputStream
import kotlin.coroutines.cancellation.CancellationException

/**
 * How the app looks inside the archives of [provider]: the host's own way ([StorageProvider.archives]), or, for a host
 * with none whose files can be read in parts (SMB through the app's own server, WebDAV, S3), the app's own for zips (see
 * [DeviceZipArchives]). Null where neither can.
 */
fun archivesOf(provider: StorageProvider): ArchiveOps? =
    provider.archives ?: if (ProviderCapability.BROWSE in provider.capabilities) DeviceZipArchives(provider) else null

/** The extensions of the archives [DeviceZipArchives] reads: zips, under whatever name they go by. */
private val ZIP_EXTENSIONS = setOf("zip", "cbz", "jar", "apk")

/** How much of the end of a zip is read to find its directory: the end record (22 bytes) and the longest comment. */
private const val END_READ_BYTES = 22 + 0xFFFF

/** The biggest directory of a zip that's read; a bigger one has far more files than anyone looks through here. */
private const val MAX_DIRECTORY_BYTES = 32 * 1024 * 1024

private const val END_SIGNATURE = 0x06054b50
private const val ZIP64_LOCATOR_SIGNATURE = 0x07064b50
private const val ZIP64_END_SIGNATURE = 0x06064b50
private const val ENTRY_SIGNATURE = 0x02014b50
private const val LOCAL_SIGNATURE = 0x04034b50

/** One file (or folder, its path ending in "/") in a zip's directory, with where its data is. */
internal class ZipItem(
    val path: String,
    val method: Int,
    val encrypted: Boolean,
    val compressedSize: Long,
    val size: Long,
    val localHeaderOffset: Long
)

/**
 * Looks inside the zips of [provider] without downloading them, through the file's own address (rawContentUrl): only
 * the zip's directory at its end is read (a few requests for the parts of the file it's in), and for one file of it,
 * only that file's part. Stored and deflated files are read, as nearly every zip has; a file with a password isn't.
 *
 * A host which doesn't answer for parts of a file (no ranges) can't be looked inside this way: that's said, rather than
 * the whole archive being downloaded for it.
 */
class DeviceZipArchives(private val provider: StorageProvider) : ArchiveOps {

    override fun canOpen(archive: StorageRef): Boolean =
        archive.path.substringAfterLast('.', "").lowercase() in ZIP_EXTENSIONS && urlOf(archive) != null

    override suspend fun list(archive: StorageRef, inside: String): ApiResponse<List<StorageNode>> = reading {
        val items = directoryOf(archive)
        val prefix = inside.trim('/').let { if (it.isEmpty()) "" else "$it/" }
        // The direct entries of [inside]: its files, and its folders, whether the zip lists them or only their files
        val folders = LinkedHashSet<String>()
        val files = ArrayList<ZipItem>()
        for (item in items) {
            if (!item.path.startsWith(prefix) || item.path.length == prefix.length) continue
            val rest = item.path.substring(prefix.length)
            val slash = rest.indexOf('/')
            if (slash >= 0) folders += rest.substring(0, slash) else files += item
        }
        val nodes = folders.map { name ->
            StorageNode(ref = StorageRef(path = prefix + name), name = name, isDirectory = true)
        } + files.filter { it.path.substringAfterLast('/') !in folders }.map { item ->
            StorageNode(
                ref = StorageRef(path = item.path),
                name = item.path.substringAfterLast('/'),
                isDirectory = false,
                size = item.size,
                richDetails = ArchiveEntryDetails(archive, item.path)
            )
        }
        ApiResponse.Success(nodes)
    }

    override suspend fun read(
        archive: StorageRef,
        entryPath: String,
        outputStream: OutputStream,
        onProgress: (read: Long, total: Long?) -> Unit
    ): ApiResponse<Long> = reading {
        val path = entryPath.trimStart('/')
        val item = directoryOf(archive).firstOrNull { it.path == path }
            ?: return@reading ApiResponse.Error(ProviderError(ErrorCodes.NOT_FOUND, "That file isn't in the archive."))
        if (item.encrypted) return@reading ApiResponse.Error(ProviderError("encrypted_entry", "This file of the archive has a password."))
        if (item.method != 0 && item.method != 8) {
            return@reading ApiResponse.Error(ProviderError("unsupported_zip_compression", "This file of the archive is compressed in a way the app can't read."))
        }
        val url = urlOf(archive) ?: return@reading noAddress()
        // The local header has its own name and extra field lengths, which can differ from the directory's
        val local = range(url, item.localHeaderOffset, 30).order(ByteOrder.LITTLE_ENDIAN)
        if (local.getInt(0) != LOCAL_SIGNATURE) return@reading broken()
        val dataStart = item.localHeaderOffset + 30 + u16(local, 26) + u16(local, 28)
        if (item.compressedSize == 0L) {
            onProgress(0L, 0L)
            return@reading ApiResponse.Success(0L)
        }
        val connection = open(url, dataStart, dataStart + item.compressedSize - 1)
        try {
            val raw = connection.inputStream
            val data: InputStream = if (item.method == 8) InflaterInputStream(raw, Inflater(true), 64 * 1024) else raw
            val buffer = ByteArray(64 * 1024)
            var written = 0L
            data.use { source ->
                while (true) {
                    val read = source.read(buffer)
                    if (read < 0) break
                    outputStream.write(buffer, 0, read)
                    written += read
                    onProgress(written, item.size)
                }
            }
            ApiResponse.Success(written)
        } finally {
            connection.disconnect()
        }
    }

    // ---- Reading ----

    private fun urlOf(archive: StorageRef): String? =
        provider.rawContentUrl(StorageNode(ref = archive, name = archive.path.substringAfterLast('/'), isDirectory = false))

    /** The directory of the zip at [archive], read once and kept for the folders opened after (see zipDirectories). */
    private fun directoryOf(archive: StorageRef): List<ZipItem> {
        val url = urlOf(archive) ?: throw ArchiveFailure(noAddress().error)
        val key = "${provider.id}\n${archive.path}"
        zipDirectories.get(key)?.let { return it }
        return readDirectory(url).also { zipDirectories.put(key, it) }
    }

    private fun readDirectory(url: String): List<ZipItem> {
        // The end of the file, which also says how big the file is
        val (end, size) = tail(url, END_READ_BYTES)
        end.order(ByteOrder.LITTLE_ENDIAN)
        var at = -1
        for (i in end.limit() - 22 downTo 0) {
            if (end.getInt(i) == END_SIGNATURE) {
                at = i
                break
            }
        }
        if (at < 0) throw ArchiveFailure(ProviderError("not_a_zip", "This file isn't a zip archive."))
        val endOffset = size - (end.limit() - at)
        var count = u16(end, at + 10).toLong()
        var directorySize = u32(end, at + 12)
        var directoryOffset = u32(end, at + 16)
        // A zip too big for those fields keeps the real ones in its Zip64 end record, which a locator before this points to
        if (count == 0xFFFFL || directorySize == 0xFFFFFFFFL || directoryOffset == 0xFFFFFFFFL) {
            val locator = range(url, endOffset - 20, 20).order(ByteOrder.LITTLE_ENDIAN)
            if (locator.getInt(0) == ZIP64_LOCATOR_SIGNATURE) {
                val record = range(url, locator.getLong(8), 56).order(ByteOrder.LITTLE_ENDIAN)
                if (record.getInt(0) != ZIP64_END_SIGNATURE) throw ArchiveFailure(broken().error)
                count = record.getLong(32)
                directorySize = record.getLong(40)
                directoryOffset = record.getLong(48)
            }
        }
        if (directorySize > MAX_DIRECTORY_BYTES) throw ArchiveFailure(ProviderError("archive_too_large", "This archive has too many files to look through here."))
        val directory = range(url, directoryOffset, directorySize.toInt()).order(ByteOrder.LITTLE_ENDIAN)
        val items = ArrayList<ZipItem>(count.coerceAtMost(100_000L).toInt())
        var p = 0
        while (p + 46 <= directory.limit() && directory.getInt(p) == ENTRY_SIGNATURE) {
            val flags = u16(directory, p + 8)
            val method = u16(directory, p + 10)
            var compressed = u32(directory, p + 20)
            var uncompressed = u32(directory, p + 24)
            val nameLength = u16(directory, p + 28)
            val extraLength = u16(directory, p + 30)
            val commentLength = u16(directory, p + 32)
            var offset = u32(directory, p + 42)
            val nameBytes = ByteArray(nameLength).also { directory.position(p + 46); directory.get(it) }
            // Bit 11: the name is UTF-8; otherwise it's in the old DOS code page, as zips made on Windows have it
            val name = String(nameBytes, if (flags and 0x800 != 0) Charsets.UTF_8 else DOS_CHARSET)
            // Sizes and offsets too big for their fields are in the Zip64 extra field, in this order, only those that are
            var extra = p + 46 + nameLength
            val extraEnd = extra + extraLength
            while (extra + 4 <= extraEnd) {
                val id = u16(directory, extra)
                val length = u16(directory, extra + 2)
                if (id == 0x0001) {
                    var field = extra + 4
                    if (uncompressed == 0xFFFFFFFFL) { uncompressed = directory.getLong(field); field += 8 }
                    if (compressed == 0xFFFFFFFFL) { compressed = directory.getLong(field); field += 8 }
                    if (offset == 0xFFFFFFFFL) offset = directory.getLong(field)
                }
                extra += 4 + length
            }
            items += ZipItem(name.replace('\\', '/').trimStart('/'), method, flags and 0x1 != 0, compressed, uncompressed, offset)
            p += 46 + nameLength + extraLength + commentLength
        }
        return items
    }

    /** The last [count] bytes of the file at [url], and the file's whole size. */
    private fun tail(url: String, count: Int): Pair<ByteBuffer, Long> {
        val connection = open(url, rangeHeader = "bytes=-$count")
        try {
            val total = connection.getHeaderField("Content-Range")?.substringAfterLast('/')?.toLongOrNull()
                ?: throw noRanges()
            return ByteBuffer.wrap(connection.inputStream.use { it.readBytes() }) to total
        } finally {
            connection.disconnect()
        }
    }

    /** [count] bytes of the file at [url] from [start]. */
    private fun range(url: String, start: Long, count: Int): ByteBuffer {
        val connection = open(url, start, start + count - 1)
        try {
            val bytes = connection.inputStream.use { it.readBytes() }
            if (bytes.size < count) throw ArchiveFailure(broken().error)
            return ByteBuffer.wrap(bytes, 0, count).slice()
        } finally {
            connection.disconnect()
        }
    }

    private fun open(url: String, first: Long, last: Long): HttpURLConnection = open(url, "bytes=$first-$last")

    /** A request for the part [rangeHeader] of [url], with the host's login; one that isn't answered with that part fails. */
    private fun open(url: String, rangeHeader: String): HttpURLConnection {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 15_000
        connection.readTimeout = 30_000
        connection.setRequestProperty("Range", rangeHeader)
        // The bytes as they are on the host, so the range is of the file itself
        connection.setRequestProperty("Accept-Encoding", "identity")
        provider.requestHeaders(url).forEach { (name, value) -> connection.setRequestProperty(name, value) }
        val code = connection.responseCode
        if (code != HttpURLConnection.HTTP_PARTIAL) {
            connection.disconnect()
            throw if (code == HttpURLConnection.HTTP_OK) noRanges() else ArchiveFailure(ProviderError(ErrorCodes.NETWORK_ERROR, "The host answered with HTTP $code."))
        }
        return connection
    }

    /** Runs [block] off the main thread, with what goes wrong in it as an [ApiResponse.Error]. */
    private suspend fun <T> reading(block: suspend () -> ApiResponse<T>): ApiResponse<T> = withContext(Dispatchers.IO) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: ArchiveFailure) {
            ApiResponse.Error(e.error)
        } catch (e: IOException) {
            ApiResponse.Error(ProviderError(ErrorCodes.NETWORK_ERROR, e.message ?: "The archive couldn't be read."))
        } catch (e: RuntimeException) {
            // A directory whose numbers point outside of it
            ApiResponse.Error(broken().error)
        }
    }

    private class ArchiveFailure(val error: ProviderError) : Exception(error.message)

    private fun noRanges() = ArchiveFailure(ProviderError(ErrorCodes.NOT_SUPPORTED, "This host can't send parts of a file, so the archive can't be looked inside."))

    private fun noAddress() = ApiResponse.Error(ProviderError(ErrorCodes.NOT_SUPPORTED, "This archive can't be reached to look inside it."))

    private fun broken() = ApiResponse.Error(ProviderError("invalid_zip_file", "This archive is either not valid or damaged."))

    private fun u16(buffer: ByteBuffer, at: Int): Int = buffer.getShort(at).toInt() and 0xFFFF

    private fun u32(buffer: ByteBuffer, at: Int): Long = buffer.getInt(at).toLong() and 0xFFFFFFFFL
}

/** The directories of zips read lately, by host and path: opening another folder of an archive doesn't read it again. */
private val zipDirectories = LruCache<String, List<ZipItem>>(8)

/** The old DOS code page, which the names in zips made on Windows are in unless they say they're UTF-8. */
private val DOS_CHARSET: Charset = runCatching { Charset.forName("IBM437") }.getOrDefault(Charsets.UTF_8)
