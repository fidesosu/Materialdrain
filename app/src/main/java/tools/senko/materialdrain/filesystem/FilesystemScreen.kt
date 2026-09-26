package tools.senko.materialdrain.filesystem

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.ktor.http.encodeURLPathPart
import kotlinx.coroutines.delay
import tools.senko.materialdrain.api.FilesystemEntry
import tools.senko.materialdrain.api.toFileInfoResponse
import tools.senko.materialdrain.files.FileInfoViewModel
import tools.senko.materialdrain.files.SelectAllMenuItem
import tools.senko.materialdrain.files.SelectionActionBar
import tools.senko.materialdrain.settings.SEARCH_INDEX_DELETE_WARNING
import tools.senko.materialdrain.settings.isSearchIndex
import tools.senko.materialdrain.ui.components.FileListItem
import tools.senko.materialdrain.ui.components.ConfirmDialog
import tools.senko.materialdrain.ui.components.TextInputDialog
import tools.senko.materialdrain.ui.components.FolderPickerDialog

private const val LOADING_INDICATOR_DELAY_MS = 400L
private const val LOADING_START_GRACE_MS = 1500L

@Composable
fun PathBreadcrumb(
    pathSegments: List<PathSegment>,
    onPathSegmentClick: (segment: PathSegment) -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    LaunchedEffect(pathSegments, scrollState.maxValue) {
        if (pathSegments.isNotEmpty()) {
            scrollState.animateScrollTo(scrollState.maxValue)
        }
    }

    if (pathSegments.isEmpty()) {
        Text(
            text = "Storage",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        )
        return
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        pathSegments.forEachIndexed { index, segment ->
            Text(
                text = segment.name,
                style = MaterialTheme.typography.titleSmall.copy(
                    color = if (index == pathSegments.lastIndex) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                ),
                fontWeight = if (index == pathSegments.lastIndex) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .clickable { onPathSegmentClick(segment) }
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            )
            if (index < pathSegments.lastIndex) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                    contentDescription = "Path separator",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilesystemScreen(
    filesystemViewModel: FilesystemViewModel,
    fileInfoViewModel: FileInfoViewModel,
    onFileSelected: () -> Unit,
    fabHeight: Dp,
    isFabVisible: Boolean
) {
    val uiState by filesystemViewModel.uiState.collectAsState()
    val pullRefreshState = rememberPullToRefreshState()
    val context = LocalContext.current

    // Folders usually load within a fraction of a second, so the loading indicators only appear when
    // it takes longer (or right away when the user pulls to refresh) instead of flashing on every navigation.
    var showLoadingIndicator by remember { mutableStateOf(false) }
    var userRefreshing by remember { mutableStateOf(false) }
    val isLoadingNow by rememberUpdatedState(uiState.isLoading)
    LaunchedEffect(uiState.isLoading) {
        if (uiState.isLoading) {
            delay(LOADING_INDICATOR_DELAY_MS)
            showLoadingIndicator = true
        } else {
            showLoadingIndicator = false
            userRefreshing = false
        }
    }
    LaunchedEffect(userRefreshing) {
        // A refresh which never started loading (e.g. missing API key) must not leave the indicator stuck
        if (userRefreshing) {
            delay(LOADING_START_GRACE_MS)
            if (!isLoadingNow) userRefreshing = false
        }
    }

    val visibleChildren = remember(uiState.children, uiState.hideSearchIndex) { uiState.visibleChildren }

    var showNewFolderDialog by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }
    var entryToRename by remember { mutableStateOf<FilesystemEntry?>(null) }
    var entryToMove by remember { mutableStateOf<FilesystemEntry?>(null) }
    var entryToDelete by remember { mutableStateOf<FilesystemEntry?>(null) }

    // Being in selection mode is separate from having something selected: deselecting the last item keeps the mode
    var selecting by rememberSaveable { mutableStateOf(false) }
    var selectedPaths by rememberSaveable { mutableStateOf<Set<String>>(emptySet()) }
    val selectionMode = selecting
    val exitSelection = {
        selecting = false
        selectedPaths = emptySet()
    }
    val startSelecting = { path: String ->
        selecting = true
        selectedPaths = selectedPaths + path
    }
    val selectedEntries = visibleChildren.filter { it.path in selectedPaths }
    var showBulkMove by remember { mutableStateOf(false) }
    var showBulkDelete by remember { mutableStateOf(false) }

    // Items which were deleted, moved or are in another folder now can't stay selected
    LaunchedEffect(visibleChildren) {
        if (selectedPaths.isNotEmpty()) {
            val listedPaths = visibleChildren.mapTo(HashSet()) { it.path }
            val remaining = selectedPaths.filterTo(HashSet()) { it in listedPaths }
            if (remaining.size != selectedPaths.size) selectedPaths = remaining
        }
    }

    BackHandler(enabled = true) {
        val navigatedUp = filesystemViewModel.navigateToParentPath()
        if (!navigatedUp) {
            // If already at root, finish the activity to exit
            (context as? Activity)?.finish()
        }
    }
    // Registered last, so leaving the selection mode takes precedence over going up a folder
    BackHandler(enabled = selectionMode) { exitSelection() }

    if (showNewFolderDialog) {
        TextInputDialog(
            title = "New folder",
            label = "Folder name",
            confirmLabel = "Create",
            onConfirm = {
                filesystemViewModel.createFolder(it)
                showNewFolderDialog = false
            },
            onDismiss = { showNewFolderDialog = false }
        )
    }
    if (showImportDialog) {
        TextInputDialog(
            title = "Import files by ID",
            label = "File IDs",
            confirmLabel = "Import",
            singleLine = false,
            supportingText = "Separate several IDs with spaces or commas. The files are copied from your Pixeldrain files into this folder.",
            onConfirm = {
                filesystemViewModel.importFilesById(it.split(',', ' ', '\n', '\t'))
                showImportDialog = false
            },
            onDismiss = { showImportDialog = false }
        )
    }
    entryToRename?.let { entry ->
        TextInputDialog(
            title = if (entry.type == "dir") "Rename folder" else "Rename file",
            label = "New name",
            confirmLabel = "Rename",
            initialValue = entry.name,
            onConfirm = {
                filesystemViewModel.renameEntry(entry, it)
                entryToRename = null
            },
            onDismiss = { entryToRename = null }
        )
    }
    entryToMove?.let { entry ->
        FolderPickerDialog(
            title = "Move \"${entry.name}\"",
            confirmLabel = "Move here",
            initialPath = uiState.currentPath,
            excludedPaths = if (entry.type == "dir") setOf(entry.path.trim('/')) else emptySet(),
            loadFolders = { filesystemViewModel.listSubdirectories(it) },
            onConfirm = {
                filesystemViewModel.moveEntry(entry, it)
                entryToMove = null
            },
            onDismiss = { entryToMove = null }
        )
    }
    if (showBulkMove) {
        FolderPickerDialog(
            title = "Move ${selectedEntries.size} items",
            confirmLabel = "Move here",
            initialPath = uiState.currentPath,
            // A folder can't be moved into itself
            excludedPaths = selectedEntries.filter { it.type == "dir" }.mapTo(HashSet()) { it.path.trim('/') },
            loadFolders = { filesystemViewModel.listSubdirectories(it) },
            onConfirm = {
                filesystemViewModel.moveEntries(selectedEntries, it)
                exitSelection()
                showBulkMove = false
            },
            onDismiss = { showBulkMove = false }
        )
    }
    if (showBulkDelete) {
        val folderCount = selectedEntries.count { it.type == "dir" }
        ConfirmDialog(
            title = "Delete ${selectedEntries.size} items?",
            text = "The selected items will be deleted." +
                (if (folderCount > 0) " This includes everything inside the selected folder" + (if (folderCount > 1) "s" else "") + "." else "") +
                (if (selectedEntries.any { it.isSearchIndex() }) "\n\n$SEARCH_INDEX_DELETE_WARNING" else "") +
                "\n\nThis cannot be undone.",
            confirmLabel = "Delete",
            isDestructive = true,
            onConfirm = {
                filesystemViewModel.deleteEntries(selectedEntries)
                exitSelection()
                showBulkDelete = false
            },
            onDismiss = { showBulkDelete = false }
        )
    }
    entryToDelete?.let { entry ->
        ConfirmDialog(
            title = if (entry.isSearchIndex()) "Delete the search index?" else if (entry.type == "dir") "Delete folder?" else "Delete file?",
            text = if (entry.isSearchIndex()) {
                SEARCH_INDEX_DELETE_WARNING
            } else if (entry.type == "dir") {
                "\"${entry.name}\" and everything inside it will be deleted. This cannot be undone."
            } else {
                "\"${entry.name}\" will be deleted. This cannot be undone."
            },
            confirmLabel = if (entry.isSearchIndex()) "Delete anyway" else "Delete",
            isDestructive = true,
            onConfirm = {
                filesystemViewModel.deleteEntry(entry)
                entryToDelete = null
            },
            onDismiss = { entryToDelete = null }
        )
    }
    uiState.pendingUpload?.let { pending ->
        val names = pending.conflictingNames
        ConfirmDialog(
            title = "Replace existing files?",
            text = "${names.size} of the selected files already exist in this folder and will be overwritten: " +
                names.take(5).joinToString(", ") + if (names.size > 5) ", …" else "",
            confirmLabel = "Overwrite",
            isDestructive = true,
            onConfirm = { filesystemViewModel.confirmPendingUpload() },
            onDismiss = { filesystemViewModel.cancelPendingUpload() }
        )
    }

    PullToRefreshBox(
        isRefreshing = userRefreshing || showLoadingIndicator,
        onRefresh = {
            userRefreshing = true
            filesystemViewModel.refreshCurrentPath()
        },
        state = pullRefreshState,
        modifier = Modifier.fillMaxSize(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
        ) {
            SelectionActionBar(
                visible = selectionMode,
                selectedCount = selectedEntries.size,
                onClose = { exitSelection() }
            ) { dismiss ->
                // Only files can be downloaded, folders in the selection are skipped
                val selectedFiles = selectedEntries.filter { it.type == "file" }
                val allSelected = visibleChildren.isNotEmpty() && selectedEntries.size == visibleChildren.size
                SelectAllMenuItem(
                    allSelected = allSelected,
                    onToggle = { selectedPaths = if (allSelected) emptySet() else visibleChildren.mapTo(HashSet()) { it.path } },
                    dismiss = dismiss
                )
                HorizontalDivider()
                DropdownMenuItem(
                    text = { Text("Download") },
                    leadingIcon = { Icon(Icons.Filled.Download, contentDescription = null) },
                    enabled = selectedFiles.isNotEmpty(),
                    onClick = {
                        dismiss()
                        fileInfoViewModel.downloadFilesSequentially(selectedFiles.mapNotNull { it.toFileInfoResponse() })
                        exitSelection()
                    }
                )
                if (uiState.canWrite) {
                    DropdownMenuItem(
                        text = { Text("Move") },
                        leadingIcon = { Icon(Icons.AutoMirrored.Filled.DriveFileMove, contentDescription = null) },
                        enabled = selectedEntries.isNotEmpty() && !uiState.isModifying,
                        onClick = { dismiss(); showBulkMove = true }
                    )
                }
                if (uiState.canDelete) {
                    val canDeleteNow = selectedEntries.isNotEmpty() && !uiState.isModifying
                    DropdownMenuItem(
                        text = { Text("Delete", color = if (canDeleteNow) MaterialTheme.colorScheme.error else Color.Unspecified) },
                        leadingIcon = {
                            Icon(Icons.Filled.Delete, contentDescription = null, tint = if (canDeleteNow) MaterialTheme.colorScheme.error else LocalContentColor.current)
                        },
                        enabled = canDeleteNow,
                        onClick = { dismiss(); showBulkDelete = true }
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                PathBreadcrumb(
                    pathSegments = uiState.pathSegments,
                    onPathSegmentClick = {
                        filesystemViewModel.navigateToPathSegment(it)
                    },
                    modifier = Modifier.weight(1f)
                )
                if (uiState.canWrite && !selectionMode) {
                    IconButton(onClick = { showNewFolderDialog = true }, enabled = !uiState.isModifying) {
                        Icon(Icons.Filled.CreateNewFolder, contentDescription = "New folder")
                    }
                    IconButton(onClick = { showImportDialog = true }, enabled = !uiState.isModifying) {
                        Icon(Icons.Filled.Link, contentDescription = "Import files by ID")
                    }
                }
            }
            if (uiState.isModifying) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            uiState.uploadProgress?.let { progress ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Uploading ${progress.currentFileName} (${progress.currentIndex}/${progress.totalFiles})",
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { filesystemViewModel.cancelUpload() }) { Text("Cancel") }
                }
            }
            HorizontalDivider()

            if (showLoadingIndicator && visibleChildren.isEmpty() && !uiState.apiKeyMissingError && uiState.errorMessage == null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (uiState.apiKeyMissingError) {
                 Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                    Text(
                        text = uiState.errorMessage ?: "API Key is missing. Please set it in Settings to browse the filesystem.",
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            } else if (uiState.errorMessage != null) {
                Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                    Text(
                        text = uiState.errorMessage!!,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            } else if (visibleChildren.isEmpty() && !uiState.isLoading) {
                Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                    Text(
                        text = "This folder is empty.",
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(bottom = if (isFabVisible) fabHeight + 16.dp else 16.dp)
                ) {
                    items(visibleChildren, key = { it.path }) { entry ->
                        FilesystemEntryItem(
                            entry = entry,
                            apiKey = uiState.apiKey, // Pass the API key from the ViewModel
                            canModify = uiState.canWrite && !uiState.isModifying,
                            canDelete = uiState.canDelete && !uiState.isModifying,
                            selectionMode = selectionMode,
                            selected = entry.path in selectedPaths,
                            onLongClick = { startSelecting(entry.path) },
                            onSelect = { startSelecting(entry.path) },
                            onClick = {
                                if (selectionMode) {
                                    selectedPaths = if (entry.path in selectedPaths) selectedPaths - entry.path else selectedPaths + entry.path
                                } else if (entry.type == "file") {
                                    fileInfoViewModel.setFileInfoFromFilesystemEntry(entry)
                                    onFileSelected()
                                } else {
                                    filesystemViewModel.navigateToChild(entry)
                                }
                            },
                            onDownload = { entry.toFileInfoResponse()?.let { fileInfoViewModel.initiateDownloadFile(it) } },
                            onRename = { entryToRename = entry },
                            onMove = { entryToMove = entry },
                            onDelete = { entryToDelete = entry }
                        )
                        HorizontalDivider(
                            thickness = 0.5.dp,
                            color = MaterialTheme.colorScheme.outlineVariant.copy(
                                alpha = 0.5f
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun FilesystemEntryItem(
    entry: FilesystemEntry,
    apiKey: String,
    canModify: Boolean,
    canDelete: Boolean,
    selectionMode: Boolean,
    selected: Boolean,
    onLongClick: () -> Unit,
    onSelect: () -> Unit,
    onClick: () -> Unit,
    onDownload: () -> Unit,
    onRename: () -> Unit,
    onMove: () -> Unit,
    onDelete: () -> Unit
) {
    val isFile = entry.type == "file"
    FileListItem(
        name = entry.name,
        type = entry.type,
        fileSize = entry.fileSize,
        modified = tools.senko.materialdrain.util.formatApiDateTimeString(entry.modified),
        mimeType = entry.mimeType,
        thumbnailUrl = entry.thumbnailHref?.let { "https://pixeldrain.com${it}" } ?: run {
            if (isFile) {
                val cleanedPath = entry.path.removePrefix("/").split('/').filter { it.isNotEmpty() }
                val encodedPathSegments = cleanedPath.joinToString("/") { it.encodeURLPathPart() }
                if (encodedPathSegments.isNotEmpty()) {
                    "https://pixeldrain.com/api/filesystem/${encodedPathSegments}?thumbnail&width=48&height=48"
                } else null
            } else null
        },
        apiKey = apiKey,
        selectionMode = selectionMode,
        selected = selected,
        onLongClick = onLongClick,
        trailingContent = {
            var menuExpanded by remember { mutableStateOf(false) }
            Box {
                IconButton(onClick = { menuExpanded = true }, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "More options for ${entry.name}")
                }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    if (isFile) {
                        DropdownMenuItem(
                            text = { Text("Download") },
                            leadingIcon = { Icon(Icons.Filled.Download, contentDescription = null) },
                            onClick = { menuExpanded = false; onDownload() }
                        )
                    }
                    if (canModify) {
                        DropdownMenuItem(
                            text = { Text("Rename") },
                            leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                            onClick = { menuExpanded = false; onRename() }
                        )
                        DropdownMenuItem(
                            text = { Text("Move") },
                            leadingIcon = { Icon(Icons.AutoMirrored.Filled.DriveFileMove, contentDescription = null) },
                            onClick = { menuExpanded = false; onMove() }
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("Select") },
                        leadingIcon = { Icon(Icons.Filled.CheckBox, contentDescription = null) },
                        onClick = { menuExpanded = false; onSelect() }
                    )
                    if (canDelete) {
                        DropdownMenuItem(
                            text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                            leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                            onClick = { menuExpanded = false; onDelete() }
                        )
                    }
                }
            }
        },
        onClick = onClick
    )
}
