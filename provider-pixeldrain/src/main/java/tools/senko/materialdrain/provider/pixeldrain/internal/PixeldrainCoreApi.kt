package tools.senko.materialdrain.provider.pixeldrain.internal

import android.content.Context
import android.net.Uri
import android.util.Log
import io.ktor.client.call.body
import io.ktor.client.request.headers
import io.ktor.client.request.prepareDelete
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.URLProtocol
import io.ktor.http.path
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayInputStream

private const val TAG_CORE_API = "PIXEL_API_SERVICE"

/** General pixeldrain API: /file and /list endpoints. */
interface PixeldrainCoreApi {

    suspend fun uploadFile(
        apiKey: String,
        fileName: String,
        fileBytes: ByteArray,
        onProgress: (bytesSent: Long, totalBytes: Long?) -> Unit
    ): FileUploadResponse

    suspend fun uploadFileFromUri(
        apiKey: String,
        fileName: String,
        fileUri: Uri,
        context: Context,
        onProgress: (bytesSent: Long, totalBytes: Long?) -> Unit
    ): FileUploadResponse

    suspend fun getFileInfo(fileId: String, apiKey: String?): ApiResponse<FileInfoResponse>

    /** [fileId] may hold several comma separated IDs, the server then answers with a zip archive. */
    suspend fun downloadFileToOutputStream(
        fileId: String,
        outputStream: java.io.OutputStream,
        apiKey: String? = null,
        onProgress: (bytesRead: Long, totalBytes: Long?) -> Unit
    ): ApiResponse<Long>

    suspend fun getFileContentAsText(fileId: String): ApiResponse<String>

    suspend fun deleteFile(apiKey: String, fileId: String): ApiResponse<FileUploadResponse>

    suspend fun getList(listId: String, apiKey: String?): ApiResponse<ListInfoResponse>

    /** POST /list: creates a list of the given files, linked to the account of [apiKey]. The id of the list is in the result. */
    suspend fun createList(apiKey: String, title: String, fileIds: List<String>): ApiResponse<FileUploadResponse>

    /**
     * PUT /list/{id}: replaces the title and the files of an existing list, the id (and so the link) stays the
     * same. The endpoint is not in the published API documentation, the request has the shape of [createList].
     */
    suspend fun updateList(apiKey: String, listId: String, title: String, fileIds: List<String>): ApiResponse<FileUploadResponse>

    /** DELETE /list/{id}: removes the list itself, the files in it are not touched. */
    suspend fun deleteList(apiKey: String, listId: String): ApiResponse<FileUploadResponse>
}

class PixeldrainCoreApiImpl(private val http: PixeldrainHttpClient) : PixeldrainCoreApi {

    override suspend fun uploadFile(
        apiKey: String,
        fileName: String,
        fileBytes: ByteArray,
        onProgress: (bytesSent: Long, totalBytes: Long?) -> Unit
    ): FileUploadResponse {
        val body = StreamingRequestBody(
            mediaType = ContentType.Application.OctetStream.toString(),
            length = fileBytes.size.toLong(),
            openStream = { ByteArrayInputStream(fileBytes) },
            onProgress = { sent -> onProgress(sent, fileBytes.size.toLong()) }
        )
        return putFile(apiKey, fileName, body, "network_exception_upload_bytearray")
    }

    override suspend fun uploadFileFromUri(
        apiKey: String,
        fileName: String,
        fileUri: Uri,
        context: Context,
        onProgress: (bytesSent: Long, totalBytes: Long?) -> Unit
    ): FileUploadResponse {
        val contentResolver = context.contentResolver
        val mimeType = contentResolver.getType(fileUri) ?: ContentType.Application.OctetStream.toString()
        val fileSize = queryContentSize(context, fileUri)

        val body = StreamingRequestBody(
            mediaType = mimeType,
            length = fileSize ?: -1L,
            openStream = { contentResolver.openInputStream(fileUri) },
            onProgress = { sent -> onProgress(sent, fileSize) }
        )
        return putFile(apiKey, fileName, body, "network_exception_upload_uri_stream")
    }

    private suspend fun putFile(apiKey: String, fileName: String, body: RequestBody, errorCode: String): FileUploadResponse {
        val request = Request.Builder()
            .url(http.apiUrl("file", fileName).build())
            .header(HttpHeaders.Authorization, http.basicAuth(apiKey))
            .put(body)
            .build()
        return try {
            val raw = http.send(request)
            if (raw.code == HttpStatusCode.Created.value) {
                FileUploadResponse(success = true, id = http.json.decodeFromString<FileUploadPutSuccessResponse>(raw.body).id)
            } else {
                http.parseError(raw.code, raw.body, "upload_failed_status")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG_CORE_API, "Exception during PUT upload: ${e.message}", e)
            FileUploadResponse(success = false, value = errorCode, message = e.message ?: "Network request failed")
        }
    }

