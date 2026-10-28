package tools.senko.materialdrain.ui.media

import android.app.Application
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.media3.common.Player
import coil.compose.AsyncImage
import coil.imageLoader
import coil.memory.MemoryCache
import coil.request.SuccessResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.withContext
import tools.senko.materialdrain.AppContainer
import tools.senko.materialdrain.UiMessageAction
import tools.senko.materialdrain.settings.ui.accentContainer
import tools.senko.materialdrain.settings.ui.accentOf
import tools.senko.materialdrain.ui.HapticKind
import tools.senko.materialdrain.ui.HapticPlace
import tools.senko.materialdrain.ui.LocalAppHaptics
import tools.senko.materialdrain.ui.LocalPlayerSettings
import tools.senko.materialdrain.ui.LocalReduceMotion
import tools.senko.materialdrain.ui.components.ScrollingText
import tools.senko.materialdrain.ui.icons.filled.MusicNote
import tools.senko.materialdrain.ui.icons.filled.Pause
import tools.senko.materialdrain.ui.icons.filled.PlayArrow
import tools.senko.materialdrain.ui.icons.filled.Refresh
import tools.senko.materialdrain.ui.icons.outlined.Image
import tools.senko.materialdrain.ui.preview.PreviewTheme
import tools.senko.materialdrain.ui.preview.ThemePreviews
import tools.senko.materialdrain.ui.theme.AppShapes
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sign
import androidx.compose.ui.res.stringResource
import tools.senko.materialdrain.R

/** How far a swipe on the miniplayer has to go sideways to skip, when it isn't quick. */
private val SwipeDistance = 72.dp

/**
 * How far down it has to go to close the song, when it isn't quick: far less, as the miniplayer sits at the bottom of the
 * screen and the finger soon runs out of room under it.
 */
private val DismissDistance = 24.dp

/** How quick a shorter swipe has to be to count, per second, the player's pull up too. */
private val SwipeVelocity = 600.dp

/** How far sideways the miniplayer can be pulled at most, of its width (see [stretched]). */
private const val SIDEWAYS_STRETCH = 0.35f

/**
 * Where something pulled [pulled] by the finger is, held back as if on a spring: nearly with the finger at first, then
 * slower and slower, never past [limit].
 */
private fun stretched(pulled: Float, limit: Float): Float =
    if (limit <= 0f) 0f else sign(pulled) * limit * (1f - exp(-abs(pulled) / limit))

/**
 * With reduced animations, how far the compact player has to have grown out of the miniplayer before it hides it: by
 * then it has faded in over it (see AudioPlayerView). Otherwise the miniplayer turns into it, hidden from the start.
 */
internal const val COMPACT_OPAQUE_AT = 0.3f

/** The height of the miniplayer's row, under which its progress line runs. */
private val MiniPlayerHeight = 64.dp

/** The shadow of the miniplayer's card, which the compact player's starts out as. */
internal val MiniShadow = 4.dp

/** The size of the cover a tint's colour is read from: plenty for its colour, and quick. */
private const val ACCENT_SAMPLE_PX = 128

/**
 * The miniplayer of the song the background player has ([song], see [PlaybackService] and the Media setting "Play songs
 * in the background"), above the navigation bar: shown (while [visible]) from the moment a song is started until it's
 * swiped down, rising out of the bottom like the bar. Not while the page shows that song's own card ([hiddenSongId],
 * the address a file's details play it from), nor while the compact player it grows into is open over it. See
 * [MiniPlayer] for what it does. With [aboveSystemBar], nothing is under it but the system's navigation bar, which it
 * keeps clear of.
 */
