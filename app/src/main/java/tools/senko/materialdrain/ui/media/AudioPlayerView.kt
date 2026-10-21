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
import androidx.compose.foundation.lazy.items
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
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.util.lerp
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import tools.senko.materialdrain.settings.ui.accentContainer
import tools.senko.materialdrain.ui.LocalPlayerSettings
import tools.senko.materialdrain.ui.LocalReduceMotion
import tools.senko.materialdrain.ui.components.ScrollingText
import tools.senko.materialdrain.ui.components.pageTransition
import tools.senko.materialdrain.ui.components.sidewaysSwipe
import tools.senko.materialdrain.ui.icons.filled.Close
import tools.senko.materialdrain.ui.icons.filled.ExpandMore
import tools.senko.materialdrain.ui.icons.filled.Pause
import tools.senko.materialdrain.ui.icons.filled.PlayArrow
import tools.senko.materialdrain.ui.icons.filled.QueueMusic
import tools.senko.materialdrain.ui.icons.filled.Refresh
import tools.senko.materialdrain.ui.icons.filled.Repeat
import tools.senko.materialdrain.ui.icons.filled.RepeatOne
import tools.senko.materialdrain.ui.icons.filled.Shuffle
import tools.senko.materialdrain.ui.icons.filled.SkipNext
import tools.senko.materialdrain.ui.icons.filled.SkipPrevious
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
private const val COMPACT_SCRIM_ALPHA = 0.32f

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
            SongTitle(song, large = true, centered = true, modifier = Modifier.fillMaxWidth())
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
                    Icon(Icons.Filled.ExpandMore, contentDescription = "Close the player")
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

/** The parts of the miniplayer the compact player grows out of (see CompactMorph): the cover and title move, the play button stays. */
internal enum class MorphPart { Cover, Text, Play }

/** How big the miniplayer's title is next to the compact player's (titleSmall, 14sp, of titleMedium, 16sp). */
private const val MINI_TEXT_SCALE = 14f / 16f

/** Up to how far open the miniplayer's title and artist fade out, as the compact player's own fade in over them. */
private const val MINI_TEXT_GONE_AT = 0.35f

/** From how far open the rest of the compact player (its seek bar, its other buttons) fades in, until it's open. */
private const val COMPACT_REST_FROM = 0.35f

/** The compact player's shadow, open; it starts out as the miniplayer's ([MiniShadow]). */
private val CompactShadow = 6.dp

/**
 * The miniplayer turning into the compact player: where the parts they share ([MorphPart]) are in the compact player
 * ([parts], in the coordinates of its content, [root]) and in the miniplayer (AudioPlayerSheetState.miniParts), for each
 * to go from the miniplayer's place and size to its own as the sheet opens (see [morphs]). All of it is read as it's
 * drawn, so nothing is composed again for it.
 */
private class CompactMorph(val sheet: AudioPlayerSheetState) {
    private var root: LayoutCoordinates? = null
    private val parts = HashMap<MorphPart, LayoutCoordinates>()

    /**
     * Counts the places becoming known (or new), read with them: the compact player is first placed as it starts to
     * open, and its parts are drawn before their places are told, so this draws them again once they are.
     */
    private var known by mutableIntStateOf(0)

    fun placeRoot(coordinates: LayoutCoordinates) {
        if (root !== coordinates) {
            root = coordinates
            known++
        }
    }

    fun place(part: MorphPart, coordinates: LayoutCoordinates) {
        if (parts.put(part, coordinates) !== coordinates) known++
    }

    /** How far open the sheet is, 0 to 1. */
    val progress: Float get() = sheet.progress.value

    /**
     * Where the miniplayer has [part], from its card's top left corner: the compact card starts out right where the
     * miniplayer's is, so that's where the part starts out in [root] too. Measured from the card rather than the screen,
     * the part goes up with the card's top as it grows, and on to its own place, in one straight move.
     */
    fun miniPlace(part: MorphPart): Rect? {
        known
        val card = sheet.miniBounds ?: return null
        val mini = sheet.miniParts[part]?.takeIf { it.isAttached } ?: return null
        return runCatching { mini.findRootCoordinates().localBoundingBoxOf(mini, clipBounds = false).translate(-card.topLeft) }.getOrNull()
    }

