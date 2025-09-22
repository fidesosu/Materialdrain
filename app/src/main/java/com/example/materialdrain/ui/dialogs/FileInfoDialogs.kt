package com.example.materialdrain.ui.dialogs

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset // For SnackbarHost in FileInfoDetailsCard, though it's not strictly necessary with defaults
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ImageNotSupported
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.materialdrain.network.FileInfoResponse
import com.example.materialdrain.ui.formatApiDateTimeString // Helper from ui package
import com.example.materialdrain.ui.formatSize // Helper from ui package
import com.example.materialdrain.viewmodel.FileInfoViewModel
import kotlinx.coroutines.launch

@Composable
fun EnterFileIdDialog(fileInfoViewModel: FileInfoViewModel) {
    val uiState by fileInfoViewModel.uiState.collectAsState()

    AlertDialog(
        onDismissRequest = { fileInfoViewModel.dismissEnterFileIdDialog() },
        title = { Text("Search File by ID") },
        text = {
            Column {
                OutlinedTextField(
                    value = uiState.fileIdInput,
                    onValueChange = { fileInfoViewModel.onFileIdInputChange(it) },
                    label = { Text("Enter File ID") },
                    singleLine = true,
                    isError = uiState.fileInfoErrorMessage?.let { it.contains("Please enter", true) || it.contains("not found", true) || it.contains("error fetching", true) } == true,
                    keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = {
                        if (uiState.fileIdInput.isNotBlank()) {
                            fileInfoViewModel.fetchFileInfoFromDialogInput()
                        }
                    })
                )
                uiState.fileInfoErrorMessage?.let {
                    if (it != "Please enter or select a File ID.") {
                         Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (uiState.fileIdInput.isNotBlank()) {
                        fileInfoViewModel.fetchFileInfoFromDialogInput()
                    }
                },
                enabled = uiState.fileIdInput.isNotBlank() && !uiState.isLoadingFileInfo
            ) {
                if (uiState.isLoadingFileInfo) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp))
                } else {
                    Text("Search")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = { fileInfoViewModel.dismissEnterFileIdDialog() }) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun FileInfoDetailsCard(fileInfo: FileInfoResponse, fileInfoViewModel: FileInfoViewModel, context: Context) {
    val localClipboardManager = LocalContext.current.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    val fileUrl = "https://pixeldrain.com/u/${fileInfo.id}"

    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, top = 0.dp, end = 4.dp, bottom = 0.dp), 
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween 
            ) {
                Text(
                    text = fileInfo.name,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier
                        .weight(1f, fill = false) 
                        .padding(start = 16.dp, top = 0.dp, end = 4.dp, bottom = 0.dp), 
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                IconButton(
                    onClick = { fileInfoViewModel.clearFileInfoDisplay() },
                    modifier = Modifier.padding(vertical = 4.dp) 
                ) {
                    Icon(Icons.Filled.Close, contentDescription = "Close")
                }
            }

            Column(
                modifier = Modifier
                    .padding(horizontal = 16.dp) 
                    .padding(bottom = 16.dp) 
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f) 
                        .verticalScroll(rememberScrollState())
                ) {
                    AsyncImage(
                        model = "https://pixeldrain.com/api/file/${fileInfo.id}/thumbnail?width=256&height=256",
                        contentDescription = "File thumbnail",
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 150.dp, max = 250.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .align(Alignment.CenterHorizontally)
                            .padding(top = 8.dp, bottom = 8.dp), 
                        contentScale = ContentScale.Fit,
                        error = rememberVectorPainter(Icons.Filled.ImageNotSupported)
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                    Text("File Details", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 8.dp))
                    InfoRow("ID:", fileInfo.id, true)
                    InfoRow("Size:", formatSize(fileInfo.size))
                    fileInfo.mimeType?.let { InfoRow("Type:", it) }
                    InfoRow("Upload Date:", formatApiDateTimeString(fileInfo.dateUpload))
                    fileInfo.dateLastView?.let { InfoRow("Last View:", formatApiDateTimeString(it)) }
                    fileInfo.views?.let { InfoRow("Views:", it.toString()) }
                    fileInfo.downloads?.let { InfoRow("Downloads:", it.toString()) }
                    fileInfo.hashSha256?.let { InfoRow("SHA256:", it, true) }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                    Text("Actions", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 12.dp))

                    Button( 
                        onClick = {
                            val sendIntent: Intent = Intent().apply {
                                action = Intent.ACTION_SEND
                                putExtra(Intent.EXTRA_TEXT, fileUrl)
                                type = "text/plain"
                            }
                            val shareIntent = Intent.createChooser(sendIntent, null)
                            context.startActivity(shareIntent)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.Share, contentDescription = "Share Link")
                        Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                        Text("Share", maxLines = 1)
                    }

                    Spacer(Modifier.height(8.dp))

                    OutlinedButton( 
                        onClick = {
                            val clip = ClipData.newPlainText("Pixeldrain URL", fileUrl)
                            localClipboardManager.setPrimaryClip(clip)
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar("Link copied to clipboard!")
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.ContentCopy, contentDescription = "Copy Link")
                        Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                        Text("Copy Link", maxLines = 1)
                    }

                    Spacer(Modifier.height(8.dp))

                    Button( 
                        onClick = {
                            fileInfoViewModel.initiateDownloadFile(fileInfo)
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar("Download started for ${fileInfo.name}")
                            }
                            fileInfoViewModel.clearFileInfoDisplay()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.Download, contentDescription = "Download")
                        Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                        Text("Download", maxLines = 1)
                    }

                    if (fileInfo.canEdit == true) { 
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = { fileInfoViewModel.initiateDeleteFile(fileInfo.id) },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete")
                            Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                            Text("Delete", maxLines = 1)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }

                Box(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(top = 4.dp)
                ) {
                    SnackbarHost(hostState = snackbarHostState) { data ->
                        Snackbar(
                            snackbarData = data,
                            containerColor = MaterialTheme.colorScheme.inverseSurface,
                            contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                        )
                    }
                }
            }
        }
    }
}


@Composable
fun InfoRow(label: String, value: String, isValueSelectable: Boolean = false) {
    Row(
        modifier = Modifier.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically 
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier
                .defaultMinSize(minWidth = 90.dp) 
                .padding(end = 8.dp)
        )
        if (isValueSelectable) {
            SelectionContainer {
                Text(
                    value,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f) 
                )
            }
        } else {
            Text(
                value,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f) 
            )
        }
    }
}
