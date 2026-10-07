package tools.senko.materialdrain.browser

import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import tools.senko.materialdrain.filesystem.PathSegment
import tools.senko.materialdrain.provider.api.StorageNode
import tools.senko.materialdrain.files.SortOptions
import tools.senko.materialdrain.files.SortableField

/** The path at the top of the filesystem: each folder is tappable, and the row scrolls to the end of it. */
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

/** How wide the "Sort by" control is: just enough for its label and the longest field name. */
private val SortControlWidth = 140.dp

/**
 * The sort field and direction — shared by every sortable list in the app, see `AppSettings.sortField` — and, while
 * a search is active, a chip naming it with a clear button. Searching itself opens as a modal over the screen (the
 * magnifier at the top of the screen), instead of living in this row.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SortControls(
    sortField: SortableField,
    sortAscending: Boolean,
    onSortFieldSelected: (SortableField) -> Unit,
    activeFilterQuery: String = "",
    filteredCount: Int = 0,
    totalCount: Int = 0,
    onClearFilter: () -> Unit = {},
    /** Actions at the end of the row, behind a "New" menu, e.g. the filesystem's upload and new folder. */
    actions: List<SortRowAction> = emptyList()
) {
    var expanded by remember { mutableStateOf(false) }
    val currentSortName = SortOptions.find { it.second == sortField }?.first.orEmpty()
    val directionIcon = if (sortAscending) Icons.Filled.ArrowUpward else Icons.Filled.ArrowDownward
    val directionDescription = if (sortAscending) "Ascending" else "Descending"

    Row(
        modifier = Modifier
            .fillMaxWidth()
            // Lines up with the file rows below, which start at 16dp
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Sort: a dropdown. Reselecting the field already sorted on flips its direction (see
        // AppSettings.changeSortOrder); the direction otherwise only shows in the menu.
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded },
            modifier = Modifier.width(SortControlWidth)
        ) {
            OutlinedTextField(
                value = currentSortName,
                onValueChange = {},
                readOnly = true,
                singleLine = true,
                label = { Text("Sort by", maxLines = 1) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier
                    .menuAnchor(
                        type = ExposedDropdownMenuAnchorType.PrimaryNotEditable,
                        enabled = true
                    )
                    .fillMaxWidth()
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                SortOptions.forEach { (name, field) ->
                    val selected = field == sortField
                    DropdownMenuItem(
                        text = { Text(name) },
                        trailingIcon = if (selected) {
                            { Icon(directionIcon, contentDescription = directionDescription) }
                        } else null,
                        // The menu stays open, so the effect can be seen and the direction flipped again
                        onClick = { onSortFieldSelected(field) }
                    )
                }
            }
        }

        // Only shown while a search is narrowing the list down; the search field itself is the modal (see the
        // magnifier at the top of the screen). The box takes the free space either way, keeping the actions at the end.
        Box(modifier = Modifier.weight(1f)) {
            if (activeFilterQuery.isNotBlank()) FilterChipRow(activeFilterQuery, filteredCount, totalCount, onClearFilter)
        }
        if (actions.isNotEmpty()) SortRowActions(actions)
    }
}

/** An action in the sort row's "New" menu (see [SortControls]); [description] is its name there. */
data class SortRowAction(
    val icon: ImageVector,
    val description: String,
    val enabled: Boolean = true,
    val onClick: () -> Unit
)

/**
 * The sort row's actions behind one outlined "New" button, shaped and bordered like the "Sort by" field beside it (the
 * same height and corners, moved down by the room the field keeps above itself for its floating label, so the two line
 * up). Each action is listed in its menu with its name.
 */
@Composable
private fun SortRowActions(actions: List<SortRowAction>) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = Modifier.padding(top = SortFieldLabelSpace)) {
        val enabled = actions.any { it.enabled }
        OutlinedButton(
            onClick = { expanded = true },
            enabled = enabled,
            shape = OutlinedTextFieldDefaults.shape,
            // The field's own outline and text colours, so the two read as a pair
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = if (enabled) 1f else 0.38f)),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
            contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
            modifier = Modifier.height(OutlinedTextFieldDefaults.MinHeight)
        ) {
            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
            Spacer(Modifier.size(ButtonDefaults.IconSpacing))
            Text("New")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            actions.forEach { action ->
                DropdownMenuItem(
                    text = { Text(action.description) },
                    leadingIcon = { Icon(action.icon, contentDescription = null) },
                    enabled = action.enabled,
                    onClick = {
                        expanded = false
                        action.onClick()
                    }
                )
            }
        }
    }
}

/** The room an outlined field with a label keeps above its border, for the label to sit on it. */
private val SortFieldLabelSpace = 8.dp

/** The active search, with how many of the items it matches and a button to clear it. */
@Composable
private fun FilterChipRow(activeFilterQuery: String, filteredCount: Int, totalCount: Int, onClearFilter: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(16.dp))
        Text(
            text = "\"$activeFilterQuery\" · $filteredCount/$totalCount",
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 6.dp).weight(1f, fill = false)
        )
        IconButton(onClick = onClearFilter, modifier = Modifier.size(28.dp)) {
            Icon(Icons.Filled.Clear, contentDescription = "Clear search", modifier = Modifier.size(16.dp))
        }
    }
}

