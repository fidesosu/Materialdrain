package tools.senko.materialdrain

import tools.senko.materialdrain.provider.api.FileList
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.runtime.mutableFloatStateOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
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
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextAlign
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
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.graphics.rememberGraphicsLayer
import kotlin.time.Duration.Companion.milliseconds
import tools.senko.materialdrain.files.SelectionBarHeight
import tools.senko.materialdrain.files.openDownloadedFile
import tools.senko.materialdrain.hosts.HostSwitcher
import tools.senko.materialdrain.hosts.hostOptions
import tools.senko.materialdrain.files.FileInfoDetailsCard
import tools.senko.materialdrain.files.FileInfoViewModel
import tools.senko.materialdrain.browser.BrowserMode
import tools.senko.materialdrain.provider.PIXELDRAIN_PROVIDER_ID
import tools.senko.materialdrain.provider.api.ProviderCapability
import tools.senko.materialdrain.browser.BrowserScreen
import tools.senko.materialdrain.browser.SharedUpload
import tools.senko.materialdrain.ui.media.AudioPlayerView
import tools.senko.materialdrain.ui.media.BackgroundMiniPlayer
import tools.senko.materialdrain.ui.media.LocalOpenAudioPlayer
import tools.senko.materialdrain.ui.media.LocalSongCardShown
import tools.senko.materialdrain.ui.media.rememberAudioPlayerSheetState
import tools.senko.materialdrain.ui.media.rememberBackgroundSong
import tools.senko.materialdrain.ui.media.rememberPlaybackController
import tools.senko.materialdrain.filesystem.FilesystemViewModel
import tools.senko.materialdrain.lists.ListViewModel
import tools.senko.materialdrain.ui.components.sidewaysSwipe
import tools.senko.materialdrain.settings.ui.ACCOUNT_CATEGORY_ID
import tools.senko.materialdrain.settings.ui.AccountViewModel
import tools.senko.materialdrain.settings.ui.HOSTS_CATEGORY_ID
import tools.senko.materialdrain.settings.DataSaverState
import tools.senko.materialdrain.settings.LocalDataSaver
import tools.senko.materialdrain.settings.ui.AuthViewModel
import tools.senko.materialdrain.settings.ui.ProviderSettingsViewModel
import tools.senko.materialdrain.settings.ui.SettingsScreenContent
import tools.senko.materialdrain.settings.ui.settingsCategory
import tools.senko.materialdrain.settings.ui.settingsCatalog
import tools.senko.materialdrain.files.key
import tools.senko.materialdrain.ui.LocalBlurredBackdrop
import tools.senko.materialdrain.ui.LocalBottomInset
import tools.senko.materialdrain.browser.ScreenAction
import tools.senko.materialdrain.browser.ViewChoice
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
import tools.senko.materialdrain.ui.LocalAppHaptics
import tools.senko.materialdrain.ui.HapticKind
import tools.senko.materialdrain.ui.HapticPlace
import tools.senko.materialdrain.ui.rememberAppHaptics
import tools.senko.materialdrain.ui.LocalListLook
import tools.senko.materialdrain.ui.components.PageSwap
import tools.senko.materialdrain.ui.components.PAGE_TRANSITION_MS
import tools.senko.materialdrain.ui.components.pageTransition
import tools.senko.materialdrain.ui.components.AppSnackbarHost
import tools.senko.materialdrain.ui.components.CenteredTextMessage
import tools.senko.materialdrain.ui.components.snackbarMotion
import tools.senko.materialdrain.update.UpdateSheet
import tools.senko.materialdrain.update.WhatsNewSheet
import tools.senko.materialdrain.upload.UploadItemStatus
import tools.senko.materialdrain.upload.UploadScreenContent
import tools.senko.materialdrain.upload.UploadViewModel
import androidx.compose.ui.res.stringResource

