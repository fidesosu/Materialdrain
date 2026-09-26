package tools.senko.materialdrain.preferences

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import tools.senko.materialdrain.auth.ApiKeySource

/** Username/password login, e-mail login link and second factor. */
@Composable
fun SettingsEnvironment.LoginSection() {
    val authState by authViewModel.uiState.collectAsState()
    var username by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var totp by rememberSaveable { mutableStateOf("") }
    var pastedLink by rememberSaveable { mutableStateOf("") }

    // Never keep credentials around once they were used
    LaunchedEffect(authState.loggedInUser) {
        if (authState.loggedInUser != null) {
            password = ""
            totp = ""
            pastedLink = ""
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        val loggedInUser = authState.loggedInUser
        if (loggedInUser != null) {
            Text("Logged in as $loggedInUser", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(onClick = { authViewModel.logout() }, enabled = !authState.isBusy, modifier = Modifier.fillMaxWidth()) {
                if (authState.isBusy) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp) else Text("Log out")
            }
        } else {
            val canSubmit = !authState.isBusy && username.isNotBlank()
            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text("Username or e-mail address") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next)
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = {
                    if (canSubmit && password.isNotEmpty()) authViewModel.login(username, password, totp)
                })
            )
            if (authState.otpRequired) {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = totp,
                    onValueChange = { totp = it.filter { c -> c.isDigit() }.take(6) },
                    label = { Text("One-time password (2FA)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(
                    onClick = { authViewModel.login(username, password, totp) },
                    enabled = canSubmit && password.isNotEmpty(),
                    modifier = Modifier.weight(1f)
                ) {
                    if (authState.isBusy) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp) else Text("Log in")
                }
                TextButton(
                    onClick = { authViewModel.login(username, "", "") },
                    enabled = canSubmit
                ) { Text("E-mail me a login link") }
            }

            if (authState.loginLinkSent) {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = pastedLink,
                    onValueChange = { pastedLink = it },
                    label = { Text("Login link from the e-mail") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = { authViewModel.completeLinkLogin(username, pastedLink, password, totp) },
                    enabled = canSubmit && pastedLink.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Finish login") }
            }
        }

        authState.errorMessage?.let {
            Spacer(modifier = Modifier.height(8.dp))
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.fillMaxWidth())
        }
        authState.infoMessage?.let {
            Spacer(modifier = Modifier.height(8.dp))
            Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall, modifier = Modifier.fillMaxWidth())
        }
    }
}

/** The API key field, which is saved with the Save button of the screen. */
@Composable
fun SettingsEnvironment.ApiKeySection() {
    val authState by authViewModel.uiState.collectAsState()
    Column(modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = apiKeyInput,
            onValueChange = { onApiKeyInputChange(it) },
            label = { Text("Pixeldrain API Key") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = when (authState.activeSource) {
                ApiKeySource.LOGIN -> "Your login is used for requests. This API key is used again after you log out."
                ApiKeySource.MANUAL -> "This API key is used for requests. Logging in above replaces it while you are logged in."
                ApiKeySource.NONE -> "Log in above, or enter an API key and press Save Settings."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
