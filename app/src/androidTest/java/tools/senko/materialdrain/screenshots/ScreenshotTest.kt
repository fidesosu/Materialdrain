package tools.senko.materialdrain.screenshots

import android.Manifest
import android.app.Application
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.StaleObjectException
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.UiScrollable
import androidx.test.uiautomator.UiSelector
import androidx.test.uiautomator.Until
import org.junit.After
import org.junit.FixMethodOrder
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import tools.senko.materialdrain.AppContainer
import tools.senko.materialdrain.MainActivity
import tools.senko.materialdrain.Screen
import tools.senko.materialdrain.provider.api.HttpInterceptors
import tools.senko.materialdrain.provider.pixeldrain.internal.LoginSession
import tools.senko.materialdrain.settings.AppSettings
import tools.senko.materialdrain.ui.theme.ThemeMode

/**
 * The pictures of the app for the README and the docs: the real app, signed in to a made-up Pixeldrain account
 * (FakePixeldrain), with a screen, menu or player open in each, light and dark. Run with ./gradlew :app:recordScreenshots,
 * which puts them in app/build/screenshots/light and dark, named as each shot() says.
 *
 * Each test starts the app once and takes the pictures of one part of it, changing settings (grid, compact rows...) while
 * it runs, as they apply at once. Driven with UiAutomator, as a person would through the screen (by the texts and
 * descriptions it shows), so menus and dialogs, which are windows of their own, are reached too. A menu is shown once
 * with all it has: no pictures of the same menu with fewer entries. In name order, the songs last, so their player isn't
 * in the others.
 */
