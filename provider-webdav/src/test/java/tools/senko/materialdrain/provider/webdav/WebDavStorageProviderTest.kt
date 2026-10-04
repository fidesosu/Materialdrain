package tools.senko.materialdrain.provider.webdav

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import tools.senko.materialdrain.provider.api.ApiResponse
import tools.senko.materialdrain.provider.api.AuthConfig
import tools.senko.materialdrain.provider.api.AuthType
import tools.senko.materialdrain.provider.api.Credentials
import tools.senko.materialdrain.provider.api.ProviderCapability
import tools.senko.materialdrain.provider.api.WebDavConfig

/** End-to-end: config -> provider -> real HTTP call -> normalized [tools.senko.materialdrain.provider.api.StorageListing]. */
class WebDavStorageProviderTest {

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

    private fun provider(rootPath: String = "/", credentials: Credentials = Credentials(apiKey = "secret")) =
        WebDavStorageProvider(
            id = "test",
            config = WebDavConfig(name = "Test", baseUrl = server.url("/dav").toString(), auth = AuthConfig(type = AuthType.BASIC), rootPath = rootPath),
            credentials = { credentials }
        )

    private fun multistatus(selfHref: String, vararg children: Pair<String, Boolean>): String {
        val childEntries = children.joinToString("") { (href, isCollection) ->
            """<d:response>
                 <d:href>$href</d:href>
                 <d:propstat>
                   <d:prop>
                     <d:resourcetype>${if (isCollection) "<d:collection/>" else ""}</d:resourcetype>
                     <d:getcontentlength>42</d:getcontentlength>
                   </d:prop>
                   <d:status>HTTP/1.1 200 OK</d:status>
                 </d:propstat>
               </d:response>"""
        }
        return """<?xml version="1.0"?>
            <d:multistatus xmlns:d="DAV:">
              <d:response>
                <d:href>$selfHref</d:href>
                <d:propstat>
                  <d:prop><d:resourcetype><d:collection/></d:resourcetype></d:prop>
                  <d:status>HTTP/1.1 200 OK</d:status>
                </d:propstat>
              </d:response>
              $childEntries
            </d:multistatus>
        """.trimIndent()
    }

    @Test
    fun `list excludes the folder itself and maps children`() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(207).setBody(
                multistatus("/dav/", "/dav/Photos/" to true, "/dav/notes.txt" to false)
            )
        )
        val provider = provider()
        assertTrue(ProviderCapability.BROWSE in provider.capabilities)

        val result = provider.browse.list("") as ApiResponse.Success
        assertEquals(setOf("Photos", "notes.txt"), result.data.children.map { it.name }.toSet())
        assertEquals(true, result.data.children.first { it.name == "Photos" }.isDirectory)
        assertEquals(false, result.data.children.first { it.name == "notes.txt" }.isDirectory)
        assertTrue(result.data.canWrite)

        val request = server.takeRequest()
        assertEquals("PROPFIND", request.method)
        assertEquals("1", request.getHeader("Depth"))
        assertEquals("Basic OnNlY3JldA==", request.getHeader("Authorization")) // base64(":secret") - empty keyUsername + the api key
    }

    @Test
    fun `delete refuses a non-empty folder when recursive is false`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(207).setBody(multistatus("/dav/Photos/", "/dav/Photos/a.jpg" to false)))
        val result = provider().browse.delete("Photos", recursive = false)
        assertTrue(result is ApiResponse.Error)
        assertEquals(1, server.requestCount) // only the listing probe, no DELETE was attempted
    }

    @Test
    fun `delete proceeds when the folder is empty`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(207).setBody(multistatus("/dav/Photos/")))
        server.enqueue(MockResponse().setResponseCode(204))
        val result = provider().browse.delete("Photos", recursive = false)
        assertTrue(result is ApiResponse.Success)
        server.takeRequest()
        assertEquals("DELETE", server.takeRequest().method)
    }

    @Test
    fun `createDirectory issues MKCOL and reports an existing folder distinctly`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(201))
        assertTrue(provider().browse.createDirectory("NewFolder", makeParents = false) is ApiResponse.Success)
        assertEquals("MKCOL", server.takeRequest().method)

        server.enqueue(MockResponse().setResponseCode(405))
        val result = provider().browse.createDirectory("NewFolder", makeParents = false)
        assertTrue(result is ApiResponse.Error)
        assertEquals("already_exists", (result as ApiResponse.Error).error.code)
    }

    @Test
    fun `rename issues MOVE with a Destination header`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(201))
        val result = provider().browse.rename("old.txt", "new.txt", makeParents = false)
        assertTrue(result is ApiResponse.Success)
        val request = server.takeRequest()
        assertEquals("MOVE", request.method)
        assertTrue(request.getHeader("Destination")?.endsWith("/dav/new.txt") == true)
    }

    @Test
    fun `base url username placeholder is filled from the entered username`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(207).setBody(multistatus("/dav/alice/")))
        // Appended as a plain string, not built through HttpUrl, so "{username}" stays a literal substring to
        // replace - the same shape a hand-typed config's base_url has in production.
        val config = WebDavConfig(name = "Test", baseUrl = server.url("/dav").toString() + "/{username}", rootPath = "/")
        val provider = WebDavStorageProvider("test", config) { Credentials(username = "alice", password = "pw") }
        provider.browse.list("")
        assertTrue(server.takeRequest().path?.startsWith("/dav/alice") == true)
    }

    @Test
    fun `validate reports rejected credentials distinctly from an unreachable endpoint`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(207).setBody(multistatus("/dav/"))) // reachability probe (no auth)
        server.enqueue(MockResponse().setResponseCode(401)) // credentials probe
        val result = provider().validate()
        val baseField = result.fields.first { it.fieldId == "base_url" }
        assertTrue(baseField.ok)
        val credentialsField = result.fields.first { it.fieldId == "credentials" }
        assertEquals(false, credentialsField.ok)
        assertTrue(credentialsField.message.contains("401"))
    }
}
