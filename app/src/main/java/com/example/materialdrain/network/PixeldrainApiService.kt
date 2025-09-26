package com.example.materialdrain.network

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns // Added for file size
import android.util.Base64
import android.util.Log
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.plugins.onDownload
import io.ktor.client.plugins.onUpload
import io.ktor.client.request.headers
import io.ktor.client.request.prepareDelete
import io.ktor.client.request.prepareGet
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.URLProtocol
import io.ktor.http.path
import io.ktor.http.encodedPath
import io.ktor.http.takeFrom
import io.ktor.http.Url
import io.ktor.http.content.OutputStreamContent
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.Protocol
import okhttp3.OkHttpClient
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.TimeUnit

// --- Generic API Response Wrapper ---
sealed class ApiResponse<T> {
    data class Success<T>(val data: T) : ApiResponse<T>()
    data class Error<T>(val errorDetails: FileUploadResponse) : ApiResponse<T>() // Consistent error type
}

// --- Data Classes for API Response ---
@Serializable
data class FileUploadResponse(
    val success: Boolean,
    val id: String? = null,
    val value: String? = null,
    val message: String? = null
)

@Serializable
data class FileUploadPutSuccessResponse(
    val id: String
)

@Serializable
data class UserFilesListResponse(
    val files: List<FileInfoResponse>
)

@Serializable
data class FileInfoResponse(
    val id: String,
    val name: String,
    val size: Long,
    val views: Int? = null,
    @SerialName("bandwidth_used") val bandwidthUsed: Long? = null,
    @SerialName("bandwidth_used_paid") val bandwidthUsedPaid: Long? = null,
    val downloads: Int? = null,
    @SerialName("date_upload") val dateUpload: String,
    @SerialName("date_last_view") val dateLastView: String? = null,
    @SerialName("mime_type") val mimeType: String? = null,
    @SerialName("thumbnail_href") val thumbnailHref: String? = null,
    @SerialName("hash_sha256") val hashSha256: String? = null,
    @SerialName("can_edit") val canEdit: Boolean? = null,
    @SerialName("delete_after_date") val deleteAfterDate: String? = null,
    @SerialName("delete_after_downloads") val deleteAfterDownloads: Int? = null,
    val availability: String? = null,
    @SerialName("availability_message") val availabilityMessage: String? = null,
    @SerialName("abuse_type") val abuseType: String? = null,
    @SerialName("abuse_reporter_name") val abuseReporterName: String? = null,
    @SerialName("can_download") val canDownload: Boolean? = null,
    @SerialName("show_ads") val showAds: Boolean? = null,
    @SerialName("allow_video_player") val allowVideoPlayer: Boolean? = null,
    @SerialName("download_speed_limit") val downloadSpeedLimit: Long? = null
)

// --- Filesystem API Data Classes ---
@Serializable
data class FilesystemEntry(
    val type: String, // "dir" or "file"
    val path: String,
    val name: String,
    val created: String,
    val modified: String,
    @SerialName("mode_string") val modeString: String,
    @SerialName("mode_octal") val modeOctal: String,
    @SerialName("created_by") val createdBy: String,
    @SerialName("file_size") val fileSize: Long, // Will be 0 for dirs
    @SerialName("file_type") val fileType: String, // Mime type for files, empty for dirs
    @SerialName("sha256_sum") val sha256Sum: String, // SHA256 for files, empty for dirs
    val id: String? = null, // Pixeldrain file ID if it\'s a direct file, null for dirs or "me" for root dir in path context
    @SerialName("logging_enabled_at") val loggingEnabledAt: String? = null,
    // Fields from FileInfoResponse that might appear if \'?stats\' is used for a file child
    val views: Int? = null,
    @SerialName("bandwidth_used") val bandwidthUsed: Long? = null,
    @SerialName("bandwidth_used_paid") val bandwidthUsedPaid: Long? = null,
    val downloads: Int? = null,
    @SerialName("date_last_view") val dateLastView: String? = null,
    @SerialName("mime_type") val mimeType: String? = null, // More specific than fileType for files
    @SerialName("thumbnail_href") val thumbnailHref: String? = null,
    @SerialName("can_edit") val canEdit: Boolean? = null,
    @SerialName("delete_after_date") val deleteAfterDate: String? = null,
    @SerialName("delete_after_downloads") val deleteAfterDownloads: Int? = null,
    val availability: String? = null,
    @SerialName("availability_message") val availabilityMessage: String? = null,
    @SerialName("abuse_type") val abuseType: String? = null,
    @SerialName("abuse_reporter_name") val abuseReporterName: String? = null,
    @SerialName("can_download") val canDownload: Boolean? = null,
    @SerialName("show_ads") val showAds: Boolean? = null,
    @SerialName("allow_video_player") val allowVideoPlayer: Boolean? = null,
    @SerialName("download_speed_limit") val downloadSpeedLimit: Long? = null
)

