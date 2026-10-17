package tools.senko.materialdrain.files

import tools.senko.materialdrain.provider.api.ErrorCodes
import android.app.Application
import android.content.ContentResolver
import android.content.ContentValues
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedOutputStream
import java.io.IOException
import java.io.OutputStream
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.coroutineContext
import tools.senko.materialdrain.UiMessageAction
import tools.senko.materialdrain.UiMessages
import tools.senko.materialdrain.provider.ProviderConfigStore
import tools.senko.materialdrain.provider.ProviderRegistry
import tools.senko.materialdrain.provider.api.ApiResponse
import tools.senko.materialdrain.provider.api.ArchiveEntryDetails
import tools.senko.materialdrain.provider.api.ProviderCapability
import tools.senko.materialdrain.provider.api.ProviderError
import tools.senko.materialdrain.provider.api.StorageNode
import tools.senko.materialdrain.provider.api.StorageProvider
import tools.senko.materialdrain.provider.api.StorageRef
import tools.senko.materialdrain.transfer.TransferInfo
import tools.senko.materialdrain.transfer.TransferKind
import tools.senko.materialdrain.transfer.TransferOutcome
import tools.senko.materialdrain.transfer.TransferRegistry
import tools.senko.materialdrain.transfer.TransferSpeedTracker
import tools.senko.materialdrain.transfer.estimateEtaSeconds
import tools.senko.materialdrain.util.formatSize

private const val TAG = "DownloadManager"
private const val DOWNLOAD_WRITE_BUFFER_BYTES = 256 * 1024
private const val MAX_FILES_PER_ZIP = 100
/** How many names a download tries when its own is taken, before MediaStore names it (see insertDownloadEntry). */
private const val MAX_NAME_TRIES = 100

/**
 * The name a download tries next when [name] is taken in Downloads: the number in brackets at the end of the part before
 * the first dot one up, or " (1)" put there when it has none. "senko.jpg" becomes "senko (1).jpg", "senko (2).jpg"
 * "senko (3).jpg", "archive.tar.gz" "archive (1).tar.gz". Brackets anywhere else ("a (2) b.jpg") are left alone, and a
 * dot the name starts with (".bashrc") isn't the first.
 */
internal fun nextDuplicateName(name: String): String {
    val dot = name.indexOf('.', startIndex = 1).let { if (it < 0) name.length else it }
    val base = name.substring(0, dot)
    val extensions = name.substring(dot)
    val number = DUPLICATE_NUMBER.find(base)
    val next = number?.groupValues?.get(1)?.toLongOrNull()?.takeIf { it < Long.MAX_VALUE }?.plus(1)
    return if (number != null && next != null) base.substring(0, number.range.first) + "($next)" + extensions else "$base (1)$extensions"
}

private val DUPLICATE_NUMBER = Regex("""\((\d+)\)$""")

/**
 * [name] with the characters MediaStore doesn't allow in a file name (those FAT doesn't) replaced by "_", as MediaStore
 * would: the name tried is then the name saved, unless it's taken.
 */
private fun validFileName(name: String): String =
    name.map { if (it.code < 0x20 || it.code == 0x7F || it in "\"*/:<>?\\|") '_' else it }.joinToString("")

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

/**
 * The downloads, by node key, and the batch they belong to (null when no batch runs). Kept apart from the screens' UI
 * states: it changes with every progress tick, and the screens which don't show the progress shouldn't redraw for those.
 */
data class DownloadsState(
    val active: Map<String, FileDownloadState> = emptyMap(),
    val batch: DownloadBatch? = null
) {
    /** Whether a download is waiting or running. */
    val isDownloading: Boolean
        get() = batch != null || active.values.any { it.status == DownloadStatus.PENDING || it.status == DownloadStatus.DOWNLOADING }
}

