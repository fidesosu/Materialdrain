package tools.senko.materialdrain.browser

import tools.senko.materialdrain.ui.LocalListLook
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import tools.senko.materialdrain.ui.icons.filled.FolderZip
import tools.senko.materialdrain.ui.icons.filled.Link
import tools.senko.materialdrain.ui.icons.filled.Upload
import tools.senko.materialdrain.ui.components.PlaceholderList
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.unit.Dp
import kotlinx.coroutines.delay
import tools.senko.materialdrain.files.FileActionDialogs
import tools.senko.materialdrain.files.FileActionRequest
import tools.senko.materialdrain.files.FileInfoViewModel
import tools.senko.materialdrain.files.DownloadStyle
import tools.senko.materialdrain.files.SelectionAction
import tools.senko.materialdrain.files.key
import tools.senko.materialdrain.files.fileComparator
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
import tools.senko.materialdrain.ui.components.ConfirmDialog
import tools.senko.materialdrain.filesystem.FilesystemUiState
import tools.senko.materialdrain.ui.components.TextInputDialog
import androidx.compose.ui.res.stringResource
import tools.senko.materialdrain.R
import android.content.res.Resources

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
    /** The scroll position of each folder of the filesystem, by its path; without it, [scrollState] for all of them */
    folderScrollState: ((path: String) -> LazyListState)? = null,
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
    // The app's texts, for those its callbacks show
    val res = LocalResources.current
    val fsState by filesystemViewModel.uiState.collectAsState()
    val fsDisplayedChildren by filesystemViewModel.displayedChildren.collectAsState()
    val fsDisplayedPath by filesystemViewModel.displayedPath.collectAsState()
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
    val fsUploadStatus by remember(filesystemViewModel) { derivedStateOf { fsUploadProgress.value?.statusLine(res) } }

    // The sort order: shared by every sortable list in the app (see AppSettings.sortField)
    val sortField by appSettings.sortField.collectAsState()
    val sortAscending by appSettings.sortAscending.collectAsState()
    val foldersFirst by appSettings.foldersFirst.collectAsState()

    // A folder just opened, before its files come back sorted (a moment later, off the main thread): sorted here at once,
    // so the screen goes to it with the path instead of the folder left staying a moment longer. Opening one clears the
    // filter, so there's nothing to filter
    val fsCaughtUp = fsDisplayedPath == fsState.currentPath
    val fsJustOpened = remember(fsCaughtUp, fsState.currentPath, fsState.visibleChildren, sortField, sortAscending, foldersFirst) {
        if (fsCaughtUp) null else fsState.visibleChildren.sortedWith(fileComparator(sortField, sortAscending, foldersFirst))
    }

    // The lists as folders, sorted again only when the lists change
    val listFolders = remember(listState.lists) { listState.lists.sortedBy { it.title.lowercase() }.map { it.asFolder() } }
    // What this mode lists, and how it loads and fails
    val entries: List<StorageNode> = when (mode) {
        BrowserMode.FILESYSTEM -> fsJustOpened ?: fsDisplayedChildren
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
    // What the body shows (a folder, a list, the lists, the files) and how deep it is, for the slide between them: into a
    // folder from the right, back up from the left (see BrowserContent)
    val pageKey = when (mode) {
        BrowserMode.FILESYSTEM -> "folder:" + fsState.currentPath.trim('/')
        BrowserMode.LISTS -> "list:" + openedList?.id.orEmpty()
        BrowserMode.FILES -> "files"
    }
    val pageDepth = when (mode) {
        BrowserMode.FILESYSTEM -> fsState.pathSegments.size
        BrowserMode.LISTS -> if (openedList != null) 1 else 0
        BrowserMode.FILES -> 0
    }
    // Each folder keeps its own scroll position, so going back up is where the folder above was left
    val scrollStateOf: (String) -> LazyListState = { key ->
        if (mode == BrowserMode.FILESYSTEM && folderScrollState != null) folderScrollState(key.removePrefix("folder:")) else scrollState
    }
    // Only the filesystem can be changed here; the files of a list can be removed from it when the list allows
    val canWriteFs = mode == BrowserMode.FILESYSTEM && fsState.canWrite && ProviderCapability.RENAME !in disabledCapabilities
    val canUploadFs = ProviderCapability.UPLOAD !in disabledCapabilities
    val canMkdirFs = ProviderCapability.MKDIR !in disabledCapabilities
    val canDelete = ProviderCapability.DELETE !in disabledCapabilities
    val listCanEdit = openedList?.canEdit == true

    // The circle only when the user pulls to refresh: what's shown is listed again quietly, and a folder or list with
    // nothing to show yet has its placeholder rows (see BrowserContent)
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
        if (!isLoading) userRefreshing = false
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
        if (entries.any { it.key !in before }) scrollStateOf(pageKey).scrollToItem(0)
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
                messages.show(res.getString(R.string.browser_is_being_moved, node.name))
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
            title = stringResource(R.string.browser_new_folder),
            label = stringResource(R.string.browser_folder_name),
            confirmLabel = stringResource(R.string.browser_create),
            onConfirm = {
                filesystemViewModel.createFolder(it)
                showNewFolderDialog = false
            },
            onDismiss = { showNewFolderDialog = false }
        )
    }
    if (showImportDialog) {
        TextInputDialog(
            title = stringResource(R.string.browser_import_files_by_id),
            label = stringResource(R.string.browser_file_ids),
            confirmLabel = stringResource(R.string.browser_import),
            singleLine = false,
            supportingText = stringResource(R.string.browser_separate_ids_with_spaces_or),
            onConfirm = {
                filesystemViewModel.importFilesById(it.split(',', ' ', '\n', '\t'))
                showImportDialog = false
            },
            onDismiss = { showImportDialog = false }
        )
    }
    entryToRename?.let { entry ->
        TextInputDialog(
            title = if (entry.isDirectory) stringResource(R.string.browser_rename_folder) else stringResource(R.string.browser_rename_file),
            label = stringResource(R.string.browser_new_name),
            confirmLabel = stringResource(R.string.browser_rename),
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
            title = stringResource(R.string.browser_delete_items, selectedEntries.size),
            text = (if (folderCount > 0) stringResource(R.string.browser_folders_are_deleted_with_everything) else "") +
                stringResource(R.string.browser_this_can_t_be_undone) +
                (if (selectedEntries.any { it.isSearchIndex() }) "\n\n" + res.getString(SEARCH_INDEX_DELETE_WARNING) else ""),
            confirmLabel = stringResource(R.string.browser_delete),
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
            title = if (entry.isSearchIndex()) stringResource(R.string.browser_delete_the_search_index) else if (entry.isDirectory) stringResource(R.string.browser_delete_folder) else stringResource(R.string.browser_delete_file),
            text = if (entry.isSearchIndex()) {
                res.getString(SEARCH_INDEX_DELETE_WARNING)
            } else if (entry.isDirectory) {
                stringResource(R.string.browser_and_everything_inside_it_will, entry.name)
            } else {
                stringResource(R.string.browser_will_be_deleted_this_cannot, entry.name)
            },
            confirmLabel = if (entry.isSearchIndex()) stringResource(R.string.browser_delete_anyway) else stringResource(R.string.browser_delete),
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
            title = stringResource(R.string.browser_replace_existing_files),
            text = stringResource(R.string.browser_files_already_exist_here_and, names.size) +
                names.take(5).joinToString(", ") + if (names.size > 5) ", …" else "",
            confirmLabel = stringResource(R.string.browser_overwrite),
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
                    placeholder = stringResource(R.string.browser_search_this_folder_and_its),
                    onResultClick = onOpen,
                    onDismiss = onDismissSearchModal,
                    locationOf = { node ->
                        val parent = node.ref.path.trim('/').substringBeforeLast('/', "")
                        parent.removePrefix(root).trim('/').ifEmpty { null }
                    },
                    status = when {
                        tree == null -> null
                        !tree.done -> stringResource(R.string.browser_looking_through_subfolders_so_far, tree.foldersScanned)
                        tree.foldersFailed > 0 -> stringResource(R.string.browser_folders_couldn_t_be_opened, tree.foldersFailed)
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
                placeholder = stringResource(R.string.browser_search_your_files),
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
                placeholder = stringResource(R.string.browser_search, openedList.title),
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
        label = if (allSelected) stringResource(R.string.browser_deselect_all) else stringResource(R.string.browser_select_all),
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
        SelectionAction(stringResource(R.string.browser_add_to_queue), Icons.Filled.QueueMusic, {
            val wasEmpty = player.mediaItemCount == 0
            songs.forEach { player.addToQueue(it, placeholderArt) }
            messages.show(
                when {
                    wasEmpty -> res.getString(R.string.browser_playing, songs.first().title) + if (songs.size > 1) res.getQuantityString(R.plurals.common_and_more, songs.size - 1, songs.size - 1) else ""
                    songs.size == 1 -> res.getString(R.string.browser_added_to_the_queue)
                    else -> res.getString(R.string.browser_added_songs_to_the_queue, songs.size)
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
                add(SelectionAction(stringResource(R.string.browser_download), Icons.Filled.Download, { fileInfoViewModel.initiateDownloadFile(single); exitSelection() }))
            } else {
                add(downloadChoices(res, enabled = selectedEntries.isNotEmpty()) { style ->
                    fileInfoViewModel.downloadItems(selectedEntries, style, single?.name ?: folderName)
                    exitSelection()
                })
            }
            // Then browsed to the folder they go into, in this tab itself (see the move bar below)
            if (fsState.canWrite) add(SelectionAction(stringResource(R.string.browser_move), Icons.AutoMirrored.Filled.DriveFileMove, { filesystemViewModel.startMove(selectedEntries); exitSelection() }, enabled = selectedEntries.isNotEmpty() && !fsState.isModifying))
            if (fsState.canDelete && canDelete) add(SelectionAction(stringResource(R.string.browser_delete), Icons.Filled.Delete, { showBulkDelete = true }, enabled = selectedEntries.isNotEmpty() && !fsState.isModifying, destructive = true))
            addSongsToQueue?.let { add(it) }
            add(selectAll)
        }
        BrowserMode.FILES, BrowserMode.LISTS -> buildList {
            // Each file on its own, or all of them in one archive; one file is only downloaded
            if (files.size <= 1) {
                add(SelectionAction(stringResource(R.string.browser_download), Icons.Filled.Download, { fileInfoViewModel.downloadFilesSequentially(files); exitSelection() }, enabled = files.isNotEmpty()))
            } else {
                add(downloadChoices(res, enabled = true) { style ->
                    if (style == DownloadStyle.ZIP) fileInfoViewModel.downloadFilesAsZip(files, openedList?.title ?: res.getString(R.string.browser_pixeldrain_files))
                    else fileInfoViewModel.downloadFilesSequentially(files)
                    exitSelection()
                })
            }
            if (canDelete) add(SelectionAction(stringResource(R.string.browser_delete), Icons.Filled.Delete, { actionRequest = FileActionRequest.Delete(files) }, enabled = files.isNotEmpty(), destructive = true))
            add(SelectionAction(stringResource(R.string.files_add_to_list), Icons.AutoMirrored.Filled.PlaylistAdd, { actionRequest = FileActionRequest.AddToList(files) }, enabled = files.isNotEmpty()))
            add(SelectionAction(stringResource(R.string.browser_add_to_filesystem), Icons.Filled.FolderCopy, { actionRequest = FileActionRequest.AddToFilesystem(files) }, enabled = files.isNotEmpty()))
            openedList?.takeIf { it.canEdit }?.let { list ->
                // The list's own file count, not the filtered entries (see the single-item action below)
                add(SelectionAction(stringResource(R.string.files_remove_from_this_list), Icons.Filled.Remove, { actionRequest = FileActionRequest.RemoveFromList(files, list, listState.listFiles.size) }, enabled = files.isNotEmpty()))
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
                    if (listFiles.size > 1) stringResource(R.string.browser_download_all) else stringResource(R.string.browser_download),
                    enabled = listFiles.isNotEmpty(),
                    group = ScreenActionGroup.PAGE,
                    choices = if (listFiles.size > 1) {
                        downloadScreenChoices(res) { style ->
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
                    stringResource(R.string.browser_download_this_folder),
                    group = ScreenActionGroup.PAGE,
                    choices = downloadScreenChoices(res) { style -> fileInfoViewModel.downloadItems(listOf(folder), style, folderName) }
                ) {}
            )
        }
        // What a long press on an entry starts, for those who don't know it (not the lists overview, which only opens)
        if (entries.isNotEmpty() && !(mode == BrowserMode.LISTS && openedList == null)) {
            add(ScreenAction(Icons.Filled.CheckBox, stringResource(R.string.browser_select), group = ScreenActionGroup.PAGE) { selecting = true })
        }
        // The Files screen's upload, as the Filesystem's: new files of the host
        if (mode == BrowserMode.FILES && onUploadFiles != null) {
            add(ScreenAction(Icons.Filled.Upload, stringResource(R.string.upload_upload)) { filesUploadLauncher.launch("*/*") })
        }
        if (mode == BrowserMode.FILESYSTEM && fsState.canWrite) {
            if (canUploadFs) add(ScreenAction(Icons.Filled.Upload, stringResource(R.string.upload_upload), enabled = !fsState.isModifying && fsUploadStatus == null) { filesystemUploadLauncher.launch("*/*") })
            if (canMkdirFs) add(ScreenAction(Icons.Filled.CreateNewFolder, stringResource(R.string.browser_new_folder), enabled = !fsState.isModifying) { showNewFolderDialog = true })
            if (fsState.canImport) add(ScreenAction(Icons.Filled.Link, stringResource(R.string.browser_import_files_by_id), enabled = !fsState.isModifying) { showImportDialog = true })
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
            fallbackLayout == ListLayout.SMART -> stringResource(R.string.browser_smart)
            fallbackLayout == ListLayout.GRID -> stringResource(R.string.browser_grid)
            fallbackCompact -> stringResource(R.string.browser_compact_rows)
            else -> stringResource(R.string.browser_list)
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
        inheritedFrom = inheritedPath?.let { path -> if (path.isEmpty()) res.getString(R.string.browser_the_top_folder) else path.substringAfterLast('/') },
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
        // Only a pull: a folder or list loading by itself shows its placeholder rows, or the files it had (see PlaceholderList)
        refreshing = userRefreshing,
        pageKey = pageKey,
        pageDepth = pageDepth,
        scrollStateFor = scrollStateOf,
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
                        messages.show(if (wasEmpty) res.getString(R.string.browser_playing, node.name) else res.getString(R.string.browser_added_to_the_queue))
                    }
                }
            }
        ),
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
            title = if (pendingMove.size == 1) stringResource(R.string.browser_moving, pendingMove.first().name) else stringResource(R.string.browser_moving_items, pendingMove.size),
            destination = folderName.takeIf { canMoveHere },
            unavailable = when {
                insideMoved -> stringResource(R.string.browser_not_into_a_folder_that)
                alreadyHere -> stringResource(R.string.browser_already_in_this_folder_open)
                else -> stringResource(R.string.browser_open_a_folder_that_can)
            },
            confirmLabel = stringResource(R.string.browser_move_here),
            cancelDescription = stringResource(R.string.browser_leave_them_where_they_are),
            onConfirm = { filesystemViewModel.moveHere() },
            onCancel = { filesystemViewModel.cancelMove() }
        )
    }
    val shared = sharedUpload ?: return null
    // Where this screen puts them: the folder that's open, the host's own files; a list takes no files of its own
    val destination = when (mode) {
        BrowserMode.FILESYSTEM -> folderName.takeIf { fsState.canWrite && canUploadFs && !fsState.isModifying && !fsUploadBusy }
        BrowserMode.FILES -> stringResource(R.string.browser_your_files).takeIf { onUploadFiles != null }
        BrowserMode.LISTS -> null
    }
    return DestinationRequest(
        title = if (shared.count == 1) stringResource(R.string.browser_1_shared_file) else stringResource(R.string.browser_shared_files, shared.count),
        destination = destination,
        unavailable = when (mode) {
            BrowserMode.FILESYSTEM -> stringResource(R.string.browser_open_a_folder_that_can)
            BrowserMode.FILES -> stringResource(R.string.browser_this_host_takes_no_uploads)
            BrowserMode.LISTS -> stringResource(R.string.browser_go_to_files_or_a)
        },
        confirmLabel = stringResource(R.string.browser_upload_here),
        cancelDescription = stringResource(R.string.browser_don_t_upload_the_shared),
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

/** "Download" for the selection bar, as each file on its own or all in one zip, chosen in a menu of the two. */
private fun downloadChoices(res: Resources, enabled: Boolean, onDownload: (DownloadStyle) -> Unit) = SelectionAction(
    res.getString(R.string.browser_download),
    Icons.Filled.Download,
    {},
    enabled = enabled,
    choices = listOf(
        SelectionAction(res.getString(R.string.files_separately), Icons.Filled.Download, { onDownload(DownloadStyle.SEPARATELY) }),
        SelectionAction(res.getString(R.string.files_as_one_zip), Icons.Filled.FolderZip, { onDownload(DownloadStyle.ZIP) })
    )
)

/** The same two ways, for a Download of the ⋮ menu at the top of the screen (see ScreenAction.choices). */
private fun downloadScreenChoices(res: Resources, onDownload: (DownloadStyle) -> Unit) = listOf(
    ScreenAction(Icons.Filled.Download, res.getString(R.string.files_separately)) { onDownload(DownloadStyle.SEPARATELY) },
    ScreenAction(Icons.Filled.FolderZip, res.getString(R.string.files_as_one_zip)) { onDownload(DownloadStyle.ZIP) }
)

/** The line under the sort row while the filesystem uploads: which file it's on, of how many. */
private fun FilesystemUploadProgress.statusLine(res: Resources): String = res.getString(R.string.browser_uploading, currentFileName, currentIndex, totalFiles)
