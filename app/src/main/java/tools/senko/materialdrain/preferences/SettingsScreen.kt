package tools.senko.materialdrain.preferences

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import tools.senko.materialdrain.ui.icons.automirrored.filled.KeyboardArrowRight
import tools.senko.materialdrain.ui.icons.automirrored.filled.OpenInNew
import tools.senko.materialdrain.ui.icons.filled.Info
import tools.senko.materialdrain.ui.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import tools.senko.materialdrain.settings.AppSettings
import tools.senko.materialdrain.ui.LocalReduceMotion
import tools.senko.materialdrain.ui.components.DotScrollbar
import tools.senko.materialdrain.ui.components.pageTransition
import tools.senko.materialdrain.ui.preview.PreviewTheme
import tools.senko.materialdrain.ui.preview.ScreenPreviews
import tools.senko.materialdrain.ui.preview.LongScreenPreviews

/** The corners of the settings' cards and tiles, the same as the app's other cards. */
private val CardShape = RoundedCornerShape(20.dp)

/** The space between a row and the card's edge. */
private val RowStart = 16.dp

/** How far a setting which belongs to another one is moved in, per level. */
private val Indent = 24.dp

/** Where the tree line of a level runs, from the start of the indent before it. */
private val GuideOffset = 10.dp

/** How long the explanation behind a row's ⓘ takes to unfold. */
private const val UNFOLD_MS = 200

/** How dim the settings of a switch which is off are. */
private const val DIMMED_ALPHA = 0.38f

/**
 * The settings: a tile per category, its icon on a tile of one of the theme's colours so the categories are told apart
 * at a glance, and the account at the top with more in it. A tile opens the category's page with all of its settings,
 * the deepest the settings go: a setting's own settings are indented under it there, not a page further in. What the
 * categories and their settings are is defined in [SettingsCatalog].
 *
 * @param categoryId the opened page, null for the settings themselves
 */
@Composable
fun SettingsScreenContent(
    categoryId: String?,
    onCategoryChange: (String?) -> Unit,
    appSettings: AppSettings,
    authViewModel: AuthViewModel,
    accountViewModel: AccountViewModel,
    apiKeyInput: String,
    onApiKeyInputChange: (String) -> Unit,
    providerSettingsViewModel: ProviderSettingsViewModel,
    fabHeight: Dp,
    isFabVisible: Boolean,
    onNavigateBack: () -> Unit
) {
    // Back leaves a page first, only then the settings
    BackHandler(enabled = true) {
        if (categoryId != null) onCategoryChange(null) else onNavigateBack()
    }

    val environment = SettingsEnvironment(appSettings, authViewModel, accountViewModel, apiKeyInput, onApiKeyInputChange, providerSettingsViewModel)
    val reduceMotion = LocalReduceMotion.current
    // Each page keeps how far it was scrolled, for when it's come back to
    val pageStates = rememberSaveableStateHolder()

    AnimatedContent(
        targetState = settingsCategory(categoryId),
        transitionSpec = { pageTransition(forward = targetState != null, reduceMotion = reduceMotion) },
        label = "settingsCategory",
        modifier = Modifier.fillMaxSize()
    ) { category ->
        // Room after the last row for the button over the bottom of the screen (the Save button, or the navigation
        // button of the prototype): its height, the margin it keeps from the edge, and a gap above it
        val bottomRoom = if (isFabVisible) fabHeight + 32.dp else 16.dp
        pageStates.SaveableStateProvider(category?.id ?: "categories") {
            if (category == null) {
                SettingsHome(environment, onOpen = { onCategoryChange(it.id) }, bottomRoom = bottomRoom)
            } else {
                SettingsCategoryPage(category, environment, bottomRoom)
            }
        }
    }
}

/**
 * The settings themselves: a tile per category, which opens its page. A category with a tile of its own (the account)
 * stands a little apart from the others.
 */
@Composable
private fun SettingsHome(environment: SettingsEnvironment, onOpen: (SettingsCategory) -> Unit, bottomRoom: Dp) {
    val scrollState = rememberScrollState()
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = bottomRoom)
        ) {
            SettingsCatalog.forEach { category ->
                val tile = category.tile
                if (tile != null) {
                    tile(environment) { onOpen(category) }
                    Spacer(Modifier.size(4.dp))
                } else {
                    PageTile(category, environment) { onOpen(category) }
                }
            }
        }
        DotScrollbar(state = scrollState)
    }
}

/**
 * A category's page (the third level, the deepest the settings go): its rows on a card, and its custom parts (forms, the
 * account) standing on their own between them.
 */
