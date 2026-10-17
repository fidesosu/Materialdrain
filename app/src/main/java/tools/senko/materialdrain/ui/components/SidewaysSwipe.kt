package tools.senko.materialdrain.ui.components

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.sign

/** How far a swipe has to go to count, when it isn't quick. */
private val SwipeDistance = 72.dp

/** How quick a shorter swipe has to be to count, per second. */
private val SwipeVelocity = 600.dp

/** A swipe has to go this much more sideways than up or down, or it's left to the list. */
private const val SIDEWAYS_RATIO = 2f

/**
 * A swipe sideways across what this modifies, for going to the tab beside the one shown, or the file beside the one in
 * the details ([onSwipe] with true for the next one: the one to the right, or to the left in a right-to-left language).
 * Like the navigation button's swipe, it's one step per swipe, on release, and the page doesn't follow the finger: it
 * changes with the usual page transition.
 *
 * What's inside comes first: a swipe whose touches something else takes (a row that scrolls sideways, such as the folder
 * path, or the list scrolling up and down) is left to it. Nor does one that starts in the system's back gesture area at
 * the edges of the screen count, or one with a second finger (a pinch). Once a swipe is taken, nothing under it gets
 * its touches any more, so the row it started on isn't tapped as well.
 */
@Composable
fun Modifier.sidewaysSwipe(enabled: Boolean, onSwipe: (toNext: Boolean) -> Unit): Modifier {
    if (!enabled) return this
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val gestures = WindowInsets.systemGestures
    val edgeLeft = gestures.getLeft(density, layoutDirection)
    val edgeRight = gestures.getRight(density, layoutDirection)
    val rtl = layoutDirection == LayoutDirection.Rtl
    val currentOnSwipe by rememberUpdatedState(onSwipe)
    return pointerInput(edgeLeft, edgeRight, rtl) {
        val distance = SwipeDistance.toPx()
        val minVelocity = SwipeVelocity.toPx()
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            // The system's back gesture starts there
            if (down.position.x < edgeLeft || down.position.x > size.width - edgeRight) return@awaitEachGesture
            val velocity = VelocityTracker().apply { addPosition(down.uptimeMillis, down.position) }
            var total = Offset.Zero
            var sideways = false
            while (true) {
                val event = awaitPointerEvent()
                if (event.changes.size > 1) return@awaitEachGesture
                val change = event.changes.firstOrNull { it.id == down.id } ?: return@awaitEachGesture
                if (change.changedToUpIgnoreConsumed()) break
                // Taken by something inside, which goes first (this sees the touches after it)
                if (change.isConsumed) return@awaitEachGesture
                total += change.positionChange()
                velocity.addPosition(change.uptimeMillis, change.position)
                if (!sideways && total.getDistance() > viewConfiguration.touchSlop) {
                    if (abs(total.x) < abs(total.y) * SIDEWAYS_RATIO) return@awaitEachGesture
                    sideways = true
                }
                if (sideways) change.consume()
            }
            if (!sideways) return@awaitEachGesture
            val speed = velocity.calculateVelocity().x
            val far = abs(total.x) >= distance
            val quick = abs(speed) >= minVelocity && sign(speed) == sign(total.x)
            // A swipe to the left moves on to the tab to the right
            if (far || quick) currentOnSwipe(if (rtl) total.x > 0 else total.x < 0)
        }
    }
}
