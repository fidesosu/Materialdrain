package com.example.materialdrain.network

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns // Added for file size
import android.util.Base64
import android.util.Log
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.plugins.onDownload // Added for download progress
import io.ktor.client.plugins.onUpload // Corrected import for onUpload
import io.ktor.client.request.delete // Added for deleteFile
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.URLProtocol
import io.ktor.http.content.OutputStreamContent // Added for streaming
import io.ktor.http.path
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.IOException

// --- Generic API Response Wrapper ---
sealed class ApiResponse<T> {
    data class Success<T>(val data: T) : ApiResponse<T>()
    data class Error<T>(val errorDetails: FileUploadResponse) : ApiResponse<T>() // Reusing FileUploadResponse for error structure
}

// --- Data Classes for API Response ---
@Serializable
data class FileUploadResponse( // Used for upload responses and general API errors
    val success: Boolean,
    val id: String? = null,
    val value: String? = null,
    val message: String? = null
)

@Serializable
data class FileUploadPutSuccessResponse( // Specific for successful PUT response
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

    // Fields from /user/files endpoint (nullable for compatibility)
    @SerialName("delete_after_date") val deleteAfterDate: String? = null,
    @SerialName("delete_after_downloads") val deleteAfterDownloads: Int? = null,
    val availability: String? = null,
    @SerialName("availability_message") val availabilityMessage: String? = null,
    @SerialName("abuse_type") val abuseType: String? = null,
    @SerialName("abuse_reporter_name") val abuseReporterName: String? = null,
    @SerialName("can_download") val canDownload: Boolean? = null,
    @SerialName("show_ads") val showAds: Boolean? = null,
    @SerialName("allow_video_player") val allowVideoPlayer: Boolean? = null,
    @SerialName("download_speed_limit") val downloadSpeedLimit: Long? = null // Assuming Long for speed limit in B/s
)

class PixeldrainApiService {

    private val client = HttpClient(CIO) {
        engine {
            // Explicit engine configuration block (can be empty)
        }
        install(ContentNegotiation) {
            val jsonFormatter = Json {
                prettyPrint = true
                isLenient = true
                ignoreUnknownKeys = true
            }
            json(jsonFormatter, contentType = ContentType.Application.Json)
            json(jsonFormatter, contentType = ContentType.Text.Plain) // For error responses that might be plain text
        }
        install(HttpTimeout) {
            requestTimeoutMillis = HttpTimeout.INFINITE_TIMEOUT_MS // Allow very long total request time for uploads/downloads
            connectTimeoutMillis = 30000L  // 30 seconds for initial connection
            socketTimeoutMillis = 900000L   // 15 minutes for inactivity between data packets
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
                    path("api/file", fileName)
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
                    path("api/file", fileName)
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
                            contentResolver.openInputStream(fileUri)?.use { inputStream ->
                                inputStream.copyTo(this) 
                            } ?: throw IOException("Failed to open input stream for URI after initial check.")
                        },
                        contentType = ContentType.parse(mimeType),
                        contentLength = fileSize 
                    )
                )
                onUpload { bytesSentTotal, contentLength ->
                    onProgress(bytesSentTotal, contentLength ?: fileSize)
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

    suspend fun getFileInfo(fileId: String): ApiResponse<FileInfoResponse> {
        return try {
            val response: HttpResponse = client.get {
                url {
                    protocol = URLProtocol.HTTPS
                    host = "pixeldrain.com"
                    path("api/file", fileId, "info")
                }
            }
            if (response.status == HttpStatusCode.OK) {
                ApiResponse.Success(response.body<FileInfoResponse>())
            } else {
                ApiResponse.Error(response.body<FileUploadResponse>())
            }
        } catch (e: Exception) {
            Log.e("PIXEL_API_SERVICE", "Exception for GET file info: ${e.message}", e)
            val errorMsg = e.message ?: "Network request failed or failed to parse error response"
            ApiResponse.Error(FileUploadResponse(success = false, value = "network_exception_file_info", message = errorMsg))
        }
    }

    suspend fun downloadFileBytes(
        fileId: String,
        onProgress: (bytesRead: Long, totalBytes: Long?) -> Unit
    ): ApiResponse<ByteArray> {
        if (fileId.isBlank()) {
            return ApiResponse.Error(FileUploadResponse(success = false, value = "file_id_missing", message = "File ID is required to download a file."))
        }
        return try {
            val response: HttpResponse = client.get {
                url {
                    protocol = URLProtocol.HTTPS
                    host = "pixeldrain.com"
                    path("api/file", fileId) // Direct file download endpoint
                }
                // The onDownload block in the request scope is used to monitor the progress of receiving the response body.
                onDownload { bytesSentTotal, contentLength ->
                    onProgress(bytesSentTotal, contentLength)
                }
            }

            if (response.status == HttpStatusCode.OK) {
                // body<ByteArray>() will read the entire response body into memory.
                // The onDownload listener should have been invoked during this process by Ktor.
                val bytes = response.body<ByteArray>()
                // If onDownload wasn't precise or if we want a final confirmation:
                // onProgress(bytes.size.toLong(), bytes.size.toLong()) // This might be redundant if onDownload works as expected
                ApiResponse.Success(bytes)
            } else {
                try {
                    // Attempt to parse error response as JSON, common for Pixeldrain API
                    ApiResponse.Error(response.body<FileUploadResponse>())
                } catch (e: Exception) {
                    // Fallback if error response is not the expected JSON structure
                    ApiResponse.Error(FileUploadResponse(success = false, value = "download_failed_status_${response.status.value}", message = "Download failed: ${response.status.description}"))
                }
            }
        } catch (e: Exception) {
            Log.e("PIXEL_API_SERVICE", "Exception during GET file download: ${e.message}", e)
            val errorMsg = e.message ?: "Network request failed or failed to parse response"
            ApiResponse.Error(FileUploadResponse(success = false, value = "network_exception_file_download", message = errorMsg))
        }
    }

    suspend fun getUserFiles(apiKey: String): ApiResponse<UserFilesListResponse> {
        if (apiKey.isBlank()) {
            return ApiResponse.Error(FileUploadResponse(success = false, value = "api_key_missing", message = "API Key is required to fetch user files."))
        }
        val basicAuth = "Basic " + Base64.encodeToString(":$apiKey".toByteArray(), Base64.NO_WRAP)
        return try {
            val response: HttpResponse = client.get {
                url {
                    protocol = URLProtocol.HTTPS
                    host = "pixeldrain.com"
                    path("api/user/files")
                }
                headers {
                    append(HttpHeaders.Authorization, basicAuth)
                }
            }
            if (response.status == HttpStatusCode.OK) {
                ApiResponse.Success(response.body<UserFilesListResponse>())
            } else {
                ApiResponse.Error(response.body<FileUploadResponse>())
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
            val response: HttpResponse = client.delete {
                url {
                    protocol = URLProtocol.HTTPS
                    host = "pixeldrain.com"
                    path("api/file", fileId)
                }
                headers {
                    append(HttpHeaders.Authorization, basicAuth)
                }
            }
            val responseBody = response.body<FileUploadResponse>()
            if (responseBody.success) { 
                ApiResponse.Success(responseBody)
            } else {
                ApiResponse.Error(responseBody)
            }
        } catch (e: Exception) {
            Log.e("PIXEL_API_SERVICE", "Exception during DELETE file: ${e.message}", e)
            val errorMsg = e.message ?: "Network request failed or failed to parse error/success response"
            ApiResponse.Error(FileUploadResponse(success = false, value = "network_exception_delete_file", message = errorMsg))
        }
    }
}
