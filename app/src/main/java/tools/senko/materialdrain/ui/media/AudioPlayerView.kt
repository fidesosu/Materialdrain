package tools.senko.materialdrain.ui.media

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import androidx.media3.common.MediaItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import tools.senko.materialdrain.settings.ui.accentContainer
import tools.senko.materialdrain.ui.LocalPlayerSettings
import tools.senko.materialdrain.ui.LocalReduceMotion
import tools.senko.materialdrain.ui.components.AppMenu
import tools.senko.materialdrain.ui.components.AppMenuItem
import tools.senko.materialdrain.ui.components.MenuEdge
import tools.senko.materialdrain.ui.components.ScrollingText
import tools.senko.materialdrain.ui.components.pageTransition
import tools.senko.materialdrain.ui.components.sidewaysSwipe
import tools.senko.materialdrain.ui.icons.filled.Close
import tools.senko.materialdrain.ui.icons.filled.ExpandMore
import tools.senko.materialdrain.ui.icons.filled.Pause
import tools.senko.materialdrain.ui.icons.filled.PlayArrow
import tools.senko.materialdrain.ui.icons.filled.QueueMusic
import tools.senko.materialdrain.ui.icons.filled.Refresh
import tools.senko.materialdrain.ui.icons.filled.SkipNext
import tools.senko.materialdrain.ui.icons.filled.SkipPrevious
import tools.senko.materialdrain.ui.icons.filled.Speed
import tools.senko.materialdrain.ui.preview.PreviewTheme
import tools.senko.materialdrain.ui.preview.ScreenPreviews
import tools.senko.materialdrain.ui.preview.ThemePreviews
import tools.senko.materialdrain.ui.theme.AppShapes
import tools.senko.materialdrain.util.formatDurationMillis
import kotlin.math.roundToInt

/**
 * Opens the song player over the whole app (see [AudioPlayerView]), for the song card of a file's details; null where
 * there's none to open (a preview, the upload screen, songs not playing in the background).
 */
val LocalOpenAudioPlayer = staticCompositionLocalOf<(() -> Unit)?> { null }

/**
 * How far the player has to be pulled to go where it's pulled when it's let go slowly, of the way: up from the
 * miniplayer to open, or down from open to close.
 */
private const val SETTLE_FRACTION = 0.35f

/** How quick a shorter pull down has to be to close the player, per second. */
private val CloseSwipeVelocity = 600.dp

/** How much the compact player darkens the page above it, when it's open. */
private const val COMPACT_SCRIM_ALPHA = 0.32f

/**
 * Where the song player over the whole app is (see [AudioPlayerView]): [progress] goes from 0 (closed: under the bottom
 * of the screen, or, compact, the miniplayer itself) to 1 (open). The miniplayer's swipe up moves it with the finger
 * ([dragTo], then [settle]), a tap opens it at once ([open]).
 */
@Stable
class AudioPlayerSheetState internal constructor(private val scope: CoroutineScope, initiallyOpen: Boolean) {
    val progress = Animatable(if (initiallyOpen) 1f else 0f)

    /**
     * How far the finger moves it from closed to open: the screen's height, or the compact player's growth from the
     * miniplayer (set by [AudioPlayerView]). Only the drags read it, so it's no state.
     */
    internal var travelPx = 0f

    /** Where the miniplayer is on the screen while it's there, which the compact player grows out of (see MiniPlayer). */
    internal var miniBounds by mutableStateOf<Rect?>(null)

    /** The "reduce motion" setting: short fades instead of the slide (set by [AudioPlayerView]). */
    internal var reduceMotion = false

    /** Whether any of it is on the screen. */
    val visible: Boolean get() = progress.value > 0f || progress.targetValue > 0f

    private val motion: AnimationSpec<Float>
        get() = if (reduceMotion) tween(100) else spring(stiffness = Spring.StiffnessMediumLow)

    fun open() {
        scope.launch { progress.animateTo(1f, motion) }
    }

    fun close() {
        scope.launch { progress.animateTo(0f, motion) }
    }

