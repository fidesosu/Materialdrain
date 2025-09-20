package com.example.materialdrain.ui

import android.app.Application
import android.content.Context // Added for SharedPreferences
import android.content.Intent
import android.graphics.BitmapFactory
import android.media.MediaPlayer // Added for Audio Preview
import android.net.Uri
// import android.util.Log // Added for Audio Preview Logging
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize // Added for smooth content animation
import androidx.compose.animation.core.tween // Added for custom animation spec
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
// import androidx.compose.foundation.ExperimentalFoundationApi // REMOVED
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState // Added for scrolling to top
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState // Added for scrolling to top
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale // Added for Image Preview
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource // Added for painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset // Added for tween type
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage // Added for Image Preview
import com.example.materialdrain.network.FileInfoResponse
import com.example.materialdrain.network.PixeldrainApiService
import com.example.materialdrain.ui.theme.MaterialdrainTheme
import com.example.materialdrain.viewmodel.FileInfoViewModel
import com.example.materialdrain.viewmodel.SortableField // Import for sorting
import com.example.materialdrain.viewmodel.UploadViewModel
import com.example.materialdrain.viewmodel.ViewModelFactory
import kotlinx.coroutines.android.awaitFrame
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.IOException // Added for Audio Preview
import java.text.DecimalFormat
import java.util.Locale
import java.util.concurrent.TimeUnit
import androidx.compose.material3.MenuAnchorType // Added for MenuAnchorType

// SharedPreferences constants
private const val PREFS_NAME = "pixeldrain_prefs"
private const val API_KEY_PREF = "api_key"
private const val TAG_MEDIA_PLAYER = "MediaPlayerPreview" // Added for Audio Preview Logging

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

