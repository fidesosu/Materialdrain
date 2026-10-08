package tools.senko.materialdrain.browser

import android.os.Build
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import tools.senko.materialdrain.filesystem.PathSegment
import tools.senko.materialdrain.provider.api.StorageNode
import tools.senko.materialdrain.files.SortOptions
import tools.senko.materialdrain.files.SortableField
import tools.senko.materialdrain.ui.LocalReduceMotion

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
 * The sort field and direction — shared by every sortable list in the app, see `AppSettings.sortField` — and the
 * screen's actions (see [SortRowAction]). Searching opens as a modal over the screen (the magnifier at the top of the
 * screen), instead of living in this row.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SortControls(
    sortField: SortableField,
    sortAscending: Boolean,
    onSortFieldSelected: (SortableField) -> Unit,
    /** Actions at the end of the row, behind a "New" menu, e.g. the filesystem's upload and new folder. */
    actions: List<SortRowAction> = emptyList(),
    /** Stops the transfer shown in the bar above (a download, an upload); null while there's none. */
    onCancelTransfer: (() -> Unit)? = null
) {
    var expanded by remember { mutableStateOf(false) }
    val currentSortName = SortOptions.find { it.second == sortField }?.first.orEmpty()
    val directionIcon = if (sortAscending) Icons.Filled.ArrowUpward else Icons.Filled.ArrowDownward
    val directionDescription = if (sortAscending) "Ascending" else "Descending"

    // Kept while Cancel shrinks away, when there's no transfer left to cancel
    var lastCancel by remember { mutableStateOf(onCancelTransfer) }
    if (onCancelTransfer != null) lastCancel = onCancelTransfer

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            // Lines up with the file rows below, which start at 16dp
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        // Each card takes a share of the row as big as its share of what they all need, worked out from their own text:
        // Sort by alone fills the row, beside New they split it by content, and a Cancel appearing during a transfer
        // makes room for itself. On a narrow screen they all give way alike, their text ending in "…"
        val needs = sortRowNeeds()
        val shown = listOf(true, onCancelTransfer != null, actions.isNotEmpty())
        val visibleNeeds = needs.filterIndexed { i, _ -> shown[i] }
        val room = maxWidth - SortRowGap * (visibleNeeds.size - 1)
        val scale = room / visibleNeeds.fold(0.dp) { sum, need -> sum + need }
        val firstShown = shown.indexOf(true)
        val sizeSpring = spring<Dp>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)
        val widths = needs.mapIndexed { i, need ->
            animateDpAsState(if (shown[i]) need * scale else 0.dp, sizeSpring, label = "sortRowWidth$i").value
        }
        val gaps = shown.mapIndexed { i, isShown ->
            animateDpAsState(if (isShown && i != firstShown) SortRowGap else 0.dp, sizeSpring, label = "sortRowGap$i").value
        }
        val cancelAlpha by animateFloatAsState(if (shown[1]) 1f else 0f, label = "sortRowCancel")

        Row(verticalAlignment = Alignment.CenterVertically) {
            // Sort: a floating card with a menu. Reselecting the field already sorted on flips its direction (see
            // AppSettings.changeSortOrder); the direction shows by the field's name and in the menu
            Box(modifier = Modifier.width(widths[0])) {
                FloatingCardButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Sort by",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(currentSortName, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Icon(directionIcon, contentDescription = directionDescription, modifier = Modifier.size(16.dp))
                }
                FloatingCardMenu(expanded = expanded, onDismiss = { expanded = false }, minWidth = SortControlWidth) {
                    SortOptions.forEach { (name, field) ->
                        val selected = field == sortField
                        FloatingMenuItem(
                            text = name,
                            active = selected,
                            trailingIcon = if (selected) directionIcon else null,
                            trailingDescription = directionDescription,
                            // The menu stays open, so the effect can be seen and the direction flipped again
                            onClick = { onSortFieldSelected(field) }
                        )
                    }
                }
            }

            // Stops the transfer in the bar above; there while one runs, and while it shrinks away after
            if (shown[1] || widths[1] > 0.dp) {
                Box(modifier = Modifier.padding(start = gaps[1]).width(widths[1]).graphicsLayer { alpha = cancelAlpha }) {
                    FloatingCardButton(onClick = { lastCancel?.invoke() }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Filled.Close, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "Cancel",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.error,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            if (actions.isNotEmpty() || widths[2] > 0.dp) {
                SortRowActions(actions, modifier = Modifier.padding(start = gaps[2]).width(widths[2]))
            }
        }
    }
}

