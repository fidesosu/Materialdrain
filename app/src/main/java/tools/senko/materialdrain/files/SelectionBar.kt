package tools.senko.materialdrain.files

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import tools.senko.materialdrain.ui.icons.filled.Close
import tools.senko.materialdrain.ui.icons.filled.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import tools.senko.materialdrain.ui.LocalBottomInset
import tools.senko.materialdrain.ui.LocalReduceMotion
import tools.senko.materialdrain.ui.components.AppMenu
import tools.senko.materialdrain.ui.components.AppMenuItem
import tools.senko.materialdrain.ui.components.AppMenuDivider
import tools.senko.materialdrain.ui.components.AppMenuPages
import tools.senko.materialdrain.ui.icons.automirrored.filled.KeyboardArrowLeft
import tools.senko.materialdrain.ui.icons.automirrored.filled.KeyboardArrowRight
import tools.senko.materialdrain.ui.icons.filled.FolderZip
import tools.senko.materialdrain.ui.preview.PreviewTheme
import tools.senko.materialdrain.ui.preview.ThemePreviews
import tools.senko.materialdrain.ui.icons.filled.Download
import tools.senko.materialdrain.ui.icons.filled.Share
import tools.senko.materialdrain.ui.icons.filled.Delete
import tools.senko.materialdrain.ui.icons.filled.SelectAll
import tools.senko.materialdrain.ui.icons.automirrored.filled.PlaylistAdd
import androidx.compose.ui.res.stringResource
import tools.senko.materialdrain.R

/**
 * One action on the selected items. [destructive] actions are tinted red. With [choices] (Download: each file on its
 * own, or all in one zip), it's a menu of those instead: its button opens them, in the overflow menu a page of them.
 */
data class SelectionAction(
    val label: String,
    val icon: ImageVector,
    val onClick: () -> Unit,
    val enabled: Boolean = true,
    val destructive: Boolean = false,
    val choices: List<SelectionAction> = emptyList(),
)

/** How tall the bar itself is: the snackbar's and the screens' buttons' height (see AppSnackbarHost). */
private val BarCardHeight = 56.dp

/** The space between the bar and the screen's sides and bottom, the snackbar's. */
private val BarMargin = 8.dp

/** The bar's corner radius, the snackbar's. */
private val BarCorner = 16.dp

/**
 * The room the selection bar takes at the bottom: the bar and the margin under it, so what sits above it (the FAB, the
 * snackbars) can move up by the same amount.
 */
val SelectionBarHeight = BarCardHeight + BarMargin

/**
 * The room under the page (LocalBottomInset) that a bar at the bottom of it ([visible]) leaves under its buttons, as it
 * was while the bar was shown. When the bar goes, the miniplayer it kept away comes back, and the room under the page
 * grows by the miniplayer's height: the leaving bar would have jumped up with it. Held, the bar slides away as it was.
 */
@Composable
internal fun rememberBarInset(visible: Boolean): Dp {
    val inset = LocalBottomInset.current
    var held by remember { mutableStateOf(inset) }
    if (visible && held != inset) SideEffect { held = inset }
    return if (visible) inset else held
}

/**
 * A bar floating at the bottom of the page while [visible], in the snackbar's look (see AppSnackbarHost): a card with
 * rounded corners and its colours, kept off the sides and above the system's navigation bar (or the miniplayer). The
 * selection's bar, and the bar of files shared to a Filesystem tab (see SharedUploadBar).
 */
@Composable
internal fun FloatingBottomBar(
    visible: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit
) {
    val reduceMotion = LocalReduceMotion.current
    val bottomInset = rememberBarInset(visible)
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = if (reduceMotion) fadeIn(animationSpec = tween(100)) else slideInVertically(animationSpec = tween(200)) { it } + fadeIn(animationSpec = tween(200)),
        exit = if (reduceMotion) fadeOut(animationSpec = tween(100)) else slideOutVertically(animationSpec = tween(200)) { it } + fadeOut(animationSpec = tween(150))
    ) {
        Surface(
            shape = RoundedCornerShape(BarCorner),
            // The snackbar's colours, so the two look like one kind of thing
            color = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurface,
            shadowElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = BarMargin, end = BarMargin, bottom = BarMargin + bottomInset)
                .height(BarCardHeight)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                content = content
            )
        }
    }
}

/** How many actions are shown as icons; the rest go into the overflow menu. */
private const val ICON_ACTIONS = 3

/**
 * The bar at the bottom of the screen while items are selected, a floating card like the snackbar (see
 * [FloatingBottomBar]). The first actions are icons, the others are in the overflow menu.
 */
