package tools.senko.materialdrain.provider.webdav

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import tools.senko.materialdrain.provider.api.AuthConfig
import tools.senko.materialdrain.provider.api.AuthType
import tools.senko.materialdrain.provider.api.Credentials
import tools.senko.materialdrain.provider.api.PasswordAuth
import tools.senko.materialdrain.provider.api.PasswordAuthMode
import tools.senko.materialdrain.provider.api.StorageNode
import tools.senko.materialdrain.provider.api.StorageRef
import tools.senko.materialdrain.provider.api.WebDavConfig

/** The addresses previews and thumbnails load from, and the login they need. */
class WebDavMediaUrlTest {

    private val base = "https://truenas.local:8080/dav"

    private fun provider() = WebDavStorageProvider(
        id = "truenas",
        config = WebDavConfig(
            name = "TrueNAS",
            baseUrl = base,
            auth = AuthConfig(type = AuthType.BASIC, passwordAuth = PasswordAuth(mode = PasswordAuthMode.BASIC)),
            rootPath = "/"
        ),
        credentials = { Credentials(username = "alice", password = "pw") }
    )

    private fun file(path: String, mime: String? = null) = StorageNode(
        ref = StorageRef(path = path),
        name = path.substringAfterLast('/'),
        isDirectory = false,
        mimeType = mime
    )

    @Test
    fun `a file's raw address is its path under the share`() {
        assertEquals("https://truenas.local:8080/dav/Photos/a%20b.png", provider().rawContentUrl(file("Photos/a b.png"), attachment = false))
    }

    @Test
    fun `images are their own thumbnail, other files have none`() {
        assertEquals(
            provider().rawContentUrl(file("a.png", "image/png"), false),
            provider().thumbnailUrl(file("a.png", "image/png"))
        )
        assertNull(provider().thumbnailUrl(file("notes.txt", "text/plain")))
    }

    @Test
    fun `the login is sent to the share's addresses and nowhere else`() {
        val auth = provider().requestHeaders("$base/Photos/a.png")
        assertEquals("Basic " + java.util.Base64.getEncoder().encodeToString("alice:pw".toByteArray()), auth["Authorization"])
        assertEquals(emptyMap<String, String>(), provider().requestHeaders("https://example.com/a.png"))
    }

    @Test
    fun `folders have no address`() {
        val folder = StorageNode(ref = StorageRef(path = "Photos"), name = "Photos", isDirectory = true)
        assertNull(provider().rawContentUrl(folder, attachment = false))
    }
}
