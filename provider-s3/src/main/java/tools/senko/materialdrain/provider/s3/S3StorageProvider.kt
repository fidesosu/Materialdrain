package tools.senko.materialdrain.provider.s3

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.CancellationException
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
import tools.senko.materialdrain.provider.api.S3Config
import tools.senko.materialdrain.provider.api.StorageListing
import tools.senko.materialdrain.provider.api.StorageNode
import tools.senko.materialdrain.provider.api.StorageProvider
import tools.senko.materialdrain.provider.api.StorageRef
import java.io.OutputStream

private const val MAX_LIST_PAGES = 10
private const val MAX_RENAME_DELETE_PAGES = 50
private const val SHARE_LINK_EXPIRY_SECONDS = 3600L

/**
 * MinIO / TrueNAS-S3 / Backblaze B2 / R2 / Wasabi, browsed hierarchically via the standard
 * "`delimiter=/`, common-prefixes-as-folders" convention every S3 console/browser uses: a "folder" is a
 * key prefix ending in `/`, `createDirectory` writes a zero-byte marker object at it, `rename` copies
 * every object under the old prefix to the new one (S3 has no native rename). [S3Config.prefix] scopes
 * every request under a fixed subtree of the bucket, same role [tools.senko.materialdrain.provider.api.WebDavConfig.rootPath] plays for WebDAV.
 *
 * Credentials: [Credentials.username] is the Access Key ID, [Credentials.password] the Secret Access Key.
 *
 * @param credentials what the user entered for this host; read on every request so changes apply at once
 */
