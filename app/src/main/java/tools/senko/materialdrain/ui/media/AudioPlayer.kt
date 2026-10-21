package tools.senko.materialdrain.ui.media

import android.net.Uri
import android.os.Bundle
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import tools.senko.materialdrain.ui.icons.filled.ExpandLess
import tools.senko.materialdrain.ui.icons.filled.Pause
import tools.senko.materialdrain.ui.icons.filled.PlayArrow
import androidx.compose.material3.Card
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
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.style.TextAlign
import tools.senko.materialdrain.settings.ui.accentContainer
import tools.senko.materialdrain.ui.icons.automirrored.filled.ArrowBack
import tools.senko.materialdrain.ui.LocalPlayerSettings
import tools.senko.materialdrain.ui.theme.AppShapes
import tools.senko.materialdrain.util.formatDurationMillis
import tools.senko.materialdrain.ui.preview.PreviewTheme
import tools.senko.materialdrain.ui.preview.ScreenPreviews
import tools.senko.materialdrain.ui.preview.ThemePreviews
import tools.senko.materialdrain.ui.preview.PreviewSamples

/**
 * An audio player in a card: art, what is playing, a seek bar and the controls. It loads nothing until play is
 * pressed. Works for files on the device and for files on Pixeldrain.
 *
 * With the Media setting "Play songs in the background" on, it plays through [PlaybackService] instead of a player of its
 * own, so the song goes on after the details are left. That player has one song at a time: while it has another one,
 * this card shows its own song at the start, and playing it takes the player over.
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
    outerPadding: PaddingValues = PaddingValues(vertical = 8.dp),
    /** The cover is held back (the data saver, see DataSaver): its square loads it when tapped. */
    onLoadArt: (() -> Unit)? = null,
    /** The songs of the list it was opened from, which the background player goes on with after it */
    queue: SongQueue? = null
) {
    SongPlayerHost(audioUri, apiKey, title, artist, album, albumArtSource, queue) { texts, player, isThisSong, start ->
        AudioPlayerCard(
            player, isThisSong, start, texts.title, texts.artist, texts.album, albumArtSource, durationHintMillis, apiKey,
            modifier, outerPadding, onLoadArt
        )
    }
}

/**
 * A song over the whole screen, as the player over the app shows the song playing (see AudioPlayerView): the big cover,
 * what it is, the seek bar and the controls, under a bar with [onBack]. For the fullscreen view of a file's details, so a
 * song swiped to there is a player, not the details' card. It plays the way [AudioPlayerPreview] does (in the background
 * or on its own), and loads nothing until play is pressed.
 */
@Composable
fun AudioPlayerFullscreen(
    audioUri: Uri,
    apiKey: String?,
    title: String?,
    albumArtSource: Any?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    queue: SongQueue? = null
) {
    SongPlayerHost(audioUri, apiKey, title, artist = null, album = null, albumArtSource, queue) { texts, player, isThisSong, start ->
        AudioPlayerFullContent(player, isThisSong, start, texts, albumArtSource, onBack, modifier)
    }
}

/** A song's title, artist and album as it's shown: from its tags once they're read (see SongTags), else as given. */
private data class SongTexts(val title: String?, val artist: String?, val album: String?)

/**
 * Plays the song at [audioUri] for what [content] shows of it: through the background player with the Media setting
 * "Play songs in the background" on, which has one song at a time (while it has another one, [content] isn't
 * isThisSong, and its startThisSong takes the player over, with the songs of [queue] after it), else a player of its own.
 */
