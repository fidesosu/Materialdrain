package tools.senko.materialdrain.browser

import tools.senko.materialdrain.ui.theme.AppShapes
import android.os.Build
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.text.rememberTextMeasurer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import tools.senko.materialdrain.ui.LocalReduceMotion
import tools.senko.materialdrain.ui.components.AppMenuPages
import tools.senko.materialdrain.ui.icons.automirrored.filled.KeyboardArrowRight
import tools.senko.materialdrain.ui.icons.filled.Tune
import tools.senko.materialdrain.ui.icons.filled.Settings
import tools.senko.materialdrain.ui.icons.filled.CreateNewFolder
import tools.senko.materialdrain.ui.icons.filled.Link
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import tools.senko.materialdrain.ui.icons.automirrored.filled.ArrowForwardIos
import tools.senko.materialdrain.ui.icons.automirrored.filled.KeyboardArrowLeft
import tools.senko.materialdrain.ui.icons.filled.Add
import tools.senko.materialdrain.ui.icons.filled.ArrowDownward
import tools.senko.materialdrain.ui.icons.filled.ArrowUpward
import tools.senko.materialdrain.ui.icons.filled.Clear
import tools.senko.materialdrain.ui.icons.filled.Close
import tools.senko.materialdrain.ui.icons.filled.Search
import tools.senko.materialdrain.ui.icons.outlined.Image
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import tools.senko.materialdrain.filesystem.PathSegment
import tools.senko.materialdrain.provider.api.StorageNode
import tools.senko.materialdrain.files.SortOptions
import tools.senko.materialdrain.files.SortableField
import tools.senko.materialdrain.ui.components.AppMenuDivider
import tools.senko.materialdrain.ui.components.AppMenuItem
import tools.senko.materialdrain.ui.components.AppMenuPreview
import tools.senko.materialdrain.ui.components.FloatingCardShape
import tools.senko.materialdrain.ui.components.floatingCardBorder
import tools.senko.materialdrain.ui.components.floatingCardColor
import androidx.compose.ui.platform.LocalInspectionMode
import tools.senko.materialdrain.ui.preview.PreviewTheme
import tools.senko.materialdrain.ui.preview.ThemePreviews
import tools.senko.materialdrain.ui.preview.ScreenPreviews
import tools.senko.materialdrain.ui.preview.PreviewSamples
import tools.senko.materialdrain.files.fileComparator
import tools.senko.materialdrain.ui.icons.filled.Upload
import tools.senko.materialdrain.ui.icons.filled.Check
import tools.senko.materialdrain.ui.icons.filled.Download
import tools.senko.materialdrain.ui.icons.filled.FolderZip
import tools.senko.materialdrain.ui.icons.filled.GridView
import tools.senko.materialdrain.ui.icons.filled.AutoAwesome
import tools.senko.materialdrain.ui.icons.filled.Refresh
import tools.senko.materialdrain.ui.icons.filled.ViewList
import tools.senko.materialdrain.settings.ListLayout
import androidx.compose.ui.res.stringResource
import tools.senko.materialdrain.R
import androidx.compose.ui.platform.LocalContext

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
            text = stringResource(R.string.browser_storage),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            modifier = modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 10.dp)
        )
        return
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState)
            // A little closer to the bar above than to the list below
            .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 10.dp),
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
                    .clip(AppShapes.ExtraSmall)
                    .clickable { onPathSegmentClick(segment) }
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            )
            if (index < pathSegments.lastIndex) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                    contentDescription = stringResource(R.string.browser_path_separator),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

/**
 * The cards above the list for what's going on: Cancel, which stops the transfer the bar above shows, and Thumbnails,
 * which loads the ones the data saver holds back. The row is only there while one of them is, and grows out of the
 * header and shrinks back into it as they come and go. The sorting and the screen's New actions are in the ⋮ menu at the
 * top of the screen (see [ScreenMenuEntries]).
 */