@Composable
private fun SettingsCategoryPage(category: SettingsCategory, environment: SettingsEnvironment, bottomRoom: Dp) {
    val scrollState = rememberScrollState()
    // The custom parts (forms, the account) stand alone; the rows between them are put on a card together
    val blocks = remember(category) {
        category.items.fold(mutableListOf<List<SettingsItem>>()) { blocks, item ->
            val last = blocks.lastOrNull()
            if (item !is SettingsItem.Custom && last != null && last.none { it is SettingsItem.Custom }) {
                blocks[blocks.lastIndex] = last + item
            } else {
                blocks += listOf(item)
            }
            blocks
        }
    }
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = bottomRoom)
        ) {
            blocks.forEach { block ->
                val custom = block.singleOrNull() as? SettingsItem.Custom
                if (custom != null) {
                    Column(modifier = Modifier.fillMaxWidth()) { with(custom) { environment.content() } }
                } else {
                    Card { SettingsItems(block, environment, depth = 0, enabled = true) }
                }
            }
        }
        DotScrollbar(state = scrollState)
    }
}

// ---- Colours ----

/** A category's colours: its icon's tile, and the icon on it. */
private class CategoryColors(val container: Color, val content: Color)

/** The colours of a category's [tone], from the theme, like the tiles of the files (see FileIcon). */
@Composable
private fun categoryColors(tone: CategoryTone): CategoryColors {
    val scheme = MaterialTheme.colorScheme
    return when (tone) {
        CategoryTone.PRIMARY -> CategoryColors(scheme.primaryContainer, scheme.onPrimaryContainer)
        CategoryTone.SECONDARY -> CategoryColors(scheme.secondaryContainer, scheme.onSecondaryContainer)
        CategoryTone.TERTIARY -> CategoryColors(scheme.tertiaryContainer, scheme.onTertiaryContainer)
        CategoryTone.NEUTRAL -> CategoryColors(scheme.surfaceContainerHighest, scheme.onSurfaceVariant)
    }
}

/** The icon of a category on a squircle of its colour, like the tiles of the files. */
@Composable
private fun CategoryIcon(icon: ImageVector, colors: CategoryColors, size: Dp) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(size).clip(RoundedCornerShape(size / 4)).background(colors.container)
    ) {
        Icon(icon, contentDescription = null, tint = colors.content, modifier = Modifier.size(size * 0.55f))
    }
}

// ---- Cards and tiles ----

@Composable
private fun Card(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(bottom = 6.dp),
        content = content
    )
}

/** A category, which opens its page: its icon, name, what it holds and a short live status. */
@Composable
private fun PageTile(category: SettingsCategory, environment: SettingsEnvironment, onOpen: () -> Unit) {
    val colors = categoryColors(category.tone)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .clickable(onClick = onOpen)
            .padding(horizontal = 12.dp, vertical = 12.dp)
    ) {
        CategoryIcon(category.icon, colors, 40.dp)
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(category.title, style = MaterialTheme.typography.titleMedium)
            OneLine(category.summary)
        }
        category.status?.invoke(environment)?.let { status ->
            Text(status, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 8.dp))
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ---- Rows ----

/**
 * [items] one under the other, [depth] levels in. A setting's children follow it, a level further in; they're
 * [enabled] only while the switch they belong to is on (and everything above it).
 */
@Composable
private fun SettingsItems(items: List<SettingsItem>, environment: SettingsEnvironment, depth: Int, enabled: Boolean) {
    items.forEachIndexed { index, item ->
        SettingsItemBlock(item, environment, depth, enabled, isLast = index == items.lastIndex)
    }
}

/**
 * One item with whatever belongs to it: its explanation when unfolded, and its children. A child draws its part of the
 * tree line in the indent before it: down past it to the next one, or, for the last one, to its middle and across.
 */
