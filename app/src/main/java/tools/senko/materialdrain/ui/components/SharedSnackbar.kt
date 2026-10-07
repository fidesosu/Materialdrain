package tools.senko.materialdrain.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxState
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
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import tools.senko.materialdrain.navmenu.NavFabPosition

/** The gap between the snackbar and the navigation button beside it. */
private val EdgeGap = 8.dp

/** The corner radius of the navigation button, which the snackbar copies. */
private val EdgeCorner = 16.dp

/** The space between the snackbar and the screen's sides and bottom: half the navigation button's own margin. */
private val SideMargin = 8.dp

/** How long the snackbar moves and the navigation button lifts. They share it, so they flow together. */
private const val SNACKBAR_MOTION_MS = 300

/** How long the styled and the standard look take to fade into each other. */
private const val LOOK_FADE_MS = 200

/** How long the snackbar takes to auto-dismiss, as [SnackbarHost] times it. Null for an indefinite one. */
private fun autoDismissMs(duration: SnackbarDuration): Long? = when (duration) {
    SnackbarDuration.Short -> 4_000L
    SnackbarDuration.Long -> 10_000L
    SnackbarDuration.Indefinite -> null
}

/**
 * The motion of everything the snackbar moves: its size, its entrance and exit, the gap beside the button, and the lift of
 * the buttons above it. Instant with reduced motion.
 */
fun <T> snackbarMotion(reduceMotion: Boolean): FiniteAnimationSpec<T> =
    if (reduceMotion) snap() else tween(SNACKBAR_MOTION_MS, easing = FastOutSlowInEasing)

