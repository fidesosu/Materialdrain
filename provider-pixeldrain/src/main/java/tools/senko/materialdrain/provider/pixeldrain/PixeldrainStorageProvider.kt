package tools.senko.materialdrain.provider.pixeldrain

import android.content.Context
import android.net.Uri
import io.ktor.http.encodeURLPathPart
import okhttp3.Request
import tools.senko.materialdrain.provider.api.AccountOps
import tools.senko.materialdrain.provider.api.ApiResponse
import tools.senko.materialdrain.provider.api.BrowseOps
import tools.senko.materialdrain.provider.api.FieldValidation
import tools.senko.materialdrain.provider.api.FileList
import tools.senko.materialdrain.provider.api.FileListDetail
import tools.senko.materialdrain.provider.api.ListOps
import tools.senko.materialdrain.provider.api.FileListOps
import tools.senko.materialdrain.provider.api.FileStoreOps
import tools.senko.materialdrain.provider.api.PixeldrainRichDetails
import tools.senko.materialdrain.provider.api.ProviderCapability
import tools.senko.materialdrain.provider.api.ProviderError
import tools.senko.materialdrain.provider.api.ProviderKind
import tools.senko.materialdrain.provider.api.ProviderValidationResult
import tools.senko.materialdrain.provider.api.StorageListing
import tools.senko.materialdrain.provider.api.ArchiveEntryDetails
import tools.senko.materialdrain.provider.api.ArchiveOps
import tools.senko.materialdrain.provider.api.ZipInfo
import tools.senko.materialdrain.provider.api.StorageNode
import tools.senko.materialdrain.provider.api.StorageProvider
import tools.senko.materialdrain.provider.api.StorageRef
import tools.senko.materialdrain.provider.pixeldrain.internal.FileInfoResponse
import tools.senko.materialdrain.provider.pixeldrain.internal.FileUploadResponse
import tools.senko.materialdrain.provider.pixeldrain.internal.FilesystemEntry
import tools.senko.materialdrain.provider.pixeldrain.internal.PIXELDRAIN_HOST
import tools.senko.materialdrain.provider.pixeldrain.internal.PixeldrainCoreApi
import tools.senko.materialdrain.provider.pixeldrain.internal.PixeldrainCoreApiImpl
import tools.senko.materialdrain.provider.pixeldrain.internal.PixeldrainFilesystemApi
import tools.senko.materialdrain.provider.pixeldrain.internal.PixeldrainFilesystemApiImpl
import tools.senko.materialdrain.provider.pixeldrain.internal.PixeldrainHttpClient
import tools.senko.materialdrain.provider.pixeldrain.internal.PixeldrainUserApi
import tools.senko.materialdrain.provider.pixeldrain.internal.PixeldrainUserApiImpl
import java.io.OutputStream
import tools.senko.materialdrain.provider.pixeldrain.internal.ApiResponse as PdResult

private fun FilesystemEntry.toStorageNode(): StorageNode = StorageNode(
    ref = StorageRef(path = path, id = id),
    name = name,
    isDirectory = type == "dir",
    size = fileSize.takeIf { type == "file" },
    createdAt = created,
    modifiedAt = modified,
    mimeType = mimeType ?: fileType.ifBlank { null },
    richDetails = if (type == "file") {
        PixeldrainRichDetails(
            fileId = id ?: path,
            modeString = modeString,
            sha256 = sha256Sum.ifBlank { null },
            views = views,
            bandwidthUsed = bandwidthUsed,
            downloads = downloads,
            availability = availability,
            availabilityMessage = availabilityMessage,
            abuseType = abuseType,
            canEdit = canEdit,
            canDownload = canDownload,
            showAds = showAds,
            allowVideoPlayer = allowVideoPlayer,
            thumbnailHref = thumbnailHref,
            bandwidthUsedPaid = bandwidthUsedPaid,
            deleteAfterDate = deleteAfterDate,
            deleteAfterDownloads = deleteAfterDownloads,
            abuseReporterName = abuseReporterName,
            downloadSpeedLimit = downloadSpeedLimit
        )
    } else null
)