/** Reads the file from [provider] into [outputStream], as the file id or the path it has. */
internal suspend fun downloadNode(
    provider: StorageProvider,
    node: StorageNode,
    outputStream: OutputStream,
    onProgress: (Long, Long?) -> Unit
): ApiResponse<Long> {
    // A file inside an archive is read from the archive
    (node.richDetails as? ArchiveEntryDetails)?.let { entry ->
        val archives = provider.archives
            ?: return ApiResponse.Error(ProviderError(ErrorCodes.NOT_SUPPORTED, "This host can't look inside archives."))
        return archives.read(entry.archive, entry.entryPath, outputStream, onProgress)
    }
    val id = node.ref.id
    return if (id != null) {
        val store = provider.fileStore
            ?: return ApiResponse.Error(ProviderError(ErrorCodes.NOT_SUPPORTED, "This host can't download files by id."))
        // Several ids joined by commas (see DownloadManager.downloadAsZip) are one archive; file ids never contain commas
        if (',' in id) {
            store.downloadArchive(id.split(',').map { StorageRef(id = it) }, outputStream, onProgress)
        } else {
            store.download(StorageRef(id = id), outputStream, onProgress)
        }
    } else {
        val browse = provider.browse
            ?: return ApiResponse.Error(ProviderError(ErrorCodes.NOT_SUPPORTED, "This host can't download this file."))
        browse.download(node.ref.path, outputStream, onProgress)
    }
}

/**
 * Downloads files of the active host into the device's Downloads folder: single files, batches one after another, and
 * several files as zip archives where the host can. Shared by the whole app (see AppContainer), so any screen can start a
 * download and the downloads run on whichever screen is open. Each one is a transfer of [transfers] (the notification, and
 * cancelling it), and its outcome goes to the snackbar ([messages]).
 */
