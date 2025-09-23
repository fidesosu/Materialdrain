package com.example.materialdrain.ui

import android.app.Application
import android.util.Log
import androidx.activity.compose.BackHandler // Added import for BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalOverscrollConfiguration // Added import
import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.foundation.layout.* // Corrected import
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
// import androidx.compose.ui.window.Dialog // Dialog import removed
import androidx.compose.ui.zIndex
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.materialdrain.network.PixeldrainApiService
import com.example.materialdrain.ui.dialogs.EnterFileIdDialog
import com.example.materialdrain.ui.dialogs.FileInfoDetailsCard
import com.example.materialdrain.ui.screens.FilesScreenContent
import com.example.materialdrain.ui.screens.ListsScreenContent
import com.example.materialdrain.ui.screens.SettingsScreenContent
import com.example.materialdrain.ui.screens.UploadScreenContent
import com.example.materialdrain.ui.theme.MaterialdrainTheme
import com.example.materialdrain.viewmodel.FileInfoViewModel
import com.example.materialdrain.viewmodel.UploadViewModel
import com.example.materialdrain.viewmodel.ViewModelFactory
import kotlinx.coroutines.flow.collectLatest
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.DateTimeParseException

// Define the screens in the app
enum class Screen(val title: String, val icon: ImageVector, val hasDetailsPage: Boolean = false) {
    Upload("Upload", Icons.Filled.FileUpload),
    Files("Files", Icons.Filled.Folder, true), // Mark that Files can navigate to a detail page
    FileDetail("File Details", Icons.Filled.Description), // New screen for file details
    Lists("Lists", Icons.AutoMirrored.Filled.List),
    Settings("Settings", Icons.Filled.Settings)
}