    /** Pulled up [distancePx] from closed, under the finger. */
    fun dragTo(distancePx: Float) {
        if (travelPx <= 0f) return
        snapTo(distancePx / travelPx)
    }

    /** Whether, pulled up [distancePx] from closed and let go slowly, it would open (see [settle]). */
    internal fun opensAt(distancePx: Float): Boolean = travelPx > 0f && distancePx / travelPx >= SETTLE_FRACTION

    /** Pulled down [distancePx] from where it was at [startProgress], under the finger. */
    internal fun dragDown(startProgress: Float, distancePx: Float) {
        if (travelPx <= 0f) return
        snapTo(startProgress - distancePx / travelPx)
    }

    private fun snapTo(value: Float) {
        scope.launch { progress.snapTo(value.coerceIn(0f, 1f)) }
    }

    /**
     * Let go of, at [velocityPx] per second (negative is up): flung, it goes the way it was flung; let go slowly, it goes
     * where it was pulled once that's [SETTLE_FRACTION] of the way, up to open [fromOpen] false, down to close otherwise.
     */
    fun settle(velocityPx: Float, flingVelocityPx: Float, fromOpen: Boolean = false) {
        val toOpen = when {
            velocityPx <= -flingVelocityPx -> true
            velocityPx >= flingVelocityPx -> false
            fromOpen -> progress.value > 1f - SETTLE_FRACTION
            else -> progress.value >= SETTLE_FRACTION
        }
        if (toOpen) open() else close()
    }
}

/** The state of the song player over the whole app, open again after the activity is made again if it was open. */
@Composable
fun rememberAudioPlayerSheetState(): AudioPlayerSheetState {
    val scope = rememberCoroutineScope()
    return rememberSaveable(
        saver = Saver(save = { it.progress.targetValue >= 1f }, restore = { AudioPlayerSheetState(scope, it) })
    ) { AudioPlayerSheetState(scope, initiallyOpen = false) }
}

/**
 * The song player over the whole app, of the song the background player has ([song]): opened from the song card's cover
 * and title or from the miniplayer, and closed with its button, back, or pulling it down. Closing it only hides it: the
 * song goes on, in the miniplayer.
 *
 * It covers the whole screen, coming up from the bottom; [compact] (the Media setting "Compact player"), it's a card
 * about a third of the screen high which the miniplayer grows into, with the page dimmed above it (a tap there closes
 * it). Either follows the finger from the miniplayer.
 */
@Composable
fun AudioPlayerView(sheet: AudioPlayerSheetState, song: BackgroundSong?, compact: Boolean) {
    val reduceMotion = LocalReduceMotion.current
    SideEffect { sheet.reduceMotion = reduceMotion }
    // Nothing left to show once the song is closed
    val songId = song?.songId
    LaunchedEffect(songId) { if (songId == null && sheet.visible) sheet.close() }
    Box(modifier = Modifier.fillMaxSize().onSizeChanged { if (!compact) sheet.travelPx = it.height.toFloat() }) {
        if (sheet.visible && song != null) {
            BackHandler(onBack = sheet::close)
            // A pull down anywhere on it (what's in it that moves goes first) hides it, back into the miniplayer, which
            // goes on playing. On this box, which stays where it is, so the finger is measured against the screen
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(sheet) {
                        val flingVelocity = CloseSwipeVelocity.toPx()
                        val velocity = VelocityTracker()
                        var start = 1f
                        var pulled = 0f
                        detectVerticalDragGestures(
                            onDragStart = {
                                start = sheet.progress.value
                                pulled = 0f
                                velocity.resetTracking()
                            },
                            onVerticalDrag = { change, dragAmount ->
                                change.consume()
                                pulled += dragAmount
                                velocity.addPosition(change.uptimeMillis, change.position)
                                sheet.dragDown(start, pulled)
                            },
                            onDragEnd = { sheet.settle(velocity.calculateVelocity().y, flingVelocity, fromOpen = true) },
                            onDragCancel = { sheet.settle(0f, flingVelocity, fromOpen = true) }
                        )
                    }
            ) {
                if (compact) {
                    CompactPlayerSheet(sheet, song)
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                val shown = sheet.progress.value
                                // Reduced animations: it fades in place instead of sliding
                                if (reduceMotion) alpha = shown else translationY = (1f - shown) * size.height
                            }
                    ) {
                        FullPlayer(song, onClose = sheet::close)
                    }
                }
            }
        }
    }
}

