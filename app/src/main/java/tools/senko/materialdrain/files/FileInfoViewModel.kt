package tools.senko.materialdrain.files

import tools.senko.materialdrain.provider.api.ErrorCodes
import android.app.Application
import android.webkit.MimeTypeMap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.coroutineContext
import tools.senko.materialdrain.ContentChanges
import tools.senko.materialdrain.UiMessages
import tools.senko.materialdrain.auth.SessionManager
import tools.senko.materialdrain.settings.AppSettings
import tools.senko.materialdrain.settings.DataSaver
import tools.senko.materialdrain.settings.SavedOpenedFile
import tools.senko.materialdrain.provider.ProviderConfigStore
import tools.senko.materialdrain.provider.ProviderRegistry
import tools.senko.materialdrain.provider.api.ApiResponse
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
import tools.senko.materialdrain.util.formatSize

internal const val MAX_TEXT_PREVIEW_FETCH_SIZE_BYTES = 1 * 1024 * 1024 // 1MB
private const val MAX_TEXT_PREVIEW_DISPLAY_LENGTH = 8 * 1024 // 8KB
private const val API_KEY_MISSING = "API Key is missing. Please set it in Settings."


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

/** Takes at most [limit] bytes: writing more stops the copy with an IOException, and sets [overflowed]. */
private class LimitedByteArrayOutputStream(private val limit: Int) : ByteArrayOutputStream() {
    @Volatile
    var overflowed = false
        private set

    override fun write(b: Int) {
        ensureRoom(1)
        super.write(b)
    }

    override fun write(b: ByteArray, off: Int, len: Int) {
        ensureRoom(len)
        super.write(b, off, len)
    }

    private fun ensureRoom(len: Int) {
        if (size() + len > limit) {
            overflowed = true
            throw IOException("More than $limit bytes")
        }
    }
}

/** A file that takes at most [limit] bytes, as [LimitedByteArrayOutputStream] does. */
private class LimitedFileOutputStream(file: File, private val limit: Long) : FileOutputStream(file) {
    @Volatile
    var overflowed = false
        private set
    private var written = 0L

    override fun write(b: Int) {
        ensureRoom(1)
        super.write(b)
    }

    override fun write(b: ByteArray, off: Int, len: Int) {
        ensureRoom(len)
        super.write(b, off, len)
    }

    private fun ensureRoom(len: Int) {
        if (written + len > limit) {
            overflowed = true
            throw IOException("More than $limit bytes")
        }
        written += len
    }
}

/** Where PDFs are kept for their preview, in the app's cache, and how many of them. */
private const val PDF_CACHE_FOLDER = "pdf-preview"
private const val PDF_CACHE_KEPT = 5

/** The folder [inside] of the [archive], and its entries once they've loaded. */
data class ArchiveView(val archive: StorageRef, val inside: String, val entries: List<StorageNode>?, val error: String?)

/** Archives which can be looked inside from the file details (see ArchiveOps). */
private val ARCHIVE_EXTENSIONS = setOf("zip", "7z", "rar", "tar", "tgz", "apk")

/** Image formats whose thumbnails are made on the device rather than by the host (see FileInfoViewModel.thumbnailFor). */
private val DEVICE_THUMBNAIL_EXTENSIONS = setOf("heic", "heif", "avif")

fun isArchiveName(name: String): Boolean = name.substringAfterLast('.', "").lowercase() in ARCHIVE_EXTENSIONS

/** Whether a file is a PDF, by its type or (when the host doesn't say) its name. */
fun isPdf(name: String, mimeType: String?): Boolean =
    mimeType == "application/pdf" || name.substringAfterLast('.', "").equals("pdf", ignoreCase = true)

/** The most of a PDF that's loaded for its preview: Android draws the pages from a whole file on the device. */
internal const val MAX_PDF_PREVIEW_BYTES = 64L * 1024 * 1024