@Composable
fun StatusCards(
    /** Stops the transfer shown in the bar above (a download, an upload); null while there's none. */
    onCancelTransfer: (() -> Unit)?,
    /** Loads the thumbnails the data saver holds back (see DataSaver); null while there are none held back. */
    onLoadThumbnails: (() -> Unit)?
) {
    val reduceMotion = LocalReduceMotion.current
    // Kept while Cancel shrinks away, when there's no transfer left to cancel; the same for Thumbnails once they're loaded
    var lastCancel by remember { mutableStateOf(onCancelTransfer) }
    if (onCancelTransfer != null) lastCancel = onCancelTransfer
    var lastLoadThumbnails by remember { mutableStateOf(onLoadThumbnails) }
    if (onLoadThumbnails != null) lastLoadThumbnails = onLoadThumbnails

    val nowShown = listOf(onCancelTransfer != null, onLoadThumbnails != null)
    // While the whole row shrinks away the cards it had stay as they were, rather than shrinking inside it
    var lastShown by remember { mutableStateOf(nowShown) }
    if (nowShown.any { it }) lastShown = nowShown

    AnimatedVisibility(
        visible = nowShown.any { it },
        enter = if (reduceMotion) fadeIn(tween(100)) else expandVertically(expandFrom = Alignment.Top) + fadeIn(),
        exit = if (reduceMotion) fadeOut(tween(100)) else shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut()
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                // Lines up with the file rows below, which start at 16dp
                .padding(horizontal = 16.dp, vertical = 4.dp)
        ) {
            // Each card takes a share of the row as big as its share of what they both need, worked out from their own
            // text: one alone fills the row, and one appearing beside it makes room for itself
            val needs = statusCardNeeds()
            val shown = lastShown
            val visibleNeeds = needs.filterIndexed { i, _ -> shown[i] }
            val room = maxWidth - SortRowGap * (visibleNeeds.size - 1).coerceAtLeast(0)
            val scale = room / visibleNeeds.fold(0.dp) { sum, need -> sum + need }.coerceAtLeast(1.dp)
            val sizeSpring = spring<Dp>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)
            val widths = needs.mapIndexed { i, need ->
                animateDpAsState(if (shown[i]) need * scale else 0.dp, sizeSpring, label = "statusCardWidth$i").value
            }
            val gap by animateDpAsState(if (shown.all { it }) SortRowGap else 0.dp, sizeSpring, label = "statusCardGap")
            val cancelAlpha by animateFloatAsState(if (shown[0]) 1f else 0f, label = "statusCardCancel")
            val thumbnailsAlpha by animateFloatAsState(if (shown[1]) 1f else 0f, label = "statusCardThumbnails")

            Row(verticalAlignment = Alignment.CenterVertically) {
                // Stops the transfer in the bar above; there while one runs, and while it shrinks away after
                if (shown[0] || widths[0] > 0.dp) {
                    Box(modifier = Modifier.width(widths[0]).graphicsLayer { alpha = cancelAlpha }) {
                        FloatingCardButton(onClick = { lastCancel?.invoke() }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Filled.Close, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                stringResource(R.string.browser_cancel),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.error,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                // The thumbnails the data saver holds back, all at once; there while they're held back, and while it shrinks away
                if (shown[1] || widths[1] > 0.dp) {
                    Box(modifier = Modifier.padding(start = gap).width(widths[1]).graphicsLayer { alpha = thumbnailsAlpha }) {
                        FloatingCardButton(onClick = { lastLoadThumbnails?.invoke() }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Outlined.Image, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.browser_thumbnails), style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}

/** The sort order of a sortable list, for the ⋮ menu at the top of its screen (see [ScreenMenuEntries]). */
class SortChoice(val field: SortableField, val ascending: Boolean, val onSelect: (SortableField) -> Unit)

/** Where a [ScreenAction] goes in the ⋮ menu at the top of its screen (see [ScreenMenuEntries]). */
enum class ScreenActionGroup {
    /** At the top, as it is: what's done with the page shown (download an open list, select) */
    PAGE,

    /** Behind New: what makes something new on it (upload, a new folder) */
    NEW
}

/**
 * An action of a screen in the ⋮ menu at its top (see [ScreenMenuEntries]), in its [group]: e.g. the filesystem's upload.
 * With [choices] (Download: each file on its own, or all in one zip), it opens a page of those instead.
 */
data class ScreenAction(
    val icon: ImageVector,
    val description: String,
    val enabled: Boolean = true,
    val group: ScreenActionGroup = ScreenActionGroup.NEW,
    val choices: List<ScreenAction> = emptyList(),
    val onClick: () -> Unit
)

/**
 * How the list a screen shows looks, for the View page of its ⋮ menu (see [ScreenMenuEntries]): as a [grid] or rows,
 * [compact] rows, and whether that's this folder's own look ([custom]) or the Look and feel's defaults, which
 * [defaultLabel] says. [onLayout] and [onCompact] give the folder its own look, [onReset] takes it back to the defaults.
 *
 * A Filesystem folder's look can be for its subfolders too ([subfolders], null where there are none to have it, set with
 * [onSubfolders]); one that has the look of such a folder above it says which ([inheritedFrom]), and that look is then
 * what [defaultLabel] says and [onReset] goes back to. With the Smart layout chosen ([smart]), [grid] is what it picked.
 */
class ViewChoice(
    val grid: Boolean,
    val compact: Boolean,
    val custom: Boolean,
    val defaultLabel: String,
    val onLayout: (ListLayout) -> Unit,
    val onCompact: (Boolean) -> Unit,
    val onReset: () -> Unit,
    val subfolders: Boolean? = null,
    val onSubfolders: (Boolean) -> Unit = {},
    val inheritedFrom: String? = null,
    val smart: Boolean = false
)

/** A page of the ⋮ menu at the top of a screen (see [ScreenMenuEntries]); [Choices], those of a [ScreenAction]. */
enum class ScreenMenuPage { Sort, View, New, Choices }

/**
 * The entries of the ⋮ menu at the top of a screen, in the order they're reached for:
 * - first what's done with the page shown (the [actions] of [ScreenActionGroup.PAGE]: an open list's downloads, Select),
 *   then New, a page of the screen's actions which make something ([ScreenActionGroup.NEW]): what the menu is opened for;
 * - then how the list is shown: Sort by for a sortable list ([sort]), with the field it's sorted on, and View ([view]),
 *   its look, each a page of its own; set less often, they come after;
 * - last Settings, which leaves the screen.
 *
 * [close] closes the menu, which choosing on the Sort by and View pages doesn't, so the change can be seen (see
 * [SortMenuEntries]). [startPage] is the page it opens on, for the previews.
 */
@Composable
fun ScreenMenuEntries(
    close: () -> Unit,
    sort: SortChoice?,
    actions: List<ScreenAction>,
    onOpenSettings: () -> Unit,
    view: ViewChoice? = null,
    startPage: ScreenMenuPage? = null
) {
    val pageActions = actions.filter { it.group == ScreenActionGroup.PAGE }
    val newActions = actions.filter { it.group == ScreenActionGroup.NEW }
    // Remembered in the menu's popup, so each opening starts on the first page
    var page by remember { mutableStateOf(startPage) }
    // The page shown on the second level, kept while going back so it slides out as it was
    var secondPage by remember { mutableStateOf(startPage ?: ScreenMenuPage.Sort) }
    fun open(to: ScreenMenuPage) {
        secondPage = to
        page = to
    }
    // The action whose choices are on the Choices page
    var choicesOf by remember { mutableStateOf(pageActions.firstOrNull { it.choices.isNotEmpty() }) }
    // The pages behind Sort by, View and New. Each one is measured even while another is shown, so the menu keeps one width
    val sortPage: @Composable ColumnScope.() -> Unit = {
        if (sort != null) SortMenuEntries(sort.field, sort.ascending, sort.onSelect, onBack = { page = null })
    }
    val viewPage: @Composable ColumnScope.() -> Unit = {
        if (view != null) ViewMenuEntries(view, onBack = { page = null })
    }
    val newPage: @Composable ColumnScope.() -> Unit = {
        if (newActions.isNotEmpty()) {
            AppMenuItem(stringResource(R.string.browser_new), onClick = { page = null }, leadingIcon = Icons.AutoMirrored.Filled.KeyboardArrowLeft)
            AppMenuDivider()
            newActions.forEach { action ->
                AppMenuItem(
                    text = action.description,
                    leadingIcon = action.icon,
                    enabled = action.enabled,
                    onClick = {
                        close()
                        action.onClick()
                    }
                )
            }
        }
    }
    val choicesPage: @Composable ColumnScope.() -> Unit = {
        choicesOf?.let { action ->
            AppMenuItem(action.description, onClick = { page = null }, leadingIcon = Icons.AutoMirrored.Filled.KeyboardArrowLeft)
            AppMenuDivider()
            action.choices.forEach { choice ->
                AppMenuItem(
                    text = choice.description,
                    leadingIcon = choice.icon,
                    enabled = choice.enabled,
                    onClick = {
                        close()
                        choice.onClick()
                    }
                )
            }
        }
    }
    val pages = mapOf(
        ScreenMenuPage.Sort to sortPage,
        ScreenMenuPage.View to viewPage,
        ScreenMenuPage.New to newPage,
        ScreenMenuPage.Choices to choicesPage
    )
    AppMenuPages(
        showSecondary = page != null,
        primary = {
            // What's done with this page
            pageActions.forEach { action ->
                AppMenuItem(
                    text = action.description,
                    leadingIcon = action.icon,
                    trailingIcon = if (action.choices.isEmpty()) null else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    enabled = action.enabled,
                    onClick = {
                        if (action.choices.isEmpty()) {
                            close()
                            action.onClick()
                        } else {
                            choicesOf = action
                            open(ScreenMenuPage.Choices)
                        }
                    }
                )
            }
            if (newActions.isNotEmpty()) {
                AppMenuItem(
                    stringResource(R.string.browser_new),
                    leadingIcon = Icons.Filled.Add,
                    trailingIcon = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    enabled = newActions.any { it.enabled },
                    onClick = { open(ScreenMenuPage.New) }
                )
            }
            // How it's shown
            if ((pageActions.isNotEmpty() || newActions.isNotEmpty()) && (sort != null || view != null)) AppMenuDivider()
            if (sort != null) {
                AppMenuItem(
                    stringResource(R.string.browser_sort_by),
                    leadingIcon = Icons.Filled.Tune,
                    trailingText = SortOptions.find { it.second == sort.field }?.first,
                    trailingIcon = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    onClick = { open(ScreenMenuPage.Sort) }
                )
            }
            if (view != null) {
                AppMenuItem(
                    stringResource(R.string.browser_view),
                    leadingIcon = if (view.grid) Icons.Filled.GridView else Icons.Filled.ViewList,
                    trailingText = if (view.smart) stringResource(R.string.browser_smart) else if (view.grid) stringResource(R.string.browser_grid) else if (view.compact) stringResource(R.string.browser_compact) else stringResource(R.string.browser_list),
                    trailingIcon = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    onClick = { open(ScreenMenuPage.View) }
                )
            }
            if (pageActions.isNotEmpty() || newActions.isNotEmpty() || sort != null || view != null) AppMenuDivider()
            AppMenuItem(stringResource(R.string.browser_settings), leadingIcon = Icons.Filled.Settings, onClick = { close(); onOpenSettings() })
        },
        secondary = { pages.getValue(secondPage)() },
        otherPages = { pages.filterKeys { it != secondPage }.values.forEach { it() } }
    )
}

/**
 * The entries of the View page: "View" going back ([onBack]), the layouts (rows, a grid, and Smart, either by what's shown),
 * Compact rows, whether a Filesystem folder's look is for its subfolders too, and going back to the Look and feel's
 * defaults (or the look of the folder above it has). What's chosen is this folder's own look (see ViewLook), and the menu
 * stays open, so it can be seen.
 */
@Composable
private fun ViewMenuEntries(view: ViewChoice, onBack: () -> Unit) {
    AppMenuItem(stringResource(R.string.browser_view), onClick = onBack, leadingIcon = Icons.AutoMirrored.Filled.KeyboardArrowLeft)
    AppMenuDivider()
    val list = !view.smart && !view.grid
    val grid = !view.smart && view.grid
    AppMenuItem(
        stringResource(R.string.browser_list),
        leadingIcon = Icons.Filled.ViewList,
        active = list,
        trailingIcon = if (list) Icons.Filled.Check else null,
        onClick = { view.onLayout(ListLayout.LIST) }
    )
    AppMenuItem(
        stringResource(R.string.browser_grid),
        leadingIcon = Icons.Filled.GridView,
        active = grid,
        trailingIcon = if (grid) Icons.Filled.Check else null,
        onClick = { view.onLayout(ListLayout.GRID) }
    )
    // Which of the two it picked, while it's chosen
    AppMenuItem(
        stringResource(R.string.browser_smart),
        leadingIcon = Icons.Filled.AutoAwesome,
        supportingText = if (view.smart) stringResource(R.string.browser_here, if (view.grid) stringResource(R.string.browser_grid) else stringResource(R.string.browser_list)) else stringResource(R.string.browser_a_grid_for_pictures_and),
        active = view.smart,
        trailingIcon = if (view.smart) Icons.Filled.Check else null,
        onClick = { view.onLayout(ListLayout.SMART) }
    )
    AppMenuDivider()
    // Only rows have a height to lower; Smart may pick them, so it's chosen for them there too, whichever it picked
    val compactShown = view.compact && (!view.grid || view.smart)
    AppMenuItem(
        stringResource(R.string.browser_compact_rows),
        active = compactShown,
        trailingIcon = if (compactShown) Icons.Filled.Check else null,
        enabled = !view.grid || view.smart,
        onClick = { view.onCompact(!view.compact) }
    )
    AppMenuDivider()
    // What this look is set for: a folder's can be for the ones inside it too, except those with a look of their own
    if (view.subfolders != null) {
        AppMenuItem(
            stringResource(R.string.browser_subfolders_too),
            active = view.subfolders,
            trailingIcon = if (view.subfolders) Icons.Filled.Check else null,
            onClick = { view.onSubfolders(!view.subfolders) }
        )
    }
    AppMenuItem(
        stringResource(R.string.browser_use_the_default),
        leadingIcon = Icons.Filled.Refresh,
        supportingText = when {
            view.custom -> "${view.inheritedFrom?.let { "From $it" } ?: "Look and feel's"}: ${view.defaultLabel}"
            view.inheritedFrom != null -> stringResource(R.string.browser_same_as, view.inheritedFrom)
            else -> stringResource(R.string.browser_already_the_default)
        },
        enabled = view.custom,
        onClick = view.onReset
    )
}

/**
 * The entries of the sort page: "Sort by" going back ([onBack]), then the fields, the one sorted on with its direction.
 * Choosing a field leaves the menu open, so the effect can be seen and the direction flipped again: reselecting the field
 * already sorted on flips its direction (see AppSettings.changeSortOrder).
 */
@Composable
private fun SortMenuEntries(
    sortField: SortableField,
    sortAscending: Boolean,
    onSortFieldSelected: (SortableField) -> Unit,
    onBack: () -> Unit
) {
    AppMenuItem(stringResource(R.string.browser_sort_by), onClick = onBack, leadingIcon = Icons.AutoMirrored.Filled.KeyboardArrowLeft)
    AppMenuDivider()
    SortOptions.forEach { (name, field) ->
        val selected = field == sortField
        AppMenuItem(
            text = name,
            active = selected,
            trailingIcon = if (!selected) null else if (sortAscending) Icons.Filled.ArrowUpward else Icons.Filled.ArrowDownward,
            trailingIconDescription = if (sortAscending) stringResource(R.string.browser_ascending) else stringResource(R.string.browser_descending),
            onClick = { onSortFieldSelected(field) }
        )
    }
}

/** The space between the cards of the status row. */
private val SortRowGap = 8.dp

/**
 * What each card of the status row needs to show all of itself, in the row's order: Cancel and Thumbnails. From their
 * text as it's drawn, plus their icon and padding (see [FloatingCardButton]).
 */
@Composable
private fun statusCardNeeds(): List<Dp> {
    // The app's texts, for those its callbacks show
    val res = LocalContext.current.resources
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val large = MaterialTheme.typography.labelLarge
    return remember(measurer, density, large) {
        fun textWidth(text: String): Dp = with(density) { measurer.measure(text, large, maxLines = 1).size.width.toDp() }
        val padding = CardPadding * 2
        listOf(padding + 18.dp + 6.dp + textWidth(res.getString(R.string.browser_cancel)), padding + 18.dp + 6.dp + textWidth(res.getString(R.string.browser_thumbnails)))
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
    // A preview (in Android Studio) shows one frame: its matches are there at once
    val inPreview = LocalInspectionMode.current
    fun matching(text: String) = candidates.filter { it.name.contains(text, ignoreCase = true) }.sortedWith(order)
    var matches by remember { mutableStateOf(if (inPreview && query.isNotBlank()) matching(query) else emptyList()) }
    LaunchedEffect(candidates, fieldValue.text, order) {
        val text = fieldValue.text
        matches = if (text.isBlank()) {
            emptyList()
        } else {
            withContext(Dispatchers.Default) { matching(text) }
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
                    shape = AppShapes.ExtraLarge,
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
                                        Icon(Icons.Filled.Clear, contentDescription = stringResource(R.string.browser_clear_search))
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
                            if (fieldValue.text.isNotBlank()) stringResource(R.string.browser_showing_of, matches.size, candidates.size) else null,
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
                        text = stringResource(R.string.browser_more_matches, matches.size - shown.size),
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

// ---- Previews ----

/** The row above the list: with a transfer to cancel, with held-back thumbnails, and with both. */
@ThemePreviews
@Composable
private fun StatusCardsPreview() {
    PreviewTheme {
        Column {
            StatusCards(onCancelTransfer = {}, onLoadThumbnails = null)
            StatusCards(onCancelTransfer = null, onLoadThumbnails = {})
            StatusCards(onCancelTransfer = {}, onLoadThumbnails = {})
        }
    }
}

/** The filesystem's ⋮ menu, open: New, Sort by (sorted on name), View, and Settings; and an open list's, its downloads first. */
@ThemePreviews
@Composable
private fun ScreenMenuPreview() {
    PreviewTheme(padding = 8.dp) {
        Row {
            AppMenuPreview { ScreenMenuEntries({}, PreviewSort, PreviewActions, onOpenSettings = {}, view = PreviewView) }
            AppMenuPreview { ScreenMenuEntries({}, PreviewSort, PreviewListActions, onOpenSettings = {}, view = PreviewView) }
        }
    }
}

/** Its pages: the sorting (on size, largest first), View (this folder's own grid, for its subfolders too) and New. */
@ThemePreviews
@Composable
private fun ScreenMenuPagesPreview() {
    PreviewTheme(padding = 8.dp) {
        Row {
            AppMenuPreview {
                ScreenMenuEntries({}, SortChoice(SortableField.SIZE, ascending = false) {}, PreviewActions, onOpenSettings = {}, view = PreviewView, startPage = ScreenMenuPage.Sort)
            }
            AppMenuPreview {
                ScreenMenuEntries(
                    {}, PreviewSort, PreviewActions, onOpenSettings = {},
                    view = ViewChoice(grid = true, compact = false, custom = true, defaultLabel = "List", {}, {}, {}, subfolders = true),
                    startPage = ScreenMenuPage.View
                )
            }
            AppMenuPreview { ScreenMenuEntries({}, PreviewSort, PreviewActions, onOpenSettings = {}, view = PreviewView, startPage = ScreenMenuPage.New) }
        }
    }
}

private val PreviewSort = SortChoice(SortableField.NAME, ascending = true) {}
private val PreviewView = ViewChoice(grid = false, compact = false, custom = false, defaultLabel = "List", {}, {}, {})
private val PreviewActions = listOf(
    ScreenAction(Icons.Filled.Upload, "Upload") {},
    ScreenAction(Icons.Filled.CreateNewFolder, "New folder") {},
    ScreenAction(Icons.Filled.Link, "Import files by ID") {}
)
private val PreviewListActions = listOf(
    ScreenAction(
        Icons.Filled.Download,
        "Download all",
        group = ScreenActionGroup.PAGE,
        choices = listOf(ScreenAction(Icons.Filled.Download, "Separately") {}, ScreenAction(Icons.Filled.FolderZip, "As one ZIP") {})
    ) {}
)

@ThemePreviews
@Composable
private fun PathBreadcrumbPreview() {
    PreviewTheme {
        PathBreadcrumb(
            pathSegments = listOf("me", "Photos", "2026", "Holiday").runningReduce { a, b -> "$a/$b" }.map { PathSegment(it.substringAfterLast('/'), it) },
            onPathSegmentClick = {}
        )
    }
}

/** Searching "s": the search card and its matches under it, over the dimmed screen. */
@ScreenPreviews
@Composable
private fun SearchModalPreview() {
    PreviewTheme {
        SearchModal(
            query = "s",
            onQueryChange = {},
            candidates = PreviewSamples.files,
            order = fileComparator(SortableField.NAME, ascending = true),
            resultLimit = 0,
            thumbnailFor = { null },
            placeholder = "Search your files",
            onResultClick = {},
            onDismiss = {},
            status = "Looking through subfolders… 12 so far"
        )
    }
}
