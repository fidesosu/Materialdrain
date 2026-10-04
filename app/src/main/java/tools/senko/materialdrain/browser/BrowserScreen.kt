package tools.senko.materialdrain.browser

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FolderCopy
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import tools.senko.materialdrain.files.FileActionDialogs
import tools.senko.materialdrain.files.FileActionRequest
import tools.senko.materialdrain.files.FileInfoViewModel
import tools.senko.materialdrain.files.FileItemMenu
import tools.senko.materialdrain.files.SelectionAction
import tools.senko.materialdrain.files.SelectionActionBar
import tools.senko.materialdrain.files.key
import tools.senko.materialdrain.files.copyLinkToClipboard
import tools.senko.materialdrain.files.shareLink
import tools.senko.materialdrain.filesystem.FilesystemViewModel
import tools.senko.materialdrain.lists.ListViewModel
import tools.senko.materialdrain.provider.api.FileList
import tools.senko.materialdrain.provider.api.ProviderKind
import tools.senko.materialdrain.provider.api.StorageNode
import tools.senko.materialdrain.provider.api.StorageRef
import tools.senko.materialdrain.settings.SEARCH_INDEX_DELETE_WARNING
import tools.senko.materialdrain.settings.isSearchIndex
import tools.senko.materialdrain.ui.components.CenteredTextMessage
import tools.senko.materialdrain.ui.components.ConfirmDialog
import tools.senko.materialdrain.ui.components.ErrorMessage
import tools.senko.materialdrain.ui.components.FileListItem
import tools.senko.materialdrain.ui.components.FolderPickerDialog
import tools.senko.materialdrain.ui.components.TextInputDialog
import tools.senko.materialdrain.util.formatApiDateTimeString

private const val LOADING_INDICATOR_DELAY_MS = 400L
private const val LOADING_START_GRACE_MS = 1500L

/** Which of the three views of the account the browser shows; the screen is the same for each, only what it lists and offers differs. */
enum class BrowserMode { FILES, LISTS, FILESYSTEM }

/** A list shown as a folder: opening it shows the files in it. */
private fun FileList.asFolder(): StorageNode = StorageNode(ref = StorageRef(id = id), name = title, isDirectory = true)

