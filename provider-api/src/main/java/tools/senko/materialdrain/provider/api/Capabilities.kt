package tools.senko.materialdrain.provider.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonTransformingSerializer
import kotlinx.serialization.json.buildJsonObject

/**
 * A tab of the app a config can turn on. Separate from [ProviderCapability]: capability is what a host can do,
 * this is what the config's author chose to show for it (see [ProviderConfig.screens] and [resolveScreens]).
 */
@Serializable
enum class HostScreen {
    UPLOAD, FILES, LISTS, FILESYSTEM;

    /** Whether a host that can do [capabilities] can back this screen; one that can't would only show errors. */
    fun isBackedBy(capabilities: Set<ProviderCapability>): Boolean = when (this) {
        UPLOAD -> ProviderCapability.UPLOAD in capabilities
        FILES -> ProviderCapability.ENUMERATE in capabilities
        LISTS -> ProviderCapability.LISTS in capabilities
        FILESYSTEM -> ProviderCapability.BROWSE in capabilities
    }
}

/**
 * One screen a config turns on, in the order the tabs are shown. [name] overrides the screen's default label
 * ("Files", "Lists", "Filesystem", "Upload") when it means something more specific for this host, e.g. a Filesystem
 * screen that's really one NAS share called "Media". [disabledCapabilities] takes away actions this screen would
 * otherwise offer (what the host actually supports, from [StorageProvider.capabilities]) without touching the
 * other screens sharing the same host and the same underlying code — e.g. a Filesystem screen with `MKDIR` in here
 * has no "new folder" button even though the host (and every other screen of it) can make one.
 *
 * Written in a config either as an object, or as just the screen's name when nothing else is set: `"FILESYSTEM"` is
 * `{"screen": "FILESYSTEM"}`. Screen names are read in any case (see [ScreenEntrySerializer]).
 */
@Serializable
data class ScreenConfig(
    val screen: HostScreen,
    val name: String? = null,
    @SerialName("disabled_capabilities") val disabledCapabilities: Set<ProviderCapability> = emptySet()
)

/**
 * Reads a [ScreenConfig] from its name alone ("FILESYSTEM", "filesystem") or from an object, and writes one back as
 * just its name when it has nothing but that, so exported configs stay short.
 */
object ScreenEntrySerializer : JsonTransformingSerializer<ScreenConfig>(ScreenConfig.serializer()) {
    override fun transformDeserialize(element: JsonElement): JsonElement = when {
        element is JsonPrimitive && element.isString -> buildJsonObject { put("screen", JsonPrimitive(element.content.trim().uppercase())) }
        element is JsonObject -> {
            val screen = element["screen"] as? JsonPrimitive
            if (screen != null && screen.isString) {
                JsonObject(element.toMutableMap().apply { put("screen", JsonPrimitive(screen.content.trim().uppercase())) })
            } else {
                element
            }
        }
        else -> element
    }

    override fun transformSerialize(element: JsonElement): JsonElement {
        val obj = element as? JsonObject ?: return element
        val nameUnset = obj["name"].let { it == null || it is JsonNull }
        val nothingDisabled = (obj["disabled_capabilities"] as? JsonArray)?.isEmpty() ?: true
        return if (nameUnset && nothingDisabled) obj.getValue("screen") else obj
    }
}

/**
 * The screens to show for a host made from [screens] (a config's own choice, or null when it didn't make one), for a
 * host that can do [capabilities]:
 *
 * - not chosen: every screen the host can back, in the usual order (Upload, Files, Lists, Filesystem)
 * - chosen: those, in the config's order, without the ones the host can't back (they would only show errors) and
 *   without repeats (the first one counts)
 *
 * Empty when the config chose none (`"screens": []`), or only ones the host can't back.
 */
fun resolveScreens(screens: List<ScreenConfig>?, capabilities: Set<ProviderCapability>): List<ScreenConfig> =
    (screens ?: HostScreen.entries.map { ScreenConfig(it) })
        .filter { it.screen.isBackedBy(capabilities) }
        .distinctBy { it.screen }

/** What a [StorageProvider] can do. The UI reads these instead of guessing from which ops are non-null. */
@Serializable
enum class ProviderCapability {
    UPLOAD, DOWNLOAD, DELETE, FILE_INFO,
    BROWSE, MKDIR, RENAME,
    // A flat "give me everything on the account" call - distinct from BROWSE, which is hierarchical
    // (folders, mkdir, rename). Named to not read next to LISTS as a one-letter typo of it.
    ENUMERATE,
    SEARCH, SHARE_LINK, PERMISSIONS,
    USER_QUOTA, RICH_FILE_STATS, LISTS,
    // Several files at once as one zip archive (Pixeldrain: comma separated ids). See FileStoreOps.downloadArchive
    ARCHIVE_DOWNLOAD,
    // Looking inside an archive (zip, 7z, rar...) without downloading it. See ArchiveOps
    ARCHIVE_BROWSE
}

enum class ProviderKind { PIXELDRAIN, WEBDAV, S3, GENERIC_REST, SMB }
