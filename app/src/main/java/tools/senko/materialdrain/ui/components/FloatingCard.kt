package tools.senko.materialdrain.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

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