@Composable
fun SelectionActionBar(
    visible: Boolean,
    selectedCount: Int,
    onClose: () -> Unit,
    actions: List<SelectionAction>,
) {
    FloatingBottomBar(visible = visible) {
        IconButton(onClick = onClose) { Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.files_stop_selecting)) }
        Text(
            text = if (selectedCount == 0) stringResource(R.string.files_select_items) else "$selectedCount selected",
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(horizontal = 4.dp)
        )
        actions.take(ICON_ACTIONS).forEach { action ->
            ActionIcon(action)
        }
        val overflow = actions.drop(ICON_ACTIONS)
        if (overflow.isNotEmpty()) {
            OverflowMenu(overflow)
        }
    }
}

@Composable
private fun ActionIcon(action: SelectionAction) {
    var expanded by remember { mutableStateOf(false) }
    // Its own box, which the menu of its choices opens from
    Box {
        IconButton(onClick = { if (action.choices.isEmpty()) action.onClick() else expanded = true }, enabled = action.enabled) {
            Icon(
                imageVector = action.icon,
                contentDescription = action.label,
                tint = when {
                    !action.enabled -> LocalContentColor.current.copy(alpha = 0.38f)
                    action.destructive -> MaterialTheme.colorScheme.error
                    else -> LocalContentColor.current
                }
            )
        }
        if (action.choices.isNotEmpty()) {
            AppMenu(expanded = expanded, onDismiss = { expanded = false }) {
                action.choices.forEach { choice -> ChoiceEntry(choice, close = { expanded = false }) }
            }
        }
    }
}

/** An action as an entry of a menu, which closes it ([close]) before it acts. */
@Composable
private fun ChoiceEntry(action: SelectionAction, close: () -> Unit, trailing: ImageVector? = null, onClick: (() -> Unit)? = null) {
    AppMenuItem(
        text = action.label,
        leadingIcon = action.icon,
        trailingIcon = trailing,
        enabled = action.enabled,
        destructive = action.destructive,
        onClick = onClick ?: {
            close()
            action.onClick()
        }
    )
}

@Composable
private fun OverflowMenu(actions: List<SelectionAction>) {
    var expanded by remember { mutableStateOf(false) }
    val close = { expanded = false }
    // The menu is anchored to its own box, so it opens from the button and not from the edge of the bar
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.files_more_actions_for_the_selection))
        }
        AppMenu(expanded = expanded, onDismiss = close) {
            // The action whose choices are shown, on the second page; remembered in the menu's popup, so each opening
            // starts on the first
            var opened by remember { mutableStateOf<SelectionAction?>(null) }
            var shown by remember { mutableStateOf<SelectionAction?>(null) }
            AppMenuPages(
                showSecondary = opened != null,
                primary = {
                    actions.forEach { action ->
                        if (action.choices.isEmpty()) {
                            ChoiceEntry(action, close)
                        } else {
                            ChoiceEntry(action, close, trailing = Icons.AutoMirrored.Filled.KeyboardArrowRight, onClick = {
                                shown = action
                                opened = action
                            })
                        }
                    }
                },
                secondary = {
                    // Kept while going back, so it slides out as it was
                    shown?.let { action ->
                        AppMenuItem(action.label, leadingIcon = Icons.AutoMirrored.Filled.KeyboardArrowLeft, onClick = { opened = null })
                        AppMenuDivider()
                        action.choices.forEach { ChoiceEntry(it, close) }
                    }
                },
                otherPages = {
                    actions.filter { it.choices.isNotEmpty() && it != shown }.forEach { action -> action.choices.forEach { ChoiceEntry(it, close) } }
                }
            )
        }
    }
}

// ---- Previews ----

/** Three files selected: the first actions as icons, the rest in its ⋮ menu (Delete among them, in red). */
@ThemePreviews
@Composable
private fun SelectionActionBarPreview() {
    PreviewTheme {
        SelectionActionBar(
            visible = true,
            selectedCount = 3,
            onClose = {},
            actions = listOf(
                SelectionAction(
                    "Download", Icons.Filled.Download, {},
                    choices = listOf(SelectionAction("Separately", Icons.Filled.Download, {}), SelectionAction("As one ZIP", Icons.Filled.FolderZip, {}))
                ),
                SelectionAction("Share", Icons.Filled.Share, {}, enabled = false),
                SelectionAction("Delete", Icons.Filled.Delete, {}, destructive = true),
                SelectionAction("Add to list", Icons.AutoMirrored.Filled.PlaylistAdd, {}),
                SelectionAction("Select all", Icons.Filled.SelectAll, {})
            )
        )
    }
}
