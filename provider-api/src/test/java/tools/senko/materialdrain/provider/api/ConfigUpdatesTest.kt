package tools.senko.materialdrain.provider.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfigUpdatesTest {

    private val updateUrl = "https://raw.githubusercontent.com/someone/configs/main/host.json"

    private fun config(
        version: Int = 1,
        baseUrl: String = "https://files.example.com/api",
        auth: AuthConfig = AuthConfig(type = AuthType.BEARER),
        uploadPath: String = "/upload/{filename}",
        id: String? = "io.example.host",
        updateUrl: String? = this.updateUrl,
        minAppVersion: Int? = null
    ) = GenericRestConfig(
        name = "Example",
        baseUrl = baseUrl,
        auth = auth,
        endpoints = mapOf("upload" to EndpointConfig(method = "PUT", path = uploadPath)),
        meta = ProviderConfigMeta(id = id, version = version, updateUrl = updateUrl, minAppVersion = minAppVersion)
    )

    private fun remote(config: ProviderConfig) = ProviderConfigCodec.encode(config)

    @Test
    fun `meta survives an encode and decode`() {
        val original = config(version = 7, minAppVersion = 3)
        assertEquals(original, ProviderConfigCodec.decode(ProviderConfigCodec.encode(original)))
    }

    @Test
    fun `configs without meta still decode and encode without a meta block`() {
        val plain = GenericRestConfig(name = "Plain", baseUrl = "https://x.example", endpoints = emptyMap())
        val encoded = ProviderConfigCodec.encode(plain)
        assertTrue("meta must be left out when null", !encoded.contains("\"meta\""))
        assertEquals(plain, ProviderConfigCodec.decode(encoded))
    }

    @Test
    fun `same or lower version is up to date even when content differs`() {
        val local = config(version = 3)
        assertEquals(UpdateCheckResult.UpToDate, ConfigUpdates.evaluate(local, remote(config(version = 3, uploadPath = "/v2/upload")), 1))
        assertEquals(UpdateCheckResult.UpToDate, ConfigUpdates.evaluate(local, remote(config(version = 2)), 1))
    }

    @Test
    fun `higher version with only request-shape changes is available and not sensitive`() {
        val result = ConfigUpdates.evaluate(config(version = 1), remote(config(version = 2, uploadPath = "/v2/upload/{filename}")), 1)
        result as UpdateCheckResult.Available
        assertEquals(emptyList<SensitiveChange>(), result.sensitiveChanges)
    }

    @Test
    fun `a different host is sensitive`() {
        val result = ConfigUpdates.evaluate(config(version = 1), remote(config(version = 2, baseUrl = "https://attacker.example/api")), 1)
        result as UpdateCheckResult.Available
        assertEquals(listOf(SensitiveChange.Kind.DESTINATION), result.sensitiveChanges.map { it.kind })
    }

    @Test
    fun `scheme downgrade and port changes are sensitive`() {
        val downgrade = ConfigUpdates.sensitiveChanges(config(), config(baseUrl = "http://files.example.com/api"))
        assertEquals(listOf(SensitiveChange.Kind.DESTINATION), downgrade.map { it.kind })
        val port = ConfigUpdates.sensitiveChanges(config(), config(baseUrl = "https://files.example.com:8443/api"))
        assertEquals(listOf(SensitiveChange.Kind.DESTINATION), port.map { it.kind })
    }

    @Test
    fun `userinfo tricks don't hide the real host`() {
        val changes = ConfigUpdates.sensitiveChanges(config(), config(baseUrl = "https://files.example.com@attacker.example/api"))
        assertEquals(listOf(SensitiveChange.Kind.DESTINATION), changes.map { it.kind })
    }

    @Test
    fun `an absolute endpoint on another host is sensitive`() {
        val changes = ConfigUpdates.sensitiveChanges(config(), config(uploadPath = "https://collector.example/steal/{filename}"))
        assertEquals(listOf(SensitiveChange.Kind.DESTINATION), changes.map { it.kind })
    }

    @Test
    fun `placeholders in urls don't make a host change invisible`() {
        val old = WebDavConfig(name = "Cloud", baseUrl = "https://cloud.example.com/remote.php/dav/files/{username}")
        val same = old.copy(baseUrl = "https://cloud.example.com/remote.php/webdav/{username}")
        val moved = old.copy(baseUrl = "https://other.example/remote.php/dav/files/{username}")
        assertEquals(emptyList<SensitiveChange>(), ConfigUpdates.sensitiveChanges(old, same))
        assertEquals(listOf(SensitiveChange.Kind.DESTINATION), ConfigUpdates.sensitiveChanges(old, moved).map { it.kind })
    }

    @Test
    fun `auth changes are sensitive`() {
        val type = ConfigUpdates.sensitiveChanges(config(), config(auth = AuthConfig(type = AuthType.BASIC)))
        assertEquals(listOf(SensitiveChange.Kind.AUTH), type.map { it.kind })
        val header = ConfigUpdates.sensitiveChanges(
            config(auth = AuthConfig(type = AuthType.HEADER, headerName = "X-Api-Key")),
            config(auth = AuthConfig(type = AuthType.HEADER, headerName = "X-Forward-To"))
        )
        assertEquals(listOf(SensitiveChange.Kind.AUTH), header.map { it.kind })
    }

    @Test
    fun `sign-in changes are sensitive`() {
        val login = PasswordAuth(PasswordAuthMode.LOGIN, LoginEndpoint(path = "/user/login", token = "$.auth_key"))
        val withLogin = config(auth = AuthConfig(type = AuthType.BEARER, passwordAuth = login))

        val added = ConfigUpdates.sensitiveChanges(config(), withLogin)
        assertEquals(listOf(SensitiveChange.Kind.AUTH), added.map { it.kind })

        val otherField = withLogin.copy(
            auth = withLogin.auth.copy(passwordAuth = login.copy(login = login.login!!.copy(fields = mapOf("user" to "{username}", "pass" to "{password}"))))
        )
        assertEquals(listOf(SensitiveChange.Kind.AUTH), ConfigUpdates.sensitiveChanges(withLogin, otherField).map { it.kind })

        val keyUsername = ConfigUpdates.sensitiveChanges(config(), config(auth = AuthConfig(type = AuthType.BEARER, keyUsername = "someone")))
        assertEquals(listOf(SensitiveChange.Kind.AUTH), keyUsername.map { it.kind })
    }

    @Test
    fun `a login endpoint on another host is a new destination`() {
        val elsewhere = config(
            auth = AuthConfig(
                type = AuthType.BEARER,
                passwordAuth = PasswordAuth(PasswordAuthMode.LOGIN, LoginEndpoint(path = "https://phish.example/login", token = "$.token"))
            )
        )
        val kinds = ConfigUpdates.sensitiveChanges(config(), elsewhere).map { it.kind }
        assertTrue(SensitiveChange.Kind.DESTINATION in kinds)
    }

    @Test
    fun `a new update source is sensitive but dropping it is not`() {
        val moved = ConfigUpdates.sensitiveChanges(config(), config(updateUrl = "https://elsewhere.example/host.json"))
        assertEquals(listOf(SensitiveChange.Kind.UPDATE_SOURCE), moved.map { it.kind })
        assertEquals(emptyList<SensitiveChange>(), ConfigUpdates.sensitiveChanges(config(), config(updateUrl = null)))
    }

    @Test
    fun `a different kind of host is sensitive`() {
        val webdav = WebDavConfig(name = "Example", baseUrl = "https://files.example.com/api", auth = AuthConfig(type = AuthType.BEARER))
        val kinds = ConfigUpdates.sensitiveChanges(config(), webdav).map { it.kind }
        assertTrue(SensitiveChange.Kind.KIND in kinds)
    }

    @Test
    fun `a config served under another id is rejected`() {
        val result = ConfigUpdates.evaluate(config(version = 1), remote(config(version = 5, id = "io.example.other")), 1)
        assertTrue(result is UpdateCheckResult.Invalid)
    }

    @Test
    fun `garbage at the update url is rejected`() {
        assertTrue(ConfigUpdates.evaluate(config(), "<html>404</html>", 1) is UpdateCheckResult.Invalid)
    }

    @Test
    fun `an update needing a newer app says so instead of applying`() {
        val result = ConfigUpdates.evaluate(config(version = 1), remote(config(version = 2, minAppVersion = 9)), appVersion = 4)
        result as UpdateCheckResult.NeedsNewerApp
        assertEquals(9, result.minAppVersion)
    }

    @Test
    fun `hostOf and isHttps`() {
        assertEquals("raw.githubusercontent.com", ConfigUpdates.hostOf(updateUrl))
        assertEquals("cloud.example.com", ConfigUpdates.hostOf("https://cloud.example.com/dav/{username}"))
        assertNull(ConfigUpdates.hostOf("not a url"))
        assertTrue(ConfigUpdates.isHttps(updateUrl))
        assertTrue(!ConfigUpdates.isHttps("http://raw.example/host.json"))
    }
}
