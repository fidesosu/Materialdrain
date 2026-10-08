package tools.senko.materialdrain.util

import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/**
 * A date and time as the hosts write it: ISO 8601 with or without a zone (Pixeldrain, S3, SMB; no zone means UTC), or
 * RFC 1123 ("Mon, 01 Jan 2024 10:00:00 GMT", WebDAV). Null when it's none of those.
 *
 * The format is told by the text's shape (RFC 1123 starts with the day's name, ISO with the year; a zone is a "Z" or an
 * offset after the time), so each date is read once, by the one format it can be. Sorting a big folder by date reads
 * every date there, and trying formats in turn until one doesn't throw made that slow.
 */
fun parseDateTime(text: String?): Instant? {
    if (text.isNullOrBlank()) return null
    val value = text.trim()
    return try {
        when {
            value.first().isLetter() -> ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant()
            hasZone(value) -> OffsetDateTime.parse(value, DateTimeFormatter.ISO_OFFSET_DATE_TIME).toInstant()
            else -> LocalDateTime.parse(value, DateTimeFormatter.ISO_LOCAL_DATE_TIME).toInstant(ZoneOffset.UTC)
        }
    } catch (_: DateTimeParseException) {
        null
    }
}

/** Whether an ISO date-time ends in a zone: "Z", or a "+hh:mm" / "-hh:mm" offset after its time. */
private fun hasZone(value: String): Boolean {
    if (value.endsWith('Z')) return true
    val time = value.substringAfter('T', "")
    return '+' in time || '-' in time
}
