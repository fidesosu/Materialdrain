package tools.senko.materialdrain.files

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.text.format.DateUtils
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import tools.senko.materialdrain.ui.icons.automirrored.filled.OpenInNew
import tools.senko.materialdrain.ui.icons.filled.AutoDelete
import tools.senko.materialdrain.ui.icons.filled.CalendarToday
import tools.senko.materialdrain.ui.icons.filled.Category
import tools.senko.materialdrain.ui.icons.filled.ContentCopy
import tools.senko.materialdrain.ui.icons.filled.DataSaverOn
import tools.senko.materialdrain.ui.icons.filled.DataUsage
import tools.senko.materialdrain.ui.icons.filled.Delete
import tools.senko.materialdrain.ui.icons.filled.Download
import tools.senko.materialdrain.ui.icons.filled.Edit
import tools.senko.materialdrain.ui.icons.filled.Fingerprint
import tools.senko.materialdrain.ui.icons.filled.Folder
import tools.senko.materialdrain.ui.icons.filled.ImageNotSupported
import tools.senko.materialdrain.ui.icons.filled.Link
import tools.senko.materialdrain.ui.icons.filled.Numbers
import tools.senko.materialdrain.ui.icons.filled.Share
import tools.senko.materialdrain.ui.icons.filled.Straighten
import tools.senko.materialdrain.ui.icons.filled.Visibility
import tools.senko.materialdrain.ui.icons.filled.WarningAmber
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import coil.compose.AsyncImage
import coil.imageLoader
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import tools.senko.materialdrain.provider.api.PixeldrainRichDetails
import tools.senko.materialdrain.provider.api.StorageNode
import tools.senko.materialdrain.settings.LocalDataSaver
import tools.senko.materialdrain.ui.LocalBlurredBackdrop
import tools.senko.materialdrain.ui.LocalBottomInset
import tools.senko.materialdrain.ui.LocalReduceMotion
import tools.senko.materialdrain.ui.components.CodePreview
import tools.senko.materialdrain.ui.components.DotScrollbar
import tools.senko.materialdrain.ui.components.ExpandableText
import tools.senko.materialdrain.ui.components.FileIcon
import tools.senko.materialdrain.ui.components.NumberOdometer
import tools.senko.materialdrain.ui.components.OdometerText
import tools.senko.materialdrain.ui.components.fileKindLabel
import tools.senko.materialdrain.ui.media.AudioPlayerPreview
import tools.senko.materialdrain.ui.media.BlurredBackdrop
import tools.senko.materialdrain.ui.media.FullScreenMediaPreviewDialog
import tools.senko.materialdrain.ui.media.FullScreenPdfDialog
import tools.senko.materialdrain.ui.media.InlineImagePreview
import tools.senko.materialdrain.ui.media.InlinePdfPreview
import tools.senko.materialdrain.ui.media.InlineVideoPreview
import tools.senko.materialdrain.ui.media.imageRequest
import tools.senko.materialdrain.util.formatApiDateTimeString
import tools.senko.materialdrain.util.formatSize
import tools.senko.materialdrain.util.parseDateTime
import java.text.NumberFormat
import tools.senko.materialdrain.ui.preview.PreviewTheme
import tools.senko.materialdrain.ui.preview.ScreenPreviews
import tools.senko.materialdrain.ui.preview.ThemePreviews
import tools.senko.materialdrain.ui.preview.PreviewSamples
import kotlinx.coroutines.flow.MutableStateFlow
import androidx.compose.foundation.layout.Arrangement
import tools.senko.materialdrain.provider.api.StorageRef
import androidx.compose.ui.platform.LocalInspectionMode
import tools.senko.materialdrain.ui.preview.LongScreenPreviews

/** The corners of the cards on this page, the same as the app's other floating cards. */
private val DetailCardShape = RoundedCornerShape(20.dp)

/** What a file's details do, handed to [FileInfoDetailsCard]: FileInfoViewModel's in the app, nothing in a preview. */
class FileDetailsActions(
    val loadPreview: () -> Unit = {},
    val loadPdfPreview: () -> Unit = {},
    val openArchiveFolder: (inside: String) -> Unit = {},
    val downloadArchiveEntry: (StorageNode) -> Unit = {},
    val download: () -> Unit = {},
    val cancelDownload: () -> Unit = {},
    val delete: () -> Unit = {}
)

