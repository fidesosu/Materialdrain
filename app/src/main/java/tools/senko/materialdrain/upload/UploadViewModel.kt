package tools.senko.materialdrain.upload

import android.app.Application
import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap // Added for video thumbnail
import android.graphics.drawable.BitmapDrawable
import android.graphics.pdf.PdfRenderer
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.ByteArrayOutputStream // Added for video thumbnail
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStreamReader
import tools.senko.materialdrain.provider.ProviderConfigStore
import tools.senko.materialdrain.provider.ProviderRegistry
import tools.senko.materialdrain.provider.api.ApiResponse
import tools.senko.materialdrain.provider.api.ProviderKind
import tools.senko.materialdrain.provider.pixeldrain.internal.PixeldrainCoreApi
import tools.senko.materialdrain.auth.SessionManager
import tools.senko.materialdrain.transfer.TransferInfo
import tools.senko.materialdrain.transfer.TransferKind
import tools.senko.materialdrain.transfer.TransferOutcome
import tools.senko.materialdrain.transfer.TransferRegistry
import tools.senko.materialdrain.transfer.TransferSpeedTracker
import tools.senko.materialdrain.transfer.estimateEtaSeconds
import tools.senko.materialdrain.util.readContentUriInfo

// SharedPreferences constants
private const val TAG = "PIXEL_VM_DEBUG" // Tag for Logcat
private const val TEXT_PREVIEW_MAX_LENGTH = 4096 // Max 4KB for text preview
private const val VIDEO_THUMBNAIL_QUALITY = 75 // JPEG quality for video thumbnails
private const val VIDEO_THUMBNAIL_TARGET_TIME_US = 1_000_000L // 1 second for thumbnail
private const val APK_THUMBNAIL_QUALITY = 75 // JPEG quality for APK icons
private const val MAX_PARALLEL_UPLOADS = 3

enum class UploadItemStatus { PENDING, UPLOADING, DONE, FAILED }

data class UploadItem(
    val id: Int,
    val uri: Uri,
    val name: String,
    val sizeBytes: Long?,
    val mimeType: String?,
    val status: UploadItemStatus = UploadItemStatus.PENDING,
    val uploadedBytes: Long = 0L,
    val fileId: String? = null,
    val errorMessage: String? = null
)

