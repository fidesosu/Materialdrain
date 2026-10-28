package tools.senko.materialdrain.screenshots

import android.Manifest
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import androidx.test.uiautomator.By
import org.junit.After
import org.junit.FixMethodOrder
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters

/**
 * The pictures of the app for the README and the docs: the real app, signed in to a made-up Pixeldrain account
 * (FakePixeldrain), with a screen, menu or player open in each, light and dark. Run with ./gradlew :app:recordScreenshots,
 * which puts them in app/build/screenshots/light and dark, named as each shot() says.
 *
 * Each test starts the app once and takes the pictures of one part of it, changing settings (grid, compact rows...) while
 * it runs, as they apply at once. Driven through the screen as a person would (see AppDriver). A menu is shown once
 * with all it has: no pictures of the same menu with fewer entries. In name order, the songs last, so their player isn't
 * in the others.
 */
@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class ScreenshotTest {

    @get:Rule
    val permissions: GrantPermissionRule = GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS)

    private val app = AppDriver()

    @After
    fun tearDown() = app.reset()

    // ---- Files, its looks and menus ----

    @Test
    fun a_files(): Unit = with(app) {
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
    fun b_listsAndFolders(): Unit = with(app) {
        start()
        tapTab("Lists")
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
    fun c_details(): Unit = with(app) {
        startOnFiles()
        openFile("Sunset over the bay.jpg")
        Thread.sleep(500)
        shot("details-image")
        // The picture, above its name
        openFullscreenPicture("Sunset over the bay.jpg")
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
    fun d_settings(): Unit = with(app) {
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
    fun e_songs(): Unit = with(app) {
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
