package com.example.materialdrain.components

import android.util.Log
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.materialdrain.ui.formatSize

private const val TAG_FILE_LIST_ITEM = "FileListItem"

@Composable
fun FileListItem(
    name: String,
    type: String,
    fileSize: Long? = null,
    modified: String? = null,
    mimeType: String? = null,
    thumbnailUrl: String? = null,
    apiKey: String = "",
    onClick: () -> Unit
) {

    ListItem(
        modifier = Modifier.clickable(onClick = onClick),

        headlineContent = {
            Text(
                text = name,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },

        supportingContent = {

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
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        },

        leadingContent = {

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

                    val requestBuilder = ImageRequest.Builder(LocalContext.current)
                        .data(thumbnailUrl)
                        .crossfade(true)

                    if (thumbnailUrl.contains("pixeldrain.com/api/filesystem") && apiKey.isNotBlank()) {
                        requestBuilder.addHeader("Cookie", "pd_auth_key=$apiKey")
                        Log.d(TAG_FILE_LIST_ITEM, "Adding auth header for $thumbnailUrl")
                    }

                    AsyncImage(
                        model = requestBuilder.build(),
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
        }
    )
}