@Serializable
data class FilesystemPermissions(
    val owner: Boolean,
    val read: Boolean,
    val write: Boolean,
    val delete: Boolean
)

@Serializable
data class FilesystemContext(
    @SerialName("premium_transfer") val premiumTransfer: Boolean
)

@Serializable
data class FilesystemListResponse(
    val path: List<FilesystemEntry>,
    @SerialName("base_index") val baseIndex: Int,
    val children: List<FilesystemEntry>,
    val permissions: FilesystemPermissions,
    val context: FilesystemContext,
    val success: Boolean? = null,
    val value: String? = null,
    val message: String? = null
)

class PixeldrainApiService {

    private val streamBufferSize = 1024 * 1024

    private val client = HttpClient(OkHttp) {
        engine {
            preconfigured = OkHttpClient.Builder()
                .protocols(listOf(Protocol.HTTP_2, Protocol.HTTP_1_1))
                .connectTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(0, TimeUnit.SECONDS)
                .readTimeout(0, TimeUnit.SECONDS)
                .build()
        }
        install(ContentNegotiation) {
            val jsonFormatter = Json {
                prettyPrint = true
                isLenient = true
                ignoreUnknownKeys = true
            }
            json(jsonFormatter, contentType = ContentType.Application.Json)
            json(jsonFormatter, contentType = ContentType.Text.Plain)
        }
        install(HttpTimeout) {
            requestTimeoutMillis = HttpTimeout.INFINITE_TIMEOUT_MS
            connectTimeoutMillis = 30000L
            socketTimeoutMillis = 900000L
        }
        install(Logging) {
            logger = object : Logger {
                override fun log(message: String) {
                    Log.d("KTOR_HTTP_CLIENT", message)
                }
            }
            level = LogLevel.HEADERS
        }
        install(HttpRequestRetry) {
            retryOnServerErrors(maxRetries = 2)
            retryOnExceptionIf { _, cause ->
                cause is IOException && cause !is java.net.SocketTimeoutException
            }
        }
    }

    suspend fun uploadFile(
        apiKey: String,
        fileName: String,
        fileBytes: ByteArray,
        onProgress: (bytesSent: Long, totalBytes: Long?) -> Unit
    ): FileUploadResponse {
        val basicAuth = "Basic " + Base64.encodeToString(":$apiKey".toByteArray(), Base64.NO_WRAP)
        return try {
            val response: HttpResponse = client.put {
                url {
                    protocol = URLProtocol.HTTPS
                    host = "pixeldrain.com"
                    path("api/file", fileName) // Ktor handles encoding for path segments
                }
                headers {
                    append(HttpHeaders.Authorization, basicAuth)
                    append(HttpHeaders.ContentType, ContentType.Application.OctetStream.toString())
                    append(HttpHeaders.ContentLength, fileBytes.size.toString())
                }
                setBody(fileBytes)
                onUpload { bytesSentTotal, contentLength ->
                    onProgress(bytesSentTotal, contentLength)
                }
            }
            if (response.status == HttpStatusCode.Created) {
                val successBody = response.body<FileUploadPutSuccessResponse>()
                FileUploadResponse(success = true, id = successBody.id)
            } else {
                response.body<FileUploadResponse>()
            }
        } catch (e: Exception) {
            Log.e("PIXEL_API_SERVICE", "Exception during PUT text/byte array upload: ${e.message}", e)
            FileUploadResponse(success = false, value = "network_exception_upload_bytearray", message = e.message ?: "Network request failed")
        }
    }

