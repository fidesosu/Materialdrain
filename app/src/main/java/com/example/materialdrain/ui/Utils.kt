package com.example.materialdrain.ui

import java.text.DecimalFormat
import java.util.Locale // Added import for Locale
import java.util.concurrent.TimeUnit

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
