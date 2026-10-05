package tools.senko.materialdrain.provider.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderConfigCodecTest {

    @Test
    fun `round trips a generic rest config`() {
        val config = GenericRestConfig(
            name = "My Homelab Uploader",
            baseUrl = "https://files.example.com/api",
            auth = AuthConfig(type = AuthType.BEARER),
            endpoints = mapOf(
                "upload" to EndpointConfig(
                    method = "PUT",
                    path = "/upload/{filename}",
                    body = BodyEncoding.RAW,
                    successStatus = listOf(200, 201),
                    responseMap = mapOf("id" to "$.file.id")
                )
            )
        )

        val encoded = ProviderConfigCodec.encode(config)
        assertTrue(encoded.startsWith(PROVIDER_CONFIG_MARKER + "\n"))

        val decoded = ProviderConfigCodec.decode(encoded)
        assertEquals(config, decoded)
    }

    @Test
    fun `round trips webdav and s3 configs`() {
        val webdav = WebDavConfig(name = "Nextcloud", baseUrl = "https://cloud.example.com/remote.php/dav/files/{username}")
        assertEquals(webdav, ProviderConfigCodec.decode(ProviderConfigCodec.encode(webdav)))

        val s3 = S3Config(name = "MinIO", endpoint = "https://nas.example.com:9000", bucket = "materialdrain")
        assertEquals(s3, ProviderConfigCodec.decode(ProviderConfigCodec.encode(s3)))
    }

    @Test
    fun `round trips an smb config and fills in the defaults of a hand-written one`() {
        val smb = SmbConfig(
            name = "Office share", host = "nas.example.com", share = "Public", rootPath = "projects",
            domain = "WORKGROUP", auth = SmbAuthMode.CREDENTIALS, minVersion = SmbMinVersion.SMB3, encrypt = true
        )
        assertEquals(smb, ProviderConfigCodec.decode(ProviderConfigCodec.encode(smb)))

        val handWritten = """{"kind": "smb", "name": "Share", "host": "10.0.2.2", "share": "MDTest"}"""
        assertEquals(
            SmbConfig(name = "Share", host = "10.0.2.2", share = "MDTest"),
            ProviderConfigCodec.decode(handWritten)
        )
        assertNull(ProviderConfigCodec.explainFailure(handWritten))
    }

    @Test
    fun `a hand-written config without the marker line decodes as plain json`() {
        val plainJson = """{"kind":"generic_rest","name":"x","base_url":"https://x","endpoints":{}}"""
        val decoded = ProviderConfigCodec.decode(plainJson) as GenericRestConfig
        assertEquals("x", decoded.name)
        assertEquals("https://x", decoded.baseUrl)
    }

    @Test
    fun `rejects text that is neither marker-prefixed nor json`() {
        assertNull(ProviderConfigCodec.decode("some random pasted text"))
    }

    @Test
    fun `rejects malformed json, with or without a correct marker`() {
        assertNull(ProviderConfigCodec.decode(PROVIDER_CONFIG_MARKER + "\nnot json at all"))
        assertNull(ProviderConfigCodec.decode("not json at all"))
    }

    @Test
    fun `looksLikeProviderConfig accepts the marker line or plain json with a kind field`() {
        assertTrue(ProviderConfigCodec.looksLikeProviderConfig(PROVIDER_CONFIG_MARKER + "\n{}"))
        assertTrue(ProviderConfigCodec.looksLikeProviderConfig(PROVIDER_CONFIG_MARKER))
        assertTrue(ProviderConfigCodec.looksLikeProviderConfig("  $PROVIDER_CONFIG_MARKER  \n{}"))
        assertTrue(ProviderConfigCodec.looksLikeProviderConfig("""{"kind":"generic_rest"}"""))
        assertTrue(!ProviderConfigCodec.looksLikeProviderConfig("{}"))
        assertTrue(!ProviderConfigCodec.looksLikeProviderConfig("some random pasted text"))
    }
}
