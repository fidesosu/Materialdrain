package tools.senko.materialdrain.ui.media

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
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
import java.util.concurrent.ConcurrentHashMap

/**
 * The tags of songs (their title, artist and album), read out of the files themselves the way their covers are (see
 * MediaCovers: only the parts of the file that are needed), and kept on disk. The song players show a song's title
 * from here, and its file name only when it has none or until it's read.
 *
 * Read when asked for ([request]); [of] is a snapshot read, so what shows a song's tags is drawn again once they're in.
 */
object SongTags {
    private const val TAG = "SongTags"

    /** What a song's file says about it; a field it leaves out is null. */
    data class Tags(val title: String?, val artist: String?, val album: String?)

    private val NONE = Tags(null, null, null)

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
                album = tag(MediaMetadataRetriever.METADATA_KEY_ALBUM)
            )
        } catch (e: Exception) {
            Log.w(TAG, "no tags read from $id: ${e.message}")
            null
        } finally {
            retriever.release()
        }
    }

    // ---- On disk: three lines (title, artist, album), an empty line for a missing one ----

    private fun file(context: Context, id: String): File {
        val key = MessageDigest.getInstance("SHA-1").digest(id.toByteArray()).joinToString("") { "%02x".format(it) }
        return File(File(context.cacheDir, "song-tags"), "$key.txt")
    }

    private fun cached(context: Context, id: String): Tags? = runCatching {
        val lines = file(context, id).takeIf { it.isFile }?.readLines() ?: return null
        fun line(index: Int) = lines.getOrNull(index)?.takeIf { it.isNotEmpty() }
        Tags(line(0), line(1), line(2))
    }.getOrNull()

    private fun save(context: Context, id: String, tags: Tags) {
        runCatching {
            val target = file(context, id)
            target.parentFile?.mkdirs()
            // Line breaks inside a tag would shift the others
            fun clean(value: String?) = value.orEmpty().replace('\n', ' ').replace('\r', ' ')
            target.writeText(listOf(clean(tags.title), clean(tags.artist), clean(tags.album)).joinToString("\n"))
        }
    }
}
