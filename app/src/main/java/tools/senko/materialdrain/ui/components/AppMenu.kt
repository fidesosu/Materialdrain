package tools.senko.materialdrain.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import tools.senko.materialdrain.ui.icons.filled.MoreVert
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.layout.IntrinsicMeasurable
import androidx.compose.ui.layout.IntrinsicMeasureScope
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.layout.MeasurePolicy
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.layout.onGloballyPositioned
import android.view.View
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.platform.LocalView
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.round
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import tools.senko.materialdrain.ui.LocalReduceMotion
import tools.senko.materialdrain.ui.icons.automirrored.filled.DriveFileMove
import tools.senko.materialdrain.ui.icons.automirrored.filled.KeyboardArrowLeft
import tools.senko.materialdrain.ui.icons.automirrored.filled.KeyboardArrowRight
import tools.senko.materialdrain.ui.icons.filled.Check
import tools.senko.materialdrain.ui.icons.filled.Delete
import tools.senko.materialdrain.ui.icons.filled.Edit
import tools.senko.materialdrain.ui.icons.filled.Share
import tools.senko.materialdrain.ui.icons.filled.Tune
import tools.senko.materialdrain.ui.preview.PreviewTheme
import tools.senko.materialdrain.ui.preview.ThemePreviews

// The app's one menu: every menu (the ⋮ menus of files, Sort by, New, the host switcher, the players' settings, the
// choices of the config editor) is an AppMenu with AppMenuItems, so they look and behave alike, and a change made here
// reaches all of them. What differs between them is a parameter, not a copy.

/** The measures of every [AppMenu]; change one here and every menu follows. */
object AppMenuDefaults {
    /** Between the menu and its button, and around the entries inside it. */
    val Gap = 6.dp
    /** The closest a menu comes to the edge of the screen. */
    val ScreenMargin = 8.dp
    /** The narrowest a menu is, however short its entries; see [AppMenu]'s minWidth. */
    val MinWidth = 168.dp
    /** The widest a menu grows for long entries (less on a narrow screen); see [AppMenu]'s maxWidth. */
    val MaxWidth = 400.dp
    /** How much of the screen's height a menu takes at most, before it scrolls. */
    const val MAX_HEIGHT_FRACTION = 0.6f
    /** An entry's height, at least: one line. An entry with a second line grows. */
    val ItemHeight = 44.dp
    /** An entry's highlight: the menu's corners less [Gap], so the two curves run alongside. */
    val ItemShape = RoundedCornerShape(14.dp)
    /** Room around a menu drawn in place (see [AppMenuPreview]) for its shadow. */
    val ShadowRoom = 8.dp
    const val ANIMATION_MS = 200
}

/** Which side of its button a menu opens on. [Auto]: below, unless it only fits above. */
enum class MenuSide { Below, Above, Auto }

/** Which edge of its button a menu lines up with. [Auto]: the start edge, or the end one for a button in the end half of the screen. */
enum class MenuEdge { Start, Center, End, Auto }

/**
 * A menu for the button (or field, or card) in whose box it's called: the box should hold only that, as the menu takes
 * its size and place from it. It opens on [side] of the box, lined up with its [edge], kept on the screen, and grows out
 * of the box as it opens.
 *
 * It's as wide as its widest entry, between [minWidth] (or the box's own width, with [matchAnchorWidth], for a menu
 * under a wide card) and [maxWidth] (and never wider than the screen); taller than [maxHeight] (by default most of the
 * screen) it scrolls. Long entries end in an ellipsis rather than making it wider than that.
 *
 * It closes on a tap outside it, or back, by calling [onDismiss]; choosing an entry doesn't close it, that's up to the
 * entry (so a choice's effect can be seen, as with a sort order). See [OverflowMenuButton] for the usual ⋮ menu.
 *
 * @param content the entries: [AppMenuItem], [AppMenuDivider], [AppMenuPages], or anything else
 */
