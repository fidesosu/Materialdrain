package tools.senko.materialdrain.ui.media

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import tools.senko.materialdrain.ui.icons.filled.PlayArrow
import tools.senko.materialdrain.ui.preview.PreviewTheme
import tools.senko.materialdrain.ui.preview.ThemePreviews
import tools.senko.materialdrain.ui.theme.AppShapes

// The pieces the media previews and players share, side by side in one picture each, to see that they look alike where
// they should (see the recordRoborazziDebug task). Only previews: nothing here is used by the app itself.

/** A row of [content] with its [name] over it. */
@Composable
private fun GalleryRow(name: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(name, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) { content() }
    }
}

/**
 * The play buttons: the miniplayer's, the compact player's and the full one's, each paused, playing, loading and failed; the song card's square buttons; and the badge that opens a preview.
 */
@ThemePreviews
@Composable
private fun ButtonsGalleryPreview() {
    PreviewTheme(padding = 16.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            GalleryRow("Miniplayer") {
                MiniPlayButton(playing = false, buffering = false, failed = false, onClick = {})
                MiniPlayButton(playing = true, buffering = false, failed = false, onClick = {})
                MiniPlayButton(playing = true, buffering = true, failed = false, onClick = {})
                MiniPlayButton(playing = false, buffering = false, failed = true, onClick = {})
            }
            listOf("Compact player, 56" to 56.dp, "Full player, 72" to 72.dp).forEach { (name, size) ->
                GalleryRow(name) {
                    PlayPauseButton(playing = false, buffering = false, failed = false, onClick = {}, size = size)
                    PlayPauseButton(playing = true, buffering = false, failed = false, onClick = {}, size = size)
                    PlayPauseButton(playing = true, buffering = true, failed = false, onClick = {}, size = size)
                    PlayPauseButton(playing = false, buffering = false, failed = true, onClick = {}, size = size)
                }
            }
            GalleryRow("Song card") {
                val onSurface = MaterialTheme.colorScheme.onSurface
                MediaSkipButton(forward = false, stepMs = 10_000, onClick = {}, container = onSurface.copy(alpha = 0.08f), contentColor = onSurface)
                MediaIconButton(
                    Icons.Filled.PlayArrow, "Play", onClick = {},
                    container = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary
                )
                MediaSkipButton(forward = true, stepMs = 15_000, onClick = {}, container = onSurface.copy(alpha = 0.08f), contentColor = onSurface)
            }
            GalleryRow("Over a picture, and the badge") {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.background(Color.DarkGray, AppShapes.Medium).padding(12.dp)
                ) {
                    MediaIconButton(Icons.Filled.PlayArrow, "Play", onClick = {}, container = MediaScrim.Button)
                    MediaTextButton("1×", onClick = {})
                    FullscreenBadge(contentDescription = null)
                }
            }
        }
    }
}

/** A song's cover (here the note, as a preview loads no pictures) at each size it's shown at, and held by the data saver. */
@ThemePreviews
@Composable
private fun CoversGalleryPreview() {
    PreviewTheme(padding = 16.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            GalleryRow("Queue 40 · Miniplayer 48 · Song card 72 · Compact 96") {
                MiniCover(null, size = 40.dp)
                MiniCover(null, size = 48.dp)
                MiniCover(null, size = 72.dp)
                MiniCover(null, size = 96.dp, shape = AppShapes.Medium)
            }
            GalleryRow("Held by the data saver") {
                MiniCover(null, size = 72.dp, onLoad = {})
            }
        }
    }
}
