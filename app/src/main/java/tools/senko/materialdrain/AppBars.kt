package tools.senko.materialdrain

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import tools.senko.materialdrain.ui.icons.automirrored.filled.ArrowBack
import tools.senko.materialdrain.ui.icons.filled.MoreVert
import tools.senko.materialdrain.ui.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.flow.map
import tools.senko.materialdrain.files.DownloadStatus
import tools.senko.materialdrain.hosts.HostSwitcher
import tools.senko.materialdrain.files.FileInfoViewModel
import tools.senko.materialdrain.browser.BrowserMode
import tools.senko.materialdrain.filesystem.FilesystemViewModel
import tools.senko.materialdrain.settings.ui.SettingsScreenContent
import tools.senko.materialdrain.files.key
import tools.senko.materialdrain.browser.ScreenMenuEntries
import tools.senko.materialdrain.ui.LocalReduceMotion
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
import androidx.compose.ui.res.stringResource

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
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.files_back))
                    }
                    // Opposite the settings icon
                    else -> if (searchAvailable) {
                        IconButton(onClick = onSearch) {
                            Icon(Icons.Filled.Search, contentDescription = stringResource(R.string.app_search))
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
internal fun ColumnScope.TransferBarSlot(
    currentScreen: Screen,
    uploadViewModel: UploadViewModel,
    filesystemViewModel: FilesystemViewModel,
    fileInfoViewModel: FileInfoViewModel,
) {
    val uploadUiState by uploadViewModel.uiState.collectAsState()
    val filesystemUpload by filesystemViewModel.uploadProgress.collectAsState()
    val fileInfoUiState by fileInfoViewModel.uiState.collectAsState()
    val downloads by fileInfoViewModel.downloads.collectAsState()
    // The upload screen's upload, or the Files screen's (see UploadViewModel.uploadNow), on either
    val upload = if (uploadUiState.isLoading && (currentScreen == Screen.Upload || currentScreen == Screen.Files)) {
        TransferProgress(
            transferredBytes = uploadUiState.uploadedBytes,
            totalBytes = uploadUiState.uploadTotalSizeBytes,
            bytesPerSecond = uploadUiState.uploadSpeedBytesPerSec,
            etaSeconds = uploadUiState.uploadEtaSeconds,
            label = uploadUiState.queuedItems.takeIf { it.isNotEmpty() }?.let { items ->
                "${items.count { it.status == UploadItemStatus.DONE }} / ${items.size} files"
            }
        )
    } else null
    val activeTransfer: TransferProgress? = upload ?: when (currentScreen) {
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
                label = stringResource(R.string.app_of_files_done, batch.doneFiles, batch.totalFiles)
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
                        pending -> stringResource(R.string.app_starting_download)
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

// ---- Previews ----

/** The tabs of the built-in Pixeldrain, as the bottom bar has them. */
private val PreviewTabs = listOf(Screen.Files, Screen.Lists, Screen.Filesystem).map { Tab(it) }

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
        onFilesPicked = {},
        onClearPickedFile = {},
        onTextChange = {},
        onRemoveQueued = {},
        onClearQueued = {},
        onCancel = {}
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
