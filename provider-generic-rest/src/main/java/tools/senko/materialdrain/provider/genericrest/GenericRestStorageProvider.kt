package tools.senko.materialdrain.provider.genericrest

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import tools.senko.materialdrain.provider.api.AccountInfo
import tools.senko.materialdrain.provider.api.AccountOps
import tools.senko.materialdrain.provider.api.ApiResponse
import tools.senko.materialdrain.provider.api.AuthType
import tools.senko.materialdrain.provider.api.ZipInfo
import tools.senko.materialdrain.provider.api.ArchiveOps
import tools.senko.materialdrain.provider.api.ArchiveEntryDetails
import tools.senko.materialdrain.provider.api.ArchiveEntry
import tools.senko.materialdrain.provider.api.BrowseOps
import tools.senko.materialdrain.provider.api.Credentials
import tools.senko.materialdrain.provider.api.EndpointConfig
import tools.senko.materialdrain.provider.api.FieldValidation
import tools.senko.materialdrain.provider.api.FileList
import tools.senko.materialdrain.provider.api.FileListDetail
import tools.senko.materialdrain.provider.api.ListOps
import tools.senko.materialdrain.provider.api.FileListOps
import tools.senko.materialdrain.provider.api.FileStoreOps
import tools.senko.materialdrain.provider.api.GenericRestConfig
import tools.senko.materialdrain.provider.api.PasswordAuthMode
import tools.senko.materialdrain.provider.api.PasswordSignIn
import tools.senko.materialdrain.provider.api.ProviderCapability
import tools.senko.materialdrain.provider.api.ProviderError
import tools.senko.materialdrain.provider.api.ProviderKind
import tools.senko.materialdrain.provider.api.ProviderLog
import tools.senko.materialdrain.provider.api.ProviderValidationResult
import tools.senko.materialdrain.provider.api.SignInResult
import tools.senko.materialdrain.provider.api.StorageListing
import tools.senko.materialdrain.provider.api.StorageNode
import tools.senko.materialdrain.provider.api.StorageProvider
import tools.senko.materialdrain.provider.api.StorageRef
import java.io.OutputStream

private const val ENDPOINT_UPLOAD = "upload"
private const val ENDPOINT_DOWNLOAD = "download"
private const val ENDPOINT_DOWNLOAD_ARCHIVE = "download_archive"
private const val ENDPOINT_ARCHIVE_INFO = "archive_info"
private const val ENDPOINT_ARCHIVE_FILE = "archive_file"
private const val ENDPOINT_DELETE = "delete"
private const val ENDPOINT_FILE_INFO = "file_info"
private const val ENDPOINT_USER_INFO = "user_info"
private const val ENDPOINT_LIST = "list"
private const val ENDPOINT_BROWSE_LIST = "browse_list"
private const val ENDPOINT_BROWSE_UPLOAD = "browse_upload"
private const val ENDPOINT_BROWSE_DOWNLOAD = "browse_download"
private const val ENDPOINT_BROWSE_MKDIR = "browse_mkdir"
private const val ENDPOINT_BROWSE_RENAME = "browse_rename"
private const val ENDPOINT_BROWSE_DELETE = "browse_delete"
private const val ENDPOINT_BROWSE_THUMBNAIL = "browse_thumbnail"
private const val ENDPOINT_BROWSE_IMPORT = "browse_import"
private const val ENDPOINT_THUMBNAIL_ID = "thumbnail_id"
private const val ENDPOINT_RAW_ID = "raw_id"
private const val ENDPOINT_SHARE_ID = "share_id"
private const val ENDPOINT_SHARE_PATH = "share_path"
private const val ENDPOINT_USER_LISTS = "user_lists"
private const val ENDPOINT_LIST_INFO = "list_info"
private const val ENDPOINT_LIST_CREATE = "list_create"
private const val ENDPOINT_LIST_UPDATE = "list_update"
private const val ENDPOINT_LIST_DELETE = "list_delete"

/** An entry of an archive as a node; a file keeps where it is inside which archive, so it can be read later. */
private fun ArchiveEntry.toStorageNode(archivePath: String) = StorageNode(
    ref = StorageRef(path = path),
    name = name,
    isDirectory = isDirectory,
    size = size,
    richDetails = if (isDirectory) null else ArchiveEntryDetails(archivePath, path),
)

/** Values of a node's "is_directory" mapping that mean it's a folder: the config maps whatever its API calls it. */
private val DIRECTORY_VALUES = setOf("dir", "directory", "folder", "true")

/**
 * The long-tail case: a host described entirely by [config], no Kotlin written for it. Only supports
 * upload/download/delete/file_info/user_info/list — whichever of those the config actually declares an
 * endpoint for. There is no hierarchical browse support: a directory listing needs the app to know the
 * *shape* of a listing response (which arbitrary hosts describe too differently to map with plain field
 * paths), not just where to fetch it from. "list" is the flat exception: one endpoint, one array of items,
 * the same per-item field mapping [ENDPOINT_FILE_INFO] already uses - see [fileList].
 *
 * @param credentials what the user entered for this host; read on every request so changes apply at once
 * @param onLoginToken called with a new token after signing in, or null when a saved token stopped working
 */
