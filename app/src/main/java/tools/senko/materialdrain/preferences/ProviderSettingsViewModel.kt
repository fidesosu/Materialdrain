package tools.senko.materialdrain.preferences

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tools.senko.materialdrain.provider.CheckOutcome
import tools.senko.materialdrain.provider.ConfigSource
import tools.senko.materialdrain.provider.PIXELDRAIN_PROVIDER_ID
import tools.senko.materialdrain.provider.ProviderConfigStore
import tools.senko.materialdrain.provider.ProviderRegistry
import tools.senko.materialdrain.provider.ProviderUpdater
import tools.senko.materialdrain.provider.StoredProvider
import tools.senko.materialdrain.provider.api.ConfigUpdates
import tools.senko.materialdrain.provider.api.Credentials
import tools.senko.materialdrain.provider.api.PasswordSignIn
import tools.senko.materialdrain.provider.api.ProviderCapability
import tools.senko.materialdrain.provider.api.SignInResult
import tools.senko.materialdrain.provider.api.ProviderConfigCodec
import tools.senko.materialdrain.provider.api.ProviderValidationResult
import tools.senko.materialdrain.provider.api.SensitiveChange

/** An update which needs the user's go-ahead: it changes where credentials go, and/or discards their edits. */
data class UpdateApproval(
    val id: String,
    val hostName: String,
    val fromVersion: Int,
    val toVersion: Int,
    val sensitiveChanges: List<SensitiveChange>,
    val discardsEdits: Boolean
) {
    val clearsToken: Boolean get() = sensitiveChanges.any { it.kind == SensitiveChange.Kind.DESTINATION || it.kind == SensitiveChange.Kind.KIND }
}

/** Shown after saving an edit to a config which still updates from an update URL. */
data class EditNotice(val id: String, val hostName: String, val updateHost: String?, val autoUpdateTurnedOff: Boolean)

/** The host asked for a two-factor code during sign-in. [rejectedCode] isn't pasted from the clipboard again. */
data class OtpPrompt(val id: String, val hostName: String, val digits: Int, val error: String? = null, val rejectedCode: String? = null)

data class ProviderSettingsUiState(
    val providers: List<StoredProvider> = emptyList(),
    val activeProviderId: String = PIXELDRAIN_PROVIDER_ID,
    val validation: Map<String, ProviderValidationResult> = emptyMap(),
    val busyId: String? = null,
    val importError: String? = null,
    val checkingIds: Set<String> = emptySet(),
    /** Result of the last update check per host, e.g. "Up to date". */
    val checkMessages: Map<String, String> = emptyMap(),
    val editingId: String? = null,
    /** A new host being filled in, before it's added: the config's text it started from (a template, a paste). */
    val newHostText: String? = null,
    /** Why the config in the editor (an edit, or a new host) wasn't saved. */
    val editError: String? = null,
    val approval: UpdateApproval? = null,
    val editNotice: EditNotice? = null,
    /** Hosts with a session, and the username they're signed in as. */
    val signedInAs: Map<String, String> = emptyMap(),
    val signingInId: String? = null,
    val signInErrors: Map<String, String> = emptyMap(),
    val otpPrompt: OtpPrompt? = null,
    /** A host to open and scroll to in the settings, e.g. from the host switcher; cleared once it's shown. */
    val focusedHostId: String? = null
)

