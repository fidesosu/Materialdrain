package com.example.materialdrain.ui.screens

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.Folder // Generic folder icon
import androidx.compose.material.icons.filled.InsertDriveFile // Generic file icon
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.materialdrain.network.FilesystemEntry
import com.example.materialdrain.ui.formatApiDateTimeString
import com.example.materialdrain.ui.formatSize
import com.example.materialdrain.viewmodel.FilesystemViewModel
import com.example.materialdrain.viewmodel.PathSegment

@Composable
fun PathBreadcrumb(
    pathSegments: List<PathSegment>,
    onPathSegmentClick: (segment: PathSegment) -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    // Auto-scroll to the end when pathSegments change or the content width changes
    LaunchedEffect(pathSegments, scrollState.maxValue) {
        if (pathSegments.isNotEmpty()) {
            scrollState.animateScrollTo(scrollState.maxValue)
        }
    }

    if (pathSegments.isEmpty()) {
        Text(
            text = "Storage", // Default root name for an empty path
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        )
        return
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        pathSegments.forEachIndexed { index, segment ->
            Text(
                text = segment.name,
                style = MaterialTheme.typography.titleSmall.copy(
                    color = if (index == pathSegments.lastIndex) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                ),
                fontWeight = if (index == pathSegments.lastIndex) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .clickable { onPathSegmentClick(segment) }
                    .padding(horizontal = 4.dp, vertical = 2.dp) // Adjusted padding for better touch targets in a row
            )
            if (index < pathSegments.lastIndex) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                    contentDescription = "Path separator",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp) // Ensure icon aligns well
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilesystemScreen(
    filesystemViewModel: FilesystemViewModel,
    fabHeight: Dp,
    isFabVisible: Boolean
) {
    val uiState by filesystemViewModel.uiState.collectAsState()
    val pullRefreshState = rememberPullToRefreshState()

    PullToRefreshBox(
        isRefreshing = uiState.isLoading,
        onRefresh = { filesystemViewModel.refreshCurrentPath() },
        state = pullRefreshState,
        modifier = Modifier.fillMaxSize(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
        ) {
            PathBreadcrumb(
                pathSegments = uiState.pathSegments,
                onPathSegmentClick = {
                    filesystemViewModel.navigateToPathSegment(it)
                }
            )
            HorizontalDivider()

            if (uiState.isLoading && uiState.children.isEmpty() && !uiState.apiKeyMissingError && uiState.errorMessage == null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (uiState.apiKeyMissingError) {
                 Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                    Text(
                        text = uiState.errorMessage ?: "API Key is missing. Please set it in Settings to browse the filesystem.",
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
            } else if (uiState.children.isEmpty() && !uiState.isLoading) {
                Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                    Text(
                        text = "This folder is empty.",
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(bottom = if (isFabVisible) fabHeight + 16.dp else 16.dp)
                ) {
                    items(uiState.children, key = { it.path }) { entry ->
                        FilesystemEntryItem(entry = entry, onClick = {
                            filesystemViewModel.navigateToChild(entry)
                        })
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
fun FilesystemEntryItem(
    entry: FilesystemEntry,
    onClick: () -> Unit
) {
    ListItem(
        headlineContent = { Text(entry.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            val details = mutableListOf<String>()
            if (entry.type == "dir") {
                details.add("Folder")
            } else {
                details.add(formatSize(entry.fileSize))
                entry.mimeType?.let { if(it.isNotBlank()) details.add(it) }
            }
            details.add("Modified: ${formatApiDateTimeString(entry.modified)}")
            Text(details.joinToString(" • "), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
        },
        leadingContent = {
            Icon(
                imageVector = if (entry.type == "dir") Icons.Filled.Folder else Icons.Filled.InsertDriveFile,
                contentDescription = if (entry.type == "dir") "Folder" else "File",
                modifier = Modifier.size(40.dp)
            )
        },
        modifier = Modifier.clickable(onClick = onClick)
    )
}
