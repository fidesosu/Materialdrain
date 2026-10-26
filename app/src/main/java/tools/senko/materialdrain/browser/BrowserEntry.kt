package tools.senko.materialdrain.browser

import tools.senko.materialdrain.ui.LocalListLook
import tools.senko.materialdrain.ui.components.FileGridItem
import tools.senko.materialdrain.util.formatApiDateTimeString
import androidx.compose.material.icons.Icons
import tools.senko.materialdrain.ui.icons.automirrored.filled.DriveFileMove
import tools.senko.materialdrain.ui.icons.filled.Delete
import tools.senko.materialdrain.ui.icons.filled.Download
import tools.senko.materialdrain.ui.icons.filled.Edit
import tools.senko.materialdrain.ui.icons.filled.Share
import tools.senko.materialdrain.ui.icons.automirrored.filled.OpenInNew
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import tools.senko.materialdrain.ui.icons.filled.QueueMusic
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import tools.senko.materialdrain.files.listDate
import tools.senko.materialdrain.files.FileItemMenu
import tools.senko.materialdrain.files.DownloadChoiceEntries
import tools.senko.materialdrain.files.DownloadMenuItem
import tools.senko.materialdrain.files.DownloadStyle
import tools.senko.materialdrain.ui.components.AppMenuPages
import tools.senko.materialdrain.provider.api.FileList
import tools.senko.materialdrain.provider.api.StorageNode
import tools.senko.materialdrain.ui.components.AppMenuDivider
import tools.senko.materialdrain.ui.components.AppMenuItem
import tools.senko.materialdrain.ui.components.FileListItem
import tools.senko.materialdrain.ui.components.OverflowMenuButton
import tools.senko.materialdrain.util.formatRelativeDateTime

/** One row (or, [asTile], one tile of the grid) of the browser, with the menu and the actions its mode offers. */
@Composable
internal fun BrowserEntry(
    node: StorageNode,
    asTile: Boolean,
    modifier: Modifier,
    mode: BrowserMode,
    openedList: FileList?,
    selectionMode: Boolean,
    selected: Boolean,
    canWriteFs: Boolean,
    canDeleteFs: Boolean,
    canDelete: Boolean,
    listCanEdit: Boolean,
    thumbnailUrl: String?,
    shareUrl: String?,
    onOpen: () -> Unit,
    onToggleSelection: () -> Unit,
    onLongClick: () -> Unit,
    onDownload: (DownloadStyle) -> Unit,
    onRename: () -> Unit,
    onMove: () -> Unit,
    onDelete: () -> Unit,
    onAddToFilesystem: () -> Unit,
    onAddToList: () -> Unit,
    onRemoveFromList: () -> Unit,
    onShareLink: (String) -> Unit,
    onAddToQueue: (() -> Unit)? = null,
    downloadHasChoices: Boolean = node.isDirectory,
    onShareFile: (() -> Unit)? = null
) {
    // A list in the lists overview: its own page, when the host has one
    val folderLink = if (node.isDirectory && mode == BrowserMode.LISTS) shareUrl else null
    // How long ago, or the date and time itself (Look and feel, Relative dates)
    val relative = LocalListLook.current.relativeDates
    val modified = remember(node.listDate, relative) {
        if (relative) formatRelativeDateTime(node.listDate) else node.listDate?.let { formatApiDateTimeString(it) }
    }
    val trailingContent: @Composable () -> Unit = {
        when {
            // The lists overview: a folder per list, opened or linked
            node.isDirectory && mode == BrowserMode.LISTS -> ListFolderMenu(node.name, folderLink, onOpen, onShareLink, onDownload, downloadHasChoices)
            // The filesystem: folders and files with their own actions
            mode == BrowserMode.FILESYSTEM -> FilesystemMenu(
                node = node,
                shareUrl = shareUrl,
                canWrite = canWriteFs,
                canDelete = canDeleteFs,
                onDownload = onDownload,
                onRename = onRename,
                onMove = onMove,
                onDelete = onDelete,
                onAddToQueue = onAddToQueue,
                onShareFile = onShareFile
            )
            // Files and the files of a list: the shared file menu
            else -> FileItemMenu(
                file = node,
                shareUrl = shareUrl,
                canDelete = canDelete && (if (mode == BrowserMode.LISTS) listCanEdit else true),
                onDownload = { onDownload(DownloadStyle.SEPARATELY) },
                onAddToFilesystem = onAddToFilesystem,
                onAddToList = onAddToList,
                onDelete = onDelete,
                onRemoveFromList = if (mode == BrowserMode.LISTS && listCanEdit && openedList != null) onRemoveFromList else null,
                onAddToQueue = onAddToQueue,
                onShareFile = onShareFile
            )
        }
    }
    val onClick = { if (selectionMode) onToggleSelection() else onOpen() }
    if (asTile) {
        FileGridItem(
            name = node.name,
            type = if (node.isDirectory) "dir" else "file",
            fileSize = node.size,
            modified = modified,
            thumbnailUrl = thumbnailUrl,
            selectionMode = selectionMode,
            selected = selected,
            onLongClick = onLongClick,
            trailingContent = trailingContent,
            modifier = modifier,
            onClick = onClick
        )
    } else {
        FileListItem(
            name = node.name,
            type = if (node.isDirectory) "dir" else "file",
            fileSize = node.size,
            modified = modified,
            thumbnailUrl = thumbnailUrl,
            selectionMode = selectionMode,
            selected = selected,
            onLongClick = onLongClick,
            trailingContent = trailingContent,
            onClick = onClick
        )
    }
}

