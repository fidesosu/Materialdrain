package tools.senko.materialdrain.browser

import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import tools.senko.materialdrain.files.key
import tools.senko.materialdrain.provider.api.StorageNode
import tools.senko.materialdrain.ui.components.FileIcon
import tools.senko.materialdrain.ui.components.fileDetails
import kotlin.math.roundToInt

// ---- The floating card look, shared by the search results and the controls above the browser's list ----

/** The corners of a floating card. */
internal val FloatingCardShape = RoundedCornerShape(20.dp)

/** A floating card's fill: translucent, so whatever is behind it (the blur, in the search) still shows through. */
@Composable
internal fun floatingCardColor(): Color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.72f)

/** A floating card's hairline edge, which keeps it apart from what's behind it. */
@Composable
internal fun floatingCardBorder(): BorderStroke = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

// ---- The search results ----

/** The height of one result card; every card is the same height, so a card's place follows from its index alone. */
internal val SearchResultHeight = 60.dp

/** The space between two result cards, where the blurred screen shows through. */
internal val SearchResultGap = 8.dp

/** How long a result that no longer matches takes to shrink away. */
private const val LEAVE_MILLIS = 180

/** A card made within this long of the results changing is a new match, and grows in; later, it was scrolled to. */
private const val ENTER_WINDOW_MILLIS = 400L

/** How far past the visible part of the stack cards are made, so a quick scroll doesn't show them popping in. */
private val OffscreenMargin = 240.dp

/** A result growing into place: a little springy, so it reads as popping in. */
private val EnterSpring = spring<Float>(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow)

/** A result moving to its new place, when ones above it leave or arrive. */
private val PlacementSpring = spring<Float>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)

/**
 * One card of the stack: how grown it is (0 gone, 1 full size) and where it sits. Only cards near the visible part of
 * the stack exist; one that stops matching is kept while it shrinks away where it was.
 *
 * Plain fields rather than state: they only change while the stack is composed, along with the results, or right
 * before the stack is told to recompose (see `removals` below).
 */
private class ResultSlot(val key: String, var node: StorageNode, grow: Boolean) {
    val presence = Animatable(if (grow) 0f else 1f)
    val y = Animatable(0f)
    var placed = false
    var shown = false
    var leaving = false
}

/**
 * The matches of a search, floating over the blurred screen as separate translucent cards (see [SearchModal]). Scrolls
 * when there are more than fit, making only the cards near the visible part, so thousands of matches stay smooth.
 *
 * A card that starts matching grows into its place; one that stops matching shrinks away where it was, drawn beneath
 * the others, while the rest glide to their new places over it. The stack's height follows along, so whatever is under
 * it moves smoothly too.
 *
 * @param locationOf where a match is, shown on its card; null when that's where the search was opened
 */
@Composable
internal fun SearchResultStack(
    results: List<StorageNode>,
    thumbnailFor: (StorageNode) -> String?,
    locationOf: (StorageNode) -> String?,
    onClick: (StorageNode) -> Unit,
    modifier: Modifier = Modifier
) {
    val slots = remember { HashMap<String, ResultSlot>() }
    val leaving = remember { ArrayList<ResultSlot>() }
    // Bumped when a card finished shrinking away, so the stack is composed again without it
    var removals by remember { mutableIntStateOf(0) }
    @Suppress("UNUSED_VARIABLE") val readRemovals = removals

    // What changed since the last results: new matches grow in, ones that stopped matching shrink away
    val changedAt = remember(results) {
        val incoming = results.associateBy { it.key }
        slots.values.toList().forEach { slot ->
            val node = incoming[slot.key]
            when {
                node != null -> {
                    slot.node = node
                    if (slot.leaving) {
                        slot.leaving = false
                        leaving.remove(slot)
                    }
                }
                slot.leaving -> Unit
                // Shown: it shrinks away. Not shown (scrolled away): nothing to see, just forgotten
                slot.shown -> {
                    slot.leaving = true
                    leaving += slot
                }
                else -> slots.remove(slot.key)
            }
        }
        SystemClock.uptimeMillis()
    }

    val density = LocalDensity.current
    val pitchPx = with(density) { (SearchResultHeight + SearchResultGap).toPx() }
    val marginPx = with(density) { OffscreenMargin.toPx() }
    val height by animateDpAsState(
        targetValue = stackHeight(results.size),
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "searchResultsHeight"
    )
    val scroll = rememberScrollState()

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val viewportPx = constraints.maxHeight.toFloat()
        // Not clipped inside: a card shrinking away below the stack's new (smaller) height stays until it's gone
        Box(modifier = Modifier.fillMaxWidth().verticalScroll(scroll)) {
            Box(modifier = Modifier.fillMaxWidth().height(height)) {
                if (results.isNotEmpty()) {
                    val first = ((scroll.value - marginPx) / pitchPx).toInt().coerceIn(0, results.lastIndex)
                    val last = ((scroll.value + viewportPx + marginPx) / pitchPx).toInt().coerceIn(first, results.lastIndex)
                    for (index in first..last) {
                        val node = results[index]
                        val slot = slots.getOrPut(node.key) {
                            ResultSlot(node.key, node, grow = SystemClock.uptimeMillis() - changedAt < ENTER_WINDOW_MILLIS)
                        }
                        key(slot.key) {
                            ResultSlotCard(slot, index, pitchPx, thumbnailFor, locationOf, onClick) {
                                leaving.remove(slot)
                                slots.remove(slot.key)
                                removals++
                            }
                        }
                    }
                }
                leaving.toList().forEach { slot ->
                    key(slot.key) {
                        ResultSlotCard(slot, null, pitchPx, thumbnailFor, locationOf, onClick) {
                            leaving.remove(slot)
                            slots.remove(slot.key)
                            removals++
                        }
                    }
                }
            }
        }
    }
}

