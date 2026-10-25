package tools.senko.materialdrain.auth

import androidx.biometric.BiometricPrompt
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The lock prompt can be built on every Android the app runs on (10 and up): androidx.biometric refuses some combinations
 * of what's asked for on older versions (a fingerprint or the screen lock, STRONG or DEVICE_CREDENTIAL, before Android 11),
 * by throwing when the prompt is built, which happens as the app opens locked.
 */
@RunWith(RobolectricTestRunner::class)
class AppLockTest {

    private fun buildPrompt() {
        try {
            BiometricPrompt.PromptInfo.Builder()
                .setTitle("Unlock Materialdrain")
                .setAllowedAuthenticators(LOCK_AUTHENTICATORS)
                .build()
        } catch (e: IllegalArgumentException) {
            fail("The lock prompt can't be built here: ${e.message}")
        }
    }

    @Test
    @Config(sdk = [29])
    fun promptBuildsOnAndroid10() = buildPrompt()

    @Test
    @Config(sdk = [30])
    fun promptBuildsOnAndroid11() = buildPrompt()

    @Test
    @Config(sdk = [35])
    fun promptBuildsOnAndroid15() = buildPrompt()
}
