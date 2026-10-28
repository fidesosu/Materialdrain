package tools.senko.materialdrain.compat

import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import android.provider.DocumentsContract.Root
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import tools.senko.materialdrain.screenshots.AppDriver
import java.io.FileInputStream

/**
 * A file of a host opened by another app through the system's file picker (HostDocumentsProvider): it comes in parts as
 * it's read, from anywhere in it, instead of being downloaded whole before the app gets anything.
 */
@RunWith(AndroidJUnit4::class)
class DocumentsProviderTest {

    private val app = AppDriver()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val authority = "${context.packageName}.documents"

    @After
    fun tearDown() {
        app.fake.ignoreRanges = false
        app.settings.setDocumentsProvider(false)
        app.reset()
    }

    private fun children(documentId: String): List<Pair<String, String>> =
        context.contentResolver.query(
            DocumentsContract.buildChildDocumentsUri(authority, documentId),
            arrayOf(Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME), null, null, null
        )!!.use { cursor -> buildList { while (cursor.moveToNext()) add(cursor.getString(0) to cursor.getString(1)) } }

    /** The document id of "Night drive.mp3" in the active host's Music folder, as the file picker would find it. */
    private fun nightDrive(): String {
        app.prepare { setDocumentsProvider(true) }
        // The active host's, which has the made-up account's password
        val host = app.container.providerConfigStore.activeProviderId.value
        val rootDocument = context.contentResolver.query(
            DocumentsContract.buildRootsUri(authority), arrayOf(Root.COLUMN_ROOT_ID, Root.COLUMN_DOCUMENT_ID), null, null, null
        )!!.use { cursor -> buildList { while (cursor.moveToNext()) add(cursor.getString(0) to cursor.getString(1)) } }
            .first { it.first == host }.second
        val music = children(rootDocument).first { it.second == "Music" }.first
        return children(music).first { it.second == "Night drive.mp3" }.first
    }

    private val expected: ByteArray
        get() = InstrumentationRegistry.getInstrumentation().context.assets.open("fake-pixeldrain/night-drive.mp3").use { it.readBytes() }

    @Test
    fun aFileIsReadInPartsAsItsRead() {
        val song = nightDrive()
        val expected = expected

        app.fake.ranges.clear()
        val started = System.currentTimeMillis()
        context.contentResolver.openFileDescriptor(DocumentsContract.buildDocumentUri(authority, song), "r")!!.use { descriptor ->
            FileInputStream(descriptor.fileDescriptor).use { input ->
                val first = ByteArray(4096)
                var read = 0
                while (read < first.size) read += input.read(first, read, first.size - read).also { check(it > 0) }
                val firstByteMs = System.currentTimeMillis() - started
                assertArrayEquals(expected.copyOfRange(0, 4096), first)
                assertTrue("The first bytes took ${firstByteMs} ms", firstByteMs < 2_000)

                // Somewhere further in, as a player jumping ahead reads
                val at = expected.size - 10_000L
                input.channel.position(at)
                val later = ByteArray(5_000)
                read = 0
                while (read < later.size) read += input.read(later, read, later.size - read).also { check(it > 0) }
                assertArrayEquals(expected.copyOfRange(at.toInt(), at.toInt() + 5_000), later)
            }
        }
        assertTrue("Fetched in parts, not whole: ${app.fake.ranges}", app.fake.ranges.isNotEmpty() && app.fake.ranges.all { it.matches(Regex("""bytes=\d+-\d+""")) })
    }

    /** A host which sends the whole file for a range: the file comes from the start on as it's downloaded, whole. */
    @Test
    fun aHostWithoutRangesSendsItInOrder() {
        val song = nightDrive()
        app.fake.ignoreRanges = true
        val bytes = context.contentResolver.openInputStream(DocumentsContract.buildDocumentUri(authority, song))!!.use { it.readBytes() }
        assertArrayEquals(expected, bytes)
    }
}