class S3StorageProvider(
    override val id: String,
    private val config: S3Config,
    private val credentials: () -> Credentials = { Credentials() }
) : StorageProvider {

    private val client = S3Client(config.endpoint, config.bucket, config.pathStyle)

    override val displayName: String = config.name
    override val kind: ProviderKind = ProviderKind.S3
    override val capabilities: Set<ProviderCapability> = setOf(
        ProviderCapability.BROWSE, ProviderCapability.MKDIR, ProviderCapability.RENAME,
        ProviderCapability.UPLOAD, ProviderCapability.DOWNLOAD, ProviderCapability.DELETE, ProviderCapability.SHARE_LINK
    )

    override val fileStore: FileStoreOps? = null
    override val fileList: FileListOps? = null
    override val account: AccountOps? = null

    // --- Credentials / keys ---

    private fun credentialsOrError(): Pair<String, String>? {
        val creds = credentials()
        val accessKeyId = creds.username.trim()
        val secretAccessKey = creds.password
        return if (accessKeyId.isNotBlank() && secretAccessKey.isNotBlank()) accessKeyId to secretAccessKey else null
    }

    /** The full object key for a path relative to [S3Config.prefix]. */
    private fun fullKey(path: String): String {
        val root = config.prefix.trim('/')
        return listOf(root, path).filter { it.isNotEmpty() }.joinToString("/")
    }

    // --- Browsing ---

    override val browse: BrowseOps = object : BrowseOps {
        override suspend fun list(path: String): ApiResponse<StorageListing> {
            val normalizedPath = path.trim('/')
            val (accessKeyId, secretAccessKey) = credentialsOrError() ?: return ApiResponse.Error(missingCredentials())
            val queryPrefix = fullKey(normalizedPath).let { if (it.isEmpty()) "" else "$it/" }

            val objects = mutableListOf<S3Object>()
            val prefixes = mutableListOf<String>()
            var token: String? = null
            var pages = 0
            do {
                val result = try {
                    client.listObjects(queryPrefix, "/", token, 1000, accessKeyId, secretAccessKey, config.region)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    return ApiResponse.Error(networkError(e))
                } ?: return ApiResponse.Error(badEndpoint())
                if (!result.isSuccessful) return ApiResponse.Error(statusError(result))
                val parsed = S3Xml.parseListResult(result.bodyText) ?: return ApiResponse.Error(ProviderError("bad_response", "The server's response couldn't be parsed."))
                objects += parsed.objects
                prefixes += parsed.commonPrefixes
                token = parsed.nextContinuationToken
                pages++
            } while (token != null && pages < MAX_LIST_PAGES)

            val folderNodes = prefixes.mapNotNull { commonPrefix ->
                val name = commonPrefix.removeSuffix("/").substringAfterLast('/')
                name.takeIf { it.isNotEmpty() }?.let {
                    val childPath = if (normalizedPath.isEmpty()) it else "$normalizedPath/$it"
                    StorageNode(ref = StorageRef(path = childPath), name = it, isDirectory = true)
                }
            }
            val fileNodes = objects.mapNotNull { obj ->
                if (obj.key == queryPrefix) return@mapNotNull null // the folder marker for this level, not a file
                val name = obj.key.removePrefix(queryPrefix)
                name.takeIf { it.isNotEmpty() }?.let {
                    val childPath = if (normalizedPath.isEmpty()) it else "$normalizedPath/$it"
                    StorageNode(ref = StorageRef(path = childPath), name = it, isDirectory = false, size = obj.size, modifiedAt = obj.lastModified)
                }
            }
            val children = (folderNodes + fileNodes).sortedWith(compareBy<StorageNode> { !it.isDirectory }.thenBy { it.name.lowercase() })
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
            path: String, fileUri: Uri, context: Context, makeParents: Boolean, onProgress: (sent: Long, total: Long?) -> Unit
        ): ApiResponse<StorageNode> {
            val normalizedPath = path.trim('/')
            val (accessKeyId, secretAccessKey) = credentialsOrError() ?: return ApiResponse.Error(missingCredentials())
            val result = try {
                client.putObject(fullKey(normalizedPath), fileUri, context, accessKeyId, secretAccessKey, config.region, onProgress)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                return ApiResponse.Error(networkError(e))
            } ?: return ApiResponse.Error(badEndpoint())
            if (!result.isSuccessful) return ApiResponse.Error(statusError(result))
            return ApiResponse.Success(StorageNode(ref = StorageRef(path = normalizedPath), name = normalizedPath.substringAfterLast('/'), isDirectory = false))
        }

        override suspend fun download(path: String, outputStream: OutputStream, onProgress: (read: Long, total: Long?) -> Unit): ApiResponse<Long> {
            val normalizedPath = path.trim('/')
            val (accessKeyId, secretAccessKey) = credentialsOrError() ?: return ApiResponse.Error(missingCredentials())
            var totalCopied = 0L
            val code = try {
                client.getObject(fullKey(normalizedPath), accessKeyId, secretAccessKey, config.region, outputStream) { read, total ->
                    totalCopied = read; onProgress(read, total)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                return ApiResponse.Error(networkError(e))
            } ?: return ApiResponse.Error(badEndpoint())
            return if (code in 200..299) ApiResponse.Success(totalCopied)
            else ApiResponse.Error(ProviderError("download_failed_status_$code", "Download failed: HTTP $code"))
        }

        override suspend fun createDirectory(path: String, makeParents: Boolean): ApiResponse<Unit> {
            // S3 prefixes need no ancestor creation (a nested prefix "shows up" once anything exists under it),
            // so makeParents is a no-op here - unlike WebDAV, there's nothing to create.
            val normalizedPath = path.trim('/')
            if (normalizedPath.isEmpty()) return ApiResponse.Error(ProviderError("invalid_path", "A folder needs a name."))
            val (accessKeyId, secretAccessKey) = credentialsOrError() ?: return ApiResponse.Error(missingCredentials())
            val result = try {
                client.putEmptyObject("${fullKey(normalizedPath)}/", accessKeyId, secretAccessKey, config.region)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                return ApiResponse.Error(networkError(e))
            } ?: return ApiResponse.Error(badEndpoint())
            return if (result.isSuccessful) ApiResponse.Success(Unit) else ApiResponse.Error(statusError(result))
        }

        override suspend fun rename(path: String, targetPath: String, makeParents: Boolean): ApiResponse<StorageNode> {
            val normalizedPath = path.trim('/')
            val normalizedTarget = targetPath.trim('/')
            val (accessKeyId, secretAccessKey) = credentialsOrError() ?: return ApiResponse.Error(missingCredentials())
            val oldKey = fullKey(normalizedPath)
            val newKey = fullKey(normalizedTarget)

            val probe = try {
                client.listObjects("$oldKey/", "/", null, 1, accessKeyId, secretAccessKey, config.region)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                return ApiResponse.Error(networkError(e))
            }
            val probeParsed = probe?.takeIf { it.isSuccessful }?.let { S3Xml.parseListResult(it.bodyText) }
            val isFolder = probeParsed != null && (probeParsed.objects.isNotEmpty() || probeParsed.commonPrefixes.isNotEmpty())

            if (!isFolder) {
                val copyResult = try {
                    client.copyObject(oldKey, newKey, accessKeyId, secretAccessKey, config.region)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    return ApiResponse.Error(networkError(e))
                } ?: return ApiResponse.Error(badEndpoint())
                if (!copyResult.isSuccessful) return ApiResponse.Error(statusError(copyResult))
                client.deleteObject(oldKey, accessKeyId, secretAccessKey, config.region)
                return ApiResponse.Success(StorageNode(ref = StorageRef(path = normalizedTarget), name = normalizedTarget.substringAfterLast('/'), isDirectory = false))
            }

            val keys = try {
                collectKeysUnder("$oldKey/", accessKeyId, secretAccessKey)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                return ApiResponse.Error(networkError(e))
            }
            var failures = 0
            keys.forEach { key ->
                val newChildKey = newKey + key.removePrefix(oldKey)
                val copyResult = try {
                    client.copyObject(key, newChildKey, accessKeyId, secretAccessKey, config.region)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    null
                }
                if (copyResult?.isSuccessful != true) {
                    failures++
                } else {
                    client.deleteObject(key, accessKeyId, secretAccessKey, config.region)
                }
            }
            return if (failures == 0) {
                // The caller already knows this was a folder; this result is only used for success/failure,
                // the listing is re-fetched for display afterward.
                ApiResponse.Success(StorageNode(ref = StorageRef(path = normalizedTarget), name = normalizedTarget.substringAfterLast('/'), isDirectory = true))
            } else {
                ApiResponse.Error(ProviderError("rename_partial_failure", "$failures of ${keys.size} items couldn't be moved."))
            }
        }

        override suspend fun delete(path: String, recursive: Boolean): ApiResponse<Unit> {
            val normalizedPath = path.trim('/')
            val (accessKeyId, secretAccessKey) = credentialsOrError() ?: return ApiResponse.Error(missingCredentials())
            val key = fullKey(normalizedPath)

            val probe = try {
                client.listObjects("$key/", "/", null, 2, accessKeyId, secretAccessKey, config.region)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                return ApiResponse.Error(networkError(e))
            }
            val probeParsed = probe?.takeIf { it.isSuccessful }?.let { S3Xml.parseListResult(it.bodyText) }
            val isFolder = probeParsed != null && (probeParsed.objects.isNotEmpty() || probeParsed.commonPrefixes.isNotEmpty())

            if (isFolder) {
                val realChildren = probeParsed.objects.any { it.key != "$key/" } || probeParsed.commonPrefixes.isNotEmpty()
                if (!recursive && realChildren) return ApiResponse.Error(ProviderError("directory_not_empty", "The folder is not empty."))
                val keys = try {
                    collectKeysUnder("$key/", accessKeyId, secretAccessKey)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    return ApiResponse.Error(networkError(e))
                }
                var failed = 0
                keys.forEach {
                    val result = try {
                        client.deleteObject(it, accessKeyId, secretAccessKey, config.region)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        null
                    }
                    if (result?.isSuccessful != true) failed++
                }
                return if (failed == 0) ApiResponse.Success(Unit) else ApiResponse.Error(ProviderError("delete_partial_failure", "$failed item(s) couldn't be deleted."))
            }

            val result = try {
                client.deleteObject(key, accessKeyId, secretAccessKey, config.region)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                return ApiResponse.Error(networkError(e))
            } ?: return ApiResponse.Error(badEndpoint())
            return if (result.isSuccessful) ApiResponse.Success(Unit) else ApiResponse.Error(statusError(result))
        }
    }

    /** Every key under [prefix] (no delimiter: recurses through the whole subtree), paginated. */
    private suspend fun collectKeysUnder(prefix: String, accessKeyId: String, secretAccessKey: String): List<String> {
        val keys = mutableListOf<String>()
        var token: String? = null
        var pages = 0
        do {
            val page = client.listObjects(prefix, null, token, 1000, accessKeyId, secretAccessKey, config.region) ?: break
            if (!page.isSuccessful) break
            val parsed = S3Xml.parseListResult(page.bodyText) ?: break
            keys += parsed.objects.map { it.key }
            token = parsed.nextContinuationToken
            pages++
        } while (token != null && pages < MAX_RENAME_DELETE_PAGES)
        return keys
    }

    // --- Links ---

    override fun shareUrl(node: StorageNode): String? = presignedUrl(node)
    override fun rawContentUrl(node: StorageNode, attachment: Boolean): String? = presignedUrl(node)
    override fun thumbnailUrl(node: StorageNode): String? = null

    private fun presignedUrl(node: StorageNode): String? {
        if (node.isDirectory) return null
        val path = node.ref.path.trim('/').takeIf { it.isNotEmpty() } ?: return null
        val (accessKeyId, secretAccessKey) = credentialsOrError() ?: return null
        return client.presignedGetUrl(fullKey(path), accessKeyId, secretAccessKey, config.region, SHARE_LINK_EXPIRY_SECONDS)?.toString()
    }

    // --- Validation ---

    override suspend fun validate(): ProviderValidationResult {
        val creds = credentialsOrError()
        if (creds == null) {
            return ProviderValidationResult(
                listOf(
                    FieldValidation("endpoint", "Endpoint", ok = true, inconclusive = true, message = "Not checked: enter the Access Key ID and Secret Access Key first"),
                    FieldValidation("credentials", "Credentials", ok = false, message = "Nothing entered: add the Access Key ID and Secret Access Key")
                )
            )
        }
        val (accessKeyId, secretAccessKey) = creds
        val result = try {
            client.listObjects(config.prefix.trim('/'), "/", null, 1, accessKeyId, secretAccessKey, config.region)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return ProviderValidationResult(
                listOf(
                    FieldValidation("endpoint", "Endpoint", ok = false, message = e.message ?: "Couldn't reach it"),
                    FieldValidation("credentials", "Credentials", ok = false, inconclusive = true, message = "Not checked: the endpoint couldn't be reached")
                )
            )
        } ?: return ProviderValidationResult(listOf(FieldValidation("endpoint", "Endpoint", ok = false, message = "The endpoint/bucket address couldn't be resolved.")))

        return when {
            result.isSuccessful -> ProviderValidationResult(
                listOf(
                    FieldValidation("endpoint", "Endpoint", ok = true, message = "Reachable"),
                    FieldValidation("credentials", "Credentials", ok = true, message = "Accepted")
                )
            )
            result.code == 401 || result.code == 403 -> ProviderValidationResult(
                listOf(
                    FieldValidation("endpoint", "Endpoint", ok = true, message = "Reachable"),
                    FieldValidation("credentials", "Credentials", ok = false, message = "Rejected: ${S3Xml.parseErrorMessage(result.bodyText) ?: "HTTP ${result.code}"}")
                )
            )
            else -> ProviderValidationResult(
                listOf(FieldValidation("endpoint", "Endpoint", ok = false, message = S3Xml.parseErrorMessage(result.bodyText) ?: "HTTP ${result.code}"))
            )
        }
    }

    // --- Helpers ---

    private fun breadcrumbFor(path: String): List<StorageNode> {
        if (path.isEmpty()) return emptyList()
        var built = ""
        return path.split('/').map { segment ->
            built = if (built.isEmpty()) segment else "$built/$segment"
            StorageNode(ref = StorageRef(path = built), name = segment, isDirectory = true)
        }
    }

    private fun statusError(result: S3Result): ProviderError =
        ProviderError(code = "http_${result.code}", message = "Request failed: ${S3Xml.parseErrorMessage(result.bodyText) ?: "HTTP ${result.code}"}", httpStatus = result.code)

    private fun networkError(e: Exception): ProviderError = ProviderError("network_error", e.message ?: "Couldn't reach the server.")

    private fun badEndpoint(): ProviderError = ProviderError("bad_endpoint", "The endpoint/bucket address couldn't be resolved.")

    private fun missingCredentials(): ProviderError = ProviderError("credentials_missing", "Enter the Access Key ID and Secret Access Key for this host.")
}
