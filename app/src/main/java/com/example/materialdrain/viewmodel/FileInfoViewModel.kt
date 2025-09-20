package com.example.materialdrain.viewmodel

import android.app.Application
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.materialdrain.network.ApiResponse
import com.example.materialdrain.network.FileInfoResponse
import com.example.materialdrain.network.PixeldrainApiService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

private const val PREFS_NAME = "pixeldrain_prefs"
private const val API_KEY_PREF = "api_key"
private const val TAG = "FileInfoViewModel"

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
    val message: String? = null // For individual success/error messages
)
// --- End Download State Management ---

data class FileInfoUiState(
    // For single file info
    val isLoadingFileInfo: Boolean = false,
    val fileInfo: FileInfoResponse? = null,
    val fileInfoErrorMessage: String? = null,
    val fileIdInput: String = "",
    val showEnterFileIdDialog: Boolean = false,

    // For user's list of files
    val userFilesList: List<FileInfoResponse> = emptyList(),
    val isLoadingUserFiles: Boolean = false,
    val userFilesListErrorMessage: String? = null,

    // For file deletion
    val isLoadingDeleteFile: Boolean = false,
    val deleteFileSuccessMessage: String? = null,
    val deleteFileErrorMessage: String? = null,
    val showDeleteConfirmDialog: Boolean = false,
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
    val fileDownloadErrorMessage: String? = null
)

