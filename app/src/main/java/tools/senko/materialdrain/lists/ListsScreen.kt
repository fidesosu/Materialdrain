package tools.senko.materialdrain.lists

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import tools.senko.materialdrain.files.copyLinkToClipboard
import tools.senko.materialdrain.files.shareLink
import tools.senko.materialdrain.util.formatApiDateTimeString

@Composable
fun ListsScreenContent(
    listViewModel: ListViewModel,
    onListSelected: () -> Unit,
    fabHeight: Dp,
    isFabVisible: Boolean
) {
    val uiState by listViewModel.uiState.collectAsState()

    if (uiState.isLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    } else if (uiState.apiKeyMissingError) {
        Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
            Text(
                text = uiState.errorMessage ?: "API Key is missing. Please set it in Settings to browse your lists.",
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyLarge
            )
        }
    } else if (uiState.errorMessage != null) {
        Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
            Text(
                text = uiState.errorMessage!!,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyLarge
            )
        }
    } else if (uiState.lists.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
            Text(
                text = "No lists found.",
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyLarge
            )
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = if (isFabVisible) fabHeight + 16.dp else 16.dp)
        ) {
            items(uiState.lists) { list ->
                ListItem(
                    headlineContent = { Text(list.title) },
                    supportingContent = {
                        Text(
                            "Files: ${list.fileCount} • Created: ${formatApiDateTimeString(list.dateCreated)}"
                        )
                    },
                    trailingContent = {
                        val context = LocalContext.current
                        val url = "https://pixeldrain.com/l/${list.id}"
                        var menuExpanded by remember { mutableStateOf(false) }
                        Box {
                            IconButton(onClick = { menuExpanded = true }, modifier = Modifier.size(40.dp)) {
                                Icon(Icons.Filled.MoreVert, contentDescription = "More options for ${list.title}")
                            }
                            DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                                DropdownMenuItem(
                                    text = { Text("Open") },
                                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null) },
                                    onClick = { menuExpanded = false; listViewModel.openList(list); onListSelected() }
                                )
                                DropdownMenuItem(
                                    text = { Text("Copy link") },
                                    leadingIcon = { Icon(Icons.Filled.ContentCopy, contentDescription = null) },
                                    onClick = { menuExpanded = false; copyLinkToClipboard(context, url) }
                                )
                                DropdownMenuItem(
                                    text = { Text("Share link") },
                                    leadingIcon = { Icon(Icons.Filled.Share, contentDescription = null) },
                                    onClick = { menuExpanded = false; shareLink(context, url) }
                                )
                            }
                        }
                    },
                    modifier = Modifier.clickable {
                        listViewModel.openList(list)
                        onListSelected()
                    }
                )
                HorizontalDivider(
                    thickness = 0.5.dp, 
                    color = MaterialTheme.colorScheme.outlineVariant.copy(
                        alpha = 0.5f
                    )
                )
            }
        }
    }
}
