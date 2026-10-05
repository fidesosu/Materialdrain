package tools.senko.materialdrain.provider.api

import kotlinx.serialization.json.Json
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

    fun encode(config: ProviderConfig): String {
        val (kind, element) = when (config) {
            is GenericRestConfig -> KIND_GENERIC_REST to json.encodeToJsonElement(GenericRestConfig.serializer(), config)
            is WebDavConfig -> KIND_WEBDAV to json.encodeToJsonElement(WebDavConfig.serializer(), config)
            is S3Config -> KIND_S3 to json.encodeToJsonElement(S3Config.serializer(), config)
            is SmbConfig -> KIND_SMB to json.encodeToJsonElement(SmbConfig.serializer(), config)
        }
        val withKind = JsonObject(element.jsonObject.toMutableMap().apply { put("kind", JsonPrimitive(kind)) })
        return PROVIDER_CONFIG_MARKER + "\n" + json.encodeToString(JsonObject.serializer(), withKind)
    }

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
