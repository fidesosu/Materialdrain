package tools.senko.materialdrain.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import tools.senko.materialdrain.provider.api.ApiResponse
import tools.senko.materialdrain.provider.api.StorageNode

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
/**
 * Lets the user browse the folders of the filesystem and pick one: a path along the top (each folder in it tappable),
 * the folders of the open one below, in a list of a fixed height so the dialog never changes size while browsing. Going
 * into a folder slides the list in from the side, going back slides it the other way. Back goes up a folder before it
 * closes the dialog.
 *
 * @param rootPath the top of the host's folders ("me" on Pixeldrain, "" for an SMB share's top); the picker stays in it
 * @param rootName what the path calls [rootPath]
 */
@Composable
fun FolderPickerDialog(
    title: String,
    confirmLabel: String,
    initialPath: String,
    rootPath: String = "me",
    rootName: String = rootPath.substringAfterLast('/'),
    excludedPaths: Set<String> = emptySet(),
    loadFolders: suspend (path: String) -> ApiResponse<List<StorageNode>>,
    onConfirm: (path: String) -> Unit,
    onDismiss: () -> Unit
) {
    val root = rootPath.trim('/')
    var path by remember { mutableStateOf(initialPath.trim('/').ifEmpty { root }) }
    // Each folder's listing, kept while the dialog is open: going back shows it at once
    val listings = remember { mutableStateMapOf<String, FolderListing>() }
    LaunchedEffect(path) {
        if (listings[path] is FolderListing.Loaded) return@LaunchedEffect
        listings[path] = FolderListing.Loading
        listings[path] = when (val response = loadFolders(path)) {
            is ApiResponse.Success -> FolderListing.Loaded(response.data.filter { it.ref.path.trim('/') !in excludedPaths })
            is ApiResponse.Error -> FolderListing.Failed(response.error.message.ifBlank { "Could not load this folder." })
        }
    }

    // The path from the top: the root's own crumb, then each folder under it
    val crumbs = remember(path, root, rootName) {
        val inside = if (root.isEmpty()) path else path.removePrefix(root).trim('/')
        var built = root
        listOf(rootName.ifEmpty { "/" } to root) + inside.split('/').filter { it.isNotEmpty() }.map { name ->
            built = if (built.isEmpty()) name else "$built/$name"
            name to built
        }
    }
    val hasParent = crumbs.size > 1
    val goUp = { path = crumbs[crumbs.size - 2].second }

    Dialog(onDismissRequest = onDismiss) {
        // Back goes up a folder before it closes the dialog
        BackHandler(enabled = hasParent) { goUp() }
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(top = 24.dp, bottom = 16.dp)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 24.dp)
                )
                Spacer(Modifier.height(12.dp))
                PickerPath(crumbs = crumbs, onCrumbClick = { path = it })
                Spacer(Modifier.height(8.dp))

                // A fixed height, whatever the folder holds, so nothing moves while browsing
                Box(modifier = Modifier.fillMaxWidth().height(PickerListHeight)) {
                    AnimatedContent(
                        targetState = path,
                        transitionSpec = {
                            // Deeper slides in from the end, back up from the start
                            val deeper = targetState.length > initialState.length
                            val direction = if (deeper) 1 else -1
                            (slideInHorizontally(tween(220)) { it / 4 * direction } + fadeIn(tween(220))) togetherWith
                                (slideOutHorizontally(tween(220)) { -it / 4 * direction } + fadeOut(tween(160)))
                        },
                        label = "folderPickerList"
                    ) { shownPath ->
                        Box(modifier = Modifier.fillMaxSize()) {
                            when (val listing = listings[shownPath]) {
                                null, FolderListing.Loading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                                is FolderListing.Failed -> Text(
                                    listing.message,
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.align(Alignment.Center).padding(horizontal = 24.dp)
                                )
                                is FolderListing.Loaded -> if (listing.folders.isEmpty()) {
                                    Text(
                                        "No folders in here",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.align(Alignment.Center)
                                    )
                                } else {
                                    LazyColumn(contentPadding = PaddingValues(horizontal = 12.dp)) {
                                        items(listing.folders, key = { it.ref.path }) { folder ->
                                            PickerFolderRow(folder.name, onClick = { path = folder.ref.path.trim('/') })
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = { onConfirm(path) }, enabled = listings[path] !is FolderListing.Failed) { Text(confirmLabel) }
                }
            }
        }
    }
}

/** How tall the folder list of [FolderPickerDialog] always is: about six folders. */
private val PickerListHeight = 312.dp

private sealed interface FolderListing {
    data object Loading : FolderListing
    data class Loaded(val folders: List<StorageNode>) : FolderListing
    data class Failed(val message: String) : FolderListing
}

/** The picker's path: the folder that's open last and in bold; the ones before it lead back up. Scrolls to its end. */
@Composable
private fun PickerPath(crumbs: List<Pair<String, String>>, onCrumbClick: (String) -> Unit) {
    val scroll = rememberScrollState()
    LaunchedEffect(crumbs.size) { scroll.animateScrollTo(scroll.maxValue) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(scroll)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        crumbs.forEachIndexed { index, (name, crumbPath) ->
            val last = index == crumbs.lastIndex
            Text(
                name,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (last) FontWeight.Bold else FontWeight.Normal,
                color = if (last) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(enabled = !last) { onCrumbClick(crumbPath) }
                    .padding(horizontal = 4.dp, vertical = 6.dp)
            )
            if (!last) {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

/** A folder in the picker, with the same folder tile as the file list. */
@Composable
private fun PickerFolderRow(name: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FileIcon(name = name, isDirectory = true, thumbnailUrl = null, size = 36.dp)
        Spacer(Modifier.width(14.dp))
        Text(name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = "Open",
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
