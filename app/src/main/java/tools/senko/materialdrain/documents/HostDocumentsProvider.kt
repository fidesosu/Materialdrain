package tools.senko.materialdrain.documents

import android.app.Application
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.os.CancellationSignal
import android.util.Log
import android.os.Handler
import android.os.HandlerThread
import android.os.ProxyFileDescriptorCallback
import android.os.storage.StorageManager
import android.system.ErrnoException
import android.system.OsConstants
import kotlin.concurrent.thread
import tools.senko.materialdrain.provider.api.ErrorCodes
import tools.senko.materialdrain.provider.api.ProviderError
import tools.senko.materialdrain.ui.media.HostRequestAuth
import tools.senko.materialdrain.ui.media.RangedReader
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import android.provider.DocumentsContract.Root
import android.provider.DocumentsProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import tools.senko.materialdrain.AppContainer
import tools.senko.materialdrain.R
import tools.senko.materialdrain.files.downloadedFileMimeType
import tools.senko.materialdrain.files.listDate
import tools.senko.materialdrain.provider.PIXELDRAIN_PROVIDER_ID
import tools.senko.materialdrain.provider.api.ApiResponse
import tools.senko.materialdrain.provider.api.ProviderCapability
import tools.senko.materialdrain.provider.api.StorageNode
import tools.senko.materialdrain.provider.api.StorageProvider
import tools.senko.materialdrain.settings.isSearchIndex
import tools.senko.materialdrain.util.parseDateTime
import java.io.FileNotFoundException
import java.util.concurrent.ConcurrentHashMap

private const val TAG = "HostDocuments"

/** How much of a file another app reads one request fetches, and how many of those are kept: 8 MB. */
private const val DOCUMENT_BLOCK = 512 * 1024
private const val DOCUMENT_BLOCKS_KEPT = 16

private val DEFAULT_ROOT_PROJECTION = arrayOf(
    Root.COLUMN_ROOT_ID, Root.COLUMN_DOCUMENT_ID, Root.COLUMN_TITLE, Root.COLUMN_SUMMARY, Root.COLUMN_FLAGS, Root.COLUMN_ICON
)

private val DEFAULT_DOCUMENT_PROJECTION = arrayOf(
    Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME, Document.COLUMN_MIME_TYPE, Document.COLUMN_SIZE,
    Document.COLUMN_LAST_MODIFIED, Document.COLUMN_FLAGS
)

/**
 * The hosts with folders, in the system's file picker (and the Files app): other apps can open their files as they open
 * the device's own (Files and transfers, Show hosts in the file picker). Only to read: a file is fetched as the other app
 * reads it, never all of it first (see [openDocument]).
 *
 * Off by default, and nothing is shown while the fingerprint lock is on, as the picker doesn't ask for it. A document's id
 * is its host's id and its path, "host|path".
 */
class HostDocumentsProvider : DocumentsProvider() {

    private val container by lazy { AppContainer.get(requireNotNull(context).applicationContext as Application) }

    // What the folders listed last held, so a document's details don't need its folder listed again
    private val known = ConcurrentHashMap<String, StorageNode>()

    override fun onCreate(): Boolean = true

    /** The hosts shown: those which have folders, while the setting is on and the app isn't locked with a fingerprint. */
    private fun shownHosts(): List<Pair<String, StorageProvider>> {
        val settings = container.appSettings
        if (!settings.documentsProvider.value || settings.biometricLock.value) return emptyList()
        val ids = (listOf(PIXELDRAIN_PROVIDER_ID) + container.providerConfigStore.providers.value.map { it.id }).distinct()
        return ids.map { it to container.providerRegistry.resolve(it) }
            .filter { (_, provider) -> ProviderCapability.BROWSE in provider.capabilities && provider.browse != null }
    }

    private fun hostOf(documentId: String): Pair<StorageProvider, String> {
        val hostId = documentId.substringBefore('|')
        val path = documentId.substringAfter('|', "")
        val provider = shownHosts().firstOrNull { it.first == hostId }?.second
            ?: throw FileNotFoundException("No host for $documentId")
        return provider to path
    }

