package tools.senko.materialdrain.files

import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import tools.senko.materialdrain.provider.api.ApiResponse
import java.io.ByteArrayOutputStream
import java.io.OutputStream

/**
 * A download that stopped halfway goes on from there: the rest of the file is asked for with a range and appended, and
 * a host which sends the whole file instead has it written again from the start. Either way the file comes out whole.
 */
class ResumableDownloadTest {

    private val file = ByteArray(300_000) { (it * 31 % 251).toByte() }
    private val server = MockWebServer().apply { start() }

    @After
    fun tearDown() = server.shutdown()

    /** The file on the device: what was written before it stopped, then whatever the retry writes. */
    private class Saved(initial: ByteArray) {
        var bytes = initial
        var appended: Boolean? = null
        fun open(append: Boolean): OutputStream {
            appended = append
            return object : ByteArrayOutputStream() {
                override fun close() {
                    bytes = if (append) bytes + toByteArray() else toByteArray()
                }
            }
        }
    }

    private fun serve(honourRange: Boolean) {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val from = request.getHeader("Range")?.removePrefix("bytes=")?.removeSuffix("-")?.toIntOrNull()
                if (!honourRange || from == null) return MockResponse().setBody(Buffer().write(file))
                return MockResponse().setResponseCode(206)
                    .setHeader("Content-Range", "bytes $from-${file.size - 1}/${file.size}")
                    .setBody(Buffer().write(file, from, file.size - from))
            }
        }
    }

    private fun resume(saved: Saved, offset: Long, progress: MutableList<Pair<Long, Long?>> = mutableListOf()) =
        downloadRest(OkHttpClient(), ResumeTexts({ "HTTP $it" }, "another part"), server.url("/file").toString(), mapOf("Authorization" to "Basic x"), offset, saved::open) { read, total ->
            progress += read to total
        }

    @Test
    fun theRestIsAskedForAndAppended() {
        serve(honourRange = true)
        val saved = Saved(file.copyOfRange(0, 120_000))
        val progress = mutableListOf<Pair<Long, Long?>>()
        val result = resume(saved, 120_000, progress)

        val request = server.takeRequest()
        assertEquals("bytes=120000-", request.getHeader("Range"))
        assertEquals("The host's login goes with it", "Basic x", request.getHeader("Authorization"))
        assertEquals(true, saved.appended)
        assertArrayEquals(file, saved.bytes)
        assertEquals(file.size.toLong(), (result as ApiResponse.Success).data)
        // The progress counts the whole file, what was there before included
        assertTrue(progress.first().first > 120_000)
        assertEquals(file.size.toLong(), progress.last().second)
    }

    @Test
    fun aHostWithoutRangesSendsItAllAndItsWrittenAgain() {
        serve(honourRange = false)
        val saved = Saved(file.copyOfRange(0, 120_000))
        resume(saved, 120_000)
        assertEquals(false, saved.appended)
        assertArrayEquals(file, saved.bytes)
    }

    @Test
    fun aPartFromElsewhereIsRefused() {
        server.enqueue(MockResponse().setResponseCode(206).setHeader("Content-Range", "bytes 0-99/300000").setBody("x".repeat(100)))
        val saved = Saved(file.copyOfRange(0, 120_000))
        val result = resume(saved, 120_000)
        assertTrue(result is ApiResponse.Error)
        assertEquals("What was there stays as it was", 120_000, saved.bytes.size)
    }
}
