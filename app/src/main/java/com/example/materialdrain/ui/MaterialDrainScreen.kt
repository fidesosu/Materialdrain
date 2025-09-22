package com.example.materialdrain.ui

import android.app.Application
import android.content.Context // Kept for LocalContext.current.applicationContext
import android.content.Intent // Kept for Scaffold's when block (indirectly, if dialogs ever need it, though current moved ones do not directly trigger intents from here)
import android.net.Uri // Kept for similar reasons as Intent
import android.util.Log 
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween 
import androidx.compose.animation.fadeIn 
import androidx.compose.animation.fadeOut 
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState // Used by a when case that's not moved
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog // Kept for FileInfoDetailsCard dialog wrapper
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.materialdrain.network.PixeldrainApiService
import com.example.materialdrain.ui.theme.MaterialdrainTheme
import com.example.materialdrain.viewmodel.FileInfoViewModel
import com.example.materialdrain.viewmodel.UploadViewModel
import com.example.materialdrain.viewmodel.ViewModelFactory
import kotlinx.coroutines.flow.collectLatest
import java.text.DecimalFormat
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.DateTimeParseException
import androidx.compose.ui.zIndex
import com.example.materialdrain.ui.screens.UploadScreenContent
import com.example.materialdrain.ui.screens.FilesScreenContent
import com.example.materialdrain.ui.screens.ListsScreenContent
import com.example.materialdrain.ui.screens.SettingsScreenContent
import com.example.materialdrain.ui.dialogs.EnterFileIdDialog // IMPORT THE MOVED DIALOG
import com.example.materialdrain.ui.dialogs.FileInfoDetailsCard // IMPORT THE MOVED DIALOG/CARD

// Define the screens in the app
enum class Screen(val title: String, val icon: ImageVector) {
    Upload("Upload", Icons.Filled.FileUpload),
    Files("Files", Icons.Filled.Folder),
    Lists("Lists", Icons.AutoMirrored.Filled.List),
    Settings("Settings", Icons.Filled.Settings)
}

// Helper function to format size in bytes to a human-readable string
internal fun formatSize(bytes: Long): String {
    if (bytes < 0) return "0 B"
    if (bytes == 0L) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    var size = bytes.toDouble()
    var unitIndex = 0
    while (size >= 1024 && unitIndex < units.size - 1) {
        size /= 1024
        unitIndex++
    }
    return DecimalFormat("#,##0.#").format(size) + " " + units[unitIndex]
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MaterialDrainScreen() {
    var currentScreen by remember { mutableStateOf(Screen.Upload) }
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

    if (fileInfoUiState.apiKeyMissingError && currentScreen == Screen.Files &&
        !fileInfoUiState.userFilesListErrorMessage.isNullOrBlank() &&
        !showGenericDialog && !fileInfoUiState.showDeleteConfirmDialog && !fileInfoUiState.showEnterFileIdDialog && fileInfoUiState.fileInfo == null) {
        LaunchedEffect(fileInfoUiState.apiKeyMissingError, currentScreen, fileInfoUiState.userFilesListErrorMessage) {
            if (fileInfoUiState.userFilesListErrorMessage!!.contains("API Key", ignoreCase = true)) {
                genericDialogTitle = "API Key Required for Files"
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
                            Text(currentScreen.title, modifier = Modifier.weight(1f))
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
            BottomNavigationBar(currentScreen) { screen ->
                currentScreen = screen
                if (screen != Screen.Files) {
                    fileInfoViewModel.clearFileInfoDisplay()
                    fileInfoViewModel.clearUserFilesError()
                    fileInfoViewModel.clearApiKeyMissingError()
                    fileInfoViewModel.setFilterInputVisible(false)
                } else {
                    fileInfoViewModel.loadApiKey()
                }
            }
        },
        floatingActionButton = {
            if (currentScreen == Screen.Files) {
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
                    .offset(y = 72.dp)
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
        Box(modifier = Modifier.padding(paddingValues)) {
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
                    fileInfoViewModel = fileInfoViewModel
                )
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
                if (genericDialogTitle.contains("API Key Required for Files")) {
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
                    if (genericDialogTitle.contains("API Key Required for Files")) {
                        fileInfoViewModel.clearUserFilesError()
                        fileInfoViewModel.clearApiKeyMissingError()
                        if (genericDialogContent.contains("Settings")) {
                            currentScreen = Screen.Settings
                        }
                    }
                }) {
                    Text("OK")
                }
            }
        )
    }

    if (fileInfoUiState.fileInfo != null && currentScreen == Screen.Files && !fileInfoUiState.showFilterInput) {
        Dialog(onDismissRequest = { fileInfoViewModel.clearFileInfoDisplay() }) {
            FileInfoDetailsCard(fileInfo = fileInfoUiState.fileInfo!!, fileInfoViewModel = fileInfoViewModel, context = LocalContext.current)
        }
    }

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

    // EnterFileIdDialog is called from FilesScreenContent or FAB if we decide to add a FAB action for it.
    // For now, it might be triggered by a button within FilesScreenContent if that screen needs it.
    // Or, if it's a global action, its visibility could be controlled by a state in FileInfoViewModel.
    // The current implementation calls fileInfoViewModel.showEnterFileIdDialog() which updates uiState.showEnterFileIdDialog
    // We need to ensure that EnterFileIdDialog is displayed based on this state.
    if (fileInfoUiState.showEnterFileIdDialog) {
        EnterFileIdDialog(fileInfoViewModel = fileInfoViewModel)
    }
}


@Composable
fun BottomNavigationBar(currentScreen: Screen, onScreenSelected: (Screen) -> Unit) {
    NavigationBar {
        Screen.entries.forEach { screen ->
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

