package com.example.materialdrain.network

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
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
import io.ktor.client.plugins.onUpload
import io.ktor.client.request.delete // Added for client.delete
import io.ktor.client.request.get    // Added for client.get
import io.ktor.client.request.headers
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.URLProtocol
import io.ktor.http.content.OutputStreamContent
import io.ktor.http.path
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.BufferedInputStream
import java.io.IOException
import java.io.InputStream

sealed class ApiResponse<T> {
    data class Success<T>(val data: T) : ApiResponse<T>()
    data class Error<T>(val errorDetails: FileUploadResponse) : ApiResponse<T>()
}

@Serializable
data class FileUploadResponse(
    val success: Boolean,
    val id: String? = null,
    val value: String? = null,
    val message: String? = null
)

@Serializable
data class FileUploadPutSuccessResponse(val id: String)

@Serializable
data class UserFilesListResponse(val files: List<FileInfoResponse>)

@Serializable
data class FileInfoResponse(
    val id: String, val name: String, val size: Long, val views: Int? = null,
    @SerialName("bandwidth_used") val bandwidthUsed: Long? = null,
    @SerialName("bandwidth_used_paid") val bandwidthUsedPaid: Long? = null,
    val downloads: Int? = null, @SerialName("date_upload") val dateUpload: String,
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

class PixeldrainApiService {
    private val client = HttpClient(CIO) {
        engine {}
        install(ContentNegotiation) {
            val jsonFormatter = Json { prettyPrint = true; isLenient = true; ignoreUnknownKeys = true }
            json(jsonFormatter, contentType = ContentType.Application.Json)
            json(jsonFormatter, contentType = ContentType.Text.Plain) // For errors
        }
        install(HttpTimeout) { requestTimeoutMillis = HttpTimeout.INFINITE_TIMEOUT_MS; connectTimeoutMillis = 30000L; socketTimeoutMillis = 900000L }
        install(Logging) { logger = object : Logger { override fun log(message: String) { Log.d("KTOR_HTTP_CLIENT", message) } }; level = LogLevel.HEADERS }
        install(HttpRequestRetry) { retryOnServerErrors(maxRetries = 2); retryOnExceptionIf { _, cause -> cause is IOException && cause !is java.net.SocketTimeoutException } }
    }

    suspend fun uploadFile(apiKey: String, fileName: String, fileBytes: ByteArray, onProgress: (bytesSent: Long, totalBytes: Long?) -> Unit): FileUploadResponse {
        val basicAuth = "Basic " + Base64.encodeToString(":$apiKey".toByteArray(), Base64.NO_WRAP)
        return try {
            val response: HttpResponse = client.put {
                url { protocol = URLProtocol.HTTPS; host = "pixeldrain.com"; path("api/file", fileName) }
                headers { append(HttpHeaders.Authorization, basicAuth); append(HttpHeaders.ContentType, ContentType.Application.OctetStream.toString()); append(HttpHeaders.ContentLength, fileBytes.size.toString()) }
                setBody(fileBytes)
                onUpload { bytesSentTotal, contentLength -> onProgress(bytesSentTotal, contentLength) }
            }
            if (response.status == HttpStatusCode.Created) {
                val successBody = response.body<FileUploadPutSuccessResponse>()
                FileUploadResponse(success = true, id = successBody.id)
            } else { response.body<FileUploadResponse>() }
        } catch (e: Exception) { Log.e("PIXEL_API_SERVICE", "PUT byte array upload exception: ${e.message}", e); FileUploadResponse(false, value = "net_exc_upload_bytearray", message = e.message ?: "Network error") }
    }

    suspend fun uploadFileFromUri(apiKey: String, fileName: String, fileUri: Uri, context: Context, onProgress: (bytesSent: Long, totalBytes: Long?) -> Unit): FileUploadResponse {
        val cr = context.contentResolver
        val basicAuth = "Basic " + Base64.encodeToString(":$apiKey".toByteArray(), Base64.NO_WRAP)
        val mime = cr.getType(fileUri) ?: ContentType.Application.OctetStream.toString()
        var fileSize: Long? = null
        try { cr.query(fileUri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { if (it.moveToFirst() && !it.isNull(it.getColumnIndex(OpenableColumns.SIZE))) fileSize = it.getLong(it.getColumnIndex(OpenableColumns.SIZE)) } } catch (e: Exception) { Log.w("PIXEL_API_SERVICE", "Size query error: ${e.message}") }
        return try {
            val response: HttpResponse = client.put {
                url { protocol = URLProtocol.HTTPS; host = "pixeldrain.com"; path("api/file", fileName) }
                headers { append(HttpHeaders.Authorization, basicAuth); fileSize?.let { append(HttpHeaders.ContentLength, it.toString()) } }
                setBody(OutputStreamContent( body = { cr.openInputStream(fileUri)?.use { BufferedInputStream(it).copyTo(this) } ?: throw IOException("Failed to open URI stream") }, contentType = ContentType.parse(mime), contentLength = fileSize ) )
                onUpload { bytesSent, total -> onProgress(bytesSent, total ?: fileSize) }
            }
            if (response.status == HttpStatusCode.Created) {
                val successBody = response.body<FileUploadPutSuccessResponse>()
                FileUploadResponse(success = true, id = successBody.id)
            } else { response.body<FileUploadResponse>() }
        } catch (e: Exception) { Log.e("PIXEL_API_SERVICE", "PUT URI upload exception: ${e.message}", e); FileUploadResponse(false, value = "net_exc_upload_uri", message = e.message ?: "Network error") }
    }

    suspend fun getFileInfo(fileId: String): ApiResponse<FileInfoResponse> {
        return try {
            val response: HttpResponse = client.get {
                url { protocol = URLProtocol.HTTPS; host = "pixeldrain.com"; path("api/file", fileId, "info") }
            }
            if (response.status == HttpStatusCode.OK) ApiResponse.Success(response.body())
            else ApiResponse.Error(response.body())
        } catch (e: Exception) { Log.e("PIXEL_API_SERVICE", "GET file info exception: ${e.message}", e); ApiResponse.Error(FileUploadResponse(false, value = "net_exc_file_info", message = e.message ?: "Network error")) }
    }

    suspend fun downloadFileToOutputStream(fileId: String, outputStream: java.io.OutputStream, onProgress: (bytesRead: Long, totalBytes: Long?) -> Unit): ApiResponse<Long> {
        if (fileId.isBlank()) return ApiResponse.Error(FileUploadResponse(false, value = "file_id_missing", message = "File ID required"))
        return try {
            val response: HttpResponse = client.get {
                url { protocol = URLProtocol.HTTPS; host = "pixeldrain.com"; path("api/file", fileId) }
                // onDownload can be used here if needed, or manual progress as below
            }
            if (response.status == HttpStatusCode.OK) {
                val inputStream: InputStream = response.body() // Get InputStream
                val totalBytesServer = response.headers[HttpHeaders.ContentLength]?.toLongOrNull()
                var copied = 0L; val buf = ByteArray(8192); var read: Int
                try {
                    inputStream.use { netIn -> while (netIn.read(buf).also { read = it } != -1) { outputStream.write(buf, 0, read); copied += read; onProgress(copied, totalBytesServer) } }
                    outputStream.flush()
                    ApiResponse.Success(copied)
                } catch (ioe: IOException) { Log.e("PIXEL_API_SERVICE", "Stream copy IOExc: ${ioe.message}", ioe); ApiResponse.Error(FileUploadResponse(false, value = "dl_stream_copy_err", message = ioe.message ?: "Stream copy error")) }
            } else {
                try { ApiResponse.Error(response.body<FileUploadResponse>()) }
                catch (e: Exception) { Log.e("PIXEL_API_SERVICE", "DL error body parse failed: ${e.message}"); ApiResponse.Error(FileUploadResponse(false, value = "dl_err_parse_fail_s${response.status.value}", message = "DL failed: ${response.status.description}")) }
            }
        } catch (e: Exception) { Log.e("PIXEL_API_SERVICE", "GET DL stream exception: ${e.message}", e); ApiResponse.Error(FileUploadResponse(false, value = "net_exc_dl_stream", message = e.message ?: "Network error")) }
    }

    suspend fun getFileContentAsText(fileId: String): ApiResponse<String> {
        if (fileId.isBlank()) return ApiResponse.Error(FileUploadResponse(false, value = "file_id_missing", message = "File ID required"))
        return try {
            val response: HttpResponse = client.get {
                url { protocol = URLProtocol.HTTPS; host = "pixeldrain.com"; path("api/file", fileId) }
            }
            if (response.status == HttpStatusCode.OK) ApiResponse.Success(response.body())
            else { try { ApiResponse.Error(response.body<FileUploadResponse>()) } catch (e:Exception) { ApiResponse.Error(FileUploadResponse(false, value="get_text_err_s${response.status.value}", message="Failed get text: ${response.status.description}")) } }
        } catch (e: Exception) { Log.e("PIXEL_API_SERVICE", "GET text content exception: ${e.message}", e); ApiResponse.Error(FileUploadResponse(false, value = "net_exc_get_text", message = e.message ?: "Network error")) }
    }
 
    // getUserFiles uses client.get
    suspend fun getUserFiles(apiKey: String): ApiResponse<UserFilesListResponse> {
        if (apiKey.isBlank()) return ApiResponse.Error(FileUploadResponse(false, value = "api_key_missing", message = "API Key required"))
        val basicAuth = "Basic " + Base64.encodeToString(":$apiKey".toByteArray(), Base64.NO_WRAP)
        return try {
            val response: HttpResponse = client.get {
                url { protocol = URLProtocol.HTTPS; host = "pixeldrain.com"; path("api/user/files") }
                headers { append(HttpHeaders.Authorization, basicAuth) }
            }
            if (response.status == HttpStatusCode.OK) ApiResponse.Success(response.body())
            else ApiResponse.Error(response.body())
        } catch (e: Exception) { Log.e("PIXEL_API_SERVICE", "GET user files exception: ${e.message}", e); ApiResponse.Error(FileUploadResponse(false, value = "net_exc_user_files", message = e.message ?: "Network error")) }
    }

    // deleteFile uses client.delete
    suspend fun deleteFile(apiKey: String, fileId: String): ApiResponse<FileUploadResponse> {
        if (apiKey.isBlank()) return ApiResponse.Error(FileUploadResponse(false, value = "api_key_missing", message = "API Key required for delete"))
        if (fileId.isBlank()) return ApiResponse.Error(FileUploadResponse(false, value = "file_id_missing", message = "File ID required for delete"))
        val basicAuth = "Basic " + Base64.encodeToString(":$apiKey".toByteArray(), Base64.NO_WRAP)
        return try {
            val response: HttpResponse = client.delete {
                url { protocol = URLProtocol.HTTPS; host = "pixeldrain.com"; path("api/file", fileId) }
                headers { append(HttpHeaders.Authorization, basicAuth) }
            }
            // Pixeldrain delete returns a body with success:true/false according to docs, check status and body
            if (response.status == HttpStatusCode.OK || response.status == HttpStatusCode.Accepted) {
                val responseBody = response.body<FileUploadResponse>() // Assuming it always returns this structure
                if (responseBody.success) ApiResponse.Success(responseBody)
                else ApiResponse.Error(responseBody) // Use body's success field
            } else {
                 try {ApiResponse.Error(response.body<FileUploadResponse>()) } // Try to parse error body
                 catch(e:Exception) {ApiResponse.Error(FileUploadResponse(false, value="del_fail_s${response.status.value}", message="Delete failed: ${response.status.description}"))}
            }
        } catch (e: Exception) { Log.e("PIXEL_API_SERVICE", "DELETE file exception: ${e.message}", e); ApiResponse.Error(FileUploadResponse(false, value = "net_exc_delete_file", message = e.message ?: "Network error")) }
    }
}