@Composable
fun BoxScope.AppMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    side: MenuSide = MenuSide.Auto,
    edge: MenuEdge = MenuEdge.Auto,
    matchAnchorWidth: Boolean = false,
    minWidth: Dp = AppMenuDefaults.MinWidth,
    maxWidth: Dp = AppMenuDefaults.MaxWidth,
    maxHeight: Dp = Dp.Unspecified,
    content: @Composable ColumnScope.() -> Unit
) {
    // As big as the box the menu is called in, which is its button's: how wide the button is
    var anchorWidthPx by remember { mutableIntStateOf(0) }
    // Where it is, to find the part of the window the menu may take (see visibleArea); not state, as it changes with every
    // scroll of a list of buttons with menus, and is only read while one is open
    val anchorCoordinates = remember { AnchorCoordinates() }
    Spacer(Modifier.matchParentSize().onSizeChanged { anchorWidthPx = it.width }.onPlaced { anchorCoordinates.coordinates = it })
    val hostView = LocalView.current
    val menuFloor = LocalMenuFloor.current

    // The popup stays while the menu shrinks away; everything remembered after this is new for each opening
    var present by remember { mutableStateOf(false) }
    if (!expanded && !present) return

    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val duration = if (LocalReduceMotion.current) 0 else AppMenuDefaults.ANIMATION_MS
    val widest = minOf(maxWidth, configuration.screenWidthDp.dp - AppMenuDefaults.ScreenMargin * 2)
    val anchorWidth = with(density) { anchorWidthPx.toDp() }
    val narrowest = (if (matchAnchorWidth) maxOf(minWidth, anchorWidth) else minWidth).coerceAtMost(widest)
    val tallest = if (maxHeight != Dp.Unspecified) maxHeight else configuration.screenHeightDp.dp * AppMenuDefaults.MAX_HEIGHT_FRACTION

    // The popup covers the whole window and places the menu in it itself, instead of being as big as the menu and placed
    // by the window manager: a popup window resized to a menu growing or shrinking (between the pages of AppMenuPages) is
    // moved a frame after it's resized, so the menu's far edge jumped first and then the whole menu. Where the button and
    // the window are is only known once the popup is placed, a frame after it's first laid out
    var anchor by remember { mutableStateOf<MenuAnchor?>(null) }
    val positionProvider = remember { WindowCoveringPosition { if (it != anchor) anchor = it } }
    // Where the menu ended up, so it grows out of the button's side of it. Until it's placed the menu isn't shown, and only
    // then it starts growing. Grown from a guess, it would start out of the wrong corner and visibly move to the right one
    var placement by remember { mutableStateOf<MenuPlacement?>(null) }
    val layoutState = remember { MenuLayoutState() }
    val gapPx = with(density) { AppMenuDefaults.Gap.roundToPx() }
    val marginPx = with(density) { AppMenuDefaults.ScreenMargin.roundToPx() }
    // 0 = gone, 1 = open
    val progress = remember { Animatable(0f) }
    val placed = placement != null
    LaunchedEffect(expanded, placed) {
        if (expanded) {
            present = true
            if (placed) progress.animateTo(1f, tween(duration, easing = FastOutSlowInEasing))
        } else {
            progress.animateTo(0f, tween(duration, easing = FastOutSlowInEasing))
            present = false
        }
    }

    Popup(popupPositionProvider = positionProvider, onDismissRequest = onDismiss, properties = PopupProperties(focusable = true, clippingEnabled = false)) {
        val currentAnchor = anchor
        Layout(
            content = {
                AppMenuPanel(
                    minWidth = narrowest,
                    maxWidth = widest,
                    maxHeight = tallest,
                    modifier = modifier.graphicsLayer {
                        val shown = placement
                        val p = progress.value
                        // Fades in during the first half of opening, out during the whole of closing
                        alpha = if (shown == null) 0f else if (expanded) (p * 2f).coerceAtMost(1f) else p
                        val scale = 0.85f + 0.15f * p
                        scaleX = scale
                        scaleY = scale
                        if (shown != null) transformOrigin = TransformOrigin(shown.pivotX, if (shown.below) 0f else 1f)
                    },
                    content = content
                )
            },
            // A press anywhere but on the menu closes it, as one outside a popup of the menu's own size did
            modifier = Modifier.pointerInput(onDismiss) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    if (!layoutState.bounds.contains(down.position.round())) onDismiss()
                }
            }
        ) { measurables, constraints ->
            val windowWidth = (currentAnchor?.windowSize?.width ?: constraints.maxWidth).coerceAtMost(constraints.maxWidth)
            val windowHeight = (currentAnchor?.windowSize?.height ?: constraints.maxHeight).coerceAtMost(constraints.maxHeight)
            val menu = measurables.single().measure(Constraints(maxWidth = windowWidth, maxHeight = windowHeight))
            layout(windowWidth, windowHeight) {
                if (currentAnchor == null) return@layout
                val position = placeMenu(
                    anchorBounds = currentAnchor.bounds,
                    area = visibleArea(hostView, anchorCoordinates.coordinates, currentAnchor.bounds, menuFloor),
                    width = menu.width,
                    height = menu.height,
                    layoutDirection = layoutDirection,
                    side = side,
                    edge = edge,
                    gapPx = gapPx,
                    marginPx = marginPx,
                    state = layoutState
                )
                if (position.placement != placement) placement = position.placement
                menu.place(position.offset)
            }
        }
    }
}

