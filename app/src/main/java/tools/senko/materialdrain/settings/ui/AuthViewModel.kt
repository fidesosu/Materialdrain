package tools.senko.materialdrain.settings.ui

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tools.senko.materialdrain.provider.pixeldrain.internal.ApiResponse
import tools.senko.materialdrain.provider.pixeldrain.internal.FileUploadResponse
import tools.senko.materialdrain.provider.pixeldrain.internal.LoginResult
import tools.senko.materialdrain.provider.pixeldrain.internal.PixeldrainUserApi
import tools.senko.materialdrain.auth.ApiKeySource
import tools.senko.materialdrain.auth.SessionManager
import android.content.res.Resources
import tools.senko.materialdrain.R

data class AuthUiState(
    val loggedInUser: String? = null,
    val activeSource: ApiKeySource = ApiKeySource.NONE,
    val isBusy: Boolean = false,
    val errorMessage: String? = null,
    val infoMessage: String? = null,
    // The server asked for a one-time password (two-factor authentication)
    val otpRequired: Boolean = false,
    // A login link was e-mailed, the user has to paste it to finish logging in
    val loginLinkSent: Boolean = false
)

class AuthViewModel(
    /** The app's texts (its messages) */
    private val resources: Resources,
    private val userApi: PixeldrainUserApi,
    private val sessionManager: SessionManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        AuthUiState(loggedInUser = sessionManager.loggedInUser.value, activeSource = sessionManager.activeSource())
    )
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    init {
        // A saved API key changes where the key comes from (see ApiKeySource); a login or logout says so itself below
        viewModelScope.launch {
            sessionManager.apiKey.drop(1).collect { refreshSource() }
        }
    }

    /** Username/password login, or e-mail login when [password] is empty. */
    fun login(username: String, password: String, totp: String) {
        if (_uiState.value.isBusy) return
        if (username.isBlank()) {
            _uiState.update { it.copy(errorMessage = resources.getString(R.string.settings_enter_your_username_or_e), infoMessage = null) }
            return
        }
        submitLogin(username, password, totp, linkLoginUserId = "", linkLoginId = "")
    }

    /** Completes an e-mail login with the link which was sent to the account. */
    fun completeLinkLogin(username: String, pastedLink: String, password: String, totp: String) {
        if (_uiState.value.isBusy) return
        val uri = try { Uri.parse(pastedLink.trim()) } catch (_: Exception) { null }
        val userId = uri?.getQueryParameter("link_login_user_id")
        val loginId = uri?.getQueryParameter("link_login_id")
        if (userId.isNullOrBlank() || loginId.isNullOrBlank()) {
            _uiState.update { it.copy(errorMessage = resources.getString(R.string.settings_paste_the_whole_link_from), infoMessage = null) }
            return
        }
        submitLogin(username, password, totp, userId, loginId)
    }

    private fun submitLogin(username: String, password: String, totp: String, linkLoginUserId: String, linkLoginId: String) {
        _uiState.update { it.copy(isBusy = true, errorMessage = null, infoMessage = null) }
        viewModelScope.launch {
            when (val response = userApi.login(username, password, totp, linkLoginUserId, linkLoginId)) {
                is ApiResponse.Success -> when (val result = response.data) {
                    is LoginResult.LoggedIn -> {
                        sessionManager.saveSession(result.session, username.trim())
                        _uiState.update {
                            AuthUiState(
                                loggedInUser = username.trim(),
                                activeSource = sessionManager.activeSource(),
                                infoMessage = resources.getString(R.string.settings_logged_in_as_2, username.trim())
                            )
                        }
                    }
                    LoginResult.LoginLinkSent -> _uiState.update {
                        it.copy(isBusy = false, loginLinkSent = true, infoMessage = resources.getString(R.string.settings_login_link_sent_paste_it))
                    }
                }
                is ApiResponse.Error -> _uiState.update {
                    it.copy(
                        isBusy = false,
                        otpRequired = it.otpRequired || response.errorDetails.value == "otp_required",
                        errorMessage = describeLoginError(response.errorDetails)
                    )
                }
            }
        }
    }

    /** Ends the login session on the server (best effort) and forgets it on this device. */
    fun logout() {
        if (_uiState.value.isBusy) return
        val sessionKey = if (sessionManager.activeSource() == ApiKeySource.LOGIN) sessionManager.currentApiKey() else ""
        _uiState.update { it.copy(isBusy = true, errorMessage = null, infoMessage = null) }
        viewModelScope.launch {
            val serverResult = if (sessionKey.isNotBlank()) userApi.logout(sessionKey) else null
            sessionManager.clearSession()
            _uiState.update {
                AuthUiState(
                    activeSource = sessionManager.activeSource(),
                    infoMessage = if (serverResult is ApiResponse.Error) resources.getString(R.string.settings_logged_out_here_the_server) else resources.getString(R.string.settings_logged_out)
                )
            }
        }
    }

    /** Saves the API key entered by hand, which is used while not logged in. */
    fun saveApiKey(apiKey: String) {
        // The ViewModels which use the key watch it themselves (see SessionManager.apiKey)
        sessionManager.saveManualApiKey(apiKey)
        _uiState.update {
            it.copy(
                errorMessage = null,
                infoMessage = when {
                    apiKey.isBlank() -> resources.getString(R.string.settings_api_key_removed)
                    sessionManager.activeSource() == ApiKeySource.LOGIN -> resources.getString(R.string.settings_api_key_saved_it_s)
                    else -> resources.getString(R.string.settings_api_key_saved)
                }
            )
        }
    }

    /** Keeps the source shown in the UI current. */
    private fun refreshSource() {
        _uiState.update { it.copy(activeSource = sessionManager.activeSource(), loggedInUser = sessionManager.loggedInUser.value) }
    }

    private fun describeLoginError(error: FileUploadResponse): String = when (error.value) {
        "user_not_found" -> resources.getString(R.string.settings_no_account_exists_with_this)
        "password_incorrect" -> resources.getString(R.string.settings_the_password_is_not_correct)
        "no_login_method_available" -> resources.getString(R.string.settings_enter_a_password_this_account)
        "otp_required" -> resources.getString(R.string.settings_enter_the_code_from_your)
        "otp_incorrect" -> resources.getString(R.string.settings_the_one_time_password_is)
        "login_link_already_sent" -> resources.getString(R.string.settings_a_link_was_already_sent)
        "invalid_login_link" -> resources.getString(R.string.settings_this_login_link_is_not)
        "invalid_email_address" -> resources.getString(R.string.settings_the_e_mail_address_is)
        "ip_rate_limit_reached" -> resources.getString(R.string.settings_too_many_login_attempts_try)
        else -> error.message ?: error.value ?: resources.getString(R.string.settings_login_failed)
    }
}
