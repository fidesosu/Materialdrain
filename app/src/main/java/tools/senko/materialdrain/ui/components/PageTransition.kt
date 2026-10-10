package tools.senko.materialdrain.ui.components

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith

// The app's one way of going from page to page: the main screens, the settings and their categories, the pages of the
// config editor. A change made here reaches all of them, so they keep moving alike.

/** How long a page takes to come in. */
const val PAGE_TRANSITION_MS = 250

/** How long the page that's left takes to fade, shorter so the two don't blur into each other. */
private const val PAGE_FADE_OUT_MS = 150

/**
 * One page making way for another: the new one comes in from a quarter of the width away while fading in, and the old
 * one goes a quarter the other way while fading out. A short move, enough to tell which way the page went without the
 * whole screen sweeping across.
 *
 * @param forward deeper, or further along (a tab to the right): the new page comes from the end side; otherwise from
 *   the start side. Null for neither, e.g. between two pages side by side: they only cross-fade.
 * @param reduceMotion the "reduce motion" setting: a short cross-fade instead
 */
fun pageTransition(forward: Boolean?, reduceMotion: Boolean): ContentTransform {
    val transition = when {
        reduceMotion -> fadeIn(tween(100)) togetherWith fadeOut(tween(100))
        forward == null -> fadeIn(tween(PAGE_TRANSITION_MS)) togetherWith fadeOut(tween(PAGE_FADE_OUT_MS))
        else -> {
            val direction = if (forward) 1 else -1
            (slideInHorizontally(tween(PAGE_TRANSITION_MS)) { it * direction / 4 } + fadeIn(tween(PAGE_TRANSITION_MS))) togetherWith
                (slideOutHorizontally(tween(PAGE_TRANSITION_MS)) { -it * direction / 4 } + fadeOut(tween(PAGE_FADE_OUT_MS)))
        }
    }
    // The size changes at once: the pages fill the same space anyway
    return ContentTransform(
        targetContentEnter = transition.targetContentEnter,
        initialContentExit = transition.initialContentExit,
        sizeTransform = SizeTransform(clip = false) { _, _ -> snap() }
    )
}