/** Backs the "Custom host settings" section of the Advanced settings tab. */
class ProviderSettingsViewModel(
    private val configStore: ProviderConfigStore,
    private val registry: ProviderRegistry,
    private val updater: ProviderUpdater
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProviderSettingsUiState())
    val uiState: StateFlow<ProviderSettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(configStore.providers, configStore.activeProviderId) { providers, activeId -> providers to activeId }
                .collect { (providers, activeId) ->
                    _uiState.update { it.copy(providers = providers, activeProviderId = activeId) }
                    refreshSignedIn()
                }
        }
    }

    private fun refreshSignedIn() {
        val signedIn = _uiState.value.providers.mapNotNull { stored ->
            val credentials = configStore.credentials(stored.id)
            credentials.loginToken?.let { stored.id to credentials.username }
        }.toMap()
        _uiState.update { it.copy(signedInAs = signedIn) }
    }

    // --- Import / export ---

    /** Decodes [text] as a provider config (magic line + JSON) and stores it. Sets an error on failure. */
    fun importConfig(text: String): Boolean {
        val source = ConfigSource.of(text)
        if (source == null) {
            val reason = ProviderConfigCodec.explainFailure(text) ?: "it couldn't be read"
            _uiState.update { it.copy(importError = "This isn't a usable provider config: $reason.") }
            return false
        }
        // The newly imported host is the one the Files, Lists and Filesystem screens use
        configStore.setActive(configStore.import(source))
        _uiState.update { it.copy(importError = null) }
        return true
    }

    fun clearImportError() = _uiState.update { it.copy(importError = null) }

    /** Opens the host's card the next time the host settings are shown. */
    fun focusHost(id: String) = _uiState.update { it.copy(focusedHostId = id) }

    fun clearFocusedHost() = _uiState.update { it.copy(focusedHostId = null) }

    fun exportConfig(id: String): String? = configStore.get(id)?.text


    fun remove(id: String) {
        configStore.remove(id)
        _uiState.update { it.copy(validation = it.validation - id, checkMessages = it.checkMessages - id) }
    }

    // --- Credentials / connection test ---

    fun saveApiKey(id: String, apiKey: String) = configStore.saveApiKey(id, apiKey)

    /** Only for hosts that take the username and password with every request; session hosts use [signIn]. */
    fun savePasswordCredentials(id: String, username: String, password: String) =
        configStore.savePasswordCredentials(id, username, password)

    fun credentialsFor(id: String): Credentials = configStore.credentials(id)

    /** Builds the provider just to read what it can do - cheap, no network calls happen in a provider's constructor. */
    fun capabilitiesFor(id: String): Set<ProviderCapability> = configStore.get(id)?.let { registry.build(it).capabilities } ?: emptySet()

    /** Runs the provider's (read-only) per-field reachability check and records the result for display. */
    fun validate(id: String) {
        val stored = configStore.get(id) ?: return
        _uiState.update { it.copy(busyId = id) }
        viewModelScope.launch {
            val result = registry.build(stored).validate()
            _uiState.update { it.copy(busyId = null, validation = it.validation + (id to result)) }
            // The check drops a session the host no longer accepts
            refreshSignedIn()
        }
    }

    // --- Sign-in (hosts where a username and password start a session) ---

    /** Kept in memory only between the first attempt and the two-factor code, never saved. */
    private data class PendingSignIn(val id: String, val username: String, val password: String)
    private var pendingSignIn: PendingSignIn? = null

    private fun signInProvider(id: String): PasswordSignIn? = configStore.get(id)?.let { registry.build(it) } as? PasswordSignIn

    fun signIn(id: String, username: String, password: String) {
        pendingSignIn = PendingSignIn(id, username.trim(), password)
        _uiState.update { it.copy(signInErrors = it.signInErrors - id) }
        attemptSignIn(otp = null)
    }

    fun submitOtp(code: String) = attemptSignIn(otp = code)

    fun cancelOtp() {
        pendingSignIn = null
        _uiState.update { it.copy(otpPrompt = null) }
    }

    private fun attemptSignIn(otp: String?) {
        val pending = pendingSignIn ?: return
        val provider = signInProvider(pending.id) ?: return
        val hostName = configStore.get(pending.id)?.config?.name.orEmpty()
        _uiState.update { it.copy(signingInId = pending.id) }
        viewModelScope.launch {
            when (val result = provider.signIn(pending.username, pending.password, otp)) {
                is SignInResult.Success -> {
                    configStore.saveSignIn(pending.id, pending.username, result.token)
                    pendingSignIn = null
                    _uiState.update {
                        it.copy(signingInId = null, otpPrompt = null, signInErrors = it.signInErrors - pending.id, validation = it.validation - pending.id)
                    }
                    refreshSignedIn()
                }
                is SignInResult.OtpRequired -> _uiState.update {
                    it.copy(signingInId = null, otpPrompt = OtpPrompt(pending.id, hostName, result.digits))
                }
                is SignInResult.OtpRejected -> _uiState.update {
                    it.copy(signingInId = null, otpPrompt = OtpPrompt(pending.id, hostName, result.digits, error = result.message, rejectedCode = otp))
                }
                is SignInResult.Failed -> {
                    pendingSignIn = null
                    _uiState.update {
                        it.copy(signingInId = null, otpPrompt = null, signInErrors = it.signInErrors + (pending.id to result.message))
                    }
                }
            }
        }
    }

    fun signOut(id: String) {
        val provider = signInProvider(id)
        viewModelScope.launch {
            provider?.signOut()
            configStore.saveLoginToken(id, null)
            _uiState.update { it.copy(validation = it.validation - id) }
            refreshSignedIn()
        }
    }

    // --- Editing ---

    fun startEdit(id: String) = _uiState.update { it.copy(editingId = id, editError = null) }

    fun cancelEdit() = _uiState.update { it.copy(editingId = null, newHostText = null, editError = null) }

    /** Opens the editor on a new host, starting from [text]: a template, or a pasted config. */
    fun startNewHost(text: String) = _uiState.update { it.copy(newHostText = text, editError = null) }

    /** Adds the host filled in in the editor and makes it the active one; when it isn't usable yet, says why. */
    fun saveNewHost(text: String) {
        val source = sourceOrError(text) ?: return
        configStore.setActive(configStore.import(source))
        _uiState.update { it.copy(newHostText = null, editError = null, importError = null) }
    }

    private fun sourceOrError(text: String): ConfigSource? {
        val source = ConfigSource.of(text)
        if (source == null) {
            val reason = ProviderConfigCodec.explainFailure(text) ?: "it couldn't be read"
            _uiState.update { it.copy(editError = "This config can't be used yet: $reason.") }
        }
        return source
    }

    fun saveEdit(id: String, text: String) {
        val source = sourceOrError(text) ?: return
        val config = source.config
        val outcome = configStore.saveEdit(id, source)
        val stored = configStore.get(id)
        _uiState.update {
            it.copy(
                editingId = null,
                editError = null,
                editNotice = if (outcome.nowEdited && stored?.updateUrl != null) {
                    EditNotice(id, config.name, ConfigUpdates.hostOf(stored.updateUrl), outcome.autoUpdateTurnedOff)
                } else null
            )
        }
    }

    fun dismissEditNotice() = _uiState.update { it.copy(editNotice = null) }

    fun removeUpdateLink(id: String) {
        configStore.removeUpdateLink(id)
        _uiState.update { it.copy(editNotice = null, checkMessages = it.checkMessages - id) }
    }

    // --- Updates ---

    fun setAutoUpdate(id: String, enabled: Boolean) = configStore.setAutoUpdate(id, enabled)

    fun checkForUpdates(id: String) {
        _uiState.update { it.copy(checkingIds = it.checkingIds + id) }
        viewModelScope.launch {
            val message = describe(updater.check(id))
            _uiState.update { it.copy(checkingIds = it.checkingIds - id, checkMessages = it.checkMessages + (id to message)) }
        }
    }

    fun checkAllForUpdates() {
        _uiState.value.providers.filter { it.updateUrl != null }.forEach { checkForUpdates(it.id) }
    }

    /** Applies the pending update right away, unless it needs approval: then the approval dialog is shown. */
    fun requestApplyUpdate(id: String) {
        val stored = configStore.get(id) ?: return
        val pending = stored.pendingUpdate ?: return
        val changes = updater.pendingSensitiveChanges(id)
        if (changes.isEmpty() && !stored.isEdited) {
            applyUpdate(id)
            return
        }
        _uiState.update {
            it.copy(
                approval = UpdateApproval(
                    id = id,
                    hostName = stored.config.name,
                    fromVersion = stored.config.meta?.version ?: 0,
                    toVersion = pending.config.meta?.version ?: 0,
                    sensitiveChanges = changes,
                    discardsEdits = stored.isEdited
                )
            )
        }
    }

    fun confirmApproval() {
        val approval = _uiState.value.approval ?: return
        _uiState.update { it.copy(approval = null) }
        applyUpdate(approval.id)
    }

    fun dismissApproval() = _uiState.update { it.copy(approval = null) }

    private fun applyUpdate(id: String) {
        updater.applyPending(id)
        _uiState.update { it.copy(checkMessages = it.checkMessages + (id to "Updated"), validation = it.validation - id) }
    }

    fun revert(id: String) {
        configStore.revert(id)
        _uiState.update {
            it.copy(checkMessages = it.checkMessages + (id to "Reverted to the previous version, auto-update is off"), validation = it.validation - id)
        }
    }

    private fun describe(outcome: CheckOutcome): String = when (outcome) {
        CheckOutcome.UpToDate -> "Up to date"
        is CheckOutcome.UpdateAvailable ->
            if (outcome.sensitiveChanges.isEmpty()) "Version ${outcome.version} is available"
            else "Version ${outcome.version} is available and needs your approval"
        is CheckOutcome.NeedsNewerApp -> "A newer version needs a newer app (version ${outcome.minAppVersion})"
        is CheckOutcome.Failed -> "Couldn't check: ${outcome.message}"
        CheckOutcome.NoUpdateUrl -> "This config has no update URL"
    }
}