/** A PDF's preview: loading into the app's cache, ready to be drawn from [file], or why it can't be shown. */
sealed interface PdfPreviewState {
    data class Loading(val loadedBytes: Long, val totalBytes: Long?) : PdfPreviewState
    data class Ready(val file: File) : PdfPreviewState
    data class Failed(val message: String) : PdfPreviewState
}

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

    // For file deletion (its outcome goes to the snackbar, see UiMessages)
    val isLoadingDeleteFile: Boolean = false,
    val initiateDeleteFile: Boolean = false,
    val fileIdToDelete: String? = null,
    val nodeToDelete: StorageNode? = null,

    val apiKeyMissingError: Boolean = false,
    val apiKey: String = "", // Exposed API Key, only the built-in Pixeldrain uses it

    // Filtering state
    val filterQuery: String = "",
    val showFilterInput: Boolean = false,

    // The contents of an archive shown in the file details, see loadArchive
    val archive: ArchiveView? = null,

    // The data saver holds the preview back (see DataSaver): nothing of it is loaded until loadPreview
    val previewHeld: Boolean = false,

    // A PDF's preview, see fetchPdfPreview
    val pdfPreview: PdfPreviewState? = null,

    // Scroll state preservation
    val shouldPreserveScrollPosition: Boolean = false,

    // An action on several files at once (delete, add to filesystem, create list) is running; its outcome goes to the
    // snackbar, as does the outcome of a download (see UiMessages)
    val isBulkOperationRunning: Boolean = false
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
    private val downloadManager: DownloadManager,
    private val appSettings: AppSettings,
    private val messages: UiMessages,
    private val contentChanges: ContentChanges,
    private val dataSaver: DataSaver
) : ViewModel() {

    private val _uiState = MutableStateFlow(FileInfoUiState())
    val uiState: StateFlow<FileInfoUiState> = _uiState.asStateFlow()

    // What's loading for the file details. Each is cancelled when another file is opened: a slow answer for the file
    // opened before would otherwise land on the details of the one opened after it
    private var fileInfoJob: Job? = null
    private var previewJob: Job? = null
    private var archiveJob: Job? = null

    private val _fileDeleted = Channel<Unit>(Channel.CONFLATED)
    /** A file was deleted with [confirmDeleteFile]: the details of it, when they're open, have nothing left to show. */
    val fileDeleted: Flow<Unit> = _fileDeleted.receiveAsFlow()

    private val _displayedFiles = MutableStateFlow<List<StorageNode>>(emptyList())
    val displayedFiles: StateFlow<List<StorageNode>> = _displayedFiles.asStateFlow()

    init {
        loadApiKey()

        // Choosing another host as the active one reloads the files of that host
        viewModelScope.launch {
            configStore.changes.drop(1).collect { onActiveProviderChanged() }
        }
        // A login, a logout or a saved API key
        viewModelScope.launch {
            sessionManager.apiKey.drop(1).collect { loadApiKey() }
        }

        // Recomputes displayedFiles whenever the list, the shared sorting, or the filter changes
        viewModelScope.launch {
            filteredAndSorted(
                uiState.map { it.userFilesList },
                uiState.map { it.filterQuery },
                appSettings.sortField,
                appSettings.sortAscending
            ).collect { sortedAndFiltered ->
                _displayedFiles.value = sortedAndFiltered
            }
        }
    }

    private fun provider(): StorageProvider = registry.resolve(configStore.activeProviderId.value)

    /** The public link of a node, or null when the active host has none. */
    fun shareUrlFor(node: StorageNode): String? = provider().shareUrl(node)

    /** The public link of the list with [listId], or null when the active host's lists have none. */
    fun listShareUrlFor(listId: String): String? = provider().listShareUrl(listId)

    /** The preview image of a node, from the active host; null when it has none. */
    fun thumbnailFor(node: StorageNode): String? {
        val provider = provider()
        val thumbnail = provider.thumbnailUrl(node)
        // A host whose thumbnails are made on the device already (SMB shares) has nothing to improve on
        if (provider.thumbnailsMadeOnDevice) return thumbnail
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
    fun canDeleteFiles(): Boolean = ProviderCapability.DELETE in provider().capabilities && ProviderCapability.DELETE !in tabLimits

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

    private fun loadApiKey() {
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
        previewJob?.cancel()
        pdfJob?.cancel()
        _uiState.update {
            it.copy(
                isLoadingTextPreview = false,
                textPreviewContent = null, textPreviewFullContent = null,
                textPreviewErrorMessage = null,
                pdfPreview = null
            )
        }
    }

    private var pdfJob: Job? = null

    /**
     * Loads a PDF into the app's cache for its preview: Android draws a PDF's pages from a file on the device (see
     * PdfPreview). One already there from before is used again; the cache holds only the last few, see [trimPdfCache].
     */
    private fun fetchPdfPreview(node: StorageNode) {
        pdfJob?.cancel()
        if (!isPdf(node.name, node.previewMimeType())) {
            _uiState.update { it.copy(pdfPreview = null) }
            return
        }
        val size = node.size
        if (size != null && size > MAX_PDF_PREVIEW_BYTES) {
            _uiState.update {
                it.copy(pdfPreview = PdfPreviewState.Failed("Too big to preview (${formatSize(size)}, at most ${formatSize(MAX_PDF_PREVIEW_BYTES)}). Download it to read it."))
            }
            return
        }
        val folder = File(application.cacheDir, PDF_CACHE_FOLDER)
        // By host and file, and its size and date, so a changed file isn't shown from an older copy
        val name = "${configStore.activeProviderId.value}|${node.key}|${node.size}|${node.modifiedAt ?: node.createdAt}".hashCode().toUInt().toString(16)
        val file = File(folder, "$name.pdf")
        if (file.isFile && file.length() > 0) {
            file.setLastModified(System.currentTimeMillis())
            _uiState.update { it.copy(pdfPreview = PdfPreviewState.Ready(file)) }
            return
        }
        _uiState.update { it.copy(pdfPreview = PdfPreviewState.Loading(0L, size)) }
        pdfJob = viewModelScope.launch {
            val partial = File(folder, "$name.part")
            var lastShown = 0L
            var overflowed = false
            val response = try {
                withContext(Dispatchers.IO) {
                    folder.mkdirs()
                    // Capped as well: a host which doesn't tell the size mustn't fill the device with a huge file
                    val out = LimitedFileOutputStream(partial, MAX_PDF_PREVIEW_BYTES)
                    try {
                        downloadNodeTo(node, out) { read, total ->
                            // Often enough to move the bar, not on every buffer
                            if (read - lastShown >= 256 * 1024 || read == total) {
                                lastShown = read
                                _uiState.update { it.copy(pdfPreview = PdfPreviewState.Loading(read, total ?: size)) }
                            }
                        }
                    } finally {
                        overflowed = out.overflowed
                        out.close()
                    }
                }
            } catch (e: CancellationException) {
                withContext(NonCancellable + Dispatchers.IO) { partial.delete() }
                throw e
            } catch (e: Exception) {
                // The cap stops the copy by throwing, whichever way the host passes that on
                ApiResponse.Error(ProviderError("preview_failed", e.message.orEmpty()))
            }
            if (overflowed) {
                withContext(Dispatchers.IO) { partial.delete() }
                _uiState.update {
                    it.copy(pdfPreview = PdfPreviewState.Failed("Too big to preview (at most ${formatSize(MAX_PDF_PREVIEW_BYTES)}). Download it to read it."))
                }
                return@launch
            }
            val outcome = withContext(Dispatchers.IO) {
                when (response) {
                    is ApiResponse.Success -> if (partial.renameTo(file)) {
                        trimPdfCache(folder, keep = file)
                        PdfPreviewState.Ready(file)
                    } else {
                        partial.delete()
                        PdfPreviewState.Failed("The PDF couldn't be kept for its preview.")
                    }
                    is ApiResponse.Error -> {
                        partial.delete()
                        PdfPreviewState.Failed(response.error.message.ifBlank { "The PDF couldn't be loaded." })
                    }
                }
            }
            _uiState.update { it.copy(pdfPreview = outcome) }
        }
    }

    /** Keeps the cache of PDFs to the last few opened, [keep] (the one just loaded) among them. */
    private fun trimPdfCache(folder: File, keep: File) {
        folder.listFiles { it: File -> it.extension == "pdf" && it != keep }
            ?.sortedByDescending { it.lastModified() }
            ?.drop(PDF_CACHE_KEPT - 1)
            ?.forEach { it.delete() }
    }

    /** Reads the file from the provider, as the file id or the path it has. */
    private suspend fun downloadNodeTo(
        node: StorageNode,
        outputStream: OutputStream,
        onProgress: (Long, Long?) -> Unit
    ): ApiResponse<Long> = downloadNode(provider(), node, outputStream, onProgress)

    private fun fetchTextFilePreviewContent(fileInfo: StorageNode) {
        previewJob?.cancel()
        // Not text: nothing to say about it (the details show the file's kind already), but the text of the previous file
        // mustn't stay on screen
        if (!isTextFile(fileInfo.name, fileInfo.previewMimeType())) {
            _uiState.update { it.copy(isLoadingTextPreview = false, textPreviewContent = null, textPreviewFullContent = null, textPreviewErrorMessage = null) }
            return
        }

        val maxSize = formatSize(MAX_TEXT_PREVIEW_FETCH_SIZE_BYTES.toLong())
        val size = fileInfo.size ?: 0L
        if (size > MAX_TEXT_PREVIEW_FETCH_SIZE_BYTES) {
            _uiState.update { it.copy(isLoadingTextPreview = false, textPreviewContent = null, textPreviewFullContent = null, textPreviewErrorMessage = "File is too large (${formatSize(size)}) for text preview. Max $maxSize.") }
            return
        }

        _uiState.update { it.copy(isLoadingTextPreview = true, textPreviewContent = null, textPreviewFullContent = null, textPreviewErrorMessage = null) }
        previewJob = viewModelScope.launch {
            // Capped as well: a host which doesn't tell the size (or tells it wrong) mustn't fill the memory with a huge file
            val buffer = LimitedByteArrayOutputStream(MAX_TEXT_PREVIEW_FETCH_SIZE_BYTES)
            val response = try {
                downloadNodeTo(fileInfo, buffer) { _, _ -> }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // The cap stops the copy by throwing, whichever way the host passes that on
                ApiResponse.Error(ProviderError("preview_failed", e.message.orEmpty()))
            }
            if (buffer.overflowed) {
                _uiState.update { it.copy(isLoadingTextPreview = false, textPreviewErrorMessage = "File is too large for text preview. Max $maxSize.") }
                return@launch
            }
            when (response) {
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
        fileInfoJob?.cancel()
        _uiState.update { it.copy(isLoadingFileInfo = true, fileInfo = null, fileInfoErrorMessage = null) }
        clearTextPreviewStates()
        fileInfoJob = viewModelScope.launch {
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
                    showPreview(response.data, withArchive = showsArchive(response.data))
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

    /** Shows the folder [inside] of the archive in the details (its top when empty). */
    fun openArchiveFolder(inside: String) {
        val archive = uiState.value.archive?.archive ?: return
        loadArchive(archive, inside)
    }

    /**
     * What the tab the files are opened from takes away (see ScreenConfig.disabledCapabilities): looking inside archives,
     * deleting. Set by the screen whenever another tab is shown.
     */
    private var tabLimits: Set<ProviderCapability> = emptySet()

    fun setTabLimits(disabled: Set<ProviderCapability>) {
        tabLimits = disabled
    }

    /**
     * Whether the details show what's inside [node]: an archive the host can reach to look inside (Pixeldrain only can for
     * the filesystem's, not the files of the account), unless the tab it's opened from leaves that out.
     */
    fun showsArchive(node: StorageNode): Boolean =
        isArchiveName(node.name) && ProviderCapability.ARCHIVE_BROWSE !in tabLimits && provider().archives?.canOpen(node.ref) == true

    /** Lists the folder [inside] of the [archive] for the details, see FileDetailsScreen. */
    private fun loadArchive(archive: StorageRef, inside: String) {
        val archives = provider().archives ?: return
        archiveJob?.cancel()
        _uiState.update { it.copy(archive = ArchiveView(archive, inside, entries = null, error = null)) }
        archiveJob = viewModelScope.launch {
            // Off the main thread, whether or not the host's own call already is
            val result = withContext(Dispatchers.IO) { archives.list(archive, inside) }
            _uiState.update { state ->
                if (state.archive?.archive != archive) return@update state
                when (result) {
                    is ApiResponse.Success -> state.copy(
                        archive = ArchiveView(
                            archive,
                            inside,
                            result.data.sortedWith(compareBy<StorageNode> { !it.isDirectory }.thenBy { it.name.lowercase() }),
                            error = null
                        )
                    )
                    is ApiResponse.Error -> state.copy(
                        archive = ArchiveView(archive, inside, entries = null, error = result.error.message.ifBlank { "This file can't be opened as an archive." })
                    )
                }
            }
        }
    }

    /** Saves one file of the archive shown in the details, as any other download. */
    fun downloadArchiveEntry(entry: StorageNode) {
        initiateDownloadFile(entry)
    }

    /** Shows the details of a file node (from the filesystem or the file list). Folders have no details. */
    fun setFileInfoFromNode(node: StorageNode) {
        fileInfoJob?.cancel()
        archiveJob?.cancel()
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
        _uiState.update { it.copy(archive = null) }
        // An archive shows what's inside it, in the details, where the host can look inside it
        showPreview(node, withArchive = showsArchive(node))
    }

    /** Whether the preview of the file shown now lists an archive's contents too, for [loadPreview]. */
    private var previewWithArchive = false

    /**
     * Loads what the details preview [node] with: its text, and with [withArchive] what's inside it. With the data saver
     * holding previews back, nothing is loaded yet: the details offer to (see [loadPreview]).
     */
    private fun showPreview(node: StorageNode, withArchive: Boolean) {
        previewWithArchive = withArchive
        if (dataSaver.holdPreviews.value) {
            clearTextPreviewStates()
            _uiState.update { it.copy(previewHeld = true) }
            return
        }
        _uiState.update { it.copy(previewHeld = false) }
        fetchTextFilePreviewContent(node)
        fetchPdfPreview(node)
        if (withArchive) loadArchive(node.ref, "")
    }

    /** Loads the preview the data saver held back, see [showPreview]. */
    fun loadPreview() {
        val node = uiState.value.fileInfo ?: return
        _uiState.update { it.copy(previewHeld = false) }
        fetchTextFilePreviewContent(node)
        fetchPdfPreview(node)
        if (previewWithArchive) loadArchive(node.ref, "")
    }

    fun clearFileInfoDisplay() {
        fileInfoJob?.cancel()
        archiveJob?.cancel()
        appSettings.openedFile = null
        _uiState.update { it.copy(fileInfo = null, isLoadingFileInfo = false, archive = null, previewHeld = false) }
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
                            apiKeyMissingError = response.error.code == ErrorCodes.API_KEY_MISSING || response.error.code == ErrorCodes.AUTHENTICATION_REQUIRED
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

    fun cancelDeleteFile() { _uiState.update { it.copy(initiateDeleteFile = false, fileIdToDelete = null, nodeToDelete = null) } }

    fun confirmDeleteFile() {
        val node = _uiState.value.nodeToDelete ?: return
        if (needsKeyButMissing()) {
            _uiState.update { it.copy(initiateDeleteFile = false, apiKeyMissingError = true) }
            messages.show("Delete failed: API Key is missing. Cannot delete file.", long = true)
            return
        }
        _uiState.update { it.copy(isLoadingDeleteFile = true) }
        viewModelScope.launch {
            when (val response = deleteNode(node)) {
                is ApiResponse.Success -> {
                    downloadManager.forget(listOf(node.key))
                    val openedFileDeleted = _uiState.value.fileInfo?.key == node.key
                    if (openedFileDeleted) clearTextPreviewStates()
                    _uiState.update {
                        it.copy(
                            isLoadingDeleteFile = false,
                            initiateDeleteFile = false,
                            fileIdToDelete = null,
                            nodeToDelete = null,
                            userFilesList = it.userFilesList.filterNot { item -> item.key == node.key },
                            fileInfo = if (it.fileInfo?.key == node.key) null else it.fileInfo
                        )
                    }
                    messages.show("File deleted successfully.")
                    _fileDeleted.trySend(Unit)
                    // A deleted file has to disappear from the folder or list it was shown in too
                    contentChanges.notifyChanged()
                }
                is ApiResponse.Error -> {
                    _uiState.update {
                        it.copy(
                            isLoadingDeleteFile = false,
                            initiateDeleteFile = false,
                            fileIdToDelete = null,
                            nodeToDelete = null
                        )
                    }
                    messages.show("Delete failed: ${response.error.message.ifBlank { "Unknown error deleting file." }}", long = true)
                }
            }
        }
    }

    /**
     * Deletes a file by its id when it has one, otherwise by its path (folders are only deleted from the filesystem).
     * Never throws but for a cancel: a failure of the host or the network is an error result, so it's reported (and in a
     * batch the next files still go) instead of crashing the app.
     */
    private suspend fun deleteNode(node: StorageNode): ApiResponse<Unit> {
        val current = provider()
        val id = node.ref.id
        return try {
            if (id != null) {
                val store = current.fileStore ?: return ApiResponse.Error(ProviderError(ErrorCodes.NOT_SUPPORTED, "This host can't delete files by id."))
                store.delete(StorageRef(id = id))
            } else {
                val browse = current.browse ?: return ApiResponse.Error(ProviderError(ErrorCodes.NOT_SUPPORTED, "This host can't delete this file."))
                browse.delete(node.ref.path, recursive = node.isDirectory)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            coroutineContext.ensureActive()
            ProviderLog.e("Files", "deleting '${node.name}' failed: ${e.message}", e)
            ApiResponse.Error(ProviderError(ErrorCodes.NETWORK_ERROR, e.message ?: "The host couldn't be reached."))
        }
    }


    fun clearApiKeyMissingError() {
        _uiState.update {
            it.copy(
                apiKeyMissingError = false,
                userFilesListErrorMessage = if (it.userFilesListErrorMessage == API_KEY_MISSING) null else it.userFilesListErrorMessage
            )
        }
    }

    // --- Downloads (see DownloadManager) ---

    /** The downloads and their progress, see DownloadManager. */
    val downloads: StateFlow<DownloadsState> get() = downloadManager.downloads

    /** Starts the download of [node] and returns its job. */
    fun initiateDownloadFile(node: StorageNode): Job = downloadManager.download(node)

    /** Stops the download of [node] started on its own (not one of a batch); its unfinished file is deleted. */
    fun cancelDownload(node: StorageNode) = downloadManager.cancel(node)

    /** Stops every download, single files and batches alike. */
    fun cancelAllDownloads() = downloadManager.cancelAll()

    // --- Actions on several files at once ---

    /** Downloads the files one after another, several simultaneous downloads can run into the download limits. */
    fun downloadFilesSequentially(files: List<StorageNode>) = downloadManager.downloadSequentially(files)

    /** Downloads the files as zip archive(s) where the host can, see DownloadManager.downloadAsZip. */
    fun downloadFilesAsZip(files: List<StorageNode>, archiveName: String) = downloadManager.downloadAsZip(files, archiveName)


    /** Checks the preconditions of an action on several files and marks it as running. */
    private fun beginBulkOperation(): Boolean {
        if (needsKeyButMissing()) {
            messages.show(API_KEY_MISSING, long = true)
            return false
        }
        if (_uiState.value.isBulkOperationRunning) return false
        _uiState.update { it.copy(isBulkOperationRunning = true) }
        return true
    }

    /**
     * Ends an action on several files: reports how it went (a partial failure may still have changed something), and lets
     * the other screens reload what it may have changed.
     */
    private fun finishBulkOperation(message: String?, error: String? = null) {
        _uiState.update { it.copy(isBulkOperationRunning = false) }
        message?.let { messages.show(it, long = true) }
        error?.let { messages.show(it, long = true) }
        contentChanges.notifyChanged()
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
            downloadManager.forget(deletedKeys)
            _uiState.update {
                val openedFileDeleted = it.fileInfo?.key in deletedKeys
                it.copy(
                    userFilesList = it.userFilesList.filterNot { file -> file.key in deletedKeys },
                    fileInfo = if (openedFileDeleted) null else it.fileInfo
                )
            }
            if (failures.isEmpty()) {
                finishBulkOperation(if (deletedKeys.size == 1) "File deleted." else "${deletedKeys.size} files deleted.")
            } else {
                finishBulkOperation(null, "Deleted ${deletedKeys.size} of ${targets.size} files. Failed: ${failureSummary(failures)}")
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
                ?: ApiResponse.Error(ProviderError(ErrorCodes.NOT_SUPPORTED, "This host can't add files to a folder."))
            when (response) {
                is ApiResponse.Success -> finishBulkOperation(
                    (if (ids.size == 1) "File added to " else "${ids.size} files added to ") + directoryPath.trim('/')
                )
                is ApiResponse.Error -> finishBulkOperation(
                    null, "Could not add the files: ${response.error.message.ifBlank { "unknown error" }}"
                )
            }
        }
    }

    fun createListFromFiles(fileIds: List<String>, title: String) {
        val ids = fileIds.distinct()
        if (ids.isEmpty() || !beginBulkOperation()) return
        viewModelScope.launch {
            val lists = provider().lists
            val response = lists?.create(title, ids)
                ?: ApiResponse.Error(ProviderError(ErrorCodes.NOT_SUPPORTED, "This host has no lists."))
            when (response) {
                is ApiResponse.Success -> finishBulkOperation("List created" + linkSuffix(response.data))
                is ApiResponse.Error -> finishBulkOperation(
                    null, "Could not create the list: ${response.error.message.ifBlank { "unknown error" }}"
                )
            }
        }
    }

    /** A link to a list, for the hosts that have one; the id on its own otherwise. */
    private fun linkSuffix(listId: String): String =
        provider().listShareUrl(listId)?.let { ": $it" } ?: "."

    /** The lists of the user which can be changed, for choosing the list to add files to. */
    suspend fun loadEditableLists(): ApiResponse<List<FileList>> {
        if (needsKeyButMissing()) return ApiResponse.Error(ProviderError(ErrorCodes.API_KEY_MISSING, API_KEY_MISSING))
        val lists = provider().lists ?: return ApiResponse.Error(ProviderError(ErrorCodes.NOT_SUPPORTED, "This host has no lists."))
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
        if (ErrorCodes.isNetworkError(code) ||
            code in setOf(ErrorCodes.FORBIDDEN, ErrorCodes.AUTHENTICATION_REQUIRED, ErrorCodes.API_KEY_MISSING)
        ) {
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
        when (result) {
            is ListRewrite.Failed -> finishBulkOperation(null, result.message)
            is ListRewrite.Replaced -> finishBulkOperation(
                message(
                    provider().listShareUrl(result.newId)?.let { " The list has a new link: $it" }
                        ?: " The list has a new id: ${result.newId}"
                )
            )
            else -> finishBulkOperation(message(""))
        }
    }

    fun setPreserveScrollPosition(preserve: Boolean) {
        _uiState.update { it.copy(shouldPreserveScrollPosition = preserve) }
    }
}
