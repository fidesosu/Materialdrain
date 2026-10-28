package tools.senko.materialdrain.screenshots

import android.app.Application
import android.app.KeyguardManager
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.StaleObjectException
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.UiScrollable
import androidx.test.uiautomator.UiSelector
import androidx.test.uiautomator.Until
import tools.senko.materialdrain.AppContainer
import tools.senko.materialdrain.MainActivity
import tools.senko.materialdrain.provider.api.HttpInterceptors
import tools.senko.materialdrain.provider.pixeldrain.internal.LoginSession
import tools.senko.materialdrain.settings.AppSettings
import tools.senko.materialdrain.ui.theme.ThemeMode

/**
 * The real app on the made-up account (FakePixeldrain), driven through the screen as a person would, with UiAutomator: by
 * the texts and descriptions it shows, so menus and dialogs (windows of their own) are reached too. Shared by the tests of
 * the app as a whole: the screenshots, the theme and the lock on each Android version.
 */
class AppDriver {
    val device: UiDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    /** The made-up host: one for all the tests run together, as the app's HTTP clients keep the first they're given. */
    val fake: FakePixeldrain get() = sharedFake
    val container: AppContainer get() = AppContainer.get(ApplicationProvider.getApplicationContext<Application>())
    val settings: AppSettings get() = container.appSettings
    var scenario: ActivityScenario<MainActivity>? = null
        private set

    /**
     * Opens the device's own lock screen if it's showing (the lock tests give the device a PIN, [DEVICE_PIN], and a slow
     * emulator may lock itself meanwhile), so the app can be reached.
     */
    fun unlockDevice() {
        val keyguard = InstrumentationRegistry.getInstrumentation().targetContext.getSystemService(KeyguardManager::class.java)
        // A few tries: the PIN typed while the lock screen's keypad is still coming up is lost
        repeat(3) {
            if (!keyguard.isKeyguardLocked) return
            Screenshots.shell("input keyevent KEYCODE_WAKEUP")
            Screenshots.shell("wm dismiss-keyguard")
            Thread.sleep(1_000)
            if (keyguard.isKeyguardLocked) {
                Screenshots.shell("input text $DEVICE_PIN")
                Screenshots.shell("input keyevent KEYCODE_ENTER")
                Thread.sleep(1_500)
            }
        }
    }

    /** Signs in to the made-up account and sets [setup], without starting the app (see [launch]). */
    fun prepare(setup: AppSettings.() -> Unit = {}) {
        unlockDevice()
        // Before the first HTTP client is built, which AppContainer does
        HttpInterceptors.all = listOf(sharedFake)
        // Signed in as sam: the built-in Pixeldrain with a session, the active host (the bundled config) with a password
        container.sessionManager.saveSession(LoginSession(authKey = "screenshots"), "sam")
        container.providerConfigStore.savePasswordCredentials(container.providerConfigStore.activeProviderId.value, "sam", "screenshots")
        // Light, not the device's own (see shot for the dark ones)
        settings.setThemeMode(ThemeMode.LIGHT)
        settings.setup()
        Screenshots.cleanStatusBar()
    }

    fun launch() {
        scenario = ActivityScenario.launch(MainActivity::class.java)
    }

    /** Starts the app on the made-up account, with [setup] set first. */
    fun start(setup: AppSettings.() -> Unit = {}) {
        prepare(setup)
        launch()
    }

    fun startOnFiles(setup: AppSettings.() -> Unit = {}) {
        start(setup)
        tapTab("Files")
        waitText("Sunset over the bay")
    }

    /** Back to how a new install starts, for the next test: the tests share the app (and its settings). */
    fun reset() {
        scenario?.close()
        scenario = null
        with(settings) {
            setGridView(false)
            setCompactRows(false)
            setGestureNavigation(false)
            setStartScreen(null)
            setBiometricLock(false)
            setBlockScreenshots(false)
            setThemeMode(ThemeMode.LIGHT)
            openedFile = null
            openedList = null
        }
        Screenshots.leaveDemoMode()
    }

    // ---- Steps ----

    /** Waits for [selector] (the first start on an emulator takes a while), failing with a picture of the screen. */
    fun find(selector: BySelector, what: String, timeoutMs: Long = 30_000): UiObject2 =
        device.wait(Until.findObject(selector), timeoutMs) ?: run {
            val name = "failed-" + what.replace(Regex("[^A-Za-z0-9]+"), "-")
            Screenshots.capture(name)
            error("$what didn't show (see $name.png)")
        }

    fun waitText(text: String) = find(By.textContains(text), text)

    /** Whether [selector] shows within [timeoutMs]. */
    fun shows(selector: BySelector, timeoutMs: Long = 3_000): Boolean = device.wait(Until.hasObject(selector), timeoutMs)

