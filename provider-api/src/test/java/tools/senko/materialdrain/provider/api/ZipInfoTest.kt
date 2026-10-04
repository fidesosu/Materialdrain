package tools.senko.materialdrain.provider.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The answers of ?zip_info, as the Pixeldrain API gave them for real archives. */
class ZipInfoTest {

    // A test.7z with test/file1.txt and test/folder1/{file2.txt, folder2.json}
    private val sevenZip =
        """{"size":351,"children":{"test":{"size":351,"children":{"file1.txt":{"size":323},"folder1":{"size":28,"children":{"file2.txt":{"size":28},"folder2.json":{"size":0}}}}}}}"""

    @Test
    fun `the top of the archive has its top folder`() {
        val entries = (ZipInfo.entriesIn(sevenZip, "") as ApiResponse.Success).data
        assertEquals(listOf("test"), entries.map { it.name })
        assertTrue(entries.single().isDirectory)
    }

    @Test
    fun `a folder lists its files and folders with their sizes`() {
        val entries = (ZipInfo.entriesIn(sevenZip, "test") as ApiResponse.Success).data.sortedBy { it.name }
        assertEquals(listOf("file1.txt", "folder1"), entries.map { it.name })
        assertFalse(entries[0].isDirectory)
        assertEquals(323L, entries[0].size)
        assertTrue(entries[1].isDirectory)
        assertEquals("test/folder1", entries[1].path)
    }

    @Test
    fun `a nested folder lists its contents with full paths`() {
        val entries = (ZipInfo.entriesIn(sevenZip, "test/folder1") as ApiResponse.Success).data.sortedBy { it.name }
        assertEquals(listOf("file2.txt", "folder2.json"), entries.map { it.name })
        assertEquals("test/folder1/file2.txt", entries[0].path)
        assertEquals(28L, entries[0].size)
    }

    @Test
    fun `an empty folder written without children reads as a file`() {
        // Exactly as the API gave it: folder2.json has no "children", so it's shown as a file of size 0
        val entries = (ZipInfo.entriesIn(sevenZip, "test/folder1") as ApiResponse.Success).data
        val empty = entries.single { it.name == "folder2.json" }
        assertFalse(empty.isDirectory)
        assertEquals(0L, empty.size)
    }

    @Test
    fun `a password protected archive gives its reason`() {
        val answer = """{"success":false,"value":"invalid_zip_file","message":"This file is not a valid zip file"}"""
        val error = ZipInfo.entriesIn(answer, "") as ApiResponse.Error
        assertEquals("invalid_zip_file", error.error.code)
        assertEquals("This file is not a valid zip file", error.error.message)
    }

    @Test
    fun `a folder that is not in the archive is an error`() {
        assertTrue(ZipInfo.entriesIn(sevenZip, "nope") is ApiResponse.Error)
    }

    @Test
    fun `an answer which is not json is an error, not a crash`() {
        assertTrue(ZipInfo.entriesIn("<html>oops</html>", "") is ApiResponse.Error)
    }
}
