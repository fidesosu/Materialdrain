package tools.senko.materialdrain.provider

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import tools.senko.materialdrain.provider.api.ProviderLog
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import tools.senko.materialdrain.auth.KeystoreCipher
import tools.senko.materialdrain.provider.api.Credentials
import tools.senko.materialdrain.provider.api.ProviderConfig
import tools.senko.materialdrain.provider.api.ProviderConfigCodec
import tools.senko.materialdrain.provider.api.withMeta
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

private const val PREFS_NAME = "provider_prefs"
private const val SECURE_PREFS_NAME = "provider_secure_prefs"
private const val CONFIGS_KEY = "configs"
private const val ACTIVE_ID_KEY = "active_provider_id"

/** Stable id of the built-in, always-available Pixeldrain provider; never stored in [ProviderConfigStore]. */
const val PIXELDRAIN_PROVIDER_ID = "pixeldrain-default"

/**
 * One configured host.
 *
 * @param upstream the last version that came from the config's update URL (import or update); null when the
 *   config has no update URL. Comparing it with [config] is how edits are detected.
 * @param previous the version before the last update, for "revert"
 * @param keepAutoUpdateWhenEdited the user turned auto-update back on after an edit had turned it off, so
 *   further edits leave it on (it's only ever switched off automatically once)
 * @param pendingUpdate a newer version found by a check but not applied yet
 */
data class StoredProvider(
    val id: String,
    val config: ProviderConfig,
    val upstream: ProviderConfig? = null,
    val previous: ProviderConfig? = null,
    val autoUpdate: Boolean = false,
    val keepAutoUpdateWhenEdited: Boolean = false,
    val pendingUpdate: ProviderConfig? = null,
    val etag: String? = null,
    val lastCheckedMillis: Long = 0
) {
    val updateUrl: String? get() = config.meta?.updateUrl
    val isEdited: Boolean get() = upstream != null && config != upstream
}

/** What saving an edit did, so the UI can tell the user. */
data class EditOutcome(val nowEdited: Boolean, val autoUpdateTurnedOff: Boolean)

@Serializable
private data class StoredRecord(
    val config: String,
    val upstream: String? = null,
    val previous: String? = null,
    @SerialName("auto_update") val autoUpdate: Boolean = false,
    @SerialName("keep_auto_update_when_edited") val keepAutoUpdateWhenEdited: Boolean = false,
    @SerialName("pending_update") val pendingUpdate: String? = null,
    val etag: String? = null,
    @SerialName("last_checked") val lastCheckedMillis: Long = 0
)

/**
 * Persists custom host configs (as [ProviderConfigCodec] text, never with secrets) and their update state, plus
 * which provider is active. Secrets live in their own encrypted entry per provider id, the same Keystore-backed
 * pattern [tools.senko.materialdrain.auth.SessionManager] uses for the Pixeldrain login key.
 */
