package tools.senko.materialdrain.browser

import tools.senko.materialdrain.ui.LocalListLook
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import tools.senko.materialdrain.ui.icons.automirrored.filled.DriveFileMove
import tools.senko.materialdrain.ui.icons.filled.Delete
import tools.senko.materialdrain.ui.icons.filled.Download
import tools.senko.materialdrain.ui.icons.filled.SelectAll
import tools.senko.materialdrain.ui.icons.automirrored.filled.ArrowBack
import tools.senko.materialdrain.ui.components.PlaceholderList
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.remember
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import tools.senko.materialdrain.files.previewMimeType
import tools.senko.materialdrain.ui.media.addToQueue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import tools.senko.materialdrain.settings.LocalDataSaver
import tools.senko.materialdrain.files.SelectionAction
import tools.senko.materialdrain.files.SelectionActionBar
import tools.senko.materialdrain.files.SelectionBarHeight
import tools.senko.materialdrain.files.key
import tools.senko.materialdrain.files.shareLink
import tools.senko.materialdrain.provider.api.FileList
import tools.senko.materialdrain.provider.api.StorageNode
import tools.senko.materialdrain.provider.api.StorageRef
import tools.senko.materialdrain.ui.LocalBottomInset
import tools.senko.materialdrain.ui.components.DotScrollbar
import tools.senko.materialdrain.ui.components.CenteredTextMessage
import tools.senko.materialdrain.ui.components.ErrorMessage
import tools.senko.materialdrain.filesystem.PathSegment
import tools.senko.materialdrain.ui.preview.PreviewTheme
import tools.senko.materialdrain.ui.preview.ScreenPreviews
import tools.senko.materialdrain.ui.preview.PreviewSamples
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.res.stringResource
import tools.senko.materialdrain.R
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Immutable
import tools.senko.materialdrain.ui.LocalReduceMotion
import androidx.compose.animation.Crossfade

