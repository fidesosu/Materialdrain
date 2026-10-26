package tools.senko.materialdrain.ui.theme

import android.annotation.SuppressLint
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.google.android.material.color.utilities.DynamicColor
import com.google.android.material.color.utilities.Hct
import com.google.android.material.color.utilities.MaterialDynamicColors
import com.google.android.material.color.utilities.SchemeTonalSpot
import tools.senko.materialdrain.R

/** The app's own colour, which its colours are made from when they don't come from the wallpaper. */
const val DEFAULT_THEME_COLOR: Int = 0xFF6750A4.toInt()

/**
 * The colours offered for the theme (Look and feel), with their names (string resources); any other can be typed in as
 * well.
 */
val ThemeColorPresets: List<Pair<Int, Int>> = listOf(
    R.string.theme_purple to DEFAULT_THEME_COLOR,
    R.string.theme_blue to 0xFF2F6FDB.toInt(),
    R.string.theme_teal to 0xFF00897B.toInt(),
    R.string.theme_green to 0xFF43A047.toInt(),
    R.string.theme_yellow to 0xFFF9A825.toInt(),
    R.string.theme_orange to 0xFFF4511E.toInt(),
    R.string.theme_red to 0xFFD32F2F.toInt(),
    R.string.theme_pink to 0xFFD81B60.toInt(),
    R.string.theme_grey to 0xFF607D8B.toInt()
)

/**
 * All of the theme's colours, made from one [seed] colour the way Android makes them from the wallpaper (Material's
 * "tonal spot"), so a chosen colour looks as at home as the wallpaper's. The colour utilities are Material Components'
 * own (marked for its use only, hence the lint suppression); they are what Android 12's wallpaper colours come from.
 */
@SuppressLint("RestrictedApi")
fun seededColorScheme(seed: Int, dark: Boolean): ColorScheme {
    val scheme = SchemeTonalSpot(Hct.fromInt(seed), dark, 0.0)
    val roles = MaterialDynamicColors()
    fun DynamicColor.color() = Color(getArgb(scheme))
    // Every colour is given, so it makes no difference that the light builder is used for the dark colours as well: it
    // only sets what it's given
    return lightColorScheme(
        primary = roles.primary().color(),
        onPrimary = roles.onPrimary().color(),
        primaryContainer = roles.primaryContainer().color(),
        onPrimaryContainer = roles.onPrimaryContainer().color(),
        inversePrimary = roles.inversePrimary().color(),
        secondary = roles.secondary().color(),
        onSecondary = roles.onSecondary().color(),
        secondaryContainer = roles.secondaryContainer().color(),
        onSecondaryContainer = roles.onSecondaryContainer().color(),
        tertiary = roles.tertiary().color(),
        onTertiary = roles.onTertiary().color(),
        tertiaryContainer = roles.tertiaryContainer().color(),
        onTertiaryContainer = roles.onTertiaryContainer().color(),
        background = roles.background().color(),
        onBackground = roles.onBackground().color(),
        surface = roles.surface().color(),
        onSurface = roles.onSurface().color(),
        surfaceVariant = roles.surfaceVariant().color(),
        onSurfaceVariant = roles.onSurfaceVariant().color(),
        surfaceTint = roles.surfaceTint().color(),
        inverseSurface = roles.inverseSurface().color(),
        inverseOnSurface = roles.inverseOnSurface().color(),
        error = roles.error().color(),
        onError = roles.onError().color(),
        errorContainer = roles.errorContainer().color(),
        onErrorContainer = roles.onErrorContainer().color(),
        outline = roles.outline().color(),
        outlineVariant = roles.outlineVariant().color(),
        scrim = roles.scrim().color(),
        surfaceBright = roles.surfaceBright().color(),
        surfaceContainer = roles.surfaceContainer().color(),
        surfaceContainerHigh = roles.surfaceContainerHigh().color(),
        surfaceContainerHighest = roles.surfaceContainerHighest().color(),
        surfaceContainerLow = roles.surfaceContainerLow().color(),
        surfaceContainerLowest = roles.surfaceContainerLowest().color(),
        surfaceDim = roles.surfaceDim().color()
    )
}

/**
 * The dark colours on black, for OLED screens: the background black, and the surfaces above it darker by half, so a card
 * still stands out from the page as before.
 */
fun ColorScheme.onBlack(): ColorScheme = copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceDim = Color.Black,
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = lerp(surfaceContainerLow, Color.Black, 0.5f),
    surfaceContainer = lerp(surfaceContainer, Color.Black, 0.5f),
    surfaceContainerHigh = lerp(surfaceContainerHigh, Color.Black, 0.5f),
    surfaceContainerHighest = lerp(surfaceContainerHighest, Color.Black, 0.5f),
    surfaceBright = lerp(surfaceBright, Color.Black, 0.5f)
)
