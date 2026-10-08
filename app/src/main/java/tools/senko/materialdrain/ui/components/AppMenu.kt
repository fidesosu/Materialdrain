package tools.senko.materialdrain.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
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
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import tools.senko.materialdrain.ui.LocalReduceMotion

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
    /** Room around the menu for its shadow, which the popup would otherwise cut off. */
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
    Spacer(Modifier.matchParentSize().onSizeChanged { anchorWidthPx = it.width })

    // The popup stays while the menu shrinks away
    val visibleState = remember { MutableTransitionState(false) }
    visibleState.targetState = expanded
    if (!visibleState.currentState && !visibleState.targetState) return

    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val duration = if (LocalReduceMotion.current) 0 else AppMenuDefaults.ANIMATION_MS
    val widest = minOf(maxWidth, configuration.screenWidthDp.dp - AppMenuDefaults.ScreenMargin * 2)
    val anchorWidth = with(density) { anchorWidthPx.toDp() }
    val narrowest = (if (matchAnchorWidth) maxOf(minWidth, anchorWidth) else minWidth).coerceAtMost(widest)
    val tallest = if (maxHeight != Dp.Unspecified) maxHeight else configuration.screenHeightDp.dp * AppMenuDefaults.MAX_HEIGHT_FRACTION

    // Where the menu ended up, so it grows out of the button's side of it
    var placement by remember { mutableStateOf(MenuPlacement(below = true, pivotX = 0f)) }
    val positionProvider = remember(side, edge, density) {
        AnchoredMenuPosition(
            side = side,
            edge = edge,
            gapPx = with(density) { AppMenuDefaults.Gap.roundToPx() },
            marginPx = with(density) { AppMenuDefaults.ScreenMargin.roundToPx() },
            framePx = with(density) { AppMenuDefaults.ShadowRoom.roundToPx() },
            onPlaced = { if (it != placement) placement = it }
        )
    }

    Popup(popupPositionProvider = positionProvider, onDismissRequest = onDismiss, properties = PopupProperties(focusable = true)) {
        val origin = TransformOrigin(placement.pivotX, if (placement.below) 0f else 1f)
        Box(modifier = Modifier.padding(AppMenuDefaults.ShadowRoom)) {
            AnimatedVisibility(
                visibleState = visibleState,
                enter = fadeIn(tween(duration / 2)) +
                    scaleIn(tween(duration, easing = FastOutSlowInEasing), initialScale = 0.85f, transformOrigin = origin),
                exit = fadeOut(tween(duration)) +
                    scaleOut(tween(duration, easing = FastOutSlowInEasing), targetScale = 0.85f, transformOrigin = origin)
            ) {
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
                            .widthIn(min = narrowest, max = widest)
                            .heightIn(max = tallest)
                            .verticalScroll(rememberScrollState())
                            .padding(AppMenuDefaults.Gap),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                        content = content
                    )
                }
            }
        }
    }
}

/** Where an [AppMenu] was placed: under its button or over it, and the button's middle across the menu (0 to 1). */
private data class MenuPlacement(val below: Boolean, val pivotX: Float)

/**
 * Places an [AppMenu] against its button's bounds: on the side and edge asked for, kept [marginPx] inside the window.
 * The popup is [framePx] bigger than the menu all round (room for its shadow), which is taken off here.
 */
private class AnchoredMenuPosition(
    private val side: MenuSide,
    private val edge: MenuEdge,
    private val gapPx: Int,
    private val marginPx: Int,
    private val framePx: Int,
    private val onPlaced: (MenuPlacement) -> Unit
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize
    ): IntOffset {
        val width = (popupContentSize.width - 2 * framePx).coerceAtLeast(0)
        val height = (popupContentSize.height - 2 * framePx).coerceAtLeast(0)
        val ltr = layoutDirection == LayoutDirection.Ltr
        val resolvedEdge = if (edge != MenuEdge.Auto) edge else {
            val inEndHalf = if (ltr) anchorBounds.center.x > windowSize.width / 2 else anchorBounds.center.x < windowSize.width / 2
            if (inEndHalf) MenuEdge.End else MenuEdge.Start
        }
        val startAligned = if (ltr) anchorBounds.left else anchorBounds.right - width
        val endAligned = if (ltr) anchorBounds.right - width else anchorBounds.left
        val x = when (resolvedEdge) {
            MenuEdge.Center -> anchorBounds.center.x - width / 2
            MenuEdge.End -> endAligned
            else -> startAligned
        }.coerceIn(marginPx, (windowSize.width - marginPx - width).coerceAtLeast(marginPx))

        val spaceBelow = windowSize.height - marginPx - (anchorBounds.bottom + gapPx)
        val spaceAbove = anchorBounds.top - gapPx - marginPx
        val below = when (side) {
            MenuSide.Below -> true
            MenuSide.Above -> false
            MenuSide.Auto -> height <= spaceBelow || spaceBelow >= spaceAbove
        }
        val y = (if (below) anchorBounds.bottom + gapPx else anchorBounds.top - gapPx - height)
            .coerceIn(marginPx, (windowSize.height - marginPx - height).coerceAtLeast(marginPx))

        onPlaced(MenuPlacement(below, ((anchorBounds.center.x - x).toFloat() / width.coerceAtLeast(1)).coerceIn(0f, 1f)))
        return IntOffset(x - framePx, y - framePx)
    }
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
 */
@Composable
fun AppMenuPages(
    showSecondary: Boolean,
    primary: @Composable ColumnScope.() -> Unit,
    secondary: @Composable ColumnScope.() -> Unit
) {
    val duration = if (LocalReduceMotion.current) 0 else AppMenuDefaults.ANIMATION_MS
    AnimatedContent(
        targetState = showSecondary,
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
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) { if (secondaryPage) secondary() else primary() }
    }
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