/**
 * The one screen behind the Files, Lists and Filesystem tabs. What the config provides decides which tabs exist (see
 * the navigation), and the mode decides what this screen lists and offers:
 * - FILES: the account's files, flat, sortable and filterable.
 * - LISTS: the lists as folders, one level deep (no path at the top); opening one shows its files.
 * - FILESYSTEM: folders and files with the path at the top, and creating, moving, renaming and uploading.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserScreen(
    mode: BrowserMode,
    filesystemViewModel: FilesystemViewModel,
    fileInfoViewModel: FileInfoViewModel,
    listViewModel: ListViewModel,
    onFileSelected: () -> Unit,
    scrollState: LazyListState,
    fabHeight: Dp,
    isFabVisible: Boolean,
    activeKind: ProviderKind,
    onSelectingChange: (Boolean) -> Unit
) {
    val fsState by filesystemViewModel.uiState.collectAsState()
    val fileState by fileInfoViewModel.uiState.collectAsState()
    val displayedFiles by fileInfoViewModel.displayedFiles.collectAsState()
    val listState by listViewModel.uiState.collectAsState()
    val openedList = listState.openedList.takeIf { mode == BrowserMode.LISTS }
    val context = LocalContext.current

    // What this mode lists, and how it loads and fails
    val entries: List<StorageNode> = when (mode) {
        BrowserMode.FILESYSTEM -> fsState.visibleChildren
        BrowserMode.FILES -> displayedFiles
        BrowserMode.LISTS -> if (openedList == null) listState.lists.sortedBy { it.title.lowercase() }.map { it.asFolder() } else listState.listFiles
    }
    val isLoading = when (mode) {
        BrowserMode.FILESYSTEM -> fsState.isLoading
        BrowserMode.FILES -> fileState.isLoadingUserFiles
        BrowserMode.LISTS -> if (openedList == null) listState.isLoading else listState.isLoadingListFiles
    }
    val errorMessage = when (mode) {
        BrowserMode.FILESYSTEM -> fsState.errorMessage
        BrowserMode.FILES -> fileState.userFilesListErrorMessage
        BrowserMode.LISTS -> if (openedList == null) listState.errorMessage else listState.listFilesErrorMessage
    }
    val keyMissing = when (mode) {
        BrowserMode.FILESYSTEM -> fsState.apiKeyMissingError
        BrowserMode.FILES -> fileState.apiKeyMissingError
        BrowserMode.LISTS -> listState.apiKeyMissingError
    }
    val refresh: () -> Unit = when (mode) {
        BrowserMode.FILESYSTEM -> { { filesystemViewModel.refreshCurrentPath() } }
        BrowserMode.FILES -> { { fileInfoViewModel.fetchUserFiles() } }
        BrowserMode.LISTS -> if (openedList == null) { { listViewModel.fetchUserLists() } } else { { listViewModel.refreshOpenedList() } }
    }
    // Only the filesystem can be changed here; the files of a list can be removed from it when the list allows
    val canWriteFs = mode == BrowserMode.FILESYSTEM && fsState.canWrite
    val listCanEdit = openedList?.canEdit == true

    // Folders usually load within a fraction of a second, so the loading indicator only appears when it takes longer
    // (or right away when the user pulls to refresh)
    val pullRefreshState = rememberPullToRefreshState()
    var showLoadingIndicator by remember { mutableStateOf(false) }
    var userRefreshing by remember { mutableStateOf(false) }
    val isLoadingNow by rememberUpdatedState(isLoading)
    LaunchedEffect(isLoading) {
        if (isLoading) {
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

    // Selection: being in selection mode is separate from having something selected
    var selecting by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(selecting) { onSelectingChange(selecting) }
    var selectedKeys by rememberSaveable { mutableStateOf<Set<String>>(emptySet()) }
    val selectionMode = selecting
    val exitSelection = {
        selecting = false
        selectedKeys = emptySet()
    }
    val startSelecting = { key: String ->
        selecting = true
        selectedKeys = selectedKeys + key
    }
    val selectedEntries = entries.filter { it.key in selectedKeys }
    // Entries which were deleted, moved or left the view can't stay selected
    LaunchedEffect(entries) {
        if (selectedKeys.isNotEmpty()) {
            val listed = entries.mapTo(HashSet()) { it.key }
            val remaining = selectedKeys.filterTo(HashSet()) { it in listed }
            if (remaining.size != selectedKeys.size) selectedKeys = remaining
        }
    }

    // Filesystem dialogs
    var showNewFolderDialog by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }
    var entryToRename by remember { mutableStateOf<StorageNode?>(null) }
    var entryToMove by remember { mutableStateOf<StorageNode?>(null) }
    var entryToDelete by remember { mutableStateOf<StorageNode?>(null) }
    var showBulkMove by remember { mutableStateOf(false) }
    var showBulkDelete by remember { mutableStateOf(false) }
    // Files and list actions (delete, add to filesystem or to a list, remove from a list)
    var actionRequest by remember { mutableStateOf<FileActionRequest?>(null) }

    val filterFocusRequester = remember { FocusRequester() }

    // Back: leaves the selection first, then the open list, then goes up a folder (filesystem). At the top, the
    // activity takes the press (see MainActivity).
    BackHandler(enabled = mode == BrowserMode.FILESYSTEM && filesystemViewModel.canNavigateToParent()) {
        filesystemViewModel.navigateToParentPath()
    }
    BackHandler(enabled = openedList != null) { listViewModel.closeList() }
    BackHandler(enabled = selectionMode) { exitSelection() }

    // What happens on tapping an entry
    val onOpen: (StorageNode) -> Unit = { node ->
        when {
            mode == BrowserMode.FILESYSTEM && node.isDirectory -> filesystemViewModel.navigateToChild(node)
            mode == BrowserMode.LISTS && openedList == null ->
                listState.lists.firstOrNull { it.id == node.ref.id }?.let { listViewModel.openList(it) }
            else -> {
                if (mode == BrowserMode.FILESYSTEM) fileInfoViewModel.setFileInfoFromNode(node)
                else fileInfoViewModel.fetchFileInfo(node.key)
                onFileSelected()
            }
        }
    }

    // ---- Dialogs ----
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
            title = if (entry.isDirectory) "Rename folder" else "Rename file",
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
            initialPath = fsState.currentPath,
            excludedPaths = if (entry.isDirectory) setOf(entry.ref.path.trim('/')) else emptySet(),
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
            initialPath = fsState.currentPath,
            // A folder can't be moved into itself
            excludedPaths = selectedEntries.filter { it.isDirectory }.mapTo(HashSet()) { it.ref.path.trim('/') },
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
        val folderCount = selectedEntries.count { it.isDirectory }
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
            title = if (entry.isSearchIndex()) "Delete the search index?" else if (entry.isDirectory) "Delete folder?" else "Delete file?",
            text = if (entry.isSearchIndex()) {
                SEARCH_INDEX_DELETE_WARNING
            } else if (entry.isDirectory) {
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
    fsState.pendingUpload?.let { pending ->
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
    FileActionDialogs(
        request = actionRequest,
        fileInfoViewModel = fileInfoViewModel,
        filesystemViewModel = filesystemViewModel,
        onDismiss = { actionRequest = null },
        onStarted = { exitSelection() },
        onListDeleted = { listViewModel.closeList() }
    )

    // What can be done with the selected entries, by mode. The first three are icons on the bar, the rest its menu.
    val files = selectedEntries.filter { !it.isDirectory }
    val allSelected = entries.isNotEmpty() && selectedEntries.size == entries.size
    val toggleAll = { selectedKeys = if (allSelected) emptySet() else entries.mapTo(HashSet()) { it.key } }
    val selectAll = SelectionAction(
        label = if (allSelected) "Deselect all" else "Select all",
        icon = Icons.Filled.SelectAll,
        onClick = toggleAll
    )
    val selectionActions: List<SelectionAction> = when (mode) {
        BrowserMode.FILESYSTEM -> buildList {
            add(SelectionAction("Download", Icons.Filled.Download, { fileInfoViewModel.downloadFilesSequentially(files); exitSelection() }, enabled = files.isNotEmpty()))
            if (fsState.canWrite) add(SelectionAction("Move", Icons.AutoMirrored.Filled.DriveFileMove, { showBulkMove = true }, enabled = selectedEntries.isNotEmpty() && !fsState.isModifying))
            if (fsState.canDelete) add(SelectionAction("Delete", Icons.Filled.Delete, { showBulkDelete = true }, enabled = selectedEntries.isNotEmpty() && !fsState.isModifying, destructive = true))
            add(selectAll)
        }
        BrowserMode.FILES, BrowserMode.LISTS -> buildList {
            add(SelectionAction("Download as ZIP", Icons.Filled.FolderZip, { fileInfoViewModel.downloadFilesAsZip(files, openedList?.title ?: "Pixeldrain files"); exitSelection() }, enabled = files.isNotEmpty()))
            add(SelectionAction("Add to list", Icons.AutoMirrored.Filled.PlaylistAdd, { actionRequest = FileActionRequest.AddToList(files) }, enabled = files.isNotEmpty()))
            add(SelectionAction("Delete", Icons.Filled.Delete, { actionRequest = FileActionRequest.Delete(files) }, enabled = files.isNotEmpty(), destructive = true))
            add(SelectionAction("Add to filesystem", Icons.Filled.FolderCopy, { actionRequest = FileActionRequest.AddToFilesystem(files) }, enabled = files.isNotEmpty()))
            openedList?.takeIf { it.canEdit }?.let { list ->
                add(SelectionAction("Remove from this list", Icons.Filled.Remove, { actionRequest = FileActionRequest.RemoveFromList(files, list, entries.size) }, enabled = files.isNotEmpty()))
            }
            add(selectAll)
        }
    }

    PullToRefreshBox(
        isRefreshing = userRefreshing || showLoadingIndicator,
        onRefresh = {
            userRefreshing = true
            refresh()
        },
        state = pullRefreshState,
        modifier = Modifier.fillMaxSize()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header: the path (filesystem), the list being shown (lists), or the sorting and filter (files)
            when (mode) {
                BrowserMode.FILESYSTEM -> Row(verticalAlignment = Alignment.CenterVertically) {
                    PathBreadcrumb(
                        pathSegments = fsState.pathSegments,
                        onPathSegmentClick = { filesystemViewModel.navigateToPathSegment(it) },
                        modifier = Modifier.weight(1f)
                    )
                    if (fsState.canWrite && !selectionMode) {
                        IconButton(onClick = { showNewFolderDialog = true }, enabled = !fsState.isModifying) {
                            Icon(Icons.Filled.CreateNewFolder, contentDescription = "New folder")
                        }
                        if (fsState.canImport) {
                            IconButton(onClick = { showImportDialog = true }, enabled = !fsState.isModifying) {
                                Icon(Icons.Filled.Link, contentDescription = "Import files by ID")
                            }
                        }
                    }
                }
                BrowserMode.LISTS -> if (openedList != null && !selectionMode) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        IconButton(onClick = { listViewModel.closeList() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to the lists")
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(openedList.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(
                                "Files: ${openedList.fileCount}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (entries.isNotEmpty()) {
                            Button(
                                onClick = { fileInfoViewModel.downloadFilesAsZip(entries, openedList.title) },
                                contentPadding = ButtonDefaults.ButtonWithIconContentPadding
                            ) {
                                Icon(Icons.Filled.FolderZip, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                                Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                                Text("All (ZIP)")
                            }
                        }
                    }
                }
                BrowserMode.FILES -> SortControls(
                    uiState = fileState,
                    fileInfoViewModel = fileInfoViewModel,
                    filterFocusRequester = filterFocusRequester,
                    onFilterSubmitted = {}
                )
            }

            if (fsState.isModifying && mode == BrowserMode.FILESYSTEM) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            if (mode == BrowserMode.FILESYSTEM) {
                fsState.uploadProgress?.let { progress ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp),
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
            }
            HorizontalDivider()

            // Body
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when {
                showLoadingIndicator && entries.isEmpty() && !keyMissing && errorMessage == null -> PullableFill {
                    CircularProgressIndicator()
                }
                keyMissing -> PullableFill {
                    ErrorMessage(errorMessage ?: "API Key is missing. Please set it in Settings to load your files.")
                }
                errorMessage != null && entries.isEmpty() -> PullableFill {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(errorMessage, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge)
                        TextButton(onClick = refresh) { Text("Retry") }
                    }
                }
                entries.isEmpty() && !isLoading -> PullableFill {
                    CenteredTextMessage(emptyText(mode, openedList != null, fileState.filterQuery))
                }
                else -> LazyColumn(
                    state = scrollState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = if (isFabVisible) fabHeight + 16.dp else 16.dp)
                ) {
                    items(entries, key = { it.key }) { node ->
                        BrowserEntry(
                            node = node,
                            mode = mode,
                            openedList = openedList,
                            selectionMode = selectionMode,
                            selected = node.key in selectedKeys,
                            canWriteFs = canWriteFs,
                            canDeleteFs = fsState.canDelete && !fsState.isModifying,
                            listCanEdit = listCanEdit,
                            thumbnailUrl = if (node.isDirectory) null else fileInfoViewModel.thumbnailFor(node),
                            shareUrl = if (node.isDirectory) null else fileInfoViewModel.shareUrlFor(node),
                            onOpen = { onOpen(node) },
                            onToggleSelection = {
                                selectedKeys = if (node.key in selectedKeys) selectedKeys - node.key else selectedKeys + node.key
                            },
                            onLongClick = { startSelecting(node.key) },
                            onDownload = { fileInfoViewModel.initiateDownloadFile(node) },
                            onSelect = { startSelecting(node.key) },
                            onRename = { entryToRename = node },
                            onMove = { entryToMove = node },
                            onDelete = { if (mode == BrowserMode.FILESYSTEM) entryToDelete = node else actionRequest = FileActionRequest.Delete(listOf(node)) },
                            onAddToFilesystem = { actionRequest = FileActionRequest.AddToFilesystem(listOf(node)) },
                            onAddToList = { actionRequest = FileActionRequest.AddToList(listOf(node)) },
                            onRemoveFromList = { openedList?.let { actionRequest = FileActionRequest.RemoveFromList(listOf(node), it, entries.size) } },
                            // Lists have web links only on Pixeldrain; other hosts' lists are not on pixeldrain.com
                            pixeldrainLinks = activeKind == ProviderKind.PIXELDRAIN,
                            onCopyLink = { url -> copyLinkToClipboard(context, url) },
                            onShareLink = { url -> shareLink(context, url) }
                        )
                        HorizontalDivider(
                            thickness = 0.5.dp,
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                    }
                }
            }
            }
            SelectionActionBar(
                visible = selectionMode,
                selectedCount = selectedEntries.size,
                onClose = { exitSelection() },
                actions = selectionActions
            )
        }
    }
}

/**
 * Fills the space below the header with [content] and keeps it pull-to-refresh-able. The pull gesture needs something
 * scrollable underneath it, so an empty or failed view is a one-item list which fills the screen.
 */
