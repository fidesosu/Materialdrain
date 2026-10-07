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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
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
        FileIcon(name = name, isDirectory = type == "dir", thumbnailUrl = thumbnailUrl, size = 56.dp)

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

            val details = fileDetails(name, isDirectory = type == "dir", fileSize = fileSize, modified = modified)

            if (details.isNotEmpty()) {
                Text(
                    text = details,
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

/**
 * The picture of a file or folder: a folder icon, the file's thumbnail when it has one, or a plain file icon. The
 * thumbnail is decoded at [size], not at the size of the original image.
 */
@Composable
fun FileIcon(name: String, isDirectory: Boolean, thumbnailUrl: String?, size: Dp, cornerRadius: Dp = 6.dp) {
    val iconModifier = Modifier
        .size(size)
        .clip(RoundedCornerShape(cornerRadius))
    when {
        isDirectory -> Icon(imageVector = Icons.Filled.Folder, contentDescription = "Folder", modifier = iconModifier)
        thumbnailUrl != null -> {
            val context = LocalContext.current
            // Built once per URL and login: a new request on every recomposition makes Coil load the thumbnail again
            val headers = HostRequestAuth.headersFor(thumbnailUrl)
            val thumbnailPx = with(LocalDensity.current) { size.roundToPx() }
            val request = remember(thumbnailUrl, headers, thumbnailPx) {
                ImageRequest.Builder(context)
                    .data(thumbnailUrl)
                    .size(thumbnailPx)
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
        else -> Icon(imageVector = Icons.AutoMirrored.Filled.InsertDriveFile, contentDescription = "File", modifier = iconModifier)
    }
}

/** The details line of a file or folder: "Folder", or its size and extension; then [modified] when given. */
fun fileDetails(name: String, isDirectory: Boolean, fileSize: Long?, modified: String? = null): String =
    buildList {
        if (isDirectory) add("Folder") else {
            fileSize?.let { add(formatSize(it)) }
            extensionLabel(name)?.let { add(it) }
        }
        modified?.let { add(it) }
    }.joinToString(" • ")

/** The file extension in capitals ("PDF"), for the details line. Null when the name has none or it is not a short one. */
private fun extensionLabel(name: String): String? =
    name.substringAfterLast('.', "").takeIf { it.isNotEmpty() && it.length <= 5 && it != name }?.uppercase()
