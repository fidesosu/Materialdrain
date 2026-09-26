package tools.senko.materialdrain.files

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FolderCopy
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import tools.senko.materialdrain.api.ApiResponse
import tools.senko.materialdrain.api.FileInfoResponse
import tools.senko.materialdrain.api.UserList
import tools.senko.materialdrain.filesystem.FilesystemViewModel
import tools.senko.materialdrain.ui.LocalReduceMotion
import tools.senko.materialdrain.ui.components.ConfirmDialog
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

/**
 * The bar which slides in above the list while items are selected. All actions are in one menu, so they
 * come with a label; they are greyed out while nothing is selected. The menu only exists in this mode,
 * the menus of the individual items are replaced by checkboxes meanwhile.
 */
@Composable
fun SelectionActionBar(
    visible: Boolean,
    selectedCount: Int,
    onClose: () -> Unit,
    menuContent: @Composable ColumnScope.(dismiss: () -> Unit) -> Unit
) {
    val reduceMotion = LocalReduceMotion.current
    AnimatedVisibility(
        visible = visible,
        // Reduced animations: the bar appears without pushing the list down bit by bit
        enter = if (reduceMotion) fadeIn(animationSpec = tween(100)) else expandVertically(animationSpec = tween(200)) + fadeIn(animationSpec = tween(200)),
        exit = if (reduceMotion) fadeOut(animationSpec = tween(100)) else shrinkVertically(animationSpec = tween(200)) + fadeOut(animationSpec = tween(150))
    ) {
        Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onClose) { Icon(Icons.Filled.Close, contentDescription = "Stop selecting") }
                Text(
                    text = if (selectedCount == 0) "Select items" else "$selectedCount selected",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                var menuExpanded by remember { mutableStateOf(false) }
                Box {
                    IconButton(onClick = { menuExpanded = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "Actions for the selection") }
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        menuContent { menuExpanded = false }
                    }
                }
            }
        }
    }
}

/** "Select all" / "Deselect all" entry of the selection menu. */
@Composable
fun SelectAllMenuItem(allSelected: Boolean, onToggle: () -> Unit, dismiss: () -> Unit) {
    DropdownMenuItem(
        text = { Text(if (allSelected) "Deselect all" else "Select all") },
        leadingIcon = { Icon(Icons.Filled.SelectAll, contentDescription = null) },
        onClick = { dismiss(); onToggle() }
    )
}

/** The actions of the selection menu when files of the Files page or of a list are selected. */
@Composable
fun FileSelectionMenuItems(
    hasSelection: Boolean,
    allSelected: Boolean,
    onToggleSelectAll: () -> Unit,
    onDownloadZip: () -> Unit,
    onAddToFilesystem: () -> Unit,
    onAddToList: () -> Unit,
    onDelete: () -> Unit,
    dismiss: () -> Unit,
    /** Only for the files of a list which can be changed: takes the files out of the list, they are not deleted. */
    onRemoveFromList: (() -> Unit)? = null
) {
    SelectAllMenuItem(allSelected, onToggleSelectAll, dismiss)
    HorizontalDivider()
    DropdownMenuItem(
        text = { Text("Download as ZIP") },
        leadingIcon = { Icon(Icons.Filled.Download, contentDescription = null) },
        enabled = hasSelection,
        onClick = { dismiss(); onDownloadZip() }
    )
    DropdownMenuItem(
        text = { Text("Add to filesystem") },
        leadingIcon = { Icon(Icons.Filled.FolderCopy, contentDescription = null) },
        enabled = hasSelection,
        onClick = { dismiss(); onAddToFilesystem() }
    )
    DropdownMenuItem(
        text = { Text("Add to list") },
        leadingIcon = { Icon(Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = null) },
        enabled = hasSelection,
        onClick = { dismiss(); onAddToList() }
    )
    if (onRemoveFromList != null) {
        DropdownMenuItem(
            text = { Text("Remove from this list") },
            leadingIcon = { Icon(Icons.Filled.Remove, contentDescription = null) },
            enabled = hasSelection,
            onClick = { dismiss(); onRemoveFromList() }
        )
    }
    HorizontalDivider()
    DropdownMenuItem(
        text = { Text("Delete", color = if (hasSelection) MaterialTheme.colorScheme.error else Color.Unspecified) },
        leadingIcon = {
            Icon(Icons.Filled.Delete, contentDescription = null, tint = if (hasSelection) MaterialTheme.colorScheme.error else LocalContentColor.current)
        },
        enabled = hasSelection,
        onClick = { dismiss(); onDelete() }
    )
}

