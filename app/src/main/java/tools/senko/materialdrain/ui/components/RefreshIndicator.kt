package tools.senko.materialdrain.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import tools.senko.materialdrain.ui.LocalReduceMotion
import tools.senko.materialdrain.ui.icons.filled.Check
import tools.senko.materialdrain.ui.icons.filled.Refresh

/** What the indicator shows: the arrow being pulled, the arc while it loads, the check once it has. */
private enum class RefreshPhase { PULLING, REFRESHING, DONE }

/**
 * The pull-to-refresh indicator of the app's lists, in Material's own box (it comes down with the pull and goes back up):
 * an arrow which turns as far as it's pulled, and grows a little the moment letting go would refresh; then a spinning arc
 * while it loads, which becomes a check as the indicator goes away. Without the turning, growing and check when
 * animations are reduced.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RefreshIndicator(state: PullToRefreshState, isRefreshing: Boolean, modifier: Modifier = Modifier) {
    val reduceMotion = LocalReduceMotion.current
    val colors = MaterialTheme.colorScheme
    // A refresh which just ended shows its check while the indicator goes back up
    var justDone by remember { mutableStateOf(false) }
    var wasRefreshing by remember { mutableStateOf(false) }
    LaunchedEffect(isRefreshing) {
        if (wasRefreshing && !isRefreshing && !reduceMotion) {
            justDone = true
            delay(DONE_SHOWN_MS)
        }
        justDone = false
        wasRefreshing = isRefreshing
    }
    val phase = when {
        isRefreshing -> RefreshPhase.REFRESHING
        justDone -> RefreshPhase.DONE
        else -> RefreshPhase.PULLING
    }
    // A little bigger once letting go would refresh, springing back as it comes
    val pulledFar by remember(state) { derivedStateOf { state.distanceFraction >= 1f } }
    val ready = pulledFar && !isRefreshing
    val bump by animateFloatAsState(if (ready && !reduceMotion) 1.15f else 1f, spring(dampingRatio = 0.45f, stiffness = 600f), label = "refreshReady")

    PullToRefreshDefaults.IndicatorBox(
        state = state,
        isRefreshing = isRefreshing,
        modifier = modifier,
        containerColor = colors.primaryContainer
    ) {
        AnimatedContent(
            targetState = phase,
            transitionSpec = {
                if (reduceMotion) fadeIn(tween(0)) togetherWith fadeOut(tween(0))
                else (fadeIn(tween(150)) + scaleIn(tween(200), initialScale = 0.6f)) togetherWith (fadeOut(tween(100)) + scaleOut(tween(150), targetScale = 0.6f))
            },
            label = "refreshPhase"
        ) { shown ->
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(24.dp)) {
                when (shown) {
                    RefreshPhase.REFRESHING -> CircularProgressIndicator(
                        color = colors.onPrimaryContainer,
                        strokeWidth = 2.5.dp,
                        modifier = Modifier.size(20.dp)
                    )
                    RefreshPhase.DONE -> Icon(Icons.Filled.Check, contentDescription = null, tint = colors.onPrimaryContainer)
                    RefreshPhase.PULLING -> Icon(
                        Icons.Filled.Refresh,
                        contentDescription = null,
                        tint = colors.onPrimaryContainer,
                        modifier = Modifier.graphicsLayer {
                            // Turned as far as it's pulled (read while drawing, so a pull doesn't compose it again)
                            val pulled = state.distanceFraction.coerceIn(0f, 1.5f)
                            rotationZ = if (reduceMotion) 0f else pulled * 270f
                            alpha = (pulled * 2f).coerceIn(0f, 1f)
                            scaleX = bump
                            scaleY = bump
                        }
                    )
                }
            }
        }
    }
}

/** How long the check stays once a refresh is done, while the indicator goes back up. */
private const val DONE_SHOWN_MS = 600L
