package tools.senko.materialdrain

import android.Manifest
import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.util.UnstableApi
import tools.senko.materialdrain.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds
import androidx.core.content.edit
import tools.senko.materialdrain.files.DownloadStatus
import tools.senko.materialdrain.files.EnterFileIdDialog
import tools.senko.materialdrain.files.FileInfoDetailsCard
import tools.senko.materialdrain.files.FileInfoViewModel
import tools.senko.materialdrain.files.FilesScreenContent
import tools.senko.materialdrain.filesystem.FilesystemScreen
import tools.senko.materialdrain.filesystem.FilesystemViewModel
import tools.senko.materialdrain.lists.ListDetailScreenContent
import tools.senko.materialdrain.lists.ListViewModel
import tools.senko.materialdrain.lists.ListsScreenContent
import tools.senko.materialdrain.preferences.ACCOUNT_CATEGORY_ID
import tools.senko.materialdrain.preferences.AuthViewModel
import tools.senko.materialdrain.preferences.SettingsScreenContent
import tools.senko.materialdrain.preferences.settingsCategory
import tools.senko.materialdrain.settings.SEARCH_INDEX_DELETE_WARNING
import tools.senko.materialdrain.settings.SEARCH_INDEX_FILE_NAME
import tools.senko.materialdrain.ui.LocalBlurredBackdrop
import tools.senko.materialdrain.ui.LocalReduceMotion
import tools.senko.materialdrain.ui.LocalVideoLoop
import tools.senko.materialdrain.ui.VideoLoopSetting
import tools.senko.materialdrain.ui.components.AppSnackbarHost
import tools.senko.materialdrain.ui.components.OdometerText
import tools.senko.materialdrain.ui.components.TransferProgress
import tools.senko.materialdrain.ui.components.TransferStatusBar
import tools.senko.materialdrain.ui.theme.MaterialdrainTheme
import tools.senko.materialdrain.upload.UploadItemStatus
import tools.senko.materialdrain.upload.UploadScreenContent
import tools.senko.materialdrain.upload.UploadViewModel

