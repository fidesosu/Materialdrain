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
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.AutoDelete
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DataUsage
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.ImageNotSupported
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Numbers
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import kotlinx.coroutines.launch
import tools.senko.materialdrain.provider.api.PixeldrainRichDetails
import tools.senko.materialdrain.provider.api.StorageNode
import tools.senko.materialdrain.ui.LocalBottomInset
import tools.senko.materialdrain.ui.LocalReduceMotion
import tools.senko.materialdrain.ui.components.CodePreview
import tools.senko.materialdrain.ui.components.DotScrollbar
import tools.senko.materialdrain.ui.components.FileIcon
import tools.senko.materialdrain.ui.components.OdometerText
import tools.senko.materialdrain.ui.components.fileKindLabel
import tools.senko.materialdrain.ui.media.AudioPlayerPreview
import tools.senko.materialdrain.ui.media.FullScreenMediaPreviewDialog
import tools.senko.materialdrain.ui.media.InlineImagePreview
import tools.senko.materialdrain.ui.media.InlineVideoPreview
import tools.senko.materialdrain.ui.media.imageRequest
import tools.senko.materialdrain.util.formatApiDateTimeString
import tools.senko.materialdrain.util.formatSize
import tools.senko.materialdrain.util.parseDateTime
import java.text.NumberFormat

/** The corners of the cards on this page, the same as the app's other floating cards. */
private val DetailCardShape = RoundedCornerShape(20.dp)

@Composable
@androidx.media3.common.util.UnstableApi
fun FileInfoDetailsCard(
    fileInfo: StorageNode,
    fileInfoViewModel: FileInfoViewModel,
    context: Context,
    snackbarHostState: SnackbarHostState
) {
    val uiState by fileInfoViewModel.uiState.collectAsState()
    val localContext = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val clipboardManager = localContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

    var fullScreenPreviewUri by remember { mutableStateOf<Uri?>(null) }
    var fullScreenPreviewMimeType by remember { mutableStateOf<String?>(null) }
    var showPreviews by remember { mutableStateOf(false) }

    // A file without an id is a path in the filesystem, which is private: only that needs the login for previews
    val isFilesystemFile = fileInfo.ref.id == null
    val rich = fileInfo.richDetails as? PixeldrainRichDetails
    val actualThumbnailUrl = fileInfoViewModel.thumbnailFor(fileInfo)
    val rawFileApiUrl = fileInfoViewModel.rawUrlFor(fileInfo).orEmpty()
    val shareUrl = fileInfoViewModel.shareUrlFor(fileInfo)

    LaunchedEffect(fileInfo.key) {
        showPreviews = true
    }

    val previewType = fileInfo.previewMimeType()

    /** Copies [text] and says so, naming [what] was copied. */
    fun copy(what: String, text: String) {
        clipboardManager.setPrimaryClip(ClipData.newPlainText(what, text))
        coroutineScope.launch { snackbarHostState.showSnackbar("$what copied") }
    }

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
            // A picture, video or song preview starts right under the top bar; anything else keeps some room above it
            val mediaPreviewFirst = showPreviews && uiState.archive == null &&
                (previewType?.startsWith("image/") == true || previewType?.startsWith("video/") == true || previewType?.startsWith("audio/") == true)
            if (!mediaPreviewFirst) Spacer(Modifier.height(16.dp))
            val mediaPadding = PaddingValues(bottom = 8.dp)
            if (showPreviews) {
                // An archive shows its contents where a preview would be
                if (uiState.archive != null) {
                    uiState.archive?.let { archive ->
                        ArchiveContents(
                            archive = archive,
                            onOpenFolder = fileInfoViewModel::openArchiveFolder,
                            onDownload = fileInfoViewModel::downloadArchiveEntry
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
            val previewShowsPicture = uiState.archive == null && when {
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
            val download = uiState.activeDownloads[fileInfo.key]
            ActionRow(
                download = download,
                canDownload = rich?.canDownload != false,
                shareUrl = shareUrl,
                canDelete = fileInfoViewModel.canDeleteFiles(),
                onDownload = { fileInfoViewModel.initiateDownloadFile(fileInfo) },
                onCancel = { fileInfoViewModel.cancelDownload(fileInfo) },
                onOpen = { uri -> openDownloadedFile(context, OpenableDownload(uri, previewType ?: "*/*")) },
                onShare = { link ->
                    val send = Intent(Intent.ACTION_SEND).apply {
                        putExtra(Intent.EXTRA_TEXT, link)
                        type = "text/plain"
                    }
                    context.startActivity(Intent.createChooser(send, null))
                },
                onCopyLink = { link -> copy("Link", link) },
                onDelete = { fileInfoViewModel.initiateDeleteFile(fileInfo) }
            )

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
                        add { DetailRow(Icons.Filled.Folder, "Folder", folder, onCopy = { copy("Folder", folder) }) }
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
                                    onCopy = { copy("Link", link) },
                                    onOpen = {
                                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, link.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                                    }
                                )
                            }
                        }
                        fileInfo.ref.id?.let { id ->
                            add { DetailRow(Icons.Filled.Numbers, "ID", id, monospace = true, onCopy = { copy("ID", id) }) }
                        }
                        rich?.sha256?.let { hash ->
                            add { DetailRow(Icons.Filled.Fingerprint, "SHA-256", hash, monospace = true, collapsible = true, onCopy = { copy("SHA-256", hash) }) }
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
            SelectionContainer {
                Text(file.name, style = MaterialTheme.typography.titleMedium, maxLines = 4, overflow = TextOverflow.Ellipsis)
            }
            Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
 * The file's actions in one row: the download button, which takes the room there is, then Share, Copy link and Delete
 * as buttons of their own icon.
 *
 * The download button goes through the download (see [DownloadButton]): tapped once it asks to be tapped again (a slip
 * of the finger doesn't start a download of what may be gigabytes), while it runs it shows the progress and stops the download when tapped,
 * and once the file is saved it opens it.
 */
@Composable
private fun ActionRow(
    download: FileDownloadState?,
    canDownload: Boolean,
    shareUrl: String?,
    canDelete: Boolean,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onOpen: (Uri) -> Unit,
    onShare: (String) -> Unit,
    onCopyLink: (String) -> Unit,
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
                armed -> { { armed = false; onDownload() } }
                else -> { { armed = true } }
            },
            modifier = Modifier.weight(1f)
        )
        if (shareUrl != null) {
            IconAction(Icons.Filled.Share, "Share the link", onClick = { onShare(shareUrl) })
            IconAction(Icons.Filled.Link, "Copy the link", onClick = { onCopyLink(shareUrl) })
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
        OdometerText(value, style = MaterialTheme.typography.titleLarge)
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
 * open it. A [collapsible] value (a hash) shows one line until it's tapped.
 */
@Composable
private fun DetailRow(
    icon: ImageVector,
    label: String,
    value: String,
    supporting: String? = null,
    monospace: Boolean = false,
    collapsible: Boolean = false,
    onCopy: (() -> Unit)? = null,
    onOpen: (() -> Unit)? = null
) {
    var expanded by remember { mutableStateOf(!collapsible) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .then(if (collapsible) Modifier.clickable { expanded = !expanded } else Modifier)
            .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp)
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            SelectionContainer {
                Text(
                    value,
                    style = MaterialTheme.typography.bodyLarge,
                    fontFamily = if (monospace) FontFamily.Monospace else null,
                    maxLines = if (expanded) Int.MAX_VALUE else 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            supporting?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