/** What an [AppMenu] looks like: its card, and its entries in it, scrolled when they're taller than [maxHeight]. */
@Composable
private fun AppMenuPanel(minWidth: Dp, maxWidth: Dp, maxHeight: Dp, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        shape = FloatingCardShape,
        // Opaque, unlike a floating card button: a menu over a list would be hard to read with it showing through
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = floatingCardBorder(),
        shadowElevation = 6.dp,
        contentColor = MaterialTheme.colorScheme.onSurface,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .width(IntrinsicSize.Max)
                .widthIn(min = minWidth, max = maxWidth)
                .heightIn(max = maxHeight)
                .verticalScroll(rememberScrollState())
                .padding(AppMenuDefaults.Gap),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            content = content
        )
    }
}

/**
 * An open [AppMenu] where it's laid out, not in a popup of its own: for the previews of the menus (the preview doesn't
 * draw popups until it's interactive). Not for the app itself.
 */
@Composable
fun AppMenuPreview(content: @Composable ColumnScope.() -> Unit) {
    Box(modifier = Modifier.padding(AppMenuDefaults.ShadowRoom)) {
        AppMenuPanel(minWidth = AppMenuDefaults.MinWidth, maxWidth = AppMenuDefaults.MaxWidth, maxHeight = 600.dp, content = content)
    }
}

/** Where an [AppMenu] was placed: under its button or over it, and the button's middle across the menu (0 to 1). */
private data class MenuPlacement(val below: Boolean, val pivotX: Float)

/** The button an [AppMenu] belongs to, and the window it's in, in the window's coordinates. */
private data class MenuAnchor(val bounds: IntRect, val windowSize: IntSize)

/**
 * What an [AppMenu] keeps between its layouts while it's open: the side it opened on, which it stays on (a page of
 * [AppMenuPages] which would fit below the button, where the other didn't, still grows and shrinks over it), and where
 * it is, to tell a press outside it.
 */
private class MenuLayoutState {
    var below: Boolean? = null
    var bounds: IntRect = IntRect.Zero
}

/** Where the button of an [AppMenu] is laid out, kept by its own layout. */
private class AnchorCoordinates {
    var coordinates: LayoutCoordinates? = null
}

/**
 * The part of the screen an [AppMenu] may take, around its button at [anchorBounds]: the window less the system bars,
 * the keyboard and the app's navigation ([floor]), in the popup's coordinates, which are the button's. Those are the window's, or the screen's for a
 * menu in another popup; which one is told by where the button is on the screen ([anchor]).
 */
private fun visibleArea(hostView: View, anchor: LayoutCoordinates?, anchorBounds: IntRect, floor: MenuFloor): IntRect {
    val frame = android.graphics.Rect().also { hostView.getWindowVisibleDisplayFrame(it) }
    val onScreen = anchor?.takeIf { it.isAttached }?.positionOnScreen()?.round() ?: return IntRect(frame.left, frame.top, frame.right, frame.bottom)
    val shiftX = onScreen.x - anchorBounds.left
    val shiftY = onScreen.y - anchorBounds.top
    // Above the app's navigation, for a button above it (a menu of the navigation itself may still open over it)
    val bottom = floor.top?.takeIf { onScreen.y + anchorBounds.height <= it }?.let { minOf(frame.bottom, it.toInt()) } ?: frame.bottom
    return IntRect(frame.left - shiftX, frame.top - shiftY, frame.right - shiftX, bottom - shiftY)
}

