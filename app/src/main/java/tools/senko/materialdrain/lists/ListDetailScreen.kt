package tools.senko.materialdrain.lists

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import tools.senko.materialdrain.files.FileActionDialogs
import tools.senko.materialdrain.files.FileActionRequest
import tools.senko.materialdrain.files.FileInfoViewModel
import tools.senko.materialdrain.files.FileItemMenu
import tools.senko.materialdrain.files.FileSelectionMenuItems
import tools.senko.materialdrain.files.SelectionActionBar
import tools.senko.materialdrain.filesystem.FilesystemViewModel
import tools.senko.materialdrain.ui.components.FileListItem
import tools.senko.materialdrain.util.formatApiDateTimeString

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListDetailScreenContent(
    listViewModel: ListViewModel,
    fileInfoViewModel: FileInfoViewModel,
    filesystemViewModel: FilesystemViewModel,
    onFileSelected: () -> Unit,
    onBack: () -> Unit,
    fabHeight: Dp,
    isFabVisible: Boolean
) {
    val uiState by listViewModel.uiState.collectAsState()
    val list = uiState.openedList
    val files = uiState.listFiles

    // Being in selection mode is separate from having something selected: deselecting the last item keeps the mode
    var selecting by rememberSaveable { mutableStateOf(false) }
    var selectedIds by rememberSaveable { mutableStateOf<Set<String>>(emptySet()) }
    val selectionMode = selecting
    val exitSelection = {
        selecting = false
        selectedIds = emptySet()
    }
    val startSelecting = { id: String ->
        selecting = true
        selectedIds = selectedIds + id
    }
    var actionRequest by remember { mutableStateOf<FileActionRequest?>(null) }
    val selectedFiles = files.filter { it.id in selectedIds }

    // Files which were deleted or are no longer in the list can't stay selected
    LaunchedEffect(files) {
        if (selectedIds.isNotEmpty()) {
            val listedIds = files.mapTo(HashSet()) { it.id }
            val remaining = selectedIds.filterTo(HashSet()) { it in listedIds }
            if (remaining.size != selectedIds.size) selectedIds = remaining
        }
    }

    BackHandler(enabled = true) { onBack() }
    // Registered last, so leaving the selection mode takes precedence over leaving the list
    BackHandler(enabled = selectionMode) { exitSelection() }

    // After a process restart the screen is restored but the opened list is gone
    LaunchedEffect(list == null) { if (list == null) onBack() }

    FileActionDialogs(
        request = actionRequest,
        fileInfoViewModel = fileInfoViewModel,
        filesystemViewModel = filesystemViewModel,
        onDismiss = { actionRequest = null },
        onStarted = { exitSelection() },
        // The list is gone, so there is nothing left to show (closing it leaves the screen, see above)
        onListDeleted = { listViewModel.closeList() }
    )

    Column(modifier = Modifier.fillMaxSize()) {
        SelectionActionBar(
            visible = selectionMode,
            selectedCount = selectedFiles.size,
            onClose = { exitSelection() }
        ) { dismiss ->
            FileSelectionMenuItems(
                hasSelection = selectedFiles.isNotEmpty(),
                allSelected = files.isNotEmpty() && selectedFiles.size == files.size,
                onToggleSelectAll = {
                    selectedIds = if (selectedFiles.size == files.size) emptySet() else files.mapTo(HashSet()) { it.id }
                },
                onDownloadZip = {
                    fileInfoViewModel.downloadFilesAsZip(selectedFiles, list?.title ?: "list")
                    exitSelection()
                },
                onAddToFilesystem = { actionRequest = FileActionRequest.AddToFilesystem(selectedFiles) },
                onAddToList = { actionRequest = FileActionRequest.AddToList(selectedFiles) },
                onDelete = { actionRequest = FileActionRequest.Delete(selectedFiles) },
                dismiss = dismiss,
                onRemoveFromList = list?.takeIf { it.canEdit }?.let { openedList ->
                    { actionRequest = FileActionRequest.RemoveFromList(selectedFiles, openedList, files.size) }
                }
            )
        }
        if (!selectionMode && list != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(list.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(
                        "Files: ${list.fileCount} • Created: ${formatApiDateTimeString(list.dateCreated)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (files.isNotEmpty()) {
                    Button(
                        onClick = { fileInfoViewModel.downloadFilesAsZip(files, list.title) },
                        contentPadding = ButtonDefaults.ButtonWithIconContentPadding
                    ) {
                        Icon(Icons.Filled.FolderZip, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                        Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                        Text("All (ZIP)")
                    }
                }
            }
        }
        HorizontalDivider()

        PullToRefreshBox(
            isRefreshing = uiState.isLoadingListFiles,
            onRefresh = { listViewModel.refreshOpenedList() },
            state = rememberPullToRefreshState(),
            modifier = Modifier.fillMaxSize()
        ) {
            when {
                uiState.isLoadingListFiles && files.isEmpty() -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                uiState.listFilesErrorMessage != null && files.isEmpty() -> {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = uiState.listFilesErrorMessage!!,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodyLarge
                        )
                        TextButton(onClick = { listViewModel.refreshOpenedList() }) { Text("Retry") }
                    }
                }
                files.isEmpty() -> {
                    Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                        Text("This list has no files.", textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge)
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = if (isFabVisible) fabHeight + 16.dp else 16.dp)
                    ) {
                        items(files, key = { it.id }) { file ->
                            FileListItem(
                                name = file.name,
                                type = "file",
                                fileSize = file.size,
                                modified = formatApiDateTimeString(file.dateUpload),
                                mimeType = file.mimeType,
                                thumbnailUrl = "https://pixeldrain.com/api/file/${file.id}/thumbnail",
                                selectionMode = selectionMode,
                                selected = file.id in selectedIds,
                                onLongClick = { startSelecting(file.id) },
                                trailingContent = {
                                    FileItemMenu(
                                        file = file,
                                        // A list can hold files of other people, only the owner of a file can delete it
                                        canDelete = file.canEdit == true || list?.canEdit == true,
                                        onDownload = { fileInfoViewModel.initiateDownloadFile(file) },
                                        onSelect = { startSelecting(file.id) },
                                        onAddToFilesystem = { actionRequest = FileActionRequest.AddToFilesystem(listOf(file)) },
                                        onAddToList = { actionRequest = FileActionRequest.AddToList(listOf(file)) },
                                        onDelete = { actionRequest = FileActionRequest.Delete(listOf(file)) },
                                        onRemoveFromList = list?.takeIf { it.canEdit }?.let { openedList ->
                                            { actionRequest = FileActionRequest.RemoveFromList(listOf(file), openedList, files.size) }
                                        }
                                    )
                                },
                                onClick = {
                                    if (selectionMode) {
                                        selectedIds = if (file.id in selectedIds) selectedIds - file.id else selectedIds + file.id
                                    } else {
                                        fileInfoViewModel.fetchFileInfo(file.id)
                                        onFileSelected()
                                    }
                                }
                            )
                            HorizontalDivider(
                                thickness = 0.5.dp,
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            )
                        }
                    }
                }
            }
        }
    }
}
