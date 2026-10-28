package tools.senko.materialdrain.files

import android.webkit.MimeTypeMap
import tools.senko.materialdrain.provider.api.StorageNode

/** Identifies a node in the app: its file id, or its path when it has no id (the filesystem). */
val StorageNode.key: String get() = ref.id ?: ref.path

/**
 * The type which decides how a file is previewed. Some hosts list files without a useful type (the filesystem can
 * give a blank or generic one), so a missing or generic type is worked out from the file extension instead.
 */
fun StorageNode.previewMimeType(): String? {
    val reported = mimeType?.takeIf { it.isNotBlank() && it != "application/octet-stream" }
    if (reported != null) return reported
    val extension = name.substringAfterLast('.', "").lowercase()
    if (extension.isEmpty()) return mimeType
    return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: mimeType
}

/** Archives which can be looked inside from the file details (see ArchiveOps). */
private val ARCHIVE_EXTENSIONS = setOf("zip", "7z", "rar", "tar", "tgz", "apk")

/** Image formats whose thumbnails are made on the device rather than by the host (see FileInfoViewModel.thumbnailFor). */
internal val DEVICE_THUMBNAIL_EXTENSIONS = setOf("heic", "heif", "avif")

fun isArchiveName(name: String): Boolean = name.substringAfterLast('.', "").lowercase() in ARCHIVE_EXTENSIONS

/** Whether a file is a PDF, by its type or (when the host doesn't say) its name. */
fun isPdf(name: String, mimeType: String?): Boolean =
    mimeType == "application/pdf" || name.substringAfterLast('.', "").equals("pdf", ignoreCase = true)

/**
 * The kinds of file the fullscreen view has, which a swipe there stays within (see FileInfoViewModel.openNeighbour), so
 * it goes from one to the next of what's viewed alike: pictures, videos and songs; texts and PDFs. Anything else has no
 * fullscreen view.
 */
enum class FileGroup { MEDIA, DOCUMENT, OTHER }

/** The [FileGroup] of a file, by its name and type (see previewMimeType). */
fun fileGroup(name: String, mimeType: String?): FileGroup = when {
    mimeType != null && listOf("image/", "video/", "audio/").any { mimeType.startsWith(it) } -> FileGroup.MEDIA
    isPdf(name, mimeType) || isTextFile(name, mimeType) -> FileGroup.DOCUMENT
    else -> FileGroup.OTHER
}
