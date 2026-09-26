package tools.senko.materialdrain.util

import java.text.DecimalFormat
import java.util.Locale // Added import for Locale
import java.util.concurrent.TimeUnit
import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.DateTimeParseException

// Helper function to format size in bytes to a human-readable string
fun formatSize(bytes: Long): String {
    if (bytes < 0) return "0 B"
    if (bytes == 0L) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    var size = bytes.toDouble()
    var unitIndex = 0
    while (size >= 1024 && unitIndex < units.size - 1) {
        size /= 1024
        unitIndex++
    }
    // For DecimalFormat, explicitly using Locale.US is a good practice too if numbers might vary
    return DecimalFormat("#,##0.#", java.text.DecimalFormatSymbols(Locale.US)).format(size) + " " + units[unitIndex]
}

fun formatSpeed(bytesPerSecond: Long): String = formatSize(bytesPerSecond) + "/s"

fun formatEta(seconds: Long): String = when {
    seconds < 1 -> "<1s"
    seconds < 60 -> "${seconds}s"
    seconds < 3600 -> String.format(Locale.US, "%dm %02ds", seconds / 60, seconds % 60)
    else -> String.format(Locale.US, "%dh %02dm", seconds / 3600, (seconds % 3600) / 60)
}

// Helper function to format duration in milliseconds to MM:SS or HH:MM:SS
fun formatDurationMillis(millis: Long): String {
    if (millis < 0) return "00:00"
    val hours = TimeUnit.MILLISECONDS.toHours(millis)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(millis) % TimeUnit.HOURS.toMinutes(1)
    val seconds = TimeUnit.MILLISECONDS.toSeconds(millis) % TimeUnit.MINUTES.toSeconds(1)
    return if (hours > 0) {
        String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }
}
// Helper function to format API date-time strings
internal fun formatApiDateTimeString(dateTimeString: String?): String {
    if (dateTimeString.isNullOrBlank()) {
        return "N/A"
    }
    return try {
        val parsedDateTime = LocalDateTime.parse(dateTimeString, DateTimeFormatter.ISO_DATE_TIME)
        val formatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)
            .withZone(ZoneId.systemDefault())
        parsedDateTime.atZone(ZoneId.of("UTC")).withZoneSameInstant(ZoneId.systemDefault()).format(formatter)
    } catch (e: DateTimeParseException) {
        Log.e("DateTimeFormat", "Error parsing date: $dateTimeString", e)
        dateTimeString
    }
}
