package tools.senko.materialdrain.provider.genericrest

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DotPathTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `resolves a nested field`() {
        val body = json.parseToJsonElement("""{"file":{"id":"abc123","size":42}}""")
        assertEquals("abc123", DotPath.resolveString(body, "$.file.id"))
        assertEquals(42L, DotPath.resolveLong(body, "$.file.size"))
    }

    @Test
    fun `resolves a top level field without the leading dollar`() {
        val body = json.parseToJsonElement("""{"username":"fides"}""")
        assertEquals("fides", DotPath.resolveString(body, "username"))
    }

    @Test
    fun `returns null for a missing or null field`() {
        val body = json.parseToJsonElement("""{"file":{"id":null}}""")
        assertNull(DotPath.resolveString(body, "$.file.id"))
        assertNull(DotPath.resolveString(body, "$.file.missing"))
        assertNull(DotPath.resolveString(body, "$.missing.also_missing"))
    }

    @Test
    fun `does not traverse into arrays`() {
        val body = json.parseToJsonElement("""{"files":[{"id":"a"},{"id":"b"}]}""")
        assertNull(DotPath.resolveString(body, "$.files.id"))
    }

    @Test
    fun `resolveArray finds the array at a path, and the whole body when there's no path`() {
        val wrapped = json.parseToJsonElement("""{"files":[{"id":"a"},{"id":"b"}]}""")
        val array = DotPath.resolveArray(wrapped, "$.files")
        assertEquals(listOf("a", "b"), array?.map { DotPath.resolveString(it, "id") })

        val bare = json.parseToJsonElement("""[{"id":"a"}]""")
        assertEquals(listOf("a"), DotPath.resolveArray(bare, null)?.map { DotPath.resolveString(it, "id") })
    }

    @Test
    fun `resolveArray returns null when the path isn't an array`() {
        val body = json.parseToJsonElement("""{"files":{"id":"not an array"}}""")
        assertNull(DotPath.resolveArray(body, "$.files"))
        assertNull(DotPath.resolveArray(body, "$.missing"))
        assertNull(DotPath.resolveArray(body, null))
    }
}
