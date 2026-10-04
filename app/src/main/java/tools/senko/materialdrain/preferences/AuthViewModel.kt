package tools.senko.materialdrain.preferences

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tools.senko.materialdrain.provider.pixeldrain.internal.ApiResponse
import tools.senko.materialdrain.provider.pixeldrain.internal.FileUploadResponse
import tools.senko.materialdrain.provider.pixeldrain.internal.LoginResult
import tools.senko.materialdrain.provider.pixeldrain.internal.PixeldrainUserApi
import tools.senko.materialdrain.auth.ApiKeySource
import tools.senko.materialdrain.auth.SessionManager

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
    private val userApi: PixeldrainUserApi,
    private val sessionManager: SessionManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        AuthUiState(loggedInUser = sessionManager.loggedInUser.value, activeSource = sessionManager.activeSource())
    )
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    private val _apiKeyChanged = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    /** Emits whenever the active API key changed because of a login or logout. */
    val apiKeyChanged: SharedFlow<Unit> = _apiKeyChanged.asSharedFlow()

    /** Username/password login, or e-mail login when [password] is empty. */
    fun login(username: String, password: String, totp: String) {
        if (_uiState.value.isBusy) return
        if (username.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Enter your username or e-mail address.", infoMessage = null) }
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
            _uiState.update { it.copy(errorMessage = "Paste the complete link from the e-mail, it contains link_login_user_id and link_login_id.", infoMessage = null) }
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
                                infoMessage = "Logged in as ${username.trim()}."
                            )
                        }
                        _apiKeyChanged.tryEmit(Unit)
                    }
                    LoginResult.LoginLinkSent -> _uiState.update {
                        it.copy(isBusy = false, loginLinkSent = true, infoMessage = "A login link was sent to your e-mail address. Paste it below to finish logging in.")
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
                    infoMessage = if (serverResult is ApiResponse.Error) "Logged out on this device, the server could not end the session." else "Logged out."
                )
            }
            _apiKeyChanged.tryEmit(Unit)
        }
    }

    /** Called when the manually entered API key was saved, so that the source shown in the UI is current. */
    fun refreshSource() {
        _uiState.update { it.copy(activeSource = sessionManager.activeSource(), loggedInUser = sessionManager.loggedInUser.value) }
    }

    private fun describeLoginError(error: FileUploadResponse): String = when (error.value) {
        "user_not_found" -> "No account exists with this username or e-mail address."
        "password_incorrect" -> "The password is not correct."
        "no_login_method_available" -> "Enter a password: this account has no e-mail address to send a login link to."
        "otp_required" -> "This account uses two-factor authentication. Enter the one-time password from your authenticator app."
        "otp_incorrect" -> "The one-time password is not correct."
        "login_link_already_sent" -> "A login link was already sent, wait for it to expire before requesting another."
        "invalid_login_link" -> "This login link is not valid, it may have expired."
        "invalid_email_address" -> "The e-mail address is not valid."
        "ip_rate_limit_reached" -> "Too many login attempts, try again later."
        else -> error.message ?: error.value ?: "Login failed."
    }
}
