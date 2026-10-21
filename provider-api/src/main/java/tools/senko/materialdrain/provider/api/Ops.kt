package tools.senko.materialdrain.provider.api

import tools.senko.materialdrain.provider.api.ErrorCodes
import android.content.Context
import android.net.Uri
import java.io.OutputStream

/**
 * Flat file store: upload a blob, get a [StorageRef] back, no directories. Pixeldrain's /file API and a
 * config-driven [ProviderCapability.UPLOAD] host both speak this; it's the one every provider is expected
 * to implement if it supports files at all.
 */
interface FileStoreOps {
    suspend fun upload(
        fileName: String,
        fileUri: Uri,
        context: Context,
        onProgress: (sent: Long, total: Long?) -> Unit
    ): ApiResponse<StorageNode>

    suspend fun download(
        ref: StorageRef,
        outputStream: OutputStream,
        onProgress: (read: Long, total: Long?) -> Unit
    ): ApiResponse<Long>

    suspend fun fileInfo(ref: StorageRef): ApiResponse<StorageNode>

    suspend fun delete(ref: StorageRef): ApiResponse<Unit>

    /**
     * Several files as one zip archive, written to [outputStream]. Only hosts with [ProviderCapability.ARCHIVE_DOWNLOAD]
     * implement it; the others report that it isn't supported.
     */
    suspend fun downloadArchive(
        refs: List<StorageRef>,
        outputStream: OutputStream,
        onProgress: (read: Long, total: Long?) -> Unit
    ): ApiResponse<Long> = ApiResponse.Error(ProviderError(ErrorCodes.NOT_SUPPORTED, "This host can't download several files as one archive."))
}

/**
 * Looking inside archives (zip, 7z, rar, tar...) that are on the host. [archive] is the archive itself; [inside] is a
 * folder within it, "" for its top. Only present with [ProviderCapability.ARCHIVE_BROWSE].
 *
 * A host can often only reach an archive one way: Pixeldrain's ?zip_info is on its filesystem (by path), and there is
 * none for the files of the account (by id). [canOpen] says whether this archive can be reached, so an archive the
 * host can't look inside isn't offered at all, instead of failing.
 */
interface ArchiveOps {
    fun canOpen(archive: StorageRef): Boolean

    suspend fun list(archive: StorageRef, inside: String): ApiResponse<List<StorageNode>>

    /** Writes one file of the archive, [entryPath] is its path within the archive (no leading slash). */
    suspend fun read(
        archive: StorageRef,
        entryPath: String,
        outputStream: OutputStream,
        onProgress: (read: Long, total: Long?) -> Unit
    ): ApiResponse<Long>
}

/** Hierarchical browsing: Pixeldrain's filesystem API, WebDAV. Only present when [ProviderCapability.BROWSE] is. */
interface BrowseOps {
    suspend fun list(path: String): ApiResponse<StorageListing>

    suspend fun upload(
        path: String,
        fileUri: Uri,
        context: Context,
        makeParents: Boolean,
        onProgress: (sent: Long, total: Long?) -> Unit
    ): ApiResponse<StorageNode>

    suspend fun download(
        path: String,
        outputStream: OutputStream,
        onProgress: (read: Long, total: Long?) -> Unit
    ): ApiResponse<Long>

    /**
     * The folder at [path] and everything in it as one zip archive, made by the host, written to [outputStream]. Only
     * hosts with [ProviderCapability.FOLDER_ARCHIVE_DOWNLOAD] implement it; the others report that it isn't supported.
     */
    suspend fun downloadFolderArchive(
        path: String,
        outputStream: OutputStream,
        onProgress: (read: Long, total: Long?) -> Unit
    ): ApiResponse<Long> = ApiResponse.Error(ProviderError(ErrorCodes.NOT_SUPPORTED, "This host can't download a folder as one archive."))

    suspend fun createDirectory(path: String, makeParents: Boolean): ApiResponse<Unit>

    suspend fun rename(path: String, targetPath: String, makeParents: Boolean): ApiResponse<StorageNode>

    suspend fun delete(path: String, recursive: Boolean): ApiResponse<Unit>

    /** Copies the files with [fileIds] (from the account's own file list) into the folder [path]. */
    suspend fun importFiles(path: String, fileIds: List<String>): ApiResponse<Unit> =
        ApiResponse.Error(ProviderError(ErrorCodes.NOT_SUPPORTED, "This host can't import files into a folder."))
}

/** Account/quota info. Present when [ProviderCapability.USER_QUOTA] is. */
interface AccountOps {
    suspend fun accountInfo(): ApiResponse<AccountInfo>
}

/** A named collection of files on a host (Pixeldrain's lists). */
data class FileList(val id: String, val title: String, val fileCount: Int, val canEdit: Boolean)

data class FileListDetail(val id: String, val title: String, val canEdit: Boolean, val files: List<StorageNode>)

/** Lists of files: the overview, the contents of one list, and changing them. Present when [ProviderCapability.LISTS] is. */
interface ListOps {
    suspend fun lists(): ApiResponse<List<FileList>>

    suspend fun listContents(id: String): ApiResponse<FileListDetail>

    /** Creates a list with [fileIds] and returns the id of the new list. */
    suspend fun create(title: String, fileIds: List<String>): ApiResponse<String>

    /** Replaces the title and the files of list [id]; the id stays the same. */
    suspend fun update(id: String, title: String, fileIds: List<String>): ApiResponse<Unit>

    /** Removes the list itself; the files in it are kept. */
    suspend fun delete(id: String): ApiResponse<Unit>
}

/**
 * A flat "give me everything on the account" call, no path, no folders. Only present when
 * [ProviderCapability.ENUMERATE] is - a host can support [FileStoreOps] (upload/download/delete by id)
 * without any way to ask it what's already there, and this being null is how that's told apart from a
 * host that browses hierarchically ([BrowseOps], which lists per-folder instead).
 */
interface FileListOps {
    suspend fun list(): ApiResponse<StorageListing>
}
