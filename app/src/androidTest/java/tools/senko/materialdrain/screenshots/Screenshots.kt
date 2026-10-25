package tools.senko.materialdrain.screenshots

import android.graphics.Bitmap
import android.os.ParcelFileDescriptor
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.services.storage.TestStorage

/**
 * Taking the pictures: the whole screen as the device shows it, menus and dialogs included (they're windows of their own,
 * which a picture of the Compose tree would miss), under a status bar that's the same every time.
 */
object Screenshots {
    private val automation get() = InstrumentationRegistry.getInstrumentation().uiAutomation

    private fun shell(command: String) {
        ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand(command)).use { it.readBytes() }
    }

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
     * Saves the screen as [name].png in the test's output (TestStorage), which Gradle copies to
     * app/build/outputs/connected_android_test_additional_output after the run (and recordScreenshots on from there).
     */
    fun capture(name: String) {
        // Lets the last frame (and pictures fading in) settle first
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        Thread.sleep(300)
        val bitmap = automation.takeScreenshot() ?: error("no screenshot for $name")
        TestStorage().openOutputFile("screenshots/$name.png").use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
