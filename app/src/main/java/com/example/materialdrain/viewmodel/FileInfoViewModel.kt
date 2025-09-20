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

    // For file download
    val isDownloadingFile: Boolean = false,
    val fileToDownloadInfo: FileInfoResponse? = null, // Info of the file being downloaded
    val fileDownloadedBytes: Long = 0L, // Bytes downloaded so far
    val fileDownloadProgress: Float = 0f, // 0.0 to 1.0
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
                is ApiResponse.Success -> _uiState.update { it.copy(isLoadingDeleteFile = false, showDeleteConfirmDialog = false, fileIdToDelete = null, deleteFileSuccessMessage = response.data.message ?: "File deleted successfully.", userFilesList = _uiState.value.userFilesList.filterNot { item -> item.id == fileId }, fileInfo = if (_uiState.value.fileInfo?.id == fileId) null else _uiState.value.fileInfo) }
                is ApiResponse.Error -> _uiState.update { it.copy(isLoadingDeleteFile = false, showDeleteConfirmDialog = false, fileIdToDelete = null, deleteFileErrorMessage = response.errorDetails.message ?: response.errorDetails.value ?: "Unknown error deleting file.") }
            }
        }
    }
    fun clearDeleteMessages() { _uiState.update { it.copy(deleteFileSuccessMessage = null, deleteFileErrorMessage = null) } }
    fun clearApiKeyMissingError() { _uiState.update { it.copy(apiKeyMissingError = false, userFilesListErrorMessage = if(it.userFilesListErrorMessage?.contains("API Key is missing") == true) null else it.userFilesListErrorMessage) } }

    // --- File Download Functions ---
    fun initiateDownloadFile(file: FileInfoResponse) {
        if (_uiState.value.isDownloadingFile && _uiState.value.fileToDownloadInfo?.id == file.id) return // Prevent multiple downloads of the same file

        _uiState.update {
            it.copy(
                isDownloadingFile = true,
                fileToDownloadInfo = file,
                fileDownloadedBytes = 0L,
                fileDownloadProgress = 0f,
                fileDownloadSuccessMessage = null,
                fileDownloadErrorMessage = null
            )
        }

        viewModelScope.launch {
            try {
                when (val response = pixeldrainApiService.downloadFileBytes(file.id) { bytesRead, totalBytes ->
                    val progress = if (totalBytes != null && totalBytes > 0) {
                        (bytesRead.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
                    } else {
                        0f // Indeterminate or starting
                    }
                    _uiState.update { currentState -> 
                        currentState.copy(
                            fileDownloadedBytes = bytesRead, 
                            fileDownloadProgress = progress
                        ) 
                    }
                }) {
                    is ApiResponse.Success -> {
                        val savedUri = saveBytesToDownloads(file.name, file.mimeType, response.data)
                        if (savedUri != null) {
                            _uiState.update {
                                it.copy(
                                    isDownloadingFile = false,
                                    fileDownloadSuccessMessage = "File '${file.name}' downloaded to Downloads folder.",
                                    fileToDownloadInfo = null, // Clear after successful download
                                    fileDownloadedBytes = file.size, // Show full size on completion
                                    fileDownloadProgress = 1f
                                )
                            }
                        } else {
                            _uiState.update {
                                it.copy(
                                    isDownloadingFile = false,
                                    fileDownloadErrorMessage = "Failed to save '${file.name}' to device.",
                                    fileToDownloadInfo = null,
                                    fileDownloadedBytes = 0L, // Reset on save error
                                    fileDownloadProgress = 0f // Reset progress on save error
                                )
                            }
                        }
                    }
                    is ApiResponse.Error -> {
                        _uiState.update {
                            it.copy(
                                isDownloadingFile = false,
                                fileDownloadErrorMessage = response.errorDetails.message ?: "Download failed.",
                                fileToDownloadInfo = null,
                                fileDownloadedBytes = 0L, 
                                fileDownloadProgress = 0f
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error during file download process for ${file.id}: ${e.message}", e)
                _uiState.update {
                    it.copy(
                        isDownloadingFile = false,
                        fileDownloadErrorMessage = "Download error: ${e.localizedMessage ?: "An unexpected error occurred."}",
                        fileToDownloadInfo = null,
                        fileDownloadedBytes = 0L,
                        fileDownloadProgress = 0f
                    )
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
                    // This else block might be less relevant given minSdk 32 (Android S)
                    // but kept for robustness if minSdk were ever lowered below Q.
                    // On API < 29, you'd write to a file path obtained from Environment.getExternalStoragePublicDirectory()
                    // and then use MediaScannerConnection to make it visible, or use the _data column if allowed.
                    // However, with minSdk 32, Q's MediaStore approach is standard.
                    val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                    val file = java.io.File(downloadsDir, fileName)
                    // Note: Writing directly to file.absolutePath and then using MediaStore.Images.Media.insertImage
                    // or similar for other file types was common for pre-Q. For Q+, the IS_PENDING method is preferred.
                    put(MediaStore.MediaColumns.DATA, file.absolutePath) // This is legacy for Q+
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
            } catch (e: Exception) { // Catch generic Exception as various issues can occur
                Log.e(TAG, "Failed to save file '$fileName' to Downloads: ${e.message}", e)
                uri?.let { contentResolver.delete(it, null, null) } // Clean up pending entry if write failed
                uri = null
            }
            uri
        }
    }

    fun clearDownloadMessages() {
        _uiState.update {
            it.copy(
                fileDownloadSuccessMessage = null,
                fileDownloadErrorMessage = null
                // Optionally reset progress and currently downloading file if it's not actively downloading:
                // fileToDownloadInfo = if(it.isDownloadingFile) it.fileToDownloadInfo else null,
                // fileDownloadedBytes = if(it.isDownloadingFile) it.fileDownloadedBytes else 0L,
                // fileDownloadProgress = if(it.isDownloadingFile) it.fileDownloadProgress else 0f
            )
        }
    }
}
