package com.example.materialdrain.viewmodel

import android.app.Application
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.materialdrain.network.ApiResponse
import com.example.materialdrain.network.FileInfoResponse
import com.example.materialdrain.network.PixeldrainApiService
import com.example.materialdrain.network.FilesystemEntry // Added import
import com.example.materialdrain.network.toFileInfoResponse // Added import
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedOutputStream
import java.io.IOException
import java.io.OutputStream
import kotlin.coroutines.cancellation.CancellationException

private const val PREFS_NAME = "pixeldrain_prefs"
private const val API_KEY_PREF = "api_key"
private const val TAG = "FileInfoViewModel"
private const val MAX_TEXT_PREVIEW_FETCH_SIZE_BYTES = 1 * 1024 * 1024 // 1MB
private const val MAX_TEXT_PREVIEW_DISPLAY_LENGTH = 8 * 1024 // 8KB

// Define SortableField enum
enum class SortableField {
    NAME,
    SIZE,
    UPLOAD_DATE
}

// --- Download State Management ---
enum class DownloadStatus {
    PENDING,
    DOWNLOADING,
    COMPLETED,
    FAILED
}

data class FileDownloadState(
    val fileId: String,
    val fileName: String,
    val totalBytes: Long?,
    val downloadedBytes: Long = 0L,
    val progressFraction: Float = 0f,
    val status: DownloadStatus = DownloadStatus.PENDING,
    val message: String? = null, // For individual success/error messages
    val targetUri: Uri? = null // To store the MediaStore URI
)
// --- End Download State Management ---

data class FileInfoUiState(
    // For single file info
    val isLoadingFileInfo: Boolean = false,
    val fileInfo: FileInfoResponse? = null,
    val fileInfoErrorMessage: String? = null,
    val fileIdInput: String = "",
    val showEnterFileIdDialog: Boolean = false,

    // For text preview
    val isLoadingTextPreview: Boolean = false,
    val textPreviewContent: String? = null,
    val textPreviewErrorMessage: String? = null,

    // For user's list of files
    val userFilesList: List<FileInfoResponse> = emptyList(),
    val isLoadingUserFiles: Boolean = false,
    val userFilesListErrorMessage: String? = null,

    // For file deletion
    val isLoadingDeleteFile: Boolean = false,
    val deleteFileSuccessMessage: String? = null,
    val deleteFileErrorMessage: String? = null,
    val initiateDeleteFile: Boolean = false,
    val fileIdToDelete: String? = null,

    val apiKeyMissingError: Boolean = false,

    // Sorting state
    val sortField: SortableField = SortableField.UPLOAD_DATE,
    val sortAscending: Boolean = false, // Default: Newest first for UPLOAD_DATE

    // Filtering state
    val filterQuery: String = "",
    val showFilterInput: Boolean = false,

    // For multiple file downloads
    val activeDownloads: Map<String, FileDownloadState> = emptyMap(),
    // General messages, can be deprecated if per-file messages are sufficient
    val fileDownloadSuccessMessage: String? = null,
    val fileDownloadErrorMessage: String? = null,

    // Scroll state preservation
    val shouldPreserveScrollPosition: Boolean = false
)

