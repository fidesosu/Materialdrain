package com.example.materialdrain.network

import android.content.Context
import android.net.Uri
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
            requestTimeoutMillis = 60000 // 1 minute for general requests
            connectTimeoutMillis = 15000  // 15 seconds
            socketTimeoutMillis = 15000   // 15 seconds
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
            retryOnServerErrors(maxRetries = 3)
            retryOnExceptionIf { _, cause ->
                cause is IOException || cause is java.util.concurrent.TimeoutException
            }
        }
    }

    suspend fun uploadFile(
        apiKey: String,
        fileName: String,
        fileBytes: ByteArray
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
                }
                setBody(fileBytes)
                // timeout { requestTimeoutMillis = 300000 } // Removed to fix compilation error
            }
            if (response.status == HttpStatusCode.Created) {
                response.body<FileUploadPutSuccessResponse>()
                FileUploadResponse(success = true, id = response.body<FileUploadPutSuccessResponse>().id)
            } else {
                response.body<FileUploadResponse>()
            }
        } catch (e: Exception) {
            Log.e("PIXEL_API_SERVICE", "Exception during PUT text upload: ${e.message}", e)
            FileUploadResponse(success = false, value = "network_exception_upload_text", message = e.message ?: "Network request failed")
        }
    }

    suspend fun uploadFileFromUri(
        apiKey: String,
        fileName: String,
        fileUri: Uri,
        context: Context
    ): FileUploadResponse {
        val contentResolver = context.contentResolver
        val inputStream = contentResolver.openInputStream(fileUri)
            ?: return FileUploadResponse(success = false, value = "uri_error", message = "Failed to open input stream for URI.")
        val fileBytes = inputStream.use { it.readBytes() }
        val basicAuth = "Basic " + Base64.encodeToString(":$apiKey".toByteArray(), Base64.NO_WRAP)
        val mimeType = contentResolver.getType(fileUri) ?: ContentType.Application.OctetStream.toString()

        return try {
            val response: HttpResponse = client.put {
                url {
                    protocol = URLProtocol.HTTPS
                    host = "pixeldrain.com"
                    path("api/file", fileName)
                }
                headers {
                    append(HttpHeaders.Authorization, basicAuth)
                    append(HttpHeaders.ContentType, mimeType)
                }
                setBody(fileBytes)
                // timeout { requestTimeoutMillis = 300000 } // Removed to fix compilation error
            }
            if (response.status == HttpStatusCode.Created) {
                val successBody = response.body<FileUploadPutSuccessResponse>()
                FileUploadResponse(success = true, id = successBody.id)
            } else {
                response.body<FileUploadResponse>()
            }
        } catch (e: Exception) {
            Log.e("PIXEL_API_SERVICE", "Exception during PUT URI upload: ${e.message}", e)
            FileUploadResponse(success = false, value = "network_exception_upload_uri", message = e.message ?: "Network request failed")
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
}
