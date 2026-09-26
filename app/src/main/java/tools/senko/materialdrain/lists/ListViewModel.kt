package tools.senko.materialdrain.lists

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tools.senko.materialdrain.api.ApiResponse
import tools.senko.materialdrain.api.FileInfoResponse
import tools.senko.materialdrain.api.PixeldrainCoreApi
import tools.senko.materialdrain.api.PixeldrainUserApi
import tools.senko.materialdrain.api.UserList
import tools.senko.materialdrain.auth.SessionManager

data class ListsUiState(
    val isLoading: Boolean = false,
    val lists: List<UserList> = emptyList(),
    val errorMessage: String? = null,
    val apiKeyMissingError: Boolean = false,

    // The list which is currently opened and its files
    val openedList: UserList? = null,
    val isLoadingListFiles: Boolean = false,
    val listFiles: List<FileInfoResponse> = emptyList(),
    val listFilesErrorMessage: String? = null
)

class ListViewModel(
    private val coreApi: PixeldrainCoreApi,
    private val userApi: PixeldrainUserApi,
    private val sessionManager: SessionManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(ListsUiState())
    val uiState: StateFlow<ListsUiState> = _uiState.asStateFlow()

    private var apiKey: String = ""

    init {
        loadApiKey()
    }

    fun loadApiKey() {
        apiKey = sessionManager.currentApiKey()
        if (apiKey.isBlank()) {
            _uiState.update { it.copy(apiKeyMissingError = true, errorMessage = "API Key is missing. Please set it in Settings.", lists = emptyList()) }
        } else {
            _uiState.update { it.copy(apiKeyMissingError = false, errorMessage = null) }
            fetchUserLists()
        }
    }

    fun fetchUserLists() {
        if (apiKey.isBlank()) {
            _uiState.update { it.copy(errorMessage = "API Key is required to fetch lists.", isLoading = false) }
            return
        }
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            when (val response = userApi.getUserLists(apiKey)) {
                is ApiResponse.Success -> {
                    _uiState.update { it.copy(isLoading = false, lists = response.data.lists) }
                }
                is ApiResponse.Error -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = response.errorDetails.message ?: "Unknown error fetching lists"
                        )
                    }
                }
            }
        }
    }

    fun openList(list: UserList) {
        _uiState.update { it.copy(openedList = list, listFiles = emptyList(), listFilesErrorMessage = null) }
        fetchOpenedListFiles()
    }

    fun refreshOpenedList() = fetchOpenedListFiles()

    fun closeList() {
        _uiState.update { it.copy(openedList = null, listFiles = emptyList(), isLoadingListFiles = false, listFilesErrorMessage = null) }
    }

    private fun fetchOpenedListFiles() {
        val list = _uiState.value.openedList ?: return
        _uiState.update { it.copy(isLoadingListFiles = true, listFilesErrorMessage = null) }
        viewModelScope.launch {
            when (val response = coreApi.getList(list.id, apiKey)) {
                is ApiResponse.Success -> {
                    // Ignore the answer if another list was opened while this one was loading
                    _uiState.update {
                        if (it.openedList?.id != list.id) it
                        else it.copy(isLoadingListFiles = false, listFiles = response.data.files, openedList = list.copy(fileCount = response.data.fileCount))
                    }
                }
                is ApiResponse.Error -> {
                    _uiState.update {
                        if (it.openedList?.id != list.id) it
                        else it.copy(
                            isLoadingListFiles = false,
                            listFilesErrorMessage = response.errorDetails.message ?: response.errorDetails.value ?: "Unknown error fetching list."
                        )
                    }
                }
            }
        }
    }
}
