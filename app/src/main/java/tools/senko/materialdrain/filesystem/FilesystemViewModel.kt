package tools.senko.materialdrain.filesystem

import android.app.Application
import android.content.Context
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import tools.senko.materialdrain.auth.SessionManager
import tools.senko.materialdrain.files.fileComparator
import tools.senko.materialdrain.provider.PIXELDRAIN_PROVIDER_ID
import tools.senko.materialdrain.provider.ProviderConfigStore
import tools.senko.materialdrain.provider.ProviderRegistry
import tools.senko.materialdrain.provider.api.ApiResponse
import tools.senko.materialdrain.provider.forDisplay
import tools.senko.materialdrain.provider.api.BrowseOps
import tools.senko.materialdrain.provider.api.ProviderError
import tools.senko.materialdrain.provider.api.ProviderKind
import tools.senko.materialdrain.provider.api.ProviderLog
import tools.senko.materialdrain.provider.api.StorageNode
import tools.senko.materialdrain.provider.api.StorageProvider
import tools.senko.materialdrain.settings.AppSettings
import tools.senko.materialdrain.settings.isSearchIndex
import tools.senko.materialdrain.transfer.TransferInfo
import tools.senko.materialdrain.transfer.TransferKind
import tools.senko.materialdrain.transfer.TransferOutcome
import tools.senko.materialdrain.transfer.TransferRegistry
import tools.senko.materialdrain.transfer.TransferSpeedTracker
import tools.senko.materialdrain.transfer.estimateEtaSeconds
import tools.senko.materialdrain.util.readContentUriInfo

private const val TAG_FS_VM = "FilesystemViewModel"
/** How many folders are listed at once while indexing the folder tree: quick, without flooding the host. */
private const val TREE_INDEX_PARALLEL_LISTINGS = 4
/** How often the folder tree index shows what it has found so far while it's still going. */
private const val TREE_INDEX_PUBLISH_MS = 250L
const val FILESYSTEM_ROOT_PATH = "me"

data class FilesystemUiState(
    val hostName: String = "",
    val isLoading: Boolean = false,
    val currentPath: String = "",
    val pathSegments: List<PathSegment> = emptyList(), // Name and full path for breadcrumbs
    val children: List<StorageNode> = emptyList(),
    val canWrite: Boolean = false,
    val canDelete: Boolean = false,
    val canImport: Boolean = false,
    val errorMessage: String? = null,
    val apiKeyMissingError: Boolean = false,
    val apiKey: String = "", // Exposed API Key, only the built-in Pixeldrain needs it

    // Modifications (create folder, rename, move, delete, import)
    val isModifying: Boolean = false,
    val operationMessage: String? = null,
    val operationError: String? = null,

    // Uploads into the current directory
    val uploadProgress: FilesystemUploadProgress? = null,
    val pendingUpload: PendingFilesystemUpload? = null, // Waiting for the user to confirm overwriting existing files

    // The pixeldrain search index is useless to nearly everyone, hidden unless the user turns that off in the settings
    val hideSearchIndex: Boolean = true,

    // Filtering state; reset whenever the folder changes
    val filterQuery: String = ""
) {
    /**
     * [children] without the entries which are hidden by the settings. Computed once per state, so the screen gets the same
     * list between recompositions (a new list each time would restart everything which depends on it).
     */
    val visibleChildren: List<StorageNode> by lazy {
        if (hideSearchIndex) children.filterNot { it.isSearchIndex() } else children
    }
}

/**
 * Everything found so far under [root] (the folder the search was opened in) and all its subfolders, for the search to
 * match against. Filled in level by level while [done] is false: the folders closest to [root] come first.
 */
data class FolderTreeIndex(
    val root: String,
    val nodes: List<StorageNode> = emptyList(),
    val foldersScanned: Int = 0,
    val foldersFailed: Int = 0,
    val done: Boolean = false
)

data class PathSegment(
    val name: String,
    val fullPath: String
)

/** @param lastModifiedMillis when the file was last changed on the device, for uploading in that order */
data class FilesystemUploadItem(val uri: Uri, val name: String, val sizeBytes: Long?, val lastModifiedMillis: Long? = null)

data class PendingFilesystemUpload(val items: List<FilesystemUploadItem>, val conflictingNames: List<String>)