    /** Where [part] is in the compact player and where the miniplayer has it (see [miniPlace]), in [root]'s coordinates; null while either isn't known. */
    fun places(part: MorphPart): Pair<Rect, Rect>? {
        known
        val root = root?.takeIf { it.isAttached } ?: return null
        val own = parts[part]?.takeIf { it.isAttached } ?: return null
        val mini = miniPlace(part) ?: return null
        return runCatching { root.localBoundingBoxOf(own, clipBounds = false) to mini }.getOrNull()
    }
}

/**
 * Draws this part of the compact player where the miniplayer has it while the sheet is closed, and as big, going to its
 * own place and size as it opens (see [CompactMorph]); scaled from its top left corner, from [fromScale] or else the
 * miniplayer's width of it over its own. [corner] rounds it as many dp as it's drawn at, whatever its scale (the cover's,
 * whose corners grow with it), from closed (0) to open (1); [alpha] fades it the same way. Nothing without [morph].
 */
private fun Modifier.morphs(
    morph: CompactMorph?,
    part: MorphPart,
    fromScale: Float? = null,
    corner: ((progress: Float) -> Dp)? = null,
    alpha: ((progress: Float) -> Float)? = null
): Modifier {
    if (morph == null) return this
    return onGloballyPositioned { morph.place(part, it) }.graphicsLayer {
        val progress = morph.progress
        val places = morph.places(part)
        // Until both places are known (for its first frame), not drawn at all rather than at its own place, open
        if (places == null && progress < 1f) {
            this.alpha = 0f
            return@graphicsLayer
        }
        val t = if (places == null) 1f else progress
        var scale = 1f
        if (places != null) {
            val (own, mini) = places
            scale = lerp(fromScale ?: if (own.width > 0f) mini.width / own.width else 1f, 1f, t)
            transformOrigin = TransformOrigin(0f, 0f)
            scaleX = scale
            scaleY = scale
            translationX = (mini.left - own.left) * (1f - t)
            translationY = (mini.top - own.top) * (1f - t)
        }
        corner?.let {
            shape = RoundedCornerShape(it(t) / scale)
            clip = true
        }
        alpha?.let { this.alpha = it(t).coerceIn(0f, 1f) }
    }
}

/** The rest of the compact player (all but the parts it shares with the miniplayer), fading in once those are on their way. */
private fun Modifier.morphRest(morph: CompactMorph?): Modifier =
    if (morph == null) this else graphicsLayer { alpha = ((morph.progress - COMPACT_REST_FROM) / (1f - COMPACT_REST_FROM)).coerceIn(0f, 1f) }

/**
 * The compact player: a card where the miniplayer is (its place told by the miniplayer, see AudioPlayerSheetState), which
 * grows up out of it with the sheet's progress. The miniplayer turns into it: the card starts out as the miniplayer, its
 * cover and title going from the miniplayer's places to their own (see [CompactMorph]), its play button fading out where
 * it was (see [MiniPlayLeaving]) while the rest fades in. Opened where there's no miniplayer (from the song's own card),
 * or with reduced animations, the card fades in over the miniplayer's place instead, then the player on it. The page above is dimmed; a tap there closes it.
 *
 * Pulled up once open, the card grows on to the whole screen (see AudioPlayerSheetState.expansion), its corners going
 * square, while the player over the whole screen fades in on it in place of its own content, the covers with the rest:
 * the two players are laid out too differently for one cover to go from the one's place to the other's well.
 */
