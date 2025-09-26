package com.example.materialdrain.ui

import android.app.Application
import android.content.Context // Added for SharedPreferences
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SizeTransform // Added import for SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith // Added import for togetherWith
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
// Import specific filled icons that are still used directly
import androidx.compose.material.icons.filled.Description 
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.materialdrain.R // Import R class
import com.example.materialdrain.network.PixeldrainApiService
import com.example.materialdrain.ui.screens.EnterFileIdDialog
import com.example.materialdrain.ui.screens.FileInfoDetailsCard
import com.example.materialdrain.ui.screens.FilesScreenContent
import com.example.materialdrain.ui.screens.FilesystemScreen // Updated import
import com.example.materialdrain.ui.screens.ListsScreenContent
import com.example.materialdrain.ui.screens.SettingsScreenContent
import com.example.materialdrain.ui.screens.UploadScreenContent
import com.example.materialdrain.ui.shared.AppSnackbarHost
import com.example.materialdrain.ui.theme.MaterialdrainTheme
import com.example.materialdrain.viewmodel.FileInfoViewModel
import com.example.materialdrain.viewmodel.FilesystemViewModel 
import com.example.materialdrain.viewmodel.UploadViewModel
import com.example.materialdrain.viewmodel.ViewModelFactory
import kotlinx.coroutines.flow.collectLatest
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.DateTimeParseException

// SharedPreferences constants moved here
private const val PREFS_NAME = "pixeldrain_prefs"
private const val API_KEY_PREF = "api_key"

