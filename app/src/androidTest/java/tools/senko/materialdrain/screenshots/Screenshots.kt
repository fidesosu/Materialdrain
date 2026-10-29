package tools.senko.materialdrain.screenshots

import android.graphics.Bitmap
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.services.storage.TestStorage

/**
 * Taking the pictures: the whole screen as the device shows it, menus and dialogs included (they're windows of their own,
 * which a picture of the Compose tree would miss), under a status bar that's the same every time.
 */
object Screenshots {
    private val automation get() = InstrumentationRegistry.getInstrumentation().uiAutomation

    /** Runs [command] as adb's shell does (settings, cmd, locksettings...), and returns what it printed. */
    fun shell(command: String): String =
        ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand(command)).use { it.readBytes().decodeToString() }

    /** SystemUI's demo mode: 12:00, full battery and signal, no notifications. */
    fun cleanStatusBar() {
        shell("settings put global sysui_demo_allowed 1")
        fun demo(extras: String) = shell("am broadcast -a com.android.systemui.demo $extras")
        demo("-e command enter")
        demo("-e command clock -e hhmm 1200")
        demo("-e command battery -e level 100 -e plugged false")
        demo("-e command network -e wifi show -e level 4 -e fully true")
        demo("-e command network -e mobile show -e datatype none -e level 4 -e fully true")
        demo("-e command notifications -e visible false")
    }

    fun leaveDemoMode() = shell("am broadcast -a com.android.systemui.demo -e command exit")

    /**
     * No "isn't responding" or "keeps stopping" dialogs of other apps: on the CI's slow emulator the launcher stalls now
     * and then, and its dialog over the app hid the texts the tests wait for.
     */
    fun hideErrorDialogs() {
        shell("settings put global hide_error_dialogs 1")
    }

    /**
     * Saves the screen as [name].png in the test's output (TestStorage), which Gradle copies to
     * app/build/outputs/connected_android_test_additional_output after the run (and recordScreenshots on from there).
     */
    fun capture(name: String) {
        // Lets the last frame (and pictures fading in) settle first
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        Thread.sleep(300)
        // None while the app blocks screenshots (FLAG_SECURE: the setting, or the app lock before Android 13)
        val bitmap = automation.takeScreenshot() ?: run {
            Log.w("Screenshots", "no picture of $name: screenshots are blocked")
            return
        }
        TestStorage().openOutputFile("screenshots/$name.png").use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
