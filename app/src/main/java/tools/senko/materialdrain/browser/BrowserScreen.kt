package tools.senko.materialdrain.browser

import tools.senko.materialdrain.ui.LocalListLook
import tools.senko.materialdrain.ui.components.FileGridItem
import tools.senko.materialdrain.util.formatApiDateTimeString
import androidx.compose.foundation.layout.BoxWithConstraints
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.Icons
import tools.senko.materialdrain.ui.icons.automirrored.filled.DriveFileMove
import tools.senko.materialdrain.ui.icons.automirrored.filled.PlaylistAdd
import tools.senko.materialdrain.ui.icons.filled.CreateNewFolder
import tools.senko.materialdrain.ui.icons.filled.Delete
import tools.senko.materialdrain.ui.icons.filled.Download
import tools.senko.materialdrain.ui.icons.filled.FolderCopy
import tools.senko.materialdrain.ui.icons.filled.Remove
import tools.senko.materialdrain.ui.icons.filled.SelectAll
import tools.senko.materialdrain.ui.icons.filled.Edit
import tools.senko.materialdrain.ui.icons.filled.FolderZip
import tools.senko.materialdrain.ui.icons.filled.Link
import tools.senko.materialdrain.ui.icons.filled.Share
import tools.senko.materialdrain.ui.icons.filled.Upload
import tools.senko.materialdrain.ui.icons.automirrored.filled.ArrowBack
import tools.senko.materialdrain.ui.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import tools.senko.materialdrain.settings.LocalDataSaver
import tools.senko.materialdrain.files.FileActionDialogs
import tools.senko.materialdrain.files.FileActionRequest
import tools.senko.materialdrain.files.FileInfoViewModel
import tools.senko.materialdrain.files.listDate
import tools.senko.materialdrain.files.FileItemMenu
import tools.senko.materialdrain.files.SelectionAction
import tools.senko.materialdrain.files.SelectionActionBar
import tools.senko.materialdrain.files.key
import tools.senko.materialdrain.files.fileComparator
import tools.senko.materialdrain.files.shareLink
import tools.senko.materialdrain.filesystem.FilesystemUploadProgress
import tools.senko.materialdrain.filesystem.FilesystemViewModel
import tools.senko.materialdrain.lists.ListViewModel
import tools.senko.materialdrain.provider.api.FileList
import tools.senko.materialdrain.provider.api.ProviderCapability
import tools.senko.materialdrain.provider.api.StorageNode
import tools.senko.materialdrain.provider.api.StorageRef
import tools.senko.materialdrain.settings.AppSettings
import tools.senko.materialdrain.settings.SEARCH_INDEX_DELETE_WARNING
import tools.senko.materialdrain.settings.isSearchIndex
import tools.senko.materialdrain.ui.LocalBottomInset
import tools.senko.materialdrain.ui.components.AppMenuDivider
import tools.senko.materialdrain.ui.components.AppMenuItem
import tools.senko.materialdrain.ui.components.DotScrollbar
import tools.senko.materialdrain.ui.components.CenteredTextMessage
import tools.senko.materialdrain.ui.components.ConfirmDialog
import tools.senko.materialdrain.ui.components.ErrorMessage
import tools.senko.materialdrain.ui.components.FileListItem
import tools.senko.materialdrain.ui.components.FolderPickerDialog
import tools.senko.materialdrain.ui.components.OverflowMenuButton
import tools.senko.materialdrain.ui.components.TextInputDialog
import tools.senko.materialdrain.util.formatRelativeDateTime
import tools.senko.materialdrain.filesystem.PathSegment
import tools.senko.materialdrain.ui.preview.PreviewTheme
import tools.senko.materialdrain.ui.preview.ScreenPreviews
import tools.senko.materialdrain.ui.preview.PreviewSamples
import androidx.compose.foundation.lazy.rememberLazyListState

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
    appSettings: AppSettings,
    // Capabilities this screen's config entry takes away, on top of what the host itself can't do (see ScreenConfig)
    disabledCapabilities: Set<ProviderCapability> = emptySet(),
    // The search modal, opened by the magnifier at the top of the screen (see App.kt)
    showSearchModal: Boolean = false,
    onDismissSearchModal: () -> Unit = {},
    onFileSelected: () -> Unit,
    scrollState: LazyListState,
    fabHeight: Dp,
    isFabVisible: Boolean,
    onSelectingChange: (Boolean) -> Unit,
    /** Told the screen's actions for New in the ⋮ menu at the top (see App.kt), e.g. the filesystem's upload. */
    onActionsChange: (List<ScreenAction>) -> Unit = {},
    /** Files shared from another app, waiting for a folder of this Filesystem tab (see App.kt) */
    sharedUpload: SharedUpload? = null
) {
    val fsState by filesystemViewModel.uiState.collectAsState()
    val fsDisplayedChildren by filesystemViewModel.displayedChildren.collectAsState()
    val fileState by fileInfoViewModel.uiState.collectAsState()
    val displayedFiles by fileInfoViewModel.displayedFiles.collectAsState()
    val listState by listViewModel.uiState.collectAsState()
    val listDisplayedFiles by listViewModel.displayedListFiles.collectAsState()
    val openedList = listState.openedList.takeIf { mode == BrowserMode.LISTS }
    val context = LocalContext.current
    // Only whether a transfer runs (and which file an upload is on), not each of its progress ticks: those would redraw the
    // whole screen several times a second. The progress itself is the bar above (see App.kt's TransferBarSlot)
    val downloads = fileInfoViewModel.downloads.collectAsState()
    val downloading by remember(fileInfoViewModel) { derivedStateOf { downloads.value.isDownloading } }
    val fsUploadProgress = filesystemViewModel.uploadProgress.collectAsState()
    val fsUploadStatus by remember(filesystemViewModel) { derivedStateOf { fsUploadProgress.value?.statusLine() } }

    // The sort order: shared by every sortable list in the app (see AppSettings.sortField)
    val sortField by appSettings.sortField.collectAsState()
    val sortAscending by appSettings.sortAscending.collectAsState()
    val foldersFirst by appSettings.foldersFirst.collectAsState()

    // The lists as folders, sorted again only when the lists change
    val listFolders = remember(listState.lists) { listState.lists.sortedBy { it.title.lowercase() }.map { it.asFolder() } }
    // What this mode lists, and how it loads and fails
    val entries: List<StorageNode> = when (mode) {
        BrowserMode.FILESYSTEM -> fsDisplayedChildren
        BrowserMode.FILES -> displayedFiles
        BrowserMode.LISTS -> if (openedList == null) listFolders else listDisplayedFiles
    }
    // The filter query of whichever list this mode shows, for the empty-state message
    val activeFilterQuery = when (mode) {
        BrowserMode.FILESYSTEM -> fsState.filterQuery
        BrowserMode.FILES -> fileState.filterQuery
        BrowserMode.LISTS -> listState.listFilterQuery
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
    val canWriteFs = mode == BrowserMode.FILESYSTEM && fsState.canWrite && ProviderCapability.RENAME !in disabledCapabilities
    val canUploadFs = ProviderCapability.UPLOAD !in disabledCapabilities
    val canMkdirFs = ProviderCapability.MKDIR !in disabledCapabilities
    val canDelete = ProviderCapability.DELETE !in disabledCapabilities
    val listCanEdit = openedList?.canEdit == true

    // Folders usually load within a fraction of a second, so the loading indicator only appears when it takes longer
    // (or right away when the user pulls to refresh)
    var showLoadingIndicator by remember { mutableStateOf(false) }
    var userRefreshing by remember { mutableStateOf(false) }
    val isLoadingNow by rememberUpdatedState(isLoading)
    // The keys shown right before a refresh, so the list can tell whether it came back with something new. Null
    // except while a refresh triggered below is in flight (not set on the ViewModels' own initial load)
    var keysBeforeRefresh by remember { mutableStateOf<Set<String>?>(null) }
    // Refreshing keeps the scroll position, same as before — unless it turns up a file which wasn't there already,
    // in which case it's more useful at the top where the new file is than wherever the list happened to be scrolled to
    val triggerRefresh: () -> Unit = {
        keysBeforeRefresh = entries.mapTo(HashSet()) { it.key }
        refresh()
    }
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
    val selectedEntries = remember(entries, selectedKeys) { entries.filter { it.key in selectedKeys } }
    // Entries which were deleted, moved or left the view can't stay selected
    LaunchedEffect(entries) {
        if (selectedKeys.isNotEmpty()) {
            val listed = entries.mapTo(HashSet()) { it.key }
            val remaining = selectedKeys.filterTo(HashSet()) { it in listed }
            if (remaining.size != selectedKeys.size) selectedKeys = remaining
        }
    }
    // A refresh which turned up a file that wasn't shown before jumps to the top, instantly, so the new file is where
    // it's seen; one which didn't change what's new leaves the scroll position alone
    LaunchedEffect(entries) {
        val before = keysBeforeRefresh ?: return@LaunchedEffect
        keysBeforeRefresh = null
        if (entries.any { it.key !in before }) scrollState.scrollToItem(0)
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

    // The Filesystem screen's own upload button, scoped here (not a FAB) so it works the same whichever navigation
    // style is on — the classic bottom bar and the FAB navigation prototype don't always have a FAB of their own
    val filesystemUploadLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents(),
        onResult = { uris: List<Uri> -> filesystemViewModel.onFilesPickedForUpload(uris, context) }
    )

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
                // The files beside it, in the order they're shown, for a swipe in the details (Look and feel)
                fileInfoViewModel.setNeighbours(entries.filter { !it.isDirectory }, byNode = mode == BrowserMode.FILESYSTEM)
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
            rootPath = filesystemViewModel.browseRootPath,
            rootName = filesystemViewModel.browseRootName,
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
            rootPath = filesystemViewModel.browseRootPath,
            rootName = filesystemViewModel.browseRootName,
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
    // Search: whichever list this mode shows (the lists overview has nothing to search, App.kt offers no magnifier there).
    // Back closes it before anything else, as a dialog takes the press itself
    // The last search of this screen, there again when the search is opened again (the list itself is never filtered)
    var searchQuery by rememberSaveable(mode) { mutableStateOf("") }
    if (showSearchModal) {
        val resultLimit by appSettings.searchResultLimit.collectAsState()
        // Everything the list holds: the modal does its own matching and sorting, and never changes the list itself
        when (mode) {
            BrowserMode.FILESYSTEM -> {
                // The open folder and everything under it, looked through while the search is open (see
                // FilesystemViewModel.indexFolderTree); until the first of it arrives, the open folder itself
                DisposableEffect(Unit) {
                    filesystemViewModel.indexFolderTree()
                    onDispose { filesystemViewModel.pauseFolderTreeIndex() }
                }
                val treeIndex by filesystemViewModel.treeIndex.collectAsState()
                val root = fsState.currentPath.trim('/')
                val tree = treeIndex?.takeIf { it.root == root }
                SearchModal(
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                    candidates = tree?.nodes?.takeIf { it.isNotEmpty() } ?: fsState.visibleChildren,
                    order = remember(sortField, sortAscending, foldersFirst) { fileComparator(sortField, sortAscending, directoriesFirst = foldersFirst) },
                    resultLimit = resultLimit,
                    thumbnailFor = { fileInfoViewModel.thumbnailFor(it) },
                    placeholder = "Search this folder and its subfolders",
                    onResultClick = onOpen,
                    onDismiss = onDismissSearchModal,
                    locationOf = { node ->
                        val parent = node.ref.path.trim('/').substringBeforeLast('/', "")
                        parent.removePrefix(root).trim('/').ifEmpty { null }
                    },
                    status = when {
                        tree == null -> null
                        !tree.done -> "Looking through subfolders… ${tree.foldersScanned} so far"
                        tree.foldersFailed > 0 -> "${tree.foldersFailed} folders couldn't be opened"
                        else -> null
                    }
                )
            }
            BrowserMode.FILES -> SearchModal(
                query = searchQuery,
                onQueryChange = { searchQuery = it },
                candidates = fileState.userFilesList,
                order = remember(sortField, sortAscending) { fileComparator(sortField, sortAscending) },
                resultLimit = resultLimit,
                thumbnailFor = { fileInfoViewModel.thumbnailFor(it) },
                placeholder = "Search your files",
                onResultClick = onOpen,
                onDismiss = onDismissSearchModal
            )
            BrowserMode.LISTS -> if (openedList != null) SearchModal(
                query = searchQuery,
                onQueryChange = { searchQuery = it },
                candidates = listState.listFiles,
                order = remember(sortField, sortAscending) { fileComparator(sortField, sortAscending) },
                resultLimit = resultLimit,
                thumbnailFor = { fileInfoViewModel.thumbnailFor(it) },
                placeholder = "Search ${openedList.title}",
                onResultClick = onOpen,
                onDismiss = onDismissSearchModal
            )
        }
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
            if (fsState.canDelete && canDelete) add(SelectionAction("Delete", Icons.Filled.Delete, { showBulkDelete = true }, enabled = selectedEntries.isNotEmpty() && !fsState.isModifying, destructive = true))
            add(selectAll)
        }
        BrowserMode.FILES, BrowserMode.LISTS -> buildList {
            // Each file on its own, or all of them in one archive
            add(SelectionAction("Download", Icons.Filled.Download, { fileInfoViewModel.downloadFilesSequentially(files); exitSelection() }, enabled = files.isNotEmpty()))
            add(SelectionAction("Download as ZIP", Icons.Filled.FolderZip, { fileInfoViewModel.downloadFilesAsZip(files, openedList?.title ?: "Pixeldrain files"); exitSelection() }, enabled = files.isNotEmpty()))
            if (canDelete) add(SelectionAction("Delete", Icons.Filled.Delete, { actionRequest = FileActionRequest.Delete(files) }, enabled = files.isNotEmpty(), destructive = true))
            add(SelectionAction("Add to list", Icons.AutoMirrored.Filled.PlaylistAdd, { actionRequest = FileActionRequest.AddToList(files) }, enabled = files.isNotEmpty()))
            add(SelectionAction("Add to filesystem", Icons.Filled.FolderCopy, { actionRequest = FileActionRequest.AddToFilesystem(files) }, enabled = files.isNotEmpty()))
            openedList?.takeIf { it.canEdit }?.let { list ->
                // The list's own file count, not the filtered entries (see the single-item action below)
                add(SelectionAction("Remove from this list", Icons.Filled.Remove, { actionRequest = FileActionRequest.RemoveFromList(files, list, listState.listFiles.size) }, enabled = files.isNotEmpty()))
            }
            add(selectAll)
        }
    }

    // The Filesystem's actions behind New in the ⋮ menu at the top: upload, new folder and import, while it can be written to
    val screenActions = if (mode != BrowserMode.FILESYSTEM) emptyList() else if (fsState.canWrite && !selectionMode) buildList {
            if (canUploadFs) add(ScreenAction(Icons.Filled.Upload, "Upload", enabled = !fsState.isModifying && fsUploadStatus == null) { filesystemUploadLauncher.launch("*/*") })
            if (canMkdirFs) add(ScreenAction(Icons.Filled.CreateNewFolder, "New folder", enabled = !fsState.isModifying) { showNewFolderDialog = true })
            if (fsState.canImport) add(ScreenAction(Icons.Filled.Link, "Import files by ID", enabled = !fsState.isModifying) { showImportDialog = true })
        } else emptyList()
    val currentOnActionsChange by rememberUpdatedState(onActionsChange)
    SideEffect { currentOnActionsChange(screenActions) }
    BrowserContent(
        mode = mode,
        entries = entries,
        isLoading = isLoading,
        refreshing = userRefreshing || showLoadingIndicator,
        showLoadingIndicator = showLoadingIndicator,
        onRefresh = {
            userRefreshing = true
            triggerRefresh()
        },
        onRetry = triggerRefresh,
        errorMessage = errorMessage,
        keyMissing = keyMissing,
        filterQuery = activeFilterQuery,
        // What stops the transfer the bar above this screen shows (see App.kt's TransferBarSlot): the filesystem's upload
        // there, else the downloads. Offered as a Cancel card next to the sorting
        onCancelTransfer = when {
            mode == BrowserMode.FILESYSTEM && fsUploadStatus != null -> { { filesystemViewModel.cancelUpload() } }
            downloading -> { { fileInfoViewModel.cancelAllDownloads() } }
            else -> null
        },
        pathSegments = fsState.pathSegments,
        onPathSegmentClick = { filesystemViewModel.navigateToPathSegment(it) },
        isModifying = fsState.isModifying,
        uploadStatus = fsUploadStatus,
        openedList = openedList,
        listHasFiles = listState.listFiles.isNotEmpty(),
        onCloseList = { listViewModel.closeList() },
        onDownloadList = { openedList?.let { fileInfoViewModel.downloadFilesAsZip(listState.listFiles, it.title) } },
        onDownloadListFiles = { fileInfoViewModel.downloadFilesSequentially(listState.listFiles) },
        selectionMode = selectionMode,
        selectedKeys = selectedKeys,
        selectedCount = selectedEntries.size,
        selectionActions = selectionActions,
        onExitSelection = { exitSelection() },
        canWriteFs = canWriteFs,
        canDeleteFs = fsState.canDelete && !fsState.isModifying && canDelete,
        canDelete = canDelete,
        listCanEdit = listCanEdit,
        thumbnailFor = { fileInfoViewModel.thumbnailFor(it) },
        shareUrlFor = { node ->
            when {
                !node.isDirectory -> fileInfoViewModel.shareUrlFor(node)
                mode == BrowserMode.LISTS -> node.ref.id?.let { fileInfoViewModel.listShareUrlFor(it) }
                else -> null
            }
        },
        onOpen = onOpen,
        rowActions = BrowserRowActions(
            toggleSelection = { node -> selectedKeys = if (node.key in selectedKeys) selectedKeys - node.key else selectedKeys + node.key },
            startSelecting = { node -> startSelecting(node.key) },
            download = { node -> fileInfoViewModel.initiateDownloadFile(node) },
            rename = { node -> entryToRename = node },
            move = { node -> entryToMove = node },
            delete = { node -> if (mode == BrowserMode.FILESYSTEM) entryToDelete = node else actionRequest = FileActionRequest.Delete(listOf(node)) },
            addToFilesystem = { node -> actionRequest = FileActionRequest.AddToFilesystem(listOf(node)) },
            addToList = { node -> actionRequest = FileActionRequest.AddToList(listOf(node)) },
            // The list's own file count, not the filtered entries: removing the last matching file mustn't look like the
            // list itself went empty when a filter just hides the rest
            removeFromList = { node -> openedList?.let { actionRequest = FileActionRequest.RemoveFromList(listOf(node), it, listState.listFiles.size) } }
        ),
        scrollState = scrollState,
        fabHeight = fabHeight,
        isFabVisible = isFabVisible,
        // The selection's bar takes its place while items are selected
        sharedUpload = sharedUpload?.takeIf { mode == BrowserMode.FILESYSTEM && !selectionMode },
        sharedUploadFolder = fsState.pathSegments.lastOrNull()?.name ?: fsState.hostName,
        canUploadShared = fsState.canWrite && canUploadFs && !fsState.isModifying && fsUploadStatus == null
    )
}

