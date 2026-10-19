package tools.senko.materialdrain.browser

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import tools.senko.materialdrain.files.SelectionBarHeight
import tools.senko.materialdrain.ui.LocalBottomInset
import tools.senko.materialdrain.ui.LocalReduceMotion
import tools.senko.materialdrain.ui.icons.filled.Close
import tools.senko.materialdrain.ui.preview.PreviewTheme
import tools.senko.materialdrain.ui.preview.ThemePreviews

/**
 * Files shared from another app, waiting in a Filesystem tab for the folder they go into (see App.kt): [onUpload] sends
 * them into the folder shown, [onCancel] drops them.
 */
class SharedUpload(val count: Int, val onUpload: () -> Unit, val onCancel: () -> Unit)

/**
 * The bar at the bottom of a Filesystem tab while [shared] files wait for a folder, in the selection bar's place and look
 * (see SelectionActionBar): the folder they'd go into ([folder]) and the button which uploads them there, while
 * [canUpload] (the folder can be written to and no other upload runs).
 */
@Composable
fun SharedUploadBar(shared: SharedUpload?, folder: String, canUpload: Boolean) {
    val reduceMotion = LocalReduceMotion.current
    AnimatedVisibility(
        visible = shared != null,
        enter = if (reduceMotion) fadeIn(animationSpec = tween(100)) else slideInVertically(animationSpec = tween(200)) { it } + fadeIn(animationSpec = tween(200)),
        exit = if (reduceMotion) fadeOut(animationSpec = tween(100)) else slideOutVertically(animationSpec = tween(200)) { it } + fadeOut(animationSpec = tween(150))
    ) {
        // Kept while it slides out, when the files are already gone
        val count = shared?.count ?: 0
        Surface(
            color = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            tonalElevation = 6.dp,
            shadowElevation = 8.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    // Its colour goes on under the system's navigation bar, its buttons stay above it
                    .padding(bottom = LocalBottomInset.current)
                    .height(SelectionBarHeight)
                    .padding(start = 4.dp, end = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { shared?.onCancel?.invoke() }) { Icon(Icons.Filled.Close, contentDescription = "Don't upload the shared files") }
                Column(modifier = Modifier.weight(1f).padding(horizontal = 4.dp)) {
                    Text(
                        text = if (count == 1) "1 shared file" else "$count shared files",
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (canUpload) "Into $folder" else "Open a folder that can be written to",
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Button(onClick = { shared?.onUpload?.invoke() }, enabled = canUpload) { Text("Upload here") }
            }
        }
    }
}

// ---- Previews ----

/** Three shared files on their way into a folder; and in one they can't go into. */
@ThemePreviews
@Composable
private fun SharedUploadBarPreview() {
    PreviewTheme {
        Column {
            SharedUploadBar(SharedUpload(3, {}, {}), folder = "Holiday photos", canUpload = true)
            SharedUploadBar(SharedUpload(1, {}, {}), folder = "Read only", canUpload = false)
        }
    }
}
