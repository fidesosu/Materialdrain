package tools.senko.materialdrain

import tools.senko.materialdrain.provider.api.FileList
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.runtime.mutableFloatStateOf
import kotlinx.coroutines.CancellationException
import tools.senko.materialdrain.ui.theme.AppShapes
import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
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
import tools.senko.materialdrain.ui.icons.automirrored.filled.ArrowBack
import tools.senko.materialdrain.ui.icons.filled.MoreVert
import tools.senko.materialdrain.ui.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.offset
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds
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
import tools.senko.materialdrain.browser.BrowserScreen
import tools.senko.materialdrain.filesystem.FilesystemViewModel
import tools.senko.materialdrain.lists.ListViewModel
import tools.senko.materialdrain.navmenu.NavFabMenu
import tools.senko.materialdrain.ui.components.sidewaysSwipe
import tools.senko.materialdrain.navmenu.menu
import tools.senko.materialdrain.settings.ui.ACCOUNT_CATEGORY_ID
import tools.senko.materialdrain.settings.ui.AccountViewModel
import tools.senko.materialdrain.settings.ui.HOSTS_CATEGORY_ID
import tools.senko.materialdrain.settings.DataSaverState
import tools.senko.materialdrain.settings.LocalDataSaver
import tools.senko.materialdrain.settings.ui.AuthViewModel
import tools.senko.materialdrain.settings.ui.ProviderSettingsViewModel
import tools.senko.materialdrain.settings.ui.SettingsScreenContent
import tools.senko.materialdrain.settings.ui.settingsCategory
import tools.senko.materialdrain.files.key
import tools.senko.materialdrain.ui.LocalBlurredBackdrop
import tools.senko.materialdrain.ui.LocalBottomInset
import tools.senko.materialdrain.browser.ScreenAction
import tools.senko.materialdrain.browser.ScreenMenuEntries
import tools.senko.materialdrain.browser.SortChoice
import tools.senko.materialdrain.ui.components.OverflowMenuButton
import androidx.compose.runtime.mutableStateMapOf
import tools.senko.materialdrain.ui.components.LocalMenuFloor
import tools.senko.materialdrain.ui.components.MenuFloor
import tools.senko.materialdrain.ui.components.keepsMenusAbove
import tools.senko.materialdrain.ui.LocalTextWrap
import tools.senko.materialdrain.ui.LocalReduceMotion
import tools.senko.materialdrain.ui.LocalVideoLoop
import tools.senko.materialdrain.ui.VideoLoopSetting
import tools.senko.materialdrain.ui.LocalPlayerSettings
import tools.senko.materialdrain.ui.PlayerSettings
import tools.senko.materialdrain.ui.LocalListLook
import tools.senko.materialdrain.ui.ListLook
import tools.senko.materialdrain.settings.AppSettings
import tools.senko.materialdrain.ui.components.PAGE_TRANSITION_MS
import tools.senko.materialdrain.ui.components.pageTransition
import tools.senko.materialdrain.ui.components.AppSnackbarHost
import tools.senko.materialdrain.ui.components.CenteredTextMessage
import tools.senko.materialdrain.ui.components.snackbarMotion
import tools.senko.materialdrain.ui.components.TransferProgress
import tools.senko.materialdrain.ui.components.TransferStatusBar
import tools.senko.materialdrain.upload.UploadItemStatus
import tools.senko.materialdrain.upload.UploadScreenContent
import tools.senko.materialdrain.upload.UploadViewModel
import tools.senko.materialdrain.ui.preview.PreviewTheme
import tools.senko.materialdrain.ui.preview.ScreenPreviews
import tools.senko.materialdrain.ui.preview.PreviewSamples
import tools.senko.materialdrain.browser.SampleBrowserContent
import tools.senko.materialdrain.files.SampleFileDetails
import tools.senko.materialdrain.hosts.HostOption
import tools.senko.materialdrain.hosts.HostStatus
import tools.senko.materialdrain.hosts.HostCheck
import tools.senko.materialdrain.hosts.Reachability
import tools.senko.materialdrain.ui.icons.filled.Cloud
import tools.senko.materialdrain.upload.UploadUiState
import tools.senko.materialdrain.settings.ui.rememberPreviewSettings