    override suspend fun getFileInfo(fileId: String, apiKey: String?): ApiResponse<FileInfoResponse> {
        return try {
            http.ktor.prepareGet {
                url {
                    protocol = URLProtocol.HTTPS
                    host = PIXELDRAIN_HOST
                    path("api/file", fileId, "info")
                }
                if (!apiKey.isNullOrBlank()) {
                    headers {
                        append(HttpHeaders.Authorization, http.basicAuth(apiKey))
                    }
                }
            }.execute { response: HttpResponse ->
                if (response.status == HttpStatusCode.OK) {
                    ApiResponse.Success(response.body<FileInfoResponse>())
                } else {
                    ApiResponse.Error(http.parseError(response, "file_info_failed_status"))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG_CORE_API, "Exception for GET file info: ${e.message}", e)
            val errorMsg = e.message ?: "Network request failed or failed to parse error response"
            ApiResponse.Error(FileUploadResponse(success = false, value = "network_exception_file_info", message = errorMsg))
        }
    }

    override suspend fun downloadFileToOutputStream(
        fileId: String,
        outputStream: java.io.OutputStream,
        apiKey: String?,
        onProgress: (bytesRead: Long, totalBytes: Long?) -> Unit
    ): ApiResponse<Long> {
        if (fileId.isBlank()) {
            return ApiResponse.Error(FileUploadResponse(success = false, value = "file_id_missing", message = "File ID is required to download a file."))
        }
        // "identity" stops OkHttp from transparently gzipping, which would hide the real Content-Length.
        val request = Request.Builder()
            .url(http.apiUrl("file", fileId).build())
            .header(HttpHeaders.AcceptEncoding, "identity")
            .apply { if (!apiKey.isNullOrBlank()) header(HttpHeaders.Authorization, http.basicAuth(apiKey)) }
            .get()
            .build()
        return http.downloadToStream(request, outputStream, onProgress)
    }

    override suspend fun getFileContentAsText(fileId: String): ApiResponse<String> {
        if (fileId.isBlank()) {
            return ApiResponse.Error(FileUploadResponse(success = false, value = "file_id_missing", message = "File ID is required to get file content."))
        }
        return try {
            http.ktor.prepareGet {
                url {
                    protocol = URLProtocol.HTTPS
                    host = PIXELDRAIN_HOST
                    path("api/file", fileId)
                }
            }.execute { response: HttpResponse ->
                if (response.status == HttpStatusCode.OK) {
                    ApiResponse.Success(response.body<String>())
                } else {
                    ApiResponse.Error(http.parseError(response, "get_content_failed_status"))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG_CORE_API, "Exception during GET file content as text: ${e.message}", e)
            val errorMsg = e.message ?: "Network request failed or failed to parse response"
            ApiResponse.Error(FileUploadResponse(success = false, value = "network_exception_get_content", message = errorMsg))
        }
    }

    override suspend fun deleteFile(apiKey: String, fileId: String): ApiResponse<FileUploadResponse> {
        if (apiKey.isBlank()) {
            return ApiResponse.Error(FileUploadResponse(success = false, value = "api_key_missing", message = "API Key is required to delete files."))
        }
        if (fileId.isBlank()) {
            return ApiResponse.Error(FileUploadResponse(success = false, value = "file_id_missing", message = "File ID is required to delete a file."))
        }
        return try {
            http.ktor.prepareDelete {
                url {
                    protocol = URLProtocol.HTTPS
                    host = PIXELDRAIN_HOST
                    path("api/file", fileId)
                }
                headers {
                    append(HttpHeaders.Authorization, http.basicAuth(apiKey))
                }
            }.execute { response: HttpResponse ->
                val responseBody = http.parseError(response, "delete_failed_status")
                if (response.status.value in 200..299 && responseBody.success) {
                    ApiResponse.Success(responseBody)
                } else {
                    ApiResponse.Error(responseBody)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG_CORE_API, "Exception during DELETE file: ${e.message}", e)
            val errorMsg = e.message ?: "Network request failed or failed to parse error/success response"
            ApiResponse.Error(FileUploadResponse(success = false, value = "network_exception_delete_file", message = errorMsg))
        }
    }

    override suspend fun createList(apiKey: String, title: String, fileIds: List<String>): ApiResponse<FileUploadResponse> {
        if (apiKey.isBlank()) {
            return ApiResponse.Error(FileUploadResponse(success = false, value = "api_key_missing", message = "API Key is required to create a list."))
        }
        if (fileIds.isEmpty()) {
            return ApiResponse.Error(FileUploadResponse(success = false, value = "cannot_create_empty_list", message = "You cannot make a list with no files."))
        }
        val request = Request.Builder()
            .url(http.apiUrl("list").build())
            .header(HttpHeaders.Authorization, http.basicAuth(apiKey))
            .post(listRequestBody(title, fileIds))
            .build()
        return try {
            val raw = http.send(request)
            if (raw.code == HttpStatusCode.Created.value) {
                ApiResponse.Success(http.json.decodeFromString<FileUploadResponse>(raw.body))
            } else {
                ApiResponse.Error(http.parseError(raw.code, raw.body, "create_list_failed_status"))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG_CORE_API, "Exception during POST list: ${e.message}", e)
            ApiResponse.Error(FileUploadResponse(success = false, value = "network_exception_create_list", message = e.message ?: "Network request failed"))
        }
    }

    private fun listRequestBody(title: String, fileIds: List<String>): RequestBody = buildJsonObject {
        put("title", title.trim().ifEmpty { "Pixeldrain List" })
        put("anonymous", false)
        putJsonArray("files") { fileIds.forEach { id -> add(buildJsonObject { put("id", id) }) } }
    }.toString().toRequestBody("application/json".toMediaType())

    override suspend fun updateList(apiKey: String, listId: String, title: String, fileIds: List<String>): ApiResponse<FileUploadResponse> {
        if (apiKey.isBlank()) {
            return ApiResponse.Error(FileUploadResponse(success = false, value = "api_key_missing", message = "API Key is required to change a list."))
        }
        if (fileIds.isEmpty()) {
            return ApiResponse.Error(FileUploadResponse(success = false, value = "cannot_create_empty_list", message = "A list cannot have no files."))
        }
        val request = Request.Builder()
            .url(http.apiUrl("list", listId).build())
            .header(HttpHeaders.Authorization, http.basicAuth(apiKey))
            .put(listRequestBody(title, fileIds))
            .build()
        return sendListMutation(request, "Changed the list.", "update_list_failed_status", "network_exception_update_list")
    }

    override suspend fun deleteList(apiKey: String, listId: String): ApiResponse<FileUploadResponse> {
        if (apiKey.isBlank()) {
            return ApiResponse.Error(FileUploadResponse(success = false, value = "api_key_missing", message = "API Key is required to delete a list."))
        }
        val request = Request.Builder()
            .url(http.apiUrl("list", listId).build())
            .header(HttpHeaders.Authorization, http.basicAuth(apiKey))
            .delete()
            .build()
        return sendListMutation(request, "Deleted the list.", "delete_list_failed_status", "network_exception_delete_list")
    }

    private suspend fun sendListMutation(request: Request, successMessage: String, statusError: String, networkError: String): ApiResponse<FileUploadResponse> =
        try {
            http.mutationResult(http.send(request), successMessage, statusError)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG_CORE_API, "Exception during ${request.method} list: ${e.message}", e)
            ApiResponse.Error(FileUploadResponse(success = false, value = networkError, message = e.message ?: "Network request failed"))
        }

    override suspend fun getList(listId: String, apiKey: String?): ApiResponse<ListInfoResponse> {
        if (listId.isBlank()) {
            return ApiResponse.Error(FileUploadResponse(success = false, value = "list_id_missing", message = "List ID is required."))
        }
        return try {
            http.ktor.prepareGet {
                url {
                    protocol = URLProtocol.HTTPS
                    host = PIXELDRAIN_HOST
                    path("api/list", listId)
                }
                if (!apiKey.isNullOrBlank()) {
                    headers {
                        append(HttpHeaders.Authorization, http.basicAuth(apiKey))
                    }
                }
            }.execute { response: HttpResponse ->
                if (response.status == HttpStatusCode.OK) {
                    ApiResponse.Success(response.body<ListInfoResponse>())
                } else {
                    ApiResponse.Error(http.parseError(response, "get_list_failed_status"))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG_CORE_API, "Exception for GET list: ${e.message}", e)
            ApiResponse.Error(FileUploadResponse(success = false, value = "network_exception_get_list", message = e.message ?: "Network request failed or failed to parse response"))
        }
    }
}
