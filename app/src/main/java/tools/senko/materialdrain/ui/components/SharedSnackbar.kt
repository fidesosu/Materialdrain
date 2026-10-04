package tools.senko.materialdrain.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import tools.senko.materialdrain.navmenu.NavFabPosition

/** The gap between the snackbar and the navigation button beside it. */
private val EdgeGap = 8.dp

/** The corner radius of the navigation button, which the snackbar copies. */
private val EdgeCorner = 16.dp

/**
 * The snackbar. With the navigation button at the left or right edge ([edgeFab]), it sits in the same row as the button,
 * at its height, with the same colours, corners and label type. Otherwise it is the standard snackbar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSnackbarHost(
    hostState: SnackbarHostState,
    modifier: Modifier = Modifier,
    edgeFab: NavFabPosition? = null,
    fabSize: Dp = 56.dp,
    onDismiss: () -> Unit = {}
) {
    SnackbarHost(
        hostState = hostState,
        modifier = modifier
    ) { snackbarData ->
        val dismissState = key(snackbarData) {
            rememberSwipeToDismissBoxState()
        }

        LaunchedEffect(dismissState.currentValue) {
            if (dismissState.currentValue != SwipeToDismissBoxValue.Settled) {
                snackbarData.dismiss()
                onDismiss()
            }
        }

        // Two lines of the message; a tap shows all of it
        var expanded by remember(snackbarData) { mutableStateOf(false) }
        val actionLabel = snackbarData.visuals.actionLabel
        val message = snackbarData.visuals.message

        if (edgeFab == null) {
            SwipeToDismissBox(
                state = dismissState,
                backgroundContent = { /* No background needed for this use case */ },
                content = {
                    Snackbar(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        action = actionLabel?.let { label ->
                            {
                                TextButton(onClick = { snackbarData.performAction() }) {
                                    Text(label, color = MaterialTheme.colorScheme.primary)
                                }
                            }
                        },
                        content = {
                            Text(
                                text = message,
                                maxLines = if (expanded) Int.MAX_VALUE else 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.clickable { expanded = !expanded }
                            )
                        }
                    )
                }
            )
        } else {
            // The row is the button's: the button's space is left free on its side, the message takes the rest
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    // No navigation bar inset here: the snackbar slot already has it, the button's margin doesn't
                    .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                if (edgeFab == NavFabPosition.START) Spacer(Modifier.width(fabSize + EdgeGap))
                SwipeToDismissBox(
                    state = dismissState,
                    modifier = Modifier.weight(1f),
                    backgroundContent = { /* No background needed for this use case */ },
                    content = {
                        Surface(
                            modifier = Modifier.fillMaxWidth().heightIn(min = fabSize),
                            shape = RoundedCornerShape(EdgeCorner),
                            // The same colours as the standard snackbar, so both look like one kind of message
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurface,
                            shadowElevation = 6.dp
                        ) {
                            Row(
                                modifier = Modifier.padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = message,
                                    style = MaterialTheme.typography.labelLarge,
                                    maxLines = if (expanded) Int.MAX_VALUE else 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f).clickable { expanded = !expanded }
                                )
                                if (actionLabel != null) {
                                    TextButton(
                                        onClick = { snackbarData.performAction() },
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                                    ) {
                                        Text(actionLabel, style = MaterialTheme.typography.labelLarge)
                                    }
                                }
                            }
                        }
                    }
                )
                if (edgeFab == NavFabPosition.END) Spacer(Modifier.width(fabSize + EdgeGap))
            }
        }
    }
}
