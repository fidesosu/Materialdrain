package tools.senko.materialdrain.compat

import android.graphics.Bitmap
import android.graphics.Color
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import tools.senko.materialdrain.screenshots.AppDriver
import tools.senko.materialdrain.screenshots.Screenshots
import tools.senko.materialdrain.ui.theme.ThemeColorPresets
import tools.senko.materialdrain.ui.theme.ThemeMode

/**
 * The theme on this device's Android version (run on each, see app/build/compat/<api>): the main screens with the system
 * light or dark and the app set the other way, on black, and in a chosen colour, the only colours before Android 12. The
 * pictures are checked by eye (compat/<api>/…); what can be measured is checked here: the window behind the app follows
 * the app's light or dark, not the system's, so nothing flashes the other way when it opens.
 */
@RunWith(AndroidJUnit4::class)
class ThemeCompatTest {

    private val app = AppDriver()
    private val api = Build.VERSION.SDK_INT

    @After
    fun tearDown() {
        Screenshots.shell("cmd uimode night no")
        app.settings.setBlackBackground(false)
        app.settings.setWallpaperColors(true)
        app.reset()
    }

    private fun capture(name: String) = Screenshots.capture("compat/api$api/$name")

    /** How light the middle of the screen is, 0 (black) to 1 (white). */
    private fun middleLightness(bitmap: Bitmap): Float {
        val pixel = bitmap.getPixel(bitmap.width / 2, bitmap.height / 2)
        return (0.2126f * Color.red(pixel) + 0.7152f * Color.green(pixel) + 0.0722f * Color.blue(pixel)) / 255f
    }

    /** The main screens, each with a menu, a dialog and the fullscreen picture, under [prefix]. */
    private fun tour(prefix: String) {
        app.tapTab("Files")
        app.waitText("Sunset over the bay")
        Thread.sleep(600)
        capture("$prefix-files")
        app.tapDesc("More options for City lights.png")
        app.waitText("Download")
        capture("$prefix-menu")
        app.tap("Delete")
        Thread.sleep(600)
        capture("$prefix-dialog")
        app.device.pressBack()
        Thread.sleep(400)
        app.openFile("Sunset over the bay.jpg")
        Thread.sleep(600)
        capture("$prefix-details")
        app.openFullscreenPicture("Sunset over the bay.jpg")
        capture("$prefix-fullscreen")
        app.device.pressBack()
        app.back("Sunset over the bay")
        app.openScreenMenu()
        app.tap("Settings")
        app.tap("Look and feel")
        app.waitText("Light or dark")
        Thread.sleep(400)
        capture("$prefix-look-and-feel")
        app.device.pressBack()
        app.device.pressBack()
    }

    @Test
    fun appLightOnADarkSystem() {
        Screenshots.shell("cmd uimode night yes")
        app.start { setThemeMode(ThemeMode.LIGHT) }
        tour("light-on-dark-system")
    }

    @Test
    fun appDarkOnALightSystem() {
        Screenshots.shell("cmd uimode night no")
        app.start { setThemeMode(ThemeMode.DARK) }
        tour("dark-on-light-system")
    }

    @Test
    fun blackAndAChosenColour() {
        Screenshots.shell("cmd uimode night yes")
        app.start {
            setThemeMode(ThemeMode.DARK)
            setBlackBackground(true)
            setWallpaperColors(false)
            setThemeColor(ThemeColorPresets.first { it.first == "Teal" }.second)
        }
        tour("black-teal")
    }

    /**
     * A3: the app light on a dark system, opened again (as on a theme or language change): every picture of the screen
     * from then on is light. A dark one is the window behind the app, which follows the system.
     */
    @Test
    fun noDarkFlashWhenTheAppIsLight() {
        Screenshots.shell("cmd uimode night yes")
        app.start { setThemeMode(ThemeMode.LIGHT) }
        app.tapTab("Files")
        app.waitText("Sunset over the bay")
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val seen = mutableListOf<Float>()
        app.scenario!!.recreate()
        val until = System.currentTimeMillis() + 1_500
        var darkest: Bitmap? = null
        while (System.currentTimeMillis() < until) {
            val frame = automation.takeScreenshot() ?: continue
            val lightness = middleLightness(frame)
            if (seen.isEmpty() || lightness < seen.min()) darkest = frame
            seen += lightness
        }
        darkest?.let { frame ->
            androidx.test.services.storage.TestStorage().openOutputFile("screenshots/compat/api$api/a3-darkest-frame.png")
                .use { frame.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        app.waitText("Sunset over the bay")
        assertTrue(
            "A dark frame while the app is light (lightness of the middle of the screen: ${seen.joinToString { "%.2f".format(it) }}); see compat/api$api/a3-darkest-frame.png",
            seen.all { it > 0.5f }
        )
    }
}