@Composable
private fun SongPlayerHost(
    audioUri: Uri,
    apiKey: String?,
    title: String?,
    artist: String?,
    album: String?,
    albumArtSource: Any?,
    queue: SongQueue?,
    content: @Composable (texts: SongTexts, player: Player?, isThisSong: Boolean, startThisSong: ((Long) -> Unit)?) -> Unit
) {
    val settings = LocalPlayerSettings.current
    // The song's own title, artist and album from its tags, once they're read (see SongTags); the file name until then
    val context = LocalContext.current
    val tagsId = audioUri.toString()
    LaunchedEffect(tagsId) { SongTags.request(context, tagsId) }
    val tags = SongTags.of(tagsId)
    val title = tags?.title ?: title
    val artist = artist ?: tags?.artist
    val album = album ?: tags?.album
    val texts = SongTexts(title, artist, album)
    val card: @Composable (player: Player?, isThisSong: Boolean, startThisSong: ((Long) -> Unit)?) -> Unit = { player, isThisSong, start ->
        content(texts, player, isThisSong, start)
    }
    if (settings.backgroundAudio && !LocalInspectionMode.current) {
        val controller = rememberPlaybackController()
        // Which song the background player has: this one, or another one
        var currentId by remember(controller) { mutableStateOf(controller?.currentMediaItem?.mediaId) }
        DisposableEffect(controller) {
            val listener = object : Player.Listener {
                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) { currentId = mediaItem?.mediaId }
            }
            controller?.addListener(listener)
            onDispose { controller?.removeListener(listener) }
        }
        val id = audioUri.toString()
        val placeholderArt = rememberPlaceholderArt()
        card(controller, controller != null && currentId == id) { startAt ->
            controller?.run {
                val item = songItem(id, audioUri, title, artist, album, albumArtSource, placeholderArt)
                // With the songs around it in its list, when it's one of them; on its own otherwise
                val (items, index) = queue?.takeIf { it.songs.getOrNull(it.start)?.uri == audioUri }
                    ?.toMediaItems(item, placeholderArt) ?: (listOf(item) to 0)
                setMediaItems(items, index, 0L)
                setPlaybackSpeed(settings.defaultSpeed)
                prepare()
                if (startAt > 0) seekTo(startAt)
                play()
            }
        }
    } else {
        val player = rememberManagedPlayer(audioUri, apiKey, isVideo = false, prepareNow = false, speed = settings.defaultSpeed)
        card(player, true, null)
    }
}

/**
 * The background player's item of a song: its address as its id too, and what the notification shows of it. Its picture
 * is [art], or [placeholderArt] (see rememberPlaceholderArt) without one, or when it can't be loaded (see PlaybackService).
 */
internal fun songItem(id: String, uri: Uri, title: String?, artist: String?, album: String?, art: Any?, placeholderArt: ByteArray): MediaItem {
    val metadata = MediaMetadata.Builder()
        .setTitle(title ?: uri.lastPathSegment)
        .setArtist(artist)
        .setAlbumTitle(album)
        .apply {
            when (art) {
                is ByteArray -> setArtworkData(art, MediaMetadata.PICTURE_TYPE_FRONT_COVER)
                is Uri -> setArtworkUri(art)
                is String -> setArtworkUri(Uri.parse(art))
                else -> setArtworkData(placeholderArt, MediaMetadata.PICTURE_TYPE_FRONT_COVER)
            }
        }
        .setExtras(Bundle().apply { putByteArray(EXTRA_FALLBACK_ART, placeholderArt) })
        .build()
    return MediaItem.Builder().setMediaId(id).setUri(uri).setMediaMetadata(metadata).build()
}

/**
 * The card of [AudioPlayerPreview], showing [player] (null while the background player is connecting). When it isn't
 * [isThisSong] (the background player has another one), it shows the song at the start, and play, a skip or a seek start
 * it with [startThisSong] (from where it's asked to).
 */