@Composable
private fun PullableFill(content: @Composable () -> Unit) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            Box(modifier = Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) { content() }
        }
    }
}

private fun emptyText(mode: BrowserMode, inList: Boolean, filter: String): String = when {
    filter.isNotBlank() -> "No files match your filter: '$filter'"
    mode == BrowserMode.LISTS && !inList -> "No lists found."
    mode == BrowserMode.LISTS -> "This list has no files."
    mode == BrowserMode.FILES -> "No files found. Use the refresh button at the top to reload."
    else -> "This folder is empty."
}

/** One row of the browser, with the menu and the actions its mode offers. */
@Composable
private fun BrowserEntry(
    node: StorageNode,
    mode: BrowserMode,
    openedList: FileList?,
    selectionMode: Boolean,
    selected: Boolean,
    canWriteFs: Boolean,
    canDeleteFs: Boolean,
    listCanEdit: Boolean,
    thumbnailUrl: String?,
    shareUrl: String?,
    onOpen: () -> Unit,
    onToggleSelection: () -> Unit,
    onLongClick: () -> Unit,
    onDownload: () -> Unit,
    onSelect: () -> Unit,
    onRename: () -> Unit,
    onMove: () -> Unit,
    onDelete: () -> Unit,
    onAddToFilesystem: () -> Unit,
    onAddToList: () -> Unit,
    onRemoveFromList: () -> Unit,
    onCopyLink: (String) -> Unit,
    onShareLink: (String) -> Unit,
    pixeldrainLinks: Boolean
) {
    val folderLink = if (node.isDirectory && mode == BrowserMode.LISTS && pixeldrainLinks) "https://pixeldrain.com/l/${node.ref.id}" else null
    val modified = remember(node.createdAt) { node.createdAt?.let { formatApiDateTimeString(it) } }
    FileListItem(
        name = node.name,
        type = if (node.isDirectory) "dir" else "file",
        fileSize = node.size,
        modified = modified,
        mimeType = node.mimeType,
        thumbnailUrl = thumbnailUrl,
        selectionMode = selectionMode,
        selected = selected,
        onLongClick = onLongClick,
        trailingContent = {
            when {
                // The lists overview: a folder per list, opened or linked
                node.isDirectory && mode == BrowserMode.LISTS -> ListFolderMenu(node.name, folderLink, onOpen, onCopyLink, onShareLink)
                // The filesystem: folders and files with their own actions
                mode == BrowserMode.FILESYSTEM -> FilesystemMenu(
                    node = node,
                    shareUrl = shareUrl,
                    canWrite = canWriteFs,
                    canDelete = canDeleteFs,
                    onDownload = onDownload,
                    onSelect = onSelect,
                    onRename = onRename,
                    onMove = onMove,
                    onDelete = onDelete,
                    onCopyLink = onCopyLink,
                    onShareLink = onShareLink
                )
                // Files and the files of a list: the shared file menu
                else -> FileItemMenu(
                    file = node,
                    shareUrl = shareUrl,
                    canDelete = if (mode == BrowserMode.LISTS) listCanEdit else true,
                    onDownload = onDownload,
                    onSelect = onSelect,
                    onAddToFilesystem = onAddToFilesystem,
                    onAddToList = onAddToList,
                    onDelete = onDelete,
                    onRemoveFromList = if (mode == BrowserMode.LISTS && listCanEdit && openedList != null) onRemoveFromList else null
                )
            }
        },
        onClick = { if (selectionMode) onToggleSelection() else onOpen() }
    )
}

