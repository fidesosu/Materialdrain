package tools.senko.materialdrain.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class ParseDateTimeTest {

    private val moment = Instant.parse("2024-01-02T10:20:30Z")

    @Test
    fun readsIsoWithZone() {
        assertEquals(moment, parseDateTime("2024-01-02T10:20:30Z"))
        assertEquals(moment, parseDateTime("2024-01-02T12:20:30+02:00"))
        assertEquals(moment.plusMillis(500), parseDateTime("2024-01-02T10:20:30.500Z"))
    }

    @Test
    fun readsIsoWithoutZoneAsUtc() {
        assertEquals(moment, parseDateTime("2024-01-02T10:20:30"))
    }

    @Test
    fun readsRfc1123() {
        assertEquals(moment, parseDateTime("Tue, 2 Jan 2024 10:20:30 GMT"))
    }

    @Test
    fun givesNullForAnythingElse() {
        assertNull(parseDateTime(null))
        assertNull(parseDateTime(""))
        assertNull(parseDateTime("yesterday"))
    }
}
