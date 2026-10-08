package tools.senko.materialdrain.files

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import tools.senko.materialdrain.ui.LocalBottomInset
import tools.senko.materialdrain.ui.LocalReduceMotion
import tools.senko.materialdrain.ui.components.AppMenu
import tools.senko.materialdrain.ui.components.AppMenuItem

/** One action on the selected items. [destructive] actions are tinted red. */
data class SelectionAction(
    val label: String,
    val icon: ImageVector,
    val onClick: () -> Unit,
    val enabled: Boolean = true,
    val destructive: Boolean = false,
)

/** The height of the selection bar, so what sits above it (the FAB, the snackbars) can move up by the same amount. */
val SelectionBarHeight = 72.dp

/** How many actions are shown as icons; the rest go into the overflow menu. */
private const val ICON_ACTIONS = 3

/**
 * The bar at the bottom of the screen while items are selected. The first actions are icons, the others are in the
 * overflow menu. The bar is the selection's own, so it has its own colour, which sets it apart from the list.
 */
@Composable
fun SelectionActionBar(
    visible: Boolean,
    selectedCount: Int,
    onClose: () -> Unit,
    actions: List<SelectionAction>,
) {
    val reduceMotion = LocalReduceMotion.current
    AnimatedVisibility(
        visible = visible,
        enter = if (reduceMotion) fadeIn(animationSpec = tween(100)) else slideInVertically(animationSpec = tween(200)) { it } + fadeIn(animationSpec = tween(200)),
        exit = if (reduceMotion) fadeOut(animationSpec = tween(100)) else slideOutVertically(animationSpec = tween(200)) { it } + fadeOut(animationSpec = tween(150))
    ) {
        Surface(
            color = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            tonalElevation = 6.dp,
            shadowElevation = 8.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    // Its colour goes on under the system's navigation bar, its buttons stay above it
                    .padding(bottom = LocalBottomInset.current)
                    .height(SelectionBarHeight)
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onClose) { Icon(Icons.Filled.Close, contentDescription = "Stop selecting") }
                Text(
                    text = if (selectedCount == 0) "Select items" else "$selectedCount selected",
                    style = MaterialTheme.typography.titleMedium,
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
    }
}

@Composable
private fun ActionIcon(action: SelectionAction) {
    IconButton(onClick = action.onClick, enabled = action.enabled) {
        Icon(
            imageVector = action.icon,
            contentDescription = action.label,
            tint = when {
                !action.enabled -> MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.38f)
                action.destructive -> MaterialTheme.colorScheme.error
                else -> MaterialTheme.colorScheme.onSecondaryContainer
            }
        )
    }
}

@Composable
private fun OverflowMenu(actions: List<SelectionAction>) {
    var expanded by remember { mutableStateOf(false) }
    // The menu is anchored to its own box, so it opens from the button and not from the edge of the bar
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Filled.MoreVert, contentDescription = "More actions for the selection")
        }
        AppMenu(expanded = expanded, onDismiss = { expanded = false }) {
            actions.forEach { action ->
                AppMenuItem(
                    text = action.label,
                    leadingIcon = action.icon,
                    enabled = action.enabled,
                    destructive = action.destructive,
                    onClick = {
                        expanded = false
                        action.onClick()
                    }
                )
            }
        }
    }
}
