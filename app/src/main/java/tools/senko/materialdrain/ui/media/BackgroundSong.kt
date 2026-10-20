package tools.senko.materialdrain.ui.media

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.Timeline

/**
 * What the background player ([PlaybackService]) has, kept up to date from it, for the miniplayer and the song player
 * over the app (see [AudioPlayerView]): the song playing ([metadata], [songId]) and the queue ([queue], of which
 * [index] is the one playing).
 */
@Stable
class BackgroundSong(val player: Player) {
    var metadata by mutableStateOf(player.mediaMetadata)
        private set
    var songId by mutableStateOf(player.currentMediaItem?.mediaId)
        private set
    var queue by mutableStateOf(itemsOf(player))
        private set
    var index by mutableIntStateOf(player.currentMediaItemIndex)
        private set

    /** The song's title from its tags (see SongTags), else its file name. */
    val title: String? get() = SongTags.titleOf(songId, metadata.title)

    /** Its artist and album from its tags, else what the player knows of them. */
    val artist: String? get() = songId?.let { SongTags.of(it)?.artist } ?: metadata.artist?.toString()
    val album: String? get() = songId?.let { SongTags.of(it)?.album } ?: metadata.albumTitle?.toString()

    /** The picture to show: the cover, or the audio tile of a song without one (see songItem). */
    val art: Any? get() = metadata.artworkData ?: metadata.artworkUri ?: metadata.extras?.getByteArray(EXTRA_FALLBACK_ART)

    /** The cover itself, without the audio tile, which has no colour of its own to give (see rememberCoverAccent). */
    val cover: Any? get() {
        val fallback = metadata.extras?.getByteArray(EXTRA_FALLBACK_ART)
        return metadata.artworkData?.takeUnless { fallback != null && it.contentEquals(fallback) } ?: metadata.artworkUri
    }

    internal val listener = object : Player.Listener {
        override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) { metadata = mediaMetadata }
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            // The new song's picture with it, rather than the last one's until its own metadata comes
            metadata = player.mediaMetadata
            songId = mediaItem?.mediaId
            index = player.currentMediaItemIndex
        }
        override fun onTimelineChanged(timeline: Timeline, reason: Int) {
            songId = player.currentMediaItem?.mediaId
            queue = itemsOf(player)
            index = player.currentMediaItemIndex
        }
    }

    private companion object {
        fun itemsOf(player: Player): List<MediaItem> = List(player.mediaItemCount) { player.getMediaItemAt(it) }
    }
}

/** How many songs on each side of the one playing have their tags read ahead, for the peek and a skip (see SongTags). */
private const val TAGS_AHEAD = 2

/**
 * [player]'s song and queue, followed for as long as it's shown; null without a player (connecting, or none). The tags
 * of the songs around the one playing are read ahead, so a skip shows the next one's title at once.
 */
@Composable
fun rememberBackgroundSong(player: Player?): BackgroundSong? {
    val context = LocalContext.current
    val song = remember(player) { player?.let(::BackgroundSong) }
    DisposableEffect(song) {
        song?.let { it.player.addListener(it.listener) }
        onDispose { song?.let { it.player.removeListener(it.listener) } }
    }
    if (song != null) {
        LaunchedEffect(song, song.index, song.queue) {
            val queue = song.queue
            for (i in (song.index - TAGS_AHEAD)..(song.index + TAGS_AHEAD)) {
                queue.getOrNull(i)?.mediaId?.let { SongTags.request(context, it) }
            }
        }
    }
    return song
}
