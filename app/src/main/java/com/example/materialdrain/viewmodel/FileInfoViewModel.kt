package com.example.materialdrain.viewmodel

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.materialdrain.network.ApiResponse
import com.example.materialdrain.network.FileInfoResponse
import com.example.materialdrain.network.PixeldrainApiService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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
    val showFilterInput: Boolean = false // Added for FAB-controlled filter visibility
)

class FileInfoViewModel(
    private val application: Application, // Added Application context
    private val pixeldrainApiService: PixeldrainApiService
) : ViewModel() {

    private val _uiState = MutableStateFlow(FileInfoUiState())
    val uiState: StateFlow<FileInfoUiState> = _uiState.asStateFlow()

    private var apiKey: String = ""

    init {
        loadApiKey()
        // Initial fetchUserFiles is now handled in MaterialDrainScreen to avoid slowness if API key is immediately available
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
                 // If API key is now present and list is empty, fetch files
                 if (_uiState.value.userFilesList.isEmpty() && !_uiState.value.isLoadingUserFiles) {
                    fetchUserFiles()
                 }
            }
        }
    }

    // --- Single File Info Functions ---
    fun onFileIdInputChange(newFileId: String) {
        _uiState.update {
            it.copy(
                fileIdInput = newFileId,
                // fileInfo = null, 
                // fileInfoErrorMessage = null 
            )
        }
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
                        it.copy(
                            isLoadingFileInfo = false,
                            fileInfo = response.data,
                            fileIdInput = fileId 
                        )
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

    fun fetchFileInfoFromDialogInput() {
        fetchFileInfo(_uiState.value.fileIdInput)
    }

    fun fetchFileInfoById(fileId: String) {
        onFileIdInputChange(fileId) 
        fetchFileInfo(fileId)
    }

    fun clearFileInfoInput() {
        _uiState.update {
            it.copy(
                fileIdInput = "",
                fileInfo = null,
                fileInfoErrorMessage = null,
                isLoadingFileInfo = false
            )
        }
    }

    fun clearFileInfoDisplay() { 
        _uiState.update {
            it.copy(
                fileInfo = null,
                isLoadingFileInfo = false
            )
        }
    }

    fun clearFileInfoError() {
        _uiState.update { it.copy(fileInfoErrorMessage = null) }
    }

    fun showEnterFileIdDialog() {
        _uiState.update { it.copy(showEnterFileIdDialog = true, fileIdInput = "", fileInfoErrorMessage = null) } 
    }

    fun dismissEnterFileIdDialog() {
        _uiState.update { it.copy(showEnterFileIdDialog = false, fileInfoErrorMessage = null) } 
    }

    // --- User Files List Functions ---
    fun fetchUserFiles() {
        if (apiKey.isBlank()) {
             Log.d(TAG, "fetchUserFiles skipped: API key is blank.")
            _uiState.update {
                it.copy(
                    userFilesListErrorMessage = "API Key is required to fetch user files. Please set it in Settings.",
                    isLoadingUserFiles = false,
                    userFilesList = emptyList(),
                    apiKeyMissingError = true
                )
            }
            return
        }

        _uiState.update { it.copy(isLoadingUserFiles = true, userFilesListErrorMessage = null, apiKeyMissingError = false) } 

        viewModelScope.launch {
            when (val response = pixeldrainApiService.getUserFiles(apiKey)) {
                is ApiResponse.Success -> {
                    _uiState.update {
                        it.copy(
                            isLoadingUserFiles = false,
                            userFilesList = response.data.files
                        )
                    }
                }
                is ApiResponse.Error -> {
                    val errorMsg = response.errorDetails.message ?: response.errorDetails.value ?: "Unknown error fetching user files"
                     _uiState.update {
                        it.copy(
                            isLoadingUserFiles = false,
                            userFilesListErrorMessage = errorMsg,
                            apiKeyMissingError = errorMsg.contains("unauthorized", ignoreCase = true) || response.errorDetails.value == "api_key_missing"
                        )
                    }
                }
            }
        }
    }

    fun clearUserFilesError() {
        _uiState.update { it.copy(userFilesListErrorMessage = null, apiKeyMissingError = false) }
    }

    fun clearUserFilesList() {
         _uiState.update {
            it.copy(
                userFilesList = emptyList(),
                userFilesListErrorMessage = null,
                isLoadingUserFiles = false,
                apiKeyMissingError = false
            )
        }
    }

    // --- Sorting --- 
    fun changeSortOrder(newField: SortableField? = null, newAscending: Boolean? = null) {
        _uiState.update {
            it.copy(
                sortField = newField ?: it.sortField,
                sortAscending = newAscending ?: it.sortAscending
            )
        }
    }

    // --- Filtering ---
    fun onFilterQueryChanged(newQuery: String) {
        _uiState.update { it.copy(filterQuery = newQuery) }
    }

    fun toggleFilterInput() {
        _uiState.update { it.copy(showFilterInput = !it.showFilterInput) }
    }

    fun setFilterInputVisible(isVisible: Boolean) {
        _uiState.update { it.copy(showFilterInput = isVisible) }
    }

    // --- File Deletion Functions ---
    fun initiateDeleteFile(fileId: String) {
        _uiState.update { it.copy(showDeleteConfirmDialog = true, fileIdToDelete = fileId) }
    }

    fun cancelDeleteFile() {
        _uiState.update { it.copy(showDeleteConfirmDialog = false, fileIdToDelete = null, deleteFileErrorMessage = null) }
    }

    fun confirmDeleteFile() {
        val fileId = _uiState.value.fileIdToDelete
        if (fileId == null) {
            _uiState.update { it.copy(showDeleteConfirmDialog = false, deleteFileErrorMessage = "File ID for deletion is missing.") }
            return
        }
        if (apiKey.isBlank()) {
            _uiState.update { it.copy(showDeleteConfirmDialog = false, deleteFileErrorMessage = "API Key is missing. Cannot delete file.", apiKeyMissingError = true) }
            return
        }

        _uiState.update { it.copy(isLoadingDeleteFile = true, deleteFileErrorMessage = null, deleteFileSuccessMessage = null) }

        viewModelScope.launch {
            when (val response = pixeldrainApiService.deleteFile(apiKey, fileId)) {
                is ApiResponse.Success -> {
                    _uiState.update {
                        it.copy(
                            isLoadingDeleteFile = false,
                            showDeleteConfirmDialog = false,
                            fileIdToDelete = null,
                            deleteFileSuccessMessage = response.data.message ?: "File deleted successfully.",
                            userFilesList = _uiState.value.userFilesList.filterNot { listItem -> listItem.id == fileId }, 
                            fileInfo = if (_uiState.value.fileInfo?.id == fileId) null else _uiState.value.fileInfo
                        )
                    }
                }
                is ApiResponse.Error -> {
                    _uiState.update {
                        it.copy(
                            isLoadingDeleteFile = false,
                            showDeleteConfirmDialog = false, 
                            fileIdToDelete = null, 
                            deleteFileErrorMessage = response.errorDetails.message ?: response.errorDetails.value ?: "Unknown error deleting file."
                        )
                    }
                }
            }
        }
    }

    fun clearDeleteMessages() {
        _uiState.update { it.copy(deleteFileSuccessMessage = null, deleteFileErrorMessage = null) }
    }
    fun clearApiKeyMissingError() {
        _uiState.update { it.copy(apiKeyMissingError = false, userFilesListErrorMessage = if(it.userFilesListErrorMessage?.contains("API Key is missing") == true) null else it.userFilesListErrorMessage) }
    }
}
