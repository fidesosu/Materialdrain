package tools.senko.materialdrain.ui.media

import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/** A file read in ranged blocks, and no more of it than the reader may fetch. */
class RangedReaderTest {

    private val file = ByteArray(10_000) { (it * 7 % 251).toByte() }
    private val server = MockWebServer().apply {
        dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val (start, end) = Regex("""bytes=(\d+)-(\d+)""").find(request.getHeader("Range")!!)!!.destructured
                val last = minOf(end.toInt(), file.lastIndex)
                return MockResponse().setResponseCode(206)
                    .setHeader("Content-Range", "bytes $start-$last/${file.size}")
                    .setBody(Buffer().write(file.copyOfRange(start.toInt(), last + 1)))
            }
        }
        start()
    }

    @After
    fun tearDown() = server.shutdown()

    private fun reader(maxBytes: Long = Long.MAX_VALUE) =
        RangedReader(OkHttpClient(), server.url("/file").toString(), emptyMap(), blockSize = 1_000, blocksKept = 4, maxBytes = maxBytes)

    @Test
    fun readsTheBytesAskedFor() {
        val reader = reader()
        val read = ByteArray(2_500)
        assertEquals(2_500, reader.readAt(3_200, read, 0, read.size))
        assertArrayEquals(file.copyOfRange(3_200, 5_700), read)
        // The blocks 3, 4 and 5
        assertEquals(3_000L, reader.fetchedBytes)
    }

    @Test
    fun fetchesNoMoreThanItMay() {
        val reader = reader(maxBytes = 2_000)
        val read = ByteArray(1_000)
        reader.readAt(0, read, 0, read.size)
        reader.readAt(1_000, read, 0, read.size)
        assertThrows(RangedReader.TooMuchToRead::class.java) { reader.readAt(2_000, read, 0, read.size) }
        assertEquals(2, server.requestCount)
        // What it has is still read
        assertEquals(1_000, reader.readAt(0, read, 0, read.size))
    }
}
