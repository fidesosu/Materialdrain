package tools.senko.materialdrain.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import tools.senko.materialdrain.ui.LocalBottomInset
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/** How tall the whole content is, how far down it is scrolled (in pixels), and whether it is taller than the view at all. */
private class ScrollMetrics(val contentPx: Float, val offsetPx: Float, val scrollable: Boolean)

/** Where the dot sits on the track: its top and its length now, and the length it has when it is stretched. */
private class DotGeometry(val top: Float, val length: Float, val stretchedLength: Float)

private val StripWidth = 24.dp

/** The shortest and longest the stretched line gets, whatever the length of the content. */
private val MinLineLength = 24.dp
private val MaxLineLength = 96.dp

/** How long the dot stays a line after the last drag, before it shrinks back to a dot. */
private const val StretchHoldMs = 1500L

/**
 * A small dot at the right edge that shows where a lazy list is. Dragging the dot moves the content and stretches the dot
 * into a line for as long as it is used; letting go stops the content dead, unlike a swipe on the content which keeps its
 * momentum. Only the dot and a little around it takes touches, so everything beside it is still clickable. Nothing is shown
 * when there is nothing to scroll.
 */
@Composable
fun BoxScope.DotScrollbar(state: LazyListState, modifier: Modifier = Modifier) {
    DotScrollbarStrip(state = state, modifier = modifier) { _ -> lazyMetrics(state) }
}

/**
 * The same dot for a column with `verticalScroll`, see [DotScrollbar] for the behaviour. The strip is the viewport, so the
 * whole content is what can be scrolled plus the viewport itself.
 */
@Composable
fun BoxScope.DotScrollbar(state: ScrollState, modifier: Modifier = Modifier) {
    DotScrollbarStrip(state = state, modifier = modifier) { viewportPx ->
        ScrollMetrics(
            contentPx = state.maxValue + viewportPx,
            offsetPx = state.value.toFloat(),
            scrollable = state.maxValue > 0
        )
    }
}

/** A lazy list's content is estimated from its visible items, as the items out of view are not measured. */
private fun lazyMetrics(state: LazyListState): ScrollMetrics {
    val info = state.layoutInfo
    val visible = info.visibleItemsInfo
    if (visible.isEmpty() || info.totalItemsCount == 0) return ScrollMetrics(0f, 0f, scrollable = false)
    val first = visible.first()
    val last = visible.last()
    val allShown = first.index == 0 && first.offset >= 0 && last.index == info.totalItemsCount - 1 &&
        last.offset + last.size <= info.viewportEndOffset
    if (allShown) return ScrollMetrics(0f, 0f, scrollable = false)
    val step = if (last.index > first.index) {
        (last.offset - first.offset).toFloat() / (last.index - first.index)
    } else {
        first.size.toFloat()
    }
    return ScrollMetrics(
        contentPx = info.totalItemsCount * step,
        offsetPx = state.firstVisibleItemIndex * step + state.firstVisibleItemScrollOffset,
        scrollable = true
    )
}