@Composable
@androidx.media3.common.util.UnstableApi
fun FileInfoDetailsCard(
    fileInfo: StorageNode,
    fileInfoViewModel: FileInfoViewModel,
    context: Context,
    snackbarHostState: SnackbarHostState
) {
    val uiState by fileInfoViewModel.uiState.collectAsState()
    val confirmDownload by fileInfoViewModel.confirmDownloadTap.collectAsState()
    FileInfoDetailsCard(
        fileInfo = fileInfo,
        uiState = uiState,
        thumbnailUrl = fileInfoViewModel.thumbnailFor(fileInfo),
        rawFileUrl = fileInfoViewModel.rawUrlFor(fileInfo).orEmpty(),
        shareUrl = fileInfoViewModel.shareUrlFor(fileInfo),
        showsArchive = fileInfoViewModel.showsArchive(fileInfo),
        canDelete = fileInfoViewModel.canDeleteFiles(),
        downloads = fileInfoViewModel.downloads,
        confirmDownload = confirmDownload,
        actions = remember(fileInfoViewModel, fileInfo) {
            FileDetailsActions(
                loadPreview = fileInfoViewModel::loadPreview,
                loadPdfPreview = fileInfoViewModel::loadPdfPreview,
                openArchiveFolder = fileInfoViewModel::openArchiveFolder,
                downloadArchiveEntry = fileInfoViewModel::downloadArchiveEntry,
                download = { fileInfoViewModel.initiateDownloadFile(fileInfo) },
                cancelDownload = { fileInfoViewModel.cancelDownload(fileInfo) },
                delete = { fileInfoViewModel.initiateDeleteFile(fileInfo) }
            )
        },
        context = context,
        snackbarHostState = snackbarHostState
    )
}

/**
 * The details of [fileInfo] drawn from [uiState] and what's known of the file at the host ([thumbnailUrl] to [canDelete]),
 * its download from [downloads]; what it does goes to [actions]. See the previews at the end of the file.
 */
