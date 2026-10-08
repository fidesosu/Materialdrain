package tools.senko.materialdrain.provider.genericrest

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import tools.senko.materialdrain.provider.api.ApiResponse
import tools.senko.materialdrain.provider.api.Credentials
import tools.senko.materialdrain.provider.api.GenericRestConfig
import tools.senko.materialdrain.provider.api.ProviderCapability
import tools.senko.materialdrain.provider.api.ProviderConfigCodec
import tools.senko.materialdrain.provider.api.StorageNode
import tools.senko.materialdrain.provider.api.StorageRef
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * The shipped docs/provider-configs/pixeldrain.json, pointed at a mock server, driving the folder browsing.
 * Responses are shaped like the examples in docs/pixeldrain_api.txt (in the website branch).
 */
class PixeldrainConfigBrowseTest {

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

    private fun provider(): GenericRestStorageProvider {
        val shipped = ProviderConfigCodec.decode(File("../docs/provider-configs/pixeldrain.json").readText()) as GenericRestConfig
        val config = shipped.copy(baseUrl = server.url("/api").toString())
        return GenericRestStorageProvider(id = "pd", config = config, credentials = { Credentials(apiKey = "key") })
    }

    private val folderListing = """
        {
          "path": [{"type": "dir", "path": "/me", "name": "me"}],
          "base_index": 0,
          "children": [
            {"type": "dir", "path": "/me/photos", "name": "photos", "file_size": 0, "file_type": "", "created": "2024-01-01T00:00:00Z", "modified": "2024-01-02T00:00:00Z", "id": null},
            {"type": "file", "path": "/me/todo.txt", "name": "todo.txt", "file_size": 12, "file_type": "text/plain", "mime_type": "text/plain", "modified": "2024-01-03T00:00:00Z", "id": "abc123"}
          ],
          "permissions": {"owner": true, "read": true, "write": true, "delete": false},
          "context": {"premium_transfer": true}
        }
    """.trimIndent()

    @Test
    fun `the shipped config starts browsing in the me bucket`() {
        assertEquals("me", provider().rootPath)
    }

    @Test
    fun `the shipped config gives the browse capabilities`() {
        val capabilities = provider().capabilities
        assertTrue(ProviderCapability.BROWSE in capabilities)
        assertTrue(ProviderCapability.MKDIR in capabilities)
        assertTrue(ProviderCapability.RENAME in capabilities)
        assertTrue(ProviderCapability.DELETE in capabilities)
        assertTrue(ProviderCapability.DOWNLOAD in capabilities)
    }

    @Test
    fun `listing maps folders, files, breadcrumb and permissions`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody(folderListing))
        val result = provider().browse!!.list("me") as ApiResponse.Success

        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals("/api/filesystem/me?stat", request.path)

        assertEquals(listOf("photos", "todo.txt"), result.data.children.map { it.name })
        assertEquals(true, result.data.children[0].isDirectory)
        assertEquals("/me/photos", result.data.children[0].ref.path)
        assertEquals(false, result.data.children[1].isDirectory)
        assertEquals(12L, result.data.children[1].size)
        assertEquals("abc123", result.data.children[1].ref.id)
        assertEquals(listOf("me"), result.data.breadcrumb.map { it.name })
        assertTrue(result.data.canWrite)
        assertEquals(false, result.data.canDelete)
    }

    @Test
    fun `mkdir posts the form action and encodes nothing in the folder path`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(201).setBody("""{"success": true, "value": "created"}"""))
        val result = provider().browse!!.createDirectory("me/new folder", makeParents = false)
        assertTrue(result is ApiResponse.Success)

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/filesystem/me/new%20folder", request.path)
        assertEquals("action=mkdir", request.body.readUtf8())
    }

    @Test
    fun `mkdir with makeParents uses mkdirall`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(201))
        provider().browse!!.createDirectory("me/a/b", makeParents = true)
        assertEquals("action=mkdirall", server.takeRequest().body.readUtf8())
    }

    @Test
    fun `rename sends the target as a form field and returns the node`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"name": "b.txt", "path": "/me/b.txt"}"""))
        val result = provider().browse!!.rename("/me/a.txt", "me/b.txt", makeParents = false) as ApiResponse.Success
        assertEquals("/me/b.txt", result.data.ref.path)

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/filesystem/me/a.txt", request.path)
        assertEquals("action=rename&target=me%2Fb.txt", request.body.readUtf8())
    }

    @Test
    fun `delete omits the recursive flag unless it is set`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"success": true}"""))
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"success": true}"""))
        val browse = provider().browse!!

        browse.delete("me/file.txt", recursive = false)
        assertEquals("/api/filesystem/me/file.txt", server.takeRequest().path)

        browse.delete("me/folder", recursive = true)
        assertEquals("/api/filesystem/me/folder?recursive=true", server.takeRequest().path)
    }

    @Test
    fun `download streams the raw file contents`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("hello"))
        val out = ByteArrayOutputStream()
        val result = provider().browse!!.download("me/todo.txt", out) { _, _ -> }
        assertEquals(5L, (result as ApiResponse.Success).data)
        assertEquals("hello", out.toString("UTF-8"))
        assertEquals("/api/filesystem/me/todo.txt", server.takeRequest().path)
    }

    @Test
    fun `thumbnail and raw urls come from the config templates`() {
        val file = StorageNode(ref = StorageRef(path = "/me/photos/a b.png"), name = "a b.png", isDirectory = false)
        val p = provider()
        assertEquals("${server.url("/api/filesystem/me/photos/a%20b.png")}?thumbnail", p.thumbnailUrl(file))
        assertEquals(server.url("/api/filesystem/me/photos/a%20b.png").toString(), p.rawContentUrl(file, attachment = false))
        assertEquals(server.url("/api/filesystem/me/photos/a%20b.png").toString() + "?attach", p.rawContentUrl(file, attachment = true))
    }

    @Test
    fun `share links point at the public pages for files with an id and filesystem paths`() {
        val withId = StorageNode(ref = StorageRef(path = "/me/a.txt", id = "abc123"), name = "a.txt", isDirectory = false)
        val inFilesystem = StorageNode(ref = StorageRef(path = "/me/photos/b c.png"), name = "b c.png", isDirectory = false)
        val p = provider()
        assertEquals("https://pixeldrain.com/u/abc123", p.shareUrl(withId))
        assertEquals("https://pixeldrain.com/d/me/photos/b%20c.png", p.shareUrl(inFilesystem))
        assertNull(p.shareUrl(StorageNode(ref = StorageRef(path = "/me/photos"), name = "photos", isDirectory = true)))
    }

    @Test
    fun `folders have no thumbnail or raw url`() {
        val folder = StorageNode(ref = StorageRef(path = "/me/photos"), name = "photos", isDirectory = true)
        assertNull(provider().thumbnailUrl(folder))
        assertNull(provider().rawContentUrl(folder, attachment = false))
    }
}
