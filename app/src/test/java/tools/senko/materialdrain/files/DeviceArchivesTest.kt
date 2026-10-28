package tools.senko.materialdrain.files

import kotlinx.coroutines.runBlocking
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
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import tools.senko.materialdrain.provider.api.ApiResponse
import tools.senko.materialdrain.provider.api.StorageProvider
import tools.senko.materialdrain.provider.api.StorageRef
import java.io.ByteArrayOutputStream
import java.lang.reflect.Proxy
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** A zip on a host looked into and read from in parts (DeviceZipArchives), over HTTP with ranges. */
@RunWith(RobolectricTestRunner::class)
class DeviceArchivesTest {

    private val text = "Hello from inside the zip\n".repeat(200).toByteArray()
    private val picture = ByteArray(5_000) { (it * 13 % 256).toByte() }
    private val zip = ByteArrayOutputStream().also { out ->
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry("notes/readme.txt"))
            zip.write(text)
            zip.closeEntry()
            // Stored as it is, not deflated
            zip.putNextEntry(ZipEntry("picture.bin").apply {
                method = ZipEntry.STORED
                size = picture.size.toLong()
                crc = java.util.zip.CRC32().apply { update(picture) }.value
            })
            zip.write(picture)
            zip.closeEntry()
        }
    }.toByteArray()

    private val server = MockWebServer().apply {
        dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val range = request.getHeader("Range") ?: return MockResponse().setResponseCode(200).setBody(Buffer().write(zip))
                val (from, to) = Regex("""bytes=(\d*)-(\d*)""").find(range)!!.destructured
                val (start, end) = if (from.isEmpty()) maxOf(0, zip.size - to.toInt()) to zip.lastIndex else from.toInt() to minOf(to.toInt(), zip.lastIndex)
                return MockResponse().setResponseCode(206)
                    .setHeader("Content-Range", "bytes $start-$end/${zip.size}")
                    .setBody(Buffer().write(zip.copyOfRange(start, end + 1)))
            }
        }
        start()
    }

    /** A host whose files are at the test server, as far as the archives ask. */
    private val provider = Proxy.newProxyInstance(StorageProvider::class.java.classLoader, arrayOf(StorageProvider::class.java)) { _, method, _ ->
        when (method.name) {
            "getId" -> "test"
            "rawContentUrl" -> server.url("/backup.zip").toString()
            "requestHeaders" -> mapOf("Authorization" to "Basic x")
            else -> throw UnsupportedOperationException(method.name)
        }
    } as StorageProvider

    private val archives = DeviceZipArchives(provider, RuntimeEnvironment.getApplication().resources)
    private val archive = StorageRef(path = "backup.zip")

    @After
    fun tearDown() = server.shutdown()

    private fun <T> ApiResponse<T>.ok(): T = (this as? ApiResponse.Success)?.data ?: throw AssertionError((this as ApiResponse.Error).error.toString())

    @Test
    fun listsAndReadsTheFilesInside() = runBlocking {
        val top = archives.list(archive, "").ok()
        assertEquals(listOf("notes", "picture.bin"), top.map { it.name })
        val notes = archives.list(archive, "notes").ok()
        assertEquals(listOf("readme.txt"), notes.map { it.name })

        for ((path, expected) in listOf("notes/readme.txt" to text, "picture.bin" to picture)) {
            val out = ByteArrayOutputStream()
            val read = archives.read(archive, path, out) { _, _ -> }.ok()
            assertEquals(expected.size.toLong(), read)
            assertArrayEquals(expected, out.toByteArray())
        }

        // Every request was for a part, with the host's login, of the bytes as they are
        repeat(server.requestCount) {
            val request = server.takeRequest()
            assertTrue(request.getHeader("Range")!!.startsWith("bytes="))
            assertEquals("Basic x", request.getHeader("Authorization"))
            assertEquals("identity", request.getHeader("Accept-Encoding"))
        }
    }
}
