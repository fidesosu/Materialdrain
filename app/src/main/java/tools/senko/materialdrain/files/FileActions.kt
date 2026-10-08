package tools.senko.materialdrain.files

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FolderCopy
import androidx.compose.material.icons.filled.Share
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
import tools.senko.materialdrain.ui.components.ConfirmDialog
import tools.senko.materialdrain.ui.components.OverflowMenuButton
import tools.senko.materialdrain.ui.components.TextInputDialog
import tools.senko.materialdrain.ui.components.FolderPickerDialog

fun copyLinkToClipboard(context: Context, url: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("Pixeldrain URL", url))
    Toast.makeText(context, "Link copied to clipboard", Toast.LENGTH_SHORT).show()
}

fun shareLink(context: Context, url: String) {
    val sendIntent = Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_TEXT, url).setType("text/plain")
    context.startActivity(Intent.createChooser(sendIntent, null))
}

/** The 3 dot menu of a file of the Files page or of a list. */
@Composable
fun FileItemMenu(
    file: StorageNode,
    shareUrl: String?,
    canDelete: Boolean,
    onDownload: () -> Unit,
    onSelect: () -> Unit,
    onAddToFilesystem: () -> Unit,
    onAddToList: () -> Unit,
    onDelete: () -> Unit,
    /** Only for the files of a list which can be changed. */
    onRemoveFromList: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val url = shareUrl
    OverflowMenuButton(contentDescription = "More options for ${file.name}") { close ->
        AppMenuItem("Download", leadingIcon = Icons.Filled.Download, onClick = { close(); onDownload() })
        if (url != null) {
            AppMenuItem("Copy link", leadingIcon = Icons.Filled.ContentCopy, onClick = { close(); copyLinkToClipboard(context, url) })
            AppMenuItem("Share link", leadingIcon = Icons.Filled.Share, onClick = { close(); shareLink(context, url) })
        }
        AppMenuItem("Add to filesystem", leadingIcon = Icons.Filled.FolderCopy, onClick = { close(); onAddToFilesystem() })
        AppMenuItem("Add to list", leadingIcon = Icons.AutoMirrored.Filled.PlaylistAdd, onClick = { close(); onAddToList() })
        if (onRemoveFromList != null) {
            AppMenuItem("Remove from this list", leadingIcon = Icons.Filled.Remove, onClick = { close(); onRemoveFromList() })
        }
        AppMenuItem("Select", leadingIcon = Icons.Filled.CheckBox, onClick = { close(); onSelect() })
        if (canDelete) {
            AppMenuDivider()
            AppMenuItem("Delete", leadingIcon = Icons.Filled.Delete, destructive = true, onClick = { close(); onDelete() })
        }
    }
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
    when (request) {
        null -> Unit
        is FileActionRequest.Delete -> {
            val count = request.files.size
            ConfirmDialog(
                title = if (count == 1) "Delete file?" else "Delete $count files?",
                text = (if (count == 1) "\"${request.files.first().name}\"" else "The selected files") +
                    " will be deleted from your Pixeldrain account, this also removes " +
                    (if (count == 1) "it" else "them") + " from every list. This cannot be undone.",
                confirmLabel = "Delete",
                isDestructive = true,
                onConfirm = {
                    fileInfoViewModel.deleteFiles(request.files)
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
                initialPath = filesystemViewModel.browseRootPath,
                rootPath = filesystemViewModel.browseRootPath,
                rootName = filesystemViewModel.browseRootName,
                loadFolders = { filesystemViewModel.listSubdirectories(it) },
                onConfirm = { path ->
                    fileInfoViewModel.addFilesToFilesystem(request.files.mapNotNull { it.ref.id }, path)
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
                    supportingText = "Creates a new list with " + (if (count == 1) "this file" else "these $count files") + ".",
                    onConfirm = { title ->
                        fileInfoViewModel.createListFromFiles(request.files.mapNotNull { it.ref.id }, title)
                        onStarted()
                        onDismiss()
                    },
                    onDismiss = onDismiss
                )
            } else {
                ListPickerDialog(
                    loadLists = { fileInfoViewModel.loadEditableLists() },
                    onNewList = { creatingNewList = true },
                    onPick = { list ->
                        fileInfoViewModel.addFilesToList(list, request.files.mapNotNull { it.ref.id })
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
                    "A list can't be empty: with all of its files removed, the list \"${request.list.title}\" is deleted. " +
                        "The files themselves stay on your account."
                } else {
                    "The files are only taken out of \"${request.list.title}\", they stay on your account and in your other lists."
                },
                confirmLabel = if (deletesList) "Delete list" else "Remove",
                isDestructive = deletesList,
                onConfirm = {
                    fileInfoViewModel.removeFilesFromList(request.list, request.files.mapNotNull { it.ref.id })
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
        title = { Text("Add to list") },
        text = {
            Box(modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp, max = 360.dp)) {
                val shownLists = lists
                when {
                    error != null -> Text(error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                    shownLists == null -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    else -> LazyColumn {
                        item {
                            ListItem(
                                headlineContent = { Text("New list…") },
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
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