@Composable
@androidx.media3.common.util.UnstableApi
fun FileInfoDetailsCard(
    fileInfo: StorageNode,
    uiState: FileInfoUiState,
    thumbnailUrl: String?,
    rawFileUrl: String,
    shareUrl: String?,
    showsArchive: Boolean,
    canDelete: Boolean,
    downloads: StateFlow<DownloadsState>,
    confirmDownload: Boolean,
    actions: FileDetailsActions,
    context: Context,
    snackbarHostState: SnackbarHostState
) {
    val localContext = LocalContext.current
    val inPreview = LocalInspectionMode.current
    val coroutineScope = rememberCoroutineScope()
    val clipboardManager = localContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

    var fullScreenPreviewUri by remember { mutableStateOf<Uri?>(null) }
    var fullScreenPreviewMimeType by remember { mutableStateOf<String?>(null) }
    // From the frame after the page opens; a preview (in Android Studio) shows one frame, so at once there
    var showPreviews by remember { mutableStateOf(inPreview) }

    // A file without an id is a path in the filesystem, which is private: only that needs the login for previews
    val isFilesystemFile = fileInfo.ref.id == null
    val rich = fileInfo.richDetails as? PixeldrainRichDetails
    val actualThumbnailUrl = thumbnailUrl
    val rawFileApiUrl = rawFileUrl

    LaunchedEffect(fileInfo.key) {
        showPreviews = true
    }

    val previewType = fileInfo.previewMimeType()

    /** Copies [text] and says so, naming [what] was copied. */
    fun copy(what: String, text: String) {
        clipboardManager.setPrimaryClip(ClipData.newPlainText(what, text))
        coroutineScope.launch { snackbarHostState.showSnackbar("$what copied") }
    }

    // A PDF open full screen: its file in the app's cache (see FileInfoViewModel.fetchPdfPreview)
    var fullScreenPdf by remember { mutableStateOf<java.io.File?>(null) }
    fullScreenPdf?.let { file -> FullScreenPdfDialog(file, title = fileInfo.name, onDismissRequest = { fullScreenPdf = null }) }

    if (fullScreenPreviewUri != null) {
        FullScreenMediaPreviewDialog(
            previewUri = fullScreenPreviewUri,
            previewMimeType = fullScreenPreviewMimeType,
            thumbnailUrl = actualThumbnailUrl,
            apiKey = uiState.apiKey,
            onDismissRequest = {
                fullScreenPreviewUri = null
                fullScreenPreviewMimeType = null
            }
        )
    }

    val scrollState = rememberScrollState()
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(start = 16.dp, end = 16.dp)
        ) {
            // Only the private filesystem needs the login for previews
            val previewApiKey = uiState.apiKey.takeIf { isFilesystemFile && it.isNotBlank() }
            // A picture, video or song preview starts just under the top bar, with a little room; anything else keeps more
            // Held back by the data saver, a picture or video keeps its frame in the same place (see HeldPreview)
            val mediaPreviewFirst = showPreviews && uiState.archive == null &&
                (previewType?.startsWith("image/") == true || previewType?.startsWith("video/") == true || previewType?.startsWith("audio/") == true ||
                    isPdf(fileInfo.name, previewType))
            if (!mediaPreviewFirst) Spacer(Modifier.height(16.dp))
            val mediaPadding = PaddingValues(top = 8.dp, bottom = 8.dp)
            if (showPreviews && uiState.previewHeld) {
                // The data saver holds it back: its frame, to load it, for a file that has something to preview
                if (previewType?.startsWith("audio/") == true) {
                    // A song only streams once it's played anyway: the player as always. Its cover is its thumbnail (read
                    // out of the song, see MediaCovers), which the file list has loaded already when the data saver lets
                    // thumbnails show, so it costs nothing; otherwise it waits to be tapped
                    val cover = actualThumbnailUrl?.takeIf { LocalDataSaver.current.showsThumbnail(it) }
                    AudioPlayerPreview(
                        audioUri = rawFileApiUrl.toUri(),
                        apiKey = previewApiKey,
                        title = fileInfo.name,
                        artist = null,
                        album = null,
                        albumArtSource = cover,
                        durationHintMillis = null,
                        outerPadding = mediaPadding,
                        onLoadArt = if (cover == null) actions.loadPreview else null
                    )
                } else {
                    // Only an archive the host can look inside has its contents to load
                    val withArchive = showsArchive
                    val canPreview = withArchive || isTextFile(fileInfo.name, previewType) || isPdf(fileInfo.name, previewType) ||
                        actualThumbnailUrl != null || listOf("image/", "video/").any { previewType?.startsWith(it) == true }
                    if (canPreview) {
                        HeldPreview(fileInfo, previewType, withArchive, actualThumbnailUrl, previewApiKey, mediaPadding, onLoad = actions.loadPreview)
                    }
                }
            } else if (showPreviews) {
                val pdfPreview = uiState.pdfPreview
                // An archive shows its contents where a preview would be
                if (uiState.archive != null) {
                    uiState.archive?.let { archive ->
                        ArchiveContents(
                            archive = archive,
                            onOpenFolder = actions.openArchiveFolder,
                            onDownload = actions.downloadArchiveEntry
                        )
                    }
                } else if (previewType?.startsWith("image/") == true) {
                    InlineImagePreview(
                        imageSource = rawFileApiUrl,
                        thumbnailSource = actualThumbnailUrl,
                        contentDescription = "Image preview for ${fileInfo.name}",
                        apiKey = previewApiKey,
                        // This disables smooth filtering (nearest neighbor scaling), which removes the blur when scaling images.
                        filterQuality = FilterQuality.None,
                        outerPadding = mediaPadding,
                        onFullScreenClick = {
                            fullScreenPreviewUri = rawFileApiUrl.toUri()
                            fullScreenPreviewMimeType = previewType
                        }
                    )
                } else if (previewType?.startsWith("video/") == true) {
                    InlineVideoPreview(
                        thumbnailSource = actualThumbnailUrl,
                        contentDescription = "Video thumbnail for ${fileInfo.name}",
                        apiKey = previewApiKey,
                        outerPadding = mediaPadding,
                        onFullScreenClick = {
                            fullScreenPreviewUri = rawFileApiUrl.toUri()
                            fullScreenPreviewMimeType = previewType
                        }
                    )
                } else if (previewType?.startsWith("audio/") == true) {
                    AudioPlayerPreview(
                        audioUri = rawFileApiUrl.toUri(),
                        apiKey = previewApiKey,
                        title = fileInfo.name,
                        artist = null,
                        album = null,
                        albumArtSource = actualThumbnailUrl,
                        durationHintMillis = null,
                        outerPadding = mediaPadding
                    )
                } else if (pdfPreview != null) {
                    InlinePdfPreview(
                        pdfPreview,
                        outerPadding = mediaPadding,
                        onLoad = actions.loadPdfPreview,
                        onFullScreen = { fullScreenPdf = it }
                    )
                } else if (uiState.isLoadingTextPreview) {
                    Box(modifier = Modifier.fillMaxWidth().height(100.dp).padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else if (uiState.textPreviewContent != null) {
                    val content = uiState.textPreviewContent ?: ""
                    val language = remember(fileInfo.name, content) { languageFor(fileInfo.name, content.lineSequence().firstOrNull()) }
                    CodePreview(content = content, language = language, fullContent = uiState.textPreviewFullContent, title = fileInfo.name)
                } else if (uiState.textPreviewErrorMessage != null) {
                    Text(
                        uiState.textPreviewErrorMessage ?: "Error loading text preview.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                } else if (actualThumbnailUrl != null) {
                    val request = imageRequest(localContext, actualThumbnailUrl) { crossfade(true) }

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f)
                            .padding(vertical = 8.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        AsyncImage(
                            model = request,
                            contentDescription = "File thumbnail for ${fileInfo.name}",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                            error = rememberVectorPainter(Icons.Filled.ImageNotSupported)
                        )
                    }
                }
                // Nothing to preview: the header's tile below shows the kind of file instead of an empty picture
            } else {
                Box(modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp).padding(vertical = 8.dp), contentAlignment = Alignment.Center) {}
            }

            // Whether the preview above already shows the file's picture (the image, a video frame, a song's cover); then the
            // header doesn't repeat it with the tile of its kind
            val previewShowsPicture = !uiState.previewHeld && uiState.archive == null && when {
                previewType?.startsWith("image/") == true || previewType?.startsWith("video/") == true || previewType?.startsWith("audio/") == true -> true
                uiState.isLoadingTextPreview || uiState.textPreviewContent != null || uiState.textPreviewErrorMessage != null -> false
                else -> actualThumbnailUrl != null
            }
            Spacer(Modifier.height(12.dp))
            FileHeader(fileInfo, showTile = !previewShowsPicture)

            rich?.availabilityMessage?.takeIf { it.isNotBlank() }?.let { message ->
                Spacer(Modifier.height(12.dp))
                AvailabilityBanner(message)
            }

            Spacer(Modifier.height(12.dp))
            WithDownload(downloads, fileInfo.key) { download ->
            ActionRow(
                download = download,
                confirmDownload = confirmDownload,
                canDownload = rich?.canDownload != false,
                shareUrl = shareUrl,
                canDelete = canDelete,
                onDownload = actions.download,
                onCancel = actions.cancelDownload,
                onOpen = { uri -> openDownloadedFile(context, OpenableDownload(uri, previewType ?: "*/*")) },
                onShare = { link ->
                    val send = Intent(Intent.ACTION_SEND).apply {
                        putExtra(Intent.EXTRA_TEXT, link)
                        type = "text/plain"
                    }
                    context.startActivity(Intent.createChooser(send, null))
                },
                onDelete = actions.delete
            )
            }

            if (rich != null && (rich.views != null || rich.downloads != null || rich.bandwidthUsed != null)) {
                Spacer(Modifier.height(16.dp))
                StatsRow(rich)
            }

            Spacer(Modifier.height(16.dp))
            DetailsCard(title = "Details") {
                val rows = buildList<@Composable () -> Unit> {
                    add {
                        DetailRow(
                            icon = Icons.Filled.Category,
                            label = "Type",
                            value = fileInfo.mimeType?.takeIf { it.isNotBlank() } ?: previewType ?: "Unknown"
                        )
                    }
                    fileInfo.size?.let { size ->
                        add {
                            DetailRow(
                                icon = Icons.Filled.Straighten,
                                label = "Size",
                                value = formatSize(size),
                                supporting = "${NumberFormat.getIntegerInstance().format(size)} bytes".takeIf { size >= 1024 }
                            )
                        }
                    }
                    fileInfo.createdAt?.let { date ->
                        add { DetailRow(Icons.Filled.CalendarToday, if (isFilesystemFile) "Created" else "Uploaded", formatApiDateTimeString(date), relativeTime(date)) }
                    }
                    fileInfo.modifiedAt?.takeIf { it != fileInfo.createdAt }?.let { date ->
                        add { DetailRow(Icons.Filled.Edit, "Modified", formatApiDateTimeString(date), relativeTime(date)) }
                    }
                    rich?.dateLastView?.let { date ->
                        add { DetailRow(Icons.Filled.Visibility, "Last viewed", formatApiDateTimeString(date), relativeTime(date)) }
                    }
                    folderOf(fileInfo)?.let { folder ->
                        add { DetailRow(Icons.Filled.Folder, "Folder", folder, collapsedLines = 2, onCopy = { copy("Folder", folder) }) }
                    }
                    rich?.let { autoDeleteText(it) }?.let { text ->
                        add { DetailRow(Icons.Filled.AutoDelete, "Deletes itself", text) }
                    }
                }
                rows.forEachIndexed { index, row ->
                    if (index > 0) RowDivider()
                    row()
                }
            }

            val hasIdentifiers = shareUrl != null || fileInfo.ref.id != null || rich?.sha256 != null
            if (hasIdentifiers) {
                Spacer(Modifier.height(12.dp))
                DetailsCard(title = "Links and identifiers") {
                    val rows = buildList<@Composable () -> Unit> {
                        shareUrl?.let { link ->
                            add {
                                DetailRow(
                                    icon = Icons.Filled.Link,
                                    label = "Link",
                                    value = link.removePrefix("https://"),
                                    collapsedLines = 1,
                                    cutInMiddle = false,
                                    onCopy = { copy("Link", link) },
                                    onOpen = {
                                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, link.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                                    }
                                )
                            }
                        }
                        fileInfo.ref.id?.let { id ->
                            add { DetailRow(Icons.Filled.Numbers, "ID", id, monospace = true, collapsedLines = 1, onCopy = { copy("ID", id) }) }
                        }
                        rich?.sha256?.let { hash ->
                            add { DetailRow(Icons.Filled.Fingerprint, "SHA-256", hash, monospace = true, collapsedLines = 1, onCopy = { copy("SHA-256", hash) }) }
                        }
                    }
                    rows.forEachIndexed { index, row ->
                        if (index > 0) RowDivider()
                        row()
                    }
                }
            }

            // The page goes on under the system's navigation bar: room for it, so the last card can be scrolled clear of it
            Spacer(modifier = Modifier.height(24.dp + LocalBottomInset.current))
        }
        DotScrollbar(state = scrollState)
    }
}