class FileInfoViewModel(
    private val application: Application,
    private val pixeldrainApiService: PixeldrainApiService
) : ViewModel() {

    private val _uiState = MutableStateFlow(FileInfoUiState())
    val uiState: StateFlow<FileInfoUiState> = _uiState.asStateFlow()

    private var apiKey: String = ""

    init {
        loadApiKey()
    }

    fun loadApiKey() {
        val sharedPrefs = application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        apiKey = sharedPrefs.getString(API_KEY_PREF, "") ?: ""
        Log.d(TAG, "API Key loaded: ${if (apiKey.isNotBlank()) "Present" else "Missing"}")
        if (apiKey.isBlank()) {
            _uiState.update { it.copy(apiKeyMissingError = true, userFilesListErrorMessage = "API Key is missing. Please set it in Settings.") }
        } else {
            if (_uiState.value.apiKeyMissingError || _uiState.value.userFilesListErrorMessage?.contains("API Key is missing") == true) {
                 _uiState.update { it.copy(apiKeyMissingError = false, userFilesListErrorMessage = null) }
                 if (_uiState.value.userFilesList.isEmpty() && !_uiState.value.isLoadingUserFiles) {
                    fetchUserFiles()
                 }
            }
        }
    }

    // --- Single File Info Functions ---
    fun onFileIdInputChange(newFileId: String) {
        _uiState.update { it.copy(fileIdInput = newFileId) }
    }

    fun fetchFileInfo(fileId: String) {
        if (fileId.isBlank()) {
            _uiState.update { it.copy(fileInfoErrorMessage = "Please enter or select a File ID.", isLoadingFileInfo = false) }
            return
        }
        _uiState.update { it.copy(isLoadingFileInfo = true, fileInfo = null, fileInfoErrorMessage = null) }
        viewModelScope.launch {
            when (val response = pixeldrainApiService.getFileInfo(fileId)) {
                is ApiResponse.Success -> {
                    _uiState.update {
                        it.copy(isLoadingFileInfo = false, fileInfo = response.data, fileIdInput = fileId)
                    }
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

    fun fetchFileInfoFromDialogInput() { fetchFileInfo(_uiState.value.fileIdInput) }
    fun fetchFileInfoById(fileId: String) { onFileIdInputChange(fileId); fetchFileInfo(fileId) }
    fun clearFileInfoInput() { _uiState.update { it.copy(fileIdInput = "", fileInfo = null, fileInfoErrorMessage = null, isLoadingFileInfo = false) } }
    fun clearFileInfoDisplay() { _uiState.update { it.copy(fileInfo = null, isLoadingFileInfo = false) } }
    fun clearFileInfoError() { _uiState.update { it.copy(fileInfoErrorMessage = null) } }
    fun showEnterFileIdDialog() { _uiState.update { it.copy(showEnterFileIdDialog = true, fileIdInput = "", fileInfoErrorMessage = null) } }
    fun dismissEnterFileIdDialog() { _uiState.update { it.copy(showEnterFileIdDialog = false, fileInfoErrorMessage = null) } }

    // --- User Files List Functions ---
    fun fetchUserFiles() {
        if (apiKey.isBlank()) {
            Log.d(TAG, "fetchUserFiles skipped: API key is blank.")
            _uiState.update { it.copy(userFilesListErrorMessage = "API Key is required. Please set it in Settings.", isLoadingUserFiles = false, userFilesList = emptyList(), apiKeyMissingError = true) }
            return
        }
        _uiState.update { it.copy(isLoadingUserFiles = true, userFilesListErrorMessage = null, apiKeyMissingError = false) }
        viewModelScope.launch {
            when (val response = pixeldrainApiService.getUserFiles(apiKey)) {
                is ApiResponse.Success -> {
                    _uiState.update { it.copy(isLoadingUserFiles = false, userFilesList = response.data.files) }
                }
                is ApiResponse.Error -> {
                    val errorMsg = response.errorDetails.message ?: response.errorDetails.value ?: "Unknown error fetching user files"
                    _uiState.update { it.copy(isLoadingUserFiles = false, userFilesListErrorMessage = errorMsg, apiKeyMissingError = errorMsg.contains("unauthorized", ignoreCase = true) || response.errorDetails.value == "api_key_missing") }
                }
            }
        }
    }
    fun clearUserFilesError() { _uiState.update { it.copy(userFilesListErrorMessage = null, apiKeyMissingError = false) } }
    fun clearUserFilesList() { _uiState.update { it.copy(userFilesList = emptyList(), userFilesListErrorMessage = null, isLoadingUserFiles = false, apiKeyMissingError = false) } }

    // --- Sorting --- 
    fun changeSortOrder(newField: SortableField? = null, newAscending: Boolean? = null) { _uiState.update { it.copy(sortField = newField ?: it.sortField, sortAscending = newAscending ?: it.sortAscending) } }

    // --- Filtering ---
    fun onFilterQueryChanged(newQuery: String) { _uiState.update { it.copy(filterQuery = newQuery) } }
    fun toggleFilterInput() { _uiState.update { it.copy(showFilterInput = !it.showFilterInput) } }
    fun setFilterInputVisible(isVisible: Boolean) { _uiState.update { it.copy(showFilterInput = isVisible) } }

    // --- File Deletion Functions ---
    fun initiateDeleteFile(fileId: String) { _uiState.update { it.copy(showDeleteConfirmDialog = true, fileIdToDelete = fileId) } }
    fun cancelDeleteFile() { _uiState.update { it.copy(showDeleteConfirmDialog = false, fileIdToDelete = null, deleteFileErrorMessage = null) } }
    fun confirmDeleteFile() {
        val fileId = _uiState.value.fileIdToDelete ?: return
        if (apiKey.isBlank()) {
            _uiState.update { it.copy(showDeleteConfirmDialog = false, deleteFileErrorMessage = "API Key is missing. Cannot delete file.", apiKeyMissingError = true) }
            return
        }
        _uiState.update { it.copy(isLoadingDeleteFile = true, deleteFileErrorMessage = null, deleteFileSuccessMessage = null) }
        viewModelScope.launch {
            when (val response = pixeldrainApiService.deleteFile(apiKey, fileId)) {
                is ApiResponse.Success -> _uiState.update { 
                    it.copy(
                        isLoadingDeleteFile = false, 
                        showDeleteConfirmDialog = false, 
                        fileIdToDelete = null, 
                        deleteFileSuccessMessage = response.data.message ?: "File deleted successfully.", 
                        userFilesList = _uiState.value.userFilesList.filterNot { item -> item.id == fileId }, 
                        fileInfo = if (_uiState.value.fileInfo?.id == fileId) null else _uiState.value.fileInfo,
                        activeDownloads = it.activeDownloads.filterNot { entry -> entry.key == fileId } // Also remove from active downloads
                    )
                }
                is ApiResponse.Error -> _uiState.update { it.copy(isLoadingDeleteFile = false, showDeleteConfirmDialog = false, fileIdToDelete = null, deleteFileErrorMessage = response.errorDetails.message ?: response.errorDetails.value ?: "Unknown error deleting file.") }
            }
        }
    }
    fun clearDeleteMessages() { _uiState.update { it.copy(deleteFileSuccessMessage = null, deleteFileErrorMessage = null) } }
    fun clearApiKeyMissingError() { _uiState.update { it.copy(apiKeyMissingError = false, userFilesListErrorMessage = if(it.userFilesListErrorMessage?.contains("API Key is missing") == true) null else it.userFilesListErrorMessage) } }

    // --- File Download Functions ---
    fun initiateDownloadFile(file: FileInfoResponse) {
        if (_uiState.value.activeDownloads[file.id]?.status == DownloadStatus.DOWNLOADING || 
            _uiState.value.activeDownloads[file.id]?.status == DownloadStatus.PENDING) {
            Log.d(TAG, "Download for ${file.id} already in progress or pending.")
            return // Prevent multiple downloads of the same file if already active
        }

        val newDownloadState = FileDownloadState(
            fileId = file.id,
            fileName = file.name,
            totalBytes = file.size, // Assuming FileInfoResponse.size is the total
            status = DownloadStatus.PENDING
        )
        _uiState.update {
            it.copy(activeDownloads = it.activeDownloads + (file.id to newDownloadState))
        }

        viewModelScope.launch {
            try {
                // Update status to DOWNLOADING before starting
                _uiState.update { currentState ->
                    val updatedDownload = currentState.activeDownloads[file.id]?.copy(status = DownloadStatus.DOWNLOADING)
                    if (updatedDownload != null) {
                        currentState.copy(activeDownloads = currentState.activeDownloads + (file.id to updatedDownload))
                    } else currentState
                }

                when (val response = pixeldrainApiService.downloadFileBytes(file.id) { bytesRead, totalBytes ->
                    val progress = if (totalBytes != null && totalBytes > 0) {
                        (bytesRead.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
                    } else {
                        0f // Indeterminate or starting
                    }
                    _uiState.update { currentState ->
                        val updatedDownload = currentState.activeDownloads[file.id]?.copy(
                            downloadedBytes = bytesRead,
                            progressFraction = progress,
                            totalBytes = totalBytes ?: currentState.activeDownloads[file.id]?.totalBytes // Keep original total if callback provides null
                        )
                        if (updatedDownload != null) {
                            currentState.copy(activeDownloads = currentState.activeDownloads + (file.id to updatedDownload))
                        } else currentState
                    }
                }) {
                    is ApiResponse.Success -> {
                        val savedUri = saveBytesToDownloads(file.name, file.mimeType, response.data)
                        val finalStatus: DownloadStatus
                        val message: String
                        if (savedUri != null) {
                            finalStatus = DownloadStatus.COMPLETED
                            message = "File '${file.name}' downloaded to Downloads folder."
                            // Optionally set general success message for snackbar
                             _uiState.update { it.copy(fileDownloadSuccessMessage = message) }
                        } else {
                            finalStatus = DownloadStatus.FAILED
                            message = "Failed to save '${file.name}' to device."
                            // Optionally set general error message for snackbar
                             _uiState.update { it.copy(fileDownloadErrorMessage = message) }
                        }
                        _uiState.update { currentState ->
                            val updatedDownload = currentState.activeDownloads[file.id]?.copy(
                                status = finalStatus,
                                message = message,
                                downloadedBytes = if (finalStatus == DownloadStatus.COMPLETED) file.size else currentState.activeDownloads[file.id]?.downloadedBytes ?: 0L,
                                progressFraction = if (finalStatus == DownloadStatus.COMPLETED) 1f else currentState.activeDownloads[file.id]?.progressFraction ?: 0f
                            )
                            if (updatedDownload != null) {
                                currentState.copy(activeDownloads = currentState.activeDownloads + (file.id to updatedDownload))
                            } else currentState
                        }
                    }
                    is ApiResponse.Error -> {
                        val errorMsg = response.errorDetails.message ?: "Download failed."
                        _uiState.update { currentState ->
                            val updatedDownload = currentState.activeDownloads[file.id]?.copy(
                                status = DownloadStatus.FAILED,
                                message = errorMsg
                            )
                             // Optionally set general error message for snackbar
                            _uiState.update { it.copy(fileDownloadErrorMessage = errorMsg) }
                            if (updatedDownload != null) {
                                currentState.copy(activeDownloads = currentState.activeDownloads + (file.id to updatedDownload))
                            } else currentState
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error during file download process for ${file.id}: ${e.message}", e)
                val errorMsg = "Download error: ${e.localizedMessage ?: "An unexpected error occurred."}"
                _uiState.update { currentState ->
                    val updatedDownload = currentState.activeDownloads[file.id]?.copy(
                        status = DownloadStatus.FAILED,
                        message = errorMsg
                    )
                    // Optionally set general error message for snackbar
                    _uiState.update { it.copy(fileDownloadErrorMessage = errorMsg) }
                    if (updatedDownload != null) {
                        currentState.copy(activeDownloads = currentState.activeDownloads + (file.id to updatedDownload))
                    } else currentState
                }
            }
        }
    }

    private suspend fun saveBytesToDownloads(fileName: String, mimeType: String?, bytes: ByteArray): Uri? {
        return withContext(Dispatchers.IO) {
            val context = application.applicationContext
            val contentResolver = context.contentResolver
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType ?: "application/octet-stream")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                } else {
                    val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                    val file = java.io.File(downloadsDir, fileName)
                    put(MediaStore.MediaColumns.DATA, file.absolutePath) 
                }
            }

            var uri: Uri? = null
            try {
                uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                uri?.let { destUri ->
                    contentResolver.openOutputStream(destUri)?.use {
                        it.write(bytes)
                    } ?: throw IOException("Failed to get output stream for $destUri")
                    
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        contentValues.clear()
                        contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                        contentResolver.update(destUri, contentValues, null, null)
                    }
                }
            } catch (e: Exception) { 
                Log.e(TAG, "Failed to save file '$fileName' to Downloads: ${e.message}", e)
                uri?.let { contentResolver.delete(it, null, null) } 
                uri = null
            }
            uri
        }
    }

    // Clears general download messages
    fun clearDownloadMessages() {
        _uiState.update {
            it.copy(
                fileDownloadSuccessMessage = null,
                fileDownloadErrorMessage = null
            )
        }
    }

    // Clears a specific download state from the active map (e.g., when UI dismisses it)
    fun clearDownloadState(fileId: String) {
        _uiState.update { currentState ->
            currentState.copy(activeDownloads = currentState.activeDownloads - fileId)
        }
    }
}