    /** Taps [selector], found again when the app redrew it between finding and tapping. */
    fun tap(selector: BySelector, what: String) {
        repeat(3) { attempt ->
            try {
                find(selector, what).click()
                return
            } catch (e: StaleObjectException) {
                if (attempt == 2) throw e
            }
        }
    }

    fun tap(text: String) = tap(By.text(text), text)

    /** Taps the tab [name] of the bar at the bottom: the lowest [name] on the screen (a page may have it as its title too). */
    fun tapTab(name: String) {
        find(By.text(name), name)
        repeat(3) { attempt ->
            try {
                device.findObjects(By.text(name)).maxBy { it.visibleBounds.top }.click()
                return
            } catch (e: StaleObjectException) {
                if (attempt == 2) throw e
            }
        }
    }

    /**
     * Waits for the app's lock to be on (its own lock screen can't always be read: UiAutomator reads the window in front,
     * which may be the system's prompt, or a menu of the app's), failing with a picture of the screen.
     */
    fun waitLocked(timeoutMs: Long = 15_000) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (!container.appLock.locked.value) {
            if (System.currentTimeMillis() > deadline) {
                Screenshots.capture("failed-not-locked")
                error("The app didn't lock (see failed-not-locked.png)")
            }
            Thread.sleep(200)
        }
    }

    /**
     * Closes the system's unlock prompt over the app (the screen lock's own screen, a window of another app), as someone
     * without the PIN would, leaving the app's own lock screen. Waits [appearMs] for it to come up.
     */
    fun dismissUnlockPrompt(appearMs: Long = 10_000) {
        val ours = InstrumentationRegistry.getInstrumentation().targetContext.packageName
        val deadline = System.currentTimeMillis() + appearMs
        while (device.currentPackageName == ours && System.currentTimeMillis() < deadline) Thread.sleep(250)
        repeat(3) {
            if (device.currentPackageName == ours) return
            device.pressBack()
            Thread.sleep(800)
        }
    }

    fun tapDesc(description: String) = tap(By.desc(description), description)

    /** Goes back once, and waits for [text] to show again. */
    fun back(text: String) {
        device.pressBack()
        waitText(text)
    }

    /** Scrolls the list on screen until [text] shows. */
    fun scrollTo(text: String) {
        if (device.wait(Until.hasObject(By.text(text)), 3_000)) return
        UiScrollable(UiSelector().scrollable(true)).scrollTextIntoView(text)
    }

    /** Opens the file called [name] in the list on screen: its details, waiting for [shows] in them. */
    fun openFile(name: String, shows: BySelector = By.text("Details")) {
        scrollTo(name)
        tap(name)
        find(shows, "details of $name")
    }

    /** Opens the Filesystem tab, then [path] in it, one folder after another. */
    fun openFolder(vararg path: String) {
        tapTab("Filesystem")
        // The tab opens where it was left: back to the top through the path above the list (Wallpaper.png is only there)
        if (!device.wait(Until.hasObject(By.text("Wallpaper.png")), 3_000)) tap("me")
        waitText("Wallpaper.png")
        path.forEach { tap(it) }
    }

    /** The ⋮ menu at the top of the screen. */
    fun openScreenMenu() {
        tapDesc("More options")
        waitText("Sort by")
    }

    /** The picture in a file's details (above its name [title]), fullscreen. */
    /**
     * Opens the song [name] (in the folder that's open) and plays it, or leaves it playing: the player goes on in the
     * background from a test before.
     */
    fun playSong(name: String) {
        openFile(name, By.desc(java.util.regex.Pattern.compile("Play|Pause")))
        // Play is tapped only while it's there: a player still going from a test before can turn it into Pause by itself
        // between being seen and being tapped, and waiting for Play then failed the test
        repeat(3) {
            if (shows(By.desc("Pause"), 1_000)) return
            device.findObject(By.desc("Play"))?.let { play -> runCatching { play.click() } }
        }
        find(By.desc("Pause"), "playing")
    }

    fun openFullscreenPicture(title: String) {
        val bounds = find(By.text(title), "name").visibleBounds
        device.click(device.displayWidth / 2, bounds.top - 250)
        find(By.desc("Fullscreen image preview"), "fullscreen picture")
    }

    /**
     * Takes [name] twice: light, then dark (light/[name].png and dark/[name].png). The theme changes while the app runs,
     * so what's open stays open; the app is light again after.
     */
    fun shot(name: String, prefix: String = "") {
        Screenshots.capture("${prefix}light/$name")
        settings.setThemeMode(ThemeMode.DARK)
        Thread.sleep(400)
        Screenshots.capture("${prefix}dark/$name")
        settings.setThemeMode(ThemeMode.LIGHT)
        Thread.sleep(400)
    }

    companion object {
        /** The device's PIN while the lock tests run, which they set and remove. */
        const val DEVICE_PIN = "1234"

        // Put in place by FakeHostRunner before the app starts
        internal val sharedFake = FakePixeldrain()
    }
}