@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, ExperimentalSharedTransitionApi::class)
@Composable
fun MaterialdrainScreen() {
    // The app's texts, for those its callbacks show
    val res = LocalResources.current
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
    // Swipes between the tabs with only the tab strip at the bottom, instead of the bottom bar (see TabStrip)
    val gestureNav by appContainer.appSettings.gestureNavigation.collectAsState()
    val swipeTabs by appContainer.appSettings.swipeBetweenTabs.collectAsState()
    // How the players behave and how the lists look, for the screens deep down (see LocalPlayerSettings, LocalListLook)
    val playerSettings = rememberPlayerSettings(appContainer.appSettings)
    // The app's vibrations, as the Feedback settings have them (see AppHaptics)
    val appHaptics = rememberAppHaptics(appContainer.appSettings)
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
    @Composable
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
    // The actions of each page shown (see BrowserScreen), by Shown.key, for the ⋮ menu in the top bar; and the look of
    // its list, for the menu's View page
    val screenActions = remember { mutableStateMapOf<String, List<ScreenAction>>() }
    val screenViews = remember { mutableStateMapOf<String, ViewChoice>() }
    // Whether each page shown has a bar at its bottom (see BrowserScreen's onBottomBarChange)
    val pageBottomBars = remember { mutableStateMapOf<String, Boolean>() }
    // While items are selected the bar is at the bottom, so the FAB and the snackbars move up over it
    var selectingItems by remember { mutableStateOf(false) }
    // The song player over the whole app (see AudioPlayerView), and the height of the miniplayer which opens it too
    val audioPlayerSheet = rememberAudioPlayerSheetState()
    val openAudioPlayer = remember(audioPlayerSheet) { { audioPlayerSheet.open() } }
    // Whether the song card of a file's details is on the screen, which the miniplayer of the same song stays away for
    var songCardShown by remember { mutableStateOf(false) }
    val onSongCardShown = remember { { shown: Boolean -> songCardShown = shown } }
    // The background player and its song, for the miniplayer and the player over the app; only with background songs
    val playbackController = if (playerSettings.backgroundAudio) rememberPlaybackController() else null
    val backgroundSong = rememberBackgroundSong(playbackController)
    var miniPlayerHeightDp by remember { mutableStateOf(0.dp) }
    // Without background songs there's no miniplayer, which can't say so itself any more
    LaunchedEffect(playerSettings.backgroundAudio) { if (!playerSettings.backgroundAudio) miniPlayerHeightDp = 0.dp }
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
    // The screen behind the search modal is blurred (Android 12 and up, see SearchModal), easing in and out
    val searchBlur by animateDpAsState(
        targetValue = if (showSearchModal && Build.VERSION.SDK_INT >= 31) SEARCH_BLUR_RADIUS else 0.dp,
        animationSpec = tween(if (reduceMotion) 100 else 250),
        label = "searchBlur"
    )
    // The details page reopens the file it showed; when that file can't be found, the Files screen is shown instead
    LaunchedEffect(Unit) {
        if (currentScreen == Screen.FileDetail && !fileInfoViewModel.restoreOpenedFile()) navigateTo(Screen.Files)
    }

    // Files shared from another app (or picked with the Upload shortcut, see uploadPicker) wait for the screen they go
    // into, whichever the user goes to: a bar at the bottom of the browse screens uploads them there, into the folder
    // that's open or as new files of the host (see SharedUploadBar). A host with only an Upload tab, which has no other
    // place for them, takes them there as picked files. Nothing uploads before the button is pressed, nor while the app
    // is locked, which covers the screens
    val pendingShares by appContainer.pendingShares.collectAsState()
    val appLocked by appContainer.appLock.locked.collectAsState()
    val uploadOnlyHost = navBarOrder.isNotEmpty() && navBarOrder.none { it.screen == Screen.Files || it.screen == Screen.Filesystem }
    LaunchedEffect(pendingShares, appLocked, uploadOnlyHost) {
        if (pendingShares.isEmpty() || appLocked) return@LaunchedEffect
        if (uploadOnlyHost) {
            val uris = pendingShares
            appContainer.pendingShares.value = emptyList()
            tabOf(Screen.Upload)?.let(selectTab)
            uploadViewModel.onFilesSelected(uris, context)
        } else if (currentScreen !in setOf(Screen.Files, Screen.Filesystem, Screen.Lists)) {
            // Opened on a page without the bar (the settings, a file's details): to the first place that takes them
            navBarOrder.firstOrNull { it.screen == Screen.Files || it.screen == Screen.Filesystem }?.let(selectTab)
        }
    }
    val sharedUpload = pendingShares.takeIf { it.isNotEmpty() && !uploadOnlyHost }?.let { uris ->
        SharedUpload(
            uris,
            onSent = { appContainer.pendingShares.value = emptyList() },
            onCancel = { appContainer.pendingShares.value = emptyList() }
        )
    }
    // The Upload shortcut and tile, on a host without an Upload tab: the files picked wait as shared ones do
    val uploadPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (uris.isNotEmpty()) appContainer.pendingShares.value = uris
    }
    // Whether the Files screen can upload into the active host (see UploadViewModel.uploadNow): its own files to put them in
    val filesCanUpload = remember(activeHostId, configChanges) {
        appContainer.providerRegistry.resolve(activeHostId).let { it.fileStore != null && ProviderCapability.UPLOAD in it.capabilities }
    }
    // A link, a shortcut or shared text (see PendingOpen), also once the app is unlocked. A Pixeldrain link opens on the
    // built-in Pixeldrain, whichever host is active
    val pendingOpen by appContainer.pendingOpen.collectAsState()
    LaunchedEffect(pendingOpen, appLocked) {
        val open = pendingOpen ?: return@LaunchedEffect
        if (appLocked) return@LaunchedEffect
        appContainer.pendingOpen.value = null
        when (open) {
            // Upload where the host has no Upload tab: the files are picked, then wait for their place as shared ones do
            is PendingOpen.ToScreen -> if (open.screen == Screen.Upload && tabOf(Screen.Upload) == null) {
                uploadPicker.launch("*/*")
            } else {
                tabOf(open.screen)?.let(selectTab) ?: navigateTo(open.screen)
            }
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
            // Shared text: on an upload-only host, into its Upload tab's text; elsewhere a text file, which waits for its
            // place as shared files do
            is PendingOpen.Text -> if (tabOf(Screen.Upload) != null && uploadOnlyHost) {
                uploadViewModel.onTextToUploadChanged(open.text)
                tabOf(Screen.Upload)?.let(selectTab)
            } else {
                appContainer.pendingShares.value = listOf(sharedTextFile(context, open.text))
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
    // A swipe to the file beside this one in the details (Look and feel, Swipe between files) moves like a change of page,
    // see the details below
    val fileSwap = remember { PageSwap() }
    val detailsLayer = rememberGraphicsLayer()
    var fileSwipeJob by remember { mutableStateOf<Job?>(null) }
    LaunchedEffect(currentScreen) {
        if (currentScreen != Screen.FileDetail) {
            fileSwipeJob?.cancel()
            fileSwap.reset()
        }
    }

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
                    dialog = AppDialog.UploadFailed(failed.message ?: res.getString(R.string.app_unknown_error))
                } else if (error != null && dialog == null && currentScreen == Screen.Upload) {
                    dialog = AppDialog.UploadError(error)
                }
            }
    }

    LaunchedEffect(uploadApiKeyMissing, currentScreen) {
        if (uploadApiKeyMissing && (currentScreen == Screen.Upload || currentScreen == Screen.Files)) dialog = AppDialog.UploadApiKeyRequired
    }
    // An upload which put new files on the host (the Files screen's, see UploadViewModel.uploadNow): its list shows them
    LaunchedEffect(uploadViewModel) {
        uploadViewModel.uiState.map { it.isLoading }.distinctUntilChanged().drop(1).collect { loading ->
            val state = uploadViewModel.uiState.value
            if (!loading && (state.queuedItems.any { it.status == UploadItemStatus.DONE } || state.uploadResult?.success == true)) {
                fileInfoViewModel.fetchUserFiles()
            }
        }
    }

    // A newer version, when the setting is on (see AppUpdates.checkOnOpening). Not before the app lock is opened: none of
    // this screen is composed while it's shut
    LaunchedEffect(Unit) {
        appContainer.appUpdates.checkWhatsNew()
        appContainer.appUpdates.checkOnOpening()
    }

    // The ViewModels' messages for the snackbar, one after another (see UiMessages), those of the app's texts read in
    // its language when they're shown
    val resources = LocalResources.current
    LaunchedEffect(Unit) {
        appContainer.uiMessages.messages.collect { message ->
            val result = snackbarHostState.showSnackbar(
                message.textRes?.let { resources.getString(it) } ?: message.text,
                actionLabel = message.action?.let { resources.getString(it.labelRes) },
                duration = if (message.long) SnackbarDuration.Long else SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed) {
                when (val action = message.action) {
                    is UiMessageAction.OpenDownload -> openDownloadedFile(context, action.download)
                    is UiMessageAction.Undo -> action.undo()
                    is UiMessageAction.Retry -> action.retry()
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

    // The screen's own button in the corner: Upload, or Save in the settings
    val catalog = settingsCatalog()
    val fabState by remember {
        derivedStateOf {
            screenFab(
                resources = resources,
                screen = currentScreen,
                canUpload = uploadUiState.hasUploadable && !uploadUiState.isLoading,
                onUpload = { if (uploadUiState.hasUploadable && !uploadUiState.isLoading) uploadViewModel.upload() },
                settingsHasSaveButton = catalog.firstOrNull { it.id == settingsCategoryId }?.hasSaveButton == true,
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
    // With one screen (or none) turned on there's nothing to navigate between, so the bar or strip would only take up space
    // A wide screen has the tabs at its side instead of the bottom bar, unless swipe navigation is chosen
    val useRail = isWideScreen() && !gestureNav && navBarOrder.size > 1
    val barLabels by appContainer.appSettings.barLabels.collectAsState()
    val swipeFiles by appContainer.appSettings.swipeBetweenFiles.collectAsState()
    // A bar at the bottom of the page shown (the selection's, or one of what waits for its place, see BrowserScreen),
    // which the FAB and the snackbars go up over
    val bottomBarRaised = pageBottomBars[shownNow.key] == true
    val fabLiftTarget = (if (bottomBarRaised) SelectionBarHeight else 0.dp) +
        (if (snackbarHeightDp > 0.dp) snackbarHeightDp + 8.dp else 0.dp)
    // The button lifts with the snackbar, on the same motion as its size
    val fabLift by animateDpAsState(targetValue = fabLiftTarget, animationSpec = snackbarMotion(reduceMotion), label = "fabLift")
    val isFabVisible = fabState != null

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
        // Menus open above the bottom bar or the tab strip (see MenuFloor)
        LocalMenuFloor provides remember { MenuFloor() },
        // The song card's badge opens the song player (see AudioPlayerView)
        LocalOpenAudioPlayer provides openAudioPlayer.takeIf { playerSettings.backgroundAudio },
        LocalSongCardShown provides onSongCardShown.takeIf { playerSettings.backgroundAudio },
        LocalAppHaptics provides appHaptics
    ) {
    SharedTransitionLayout {
        // The Box lets the song player sit on top of the Scaffold. Blurred as a whole while the search modal is open,
        // which is its own window and so stays sharp
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
            modifier = Modifier.weight(1f),
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
                            OverflowMenuButton(contentDescription = stringResource(R.string.common_more_options), buttonSize = 48.dp) { close ->
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
                                    onOpenSettings = { navigateTo(Screen.Settings) },
                                    view = screenViews[shownNow.key]
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
                // Above the upload button, so the button slides down behind the bar
                Column(modifier = Modifier.zIndex(2f)) {
                // The bottom bar's own visibility (or the tab strip's), which the miniplayer above it needs too
                val navigationBarShown = !useRail && currentScreen != Screen.FileDetail && navBarOrder.size > 1
                // The song playing in the background, above the bar. Not while items are selected, whose bar has the
                // bottom then, nor on the details of the song itself while they show its own card: once that's
                // scrolled away, it comes in
                if (playerSettings.backgroundAudio) {
                    BackgroundMiniPlayer(
                        song = backgroundSong,
                        visible = !selectingItems,
                        hiddenSongId = if (currentScreen == Screen.FileDetail && songCardShown) {
                            fileInfoUiState.fileInfo?.let { fileInfoViewModel.rawUrlFor(it) }
                        } else null,
                        aboveSystemBar = !navigationBarShown,
                        sheet = audioPlayerSheet,
                        modifier = Modifier.onSizeChanged { miniPlayerHeightDp = with(localDensity) { it.height.toDp() } }
                    )
                }
                AnimatedVisibility(
                    // With no screens to show there is nothing for the bar to hold
                    visible = navigationBarShown,
                    // The bar rises out of the bottom edge and sinks back into it, its icons with it: its top stays on the
                    // growing or shrinking space, which the page above follows
                    enter = if (reduceMotion) fadeIn(tween(100)) else fadeIn() + expandVertically(expandFrom = Alignment.Top),
                    exit = if (reduceMotion) fadeOut(tween(100)) else fadeOut() + shrinkVertically(shrinkTowards = Alignment.Top)
                ) {
                    if (gestureNav) {
                        TabStrip(currentTab, navBarOrder, onTabSelected = selectTab, modifier = Modifier.keepsMenusAbove())
                    } else {
                        BottomNavigationBar(
                            currentTab, navBarOrder, onTabSelected = selectTab, modifier = Modifier.keepsMenusAbove(), alwaysShowLabels = barLabels
                        )
                    }
                }
                }
            },
            floatingActionButton = {
                AppFab(fabState, lift = fabLift, reduceMotion = reduceMotion, onHeightChanged = { fabHeightDp = it })
            },
            snackbarHost = {
                // The snackbar stays at the bottom; while selecting it sits above the selection bar
                AppSnackbarHost(
                    hostState = snackbarHostState,
                    fabSize = fabHeightDp,
                    reduceMotion = reduceMotion,
                    modifier = Modifier
                        .zIndex(1f)
                        .offset(y = if (bottomBarRaised) -SelectionBarHeight else 0.dp)
                        .onSizeChanged { snackbarHeightDp = with(localDensity) { it.height.toDp() } }
                )
            }
        ) { paddingValues ->
            // A swipe sideways goes to the tab beside this one (Look and feel; always with swipe navigation), while
            // nothing else is open over the tab
            val swipeEnabled = (swipeTabs || gestureNav) && currentTab != null && navBarOrder.size > 1 && !noScreensConfigured &&
                !selectingItems && !showSearchModal
            AnimatedContent(
                targetState = shownNow,
                modifier = Modifier.sidewaysSwipe(swipeEnabled) { toNext ->
                    val index = navBarOrder.indexOf(currentTab)
                    val next = if (index == -1) null else navBarOrder.getOrNull(if (toNext) index + 1 else index - 1)
                    next?.let {
                        if (gestureNav) appHaptics.perform(HapticKind.THRESHOLD, HapticPlace.NAV_SWIPE)
                        selectTab(it)
                    }
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
                val bottomBarShown = !useRail && targetScreen != Screen.FileDetail && navBarOrder.size > 1
                val goesUnderBars = targetScreen in setOf(Screen.FileDetail, Screen.Files, Screen.Lists, Screen.Filesystem)
                val underNavigationBar = !bottomBarShown && goesUnderBars
                // The miniplayer floats over those screens, so what's behind it shows while it's swiped away; they still
                // add room for it after their last item, so nothing stays out of reach under it
                val bottomPadding = paddingValues.calculateBottomPadding()
                val behindMiniPlayer = if (goesUnderBars) miniPlayerHeightDp.coerceAtMost(bottomPadding) else 0.dp
                val layoutDirection = LocalLayoutDirection.current
                CompositionLocalProvider(
                    LocalBottomInset provides if (underNavigationBar) bottomPadding else behindMiniPlayer
                ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(
                            start = paddingValues.calculateStartPadding(layoutDirection),
                            top = paddingValues.calculateTopPadding(),
                            end = paddingValues.calculateEndPadding(layoutDirection),
                            bottom = if (underNavigationBar) 0.dp else bottomPadding - behindMiniPlayer
                        )
                ) {
                    val noScreensMessage = stringResource(R.string.app_has_no_tabs_to_show, activeConfig?.name ?: stringResource(R.string.app_this_host))
                    when (targetScreen) {
                        in BROWSE_SCREENS if noScreensConfigured -> CenteredTextMessage(noScreensMessage)
                        Screen.Upload -> UploadScreenContent(
                            uploadViewModel = uploadViewModel,
                            fabHeight = fabHeightDp,
                            isFabVisible = isFabVisible
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
                                folderScrollState = { path -> filesystemListStates.of("${shown.hostId}:${targetTab?.id}:$path") },
                                onFileSelected = { navigateTo(Screen.FileDetail) },
                                fabHeight = fabHeightDp,
                                isFabVisible = isFabVisible,
                                onActionsChange = { screenActions[shown.key] = it },
                                viewKey = shown.key,
                                onViewChange = { screenViews[shown.key] = it },
                                sharedUpload = sharedUpload,
                                onUploadFiles = if (targetScreen == Screen.Files && filesCanUpload &&
                                    ProviderCapability.UPLOAD !in targetTab?.config?.disabledCapabilities.orEmpty()
                                ) { uris -> uploadViewModel.uploadNow(uris, context) } else null,
                                onBottomBarChange = { pageBottomBars[shown.key] = it }
                            )
                            DisposableEffect(shown.key) {
                                onDispose {
                                    screenActions.remove(shown.key)
                                    screenViews.remove(shown.key)
                                    pageBottomBars.remove(shown.key)
                                }
                            }
                        }
                        Screen.FileDetail -> Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .sidewaysSwipe(swipeFiles && shown == shownNow) { toNext ->
                                    if (fileSwipeJob?.isActive != true && fileInfoViewModel.hasNeighbour(toNext)) {
                                        fileSwipeJob = coroutineScope.launch {
                                            // The page of this file stays as a picture while the next one is drawn
                                            // behind it (shown at once from the list's entry, see openNeighbour), then
                                            // the two cross as the screens do
                                            fileSwap.capture(detailsLayer, forward = toNext, reduceMotion = reduceMotion)
                                            fileInfoViewModel.openNeighbour(toNext)
                                            withFrameNanos { }
                                            fileSwap.run()
                                        }
                                    }
                                }
                                .graphicsLayer {
                                    // Reduced animations: the page stays as it is on the back gesture until it goes
                                    val back = if (reduceMotion) 0f else detailsBackProgress
                                    scaleX = 1f - back * 0.1f
                                    scaleY = 1f - back * 0.1f
                                    shape = AppShapes.Card
                                    clip = back > 0f
                                }
                        ) {
                            with(fileSwap) { OutgoingPage() }
                            Box(modifier = with(fileSwap) { Modifier.fillMaxSize().page(detailsLayer) }) {
                                fileInfoUiState.fileInfo?.let { info ->
                                    FileInfoDetailsCard(
                                        fileInfo = info,
                                        fileInfoViewModel = fileInfoViewModel,
                                        context = LocalContext.current,
                                        snackbarHostState = snackbarHostState,
                                        swipeBetweenFiles = swipeFiles
                                    )
                                } ?: run {
                                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        if (fileInfoUiState.isLoadingFileInfo) {
                                            CircularProgressIndicator()
                                        } else {
                                            val errorMessage = fileInfoUiState.fileInfoErrorMessage ?: stringResource(R.string.app_file_details_not_available)
                                            Text(
                                                text = errorMessage,
                                                modifier = Modifier.padding(16.dp),
                                                textAlign = TextAlign.Center
                                            )
                                        }
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
        // Over everything of the app
        AudioPlayerView(audioPlayerSheet, backgroundSong, compact = playerSettings.compactPlayer)
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

        // A newer version, found on opening or by "Check for updates" (see AppUpdates)
        val updates = appContainer.appUpdates
        // What this version brought, once after an update (see AppUpdates.checkWhatsNew)
        updates.whatsNew.collectAsState().value?.let { changes ->
            WhatsNewSheet(updates.installed.versionName, changes, onClose = { updates.dismissWhatsNew() })
        }
        updates.offered.collectAsState().value?.let { update ->
            UpdateSheet(
                update = update,
                installed = updates.installed,
                download = updates.download.collectAsState().value,
                onUpdate = { updates.install(update) },
                onLater = { updates.dismiss() },
                onSkip = { updates.skip(update) }
            )
        }
    }
    }
}

private val SEARCH_BLUR_RADIUS = 16.dp
/** The screens which show the files of the active host, and so the host switcher in the top bar. */
internal val BROWSE_SCREENS = setOf(Screen.Upload, Screen.Files, Screen.Lists, Screen.Filesystem)
/** How often the hosts are checked while the app is in front, see HostHealth. */
private const val HOST_RECHECK_MILLIS = 3 * 60_000L
private const val SETTINGS_SAVED_DISPLAY_MILLIS = 2000L
