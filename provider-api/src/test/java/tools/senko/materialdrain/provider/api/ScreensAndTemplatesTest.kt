package tools.senko.materialdrain.provider.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreensAndTemplatesTest {

    private val folderHost = setOf(ProviderCapability.UPLOAD, ProviderCapability.BROWSE, ProviderCapability.MKDIR)
    private val everything = folderHost + ProviderCapability.ENUMERATE + ProviderCapability.LISTS

    private fun smb(screens: String?): SmbConfig {
        val field = screens?.let { ""","screens": $it""" }.orEmpty()
        return ProviderConfigCodec.decode("""{"kind": "smb", "name": "NAS", "host": "nas", "share": "Docs"$field}""") as SmbConfig
    }

    @Test
    fun `screens left out show every screen the host can back`() {
        assertNull(smb(null).screens)
        assertEquals(listOf(HostScreen.UPLOAD, HostScreen.FILESYSTEM), resolveScreens(null, folderHost).map { it.screen })
        assertEquals(HostScreen.entries.toList(), resolveScreens(null, everything).map { it.screen })
    }

    @Test
    fun `an empty list shows none`() {
        assertEquals(emptyList<ScreenConfig>(), smb("[]").screens)
        assertTrue(resolveScreens(emptyList(), everything).isEmpty())
    }

    @Test
    fun `screens can be written by name, in any case, or as objects`() {
        val screens = smb("""["filesystem", "Upload", {"screen": "files", "name": "All"}]""").screens!!
        assertEquals(listOf(HostScreen.FILESYSTEM, HostScreen.UPLOAD, HostScreen.FILES), screens.map { it.screen })
        assertEquals("All", screens[2].name)
    }

    @Test
    fun `screens the host can't back are left out, and repeats count once, in the config's order`() {
        val chosen = listOf(HostScreen.FILESYSTEM, HostScreen.LISTS, HostScreen.FILESYSTEM, HostScreen.UPLOAD).map { ScreenConfig(it) }
        assertEquals(listOf(HostScreen.FILESYSTEM, HostScreen.UPLOAD), resolveScreens(chosen, folderHost).map { it.screen })
    }

    @Test
    fun `a screen with nothing else set is written back by name`() {
        val config = SmbConfig(
            name = "NAS", host = "nas", share = "Docs",
            screens = listOf(ScreenConfig(HostScreen.FILESYSTEM), ScreenConfig(HostScreen.UPLOAD, name = "Send"))
        )
        val text = ProviderConfigCodec.encode(config)
        assertTrue(text, text.contains("\"FILESYSTEM\""))
        assertTrue(text, text.contains("\"name\": \"Send\""))
        assertEquals(config, ProviderConfigCodec.decode(text))
    }

    @Test
    fun `every template loads back as the config it was made from, with every field written`() {
        ProviderConfigTemplates.Kind.entries.forEach { kind ->
            val text = ProviderConfigTemplates.text(kind)
            val decoded = ProviderConfigCodec.decode(text)
            assertNotNull("$kind template doesn't decode:\n$text", decoded)
            assertEquals(ProviderConfigTemplates.config(kind), decoded)
            // Fields left at their defaults are written too, and the screens in full
            assertTrue(text, text.contains("\"disabled_capabilities\""))
            // Plain JSON from the first line
            assertTrue(text, text.lines()[0].trim() == "{" && text.contains("\"kind\""))
        }
        val smb = ProviderConfigTemplates.text(ProviderConfigTemplates.Kind.SMB)
        listOf("host", "port", "share", "root_path", "domain", "auth", "min_version", "encrypt", "screens", "meta").forEach {
            assertTrue("SMB template has no $it:\n$smb", smb.contains("\"$it\""))
        }
    }

    @Test
    fun `an exported config without screens stays without them`() {
        val text = ProviderConfigCodec.encode(SmbConfig(name = "NAS", host = "nas", share = "Docs"))
        assertFalse(text, text.contains("screens"))
    }
}