/**
 * The snackbar. With [styled], it has the navigation button's look: its colours, corners and label type, and its height
 * is the button's ([fabSize]). When the button sits at the left or right edge ([edgeFab]), the snackbar shares the button's
 * row and leaves the button's space free; otherwise it takes the full width. Without [styled] it is the standard snackbar.
 *
 * The host itself shows and hides the snackbar, so it can animate in and out. It keeps the last message while that one leaves.
 * Its size changes grow upward from the bottom.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSnackbarHost(
    hostState: SnackbarHostState,
    modifier: Modifier = Modifier,
    styled: Boolean = false,
    edgeFab: NavFabPosition? = null,
    fabSize: Dp = 56.dp,
    reduceMotion: Boolean = false,
    onDismiss: () -> Unit = {}
) {
    val current = hostState.currentSnackbarData
    // The last snackbar shown, which stays while it leaves
    var lastShown by remember { mutableStateOf<SnackbarData?>(null) }
    LaunchedEffect(current) {
        if (current != null) {
            lastShown = current
            // The host takes the snackbar away after its time, as SnackbarHost does
            autoDismissMs(current.visuals.duration)?.let {
                delay(it)
                current.dismiss()
            }
        }
    }
    val shown = current ?: lastShown

    val fade = tween<Float>(if (reduceMotion) 100 else SNACKBAR_MOTION_MS)
    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
        AnimatedVisibility(
            visible = current != null,
            enter = fadeIn(fade) + slideInVertically(snackbarMotion<IntOffset>(reduceMotion)) { it },
            exit = fadeOut(fade) + slideOutVertically(snackbarMotion<IntOffset>(reduceMotion)) { it }
        ) {
            shown?.let { snackbarData ->
                SnackbarItem(
                    snackbarData = snackbarData,
                    styled = styled,
                    edgeFab = edgeFab,
                    fabSize = fabSize,
                    reduceMotion = reduceMotion,
                    onDismiss = {
                        snackbarData.dismiss()
                        onDismiss()
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SnackbarItem(
    snackbarData: SnackbarData,
    styled: Boolean,
    edgeFab: NavFabPosition?,
    fabSize: Dp,
    reduceMotion: Boolean,
    onDismiss: () -> Unit
) {
    val dismissState = key(snackbarData) {
        rememberSwipeToDismissBoxState()
    }

    LaunchedEffect(dismissState.currentValue) {
        if (dismissState.currentValue != SwipeToDismissBoxValue.Settled) onDismiss()
    }

    // Two lines of the message; a tap shows all of it
    var expanded by remember(snackbarData) { mutableStateOf(false) }
    val actionLabel = snackbarData.visuals.actionLabel
    val message = snackbarData.visuals.message

    // Switching between the styled and the standard look fades between the two, instead of swapping at once
    AnimatedContent(
        targetState = styled,
        transitionSpec = {
            val fade = tween<Float>(if (reduceMotion) 100 else LOOK_FADE_MS)
            fadeIn(fade) togetherWith fadeOut(fade)
        },
        label = "snackbarLook"
    ) { isStyled ->
        if (isStyled) {
            StyledSnackbar(
                dismissState = dismissState,
                snackbarData = snackbarData,
                message = message,
                actionLabel = actionLabel,
                fabSize = fabSize,
                edgeFab = edgeFab,
                reduceMotion = reduceMotion,
                expanded = expanded,
                onToggleExpanded = { expanded = !expanded }
            )
        } else {
            StandardSnackbar(
                dismissState = dismissState,
                snackbarData = snackbarData,
                message = message,
                actionLabel = actionLabel,
                reduceMotion = reduceMotion,
                expanded = expanded,
                onToggleExpanded = { expanded = !expanded }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StandardSnackbar(
    dismissState: SwipeToDismissBoxState,
    snackbarData: SnackbarData,
    message: String,
    actionLabel: String?,
    reduceMotion: Boolean,
    expanded: Boolean,
    onToggleExpanded: () -> Unit
) {
    // Grows upward: the box is aligned to its bottom, so a change of height keeps the bottom edge in place
    Box(
        modifier = Modifier.fillMaxWidth().animateContentSize(animationSpec = snackbarMotion<IntSize>(reduceMotion)),
        contentAlignment = Alignment.BottomCenter
    ) {
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
                            modifier = Modifier.clickable { onToggleExpanded() }
                        )
                    }
                )
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StyledSnackbar(
    dismissState: SwipeToDismissBoxState,
    snackbarData: SnackbarData,
    message: String,
    actionLabel: String?,
    fabSize: Dp,
    edgeFab: NavFabPosition?,
    reduceMotion: Boolean,
    expanded: Boolean,
    onToggleExpanded: () -> Unit
) {
    val motion = snackbarMotion<Dp>(reduceMotion)
    // The gap beside the button eases in and out with the button, so the message doesn't jump when the button comes or goes
    val startGap by animateDpAsState(
        targetValue = if (edgeFab == NavFabPosition.START) fabSize + EdgeGap else 0.dp,
        animationSpec = motion,
        label = "snackbarStartGap"
    )
    val endGap by animateDpAsState(
        targetValue = if (edgeFab == NavFabPosition.END) fabSize + EdgeGap else 0.dp,
        animationSpec = motion,
        label = "snackbarEndGap"
    )

    // Grows upward: the box is aligned to its bottom, so a change of height keeps the bottom edge in place
    Box(
        modifier = Modifier.fillMaxWidth().animateContentSize(animationSpec = snackbarMotion<IntSize>(reduceMotion)),
        contentAlignment = Alignment.BottomCenter
    ) {
        // The row is the button's: the button's space is left free on its side, the message takes the rest
        Row(
            modifier = Modifier
                .fillMaxWidth()
                // No navigation bar inset here: the snackbar slot already has it, the button's margin doesn't
                .padding(start = SideMargin, end = SideMargin, bottom = SideMargin),
            verticalAlignment = Alignment.Bottom
        ) {
            Spacer(Modifier.width(startGap))
            SwipeToDismissBox(
                state = dismissState,
                modifier = Modifier.weight(1f),
                backgroundContent = { /* No background needed for this use case */ },
                content = {
                    Surface(
                        // As tall as the button, unless the message is longer and needs more room
                        modifier = Modifier.fillMaxWidth().then(
                            if (expanded || fabSize <= 0.dp) Modifier.heightIn(min = fabSize.coerceAtLeast(56.dp))
                            else Modifier.height(fabSize)
                        ),
                        shape = RoundedCornerShape(EdgeCorner),
                        // The same colours as the standard snackbar, so both look like one kind of message
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        shadowElevation = 6.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = message,
                                style = MaterialTheme.typography.labelLarge,
                                maxLines = if (expanded) Int.MAX_VALUE else 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f).clickable { onToggleExpanded() }
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
            Spacer(Modifier.width(endGap))
        }
    }
}
