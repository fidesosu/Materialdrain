package tools.senko.materialdrain.provider.s3

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
import tools.senko.materialdrain.provider.api.ProgressThrottle
import okio.sink
import okio.source
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.TimeUnit

internal class S3Result(val code: Int, val bodyText: String) {
    val isSuccessful: Boolean get() = code in 200..299
}

/** Streams an [openStream] straight into the OkHttp socket sink, same pattern used by the other provider clients. */
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

/**
 * Builds and signs ([S3Signer]) requests against [endpoint]/[bucket]. [pathStyle] picks
 * `endpoint/bucket/key` vs. virtual-hosted `bucket.endpoint/key` addressing.
 */
internal class S3Client(private val endpoint: String, private val bucket: String, private val pathStyle: Boolean) {

    val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(0, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS)
        .build()

    /** Resolves [key] (may be empty, for the bucket root) under [bucket]. A trailing slash in [key] (the folder-marker convention) is preserved. */
    fun objectUrl(key: String): HttpUrl? {
        val base = endpoint.toHttpUrlOrNull() ?: return null
        val builder = base.newBuilder()
        if (pathStyle) builder.addPathSegment(bucket) else builder.host("$bucket.${base.host}")
        key.split('/').filter { it.isNotEmpty() }.forEach { builder.addPathSegment(it) }
        if (key.isNotEmpty() && key.endsWith("/")) builder.addPathSegment("")
        return builder.build()
    }

    private fun Request.Builder.sign(
        method: String, url: HttpUrl, accessKeyId: String, secretAccessKey: String, region: String,
        payloadHash: String, extra: Map<String, String> = emptyMap()
    ): Request.Builder {
        S3Signer.sign(method, url, accessKeyId, secretAccessKey, region, payloadHash, extra).forEach { (k, v) -> header(k, v) }
        return this
    }

    /** `ListObjectsV2`. [delimiter] null lists every key under [prefix] recursively; `"/"` scopes it to one level. */
    suspend fun listObjects(
        prefix: String, delimiter: String?, continuationToken: String?, maxKeys: Int,
        accessKeyId: String, secretAccessKey: String, region: String
    ): S3Result? {
        val base = objectUrl("") ?: return null
        val builder = base.newBuilder().addQueryParameter("list-type", "2").addQueryParameter("max-keys", maxKeys.toString())
        if (prefix.isNotEmpty()) builder.addQueryParameter("prefix", prefix)
        if (delimiter != null) builder.addQueryParameter("delimiter", delimiter)
        if (continuationToken != null) builder.addQueryParameter("continuation-token", continuationToken)
        val url = builder.build()
        val request = Request.Builder().url(url).get()
            .sign("GET", url, accessKeyId, secretAccessKey, region, S3Signer.sha256Hex(""))
            .build()
        return execute(request)
    }

    suspend fun putObject(
        key: String, fileUri: Uri, context: Context, accessKeyId: String, secretAccessKey: String, region: String,
        onProgress: (sent: Long, total: Long?) -> Unit
    ): S3Result? {
        val url = objectUrl(key) ?: return null
        val contentResolver = context.contentResolver
        val mimeType = contentResolver.getType(fileUri) ?: "application/octet-stream"
        val fileSize = queryContentSize(context, fileUri)
        val body = StreamingBody(mimeType, fileSize ?: -1L, { contentResolver.openInputStream(fileUri) }) { sent -> onProgress(sent, fileSize) }
        val request = Request.Builder().url(url).put(body)
            .sign("PUT", url, accessKeyId, secretAccessKey, region, UNSIGNED_PAYLOAD)
            .build()
        return execute(request)
    }

    /** The zero-byte "folder marker" object convention every S3 console/browser uses. */
    suspend fun putEmptyObject(key: String, accessKeyId: String, secretAccessKey: String, region: String): S3Result? {
        val url = objectUrl(key) ?: return null
        val payloadHash = S3Signer.sha256Hex(ByteArray(0))
        val request = Request.Builder().url(url).put(ByteArray(0).toRequestBody())
            .sign("PUT", url, accessKeyId, secretAccessKey, region, payloadHash)
            .build()
        return execute(request)
    }

    suspend fun deleteObject(key: String, accessKeyId: String, secretAccessKey: String, region: String): S3Result? {
        val url = objectUrl(key) ?: return null
        val request = Request.Builder().url(url).delete()
            .sign("DELETE", url, accessKeyId, secretAccessKey, region, S3Signer.sha256Hex(""))
            .build()
        return execute(request)
    }

    /** Server-side copy ([sourceKey] to [destinationKey]), the building block [tools.senko.materialdrain.provider.s3.S3StorageProvider] uses for rename. */
    suspend fun copyObject(sourceKey: String, destinationKey: String, accessKeyId: String, secretAccessKey: String, region: String): S3Result? {
        val url = objectUrl(destinationKey) ?: return null
        // A trailing slash (the folder-marker convention) must be preserved, same as objectUrl does.
        val encodedSourceKey = sourceKey.split('/').filter { it.isNotEmpty() }.joinToString("/") { S3Signer.awsUriEncode(it, encodeSlash = false) } +
            if (sourceKey.isNotEmpty() && sourceKey.endsWith("/")) "/" else ""
        val sourcePath = "/" + S3Signer.awsUriEncode(bucket, encodeSlash = false) + "/" + encodedSourceKey
        val extra = mapOf("x-amz-copy-source" to sourcePath)
        val request = Request.Builder().url(url).put(ByteArray(0).toRequestBody())
            .sign("PUT", url, accessKeyId, secretAccessKey, region, S3Signer.sha256Hex(""), extra)
            .build()
        return execute(request)
    }

    suspend fun getObject(
        key: String, accessKeyId: String, secretAccessKey: String, region: String,
        outputStream: OutputStream, onProgress: (read: Long, total: Long?) -> Unit
    ): Int? {
        val url = objectUrl(key) ?: return null
        val request = Request.Builder().url(url).get()
            .sign("GET", url, accessKeyId, secretAccessKey, region, S3Signer.sha256Hex(""))
            .build()
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

    fun presignedGetUrl(key: String, accessKeyId: String, secretAccessKey: String, region: String, expirySeconds: Long): HttpUrl? {
        val url = objectUrl(key) ?: return null
        return S3Signer.presign(url, accessKeyId, secretAccessKey, region, expirySeconds)
    }

    private suspend fun execute(request: Request): S3Result = coroutineScope {
        val call = okHttpClient.newCall(request)
        val worker = async(Dispatchers.IO) {
            call.execute().use { response -> S3Result(response.code, response.body.string()) }
        }
        try {
            worker.await()
        } catch (e: CancellationException) {
            call.cancel()
            throw e
        }
    }
}
