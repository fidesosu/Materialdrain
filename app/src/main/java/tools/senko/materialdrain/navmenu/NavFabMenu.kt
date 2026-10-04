package tools.senko.materialdrain.navmenu

import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.first
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import tools.senko.materialdrain.Screen
import tools.senko.materialdrain.ui.LocalReduceMotion
import kotlin.math.abs
import kotlin.math.roundToInt

private val FabMargin = 16.dp
private val FabClosedCorner = 16.dp
/** Moving between spots: a quick move with a mild ease-in and a lighter ease-out. */
/** How far below its corner the button starts, enough to be off the screen; it slides up from there. */
private val EntryOffset = 160.dp
private val EntryAnimation = tween<Float>(durationMillis = 420, easing = FastOutSlowInEasing)
private val MoveAnimation = tween<Float>(durationMillis = 300, easing = CubicBezierEasing(0.4f, 0f, 0.8f, 1f))
/** Minimum horizontal travel for a release to count as a swipe. */
private val SwipeThreshold = 24.dp
/** Measured from when the finger starts moving; releasing later than this cancels the swipe. */
private const val SWIPE_TIMEOUT_MILLIS = 500L
private const val GRID_COLUMNS = 4

/**
 * Navigation prototype: a FAB which sits bottom-left, -center or -right (swipe it sideways to move it),
 * opening a small bottom-anchored window of destinations above it. The FAB shows the current screen's icon,
 * and turns into a round close button while the window is open. Tapping outside the window closes it.
 *
 * Mock items (no [NavMenuItem.screen]) only get highlighted when tapped, there's nothing behind them.
 */
@Composable
fun NavFabMenu(
    menu: NavMenu,
    currentScreen: Screen,
    position: NavFabPosition,
    onPositionChange: (NavFabPosition) -> Unit,
    onNavigate: (Screen) -> Unit,
    onFabHeightChanged: (Dp) -> Unit,
    /** How far the button rises above its corner, e.g. over the selection bar or a snackbar */
    lift: Dp = 0.dp,
    /** When false the button slides down out of the screen (and is gone once it has) */
    visible: Boolean = true,
    modifier: Modifier = Modifier
) {
    var open by rememberSaveable { mutableStateOf(false) }
    var mockSelectedId by rememberSaveable { mutableStateOf<String?>(null) }
    BackHandler(enabled = open) { open = false }

    val reduceMotion = LocalReduceMotion.current
    val isSelected: (NavMenuItem) -> Boolean = { item -> item.screen?.let { it == currentScreen } ?: (item.id == mockSelectedId) }
    val allItems = menu.pinned + menu.sections.flatMap { it.items }
    val selectedItem = allItems.firstOrNull(isSelected)

    // 0 = shown, 1 = gone. Animated by its own state, so the button is still there while it slides down
    val hidden by animateFloatAsState(
        targetValue = if (visible) 0f else 1f,
        animationSpec = tween(320, easing = FastOutSlowInEasing),
        label = "navFabHidden"
    )
    LaunchedEffect(visible) { if (!visible) open = false }
    val hiddenPx = with(LocalDensity.current) { EntryOffset.toPx() }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val maxWindowHeight = maxHeight * 0.6f

        AnimatedVisibility(visible = open, enter = fadeIn(), exit = fadeOut()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.32f))
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { open = false }
            )
        }

        // The window grows out of the FAB's corner, so it reads as coming from the button
        val origin = TransformOrigin((position.bias + 1f) / 2f, 1f)
        AnimatedVisibility(
            visible = open,
            modifier = Modifier
                .align(BiasAlignment(position.bias, 1f))
                .windowInsetsPadding(WindowInsets.systemBars)
                .padding(start = 12.dp, end = 12.dp, bottom = FabMargin + 56.dp + 12.dp),
            enter = if (reduceMotion) fadeIn(tween(100)) else fadeIn() + scaleIn(initialScale = 0.85f, transformOrigin = origin),
            exit = if (reduceMotion) fadeOut(tween(100)) else fadeOut() + scaleOut(targetScale = 0.85f, transformOrigin = origin)
        ) {
            NavMenuWindow(
                menu = menu,
                maxHeight = maxWindowHeight,
                isSelected = isSelected,
                onItemClick = { item ->
                    if (item.screen != null) onNavigate(item.screen) else mockSelectedId = item.id
                    open = false
                }
            )
        }

        val fabIcon: Painter = selectedItem?.icon?.painter()
            ?: currentScreen.iconResId?.takeIf { allItems.any { it.screen != null } }?.let { painterResource(it) }
            ?: rememberVectorPainter(Icons.Filled.Menu)

        if (hidden < 1f) NavFab(
            open = open,
            icon = fabIcon,
            position = position,
            containerWidth = constraints.maxWidth,
            onClick = { open = !open },
            onPositionChange = onPositionChange,
            onFabHeightChanged = onFabHeightChanged,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .offset(y = -lift)
                .graphicsLayer { translationY = hidden * hiddenPx }
        )
    }
}

/**
 * A swipe moves the FAB exactly one spot in its direction, however far the finger travels, and only once the
 * finger is lifted; it doesn't follow the finger while dragging. The swipe has to be released within
 * [SWIPE_TIMEOUT_MILLIS] of the finger starting to *move* (not of touching down), otherwise it doesn't count.
 *
 * The FAB is laid out at the bottom-start and placed with an x offset ([offsetX]) so moving between spots
 * animates smoothly instead of jumping between alignments.
 */