/** The 3 dot menu of a file of the Files page or of a list. */
@Composable
fun FileItemMenu(
    file: FileInfoResponse,
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
    val url = "https://pixeldrain.com/u/${file.id}"
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }, modifier = Modifier.size(40.dp)) {
            Icon(Icons.Filled.MoreVert, contentDescription = "More options for ${file.name}")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("Download") },
                leadingIcon = { Icon(Icons.Filled.Download, contentDescription = null) },
                onClick = { expanded = false; onDownload() }
            )
            DropdownMenuItem(
                text = { Text("Copy link") },
                leadingIcon = { Icon(Icons.Filled.ContentCopy, contentDescription = null) },
                onClick = { expanded = false; copyLinkToClipboard(context, url) }
            )
            DropdownMenuItem(
                text = { Text("Share link") },
                leadingIcon = { Icon(Icons.Filled.Share, contentDescription = null) },
                onClick = { expanded = false; shareLink(context, url) }
            )
            DropdownMenuItem(
                text = { Text("Add to filesystem") },
                leadingIcon = { Icon(Icons.Filled.FolderCopy, contentDescription = null) },
                onClick = { expanded = false; onAddToFilesystem() }
            )
            DropdownMenuItem(
                text = { Text("Add to list") },
                leadingIcon = { Icon(Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = null) },
                onClick = { expanded = false; onAddToList() }
            )
            if (onRemoveFromList != null) {
                DropdownMenuItem(
                    text = { Text("Remove from this list") },
                    leadingIcon = { Icon(Icons.Filled.Remove, contentDescription = null) },
                    onClick = { expanded = false; onRemoveFromList() }
                )
            }
            DropdownMenuItem(
                text = { Text("Select") },
                leadingIcon = { Icon(Icons.Filled.CheckBox, contentDescription = null) },
                onClick = { expanded = false; onSelect() }
            )
            if (canDelete) {
                HorizontalDivider()
                DropdownMenuItem(
                    text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                    leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                    onClick = { expanded = false; onDelete() }
                )
            }
        }
    }
}

/** An action on one or more files, which is confirmed or configured in a dialog first. */
sealed class FileActionRequest(val files: List<FileInfoResponse>) {
    class Delete(files: List<FileInfoResponse>) : FileActionRequest(files)
    class AddToFilesystem(files: List<FileInfoResponse>) : FileActionRequest(files)
    /** Adds the files to a list of the user or to a new one, which one is picked in the dialog. */
    class AddToList(files: List<FileInfoResponse>) : FileActionRequest(files)
    /** Takes the files out of [list], which holds [listFileCount] files. */
    class RemoveFromList(files: List<FileInfoResponse>, val list: UserList, val listFileCount: Int) : FileActionRequest(files)
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
                initialPath = "me",
                loadFolders = { filesystemViewModel.listSubdirectories(it) },
                onConfirm = { path ->
                    fileInfoViewModel.addFilesToFilesystem(request.files.map { it.id }, path)
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
                        fileInfoViewModel.createListFromFiles(request.files.map { it.id }, title)
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
                        fileInfoViewModel.addFilesToList(list, request.files.map { it.id })
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
                    fileInfoViewModel.removeFilesFromList(request.list, request.files.map { it.id })
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
    loadLists: suspend () -> ApiResponse<List<UserList>>,
    onNewList: () -> Unit,
    onPick: (UserList) -> Unit,
    onDismiss: () -> Unit
) {
    var lists by remember { mutableStateOf<List<UserList>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        when (val response = loadLists()) {
            is ApiResponse.Success -> lists = response.data
            is ApiResponse.Error -> error = response.errorDetails.message ?: response.errorDetails.value ?: "Could not load your lists."
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
