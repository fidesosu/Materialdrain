package com.example.materialdrain.ui

import android.app.Application
import android.content.Context // Added for SharedPreferences
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.materialdrain.network.FileInfoResponse
import com.example.materialdrain.network.PixeldrainApiService
import com.example.materialdrain.ui.theme.MaterialdrainTheme
import com.example.materialdrain.viewmodel.FileInfoViewModel
import com.example.materialdrain.viewmodel.UploadViewModel
import com.example.materialdrain.viewmodel.ViewModelFactory
import kotlinx.coroutines.flow.collectLatest
import java.text.DecimalFormat

// SharedPreferences constants
private const val PREFS_NAME = "pixeldrain_prefs"
private const val API_KEY_PREF = "api_key"

// Define the screens in the app
enum class Screen(val title: String, val icon: ImageVector) {
    Upload("Upload", Icons.Filled.ArrowUpward),
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
                if (!it.contains("API Key") && !showDialog && currentScreen == Screen.Upload) {
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
            if (currentScreen == Screen.Upload) {
                FloatingActionButton(onClick = { uploadViewModel.upload() }) {
                    Icon(Icons.Filled.ArrowUpward, contentDescription = "Upload")
                }
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues)) {
            when (currentScreen) {
                Screen.Upload -> UploadScreenContent(
                    uploadViewModel = uploadViewModel,
                    onShowDialog = { title, content -> // Retained for future use if specific dialogs are needed from here
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

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { uri: Uri? ->
            if (uri != null) {
                uploadViewModel.onFileSelected(uri, context)
                // Ensure text field is cleared when a file is selected for this tab
                if (selectedTabIndex == 0) uploadViewModel.onTextToUploadChanged("")
            }
        }
    )

    Column(modifier = Modifier.fillMaxSize()) { // Main column for TabRow and content
        TabRow(selectedTabIndex = selectedTabIndex) {
            tabTitles.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTabIndex == index,
                    onClick = {
                        selectedTabIndex = index
                        if (index == 0) { // File tab selected
                            uploadViewModel.onTextToUploadChanged("") // Clear text input
                        } else { // Text tab selected
                            uploadViewModel.onFileSelected(null, context) // Clear file selection
                        }
                    },
                    text = { Text(title) }
                )
            }
        }

        // Content area below tabs
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f) // Allow this column to take available space
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            when (selectedTabIndex) {
                0 -> { // File Upload Tab Content
                    Spacer(modifier = Modifier.height(16.dp))
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
                                // You could try to get and display MIME type here if available from ViewModel or Uri
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }
                1 -> { // Text Upload Tab Content
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = uiState.textToUpload,
                        onValueChange = { uploadViewModel.onTextToUploadChanged(it) },
                        label = { Text("Paste text content here") },
                        modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 150.dp),
                        maxLines = 10,
                        enabled = !uiState.isLoading
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }

            // Common Upload Status/Result Display
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
                    if (effectiveTotalSize != null && effectiveTotalSize > 0 && uiState.uploadResult == null) { 
                        Text(
                            text = "Ready to upload. Total size: ${formatSize(effectiveTotalSize)}", 
                            style = MaterialTheme.typography.bodyMedium, 
                            modifier = Modifier.padding(bottom = 8.dp),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
           // Spacer(modifier = Modifier.weight(1f)) // Removed to allow content to naturally flow; FAB is separate
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
        // Removed "View Single File Info" title
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
        Divider()
        Spacer(modifier = Modifier.height(24.dp))

        // Removed "My Files" title

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
        // Removed "Manage Lists" title
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
        // Removed "App Settings" title
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
