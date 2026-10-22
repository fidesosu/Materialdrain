package tools.senko.materialdrain

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import tools.senko.materialdrain.files.DownloadStyle
import tools.senko.materialdrain.files.FileInfoViewModel
import tools.senko.materialdrain.ui.components.ConfirmDialog
import tools.senko.materialdrain.util.formatSize
import tools.senko.materialdrain.settings.SEARCH_INDEX_DELETE_WARNING
import tools.senko.materialdrain.settings.isSearchIndex
import tools.senko.materialdrain.upload.UploadViewModel

/**
 * A problem the app shows in a dialog. Each kind decides its own title and text, and what closing it clears (see
 * [AppDialogs]), so neither depends on the wording of a message. [opensAccountSettings]: OK goes to the account,
 * where a missing API key is set.
 */
internal sealed class AppDialog(val title: String, val text: String, val opensAccountSettings: Boolean = false) {
    data class UploadFailed(val message: String) : AppDialog("Upload failed", "Error: $message")
    data class UploadError(val message: String) : AppDialog("Upload error", message)
    data object UploadApiKeyRequired : AppDialog(
        "Not signed in",
        "Log in or add an API key in Settings to upload.",
        opensAccountSettings = true
    )
    data class FilesApiKeyRequired(val onDetails: Boolean, val message: String) : AppDialog(
        "Not signed in",
        message,
        opensAccountSettings = true
    )
}

/**
 * The main screen's dialogs: the problem in [dialog], the confirmation of a file's deletion while one is asked for
 * ([confirmDelete]), and that of a big download of folders (see DownloadManager.downloadItems). Closing [dialog] clears what it reported ([onClosed] then lets it go); OK on a missing API key goes to
 * where it's set ([onOpenAccountSettings]).
 */
@Composable
internal fun AppDialogs(
    dialog: AppDialog?,
    onClosed: () -> Unit,
    onOpenAccountSettings: () -> Unit,
    confirmDelete: Boolean,
    uploadViewModel: UploadViewModel,
    fileInfoViewModel: FileInfoViewModel
) {
    dialog?.let { shown ->
        fun close(confirmed: Boolean) {
            onClosed()
            when (shown) {
                is AppDialog.UploadFailed, is AppDialog.UploadError -> uploadViewModel.clearUploadResult()
                AppDialog.UploadApiKeyRequired -> uploadViewModel.clearApiKeyError()
                is AppDialog.FilesApiKeyRequired -> {
                    fileInfoViewModel.clearUserFilesError()
                    fileInfoViewModel.clearApiKeyMissingError()
                }
            }
            if (confirmed && shown.opensAccountSettings) onOpenAccountSettings()
        }
        AlertDialog(
            onDismissRequest = { close(confirmed = false) },
            title = { Text(shown.title) },
            text = { Text(shown.text) },
            confirmButton = { Button(onClick = { close(confirmed = true) }) { Text("OK") } }
        )
    }

    // Read here, so its changes don't redraw the screen behind
    val downloadConfirmation by fileInfoViewModel.downloadConfirmation.collectAsState()
    downloadConfirmation?.let { download ->
        val size = download.totalBytes?.let { ", ${formatSize(it)}" }.orEmpty()
        ConfirmDialog(
            title = "Download ${download.fileCount} files?",
            text = "${download.fileCount} files$size. " + when (download.style) {
                DownloadStyle.SEPARATELY -> "Saved to Download in their folders."
                DownloadStyle.ZIP -> "Saved as one ZIP."
            },
            confirmLabel = "Download",
            onConfirm = { fileInfoViewModel.confirmDownload() },
            onDismiss = { fileInfoViewModel.dismissDownloadConfirmation() }
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { fileInfoViewModel.cancelDeleteFile() },
            title = { Text("Confirm Deletion") },
            text = {
                // Read here, so the details' other changes don't redraw the screen behind the dialog
                val fileInfoUiState by fileInfoViewModel.uiState.collectAsState()
                val isSearchIndex = fileInfoUiState.nodeToDelete?.isSearchIndex() == true
                Text(
                    if (isSearchIndex) SEARCH_INDEX_DELETE_WARNING
                    else "Delete \"${fileInfoUiState.nodeToDelete?.name ?: fileInfoUiState.fileIdToDelete}\"? This can't be undone."
                )
            },
            confirmButton = {
                Button(
                    onClick = { fileInfoViewModel.confirmDeleteFile() },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete") }
            },
            dismissButton = {
                Button(onClick = { fileInfoViewModel.cancelDeleteFile() }) { Text("Cancel") }
            }
        )
    }
}