class FileInfoViewModel(
    private val application: Application,
    private val pixeldrainApiService: PixeldrainApiService
) : ViewModel() {

    private val _uiState = MutableStateFlow(FileInfoUiState())
    val uiState: StateFlow<FileInfoUiState> = _uiState.asStateFlow()

    private val _displayedFiles = MutableStateFlow<List<FileInfoResponse>>(emptyList())
    val displayedFiles: StateFlow<List<FileInfoResponse>> = _displayedFiles.asStateFlow()


    private var apiKey: String = ""
    // Removed downloadJobs map

    init {
        Log.d(TAG, "ViewModel init: Calling loadApiKey()")
        loadApiKey()

        // 👇 New: start recomputing displayedFiles whenever relevant state changes
        viewModelScope.launch {
            combine(
                uiState.map { it.userFilesList },
                uiState.map { it.sortField },
                uiState.map { it.sortAscending },
                uiState.map { it.filterQuery }
            ) { files, sortField, sortAscending, filterQuery ->
                withContext(Dispatchers.Default) {
                    files
                        .filter { it.name.contains(filterQuery, ignoreCase = true) }
                        .sortedWith(
                            compareBy<FileInfoResponse> {
                                when (sortField) {
                                    SortableField.NAME -> it.name.lowercase()
                                    SortableField.SIZE -> it.size
                                    SortableField.UPLOAD_DATE -> it.dateUpload
                                }
                            }.let { comparator ->
                                if (sortAscending) comparator else comparator.reversed()
                            }
                        )
                }
            }.collect { sortedAndFiltered ->
                _displayedFiles.value = sortedAndFiltered
            }
        }
    }


    fun loadApiKey() {
        val sharedPrefs = application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        apiKey = sharedPrefs.getString(API_KEY_PREF, "") ?: ""
        Log.d(TAG, "loadApiKey called. API Key loaded: '${if (apiKey.isNotBlank()) "PRESENT (not showing value)" else "MISSING"}'")

        if (apiKey.isBlank()) {
            Log.d(TAG, "loadApiKey: API key is blank. Updating UI state for missing key.")
            _uiState.update { it.copy(apiKeyMissingError = true, userFilesListErrorMessage = "API Key is missing. Please set it in Settings.") }
        } else {
            Log.d(TAG, "loadApiKey: API key is present.")
            val currentState = _uiState.value
            val wasApiKeyMissingError = currentState.apiKeyMissingError
            val wasUserFilesListErrorMessageMissingKey = currentState.userFilesListErrorMessage?.contains("API Key is missing") == true
            Log.d(TAG, "loadApiKey: Current State -> apiKeyMissingError: $wasApiKeyMissingError, userFilesListErrorMessage: '${currentState.userFilesListErrorMessage}'")

            if (wasApiKeyMissingError || wasUserFilesListErrorMessageMissingKey) {
                Log.d(TAG, "loadApiKey: Clearing previous API key related errors.")
                _uiState.update { it.copy(apiKeyMissingError = false, userFilesListErrorMessage = null) }

                val updatedState = _uiState.value
                val isUserFilesListEmpty = updatedState.userFilesList.isEmpty()
                val isNotLoadingUserFiles = !updatedState.isLoadingUserFiles
                Log.d(TAG, "loadApiKey (after clearing error): Checking conditions to fetch files:")
                Log.d(TAG, "loadApiKey (after clearing error): - userFilesList.isEmpty(): $isUserFilesListEmpty")
                Log.d(TAG, "loadApiKey (after clearing error): - !isLoadingUserFiles: $isNotLoadingUserFiles")

                if (isUserFilesListEmpty && isNotLoadingUserFiles) {
                    Log.d(TAG, "loadApiKey (after clearing error): Conditions met. Calling fetchUserFiles().")
                    fetchUserFiles()
                } else {
                    Log.d(TAG, "loadApiKey (after clearing error): Conditions NOT met for fetchUserFiles(). List empty: $isUserFilesListEmpty, Not loading: $isNotLoadingUserFiles")
                }
            } else {
                val isUserFilesListEmpty = currentState.userFilesList.isEmpty()
                val isNotLoadingUserFiles = !currentState.isLoadingUserFiles
                Log.d(TAG, "loadApiKey: No previous API key errors. Checking conditions to fetch files:")
                Log.d(TAG, "loadApiKey: - userFilesList.isEmpty(): $isUserFilesListEmpty")
                Log.d(TAG, "loadApiKey: - !isLoadingUserFiles: $isNotLoadingUserFiles")
                if (isUserFilesListEmpty && isNotLoadingUserFiles) {
                    Log.d(TAG, "loadApiKey (no prior error path): Conditions met. Calling fetchUserFiles().")
                    fetchUserFiles()
                } else {
                    Log.d(TAG, "loadApiKey (no prior error path): Conditions NOT met for fetchUserFiles(). List empty: $isUserFilesListEmpty, Not loading: $isNotLoadingUserFiles")
                }
            }
        }
    }

    private fun clearTextPreviewStates() {
        _uiState.update {
            it.copy(
                isLoadingTextPreview = false,
                textPreviewContent = null,
                textPreviewErrorMessage = null
            )
        }
    }

    fun onFileIdInputChange(newFileId: String) {
        _uiState.update { it.copy(fileIdInput = newFileId) }
        if (newFileId != _uiState.value.fileInfo?.id) {
            clearTextPreviewStates()
        }
    }

    private fun fetchTextFilePreviewContent(fileInfo: FileInfoResponse) {
        val mimeType = fileInfo.mimeType ?: ""
        val commonTextMimeTypes = listOf(
            "text/plain", "text/html", "text/css", "text/javascript", "text/xml", "text/csv",
            "application/json", "application/xml", "application/javascript", "application/rtf",
            "application/x-sh", "application/x-csh", "application/x-python", "application/x-perl"
        )
        val commonTextExtensions = listOf(".log", ".ini", ".conf", ".cfg", ".md", ".yaml", ".yml", ".toml")

        val isLikelyTextFile = commonTextMimeTypes.any { mimeType.startsWith(it, ignoreCase = true) } ||
                (mimeType.startsWith("application/octet-stream", ignoreCase = true) &&
                        commonTextExtensions.any { fileInfo.name.endsWith(it, ignoreCase = true) })

        if (!isLikelyTextFile) {
            _uiState.update { it.copy(textPreviewErrorMessage = "Preview not supported for this file type.") }
            return
        }

        if (fileInfo.size > MAX_TEXT_PREVIEW_FETCH_SIZE_BYTES) {
            _uiState.update { it.copy(textPreviewErrorMessage = "File is too large (${formatSize(fileInfo.size)}) for text preview. Max ${formatSize(MAX_TEXT_PREVIEW_FETCH_SIZE_BYTES.toLong())}.") }
            return
        }

        _uiState.update { it.copy(isLoadingTextPreview = true, textPreviewContent = null, textPreviewErrorMessage = null) }
        viewModelScope.launch {
            when (val response = pixeldrainApiService.getFileContentAsText(fileInfo.id)) {
                is ApiResponse.Success -> {
                    val content = response.data
                    val truncatedContent = if (content.length > MAX_TEXT_PREVIEW_DISPLAY_LENGTH) {
                        content.substring(0, MAX_TEXT_PREVIEW_DISPLAY_LENGTH) + "\n... (truncated)"
                    } else {
                        content
                    }
                    _uiState.update {
                        it.copy(isLoadingTextPreview = false, textPreviewContent = truncatedContent)
                    }
                }
                is ApiResponse.Error -> {
                    _uiState.update {
                        it.copy(
                            isLoadingTextPreview = false,
                            textPreviewErrorMessage = response.errorDetails.message ?: response.errorDetails.value ?: "Error fetching text preview."
                        )
                    }
                }
            }
        }
    }

    fun fetchFileInfo(fileId: String) {
        if (fileId.isBlank()) {
            _uiState.update { it.copy(fileInfoErrorMessage = "Please enter or select a File ID.", isLoadingFileInfo = false) }
            return
        }
        _uiState.update { it.copy(isLoadingFileInfo = true, fileInfo = null, fileInfoErrorMessage = null) }
        clearTextPreviewStates()
        viewModelScope.launch {
            // Pass the API key to the service method
            when (val response = pixeldrainApiService.getFileInfo(fileId, apiKey)) {
                is ApiResponse.Success -> {
                    _uiState.update {
                        it.copy(isLoadingFileInfo = false, fileInfo = response.data, fileIdInput = fileId)
                    }
                    // Only fetch text preview if the main file info call was successful
                    fetchTextFilePreviewContent(response.data)
                }
                is ApiResponse.Error -> {
                    _uiState.update {
                        it.copy(
                            isLoadingFileInfo = false,
                            fileInfoErrorMessage = response.errorDetails.message ?: response.errorDetails.value ?: "Unknown error fetching file info",
                            fileInfo = null
                        )
                    }
                }
            }
        }
    }

    // New function to set FileInfo from a FilesystemEntry
    fun setFileInfoFromFilesystemEntry(entry: FilesystemEntry) {
        val fileInfo = entry.toFileInfoResponse()
        if (fileInfo != null) {
            _uiState.update {
                it.copy(
                    isLoadingFileInfo = false, // Assuming conversion is quick
                    fileInfo = fileInfo,
                    fileIdInput = fileInfo.id, // Keep fileIdInput in sync
                    fileInfoErrorMessage = null
                )
            }
            fetchTextFilePreviewContent(fileInfo) // Attempt to load text preview if applicable
        } else {
            _uiState.update {
                it.copy(
                    isLoadingFileInfo = false,
                    fileInfo = null,
                    fileInfoErrorMessage = "Cannot display details. Item may be a folder or a file without a direct Pixeldrain ID."
                )
            }
            clearTextPreviewStates() // Clear any previous preview
        }
    }

    fun fetchFileInfoFromDialogInput() { fetchFileInfo(_uiState.value.fileIdInput) }

    fun clearFileInfoDisplay() {
        _uiState.update { it.copy(fileInfo = null, isLoadingFileInfo = false) }
        clearTextPreviewStates()
    }

    fun dismissEnterFileIdDialog() {
        _uiState.update { it.copy(showEnterFileIdDialog = false, fileInfoErrorMessage = null) }
        clearTextPreviewStates()
    }

    fun fetchUserFiles() {
        if (apiKey.isBlank()) {
            Log.d(TAG, "fetchUserFiles skipped: API key is blank.")
            _uiState.update { it.copy(userFilesListErrorMessage = "API Key is required. Please set it in Settings.", isLoadingUserFiles = false, userFilesList = emptyList(), apiKeyMissingError = true) }
            return
        }
        Log.d(TAG, "fetchUserFiles: Attempting to fetch files with API key.")
        _uiState.update { it.copy(isLoadingUserFiles = true, userFilesListErrorMessage = null, apiKeyMissingError = false) }
        viewModelScope.launch {
            when (val response = pixeldrainApiService.getUserFiles(apiKey)) {
                is ApiResponse.Success -> {
                    Log.d(TAG, "fetchUserFiles: Successfully fetched ${response.data.files.size} files.")
                    _uiState.update { it.copy(isLoadingUserFiles = false, userFilesList = response.data.files) }
                }
                is ApiResponse.Error -> {
                    val errorMsg = response.errorDetails.message ?: response.errorDetails.value ?: "Unknown error fetching user files"
                    Log.e(TAG, "fetchUserFiles: Error fetching files: $errorMsg")
                    _uiState.update { it.copy(isLoadingUserFiles = false, userFilesListErrorMessage = errorMsg, apiKeyMissingError = errorMsg.contains("unauthorized", ignoreCase = true) || response.errorDetails.value == "api_key_missing") }
                }
            }
        }
    }
    fun clearUserFilesError() { _uiState.update { it.copy(userFilesListErrorMessage = null, apiKeyMissingError = false) } }

    fun changeSortOrder(newField: SortableField? = null, newAscending: Boolean? = null) { _uiState.update { it.copy(sortField = newField ?: it.sortField, sortAscending = newAscending ?: it.sortAscending) } }

    fun onFilterQueryChanged(newQuery: String) { _uiState.update { it.copy(filterQuery = newQuery) } }
    fun toggleFilterInput() { _uiState.update { it.copy(showFilterInput = !it.showFilterInput) } }
    fun setFilterInputVisible(isVisible: Boolean) { _uiState.update { it.copy(showFilterInput = isVisible) } }

    fun initiateDeleteFile(fileId: String) { _uiState.update { it.copy(initiateDeleteFile = true, fileIdToDelete = fileId) } }
    fun cancelDeleteFile() { _uiState.update { it.copy(initiateDeleteFile = false, fileIdToDelete = null, deleteFileErrorMessage = null) } }
    fun confirmDeleteFile() {
        val fileId = _uiState.value.fileIdToDelete ?: return
        if (apiKey.isBlank()) {
            _uiState.update { it.copy(initiateDeleteFile = false, deleteFileErrorMessage = "API Key is missing. Cannot delete file.", apiKeyMissingError = true) }
            return
        }
        _uiState.update { it.copy(isLoadingDeleteFile = true, deleteFileErrorMessage = null, deleteFileSuccessMessage = null) }
        viewModelScope.launch {
            when (val response = pixeldrainApiService.deleteFile(apiKey, fileId)) {
                is ApiResponse.Success -> _uiState.update {
                    val newFileInfo = if (it.fileInfo?.id == fileId) null else it.fileInfo
                    if (newFileInfo == null) clearTextPreviewStates()
                    it.copy(
                        isLoadingDeleteFile = false,
                        initiateDeleteFile = false,
                        fileIdToDelete = null,
                        deleteFileSuccessMessage = response.data.message ?: "File deleted successfully.",
                        userFilesList = _uiState.value.userFilesList.filterNot { item -> item.id == fileId },
                        fileInfo = newFileInfo,
                        activeDownloads = it.activeDownloads.filterNot { entry -> entry.key == fileId }
                    )
                }
                is ApiResponse.Error -> _uiState.update { it.copy(isLoadingDeleteFile = false, initiateDeleteFile = false, fileIdToDelete = null, deleteFileErrorMessage = response.errorDetails.message ?: response.errorDetails.value ?: "Unknown error deleting file.") }
            }
        }
    }
    fun clearDeleteMessages() { _uiState.update { it.copy(deleteFileSuccessMessage = null, deleteFileErrorMessage = null) } }
    fun clearApiKeyMissingError() { _uiState.update { it.copy(apiKeyMissingError = false, userFilesListErrorMessage = if(it.userFilesListErrorMessage?.contains("API Key is missing") == true) null else it.userFilesListErrorMessage) } }

    // --- File Download Functions ---

    private suspend fun prepareDownloadTargetUriAndStream(fileName: String, mimeType: String?): Pair<Uri?, OutputStream?> {
        return withContext(Dispatchers.IO) {
            val context = application.applicationContext
            val contentResolver = context.contentResolver
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType ?: "application/octet-stream")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }

            var outputStream: OutputStream? = null
            var uri: Uri? = null
            try {
                uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                uri?.let {
                    val rawOutputStream = contentResolver.openOutputStream(it)
                    if (rawOutputStream != null) {
                        outputStream = BufferedOutputStream(rawOutputStream)
                    } else {
                        Log.e(TAG, "ContentResolver.openOutputStream returned null for $uri")
                    }
                }
                if (outputStream == null && uri != null) { 
                    contentResolver.delete(uri, null, null) 
                    uri = null
                    throw IOException("Failed to open output stream for $uri")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to prepare download target for '$fileName': ${e.message}", e)
                uri?.let { contentResolver.delete(it, null, null) } 
                return@withContext null to null
            }
            uri to outputStream
        }
    }

    private suspend fun finalizeMediaStoreEntry(uri: Uri, success: Boolean) {
        withContext(Dispatchers.IO) {
            val context = application.applicationContext
            val contentResolver = context.contentResolver
            if (success) {
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.IS_PENDING, 0)
                }
                try {
                    contentResolver.update(uri, contentValues, null, null)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to clear IS_PENDING flag for $uri: ${e.message}", e)
                }
            } else {
                try {
                    contentResolver.delete(uri, null, null)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to delete MediaStore entry $uri after failed download/cancellation: ${e.message}", e)
                }
            }
        }
    }

    fun initiateDownloadFile(file: FileInfoResponse) {
        // Removed check for existing active job from downloadJobs map

        _uiState.update { currentState ->
            val newDownloadState = FileDownloadState(
                fileId = file.id,
                fileName = file.name,
                totalBytes = file.size,
                status = DownloadStatus.PENDING
            )
            currentState.copy(activeDownloads = currentState.activeDownloads + (file.id to newDownloadState))
        }

        viewModelScope.launch(Dispatchers.IO) { // No longer assigning to 'job' variable
            var targetUri: Uri? = null
            var outputStream: OutputStream? = null
            var downloadSuccessful = false

            try {
                _uiState.value.activeDownloads[file.id]?.let { 
                    targetUri = it.targetUri
                }
                if (targetUri == null) { 
                     val (uri, stream) = prepareDownloadTargetUriAndStream(file.name, file.mimeType)
                     targetUri = uri
                     outputStream = stream
                } else {
                    application.contentResolver.openOutputStream(targetUri)?.let {
                        outputStream = BufferedOutputStream(it)
                    }
                }

                if (targetUri == null || outputStream == null) {
                    throw IOException("Failed to prepare or re-open download target in MediaStore.")
                }

                _uiState.update { currentState ->
                    val updatedDownload = currentState.activeDownloads[file.id]?.copy(
                        status = DownloadStatus.DOWNLOADING,
                        targetUri = targetUri
                    )
                    if (updatedDownload != null) {
                        currentState.copy(activeDownloads = currentState.activeDownloads + (file.id to updatedDownload))
                    } else currentState
                }

                val response = pixeldrainApiService.downloadFileToOutputStream(file.id, outputStream!!) { bytesRead, totalBytes ->
                    // Removed !coroutineContext.isActive check
                    Log.d(TAG, "onProgress: bytesRead = $bytesRead, totalBytes = $totalBytes, timestamp = ${System.currentTimeMillis()}")
                    val progress = if (totalBytes != null && totalBytes > 0) {
                        (bytesRead.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
                    } else 0f
                    _uiState.update { currentState ->
                        val currentDownload = currentState.activeDownloads[file.id]
                        val updatedDownload = currentDownload?.copy(
                            downloadedBytes = bytesRead,
                            progressFraction = progress,
                            totalBytes = totalBytes ?: currentDownload.totalBytes
                        )
                        if (updatedDownload != null) {
                            currentState.copy(activeDownloads = currentState.activeDownloads + (file.id to updatedDownload))
                        } else currentState
                    }
                }

                when (response) {
                    is ApiResponse.Success -> {
                        downloadSuccessful = true
                        val bytesCopied = response.data
                        val message = "File '${file.name}' downloaded (${formatSize(bytesCopied)})."
                        _uiState.update { currentState ->
                            val updatedDownload = currentState.activeDownloads[file.id]?.copy(
                                status = DownloadStatus.COMPLETED,
                                message = message,
                                downloadedBytes = bytesCopied,
                                progressFraction = 1f
                            )
                            currentState.copy(
                                activeDownloads = if (updatedDownload != null) currentState.activeDownloads + (file.id to updatedDownload) else currentState.activeDownloads,
                                fileDownloadSuccessMessage = message
                            )
                        }
                    }
                    is ApiResponse.Error -> {
                        throw IOException(response.errorDetails.message ?: "Download failed due to API error.")
                    }
                }
            } catch (e: Exception) {
                downloadSuccessful = false
                val errorMsg = if (e is CancellationException) {
                    Log.i(TAG, "Download for ${file.id} was cancelled by scope.")
                    "Download for '${file.name}' was cancelled."
                } else {
                    Log.e(TAG, "Error during file download process for ${file.id}: ${e.message}", e)
                    "Download error for '${file.name}': ${e.localizedMessage ?: "Unexpected error"}"
                }
                _uiState.update { currentState ->
                    val updatedDownload = currentState.activeDownloads[file.id]?.copy(
                        status = DownloadStatus.FAILED,
                        message = errorMsg,
                        progressFraction = if (e is CancellationException) 0f else currentState.activeDownloads[file.id]?.progressFraction ?: 0f,
                        downloadedBytes = if (e is CancellationException) 0L else currentState.activeDownloads[file.id]?.downloadedBytes ?: 0L
                    )
                    if (updatedDownload != null) {
                        currentState.copy(
                            activeDownloads = currentState.activeDownloads + (file.id to updatedDownload),
                            fileDownloadErrorMessage = if (e !is CancellationException) errorMsg else null
                        )
                    } else currentState
                }
            } finally {
                try {
                    outputStream?.close()
                } catch (e: IOException) {
                    Log.e(TAG, "Error closing output stream for ${file.name}: ${e.message}", e)
                }
                targetUri?.let {
                    finalizeMediaStoreEntry(it, downloadSuccessful)
                }
                // Removed downloadJobs.remove(file.id)
            }
        }
        // Removed downloadJobs[file.id] = job
    }

    // Removed fun cancelDownload(fileId: String)

    fun clearDownloadMessages() {
        _uiState.update {
            it.copy(
                fileDownloadSuccessMessage = null,
                fileDownloadErrorMessage = null
            )
        }
    }

    fun clearDownloadState(fileId: String) {
        // Removed logic related to downloadJobs and cancelDownload
        val targetUriToClean = _uiState.value.activeDownloads[fileId]?.targetUri
        _uiState.update { currentState ->
            currentState.copy(activeDownloads = currentState.activeDownloads - fileId)
        }
        targetUriToClean?.let {
            viewModelScope.launch { finalizeMediaStoreEntry(it, false) } 
        }
        // Removed downloadJobs.remove(fileId)
    }

    fun setPreserveScrollPosition(preserve: Boolean) {
        _uiState.update { it.copy(shouldPreserveScrollPosition = preserve) }
    }
}

internal fun formatSize(bytes: Long): String {
    if (bytes < 0) return "0 B"
    if (bytes == 0L) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    var size = bytes.toDouble()
    var unitIndex = 0
    while (size >= 1024 && unitIndex < units.size - 1) {
        size /= 1024
        unitIndex++
    }
    return java.text.DecimalFormat("#,##0.#").format(size) + " " + units[unitIndex]
}
