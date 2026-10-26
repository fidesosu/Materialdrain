package tools.senko.materialdrain.files

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import tools.senko.materialdrain.ui.icons.automirrored.filled.PlaylistAdd
import tools.senko.materialdrain.ui.icons.automirrored.filled.KeyboardArrowLeft
import tools.senko.materialdrain.ui.icons.automirrored.filled.KeyboardArrowRight
import tools.senko.materialdrain.ui.icons.filled.Add
import tools.senko.materialdrain.ui.icons.filled.Remove
import tools.senko.materialdrain.ui.icons.filled.Delete
import tools.senko.materialdrain.ui.icons.filled.Download
import tools.senko.materialdrain.ui.icons.filled.FolderCopy
import tools.senko.materialdrain.ui.icons.filled.FolderZip
import tools.senko.materialdrain.ui.icons.filled.Share
import tools.senko.materialdrain.ui.icons.filled.QueueMusic
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import tools.senko.materialdrain.provider.api.ApiResponse
import tools.senko.materialdrain.provider.api.StorageNode
import tools.senko.materialdrain.provider.api.FileList
import tools.senko.materialdrain.filesystem.FilesystemViewModel
import tools.senko.materialdrain.ui.components.AppMenuDivider
import tools.senko.materialdrain.ui.components.AppMenuItem
import tools.senko.materialdrain.ui.components.AppMenuPages
import tools.senko.materialdrain.ui.components.ConfirmDialog
import tools.senko.materialdrain.ui.components.OverflowMenuButton
import tools.senko.materialdrain.ui.components.TextInputDialog
import tools.senko.materialdrain.ui.components.FolderPickerDialog
import tools.senko.materialdrain.ui.preview.PreviewTheme
import tools.senko.materialdrain.ui.preview.ThemePreviews
import tools.senko.materialdrain.ui.preview.PreviewSamples
import tools.senko.materialdrain.ui.components.AppMenuPreview
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import tools.senko.materialdrain.provider.api.StorageRef
import androidx.compose.ui.res.stringResource
import tools.senko.materialdrain.R

fun shareLink(context: Context, url: String) {
    val sendIntent = Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_TEXT, url).setType("text/plain")
    context.startActivity(Intent.createChooser(sendIntent, null))
}

/** "Download" in a ⋮ menu where it can be either (a folder, a list): opens the page of [DownloadChoiceEntries]. */
@Composable
fun DownloadMenuItem(onClick: () -> Unit) {
    AppMenuItem(stringResource(R.string.browser_download), leadingIcon = Icons.Filled.Download, trailingIcon = Icons.AutoMirrored.Filled.KeyboardArrowRight, onClick = onClick)
}

/**
 * The page of a ⋮ menu behind its "Download" ([DownloadMenuItem]), for several files or a folder: each file on its own
 * ([onSeparately]; a folder's files in its folders, made again in Download), or all in one zip ([onZip]). It starts with
 * "Download", going back ([onBack]).
 */
@Composable
fun DownloadChoiceEntries(onBack: () -> Unit, onSeparately: () -> Unit, onZip: () -> Unit, folders: Boolean) {
    AppMenuItem(stringResource(R.string.browser_download), leadingIcon = Icons.AutoMirrored.Filled.KeyboardArrowLeft, onClick = onBack)
    AppMenuDivider()
    AppMenuItem(
        stringResource(R.string.files_separately),
        leadingIcon = Icons.Filled.Download,
        supportingText = if (folders) stringResource(R.string.files_each_file_in_its_folders) else stringResource(R.string.files_each_file_on_its_own),
        onClick = onSeparately
    )
    AppMenuItem(stringResource(R.string.files_as_one_zip), leadingIcon = Icons.Filled.FolderZip, onClick = onZip)
}

/** The 3 dot menu of a file of the Files page or of a list. */
@Composable
fun FileItemMenu(
    file: StorageNode,
    shareUrl: String?,
    canDelete: Boolean,
    onDownload: () -> Unit,
    onAddToFilesystem: () -> Unit,
    onAddToList: () -> Unit,
    onDelete: () -> Unit,
    /** Only for the files of a list which can be changed. */
    onRemoveFromList: (() -> Unit)? = null,
    /** Only for a song, while songs play in the background (see SongQueue). */
    onAddToQueue: (() -> Unit)? = null,
    /** Shares the file itself, for a host whose files have no link ([shareUrl] null). */
    onShareFile: (() -> Unit)? = null
) {
    OverflowMenuButton(contentDescription = stringResource(R.string.browser_more_options_for, file.name)) { close ->
        FileItemMenuEntries(close, shareUrl, canDelete, onDownload, onAddToFilesystem, onAddToList, onDelete, onRemoveFromList, onAddToQueue, onShareFile)
    }
}

