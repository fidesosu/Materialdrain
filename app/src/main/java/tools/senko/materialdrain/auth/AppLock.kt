package tools.senko.materialdrain.auth

import android.os.Build
import android.os.SystemClock
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import tools.senko.materialdrain.UiMessages
import tools.senko.materialdrain.settings.AppSettings

/**
 * A fingerprint or face, or the device's screen lock when there is no biometric. Used by the lock prompt. Android 10
 * can't ask for a strong biometric or the screen lock in one prompt (androidx.biometric refuses that pair before
 * Android 11), so there any biometric the device has, or the screen lock.
 */
val LOCK_AUTHENTICATORS: Int =
    if (Build.VERSION.SDK_INT >= 30) BIOMETRIC_STRONG or DEVICE_CREDENTIAL else BIOMETRIC_WEAK or DEVICE_CREDENTIAL

/**
 * What [BiometricManager.canAuthenticate] says when the device has nothing at all to unlock with: no screen lock (so no
 * biometric either), or no way to ask. A passing problem (the sensor busy) isn't one of them: the screen lock still works.
 */
private val NOTHING_TO_UNLOCK_WITH = setOf(
    BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED,
    BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE,
    BiometricManager.BIOMETRIC_ERROR_UNSUPPORTED
)

/**
 * Whether the app is locked behind the biometric prompt. It locks when opened, and again when it comes back from the
 * background after the lock timeout (AppSettings.lockTimeoutSeconds: short absences, switching to another app and back,
 * don't ask again unless it's 0). Nothing is locked while the setting is off.
 *
 * When the device no longer has a screen lock, the lock can't be opened any more: it's turned off then, with a message,
 * rather than shutting the app for good. Only the device's owner can remove the screen lock, as that takes it.
 */
class AppLock(private val settings: AppSettings, private val messages: UiMessages) {

    private val _locked = MutableStateFlow(settings.biometricLock.value && turnedOffIfUnusable())
    val locked: StateFlow<Boolean> = _locked.asStateFlow()

    private var stoppedAt: Long? = null

    fun onStarted() {
        val timeoutMs = settings.lockTimeoutSeconds.value * 1000L
        val wasAway = stoppedAt?.let { SystemClock.elapsedRealtime() - it >= timeoutMs } ?: true
        if (settings.biometricLock.value && wasAway && turnedOffIfUnusable()) _locked.value = true
        if (!settings.biometricLock.value) _locked.value = false
    }

    fun onStopped() {
        stoppedAt = SystemClock.elapsedRealtime()
    }

    fun unlock() {
        _locked.value = false
    }

    /** After the prompt failed: opens the app if the lock can't be opened any more (see [turnedOffIfUnusable]). */
    fun promptFailed() {
        if (_locked.value && !turnedOffIfUnusable()) _locked.value = false
    }

    /**
     * Whether the lock can still be opened; when it can't (the device has nothing to unlock with), it's turned off, and
     * the app says so. Called with the setting on.
     */
    private fun turnedOffIfUnusable(): Boolean {
        if (settings.biometricLockStatus() !in NOTHING_TO_UNLOCK_WITH) return true
        settings.setBiometricLock(false)
        messages.show("The app lock was turned off: this device has no screen lock any more.", long = true)
        return false
    }
}
