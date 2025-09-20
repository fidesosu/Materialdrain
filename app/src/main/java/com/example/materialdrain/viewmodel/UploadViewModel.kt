package com.example.materialdrain.viewmodel

import android.app.Application
import android.content.Context
import android.media.MediaMetadataRetriever
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
    val selectedFileTextContent: String? = null, // For text file preview
    // Audio specific fields
    val audioDurationMillis: Long? = null,
    val audioBitrate: Int? = null,
    val audioArtist: String? = null,
    val audioAlbum: String? = null,
    val audioAlbumArt: ByteArray? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as UploadUiState

        if (isLoading != other.isLoading) return false
        if (uploadResult != other.uploadResult) return false
        if (errorMessage != other.errorMessage) return false
        if (selectedFileName != other.selectedFileName) return false
        if (uploadTotalSizeBytes != other.uploadTotalSizeBytes) return false
        if (uploadedBytes != other.uploadedBytes) return false
        if (textToUpload != other.textToUpload) return false
        if (selectedFileUri != other.selectedFileUri) return false
        if (selectedFileMimeType != other.selectedFileMimeType) return false
        if (selectedFileTextContent != other.selectedFileTextContent) return false
        if (audioDurationMillis != other.audioDurationMillis) return false
        if (audioBitrate != other.audioBitrate) return false
        if (audioArtist != other.audioArtist) return false
        if (audioAlbum != other.audioAlbum) return false
        if (audioAlbumArt != null) {
            if (other.audioAlbumArt == null) return false
            if (!audioAlbumArt.contentEquals(other.audioAlbumArt)) return false
        } else if (other.audioAlbumArt != null) return false

        return true
    }

    override fun hashCode(): Int {
        var result = isLoading.hashCode()
        result = 31 * result + (uploadResult?.hashCode() ?: 0)
        result = 31 * result + (errorMessage?.hashCode() ?: 0)
        result = 31 * result + (selectedFileName?.hashCode() ?: 0)
        result = 31 * result + (uploadTotalSizeBytes?.hashCode() ?: 0)
        result = 31 * result + uploadedBytes.hashCode()
        result = 31 * result + textToUpload.hashCode()
        result = 31 * result + (selectedFileUri?.hashCode() ?: 0)
        result = 31 * result + (selectedFileMimeType?.hashCode() ?: 0)
        result = 31 * result + (selectedFileTextContent?.hashCode() ?: 0)
        result = 31 * result + (audioDurationMillis?.hashCode() ?: 0)
        result = 31 * result + (audioBitrate ?: 0)
        result = 31 * result + (audioArtist?.hashCode() ?: 0)
        result = 31 * result + (audioAlbum?.hashCode() ?: 0)
        result = 31 * result + (audioAlbumArt?.contentHashCode() ?: 0)
        return result
    }
}

