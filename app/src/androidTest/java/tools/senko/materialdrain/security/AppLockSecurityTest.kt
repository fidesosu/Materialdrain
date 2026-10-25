package tools.senko.materialdrain.security

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import tools.senko.materialdrain.screenshots.AppDriver
import tools.senko.materialdrain.screenshots.Screenshots

/** The device's screen lock while these run, which the app lock asks for. */
internal const val PIN = AppDriver.DEVICE_PIN

/**
 * What the app lock keeps from being seen or read until it's unlocked: the files on screen (also in dialogs and menus,
 * which are windows of their own), what the app fetches, and the recent apps' picture of it. Each test is written as the
 * lock should behave, so a failing one is a way past it; the pictures (security/…) show what was on the screen.
 *
 * The device gets a PIN for them (adb's locksettings), removed after. In name order: the first starts the app cold.
 */
@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class AppLockSecurityTest {

    private val app = AppDriver()

    @Before
    fun setPin() {
        Screenshots.shell("locksettings set-pin $PIN")
    }

    @After
    fun tearDown() {
        app.reset()
        Screenshots.shell("locksettings clear --old $PIN")
    }

    /** Closes the system's prompt over the app, as someone without the PIN would, leaving the app's own lock screen. */
    private fun dismissPrompt() = app.dismissUnlockPrompt()

    /** Unlocks as the prompt would on success (the test doesn't type the PIN into the system's prompt). */
    private fun unlock() {
        dismissPrompt()
        InstrumentationRegistry.getInstrumentation().runOnMainSync { app.container.appLock.unlock() }
        app.device.waitForIdle()
    }

    /** To the home screen, and back to the app as from its icon, after the lock's time ("Always" in these tests). */
    private fun leaveAndReturn() {
        app.device.pressHome()
        Thread.sleep(1500)
        val context = ApplicationProvider.getApplicationContext<Context>()
        val launch = requireNotNull(context.packageManager.getLaunchIntentForPackage(context.packageName))
        app.unlockDevice()
        context.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        Thread.sleep(1500)
    }

    /** S2: opened locked, nothing of the account is fetched before it's unlocked. */
    @Test
    fun a_nothingIsFetchedWhileLocked() {
        // "Always": the app locks as it starts, before its first frame, as on a cold start (the tests share one process,
        // in which the app was just open)
        app.prepare { setBiometricLock(true); setLockTimeoutSeconds(0) }
        app.fake.requests.clear()
        app.launch()
        app.waitLocked()
        // Time for the screens under the lock to load whatever they would
        Thread.sleep(5_000)
        Screenshots.capture("security/s2-locked")
        val fetched = app.fake.requests.toList().filter { it.startsWith("/api/") }
        assertTrue("Fetched from the host while locked: ${fetched.distinct()}", fetched.isEmpty())
    }

    /** S1: a picture open fullscreen (a dialog, a window of its own) is closed or covered when the app locks again. */
    @Test
    fun b_fullscreenPictureIsCoveredByTheLock() {
        app.prepare { setBiometricLock(true); setLockTimeoutSeconds(0) }
        app.launch()
        unlock()
        app.tapTab("Files")
        app.waitText("Sunset over the bay")
        app.openFile("Sunset over the bay.jpg")
        Thread.sleep(500)
        app.openFullscreenPicture("Sunset over the bay.jpg")

        leaveAndReturn()
        dismissPrompt()
        app.waitLocked()
        Screenshots.capture("security/s1-fullscreen-over-lock")
        assertFalse(
            "The fullscreen picture is still open over the lock (see security/s1-fullscreen-over-lock.png)",
            app.shows(By.desc("Fullscreen image preview"), 2_000)
        )
    }

    /** S1: a file's menu (a popup, a window of its own) is closed or covered when the app locks again. */
    @Test
    fun c_fileMenuIsCoveredByTheLock() {
        app.prepare { setBiometricLock(true); setLockTimeoutSeconds(0) }
        app.launch()
        unlock()
        app.tapTab("Files")
        app.waitText("Sunset over the bay")
        app.tapDesc("More options for City lights.png")
        app.waitText("Download")

        leaveAndReturn()
        dismissPrompt()
        app.waitLocked()
        Screenshots.capture("security/s1-menu-over-lock")
        assertFalse(
            "The file's menu is still open over the lock (see security/s1-menu-over-lock.png)",
            app.shows(By.text("Download"), 2_000)
        )
    }

    /**
     * S3: the recent apps' picture of the app, taken as it goes to the background. Not checked here: the picture is only
     * pixels; security/s3-recents.png shows whether the files can be read in it.
     */
    @Test
    fun d_recentAppsPicture() {
        app.prepare { setBiometricLock(true); setLockTimeoutSeconds(0) }
        app.launch()
        unlock()
        app.tapTab("Files")
        app.waitText("Sunset over the bay")
        Thread.sleep(1000)
        app.device.pressHome()
        Thread.sleep(1500)
        app.device.pressRecentApps()
        Thread.sleep(2500)
        Screenshots.capture("security/s3-recents")
        app.device.pressHome()
    }
}