/**
 * The entries of [FileItemMenu]; each one closes the menu ([close]) before it acts. "Add to…" opens a second page with
 * where to: the filesystem or a list. [addToOpen] starts on that page, for the previews.
 */
@Composable
private fun FileItemMenuEntries(
    close: () -> Unit,
    shareUrl: String?,
    canDelete: Boolean,
    onDownload: () -> Unit,
    onAddToFilesystem: () -> Unit,
    onAddToList: () -> Unit,
    onDelete: () -> Unit,
    onRemoveFromList: (() -> Unit)?,
    onAddToQueue: (() -> Unit)? = null,
    onShareFile: (() -> Unit)? = null,
    addToOpen: Boolean = false
) {
    val context = LocalContext.current
    val url = shareUrl
    // Remembered in the menu's popup, so each opening starts on the first page
    var addToPageOpen by remember { mutableStateOf(addToOpen) }
    AppMenuPages(
        showSecondary = addToPageOpen,
        primary = {
            AppMenuItem(stringResource(R.string.browser_download), leadingIcon = Icons.Filled.Download, onClick = { close(); onDownload() })
            if (url != null) {
                AppMenuItem(stringResource(R.string.browser_share_link), leadingIcon = Icons.Filled.Share, onClick = { close(); shareLink(context, url) })
            } else if (onShareFile != null) {
                AppMenuItem(stringResource(R.string.browser_share_file), leadingIcon = Icons.Filled.Share, onClick = { close(); onShareFile() })
            }
            if (onAddToQueue != null) {
                AppMenuItem(stringResource(R.string.browser_add_to_queue), leadingIcon = Icons.Filled.QueueMusic, onClick = { close(); onAddToQueue() })
            }
            AppMenuItem(
                stringResource(R.string.files_add_to),
                leadingIcon = Icons.Filled.Add,
                trailingIcon = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                onClick = { addToPageOpen = true }
            )
            if (onRemoveFromList != null) {
                AppMenuItem(stringResource(R.string.files_remove_from_this_list), leadingIcon = Icons.Filled.Remove, onClick = { close(); onRemoveFromList() })
            }
            if (canDelete) {
                AppMenuDivider()
                AppMenuItem(stringResource(R.string.browser_delete), leadingIcon = Icons.Filled.Delete, destructive = true, onClick = { close(); onDelete() })
            }
        },
        secondary = {
            AppMenuItem(stringResource(R.string.files_add_to_2), leadingIcon = Icons.AutoMirrored.Filled.KeyboardArrowLeft, onClick = { addToPageOpen = false })
            AppMenuDivider()
            AppMenuItem(stringResource(R.string.files_filesystem), leadingIcon = Icons.Filled.FolderCopy, onClick = { close(); onAddToFilesystem() })
            AppMenuItem(stringResource(R.string.browser_list), leadingIcon = Icons.AutoMirrored.Filled.PlaylistAdd, onClick = { close(); onAddToList() })
        }
    )
}

/** An action on one or more files, which is confirmed or configured in a dialog first. */
sealed class FileActionRequest(val files: List<StorageNode>) {
    class Delete(files: List<StorageNode>) : FileActionRequest(files)
    class AddToFilesystem(files: List<StorageNode>) : FileActionRequest(files)
    /** Adds the files to a list of the user or to a new one, which one is picked in the dialog. */
    class AddToList(files: List<StorageNode>) : FileActionRequest(files)
    /** Takes the files out of [list], which holds [listFileCount] files. */
    class RemoveFromList(files: List<StorageNode>, val list: FileList, val listFileCount: Int) : FileActionRequest(files)
}