/** At most this many matches float under the search card, so they're a glance rather than a second list. */
private const val MAX_SEARCH_RESULTS = 6

/** The search card, the gap under it and the "more" note: what's left of the height after them is for the matches. */
private val SearchChromeHeight = 140.dp

/** How far the dialog window dims what's behind it while that is blurred too (see [SearchModal]); a lighter touch than the default, which would mostly hide the blur. */
private const val BLURRED_DIM_AMOUNT = 0.25f

/**
 * Searching the list a browse screen shows: a compact card near the top of the screen, opened by the magnifier in the
 * top bar (see App.kt). While it's open the list behind it stays as it was: the matches of what's typed float under the
 * card as their own cards (see [SearchResultStack]), and tapping one opens it. Closing it (the search key, back, or a
 * tap beside the card) hands the query to the list, which then filters by it; the chip by the sort control (see
 * [SortControls]) shows and clears it.
 *
 * Its own window: the screen underneath is blurred by App.kt while this is open (Android 12 and up), and the
 * dialog's own dim is lightened to match; before Android 12 the dim is all there is.
 */
@Composable
fun SearchModal(
    query: String,
    onQueryCommit: (String) -> Unit,
    candidates: List<StorageNode>,
    thumbnailFor: (StorageNode) -> String?,
    placeholder: String,
    onResultClick: (StorageNode) -> Unit,
    onDismiss: () -> Unit
) {
    // The cursor starts at the end of a query that's already there, so it can just be typed on
    var fieldValue by remember { mutableStateOf(TextFieldValue(query, selection = TextRange(query.length))) }
    // Every way of closing hands the query over to the list, to filter it by
    val close = {
        onQueryCommit(fieldValue.text)
        onDismiss()
    }
    // Matched the same way the list filters, in the list's own order
    val matches = remember(candidates, fieldValue.text) {
        val text = fieldValue.text
        if (text.isBlank()) emptyList() else candidates.filter { it.name.contains(text, ignoreCase = true) }
    }

    Dialog(
        onDismissRequest = close,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        val dialogWindow = (LocalView.current.parent as? DialogWindowProvider)?.window
        SideEffect {
            if (Build.VERSION.SDK_INT >= 31) dialogWindow?.setDimAmount(BLURRED_DIM_AMOUNT)
        }

        val focusRequester = remember { FocusRequester() }
        LaunchedEffect(Unit) { focusRequester.requestFocus() }

        // Fills the window so a tap beside the card closes it. Above the keyboard (safeDrawing includes it), so the
        // matches are only as many as fit there
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .clickable(interactionSource = null, indication = null, onClick = close)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            contentAlignment = Alignment.TopCenter
        ) {
            // Room for the card and the "more" note taken off; never more than a handful, so it stays a glance
            val fit = ((maxHeight - SearchChromeHeight) / (SearchResultHeight + SearchResultGap)).toInt()
                .coerceIn(1, MAX_SEARCH_RESULTS)
            val shown = matches.take(fit)
            Column(
                modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(
                    shape = RoundedCornerShape(28.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    tonalElevation = 6.dp,
                    shadowElevation = 6.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        // Taps on the card itself mustn't reach the dismissing box behind it
                        .clickable(interactionSource = null, indication = null, onClick = {})
                ) {
                    Column {
                        TextField(
                            value = fieldValue,
                            onValueChange = { fieldValue = it },
                            placeholder = { Text(placeholder, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                            trailingIcon = {
                                if (fieldValue.text.isNotEmpty()) {
                                    IconButton(onClick = { fieldValue = TextFieldValue("") }) {
                                        Icon(Icons.Filled.Clear, contentDescription = "Clear search")
                                    }
                                }
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { close() }),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(focusRequester)
                        )
                        if (fieldValue.text.isNotBlank()) {
                            Text(
                                text = "Showing ${matches.size} of ${candidates.size}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                // Under the query, lined up with its text (past the leading icon)
                                modifier = Modifier.padding(start = 52.dp, end = 16.dp, bottom = 12.dp)
                            )
                        }
                    }
                }
                SearchResultStack(
                    results = shown,
                    thumbnailFor = thumbnailFor,
                    onClick = { node ->
                        close()
                        onResultClick(node)
                    },
                    modifier = Modifier.padding(top = SearchResultGap * 1.5f)
                )
                // The matches past what fits are counted, not listed: closing the search filters the list by them all
                AnimatedVisibility(
                    visible = matches.size > shown.size,
                    enter = fadeIn() + scaleIn(initialScale = 0.8f),
                    exit = fadeOut() + scaleOut(targetScale = 0.8f)
                ) {
                    Text(
                        text = "+${matches.size - shown.size} more · search to filter the list",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .padding(top = SearchResultGap)
                            .clip(RoundedCornerShape(50))
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.72f))
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }
        }
    }
}