/** The space between the cards of the sort row. */
private val SortRowGap = 8.dp

/**
 * What each card of the sort row needs to show all of itself, in the row's order: Sort by (wide enough for the longest
 * field name, so it doesn't change size between fields), Cancel and New. From their text as it's drawn, plus their
 * icons and padding (see [FloatingCardButton]).
 */
@Composable
private fun sortRowNeeds(): List<Dp> {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val small = MaterialTheme.typography.labelSmall
    val large = MaterialTheme.typography.labelLarge
    return remember(measurer, density, small, large) {
        fun textWidth(text: String, style: TextStyle): Dp = with(density) { measurer.measure(text, style, maxLines = 1).size.width.toDp() }
        val padding = CardPadding * 2
        val sortText = maxOf(textWidth("Sort by", small), SortOptions.maxOf { (name, _) -> textWidth(name, large) })
        listOf(
            padding + sortText + 8.dp + 16.dp,
            padding + 18.dp + 6.dp + textWidth("Cancel", large),
            padding + 18.dp + 6.dp + textWidth("New", large) + 4.dp
        )
    }
}

/** An action in the sort row's "New" menu (see [SortControls]); [description] is its name there. */
data class SortRowAction(
    val icon: ImageVector,
    val description: String,
    val enabled: Boolean = true,
    val onClick: () -> Unit
)

