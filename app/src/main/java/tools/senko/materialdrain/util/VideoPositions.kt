package tools.senko.materialdrain.util

import android.content.Context
import androidx.core.content.edit

private const val PREFS_NAME = "video_positions"

/** A position this close to the start isn't worth going back to, and one this close to the end means it was watched. */
private const val EDGE_MS = 5_000L

/** How many videos are remembered; the ones watched longest ago go first. */
private const val MAX_ENTRIES = 200

/**
 * Where each video was left, so it goes on from there when it's opened again (Media settings, "Resume videos"). Kept by
 * the video's address, as "position:when", in preferences of their own (not backed up, see the backup rules: they're
 * only of use on this device).
 */
class VideoPositions(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Where the video at [key] was left, 0 when it wasn't (or was watched to the end). */
    fun positionOf(key: String): Long =
        prefs.getString(key, null)?.substringBefore(':')?.toLongOrNull() ?: 0L

    /** Remembers [positionMs] of the video at [key], or forgets it when it's at the very start or end of [durationMs]. */
    fun save(key: String, positionMs: Long, durationMs: Long) {
        val worthKeeping = positionMs > EDGE_MS && (durationMs <= 0 || positionMs < durationMs - EDGE_MS)
        prefs.edit {
            if (!worthKeeping) {
                remove(key)
                return@edit
            }
            putString(key, "$positionMs:${System.currentTimeMillis()}")
            // The oldest go once there are too many
            val all = prefs.all
            if (all.size >= MAX_ENTRIES) {
                all.entries
                    .sortedBy { (it.value as? String)?.substringAfter(':')?.toLongOrNull() ?: 0L }
                    .take(all.size - MAX_ENTRIES + 1)
                    .forEach { remove(it.key) }
            }
        }
    }
}
