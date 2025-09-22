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
import com.example.materialdrain.ui.formatSize // Import from com.example.materialdrain.ui
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
    onFileSelected: () -> Unit // Added callback for navigation
) {
    val uiState by fileInfoViewModel.uiState.collectAsState()
    val context = LocalContext.current // context is still needed for UserFileListItemCard
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
        } else {
            // Consider hiding keyboard if filter input becomes not visible
            // keyboardController?.hide() // This might be too aggressive, depends on desired UX
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

        // This section for loading/error messages for single file info (dialog based) might be less relevant here
        // if this composable is only for the list. Consider if this logic belongs to the main screen.
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
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(
                    items = displayedFiles,
                    key = { it.id },
                    contentType = { "fileInfoCard" }
                ) { file ->
                    Column(
                        modifier = Modifier.animateItem(fadeInSpec = null, fadeOutSpec = null, placementSpec = tween<IntOffset>(durationMillis = 700))
                    ) {
                        UserFileListItemCard(
                            fileInfo = file,
                            fileInfoViewModel = fileInfoViewModel,
                            context = context,
                            onClick = {
                                if (uiState.showFilterInput) {
                                    fileInfoViewModel.setFilterInputVisible(false)
                                }
                                fileInfoViewModel.fetchFileInfoById(file.id)
                                onFileSelected() // Call the navigation callback
                            }
                        )
                        HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))
                    }
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
    fileInfoViewModel: FileInfoViewModel,
    context: Context,
    onClick: () -> Unit
) {
    val uiState by fileInfoViewModel.uiState.collectAsState()
    val downloadState = uiState.activeDownloads[fileInfo.id]

    val isDownloadingThisItem = downloadState?.status == DownloadStatus.DOWNLOADING || downloadState?.status == DownloadStatus.PENDING
    val showProgressSection = downloadState != null &&
            downloadState.status != DownloadStatus.COMPLETED &&
            downloadState.status != DownloadStatus.FAILED

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(bottom = if (showProgressSection) 0.dp else 12.dp)) {
            Row(
                modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = if (showProgressSection) 4.dp else 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AsyncImage(
                    model = "https://pixeldrain.com/api/file/${fileInfo.id}/thumbnail",
                    contentDescription = "File thumbnail",
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    contentScale = ContentScale.Crop,
                    error = rememberVectorPainter(Icons.Filled.BrokenImage)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = fileInfo.name,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(formatSize(fileInfo.size), style = MaterialTheme.typography.bodySmall)
                    Text("Uploaded: ${fileInfo.dateUpload.substringBefore('T')}", style = MaterialTheme.typography.bodySmall)
                }
                Spacer(modifier = Modifier.width(4.dp))
                IconButton(
                    onClick = { fileInfoViewModel.initiateDownloadFile(fileInfo) },
                    enabled = !isDownloadingThisItem,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(Icons.Filled.Download, contentDescription = "Download File", modifier = Modifier.size(20.dp))
                }
                if (fileInfo.canEdit == true) {
                    IconButton(
                        onClick = { fileInfoViewModel.initiateDeleteFile(fileInfo.id) },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(Icons.Filled.DeleteOutline, contentDescription = "Delete File", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                    }
                }
            }

            if (downloadState != null && showProgressSection) {
                Column { // Removed Modifier.animateContentSize()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 12.dp, end = 12.dp, bottom = 2.dp, top = 0.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        val statusText = when (downloadState.status) {
                            DownloadStatus.PENDING -> "Download pending..."
                            DownloadStatus.DOWNLOADING -> "Downloading..."
                            DownloadStatus.COMPLETED -> downloadState.message ?: "Download completed!"
                            DownloadStatus.FAILED -> downloadState.message ?: "Download failed."
                        }
                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (downloadState.status == DownloadStatus.FAILED) MaterialTheme.colorScheme.error else LocalContentColor.current
                        )
                        if (downloadState.status == DownloadStatus.DOWNLOADING || downloadState.status == DownloadStatus.PENDING) {
                            downloadState.totalBytes?.let { total ->
                                Text(
                                    text = "${formatSize(downloadState.downloadedBytes)} / ${formatSize(total)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = FontFamily.Monospace,
                                    modifier = Modifier.width(120.dp),
                                    textAlign = TextAlign.End
                                )
                            }
                        }
                    }
                    if (downloadState.status == DownloadStatus.DOWNLOADING || downloadState.status == DownloadStatus.PENDING) {
                        LinearProgressIndicator(
                            progress = { downloadState.progressFraction },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 12.dp, end = 12.dp, bottom = 4.dp)
                        )
                    }
                }
            } else if (downloadState != null && downloadState.status == DownloadStatus.FAILED) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 12.dp, end = 12.dp, bottom = 2.dp, top = 0.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = downloadState.message ?: if (downloadState.status == DownloadStatus.COMPLETED) "Download completed!" else "Download failed.",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (downloadState.status == DownloadStatus.FAILED) MaterialTheme.colorScheme.error else LocalContentColor.current
                        )
                    }
                    TextButton(
                        onClick = { fileInfoViewModel.clearDownloadState(fileInfo.id) },
                        modifier = Modifier
                            .align(Alignment.End)
                            .padding(end = 8.dp, bottom = 0.dp, top = 0.dp)
                    ) {
                        Text("Dismiss", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}
