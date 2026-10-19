package tools.senko.materialdrain

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import tools.senko.materialdrain.provider.api.HostScreen
import tools.senko.materialdrain.provider.api.ProviderCapability
import tools.senko.materialdrain.provider.api.ScreenConfig
import tools.senko.materialdrain.ui.preview.PreviewTheme
import tools.senko.materialdrain.ui.preview.ThemePreviews
import tools.senko.materialdrain.ui.theme.AppShapes

/**
 * Where files shared from another app can go on the host with [tabs]: its Upload tab, and each of its Filesystem tabs whose
 * config doesn't take uploading away (see ScreenConfig.disabledCapabilities), in the order of the tabs.
 */
internal fun shareTargetsOf(tabs: List<Tab>): List<Tab> = tabs.filter { tab ->
    tab.screen == Screen.Upload ||
        (tab.screen == Screen.Filesystem && ProviderCapability.UPLOAD !in tab.config?.disabledCapabilities.orEmpty())
}

/**
 * Asks where shared files go, when the host has more than one place for them ([targets], see [shareTargetsOf]):
 * the Upload tab takes them as new files, a Filesystem tab opens to pick the folder they go into (see SharedUploadBar).
 * Cancel drops them.
 */
@Composable
internal fun ShareTargetDialog(targets: List<Tab>, onChoose: (Tab) -> Unit, onCancel: () -> Unit) {
    // A dialog of its own rather than an AlertDialog, whose fixed room around the title and the buttons left the choices
    // far from its edges
    Dialog(onDismissRequest = onCancel) {
        Surface(
            shape = AlertDialogDefaults.shape,
            color = AlertDialogDefaults.containerColor,
            tonalElevation = AlertDialogDefaults.TonalElevation,
            modifier = Modifier.widthIn(min = 280.dp, max = 560.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // Cancel beside the title, so it takes no row of its own under the choices
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Upload to",
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.weight(1f).padding(start = 8.dp)
                    )
                    TextButton(onClick = onCancel) { Text("Cancel") }
                }
                targets.forEach { tab ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(AppShapes.Card)
                            // A shade apart from the dialog's own background
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                            .clickable { onChoose(tab) }
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Icon(tab.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Column(modifier = Modifier.weight(1f).padding(start = 16.dp)) {
                            Text(tab.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                if (tab.screen == Screen.Upload) "As new files, on the upload screen" else "Into a folder you pick",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

// ---- Previews ----

/** Files shared to a host with the Upload tab and two Filesystem tabs. */
@ThemePreviews
@Composable
private fun ShareTargetDialogPreview() {
    PreviewTheme {
        ShareTargetDialog(
            targets = listOf(
                Tab(Screen.Upload),
                Tab(Screen.Filesystem),
                Tab(Screen.Filesystem, ScreenConfig(HostScreen.FILESYSTEM, id = "photos", name = "Photos", icon = "photo"))
            ),
            onChoose = {},
            onCancel = {}
        )
    }
}
