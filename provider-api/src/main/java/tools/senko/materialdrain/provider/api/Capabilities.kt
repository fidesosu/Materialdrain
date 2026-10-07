package tools.senko.materialdrain.provider.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A tab of the app a config can turn on. Separate from [ProviderCapability]: capability is what a host can do,
 * this is what the config's author chose to show for it. A config which declares none of these means the host
 * isn't ready yet; the UI shows a message asking for [ProviderConfig.screens] to be filled in instead of a blank
 * or broken set of tabs.
 */
@Serializable
enum class HostScreen { UPLOAD, FILES, LISTS, FILESYSTEM }

/**
 * One screen a config turns on, in the order the tabs are shown. [name] overrides the screen's default label
 * ("Files", "Lists", "Filesystem", "Upload") when it means something more specific for this host, e.g. a Filesystem
 * screen that's really one NAS share called "Media". [disabledCapabilities] takes away actions this screen would
 * otherwise offer (what the host actually supports, from [StorageProvider.capabilities]) without touching the
 * other screens sharing the same host and the same underlying code — e.g. a Filesystem screen with `MKDIR` in here
 * has no "new folder" button even though the host (and every other screen of it) can make one.
 */
@Serializable
data class ScreenConfig(
    val screen: HostScreen,
    val name: String? = null,
    @SerialName("disabled_capabilities") val disabledCapabilities: Set<ProviderCapability> = emptySet()
)

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