@Composable
private fun SettingsItemBlock(item: SettingsItem, environment: SettingsEnvironment, depth: Int, enabled: Boolean, isLast: Boolean) {
    val reduceMotion = LocalReduceMotion.current
    // Fading the settings of a switch that went off: small, so short
    val alpha by animateFloatAsState(if (enabled) 1f else DIMMED_ALPHA, tween(if (reduceMotion) 0 else 150), label = "settingDim")
    val lineColor = MaterialTheme.colorScheme.outlineVariant
    // The middle of the row itself (not of its explanation or children), where the last child's line turns
    var rowHeight by remember { mutableIntStateOf(0) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(alpha)
            .then(
                if (depth == 0) Modifier else Modifier.drawBehind {
                    val x = (RowStart + Indent * (depth - 1) + GuideOffset).toPx()
                    val middle = rowHeight / 2f
                    val stroke = 1.dp.toPx()
                    drawLine(lineColor, Offset(x, 0f), Offset(x, if (isLast) middle else size.height), stroke)
                    drawLine(lineColor, Offset(x, middle), Offset(x + (Indent - GuideOffset - 4.dp).toPx(), middle), stroke)
                }
            )
    ) {
        val rowModifier = Modifier.onSizeChanged { rowHeight = it.height }
        val start = RowStart + Indent * depth
        when (item) {
            is SettingsItem.Toggle -> {
                val checked = with(item) { environment.isChecked() }
                var unfolded by rememberSaveable { mutableStateOf(false) }
                SettingRow(
                    title = item.title, summary = item.summary, start = start, depth = depth, enabled = enabled,
                    info = item.info, unfolded = unfolded, onInfo = { unfolded = !unfolded },
                    onClick = { with(item) { environment.onCheckedChange(!checked) } },
                    modifier = rowModifier
                ) {
                    Switch(checked = checked, onCheckedChange = null, enabled = enabled)
                }
                Explanation(item.info, unfolded, start)
                SettingsItems(item.children, environment, depth + 1, enabled && checked)
            }
            is SettingsItem.Choice<*> -> {
                var unfolded by rememberSaveable { mutableStateOf(false) }
                @Suppress("UNCHECKED_CAST")
                ChoiceRow(item as SettingsItem.Choice<Any?>, environment, start, depth, enabled, unfolded, { unfolded = !unfolded }, rowModifier)
                Explanation(item.info, unfolded, start)
            }
            is SettingsItem.Action -> {
                val context = LocalContext.current
                var unfolded by rememberSaveable { mutableStateOf(false) }
                SettingRow(
                    title = item.title, summary = item.summary, start = start, depth = depth, enabled = enabled,
                    icon = item.icon, info = item.info, unfolded = unfolded, onInfo = { unfolded = !unfolded },
                    onClick = { with(item) { environment.onClick(context) } },
                    modifier = rowModifier
                ) {
                    // Leaves the app: says so, instead of the arrow of a page
                    if (item.external) {
                        Icon(
                            Icons.AutoMirrored.Filled.OpenInNew,
                            contentDescription = "Opens outside the app",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(end = 8.dp).size(18.dp)
                        )
                    }
                }
                Explanation(item.info, unfolded, start)
                SettingsItems(item.children, environment, depth + 1, enabled)
            }
            is SettingsItem.Note -> Text(
                text = item.text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = rowModifier.fillMaxWidth().padding(start = start, end = 16.dp, top = 8.dp, bottom = 8.dp)
            )
            is SettingsItem.Header -> Text(
                text = item.title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = rowModifier.fillMaxWidth().padding(start = start, end = 16.dp, top = 12.dp, bottom = 4.dp)
            )
            is SettingsItem.Custom -> Column(modifier = rowModifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                with(item) { environment.content() }
            }
        }
    }
}

/**
 * The row of a switch or an action: its title over its summary, the ⓘ which unfolds its explanation, and [trailing] (the
 * switch, the mark of a link). A setting a level in is a little smaller than the one it belongs to.
 */
@Composable
private fun SettingRow(
    title: String,
    summary: String?,
    start: Dp,
    depth: Int,
    enabled: Boolean,
    info: String?,
    unfolded: Boolean,
    onInfo: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    trailing: @Composable RowScope.() -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = if (depth == 0) 56.dp else 48.dp)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(start = start, end = 12.dp, top = 6.dp, bottom = 6.dp)
    ) {
        icon?.let {
            Icon(it, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(16.dp))
        }
        RowTexts(title, summary, depth, Modifier.weight(1f))
        if (info != null) InfoButton(unfolded, enabled, onInfo)
        trailing()
    }
}

@Composable
private fun RowTexts(title: String, summary: String?, depth: Int, modifier: Modifier) {
    Column(modifier = modifier) {
        Text(title, style = if (depth == 0) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.bodyMedium)
        summary?.let { OneLine(it) }
    }
}

/** A choice of a few options: its title, then the options side by side, the chosen one filled. */
@Composable
private fun <T> ChoiceRow(
    item: SettingsItem.Choice<T>,
    environment: SettingsEnvironment,
    start: Dp,
    depth: Int,
    enabled: Boolean,
    unfolded: Boolean,
    onInfo: () -> Unit,
    modifier: Modifier
) {
    val selected = with(item) { environment.selected() }
    Column(modifier = modifier.fillMaxWidth().padding(start = start, end = 12.dp, top = 6.dp, bottom = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.heightIn(min = 40.dp)) {
            RowTexts(item.title, item.summary, depth, Modifier.weight(1f))
            if (item.info != null) InfoButton(unfolded, enabled, onInfo)
        }
        Spacer(Modifier.size(6.dp))
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth().padding(end = 4.dp)) {
            item.options.forEachIndexed { index, (label, value) ->
                SegmentedButton(
                    selected = value == selected,
                    onClick = { with(item) { environment.onSelect(value) } },
                    enabled = enabled,
                    shape = SegmentedButtonDefaults.itemShape(index, item.options.size),
                    label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                )
            }
        }
    }
}

