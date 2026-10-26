package tools.senko.materialdrain.ui.media

import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.zIndex
import tools.senko.materialdrain.ui.icons.filled.DragHandle
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import tools.senko.materialdrain.ui.icons.filled.Close
import tools.senko.materialdrain.ui.icons.filled.PlayArrow
import tools.senko.materialdrain.ui.theme.AppShapes

/**
 * A song of the queue dragged by its handle to another place (see [QueueList]): [shown] is the queue as it's shown, the
 * dragged song moving through it with the finger, and the player is only told where it went once it's let go.
 */
@Stable
private class QueueReorder(private val listState: LazyListState, order: List<Int>) {
    /** The queue's indexes in the order they're shown. */
    var shown by mutableStateOf(order)
        private set

    /** The key of the row being dragged, null while none is. */
    var dragged by mutableStateOf<Any?>(null)
        private set

    /** The queue's index of the song being dragged, and the order it was dragged out of. */
    private var draggedIndex = -1
    private var before: List<Int> = order

    /** Where the dragged row was when it was picked up, and how far the finger has moved it since. */
    private var startOffset = 0
    private var moved by mutableFloatStateOf(0f)

    /** The player's order, shown as it is, unless a row is being dragged (its move isn't the player's yet). */
    fun sync(order: List<Int>) {
        if (dragged == null) shown = order
    }

    fun start(key: Any, index: Int) {
        val row = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == key } ?: return
        before = shown
        dragged = key
        draggedIndex = index
        startOffset = row.offset
        moved = 0f
    }

    fun dragBy(amount: Float) {
        if (dragged == null) return
        moved += amount
        swapIfOver()
    }

    /** Moves the dragged song to the row its middle is over, if it's another. */
    private fun swapIfOver() {
        val rows = listState.layoutInfo.visibleItemsInfo
        val row = rows.firstOrNull { it.key == dragged } ?: return
        val middle = startOffset + moved + row.size / 2f
        val target = rows.firstOrNull { it.key != dragged && middle >= it.offset && middle < it.offset + it.size } ?: return
        // The list keeps its first row where it is, which would scroll it along with a row moved off or onto it
        if (row.index == listState.firstVisibleItemIndex || target.index == listState.firstVisibleItemIndex) {
            listState.requestScrollToItem(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset)
        }
        shown = shown.toMutableList().apply { add(target.index, removeAt(row.index)) }
    }

    /** How far the row of [key] is drawn from its place: the dragged one under the finger, the others in their place. */
    fun offsetOf(key: Any): Float {
        if (key != dragged) return 0f
        val row = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == key } ?: return 0f
        return startOffset + moved - row.offset
    }

    /**
     * While a row is held near the top or bottom of the list, moving that way, scrolls the list on a little (called each
     * frame), the row staying under the finger and moving past the ones that go by.
     */
    suspend fun scrollAtEdge(edgePx: Float) {
        val info = listState.layoutInfo
        val row = info.visibleItemsInfo.firstOrNull { it.key == dragged } ?: return
        val top = startOffset + moved
        val bottom = top + row.size
        val amount = when {
            moved > 0f && bottom > info.viewportEndOffset - edgePx -> (bottom - (info.viewportEndOffset - edgePx)) / 4f
            moved < 0f && top < info.viewportStartOffset + edgePx -> (top - (info.viewportStartOffset + edgePx)) / 4f
            else -> 0f
        }.coerceIn(-edgePx / 2f, edgePx / 2f)
        if (amount != 0f && listState.scrollBy(amount) != 0f) swapIfOver()
    }

    /** Let go of: the order it was dragged out of, and where in it the song went from and to; null if nowhere. */
    fun end(): Triple<List<Int>, Int, Int>? {
        if (dragged == null) return null
        dragged = null
        moved = 0f
        val from = before.indexOf(draggedIndex)
        val to = shown.indexOf(draggedIndex)
        return if (from == -1 || to == -1 || from == to) null else Triple(before, from, to)
    }
}

/** How close to the top or bottom of the queue a dragged song scrolls it on. */
private val QueueEdge = 48.dp

/**
 * The songs of the queue in the order they play (shuffled, while shuffle is on), under how many there are: on a see-
 * through shade, so the player's tint shows through as it does around the cover, in the cover's [shape]. The ones played
 * are greyed out and the one playing is lit. A tap plays one, the ✕ takes one out (not the one playing, which the
 * miniplayer closes), and the handle at the end drags one to another place (see moveInPlayOrder).
 */
