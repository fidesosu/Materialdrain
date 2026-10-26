package tools.senko.materialdrain.update

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import tools.senko.materialdrain.ui.preview.PreviewTheme
import tools.senko.materialdrain.ui.preview.ThemePreviews

/**
 * Once after an update: what the version now installed brought since the one before ([changes], newest first), with the
 * same list as the update sheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WhatsNewSheet(versionName: String, changes: List<UpdateChange>, onClose: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onClose, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        WhatsNewContent(versionName, changes, onClose)
    }
}

@Composable
private fun WhatsNewContent(versionName: String, changes: List<UpdateChange>, onClose: () -> Unit) {
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(start = 24.dp, end = 24.dp, bottom = 24.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("What's new", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Materialdrain $versionName",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        ChangeList(changes, empty = "")
        Row(modifier = Modifier.fillMaxWidth()) {
            Spacer(Modifier.weight(1f))
            Button(onClick = onClose) { Text("Got it") }
        }
    }
}

// ---- Previews ----

@ThemePreviews
@Composable
private fun WhatsNewSheetPreview() = PreviewTheme {
    WhatsNewContent(
        "1.6.5",
        listOf(
            "Lists show the shape of what's coming while they load: rows (or tiles, in the grid view) like the files', with soft bars where the names and details will be.",
            "A failed download can be tried again: \"Try again\" on its message, or on its notification when the app was in the background.",
            "Light or dark as the app is set, everywhere."
        ).mapIndexed { i, text -> UpdateChange(1_792_000_000L - i, text) },
        onClose = {}
    )
}