// ---- The whole screen ----

/** The player over the whole screen: the cover, what's playing, the seek bar and the controls, or the queue. */
@Composable
private fun FullPlayer(song: BackgroundSong, onClose: () -> Unit) {
    val state = rememberPlayerUiState(song.player)
    var showQueue by remember { mutableStateOf(false) }
    val tint = rememberPlayerTint(song)
    val surface = MaterialTheme.colorScheme.surface
    Surface(color = surface, modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                // The cover's colour (Tint the player) fading down into the page's
                .background(Brush.verticalGradient(listOf(tint ?: surface, surface)))
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 24.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                IconButton(onClick = onClose, modifier = Modifier.offset(x = (-12).dp)) {
                    Icon(Icons.Filled.ExpandMore, contentDescription = "Close the player")
                }
                Text(
                    if (showQueue) "Queue" else "Now playing",
                    style = MaterialTheme.typography.labelLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
                // As wide as the close button, so the title is in the very middle
                Spacer(Modifier.width(48.dp))
            }
            // The cover up near the top, under the title bar; what room is left over goes under it
            BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                AnimatedContent(
                    targetState = showQueue,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    contentAlignment = Alignment.TopCenter,
                    label = "coverOrQueue"
                ) { queue ->
                    if (queue) {
                        QueueList(song, modifier = Modifier.fillMaxSize().padding(vertical = 8.dp))
                    } else {
                        // As big as fits, square
                        val side = minOf(maxWidth, maxHeight)
                        SwipeableCover(song, modifier = Modifier.size(side).padding(top = 4.dp, bottom = 16.dp))
                    }
                }
            }
            SongTitle(song, large = true)
            Spacer(Modifier.height(16.dp))
            SongSeek(state)
            Spacer(Modifier.height(8.dp))
            SongControls(song, state, playSize = 72.dp, modifier = Modifier.fillMaxWidth())
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                SpeedButton(state)
                Spacer(Modifier.weight(1f))
                QueueButton(showQueue, count = song.queue.size, onClick = { showQueue = !showQueue })
            }
        }
    }
}

/**
 * The big cover of the song playing. A swipe sideways on it goes to the song before or after (as the miniplayer's does),
 * the new cover coming in like a page.
 */
@Composable
private fun SwipeableCover(song: BackgroundSong, modifier: Modifier = Modifier) {
    val reduceMotion = LocalReduceMotion.current
    val player = song.player
    // Which way the queue went, for the way the new cover comes in
    var lastIndex by remember { mutableIntStateOf(song.index) }
    val forward = song.index >= lastIndex
    SideEffect { lastIndex = song.index }
    AnimatedContent(
        targetState = song.songId to song.art,
        contentKey = { it.first },
        transitionSpec = { pageTransition(forward, reduceMotion) },
        modifier = modifier.sidewaysSwipe(enabled = true) { toNext ->
            if (toNext && player.hasNextMediaItem()) player.seekToNextMediaItem()
            else if (!toNext && player.hasPreviousMediaItem()) player.seekToPreviousMediaItem()
        },
        label = "cover"
    ) { (_, art) ->
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            MiniCover(art, size = maxWidth, shape = AppShapes.Card)
        }
    }
}

// ---- Compact ----

/**
 * The compact player: a card where the miniplayer is (its place told by the miniplayer, see AudioPlayerSheetState), which
 * grows up out of it with the sheet's progress, fading in over the miniplayer (which stays under it until then) before
 * the player comes in on it. The page above is dimmed; a tap there closes it.
 */