@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, ExperimentalSharedTransitionApi::class)
@Composable
fun MaterialdrainScreen() {
    var currentScreen by rememberSaveable { mutableStateOf(Screen.Upload) }
    var previousScreen by rememberSaveable { mutableStateOf(Screen.Files) }

    var showGenericDialog by remember { mutableStateOf(false) }
    var genericDialogContent by remember { mutableStateOf("") }
    var genericDialogTitle by remember { mutableStateOf("") }

    val application = LocalContext.current.applicationContext as Application
    val context = LocalContext.current
    val appContainer = remember { AppContainer.get(application) }
    val sessionManager = appContainer.sessionManager
    val viewModelFactory = remember { ViewModelFactory(application, appContainer) }

    // The ViewModels which own uploads/downloads outlive the activity, so transfers continue in the background
    val transferOwner = appContainer.transferViewModelStoreOwner
    val uploadViewModel: UploadViewModel = viewModel(viewModelStoreOwner = transferOwner, factory = viewModelFactory)
    val fileInfoViewModel: FileInfoViewModel = viewModel(viewModelStoreOwner = transferOwner, factory = viewModelFactory)
    val filesystemViewModel: FilesystemViewModel = viewModel(viewModelStoreOwner = transferOwner, factory = viewModelFactory)
    val listViewModel: ListViewModel = viewModel(factory = viewModelFactory)
    val authViewModel: AuthViewModel = viewModel(factory = viewModelFactory)

    val snackbarHostState = remember { SnackbarHostState() }
    val fileInfoUiState by fileInfoViewModel.uiState.collectAsState()
    val uploadUiState by uploadViewModel.uiState.collectAsState()
    val filesystemUiState by filesystemViewModel.uiState.collectAsState()
    val listsUiState by listViewModel.uiState.collectAsState()

    val filesystemUploadLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents(),
        onResult = { uris: List<Uri> -> filesystemViewModel.onFilesPickedForUpload(uris, context) }
    )

    // Ask for the notification permission (Android 13+) the first time a transfer runs. Transfers work without it,
    // the progress notification is just not shown.
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { }
    )
    val activeTransfers by appContainer.transferRegistry.active.collectAsState()
    // Reduced animations: the user's own choice, or the system has animations turned off
    val reduceAnimationsSetting by appContainer.appSettings.reduceAnimations.collectAsState()
    val reduceMotion = reduceAnimationsSetting || appContainer.appSettings.systemAnimationsDisabled()
    val blurredBackdrop by appContainer.appSettings.blurredBackdrop.collectAsState()
    val loopVideos by appContainer.appSettings.loopVideos.collectAsState()
    val videoLoop = remember(loopVideos) { VideoLoopSetting(loopVideos, appContainer.appSettings::setLoopVideos) }
    LaunchedEffect(activeTransfers.isNotEmpty()) {
        if (activeTransfers.isNotEmpty() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            val prefs = context.getSharedPreferences("pixeldrain_prefs", Context.MODE_PRIVATE)
            if (!prefs.getBoolean("asked_notification_permission", false)) {
                prefs.edit { putBoolean("asked_notification_permission", true) }
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    // A login or logout changes the API key which every ViewModel uses
    LaunchedEffect(authViewModel) {
        authViewModel.apiKeyChanged.collect {
            uploadViewModel.updateApiKey(sessionManager.currentApiKey())
            fileInfoViewModel.loadApiKey()
            filesystemViewModel.updateApiKey()
            listViewModel.loadApiKey()
        }
    }
    val coroutineScope = rememberCoroutineScope()
    val localClipboardManager = LocalContext.current.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager


    val filesScreenListState = rememberLazyListState()

    var apiKeyInput by rememberSaveable { mutableStateOf("") }
    // The opened category of the settings (see SettingsCatalog), null shows the list of categories
    var settingsCategoryId by rememberSaveable { mutableStateOf<String?>(null) }
    var fabHeightDp by remember { mutableStateOf(0.dp) }
    val localDensity = LocalDensity.current

    // Define the order of screens in the bottom navigation bar
    val navBarOrder = listOf(
        Screen.Upload,
        Screen.Files,
        Screen.Lists,
        Screen.Filesystem
    )
    var showFileDetailMenu by remember { mutableStateOf(false) }

    val navigateTo = { screen: Screen ->
        if (currentScreen != screen) {
            previousScreen = currentScreen
            currentScreen = screen
        }
    }

    LaunchedEffect(Unit) {
        Log.d("App", "Initial composition. Loading the manually entered API Key.")
        apiKeyInput = sessionManager.manualApiKey()
    }

    if (currentScreen == Screen.FileDetail) {
        BackHandler(enabled = true) {
            fileInfoViewModel.setPreserveScrollPosition(previousScreen == Screen.Files)
            navigateTo(previousScreen)
        }
    }

    val closeListDetail = {
        listViewModel.closeList()
        navigateTo(Screen.Lists)
    }

    LaunchedEffect(currentScreen) {
        Log.d("App", "currentScreen changed to: ${currentScreen.name}")
        // The next visit of the settings starts at the list of categories
        if (currentScreen != Screen.Settings) settingsCategoryId = null
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
                fileInfoViewModel.setPreserveScrollPosition(previousScreen == Screen.Files)
                navigateTo(previousScreen)
            }
            // A deleted filesystem file has to disappear from the directory listing too
            filesystemViewModel.refreshCurrentPath()
        }
    }
    // Results of actions on several files (delete, add to filesystem, create list)
    LaunchedEffect(fileInfoUiState.operationMessage) {
        fileInfoUiState.operationMessage?.let {
            // What was changed may be shown on other screens: the opened list, the lists overview, the filesystem
            if (listsUiState.openedList != null) listViewModel.refreshOpenedList()
            listViewModel.fetchUserLists()
            filesystemViewModel.refreshCurrentPath()
            snackbarHostState.showSnackbar(it, duration = SnackbarDuration.Long)
            fileInfoViewModel.clearOperationMessage()
        }
    }
    LaunchedEffect(fileInfoUiState.operationError) {
        fileInfoUiState.operationError?.let {
            // A partial failure may still have changed something
            if (listsUiState.openedList != null) listViewModel.refreshOpenedList()
            filesystemViewModel.refreshCurrentPath()
            snackbarHostState.showSnackbar(it, duration = SnackbarDuration.Long)
            fileInfoViewModel.clearOperationError()
        }
    }
    LaunchedEffect(filesystemUiState.operationMessage) {
        filesystemUiState.operationMessage?.let {
            snackbarHostState.showSnackbar(it)
            filesystemViewModel.clearOperationMessage()
        }
    }
    LaunchedEffect(filesystemUiState.operationError) {
        filesystemUiState.operationError?.let {
            snackbarHostState.showSnackbar(it, duration = SnackbarDuration.Long)
            filesystemViewModel.clearOperationError()
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

    // Saving the settings is confirmed by the button itself: its icon spins and its text changes for a moment
    var settingsSaveCount by remember { mutableIntStateOf(0) }
    var settingsJustSaved by remember { mutableStateOf(false) }
    LaunchedEffect(settingsSaveCount) {
        if (settingsSaveCount > 0) {
            settingsJustSaved = true
            delay(SETTINGS_SAVED_DISPLAY_MILLIS.milliseconds)
            settingsJustSaved = false
        }
    }

    val fabState by remember {
        derivedStateOf {
            when (currentScreen) {
                // Nothing to upload yet (or already uploading): the upload screen offers its own actions instead
                Screen.Upload -> if (uploadUiState.hasUploadable && !uploadUiState.isLoading) FabDetails(
                    screen = Screen.Upload,
                    iconResId = R.drawable.icon_upload,
                    text = "Upload",
                    onClick = { if (uploadUiState.hasUploadable && !uploadUiState.isLoading) uploadViewModel.upload() },
                    isExtended = true,
                ) else null
                Screen.Files -> null /*FabDetails(
                    screen = Screen.Files,
                    iconResId = R.drawable.icon_add,
                    text = "Filter",
                    onClick = { fileInfoViewModel.toggleFilterInput() },
                    isExtended = true
                )
                */
                Screen.Lists -> null/*FabDetails(
                    screen = Screen.Lists,
                    iconResId = R.drawable.icon_add,
                    text = "New List",
                    onClick = {
                        genericDialogTitle = "New List"
                        genericDialogContent = "Create new list action (Not Implemented)"
                        showGenericDialog = true
                    },
                    isExtended = true
                )
                */
                // Only the categories whose settings are saved with the button (see SettingsCategory.hasSaveButton)
                Screen.Settings -> if (settingsCategory(settingsCategoryId)?.hasSaveButton == true) FabDetails(
                    screen = Screen.Settings,
                    iconResId = R.drawable.icon_settings_filled, // Using filled settings as a save icon
                    text = if (settingsJustSaved) SETTINGS_SAVED_TEXT else SETTINGS_SAVE_TEXT,
                    // Both texts take the same space, so the button doesn't change size when it confirms the save
                    reserveTextWidthFor = listOf(SETTINGS_SAVE_TEXT, SETTINGS_SAVED_TEXT),
                    iconSpinTrigger = settingsSaveCount,
                    onClick = {
                        sessionManager.saveManualApiKey(apiKeyInput)
                        uploadViewModel.updateApiKey(sessionManager.currentApiKey())
                        fileInfoViewModel.loadApiKey()
                        filesystemViewModel.updateApiKey() // Update FilesystemViewModel with new API key
                        listViewModel.loadApiKey()
                        authViewModel.refreshSource()
                        settingsSaveCount++
                    },
                    isExtended = true
                ) else null
                Screen.FileDetail -> null
                Screen.ListDetail -> null
                Screen.Filesystem ->
                    if (filesystemUiState.canWrite) FabDetails(
                        screen = Screen.Filesystem,
                        iconResId = R.drawable.icon_upload,
                        text = "Upload",
                        onClick = { if (filesystemUiState.uploadProgress == null) filesystemUploadLauncher.launch("*/*") },
                        isExtended = true
                    ) else null
            }
        }
    }
    val isFabVisible = fabState != null

    CompositionLocalProvider(
        LocalReduceMotion provides reduceMotion,
        LocalBlurredBackdrop provides blurredBackdrop,
        LocalVideoLoop provides videoLoop
    ) {
    SharedTransitionLayout {
        Scaffold(
            topBar = {
                Column {
                    TopAppBar(
                        title = {
                            val titleText = when (currentScreen) {
                                Screen.FileDetail -> {
                                    fileInfoUiState.fileInfo?.name ?: Screen.FileDetail.title
                                }
                                Screen.ListDetail -> {
                                    listsUiState.openedList?.title ?: Screen.ListDetail.title
                                }
                                Screen.Settings -> {
                                    settingsCategory(settingsCategoryId)?.title ?: Screen.Settings.title
                                }
                                else -> {
                                    currentScreen.title
                                }
                            }
                            AnimatedContent(
                                targetState = titleText,
                                transitionSpec = {
                                    if (reduceMotion) {
                                        fadeIn(animationSpec = tween(100)) togetherWith fadeOut(animationSpec = tween(100))
                                    } else {
                                        (fadeIn(animationSpec = tween(220, delayMillis = 90)) +
                                                slideInVertically(initialOffsetY = { it / 2 }, animationSpec = tween(220, delayMillis = 90)))
                                            .togetherWith(fadeOut(animationSpec = tween(90)))
                                    }
                                },
                                label = "topBarTitleAnimation"
                            ) { currentTitle ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = currentTitle,
                                        modifier = if (currentScreen == Screen.FileDetail) {
                                            Modifier
                                                .weight(1f, fill = false)
                                                .horizontalScroll(rememberScrollState())
                                        } else {
                                            Modifier.weight(1f)
                                        },
                                        maxLines = 1,
                                        overflow = if (currentScreen == Screen.FileDetail) TextOverflow.Clip else TextOverflow.Ellipsis
                                    )
                                }
                            }
                        },
                        navigationIcon = {
                            when (currentScreen) {
                                Screen.FileDetail -> {
                                    IconButton(onClick = {
                                        fileInfoViewModel.setPreserveScrollPosition(previousScreen == Screen.Files)
                                        navigateTo(previousScreen)
                                    }) {
                                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                                    }
                                }
                                Screen.ListDetail -> {
                                    IconButton(onClick = closeListDetail) {
                                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                                    }
                                }
                                Screen.Settings -> {
                                    // Back leaves a category first, then the settings
                                    IconButton(onClick = {
                                        if (settingsCategoryId != null) settingsCategoryId =
                                            null else navigateTo(previousScreen)
                                    }) {
                                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                                    }
                                }

                                else -> {}
                            }
                        },
                        actions = {
                            if (currentScreen == Screen.FileDetail) {
                                fileInfoUiState.fileInfo?.let { currentFile ->
                                    // The "id" of a filesystem file is its path, which starts with a slash
                                    val fileUrl = if (currentFile.id.startsWith("/")) {
                                        "https://pixeldrain.com/d/${currentFile.id.removePrefix("/")}"
                                    } else {
                                        "https://pixeldrain.com/u/${currentFile.id}"
                                    }
                                    IconButton(onClick = { showFileDetailMenu = true }) {
                                        Icon(Icons.Filled.MoreVert, contentDescription = "More options")
                                    }
                                    DropdownMenu(
                                        expanded = showFileDetailMenu,
                                        onDismissRequest = { showFileDetailMenu = false }
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text("Download") },
                                            onClick = {
                                                fileInfoViewModel.initiateDownloadFile(currentFile)
                                                coroutineScope.launch { snackbarHostState.showSnackbar("Download initiated for ${currentFile.name}") }
                                                showFileDetailMenu = false
                                            },
                                            leadingIcon = { Icon(Icons.Filled.Download, contentDescription = "Download", modifier = Modifier.size(28.dp))}
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Share Link") },
                                            onClick = {
                                                val sendIntent: Intent = Intent().apply {
                                                    action = Intent.ACTION_SEND
                                                    putExtra(Intent.EXTRA_TEXT, fileUrl)
                                                    type = "text/plain"
                                                }
                                                context.startActivity(Intent.createChooser(sendIntent, null))
                                                showFileDetailMenu = false
                                            },
                                            leadingIcon = { Icon(Icons.Filled.Share, contentDescription = "Share Link", modifier = Modifier.size(28.dp))}
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Copy Link") },
                                            onClick = {
                                                val clip = ClipData.newPlainText("Pixeldrain URL", fileUrl)
                                                localClipboardManager.setPrimaryClip(clip)
                                                coroutineScope.launch { snackbarHostState.showSnackbar("Link copied to clipboard!") }
                                                showFileDetailMenu = false
                                            },
                                            leadingIcon = { Icon(Icons.Filled.ContentCopy, contentDescription = "Copy Link", modifier = Modifier.size(28.dp))}
                                        )
                                        if (currentFile.canEdit == true) {
                                            HorizontalDivider()
                                            DropdownMenuItem(
                                                text = { Text("Delete File", color = MaterialTheme.colorScheme.error) },
                                                onClick = {
                                                    fileInfoViewModel.initiateDeleteFile(currentFile.id)
                                                    showFileDetailMenu = false
                                                },
                                                leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = "Delete File", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(28.dp))}
                                            )
                                        }
                                    }
                                }
                            } else if (currentScreen != Screen.Settings) { // Show settings icon for other screens not FileDetail or Settings itself
                                IconButton(onClick = { navigateTo(Screen.Settings) }) {
                                    Icon(painterResource(id = R.drawable.icon_settings_outlined), contentDescription = "Settings")
                                }
                            }
                        }
                    )
                    val activeTransfer: TransferProgress? = when (currentScreen) {
                        Screen.Upload if uploadUiState.isLoading -> TransferProgress(
                            transferredBytes = uploadUiState.uploadedBytes,
                            totalBytes = uploadUiState.uploadTotalSizeBytes,
                            bytesPerSecond = uploadUiState.uploadSpeedBytesPerSec,
                            etaSeconds = uploadUiState.uploadEtaSeconds,
                            label = uploadUiState.queuedItems.takeIf { it.isNotEmpty() }?.let { items ->
                                "${items.count { it.status == UploadItemStatus.DONE }} / ${items.size} files"
                            }
                        )
                        Screen.Filesystem if filesystemUiState.uploadProgress != null -> filesystemUiState.uploadProgress!!.let {
                            TransferProgress(
                                transferredBytes = it.uploadedBytes,
                                totalBytes = it.totalBytes,
                                bytesPerSecond = it.bytesPerSecond,
                                etaSeconds = it.etaSeconds,
                                label = "${it.currentIndex} / ${it.totalFiles} files"
                            )
                        }
                        else -> {
                            val isActive = { download: tools.senko.materialdrain.files.FileDownloadState ->
                                download.status == DownloadStatus.DOWNLOADING || download.status == DownloadStatus.PENDING
                            }
                            val activeDownload = when (currentScreen) {
                                Screen.FileDetail -> fileInfoUiState.fileInfo?.id?.let { fileInfoUiState.activeDownloads[it] }?.takeIf(isActive)
                                Screen.Files, Screen.Filesystem, Screen.ListDetail -> fileInfoUiState.activeDownloads.values.firstOrNull(isActive)
                                else -> null
                            }
                            activeDownload?.let {
                                val pending = it.status == DownloadStatus.PENDING
                                TransferProgress(
                                    transferredBytes = it.downloadedBytes,
                                    totalBytes = if (pending) null else it.totalBytes,
                                    bytesPerSecond = it.bytesPerSecond,
                                    etaSeconds = it.etaSeconds,
                                    label = when {
                                        pending -> "Starting download…"
                                        currentScreen != Screen.FileDetail -> it.fileName // downloads can be started from lists, name them
                                        else -> null
                                    }
                                )
                            }
                        }
                    }
                    // Keep the last value around so the bar can fade out instead of collapsing to empty content.
                    var lastTransfer by remember { mutableStateOf<TransferProgress?>(null) }
                    LaunchedEffect(activeTransfer) { if (activeTransfer != null) lastTransfer = activeTransfer }
                    AnimatedVisibility(
                        visible = activeTransfer != null,
                        enter = fadeIn(animationSpec = tween(150)),
                        exit = fadeOut(animationSpec = tween(150))
                    ) {
                        (activeTransfer ?: lastTransfer)?.let { TransferStatusBar(it) }
                    }
                }
            },
            bottomBar = {
                AnimatedVisibility(
                    visible = currentScreen != Screen.FileDetail && currentScreen != Screen.ListDetail,
                    enter = if (reduceMotion) fadeIn(tween(100)) else fadeIn() + expandVertically(),
                    exit = if (reduceMotion) fadeOut(tween(100)) else fadeOut() + shrinkVertically()
                ) {
                    BottomNavigationBar(currentScreen, navBarOrder) { selectedScreen -> // Pass navBarOrder
                        navigateTo(selectedScreen)
                    }
                }
            },
            floatingActionButton = {
                // Keep the last button around so it can animate out instead of vanishing
                var lastFab by remember { mutableStateOf<FabDetails?>(null) }
                LaunchedEffect(fabState) { if (fabState != null) lastFab = fabState }

                // The icon makes a full turn whenever the trigger of the button counts up
                val iconRotation = remember { Animatable(0f) }
                var handledSpinTrigger by remember { mutableIntStateOf(0) }
                val spinTrigger = (fabState ?: lastFab)?.iconSpinTrigger ?: 0
                LaunchedEffect(spinTrigger) {
                    if (spinTrigger > handledSpinTrigger) {
                        handledSpinTrigger = spinTrigger
                        // Reduced animations: no spinning
                        if (!reduceMotion) {
                            iconRotation.snapTo(0f)
                            iconRotation.animateTo(360f, tween(durationMillis = 700, easing = FastOutSlowInEasing))
                        }
                    }
                }

                AnimatedVisibility(
                    visible = fabState != null,
                    enter = if (reduceMotion) fadeIn(tween(100)) else scaleIn(animationSpec = tween(200)) + fadeIn(animationSpec = tween(200)),
                    exit = if (reduceMotion) fadeOut(tween(100)) else scaleOut(animationSpec = tween(150)) + fadeOut(animationSpec = tween(150))
                ) {
                (fabState ?: lastFab)?.let { details ->
                    ExtendedFloatingActionButton(
                        onClick = details.onClick,
                        expanded = details.isExtended,
                        icon = {
                            AnimatedContent(
                                targetState = details.iconResId,
                                transitionSpec = {
                                    fadeIn(animationSpec = tween(200)) togetherWith fadeOut(animationSpec = tween(200))
                                },
                                label = "fabIconAnimation"
                            ) { targetIconResId ->
                                Icon(
                                    painterResource(id = targetIconResId),
                                    contentDescription = details.text ?: "FAB icon",
                                    modifier = Modifier.rotate(iconRotation.value)
                                )
                            }
                        },
                        text = {
                            // Moving to the button of another screen crossfades and animates the size of the button,
                            // as it always did. Only when the button of the same screen changes its text the
                            // characters cycle, and then the size stays as it is.
                            AnimatedContent(
                                targetState = details,
                                contentKey = { it.screen },
                                transitionSpec = {
                                    val crossfade = fadeIn(animationSpec = tween(200)) togetherWith fadeOut(animationSpec = tween(200))
                                    // Reduced animations: the button takes its new size at once
                                    if (reduceMotion) crossfade.using(SizeTransform(clip = false) { _, _ -> snap() }) else crossfade
                                },
                                label = "fabTextAnimation"
                            ) { fab ->
                                fab.text?.let { fabText ->
                                    OdometerText(
                                        text = fabText,
                                        fixedWidth = true,
                                        widthReferenceTexts = fab.reserveTextWidthFor
                                    )
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

                        val sliding = if (initialIndex != -1 && targetIndex != -1) {
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

                        if (reduceMotion) {
                            // Reduced animations: a quick crossfade instead of sliding
                            (fadeIn(tween(100)) togetherWith fadeOut(tween(100)))
                                .using(SizeTransform(clip = false) { _, _ -> snap() })
                        } else {
                            sliding
                        }
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
                                filesystemViewModel = filesystemViewModel,
                                fileInfoViewModel = fileInfoViewModel,
                                onFileSelected = { navigateTo(Screen.FileDetail) },
                                listState = filesScreenListState,
                                fabHeight = fabHeightDp,
                                isFabVisible = isFabVisible
                            )
                            Screen.Filesystem -> FilesystemScreen(
                                filesystemViewModel = filesystemViewModel,
                                fileInfoViewModel = fileInfoViewModel,
                                onFileSelected = { navigateTo(Screen.FileDetail) },
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
                                            val errorMessage = fileInfoUiState.fileInfoErrorMessage ?: "File details not available."
                                            Text(
                                                text = "$errorMessage Please go back and select a different file.",
                                                modifier = Modifier.padding(16.dp),
                                                textAlign = TextAlign.Center
                                            )
                                        }
                                    }
                                }
                            }
                            Screen.Lists -> ListsScreenContent(
                                listViewModel = listViewModel,
                                onListSelected = { navigateTo(Screen.ListDetail) },
                                fabHeight = fabHeightDp,
                                isFabVisible = isFabVisible
                            )
                            Screen.ListDetail -> ListDetailScreenContent(
                                filesystemViewModel = filesystemViewModel,
                                listViewModel = listViewModel,
                                fileInfoViewModel = fileInfoViewModel,
                                onFileSelected = { navigateTo(Screen.FileDetail) },
                                onBack = closeListDetail,
                                fabHeight = fabHeightDp,
                                isFabVisible = isFabVisible
                            )
                            Screen.Settings -> SettingsScreenContent(
                                categoryId = settingsCategoryId,
                                onCategoryChange = { settingsCategoryId = it },
                                appSettings = appContainer.appSettings,
                                apiKeyInput = apiKeyInput,
                                onApiKeyInputChange = { apiKeyInput = it },
                                authViewModel = authViewModel,
                                fabHeight = fabHeightDp,
                                isFabVisible = isFabVisible,
                                onNavigateBack = { navigateTo(previousScreen) }
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
                                // The place to fix a missing API key is the account
                                settingsCategoryId = ACCOUNT_CATEGORY_ID
                                navigateTo(Screen.Settings)
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
                text = {
                    val isSearchIndex = fileInfoUiState.fileIdToDelete?.endsWith("/$SEARCH_INDEX_FILE_NAME") == true
                    Text(
                        if (isSearchIndex) SEARCH_INDEX_DELETE_WARNING
                        else "Are you sure you want to delete file ID: ${fileInfoUiState.fileIdToDelete}? This action cannot be undone."
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

        if (fileInfoUiState.showEnterFileIdDialog) {
            EnterFileIdDialog(fileInfoViewModel = fileInfoViewModel)
        }
    }
    }
}


private const val SETTINGS_SAVE_TEXT = "Save Settings"
private const val SETTINGS_SAVED_TEXT = "Settings saved"
private const val SETTINGS_SAVED_DISPLAY_MILLIS = 2000L


@Preview(showBackground = true)
@Composable
fun DefaultPreviewMaterialdrainScreen() {
    MaterialdrainTheme {
        MaterialdrainScreen()
    }
}
