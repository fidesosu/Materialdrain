package tools.senko.materialdrain.auth

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import tools.senko.materialdrain.api.LoginSession

private const val TAG_SESSION = "SessionManager"

// The manually entered API key keeps living where it always has
private const val PREFS_NAME = "pixeldrain_prefs"
private const val API_KEY_PREF = "api_key"

// The API key of a login session is stored encrypted, in a separate file which is excluded from backups
private const val SECURE_PREFS_NAME = "pixeldrain_secure_prefs"
private const val SESSION_KEY_PREF = "session_api_key_encrypted"
private const val SESSION_USER_PREF = "session_username"

enum class ApiKeySource { NONE, LOGIN, MANUAL }

/**
 * Single source of the API key used for authenticated requests. A key from a username/password
 * (or e-mail link) login takes precedence over a manually entered API key.
 */
class SessionManager(context: Context) {

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val securePrefs = appContext.getSharedPreferences(SECURE_PREFS_NAME, Context.MODE_PRIVATE)
    private val cipher = KeystoreCipher()

    @Volatile
    private var sessionApiKey: String? = loadSessionApiKey()

    private val _loggedInUser = MutableStateFlow(if (sessionApiKey != null) securePrefs.getString(SESSION_USER_PREF, "") else null)
    /** Username used to log in, null when there is no login session. */
    val loggedInUser: StateFlow<String?> = _loggedInUser.asStateFlow()

    private fun loadSessionApiKey(): String? {
        val encrypted = securePrefs.getString(SESSION_KEY_PREF, null) ?: return null
        val decrypted = cipher.decrypt(encrypted)
        if (decrypted.isNullOrBlank()) {
            Log.w(TAG_SESSION, "Stored login session is unreadable, discarding it.")
            securePrefs.edit().clear().apply()
            return null
        }
        return decrypted
    }

    fun manualApiKey(): String = prefs.getString(API_KEY_PREF, "") ?: ""

    fun saveManualApiKey(apiKey: String) {
        prefs.edit().putString(API_KEY_PREF, apiKey.trim()).apply()
    }

    /** The API key which authenticated requests should use right now, blank when not signed in. */
    fun currentApiKey(): String = sessionApiKey ?: manualApiKey()

    fun activeSource(): ApiKeySource = when {
        sessionApiKey != null -> ApiKeySource.LOGIN
        manualApiKey().isNotBlank() -> ApiKeySource.MANUAL
        else -> ApiKeySource.NONE
    }

    fun saveSession(session: LoginSession, username: String) {
        securePrefs.edit()
            .putString(SESSION_KEY_PREF, cipher.encrypt(session.authKey))
            .putString(SESSION_USER_PREF, username)
            .apply()
        sessionApiKey = session.authKey
        _loggedInUser.value = username
    }

    /** Forgets the login session on this device (does not contact the server). */
    fun clearSession() {
        securePrefs.edit().clear().apply()
        sessionApiKey = null
        _loggedInUser.value = null
    }
}
