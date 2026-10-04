package tools.senko.materialdrain.provider.pixeldrain.internal

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// --- Filesystem API Data Classes ---
@Serializable
data class FilesystemEntry(
    val type: String, // "dir" or "file"
    val path: String,
    val name: String,
    val created: String,
    val modified: String,
    @SerialName("mode_string") val modeString: String,
    @SerialName("mode_octal") val modeOctal: String,
    @SerialName("created_by") val createdBy: String,
    @SerialName("file_size") val fileSize: Long, // Will be 0 for dirs
    @SerialName("file_type") val fileType: String, // Mime type for files, empty for dirs
    @SerialName("sha256_sum") val sha256Sum: String, // SHA256 for files, empty for dirs
    val id: String? = null, // Pixeldrain file ID if it's a direct file, null for dirs or "me" for root dir in path context
    @SerialName("logging_enabled_at") val loggingEnabledAt: String? = null,
    // Fields from FileInfoResponse that might appear if '?stat' is used for a file child (corrected from stats)
    val views: Int? = null,
    @SerialName("bandwidth_used") val bandwidthUsed: Long? = null,
    @SerialName("bandwidth_used_paid") val bandwidthUsedPaid: Long? = null,
    val downloads: Int? = null,
    @SerialName("date_last_view") val dateLastView: String? = null,
    @SerialName("mime_type") val mimeType: String? = null, // More specific than fileType for files
    @SerialName("thumbnail_href") val thumbnailHref: String? = null,
    @SerialName("can_edit") val canEdit: Boolean? = null,
    @SerialName("delete_after_date") val deleteAfterDate: String? = null,
    @SerialName("delete_after_downloads") val deleteAfterDownloads: Int? = null,
    val availability: String? = null,
    @SerialName("availability_message") val availabilityMessage: String? = null,
    @SerialName("abuse_type") val abuseType: String? = null,
    @SerialName("abuse_reporter_name") val abuseReporterName: String? = null,
    @SerialName("can_download") val canDownload: Boolean? = null,
    @SerialName("show_ads") val showAds: Boolean? = null,
    @SerialName("allow_video_player") val allowVideoPlayer: Boolean? = null,
    @SerialName("download_speed_limit") val downloadSpeedLimit: Long? = null
)

// Extension function to convert FilesystemEntry to FileInfoResponse
fun FilesystemEntry.toFileInfoResponse(): FileInfoResponse? {
    if (this.type == "file") {
        return FileInfoResponse(
            id = this.id ?: this.path,
            name = this.name,
            size = this.fileSize,
            views = this.views,
            bandwidthUsed = this.bandwidthUsed,
            bandwidthUsedPaid = this.bandwidthUsedPaid,
            downloads = this.downloads,
            dateUpload = this.created,
            dateLastView = this.dateLastView,
            mimeType = this.mimeType ?: this.fileType.ifBlank { null },
            thumbnailHref = this.thumbnailHref,
            hashSha256 = this.sha256Sum.ifBlank { null },
            canEdit = this.canEdit,
            deleteAfterDate = this.deleteAfterDate,
            deleteAfterDownloads = this.deleteAfterDownloads,
            availability = this.availability,
            availabilityMessage = this.availabilityMessage,
            abuseType = this.abuseType,
            abuseReporterName = this.abuseReporterName,
            canDownload = this.canDownload,
            showAds = this.showAds,
            allowVideoPlayer = this.allowVideoPlayer,
            downloadSpeedLimit = this.downloadSpeedLimit
        )
    }
    return null
}


@Serializable
data class FilesystemPermissions(
    val owner: Boolean,
    val read: Boolean,
    val write: Boolean,
    val delete: Boolean
)

@Serializable
data class FilesystemContext(
    @SerialName("premium_transfer") val premiumTransfer: Boolean
)

@Serializable
data class FilesystemListResponse(
    val path: List<FilesystemEntry>,
    @SerialName("base_index") val baseIndex: Int,
    val children: List<FilesystemEntry>,
    val permissions: FilesystemPermissions,
    val context: FilesystemContext,
    val success: Boolean? = null,
    val value: String? = null,
    val message: String? = null
)
