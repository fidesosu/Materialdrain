package com.example.materialdrain.ui.screens

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone // Added
import androidx.compose.material.icons.filled.ErrorOutline // Added
import androidx.compose.material.icons.filled.Image // Added
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.materialdrain.network.FileInfoResponse
import com.example.materialdrain.ui.formatApiDateTimeString // Keep this if used for date display
import com.example.materialdrain.ui.formatSize
import com.example.materialdrain.viewmodel.FileDownloadState // Added
import com.example.materialdrain.viewmodel.DownloadStatus
import com.example.materialdrain.viewmodel.FileInfoUiState
import com.example.materialdrain.viewmodel.FileInfoViewModel
import com.example.materialdrain.viewmodel.SortableField
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SortControls(uiState: FileInfoUiState, fileInfoViewModel: FileInfoViewModel) {
    data class SortOption(val displayText: String, val field: SortableField, val ascending: Boolean)
    val sortOptions = listOf(
        SortOption("Name (A-Z)", SortableField.NAME, true),
        SortOption("Name (Z-A)", SortableField.NAME, false),
        SortOption("Size (Smallest)", SortableField.SIZE, true),
        SortOption("Size (Largest)", SortableField.SIZE, false),
        SortOption("Date (Oldest)", SortableField.UPLOAD_DATE, true),
        SortOption("Date (Newest)", SortableField.UPLOAD_DATE, false)
    )

    var expanded by remember { mutableStateOf(false) }
    val currentSortOptionText = remember(uiState.sortField, uiState.sortAscending) {
        sortOptions.find { it.field == uiState.sortField && it.ascending == uiState.sortAscending }?.displayText
            ?: (if (uiState.sortField == SortableField.UPLOAD_DATE && !uiState.sortAscending) "Date (Newest)" else "Sort by...")
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded },
            modifier = Modifier.weight(1f)
        ) {
            OutlinedTextField(
                value = currentSortOptionText,
                onValueChange = { },
                readOnly = true,
                label = { Text("Sort by") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier
                    .menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true)
                    .fillMaxWidth()
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                sortOptions.forEach { selectionOption ->
                    DropdownMenuItem(
                        text = { Text(selectionOption.displayText) },
                        onClick = {
                            fileInfoViewModel.changeSortOrder(newField = selectionOption.field, newAscending = selectionOption.ascending)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun FilesScreenContent(
    fileInfoViewModel: FileInfoViewModel,
    onFileSelected: () -> Unit
) {
    val uiState by fileInfoViewModel.uiState.collectAsState()
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val filterFocusRequester = remember { FocusRequester() }
    val listState = rememberLazyListState()

    val displayedFiles by remember(uiState.userFilesList, uiState.sortField, uiState.sortAscending, uiState.filterQuery) {
        derivedStateOf {
            val filteredList = if (uiState.filterQuery.isBlank()) {
                uiState.userFilesList
            } else {
                uiState.userFilesList.filter {
                    it.name.contains(uiState.filterQuery, ignoreCase = true)
                }
            }

            val comparator = when (uiState.sortField) {
                SortableField.NAME -> compareBy<FileInfoResponse> { it.name.lowercase(Locale.getDefault()) }
                SortableField.SIZE -> compareBy { it.size }
                SortableField.UPLOAD_DATE -> compareBy { it.dateUpload }
            }

            if (uiState.sortAscending) {
                filteredList.sortedWith(comparator)
            } else {
                filteredList.sortedWith(comparator.reversed())
            }
        }
    }

    LaunchedEffect(uiState.showFilterInput) {
        if (uiState.showFilterInput) {
            filterFocusRequester.requestFocus()
            keyboardController?.show()
        }
    }

    LaunchedEffect(uiState.sortField, uiState.sortAscending) {
        if (displayedFiles.isNotEmpty()) {
            listState.animateScrollToItem(0)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AnimatedVisibility(
            visible = uiState.showFilterInput,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            OutlinedTextField(
                value = uiState.filterQuery,
                onValueChange = { fileInfoViewModel.onFilterQueryChanged(it) },
                label = { Text("Filter by name") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
                    .focusRequester(filterFocusRequester),
                singleLine = true,
                keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {
                    keyboardController?.hide()
                }),
                trailingIcon = {
                    if (uiState.filterQuery.isNotEmpty()) {
                        IconButton(onClick = {
                            fileInfoViewModel.onFilterQueryChanged("")
                            keyboardController?.hide()
                        }) {
                            Icon(Icons.Filled.Clear, contentDescription = "Clear filter")
                        }
                    }
                }
            )
        }

        if (uiState.isLoadingFileInfo && uiState.fileInfo == null && displayedFiles.isEmpty() && !uiState.showFilterInput) {
            Spacer(modifier = Modifier.height(16.dp))
            CircularProgressIndicator()
            Text("Fetching file info...", modifier = Modifier.padding(top = 8.dp))
        } else if (uiState.fileInfoErrorMessage != null &&
            uiState.fileInfoErrorMessage != "Please enter or select a File ID." &&
            !uiState.showEnterFileIdDialog &&
            !uiState.isLoadingUserFiles && !uiState.showFilterInput) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                uiState.fileInfoErrorMessage!!,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (uiState.apiKeyMissingError) {
            Text(
                "API Key is missing. Please set it in Settings to load and manage your files.",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .padding(vertical = 8.dp)
                    .fillMaxWidth()
            )
        } else {
            Button(
                onClick = { fileInfoViewModel.fetchUserFiles() },
                enabled = !uiState.isLoadingUserFiles,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (uiState.isLoadingUserFiles && displayedFiles.isEmpty() && uiState.filterQuery.isBlank()) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                    Text("Loading Files...")
                } else {
                    Icon(Icons.Filled.Refresh, contentDescription = "Refresh Files")
                    Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                    Text(if (uiState.isLoadingUserFiles) "Refreshing..." else "Refresh My Files")
                }
            }
        }

        uiState.userFilesListErrorMessage?.let {
            if (!uiState.apiKeyMissingError) {
                Text(
                    it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .padding(vertical = 8.dp)
                        .fillMaxWidth()
                )
            }
        }

        if (displayedFiles.isNotEmpty()) {
            SortControls(uiState = uiState, fileInfoViewModel = fileInfoViewModel)
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f), // Changed from fillMaxWidth().weight(1f)
                contentPadding = PaddingValues(vertical = 8.dp) // Changed from bottom = 16.dp
            ) {
                items(
                    items = displayedFiles,
                    key = { file -> file.id },
                    contentType = { "fileInfoCard" } // Keep contentType if useful for performance tools
                ) { file ->
                    val currentDownloadState = uiState.activeDownloads[file.id]
                    UserFileListItemCard(
                        fileInfo = file,
                        downloadState = currentDownloadState,
                        context = context, // Context is stable
                        fileInfoViewModel = fileInfoViewModel,
                        onFileSelected = { // This is the onClick for the Card
                            if (uiState.showFilterInput) { // Check uiState from FilesScreenContent
                                fileInfoViewModel.setFilterInputVisible(false)
                            }
                            fileInfoViewModel.fetchFileInfo(file.id) // Corrected: Use fetchFileInfo with file.id
                            onFileSelected() // Propagate the navigation callback
                        }
                    )
                    HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                }
            }
        } else if (!uiState.isLoadingUserFiles && !uiState.apiKeyMissingError && uiState.userFilesListErrorMessage == null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                if (uiState.filterQuery.isNotBlank()) "No files match your filter: \"${uiState.filterQuery}\"."
                else "No files found. Try refreshing.",
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top=8.dp)
            )
        }
    }
}

@Composable
fun UserFileListItemCard(
    fileInfo: FileInfoResponse,
    downloadState: FileDownloadState?, // Changed from uiState: FileInfoUiState
    context: Context, // Context is usually stable and fine to pass
    fileInfoViewModel: FileInfoViewModel, // For triggering actions
    onFileSelected: () -> Unit // Callback for item click
) {
    val thumbnailUrl = "https://pixeldrain.com/api/file/${fileInfo.id}/thumbnail"

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable { // onClick lambda is now part of the Card itself
                fileInfoViewModel.fetchFileInfo(fileInfo.id) // Corrected: Use fetchFileInfo with fileInfo.id
                onFileSelected()
            },
        shape = RoundedCornerShape(8.dp)
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AsyncImage(
                    model = thumbnailUrl,
                    contentDescription = "${fileInfo.name} thumbnail",
                    modifier = Modifier
                        .size(60.dp)
                        .clip(RoundedCornerShape(6.dp)),
                    contentScale = ContentScale.Crop,
                    error = rememberVectorPainter(Icons.Filled.BrokenImage),
                    placeholder = rememberVectorPainter(Icons.Filled.Image) // Added placeholder
                )

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = fileInfo.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Size: ${formatSize(fileInfo.size)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    // Optional: Display upload date
                     Text(
                         text = "Uploaded: ${formatApiDateTimeString(fileInfo.dateUpload)}",
                         style = MaterialTheme.typography.bodySmall,
                         color = MaterialTheme.colorScheme.onSurfaceVariant
                     )
                }

                Spacer(modifier = Modifier.width(8.dp))

                if (downloadState != null && downloadState.status != DownloadStatus.COMPLETED && downloadState.status != DownloadStatus.PENDING) {
                     Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.size(40.dp)) {
                        if (downloadState.status == DownloadStatus.DOWNLOADING) {
                            CircularProgressIndicator(
                                progress = { downloadState.progressFraction },
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp
                            )
                            Text(
                                "${(downloadState.progressFraction * 100).toInt()}%",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        } else if (downloadState.status == DownloadStatus.FAILED) {
                            Icon(
                                Icons.Filled.ErrorOutline,
                                contentDescription = "Download failed",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                } else {
                     IconButton(
                        onClick = { fileInfoViewModel.initiateDownloadFile(fileInfo) }, // Corrected: Use initiateDownloadFile with FileInfoResponse
                        enabled = downloadState?.status != DownloadStatus.DOWNLOADING,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            if (downloadState?.status == DownloadStatus.COMPLETED) Icons.Filled.DownloadDone else Icons.Filled.Download,
                            contentDescription = if (downloadState?.status == DownloadStatus.COMPLETED) "Downloaded" else "Download file",
                            tint = if (downloadState?.status == DownloadStatus.COMPLETED) MaterialTheme.colorScheme.primary else LocalContentColor.current,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                if (fileInfo.canEdit == true) { // Check canEdit, as in original
                    IconButton(
                        onClick = { fileInfoViewModel.initiateDeleteFile(fileInfo.id) },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            Icons.Filled.DeleteOutline,
                            contentDescription = "Delete file",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
            if (downloadState?.status == DownloadStatus.DOWNLOADING) {
                LinearProgressIndicator(
                    progress = { downloadState.progressFraction },
                    modifier = Modifier.fillMaxWidth().height(2.dp).padding(horizontal = 8.dp, vertical = 2.dp) // Added padding
                )
            }
             // Optional: Display full status message for failed/completed if desired
            if (downloadState != null && (downloadState.status == DownloadStatus.FAILED || downloadState.status == DownloadStatus.COMPLETED)) {
                 downloadState.message?.let {
                     Text(
                         text = it,
                         style = MaterialTheme.typography.labelSmall,
                         color = if (downloadState.status == DownloadStatus.FAILED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                         modifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 4.dp).fillMaxWidth(),
                         textAlign = TextAlign.Center
                     )
                 }
            }
        }
    }
}
