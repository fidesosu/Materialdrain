package tools.senko.materialdrain.files

import java.io.File
import tools.senko.materialdrain.UiMessages
import tools.senko.materialdrain.settings.DataSaver
import tools.senko.materialdrain.provider.api.StorageNode
import tools.senko.materialdrain.provider.api.StorageRef

/** The folder [inside] of the [archive], and its entries once they've loaded. */
data class ArchiveView(val archive: StorageRef, val inside: String, val entries: List<StorageNode>?, val error: String?)

/**
 * A PDF's preview: too big to load without being asked for, loading into the app's cache, ready to be drawn from [file],
 * or why it can't be shown.
 */
sealed interface PdfPreviewState {
    data class Large(val totalBytes: Long) : PdfPreviewState
    data class Loading(val loadedBytes: Long, val totalBytes: Long?) : PdfPreviewState
    data class Ready(val file: File) : PdfPreviewState
    data class Failed(val message: String) : PdfPreviewState
}

data class FileInfoUiState(
    // For single file info
    val isLoadingFileInfo: Boolean = false,
    // Shown from a list's entry after a swipe, while the host's full details of it are on their way (see completeFileInfo):
    // its counts (views, downloads) are the list's until then, which may be older
    val fileInfoCompleting: Boolean = false,
    val fileInfo: StorageNode? = null,
    val fileInfoErrorMessage: String? = null,

    // For text preview
    val isLoadingTextPreview: Boolean = false,
    val textPreviewContent: String? = null,
    // The whole text the preview was made from (up to the fetch limit), for the fullscreen view
    val textPreviewFullContent: String? = null,
    val textPreviewErrorMessage: String? = null,
    // The text is too big for the preview but can be loaded anyway, up to a bigger limit (see loadWholeText)
    val textPreviewCanLoadAnyway: Boolean = false,

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
