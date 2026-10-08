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
private const val LEGACY_FIRST_LINE = "MATERIALDRAIN-PROVIDER-CONFIG-V1"

/** Stable id of the built-in, always-available Pixeldrain provider; never stored in [ProviderConfigStore]. */
const val PIXELDRAIN_PROVIDER_ID = "pixeldrain-default"

/**
 * A config and the text it was read from. The text is what's saved, exported and edited, so fields the app doesn't read
 * (notes, fields left at their defaults, fields for a newer app) are kept rather than dropped.
 */
data class ConfigSource(val config: ProviderConfig, val text: String) {
    companion object {
        /** Null when [text] isn't a config. */
        fun of(text: String): ConfigSource? = ProviderConfigCodec.decode(text)?.let { ConfigSource(it, text.trim()) }
    }
}

/**
 * One configured host.
 *
 * @param text the config as written, see [ConfigSource]
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
    val text: String,
    val upstream: ProviderConfig? = null,
    val previous: ConfigSource? = null,
    val autoUpdate: Boolean = false,
    val keepAutoUpdateWhenEdited: Boolean = false,
    val pendingUpdate: ConfigSource? = null,
    val etag: String? = null,
    val lastCheckedMillis: Long = 0
) {
    val updateUrl: String? get() = config.meta?.updateUrl
    val isEdited: Boolean get() = upstream != null && config != upstream
    val source: ConfigSource get() = ConfigSource(config, text)
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

    /** Set by [loadProviders] when a saved config still had the first line older versions wrote (see [withoutLegacyLine]). */
    private var hadLegacyLine = false

    private val _providers = MutableStateFlow(loadProviders())
    val providers: StateFlow<List<StoredProvider>> = _providers.asStateFlow()

    init {
        // Saved again without it, once, so it's never looked for after this
        if (hadLegacyLine) writeRecords(_providers.value)
    }

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
            val source = ConfigSource.of(withoutLegacyLine(record.config)) ?: return@mapNotNull null
            StoredProvider(
                id = id,
                config = source.config,
                text = source.text,
                upstream = record.upstream?.let { ProviderConfigCodec.decode(withoutLegacyLine(it)) },
                previous = record.previous?.let { ConfigSource.of(withoutLegacyLine(it)) },
                autoUpdate = record.autoUpdate,
                keepAutoUpdateWhenEdited = record.keepAutoUpdateWhenEdited,
                pendingUpdate = record.pendingUpdate?.let { ConfigSource.of(withoutLegacyLine(it)) },
                etag = record.etag,
                lastCheckedMillis = record.lastCheckedMillis
            )
        }
    }

    /**
     * [text] without the line older versions of the app put before every config they saved; the app no longer writes or
     * reads it, configs are plain JSON. Only for configs saved by those versions, see [hadLegacyLine].
     */
    private fun withoutLegacyLine(text: String): String {
        val trimmed = text.trim()
        if (!trimmed.startsWith(LEGACY_FIRST_LINE)) return trimmed
        hadLegacyLine = true
        return trimmed.removePrefix(LEGACY_FIRST_LINE).trim()
    }

    private fun persist(providers: List<StoredProvider>) {
        writeRecords(providers)
        _providers.value = providers
        changed()
    }

    private fun writeRecords(providers: List<StoredProvider>) {
        val records = providers.associate { p ->
            p.id to json.encodeToJsonElement(
                StoredRecord.serializer(),
                StoredRecord(
                    config = p.text,
                    // Only compared with the config, never shown, so its parsed form is enough
                    upstream = p.upstream?.let { ProviderConfigCodec.encode(it) },
                    previous = p.previous?.text,
                    autoUpdate = p.autoUpdate,
                    keepAutoUpdateWhenEdited = p.keepAutoUpdateWhenEdited,
                    pendingUpdate = p.pendingUpdate?.text,
                    etag = p.etag,
                    lastCheckedMillis = p.lastCheckedMillis
                )
            )
        }
        prefs.edit { putString(CONFIGS_KEY, JsonObject(records).toString()) }
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
    fun import(source: ConfigSource): String {
        val config = source.config
        val metaId = config.meta?.id
        val existing = metaId?.let { id ->
            _providers.value.firstOrNull { it.config.meta?.id == id && it.updateUrl == config.meta?.updateUrl }
        }
        val upstream = config.takeIf { it.meta?.updateUrl != null }
        if (existing != null) {
            update(existing.id) { it.copy(config = config, text = source.text, upstream = upstream, previous = it.source, pendingUpdate = null) }
            return existing.id
        }
        val id = UUID.randomUUID().toString()
        persist(_providers.value + StoredProvider(id = id, config = config, text = source.text, upstream = upstream))
        return id
    }

    /**
     * Saves a hand edit. If this makes the config differ from its upstream while auto-update is on, auto-update
     * is switched off, once: if the user turns it back on, later edits leave it alone.
     */
    fun saveEdit(id: String, source: ConfigSource): EditOutcome {
        val before = get(id) ?: return EditOutcome(nowEdited = false, autoUpdateTurnedOff = false)
        val edited = before.copy(config = source.config, text = source.text)
        val turnOff = edited.isEdited && edited.autoUpdate && !edited.keepAutoUpdateWhenEdited
        update(id) { edited.copy(autoUpdate = if (turnOff) false else edited.autoUpdate) }
        return EditOutcome(nowEdited = edited.isEdited, autoUpdateTurnedOff = turnOff)
    }

    /** Detaches the config from its update URL: it becomes a purely local config. */
    fun removeUpdateLink(id: String) {
        update(id) {
            it.copy(
                config = it.config.withMeta(it.config.meta?.copy(updateUrl = null)),
                text = ProviderConfigCodec.removeUpdateUrl(it.text) ?: it.text,
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

    fun recordCheck(id: String, pendingUpdate: ConfigSource?, etag: String?, checkedAtMillis: Long) {
        update(id) { it.copy(pendingUpdate = pendingUpdate, etag = etag ?: it.etag, lastCheckedMillis = checkedAtMillis) }
    }

    /**
     * Applies [newSource] (normally [StoredProvider.pendingUpdate]); the replaced version is kept for [revert].
     * With [clearSecret], the saved token is deleted: used when the update sends credentials somewhere new.
     */
    fun applyUpdate(id: String, newSource: ConfigSource, clearSecret: Boolean) {
        update(id) {
            it.copy(
                config = newSource.config,
                text = newSource.text,
                upstream = newSource.config,
                previous = it.source,
                pendingUpdate = null,
                keepAutoUpdateWhenEdited = false
            )
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
                config = previous.config,
                text = previous.text,
                upstream = previous.config.takeIf { it.meta?.updateUrl != null },
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