@Composable
private fun CompactPlayerSheet(sheet: AudioPlayerSheetState, song: BackgroundSong) {
    val density = LocalDensity.current
    val shown = sheet.progress.value
    val bounds = sheet.miniBounds
    val navigationBar = WindowInsets.navigationBars.getBottom(density)
    // The content's own height, open; the card shows as much of it as the sheet has grown
    var fullHeight by remember { mutableIntStateOf(0) }
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val margin = with(density) { 8.dp.roundToPx() }
        val screenWidth = constraints.maxWidth
        val screenHeight = constraints.maxHeight
        // Without the miniplayer (e.g. opened from the song's own card), where it would be: at the bottom
        val left = bounds?.left?.roundToInt() ?: margin
        val width = bounds?.width?.roundToInt() ?: (screenWidth - margin * 2)
        val bottom = bounds?.bottom?.roundToInt() ?: (screenHeight - navigationBar - margin)
        val miniHeight = bounds?.height?.roundToInt() ?: with(density) { 66.dp.roundToPx() }
        val openHeight = maxOf(fullHeight, miniHeight)
        val height = lerp(miniHeight.toFloat(), openHeight.toFloat(), shown).roundToInt()
        SideEffect { sheet.travelPx = (openHeight - miniHeight).toFloat() }
        // The page above, dimmed; a tap there closes the player
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = COMPACT_SCRIM_ALPHA * shown))
                .pointerInput(sheet) { detectTapGestures { sheet.close() } }
        )
        val colors = MaterialTheme.colorScheme
        val cardColor = colors.surfaceContainerHigh
        val tint = rememberPlayerTint(song)
        Surface(
            color = cardColor,
            shape = AppShapes.Medium,
            tonalElevation = 3.dp,
            shadowElevation = 6.dp,
            modifier = Modifier
                .offset { IntOffset(left, bottom - height) }
                .width(with(density) { width.toDp() })
                .height(with(density) { height.toDp() })
                // Fading in over the miniplayer, which stays under it until then (see COMPACT_OPAQUE_AT): the miniplayer
                // shows through while this one's cover and colour are still on their way
                .graphicsLayer { alpha = (sheet.progress.value / COMPACT_OPAQUE_AT).coerceIn(0f, 1f) }
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Brush.horizontalGradient(listOf(tint ?: cardColor, cardColor)))
                    // Its whole height, of which the card shows what it has grown to
                    .wrapContentHeight(Alignment.Top, unbounded = true)
                    .onSizeChanged { fullHeight = it.height }
            ) {
                // The player, coming in as the card grows: already while the card fades in over the miniplayer, so the
                // two cross rather than leaving the card empty for a moment
                Box(modifier = Modifier.graphicsLayer { alpha = ((shown - COMPACT_OPAQUE_AT / 2f) / 0.45f).coerceIn(0f, 1f) }) {
                    CompactPlayer(song)
                }
            }
        }
    }
}

/** The compact player's content: the cover beside what's playing, the seek bar, the controls, and the queue when asked. */
@Composable
private fun CompactPlayer(song: BackgroundSong) {
    val state = rememberPlayerUiState(song.player)
    var showQueue by remember { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 12.dp)) {
        // The handle, which says it can be pulled down
        Box(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(vertical = 8.dp)
                .size(width = 32.dp, height = 4.dp)
                .clip(AppShapes.ExtraSmall)
                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            MiniCover(song.art, size = 96.dp, shape = AppShapes.Medium)
            Spacer(Modifier.width(16.dp))
            SongTitle(song, large = false, modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(12.dp))
        SongSeek(state)
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            SpeedButton(state)
            SongControls(song, state, playSize = 56.dp, modifier = Modifier.weight(1f))
            QueueButton(showQueue, count = song.queue.size, onClick = { showQueue = !showQueue }, label = false)
        }
        AnimatedVisibility(visible = showQueue, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
            QueueList(song, modifier = Modifier.fillMaxWidth().heightIn(max = 280.dp).padding(top = 8.dp))
        }
    }
}

