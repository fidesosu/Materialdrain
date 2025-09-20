package com.example.materialdrain.ui

import android.app.Application
import android.content.Context // Added for SharedPreferences
import android.graphics.BitmapFactory
import android.media.MediaPlayer // Added for Audio Preview
import android.net.Uri
import android.util.Log // Added for Audio Preview Logging
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale // Added for Image Preview
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage // Added for Image Preview
import com.example.materialdrain.network.FileInfoResponse
import com.example.materialdrain.network.PixeldrainApiService
import com.example.materialdrain.ui.theme.MaterialdrainTheme
import com.example.materialdrain.viewmodel.FileInfoViewModel
import com.example.materialdrain.viewmodel.UploadViewModel
import com.example.materialdrain.viewmodel.ViewModelFactory
import kotlinx.coroutines.android.awaitFrame
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import java.io.IOException // Added for Audio Preview
import java.text.DecimalFormat
import java.util.concurrent.TimeUnit

// SharedPreferences constants
private const val PREFS_NAME = "pixeldrain_prefs"
private const val API_KEY_PREF = "api_key"
private const val TAG_MEDIA_PLAYER = "MediaPlayerPreview" // Added for Audio Preview Logging

// Define the screens in the app
enum class Screen(val title: String, val icon: ImageVector) {
    Upload("Upload", Icons.Filled.FileUpload), // Changed Icon
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
    var showDialog by remember { mutableStateOf(false) }
    var dialogContent by remember { mutableStateOf("") }
    var dialogTitle by remember { mutableStateOf("") }

    val application = LocalContext.current.applicationContext as Application
    val pixeldrainApiService = remember { PixeldrainApiService() }
    val viewModelFactory = remember { ViewModelFactory(application, pixeldrainApiService) }

    val uploadViewModel: UploadViewModel = viewModel(factory = viewModelFactory)
    val fileInfoViewModel: FileInfoViewModel = viewModel(factory = viewModelFactory)

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(key1 = uploadViewModel) {
        uploadViewModel.uiState.collectLatest { uiState ->
            uiState.uploadResult?.let {
                if (!it.success) {
                    dialogTitle = "Upload Failed"
                    dialogContent = "Error: ${it.message ?: it.value ?: "Unknown error"}"
                    showDialog = true
                }
            }
            uiState.errorMessage?.let {
                // Only show dialog for general errors, not API key missing, preview or metadata errors
                if (!it.contains("API Key", ignoreCase = true) &&
                    !it.contains("preview", ignoreCase = true) &&
                    !it.contains("metadata", ignoreCase = true) &&
                    !showDialog &&
                    currentScreen == Screen.Upload) {
                    dialogTitle = "Upload Error"
                    dialogContent = it
                    showDialog = true
                }
            }
        }
    }