class GenericRestStorageProvider(
    override val id: String,
    private val config: GenericRestConfig,
    private val credentials: () -> Credentials,
    private val onLoginToken: (String?) -> Unit = {}
) : StorageProvider, PasswordSignIn {

    private val client = GenericRestClient(config.baseUrl)
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    override val displayName: String = config.name
    override val kind: ProviderKind = ProviderKind.GENERIC_REST

    override val capabilities: Set<ProviderCapability> = buildSet {
        if (config.endpoints.containsKey(ENDPOINT_UPLOAD) || config.endpoints.containsKey(ENDPOINT_BROWSE_UPLOAD)) add(ProviderCapability.UPLOAD)
        if (config.endpoints.containsKey(ENDPOINT_DOWNLOAD) || config.endpoints.containsKey(ENDPOINT_BROWSE_DOWNLOAD)) add(ProviderCapability.DOWNLOAD)
        if (config.endpoints.containsKey(ENDPOINT_DELETE) || config.endpoints.containsKey(ENDPOINT_BROWSE_DELETE)) add(ProviderCapability.DELETE)
        if (config.endpoints.containsKey(ENDPOINT_FILE_INFO)) add(ProviderCapability.FILE_INFO)
        if (config.endpoints.containsKey(ENDPOINT_DOWNLOAD_ARCHIVE)) add(ProviderCapability.ARCHIVE_DOWNLOAD)
        if (config.endpoints.containsKey(ENDPOINT_ARCHIVE_INFO)) add(ProviderCapability.ARCHIVE_BROWSE)
        if (config.endpoints.containsKey(ENDPOINT_USER_INFO)) add(ProviderCapability.USER_QUOTA)
        if (config.endpoints.containsKey(ENDPOINT_LIST)) add(ProviderCapability.ENUMERATE)
        if (config.endpoints.containsKey(ENDPOINT_BROWSE_LIST)) add(ProviderCapability.BROWSE)
        if (config.endpoints.containsKey(ENDPOINT_BROWSE_MKDIR)) add(ProviderCapability.MKDIR)
        if (config.endpoints.containsKey(ENDPOINT_BROWSE_RENAME)) add(ProviderCapability.RENAME)
        if (config.endpoints.containsKey(ENDPOINT_USER_LISTS)) add(ProviderCapability.LISTS)
        if (config.endpoints.containsKey(ENDPOINT_RAW_ID) || config.endpoints.containsKey(ENDPOINT_BROWSE_DOWNLOAD)) add(ProviderCapability.SHARE_LINK)
    }

    /** Lists of files, when the config declares the user_lists endpoint. */
    override val lists: ListOps? = config.endpoints[ENDPOINT_USER_LISTS]?.let { listsOps(it) }

    /** Looking inside archives, when the config declares archive_info (the listing) and archive_file (one file). */
    override val archives: ArchiveOps? = config.endpoints[ENDPOINT_ARCHIVE_INFO]?.let { infoEndpoint ->
        object : ArchiveOps {
            override suspend fun list(archivePath: String, inside: String): ApiResponse<List<StorageNode>> {
                return reaching {
                    val result = buffered(infoEndpoint, mapOf("path" to archivePath.trim('/')))
                        ?: return ApiResponse.Error(ProviderError("bad_endpoint", "archive_info endpoint could not be resolved."))
                    return when (val parsed = ZipInfo.entriesIn(result.bodyText, inside)) {
                        is ApiResponse.Success -> ApiResponse.Success(parsed.data.map { it.toStorageNode(archivePath) })
                        is ApiResponse.Error -> ApiResponse.Error(parsed.error)
                    }
                }
            }

            override suspend fun read(
                archivePath: String,
                entryPath: String,
                outputStream: java.io.OutputStream,
                onProgress: (read: Long, total: Long?) -> Unit
            ): ApiResponse<Long> {
                return reaching {
                    val endpoint = config.endpoints[ENDPOINT_ARCHIVE_FILE]
                        ?: return ApiResponse.Error(ProviderError("not_supported", "This host has no archive_file endpoint configured."))
                    var totalCopied = 0L
                    val code = authorized(
                        { auth ->
                            client.executeDownload(
                                endpoint,
                                mapOf("path" to archivePath.trim('/'), "entry" to "/" + entryPath.trim('/')),
                                auth,
                                outputStream
                            ) { read, total ->
                                totalCopied = read
                                onProgress(read, total)
                            }
                        },
                        { it }
                    ) ?: return ApiResponse.Error(ProviderError("bad_endpoint", "archive_file endpoint could not be resolved."))
                    return if (code in 200..299) ApiResponse.Success(totalCopied)
                    else ApiResponse.Error(ProviderError("download_failed_status_$code", "Download failed: HTTP $code"))
                }
            }
        }
    }

    override val rootPath: String = config.browseRoot

    /**
     * Headers for media loaded straight from the host (thumbnails, previews): the same login as the requests, but only
     * for URLs under the host's base address, so nothing is sent to other sites.
     */
    override fun requestHeaders(url: String): Map<String, String> {
        if (!url.startsWith(config.baseUrl)) return emptyMap()
        val header = resolveAuthPlan().header ?: return emptyMap()
        return mapOf(header.first to header.second)
    }

    /** Folder browsing, only when the config declares a browse_list endpoint (see [browseOps]). */
    override val browse: BrowseOps? = config.endpoints[ENDPOINT_BROWSE_LIST]?.let { browseOps(it) }

    // --- Credentials ---

    /** The header to send, and whether it's the session from signing in (which can expire). */
    private data class ResolvedAuth(val header: AuthHeader, val fromSession: Boolean)

    private fun badResponse(category: String, message: String, body: String): ApiResponse.Error {
        ProviderLog.e(category, "$message Body: ${ProviderLog.snippet(body)}")
        return ApiResponse.Error(ProviderError("bad_response", message))
    }

    private fun resolveAuth(): ResolvedAuth = resolveAuthPlan().also { auth ->
        ProviderLog.d("Auth", "${config.name}: ${auth.header?.first ?: "no header"}, ${if (auth.fromSession) "session token" else "credentials"}")
    }

    /** Which credentials the requests send: none when the config says the host must not get them. */
    private fun credentialsPlan(creds: Credentials): AuthPlan =
        if (config.sendCredentials) AuthHeaders.plan(config.auth, creds) else AuthPlan.None

    private fun resolveAuthPlan(): ResolvedAuth = when (val plan = credentialsPlan(credentials())) {
        is AuthPlan.PasswordBasic -> ResolvedAuth("Authorization" to AuthHeaders.basic(plan.username, plan.password), fromSession = false)
        is AuthPlan.SignedIn -> ResolvedAuth(AuthHeaders.forKey(config.auth, plan.token), fromSession = true)
        is AuthPlan.ApiKey -> ResolvedAuth(AuthHeaders.forKey(config.auth, plan.key), fromSession = false)
        AuthPlan.None -> ResolvedAuth(null, fromSession = false)
    }

    /**
     * Runs [call] with credentials. A session the host no longer accepts (401) is dropped, so the host shows as
     * signed out, and the call is retried once with whatever is left (the API key, if there is one).
     */
    private suspend fun <R> authorized(call: suspend (AuthHeader) -> R, status: (R) -> Int?): R {
        val auth = resolveAuth()
        val result = call(auth.header)
        if (auth.fromSession && status(result) == 401) {
            onLoginToken(null)
            return call(resolveAuth().header)
        }
        return result
    }

    /**
     * Runs one operation, turning a request that fails on the way (the network gone, a connection or stream reset) into
     * an error to show. Every operation goes through this: the client throws for those, and an exception escaping an
     * operation would crash the app (as a delete sometimes did), since its callers only expect errors as results.
     */
    private inline fun <T> reaching(operation: () -> ApiResponse<T>): ApiResponse<T> = try {
        operation()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        ProviderLog.e("Http", "a request to '${config.name}' failed: ${e.message}", e)
        ApiResponse.Error(ProviderError("network_error", e.message ?: "The host couldn't be reached."))
    }

    /**
     * An upload's request failing (the network gone, the file unreadable), as an error to show rather than an exception:
     * like every other host's upload, this one only ever throws for a cancel.
     */
    private fun uploadFailure(e: Exception): ApiResponse.Error {
        ProviderLog.e("Upload", "the upload failed: ${e.message}", e)
        return ApiResponse.Error(ProviderError("network_error", e.message ?: "The host couldn't be reached."))
    }

    // --- Sign-in ---

    override suspend fun signIn(username: String, password: String, otp: String?): SignInResult {
        val passwordAuth = config.auth.passwordAuth
        val endpoint = passwordAuth?.login
        if (passwordAuth?.mode != PasswordAuthMode.LOGIN || endpoint == null) {
            return SignInResult.Failed("This host doesn't sign in with a username and password.")
        }
        val result = try {
            client.login(endpoint, username.trim(), password, otp?.filter(Char::isDigit))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return SignInResult.Failed(e.message ?: "The sign-in address couldn't be reached.")
        } ?: return SignInResult.Failed("The sign-in address couldn't be resolved.")
        return AuthHeaders.signInResult(endpoint, result.code, parseJson(result.bodyText), otpSent = !otp.isNullOrBlank())
    }

    override suspend fun signOut() {
        val logout = config.auth.passwordAuth?.logout
        val token = credentials().loginToken
        if (logout != null && token != null) {
            // Best effort: signing out locally must work even when the host can't be reached
            try {
                client.executeBuffered(logout, emptyMap(), AuthHeaders.forKey(config.auth, token))
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
            }
        }
        onLoginToken(null)
    }

    private suspend fun buffered(endpoint: EndpointConfig, placeholders: Map<String, String>): GenericRestResult? =
        authorized({ client.executeBuffered(endpoint, placeholders, it) }, { it?.code })

    // --- Operations ---

    override val account: AccountOps? = config.endpoints[ENDPOINT_USER_INFO]?.let { endpoint ->
        object : AccountOps {
            override suspend fun accountInfo(): ApiResponse<AccountInfo> {
                return reaching {
                    val result = buffered(endpoint, emptyMap())
                        ?: return ApiResponse.Error(ProviderError("bad_endpoint", "user_info endpoint could not be resolved."))
                    if (!result.isSuccessful) return ApiResponse.Error(statusError(result))
                    val body = parseJson(result.bodyText) ?: return ApiResponse.Error(ProviderError("bad_response", "user_info response was not valid JSON."))
                    return ApiResponse.Success(
                        AccountInfo(
                            username = mapField(endpoint, body, "username"),
                            quotaUsedBytes = mapField(endpoint, body, "quota_used")?.toLongOrNull(),
                            quotaTotalBytes = mapField(endpoint, body, "quota_total")?.toLongOrNull()
                        )
                    )
                }
            }
        }
    }

    override val fileList: FileListOps? = config.endpoints[ENDPOINT_LIST]?.let { endpoint ->
        object : FileListOps {
            override suspend fun list(): ApiResponse<StorageListing> {
                return reaching {
                    val result = buffered(endpoint, emptyMap())
                        ?: return ApiResponse.Error(ProviderError("bad_endpoint", "list endpoint could not be resolved."))
                    if (!result.isSuccessful) return ApiResponse.Error(statusError(result))
                    val body = parseJson(result.bodyText) ?: return badResponse("Files", "list response was not valid JSON.", result.bodyText)
                    val items = DotPath.resolveArray(body, endpoint.listPath)
                        ?: return badResponse("Files", "list response has no array at ${endpoint.listPath ?: "its root"}.", result.bodyText)
                    return ApiResponse.Success(
                        StorageListing(
                            breadcrumb = emptyList(),
                            children = items.map { item ->
                                StorageNode(
                                    ref = StorageRef(id = mapField(endpoint, item, "id")),
                                    name = mapField(endpoint, item, "name") ?: "",
                                    isDirectory = false,
                                    size = mapField(endpoint, item, "size")?.toLongOrNull(),
                                    createdAt = mapField(endpoint, item, "created"),
                                    modifiedAt = mapField(endpoint, item, "modified"),
                                    mimeType = mapField(endpoint, item, "mime_type")
                                )
                            },
                            canWrite = ProviderCapability.UPLOAD in capabilities,
                            canDelete = ProviderCapability.DELETE in capabilities
                        )
                    )
                }
            }
        }
    }

    override val fileStore = object : FileStoreOps {
        override suspend fun upload(
            fileName: String,
            fileUri: Uri,
            context: Context,
            onProgress: (sent: Long, total: Long?) -> Unit
        ): ApiResponse<StorageNode> {
            return reaching {
                val endpoint = config.endpoints[ENDPOINT_UPLOAD]
                    ?: return ApiResponse.Error(ProviderError("not_configured", "This host has no upload endpoint configured."))
                val result = try {
                    authorized(
                        { client.executeUpload(endpoint, mapOf("filename" to fileName), it, fileName, fileUri, context, onProgress) },
                        { it?.code }
                    )
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    return uploadFailure(e)
                } ?: return ApiResponse.Error(ProviderError("bad_endpoint", "upload endpoint could not be resolved."))
                if (!result.isSuccessful) return ApiResponse.Error(statusError(result))
                val body = parseJson(result.bodyText)
                val id = body?.let { mapField(endpoint, it, "id") }
                return ApiResponse.Success(
                    StorageNode(
                        ref = StorageRef(id = id),
                        name = body?.let { mapField(endpoint, it, "name") } ?: fileName,
                        isDirectory = false,
                        size = body?.let { mapField(endpoint, it, "size") }?.toLongOrNull(),
                        mimeType = body?.let { mapField(endpoint, it, "mime_type") }
                    )
                )
            }
        }

        override suspend fun download(
            ref: StorageRef,
            outputStream: OutputStream,
            onProgress: (read: Long, total: Long?) -> Unit
        ): ApiResponse<Long> {
            return reaching {
                val endpoint = config.endpoints[ENDPOINT_DOWNLOAD]
                    ?: return ApiResponse.Error(ProviderError("not_configured", "This host has no download endpoint configured."))
                val id = ref.id ?: return ApiResponse.Error(ProviderError("file_id_missing", "File ID is required to download a file."))
                var totalCopied = 0L
                val code = authorized(
                    { auth ->
                        client.executeDownload(endpoint, mapOf("id" to id), auth, outputStream) { read, total ->
                            totalCopied = read
                            onProgress(read, total)
                        }
                    },
                    { it }
                ) ?: return ApiResponse.Error(ProviderError("bad_endpoint", "download endpoint could not be resolved."))
                return if (code in 200..299) ApiResponse.Success(totalCopied) else ApiResponse.Error(ProviderError("download_failed_status_$code", "Download failed: HTTP $code"))
            }
        }

        // The download_archive endpoint takes the ids joined by commas as {ids}, e.g. "/file/{ids}"
        override suspend fun downloadArchive(
            refs: List<StorageRef>,
            outputStream: java.io.OutputStream,
            onProgress: (read: Long, total: Long?) -> Unit
        ): ApiResponse<Long> {
            return reaching {
                val endpoint = config.endpoints[ENDPOINT_DOWNLOAD_ARCHIVE]
                    ?: return ApiResponse.Error(ProviderError("not_supported", "This host can't download several files as one archive."))
                val ids = refs.map { it.id ?: return ApiResponse.Error(ProviderError("file_id_missing", "File ID is required to download a file.")) }
                var totalCopied = 0L
                val code = authorized(
                    { auth ->
                        client.executeDownload(endpoint, mapOf("ids" to ids.joinToString(",")), auth, outputStream) { read, total ->
                            totalCopied = read
                            onProgress(read, total)
                        }
                    },
                    { it }
                ) ?: return ApiResponse.Error(ProviderError("bad_endpoint", "download_archive endpoint could not be resolved."))
                return if (code in 200..299) ApiResponse.Success(totalCopied) else ApiResponse.Error(ProviderError("download_failed_status_$code", "Download failed: HTTP $code"))
            }
        }

        override suspend fun fileInfo(ref: StorageRef): ApiResponse<StorageNode> {
            return reaching {
                val endpoint = config.endpoints[ENDPOINT_FILE_INFO]
                    ?: return ApiResponse.Error(ProviderError("not_configured", "This host has no file_info endpoint configured."))
                val id = ref.id ?: return ApiResponse.Error(ProviderError("file_id_missing", "File ID is required."))
                val result = buffered(endpoint, mapOf("id" to id))
                    ?: return ApiResponse.Error(ProviderError("bad_endpoint", "file_info endpoint could not be resolved."))
                if (!result.isSuccessful) return ApiResponse.Error(statusError(result))
                val body = parseJson(result.bodyText) ?: return ApiResponse.Error(ProviderError("bad_response", "file_info response was not valid JSON."))
                return ApiResponse.Success(
                    StorageNode(
                        ref = StorageRef(id = id),
                        name = mapField(endpoint, body, "name") ?: id,
                        isDirectory = false,
                        size = mapField(endpoint, body, "size")?.toLongOrNull(),
                        mimeType = mapField(endpoint, body, "mime_type")
                    )
                )
            }
        }

        override suspend fun delete(ref: StorageRef): ApiResponse<Unit> {
            return reaching {
                val endpoint = config.endpoints[ENDPOINT_DELETE]
                    ?: return ApiResponse.Error(ProviderError("not_configured", "This host has no delete endpoint configured."))
                val id = ref.id ?: return ApiResponse.Error(ProviderError("file_id_missing", "File ID is required."))
                val result = buffered(endpoint, mapOf("id" to id))
                    ?: return ApiResponse.Error(ProviderError("bad_endpoint", "delete endpoint could not be resolved."))
                return if (result.isSuccessful) ApiResponse.Success(Unit) else ApiResponse.Error(statusError(result))
            }
        }
    }

    /**
     * The link to share a node: the config's share page when it has one, otherwise the node's own content URL, which
     * is the link a download uses without the download flag.
     */
    override fun shareUrl(node: StorageNode): String? {
        if (!node.isDirectory) {
            node.ref.id?.let { id ->
                config.endpoints[ENDPOINT_SHARE_ID]?.let { return client.resolveUrl(it, mapOf("id" to id))?.toString() }
            }
            if (node.ref.path.isNotBlank()) {
                config.endpoints[ENDPOINT_SHARE_PATH]?.let { return client.resolveUrl(it, mapOf("path" to node.ref.path.trim('/')))?.toString() }
            }
        }
        return rawContentUrl(node, attachment = false)
    }

    override fun thumbnailUrl(node: StorageNode): String? {
        if (node.isDirectory) return null
        node.ref.id?.let { id ->
            config.endpoints[ENDPOINT_THUMBNAIL_ID]?.let { return client.resolveUrl(it, mapOf("id" to id))?.toString() }
        }
        val endpoint = config.endpoints[ENDPOINT_BROWSE_THUMBNAIL] ?: return null
        if (node.ref.path.isBlank()) return null
        return client.resolveUrl(endpoint, mapOf("path" to node.ref.path.trim('/')))?.toString()
    }

    override fun rawContentUrl(node: StorageNode, attachment: Boolean): String? {
        if (node.isDirectory) return null
        node.ref.id?.let { id ->
            val endpoint = config.endpoints[ENDPOINT_RAW_ID] ?: return@let
            val text = client.resolveUrl(endpoint, mapOf("id" to id))?.toString() ?: return null
            return if (!attachment) text else text + (if ('?' in text) "&download" else "?download")
        }
        val endpoint = config.endpoints[ENDPOINT_BROWSE_DOWNLOAD] ?: return null
        if (node.ref.path.isBlank()) return null
        val url = client.resolveUrl(endpoint, mapOf("path" to node.ref.path.trim('/'))) ?: return null
        val text = url.toString()
        return if (!attachment) text else text + (if ('?' in text) "&attach" else "?attach")
    }

    // --- Test connection ---

    /**
     * Reachability of the base URL and each endpoint (HEAD only, no credentials), plus a real check of the saved
     * credentials (the session, a directly-sent password, or the API key) against the read-only user_info endpoint.
     * It never signs in: that's the explicit [signIn], which may need a two-factor code.
     */
    override suspend fun validate(): ProviderValidationResult {
        val fields = buildList {
            val baseCode = client.probe("", emptyMap())
            add(FieldValidation("base_url", "Base URL", baseCode != null, if (baseCode != null) "Reachable" else "Couldn't reach it"))
            add(checkCredentials())
            val placeholders = mapOf(
                "filename" to "test.txt", "id" to "test", "path" to "test", "target" to "test",
                "action" to "mkdir", "make_parents" to "", "recursive" to ""
            )
            config.endpoints.forEach { (key, endpoint) ->
                val code = client.probe(endpoint.path, placeholders)
                add(FieldValidation(key, key, code != null, if (code != null) "Reachable" else "Couldn't reach it"))
            }
        }
        return ProviderValidationResult(fields)
    }

    private suspend fun checkCredentials(): FieldValidation {
        val creds = credentials()
        val plan = credentialsPlan(creds)
        val label = when (plan) {
            is AuthPlan.SignedIn -> "Sign-in"
            is AuthPlan.PasswordBasic -> "Username & password"
            is AuthPlan.ApiKey -> "API key"
            AuthPlan.None -> "Credentials"
        }
        if (plan == AuthPlan.None) {
            return when {
                config.auth.type == AuthType.NONE && config.auth.passwordAuth == null ->
                    FieldValidation("credentials", label, ok = true, message = "This host doesn't use any")
                config.auth.passwordAuth?.mode == PasswordAuthMode.LOGIN ->
                    FieldValidation("credentials", label, ok = false, message = "Not signed in: sign in, or add an API key")
                else -> FieldValidation("credentials", label, ok = false, message = "Nothing entered: add an API key" +
                    if (config.auth.passwordAuth != null) " or a username and password" else "")
            }
        }

        return try {
            val auth = resolveAuth()
            val userInfo = config.endpoints[ENDPOINT_USER_INFO]
                ?: return FieldValidation(
                    "credentials", label, ok = true, inconclusive = true,
                    message = "Not checked: this config has no user_info endpoint to test them against"
                )
            val result = client.executeBuffered(userInfo, emptyMap(), auth.header)
                ?: return FieldValidation("credentials", label, ok = false, message = "The user_info endpoint couldn't be resolved")
            when {
                result.isSuccessful -> {
                    val username = parseJson(result.bodyText)?.let { mapField(userInfo, it, "username") }
                    FieldValidation("credentials", label, ok = true, message = username?.let { "Accepted, signed in as $it" } ?: "Accepted")
                }
                // An expired session: drop it so the host shows as signed out (the password isn't kept to renew it)
                auth.fromSession && result.code == 401 -> {
                    onLoginToken(null)
                    FieldValidation("credentials", label, ok = false, message = "Your session has expired, sign in again")
                }
                result.code == 401 || result.code == 403 -> FieldValidation("credentials", label, ok = false, message = "Rejected (HTTP ${result.code}): ${serverMessage(result)}")
                else -> FieldValidation("credentials", label, ok = false, message = "Unexpected answer: ${serverMessage(result)}")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            FieldValidation("credentials", label, ok = false, message = "Couldn't check: ${e.message ?: "the host couldn't be reached"}")
        }
    }

    // --- Folder browsing ---

    private fun browseOps(listEndpoint: EndpointConfig): BrowseOps = object : BrowseOps {
        override suspend fun list(path: String): ApiResponse<StorageListing> {
            return reaching {
                val result = buffered(listEndpoint, mapOf("path" to path.trim('/')))
                    ?: return ApiResponse.Error(ProviderError("bad_endpoint", "browse_list endpoint could not be resolved."))
                if (!result.isSuccessful) return ApiResponse.Error(statusError(result))
                val body = parseJson(result.bodyText) ?: return badResponse("Filesystem", "browse_list response was not valid JSON.", result.bodyText)
                val items = DotPath.resolveArray(body, listEndpoint.listPath)
                    ?: return badResponse("Filesystem", "browse_list response has no array at ${listEndpoint.listPath ?: "its root"}.", result.bodyText)
                return ApiResponse.Success(
                    StorageListing(
                        breadcrumb = DotPath.resolveArray(body, listEndpoint.breadcrumbPath).orEmpty().map { nodeFrom(listEndpoint, it) },
                        children = items.map { nodeFrom(listEndpoint, it) },
                        canWrite = listingFlag(listEndpoint, body, "can_write") ?: (ProviderCapability.UPLOAD in capabilities),
                        canDelete = listingFlag(listEndpoint, body, "can_delete") ?: (ProviderCapability.DELETE in capabilities)
                    )
                )
            }
        }

        override suspend fun upload(
            path: String,
            fileUri: Uri,
            context: Context,
            makeParents: Boolean,
            onProgress: (sent: Long, total: Long?) -> Unit
        ): ApiResponse<StorageNode> {
            return reaching {
                val endpoint = config.endpoints[ENDPOINT_BROWSE_UPLOAD]
                    ?: return ApiResponse.Error(ProviderError("not_configured", "This host has no browse upload endpoint configured."))
                val target = path.trim('/')
                val name = target.substringAfterLast('/')
                val placeholders = mapOf("path" to target, "make_parents" to makeParentsValue(makeParents))
                val result = try {
                    authorized(
                        { client.executeUpload(endpoint, placeholders, it, name, fileUri, context, onProgress) },
                        { it?.code }
                    )
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    return uploadFailure(e)
                } ?: return ApiResponse.Error(ProviderError("bad_endpoint", "browse upload endpoint could not be resolved."))
                if (!result.isSuccessful) return ApiResponse.Error(statusError(result))
                val body = parseJson(result.bodyText)
                return ApiResponse.Success(body?.let { nodeFrom(endpoint, it) } ?: StorageNode(ref = StorageRef(path = target), name = name, isDirectory = false))
            }
        }

        override suspend fun download(
            path: String,
            outputStream: OutputStream,
            onProgress: (read: Long, total: Long?) -> Unit
        ): ApiResponse<Long> {
            return reaching {
                val endpoint = config.endpoints[ENDPOINT_BROWSE_DOWNLOAD]
                    ?: return ApiResponse.Error(ProviderError("not_configured", "This host has no browse download endpoint configured."))
                var totalCopied = 0L
                val code = authorized(
                    { auth ->
                        client.executeDownload(endpoint, mapOf("path" to path.trim('/')), auth, outputStream) { read, total ->
                            totalCopied = read
                            onProgress(read, total)
                        }
                    },
                    { it }
                ) ?: return ApiResponse.Error(ProviderError("bad_endpoint", "browse download endpoint could not be resolved."))
                return if (code in 200..299) ApiResponse.Success(totalCopied)
                else ApiResponse.Error(ProviderError("download_failed_status_$code", "Download failed: HTTP $code"))
            }
        }

        override suspend fun createDirectory(path: String, makeParents: Boolean): ApiResponse<Unit> {
            return reaching {
                val endpoint = config.endpoints[ENDPOINT_BROWSE_MKDIR]
                    ?: return ApiResponse.Error(ProviderError("not_configured", "This host has no mkdir endpoint configured."))
                val placeholders = mapOf("path" to path.trim('/'), "action" to if (makeParents) "mkdirall" else "mkdir")
                val result = buffered(endpoint, placeholders)
                    ?: return ApiResponse.Error(ProviderError("bad_endpoint", "mkdir endpoint could not be resolved."))
                return if (result.isSuccessful) ApiResponse.Success(Unit) else ApiResponse.Error(statusError(result))
            }
        }

        override suspend fun rename(path: String, targetPath: String, makeParents: Boolean): ApiResponse<StorageNode> {
            return reaching {
                val endpoint = config.endpoints[ENDPOINT_BROWSE_RENAME]
                    ?: return ApiResponse.Error(ProviderError("not_configured", "This host has no rename endpoint configured."))
                val target = targetPath.trim('/')
                val placeholders = mapOf("path" to path.trim('/'), "target" to target, "make_parents" to makeParentsValue(makeParents))
                val result = buffered(endpoint, placeholders)
                    ?: return ApiResponse.Error(ProviderError("bad_endpoint", "rename endpoint could not be resolved."))
                if (!result.isSuccessful) return ApiResponse.Error(statusError(result))
                val body = parseJson(result.bodyText)
                return ApiResponse.Success(
                    body?.let { nodeFrom(endpoint, it) } ?: StorageNode(ref = StorageRef(path = target), name = target.substringAfterLast('/'), isDirectory = false)
                )
            }
        }

        override suspend fun importFiles(path: String, fileIds: List<String>): ApiResponse<Unit> {
            return reaching {
                val endpoint = config.endpoints[ENDPOINT_BROWSE_IMPORT]
                    ?: return ApiResponse.Error(ProviderError("not_supported", "This host can't import files into a folder."))
                val placeholders = mapOf("path" to path.trim('/'), "files_json" to JsonArray(fileIds.map { JsonPrimitive(it) }).toString())
                val result = buffered(endpoint, placeholders)
                    ?: return ApiResponse.Error(ProviderError("bad_endpoint", "import endpoint could not be resolved."))
                return if (result.isSuccessful) ApiResponse.Success(Unit) else ApiResponse.Error(statusError(result))
            }
        }

        override suspend fun delete(path: String, recursive: Boolean): ApiResponse<Unit> {
            return reaching {
                val endpoint = config.endpoints[ENDPOINT_BROWSE_DELETE]
                    ?: return ApiResponse.Error(ProviderError("not_configured", "This host has no browse delete endpoint configured."))
                val placeholders = mapOf("path" to path.trim('/'), "recursive" to makeParentsValue(recursive))
                val result = buffered(endpoint, placeholders)
                    ?: return ApiResponse.Error(ProviderError("bad_endpoint", "browse delete endpoint could not be resolved."))
                return if (result.isSuccessful) ApiResponse.Success(Unit) else ApiResponse.Error(statusError(result))
            }
        }
    }

    // --- Lists of files ---

    /** The JSON values a list request sends: the title as a string, the files as [{"id": ...}]. */
    private fun listJsonPlaceholders(title: String, fileIds: List<String>): Map<String, String> = mapOf(
        "title_json" to JsonPrimitive(title.trim().ifEmpty { "Pixeldrain List" }).toString(),
        "files_json" to JsonArray(fileIds.map { JsonObject(mapOf("id" to JsonPrimitive(it))) }).toString()
    )

    private fun listsOps(overview: EndpointConfig): ListOps = object : ListOps {
        override suspend fun lists(): ApiResponse<List<FileList>> {
            return reaching {
                val result = buffered(overview, emptyMap()) ?: return ApiResponse.Error(ProviderError("bad_endpoint", "user_lists endpoint could not be resolved."))
                if (!result.isSuccessful) return ApiResponse.Error(statusError(result))
                val body = parseJson(result.bodyText) ?: return badResponse("Lists", "user_lists response was not valid JSON.", result.bodyText)
                val items = DotPath.resolveArray(body, overview.listPath)
                    ?: return badResponse("Lists", "user_lists response has no array at ${overview.listPath ?: "its root"}.", result.bodyText)
                return ApiResponse.Success(
                    items.map { item ->
                        FileList(
                            id = mapField(overview, item, "id").orEmpty(),
                            title = mapField(overview, item, "title").orEmpty(),
                            fileCount = mapField(overview, item, "file_count")?.toIntOrNull() ?: 0,
                            canEdit = mapField(overview, item, "can_edit")?.lowercase() == "true"
                        )
                    }
                )
            }
        }

        override suspend fun listContents(id: String): ApiResponse<FileListDetail> {
            return reaching {
                val endpoint = config.endpoints[ENDPOINT_LIST_INFO]
                    ?: return ApiResponse.Error(ProviderError("not_configured", "This host has no list_info endpoint configured."))
                val result = buffered(endpoint, mapOf("list_id" to id))
                    ?: return ApiResponse.Error(ProviderError("bad_endpoint", "list_info endpoint could not be resolved."))
                if (!result.isSuccessful) return ApiResponse.Error(statusError(result))
                val body = parseJson(result.bodyText) ?: return badResponse("Lists", "list_info response was not valid JSON.", result.bodyText)
                val files = DotPath.resolveArray(body, endpoint.listPath)
                    ?: return badResponse("Lists", "list_info response has no array at ${endpoint.listPath ?: "its root"}.", result.bodyText)
                return ApiResponse.Success(
                    FileListDetail(
                        id = id,
                        title = endpoint.listingMap["title"]?.let { DotPath.resolveString(body, it) }.orEmpty(),
                        canEdit = endpoint.listingMap["can_edit"]?.let { DotPath.resolveString(body, it)?.lowercase() == "true" } ?: false,
                        files = files.map { nodeFrom(endpoint, it) }
                    )
                )
            }
        }

        override suspend fun create(title: String, fileIds: List<String>): ApiResponse<String> {
            return reaching {
                val endpoint = config.endpoints[ENDPOINT_LIST_CREATE]
                    ?: return ApiResponse.Error(ProviderError("not_configured", "This host has no list_create endpoint configured."))
                val result = buffered(endpoint, listJsonPlaceholders(title, fileIds))
                    ?: return ApiResponse.Error(ProviderError("bad_endpoint", "list_create endpoint could not be resolved."))
                if (!result.isSuccessful) return ApiResponse.Error(statusError(result))
                val id = parseJson(result.bodyText)?.let { mapField(endpoint, it, "id") }
                    ?: return ApiResponse.Error(ProviderError("bad_response", "The new list has no id in the response."))
                return ApiResponse.Success(id)
            }
        }

        override suspend fun update(id: String, title: String, fileIds: List<String>): ApiResponse<Unit> {
            return reaching {
                val endpoint = config.endpoints[ENDPOINT_LIST_UPDATE]
                    ?: return ApiResponse.Error(ProviderError("not_configured", "This host has no list_update endpoint configured."))
                val placeholders = listJsonPlaceholders(title, fileIds) + ("list_id" to id)
                val result = buffered(endpoint, placeholders)
                    ?: return ApiResponse.Error(ProviderError("bad_endpoint", "list_update endpoint could not be resolved."))
                return if (result.isSuccessful) ApiResponse.Success(Unit) else ApiResponse.Error(statusError(result))
            }
        }

        override suspend fun delete(id: String): ApiResponse<Unit> {
            return reaching {
                val endpoint = config.endpoints[ENDPOINT_LIST_DELETE]
                    ?: return ApiResponse.Error(ProviderError("not_configured", "This host has no list_delete endpoint configured."))
                val result = buffered(endpoint, mapOf("list_id" to id))
                    ?: return ApiResponse.Error(ProviderError("bad_endpoint", "list_delete endpoint could not be resolved."))
                return if (result.isSuccessful) ApiResponse.Success(Unit) else ApiResponse.Error(statusError(result))
            }
        }
    }

    /** A node from one item of a browse response, mapped by the endpoint's response_map. */
    private fun nodeFrom(endpoint: EndpointConfig, item: JsonElement): StorageNode {
        val path = mapField(endpoint, item, "path").orEmpty()
        return StorageNode(
            ref = StorageRef(path = path, id = mapField(endpoint, item, "id")),
            name = mapField(endpoint, item, "name") ?: path.substringAfterLast('/'),
            isDirectory = mapField(endpoint, item, "is_directory")?.lowercase()?.let { it in DIRECTORY_VALUES } ?: false,
            size = mapField(endpoint, item, "size")?.toLongOrNull(),
            createdAt = mapField(endpoint, item, "created"),
            modifiedAt = mapField(endpoint, item, "modified"),
            mimeType = mapField(endpoint, item, "mime_type")
        )
    }

    /** A boolean of the listing as a whole (listing_map), or null when the config doesn't map it. */
    private fun listingFlag(endpoint: EndpointConfig, body: JsonElement, key: String): Boolean? =
        endpoint.listingMap[key]?.let { DotPath.resolveString(body, it)?.lowercase() == "true" }

    /** "true" for a flag that is set, "" otherwise, so an optional query or form field is left out. */
    private fun makeParentsValue(flag: Boolean): String = if (flag) "true" else ""

    // --- Helpers ---

    /** The server's own error message when it sent one ({"message": ...}), otherwise the status code. */
    private fun serverMessage(result: GenericRestResult): String =
        DotPath.errorMessage(parseJson(result.bodyText)) ?: "HTTP ${result.code}"

    private fun statusError(result: GenericRestResult): ProviderError =
        ProviderError(code = "http_${result.code}", message = "Request failed: ${serverMessage(result)}", httpStatus = result.code)

    private fun parseJson(text: String): JsonElement? = try {
        json.parseToJsonElement(text)
    } catch (_: Exception) {
        null
    }

    private fun mapField(endpoint: EndpointConfig, body: JsonElement, field: String): String? =
        endpoint.responseMap[field]?.let { path -> DotPath.resolveString(body, path) }
}