/** The file-info model the detail card and downloads use, rebuilt from a Pixeldrain node. Null for folders. */
fun StorageNode.toPixeldrainFileInfo(): FileInfoResponse? {
    if (isDirectory) return null
    val details = richDetails as? PixeldrainRichDetails
    return FileInfoResponse(
        id = details?.fileId ?: ref.id ?: ref.path,
        name = name,
        size = size ?: 0L,
        views = details?.views,
        bandwidthUsed = details?.bandwidthUsed,
        bandwidthUsedPaid = details?.bandwidthUsedPaid,
        downloads = details?.downloads,
        dateUpload = createdAt.orEmpty(),
        dateLastView = details?.dateLastView,
        mimeType = mimeType,
        thumbnailHref = details?.thumbnailHref,
        hashSha256 = details?.sha256,
        canEdit = details?.canEdit,
        deleteAfterDate = details?.deleteAfterDate,
        deleteAfterDownloads = details?.deleteAfterDownloads,
        availability = details?.availability,
        availabilityMessage = details?.availabilityMessage,
        abuseType = details?.abuseType,
        abuseReporterName = details?.abuseReporterName,
        canDownload = details?.canDownload,
        showAds = details?.showAds,
        allowVideoPlayer = details?.allowVideoPlayer,
        downloadSpeedLimit = details?.downloadSpeedLimit
    )
}

private fun FileInfoResponse.toStorageNode(): StorageNode = StorageNode(
    ref = StorageRef(id = id),
    name = name,
    isDirectory = false,
    size = size,
    createdAt = dateUpload,
    mimeType = mimeType,
    richDetails = PixeldrainRichDetails(
        fileId = id,
        sha256 = hashSha256,
        views = views,
        bandwidthUsed = bandwidthUsed,
        downloads = downloads,
        availability = availability,
        availabilityMessage = availabilityMessage,
        abuseType = abuseType,
        canEdit = canEdit,
        canDownload = canDownload,
        showAds = showAds,
        allowVideoPlayer = allowVideoPlayer,
        thumbnailHref = thumbnailHref,
        bandwidthUsedPaid = bandwidthUsedPaid,
        deleteAfterDate = deleteAfterDate,
        deleteAfterDownloads = deleteAfterDownloads,
        abuseReporterName = abuseReporterName,
        downloadSpeedLimit = downloadSpeedLimit,
        dateLastView = dateLastView
    )
)

private fun FileUploadResponse.toProviderError(): ProviderError =
    ProviderError(code = value ?: "error", message = message ?: "Request failed")

private fun <T, R> PdResult<T>.map(transform: (T) -> R): ApiResponse<R> = when (this) {
    is PdResult.Success -> ApiResponse.Success(transform(data))
    is PdResult.Error -> ApiResponse.Error(errorDetails.toProviderError())
}

private fun FileUploadResponse.toUnitResponse(): ApiResponse<Unit> =
    if (success) ApiResponse.Success(Unit) else ApiResponse.Error(toProviderError())

/**
 * Adapts the existing (unmodified) Pixeldrain API implementations to [StorageProvider]. Pixeldrain stays
 * the app's hardcoded default; this class exists so the rest of the app can eventually address it through
 * the same interface as any other configured host, without touching the Pixeldrain request/response code.
 *
 * Login, and the file-list ("Lists") feature, have no equivalent on any other kind of host, so they are
 * deliberately not part of [StorageProvider] at all: callers that need them use [PixeldrainCoreApi] /
 * [tools.senko.materialdrain.provider.pixeldrain.internal.PixeldrainUserApi] directly, same as before this
 * module split.
 */
