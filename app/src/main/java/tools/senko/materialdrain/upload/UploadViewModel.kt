package tools.senko.materialdrain.upload

import tools.senko.materialdrain.provider.api.StorageRef
import tools.senko.materialdrain.provider.api.StorageNode
import android.content.ClipboardManager
import android.content.ClipData
import android.app.Application
import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap // Added for video thumbnail
import android.graphics.pdf.PdfRenderer
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import android.util.Log
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
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
import tools.senko.materialdrain.files.isTextFile
import tools.senko.materialdrain.transfer.TransferInfo
import tools.senko.materialdrain.transfer.TransferKind
import tools.senko.materialdrain.transfer.TransferOutcome
import tools.senko.materialdrain.transfer.TransferRegistry
import tools.senko.materialdrain.transfer.TransferSpeedTracker
import tools.senko.materialdrain.transfer.estimateEtaSeconds
import tools.senko.materialdrain.util.readContentUriInfo
import tools.senko.materialdrain.settings.AppSettings
import tools.senko.materialdrain.UiMessages

// SharedPreferences constants
private const val TAG = "PIXEL_VM_DEBUG" // Tag for Logcat
private const val TEXT_PREVIEW_MAX_LENGTH = 4096 // Max 4KB for text preview
private const val VIDEO_THUMBNAIL_QUALITY = 75 // JPEG quality for video thumbnails
private const val VIDEO_THUMBNAIL_TARGET_TIME_US = 1_000_000L // 1 second for thumbnail
private const val APK_ICON_SIZE_PX = 192 // APK icons are drawn at this size for the preview
private const val MAX_PARALLEL_UPLOADS = 3

enum class UploadItemStatus { PENDING, UPLOADING, DONE, FAILED }

data class UploadItem(
    val id: Int,
    val uri: Uri,
    val name: String,
    val sizeBytes: Long?,
    val mimeType: String?,
    /** When the file was last changed on the device, for uploading in that order (AppSettings.uploadInModifiedOrder) */
    val lastModifiedMillis: Long? = null,
    val status: UploadItemStatus = UploadItemStatus.PENDING,
    val uploadedBytes: Long = 0L,
    val fileId: String? = null,
    val errorMessage: String? = null
)

/**
 * [items] in the order they were last changed, oldest first, so the newest goes up last and is at the top of a list
 * ordered by upload. Files whose date isn't known go after the rest, in the order they were picked; files changed at
 * the same moment also keep that order.
 */
internal fun inModifiedOrder(items: List<UploadItem>): List<UploadItem> =
    items.sortedBy { it.lastModifiedMillis ?: Long.MAX_VALUE }

/**
 * The one file picked for upload, with what its preview shows. Which of the details are filled depends on its type:
 * the audio ones for audio, the video ones for video, and so on.
 *
 * Not a data class: it holds pictures as byte arrays, which a data class would only compare by reference anyway. One is
 * made per pick and never changed, so comparing by identity is right.
 */
class PickedFile(
    val uri: Uri,
    val name: String?,
    val sizeBytes: Long?,
    val mimeType: String?,
    /** What couldn't be read of it for the preview (its text, the album, the video frame...), shown under it */
    val detailsError: String? = null,
    val textContent: String? = null,
    // Audio
    val audioDurationMillis: Long? = null,
    val audioBitrate: Int? = null,
    val audioArtist: String? = null,
    val audioAlbum: String? = null,
    val audioAlbumArt: ByteArray? = null,
    // Video
    val videoThumbnail: ByteArray? = null,
    val videoDurationMillis: Long? = null,
    // PDF
    val pdfPageCount: Int? = null,
    // APK
    val apkVersionName: String? = null,
    val apkPackageName: String? = null,
    val apkIcon: ByteArray? = null
)

data class UploadUiState(
    val isLoading: Boolean = false,
    val uploadResult: UploadResult? = null,
    // A failure of the upload itself, shown as a dialog
    val errorMessage: String? = null,
    // Pixeldrain needs the login to upload and there is none: the app asks for it (see App.kt)
    val apiKeyMissing: Boolean = false,
    val uploadTotalSizeBytes: Long? = null,
    val uploadedBytes: Long = 0L,
    val uploadSpeedBytesPerSec: Long = 0L,
    val uploadEtaSeconds: Long? = null,
    // Multi-file selection (used when more than one file is picked)
    val queuedItems: List<UploadItem> = emptyList(),
    val textToUpload: String = "",
    // A single picked file (more than one go to queuedItems)
    val pickedFile: PickedFile? = null
) {
    val hasUploadable: Boolean
        get() = pickedFile != null || textToUpload.isNotBlank() ||
            queuedItems.any { it.status == UploadItemStatus.PENDING || it.status == UploadItemStatus.FAILED }
}