data class FilesystemUploadProgress(
    val currentFileName: String,
    val currentIndex: Int,
    val totalFiles: Int,
    val uploadedBytes: Long,
    val totalBytes: Long?,
    val bytesPerSecond: Long = 0L,
    val etaSeconds: Long? = null
)

/**
 * The universal file browser: folders, files, upload, rename, move, delete, for whichever provider is
 * active. Everything goes through [StorageProvider.browse], so a host with the BROWSE capability shows here
 * with the actions its capabilities and permissions allow.
 */
class FilesystemViewModel(
    private val application: Application,
    private val registry: ProviderRegistry,
    private val configStore: ProviderConfigStore,
    private val sessionManager: SessionManager,
    private val transfers: TransferRegistry,
    private val appSettings: AppSettings
) : ViewModel() {

    private val _uiState = MutableStateFlow(FilesystemUiState())
    val uiState: StateFlow<FilesystemUiState> = _uiState.asStateFlow()

    private val _displayedChildren = MutableStateFlow<List<StorageNode>>(emptyList())
    /** [FilesystemUiState.visibleChildren], filtered and sorted by the shared sort order. Folders always come first. */
    val displayedChildren: StateFlow<List<StorageNode>> = _displayedChildren.asStateFlow()

    private var uploadJob: Job? = null

    // --- Searching the folder tree ---

    private val _treeIndex = MutableStateFlow<FolderTreeIndex?>(null)
    /** What [indexFolderTree] has found; null when it hasn't run since the last change. */
    val treeIndex: StateFlow<FolderTreeIndex?> = _treeIndex.asStateFlow()
    private var treeIndexJob: Job? = null
    // The listings of the folders seen, by path: a search opened again (or from a folder already crawled) reuses them
    // instead of listing everything again. Of the active host only, and dropped whenever something may have changed
    private val folderCache = ConcurrentHashMap<String, List<StorageNode>>()
    private val speedTracker = TransferSpeedTracker()
    private var nextTransferNumber = 0

    init {
        viewModelScope.launch {
            appSettings.hideSearchIndex.collect { hide -> _uiState.update { it.copy(hideSearchIndex = hide) } }
        }
        Log.d(TAG_FS_VM, "ViewModel init. Loading the active provider and its root.")
        loadActiveProviderAndRoot()
        viewModelScope.launch {
            // Choosing another host as the active one re-roots the screen on it
            configStore.changes.drop(1).collect { loadActiveProviderAndRoot() }
        }

        // Recomputes displayedChildren whenever the folder's contents, the filter, or the shared sorting changes
        viewModelScope.launch {
            combine(
                uiState.map { it.visibleChildren },
                uiState.map { it.filterQuery },
                appSettings.sortField,
                appSettings.sortAscending
            ) { children, filterQuery, sortField, sortAscending ->
                withContext(Dispatchers.Default) {
                    children
                        .filter { it.name.contains(filterQuery, ignoreCase = true) }
                        .sortedWith(fileComparator(sortField, sortAscending, directoriesFirst = true))
                }
            }.collect { sortedAndFiltered -> _displayedChildren.value = sortedAndFiltered }
        }
    }

    fun onFilterQueryChanged(query: String) {
        _uiState.update { it.copy(filterQuery = query) }
    }

    private fun activeProvider(): StorageProvider = registry.resolve(configStore.activeProviderId.value)

    /** The root of the active provider, as the provider declares it (Pixeldrain's "me" bucket, a config's browse_root). */
    private fun rootPath(provider: StorageProvider): String = provider.rootPath

    private fun loadActiveProviderAndRoot() {
        forgetFolderTree()
        val provider = activeProvider()
        val apiKey = if (provider.kind == ProviderKind.PIXELDRAIN) sessionManager.currentApiKey() else ""
        _uiState.update {
            it.copy(
                hostName = provider.displayName,
                apiKey = apiKey,
                apiKeyMissingError = provider.kind == ProviderKind.PIXELDRAIN && apiKey.isBlank(),
                errorMessage = if (provider.kind == ProviderKind.PIXELDRAIN && apiKey.isBlank()) API_KEY_MISSING else null,
                canWrite = false,
                canDelete = false
            )
        }
        if (provider.browse == null) {
            _uiState.update { it.copy(isLoading = false, errorMessage = "This host can't browse files.") }
        } else if (provider.kind != ProviderKind.PIXELDRAIN || sessionManager.currentApiKey().isNotBlank()) {
            // The folder last open on this host, or its root when there is none (or it can't be listed any more)
            val saved = appSettings.filesystemPath(configStore.activeProviderId.value)
            fetchPathContent(saved ?: rootPath(provider), fallbackToRoot = saved != null)
        }
    }

    fun refreshCurrentPath() {
        forgetFolderTree()
        val provider = activeProvider()
        if (provider.kind == ProviderKind.PIXELDRAIN && sessionManager.currentApiKey().isBlank()) {
            _uiState.update { it.copy(apiKeyMissingError = true, errorMessage = API_KEY_MISSING, isLoading = false, children = emptyList()) }
            return
        }
        fetchPathContent(uiState.value.currentPath)
    }

    /** Called after the API key changed (settings saved, logged in or out). */
    fun updateApiKey() {
        loadActiveProviderAndRoot()
    }

    private fun normalizePath(path: String, root: String): String = path.trim('/').ifEmpty { root }

    private fun generatePathSegments(fullPath: String, provider: StorageProvider): List<PathSegment> {
        var built = ""
        val segments = fullPath.split('/').filter { it.isNotEmpty() }.map { name ->
            built = if (built.isEmpty()) name else "$built/$name"
            PathSegment(name, built)
        }
        // A host rooted at its very top ("") has no folder name for the root, so it gets its own crumb to go back to
        return if (rootPath(provider).isEmpty()) listOf(PathSegment(provider.rootName, "")) + segments else segments
    }

    fun fetchPathContent(path: String, fallbackToRoot: Boolean = false) {
        val provider = activeProvider()
        val browse = provider.browse ?: run {
            _uiState.update { it.copy(isLoading = false, errorMessage = "This host can't browse files.") }
            return
        }
        val normalized = normalizePath(path, rootPath(provider))
        ProviderLog.d("Filesystem", "listing '$normalized' on '${provider.displayName}' (${provider.kind})")
        _uiState.update { it.copy(isLoading = true, errorMessage = null, apiKeyMissingError = false) }
        viewModelScope.launch {
            when (val response = browse.list(normalized)) {
                is ApiResponse.Success -> {
                    val sortedChildren = response.data.children.sortedWith(
                        compareBy<StorageNode> { !it.isDirectory }
                            .thenBy { it.name.lowercase() }
                    )
                    folderCache[normalized] = response.data.children
                    appSettings.setFilesystemPath(configStore.activeProviderId.value, normalized)
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            currentPath = normalized,
                            pathSegments = generatePathSegments(normalized, provider),
                            children = sortedChildren,
                            canWrite = response.data.canWrite,
                            canDelete = response.data.canDelete,
                            canImport = provider.kind == ProviderKind.PIXELDRAIN,
                            errorMessage = null,
                            // A filter from the folder just left doesn't carry over into this one
                            filterQuery = ""
                        )
                    }
                }
                is ApiResponse.Error -> {
                    ProviderLog.e("Filesystem", "listing '$normalized' failed (${response.error.code}): ${response.error.message}")
                    if (fallbackToRoot) {
                        fetchPathContent(rootPath(provider))
                        return@launch
                    }
                    _uiState.update { it.copy(isLoading = false, errorMessage = response.error.forDisplay()) }
                }
            }
        }
    }

    /** The preview image of a node, from the active provider; null when it has none. */
    fun thumbnailFor(node: StorageNode): String? = activeProvider().thumbnailUrl(node)

    fun navigateToChild(node: StorageNode) {
        if (node.isDirectory) fetchPathContent(node.ref.path)
    }

    fun navigateToPathSegment(segment: PathSegment) {
        fetchPathContent(segment.fullPath)
    }

    /** The folder above the current one, or null when the current one is the top of the host. */
    private fun parentPath(): String? {
        val root = rootPath(activeProvider())
        val current = normalizePath(uiState.value.currentPath, root)
        if (current == root) return null
        // A folder right under a "" root has "" as its parent: that's the root, not "no parent"
        return current.substringBeforeLast('/', "").ifEmpty { root }.takeIf { it != current }
    }

    /** Whether there is a folder above the current one (false at the top of the host). */
    fun canNavigateToParent(): Boolean = parentPath() != null

    fun navigateToParentPath(): Boolean {
        val parent = parentPath()
        if (parent == null) {
            Log.d(TAG_FS_VM, "Already at root. Cannot navigate to parent.")
            return false
        }
        fetchPathContent(parent)
        return true
    }

    /**
     * Lists the current folder and every folder under it, level by level, into [treeIndex], so a search can find what's
     * anywhere below. Folders already listed are taken from the cache; a few are listed at a time. Started again for
     * another folder, or after a change, it starts over; for the same folder it carries on from what's known.
     */
    fun indexFolderTree() {
        val browse = activeProvider().browse ?: return
        val root = currentDir()
        val known = _treeIndex.value
        if (known != null && known.root == root && (known.done || treeIndexJob?.isActive == true)) return
        treeIndexJob?.cancel()
        _treeIndex.value = FolderTreeIndex(root)
        treeIndexJob = viewModelScope.launch {
            val hideSearchIndex = _uiState.value.hideSearchIndex
            val permits = Semaphore(TREE_INDEX_PARALLEL_LISTINGS)
            val lock = Mutex()
            val nodes = ArrayList<StorageNode>()
            val visited = HashSet<String>().apply { add(root) }
            var scanned = 0
            var failed = 0
            var lastPublished = 0L
            fun publish(done: Boolean) {
                _treeIndex.value = FolderTreeIndex(root, nodes.toList(), scanned, failed, done)
            }
            var level = listOf(root)
            while (level.isNotEmpty()) {
                val next = ArrayList<String>()
                coroutineScope {
                    level.forEach { folder ->
                        launch {
                            val children = folderCache[folder] ?: permits.withPermit {
                                when (val response = browse.list(folder)) {
                                    is ApiResponse.Success -> response.data.children.also { folderCache[folder] = it }
                                    is ApiResponse.Error -> null
                                }
                            }
                            lock.withLock {
                                if (children == null) {
                                    failed++
                                } else {
                                    scanned++
                                    children.forEach { child ->
                                        if (hideSearchIndex && child.isSearchIndex()) return@forEach
                                        nodes += child
                                        // A folder reachable twice (a link back up the tree) is only listed once
                                        if (child.isDirectory && visited.add(child.ref.path)) next += child.ref.path
                                    }
                                }
                                // Often enough to feel live, not so often that copying a big list each time adds up
                                val now = SystemClock.uptimeMillis()
                                if (now - lastPublished >= TREE_INDEX_PUBLISH_MS) {
                                    lastPublished = now
                                    publish(done = false)
                                }
                            }
                        }
                    }
                }
                level = next
            }
            lock.withLock { publish(done = true) }
        }
    }

    /** Stops indexing the folder tree and forgets what was found, for after something may have changed on the host. */
    private fun forgetFolderTree() {
        treeIndexJob?.cancel()
        treeIndexJob = null
        _treeIndex.value = null
        folderCache.clear()
    }

    /** Stops indexing, keeping what was found and listed so far for the next search. */
    fun pauseFolderTreeIndex() {
        if (treeIndexJob?.isActive == true) {
            treeIndexJob?.cancel()
            // Not done: the next search of this folder starts over, quickly, from the listings cached so far
            _treeIndex.value = null
        }
    }

    // --- Modifications ---

    fun createFolder(name: String) {
        val folderName = name.trim()
        if (!isValidNodeName(folderName)) {
            reportOperationError("A folder name can't be empty or contain a slash.")
            return
        }
        runModification { browse -> browse?.createDirectory(joinPath(currentDir(), folderName), makeParents = false) ?: notBrowsable() }
    }

    fun renameEntry(entry: StorageNode, newName: String) {
        val name = newName.trim()
        if (!isValidNodeName(name)) {
            reportOperationError("A name can't be empty or contain a slash.")
            return
        }
        if (name == entry.name) return
        val parent = entry.ref.path.trim('/').substringBeforeLast('/', "")
        runModification { browse -> browse?.rename(entry.ref.path, joinPath(parent, name), makeParents = false)?.discard() ?: notBrowsable() }
    }

    fun moveEntry(entry: StorageNode, destinationDirectory: String) {
        val destination = normalizePath(destinationDirectory, rootPath(activeProvider()))
        val source = entry.ref.path.trim('/')
        if (destination == source || destination.startsWith("$source/")) {
            reportOperationError("A folder can't be moved into itself.")
            return
        }
        if (destination == source.substringBeforeLast('/', "")) return // already there
        runModification { browse -> browse?.rename(entry.ref.path, joinPath(destination, entry.name), makeParents = false)?.discard() ?: notBrowsable() }
    }

    fun deleteEntry(entry: StorageNode) {
        // Deleting a folder always removes what is inside it, the UI asks for confirmation first
        runModification { browse -> browse?.delete(entry.ref.path, recursive = entry.isDirectory) ?: notBrowsable() }
    }

    /** There is no endpoint to delete several nodes everywhere, so this deletes them one by one. */
    fun deleteEntries(entries: List<StorageNode>) {
        runBulkModification(entries, "deleted") { browse, entry -> browse.delete(entry.ref.path, recursive = entry.isDirectory) }
    }

    fun moveEntries(entries: List<StorageNode>, destinationDirectory: String) {
        val destination = normalizePath(destinationDirectory, rootPath(activeProvider()))
        runBulkModification(entries, "moved") { browse, entry ->
            val source = entry.ref.path.trim('/')
            when {
                destination == source || destination.startsWith("$source/") ->
                    ApiResponse.Error(ProviderError("circular_dependency", "A folder can't be moved into itself."))
                destination == source.substringBeforeLast('/', "") ->
                    ApiResponse.Success(Unit) // already in that folder
                else -> browse.rename(entry.ref.path, joinPath(destination, entry.name), makeParents = false).discard()
            }
        }
    }

    private fun runBulkModification(
        entries: List<StorageNode>,
        pastTense: String,
        operation: suspend (browse: BrowseOps, entry: StorageNode) -> ApiResponse<Unit>
    ) {
        if (entries.isEmpty()) return
        val browse = activeProvider().browse ?: return
        if (_uiState.value.isModifying) return
        val dir = currentDir()
        _uiState.update { it.copy(isModifying = true, operationMessage = null, operationError = null) }
        viewModelScope.launch {
            var succeeded = 0
            val failures = mutableListOf<String>()
            for (entry in entries) {
                when (val response = modifying { operation(browse, entry) }) {
                    is ApiResponse.Success -> succeeded++
                    is ApiResponse.Error -> failures.add("${entry.name}: ${describeError(response.error)}")
                }
            }
            _uiState.update {
                it.copy(
                    isModifying = false,
                    operationMessage = if (succeeded > 0) (if (succeeded == 1) "1 item $pastTense." else "$succeeded items $pastTense.") else null,
                    operationError = failures.takeIf { f -> f.isNotEmpty() }
                        ?.let { f -> f.take(3).joinToString("\n") + if (f.size > 3) "\nand ${f.size - 3} more failed" else "" }
                )
            }
            forgetFolderTree()
            fetchPathContent(dir)
        }
    }

    /** The folders inside [path], for the folder picker. */
    /** The top of the active host's folders, as the folder picker starts and stops at it (see StorageProvider.rootPath). */
    val browseRootPath: String get() = rootPath(activeProvider())

    /** What the folder picker's path calls [browseRootPath]: its own name, or the host's name for its top (an SMB share's). */
    val browseRootName: String
        get() = activeProvider().let { provider -> rootPath(provider).substringAfterLast('/').ifEmpty { provider.rootName } }

    suspend fun listSubdirectories(path: String): ApiResponse<List<StorageNode>> {
        val provider = activeProvider()
        val browse = provider.browse ?: return ApiResponse.Error(ProviderError("not_browsable", "This host can't browse files."))
        return when (val response = browse.list(normalizePath(path, rootPath(provider)))) {
            is ApiResponse.Success -> ApiResponse.Success(
                response.data.children.filter { it.isDirectory }.sortedBy { it.name.lowercase() }
            )
            is ApiResponse.Error -> ApiResponse.Error(response.error)
        }
    }

    /** Pixeldrain's import from the file list into a folder: the one action no other host has, so it goes through the built-in API. */
    fun importFilesById(fileIds: List<String>) {
        val ids = fileIds.map { it.trim() }.filter { it.isNotEmpty() }
        if (ids.isEmpty()) {
            reportOperationError("Enter at least one file ID.")
            return
        }
        val dir = currentDir()
        runModification { browse -> browse?.importFiles(dir, ids) ?: notBrowsable() }
    }

    private fun currentDir(): String = normalizePath(_uiState.value.currentPath, rootPath(activeProvider()))

    private fun joinPath(dir: String, name: String) = if (dir.isEmpty()) name else "$dir/$name"

    private fun isValidNodeName(name: String) = name.isNotEmpty() && !name.contains('/') && name != "." && name != ".."

    private fun runModification(operation: suspend (browse: BrowseOps?) -> ApiResponse<Unit>) {
        val browse = activeProvider().browse
        if (_uiState.value.isModifying) return
        val dir = currentDir()
        _uiState.update { it.copy(isModifying = true, operationMessage = null, operationError = null) }
        viewModelScope.launch {
            when (val response = modifying { operation(browse) }) {
                is ApiResponse.Success -> _uiState.update { it.copy(isModifying = false, operationMessage = "Done.") }
                is ApiResponse.Error -> _uiState.update { it.copy(isModifying = false, operationError = describeError(response.error)) }
            }
            forgetFolderTree()
            fetchPathContent(dir)
        }
    }

    /**
     * Runs one change on the host (delete, rename, move, new folder). Never throws but for a cancel: a failure of the host
     * or the network is an error result, so it's reported, and in a batch the next items still go, instead of the app
     * crashing on it.
     */
    private suspend fun modifying(operation: suspend () -> ApiResponse<Unit>): ApiResponse<Unit> =
        try {
            operation()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            currentCoroutineContext().ensureActive()
            ProviderLog.e("Filesystem", "a change failed: ${e.message}", e)
            ApiResponse.Error(ProviderError("network_error", e.message ?: "The host couldn't be reached."))
        }

    private fun notBrowsable(): ApiResponse<Unit> = ApiResponse.Error(ProviderError("not_browsable", "This host can't browse files."))

    private fun <T> ApiResponse<T>.discard(): ApiResponse<Unit> = when (this) {
        is ApiResponse.Success -> ApiResponse.Success(Unit)
        is ApiResponse.Error -> this
    }

    private fun reportOperationError(message: String) {
        _uiState.update { it.copy(operationError = message) }
    }

    private fun describeError(error: ProviderError): String {
        val hint = when (error.code) {
            "node_already_exists" -> "An item with this name already exists."
            "directory_not_empty" -> "The folder is not empty."
            "permission_denied" -> "You don't have permission to do this here."
            "path_not_found" -> "The path does not exist (anymore)."
            "cannot_modify_root_dir" -> "The root folder can't be modified."
            "circular_dependency" -> "A folder can't be moved into itself."
            "authentication_required" -> "Authentication is required. Check your login or API key in Settings."
            "user_out_of_space" -> "Your account has run out of storage space."
            "list_file_not_found" -> "One of the file IDs does not exist."
            else -> null
        }
        return hint ?: error.message.ifBlank { "Unknown error." }
    }

    fun clearOperationMessage() {
        _uiState.update { it.copy(operationMessage = null) }
    }

    fun clearOperationError() {
        _uiState.update { it.copy(operationError = null) }
    }

    // --- Uploading ---

    fun onFilesPickedForUpload(uris: List<Uri>, context: Context) {
        if (uris.isEmpty() || _uiState.value.uploadProgress != null) return
        viewModelScope.launch {
            val items = withContext(Dispatchers.IO) {
                uris.map { uri ->
                    val info = readContentUriInfo(context, uri)
                    FilesystemUploadItem(
                        uri = uri,
                        name = (info.displayName ?: uri.lastPathSegment ?: "upload_${System.currentTimeMillis()}").replace('/', '_'),
                        sizeBytes = info.sizeBytes,
                        lastModifiedMillis = info.lastModifiedMillis
                    )
                }.let { picked ->
                    // They go one after another anyway: in the order they were last changed, oldest first, when that's
                    // set (files whose date Android doesn't know go last, as they were picked)
                    if (appSettings.uploadInModifiedOrder.value) picked.sortedBy { it.lastModifiedMillis ?: Long.MAX_VALUE } else picked
                }
            }
            val existingNames = _uiState.value.children.map { it.name }.toSet()
            val conflicts = items.map { it.name }.filter { it in existingNames }
            if (conflicts.isEmpty()) {
                startUpload(items)
            } else {
                _uiState.update { it.copy(pendingUpload = PendingFilesystemUpload(items, conflicts)) }
            }
        }
    }

    fun confirmPendingUpload() {
        val pending = _uiState.value.pendingUpload ?: return
        _uiState.update { it.copy(pendingUpload = null) }
        startUpload(pending.items)
    }

    fun cancelPendingUpload() {
        _uiState.update { it.copy(pendingUpload = null) }
    }

    fun cancelUpload() {
        uploadJob?.cancel()
    }

    private fun startUpload(items: List<FilesystemUploadItem>) {
        val browse = activeProvider().browse ?: return
        val dir = currentDir()
        val totalBytes = items.sumOf { it.sizeBytes ?: 0L }.takeIf { it > 0 && items.all { item -> item.sizeBytes != null } }
        speedTracker.reset()
        _uiState.update {
            it.copy(
                operationMessage = null,
                operationError = null,
                uploadProgress = FilesystemUploadProgress(items.first().name, 1, items.size, 0L, totalBytes)
            )
        }

        val transferId = "filesystem-upload-${nextTransferNumber++}"
        transfers.start(
            TransferInfo(transferId, TransferKind.UPLOAD, if (items.size == 1) items.first().name else "${items.size} files", totalBytes = totalBytes),
            onCancel = { cancelUpload() }
        )

        uploadJob = viewModelScope.launch {
            var completedBytes = 0L
            val failures = mutableListOf<String>()
            var uploaded = 0
            var outcome = TransferOutcome.FAILED
            var outcomeMessage: String? = null
            val job = coroutineContext[Job]
            try {
                items.forEachIndexed { index, item ->
                    val response = uploadOne(browse, joinPath(dir, item.name), item.uri) { sent ->
                        // Called from inside the host's sending loop: a cancelled upload stops there
                        if (job?.isActive == false) throw kotlinx.coroutines.CancellationException("The upload was cancelled")
                        val overall = completedBytes + sent
                        val speed = speedTracker.update(overall)
                        transfers.progress(transferId, overall, totalBytes, speed, estimateEtaSeconds(totalBytes, overall, speed))
                        _uiState.update {
                            it.copy(
                                uploadProgress = FilesystemUploadProgress(
                                    currentFileName = item.name,
                                    currentIndex = index + 1,
                                    totalFiles = items.size,
                                    uploadedBytes = overall,
                                    totalBytes = totalBytes,
                                    bytesPerSecond = speed,
                                    etaSeconds = estimateEtaSeconds(totalBytes, overall, speed)
                                )
                            )
                        }
                    }
                    completedBytes += item.sizeBytes ?: 0L
                    // A host which reports the connection cut under a cancelled upload as an error: that's the cancel
                    ensureActive()
                    when (response) {
                        is ApiResponse.Success -> uploaded++
                        is ApiResponse.Error -> failures.add("${item.name}: ${describeError(response.error)}")
                    }
                }
                if (failures.isEmpty()) {
                    outcome = TransferOutcome.COMPLETED
                    outcomeMessage = if (uploaded == 1) "1 file was uploaded to $dir." else "$uploaded files were uploaded to $dir."
                } else {
                    outcomeMessage = "${failures.size} of ${items.size} uploads failed: ${failures.first()}"
                }
                _uiState.update {
                    it.copy(
                        uploadProgress = null,
                        operationMessage = if (uploaded > 0) (if (uploaded == 1) "1 file uploaded." else "$uploaded files uploaded.") else null,
                        operationError = failures.takeIf { f -> f.isNotEmpty() }?.joinToString("\n")
                    )
                }
            } catch (e: CancellationException) {
                outcome = TransferOutcome.CANCELLED
                _uiState.update { it.copy(uploadProgress = null, operationMessage = if (uploaded > 0) "Upload cancelled, $uploaded file(s) were uploaded." else "Upload cancelled.") }
                throw e
            } finally {
                transfers.finish(transferId, outcome, outcomeMessage)
                forgetFolderTree()
                if (currentDir() == dir) fetchPathContent(dir)
            }
        }
    }

    /**
     * Uploads one file to [path]. Never throws but for a cancel: a failure of the host or the network is an error result,
     * like any the host reports itself, so it fails that file and the next ones still go.
     */
    private suspend fun uploadOne(browse: BrowseOps, path: String, uri: Uri, onProgress: (sent: Long) -> Unit): ApiResponse<StorageNode> =
        try {
            browse.upload(path, uri, application, makeParents = false) { sent, _ -> onProgress(sent) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            currentCoroutineContext().ensureActive()
            ProviderLog.e("Filesystem", "uploading to '$path' failed: ${e.message}", e)
            ApiResponse.Error(ProviderError("upload_failed", e.message ?: "The upload failed."))
        }

    private companion object {
        const val API_KEY_MISSING = "API Key is missing. Please set it in Settings to browse the filesystem."
    }
}
