package tools.senko.materialdrain.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

private val DarkColorScheme = darkColorScheme(
    primary = Purple80,
    secondary = PurpleGrey80,
    tertiary = Pink80
)

private val LightColorScheme = lightColorScheme(
    primary = Purple40,
    secondary = PurpleGrey40,
    tertiary = Pink40

    /* Other default colors to override
    background = Color(0xFFFFFBFE),
    surface = Color(0xFFFFFBFE),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color(0xFF1C1B1F),
    onSurface = Color(0xFF1C1B1F),
    */
)

@Composable
fun MaterialdrainTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current

    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context)
            else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

// ---- Previews ----

/** The theme's colours the app uses, each on its swatch with its name in the colour that goes on it. */
@Preview(name = "Light", showBackground = true, widthDp = 360)
@Preview(name = "Dark", showBackground = true, widthDp = 360, uiMode = Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_NORMAL)
@Composable
private fun ThemeColorsPreview() {
    MaterialdrainTheme {
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
