package tools.senko.materialdrain.ui.media

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.ui.draw.shadow
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.util.lerp
import tools.senko.materialdrain.ui.LocalReduceMotion
import tools.senko.materialdrain.ui.components.pageTransition
import tools.senko.materialdrain.ui.components.sidewaysSwipe
import tools.senko.materialdrain.ui.theme.AppShapes
import kotlin.math.roundToInt

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
internal class CompactMorph(val sheet: AudioPlayerSheetState) {
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
internal fun CompactPlayerSheet(sheet: AudioPlayerSheetState, song: BackgroundSong) {
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
internal fun CompactPlayer(song: BackgroundSong, state: PlayerUiState, morph: CompactMorph?) {
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
        // A swipe sideways on the song goes to the one before or after, which comes in like a page, as the big cover
        // does: only the song's cover and words change, the rest of the card stays as it is
        val reduceMotion = LocalReduceMotion.current
        AnimatedContent(
            targetState = song.shown,
            contentKey = { it.id },
            transitionSpec = { pageTransition(song.forward, reduceMotion) },
            modifier = Modifier.fillMaxWidth().sidewaysSwipe(enabled = true) { toNext -> song.skip(toNext) },
            label = "compactSong"
        ) { shown ->
            // Only the song playing grows out of the miniplayer's places (see CompactMorph), not one on its way out
            val partsMorph = morph.takeIf { shown.id == song.songId }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                // Its corners growing from the miniplayer's cover's to its own (see morphs), which rounds it then
                MiniCover(
                    shown.art,
                    size = 96.dp,
                    shape = if (partsMorph != null) RectangleShape else AppShapes.Medium,
                    modifier = Modifier.morphs(partsMorph, MorphPart.Cover, corner = { lerp(8.dp, 12.dp, it) })
                )
                Spacer(Modifier.width(16.dp))
                SongTitle(
                    shown,
                    large = false,
                    modifier = Modifier
                        .weight(1f)
                        .morphs(partsMorph, MorphPart.Text, fromScale = MINI_TEXT_SCALE, alpha = { (it - 0.1f) / MINI_TEXT_GONE_AT })
                )
            }
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
            QueueList(song, shape = AppShapes.Medium, modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp).padding(top = 8.dp))
        }
    }
}
