package tools.senko.materialdrain.ui.media

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import tools.senko.materialdrain.ui.LocalReduceMotion
import tools.senko.materialdrain.ui.components.pageTransition
import tools.senko.materialdrain.ui.components.sidewaysSwipe
import tools.senko.materialdrain.ui.icons.filled.Close
import tools.senko.materialdrain.ui.icons.filled.ExpandMore
import tools.senko.materialdrain.ui.preview.PreviewTheme
import tools.senko.materialdrain.ui.preview.ScreenPreviews
import tools.senko.materialdrain.ui.preview.ThemePreviews
import tools.senko.materialdrain.ui.theme.AppShapes
import androidx.compose.ui.res.stringResource
import tools.senko.materialdrain.R

/**
 * Opens the song player over the whole app (see [AudioPlayerView]), for the song card of a file's details; null where
 * there's none to open (a preview, the upload screen, songs not playing in the background).
 */
val LocalOpenAudioPlayer = staticCompositionLocalOf<(() -> Unit)?> { null }

/**
 * Told whether the song card of a file's details is on the screen, at least half of it (see AudioPlayerPreview): the
 * miniplayer stays away while its song's card is there, and comes in once that's scrolled away. Null where nothing
 * listens (a preview, the upload screen).
 */
val LocalSongCardShown = staticCompositionLocalOf<((Boolean) -> Unit)?> { null }

/**
 * How far the player has to be pulled to go where it's pulled when it's let go slowly, of the way: up from the
 * miniplayer to open, or down from open to close.
 */
private const val SETTLE_FRACTION = 0.35f

/** How quick a shorter pull down has to be to close the player, per second. */
private val CloseSwipeVelocity = 600.dp

/** How much the compact player darkens the page above it, when it's open. */
internal const val COMPACT_SCRIM_ALPHA = 0.32f

/**
 * Where the song player over the whole app is (see [AudioPlayerView]): [progress] goes from 0 (closed: under the bottom
 * of the screen, or, compact, the miniplayer itself) to 1 (open). The miniplayer's swipe up moves it with the finger
 * ([dragTo], then [settle]), a tap opens it at once ([open]).
 *
 * The compact player, open, can grow on into the player over the whole screen: [expansion] goes from 0 (the compact
 * card) to 1 (the whole screen), pulled up on it ([dragExpand], then [settleExpand]); [collapse] takes it back.
 */