@Composable
private fun AudioPlayerCard(
    player: Player?,
    isThisSong: Boolean,
    startThisSong: ((Long) -> Unit)?,
    title: String?,
    artist: String?,
    album: String?,
    albumArtSource: Any?,
    durationHintMillis: Long?,
    apiKey: String?,
    modifier: Modifier,
    outerPadding: PaddingValues,
    onLoadArt: (() -> Unit)?
) {
    val state = player?.let { rememberPlayerUiState(it) }
    val stepMs = LocalPlayerSettings.current.seekStepMs
    // What it shows: the player's, when it plays this song
    val own = state?.takeIf { isThisSong }

    var scrubbing by remember { mutableStateOf(false) }
    var scrubPositionMs by remember { mutableLongStateOf(0L) }

    val duration = own?.durationMs?.takeIf { it > 0 } ?: durationHintMillis ?: 0L
    val shownPosition = if (scrubbing) scrubPositionMs else own?.positionMs ?: 0L
    val playing = own != null && own.playWhenReady && !own.isEnded
    // From where it's asked to, this song in the player, or the player as it is
    fun startOr(positionMs: Long, action: () -> Unit) {
        if (own == null) startThisSong?.invoke(positionMs) else action()
    }
    val onSurface = MaterialTheme.colorScheme.onSurface
    val quietContainer = onSurface.copy(alpha = 0.08f)

    // Whether at least half of it is on the screen, for the miniplayer to stay away until it's scrolled off (see
    // LocalSongCardShown); not any more once it's gone
    val onShown = LocalSongCardShown.current
    var shownNow by remember { mutableStateOf<Boolean?>(null) }
    DisposableEffect(onShown) { onDispose { onShown?.invoke(false) } }
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(outerPadding)
            .then(
                if (onShown == null) Modifier
                else Modifier.onGloballyPositioned { coordinates ->
                    // The part of it the scrolling page around it leaves on the screen
                    val visible = coordinates.boundsInWindow().height
                    val shown = coordinates.size.height > 0 && visible >= coordinates.size.height / 2f
                    if (shown != shownNow) {
                        shownNow = shown
                        onShown(shown)
                    }
                }
            ),
        shape = MediaCardShape
    ) {
        // The player over the whole app, where there's one to open: the badge the other previews open theirs with
        val openPlayer = LocalOpenAudioPlayer.current
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                MiniCover(albumArtSource, size = 72.dp, onLoad = onLoadArt, contentDescription = "Album art")
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
                bufferedMs = own?.bufferedMs ?: 0L,
                onSeekStarted = { scrubbing = true },
                onSeekPreview = { scrubPositionMs = it },
                onSeekFinished = { target ->
                    startOr(target) {
                        // Seeking loads the media if it wasn't loaded yet
                        player?.let { if (it.playbackState == Player.STATE_IDLE) it.prepare() }
                        state?.seekTo(target)
                    }
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
                    MediaSkipButton(
                        forward = false, stepMs = stepMs, onClick = { startOr(0L) { state?.seekBy(-stepMs) } },
                        container = quietContainer, contentColor = onSurface
                    )
                    MediaIconButton(
                        icon = if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (playing) "Pause" else "Play",
                        onClick = { startOr(0L) { state?.togglePlayPause() } },
                        container = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        enabled = state != null
                    )
                    MediaSkipButton(
                        forward = true, stepMs = stepMs, onClick = { startOr(stepMs) { state?.seekBy(stepMs) } },
                        container = quietContainer, contentColor = onSurface
                    )
                }
                // As wide as the time before the buttons, so they're in the very middle; at its end, where there's one,
                // the button which opens the player over the whole app, in the card's own quiet look like the skips
                Box(contentAlignment = Alignment.CenterEnd, modifier = Modifier.weight(1f)) {
                    if (openPlayer != null) {
                        MediaIconButton(
                            icon = Icons.Filled.ExpandLess,
                            contentDescription = "Open the player",
                            // The player shows the song the background player has: this one, started if it isn't
                            onClick = {
                                if (own == null) startThisSong?.invoke(0L)
                                openPlayer()
                            },
                            container = quietContainer,
                            contentColor = onSurface
                        )
                    }
                }
            }

            // Loading. Not while the background player connects: that's a moment, in which the bar would only push what's
            // under the card down and back up
            if (own?.isBuffering == true) {
                LinearProgressIndicator(modifier = Modifier.padding(top = 8.dp).fillMaxWidth())
            }
            own?.error?.let { error ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                    Text(error.userMessage(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                    TextButton(onClick = { own?.retry() }) { Text("Try again") }
                }
            }
        }
    }
}

/**
 * What [AudioPlayerFullscreen] shows, laid out as the player over the app's whole screen (see FullPlayer): [player] as
 * [AudioPlayerCard] has it, and the same way of starting the song when the background player has another one.
 */