@Composable
private fun ListFolderMenu(
    title: String,
    link: String?,
    onOpen: () -> Unit,
    onCopyLink: (String) -> Unit,
    onShareLink: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }, modifier = Modifier.size(40.dp)) {
            Icon(Icons.Filled.MoreVert, contentDescription = "More options for $title")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("Open") },
                leadingIcon = { Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null) },
                onClick = { expanded = false; onOpen() }
            )
            if (link != null) {
                DropdownMenuItem(
                    text = { Text("Copy link") },
                    leadingIcon = { Icon(Icons.Filled.ContentCopy, contentDescription = null) },
                    onClick = { expanded = false; onCopyLink(link) }
                )
                DropdownMenuItem(
                    text = { Text("Share link") },
                    leadingIcon = { Icon(Icons.Filled.Share, contentDescription = null) },
                    onClick = { expanded = false; onShareLink(link) }
                )
            }
        }
    }
}

@Composable
private fun FilesystemMenu(
    node: StorageNode,
    shareUrl: String?,
    canWrite: Boolean,
    canDelete: Boolean,
    onDownload: () -> Unit,
    onSelect: () -> Unit,
    onRename: () -> Unit,
    onMove: () -> Unit,
    onDelete: () -> Unit,
    onCopyLink: (String) -> Unit,
    onShareLink: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }, modifier = Modifier.size(40.dp)) {
            Icon(Icons.Filled.MoreVert, contentDescription = "More options for ${node.name}")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            if (!node.isDirectory) {
                DropdownMenuItem(
                    text = { Text("Download") },
                    leadingIcon = { Icon(Icons.Filled.Download, contentDescription = null) },
                    onClick = { expanded = false; onDownload() }
                )
            }
            if (shareUrl != null) {
                DropdownMenuItem(
                    text = { Text("Copy link") },
                    leadingIcon = { Icon(Icons.Filled.ContentCopy, contentDescription = null) },
                    onClick = { expanded = false; onCopyLink(shareUrl) }
                )
                DropdownMenuItem(
                    text = { Text("Share link") },
                    leadingIcon = { Icon(Icons.Filled.Share, contentDescription = null) },
                    onClick = { expanded = false; onShareLink(shareUrl) }
                )
            }
            if (canWrite) {
                DropdownMenuItem(
                    text = { Text("Rename") },
                    leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                    onClick = { expanded = false; onRename() }
                )
                DropdownMenuItem(
                    text = { Text("Move") },
                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.DriveFileMove, contentDescription = null) },
                    onClick = { expanded = false; onMove() }
                )
            }
            DropdownMenuItem(
                text = { Text("Select") },
                leadingIcon = { Icon(Icons.Filled.CheckBox, contentDescription = null) },
                onClick = { expanded = false; onSelect() }
            )
            if (canDelete) {
                DropdownMenuItem(
                    text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                    leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                    onClick = { expanded = false; onDelete() }
                )
            }
        }
    }
}
