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
    val apiKeyMissingError: Boolean = false
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

    private var apiKey: String = ""

    init {
        Log.d(TAG_FS_VM, "ViewModel init. Loading API Key and initial path.")
        loadApiKeyAndFetchCurrentPath()
    }

    private fun loadApiKeyAndFetchCurrentPath() {
        val sharedPrefs = application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        apiKey = sharedPrefs.getString(API_KEY_PREF, "") ?: ""
        Log.d(TAG_FS_VM, "API Key loaded: '${if (apiKey.isNotBlank()) "PRESENT" else "MISSING"}'")

        if (apiKey.isBlank()) {
            _uiState.update {
                it.copy(
                    apiKeyMissingError = true,
                    errorMessage = "API Key is missing. Please set it in Settings to browse the filesystem.",
                    isLoading = false
                )
            }
        } else {
            // If API key is present, clear potential missing key error and load the current path
            _uiState.update { it.copy(apiKeyMissingError = false, errorMessage = null) }
            fetchPathContent(uiState.value.currentPath) // Load initial path
        }
    }
    
    fun refreshCurrentPath() {
        if (apiKey.isBlank()) {
            _uiState.update {
                it.copy(
                    apiKeyMissingError = true,
                    errorMessage = "API Key is missing. Please set it in Settings to browse the filesystem.",
                    isLoading = false,
                    children = emptyList() // Clear children on refresh if API key is missing
                )
            }
            return
        }
        _uiState.update { it.copy(apiKeyMissingError = false, errorMessage = null) } // Clear previous errors
        fetchPathContent(uiState.value.currentPath)
    }

    fun updateApiKey() {
        val oldApiKey = apiKey
        val sharedPrefs = application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        apiKey = sharedPrefs.getString(API_KEY_PREF, "") ?: ""
        Log.d(TAG_FS_VM, "API Key updated via settings. New Key: '${if (apiKey.isNotBlank()) "PRESENT" else "MISSING"}'")
        if (apiKey.isNotBlank() && oldApiKey.isBlank()) {
            // API key was just added, clear errors and load content
            _uiState.update { it.copy(apiKeyMissingError = false, errorMessage = null) }
            fetchPathContent(uiState.value.currentPath)
        } else if (apiKey.isBlank()) {
            _uiState.update {
                it.copy(
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
                 // Should not happen if root is always "me"
                segmentName
            } else {
                "$currentBuiltPath/$segmentName"
            }
            pathObjects.add(PathSegment(segmentName, currentBuiltPath))
        }
        if (pathObjects.firstOrNull()?.name != FILESYSTEM_ROOT_PATH && segments.isNotEmpty() && segments.first() == FILESYSTEM_ROOT_PATH) {
            // This ensures "me" is always the first segment if the path starts with "me/"
            // It's mostly handled by the initial construction logic.
        } else if (pathObjects.isEmpty() && fullPath == FILESYSTEM_ROOT_PATH) {
             return listOf(PathSegment(FILESYSTEM_ROOT_PATH, FILESYSTEM_ROOT_PATH))
        } else if (pathObjects.isEmpty() && fullPath.isNotEmpty()) {
            // Path like "somefolder" without "me" prefix (should not happen with current API structure)
            return listOf(PathSegment(fullPath, fullPath))
        }
        return pathObjects
    }


    fun fetchPathContent(path: String) {
        if (apiKey.isBlank()) {
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

        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            Log.d(TAG_FS_VM, "Fetching content for path: $path with API key.")
            when (val response = pixeldrainApiService.getFilesystemPath(apiKey, path)) {
                is ApiResponse.Success -> {
                    val sortedChildren = response.data.children.sortedWith(
                        compareBy<FilesystemEntry> { it.type != "dir" } // Folders first
                        .thenByDescending { it.modified } // Then by modification date (newest first)
                        .thenBy { it.name.lowercase() } // Then by name
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
                            errorMessage = response.errorDetails.message ?: response.errorDetails.value ?: "Unknown error fetching filesystem.",
                            // Optionally, reset children or currentPath on error, or keep current state
                            // children = emptyList(), 
                            // currentPath = if (path != FILESYSTEM_ROOT_PATH) it.currentPath else FILESYSTEM_ROOT_PATH 
                        )
                    }
                }
            }
        }
    }

    fun navigateToChild(childEntry: FilesystemEntry) {
        if (childEntry.type == "dir") {
            // The child's 'path' field from the API is the full path to that child
            fetchPathContent(childEntry.path)
        } else {
            // Handle file click - e.g., show info, download. For now, just log.
            Log.i(TAG_FS_VM, "File clicked: ${childEntry.name} at path ${childEntry.path}. ID: ${childEntry.id}")
            _uiState.update { it.copy(errorMessage = "Clicked on file: ${childEntry.name}. File interaction not yet implemented.") }
        }
    }

    fun navigateToPathSegment(segment: PathSegment) {
        fetchPathContent(segment.fullPath)
    }
}
