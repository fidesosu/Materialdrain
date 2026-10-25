package tools.senko.materialdrain.security

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import tools.senko.materialdrain.screenshots.AppDriver
import tools.senko.materialdrain.screenshots.Screenshots

/**
 * The app lock on each Android version, with and without a screen lock on the device: it can be turned on where the
 * device has a screen lock, turned off whatever the device has, and never leaves the app shut for good.
 */
@RunWith(AndroidJUnit4::class)
class AppLockCompatTest {

    private val app = AppDriver()
    private val api = Build.VERSION.SDK_INT

    @After
    fun tearDown() {
        app.reset()
        Screenshots.shell("locksettings clear --old $PIN")
    }

    private fun openPrivacySettings() {
        app.tapTab("Files")
        app.waitText("Sunset over the bay")
        app.openScreenMenu()
        app.tap("Settings")
        app.tap("Privacy and security")
        app.waitText("Lock with fingerprint or face")
    }

    /** A2: with a PIN on the device, the switch turns the lock on (on Android 10 as well). */
    @Test
    fun lockTurnsOnWithAScreenLock() {
        Screenshots.shell("locksettings set-pin $PIN")
        app.start()
        openPrivacySettings()
        app.tap("Lock with fingerprint or face")
        Thread.sleep(800)
        Screenshots.capture("security/a2-lock-switch-api$api")
        assertTrue("The lock didn't turn on, with a PIN on the device (Android API $api)", app.settings.biometricLock.value)
    }

    /** A1: the screen lock removed while the app was unlocked, the switch still turns the app lock off. */
    @Test
    fun lockTurnsOffAfterTheScreenLockIsRemoved() {
        Screenshots.shell("locksettings set-pin $PIN")
        app.prepare { setBiometricLock(true); setLockTimeoutSeconds(300) }
        app.launch()
        app.dismissUnlockPrompt()
        InstrumentationRegistry.getInstrumentation().runOnMainSync { app.container.appLock.unlock() }
        openPrivacySettings()
        Screenshots.shell("locksettings clear --old $PIN")
        app.tap("Lock with fingerprint or face")
        Thread.sleep(800)
        Screenshots.capture("security/a1-switch-without-screen-lock-api$api")
        assertFalse("The lock can't be turned off once the device has no screen lock", app.settings.biometricLock.value)
    }

    /**
     * A1: the app lock on, but the device's screen lock gone (removed while the app was closed): the app opens, and the
     * lock is off, rather than staying behind a prompt that can't show.
     */
    @Test
    fun appOpensWhenTheScreenLockIsGone() {
        app.prepare { setBiometricLock(true); setLockTimeoutSeconds(0) }
        app.launch()
        app.tapTab("Files")
        app.waitText("Sunset over the bay")
        Screenshots.capture("security/a1-opened-without-screen-lock-api$api")
        assertFalse("Still locked, with no screen lock on the device", app.container.appLock.locked.value)
        assertFalse("The lock is still on, which nothing can open", app.settings.biometricLock.value)
    }
}
