package tools.senko.materialdrain.provider.genericrest

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
import tools.senko.materialdrain.provider.api.EndpointConfig
import tools.senko.materialdrain.provider.api.GenericRestConfig
import tools.senko.materialdrain.provider.api.ProviderCapability

/** End-to-end: config -> provider -> real HTTP call -> normalized [tools.senko.materialdrain.provider.api.StorageListing]. */
class GenericRestStorageProviderListTest {

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

    private fun provider(config: GenericRestConfig) =
        GenericRestStorageProvider(id = "test", config = config, credentials = { Credentials() })

    @Test
    fun `lists files from a wrapped array using list_path and the shared field mapping`() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"files":[{"id":"a1","name":"one.txt","size":10,"mime_type":"text/plain"},
                             {"id":"a2","name":"two.png","size":20,"mime_type":"image/png"}]}"""
            )
        )
        val config = GenericRestConfig(
            name = "Test",
            baseUrl = server.url("/api").toString(),
            endpoints = mapOf(
                "list" to EndpointConfig(
                    method = "GET",
                    path = "/user/files",
                    listPath = "$.files",
                    responseMap = mapOf("id" to "$.id", "name" to "$.name", "size" to "$.size", "mime_type" to "$.mime_type")
                ),
                "upload" to EndpointConfig(method = "PUT", path = "/file/{filename}")
            )
        )

        val provider = provider(config)
        assertTrue(ProviderCapability.ENUMERATE in provider.capabilities)
        val result = provider.fileList!!.list() as ApiResponse.Success
        assertEquals(listOf("one.txt", "two.png"), result.data.children.map { it.name })
        assertEquals(listOf("a1", "a2"), result.data.children.map { it.ref.id })
        assertEquals(10L, result.data.children[0].size)
        assertTrue("UPLOAD is configured, so listing should report the host as writable", result.data.canWrite)
        assertEquals(false, result.data.canDelete)
    }

    @Test
    fun `no list endpoint means no ENUMERATE capability and a null fileList`() {
        val config = GenericRestConfig(name = "Test", baseUrl = "https://example.com", endpoints = emptyMap())
        val provider = provider(config)
        assertTrue(ProviderCapability.ENUMERATE !in provider.capabilities)
        assertEquals(null, provider.fileList)
    }

    @Test
    fun `a response without the expected array fails clearly instead of crashing`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"files":"not an array"}"""))
        val config = GenericRestConfig(
            name = "Test",
            baseUrl = server.url("/api").toString(),
            endpoints = mapOf("list" to EndpointConfig(method = "GET", path = "/user/files", listPath = "$.files"))
        )
        val result = provider(config).fileList!!.list()
        assertTrue(result is ApiResponse.Error)
    }
}