@Composable
internal fun QueueList(song: BackgroundSong, modifier: Modifier = Modifier, shape: Shape = AppShapes.Card) {
    val player = song.player
    // The queue's indexes in the order they play
    val order = song.playOrder.takeIf { it.size == song.queue.size } ?: song.queue.indices.toList()
    // Each song's key, kept as the queue is rearranged: its address, and which of the songs with that address it is
    val keys = remember(song.queue) {
        val seen = HashMap<String, Int>()
        song.queue.map { item ->
            val count = seen[item.mediaId] ?: 0
            seen[item.mediaId] = count + 1
            "${item.mediaId}#$count"
        }
    }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = order.indexOf(song.index).coerceAtLeast(0))
    val reorder = remember(listState) { QueueReorder(listState, order) }
    LaunchedEffect(order) { reorder.sync(order) }
    // Scrolling on while a song is held at the top or bottom
    val edgePx = with(LocalDensity.current) { QueueEdge.toPx() }
    val dragging = reorder.dragged != null
    LaunchedEffect(dragging) {
        if (!dragging) return@LaunchedEffect
        while (true) {
            withFrameNanos {}
            reorder.scrollAtEdge(edgePx)
        }
    }
    val shown = reorder.shown.takeIf { it.size == song.queue.size } ?: order
    val playingPlace = shown.indexOf(song.index)
    val letGo = { reorder.end()?.let { (before, from, to) -> player.moveInPlayOrder(before, from, to) } }
    val colors = MaterialTheme.colorScheme
    Surface(color = colors.surfaceContainerHighest.copy(alpha = 0.55f), shape = shape, modifier = modifier) {
        Column {
            Row(
                verticalAlignment = Alignment.Bottom,
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp)
            ) {
                Text("Queue", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Text(
                    if (shown.size == 1) "1 song" else "${shown.size} songs",
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.onSurfaceVariant
                )
            }
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(start = 6.dp, end = 6.dp, bottom = 6.dp)
            ) {
                itemsIndexed(shown, key = { _, index -> keys[index] }) { place, index ->
                    val key = keys[index]
                    val held = reorder.dragged == key
                    QueueRow(
                        item = song.queue[index],
                        playing = index == song.index,
                        played = playingPlace != -1 && place < playingPlace,
                        held = held,
                        onPlay = {
                            player.seekTo(index, 0L)
                            player.play()
                        },
                        onRemove = { player.removeMediaItem(index) },
                        onMove = { by -> player.moveInPlayOrder(shown, place, place + by) },
                        canMove = { by -> place + by in shown.indices },
                        handle = Modifier.pointerInput(key, index) {
                            detectVerticalDragGestures(
                                onDragStart = { reorder.start(key, index) },
                                onVerticalDrag = { change, amount ->
                                    change.consume()
                                    reorder.dragBy(amount)
                                },
                                onDragEnd = { letGo() },
                                onDragCancel = { letGo() }
                            )
                        },
                        modifier = Modifier
                            // The one held above the rest, under the finger; the others making room for it
                            .zIndex(if (held) 1f else 0f)
                            .graphicsLayer { translationY = reorder.offsetOf(key) }
                            .animateItem(
                                fadeInSpec = null,
                                placementSpec = if (held) null else spring(stiffness = Spring.StiffnessMediumLow, visibilityThreshold = IntOffset.VisibilityThreshold),
                                fadeOutSpec = null
                            )
                    )
                }
            }
        }
    }
}

/**
 * A song of the queue: its cover, title and artist, then its ✕ (the one [playing], a play sign instead) and the handle
 * it's dragged by ([handle] goes to it). [held] while it's dragged, lifted above the rest. [onMove] moves it up (-1) or
 * down (1), for TalkBack, which can't drag it; [canMove] says whether it can go that way.
 */
@Composable
private fun QueueRow(
    item: MediaItem,
    playing: Boolean,
    played: Boolean,
    held: Boolean,
    onPlay: () -> Unit,
    onRemove: () -> Unit,
    onMove: (by: Int) -> Unit,
    canMove: (by: Int) -> Boolean,
    handle: Modifier,
    modifier: Modifier = Modifier
) {
    val metadata = item.mediaMetadata
    val colors = MaterialTheme.colorScheme
    // Its title from its tags, read once the row is shown (see SongTags)
    val context = LocalContext.current
    LaunchedEffect(item.mediaId) { SongTags.request(context, item.mediaId) }
    val tags = SongTags.of(item.mediaId)
    val lift by animateDpAsState(if (held) 6.dp else 0.dp, label = "queueRowLift")
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .shadow(lift, AppShapes.Large)
            .clip(AppShapes.Large)
            .background(
                when {
                    held -> colors.surfaceContainerHighest
                    // The one playing in the theme's colour, faintly, which goes with any tint under it
                    playing -> colors.primary.copy(alpha = 0.14f)
                    else -> Color.Transparent
                }
            )
            .clickable(onClick = onPlay)
            .semantics {
                customActions = buildList {
                    if (canMove(-1)) add(CustomAccessibilityAction("Move up") { onMove(-1); true })
                    if (canMove(1)) add(CustomAccessibilityAction("Move down") { onMove(1); true })
                }
            }
            .padding(start = 8.dp, top = 6.dp, bottom = 6.dp)
    ) {
        // The songs already played, greyed out (not their handle, which still moves them)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f).graphicsLayer { alpha = if (played && !held) 0.5f else 1f }
        ) {
            MiniCover(metadata.artworkData ?: metadata.artworkUri ?: metadata.extras?.getByteArray(EXTRA_FALLBACK_ART), size = 40.dp)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    tags?.title ?: metadata.title?.toString() ?: "Audio",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (playing) FontWeight.SemiBold else null,
                    color = if (playing) colors.primary else colors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                (tags?.artist ?: metadata.artist)?.let {
                    Text(
                        it.toString(),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
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
                    Icon(Icons.Filled.PlayArrow, contentDescription = "Playing", modifier = Modifier.size(18.dp), tint = colors.primary)
                }
            }
        }
        // The handle, as tall as the cover, so it's easy to take hold of
        Box(contentAlignment = Alignment.Center, modifier = handle.size(40.dp)) {
            Icon(Icons.Filled.DragHandle, contentDescription = null, modifier = Modifier.size(20.dp), tint = colors.onSurfaceVariant)
        }
    }
}