/** The sort row's actions behind one "New" card, the same floating card as "Sort by" beside it; its menu names each. */
@Composable
private fun SortRowActions(actions: List<SortRowAction>, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        FloatingCardButton(onClick = { expanded = true }, enabled = actions.any { it.enabled }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("New", style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.width(4.dp))
        }
        FloatingCardMenu(expanded = expanded, onDismiss = { expanded = false }, alignment = Alignment.TopEnd) {
            actions.forEach { action ->
                FloatingMenuItem(
                    text = action.description,
                    leadingIcon = action.icon,
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

/** The space between a floating card button and its menu, and around the entries inside the menu. */
private val MenuGap = 6.dp

/** An entry's highlight: the menu's corners less the space around the entries, so the two curves run alongside. */
internal val MenuItemShape = RoundedCornerShape(14.dp)

private const val MENU_ANIMATION_MS = 200

/** Taller menus scroll. */
private val FloatingMenuMaxHeight = 480.dp

/**
 * The menu of a floating card button (see [FloatingCardButton], the host switcher), a floating card itself: the same
 * corners and edge, opening just under the button and growing down out of it. Call it in the box which holds only the
 * button, which is [anchorHeight] tall. It's at least [minWidth] wide, wider when the entries need it; [alignment] is the
 * button's edge it lines up with: [Alignment.TopEnd] for a button at the end of a row, [Alignment.TopCenter] centred.
 *
 * It closes on a tap outside it or back, by calling [onDismiss]; choosing an entry doesn't close it, that's up to the entry.
 */
@Composable
internal fun FloatingCardMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    alignment: Alignment = Alignment.TopStart,
    anchorHeight: Dp = ControlHeight,
    minWidth: Dp = 160.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    val duration = if (LocalReduceMotion.current) 0 else MENU_ANIMATION_MS
    val below = with(LocalDensity.current) { (anchorHeight + MenuGap).roundToPx() }
    // The popup has to stay in the composition while it closes
    val visibleState = remember { MutableTransitionState(false) }
    visibleState.targetState = expanded
    if (!visibleState.currentState && !visibleState.targetState) return

    Popup(
        alignment = alignment,
        offset = IntOffset(0, below),
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true)
    ) {
        AnimatedVisibility(
            visibleState = visibleState,
            enter = expandVertically(tween(duration, easing = FastOutSlowInEasing), expandFrom = Alignment.Top) +
                fadeIn(tween(duration / 2)),
            exit = shrinkVertically(tween(duration, easing = FastOutSlowInEasing), shrinkTowards = Alignment.Top) +
                fadeOut(tween(duration))
        ) {
            Surface(
                shape = FloatingCardShape,
                // Opaque, unlike the button: a menu over the list would be hard to read with the list showing through
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                border = floatingCardBorder(),
                shadowElevation = 6.dp,
                contentColor = MaterialTheme.colorScheme.onSurface,
                // Leaves room for the shadow, which the popup would otherwise cut off
                modifier = Modifier.padding(bottom = 8.dp)
            ) {
                Column(
                    modifier = Modifier
                        .width(IntrinsicSize.Max)
                        .widthIn(min = minWidth)
                        .heightIn(max = FloatingMenuMaxHeight)
                        .verticalScroll(rememberScrollState())
                        .padding(MenuGap),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    content = content
                )
            }
        }
    }
}

/**
 * An entry of a [FloatingCardMenu]. An [active] entry (the current choice) is in the accent colour on a tinted, rounded
 * highlight, the same tint as [tools.senko.materialdrain.ui.components.ExpandingMenuItem]'s.
 */
@Composable
internal fun FloatingMenuItem(
    text: String,
    onClick: () -> Unit,
    active: Boolean = false,
    enabled: Boolean = true,
    leadingIcon: ImageVector? = null,
    trailingIcon: ImageVector? = null,
    trailingDescription: String? = null
) {
    val accent = MaterialTheme.colorScheme.primary
    val color = if (active) accent else MaterialTheme.colorScheme.onSurface
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(MenuItemShape)
            .background(if (active) accent.copy(alpha = 0.16f) else Color.Transparent)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp)
            .graphicsLayer { alpha = if (enabled) 1f else 0.38f }
    ) {
        leadingIcon?.let {
            Icon(it, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
        }
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            color = color,
            maxLines = 1,
            modifier = Modifier.weight(1f)
        )
        trailingIcon?.let {
            Spacer(Modifier.width(12.dp))
            Icon(it, contentDescription = trailingDescription, tint = color, modifier = Modifier.size(18.dp))
        }
    }
}

/** The height of the controls above the list. */
private val ControlHeight = 48.dp

/** The space between a control's edge and its content. */
private val CardPadding = 14.dp

/** A control above the list, in the same floating card look as the search results (see [FloatingCardShape]). */
@Composable
private fun FloatingCardButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = FloatingCardShape,
        color = floatingCardColor(),
        border = floatingCardBorder(),
        contentColor = MaterialTheme.colorScheme.onSurface,
        modifier = modifier.height(ControlHeight)
    ) {
        // Centred, for a card given more room than its content needs (see SortControls)
        Row(
            modifier = Modifier
                .padding(horizontal = CardPadding)
                .graphicsLayer { alpha = if (enabled) 1f else 0.38f },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            content = content
        )
    }
}

/** How far the dialog window dims what's behind it while that is blurred too (see [SearchModal]); a lighter touch than the default, which would mostly hide the blur. */
private const val BLURRED_DIM_AMOUNT = 0.25f

/**
 * Searching the list a browse screen shows: a compact card near the top of the screen, opened by the magnifier in the
 * top bar (see App.kt). While it's open the list behind it stays as it was: the matches of what's typed float under the
 * card as their own cards (see [SearchResultStack]), and tapping one opens it. The search key only puts the keyboard
 * away; back or a tap beside the cards closes it. The list is never filtered: [onQueryChange] only lets the screen keep
 * the query, for the next time the search is opened.
 *
 * Its own window: the screen underneath is blurred by App.kt while this is open (Android 12 and up), and the
 * dialog's own dim is lightened to match; before Android 12 the dim is all there is.
 *
 * @param candidates what can match, in no particular order: they're sorted by [order] once matched
 * @param resultLimit the most matches shown, 0 for all (see AppSettings.searchResultLimit)
 * @param locationOf where a match is, for matches outside the open folder; null when it's right there
 * @param status a line about the search itself, e.g. that subfolders are still being looked through
 */