/** The outcome of one upload, the same for every host. */
data class UploadResult(val success: Boolean, val id: String? = null, val message: String? = null)

class UploadViewModel(
    private val application: Application,
    private val registry: ProviderRegistry,
    private val configStore: ProviderConfigStore,
    private val sessionManager: SessionManager,
    private val transfers: TransferRegistry,
    private val appSettings: AppSettings,
    private val messages: UiMessages
) : ViewModel() {

    private fun provider() = registry.resolve(configStore.activeProviderId.value)

    /**
     * Copies the links of the files just uploaded ([uploaded]: their ids and names), one per line, when the setting is on
     * (Files and transfers, Copy the link after uploading). Hosts without links of their files copy nothing.
     */
    private fun copyLinksOf(uploaded: List<Pair<String, String>>) {
        if (!appSettings.copyLinkAfterUpload.value || uploaded.isEmpty()) return
        val provider = provider()
        val links = uploaded.mapNotNull { (id, name) ->
            provider.shareUrl(StorageNode(ref = StorageRef(id = id), name = name, isDirectory = false))
        }
        if (links.isEmpty()) return
        application.getSystemService(ClipboardManager::class.java)
            ?.setPrimaryClip(ClipData.newPlainText(if (links.size == 1) "Link" else "Links", links.joinToString("\n")))
    }

    /** Pixeldrain uploads need the login; other hosts take their own credentials. */
    private fun missingKey(): Boolean = provider().kind == ProviderKind.PIXELDRAIN && sessionManager.apiKey.value.isBlank()

    /**
     * Uploads one file. Never throws but for a cancel: a host's failure (the network gone, the host refusing) is a failed
     * result, and a failure which comes of the upload being cancelled (the connection cut under it) is the cancel.
     */
    private suspend fun uploadFile(fileName: String, uri: Uri, onProgress: (Long, Long?) -> Unit): UploadResult {
        val provider = provider()
        val store = provider.fileStore
        val response = try {
            if (store != null) {
                store.upload(fileName, uri, application, onProgress)
            } else {
                // No flat storage to upload into (WebDAV, S3, SMB): the Upload screen uploads into the host's root
                // folder instead, through the same browse the Filesystem screen uses
                val browse = provider.browse ?: return UploadResult(false, message = "This host can't receive uploads.")
                val root = provider.rootPath.trim('/')
                browse.upload(if (root.isEmpty()) fileName else "$root/$fileName", uri, application, makeParents = false, onProgress)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            currentCoroutineContext().ensureActive()
            Log.e(TAG, "Upload of $fileName failed: ${e.message}", e)
            return UploadResult(false, message = e.message ?: "The upload failed.")
        }
        return when (response) {
            is ApiResponse.Success -> UploadResult(true, id = response.data.ref.id)
            is ApiResponse.Error -> {
                // A host which reports the connection cut under a cancelled upload as an error: that's the cancel
                currentCoroutineContext().ensureActive()
                UploadResult(false, message = response.error.message)
            }
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

    private var uploadJob: Job? = null
    // The reading of the files just picked
    private var pickJob: Job? = null
    private val speedTracker = TransferSpeedTracker()
    private var nextItemId = 0
    private var nextTransferNumber = 0

    init {
        // A key set after an upload was refused for the lack of one: the refusal no longer applies
        viewModelScope.launch {
            sessionManager.apiKey.collect { key -> if (key.isNotBlank()) clearApiKeyError() }
        }
    }

    fun onFileSelected(uri: Uri?, context: Context) {
        // A pick still being read is replaced by this one
        pickJob?.cancel()
        if (uri == null) {
            _uiState.update {
                it.copy(
                    pickedFile = null,
                    uploadTotalSizeBytes = null,
                    queuedItems = emptyList(),
                    errorMessage = null,
                    uploadResult = null,
                    uploadedBytes = 0L
                )
            }
            return
        }
        val appContext = context.applicationContext
        pickJob = viewModelScope.launch {
            // Not on the main thread: an APK is copied whole to read its icon, media and PDFs are opened to read their
            // details, and a file shared from a cloud app may first have to be downloaded
            val picked = withContext(Dispatchers.IO) { readPickedFile(uri, appContext) }
            _uiState.update {
                it.copy(
                    pickedFile = picked,
                    uploadTotalSizeBytes = picked.sizeBytes,
                    queuedItems = emptyList(),
                    textToUpload = "",
                    uploadResult = null,
                    uploadedBytes = 0L
                )
            }
        }
    }

    /** Reads the picked file's name, size and type, and what its preview shows. What can't be read is left out and told in its detailsError. */
    private fun readPickedFile(uri: Uri, context: Context): PickedFile {
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

            if (isTextFile(newFileName.orEmpty(), newMimeType)) {
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
                    // A frame a second in, else the file's own cover picture, else the very first frame
                    val bitmap = retriever.getFrameAtTime(VIDEO_THUMBNAIL_TARGET_TIME_US, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                        ?: retriever.primaryImage
                        ?: retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
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
                                // Most apps have an adaptive icon rather than a plain bitmap one: toBitmap draws either.
                                // PNG keeps the transparency around the icon, which JPEG would fill with black.
                                val bitmap = pm.getApplicationIcon(appInfo).toBitmap(APK_ICON_SIZE_PX, APK_ICON_SIZE_PX)
                                val baos = ByteArrayOutputStream()
                                bitmap.compress(Bitmap.CompressFormat.PNG, 100, baos)
                                newApkIcon = baos.toByteArray()
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

        // Each of the details is only read for its own type, so the others are still null here
        return PickedFile(
            uri = uri,
            name = newFileName,
            sizeBytes = newFileSizeBytes,
            mimeType = newMimeType,
            detailsError = newErrorMessage?.trim(),
            textContent = newTextContent,
            audioDurationMillis = newAudioDurationMillis,
            audioBitrate = newAudioBitrate,
            audioArtist = newAudioArtist,
            audioAlbum = newAudioAlbum,
            audioAlbumArt = newAudioAlbumArt,
            videoThumbnail = newVideoThumbnail,
            videoDurationMillis = newVideoDurationMillis,
            pdfPageCount = newPdfPageCount,
            apkVersionName = newApkVersionName,
            apkPackageName = newApkPackageName,
            apkIcon = newApkIcon
        )
    }

    fun onFilesSelected(uris: List<Uri>, context: Context) {
        if (uris.size <= 1) {
            onFileSelected(uris.firstOrNull(), context)
            return
        }
        // A pick still being read is replaced by this one
        pickJob?.cancel()
        pickJob = viewModelScope.launch {
            val items = withContext(Dispatchers.IO) {
                uris.map { uri -> readUploadItem(uri, context) }
            }.let { picked ->
                // In the order they'll go in, from the start, so it can be seen before uploading (see uploadQueuedItems)
                if (appSettings.uploadInModifiedOrder.value) inModifiedOrder(picked) else picked
            }
            _uiState.update {
                it.copy(
                    queuedItems = items,
                    pickedFile = null,
                    uploadTotalSizeBytes = null,
                    textToUpload = "",
                    errorMessage = null,
                    uploadResult = null,
                    uploadedBytes = 0L
                )
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
            mimeType = context.contentResolver.getType(uri),
            lastModifiedMillis = info.lastModifiedMillis
        )
    }

    /**
     * Uploads [uris] right away as new files of the host: the Files screen's Upload (in its ⋮ menu), and files shared to
     * the app sent there (see SharedUploadBar). Read and queued as picked files are, then sent as [upload] sends them;
     * how it went is told on the snackbar rather than on the Upload screen, which isn't there. Not while an upload runs.
     */
    fun uploadNow(uris: List<Uri>, context: Context) {
        if (uris.isEmpty() || _uiState.value.isLoading) return
        pickJob?.cancel()
        pickJob = viewModelScope.launch {
            val items = withContext(Dispatchers.IO) { uris.map { uri -> readUploadItem(uri, context) } }
            _uiState.update {
                it.copy(
                    queuedItems = items,
                    pickedFile = null,
                    uploadTotalSizeBytes = null,
                    textToUpload = "",
                    errorMessage = null,
                    uploadResult = null,
                    uploadedBytes = 0L
                )
            }
            uploadQueuedItems(fromScreen = false)
        }
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
        if (newText.isNotBlank()) {
            _uiState.update {
                it.copy(
                    textToUpload = newText,
                    queuedItems = emptyList(),
                    pickedFile = null,
                    uploadTotalSizeBytes = newText.toByteArray().size.toLong(),
                    errorMessage = null,
                    uploadResult = null,
                    uploadedBytes = 0L
                )
            }
        } else {
             _uiState.update {
                it.copy(
                    textToUpload = newText,
                    uploadTotalSizeBytes = if (it.pickedFile != null) it.uploadTotalSizeBytes else null, // Keep existing file size if a file is selected
                    errorMessage = null,
                    uploadResult = null,
                    uploadedBytes = 0L
                    // If clearing text, don't clear selected file info unless explicitly done by onFileSelected(null,...)
                )
            }
        }
    }

    fun clearApiKeyError() {
        _uiState.update { it.copy(apiKeyMissing = false) }
    }


    fun clearUploadResult() {
        _uiState.update { it.copy(uploadResult = null, errorMessage = null, isLoading = false, uploadedBytes = 0L) }
    }

    /**
     * Sends the queued files. [fromScreen] false (see [uploadNow]): there's no Upload screen to show how it went, and so
     * to retry from, so the snackbar says it instead.
     */
    private fun uploadQueuedItems(fromScreen: Boolean = true) {
        // In the order the files were last changed, oldest first (files whose date Android doesn't know go last, as
        // they were picked), and one after another, so they reach the host in that order; otherwise several at once
        val ordered = appSettings.uploadInModifiedOrder.value
        if (ordered) {
            _uiState.update { s -> s.copy(queuedItems = inModifiedOrder(s.queuedItems)) }
        }
        val parallelUploads = if (ordered) 1 else MAX_PARALLEL_UPLOADS
        val batch = _uiState.value.queuedItems.filter {
            it.status == UploadItemStatus.PENDING || it.status == UploadItemStatus.FAILED
        }
        if (batch.isEmpty()) return
        if (missingKey()) {
            _uiState.update { it.copy(apiKeyMissing = true) }
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
                val semaphore = Semaphore(parallelUploads)
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

                copyLinksOf(
                    _uiState.value.queuedItems
                        .filter { it.id in batchIds && it.status == UploadItemStatus.DONE }
                        .mapNotNull { item -> item.fileId?.let { it to item.name } }
                )
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
                        errorMessage = if (failed > 0 && fromScreen) "$failed of ${batch.size} failed. Tap Upload to retry." else null
                    )
                }
                if (!fromScreen) {
                    val firstError = _uiState.value.queuedItems.firstOrNull { it.id in batchIds && it.status == UploadItemStatus.FAILED }?.errorMessage
                    messages.show(if (failed > 0) "$outcomeMessage ${firstError.orEmpty()}".trim() else outcomeMessage.orEmpty(), long = failed > 0)
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
        val currentPickedFile = _uiState.value.pickedFile

        // The file or text stays, so it can be uploaded once the key is set
        if (missingKey()) {
            _uiState.update { it.copy(isLoading = false, apiKeyMissing = true) }
            return
        }

        if (currentPickedFile == null && currentTextToUpload.isBlank()) {
            _uiState.update {
                it.copy(
                    isLoading = false,
                    errorMessage = "Pick a file or enter text.",
                    pickedFile = null,
                    uploadTotalSizeBytes = null,
                    textToUpload = ""
                )
            }
            return
        }

        val totalBytesForUpload = if (currentPickedFile != null) {
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
            TransferInfo(transferId, TransferKind.UPLOAD, currentPickedFile?.name ?: "text", totalBytes = totalBytesForUpload),
            onCancel = { cancelUpload() }
        )

        uploadJob = viewModelScope.launch {
            val currentFileNameForUpload = currentPickedFile?.name
            var operationType = "unknown"

            val job = coroutineContext[Job]
            val progressCallback: (bytesSent: Long, totalBytes: Long?) -> Unit = { bytesSent, receivedTotalBytes ->
                // Called from inside the host's sending loop: a cancelled upload stops there
                if (job?.isActive == false) throw CancellationException("The upload was cancelled")
                val speed = speedTracker.update(bytesSent)
                val total = receivedTotalBytes ?: _uiState.value.uploadTotalSizeBytes
                val eta = estimateEtaSeconds(total, bytesSent, speed)
                // Not inside update {}: that may run more than once, and the notification should be updated once
                transfers.progress(transferId, bytesSent, total, speed, eta)
                _uiState.update {
                    it.copy(
                        uploadedBytes = bytesSent,
                        uploadTotalSizeBytes = total,
                        uploadSpeedBytesPerSec = speed,
                        uploadEtaSeconds = eta
                    )
                }
            }

            val response: UploadResult? = try {
                if (currentPickedFile != null) {
                    operationType = "file from URI"
                    val fileName = currentFileNameForUpload ?: "pixeldrain_upload_${System.currentTimeMillis()}"
                    uploadFile(fileName, currentPickedFile.uri, progressCallback)
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
                    response.id?.let { id -> copyLinksOf(listOf(id to (currentFileNameForUpload ?: "text.txt"))) }
                    _uiState.update {
                        val finalTotalBytes = it.uploadTotalSizeBytes
                        it.copy(
                            isLoading = false,
                            uploadResult = response,
                            errorMessage = null,
                            pickedFile = null,
                            uploadTotalSizeBytes = null,
                            uploadedBytes = finalTotalBytes ?: 0L,
                            textToUpload = ""
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            uploadResult = response,
                            errorMessage = response.message ?: "Upload failed."
                        )
                    }
                }
            } else { 
                if (_uiState.value.isLoading) {
                     _uiState.update {
                        it.copy(isLoading = false, errorMessage = it.errorMessage ?: "No answer from the host.")
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
