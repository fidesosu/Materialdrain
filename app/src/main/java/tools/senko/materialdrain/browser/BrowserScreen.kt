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
import tools.senko.materialdrain.ui.components.PlaceholderList
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import android.app.Application
import androidx.compose.ui.platform.LocalInspectionMode
import tools.senko.materialdrain.AppContainer
import tools.senko.materialdrain.files.previewMimeType
import tools.senko.materialdrain.ui.LocalPlayerSettings
import tools.senko.materialdrain.ui.icons.filled.QueueMusic
import tools.senko.materialdrain.ui.media.addToQueue
import tools.senko.materialdrain.ui.media.rememberPlaceholderArt
import tools.senko.materialdrain.ui.media.rememberPlaybackController
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
import tools.senko.materialdrain.files.DownloadChoiceEntries
import tools.senko.materialdrain.files.DownloadMenuItem
import tools.senko.materialdrain.files.DownloadStyle
import tools.senko.materialdrain.ui.components.AppMenuPages
import tools.senko.materialdrain.files.SelectionAction
import tools.senko.materialdrain.files.SelectionActionBar
import tools.senko.materialdrain.files.SelectionBarHeight
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
import tools.senko.materialdrain.settings.ListLayout
import tools.senko.materialdrain.settings.ViewLook
import tools.senko.materialdrain.ui.icons.filled.CheckBox
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
import tools.senko.materialdrain.filesystem.FilesystemUiState
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
    /** Told the screen's actions for the ⋮ menu at the top (see App.kt), e.g. the filesystem's upload. */
    onActionsChange: (List<ScreenAction>) -> Unit = {},
    /**
     * What the folders' own looks are kept by (see AppSettings.viewLooks): the host and the tab; the folder or the list
     * shown is added to it here
     */
    viewKey: String = "",
    /** Told how the list looks, for the View page of the ⋮ menu at the top (see App.kt) */
    onViewChange: (ViewChoice) -> Unit = {},
    /** Files shared from another app, waiting for the screen they go into (see App.kt): this one, where it takes them */
    sharedUpload: SharedUpload? = null,
    /**
     * Uploads files as new files of the host, for the Files screen's Upload (in the ⋮ menu) and shared files sent to it
     * (see UploadViewModel.uploadNow); null where the host can't take them there
     */
    onUploadFiles: ((List<Uri>) -> Unit)? = null,
    /** Told whether a bar is at the bottom of the screen (the selection's, or one waiting for a place), see App.kt */
    onBottomBarChange: (Boolean) -> Unit = {}
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
            // In the order they were selected, which the songs go to the queue in
            val remaining = selectedKeys.filterTo(LinkedHashSet()) { it in listed }
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
    var entryToDelete by remember { mutableStateOf<StorageNode?>(null) }
    var showBulkDelete by remember { mutableStateOf(false) }
    // Files and list actions (delete, add to filesystem or to a list, remove from a list)
    var actionRequest by remember { mutableStateOf<FileActionRequest?>(null) }

    // The background player, for a song's "Add to queue" (only while songs play in the background)
    val playback = if (LocalPlayerSettings.current.backgroundAudio && !LocalInspectionMode.current) rememberPlaybackController() else null
    val placeholderArt = rememberPlaceholderArt()
    val messages = remember { AppContainer.get(context.applicationContext as Application).uiMessages }

    // The Filesystem screen's own upload button, scoped here (not a FAB) so it works the same whichever navigation
    // style is on
    val filesystemUploadLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents(),
        onResult = { uris: List<Uri> -> filesystemViewModel.onFilesPickedForUpload(uris, context) }
    )

    // The Files screen's Upload: picked files go up right away as new files of the host (see onUploadFiles)
    val filesUploadLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents(),
        onResult = { uris: List<Uri> -> if (uris.isNotEmpty()) onUploadFiles?.invoke(uris) }
    )

    // Entries of this tab being moved (see FilesystemViewModel.pendingMove): the user browses to the folder they go into,
    // where the bar at the bottom moves them. The folders among them can't be opened meanwhile, nor moved into themselves
    val pendingMove by filesystemViewModel.pendingMove.collectAsState()
    val moving = mode == BrowserMode.FILESYSTEM && pendingMove.isNotEmpty()
    val movedFolders = remember(pendingMove) { pendingMove.filter { it.isDirectory }.map { it.ref.path.trim('/') } }

    // Back: leaves the selection first, then the open list, then goes up a folder (filesystem); at the top, it leaves
    // the entries being moved where they are. At the top, the activity takes the press (see MainActivity).
    BackHandler(enabled = moving && !filesystemViewModel.canNavigateToParent()) { filesystemViewModel.cancelMove() }
    BackHandler(enabled = mode == BrowserMode.FILESYSTEM && filesystemViewModel.canNavigateToParent()) {
        filesystemViewModel.navigateToParentPath()
    }
    BackHandler(enabled = openedList != null) { listViewModel.closeList() }
    BackHandler(enabled = selectionMode) { exitSelection() }

    // What happens on tapping an entry
    val onOpen: (StorageNode) -> Unit = { node ->
        when {
            mode == BrowserMode.FILESYSTEM && node.isDirectory && moving && node.ref.path.trim('/') in movedFolders ->
                messages.show("\"${node.name}\" is being moved")
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
            supportingText = "Separate IDs with spaces or commas.",
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
    if (showBulkDelete) {
        val folderCount = selectedEntries.count { it.isDirectory }
        ConfirmDialog(
            title = "Delete ${selectedEntries.size} items?",
            text = (if (folderCount > 0) "Folders are deleted with everything inside. " else "") +
                "This can't be undone." +
                (if (selectedEntries.any { it.isSearchIndex() }) "\n\n$SEARCH_INDEX_DELETE_WARNING" else ""),
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
            text = "${names.size} files already exist here and will be replaced: " +
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
    // The ones already selected stay first, in their order, then the rest as they're shown
    val toggleAll = { selectedKeys = if (allSelected) emptySet() else selectedKeys + entries.map { it.key } }
    val selectAll = SelectionAction(
        label = if (allSelected) "Deselect all" else "Select all",
        icon = Icons.Filled.SelectAll,
        onClick = toggleAll
    )
    // The selected songs at the end of the background player's queue, in the order they were selected (selectedKeys
    // keeps it); the rest are left out
    val songs = remember(selectedEntries, selectedKeys, playback) {
        if (playback == null) {
            emptyList()
        } else {
            val byKey = selectedEntries.associateBy { it.key }
            selectedKeys.mapNotNull { byKey[it] }.mapNotNull { fileInfoViewModel.queueSongOf(it) }
        }
    }
    val addSongsToQueue = playback?.let { player ->
        SelectionAction("Add to queue", Icons.Filled.QueueMusic, {
            val wasEmpty = player.mediaItemCount == 0
            songs.forEach { player.addToQueue(it, placeholderArt) }
            messages.show(
                when {
                    wasEmpty -> "Playing ${songs.first().title}" + if (songs.size > 1) " and ${songs.size - 1} more" else ""
                    songs.size == 1 -> "Added to the queue"
                    else -> "Added ${songs.size} songs to the queue"
                }
            )
            exitSelection()
        }, enabled = songs.isNotEmpty())
    }
    // The open folder's name, for a zip of what's selected in it
    val folderName = fsState.pathSegments.lastOrNull()?.name ?: fsState.hostName
    val selectionActions: List<SelectionAction> = when (mode) {
        BrowserMode.FILESYSTEM -> buildList {
            // One file is only downloaded; a folder, or several things, each file on its own or all in one zip
            val single = selectedEntries.singleOrNull()
            if (single != null && !single.isDirectory) {
                add(SelectionAction("Download", Icons.Filled.Download, { fileInfoViewModel.initiateDownloadFile(single); exitSelection() }))
            } else {
                add(downloadChoices(enabled = selectedEntries.isNotEmpty()) { style ->
                    fileInfoViewModel.downloadItems(selectedEntries, style, single?.name ?: folderName)
                    exitSelection()
                })
            }
            // Then browsed to the folder they go into, in this tab itself (see the move bar below)
            if (fsState.canWrite) add(SelectionAction("Move", Icons.AutoMirrored.Filled.DriveFileMove, { filesystemViewModel.startMove(selectedEntries); exitSelection() }, enabled = selectedEntries.isNotEmpty() && !fsState.isModifying))
            if (fsState.canDelete && canDelete) add(SelectionAction("Delete", Icons.Filled.Delete, { showBulkDelete = true }, enabled = selectedEntries.isNotEmpty() && !fsState.isModifying, destructive = true))
            addSongsToQueue?.let { add(it) }
            add(selectAll)
        }
        BrowserMode.FILES, BrowserMode.LISTS -> buildList {
            // Each file on its own, or all of them in one archive; one file is only downloaded
            if (files.size <= 1) {
                add(SelectionAction("Download", Icons.Filled.Download, { fileInfoViewModel.downloadFilesSequentially(files); exitSelection() }, enabled = files.isNotEmpty()))
            } else {
                add(downloadChoices(enabled = true) { style ->
                    if (style == DownloadStyle.ZIP) fileInfoViewModel.downloadFilesAsZip(files, openedList?.title ?: "Pixeldrain files")
                    else fileInfoViewModel.downloadFilesSequentially(files)
                    exitSelection()
                })
            }
            if (canDelete) add(SelectionAction("Delete", Icons.Filled.Delete, { actionRequest = FileActionRequest.Delete(files) }, enabled = files.isNotEmpty(), destructive = true))
            add(SelectionAction("Add to list", Icons.AutoMirrored.Filled.PlaylistAdd, { actionRequest = FileActionRequest.AddToList(files) }, enabled = files.isNotEmpty()))
            add(SelectionAction("Add to filesystem", Icons.Filled.FolderCopy, { actionRequest = FileActionRequest.AddToFilesystem(files) }, enabled = files.isNotEmpty()))
            openedList?.takeIf { it.canEdit }?.let { list ->
                // The list's own file count, not the filtered entries (see the single-item action below)
                add(SelectionAction("Remove from this list", Icons.Filled.Remove, { actionRequest = FileActionRequest.RemoveFromList(files, list, listState.listFiles.size) }, enabled = files.isNotEmpty()))
            }
            addSongsToQueue?.let { add(it) }
            add(selectAll)
        }
    }

    // The screen's actions in the ⋮ menu at the top (see ScreenMenuEntries for their order): an open list's downloads and
    // Select, for this page; behind New, the Filesystem's upload, new folder and import, while it can be written to
    val listFiles = listState.listFiles
    val screenActions = if (selectionMode) emptyList() else buildList {
        // Always the whole list, even while a filter narrows what's shown: each file on its own, or all in one archive
        if (openedList != null) {
            add(
                ScreenAction(
                    Icons.Filled.Download,
                    if (listFiles.size > 1) "Download all" else "Download",
                    enabled = listFiles.isNotEmpty(),
                    group = ScreenActionGroup.PAGE,
                    choices = if (listFiles.size > 1) {
                        downloadScreenChoices { style ->
                            if (style == DownloadStyle.ZIP) fileInfoViewModel.downloadFilesAsZip(listFiles, openedList.title)
                            else fileInfoViewModel.downloadFilesSequentially(listFiles, title = openedList.title)
                        }
                    } else emptyList()
                ) { fileInfoViewModel.downloadFilesSequentially(listFiles) }
            )
        }
        // The open folder itself, with everything in it
        if (mode == BrowserMode.FILESYSTEM && entries.isNotEmpty()) {
            val folder = StorageNode(ref = StorageRef(path = fsState.currentPath.trim('/')), name = folderName, isDirectory = true)
            add(
                ScreenAction(
                    Icons.Filled.Download,
                    "Download this folder",
                    group = ScreenActionGroup.PAGE,
                    choices = downloadScreenChoices { style -> fileInfoViewModel.downloadItems(listOf(folder), style, folderName) }
                ) {}
            )
        }
        // What a long press on an entry starts, for those who don't know it (not the lists overview, which only opens)
        if (entries.isNotEmpty() && !(mode == BrowserMode.LISTS && openedList == null)) {
            add(ScreenAction(Icons.Filled.CheckBox, "Select", group = ScreenActionGroup.PAGE) { selecting = true })
        }
        // The Files screen's upload, as the Filesystem's: new files of the host
        if (mode == BrowserMode.FILES && onUploadFiles != null) {
            add(ScreenAction(Icons.Filled.Upload, "Upload") { filesUploadLauncher.launch("*/*") })
        }
        if (mode == BrowserMode.FILESYSTEM && fsState.canWrite) {
            if (canUploadFs) add(ScreenAction(Icons.Filled.Upload, "Upload", enabled = !fsState.isModifying && fsUploadStatus == null) { filesystemUploadLauncher.launch("*/*") })
            if (canMkdirFs) add(ScreenAction(Icons.Filled.CreateNewFolder, "New folder", enabled = !fsState.isModifying) { showNewFolderDialog = true })
            if (fsState.canImport) add(ScreenAction(Icons.Filled.Link, "Import files by ID", enabled = !fsState.isModifying) { showImportDialog = true })
        }
    }
    val currentOnActionsChange by rememberUpdatedState(onActionsChange)
    SideEffect { currentOnActionsChange(screenActions) }

    // The look of what's shown: its own (View in the ⋮ menu at the top), else the Look and feel's (LocalListLook)
    val shownViewKey = when (mode) {
        BrowserMode.FILESYSTEM -> "$viewKey:path:${fsState.currentPath.trim('/')}"
        BrowserMode.LISTS -> openedList?.let { "$viewKey:list:${it.id}" } ?: "$viewKey:lists"
        BrowserMode.FILES -> viewKey
    }
    val viewLooks by appSettings.viewLooks.collectAsState()
    val ownLook = viewLooks[shownViewKey]
    // The nearest folder above this one whose look is for its subfolders too (see ViewLook.subfolders): this one has that
    // look where it has none of its own, over the Look and feel's
    val inheritedPath = if (mode != BrowserMode.FILESYSTEM) null else {
        generateSequence(fsState.currentPath.trim('/')) { path -> if (path.isEmpty()) null else path.substringBeforeLast('/', "") }
            .drop(1)
            .firstOrNull { viewLooks["$viewKey:path:$it"]?.subfolders == true }
    }
    val inheritedLook = inheritedPath?.let { viewLooks["$viewKey:path:$it"] }
    val defaultLook = LocalListLook.current
    val defaultLayout = if (defaultLook.grid) ListLayout.GRID else ListLayout.LIST
    val fallbackLayout = inheritedLook?.layout ?: defaultLayout
    val fallbackCompact = inheritedLook?.compact ?: defaultLook.compact
    val listLayout = ownLook?.layout ?: fallbackLayout
    // Smart: a grid for what's mostly pictures and videos, rows for the rest
    val smartGrid = remember(entries) { smartLayoutIsGrid(entries) }
    val look = defaultLook.copy(
        grid = when (listLayout) {
            ListLayout.GRID -> true
            ListLayout.LIST -> false
            ListLayout.SMART -> smartGrid
        },
        compact = ownLook?.compact ?: fallbackCompact
    )
    val viewChoice = ViewChoice(
        grid = look.grid,
        compact = look.compact,
        custom = ownLook != null,
        defaultLabel = when {
            fallbackLayout == ListLayout.SMART -> "Smart"
            fallbackLayout == ListLayout.GRID -> "Grid"
            fallbackCompact -> "Compact rows"
            else -> "List"
        },
        onLayout = { layout -> appSettings.setViewLook(shownViewKey, (ownLook ?: ViewLook()).copy(layout = layout)) },
        onCompact = { compact -> appSettings.setViewLook(shownViewKey, (ownLook ?: ViewLook()).copy(compact = compact)) },
        onReset = { appSettings.setViewLook(shownViewKey, null) },
        subfolders = if (mode == BrowserMode.FILESYSTEM) ownLook?.subfolders == true else null,
        // Turned on, the look shown becomes this folder's own, for its subfolders too, whichever it had it from
        onSubfolders = { on ->
            appSettings.setViewLook(
                shownViewKey,
                if (on) ViewLook(listLayout, look.compact, subfolders = true)
                else ownLook?.copy(subfolders = false)
            )
        },
        inheritedFrom = inheritedPath?.let { path -> if (path.isEmpty()) "the top folder" else path.substringAfterLast('/') },
        smart = listLayout == ListLayout.SMART
    )
    val currentOnViewChange by rememberUpdatedState(onViewChange)
    SideEffect { currentOnViewChange(viewChoice) }

    // The bar of what waits for its place here (entries being moved, shared files), see rememberDestinationBar; with the
    // selection's, what App.kt lifts the button and the snackbars over
    val destinationBar = rememberDestinationBar(
        mode, selectionMode, pendingMove, movedFolders, sharedUpload, fsState, canUploadFs,
        fsUploadBusy = fsUploadStatus != null, filesystemViewModel = filesystemViewModel, onUploadFiles = onUploadFiles
    )
    val bottomBarShown = selectionMode || destinationBar != null
    LaunchedEffect(bottomBarShown) { onBottomBarChange(bottomBarShown) }
    DisposableEffect(Unit) { onDispose { onBottomBarChange(false) } }
    CompositionLocalProvider(LocalListLook provides look) {
    BrowserContent(
        mode = mode,
        entries = entries,
        isLoading = isLoading,
        // Not over the placeholders of a list still empty (see PlaceholderList), which say it already
        refreshing = userRefreshing || (showLoadingIndicator && entries.isNotEmpty()),
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
        onCloseList = { listViewModel.closeList() },
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
            download = { node, style ->
                when {
                    // A list of the overview, as its files
                    mode == BrowserMode.LISTS && openedList == null ->
                        listState.lists.firstOrNull { it.id == node.ref.id }?.let { fileInfoViewModel.downloadList(it, style) }
                    node.isDirectory -> fileInfoViewModel.downloadItems(listOf(node), style, node.name)
                    else -> fileInfoViewModel.initiateDownloadFile(node)
                }
            },
            downloadHasChoices = { node ->
                if (mode == BrowserMode.LISTS && openedList == null) (listState.lists.firstOrNull { it.id == node.ref.id }?.fileCount ?: 0) > 1
                else node.isDirectory
            },
            shareFile = { node -> fileInfoViewModel.shareFile(node) },
            rename = { node -> entryToRename = node },
            move = { node -> filesystemViewModel.startMove(listOf(node)) },
            delete = { node -> if (mode == BrowserMode.FILESYSTEM) entryToDelete = node else actionRequest = FileActionRequest.Delete(listOf(node)) },
            addToFilesystem = { node -> actionRequest = FileActionRequest.AddToFilesystem(listOf(node)) },
            addToList = { node -> actionRequest = FileActionRequest.AddToList(listOf(node)) },
            // The list's own file count, not the filtered entries: removing the last matching file mustn't look like the
            // list itself went empty when a filter just hides the rest
            removeFromList = { node -> openedList?.let { actionRequest = FileActionRequest.RemoveFromList(listOf(node), it, listState.listFiles.size) } },
            addToQueue = playback?.let { player ->
                { node ->
                    fileInfoViewModel.queueSongOf(node)?.let { song ->
                        val wasEmpty = player.mediaItemCount == 0
                        player.addToQueue(song, placeholderArt)
                        messages.show(if (wasEmpty) "Playing ${node.name}" else "Added to the queue")
                    }
                }
            }
        ),
        scrollState = scrollState,
        fabHeight = fabHeight,
        isFabVisible = isFabVisible,
        destinationBar = destinationBar
    )
    }
}

/**
 * The bar of what waits at the bottom of a browser for its place (see DestinationBar), as [BrowserScreen] works it out:
 * the entries being moved ([pendingMove], into the folder that's open), else the files shared to the app
 * ([sharedUpload]), into this screen where it takes them; nothing while items are selected, whose bar has the place.
 */
@Composable
private fun rememberDestinationBar(
    mode: BrowserMode,
    selectionMode: Boolean,
    pendingMove: List<StorageNode>,
    movedFolders: List<String>,
    sharedUpload: SharedUpload?,
    fsState: FilesystemUiState,
    canUploadFs: Boolean,
    fsUploadBusy: Boolean,
    filesystemViewModel: FilesystemViewModel,
    onUploadFiles: ((List<Uri>) -> Unit)?
): DestinationRequest? {
    if (selectionMode) return null
    val context = LocalContext.current
    val folderName = fsState.pathSegments.lastOrNull()?.name ?: fsState.hostName
    if (mode == BrowserMode.FILESYSTEM && pendingMove.isNotEmpty()) {
        val here = fsState.currentPath.trim('/')
        val insideMoved = movedFolders.any { here == it || here.startsWith("$it/") }
        val alreadyHere = pendingMove.all { it.ref.path.trim('/').substringBeforeLast('/', "") == here }
        val canMoveHere = fsState.canWrite && !fsState.isModifying && !insideMoved && !alreadyHere
        return DestinationRequest(
            title = if (pendingMove.size == 1) "Moving \"${pendingMove.first().name}\"" else "Moving ${pendingMove.size} items",
            destination = folderName.takeIf { canMoveHere },
            unavailable = when {
                insideMoved -> "Not into a folder that's being moved"
                alreadyHere -> "Already in this folder: open another"
                else -> "Open a folder that can be written to"
            },
            confirmLabel = "Move here",
            cancelDescription = "Leave them where they are",
            onConfirm = { filesystemViewModel.moveHere() },
            onCancel = { filesystemViewModel.cancelMove() }
        )
    }
    val shared = sharedUpload ?: return null
    // Where this screen puts them: the folder that's open, the host's own files; a list takes no files of its own
    val destination = when (mode) {
        BrowserMode.FILESYSTEM -> folderName.takeIf { fsState.canWrite && canUploadFs && !fsState.isModifying && !fsUploadBusy }
        BrowserMode.FILES -> "your files".takeIf { onUploadFiles != null }
        BrowserMode.LISTS -> null
    }
    return DestinationRequest(
        title = if (shared.count == 1) "1 shared file" else "${shared.count} shared files",
        destination = destination,
        unavailable = when (mode) {
            BrowserMode.FILESYSTEM -> "Open a folder that can be written to"
            BrowserMode.FILES -> "This host takes no uploads here"
            BrowserMode.LISTS -> "Go to Files or a folder to upload them"
        },
        confirmLabel = "Upload here",
        cancelDescription = "Don't upload the shared files",
        // Only where there's a destination: the button is off elsewhere
        onConfirm = {
            when (mode) {
                BrowserMode.FILESYSTEM -> filesystemViewModel.onFilesPickedForUpload(shared.uris, context)
                BrowserMode.FILES -> onUploadFiles?.invoke(shared.uris)
                BrowserMode.LISTS -> Unit
            }
            if (destination != null) shared.onSent()
        },
        onCancel = shared.onCancel
    )
}

/** What a row of the browser does, besides opening: BrowserScreen's dialogs and ViewModels; nothing in a preview. */
class BrowserRowActions(
    val toggleSelection: (StorageNode) -> Unit = {},
    val startSelecting: (StorageNode) -> Unit = {},
    /** Downloads a file, or a folder or a list as [DownloadStyle] says. */
    val download: (StorageNode, DownloadStyle) -> Unit = { _, _ -> },
    /** Whether an entry's Download offers both styles (a folder, a list of several files), in a page of its menu. */
    val downloadHasChoices: (StorageNode) -> Boolean = { it.isDirectory },
    /** Shares a file itself, for a host whose files have no link. */
    val shareFile: (StorageNode) -> Unit = {},
    val rename: (StorageNode) -> Unit = {},
    val move: (StorageNode) -> Unit = {},
    val delete: (StorageNode) -> Unit = {},
    val addToFilesystem: (StorageNode) -> Unit = {},
    val addToList: (StorageNode) -> Unit = {},
    val removeFromList: (StorageNode) -> Unit = {},
    /** Puts a song at the end of the background player's queue; null while songs don't play in the background. */
    val addToQueue: ((StorageNode) -> Unit)? = null
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
    onCloseList: () -> Unit,
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
    /** What waits at the bottom for its place, in the selection bar's place (see DestinationBar) */
    destinationBar: DestinationRequest? = null
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
                    // Its title and back to the lists; what's done with the whole list (its downloads) is in the ⋮ menu at
                    // the top, so the title has the row
                    if (!selectionMode) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 16.dp, top = 4.dp, bottom = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            IconButton(onClick = onCloseList) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to the lists")
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(openedList.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text(
                                    if (openedList.fileCount == 1) "1 file" else "${openedList.fileCount} files",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
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
                // Rows of the shape of the files to come, until they're there (see PlaceholderList)
                showLoadingIndicator && entries.isEmpty() && !keyMissing && errorMessage == null -> PullableFill {
                    PlaceholderList()
                }
                keyMissing -> PullableFill {
                    ErrorMessage(errorMessage ?: "Log in or add an API key in Settings.")
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
                                onDownload = { style -> rowActions.download(node, style) },
                                downloadHasChoices = rowActions.downloadHasChoices(node),
                                onShareFile = if (node.isDirectory) null else { { rowActions.shareFile(node) } },
                                onRename = { rowActions.rename(node) },
                                onMove = { rowActions.move(node) },
                                onDelete = { rowActions.delete(node) },
                                onAddToFilesystem = { rowActions.addToFilesystem(node) },
                                onAddToList = { rowActions.addToList(node) },
                                onRemoveFromList = { rowActions.removeFromList(node) },
                                onShareLink = { url -> shareLink(context, url) },
                                // Only a song: the rest can't be played in the background
                                onAddToQueue = rowActions.addToQueue
                                    ?.takeIf { !node.isDirectory && node.previewMimeType()?.startsWith("audio/") == true }
                                    ?.let { add -> { add(node) } }
                            )
                    }
                    val grid = LocalListLook.current.grid
                    // As many tiles to a row as fit at about 120dp each, two at the least
                    val columns = (maxWidth / GRID_TILE_WIDTH).toInt().coerceAtLeast(2)
                    LazyColumn(
                        state = scrollState,
                        modifier = Modifier.fillMaxSize(),
                        // Past the last file, only the room for what covers the end of the list: the system's navigation
                        // bar or the miniplayer (LocalBottomInset), the upload button, and the bar floating over the list
                        // (the selection's, or one waiting for a place). Nothing more, so a list which fits doesn't scroll
                        contentPadding = PaddingValues(
                            bottom = LocalBottomInset.current +
                                (if (isFabVisible) fabHeight + 16.dp else 0.dp) +
                                (if (selectionMode || destinationBar != null) SelectionBarHeight else 0.dp)
                        )
                    ) {
                        if (grid) {
                            // The grid is rows of tiles, so it scrolls (and keeps its place) with the same state as the list
                            items(entries.chunked(columns), key = { row -> "row:" + row.first().key }) { row ->
                                // Clear of the scrollbar's dot at the end (see DotScrollbar), which sat over the tiles there
                                Row(modifier = Modifier.fillMaxWidth().padding(start = 6.dp, end = GRID_SCROLLBAR_ROOM)) {
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
            // Floating over the end of the list, which shows around them as it does around the miniplayer (the list
            // leaves room for them after its last entry, above)
            Box(modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
                SelectionActionBar(
                    visible = selectionMode,
                    selectedCount = selectedCount,
                    onClose = onExitSelection,
                    actions = selectionActions
                )
                DestinationBar(destinationBar)
            }
            }
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
    filter.isNotBlank() -> "No matches for '$filter'"
    mode == BrowserMode.LISTS && !inList -> "No lists found."
    mode == BrowserMode.LISTS -> "This list has no files."
    mode == BrowserMode.FILES -> "No files."
    else -> "This folder is empty."
}

/**
 * What the Smart layout picks for [entries] (see ListLayout.SMART): the grid where more than half of the files are
 * pictures or videos, which are seen by their pictures; rows for the rest (songs, documents, archives), which are told
 * apart by their names, and for a folder of only folders.
 */
private fun smartLayoutIsGrid(entries: List<StorageNode>): Boolean {
    val files = entries.filter { !it.isDirectory }
    if (files.isEmpty()) return false
    val visual = files.count { node -> node.previewMimeType()?.let { it.startsWith("image/") || it.startsWith("video/") } == true }
    return visual * 2 > files.size
}

/** How wide a tile of the grid is about to be; the row gets as many as fit. */
private val GRID_TILE_WIDTH = 120.dp

/** The room the grid leaves at its end for the scrollbar's dot (see DotScrollbar), which is drawn this far in at most. */
private val GRID_SCROLLBAR_ROOM = 14.dp

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

/** "Download" for the selection bar, as each file on its own or all in one zip, chosen in a menu of the two. */
private fun downloadChoices(enabled: Boolean, onDownload: (DownloadStyle) -> Unit) = SelectionAction(
    "Download",
    Icons.Filled.Download,
    {},
    enabled = enabled,
    choices = listOf(
        SelectionAction("Separately", Icons.Filled.Download, { onDownload(DownloadStyle.SEPARATELY) }),
        SelectionAction("As one ZIP", Icons.Filled.FolderZip, { onDownload(DownloadStyle.ZIP) })
    )
)

/** The same two ways, for a Download of the ⋮ menu at the top of the screen (see ScreenAction.choices). */
private fun downloadScreenChoices(onDownload: (DownloadStyle) -> Unit) = listOf(
    ScreenAction(Icons.Filled.Download, "Separately") { onDownload(DownloadStyle.SEPARATELY) },
    ScreenAction(Icons.Filled.FolderZip, "As one ZIP") { onDownload(DownloadStyle.ZIP) }
)

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
        onCloseList = {},
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

/** A list opened: its title and Back (its downloads are in the ⋮ menu at the top). */
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
