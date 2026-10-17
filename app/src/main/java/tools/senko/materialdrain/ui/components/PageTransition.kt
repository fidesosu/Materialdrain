package tools.senko.materialdrain.ui.components

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

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

/**
 * [pageTransition] for a page that can't be drawn twice, as both pages of an AnimatedContent are while they cross: a
 * file's details, whose previews belong to the one file shown (see App.kt). The page that's left is a picture of it,
 * taken just before ([capture]), which goes as the page that's left goes in [pageTransition], while the page itself
 * comes in on top of it the same way. Until [run] starts, the picture stays as it is and the page is hidden, so the next
 * page can get ready behind it.
 *
 * The page is drawn with [page] (which also takes the pictures), the picture under it with [OutgoingPage].
 */
@Stable
class PageSwap {
    /** The picture of the page that's left, while it goes. */
    var outgoing by mutableStateOf<ImageBitmap?>(null)
        private set
    private var forward by mutableStateOf(true)
    private var reduceMotion by mutableStateOf(false)
    // 0 to 1: how far both pages have moved; how far the page has faded in; how far the picture has faded out
    private val slide = Animatable(1f)
    private val fadeIn = Animatable(1f)
    private val fadeOut = Animatable(1f)

    /** Takes the picture of the page as it's drawn now (see [page]), and keeps it in place over the hidden page. */
    suspend fun capture(layer: GraphicsLayer, forward: Boolean, reduceMotion: Boolean) {
        outgoing = layer.toImageBitmap()
        this.forward = forward
        this.reduceMotion = reduceMotion
        slide.snapTo(0f)
        fadeIn.snapTo(0f)
        fadeOut.snapTo(0f)
    }

    /** The pages cross, as in [pageTransition]; the picture is let go once they have. */
    suspend fun run() {
        try {
            coroutineScope {
                if (reduceMotion) {
                    launch { fadeIn.animateTo(1f, tween(100)) }
                    launch { fadeOut.animateTo(1f, tween(100)) }
                } else {
                    launch { slide.animateTo(1f, tween(PAGE_TRANSITION_MS)) }
                    launch { fadeIn.animateTo(1f, tween(PAGE_TRANSITION_MS)) }
                    launch { fadeOut.animateTo(1f, tween(PAGE_FADE_OUT_MS)) }
                }
            }
        } finally {
            reset()
        }
    }

    /** Back to no swap at all: the page as it is, no picture. */
    suspend fun reset() {
        outgoing = null
        slide.snapTo(1f)
        fadeIn.snapTo(1f)
        fadeOut.snapTo(1f)
    }

    private val direction get() = if (forward) 1 else -1

    /** The page: coming in while a swap runs, and recorded into [layer] for the picture of the next one. */
    fun Modifier.page(layer: GraphicsLayer): Modifier = this
        .graphicsLayer {
            // By whole pixels, as AnimatedContent slides
            if (!reduceMotion) translationX = ((1f - slide.value) * size.width * direction / 4).roundToInt().toFloat()
            alpha = fadeIn.value
        }
        // Inside the move and fade, so the picture is of the page as it stands
        .drawWithContent {
            layer.record { this@drawWithContent.drawContent() }
            drawLayer(layer)
        }

    /** The picture of the page that's left, going; under [page], as AnimatedContent draws the new page on top. */
    // Always there, the picture read while drawing: it's drawn in the same frame as the page is hidden, never a frame of
    // neither
    @Composable
    fun BoxScope.OutgoingPage() {
        Spacer(
            Modifier
                .matchParentSize()
                .graphicsLayer {
                    if (!reduceMotion) translationX = (-slide.value * size.width * direction / 4).roundToInt().toFloat()
                    alpha = 1f - fadeOut.value
                }
                .drawBehind { outgoing?.let { drawImage(it) } }
        )
    }
}
