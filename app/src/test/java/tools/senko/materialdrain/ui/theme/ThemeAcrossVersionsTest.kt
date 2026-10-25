package tools.senko.materialdrain.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The theme on each Android version the app runs on. Before Android 12 the system doesn't colour apps from the wallpaper
 * (Material You): the app makes its colours itself, from the wallpaper's main colour or the one chosen in Look and feel,
 * and every text colour has to stay readable on what it's drawn on, for every colour offered, light, dark and on black.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29, 30, 31, 33, 35])
class ThemeAcrossVersionsTest {

    /** WCAG's contrast ratio of two colours, 1 to 21. */
    private fun contrast(a: Color, b: Color): Double {
        val (light, dark) = listOf(a.luminance() + 0.05, b.luminance() + 0.05).sortedDescending()
        return light / dark
    }

    @Test
    fun wallpaperColoursOnEveryVersion() {
        // Before Android 12 from the wallpaper's main colour (see appColorScheme), from 12 the system's own
        assertEquals(false, ThemeSettings(wallpaperColors = true).usesColor)
        assertTrue(ThemeSettings(wallpaperColors = false).usesColor)
    }

    @Test
    fun textIsReadableInEveryChosenColour() {
        val problems = mutableListOf<String>()
        for ((name, seed) in ThemeColorPresets) {
            for ((look, scheme) in listOf(
                "light" to seededColorScheme(seed, dark = false),
                "dark" to seededColorScheme(seed, dark = true),
                "black" to seededColorScheme(seed, dark = true).onBlack()
            )) {
                val pairs = listOf(
                    Triple("onPrimary on primary", scheme.onPrimary, scheme.primary),
                    Triple("onSurface on surface", scheme.onSurface, scheme.surface),
                    Triple("onBackground on background", scheme.onBackground, scheme.background),
                    Triple("onSurfaceVariant on surfaceContainerHighest", scheme.onSurfaceVariant, scheme.surfaceContainerHighest),
                    Triple("onSecondaryContainer on secondaryContainer", scheme.onSecondaryContainer, scheme.secondaryContainer),
                    Triple("onPrimaryContainer on primaryContainer", scheme.onPrimaryContainer, scheme.primaryContainer),
                    Triple("error on surface", scheme.error, scheme.surface),
                    Triple("primary on surface (links, icons)", scheme.primary, scheme.surface)
                )
                for ((what, fg, bg) in pairs) {
                    val ratio = contrast(fg, bg)
                    // 4.5 for text; 3 for icons and big text (the primary colour on the page)
                    val needed = if (what.startsWith("primary on") || what.startsWith("error on")) 3.0 else 4.5
                    if (ratio < needed) problems += "$name $look: $what %.2f (needs $needed)".format(ratio)
                }
            }
        }
        assertTrue("Hard to read:\n" + problems.joinToString("\n"), problems.isEmpty())
    }

    @Test
    fun blackBackgroundIsBlack() {
        val scheme = seededColorScheme(DEFAULT_THEME_COLOR, dark = true).onBlack()
        assertEquals(Color.Black, scheme.background)
        assertEquals(Color.Black, scheme.surface)
        assertTrue("Cards still stand out from the page", scheme.surfaceContainerHigh.luminance() > 0f)
    }
}