/** The dialogs behind [FileActionRequest]; [onStarted] is called once the action was started. */
@Composable
fun FileActionDialogs(
    request: FileActionRequest?,
    fileInfoViewModel: FileInfoViewModel,
    filesystemViewModel: FilesystemViewModel,
    onDismiss: () -> Unit,
    onStarted: () -> Unit,
    /** Called when the list of a [FileActionRequest.RemoveFromList] loses all of its files and so is deleted. */
    onListDeleted: () -> Unit = {}
) {
    FileActionDialogs(
        request = request,
        handlers = FileActionHandlers(
            deleteFiles = fileInfoViewModel::deleteFiles,
            folderRootPath = filesystemViewModel.browseRootPath,
            folderRootName = filesystemViewModel.browseRootName,
            listSubdirectories = filesystemViewModel::listSubdirectories,
            addToFilesystem = fileInfoViewModel::addFilesToFilesystem,
            createList = fileInfoViewModel::createListFromFiles,
            loadLists = fileInfoViewModel::loadEditableLists,
            addToList = fileInfoViewModel::addFilesToList,
            removeFromList = fileInfoViewModel::removeFilesFromList
        ),
        onDismiss = onDismiss,
        onStarted = onStarted,
        onListDeleted = onListDeleted
    )
}

/** What the dialogs of [FileActionDialogs] do once they're confirmed, and where the filesystem's folders come from. */
class FileActionHandlers(
    val deleteFiles: (List<StorageNode>) -> Unit,
    val folderRootPath: String,
    val folderRootName: String,
    val listSubdirectories: suspend (path: String) -> ApiResponse<List<StorageNode>>,
    val addToFilesystem: (fileIds: List<String>, path: String) -> Unit,
    val createList: (fileIds: List<String>, title: String) -> Unit,
    val loadLists: suspend () -> ApiResponse<List<FileList>>,
    val addToList: (FileList, fileIds: List<String>) -> Unit,
    val removeFromList: (FileList, fileIds: List<String>) -> Unit
)

/** The dialog of [request], which hands what it's confirmed for to [handlers]. See the previews at the end of the file. */
@Composable
fun FileActionDialogs(
    request: FileActionRequest?,
    handlers: FileActionHandlers,
    onDismiss: () -> Unit,
    onStarted: () -> Unit,
    onListDeleted: () -> Unit = {}
) {
    when (request) {
        null -> Unit
        is FileActionRequest.Delete -> {
            val count = request.files.size
            ConfirmDialog(
                title = if (count == 1) "Delete file?" else "Delete $count files?",
                text = "Also removes " + (if (count == 1) "it" else "them") + " from every list. This can't be undone.",
                confirmLabel = "Delete",
                isDestructive = true,
                onConfirm = {
                    handlers.deleteFiles(request.files)
                    onStarted()
                    onDismiss()
                },
                onDismiss = onDismiss
            )
        }
        is FileActionRequest.AddToFilesystem -> {
            FolderPickerDialog(
                title = "Add to filesystem",
                confirmLabel = "Add here",
                initialPath = handlers.folderRootPath,
                rootPath = handlers.folderRootPath,
                rootName = handlers.folderRootName,
                loadFolders = handlers.listSubdirectories,
                onConfirm = { path ->
                    handlers.addToFilesystem(request.files.mapNotNull { it.ref.id }, path)
                    onStarted()
                    onDismiss()
                },
                onDismiss = onDismiss
            )
        }
        is FileActionRequest.AddToList -> {
            val count = request.files.size
            var creatingNewList by remember { mutableStateOf(false) }
            if (creatingNewList) {
                TextInputDialog(
                    title = "Create a list",
                    label = "List title",
                    confirmLabel = "Create",
                    initialValue = "Pixeldrain List",
                    onConfirm = { title ->
                        handlers.createList(request.files.mapNotNull { it.ref.id }, title)
                        onStarted()
                        onDismiss()
                    },
                    onDismiss = onDismiss
                )
            } else {
                ListPickerDialog(
                    loadLists = handlers.loadLists,
                    onNewList = { creatingNewList = true },
                    onPick = { list ->
                        handlers.addToList(list, request.files.mapNotNull { it.ref.id })
                        onStarted()
                        onDismiss()
                    },
                    onDismiss = onDismiss
                )
            }
        }
        is FileActionRequest.RemoveFromList -> {
            val count = request.files.size
            val deletesList = count >= request.listFileCount
            ConfirmDialog(
                title = if (count == 1) "Remove file from list?" else "Remove $count files from list?",
                text = if (deletesList) {
                    "This deletes the list \"${request.list.title}\". The files stay on your account."
                } else {
                    "The files stay on your account and other lists."
                },
                confirmLabel = if (deletesList) "Delete list" else "Remove",
                isDestructive = deletesList,
                onConfirm = {
                    handlers.removeFromList(request.list, request.files.mapNotNull { it.ref.id })
                    if (deletesList) onListDeleted()
                    onStarted()
                    onDismiss()
                },
                onDismiss = onDismiss
            )
        }
    }
}

