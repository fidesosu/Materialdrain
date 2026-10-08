package tools.senko.materialdrain.files

import android.app.Application
import android.content.ContentValues
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.webkit.MimeTypeMap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.OutputStream
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.coroutineContext
import tools.senko.materialdrain.auth.SessionManager
import tools.senko.materialdrain.settings.AppSettings
import tools.senko.materialdrain.settings.SavedOpenedFile
import tools.senko.materialdrain.provider.ProviderConfigStore
import tools.senko.materialdrain.provider.ProviderRegistry
import tools.senko.materialdrain.provider.api.ApiResponse
import tools.senko.materialdrain.provider.api.ArchiveEntryDetails
import tools.senko.materialdrain.provider.forDisplay
import tools.senko.materialdrain.provider.api.ProviderCapability
import tools.senko.materialdrain.provider.api.FileList
import tools.senko.materialdrain.provider.api.ProviderError
import tools.senko.materialdrain.provider.api.ProviderKind
import tools.senko.materialdrain.provider.api.ProviderLog
import tools.senko.materialdrain.provider.api.StorageNode
import tools.senko.materialdrain.ui.media.MediaCovers
import tools.senko.materialdrain.provider.api.StorageProvider
import tools.senko.materialdrain.provider.api.StorageRef
import tools.senko.materialdrain.transfer.TransferInfo
import tools.senko.materialdrain.transfer.TransferKind
import tools.senko.materialdrain.transfer.TransferOutcome
import tools.senko.materialdrain.transfer.TransferRegistry
import tools.senko.materialdrain.transfer.TransferSpeedTracker
import tools.senko.materialdrain.transfer.estimateEtaSeconds

private const val TAG = "FileInfoViewModel"
private const val MAX_TEXT_PREVIEW_FETCH_SIZE_BYTES = 1 * 1024 * 1024 // 1MB
private const val MAX_TEXT_PREVIEW_DISPLAY_LENGTH = 8 * 1024 // 8KB
private const val DOWNLOAD_WRITE_BUFFER_BYTES = 256 * 1024
private const val MAX_FILES_PER_ZIP = 100
private const val API_KEY_MISSING = "API Key is missing. Please set it in Settings."

// --- Download State Management ---
enum class DownloadStatus {
    PENDING,
    DOWNLOADING,
    COMPLETED,
    FAILED,
    /** Stopped by the user; nothing was saved. */
    CANCELLED
}

data class FileDownloadState(
    val fileId: String,
    val fileName: String,
    val totalBytes: Long?,
    val downloadedBytes: Long = 0L,
    val progressFraction: Float = 0f,
    val bytesPerSecond: Long = 0L,
    val etaSeconds: Long? = null,
    val status: DownloadStatus = DownloadStatus.PENDING,
    val message: String? = null, // For individual success/error messages
    val targetUri: Uri? = null // To store the MediaStore URI
)
/**
 * A batch of files downloaded one after another. [doneBytes] are the bytes of the files finished so far; [totalBytes] is
 * null when a file's size is unknown, then the batch shows how many files are done but no percentage.
 */
data class DownloadBatch(
    val totalFiles: Int,
    val doneFiles: Int = 0,
    val totalBytes: Long?,
    val doneBytes: Long = 0L
)
// --- End Download State Management ---

/** Identifies a node in the app: its file id, or its path when it has no id (the filesystem). */
val StorageNode.key: String get() = ref.id ?: ref.path

/**
 * The type which decides how a file is previewed. Some hosts list files without a useful type (the filesystem can
 * give a blank or generic one), so a missing or generic type is worked out from the file extension instead.
 */
fun StorageNode.previewMimeType(): String? {
    val reported = mimeType?.takeIf { it.isNotBlank() && it != "application/octet-stream" }
    if (reported != null) return reported
    val extension = name.substringAfterLast('.', "").lowercase()
    if (extension.isEmpty()) return mimeType
    return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: mimeType
}

/** The folder [inside] of the archive at [path], and its entries once they've loaded. */
data class ArchiveView(val path: String, val inside: String, val entries: List<StorageNode>?, val error: String?)

/** Archives which can be looked inside from the file details (see ArchiveOps). */
private val ARCHIVE_EXTENSIONS = setOf("zip", "7z", "rar", "tar", "tgz", "apk")

/** Image formats whose thumbnails are made on the device rather than by the host (see FileInfoViewModel.thumbnailFor). */
private val DEVICE_THUMBNAIL_EXTENSIONS = setOf("heic", "heif", "avif")

fun isArchiveName(name: String): Boolean = name.substringAfterLast('.', "").lowercase() in ARCHIVE_EXTENSIONS

data class FileInfoUiState(
    // For single file info
    val isLoadingFileInfo: Boolean = false,
    val fileInfo: StorageNode? = null,
    val fileInfoErrorMessage: String? = null,

    // For text preview
    val isLoadingTextPreview: Boolean = false,
    val textPreviewContent: String? = null,
    // The whole text the preview was made from (up to the fetch limit), for the fullscreen view
    val textPreviewFullContent: String? = null,
    val textPreviewErrorMessage: String? = null,

    // For user's list of files
    val userFilesList: List<StorageNode> = emptyList(),
    val isLoadingUserFiles: Boolean = false,
    val userFilesListErrorMessage: String? = null,

    // For file deletion
    val isLoadingDeleteFile: Boolean = false,
    val deleteFileSuccessMessage: String? = null,
    val deleteFileErrorMessage: String? = null,
    val initiateDeleteFile: Boolean = false,
    val fileIdToDelete: String? = null,
    val nodeToDelete: StorageNode? = null,

    val apiKeyMissingError: Boolean = false,
    val apiKey: String = "", // Exposed API Key, only the built-in Pixeldrain uses it

    // Filtering state
    val filterQuery: String = "",
    val showFilterInput: Boolean = false,

    // For multiple file downloads
    val activeDownloads: Map<String, FileDownloadState> = emptyMap(),
    // Several files downloaded one after another, while that batch runs; null when none is running
    val downloadBatch: DownloadBatch? = null,
    // General messages, can be deprecated if per-file messages are sufficient
    val fileDownloadSuccessMessage: String? = null,
    // The last finished download, when it can be opened (it's saved on the device)
    val fileDownloadOpen: OpenableDownload? = null,
    // The contents of an archive shown in the file details, see loadArchive
    val archive: ArchiveView? = null,
    val fileDownloadErrorMessage: String? = null,

    // Scroll state preservation
    val shouldPreserveScrollPosition: Boolean = false,

    // Results of actions on several files at once (delete, add to filesystem, create list)
    val isBulkOperationRunning: Boolean = false,
    val operationMessage: String? = null,
    val operationError: String? = null
)

