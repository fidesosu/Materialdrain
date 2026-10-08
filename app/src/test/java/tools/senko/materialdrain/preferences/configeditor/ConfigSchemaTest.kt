package tools.senko.materialdrain.preferences.configeditor

import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import tools.senko.materialdrain.provider.api.ProviderConfigCodec
import tools.senko.materialdrain.provider.api.ProviderConfigTemplates
import java.io.File

/** The editor's form has a field for everything a config can hold, so a field added to a kind can't be forgotten there. */
class ConfigSchemaTest {

    /** The keys of [obj] the [fields] don't describe, nested objects included, as dotted paths. */
    private fun undescribed(obj: JsonObject, fields: List<FieldSpec>, prefix: String = ""): List<String> = obj.flatMap { (key, value) ->
        val spec = fields.firstOrNull { it.key == key } ?: return@flatMap listOf(prefix + key)
        val group = spec.kind as? FieldKind.Group
        if (group != null && value is JsonObject) undescribed(value, group.fields, "$prefix$key.") else emptyList()
    }

    @Test
    fun `every field of every template has a place in the form`() {
        ProviderConfigTemplates.Kind.entries.forEach { kind ->
            val obj = ProviderConfigCodec.parseObject(ProviderConfigTemplates.text(kind))!!
            val sections = sectionsFor(obj.textOf("kind"))
            assertNotNull("$kind", sections)
            val fields = sections!!.flatMap { it.fields } + FieldSpec("kind", "", "")
            assertEquals("$kind", emptyList<String>(), undescribed(obj, fields))
        }
    }

    @Test
    fun `the bundled pixeldrain config is described, endpoints and sign-in included`() {
        val obj = ProviderConfigCodec.parseObject(File("../docs/provider-configs/pixeldrain.json").readText())!!
        val sections = sectionsFor("generic_rest")!!
        val fields = sections.flatMap { it.fields } + FieldSpec("kind", "", "")
        assertEquals(emptyList<String>(), undescribed(obj, fields))

        val endpoints = obj["endpoints"] as JsonObject
        val known = KnownEndpoints.associateBy { it.name }
        endpoints.forEach { (name, endpoint) ->
            val info = known[name]
            assertNotNull("endpoint $name", info)
            assertEquals("endpoint $name", emptyList<String>(), undescribed(endpoint as JsonObject, info!!.fields))
        }
    }

    @Test
    fun `fields not described are other fields`() {
        val keys = describedKeys(sectionsFor("smb")!!)
        assertTrue("port" in keys && "kind" in keys && "notes" !in keys)
    }
}
