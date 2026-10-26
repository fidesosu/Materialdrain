package tools.senko.materialdrain.settings.ui

import tools.senko.materialdrain.ui.theme.AppShapes
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import kotlinx.coroutines.delay
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import tools.senko.materialdrain.ui.icons.filled.CheckCircle
import tools.senko.materialdrain.ui.icons.filled.Download
import tools.senko.materialdrain.ui.icons.filled.Edit
import tools.senko.materialdrain.ui.icons.filled.Error
import tools.senko.materialdrain.ui.icons.filled.ExpandLess
import tools.senko.materialdrain.ui.icons.filled.ExpandMore
import tools.senko.materialdrain.ui.icons.filled.Folder
import tools.senko.materialdrain.ui.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import kotlinx.coroutines.flow.filter
import androidx.compose.ui.unit.dp
import tools.senko.materialdrain.settings.ui.configeditor.ConfigEditor
import tools.senko.materialdrain.provider.StoredProvider
import tools.senko.materialdrain.provider.hostTypeOf
import tools.senko.materialdrain.provider.api.ConfigUpdates
import tools.senko.materialdrain.provider.api.PasswordAuthMode
import tools.senko.materialdrain.provider.api.ProviderCapability
import tools.senko.materialdrain.provider.api.passwordAuth
import tools.senko.materialdrain.ui.LocalReduceMotion
import tools.senko.materialdrain.provider.api.ProviderConfigTemplates
import tools.senko.materialdrain.ui.preview.PreviewTheme
import tools.senko.materialdrain.ui.preview.ThemePreviews
import tools.senko.materialdrain.ui.preview.ScreenPreviews
import tools.senko.materialdrain.provider.api.SensitiveChange
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.res.stringResource
import tools.senko.materialdrain.R