// Helper function to format duration in milliseconds to MM:SS or HH:MM:SS
internal fun formatDurationMillis(millis: Long): String {
    if (millis < 0) return "00:00"
    val hours = TimeUnit.MILLISECONDS.toHours(millis)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(millis) % TimeUnit.HOURS.toMinutes(1)
    val seconds = TimeUnit.MILLISECONDS.toSeconds(millis) % TimeUnit.MINUTES.toSeconds(1)
    return if (hours > 0) {
        String.format("%02d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
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
    val uploadUiState by uploadViewModel.uiState.collectAsState() // Collect UploadUiState

    // Observe UploadViewModel for general dialogs
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
            Column { // Column to hold TopAppBar and the progress indicator
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
                        0f // Indeterminate if total size is unknown or zero, or could be a specific indeterminate progress
                    }
                    if (uploadUiState.uploadTotalSizeBytes != null && uploadUiState.uploadTotalSizeBytes!! > 0L) {
                        LinearProgressIndicator(
                            progress = { progress }, // Ensure this lambda is used
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else { // Indeterminate progress if total size is unknown
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
            SnackbarHost(hostState = snackbarHostState) { data ->
                Snackbar(
                    snackbarData = data,
                    containerColor = MaterialTheme.colorScheme.inverseSurface,
                    contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                    actionColor = MaterialTheme.colorScheme.inversePrimary
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
}

@Composable
fun EnterFileIdDialog(fileInfoViewModel: FileInfoViewModel) {
    val uiState by fileInfoViewModel.uiState.collectAsState()

    AlertDialog(
        onDismissRequest = { fileInfoViewModel.dismissEnterFileIdDialog() },
        title = { Text("Search File by ID") },
        text = {
            Column {
                OutlinedTextField(
                    value = uiState.fileIdInput,
                    onValueChange = { fileInfoViewModel.onFileIdInputChange(it) },
                    label = { Text("Enter File ID") },
                    singleLine = true,
                    isError = uiState.fileInfoErrorMessage?.let { it.contains("Please enter", true) || it.contains("not found", true) || it.contains("error fetching", true) } == true,
                    keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = {
                        if (uiState.fileIdInput.isNotBlank()) {
                            fileInfoViewModel.fetchFileInfoFromDialogInput()
                        }
                    })
                )
                uiState.fileInfoErrorMessage?.let {
                    if (it != "Please enter or select a File ID.") {
                         Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (uiState.fileIdInput.isNotBlank()) {
                        fileInfoViewModel.fetchFileInfoFromDialogInput()
                    }
                },
                enabled = uiState.fileIdInput.isNotBlank() && !uiState.isLoadingFileInfo
            ) {
                if (uiState.isLoadingFileInfo) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp))
                } else {
                    Text("Search")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = { fileInfoViewModel.dismissEnterFileIdDialog() }) {
                Text("Cancel")
            }
        }
    )
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

@Composable
fun UploadScreenContent(uploadViewModel: UploadViewModel, onShowDialog: (String, String) -> Unit) {
    val uiState by uploadViewModel.uiState.collectAsState()
    val context = LocalContext.current
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val tabTitles = listOf("Upload File", "Upload Text")

    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var currentPlaybackTimeMillis by remember { mutableLongStateOf(0L) }
    var audioPreviewError by remember { mutableStateOf<String?>(null) }
    var isMediaPlayerPreparing by remember { mutableStateOf(false) }

    var isUserScrubbing by remember { mutableStateOf(false) }
    var userSeekPositionMillis by remember { mutableLongStateOf(0L) }
    var progressBarWidthPx by remember { mutableStateOf(0) }
    var playbackTimeAtDragStart by remember { mutableLongStateOf(0L) }
    var initialTouchX by remember { mutableFloatStateOf(0f) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { uri: Uri? ->
            uploadViewModel.onFileSelected(uri, context)
            if (uri != null && selectedTabIndex == 0) {
                uploadViewModel.onTextToUploadChanged("")
            }
            mediaPlayer?.release()
            mediaPlayer = null
            isPlaying = false
            currentPlaybackTimeMillis = 0L
            audioPreviewError = null
            isMediaPlayerPreparing = false
            isUserScrubbing = false
        }
    )

    DisposableEffect(key1 = uiState.selectedFileUri, key2 = selectedTabIndex, key3 = context) {
        var localMediaPlayerInstance: MediaPlayer? = null
        if (selectedTabIndex == 0 && uiState.selectedFileUri != null && uiState.selectedFileMimeType?.startsWith("audio/") == true) {
            isMediaPlayerPreparing = true
            mediaPlayer?.release()
            mediaPlayer = null
            isPlaying = false
            currentPlaybackTimeMillis = 0L
            audioPreviewError = null
            isUserScrubbing = false

            localMediaPlayerInstance = MediaPlayer()
            try {
                localMediaPlayerInstance.setDataSource(context, uiState.selectedFileUri!!)
                localMediaPlayerInstance.prepareAsync()
                localMediaPlayerInstance.setOnPreparedListener {
                    mediaPlayer = it
                    isMediaPlayerPreparing = false
                }
                localMediaPlayerInstance.setOnErrorListener { mp, what, extra ->
                    audioPreviewError = "Cannot play audio (error $what, $extra)."
                    mp?.release()
                    if (mediaPlayer == mp) mediaPlayer = null
                    isMediaPlayerPreparing = false
                    isPlaying = false
                    true
                }
                localMediaPlayerInstance.setOnCompletionListener {
                    isPlaying = false
                    currentPlaybackTimeMillis = uiState.audioDurationMillis ?: 0L
                }
            } catch (e: Exception) {
                audioPreviewError = "Error setting up audio player."
                localMediaPlayerInstance?.release()
                mediaPlayer = null
                isMediaPlayerPreparing = false
            }
        } else {
            mediaPlayer?.release()
            mediaPlayer = null
            isPlaying = false
            currentPlaybackTimeMillis = 0L
            audioPreviewError = null
            isMediaPlayerPreparing = false
            isUserScrubbing = false
        }
        onDispose {
            localMediaPlayerInstance?.release()
            mediaPlayer?.release()
            mediaPlayer = null
            isMediaPlayerPreparing = false
            isPlaying = false
            isUserScrubbing = false
        }
    }

    LaunchedEffect(isPlaying, mediaPlayer, isUserScrubbing) {
        if (isPlaying && !isUserScrubbing && mediaPlayer != null && mediaPlayer?.isPlaying == true) {
            while (isActive) {
                try {
                    if (!isUserScrubbing && mediaPlayer?.isPlaying == true) {
                        currentPlaybackTimeMillis = mediaPlayer?.currentPosition?.toLong() ?: currentPlaybackTimeMillis
                    }
                } catch (e: IllegalStateException) {
                    isPlaying = false
                    audioPreviewError = "Player error."
                    break
                }
                awaitFrame()
                if (!isPlaying || mediaPlayer?.isPlaying == false || isUserScrubbing) break
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = selectedTabIndex) {
            tabTitles.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTabIndex == index,
                    onClick = {
                        selectedTabIndex = index
                        if (index == 0) uploadViewModel.onTextToUploadChanged("")
                        else uploadViewModel.onFileSelected(null, context) 
                    },
                    text = { Text(title) }
                )
            }
        }
        Column(
            modifier = Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(16.dp))
            when (selectedTabIndex) {
                0 -> { 
                    Button(onClick = { filePickerLauncher.launch("*/*") }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Filled.AttachFile, contentDescription = "Select File")
                        Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                        Text(uiState.selectedFileName ?: "Select File")
                    }
                    uiState.selectedFileName?.let {
                        Spacer(modifier = Modifier.height(16.dp))
                        Card(modifier = Modifier.fillMaxWidth()){
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("Selected File:", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 8.dp))
                                InfoRow("Name:", uiState.selectedFileName ?: "N/A")
                                uiState.uploadTotalSizeBytes?.let { s -> InfoRow("Size:", formatSize(s)) }
                                uiState.selectedFileMimeType?.let { mt -> InfoRow("Type:", mt) }
                                if (uiState.selectedFileMimeType?.startsWith("audio/") == true) {
                                    uiState.audioDurationMillis?.let { d -> InfoRow("Duration:", formatDurationMillis(d)) }
                                    uiState.audioBitrate?.let { b -> InfoRow("Bitrate:", "${b / 1000} kbps") }
                                    uiState.audioArtist?.let { a -> InfoRow("Artist:", a) }
                                    uiState.audioAlbum?.let { al -> InfoRow("Album:", al) }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        if (uiState.selectedFileUri != null && uiState.selectedFileMimeType?.startsWith("image/") == true) {
                            AsyncImage(model = uiState.selectedFileUri, contentDescription = "Selected image preview", modifier = Modifier.fillMaxWidth().height(200.dp).padding(vertical = 8.dp).clip(RoundedCornerShape(12.dp)), contentScale = ContentScale.Fit)
                        }
                        if (uiState.selectedFileUri != null && uiState.selectedFileMimeType?.startsWith("audio/") == true) {
                            AudioPlayerPreview(
                                uiState = uiState, 
                                mediaPlayer = mediaPlayer, 
                                isPlaying = isPlaying, 
                                currentPlaybackTimeMillis = currentPlaybackTimeMillis, 
                                userSeekPositionMillis = userSeekPositionMillis, 
                                audioPreviewError = audioPreviewError, 
                                isMediaPlayerPreparing = isMediaPlayerPreparing, 
                                isUserScrubbing = isUserScrubbing, 
                                progressBarWidthPx = progressBarWidthPx,
                                onPlayPause = {
                                    if (isMediaPlayerPreparing) return@AudioPlayerPreview
                                    mediaPlayer?.let {
                                        if (it.isPlaying) { it.pause(); isPlaying = false }
                                        else {
                                            if(isUserScrubbing) { it.seekTo(userSeekPositionMillis.toInt()); currentPlaybackTimeMillis = userSeekPositionMillis}
                                            else if (currentPlaybackTimeMillis >= (uiState.audioDurationMillis ?: Long.MAX_VALUE) - 100 && (uiState.audioDurationMillis ?: 0L) > 0) {
                                                it.seekTo(0); currentPlaybackTimeMillis = 0L
                                            }
                                            it.start(); isPlaying = true; audioPreviewError = null
                                        }
                                    } ?: run { if (!isMediaPlayerPreparing) audioPreviewError = "Player not ready." }
                                },
                                onSeekBarWidthChanged = { progressBarWidthPx = it },
                                onDragStart = { offset, totalDuration, currentWidthPx ->
                                    isUserScrubbing = true
                                    initialTouchX = offset.x
                                    val initialPercentage = (offset.x / currentWidthPx).coerceIn(0f, 1f)
                                    userSeekPositionMillis = (initialPercentage * totalDuration).toLong()
                                    playbackTimeAtDragStart = userSeekPositionMillis
                                },
                                onDrag = { change, totalDuration, currentWidthPx ->
                                    val currentDragX = change.position.x
                                    val dragDeltaX = currentDragX - initialTouchX
                                    val timeDeltaMillis = (dragDeltaX / currentWidthPx) * totalDuration
                                    userSeekPositionMillis = (playbackTimeAtDragStart + timeDeltaMillis).toLong().coerceIn(0L, totalDuration)
                                    change.consume()
                                },
                                onDragEnd = {
                                    mediaPlayer?.seekTo(userSeekPositionMillis.toInt())
                                    currentPlaybackTimeMillis = userSeekPositionMillis
                                    isUserScrubbing = false
                                },
                                onDragCancel = { isUserScrubbing = false }
                            )
                        }
                        uiState.selectedFileTextContent?.takeIf { it.isNotBlank() }?.let {
                            Text("Content Preview (4KB Max):", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top=8.dp, bottom=4.dp))
                            OutlinedTextField(value = it, onValueChange = {}, readOnly = true, modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp, max = 200.dp).padding(vertical=8.dp), textStyle = MaterialTheme.typography.bodySmall)
                        }
                        if (uiState.errorMessage?.contains("preview", true) == true || uiState.errorMessage?.contains("metadata", true) == true) {
                            Text(uiState.errorMessage!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(bottom=8.dp))
                        }
                    }
                }
                1 -> { // Text Upload Tab
                    OutlinedTextField(value = uiState.textToUpload, onValueChange = uploadViewModel::onTextToUploadChanged, label = { Text("Paste text here") }, modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 150.dp), maxLines = 10, enabled = !uiState.isLoading)
                }
            }
            // REMOVED old CircularProgressIndicator and progress text from here
            if (!uiState.isLoading) { // Show success/ready messages only when not actively loading
                uiState.uploadResult?.let {
                    if (it.success) Text("Success! ID: ${it.id}", color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom=8.dp), textAlign = TextAlign.Center)
                } ?: run {
                    val effectiveSize = if (selectedTabIndex == 0) uiState.uploadTotalSizeBytes else uiState.textToUpload.toByteArray().size.toLong().takeIf { it > 0 }
                    if (effectiveSize != null && (uiState.selectedFileUri != null || uiState.textToUpload.isNotBlank())) {
                        Text("Ready to upload. Size: ${formatSize(effectiveSize)}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(bottom=8.dp), textAlign = TextAlign.Center)
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
        Button(onClick = uploadViewModel::upload, enabled = (uiState.selectedFileUri != null || uiState.textToUpload.isNotBlank()) && !uiState.isLoading, modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Icon(Icons.Filled.FileUpload, contentDescription = "Upload")
            Spacer(Modifier.size(ButtonDefaults.IconSpacing))
            Text("Upload")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SortControls(uiState: com.example.materialdrain.viewmodel.FileInfoUiState, fileInfoViewModel: FileInfoViewModel) {
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
            ?: (if (uiState.sortField == SortableField.UPLOAD_DATE && !uiState.sortAscending) "Date (Newest)" else "Sort by...")
    }

    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded },
            modifier = Modifier.weight(1f)
        ) {
            OutlinedTextField(
                value = currentSortOptionText,
                onValueChange = { },
                readOnly = true,
                label = { Text("Sort by") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true).fillMaxWidth()
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                sortOptions.forEach { selectionOption ->
                    DropdownMenuItem(
                        text = { Text(selectionOption.displayText) },
                        onClick = {
                            fileInfoViewModel.changeSortOrder(newField = selectionOption.field, newAscending = selectionOption.ascending)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun FilesScreenContent(
    fileInfoViewModel: FileInfoViewModel
) {
    val uiState by fileInfoViewModel.uiState.collectAsState()
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val filterFocusRequester = remember { FocusRequester() }
    val listState = rememberLazyListState()

    val displayedFiles by remember(uiState.userFilesList, uiState.sortField, uiState.sortAscending, uiState.filterQuery) {
        derivedStateOf {
            val filteredList = if (uiState.filterQuery.isBlank()) {
                uiState.userFilesList
            } else {
                uiState.userFilesList.filter {
                    it.name.contains(uiState.filterQuery, ignoreCase = true)
                }
            }

            val comparator = when (uiState.sortField) {
                SortableField.NAME -> compareBy<FileInfoResponse> { it.name.lowercase(Locale.getDefault()) }
                SortableField.SIZE -> compareBy { it.size }
                SortableField.UPLOAD_DATE -> compareBy { it.dateUpload }
            }
            
            if (uiState.sortAscending) {
                filteredList.sortedWith(comparator)
            } else {
                filteredList.sortedWith(comparator.reversed())
            }
        }
    }
    
    LaunchedEffect(uiState.showFilterInput) {
        if (uiState.showFilterInput) {
            filterFocusRequester.requestFocus()
            keyboardController?.show()
        } else {
        }
    }

    LaunchedEffect(uiState.sortField, uiState.sortAscending) {
        if (displayedFiles.isNotEmpty()) {
            listState.animateScrollToItem(0)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AnimatedVisibility(
            visible = uiState.showFilterInput,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            OutlinedTextField(
                value = uiState.filterQuery,
                onValueChange = { fileInfoViewModel.onFilterQueryChanged(it) },
                label = { Text("Filter by name") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
                    .focusRequester(filterFocusRequester),
                singleLine = true,
                keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {
                    keyboardController?.hide()
                }),
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

        if (uiState.isLoadingFileInfo && uiState.fileInfo == null && displayedFiles.isEmpty() && !uiState.showFilterInput) {
            Spacer(modifier = Modifier.height(16.dp))
            CircularProgressIndicator()
            Text("Fetching file info...", modifier = Modifier.padding(top = 8.dp))
        } else if (uiState.fileInfoErrorMessage != null &&
                 uiState.fileInfoErrorMessage != "Please enter or select a File ID." &&
                 !uiState.showEnterFileIdDialog &&
                 !uiState.isLoadingUserFiles && !uiState.showFilterInput) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                uiState.fileInfoErrorMessage!!,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (uiState.apiKeyMissingError) {
             Text(
                "API Key is missing. Please set it in Settings to load and manage your files.",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(vertical = 8.dp).fillMaxWidth()
            )
        } else {
            Button(
                onClick = { fileInfoViewModel.fetchUserFiles() },
                enabled = !uiState.isLoadingUserFiles,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (uiState.isLoadingUserFiles && displayedFiles.isEmpty() && uiState.filterQuery.isBlank()) {
                     CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                     Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                     Text("Loading Files...")
                } else {
                    Icon(Icons.Filled.Refresh, contentDescription = "Refresh Files")
                    Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                    Text(if (uiState.isLoadingUserFiles) "Refreshing..." else "Refresh My Files")
                }
            }
        }

        uiState.userFilesListErrorMessage?.let {
            if (!uiState.apiKeyMissingError) { 
                 Text(
                    it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(vertical = 8.dp).fillMaxWidth()
                )
            }
        }

        if (displayedFiles.isNotEmpty()) {
            SortControls(uiState = uiState, fileInfoViewModel = fileInfoViewModel)
            LazyColumn(
                state = listState, 
                modifier = Modifier.fillMaxWidth().weight(1f), 
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(
                    items = displayedFiles, 
                    key = { it.id },
                    contentType = { "fileInfoCard" }
                ) { file ->
                    Column(
                        modifier = Modifier.animateItem(fadeInSpec = null, fadeOutSpec = null, placementSpec = tween<IntOffset>(durationMillis = 700))
                    ) {
                        UserFileListItemCard(
                            fileInfo = file,
                            fileInfoViewModel = fileInfoViewModel,
                            context = context,
                            onClick = {
                                if (uiState.showFilterInput) {
                                    fileInfoViewModel.setFilterInputVisible(false)
                                }
                                fileInfoViewModel.fetchFileInfoById(file.id)
                            }
                        )
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    }
                }
            }
        } else if (!uiState.isLoadingUserFiles && !uiState.apiKeyMissingError && uiState.userFilesListErrorMessage == null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                if (uiState.filterQuery.isNotBlank()) "No files match your filter: \"${uiState.filterQuery}\"."
                else "No files found. Try refreshing.",
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top=8.dp)
            )
        }
    }
}

@Composable
fun FileInfoDetailsCard(fileInfo: FileInfoResponse, fileInfoViewModel: FileInfoViewModel, context: Context) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
            AsyncImage(
                model = "https://pixeldrain.com/api/file/${fileInfo.id}/thumbnail?width=128&height=128",
                contentDescription = "File thumbnail",
                modifier = Modifier.fillMaxWidth().heightIn(max = 200.dp).clip(RoundedCornerShape(8.dp)).align(Alignment.CenterHorizontally),
                contentScale = ContentScale.Fit,
                error = rememberVectorPainter(Icons.Filled.BrokenImage)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text("File Details:", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 8.dp))
            InfoRow("ID:", fileInfo.id, true)
            InfoRow("Name:", fileInfo.name)
            InfoRow("Size:", formatSize(fileInfo.size))
            fileInfo.mimeType?.let { InfoRow("MIME Type:", it) }
            InfoRow("Upload Date:", fileInfo.dateUpload)
            fileInfo.views?.let { InfoRow("Views:", it.toString()) }
            fileInfo.downloads?.let { InfoRow("Downloads:", it.toString()) }
            fileInfo.hashSha256?.let { InfoRow("SHA256:", it, true) }

            Spacer(modifier = Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                Button(onClick = {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://pixeldrain.com/api/file/${fileInfo.id}?download"))
                    context.startActivity(intent)
                }) {
                    Icon(Icons.Filled.Download, contentDescription = "Download")
                    Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                    Text("Download")
                }
                if (fileInfo.canEdit == true) {
                    Button(
                        onClick = { fileInfoViewModel.initiateDeleteFile(fileInfo.id) },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(Icons.Filled.Delete, contentDescription = "Delete")
                        Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                        Text("Delete")
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            TextButton(onClick = { fileInfoViewModel.clearFileInfoDisplay() }, modifier = Modifier.align(Alignment.End)) {
                Text("Close")
            }
        }
    }
}

@Composable
fun UserFileListItemCard(
    fileInfo: FileInfoResponse, 
    fileInfoViewModel: FileInfoViewModel, 
    context: Context,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable(onClick = onClick) 
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = "https://pixeldrain.com/api/file/${fileInfo.id}/thumbnail?width=64&height=64",
                contentDescription = "File thumbnail",
                modifier = Modifier.size(64.dp).clip(RoundedCornerShape(4.dp)),
                contentScale = ContentScale.Crop,
                error = rememberVectorPainter(Icons.Filled.BrokenImage)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(fileInfo.name, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                Text(formatSize(fileInfo.size), style = MaterialTheme.typography.bodySmall)
                Text("Uploaded: ${fileInfo.dateUpload.substringBefore('T')}", style = MaterialTheme.typography.bodySmall)
            }
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(onClick = {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://pixeldrain.com/api/file/${fileInfo.id}?download"))
                context.startActivity(intent)
            }) {
                Icon(Icons.Filled.Download, contentDescription = "Download File")
            }
            if (fileInfo.canEdit == true) {
                IconButton(onClick = { fileInfoViewModel.initiateDeleteFile(fileInfo.id) }) {
                    Icon(Icons.Filled.DeleteOutline, contentDescription = "Delete File", tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
fun AudioPlayerPreview(
    uiState: com.example.materialdrain.viewmodel.UploadUiState,
    mediaPlayer: MediaPlayer?,
    isPlaying: Boolean,
    currentPlaybackTimeMillis: Long,
    userSeekPositionMillis: Long, 
    audioPreviewError: String?,
    isMediaPlayerPreparing: Boolean,
    isUserScrubbing: Boolean,
    progressBarWidthPx: Int,
    onPlayPause: () -> Unit,
    onSeekBarWidthChanged: (Int) -> Unit,
    onDragStart: (offset: androidx.compose.ui.geometry.Offset, totalDuration: Long, progressBarWidthPx: Int) -> Unit,
    onDrag: (change: androidx.compose.ui.input.pointer.PointerInputChange, totalDuration: Long, progressBarWidthPx: Int) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Column(modifier = Modifier.padding(bottom = 0.dp)) {
            Row(
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                uiState.audioAlbumArt?.let {
                    val bitmap = remember(it) { try { BitmapFactory.decodeByteArray(it, 0, it.size) } catch (e: Exception) { null } }
                    bitmap?.let {bmp -> Image(bitmap = bmp.asImageBitmap(), contentDescription = "Album Art", modifier = Modifier.size(64.dp).clip(RoundedCornerShape(8.dp)), contentScale = ContentScale.Crop) }
                        ?: Icon(Icons.Filled.MusicNote, contentDescription = "Album Art Placeholder", modifier = Modifier.size(64.dp))
                } ?: Icon(Icons.Filled.MusicNote, contentDescription = "Album Art Placeholder", modifier = Modifier.size(64.dp))
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    uiState.audioArtist?.let { Text(it, style = MaterialTheme.typography.titleSmall) }
                    uiState.audioAlbum?.let { Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(bottom=4.dp)) }
                    Text(
                        text = "${formatDurationMillis( (if (isUserScrubbing) userSeekPositionMillis else currentPlaybackTimeMillis).coerceAtMost(uiState.audioDurationMillis ?: 0L) )} / ${formatDurationMillis(uiState.audioDurationMillis ?: 0L)}",
                        style = MaterialTheme.typography.labelMedium
                    )
                }
                FilledTonalIconButton(
                    onClick = onPlayPause,
                    enabled = mediaPlayer != null && !isMediaPlayerPreparing,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, contentDescription = if (isPlaying) "Pause" else "Play")
                }
            }
            if (isMediaPlayerPreparing) Text("Player preparing...", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top=0.dp, start=16.dp, end=16.dp, bottom=8.dp))
            audioPreviewError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top=0.dp, start=16.dp, end=16.dp, bottom=8.dp)) }

            val totalDuration = uiState.audioDurationMillis ?: 0L
            val progressFraction = { 
                val currentPos = if (isUserScrubbing) userSeekPositionMillis else currentPlaybackTimeMillis
                if (totalDuration > 0L) (currentPos.toFloat() / totalDuration.toFloat()).coerceIn(0f, 1f) else 0f
            }

            if (mediaPlayer != null || isMediaPlayerPreparing) {
                Box(
                    contentAlignment = Alignment.BottomCenter,
                    modifier = Modifier.fillMaxWidth().height(24.dp)
                        .onSizeChanged { onSeekBarWidthChanged(it.width) } 
                        .pointerInput(mediaPlayer, totalDuration, progressBarWidthPx) {
                            if (mediaPlayer == null || totalDuration <= 0L || progressBarWidthPx <= 0) return@pointerInput
                            detectHorizontalDragGestures(
                                onDragStart = { offset -> onDragStart(offset, totalDuration, progressBarWidthPx) },
                                onHorizontalDrag = { change, _ -> onDrag(change, totalDuration, progressBarWidthPx) },
                                onDragEnd = onDragEnd,
                                onDragCancel = onDragCancel
                            )
                        }
                ) {
                    LinearProgressIndicator(progress = progressFraction, modifier = Modifier.fillMaxWidth()) 
                }
            }
        }
    }
}

@Composable
fun InfoRow(label: String, value: String, isValueSelectable: Boolean = false) {
    Row(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(0.4f))
        if (isValueSelectable) {
             SelectionContainer {
                Text(value, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(0.6f))
            }
        } else {
            Text(value, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(0.6f))
        }
    }
}

@Composable
fun ListsScreenContent(onShowDialog: (String, String) -> Unit) {
    var listId by remember { mutableStateOf("") }
    var newListFileIds by remember { mutableStateOf("") }
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(value = newListFileIds, onValueChange = { newListFileIds = it }, label = { Text("Enter File IDs for new list (comma-separated)") }, modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(8.dp))
        Button(onClick = { onShowDialog("Create List", "Creating new list with files: $newListFileIds (Not Implemented)") }, modifier = Modifier.fillMaxWidth(), enabled = newListFileIds.isNotBlank()) {
            Text("Create New List")
        }
        Spacer(modifier = Modifier.height(24.dp))
        OutlinedTextField(value = listId, onValueChange = { listId = it }, label = { Text("Enter List ID to View") }, modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(8.dp))
        Button(onClick = { onShowDialog("View List", "Fetching details for List ID: $listId (Not Implemented)") }, modifier = Modifier.fillMaxWidth(), enabled = listId.isNotBlank()) {
            Text("View List Details")
        }
        Spacer(modifier = Modifier.weight(1f))
        Text("List functionality is not yet implemented.", style = MaterialTheme.typography.bodySmall)
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
fun SettingsScreenContent(
    uploadViewModel: UploadViewModel,
    fileInfoViewModel: FileInfoViewModel,
    onShowDialog: (String, String) -> Unit
) {
    var apiKeyInput by remember { mutableStateOf("") }
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        val sharedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        apiKeyInput = sharedPrefs.getString(API_KEY_PREF, "") ?: ""
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Pixeldrain Settings", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(bottom=24.dp))
        OutlinedTextField(value = apiKeyInput, onValueChange = { apiKeyInput = it }, label = { Text("Pixeldrain API Key") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = {
                val sharedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                with(sharedPrefs.edit()) {
                    putString(API_KEY_PREF, apiKeyInput.trim())
                    apply()
                }
                uploadViewModel.updateApiKey(apiKeyInput.trim())
                fileInfoViewModel.loadApiKey()
                onShowDialog("Settings Saved", "API Key saved successfully.")
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Save API Key")
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
