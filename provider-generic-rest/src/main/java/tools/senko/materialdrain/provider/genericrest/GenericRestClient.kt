package tools.senko.materialdrain.provider.genericrest

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSink
import okio.buffer
import tools.senko.materialdrain.provider.api.ProgressThrottle
import okio.sink
import okio.source
import tools.senko.materialdrain.provider.api.BodyEncoding
import tools.senko.materialdrain.provider.api.EndpointConfig
import tools.senko.materialdrain.provider.api.LoginBody
import tools.senko.materialdrain.provider.api.LoginEndpoint
import tools.senko.materialdrain.provider.api.ProviderLog
import java.io.IOException
import java.net.URLEncoder
import java.io.InputStream
import java.util.concurrent.TimeUnit

internal class GenericRestResult(val code: Int, val bodyText: String) {
    val isSuccessful: Boolean get() = code in 200..299
}

/** Streams an [openStream] straight into the OkHttp socket sink, mirroring the pattern used for Pixeldrain uploads. */
private class StreamingBody(
    private val mediaType: String?,
    private val length: Long,
    private val openStream: () -> InputStream?,
    private val onProgress: (sent: Long) -> Unit
) : RequestBody() {
    override fun contentType() = mediaType?.toMediaTypeOrNull()
    override fun contentLength() = length

    override fun writeTo(sink: BufferedSink) {
        val input = openStream() ?: throw IOException("Failed to open input stream for upload.")
        // A read gives at most one 8 KB segment: reporting each would be over a thousand updates a second of the
        // screen and the notification, so they're passed on a few times a second (the last one always)
        val progress = ProgressThrottle(onProgress)
        var sent = 0L
        input.source().use { source ->
            while (true) {
                val read = source.read(sink.buffer, 64L * 1024)
                if (read == -1L) break
                sink.emitCompleteSegments()
                sent += read
                progress.update(sent)
            }
        }
        progress.finish(sent)
    }
}

private fun queryContentSize(context: Context, uri: Uri): Long? = try {
    context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) {
            val index = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (index >= 0 && !cursor.isNull(index)) cursor.getLong(index) else null
        } else null
    }
} catch (_: Exception) {
    null
}

/** An auth header (name to value), decided by the provider from the entered credentials; null sends none. */
internal typealias AuthHeader = Pair<String, String>?

private val BODY_REQUIRED_METHODS = setOf("POST", "PUT", "PATCH")

/** A path value with its slashes kept, every segment percent-encoded (plain JVM, so it works in unit tests too). */
// Commas are legal in a path segment, and keeping them lets several ids go into one path ("/file/a,b" for an archive)
internal fun encodePathValue(value: String): String =
    value.split('/').joinToString("/") { URLEncoder.encode(it, "UTF-8").replace("+", "%20").replace("%2C", ",") }

/**
 * A query or form value from its template, or null to leave the field out: a template that is exactly one
 * {placeholder} with nothing filled in is omitted, so optional flags can be dropped.
 */
internal fun fieldValue(template: String, placeholders: Map<String, String>): String? {
    val single = Regex("^\\{(\\w+)\\}$").find(template.trim())
    if (single != null) return placeholders[single.groupValues[1]]?.takeIf { it.isNotEmpty() }
    var resolved = template
    placeholders.forEach { (key, value) -> resolved = resolved.replace("{$key}", value) }
    return resolved
}

/**
 * Builds and executes the requests a [tools.senko.materialdrain.provider.api.GenericRestConfig] describes. Which
 * credentials to send is the provider's decision ([AuthHeaders]); this only attaches the header it's given.
 */
internal class GenericRestClient(private val baseUrl: String) {

    val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(0, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS)
        .build()

    /** Resolves {placeholder} tokens in [endpoint]'s path against [baseUrl], then adds its query parameters. */
    fun resolveUrl(endpoint: EndpointConfig, placeholders: Map<String, String>): HttpUrl? {
        val base = resolveUrl(endpoint.path, placeholders) ?: return null
        val builder = base.newBuilder()
        endpoint.query.forEach { (name, template) -> fieldValue(template, placeholders)?.let { builder.addQueryParameter(name, it) } }
        return builder.build()
    }

    fun resolveUrl(path: String, placeholders: Map<String, String>): HttpUrl? {
        var resolved = path
        // Segment by segment, so a value with slashes (a folder path) keeps them as path separators
        placeholders.forEach { (key, value) -> resolved = resolved.replace("{$key}", encodePathValue(value)) }
        val full = if (resolved.startsWith("http://") || resolved.startsWith("https://")) {
            resolved
        } else {
            baseUrl.trimEnd('/') + "/" + resolved.trimStart('/')
        }
        return full.toHttpUrlOrNull()
    }

    private fun Request.Builder.applyAuth(auth: AuthHeader): Request.Builder =
        if (auth == null) this else header(auth.first, auth.second)

    /** The body of [endpoint]: its form fields, or an empty body for a method that can't be sent without one. */
    private fun requestBody(endpoint: EndpointConfig, placeholders: Map<String, String>): RequestBody? {
        if (endpoint.body == BodyEncoding.JSON) {
            var text = endpoint.json.orEmpty()
            placeholders.forEach { (key, value) -> text = text.replace("{$key}", value) }
            return text.toRequestBody("application/json".toMediaType())
        }
        if (endpoint.form.isNotEmpty() || endpoint.body == BodyEncoding.FORM) {
            return FormBody.Builder().apply {
                endpoint.form.forEach { (name, template) -> fieldValue(template, placeholders)?.let { add(name, it) } }
            }.build()
        }
        return if (endpoint.method.uppercase() in BODY_REQUIRED_METHODS) ByteArray(0).toRequestBody(null) else null
    }

