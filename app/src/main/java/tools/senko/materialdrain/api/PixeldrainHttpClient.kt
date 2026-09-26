package tools.senko.materialdrain.api

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import android.util.Log
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody
import okio.BufferedSink
import okio.buffer
import okio.sink
import okio.source
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.TimeUnit

internal const val PIXELDRAIN_HOST = "pixeldrain.com"
private const val TAG_HTTP = "PIXEL_HTTP"
private const val PROGRESS_REPORT_INTERVAL_NANOS = 200_000_000L
private const val TRANSFER_CHUNK_BYTES = 64L * 1024

/** Calls [report] at most every [PROGRESS_REPORT_INTERVAL_NANOS], plus once explicitly via [finish]. */
internal class ThrottledProgress(private val report: (Long) -> Unit) {
    private var lastReportNanos = 0L

    fun update(bytes: Long) {
        val now = System.nanoTime()
        if (now - lastReportNanos >= PROGRESS_REPORT_INTERVAL_NANOS) {
            lastReportNanos = now
            report(bytes)
        }
    }

    fun finish(bytes: Long) = report(bytes)
}

/** Streams an InputStream straight into the OkHttp socket sink, without any intermediate channel. */
internal class StreamingRequestBody(
    private val mediaType: String?,
    private val length: Long,
    private val openStream: () -> InputStream?,
    private val onProgress: (bytesSent: Long) -> Unit
) : RequestBody() {
    override fun contentType() = mediaType?.toMediaTypeOrNull()
    override fun contentLength() = length

    override fun writeTo(sink: BufferedSink) {
        val input = openStream() ?: throw IOException("Failed to open input stream for upload.")
        val progress = ThrottledProgress(onProgress)
        var sent = 0L
        input.source().use { source ->
            while (true) {
                val read = source.read(sink.buffer, TRANSFER_CHUNK_BYTES)
                if (read == -1L) break
                sink.emitCompleteSegments()
                sent += read
                progress.update(sent)
            }
        }
        progress.finish(sent)
    }
}

internal fun queryContentSize(context: Context, uri: Uri): Long? {
    return try {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) cursor.getLong(sizeIndex) else null
            } else null
        }
    } catch (e: Exception) {
        Log.w(TAG_HTTP, "Could not determine file size for Content-Length: ${e.message}")
        null
    }
}

internal class RawResponse(val code: Int, val body: String) {
    val isSuccessful: Boolean get() = code in 200..299
}

/**
 * Shared networking used by the core, filesystem and user API implementations: one OkHttp
 * connection pool, one Ktor client for JSON calls, and the streaming/error helpers.
 */
class PixeldrainHttpClient {

    internal val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .protocols(listOf(Protocol.HTTP_2, Protocol.HTTP_1_1))
        .connectTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(0, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS)
        .build()

    internal val json = Json {
        isLenient = true
        ignoreUnknownKeys = true
    }

    internal val ktor = HttpClient(OkHttp) {
        engine {
            preconfigured = okHttpClient
        }
        install(ContentNegotiation) {
            val jsonFormatter = Json {
                prettyPrint = true
                isLenient = true
                ignoreUnknownKeys = true
            }
            json(jsonFormatter, contentType = ContentType.Application.Json)
            json(jsonFormatter, contentType = ContentType.Text.Plain) // Added for flexibility with text/plain responses
        }
        install(HttpTimeout) {
            requestTimeoutMillis = null
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
            sanitizeHeader { header -> header == HttpHeaders.Authorization || header == HttpHeaders.Cookie }
        }
        install(HttpRequestRetry) {
            retryOnServerErrors(maxRetries = 2)
            retryOnExceptionIf { _, cause ->
                cause is IOException && cause !is java.net.SocketTimeoutException
            }
        }
    }

    internal fun basicAuth(apiKey: String): String =
        "Basic " + Base64.encodeToString(":$apiKey".toByteArray(), Base64.NO_WRAP)

    /** https://pixeldrain.com/api/{segments...}; every segment is percent-encoded individually. */
    internal fun apiUrl(vararg segments: String): HttpUrl.Builder {
        val builder = HttpUrl.Builder().scheme("https").host(PIXELDRAIN_HOST).addPathSegment("api")
        segments.forEach { builder.addPathSegment(it) }
        return builder
    }