// ---- Header ----

/**
 * The file's whole name (selectable, to copy it), with what it is in a line under it. [showTile] puts the tile of its
 * kind before it, for a file whose preview doesn't already show its picture (an archive, a text file).
 */
@Composable
private fun FileHeader(file: StorageNode, showTile: Boolean) {
    val summary = listOfNotNull(
        fileKindLabel(file.name, file.isDirectory),
        file.size?.let { formatSize(it) },
        file.createdAt?.let { relativeTime(it) }?.let { "uploaded $it" }
    ).joinToString(" · ")
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (showTile) {
            FileIcon(name = file.name, isDirectory = file.isDirectory, thumbnailUrl = null, size = 44.dp)
            Spacer(Modifier.width(12.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            ExpandableText(file.name, style = MaterialTheme.typography.titleMedium, collapsedLines = 2)
            Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** How tall a held text or archive preview is: the most the text preview takes (see CodePreview). */
private val HeldPreviewHeight = 200.dp

/**
 * A preview the data saver holds back (see DataSaver): the frame the preview would have, the same size and shape, saying
 * what it would show and (where it's known) about how much that loads. A tap anywhere on it loads it.
 *
 * Its background is the file's thumbnail, blurred as behind a loading preview (see BlurredBackdrop), when the data saver
 * lets thumbnails show: the file list has loaded it already then, so it costs nothing. Otherwise a plain colour.
 */
@Composable
private fun HeldPreview(
    file: StorageNode,
    previewType: String?,
    withArchive: Boolean,
    thumbnailUrl: String?,
    apiKey: String?,
    mediaPadding: PaddingValues,
    onLoad: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val dataSaver = LocalDataSaver.current
    val backdrop = thumbnailUrl?.takeIf { LocalBlurredBackdrop.current && dataSaver.showsThumbnail(it) }
    val size = file.size
    val media = RoundedCornerShape(8.dp)
    // What it would show, what that loads, and the preview's own frame: its size, shape and colour
    class Held(val what: String, val cost: String?, val frame: Modifier, val shape: RoundedCornerShape, val color: Color)
    val held = when {
        withArchive -> Held("what's inside", null, Modifier.height(HeldPreviewHeight), RoundedCornerShape(12.dp), scheme.surfaceContainerHighest)
        isPdf(file.name, previewType) -> Held(
            "the PDF",
            size?.let { if (it > MAX_PDF_PREVIEW_BYTES) "Too big to preview" else "About ${formatSize(it)}" },
            Modifier.aspectRatio(16f / 9f).padding(mediaPadding),
            media,
            scheme.surfaceContainerHigh
        )
        isTextFile(file.name, previewType) && previewType?.startsWith("image/") != true -> Held(
            "the text",
            size?.let { "Up to ${formatSize(minOf(it, MAX_TEXT_PREVIEW_FETCH_SIZE_BYTES.toLong()))}" },
            Modifier.padding(top = 8.dp).height(HeldPreviewHeight),
            RoundedCornerShape(16.dp),
            scheme.surfaceContainerHighest
        )
        previewType?.startsWith("image/") == true ->
            Held("the picture", size?.let { "About ${formatSize(it)}" }, Modifier.aspectRatio(16f / 9f).padding(mediaPadding), media, scheme.surfaceContainerHigh)
        previewType?.startsWith("video/") == true ->
            Held("the video", "Streams once it's played", Modifier.aspectRatio(16f / 9f).padding(mediaPadding), media, scheme.surfaceContainerHigh)
        else -> Held("the thumbnail", null, Modifier.aspectRatio(16f / 9f).padding(vertical = 8.dp), RoundedCornerShape(12.dp), scheme.surfaceContainerHigh)
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxWidth()
            .then(held.frame)
            .clip(held.shape)
            // Black under the thumbnail, as around a fullscreen picture: before Android 12 it's only made faint, not blurred
            .background(if (backdrop != null) Color.Black else held.color)
            .clickable(onClickLabel = "Load the preview", onClick = onLoad)
    ) {
        if (backdrop != null) {
            BlurredBackdrop(backdrop, apiKey, LocalContext.current.imageLoader, Modifier.matchParentSize())
            // Darkened, so the white text reads on a light picture too
            Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = 0.35f)))
        }
        val text = if (backdrop != null) Color.White else scheme.onSurface
        val subtext = if (backdrop != null) Color.White.copy(alpha = 0.8f) else scheme.onSurfaceVariant
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(16.dp)) {
            Icon(Icons.Filled.DataSaverOn, contentDescription = null, tint = subtext, modifier = Modifier.size(28.dp))
            Spacer(Modifier.height(8.dp))
            Text("Tap to load ${held.what}", style = MaterialTheme.typography.labelLarge, color = text)
            Text(
                listOfNotNull("Data saver", held.cost).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = subtext
            )
        }
    }
}

