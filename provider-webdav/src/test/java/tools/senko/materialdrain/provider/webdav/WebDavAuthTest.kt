package tools.senko.materialdrain.provider.webdav

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import tools.senko.materialdrain.provider.api.AuthConfig
import tools.senko.materialdrain.provider.api.AuthType
import tools.senko.materialdrain.provider.api.Credentials
import tools.senko.materialdrain.provider.api.PasswordAuth
import tools.senko.materialdrain.provider.api.PasswordAuthMode
import java.util.Base64

class WebDavAuthTest {

    private fun decodeBasic(header: String) = String(Base64.getDecoder().decode(header.removePrefix("Basic ")))

    @Test
    fun `no credentials means no header`() {
        assertNull(WebDavAuth.resolve(AuthConfig(type = AuthType.BASIC), Credentials()))
    }

    @Test
    fun `an api key is sent basic-auth style, with the fixed username from the config`() {
        // The Nextcloud "app password" convention: the account name lives in the config, the per-device
        // app password is what the user enters as the API key.
        val auth = AuthConfig(type = AuthType.BASIC, keyUsername = "alice")
        val header = WebDavAuth.resolve(auth, Credentials(apiKey = "app-password"))
        assertEquals("alice:app-password", decodeBasic(header!!.second))
        assertEquals("Authorization", header.first)
    }

    @Test
    fun `an explicit username and password win over the api key when passwordAuth is BASIC`() {
        val auth = AuthConfig(type = AuthType.BASIC, passwordAuth = PasswordAuth(PasswordAuthMode.BASIC))
        val header = WebDavAuth.resolve(auth, Credentials(apiKey = "unused", username = "bob", password = "secret"))
        assertEquals("bob:secret", decodeBasic(header!!.second))
    }

    @Test
    fun `falls back to the api key when the username or password is missing, even with passwordAuth BASIC configured`() {
        val auth = AuthConfig(type = AuthType.BASIC, keyUsername = "svc", passwordAuth = PasswordAuth(PasswordAuthMode.BASIC))
        val header = WebDavAuth.resolve(auth, Credentials(apiKey = "key", username = "bob"))
        assertEquals("svc:key", decodeBasic(header!!.second))
    }

    @Test
    fun `bearer and custom header auth types`() {
        assertEquals("Bearer tok", WebDavAuth.resolve(AuthConfig(type = AuthType.BEARER), Credentials(apiKey = "tok"))!!.second)
        val custom = WebDavAuth.resolve(AuthConfig(type = AuthType.HEADER, headerName = "X-Api-Key"), Credentials(apiKey = "tok"))
        assertEquals("X-Api-Key" to "tok", custom)
    }

    @Test
    fun `AuthType NONE never sends an api key header`() {
        assertNull(WebDavAuth.resolve(AuthConfig(type = AuthType.NONE), Credentials(apiKey = "key")))
    }
}
