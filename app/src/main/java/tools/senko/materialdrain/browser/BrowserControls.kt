package tools.senko.materialdrain.browser

import android.os.Build
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
import androidx.compose.material3.FilledTonalButton
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
 * The sort row's actions behind one "New" button, shaped like the "Sort by" field beside it (the same height and
 * corners, moved down by the room the field keeps above itself for its floating label, so the two line up). Each
 * action is listed in its menu with its name.
 */
@Composable
private fun SortRowActions(actions: List<SortRowAction>) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = Modifier.padding(top = SortFieldLabelSpace)) {
        FilledTonalButton(
            onClick = { expanded = true },
            enabled = actions.any { it.enabled },
            shape = OutlinedTextFieldDefaults.shape,
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

/** How far the dialog window dims what's behind it while that is blurred too (see [SearchModal]); a lighter touch than the default, which would mostly hide the blur. */
private const val BLURRED_DIM_AMOUNT = 0.25f

/**
 * Searching the list a browse screen shows: a compact card near the top of the screen, opened by the magnifier in the
 * top bar (see App.kt). The list filters as the query is typed, so the search key and tapping beside the card just
 * close it; the query stays, and the chip by the sort control (see [SortControls]) shows and clears it.
 *
 * Its own window: the screen underneath is blurred by App.kt while this is open (Android 12 and up), and the
 * dialog's own dim is lightened to match; before Android 12 the dim is all there is.
 */
@Composable
fun SearchModal(
    query: String,
    onQueryChange: (String) -> Unit,
    filteredCount: Int,
    totalCount: Int,
    placeholder: String,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        val dialogWindow = (LocalView.current.parent as? DialogWindowProvider)?.window
        SideEffect {
            if (Build.VERSION.SDK_INT >= 31) dialogWindow?.setDimAmount(BLURRED_DIM_AMOUNT)
        }

        // The cursor starts at the end of a query that's already there, so it can just be typed on
        var fieldValue by remember { mutableStateOf(TextFieldValue(query, selection = TextRange(query.length))) }
        val focusRequester = remember { FocusRequester() }
        LaunchedEffect(Unit) { focusRequester.requestFocus() }

        // Fills the window so a tap beside the card closes it
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(interactionSource = null, indication = null, onClick = onDismiss)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            contentAlignment = Alignment.TopCenter
        ) {
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 6.dp,
                shadowElevation = 6.dp,
                modifier = Modifier
                    .widthIn(max = 560.dp)
                    .fillMaxWidth()
                    // Taps on the card itself mustn't reach the dismissing box behind it
                    .clickable(interactionSource = null, indication = null, onClick = {})
            ) {
                Column {
                    TextField(
                        value = fieldValue,
                        onValueChange = {
                            fieldValue = it
                            onQueryChange(it.text)
                        },
                        placeholder = { Text(placeholder, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                        trailingIcon = {
                            if (fieldValue.text.isNotEmpty()) {
                                IconButton(onClick = {
                                    fieldValue = TextFieldValue("")
                                    onQueryChange("")
                                }) {
                                    Icon(Icons.Filled.Clear, contentDescription = "Clear search")
                                }
                            }
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { onDismiss() }),
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
                            text = "Showing $filteredCount of $totalCount",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            // Under the query, lined up with its text (past the leading icon)
                            modifier = Modifier.padding(start = 52.dp, end = 16.dp, bottom = 12.dp)
                        )
                    }
                }
            }
        }
    }
}
