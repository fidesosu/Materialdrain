package tools.senko.materialdrain.security

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import tools.senko.materialdrain.screenshots.AppDriver
import tools.senko.materialdrain.screenshots.Screenshots

/** An app of its own, not part of the project, installed for this test: connects to the player and logs (tag Probe) what it reads. */
private const val PROBE = "probe.media/.ProbeActivity"

/**
 * S4: the song player is open to the system's media controls, which only need to play, pause and skip. Another app on the
 * device mustn't read the queue through it: each song's id is its address, which for a Pixeldrain file is the file, and
 * for an SMB share carries the key to all of its files (see SmbContentServerTokenTest).
 */
@RunWith(AndroidJUnit4::class)
class MediaSessionExposureTest {

    private val app = AppDriver()

    @After
    fun tearDown() {
        app.settings.setBackgroundAudio(false)
        app.reset()
    }

    @Test
    fun anotherAppCantReadTheQueue() {
        assumeTrue("The probe app isn't installed", Screenshots.shell("pm list packages probe.media").contains("probe.media"))
        app.start { setBackgroundAudio(true) }
        app.openFolder("Music")
        app.playSong("Night drive.mp3")

        Screenshots.shell("logcat -c")
        Screenshots.shell("am start -W -n $PROBE")
        Thread.sleep(4_000)
        val read = Screenshots.shell("logcat -d -s Probe:I").lines().filter { "Probe" in it }
        val addresses = read.filter { "id=http" in it }
        assertFalse("Another app read the queue's addresses: ${read.joinToString(" | ")}", addresses.isNotEmpty())
    }
}