/** What the host says about the file being limited, e.g. rate limited or blocked. */
@Composable
private fun AvailabilityBanner(message: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(DetailCardShape)
            .background(MaterialTheme.colorScheme.errorContainer)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Icon(Icons.Filled.WarningAmber, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
        Spacer(Modifier.width(12.dp))
        Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onErrorContainer)
    }
}

// ---- Actions ----

/** How long the Download button waits for the second tap that starts the download. */
private const val CONFIRM_WINDOW_MS = 3_000L

private val ActionHeight = 48.dp
private val ActionShape = RoundedCornerShape(16.dp)

/**
 * The download of [key], read in a scope of its own: its progress ticks then redraw only [content], not the whole page
 * (with its preview) around it.
 */
@Composable
private fun WithDownload(downloads: StateFlow<DownloadsState>, key: String, content: @Composable (FileDownloadState?) -> Unit) {
    val state by downloads.collectAsState()
    content(state.active[key])
}

/**
 * The file's actions in one row: the download button, which takes the room there is, then Share and Delete as buttons
 * of their own icon.
 *
 * The download button goes through the download (see [DownloadButton]): with [confirmDownload], tapped once it asks to be
 * tapped again (a slip of the finger doesn't start a download of what may be gigabytes), while it runs it shows the
 * progress and stops the download when tapped, and once the file is saved it opens it.
 */
