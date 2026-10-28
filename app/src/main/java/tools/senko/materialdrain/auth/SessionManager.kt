package tools.senko.materialdrain.auth

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import tools.senko.materialdrain.provider.pixeldrain.internal.LoginSession

private const val TAG_SESSION = "SessionManager"

// The manually entered API key keeps living where it always has
private const val PREFS_NAME = "pixeldrain_prefs"
private const val API_KEY_PREF = "api_key"
// The manually entered key, encrypted; the plain one above is only read to move it over
private const val MANUAL_KEY_PREF = "manual_api_key_encrypted"

// The API key of a login session is stored encrypted, in a separate file which is excluded from backups
private const val SECURE_PREFS_NAME = "pixeldrain_secure_prefs"
private const val SESSION_KEY_PREF = "session_api_key_encrypted"
private const val SESSION_USER_PREF = "session_username"

enum class ApiKeySource { NONE, LOGIN, MANUAL }

/**
 * Single source of the API key used for authenticated requests. A key from a username/password
 * (or e-mail link) login takes precedence over a manually entered API key.
 */
class SessionManager(
    context: Context,
    /**
     * Only for the previews of the settings (see PreviewSettings): signed in as this user, with a made-up key. Nothing is
     * read from the device, where a preview has no saved login and no Android Keystore to read one with.
     */
    previewUser: String? = null
) {

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val securePrefs = appContext.getSharedPreferences(SECURE_PREFS_NAME, Context.MODE_PRIVATE)
    private val cipher = KeystoreCipher()

    @Volatile
    private var sessionApiKey: String? = if (previewUser != null) "preview" else loadSessionApiKey()

    private val _loggedInUser = MutableStateFlow(previewUser ?: if (sessionApiKey != null) securePrefs.getString(SESSION_USER_PREF, "") else null)
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

    // The key is kept encrypted, like the login session. Read once, then kept in memory: it's asked for on every request.
    private var manualKey: String? = null

    fun manualApiKey(): String {
        manualKey?.let { return it }
        val encrypted = securePrefs.getString(MANUAL_KEY_PREF, null)
        val key = when {
            encrypted != null -> cipher.decrypt(encrypted).orEmpty()
            // A key saved before it was encrypted: moved over once, and the plain copy is removed
            // (written here rather than with saveManualApiKey, which this runs before: it's read while this is constructed)
            else -> prefs.getString(API_KEY_PREF, "").orEmpty().trim().also { legacy ->
                if (legacy.isNotEmpty()) securePrefs.edit().putString(MANUAL_KEY_PREF, cipher.encrypt(legacy)).apply()
                prefs.edit().remove(API_KEY_PREF).apply()
            }
        }
        manualKey = key
        return key
    }

    fun saveManualApiKey(apiKey: String) {
        val trimmed = apiKey.trim()
        if (trimmed.isEmpty()) securePrefs.edit().remove(MANUAL_KEY_PREF).apply()
        else securePrefs.edit().putString(MANUAL_KEY_PREF, cipher.encrypt(trimmed)).apply()
        manualKey = trimmed
        _apiKey.value = currentApiKey()
    }

    /** The API key which authenticated requests should use right now, blank when not signed in. */
    fun currentApiKey(): String = sessionApiKey ?: manualApiKey()

    private val _apiKey = MutableStateFlow(currentApiKey())
    /**
     * [currentApiKey], as it changes with a login, a logout or a saved key. The ViewModels which use the key watch it
     * themselves, so whatever changes the key doesn't have to tell each of them.
     */
    val apiKey: StateFlow<String> = _apiKey.asStateFlow()

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
        _apiKey.value = currentApiKey()
    }

    /** Forgets the login session on this device (does not contact the server). */
    fun clearSession() {
        securePrefs.edit().clear().apply()
        sessionApiKey = null
        _loggedInUser.value = null
        _apiKey.value = currentApiKey()
    }
}
