package tools.senko.materialdrain.ui.media

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.compose.runtime.mutableStateMapOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * The tags of songs (their title, artist and album, and what kind of file it is), read out of the files themselves the way their covers are (see
 * MediaCovers: only the parts of the file that are needed), and kept on disk. The song players show a song's title
 * from here, and its file name only when it has none or until it's read.
 *
 * Read when asked for ([request]); [of] is a snapshot read, so what shows a song's tags is drawn again once they're in.
 */
object SongTags {
    private const val TAG = "SongTags"

    /**
     * What a song's file says about it; a field it leaves out is null. Besides its tags, its kind: the file's type
     * ([mimeType]), its bitrate (bits per second), sample rate (Hz) and bit depth (the last two only from Android 12).
     */
    data class Tags(
        val title: String?,
        val artist: String?,
        val album: String?,
        val mimeType: String? = null,
        val bitrate: Int? = null,
        val sampleRate: Int? = null,
        val bitsPerSample: Int? = null
    ) {
        /**
         * The kind of file in a line, as music players show it: "FLAC • 24-bit • 1751 kb/s • 48.0 kHz", with what's
         * known of it; the bit depth only of a lossless file, as a lossy one has none of its own. Null when nothing is.
         */
        val formatLine: String?
            get() {
                val type = mimeType?.let(::typeName)
                val parts = listOfNotNull(
                    type,
                    bitsPerSample?.takeIf { it > 0 && type != null && type in LOSSLESS }?.let { "$it-bit" },
                    bitrate?.takeIf { it > 0 }?.let { "${(it + 500) / 1000} kb/s" },
                    sampleRate?.takeIf { it > 0 }?.let { String.format(Locale.ROOT, "%.1f kHz", it / 1000f) }
                )
                return parts.takeIf { it.isNotEmpty() }?.joinToString(" • ")
            }
    }

    private val NONE = Tags(null, null, null)

    /** How many lines a song's tags are on disk (see [save]). */
    private const val CACHE_LINES = 7

    /** The types (see [typeName]) whose bit depth means something. */
    private val LOSSLESS = setOf("FLAC", "WAV", "ALAC", "AIFF")

    /** What music players call a file of [mimeType]: "FLAC", "MP3", or else its subtype in capitals. */
    private fun typeName(mimeType: String): String? {
        val subtype = mimeType.substringAfter('/', "").lowercase().removePrefix("x-").takeIf { it.isNotEmpty() } ?: return null
        return when (subtype) {
            "flac" -> "FLAC"
            "mpeg", "mp3", "mpeg3" -> "MP3"
            "wav", "wave", "vnd.wave" -> "WAV"
            "mp4", "m4a", "mp4a-latm" -> "M4A"
            "ogg", "vorbis" -> "OGG"
            "aiff", "aif" -> "AIFF"
            "matroska", "mka" -> "MKA"
            else -> subtype.uppercase()
        }
    }

    /** The tags read so far, by the song's address. */
    private val known = mutableStateMapOf<String, Tags>()
    private val loading = ConcurrentHashMap.newKeySet<String>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Reading tags takes a few requests to the host: only a couple at once. */
    private val permits = Semaphore(2)

    /** The tags of the song at [id] (its address), when they've been read; null until then. */
    fun of(id: String): Tags? = known[id]?.takeUnless { it == NONE }

    /** The song's title from its tags, else [fallback] (its file name, as the player's item has it). */
    fun titleOf(id: String?, fallback: CharSequence?): String? = id?.let { of(it)?.title } ?: fallback?.toString()

    /** Reads the tags of the song at [id] (its address), unless they're read or being read. */
    fun request(context: Context, id: String) {
        if (id.isBlank() || known.containsKey(id) || !loading.add(id)) return
        val appContext = context.applicationContext
        scope.launch {
            val tags = cached(appContext, id) ?: permits.withPermit { read(appContext, id) }?.also { save(appContext, id, it) }
            withContext(Dispatchers.Main) {
                // One that couldn't be read (the network, the host being slow) is tried again the next time it's asked for
                if (tags != null) known[id] = tags
                loading.remove(id)
            }
        }
    }

    /** Reads the tags of [id] over the network (or from the device); null when the file couldn't be read. */
    private fun read(context: Context, id: String): Tags? {
        val retriever = MediaMetadataRetriever()
        return try {
            val uri = Uri.parse(id)
            if (uri.scheme == "content" || uri.scheme == "file") retriever.setDataSource(context, uri)
            else retriever.setDataSource(id, HostRequestAuth.headersFor(id))
            fun tag(key: Int) = retriever.extractMetadata(key)?.trim()?.takeIf { it.isNotEmpty() }
            Tags(
                title = tag(MediaMetadataRetriever.METADATA_KEY_TITLE),
                artist = tag(MediaMetadataRetriever.METADATA_KEY_ARTIST) ?: tag(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST),
                album = tag(MediaMetadataRetriever.METADATA_KEY_ALBUM),
                mimeType = tag(MediaMetadataRetriever.METADATA_KEY_MIMETYPE),
                bitrate = tag(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toIntOrNull(),
                sampleRate = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) tag(MediaMetadataRetriever.METADATA_KEY_SAMPLERATE)?.toIntOrNull() else null,
                bitsPerSample = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) tag(MediaMetadataRetriever.METADATA_KEY_BITS_PER_SAMPLE)?.toIntOrNull() else null
            )
        } catch (e: Exception) {
            Log.w(TAG, "no tags read from $id: ${e.message}")
            null
        } finally {
            retriever.release()
        }
    }

    // ---- On disk: seven lines (title, artist, album, type, bitrate, sample rate, bit depth), an empty line for a missing one ----

    private fun file(context: Context, id: String): File {
        val key = MessageDigest.getInstance("SHA-1").digest(id.toByteArray()).joinToString("") { "%02x".format(it) }
        return File(File(context.cacheDir, "song-tags"), "$key.txt")
    }

    private fun cached(context: Context, id: String): Tags? = runCatching {
        val lines = file(context, id).takeIf { it.isFile }?.readLines() ?: return null
        // Kept before the kind of file was read with the tags: read again, for it
        if (lines.size < CACHE_LINES) return null
        fun line(index: Int) = lines.getOrNull(index)?.takeIf { it.isNotEmpty() }
        Tags(line(0), line(1), line(2), line(3), line(4)?.toIntOrNull(), line(5)?.toIntOrNull(), line(6)?.toIntOrNull())
    }.getOrNull()

    private fun save(context: Context, id: String, tags: Tags) {
        runCatching {
            val target = file(context, id)
            target.parentFile?.mkdirs()
            // Line breaks inside a tag would shift the others
            fun clean(value: String?) = value.orEmpty().replace('\n', ' ').replace('\r', ' ')
            target.writeText(
                listOf(
                    clean(tags.title), clean(tags.artist), clean(tags.album), clean(tags.mimeType),
                    tags.bitrate?.toString().orEmpty(), tags.sampleRate?.toString().orEmpty(), tags.bitsPerSample?.toString().orEmpty()
                // Each line ended, the last too: empty lines at the end are still lines when it's read back
                ).joinToString("\n", postfix = "\n")
            )
        }
    }
}
