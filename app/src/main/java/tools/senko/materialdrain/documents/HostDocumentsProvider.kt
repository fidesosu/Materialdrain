package tools.senko.materialdrain.documents

import android.app.Application
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.os.CancellationSignal
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
import java.io.File
import java.io.FileNotFoundException
import java.util.concurrent.ConcurrentHashMap

private val DEFAULT_ROOT_PROJECTION = arrayOf(
    Root.COLUMN_ROOT_ID, Root.COLUMN_DOCUMENT_ID, Root.COLUMN_TITLE, Root.COLUMN_SUMMARY, Root.COLUMN_FLAGS, Root.COLUMN_ICON
)

private val DEFAULT_DOCUMENT_PROJECTION = arrayOf(
    Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME, Document.COLUMN_MIME_TYPE, Document.COLUMN_SIZE,
    Document.COLUMN_LAST_MODIFIED, Document.COLUMN_FLAGS
)

/**
 * The hosts with folders, in the system's file picker (and the Files app): other apps can open their files as they open
 * the device's own (Files and transfers, Show hosts in the file picker). Only to read: a file is fetched whole when it's
 * opened, into the app's cache, which it leaves as soon as the other app has it.
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
        val browse = provider.browse ?: throw FileNotFoundException("No folders on this host")
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

    override fun openDocument(documentId: String, mode: String, signal: CancellationSignal?): ParcelFileDescriptor {
        if (mode != "r") throw UnsupportedOperationException("The hosts can only be read from here")
        val (provider, path) = hostOf(documentId)
        val browse = provider.browse ?: throw FileNotFoundException("No folders on this host")
        val dir = File(requireNotNull(context).cacheDir, "documents").apply { mkdirs() }
        val file = File.createTempFile("open", null, dir)
        try {
            val result = runBlocking(Dispatchers.IO) {
                file.outputStream().use { out -> browse.download(path, out) { _, _ -> signal?.throwIfCanceled() } }
            }
            if (result is ApiResponse.Error) throw FileNotFoundException(result.error.message)
            // Opened, the file can go: the other app reads it through what's opened, and nothing is left behind
            return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        } finally {
            file.delete()
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