/** "Custom host settings": the list of imported configs, their update state, and importing new ones. */
@Composable
fun SettingsEnvironment.ProviderHostsSection() {
    val viewModel = providerSettingsViewModel
    val uiState by viewModel.uiState.collectAsState()
    var importText by rememberSaveable { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            stringResource(R.string.settings_pixeldrain_is_always_there_switch),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (uiState.providers.any { it.updateUrl != null }) {
            TextButton(onClick = { viewModel.checkAllForUpdates() }, enabled = uiState.checkingIds.isEmpty()) {
                Text(stringResource(R.string.settings_check_all_for_updates))
            }
        }

        if (uiState.providers.isNotEmpty()) {
            Spacer(modifier = Modifier.height(4.dp))
            uiState.providers.forEach { stored -> HostCard(stored, uiState, viewModel) }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text(stringResource(R.string.settings_add_a_custom_host), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            stringResource(R.string.settings_pick_a_kind_of_host),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        // A complete config to fill in, for each kind of host the app speaks, opened in the editor
        Text(stringResource(R.string.settings_new_host), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ProviderConfigTemplates.Kind.entries.forEach { kind ->
                AssistChip(onClick = { viewModel.startNewHost(ProviderConfigTemplates.text(kind)) }, label = { Text(kind.label) })
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
            value = importText,
            onValueChange = { importText = it; viewModel.clearImportError() },
            label = { Text(stringResource(R.string.settings_or_paste_a_config)) },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3
        )
        uiState.importError?.let {
            Spacer(modifier = Modifier.height(4.dp))
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                // Moved into the editor, so the box doesn't still hold it once the host is added
                onClick = { viewModel.startNewHost(importText); importText = "" },
                enabled = importText.isNotBlank(),
                modifier = Modifier.weight(1f)
            ) { Text(stringResource(R.string.settings_review_first)) }
            Button(
                onClick = { if (viewModel.importConfig(importText)) importText = "" },
                enabled = importText.isNotBlank(),
                modifier = Modifier.weight(1f)
            ) { Text(stringResource(R.string.settings_add_host)) }
        }
    }

    uiState.editingId?.let { id ->
        ConfigEditor(
            title = stringResource(R.string.settings_edit_host),
            initialText = remember(id) { viewModel.exportConfig(id).orEmpty() },
            saveLabel = stringResource(R.string.settings_save),
            error = uiState.editError,
            onSave = { viewModel.saveEdit(id, it) },
            onDismiss = { viewModel.cancelEdit() }
        )
    }
    uiState.newHostText?.let { text ->
        ConfigEditor(
            title = stringResource(R.string.settings_new_host),
            initialText = text,
            saveLabel = stringResource(R.string.settings_add_host),
            error = uiState.editError,
            onSave = { viewModel.saveNewHost(it) },
            onDismiss = { viewModel.cancelEdit() }
        )
    }
    uiState.approval?.let { UpdateApprovalDialog(it, viewModel) }
    uiState.editNotice?.let { EditNoticeDialog(it, viewModel) }
    uiState.otpPrompt?.let { OtpDialog(it, busy = uiState.signingInId == it.id, viewModel) }
}

/** Six digits (or whatever the host uses) on the clipboard, allowing spaces or a dash like "123 456". */
private fun clipboardCode(context: Context, digits: Int): String? {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    if (!clipboard.hasPrimaryClip()) return null
    val text = clipboard.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString() ?: return null
    val compact = text.trim().filterNot { it == ' ' || it == '-' }
    return compact.takeIf { it.length == digits && it.all(Char::isDigit) }
}

@Composable
private fun OtpDialog(prompt: OtpPrompt, busy: Boolean, viewModel: ProviderSettingsViewModel) {
    var code by remember(prompt.id) { mutableStateOf("") }
    var lastPasted by remember(prompt.id) { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val windowInfo = LocalWindowInfo.current

    // Fills in a code from the clipboard when the dialog opens, and again every time the app comes back to the
    // front (after copying the code in the authenticator app). Only pastes over an empty field or an earlier
    // paste, and never the code the host just rejected. Not submitted automatically: codes expire and hosts
    // limit wrong attempts, so an old code on the clipboard mustn't use one up by itself.
    LaunchedEffect(prompt.id, prompt.rejectedCode) {
        snapshotFlow { windowInfo.isWindowFocused }.filter { it }.collect {
            val candidate = clipboardCode(context, prompt.digits) ?: return@collect
            if (candidate != prompt.rejectedCode && candidate != code && (code.isEmpty() || code == lastPasted || code == prompt.rejectedCode)) {
                code = candidate
                lastPasted = candidate
            }
        }
    }

    val complete = code.length == prompt.digits
    AlertDialog(
        onDismissRequest = { viewModel.cancelOtp() },
        title = { Text(stringResource(R.string.settings_two_factor_authentication)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.settings_enter_the_digit_code_from, prompt.digits))
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it.filter(Char::isDigit).take(prompt.digits) },
                    label = { Text(stringResource(R.string.settings_code)) },
                    singleLine = true,
                    isError = prompt.error != null && code == prompt.rejectedCode,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { if (complete && !busy) viewModel.submitOtp(code) }),
                    modifier = Modifier.fillMaxWidth()
                )
                if (code.isNotEmpty() && code == lastPasted) {
                    Text(stringResource(R.string.settings_pasted_from_the_clipboard), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                prompt.error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(onClick = { viewModel.submitOtp(code) }, enabled = complete && !busy) {
                if (busy) CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp) else Text(stringResource(R.string.settings_sign_in))
            }
        },
        dismissButton = { TextButton(onClick = { viewModel.cancelOtp() }) { Text(stringResource(R.string.browser_cancel)) } }
    )
}

/** How long a host asked for from the switcher waits before it's scrolled to, see HostCard. */
private const val FOCUS_SCROLL_DELAY_MS = 400L

