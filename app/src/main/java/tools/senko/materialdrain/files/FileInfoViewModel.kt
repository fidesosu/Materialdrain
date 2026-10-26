package tools.senko.materialdrain.files

import tools.senko.materialdrain.provider.api.ErrorCodes
import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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
import java.io.FilterOutputStream
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
import tools.senko.materialdrain.ui.media.QueueSong
import tools.senko.materialdrain.ui.media.SongQueue
import android.net.Uri
import tools.senko.materialdrain.provider.api.StorageProvider
import tools.senko.materialdrain.provider.api.StorageRef
import tools.senko.materialdrain.util.formatSize
import tools.senko.materialdrain.R

internal const val MAX_TEXT_PREVIEW_FETCH_SIZE_BYTES = 1 * 1024 * 1024 // 1MB
private const val MAX_TEXT_PREVIEW_DISPLAY_LENGTH = 8 * 1024 // 8KB

/** The most of a text that's loaded when it's asked for anyway, past [MAX_TEXT_PREVIEW_FETCH_SIZE_BYTES] (see loadWholeText). */
internal const val MAX_TEXT_LOAD_ANYWAY_BYTES = 16 * 1024 * 1024
private const val API_KEY_MISSING = "Log in or add an API key in Settings."

/** Passes at most [limit] bytes on to [target]: writing more stops the copy with an IOException, and sets [overflowed]. */
private class LimitedOutputStream(target: OutputStream, private val limit: Long) : FilterOutputStream(target) {
    @Volatile
    var overflowed = false
        private set
    private var written = 0L

    override fun write(b: Int) {
        ensureRoom(1)
        out.write(b)
    }

