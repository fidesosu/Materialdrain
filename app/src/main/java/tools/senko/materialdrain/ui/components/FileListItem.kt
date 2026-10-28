package tools.senko.materialdrain.ui.components

import tools.senko.materialdrain.ui.LocalListLook
import tools.senko.materialdrain.ui.ListLook
import tools.senko.materialdrain.ui.theme.AppShapes
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import tools.senko.materialdrain.ui.icons.automirrored.filled.InsertDriveFile
import tools.senko.materialdrain.ui.icons.filled.Folder
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.Color
import tools.senko.materialdrain.ui.icons.filled.Android
import tools.senko.materialdrain.ui.icons.filled.Description
import tools.senko.materialdrain.ui.icons.filled.Code
import tools.senko.materialdrain.ui.icons.filled.PictureAsPdf
import tools.senko.materialdrain.ui.icons.filled.FolderZip
import tools.senko.materialdrain.ui.icons.filled.MusicNote
import tools.senko.materialdrain.ui.icons.filled.Movie
import tools.senko.materialdrain.ui.icons.filled.Image
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.decode.DataSource
import coil.request.ImageRequest
import tools.senko.materialdrain.settings.LocalDataSaver
import tools.senko.materialdrain.ui.media.HostRequestAuth
import tools.senko.materialdrain.ui.preview.PreviewSamples
import tools.senko.materialdrain.ui.preview.PreviewTheme
import tools.senko.materialdrain.ui.preview.ThemePreviews
import tools.senko.materialdrain.util.formatSize
import androidx.compose.ui.res.stringResource
import tools.senko.materialdrain.R
import androidx.annotation.StringRes

