package tools.senko.materialdrain.browser

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

/** The height of one result card; every card is the same height, so a card's place follows from its index alone. */
internal val SearchResultHeight = 60.dp

/** The space between two result cards, where the blurred screen shows through. */
internal val SearchResultGap = 8.dp

/** How long a result that no longer matches takes to shrink away. */
private const val LEAVE_MILLIS = 180

/** A result growing into place: a little springy, so it reads as popping in. */
private val EnterSpring = spring<Float>(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow)

/** A result moving to its new place, when ones above it leave or arrive. */
private val PlacementSpring = spring<Float>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)

/**
 * One card of the stack: its node, how grown it is (0 gone, 1 full size) and where it sits. Kept for a while after
 * its node stops matching, so it can shrink away where it was.
 */
private class ResultSlot(val key: String, node: StorageNode) {
    var node by mutableStateOf(node)
    var leaving by mutableStateOf(false)
    val presence = Animatable(0f)
    val y = Animatable(0f)
    var placed = false
}

/**
 * The matches of a search, floating over the blurred screen as separate translucent cards (see [SearchModal]).
 *
 * A card that starts matching grows into its place; one that stops matching shrinks away where it was, drawn beneath
 * the others, while the rest glide to their new places over it. The stack's own height follows along, so whatever is
 * under it (the "more matches" note) moves smoothly too.
 */
@Composable
internal fun SearchResultStack(
    results: List<StorageNode>,
    thumbnailFor: (StorageNode) -> String?,
    onClick: (StorageNode) -> Unit,
    modifier: Modifier = Modifier
) {
    val slots = remember { mutableStateListOf<ResultSlot>() }
    LaunchedEffect(results) {
        val incoming = results.associateBy { it.key }
        slots.forEach { slot ->
            val node = incoming[slot.key]
            if (node != null) {
                slot.node = node
                slot.leaving = false
            } else {
                slot.leaving = true
            }
        }
        val known = slots.mapTo(HashSet()) { it.key }
        results.forEach { if (it.key !in known) slots.add(ResultSlot(it.key, it)) }
    }
    val indexOf = remember(results) { results.withIndex().associate { (index, node) -> node.key to index } }
    val pitchPx = with(LocalDensity.current) { (SearchResultHeight + SearchResultGap).toPx() }
    val height by animateDpAsState(
        targetValue = stackHeight(results.size),
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "searchResultsHeight"
    )

    // Not clipped: a card shrinking away below the stack's new (smaller) height stays visible until it's gone
    Box(modifier = modifier.fillMaxWidth().height(height)) {
        slots.forEach { slot ->
            key(slot.key) {
                val index = indexOf[slot.key]
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
                LaunchedEffect(slot.leaving) {
                    if (slot.leaving) {
                        slot.presence.animateTo(0f, tween(LEAVE_MILLIS))
                        slots.remove(slot)
                    } else {
                        slot.presence.animateTo(1f, EnterSpring)
                    }
                }
                SearchResultCard(
                    node = slot.node,
                    thumbnailUrl = if (slot.node.isDirectory) null else thumbnailFor(slot.node),
                    onClick = { if (!slot.leaving) onClick(slot.node) },
                    modifier = Modifier
                        // Leaving cards go beneath, so the ones moving into their place slide over them
                        .zIndex(if (slot.leaving) 0f else 1f)
                        .offset { IntOffset(0, slot.y.value.roundToInt()) }
                        .graphicsLayer {
                            val presence = slot.presence.value
                            scaleX = presence
                            scaleY = presence
                            alpha = (presence * 1.5f).coerceIn(0f, 1f)
                        }
                )
            }
        }
    }
}

/** The height of [count] cards with the gaps between them. */
internal fun stackHeight(count: Int): Dp =
    if (count == 0) 0.dp else SearchResultHeight * count + SearchResultGap * (count - 1)

/** One match: its picture, name and details on a translucent card, so the blurred screen still shows through. */
@Composable
private fun SearchResultCard(node: StorageNode, thumbnailUrl: String?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(20.dp)
    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.72f),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
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
                val details = fileDetails(node.name, node.isDirectory, node.size)
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
