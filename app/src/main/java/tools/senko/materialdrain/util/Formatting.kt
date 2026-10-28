package tools.senko.materialdrain.util

import java.text.DecimalFormat
import java.util.Locale // Added import for Locale
import java.util.concurrent.TimeUnit
import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import android.content.res.Resources
import tools.senko.materialdrain.R

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

fun formatEta(seconds: Long, res: Resources): String = when {
    seconds < 1 -> res.getString(R.string.common_eta_under_a_second)
    seconds < 60 -> res.getString(R.string.common_eta_seconds, seconds)
    seconds < 3600 -> res.getString(R.string.common_eta_minutes, seconds / 60, seconds % 60)
    else -> res.getString(R.string.common_eta_hours, seconds / 3600, (seconds % 3600) / 60)
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
    val instant = parseDateTime(dateTimeString) ?: run {
        Log.e("DateTimeFormat", "Error parsing date: $dateTimeString")
        return dateTimeString
    }
    return DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM).withZone(ZoneId.systemDefault()).format(instant)
}

/**
 * How long ago an API date-time was ("3 days ago"), for the lists of files. From a week on it is the date itself.
 * Null when the date is missing or can't be read.
 */
internal fun formatRelativeDateTime(dateTimeString: String?, res: Resources, now: Instant = Instant.now()): String? {
    if (dateTimeString.isNullOrBlank()) return null
    val then = parseDateTime(dateTimeString) ?: return null
    val seconds = Duration.between(then, now).seconds
    fun ago(id: Int, amount: Long) = amount.toInt().let { res.getQuantityString(id, it, it) }
    return when {
        seconds < 60 -> res.getString(R.string.common_just_now)
        seconds < 3600 -> ago(R.plurals.common_minutes_ago, seconds / 60)
        seconds < 86_400 -> ago(R.plurals.common_hours_ago, seconds / 3600)
        seconds < 7 * 86_400 -> ago(R.plurals.common_days_ago, seconds / 86_400)
        else -> DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withZone(ZoneId.systemDefault()).format(then)
    }
}
