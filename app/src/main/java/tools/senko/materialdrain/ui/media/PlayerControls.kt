package tools.senko.materialdrain.ui.media

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import tools.senko.materialdrain.settings.ui.accentContainer
import tools.senko.materialdrain.ui.LocalPlayerSettings
import tools.senko.materialdrain.ui.components.ScrollingText
import tools.senko.materialdrain.ui.icons.filled.Pause
import tools.senko.materialdrain.ui.icons.filled.PlayArrow
import tools.senko.materialdrain.ui.icons.filled.QueueMusic
import tools.senko.materialdrain.ui.icons.filled.Refresh
import tools.senko.materialdrain.ui.icons.filled.Repeat
import tools.senko.materialdrain.ui.icons.filled.RepeatOne
import tools.senko.materialdrain.ui.icons.filled.Shuffle
import tools.senko.materialdrain.ui.icons.filled.SkipNext
import tools.senko.materialdrain.ui.icons.filled.SkipPrevious
import tools.senko.materialdrain.util.formatDurationMillis

// ---- Shared by both ----

/** The cover's colour as a container colour of the theme, with the Media setting "Tint the player" on; else null. */
@Composable
internal fun rememberPlayerTint(song: BackgroundSong): Color? {
    val colors = MaterialTheme.colorScheme
    val accent = rememberCoverAccent(song.cover, enabled = LocalPlayerSettings.current.tintPlayer)
    val target = accent?.let { accentContainer(it, colors.primaryContainer, colors.primary) }
    val animated by animateColorAsState(target ?: colors.surfaceContainerHigh, label = "playerTint")
    return if (target == null && !LocalPlayerSettings.current.tintPlayer) null else animated
}

/**
 * The song's title and artist (and album, when it's known), bigger on the whole screen ([large]), and in the middle
 * ([centered]) under the seek bar there.
 */
