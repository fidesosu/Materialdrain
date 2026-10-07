package tools.senko.materialdrain.provider.api

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * First line of every exported/imported provider config. Lets the app recognize its own config text (a
 * pasted clipboard blob, an imported file) at a glance, without attempting to parse it as JSON first.
 */
const val PROVIDER_CONFIG_MARKER = "MATERIALDRAIN-PROVIDER-CONFIG-V1"

private const val KIND_GENERIC_REST = "generic_rest"
private const val KIND_WEBDAV = "webdav"
private const val KIND_S3 = "s3"
private const val KIND_SMB = "smb"

/**
 * Encodes/decodes [ProviderConfig] as JSON, discriminated by a top-level "kind" field. [encode] always
 * writes the `MARKER\n{json}` form, so pasting an exported config back in is unmistakably recognized. A
 * hand-written config can skip the marker line and just be plain JSON, e.g. as a ".json" file that other
 * tools (editors, syntax highlighters, `jq`) can read as-is — [decode] accepts both.
 */
object ProviderConfigCodec {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true; prettyPrint = true }

    /** Writes every field, set or not: for templates, which show everything a config of their kind can have. */
    private val fullJson = Json(json) { encodeDefaults = true; explicitNulls = true }

    /**
     * The config as text. With [allFields], every field the app reads is written, even those left at their defaults,
     * and every screen in its full form (see [ProviderConfigTemplates]); otherwise only what differs from the defaults.
     */
    fun encode(config: ProviderConfig, allFields: Boolean = false): String {
        val format = if (allFields) fullJson else json
        val (kind, element) = when (config) {
            is GenericRestConfig -> KIND_GENERIC_REST to format.encodeToJsonElement(GenericRestConfig.serializer(), config)
            is WebDavConfig -> KIND_WEBDAV to format.encodeToJsonElement(WebDavConfig.serializer(), config)
            is S3Config -> KIND_S3 to format.encodeToJsonElement(S3Config.serializer(), config)
            is SmbConfig -> KIND_SMB to format.encodeToJsonElement(SmbConfig.serializer(), config)
        }
        // The kind first, as it decides what the rest means
        val fields = linkedMapOf<String, JsonElement>("kind" to JsonPrimitive(kind))
        element.jsonObject.forEach { (key, value) ->
            fields[key] = if (allFields && key == "screens" && value is JsonArray) fullScreens(value) else value
        }
        return PROVIDER_CONFIG_MARKER + "\n" + format.encodeToString(JsonObject.serializer(), JsonObject(fields))
    }

    /** Screens written by name alone (see ScreenEntrySerializer), spelled out with every field instead. */
    private fun fullScreens(screens: JsonArray): JsonArray = JsonArray(screens.map { entry ->
        val obj = entry as? JsonObject ?: JsonObject(mapOf("screen" to entry))
        JsonObject(
            linkedMapOf(
                "screen" to obj.getValue("screen"),
                "name" to (obj["name"] ?: JsonNull),
                "disabled_capabilities" to (obj["disabled_capabilities"] ?: JsonArray(emptyList()))
            )
        )
    })

    /** Returns null when [text] isn't a Materialdrain provider config (unknown kind, or malformed JSON). */
    fun decode(text: String): ProviderConfig? {
        val markerLine = text.lineSequence().firstOrNull()?.trim()
        val body = if (markerLine == PROVIDER_CONFIG_MARKER) text.substringAfter('\n', missingDelimiterValue = "") else text
        return decodeBody(body.trim())
    }

    private fun decodeBody(body: String): ProviderConfig? {
        if (body.isEmpty()) return null
        val obj = try {
            json.parseToJsonElement(body).jsonObject
        } catch (_: Exception) {
            return null
        }
        val kind = obj["kind"]?.jsonPrimitive?.content ?: return null
        return try {
            when (kind) {
                KIND_GENERIC_REST -> json.decodeFromJsonElement(GenericRestConfig.serializer(), obj)
                KIND_WEBDAV -> json.decodeFromJsonElement(WebDavConfig.serializer(), obj)
                KIND_S3 -> json.decodeFromJsonElement(S3Config.serializer(), obj)
                KIND_SMB -> json.decodeFromJsonElement(SmbConfig.serializer(), obj)
                else -> null
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Why [text] isn't a config, for the import message: null when it is one. The first line doesn't matter, the
     * marker is optional; what matters is the JSON after it.
     */
    fun explainFailure(text: String): String? {
        val markerLine = text.lineSequence().firstOrNull()?.trim()
        val body = (if (markerLine == PROVIDER_CONFIG_MARKER) text.substringAfter('\n', missingDelimiterValue = "") else text).trim()
        if (body.isEmpty()) return "the text is empty"
        val obj = try {
            json.parseToJsonElement(body).jsonObject
        } catch (e: Exception) {
            return "the JSON can't be read (${e.message?.lineSequence()?.firstOrNull() ?: "syntax error"})"
        }
        val kind = obj["kind"]?.jsonPrimitive?.content ?: return "there is no \"kind\" field"
        if (kind !in setOf(KIND_GENERIC_REST, KIND_WEBDAV, KIND_S3, KIND_SMB)) return "the kind \"$kind\" is unknown"
        return try {
            when (kind) {
                KIND_GENERIC_REST -> json.decodeFromJsonElement(GenericRestConfig.serializer(), obj)
                KIND_WEBDAV -> json.decodeFromJsonElement(WebDavConfig.serializer(), obj)
                KIND_SMB -> json.decodeFromJsonElement(SmbConfig.serializer(), obj)
                else -> json.decodeFromJsonElement(S3Config.serializer(), obj)
            }
            null
        } catch (e: Exception) {
            "a field doesn't fit (${e.message?.lineSequence()?.firstOrNull() ?: "unknown"})"
        }
    }

    /** Cheap check for a paste/import sheet: is this text even worth trying to [decode]? */
    fun looksLikeProviderConfig(text: String): Boolean {
        val trimmed = text.trim()
        if (trimmed.lineSequence().firstOrNull()?.trim() == PROVIDER_CONFIG_MARKER) return true
        // Plain JSON fallback: not a full parse, just enough to skip obviously-unrelated text
        return trimmed.startsWith("{") && trimmed.contains("\"kind\"")
    }
}