/**
 * The browser as it's drawn: the header of [mode] (the sorting, the path or the open list), then [entries] or why there
 * are none, and the selection's bar. Everything comes in as it is and what it does goes out through the callbacks, so a
 * preview can show it (see the end of the file); BrowserScreen works it all out from the ViewModels.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserContent(
    mode: BrowserMode,
    entries: List<StorageNode>,
    isLoading: Boolean,
    refreshing: Boolean,
    onRefresh: () -> Unit,
    onRetry: () -> Unit,
    errorMessage: String?,
    keyMissing: Boolean,
    filterQuery: String,
    onCancelTransfer: (() -> Unit)?,
    pathSegments: List<PathSegment>,
    onPathSegmentClick: (PathSegment) -> Unit,
    isModifying: Boolean,
    uploadStatus: String?,
    openedList: FileList?,
    onCloseList: () -> Unit,
    selectionMode: Boolean,
    selectedKeys: Set<String>,
    selectedCount: Int,
    selectionActions: List<SelectionAction>,
    onExitSelection: () -> Unit,
    canWriteFs: Boolean,
    canDeleteFs: Boolean,
    canDelete: Boolean,
    listCanEdit: Boolean,
    thumbnailFor: (StorageNode) -> String?,
    shareUrlFor: (StorageNode) -> String?,
    onOpen: (StorageNode) -> Unit,
    rowActions: BrowserRowActions,
    /** What's shown (a folder, a list), and how deep it is: another one slides in, see the body */
    pageKey: String = mode.name,
    pageDepth: Int = 0,
    /** The scroll position of each page, by its [pageKey] */
    scrollStateFor: (pageKey: String) -> LazyListState,
    fabHeight: Dp,
    isFabVisible: Boolean,
    /** What waits at the bottom for its place, in the selection bar's place (see DestinationBar) */
    destinationBar: DestinationRequest? = null
) {
    val context = LocalContext.current
    val pullRefreshState = rememberPullToRefreshState()
    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = onRefresh,
        state = pullRefreshState,
        modifier = Modifier.fillMaxSize()
    ) {
        // The thumbnails the data saver holds back, all at once (see DataSaver); null while none are held back
        val loadThumbnails = LocalDataSaver.current.let { if (it.holdThumbnails) it.loadAllThumbnails else null }
        Column(modifier = Modifier.fillMaxSize()) {
            // Header: the sorting and filter of whichever mode's list is sortable (every mode except the lists overview,
            // which is just titles), then right above the content the path (filesystem) or the list being shown (lists)
            when (mode) {
                BrowserMode.FILESYSTEM -> {
                    StatusCards(onCancelTransfer = onCancelTransfer, onLoadThumbnails = loadThumbnails)
                    PathBreadcrumb(
                        pathSegments = pathSegments,
                        onPathSegmentClick = onPathSegmentClick
                    )
                }
                BrowserMode.LISTS -> if (openedList != null) {
                    StatusCards(onCancelTransfer = onCancelTransfer, onLoadThumbnails = loadThumbnails)
                    // Its title and back to the lists; what's done with the whole list (its downloads) is in the ⋮ menu at
                    // the top, so the title has the row
                    if (!selectionMode) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 16.dp, top = 4.dp, bottom = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            IconButton(onClick = onCloseList) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.browser_back_to_the_lists))
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(openedList.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text(
                                    pluralStringResource(R.plurals.common_files, openedList.fileCount, openedList.fileCount),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
                BrowserMode.FILES -> StatusCards(onCancelTransfer = onCancelTransfer, onLoadThumbnails = loadThumbnails)
            }

            if (isModifying && mode == BrowserMode.FILESYSTEM) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            if (mode == BrowserMode.FILESYSTEM) {
                uploadStatus?.let { status ->
                    // Cancelled with the Cancel card in the sort row above
                    Text(
                        text = status,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)
                    )
                }
            }
            HorizontalDivider()

            // Body
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            // A folder (or list) opened slides in over the one left, which keeps what it showed as it goes: into a folder
            // from the right, back up from the left, the way Material's shared axis moves. Its own changes (loaded,
            // refreshed) don't slide
            val reduceMotion = LocalReduceMotion.current
            // What each page showed last: the one sliding out keeps showing it. Only which page it is goes to the slide, so
            // the page's own changes while it slides (its listing coming) don't start the slide over
            val current = BrowserPage(entries, isLoading, errorMessage, keyMissing)
            val shownLast = remember { HashMap<String, BrowserPage>() }
            shownLast[pageKey] = current
            AnimatedContent(
                targetState = PageSlot(pageKey, pageDepth),
                contentKey = { it.key },
                transitionSpec = {
                    val direction = targetState.depth.compareTo(initialState.depth)
                    when {
                        reduceMotion || direction == 0 -> fadeIn(tween(PAGE_FADE_MS)) togetherWith fadeOut(tween(PAGE_FADE_MS))
                        else -> (slideInHorizontally(tween(PAGE_SLIDE_MS, easing = EaseOutCubic)) { width -> direction * width / 4 } +
                            fadeIn(tween(PAGE_SLIDE_MS - PAGE_FADE_OUT_MS, delayMillis = PAGE_FADE_OUT_MS))) togetherWith
                            (slideOutHorizontally(tween(PAGE_SLIDE_MS, easing = EaseOutCubic)) { width -> -direction * width / 4 } +
                                fadeOut(tween(PAGE_FADE_OUT_MS)))
                    }
                },
                label = "browserPage",
                modifier = Modifier.fillMaxSize()
            ) { slot ->
            // The page's own, not the screen's now: a page sliding out shows what it had
            val page = if (slot.key == pageKey) current else shownLast[slot.key] ?: current
            val entries = page.entries
            val isLoading = page.isLoading
            val errorMessage = page.errorMessage
            val keyMissing = page.keyMissing
            val scrollState = scrollStateFor(slot.key)
            val body = when {
                // Rows of the shape of the files to come, until they're there (see PlaceholderList): at once, as the page
                // fades in, and faded into the files when they come quickly
                isLoading && entries.isEmpty() && !keyMissing && errorMessage == null -> PageBody.PLACEHOLDERS
                keyMissing -> PageBody.KEY_MISSING
                errorMessage != null && entries.isEmpty() -> PageBody.ERROR
                entries.isEmpty() && !isLoading -> PageBody.EMPTY
                else -> PageBody.LIST
            }
            // The placeholder rows fade into the files when they come (or into why there are none)
            Crossfade(body, animationSpec = tween(if (reduceMotion) 0 else BODY_FADE_MS), label = "pageBody") { shownBody ->
            when (shownBody) {
                PageBody.PLACEHOLDERS -> PullableFill {
                    PlaceholderList()
                }
                PageBody.KEY_MISSING -> PullableFill {
                    ErrorMessage(errorMessage ?: stringResource(R.string.browser_log_in_or_add_an))
                }
                PageBody.ERROR -> PullableFill {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(errorMessage.orEmpty(), color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge)
                        TextButton(onClick = onRetry) { Text(stringResource(R.string.browser_retry)) }
                    }
                }
                PageBody.EMPTY -> PullableFill {
                    CenteredTextMessage(emptyText(mode, openedList != null, filterQuery))
                }
                PageBody.LIST -> BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    // One entry, as a row or as a tile of the grid (Look and feel)
                    val entry: @Composable (StorageNode, Boolean, Modifier) -> Unit = { node, asTile, entryModifier ->
                            BrowserEntry(
                                node = node,
                                asTile = asTile,
                                modifier = entryModifier,
                                mode = mode,
                                openedList = openedList,
                                selectionMode = selectionMode,
                                selected = node.key in selectedKeys,
                                canWriteFs = canWriteFs,
                                canDeleteFs = canDeleteFs,
                                canDelete = canDelete,
                                listCanEdit = listCanEdit,
                                thumbnailUrl = if (node.isDirectory) null else thumbnailFor(node),
                                shareUrl = shareUrlFor(node),
                                onOpen = { onOpen(node) },
                                onToggleSelection = { rowActions.toggleSelection(node) },
                                onLongClick = { rowActions.startSelecting(node) },
                                onDownload = { style -> rowActions.download(node, style) },
                                downloadHasChoices = rowActions.downloadHasChoices(node),
                                onShareFile = if (node.isDirectory) null else { { rowActions.shareFile(node) } },
                                onRename = { rowActions.rename(node) },
                                onMove = { rowActions.move(node) },
                                onDelete = { rowActions.delete(node) },
                                onAddToFilesystem = { rowActions.addToFilesystem(node) },
                                onAddToList = { rowActions.addToList(node) },
                                onRemoveFromList = { rowActions.removeFromList(node) },
                                onShareLink = { url -> shareLink(context, url) },
                                // Only a song: the rest can't be played in the background
                                onAddToQueue = rowActions.addToQueue
                                    ?.takeIf { !node.isDirectory && node.previewMimeType()?.startsWith("audio/") == true }
                                    ?.let { add -> { add(node) } }
                            )
                    }
                    val grid = LocalListLook.current.grid
                    // As many tiles to a row as fit at about 120dp each, two at the least
                    val columns = (maxWidth / GRID_TILE_WIDTH).toInt().coerceAtLeast(2)
                    LazyColumn(
                        state = scrollState,
                        modifier = Modifier.fillMaxSize(),
                        // Past the last file, only the room for what covers the end of the list: the system's navigation
                        // bar or the miniplayer (LocalBottomInset), the upload button, and the bar floating over the list
                        // (the selection's, or one waiting for a place). Nothing more, so a list which fits doesn't scroll
                        contentPadding = PaddingValues(
                            bottom = LocalBottomInset.current +
                                (if (isFabVisible) fabHeight + 16.dp else 0.dp) +
                                (if (selectionMode || destinationBar != null) SelectionBarHeight else 0.dp)
                        )
                    ) {
                        if (grid) {
                            // The grid is rows of tiles, so it scrolls (and keeps its place) with the same state as the list
                            items(entries.chunked(columns), key = { row -> "row:" + row.first().key }) { row ->
                                // Clear of the scrollbar's dot at the end (see DotScrollbar), which sat over the tiles there
                                Row(modifier = Modifier.fillMaxWidth().padding(start = 6.dp, end = GRID_SCROLLBAR_ROOM)) {
                                    row.forEach { node -> entry(node, true, Modifier.weight(1f)) }
                                    repeat(columns - row.size) { Spacer(modifier = Modifier.weight(1f)) }
                                }
                            }
                        } else {
                            items(entries, key = { it.key }) { node ->
                                entry(node, false, Modifier)
                                HorizontalDivider(
                                    thickness = 0.5.dp,
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                )
                            }
                        }
                    }
                    DotScrollbar(state = scrollState)
                }
            }
            }
            }
            // Floating over the end of the list, which shows around them as it does around the miniplayer (the list
            // leaves room for them after its last entry, above)
            Box(modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
                SelectionActionBar(
                    visible = selectionMode,
                    selectedCount = selectedCount,
                    onClose = onExitSelection,
                    actions = selectionActions
                )
                DestinationBar(destinationBar)
            }
            }
        }
    }
}

