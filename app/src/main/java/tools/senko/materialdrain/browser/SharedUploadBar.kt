package tools.senko.materialdrain.browser

import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import tools.senko.materialdrain.files.FloatingBottomBar
import tools.senko.materialdrain.ui.icons.filled.Close
import tools.senko.materialdrain.ui.preview.PreviewTheme
import tools.senko.materialdrain.ui.preview.ThemePreviews
import androidx.compose.ui.res.stringResource
import tools.senko.materialdrain.R

/**
 * Files shared from another app (or picked with the Upload shortcut), waiting for the screen they go into, whichever
 * the user goes to (see App.kt): the browser that takes them sends them there, then calls [onSent]; [onCancel] drops them.
 */
class SharedUpload(val uris: List<Uri>, val onSent: () -> Unit, val onCancel: () -> Unit) {
    val count: Int get() = uris.size
}

/**
 * Something waiting at the bottom of a browser for the place it goes, which the user browses to (see [DestinationBar]):
 * shared files on their way to a screen, entries being moved to another folder. [title] says what waits; [destination]
 * where it would go from here, or null where it can't, which [unavailable] says why; [confirmLabel] is the button which
 * sends it there ([onConfirm]), [onCancel] drops it, which the ✕ says with [cancelDescription].
 */
class DestinationRequest(
    val title: String,
    val destination: String?,
    val unavailable: String,
    val confirmLabel: String,
    val cancelDescription: String,
    val onConfirm: () -> Unit,
    val onCancel: () -> Unit
)

/**
 * The bar at the bottom of a browser while [request] waits for its place, in the selection bar's place and look (see
 * FloatingBottomBar): what waits, where it would go, and the button which sends it there.
 */
@Composable
fun DestinationBar(request: DestinationRequest?) {
    // Kept while it slides out, when what waited is already gone
    var last by remember { mutableStateOf(request) }
    if (request != null && request !== last) SideEffect { last = request }
    FloatingBottomBar(visible = request != null) {
        val shown = request ?: last ?: return@FloatingBottomBar
        IconButton(onClick = shown.onCancel) { Icon(Icons.Filled.Close, contentDescription = shown.cancelDescription) }
        Column(modifier = Modifier.weight(1f).padding(horizontal = 4.dp)) {
            Text(
                text = shown.title,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = shown.destination?.let { stringResource(R.string.browser_into, it) } ?: shown.unavailable,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Button(onClick = shown.onConfirm, enabled = shown.destination != null, modifier = Modifier.padding(end = 4.dp)) {
            Text(shown.confirmLabel)
        }
    }
}

// ---- Previews ----

/** Three shared files on their way into a folder; and items being moved, over a folder they can't go into. */
@ThemePreviews
@Composable
private fun DestinationBarPreview() {
    PreviewTheme {
        Column {
            DestinationBar(DestinationRequest("3 shared files", "Holiday photos", "", "Upload here", "", {}, {}))
            DestinationBar(DestinationRequest("Moving 2 items", null, "Already in this folder", "Move here", "", {}, {}))
        }
    }
}
