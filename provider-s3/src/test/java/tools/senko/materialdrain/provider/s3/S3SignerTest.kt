package tools.senko.materialdrain.provider.s3

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class S3SignerTest {

    @Test
    fun `sha256 of an empty body is the well-known constant`() {
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", S3Signer.sha256Hex(""))
    }

    @Test
    fun `awsUriEncode leaves unreserved characters alone and percent-encodes the rest`() {
        assertEquals("abcABC012-_.~", S3Signer.awsUriEncode("abcABC012-_.~"))
        assertEquals("a%20b", S3Signer.awsUriEncode("a b"))
        assertEquals("a%2Fb", S3Signer.awsUriEncode("a/b")) // slash encoded by default
        assertEquals("a/b", S3Signer.awsUriEncode("a/b", encodeSlash = false))
        assertEquals("%E2%98%83", S3Signer.awsUriEncode("☃")) // multi-byte UTF-8, encoded byte by byte
    }

    @Test
    fun `canonicalUri keeps slashes but encodes each segment`() {
        val url = "https://bucket.example.com/a%20b/c+d".toHttpUrl()
        assertEquals("/a%20b/c%2Bd", S3Signer.canonicalUri(url))
    }

    @Test
    fun `canonicalUri of the bucket root is a single slash`() {
        val url = "https://bucket.example.com/".toHttpUrl()
        assertEquals("/", S3Signer.canonicalUri(url))
    }

    @Test
    fun `canonicalQuery is sorted by key and percent-encodes values`() {
        val url = "https://bucket.example.com/?list-type=2&prefix=a%20b&delimiter=%2F".toHttpUrl()
        // Sorted alphabetically by key: delimiter, list-type, prefix.
        assertEquals("delimiter=%2F&list-type=2&prefix=a%20b", S3Signer.canonicalQuery(url))
    }

    @Test
    fun `canonicalQuery is empty when the url has no query`() {
        assertEquals("", S3Signer.canonicalQuery("https://bucket.example.com/key".toHttpUrl()))
    }

    @Test
    fun `signing is deterministic and sensitive to its inputs`() {
        val url = "https://examplebucket.s3.amazonaws.com/test.txt".toHttpUrl()
        val amzDate = "20130524T000000Z"
        val payloadHash = S3Signer.sha256Hex("")

        val first = S3Signer.sign("GET", url, "AKIDEXAMPLE", "secretkey", "us-east-1", payloadHash, amzDate = amzDate)
        val second = S3Signer.sign("GET", url, "AKIDEXAMPLE", "secretkey", "us-east-1", payloadHash, amzDate = amzDate)
        assertEquals("Same inputs must produce the same signature", first["Authorization"], second["Authorization"])

        val differentMethod = S3Signer.sign("PUT", url, "AKIDEXAMPLE", "secretkey", "us-east-1", payloadHash, amzDate = amzDate)
        assertNotEquals(first["Authorization"], differentMethod["Authorization"])

        val differentKey = S3Signer.sign("GET", url, "AKIDEXAMPLE", "a-different-secret", "us-east-1", payloadHash, amzDate = amzDate)
        assertNotEquals(first["Authorization"], differentKey["Authorization"])

        assertTrue(first.getValue("Authorization").startsWith("AWS4-HMAC-SHA256 Credential=AKIDEXAMPLE/20130524/us-east-1/s3/aws4_request, SignedHeaders=host;x-amz-content-sha256;x-amz-date, Signature="))
    }

    @Test
    fun `matches the published AWS SigV4 GET Object test vector`() {
        // From the AWS documentation's "Example: GET Object" (docs.aws.amazon.com, Signature Version 4
        // signing examples) - an external reference point for the whole signing pipeline (canonical
        // request, string to sign, signing key derivation), not just the parts tested in isolation above.
        val url = "https://examplebucket.s3.amazonaws.com/test.txt".toHttpUrl()
        val headers = S3Signer.sign(
            method = "GET",
            url = url,
            accessKeyId = "AKIAIOSFODNN7EXAMPLE",
            secretAccessKey = "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY",
            region = "us-east-1",
            payloadHash = S3Signer.sha256Hex(""),
            amzDate = "20130524T000000Z",
            extraHeadersToSign = mapOf("range" to "bytes=0-9")
        )
        assertEquals(
            "AWS4-HMAC-SHA256 Credential=AKIAIOSFODNN7EXAMPLE/20130524/us-east-1/s3/aws4_request, " +
                "SignedHeaders=host;range;x-amz-content-sha256;x-amz-date, " +
                "Signature=f0e8bdb87c964420e857bd35b5d6ed310bd44f0170aba48dd91039c6036bdb41",
            headers["Authorization"]
        )
    }
}
