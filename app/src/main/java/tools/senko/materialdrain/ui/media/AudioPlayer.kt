package tools.senko.materialdrain.ui.media

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import coil.compose.AsyncImage
import tools.senko.materialdrain.ui.components.AppMenu
import tools.senko.materialdrain.ui.components.AppMenuItem
import tools.senko.materialdrain.ui.components.MenuEdge
import tools.senko.materialdrain.util.formatDurationMillis

/**
 * An audio player in a card: art, what is playing, a seek bar and the controls. It loads nothing until play is
 * pressed. Works for files on the device and for files on Pixeldrain.
 *
 * @param albumArtSource a ByteArray (embedded art), a Uri or a URL, or null for a placeholder
 * @param durationHintMillis the duration when it is known beforehand, it is shown until the player knows it
 */
@Composable
fun AudioPlayerPreview(
    audioUri: Uri,
    apiKey: String?,
    title: String?,
    artist: String?,
    album: String?,
    albumArtSource: Any?,
    durationHintMillis: Long?,
    modifier: Modifier = Modifier,
    /** The space around the card; the file details leave none above it, so it starts right under the top bar. */
    outerPadding: PaddingValues = PaddingValues(vertical = 8.dp)
) {
    val player = rememberManagedPlayer(audioUri, apiKey, isVideo = false, prepareNow = false)
    val state = rememberPlayerUiState(player)
    val context = LocalContext.current

    var scrubbing by remember { mutableStateOf(false) }
    var scrubPositionMs by remember { mutableLongStateOf(0L) }
    var speedMenuOpen by remember { mutableStateOf(false) }

    val duration = if (state.durationMs > 0) state.durationMs else durationHintMillis ?: 0L
    val shownPosition = if (scrubbing) scrubPositionMs else state.positionMs
    val onSurface = MaterialTheme.colorScheme.onSurface
    val quietContainer = onSurface.copy(alpha = 0.08f)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(outerPadding),
        shape = MediaCardShape
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant, MediaControlShape),
                    contentAlignment = Alignment.Center
                ) {
                    val artModifier = Modifier.fillMaxSize()
                    when (albumArtSource) {
                        is ByteArray -> {
                            val bitmap = remember(albumArtSource) {
                                try { BitmapFactory.decodeByteArray(albumArtSource, 0, albumArtSource.size) } catch (_: Exception) { null }
                            }
                            if (bitmap != null) {
                                Image(bitmap.asImageBitmap(), "Album art", artModifier.clip(MediaControlShape), contentScale = ContentScale.Crop)
                            } else {
                                Icon(Icons.Filled.MusicNote, contentDescription = null, modifier = Modifier.size(32.dp))
                            }
                        }
                        is Uri, is String -> {
                            val artRequest = remember(albumArtSource, apiKey) {
                                imageRequest(context, albumArtSource) { crossfade(true) }
                            }
                            AsyncImage(
                                model = artRequest,
                                contentDescription = "Album art",
                                modifier = artModifier.clip(MediaControlShape),
                                contentScale = ContentScale.Crop,
                                error = rememberVectorPainter(Icons.Filled.MusicNote)
                            )
                        }
                        else -> Icon(Icons.Filled.MusicNote, contentDescription = null, modifier = Modifier.size(32.dp))
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    title?.let { Text(it, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                    artist?.let { Text(it, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    album?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    if (title == null && artist == null && album == null) {
                        Text("Audio", style = MaterialTheme.typography.titleSmall)
                    }
                }
            }

            Spacer(Modifier.height(4.dp))
            MediaSeekBar(
                positionMs = shownPosition,
                durationMs = duration,
                bufferedMs = state.bufferedMs,
                onSeekStarted = { scrubbing = true },
                onSeekPreview = { scrubPositionMs = it },
                onSeekFinished = {
                    // Seeking loads the media if it wasn't loaded yet
                    if (player.playbackState == androidx.media3.common.Player.STATE_IDLE) player.prepare()
                    state.seekTo(it)
                    scrubbing = false
                },
                activeColor = MaterialTheme.colorScheme.primary,
                trackColor = onSurface.copy(alpha = 0.15f),
                bufferedColor = onSurface.copy(alpha = 0.25f)
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${formatDurationMillis(shownPosition)} / ${if (duration > 0) formatDurationMillis(duration) else "--:--"}",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.weight(1f)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    MediaIconButton(Icons.Filled.Replay10, "Back 10 seconds", { state.seekBy(-SEEK_STEP_MS) }, container = quietContainer, contentColor = onSurface)
                    MediaIconButton(
                        icon = if (state.playWhenReady && !state.isEnded) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (state.playWhenReady && !state.isEnded) "Pause" else "Play",
                        onClick = { state.togglePlayPause() },
                        container = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                    MediaIconButton(Icons.Filled.Forward10, "Forward 10 seconds", { state.seekBy(SEEK_STEP_MS) }, container = quietContainer, contentColor = onSurface)
                }
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                    MediaTextButton(speedLabel(state.speed), onClick = { speedMenuOpen = true }, container = quietContainer, contentColor = onSurface)
                    // Lined up with the button at the end of the row; below it, or above when there's no room
                    AppMenu(expanded = speedMenuOpen, onDismiss = { speedMenuOpen = false }, edge = MenuEdge.End, minWidth = 120.dp) {
                        PlaybackSpeeds.forEach { speed ->
                            AppMenuItem(
                                text = speedLabel(speed),
                                active = speed == state.speed,
                                onClick = { state.changeSpeed(speed) }
                            )
                        }
                    }
                }
            }

            if (state.isBuffering) {
                LinearProgressIndicator(modifier = Modifier.padding(top = 8.dp).fillMaxWidth())
            }
            state.error?.let { error ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                    Text(error.userMessage(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                    TextButton(onClick = { state.retry() }) { Text("Try again") }
                }
            }
        }
    }
}
