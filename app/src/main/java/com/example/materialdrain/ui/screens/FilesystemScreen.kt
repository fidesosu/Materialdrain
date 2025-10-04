package com.example.materialdrain.ui.screens

import android.app.Activity
import android.util.Log
import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile // Changed import
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.Folder
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
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.materialdrain.network.FilesystemEntry
import com.example.materialdrain.ui.formatApiDateTimeString
import com.example.materialdrain.ui.formatSize
import com.example.materialdrain.viewmodel.FileInfoViewModel
import com.example.materialdrain.viewmodel.FilesystemViewModel
import com.example.materialdrain.viewmodel.PathSegment
import io.ktor.http.encodeURLPathPart

private const val TAG_FILESYSTEM_SCREEN = "FilesystemScreen"

@Composable
fun PathBreadcrumb(
    pathSegments: List<PathSegment>,
    onPathSegmentClick: (segment: PathSegment) -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    LaunchedEffect(pathSegments, scrollState.maxValue) {
        if (pathSegments.isNotEmpty()) {
            scrollState.animateScrollTo(scrollState.maxValue)
        }
    }

    if (pathSegments.isEmpty()) {
        Text(
            text = "Storage",
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
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            )
            if (index < pathSegments.lastIndex) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                    contentDescription = "Path separator",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilesystemScreen(
    filesystemViewModel: FilesystemViewModel,
    fileInfoViewModel: FileInfoViewModel,
    onFileSelected: () -> Unit,
    fabHeight: Dp,
    isFabVisible: Boolean
) {
    val uiState by filesystemViewModel.uiState.collectAsState()
    val pullRefreshState = rememberPullToRefreshState()
    val context = LocalContext.current

    BackHandler(enabled = true) {
        val navigatedUp = filesystemViewModel.navigateToParentPath()
        if (!navigatedUp) {
            // If already at root, finish the activity to exit
            (context as? Activity)?.finish()
        }
    }

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
                        FilesystemEntryItem(
                            entry = entry,
                            apiKey = uiState.apiKey, // Pass the API key from the ViewModel
                            onClick = {
                                if (entry.type == "file") {
                                    fileInfoViewModel.setFileInfoFromFilesystemEntry(entry)
                                    onFileSelected()
                                } else {
                                    filesystemViewModel.navigateToChild(entry)
                                }
                            }
                        )
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
    apiKey: String, // Accept API key as a parameter
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
            val imageModifier = Modifier
                .size(48.dp) // Adjusted size to match thumbnail request
                .clip(RoundedCornerShape(4.dp))

            if (entry.type == "file") {
                val context = LocalContext.current
                val finalThumbnailUrl: String
                var isFilesystemApiThumbnailUrl = false

                if (!entry.thumbnailHref.isNullOrBlank()) {
                    finalThumbnailUrl = "https://pixeldrain.com${entry.thumbnailHref.trim()}"
                    Log.d(TAG_FILESYSTEM_SCREEN, "Using thumbnailHref for ${entry.name}: $finalThumbnailUrl")
                    if (finalThumbnailUrl.startsWith("https://pixeldrain.com/api/filesystem/") && finalThumbnailUrl.contains("?thumbnail")) {
                        isFilesystemApiThumbnailUrl = true
                        Log.d(TAG_FILESYSTEM_SCREEN, "thumbnailHref is a filesystem API thumbnail URL: $finalThumbnailUrl")
                    }
                } else {
                    val cleanedPath = entry.path.removePrefix("/").split('/').filter { it.isNotEmpty() }
                    val encodedPathSegments = cleanedPath.joinToString("/") { it.encodeURLPathPart() }

                    if (encodedPathSegments.isNotEmpty()) {
                        finalThumbnailUrl = "https://pixeldrain.com/api/filesystem/${encodedPathSegments}?thumbnail&width=48&height=48"
                        isFilesystemApiThumbnailUrl = true 
                        Log.d(TAG_FILESYSTEM_SCREEN, "Using constructed filesystem path for ${entry.name}: $finalThumbnailUrl (Original entry.path: '${entry.path}')")
                    } else {
                        Log.w(TAG_FILESYSTEM_SCREEN, "Could not construct thumbnail URL for ${entry.name} from problematic path: '${entry.path}'")
                        finalThumbnailUrl = "invalid_path_for_thumbnail"
                    }
                }

                if (finalThumbnailUrl != "invalid_path_for_thumbnail") {
                    val requestBuilder = ImageRequest.Builder(context)
                        .data(finalThumbnailUrl)
                        .crossfade(true)
                        .listener(
                            onStart = { request ->
                                Log.d(TAG_FILESYSTEM_SCREEN, "Coil request started for: ${request.data}")
                            },
                            onSuccess = { request, result ->
                                Log.d(TAG_FILESYSTEM_SCREEN, "Coil request SUCCESS for: ${request.data} - from datasource: ${result.dataSource}")
                            },
                            onError = { request, result ->
                                Log.e(TAG_FILESYSTEM_SCREEN, "Coil request ERROR for: ${request.data} - ${result.throwable.localizedMessage}", result.throwable)
                            }
                        )

                    if (isFilesystemApiThumbnailUrl && apiKey.isNotBlank()) {
                        requestBuilder.addHeader("Cookie", "pd_auth_key=$apiKey")
                        Log.d(TAG_FILESYSTEM_SCREEN, "Added Cookie header for: $finalThumbnailUrl (pd_auth_key=...)")
                    } else if (isFilesystemApiThumbnailUrl && apiKey.isBlank()) {
                        Log.w(TAG_FILESYSTEM_SCREEN, "API Key is blank. Filesystem API thumbnail might fail for: $finalThumbnailUrl")
                    }

                    AsyncImage(
                        model = requestBuilder.build(),
                        contentDescription = "${entry.name} thumbnail",
                        modifier = imageModifier,
                        contentScale = ContentScale.Crop,
                        placeholder = rememberVectorPainter(Icons.AutoMirrored.Filled.InsertDriveFile), // Changed Icon
                        error = rememberVectorPainter(Icons.Filled.BrokenImage)
                    )
                } else {
                     Icon(
                        painter = rememberVectorPainter(Icons.Filled.BrokenImage),
                        contentDescription = "${entry.name} thumbnail error (invalid path)",
                        modifier = imageModifier
                    )
                }
            } else { // entry.type == "dir"
                Icon(
                    imageVector = Icons.Filled.Folder,
                    contentDescription = "Folder",
                    modifier = imageModifier 
                )
            }
        },
        modifier = Modifier.clickable(onClick = onClick)
    )
}