/** The ⋮ menu of a list in the lists overview: open it, download its files ([downloadHasChoices]: in a page of two ways), share its link. */
@Composable
private fun ListFolderMenu(
    title: String,
    link: String?,
    onOpen: () -> Unit,
    onShareLink: (String) -> Unit,
    onDownload: (DownloadStyle) -> Unit,
    downloadHasChoices: Boolean
) {
    OverflowMenuButton(contentDescription = "More options for $title") { close ->
        // Remembered in the menu's popup, so each opening starts on the first page
        var downloadPage by remember { mutableStateOf(false) }
        AppMenuPages(
            showSecondary = downloadPage,
            primary = {
                AppMenuItem("Open", leadingIcon = Icons.AutoMirrored.Filled.OpenInNew, onClick = { close(); onOpen() })
                if (downloadHasChoices) DownloadMenuItem { downloadPage = true }
                else AppMenuItem("Download", leadingIcon = Icons.Filled.Download, onClick = { close(); onDownload(DownloadStyle.SEPARATELY) })
                if (link != null) {
                    AppMenuItem("Share link", leadingIcon = Icons.Filled.Share, onClick = { close(); onShareLink(link) })
                }
            },
            secondary = {
                DownloadChoiceEntries(
                    onBack = { downloadPage = false },
                    onSeparately = { close(); onDownload(DownloadStyle.SEPARATELY) },
                    onZip = { close(); onDownload(DownloadStyle.ZIP) },
                    folders = false
                )
            }
        )
    }
}

@Composable
private fun FilesystemMenu(
    node: StorageNode,
    shareUrl: String?,
    canWrite: Boolean,
    canDelete: Boolean,
    onDownload: (DownloadStyle) -> Unit,
    onRename: () -> Unit,
    onMove: () -> Unit,
    onDelete: () -> Unit,
    onAddToQueue: (() -> Unit)? = null,
    /** Shares the file itself, where it has no link ([shareUrl] null). */
    onShareFile: (() -> Unit)? = null
) {
    OverflowMenuButton(contentDescription = "More options for ${node.name}") { close ->
        // A folder's Download is a page of its own: each file on its own, or all in one zip. Remembered in the menu's
        // popup, so each opening starts on the first page
        var downloadPage by remember { mutableStateOf(false) }
        AppMenuPages(
            showSecondary = downloadPage,
            primary = {
                if (node.isDirectory) {
                    DownloadMenuItem { downloadPage = true }
                } else {
                    AppMenuItem("Download", leadingIcon = Icons.Filled.Download, onClick = { close(); onDownload(DownloadStyle.SEPARATELY) })
                    if (shareUrl == null && onShareFile != null) {
                        AppMenuItem("Share file", leadingIcon = Icons.Filled.Share, onClick = { close(); onShareFile() })
                    }
                }
                if (onAddToQueue != null) {
                    AppMenuItem("Add to queue", leadingIcon = Icons.Filled.QueueMusic, onClick = { close(); onAddToQueue() })
                }
                if (canWrite) {
                    AppMenuItem("Rename", leadingIcon = Icons.Filled.Edit, onClick = { close(); onRename() })
                    AppMenuItem("Move", leadingIcon = Icons.AutoMirrored.Filled.DriveFileMove, supportingText = "To another folder", onClick = { close(); onMove() })
                }
                if (canDelete) {
                    AppMenuDivider()
                    AppMenuItem("Delete", leadingIcon = Icons.Filled.Delete, destructive = true, onClick = { close(); onDelete() })
                }
            },
            secondary = {
                DownloadChoiceEntries(
                    onBack = { downloadPage = false },
                    onSeparately = { close(); onDownload(DownloadStyle.SEPARATELY) },
                    onZip = { close(); onDownload(DownloadStyle.ZIP) },
                    folders = true
                )
            }
        )
    }
}
