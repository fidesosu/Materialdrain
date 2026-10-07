package tools.senko.materialdrain.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Folder
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.Color
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import tools.senko.materialdrain.ui.media.HostRequestAuth
import tools.senko.materialdrain.util.formatSize

/**
 * A row of a file or folder. This is a plain row instead of a Material ListItem, whose trailing area
 * reserves far more width than the small menu button needs and takes it away from the file name.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileListItem(
    name: String,
    type: String,
    fileSize: Long? = null,
    modified: String? = null,
    thumbnailUrl: String? = null,
    trailingContent: (@Composable () -> Unit)? = null,
    selectionMode: Boolean = false,
    selected: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .heightIn(min = 72.dp)
            .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FileIcon(name = name, isDirectory = type == "dir", thumbnailUrl = thumbnailUrl, size = 56.dp)

        Spacer(modifier = Modifier.width(16.dp))

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = name,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            val details = fileDetails(name, isDirectory = type == "dir", fileSize = fileSize, modified = modified)

            if (details.isNotEmpty()) {
                Text(
                    text = details,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // While selecting, the item menu makes way for a checkbox
        if (selectionMode) {
            Box(modifier = Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                Checkbox(checked = selected, onCheckedChange = null)
            }
        } else if (trailingContent != null) {
            trailingContent()
        } else {
            Spacer(modifier = Modifier.width(12.dp))
        }
    }
}

/**
 * The picture of a file or folder: a soft tile in the colour of its kind (folder, image, video, audio, archive,
 * document...) with a small glyph of it, and the file's thumbnail over it when it has one. The thumbnail fades in over
 * the tile, so a row looks the same while it loads, and a file whose thumbnail fails just keeps its tile. The
 * thumbnail is decoded at [size], not at the size of the original image.
 */
@Composable
fun FileIcon(name: String, isDirectory: Boolean, thumbnailUrl: String?, size: Dp, cornerRadius: Dp = size / 4) {
    val shape = RoundedCornerShape(cornerRadius)
    val kind = remember(name, isDirectory) { FileKind.of(name, isDirectory) }
    val (container, content) = kind.colors()
    Box(
        modifier = Modifier
            .size(size)
            .clip(shape)
            .background(container),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = kind.icon,
            contentDescription = kind.label,
            tint = content,
            modifier = Modifier.size(size * 0.46f)
        )
        if (thumbnailUrl != null && !isDirectory) {
            val context = LocalContext.current
            // Built once per URL and login: a new request on every recomposition makes Coil load the thumbnail again
            val headers = HostRequestAuth.headersFor(thumbnailUrl)
            val thumbnailPx = with(LocalDensity.current) { size.roundToPx() }
            val request = remember(thumbnailUrl, headers, thumbnailPx) {
                ImageRequest.Builder(context)
                    .data(thumbnailUrl)
                    .size(thumbnailPx)
                    .crossfade(true)
                    .apply { headers.forEach { (name, value) -> addHeader(name, value) } }
                    .build()
            }
            // No placeholder or error picture: the tile underneath is both
            AsyncImage(
                model = request,
                contentDescription = "$name thumbnail",
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(size)
            )
        }
    }
}

/** What kind of file a name is, for the colour and glyph of its tile (see [FileIcon]). */
private enum class FileKind(val icon: ImageVector, val label: String) {
    FOLDER(Icons.Filled.Folder, "Folder"),
    IMAGE(Icons.Filled.Image, "Image"),
    VIDEO(Icons.Filled.Movie, "Video"),
    AUDIO(Icons.Filled.MusicNote, "Audio"),
    ARCHIVE(Icons.Filled.FolderZip, "Archive"),
    PDF(Icons.Filled.PictureAsPdf, "PDF"),
    CODE(Icons.Filled.Code, "Code"),
    TEXT(Icons.Filled.Description, "Document"),
    APP(Icons.Filled.Android, "App"),
    OTHER(Icons.AutoMirrored.Filled.InsertDriveFile, "File");

    /** The tile's colour and the glyph's: from the theme, so they follow the app's colours (and dark mode). */
    @Composable
    fun colors(): Pair<Color, Color> {
        val scheme = MaterialTheme.colorScheme
        return when (this) {
            FOLDER -> scheme.primaryContainer to scheme.onPrimaryContainer
            IMAGE, VIDEO, AUDIO -> scheme.tertiaryContainer to scheme.onTertiaryContainer
            PDF, TEXT, CODE -> scheme.secondaryContainer to scheme.onSecondaryContainer
            ARCHIVE, APP, OTHER -> scheme.surfaceContainerHighest to scheme.onSurfaceVariant
        }
    }

    companion object {
        private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "heif", "avif", "svg", "tif", "tiff", "raw", "dng")
        private val VIDEO_EXTENSIONS = setOf("mp4", "m4v", "mkv", "webm", "mov", "avi", "3gp", "ts", "wmv", "flv")
        private val AUDIO_EXTENSIONS = setOf("mp3", "m4a", "flac", "ogg", "opus", "aac", "wav", "wma", "alac", "aiff")
        private val ARCHIVE_EXTENSIONS = setOf("zip", "7z", "rar", "tar", "gz", "tgz", "bz2", "xz", "zst", "iso")
        private val CODE_EXTENSIONS = setOf(
            "kt", "kts", "java", "js", "ts", "tsx", "jsx", "py", "rb", "go", "rs", "c", "cpp", "h", "hpp", "cs", "swift",
            "php", "sh", "bash", "html", "css", "json", "xml", "yaml", "yml", "toml", "sql", "gradle"
        )
        private val TEXT_EXTENSIONS = setOf("txt", "md", "log", "csv", "rtf", "doc", "docx", "odt", "xls", "xlsx", "ods", "ppt", "pptx", "odp", "ini", "conf", "cfg", "epub")

        fun of(name: String, isDirectory: Boolean): FileKind {
            if (isDirectory) return FOLDER
            return when (name.substringAfterLast('.', "").lowercase()) {
                in IMAGE_EXTENSIONS -> IMAGE
                in VIDEO_EXTENSIONS -> VIDEO
                in AUDIO_EXTENSIONS -> AUDIO
                in ARCHIVE_EXTENSIONS -> ARCHIVE
                "pdf" -> PDF
                in CODE_EXTENSIONS -> CODE
                in TEXT_EXTENSIONS -> TEXT
                "apk", "aab", "xapk" -> APP
                else -> OTHER
            }
        }
    }
}

/** The details line of a file or folder: "Folder", or its size and extension; then [modified] when given. */
fun fileDetails(name: String, isDirectory: Boolean, fileSize: Long?, modified: String? = null): String =
    buildList {
        if (isDirectory) add("Folder") else {
            fileSize?.let { add(formatSize(it)) }
            extensionLabel(name)?.let { add(it) }
        }
        modified?.let { add(it) }
    }.joinToString(" • ")

/** The file extension in capitals ("PDF"), for the details line. Null when the name has none or it is not a short one. */
private fun extensionLabel(name: String): String? =
    name.substringAfterLast('.', "").takeIf { it.isNotEmpty() && it.length <= 5 && it != name }?.uppercase()
