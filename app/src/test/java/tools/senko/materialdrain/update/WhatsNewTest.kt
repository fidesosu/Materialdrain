package tools.senko.materialdrain.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * "What's new" after an update: the changes after the build the app had, up to the one now installed, newest first. A
 * first start, or the same build opened again, shows nothing.
 */
class WhatsNewTest {

    private val installed = InstalledBuild("1.6.5", versionCode = 2000, commitTime = 9000, updatable = true, isDev = false)
    private val changes = listOf(
        UpdateChange(7000, "before the build the app had"),
        UpdateChange(8000, "the build the app had"),
        UpdateChange(8500, "between"),
        UpdateChange(9000, "this build"),
        UpdateChange(9500, "after this build (a newer list than the app's own)")
    )

    @Test
    fun theChangesSinceTheBuildBeforeNewestFirst() {
        assertEquals(listOf("this build", "between"), whatsNewSince(8000, installed, changes).map { it.text })
    }

    @Test
    fun nothingOnAFirstStart() {
        assertTrue(whatsNewSince(0, installed, changes).isEmpty())
    }

    @Test
    fun nothingForTheSameOrAnOlderBuild() {
        assertTrue(whatsNewSince(9000, installed, changes).isEmpty())
        assertTrue(whatsNewSince(9500, installed, changes).isEmpty())
    }
}
