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
import java.io.File

/** The lists, user files and import of the shipped docs/provider-configs/pixeldrain.json, against a mock server. */
class PixeldrainConfigListsTest {

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
        return GenericRestStorageProvider(
            id = "pd",
            config = shipped.copy(baseUrl = server.url("/api").toString()),
            credentials = { Credentials(apiKey = "key") }
        )
    }

    @Test
    fun `the shipped config declares the lists capability`() {
        assertTrue(ProviderCapability.LISTS in provider().capabilities)
        assertTrue(provider().lists != null)
    }

    @Test
    fun `the overview maps lists from user_lists`() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"lists": [{"id": "L8bhwx", "title": "Rust in Peace", "date_created": "2020-02-04T18:34:13Z", "file_count": 3, "can_edit": true}]}"""
            )
        )
        val lists = (provider().lists!!.lists() as ApiResponse.Success).data
        assertEquals(listOf("L8bhwx"), lists.map { it.id })
        assertEquals("Rust in Peace", lists[0].title)
        assertEquals(3, lists[0].fileCount)
        assertTrue(lists[0].canEdit)
        assertEquals("/api/user/lists", server.takeRequest().path)
    }

    @Test
    fun `a list's contents map its files and title`() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"id": "L8bhwx", "title": "Rust in Peace", "can_edit": false, "file_count": 1,
                   "files": [{"id": "_SqVWi", "name": "01.mp3", "size": 123456, "mime_type": "audio/mp3", "date_upload": "2020-02-04T18:34:13Z", "date_last_view": "2020-02-04T18:34:13Z"}]}"""
            )
        )
        val detail = (provider().lists!!.listContents("L8bhwx") as ApiResponse.Success).data
        assertEquals("Rust in Peace", detail.title)
        assertEquals(false, detail.canEdit)
        assertEquals(listOf("_SqVWi"), detail.files.map { it.ref.id })
        assertEquals("01.mp3", detail.files[0].name)
        assertEquals(123456L, detail.files[0].size)
        assertEquals("/api/list/L8bhwx", server.takeRequest().path)
    }

    @Test
    fun `create posts the title and file ids as JSON and returns the new id`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(201).setBody("""{"success": true, "id": "yay137"}"""))
        val id = (provider().lists!!.create("My \"photos\"", listOf("a1", "b2")) as ApiResponse.Success).data
        assertEquals("yay137", id)

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/list", request.path)
        assertEquals(
            """{"title": "My \"photos\"", "anonymous": false, "files": [{"id":"a1"},{"id":"b2"}]}""",
            request.body.readUtf8()
        )
        assertEquals("application/json; charset=utf-8", request.getHeader("Content-Type"))
    }

    @Test
    fun `update puts the new title and files to the list`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"success": true}"""))
        val result = provider().lists!!.update("L8bhwx", "Renamed", listOf("a1"))
        assertTrue(result is ApiResponse.Success)

        val request = server.takeRequest()
        assertEquals("PUT", request.method)
        assertEquals("/api/list/L8bhwx", request.path)
        assertEquals("""{"title": "Renamed", "files": [{"id":"a1"}]}""", request.body.readUtf8())
    }

    @Test
    fun `delete removes the list itself`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"success": true}"""))
        assertTrue(provider().lists!!.delete("L8bhwx") is ApiResponse.Success)
        val request = server.takeRequest()
        assertEquals("DELETE", request.method)
        assertEquals("/api/list/L8bhwx", request.path)
    }

    @Test
    fun `the user's files come from the list endpoint as nodes with ids`() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"files": [{"id": "1234abcd", "name": "screenshot.png", "size": 5694837, "mime_type": "image/png"}]}"""
            )
        )
        val listing = (provider().fileList!!.list() as ApiResponse.Success).data
        assertEquals(listOf("1234abcd"), listing.children.map { it.ref.id })
        assertEquals("screenshot.png", listing.children[0].name)
        assertEquals("/api/user/files", server.takeRequest().path)
    }

    @Test
    fun `the user's files carry their dates so they can be sorted`() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"files": [{"id": "a", "name": "a.png", "size": 1, "date_upload": "2024-01-02T00:00:00Z", "date_last_view": "2024-03-04T00:00:00Z"}]}"""
            )
        )
        val file = (provider().fileList!!.list() as ApiResponse.Success).data.children.single()
        assertEquals("2024-01-02T00:00:00Z", file.createdAt)
        // The last view isn't a change to the file: the list sorts and dates it by its upload instead
        assertNull(file.modifiedAt)
    }

    @Test
    fun `files with an id get thumbnail and raw urls from the id endpoints`() {
        val file = tools.senko.materialdrain.provider.api.StorageNode(
            ref = tools.senko.materialdrain.provider.api.StorageRef(id = "abc123"),
            name = "a.png",
            isDirectory = false
        )
        assertEquals("https://pixeldrain.com/api/file/abc123/thumbnail", provider().thumbnailUrl(file))
        assertEquals("https://pixeldrain.com/api/file/abc123", provider().rawContentUrl(file, attachment = false))
    }

    @Test
    fun `the shipped config falls back to the account login, so Pixeldrain works without a host credential`() {
        val shipped = ProviderConfigCodec.decode(File("../docs/provider-configs/pixeldrain.json").readText()) as GenericRestConfig
        assertTrue(shipped.accountFallback)
        assertTrue(shipped.sendCredentials)
    }

    @Test
    fun `requests to the host carry its login and other hosts get nothing`() {
        val auth = provider().requestHeaders(server.url("/api/filesystem/me/a.png").toString())
        assertTrue(auth["Authorization"]?.startsWith("Basic ") == true)
        assertTrue(provider().requestHeaders("https://example.com/a.png").isEmpty())
    }

    @Test
    fun `importing copies files into a folder with the import form action`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(201).setBody("""{"success": true, "value": "created"}"""))
        val result = provider().browse!!.importFiles("me/Photos", listOf("a1", "b2"))
        assertTrue(result is ApiResponse.Success)

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/filesystem/me/Photos", request.path)
        assertEquals("action=import&files=%5B%22a1%22%2C%22b2%22%5D", request.body.readUtf8())
    }

    @Test
    fun `several files are downloaded as one archive from the joined ids`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("PK-zip-bytes"))
        val out = java.io.ByteArrayOutputStream()
        val result = provider().fileStore!!.downloadArchive(
            listOf(tools.senko.materialdrain.provider.api.StorageRef(id = "a1"), tools.senko.materialdrain.provider.api.StorageRef(id = "b2")),
            out
        ) { _, _ -> }
        assertTrue(result is ApiResponse.Success)
        assertEquals("/api/file/a1,b2", server.takeRequest().path)
        assertEquals("PK-zip-bytes", out.toString(Charsets.UTF_8))
    }
}
