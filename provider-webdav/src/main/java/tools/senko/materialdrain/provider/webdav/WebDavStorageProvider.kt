package tools.senko.materialdrain.provider.webdav

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.CancellationException
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
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
import tools.senko.materialdrain.provider.api.StorageListing
import tools.senko.materialdrain.provider.api.StorageNode
import tools.senko.materialdrain.provider.api.StorageProvider
import tools.senko.materialdrain.provider.api.StorageRef
import tools.senko.materialdrain.provider.api.WebDavConfig
import java.io.OutputStream

/**
 * Nextcloud / ownCloud / TrueNAS WebDAV share, browsed hierarchically under [WebDavConfig.rootPath] -
 * the natural shape for WebDAV, unlike the flat [FileListOps]/[FileStoreOps] model the generic-REST
 * adapter uses.
 *
 * @param credentials what the user entered for this host; read on every request so changes apply at once
 */
class WebDavStorageProvider(
    override val id: String,
    private val config: WebDavConfig,
    private val credentials: () -> Credentials = { Credentials() }
) : StorageProvider {

    private val client = WebDavClient(config.rootPath)

    override val displayName: String = config.name
    override val kind: ProviderKind = ProviderKind.WEBDAV
    override val capabilities: Set<ProviderCapability> = setOf(
        ProviderCapability.BROWSE, ProviderCapability.MKDIR, ProviderCapability.RENAME,
        ProviderCapability.UPLOAD, ProviderCapability.DOWNLOAD, ProviderCapability.DELETE
    )

    override val fileStore: FileStoreOps? = null
    override val fileList: FileListOps? = null
    override val account: AccountOps? = null

    // --- Credentials / base URL ---

    private fun resolveAuth(): AuthHeader = WebDavAuth.resolve(config.auth, credentials())

    /** [WebDavConfig.baseUrl] with `{username}` (Nextcloud-style per-user DAV paths) filled in. */
    private fun effectiveBaseUrl(): String {
        // java.net.URLEncoder (not android.net.Uri, which isn't available outside an Android runtime) is
        // used here since this module's provider classes are also exercised by plain JVM unit tests.
        val encodedUsername = java.net.URLEncoder.encode(credentials().username.trim(), "UTF-8").replace("+", "%20")
        return config.baseUrl.replace("{username}", encodedUsername)
    }

    // --- Browsing ---

    override val browse: BrowseOps = object : BrowseOps {
        override suspend fun list(path: String): ApiResponse<StorageListing> {
            val normalizedPath = path.trim('/')
            val baseUrl = effectiveBaseUrl()
            val result = try {
                client.propfind(baseUrl, normalizedPath, depth = 1, resolveAuth())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                return ApiResponse.Error(networkError(e))
            } ?: return ApiResponse.Error(badEndpoint())
            if (!result.isSuccessful) return ApiResponse.Error(statusError(result))
            val entries = WebDavXml.parseMultistatus(result.bodyText)
            val selfUrl = client.resolveUrl(baseUrl, normalizedPath) ?: return ApiResponse.Error(badEndpoint())
            val selfPath = selfUrl.encodedPath.trimEnd('/')
            val children = entries
                .filter { hrefPath(it.href) != selfPath }
                .map { toStorageNode(it, normalizedPath) }
                .sortedWith(compareBy<StorageNode> { !it.isDirectory }.thenBy { it.name.lowercase() })
            return ApiResponse.Success(
                StorageListing(
                    breadcrumb = breadcrumbFor(normalizedPath),
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
        ): ApiResponse<StorageNode> {
            val normalizedPath = path.trim('/')
            val baseUrl = effectiveBaseUrl()
            if (makeParents) ensureDirectoryChain(baseUrl, normalizedPath.substringBeforeLast('/', ""))
            val result = try {
                client.put(baseUrl, normalizedPath, fileUri, context, resolveAuth(), onProgress)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                return ApiResponse.Error(networkError(e))
            } ?: return ApiResponse.Error(badEndpoint())
            if (!result.isSuccessful) return ApiResponse.Error(statusError(result))
            return ApiResponse.Success(
                StorageNode(ref = StorageRef(path = normalizedPath), name = normalizedPath.substringAfterLast('/'), isDirectory = false)
            )
        }

        override suspend fun download(path: String, outputStream: OutputStream, onProgress: (read: Long, total: Long?) -> Unit): ApiResponse<Long> {
            val normalizedPath = path.trim('/')
            val baseUrl = effectiveBaseUrl()
            var totalCopied = 0L
            val code = try {
                client.get(baseUrl, normalizedPath, resolveAuth(), outputStream) { read, total -> totalCopied = read; onProgress(read, total) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                return ApiResponse.Error(networkError(e))
            } ?: return ApiResponse.Error(badEndpoint())
            return if (code in 200..299) ApiResponse.Success(totalCopied)
            else ApiResponse.Error(ProviderError("download_failed_status_$code", "Download failed: HTTP $code"))
        }

        override suspend fun createDirectory(path: String, makeParents: Boolean): ApiResponse<Unit> {
            val normalizedPath = path.trim('/')
            if (normalizedPath.isEmpty()) return ApiResponse.Error(ProviderError("invalid_path", "A folder needs a name."))
            val baseUrl = effectiveBaseUrl()
            if (makeParents) ensureDirectoryChain(baseUrl, normalizedPath.substringBeforeLast('/', ""))
            val result = try {
                client.mkcol(baseUrl, normalizedPath, resolveAuth())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                return ApiResponse.Error(networkError(e))
            } ?: return ApiResponse.Error(badEndpoint())
            return when {
                result.isSuccessful -> ApiResponse.Success(Unit)
                result.code == 405 -> ApiResponse.Error(ProviderError("already_exists", "A folder with this name already exists."))
                else -> ApiResponse.Error(statusError(result))
            }
        }

        override suspend fun rename(path: String, targetPath: String, makeParents: Boolean): ApiResponse<StorageNode> {
            val normalizedPath = path.trim('/')
            val normalizedTarget = targetPath.trim('/')
            val baseUrl = effectiveBaseUrl()
            if (makeParents) ensureDirectoryChain(baseUrl, normalizedTarget.substringBeforeLast('/', ""))
            val result = try {
                client.move(baseUrl, normalizedPath, normalizedTarget, resolveAuth())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                return ApiResponse.Error(networkError(e))
            } ?: return ApiResponse.Error(badEndpoint())
            if (!result.isSuccessful) return ApiResponse.Error(statusError(result))
            return ApiResponse.Success(
                // The caller already knows whether this was a file or a folder; this result is only used for
                // success/failure, the listing is re-fetched for display afterward.
                StorageNode(ref = StorageRef(path = normalizedTarget), name = normalizedTarget.substringAfterLast('/'), isDirectory = false)
            )
        }

        override suspend fun delete(path: String, recursive: Boolean): ApiResponse<Unit> {
            val normalizedPath = path.trim('/')
            val baseUrl = effectiveBaseUrl()
            if (!recursive) {
                // WebDAV DELETE on a collection is always recursive server-side, so a non-recursive delete is
                // enforced here by refusing it when the path isn't empty (a file's "listing" is always empty,
                // so this never blocks deleting a file).
                val listing = list(path)
                if (listing is ApiResponse.Success && listing.data.children.isNotEmpty()) {
                    return ApiResponse.Error(ProviderError("directory_not_empty", "The folder is not empty."))
                }
            }
            val result = try {
                client.delete(baseUrl, normalizedPath, resolveAuth())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                return ApiResponse.Error(networkError(e))
            } ?: return ApiResponse.Error(badEndpoint())
            return if (result.isSuccessful) ApiResponse.Success(Unit) else ApiResponse.Error(statusError(result))
        }
    }

    /**
     * The address of a file. It needs the login (see [requestHeaders]), so it's only usable inside the app, which is
     * where previews and thumbnails are loaded. There's no public link for an arbitrary share, so there's no share link.
     */
    private fun fileUrl(node: StorageNode): String? {
        if (node.isDirectory) return null
        return client.resolveUrl(effectiveBaseUrl(), node.ref.path)?.toString()
    }

    /** The login for media loaded straight from the share, only for addresses under the share's base URL. */
    override fun requestHeaders(url: String): Map<String, String> {
        if (!url.startsWith(effectiveBaseUrl())) return emptyMap()
        val header = resolveAuth() ?: return emptyMap()
        return mapOf(header.first to header.second)
    }

    override fun shareUrl(node: StorageNode): String? = null

    // Images are their own thumbnail: Coil scales them down to the row size
    override fun thumbnailUrl(node: StorageNode): String? =
        if (node.mimeType?.startsWith("image/") == true) fileUrl(node) else null

    override fun rawContentUrl(node: StorageNode, attachment: Boolean): String? = fileUrl(node)

    override suspend fun validate(): ProviderValidationResult {
        val baseUrl = effectiveBaseUrl()
        val reachable = try {
            client.propfind(baseUrl, "", depth = 0, null)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
        return ProviderValidationResult(
            listOf(
                FieldValidation("base_url", "Base URL", reachable != null, if (reachable != null) "Reachable" else "Couldn't reach it"),
                checkCredentials(baseUrl)
            )
        )
    }

    private suspend fun checkCredentials(baseUrl: String): FieldValidation {
        val auth = resolveAuth()
        if (auth == null) {
            return FieldValidation(
                "credentials", "Credentials", ok = false,
                message = "Nothing entered: add an API key" + if (config.auth.passwordAuth != null) " or a username and password" else ""
            )
        }
        return try {
            val result = client.propfind(baseUrl, "", depth = 0, auth)
                ?: return FieldValidation("credentials", "Credentials", ok = false, message = "The root couldn't be resolved")
            when {
                result.isSuccessful -> FieldValidation("credentials", "Credentials", ok = true, message = "Accepted")
                result.code == 401 || result.code == 403 -> FieldValidation("credentials", "Credentials", ok = false, message = "Rejected (HTTP ${result.code})")
                else -> FieldValidation("credentials", "Credentials", ok = false, message = "Unexpected answer: HTTP ${result.code}")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            FieldValidation("credentials", "Credentials", ok = false, message = "Couldn't check: ${e.message ?: "the host couldn't be reached"}")
        }
    }

    // --- Helpers ---

    private suspend fun ensureDirectoryChain(baseUrl: String, parentPath: String) {
        if (parentPath.isEmpty()) return
        val auth = resolveAuth()
        var built = ""
        parentPath.split('/').filter { it.isNotEmpty() }.forEach { segment ->
            built = if (built.isEmpty()) segment else "$built/$segment"
            try {
                client.mkcol(baseUrl, built, auth)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Best effort: a missing ancestor surfaces clearly when the real operation below fails too.
            }
        }
    }

    private fun toStorageNode(entry: WebDavEntry, parentPath: String): StorageNode {
        val name = decodeSegment(hrefPath(entry.href).substringAfterLast('/'))
        val childPath = if (parentPath.isEmpty()) name else "$parentPath/$name"
        return StorageNode(
            ref = StorageRef(path = childPath),
            name = name,
            isDirectory = entry.isCollection,
            size = entry.contentLength,
            modifiedAt = entry.lastModified,
            mimeType = entry.contentType
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

    /** The path portion of an href, whether the server sent it absolute or relative; trailing slash trimmed. */
    private fun hrefPath(href: String): String {
        val path = if (href.startsWith("http://") || href.startsWith("https://")) href.toHttpUrlOrNull()?.encodedPath ?: href else href
        return path.trimEnd('/')
    }

    private fun decodeSegment(segment: String): String = try {
        java.net.URLDecoder.decode(segment.replace("+", "%2B"), "UTF-8")
    } catch (_: Exception) {
        segment
    }

    private fun statusError(result: WebDavResult): ProviderError =
        ProviderError(code = "http_${result.code}", message = "Request failed: HTTP ${result.code}", httpStatus = result.code)

    private fun networkError(e: Exception): ProviderError = ProviderError("network_error", e.message ?: "Couldn't reach the server.")

    private fun badEndpoint(): ProviderError = ProviderError("bad_endpoint", "The base URL couldn't be resolved.")
}
