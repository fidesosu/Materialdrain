package tools.senko.materialdrain.preferences

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
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
import tools.senko.materialdrain.provider.StoredProvider
import tools.senko.materialdrain.provider.api.ConfigUpdates
import tools.senko.materialdrain.provider.api.GenericRestConfig
import tools.senko.materialdrain.provider.api.PasswordAuthMode
import tools.senko.materialdrain.provider.api.ProviderCapability
import tools.senko.materialdrain.provider.api.ProviderConfig
import tools.senko.materialdrain.provider.api.S3Config
import tools.senko.materialdrain.provider.api.WebDavConfig
import tools.senko.materialdrain.provider.api.passwordAuth
import tools.senko.materialdrain.ui.LocalReduceMotion

private fun kindLabel(config: ProviderConfig): String = when (config) {
    is GenericRestConfig -> "Generic REST"
    is WebDavConfig -> "WebDAV"
    is S3Config -> "S3-compatible"
}

/** "Custom host settings": the list of imported configs, their update state, and importing new ones. */
@Composable
fun SettingsEnvironment.ProviderHostsSection() {
    val viewModel = providerSettingsViewModel
    val uiState by viewModel.uiState.collectAsState()
    var importText by rememberSaveable { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            "Pixeldrain is always available and is the default. Custom hosts below can be tested and " +
                "kept configured, but browsing/uploading through them from the rest of the app is still being built.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (uiState.providers.any { it.updateUrl != null }) {
            TextButton(onClick = { viewModel.checkAllForUpdates() }, enabled = uiState.checkingIds.isEmpty()) {
                Text("Check all for updates")
            }
        }

        if (uiState.providers.isNotEmpty()) {
            Spacer(modifier = Modifier.height(4.dp))
            uiState.providers.forEach { stored -> HostCard(stored, uiState, viewModel) }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text("Add a custom host", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            "Paste a provider config here, as JSON (with or without the MATERIALDRAIN-PROVIDER-CONFIG-V1 first " +
                "line this app adds when exporting one). Configs never contain passwords or tokens, so they're " +
                "safe to share; sign-in details are entered here in the app, per host.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = importText,
            onValueChange = { importText = it; viewModel.clearImportError() },
            label = { Text("Provider config") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 4
        )
        uiState.importError?.let {
            Spacer(modifier = Modifier.height(4.dp))
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(
            onClick = { if (viewModel.importConfig(importText)) importText = "" },
            enabled = importText.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) { Text("Import host") }
    }

    uiState.editingId?.let { id -> EditConfigDialog(id, uiState.editError, viewModel) }
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
        title = { Text("Two-factor authentication") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Enter the ${prompt.digits}-digit code from your authenticator app to sign in to ${prompt.hostName}.")
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it.filter(Char::isDigit).take(prompt.digits) },
                    label = { Text("Code") },
                    singleLine = true,
                    isError = prompt.error != null && code == prompt.rejectedCode,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { if (complete && !busy) viewModel.submitOtp(code) }),
                    modifier = Modifier.fillMaxWidth()
                )
                if (code.isNotEmpty() && code == lastPasted) {
                    Text("Pasted from the clipboard", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                prompt.error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(onClick = { viewModel.submitOtp(code) }, enabled = complete && !busy) {
                if (busy) CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp) else Text("Sign in")
            }
        },
        dismissButton = { TextButton(onClick = { viewModel.cancelOtp() }) { Text("Cancel") } }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HostCard(
    stored: StoredProvider,
    uiState: ProviderSettingsUiState,
    viewModel: ProviderSettingsViewModel
) {
    var expanded by rememberSaveable(stored.id) { mutableStateOf(false) }
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
    val signedInAs = uiState.signedInAs[stored.id]
    val signingIn = uiState.signingInId == stored.id
    var justCopied by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    val reduceMotion = LocalReduceMotion.current
    val busy = uiState.busyId == stored.id
    val checking = stored.id in uiState.checkingIds
    val meta = stored.config.meta
    val pending = stored.pendingUpdate

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surfaceContainerHigh),
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
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
                        listOfNotNull(kindLabel(stored.config), meta?.let { "v${it.version}" }, meta?.author?.let { "by $it" }).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant
                    )
                    val statuses = listOfNotNull(
                        "Edited".takeIf { stored.isEdited },
                        pending?.let { "Version ${it.meta?.version ?: 0} available" }
                    )
                    if (statuses.isNotEmpty()) {
                        Text(statuses.joinToString(" · "), style = MaterialTheme.typography.labelMedium, color = colors.tertiary)
                    }
                    signedInAs?.let {
                        Text("Signed in as $it", style = MaterialTheme.typography.labelMedium, color = colors.primary)
                    }
                }
                if (pending != null) {
                    // Slightly highlighted: a tonal button stands out from the plain edit icon next to it
                    FilledTonalIconButton(onClick = { viewModel.requestApplyUpdate(stored.id) }) {
                        Icon(Icons.Filled.Download, contentDescription = "Update to version ${pending.meta?.version ?: 0}")
                    }
                }
                IconButton(onClick = { viewModel.startEdit(stored.id) }) {
                    Icon(Icons.Filled.Edit, contentDescription = "Edit config")
                }
                Icon(
                    if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = if (expanded) "Collapse" else "Expand",
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
                                Text("Auto-update from ${ConfigUpdates.hostOf(url) ?: url}", style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    "Checked when the app starts, at most once a day. Updates that change where your sign-in is sent always wait for you.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.onSurfaceVariant
                                )
                            }
                            Switch(checked = stored.autoUpdate, onCheckedChange = { viewModel.setAutoUpdate(stored.id, it) })
                        }
                    }

                    if (stored.config is S3Config) {
                        // S3 always needs both halves of a key pair - there's no single "API key" concept to
                        // fall back to, unlike WebDAV/generic REST hosts.
                        Text("Credentials", style = MaterialTheme.typography.labelLarge, color = colors.primary)
                        OutlinedTextField(
                            value = username,
                            onValueChange = { username = it },
                            label = { Text("Access Key ID") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            label = { Text("Secret Access Key") },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        Text("Sign-in", style = MaterialTheme.typography.labelLarge, color = colors.primary)
                        when (passwordMode) {
                            // A session, like signing in to any app: the password is used once, only the session is kept
                            PasswordAuthMode.LOGIN -> if (signedInAs != null) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                    Text("Signed in as $signedInAs", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                                    OutlinedButton(onClick = { viewModel.signOut(stored.id) }) { Text("Sign out") }
                                }
                            } else {
                                val canSignIn = username.isNotBlank() && password.isNotEmpty() && !signingIn
                                OutlinedTextField(
                                    value = username,
                                    onValueChange = { username = it },
                                    label = { Text("Username or e-mail") },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                                    modifier = Modifier.fillMaxWidth()
                                )
                                OutlinedTextField(
                                    value = password,
                                    onValueChange = { password = it },
                                    label = { Text("Password") },
                                    singleLine = true,
                                    visualTransformation = PasswordVisualTransformation(),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                                    keyboardActions = KeyboardActions(onDone = { if (canSignIn) viewModel.signIn(stored.id, username, password) }),
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Button(
                                    onClick = { viewModel.signIn(stored.id, username, password) },
                                    enabled = canSignIn,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    if (signingIn) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp) else Text("Sign in")
                                }
                                uiState.signInErrors[stored.id]?.let {
                                    Text(it, style = MaterialTheme.typography.bodySmall, color = colors.error)
                                }
                            }
                            // Sent with every request, so it has to be kept
                            PasswordAuthMode.BASIC -> {
                                OutlinedTextField(
                                    value = username,
                                    onValueChange = { username = it },
                                    label = { Text("Username") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                OutlinedTextField(
                                    value = password,
                                    onValueChange = { password = it },
                                    label = { Text("Password") },
                                    singleLine = true,
                                    visualTransformation = PasswordVisualTransformation(),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            null -> {}
                        }

                        // The API key is the secondary way in for hosts that take a username and password
                        if (passwordMode == null || showApiKey) {
                            OutlinedTextField(
                                value = apiKey,
                                onValueChange = { apiKey = it },
                                label = { Text("API key") },
                                singleLine = true,
                                visualTransformation = PasswordVisualTransformation(),
                                modifier = Modifier.fillMaxWidth()
                            )
                            Text(
                                when (passwordMode) {
                                    PasswordAuthMode.LOGIN -> "Used while you're not signed in."
                                    PasswordAuthMode.BASIC -> "Used when the username and password aren't both filled in."
                                    null -> "This host only takes an API key."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.onSurfaceVariant
                            )
                        } else {
                            TextButton(onClick = { showApiKey = true }) { Text("Use an API key instead") }
                        }
                    }

                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                if (stored.config is S3Config) {
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
                            else Text(if (passwordMode == PasswordAuthMode.LOGIN && !showApiKey) "Test connection" else "Save & test")
                        }
                        if (stored.updateUrl != null) {
                            OutlinedButton(onClick = { viewModel.checkForUpdates(stored.id) }, enabled = !checking) {
                                if (checking) CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp) else Text("Check for updates")
                            }
                        }
                        TextButton(onClick = {
                            viewModel.exportConfig(stored.id)?.let { text ->
                                val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboardManager.setPrimaryClip(ClipData.newPlainText("Provider config", text))
                            }
                            justCopied = true
                        }) { Text(if (justCopied) "Copied" else "Export") }
                        if (stored.previous != null) {
                            TextButton(onClick = { viewModel.revert(stored.id) }) { Text("Revert update") }
                        }
                        TextButton(onClick = { viewModel.remove(stored.id) }) { Text("Remove") }
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

@Composable
private fun EditConfigDialog(id: String, error: String?, viewModel: ProviderSettingsViewModel) {
    var text by rememberSaveable(id) { mutableStateOf(viewModel.exportConfig(id).orEmpty()) }
    AlertDialog(
        onDismissRequest = { viewModel.cancelEdit() },
        title = { Text("Edit config") },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 200.dp, max = 420.dp)
                )
                error?.let {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = { TextButton(onClick = { viewModel.saveEdit(id, text) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = { viewModel.cancelEdit() }) { Text("Cancel") } }
    )
}

@Composable
private fun UpdateApprovalDialog(approval: UpdateApproval, viewModel: ProviderSettingsViewModel) {
    AlertDialog(
        onDismissRequest = { viewModel.dismissApproval() },
        title = { Text("Review update") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${approval.hostName}: version ${approval.fromVersion} → ${approval.toVersion}")
                if (approval.sensitiveChanges.isNotEmpty()) {
                    Text("This update changes where or how your sign-in details are sent:")
                    approval.sensitiveChanges.forEach { change ->
                        Text("• ${change.description}", style = MaterialTheme.typography.bodyMedium)
                    }
                    Text(
                        "Only update if you trust where this config comes from.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (approval.clearsToken) {
                    Text(
                        "Your saved API key, username and password will be removed. Enter them again once you've checked the new address.",
                        color = MaterialTheme.colorScheme.error
                    )
                }
                if (approval.discardsEdits) Text("Updating replaces the edits you made to this config.")
            }
        },
        confirmButton = { TextButton(onClick = { viewModel.confirmApproval() }) { Text("Update") } },
        dismissButton = { TextButton(onClick = { viewModel.dismissApproval() }) { Text("Cancel") } }
    )
}

@Composable
private fun EditNoticeDialog(notice: EditNotice, viewModel: ProviderSettingsViewModel) {
    AlertDialog(
        onDismissRequest = { viewModel.dismissEditNotice() },
        title = { Text("This config is edited") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${notice.hostName} still updates from ${notice.updateHost ?: "its update URL"}, so an update would overwrite your changes.")
                if (notice.autoUpdateTurnedOff) {
                    Text("Auto-update has been turned off for it. You can turn it back on if you want updates to replace your edits.")
                }
            }
        },
        confirmButton = { TextButton(onClick = { viewModel.removeUpdateLink(notice.id) }) { Text("Stop updating") } },
        dismissButton = { TextButton(onClick = { viewModel.dismissEditNotice() }) { Text("Keep updates") } }
    )
}
