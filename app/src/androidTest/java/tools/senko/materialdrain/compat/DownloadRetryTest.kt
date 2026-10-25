package tools.senko.materialdrain.compat

import android.content.ContentUris
import android.provider.MediaStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import tools.senko.materialdrain.screenshots.AppDriver

/**
 * A download whose connection drops halfway: "Try again" goes on from where it stopped (it asks the host for the rest,
 * with a range) and the file in Downloads comes out whole, the same as the host's.
 */
@RunWith(AndroidJUnit4::class)
class DownloadRetryTest {

    private val app = AppDriver()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val name = "Night drive.mp3"

    @After
    fun tearDown() {
        app.fake.breakNextDownloadOf = null
        savedDownloads().forEach { context.contentResolver.delete(it, null, null) }
        app.reset()
    }

    /** The entries called [name] in Downloads which the app made. */
    private fun savedDownloads() =
        context.contentResolver.query(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI, arrayOf(MediaStore.MediaColumns._ID),
            "${MediaStore.MediaColumns.DISPLAY_NAME} LIKE ?", arrayOf("Night drive%.mp3"), null
        )?.use { cursor ->
            buildList { while (cursor.moveToNext()) add(ContentUris.withAppendedId(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cursor.getLong(0))) }
        }.orEmpty()

    @Test
    fun aDroppedDownloadGoesOnFromWhereItStopped() = with(app) {
        savedDownloads().forEach { context.contentResolver.delete(it, null, null) }
        // The first download asks for notifications, which would cover the snackbar
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            tools.senko.materialdrain.screenshots.Screenshots.shell("pm grant ${context.packageName} android.permission.POST_NOTIFICATIONS")
        }
        startOnFiles()
        fake.breakNextDownloadOf = name
        fake.ranges.clear()
        tapDesc("More options for $name")
        tap("Download")
        waitText("Download failed")
        // Before any picture: a picture in the other theme makes the screen again, and the snackbar goes with it
        tap("Try again")
        waitText("Downloaded $name")

        assertTrue("The retry asks for the rest: ${fake.ranges}", fake.ranges.any { it.matches(Regex("""bytes=[1-9]\d*-""")) })
        val saved = savedDownloads()
        assertTrue("One file in Downloads, not ${saved.size}", saved.size == 1)
        val expected = InstrumentationRegistry.getInstrumentation().context.assets.open("fake-pixeldrain/night-drive.mp3").use { it.readBytes() }
        val bytes = context.contentResolver.openInputStream(saved.single())!!.use { it.readBytes() }
        assertArrayEquals("The file in Downloads is the host's, whole", expected, bytes)
    }
}