@Composable
fun BackgroundMiniPlayer(
    song: BackgroundSong?,
    visible: Boolean,
    aboveSystemBar: Boolean,
    sheet: AudioPlayerSheetState,
    modifier: Modifier = Modifier,
    hiddenSongId: String? = null
) {
    val context = LocalContext.current
    val messages = remember { AppContainer.get(context.applicationContext as Application).uiMessages }
    val reduceMotion = LocalReduceMotion.current
    val settings = LocalPlayerSettings.current
    AnimatedVisibility(
        visible = visible && song != null && song.songId != null && song.songId != hiddenSongId,
        modifier = modifier,
        enter = if (reduceMotion) fadeIn(tween(100)) else fadeIn() + expandVertically(expandFrom = Alignment.Top),
        exit = if (reduceMotion) fadeOut(tween(100)) else fadeOut() + shrinkVertically(shrinkTowards = Alignment.Top)
    ) {
        val current = song ?: return@AnimatedVisibility
        val player = current.player
        val state = rememberPlayerUiState(player)
        val playing = state.playWhenReady && !state.isEnded
        val failed = state.error != null
        // The compact player starts out as this card, in its place (see AudioPlayerView): this one steps aside under it
        val compactPlayer = settings.compactPlayer
        MiniPlayer(
            title = current.title,
            artist = current.artist,
            art = current.art,
            songKey = current.songId,
            progress = { if (state.durationMs > 0) state.positionMs.toFloat() / state.durationMs else 0f },
            sheet = sheet,
            accent = rememberCoverAccent(current.cover, enabled = settings.tintPlayer),
            buffering = state.isBuffering,
            failed = failed,
            // The songs before and after this one in the player's queue
            peek = { toNext ->
                player.neighbourSong(toNext)?.let { item ->
                    val metadata = item.mediaMetadata
                    MiniSong(
                        title = SongTags.titleOf(item.mediaId, metadata.title),
                        artist = SongTags.of(item.mediaId)?.artist ?: metadata.artist?.toString(),
                        art = metadata.artworkData ?: metadata.artworkUri ?: metadata.extras?.getByteArray(EXTRA_FALLBACK_ART)
                    )
                }
            },
            canSkip = { toNext -> if (toNext) player.hasNextMediaItem() else player.hasPreviousMediaItem() },
            onSkip = { toNext -> current.skip(toNext) },
            onDismiss = { closeSong(player, current, messages.takeIf { settings.undoCloseSong }) },
            modifier = (if (aboveSystemBar) Modifier.navigationBarsPadding() else Modifier)
                // Hidden as soon as the compact player it turns into is there in its place; with reduced animations, once
                // that has faded in over it (see COMPACT_OPAQUE_AT). Read as it's drawn
                .graphicsLayer {
                    val shown = sheet.progress.value
                    alpha = if (compactPlayer && (if (reduceMotion) shown > COMPACT_OPAQUE_AT else shown > 0f)) 0f else 1f
                }
        ) {
            MiniPlayButton(
                playing = playing,
                buffering = state.isBuffering,
                failed = failed,
                onClick = { if (failed) state.retry() else state.togglePlayPause() }
            )
        }
    }
}

/**
 * Stops the song and empties the queue. With [messages] (the Media setting "Undo closing a song"), an Undo on the snackbar
 * puts both back as they were: the songs, the one playing, where it was, and whether it played.
 */
internal fun closeSong(player: Player, song: BackgroundSong, messages: tools.senko.materialdrain.UiMessages?) {
    val items = song.queue
    val index = player.currentMediaItemIndex
    val position = player.currentPosition
    val wasPlaying = player.playWhenReady
    player.stop()
    player.clearMediaItems()
    if (messages != null && items.isNotEmpty()) {
        messages.show(R.string.media_song_closed, action = UiMessageAction.Undo {
            player.setMediaItems(items, index.coerceIn(0, items.lastIndex), position)
            player.prepare()
            player.playWhenReady = wasPlaying
        })
    }
}