/**
 * The top of the app's navigation at the bottom of the screen, which menus open above rather than over: the bottom bar,
 * or the button of the navigation prototype. Each tells it with [keepsMenusAbove]; the highest one counts.
 */
class MenuFloor {
    private val tops = mutableMapOf<Any, Float>()

    /** On the screen, in pixels; null while there's no navigation at the bottom. */
    val top: Float? get() = tops.values.minOrNull()

    fun set(key: Any, top: Float) { tops[key] = top }

    fun remove(key: Any) { tops.remove(key) }
}

/** Where the app keeps its [MenuFloor] (see App.kt); a menu outside it (a preview) has none. */
val LocalMenuFloor = staticCompositionLocalOf { MenuFloor() }

/** Makes this the navigation at the bottom of the screen, which menus open above (see [MenuFloor]) while it's there. */
@Composable
fun Modifier.keepsMenusAbove(): Modifier {
    val floor = LocalMenuFloor.current
    val key = remember { Any() }
    DisposableEffect(floor) { onDispose { floor.remove(key) } }
    return onGloballyPositioned { floor.set(key, it.positionOnScreen().y) }
}

/**
 * Puts the popup of an [AppMenu] over the whole window, and tells where the button and the window are. The popup isn't
 * kept inside the visible part of the window (clippingEnabled = false): kept in, it would start below the status bar,
 * where the button's coordinates don't, and the menu would be drawn that much lower. [placeMenu] keeps the menu in it.
 */
private class WindowCoveringPosition(private val onAnchor: (MenuAnchor) -> Unit) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize
    ): IntOffset {
        onAnchor(MenuAnchor(anchorBounds, windowSize))
        return IntOffset.Zero
    }
}

/** Where [placeMenu] put an [AppMenu]: its top left corner in the window, and its [MenuPlacement]. */
private class MenuPosition(val offset: IntOffset, val placement: MenuPlacement)

/**
 * Places an [AppMenu] of [width] by [height] against its button's bounds: on the side and edge asked for, kept [marginPx]
 * inside the visible [area]. The side is chosen once, at the menu's first size, and kept in [state].
 */
private fun placeMenu(
    anchorBounds: IntRect,
    area: IntRect,
    width: Int,
    height: Int,
    layoutDirection: LayoutDirection,
    side: MenuSide,
    edge: MenuEdge,
    gapPx: Int,
    marginPx: Int,
    state: MenuLayoutState
): MenuPosition {
    val ltr = layoutDirection == LayoutDirection.Ltr
    val resolvedEdge = if (edge != MenuEdge.Auto) edge else {
        val inEndHalf = if (ltr) anchorBounds.center.x > area.center.x else anchorBounds.center.x < area.center.x
        if (inEndHalf) MenuEdge.End else MenuEdge.Start
    }
    val startAligned = if (ltr) anchorBounds.left else anchorBounds.right - width
    val endAligned = if (ltr) anchorBounds.right - width else anchorBounds.left
    val x = when (resolvedEdge) {
        MenuEdge.Center -> anchorBounds.center.x - width / 2
        MenuEdge.End -> endAligned
        else -> startAligned
    }.coerceIn(area.left + marginPx, (area.right - marginPx - width).coerceAtLeast(area.left + marginPx))

    val below = state.below ?: when (side) {
        MenuSide.Below -> true
        MenuSide.Above -> false
        MenuSide.Auto -> {
            val spaceBelow = area.bottom - marginPx - (anchorBounds.bottom + gapPx)
            val spaceAbove = anchorBounds.top - gapPx - (area.top + marginPx)
            height <= spaceBelow || spaceBelow >= spaceAbove
        }
    }.also { state.below = it }
    val y = (if (below) anchorBounds.bottom + gapPx else anchorBounds.top - gapPx - height)
        .coerceIn(area.top + marginPx, (area.bottom - marginPx - height).coerceAtLeast(area.top + marginPx))

    state.bounds = IntRect(x, y, x + width, y + height)
    val pivotX = ((anchorBounds.center.x - x).toFloat() / width.coerceAtLeast(1)).coerceIn(0f, 1f)
    return MenuPosition(IntOffset(x, y), MenuPlacement(below, pivotX))
}