// Define the screens in the app
enum class Screen(val title: String, @DrawableRes val iconResId: Int?) {
    Upload("Upload", R.drawable.icon_upload),
    Files("Files", R.drawable.icon_folder_outlined),
    Filesystem("Filesystem", R.drawable.icon_hard_drive_outlined),
    FileDetail("File Details", null), // Using null for now, direct usage for its icon
    Lists("Lists", R.drawable.icon_list),
    Settings("Settings", R.drawable.icon_settings_outlined)
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, ExperimentalSharedTransitionApi::class)
@Composable
fun MaterialDrainScreen() {
    var currentScreen by rememberSaveable { mutableStateOf(Screen.Upload) }

    var showGenericDialog by remember { mutableStateOf(false) }
    var genericDialogContent by remember { mutableStateOf("") }
    var genericDialogTitle by remember { mutableStateOf("") }

    val application = LocalContext.current.applicationContext as Application
    val context = LocalContext.current // For SharedPreferences
    val pixeldrainApiService = remember { PixeldrainApiService() }
    val viewModelFactory = remember { ViewModelFactory(application, pixeldrainApiService) }

    val uploadViewModel: UploadViewModel = viewModel(factory = viewModelFactory)
    val fileInfoViewModel: FileInfoViewModel = viewModel(factory = viewModelFactory)
    val filesystemViewModel: FilesystemViewModel = viewModel(factory = viewModelFactory) // Instantiate FilesystemViewModel

    val snackbarHostState = remember { SnackbarHostState() }
    val fileInfoUiState by fileInfoViewModel.uiState.collectAsState()
    val uploadUiState by uploadViewModel.uiState.collectAsState()
    // val filesystemUiState by filesystemViewModel.uiState.collectAsState() // Collect if needed directly here

    val filesScreenListState = rememberLazyListState()

    var apiKeyInput by rememberSaveable { mutableStateOf("") }
    var fabHeightDp by remember { mutableStateOf(0.dp) }
    val localDensity = LocalDensity.current

    // Define the order of screens in the bottom navigation bar
    val navBarOrder = listOf(
        Screen.Upload,
        Screen.Files,
        Screen.Lists,
        Screen.Filesystem
    )

    LaunchedEffect(Unit) {
        Log.d("App", "Initial composition. Loading API Key from SharedPreferences.")
        val sharedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        apiKeyInput = sharedPrefs.getString(API_KEY_PREF, "") ?: ""
    }

    if (currentScreen == Screen.FileDetail) {
        BackHandler(enabled = true) {
            fileInfoViewModel.setPreserveScrollPosition(true)
            currentScreen = Screen.Files
        }
    }

    LaunchedEffect(currentScreen) {
        Log.d("App", "currentScreen changed to: ${currentScreen.name}")
        when (currentScreen) {
            Screen.Files -> { /* Scroll handling is managed by FilesScreenContent and its LazyListState */ }
            Screen.Filesystem -> { /* Placeholder for Filesystem specific logic if needed. ViewModel handles its own loading. */ }
            Screen.FileDetail -> fileInfoViewModel.setFilterInputVisible(false)
            else -> {
                if (currentScreen != Screen.FileDetail) fileInfoViewModel.clearFileInfoDisplay()
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
            if (currentScreen == Screen.FileDetail) {
                fileInfoViewModel.setPreserveScrollPosition(true)
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
        !showGenericDialog && !fileInfoUiState.initiateDeleteFile && !fileInfoUiState.showEnterFileIdDialog) {
        LaunchedEffect(true, currentScreen, fileInfoUiState.userFilesListErrorMessage) {
            if (fileInfoUiState.userFilesListErrorMessage!!.contains("API Key", ignoreCase = true)) {
                genericDialogTitle = if (currentScreen == Screen.FileDetail) "API Key Required" else "API Key Required for Files"
                genericDialogContent = fileInfoUiState.userFilesListErrorMessage!!
                showGenericDialog = true
            }
        }
    }

    val fabState by remember {
        derivedStateOf {
            when (currentScreen) {
                Screen.Upload -> FabDetails(
                    iconResId = R.drawable.icon_upload,
                    text = "Upload",
                    onClick = { if ((uploadUiState.selectedFileUri != null || uploadUiState.textToUpload.isNotBlank()) && !uploadUiState.isLoading) uploadViewModel.upload() },
                    isExtended = true,
                )
                Screen.Files -> FabDetails(
                    iconResId = R.drawable.icon_add,
                    text = "Filter",
                    onClick = { fileInfoViewModel.toggleFilterInput() },
                    isExtended = true
                )
                Screen.Lists -> FabDetails(
                    iconResId = R.drawable.icon_add,
                    text = "New List",
                    onClick = {
                        genericDialogTitle = "New List"
                        genericDialogContent = "Create new list action (Not Implemented)"
                        showGenericDialog = true
                    },
                    isExtended = true
                )
                Screen.Settings -> FabDetails(
                    iconResId = R.drawable.icon_settings_filled, // Using filled settings as a save icon
                    text = "Save Settings",
                    onClick = {
                        val sharedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                        with(sharedPrefs.edit()) {
                            putString(API_KEY_PREF, apiKeyInput.trim())
                            apply()
                        }
                        uploadViewModel.updateApiKey(apiKeyInput.trim())
                        fileInfoViewModel.loadApiKey()
                        filesystemViewModel.updateApiKey() // Update FilesystemViewModel with new API key
                        genericDialogTitle = "Settings Saved"
                        genericDialogContent = "API Key saved successfully."
                        showGenericDialog = true
                    },
                    isExtended = true
                )
                Screen.FileDetail -> null
                Screen.Filesystem -> null 
            }
        }
    }
    val isFabVisible = fabState != null

    SharedTransitionLayout { 
        Scaffold(
            topBar = {
                Column {
                    TopAppBar(
                        title = {
                            val titleText = if (currentScreen == Screen.FileDetail) {
                                fileInfoUiState.fileInfo?.name ?: Screen.FileDetail.title
                            } else {
                                currentScreen.title
                            }
                            AnimatedContent(
                                targetState = titleText,
                                transitionSpec = {
                                    (fadeIn(animationSpec = tween(220, delayMillis = 90)) +
                                            slideInVertically(initialOffsetY = { it / 2 }, animationSpec = tween(220, delayMillis = 90)))
                                        .togetherWith(fadeOut(animationSpec = tween(90)))
                                },
                                label = "topBarTitleAnimation"
                            ) { currentTitle ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = currentTitle,
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
                            }
                        },
                        navigationIcon = {
                            if (currentScreen == Screen.FileDetail) {
                                IconButton(onClick = {
                                    fileInfoViewModel.setPreserveScrollPosition(true)
                                    currentScreen = Screen.Files
                                }) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to Files")
                                }
                            }
                        },
                        actions = {
                            if (currentScreen != Screen.FileDetail && currentScreen != Screen.Settings) {
                                IconButton(onClick = { currentScreen = Screen.Settings }) {
                                    Icon(painterResource(id = R.drawable.icon_settings_outlined), contentDescription = "Settings")
                                }
                            }
                        }
                    )
                    AnimatedVisibility(
                        visible = currentScreen == Screen.Upload && uploadUiState.isLoading,
                        enter = fadeIn(animationSpec = tween(150)),
                        exit = fadeOut(animationSpec = tween(150))
                    ) {
                        val progressFloat = if (uploadUiState.uploadTotalSizeBytes != null && uploadUiState.uploadTotalSizeBytes!! > 0L) {
                            (uploadUiState.uploadedBytes.toFloat() / uploadUiState.uploadTotalSizeBytes!!).coerceIn(0f, 1f)
                        } else {0f} // Default to 0f if total size is null or 0, or handle as indeterminate
                        if (uploadUiState.uploadTotalSizeBytes != null && uploadUiState.uploadTotalSizeBytes!! > 0L) {
                            LinearProgressIndicator(progress = progressFloat, modifier = Modifier.fillMaxWidth())
                        } else {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth()) // Indeterminate
                        }
                    }
                }
            },
            bottomBar = {
                AnimatedVisibility(visible = currentScreen != Screen.FileDetail) {
                    BottomNavigationBar(currentScreen, navBarOrder) { selectedScreen -> // Pass navBarOrder
                        if (currentScreen != selectedScreen) {
                            currentScreen = selectedScreen
                        }
                    }
                }
            },
            floatingActionButton = {
                fabState?.let { details ->
                    ExtendedFloatingActionButton(
                        onClick = details.onClick,
                        expanded = details.isExtended,
                        icon = {
                            AnimatedContent(
                                targetState = details.iconResId,
                                // contentKey = { it.name }, // contentKey might not be applicable directly for @DrawableRes Int
                                transitionSpec = {
                                    fadeIn(animationSpec = tween(200)) togetherWith fadeOut(animationSpec = tween(200))
                                },
                                label = "fabIconAnimation"
                            ) { targetIconResId ->
                                Icon(painterResource(id = targetIconResId), contentDescription = details.text ?: "FAB icon")
                            }
                        },
                        text = {
                            AnimatedContent(
                                targetState = details.text,
                                transitionSpec = {
                                    fadeIn(animationSpec = tween(200)) togetherWith fadeOut(animationSpec = tween(200))
                                },
                                label = "fabTextAnimation"
                            ) { targetText ->
                                if (targetText != null) {
                                    Text(targetText)
                                }
                            }
                        },
                        modifier = Modifier
                            .onSizeChanged {
                                fabHeightDp = with(localDensity) { it.height.toDp() }
                            }
                            .then(if (details.yOffset != 0.dp) Modifier.offset(y = details.yOffset) else Modifier)
                    )
                }
            },
            snackbarHost = {
                AppSnackbarHost(
                    hostState = snackbarHostState,
                    modifier = Modifier.zIndex(1f)
                )
            }
        ) { paddingValues -> 
            CompositionLocalProvider(LocalOverscrollFactory provides null) {
                AnimatedContent(
                    targetState = currentScreen,
                    transitionSpec = {
                        val initialIndex = navBarOrder.indexOf(initialState)
                        val targetIndex = navBarOrder.indexOf(targetState)

                        if (initialIndex != -1 && targetIndex != -1) {
                            // Both screens are in the main navigation bar
                            if (targetIndex > initialIndex) {
                                (slideInVertically { height -> height } + fadeIn())
                                    .togetherWith(slideOutVertically { height -> -height } + fadeOut())
                            } else {
                                (slideInVertically { height -> -height } + fadeIn())
                                    .togetherWith(slideOutVertically { height -> height } + fadeOut())
                            }
                        } else {
                            // Default transition for screens not in navBarOrder (e.g., FileDetail, Settings)
                            // Or if one of them is not in navBarOrder (should ideally not happen for main nav)
                            if (targetState.ordinal > initialState.ordinal) {
                                (slideInVertically { height -> height } + fadeIn())
                                    .togetherWith(slideOutVertically { height -> -height } + fadeOut())
                            } else {
                                (slideInVertically { height -> -height } + fadeIn())
                                    .togetherWith(slideOutVertically { height -> height } + fadeOut())
                            }
                        }.using(
                            SizeTransform(clip = false)
                        )
                    },
                    label = "screenTransition"
                ) { targetScreen ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues) 
                    ) {
                        when (targetScreen) {
                            Screen.Upload -> UploadScreenContent(
                                uploadViewModel = uploadViewModel,
                                fabHeight = fabHeightDp,
                                isFabVisible = isFabVisible
                            )
                            Screen.Files -> FilesScreenContent(
                                fileInfoViewModel = fileInfoViewModel,
                                onFileSelected = { currentScreen = Screen.FileDetail },
                                listState = filesScreenListState,
                                fabHeight = fabHeightDp,
                                isFabVisible = isFabVisible
                            )
                            Screen.Filesystem -> FilesystemScreen(
                                filesystemViewModel = filesystemViewModel, // Pass the ViewModel
                                fabHeight = fabHeightDp,
                                isFabVisible = isFabVisible
                            )
                            Screen.FileDetail -> {
                                fileInfoUiState.fileInfo?.let { info ->
                                    FileInfoDetailsCard(
                                        fileInfo = info,
                                        fileInfoViewModel = fileInfoViewModel,
                                        context = LocalContext.current,
                                        snackbarHostState = snackbarHostState
                                    )
                                } ?: run {
                                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        if (fileInfoUiState.isLoadingFileInfo) {
                                            CircularProgressIndicator()
                                        } else {
                                            Text("File details not available. Please go back and select a file.")
                                            LaunchedEffect(Unit) {
                                                fileInfoViewModel.setPreserveScrollPosition(true)
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
                                },
                                fabHeight = fabHeightDp,
                                isFabVisible = isFabVisible
                            )
                            Screen.Settings -> SettingsScreenContent(
                                apiKeyInput = apiKeyInput,
                                onApiKeyInputChange = { apiKeyInput = it },
                                onShowDialog = { title, content ->
                                    genericDialogTitle = title
                                    genericDialogContent = content
                                    showGenericDialog = true
                                },
                                fabHeight = fabHeightDp,
                                isFabVisible = isFabVisible,
                                onNavigateBack = { currentScreen = Screen.Files } // Added onNavigateBack
                            )
                        }
                    }
                }
            }
        }

        if (showGenericDialog) {
            AlertDialog(
                onDismissRequest = {
                    showGenericDialog = false
                    if (genericDialogTitle == "Upload Failed" || genericDialogTitle == "Upload Error") uploadViewModel.clearUploadResult()
                    if (genericDialogContent.contains("API Key") && uploadUiState.errorMessage?.contains("API Key") == true) uploadViewModel.clearApiKeyError()
                    if (genericDialogTitle.contains("API Key Required")) {
                        fileInfoViewModel.clearUserFilesError()
                        fileInfoViewModel.clearApiKeyMissingError()
                        // Consider clearing filesystemViewModel error too if relevant
                    }
                },
                title = { Text(genericDialogTitle) },
                text = { Text(genericDialogContent) },
                confirmButton = {
                    Button(onClick = {
                        showGenericDialog = false
                        if (genericDialogTitle == "Upload Failed" || genericDialogTitle == "Upload Error") uploadViewModel.clearUploadResult()
                        if (genericDialogContent.contains("API Key") && uploadUiState.errorMessage?.contains("API Key") == true) uploadViewModel.clearApiKeyError()
                        if (genericDialogTitle.contains("API Key Required")) {
                            fileInfoViewModel.clearUserFilesError()
                            fileInfoViewModel.clearApiKeyMissingError()
                            // Consider clearing filesystemViewModel error too if relevant
                            if (genericDialogContent.contains("Settings")) {
                                if (currentScreen != Screen.Settings) currentScreen = Screen.Settings
                            }
                        }
                    }) { Text("OK") }
                }
            )
        }

        if (fileInfoUiState.initiateDeleteFile) {
            AlertDialog(
                onDismissRequest = { fileInfoViewModel.cancelDeleteFile() },
                title = { Text("Confirm Deletion") },
                text = { Text("Are you sure you want to delete file ID: ${fileInfoUiState.fileIdToDelete}? This action cannot be undone.") },
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

        if (fileInfoUiState.showEnterFileIdDialog) {
            EnterFileIdDialog(fileInfoViewModel = fileInfoViewModel)
        }
    }
}

data class FabDetails(
    @DrawableRes val iconResId: Int,
    val text: String?,
    val onClick: () -> Unit,
    val isExtended: Boolean,
    val yOffset: androidx.compose.ui.unit.Dp = 0.dp 
)

@Composable
fun BottomNavigationBar(currentScreen: Screen, navBarOrder: List<Screen>, onScreenSelected: (Screen) -> Unit) {
    // val navBarOrder = listOf(Screen.Upload, Screen.Files, Screen.Lists, Screen.Filesystem) // Moved up
    NavigationBar {
        navBarOrder.forEach { screen ->
            screen.iconResId?.let { // Ensure iconResId is not null before using
                NavigationBarItem(
                    icon = { Icon(painterResource(id = it), contentDescription = screen.title) },
                    label = { Text(screen.title) },
                    selected = currentScreen == screen,
                    onClick = { onScreenSelected(screen) }
                )
            }
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