@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, ExperimentalSharedTransitionApi::class)
@Composable
fun MaterialdrainScreen() {
    var previousScreen by rememberSaveable { mutableStateOf(Screen.Files) }

    // The problem shown in a dialog, if any (see AppDialog)
    var dialog by remember { mutableStateOf<AppDialog?>(null) }

    val application = LocalContext.current.applicationContext as Application
    val context = LocalContext.current
    val appContainer = remember { AppContainer.get(application) }
    // The app opens on the screen it was last on, and of a host with several tabs of that kind, on the same one
    // ...unless it's set to open on one screen (Look and feel, Start screen)
    var currentScreen by rememberSaveable {
        mutableStateOf(appContainer.appSettings.startScreen.value ?: appContainer.appSettings.lastScreen)
    }
    var currentTabId by rememberSaveable { mutableStateOf(appContainer.appSettings.lastTabId) }
    val sessionManager = appContainer.sessionManager
    val viewModelFactory = remember { ViewModelFactory(application, appContainer) }

    // The ViewModels which own uploads/downloads outlive the activity, so transfers continue in the background. The
    // filesystem has one per Filesystem tab, see filesystemViewModelFor
    val transferOwner = appContainer.transferViewModelStoreOwner
    val uploadViewModel: UploadViewModel = viewModel(viewModelStoreOwner = transferOwner, factory = viewModelFactory)
    val fileInfoViewModel: FileInfoViewModel = viewModel(viewModelStoreOwner = transferOwner, factory = viewModelFactory)
    val listViewModel: ListViewModel = viewModel(factory = viewModelFactory)
    val authViewModel: AuthViewModel = viewModel(factory = viewModelFactory)
    val accountViewModel: AccountViewModel = viewModel(factory = viewModelFactory)
    val providerSettingsViewModel: ProviderSettingsViewModel = viewModel(factory = viewModelFactory)

    val snackbarHostState = remember { SnackbarHostState() }
    val fileInfoUiState by fileInfoViewModel.uiState.collectAsState()
    val uploadUiState by uploadViewModel.uiState.collectAsState()
    val listsUiState by listViewModel.uiState.collectAsState()
    // Values which the effects and conditions of this screen use. A derived state recomposes this screen only when its own
    // value changes, so a progress tick of a transfer doesn't recompose the whole screen (see TransferBarSlot)
    val uploadApiKeyMissing by derivedFrom { uploadUiState.apiKeyMissing }
    val fileInfoApiKeyMissing by derivedFrom { fileInfoUiState.apiKeyMissingError }
    val userFilesListError by derivedFrom { fileInfoUiState.userFilesListErrorMessage }
    val fileInfoInitiateDelete by derivedFrom { fileInfoUiState.initiateDeleteFile }
    val listOpened by derivedFrom { listsUiState.openedList != null }

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
    val swipeTabs by appContainer.appSettings.swipeBetweenTabs.collectAsState()
    // How the players behave and how the lists look, for the screens deep down (see LocalPlayerSettings, LocalListLook)
    val playerSettings = rememberPlayerSettings(appContainer.appSettings)
    val listLook = rememberListLook(appContainer.appSettings)
    val videoLoop = remember(loopVideos) { VideoLoopSetting(loopVideos, appContainer.appSettings::setLoopVideos) }
    LaunchedEffect(hasActiveTransfers) {
        if (hasActiveTransfers && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            if (!appContainer.appSettings.askedNotificationPermission) {
                appContainer.appSettings.askedNotificationPermission = true
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    val coroutineScope = rememberCoroutineScope()



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
    val hostTabs = rememberHostTabs(appContainer, activeHostId, configChanges)
    val navBarOrder = hostTabs.tabs
    val activeConfig = hostTabs.config
    val noScreensConfigured = hostTabs.noScreensConfigured
    fun tabOf(screen: Screen): Tab? = hostTabs.tabOf(screen, currentTabId)
    val currentTab = if (currentScreen in BROWSE_SCREENS) tabOf(currentScreen) else null
    fun screenTitle(screen: Screen): String = tabOf(screen)?.title ?: screen.title
    LaunchedEffect(navBarOrder, currentScreen) {
        // A tab the new host doesn't offer falls back to the first one it does (e.g. Upload isn't reachable on a
        // host which only turned on Filesystem, even if Upload was the screen open before switching to it). Also
        // checked when the screen changes: a host added or switched to from Settings only meets its tabs when Settings
        // goes back to the screen open before it, and with a single tab there's no bar to leave a missing one by
        if (currentScreen in BROWSE_SCREENS && tabOf(currentScreen) == null && navBarOrder.isNotEmpty()) {
            currentScreen = navBarOrder.first().screen
            currentTabId = navBarOrder.first().id
        }
    }
    LaunchedEffect(currentTab?.id) { currentTab?.let { appContainer.appSettings.lastTabId = it.id } }
    // The details take away what the tab they're opened from does (see FileInfoViewModel.setTabLimits): the tab shown, or
    // the one the details were opened from
    val openingTab = currentTab ?: tabOf(previousScreen)?.takeIf { previousScreen in BROWSE_SCREENS }
    LaunchedEffect(openingTab?.config?.disabledCapabilities) {
        fileInfoViewModel.setTabLimits(openingTab?.config?.disabledCapabilities ?: emptySet())
    }
    // The browser of each Filesystem tab of the active host (one per tab, see FilesystemViewModel.bind). The other screens
    // use the host's first one for picking a folder in it
    val hostFilesystemTab = navBarOrder.firstOrNull { it.screen == Screen.Filesystem }
    val filesystemViewModel = filesystemViewModelFor(
        activeHostId, currentTab?.takeIf { it.screen == Screen.Filesystem } ?: hostFilesystemTab, transferOwner, viewModelFactory
    )
    // The scroll position of each Filesystem tab, kept across visits to other screens
    val filesystemListStates = rememberSaveable(saver = TabScrollStates.Saver) { TabScrollStates() }
    // The page the main content shows now (see Shown)
    val shownNow = Shown(currentScreen, currentTab, activeHostId)
    // The New actions of each page shown (see BrowserScreen), by Shown.key, for the ⋮ menu in the top bar
    val screenActions = remember { mutableStateMapOf<String, List<ScreenAction>>() }
    // While items are selected the bar is at the bottom, so the FAB and the snackbars move up over it
    var selectingItems by remember { mutableStateOf(false) }
    // The height of the snackbar while one is shown (0 when none is); the FAB rises above it
    var snackbarHeightDp by remember { mutableStateOf(0.dp) }
    // The scroll position of each list survives visits to other screens, so it's kept here rather than in the screen
    val filesListState = rememberLazyListState()
    val listsListState = rememberLazyListState()
    val listContentsListState = rememberLazyListState()

    val navigateTo = { screen: Screen ->
        if (currentScreen != screen) {
            previousScreen = currentScreen
            currentScreen = screen
        }
    }
    // A tab of the bar: the screen of its kind, as that tab
    val selectTab = { tab: Tab ->
        currentTabId = tab.id
        navigateTo(tab.screen)
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
        (currentScreen == Screen.Lists && listOpened)
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
    // A link, a shortcut or shared text (see PendingOpen), also once the app is unlocked. A Pixeldrain link opens on the
    // built-in Pixeldrain, whichever host is active
    val pendingOpen by appContainer.pendingOpen.collectAsState()
    LaunchedEffect(pendingOpen, appLocked) {
        val open = pendingOpen ?: return@LaunchedEffect
        if (appLocked) return@LaunchedEffect
        appContainer.pendingOpen.value = null
        when (open) {
            is PendingOpen.ToScreen -> tabOf(open.screen)?.let(selectTab) ?: navigateTo(open.screen)
            is PendingOpen.PixeldrainFile -> {
                appContainer.providerConfigStore.setActive(PIXELDRAIN_PROVIDER_ID)
                fileInfoViewModel.setNeighbours(emptyList(), byNode = false)
                fileInfoViewModel.fetchFileInfo(open.id)
                navigateTo(Screen.FileDetail)
            }
            is PendingOpen.PixeldrainList -> {
                appContainer.providerConfigStore.setActive(PIXELDRAIN_PROVIDER_ID)
                // Its title and count come with its files
                listViewModel.openList(FileList(id = open.id, title = "", fileCount = 0, canEdit = false))
                navigateTo(Screen.Lists)
            }
            is PendingOpen.Text -> {
                uploadViewModel.onTextToUploadChanged(open.text)
                navigateTo(Screen.Upload)
            }
        }
    }

    LaunchedEffect(Unit) {
        Log.d("App", "Initial composition. Loading the manually entered API Key.")
        apiKeyInput = sessionManager.manualApiKey()
    }

    // The back gesture out of a file's details (Android 14 and up shows it as it's made): the page shrinks back with the
    // swipe, and goes when it's let go; a gesture given up on puts it back
    var detailsBackProgress by remember { mutableFloatStateOf(0f) }
    PredictiveBackHandler(enabled = currentScreen == Screen.FileDetail) { progress ->
        try {
            progress.collect { event -> detailsBackProgress = event.progress }
            fileInfoViewModel.setPreserveScrollPosition(previousScreen == Screen.Files)
            navigateTo(previousScreen)
        } catch (e: CancellationException) {
            detailsBackProgress = 0f
            throw e
        }
    }
    LaunchedEffect(currentScreen) { if (currentScreen != Screen.FileDetail) detailsBackProgress = 0f }

    LaunchedEffect(currentScreen) {
        Log.d("App", "currentScreen changed to: ${currentScreen.name}")
        // The next visit of the settings starts at the list of categories
        if (currentScreen != Screen.Settings) settingsCategoryId = null
        when (currentScreen) {
            Screen.FileDetail -> fileInfoViewModel.setFilterInputVisible(false)
            else -> {
                fileInfoViewModel.clearUserFilesError()
                fileInfoViewModel.clearApiKeyMissingError()
                fileInfoViewModel.setFilterInputVisible(false)
                // Once the details have slid out: until then they still show the file, instead of "File details not
                // available" with its previews gone. Going back into the details before that cancels it
                delay(PAGE_TRANSITION_MS.toLong())
                fileInfoViewModel.clearFileInfoDisplay()
            }
        }
    }

    LaunchedEffect(key1 = uploadViewModel) {
        // Only the failure and the error of the upload, not every progress tick
        uploadViewModel.uiState
            .map { it.uploadResult?.takeIf { result -> !result.success } to it.errorMessage }
            .distinctUntilChanged()
            .collect { (failed, error) ->
                if (failed != null) {
                    dialog = AppDialog.UploadFailed(failed.message ?: "Unknown error")
                } else if (error != null && dialog == null && currentScreen == Screen.Upload) {
                    dialog = AppDialog.UploadError(error)
                }
            }
    }

    LaunchedEffect(uploadApiKeyMissing, currentScreen) {
        if (uploadApiKeyMissing && currentScreen == Screen.Upload) dialog = AppDialog.UploadApiKeyRequired
    }

    // The ViewModels' messages for the snackbar, one after another (see UiMessages)
    LaunchedEffect(Unit) {
        appContainer.uiMessages.messages.collect { message ->
            val result = snackbarHostState.showSnackbar(
                message.text,
                actionLabel = message.action?.label,
                duration = if (message.long) SnackbarDuration.Long else SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed) {
                when (val action = message.action) {
                    is UiMessageAction.OpenDownload -> openDownloadedFile(context, action.download)
                    null -> Unit
                }
            }
        }
    }
    // The file whose details are open was deleted: there's nothing left to show, so back to where it was opened from
    LaunchedEffect(fileInfoViewModel) {
        fileInfoViewModel.fileDeleted.collect {
            if (currentScreen == Screen.FileDetail) {
                fileInfoViewModel.setPreserveScrollPosition(previousScreen == Screen.Files)
                navigateTo(previousScreen)
            }
        }
    }

    if (fileInfoApiKeyMissing && (currentScreen == Screen.Files || currentScreen == Screen.FileDetail) &&
        !userFilesListError.isNullOrBlank() &&
        dialog == null && !fileInfoInitiateDelete) {
        LaunchedEffect(true, currentScreen, userFilesListError) {
            val message = fileInfoUiState.userFilesListErrorMessage
            if (message != null && message.contains("API Key", ignoreCase = true)) {
                dialog = AppDialog.FilesApiKeyRequired(onDetails = currentScreen == Screen.FileDetail, message = message)
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

    // The navigation prototype has its own button in the corner, which takes the place of the screens' buttons; the
    // upload screen keeps its upload button
    val fabState by remember {
        derivedStateOf {
            if (navPrototype) null else screenFab(
                screen = currentScreen,
                canUpload = uploadUiState.hasUploadable && !uploadUiState.isLoading,
                onUpload = { if (uploadUiState.hasUploadable && !uploadUiState.isLoading) uploadViewModel.upload() },
                settingsHasSaveButton = settingsCategory(settingsCategoryId)?.hasSaveButton == true,
                settingsJustSaved = settingsJustSaved,
                settingsSaveCount = settingsSaveCount,
                onSaveSettings = {
                    // The ViewModels which use the key watch it themselves (see SessionManager.apiKey)
                    sessionManager.saveManualApiKey(apiKeyInput)
                    settingsSaveCount++
                }
            )
        }
    }
    // Like the old bottom bar, the navigation FAB stays out of the way on detail screens
    // With one screen (or none) turned on there's nothing to navigate between, so the bar/FAB would only take up space
    // A wide screen has the tabs at its side instead of the bottom bar, unless the navigation button is chosen
    val useRail = isWideScreen() && !navPrototype && navBarOrder.size > 1
    val barLabels by appContainer.appSettings.barLabels.collectAsState()
    val swipeFiles by appContainer.appSettings.swipeBetweenFiles.collectAsState()
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
    val isFabVisible = showNavFab || fabState != null

    // The thumbnails the data saver holds back, for every picture of a file in the app (see FileIcon)
    val dataSaver = appContainer.dataSaver
    val holdThumbnails by dataSaver.holdThumbnails.collectAsState()
    val allThumbnailsAsked by dataSaver.allThumbnailsAsked.collectAsState()
    val askedThumbnails by dataSaver.askedThumbnails.collectAsState()
    val dataSaverState = remember(holdThumbnails, allThumbnailsAsked, askedThumbnails) {
        DataSaverState(
            holdThumbnails = holdThumbnails && !allThumbnailsAsked,
            askedThumbnails = askedThumbnails,
            loadThumbnail = dataSaver::askThumbnail,
            loadAllThumbnails = dataSaver::askAllThumbnails
        )
    }

    CompositionLocalProvider(
        LocalReduceMotion provides reduceMotion,
        LocalBlurredBackdrop provides blurredBackdrop,
        LocalTextWrap provides textWrap,
        LocalVideoLoop provides videoLoop,
        LocalPlayerSettings provides playerSettings,
        LocalListLook provides listLook,
        LocalDataSaver provides dataSaverState,
        // Menus open above the bottom bar or the navigation button (see MenuFloor)
        LocalMenuFloor provides remember { MenuFloor() }
    ) {
    SharedTransitionLayout {
        // The Box lets the FAB navigation prototype (Developer settings) sit on top of the Scaffold. Blurred as a
        // whole while the search modal is open, which is its own window and so stays sharp
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(if (searchBlur > 0.dp) Modifier.blur(searchBlur) else Modifier)
        ) {
        Row(modifier = Modifier.fillMaxSize()) {
        if (useRail) {
            AppNavigationRail(currentTab, navBarOrder, onTabSelected = selectTab, alwaysShowLabels = barLabels)
        }
        Scaffold(
            // Blurred behind the navigation window, which sits on top of it in this Box and so stays sharp
            modifier = Modifier.weight(1f).then(if (navMenuBlur > 0.dp) Modifier.blur(navMenuBlur) else Modifier),
            topBar = {
                Column {
                    AppTopBar(
                        currentScreen = currentScreen,
                        title = when (currentScreen) {
                            // The name is under the preview (see FileInfoDetailsCard), not up here as well
                            Screen.FileDetail -> ""
                            Screen.Settings -> settingsCategory(settingsCategoryId)?.title ?: Screen.Settings.title
                            else -> screenTitle(currentScreen)
                        },
                        reduceMotion = reduceMotion,
                        searchAvailable = searchAvailable,
                        onBack = {
                            when (currentScreen) {
                                Screen.FileDetail -> {
                                    fileInfoViewModel.setPreserveScrollPosition(previousScreen == Screen.Files)
                                    navigateTo(previousScreen)
                                }
                                // Back leaves a category first, then the settings
                                else -> if (settingsCategoryId != null) settingsCategoryId = null else navigateTo(previousScreen)
                            }
                        },
                        onSearch = { showSearchModal = true },
                        // A file's details have their own buttons for what's done with the file, and the settings
                        // are where the menu leads: neither has the menu
                        menu = if (currentScreen == Screen.FileDetail || currentScreen == Screen.Settings) null else { {
                            OverflowMenuButton(contentDescription = "More options", buttonSize = 48.dp) { close ->
                                // Read only while the menu is open, so a change of the order doesn't redraw the bar
                                val sortField by appContainer.appSettings.sortField.collectAsState()
                                val sortAscending by appContainer.appSettings.sortAscending.collectAsState()
                                // Every list that's sorted: not the upload screen, nor the overview of the lists (by name)
                                val sortable = currentScreen == Screen.Files || currentScreen == Screen.Filesystem ||
                                    (currentScreen == Screen.Lists && listOpened)
                                ScreenMenuEntries(
                                    close = close,
                                    sort = if (sortable) SortChoice(sortField, sortAscending, appContainer.appSettings::changeSortOrder) else null,
                                    actions = screenActions[shownNow.key].orEmpty(),
                                    onOpenSettings = { navigateTo(Screen.Settings) }
                                )
                            }
                        } },
                        hostSwitcher = if (currentScreen !in BROWSE_SCREENS) null else { {
                        HostSwitcher(
                            hosts = hostOptions(appContainer.providerConfigStore, sessionManager, loggedInUser),
                            activeId = activeHostId,
                            checks = hostChecks,
                            onSelect = { appContainer.providerConfigStore.setActive(it) },
                            onOpenHostSettings = { openHostSettings(it) },
                            onManage = { openHostSettings(null) },
                            // Only hosts not checked in the last half minute: the list is normally known already
                            onOpened = { appContainer.hostHealth.checkAll() },
                            onRefresh = { appContainer.hostHealth.checkAll(force = true, asked = true) }
                        )
                        } }
                    )
                    TransferBarSlot(currentScreen, uploadViewModel, filesystemViewModel, fileInfoViewModel)
                }
            },
            bottomBar = {
                AnimatedVisibility(
                    // The FAB navigation prototype (Developer settings) replaces the bar while it's on; with no
                    // screens to show there is nothing for the bar to hold either
                    visible = !navPrototype && !useRail && currentScreen != Screen.FileDetail && navBarOrder.size > 1,
                    // Above the upload button, so the button slides down behind the bar
                    modifier = Modifier.zIndex(2f),
                    // The bar rises out of the bottom edge and sinks back into it, its icons with it: its top stays on the
                    // growing or shrinking space, which the page above follows
                    enter = if (reduceMotion) fadeIn(tween(100)) else fadeIn() + expandVertically(expandFrom = Alignment.Top),
                    exit = if (reduceMotion) fadeOut(tween(100)) else fadeOut() + shrinkVertically(shrinkTowards = Alignment.Top)
                ) {
                    BottomNavigationBar(
                        currentTab, navBarOrder, onTabSelected = selectTab, modifier = Modifier.keepsMenusAbove(), alwaysShowLabels = barLabels
                    )
                }
            },
            floatingActionButton = {
                AppFab(fabState, lift = fabLift, reduceMotion = reduceMotion, onHeightChanged = { fabHeightDp = it })
            },
            snackbarHost = {
                // The snackbar stays at the bottom; while selecting it sits above the selection bar
                AppSnackbarHost(
                    hostState = snackbarHostState,
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
            // A swipe sideways goes to the tab beside this one (Look and feel), while nothing else is open over the tab
            val swipeEnabled = swipeTabs && currentTab != null && navBarOrder.size > 1 && !noScreensConfigured &&
                !selectingItems && !showSearchModal && !navMenuOpen
            AnimatedContent(
                targetState = shownNow,
                modifier = Modifier.sidewaysSwipe(swipeEnabled) { toNext ->
                    val index = navBarOrder.indexOf(currentTab)
                    val next = if (index == -1) null else navBarOrder.getOrNull(if (toNext) index + 1 else index - 1)
                    next?.let(selectTab)
                },
                // The tab too: two tabs of one kind (two Filesystem tabs) are two pages, moved between like any tabs. Not
                // the host: switching hosts on the same tab changes the page in place instead of sliding
                contentKey = { it.screen to it.tab?.id },
                transitionSpec = {
                    val initialIndex = navBarOrder.indexOf(initialState.tab)
                    val targetIndex = navBarOrder.indexOf(targetState.tab)
                    val forward = when {
                        // Between two tabs: the way they lie in the navigation bar
                        initialIndex != -1 && targetIndex != -1 -> targetIndex > initialIndex
                        // Into a screen that isn't a tab (a file's details, the settings) is deeper, out of one back
                        targetIndex == -1 && initialIndex != -1 -> true
                        initialIndex == -1 && targetIndex != -1 -> false
                        else -> targetState.screen.ordinal > initialState.screen.ordinal
                    }
                    pageTransition(forward, reduceMotion)
                },
                label = "screenTransition"
            ) { shown ->
                // A page sliding out keeps its own host and tab, so it goes on showing what it showed (and its browser)
                val targetScreen = shown.screen
                val targetTab = shown.tab
                // Without the app's bottom bar, the file screens and the details go on under the system's navigation bar
                // (the lists add room for it after their last item, see LocalBottomInset) instead of being cut off above it.
                // The upload and settings screens keep their buttons clear of it.
                val bottomBarShown = !navPrototype && !useRail && targetScreen != Screen.FileDetail && navBarOrder.size > 1
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
                        in BROWSE_SCREENS if noScreensConfigured -> CenteredTextMessage(noScreensMessage)
                        Screen.Upload -> UploadScreenContent(
                            uploadViewModel = uploadViewModel,
                            fabHeight = fabHeightDp,
                            isFabVisible = isFabVisible,
                            inlineUploadButton = navPrototype
                        )
                        Screen.Files, Screen.Lists, Screen.Filesystem -> {
                            // A Filesystem tab has its own browser, loading only while it's on screen; the other tabs use
                            // the host's first one, for picking a folder in it
                            val tabFilesystem = if (targetScreen == Screen.Filesystem) {
                                filesystemViewModelFor(shown.hostId, targetTab, transferOwner, viewModelFactory).also { browser ->
                                    DisposableEffect(browser) {
                                        browser.show()
                                        onDispose { browser.hide() }
                                    }
                                }
                            } else {
                                filesystemViewModel
                            }
                            BrowserScreen(
                                mode = when (targetScreen) {
                                    Screen.Files -> BrowserMode.FILES
                                    Screen.Lists -> BrowserMode.LISTS
                                    else -> BrowserMode.FILESYSTEM
                                },
                                filesystemViewModel = tabFilesystem,
                                fileInfoViewModel = fileInfoViewModel,
                                listViewModel = listViewModel,
                                appSettings = appContainer.appSettings,
                                disabledCapabilities = targetTab?.config?.disabledCapabilities ?: emptySet(),
                                // Only the screen being shown, not one still animating out
                                showSearchModal = showSearchModal && shown == shownNow,
                                onDismissSearchModal = { showSearchModal = false },
                                onSelectingChange = { selectingItems = it },
                                scrollState = when (targetScreen) {
                                    Screen.Files -> filesListState
                                    Screen.Lists -> if (listOpened) listContentsListState else listsListState
                                    else -> filesystemListStates.of("${shown.hostId}:${targetTab?.id}")
                                },
                                onFileSelected = { navigateTo(Screen.FileDetail) },
                                fabHeight = fabHeightDp,
                                isFabVisible = isFabVisible,
                                onActionsChange = { screenActions[shown.key] = it }
                            )
                            DisposableEffect(shown.key) { onDispose { screenActions.remove(shown.key) } }
                        }
                        Screen.FileDetail -> Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .sidewaysSwipe(swipeFiles && shown == shownNow) { toNext -> fileInfoViewModel.openNeighbour(toNext) }
                                .graphicsLayer {
                                    // Reduced animations: the page stays as it is until it goes
                                    val back = if (reduceMotion) 0f else detailsBackProgress
                                    scaleX = 1f - back * 0.1f
                                    scaleY = 1f - back * 0.1f
                                    shape = AppShapes.Card
                                    clip = back > 0f
                                }
                        ) {
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
                            accountViewModel = accountViewModel,
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
        }
        // Kept while the navigation prototype is on: it slides out of the screen when hidden (see NavFabMenu)
        if (navPrototype) {
            NavFabMenu(
                menu = navMenuPreview.menu(navBarOrder),
                currentScreen = currentScreen,
                currentTab = currentTab,
                position = navFabPosition,
                onPositionChange = appContainer.appSettings::setNavFabPosition,
                onNavigate = selectTab,
                onFabHeightChanged = { fabHeightDp = it },
                lift = navLift,
                visible = showNavFab,
                onOpenChange = { navMenuOpen = it }
            )
        }
        }

        AppDialogs(
            dialog = dialog,
            onClosed = { dialog = null },
            onOpenAccountSettings = {
                settingsCategoryId = ACCOUNT_CATEGORY_ID
                navigateTo(Screen.Settings)
            },
            confirmDelete = fileInfoInitiateDelete,
            uploadViewModel = uploadViewModel,
            fileInfoViewModel = fileInfoViewModel
        )
    }
    }
}

/** The Media settings, as the players read them (see LocalPlayerSettings). */
@Composable
private fun rememberPlayerSettings(settings: AppSettings): PlayerSettings {
    val autoplay by settings.autoplay.collectAsState()
    val resume by settings.resumeVideos.collectAsState()
    val step by settings.seekStepSeconds.collectAsState()
    val speed by settings.defaultSpeed.collectAsState()
    val gestures by settings.playerGestures.collectAsState()
    val background by settings.backgroundAudio.collectAsState()
    return remember(autoplay, resume, step, speed, gestures, background) {
        PlayerSettings(autoplay, resume, step * 1000L, speed, gestures, background)
    }
}

/** The Look and feel settings of the file lists (see LocalListLook). */
@Composable
private fun rememberListLook(settings: AppSettings): ListLook {
    val compact by settings.compactRows.collectAsState()
    val grid by settings.gridView.collectAsState()
    val relative by settings.relativeDates.collectAsState()
    return remember(compact, grid, relative) { ListLook(compact, grid, relative) }
}

/** What the main content shows: a [screen], and for a tab of the host [hostId], which of its tabs. */
private data class Shown(val screen: Screen, val tab: Tab?, val hostId: String) {
    /** The page, for what it tells the app around it (see screenActions). */
    val key: String get() = "$hostId:$screen:${tab?.id}"
}

/**
 * The browser of the Filesystem [tab] on the host [hostId]: one per tab (and root), so each keeps its own folder and
 * listing, and like the other ViewModels which own transfers it outlives the activity. Without a tab, the host's usual one.
 */
@Composable
private fun filesystemViewModelFor(
    hostId: String,
    tab: Tab?,
    owner: androidx.lifecycle.ViewModelStoreOwner,
    factory: ViewModelFactory
): FilesystemViewModel {
    val tabId = tab?.id ?: FilesystemViewModel.DEFAULT_TAB_ID
    val root = tab?.config?.rootFolder.orEmpty()
    val browser: FilesystemViewModel = viewModel(key = "filesystem:$hostId:$tabId:$root", viewModelStoreOwner = owner, factory = factory)
    browser.bind(hostId, tabId, root)
    return browser
}

/** The scroll position of each Filesystem tab, by host and tab, kept across visits and when the activity is made again. */
private class TabScrollStates(initial: Map<String, Pair<Int, Int>> = emptyMap()) {
    private val states = HashMap<String, androidx.compose.foundation.lazy.LazyListState>().apply {
        initial.forEach { (key, position) -> put(key, androidx.compose.foundation.lazy.LazyListState(position.first, position.second)) }
    }

    fun of(key: String) = states.getOrPut(key) { androidx.compose.foundation.lazy.LazyListState() }

    companion object {
        val Saver = androidx.compose.runtime.saveable.Saver<TabScrollStates, ArrayList<Any>>(
            save = { scrolls ->
                ArrayList(scrolls.states.flatMap { (key, state) -> listOf(key, state.firstVisibleItemIndex, state.firstVisibleItemScrollOffset) })
            },
            restore = { saved ->
                TabScrollStates(saved.chunked(3).associate { (key, index, offset) -> key as String to ((index as Int) to (offset as Int)) })
            }
        )
    }
}

private val SEARCH_BLUR_RADIUS = 16.dp
/** The screens which show the files of the active host, and so the host switcher in the top bar. */
private val BROWSE_SCREENS = setOf(Screen.Upload, Screen.Files, Screen.Lists, Screen.Filesystem)
/** How often the hosts are checked while the app is in front, see HostHealth. */
private const val HOST_RECHECK_MILLIS = 3 * 60_000L
private const val SETTINGS_SAVED_DISPLAY_MILLIS = 2000L


/**
 * The bar at the top of the app: the screen's title (or, on the browse screens, the host switcher over the middle of the
 * bar), back or search at the start, and the screen's ⋮ menu at the end.
 *
 * @param menu the ⋮ button with the screen's menu (see ScreenMenuEntries); null on a screen without one
 * @param hostSwitcher the host switcher on the browse screens, centred on the bar so the icons at its edges don't move it;
 * null elsewhere, where the title is shown instead
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AppTopBar(
    currentScreen: Screen,
    title: String,
    reduceMotion: Boolean,
    searchAvailable: Boolean,
    onBack: () -> Unit,
    onSearch: () -> Unit,
    menu: (@Composable () -> Unit)?,
    hostSwitcher: (@Composable () -> Unit)?
) {
    Box(modifier = Modifier.fillMaxWidth()) {
        CenterAlignedTopAppBar(
            title = {
                if (hostSwitcher != null) Unit else AnimatedContent(
                    targetState = title,
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
                    Screen.FileDetail, Screen.Settings -> IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                    // Opposite the settings icon
                    else -> if (searchAvailable) {
                        IconButton(onClick = onSearch) {
                            Icon(Icons.Filled.Search, contentDescription = "Search")
                        }
                    }
                }
            },
            actions = { menu?.invoke() }
        )
        if (hostSwitcher != null) {
            // Centred on the band the bar's icons sit in (below the status bar), not on the whole bar
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .windowInsetsPadding(TopAppBarDefaults.windowInsets)
                    .fillMaxWidth()
                    .height(64.dp),
                contentAlignment = Alignment.Center
            ) {
                hostSwitcher()
            }
        }
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
    val filesystemUpload by filesystemViewModel.uploadProgress.collectAsState()
    val fileInfoUiState by fileInfoViewModel.uiState.collectAsState()
    val downloads by fileInfoViewModel.downloads.collectAsState()
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
        Screen.Filesystem if filesystemUpload != null -> filesystemUpload!!.let {
            TransferProgress(
                transferredBytes = it.uploadedBytes,
                totalBytes = it.totalBytes,
                bytesPerSecond = it.bytesPerSecond,
                etaSeconds = it.etaSeconds,
                label = "${it.currentIndex} / ${it.totalFiles} files"
            )
        }
        else -> downloads.batch?.takeIf {
            currentScreen in listOf(Screen.Files, Screen.Filesystem, Screen.Lists, Screen.FileDetail)
        }?.let { batch ->
            // One bar for the whole batch: the finished files plus the bytes of the file in progress
            val current = downloads.active.values.firstOrNull {
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
                Screen.FileDetail -> fileInfoUiState.fileInfo?.key?.let { downloads.active[it] }?.takeIf(isActive)
                Screen.Files, Screen.Filesystem, Screen.Lists -> downloads.active.values.firstOrNull(isActive)
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
    // Keep the last value around so the bar can shrink away showing it, instead of collapsing to empty content.
    var lastTransfer by remember { mutableStateOf<TransferProgress?>(null) }
    LaunchedEffect(activeTransfer) { if (activeTransfer != null) lastTransfer = activeTransfer }
    // Grows out of the top bar and shrinks back into it, as the Cancel card's row below does (see StatusCards), so the
    // page under it slides rather than jumps; with reduced animations it only fades
    val reduceMotion = LocalReduceMotion.current
    AnimatedVisibility(
        visible = activeTransfer != null,
        enter = if (reduceMotion) fadeIn(tween(100)) else expandVertically(expandFrom = Alignment.Top) + fadeIn(),
        exit = if (reduceMotion) fadeOut(tween(100)) else shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut()
    ) {
        (activeTransfer ?: lastTransfer)?.let { TransferStatusBar(it) }
    }
}

/** A value computed from [calc]: a reader recomposes only when that value changes, not on every change of the state it reads. */
@Composable
private fun <T> derivedFrom(calc: () -> T): State<T> = remember { derivedStateOf(calc) }

// ---- Previews ----

/** The tabs of the built-in Pixeldrain, as the bottom bar has them. */
private val PreviewTabs = listOf(Screen.Upload, Screen.Files, Screen.Lists, Screen.Filesystem).map { Tab(it) }

/**
 * The app as a whole on [screen]: its top bar, [content] (one of the screens' own previews' content), and the bottom bar
 * on the tabs, put together as MaterialdrainScreen puts them together. [transfer] is the bar under the top bar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppFramePreview(screen: Screen, title: String = screen.title, transfer: TransferProgress? = null, content: @Composable () -> Unit) {
    PreviewTheme {
        Scaffold(
            topBar = {
                Column {
                    AppTopBar(
                        currentScreen = screen,
                        title = title,
                        reduceMotion = false,
                        searchAvailable = screen == Screen.Files || screen == Screen.Filesystem,
                        onBack = {},
                        onSearch = {},
                        menu = if (screen == Screen.FileDetail || screen == Screen.Settings) null else { {
                            IconButton(onClick = {}) { Icon(Icons.Filled.MoreVert, contentDescription = "More options") }
                        } },
                        hostSwitcher = if (screen !in BROWSE_SCREENS) null else { {
                            HostSwitcher(
                                hosts = listOf(HostOption("pixeldrain", "Pixeldrain", "Pixeldrain", Icons.Filled.Cloud, HostStatus.SIGNED_IN, "Signed in as sam", "pixeldrain.com")),
                                activeId = "pixeldrain",
                                checks = mapOf("pixeldrain" to HostCheck(Reachability.REACHABLE, latencyMillis = 84, checkedAtMillis = 1)),
                                onSelect = {}, onOpenHostSettings = {}, onManage = {}, onOpened = {}, onRefresh = {}
                            )
                        } }
                    )
                    transfer?.let { TransferStatusBar(it) }
                }
            },
            bottomBar = {
                if (screen in BROWSE_SCREENS) BottomNavigationBar(PreviewTabs.firstOrNull { it.screen == screen }, PreviewTabs, onTabSelected = {})
            }
        ) { padding ->
            Box(Modifier.padding(padding)) { content() }
        }
    }
}

/** The Files tab, while a file downloads. */
@ScreenPreviews
@Composable
private fun AppFilesPreview() = AppFramePreview(
    Screen.Files,
    transfer = TransferProgress(97_517_568, 248_313_856, bytesPerSecond = 21_495_808, etaSeconds = 7, label = "drone-flight.mp4")
) { SampleBrowserContent(BrowserMode.FILES, PreviewSamples.files.filter { !it.isDirectory }) }

@ScreenPreviews
@Composable
private fun AppFilesystemPreview() = AppFramePreview(Screen.Filesystem) { SampleBrowserContent(BrowserMode.FILESYSTEM) }

@ScreenPreviews
@Composable
private fun AppUploadPreview() = AppFramePreview(Screen.Upload) {
    UploadScreenContent(
        uiState = UploadUiState(),
        fabHeight = 0.dp,
        isFabVisible = false,
        inlineUploadButton = false,
        onFilesPicked = {},
        onClearPickedFile = {},
        onTextChange = {},
        onRemoveQueued = {},
        onClearQueued = {},
        onCancel = {},
        onUpload = {}
    )
}

/** A file's details: back and the ⋮ menu in the top bar, no bottom bar. */
@androidx.media3.common.util.UnstableApi
@ScreenPreviews
@Composable
private fun AppFileDetailsPreview() = AppFramePreview(Screen.FileDetail, title = "") { SampleFileDetails(PreviewSamples.pixeldrainFile) }

/** The settings, with the app's own settings and a made-up account (see rememberPreviewSettings). */
@ScreenPreviews
@Composable
private fun AppSettingsPreview() = AppFramePreview(Screen.Settings) {
    val environment = rememberPreviewSettings()
    SettingsScreenContent(
        categoryId = null,
        onCategoryChange = {},
        appSettings = environment.appSettings,
        authViewModel = environment.authViewModel,
        accountViewModel = environment.accountViewModel,
        apiKeyInput = "",
        onApiKeyInputChange = {},
        providerSettingsViewModel = environment.providerSettingsViewModel,
        fabHeight = 0.dp,
        isFabVisible = false,
        onNavigateBack = {}
    )
}
