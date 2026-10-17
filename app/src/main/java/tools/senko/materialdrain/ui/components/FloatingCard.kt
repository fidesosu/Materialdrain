package tools.senko.materialdrain.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import tools.senko.materialdrain.ui.preview.PreviewTheme
import tools.senko.materialdrain.ui.preview.ThemePreviews
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush

// The floating card look: the cards above the file list (Sort by, New), the search results, the host switcher and every
// menu (see AppMenu). Changed here, it changes everywhere.

/** The corners of a floating card. */
val FloatingCardShape = RoundedCornerShape(20.dp)

/** A floating card's fill: translucent, so whatever is behind it (the blur, in the search) still shows through. */
@Composable
fun floatingCardColor(): Color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.72f)

/** A floating card's hairline edge, which keeps it apart from what's behind it. */
@Composable
fun floatingCardBorder(): BorderStroke = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

// ---- Previews ----

/** A floating card over a busy background, which shows through it. */
@ThemePreviews
@Composable
private fun FloatingCardPreview() {
    PreviewTheme {
        Box(Modifier.background(Brush.linearGradient(listOf(Color(0xFF6750A4), Color(0xFFE8B4B8)))).padding(24.dp)) {
            Text(
                "Sort by  ·  Name",
                modifier = Modifier
                    .border(floatingCardBorder(), FloatingCardShape)
                    .background(floatingCardColor(), FloatingCardShape)
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            )
        }
    }
}