    suspend fun uploadFileFromUri(
        apiKey: String,
        fileName: String,
        fileUri: Uri,
        context: Context,
        onProgress: (bytesSent: Long, totalBytes: Long?) -> Unit
    ): FileUploadResponse {
        val contentResolver = context.contentResolver
        val basicAuth = "Basic " + Base64.encodeToString(":$apiKey".toByteArray(), Base64.NO_WRAP)
        val mimeType = contentResolver.getType(fileUri) ?: ContentType.Application.OctetStream.toString()
        var fileSize: Long? = null
        try {
            contentResolver.query(fileUri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (!cursor.isNull(sizeIndex)) {
                        fileSize = cursor.getLong(sizeIndex)
                    }
                }
            }
        } catch (e: Exception) {
            Log.w("PIXEL_API_SERVICE", "Could not determine file size for Content-Length: ${e.message}")
        }

        return try {
            val response: HttpResponse = client.put {
                url {
                    protocol = URLProtocol.HTTPS
                    host = "pixeldrain.com"
                    path("api/file", fileName) // Ktor handles encoding for path segments
                }
                headers {
                    append(HttpHeaders.Authorization, basicAuth)
                    fileSize?.let {
                        append(HttpHeaders.ContentLength, it.toString())
                    }
                }
                setBody(
                    OutputStreamContent(
                        body = {
                            contentResolver.openInputStream(fileUri)?.use { rawInputStream ->
                                BufferedInputStream(rawInputStream).use { bufferedInputStream ->
                                    bufferedInputStream.copyTo(this, bufferSize = streamBufferSize)
                                }
                            } ?: throw IOException("Failed to open input stream for URI after initial check.")
                        },
                        contentType = ContentType.parse(mimeType),
                        contentLength = fileSize
                    )
                )
                onUpload { bytesSentTotal, contentLength -> // contentLength here is non-nullable Long
                    onProgress(bytesSentTotal, contentLength) // Pass directly to onProgress which expects Long?
                }
            }
            if (response.status == HttpStatusCode.Created) {
                val successBody = response.body<FileUploadPutSuccessResponse>()
                FileUploadResponse(success = true, id = successBody.id)
            } else {
                response.body<FileUploadResponse>()
            }
        } catch (e: Exception) {
            Log.e("PIXEL_API_SERVICE", "Exception during PUT URI upload (streaming): ${e.message}", e)
            FileUploadResponse(success = false, value = "network_exception_upload_uri_stream", message = e.message ?: "Network request failed or stream error")
        }
    }

    suspend fun getFileInfo(fileId: String, apiKey: String?): ApiResponse<FileInfoResponse> {
        return try {
            client.prepareGet {
                url {
                    protocol = URLProtocol.HTTPS
                    host = "pixeldrain.com"
                    path("api/file", fileId, "info")
                }
                if (!apiKey.isNullOrBlank()) {
                    val basicAuth = "Basic " + Base64.encodeToString(":$apiKey".toByteArray(), Base64.NO_WRAP)
                    headers {
                        append(HttpHeaders.Authorization, basicAuth)
                    }
                }
            }.execute { response: HttpResponse ->
                if (response.status == HttpStatusCode.OK) {
                    ApiResponse.Success(response.body<FileInfoResponse>())
                } else {
                    ApiResponse.Error(response.body<FileUploadResponse>())
                }
            }
        } catch (e: Exception) {
            Log.e("PIXEL_API_SERVICE", "Exception for GET file info: ${e.message}", e)
            val errorMsg = e.message ?: "Network request failed or failed to parse error response"
            ApiResponse.Error(FileUploadResponse(success = false, value = "network_exception_file_info", message = errorMsg))
        }
    }

    suspend fun getFilesystemPath(apiKey: String, fsPath: String): ApiResponse<FilesystemListResponse> {
        if (apiKey.isBlank()) {
            return ApiResponse.Error(FileUploadResponse(success = false, value = "api_key_missing", message = "API Key is required to browse filesystem."))
        }
        val basicAuth = "Basic " + Base64.encodeToString(":$apiKey".toByteArray(), Base64.NO_WRAP)
        val actualPath = fsPath.ifBlank { "me" }

        return try {
            client.prepareGet {
                url {
                    protocol = URLProtocol.HTTPS
                    host = "pixeldrain.com"
                    val pathSegments = listOf("api", "filesystem") + actualPath.split('/').filter { it.isNotEmpty() }
                    path(*pathSegments.toTypedArray()) // Correctly spread the list into varargs for path()
                    parameters.append("stats", "true")
                }
                headers {
                    append(HttpHeaders.Authorization, basicAuth)
                }
            }.execute { response: HttpResponse ->
                if (response.status == HttpStatusCode.OK) {
                    val filesystemData = response.body<FilesystemListResponse>()
                    ApiResponse.Success(filesystemData)
                } else {
                    try {
                        val errorBody = response.body<FilesystemListResponse>()
                         ApiResponse.Error(FileUploadResponse(success = errorBody.success ?: false, value = errorBody.value, message = errorBody.message ?: "Filesystem API error"))
                    } catch (_: Exception) {
                        try {
                            ApiResponse.Error(response.body<FileUploadResponse>())
                        } catch (_: Exception) {
                             ApiResponse.Error(FileUploadResponse(success = false, value = "filesystem_api_error_parsing_failed", message = "HTTP ${response.status.value}: Could not parse error response."))
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("PIXEL_API_SERVICE", "Exception for GET filesystem path '$actualPath': ${e.message}", e)
            val errorMsg = e.message ?: "Network request failed or failed to parse error response"
            ApiResponse.Error(FileUploadResponse(success = false, value = "network_exception_filesystem_path", message = errorMsg))
        }
    }

    suspend fun downloadFileToOutputStream(
        fileId: String,
        outputStream: java.io.OutputStream,
        onProgress: (bytesRead: Long, totalBytes: Long?) -> Unit
    ): ApiResponse<Long> {
        if (fileId.isBlank()) {
            return ApiResponse.Error(FileUploadResponse(success = false, value = "file_id_missing", message = "File ID is required to download a file."))
        }
        return try {
            client.prepareGet {
                url {
                    protocol = URLProtocol.HTTPS
                    host = "pixeldrain.com"
                    path("api/file", fileId)
                }
            }.execute { httpResponse: HttpResponse ->
                if (httpResponse.status == HttpStatusCode.OK) {
                    val inputStream: InputStream = httpResponse.body()
                    val totalBytesFromServer = httpResponse.headers[HttpHeaders.ContentLength]?.toLongOrNull()
                    var totalBytesCopied = 0L
                    val buffer = ByteArray(streamBufferSize)
                    var bytesRead: Int
                    try {
                        inputStream.use { netStream ->
                            while (netStream.read(buffer).also { bytesRead = it } != -1) {
                                outputStream.write(buffer, 0, bytesRead)
                                totalBytesCopied += bytesRead
                                onProgress(totalBytesCopied, totalBytesFromServer)
                            }
                        }
                        outputStream.flush()
                        ApiResponse.Success(totalBytesCopied)
                    } catch (e: IOException) {
                        Log.e("PIXEL_API_SERVICE", "IOException during stream copy: ${e.message}", e)
                        ApiResponse.Error(FileUploadResponse(success = false, value = "download_stream_copy_error", message = e.message ?: "Error copying download stream"))
                    }
                } else {
                    try {
                        val errorBody = httpResponse.body<FileUploadResponse>()
                        ApiResponse.Error(errorBody)
                    } catch (_: Exception) {
                        Log.e("PIXEL_API_SERVICE", "Failed to parse error body for download")
                        ApiResponse.Error(FileUploadResponse(success = false, value = "download_failed_status_${httpResponse.status.value}", message = "Download failed: ${httpResponse.status.description}. Error body parsing failed."))
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("PIXEL_API_SERVICE", "Exception during GET file download to stream: ${e.message}", e)
            val errorMsg = e.message ?: "Network request failed or failed to parse response"
            ApiResponse.Error(FileUploadResponse(success = false, value = "network_exception_file_download_stream", message = errorMsg))
        }
    }

    suspend fun getFileContentAsText(fileId: String): ApiResponse<String> {
        if (fileId.isBlank()) {
            return ApiResponse.Error(FileUploadResponse(success = false, value = "file_id_missing", message = "File ID is required to get file content."))
        }
        return try {
            client.prepareGet {
                url {
                    protocol = URLProtocol.HTTPS
                    host = "pixeldrain.com"
                    path("api/file", fileId)
                }
            }.execute { response: HttpResponse ->
                if (response.status == HttpStatusCode.OK) {
                    val textContent = response.body<String>()
                    ApiResponse.Success(textContent)
                } else {
                    try {
                        ApiResponse.Error(response.body<FileUploadResponse>())
                    } catch (_: Exception) {
                        ApiResponse.Error(FileUploadResponse(success = false, value = "get_content_failed_status_${response.status.value}", message = "Failed to get content: ${response.status.description}"))
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("PIXEL_API_SERVICE", "Exception during GET file content as text: ${e.message}", e)
            val errorMsg = e.message ?: "Network request failed or failed to parse response"
            ApiResponse.Error(FileUploadResponse(success = false, value = "network_exception_get_content", message = errorMsg))
        }
    }

    suspend fun getUserFiles(apiKey: String): ApiResponse<UserFilesListResponse> {
        if (apiKey.isBlank()) {
            return ApiResponse.Error(FileUploadResponse(success = false, value = "api_key_missing", message = "API Key is required to fetch user files."))
        }
        val basicAuth = "Basic " + Base64.encodeToString(":$apiKey".toByteArray(), Base64.NO_WRAP)
        return try {
            client.prepareGet {
                url {
                    protocol = URLProtocol.HTTPS
                    host = "pixeldrain.com"
                    path("api/user/files")
                }
                headers {
                    append(HttpHeaders.Authorization, basicAuth)
                }
            }.execute { response: HttpResponse ->
                if (response.status == HttpStatusCode.OK) {
                    ApiResponse.Success(response.body<UserFilesListResponse>())
                } else {
                    ApiResponse.Error(response.body<FileUploadResponse>())
                }
            }
        } catch (e: Exception) {
            Log.e("PIXEL_API_SERVICE", "Exception for GET user files: ${e.message}", e)
            val errorMsg = e.message ?: "Network request failed or failed to parse error response"
            ApiResponse.Error(FileUploadResponse(success = false, value = "network_exception_user_files", message = errorMsg))
        }
    }

    suspend fun deleteFile(apiKey: String, fileId: String): ApiResponse<FileUploadResponse> {
        if (apiKey.isBlank()) {
            return ApiResponse.Error(FileUploadResponse(success = false, value = "api_key_missing", message = "API Key is required to delete files."))
        }
        if (fileId.isBlank()) {
            return ApiResponse.Error(FileUploadResponse(success = false, value = "file_id_missing", message = "File ID is required to delete a file."))
        }
        val basicAuth = "Basic " + Base64.encodeToString(":$apiKey".toByteArray(), Base64.NO_WRAP)
        return try {
            client.prepareDelete {
                url {
                    protocol = URLProtocol.HTTPS
                    host = "pixeldrain.com"
                    path("api/file", fileId)
                }
                headers {
                    append(HttpHeaders.Authorization, basicAuth)
                }
            }.execute { response: HttpResponse ->
                val responseBody = response.body<FileUploadResponse>()
                if (responseBody.success) {
                    ApiResponse.Success(responseBody)
                } else {
                    ApiResponse.Error(responseBody)
                }
            }
        } catch (e: Exception) {
            Log.e("PIXEL_API_SERVICE", "Exception during DELETE file: ${e.message}", e)
            val errorMsg = e.message ?: "Network request failed or failed to parse error/success response"
            ApiResponse.Error(FileUploadResponse(success = false, value = "network_exception_delete_file", message = errorMsg))
        }
    }
}