class ProviderConfigStore(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val securePrefs = appContext.getSharedPreferences(SECURE_PREFS_NAME, Context.MODE_PRIVATE)
    private val cipher = KeystoreCipher(alias = "materialdrain_provider_secrets_key")
    private val json = Json { ignoreUnknownKeys = true }

    private val _providers = MutableStateFlow(loadProviders())
    val providers: StateFlow<List<StoredProvider>> = _providers.asStateFlow()

    private val _activeProviderId = MutableStateFlow(prefs.getString(ACTIVE_ID_KEY, PIXELDRAIN_PROVIDER_ID) ?: PIXELDRAIN_PROVIDER_ID)
    val activeProviderId: StateFlow<String> = _activeProviderId.asStateFlow()

    fun get(id: String): StoredProvider? = _providers.value.firstOrNull { it.id == id }

    private fun loadProviders(): List<StoredProvider> {
        val raw = prefs.getString(CONFIGS_KEY, null) ?: return emptyList()
        val entries = try {
            json.parseToJsonElement(raw).jsonObject
        } catch (_: Exception) {
            return emptyList()
        }
        return entries.mapNotNull { (id, value) ->
            val record = try {
                // The first version of this store kept only the config text per id
                if (value is JsonPrimitive) StoredRecord(config = value.content)
                else json.decodeFromJsonElement(StoredRecord.serializer(), value)
            } catch (_: Exception) {
                return@mapNotNull null
            }
            val config = ProviderConfigCodec.decode(record.config) ?: return@mapNotNull null
            StoredProvider(
                id = id,
                config = config,
                upstream = record.upstream?.let(ProviderConfigCodec::decode),
                previous = record.previous?.let(ProviderConfigCodec::decode),
                autoUpdate = record.autoUpdate,
                keepAutoUpdateWhenEdited = record.keepAutoUpdateWhenEdited,
                pendingUpdate = record.pendingUpdate?.let(ProviderConfigCodec::decode),
                etag = record.etag,
                lastCheckedMillis = record.lastCheckedMillis
            )
        }
    }

    private fun persist(providers: List<StoredProvider>) {
        val records = providers.associate { p ->
            p.id to json.encodeToJsonElement(
                StoredRecord.serializer(),
                StoredRecord(
                    config = ProviderConfigCodec.encode(p.config),
                    upstream = p.upstream?.let(ProviderConfigCodec::encode),
                    previous = p.previous?.let(ProviderConfigCodec::encode),
                    autoUpdate = p.autoUpdate,
                    keepAutoUpdateWhenEdited = p.keepAutoUpdateWhenEdited,
                    pendingUpdate = p.pendingUpdate?.let(ProviderConfigCodec::encode),
                    etag = p.etag,
                    lastCheckedMillis = p.lastCheckedMillis
                )
            )
        }
        prefs.edit { putString(CONFIGS_KEY, JsonObject(records).toString()) }
        _providers.value = providers
        changed()
    }

    private fun update(id: String, change: (StoredProvider) -> StoredProvider): StoredProvider? {
        var result: StoredProvider? = null
        persist(_providers.value.map { if (it.id == id) change(it).also { changed -> result = changed } else it })
        return result
    }

    /**
     * Adds an imported config. Importing a config that is already here (same meta id and update URL) replaces
     * it instead, like a manual update, keeping its token and settings. Returns the id it's stored under.
     */
    fun import(config: ProviderConfig): String {
        val metaId = config.meta?.id
        val existing = metaId?.let { id ->
            _providers.value.firstOrNull { it.config.meta?.id == id && it.updateUrl == config.meta?.updateUrl }
        }
        val upstream = config.takeIf { it.meta?.updateUrl != null }
        if (existing != null) {
            update(existing.id) { it.copy(config = config, upstream = upstream, previous = it.config, pendingUpdate = null) }
            return existing.id
        }
        val id = UUID.randomUUID().toString()
        persist(_providers.value + StoredProvider(id = id, config = config, upstream = upstream))
        return id
    }

    /**
     * Saves a hand edit. If this makes the config differ from its upstream while auto-update is on, auto-update
     * is switched off, once: if the user turns it back on, later edits leave it alone.
     */
    fun saveEdit(id: String, config: ProviderConfig): EditOutcome {
        val before = get(id) ?: return EditOutcome(nowEdited = false, autoUpdateTurnedOff = false)
        val edited = before.copy(config = config)
        val turnOff = edited.isEdited && edited.autoUpdate && !edited.keepAutoUpdateWhenEdited
        update(id) { edited.copy(autoUpdate = if (turnOff) false else edited.autoUpdate) }
        return EditOutcome(nowEdited = edited.isEdited, autoUpdateTurnedOff = turnOff)
    }

    /** Detaches the config from its update URL: it becomes a purely local config. */
    fun removeUpdateLink(id: String) {
        update(id) {
            it.copy(
                config = it.config.withMeta(it.config.meta?.copy(updateUrl = null)),
                upstream = null,
                pendingUpdate = null,
                autoUpdate = false,
                keepAutoUpdateWhenEdited = false,
                etag = null
            )
        }
    }

    fun setAutoUpdate(id: String, enabled: Boolean) {
        update(id) {
            it.copy(autoUpdate = enabled, keepAutoUpdateWhenEdited = if (enabled) it.isEdited || it.keepAutoUpdateWhenEdited else it.keepAutoUpdateWhenEdited)
        }
    }

    fun recordCheck(id: String, pendingUpdate: ProviderConfig?, etag: String?, checkedAtMillis: Long) {
        update(id) { it.copy(pendingUpdate = pendingUpdate, etag = etag ?: it.etag, lastCheckedMillis = checkedAtMillis) }
    }

    /**
     * Applies [newConfig] (normally [StoredProvider.pendingUpdate]); the replaced version is kept for [revert].
     * With [clearSecret], the saved token is deleted: used when the update sends credentials somewhere new.
     */
    fun applyUpdate(id: String, newConfig: ProviderConfig, clearSecret: Boolean) {
        update(id) {
            it.copy(config = newConfig, upstream = newConfig, previous = it.config, pendingUpdate = null, keepAutoUpdateWhenEdited = false)
        }
        if (clearSecret) clearCredentials(id)
    }

    /**
     * Goes back to the version before the last update. Auto-update is turned off, otherwise the next check
     * would immediately re-apply what the user just reverted.
     */
    fun revert(id: String) {
        update(id) { current ->
            val previous = current.previous ?: return@update current
            current.copy(
                config = previous,
                upstream = previous.takeIf { it.meta?.updateUrl != null },
                previous = null,
                pendingUpdate = null,
                autoUpdate = false,
                etag = null
            )
        }
    }

    fun remove(id: String) {
        persist(_providers.value.filterNot { it.id == id })
        clearCredentials(id)
        if (_activeProviderId.value == id) setActive(PIXELDRAIN_PROVIDER_ID)
    }

    fun setActive(id: String) {
        prefs.edit { putString(ACTIVE_ID_KEY, id) }
        _activeProviderId.value = id
        changed()
    }

    /**
     * Counts every change of the hosts, the active host or their credentials. Screens which show a host's data
     * reload when it changes, so a new API key or a sign-in shows its files without a manual refresh.
     */
    private val _changes = MutableStateFlow(0)
    val changes: StateFlow<Int> = _changes.asStateFlow()

    private fun changed() {
        credentialCache.clear()
        _changes.update { it + 1 }
    }

    // --- Credentials: each value encrypted in its own entry; never part of a config ---

    // "secret" is the API key; the name predates usernames and passwords, and is kept so saved keys still load
    private fun credentialKey(kind: String, id: String) = "${kind}_$id"
    private val credentialKinds = listOf("secret", "username", "password", "login_token")

    private fun readEncrypted(kind: String, id: String): String? =
        securePrefs.getString(credentialKey(kind, id), null)?.let { cipher.decrypt(it) }

    private fun SharedPreferences.Editor.putEncrypted(kind: String, id: String, value: String?) {
        if (value.isNullOrEmpty()) remove(credentialKey(kind, id)) else putString(credentialKey(kind, id), cipher.encrypt(value))
    }

    // Decrypted values, kept until the next change: a request for every visible row would otherwise decrypt them all again
    private val credentialCache = ConcurrentHashMap<String, Credentials>()

    /** Blank values when nothing was saved, or it couldn't be decrypted (e.g. Keystore key lost after a backup restore). */
    fun credentials(id: String): Credentials = credentialCache.getOrPut(id) {
        Credentials(
            apiKey = readEncrypted("secret", id).orEmpty(),
            username = readEncrypted("username", id).orEmpty(),
            password = readEncrypted("password", id).orEmpty(),
            loginToken = readEncrypted("login_token", id)
        )
    }

    fun saveApiKey(id: String, apiKey: String) {
        securePrefs.edit { putEncrypted("secret", id, apiKey.trim()) }
        ProviderLog.i("Config", "saved the API key of host $id")
        changed()
    }

    /** For hosts that take the username and password with every request (no session), so the password is kept. */
    fun savePasswordCredentials(id: String, username: String, password: String) {
        securePrefs.edit {
            putEncrypted("username", id, username.trim())
            putEncrypted("password", id, password)
        }
        changed()
    }

    /**
     * A successful sign-in: keeps the session and the username (to show who's signed in), never the password,
     * like any app's normal sign-in. A password saved before sign-in worked this way is dropped here too.
     */
    fun saveSignIn(id: String, username: String, token: String) {
        securePrefs.edit {
            putEncrypted("username", id, username.trim())
            putEncrypted("login_token", id, token)
            putEncrypted("password", id, null)
        }
        ProviderLog.i("Config", "signed in to host $id as '${username.trim()}'")
        changed()
    }

    /** A new session, or null when it ended (signed out, or the host stopped accepting it). */
    fun saveLoginToken(id: String, token: String?) {
        securePrefs.edit { putEncrypted("login_token", id, token) }
        ProviderLog.i("Config", if (token == null) "signed out of host $id" else "saved a session for host $id")
        changed()
    }

    fun clearCredentials(id: String) {
        securePrefs.edit { credentialKinds.forEach { remove(credentialKey(it, id)) } }
        changed()
    }
}
