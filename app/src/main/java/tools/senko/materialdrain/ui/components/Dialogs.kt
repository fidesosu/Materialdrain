package tools.senko.materialdrain.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import tools.senko.materialdrain.api.ApiResponse
import tools.senko.materialdrain.api.FilesystemEntry

@Composable
fun TextInputDialog(
    title: String,
    label: String,
    confirmLabel: String,
    initialValue: String = "",
    supportingText: String? = null,
    singleLine: Boolean = true,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var value by remember { mutableStateOf(initialValue) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    label = { Text(label) },
                    singleLine = singleLine,
                    minLines = if (singleLine) 1 else 3
                )
                supportingText?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
                }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(value) }, enabled = value.isNotBlank()) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    confirmLabel: String,
    isDestructive: Boolean = false,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = if (isDestructive) ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error) else ButtonDefaults.buttonColors()
            ) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
/** Lets the user browse the folders of the filesystem and pick one. Paths look like "me/photos". */
@Composable
fun FolderPickerDialog(
    title: String,
    confirmLabel: String,
    initialPath: String,
    excludedPaths: Set<String> = emptySet(),
    loadFolders: suspend (path: String) -> ApiResponse<List<FilesystemEntry>>,
    onConfirm: (path: String) -> Unit,
    onDismiss: () -> Unit
) {
    var path by remember { mutableStateOf(initialPath.trim('/').ifEmpty { "me" }) }
    var folders by remember { mutableStateOf<List<FilesystemEntry>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(path) {
        folders = null
        error = null
        when (val response = loadFolders(path)) {
            is ApiResponse.Success -> folders = response.data
            is ApiResponse.Error -> error = response.errorDetails.message ?: response.errorDetails.value ?: "Could not load this folder."
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Text(
                    text = path,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 160.dp, max = 300.dp)
                        .padding(top = 8.dp)
                ) {
                    val shownFolders = folders
                    when {
                        error != null -> Text(error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                        shownFolders == null -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                        else -> LazyColumn {
                            if (path.contains('/')) {
                                item {
                                    ListItem(
                                        headlineContent = { Text("Parent folder") },
                                        leadingContent = { Icon(Icons.Filled.ArrowUpward, contentDescription = null) },
                                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                        modifier = Modifier.clickable { path = path.substringBeforeLast('/') }
                                    )
                                }
                            }
                            val selectable = shownFolders.filter { it.path.trim('/') !in excludedPaths }
                            items(selectable, key = { it.path }) { folder ->
                                ListItem(
                                    headlineContent = { Text(folder.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                    leadingContent = { Icon(Icons.Filled.Folder, contentDescription = null) },
                                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                    modifier = Modifier.clickable { path = folder.path.trim('/') }
                                )
                            }
                            if (selectable.isEmpty()) {
                                item { Text("No subfolders", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(16.dp)) }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(path) }, enabled = error == null) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
