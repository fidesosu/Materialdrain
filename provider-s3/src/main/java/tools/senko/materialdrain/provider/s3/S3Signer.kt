package tools.senko.materialdrain.provider.s3

import okhttp3.HttpUrl
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/** The payload hash to use for streamed bodies of unknown/expensive-to-hash length, per the SigV4 spec. */
internal const val UNSIGNED_PAYLOAD = "UNSIGNED-PAYLOAD"

/**
 * AWS Signature Version 4, implemented from scratch (no AWS SDK dependency, which would drag in a large
 * dependency graph for three HMAC calls) - works the same way against any S3-compatible host (AWS, MinIO,
 * Backblaze B2, Cloudflare R2, TrueNAS).
 */
internal object S3Signer {
    private const val ALGORITHM = "AWS4-HMAC-SHA256"
    private const val SERVICE = "s3"

    fun amzDateNow(): String {
        val format = SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
        return format.format(Date())
    }

    /**
     * Signs [method]/[url] for [region]. [payloadHash] is either [UNSIGNED_PAYLOAD] (streamed bodies of
     * unknown length) or the lowercase hex SHA-256 of the body. [extraHeadersToSign] are headers the
     * caller will also set on the request (e.g. `x-amz-copy-source`) that must be part of the signature -
     * returned headers include them unchanged, so the caller has one place to get the full header set from.
     */
    fun sign(
        method: String,
        url: HttpUrl,
        accessKeyId: String,
        secretAccessKey: String,
        region: String,
        payloadHash: String,
        extraHeadersToSign: Map<String, String> = emptyMap(),
        amzDate: String = amzDateNow()
    ): Map<String, String> {
        val dateStamp = amzDate.substring(0, 8)
        val headersToSign = (extraHeadersToSign + mapOf(
            "host" to hostHeader(url),
            "x-amz-content-sha256" to payloadHash,
            "x-amz-date" to amzDate
        )).mapKeys { it.key.lowercase() }
        val sortedNames = headersToSign.keys.sorted()
        val canonicalHeaders = sortedNames.joinToString("") { "$it:${normalizeHeaderValue(headersToSign.getValue(it))}\n" }
        val signedHeaders = sortedNames.joinToString(";")
        val canonicalRequest = listOf(
            method, canonicalUri(url), canonicalQuery(url), canonicalHeaders, signedHeaders, payloadHash
        ).joinToString("\n")
        val credentialScope = "$dateStamp/$region/$SERVICE/aws4_request"
        val stringToSign = listOf(ALGORITHM, amzDate, credentialScope, sha256Hex(canonicalRequest)).joinToString("\n")
        val signature = hmacHex(signingKey(secretAccessKey, dateStamp, region), stringToSign)
        val authorization = "$ALGORITHM Credential=$accessKeyId/$credentialScope, SignedHeaders=$signedHeaders, Signature=$signature"
        return mapOf("x-amz-date" to amzDate, "x-amz-content-sha256" to payloadHash, "Authorization" to authorization) + extraHeadersToSign
    }

    /** Presigned query-string signing (GET only): the signature lives in the URL, no Authorization header. */
    fun presign(url: HttpUrl, accessKeyId: String, secretAccessKey: String, region: String, expirySeconds: Long, amzDate: String = amzDateNow()): HttpUrl {
        val dateStamp = amzDate.substring(0, 8)
        val credentialScope = "$dateStamp/$region/$SERVICE/aws4_request"
        val withParams = url.newBuilder()
            .setQueryParameter("X-Amz-Algorithm", ALGORITHM)
            .setQueryParameter("X-Amz-Credential", "$accessKeyId/$credentialScope")
            .setQueryParameter("X-Amz-Date", amzDate)
            .setQueryParameter("X-Amz-Expires", expirySeconds.toString())
            .setQueryParameter("X-Amz-SignedHeaders", "host")
            .build()
        val canonicalRequest = listOf(
            "GET", canonicalUri(withParams), canonicalQuery(withParams), "host:${hostHeader(withParams)}\n", "host", UNSIGNED_PAYLOAD
        ).joinToString("\n")
        val stringToSign = listOf(ALGORITHM, amzDate, credentialScope, sha256Hex(canonicalRequest)).joinToString("\n")
        val signature = hmacHex(signingKey(secretAccessKey, dateStamp, region), stringToSign)
        return withParams.newBuilder().addQueryParameter("X-Amz-Signature", signature).build()
    }

    private fun hostHeader(url: HttpUrl): String {
        val defaultPort = if (url.scheme == "https") 443 else 80
        return url.host + if (url.port != defaultPort) ":${url.port}" else ""
    }

    private fun normalizeHeaderValue(value: String): String = value.trim().replace(Regex("\\s+"), " ")

    internal fun canonicalUri(url: HttpUrl): String {
        val segments = url.pathSegments
        if (segments.size == 1 && segments[0].isEmpty()) return "/"
        return "/" + segments.joinToString("/") { awsUriEncode(it, encodeSlash = false) }
    }

    internal fun canonicalQuery(url: HttpUrl): String {
        val names = url.queryParameterNames
        if (names.isEmpty()) return ""
        val pairs = names.flatMap { name -> url.queryParameterValues(name).map { value -> awsUriEncode(name) to awsUriEncode(value ?: "") } }
        return pairs.sortedWith(compareBy({ it.first }, { it.second })).joinToString("&") { "${it.first}=${it.second}" }
    }

    /** AWS's URI-encoding rules: unreserved ASCII passes through, everything else (byte by byte) becomes %XX. */
    internal fun awsUriEncode(value: String, encodeSlash: Boolean = true): String {
        val sb = StringBuilder()
        for (byte in value.toByteArray(Charsets.UTF_8)) {
            val b = byte.toInt() and 0xFF
            val c = b.toChar()
            when {
                c in 'A'..'Z' || c in 'a'..'z' || c in '0'..'9' -> sb.append(c)
                c == '-' || c == '_' || c == '.' || c == '~' -> sb.append(c)
                c == '/' && !encodeSlash -> sb.append(c)
                else -> sb.append("%%%02X".format(b))
            }
        }
        return sb.toString()
    }

    fun sha256Hex(text: String): String = sha256Hex(text.toByteArray(Charsets.UTF_8))

    fun sha256Hex(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun hmac(key: ByteArray, data: String): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(data.toByteArray(Charsets.UTF_8))
    }

    private fun hmacHex(key: ByteArray, data: String): String = hmac(key, data).joinToString("") { "%02x".format(it) }

    private fun signingKey(secretAccessKey: String, dateStamp: String, region: String): ByteArray {
        val kDate = hmac("AWS4$secretAccessKey".toByteArray(Charsets.UTF_8), dateStamp)
        val kRegion = hmac(kDate, region)
        val kService = hmac(kRegion, SERVICE)
        return hmac(kService, "aws4_request")
    }
}
