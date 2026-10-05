package tools.senko.materialdrain.provider.smb

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import jcifs.smb.SmbAuthException
import jcifs.smb.SmbException
import jcifs.smb.SmbFile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import tools.senko.materialdrain.provider.api.AccountOps
import tools.senko.materialdrain.provider.api.ApiResponse
import tools.senko.materialdrain.provider.api.BrowseOps
import tools.senko.materialdrain.provider.api.Credentials
import tools.senko.materialdrain.provider.api.FieldValidation
import tools.senko.materialdrain.provider.api.FileListOps
import tools.senko.materialdrain.provider.api.FileStoreOps
import tools.senko.materialdrain.provider.api.ProviderCapability
import tools.senko.materialdrain.provider.api.ProviderError
import tools.senko.materialdrain.provider.api.ProviderKind
import tools.senko.materialdrain.provider.api.ProviderValidationResult
import tools.senko.materialdrain.provider.api.SmbAuthMode
import tools.senko.materialdrain.provider.api.SmbConfig
import tools.senko.materialdrain.provider.api.StorageListing
import tools.senko.materialdrain.provider.api.StorageNode
import tools.senko.materialdrain.provider.api.StorageProvider
import tools.senko.materialdrain.provider.api.StorageRef
import java.io.InputStream
import java.io.OutputStream
import java.net.ConnectException
import java.net.MalformedURLException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.net.URLConnection
import java.time.Instant

/**
 * A Windows, Samba or NAS share over SMB, browsed as folders and files. An SMB share has no public links and no
 * web address for its files, so there are no thumbnails or previews: a file shows its name, size and modified time.
 *
 * Paths are relative to the top of the share. [SmbConfig.rootPath] is where the browser starts.
 *
 * @param credentials what the user entered for this host; read on every request so changes apply at once
 */
