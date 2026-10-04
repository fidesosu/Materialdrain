package tools.senko.materialdrain.provider.genericrest

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Resolves dot-paths like "$.file.id" or "filename" into a JSON response. Object-key traversal only —
 * no array indexing — since a config author writing "which item" logic is exactly the scripting-language
 * trap a plain field-mapping config is meant to avoid.
 */
internal object DotPath {
    fun resolve(root: JsonElement, path: String): JsonElement? {
        var current: JsonElement? = root
        path.removePrefix("$").split('.').filter { it.isNotEmpty() }.forEach { key ->
            val obj = current as? JsonObject ?: return null
            current = obj[key]
        }
        return current
    }

    fun resolveString(root: JsonElement, path: String): String? {
        val element = resolve(root, path) ?: return null
        if (element is JsonNull) return null
        return try {
            element.jsonPrimitive.content
        } catch (_: Exception) {
            null
        }
    }

    fun resolveLong(root: JsonElement, path: String): Long? = resolveString(root, path)?.toLongOrNull()

    /** The array at [path] (e.g. "$.files"), or [root] itself when [path] is null and is already an array. */
    fun resolveArray(root: JsonElement, path: String?): JsonArray? = (if (path == null) root else resolve(root, path)) as? JsonArray

    /**
     * The individual problems of a response that wraps several in one, e.g. Pixeldrain's "multiple_errors":
     * {"value": "multiple_errors", "message": "...", "errors": [{"value": "missing_field", ...}]}. Empty when
     * [body] isn't that shape, so callers can uniformly check "the top level, or any of these" in one place.
     */
    fun errorEntries(body: JsonElement?): List<JsonElement> = ((body as? JsonObject)?.get("errors") as? JsonArray).orEmpty()

    /**
     * The most specific message in an error response, for display. The wrapper's own "message" (see
     * [errorEntries]) only restates that there's a list to check, so when there are entries, the first one's
     * own message (plus which field it's about, if any) is what's actually useful; the plain top-level
     * "message" otherwise.
     */
    fun errorMessage(body: JsonElement?): String? {
        val detail = errorEntries(body).firstOrNull()?.let { error ->
            val message = resolveString(error, "message")
            val field = resolveString(error, "extra.field")
            listOfNotNull(message, field?.let { "field: $it" }).joinToString(", ").ifBlank { null }
        }
        return detail ?: body?.let { resolveString(it, "message") }
    }
}