@Composable
private fun CompactPlayerSheet(sheet: AudioPlayerSheetState, song: BackgroundSong) {
    val density = LocalDensity.current
    val shown = sheet.progress.value
    val bounds = sheet.miniBounds
    val navigationBar = WindowInsets.navigationBars.getBottom(density)
    // The content's own height, open; the card shows as much of it as the sheet has grown
    var fullHeight by remember { mutableIntStateOf(0) }
    val reduceMotion = LocalReduceMotion.current
    val morph = remember(sheet) { CompactMorph(sheet) }.takeIf { bounds != null && !reduceMotion }
    // Growing into the player over the whole screen (see AudioPlayerSheetState.expansion): how far, and whether the
    // player over the whole screen is to be made at all, under the card
    val grown = sheet.expansion.value
    val growing = sheet.expanded
    // One for all of it; kept ready while closed (see AudioPlayerView), when it doesn't ask the player where the song is
    val state = rememberPlayerUiState(song.player, polling = sheet.visible)
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
        SideEffect {
            sheet.travelPx = (openHeight - miniHeight).toFloat()
            sheet.expandTravelPx = (screenHeight - openHeight).toFloat()
        }
        // The card, from the compact player's place and size to the whole screen's as it grows
        val cardLeft = lerp(left.toFloat(), 0f, grown).roundToInt()
        val cardWidth = lerp(width.toFloat(), screenWidth.toFloat(), grown).roundToInt()
        val cardBottom = lerp(bottom.toFloat(), screenHeight.toFloat(), grown).roundToInt()
        val cardHeight = lerp(height.toFloat(), screenHeight.toFloat(), grown).roundToInt()
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
            // Its corners going square as it fills the screen
            shape = if (grown > 0f) RoundedCornerShape(lerp(CompactCorner, 0.dp, grown)) else AppShapes.Medium,
            shadowElevation = lerp(MiniShadow, CompactShadow, shown),
            modifier = Modifier
                .offset { IntOffset(cardLeft, cardBottom - cardHeight) }
                .width(with(density) { cardWidth.toDp() })
                .height(with(density) { cardHeight.toDp() })
                // Without turning the miniplayer into it, fading in over it, which stays under it until then (see
                // COMPACT_OPAQUE_AT): the miniplayer shows through while this one's cover and colour are on their way
                .graphicsLayer { alpha = if (morph != null) 1f else (sheet.progress.value / COMPACT_OPAQUE_AT).coerceIn(0f, 1f) }
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // The cover's colour across the whole card, however tall it's grown, fading out as the player over the
                // whole screen fades in with its own
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = (1f - sheet.expansion.value / COMPACT_GONE_AT).coerceIn(0f, 1f) }
                        .background(Brush.horizontalGradient(listOf(tint ?: cardColor, cardColor)))
                )
                Box(
                    modifier = Modifier
                        // As wide as the compact card and where it was across, as the card grows past it, so it isn't
                        // laid out again on the way; fading out as the player over the whole screen fades in over it
                        .offset { IntOffset(left - cardLeft, 0) }
                        .width(with(density) { width.toDp() })
                        .graphicsLayer { alpha = (1f - sheet.expansion.value / COMPACT_GONE_AT).coerceIn(0f, 1f) }
                        // Its whole height, of which the card shows what it has grown to
                        .wrapContentHeight(Alignment.Top, unbounded = true)
                        .onSizeChanged { fullHeight = it.height }
                        .then(if (morph != null) Modifier.onGloballyPositioned { morph.placeRoot(it) } else Modifier)
                ) {
                    if (morph != null) {
                        CompactPlayer(song, state, morph)
                        MiniTextLeaving(song, morph)
                        MiniPlayLeaving(state, morph)
                    } else {
                        // The player, coming in as the card grows: already while the card fades in over the miniplayer,
                        // so the two cross rather than leaving the card empty for a moment
                        Box(modifier = Modifier.graphicsLayer { alpha = ((shown - COMPACT_OPAQUE_AT / 2f) / 0.45f).coerceIn(0f, 1f) }) {
                            CompactPlayer(song, state, morph = null)
                        }
                    }
                }
                if (morph != null) {
                    // The miniplayer's progress line, still along the bottom as the card starts to grow, fading out as
                    // the seek bar fades in
                    MiniProgressLine(
                        progress = { if (state.durationMs > 0) state.positionMs.toFloat() / state.durationMs else 0f },
                        buffering = state.isBuffering,
                        failed = state.error != null,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .graphicsLayer { alpha = (1f - morph.progress / COMPACT_REST_FROM).coerceIn(0f, 1f) }
                    )
                }
                if (growing) {
                    // The player over the whole screen, laid out over the whole of it, where it ends up, whatever the
                    // card's size now: the card shows of it what it has grown over, as it fades in
                    Box(
                        modifier = Modifier
                            .layout { measurable, _ ->
                                val placeable = measurable.measure(Constraints.fixed(screenWidth, screenHeight))
                                layout(cardWidth, cardHeight) { placeable.place(-cardLeft, -(cardBottom - cardHeight)) }
                            }
                            .graphicsLayer { alpha = ((sheet.expansion.value - FULL_FROM) / (1f - FULL_FROM)).coerceIn(0f, 1f) }
                    ) {
                        FullPlayer(song, onClose = sheet::collapse)
                    }
                }
            }
        }
    }
}

