package tools.senko.materialdrain.auth

import android.os.SystemClock
import androidx.biometric.BiometricManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import tools.senko.materialdrain.settings.AppSettings

/** A fingerprint or face, or the device's screen lock when there is no biometric. Used by the lock prompt. */
const val LOCK_AUTHENTICATORS = BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL

/** Short absences (switching to another app and back) don't ask again, a longer one does. */
private const val LOCK_AFTER_BACKGROUND_MS = 30_000L

/**
 * Whether the app is locked behind the biometric prompt. It locks when opened, and again when it comes back from the
 * background after [LOCK_AFTER_BACKGROUND_MS]. Nothing is locked while the setting is off.
 */
class AppLock(private val settings: AppSettings) {

    private val _locked = MutableStateFlow(settings.biometricLock.value)
    val locked: StateFlow<Boolean> = _locked.asStateFlow()

    private var stoppedAt: Long? = null

    fun onStarted() {
        val wasAway = stoppedAt?.let { SystemClock.elapsedRealtime() - it > LOCK_AFTER_BACKGROUND_MS } ?: true
        if (settings.biometricLock.value && wasAway) _locked.value = true
        if (!settings.biometricLock.value) _locked.value = false
    }

    fun onStopped() {
        stoppedAt = SystemClock.elapsedRealtime()
    }

    fun unlock() {
        _locked.value = false
    }
}
