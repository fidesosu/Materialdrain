package com.example.materialdrain.viewmodel

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.materialdrain.network.ApiResponse
import com.example.materialdrain.network.PixeldrainApiService
import com.example.materialdrain.network.UserList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val PREFS_NAME = "pixeldrain_prefs"
private const val API_KEY_PREF = "api_key"
private const val TAG = "ListViewModel"

data class ListsUiState(
    val isLoading: Boolean = false,
    val lists: List<UserList> = emptyList(),
    val errorMessage: String? = null,
    val apiKeyMissingError: Boolean = false
)

class ListViewModel(
    private val application: Application,
    private val pixeldrainApiService: PixeldrainApiService
) : ViewModel() {

    private val _uiState = MutableStateFlow(ListsUiState())
    val uiState: StateFlow<ListsUiState> = _uiState.asStateFlow()

    private var apiKey: String = ""

    init {
        loadApiKey()
    }

    fun loadApiKey() {
        val sharedPrefs = application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        apiKey = sharedPrefs.getString(API_KEY_PREF, "") ?: ""
        if (apiKey.isBlank()) {
            _uiState.update { it.copy(apiKeyMissingError = true, errorMessage = "API Key is missing. Please set it in Settings.") }
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
            when (val response = pixeldrainApiService.getUserLists(apiKey)) {
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
}
