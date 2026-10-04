package tools.senko.materialdrain.provider.pixeldrain.internal

import android.content.Context
import android.net.Uri
import android.util.Log
import io.ktor.client.call.body
import io.ktor.client.request.headers
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.URLProtocol
import io.ktor.http.path
import kotlinx.coroutines.CancellationException
import okhttp3.FormBody
import okhttp3.Request
import java.io.OutputStream

private const val TAG_FS_API = "PIXEL_API_SERVICE"

/** Pixeldrain filesystem API: every /filesystem endpoint. Paths include the bucket, e.g. "me/photos/a.png". */
interface PixeldrainFilesystemApi {

    suspend fun getFilesystemPath(apiKey: String, fsPath: String): ApiResponse<FilesystemListResponse>

    suspend fun downloadFile(
        apiKey: String,
        fsPath: String,
        outputStream: OutputStream,
        onProgress: (bytesRead: Long, totalBytes: Long?) -> Unit
    ): ApiResponse<Long>

    /** PUT: writes (or overwrites) the file at [fsPath], the last path component being the file name. */
    suspend fun uploadFileFromUri(
        apiKey: String,
        fsPath: String,
        fileUri: Uri,
        context: Context,
        makeParents: Boolean = false,
        onProgress: (bytesSent: Long, totalBytes: Long?) -> Unit
    ): ApiResponse<FileUploadResponse>

    /** POST action mkdir / mkdirall. */
    suspend fun createDirectory(apiKey: String, fsPath: String, makeParents: Boolean = false): ApiResponse<FileUploadResponse>

    /** POST action rename. Also moves a node when the target lies in another directory. */
    suspend fun renameNode(apiKey: String, fsPath: String, targetPath: String, makeParents: Boolean = false): ApiResponse<FileUploadResponse>

    suspend fun deleteNode(apiKey: String, fsPath: String, recursive: Boolean = false): ApiResponse<FileUploadResponse>

    /** POST action import: copies files from the pixeldrain file list into the directory at [fsPath]. */
    suspend fun importFiles(apiKey: String, fsPath: String, sourceFileIds: List<String>): ApiResponse<FileUploadResponse>

    /** POST action update: changes node metadata, only the given [properties] are sent. */
    suspend fun updateNode(apiKey: String, fsPath: String, properties: Map<String, String>): ApiResponse<FileUploadResponse>
}

class PixeldrainFilesystemApiImpl(private val http: PixeldrainHttpClient) : PixeldrainFilesystemApi {

    private fun apiKeyMissing(message: String) =
        ApiResponse.Error<FileUploadResponse>(FileUploadResponse(success = false, value = "api_key_missing", message = message))