// ---- Shared by both ----

/** The cover's colour as a container colour of the theme, with the Media setting "Tint the player" on; else null. */
@Composable
private fun rememberPlayerTint(song: BackgroundSong): Color? {
    val colors = MaterialTheme.colorScheme
    val accent = rememberCoverAccent(song.cover, enabled = LocalPlayerSettings.current.tintPlayer)
    val target = accent?.let { accentContainer(it, colors.primaryContainer, colors.primary) }
    val animated by animateColorAsState(target ?: colors.surfaceContainerHigh, label = "playerTint")
    return if (target == null && !LocalPlayerSettings.current.tintPlayer) null else animated
}

/** The song's title and artist (and album, when it's known), bigger on the whole screen ([large]). */
@Composable
private fun SongTitle(song: BackgroundSong, large: Boolean, modifier: Modifier = Modifier) {
    // Wrapping while they fit; one that wouldn't, scrolling on one line instead (see ScrollingText)
    Column(modifier = modifier) {
        ScrollingText(
            song.title ?: "Audio",
            style = if (large) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleMedium,
            maxLines = 2
        )
        val details = listOfNotNull(song.artist, song.album).joinToString(" · ")
        if (details.isNotEmpty()) {
            ScrollingText(
                details,
                style = if (large) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** The seek bar, with where the song is and how long it is under it. */
@Composable
private fun SongSeek(state: PlayerUiState) {
    var scrubbing by remember { mutableStateOf(false) }
    var scrubPositionMs by remember { mutableLongStateOf(0L) }
    val shownPosition = if (scrubbing) scrubPositionMs else state.positionMs
    val onSurface = MaterialTheme.colorScheme.onSurface
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
        activeColor = MaterialTheme.colorScheme.primary,
        trackColor = onSurface.copy(alpha = 0.15f),
        bufferedColor = onSurface.copy(alpha = 0.25f)
    )
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(formatDurationMillis(shownPosition), style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
        Text(
            if (state.durationMs > 0) formatDurationMillis(state.durationMs) else "--:--",
            style = MaterialTheme.typography.labelMedium
        )
    }
}

/**
 * The song before, play or pause (or try again, or the loading circle), and the song after. "Before" starts the song
 * over first when it's past its start, as players do.
 */
@Composable
private fun SongControls(song: BackgroundSong, state: PlayerUiState, playSize: androidx.compose.ui.unit.Dp, modifier: Modifier = Modifier) {
    val player = song.player
    val playing = state.playWhenReady && !state.isEnded
    val failed = state.error != null
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = { player.seekToPrevious() }) {
            Icon(Icons.Filled.SkipPrevious, contentDescription = "Previous song")
        }
        FilledIconButton(
            onClick = { if (failed) state.retry() else state.togglePlayPause() },
            shape = AppShapes.Large,
            colors = if (failed) IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.error) else IconButtonDefaults.filledIconButtonColors(),
            modifier = Modifier.size(playSize)
        ) {
            val glyph = Modifier.size(playSize * 0.45f)
            when {
                failed -> Icon(Icons.Filled.Refresh, contentDescription = "Try again", modifier = glyph)
                state.isBuffering -> CircularProgressIndicator(
                    modifier = glyph,
                    strokeWidth = 3.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
                else -> Icon(if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, contentDescription = if (playing) "Pause" else "Play", modifier = glyph)
            }
        }
        IconButton(onClick = { player.seekToNextMediaItem() }, enabled = song.index < song.queue.lastIndex) {
            Icon(Icons.Filled.SkipNext, contentDescription = "Next song")
        }
    }
}

/** The playback speed, which opens a menu of the speeds: a button like the queue's, its icon lit when it isn't 1×. */
@Composable
private fun SpeedButton(state: PlayerUiState) {
    var open by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { open = true }) {
            Icon(
                Icons.Filled.Speed,
                contentDescription = null,
                tint = if (state.speed != 1f) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(6.dp))
            Text(speedLabel(state.speed))
        }
        AppMenu(expanded = open, onDismiss = { open = false }, edge = MenuEdge.Start, minWidth = 120.dp) {
            PlaybackSpeeds.forEach { speed ->
                AppMenuItem(text = speedLabel(speed), active = speed == state.speed, onClick = { open = false; state.changeSpeed(speed) })
            }
        }
    }
}

