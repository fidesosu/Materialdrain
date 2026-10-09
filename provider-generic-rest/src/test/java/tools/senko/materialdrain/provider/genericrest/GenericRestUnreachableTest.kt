package tools.senko.materialdrain.provider.genericrest

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import tools.senko.materialdrain.provider.api.ApiResponse
import tools.senko.materialdrain.provider.api.Credentials
import tools.senko.materialdrain.provider.api.EndpointConfig
import tools.senko.materialdrain.provider.api.GenericRestConfig
import tools.senko.materialdrain.provider.api.StorageRef
import java.io.ByteArrayOutputStream

/**
 * A host that can't be reached (the network gone, the connection dropped) gives errors, never exceptions: the screens
 * only expect errors as results, and an exception escaping a delete used to crash the app.
 */
class GenericRestUnreachableTest {

    private lateinit var provider: GenericRestStorageProvider

    @Before
    fun setUp() {
        // A server that has been shut down: its address refuses every connection
        val server = MockWebServer()
        server.start()
        val baseUrl = server.url("/api").toString()
        server.shutdown()

        val endpoints = listOf(
            "list", "file_info", "download", "delete", "user_info",
            "browse_list", "browse_download", "browse_mkdir", "browse_rename", "browse_delete",
            "user_lists", "list_info", "list_delete"
        ).associateWith { EndpointConfig(method = "GET", path = "/$it") }
        provider = GenericRestStorageProvider(
            id = "test",
            config = GenericRestConfig(name = "Unreachable", baseUrl = baseUrl, endpoints = endpoints),
            credentials = { Credentials(apiKey = "key") }
        )
    }

    private fun assertNetworkError(response: ApiResponse<*>) {
        val error = (response as? ApiResponse.Error)?.error
        assertEquals("network_error", error?.code)
    }

    @Test
    fun `deleting gives an error instead of throwing`() = runBlocking {
        assertNetworkError(provider.fileStore.delete(StorageRef(id = "a1")))
        assertNetworkError(provider.browse!!.delete("folder/file.txt", recursive = false))
        assertNetworkError(provider.lists!!.delete("list1"))
    }

    @Test
    fun `listing and changing folders give an error instead of throwing`() = runBlocking {
        assertNetworkError(provider.fileList!!.list())
        assertNetworkError(provider.browse!!.list("folder"))
        assertNetworkError(provider.browse!!.createDirectory("folder/new", makeParents = false))
        assertNetworkError(provider.browse!!.rename("folder/a", "folder/b", makeParents = false))
        assertNetworkError(provider.lists!!.lists())
        assertNetworkError(provider.lists!!.listContents("list1"))
        assertNetworkError(provider.account!!.accountInfo())
        assertNetworkError(provider.fileStore.fileInfo(StorageRef(id = "a1")))
    }

    @Test
    fun `downloading gives an error instead of throwing`() = runBlocking {
        assertNetworkError(provider.fileStore.download(StorageRef(id = "a1"), ByteArrayOutputStream()) { _, _ -> })
        assertNetworkError(provider.browse!!.download("folder/file.txt", ByteArrayOutputStream()) { _, _ -> })
    }
}