/** The ⓘ of a row: unfolds its explanation under it, and is filled while it's unfolded. */
@Composable
private fun InfoButton(unfolded: Boolean, enabled: Boolean, onClick: () -> Unit) {
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(40.dp)) {
        Icon(
            if (unfolded) Icons.Filled.Info else Icons.Outlined.Info,
            contentDescription = if (unfolded) "Hide the explanation" else "Explain this setting",
            tint = if (unfolded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
    }
}

/**
 * A row's explanation, unfolded under it by its ⓘ. The page grows by it, so it unfolds and folds rather than jumping;
 * with reduced animations it only fades.
 */
@Composable
private fun Explanation(text: String?, unfolded: Boolean, start: Dp) {
    if (text == null) return
    val reduceMotion = LocalReduceMotion.current
    AnimatedVisibility(
        visible = unfolded,
        enter = if (reduceMotion) fadeIn(tween(100)) else expandVertically(tween(UNFOLD_MS)) + fadeIn(tween(UNFOLD_MS)),
        exit = if (reduceMotion) fadeOut(tween(100)) else shrinkVertically(tween(UNFOLD_MS)) + fadeOut(tween(UNFOLD_MS / 2))
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth().padding(start = start, end = 16.dp, bottom = 10.dp)
        )
    }
}

/**
 * A row's description: one line at most, so every row has the same height and its switch stays centred on it. A longer
 * explanation goes behind the row's ⓘ instead.
 */
@Composable
private fun OneLine(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

// ---- Previews ----

/**
 * The settings on [categoryId]'s page (null: the settings themselves), with the app's own settings and ViewModels and a
 * made-up account (see rememberPreviewSettings): what's shown is worked out by the real code, only the account is made up.
 */
@Composable
private fun SettingsPreview(categoryId: String?, signedIn: Boolean = true) {
    PreviewTheme {
        val environment = rememberPreviewSettings(signedIn)
        SettingsScreenContent(
            categoryId = categoryId,
            onCategoryChange = {},
            appSettings = environment.appSettings,
            authViewModel = environment.authViewModel,
            accountViewModel = environment.accountViewModel,
            apiKeyInput = "",
            onApiKeyInputChange = {},
            providerSettingsViewModel = environment.providerSettingsViewModel,
            fabHeight = 0.dp,
            isFabVisible = false,
            onNavigateBack = {}
        )
    }
}

/** The settings: the account's tile, then a tile per category. */
@ScreenPreviews
@Composable
private fun SettingsHomePreview() = SettingsPreview(null)

@ScreenPreviews
@Composable
private fun SettingsHomeSignedOutPreview() = SettingsPreview(null, signedIn = false)

/** The account page, signed in to the made-up account: all of it, each card under the other. */
@LongScreenPreviews
@Composable
private fun SettingsAccountPreview() = SettingsPreview(ACCOUNT_CATEGORY_ID)

/** The account page signed out: the login. */
@ScreenPreviews
@Composable
private fun SettingsAccountSignedOutPreview() = SettingsPreview(ACCOUNT_CATEGORY_ID, signedIn = false)

/** The hosts: Pixeldrain and a few of the example configs. */
@ScreenPreviews
@Composable
private fun SettingsHostsPreview() = SettingsPreview(HOSTS_CATEGORY_ID)

@ScreenPreviews
@Composable
private fun SettingsDataSaverPreview() = SettingsPreview("data_saver")

@ScreenPreviews
@Composable
private fun SettingsLookPreview() = SettingsPreview("look")

@ScreenPreviews
@Composable
private fun SettingsFilesPreview() = SettingsPreview("files")

@ScreenPreviews
@Composable
private fun SettingsNotificationsPreview() = SettingsPreview("notifications")

@ScreenPreviews
@Composable
private fun SettingsSecurityPreview() = SettingsPreview("security")

@ScreenPreviews
@Composable
private fun SettingsDeveloperPreview() = SettingsPreview("developer")

@ScreenPreviews
@Composable
private fun SettingsAboutPreview() = SettingsPreview("about")
