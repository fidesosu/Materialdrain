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
    data class Error<T>(val errorDetails: FileUploadResponse) : ApiResponse<T>()
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

class PixeldrainApiService(private val appContext: Context? = null) {

    // Larger buffer used across streaming operations (1 MB)
    private val STREAM_BUFFER_SIZE = 1024 * 1024

    /**
     * OkHttp client used by Ktor.
     * OkHttp supports HTTP/2 automatically (ALPN) where the server and platform allow it.
     * If you want to use Cronet (HTTP/3), see notes below (extra setup).
     */
    private val client = HttpClient(OkHttp) {
        engine {
            // configure the underlying OkHttp client
            preconfigured = OkHttpClient.Builder()
                // prefer HTTP/2 and HTTP/1.1; OkHttp will negotiate using ALPN
                .protocols(listOf(Protocol.HTTP_2, Protocol.HTTP_1_1))
                // timeouts — tune as appropriate
                .connectTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(0, TimeUnit.SECONDS) // 0 for infinite (careful)
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

    // --- (existing uploads) -- keep them but use larger buffer for streaming copyTo where applicable ---

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
                            contentResolver.openInputStream(fileUri)?.use { rawInputStream ->
                                BufferedInputStream(rawInputStream).use { bufferedInputStream ->
                                    // use large buffer for copyTo
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

    // --- FILE INFO / OTHER unchanged ---

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

    // --- improved streaming single-stream download with larger buffer ---
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
                    val buffer = ByteArray(STREAM_BUFFER_SIZE)
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

    // --- download bytes (single call) with onDownload progress hookup ---
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

    // --- PARALLEL RANGE DOWNLOAD (new):
    /**
     * Downloads a file using HTTP Range requests in N parts concurrently and writes final content to `outputFile`.
     * - PartCount: number of concurrent parts to download. Typical good values: 4..8.
     * - This function will:
     *   1. HEAD/GET to determine support for ranges and content-length
     *   2. If range supported and size known: download parts concurrently and merge
     *   3. If not supported: fallback to single-stream download
     */
    suspend fun downloadFileWithRanges(
        fileId: String,
        outputFile: File,
        partCount: Int = 4,
        onProgress: (bytesDownloaded: Long, totalBytes: Long?) -> Unit
    ): ApiResponse<Long> {
        if (fileId.isBlank()) {
            return ApiResponse.Error(FileUploadResponse(success = false, value = "file_id_missing", message = "File ID is required to download a file."))
        }

        // 1) Probe file headers to get size and Accept-Ranges support
        val headResponse = try {
            client.prepareGet {
                url {
                    protocol = URLProtocol.HTTPS
                    host = "pixeldrain.com"
                    path("api/file", fileId)
                }
            }.execute { it } // we only need headers
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
            // Server doesn't support range requests or size unknown — fallback to single-stream download
            Log.i("PIXEL_API_SERVICE", "Range unsupported or size unknown — falling back to single-stream download.")
            // use temporary file and stream into it
            try {
                FileOutputStream(outputFile).use { fos ->
                    val single = downloadFileToOutputStream(fileId, fos) { downloaded, total ->
                        onProgress(downloaded, total)
                    }
                    return single
                }
            } catch (e: Exception) {
                Log.e("PIXEL_API_SERVICE", "Fallback download failed: ${e.message}", e)
                return ApiResponse.Error(FileUploadResponse(success = false, value = "fallback_download_failed", message = e.message ?: "Fallback failed"))
            }
        }

        // 2) Partition the byte ranges
        val partSize = totalSize / partCount
        val ranges = (0 until partCount).map { idx ->
            val start = idx * partSize
            val end = if (idx == partCount - 1) totalSize - 1 else (start + partSize - 1)
            start to end
        }

        // 3) Create temp files and download concurrently using coroutines
        val tempFiles = List(partCount) { idx ->
            File(outputFile.parentFile, "${outputFile.name}.part$idx")
        }

        return try {
            var totalDownloadedSoFar = 0L
            // run concurrent downloads
            coroutineScope {
                val deferred = ranges.mapIndexed { idx, (start, end) ->
                    async(Dispatchers.IO) {
                        client.prepareGet {
                            url {
                                protocol = URLProtocol.HTTPS
                                host = "pixeldrain.com"
                                path("api/file", fileId)
                            }
                            headers {
                                append("Range", "bytes=$start-$end")
                                // Optionally append a browser-like User-Agent if you want to test server throttling
                                append(HttpHeaders.UserAgent, "Mozilla/5.0 (Android) Ktor/OkHttp")
                            }
                        }.execute { resp ->
                            if (resp.status == HttpStatusCode.PartialContent || resp.status == HttpStatusCode.OK) {
                                // write to temp file with large buffer
                                tempFiles[idx].outputStream().use { fos ->
                                    resp.body<InputStream>().use { input ->
                                        val buffer = ByteArray(STREAM_BUFFER_SIZE)
                                        var read: Int
                                        var localDownloaded = 0L
                                        while (input.read(buffer).also { read = it } != -1) {
                                            fos.write(buffer, 0, read)
                                            localDownloaded += read
                                            // update aggregate progress (not exact real-time but useful)
                                            synchronized(this@PixeldrainApiService) {
                                                totalDownloadedSoFar += read
                                                onProgress(totalDownloadedSoFar, totalSize)
                                            }
                                        }
                                        fos.flush()
                                    }
                                }
                                true
                            } else {
                                Log.e("PIXEL_API_SERVICE", "Range download failed for part $idx: status ${resp.status}")
                                false
                            }
                        }
                    }
                }
                // wait for all
                val results = deferred.awaitAll()
                if (!results.all { it }) throw IOException("One or more part downloads failed")
            }

            // 4) Merge temp files into outputFile
            withContext(Dispatchers.IO) {
                FileOutputStream(outputFile, false).use { finalOut ->
                    tempFiles.forEach { part ->
                        part.inputStream().use { partIn ->
                            partIn.copyTo(finalOut, bufferSize = STREAM_BUFFER_SIZE)
                        }
                        // delete temp part after merging
                        try {
                            part.delete()
                        } catch (ignored: Exception) {}
                    }
                }
            }

            ApiResponse.Success(totalSize)
        } catch (e: Exception) {
            Log.e("PIXEL_API_SERVICE", "Parallel range download failed: ${e.message}", e)
            // cleanup temp files if exist
            tempFiles.forEach { try { if (it.exists()) it.delete() } catch (_: Exception) {} }
            ApiResponse.Error(FileUploadResponse(success = false, value = "parallel_download_failed", message = e.message ?: "Parallel download failed"))
        }
    }

    // --- remaining APIs unchanged: getFileContentAsText, getUserFiles, deleteFile ---

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
        // convenience wrapper: keeps original signature if you used this name elsewhere
        return downloadFileToOutputStream(fileId, outputStream) { _, _ -> }
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