@Composable
private fun AudioPlayerFullContent(
    player: Player?,
    isThisSong: Boolean,
    startThisSong: ((Long) -> Unit)?,
    texts: SongTexts,
    albumArtSource: Any?,
    onBack: () -> Unit,
    modifier: Modifier
) {
    val state = player?.let { rememberPlayerUiState(it) }
    val stepMs = LocalPlayerSettings.current.seekStepMs
    val own = state?.takeIf { isThisSong }
    var scrubbing by remember { mutableStateOf(false) }
    var scrubPositionMs by remember { mutableLongStateOf(0L) }
    val duration = own?.durationMs?.takeIf { it > 0 } ?: 0L
    val shownPosition = if (scrubbing) scrubPositionMs else own?.positionMs ?: 0L
    val failed = own?.error != null
    fun startOr(positionMs: Long, action: () -> Unit) {
        if (own == null) startThisSong?.invoke(positionMs) else action()
    }
    val colors = MaterialTheme.colorScheme
    val onSurface = colors.onSurface
    val surface = colors.surface
    // The cover's colour (Tint the player) fading down into the page's, as on the player over the app
    val accent = rememberCoverAccent(albumArtSource, enabled = LocalPlayerSettings.current.tintPlayer)
    val tint by animateColorAsState(accent?.let { accentContainer(it, colors.primaryContainer, colors.primary) } ?: surface, label = "songTint")
    Surface(color = surface, modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(tint, surface)))
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 24.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                IconButton(onClick = onBack, modifier = Modifier.offset(x = (-12).dp)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            }
            // The cover up near the top, as big as fits, square; what room is left over goes under it
            BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                val side = minOf(maxWidth, maxHeight - 20.dp).coerceAtLeast(0.dp)
                MiniCover(
                    albumArtSource,
                    size = side,
                    shape = AppShapes.Card,
                    contentDescription = "Album art",
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            Text(
                texts.title ?: "Audio",
                style = MaterialTheme.typography.headlineSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            val details = listOfNotNull(texts.artist, texts.album).joinToString(" · ")
            if (details.isNotEmpty()) {
                Text(details, style = MaterialTheme.typography.bodyLarge, color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height(16.dp))
            MediaSeekBar(
                positionMs = shownPosition,
                durationMs = duration,
                bufferedMs = own?.bufferedMs ?: 0L,
                onSeekStarted = { scrubbing = true },
                onSeekPreview = { scrubPositionMs = it },
                onSeekFinished = { target ->
                    startOr(target) {
                        player?.let { if (it.playbackState == Player.STATE_IDLE) it.prepare() }
                        state?.seekTo(target)
                    }
                    scrubbing = false
                },
                activeColor = colors.primary,
                trackColor = onSurface.copy(alpha = 0.15f),
                bufferedColor = onSurface.copy(alpha = 0.25f)
            )
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(formatDurationMillis(shownPosition), style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
                Text(if (duration > 0) formatDurationMillis(duration) else "--:--", style = MaterialTheme.typography.labelMedium)
            }
            Spacer(Modifier.height(8.dp))
            val quietContainer = onSurface.copy(alpha = 0.08f)
            Row(
                horizontalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                MediaSkipButton(
                    forward = false, stepMs = stepMs, onClick = { startOr(0L) { state?.seekBy(-stepMs) } },
                    container = quietContainer, contentColor = onSurface
                )
                PlayPauseButton(
                    playing = own != null && own.playWhenReady && !own.isEnded,
                    buffering = own?.isBuffering == true,
                    failed = failed,
                    onClick = { if (failed) own?.retry() else startOr(0L) { state?.togglePlayPause() } },
                    size = 72.dp
                )
                MediaSkipButton(
                    forward = true, stepMs = stepMs, onClick = { startOr(stepMs) { state?.seekBy(stepMs) } },
                    container = quietContainer, contentColor = onSurface
                )
            }
            // Why it can't be played; the play button tries again
            Text(
                own?.error?.userMessage().orEmpty(),
                color = colors.error,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                minLines = 2,
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
            )
        }
    }
}

// ---- Previews ----

/** Paused partway (the preview's player loads nothing, see rememberManagedPlayer); the second one's cover waits for a tap. */
@ThemePreviews
@Composable
private fun AudioPlayerPreviewPreview() {
    PreviewTheme(padding = 16.dp) {
        Column {
            AudioPlayerPreview(
                audioUri = Uri.EMPTY,
                apiKey = null,
                title = "A Song",
                artist = "Some Artist",
                album = "An Album (2026)",
                albumArtSource = null,
                durationHintMillis = 213_000
            )
            AudioPlayerPreview(
                audioUri = Uri.EMPTY,
                apiKey = null,
                title = PreviewSamples.song.name,
                artist = null,
                album = null,
                albumArtSource = null,
                durationHintMillis = null,
                onLoadArt = {}
            )
        }
    }
}

/** A song over the whole screen, as the fullscreen view of a file's details shows one swiped to; not started yet. */
@ScreenPreviews
@Composable
private fun AudioPlayerFullscreenPreview() {
    PreviewTheme {
        AudioPlayerFullscreen(audioUri = Uri.EMPTY, apiKey = null, title = PreviewSamples.song.name, albumArtSource = null, onBack = {})
    }
}