class PixeldrainStorageProvider(
    private val apiKeyProvider: () -> String,
    val httpClient: PixeldrainHttpClient = PixeldrainHttpClient(),
    val coreApi: PixeldrainCoreApi = PixeldrainCoreApiImpl(httpClient),
    val filesystemApi: PixeldrainFilesystemApi = PixeldrainFilesystemApiImpl(httpClient),
    val userApi: PixeldrainUserApi = PixeldrainUserApiImpl(httpClient)
) : StorageProvider {

    override val id: String = "pixeldrain-default"
    override val displayName: String = "Pixeldrain"
    override val kind: ProviderKind = ProviderKind.PIXELDRAIN
    override val rootPath: String = "me"

    /** The client backing this provider, exposed so other things that talk HTTP (media playback) share its connection pool. */
    val okHttpClient get() = httpClient.okHttpClient

    override val capabilities: Set<ProviderCapability> = setOf(
        ProviderCapability.UPLOAD, ProviderCapability.DOWNLOAD, ProviderCapability.DELETE, ProviderCapability.FILE_INFO,
        ProviderCapability.BROWSE, ProviderCapability.MKDIR, ProviderCapability.RENAME,
        ProviderCapability.SHARE_LINK, ProviderCapability.RICH_FILE_STATS, ProviderCapability.LISTS, ProviderCapability.ENUMERATE,
        ProviderCapability.ARCHIVE_DOWNLOAD, ProviderCapability.ARCHIVE_BROWSE
        // Deliberately absent: SEARCH and PERMISSIONS (the pixeldrain API supports both, but nothing in this
        // app calls them yet, so claiming the capability here would be a promise the UI can't keep) and
        // USER_QUOTA (no account-info endpoint is wired up).
    )

    // Listing goes through the native filesystem/user APIs instead (see the class doc comment); nothing here
    // routes through the generic StorageProvider surface for it yet.
    override val account: AccountOps? = null

    override val fileList: FileListOps? = object : FileListOps {
        override suspend fun list(): ApiResponse<StorageListing> =
            userApi.getUserFiles(apiKeyProvider()).map { response ->
                StorageListing(
                    breadcrumb = emptyList(),
                    children = response.files.map { it.toStorageNode() },
                    canWrite = true,
                    canDelete = true
                )
            }
    }

    override val lists: ListOps = object : ListOps {
        override suspend fun lists(): ApiResponse<List<FileList>> =
            userApi.getUserLists(apiKeyProvider()).map { response ->
                response.lists.map { FileList(it.id, it.title, it.fileCount, it.canEdit) }
            }

        override suspend fun listContents(id: String): ApiResponse<FileListDetail> =
            coreApi.getList(id, apiKeyProvider().ifBlank { null }).map { info ->
                FileListDetail(info.id, info.title, info.canEdit, info.files.map { it.toStorageNode() })
            }

        override suspend fun create(title: String, fileIds: List<String>): ApiResponse<String> =
            when (val result = coreApi.createList(apiKeyProvider(), title, fileIds)) {
                is PdResult.Success -> result.data.id?.let { ApiResponse.Success(it) }
                    ?: ApiResponse.Error(ProviderError("missing_id", "The new list has no id."))
                is PdResult.Error -> ApiResponse.Error(result.errorDetails.toProviderError())
            }

        override suspend fun update(id: String, title: String, fileIds: List<String>): ApiResponse<Unit> =
            coreApi.updateList(apiKeyProvider(), id, title, fileIds).let { result ->
                when (result) {
                    is PdResult.Success -> result.data.toUnitResponse()
                    is PdResult.Error -> ApiResponse.Error(result.errorDetails.toProviderError())
                }
            }

        override suspend fun delete(id: String): ApiResponse<Unit> =
            coreApi.deleteList(apiKeyProvider(), id).let { result ->
                when (result) {
                    is PdResult.Success -> result.data.toUnitResponse()
                    is PdResult.Error -> ApiResponse.Error(result.errorDetails.toProviderError())
                }
            }
    }

    /** Inside archives with ?zip_info and ?zip_file: the archive itself is never downloaded just to look at it. */
    override val archives: ArchiveOps = object : ArchiveOps {
        override suspend fun list(archivePath: String, inside: String): ApiResponse<List<StorageNode>> {
            val body = when (val raw = filesystemApi.getZipInfo(apiKeyProvider(), archivePath)) {
                is PdResult.Success -> raw.data
                else -> return ApiResponse.Error(ProviderError("network_error", "The archive couldn't be read."))
            }
            return when (val parsed = ZipInfo.entriesIn(body, inside)) {
                is ApiResponse.Success -> ApiResponse.Success(
                    parsed.data.map {
                        StorageNode(
                            ref = StorageRef(path = it.path),
                            name = it.name,
                            isDirectory = it.isDirectory,
                            size = it.size,
                            richDetails = if (it.isDirectory) null else ArchiveEntryDetails(archivePath, it.path),
                        )
                    }
                )
                is ApiResponse.Error -> ApiResponse.Error(parsed.error)
            }
        }

        override suspend fun read(
            archivePath: String,
            entryPath: String,
            outputStream: OutputStream,
            onProgress: (read: Long, total: Long?) -> Unit
        ): ApiResponse<Long> =
            filesystemApi.downloadZipEntry(apiKeyProvider(), archivePath, "/" + entryPath.trimStart('/'), outputStream, onProgress).map { it }
    }

    override val fileStore = object : FileStoreOps {
        override suspend fun upload(
            fileName: String,
            fileUri: Uri,
            context: Context,
            onProgress: (sent: Long, total: Long?) -> Unit
        ): ApiResponse<StorageNode> {
            val result = coreApi.uploadFileFromUri(apiKeyProvider(), fileName, fileUri, context, onProgress)
            return if (result.success && result.id != null) {
                ApiResponse.Success(StorageNode(ref = StorageRef(id = result.id), name = fileName, isDirectory = false))
            } else {
                ApiResponse.Error(result.toProviderError())
            }
        }

        override suspend fun download(
            ref: StorageRef,
            outputStream: OutputStream,
            onProgress: (read: Long, total: Long?) -> Unit
        ): ApiResponse<Long> {
            val fileId = ref.id ?: return ApiResponse.Error(ProviderError("file_id_missing", "File ID is required to download a file."))
            return coreApi.downloadFileToOutputStream(fileId, outputStream, apiKeyProvider(), onProgress).map { it }
        }

        // GET /file/{id1},{id2}: Pixeldrain serves the files as one zip archive
        override suspend fun downloadArchive(
            refs: List<StorageRef>,
            outputStream: OutputStream,
            onProgress: (read: Long, total: Long?) -> Unit
        ): ApiResponse<Long> {
            val ids = refs.map { it.id ?: return ApiResponse.Error(ProviderError("file_id_missing", "File ID is required to download a file.")) }
            return coreApi.downloadFileToOutputStream(ids.joinToString(","), outputStream, apiKeyProvider(), onProgress).map { it }
        }

        override suspend fun fileInfo(ref: StorageRef): ApiResponse<StorageNode> {
            val fileId = ref.id ?: return ApiResponse.Error(ProviderError("file_id_missing", "File ID is required."))
            return coreApi.getFileInfo(fileId, apiKeyProvider().ifBlank { null }).map { it.toStorageNode() }
        }

        override suspend fun delete(ref: StorageRef): ApiResponse<Unit> {
            val fileId = ref.id ?: return ApiResponse.Error(ProviderError("file_id_missing", "File ID is required."))
            return coreApi.deleteFile(apiKeyProvider(), fileId).map { }
        }
    }

    override val browse = object : BrowseOps {
        override suspend fun importFiles(path: String, fileIds: List<String>): ApiResponse<Unit> =
            filesystemApi.importFiles(apiKeyProvider(), path, fileIds).map { }

        override suspend fun list(path: String): ApiResponse<StorageListing> =
            filesystemApi.getFilesystemPath(apiKeyProvider(), path).map { listing ->
                StorageListing(
                    breadcrumb = listing.path.map { it.toStorageNode() },
                    children = listing.children.map { it.toStorageNode() },
                    canWrite = listing.permissions.write,
                    canDelete = listing.permissions.delete
                )
            }

        override suspend fun upload(
            path: String,
            fileUri: Uri,
            context: Context,
            makeParents: Boolean,
            onProgress: (sent: Long, total: Long?) -> Unit
        ): ApiResponse<StorageNode> =
            filesystemApi.uploadFileFromUri(apiKeyProvider(), path, fileUri, context, makeParents, onProgress)
                .map { StorageNode(ref = StorageRef(path = path, id = it.id), name = path.substringAfterLast('/'), isDirectory = false) }

        override suspend fun download(
            path: String,
            outputStream: OutputStream,
            onProgress: (read: Long, total: Long?) -> Unit
        ): ApiResponse<Long> =
            filesystemApi.downloadFile(apiKeyProvider(), path, outputStream, onProgress).map { it }

        override suspend fun createDirectory(path: String, makeParents: Boolean): ApiResponse<Unit> =
            filesystemApi.createDirectory(apiKeyProvider(), path, makeParents).map { }

        override suspend fun rename(path: String, targetPath: String, makeParents: Boolean): ApiResponse<StorageNode> =
            filesystemApi.renameNode(apiKeyProvider(), path, targetPath, makeParents)
                .map { StorageNode(ref = StorageRef(path = targetPath), name = targetPath.substringAfterLast('/'), isDirectory = false) }

        override suspend fun delete(path: String, recursive: Boolean): ApiResponse<Unit> =
            filesystemApi.deleteNode(apiKeyProvider(), path, recursive).map { }
    }

    /** The private filesystem only shows to the account it belongs to, so its URLs carry the login. */
    override fun requestHeaders(url: String): Map<String, String> {
        val key = apiKeyProvider()
        return if (key.isNotBlank() && url.startsWith("https://$PIXELDRAIN_HOST/api/filesystem/")) {
            mapOf("Cookie" to "pd_auth_key=$key")
        } else {
            emptyMap()
        }
    }

    override fun shareUrl(node: StorageNode): String? = when {
        node.isDirectory -> null
        node.ref.id != null -> "https://$PIXELDRAIN_HOST/u/${node.ref.id}"
        node.ref.path.isNotBlank() -> "https://$PIXELDRAIN_HOST/d/${node.ref.path.trim('/')}"
        else -> null
    }

    override fun thumbnailUrl(node: StorageNode): String? {
        if (node.isDirectory) return null
        val richDetails = node.richDetails as? PixeldrainRichDetails
        return when {
            node.ref.id != null -> "https://$PIXELDRAIN_HOST/api/file/${node.ref.id}/thumbnail"
            richDetails?.thumbnailHref != null -> "https://$PIXELDRAIN_HOST${richDetails.thumbnailHref}"
            node.ref.path.isNotBlank() -> {
                val encoded = node.ref.path.trim('/').split('/').joinToString("/") { it.encodeURLPathPart() }
                "https://$PIXELDRAIN_HOST/api/filesystem/$encoded?thumbnail"
            }
            else -> null
        }
    }

    override fun rawContentUrl(node: StorageNode, attachment: Boolean): String? = when {
        node.ref.id != null -> "https://$PIXELDRAIN_HOST/api/file/${node.ref.id}" + (if (attachment) "?download" else "")
        node.ref.path.isNotBlank() -> "https://$PIXELDRAIN_HOST/api/filesystem${node.ref.path}" + (if (attachment) "?attach" else "")
        else -> null
    }

    override suspend fun validate(): ProviderValidationResult {
        val ok = try {
            val response = httpClient.okHttpClient.newCall(Request.Builder().url("https://$PIXELDRAIN_HOST/api/misc").head().build()).execute()
            response.use { it.code < 500 }
        } catch (_: Exception) {
            false
        }
        return ProviderValidationResult(
            listOf(FieldValidation(fieldId = "base_url", label = "pixeldrain.com", ok = ok, message = if (ok) "Reachable" else "Could not reach pixeldrain.com"))
        )
    }
}
