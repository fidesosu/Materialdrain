package tools.senko.materialdrain.provider.s3

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class S3XmlTest {

    @Test
    fun `parses contents and common prefixes from a ListObjectsV2 response`() {
        val xml = """<?xml version="1.0" encoding="UTF-8"?>
            <ListBucketResult xmlns="http://s3.amazonaws.com/doc/2006-03-01/">
              <Name>materialdrain</Name>
              <Prefix>photos/</Prefix>
              <Delimiter>/</Delimiter>
              <IsTruncated>false</IsTruncated>
              <Contents>
                <Key>photos/IMG_001.jpg</Key>
                <LastModified>2024-01-02T03:04:05.000Z</LastModified>
                <ETag>"abc123"</ETag>
                <Size>12345</Size>
              </Contents>
              <Contents>
                <Key>photos/</Key>
                <LastModified>2024-01-01T00:00:00.000Z</LastModified>
                <ETag>"emptymarker"</ETag>
                <Size>0</Size>
              </Contents>
              <CommonPrefixes>
                <Prefix>photos/2024/</Prefix>
              </CommonPrefixes>
            </ListBucketResult>
        """.trimIndent()

        val result = S3Xml.parseListResult(xml)!!
        assertEquals(2, result.objects.size)
        assertEquals("photos/IMG_001.jpg", result.objects[0].key)
        assertEquals(12345L, result.objects[0].size)
        assertEquals("abc123", result.objects[0].etag)
        assertEquals(listOf("photos/2024/"), result.commonPrefixes)
        assertEquals(false, result.nextContinuationToken != null)
    }

    @Test
    fun `parses the continuation token when the listing is truncated`() {
        val xml = """<?xml version="1.0" encoding="UTF-8"?>
            <ListBucketResult>
              <IsTruncated>true</IsTruncated>
              <NextContinuationToken>abc==</NextContinuationToken>
            </ListBucketResult>
        """.trimIndent()
        val result = S3Xml.parseListResult(xml)!!
        assertTrue(result.objects.isEmpty())
        assertEquals("abc==", result.nextContinuationToken)
    }

    @Test
    fun `parses an S3 error body`() {
        val xml = """<?xml version="1.0" encoding="UTF-8"?>
            <Error>
              <Code>AccessDenied</Code>
              <Message>Access Denied</Message>
              <RequestId>ABC123</RequestId>
            </Error>
        """.trimIndent()
        assertEquals("Access Denied", S3Xml.parseErrorMessage(xml))
    }

    @Test
    fun `falls back to the error code when there is no message`() {
        val xml = "<Error><Code>NoSuchBucket</Code></Error>"
        assertEquals("NoSuchBucket", S3Xml.parseErrorMessage(xml))
    }

    @Test
    fun `malformed xml returns null instead of throwing`() {
        assertNull(S3Xml.parseListResult("not xml at all"))
        assertNull(S3Xml.parseErrorMessage("<<<"))
    }
}
