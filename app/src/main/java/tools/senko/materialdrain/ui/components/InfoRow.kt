package tools.senko.materialdrain.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource // Added for clickable without ripple
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun InfoRow(
    label: String,
    value: String,
    isValueSelectable: Boolean = false,
    onValueClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .padding(vertical = 4.dp)
            .fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "$label:",
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
            modifier = Modifier
                .defaultMinSize(minWidth = 110.dp)
                .padding(end = 8.dp)
        )
        val valueModifier = Modifier
            .weight(1f)
            .then(
                if (onValueClick != null) {
                    Modifier.clickable(
                        onClick = onValueClick,
                        indication = null, // Disable ripple
                        interactionSource = remember { MutableInteractionSource() } // Required for indication = null
                    )
                } else {
                    Modifier
                }
            )

        val valueColor = if (onValueClick != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface

        if (isValueSelectable) {
            SelectionContainer {
                Text(
                    value,
                    style = MaterialTheme.typography.bodyMedium,
                    color = valueColor,
                    modifier = valueModifier
                )
            }
        } else {
            Text(
                value,
                style = MaterialTheme.typography.bodyMedium,
                color = valueColor, // Apply color here too for non-selectable but clickable items if any in future
                modifier = valueModifier
            )
        }
    }
}