/** A card of the stack at its animated place and size; [index] is null while it shrinks away. */
@Composable
private fun ResultSlotCard(
    slot: ResultSlot,
    index: Int?,
    pitchPx: Float,
    thumbnailFor: (StorageNode) -> String?,
    locationOf: (StorageNode) -> String?,
    onClick: (StorageNode) -> Unit,
    onGone: () -> Unit
) {
    DisposableEffect(slot) {
        slot.shown = true
        onDispose {
            slot.shown = false
            // Made again later (scrolled back to): it appears right in its place, no gliding from where it once was
            slot.placed = false
        }
    }
    LaunchedEffect(index) {
        if (index == null) return@LaunchedEffect
        val target = index * pitchPx
        // A new card appears right in its place; one already shown glides there
        if (!slot.placed) {
            slot.y.snapTo(target)
            slot.placed = true
        } else {
            slot.y.animateTo(target, PlacementSpring)
        }
    }
    val isLeaving = index == null
    LaunchedEffect(isLeaving) {
        if (isLeaving) {
            slot.presence.animateTo(0f, tween(LEAVE_MILLIS))
            onGone()
        } else {
            slot.presence.animateTo(1f, EnterSpring)
        }
    }
    val node = slot.node
    SearchResultCard(
        node = node,
        thumbnailUrl = if (node.isDirectory) null else thumbnailFor(node),
        location = locationOf(node),
        onClick = { if (!isLeaving) onClick(node) },
        modifier = Modifier
            // Leaving cards go beneath, so the ones moving into their place slide over them
            .zIndex(if (isLeaving) 0f else 1f)
            .offset { IntOffset(0, slot.y.value.roundToInt()) }
            .graphicsLayer {
                val presence = slot.presence.value
                scaleX = presence
                scaleY = presence
                alpha = (presence * 1.5f).coerceIn(0f, 1f)
            }
    )
}

/** The height of [count] cards with the gaps between them. */
internal fun stackHeight(count: Int): Dp =
    if (count == 0) 0.dp else SearchResultHeight * count + SearchResultGap * (count - 1)

/** One match: its picture, name, where it is and its details, on a floating card. */
@Composable
private fun SearchResultCard(
    node: StorageNode,
    thumbnailUrl: String?,
    location: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = FloatingCardShape,
        color = floatingCardColor(),
        border = floatingCardBorder(),
        modifier = modifier
            .fillMaxWidth()
            .height(SearchResultHeight)
    ) {
        Row(
            modifier = Modifier
                .clickable(onClick = onClick)
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FileIcon(name = node.name, isDirectory = node.isDirectory, thumbnailUrl = thumbnailUrl, size = 40.dp, cornerRadius = 12.dp)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = node.name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val details = listOfNotNull(location?.let { "in $it" }, fileDetails(node.name, node.isDirectory, node.size).ifEmpty { null })
                    .joinToString(" • ")
                if (details.isNotEmpty()) {
                    Text(
                        text = details,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(Modifier.width(6.dp))
        }
    }
}