/** Up to how far grown into the whole screen the compact player's content fades out (see CompactPlayerSheet). */
private const val COMPACT_GONE_AT = 0.4f

/** From how far grown the player over the whole screen fades in over it, until it's there. */
private const val FULL_FROM = 0.25f

/** The compact card's corner radius (AppShapes.Medium's), which goes square as it grows into the whole screen. */
private val CompactCorner = 12.dp

/**
 * The miniplayer's title and artist as it had them, going from its place to where the compact player's own come in
 * (which fade in over them, see CompactPlayer), and fading out meanwhile: the two are laid out differently (one line
 * each, or wrapping), so they cross rather than one turning into the other.
 */
@Composable
private fun BoxScope.MiniTextLeaving(song: BackgroundSong, morph: CompactMorph) {
    MiniPlayerText(
        song.title,
        song.artist,
        modifier = Modifier
            .align(AbsoluteAlignment.TopLeft)
            // As wide as the miniplayer has them, so they're cut and scroll as they did there
            .layout { measurable, constraints ->
                val width = morph.sheet.miniParts[MorphPart.Text]?.takeIf { it.isAttached }?.size?.width ?: constraints.minWidth
                val placeable = measurable.measure(Constraints.fixedWidth(width.coerceAtMost(constraints.maxWidth)))
                layout(placeable.width, placeable.height) { placeable.placeRelative(0, 0) }
            }
            .graphicsLayer {
                val t = morph.progress
                val places = morph.places(MorphPart.Text)
                if (places == null) {
                    alpha = 0f
                    return@graphicsLayer
                }
                val (own, mini) = places
                val scale = lerp(1f, 1f / MINI_TEXT_SCALE, t)
                transformOrigin = TransformOrigin(0f, 0f)
                scaleX = scale
                scaleY = scale
                translationX = lerp(mini.left, own.left, t)
                translationY = lerp(mini.top, own.top, t)
                alpha = (1f - t / MINI_TEXT_GONE_AT).coerceIn(0f, 1f)
            }
    )
}

/**
 * The miniplayer's play button as it had it, staying in its place at the card's top end (going up with it) and fading out
 * as the compact player's own buttons fade in below: a button jumping across the card to the middle of its controls
 * looks worse than the two crossing.
 */
@Composable
private fun BoxScope.MiniPlayLeaving(state: PlayerUiState, morph: CompactMorph) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .align(AbsoluteAlignment.TopLeft)
            .size(48.dp)
            .clearAndSetSemantics {}
            .graphicsLayer {
                val t = morph.progress
                val mini = morph.miniPlace(MorphPart.Play)
                if (mini == null) {
                    alpha = 0f
                    return@graphicsLayer
                }
                translationX = mini.left
                translationY = mini.top
                alpha = (1f - t / MINI_TEXT_GONE_AT).coerceIn(0f, 1f)
            }
    ) {
        MiniPlayGlyph(playing = state.playWhenReady && !state.isEnded, buffering = state.isBuffering, failed = state.error != null)
    }
}

/**
 * The compact player's content: the cover beside what's playing, the seek bar, the controls, and the queue when asked.
 * With [morph], its cover and title start out as the miniplayer's (see [morphs]), and the rest fades in.
 */