/** How long a thumbnail fades in over its tile (Coil's own default). */
private const val THUMBNAIL_CROSSFADE_MS = 100

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
    // Lower rows with smaller pictures, so more of them fit (Look and feel, Compact rows)
    val compact = LocalListLook.current.compact
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .heightIn(min = if (compact) 56.dp else 72.dp)
            .padding(start = 16.dp, end = 4.dp, top = if (compact) 4.dp else 8.dp, bottom = if (compact) 4.dp else 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FileIcon(name = name, isDirectory = type == "dir", thumbnailUrl = thumbnailUrl, size = if (compact) 40.dp else 56.dp, loadOnTap = !selectionMode)

        Spacer(modifier = Modifier.width(if (compact) 12.dp else 16.dp))

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
 * A file or folder as a tile of the grid (Look and feel, Grid view): its picture as big as the tile is wide, and under it
 * its name and details, with [trailingContent] (its menu) or, while selecting, a checkbox beside them.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileGridItem(
    name: String,
    type: String,
    fileSize: Long? = null,
    modified: String? = null,
    thumbnailUrl: String? = null,
    trailingContent: (@Composable () -> Unit)? = null,
    selectionMode: Boolean = false,
    selected: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = modifier
            .clip(AppShapes.Medium)
            .background(if (selected) colors.secondaryContainer else Color.Transparent)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(6.dp)
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth().aspectRatio(1f), contentAlignment = Alignment.Center) {
            FileIcon(
                name = name,
                isDirectory = type == "dir",
                thumbnailUrl = thumbnailUrl,
                size = maxWidth,
                cornerRadius = 12.dp,
                loadOnTap = !selectionMode
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
            Column(modifier = Modifier.weight(1f).padding(start = 2.dp)) {
                Text(name, style = MaterialTheme.typography.bodyMedium, color = colors.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val details = fileDetails(name, isDirectory = type == "dir", fileSize = fileSize, modified = modified)
                if (details.isNotEmpty()) {
                    Text(details, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            if (selectionMode) {
                Box(modifier = Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                    Checkbox(checked = selected, onCheckedChange = null)
                }
            } else {
                trailingContent?.invoke()
            }
        }
    }
}

/**
 * The picture of a file or folder: a soft tile in the colour of its kind (folder, image, video, audio, archive,
 * document...) with a small glyph of it, and the file's thumbnail over it when it has one. The thumbnail fades in over
 * the tile, so a row looks the same while it loads, and a file whose thumbnail fails just keeps its tile. The
 * thumbnail is decoded at [size], not at the size of the original image. Once it's there the tile goes, so a picture
 * with transparent parts shows the row's background through them.
 *
 * A thumbnail the data saver holds back (see DataSaver) isn't loaded: the tile looks as it does without one, and loads
 * the thumbnail when tapped, unless [loadOnTap] is off (while files are being selected, a tap selects).
 */
@Composable
fun FileIcon(name: String, isDirectory: Boolean, thumbnailUrl: String?, size: Dp, cornerRadius: Dp = size / 4, loadOnTap: Boolean = true) {
    val shape = RoundedCornerShape(cornerRadius)
    val kind = remember(name, isDirectory) { FileKind.of(name, isDirectory) }
    val (container, content) = kind.colors()
    val dataSaver = LocalDataSaver.current
    val held = thumbnailUrl != null && !isDirectory && !dataSaver.showsThumbnail(thumbnailUrl)
    // Once the thumbnail is there the tile underneath goes, so a picture with transparent parts shows the page through
    // them. Until then (held back, loading, failed) the tile is the picture. One faded in leaves it until it has covered
    // it; one from memory, which Coil doesn't fade in, at once
    var thumbnailLoaded by remember(thumbnailUrl) { mutableStateOf(false) }
    var thumbnailInstant by remember(thumbnailUrl) { mutableStateOf(false) }
    val tileAlpha = animateFloatAsState(
        targetValue = if (thumbnailLoaded && !held) 0f else 1f,
        animationSpec = if (thumbnailInstant) snap() else tween(150, delayMillis = THUMBNAIL_CROSSFADE_MS),
        label = "tileAlpha"
    )
    Box(
        modifier = Modifier
            .size(size)
            .clip(shape)
            // Read while drawing, so the fade only draws again rather than recomposing the list
            .drawBehind { drawRect(container, alpha = tileAlpha.value) }
            .then(
                if (held && loadOnTap) Modifier.clickable(onClickLabel = stringResource(R.string.common_load_the_thumbnail)) { dataSaver.loadThumbnail(thumbnailUrl!!) }
                else Modifier
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = kind.icon,
            contentDescription = stringResource(kind.labelRes),
            tint = content,
            modifier = Modifier.size(size * 0.46f).graphicsLayer { alpha = tileAlpha.value }
        )
        if (!held && thumbnailUrl != null && !isDirectory) {
            val context = LocalContext.current
            // Built once per URL and login: a new request on every recomposition makes Coil load the thumbnail again
            val headers = HostRequestAuth.headersFor(thumbnailUrl)
            val thumbnailPx = with(LocalDensity.current) { size.roundToPx() }
            val request = remember(thumbnailUrl, headers, thumbnailPx) {
                ImageRequest.Builder(context)
                    .data(thumbnailUrl)
                    .size(thumbnailPx)
                    .crossfade(THUMBNAIL_CROSSFADE_MS)
                    .apply { headers.forEach { (name, value) -> addHeader(name, value) } }
                    .build()
            }
            // No placeholder or error picture: the tile underneath is both
            AsyncImage(
                model = request,
                contentDescription = stringResource(R.string.files_thumbnail_of, name),
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(size),
                onSuccess = { success ->
                    thumbnailInstant = success.result.dataSource == DataSource.MEMORY_CACHE
                    thumbnailLoaded = true
                }
            )
        }
    }
}

/** What kind of file [name] is, in a word, e.g. "Video" or "Archive": the same kind its tile shows (see [FileIcon]). */
@Composable
fun fileKindLabel(name: String, isDirectory: Boolean): String = stringResource(FileKind.of(name, isDirectory).labelRes)

/** What kind of file a name is, for the colour and glyph of its tile (see [FileIcon]). */
private enum class FileKind(val icon: ImageVector, @StringRes val labelRes: Int) {
    FOLDER(Icons.Filled.Folder, R.string.files_folder),
    IMAGE(Icons.Filled.Image, R.string.common_image),
    VIDEO(Icons.Filled.Movie, R.string.common_video),
    AUDIO(Icons.Filled.MusicNote, R.string.media_audio),
    ARCHIVE(Icons.Filled.FolderZip, R.string.common_archive),
    PDF(Icons.Filled.PictureAsPdf, R.string.common_pdf),
    CODE(Icons.Filled.Code, R.string.settings_code),
    TEXT(Icons.Filled.Description, R.string.common_document),
    APP(Icons.Filled.Android, R.string.common_app),
    OTHER(Icons.AutoMirrored.Filled.InsertDriveFile, R.string.common_file);

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
@Composable
fun fileDetails(name: String, isDirectory: Boolean, fileSize: Long?, modified: String? = null): String {
    val folder = stringResource(R.string.files_folder)
    return buildList {
        if (isDirectory) add(folder) else {
            fileSize?.let { add(formatSize(it)) }
            extensionLabel(name)?.let { add(it) }
        }
        modified?.let { add(it) }
    }.joinToString(" • ")
}

/** The file extension in capitals ("PDF"), for the details line. Null when the name has none or it is not a short one. */
private fun extensionLabel(name: String): String? =
    name.substringAfterLast('.', "").takeIf { it.isNotEmpty() && it.length <= 5 && it != name }?.uppercase()

// ---- Previews ----

@ThemePreviews
@Composable
private fun FileListItemPreview() {
    PreviewTheme {
        Column {
            PreviewSamples.files.forEach { file ->
                FileListItem(
                    name = file.name,
                    type = if (file.isDirectory) "dir" else "file",
                    fileSize = file.size,
                    modified = "3 days ago",
                    trailingContent = { OverflowMenuButton(contentDescription = "More") { } },
                    onClick = {}
                )
            }
        }
    }
}

@ThemePreviews
@Composable
private fun FileListItemSelectingPreview() {
    PreviewTheme {
        Column {
            PreviewSamples.files.take(4).forEachIndexed { index, file ->
                FileListItem(
                    name = file.name,
                    type = if (file.isDirectory) "dir" else "file",
                    fileSize = file.size,
                    selectionMode = true,
                    selected = index % 2 == 1,
                    onClick = {}
                )
            }
        }
    }
}

/** The rows lower, as Compact rows has them. */
@ThemePreviews
@Composable
private fun FileListItemCompactPreview() {
    PreviewTheme {
        CompositionLocalProvider(LocalListLook provides ListLook(compact = true)) {
            Column {
                PreviewSamples.files.take(4).forEach { file ->
                    FileListItem(
                        name = file.name,
                        type = if (file.isDirectory) "dir" else "file",
                        fileSize = file.size,
                        modified = "3 days ago",
                        trailingContent = { OverflowMenuButton(contentDescription = "More") { } },
                        onClick = {}
                    )
                }
            }
        }
    }
}

/** A row of the grid, the second tile selected. */
@ThemePreviews
@Composable
private fun FileGridItemPreview() {
    PreviewTheme(padding = 8.dp) {
        Row {
            PreviewSamples.files.take(3).forEachIndexed { index, file ->
                FileGridItem(
                    name = file.name,
                    type = if (file.isDirectory) "dir" else "file",
                    fileSize = file.size,
                    selectionMode = true,
                    selected = index == 1,
                    modifier = Modifier.weight(1f),
                    onClick = {}
                )
            }
        }
    }
}

/** The tile of each kind of file, as a file without a thumbnail (or with one not loaded yet) has it. */
@ThemePreviews
@Composable
private fun FileIconPreview() {
    PreviewTheme(padding = 16.dp) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PreviewSamples.files.forEach { file -> FileIcon(file.name, file.isDirectory, thumbnailUrl = null, size = 40.dp) }
        }
    }
}