class DownloadManager(
    private val application: Application,
    private val registry: ProviderRegistry,
    private val configStore: ProviderConfigStore,
    private val transfers: TransferRegistry,
    private val messages: UiMessages
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _downloads = MutableStateFlow(DownloadsState())
    val downloads: StateFlow<DownloadsState> = _downloads.asStateFlow()

    private fun provider(): StorageProvider = registry.resolve(configStore.activeProviderId.value)

    /** Changes the download of [key], when there is one. */
    private fun updateDownload(key: String, transform: (FileDownloadState) -> FileDownloadState) {
        _downloads.update { state ->
            val current = state.active[key] ?: return@update state
            state.copy(active = state.active + (key to transform(current)))
        }
    }

    /** Drops what's known of the downloads of [keys], e.g. of files which were deleted. */
    fun forget(keys: Collection<String>) {
        _downloads.update { it.copy(active = it.active - keys.toSet()) }
    }

    private suspend fun prepareDownloadTargetUriAndStream(fileName: String): Pair<Uri?, OutputStream?> {
        return withContext(Dispatchers.IO) {
            val context = application.applicationContext
            val contentResolver = context.contentResolver
            var outputStream: OutputStream? = null
            var uri: Uri? = null
            try {
                uri = insertDownloadEntry(contentResolver, fileName)
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

    /**
     * Makes the entry in Downloads that a download is written to, named [fileName], or when that's taken the next name of
     * [nextDuplicateName]. MediaStore would pick its own for a name that's taken, but puts the number after the last dot
     * ("archive.tar (1).gz") and adds one to a name with a number already ("senko (2) (1).jpg"). Whether a name is taken
     * can't be asked beforehand, as the app doesn't see the files other apps put in Downloads; so each name is tried, and
     * an entry MediaStore renamed is deleted for the next name. After [MAX_NAME_TRIES] MediaStore's own name is kept.
     */
    private fun insertDownloadEntry(contentResolver: ContentResolver, fileName: String): Uri? {
        var candidate = validFileName(fileName)
        repeat(MAX_NAME_TRIES) {
            val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, downloadEntryValues(candidate)) ?: return null
            val savedName = displayNameOf(contentResolver, uri)
            // Renamed for a name that's taken: MediaStore added " (n)" to it. Kept when it changed it otherwise, e.g. cut
            // off a name too long, which another try wouldn't change
            if (savedName == null || savedName == candidate || savedName.length <= candidate.length) return uri
            contentResolver.delete(uri, null, null)
            candidate = nextDuplicateName(candidate)
        }
        return contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, downloadEntryValues(validFileName(fileName)))
    }

    private fun downloadEntryValues(fileName: String) = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
        // The type of the name's extension, never the host's: when the extension doesn't match the type MediaStore adds
        // the type's own (a .rmbak the host calls a zip became .rmbak.zip). An unknown extension gets the generic type
        put(MediaStore.MediaColumns.MIME_TYPE, downloadedFileMimeType(fileName).takeIf { it != "*/*" } ?: "application/octet-stream")
        put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
        put(MediaStore.MediaColumns.IS_PENDING, 1)
    }

    /** The name MediaStore gave the entry at [uri], which is still pending (only its owner sees it, and only when asked). */
    private fun displayNameOf(contentResolver: ContentResolver, uri: Uri): String? {
        val projection = arrayOf(MediaStore.MediaColumns.DISPLAY_NAME)
        val cursor = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            contentResolver.query(uri, projection, Bundle().apply { putInt(MediaStore.QUERY_ARG_MATCH_PENDING, MediaStore.MATCH_INCLUDE) }, null)
        } else {
            @Suppress("DEPRECATION")
            contentResolver.query(MediaStore.setIncludePending(uri), projection, null, null, null)
        }
        return cursor?.use { if (it.moveToFirst()) it.getString(0) else null }
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
    fun download(node: StorageNode, queue: Job? = null, batchId: String? = null): Job {
        val key = node.key
        val size = node.size ?: 0L

        _downloads.update { state ->
            val newDownloadState = FileDownloadState(
                fileId = key,
                fileName = node.name,
                totalBytes = size,
                status = DownloadStatus.PENDING
            )
            state.copy(active = state.active + (key to newDownloadState))
        }

        // A file of a batch reports into the batch's transfer, see downloadSequentially
        val transferId = batchId ?: "download-$key"
        val provider = provider()
        // Started lazily so that the transfer is registered before it can possibly finish
        val downloadJob = scope.launch(Dispatchers.IO, start = CoroutineStart.LAZY) {
            var targetUri: Uri? = null
            var outputStream: OutputStream? = null
            var downloadSuccessful = false
            var outcome = TransferOutcome.FAILED
            var outcomeMessage: String? = null

            try {
                _downloads.value.active[key]?.let {
                    targetUri = it.targetUri
                }
                if (targetUri == null) {
                    val (uri, stream) = prepareDownloadTargetUriAndStream(node.name)
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

                updateDownload(key) { it.copy(status = DownloadStatus.DOWNLOADING, targetUri = targetUri) }

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
                        val batch = _downloads.value.batch
                        transfers.progress(transferId, (batch?.doneBytes ?: 0L) + bytesRead, batch?.totalBytes, speed, eta)
                    }
                    updateDownload(key) { currentDownload ->
                        val effectiveTotal = totalBytes ?: currentDownload.totalBytes
                        currentDownload.copy(
                            downloadedBytes = bytesRead,
                            progressFraction = progress,
                            totalBytes = effectiveTotal,
                            bytesPerSecond = speed,
                            etaSeconds = estimateEtaSeconds(effectiveTotal, bytesRead, speed)
                        )
                    }
                }

                val response = downloadNode(provider, node, outputStream!!, onProgress)
                // A host which turns the stopped copy into an error of its own: it was still a cancel
                ensureActive()
                when (response) {
                    is ApiResponse.Success -> {
                        downloadSuccessful = true
                        val bytesCopied = response.data
                        val message = "File '${node.name}' downloaded (${formatSize(bytesCopied)})."
                        outcome = TransferOutcome.COMPLETED
                        outcomeMessage = message
                        updateDownload(key) {
                            it.copy(
                                status = DownloadStatus.COMPLETED,
                                message = message,
                                downloadedBytes = bytesCopied,
                                progressFraction = 1f,
                                bytesPerSecond = 0L,
                                etaSeconds = null
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
                val hadDownload = _downloads.value.active.containsKey(key)
                updateDownload(key) {
                    it.copy(
                        status = if (e is CancellationException) DownloadStatus.CANCELLED else DownloadStatus.FAILED,
                        message = errorMsg,
                        // The unfinished file is deleted (see finalizeMediaStoreEntry): another try starts a new one
                        targetUri = null,
                        bytesPerSecond = 0L,
                        etaSeconds = null,
                        progressFraction = if (e is CancellationException) 0f else it.progressFraction,
                        downloadedBytes = if (e is CancellationException) 0L else it.downloadedBytes
                    )
                }
                if (hadDownload && e !is CancellationException) messages.show("Download failed: $errorMsg", long = true)
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
                // Reported after the file was finalized, so the notification (and the snackbar's Open) can open it
                val openMimeType = downloadedFileMimeType(node.name)
                if (batchId == null) transfers.finish(transferId, outcome, outcomeMessage, if (downloadSuccessful) targetUri else null, openMimeType)
                if (downloadSuccessful) {
                    messages.show(
                        outcomeMessage.orEmpty(),
                        long = true,
                        action = targetUri?.let { UiMessageAction.OpenDownload(OpenableDownload(it, openMimeType)) }
                    )
                }
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
    fun cancel(node: StorageNode) = transfers.cancel("download-${node.key}")

    /** Stops every download, single files and batches alike. */
    fun cancelAll() = transfers.cancelAll(TransferKind.DOWNLOAD)

    /** Downloads the files one after another, several simultaneous downloads can run into the download limits. */
    fun downloadSequentially(files: List<StorageNode>) {
        if (files.isEmpty()) return
        if (files.size == 1) {
            download(files.first())
            return
        }
        // The total is only known when every file has a size
        val totalBytes = files.map { it.size }.takeIf { sizes -> sizes.all { it != null } }?.sumOf { it ?: 0L }
        _downloads.update { it.copy(batch = DownloadBatch(totalFiles = files.size, totalBytes = totalBytes)) }
        val batchId = "download-batch-${System.nanoTime()}"
        // The whole batch is one transfer: one notification with its total, and one result when it ends
        val batchJob = scope.launch(start = CoroutineStart.LAZY) {
            val queue = coroutineContext[Job]
            var savedFiles = 0
            try {
                for (file in files) {
                    ensureActive()
                    // Its own job, not a child of the batch's: cancelling the batch has to stop it too
                    val download = download(file, queue, batchId)
                    try {
                        download.join()
                    } catch (e: CancellationException) {
                        download.cancel()
                        throw e
                    }
                    if (_downloads.value.active[file.key]?.status == DownloadStatus.COMPLETED) savedFiles++
                    // Counted once the file is over, whether it was saved or failed
                    _downloads.update { state ->
                        val batch = state.batch ?: return@update state
                        val finishedBytes = state.active[file.key]?.downloadedBytes ?: file.size ?: 0L
                        state.copy(batch = batch.copy(doneFiles = batch.doneFiles + 1, doneBytes = batch.doneBytes + finishedBytes))
                    }
                }
            } finally {
                _downloads.update { it.copy(batch = null) }
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
    fun downloadAsZip(files: List<StorageNode>, archiveName: String) {
        if (files.isEmpty()) return
        if (files.size == 1) {
            download(files.first())
            return
        }
        if (files.any { it.ref.id == null || it.isDirectory } || ProviderCapability.ARCHIVE_DOWNLOAD !in provider().capabilities) {
            downloadSequentially(files)
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
        downloadSequentially(archives)
    }
}
