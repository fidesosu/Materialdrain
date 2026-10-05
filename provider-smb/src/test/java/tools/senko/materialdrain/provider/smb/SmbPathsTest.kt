package tools.senko.materialdrain.provider.smb

import org.junit.Assert.assertEquals
import org.junit.Test
import tools.senko.materialdrain.provider.api.SmbConfig

class SmbPathsTest {

    private val config = SmbConfig(name = "Test", host = " 10.0.2.2 ", port = 4450, share = "/MDTest/")

    @Test
    fun normalizeDropsEmptyAndDoubledSeparators() {
        assertEquals("a/b", SmbPaths.normalize("/a//b/"))
        assertEquals("a/b", SmbPaths.normalize("a\\b"))
        assertEquals("", SmbPaths.normalize("/"))
    }

    @Test
    fun folderAddressEndsWithSlashAndFileAddressDoesNot() {
        assertEquals("smb://10.0.2.2:4450/MDTest/Folder%20One/", SmbPaths.url(config, "Folder One", directory = true))
        assertEquals("smb://10.0.2.2:4450/MDTest/Folder%20One/notes.txt", SmbPaths.url(config, "Folder One/notes.txt", directory = false))
    }

    @Test
    fun shareTopIsTheShareAddress() {
        assertEquals("smb://10.0.2.2:4450/MDTest/", SmbPaths.url(config, "", directory = true))
    }

    @Test
    fun plusInANameIsNotASpace() {
        assertEquals("smb://10.0.2.2:4450/MDTest/a%2Bb.txt", SmbPaths.url(config, "a+b.txt", directory = false))
    }

    @Test
    fun namesComeBackDecoded() {
        assertEquals("test.zip (2)", SmbPaths.displayName("test.zip%20%282%29"))
        assertEquals("a+b.txt", SmbPaths.displayName("a%2Bb.txt"))
        assertEquals("plain.txt", SmbPaths.displayName("plain.txt"))
    }

    @Test
    fun parentAndNameOfAPath() {
        assertEquals("a/b", SmbPaths.parentOf("a/b/c"))
        assertEquals("", SmbPaths.parentOf("c"))
        assertEquals("c", SmbPaths.nameOf("a/b/c"))
    }
}