@Stable
class AudioPlayerSheetState internal constructor(
    private val scope: CoroutineScope,
    initiallyOpen: Boolean,
    initiallyExpanded: Boolean = false
) {
    val progress = Animatable(if (initiallyOpen) 1f else 0f)

    /** How far the open compact player has grown into the player over the whole screen, 0 to 1 (see CompactPlayerSheet). */
    val expansion = Animatable(if (initiallyOpen && initiallyExpanded) 1f else 0f)

    /** How far the finger moves the compact player from its card to the whole screen (set by CompactPlayerSheet). */
    internal var expandTravelPx = 0f

    /**
     * How far the finger moves it from closed to open: the screen's height, or the compact player's growth from the
     * miniplayer (set by [AudioPlayerView]). Only the drags read it, so it's no state.
     */
    internal var travelPx = 0f

    /** Where the miniplayer is on the screen while it's there, which the compact player grows out of (see MiniPlayer). */
    internal var miniBounds by mutableStateOf<Rect?>(null)

    /**
     * Where the miniplayer's cover, text and play button are while it's there (see MiniPlayerRow), which the compact
     * player's own come out of (see CompactMorph). Only read as the compact player is drawn, so it's no state.
     */
    internal val miniParts = HashMap<MorphPart, LayoutCoordinates>()

    /** The "reduce motion" setting: short fades instead of the slide (set by [AudioPlayerView]). */
    internal var reduceMotion = false

    /** Whether any of it is on the screen. */
    val visible: Boolean get() = progress.value > 0f || progress.targetValue > 0f

    /** Whether the compact player is grown into the whole screen, or on its way there or back. */
    val expanded: Boolean get() = expansion.value > 0f || expansion.targetValue > 0f

    private val motion: AnimationSpec<Float>
        get() = if (reduceMotion) tween(100) else spring(stiffness = Spring.StiffnessMediumLow)

    fun open() {
        scope.launch { progress.animateTo(1f, motion) }
    }

    /** Closes it, from the whole screen too (the song closed while it was grown). */
    fun close() {
        scope.launch { progress.animateTo(0f, motion) }
        if (expanded) scope.launch { expansion.animateTo(0f, motion) }
    }

    /** Grows the open compact player into the whole screen. */
    fun expand() {
        scope.launch { expansion.animateTo(1f, motion) }
    }

    /** Takes the compact player back from the whole screen to its card. */
    fun collapse() {
        scope.launch { expansion.animateTo(0f, motion) }
    }

    /** Pulled up [distancePx] (down, negative) from where it was grown at [startExpansion], under the finger. */
    internal fun dragExpand(startExpansion: Float, distancePx: Float) {
        if (expandTravelPx <= 0f) return
        scope.launch { expansion.snapTo((startExpansion + distancePx / expandTravelPx).coerceIn(0f, 1f)) }
    }

    /**
     * Let go of while growing or shrinking, at [velocityPx] per second (negative is up): as [settle], to the whole screen
     * or back to the card, from the card or ([fromExpanded]) from the whole screen.
     */
    internal fun settleExpand(velocityPx: Float, flingVelocityPx: Float, fromExpanded: Boolean) {
        val toExpanded = when {
            velocityPx <= -flingVelocityPx -> true
            velocityPx >= flingVelocityPx -> false
            fromExpanded -> expansion.value > 1f - SETTLE_FRACTION
            else -> expansion.value >= SETTLE_FRACTION
        }
        if (toExpanded) expand() else collapse()
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

/**
 * The state of the song player over the whole app, open again after the activity is made again if it was open (and the
 * compact player grown into the whole screen, if it was).
 */
@Composable
fun rememberAudioPlayerSheetState(): AudioPlayerSheetState {
    val scope = rememberCoroutineScope()
    return rememberSaveable(
        saver = Saver<AudioPlayerSheetState, List<Boolean>>(
            save = { listOf(it.progress.targetValue >= 1f, it.expansion.targetValue >= 1f) },
            restore = { AudioPlayerSheetState(scope, initiallyOpen = it[0], initiallyExpanded = it[1]) }
        )
    ) { AudioPlayerSheetState(scope, initiallyOpen = false) }
}

/**
 * The song player over the whole app, of the song the background player has ([song]): opened from the song card's badge
 * or from the miniplayer, and closed with its button, back, or pulling it down. Closing it only hides it: the
 * song goes on, in the miniplayer.
 *
 * It covers the whole screen, coming up from the bottom; [compact] (the Media setting "Compact player"), it's a card
 * about a third of the screen high which the miniplayer grows into, with the page dimmed above it (a tap there closes
 * it), and which a pull up grows on into the player over the whole screen (back and a pull down take it back to the
 * card). Either follows the finger from the miniplayer.
 */
@Composable
fun AudioPlayerView(sheet: AudioPlayerSheetState, song: BackgroundSong?, compact: Boolean) {
    val reduceMotion = LocalReduceMotion.current
    SideEffect { sheet.reduceMotion = reduceMotion }
    // Nothing left to show once the song is closed
    val songId = song?.songId
    LaunchedEffect(songId) { if (songId == null && sheet.visible) sheet.close() }
    Box(modifier = Modifier.fillMaxSize().onSizeChanged { if (!compact) sheet.travelPx = it.height.toFloat() }) {
        // The compact player is made while the miniplayer it grows out of is there, closed too (not placed then, so
        // neither drawn nor touched): opening it doesn't wait for it to be made (nor its cover to be read), and its
        // height, which the finger pulls it by, is known from the start
        val keptReady = compact && sheet.miniBounds != null
        if (song != null && (sheet.visible || keptReady)) {
            if (sheet.visible) BackHandler { if (compact && sheet.expanded) sheet.collapse() else sheet.close() }
            // A pull down anywhere on it (what's in it that moves goes first) hides it, back into the miniplayer, which
            // goes on playing; on the open compact player, a pull up grows it into the whole screen, and a pull down from
            // there takes it back to its card. On this box, which stays where it is, so the finger is measured against
            // the screen
            Box(
                modifier = Modifier
                    .placedWhen { sheet.visible }
                    .fillMaxSize()
                    .pointerInput(sheet, compact) {
                        val flingVelocity = CloseSwipeVelocity.toPx()
                        val velocity = VelocityTracker()
                        var start = 1f
                        var startExpansion = 0f
                        var pulled = 0f
                        // Whether the pull grows or shrinks the compact player rather than closing it: told by its first
                        // move, up on the open card, or either way while it's grown
                        var growing: Boolean? = null
                        fun letGo(velocityY: Float) {
                            if (growing == true) sheet.settleExpand(velocityY, flingVelocity, fromExpanded = startExpansion >= 1f)
                            else sheet.settle(velocityY, flingVelocity, fromOpen = true)
                        }
                        detectVerticalDragGestures(
                            onDragStart = {
                                start = sheet.progress.value
                                startExpansion = sheet.expansion.value
                                pulled = 0f
                                growing = null
                                velocity.resetTracking()
                            },
                            onVerticalDrag = { change, dragAmount ->
                                change.consume()
                                pulled += dragAmount
                                velocity.addPosition(change.uptimeMillis, change.position)
                                val grows = growing
                                    ?: (compact && (startExpansion > 0f || (dragAmount < 0f && start >= 1f))).also { growing = it }
                                if (grows) sheet.dragExpand(startExpansion, -pulled) else sheet.dragDown(start, pulled)
                            },
                            onDragEnd = { letGo(velocity.calculateVelocity().y) },
                            onDragCancel = { letGo(0f) }
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

/** Lays this out, but only places it (so draws it, and lets it be touched) while [placed]; read as it's placed. */
private fun Modifier.placedWhen(placed: () -> Boolean): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    layout(placeable.width, placeable.height) { if (placed()) placeable.place(0, 0) }
}

// ---- The whole screen ----

/**
 * The player over the whole screen, from the top down: the cover (or the queue), the seek bar between where the song is
 * and how long it is, the title and artist in the middle, the controls (repeat, before, play, after, shuffle), the kind
 * of file, and the player's own buttons along the bottom (close at the start, the queue at the end).
 */
@Composable
internal fun FullPlayer(song: BackgroundSong, onClose: () -> Unit) {
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
            // The cover up at the top, taking what room the rest leaves it
            BoxWithConstraints(
                modifier = Modifier.weight(1f).fillMaxWidth().padding(top = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                AnimatedContent(
                    targetState = showQueue,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    contentAlignment = Alignment.Center,
                    label = "coverOrQueue"
                ) { queue ->
                    if (queue) {
                        QueueList(song, modifier = Modifier.fillMaxSize())
                    } else {
                        // As big as fits, square
                        SwipeableCover(song, modifier = Modifier.size(minOf(maxWidth, maxHeight).coerceAtLeast(0.dp)))
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
            SongSeek(state, inline = true)
            Spacer(Modifier.height(20.dp))
            SongTitle(song.shown, large = true, centered = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(16.dp))
            SongControls(song, state, playSize = 80.dp, withModes = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(16.dp))
            SongFormat(song, modifier = Modifier.fillMaxWidth())
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp)
            ) {
                // Right under repeat and shuffle
                IconButton(onClick = onClose) {
                    Icon(Icons.Filled.ExpandMore, contentDescription = stringResource(R.string.media_close_the_player))
                }
                Spacer(Modifier.weight(1f))
                QueueButton(showQueue, count = song.queue.size, onClick = { showQueue = !showQueue })
            }
        }
    }
}

/**
 * What kind of file the song is, on one line ("FLAC • 24-bit • 1751 kb/s • 48.0 kHz", see SongTags.Tags.formatLine),
 * once it's read; the line is there before then too, empty, so nothing above it moves when it comes.
 */
@Composable
private fun SongFormat(song: BackgroundSong, modifier: Modifier = Modifier) {
    val line = song.songId?.let { SongTags.of(it)?.formatLine }
    Text(
        line.orEmpty(),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
    )
}

/**
 * The big cover of the song playing. A swipe sideways on it goes to the song before or after (as the miniplayer's does),
 * the new cover coming in like a page.
 */
@Composable
private fun SwipeableCover(song: BackgroundSong, modifier: Modifier = Modifier) {
    val reduceMotion = LocalReduceMotion.current
    AnimatedContent(
        targetState = song.songId to song.art,
        contentKey = { it.first },
        // The way the queue went (round from its last song to its first is on, see BackgroundSong.forward)
        transitionSpec = { pageTransition(song.forward, reduceMotion) },
        modifier = modifier.sidewaysSwipe(enabled = true) { toNext -> song.skip(toNext) },
        label = "cover"
    ) { (_, art) ->
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            MiniCover(art, size = maxWidth, shape = AppShapes.Card)
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
            CompactPlayer(BackgroundSong(player), rememberPlayerUiState(player), morph = null)
        }
    }
}
