package com.example.materialdrain.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.platform.LocalContext // Added import
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest // Added import
import com.example.materialdrain.network.FileInfoResponse
import com.example.materialdrain.ui.formatApiDateTimeString
import com.example.materialdrain.ui.formatSize
import com.example.materialdrain.viewmodel.FileDownloadState
import com.example.materialdrain.viewmodel.DownloadStatus
import com.example.materialdrain.viewmodel.FileInfoUiState
import com.example.materialdrain.viewmodel.FileInfoViewModel
import com.example.materialdrain.viewmodel.SortableField

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilesScreenContent(
    fileInfoViewModel: FileInfoViewModel,
    onFileSelected: () -> Unit,
    listState: LazyListState, // Accept LazyListState as a parameter
    fabHeight: Dp,
    isFabVisible: Boolean
) {
    val uiState by fileInfoViewModel.uiState.collectAsState()
    val keyboardController = LocalSoftwareKeyboardController.current
    val filterFocusRequester = remember { FocusRequester() }
    val pullRefreshState = rememberPullToRefreshState()
    var isInitialUserFilesLoad by remember { mutableStateOf(true) }

    val displayedFiles by fileInfoViewModel.displayedFiles.collectAsState()

    LaunchedEffect(uiState.showFilterInput) {
        if (uiState.showFilterInput) {
            filterFocusRequester.requestFocus()
            keyboardController?.show()
        }
    }

    LaunchedEffect(uiState.shouldPreserveScrollPosition) {
        if (uiState.shouldPreserveScrollPosition) {
            // If preserving scroll, reset the flag and do nothing else with scroll.
            // This ensures subsequent sort/refresh actions will scroll to top unless this flag is set again.
            fileInfoViewModel.setPreserveScrollPosition(false)
        }
    }

    LaunchedEffect(uiState.sortField, uiState.sortAscending) {
        // Only scroll if not currently preserving scroll from a back navigation
        if (!uiState.shouldPreserveScrollPosition) {
            if (displayedFiles.isNotEmpty() && listState.firstVisibleItemIndex != 0) {
                listState.scrollToItem(0)
            }
        }
    }

    LaunchedEffect(uiState.userFilesList) {
        if (isInitialUserFilesLoad) {
            if (uiState.userFilesList.isNotEmpty()) { // Mark initial load as done only when data actually arrives
                isInitialUserFilesLoad = false
            }
        } else {
            // This is a refresh (not the initial load)
            if (!uiState.shouldPreserveScrollPosition) {
                if (displayedFiles.isNotEmpty() && listState.firstVisibleItemIndex != 0) {
                    listState.scrollToItem(0)
                }
            }
        }
    }

    PullToRefreshBox(
        isRefreshing = uiState.isLoadingUserFiles,
        onRefresh = { 
            fileInfoViewModel.fetchUserFiles()
        },
        state = pullRefreshState,
        modifier = Modifier.fillMaxSize(),
    ) {
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
                if (uiState.isLoadingUserFiles && displayedFiles.isEmpty() && uiState.filterQuery.isBlank()) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp)
                    ) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(8.dp))
                        Text("Loading Files...")
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
                    state = listState, // Use the passed-in listState
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(
                        top = 8.dp, 
                        bottom = if (isFabVisible) fabHeight + 16.dp else 8.dp
                    )
                ) {
                    items(
                        items = displayedFiles,
                        key = { file -> file.id },
                        contentType = { "fileInfoCard" }
                    ) { file ->
                        val currentDownloadState = uiState.activeDownloads[file.id]
                        UserFileListItemCard(
                            fileInfo = file,
                            downloadState = currentDownloadState,
                            fileInfoViewModel = fileInfoViewModel,
                            onFileSelected = {
                                if (uiState.showFilterInput) {
                                    fileInfoViewModel.setFilterInputVisible(false)
                                }
                                fileInfoViewModel.fetchFileInfo(file.id)
                                onFileSelected()
                            }
                        )
                        HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    }
                }
            } else if (!uiState.isLoadingUserFiles && !uiState.apiKeyMissingError && uiState.userFilesListErrorMessage == null && uiState.filterQuery.isBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "No files found. Try pulling down to refresh.",
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top=8.dp)
                )
            } else if (uiState.filterQuery.isNotBlank() && displayedFiles.isEmpty()) {
                 Spacer(modifier = Modifier.height(8.dp))
                 Text(
                    "No files match your filter: '${uiState.filterQuery}'",
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top=8.dp)
                )
            }
        }
    }
}

