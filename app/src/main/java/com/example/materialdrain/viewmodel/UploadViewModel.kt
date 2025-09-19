package com.example.materialdrain.viewmodel

import android.app.Application
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.materialdrain.network.FileUploadResponse
import com.example.materialdrain.network.PixeldrainApiService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// SharedPreferences constants
private const val PREFS_NAME = "pixeldrain_prefs"
private const val API_KEY_PREF = "api_key"
private const val TAG = "PIXEL_VM_DEBUG" // Tag for Logcat

data class UploadUiState(
    val isLoading: Boolean = false,
    val uploadResult: FileUploadResponse? = null,
    val errorMessage: String? = null,
    val selectedFileName: String? = null,
    val uploadTotalSizeBytes: Long? = null,
    val uploadedBytes: Long = 0L, // Will remain 0 during upload now
    val textToUpload: String = "" 
)

class UploadViewModel(
    private val application: Application,
    private val pixeldrainApiService: PixeldrainApiService
) : ViewModel() {

    private val _uiState = MutableStateFlow(UploadUiState())
    val uiState: StateFlow<UploadUiState> = _uiState.asStateFlow()

    private var apiKey: String = ""
    private var selectedFileUri: Uri? = null

    init {
        Log.d(TAG, "ViewModel initialized")
        loadApiKey()
    }

    private fun loadApiKey() {
        val sharedPrefs = application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        apiKey = sharedPrefs.getString(API_KEY_PREF, "") ?: ""
        Log.d(TAG, "API Key loaded: ${if (apiKey.isNotBlank()) "Present" else "Missing"}")
    }

    fun onFileSelected(uri: Uri?, context: Context) {
        var newFileName: String? = null
        var newFileSizeBytes: Long? = null
        var newTextToUpload = _uiState.value.textToUpload

        if (uri != null) {
            newTextToUpload = "" 
            selectedFileUri = uri
            try {
                context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                        newFileName = cursor.getString(nameIndex)
                        if (!cursor.isNull(sizeIndex)) {
                            newFileSizeBytes = cursor.getLong(sizeIndex)
                        } else {
                            Log.w(TAG, "File size is unavailable for URI: $uri")
                            newFileSizeBytes = null
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error querying file details: ${e.message}", e)
                newFileName = "Error reading file"
                newFileSizeBytes = null
            }
        } else { 
            selectedFileUri = null
        }

        Log.d(TAG, "File selected: $newFileName, Uri: $uri, SizeBytes: $newFileSizeBytes")
        _uiState.update {
            it.copy(
                selectedFileName = newFileName,
                textToUpload = newTextToUpload,
                uploadTotalSizeBytes = if (uri != null) newFileSizeBytes else (if (it.textToUpload.isBlank()) null else it.uploadTotalSizeBytes),
                errorMessage = null,
                uploadResult = null,
                uploadedBytes = 0L // Reset progress/uploaded bytes
            )
        }
    }

    fun onTextToUploadChanged(newText: String) {
        var newTextSizeBytes: Long? = null
        var newSelectedFileName: String? = _uiState.value.selectedFileName

        if (newText.isNotBlank()) {
            if (selectedFileUri != null) {
                selectedFileUri = null
                newSelectedFileName = null
            }
            newTextSizeBytes = newText.toByteArray().size.toLong()
        }

        _uiState.update {
            it.copy(
                textToUpload = newText,
                selectedFileName = newSelectedFileName,
                uploadTotalSizeBytes = if (newText.isNotBlank()) newTextSizeBytes else (if (selectedFileUri == null) null else it.uploadTotalSizeBytes),
                errorMessage = null,
                uploadResult = null,
                uploadedBytes = 0L // Reset progress/uploaded bytes
            )
        }
    }

    fun clearApiKeyError() {
        if (_uiState.value.errorMessage?.contains("API Key") == true) {
            Log.d(TAG, "Clearing API Key error message.")
            _uiState.update { it.copy(errorMessage = null) }
        }
    }

    fun updateApiKey(newApiKey: String) {
        apiKey = newApiKey
        Log.d(TAG, "API Key updated in ViewModel.")
        if (newApiKey.isNotBlank()) {
            clearApiKeyError()
        }
    }

    fun clearUploadResult() {
        _uiState.update { it.copy(uploadResult = null, uploadedBytes = 0L) }
    }

    fun upload() {
        val currentTextToUpload = _uiState.value.textToUpload

        if (apiKey.isBlank()) {
            _uiState.update {
                it.copy(
                    isLoading = false,
                    errorMessage = "API Key is missing. Please set it in Settings.",
                    selectedFileName = null,
                    uploadTotalSizeBytes = null,
                    uploadedBytes = 0L,
                    textToUpload = ""
                )
            }
            selectedFileUri = null
            return
        }

        if (selectedFileUri == null && currentTextToUpload.isBlank()) {
            _uiState.update {
                it.copy(
                    isLoading = false,
                    errorMessage = "No file selected or text provided.",
                    selectedFileName = null,
                    uploadTotalSizeBytes = null,
                    uploadedBytes = 0L,
                    textToUpload = ""
                )
            }
            selectedFileUri = null
            return
        }

        _uiState.update { it.copy(isLoading = true, errorMessage = null, uploadResult = null, uploadedBytes = 0L) }
        Log.d(TAG, "Upload started, isLoading set to true, uploadedBytes reset.")

        viewModelScope.launch {
            val currentSelectedUri = selectedFileUri
            val currentFileNameForUpload = _uiState.value.selectedFileName
            var operationType = "unknown"

            // Removed progressCallback

            val response: FileUploadResponse? = try {
                if (currentSelectedUri != null) {
                    operationType = "file from URI"
                    val fileName = currentFileNameForUpload ?: "pixeldrain_upload_${System.currentTimeMillis()}"
                    Log.d(TAG, "Attempting to upload file from URI: $fileName")
                    // Call service method without progress callback
                    pixeldrainApiService.uploadFileFromUri(apiKey, fileName, currentSelectedUri, application)
                } else if (currentTextToUpload.isNotBlank()) {
                    operationType = "text"
                    val fileName = "text_upload_${System.currentTimeMillis()}.txt"
                    Log.d(TAG, "Attempting to upload text.")
                    // Call service method without progress callback
                    pixeldrainApiService.uploadFile(apiKey, fileName, currentTextToUpload.toByteArray())
                } else {
                    null // Should not happen due to earlier checks
                }
            } catch (e: Exception) {
                Log.e(TAG, "Upload failed for $operationType. Exception: ${e.message}", e)
                _uiState.update {
                    it.copy(isLoading = false, errorMessage = "Upload failed: ${e.message ?: "Unknown error"}", uploadedBytes = 0L)
                }
                null
            }

            if (response != null) {
                if (response.success) {
                    _uiState.update {
                        val finalTotalBytes = it.uploadTotalSizeBytes // Keep the initially determined total size
                        it.copy(
                            isLoading = false,
                            uploadResult = response,
                            errorMessage = null,
                            selectedFileName = null,
                            uploadTotalSizeBytes = null, // Clear total size after successful upload
                            uploadedBytes = finalTotalBytes ?: 0L, // Show full size as 'uploaded' on success
                            textToUpload = ""
                        )
                    }
                    selectedFileUri = null
                } else {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            uploadResult = response,
                            errorMessage = response.message ?: response.value ?: "Upload failed with no specific message.",
                            uploadedBytes = 0L // Reset on failure
                        )
                    }
                }
            } else {
                if (_uiState.value.isLoading) {
                     _uiState.update {
                        it.copy(isLoading = false, errorMessage = it.errorMessage ?: "Upload did not return a response.", uploadedBytes = 0L)
                    }
                }
            }
        }
    }
}
