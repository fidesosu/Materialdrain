package tools.senko.materialdrain.filesystem

import android.app.Application
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tools.senko.materialdrain.api.ApiResponse
import tools.senko.materialdrain.api.FileUploadResponse
import tools.senko.materialdrain.api.FilesystemEntry
import tools.senko.materialdrain.api.FilesystemPermissions
import tools.senko.materialdrain.api.PixeldrainFilesystemApi
import tools.senko.materialdrain.auth.SessionManager
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
const val FILESYSTEM_ROOT_PATH = "me"

data class FilesystemUiState(
    val isLoading: Boolean = false,
    val currentPath: String = FILESYSTEM_ROOT_PATH,
    val pathSegments: List<PathSegment> = listOf(PathSegment(FILESYSTEM_ROOT_PATH, FILESYSTEM_ROOT_PATH)), // Name and full path for breadcrumbs
    val children: List<FilesystemEntry> = emptyList(),
    val permissions: FilesystemPermissions? = null, // Permissions on the current directory
    val errorMessage: String? = null,
    val apiKeyMissingError: Boolean = false,
    val apiKey: String = "", // Exposed API Key

    // Modifications (create folder, rename, move, delete, import)
    val isModifying: Boolean = false,
    val operationMessage: String? = null,
    val operationError: String? = null,

    // Uploads into the current directory
    val uploadProgress: FilesystemUploadProgress? = null,
    val pendingUpload: PendingFilesystemUpload? = null, // Waiting for the user to confirm overwriting existing files

    // The pixeldrain search index is useless to nearly everyone, hidden unless the user turns that off in the settings
    val hideSearchIndex: Boolean = true
) {
    /** [children] without the entries which are hidden by the settings. */
    val visibleChildren: List<FilesystemEntry>
        get() = if (hideSearchIndex) children.filterNot { it.isSearchIndex() } else children

    val canWrite: Boolean get() = permissions?.write == true
    val canDelete: Boolean get() = permissions?.delete == true
}

data class PathSegment(
    val name: String,
    val fullPath: String
)

data class FilesystemUploadItem(val uri: Uri, val name: String, val sizeBytes: Long?)

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

/** "/me/photos/" -> "me/photos"; blank -> "me". */
private fun normalizeFsPath(path: String): String = path.trim().trim('/').ifEmpty { FILESYSTEM_ROOT_PATH }

