package tools.senko.materialdrain.security

import android.app.KeyguardManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import tools.senko.materialdrain.screenshots.AppDriver
import tools.senko.materialdrain.screenshots.Screenshots

/**
 * The song playing in the background on the device's lock screen: with the app lock on, nothing of it shows there (its
 * name, its cover), as with the transfers; without the lock it shows as Android shows songs. The device gets a PIN, so its
 * lock screen is a secure one.
 */
@RunWith(AndroidJUnit4::class)
class LockScreenSongTest {

    private val app = AppDriver()

    @Before
    fun setPin() {
        Screenshots.shell("locksettings set-pin $PIN")
    }

    @After
    fun tearDown() {
        app.unlockDevice()
        app.settings.setBiometricLock(false)
        app.settings.setBackgroundAudio(false)
        app.reset()
        Screenshots.shell("locksettings clear --old $PIN")
    }

    /** Plays Night drive in the background, turns the app lock on or off, and shows the device's lock screen. */
    private fun playThenLockTheDevice(appLock: Boolean, picture: String) {
        app.start { setBackgroundAudio(true) }
        app.openFolder("Music")
        // It may play on from the test before: the player goes on in the background
        app.openFile("Night drive.mp3", By.desc(java.util.regex.Pattern.compile("Play|Pause")))
        if (app.shows(By.desc("Play"), 500)) app.tapDesc("Play")
        app.find(By.desc("Pause"), "playing")
        app.settings.setBiometricLock(appLock)
        app.device.pressHome()
        Screenshots.shell("input keyevent KEYCODE_SLEEP")
        Thread.sleep(1_500)
        Screenshots.shell("input keyevent KEYCODE_WAKEUP")
        Thread.sleep(2_500)
        Screenshots.capture("security/$picture")
    }

    // Its title from its tags, "Night Drive", or its file name
    private fun songOnTheLockScreen() = app.shows(By.text(java.util.regex.Pattern.compile("(?is).*night drive.*")), 3_000)

    @Test
    fun withoutTheAppLockTheSongShows() {
        playThenLockTheDevice(appLock = false, picture = "lock-screen-song")
        assertTrue("The song should show on the lock screen without the app lock", songOnTheLockScreen())
    }

    @Test
    fun withTheAppLockTheSongIsHidden() {
        playThenLockTheDevice(appLock = true, picture = "lock-screen-song-hidden")
        assertFalse("The song's name is on the lock screen while the app lock is on", songOnTheLockScreen())
        // Unlocked, it's the song again, in the notifications
        app.unlockDevice()
        val keyguard = InstrumentationRegistry.getInstrumentation().targetContext.getSystemService(KeyguardManager::class.java)
        assertFalse("The device didn't unlock", keyguard.isKeyguardLocked)
        Thread.sleep(1_500)
        app.device.openNotification()
        val back = songOnTheLockScreen()
        Screenshots.capture("security/notifications-after-unlock")
        app.device.pressBack()
        assertTrue("The song should be named again once the device is unlocked", back)
    }
}
