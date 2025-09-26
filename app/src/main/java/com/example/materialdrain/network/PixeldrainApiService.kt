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
import io.ktor.client.request.url
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.path
import io.ktor.http.URLProtocol
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
    // date_upload will be `created` for filesystem entries. We can map if needed in ViewModel.
    @SerialName("date_last_view") val dateLastView: String? = null,
    @SerialName("mime_type") val mimeType: String? = null, // More specific than fileType for files
    @SerialName("thumbnail_href") val thumbnailHref: String? = null,
    // hash_sha256 is redundant with sha256_sum, keeping one from the example
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
    // For potential error message directly in this response type
    val success: Boolean? = null, 
    val value: String? = null, 
    val message: String? = null
)

class PixeldrainApiService(private val appContext: Context? = null) {

    private val STREAM_BUFFER_SIZE = 1024 * 1024

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
            json(jsonFormatter, contentType = ContentType.Text.Plain) // Allow plain text for some error responses
        }
        install(HttpTimeout) {
            requestTimeoutMillis = HttpTimeout.INFINITE_TIMEOUT_MS
            connectTimeoutMillis = 30000L
            socketTimeoutMillis = 900000L // 15 minutes for socket timeout
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
            // exponentialDelay() // Consider adding delay if needed
        }
    }

    suspend fun uploadFile(
        apiKey: String,
        fileName: String,
        fileBytes: ByteArray,
        onProgress: (bytesSent: Long, totalBytes: Long?) -> Unit
    ): FileUploadResponse { // Return FileUploadResponse for consistency
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
                response.body<FileUploadResponse>() // Assume errors conform to FileUploadResponse
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
    ): FileUploadResponse { // Return FileUploadResponse for consistency
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
                    // append(HttpHeaders.ContentType, mimeType) // Ktor sets this from OutputStreamContent
                }
                setBody(
                    OutputStreamContent(
                        body = {
                            contentResolver.openInputStream(fileUri)?.use { rawInputStream ->
                                BufferedInputStream(rawInputStream).use { bufferedInputStream ->
                                    bufferedInputStream.copyTo(this, bufferSize = STREAM_BUFFER_SIZE)
                                }
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
            client.prepareGet {
                url {
                    protocol = URLProtocol.HTTPS
                    host = "pixeldrain.com"
                    path("api/file", fileId, "info")
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
        val actualPath = fsPath.ifBlank { "me" } // Default to "me" if blank
    
        // Construct the full URL string
        val urlString = "https://pixeldrain.com/api/filesystem/$actualPath?stats=true"

        return try {
            client.prepareGet {
                url(urlString) // Use the fully constructed URL string here
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
                // No onDownload here for manual streaming
            }.execute { httpResponse: HttpResponse ->
                if (httpResponse.status == HttpStatusCode.OK) {
                    val inputStream: InputStream = httpResponse.body() // Get as stream
                    val totalBytesFromServer = httpResponse.headers[HttpHeaders.ContentLength]?.toLongOrNull()
                    var totalBytesCopied = 0L
                    val buffer = ByteArray(STREAM_BUFFER_SIZE) // Use defined buffer size
                    var bytesRead: Int
                    try {
                        inputStream.use { netStream ->
                            while (netStream.read(buffer).also { bytesRead = it } != -1) {
                                outputStream.write(buffer, 0, bytesRead)
                                totalBytesCopied += bytesRead
                                onProgress(totalBytesCopied, totalBytesFromServer)
                            }
                        }
                        outputStream.flush() // Ensure all data is written
                        ApiResponse.Success(totalBytesCopied)
                    } catch (e: IOException) {
                        Log.e("PIXEL_API_SERVICE", "IOException during stream copy: ${e.message}", e)
                        ApiResponse.Error(FileUploadResponse(success = false, value = "download_stream_copy_error", message = e.message ?: "Error copying download stream"))
                    }
                } else {
                    try {
                        val errorBody = httpResponse.body<FileUploadResponse>()
                        ApiResponse.Error(errorBody)
                    } catch (e: Exception) {
                        Log.e("PIXEL_API_SERVICE", "Failed to parse error body for download: ${e.message}")
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
    
    suspend fun downloadFileBytes(
        fileId: String,
        onProgress: (bytesRead: Long, totalBytes: Long?) -> Unit
    ): ApiResponse<ByteArray> {
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
                onDownload { bytesSentTotal, contentLength -> onProgress(bytesSentTotal, contentLength) }
            }.execute { response: HttpResponse ->
                if (response.status == HttpStatusCode.OK) {
                    val bytes = response.body<ByteArray>()
                    ApiResponse.Success(bytes)
                } else {
                    try {
                        ApiResponse.Error(response.body<FileUploadResponse>())
                    } catch (e: Exception) {
                        // Fallback if error body is not FileUploadResponse format
                        ApiResponse.Error(FileUploadResponse(success = false, value = "download_failed_status_${response.status.value}", message = "Download failed: ${response.status.description}"))
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("PIXEL_API_SERVICE", "Exception during GET file download: ${e.message}", e)
            val errorMsg = e.message ?: "Network request failed or failed to parse response"
            ApiResponse.Error(FileUploadResponse(success = false, value = "network_exception_file_download", message = errorMsg))
        }
    }

    suspend fun downloadFileWithRanges(
        fileId: String,
        outputFile: File,
        partCount: Int = 4, 
        onProgress: (bytesDownloaded: Long, totalBytes: Long?) -> Unit
    ): ApiResponse<Long> {
        if (fileId.isBlank()) {
            return ApiResponse.Error(FileUploadResponse(success = false, value = "file_id_missing", message = "File ID is required to download a file."))
        }

        val headResponse = try {
            client.prepareGet {
                url {
                    protocol = URLProtocol.HTTPS
                    host = "pixeldrain.com"
                    path("api/file", fileId) 
                }
            }.execute { it } 
        } catch (e: Exception) {
            Log.e("PIXEL_API_SERVICE", "Failed to probe file headers: ${e.message}", e)
            return ApiResponse.Error(FileUploadResponse(success = false, value = "network_exception_probe", message = e.message ?: "Failed to probe file"))
        }

        if (headResponse.status != HttpStatusCode.OK) {
            return try {
                ApiResponse.Error(headResponse.body())
            } catch (e: Exception) {
                ApiResponse.Error(FileUploadResponse(success = false, value = "probe_failed_status_${headResponse.status.value}", message = headResponse.status.description))
            }
        }

        val totalSize = headResponse.headers[HttpHeaders.ContentLength]?.toLongOrNull()
        val acceptRanges = headResponse.headers["Accept-Ranges"]?.lowercase() ?: ""

        if (totalSize == null || !acceptRanges.contains("bytes")) {
            Log.i("PIXEL_API_SERVICE", "Range unsupported or size unknown fall back to single-stream download.")
            try {
                FileOutputStream(outputFile).use { fos -> 
                    return downloadFileToOutputStream(fileId, fos, onProgress)
                }
            } catch (e: Exception) {
                Log.e("PIXEL_API_SERVICE", "Fallback download failed: ${e.message}", e)
                return ApiResponse.Error(FileUploadResponse(success = false, value = "fallback_download_failed", message = e.message ?: "Fallback failed"))
            }
        }

        val partSize = totalSize / partCount
        val ranges = (0 until partCount).map {
            val start = it * partSize
            val end = if (it == partCount - 1) totalSize - 1 else (start + partSize - 1)
            start to end
        }

        val tempFiles = List(partCount) { idx -> 
            File.createTempFile("\${outputFile.nameWithoutExtension}.part$idx", ".tmp", outputFile.parentFile)
        }
        
        var totalDownloadedSoFar = 0L
        val progressLock = Any()

        return try {
            coroutineScope {
                val deferreds = ranges.mapIndexed { idx, (start, end) ->
                    async(Dispatchers.IO) {
                        client.prepareGet {
                            url {
                                protocol = URLProtocol.HTTPS
                                host = "pixeldrain.com"
                                path("api/file", fileId)
                            }
                            headers {
                                append("Range", "bytes=$start-$end")
                                append(HttpHeaders.UserAgent, "Mozilla/5.0 (Android) Ktor/OkHttp MaterialDrainApp")
                            }
                        }.execute { resp ->
                            if (resp.status == HttpStatusCode.PartialContent || resp.status == HttpStatusCode.OK /* Some servers might send OK for single part */) {
                                tempFiles[idx].outputStream().use { fos ->
                                    resp.body<InputStream>().use { input ->
                                        val buffer = ByteArray(STREAM_BUFFER_SIZE)
                                        var read: Int
                                        var localPartDownloaded = 0L
                                        while (input.read(buffer).also { read = it } != -1) {
                                            fos.write(buffer, 0, read)
                                            localPartDownloaded += read
                                            synchronized(progressLock) {
                                                totalDownloadedSoFar += read
                                                onProgress(totalDownloadedSoFar, totalSize)
                                            }
                                        }
                                    }
                                }
                                true // Part download success
                            } else {
                                Log.e("PIXEL_API_SERVICE", "Range download failed for part $idx: status ${resp.status.value}")
                                false // Part download failed
                            }
                        }
                    }
                }
                val results = deferreds.awaitAll()
                if (!results.all { it }) {
                    throw IOException("One or more part downloads failed. Check logs for details.")
                }
            }

            withContext(Dispatchers.IO) {
                FileOutputStream(outputFile, false).use { finalOut -> // Overwrite if exists
                    tempFiles.forEach { partFile ->
                        partFile.inputStream().use { partIn ->
                            partIn.copyTo(finalOut, bufferSize = STREAM_BUFFER_SIZE)
                        }
                        try { partFile.delete() } catch (e: Exception) { Log.w("PIXEL_API_SERVICE", "Failed to delete temp part: ${partFile.name}", e) }
                    }
                }
            }
            ApiResponse.Success(totalSize)
        } catch (e: Exception) {
            Log.e("PIXEL_API_SERVICE", "Parallel range download failed: ${e.message}", e)
            tempFiles.forEach { try { if (it.exists()) it.delete() } catch (ignored: Exception) {} }
            ApiResponse.Error(FileUploadResponse(success = false, value = "parallel_download_failed", message = e.message ?: "Parallel download failed"))
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
                // Consider adding Accept: text/plain if server respects it, though /api/file/{id} is usually direct download
            }.execute { response: HttpResponse ->
                if (response.status == HttpStatusCode.OK) {
                    val textContent = response.body<String>()
                    ApiResponse.Success(textContent)
                } else {
                    try {
                        ApiResponse.Error(response.body<FileUploadResponse>())
                    } catch (e: Exception) {
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

    suspend fun downloadFileToOutputStreamSimple(
        fileId: String,
        outputStream: java.io.OutputStream
    ): ApiResponse<Long> {
        return downloadFileToOutputStream(fileId, outputStream) { _, _ -> /* No-op progress */ }
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
                // Pixeldrain delete API returns 200 OK with a body indicating success/failure
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