class FilesystemViewModel(
    private val application: Application,
    private val filesystemApi: PixeldrainFilesystemApi,
    private val sessionManager: SessionManager,
    private val transfers: TransferRegistry,
    private val appSettings: AppSettings
) : ViewModel() {

    private val _uiState = MutableStateFlow(FilesystemUiState())
    val uiState: StateFlow<FilesystemUiState> = _uiState.asStateFlow()

    private var internalApiKey: String = ""
    private var uploadJob: Job? = null
    private val speedTracker = TransferSpeedTracker()
    private var nextTransferNumber = 0

    init {
        Log.d(TAG_FS_VM, "ViewModel init. Loading API Key and initial path.")
        viewModelScope.launch {
            appSettings.hideSearchIndex.collect { hide -> _uiState.update { it.copy(hideSearchIndex = hide) } }
        }
        loadApiKeyAndFetchCurrentPath()
    }

    private fun loadApiKeyAndFetchCurrentPath() {
        internalApiKey = sessionManager.currentApiKey()
        Log.d(TAG_FS_VM, "API Key loaded: '${if (internalApiKey.isNotBlank()) "PRESENT" else "MISSING"}'")

        if (internalApiKey.isBlank()) {
            _uiState.update {
                it.copy(
                    apiKey = "",
                    apiKeyMissingError = true,
                    errorMessage = "API Key is missing. Please set it in Settings to browse the filesystem.",
                    isLoading = false
                )
            }
        } else {
            _uiState.update {
                it.copy(
                    apiKey = internalApiKey,
                    apiKeyMissingError = false,
                    errorMessage = null
                )
            }
            fetchPathContent(uiState.value.currentPath)
        }
    }

    fun refreshCurrentPath() {
        if (internalApiKey.isBlank()) {
            _uiState.update {
                it.copy(
                    apiKeyMissingError = true,
                    errorMessage = "API Key is missing. Please set it in Settings to browse the filesystem.",
                    isLoading = false,
                    children = emptyList()
                )
            }
            return
        }
        _uiState.update { it.copy(apiKey = internalApiKey, apiKeyMissingError = false, errorMessage = null) }
        fetchPathContent(uiState.value.currentPath)
    }

    /** Called after the API key changed (settings saved, logged in or out). */
    fun updateApiKey() {
        val oldApiKey = internalApiKey
        internalApiKey = sessionManager.currentApiKey()
        Log.d(TAG_FS_VM, "API Key updated. New Key: '${if (internalApiKey.isNotBlank()) "PRESENT" else "MISSING"}'")

        if (internalApiKey.isNotBlank()) {
            _uiState.update {
                it.copy(
                    apiKey = internalApiKey,
                    apiKeyMissingError = false,
                    errorMessage = if (oldApiKey.isBlank()) null else it.errorMessage
                )
            }
            if (internalApiKey != oldApiKey) {
                // Another account may have a different directory structure
                _uiState.update { it.copy(children = emptyList(), permissions = null) }
                fetchPathContent(FILESYSTEM_ROOT_PATH)
            }
        } else {
            _uiState.update {
                it.copy(
                    apiKey = "",
                    apiKeyMissingError = true,
                    errorMessage = "API Key is missing. Please set it in Settings.",
                    isLoading = false,
                    children = emptyList(),
                    permissions = null
                )
            }
        }
    }

    private fun generatePathSegments(fullPath: String): List<PathSegment> {
        var built = ""
        return normalizeFsPath(fullPath).split('/').map { name ->
            built = if (built.isEmpty()) name else "$built/$name"
            PathSegment(name, built)
        }
    }

    fun fetchPathContent(path: String) {
        val currentApiKey = uiState.value.apiKey
        val normalizedPath = normalizeFsPath(path)
        if (currentApiKey.isBlank()) {
            Log.w(TAG_FS_VM, "fetchPathContent called with blank API key for path: $normalizedPath")
            _uiState.update {
                it.copy(
                    apiKeyMissingError = true,
                    errorMessage = "API Key is missing. Please set it in Settings.",
                    isLoading = false,
                    children = emptyList()
                )
            }
            return
        }

        _uiState.update { it.copy(isLoading = true, errorMessage = null, apiKeyMissingError = false) }
        viewModelScope.launch {
            Log.d(TAG_FS_VM, "Fetching content for path: $normalizedPath with API key.")
            when (val response = filesystemApi.getFilesystemPath(currentApiKey, normalizedPath)) {
                is ApiResponse.Success -> {
                    val sortedChildren = response.data.children.sortedWith(
                        compareBy<FilesystemEntry> { it.type != "dir" }
                        .thenByDescending { it.modified }
                        .thenBy { it.name.lowercase() }
                    )
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            currentPath = normalizedPath,
                            pathSegments = generatePathSegments(normalizedPath),
                            children = sortedChildren,
                            permissions = response.data.permissions,
                            errorMessage = null
                        )
                    }
                    Log.d(TAG_FS_VM, "Successfully fetched ${response.data.children.size} children for path: $normalizedPath")
                }
                is ApiResponse.Error -> {
                    Log.e(TAG_FS_VM, "Error fetching path '$normalizedPath': ${response.errorDetails.message ?: response.errorDetails.value}")
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = response.errorDetails.message ?: response.errorDetails.value ?: "Unknown error fetching filesystem."
                        )
                    }
                }
            }
        }
    }

    fun navigateToChild(childEntry: FilesystemEntry) {
        if (childEntry.type == "dir") {
            fetchPathContent(childEntry.path)
        }
    }

    fun navigateToPathSegment(segment: PathSegment) {
        fetchPathContent(segment.fullPath)
    }

    fun navigateToParentPath(): Boolean {
        val parentPath = normalizeFsPath(uiState.value.currentPath).substringBeforeLast('/', "")
        if (parentPath.isEmpty()) {
            Log.d(TAG_FS_VM, "Already at root. Cannot navigate to parent.")
            return false
        }
        fetchPathContent(parentPath)
        return true
    }

    // --- Modifications ---

    fun createFolder(name: String) {
        val folderName = name.trim()
        if (!isValidNodeName(folderName)) {
            reportOperationError("A folder name can't be empty or contain a slash.")
            return
        }
        runModification { apiKey, dir -> filesystemApi.createDirectory(apiKey, "$dir/$folderName") }
    }

    fun renameEntry(entry: FilesystemEntry, newName: String) {
        val name = newName.trim()
        if (!isValidNodeName(name)) {
            reportOperationError("A name can't be empty or contain a slash.")
            return
        }
        if (name == entry.name) return
        val parent = normalizeFsPath(entry.path).substringBeforeLast('/', FILESYSTEM_ROOT_PATH)
        runModification { apiKey, _ -> filesystemApi.renameNode(apiKey, entry.path, "$parent/$name") }
    }

    fun moveEntry(entry: FilesystemEntry, destinationDirectory: String) {
        val destination = normalizeFsPath(destinationDirectory)
        val source = normalizeFsPath(entry.path)
        if (destination == source || destination.startsWith("$source/")) {
            reportOperationError("A folder can't be moved into itself.")
            return
        }
        if (destination == source.substringBeforeLast('/', "")) return // already there
        runModification { apiKey, _ -> filesystemApi.renameNode(apiKey, entry.path, "$destination/${entry.name}") }
    }

    fun deleteEntry(entry: FilesystemEntry) {
        // Deleting a folder always removes what is inside it, the UI asks for confirmation first
        runModification { apiKey, _ -> filesystemApi.deleteNode(apiKey, entry.path, recursive = entry.type == "dir") }
    }

    /** There is no endpoint to delete several nodes, so this deletes them one by one (folders with their contents). */
    fun deleteEntries(entries: List<FilesystemEntry>) {
        runBulkModification(entries, "deleted") { apiKey, entry ->
            filesystemApi.deleteNode(apiKey, entry.path, recursive = entry.type == "dir")
        }
    }

    fun moveEntries(entries: List<FilesystemEntry>, destinationDirectory: String) {
        val destination = normalizeFsPath(destinationDirectory)
        runBulkModification(entries, "moved") { apiKey, entry ->
            val source = normalizeFsPath(entry.path)
            when {
                destination == source || destination.startsWith("$source/") ->
                    ApiResponse.Error(FileUploadResponse(success = false, value = "circular_dependency"))
                destination == source.substringBeforeLast('/', "") ->
                    ApiResponse.Success(FileUploadResponse(success = true, value = "ok")) // already in that folder
                else -> filesystemApi.renameNode(apiKey, entry.path, "$destination/${entry.name}")
            }
        }
    }

    private fun runBulkModification(
        entries: List<FilesystemEntry>,
        pastTense: String,
        operation: suspend (apiKey: String, entry: FilesystemEntry) -> ApiResponse<FileUploadResponse>
    ) {
        if (entries.isEmpty()) return
        val apiKey = internalApiKey
        if (apiKey.isBlank()) {
            reportOperationError("API Key is missing. Please set it in Settings.")
            return
        }
        if (_uiState.value.isModifying) return
        val dir = normalizeFsPath(_uiState.value.currentPath)
        _uiState.update { it.copy(isModifying = true, operationMessage = null, operationError = null) }
        viewModelScope.launch {
            var succeeded = 0
            val failures = mutableListOf<String>()
            for (entry in entries) {
                when (val response = operation(apiKey, entry)) {
                    is ApiResponse.Success -> succeeded++
                    is ApiResponse.Error -> failures.add("${entry.name}: ${describeError(response.errorDetails)}")
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
            fetchPathContent(dir)
        }
    }

    /** The folders inside [path], for the folder picker. */
    suspend fun listSubdirectories(path: String): ApiResponse<List<FilesystemEntry>> {
        val apiKey = internalApiKey
        if (apiKey.isBlank()) {
            return ApiResponse.Error(FileUploadResponse(success = false, value = "api_key_missing", message = "API Key is missing. Please set it in Settings."))
        }
        return when (val response = filesystemApi.getFilesystemPath(apiKey, normalizeFsPath(path))) {
            is ApiResponse.Success -> ApiResponse.Success(
                response.data.children.filter { it.type == "dir" }.sortedBy { it.name.lowercase() }
            )
            is ApiResponse.Error -> ApiResponse.Error(response.errorDetails)
        }
    }

    fun importFilesById(fileIds: List<String>) {
        val ids = fileIds.map { it.trim() }.filter { it.isNotEmpty() }
        if (ids.isEmpty()) {
            reportOperationError("Enter at least one file ID.")
            return
        }
        runModification { apiKey, dir -> filesystemApi.importFiles(apiKey, dir, ids) }
    }

    private fun isValidNodeName(name: String) = name.isNotEmpty() && !name.contains('/') && name != "." && name != ".."

    private fun runModification(operation: suspend (apiKey: String, currentDir: String) -> ApiResponse<FileUploadResponse>) {
        val apiKey = internalApiKey
        if (apiKey.isBlank()) {
            reportOperationError("API Key is missing. Please set it in Settings.")
            return
        }
        if (_uiState.value.isModifying) return
        val dir = normalizeFsPath(_uiState.value.currentPath)
        _uiState.update { it.copy(isModifying = true, operationMessage = null, operationError = null) }
        viewModelScope.launch {
            when (val response = operation(apiKey, dir)) {
                is ApiResponse.Success -> {
                    _uiState.update { it.copy(isModifying = false, operationMessage = response.data.message ?: "Done.") }
                    fetchPathContent(dir)
                }
                is ApiResponse.Error -> {
                    _uiState.update { it.copy(isModifying = false, operationError = describeError(response.errorDetails)) }
                }
            }
        }
    }

    private fun reportOperationError(message: String) {
        _uiState.update { it.copy(operationError = message) }
    }

    private fun describeError(error: FileUploadResponse): String {
        val hint = when (error.value) {
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
        return hint ?: error.message ?: error.value ?: "Unknown error."
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
                        sizeBytes = info.sizeBytes
                    )
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
        val apiKey = internalApiKey
        if (apiKey.isBlank()) {
            reportOperationError("API Key is missing. Please set it in Settings.")
            return
        }
        val dir = normalizeFsPath(_uiState.value.currentPath)
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
            try {
                items.forEachIndexed { index, item ->
                    val response = filesystemApi.uploadFileFromUri(apiKey, "$dir/${item.name}", item.uri, application) { sent, _ ->
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
                    when (response) {
                        is ApiResponse.Success -> uploaded++
                        is ApiResponse.Error -> failures.add("${item.name}: ${describeError(response.errorDetails)}")
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
                if (normalizeFsPath(_uiState.value.currentPath) == dir) fetchPathContent(dir)
            }
        }
    }
}