/**
 * The file details, the files of the account and the actions on them (download, delete, filesystem, lists),
 * for whichever provider is active. Every action goes through the provider's operations; the API key is only
 * checked for the built-in Pixeldrain, which is the one host that needs it for the UI.
 */
class FileInfoViewModel(
    private val application: Application,
    private val registry: ProviderRegistry,
    private val configStore: ProviderConfigStore,
    private val sessionManager: SessionManager,
    private val transfers: TransferRegistry,
    private val appSettings: AppSettings
) : ViewModel() {

    private val _uiState = MutableStateFlow(FileInfoUiState())
    val uiState: StateFlow<FileInfoUiState> = _uiState.asStateFlow()

    private val _displayedFiles = MutableStateFlow<List<StorageNode>>(emptyList())
    val displayedFiles: StateFlow<List<StorageNode>> = _displayedFiles.asStateFlow()

    init {
        loadApiKey()

        // Choosing another host as the active one reloads the files of that host
        viewModelScope.launch {
            configStore.changes.drop(1).collect { onActiveProviderChanged() }
        }

        // Recomputes displayedFiles whenever the list, the shared sorting, or the filter changes
        viewModelScope.launch {
            combine(
                uiState.map { it.userFilesList },
                uiState.map { it.filterQuery },
                appSettings.sortField,
                appSettings.sortAscending
            ) { files, filterQuery, sortField, sortAscending ->
                withContext(Dispatchers.Default) {
                    files
                        .filter { it.name.contains(filterQuery, ignoreCase = true) }
                        .sortedWith(fileComparator(sortField, sortAscending))
                }
            }.collect { sortedAndFiltered ->
                _displayedFiles.value = sortedAndFiltered
            }
        }
    }

    private fun provider(): StorageProvider = registry.resolve(configStore.activeProviderId.value)

    /** The public link of a node, or null when the active host has none. */
    fun shareUrlFor(node: StorageNode): String? = provider().shareUrl(node)

    /** The preview image of a node, from the active host; null when it has none. */
    fun thumbnailFor(node: StorageNode): String? {
        val provider = provider()
        val thumbnail = provider.thumbnailUrl(node)
        // SMB shares make their own on the device, and only real ones: photos, video frames and album covers
        if (provider.kind == ProviderKind.SMB) return thumbnail
        // Songs show their album cover, read out of the file itself. One without a cover keeps the app's own music tile
        // (see FileIcon) rather than the host's generic picture
        if (!node.isDirectory && node.previewMimeType()?.startsWith("audio/") == true) {
            provider.rawContentUrl(node, attachment = false)?.let { raw ->
                return MediaCovers.audioCoverUrl(raw, fallback = null, version = node.modifiedAt ?: node.createdAt)
            }
        }
        // Videos show a frame read out of the file itself too: a host's thumbnailer can't read every video, and gives
        // those a generic picture instead. The host's thumbnail is kept for a video the device can't read either
        if (!node.isDirectory && node.previewMimeType()?.startsWith("video/") == true) {
            provider.rawContentUrl(node, attachment = false)?.let { raw ->
                return MediaCovers.videoFrameUrl(raw, fallback = thumbnail, version = node.modifiedAt ?: node.createdAt)
            }
        }
        // Photos in formats the hosts' own thumbnailers often can't read (HEIC from phones, AVIF): decoded on the device
        // from the file itself, scaled to the thumbnail's size, as the preview already does
        if (!node.isDirectory && node.name.substringAfterLast('.', "").lowercase() in DEVICE_THUMBNAIL_EXTENSIONS) {
            provider.rawContentUrl(node, attachment = false)?.let { return it }
        }
        // Only pictures and videos have a thumbnail worth showing: for everything else (text, archives, apps, unknown
        // kinds) the hosts hand out a generic picture, and the app's own tile for the kind of file is used instead
        val mime = node.previewMimeType().orEmpty()
        return thumbnail.takeIf { mime.startsWith("image/") || mime.startsWith("video/") }
    }

    /** The content of a node itself (for previews and the full screen view), from the active host. */
    fun rawUrlFor(node: StorageNode): String? = provider().rawContentUrl(node, attachment = false)

    /** Whether the active host lets its files be deleted. */
    fun canDeleteFiles(): Boolean = ProviderCapability.DELETE in provider().capabilities

    /** The key the UI needs, only the built-in Pixeldrain has one. */
    private fun apiKeyForUi(): String =
        if (provider().kind == ProviderKind.PIXELDRAIN) sessionManager.currentApiKey() else ""

    /** Pixeldrain's operations need the login: without it there's nothing to show or change. */
    private fun needsKeyButMissing(): Boolean = provider().kind == ProviderKind.PIXELDRAIN && apiKeyForUi().isBlank()

    private fun onActiveProviderChanged() {
        val current = provider()
        ProviderLog.i("Files", "active host is now '${current.displayName}' (${current.kind}), reloading its files")
        _uiState.update {
            it.copy(
                userFilesList = emptyList(),
                userFilesListErrorMessage = null,
                fileInfo = null,
                apiKeyMissingError = false
            )
        }
        loadApiKey()
    }

    fun loadApiKey() {
        val key = apiKeyForUi()
        _uiState.update { it.copy(apiKey = key) }
        if (needsKeyButMissing()) {
            _uiState.update { it.copy(apiKeyMissingError = true, userFilesListErrorMessage = API_KEY_MISSING) }
            return
        }
        _uiState.update { it.copy(apiKeyMissingError = false, userFilesListErrorMessage = null) }
        val state = _uiState.value
        if (state.userFilesList.isEmpty() && !state.isLoadingUserFiles) fetchUserFiles()
    }

    private fun clearTextPreviewStates() {
        _uiState.update {
            it.copy(
                isLoadingTextPreview = false,
                textPreviewContent = null, textPreviewFullContent = null,
                textPreviewErrorMessage = null
            )
        }
    }

    /** Reads the file from the provider, as the file id or the path it has. */
    private suspend fun downloadNodeTo(
        node: StorageNode,
        outputStream: OutputStream,
        onProgress: (Long, Long?) -> Unit
    ): ApiResponse<Long> {
        val current = provider()
        // A file inside an archive is read from the archive
        (node.richDetails as? ArchiveEntryDetails)?.let { entry ->
            val archives = current.archives
                ?: return ApiResponse.Error(ProviderError("not_supported", "This host can't look inside archives."))
            return archives.read(entry.archivePath, entry.entryPath, outputStream, onProgress)
        }
        val id = node.ref.id
        return if (id != null) {
            val store = current.fileStore
                ?: return ApiResponse.Error(ProviderError("not_supported", "This host can't download files by id."))
            // Several ids joined by commas (see downloadFilesAsZip) are one archive; file ids never contain commas
            if (',' in id) {
                store.downloadArchive(id.split(',').map { StorageRef(id = it) }, outputStream, onProgress)
            } else {
                store.download(StorageRef(id = id), outputStream, onProgress)
            }
        } else {
            val browse = current.browse
                ?: return ApiResponse.Error(ProviderError("not_supported", "This host can't download this file."))
            browse.download(node.ref.path, outputStream, onProgress)
        }
    }

    private fun fetchTextFilePreviewContent(fileInfo: StorageNode) {
        val mimeType = fileInfo.previewMimeType() ?: ""
        val commonTextMimeTypes = listOf(
            "text/plain", "text/html", "text/css", "text/javascript", "text/xml", "text/csv",
            "application/json", "application/xml", "application/javascript", "application/rtf",
            "application/x-sh", "application/x-csh", "application/x-python", "application/x-perl"
        )
        val commonTextExtensions = listOf(".log", ".ini", ".conf", ".cfg", ".md", ".yaml", ".yml", ".toml")

        val isLikelyTextFile = commonTextMimeTypes.any { mimeType.startsWith(it, ignoreCase = true) } ||
                isTextFile(fileInfo.name, mimeType) ||
                (mimeType.startsWith("application/octet-stream", ignoreCase = true) &&
                        commonTextExtensions.any { fileInfo.name.endsWith(it, ignoreCase = true) })

        // An error replaces any text shown for the previous file, so the old preview can't stay on screen
        if (!isLikelyTextFile) {
            _uiState.update { it.copy(isLoadingTextPreview = false, textPreviewContent = null, textPreviewFullContent = null, textPreviewErrorMessage = "Preview not supported for this file type.") }
            return
        }

        val size = fileInfo.size ?: 0L
        if (size > MAX_TEXT_PREVIEW_FETCH_SIZE_BYTES) {
            _uiState.update { it.copy(isLoadingTextPreview = false, textPreviewContent = null, textPreviewFullContent = null, textPreviewErrorMessage = "File is too large (${formatSize(size)}) for text preview. Max ${formatSize(MAX_TEXT_PREVIEW_FETCH_SIZE_BYTES.toLong())}.") }
            return
        }

        _uiState.update { it.copy(isLoadingTextPreview = true, textPreviewContent = null, textPreviewFullContent = null, textPreviewErrorMessage = null) }
        viewModelScope.launch {
            val buffer = ByteArrayOutputStream()
            when (val response = downloadNodeTo(fileInfo, buffer) { _, _ -> }) {
                is ApiResponse.Success -> {
                    val content = buffer.toString(Charsets.UTF_8.name())
                    // The preview on the page shows the start, cut at a line; the fullscreen view the whole file
                    val start = if (content.length > MAX_TEXT_PREVIEW_DISPLAY_LENGTH) {
                        content.substring(0, MAX_TEXT_PREVIEW_DISPLAY_LENGTH).substringBeforeLast('\n')
                    } else {
                        content
                    }
                    _uiState.update {
                        it.copy(isLoadingTextPreview = false, textPreviewContent = start, textPreviewFullContent = content)
                    }
                }
                is ApiResponse.Error -> {
                    _uiState.update {
                        it.copy(
                            isLoadingTextPreview = false,
                            textPreviewErrorMessage = response.error.message.ifBlank { "Error fetching text preview." }
                        )
                    }
                }
            }
        }
    }

    /** Shows the file that was open when the app was closed. False when there is none, or it belongs to another host. */
    fun restoreOpenedFile(): Boolean {
        val saved = appSettings.openedFile ?: return false
        if (saved.hostId != configStore.activeProviderId.value) return false
        fetchFileInfo(saved.id)
        return true
    }

    fun fetchFileInfo(fileId: String) {
        if (fileId.isBlank()) {
            _uiState.update { it.copy(fileInfoErrorMessage = "Please enter or select a File ID.", isLoadingFileInfo = false) }
            return
        }
        _uiState.update { it.copy(isLoadingFileInfo = true, fileInfo = null, fileInfoErrorMessage = null) }
        clearTextPreviewStates()
        viewModelScope.launch {
            val store = provider().fileStore
            if (store == null) {
                _uiState.update { it.copy(isLoadingFileInfo = false, fileInfoErrorMessage = "This host can't show file details by id.") }
                return@launch
            }
            when (val response = store.fileInfo(StorageRef(id = fileId))) {
                is ApiResponse.Success -> {
                    _uiState.update { it.copy(isLoadingFileInfo = false, fileInfo = response.data) }
                    appSettings.openedFile = SavedOpenedFile(configStore.activeProviderId.value, fileId, "")
                    // Only fetch text preview if the main file info call was successful
                    fetchTextFilePreviewContent(response.data)
                }
                is ApiResponse.Error -> {
                    _uiState.update {
                        it.copy(
                            isLoadingFileInfo = false,
                            fileInfoErrorMessage = response.error.message.ifBlank { "Unknown error fetching file info" },
                            fileInfo = null
                        )
                    }
                }
            }
        }
    }

    /** Shows the details of a file node (from the filesystem or the file list). Folders have no details. */
    /** Shows the folder [inside] of the archive in the details (its top when empty). */
    fun openArchiveFolder(inside: String) {
        val path = uiState.value.archive?.path ?: return
        loadArchive(path, inside)
    }

    /** Lists the folder [inside] of the archive at [path] for the details, see FileDetailsScreen. */
    private fun loadArchive(path: String, inside: String) {
        val archives = provider().archives ?: return
        _uiState.update { it.copy(archive = ArchiveView(path, inside, entries = null, error = null)) }
        viewModelScope.launch {
            val result = archives.list(path, inside)
            _uiState.update { state ->
                if (state.archive?.path != path) return@update state
                when (result) {
                    is ApiResponse.Success -> state.copy(
                        archive = ArchiveView(
                            path,
                            inside,
                            result.data.sortedWith(compareBy<StorageNode> { !it.isDirectory }.thenBy { it.name.lowercase() }),
                            error = null
                        )
                    )
                    is ApiResponse.Error -> state.copy(
                        archive = ArchiveView(path, inside, entries = null, error = result.error.message.ifBlank { "This file can't be opened as an archive." })
                    )
                }
            }
        }
    }

    /** Saves one file of the archive shown in the details, as any other download. */
    fun downloadArchiveEntry(entry: StorageNode) {
        initiateDownloadFile(entry)
    }

    fun setFileInfoFromNode(node: StorageNode) {
        if (node.isDirectory) {
            _uiState.update {
                it.copy(
                    isLoadingFileInfo = false,
                    fileInfo = null,
                    fileInfoErrorMessage = "Folders have no file details."
                )
            }
            clearTextPreviewStates()
            return
        }
        _uiState.update {
            it.copy(
                isLoadingFileInfo = false,
                fileInfo = node,
                fileInfoErrorMessage = null
            )
        }
        node.ref.id?.let { appSettings.openedFile = SavedOpenedFile(configStore.activeProviderId.value, it, node.ref.path) }
        fetchTextFilePreviewContent(node)
        // An archive shows what's inside it, in the details
        _uiState.update { it.copy(archive = null) }
        if (isArchiveName(node.name)) loadArchive(node.ref.path, "")
    }

    fun clearFileInfoDisplay() {
        appSettings.openedFile = null
        _uiState.update { it.copy(fileInfo = null, isLoadingFileInfo = false, archive = null) }
        clearTextPreviewStates()
    }


    fun fetchUserFiles() {
        val files = provider().fileList
        if (files == null) {
            _uiState.update { it.copy(userFilesListErrorMessage = "This host has no list of its files.", isLoadingUserFiles = false) }
            return
        }
        if (needsKeyButMissing()) {
            _uiState.update { it.copy(userFilesListErrorMessage = API_KEY_MISSING, isLoadingUserFiles = false, userFilesList = emptyList(), apiKeyMissingError = true) }
            return
        }
        _uiState.update { it.copy(isLoadingUserFiles = true, userFilesListErrorMessage = null, apiKeyMissingError = false) }
        ProviderLog.d("Files", "loading the file list of '${provider().displayName}'")
        viewModelScope.launch {
            when (val response = files.list()) {
                is ApiResponse.Success -> {
                    ProviderLog.i("Files", "loaded ${response.data.children.size} files")
                    _uiState.update { it.copy(isLoadingUserFiles = false, userFilesList = response.data.children) }
                }
                is ApiResponse.Error -> {
                    val errorMsg = response.error.forDisplay().ifBlank { "Unknown error fetching user files" }
                    ProviderLog.e("Files", "loading the file list failed (${response.error.code}): $errorMsg")
                    _uiState.update {
                        it.copy(
                            isLoadingUserFiles = false,
                            userFilesListErrorMessage = errorMsg,
                            apiKeyMissingError = response.error.code == "api_key_missing" || response.error.code == "authentication_required"
                        )
                    }
                }
            }
        }
    }

    fun clearUserFilesError() { _uiState.update { it.copy(userFilesListErrorMessage = null, apiKeyMissingError = false) } }

    fun onFilterQueryChanged(newQuery: String) { _uiState.update { it.copy(filterQuery = newQuery) } }
    fun toggleFilterInput() { _uiState.update { it.copy(showFilterInput = !it.showFilterInput) } }
    fun setFilterInputVisible(isVisible: Boolean) { _uiState.update { it.copy(showFilterInput = isVisible) } }

    fun initiateDeleteFile(node: StorageNode) {
        _uiState.update { it.copy(initiateDeleteFile = true, fileIdToDelete = node.key, nodeToDelete = node) }
    }

    fun cancelDeleteFile() { _uiState.update { it.copy(initiateDeleteFile = false, fileIdToDelete = null, nodeToDelete = null, deleteFileErrorMessage = null) } }

    fun confirmDeleteFile() {
        val node = _uiState.value.nodeToDelete ?: return
        if (needsKeyButMissing()) {
            _uiState.update { it.copy(initiateDeleteFile = false, deleteFileErrorMessage = "API Key is missing. Cannot delete file.", apiKeyMissingError = true) }
            return
        }
        _uiState.update { it.copy(isLoadingDeleteFile = true, deleteFileErrorMessage = null, deleteFileSuccessMessage = null) }
        viewModelScope.launch {
            when (val response = deleteNode(node)) {
                is ApiResponse.Success -> _uiState.update {
                    val newFileInfo = if (it.fileInfo?.key == node.key) null else it.fileInfo
                    if (newFileInfo == null) clearTextPreviewStates()
                    it.copy(
                        isLoadingDeleteFile = false,
                        initiateDeleteFile = false,
                        fileIdToDelete = null,
                        nodeToDelete = null,
                        deleteFileSuccessMessage = "File deleted successfully.",
                        userFilesList = it.userFilesList.filterNot { item -> item.key == node.key },
                        fileInfo = newFileInfo,
                        activeDownloads = it.activeDownloads.filterNot { entry -> entry.key == node.key }
                    )
                }
                is ApiResponse.Error -> _uiState.update {
                    it.copy(
                        isLoadingDeleteFile = false,
                        initiateDeleteFile = false,
                        fileIdToDelete = null,
                        nodeToDelete = null,
                        deleteFileErrorMessage = response.error.message.ifBlank { "Unknown error deleting file." }
                    )
                }
            }
        }
    }

    /** Deletes a file by its id when it has one, otherwise by its path (folders are only deleted from the filesystem). */
    private suspend fun deleteNode(node: StorageNode): ApiResponse<Unit> {
        val current = provider()
        val id = node.ref.id
        return if (id != null) {
            val store = current.fileStore ?: return ApiResponse.Error(ProviderError("not_supported", "This host can't delete files by id."))
            store.delete(StorageRef(id = id))
        } else {
            val browse = current.browse ?: return ApiResponse.Error(ProviderError("not_supported", "This host can't delete this file."))
            browse.delete(node.ref.path, recursive = node.isDirectory)
        }
    }

    fun clearDeleteMessages() { _uiState.update { it.copy(deleteFileSuccessMessage = null, deleteFileErrorMessage = null) } }

    fun clearApiKeyMissingError() {
        _uiState.update {
            it.copy(
                apiKeyMissingError = false,
                userFilesListErrorMessage = if (it.userFilesListErrorMessage == API_KEY_MISSING) null else it.userFilesListErrorMessage
            )
        }
    }

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
                        outputStream = BufferedOutputStream(rawOutputStream, DOWNLOAD_WRITE_BUFFER_BYTES)
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

    /** Starts the download and returns its job. When [queue] is given, cancelling this download cancels the queue too. */
    fun initiateDownloadFile(node: StorageNode, queue: Job? = null, batchId: String? = null): Job {
        val key = node.key
        val size = node.size ?: 0L

        _uiState.update { currentState ->
            val newDownloadState = FileDownloadState(
                fileId = key,
                fileName = node.name,
                totalBytes = size,
                status = DownloadStatus.PENDING
            )
            currentState.copy(activeDownloads = currentState.activeDownloads + (key to newDownloadState))
        }

        // A file of a batch reports into the batch's transfer, see downloadFilesSequentially
        val transferId = batchId ?: "download-$key"
        // Started lazily so that the transfer is registered before it can possibly finish
        val downloadJob = viewModelScope.launch(Dispatchers.IO, start = CoroutineStart.LAZY) {
            var targetUri: Uri? = null
            var outputStream: OutputStream? = null
            var downloadSuccessful = false
            var outcome = TransferOutcome.FAILED
            var outcomeMessage: String? = null

            try {
                _uiState.value.activeDownloads[key]?.let {
                    targetUri = it.targetUri
                }
                if (targetUri == null) {
                    val (uri, stream) = prepareDownloadTargetUriAndStream(node.name, node.mimeType)
                    targetUri = uri
                    outputStream = stream
                } else {
                    application.contentResolver.openOutputStream(targetUri)?.let {
                        outputStream = BufferedOutputStream(it, DOWNLOAD_WRITE_BUFFER_BYTES)
                    }
                }

                if (targetUri == null || outputStream == null) {
                    throw IOException("Failed to prepare or re-open download target in MediaStore.")
                }

                _uiState.update { currentState ->
                    val updatedDownload = currentState.activeDownloads[key]?.copy(
                        status = DownloadStatus.DOWNLOADING,
                        targetUri = targetUri
                    )
                    if (updatedDownload != null) {
                        currentState.copy(activeDownloads = currentState.activeDownloads + (key to updatedDownload))
                    } else currentState
                }

                val speedTracker = TransferSpeedTracker()
                val job = coroutineContext[Job]
                val onProgress: (Long, Long?) -> Unit = { bytesRead, totalBytes ->
                    // Called from inside the host's copy loop, which keeps reading until the file ends unless it's
                    // stopped: a cancelled download stops here, whatever the host's own code does with cancelling
                    if (job?.isActive == false) throw CancellationException("The download was cancelled")
                    val progress = if (totalBytes != null && totalBytes > 0) {
                        (bytesRead.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
                    } else 0f
                    val speed = speedTracker.update(bytesRead)
                    val eta = estimateEtaSeconds(totalBytes ?: size.takeIf { it > 0 }, bytesRead, speed)
                    if (batchId == null) {
                        transfers.progress(transferId, bytesRead, totalBytes, speed, eta)
                    } else {
                        // Within a batch: the bytes of the finished files plus this one, against the whole batch
                        val batch = _uiState.value.downloadBatch
                        transfers.progress(transferId, (batch?.doneBytes ?: 0L) + bytesRead, batch?.totalBytes, speed, eta)
                    }
                    _uiState.update { currentState ->
                        val currentDownload = currentState.activeDownloads[key]
                        val effectiveTotal = totalBytes ?: currentDownload?.totalBytes
                        val updatedDownload = currentDownload?.copy(
                            downloadedBytes = bytesRead,
                            progressFraction = progress,
                            totalBytes = effectiveTotal,
                            bytesPerSecond = speed,
                            etaSeconds = estimateEtaSeconds(effectiveTotal, bytesRead, speed)
                        )
                        if (updatedDownload != null) {
                            currentState.copy(activeDownloads = currentState.activeDownloads + (key to updatedDownload))
                        } else currentState
                    }
                }

                val response = downloadNodeTo(node, outputStream!!, onProgress)
                // A host which turns the stopped copy into an error of its own: it was still a cancel
                ensureActive()
                when (response) {
                    is ApiResponse.Success -> {
                        downloadSuccessful = true
                        val bytesCopied = response.data
                        val message = "File '${node.name}' downloaded (${formatSize(bytesCopied)})."
                        outcome = TransferOutcome.COMPLETED
                        outcomeMessage = message
                        _uiState.update { currentState ->
                            val updatedDownload = currentState.activeDownloads[key]?.copy(
                                status = DownloadStatus.COMPLETED,
                                message = message,
                                downloadedBytes = bytesCopied,
                                progressFraction = 1f,
                                bytesPerSecond = 0L,
                                etaSeconds = null
                            )
                            currentState.copy(
                                activeDownloads = if (updatedDownload != null) currentState.activeDownloads + (key to updatedDownload) else currentState.activeDownloads,
                                fileDownloadSuccessMessage = message,
                                fileDownloadOpen = targetUri?.let { OpenableDownload(it, node.previewMimeType() ?: "*/*") }
                            )
                        }
                    }
                    is ApiResponse.Error -> {
                        throw IOException(response.error.message.ifBlank { "Download failed due to API error." })
                    }
                }
            } catch (e: Exception) {
                downloadSuccessful = false
                outcome = if (e is CancellationException) TransferOutcome.CANCELLED else TransferOutcome.FAILED
                val errorMsg = if (e is CancellationException) {
                    Log.i(TAG, "Download for $key was cancelled by scope.")
                    "Download for '${node.name}' was cancelled."
                } else {
                    Log.e(TAG, "Error during file download process for $key: ${e.message}", e)
                    "Download error for '${node.name}': ${e.localizedMessage ?: "Unexpected error"}"
                }
                outcomeMessage = errorMsg
                _uiState.update { currentState ->
                    val updatedDownload = currentState.activeDownloads[key]?.copy(
                        status = if (e is CancellationException) DownloadStatus.CANCELLED else DownloadStatus.FAILED,
                        message = errorMsg,
                        // The unfinished file is deleted (see finalizeMediaStoreEntry): another try starts a new one
                        targetUri = null,
                        bytesPerSecond = 0L,
                        etaSeconds = null,
                        progressFraction = if (e is CancellationException) 0f else currentState.activeDownloads[key]?.progressFraction ?: 0f,
                        downloadedBytes = if (e is CancellationException) 0L else currentState.activeDownloads[key]?.downloadedBytes ?: 0L
                    )
                    if (updatedDownload != null) {
                        currentState.copy(
                            activeDownloads = currentState.activeDownloads + (key to updatedDownload),
                            fileDownloadErrorMessage = if (e !is CancellationException) errorMsg else null
                        )
                    } else currentState
                }
            } finally {
                try {
                    outputStream?.close()
                } catch (e: IOException) {
                    Log.e(TAG, "Error closing output stream for ${node.name}: ${e.message}", e)
                }
                targetUri?.let {
                    // NonCancellable: a cancelled coroutine could otherwise not clean up the partial file
                    withContext(NonCancellable) { finalizeMediaStoreEntry(it, downloadSuccessful) }
                }
                // Reported after the file was finalized, so the notification can open it
                if (batchId == null) transfers.finish(transferId, outcome, outcomeMessage, if (downloadSuccessful) targetUri else null, node.mimeType)
            }
        }
        if (batchId == null) transfers.start(
            TransferInfo(transferId, TransferKind.DOWNLOAD, node.name, totalBytes = size.takeIf { it > 0 }),
            onCancel = {
                downloadJob.cancel()
                queue?.cancel()
            }
        )
        downloadJob.start()
        return downloadJob
    }

    /** Stops the download of [node] started on its own (not one of a batch); its unfinished file is deleted. */
    fun cancelDownload(node: StorageNode) = transfers.cancel("download-${node.key}")

    /** Stops every download, single files and batches alike. */
    fun cancelAllDownloads() = transfers.cancelAll(TransferKind.DOWNLOAD)

    // --- Actions on several files at once ---

    /** Downloads the files one after another, several simultaneous downloads can run into the download limits. */
    fun downloadFilesSequentially(files: List<StorageNode>) {
        if (files.isEmpty()) return
        if (files.size == 1) {
            initiateDownloadFile(files.first())
            return
        }
        // The total is only known when every file has a size
        val totalBytes = files.map { it.size }.takeIf { sizes -> sizes.all { it != null } }?.sumOf { it ?: 0L }
        _uiState.update { it.copy(downloadBatch = DownloadBatch(totalFiles = files.size, totalBytes = totalBytes)) }
        val batchId = "download-batch-${System.nanoTime()}"
        // The whole batch is one transfer: one notification with its total, and one result when it ends
        val batchJob = viewModelScope.launch(start = CoroutineStart.LAZY) {
            val queue = coroutineContext[Job]
            var savedFiles = 0
            try {
                for (file in files) {
                    ensureActive()
                    // Its own job, not a child of the batch's: cancelling the batch has to stop it too
                    val download = initiateDownloadFile(file, queue, batchId)
                    try {
                        download.join()
                    } catch (e: CancellationException) {
                        download.cancel()
                        throw e
                    }
                    if (_uiState.value.activeDownloads[file.key]?.status == DownloadStatus.COMPLETED) savedFiles++
                    // Counted once the file is over, whether it was saved or failed
                    _uiState.update { state ->
                        val batch = state.downloadBatch ?: return@update state
                        val finishedBytes = state.activeDownloads[file.key]?.downloadedBytes ?: file.size ?: 0L
                        state.copy(downloadBatch = batch.copy(doneFiles = batch.doneFiles + 1, doneBytes = batch.doneBytes + finishedBytes))
                    }
                }
            } finally {
                _uiState.update { it.copy(downloadBatch = null) }
                val outcome = when {
                    coroutineContext[Job]?.isCancelled == true -> TransferOutcome.CANCELLED
                    savedFiles == files.size -> TransferOutcome.COMPLETED
                    else -> TransferOutcome.FAILED
                }
                val message = when (outcome) {
                    TransferOutcome.COMPLETED -> "${files.size} files downloaded."
                    TransferOutcome.FAILED -> "$savedFiles of ${files.size} files downloaded."
                    TransferOutcome.CANCELLED -> null
                }
                transfers.finish(batchId, outcome, message)
            }
        }
        transfers.start(
            TransferInfo(batchId, TransferKind.DOWNLOAD, "${files.size} files", totalBytes = totalBytes),
            onCancel = { batchJob.cancel() }
        )
        batchJob.start()
    }

    /**
     * Downloads the files as zip archive(s) when the provider serves several ids as one archive (Pixeldrain does,
     * with comma separated ids). Otherwise the files are downloaded one after another.
     */
    fun downloadFilesAsZip(files: List<StorageNode>, archiveName: String) {
        if (files.isEmpty()) return
        if (files.size == 1) {
            initiateDownloadFile(files.first())
            return
        }
        if (files.any { it.ref.id == null || it.isDirectory } || ProviderCapability.ARCHIVE_DOWNLOAD !in provider().capabilities) {
            downloadFilesSequentially(files)
            return
        }
        val baseName = archiveName.replace(Regex("[\\\\/:*?\"<>|]"), "_").ifBlank { "files" }
        val chunks = files.chunked(MAX_FILES_PER_ZIP)
        val archives = chunks.mapIndexed { index, chunk ->
            StorageNode(
                ref = StorageRef(id = chunk.joinToString(",") { it.ref.id.orEmpty() }),
                name = if (chunks.size == 1) "$baseName.zip" else "$baseName (part ${index + 1}).zip",
                isDirectory = false,
                size = chunk.sumOf { it.size ?: 0L },
                createdAt = chunk.first().createdAt,
                mimeType = "application/zip"
            )
        }
        downloadFilesSequentially(archives)
    }

    /** Checks the preconditions of an action on several files and marks it as running. */
    private fun beginBulkOperation(): Boolean {
        if (needsKeyButMissing()) {
            _uiState.update { it.copy(operationError = API_KEY_MISSING) }
            return false
        }
        if (_uiState.value.isBulkOperationRunning) return false
        _uiState.update { it.copy(isBulkOperationRunning = true, operationMessage = null, operationError = null) }
        return true
    }

    private fun failureSummary(failures: List<String>): String =
        failures.take(3).joinToString("; ") + if (failures.size > 3) " and ${failures.size - 3} more" else ""

    /** There is no endpoint to delete several files everywhere, so this deletes them one by one. */
    fun deleteFiles(files: List<StorageNode>) {
        val targets = files.distinctBy { it.key }
        if (targets.isEmpty() || !beginBulkOperation()) return
        viewModelScope.launch {
            val deletedKeys = mutableSetOf<String>()
            val failures = mutableListOf<String>()
            for (file in targets) {
                when (val response = deleteNode(file)) {
                    is ApiResponse.Success -> deletedKeys.add(file.key)
                    is ApiResponse.Error -> failures.add("${file.name} (${response.error.message.ifBlank { "unknown error" }})")
                }
            }
            _uiState.update {
                val openedFileDeleted = it.fileInfo?.key in deletedKeys
                it.copy(
                    isBulkOperationRunning = false,
                    userFilesList = it.userFilesList.filterNot { file -> file.key in deletedKeys },
                    fileInfo = if (openedFileDeleted) null else it.fileInfo,
                    activeDownloads = it.activeDownloads.filterKeys { key -> key !in deletedKeys },
                    operationMessage = if (failures.isEmpty()) {
                        if (deletedKeys.size == 1) "File deleted." else "${deletedKeys.size} files deleted."
                    } else null,
                    operationError = if (failures.isEmpty()) null
                    else "Deleted ${deletedKeys.size} of ${targets.size} files. Failed: ${failureSummary(failures)}"
                )
            }
        }
    }

    /** Copies the files into a folder, this is a single request for any number of files. */
    fun addFilesToFilesystem(fileIds: List<String>, directoryPath: String) {
        val ids = fileIds.distinct()
        if (ids.isEmpty() || !beginBulkOperation()) return
        viewModelScope.launch {
            val browse = provider().browse
            val response = browse?.importFiles(directoryPath, ids)
                ?: ApiResponse.Error(ProviderError("not_supported", "This host can't add files to a folder."))
            when (response) {
                is ApiResponse.Success -> _uiState.update {
                    it.copy(
                        isBulkOperationRunning = false,
                        operationMessage = (if (ids.size == 1) "File added to " else "${ids.size} files added to ") + directoryPath.trim('/')
                    )
                }
                is ApiResponse.Error -> _uiState.update {
                    it.copy(
                        isBulkOperationRunning = false,
                        operationError = "Could not add the files: ${response.error.message.ifBlank { "unknown error" }}"
                    )
                }
            }
        }
    }

    fun createListFromFiles(fileIds: List<String>, title: String) {
        val ids = fileIds.distinct()
        if (ids.isEmpty() || !beginBulkOperation()) return
        viewModelScope.launch {
            val lists = provider().lists
            val response = lists?.create(title, ids)
                ?: ApiResponse.Error(ProviderError("not_supported", "This host has no lists."))
            when (response) {
                is ApiResponse.Success -> _uiState.update {
                    it.copy(
                        isBulkOperationRunning = false,
                        operationMessage = "List created" + linkSuffix(response.data)
                    )
                }
                is ApiResponse.Error -> _uiState.update {
                    it.copy(
                        isBulkOperationRunning = false,
                        operationError = "Could not create the list: ${response.error.message.ifBlank { "unknown error" }}"
                    )
                }
            }
        }
    }

    /** A link to a list, for the hosts that have one; the id on its own otherwise. */
    private fun linkSuffix(listId: String): String =
        if (provider().kind == ProviderKind.PIXELDRAIN) ": pixeldrain.com/l/$listId" else "."

    /** The lists of the user which can be changed, for choosing the list to add files to. */
    suspend fun loadEditableLists(): ApiResponse<List<FileList>> {
        if (needsKeyButMissing()) return ApiResponse.Error(ProviderError("api_key_missing", API_KEY_MISSING))
        val lists = provider().lists ?: return ApiResponse.Error(ProviderError("not_supported", "This host has no lists."))
        return when (val response = lists.lists()) {
            is ApiResponse.Success -> ApiResponse.Success(response.data.filter { it.canEdit })
            is ApiResponse.Error -> ApiResponse.Error(response.error)
        }
    }

    /** Adds files to a list; files which are in it already stay where they are. */
    fun addFilesToList(list: FileList, fileIds: List<String>) {
        val newIds = fileIds.distinct()
        if (newIds.isEmpty() || !beginBulkOperation()) return
        viewModelScope.launch {
            var added = 0
            val result = rewriteList(list) { current ->
                (current + newIds).distinct().also { added = it.size - current.size }
            }
            finishListOperation(result) {
                when {
                    added == 0 -> "Everything was in \"${list.title}\" already."
                    added == 1 -> "1 file added to \"${list.title}\"."
                    else -> "$added files added to \"${list.title}\"."
                } + it
            }
        }
    }

    /**
     * Removes files from a list, a list can't be empty so removing all of its files deletes the list. Files
     * are only taken out of the list, they are not deleted.
     */
    fun removeFilesFromList(list: FileList, fileIds: List<String>) {
        val removeIds = fileIds.toSet()
        if (removeIds.isEmpty() || !beginBulkOperation()) return
        viewModelScope.launch {
            var removed = 0
            val result = rewriteList(list) { current ->
                current.filterNot { it in removeIds }.also { removed = current.size - it.size }
            }
            finishListOperation(result) {
                when {
                    result is ListRewrite.Deleted -> "The list \"${list.title}\" was deleted, it had no files left."
                    removed == 1 -> "1 file removed from \"${list.title}\"."
                    else -> "$removed files removed from \"${list.title}\"."
                } + it
            }
        }
    }

    private sealed class ListRewrite {
        object Updated : ListRewrite()
        object Deleted : ListRewrite()
        /** The list was replaced by a new one with another id, see [rewriteList]. */
        class Replaced(val newId: String) : ListRewrite()
        class Failed(val message: String) : ListRewrite()
    }

    /**
     * Gives a list other files: the current files are fetched, [transform] makes the new list of ids out of
     * them, and the list is changed in place. If the host doesn't accept that, the list is rebuilt: a new list
     * with the same title is created first and only when that worked the old one is deleted, so nothing is lost
     * when a step fails. A rebuilt list has a new id, so its old links stop working.
     */
    private suspend fun rewriteList(list: FileList, transform: (current: List<String>) -> List<String>): ListRewrite {
        val lists = provider().lists ?: return ListRewrite.Failed("This host has no lists.")
        val current = when (val response = lists.listContents(list.id)) {
            is ApiResponse.Success -> response.data.files.mapNotNull { it.ref.id }
            is ApiResponse.Error -> return ListRewrite.Failed("Could not read the list: ${response.error.message}")
        }
        val newIds = transform(current)
        if (newIds == current) return ListRewrite.Updated
        if (newIds.isEmpty()) {
            return when (val response = lists.delete(list.id)) {
                is ApiResponse.Success -> ListRewrite.Deleted
                is ApiResponse.Error -> ListRewrite.Failed("Could not delete the list: ${response.error.message}")
            }
        }

        val updateError = when (val response = lists.update(list.id, list.title, newIds)) {
            is ApiResponse.Success -> return ListRewrite.Updated
            is ApiResponse.Error -> response.error
        }
        // These say the request itself is the problem (or it never arrived), a rebuilt list would fail or be a copy
        val code = updateError.code
        if (code.startsWith("network_exception") || code in setOf("forbidden", "authentication_required", "api_key_missing")) {
            return ListRewrite.Failed("Could not change the list: ${updateError.message}")
        }

        val newId = when (val created = lists.create(list.title, newIds)) {
            is ApiResponse.Success -> created.data
            is ApiResponse.Error -> return ListRewrite.Failed("Could not change the list: ${created.error.message}")
        }
        return when (val deleted = lists.delete(list.id)) {
            is ApiResponse.Success -> ListRewrite.Replaced(newId)
            is ApiResponse.Error -> ListRewrite.Failed(
                "The list was rebuilt as a new list, but the old one could not be deleted: ${deleted.error.message}"
            )
        }
    }

    /** Publishes the outcome of a list change; [message] gets the extra text which goes with the outcome. */
    private fun finishListOperation(result: ListRewrite, message: (extra: String) -> String) {
        _uiState.update {
            when (result) {
                is ListRewrite.Failed -> it.copy(isBulkOperationRunning = false, operationError = result.message)
                is ListRewrite.Replaced -> it.copy(
                    isBulkOperationRunning = false,
                    operationMessage = message(
                        if (provider().kind == ProviderKind.PIXELDRAIN) " The list has a new link: pixeldrain.com/l/${result.newId}"
                        else " The list has a new id: ${result.newId}"
                    )
                )
                else -> it.copy(isBulkOperationRunning = false, operationMessage = message(""))
            }
        }
    }

    fun clearOperationMessage() {
        _uiState.update { it.copy(operationMessage = null) }
    }

    fun clearOperationError() {
        _uiState.update { it.copy(operationError = null) }
    }

    fun clearDownloadMessages() {
        _uiState.update {
            it.copy(
                fileDownloadSuccessMessage = null,
                fileDownloadOpen = null,
                fileDownloadErrorMessage = null
            )
        }
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