    // FilterOutputStream's own would pass an array on one byte at a time
    override fun write(b: ByteArray, off: Int, len: Int) {
        ensureRoom(len)
        out.write(b, off, len)
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

/** The most of a PDF that's loaded for its preview: Android draws the pages from a whole file on the device. */
internal const val MAX_PDF_PREVIEW_BYTES = 64L * 1024 * 1024

/** The most of a PDF that's loaded without being asked for: a bigger one waits for a tap (see PdfPreviewState.Large). */
private const val MAX_PDF_AUTO_LOAD_BYTES = 16L * 1024 * 1024

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

    /** Whether the details' Download button asks to be tapped again (see AppSettings.confirmDownloadTap). */
    val confirmDownloadTap: StateFlow<Boolean> get() = appSettings.confirmDownloadTap

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
                textPreviewErrorMessage = null, textPreviewCanLoadAnyway = false,
                pdfPreview = null
            )
        }
    }

    private var pdfJob: Job? = null

    /**
     * Loads a PDF into the app's cache for its preview: Android draws a PDF's pages from a file on the device (see
     * PdfPreview). One already there from before is used again; the cache holds only the last few, see [trimPdfCache].
     * A big one waits to be [asked] for, see [loadPdfPreview].
     */
    private fun fetchPdfPreview(node: StorageNode, asked: Boolean = false) {
        pdfJob?.cancel()
        if (!isPdf(node.name, node.previewMimeType())) {
            _uiState.update { it.copy(pdfPreview = null) }
            return
        }
        val size = node.size
        val tooBig = PdfPreviewState.Failed(
            "Too big to preview (max ${formatSize(MAX_PDF_PREVIEW_BYTES)})."
        )
        if (size != null && size > MAX_PDF_PREVIEW_BYTES) {
            _uiState.update { it.copy(pdfPreview = tooBig) }
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
        if (!asked && size != null && size > MAX_PDF_AUTO_LOAD_BYTES) {
            _uiState.update { it.copy(pdfPreview = PdfPreviewState.Large(size)) }
            return
        }
        _uiState.update { it.copy(pdfPreview = PdfPreviewState.Loading(0L, size)) }
        pdfJob = viewModelScope.launch {
            val outcome = withContext(Dispatchers.IO) {
                val partial = File(folder, "$name.part")
                var lastShown = 0L
                try {
                    folder.mkdirs()
                    // Capped as well: a host which doesn't tell the size mustn't fill the device with a huge file
                    val out = LimitedOutputStream(FileOutputStream(partial), MAX_PDF_PREVIEW_BYTES)
                    val response = try {
                        out.use {
                            downloadNodeTo(node, it) { read, total ->
                                // Often enough to move the bar, not on every buffer
                                if (read - lastShown >= 256 * 1024 || read == total) {
                                    lastShown = read
                                    _uiState.update { state -> state.copy(pdfPreview = PdfPreviewState.Loading(read, total ?: size)) }
                                }
                            }
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        // The cap stops the copy by throwing, whichever way the host passes that on
                        ApiResponse.Error(ProviderError("preview_failed", e.message.orEmpty()))
                    }
                    when {
                        out.overflowed -> tooBig
                        response is ApiResponse.Error -> PdfPreviewState.Failed(response.error.message.ifBlank { "The PDF couldn't be loaded." })
                        partial.renameTo(file) -> {
                            trimPdfCache(folder, keep = file)
                            PdfPreviewState.Ready(file)
                        }
                        else -> PdfPreviewState.Failed("The PDF couldn't be saved for preview.")
                    }
                } catch (e: IOException) {
                    // The cache can't be written to, e.g. the device is full
                    PdfPreviewState.Failed("The PDF couldn't be saved for preview.")
                } finally {
                    // Gone already once it's renamed; otherwise what was loaded is of no use, cancelled or not
                    partial.delete()
                }
            }
            _uiState.update { it.copy(pdfPreview = outcome) }
        }
    }

    /** Loads the PDF's preview whatever its size (up to the most): tapped while it waited, or its copy left the cache. */
    fun loadPdfPreview() {
        uiState.value.fileInfo?.let { fetchPdfPreview(it, asked = true) }
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
    ): ApiResponse<Long> = downloadNode(provider(), node, outputStream, application.resources, onProgress)

    /**
     * Loads the text of [fileInfo] for its preview, up to [limit] bytes: a bigger one says so, and (under the limit of
     * [loadWholeText]) that it can be loaded anyway.
     */
    private fun fetchTextFilePreviewContent(fileInfo: StorageNode, limit: Int = MAX_TEXT_PREVIEW_FETCH_SIZE_BYTES) {
        previewJob?.cancel()
        // Not text: nothing to say about it (the details show the file's kind already), but the text of the previous file
        // mustn't stay on screen
        if (!isTextFile(fileInfo.name, fileInfo.previewMimeType())) {
            _uiState.update { it.copy(isLoadingTextPreview = false, textPreviewContent = null, textPreviewFullContent = null, textPreviewErrorMessage = null, textPreviewCanLoadAnyway = false) }
            return
        }

        val maxSize = formatSize(limit.toLong())
        val size = fileInfo.size ?: 0L
        val canLoadAnyway = limit < MAX_TEXT_LOAD_ANYWAY_BYTES && size <= MAX_TEXT_LOAD_ANYWAY_BYTES
        if (size > limit) {
            _uiState.update {
                it.copy(
                    isLoadingTextPreview = false, textPreviewContent = null, textPreviewFullContent = null,
                    textPreviewErrorMessage = application.getString(R.string.files_too_big_to_preview_max, maxSize),
                    textPreviewCanLoadAnyway = canLoadAnyway
                )
            }
            return
        }

        _uiState.update { it.copy(isLoadingTextPreview = true, textPreviewContent = null, textPreviewFullContent = null, textPreviewErrorMessage = null, textPreviewCanLoadAnyway = false) }
        previewJob = viewModelScope.launch {
            // Capped as well: a host which doesn't tell the size (or tells it wrong) mustn't fill the memory with a huge file
            val bytes = ByteArrayOutputStream()
            val buffer = LimitedOutputStream(bytes, limit.toLong())
            val response = try {
                downloadNodeTo(fileInfo, buffer) { _, _ -> }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // The cap stops the copy by throwing, whichever way the host passes that on
                ApiResponse.Error(ProviderError("preview_failed", e.message.orEmpty()))
            }
            if (buffer.overflowed) {
                _uiState.update {
                    it.copy(
                        isLoadingTextPreview = false,
                        textPreviewErrorMessage = application.getString(R.string.files_too_big_to_preview_max, maxSize),
                        textPreviewCanLoadAnyway = limit < MAX_TEXT_LOAD_ANYWAY_BYTES
                    )
                }
                return@launch
            }
            when (response) {
                is ApiResponse.Success -> {
                    val content = bytes.toString(Charsets.UTF_8.name())
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
                            textPreviewErrorMessage = response.error.message.ifBlank { application.getString(R.string.files_error_fetching_text_preview) }
                        )
                    }
                }
            }
        }
    }

    // The files of the list the details were opened from, in its order, for a swipe to the next or previous one (Look and
    // feel, Swipe between files); [neighboursByNode] when they're shown from the node itself, as the filesystem's are
    private var neighbours: List<StorageNode> = emptyList()
    private var neighboursByNode = false

    /** The files beside the one about to be opened, see [openNeighbour]. */
    fun setNeighbours(files: List<StorageNode>, byNode: Boolean) {
        neighbours = files
        neighboursByNode = byNode
    }

    /**
     * The file after (or, not [next], before) the one shown, in the list it was opened from; [withinGroup], the nearest
     * one that's of the shown one's [FileGroup] (no folder), the others passed over. Null when there's none.
     */
    private fun neighbour(next: Boolean, withinGroup: Boolean = false): StorageNode? {
        val shown = _uiState.value.fileInfo ?: return null
        val index = neighbours.indexOfFirst { it.key == shown.key }
        if (index == -1) return null
        val group = if (withinGroup) shown.group() else null
        val step = if (next) 1 else -1
        var at = index + step
        while (at in neighbours.indices) {
            val candidate = neighbours[at]
            if (group == null || (!candidate.isDirectory && candidate.group() == group)) return candidate
            at += step
        }
        return null
    }

    private fun StorageNode.group(): FileGroup = fileGroup(name, previewMimeType())

    /** [node] as a song of the background player's queue, or null when it isn't a song (or has no address). */
    fun queueSongOf(node: StorageNode): QueueSong? {
        if (node.isDirectory || node.previewMimeType()?.startsWith("audio/") != true) return null
        val url = rawUrlFor(node) ?: return null
        return QueueSong(Uri.parse(url), node.name, thumbnailFor(node))
    }

    /**
     * The songs of the list [node] was opened from, in its order, to go on with after it when it's played in the
     * background (see AudioPlayerPreview); null when it wasn't opened from a list.
     */
    fun songQueueFor(node: StorageNode): SongQueue? {
        val songs = neighbours.filter { it.previewMimeType()?.startsWith("audio/") == true }
        val start = songs.indexOfFirst { it.key == node.key }
        if (start == -1) return null
        val queue = songs.mapNotNull { queueSongOf(it) }
        // A song without an address would shift the others: then it's played on its own
        return if (queue.size == songs.size) SongQueue(queue, start) else null
    }

    /** Whether there's a file to swipe to, see [openNeighbour]. */
    fun hasNeighbour(next: Boolean): Boolean = neighbour(next) != null

    /**
     * Opens the file after (or, not [next], before) the one shown, in the list it was opened from; [withinGroup] (the
     * fullscreen view), the nearest one of its kind (see [FileGroup]). False when there's none.
     */
    fun openNeighbour(next: Boolean, withinGroup: Boolean = false): Boolean {
        val target = neighbour(next, withinGroup) ?: return false
        // At once from the list's own entry, so the page comes in without waiting for the host; a host's file has its
        // full details (Pixeldrain's views and downloads) filled in once they come
        setFileInfoFromNode(target)
        if (!neighboursByNode) completeFileInfo(target)
        return true
    }

    /**
     * Replaces [node], shown from a list's entry, with the host's full details of it, without a loading state in between.
     * Its preview is only loaded again when the host gives it another type than the entry did.
     */
    private fun completeFileInfo(node: StorageNode) {
        val id = node.ref.id ?: return
        val store = provider().fileStore ?: return
        _uiState.update { it.copy(fileInfoCompleting = true) }
        fileInfoJob = viewModelScope.launch {
            val full = (store.fileInfo(StorageRef(id = id)) as? ApiResponse.Success)?.data
            if (_uiState.value.fileInfo?.key != node.key) return@launch
            if (full == null) {
                _uiState.update { it.copy(fileInfoCompleting = false) }
                return@launch
            }
            _uiState.update { it.copy(fileInfo = full, fileInfoCompleting = false) }
            if (full.previewMimeType() != node.previewMimeType()) showPreview(full, withArchive = showsArchive(full))
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
            _uiState.update { it.copy(fileInfoErrorMessage = application.getString(R.string.files_enter_a_file_id), isLoadingFileInfo = false) }
            return
        }
        fileInfoJob?.cancel()
        _uiState.update { it.copy(isLoadingFileInfo = true, fileInfo = null, fileInfoErrorMessage = null) }
        clearTextPreviewStates()
        fileInfoJob = viewModelScope.launch {
            val store = provider().fileStore
            if (store == null) {
                _uiState.update { it.copy(isLoadingFileInfo = false, fileInfoErrorMessage = application.getString(R.string.files_this_host_can_t_show)) }
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
                            fileInfoErrorMessage = response.error.message.ifBlank { application.getString(R.string.files_unknown_error_fetching_file_info) },
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
     * the filesystem's, not the files of the account), or a zip the app can read in parts itself (see archivesOf), unless
     * the tab it's opened from leaves that out.
     */
    fun showsArchive(node: StorageNode): Boolean =
        isArchiveName(node.name) && ProviderCapability.ARCHIVE_BROWSE !in tabLimits && archivesOf(provider(), application.resources)?.canOpen(node.ref) == true

    /** Lists the folder [inside] of the [archive] for the details, see FileDetailsScreen. */
    private fun loadArchive(archive: StorageRef, inside: String) {
        val archives = archivesOf(provider(), application.resources) ?: return
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
                        archive = ArchiveView(archive, inside, entries = null, error = result.error.message.ifBlank { application.getString(R.string.files_this_file_can_t_be) })
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
                    fileInfoErrorMessage = application.getString(R.string.files_folders_have_no_file_details)
                )
            }
            clearTextPreviewStates()
            return
        }
        _uiState.update {
            it.copy(
                isLoadingFileInfo = false,
                fileInfo = node,
                fileInfoErrorMessage = null,
                fileInfoCompleting = false
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

    /** Loads a text that's too big for the preview anyway, up to [MAX_TEXT_LOAD_ANYWAY_BYTES]: asked for in its details. */
    fun loadWholeText() {
        uiState.value.fileInfo?.let { fetchTextFilePreviewContent(it, limit = MAX_TEXT_LOAD_ANYWAY_BYTES) }
    }

    /** Loads the preview the data saver held back, see [showPreview]. */
    fun loadPreview() {
        val node = uiState.value.fileInfo ?: return
        _uiState.update { it.copy(previewHeld = false) }
        fetchTextFilePreviewContent(node)
        // The held preview said how big the PDF is, and was tapped anyway
        fetchPdfPreview(node, asked = true)
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
            _uiState.update { it.copy(userFilesListErrorMessage = application.getString(R.string.files_this_host_has_no_list), isLoadingUserFiles = false) }
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
                    val errorMsg = response.error.forDisplay().ifBlank { application.getString(R.string.files_unknown_error_fetching_user_files) }
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
            messages.show(application.getString(R.string.files_log_in_to_delete_files), long = true)
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
                    messages.show(application.getString(R.string.files_file_deleted_successfully))
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
                    messages.show(application.getString(R.string.files_delete_failed, response.error.message.ifBlank { "Unknown error deleting file." }), long = true)
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
                val store = current.fileStore ?: return ApiResponse.Error(ProviderError(ErrorCodes.NOT_SUPPORTED, application.getString(R.string.files_this_host_can_t_delete)))
                store.delete(StorageRef(id = id))
            } else {
                val browse = current.browse ?: return ApiResponse.Error(ProviderError(ErrorCodes.NOT_SUPPORTED, application.getString(R.string.files_this_host_can_t_delete_2)))
                browse.delete(node.ref.path, recursive = node.isDirectory)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            coroutineContext.ensureActive()
            ProviderLog.e("Files", "deleting '${node.name}' failed: ${e.message}", e)
            ApiResponse.Error(ProviderError(ErrorCodes.NETWORK_ERROR, e.message ?: application.getString(R.string.files_the_host_couldn_t_be)))
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
    fun downloadFilesSequentially(files: List<StorageNode>, title: String? = null) = downloadManager.downloadSequentially(files, title = title)

    /** Downloads the files as zip archive(s), made by the host where it can, see DownloadManager.downloadAsZip. */
    fun downloadFilesAsZip(files: List<StorageNode>, archiveName: String) = downloadManager.downloadAsZip(files, archiveName)

    /** Downloads files and folders of a folder browser, each file on its own or all in one zip, see DownloadManager.downloadItems. */
    fun downloadItems(items: List<StorageNode>, style: DownloadStyle, archiveName: String) =
        downloadManager.downloadItems(items, style, archiveName)

    /** A download of folders waiting to be confirmed for being big, see DownloadManager.confirmation. */
    val downloadConfirmation: StateFlow<DownloadConfirmation?> get() = downloadManager.confirmation

    fun confirmDownload() = downloadManager.confirmDownload()

    fun dismissDownloadConfirmation() = downloadManager.dismissConfirmation()

    /** Shares the file itself with another app, for a host whose files have no link, see DownloadManager.shareFile. */
    fun shareFile(node: StorageNode) = downloadManager.shareFile(node)

    /** Downloads the files of [list] (from the lists overview, where it isn't open), each on its own or all in one zip. */
    fun downloadList(list: FileList, style: DownloadStyle) {
        val lists = provider().lists ?: return
        viewModelScope.launch {
            when (val response = lists.listContents(list.id)) {
                is ApiResponse.Success -> {
                    val files = response.data.files
                    when {
                        files.isEmpty() -> messages.show(application.getString(R.string.files_has_no_files, list.title))
                        style == DownloadStyle.ZIP -> downloadManager.downloadAsZip(files, list.title)
                        else -> downloadManager.downloadSequentially(files, title = list.title)
                    }
                }
                is ApiResponse.Error -> messages.show(application.getString(R.string.files_couldn_t_open, list.title, response.error.message), long = true)
            }
        }
    }


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
                finishBulkOperation(if (deletedKeys.size == 1) application.getString(R.string.files_file_deleted) else application.getString(R.string.files_files_deleted, deletedKeys.size))
            } else {
                finishBulkOperation(null, application.getString(R.string.files_deleted_of_files_failed, deletedKeys.size, targets.size, failureSummary(failures)))
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
                ?: ApiResponse.Error(ProviderError(ErrorCodes.NOT_SUPPORTED, application.getString(R.string.files_this_host_can_t_add)))
            when (response) {
                is ApiResponse.Success -> finishBulkOperation(
                    (if (ids.size == 1) application.getString(R.string.files_file_added_to) else application.getString(R.string.files_files_added_to, ids.size)) + directoryPath.trim('/')
                )
                is ApiResponse.Error -> finishBulkOperation(
                    null, application.getString(R.string.files_could_not_add_the_files, response.error.message.ifBlank { "unknown error" })
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
                ?: ApiResponse.Error(ProviderError(ErrorCodes.NOT_SUPPORTED, application.getString(R.string.files_this_host_has_no_lists)))
            when (response) {
                is ApiResponse.Success -> finishBulkOperation(application.getString(R.string.files_list_created) + linkSuffix(response.data))
                is ApiResponse.Error -> finishBulkOperation(
                    null, application.getString(R.string.files_could_not_create_the_list, response.error.message.ifBlank { "unknown error" })
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
        val lists = provider().lists ?: return ApiResponse.Error(ProviderError(ErrorCodes.NOT_SUPPORTED, application.getString(R.string.files_this_host_has_no_lists)))
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
                    added == 0 -> application.getString(R.string.files_everything_was_in_already, list.title)
                    added == 1 -> application.getString(R.string.files_1_file_added_to, list.title)
                    else -> application.getString(R.string.files_files_added_to_2, added, list.title)
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
                    result is ListRewrite.Deleted -> application.getString(R.string.files_the_list_was_deleted_it, list.title)
                    removed == 1 -> application.getString(R.string.files_1_file_removed_from, list.title)
                    else -> application.getString(R.string.files_files_removed_from, removed, list.title)
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
        val lists = provider().lists ?: return ListRewrite.Failed(application.getString(R.string.files_this_host_has_no_lists))
        val current = when (val response = lists.listContents(list.id)) {
            is ApiResponse.Success -> response.data.files.mapNotNull { it.ref.id }
            is ApiResponse.Error -> return ListRewrite.Failed(application.getString(R.string.files_could_not_read_the_list, response.error.message))
        }
        val newIds = transform(current)
        if (newIds == current) return ListRewrite.Updated
        if (newIds.isEmpty()) {
            return when (val response = lists.delete(list.id)) {
                is ApiResponse.Success -> ListRewrite.Deleted
                is ApiResponse.Error -> ListRewrite.Failed(application.getString(R.string.files_could_not_delete_the_list, response.error.message))
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
            return ListRewrite.Failed(application.getString(R.string.files_could_not_change_the_list, updateError.message))
        }

        val newId = when (val created = lists.create(list.title, newIds)) {
            is ApiResponse.Success -> created.data
            is ApiResponse.Error -> return ListRewrite.Failed(application.getString(R.string.files_could_not_change_the_list, created.error.message))
        }
        return when (val deleted = lists.delete(list.id)) {
            is ApiResponse.Success -> ListRewrite.Replaced(newId)
            is ApiResponse.Error -> ListRewrite.Failed(
                application.getString(R.string.files_saved_as_a_new_list, deleted.error.message)
            )
        }
    }

    /** Publishes the outcome of a list change; [message] gets the extra text which goes with the outcome. */
    private fun finishListOperation(result: ListRewrite, message: (extra: String) -> String) {
        when (result) {
            is ListRewrite.Failed -> finishBulkOperation(null, result.message)
            is ListRewrite.Replaced -> finishBulkOperation(
                message(
                    provider().listShareUrl(result.newId)?.let { application.getString(R.string.files_the_list_has_a_new, it) }
                        ?: application.getString(R.string.files_the_list_has_a_new_2, result.newId)
                )
            )
            else -> finishBulkOperation(message(""))
        }
    }

    fun setPreserveScrollPosition(preserve: Boolean) {
        _uiState.update { it.copy(shouldPreserveScrollPosition = preserve) }
    }
}
