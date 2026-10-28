package tools.senko.materialdrain.ui.components

import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import tools.senko.materialdrain.ui.LocalReduceMotion
import tools.senko.materialdrain.ui.preview.PreviewTheme
import tools.senko.materialdrain.ui.preview.ThemePreviews

/**
 * [text] on up to [maxLines] lines, wrapping as usual while it fits in them. Only when it wouldn't, it stays on one line
 * which scrolls along to show all of it (a marquee), so nothing of it is cut off; with reduced animations it ends with
 * "…" instead.
 */
@Composable
fun ScrollingText(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    maxLines: Int = 1
) {
    val measurer = rememberTextMeasurer()
    val reduceMotion = LocalReduceMotion.current
    BoxWithConstraints(modifier = modifier) {
        val width = constraints.maxWidth
        // Whether it needs more than its lines at this width, measured as it would be drawn
        val overflows = remember(text, style, width, maxLines) {
            constraints.hasBoundedWidth &&
                measurer.measure(text, style, maxLines = maxLines, constraints = Constraints(maxWidth = width)).hasVisualOverflow
        }
        when {
            !overflows -> Text(text, style = style, color = color, maxLines = maxLines, overflow = TextOverflow.Ellipsis)
            reduceMotion -> Text(text, style = style, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
            else -> Text(
                text,
                style = style,
                color = color,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE)
            )
        }
    }
}

// ---- Previews ----

/** A title that fits on two lines (it wraps), and one too long for them (one line, scrolling). */
@ThemePreviews
@Composable
private fun ScrollingTextPreview() {
    PreviewTheme(padding = 16.dp) {
        Column(modifier = Modifier.width(220.dp)) {
            ScrollingText("A song with a longer title", style = MaterialTheme.typography.titleMedium, maxLines = 2)
            ScrollingText(
                "A song whose title is far too long to fit on two lines of this narrow card at all",
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2
            )
        }
    }
}