    val uploadUiState by uploadViewModel.uiState.collectAsState()
    if (uploadUiState.errorMessage?.contains("API Key is missing") == true && currentScreen != Screen.Settings) {
        LaunchedEffect(uploadUiState.errorMessage) {
            dialogTitle = "API Key Required"
            dialogContent = "Please set your API Key in the Settings screen."
            showDialog = true
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("") }) // Removed app title
        },
        bottomBar = {
            BottomNavigationBar(currentScreen) { screen ->
                currentScreen = screen
                if (screen != Screen.Files) {
                    fileInfoViewModel.clearFileInfoError()
                    fileInfoViewModel.clearUserFilesError()
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
        },
        floatingActionButton = {
            // No FAB for Upload screen; moved to a standard button within UploadScreenContent
            // Other screens could have FABs defined here if needed in the future.
        }
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues)) {
            when (currentScreen) {
                Screen.Upload -> UploadScreenContent(
                    uploadViewModel = uploadViewModel,
                    onShowDialog = { title, content ->
                        dialogTitle = title
                        dialogContent = content
                        showDialog = true
                    }
                )
                Screen.Files -> FilesScreenContent(fileInfoViewModel = fileInfoViewModel)
                Screen.Lists -> ListsScreenContent(
                    onShowDialog = { title, content ->
                        dialogTitle = title
                        dialogContent = content
                        showDialog = true
                    }
                )
                Screen.Settings -> SettingsScreenContent(
                    uploadViewModel = uploadViewModel,
                    fileInfoViewModel = fileInfoViewModel,
                    onShowDialog = { title, content ->
                        dialogTitle = title
                        dialogContent = content
                        showDialog = true
                    }
                )
            }
        }
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = {
                showDialog = false
                if (dialogTitle == "Upload Failed" || dialogTitle == "Upload Error"){
                    uploadViewModel.clearUploadResult()
                }
                if (dialogContent.contains("API Key") && uploadViewModel.uiState.value.errorMessage?.contains("API Key") == true) {
                     uploadViewModel.clearApiKeyError()
                }
            },
            title = { Text(dialogTitle) },
            text = { Text(dialogContent) },
            confirmButton = {
                Button(onClick = {
                    showDialog = false
                     if (dialogTitle == "Upload Failed" || dialogTitle == "Upload Error"){
                        uploadViewModel.clearUploadResult()
                    }
                    if (dialogContent.contains("API Key") && uploadViewModel.uiState.value.errorMessage?.contains("API Key") == true) {
                        uploadViewModel.clearApiKeyError()
                    }
                }) {
                    Text("OK")
                }
            }
        )
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
            // Reset all audio preview states on new file selection
            mediaPlayer?.release()
            mediaPlayer = null
            isPlaying = false
            currentPlaybackTimeMillis = 0L
            audioPreviewError = null
            isMediaPlayerPreparing = false
            isUserScrubbing = false
            userSeekPositionMillis = 0L
            playbackTimeAtDragStart = 0L
            initialTouchX = 0f
        }
    )

    DisposableEffect(key1 = uiState.selectedFileUri, key2 = selectedTabIndex, key3 = context) {
        var localMediaPlayerInstance: MediaPlayer? = null

        if (selectedTabIndex == 0 && uiState.selectedFileUri != null && uiState.selectedFileMimeType?.startsWith("audio/") == true) {
            Log.d(TAG_MEDIA_PLAYER, "DisposableEffect: Setting up MediaPlayer for: ${uiState.selectedFileUri}")

            isMediaPlayerPreparing = true
            mediaPlayer?.release()
            mediaPlayer = null
            isPlaying = false
            currentPlaybackTimeMillis = 0L
            audioPreviewError = null
            isUserScrubbing = false
            userSeekPositionMillis = 0L
            playbackTimeAtDragStart = 0L
            initialTouchX = 0f

            localMediaPlayerInstance = MediaPlayer()
            try {
                localMediaPlayerInstance.setDataSource(context, uiState.selectedFileUri!!)
                localMediaPlayerInstance.prepareAsync()

                localMediaPlayerInstance.setOnPreparedListener { preparedPlayer ->
                    Log.d(TAG_MEDIA_PLAYER, "MediaPlayer prepared: ${uiState.selectedFileUri}")
                    mediaPlayer = preparedPlayer
                    isMediaPlayerPreparing = false
                    audioPreviewError = null
                }
                localMediaPlayerInstance.setOnErrorListener { mp, what, extra ->
                    Log.e(TAG_MEDIA_PLAYER, "MediaPlayer error (what: $what, extra: $extra) for: ${uiState.selectedFileUri}")
                    audioPreviewError = "Cannot play this audio file (error $what, $extra)."
                    mp?.release()
                    if (mediaPlayer === mp || mediaPlayer === localMediaPlayerInstance) {
                        mediaPlayer = null
                    }
                    isMediaPlayerPreparing = false
                    isPlaying = false
                    currentPlaybackTimeMillis = 0L
                    isUserScrubbing = false
                    userSeekPositionMillis = 0L
                    playbackTimeAtDragStart = 0L
                    initialTouchX = 0f
                    true
                }
                localMediaPlayerInstance.setOnCompletionListener { mp ->
                    Log.d(TAG_MEDIA_PLAYER, "MediaPlayer playback completed for: ${uiState.selectedFileUri}")
                    isPlaying = false
                    currentPlaybackTimeMillis = uiState.audioDurationMillis ?: 0L
                    isUserScrubbing = false
                }
            } catch (e: Exception) {
                Log.e(TAG_MEDIA_PLAYER, "Exception during MediaPlayer setup: ${e.message}", e)
                audioPreviewError = when (e) {
                    is IOException -> "Error loading audio file (I/O)."
                    is IllegalStateException -> "Error initializing audio player (State)."
                    is SecurityException -> "Permission denied for audio file."
                    else -> "Unknown error setting up audio player."
                }
                localMediaPlayerInstance?.release()
                mediaPlayer = null
                isMediaPlayerPreparing = false
                isPlaying = false
                currentPlaybackTimeMillis = 0L
                isUserScrubbing = false
                userSeekPositionMillis = 0L
                playbackTimeAtDragStart = 0L
                initialTouchX = 0f
            }
        } else {
            Log.d(TAG_MEDIA_PLAYER, "DisposableEffect: Cleaning up MediaPlayer (not audio tab or no URI).")
            mediaPlayer?.release()
            mediaPlayer = null
            isPlaying = false
            currentPlaybackTimeMillis = 0L
            audioPreviewError = null
            isMediaPlayerPreparing = false
            isUserScrubbing = false
            userSeekPositionMillis = 0L
            playbackTimeAtDragStart = 0L
            initialTouchX = 0f
        }

        onDispose {
            Log.d(TAG_MEDIA_PLAYER, "DisposableEffect onDispose for ${uiState.selectedFileUri}, releasing players.")
            localMediaPlayerInstance?.release()
            mediaPlayer?.release()
            mediaPlayer = null
            isMediaPlayerPreparing = false
            isPlaying = false
            currentPlaybackTimeMillis = 0L
            isUserScrubbing = false
            userSeekPositionMillis = 0L
            playbackTimeAtDragStart = 0L
            initialTouchX = 0f
        }
    }

    LaunchedEffect(isPlaying, mediaPlayer, uiState.selectedFileUri, uiState.audioDurationMillis, isUserScrubbing) {
        if (isPlaying && !isUserScrubbing && mediaPlayer != null && mediaPlayer?.isPlaying == true) {
            while (isActive) {
                try {
                    if (!isUserScrubbing && mediaPlayer?.isPlaying == true) {
                        currentPlaybackTimeMillis = mediaPlayer?.currentPosition?.toLong() ?: currentPlaybackTimeMillis
                    }
                } catch (e: IllegalStateException) {
                    Log.e(TAG_MEDIA_PLAYER, "Error getting currentPosition: ${e.message}")
                    isPlaying = false
                    audioPreviewError = "Player error during playback."
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
                        if (index == 0) {
                            uploadViewModel.onTextToUploadChanged("")
                        } else {
                            uploadViewModel.onFileSelected(null, context)
                            mediaPlayer?.release()
                            mediaPlayer = null
                            isPlaying = false
                            currentPlaybackTimeMillis = 0L
                            audioPreviewError = null
                            isMediaPlayerPreparing = false
                        }
                        isUserScrubbing = false
                        userSeekPositionMillis = 0L
                        playbackTimeAtDragStart = 0L
                        initialTouchX = 0f
                    },
                    text = { Text(title) }
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            when (selectedTabIndex) {
                0 -> { // File Upload Tab Content
                    Button(
                        onClick = { filePickerLauncher.launch("*/*") },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.AttachFile, contentDescription = "Select File Icon")
                        Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                        Text(uiState.selectedFileName ?: "Select File")
                    }

                    if (uiState.selectedFileName != null) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Card(modifier = Modifier.fillMaxWidth()){
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("Selected File:", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 8.dp))
                                InfoRow("Name:", uiState.selectedFileName ?: "N/A")
                                uiState.uploadTotalSizeBytes?.let {
                                    InfoRow("Size:", formatSize(it))
                                }
                                uiState.selectedFileMimeType?.let {
                                    InfoRow("Type:", it)
                                }
                                if (uiState.selectedFileMimeType?.startsWith("audio/") == true) {
                                    uiState.audioDurationMillis?.let { InfoRow("Duration:", formatDurationMillis(it)) }
                                    uiState.audioBitrate?.let { InfoRow("Bitrate:", "${it / 1000} kbps") }
                                    uiState.audioArtist?.let { InfoRow("Artist:", it) }
                                    uiState.audioAlbum?.let { InfoRow("Album:", it) }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))

                        if (uiState.selectedFileUri != null && uiState.selectedFileMimeType?.startsWith("image/") == true) {
                            AsyncImage(
                                model = uiState.selectedFileUri,
                                contentDescription = "Selected image preview",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(200.dp)
                                    .padding(vertical = 8.dp)
                                    .clip(RoundedCornerShape(12.dp)),
                                contentScale = ContentScale.Fit
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                        }

                        if (uiState.selectedFileUri != null && uiState.selectedFileMimeType?.startsWith("audio/") == true) {
                            Card(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                                Column(modifier = Modifier.padding(bottom = 0.dp)) { // Ensure no extra bottom padding inside the card for the progress bar
                                    Row(
                                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        uiState.audioAlbumArt?.let { albumArtBytes ->
                                            val bitmap = remember(albumArtBytes) {
                                                try { BitmapFactory.decodeByteArray(albumArtBytes, 0, albumArtBytes.size) } catch (e: Exception) { null }
                                            }
                                            bitmap?.let {
                                                Image(bitmap = it.asImageBitmap(), contentDescription = "Album Art", modifier = Modifier.size(64.dp).clip(RoundedCornerShape(8.dp)), contentScale = ContentScale.Crop)
                                            } ?: Icon(Icons.Filled.MusicNote, contentDescription = "Album Art Placeholder", modifier = Modifier.size(64.dp))
                                        } ?: Icon(Icons.Filled.MusicNote, contentDescription = "Album Art Placeholder", modifier = Modifier.size(64.dp))

                                        Spacer(modifier = Modifier.width(16.dp))

                                        Column(modifier = Modifier.weight(1f)) {
                                            uiState.audioArtist?.let { Text(it, style = MaterialTheme.typography.titleSmall) }
                                            uiState.audioAlbum?.let { Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(bottom = 4.dp)) }
                                            Text(
                                                text = "${formatDurationMillis( (if (isUserScrubbing) userSeekPositionMillis else currentPlaybackTimeMillis).coerceAtMost(uiState.audioDurationMillis ?: 0L) )} / ${formatDurationMillis(uiState.audioDurationMillis ?: 0L)}",
                                                style = MaterialTheme.typography.labelMedium
                                            )
                                        }
                                        FilledTonalIconButton(
                                            onClick = {
                                                if (isMediaPlayerPreparing) return@FilledTonalIconButton
                                                mediaPlayer?.let { player ->
                                                    val totalDuration = uiState.audioDurationMillis ?: Long.MAX_VALUE
                                                    if (player.isPlaying) {
                                                        try {
                                                          player.pause()
                                                          isPlaying = false
                                                        } catch (e: IllegalStateException) {
                                                            Log.e(TAG_MEDIA_PLAYER, "Error pausing: ${e.message}", e)
                                                            audioPreviewError = "Player error on pause."
                                                        }
                                                    } else {
                                                        try {
                                                            val currentTimeToPlayFrom = if (isUserScrubbing) userSeekPositionMillis else currentPlaybackTimeMillis
                                                            if (currentTimeToPlayFrom >= totalDuration - 100 && totalDuration > 0 && !isUserScrubbing) {
                                                                player.seekTo(0)
                                                                currentPlaybackTimeMillis = 0L
                                                                userSeekPositionMillis = 0L
                                                            }
                                                            // Ensure seek happens if scrubbing changed position before play
                                                            if (isUserScrubbing || player.currentPosition.toLong() != currentTimeToPlayFrom) {
                                                                // player.seekTo(currentTimeToPlayFrom.toInt()) // Already handled by onDragEnd for scrubbing
                                                            }
                                                            player.start()
                                                            isPlaying = true
                                                            audioPreviewError = null
                                                        } catch (e: IllegalStateException) {
                                                            Log.e(TAG_MEDIA_PLAYER, "Error starting/resuming: ${e.message}", e)
                                                            audioPreviewError = "Could not play audio."
                                                        }
                                                    }
                                                } ?: run {
                                                    if (audioPreviewError == null && !isMediaPlayerPreparing) audioPreviewError = "Player not available. Try selecting file again."
                                                }
                                            },
                                            enabled = mediaPlayer != null && !isMediaPlayerPreparing,
                                            modifier = Modifier.size(48.dp)
                                        ) {
                                            Icon(if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, contentDescription = if (isPlaying) "Pause" else "Play")
                                        }
                                    }
                                    if (isMediaPlayerPreparing) {
                                        Text("Player preparing...", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 0.dp, start = 16.dp, end = 16.dp, bottom = 8.dp))
                                    }
                                    audioPreviewError?.let {
                                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 0.dp, start = 16.dp, end = 16.dp, bottom = 8.dp))
                                    }

                                    val totalDuration = uiState.audioDurationMillis ?: 0L
                                    val progressFraction = {
                                        val currentPos = if (isUserScrubbing) userSeekPositionMillis else currentPlaybackTimeMillis
                                        if (totalDuration > 0L) (currentPos.toFloat() / totalDuration.toFloat()).coerceIn(0f, 1f) else 0f
                                    }

                                    if (mediaPlayer != null || isMediaPlayerPreparing) {
                                        Box(
                                            contentAlignment = Alignment.BottomCenter, // Align progress bar to bottom
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(24.dp)
                                                .onSizeChanged { size -> progressBarWidthPx = size.width }
                                                .pointerInput(mediaPlayer, totalDuration, progressBarWidthPx) {
                                                    if (mediaPlayer == null || totalDuration <= 0L || progressBarWidthPx <= 0) return@pointerInput
                                                    detectHorizontalDragGestures(
                                                        onDragStart = { offset ->
                                                            isUserScrubbing = true
                                                            initialTouchX = offset.x
                                                            val initialPercentage = (offset.x / progressBarWidthPx).coerceIn(0f, 1f)
                                                            val tappedTimeMillis = (initialPercentage * totalDuration).toLong()

                                                            userSeekPositionMillis = tappedTimeMillis
                                                            playbackTimeAtDragStart = tappedTimeMillis // Base for relative drag
                                                        },
                                                        onHorizontalDrag = { change, _ ->
                                                            if (!isUserScrubbing) return@detectHorizontalDragGestures

                                                            val currentDragX = change.position.x
                                                            val dragDeltaX = currentDragX - initialTouchX
                                                            val timeDeltaMillis = (dragDeltaX / progressBarWidthPx) * totalDuration

                                                            userSeekPositionMillis = (playbackTimeAtDragStart + timeDeltaMillis).toLong().coerceIn(0L, totalDuration)
                                                            change.consume()
                                                        },
                                                        onDragEnd = {
                                                            if (!isUserScrubbing) return@detectHorizontalDragGestures
                                                            mediaPlayer?.let {
                                                                try {
                                                                    it.seekTo(userSeekPositionMillis.toInt())
                                                                    currentPlaybackTimeMillis = userSeekPositionMillis
                                                                } catch (e: IllegalStateException) {
                                                                    Log.e(TAG_MEDIA_PLAYER, "Error seeking on drag end: ${e.message}", e)
                                                                    audioPreviewError = "Player error during seek."
                                                                }
                                                            }
                                                            isUserScrubbing = false
                                                        },
                                                        onDragCancel = {
                                                            isUserScrubbing = false
                                                        }
                                                    )
                                                }
                                        ) {
                                            LinearProgressIndicator(
                                                progress = progressFraction,
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                        }
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                        }

                        val mimeType = uiState.selectedFileMimeType
                        val textContent = uiState.selectedFileTextContent
                        if (uiState.selectedFileUri != null && !textContent.isNullOrEmpty() && mimeType != null &&
                            (mimeType.startsWith("text/") ||
                             mimeType == "application/json" ||
                             mimeType == "application/xml" ||
                             mimeType == "application/javascript" ||
                             mimeType == "application/rss+xml" ||
                             mimeType == "application/atom+xml" ||
                             (mimeType == "application/octet-stream" &&
                              (uiState.selectedFileName?.endsWith(".txt", true) == true ||
                               uiState.selectedFileName?.endsWith(".log", true) == true ||
                               uiState.selectedFileName?.endsWith(".ini", true) == true ||
                               uiState.selectedFileName?.endsWith(".xml", true) == true ||
                               uiState.selectedFileName?.endsWith(".json", true) == true ||
                               uiState.selectedFileName?.endsWith(".js", true) == true ||
                               uiState.selectedFileName?.endsWith(".config", true) == true ||
                               uiState.selectedFileName?.endsWith(".md", true) == true ||
                               uiState.selectedFileName?.endsWith(".csv", true) == true))
                            )
                        ) {
                            Text("File Content Preview (First 4KB):", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
                            OutlinedTextField(
                                value = textContent,
                                onValueChange = {},
                                readOnly = true,
                                modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp, max = 200.dp).padding(vertical = 8.dp),
                                textStyle = MaterialTheme.typography.bodySmall
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                        if (uiState.errorMessage?.contains("preview", ignoreCase = true) == true || uiState.errorMessage?.contains("metadata", ignoreCase = true) == true){
                             Text(uiState.errorMessage!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(bottom = 8.dp))
                        }
                    }
                }
                1 -> { // Text Upload Tab Content
                    OutlinedTextField(
                        value = uiState.textToUpload,
                        onValueChange = { uploadViewModel.onTextToUploadChanged(it) },
                        label = { Text("Paste text content here") },
                        modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 150.dp),
                        maxLines = 10,
                        enabled = !uiState.isLoading
                    )
                }
            }

            val totalSizeBytes = uiState.uploadTotalSizeBytes
            val textToUpload = uiState.textToUpload
            val effectiveTotalSize = if (selectedTabIndex == 0) totalSizeBytes else if (textToUpload.isNotBlank()) textToUpload.toByteArray().size.toLong() else null

            if (uiState.isLoading) {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(8.dp))
                Text("Uploading...", style = MaterialTheme.typography.bodyMedium)
                if (effectiveTotalSize != null && effectiveTotalSize > 0) {
                    Text("Total size: ${formatSize(effectiveTotalSize)}", style = MaterialTheme.typography.bodySmall)
                }
            } else {
                uiState.uploadResult?.let { result ->
                    if(result.success) {
                        Text("Upload Successful! ID: ${result.id}", color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom = 8.dp), textAlign = TextAlign.Center)
                        if (effectiveTotalSize != null && effectiveTotalSize > 0) {
                             Text("Uploaded size: ${formatSize(effectiveTotalSize)}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(bottom = 8.dp), textAlign = TextAlign.Center)
                        }
                    }
                } ?: run {
                    if (effectiveTotalSize != null && effectiveTotalSize > 0 && uiState.uploadResult == null && (uiState.selectedFileUri != null || uiState.textToUpload.isNotBlank())) {
                        Text(
                            text = "Ready to upload. Total size: ${formatSize(effectiveTotalSize)}",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(bottom = 8.dp),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        val canUpload = (uiState.selectedFileUri != null || uiState.textToUpload.isNotBlank()) && !uiState.isLoading
        Button(
            onClick = { uploadViewModel.upload() },
            enabled = canUpload,
            modifier = Modifier.fillMaxWidth().padding(16.dp)
        ) {
            Icon(Icons.Filled.FileUpload, contentDescription = null)
            Spacer(Modifier.size(ButtonDefaults.IconSpacing))
            Text("Upload")
        }
    }
}

@Composable
fun FilesScreenContent(fileInfoViewModel: FileInfoViewModel) {
    val uiState by fileInfoViewModel.uiState.collectAsState()
    val context = LocalContext.current

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = uiState.fileIdInput,
            onValueChange = { fileInfoViewModel.onFileIdInputChange(it) },
            label = { Text("Enter File ID to view details") },
            modifier = Modifier.fillMaxWidth(),
            isError = uiState.fileInfoErrorMessage?.contains("Please enter a File ID") == true,
            enabled = !uiState.isLoadingFileInfo
        )
        uiState.fileInfoErrorMessage?.let {
            if (it != "Please enter a File ID.") {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Button(
                onClick = { fileInfoViewModel.fetchFileInfo() },
                enabled = uiState.fileIdInput.isNotBlank() && !uiState.isLoadingFileInfo,
                modifier = Modifier.weight(1f).padding(end = 4.dp)
            ) {
                if (uiState.isLoadingFileInfo) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                    Text("Fetching...")
                } else {
                    Text("Get File Info")
                }
            }
            Button(
                onClick = { fileInfoViewModel.clearFileInfoInput() },
                modifier = Modifier.weight(1f).padding(start = 4.dp),
                enabled = !uiState.isLoadingFileInfo
            ) {
                Text("Clear Input")
            }
        }
        Spacer(modifier = Modifier.height(16.dp))

        val currentFileInfo = uiState.fileInfo
        if (currentFileInfo != null) {
            FileInfoDetails(fileInfo = currentFileInfo)
        } else if (!uiState.isLoadingFileInfo && uiState.fileIdInput.isBlank() && uiState.fileInfoErrorMessage == null) {
             Text("Enter a file ID to see its details.", style = MaterialTheme.typography.bodySmall)
        }

        Spacer(modifier = Modifier.height(24.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(24.dp))

        var apiKey by remember { mutableStateOf("") }
        LaunchedEffect(Unit) {
            val sharedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            apiKey = sharedPrefs.getString(API_KEY_PREF, "") ?: ""
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Button(
                onClick = { fileInfoViewModel.fetchUserFiles(apiKey) },
                enabled = apiKey.isNotBlank() && !uiState.isLoadingUserFiles,
                modifier = Modifier.weight(1f).padding(end = 4.dp)
            ) {
                Text("Load My Files")
            }
            Button(
                onClick = { fileInfoViewModel.clearUserFilesList() },
                modifier = Modifier.weight(1f).padding(start = 4.dp),
                enabled = !uiState.isLoadingUserFiles
            ) {
                Text("Clear List")
            }
        }
        if (apiKey.isBlank()) {
            Text("API Key is missing. Please set it in Settings to load your files.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center, modifier = Modifier.padding(top=8.dp))
        }

        if (uiState.isLoadingUserFiles) {
            Spacer(modifier = Modifier.height(8.dp))
            CircularProgressIndicator()
        }

        uiState.userFilesListErrorMessage?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center, modifier = Modifier.padding(top=8.dp))
        }

        if (uiState.userFilesList.isNotEmpty()) {
            LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                items(uiState.userFilesList) { file ->
                    UserFileListItem(fileInfo = file)
                }
            }
        } else if (!uiState.isLoadingUserFiles && uiState.userFilesListErrorMessage == null && apiKey.isNotBlank()) {
            Text("No files found or click 'Load My Files'.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top=8.dp))
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
fun UserFileListItem(fileInfo: FileInfoResponse) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Description, contentDescription = "File type icon", modifier = Modifier.size(36.dp).padding(end = 12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(fileInfo.name, style = MaterialTheme.typography.titleSmall)
                Text(formatSize(fileInfo.size), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
fun FileInfoDetails(fileInfo: FileInfoResponse) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("File Details:", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 8.dp))
            InfoRow("ID:", fileInfo.id)
            InfoRow("Name:", fileInfo.name)
            InfoRow("Size:", formatSize(fileInfo.size))
            fileInfo.mimeType?.let { InfoRow("MIME Type:", it) }
            InfoRow("Upload Date:", fileInfo.dateUpload)
            fileInfo.views?.let { InfoRow("Views:", it.toString()) }
            fileInfo.downloads?.let { InfoRow("Downloads:", it.toString()) }
            fileInfo.bandwidthUsed?.let { InfoRow("Bandwidth Used (Free):", formatSize(it)) }
            fileInfo.bandwidthUsedPaid?.let { InfoRow("Bandwidth Used (Paid):", formatSize(it)) }
            fileInfo.dateLastView?.let { InfoRow("Last Viewed:", it) }
            fileInfo.thumbnailHref?.let { InfoRow("Thumbnail:", "pixeldrain.com${it}") }
            fileInfo.hashSha256?.let { InfoRow("SHA256:", it, true) }
            fileInfo.canEdit?.let { InfoRow("Can Edit:", it.toString()) }
            fileInfo.deleteAfterDate?.let { if(it != "0001-01-01T00:00:00Z") InfoRow("Deletes After:", it) }
            fileInfo.deleteAfterDownloads?.let { if(it > 0) InfoRow("Deletes After Downloads:", it.toString()) }
            fileInfo.availability?.let { InfoRow("Availability:", it) }
            fileInfo.availabilityMessage?.let { if(it.isNotBlank()) InfoRow("Avail. Message:", it) }
            fileInfo.canDownload?.let { InfoRow("Can Download:", it.toString()) }
            fileInfo.showAds?.let { InfoRow("Shows Ads:", it.toString()) }
            fileInfo.allowVideoPlayer?.let { InfoRow("Video Player:", it.toString()) }
            fileInfo.downloadSpeedLimit?.let { InfoRow("Speed Limit:", if(it == 0L) "None" else formatSize(it) + "/s") }
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
        Spacer(modifier = Modifier.height(24.dp))

        OutlinedTextField(value = apiKeyInput, onValueChange = { apiKeyInput = it }, label = { Text("Pixeldrain API Key") }, modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = {
                val sharedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                with(sharedPrefs.edit()) {
                    putString(API_KEY_PREF, apiKeyInput)
                    apply()
                }
                uploadViewModel.updateApiKey(apiKeyInput)
                fileInfoViewModel.fetchUserFiles(apiKeyInput)
                onShowDialog("Settings Saved", "API Key saved successfully.")
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = apiKeyInput.isNotBlank()
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
