package tools.senko.materialdrain.lists

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tools.senko.materialdrain.auth.SessionManager
import tools.senko.materialdrain.provider.ProviderConfigStore
import tools.senko.materialdrain.provider.ProviderRegistry
import tools.senko.materialdrain.provider.api.ApiResponse
import tools.senko.materialdrain.provider.forDisplay
import tools.senko.materialdrain.provider.api.FileList
import tools.senko.materialdrain.provider.api.ProviderLog
import tools.senko.materialdrain.provider.api.ProviderKind
import tools.senko.materialdrain.provider.api.StorageNode

data class ListsUiState(
    val isLoading: Boolean = false,
    val lists: List<FileList> = emptyList(),
    val errorMessage: String? = null,
    val apiKeyMissingError: Boolean = false,

    // The list which is currently opened and its files
    val openedList: FileList? = null,
    val isLoadingListFiles: Boolean = false,
    val listFiles: List<StorageNode> = emptyList(),
    val listFilesErrorMessage: String? = null
)

/** The lists of files of the active provider: the overview, and the contents of the list which is opened. */
class ListViewModel(
    private val registry: ProviderRegistry,
    private val configStore: ProviderConfigStore,
    private val sessionManager: SessionManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(ListsUiState())
    val uiState: StateFlow<ListsUiState> = _uiState.asStateFlow()

    init {
        loadApiKey()
        viewModelScope.launch {
            configStore.changes.drop(1).collect {
                ProviderLog.i("Lists", "active host changed, reloading the lists")
                _uiState.update { it.copy(lists = emptyList(), openedList = null, listFiles = emptyList(), errorMessage = null) }
                loadApiKey()
            }
        }
    }

    private fun lists() = registry.resolve(configStore.activeProviderId.value).lists

    /** Pixeldrain's lists need the login; other hosts' lists come from their config. */
    private fun needsKeyButMissing(): Boolean {
        val provider = registry.resolve(configStore.activeProviderId.value)
        return provider.kind == ProviderKind.PIXELDRAIN && sessionManager.currentApiKey().isBlank()
    }

    fun loadApiKey() {
        if (needsKeyButMissing()) {
            _uiState.update { it.copy(apiKeyMissingError = true, errorMessage = "API Key is missing. Please set it in Settings.", lists = emptyList()) }
        } else {
            _uiState.update { it.copy(apiKeyMissingError = false, errorMessage = null) }
            fetchUserLists()
        }
    }

    fun fetchUserLists() {
        val ops = lists() ?: run {
            _uiState.update { it.copy(errorMessage = "This host has no lists.", isLoading = false) }
            return
        }
        if (needsKeyButMissing()) {
            _uiState.update { it.copy(errorMessage = "API Key is required to fetch lists.", isLoading = false) }
            return
        }
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        ProviderLog.d("Lists", "loading the lists of '${registry.resolve(configStore.activeProviderId.value).displayName}'")
        viewModelScope.launch {
            when (val response = ops.lists()) {
                is ApiResponse.Success -> {
                    ProviderLog.i("Lists", "loaded ${response.data.size} lists")
                    _uiState.update { it.copy(isLoading = false, lists = response.data) }
                }
                is ApiResponse.Error -> {
                    ProviderLog.e("Lists", "loading the lists failed (${response.error.code}): ${response.error.message}")
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = response.error.forDisplay().ifBlank { "Unknown error fetching lists" })
                    }
                }
            }
        }
    }

    fun openList(list: FileList) {
        _uiState.update { it.copy(openedList = list, listFiles = emptyList(), listFilesErrorMessage = null) }
        fetchOpenedListFiles()
    }

    fun refreshOpenedList() = fetchOpenedListFiles()

    fun closeList() {
        _uiState.update { it.copy(openedList = null, listFiles = emptyList(), isLoadingListFiles = false, listFilesErrorMessage = null) }
    }

    private fun fetchOpenedListFiles() {
        val list = _uiState.value.openedList ?: return
        val ops = lists() ?: run {
            _uiState.update { it.copy(listFilesErrorMessage = "This host has no lists.") }
            return
        }
        _uiState.update { it.copy(isLoadingListFiles = true, listFilesErrorMessage = null) }
        viewModelScope.launch {
            when (val response = ops.listContents(list.id)) {
                is ApiResponse.Success -> {
                    // Ignore the answer if another list was opened while this one was loading
                    _uiState.update {
                        if (it.openedList?.id != list.id) it
                        else it.copy(
                            isLoadingListFiles = false,
                            listFiles = response.data.files.sortedBy { it.name.lowercase() },
                            openedList = list.copy(fileCount = response.data.files.size, canEdit = response.data.canEdit, title = response.data.title)
                        )
                    }
                }
                is ApiResponse.Error -> _uiState.update {
                    if (it.openedList?.id != list.id) it
                    else it.copy(
                        isLoadingListFiles = false,
                        listFilesErrorMessage = response.error.forDisplay().ifBlank { "Unknown error fetching list." }
                    )
                }
            }
        }
    }
}
