package tools.senko.materialdrain.files

import okhttp3.OkHttpClient
import okhttp3.Request
import tools.senko.materialdrain.provider.api.ApiResponse
import tools.senko.materialdrain.provider.api.ErrorCodes
import tools.senko.materialdrain.provider.api.ProviderError
import java.io.OutputStream

private const val COPY_BUFFER_BYTES = 64 * 1024

/**
 * The rest of a download that stopped after [offset] bytes: the file at [url] from there on, asked for with a range. A host
 * which answers with the part (206) has it appended, through [openOutput] (true: append to what's there); one which sends
 * the whole file again (200) has it written from the start (false: what's there is replaced). [onProgress] counts the
 * whole file, the bytes there before included. The result is the size of the whole file.
 */
/** What [downloadRest] says when the host gets it wrong: an answer which is neither the part nor the file, or another part. */
internal class ResumeTexts(val httpStatus: (code: Int) -> String, val wrongPart: String)

internal fun downloadRest(
    client: OkHttpClient,
    texts: ResumeTexts,
    url: String,
    headers: Map<String, String>,
    offset: Long,
    openOutput: (append: Boolean) -> OutputStream,
    onProgress: (read: Long, total: Long?) -> Unit
): ApiResponse<Long> {
    val request = Request.Builder().url(url)
        .apply { headers.forEach { (name, value) -> header(name, value) } }
        .header("Range", "bytes=$offset-")
        .build()
    client.newCall(request).execute().use { response ->
        val partial = response.code == 206
        if (!partial && !response.isSuccessful) {
            return ApiResponse.Error(ProviderError(ErrorCodes.NETWORK_ERROR, texts.httpStatus(response.code)))
        }
        // A part which doesn't start where the file stopped would leave a gap: start over instead
        val start = if (partial) contentRangeStart(response.header("Content-Range")) else 0L
        if (partial && start != offset) {
            return ApiResponse.Error(ProviderError(ErrorCodes.NETWORK_ERROR, texts.wrongPart))
        }
        val before = if (partial) offset else 0L
        val total = response.body.contentLength().takeIf { it >= 0 }?.plus(before)
        var done = before
        openOutput(partial).use { output ->
            response.body.byteStream().use { input ->
                val buffer = ByteArray(COPY_BUFFER_BYTES)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    output.write(buffer, 0, read)
                    done += read
                    onProgress(done, total)
                }
            }
        }
        return ApiResponse.Success(done)
    }
}

/** Where the part of a "Content-Range: bytes 100-199/200" starts, or -1 when it says nothing readable. */
private fun contentRangeStart(header: String?): Long =
    header?.substringAfter("bytes ", "")?.substringBefore('-')?.trim()?.toLongOrNull() ?: -1L