/** Lets the user pick one of their lists, or start a new one. */
@Composable
private fun ListPickerDialog(
    loadLists: suspend () -> ApiResponse<List<FileList>>,
    onNewList: () -> Unit,
    onPick: (FileList) -> Unit,
    onDismiss: () -> Unit
) {
    var lists by remember { mutableStateOf<List<FileList>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        when (val response = loadLists()) {
            is ApiResponse.Success -> lists = response.data
            is ApiResponse.Error -> error = response.error.message
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.files_add_to_list)) },
        text = {
            Box(modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp, max = 360.dp)) {
                val shownLists = lists
                when {
                    error != null -> Text(error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                    shownLists == null -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    else -> LazyColumn {
                        item {
                            ListItem(
                                headlineContent = { Text(stringResource(R.string.files_new_list)) },
                                leadingContent = { Icon(Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = null) },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                modifier = Modifier.clickable { onNewList() }
                            )
                            HorizontalDivider()
                        }
                        items(shownLists, key = { it.id }) { list ->
                            ListItem(
                                headlineContent = { Text(list.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                supportingContent = { Text(if (list.fileCount == 1) "1 file" else "${list.fileCount} files") },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                modifier = Modifier.clickable { onPick(list) }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.browser_cancel)) } }
    )
}

// ---- Previews ----

/** The ⋮ menu of a file, open: of the Files page, and of a list which can be changed (with Remove from this list). */
@ThemePreviews
@Composable
private fun FileItemMenuPreview() {
    PreviewTheme(padding = 8.dp) {
        Row {
            AppMenuPreview { FileItemMenuEntries({}, "https://pixeldrain.com/u/aBcD1234", true, {}, {}, {}, {}, null, onAddToQueue = {}) }
            AppMenuPreview { FileItemMenuEntries({}, "https://pixeldrain.com/u/aBcD1234", false, {}, {}, {}, {}, {}) }
        }
    }
}

/** The ⋮ menu of a file on its Add to page. */
@ThemePreviews
@Composable
private fun FileItemMenuAddToPreview() {
    PreviewTheme(padding = 8.dp) {
        AppMenuPreview { FileItemMenuEntries({}, "https://pixeldrain.com/u/aBcD1234", true, {}, {}, {}, {}, null, addToOpen = true) }
    }
}

/** Made-up lists, folders and nothing done, for the dialogs' previews. */
private val PreviewHandlers = FileActionHandlers(
    deleteFiles = {},
    folderRootPath = "me",
    folderRootName = "me",
    listSubdirectories = { path ->
        ApiResponse.Success(listOf("Photos", "Music", "Backups").map { StorageNode(StorageRef(path = "$path/$it"), it, isDirectory = true) })
    },
    addToFilesystem = { _, _ -> },
    createList = { _, _ -> },
    loadLists = {
        ApiResponse.Success(listOf(FileList("a", "Holiday 2026", 48, true), FileList("b", "Wallpapers", 1, true), FileList("c", "Shared with Sam", 7, true)))
    },
    addToList = { _, _ -> },
    removeFromList = { _, _ -> }
)

@Composable
private fun FileActionDialogPreview(request: FileActionRequest) {
    PreviewTheme { FileActionDialogs(request, PreviewHandlers, onDismiss = {}, onStarted = {}) }
}

@ThemePreviews
@Composable
private fun DeleteFilesDialogPreview() = FileActionDialogPreview(FileActionRequest.Delete(PreviewSamples.files.take(3)))

@ThemePreviews
@Composable
private fun AddToFilesystemDialogPreview() = FileActionDialogPreview(FileActionRequest.AddToFilesystem(listOf(PreviewSamples.image)))

/** The lists to pick from; New list… opens the dialog naming a new one. */
@ThemePreviews
@Composable
private fun AddToListDialogPreview() = FileActionDialogPreview(FileActionRequest.AddToList(listOf(PreviewSamples.image)))

/** Taking every file out of a list deletes it, which the dialog says. */
@ThemePreviews
@Composable
private fun RemoveFromListDialogPreview() = FileActionDialogPreview(
    FileActionRequest.RemoveFromList(listOf(PreviewSamples.image), FileList("b", "Wallpapers", 1, true), listFileCount = 1)
)
