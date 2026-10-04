package tools.senko.materialdrain.auth

import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/**
 * Covers the app while it is locked, and asks for the fingerprint, face or screen lock straight away. The overlay
 * takes all touches, so nothing underneath can be used until the prompt succeeds.
 */
@Composable
fun AppLockOverlay(activity: FragmentActivity, appLock: AppLock) {
    val locked by appLock.locked.collectAsState()
    if (!locked) return

    LaunchedEffect(Unit) { showUnlockPrompt(activity) { appLock.unlock() } }

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                // Consumes every touch, so the app underneath doesn't react to it
                awaitPointerEventScope { while (true) awaitPointerEvent().changes.forEach { it.consume() } }
            },
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                Icons.Filled.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(64.dp)
            )
            Spacer(Modifier.height(16.dp))
            Text("Materialdrain is locked", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(24.dp))
            Button(onClick = { showUnlockPrompt(activity) { appLock.unlock() } }) { Text("Unlock") }
        }
    }
}

/** The system prompt for a fingerprint, face or the screen lock. [onUnlocked] runs when it succeeds. */
fun showUnlockPrompt(activity: FragmentActivity, onUnlocked: () -> Unit) {
    val callback = object : BiometricPrompt.AuthenticationCallback() {
        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
            onUnlocked()
        }
    }
    BiometricPrompt(activity, ContextCompat.getMainExecutor(activity), callback).authenticate(
        BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock Materialdrain")
            .setAllowedAuthenticators(LOCK_AUTHENTICATORS)
            .build()
    )
}