class SmbStorageProvider(
    override val id: String,
    private val config: SmbConfig,
    private val credentials: () -> Credentials = { Credentials() }
) : StorageProvider {

    private val client = SmbClient(config, credentials)

    override val displayName: String = config.name
    override val kind: ProviderKind = ProviderKind.SMB
    override val capabilities: Set<ProviderCapability> = setOf(
        ProviderCapability.BROWSE, ProviderCapability.MKDIR, ProviderCapability.RENAME,
        ProviderCapability.UPLOAD, ProviderCapability.DOWNLOAD, ProviderCapability.DELETE
    )

    override val fileStore: FileStoreOps? = null
    override val fileList: FileListOps? = null
    override val account: AccountOps? = null

    override val rootPath: String
        get() = SmbPaths.normalize(config.rootPath)

    // --- Browsing ---

    override val browse: BrowseOps = object : BrowseOps {
        override suspend fun list(path: String): ApiResponse<StorageListing> = onShare {
            val folder = SmbPaths.normalize(path)
            val dir = client.file(folder, directory = true)
            if (!dir.isDirectory) return@onShare ApiResponse.Error(ProviderError("not_a_folder", "This is not a folder."))
            val children = dir.listFiles()
                .map { toNode(it, folder) }
                .sortedWith(compareBy<StorageNode> { !it.isDirectory }.thenBy { it.name.lowercase() })
            ApiResponse.Success(
                StorageListing(
                    breadcrumb = breadcrumbFor(folder),
                    children = children,
                    canWrite = ProviderCapability.UPLOAD in capabilities,
                    canDelete = ProviderCapability.DELETE in capabilities
                )
            )
        }

        override suspend fun upload(
            path: String,
            fileUri: Uri,
            context: Context,
            makeParents: Boolean,
            onProgress: (sent: Long, total: Long?) -> Unit
        ): ApiResponse<StorageNode> = onShare {
            val target = SmbPaths.normalize(path)
            if (target.isEmpty()) return@onShare ApiResponse.Error(ProviderError("invalid_path", "A file needs a name."))
            if (makeParents) client.file(SmbPaths.parentOf(target), directory = true).mkdirs()
            val total = sizeOf(context, fileUri)
            val input = context.contentResolver.openInputStream(fileUri)
                ?: return@onShare ApiResponse.Error(ProviderError("unreadable_file", "The file can't be read."))
            input.use { source ->
                client.file(target, directory = false).outputStream.use { sink -> copy(source, sink) { sent -> onProgress(sent, total) } }
            }
            ApiResponse.Success(StorageNode(ref = StorageRef(path = target), name = SmbPaths.nameOf(target), isDirectory = false, size = total))
        }

        override suspend fun download(
            path: String,
            outputStream: OutputStream,
            onProgress: (read: Long, total: Long?) -> Unit
        ): ApiResponse<Long> = onShare {
            val file = client.file(SmbPaths.normalize(path), directory = false)
            if (!file.exists()) return@onShare ApiResponse.Error(notFound())
            val total = file.length()
            file.inputStream.use { source -> ApiResponse.Success(copy(source, outputStream) { read -> onProgress(read, total) }) }
        }

        override suspend fun createDirectory(path: String, makeParents: Boolean): ApiResponse<Unit> = onShare {
            val folder = SmbPaths.normalize(path)
            if (folder.isEmpty()) return@onShare ApiResponse.Error(ProviderError("invalid_path", "A folder needs a name."))
            val dir = client.file(folder, directory = true)
            if (dir.exists()) return@onShare ApiResponse.Error(ProviderError("already_exists", "A folder with this name already exists."))
            if (makeParents) dir.mkdirs() else dir.mkdir()
            ApiResponse.Success(Unit)
        }

        override suspend fun rename(path: String, targetPath: String, makeParents: Boolean): ApiResponse<StorageNode> = onShare {
            val source = SmbPaths.normalize(path)
            val target = SmbPaths.normalize(targetPath)
            if (target.isEmpty() || source.isEmpty()) return@onShare ApiResponse.Error(ProviderError("invalid_path", "The share's top can't be renamed."))
            val from = client.file(source, directory = false).let { if (it.isDirectory) client.file(source, directory = true) else it }
            if (!from.exists()) return@onShare ApiResponse.Error(notFound())
            if (makeParents) client.file(SmbPaths.parentOf(target), directory = true).mkdirs()
            val to = client.file(target, directory = from.isDirectory)
            if (to.exists()) return@onShare ApiResponse.Error(ProviderError("already_exists", "An item with this name already exists."))
            from.renameTo(to)
            ApiResponse.Success(StorageNode(ref = StorageRef(path = target), name = SmbPaths.nameOf(target), isDirectory = from.isDirectory))
        }

        override suspend fun delete(path: String, recursive: Boolean): ApiResponse<Unit> = onShare {
            val target = SmbPaths.normalize(path)
            // The top of the share is never deleted: it's the host itself, not an item in it
            if (target.isEmpty()) return@onShare ApiResponse.Error(ProviderError("invalid_path", "The top of the share can't be deleted."))
            val item = client.file(target, directory = false).let { if (it.isDirectory) client.file(target, directory = true) else it }
            if (!item.exists()) return@onShare ApiResponse.Error(notFound())
            if (item.isDirectory && !recursive && item.listFiles().isNotEmpty()) {
                return@onShare ApiResponse.Error(ProviderError("directory_not_empty", "The folder is not empty."))
            }
            deleteTree(target, item)
            ApiResponse.Success(Unit)
        }
    }

    // --- Media ---

    // No public or web address for a file on a share, so nothing to share, preview or stream by URL
    override fun shareUrl(node: StorageNode): String? = null
    override fun thumbnailUrl(node: StorageNode): String? = null
    override fun rawContentUrl(node: StorageNode, attachment: Boolean): String? = null

    // --- Checks ---

    override suspend fun validate(): ProviderValidationResult = withContext(Dispatchers.IO) {
        val hostOk = config.host.isNotBlank() && config.share.isNotBlank() && config.port in 1..65535
        if (!hostOk) {
            return@withContext ProviderValidationResult(
                listOf(FieldValidation("host", "Host and share", ok = false, message = "Enter the host name, the port and the share name"))
            )
        }
        val reachable = try {
            client.file("", directory = true).exists()
            FieldValidation("host", "Host and share", ok = true, message = "Reachable")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            FieldValidation("host", "Host and share", ok = false, message = failure(e).message)
        }
        ProviderValidationResult(listOf(reachable, checkLogin()))
    }

    /** Lists the top of the share: a login the share accepts gets through, a refused one says so. */
    private fun checkLogin(): FieldValidation {
        if (config.auth != SmbAuthMode.CREDENTIALS) {
            return FieldValidation("credentials", "Sign-in", ok = true, message = "No sign-in: ${config.auth.name.lowercase()}")
        }
        if (credentials().username.isBlank()) {
            return FieldValidation("credentials", "Sign-in", ok = false, message = "Enter a username and password")
        }
        return try {
            client.file("", directory = true).listFiles()
            FieldValidation("credentials", "Sign-in", ok = true, message = "Accepted")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            FieldValidation("credentials", "Sign-in", ok = false, message = failure(e).message)
        }
    }

    // --- Helpers ---

    /** Runs a blocking share call off the main thread, with its failures turned into an [ApiResponse.Error]. */
    private suspend fun <T> onShare(block: () -> ApiResponse<T>): ApiResponse<T> = withContext(Dispatchers.IO) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ApiResponse.Error(failure(e))
        }
    }

    private fun deleteTree(path: String, item: SmbFile) {
        if (item.isDirectory) {
            item.listFiles().forEach { child -> deleteTree("$path/${SmbPaths.displayName(child.name.trimEnd('/'))}", child) }
        }
        item.delete()
    }

    private fun copy(source: InputStream, sink: OutputStream, onBytes: (Long) -> Unit): Long {
        val buffer = ByteArray(BUFFER_BYTES)
        var total = 0L
        while (true) {
            val read = source.read(buffer)
            if (read < 0) break
            sink.write(buffer, 0, read)
            total += read
            onBytes(total)
        }
        return total
    }

    private fun sizeOf(context: Context, uri: Uri): Long? =
        context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getColumnIndex(OpenableColumns.SIZE).takeIf { it >= 0 && !cursor.isNull(it) }?.let { cursor.getLong(it) } else null
        }

    private fun toNode(item: SmbFile, folder: String): StorageNode {
        val name = SmbPaths.displayName(item.name.trimEnd('/'))
        val path = if (folder.isEmpty()) name else "$folder/$name"
        val directory = item.isDirectory
        return StorageNode(
            ref = StorageRef(path = path),
            name = name,
            isDirectory = directory,
            size = if (directory) null else item.length(),
            modifiedAt = item.lastModified.takeIf { it > 0 }?.let { Instant.ofEpochMilli(it).toString() },
            mimeType = if (directory) null else URLConnection.guessContentTypeFromName(name)
        )
    }

    private fun breadcrumbFor(path: String): List<StorageNode> {
        if (path.isEmpty()) return emptyList()
        var built = ""
        return path.split('/').map { segment ->
            built = if (built.isEmpty()) segment else "$built/$segment"
            StorageNode(ref = StorageRef(path = built), name = segment, isDirectory = true)
        }
    }

    private fun notFound(): ProviderError = ProviderError("not_found", "This item isn't on the share any more.")

    /** Turns a failure of the share into a message the app can show. The sign-in and the protocol errors are named. */
    private fun failure(e: Exception): ProviderError = when (e) {
        is SmbAuthException -> ProviderError("auth_failed", "The share refused the username or password.")
        // jCIFS wraps a failed connection in its own status, so the cause tells which it was
        is SmbException -> when (e.cause) {
            is UnknownHostException -> ProviderError("unknown_host", "The host can't be found: check its name or address.")
            is ConnectException, is SocketTimeoutException -> ProviderError("network_error", "Couldn't reach ${config.host}:${config.port}.")
            else -> statusError(e.ntStatus)
        }
        is UnknownHostException -> ProviderError("unknown_host", "The host can't be found: check its name or address.")
        is ConnectException, is SocketTimeoutException -> ProviderError("network_error", "Couldn't reach ${config.host}:${config.port}.")
        is MalformedURLException -> ProviderError("invalid_address", "The host or share name has characters that aren't allowed.")
        else -> ProviderError("network_error", e.message ?: "Couldn't reach the share.")
    }

    private fun statusError(status: Int): ProviderError = when (status) {
        STATUS_OBJECT_NAME_NOT_FOUND -> notFound()
        STATUS_OBJECT_NAME_COLLISION -> ProviderError("already_exists", "An item with this name already exists.")
        STATUS_ACCESS_DENIED -> ProviderError("access_denied", "The share doesn't allow this.")
        STATUS_DIRECTORY_NOT_EMPTY -> ProviderError("directory_not_empty", "The folder is not empty.")
        else -> ProviderError("smb_status", "The share answered with an error (0x${status.toUInt().toString(16).uppercase()}).")
    }

    private companion object {
        const val BUFFER_BYTES = 64 * 1024

        // NT status codes from the SMB protocol, as jCIFS reports them (negative as Java ints)
        val STATUS_OBJECT_NAME_NOT_FOUND = 0xC0000034.toInt()
        val STATUS_OBJECT_NAME_COLLISION = 0xC0000035.toInt()
        val STATUS_ACCESS_DENIED = 0xC0000022.toInt()
        val STATUS_DIRECTORY_NOT_EMPTY = 0xC0000101.toInt()
    }
}
