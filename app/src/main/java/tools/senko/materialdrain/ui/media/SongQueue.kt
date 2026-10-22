package tools.senko.materialdrain.ui.media

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ShuffleOrder.DefaultShuffleOrder
import androidx.media3.session.MediaController
import androidx.core.os.bundleOf
import kotlin.random.Random

/**
 * A song for the background player's queue (see [PlaybackService]): its address, which is its id there too, its name
 * and its cover ([art], a thumbnail's URL, or null for none).
 */
data class QueueSong(val uri: Uri, val title: String, val art: String?)

/**
 * The songs of the list a song was opened from, in its order, to go on with after it: [songs], of which [start] is
 * the one played.
 */
data class SongQueue(val songs: List<QueueSong>, val start: Int)

/** How many songs a queue takes from the list at most, on each side of the one played: plenty, without a huge queue. */
private const val QUEUE_REACH = 100

/** The background player's item of [this] (see songItem), with its tags when they've been read (see SongTags). */
internal fun QueueSong.toMediaItem(placeholderArt: ByteArray): MediaItem {
    val tags = SongTags.of(uri.toString())
    return songItem(uri.toString(), uri, tags?.title ?: title, artist = tags?.artist, album = tags?.album, art = art, placeholderArt = placeholderArt)
}

/**
 * The queue around its played song, at most [QUEUE_REACH] songs on each side, as the player's items, with the played
 * one being [current] (the details' own item of it, with what they know of it) and the index it has in them.
 */
internal fun SongQueue.toMediaItems(current: MediaItem, placeholderArt: ByteArray): Pair<List<MediaItem>, Int> {
    val from = (start - QUEUE_REACH).coerceAtLeast(0)
    val to = (start + QUEUE_REACH + 1).coerceAtMost(songs.size)
    val items = songs.subList(from, to).mapIndexed { index, song -> if (from + index == start) current else song.toMediaItem(placeholderArt) }
    return items to start - from
}

/** Puts [song] at the end of the queue; when the player has nothing, it plays it. */
internal fun Player.addToQueue(song: QueueSong, placeholderArt: ByteArray) {
    val item = song.toMediaItem(placeholderArt)
    if (mediaItemCount == 0) {
        setMediaItem(item)
        prepare()
        play()
    } else {
        addMediaItem(item)
    }
}

/** The song after (or, not [next], before) the one playing, or null for none: for the miniplayer's peek. */
internal fun Player.neighbourSong(next: Boolean): MediaItem? {
    val index = if (next) nextMediaItemIndex else previousMediaItemIndex
    return if (index == C.INDEX_UNSET) null else getMediaItemAt(index)
}

/**
 * Moves the song at [from] in the order the queue plays in to [to], which [order] is (the queue's indexes, see
 * BackgroundSong.playOrder): in the queue itself, or, while it's shuffled, in the shuffled order, which the background
 * player is told of (see PlaybackService).
 */
@androidx.annotation.OptIn(UnstableApi::class)
internal fun Player.moveInPlayOrder(order: List<Int>, from: Int, to: Int) {
    if (from == to || from !in order.indices || to !in order.indices) return
    if (!shuffleModeEnabled) {
        moveMediaItem(order[from], order[to])
        return
    }
    val moved = order.toMutableList().apply { add(to, removeAt(from)) }.toIntArray()
    when (this) {
        is MediaController -> sendCustomCommand(SetShuffleOrderCommand, bundleOf(EXTRA_SHUFFLE_ORDER to moved))
        is ExoPlayer -> setShuffleOrder(DefaultShuffleOrder(moved, Random.nextLong()))
    }
}