    override suspend fun getFilesystemPath(apiKey: String, fsPath: String): ApiResponse<FilesystemListResponse> {
        if (apiKey.isBlank()) {
            return ApiResponse.Error(FileUploadResponse(success = false, value = "api_key_missing", message = "API Key is required to browse filesystem."))
        }
        val actualPath = fsPath.ifBlank { "me" }

        return try {
            http.ktor.prepareGet {
                url {
                    protocol = URLProtocol.HTTPS
                    host = PIXELDRAIN_HOST
                    val pathSegments = listOf("api", "filesystem") + actualPath.split('/').filter { it.isNotEmpty() }
                    path(*pathSegments.toTypedArray())
                    parameters.append("stat", "") // ?stat=
                }
                headers {
                    append(HttpHeaders.Authorization, http.basicAuth(apiKey))
                }
            }.execute { response: HttpResponse ->
                if (response.status == HttpStatusCode.OK) {
                    ApiResponse.Success(response.body<FilesystemListResponse>())
                } else {
                    ApiResponse.Error(http.parseError(response, "filesystem_api_error"))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG_FS_API, "Exception for GET filesystem path '$actualPath': ${e.message}", e)
            val errorMsg = e.message ?: "Network request failed or failed to parse error response"
            ApiResponse.Error(FileUploadResponse(success = false, value = "network_exception_filesystem_path", message = errorMsg))
        }
    }

    override suspend fun downloadFile(
        apiKey: String,
        fsPath: String,
        outputStream: OutputStream,
        onProgress: (bytesRead: Long, totalBytes: Long?) -> Unit
    ): ApiResponse<Long> {
        if (fsPath.isBlank()) {
            return ApiResponse.Error(FileUploadResponse(success = false, value = "path_missing", message = "A path is required to download a file."))
        }
        val request = Request.Builder()
            .url(http.filesystemUrl(fsPath).addQueryParameter("attach", "").build())
            .header(HttpHeaders.AcceptEncoding, "identity")
            .apply { if (apiKey.isNotBlank()) header(HttpHeaders.Authorization, http.basicAuth(apiKey)) }
            .get()
            .build()
        return http.downloadToStream(request, outputStream, onProgress)
    }

    override suspend fun uploadFileFromUri(
        apiKey: String,
        fsPath: String,
        fileUri: Uri,
        context: Context,
        makeParents: Boolean,
        onProgress: (bytesSent: Long, totalBytes: Long?) -> Unit
    ): ApiResponse<FileUploadResponse> {
        if (apiKey.isBlank()) return apiKeyMissing("API Key is required to upload to the filesystem.")

        val contentResolver = context.contentResolver
        val mimeType = contentResolver.getType(fileUri) ?: ContentType.Application.OctetStream.toString()
        val fileSize = queryContentSize(context, fileUri)
        val body = StreamingRequestBody(
            mediaType = mimeType,
            length = fileSize ?: -1L,
            openStream = { contentResolver.openInputStream(fileUri) },
            onProgress = { sent -> onProgress(sent, fileSize) }
        )
        val url = http.filesystemUrl(fsPath).apply { if (makeParents) addQueryParameter("make_parents", "true") }.build()
        val request = Request.Builder().url(url).header(HttpHeaders.Authorization, http.basicAuth(apiKey)).put(body).build()
        return execute(request, "File uploaded.", "filesystem_upload", "network_exception_filesystem_upload")
    }

    override suspend fun createDirectory(apiKey: String, fsPath: String, makeParents: Boolean): ApiResponse<FileUploadResponse> =
        postAction(
            apiKey, fsPath,
            listOf("action" to if (makeParents) "mkdirall" else "mkdir"),
            successMessage = "Folder created.", actionName = "mkdir"
        )

    override suspend fun renameNode(apiKey: String, fsPath: String, targetPath: String, makeParents: Boolean): ApiResponse<FileUploadResponse> =
        postAction(
            apiKey, fsPath,
            buildList {
                add("action" to "rename")
                add("target" to "/" + targetPath.trim('/')) // the target has to include the bucket
                if (makeParents) add("make_parents" to "true")
            },
            successMessage = "Item renamed.", actionName = "rename"
        )

    override suspend fun deleteNode(apiKey: String, fsPath: String, recursive: Boolean): ApiResponse<FileUploadResponse> {
        if (apiKey.isBlank()) return apiKeyMissing("API Key is required to delete filesystem items.")
        val url = http.filesystemUrl(fsPath).apply { if (recursive) addQueryParameter("recursive", "true") }.build()
        val request = Request.Builder().url(url).header(HttpHeaders.Authorization, http.basicAuth(apiKey)).delete().build()
        return execute(request, "Item deleted.", "filesystem_delete", "network_exception_filesystem_delete")
    }

    override suspend fun importFiles(apiKey: String, fsPath: String, sourceFileIds: List<String>): ApiResponse<FileUploadResponse> {
        if (sourceFileIds.isEmpty()) {
            return ApiResponse.Error(FileUploadResponse(success = false, value = "no_source_files", message = "No source file IDs provided for import."))
        }
        return postAction(
            apiKey, fsPath,
            listOf("action" to "import", "files" to http.json.encodeToString(sourceFileIds)),
            successMessage = "Files imported.", actionName = "import"
        )
    }

    override suspend fun updateNode(apiKey: String, fsPath: String, properties: Map<String, String>): ApiResponse<FileUploadResponse> =
        postAction(
            apiKey, fsPath,
            listOf("action" to "update") + properties.map { it.key to it.value },
            successMessage = "Item updated.", actionName = "update"
        )

    private suspend fun postAction(
        apiKey: String,
        fsPath: String,
        params: List<Pair<String, String>>,
        successMessage: String,
        actionName: String
    ): ApiResponse<FileUploadResponse> {
        if (apiKey.isBlank()) return apiKeyMissing("API Key is required for filesystem operations.")
        val form = FormBody.Builder().apply { params.forEach { (name, value) -> add(name, value) } }.build()
        val request = Request.Builder()
            .url(http.filesystemUrl(fsPath).build())
            .header(HttpHeaders.Authorization, http.basicAuth(apiKey))
            .post(form)
            .build()
        return execute(request, successMessage, "filesystem_$actionName", "network_exception_filesystem_$actionName")
    }

    private suspend fun execute(
        request: Request,
        successMessage: String,
        errorFallback: String,
        networkErrorCode: String
    ): ApiResponse<FileUploadResponse> {
        return try {
            http.mutationResult(http.send(request), successMessage, "${errorFallback}_failed_status")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG_FS_API, "Exception during ${request.method} ${request.url}: ${e.message}", e)
            ApiResponse.Error(FileUploadResponse(success = false, value = networkErrorCode, message = e.message ?: "Network request failed"))
        }
    }
}