data class UploadUiState(
    val isLoading: Boolean = false,
    val uploadResult: UploadResult? = null,
    val errorMessage: String? = null,
    val selectedFileName: String? = null,
    val uploadTotalSizeBytes: Long? = null,
    val uploadedBytes: Long = 0L,
    val uploadSpeedBytesPerSec: Long = 0L,
    val uploadEtaSeconds: Long? = null,
    // Multi-file selection (used when more than one file is picked)
    val queuedItems: List<UploadItem> = emptyList(),
    val textToUpload: String = "",
    val selectedFileUri: Uri? = null,      // For preview purposes
    val selectedFileMimeType: String? = null, // For preview type determination
    val selectedFileTextContent: String? = null, // For text file preview
    // Audio specific fields
    val audioDurationMillis: Long? = null,
    val audioBitrate: Int? = null,
    val audioArtist: String? = null,
    val audioAlbum: String? = null,
    val audioAlbumArt: ByteArray? = null,
    // Video specific fields
    val videoThumbnail: ByteArray? = null,
    val videoDurationMillis: Long? = null,
    // PDF specific fields
    val pdfPageCount: Int? = null,
    // APK specific fields
    val apkVersionName: String? = null,
    val apkPackageName: String? = null,
    val apkIcon: ByteArray? = null
) {
    val hasUploadable: Boolean
        get() = selectedFileUri != null || textToUpload.isNotBlank() ||
            queuedItems.any { it.status == UploadItemStatus.PENDING || it.status == UploadItemStatus.FAILED }

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
        if (uploadSpeedBytesPerSec != other.uploadSpeedBytesPerSec) return false
        if (uploadEtaSeconds != other.uploadEtaSeconds) return false
        if (queuedItems != other.queuedItems) return false
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
        if (videoDurationMillis != other.videoDurationMillis) return false
        if (videoThumbnail != null) {
            if (other.videoThumbnail == null) return false
            if (!videoThumbnail.contentEquals(other.videoThumbnail)) return false
        } else if (other.videoThumbnail != null) return false
        if (pdfPageCount != other.pdfPageCount) return false
        if (apkVersionName != other.apkVersionName) return false
        if (apkPackageName != other.apkPackageName) return false
        if (apkIcon != null) {
            if (other.apkIcon == null) return false
            if (!apkIcon.contentEquals(other.apkIcon)) return false
        } else if (other.apkIcon != null) return false

        return true
    }

    override fun hashCode(): Int {
        var result = isLoading.hashCode()
        result = 31 * result + (uploadResult?.hashCode() ?: 0)
        result = 31 * result + (errorMessage?.hashCode() ?: 0)
        result = 31 * result + (selectedFileName?.hashCode() ?: 0)
        result = 31 * result + (uploadTotalSizeBytes?.hashCode() ?: 0)
        result = 31 * result + uploadedBytes.hashCode()
        result = 31 * result + uploadSpeedBytesPerSec.hashCode()
        result = 31 * result + (uploadEtaSeconds?.hashCode() ?: 0)
        result = 31 * result + queuedItems.hashCode()
        result = 31 * result + textToUpload.hashCode()
        result = 31 * result + (selectedFileUri?.hashCode() ?: 0)
        result = 31 * result + (selectedFileMimeType?.hashCode() ?: 0)
        result = 31 * result + (selectedFileTextContent?.hashCode() ?: 0)
        result = 31 * result + (audioDurationMillis?.hashCode() ?: 0)
        result = 31 * result + (audioBitrate ?: 0)
        result = 31 * result + (audioArtist?.hashCode() ?: 0)
        result = 31 * result + (audioAlbum?.hashCode() ?: 0)
        result = 31 * result + (audioAlbumArt?.contentHashCode() ?: 0)
        result = 31 * result + (videoDurationMillis?.hashCode() ?: 0)
        result = 31 * result + (videoThumbnail?.contentHashCode() ?: 0)
        result = 31 * result + (pdfPageCount ?: 0)
        result = 31 * result + (apkVersionName?.hashCode() ?: 0)
        result = 31 * result + (apkPackageName?.hashCode() ?: 0)
        result = 31 * result + (apkIcon?.contentHashCode() ?: 0)
        return result
    }
}

/** The outcome of one upload, the same for every host. */
data class UploadResult(val success: Boolean, val id: String? = null, val message: String? = null)

