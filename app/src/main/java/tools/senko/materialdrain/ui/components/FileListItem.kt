package tools.senko.materialdrain.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import tools.senko.materialdrain.ui.media.HostRequestAuth
import tools.senko.materialdrain.util.formatSize

/**
 * A row of a file or folder. This is a plain row instead of a Material ListItem, whose trailing area
 * reserves far more width than the small menu button needs and takes it away from the file name.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileListItem(
    name: String,
    type: String,
    fileSize: Long? = null,
    modified: String? = null,
    mimeType: String? = null,
    thumbnailUrl: String? = null,
    trailingContent: (@Composable () -> Unit)? = null,
    selectionMode: Boolean = false,
    selected: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .heightIn(min = 72.dp)
            .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val iconModifier = Modifier
            .size(56.dp)
            .clip(RoundedCornerShape(6.dp))

        when {
            type == "dir" -> {
                Icon(
                    imageVector = Icons.Filled.Folder,
                    contentDescription = "Folder",
                    modifier = iconModifier
                )
            }

            thumbnailUrl != null -> {
                val context = LocalContext.current
                // Built once per URL and login: a new request on every recomposition makes Coil load the thumbnail again
                val headers = HostRequestAuth.headersFor(thumbnailUrl)
                val request = remember(thumbnailUrl, headers) {
                    ImageRequest.Builder(context)
                        .data(thumbnailUrl)
                        .crossfade(true)
                        .apply { headers.forEach { (name, value) -> addHeader(name, value) } }
                        .build()
                }

                AsyncImage(
                    model = request,
                    contentDescription = "$name thumbnail",
                    contentScale = ContentScale.Crop,
                    modifier = iconModifier,
                    placeholder = rememberVectorPainter(Icons.AutoMirrored.Filled.InsertDriveFile),
                    error = rememberVectorPainter(Icons.Filled.BrokenImage)
                )
            }

            else -> {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.InsertDriveFile,
                    contentDescription = "File",
                    modifier = iconModifier
                )
            }
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = name,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            val details = mutableListOf<String>()

            if (type == "dir") {
                details.add("Folder")
            } else {
                fileSize?.let { details.add(formatSize(it)) }
                mimeType?.let { if (it.isNotBlank()) details.add(it) }
            }

            modified?.let { details.add("Modified: $it") }

            if (details.isNotEmpty()) {
                Text(
                    text = details.joinToString(" • "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // While selecting, the item menu makes way for a checkbox
        if (selectionMode) {
            Box(modifier = Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                Checkbox(checked = selected, onCheckedChange = null)
            }
        } else if (trailingContent != null) {
            trailingContent()
        } else {
            Spacer(modifier = Modifier.width(12.dp))
        }
    }
}
