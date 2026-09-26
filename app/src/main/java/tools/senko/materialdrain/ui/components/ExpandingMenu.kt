package tools.senko.materialdrain.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandIn
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import tools.senko.materialdrain.ui.LocalReduceMotion

private const val MENU_ANIMATION_MS = 220
private val MenuWidth = 220.dp
/** Taller menus scroll. Room for a row to go back and six choices, which is about 340 dp. */
private val MenuMaxHeight = 380.dp

/**
 * A menu which grows out of the button which opens it and shrinks back into it when it closes. Call it inside the
 * box which holds that button, and only that button (the menu is placed on the corner of the box that
 * [origin] names and grows from there): [Alignment.BottomEnd] makes it grow up and to the left, for a button at the
 * bottom of the screen, and [Alignment.TopEnd] makes it grow down and to the left. The other two corners work too.
 *
 * The menu is a popup, it closes itself on a tap outside it (or back) by calling [onDismiss]. Choosing something
 * doesn't close it, that is up to the caller, so the result of a choice can be seen.
 *
 * @param anchorSize the size of the button, the menu starts and ends as big as that
 * @param content the entries, see [ExpandingMenuItem] and [ExpandingMenuPages]
 */
@Composable
fun ExpandingMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    anchorSize: Dp,
    modifier: Modifier = Modifier,
    origin: Alignment = Alignment.BottomEnd,
    content: @Composable ColumnScope.() -> Unit
) {
    val duration = if (LocalReduceMotion.current) 0 else MENU_ANIMATION_MS
    val anchorPx = with(LocalDensity.current) { anchorSize.roundToPx() }

    // The popup has to stay in the composition while it shrinks away
    val visibleState = remember { MutableTransitionState(false) }
    visibleState.targetState = expanded
    if (!visibleState.currentState && !visibleState.targetState) return

    Popup(
        alignment = origin,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true)
    ) {
        AnimatedVisibility(
            visibleState = visibleState,
            enter = expandIn(
                expandFrom = origin,
                initialSize = { IntSize(anchorPx, anchorPx) },
                animationSpec = tween(duration, easing = FastOutSlowInEasing)
            ) + fadeIn(tween(duration / 2)),
            exit = shrinkOut(
                shrinkTowards = origin,
                targetSize = { IntSize(anchorPx, anchorPx) },
                animationSpec = tween(duration, easing = FastOutSlowInEasing)
            ) + fadeOut(tween(duration))
        ) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = modifier.width(MenuWidth)
            ) {
                Column(modifier = Modifier.heightIn(max = MenuMaxHeight).verticalScroll(rememberScrollState()), content = content)
            }
        }
    }
}

/**
 * The content of an [ExpandingMenu] with two levels: the [primary] entries, and the [secondary] ones which replace
 * them while [showSecondary] is true (start it with a back entry, the menu doesn't add one). The change slides the
 * content sideways while the menu grows or shrinks to the size of the entries.
 */
@Composable
fun ExpandingMenuPages(
    showSecondary: Boolean,
    primary: @Composable ColumnScope.() -> Unit,
    secondary: @Composable ColumnScope.() -> Unit
) {
    val duration = if (LocalReduceMotion.current) 0 else MENU_ANIMATION_MS
    AnimatedContent(
        targetState = showSecondary,
        transitionSpec = {
            // Going deeper moves the content to the left, coming back to the right
            val direction = if (targetState) 1 else -1
            (slideInHorizontally(tween(duration, easing = FastOutSlowInEasing)) { it * direction / 4 } +
                fadeIn(tween(duration))) togetherWith
                (slideOutHorizontally(tween(duration, easing = FastOutSlowInEasing)) { -it * direction / 4 } +
                    fadeOut(tween(duration / 2))) using
                SizeTransform(clip = true) { _, _ -> tween(duration, easing = FastOutSlowInEasing) }
        },
        label = "menu page"
    ) { secondaryPage ->
        Column { if (secondaryPage) secondary() else primary() }
    }
}

/**
 * An entry of an [ExpandingMenu]. An [active] entry (a switch which is on, the current choice) has a slightly
 * tinted background and is in the colour of the played part of the seek bar, there is no check mark.
 */
@Composable
fun ExpandingMenuItem(
    text: String,
    onClick: () -> Unit,
    active: Boolean = false,
    leadingIcon: ImageVector? = null,
    trailingText: String? = null,
    trailingIcon: ImageVector? = null
) {
    val accent = MaterialTheme.colorScheme.primary // the colour MediaSeekBar plays in
    DropdownMenuItem(
        text = { Text(text) },
        leadingIcon = leadingIcon?.let { icon -> { Icon(icon, contentDescription = null) } },
        trailingIcon = if (trailingText != null || trailingIcon != null) {
            {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    trailingText?.let { Text(it, style = MaterialTheme.typography.labelLarge) }
                    trailingIcon?.let { Icon(it, contentDescription = null) }
                }
            }
        } else null,
        colors = if (active) {
            MenuDefaults.itemColors(textColor = accent, leadingIconColor = accent, trailingIconColor = accent)
        } else {
            MenuDefaults.itemColors()
        },
        onClick = onClick,
        modifier = if (active) Modifier.background(accent.copy(alpha = 0.16f)) else Modifier
    )
}
