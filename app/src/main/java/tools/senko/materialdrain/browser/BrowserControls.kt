package tools.senko.materialdrain.browser

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
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
 * The sort field and direction — shared by every sortable list in the app, see `AppSettings.sortField` — and the name
 * filter of this screen's own list. Choosing the field already sorted on flips its direction, the same as before.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SortControls(
    sortField: SortableField,
    sortAscending: Boolean,
    onSortFieldSelected: (SortableField) -> Unit,
    filterQuery: String,
    onFilterQueryChanged: (String) -> Unit,
    filteredCount: Int,
    totalCount: Int,
    filterFocusRequester: FocusRequester,
    onFilterSubmitted: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    // Back gives up the focus of the filter (and with it the keyboard) instead of leaving the app
    val focusManager = LocalFocusManager.current
    var filterFocused by remember { mutableStateOf(false) }
    BackHandler(enabled = filterFocused) {
        focusManager.clearFocus()
        onFilterSubmitted()
    }
    val currentSortName = SortOptions.find { it.second == sortField }?.first.orEmpty()
    val directionIcon = if (sortAscending) Icons.Filled.ArrowUpward else Icons.Filled.ArrowDownward
    val directionDescription = if (sortAscending) "Ascending" else "Descending"

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                // Lines up with the file rows below, which start at 16dp
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Sort: a dropdown, the same width the compact control used before. Reselecting the field already sorted
            // on flips its direction (see AppSettings.changeSortOrder); the direction otherwise only shows in the menu.
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

            // Search: takes the rest of the row. Built from a BasicTextField so the label can sit on the border all
            // the time, like the sort control above. (OutlinedTextField only moves the label there on focus or text.)
            val filterInteractionSource = remember { MutableInteractionSource() }
            BasicTextField(
                value = filterQuery,
                onValueChange = onFilterQueryChanged,
                modifier = Modifier
                    .weight(1f)
                    // The space the label takes above the border, the same as OutlinedTextField reserves
                    .padding(top = 8.dp)
                    .defaultMinSize(minHeight = OutlinedTextFieldDefaults.MinHeight)
                    .focusRequester(filterFocusRequester)
                    .onFocusChanged { filterFocused = it.isFocused },
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                singleLine = true,
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onFilterSubmitted() }),
                interactionSource = filterInteractionSource,
                decorationBox = { innerTextField ->
                    OutlinedTextFieldDefaults.DecorationBox(
                        // Never empty: that is what keeps the label up on the border
                        value = filterQuery.ifEmpty { " " },
                        innerTextField = innerTextField,
                        enabled = true,
                        singleLine = true,
                        visualTransformation = VisualTransformation.None,
                        interactionSource = filterInteractionSource,
                        label = { Text("Search", maxLines = 1) },
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                        // No trailing icon slot at all while empty, a slot takes room away from the text
                        trailingIcon = if (filterQuery.isNotEmpty()) {
                            {
                                IconButton(onClick = {
                                    onFilterQueryChanged("")
                                    onFilterSubmitted()
                                }) {
                                    Icon(Icons.Filled.Clear, contentDescription = "Clear filter")
                                }
                            }
                        } else null
                    )
                }
            )
        }
        // How many files match, worth a mention only while a filter is actually narrowing the list down
        if (filterQuery.isNotBlank()) {
            Text(
                text = "$filteredCount of $totalCount",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 4.dp)
            )
        }
    }
}