class UploadViewModel(
    private val application: Application,
    private val pixeldrainApiService: PixeldrainApiService
) : ViewModel() {

    private val _uiState = MutableStateFlow(UploadUiState())
    val uiState: StateFlow<UploadUiState> = _uiState.asStateFlow()

    private var apiKey: String = ""

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

        var newAudioDurationMillis: Long? = null
        var newAudioBitrate: Int? = null
        var newAudioArtist: String? = null
        var newAudioAlbum: String? = null
        var newAudioAlbumArt: ByteArray? = null

        if (uri != null) {
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

                if (newMimeType != null &&
                    (newMimeType.startsWith("text/") ||
                     newMimeType == "application/json" ||
                     newMimeType == "application/xml" ||
                     newMimeType == "application/javascript" ||
                     newMimeType == "application/rss+xml" ||
                     newMimeType == "application/atom+xml" ||
                     (newMimeType == "application/octet-stream" &&
                      (newFileName?.endsWith(".txt", true) == true ||
                       newFileName?.endsWith(".log", true) == true ||
                       newFileName?.endsWith(".ini", true) == true ||
                       newFileName?.endsWith(".xml", true) == true ||
                       newFileName?.endsWith(".json", true) == true ||
                       newFileName?.endsWith(".js", true) == true ||
                       newFileName?.endsWith(".config", true) == true ||
                       newFileName?.endsWith(".md", true) == true ||
                       newFileName?.endsWith(".csv", true) == true)))
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
                        newErrorMessage = (newErrorMessage ?: "") + " Could not read file for preview."
                    }
                }

                if (newMimeType?.startsWith("audio/") == true) {
                    val retriever = MediaMetadataRetriever()
                    try {
                        retriever.setDataSource(context, uri)
                        retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()?.let {
                            newAudioDurationMillis = it
                        }
                        retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toIntOrNull()?.let {
                            newAudioBitrate = it
                        }
                        newAudioArtist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                        newAudioAlbum = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
                        newAudioAlbumArt = retriever.embeddedPicture
                    } catch (e: Exception) {
                        Log.e(TAG, "Error extracting audio metadata: ${e.message}", e)
                        newErrorMessage = (newErrorMessage ?: "") + " Could not retrieve audio metadata."
                    } finally {
                        retriever.release()
                    }
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error querying file details: ${e.message}", e)
                newFileName = "Error reading file"
                newFileSizeBytes = null
                newMimeType = null
                newErrorMessage = (newErrorMessage ?: "") + " Error accessing file details."
            }
            _uiState.update {
                it.copy(
                    selectedFileName = newFileName,
                    uploadTotalSizeBytes = newFileSizeBytes, // Initialize total size here
                    selectedFileUri = uri,
                    selectedFileMimeType = newMimeType,
                    selectedFileTextContent = newTextContent,
                    textToUpload = "",
                    errorMessage = newErrorMessage ?: it.errorMessage,
                    uploadResult = null,
                    uploadedBytes = 0L, // Reset progress
                    audioDurationMillis = if (newMimeType?.startsWith("audio/") == true) newAudioDurationMillis else null,
                    audioBitrate = if (newMimeType?.startsWith("audio/") == true) newAudioBitrate else null,
                    audioArtist = if (newMimeType?.startsWith("audio/") == true) newAudioArtist else null,
                    audioAlbum = if (newMimeType?.startsWith("audio/") == true) newAudioAlbum else null,
                    audioAlbumArt = if (newMimeType?.startsWith("audio/") == true) newAudioAlbumArt else null
                )
            }
        } else {
            _uiState.update {
                it.copy(
                    selectedFileName = null,
                    uploadTotalSizeBytes = null,
                    selectedFileUri = null,
                    selectedFileMimeType = null,
                    selectedFileTextContent = null,
                    errorMessage = null,
                    uploadResult = null,
                    uploadedBytes = 0L,
                    audioDurationMillis = null,
                    audioBitrate = null,
                    audioArtist = null,
                    audioAlbum = null,
                    audioAlbumArt = null
                )
            }
        }
    }

    fun onTextToUploadChanged(newText: String) {
        var newTextSizeBytes: Long? = null
        if (newText.isNotBlank()) {
            newTextSizeBytes = newText.toByteArray().size.toLong()
            _uiState.update {
                it.copy(
                    textToUpload = newText,
                    selectedFileName = null,
                    uploadTotalSizeBytes = newTextSizeBytes, // Initialize total size for text
                    selectedFileUri = null,
                    selectedFileMimeType = null,
                    selectedFileTextContent = null,
                    errorMessage = null,
                    uploadResult = null,
                    uploadedBytes = 0L, // Reset progress
                    audioDurationMillis = null,
                    audioBitrate = null,
                    audioArtist = null,
                    audioAlbum = null,
                    audioAlbumArt = null
                )
            }
        } else {
             _uiState.update {
                it.copy(
                    textToUpload = newText,
                    uploadTotalSizeBytes = if(it.selectedFileUri != null) it.uploadTotalSizeBytes else null,
                    errorMessage = null,
                    uploadResult = null,
                    uploadedBytes = 0L
                )
            }
        }
    }

    fun clearApiKeyError() {
        if (_uiState.value.errorMessage?.contains("API Key") == true) {
            _uiState.update { it.copy(errorMessage = null) }
        }
    }

    fun updateApiKey(newApiKey: String) {
        apiKey = newApiKey
        if (newApiKey.isNotBlank()) {
            clearApiKeyError()
        }
    }

    fun clearUploadResult() {
        _uiState.update { it.copy(uploadResult = null, isLoading = false, uploadedBytes = 0L) }
    }

    fun upload() {
        val currentTextToUpload = _uiState.value.textToUpload
        val currentSelectedFileUri = _uiState.value.selectedFileUri

        if (apiKey.isBlank()) {
            _uiState.update {
                it.copy(
                    isLoading = false,
                    errorMessage = "API Key is missing. Please set it in Settings.",
                    // Clear fields that indicate a pending upload
                    selectedFileName = null,
                    uploadTotalSizeBytes = null,
                    selectedFileUri = null,
                    selectedFileMimeType = null,
                    selectedFileTextContent = null,
                    textToUpload = "",
                    audioDurationMillis = null,
                    audioBitrate = null,
                    audioArtist = null,
                    audioAlbum = null,
                    audioAlbumArt = null
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
                    textToUpload = "",
                    audioDurationMillis = null,
                    audioBitrate = null,
                    audioArtist = null,
                    audioAlbum = null,
                    audioAlbumArt = null
                )
            }
            return
        }

        // Ensure total size is set correctly before starting the upload
        val totalBytesForUpload = if (currentSelectedFileUri != null) {
            _uiState.value.uploadTotalSizeBytes // Should have been set by onFileSelected
        } else if (currentTextToUpload.isNotBlank()) {
            currentTextToUpload.toByteArray().size.toLong()
        } else {
            null
        }

        _uiState.update { it.copy(isLoading = true, errorMessage = null, uploadResult = null, uploadedBytes = 0L, uploadTotalSizeBytes = totalBytesForUpload) }
        Log.d(TAG, "Upload started. isLoading: true, uploadedBytes: 0, totalBytes: $totalBytesForUpload")


        viewModelScope.launch {
            val currentFileNameForUpload = _uiState.value.selectedFileName
            var operationType = "unknown"

            val progressCallback: (bytesSent: Long, totalBytes: Long?) -> Unit = { bytesSent, receivedTotalBytes ->
                _uiState.update {
                    it.copy(
                        uploadedBytes = bytesSent,
                        // Prefer totalBytes from Ktor's callback if available, otherwise stick to what was initially set
                        uploadTotalSizeBytes = receivedTotalBytes ?: it.uploadTotalSizeBytes
                    )
                }
                Log.d(TAG, "Upload progress: $bytesSent / ${receivedTotalBytes ?: _uiState.value.uploadTotalSizeBytes}")
            }

            val response: FileUploadResponse? = try {
                if (currentSelectedFileUri != null) {
                    operationType = "file from URI"
                    val fileName = currentFileNameForUpload ?: "pixeldrain_upload_${System.currentTimeMillis()}"
                    pixeldrainApiService.uploadFileFromUri(apiKey, fileName, currentSelectedFileUri, application, progressCallback)
                } else if (currentTextToUpload.isNotBlank()) {
                    operationType = "text"
                    val fileName = "text_upload_${System.currentTimeMillis()}.txt"
                    pixeldrainApiService.uploadFile(apiKey, fileName, currentTextToUpload.toByteArray(), progressCallback)
                } else {
                    null
                }
            } catch (e: Exception) {
                Log.e(TAG, "Upload failed for $operationType. Exception: ${e.message}", e)
                _uiState.update {
                    it.copy(isLoading = false, errorMessage = "Upload failed: ${e.message ?: "Unknown error"}")
                    // Don't reset uploadedBytes here, so user sees the last progress before failure
                }
                null
            }

            if (response != null) {
                if (response.success) {
                    _uiState.update {
                        // On success, set uploadedBytes to total to show 100%
                        val finalTotalBytes = it.uploadTotalSizeBytes
                        it.copy(
                            isLoading = false,
                            uploadResult = response,
                            errorMessage = null,
                            selectedFileName = null,
                            uploadTotalSizeBytes = null,
                            uploadedBytes = finalTotalBytes ?: 0L, // Mark as complete
                            textToUpload = "",
                            selectedFileUri = null,
                            selectedFileMimeType = null,
                            selectedFileTextContent = null,
                            audioDurationMillis = null,
                            audioBitrate = null,
                            audioArtist = null,
                            audioAlbum = null,
                            audioAlbumArt = null
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            uploadResult = response,
                            errorMessage = response.message ?: response.value ?: "Upload failed with no specific message."
                            // Keep uploadedBytes as is on failure to show last progress
                        )
                    }
                }
            } else { // response is null (likely due to an exception caught above)
                if (_uiState.value.isLoading) {
                     _uiState.update {
                        it.copy(isLoading = false, errorMessage = it.errorMessage ?: "Upload did not return a response.")
                         // Keep uploadedBytes as is
                    }
                }
            }
        }
    }
}
