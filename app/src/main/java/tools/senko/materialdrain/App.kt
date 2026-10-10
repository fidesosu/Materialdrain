package tools.senko.materialdrain

import android.Manifest
import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
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
import androidx.compose.animation.togetherWith
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.offset
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.media3.common.util.UnstableApi
import tools.senko.materialdrain.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds
import androidx.core.content.edit
import tools.senko.materialdrain.files.DownloadStatus
import tools.senko.materialdrain.navmenu.NavFabPosition
import tools.senko.materialdrain.files.SelectionBarHeight
import tools.senko.materialdrain.files.openDownloadedFile
import tools.senko.materialdrain.hosts.HostSwitcher
import tools.senko.materialdrain.hosts.hostOptions
import tools.senko.materialdrain.files.FileInfoDetailsCard
import tools.senko.materialdrain.files.FileInfoViewModel
import tools.senko.materialdrain.browser.BrowserMode
import tools.senko.materialdrain.provider.PIXELDRAIN_PROVIDER_ID
import tools.senko.materialdrain.provider.api.ProviderCapability
import tools.senko.materialdrain.provider.api.resolveScreens
import tools.senko.materialdrain.browser.BrowserScreen
import tools.senko.materialdrain.filesystem.FilesystemViewModel
import tools.senko.materialdrain.lists.ListViewModel
import tools.senko.materialdrain.navmenu.NavFabMenu
import tools.senko.materialdrain.navmenu.menu
import tools.senko.materialdrain.preferences.ACCOUNT_CATEGORY_ID
import tools.senko.materialdrain.preferences.HOSTS_CATEGORY_ID
import tools.senko.materialdrain.preferences.AuthViewModel
import tools.senko.materialdrain.preferences.ProviderSettingsViewModel
import tools.senko.materialdrain.preferences.SettingsScreenContent
import tools.senko.materialdrain.preferences.settingsCategory
import tools.senko.materialdrain.settings.SEARCH_INDEX_DELETE_WARNING
import tools.senko.materialdrain.settings.isSearchIndex
import tools.senko.materialdrain.files.key
import tools.senko.materialdrain.ui.LocalBlurredBackdrop
import tools.senko.materialdrain.ui.LocalBottomInset
import tools.senko.materialdrain.ui.LocalTextWrap
import tools.senko.materialdrain.ui.LocalReduceMotion
import tools.senko.materialdrain.ui.LocalVideoLoop
import tools.senko.materialdrain.ui.VideoLoopSetting
import tools.senko.materialdrain.ui.components.AppMenu
import tools.senko.materialdrain.ui.components.pageTransition
import tools.senko.materialdrain.ui.components.AppMenuDivider
import tools.senko.materialdrain.ui.components.AppMenuItem
import tools.senko.materialdrain.ui.components.AppSnackbarHost
import tools.senko.materialdrain.ui.components.CenteredTextMessage
import tools.senko.materialdrain.ui.components.snackbarMotion
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
    var previousScreen by rememberSaveable { mutableStateOf(Screen.Files) }

    var showGenericDialog by remember { mutableStateOf(false) }
    var genericDialogContent by remember { mutableStateOf("") }
    var genericDialogTitle by remember { mutableStateOf("") }

    val application = LocalContext.current.applicationContext as Application
    val context = LocalContext.current
    val appContainer = remember { AppContainer.get(application) }
    // The app opens on the screen it was last on
    var currentScreen by rememberSaveable { mutableStateOf(appContainer.appSettings.lastScreen) }
    val sessionManager = appContainer.sessionManager
    val viewModelFactory = remember { ViewModelFactory(application, appContainer) }

    // The ViewModels which own uploads/downloads outlive the activity, so transfers continue in the background
    val transferOwner = appContainer.transferViewModelStoreOwner
    val uploadViewModel: UploadViewModel = viewModel(viewModelStoreOwner = transferOwner, factory = viewModelFactory)
    val fileInfoViewModel: FileInfoViewModel = viewModel(viewModelStoreOwner = transferOwner, factory = viewModelFactory)
    val filesystemViewModel: FilesystemViewModel = viewModel(viewModelStoreOwner = transferOwner, factory = viewModelFactory)
    val listViewModel: ListViewModel = viewModel(factory = viewModelFactory)
    val authViewModel: AuthViewModel = viewModel(factory = viewModelFactory)
    val providerSettingsViewModel: ProviderSettingsViewModel = viewModel(factory = viewModelFactory)

    val snackbarHostState = remember { SnackbarHostState() }
    val fileInfoUiState by fileInfoViewModel.uiState.collectAsState()
    val uploadUiState by uploadViewModel.uiState.collectAsState()
    val filesystemUiState by filesystemViewModel.uiState.collectAsState()
    val listsUiState by listViewModel.uiState.collectAsState()
    // Values which the effects and conditions of this screen use. A derived state recomposes this screen only when its own
    // value changes, so a progress tick of a transfer doesn't recompose the whole screen (see TransferBarSlot)
    val uploadErrorMessage by derivedFrom { uploadUiState.errorMessage }
    val fileInfoDeleteSuccess by derivedFrom { fileInfoUiState.deleteFileSuccessMessage }
    val fileInfoDeleteError by derivedFrom { fileInfoUiState.deleteFileErrorMessage }
    val fileInfoOperationMessage by derivedFrom { fileInfoUiState.operationMessage }
    val fileInfoOperationError by derivedFrom { fileInfoUiState.operationError }
    val fileDownloadSuccess by derivedFrom { fileInfoUiState.fileDownloadSuccessMessage }
    val fileDownloadError by derivedFrom { fileInfoUiState.fileDownloadErrorMessage }
    val fileInfoApiKeyMissing by derivedFrom { fileInfoUiState.apiKeyMissingError }
    val userFilesListError by derivedFrom { fileInfoUiState.userFilesListErrorMessage }
    val fileInfoInitiateDelete by derivedFrom { fileInfoUiState.initiateDeleteFile }
    val filesystemOperationMessage by derivedFrom { filesystemUiState.operationMessage }
    val filesystemOperationError by derivedFrom { filesystemUiState.operationError }

    // Ask for the notification permission (Android 13+) the first time a transfer runs. Transfers work without it,
    // the progress notification is just not shown.
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { }
    )
    // Only whether any transfer runs matters here: a progress tick must not recompose the whole screen
    val hasActiveTransfers by remember {
        appContainer.transferRegistry.active.map { it.isNotEmpty() }.distinctUntilChanged()
    }.collectAsState(initial = false)
    // Reduced animations: the user's own choice, or the system has animations turned off
    val reduceAnimationsSetting by appContainer.appSettings.reduceAnimations.collectAsState()
    val reduceMotion = reduceAnimationsSetting || appContainer.appSettings.systemAnimationsDisabled()
    val blurredBackdrop by appContainer.appSettings.blurredBackdrop.collectAsState()
    val textWrap by appContainer.appSettings.textWrap.collectAsState()
    val loopVideos by appContainer.appSettings.loopVideos.collectAsState()
    val navPrototype by appContainer.appSettings.navPrototype.collectAsState()
    val navMenuPreview by appContainer.appSettings.navMenuPreview.collectAsState()
    val navFabPosition by appContainer.appSettings.navFabPosition.collectAsState()
    val videoLoop = remember(loopVideos) { VideoLoopSetting(loopVideos, appContainer.appSettings::setLoopVideos) }
    LaunchedEffect(hasActiveTransfers) {
        if (hasActiveTransfers && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
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



    var apiKeyInput by rememberSaveable { mutableStateOf("") }
    // The opened category of the settings (see SettingsCatalog), null shows the list of categories
    var settingsCategoryId by rememberSaveable { mutableStateOf<String?>(null) }
    var fabHeightDp by remember { mutableStateOf(0.dp) }
    val localDensity = LocalDensity.current

    // The tabs follow what the active host offers: its config decides which of Files, Lists and Filesystem exist
    val configChanges by appContainer.providerConfigStore.changes.collectAsState()
    val activeHostId by appContainer.providerConfigStore.activeProviderId.collectAsState()
    val loggedInUser by sessionManager.loggedInUser.collectAsState()
    val hostChecks by appContainer.hostHealth.states.collectAsState()
    // The screens which show the files of the active host, and so the host switcher in the top bar
    val browseScreens = setOf(Screen.Upload, Screen.Files, Screen.Lists, Screen.Filesystem)
    // Every host is checked ahead of time, so the switcher knows which ones answer before it's opened: every few minutes
    // while the app is in front (HostHealth also checks them when the network changes), and a host that was added or
    // whose address changed right away
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                appContainer.hostHealth.checkAll()
                delay(HOST_RECHECK_MILLIS)
            }
        }
    }
    LaunchedEffect(configChanges) { appContainer.hostHealth.checkAll() }
    val activeCapabilities = remember(configChanges) {
        appContainer.providerRegistry.resolve(appContainer.providerConfigStore.activeProviderId.value).capabilities
    }
    // The built-in Pixeldrain host isn't config-driven, so it keeps today's capability-based navigation. Every other
    // host is a config, whose "screens" field picks what shows for it (or, left out, everything the host can back)
    val activeConfig = remember(configChanges) {
        if (activeHostId == PIXELDRAIN_PROVIDER_ID) {
            null
        } else {
            appContainer.providerConfigStore.providers.value.firstOrNull { it.id == activeHostId }?.config
        }
    }
    // The config's screens in its own order, so a config can put them in whichever order makes sense for that host
    // (the tabs follow it), without those the host can't back (see resolveScreens)
    val configScreens = remember(activeCapabilities, activeConfig) {
        activeConfig?.let { resolveScreens(it.screens, activeCapabilities) }
    }
    val navBarOrder = remember(activeCapabilities, configScreens) {
        configScreens?.map { it.screen.toScreen() } ?: buildList {
            add(Screen.Upload)
            if (ProviderCapability.ENUMERATE in activeCapabilities) add(Screen.Files)
            if (ProviderCapability.LISTS in activeCapabilities) add(Screen.Lists)
            if (ProviderCapability.BROWSE in activeCapabilities) add(Screen.Filesystem)
        }
    }
    // A config with no screens to show (it chose none, or only ones the host can't back): nothing to fall back to, so
    // the current screen shows a message instead (see the main content below)
    val noScreensConfigured = activeConfig != null && navBarOrder.isEmpty()
    // This screen's entry in the config, for its custom name and the capabilities it takes away (see ScreenConfig).
    // Not config-driven (the built-in Pixeldrain) means nothing is taken away and the screen keeps its default name
    val activeScreenConfigs = remember(configScreens) {
        configScreens?.associateBy { it.screen.toScreen() } ?: emptyMap()
    }
    fun screenTitle(screen: Screen): String = activeScreenConfigs[screen]?.name?.takeIf { it.isNotBlank() } ?: screen.title
    LaunchedEffect(navBarOrder, currentScreen) {
        // A tab the new host doesn't offer falls back to the first one it does (e.g. Upload isn't reachable on a
        // host which only turned on Filesystem, even if Upload was the screen open before switching to it). Also
        // checked when the screen changes: a host added or switched to from Settings only meets its tabs when Settings
        // goes back to the screen open before it, and with a single tab there's no bar to leave a missing one by
        if (currentScreen in browseScreens &&
            currentScreen !in navBarOrder && navBarOrder.isNotEmpty()
        ) {
            currentScreen = navBarOrder.first()
        }
    }
    var showFileDetailMenu by remember { mutableStateOf(false) }
    // While items are selected the bar is at the bottom, so the FAB and the snackbars move up over it
    var selectingItems by remember { mutableStateOf(false) }
    // The height of the snackbar while one is shown (0 when none is); the FAB rises above it
    var snackbarHeightDp by remember { mutableStateOf(0.dp) }
    // The scroll position of each list survives visits to other screens, so it's kept here rather than in the screen
    val filesListState = rememberLazyListState()
    val listsListState = rememberLazyListState()
    val listContentsListState = rememberLazyListState()
    val filesystemListState = rememberLazyListState()

    val navigateTo = { screen: Screen ->
        if (currentScreen != screen) {
            previousScreen = currentScreen
            currentScreen = screen
        }
    }
    // Remembered so the app opens where it was closed
    LaunchedEffect(currentScreen) { appContainer.appSettings.lastScreen = currentScreen }

    // The settings of one host: the account for the built-in Pixeldrain, its card in the custom hosts for the others.
    // Without a host, the custom hosts themselves
    val openHostSettings: (String?) -> Unit = { id ->
        if (id == PIXELDRAIN_PROVIDER_ID) {
            settingsCategoryId = ACCOUNT_CATEGORY_ID
        } else {
            id?.let { providerSettingsViewModel.focusHost(it) }
            settingsCategoryId = HOSTS_CATEGORY_ID
        }
        navigateTo(Screen.Settings)
    }

    // The search modal of the browse screens (see BrowserScreen), opened by the magnifier at the top left. Only the
    // screens with a list of files to search have one: the lists overview is just titles
    var showSearchModal by rememberSaveable { mutableStateOf(false) }
    val searchAvailable = currentScreen == Screen.Files || currentScreen == Screen.Filesystem ||
        (currentScreen == Screen.Lists && listsUiState.openedList != null)
    LaunchedEffect(searchAvailable) { if (!searchAvailable) showSearchModal = false }
    // Whether the navigation prototype's window is open (see NavFabMenu)
    var navMenuOpen by remember { mutableStateOf(false) }
    // The screen behind the search modal is blurred (Android 12 and up, see SearchModal), easing in and out
    val searchBlur by animateDpAsState(
        targetValue = if (showSearchModal && Build.VERSION.SDK_INT >= 31) SEARCH_BLUR_RADIUS else 0.dp,
        animationSpec = tween(if (reduceMotion) 100 else 250),
        label = "searchBlur"
    )
    // And behind the navigation window: only the screen under it, as the window is drawn in the same window as the screen
    val navMenuBlur by animateDpAsState(
        targetValue = if (navMenuOpen && navPrototype && Build.VERSION.SDK_INT >= 31) SEARCH_BLUR_RADIUS else 0.dp,
        animationSpec = tween(if (reduceMotion) 100 else 250),
        label = "navMenuBlur"
    )
    // The details page reopens the file it showed; when that file can't be found, the Files screen is shown instead
    LaunchedEffect(Unit) {
        if (currentScreen == Screen.FileDetail && !fileInfoViewModel.restoreOpenedFile()) navigateTo(Screen.Files)
    }

    // Files shared from another app go to the upload screen, which queues them like picked files
    val pendingShares by appContainer.pendingShares.collectAsState()
    val appLocked by appContainer.appLock.locked.collectAsState()
    // A share waits for the unlock: nothing uploads while the app is locked
    LaunchedEffect(pendingShares, appLocked) {
        if (pendingShares.isNotEmpty() && !appLocked) {
            navigateTo(Screen.Upload)
            uploadViewModel.onFilesSelected(pendingShares, context)
            appContainer.pendingShares.value = emptyList()
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

    LaunchedEffect(currentScreen) {
        Log.d("App", "currentScreen changed to: ${currentScreen.name}")
        // The next visit of the settings starts at the list of categories
        if (currentScreen != Screen.Settings) settingsCategoryId = null
        when (currentScreen) {
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
                    genericDialogContent = "Error: ${it.message ?: "Unknown error"}"
                    showGenericDialog = true
                }
            }
            uiState.errorMessage?.let {
                if (!it.contains("API Key", ignoreCase = true) &&
                    !it.contains("preview", ignoreCase = true) &&
                    !it.contains("metadata", ignoreCase = true) &&
                    !showGenericDialog &&
                    currentScreen == Screen.Upload) {
                    genericDialogTitle = "Upload Error"
                    genericDialogContent = it
                    showGenericDialog = true
                }
            }
        }
    }

    if (uploadErrorMessage?.contains("API Key is missing") == true && currentScreen == Screen.Upload) {
        LaunchedEffect(uploadErrorMessage, currentScreen) {
            genericDialogTitle = "API Key Required for Upload"
            genericDialogContent = "Please set your API Key in the Settings screen to upload files."
            showGenericDialog = true
        }
    }

    LaunchedEffect(fileInfoDeleteSuccess) {
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
    LaunchedEffect(fileInfoOperationMessage) {
        fileInfoUiState.operationMessage?.let {
            // What was changed may be shown on other screens: the opened list, the lists overview, the filesystem
            if (listsUiState.openedList != null) listViewModel.refreshOpenedList()
            listViewModel.fetchUserLists()
            filesystemViewModel.refreshCurrentPath()
            snackbarHostState.showSnackbar(it, duration = SnackbarDuration.Long)
            fileInfoViewModel.clearOperationMessage()
        }
    }
    LaunchedEffect(fileInfoOperationError) {
        fileInfoUiState.operationError?.let {
            // A partial failure may still have changed something
            if (listsUiState.openedList != null) listViewModel.refreshOpenedList()
            filesystemViewModel.refreshCurrentPath()
            snackbarHostState.showSnackbar(it, duration = SnackbarDuration.Long)
            fileInfoViewModel.clearOperationError()
        }
    }
    LaunchedEffect(filesystemOperationMessage) {
        filesystemUiState.operationMessage?.let {
            snackbarHostState.showSnackbar(it)
            filesystemViewModel.clearOperationMessage()
        }
    }
    LaunchedEffect(filesystemOperationError) {
        filesystemUiState.operationError?.let {
            snackbarHostState.showSnackbar(it, duration = SnackbarDuration.Long)
            filesystemViewModel.clearOperationError()
        }
    }
    LaunchedEffect(fileInfoDeleteError) {
        fileInfoUiState.deleteFileErrorMessage?.let {
            snackbarHostState.showSnackbar("Delete failed: $it", duration = SnackbarDuration.Long)
            fileInfoViewModel.clearDeleteMessages()
        }
    }

    LaunchedEffect(fileDownloadSuccess) {
        fileInfoUiState.fileDownloadSuccessMessage?.let {
            val openable = fileInfoUiState.fileDownloadOpen
            val result = snackbarHostState.showSnackbar(it, actionLabel = openable?.let { "Open" }, duration = SnackbarDuration.Long)
            if (result == SnackbarResult.ActionPerformed && openable != null) openDownloadedFile(context, openable)
            fileInfoViewModel.clearDownloadMessages()
        }
    }
    LaunchedEffect(fileDownloadError) {
        fileInfoUiState.fileDownloadErrorMessage?.let {
            snackbarHostState.showSnackbar("Download failed: $it", duration = SnackbarDuration.Long)
            fileInfoViewModel.clearDownloadMessages()
        }
    }

    if (fileInfoApiKeyMissing && (currentScreen == Screen.Files || currentScreen == Screen.FileDetail) &&
        !userFilesListError.isNullOrBlank() &&
        !showGenericDialog && !fileInfoInitiateDelete) {
        LaunchedEffect(true, currentScreen, userFilesListError) {
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
            // The navigation prototype takes the FAB's place (and its bottom padding), see NavFabMenu
            // The navigation prototype has its own button in the corner; the upload screen keeps its upload button
            if (navPrototype) null else when (currentScreen) {
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
                // Upload is an icon in the Filesystem screen's own header now (see BrowserScreen), so it works the
                // same whichever navigation style is on, instead of only existing as this FAB
                Screen.Filesystem -> null
            }
        }
    }
    // Like the old bottom bar, the navigation FAB stays out of the way on detail screens
    // With one screen (or none) turned on there's nothing to navigate between, so the bar/FAB would only take up space
    val showNavFab = navPrototype && currentScreen != Screen.FileDetail && navBarOrder.size > 1
    // The snackbar shares the row of the navigation button at an edge, so the button doesn't rise over it. Where no button
    // sits beside it (hidden on detail screens, or in the middle) it takes the full width, and in the middle the button rises over it
    val snackbarBesideFab = navPrototype && fabState == null && showNavFab && navFabPosition != NavFabPosition.CENTER
    val fabLiftTarget = (if (selectingItems) SelectionBarHeight else 0.dp) +
        (if (!snackbarBesideFab && snackbarHeightDp > 0.dp) snackbarHeightDp + 8.dp else 0.dp)
    // The buttons lift with the snackbar, on the same motion as its size and the gap beside the button
    val fabLift by animateDpAsState(targetValue = fabLiftTarget, animationSpec = snackbarMotion(reduceMotion), label = "fabLift")
    // The navigation button sits in the same corner: it rises over the upload button when both are shown
    val navLiftTarget = fabLiftTarget + (if (navPrototype && fabState != null) fabHeightDp + 16.dp else 0.dp)
    val navLift by animateDpAsState(targetValue = navLiftTarget, animationSpec = snackbarMotion(reduceMotion), label = "navLift")
    // How far below its corner a button starts before it slides up (see NavFabMenu)
    val entryOffsetPx = with(localDensity) { 160.dp.toPx() }
    val isFabVisible = showNavFab || fabState != null

    CompositionLocalProvider(
        LocalReduceMotion provides reduceMotion,
        LocalBlurredBackdrop provides blurredBackdrop,
        LocalTextWrap provides textWrap,
        LocalVideoLoop provides videoLoop
    ) {
    SharedTransitionLayout {
        // The Box lets the FAB navigation prototype (Developer settings) sit on top of the Scaffold. Blurred as a
        // whole while the search modal is open, which is its own window and so stays sharp
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(if (searchBlur > 0.dp) Modifier.blur(searchBlur) else Modifier)
        ) {
        Scaffold(
            // Blurred behind the navigation window, which sits on top of it in this Box and so stays sharp
            modifier = if (navMenuBlur > 0.dp) Modifier.blur(navMenuBlur) else Modifier,
            topBar = {
                Column {
                    // The switcher is centred on the bar itself, so the icons at the edges don't move it
                    Box(modifier = Modifier.fillMaxWidth()) {
                    CenterAlignedTopAppBar(
                        title = {
                            val titleText = when (currentScreen) {
                                // The name is under the preview (see FileInfoDetailsCard), not up here as well
                                Screen.FileDetail -> ""
                                Screen.Settings -> {
                                    settingsCategory(settingsCategoryId)?.title ?: Screen.Settings.title
                                }
                                else -> {
                                    screenTitle(currentScreen)
                                }
                            }
                            // On the browse screens the host switcher is drawn over the bar instead (see below)
                            if (currentScreen in browseScreens) Unit else AnimatedContent(
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
                                Screen.Settings -> {
                                    // Back leaves a category first, then the settings
                                    IconButton(onClick = {
                                        if (settingsCategoryId != null) settingsCategoryId =
                                            null else navigateTo(previousScreen)
                                    }) {
                                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                                    }
                                }

                                // Opposite the settings icon
                                else -> if (searchAvailable) {
                                    IconButton(onClick = { showSearchModal = true }) {
                                        Icon(Icons.Filled.Search, contentDescription = "Search")
                                    }
                                }
                            }
                        },
                        actions = {
                            if (currentScreen == Screen.FileDetail) {
                                fileInfoUiState.fileInfo?.let { currentFile ->
                                    val fileUrl = fileInfoViewModel.shareUrlFor(currentFile)
                                    Box {
                                        IconButton(onClick = { showFileDetailMenu = true }) {
                                            Icon(Icons.Filled.MoreVert, contentDescription = "More options")
                                        }
                                        AppMenu(expanded = showFileDetailMenu, onDismiss = { showFileDetailMenu = false }) {
                                            AppMenuItem(
                                                text = "Download",
                                                leadingIcon = Icons.Filled.Download,
                                                onClick = {
                                                    fileInfoViewModel.initiateDownloadFile(currentFile)
                                                    coroutineScope.launch { snackbarHostState.showSnackbar("Download initiated for ${currentFile.name}") }
                                                    showFileDetailMenu = false
                                                }
                                            )
                                            if (fileUrl != null) {
                                                AppMenuItem(
                                                    text = "Share link",
                                                    leadingIcon = Icons.Filled.Share,
                                                    onClick = {
                                                        val sendIntent: Intent = Intent().apply {
                                                            action = Intent.ACTION_SEND
                                                            putExtra(Intent.EXTRA_TEXT, fileUrl)
                                                            type = "text/plain"
                                                        }
                                                        context.startActivity(Intent.createChooser(sendIntent, null))
                                                        showFileDetailMenu = false
                                                    }
                                                )
                                                AppMenuItem(
                                                    text = "Copy link",
                                                    leadingIcon = Icons.Filled.ContentCopy,
                                                    onClick = {
                                                        val clip = ClipData.newPlainText("File link", fileUrl)
                                                        localClipboardManager.setPrimaryClip(clip)
                                                        coroutineScope.launch { snackbarHostState.showSnackbar("Link copied to clipboard!") }
                                                        showFileDetailMenu = false
                                                    }
                                                )
                                            }
                                            if (fileInfoViewModel.canDeleteFiles()) {
                                                AppMenuDivider()
                                                AppMenuItem(
                                                    text = "Delete file",
                                                    leadingIcon = Icons.Filled.Delete,
                                                    destructive = true,
                                                    onClick = {
                                                        fileInfoViewModel.initiateDeleteFile(currentFile)
                                                        showFileDetailMenu = false
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                            } else if (currentScreen != Screen.Settings) { // Show settings icon for other screens not FileDetail or Settings itself
                                // Refreshing is pulling down the list (see BrowserScreen)
                                IconButton(onClick = { navigateTo(Screen.Settings) }) {
                                    Icon(painterResource(id = R.drawable.icon_settings_outlined), contentDescription = "Settings")
                                }
                            }
                        }
                    )
                    if (currentScreen in browseScreens) {
                        // Centred on the band the bar's icons sit in (below the status bar), not on the whole bar
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .windowInsetsPadding(TopAppBarDefaults.windowInsets)
                                .fillMaxWidth()
                                .height(64.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            HostSwitcher(
                                hosts = hostOptions(appContainer.providerConfigStore, sessionManager, loggedInUser),
                                activeId = activeHostId,
                                checks = hostChecks,
                                onSelect = { appContainer.providerConfigStore.setActive(it) },
                                onOpenHostSettings = { openHostSettings(it) },
                                onManage = { openHostSettings(null) },
                                // Only hosts not checked in the last half minute: the list is normally known already
                                onOpened = { appContainer.hostHealth.checkAll() },
                                onRefresh = { appContainer.hostHealth.checkAll(force = true) }
                            )
                        }
                    }
                    }
                    TransferBarSlot(currentScreen, uploadViewModel, filesystemViewModel, fileInfoViewModel)
                }
            },
            bottomBar = {
                AnimatedVisibility(
                    // The FAB navigation prototype (Developer settings) replaces the bar while it's on; with no
                    // screens to show there is nothing for the bar to hold either
                    visible = !navPrototype && currentScreen != Screen.FileDetail && navBarOrder.size > 1,
                    // Above the upload button, so the button slides down behind the bar
                    modifier = Modifier.zIndex(2f),
                    enter = if (reduceMotion) fadeIn(tween(100)) else fadeIn() + expandVertically(),
                    exit = if (reduceMotion) fadeOut(tween(100)) else fadeOut() + shrinkVertically()
                ) {
                    BottomNavigationBar(currentScreen, navBarOrder, labelFor = ::screenTitle) { selectedScreen ->
                        navigateTo(selectedScreen)
                    }
                }
            },
            floatingActionButton = {
                // Moved visually (an offset, not padding), so the FAB rises over the selection bar and a snackbar
                Box(modifier = Modifier.offset(y = -fabLift)) {
                // Keep the last button around so it can animate out instead of vanishing
                var lastFab by remember { mutableStateOf<FabDetails?>(null) }
                LaunchedEffect(fabState) { if (fabState != null) lastFab = fabState }

                // 0 = shown, 1 = gone. Animated by its own state, so leaving slides it down instead of removing it at once
                val fabHidden by animateFloatAsState(
                    targetValue = if (fabState != null) 0f else 1f,
                    animationSpec = tween(if (reduceMotion) 100 else 320, easing = FastOutSlowInEasing),
                    label = "fabHidden"
                )

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

                Box(modifier = Modifier.graphicsLayer {
                    translationY = fabHidden * entryOffsetPx
                    alpha = 1f - fabHidden
                }) {
                if (fabHidden < 1f) (fabState ?: lastFab)?.let { details ->
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
                }
            },
            snackbarHost = {
                // The snackbar stays at the bottom; while selecting it sits above the selection bar
                AppSnackbarHost(
                    hostState = snackbarHostState,
                    styled = navPrototype && fabState == null,
                    edgeFab = if (snackbarBesideFab) navFabPosition else null,
                    fabSize = fabHeightDp,
                    reduceMotion = reduceMotion,
                    modifier = Modifier
                        .zIndex(1f)
                        .offset(y = if (selectingItems) -SelectionBarHeight else 0.dp)
                        .onSizeChanged { snackbarHeightDp = with(localDensity) { it.height.toDp() } }
                )
            }
        ) { paddingValues ->
            AnimatedContent(
                targetState = currentScreen,
                transitionSpec = {
                    val initialIndex = navBarOrder.indexOf(initialState)
                    val targetIndex = navBarOrder.indexOf(targetState)
                    val forward = when {
                        // Between two tabs: the way they lie in the navigation bar
                        initialIndex != -1 && targetIndex != -1 -> targetIndex > initialIndex
                        // Into a screen that isn't a tab (a file's details, the settings) is deeper, out of one back
                        targetIndex == -1 && initialIndex != -1 -> true
                        initialIndex == -1 && targetIndex != -1 -> false
                        else -> targetState.ordinal > initialState.ordinal
                    }
                    pageTransition(forward, reduceMotion)
                },
                label = "screenTransition"
            ) { targetScreen ->
                // Without the app's bottom bar, the file screens and the details go on under the system's navigation bar
                // (the lists add room for it after their last item, see LocalBottomInset) instead of being cut off above it.
                // The upload and settings screens keep their buttons clear of it.
                val bottomBarShown = !navPrototype && targetScreen != Screen.FileDetail && navBarOrder.size > 1
                val underNavigationBar = !bottomBarShown && targetScreen in setOf(Screen.FileDetail, Screen.Files, Screen.Lists, Screen.Filesystem)
                val layoutDirection = LocalLayoutDirection.current
                CompositionLocalProvider(
                    LocalBottomInset provides if (underNavigationBar) paddingValues.calculateBottomPadding() else 0.dp
                ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(
                            start = paddingValues.calculateStartPadding(layoutDirection),
                            top = paddingValues.calculateTopPadding(),
                            end = paddingValues.calculateEndPadding(layoutDirection),
                            bottom = if (underNavigationBar) 0.dp else paddingValues.calculateBottomPadding()
                        )
                ) {
                    val noScreensMessage = "${activeConfig?.name ?: "This host"} has no screens to show: its config's " +
                        "\"screens\" list is empty, or only names screens this host can't offer. Edit the config in " +
                        "Settings → Hosts, or remove \"screens\" to show everything the host can do."
                    when (targetScreen) {
                        Screen.Upload if noScreensConfigured -> CenteredTextMessage(noScreensMessage)
                        Screen.Files if noScreensConfigured -> CenteredTextMessage(noScreensMessage)
                        Screen.Lists if noScreensConfigured -> CenteredTextMessage(noScreensMessage)
                        Screen.Filesystem if noScreensConfigured -> CenteredTextMessage(noScreensMessage)
                        Screen.Upload -> UploadScreenContent(
                            uploadViewModel = uploadViewModel,
                            fabHeight = fabHeightDp,
                            isFabVisible = isFabVisible,
                            inlineUploadButton = navPrototype
                        )
                        Screen.Files, Screen.Lists, Screen.Filesystem -> BrowserScreen(
                            mode = when (targetScreen) {
                                Screen.Files -> BrowserMode.FILES
                                Screen.Lists -> BrowserMode.LISTS
                                else -> BrowserMode.FILESYSTEM
                            },
                            filesystemViewModel = filesystemViewModel,
                            fileInfoViewModel = fileInfoViewModel,
                            listViewModel = listViewModel,
                            appSettings = appContainer.appSettings,
                            disabledCapabilities = activeScreenConfigs[targetScreen]?.disabledCapabilities ?: emptySet(),
                            // Only the screen being shown, not one still animating out
                            showSearchModal = showSearchModal && targetScreen == currentScreen,
                            onDismissSearchModal = { showSearchModal = false },
                            activeKind = appContainer.providerRegistry.resolve(activeHostId).kind,
                            onSelectingChange = { selectingItems = it },
                            scrollState = when (targetScreen) {
                                Screen.Files -> filesListState
                                Screen.Lists -> if (listsUiState.openedList != null) listContentsListState else listsListState
                                else -> filesystemListState
                            },
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
                        Screen.Settings -> SettingsScreenContent(
                            categoryId = settingsCategoryId,
                            onCategoryChange = { settingsCategoryId = it },
                            appSettings = appContainer.appSettings,
                            apiKeyInput = apiKeyInput,
                            onApiKeyInputChange = { apiKeyInput = it },
                            authViewModel = authViewModel,
                            providerSettingsViewModel = providerSettingsViewModel,
                            fabHeight = fabHeightDp,
                            isFabVisible = isFabVisible,
                            onNavigateBack = { navigateTo(previousScreen) }
                        )
                    }
                }
                }
            }
        }
        // Kept while the navigation prototype is on: it slides out of the screen when hidden (see NavFabMenu)
        if (navPrototype) {
            NavFabMenu(
                menu = navMenuPreview.menu(),
                currentScreen = currentScreen,
                position = navFabPosition,
                onPositionChange = appContainer.appSettings::setNavFabPosition,
                onNavigate = navigateTo,
                onFabHeightChanged = { fabHeightDp = it },
                lift = navLift,
                visible = showNavFab,
                onOpenChange = { navMenuOpen = it }
            )
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

        if (fileInfoInitiateDelete) {
            AlertDialog(
                onDismissRequest = { fileInfoViewModel.cancelDeleteFile() },
                title = { Text("Confirm Deletion") },
                text = {
                    val isSearchIndex = fileInfoUiState.nodeToDelete?.isSearchIndex() == true
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
    }
    }
}


private val SEARCH_BLUR_RADIUS = 16.dp
/** How often the hosts are checked while the app is in front, see HostHealth. */
private const val HOST_RECHECK_MILLIS = 3 * 60_000L
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

/**
 * The progress bar under the top bar, for the transfer the current screen shows. It reads the upload and download state
 * itself, so each progress tick redraws only this bar and not the whole screen.
 */
@Composable
private fun ColumnScope.TransferBarSlot(
    currentScreen: Screen,
    uploadViewModel: UploadViewModel,
    filesystemViewModel: FilesystemViewModel,
    fileInfoViewModel: FileInfoViewModel,
) {
    val uploadUiState by uploadViewModel.uiState.collectAsState()
    val filesystemUiState by filesystemViewModel.uiState.collectAsState()
    val fileInfoUiState by fileInfoViewModel.uiState.collectAsState()
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
        else -> fileInfoUiState.downloadBatch?.takeIf {
            currentScreen in listOf(Screen.Files, Screen.Filesystem, Screen.Lists, Screen.FileDetail)
        }?.let { batch ->
            // One bar for the whole batch: the finished files plus the bytes of the file in progress
            val current = fileInfoUiState.activeDownloads.values.firstOrNull {
                it.status == DownloadStatus.DOWNLOADING || it.status == DownloadStatus.PENDING
            }
            TransferProgress(
                transferredBytes = batch.doneBytes + (current?.downloadedBytes ?: 0L),
                totalBytes = batch.totalBytes,
                bytesPerSecond = current?.bytesPerSecond ?: 0L,
                etaSeconds = current?.etaSeconds,
                label = "${batch.doneFiles} of ${batch.totalFiles} files done"
            )
        } ?: run {
            val isActive = { download: tools.senko.materialdrain.files.FileDownloadState ->
                download.status == DownloadStatus.DOWNLOADING || download.status == DownloadStatus.PENDING
            }
            val activeDownload = when (currentScreen) {
                Screen.FileDetail -> fileInfoUiState.fileInfo?.key?.let { fileInfoUiState.activeDownloads[it] }?.takeIf(isActive)
                Screen.Files, Screen.Filesystem, Screen.Lists -> fileInfoUiState.activeDownloads.values.firstOrNull(isActive)
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

/** A value computed from [calc]: a reader recomposes only when that value changes, not on every change of the state it reads. */
@Composable
private fun <T> derivedFrom(calc: () -> T): State<T> = remember { derivedStateOf(calc) }