/** What a row of the browser does, besides opening: BrowserScreen's dialogs and ViewModels; nothing in a preview. */
class BrowserRowActions(
    val toggleSelection: (StorageNode) -> Unit = {},
    val startSelecting: (StorageNode) -> Unit = {},
    val download: (StorageNode) -> Unit = {},
    val rename: (StorageNode) -> Unit = {},
    val move: (StorageNode) -> Unit = {},
    val delete: (StorageNode) -> Unit = {},
    val addToFilesystem: (StorageNode) -> Unit = {},
    val addToList: (StorageNode) -> Unit = {},
    val removeFromList: (StorageNode) -> Unit = {}
)

/**
 * The browser as it's drawn: the header of [mode] (the sorting, the path or the open list), then [entries] or why there
 * are none, and the selection's bar. Everything comes in as it is and what it does goes out through the callbacks, so a
 * preview can show it (see the end of the file); BrowserScreen works it all out from the ViewModels.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserContent(
    mode: BrowserMode,
    entries: List<StorageNode>,
    isLoading: Boolean,
    refreshing: Boolean,
    showLoadingIndicator: Boolean,
    onRefresh: () -> Unit,
    onRetry: () -> Unit,
    errorMessage: String?,
    keyMissing: Boolean,
    filterQuery: String,
    onCancelTransfer: (() -> Unit)?,
    pathSegments: List<PathSegment>,
    onPathSegmentClick: (PathSegment) -> Unit,
    isModifying: Boolean,
    uploadStatus: String?,
    openedList: FileList?,
    listHasFiles: Boolean,
    onCloseList: () -> Unit,
    onDownloadList: () -> Unit,
    onDownloadListFiles: () -> Unit,
    selectionMode: Boolean,
    selectedKeys: Set<String>,
    selectedCount: Int,
    selectionActions: List<SelectionAction>,
    onExitSelection: () -> Unit,
    canWriteFs: Boolean,
    canDeleteFs: Boolean,
    canDelete: Boolean,
    listCanEdit: Boolean,
    thumbnailFor: (StorageNode) -> String?,
    shareUrlFor: (StorageNode) -> String?,
    onOpen: (StorageNode) -> Unit,
    rowActions: BrowserRowActions,
    scrollState: LazyListState,
    fabHeight: Dp,
    isFabVisible: Boolean,
    sharedUpload: SharedUpload? = null,
    sharedUploadFolder: String = "",
    canUploadShared: Boolean = false
) {
    val context = LocalContext.current
    val pullRefreshState = rememberPullToRefreshState()
    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = onRefresh,
        state = pullRefreshState,
        modifier = Modifier.fillMaxSize()
    ) {
        // The thumbnails the data saver holds back, all at once (see DataSaver); null while none are held back
        val loadThumbnails = LocalDataSaver.current.let { if (it.holdThumbnails) it.loadAllThumbnails else null }
        Column(modifier = Modifier.fillMaxSize()) {
            // Header: the sorting and filter of whichever mode's list is sortable (every mode except the lists overview,
            // which is just titles), then right above the content the path (filesystem) or the list being shown (lists)
            when (mode) {
                BrowserMode.FILESYSTEM -> {
                    StatusCards(onCancelTransfer = onCancelTransfer, onLoadThumbnails = loadThumbnails)
                    PathBreadcrumb(
                        pathSegments = pathSegments,
                        onPathSegmentClick = onPathSegmentClick
                    )
                }
                BrowserMode.LISTS -> if (openedList != null) {
                    StatusCards(onCancelTransfer = onCancelTransfer, onLoadThumbnails = loadThumbnails)
                    if (!selectionMode) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            IconButton(onClick = onCloseList) {
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
                            // Always the whole list, even while a filter narrows what's shown below: each file on its own,
                            // or all of them in one archive
                            if (listHasFiles) {
                                FilledTonalIconButton(onClick = onDownloadListFiles) {
                                    Icon(Icons.Filled.Download, contentDescription = "Download all, each file on its own")
                                }
                                Button(
                                    onClick = onDownloadList,
                                    contentPadding = ButtonDefaults.ButtonWithIconContentPadding
                                ) {
                                    Icon(Icons.Filled.FolderZip, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                                    Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                                    Text("All (ZIP)")
                                }
                            }
                        }
                    }
                }
                BrowserMode.FILES -> StatusCards(onCancelTransfer = onCancelTransfer, onLoadThumbnails = loadThumbnails)
            }

            if (isModifying && mode == BrowserMode.FILESYSTEM) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            if (mode == BrowserMode.FILESYSTEM) {
                uploadStatus?.let { status ->
                    // Cancelled with the Cancel card in the sort row above
                    Text(
                        text = status,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)
                    )
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
                        TextButton(onClick = onRetry) { Text("Retry") }
                    }
                }
                entries.isEmpty() && !isLoading -> PullableFill {
                    CenteredTextMessage(emptyText(mode, openedList != null, filterQuery))
                }
                else -> BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    // One entry, as a row or as a tile of the grid (Look and feel)
                    val entry: @Composable (StorageNode, Boolean, Modifier) -> Unit = { node, asTile, entryModifier ->
                            BrowserEntry(
                                node = node,
                                asTile = asTile,
                                modifier = entryModifier,
                                mode = mode,
                                openedList = openedList,
                                selectionMode = selectionMode,
                                selected = node.key in selectedKeys,
                                canWriteFs = canWriteFs,
                                canDeleteFs = canDeleteFs,
                                canDelete = canDelete,
                                listCanEdit = listCanEdit,
                                thumbnailUrl = if (node.isDirectory) null else thumbnailFor(node),
                                shareUrl = shareUrlFor(node),
                                onOpen = { onOpen(node) },
                                onToggleSelection = { rowActions.toggleSelection(node) },
                                onLongClick = { rowActions.startSelecting(node) },
                                onDownload = { rowActions.download(node) },
                                onRename = { rowActions.rename(node) },
                                onMove = { rowActions.move(node) },
                                onDelete = { rowActions.delete(node) },
                                onAddToFilesystem = { rowActions.addToFilesystem(node) },
                                onAddToList = { rowActions.addToList(node) },
                                onRemoveFromList = { rowActions.removeFromList(node) },
                                onShareLink = { url -> shareLink(context, url) }
                            )
                    }
                    val grid = LocalListLook.current.grid
                    // As many tiles to a row as fit at about 120dp each, two at the least
                    val columns = (maxWidth / GRID_TILE_WIDTH).toInt().coerceAtLeast(2)
                    LazyColumn(
                        state = scrollState,
                        modifier = Modifier.fillMaxSize(),
                        // Past the last file: room for the upload button, or the system's navigation bar (LocalBottomInset)
                        contentPadding = PaddingValues(bottom = (if (isFabVisible) fabHeight + 16.dp else 16.dp) + LocalBottomInset.current)
                    ) {
                        if (grid) {
                            // The grid is rows of tiles, so it scrolls (and keeps its place) with the same state as the list
                            items(entries.chunked(columns), key = { row -> "row:" + row.first().key }) { row ->
                                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp)) {
                                    row.forEach { node -> entry(node, true, Modifier.weight(1f)) }
                                    repeat(columns - row.size) { Spacer(modifier = Modifier.weight(1f)) }
                                }
                            }
                        } else {
                            items(entries, key = { it.key }) { node ->
                                entry(node, false, Modifier)
                                HorizontalDivider(
                                    thickness = 0.5.dp,
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                )
                            }
                        }
                    }
                    DotScrollbar(state = scrollState)
                }
            }
            }
            SelectionActionBar(
                visible = selectionMode,
                selectedCount = selectedCount,
                onClose = onExitSelection,
                actions = selectionActions
            )
            SharedUploadBar(sharedUpload, folder = sharedUploadFolder, canUpload = canUploadShared)
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

/** How wide a tile of the grid is about to be; the row gets as many as fit. */
private val GRID_TILE_WIDTH = 120.dp