@Composable
private fun ActionRow(
    download: FileDownloadState?,
    confirmDownload: Boolean,
    canDownload: Boolean,
    shareUrl: String?,
    canDelete: Boolean,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onOpen: (Uri) -> Unit,
    onShare: (String) -> Unit,
    onDelete: () -> Unit
) {
    var armed by remember { mutableStateOf(false) }
    LaunchedEffect(armed) {
        if (armed) {
            kotlinx.coroutines.delay(CONFIRM_WINDOW_MS)
            armed = false
        }
    }
    val running = download != null && (download.status == DownloadStatus.PENDING || download.status == DownloadStatus.DOWNLOADING)
    val saved = download?.takeIf { it.status == DownloadStatus.COMPLETED }?.targetUri
    LaunchedEffect(running) { if (running) armed = false }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        DownloadButton(
            download = download,
            running = running,
            saved = saved,
            armed = armed,
            enabled = canDownload || running || saved != null,
            onClick = when {
                running -> onCancel
                saved != null -> { { onOpen(saved) } }
                armed || !confirmDownload -> { { armed = false; onDownload() } }
                else -> { { armed = true } }
            },
            modifier = Modifier.weight(1f)
        )
        if (shareUrl != null) {
            IconAction(Icons.Filled.Share, "Share the link", onClick = { onShare(shareUrl) })
        }
        if (canDelete) IconAction(Icons.Filled.Delete, "Delete", onClick = onDelete, destructive = true)
    }
}

/** What the download button shows, each with its own label and icon (see [DownloadButton]). */
private enum class DownloadButtonState { IDLE, FAILED, ARMED, RUNNING, SAVED }

/**
 * The download button of [ActionRow]. Its colour fades between states, and its label and icon slide from one state to the
 * next; asked for the second tap, a bar along its bottom runs down the time left for it. A label that doesn't fit the
 * room the row leaves gets a smaller font rather than being cut off.
 */
@Composable
private fun DownloadButton(
    download: FileDownloadState?,
    running: Boolean,
    saved: Uri?,
    armed: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val state = when {
        running -> DownloadButtonState.RUNNING
        saved != null -> DownloadButtonState.SAVED
        armed -> DownloadButtonState.ARMED
        download?.status == DownloadStatus.FAILED -> DownloadButtonState.FAILED
        else -> DownloadButtonState.IDLE
    }
    // Asking for the second tap, it stands out in the stronger colour
    val container by animateColorAsState(if (armed) colors.primary else colors.primaryContainer, label = "downloadButtonColor")
    val content by animateColorAsState(if (armed) colors.onPrimary else colors.onPrimaryContainer, label = "downloadButtonContent")
    // The time left for the second tap, from full to empty
    val countdown = remember { Animatable(0f) }
    LaunchedEffect(armed) {
        if (armed) {
            countdown.snapTo(1f)
            countdown.animateTo(0f, tween(CONFIRM_WINDOW_MS.toInt(), easing = LinearEasing))
        } else {
            countdown.snapTo(0f)
        }
    }
    val reduceMotion = LocalReduceMotion.current

    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = ActionShape,
        color = container,
        contentColor = content,
        modifier = modifier.height(ActionHeight)
    ) {
        Box {
            AnimatedContent(
                targetState = state,
                transitionSpec = {
                    if (reduceMotion) {
                        fadeIn(tween(100)) togetherWith fadeOut(tween(100))
                    } else {
                        (slideInVertically(tween(220)) { it / 2 } + fadeIn(tween(220))) togetherWith
                            (slideOutVertically(tween(180)) { -it / 2 } + fadeOut(tween(140)))
                    }
                },
                contentAlignment = Alignment.Center,
                label = "downloadButtonState",
                modifier = Modifier.fillMaxSize()
            ) { shown ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp)
                        .graphicsLayer { alpha = if (enabled) 1f else 0.38f }
                ) {
                    Box(modifier = Modifier.size(20.dp), contentAlignment = Alignment.Center) {
                        when (shown) {
                            DownloadButtonState.RUNNING -> {
                                val progress = download?.progressFraction ?: 0f
                                if (progress > 0f) {
                                    CircularProgressIndicator(progress = { progress }, modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = LocalContentColor.current)
                                } else {
                                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = LocalContentColor.current)
                                }
                            }
                            DownloadButtonState.SAVED -> Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(20.dp))
                            else -> Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(20.dp))
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    val label = when (shown) {
                        DownloadButtonState.RUNNING -> {
                            val percent = download?.progressFraction?.takeIf { it > 0f }?.let { " · ${(it * 100).toInt()}%" }.orEmpty()
                            "Cancel$percent"
                        }
                        DownloadButtonState.SAVED -> "Open"
                        DownloadButtonState.ARMED -> "Tap again"
                        DownloadButtonState.FAILED -> "Retry download"
                        DownloadButtonState.IDLE -> "Download"
                    }
                    val style = MaterialTheme.typography.labelLarge
                    BasicText(
                        text = label,
                        style = style.copy(color = LocalContentColor.current),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        autoSize = TextAutoSize.StepBased(minFontSize = 10.sp, maxFontSize = style.fontSize, stepSize = 0.5.sp)
                    )
                }
            }
            // The countdown of the second tap, along the bottom edge
            if (countdown.value > 0f) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth(countdown.value)
                        .height(3.dp)
                        .background(LocalContentColor.current.copy(alpha = 0.45f))
                )
            }
        }
    }
}