/**
 * The miniplayer as it's drawn: a floating card with the cover ([art]: a ByteArray, a Uri or a URL, or null), the title
 * and the artist, then [controls] (any buttons, at the end), and how far the song is ([progress], 0 to 1) as a line along
 * its bottom; while it's [buffering] the line runs on its own, and when it [failed] it's red. With an [accent] (the
 * cover's colour, see the Media setting "Tint the player"), the card fades from it into its own colour.
 *
 * It follows the finger: a swipe sideways goes to the song before (to the right) or after (to the left, [onSkip], which
 * says whether there was one; if not, it springs back), a swipe down closes the song ([onDismiss]), and a swipe up pulls
 * the player ([sheet]) up from the bottom of the screen, which opens when it's let go far enough up or flung. A tap
 * opens the player too. A short vibration says when letting go would do it ([canSkip] tells whether a skip would), and
 * another when the finger goes back from there. While it's pulled sideways, the song it would go to ([peek]) comes in
 * beside it, and takes its place when it skips.
 */
@Composable
fun MiniPlayer(
    title: String?,
    artist: String?,
    art: Any?,
    progress: () -> Float,
    sheet: AudioPlayerSheetState,
    modifier: Modifier = Modifier,
    /** Which song it shows, so a skip can wait for the next one to be there before the card takes its place */
    songKey: Any? = title,
    accent: Color? = null,
    buffering: Boolean = false,
    failed: Boolean = false,
    peek: (toNext: Boolean) -> MiniSong? = { null },
    canSkip: (toNext: Boolean) -> Boolean = { false },
    onSkip: (toNext: Boolean) -> Boolean = { false },
    onDismiss: () -> Unit = {},
    controls: @Composable RowScope.() -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    val reduceMotion = LocalReduceMotion.current
    val haptics = LocalAppHaptics.current
    val currentSongKey by rememberUpdatedState(songKey)
    // Where the finger has moved the card: sideways, and down
    val offsetX = remember { Animatable(0f) }
    val offsetY = remember { Animatable(0f) }
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val currentCanSkip by rememberUpdatedState(canSkip)
    val currentOnSkip by rememberUpdatedState(onSkip)
    val currentOnDismiss by rememberUpdatedState(onDismiss)
    val currentPeek by rememberUpdatedState(peek)
    // The way it's pulled sideways (true: towards the next song), which decides which song peeks in beside it
    val pulledToNext by remember(rtl) {
        derivedStateOf { offsetX.value.let { x -> if (x == 0f) null else if (rtl) x > 0f else x < 0f } }
    }
    // While a skip slides it into place, the song it showed when it was let go: the player moves on meanwhile, and the
    // song beside the new one is another (or none, at the end of the queue)
    var heldPeek by remember { mutableStateOf<MiniSong?>(null) }
    val peeking = heldPeek ?: pulledToNext?.let { currentPeek(it) }
    val gap = 8.dp
    val colors = MaterialTheme.colorScheme
    val cardColor = colors.surfaceContainerHigh
    // The tint eases in once the cover's colour is known, and out when it's turned off
    val tint by animateColorAsState(
        targetValue = accent?.let { accentContainer(it, colors.primaryContainer, colors.primary) } ?: cardColor,
        label = "miniPlayerTint"
    )
    DisposableEffect(sheet) {
        onDispose {
            sheet.miniBounds = null
            sheet.miniParts.clear()
        }
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 8.dp, end = 8.dp, bottom = 8.dp)
            // Where it is on the screen, for the compact player to grow out of (see AudioPlayerView)
            .onGloballyPositioned { sheet.miniBounds = it.boundsInRoot() }
    ) {
    // The song it would go to, beside it on the side it's pulled away from
    if (peeking != null) {
        Surface(
            color = cardColor,
            shape = AppShapes.Medium,
            shadowElevation = MiniShadow,
            modifier = Modifier
                .matchParentSize()
                .graphicsLayer {
                    val side = if (offsetX.value < 0f) 1f else -1f
                    translationX = offsetX.value + side * (size.width + gap.toPx())
                    alpha = if (size.width > 0f) (0.5f + abs(offsetX.value) / size.width).coerceAtMost(1f) else 1f
                }
        ) {
            MiniPlayerRow(peeking.art, peeking.title, peeking.artist)
        }
    }
    Surface(
        color = cardColor,
        shape = AppShapes.Medium,
        shadowElevation = MiniShadow,
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                translationX = offsetX.value
                translationY = offsetY.value
                // Fading the further it's pulled away, down behind the navigation bar or off to the side
                if (size.width > 0f && size.height > 0f) {
                    val away = maxOf(abs(offsetX.value) / size.width, offsetY.value / size.height).coerceIn(0f, 1f)
                    alpha = 1f - away * 0.7f
                }
            }
            .pointerInput(sheet, rtl, reduceMotion) {
                val distance = SwipeDistance.toPx()
                val dismissDistance = DismissDistance.toPx()
                val flingVelocity = SwipeVelocity.toPx()
                // A swipe to the left goes on to the next song, as the tabs and the files do
                fun toNext(pulledX: Float) = if (rtl) pulledX > 0 else pulledX < 0
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val velocity = VelocityTracker().apply { addPosition(down.uptimeMillis, down.position) }
                    var total = Offset.Zero
                    // Sideways or up and down, decided once the finger has moved past the touch slop
                    var sideways: Boolean? = null
                    // Whether letting go now (slowly) would do what the swipe is for, which the vibration tells
                    var armed = false
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (change.changedToUpIgnoreConsumed()) break
                        total += change.positionChange()
                        velocity.addPosition(change.uptimeMillis, change.position)
                        if (sideways == null && total.getDistance() > viewConfiguration.touchSlop) sideways = abs(total.x) > abs(total.y)
                        when (sideways) {
                            // Held back the further it's pulled, as if on a spring (see stretched)
                            true -> scope.launch { offsetX.snapTo(stretched(total.x, limit = size.width * SIDEWAYS_STRETCH)) }
                            // Up pulls the player, down the card itself
                            false -> {
                                sheet.dragTo(-total.y)
                                scope.launch { offsetY.snapTo(total.y.coerceAtLeast(0f)) }
                            }
                            null -> {}
                        }
                        val nowArmed = when (sideways) {
                            true -> abs(total.x) >= distance && currentCanSkip(toNext(total.x))
                            false -> if (total.y < 0f) sheet.opensAt(-total.y) else total.y >= dismissDistance
                            null -> false
                        }
                        if (nowArmed != armed) {
                            armed = nowArmed
                            haptics.perform(if (nowArmed) HapticKind.THRESHOLD else HapticKind.RELEASE, HapticPlace.PLAYER)
                        }
                        // Taken: not a tap of the card or of its buttons any more
                        if (sideways != null) change.consume()
                    }
                    val speed = velocity.calculateVelocity()
                    val width = size.width.toFloat()
                    when (sideways) {
                        true -> {
                            // As the pull up does: let go while moving, the way it moves decides (on, or back),
                            // let go still, how far it was pulled
                            val skip = if (abs(speed.x) >= flingVelocity) sign(speed.x) == sign(total.x) else abs(total.x) >= distance
                            val peekSong = currentPeek(toNext(total.x))
                            val hasPeek = peekSong != null
                            val songBefore = currentSongKey
                            if (skip) heldPeek = peekSong
                            if (skip && currentOnSkip(toNext(total.x))) scope.launch {
                                val side = sign(total.x)
                                // The card only takes the next song's place once the player has moved to it, so it
                                // never shows the last song (or half of the next one) for a moment
                                if (hasPeek && !reduceMotion) {
                                    // The song beside it slides into its place, which then shows that song
                                    offsetX.animateTo(side * (width + gap.toPx()), tween(200))
                                    awaitSongChange(songBefore) { currentSongKey }
                                    offsetX.snapTo(0f)
                                    heldPeek = null
                                } else {
                                    // Off the side it went to, and the next song in from the other
                                    if (!reduceMotion) offsetX.animateTo(side * width, tween(150))
                                    awaitSongChange(songBefore) { currentSongKey }
                                    offsetX.snapTo(-side * width * 0.4f)
                                    heldPeek = null
                                    offsetX.animateTo(0f, tween(200))
                                }
                            } else {
                                heldPeek = null
                                scope.launch { offsetX.animateTo(0f, spring()) }
                            }
                        }
                        false -> {
                            if (total.y < 0f) {
                                sheet.settle(speed.y, flingVelocity)
                            } else {
                                sheet.dragTo(0f)
                                // Let go while moving down it goes, while moving back up it stays; let go still, how far
                                val dismiss = when {
                                    speed.y >= flingVelocity -> true
                                    speed.y <= -flingVelocity -> false
                                    else -> total.y >= dismissDistance
                                }
                                scope.launch {
                                    if (dismiss) {
                                        offsetY.animateTo(size.height * 1.5f, tween(150))
                                        currentOnDismiss()
                                    } else {
                                        offsetY.animateTo(0f, spring())
                                    }
                                }
                            }
                        }
                        null -> {}
                    }
                }
            }
    ) {
        Column(
            modifier = Modifier
                .background(Brush.horizontalGradient(listOf(tint, cardColor)))
                .clickable(onClickLabel = stringResource(R.string.media_open_the_player), onClick = sheet::open)
        ) {
            MiniPlayerRow(art, title, artist, controls, sheet = sheet)
            MiniProgressLine(progress, buffering, failed)
        }
    }
    }
}

