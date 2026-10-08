package tools.senko.materialdrain.provider.api

import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** What the import screen says about a config text: nothing when it's fine, the reason when it isn't. */
class ImportReasonTest {

    @Test
    fun `the shipped pixeldrain config imports`() {
        val text = File("../docs/provider-configs/pixeldrain.json").readText()
        assertNull(ProviderConfigCodec.explainFailure(text))
    }

    @Test
    fun `broken json says where it breaks`() {
        val reason = ProviderConfigCodec.explainFailure("{\"kind\": \"generic_rest\", \"name\": ")
        assertNotNull(reason)
        assertTrue(reason!!.contains("JSON"))
    }

    @Test
    fun `a missing kind is named`() {
        assertTrue(ProviderConfigCodec.explainFailure("{\"name\": \"x\"}")!!.contains("kind"))
    }

    @Test
    fun `missing required fields are named plainly`() {
        val reason = ProviderConfigCodec.explainFailure("{\"kind\": \"smb\", \"name\": \"x\", \"share\": \"Docs\"}")!!
        assertTrue(reason, reason.startsWith("it still needs") && reason.contains("\"host\""))
    }

    @Test
    fun `an unknown kind is named`() {
        assertTrue(ProviderConfigCodec.explainFailure("{\"kind\": \"ftp\", \"name\": \"x\"}")!!.contains("ftp"))
    }
}