/**
 * Fills the space below the header with [content] and keeps it pull-to-refresh-able. The pull gesture needs something
 * scrollable underneath it, so an empty or failed view is a one-item list which fills the screen.
 */
@Composable
private fun PullableFill(content: @Composable () -> Unit) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            Box(modifier = Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) { content() }
        }
    }
}

@Composable
private fun emptyText(mode: BrowserMode, inList: Boolean, filter: String): String = when {
    filter.isNotBlank() -> stringResource(R.string.app_no_matches_for, filter)
    mode == BrowserMode.LISTS && !inList -> stringResource(R.string.app_no_lists_found)
    mode == BrowserMode.LISTS -> stringResource(R.string.app_this_list_has_no_files)
    mode == BrowserMode.FILES -> stringResource(R.string.app_no_files)
    else -> stringResource(R.string.app_this_folder_is_empty)
}

/** Which page the body shows (a folder, a list), and how deep it is: what the slide goes by (see BrowserContent). */
@Immutable
private data class PageSlot(val key: String, val depth: Int)

/** What the body shows of one folder or list, as it was: kept by the page while it slides out (see BrowserContent). */
@Immutable
private class BrowserPage(
    val entries: List<StorageNode>,
    val isLoading: Boolean,
    val errorMessage: String?,
    val keyMissing: Boolean
)