// Helper function to format API date-time strings
internal fun formatApiDateTimeString(dateTimeString: String?): String {
    if (dateTimeString.isNullOrBlank()) {
        return "N/A"
    }
    return try {
        val parsedDateTime = LocalDateTime.parse(dateTimeString, DateTimeFormatter.ISO_DATE_TIME)
        val formatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)
            .withZone(ZoneId.systemDefault())
        parsedDateTime.atZone(ZoneId.of("UTC")).withZoneSameInstant(ZoneId.systemDefault()).format(formatter)
    } catch (e: DateTimeParseException) {
        Log.e("DateTimeFormat", "Error parsing date: $dateTimeString", e)
        dateTimeString
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun MaterialDrainScreen() {
    var currentScreen by remember { mutableStateOf<Screen>(Screen.Upload) }

    var showGenericDialog by remember { mutableStateOf(false) }
    var genericDialogContent by remember { mutableStateOf("") }
    var genericDialogTitle by remember { mutableStateOf("") }

    val application = LocalContext.current.applicationContext as Application
    val pixeldrainApiService = remember { PixeldrainApiService() }
    val viewModelFactory = remember { ViewModelFactory(application, pixeldrainApiService) }

    val uploadViewModel: UploadViewModel = viewModel(factory = viewModelFactory)
    val fileInfoViewModel: FileInfoViewModel = viewModel(factory = viewModelFactory)

    val snackbarHostState = remember { SnackbarHostState() }
    val fileInfoUiState by fileInfoViewModel.uiState.collectAsState()
    val uploadUiState by uploadViewModel.uiState.collectAsState()

    // Initial file fetch on app launch
    LaunchedEffect(Unit) { // Runs once when MaterialDrainScreen is first composed
        Log.d("MaterialDrainScreen", "Initial composition. ViewModels should handle their own initial data loading via init blocks.")
        // fileInfoViewModel.loadApiKey() // Removed: ViewModel's init now handles this.
    }

    // Handle system back press for FileDetail screen
    if (currentScreen == Screen.FileDetail) {
        BackHandler(enabled = true) {
            currentScreen = Screen.Files
            // fileInfoViewModel.clearFileInfoDisplay() // Optionally clear, or rely on LaunchedEffect
        }
    }

    LaunchedEffect(currentScreen) {
        Log.d("MaterialDrainScreen", "currentScreen changed to: ${currentScreen.name}")
        when (currentScreen) {
            Screen.Files -> {
                // fileInfoViewModel.loadApiKey() // Call is made on initial composition now, or when settings change it.
                // Still, if user navigates to Files and API key was just set, this might be useful.
                // For now, relying on initial load and settings screen to update.
            }
            Screen.FileDetail -> {
                // Ensure filter is hidden on detail screen
                fileInfoViewModel.setFilterInputVisible(false)
            }
            else -> { // Upload, Lists, Settings
                if (currentScreen != Screen.FileDetail) { // Don'''t clear if we are on file detail
                    fileInfoViewModel.clearFileInfoDisplay() // Clear if navigating away from Files/FileDetail to other main screens
                }
                fileInfoViewModel.clearUserFilesError()
                fileInfoViewModel.clearApiKeyMissingError()
                fileInfoViewModel.setFilterInputVisible(false)
            }
        }
    }

    LaunchedEffect(key1 = uploadViewModel) {
        uploadViewModel.uiState.collectLatest { uiState ->
            uiState.uploadResult?.let {
                if (!it.success) {
                    genericDialogTitle = "Upload Failed"
                    genericDialogContent = "Error: ${it.message ?: it.value ?: "Unknown error"}"
                    showGenericDialog = true
                }
            }
            uiState.errorMessage?.let {
                if (!it.contains("API Key", ignoreCase = true) &&
                    !it.contains("preview", ignoreCase = true) &&
                    !it.contains("metadata", ignoreCase = true) &&
                    !showGenericDialog &&
                    !fileInfoUiState.showEnterFileIdDialog &&
                    currentScreen == Screen.Upload) {
                    genericDialogTitle = "Upload Error"
                    genericDialogContent = it
                    showGenericDialog = true
                }
            }
        }
    }

    if (uploadUiState.errorMessage?.contains("API Key is missing") == true && currentScreen == Screen.Upload && !fileInfoUiState.showEnterFileIdDialog) {
        LaunchedEffect(uploadUiState.errorMessage, currentScreen) {
            genericDialogTitle = "API Key Required for Upload"
            genericDialogContent = "Please set your API Key in the Settings screen to upload files."
            showGenericDialog = true
        }
    }

    LaunchedEffect(fileInfoUiState.deleteFileSuccessMessage) {
        fileInfoUiState.deleteFileSuccessMessage?.let {
            snackbarHostState.showSnackbar(it)
            fileInfoViewModel.clearDeleteMessages()
            if (currentScreen == Screen.FileDetail) { // If on detail page, navigate back after delete
                currentScreen = Screen.Files
            }
        }
    }
    LaunchedEffect(fileInfoUiState.deleteFileErrorMessage) {
        fileInfoUiState.deleteFileErrorMessage?.let {
            snackbarHostState.showSnackbar("Delete failed: $it", duration = SnackbarDuration.Long)
            fileInfoViewModel.clearDeleteMessages()
        }
    }

    LaunchedEffect(fileInfoUiState.fileDownloadSuccessMessage) {
        fileInfoUiState.fileDownloadSuccessMessage?.let {
            snackbarHostState.showSnackbar(it, duration = SnackbarDuration.Long)
            fileInfoViewModel.clearDownloadMessages()
        }
    }
    LaunchedEffect(fileInfoUiState.fileDownloadErrorMessage) {
        fileInfoUiState.fileDownloadErrorMessage?.let {
            snackbarHostState.showSnackbar("Download failed: $it", duration = SnackbarDuration.Long)
            fileInfoViewModel.clearDownloadMessages()
        }
    }

    if (fileInfoUiState.apiKeyMissingError && (currentScreen == Screen.Files || currentScreen == Screen.FileDetail) &&
        !fileInfoUiState.userFilesListErrorMessage.isNullOrBlank() &&
        !showGenericDialog && !fileInfoUiState.showDeleteConfirmDialog && !fileInfoUiState.showEnterFileIdDialog) {
        LaunchedEffect(fileInfoUiState.apiKeyMissingError, currentScreen, fileInfoUiState.userFilesListErrorMessage) {
            if (fileInfoUiState.userFilesListErrorMessage!!.contains("API Key", ignoreCase = true)) {
                genericDialogTitle = if (currentScreen == Screen.FileDetail) "API Key Required" else "API Key Required for Files"
                genericDialogContent = fileInfoUiState.userFilesListErrorMessage!!
                showGenericDialog = true
            }
        }
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (currentScreen == Screen.FileDetail) {
                                    fileInfoUiState.fileInfo?.name ?: Screen.FileDetail.title
                                } else {
                                    currentScreen.title
                                },
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            AnimatedVisibility(
                                visible = currentScreen == Screen.Upload && uploadUiState.isLoading,
                                enter = fadeIn(animationSpec = tween(300)),
                                exit = fadeOut(animationSpec = tween(300))
                            ) {
                                val totalSize = uploadUiState.uploadTotalSizeBytes
                                val progressText = if (totalSize != null && totalSize > 0) {
                                    "${formatSize(uploadUiState.uploadedBytes)} / ${formatSize(totalSize)}"
                                } else {
                                    formatSize(uploadUiState.uploadedBytes)
                                }
                                Text(
                                    text = progressText,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(start = 8.dp)
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        if (currentScreen == Screen.FileDetail) {
                            IconButton(onClick = {
                                currentScreen = Screen.Files
                                // fileInfoViewModel.clearFileInfoDisplay() // Optionally clear here or rely on LaunchedEffect
                            }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to Files")
                            }
                        }
                    }
                )
                AnimatedVisibility(
                    visible = currentScreen == Screen.Upload && uploadUiState.isLoading,
                    enter = fadeIn(animationSpec = tween(150)),
                    exit = fadeOut(animationSpec = tween(150))
                ) {
                    val progress = if (uploadUiState.uploadTotalSizeBytes != null && uploadUiState.uploadTotalSizeBytes!! > 0L) {
                        (uploadUiState.uploadedBytes.toFloat() / uploadUiState.uploadTotalSizeBytes!!).coerceIn(0f, 1f)
                    } else {
                        0f
                    }
                    if (uploadUiState.uploadTotalSizeBytes != null && uploadUiState.uploadTotalSizeBytes!! > 0L) {
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        },
        bottomBar = {
            AnimatedVisibility(visible = currentScreen != Screen.FileDetail) { // Hide for FileDetail
                BottomNavigationBar(currentScreen) { selectedScreen ->
                    if (currentScreen != selectedScreen) {
                        currentScreen = selectedScreen
                    }
                }
            }
        },
        floatingActionButton = {
            AnimatedVisibility(
                visible = currentScreen == Screen.Files,
                enter = scaleIn(animationSpec = tween(300)),
                exit = scaleOut(animationSpec = tween(300))
            ) {
                FloatingActionButton(
                    onClick = { fileInfoViewModel.toggleFilterInput() }
                ) {
                    Icon(
                        if (fileInfoUiState.showFilterInput) Icons.Filled.Close else Icons.Filled.FilterList,
                        contentDescription = if (fileInfoUiState.showFilterInput) "Close Filter" else "Filter by Name"
                    )
                }
            }
        },
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .offset(y = if (currentScreen != Screen.FileDetail) 72.dp else 0.dp) // Adjust offset if bottom bar hidden
                    .zIndex(1f)
            ) { data ->
                Snackbar(
                    snackbarData = data,
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    actionColor = MaterialTheme.colorScheme.primary
                )
            }
        }
    ) { paddingValues ->
        CompositionLocalProvider(LocalOverscrollFactory provides null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues) // This padding is important for Scaffold content
            ) {
                when (currentScreen) {
                    Screen.Upload -> UploadScreenContent(
                        uploadViewModel = uploadViewModel,
                        onShowDialog = { title, content ->
                            genericDialogTitle = title
                            genericDialogContent = content
                            showGenericDialog = true
                        }
                    )
                    Screen.Files -> FilesScreenContent(
                        fileInfoViewModel = fileInfoViewModel,
                        onFileSelected = { // This will be passed to FilesScreenContent
                            // ViewModel should already have fetched/set this file info
                            currentScreen = Screen.FileDetail
                        }
                    )
                    Screen.FileDetail -> {
                        fileInfoUiState.fileInfo?.let { info ->
                            FileInfoDetailsCard(
                                fileInfo = info,
                                fileInfoViewModel = fileInfoViewModel,
                                context = LocalContext.current
                            )
                        } ?: run {
                            // Show a loading or error state if fileInfo is null for some reason
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                if (fileInfoUiState.isLoadingFileInfo) {
                                    CircularProgressIndicator()
                                } else {
                                    Text("File details not available. Please go back and select a file.")
                                    LaunchedEffect(Unit) { // Auto navigate back if no info
                                        currentScreen = Screen.Files
                                    }
                                }
                            }
                        }
                    }
                    Screen.Lists -> ListsScreenContent(
                        onShowDialog = { title, content ->
                            genericDialogTitle = title
                            genericDialogContent = content
                            showGenericDialog = true
                        }
                    )
                    Screen.Settings -> SettingsScreenContent(
                        uploadViewModel = uploadViewModel,
                        fileInfoViewModel = fileInfoViewModel,
                        onShowDialog = { title, content ->
                            genericDialogTitle = title
                            genericDialogContent = content
                            showGenericDialog = true
                        }
                    )
                }
            }
        }
    }

    if (showGenericDialog) {
        AlertDialog(
            onDismissRequest = {
                showGenericDialog = false
                if (genericDialogTitle == "Upload Failed" || genericDialogTitle == "Upload Error"){
                    uploadViewModel.clearUploadResult()
                }
                if (genericDialogContent.contains("API Key") && uploadUiState.errorMessage?.contains("API Key") == true) {
                    uploadViewModel.clearApiKeyError()
                }
                if (genericDialogTitle.contains("API Key Required")) { // Broader match for API key dialogs
                    fileInfoViewModel.clearUserFilesError()
                    fileInfoViewModel.clearApiKeyMissingError()
                }
            },
            title = { Text(genericDialogTitle) },
            text = { Text(genericDialogContent) },
            confirmButton = {
                Button(onClick = {
                    showGenericDialog = false
                    if (genericDialogTitle == "Upload Failed" || genericDialogTitle == "Upload Error"){
                        uploadViewModel.clearUploadResult()
                    }
                    if (genericDialogContent.contains("API Key") && uploadUiState.errorMessage?.contains("API Key") == true) {
                        uploadViewModel.clearApiKeyError()
                    }
                    if (genericDialogTitle.contains("API Key Required")) { // Broader match for API key dialogs
                        fileInfoViewModel.clearUserFilesError()
                        fileInfoViewModel.clearApiKeyMissingError()
                        if (genericDialogContent.contains("Settings")) {
                            val settingsScreen = Screen.Settings
                            if (currentScreen != settingsScreen) {
                                currentScreen = settingsScreen
                            }
                        }
                    }
                }) {
                    Text("OK")
                }
            }
        )
    }

    // Removed the Dialog wrapper for FileInfoDetailsCard as it'''s now a screen

    if (fileInfoUiState.showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { fileInfoViewModel.cancelDeleteFile() },
            title = { Text("Confirm Deletion") },
            text = { Text("Are you sure you want to delete file ID: ${fileInfoUiState.fileIdToDelete}? This action cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = { fileInfoViewModel.confirmDeleteFile() },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                Button(onClick = { fileInfoViewModel.cancelDeleteFile() }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (fileInfoUiState.showEnterFileIdDialog) {
        EnterFileIdDialog(fileInfoViewModel = fileInfoViewModel)
    }
}


@Composable
fun BottomNavigationBar(currentScreen: Screen, onScreenSelected: (Screen) -> Unit) {
    NavigationBar {
        // Filter out FileDetail from bottom navigation items
        Screen.entries.filter { it != Screen.FileDetail }.forEach { screen ->
            NavigationBarItem(
                icon = { Icon(screen.icon, contentDescription = screen.title) },
                label = { Text(screen.title) },
                selected = currentScreen == screen,
                onClick = { onScreenSelected(screen) }
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun DefaultPreviewMaterialDrainScreen() {
    MaterialdrainTheme {
        MaterialDrainScreen()
    }
}