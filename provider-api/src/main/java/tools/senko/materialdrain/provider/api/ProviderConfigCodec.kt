package tools.senko.materialdrain.provider.api

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

private const val KIND_GENERIC_REST = "generic_rest"
private const val KIND_WEBDAV = "webdav"
private const val KIND_S3 = "s3"
private const val KIND_SMB = "smb"

/**
 * Encodes/decodes [ProviderConfig] as plain JSON, discriminated by a top-level "kind" field. A config is just that
 * JSON, so it can be a ".json" file which other tools (editors, syntax highlighters, `jq`) read as it is.
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
        return format.encodeToString(JsonObject.serializer(), JsonObject(fields))
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
        val obj = parseObject(text) ?: return null
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

    /** Why [text] isn't a config, for the import message: null when it is one. */
    fun explainFailure(text: String): String? {
        val body = text.trim()
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
            val message = e.message?.lineSequence()?.firstOrNull() ?: "unknown"
            missingFields(message)?.let { return "it still needs ${it.joinToString(" and ") { name -> "\"$name\"" }}" }
            "a field doesn't fit ($message)"
        }
    }

    /** The fields a decoding error says are missing, e.g. "Field 'host' is required…"; null for any other error. */
    private fun missingFields(message: String): List<String>? {
        Regex("""Field '([^']+)' is required""").find(message)?.let { return listOf(it.groupValues[1]) }
        return Regex("""Fields \[([^\]]+)] are required""").find(message)?.groupValues?.get(1)?.split(',')?.map { it.trim() }
    }

    /** [text] without its meta's update URL, everything else kept; null when it isn't a JSON object. */
    fun removeUpdateUrl(text: String): String? {
        val obj = parseObject(text) ?: return null
        val meta = obj["meta"] as? JsonObject ?: return text.trim()
        return format(JsonObject(LinkedHashMap(obj).apply { put("meta", JsonObject(meta - "update_url")) }))
    }

    /**
     * The JSON object of a config's [text], every field kept as written (in its order), whether the app reads it or not;
     * null when it isn't a JSON object. For editing a config field by field.
     */
    fun parseObject(text: String): JsonObject? {
        val body = text.trim()
        if (body.isEmpty()) return null
        return try {
            json.parseToJsonElement(body) as? JsonObject
        } catch (_: Exception) {
            null
        }
    }

    /** [obj] as a config's text: the JSON, indented. */
    fun format(obj: JsonObject): String = json.encodeToString(JsonObject.serializer(), obj)
}
