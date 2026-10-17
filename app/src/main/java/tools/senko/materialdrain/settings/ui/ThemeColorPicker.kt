package tools.senko.materialdrain.settings.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import tools.senko.materialdrain.ui.icons.filled.Check
import tools.senko.materialdrain.ui.icons.filled.Edit
import tools.senko.materialdrain.ui.preview.PreviewTheme
import tools.senko.materialdrain.ui.preview.ThemePreviews
import tools.senko.materialdrain.ui.theme.ThemeColorPresets
import tools.senko.materialdrain.ui.theme.seededColorScheme

private val SwatchSize = 40.dp

/** [color] as "#RRGGBB". */
private fun hexOf(color: Int): String = "#%06X".format(color and 0xFFFFFF)

/** The colour of a typed "#RRGGBB" or "RRGGBB" (opaque), null when it isn't one. */
internal fun parseHexColor(text: String): Int? {
    val hex = text.trim().removePrefix("#")
    if (hex.length != 6) return null
    return hex.toIntOrNull(16)?.let { it or 0xFF000000.toInt() }
}

/**
 * The colour the theme is made from: one of [ThemeColorPresets], or any other typed in as a hex code (the last swatch,
 * which shows a typed colour once there is one). [enabled] is false while the colours come from the wallpaper.
 */
@Composable
fun ThemeColorPicker(selected: Int, enabled: Boolean, onSelect: (Int) -> Unit) {
    var typing by rememberSaveable { mutableStateOf(false) }
    val custom = selected.takeIf { color -> ThemeColorPresets.none { it.second == color } }
    Column(modifier = Modifier.alpha(if (enabled) 1f else 0.38f)) {
        Text("Colour", style = MaterialTheme.typography.bodyLarge)
        Text(
            if (enabled) "What the app's colours are made from" else "Turn off the wallpaper's colours to choose one",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(10.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.horizontalScroll(rememberScrollState()).padding(bottom = 4.dp)
        ) {
            ThemeColorPresets.forEach { (name, color) ->
                Swatch(color, name, chosen = color == selected, enabled = enabled, onClick = { onSelect(color) })
            }
            Swatch(custom, "Custom", chosen = custom != null, enabled = enabled, onClick = { typing = true }, onType = true)
        }
    }
    if (typing) {
        HexColorDialog(
            initial = custom ?: selected,
            onConfirm = { onSelect(it); typing = false },
            onDismiss = { typing = false }
        )
    }
}

/**
 * A round swatch of the theme [color] would give: its main colour, with a tick when [chosen]. [onType] for the swatch
 * of a typed colour, which has a pencil (on a plain circle while there's no [color]).
 */
@Composable
private fun Swatch(color: Int?, name: String, chosen: Boolean, enabled: Boolean, onClick: () -> Unit, onType: Boolean = false) {
    val colors = MaterialTheme.colorScheme
    // The colour as the theme would show it (its primary), not as typed: that's what the app will look like
    val dark = colors.background.luminance() < 0.5f
    val primary = remember(color, dark) { color?.let { seededColorScheme(it, dark).primary } }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(SwatchSize)
            .clip(CircleShape)
            .background(primary ?: colors.surfaceContainerHighest)
            .then(if (chosen) Modifier.border(3.dp, colors.onSurface, CircleShape) else Modifier)
            .clickable(enabled = enabled, role = Role.RadioButton, onClickLabel = name, onClick = onClick)
    ) {
        // The custom swatch has its pencil until its colour is the one chosen, so it reads as "type one in"
        val mark = if (chosen) Icons.Filled.Check else if (onType) Icons.Filled.Edit else null
        mark?.let {
            Icon(it, contentDescription = name, tint = primary?.let(::contentColorOn) ?: colors.onSurfaceVariant, modifier = Modifier.size(20.dp))
        }
    }
}

private fun contentColorOn(color: Color): Color = if (color.luminance() > 0.5f) Color.Black else Color.White

/** A colour typed in as a hex code, with a swatch of it as it's typed. */
@Composable
private fun HexColorDialog(initial: Int, onConfirm: (Int) -> Unit, onDismiss: () -> Unit) {
    var text by rememberSaveable { mutableStateOf(hexOf(initial)) }
    val parsed = parseHexColor(text)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Custom colour") },
        text = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.take(7) },
                    label = { Text("Hex code") },
                    placeholder = { Text("#6750A4") },
                    singleLine = true,
                    isError = parsed == null,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(12.dp))
                Box(
                    modifier = Modifier
                        .size(SwatchSize)
                        .clip(CircleShape)
                        .background(parsed?.let { Color(it) } ?: MaterialTheme.colorScheme.surfaceContainerHighest)
                )
            }
        },
        confirmButton = { Button(onClick = { parsed?.let(onConfirm) }, enabled = parsed != null) { Text("Use") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

// ---- Previews ----

/** The presets with teal chosen, and off while the wallpaper's colours are on. */
@ThemePreviews
@Composable
private fun ThemeColorPickerPreview() {
    PreviewTheme(padding = 16.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            ThemeColorPicker(selected = ThemeColorPresets[2].second, enabled = true, onSelect = {})
            ThemeColorPicker(selected = 0xFF123456.toInt(), enabled = false, onSelect = {})
        }
    }
}
