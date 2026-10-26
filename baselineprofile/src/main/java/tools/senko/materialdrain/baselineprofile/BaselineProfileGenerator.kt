package tools.senko.materialdrain.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The baseline profile: what the app runs as it starts and is first used, which Android then compiles ahead of time
 * instead of interpreting it the first times. Here the app as it is on a new device (no account): it starts, shows its
 * screens, and opens the settings and a few of their pages. ./gradlew :app:generateBaselineProfile with an emulator or
 * phone connected (Android 13 or newer, or a rooted one).
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() = rule.collect(packageName = TARGET_PACKAGE, includeInStartupProfile = true) {
        pressHome()
        startActivityAndWait()
        device.wait(Until.hasObject(By.text("Files")), 5_000)
        // The screens of the navigation bar
        for (screen in listOf("Lists", "Filesystem", "Files")) {
            device.findObject(By.text(screen))?.click()
            device.waitForIdle()
        }
        // The settings and some of their pages
        device.findObject(By.desc("More options"))?.click()
        device.wait(Until.hasObject(By.text("Settings")), 2_000)
        device.findObject(By.text("Settings"))?.click()
        device.wait(Until.hasObject(By.text("Look and feel")), 3_000)
        for (page in listOf("Look and feel", "About")) {
            device.findObject(By.text(page))?.click() ?: continue
            device.waitForIdle()
            device.pressBack()
            device.waitForIdle()
        }
    }
}

/** The app being measured: the release build's package. */
internal const val TARGET_PACKAGE = "tools.senko.materialdrain"
