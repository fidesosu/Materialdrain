package com.example.materialdrain.ui.screens

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.materialdrain.network.FileInfoResponse
import com.example.materialdrain.ui.formatApiDateTimeString
import com.example.materialdrain.ui.formatSize
import com.example.materialdrain.viewmodel.FileInfoUiState
import com.example.materialdrain.viewmodel.FileInfoViewModel
import com.example.materialdrain.viewmodel.SortableField

// Component imports
import com.example.materialdrain.components.FileListItem
import com.example.materialdrain.components.CenteredTextMessage
import com.example.materialdrain.components.ErrorMessage
import com.example.materialdrain.components.CenteredLoadingMessage

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
            ?: "Sort by..."
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
            //.padding(bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded },
            modifier = Modifier.weight(1f)
        ) {
            OutlinedTextField(
                value = currentSortOptionText,
                onValueChange = {},
                readOnly = true,
                label = { Text("Sort by") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier
                    .menuAnchor(
                        type = ExposedDropdownMenuAnchorType.PrimaryNotEditable,
                        enabled = true
                    )
                    .fillMaxWidth()
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                sortOptions.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option.displayText) },
                        onClick = {
                            fileInfoViewModel.changeSortOrder(option.field, option.ascending)
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
    listState: LazyListState,
    fabHeight: Dp,
    isFabVisible: Boolean
) {
    val uiState by fileInfoViewModel.uiState.collectAsState()
    val keyboardController = LocalSoftwareKeyboardController.current
    val filterFocusRequester = remember { FocusRequester() }
    val pullRefreshState = rememberPullToRefreshState()
    var isInitialUserFilesLoad by remember { mutableStateOf(true) }
    val context = LocalContext.current
    val displayedFiles by fileInfoViewModel.displayedFiles.collectAsState()

    BackHandler(enabled = true) {
        if (uiState.showFilterInput && uiState.filterQuery.isNotEmpty()) {
            fileInfoViewModel.onFilterQueryChanged("")
            fileInfoViewModel.setFilterInputVisible(false)
            keyboardController?.hide()
        } else if (uiState.showFilterInput) {
            fileInfoViewModel.setFilterInputVisible(false)
            keyboardController?.hide()
        } else {
            (context as? Activity)?.finish()
        }
    }

    LaunchedEffect(uiState.showFilterInput) {
        if (uiState.showFilterInput) {
            filterFocusRequester.requestFocus()
            keyboardController?.show()
        }
    }

    LaunchedEffect(uiState.shouldPreserveScrollPosition) {
        if (uiState.shouldPreserveScrollPosition) {
            fileInfoViewModel.setPreserveScrollPosition(false)
        }
    }

    LaunchedEffect(uiState.sortField, uiState.sortAscending) {
        if (!uiState.shouldPreserveScrollPosition && displayedFiles.isNotEmpty() && listState.firstVisibleItemIndex != 0) {
            listState.scrollToItem(0)
        }
    }

    LaunchedEffect(uiState.userFilesList) {
        if (isInitialUserFilesLoad) {
            if (uiState.userFilesList.isNotEmpty()) isInitialUserFilesLoad = false
        } else {
            if (!uiState.shouldPreserveScrollPosition && displayedFiles.isNotEmpty() && listState.firstVisibleItemIndex != 0) {
                listState.scrollToItem(0)
            }
        }
    }

    PullToRefreshBox(
        isRefreshing = uiState.isLoadingUserFiles,
        onRefresh = { fileInfoViewModel.fetchUserFiles() },
        state = pullRefreshState,
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

          // This needs a lot of work to be good. It should animate in and out, and ideally not cause the list to jump around when it appears/disappears. It also needs a "clear" button when there's text in it, and should hide the keyboard when you submit a search or clear the text.
            AnimatedVisibility(visible = uiState.showFilterInput, enter = fadeIn(), exit = fadeOut()) {
                OutlinedTextField(
                    value = uiState.filterQuery,
                    onValueChange = { fileInfoViewModel.onFilterQueryChanged(it) },
                    label = { Text("Filter by name") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp)
                        .padding(bottom = 4.dp)
                        .focusRequester(filterFocusRequester),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { keyboardController?.hide() }),
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

            when {
                uiState.apiKeyMissingError -> {
                    ErrorMessage("API Key is missing. Please set it in Settings to load and manage your files.")
                }
                uiState.isLoadingUserFiles && displayedFiles.isEmpty() && uiState.filterQuery.isBlank() -> {
                    CenteredLoadingMessage("Loading Files...")
                }
                uiState.userFilesListErrorMessage != null -> {
                    uiState.userFilesListErrorMessage?.let { ErrorMessage(it) }
                }
                displayedFiles.isEmpty() && uiState.filterQuery.isBlank() -> {
                    CenteredTextMessage("No files found. Try pulling down to refresh.")
                }
                displayedFiles.isEmpty() && uiState.filterQuery.isNotBlank() -> {
                    CenteredTextMessage("No files match your filter: '${uiState.filterQuery}'")
                }
            }

            if (displayedFiles.isNotEmpty()) {
                SortControls(uiState, fileInfoViewModel)
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(
                        top = 8.dp,
                        bottom = if (isFabVisible) fabHeight + 16.dp else 8.dp
                    )
                ) {
                    items(displayedFiles, key = { it.id }) { file ->
                        FileListItem(
                            name = file.name,
                            type = "file",
                            fileSize = file.size,
                            modified = file.dateUpload,
                            mimeType = file.mimeType,
                            thumbnailUrl = "https://pixeldrain.com/api/file/${file.id}/thumbnail",
                            apiKey = uiState.apiKey,
                            onClick = {
                                if (uiState.showFilterInput) fileInfoViewModel.setFilterInputVisible(false)
                                fileInfoViewModel.fetchFileInfo(file.id)
                                onFileSelected()
                            }
                        )
                        HorizontalDivider(
                            thickness = 0.5.dp, 
                            color = MaterialTheme.colorScheme.outlineVariant.copy(
                                alpha = 0.5f
                            )
                        )
                    }
                }
            }
        }
    }
}