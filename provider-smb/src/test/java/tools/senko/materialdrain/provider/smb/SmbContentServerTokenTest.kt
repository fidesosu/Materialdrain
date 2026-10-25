package tools.senko.materialdrain.provider.smb

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.net.HttpURLConnection
import java.net.URL

/**
 * The address of one SMB file must not open any other: the app hands it to the players, to a song's queue (which other
 * apps can read, see PlaybackService) and to another app ("Open the video with"). Written as the server should behave,
 * so a failure here means one address is a key to every share.
 */
class SmbContentServerTokenTest {

    private fun tokenOf(url: String) = URL(url).path.split('/')[1]

    private fun status(url: String): Int =
        (URL(url).openConnection() as HttpURLConnection).run {
            requestMethod = "HEAD"
            try { responseCode } finally { disconnect() }
        }

    @Test
    fun twoFilesDontShareOneKey() {
        val first = SmbContentServer.rawUrl("home-nas", "Photos/holiday.jpg")
        val other = SmbContentServer.rawUrl("work-share", "HR/salaries.xlsx")
        assertNotEquals("Both files' addresses carry the same key", tokenOf(first), tokenOf(other))
    }

    @Test
    fun oneFilesKeyDoesntOpenAnotherPath() {
        val given = SmbContentServer.rawUrl("home-nas", "Photos/holiday.jpg")
        val token = tokenOf(given)
        val port = URL(given).port
        val wrongKey = status("http://127.0.0.1:$port/0000/raw/home-nas/Photos/holiday.jpg")
        // Another host and path with the key taken from the first address: no host is registered in this test, so the
        // server answers 404 once the key is accepted, and 403 when it isn't
        val reused = status("http://127.0.0.1:$port/$token/raw/work-share/HR/salaries.xlsx")
        assertNotEquals("A wrong key is turned away, as a check of the test", 404, wrongKey)
        assertEquals("The file's own key is taken", 404, status(given))
        assertNotEquals("The key of one file was accepted for another host and path", 404, reused)
    }
}