@Composable
fun SearchModal(
    query: String,
    onQueryChange: (String) -> Unit,
    candidates: List<StorageNode>,
    order: Comparator<StorageNode>,
    resultLimit: Int,
    thumbnailFor: (StorageNode) -> String?,
    placeholder: String,
    onResultClick: (StorageNode) -> Unit,
    onDismiss: () -> Unit,
    locationOf: (StorageNode) -> String? = { null },
    status: String? = null
) {
    // The cursor starts at the end of a query that's already there, so it can just be typed on
    var fieldValue by remember { mutableStateOf(TextFieldValue(query, selection = TextRange(query.length))) }
    val close = onDismiss
    // The search key only puts the keyboard away, leaving more room for the matches; the search stays open
    val keyboard = LocalSoftwareKeyboardController.current
    // Matched the same way the list filters, then sorted the way the list is. Off the main thread: a folder tree can
    // hold tens of thousands of files, and they keep coming in while it's being looked through
    var matches by remember { mutableStateOf(emptyList<StorageNode>()) }
    LaunchedEffect(candidates, fieldValue.text, order) {
        val text = fieldValue.text
        matches = if (text.isBlank()) {
            emptyList()
        } else {
            withContext(Dispatchers.Default) {
                candidates.filter { it.name.contains(text, ignoreCase = true) }.sortedWith(order)
            }
        }
    }
    val shown = if (resultLimit > 0) matches.take(resultLimit) else matches

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

        // Fills the window so a tap beside the cards closes it. The matches go on down to the edge of the screen, under the
        // navigation bar and the keyboard, rather than being cut off above them: the stack adds room for those after its
        // last card, so every match can still be scrolled into view
        val bottomInset = with(LocalDensity.current) { WindowInsets.safeDrawing.getBottom(this).toDp() }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(interactionSource = null, indication = null, onClick = close)
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                .padding(start = 16.dp, end = 16.dp, top = 8.dp),
            contentAlignment = Alignment.TopCenter
        ) {
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
                            keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
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
                        val lines = listOfNotNull(
                            if (fieldValue.text.isNotBlank()) "Showing ${matches.size} of ${candidates.size}" else null,
                            status
                        )
                        if (lines.isNotEmpty()) {
                            Text(
                                text = lines.joinToString("\n"),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                // Under the query, lined up with its text (past the leading icon)
                                modifier = Modifier.padding(start = 52.dp, end = 16.dp, bottom = 12.dp)
                            )
                        }
                    }
                }
                val moreMatches = matches.size > shown.size
                SearchResultStack(
                    results = shown,
                    thumbnailFor = thumbnailFor,
                    locationOf = locationOf,
                    onClick = { node ->
                        onDismiss()
                        onResultClick(node)
                    },
                    // The count of more matches below keeps clear of the navigation bar itself
                    bottomPadding = if (moreMatches) 0.dp else bottomInset + 8.dp,
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .padding(top = SearchResultGap * 1.5f)
                )
                // Only with a limit set (in the settings): the matches past it are counted, not listed
                AnimatedVisibility(
                    visible = moreMatches,
                    enter = fadeIn() + scaleIn(initialScale = 0.8f),
                    exit = fadeOut() + scaleOut(targetScale = 0.8f)
                ) {
                    Text(
                        text = "+${matches.size - shown.size} more matches",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .padding(top = SearchResultGap, bottom = bottomInset + 8.dp)
                            .clip(RoundedCornerShape(50))
                            .background(floatingCardColor())
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }
        }
    }
}