@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
private fun HostCard(
    stored: StoredProvider,
    uiState: ProviderSettingsUiState,
    viewModel: ProviderSettingsViewModel,
    /** Opened at first: for the previews at the end of the file */
    startExpanded: Boolean = false
) {
    var expanded by rememberSaveable(stored.id) { mutableStateOf(startExpanded) }
    // Asked for from the host switcher: opened, and scrolled to once the settings page has slid in and the card has grown
    val bringIntoView = remember { BringIntoViewRequester() }
    val focused = uiState.focusedHostId == stored.id
    LaunchedEffect(focused) {
        if (focused) {
            expanded = true
            delay(FOCUS_SCROLL_DELAY_MS)
            bringIntoView.bringIntoView()
            viewModel.clearFocusedHost()
        }
    }
    // Capabilities don't change without an edit or an update, both already keyed into this computation
    val capabilities = remember(stored.id, stored.config) { viewModel.capabilitiesFor(stored.id) }
    // Re-read whenever the config changes: an update that moves to a new host clears the saved credentials, and
    // the fields mustn't keep showing (and then re-save) the old ones
    var apiKey by rememberSaveable(stored.id, stored.config) { mutableStateOf(viewModel.credentialsFor(stored.id).apiKey) }
    var username by rememberSaveable(stored.id, stored.config) { mutableStateOf(viewModel.credentialsFor(stored.id).username) }
    // Not saveable: saved instance state can outlive the screen, a password shouldn't
    var password by remember(stored.id, stored.config) { mutableStateOf(viewModel.credentialsFor(stored.id).password) }
    var showApiKey by rememberSaveable(stored.id) { mutableStateOf(apiKey.isNotBlank()) }
    val passwordMode = stored.config.passwordAuth?.mode
    val hostType = remember(stored.config) { hostTypeOf(stored.config) }
    // A host which signs in with two keys and has no API key to fall back to (S3, SMB)
    val keyPairLabels = hostType.keyPairLabels
    val signedInAs = uiState.signedInAs[stored.id]
    val signingIn = uiState.signingInId == stored.id
    var justCopied by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    val reduceMotion = LocalReduceMotion.current
    val busy = uiState.busyId == stored.id
    val checking = stored.id in uiState.checkingIds
    val meta = stored.config.meta
    val pending = stored.pendingUpdate?.config

    Card(
        shape = AppShapes.Large,
        colors = CardDefaults.cardColors(containerColor = colors.surfaceContainerHigh),
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp).bringIntoViewRequester(bringIntoView)
    ) {
        Column(modifier = if (reduceMotion) Modifier else Modifier.animateContentSize()) {
            // Always visible: what the host is, its state, and the two quick actions. Tapping it expands the rest.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(start = 16.dp, end = 4.dp, top = 10.dp, bottom = 10.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stored.config.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        listOfNotNull(hostType.label,meta?.let { "v${it.version}" }, meta?.author?.let { "by $it" }).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant
                    )
                    val statuses = listOfNotNull(
                        stringResource(R.string.settings_edited).takeIf { stored.isEdited },
                        pending?.let { stringResource(R.string.settings_version_available, it.meta?.version ?: 0) }
                    )
                    if (statuses.isNotEmpty()) {
                        Text(statuses.joinToString(" · "), style = MaterialTheme.typography.labelMedium, color = colors.tertiary)
                    }
                    signedInAs?.let {
                        Text(stringResource(R.string.settings_signed_in_as, it), style = MaterialTheme.typography.labelMedium, color = colors.primary)
                    }
                }
                if (pending != null) {
                    // Slightly highlighted: a tonal button stands out from the plain edit icon next to it
                    FilledTonalIconButton(onClick = { viewModel.requestApplyUpdate(stored.id) }) {
                        Icon(Icons.Filled.Download, contentDescription = stringResource(R.string.settings_update_to_version, pending.meta?.version ?: 0))
                    }
                }
                IconButton(onClick = { viewModel.startEdit(stored.id) }) {
                    Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.settings_edit_config))
                }
                Icon(
                    if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = if (expanded) stringResource(R.string.settings_collapse) else stringResource(R.string.settings_expand),
                    tint = colors.onSurfaceVariant,
                    modifier = Modifier.padding(end = 8.dp)
                )
            }

            if (expanded) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 12.dp)
                ) {
                    stored.updateUrl?.let { url ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(stringResource(R.string.settings_auto_update_from, ConfigUpdates.hostOf(url) ?: url), style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    stringResource(R.string.settings_checked_daily_updates_that_change),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.onSurfaceVariant
                                )
                            }
                            Switch(checked = stored.autoUpdate, onCheckedChange = { viewModel.setAutoUpdate(stored.id, it) })
                        }
                    }

                    if (keyPairLabels != null) {
                        Text(stringResource(R.string.settings_credentials), style = MaterialTheme.typography.labelLarge, color = colors.primary)
                        if (!hostType.needsSignIn) {
                            Text(hostType.noSignInNote ?: stringResource(R.string.settings_there_s_nothing_to_sign), style = MaterialTheme.typography.bodyMedium)
                        } else {
                            CredentialFields(
                                username, { username = it }, password, { password = it },
                                usernameLabel = keyPairLabels.username,
                                passwordLabel = keyPairLabels.password
                            )
                        }
                    } else {
                        Text(stringResource(R.string.settings_sign_in_2), style = MaterialTheme.typography.labelLarge, color = colors.primary)
                        when (passwordMode) {
                            // A session, like signing in to any app: the password is used once, only the session is kept
                            PasswordAuthMode.LOGIN -> if (signedInAs != null) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                    Text(stringResource(R.string.settings_signed_in_as, signedInAs), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                                    OutlinedButton(onClick = { viewModel.signOut(stored.id) }) { Text(stringResource(R.string.settings_sign_out)) }
                                }
                            } else {
                                val canSignIn = username.isNotBlank() && password.isNotEmpty() && !signingIn
                                CredentialFields(
                                    username, { username = it }, password, { password = it },
                                    usernameLabel = stringResource(R.string.settings_username_or_e_mail),
                                    onSignIn = { if (canSignIn) viewModel.signIn(stored.id, username, password) }
                                )
                                Button(
                                    onClick = { viewModel.signIn(stored.id, username, password) },
                                    enabled = canSignIn,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    if (signingIn) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp) else Text(stringResource(R.string.settings_sign_in))
                                }
                                uiState.signInErrors[stored.id]?.let {
                                    Text(it, style = MaterialTheme.typography.bodySmall, color = colors.error)
                                }
                            }
                            // Sent with every request, so it has to be kept
                            PasswordAuthMode.BASIC -> CredentialFields(username, { username = it }, password, { password = it })
                            null -> {}
                        }

                        // The API key is the secondary way in for hosts that take a username and password
                        if (passwordMode == null || showApiKey) {
                            OutlinedTextField(
                                value = apiKey,
                                onValueChange = { apiKey = it },
                                label = { Text(stringResource(R.string.settings_api_key)) },
                                singleLine = true,
                                visualTransformation = PasswordVisualTransformation(),
                                modifier = Modifier.fillMaxWidth()
                            )
                            Text(
                                when (passwordMode) {
                                    PasswordAuthMode.LOGIN -> stringResource(R.string.settings_used_while_you_re_not)
                                    PasswordAuthMode.BASIC -> stringResource(R.string.settings_used_without_a_username_and)
                                    null -> stringResource(R.string.settings_this_host_only_takes_an)
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.onSurfaceVariant
                            )
                        } else {
                            TextButton(onClick = { showApiKey = true }) { Text(stringResource(R.string.settings_use_an_api_key_instead)) }
                        }
                    }

                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                if (keyPairLabels != null) {
                                    viewModel.savePasswordCredentials(stored.id, username, password)
                                } else {
                                    viewModel.saveApiKey(stored.id, apiKey)
                                    if (passwordMode == PasswordAuthMode.BASIC) viewModel.savePasswordCredentials(stored.id, username, password)
                                }
                                viewModel.validate(stored.id)
                            },
                            enabled = !busy
                        ) {
                            if (busy) CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            else Text(if (passwordMode == PasswordAuthMode.LOGIN && !showApiKey) stringResource(R.string.settings_test_connection) else stringResource(R.string.settings_save_test))
                        }
                        if (stored.updateUrl != null) {
                            OutlinedButton(onClick = { viewModel.checkForUpdates(stored.id) }, enabled = !checking) {
                                if (checking) CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp) else Text(stringResource(R.string.settings_check_for_updates))
                            }
                        }
                        TextButton(onClick = {
                            viewModel.exportConfig(stored.id)?.let { text ->
                                val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboardManager.setPrimaryClip(ClipData.newPlainText("Provider config", text))
                            }
                            justCopied = true
                        }) { Text(if (justCopied) stringResource(R.string.settings_copied) else stringResource(R.string.settings_export)) }
                        if (stored.previous != null) {
                            TextButton(onClick = { viewModel.revert(stored.id) }) { Text(stringResource(R.string.settings_revert_update)) }
                        }
                        TextButton(onClick = { viewModel.remove(stored.id) }) { Text(stringResource(R.string.settings_remove)) }
                    }

                    uiState.checkMessages[stored.id]?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                    }

                    uiState.validation[stored.id]?.let { result ->
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            result.fields.forEach { field ->
                                Row(verticalAlignment = Alignment.Top) {
                                    Icon(
                                        imageVector = when {
                                            field.inconclusive -> Icons.Filled.Info
                                            field.ok -> Icons.Filled.CheckCircle
                                            else -> Icons.Filled.Error
                                        },
                                        contentDescription = null,
                                        tint = when {
                                            field.inconclusive -> colors.onSurfaceVariant
                                            field.ok -> colors.primary
                                            else -> colors.error
                                        },
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        "${field.label}: ${field.message}",
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.padding(start = 6.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * A host's username and password fields, labelled as that kind of host calls them. The password is never shown.
 *
 * @param onSignIn for a sign-in form: the username gets an e-mail keyboard and Next, and Done on the password calls this
 */
@Composable
private fun CredentialFields(
    username: String,
    onUsernameChange: (String) -> Unit,
    password: String,
    onPasswordChange: (String) -> Unit,
    usernameLabel: String = stringResource(R.string.settings_username),
    passwordLabel: String = stringResource(R.string.settings_password),
    onSignIn: (() -> Unit)? = null
) {
    OutlinedTextField(
        value = username,
        onValueChange = onUsernameChange,
        label = { Text(usernameLabel) },
        singleLine = true,
        keyboardOptions = if (onSignIn != null) KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next) else KeyboardOptions.Default,
        modifier = Modifier.fillMaxWidth()
    )
    OutlinedTextField(
        value = password,
        onValueChange = onPasswordChange,
        label = { Text(passwordLabel) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = if (onSignIn != null) KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done) else KeyboardOptions.Default,
        keyboardActions = if (onSignIn != null) KeyboardActions(onDone = { onSignIn() }) else KeyboardActions.Default,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun UpdateApprovalDialog(approval: UpdateApproval, viewModel: ProviderSettingsViewModel) {
    AlertDialog(
        onDismissRequest = { viewModel.dismissApproval() },
        title = { Text(stringResource(R.string.settings_review_update)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${approval.hostName}: version ${approval.fromVersion} → ${approval.toVersion}")
                if (approval.sensitiveChanges.isNotEmpty()) {
                    Text(stringResource(R.string.settings_this_update_changes_how_your))
                    approval.sensitiveChanges.forEach { change ->
                        Text("• ${change.description}", style = MaterialTheme.typography.bodyMedium)
                    }
                    Text(
                        stringResource(R.string.settings_only_update_if_you_trust),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (approval.clearsToken) {
                    Text(
                        stringResource(R.string.settings_your_saved_sign_in_will),
                        color = MaterialTheme.colorScheme.error
                    )
                }
                if (approval.discardsEdits) Text(stringResource(R.string.settings_updating_replaces_your_edits))
            }
        },
        confirmButton = { TextButton(onClick = { viewModel.confirmApproval() }) { Text(stringResource(R.string.settings_update)) } },
        dismissButton = { TextButton(onClick = { viewModel.dismissApproval() }) { Text(stringResource(R.string.browser_cancel)) } }
    )
}

@Composable
private fun EditNoticeDialog(notice: EditNotice, viewModel: ProviderSettingsViewModel) {
    AlertDialog(
        onDismissRequest = { viewModel.dismissEditNotice() },
        title = { Text(stringResource(R.string.settings_this_config_is_edited)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.settings_updates_from_would_replace_your, notice.updateHost ?: "its update URL"))
                if (notice.autoUpdateTurnedOff) {
                    Text(stringResource(R.string.settings_auto_update_is_now_off))
                }
            }
        },
        confirmButton = { TextButton(onClick = { viewModel.removeUpdateLink(notice.id) }) { Text(stringResource(R.string.settings_stop_updating)) } },
        dismissButton = { TextButton(onClick = { viewModel.dismissEditNotice() }) { Text(stringResource(R.string.settings_keep_updates)) } }
    )
}

// ---- Previews ----

/** Each of the preview's example hosts (see rememberPreviewSettings) opened: its sign-in, its config and updates. */
@ScreenPreviews
@Composable
private fun HostCardPreview() {
    PreviewTheme {
        val viewModel = rememberPreviewSettings().providerSettingsViewModel
        val state by viewModel.uiState.collectAsState()
        Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
            state.providers.forEach { HostCard(it, state, viewModel, startExpanded = true) }
        }
    }
}

@ThemePreviews
@Composable
private fun OtpDialogPreview() {
    PreviewTheme {
        OtpDialog(OtpPrompt("nas", "Home NAS", digits = 6, error = "That code didn't work. Try the next one.", rejectedCode = "123456"), busy = false, viewModel = rememberPreviewSettings().providerSettingsViewModel)
    }
}

/** An update which sends the sign-in somewhere else: what changes, and that the saved sign-in goes. */
@ThemePreviews
@Composable
private fun UpdateApprovalDialogPreview() {
    PreviewTheme {
        UpdateApprovalDialog(
            UpdateApproval(
                id = "nas",
                hostName = "Home NAS",
                fromVersion = 3,
                toVersion = 4,
                sensitiveChanges = listOf(
                    SensitiveChange(SensitiveChange.Kind.DESTINATION, "Requests go to nas.example.org instead of 192.168.1.20"),
                    SensitiveChange(SensitiveChange.Kind.AUTH, "The key is sent in the X-Api-Key header instead of Authorization")
                ),
                discardsEdits = true
            ),
            viewModel = rememberPreviewSettings().providerSettingsViewModel
        )
    }
}

@ThemePreviews
@Composable
private fun EditNoticeDialogPreview() {
    PreviewTheme {
        EditNoticeDialog(EditNotice("nas", "Home NAS", "example.com", autoUpdateTurnedOff = true), viewModel = rememberPreviewSettings().providerSettingsViewModel)
    }
}
