package tools.senko.materialdrain.provider.s3

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import tools.senko.materialdrain.provider.api.ApiResponse
import tools.senko.materialdrain.provider.api.Credentials
import tools.senko.materialdrain.provider.api.ProviderCapability
import tools.senko.materialdrain.provider.api.S3Config

/** End-to-end: config -> provider -> signed HTTP call -> normalized [tools.senko.materialdrain.provider.api.StorageListing]. */
class S3StorageProviderTest {

    private lateinit var server: MockWebServer

    @Before
    fun start() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun stop() {
        server.shutdown()
    }

    private fun provider(prefix: String = "", credentials: Credentials = Credentials(username = "AKID", password = "SECRET")) =
        S3StorageProvider(
            id = "test",
            config = S3Config(name = "Test", endpoint = server.url("/").toString(), bucket = "bucket", prefix = prefix),
            credentials = { credentials }
        )

    private val listingXml = """<?xml version="1.0" encoding="UTF-8"?>
        <ListBucketResult>
          <Contents><Key>IMG_001.jpg</Key><Size>12345</Size><LastModified>2024-01-02T03:04:05.000Z</LastModified></Contents>
          <CommonPrefixes><Prefix>Vacation/</Prefix></CommonPrefixes>
        </ListBucketResult>
    """.trimIndent()

    @Test
    fun `list maps files and folders, and reports the host as writable`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody(listingXml))
        val provider = provider()
        assertTrue(ProviderCapability.BROWSE in provider.capabilities)

        val result = provider.browse.list("") as ApiResponse.Success
        assertEquals(listOf("Vacation", "IMG_001.jpg"), result.data.children.map { it.name })
        assertEquals(listOf(true, false), result.data.children.map { it.isDirectory })
        assertEquals(12345L, result.data.children[1].size)
        assertTrue(result.data.canWrite)
        assertTrue(result.data.canDelete)

        val request = server.takeRequest()
        assertEquals("GET", request.method)
        // Signed every request - no Authorization header means credentials silently weren't applied.
        assertTrue(request.getHeader("Authorization")?.startsWith("AWS4-HMAC-SHA256") == true)
    }

    @Test
    fun `missing credentials fail locally without a network call`() = runBlocking {
        val result = provider(credentials = Credentials()).browse.list("")
        assertTrue(result is ApiResponse.Error)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `a nested path is scoped by the config prefix plus the browsed path`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""<ListBucketResult></ListBucketResult>"""))
        provider(prefix = "materialdrain").browse.list("Photos/2024")
        val request = server.takeRequest()
        assertEquals("materialdrain/Photos/2024/", request.requestUrl?.queryParameter("prefix"))
    }

    @Test
    fun `delete on an empty-looking path with recursive=false just deletes the single key`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""<ListBucketResult></ListBucketResult>""")) // probe: nothing under it
        server.enqueue(MockResponse().setResponseCode(204))
        val result = provider().browse.delete("IMG_001.jpg", recursive = false)
        assertTrue(result is ApiResponse.Success)
        server.takeRequest() // probe
        val deleteRequest = server.takeRequest()
        assertEquals("DELETE", deleteRequest.method)
    }

    @Test
    fun `delete refuses a non-empty folder when recursive is false`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody(listingXml)) // probe finds children
        val result = provider().browse.delete("", recursive = false)
        assertTrue(result is ApiResponse.Error)
        assertEquals(1, server.requestCount) // only the probe, no deletes were attempted
    }

    @Test
    fun `createDirectory puts a zero-byte marker object ending in a slash`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200))
        val result = provider().browse.createDirectory("NewFolder", makeParents = false)
        assertTrue(result is ApiResponse.Success)
        val request = server.takeRequest()
        assertEquals("PUT", request.method)
        assertTrue(request.path?.endsWith("NewFolder/") == true)
        assertEquals(0L, request.bodySize)
    }

    @Test
    fun `rename of a single file copies then deletes the source`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""<ListBucketResult></ListBucketResult>""")) // probe: not a folder
        server.enqueue(MockResponse().setResponseCode(200)) // copy
        server.enqueue(MockResponse().setResponseCode(204)) // delete old key
        val result = provider().browse.rename("old.txt", "new.txt", makeParents = false)
        assertTrue(result is ApiResponse.Success)
        server.takeRequest()
        val copyRequest = server.takeRequest()
        assertEquals("PUT", copyRequest.method)
        assertTrue(copyRequest.getHeader("x-amz-copy-source")?.contains("old.txt") == true)
        val deleteRequest = server.takeRequest()
        assertEquals("DELETE", deleteRequest.method)
        assertTrue(deleteRequest.path?.endsWith("old.txt") == true)
    }

    @Test
    fun `validate reports rejected credentials distinctly from an unreachable endpoint`() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(403).setBody("<Error><Code>AccessDenied</Code><Message>Access Denied</Message></Error>")
        )
        val result = provider().validate()
        val credentialsField = result.fields.first { it.fieldId == "credentials" }
        assertEquals(false, credentialsField.ok)
        assertTrue(credentialsField.message.contains("Access Denied"))
        val endpointField = result.fields.first { it.fieldId == "endpoint" }
        assertTrue("The endpoint itself answered, so it's reachable even though the credentials were rejected", endpointField.ok)
    }
}