/**
 * How far the song is ([progress], 0 to 1), as the line along the bottom of the miniplayer: running on its own while it's
 * [buffering], red when it [failed].
 */
@Composable
internal fun MiniProgressLine(progress: () -> Float, buffering: Boolean, failed: Boolean, modifier: Modifier = Modifier) {
    val lineModifier = modifier.fillMaxWidth().height(2.dp)
    val trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
    when {
        failed -> LinearProgressIndicator(
            progress = { 1f },
            drawStopIndicator = {},
            gapSize = 0.dp,
            color = MaterialTheme.colorScheme.error,
            trackColor = trackColor,
            modifier = lineModifier
        )
        buffering -> LinearProgressIndicator(gapSize = 0.dp, trackColor = trackColor, modifier = lineModifier)
        else -> LinearProgressIndicator(
            progress = progress,
            drawStopIndicator = {},
            gapSize = 0.dp,
            trackColor = trackColor,
            modifier = lineModifier
        )
    }
}

/**
 * Waits until [current] (which song the miniplayer shows) is no longer [before], for at most a second: a player which
 * doesn't move to the next song (or is slow to say so) doesn't hold the card up for longer.
 */
private suspend fun awaitSongChange(before: Any?, current: () -> Any?) {
    withTimeoutOrNull(1000) { snapshotFlow { current() }.first { it != before } }
}

