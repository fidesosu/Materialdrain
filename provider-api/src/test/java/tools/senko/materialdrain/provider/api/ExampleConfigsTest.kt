package tools.senko.materialdrain.provider.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** The ready-to-use configs in docs/provider-configs must keep loading as the config format evolves. */
class ExampleConfigsTest {

    // Unit tests run with the module directory as the working directory
    private val examplesDir = File("../docs/provider-configs")

    @Test
    fun `every example config decodes`() {
        val files = examplesDir.listFiles().orEmpty().filter { it.isFile }
        assertTrue("No example configs found in ${examplesDir.absolutePath}", files.isNotEmpty())
        files.forEach { file ->
            assertNotNull("${file.name} doesn't decode", ProviderConfigCodec.decode(file.readText()))
        }
    }

    @Test
    fun `the pixeldrain example has every generic endpoint`() {
        val config = ProviderConfigCodec.decode(File(examplesDir, "pixeldrain.json").readText()) as GenericRestConfig
        assertEquals(
            setOf(
                "upload", "download", "download_archive", "delete", "file_info", "user_info", "list",
                "browse_list", "browse_upload", "browse_download", "browse_thumbnail",
                "browse_mkdir", "browse_rename", "browse_delete", "browse_import",
                "user_lists", "list_info", "list_create", "list_update", "list_delete", "share_id", "share_path",
                "thumbnail_id", "raw_id"
            ),
            config.endpoints.keys
        )
        assertEquals("$.files", config.endpoints.getValue("list").listPath)
        assertEquals("$.id", config.endpoints.getValue("list").responseMap["id"])
        assertEquals(AuthType.BASIC, config.auth.type)
        assertEquals("", config.auth.keyUsername)
        assertEquals(PasswordAuthMode.LOGIN, config.auth.passwordAuth?.mode)
        assertEquals("$.auth_key", config.auth.passwordAuth?.login?.token)
        assertEquals(LoginBody.FORM, config.auth.passwordAuth?.login?.body)
        assertEquals(
            mapOf("username" to "{username}", "password" to "{password}", "totp" to "{otp}", "app_name" to "Materialdrain"),
            config.auth.passwordAuth?.login?.fields
        )
        assertEquals(ResponseCondition("$.value", "otp_required"), config.auth.passwordAuth?.login?.otp?.requiredWhen)
        assertEquals("/user/session", config.auth.passwordAuth?.logout?.path)
        assertEquals("tools.senko.materialdrain.examples.pixeldrain", config.meta?.id)
    }
}
