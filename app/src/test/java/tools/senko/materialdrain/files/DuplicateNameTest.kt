package tools.senko.materialdrain.files

import org.junit.Assert.assertEquals
import org.junit.Test

/** The names a download tries when its own is taken in Downloads (see nextDuplicateName). */
class DuplicateNameTest {
    @Test
    fun addsOneBeforeTheFirstDot() {
        assertEquals("senko (1).jpg", nextDuplicateName("senko.jpg"))
        assertEquals("archive (1).tar.gz", nextDuplicateName("archive.tar.gz"))
        assertEquals("README (1)", nextDuplicateName("README"))
    }

    @Test
    fun countsUpANumberAlreadyThere() {
        assertEquals("senko (3).jpg", nextDuplicateName("senko (2).jpg"))
        assertEquals("archive (10).tar.gz", nextDuplicateName("archive (9).tar.gz"))
        assertEquals("senko(3).jpg", nextDuplicateName("senko(2).jpg"))
    }

    @Test
    fun leavesBracketsElsewhereAlone() {
        assertEquals("a (2) b (1).jpg", nextDuplicateName("a (2) b.jpg"))
        assertEquals("photo (1).v(2).jpg", nextDuplicateName("photo.v(2).jpg"))
        assertEquals("draft (x) (1).txt", nextDuplicateName("draft (x).txt"))
    }

    @Test
    fun aLeadingDotIsntTheFirst() {
        assertEquals(".bashrc (1)", nextDuplicateName(".bashrc"))
        assertEquals(".config (1).json", nextDuplicateName(".config.json"))
    }
}
