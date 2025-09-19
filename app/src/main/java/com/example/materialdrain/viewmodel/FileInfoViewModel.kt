package com.example.materialdrain.viewmodel

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

data class FileInfoUiState(
    // For single file info
    val isLoadingFileInfo: Boolean = false,
    val fileInfo: FileInfoResponse? = null,
    val fileInfoErrorMessage: String? = null,
    val fileIdInput: String = "",

    // For user's list of files
    val userFilesList: List<FileInfoResponse> = emptyList(),
    val isLoadingUserFiles: Boolean = false,
    val userFilesListErrorMessage: String? = null
)

class FileInfoViewModel(
    private val pixeldrainApiService: PixeldrainApiService
) : ViewModel() {

    private val _uiState = MutableStateFlow(FileInfoUiState())
    val uiState: StateFlow<FileInfoUiState> = _uiState.asStateFlow()

    // --- Single File Info Functions ---
    fun onFileIdInputChange(newFileId: String) {
        _uiState.update {
            it.copy(
                fileIdInput = newFileId,
                fileInfo = null, // Clear previous info when ID changes
                fileInfoErrorMessage = null
            )
        }
    }

    fun fetchFileInfo() {
        val fileId = _uiState.value.fileIdInput
        if (fileId.isBlank()) {
            _uiState.update { it.copy(fileInfoErrorMessage = "Please enter a File ID.") }
            return
        }

        _uiState.update { it.copy(isLoadingFileInfo = true, fileInfo = null, fileInfoErrorMessage = null) }

        viewModelScope.launch {
            when (val response = pixeldrainApiService.getFileInfo(fileId)) {
                is ApiResponse.Success -> {
                    _uiState.update {
                        it.copy(
                            isLoadingFileInfo = false,
                            fileInfo = response.data
                        )
                    }
                }
                is ApiResponse.Error -> {
                    _uiState.update {
                        it.copy(
                            isLoadingFileInfo = false,
                            fileInfoErrorMessage = response.errorDetails.message ?: response.errorDetails.value ?: "Unknown error fetching file info"
                        )
                    }
                }
            }
        }
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

    fun clearFileInfoError() {
        _uiState.update { it.copy(fileInfoErrorMessage = null) }
    }

    // --- User Files List Functions ---
    fun fetchUserFiles(apiKey: String) {
        if (apiKey.isBlank()) {
            _uiState.update {
                it.copy(
                    userFilesListErrorMessage = "API Key is required to fetch user files.",
                    isLoadingUserFiles = false,
                    userFilesList = emptyList() // Ensure list is cleared
                )
            }
            return
        }

        _uiState.update { it.copy(isLoadingUserFiles = true, userFilesList = emptyList(), userFilesListErrorMessage = null) }

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
                    _uiState.update {
                        it.copy(
                            isLoadingUserFiles = false,
                            userFilesListErrorMessage = response.errorDetails.message ?: response.errorDetails.value ?: "Unknown error fetching user files"
                        )
                    }
                }
            }
        }
    }

    fun clearUserFilesError() {
        _uiState.update { it.copy(userFilesListErrorMessage = null) }
    }

    fun clearUserFilesList() {
         _uiState.update {
            it.copy(
                userFilesList = emptyList(),
                userFilesListErrorMessage = null,
                isLoadingUserFiles = false
            )
        }
    }
}
