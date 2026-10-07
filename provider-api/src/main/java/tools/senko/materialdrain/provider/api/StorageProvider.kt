package tools.senko.materialdrain.provider.api

/** @param inconclusive nothing failed, but it couldn't be confirmed either (e.g. no endpoint to check credentials against) */
data class FieldValidation(val fieldId: String, val label: String, val ok: Boolean, val message: String, val inconclusive: Boolean = false)

data class ProviderValidationResult(val fields: List<FieldValidation>) {
    val allOk: Boolean get() = fields.all { it.ok || it.inconclusive }
}

/**
 * One storage backend. The UI never talks to Pixeldrain/WebDAV/S3/generic-REST types directly — it goes
 * through this interface and reads [capabilities] to know what it can show. [fileStore], [browse] and
 * [account] are null when the matching capability is absent; there is deliberately no fallback emulation
 * (e.g. faking directories out of a flat store) because that would silently misrepresent what the host
 * actually supports.
 */
interface StorageProvider {
    val id: String
    val displayName: String
    val kind: ProviderKind
    val capabilities: Set<ProviderCapability>

    val fileStore: FileStoreOps?
    val browse: BrowseOps?
    val account: AccountOps?
    val fileList: FileListOps?

    /**
     * The headers a request for [url] must carry to be allowed to see it (the login of this host, for its own
     * private URLs), e.g. for the thumbnail or the content of a file. Empty for public URLs and other hosts.
     */
    fun requestHeaders(url: String): Map<String, String> = emptyMap()

    /** The path the browser starts at (Pixeldrain's "me" bucket, or a configured folder); "" for the top of the host. */
    val rootPath: String
        get() = ""

    /** What the browser's path calls the top of the host when [rootPath] is "" ("/", or e.g. the name of an SMB share). */
    val rootName: String
        get() = "/"

    /** Lists of files, null when [ProviderCapability.LISTS] is absent. */
    val lists: ListOps?
        get() = null

    /** Looking inside archives on the host, null when [ProviderCapability.ARCHIVE_BROWSE] is absent. */
    val archives: ArchiveOps?
        get() = null

    /** Public link to view/share [node], or null if [ProviderCapability.SHARE_LINK] is absent. */
    fun shareUrl(node: StorageNode): String?

    /** Thumbnail image URL for [node], or null if the provider can't generate one. */
    fun thumbnailUrl(node: StorageNode): String?

    /** Direct link to the raw bytes of [node] (e.g. for streaming media playback). */
    fun rawContentUrl(node: StorageNode, attachment: Boolean = false): String?

    /**
     * Per-field reachability check for the Advanced settings screen. Read-only probes only (HEAD/GET) —
     * never exercises an upload/delete endpoint for real, since "testing" a destructive endpoint would
     * itself be a destructive action.
     */
    suspend fun validate(): ProviderValidationResult
}
