package tools.senko.materialdrain.compat

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import tools.senko.materialdrain.screenshots.AppDriver

/**
 * Opening a folder of the filesystem on a slow connection: the folder shows at once, with rows of the shape of its files
 * until they're there, instead of the folder left staying on screen; one opened before shows what it had right away.
 */
@RunWith(AndroidJUnit4::class)
class FolderNavigationTest {

    private val app = AppDriver()

    @After
    fun tearDown() {
        app.fake.listingDelayMs = 0
        app.reset()
    }

    @Test
    fun aFolderOpensAtOnceAndOneSeenBeforeShowsWhatItHad() {
        app.start()
        app.openFolder()
        app.fake.listingDelayMs = 3_000
        app.tap("Photos")
        // The rows of the folder to come, where the folder left was
        assertTrue("no placeholder rows", app.shows(By.desc("Loading"), 2_000))
        assertFalse("the folder left is still shown", app.device.hasObject(By.text("Wallpaper.png")))
        app.find(By.text("Mountain trail.jpg"), "the folder's files", timeoutMs = 10_000)
        // Back up: the folder above as it was, long before it's listed again
        app.device.pressBack()
        assertTrue("the folder above wasn't shown at once", app.shows(By.text("Wallpaper.png"), 1_500))
    }
}