@Composable
fun UserFileListItemCard(
    fileInfo: FileInfoResponse,
    downloadState: FileDownloadState?,
    fileInfoViewModel: FileInfoViewModel,
    onFileSelected: () -> Unit
) {
    val thumbnailUrl = "https://pixeldrain.com/api/file/${fileInfo.id}/thumbnail"
    var menuExpanded by remember { mutableStateOf(false) }

    val showLpiSection = downloadState?.status == DownloadStatus.DOWNLOADING || downloadState?.status == DownloadStatus.PENDING
    val showStatusMessage = downloadState != null && !showLpiSection && (downloadState.status == DownloadStatus.FAILED || downloadState.status == DownloadStatus.COMPLETED) && downloadState.message != null

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable {
                fileInfoViewModel.fetchFileInfo(fileInfo.id)
                onFileSelected()
            },
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(modifier = Modifier.animateContentSize(animationSpec = tween(150))) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
            ) {
                val context = LocalContext.current
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(thumbnailUrl)
                        .size(80, 80) // Target 80x80 pixels for decoding
                        .crossfade(true)
                        .build(),
                    contentDescription = "${fileInfo.name} thumbnail",
                    modifier = Modifier
                        .size(80.dp)
                        .clip(
                            RoundedCornerShape(
                                topStart = 6.dp,
                                bottomStart = 6.dp,
                                topEnd = 0.dp,
                                bottomEnd = 0.dp
                            )
                        ),
                    contentScale = ContentScale.Crop,
                    error = rememberVectorPainter(Icons.Filled.BrokenImage),
                    placeholder = rememberVectorPainter(Icons.Filled.Image)
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
                     Text(
                         text = "Uploaded: ${formatApiDateTimeString(fileInfo.dateUpload)}",
                         style = MaterialTheme.typography.bodySmall,
                         color = MaterialTheme.colorScheme.onSurfaceVariant
                     )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Box {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(
                            imageVector = Icons.Filled.MoreVert,
                            contentDescription = "More options"
                        )
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        val downloadMenuItemText: @Composable () -> Unit
                        val downloadMenuItemIcon: (@Composable () -> Unit)?
                        val downloadMenuItemOnClick: () -> Unit
                        var isDownloadEnabled: Boolean

                        when (downloadState?.status) {
                            DownloadStatus.DOWNLOADING -> {
                                downloadMenuItemText = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        CircularProgressIndicator(
                                            progress = { downloadState.progressFraction },
                                            modifier = Modifier.size(20.dp),
                                            strokeWidth = 2.dp
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text("Downloading...")
                                    }
                                }
                                downloadMenuItemIcon = null
                                downloadMenuItemOnClick = { menuExpanded = false }
                                isDownloadEnabled = false
                            }
                            DownloadStatus.PENDING -> {
                                downloadMenuItemText = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(20.dp),
                                            strokeWidth = 2.dp
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text("Pending...")
                                    }
                                }
                                downloadMenuItemIcon = null
                                downloadMenuItemOnClick = { menuExpanded = false }
                                isDownloadEnabled = false
                            }
                            DownloadStatus.COMPLETED -> {
                                downloadMenuItemText = { Text("Download Again") }
                                downloadMenuItemIcon = { Icon(Icons.Filled.DownloadDone, "Download again icon", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp)) }
                                downloadMenuItemOnClick = {
                                    fileInfoViewModel.initiateDownloadFile(fileInfo)
                                    menuExpanded = false
                                }
                                isDownloadEnabled = true
                            }
                            DownloadStatus.FAILED -> {
                                downloadMenuItemText = { Text("Retry Download") }
                                downloadMenuItemIcon = { Icon(Icons.Filled.ErrorOutline, "Retry download icon", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(24.dp)) }
                                downloadMenuItemOnClick = {
                                    fileInfoViewModel.initiateDownloadFile(fileInfo)
                                    menuExpanded = false
                                }
                                isDownloadEnabled = true
                            }
                            else -> { // null for a fresh download
                                downloadMenuItemText = { Text("Download") }
                                downloadMenuItemIcon = { Icon(Icons.Filled.Download, "Download icon", modifier = Modifier.size(24.dp)) }
                                downloadMenuItemOnClick = {
                                    fileInfoViewModel.initiateDownloadFile(fileInfo)
                                    menuExpanded = false
                                }
                                isDownloadEnabled = true
                            }
                        }

                        DropdownMenuItem(
                            text = downloadMenuItemText,
                            onClick = downloadMenuItemOnClick,
                            leadingIcon = downloadMenuItemIcon,
                            enabled = isDownloadEnabled
                        )

                        if (fileInfo.canEdit == true) {
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("Delete") },
                                onClick = {
                                    fileInfoViewModel.initiateDeleteFile(fileInfo.id)
                                    menuExpanded = false
                                },
                                leadingIcon = {
                                    Icon(
                                        Icons.Filled.DeleteOutline,
                                        contentDescription = "Delete file icon",
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            )
                        }
                    }
                }
            }

            if (showLpiSection || showStatusMessage) {
                HorizontalDivider(
                    thickness = 1.0.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                    modifier = Modifier.padding(top = 4.dp) // Add some padding above the divider
                )
            }

            // Progress Indicator and Status Message Section
            if (showLpiSection) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp) // Padding for the whole LPI section
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (downloadState.status == DownloadStatus.PENDING) "Download pending..." else "Downloading...",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        val totalBytes = downloadState.totalBytes ?: 0L
                        if (totalBytes > 0) {
                            Text(
                                text = "${formatSize(downloadState.downloadedBytes)} / ${formatSize(totalBytes)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else if (downloadState.status == DownloadStatus.DOWNLOADING) { // Show percentage if total bytes unknown during download
                             Text(
                                text = "${(downloadState.progressFraction * 100).toInt()}%",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(2.dp)) // Small space between text and LPI
                    LinearProgressIndicator(
                        progress = { downloadState.progressFraction }, // showLpiSection ensures downloadState is not null
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                    )
                }
            } else if (showStatusMessage) {
                if (downloadState.status == DownloadStatus.COMPLETED) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp), // More compact padding
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.DownloadDone,
                            contentDescription = "Download completed", // Accessibility text
                            tint = MaterialTheme.colorScheme.primary, // Consistent with menu item
                            modifier = Modifier.size(24.dp) // Standard icon size
                        )
                    }
                } else if (downloadState.status == DownloadStatus.FAILED) {
                    // Error message remains as text for clarity
                    Text(
                        text = downloadState.message,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 8.dp), // Original padding for error text
                        textAlign = TextAlign.Center
                    )
                }
            }
        } // End of Column (with animateContentSize)
    } // End of Card
}
