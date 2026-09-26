package tools.senko.materialdrain.files

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import tools.senko.materialdrain.filesystem.FilesystemViewModel
import tools.senko.materialdrain.ui.LocalReduceMotion
import tools.senko.materialdrain.ui.components.CenteredLoadingMessage
import tools.senko.materialdrain.ui.components.CenteredTextMessage
import tools.senko.materialdrain.ui.components.ErrorMessage
import tools.senko.materialdrain.ui.components.FileListItem

// Component imports

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SortControls(
    uiState: FileInfoUiState,
    fileInfoViewModel: FileInfoViewModel,
    filterFocusRequester: FocusRequester,
    onFilterSubmitted: () -> Unit
) {
    // Choosing the field which is sorted on flips the direction, choosing another one starts ascending
    val sortOptions = listOf(
        "Name" to SortableField.NAME,
        "Size" to SortableField.SIZE,
        "Date" to SortableField.UPLOAD_DATE
    )

    var expanded by remember { mutableStateOf(false) }
    // Back gives up the focus of the filter (and with it the keyboard) instead of leaving the app
    val focusManager = LocalFocusManager.current
    var filterFocused by remember { mutableStateOf(false) }
    BackHandler(enabled = filterFocused) {
        focusManager.clearFocus()
        onFilterSubmitted()
    }
    val currentSortName = sortOptions.find { it.second == uiState.sortField }?.first.orEmpty()
    val directionIcon = if (uiState.sortAscending) Icons.Filled.ArrowUpward else Icons.Filled.ArrowDownward
    val directionDescription = if (uiState.sortAscending) "Ascending" else "Descending"

    // "Sort by" and "Filter by name" share the row, half of the width each
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded },
            modifier = Modifier.weight(1f)
        ) {
            OutlinedTextField(
                value = currentSortName,
                onValueChange = {},
                readOnly = true,
                singleLine = true,
                label = { Text("Sort by", maxLines = 1) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier
                    .menuAnchor(
                        type = ExposedDropdownMenuAnchorType.PrimaryNotEditable,
                        enabled = true
                    )
                    .fillMaxWidth()
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                sortOptions.forEach { (name, field) ->
                    val selected = field == uiState.sortField
                    DropdownMenuItem(
                        text = { Text(name) },
                        trailingIcon = if (selected) {
                            { Icon(directionIcon, contentDescription = directionDescription) }
                        } else null,
                        // The menu stays open, so the effect can be seen and the direction flipped again
                        onClick = {
                            fileInfoViewModel.changeSortOrder(field, if (selected) !uiState.sortAscending else true)
                        }
                    )
                }
            }
        }
        // Built from a BasicTextField so the label can sit on the border all the time, like the one of "Sort by".
        // (OutlinedTextField only moves the label there while it has focus or text.)
        val filterInteractionSource = remember { MutableInteractionSource() }
        BasicTextField(
            value = uiState.filterQuery,
            onValueChange = { fileInfoViewModel.onFilterQueryChanged(it) },
            modifier = Modifier
                .weight(1f)
                // The space the label takes above the border, the same as OutlinedTextField reserves
                .padding(top = 8.dp)
                .defaultMinSize(minHeight = OutlinedTextFieldDefaults.MinHeight)
                .focusRequester(filterFocusRequester)
                .onFocusChanged { filterFocused = it.isFocused },
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
            singleLine = true,
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onFilterSubmitted() }),
            interactionSource = filterInteractionSource,
            decorationBox = { innerTextField ->
                OutlinedTextFieldDefaults.DecorationBox(
                    // Never empty: that is what keeps the label up on the border
                    value = uiState.filterQuery.ifEmpty { " " },
                    innerTextField = innerTextField,
                    enabled = true,
                    singleLine = true,
                    visualTransformation = VisualTransformation.None,
                    interactionSource = filterInteractionSource,
                    label = { Text("Filter", maxLines = 1) },
                    // No trailing icon slot at all while empty, a slot takes room away from the text
                    trailingIcon = if (uiState.filterQuery.isNotEmpty()) {
                        {
                            IconButton(onClick = {
                                fileInfoViewModel.onFilterQueryChanged("")
                                onFilterSubmitted()
                            }) {
                                Icon(Icons.Filled.Clear, contentDescription = "Clear filter")
                            }
                        }
                    } else null
                )
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilesScreenContent(
    fileInfoViewModel: FileInfoViewModel,
    filesystemViewModel: FilesystemViewModel,
    onFileSelected: () -> Unit,
    listState: LazyListState,
    fabHeight: Dp,
    isFabVisible: Boolean
) {
    val uiState by fileInfoViewModel.uiState.collectAsState()
    val keyboardController = LocalSoftwareKeyboardController.current
    val filterFocusRequester = remember { FocusRequester() }
    val pullRefreshState = rememberPullToRefreshState()
    val context = LocalContext.current
    val displayedFiles by fileInfoViewModel.displayedFiles.collectAsState()

    // Being in selection mode is separate from having something selected: deselecting the last item keeps the mode
    var selecting by rememberSaveable { mutableStateOf(false) }
    var selectedIds by rememberSaveable { mutableStateOf<Set<String>>(emptySet()) }
    val selectionMode = selecting
    val exitSelection = {
        selecting = false
        selectedIds = emptySet()
    }
    val startSelecting = { id: String ->
        selecting = true
        selectedIds = selectedIds + id
    }
    var actionRequest by remember { mutableStateOf<FileActionRequest?>(null) }
    val selectedFiles = displayedFiles.filter { it.id in selectedIds }

    // Files which were deleted or are no longer listed can't stay selected
    LaunchedEffect(displayedFiles) {
        if (selectedIds.isNotEmpty()) {
            val listedIds = displayedFiles.mapTo(HashSet()) { it.id }
            val remaining = selectedIds.filterTo(HashSet()) { it in listedIds }
            if (remaining.size != selectedIds.size) selectedIds = remaining
        }
    }

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
    // Registered last, so leaving the selection mode takes precedence over the handler above
    BackHandler(enabled = selectionMode) { exitSelection() }

    FileActionDialogs(
        request = actionRequest,
        fileInfoViewModel = fileInfoViewModel,
        filesystemViewModel = filesystemViewModel,
        onDismiss = { actionRequest = null },
        onStarted = { exitSelection() }
    )

    LaunchedEffect(uiState.showFilterInput) {
        if (uiState.showFilterInput) {
            filterFocusRequester.requestFocus()
            keyboardController?.show()
        }
    }

    // Only coming back from the file details keeps the scroll position, any other way of entering the
    // screen starts at the top.
    LaunchedEffect(Unit) {
        if (uiState.shouldPreserveScrollPosition) {
            fileInfoViewModel.setPreserveScrollPosition(false)
        } else if (listState.firstVisibleItemIndex != 0 || listState.firstVisibleItemScrollOffset != 0) {
            listState.scrollToItem(0)
        }
    }

    var sortEffectInitialized by remember { mutableStateOf(false) }
    LaunchedEffect(uiState.sortField, uiState.sortAscending) {
        if (!sortEffectInitialized) {
            sortEffectInitialized = true
            return@LaunchedEffect
        }
        if (displayedFiles.isNotEmpty()) listState.scrollToItem(0)
    }

    // After a manual refresh, glide to the top once the refreshed files are displayed. A list which is scrolled
    // to the top stays anchored to the file which was first before, so without this a new file would be
    // inserted above the visible area and it would look like nothing happened.
    val reduceMotion = LocalReduceMotion.current
    var scrollToTopAfterRefresh by remember { mutableStateOf(false) }
    LaunchedEffect(scrollToTopAfterRefresh) {
        if (!scrollToTopAfterRefresh) return@LaunchedEffect
        // The refresh starts loading a moment after it was requested
        withTimeoutOrNull(1500) { snapshotFlow { uiState.isLoadingUserFiles }.first { it } }
        withTimeoutOrNull(30_000) {
            snapshotFlow {
                // The displayed list is derived from the loaded files (filtered and sorted) a moment later
                val expectedIds = uiState.userFilesList
                    .filter { it.name.contains(uiState.filterQuery, ignoreCase = true) }
                    .mapTo(HashSet()) { it.id }
                !uiState.isLoadingUserFiles && displayedFiles.mapTo(HashSet()) { it.id } == expectedIds
            }.first { it }
        }
        scrollToTopAfterRefresh = false
        if (reduceMotion) listState.scrollToItem(0) else listState.animateScrollToItem(0)
    }

    PullToRefreshBox(
        isRefreshing = uiState.isLoadingUserFiles,
        onRefresh = {
            scrollToTopAfterRefresh = true
            fileInfoViewModel.fetchUserFiles()
        },
        state = pullRefreshState,
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            SelectionActionBar(
                visible = selectionMode,
                selectedCount = selectedFiles.size,
                onClose = { exitSelection() }
            ) { dismiss ->
                FileSelectionMenuItems(
                    hasSelection = selectedFiles.isNotEmpty(),
                    allSelected = displayedFiles.isNotEmpty() && selectedFiles.size == displayedFiles.size,
                    onToggleSelectAll = {
                        selectedIds = if (selectedFiles.size == displayedFiles.size) emptySet() else displayedFiles.mapTo(HashSet()) { it.id }
                    },
                    onDownloadZip = {
                        fileInfoViewModel.downloadFilesAsZip(selectedFiles, "Pixeldrain files")
                        exitSelection()
                    },
                    onAddToFilesystem = { actionRequest = FileActionRequest.AddToFilesystem(selectedFiles) },
                    onAddToList = { actionRequest = FileActionRequest.AddToList(selectedFiles) },
                    onDelete = { actionRequest = FileActionRequest.Delete(selectedFiles) },
                    dismiss = dismiss
                )
            }

            // Always shown, also when nothing matches the filter, so the filter can be changed or cleared
            SortControls(
                uiState = uiState,
                fileInfoViewModel = fileInfoViewModel,
                filterFocusRequester = filterFocusRequester,
                onFilterSubmitted = { keyboardController?.hide() }
            )

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
                            selectionMode = selectionMode,
                            selected = file.id in selectedIds,
                            onLongClick = { startSelecting(file.id) },
                            trailingContent = {
                                FileItemMenu(
                                    file = file,
                                    canDelete = file.canEdit != false,
                                    onDownload = { fileInfoViewModel.initiateDownloadFile(file) },
                                    onSelect = { startSelecting(file.id) },
                                    onAddToFilesystem = { actionRequest = FileActionRequest.AddToFilesystem(listOf(file)) },
                                    onAddToList = { actionRequest = FileActionRequest.AddToList(listOf(file)) },
                                    onDelete = { actionRequest = FileActionRequest.Delete(listOf(file)) }
                                )
                            },
                            onClick = {
                                if (selectionMode) {
                                    selectedIds = if (file.id in selectedIds) selectedIds - file.id else selectedIds + file.id
                                } else {
                                    if (uiState.showFilterInput) fileInfoViewModel.setFilterInputVisible(false)
                                    fileInfoViewModel.fetchFileInfo(file.id)
                                    onFileSelected()
                                }
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