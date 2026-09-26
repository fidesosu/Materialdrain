package tools.senko.materialdrain.ui

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf

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

/** Whether videos start over when they end, and how to change that. One setting for every video. */
@Immutable
class VideoLoopSetting(val enabled: Boolean, val onChange: (Boolean) -> Unit)

val LocalVideoLoop = compositionLocalOf { VideoLoopSetting(enabled = false, onChange = {}) }