@Composable
private fun IconAction(icon: ImageVector, description: String, onClick: () -> Unit, destructive: Boolean = false) {
    val colors = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        shape = ActionShape,
        color = if (destructive) colors.errorContainer.copy(alpha = 0.6f) else colors.surfaceContainerHigh,
        contentColor = if (destructive) colors.onErrorContainer else colors.onSurface,
        modifier = Modifier.size(ActionHeight)
    ) {
        Box(contentAlignment = Alignment.Center) { Icon(icon, contentDescription = description, modifier = Modifier.size(20.dp)) }
    }
}

// ---- Stats ----

/** Pixeldrain's counts of the file: views, downloads and the bandwidth it used. */
@Composable
private fun StatsRow(rich: PixeldrainRichDetails) {
    val numbers = NumberFormat.getIntegerInstance()
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        rich.views?.let { StatTile(Icons.Filled.Visibility, numbers.format(it), "Views") }
        rich.downloads?.let { StatTile(Icons.Filled.Download, numbers.format(it), "Downloads") }
        rich.bandwidthUsed?.let { StatTile(Icons.Filled.DataUsage, formatSize(it), "Bandwidth") }
    }
}

@Composable
private fun RowScope.StatTile(icon: ImageVector, value: String, label: String) {
    Column(
        modifier = Modifier
            .weight(1f)
            .clip(DetailCardShape)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.height(6.dp))
        OdometerText(value, style = MaterialTheme.typography.titleLarge, spec = NumberOdometer)
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ---- Details ----

@Composable
private fun DetailsCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(DetailCardShape)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(vertical = 8.dp)
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp)
        )
        content()
    }
}

@Composable
private fun RowDivider() {
    HorizontalDivider(
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
        // Under the text, not the icons, like the lists in the system settings
        modifier = Modifier.padding(start = 56.dp, end = 16.dp)
    )
}

/**
 * One detail: its icon, its name over its value (selectable), an optional line under the value, and buttons to copy or
 * open it. The value shows [collapsedLines] lines at most, and all of it when tapped (see [ExpandableText]).
 *
 * Everything is placed from the top, where a one-line row centred it: a value unfolding only grows the row downwards,
 * its name and value and the icons staying where they are, as far down as centring put them when the value was one line.
 */