@Composable
internal fun SongTitle(song: ShownSong, large: Boolean, modifier: Modifier = Modifier, centered: Boolean = false) {
    val align = if (centered) TextAlign.Center else TextAlign.Start
    // Wrapping while they fit; one that wouldn't, scrolling on one line instead (see ScrollingText)
    Column(modifier = modifier, horizontalAlignment = if (centered) Alignment.CenterHorizontally else Alignment.Start) {
        ScrollingText(
            song.title ?: "Audio",
            style = (if (large) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleMedium).copy(textAlign = align),
            maxLines = 2
        )
        val details = listOfNotNull(song.artist, song.album).joinToString(" · ")
        if (details.isNotEmpty()) {
            ScrollingText(
                details,
                style = (if (large) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.bodyMedium).copy(textAlign = align),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * The seek bar, with where the song is and how long it is under it; [inline] (the whole screen's), on either side of
 * it instead.
 */
@Composable
internal fun SongSeek(state: PlayerUiState, inline: Boolean = false) {
    var scrubbing by remember { mutableStateOf(false) }
    var scrubPositionMs by remember { mutableLongStateOf(0L) }
    val shownPosition = if (scrubbing) scrubPositionMs else state.positionMs
    val onSurface = MaterialTheme.colorScheme.onSurface
    val bar = @Composable { modifier: Modifier ->
        MediaSeekBar(
            positionMs = shownPosition,
            durationMs = state.durationMs,
            bufferedMs = state.bufferedMs,
            onSeekStarted = { scrubbing = true },
            onSeekPreview = { scrubPositionMs = it },
            onSeekFinished = { target ->
                state.seekTo(target)
                scrubbing = false
            },
            modifier = modifier,
            activeColor = MaterialTheme.colorScheme.primary,
            trackColor = onSurface.copy(alpha = 0.15f),
            bufferedColor = onSurface.copy(alpha = 0.25f)
        )
    }
    val position = @Composable { Text(formatDurationMillis(shownPosition), style = MaterialTheme.typography.labelMedium) }
    val duration = @Composable {
        Text(if (state.durationMs > 0) formatDurationMillis(state.durationMs) else "--:--", style = MaterialTheme.typography.labelMedium)
    }
    if (inline) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            position()
            bar(Modifier.weight(1f).padding(horizontal = 12.dp))
            duration()
        }
        return
    }
    bar(Modifier)
    Row(modifier = Modifier.fillMaxWidth()) {
        Box(Modifier.weight(1f)) { position() }
        duration()
    }
}

/**
 * The song before, play or pause (or try again, or the loading circle), and the song after. "Before" starts the song
 * over first when it's past its start, as players do. [playModifier] goes to the play button, [skipModifier] to the
 * other two. [withModes] (the whole screen's), repeat at the start and shuffle at the end too, all spread across.
 */
@Composable
internal fun SongControls(
    song: BackgroundSong,
    state: PlayerUiState,
    playSize: Dp,
    modifier: Modifier = Modifier,
    playModifier: Modifier = Modifier,
    skipModifier: Modifier = Modifier,
    withModes: Boolean = false
) {
    val player = song.player
    val failed = state.error != null
    Row(
        modifier = modifier,
        horizontalArrangement = if (withModes) Arrangement.SpaceBetween else Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (withModes) RepeatButton(song)
        IconButton(onClick = { player.seekToPrevious() }, modifier = skipModifier) {
            Icon(Icons.Filled.SkipPrevious, contentDescription = "Previous song")
        }
        PlayPauseButton(
            playing = state.playWhenReady && !state.isEnded,
            buffering = state.isBuffering,
            failed = failed,
            onClick = { if (failed) state.retry() else state.togglePlayPause() },
            size = playSize,
            modifier = playModifier
        )
        IconButton(onClick = { song.skip(toNext = true) }, enabled = song.hasNext, modifier = skipModifier) {
            Icon(Icons.Filled.SkipNext, contentDescription = "Next song")
        }
        if (withModes) ShuffleButton(song)
    }
}

/** Repeat: off (greyed), the whole queue, or the one song (its own icon), lit while on; a tap goes to the next. */
@Composable
private fun RepeatButton(song: BackgroundSong) {
    val mode = song.repeatMode
    val colors = MaterialTheme.colorScheme
    IconButton(onClick = song::cycleRepeat) {
        Icon(
            if (mode == Player.REPEAT_MODE_ONE) Icons.Filled.RepeatOne else Icons.Filled.Repeat,
            contentDescription = when (mode) {
                Player.REPEAT_MODE_ALL -> "Repeating the queue"
                Player.REPEAT_MODE_ONE -> "Repeating this song"
                else -> "Repeat"
            },
            tint = if (mode == Player.REPEAT_MODE_OFF) colors.onSurfaceVariant.copy(alpha = 0.6f) else colors.primary
        )
    }
}

/** Shuffle, lit while on. */
@Composable
private fun ShuffleButton(song: BackgroundSong) {
    val colors = MaterialTheme.colorScheme
    IconButton(onClick = song::toggleShuffle) {
        Icon(
            Icons.Filled.Shuffle,
            contentDescription = if (song.shuffle) "Shuffling" else "Shuffle",
            tint = if (song.shuffle) colors.primary else colors.onSurfaceVariant.copy(alpha = 0.6f)
        )
    }
}

/**
 * Play or pause, a circle filled in the theme's colour: try again (in the error colour) when the song [failed], the
 * loading circle while it's [buffering]. The same in both players, only its [size] differs; the miniplayer's is only its
 * sign (see MiniPlayButton).
 */
@Composable
internal fun PlayPauseButton(playing: Boolean, buffering: Boolean, failed: Boolean, onClick: () -> Unit, size: Dp, modifier: Modifier = Modifier) {
    FilledIconButton(
        onClick = onClick,
        shape = CircleShape,
        colors = if (failed) IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.error) else IconButtonDefaults.filledIconButtonColors(),
        modifier = modifier.size(size)
    ) {
        val glyph = Modifier.size(size * 0.45f)
        when {
            failed -> Icon(Icons.Filled.Refresh, contentDescription = "Try again", modifier = glyph)
            buffering -> CircularProgressIndicator(
                modifier = glyph,
                strokeWidth = 3.dp,
                color = MaterialTheme.colorScheme.onPrimary
            )
            else -> Icon(if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, contentDescription = if (playing) "Pause" else "Play", modifier = glyph)
        }
    }
}

/** Shows or hides the queue, lit while it's shown; its icon only, saying how many songs are in it to TalkBack. */
@Composable
internal fun QueueButton(open: Boolean, count: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    IconButton(onClick = onClick, modifier = modifier) {
        Icon(
            Icons.Filled.QueueMusic,
            contentDescription = if (open) "Hide the queue" else "Queue, $count songs",
            tint = if (open) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
