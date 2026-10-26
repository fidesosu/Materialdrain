package tools.senko.materialdrain.compat

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import tools.senko.materialdrain.screenshots.AppDriver
import tools.senko.materialdrain.screenshots.Screenshots

/**
 * While a list's files load, it shows rows of their shape (PlaceholderList), as the list is set to look, until the files
 * are there. The made-up host takes its time for it.
 */
@RunWith(AndroidJUnit4::class)
class PlaceholderTest {

    private val app = AppDriver()

    @After
    fun tearDown() {
        app.fake.listDelayMs = 0
        app.reset()
    }

    private fun placeholdersThenFiles(picture: String) {
        app.find(By.desc("Loading"), "the placeholders")
        Screenshots.capture("compat/placeholders-$picture")
        app.waitText("Sunset over the bay")
        assertFalse("The placeholders stay after the files came", app.shows(By.desc("Loading"), 500))
    }

    @Test
    fun rowsWhileTheFilesLoad() {
        app.fake.listDelayMs = 10_000
        app.start()
        app.tapTab("Files")
        placeholdersThenFiles("rows")
    }

    @Test
    fun tilesInTheGrid() {
        app.fake.listDelayMs = 10_000
        app.start { setGridView(true) }
        app.tapTab("Files")
        placeholdersThenFiles("grid")
    }

    @Test
    fun compactRowsInAFolder() {
        app.start { setCompactRows(true) }
        app.fake.listDelayMs = 10_000
        app.tapTab("Filesystem")
        app.find(By.desc("Loading"), "the placeholders")
        Screenshots.capture("compat/placeholders-compact")
    }
}