/** A song as the miniplayer shows it, e.g. the one that peeks in beside it: its title, artist and cover (see MiniPlayer). */
data class MiniSong(val title: String?, val artist: String?, val art: Any?)

/**
 * The cover, the title and the artist of the miniplayer, then [controls] at the end. With [sheet] (the miniplayer's own
 * row, not the song peeking in beside it), where the cover, the text and the controls are goes to it, for the compact
 * player to grow its own out of them (see AudioPlayerSheetState.miniParts).
 */
@Composable
internal fun MiniPlayerRow(
    art: Any?,
    title: String?,
    artist: String?,
    controls: @Composable RowScope.() -> Unit = {},
    sheet: AudioPlayerSheetState? = null
) {
    fun Modifier.part(part: MorphPart) = if (sheet == null) this else onGloballyPositioned { sheet.miniParts[part] = it }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.height(MiniPlayerHeight).padding(start = 8.dp, end = 4.dp)
    ) {
        MiniCover(art, modifier = Modifier.part(MorphPart.Cover))
        Spacer(Modifier.width(12.dp))
        MiniPlayerText(title, artist, modifier = Modifier.weight(1f).part(MorphPart.Text))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.part(MorphPart.Play), content = controls)
    }
}

/**
 * The miniplayer's play button: only its sign, with no container, the loading circle while it's [buffering], try again
 * (in the error colour) when it [failed].
 */
