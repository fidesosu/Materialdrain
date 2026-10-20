package tools.senko.materialdrain.ui.media

import android.net.Uri
import android.os.Bundle
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.material.icons.Icons
import tools.senko.materialdrain.ui.icons.filled.MusicNote
import tools.senko.materialdrain.ui.icons.filled.Pause
import tools.senko.materialdrain.ui.icons.filled.PlayArrow
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
import androidx.compose.foundation.clickable
import tools.senko.materialdrain.ui.icons.outlined.Image
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalInspectionMode
import tools.senko.materialdrain.ui.LocalPlayerSettings
import coil.compose.AsyncImage
import tools.senko.materialdrain.ui.components.AppMenu
import tools.senko.materialdrain.ui.components.AppMenuItem
import tools.senko.materialdrain.ui.components.MenuEdge
import tools.senko.materialdrain.util.formatDurationMillis
import tools.senko.materialdrain.ui.preview.PreviewTheme
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
    val settings = LocalPlayerSettings.current
    // The song's own title, artist and album from its tags, once they're read (see SongTags); the file name until then
    val context = LocalContext.current
    val tagsId = audioUri.toString()
    LaunchedEffect(tagsId) { SongTags.request(context, tagsId) }
    val tags = SongTags.of(tagsId)
    val title = tags?.title ?: title
    val artist = artist ?: tags?.artist
    val album = album ?: tags?.album
    val card: @Composable (player: Player?, isThisSong: Boolean, startThisSong: ((Long) -> Unit)?) -> Unit = { player, isThisSong, start ->
        AudioPlayerCard(
            player, isThisSong, start, title, artist, album, albumArtSource, durationHintMillis, apiKey, modifier, outerPadding, onLoadArt
        )
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
    val context = LocalContext.current
    val stepMs = LocalPlayerSettings.current.seekStepMs
    // What it shows: the player's, when it plays this song
    val own = state?.takeIf { isThisSong }

    var scrubbing by remember { mutableStateOf(false) }
    var scrubPositionMs by remember { mutableLongStateOf(0L) }
    var speedMenuOpen by remember { mutableStateOf(false) }

    val duration = own?.durationMs?.takeIf { it > 0 } ?: durationHintMillis ?: 0L
    val shownPosition = if (scrubbing) scrubPositionMs else own?.positionMs ?: 0L
    val playing = own != null && own.playWhenReady && !own.isEnded
    // From where it's asked to, this song in the player, or the player as it is
    fun startOr(positionMs: Long, action: () -> Unit) {
        if (own == null) startThisSong?.invoke(positionMs) else action()
    }
    val onSurface = MaterialTheme.colorScheme.onSurface
    val quietContainer = onSurface.copy(alpha = 0.08f)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(outerPadding),
        shape = MediaCardShape
    ) {
        // The cover and the title open the player over the whole app, where there's one to open: their area has the
        // corner marks which say so, and reaches into the card's padding so the card keeps its size
        val openPlayer = LocalOpenAudioPlayer.current
        // Quiet until the area is pressed, when they take the theme's colour for as long as it's held
        val openInteractions = remember { MutableInteractionSource() }
        val openPressed by openInteractions.collectIsPressedAsState()
        val markColor by animateColorAsState(
            targetValue = if (openPressed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
            animationSpec = tween(150),
            label = "openMarks"
        )
        Column(modifier = Modifier.padding(6.dp)) {
            // As wide as the card, so the marks frame the cover and the title from edge to edge
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (openPlayer == null) Modifier
                        else Modifier
                            .clip(OpenAreaShape)
                            .clickable(
                                interactionSource = openInteractions,
                                indication = LocalIndication.current,
                                onClickLabel = "Open the player",
                                // The player shows the song the background player has: this one, started if it isn't
                                onClick = {
                                    if (own == null) startThisSong?.invoke(0L)
                                    openPlayer()
                                }
                            )
                            .cornerMarks { markColor }
                    )
                    .padding(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(MediaControlShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .then(if (onLoadArt != null) Modifier.clickable(onClickLabel = "Load the cover", onClick = onLoadArt) else Modifier),
                    contentAlignment = Alignment.Center
                ) {
                    val artModifier = Modifier.fillMaxSize()
                    if (onLoadArt != null) {
                        Icon(Icons.Outlined.Image, contentDescription = "Load the cover", modifier = Modifier.size(28.dp))
                    } else when (albumArtSource) {
                        is ByteArray -> {
                            val bitmap = rememberDecodedBitmap(albumArtSource)
                            if (bitmap != null) {
                                Image(bitmap, "Album art", artModifier.clip(MediaControlShape), contentScale = ContentScale.Crop)
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

            Column(modifier = Modifier.padding(start = 6.dp, end = 6.dp, bottom = 6.dp)) {
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
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                    MediaTextButton(speedLabel(own?.speed ?: LocalPlayerSettings.current.defaultSpeed), onClick = { speedMenuOpen = true }, container = quietContainer, contentColor = onSurface)
                    // Lined up with the button at the end of the row; below it, or above when there's no room
                    AppMenu(expanded = speedMenuOpen, onDismiss = { speedMenuOpen = false }, edge = MenuEdge.End, minWidth = 120.dp) {
                        PlaybackSpeeds.forEach { speed ->
                            AppMenuItem(
                                text = speedLabel(speed),
                                active = speed == (own?.speed ?: LocalPlayerSettings.current.defaultSpeed),
                                onClick = { state?.changeSpeed(speed) }
                            )
                        }
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
}

/** The corners of the song card's area which opens the player, those of the cover and the buttons; [cornerMarks] follows them. */
private val OpenAreaCorner = 8.dp
private val OpenAreaShape = RoundedCornerShape(OpenAreaCorner)

/**
 * Two opposite corners of the outline of what this modifies ([OpenAreaShape], the card's width), the top left and the
 * bottom right: its rounded corners, with the edges from them going on a part of its width ([MARK_WIDTH_FRACTION]) and
 * of its height ([MARK_HEIGHT_FRACTION]), so they grow with the area. The mark of an area that opens something bigger. [color] is
 * read as they're drawn, so a change of it only draws them again.
 */
private fun Modifier.cornerMarks(color: () -> Color): Modifier = drawBehind {
    val stroke = 2.dp.toPx()
    val radius = OpenAreaCorner.toPx()
    val inset = stroke / 2f
    val across = size.width * MARK_WIDTH_FRACTION
    val down = size.height * MARK_HEIGHT_FRACTION
    val left = inset
    val right = size.width - inset
    val top = inset
    val bottom = size.height - inset
    val arc = Size(radius * 2f, radius * 2f)
    val marks = Path().apply {
        // Top left: up the side, around the corner, along the top
        moveTo(left, top + radius + down)
        lineTo(left, top + radius)
        arcTo(Rect(Offset(left, top), arc), startAngleDegrees = 180f, sweepAngleDegrees = 90f, forceMoveTo = false)
        lineTo(left + radius + across, top)
        // Bottom right: down the side, around the corner, along the bottom
        moveTo(right, bottom - radius - down)
        lineTo(right, bottom - radius)
        arcTo(Rect(Offset(right - arc.width, bottom - arc.height), arc), startAngleDegrees = 0f, sweepAngleDegrees = 90f, forceMoveTo = false)
        lineTo(right - radius - across, bottom)
    }
    drawPath(marks, color(), style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

/** How far the corner marks go along the top and bottom, of the area's width, and along its sides, of its height. */
private const val MARK_WIDTH_FRACTION = 0.12f
private const val MARK_HEIGHT_FRACTION = 0.2f

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