/**
 * Shows or hides the queue, lit while it's shown, with how many songs are in it; without [label] (the compact player's
 * row, which has no room for it), only its icon.
 */
@Composable
private fun QueueButton(open: Boolean, count: Int, onClick: () -> Unit, label: Boolean = true) {
    if (!label) {
        IconButton(onClick = onClick) {
            Icon(
                Icons.Filled.QueueMusic,
                contentDescription = if (open) "Hide the queue" else "Queue, $count songs",
                tint = if (open) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }
    TextButton(onClick = onClick) {
        Icon(
            Icons.Filled.QueueMusic,
            contentDescription = null,
            tint = if (open) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.width(6.dp))
        Text(if (open) "Hide queue" else "Queue · $count")
    }
}

/**
 * The songs of the queue in their order, the one playing lit. A tap plays one; the ✕ takes one out (not the one
 * playing, which the miniplayer closes).
 */
@Composable
private fun QueueList(song: BackgroundSong, modifier: Modifier = Modifier) {
    val player = song.player
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = song.index.coerceAtLeast(0))
    LazyColumn(state = listState, modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        itemsIndexed(song.queue, key = { index, item -> "$index:${item.mediaId}" }) { index, item ->
            QueueRow(
                item = item,
                playing = index == song.index,
                onPlay = {
                    player.seekTo(index, 0L)
                    player.play()
                },
                onRemove = { player.removeMediaItem(index) }
            )
        }
    }
}

@Composable
private fun QueueRow(item: MediaItem, playing: Boolean, onPlay: () -> Unit, onRemove: () -> Unit) {
    val metadata = item.mediaMetadata
    val colors = MaterialTheme.colorScheme
    // Its title from its tags, read once the row is shown (see SongTags)
    val context = LocalContext.current
    LaunchedEffect(item.mediaId) { SongTags.request(context, item.mediaId) }
    val tags = SongTags.of(item.mediaId)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(AppShapes.Large)
            .background(if (playing) colors.primaryContainer.copy(alpha = 0.6f) else Color.Transparent)
            .clickable(onClick = onPlay)
            .padding(start = 8.dp, end = 4.dp, top = 6.dp, bottom = 6.dp)
    ) {
        MiniCover(metadata.artworkData ?: metadata.artworkUri ?: metadata.extras?.getByteArray(EXTRA_FALLBACK_ART), size = 40.dp)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                tags?.title ?: metadata.title?.toString() ?: "Audio",
                style = MaterialTheme.typography.bodyLarge,
                color = if (playing) colors.primary else colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            (tags?.artist ?: metadata.artist)?.let {
                Text(it.toString(), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        // Small and quiet, so it doesn't make the row taller than its cover
        if (!playing) {
            IconButton(onClick = onRemove, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Filled.Close, contentDescription = "Take out of the queue", modifier = Modifier.size(18.dp), tint = colors.onSurfaceVariant)
            }
        } else {
            Spacer(Modifier.width(32.dp))
        }
    }
}

// ---- Previews ----

/** The player over the whole screen, paused partway (the preview's player loads nothing). */
@ScreenPreviews
@Composable
private fun FullPlayerPreview() {
    PreviewTheme {
        val player = remember { previewPlayer() }
        FullPlayer(BackgroundSong(player), onClose = {})
    }
}

/** The compact player's content, open. */
@ThemePreviews
@Composable
private fun CompactPlayerPreview() {
    PreviewTheme {
        val player = remember { previewPlayer() }
        Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = AppShapes.Medium, modifier = Modifier.widthIn(max = 420.dp)) {
            CompactPlayer(BackgroundSong(player))
        }
    }
}