@Composable
private fun NavFab(
    open: Boolean,
    icon: Painter,
    position: NavFabPosition,
    containerWidth: Int,
    onClick: () -> Unit,
    onPositionChange: (NavFabPosition) -> Unit,
    onFabHeightChanged: (Dp) -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val marginPx = with(density) { FabMargin.toPx() }
    var fabWidth by remember { mutableIntStateOf(0) }
    val offsetX = remember { Animatable(0f) }
    // Starts below the screen: until it has its saved side it's hidden, then it slides up into place
    val entryOffsetPx = with(density) { EntryOffset.toPx() }
    val entryY = remember { Animatable(entryOffsetPx) }
    var placed by remember { mutableStateOf(false) }

    val travel = (containerWidth - fabWidth - 2 * marginPx).coerceAtLeast(0f)
    fun targetFor(p: NavFabPosition) = travel * (p.bias + 1f) / 2f

    // Placement and layout changes snap to the side: a layout pass must never look like the button moving
    LaunchedEffect(travel, fabWidth) {
        if (fabWidth == 0) return@LaunchedEffect
        offsetX.snapTo(targetFor(position))
        placed = true
    }

    // Only a change of side, such as a swipe, moves the button sideways
    LaunchedEffect(position) {
        if (placed) offsetX.animateTo(targetFor(position), MoveAnimation)
    }

    // Slides up once the button is at its saved side. Keyed to nothing else, so a later layout change can't cancel it
    LaunchedEffect(Unit) {
        snapshotFlow { placed }.first { it }
        entryY.animateTo(0f, EntryAnimation)
    }

    val currentPosition by rememberUpdatedState(position)
    val currentOnPositionChange by rememberUpdatedState(onPositionChange)

    val corner by animateDpAsState(if (open) 28.dp else FabClosedCorner, label = "navFabCorner")

    FloatingActionButton(
        onClick = onClick,
        shape = RoundedCornerShape(corner),
        modifier = modifier
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(FabMargin)
            .offset { IntOffset(offsetX.value.roundToInt(), entryY.value.roundToInt()) }
            .onSizeChanged {
                fabWidth = it.width
                onFabHeightChanged(with(density) { it.height.toDp() })
            }
            .pointerInput(Unit) {
                val thresholdPx = SwipeThreshold.toPx()
                var totalDrag = 0f
                var dragStartMillis = 0L
                detectHorizontalDragGestures(
                    onDragStart = {
                        totalDrag = 0f
                        dragStartMillis = SystemClock.uptimeMillis()
                    },
                    onDragEnd = {
                        val inTime = SystemClock.uptimeMillis() - dragStartMillis <= SWIPE_TIMEOUT_MILLIS
                        if (inTime && abs(totalDrag) >= thresholdPx) {
                            val spots = NavFabPosition.entries
                            val step = if (totalDrag > 0) 1 else -1
                            val next = spots[(currentPosition.ordinal + step).coerceIn(0, spots.lastIndex)]
                            if (next != currentPosition) currentOnPositionChange(next)
                        }
                    },
                    onHorizontalDrag = { change, amount ->
                        change.consume()
                        totalDrag += amount
                    }
                )
            }
    ) {
        Crossfade(targetState = open, label = "navFabIcon") { isOpen ->
            if (isOpen) Icon(Icons.Filled.Close, contentDescription = "Close navigation")
            else Icon(icon, contentDescription = "Navigation")
        }
    }
}

@Composable
private fun NavMenuWindow(
    menu: NavMenu,
    maxHeight: Dp,
    isSelected: (NavMenuItem) -> Boolean,
    onItemClick: (NavMenuItem) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 6.dp
    ) {
        // Items only, no header: Settings is the gear at the top right of the screen.
        // Reversed: item 0 (the pinned row) is laid out at the bottom and the list starts scrolled to it
        LazyColumn(
            reverseLayout = true,
            modifier = Modifier.widthIn(max = 420.dp).fillMaxWidth().heightIn(max = maxHeight),
            contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = 12.dp, bottom = 12.dp)
        ) {
            item(key = "pinned") { TileGrid(menu.pinned, isSelected, onItemClick) }
            if (menu.sections.isNotEmpty()) {
                item(key = "divider") { HorizontalDivider(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) }
            }
            items(menu.sections.asReversed(), key = { it.title }) { section ->
                Column {
                    Text(
                        section.title.uppercase(),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 12.dp, top = 12.dp, bottom = 4.dp)
                    )
                    TileGrid(section.items, isSelected, onItemClick)
                }
            }
        }
    }
}

@Composable
private fun TileGrid(items: List<NavMenuItem>, isSelected: (NavMenuItem) -> Boolean, onItemClick: (NavMenuItem) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        items.chunked(GRID_COLUMNS).forEach { row ->
            Row(modifier = Modifier.fillMaxWidth()) {
                row.forEach { item ->
                    Tile(item, selected = isSelected(item), onClick = { onItemClick(item) }, modifier = Modifier.weight(1f))
                }
                repeat(GRID_COLUMNS - row.size) { Spacer(modifier = Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun Tile(item: NavMenuItem, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 4.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(if (selected) colors.primaryContainer else colors.surfaceVariant)
        ) {
            Icon(
                item.icon.painter(),
                contentDescription = null,
                tint = if (selected) colors.onPrimaryContainer else colors.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            item.label,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = if (selected) colors.primary else colors.onSurface
        )
    }
}