@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class ScreenshotTest {

    @get:Rule
    val permissions: GrantPermissionRule = GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS)

    private val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    private var scenario: ActivityScenario<MainActivity>? = null
    private lateinit var settings: AppSettings

    /** Starts the app on the made-up account, with [setup] set first. */
    private fun start(setup: AppSettings.() -> Unit = {}) {
        // Before the first HTTP client is built, which AppContainer does
        HttpInterceptors.all = listOf(FakePixeldrain())
        val container = AppContainer.get(ApplicationProvider.getApplicationContext<Application>())
        // Signed in as sam: the built-in Pixeldrain with a session, the active host (the bundled config) with a password
        container.sessionManager.saveSession(LoginSession(authKey = "screenshots"), "sam")
        container.providerConfigStore.savePasswordCredentials(container.providerConfigStore.activeProviderId.value, "sam", "screenshots")
        settings = container.appSettings
        // Light, not the device's own (see shot for the dark ones)
        settings.setThemeMode(ThemeMode.LIGHT)
        settings.setup()
        Screenshots.cleanStatusBar()
        scenario = ActivityScenario.launch(MainActivity::class.java)
    }

    /** Back to how a new install starts, for the next test: the tests share the app (and its settings). */
    @After
    fun tearDown() {
        scenario?.close()
        with(settings) {
            setGridView(false)
            setCompactRows(false)
            setGestureNavigation(false)
            setStartScreen(null)
            openedFile = null
            openedList = null
        }
        Screenshots.leaveDemoMode()
    }

    // ---- Steps ----

    /** Waits for [selector] (the first start on an emulator takes a while), failing with a picture of the screen. */
    private fun find(selector: BySelector, what: String, timeoutMs: Long = 30_000): UiObject2 =
        device.wait(Until.findObject(selector), timeoutMs) ?: run {
            val name = "failed-" + what.replace(Regex("[^A-Za-z0-9]+"), "-")
            Screenshots.capture(name)
            error("$what didn't show (see $name.png)")
        }

    private fun waitText(text: String) = find(By.textContains(text), text)

    /** Taps [selector], found again when the app redrew it between finding and tapping. */
    private fun tap(selector: BySelector, what: String) {
        repeat(3) { attempt ->
            try {
                find(selector, what).click()
                return
            } catch (e: StaleObjectException) {
                if (attempt == 2) throw e
            }
        }
    }

    private fun tap(text: String) = tap(By.text(text), text)

    private fun tapDesc(description: String) = tap(By.desc(description), description)

    /** Goes back once, and waits for [text] to show again. */
    private fun back(text: String) {
        device.pressBack()
        waitText(text)
    }

    /** Scrolls the list on screen until [text] shows. */
    private fun scrollTo(text: String) {
        if (device.wait(Until.hasObject(By.text(text)), 3_000)) return
        UiScrollable(UiSelector().scrollable(true)).scrollTextIntoView(text)
    }

    /** Opens the file called [name] in the list on screen: its details, waiting for [shows] in them. */
    private fun openFile(name: String, shows: BySelector = By.text("Details")) {
        scrollTo(name)
        tap(name)
        find(shows, "details of $name")
    }

    /** Opens the Filesystem tab, then [path] in it, one folder after another. */
    private fun openFolder(vararg path: String) {
        tap("Filesystem")
        // The tab opens where it was left: back to the top through the path above the list (Wallpaper.png is only there)
        if (!device.wait(Until.hasObject(By.text("Wallpaper.png")), 3_000)) tap("me")
        waitText("Wallpaper.png")
        path.forEach { tap(it) }
    }

    /** The ⋮ menu at the top of the screen. */
    private fun openScreenMenu() {
        tapDesc("More options")
        waitText("Sort by")
    }

    /**
     * Takes [name] twice: light, then dark (light/[name].png and dark/[name].png). The theme changes while the app runs,
     * so what's open stays open; the app is light again after.
     */
    private fun shot(name: String) {
        Screenshots.capture("light/$name")
        settings.setThemeMode(ThemeMode.DARK)
        Thread.sleep(400)
        Screenshots.capture("dark/$name")
        settings.setThemeMode(ThemeMode.LIGHT)
        Thread.sleep(400)
    }

    private fun startOnFiles(setup: AppSettings.() -> Unit = {}) {
        start(setup)
        tap("Files")
        waitText("Sunset over the bay")
    }

    // ---- Files, its looks and menus ----

    @Test
    fun a_files() {
        startOnFiles()
        shot("files")

        openScreenMenu()
        shot("menu")
        tap("Sort by")
        waitText("Size")
        shot("menu-sort")
        back("Sunset over the bay")

        openScreenMenu()
        tap("View")
        waitText("Compact rows")
        shot("menu-view")
        back("Sunset over the bay")

        settings.setGridView(true)
        Thread.sleep(500)
        shot("files-grid")
        settings.setGridView(false)
        settings.setCompactRows(true)
        Thread.sleep(500)
        shot("files-compact")
        settings.setCompactRows(false)
        Thread.sleep(500)

        tapDesc("More options for City lights.png")
        waitText("Download")
        shot("file-menu")
        tap(By.textStartsWith("Add to"), "Add to")
        waitText("List")
        shot("file-menu-add-to")
        back("Sunset over the bay")

        find(By.text("Sunset over the bay.jpg"), "file").longClick()
        tap("City lights.png")
        tap("Mountain trail.jpg")
        waitText("3 selected")
        shot("selection")
        tapDesc("More actions for the selection")
        Thread.sleep(300)
        shot("selection-menu")
        device.pressBack()
        tapDesc("Stop selecting")

        find(By.descStartsWith("Host:"), "host switcher").click()
        Thread.sleep(500)
        shot("host-switcher")
    }

    // ---- Lists and the filesystem ----

    @Test
    fun b_listsAndFolders() {
        start()
        tap("Lists")
        waitText("Holiday 2026")
        shot("lists")
        tap("Holiday 2026")
        waitText("Weekend vlog")
        shot("list")

        openFolder()
        shot("filesystem")
        openScreenMenu()
        tap("New")
        Thread.sleep(300)
        shot("menu-new")
        back("Wallpaper.png")

        tap("Photos")
        tap("Holiday 2026")
        waitText("Lighthouse")
        shot("filesystem-photos")
    }

    // ---- A file's details and previews ----

    @Test
    fun c_details() {
        startOnFiles()
        openFile("Sunset over the bay.jpg")
        Thread.sleep(500)
        shot("details-image")
        // The picture, above its name
        val title = find(By.text("Sunset over the bay.jpg"), "name").visibleBounds
        device.click(device.displayWidth / 2, title.top - 250)
        find(By.desc("Fullscreen image preview"), "fullscreen picture")
        shot("preview-image")
        device.pressBack()
        back("Sunset over the bay")

        openFile("Weekend vlog.mp4")
        Thread.sleep(500)
        shot("details-video")
        back("Sunset over the bay")

        openFolder("Documents")
        openFile("Trip itinerary.pdf")
        Thread.sleep(800)
        shot("details-pdf")
        back("Notes.txt")
        openFile("Notes.txt", By.textContains("Things to do"))
        shot("details-text")
        back("Readme.md")
        openFile("Readme.md", By.textContains("Holiday photos"))
        shot("details-markdown")

        device.pressBack()
        openFolder("Projects")
        openFile("Project backup.zip")
        Thread.sleep(800)
        shot("details-zip")
    }

    // ---- Settings and the navigation ----

    @Test
    fun d_settings() {
        startOnFiles()
        openScreenMenu()
        tap("Settings")
        waitText("Look and feel")
        shot("settings")
        tap("Look and feel")
        waitText("Navigation")
        shot("settings-look")
        device.pressBack()
        device.pressBack()

        settings.setGestureNavigation(true)
        waitText("Sunset over the bay")
        Thread.sleep(500)
        shot("navigation-swipes")
    }

    // ---- The song players ----

    /** The players over the app (mini, full and compact) are those of songs going on in the background, a setting. */
    @Test
    fun e_songs() {
        start { setBackgroundAudio(true) }
        openFolder("Music")
        openFile("Night drive.mp3", By.desc("Play"))
        tapDesc("Play")
        find(By.desc("Pause"), "playing")
        Thread.sleep(1000)
        shot("details-song")

        tapDesc("Open the player")
        find(By.desc("Next song"), "the player")
        Thread.sleep(500)
        shot("player-full")
        device.pressBack()
        device.pressBack()
        waitText("Ocean waves")
        Thread.sleep(500)
        shot("player-mini")

        settings.setCompactPlayer(true)
        // The miniplayer opens the player when tapped (its artist, which only it shows on its own line)
        tap("Neon Harbour")
        Thread.sleep(800)
        shot("player-compact")
        settings.setCompactPlayer(false)
        settings.setBackgroundAudio(false)
    }
}