    override fun queryRoots(projection: Array<out String>?): Cursor {
        val cursor = MatrixCursor(projection ?: DEFAULT_ROOT_PROJECTION)
        shownHosts().forEach { (hostId, provider) ->
            cursor.newRow().apply {
                add(Root.COLUMN_ROOT_ID, hostId)
                add(Root.COLUMN_DOCUMENT_ID, "$hostId|${provider.rootPath}")
                add(Root.COLUMN_TITLE, provider.displayName)
                add(Root.COLUMN_SUMMARY, "Materialdrain")
                add(Root.COLUMN_FLAGS, Root.FLAG_SUPPORTS_IS_CHILD)
                add(Root.COLUMN_ICON, R.mipmap.ic_launcher)
            }
        }
        return cursor
    }

    override fun queryDocument(documentId: String, projection: Array<out String>?): Cursor {
        val (provider, path) = hostOf(documentId)
        val cursor = MatrixCursor(projection ?: DEFAULT_DOCUMENT_PROJECTION)
        if (path == provider.rootPath) {
            cursor.newRow().apply {
                add(Document.COLUMN_DOCUMENT_ID, documentId)
                add(Document.COLUMN_DISPLAY_NAME, provider.rootName.takeIf { it != "/" } ?: provider.displayName)
                add(Document.COLUMN_MIME_TYPE, Document.MIME_TYPE_DIR)
                add(Document.COLUMN_FLAGS, 0)
            }
            return cursor
        }
        // Not known yet: from a listing of its folder
        val node = known[documentId] ?: run {
            listChildren(documentId.substringBefore('|'), provider, path.substringBeforeLast('/', ""))
            known[documentId]
        } ?: throw FileNotFoundException("Not found: $documentId")
        addRow(cursor, documentId, node)
        return cursor
    }

    override fun queryChildDocuments(parentDocumentId: String, projection: Array<out String>?, sortOrder: String?): Cursor {
        val (provider, path) = hostOf(parentDocumentId)
        val hostId = parentDocumentId.substringBefore('|')
        val cursor = MatrixCursor(projection ?: DEFAULT_DOCUMENT_PROJECTION)
        listChildren(hostId, provider, path).forEach { node -> addRow(cursor, "$hostId|${node.ref.path}", node) }
        return cursor
    }

    /** The folder [path] of [provider], also kept in [known]. The search index stays out, as in the app when it's hidden. */
    private fun listChildren(hostId: String, provider: StorageProvider, path: String): List<StorageNode> {
        val browse = provider.browse ?: throw FileNotFoundException(context?.getString(R.string.documents_no_folders))
        val hideIndex = container.appSettings.hideSearchIndex.value
        return when (val listing = runBlocking(Dispatchers.IO) { browse.list(path) }) {
            is ApiResponse.Success -> listing.data.children
                .filter { !(hideIndex && it.isSearchIndex()) }
                .onEach { known["$hostId|${it.ref.path}"] = it }
            is ApiResponse.Error -> throw FileNotFoundException(listing.error.message)
        }
    }

    private fun addRow(cursor: MatrixCursor, documentId: String, node: StorageNode) {
        cursor.newRow().apply {
            add(Document.COLUMN_DOCUMENT_ID, documentId)
            add(Document.COLUMN_DISPLAY_NAME, node.name)
            add(
                Document.COLUMN_MIME_TYPE,
                if (node.isDirectory) Document.MIME_TYPE_DIR
                else node.mimeType ?: downloadedFileMimeType(node.name).takeIf { it != "*/*" } ?: "application/octet-stream"
            )
            add(Document.COLUMN_SIZE, node.size)
            add(Document.COLUMN_LAST_MODIFIED, parseDateTime(node.listDate)?.toEpochMilli())
            add(Document.COLUMN_FLAGS, 0)
        }
    }

    override fun isChildDocument(parentDocumentId: String, documentId: String): Boolean {
        if (parentDocumentId.substringBefore('|') != documentId.substringBefore('|')) return false
        val parent = parentDocumentId.substringAfter('|', "").trimEnd('/')
        val child = documentId.substringAfter('|', "")
        return parent.isEmpty() || child.startsWith("$parent/")
    }

