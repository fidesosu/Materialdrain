package tools.senko.materialdrain.ui

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.dp

// Settings of the user which composables deep in the tree need. Each is provided once at the root of the app (App.kt).

/**
 * Whether animations are reduced (an accessibility setting, also on when the system has animations turned off).
 *
 * When it is on, decorative motion is left out: sliding, scaling, spinning, growing, rolling text and animated
 * scrolling. Fades and changes which give feedback (like the progress of a transfer) stay.
 * Read it wherever something moves.
 */
val LocalReduceMotion = compositionLocalOf { false }

/**
 * Whether the previews of files show the thumbnail of the file, blurred, as a backdrop (behind a fullscreen image,
 * and while a preview is loading).
 */
val LocalBlurredBackdrop = compositionLocalOf { true }

/** Whether long lines of a text preview wrap, or scroll sideways (see the Previews settings). */
val LocalTextWrap = compositionLocalOf { true }

/**
 * How far the system's navigation bar reaches over the bottom of the screen's content. A screen without the app's
 * bottom bar is drawn down to the edge of the screen, under the navigation bar (which is see-through), so a list
 * scrolls on behind it instead of being cut off above it; the list adds this much room after its last item, so that
 * one can still be scrolled clear of the bar. Zero while the app's bottom bar is there, which keeps the content above it.
 */
val LocalBottomInset = compositionLocalOf { 0.dp }

/** Whether videos start over when they end, and how to change that. One setting for every video. */
@Immutable
class VideoLoopSetting(val enabled: Boolean, val onChange: (Boolean) -> Unit)

val LocalVideoLoop = compositionLocalOf { VideoLoopSetting(enabled = false, onChange = {}) }
