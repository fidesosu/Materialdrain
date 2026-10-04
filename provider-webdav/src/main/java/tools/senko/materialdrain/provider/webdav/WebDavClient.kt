package tools.senko.materialdrain.provider.webdav

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSink
import okio.buffer
import okio.sink
import okio.source
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.TimeUnit

internal class WebDavResult(val code: Int, val bodyText: String) {
    val isSuccessful: Boolean get() = code in 200..299
}

/** Streams an [openStream] straight into the OkHttp socket sink, same pattern as the generic-REST client. */
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
        var sent = 0L
        input.source().use { source ->
            while (true) {
                val read = source.read(sink.buffer, 64L * 1024)
                if (read == -1L) break
                sink.emitCompleteSegments()
                sent += read
                onProgress(sent)
            }
        }
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

/** Requests every property this app understands; some servers (notably older Apache mod_dav) don't default to it on an empty body. */
private val PROPFIND_BODY = """<?xml version="1.0" encoding="utf-8" ?>
<D:propfind xmlns:D="DAV:">
  <D:prop>
    <D:resourcetype/>
    <D:getcontentlength/>
    <D:getlastmodified/>
    <D:getcontenttype/>
    <D:getetag/>
  </D:prop>
</D:propfind>"""

/**
 * Builds and executes WebDAV requests. [rootPath] scopes every request under a fixed subtree of the
 * share, the same role [tools.senko.materialdrain.provider.api.WebDavConfig.rootPath] plays in the
 * config; it's fixed per provider instance, unlike [baseUrl] which is passed per call since it may embed
 * a `{username}` placeholder resolved from credentials that can change at runtime.
 */
internal class WebDavClient(private val rootPath: String) {

    val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(0, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS)
        .build()

    /** Resolves [path] (relative to [rootPath]) against [baseUrl], percent-encoding each segment. */
    fun resolveUrl(baseUrl: String, path: String): HttpUrl? {
        val base = baseUrl.toHttpUrlOrNull() ?: return null
        val builder = base.newBuilder()
        (rootPath.trim('/') + "/" + path.trim('/')).split('/').filter { it.isNotEmpty() }.forEach { builder.addPathSegment(it) }
        return builder.build()
    }

    private fun Request.Builder.applyAuth(auth: AuthHeader): Request.Builder = if (auth == null) this else header(auth.first, auth.second)

    suspend fun propfind(baseUrl: String, path: String, depth: Int, auth: AuthHeader): WebDavResult? {
        val url = resolveUrl(baseUrl, path) ?: return null
        val body = PROPFIND_BODY.toRequestBody("application/xml; charset=utf-8".toMediaTypeOrNull())
        val request = Request.Builder().url(url).method("PROPFIND", body).header("Depth", depth.toString()).applyAuth(auth).build()
        return execute(request)
    }

    suspend fun mkcol(baseUrl: String, path: String, auth: AuthHeader): WebDavResult? {
        val url = resolveUrl(baseUrl, path) ?: return null
        return execute(Request.Builder().url(url).method("MKCOL", null).applyAuth(auth).build())
    }

    /** [destinationPath] is relative, like [path]; resolved against the same [baseUrl]/[rootPath]. */
    suspend fun move(baseUrl: String, path: String, destinationPath: String, auth: AuthHeader): WebDavResult? {
        val url = resolveUrl(baseUrl, path) ?: return null
        val destination = resolveUrl(baseUrl, destinationPath) ?: return null
        val request = Request.Builder().url(url).method("MOVE", null)
            .header("Destination", destination.toString())
            .header("Overwrite", "F")
            .applyAuth(auth).build()
        return execute(request)
    }

    suspend fun delete(baseUrl: String, path: String, auth: AuthHeader): WebDavResult? {
        val url = resolveUrl(baseUrl, path) ?: return null
        return execute(Request.Builder().url(url).delete().applyAuth(auth).build())
    }

    suspend fun put(
        baseUrl: String,
        path: String,
        fileUri: Uri,
        context: Context,
        auth: AuthHeader,
        onProgress: (sent: Long, total: Long?) -> Unit
    ): WebDavResult? {
        val url = resolveUrl(baseUrl, path) ?: return null
        val contentResolver = context.contentResolver
        val mimeType = contentResolver.getType(fileUri) ?: "application/octet-stream"
        val fileSize = queryContentSize(context, fileUri)
        val body = StreamingBody(mimeType, fileSize ?: -1L, { contentResolver.openInputStream(fileUri) }) { sent -> onProgress(sent, fileSize) }
        return execute(Request.Builder().url(url).put(body).applyAuth(auth).build())
    }

    suspend fun get(
        baseUrl: String,
        path: String,
        auth: AuthHeader,
        outputStream: OutputStream,
        onProgress: (read: Long, total: Long?) -> Unit
    ): Int? {
        val url = resolveUrl(baseUrl, path) ?: return null
        val request = Request.Builder().url(url).get().applyAuth(auth).build()
        val call = okHttpClient.newCall(request)
        return coroutineScope {
            val worker = async(Dispatchers.IO) {
                call.execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body
                        val total = body.contentLength().takeIf { it >= 0 }
                        val sink = outputStream.sink().buffer()
                        val source = body.source()
                        var copied = 0L
                        while (true) {
                            val read = source.read(sink.buffer, 64L * 1024)
                            if (read == -1L) break
                            sink.emitCompleteSegments()
                            copied += read
                            onProgress(copied, total)
                        }
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

    private suspend fun execute(request: Request): WebDavResult = coroutineScope {
        val call = okHttpClient.newCall(request)
        val worker = async(Dispatchers.IO) {
            call.execute().use { response -> WebDavResult(response.code, response.body.string()) }
        }
        try {
            worker.await()
        } catch (e: CancellationException) {
            call.cancel()
            throw e
        }
    }
}