/** What the body of a page is: rows to come, why there's nothing, or the files. */
private enum class PageBody { PLACEHOLDERS, KEY_MISSING, ERROR, EMPTY, LIST }

/** How long the body of a page takes to fade into another (the rows to come into the files). */
private const val BODY_FADE_MS = 180

/** How long a page slides for, and how soon the one left has faded out of it. */
private const val PAGE_SLIDE_MS = 300
private const val PAGE_FADE_OUT_MS = 90

/** With reduced animations, or to a page as deep as the one left: a crossfade. */
private const val PAGE_FADE_MS = 100

/** How wide a tile of the grid is about to be; the row gets as many as fit. */
private val GRID_TILE_WIDTH = 120.dp

/** The room the grid leaves at its end for the scrollbar's dot (see DotScrollbar), which is drawn this far in at most. */
private val GRID_SCROLLBAR_ROOM = 14.dp

// ---- Previews ----

/** The browser in [mode] showing [entries], in the previews' theme. */
@Composable
private fun BrowserPreview(
    mode: BrowserMode,
    entries: List<StorageNode> = PreviewSamples.files,
    isLoading: Boolean = false,
    errorMessage: String? = null,
    keyMissing: Boolean = false,
    openedList: FileList? = null,
    selectedKeys: Set<String>? = null,
    uploadStatus: String? = null
) {
    PreviewTheme { SampleBrowserContent(mode, entries, isLoading, errorMessage, keyMissing, openedList, selectedKeys, uploadStatus) }
}

/**
 * The browser in [mode] showing [entries], as BrowserScreen would draw it, for a preview (here and in App.kt's previews
 * of the whole screen); nothing happens when it's used. Not for the app itself.
 */