    suspend fun executeBuffered(endpoint: EndpointConfig, placeholders: Map<String, String>, auth: AuthHeader): GenericRestResult? {
        val url = resolveUrl(endpoint, placeholders) ?: return null
        val request = Request.Builder().url(url).method(endpoint.method, requestBody(endpoint, placeholders)).applyAuth(auth).build()
        return execute(request)
    }

    /** Sends the sign-in request of [login], see [AuthHeaders.loginFields] for how its fields are filled in. */
    suspend fun login(login: LoginEndpoint, username: String, password: String, otp: String?): GenericRestResult? {
        val url = resolveUrl(login.path, emptyMap()) ?: return null
        val values = AuthHeaders.loginFields(login.fields, username, password, otp)
        val body: RequestBody = when (login.body) {
            LoginBody.FORM -> FormBody.Builder().apply { values.forEach { (name, value) -> add(name, value) } }.build()
            LoginBody.JSON -> JsonObject(values.mapValues { JsonPrimitive(it.value) }).toString()
                .toRequestBody("application/json".toMediaType())
        }
        return execute(Request.Builder().url(url).method(login.method, body).build())
    }

    suspend fun executeUpload(
        endpoint: EndpointConfig,
        placeholders: Map<String, String>,
        auth: AuthHeader,
        fileName: String,
        fileUri: Uri,
        context: Context,
        onProgress: (sent: Long, total: Long?) -> Unit
    ): GenericRestResult? {
        val url = resolveUrl(endpoint, placeholders) ?: return null
        val contentResolver = context.contentResolver
        val mimeType = contentResolver.getType(fileUri) ?: "application/octet-stream"
        val fileSize = queryContentSize(context, fileUri)
        val body: RequestBody = when (endpoint.body) {
            BodyEncoding.MULTIPART -> MultipartBody.Builder().setType(MultipartBody.FORM).addFormDataPart(
                "file", fileName,
                StreamingBody(mimeType, fileSize ?: -1L, { contentResolver.openInputStream(fileUri) }) { sent -> onProgress(sent, fileSize) }
            ).build()
            BodyEncoding.RAW, BodyEncoding.FORM, BodyEncoding.JSON -> StreamingBody(mimeType, fileSize ?: -1L, { contentResolver.openInputStream(fileUri) }) { sent -> onProgress(sent, fileSize) }
        }
        val request = Request.Builder().url(url).method(endpoint.method, body).applyAuth(auth).build()
        return execute(request)
    }

    suspend fun executeDownload(
        endpoint: EndpointConfig,
        placeholders: Map<String, String>,
        auth: AuthHeader,
        outputStream: java.io.OutputStream,
        onProgress: (read: Long, total: Long?) -> Unit
    ): Int? {
        val url = resolveUrl(endpoint, placeholders) ?: return null
        val request = Request.Builder().url(url).method(endpoint.method, null).applyAuth(auth).build()
        val call = okHttpClient.newCall(request)
        return coroutineScope {
            val worker = async(Dispatchers.IO) {
                val started = System.nanoTime()
                ProviderLog.d("Http", "${request.method} ${request.url} (download)")
                call.execute().use { response ->
                    if (!response.isSuccessful) logResponse(request, response.code, started, response.body.string())
                    if (response.isSuccessful) {
                        ProviderLog.d("Http", "${request.method} ${request.url} -> ${response.code}, streaming ${response.body.contentLength()} bytes")
                        val body = response.body
                        val total = body.contentLength().takeIf { it >= 0 }
                        val sink = outputStream.sink().buffer()
                        val source = body.source()
                        var copied = 0L
                        val progress = ProgressThrottle { onProgress(it, total) }
                        while (true) {
                            val read = source.read(sink.buffer, 64L * 1024)
                            if (read == -1L) break
                            sink.emitCompleteSegments()
                            copied += read
                            progress.update(copied)
                        }
                        progress.finish(copied)
                        sink.flush()
                    }
                    response.code
                }
            }
            try {
                worker.await()
            } catch (e: CancellationException) {
                call.cancel()
                throw e
            }
        }
    }

    /**
     * HEAD-only reachability probe, sent without credentials: it never exercises the endpoint's real method, since
     * "testing" a destructive one would itself be destructive. Returns the status code, null when unreachable.
     * Any status means the server answered; it says nothing about whether credentials work.
     */
    suspend fun probe(path: String, placeholders: Map<String, String>): Int? {
        val url = resolveUrl(path, placeholders) ?: return null
        return try {
            execute(Request.Builder().url(url).head().build()).code
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun execute(request: Request): GenericRestResult = coroutineScope {
        val call = okHttpClient.newCall(request)
        val worker = async(Dispatchers.IO) {
            val started = System.nanoTime()
            ProviderLog.d("Http", "${request.method} ${request.url}")
            try {
                call.execute().use { response ->
                    val body = response.body.string()
                    logResponse(request, response.code, started, body)
                    GenericRestResult(response.code, body)
                }
            } catch (e: IOException) {
                ProviderLog.e("Http", "${request.method} ${request.url} failed after ${elapsedMs(started)} ms: ${e.message}", e)
                throw e
            }
        }
        try {
            worker.await()
        } catch (e: CancellationException) {
            call.cancel()
            throw e
        }
    }

    private fun logResponse(request: Request, code: Int, started: Long, body: String) {
        val summary = "${request.method} ${request.url} -> $code in ${elapsedMs(started)} ms"
        if (code in 200..299) {
            ProviderLog.d("Http", summary)
        } else {
            ProviderLog.w("Http", "$summary, body: ${ProviderLog.snippet(body)}")
        }
    }

    private fun elapsedMs(started: Long): Long = (System.nanoTime() - started) / 1_000_000
}
