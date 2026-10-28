package tools.senko.materialdrain.ui.theme

import android.app.WallpaperColors
import android.app.WallpaperManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.annotation.StringRes
import androidx.compose.ui.res.stringResource
import tools.senko.materialdrain.R

/** Whether the app is light or dark: as the system is, or always one of them. */
enum class ThemeMode(@StringRes val labelRes: Int) { SYSTEM(R.string.theme_system), LIGHT(R.string.theme_light), DARK(R.string.theme_dark) }

/** The choice's name in the app's language. */
val ThemeMode.label: String
    @Composable get() = stringResource(labelRes)

/**
 * How the app is coloured (Look and feel): light or dark ([mode]), from the wallpaper ([wallpaperColors]) or else from
 * [color], and in the dark on black ([blackBackground]).
 */
@Immutable
data class ThemeSettings(
    val mode: ThemeMode = ThemeMode.SYSTEM,
    val wallpaperColors: Boolean = true,
    val color: Int = DEFAULT_THEME_COLOR,
    val blackBackground: Boolean = false
) {
    /** Whether the colours come from [color]: the wallpaper's are off. */
    val usesColor: Boolean get() = !wallpaperColors
}

/** The theme's settings, for what has a theme of its own inside the app's (see [DarkTheme]). */
val LocalThemeSettings = staticCompositionLocalOf { ThemeSettings() }

/** Whether [settings] make the app dark here. */
@Composable
fun isDarkTheme(settings: ThemeSettings): Boolean = when (settings.mode) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

/**
 * The colours [settings] give, in their [dark] or light version. From the wallpaper, Android 12 and up give the app its
 * colours themselves; before, the app makes them from the wallpaper's main colour the way Android 12 does (see
 * [seededColorScheme]), or from [ThemeSettings.color] when the wallpaper has none to give (a live one may not).
 */
@Composable
fun appColorScheme(settings: ThemeSettings, dark: Boolean): ColorScheme {
    val context = LocalContext.current
    val fromWallpaper = !settings.usesColor
    val seed = if (fromWallpaper && Build.VERSION.SDK_INT < Build.VERSION_CODES.S) wallpaperColor(context) ?: settings.color else settings.color
    val scheme = remember(fromWallpaper, seed, dark, context) {
        if (fromWallpaper && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        } else {
            seededColorScheme(seed, dark)
        }
    }
    return if (dark && settings.blackBackground) remember(scheme) { scheme.onBlack() } else scheme
}

/** The main colour of the wallpaper on the home screen, as the system tells it, kept up to date; null when it has none. */
@Composable
private fun wallpaperColor(context: Context): Int? {
    val manager = remember(context) { WallpaperManager.getInstance(context) }
    var color by remember(manager) { mutableStateOf(primaryColorOf(manager.getWallpaperColors(WallpaperManager.FLAG_SYSTEM))) }
    DisposableEffect(manager) {
        val listener = WallpaperManager.OnColorsChangedListener { colors, which ->
            if (which and WallpaperManager.FLAG_SYSTEM != 0) color = primaryColorOf(colors)
        }
        manager.addOnColorsChangedListener(listener, Handler(Looper.getMainLooper()))
        onDispose { manager.removeOnColorsChangedListener(listener) }
    }
    return color
}

private fun primaryColorOf(colors: WallpaperColors?): Int? = colors?.primaryColor?.toArgb()

@Composable
fun MaterialdrainTheme(
    settings: ThemeSettings = ThemeSettings(),
    darkTheme: Boolean = isDarkTheme(settings),
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(LocalThemeSettings provides settings) {
        MaterialTheme(
            colorScheme = appColorScheme(settings, darkTheme),
            typography = Typography,
            shapes = Shapes,
            content = content
        )
    }
}

/**
 * The app's colours in their dark version, whichever the app is in: for what is drawn on black, such as the video
 * player, so its accents are the ones made to be seen on a dark background.
 */
@Composable
fun DarkTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = appColorScheme(LocalThemeSettings.current, dark = true),
        typography = Typography,
        shapes = Shapes,
        content = content
    )
}

// ---- Previews ----

/** The theme's colours the app uses, each on its swatch with its name in the colour that goes on it. */
@Preview(name = "Light", showBackground = true, widthDp = 360)
@Preview(name = "Dark", showBackground = true, widthDp = 360, uiMode = Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_NORMAL)
@Composable
private fun ThemeColorsPreview() {
    MaterialdrainTheme(ThemeSettings(wallpaperColors = false)) {
        val c = MaterialTheme.colorScheme
        val swatches = listOf(
            Triple("primary", c.primary, c.onPrimary),
            Triple("primaryContainer", c.primaryContainer, c.onPrimaryContainer),
            Triple("secondary", c.secondary, c.onSecondary),
            Triple("secondaryContainer", c.secondaryContainer, c.onSecondaryContainer),
            Triple("tertiary", c.tertiary, c.onTertiary),
            Triple("tertiaryContainer", c.tertiaryContainer, c.onTertiaryContainer),
            Triple("error", c.error, c.onError),
            Triple("errorContainer", c.errorContainer, c.onErrorContainer),
            Triple("background", c.background, c.onBackground),
            Triple("surface", c.surface, c.onSurface),
            Triple("surfaceContainerLow", c.surfaceContainerLow, c.onSurface),
            Triple("surfaceContainer", c.surfaceContainer, c.onSurface),
            Triple("surfaceContainerHigh", c.surfaceContainerHigh, c.onSurface),
            Triple("surfaceContainerHighest", c.surfaceContainerHighest, c.onSurfaceVariant),
            Triple("outlineVariant", c.outlineVariant, c.onSurface)
        )
        Column(Modifier.background(c.background)) {
            swatches.forEach { (name, color, on) ->
                Text(name, color = on, style = MaterialTheme.typography.labelLarge, modifier = Modifier.fillMaxWidth().background(color).padding(12.dp))
            }
        }
    }
}

/** The theme's own colour and a chosen one (teal), on black: what the Look and feel settings change. */
@Preview(name = "Dark", showBackground = true, widthDp = 360, uiMode = Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_NORMAL)
@Composable
private fun ThemeChoicesPreview() {
    Row {
        listOf(
            ThemeSettings(wallpaperColors = false),
            ThemeSettings(wallpaperColors = false, color = ThemeColorPresets[2].second, blackBackground = true)
        ).forEach { settings ->
            MaterialdrainTheme(settings) {
                val c = MaterialTheme.colorScheme
                Column(Modifier.weight(1f).background(c.background).padding(12.dp)) {
                    Text("Page", color = c.onBackground)
                    Text("Card", color = c.onSurface, modifier = Modifier.fillMaxWidth().background(c.surfaceContainerHigh).padding(12.dp))
                    Text("Button", color = c.onPrimary, modifier = Modifier.fillMaxWidth().background(c.primary).padding(12.dp))
                    Text("Selected", color = c.onSecondaryContainer, modifier = Modifier.fillMaxWidth().background(c.secondaryContainer).padding(12.dp))
                }
            }
        }
    }
}
