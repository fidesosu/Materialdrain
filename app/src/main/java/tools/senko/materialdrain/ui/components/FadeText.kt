package tools.senko.materialdrain.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import tools.senko.materialdrain.ui.LocalReduceMotion
import tools.senko.materialdrain.ui.preview.PreviewTheme
import tools.senko.materialdrain.ui.preview.ThemePreviews

/**
 * Text which fades into its new text when [text] changes, the old one fading out where it was: for a value that changes
 * in place (a count, an amount, the floating button's text). Its width follows the text's, unless [fixedWidth] keeps the
 * width of the widest of [text] and [widthReferenceTexts], so the container around it doesn't change size. At once when
 * animations are reduced.
 */
@Composable
fun FadeText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
    fixedWidth: Boolean = false,
    widthReferenceTexts: List<String> = emptyList()
) {
    val reduceMotion = LocalReduceMotion.current
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val reservedWidth = if (fixedWidth) {
        remember(text, widthReferenceTexts, style, density) {
            with(density) { (widthReferenceTexts + text).maxOf { measurer.measure(it, style).size.width }.toDp() }
        }
    } else null
    AnimatedContent(
        targetState = text,
        modifier = modifier.then(if (reservedWidth != null) Modifier.width(reservedWidth) else Modifier),
        contentAlignment = Alignment.Center,
        transitionSpec = {
            val fade = if (reduceMotion) 0 else FADE_MS
            (fadeIn(tween(fade)) togetherWith fadeOut(tween(fade)))
                .using(SizeTransform(clip = false) { _, _ -> if (reduceMotion) snap() else tween(fade) })
        },
        label = "fadeText"
    ) { shown ->
        Text(shown, style = style, color = color, maxLines = 1)
    }
}

/** How long the old text takes to fade out and the new one in. */
private const val FADE_MS = 220

// ---- Previews ----

/** In the interactive preview, + and − change the number. */
@ThemePreviews
@Composable
private fun FadeTextPreview() {
    PreviewTheme(padding = 16.dp) {
        var value by remember { mutableIntStateOf(1_284) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { value -= 37 }) { Text("−") }
            FadeText("$value views", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 8.dp))
            TextButton(onClick = { value += 37 }) { Text("+") }
        }
    }
}
