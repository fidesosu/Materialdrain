package tools.senko.materialdrain.provider.api

/**
 * Identifies a node on a provider. [path] is what browse-capable providers (Pixeldrain filesystem, WebDAV)
 * navigate by; [id] is what flat file stores (Pixeldrain's /file store, a generic REST host) hand back
 * instead. A provider only ever reads the field it produced.
 */
data class StorageRef(val path: String = "", val id: String? = null)

/**
 * Extra, provider-specific fields that don't generalize. Kept as a closed hierarchy (rather than a
 * generic bag) so the UI can match on a concrete type instead of guessing keys out of a map.
 */
sealed interface RichDetails

/** Pixeldrain's filesystem/file-info fields that have no equivalent on other providers. */
data class PixeldrainRichDetails(
    val fileId: String,
    val modeString: String? = null,
    val sha256: String? = null,
    val views: Int? = null,
    val bandwidthUsed: Long? = null,
    val downloads: Int? = null,
    val availability: String? = null,
    val availabilityMessage: String? = null,
    val abuseType: String? = null,
    val canEdit: Boolean? = null,
    val canDownload: Boolean? = null,
    val showAds: Boolean? = null,
    val allowVideoPlayer: Boolean? = null,
    val thumbnailHref: String? = null,
    val bandwidthUsedPaid: Long? = null,
    val deleteAfterDate: String? = null,
    val deleteAfterDownloads: Int? = null,
    val abuseReporterName: String? = null,
    val downloadSpeedLimit: Long? = null
) : RichDetails

/** One file or directory, normalized across providers. */
data class StorageNode(
    val ref: StorageRef,
    val name: String,
    val isDirectory: Boolean,
    val size: Long? = null,
    val createdAt: String? = null,
    val modifiedAt: String? = null,
    val mimeType: String? = null,
    val richDetails: RichDetails? = null
)

/** Result of browsing a directory: the breadcrumb up to it, its children, and the caller's permissions on it. */
data class StorageListing(
    val breadcrumb: List<StorageNode>,
    val children: List<StorageNode>,
    val canWrite: Boolean = false,
    val canDelete: Boolean = false
)

data class AccountInfo(
    val username: String? = null,
    val quotaUsedBytes: Long? = null,
    val quotaTotalBytes: Long? = null
)
