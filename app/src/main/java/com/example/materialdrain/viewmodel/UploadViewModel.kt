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
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader

// SharedPreferences constants
private const val PREFS_NAME = "pixeldrain_prefs"
private const val API_KEY_PREF = "api_key"
private const val TAG = "PIXEL_VM_DEBUG" // Tag for Logcat
private const val TEXT_PREVIEW_MAX_LENGTH = 4096 // Max 4KB for text preview

data class UploadUiState(
    val isLoading: Boolean = false,
    val uploadResult: FileUploadResponse? = null,
    val errorMessage: String? = null,
    val selectedFileName: String? = null,
    val uploadTotalSizeBytes: Long? = null,
    val uploadedBytes: Long = 0L,
    val textToUpload: String = "",
    val selectedFileUri: Uri? = null,      // For preview purposes
    val selectedFileMimeType: String? = null, // For preview type determination
    val selectedFileTextContent: String? = null // For text file preview
)

class UploadViewModel(
    private val application: Application,
    private val pixeldrainApiService: PixeldrainApiService
) : ViewModel() {

    private val _uiState = MutableStateFlow(UploadUiState())
    val uiState: StateFlow<UploadUiState> = _uiState.asStateFlow()

    private var apiKey: String = ""
    // private var selectedFileUriInternal: Uri? = null // Replaced by selectedFileUri in UiState

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
        var newMimeType: String? = null
        var newTextContent: String? = null
        var newErrorMessage: String? = null

        if (uri != null) {
            // selectedFileUriInternal = uri // Store internal reference for upload
            try {
                newMimeType = context.contentResolver.getType(uri)
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

                // Try to read text content for preview if it seems like a text file
                if (newMimeType != null && 
                    (newMimeType.startsWith("text/") || 
                     newMimeType == "application/json" || 
                     newMimeType == "application/xml" ||
                     newMimeType == "application/javascript" || 
                     newMimeType == "application/rss+xml" || 
                     newMimeType == "application/atom+xml" || 
                     // common config/log file extensions often default to octet-stream
                     (newMimeType == "application/octet-stream" && 
                      (newFileName?.endsWith(".txt", true) == true || 
                       newFileName?.endsWith(".log", true) == true || 
                       newFileName?.endsWith(".ini", true) == true || 
                       newFileName?.endsWith(".xml", true) == true || 
                       newFileName?.endsWith(".json", true) == true || 
                       newFileName?.endsWith(".js", true) == true || 
                       newFileName?.endsWith(".config", true) == true || 
                       newFileName?.endsWith(".md", true) == true || 
                       newFileName?.endsWith(".csv", true) == true)) 
                    )
                ) {
                    try {
                        context.contentResolver.openInputStream(uri)?.use { inputStream ->
                            BufferedReader(InputStreamReader(inputStream)).use { reader ->
                                val charBuffer = CharArray(TEXT_PREVIEW_MAX_LENGTH)
                                val bytesRead = reader.read(charBuffer)
                                if (bytesRead > 0) {
                                    newTextContent = String(charBuffer, 0, bytesRead)
                                }
                            }
                        }
                    } catch (e: IOException) {
                        Log.e(TAG, "Error reading text content for preview: ${e.message}", e)
                        newErrorMessage = "Could not read file for preview."
                    }
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error querying file details: ${e.message}", e)
                newFileName = "Error reading file"
                newFileSizeBytes = null
                newMimeType = null
                newErrorMessage = "Error accessing file details."
            }
            _uiState.update {
                it.copy(
                    selectedFileName = newFileName,
                    uploadTotalSizeBytes = newFileSizeBytes,
                    selectedFileUri = uri, // Update URI in state
                    selectedFileMimeType = newMimeType,
                    selectedFileTextContent = newTextContent, 
                    textToUpload = "", // Clear text upload field
                    errorMessage = newErrorMessage ?: it.errorMessage, // Preserve existing error unless new one occurs
                    uploadResult = null,
                    uploadedBytes = 0L
                )
            }
        } else {
            // selectedFileUriInternal = null
            _uiState.update {
                it.copy(
                    selectedFileName = null,
                    uploadTotalSizeBytes = null,
                    selectedFileUri = null,
                    selectedFileMimeType = null,
                    selectedFileTextContent = null,
                    errorMessage = null, // Clear errors when file is deselected
                    uploadResult = null,
                    uploadedBytes = 0L
                )
            }
        }
        Log.d(TAG, "File selected: Name: $newFileName, Uri: $uri, Size: $newFileSizeBytes, MIME: $newMimeType, TextPreview: ${newTextContent != null}")
    }

    fun onTextToUploadChanged(newText: String) {
        var newTextSizeBytes: Long? = null
        if (newText.isNotBlank()) {
            newTextSizeBytes = newText.toByteArray().size.toLong()
            // If user starts typing text, clear file selection for preview
            _uiState.update {
                it.copy(
                    textToUpload = newText,
                    selectedFileName = null,
                    uploadTotalSizeBytes = newTextSizeBytes,
                    selectedFileUri = null,
                    selectedFileMimeType = null,
                    selectedFileTextContent = null,
                    errorMessage = null,
                    uploadResult = null,
                    uploadedBytes = 0L
                )
            }
        } else {
            // Text field cleared, potentially revert to showing file info if a file was selected before typing
            // For simplicity, we just clear the text and its size. If a file was selected, it remains in selectedFileUri.
             _uiState.update {
                it.copy(
                    textToUpload = newText,
                    uploadTotalSizeBytes = if(it.selectedFileUri != null) it.uploadTotalSizeBytes else null, // Keep file size if file still selected
                    errorMessage = null,
                    uploadResult = null,
                    uploadedBytes = 0L
                )
            }
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
        val currentSelectedFileUri = _uiState.value.selectedFileUri // Use URI from UiState

        if (apiKey.isBlank()) {
            _uiState.update {
                it.copy(
                    isLoading = false,
                    errorMessage = "API Key is missing. Please set it in Settings.",
                    // Clear file selection on API key error as well
                    selectedFileName = null, 
                    uploadTotalSizeBytes = null,
                    selectedFileUri = null,
                    selectedFileMimeType = null,
                    selectedFileTextContent = null,
                    textToUpload = ""
                )
            }
            return
        }

        if (currentSelectedFileUri == null && currentTextToUpload.isBlank()) {
            _uiState.update {
                it.copy(
                    isLoading = false,
                    errorMessage = "No file selected or text provided.",
                     selectedFileName = null, 
                    uploadTotalSizeBytes = null,
                    selectedFileUri = null,
                    selectedFileMimeType = null,
                    selectedFileTextContent = null,
                    textToUpload = ""
                )
            }
            return
        }

        _uiState.update { it.copy(isLoading = true, errorMessage = null, uploadResult = null, uploadedBytes = 0L) }
        Log.d(TAG, "Upload started, isLoading set to true, uploadedBytes reset.")

        viewModelScope.launch {
            val currentFileNameForUpload = _uiState.value.selectedFileName
            var operationType = "unknown"

            val response: FileUploadResponse? = try {
                if (currentSelectedFileUri != null) {
                    operationType = "file from URI"
                    val fileName = currentFileNameForUpload ?: "pixeldrain_upload_${System.currentTimeMillis()}"
                    Log.d(TAG, "Attempting to upload file from URI: $fileName")
                    pixeldrainApiService.uploadFileFromUri(apiKey, fileName, currentSelectedFileUri, application)
                } else if (currentTextToUpload.isNotBlank()) {
                    operationType = "text"
                    val fileName = "text_upload_${System.currentTimeMillis()}.txt"
                    Log.d(TAG, "Attempting to upload text.")
                    pixeldrainApiService.uploadFile(apiKey, fileName, currentTextToUpload.toByteArray())
                } else {
                    null 
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
                        val finalTotalBytes = it.uploadTotalSizeBytes 
                        it.copy(
                            isLoading = false,
                            uploadResult = response,
                            errorMessage = null,
                            selectedFileName = null,
                            uploadTotalSizeBytes = null, 
                            uploadedBytes = finalTotalBytes ?: 0L, 
                            textToUpload = "",
                            selectedFileUri = null, // Clear URI after successful upload
                            selectedFileMimeType = null,
                            selectedFileTextContent = null
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            uploadResult = response,
                            errorMessage = response.message ?: response.value ?: "Upload failed with no specific message.",
                            uploadedBytes = 0L
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
