package tools.senko.materialdrain.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * A Material icon from its [paths] (SVG path data on a 24 x 24 grid), drawn the way material-icons-extended draws it. The
 * app keeps only the icons it uses (FilledIcons.kt and the others next to it) instead of that whole library, which is
 * thousands of icons: it made the release build slower and the debug APK about 30 MB bigger.
 *
 * Another icon is one more line in the file of its style, named as in the library so `Icons.Filled.Name` reads the same:
 * its path is the "d" of its SVG at fonts.google.com/icons (the "Material Icons" set, not "Material Symbols"), e.g.
 * `val Icons.Filled.Add: ImageVector by lazy { icon("Filled.Add", "M19,13h-6v6h-2v-6H5v-2h6V5h2v6h6v2Z") }`. One
 * that flips for right-to-left languages (an arrow) goes in AutoMirroredFilledIcons.kt with `autoMirror = true`.
 */
internal fun icon(name: String, vararg paths: String, autoMirror: Boolean = false): ImageVector =
    ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
        autoMirror = autoMirror
    ).apply {
        paths.forEach { addPath(addPathNodes(it), fill = SolidColor(Color.Black)) }
    }.build()