@Composable
private fun BoxScope.DotScrollbarStrip(
    state: ScrollableState,
    modifier: Modifier,
    metrics: (viewportPx: Float) -> ScrollMetrics
) {
    val density = LocalDensity.current
    val dotWidthPx = with(density) { 6.dp.toPx() }
    val marginPx = with(density) { 6.dp.toPx() }
    val grabPx = with(density) { 12.dp.toPx() }
    val minLinePx = with(density) { MinLineLength.toPx() }
    val maxLinePx = with(density) { MaxLineLength.toPx() }
    // A list which goes on under the system's navigation bar (LocalBottomInset): the dot stops above the bar, though the
    // list itself, and so the scrolling it stands for, is measured whole
    val bottomInsetPx = with(density) { LocalBottomInset.current.toPx() }
    val touchSlop = LocalViewConfiguration.current.touchSlop
    val color = MaterialTheme.colorScheme.primary
    val scope = rememberCoroutineScope()

    // The height of the strip, and the coordinates of the touch zone, which moves with the dot as the content scrolls
    var viewportPx by remember { mutableFloatStateOf(0f) }
    var zoneCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }

    // Stretched while the dot is being dragged, and for a moment after; each drag step restarts the hold
    var stretched by remember { mutableStateOf(false) }
    var dragSteps by remember { mutableIntStateOf(0) }
    LaunchedEffect(dragSteps) {
        if (dragSteps > 0) {
            delay(StretchHoldMs)
            stretched = false
        }
    }
    val stretch by animateFloatAsState(targetValue = if (stretched) 1f else 0f, label = "dotStretch")

    fun geometry(viewportPx: Float): DotGeometry? {
        val track = (viewportPx - 2 * marginPx - bottomInsetPx).coerceAtLeast(0f)
        val m = metrics(viewportPx)
        if (!m.scrollable || track <= 0f) return null
        // The stretched line is the visible share of the content, kept between a short and a modest length so it never
        // shrinks to a speck on long lists or fills the track on short ones
        val full = (viewportPx / m.contentPx * track).coerceIn(minLinePx.coerceAtMost(track), maxLinePx.coerceAtMost(track))
        val length = dotWidthPx + (full - dotWidthPx) * stretch
        val fraction = (m.offsetPx / (m.contentPx - viewportPx)).coerceIn(0f, 1f)
        return DotGeometry(top = marginPx + fraction * (track - length), length = length, stretchedLength = full)
    }

    // The strip itself takes no touches. Only the zone placed over the dot does, and it is no taller than the dot and its padding.
    Box(
        modifier = modifier
            .align(Alignment.TopEnd)
            .fillMaxHeight()
            .width(StripWidth)
            .onSizeChanged { viewportPx = it.height.toFloat() }
            .layout { measurable, constraints ->
                val dot = geometry(constraints.maxHeight.toFloat())
                val top = dot?.let { (it.top - grabPx).roundToInt() } ?: 0
                val placeable = measurable.measure(constraints)
                layout(placeable.width, constraints.maxHeight) { placeable.place(0, top) }
            }
    ) {
        Spacer(
            modifier = Modifier
                .layout { measurable, constraints ->
                    val dot = geometry(constraints.maxHeight.toFloat())
                    val zoneHeight = dot?.let { (it.length + 2 * grabPx).roundToInt() } ?: 0
                    val placeable = measurable.measure(Constraints.fixed(constraints.maxWidth, zoneHeight))
                    layout(placeable.width, placeable.height) { placeable.place(0, 0) }
                }
                .onGloballyPositioned { zoneCoordinates = it }
                .drawBehind {
                    val length = size.height - 2 * grabPx
                    if (length <= 0f) return@drawBehind
                    drawRoundRect(
                        color = color,
                        topLeft = Offset((size.width - dotWidthPx) / 2f, grabPx),
                        size = Size(dotWidthPx, length),
                        cornerRadius = CornerRadius(dotWidthPx / 2f)
                    )
                }
                .pointerInput(state) {
                    awaitEachGesture {
                        // Not consumed until the finger moves: a press on the zone without moving is a click on the content
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val coordinates = zoneCoordinates ?: return@awaitEachGesture
                        val startY = coordinates.localToRoot(down.position).y
                        var previousY = startY
                        var dragging = false
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            // Measured on the screen, as the zone itself moves while the content scrolls
                            val y = coordinates.localToRoot(change.position).y
                            if (!dragging) {
                                if (abs(y - startY) <= touchSlop) continue
                                dragging = true
                                stretched = true
                            }
                            val dy = y - previousY
                            previousY = y
                            change.consume()
                            dragSteps++
                            val now = geometry(viewportPx) ?: continue
                            // The dot follows the finger at its stretched length, so the movement is measured against that
                            val movable = viewportPx - 2 * marginPx - bottomInsetPx - now.stretchedLength
                            if (dy == 0f || movable <= 0f) continue
                            // Per pixel of the dot the content moves as far as it is longer than the view, so the dot stays under the finger
                            val contentPerPx = (metrics(viewportPx).contentPx - viewportPx) / movable
                            // A plain scroll, so nothing carries on after the finger lets go
                            scope.launch { state.scroll(MutatePriority.UserInput) { scrollBy(dy * contentPerPx) } }
                        }
                    }
                }
        )
    }
}
