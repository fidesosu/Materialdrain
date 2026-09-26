package tools.senko.materialdrain.ui.media

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun InlineTextPreview(
    textContent: String?
) {
    textContent?.takeIf { it.isNotBlank() }?.let {
        Text("Content Preview (4KB Max):", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top=8.dp, bottom=4.dp))
        OutlinedTextField(
            value = it,
            onValueChange = { /* Read-only */ },
            readOnly = true,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 100.dp, max = 200.dp) // Adjusted max height
                .padding(vertical = 8.dp),
            textStyle = MaterialTheme.typography.bodySmall
        )
    }
}
