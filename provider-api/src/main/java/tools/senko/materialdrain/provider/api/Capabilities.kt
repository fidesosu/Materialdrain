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
 * [id] tells the tabs apart, so one kind can be shown more than once: two Filesystem tabs, each with its own [root]
 * (say "Media" and "Backups" on one NAS), its own name and its own actions. Left out, it's the screen's kind in lower
 * case ("filesystem"), so a config with one tab of each kind needs none. What the app keeps of a tab (the folder open in
 * it) goes by its id, so it survives the tabs being put in another order.
 *
 * [root] is for a Filesystem tab: the folder it opens at, under the host's own top (a config's browse_root, a share's
 * root_path), which it can't go above. Left out, the tab has the whole host.
 *
 * [icon] is the tab's own icon, by name (the app knows a set of them, e.g. "photo", "music", "backup"; another name
 * keeps the screen's icon). [section] puts the tab in a group of that name in the navigation button's menu, where the
 * tabs without one sit in the row nearest the button; the bottom bar has every tab either way.
 *
 * Written in a config either as an object, or as just the screen's name when nothing else is set: `"FILESYSTEM"` is
 * `{"screen": "FILESYSTEM"}`. Screen names are read in any case (see [ScreenEntrySerializer]).
 */
@Serializable
data class ScreenConfig(
    val screen: HostScreen,
    val id: String? = null,
    val name: String? = null,
    val root: String? = null,
    @SerialName("disabled_capabilities") val disabledCapabilities: Set<ProviderCapability> = emptySet(),
    val icon: String? = null,
    val section: String? = null
) {
    /** What tells this tab apart from the others of the host: its own [id], or its kind's name. */
    val tabId: String get() = id?.trim()?.takeIf { it.isNotEmpty() } ?: screen.name.lowercase()

    /** [root] without slashes around it; "" for the whole host. */
    val rootFolder: String get() = root?.trim()?.trim('/').orEmpty()
}

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
        val unset = { key: String -> obj[key].let { it == null || it is JsonNull } }
        val nothingDisabled = (obj["disabled_capabilities"] as? JsonArray)?.isEmpty() ?: true
        val plain = unset("id") && unset("name") && unset("root") && unset("icon") && unset("section") && nothingDisabled
        return if (plain) obj.getValue("screen") else obj
    }
}

/**
 * The screens to show for a host made from [screens] (a config's own choice, or null when it didn't make one), for a
 * host that can do [capabilities]:
 *
 * - not chosen: every screen the host can back, in the usual order (Upload, Files, Lists, Filesystem)
 * - chosen: those, in the config's order, without the ones the host can't back (they would only show errors) and
 *   without repeats of an id (the first one counts, see [screenProblems])
 *
 * Empty when the config chose none (`"screens": []`), or only ones the host can't back.
 */
fun resolveScreens(screens: List<ScreenConfig>?, capabilities: Set<ProviderCapability>): List<ScreenConfig> =
    (screens ?: HostScreen.entries.map { ScreenConfig(it) })
        .filter { it.screen.isBackedBy(capabilities) }
        .distinctBy { it.tabId }

/**
 * What's wrong with a config's [screens], in words, for the config editor and the import: empty when nothing is. Two
 * screens with one id (two of a kind without their own ids, most often) would show only the first, and a root on a tab
 * that has no folders does nothing.
 */
fun screenProblems(screens: List<ScreenConfig>?): List<String> {
    if (screens == null) return emptyList()
    val problems = mutableListOf<String>()
    screens.groupBy { it.tabId }.filterValues { it.size > 1 }.forEach { (id, same) ->
        val kinds = same.map { it.screen }.distinct()
        problems += if (kinds.size == 1 && same.all { it.id.isNullOrBlank() }) {
            "There are ${same.size} ${kinds.single().name} tabs without an id of their own: only the first one shows. " +
                "Give each one its own \"id\"."
        } else {
            "${same.size} tabs have the id \"$id\": only the first one shows. Each tab needs its own \"id\"."
        }
    }
    screens.filter { it.screen != HostScreen.FILESYSTEM && it.rootFolder.isNotEmpty() }.forEach {
        problems += "The ${it.screen.name} tab \"${it.tabId}\" has a \"root\", which only a FILESYSTEM tab uses."
    }
    return problems
}

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