class UploadViewModel(
    private val application: Application,
    private val registry: ProviderRegistry,
    private val configStore: ProviderConfigStore,
    private val sessionManager: SessionManager,
    private val transfers: TransferRegistry
) : ViewModel() {

    private fun provider() = registry.resolve(configStore.activeProviderId.value)

    /** Pixeldrain uploads need the login; other hosts take their own credentials. */
    private fun missingKey(): Boolean = provider().kind == ProviderKind.PIXELDRAIN && apiKey.isBlank()

    private suspend fun uploadFile(fileName: String, uri: Uri, onProgress: (Long, Long?) -> Unit): UploadResult {
        val provider = provider()
        val store = provider.fileStore
        val response = if (store != null) {
            store.upload(fileName, uri, application, onProgress)
        } else {
            // No flat storage to upload into (WebDAV, S3, SMB): the Upload screen uploads into the host's root
            // folder instead, through the same browse the Filesystem screen uses
            val browse = provider.browse ?: return UploadResult(false, message = "This host can't receive uploads.")
            val root = provider.rootPath.trim('/')
            browse.upload(if (root.isEmpty()) fileName else "$root/$fileName", uri, application, makeParents = false, onProgress)
        }
        return when (response) {
            is ApiResponse.Success -> UploadResult(true, id = response.data.ref.id)
            is ApiResponse.Error -> UploadResult(false, message = response.error.message)
        }
    }

    /** Text is uploaded as a file: it's written to a temporary file first, which is removed again afterwards. */
    private suspend fun uploadText(fileName: String, text: ByteArray, onProgress: (Long, Long?) -> Unit): UploadResult {
        val file = File(application.cacheDir, fileName).apply { writeBytes(text) }
        return try {
            uploadFile(fileName, Uri.fromFile(file), onProgress)
        } finally {
            file.delete()
        }
    }

    private val _uiState = MutableStateFlow(UploadUiState())
    val uiState: StateFlow<UploadUiState> = _uiState.asStateFlow()

    private var apiKey: String = ""
    private var uploadJob: Job? = null
    private val speedTracker = TransferSpeedTracker()
    private var nextItemId = 0
    private var nextTransferNumber = 0

    init {
        Log.d(TAG, "ViewModel initialized")
        loadApiKey()
    }

    private fun loadApiKey() {
        apiKey = sessionManager.currentApiKey()
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

        var newVideoThumbnail: ByteArray? = null
        var newVideoDurationMillis: Long? = null

        var newPdfPageCount: Int? = null
        var newApkVersionName: String? = null
        var newApkPackageName: String? = null
        var newApkIcon: ByteArray? = null

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
                     (newMimeType == "application/octet-stream" && // For octet-stream, check common text extensions
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

                if (newMimeType?.startsWith("video/") == true) {
                    val retriever = MediaMetadataRetriever()
                    try {
                        retriever.setDataSource(context, uri)
                        newVideoDurationMillis = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
                        var bitmap: Bitmap? = null
                        bitmap = retriever.getFrameAtTime(VIDEO_THUMBNAIL_TARGET_TIME_US, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                        if (bitmap == null && true) {
                            bitmap = retriever.getPrimaryImage()
                        }
                        if (bitmap == null) {
                            bitmap = retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                        }
                        bitmap?.let {
                            val baos = ByteArrayOutputStream()
                            it.compress(Bitmap.CompressFormat.JPEG, VIDEO_THUMBNAIL_QUALITY, baos)
                            newVideoThumbnail = baos.toByteArray()
                            it.recycle()
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error extracting video metadata/thumbnail: ${e.message}", e)
                        newErrorMessage = (newErrorMessage ?: "") + " Could not retrieve video metadata or thumbnail."
                    } finally {
                        retriever.release()
                    }
                }

                if (newMimeType == "application/pdf") {
                    var pfd: ParcelFileDescriptor? = null
                    var renderer: PdfRenderer? = null
                    try {
                        pfd = context.contentResolver.openFileDescriptor(uri, "r")
                        pfd?.let {
                            renderer = PdfRenderer(it)
                            newPdfPageCount = renderer.pageCount
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error getting PDF page count: ${e.message}", e)
                        newErrorMessage = (newErrorMessage ?: "") + " Could not retrieve PDF details."
                    } finally {
                        renderer?.close()
                        pfd?.close()
                    }
                }

                if (newMimeType == "application/vnd.android.package-archive") {
                    var tempApkFile: File? = null
                    try {
                        // PackageManager needs a file path, so copy from URI to a temp file if it's a content URI
                        val apkPath = if (uri.scheme == "content") {
                            val tempFileName = "temp_apk_preview_${System.currentTimeMillis()}.apk"
                            tempApkFile = File(context.cacheDir, tempFileName)
                            context.contentResolver.openInputStream(uri)?.use { input ->
                                FileOutputStream(tempApkFile).use { output ->
                                    input.copyTo(output)
                                }
                            }
                            tempApkFile.absolutePath
                        } else {
                            uri.path // Assume it's already a file path
                        }

                        apkPath?.let {
                            val pm = context.packageManager
                            val packageInfo: PackageInfo? = pm.getPackageArchiveInfo(it, PackageManager.GET_META_DATA)
                            if (packageInfo != null) {
                                newApkVersionName = packageInfo.versionName
                                newApkPackageName = packageInfo.packageName
                                packageInfo.applicationInfo?.let { appInfo ->
                                    // Necessary for getApplicationIcon to work with an archive file
                                    appInfo.sourceDir = apkPath
                                    appInfo.publicSourceDir = apkPath
                                    val iconDrawable = pm.getApplicationIcon(appInfo)
                                    if (iconDrawable is BitmapDrawable) {
                                        val bitmap = iconDrawable.bitmap
                                        val baos = ByteArrayOutputStream()
                                        bitmap.compress(Bitmap.CompressFormat.JPEG, APK_THUMBNAIL_QUALITY, baos)
                                        newApkIcon = baos.toByteArray()
                                        // Bitmap doesn't need explicit recycle here as it's from system
                                    }
                                }
                            } else {
                                newErrorMessage = (newErrorMessage ?: "") + " Could not parse APK details."
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error extracting APK info: ${e.message}", e)
                        newErrorMessage = (newErrorMessage ?: "") + " Could not retrieve APK details."
                    } finally {
                        tempApkFile?.delete() // Clean up temp file
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
                    queuedItems = emptyList(),
                    selectedFileName = newFileName,
                    uploadTotalSizeBytes = newFileSizeBytes,
                    selectedFileUri = uri,
                    selectedFileMimeType = newMimeType,
                    selectedFileTextContent = if (newMimeType?.startsWith("text/") == true || (newMimeType == "application/octet-stream" && newTextContent != null) ) newTextContent else null,
                    textToUpload = "",
                    errorMessage = newErrorMessage ?: it.errorMessage,
                    uploadResult = null,
                    uploadedBytes = 0L,
                    // Audio fields
                    audioDurationMillis = if (newMimeType?.startsWith("audio/") == true) newAudioDurationMillis else null,
                    audioBitrate = if (newMimeType?.startsWith("audio/") == true) newAudioBitrate else null,
                    audioArtist = if (newMimeType?.startsWith("audio/") == true) newAudioArtist else null,
                    audioAlbum = if (newMimeType?.startsWith("audio/") == true) newAudioAlbum else null,
                    audioAlbumArt = if (newMimeType?.startsWith("audio/") == true) newAudioAlbumArt else null,
                    // Video fields
                    videoThumbnail = if (newMimeType?.startsWith("video/") == true) newVideoThumbnail else null,
                    videoDurationMillis = if (newMimeType?.startsWith("video/") == true) newVideoDurationMillis else null,
                    // PDF fields
                    pdfPageCount = if (newMimeType == "application/pdf") newPdfPageCount else null,
                    // APK fields
                    apkVersionName = if (newMimeType == "application/vnd.android.package-archive") newApkVersionName else null,
                    apkPackageName = if (newMimeType == "application/vnd.android.package-archive") newApkPackageName else null,
                    apkIcon = if (newMimeType == "application/vnd.android.package-archive") newApkIcon else null
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
                    queuedItems = emptyList(),
                    errorMessage = null,
                    uploadResult = null,
                    uploadedBytes = 0L,
                    audioDurationMillis = null,
                    audioBitrate = null,
                    audioArtist = null,
                    audioAlbum = null,
                    audioAlbumArt = null,
                    videoThumbnail = null,
                    videoDurationMillis = null,
                    pdfPageCount = null,
                    apkVersionName = null,
                    apkPackageName = null,
                    apkIcon = null
                )
            }
        }
    }

    fun onFilesSelected(uris: List<Uri>, context: Context) {
        when (uris.size) {
            0 -> onFileSelected(null, context)
            1 -> onFileSelected(uris.first(), context)
            else -> viewModelScope.launch {
                val items = withContext(Dispatchers.IO) {
                    uris.map { uri -> readUploadItem(uri, context) }
                }
                _uiState.update {
                    it.copy(
                        queuedItems = items,
                        selectedFileName = null,
                        uploadTotalSizeBytes = null,
                        selectedFileUri = null,
                        selectedFileMimeType = null,
                        selectedFileTextContent = null,
                        textToUpload = "",
                        errorMessage = null,
                        uploadResult = null,
                        uploadedBytes = 0L,
                        audioDurationMillis = null,
                        audioBitrate = null,
                        audioArtist = null,
                        audioAlbum = null,
                        audioAlbumArt = null,
                        videoThumbnail = null,
                        videoDurationMillis = null,
                        pdfPageCount = null,
                        apkVersionName = null,
                        apkPackageName = null,
                        apkIcon = null
                    )
                }
            }
        }
    }

    private fun readUploadItem(uri: Uri, context: Context): UploadItem {
        val info = readContentUriInfo(context, uri)
        return UploadItem(
            id = nextItemId++,
            uri = uri,
            name = info.displayName ?: uri.lastPathSegment ?: "pixeldrain_upload_${System.currentTimeMillis()}",
            sizeBytes = info.sizeBytes,
            mimeType = context.contentResolver.getType(uri)
        )
    }

    fun removeQueuedItem(itemId: Int) {
        if (_uiState.value.isLoading) return
        _uiState.update { it.copy(queuedItems = it.queuedItems.filterNot { item -> item.id == itemId }) }
    }

    fun clearQueuedItems() {
        if (_uiState.value.isLoading) return
        _uiState.update { it.copy(queuedItems = emptyList(), errorMessage = null, uploadedBytes = 0L) }
    }

    fun cancelUpload() {
        uploadJob?.cancel()
    }

    fun onTextToUploadChanged(newText: String) {
        var newTextSizeBytes: Long? = null
        if (newText.isNotBlank()) {
            newTextSizeBytes = newText.toByteArray().size.toLong()
            _uiState.update {
                it.copy(
                    textToUpload = newText,
                    queuedItems = emptyList(),
                    selectedFileName = null,
                    uploadTotalSizeBytes = newTextSizeBytes,
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
                    audioAlbumArt = null,
                    videoThumbnail = null,
                    videoDurationMillis = null,
                    pdfPageCount = null,
                    apkVersionName = null,
                    apkPackageName = null,
                    apkIcon = null
                )
            }
        } else {
             _uiState.update {
                it.copy(
                    textToUpload = newText,
                    uploadTotalSizeBytes = if(it.selectedFileUri != null) it.uploadTotalSizeBytes else null, // Keep existing file size if a file is selected
                    errorMessage = null,
                    uploadResult = null,
                    uploadedBytes = 0L
                    // If clearing text, don't clear selected file info unless explicitly done by onFileSelected(null,...)
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
        _uiState.update { it.copy(uploadResult = null, errorMessage = null, isLoading = false, uploadedBytes = 0L) }
    }

    private fun uploadQueuedItems() {
        val batch = _uiState.value.queuedItems.filter {
            it.status == UploadItemStatus.PENDING || it.status == UploadItemStatus.FAILED
        }
        if (batch.isEmpty()) return
        if (missingKey()) {
            _uiState.update { it.copy(errorMessage = "API Key is missing. Please set it in Settings.") }
            return
        }

        val batchIds = batch.map { it.id }.toSet()
        val batchTotalBytes = batch.sumOf { it.sizeBytes ?: 0L }
        val bytesPerItem = HashMap<Int, Long>()
        val lock = Any()
        speedTracker.reset()

        _uiState.update { s ->
            s.copy(
                isLoading = true,
                errorMessage = null,
                uploadResult = null,
                uploadedBytes = 0L,
                uploadTotalSizeBytes = batchTotalBytes.takeIf { it > 0 },
                uploadSpeedBytesPerSec = 0L,
                uploadEtaSeconds = null,
                queuedItems = s.queuedItems.map {
                    if (it.id in batchIds) it.copy(status = UploadItemStatus.PENDING, uploadedBytes = 0L, errorMessage = null) else it
                }
            )
        }

        val transferId = "upload-batch-${nextTransferNumber++}"
        val knownTotal = batchTotalBytes.takeIf { it > 0 }
        transfers.start(
            TransferInfo(transferId, TransferKind.UPLOAD, if (batch.size == 1) batch.first().name else "${batch.size} files", totalBytes = knownTotal),
            onCancel = { cancelUpload() }
        )

        fun updateItem(itemId: Int, transform: (UploadItem) -> UploadItem) {
            synchronized(lock) {
                val newBytes = bytesPerItem.values.sum()
                val speed = speedTracker.update(newBytes)
                val eta = estimateEtaSeconds(knownTotal, newBytes, speed)
                transfers.progress(transferId, newBytes, knownTotal, speed, eta)
                _uiState.update { s ->
                    s.copy(
                        queuedItems = s.queuedItems.map { if (it.id == itemId) transform(it) else it },
                        uploadedBytes = newBytes,
                        uploadSpeedBytesPerSec = speed,
                        uploadEtaSeconds = eta
                    )
                }
            }
        }

        uploadJob = viewModelScope.launch {
            var outcome = TransferOutcome.FAILED
            var outcomeMessage: String? = null
            try {
                val semaphore = Semaphore(MAX_PARALLEL_UPLOADS)
                batch.map { item ->
                    async {
                        val job = coroutineContext[Job]
                        semaphore.withPermit {
                            updateItem(item.id) { it.copy(status = UploadItemStatus.UPLOADING) }
                            val response = uploadFile(item.name, item.uri) { sent, _ ->
                                // Called from inside the host's sending loop: a cancelled upload stops there
                                if (job?.isActive == false) throw CancellationException("The upload was cancelled")
                                synchronized(lock) { bytesPerItem[item.id] = sent }
                                updateItem(item.id) { it.copy(uploadedBytes = sent) }
                            }
                            if (response.success) {
                                synchronized(lock) { bytesPerItem[item.id] = item.sizeBytes ?: bytesPerItem[item.id] ?: 0L }
                                updateItem(item.id) {
                                    it.copy(status = UploadItemStatus.DONE, uploadedBytes = item.sizeBytes ?: it.uploadedBytes, fileId = response.id)
                                }
                            } else {
                                val message = response.message ?: "Upload failed."
                                updateItem(item.id) { it.copy(status = UploadItemStatus.FAILED, uploadedBytes = 0L, errorMessage = message) }
                            }
                        }
                    }
                }.awaitAll()

                val failed = _uiState.value.queuedItems.count { it.id in batchIds && it.status == UploadItemStatus.FAILED }
                if (failed > 0) {
                    outcomeMessage = "$failed of ${batch.size} uploads failed."
                } else {
                    outcome = TransferOutcome.COMPLETED
                    outcomeMessage = if (batch.size == 1) "${batch.first().name} was uploaded." else "${batch.size} files were uploaded."
                }
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        uploadSpeedBytesPerSec = 0L,
                        uploadEtaSeconds = null,
                        errorMessage = if (failed > 0) "$failed of ${batch.size} uploads failed. Press Upload to retry the failed files." else null
                    )
                }
            } catch (e: CancellationException) {
                outcome = TransferOutcome.CANCELLED
                _uiState.update { s ->
                    s.copy(
                        isLoading = false,
                        uploadedBytes = 0L,
                        uploadSpeedBytesPerSec = 0L,
                        uploadEtaSeconds = null,
                        queuedItems = s.queuedItems.map {
                            if (it.status == UploadItemStatus.UPLOADING) it.copy(status = UploadItemStatus.PENDING, uploadedBytes = 0L) else it
                        }
                    )
                }
                throw e
            } finally {
                transfers.finish(transferId, outcome, outcomeMessage)
            }
        }
    }

    fun upload() {
        if (_uiState.value.isLoading) return
        if (_uiState.value.queuedItems.isNotEmpty()) {
            uploadQueuedItems()
            return
        }

        val currentTextToUpload = _uiState.value.textToUpload
        val currentSelectedFileUri = _uiState.value.selectedFileUri

        if (missingKey()) {
            _uiState.update {
                it.copy(
                    isLoading = false,
                    errorMessage = "API Key is missing. Please set it in Settings.",
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
                    audioAlbumArt = null,
                    videoThumbnail = null,
                    videoDurationMillis = null,
                    pdfPageCount = null,
                    apkVersionName = null,
                    apkPackageName = null,
                    apkIcon = null
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
                    audioAlbumArt = null,
                    videoThumbnail = null,
                    videoDurationMillis = null,
                    pdfPageCount = null,
                    apkVersionName = null,
                    apkPackageName = null,
                    apkIcon = null
                )
            }
            return
        }

        val totalBytesForUpload = if (currentSelectedFileUri != null) {
            _uiState.value.uploadTotalSizeBytes
        } else if (currentTextToUpload.isNotBlank()) {
            currentTextToUpload.toByteArray().size.toLong()
        } else {
            null
        }

        speedTracker.reset()
        _uiState.update {
            it.copy(
                isLoading = true,
                errorMessage = null,
                uploadResult = null,
                uploadedBytes = 0L,
                uploadTotalSizeBytes = totalBytesForUpload,
                uploadSpeedBytesPerSec = 0L,
                uploadEtaSeconds = null
            )
        }
        Log.d(TAG, "Upload started. isLoading: true, uploadedBytes: 0, totalBytes: $totalBytesForUpload")

        val transferId = "upload-single-${nextTransferNumber++}"
        transfers.start(
            TransferInfo(transferId, TransferKind.UPLOAD, _uiState.value.selectedFileName ?: "text", totalBytes = totalBytesForUpload),
            onCancel = { cancelUpload() }
        )

        uploadJob = viewModelScope.launch {
            val currentFileNameForUpload = _uiState.value.selectedFileName
            var operationType = "unknown"

            val job = coroutineContext[Job]
            val progressCallback: (bytesSent: Long, totalBytes: Long?) -> Unit = { bytesSent, receivedTotalBytes ->
                // Called from inside the host's sending loop: a cancelled upload stops there
                if (job?.isActive == false) throw CancellationException("The upload was cancelled")
                val speed = speedTracker.update(bytesSent)
                _uiState.update {
                    val total = receivedTotalBytes ?: it.uploadTotalSizeBytes
                    val eta = estimateEtaSeconds(total, bytesSent, speed)
                    transfers.progress(transferId, bytesSent, total, speed, eta)
                    it.copy(
                        uploadedBytes = bytesSent,
                        uploadTotalSizeBytes = total,
                        uploadSpeedBytesPerSec = speed,
                        uploadEtaSeconds = eta
                    )
                }
            }

            val response: UploadResult? = try {
                if (currentSelectedFileUri != null) {
                    operationType = "file from URI"
                    val fileName = currentFileNameForUpload ?: "pixeldrain_upload_${System.currentTimeMillis()}"
                    uploadFile(fileName, currentSelectedFileUri, progressCallback)
                } else if (currentTextToUpload.isNotBlank()) {
                    operationType = "text"
                    val fileName = "text_upload_${System.currentTimeMillis()}.txt"
                    uploadText(fileName, currentTextToUpload.toByteArray(), progressCallback)
                } else {
                    null
                }
            } catch (e: CancellationException) {
                transfers.finish(transferId, TransferOutcome.CANCELLED)
                _uiState.update {
                    it.copy(isLoading = false, uploadedBytes = 0L, uploadSpeedBytesPerSec = 0L, uploadEtaSeconds = null)
                }
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Upload failed for $operationType. Exception: ${e.message}", e)
                _uiState.update {
                    it.copy(isLoading = false, errorMessage = "Upload failed: ${e.message ?: "Unknown error"}")
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
                            selectedFileUri = null,
                            selectedFileMimeType = null,
                            selectedFileTextContent = null,
                            audioDurationMillis = null,
                            audioBitrate = null,
                            audioArtist = null,
                            audioAlbum = null,
                            audioAlbumArt = null,
                            videoThumbnail = null,
                            videoDurationMillis = null,
                            pdfPageCount = null,
                            apkVersionName = null,
                            apkPackageName = null,
                            apkIcon = null
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            uploadResult = response,
                            errorMessage = response.message ?: "Upload failed with no specific message."
                        )
                    }
                }
            } else { 
                if (_uiState.value.isLoading) {
                     _uiState.update {
                        it.copy(isLoading = false, errorMessage = it.errorMessage ?: "Upload did not return a response.")
                    }
                }
            }
            _uiState.update { it.copy(uploadSpeedBytesPerSec = 0L, uploadEtaSeconds = null) }
            if (response?.success == true) {
                transfers.finish(transferId, TransferOutcome.COMPLETED, "${currentFileNameForUpload ?: "Text"} was uploaded.")
            } else {
                transfers.finish(transferId, TransferOutcome.FAILED, response?.message ?: "The upload failed.")
            }
        }
    }
}