@Composable
internal fun MiniPlayButton(playing: Boolean, buffering: Boolean, failed: Boolean, onClick: () -> Unit) {
    IconButton(onClick = onClick) { MiniPlayGlyph(playing, buffering, failed) }
}

/** What [MiniPlayButton] shows; the compact player growing out of the miniplayer draws it too, fading out (see AudioPlayerView). */
@Composable
internal fun MiniPlayGlyph(playing: Boolean, buffering: Boolean, failed: Boolean) {
    when {
        failed -> Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.media_try_again), tint = MaterialTheme.colorScheme.error)
        buffering -> CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
        else -> Icon(if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, contentDescription = if (playing) stringResource(R.string.media_pause) else stringResource(R.string.media_play))
    }
}

/** The title and the artist as the miniplayer shows them: one line each, scrolling when it doesn't fit (see ScrollingText). */
@Composable
internal fun MiniPlayerText(title: String?, artist: String?, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        ScrollingText(title ?: stringResource(R.string.media_audio), style = MaterialTheme.typography.titleSmall)
        artist?.let {
            ScrollingText(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * The colour [art] (a ByteArray, a Uri or a URL) is most about (see accentOf), read from a small copy of it while
 * [enabled]; null without art, while it's read, or for a cover without much colour.
 */
@Composable
internal fun rememberCoverAccent(art: Any?, enabled: Boolean): Color? {
    val context = LocalContext.current
    val key = art?.let(::accentKey)
    // Known at once when it was read before (the miniplayer's, for the player that grows out of it)
    var accent by remember(art, enabled) { mutableStateOf(if (enabled && key != null) AccentCache.get(key) else null) }
    LaunchedEffect(art, enabled) {
        if (!enabled || art == null || key == null || AccentCache.has(key)) return@LaunchedEffect
        accent = withContext(Dispatchers.Default) {
            runCatching {
                val bitmap = when (art) {
                    is ByteArray -> BitmapFactory.decodeByteArray(art, 0, art.size)
                    else -> {
                        val request = imageRequest(context, art) {
                            allowHardware(false)
                            size(ACCENT_SAMPLE_PX)
                        }
                        (context.imageLoader.execute(request) as? SuccessResult)?.drawable?.toBitmap()
                    }
                }
                bitmap?.let(::accentOf)
            }.getOrNull()
        }.also { AccentCache.put(key, it) }
    }
    return accent
}

/** What a cover's colour is remembered by: a picture's address, or the bytes of one inside the song. */
private fun accentKey(art: Any): String = if (art is ByteArray) "bytes:${art.size}:${art.contentHashCode()}" else art.toString()

/** The colours of the covers read lately (null for one without much colour), so they're not read again for each card. */
private object AccentCache {
    private const val SIZE = 64
    private val colors = object : LinkedHashMap<String, Color?>(SIZE, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Color?>) = size > SIZE
    }

    @Synchronized fun has(key: String) = colors.containsKey(key)
    @Synchronized fun get(key: String): Color? = colors[key]
    @Synchronized fun put(key: String, color: Color?) { colors[key] = color }
}

/**
 * Up to how big a cover is loaded at this one size, whatever size it's shown at: the miniplayer's, the queue's and the
 * song card's then share one copy in the memory cache.
 */
private val SharedCoverSize = 96.dp

/**
 * From how big a cover is shown as big as it is in its file (see MediaCovers.large) rather than as its thumbnail: the
 * players' (the compact one's and up). The thumbnail stands in until it's there, so the compact player growing out of
 * the miniplayer has its cover at once.
 */
private val LargeCoverSize = 96.dp

/**
 * The song's cover in the miniplayer (and the players, the song card and the queue), or a note where it has none. With
 * [onLoad], the cover is held back (the data saver, see DataSaver): a picture's sign, which loads it when tapped. A cover
 * inside the song's file is decoded away from the main thread, only as big as it's shown (see rememberDecoded).
 */
@Composable
internal fun MiniCover(
    art: Any?,
    size: androidx.compose.ui.unit.Dp = 48.dp,
    shape: androidx.compose.ui.graphics.Shape = MediaControlShape,
    modifier: Modifier = Modifier,
    onLoad: (() -> Unit)? = null,
    contentDescription: String? = null
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val sharedPx = with(density) { SharedCoverSize.roundToPx() }
    val large = size >= LargeCoverSize
    val shared = !large && size <= SharedCoverSize
    // The note's size: bigger on the bigger covers (the song card's and up)
    val glyph = Modifier.size(if (size >= 64.dp) 32.dp else 24.dp)
    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .then(if (onLoad != null) Modifier.clickable(onClickLabel = stringResource(R.string.media_load_the_cover), onClick = onLoad) else Modifier),
        contentAlignment = Alignment.Center
    ) {
        when {
            onLoad != null -> Icon(Icons.Outlined.Image, contentDescription = stringResource(R.string.media_load_the_cover), modifier = glyph)
            art is ByteArray -> when (val decoded = rememberDecoded(art, maxSizePx = with(density) { size.roundToPx() })) {
                is Decoded.Done -> Image(
                    decoded.bitmap,
                    contentDescription = contentDescription,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                // Only the square while it decodes, rather than the note for a moment
                Decoded.Decoding -> Unit
                Decoded.Failed -> Icon(Icons.Filled.MusicNote, contentDescription = null, modifier = glyph)
            }
            art is Uri || art is String -> AsyncImage(
                model = remember(art, shared, large) {
                    imageRequest(context, if (large) MediaCovers.large(art) ?: art else art) {
                        crossfade(true)
                        if (shared) size(sharedPx)
                        // The thumbnail (already in the memory cache, see SharedCoverSize) until the big one is there
                        if (large) placeholderMemoryCacheKey(MemoryCache.Key(art.toString()))
                    }
                },
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                error = rememberVectorPainter(Icons.Filled.MusicNote),
                modifier = Modifier.fillMaxSize()
            )
            else -> Icon(Icons.Filled.MusicNote, contentDescription = null, modifier = glyph)
        }
    }
}

// ---- Previews ----

/** A song partway, paused; one tinted (a made-up cover colour), playing; one loading; one that failed. */
@ThemePreviews
@Composable
private fun MiniPlayerPreview() {
    PreviewTheme {
        val sheet = rememberAudioPlayerSheetState()
        Column {
            MiniPlayer(title = "A Song", artist = "Some Artist", art = null, progress = { 0.35f }, sheet = sheet) {
                MiniPlayButton(playing = false, buffering = false, failed = false, onClick = {})
            }
            MiniPlayer(
                title = "A rather long file name of a song - Remastered (2026).flac", artist = null, art = null,
                progress = { 0.8f }, sheet = sheet, accent = Color(0xFFD0583A)
            ) {
                MiniPlayButton(playing = true, buffering = false, failed = false, onClick = {})
            }
            MiniPlayer(title = "Loading", artist = "Some Artist", art = null, progress = { 0f }, sheet = sheet, buffering = true) {
                MiniPlayButton(playing = true, buffering = true, failed = false, onClick = {})
            }
            MiniPlayer(title = "Can't be played", artist = null, art = null, progress = { 0f }, sheet = sheet, failed = true) {
                MiniPlayButton(playing = false, buffering = false, failed = true, onClick = {})
            }
        }
    }
}