    /**
     * A file for another app to read, without fetching all of it first. From a host which sends parts of a file, each part
     * is fetched as the app reads there (a video player can jump about); from one which doesn't, the file comes as it's
     * downloaded, from the start on, through a pipe.
     */
    override fun openDocument(documentId: String, mode: String, signal: CancellationSignal?): ParcelFileDescriptor {
        if (mode != "r") throw UnsupportedOperationException("The hosts can only be read from here")
        val (provider, path) = hostOf(documentId)
        val browse = provider.browse ?: throw FileNotFoundException(context?.getString(R.string.documents_no_folders))
        signal?.throwIfCanceled()
        rangedReader(documentId, provider, path)?.let { reader ->
            val thread = HandlerThread("document-$documentId").apply { start() }
            val storage = requireNotNull(context).getSystemService(StorageManager::class.java)
            return storage.openProxyFileDescriptor(ParcelFileDescriptor.MODE_READ_ONLY, RangedCallback(reader, thread), Handler(thread.looper))
        }
        // Read in order, as it comes: the other app's end of the pipe gets an error when the download fails
        val (read, write) = ParcelFileDescriptor.createReliablePipe()
        thread(name = "document-$documentId") {
            val result = try {
                ParcelFileDescriptor.AutoCloseOutputStream(write).use { out ->
                    runBlocking(Dispatchers.IO) { browse.download(path, out) { _, _ -> signal?.throwIfCanceled() } }
                }
            } catch (e: Exception) {
                ApiResponse.Error(ProviderError(ErrorCodes.NETWORK_ERROR, e.message ?: context?.getString(R.string.documents_download_stopped).orEmpty()))
            }
            if (result is ApiResponse.Error) runCatching { write.closeWithError(result.error.message) }
        }
        return read
    }

    /**
     * Reads the file of [path] in parts, when the host gives it an address of its own and answers a range there; null
     * when it doesn't, or the address isn't one the app's client can fetch.
     */
    private fun rangedReader(documentId: String, provider: StorageProvider, path: String): RangedReader? {
        val node = known[documentId] ?: run {
            listChildren(documentId.substringBefore('|'), provider, path.substringBeforeLast('/', ""))
            known[documentId]
        } ?: return null
        val url = runCatching { provider.rawContentUrl(node) }.getOrNull()?.takeIf { it.startsWith("https://") || it.startsWith("http://") }
        val client = HostRequestAuth.client
        if (url == null || client == null) {
            Log.d(TAG, "read as it comes: ${if (url == null) "no address of its own" else "no client yet"}")
            return null
        }
        val reader = RangedReader(client, url, provider.requestHeaders(url), DOCUMENT_BLOCK, DOCUMENT_BLOCKS_KEPT)
        // The first part says whether the host sends parts at all
        return try {
            reader.size()
            reader
        } catch (e: Exception) {
            Log.d(TAG, "read as it comes from ${url.substringBefore("?")}: ${e.message}")
            null
        }
    }

    /** What another app reads of a file opened in parts: each read fetched by [reader], on [thread], which ends with it. */
    private class RangedCallback(private val reader: RangedReader, private val thread: HandlerThread) : ProxyFileDescriptorCallback() {
        override fun onGetSize(): Long = reader.size()

        override fun onRead(offset: Long, size: Int, data: ByteArray): Int = try {
            reader.readAt(offset, data, 0, size).coerceAtLeast(0)
        } catch (e: Exception) {
            throw ErrnoException("read", OsConstants.EIO)
        }

        override fun onRelease() {
            reader.close()
            thread.quitSafely()
        }
    }

    companion object {
        private fun authority(context: Context) = "${context.packageName}.documents"

        /** Tells the file picker the hosts it shows may have changed: the setting, or the lock, was turned on or off. */
        fun notifyRootsChanged(context: Context) {
            context.contentResolver.notifyChange(DocumentsContract.buildRootsUri(authority(context)), null)
        }
    }
}