@Composable
private fun DetailRow(
    icon: ImageVector,
    label: String,
    value: String,
    supporting: String? = null,
    monospace: Boolean = false,
    collapsedLines: Int = 3,
    cutInMiddle: Boolean = true,
    onCopy: (() -> Unit)? = null,
    onOpen: (() -> Unit)? = null
) {
    val hasButtons = onCopy != null || onOpen != null
    Row(
        verticalAlignment = Alignment.Top,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp)
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            // Where centring put it beside a one-line value (and line under it)
            modifier = Modifier.padding(top = if (hasButtons) 12.dp else if (supporting != null) 16.dp else 8.dp).size(24.dp)
        )
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f).padding(top = if (hasButtons) 4.dp else 0.dp, end = 8.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            ExpandableText(
                value,
                style = MaterialTheme.typography.bodyLarge,
                fontFamily = if (monospace) FontFamily.Monospace else null,
                collapsedLines = collapsedLines,
                cutInMiddle = cutInMiddle
            )
            supporting?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        onOpen?.let {
            IconButton(onClick = it) { Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = "Open $label", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        if (onCopy != null) {
            IconButton(onClick = onCopy) { Icon(Icons.Filled.ContentCopy, contentDescription = "Copy $label", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
        } else if (onOpen == null) {
            Spacer(Modifier.width(12.dp))
        }
    }
}

/** "3 days ago" for a date as the hosts write it; null when it can't be read. */
private fun relativeTime(date: String): String? = parseDateTime(date)?.let {
    DateUtils.getRelativeTimeSpanString(it.toEpochMilli(), System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString()
}

/** The folder a path-based file is in, e.g. "/Photos/2024"; null for files with an id, or at the top. */
private fun folderOf(file: StorageNode): String? {
    if (file.ref.id != null && file.ref.path.isBlank()) return null
    val path = file.ref.path.trim('/')
    if (!path.contains('/')) return null
    return "/" + path.substringBeforeLast('/')
}

/** When Pixeldrain deletes the file by itself, e.g. "after 10 downloads"; null when it doesn't. */
private fun autoDeleteText(rich: PixeldrainRichDetails): String? {
    val parts = listOfNotNull(
        rich.deleteAfterDate?.takeIf { it.isNotBlank() && !it.startsWith("0001") }?.let { "on ${formatApiDateTimeString(it)}" },
        rich.deleteAfterDownloads?.takeIf { it > 0 }?.let { "after $it downloads" }
    )
    return parts.takeIf { it.isNotEmpty() }?.joinToString(", or ")?.replaceFirstChar { it.uppercase() }
}

// ---- Previews ----

/** The details of [file] with [uiState], in the previews' theme. */
@androidx.media3.common.util.UnstableApi
@Composable
private fun FileDetailsPreview(
    file: StorageNode,
    uiState: FileInfoUiState = FileInfoUiState(),
    downloads: DownloadsState = DownloadsState(),
    showsArchive: Boolean = false
) {
    PreviewTheme { SampleFileDetails(file, uiState, downloads, showsArchive) }
}

/**
 * The details of [file] with [uiState], as they look when the page has opened, for a preview (here and in App.kt's
 * previews of the whole screen); nothing happens when they're used. Not for the app itself.
 */
@androidx.media3.common.util.UnstableApi
@Composable
internal fun SampleFileDetails(
    file: StorageNode,
    uiState: FileInfoUiState = FileInfoUiState(),
    downloads: DownloadsState = DownloadsState(),
    showsArchive: Boolean = false
) {
    FileInfoDetailsCard(
        fileInfo = file,
        uiState = uiState.copy(fileInfo = file),
        thumbnailUrl = null,
        rawFileUrl = "https://example.com/${file.name}",
        shareUrl = "https://pixeldrain.com/u/${file.ref.id}",
        showsArchive = showsArchive,
        canDelete = true,
        downloads = remember(downloads) { MutableStateFlow(downloads) },
        confirmDownload = true,
        actions = FileDetailsActions(),
        context = LocalContext.current,
        snackbarHostState = remember { SnackbarHostState() }
    )
}

/** A picture on Pixeldrain, with what only Pixeldrain tells: views, downloads, the hash, when it deletes itself. */
@androidx.media3.common.util.UnstableApi
@LongScreenPreviews
@Composable
private fun FileDetailsPicturePreview() = FileDetailsPreview(PreviewSamples.pixeldrainFile)

/** A text file: its start, coloured. */
@androidx.media3.common.util.UnstableApi
@ScreenPreviews
@Composable
private fun FileDetailsTextPreview() = FileDetailsPreview(
    PreviewSamples.text,
    FileInfoUiState(textPreviewContent = PreviewSamples.CODE, textPreviewFullContent = PreviewSamples.CODE)
)

/** A PDF, with the previews' made-up pages (see PdfPreview.kt). */
@androidx.media3.common.util.UnstableApi
@ScreenPreviews
@Composable
private fun FileDetailsPdfPreview() = FileDetailsPreview(
    PreviewSamples.pdf,
    FileInfoUiState(pdfPreview = PdfPreviewState.Ready(java.io.File("sample.pdf")))
)

/** A song: its player, which streams only once it's played. */
@androidx.media3.common.util.UnstableApi
@ScreenPreviews
@Composable
private fun FileDetailsSongPreview() = FileDetailsPreview(PreviewSamples.song)

/** An archive, what's inside it listed where a preview would be. */
@androidx.media3.common.util.UnstableApi
@ScreenPreviews
@Composable
private fun FileDetailsArchivePreview() = FileDetailsPreview(
    PreviewSamples.archive,
    FileInfoUiState(
        archive = ArchiveView(
            archive = PreviewSamples.archive.ref,
            inside = "",
            entries = PreviewSamples.files.take(6).map { it.copy(ref = StorageRef(path = it.name)) },
            error = null
        )
    ),
    showsArchive = true
)

/** The data saver holds the preview back: its frame, to tap. */
@androidx.media3.common.util.UnstableApi
@ScreenPreviews
@Composable
private fun FileDetailsHeldPreview() = FileDetailsPreview(PreviewSamples.video, FileInfoUiState(previewHeld = true))

/** Downloading: the download button shows how far along it is. */
@androidx.media3.common.util.UnstableApi
@ScreenPreviews
@Composable
private fun FileDetailsDownloadingPreview() {
    val file = PreviewSamples.app
    FileDetailsPreview(
        file,
        downloads = DownloadsState(
            active = mapOf(
                file.key to FileDownloadState(
                    fileId = file.key,
                    fileName = file.name,
                    totalBytes = file.size,
                    downloadedBytes = (file.size ?: 0L) * 2 / 5,
                    progressFraction = 0.4f,
                    bytesPerSecond = 2_621_440,
                    etaSeconds = 2,
                    status = DownloadStatus.DOWNLOADING
                )
            )
        )
    )
}

/** The action row in each of its download button's looks: ready, asking for a second tap, saved, failed. */
@ThemePreviews
@Composable
private fun ActionRowPreview() {
    PreviewTheme(padding = 16.dp) {
        val file = PreviewSamples.image
        fun download(status: DownloadStatus) = FileDownloadState(
            fileId = file.key, fileName = file.name, totalBytes = file.size, status = status,
            targetUri = if (status == DownloadStatus.COMPLETED) Uri.EMPTY else null
        )
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf(null, null, download(DownloadStatus.COMPLETED), download(DownloadStatus.FAILED)).forEachIndexed { index, download ->
                DownloadButton(
                    download = download,
                    running = false,
                    saved = download?.targetUri,
                    armed = index == 1,
                    enabled = true,
                    onClick = {}
                )
            }
        }
    }
}