/** One row (or, [asTile], one tile of the grid) of the browser, with the menu and the actions its mode offers. */
@Composable
private fun BrowserEntry(
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
    onDownload: () -> Unit,
    onRename: () -> Unit,
    onMove: () -> Unit,
    onDelete: () -> Unit,
    onAddToFilesystem: () -> Unit,
    onAddToList: () -> Unit,
    onRemoveFromList: () -> Unit,
    onShareLink: (String) -> Unit
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
                node.isDirectory && mode == BrowserMode.LISTS -> ListFolderMenu(node.name, folderLink, onOpen, onShareLink)
                // The filesystem: folders and files with their own actions
                mode == BrowserMode.FILESYSTEM -> FilesystemMenu(
                    node = node,
                    shareUrl = shareUrl,
                    canWrite = canWriteFs,
                    canDelete = canDeleteFs,
                    onDownload = onDownload,
                    onRename = onRename,
                    onMove = onMove,
                    onDelete = onDelete
                )
                // Files and the files of a list: the shared file menu
                else -> FileItemMenu(
                    file = node,
                    shareUrl = shareUrl,
                    canDelete = canDelete && (if (mode == BrowserMode.LISTS) listCanEdit else true),
                    onDownload = onDownload,
                    onAddToFilesystem = onAddToFilesystem,
                    onAddToList = onAddToList,
                    onDelete = onDelete,
                    onRemoveFromList = if (mode == BrowserMode.LISTS && listCanEdit && openedList != null) onRemoveFromList else null
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

@Composable
private fun ListFolderMenu(
    title: String,
    link: String?,
    onOpen: () -> Unit,
    onShareLink: (String) -> Unit
) {
    OverflowMenuButton(contentDescription = "More options for $title") { close ->
        AppMenuItem("Open", leadingIcon = Icons.AutoMirrored.Filled.OpenInNew, onClick = { close(); onOpen() })
        if (link != null) {
            AppMenuItem("Share link", leadingIcon = Icons.Filled.Share, onClick = { close(); onShareLink(link) })
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
    onRename: () -> Unit,
    onMove: () -> Unit,
    onDelete: () -> Unit
) {
    OverflowMenuButton(contentDescription = "More options for ${node.name}") { close ->
        if (!node.isDirectory) {
            AppMenuItem("Download", leadingIcon = Icons.Filled.Download, onClick = { close(); onDownload() })
        }
        if (canWrite) {
            AppMenuItem("Rename", leadingIcon = Icons.Filled.Edit, onClick = { close(); onRename() })
            AppMenuItem("Move", leadingIcon = Icons.AutoMirrored.Filled.DriveFileMove, supportingText = "To another folder", onClick = { close(); onMove() })
        }
        if (canDelete) {
            AppMenuDivider()
            AppMenuItem("Delete", leadingIcon = Icons.Filled.Delete, destructive = true, onClick = { close(); onDelete() })
        }
    }
}

/** The line under the sort row while the filesystem uploads: which file it's on, of how many. */
private fun FilesystemUploadProgress.statusLine(): String = "Uploading $currentFileName ($currentIndex/$totalFiles)"

// ---- Previews ----

/** The browser in [mode] showing [entries], in the previews' theme. */
@Composable
private fun BrowserPreview(
    mode: BrowserMode,
    entries: List<StorageNode> = PreviewSamples.files,
    isLoading: Boolean = false,
    errorMessage: String? = null,
    keyMissing: Boolean = false,
    openedList: FileList? = null,
    selectedKeys: Set<String>? = null,
    uploadStatus: String? = null
) {
    PreviewTheme { SampleBrowserContent(mode, entries, isLoading, errorMessage, keyMissing, openedList, selectedKeys, uploadStatus) }
}

/**
 * The browser in [mode] showing [entries], as BrowserScreen would draw it, for a preview (here and in App.kt's previews
 * of the whole screen); nothing happens when it's used. Not for the app itself.
 */
@Composable
internal fun SampleBrowserContent(
    mode: BrowserMode,
    entries: List<StorageNode> = PreviewSamples.files,
    isLoading: Boolean = false,
    errorMessage: String? = null,
    keyMissing: Boolean = false,
    openedList: FileList? = null,
    selectedKeys: Set<String>? = null,
    uploadStatus: String? = null
) {
    val selecting = selectedKeys != null
    BrowserContent(
        mode = mode,
        entries = entries,
        isLoading = isLoading,
        refreshing = isLoading,
        showLoadingIndicator = isLoading,
        onRefresh = {},
        onRetry = {},
        errorMessage = errorMessage,
        keyMissing = keyMissing,
        filterQuery = "",
        onCancelTransfer = if (uploadStatus != null) ({}) else null,
        pathSegments = listOf("me", "Holiday photos").runningReduce { a, b -> "$a/$b" }.map { PathSegment(it.substringAfterLast('/'), it) },
        onPathSegmentClick = {},
        isModifying = false,
        uploadStatus = uploadStatus,
        openedList = openedList,
        listHasFiles = entries.isNotEmpty(),
        onCloseList = {},
        onDownloadList = {},
        onDownloadListFiles = {},
        selectionMode = selecting,
        selectedKeys = selectedKeys.orEmpty(),
        selectedCount = selectedKeys?.size ?: 0,
        selectionActions = listOf(
            SelectionAction("Download", Icons.Filled.Download, {}),
            SelectionAction("Move", Icons.AutoMirrored.Filled.DriveFileMove, {}),
            SelectionAction("Delete", Icons.Filled.Delete, {}, destructive = true),
            SelectionAction("Select all", Icons.Filled.SelectAll, {})
        ),
        onExitSelection = {},
        canWriteFs = true,
        canDeleteFs = true,
        canDelete = true,
        listCanEdit = true,
        thumbnailFor = { null },
        shareUrlFor = { "https://pixeldrain.com/u/${it.ref.id}" },
        onOpen = {},
        rowActions = BrowserRowActions(),
        scrollState = rememberLazyListState(),
        fabHeight = 0.dp,
        isFabVisible = false
    )
}

/** The Files tab: the account's files. */
@ScreenPreviews
@Composable
private fun BrowserFilesPreview() = BrowserPreview(BrowserMode.FILES, PreviewSamples.files.filter { !it.isDirectory })

/** The Filesystem tab inside a folder: its path, the New menu, and an upload under way. */
@ScreenPreviews
@Composable
private fun BrowserFilesystemPreview() = BrowserPreview(BrowserMode.FILESYSTEM, uploadStatus = "Uploading drone-flight.mp4 (2/5)")

/** The Lists tab: the lists as folders. */
@ScreenPreviews
@Composable
private fun BrowserListsPreview() = BrowserPreview(
    BrowserMode.LISTS,
    listOf("Holiday 2026", "Shared with Sam", "Wallpapers").map { StorageNode(StorageRef(id = it), it, isDirectory = true) }
)

/** A list opened: its title, Back, and its downloads (each file, and All (ZIP)). */
@ScreenPreviews
@Composable
private fun BrowserOpenedListPreview() = BrowserPreview(
    BrowserMode.LISTS,
    PreviewSamples.files.filter { !it.isDirectory }.take(5),
    openedList = FileList("a", "Holiday 2026", fileCount = 5, canEdit = true)
)

/** Selecting: the checkboxes, and the selection's bar at the bottom. */
@ScreenPreviews
@Composable
private fun BrowserSelectingPreview() = BrowserPreview(
    BrowserMode.FILESYSTEM,
    selectedKeys = PreviewSamples.files.filterIndexed { index, _ -> index in setOf(1, 2, 4) }.mapTo(HashSet()) { it.key }
)

@ScreenPreviews
@Composable
private fun BrowserEmptyPreview() = BrowserPreview(BrowserMode.FILESYSTEM, entries = emptyList())

@ScreenPreviews
@Composable
private fun BrowserErrorPreview() = BrowserPreview(BrowserMode.FILES, entries = emptyList(), errorMessage = "The host couldn't be reached: timeout after 30 seconds.")

@ScreenPreviews
@Composable
private fun BrowserLoadingPreview() = BrowserPreview(BrowserMode.FILES, entries = emptyList(), isLoading = true)

/** Pixeldrain without its login: what the screen asks for. */
@ScreenPreviews
@Composable
private fun BrowserKeyMissingPreview() = BrowserPreview(BrowserMode.FILES, entries = emptyList(), keyMissing = true)
