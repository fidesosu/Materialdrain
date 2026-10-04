package tools.senko.materialdrain.provider.api

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/** An entry inside an archive. [path] is relative to the archive's top, e.g. "test/folder1". */
data class ArchiveEntry(val name: String, val path: String, val isDirectory: Boolean, val size: Long?)

/** A file inside an archive: where the archive is on the host, and the file's path within it. */
data class ArchiveEntryDetails(val archivePath: String, val entryPath: String) : RichDetails

/**
 * Reads a `?zip_info` answer. Its shape: the root object has "children", an object which maps each entry's name to
 * its own object. A folder's object has "children" as well, a file's object only has its "size". An answer which
 * couldn't read the archive has "success": false, with "value" and "message".
 */
object ZipInfo {

    /** The direct entries of [inside] (a folder path within the archive, "" for its top). */
    fun entriesIn(json: String, inside: String): ApiResponse<List<ArchiveEntry>> {
        val root = try {
            Json.parseToJsonElement(json).jsonObject
        } catch (_: Exception) {
            return ApiResponse.Error(ProviderError("bad_response", "The archive couldn't be read."))
        }
        if (root["success"]?.jsonPrimitive?.booleanOrNull == false) {
            val code = root["value"]?.jsonPrimitive?.contentOrNull ?: "archive_error"
            val message = root["message"]?.jsonPrimitive?.contentOrNull ?: "The archive couldn't be read."
            return ApiResponse.Error(ProviderError(code, message))
        }

        var children: JsonObject = root["children"] as? JsonObject ?: JsonObject(emptyMap())
        for (segment in inside.split('/').filter { it.isNotEmpty() }) {
            val folder = children[segment] as? JsonObject
                ?: return ApiResponse.Error(ProviderError("path_not_found", "That folder isn't in the archive."))
            children = folder["children"] as? JsonObject ?: JsonObject(emptyMap())
        }

        val prefix = inside.trim('/')
        val entries = children.map { (name, value) ->
            val node = value as? JsonObject
            ArchiveEntry(
                name = name,
                path = if (prefix.isEmpty()) name else "$prefix/$name",
                // A folder is the one with its own "children"; an empty folder is written without them, so it reads as a file
                isDirectory = node?.containsKey("children") == true,
                size = node?.get("size")?.jsonPrimitive?.longOrNull,
            )
        }
        return ApiResponse.Success(entries)
    }
}