@Composable
private fun CompactPlayer(song: BackgroundSong, state: PlayerUiState, morph: CompactMorph?) {
    var showQueue by remember { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 12.dp)) {
        // The handle, which says it can be pulled down
        Box(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .morphRest(morph)
                .padding(vertical = 8.dp)
                .size(width = 32.dp, height = 4.dp)
                .clip(AppShapes.ExtraSmall)
                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Its corners growing from the miniplayer's cover's to its own (see morphs), which rounds it then
            MiniCover(
                song.art,
                size = 96.dp,
                shape = if (morph != null) RectangleShape else AppShapes.Medium,
                modifier = Modifier.morphs(morph, MorphPart.Cover, corner = { lerp(8.dp, 12.dp, it) })
            )
            Spacer(Modifier.width(16.dp))
            SongTitle(
                song,
                large = false,
                modifier = Modifier
                    .weight(1f)
                    .morphs(morph, MorphPart.Text, fromScale = MINI_TEXT_SCALE, alpha = { (it - 0.1f) / MINI_TEXT_GONE_AT })
            )
        }
        Spacer(Modifier.height(12.dp))
        Column(modifier = Modifier.morphRest(morph)) { SongSeek(state) }
        // The song's buttons in the very middle, the queue's at the end
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth()) {
            SongControls(
                song,
                state,
                playSize = 56.dp,
                playModifier = Modifier.morphRest(morph),
                skipModifier = Modifier.morphRest(morph)
            )
            QueueButton(
                showQueue,
                count = song.queue.size,
                onClick = { showQueue = !showQueue },
                modifier = Modifier.align(Alignment.CenterEnd).morphRest(morph)
            )
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

/**
 * The song's title and artist (and album, when it's known), bigger on the whole screen ([large]), and in the middle
 * ([centered]) under the seek bar there.
 */
@Composable
private fun SongTitle(song: BackgroundSong, large: Boolean, modifier: Modifier = Modifier, centered: Boolean = false) {
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
private fun SongSeek(state: PlayerUiState, inline: Boolean = false) {
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
private fun SongControls(
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
        IconButton(onClick = { player.seekToNextMediaItem() }, enabled = song.hasNext, modifier = skipModifier) {
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
private fun QueueButton(open: Boolean, count: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    IconButton(onClick = onClick, modifier = modifier) {
        Icon(
            Icons.Filled.QueueMusic,
            contentDescription = if (open) "Hide the queue" else "Queue, $count songs",
            tint = if (open) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * The songs of the queue in the order they play (shuffled, while shuffle is on), in a box of their own (with its own
 * colour, not the player's tint, which the rows' colours didn't go with), the ones played greyed out and the one playing
 * lit. A tap plays one; the ✕ takes one out (not the one playing, which the miniplayer closes).
 */
@Composable
private fun QueueList(song: BackgroundSong, modifier: Modifier = Modifier) {
    val player = song.player
    // The queue's indexes in the order they play, and where each of them is in it
    val order = song.playOrder.takeIf { it.size == song.queue.size } ?: song.queue.indices.toList()
    val places = remember(order) { IntArray(order.size).also { places -> order.forEachIndexed { place, index -> places[index] = place } } }
    val playingPlace = places.getOrElse(song.index) { 0 }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = playingPlace.coerceAtLeast(0))
    val colors = MaterialTheme.colorScheme
    Surface(
        color = colors.surfaceContainerLow,
        shape = AppShapes.Large,
        border = BorderStroke(1.dp, colors.outlineVariant),
        modifier = modifier
    ) {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(4.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            items(order, key = { index -> "$index:${song.queue[index].mediaId}" }) { index ->
                QueueRow(
                    item = song.queue[index],
                    playing = index == song.index,
                    played = places[index] < playingPlace,
                    onPlay = {
                        player.seekTo(index, 0L)
                        player.play()
                    },
                    onRemove = { player.removeMediaItem(index) }
                )
            }
        }
    }
}

@Composable
private fun QueueRow(item: MediaItem, playing: Boolean, played: Boolean, onPlay: () -> Unit, onRemove: () -> Unit) {
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
            // The songs already played, greyed out
            .graphicsLayer { alpha = if (played) 0.45f else 1f }
            .clip(AppShapes.Medium)
            .background(if (playing) colors.secondaryContainer else Color.Transparent)
            .clickable(onClick = onPlay)
            .padding(start = 8.dp, end = 4.dp, top = 6.dp, bottom = 6.dp)
    ) {
        MiniCover(metadata.artworkData ?: metadata.artworkUri ?: metadata.extras?.getByteArray(EXTRA_FALLBACK_ART), size = 40.dp)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                tags?.title ?: metadata.title?.toString() ?: "Audio",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (playing) FontWeight.SemiBold else null,
                color = if (playing) colors.onSecondaryContainer else colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            (tags?.artist ?: metadata.artist)?.let {
                Text(
                    it.toString(),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (playing) colors.onSecondaryContainer.copy(alpha = 0.8f) else colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        // Small and quiet, so it doesn't make the row taller than its cover
        if (!playing) {
            IconButton(onClick = onRemove, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Filled.Close, contentDescription = "Take out of the queue", modifier = Modifier.size(18.dp), tint = colors.onSurfaceVariant)
            }
        } else {
            // The one playing: a play sign where the others have their ✕
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Filled.PlayArrow, contentDescription = "Playing", modifier = Modifier.size(18.dp), tint = colors.onSecondaryContainer)
            }
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
