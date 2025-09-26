package com.example.materialdrain.viewmodel

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.materialdrain.network.ApiResponse
import com.example.materialdrain.network.FilesystemEntry
import com.example.materialdrain.network.PixeldrainApiService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val PREFS_NAME = "pixeldrain_prefs"
private const val API_KEY_PREF = "api_key"
private const val TAG_FS_VM = "FilesystemViewModel"
const val FILESYSTEM_ROOT_PATH = "me"

data class FilesystemUiState(
    val isLoading: Boolean = false,
    val currentPath: String = FILESYSTEM_ROOT_PATH,
    val pathSegments: List<PathSegment> = listOf(PathSegment(FILESYSTEM_ROOT_PATH, FILESYSTEM_ROOT_PATH)), // Name and full path for breadcrumbs
    val children: List<FilesystemEntry> = emptyList(),
    val errorMessage: String? = null,
    val apiKeyMissingError: Boolean = false,
    val apiKey: String = "" // Exposed API Key
)

data class PathSegment(
    val name: String,
    val fullPath: String
)

class FilesystemViewModel(
    private val application: Application,
    private val pixeldrainApiService: PixeldrainApiService
) : ViewModel() {

    private val _uiState = MutableStateFlow(FilesystemUiState())
    val uiState: StateFlow<FilesystemUiState> = _uiState.asStateFlow()

    private var internalApiKey: String = ""

    init {
        Log.d(TAG_FS_VM, "ViewModel init. Loading API Key and initial path.")
        loadApiKeyAndFetchCurrentPath()
    }

    private fun loadApiKeyAndFetchCurrentPath() {
        val sharedPrefs = application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        internalApiKey = sharedPrefs.getString(API_KEY_PREF, "") ?: ""
        Log.d(TAG_FS_VM, "API Key loaded: '${if (internalApiKey.isNotBlank()) "PRESENT" else "MISSING"}'")

        if (internalApiKey.isBlank()) {
            _uiState.update {
                it.copy(
                    apiKey = "",
                    apiKeyMissingError = true,
                    errorMessage = "API Key is missing. Please set it in Settings to browse the filesystem.",
                    isLoading = false
                )
            }
        } else {
            _uiState.update {
                it.copy(
                    apiKey = internalApiKey,
                    apiKeyMissingError = false,
                    errorMessage = null
                )
            }
            fetchPathContent(uiState.value.currentPath)
        }
    }

    fun refreshCurrentPath() {
        if (internalApiKey.isBlank()) {
            _uiState.update {
                it.copy(
                    apiKeyMissingError = true,
                    errorMessage = "API Key is missing. Please set it in Settings to browse the filesystem.",
                    isLoading = false,
                    children = emptyList()
                )
            }
            return
        }
        _uiState.update { it.copy(apiKey = internalApiKey, apiKeyMissingError = false, errorMessage = null) }
        fetchPathContent(uiState.value.currentPath)
    }

    fun updateApiKey() {
        val oldApiKey = internalApiKey
        val sharedPrefs = application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        internalApiKey = sharedPrefs.getString(API_KEY_PREF, "") ?: ""
        Log.d(TAG_FS_VM, "API Key updated via settings. New Key: '${if (internalApiKey.isNotBlank()) "PRESENT" else "MISSING"}'")

        if (internalApiKey.isNotBlank()) {
            _uiState.update {
                it.copy(
                    apiKey = internalApiKey,
                    apiKeyMissingError = false,
                    errorMessage = if (oldApiKey.isBlank()) null else it.errorMessage
                )
            }
            if (oldApiKey.isBlank()) {
                fetchPathContent(uiState.value.currentPath)
            }
        } else { 
            _uiState.update {
                it.copy(
                    apiKey = "",
                    apiKeyMissingError = true,
                    errorMessage = "API Key is missing. Please set it in Settings.",
                    isLoading = false,
                    children = emptyList()
                )
            }
        }
    }

    private fun generatePathSegments(fullPath: String): List<PathSegment> {
        if (fullPath.isBlank() || fullPath == FILESYSTEM_ROOT_PATH) {
            return listOf(PathSegment(FILESYSTEM_ROOT_PATH, FILESYSTEM_ROOT_PATH))
        }
        val segments = fullPath.split('/').filter { it.isNotEmpty() }
        val pathObjects = mutableListOf<PathSegment>()
        var currentBuiltPath = ""
        segments.forEachIndexed { index, segmentName ->
            currentBuiltPath = if (index == 0 && segmentName == FILESYSTEM_ROOT_PATH) {
                FILESYSTEM_ROOT_PATH
            } else if (index == 0) {
                segmentName
            } else {
                "$currentBuiltPath/$segmentName"
            }
            pathObjects.add(PathSegment(segmentName, currentBuiltPath))
        }
        if (pathObjects.firstOrNull()?.name != FILESYSTEM_ROOT_PATH && segments.isNotEmpty() && segments.first() == FILESYSTEM_ROOT_PATH) {
            // This logic path seems to be intended to handle if "me" is not the first segment but path starts with "me"
            // however, the path construction ensures "me" is correctly handled if it's the first actual segment name.
        } else if (pathObjects.isEmpty() && fullPath == FILESYSTEM_ROOT_PATH) {
             return listOf(PathSegment(FILESYSTEM_ROOT_PATH, FILESYSTEM_ROOT_PATH))
        } else if (pathObjects.isEmpty() && fullPath.isNotEmpty()) {
            return listOf(PathSegment(fullPath, fullPath))
        }
        return pathObjects
    }

    fun fetchPathContent(path: String) {
        val currentApiKey = uiState.value.apiKey
        if (currentApiKey.isBlank()) {
            Log.w(TAG_FS_VM, "fetchPathContent called with blank API key for path: $path")
            _uiState.update {
                it.copy(
                    apiKeyMissingError = true,
                    errorMessage = "API Key is missing. Please set it in Settings.",
                    isLoading = false,
                    children = emptyList()
                )
            }
            return
        }

        _uiState.update { it.copy(isLoading = true, errorMessage = null, apiKeyMissingError = false) }
        viewModelScope.launch {
            Log.d(TAG_FS_VM, "Fetching content for path: $path with API key.")
            when (val response = pixeldrainApiService.getFilesystemPath(currentApiKey, path)) {
                is ApiResponse.Success -> {
                    val sortedChildren = response.data.children.sortedWith(
                        compareBy<FilesystemEntry> { it.type != "dir" }
                        .thenByDescending { it.modified }
                        .thenBy { it.name.lowercase() }
                    )
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            currentPath = path,
                            pathSegments = generatePathSegments(path),
                            children = sortedChildren,
                            errorMessage = null
                        )
                    }
                    Log.d(TAG_FS_VM, "Successfully fetched ${response.data.children.size} children for path: $path. First segment: ${uiState.value.pathSegments.firstOrNull()?.name}")
                }
                is ApiResponse.Error -> {
                    Log.e(TAG_FS_VM, "Error fetching path '$path': ${response.errorDetails.message ?: response.errorDetails.value}")
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = response.errorDetails.message ?: response.errorDetails.value ?: "Unknown error fetching filesystem."
                        )
                    }
                }
            }
        }
    }

    fun navigateToChild(childEntry: FilesystemEntry) {
        if (childEntry.type == "dir") {
            fetchPathContent(childEntry.path)
        } else {
            Log.i(TAG_FS_VM, "File clicked: ${childEntry.name} at path ${childEntry.path}. ID: ${childEntry.id}")
            _uiState.update { it.copy(errorMessage = "Clicked on file: ${childEntry.name}. File interaction not yet implemented.") }
        }
    }

    fun navigateToPathSegment(segment: PathSegment) {
        fetchPathContent(segment.fullPath)
    }

    fun navigateToParentPath(): Boolean {
        val currentPath = uiState.value.currentPath
        if (currentPath == FILESYSTEM_ROOT_PATH || currentPath.isBlank()) {
            Log.d(TAG_FS_VM, "Already at root or path is blank. Cannot navigate to parent.")
            return false // Already at root or invalid path
        }

        val parentPath = currentPath.substringBeforeLast('/', missingDelimiterValue = "").ifEmpty {
            // If substringBeforeLast returns empty (e.g., path was "me/somefile" and became "me"),
            // or if the path was just "somefile" (shouldn't happen with "me" prefix)
            // we check if the result is FILESYSTEM_ROOT_PATH directly or if we need to assign it.
            if (currentPath.contains('/')) FILESYSTEM_ROOT_PATH else "" // Go to root if there was a slash, else invalid
        }

        // Special case: if parentPath becomes empty AND currentPath was like "me/file", parent should be "me"
        if (parentPath.isEmpty() && currentPath.startsWith("$FILESYSTEM_ROOT_PATH/") && currentPath != FILESYSTEM_ROOT_PATH) {
             Log.d(TAG_FS_VM, "Calculated parent path for '$currentPath' is empty, but it's a child of root. Setting parent to root: $FILESYSTEM_ROOT_PATH")
             fetchPathContent(FILESYSTEM_ROOT_PATH)
             return true
        } else if (parentPath.isNotEmpty()) {
            Log.d(TAG_FS_VM, "Navigating from '$currentPath' to parent path: '$parentPath'")
            fetchPathContent(parentPath)
            return true
        } else {
            // This case might be hit if currentPath was something like "justafile" without "me/" prefix,
            // which shouldn't occur with the current API design. Or if at root and somehow logic fell through.
            Log.w(TAG_FS_VM, "Could not determine parent path for '$currentPath'. Staying at current path or considering it root.")
            // If we are at root according to primary check, this ensures we still return false.
            if (currentPath == FILESYSTEM_ROOT_PATH) return false
            // If path is not root but parentPath calculation failed to produce a valid parent, 
            // it might be an unhandled edge case or an invalid path state. Treating as cannot go further up.
            return false
        }
    }
}