@Composable
internal fun SampleBrowserContent(
    mode: BrowserMode,
    entries: List<StorageNode> = PreviewSamples.files,
    isLoading: Boolean = false,
    errorMessage: String? = null,
    keyMissing: Boolean = false,
    openedList: FileList? = null,
    selectedKeys: Set<String>? = null,
    uploadStatus: String? = null
) {
    val selecting = selectedKeys != null
    BrowserContent(
        mode = mode,
        entries = entries,
        isLoading = isLoading,
        refreshing = isLoading,
        onRefresh = {},
        onRetry = {},
        errorMessage = errorMessage,
        keyMissing = keyMissing,
        filterQuery = "",
        onCancelTransfer = if (uploadStatus != null) ({}) else null,
        pathSegments = listOf("me", "Holiday photos").runningReduce { a, b -> "$a/$b" }.map { PathSegment(it.substringAfterLast('/'), it) },
        onPathSegmentClick = {},
        isModifying = false,
        uploadStatus = uploadStatus,
        openedList = openedList,
        onCloseList = {},
        selectionMode = selecting,
        selectedKeys = selectedKeys.orEmpty(),
        selectedCount = selectedKeys?.size ?: 0,
        selectionActions = listOf(
            SelectionAction(stringResource(R.string.browser_download), Icons.Filled.Download, {}),
            SelectionAction(stringResource(R.string.browser_move), Icons.AutoMirrored.Filled.DriveFileMove, {}),
            SelectionAction(stringResource(R.string.browser_delete), Icons.Filled.Delete, {}, destructive = true),
            SelectionAction(stringResource(R.string.browser_select_all), Icons.Filled.SelectAll, {})
        ),
        onExitSelection = {},
        canWriteFs = true,
        canDeleteFs = true,
        canDelete = true,
        listCanEdit = true,
        thumbnailFor = { null },
        shareUrlFor = { "https://pixeldrain.com/u/${it.ref.id}" },
        onOpen = {},
        rowActions = BrowserRowActions(),
        scrollStateFor = rememberLazyListState().let { state -> { state } },
        fabHeight = 0.dp,
        isFabVisible = false
    )
}

/** The Files tab: the account's files. */
@ScreenPreviews
@Composable
private fun BrowserFilesPreview() = BrowserPreview(BrowserMode.FILES, PreviewSamples.files.filter { !it.isDirectory })

/** The Filesystem tab inside a folder: its path, the New menu, and an upload under way. */
@ScreenPreviews
@Composable
private fun BrowserFilesystemPreview() = BrowserPreview(BrowserMode.FILESYSTEM, uploadStatus = "Uploading drone-flight.mp4 (2/5)")

/** The Lists tab: the lists as folders. */
@ScreenPreviews
@Composable
private fun BrowserListsPreview() = BrowserPreview(
    BrowserMode.LISTS,
    listOf("Holiday 2026", "Shared with Sam", "Wallpapers").map { StorageNode(StorageRef(id = it), it, isDirectory = true) }
)

/** A list opened: its title and Back (its downloads are in the ⋮ menu at the top). */
@ScreenPreviews
@Composable
private fun BrowserOpenedListPreview() = BrowserPreview(
    BrowserMode.LISTS,
    PreviewSamples.files.filter { !it.isDirectory }.take(5),
    openedList = FileList("a", stringResource(R.string.browser_holiday_2026), fileCount = 5, canEdit = true)
)

/** Selecting: the checkboxes, and the selection's bar at the bottom. */
@ScreenPreviews
@Composable
private fun BrowserSelectingPreview() = BrowserPreview(
    BrowserMode.FILESYSTEM,
    selectedKeys = PreviewSamples.files.filterIndexed { index, _ -> index in setOf(1, 2, 4) }.mapTo(HashSet()) { it.key }
)

@ScreenPreviews
@Composable
private fun BrowserEmptyPreview() = BrowserPreview(BrowserMode.FILESYSTEM, entries = emptyList())

@ScreenPreviews
@Composable
private fun BrowserErrorPreview() = BrowserPreview(BrowserMode.FILES, entries = emptyList(), errorMessage = "The host couldn't be reached: timeout after 30 seconds.")

@ScreenPreviews
@Composable
private fun BrowserLoadingPreview() = BrowserPreview(BrowserMode.FILES, entries = emptyList(), isLoading = true)

/** Pixeldrain without its login: what the screen asks for. */
@ScreenPreviews
@Composable
private fun BrowserKeyMissingPreview() = BrowserPreview(BrowserMode.FILES, entries = emptyList(), keyMissing = true)