    /** Filesystem paths such as "me/photos/a.png" or "/me/photos/a.png" become individual path segments. */
    internal fun filesystemUrl(path: String): HttpUrl.Builder {
        val builder = apiUrl("filesystem")
        path.split('/').filter { it.isNotEmpty() }.forEach { builder.addPathSegment(it) }
        return builder
    }

    /** Executes the call on an IO thread; cancelling the calling coroutine aborts the socket immediately. */
    internal suspend fun <T> executeCancellable(call: Call, block: () -> T): T = coroutineScope {
        val worker = async(Dispatchers.IO) { block() }
        try {
            worker.await()
        } catch (e: CancellationException) {
            call.cancel()
            throw e
        }
    }

    /** Sends a request and buffers the whole (small) response body. Throws IOException on network errors. */
    internal suspend fun send(request: Request): RawResponse {
        val call = okHttpClient.newCall(request)
        return executeCancellable(call) {
            call.execute().use { response -> RawResponse(response.code, response.body.string()) }
        }
    }

    /** Streams the response body of [request] into [outputStream]. */
    internal suspend fun downloadToStream(
        request: Request,
        outputStream: OutputStream,
        onProgress: (bytesRead: Long, totalBytes: Long?) -> Unit
    ): ApiResponse<Long> {
        val call = okHttpClient.newCall(request)
        return try {
            executeCancellable(call) {
                call.execute().use { response ->
                    val body = response.body
                    if (response.code == 200) {
                        val totalBytesFromServer = body.contentLength().takeIf { it >= 0 }
                        val progress = ThrottledProgress { onProgress(it, totalBytesFromServer) }
                        var totalBytesCopied = 0L
                        try {
                            val sink = outputStream.sink().buffer()
                            val source = body.source()
                            while (true) {
                                val read = source.read(sink.buffer, TRANSFER_CHUNK_BYTES)
                                if (read == -1L) break
                                sink.emitCompleteSegments()
                                totalBytesCopied += read
                                progress.update(totalBytesCopied)
                            }
                            sink.flush()
                            progress.finish(totalBytesCopied)
                            ApiResponse.Success(totalBytesCopied)
                        } catch (e: IOException) {
                            Log.e(TAG_HTTP, "IOException during stream copy: ${e.message}", e)
                            ApiResponse.Error(FileUploadResponse(success = false, value = "download_stream_copy_error", message = e.message ?: "Error copying download stream"))
                        }
                    } else {
                        ApiResponse.Error(parseError(response.code, body.string(), "download_failed_status"))
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG_HTTP, "Exception during download to stream: ${e.message}", e)
            ApiResponse.Error(FileUploadResponse(success = false, value = "network_exception_file_download_stream", message = e.message ?: "Network request failed or failed to parse response"))
        }
    }

    internal fun parseError(code: Int, body: String, fallbackValue: String): FileUploadResponse =
        try {
            json.decodeFromString<FileUploadResponse>(body)
        } catch (_: Exception) {
            FileUploadResponse(success = false, value = "${fallbackValue}_$code", message = "Request failed: HTTP $code")
        }

    /** Ktor variant of [parseError] for the JSON calls which use the Ktor client. */
    internal suspend fun parseError(response: HttpResponse, fallbackValue: String): FileUploadResponse =
        try {
            response.body<FileUploadResponse>()
        } catch (_: Exception) {
            FileUploadResponse(success = false, value = "${fallbackValue}_${response.status.value}", message = "Request failed: HTTP ${response.status.value} ${response.status.description}")
        }

    /** Maps a buffered response of a modifying call to the common result type. */
    internal fun mutationResult(raw: RawResponse, successMessage: String, errorFallback: String): ApiResponse<FileUploadResponse> =
        if (raw.isSuccessful) {
            // Success bodies are either the {success, value, message} object or a node object
            val parsed = try { json.decodeFromString<FileUploadResponse>(raw.body) } catch (_: Exception) { null }
            ApiResponse.Success(parsed ?: FileUploadResponse(success = true, value = "ok", message = successMessage))
        } else {
            ApiResponse.Error(parseError(raw.code, raw.body, errorFallback))
        }
}