/**
 * An entry of an [AppMenu]: an optional icon, its [text] (and [supportingText] under it), and an optional [trailingText]
 * and [trailingIcon] at the end. An [active] entry (the current choice, a switch that's on) is in the accent colour on a
 * tinted highlight; a [destructive] one (delete) is in the error colour.
 */
@Composable
fun AppMenuItem(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    trailingIcon: ImageVector? = null,
    trailingIconDescription: String? = null,
    trailingText: String? = null,
    supportingText: String? = null,
    active: Boolean = false,
    destructive: Boolean = false,
    enabled: Boolean = true
) {
    val scheme = MaterialTheme.colorScheme
    val color = when {
        destructive -> scheme.error
        active -> scheme.primary
        else -> scheme.onSurface
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = AppMenuDefaults.ItemHeight)
            .clip(AppMenuDefaults.ItemShape)
            .background(if (active) scheme.primary.copy(alpha = 0.16f) else Color.Transparent)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .graphicsLayer { alpha = if (enabled) 1f else 0.38f }
    ) {
        leadingIcon?.let {
            Icon(it, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(text, style = MaterialTheme.typography.labelLarge, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
            supportingText?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        trailingText?.let {
            Spacer(Modifier.width(12.dp))
            Text(it, style = MaterialTheme.typography.labelLarge, color = if (active) color else scheme.onSurfaceVariant, maxLines = 1)
        }
        trailingIcon?.let {
            Spacer(Modifier.width(if (trailingText != null) 4.dp else 12.dp))
            Icon(it, contentDescription = trailingIconDescription, tint = color, modifier = Modifier.size(18.dp))
        }
    }
}

/** A line between groups of entries of an [AppMenu], e.g. before a destructive one. */
@Composable
fun AppMenuDivider() {
    HorizontalDivider(
        thickness = 0.5.dp,
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
    )
}

/**
 * The content of an [AppMenu] with two levels: the [primary] entries, and the [secondary] ones which replace them while
 * [showSecondary] is true (start it with an entry going back, it isn't added). The change slides the entries sideways
 * while the menu grows or shrinks to them.
 *
 * Both levels are as wide as the wider of the two, from the start: only the menu's height changes between them. A menu
 * whose second level can be one of several pages gives the ones not shown as [otherPages], so it's as wide as those too
 * and doesn't change width when another is opened. The
 * menu takes its width from its widest entry before laying any out (IntrinsicSize.Max, see AppMenuPanel), which a level
 * sliding in can't follow while it animates; asked then, the menu jumped to the new width at once.
 */
@Composable
fun AppMenuPages(
    showSecondary: Boolean,
    primary: @Composable ColumnScope.() -> Unit,
    secondary: @Composable ColumnScope.() -> Unit,
    otherPages: @Composable ColumnScope.() -> Unit = {}
) {
    val duration = if (LocalReduceMotion.current) 0 else AppMenuDefaults.ANIMATION_MS
    Box {
        // Both levels' entries, only asked how wide they are: never placed, so never drawn nor tapped, and kept from
        // screen readers, which would read them twice
        Layout(
            modifier = Modifier.clearAndSetSemantics {},
            content = {
                Column { primary() }
                Column { secondary() }
                Column { otherPages() }
            },
            measurePolicy = WidthOnly
        )
        AnimatedContent(
            targetState = showSecondary,
            modifier = Modifier.fillMaxWidth(),
            transitionSpec = {
                // Going deeper moves the entries to the left, coming back to the right
                val direction = if (targetState) 1 else -1
                (slideInHorizontally(tween(duration, easing = FastOutSlowInEasing)) { it * direction / 4 } +
                    fadeIn(tween(duration))) togetherWith
                    (slideOutHorizontally(tween(duration, easing = FastOutSlowInEasing)) { -it * direction / 4 } +
                        fadeOut(tween(duration / 2))) using
                    SizeTransform(clip = true) { _, _ -> tween(duration, easing = FastOutSlowInEasing) }
            },
            label = "menuPage"
        ) { secondaryPage ->
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) { if (secondaryPage) secondary() else primary() }
        }
    }
}

/**
 * Lays out nothing (no size, nothing placed) but answers how wide its widest child is: what [AppMenuPages] makes the menu
 * as wide as, with both its levels.
 */
private object WidthOnly : MeasurePolicy {
    override fun MeasureScope.measure(measurables: List<Measurable>, constraints: Constraints): MeasureResult = layout(0, 0) {}

    override fun IntrinsicMeasureScope.maxIntrinsicWidth(measurables: List<IntrinsicMeasurable>, height: Int): Int =
        measurables.maxOfOrNull { it.maxIntrinsicWidth(height) } ?: 0

    override fun IntrinsicMeasureScope.minIntrinsicWidth(measurables: List<IntrinsicMeasurable>, height: Int): Int =
        measurables.maxOfOrNull { it.minIntrinsicWidth(height) } ?: 0

    override fun IntrinsicMeasureScope.maxIntrinsicHeight(measurables: List<IntrinsicMeasurable>, width: Int): Int = 0

    override fun IntrinsicMeasureScope.minIntrinsicHeight(measurables: List<IntrinsicMeasurable>, width: Int): Int = 0
}

/**
 * The usual ⋮ button with its [AppMenu]. [content] gets `close`, for an entry to close the menu before it acts:
 * `AppMenuItem("Rename", onClick = { close(); onRename() })`.
 */
@Composable
fun OverflowMenuButton(
    contentDescription: String,
    modifier: Modifier = Modifier,
    buttonSize: Dp = 40.dp,
    content: @Composable ColumnScope.(close: () -> Unit) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val close = { expanded = false }
    Box(modifier = modifier) {
        IconButton(onClick = { expanded = true }, modifier = Modifier.size(buttonSize)) {
            Icon(Icons.Filled.MoreVert, contentDescription = contentDescription)
        }
        AppMenu(expanded = expanded, onDismiss = close) { content(close) }
    }
}

// ---- Previews ----

/** Every kind of entry: with an icon, a second line, a value at its end, the current choice, disabled, destructive. */
@ThemePreviews
@Composable
private fun AppMenuPreviewEntries() {
    PreviewTheme(padding = 8.dp) {
        AppMenuPreview {
            AppMenuItem("Rename", onClick = {}, leadingIcon = Icons.Filled.Edit)
            AppMenuItem("Move", onClick = {}, leadingIcon = Icons.AutoMirrored.Filled.DriveFileMove, supportingText = "To another folder")
            AppMenuItem("Sort by", onClick = {}, leadingIcon = Icons.Filled.Tune, trailingText = "Name", trailingIcon = Icons.AutoMirrored.Filled.KeyboardArrowRight)
            AppMenuItem("Newest first", onClick = {}, trailingIcon = Icons.Filled.Check, active = true)
            AppMenuItem("Share", onClick = {}, leadingIcon = Icons.Filled.Share, enabled = false)
            AppMenuDivider()
            AppMenuItem("Delete", onClick = {}, leadingIcon = Icons.Filled.Delete, destructive = true)
        }
    }
}

/** The second level of a menu with two (see AppMenuPages): its entries start with one going back. */
@ThemePreviews
@Composable
private fun AppMenuPagesPreview() {
    PreviewTheme(padding = 8.dp) {
        AppMenuPreview {
            AppMenuPages(
                showSecondary = true,
                primary = { AppMenuItem("Sort by", onClick = {}) },
                secondary = {
                    AppMenuItem("Sort by", onClick = {}, leadingIcon = Icons.AutoMirrored.Filled.KeyboardArrowLeft)
                    AppMenuDivider()
                    AppMenuItem("Name", onClick = {}, active = true, trailingIcon = Icons.Filled.Check)
                    AppMenuItem("Size", onClick = {})
                    AppMenuItem("Date", onClick = {})
                }
            )
        }
    }
}

/** The ⋮ button; in the interactive preview a tap opens its menu. */
@ThemePreviews
@Composable
private fun OverflowMenuButtonPreview() {
    PreviewTheme(padding = 8.dp) {
        OverflowMenuButton(contentDescription = "More") { close ->
            AppMenuItem("Rename", onClick = close, leadingIcon = Icons.Filled.Edit)
            AppMenuItem("Delete", onClick = close, leadingIcon = Icons.Filled.Delete, destructive = true)
        }
    }
}
