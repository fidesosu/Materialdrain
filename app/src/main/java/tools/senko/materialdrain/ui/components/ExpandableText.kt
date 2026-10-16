package tools.senko.materialdrain.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import tools.senko.materialdrain.ui.LocalReduceMotion

/**
 * Text of any length (a file's name, a link, a hash) kept to [collapsedLines] lines, so a long one can't take over the
 * page. Only when it's cut off does a tap show all of it, and another tap folds it again. On one line it's cut in the
 * middle, so both its start and its end (a name's extension, a hash's last digits) stay in sight, unless [cutInMiddle]
 * is off. It stays selectable.
 */
@Composable
fun ExpandableText(
    text: String,
    modifier: Modifier = Modifier,
    collapsedLines: Int = 2,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
    fontFamily: FontFamily? = null,
    cutInMiddle: Boolean = true
) {
    var expanded by remember(text) { mutableStateOf(false) }
    // Only a collapsed layout says whether the text fits; expanded, nothing is cut off but it can still be folded
    var cutOff by remember(text) { mutableStateOf(false) }
    val lines = collapsedLines.coerceAtLeast(1)
    val reduceMotion = LocalReduceMotion.current
    SelectionContainer(
        modifier = modifier
            .animateContentSize(if (reduceMotion) snap() else tween(200, easing = FastOutSlowInEasing))
            .then(if (cutOff || expanded) Modifier.clickable { expanded = !expanded } else Modifier)
    ) {
        Text(
            text = text,
            style = style,
            color = color,
            fontFamily = fontFamily,
            maxLines = if (expanded) Int.MAX_VALUE else lines,
            overflow = if (!expanded && lines == 1 && cutInMiddle) TextOverflow.MiddleEllipsis else TextOverflow.Ellipsis,
            onTextLayout = { if (!expanded) cutOff = it.hasVisualOverflow }
        )
    }
}
