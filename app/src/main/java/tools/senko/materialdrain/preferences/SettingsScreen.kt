package tools.senko.materialdrain.preferences

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import tools.senko.materialdrain.settings.AppSettings
import tools.senko.materialdrain.ui.LocalReduceMotion
import androidx.compose.foundation.layout.Box
import tools.senko.materialdrain.ui.components.DotScrollbar
import tools.senko.materialdrain.ui.components.pageTransition

/**
 * The settings: a list of categories, each of which opens a page with its settings. What the categories and
 * their settings are is defined in [SettingsCatalog].
 *
 * @param categoryId the opened category, null for the list of categories
 */
@Composable
fun SettingsScreenContent(
    categoryId: String?,
    onCategoryChange: (String?) -> Unit,
    appSettings: AppSettings,
    authViewModel: AuthViewModel,
    apiKeyInput: String,
    onApiKeyInputChange: (String) -> Unit,
    providerSettingsViewModel: ProviderSettingsViewModel,
    fabHeight: Dp,
    isFabVisible: Boolean,
    onNavigateBack: () -> Unit
) {
    // Back leaves a category first, only then the settings
    BackHandler(enabled = true) {
        if (categoryId != null) onCategoryChange(null) else onNavigateBack()
    }

    val environment = SettingsEnvironment(appSettings, authViewModel, apiKeyInput, onApiKeyInputChange, providerSettingsViewModel)
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
                SettingsCategoryList(onCategoryClick = { onCategoryChange(it.id) }, bottomRoom = bottomRoom)
            } else {
                SettingsCategoryPage(category, environment, bottomRoom)
            }
        }
    }
}

/**
 * The list of categories, in sections (see [SettingsGroup]): under each section's title, the standard Material list, an
 * icon, a title and a summary per row.
 */
@Composable
private fun SettingsCategoryList(onCategoryClick: (SettingsCategory) -> Unit, bottomRoom: Dp) {
    val scrollState = rememberScrollState()
    // In the order of the sections; within one, in the order of the catalog
    val sections = remember { SettingsCatalog.groupBy { it.group }.toSortedMap(compareBy { it.ordinal }) }
    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().verticalScroll(scrollState).padding(bottom = bottomRoom)) {
            sections.forEach { (group, categories) ->
                SectionTitle(group.title)
                categories.forEach { category ->
                    ListItem(
                        headlineContent = { Text(category.title) },
                        supportingContent = { OneLine(category.summary) },
                        leadingContent = { Icon(category.icon, contentDescription = null) },
                        trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) },
                        modifier = Modifier.clickable { onCategoryClick(category) }
                    )
                }
            }
        }
        DotScrollbar(state = scrollState)
    }
}

/** The title of a group of rows: of a section of the categories, or a [SettingsItem.Header] on a page. */
@Composable
private fun SectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp)
    )
}

/** One category: every item of the catalog rendered according to its kind. */
@Composable
private fun SettingsCategoryPage(
    category: SettingsCategory,
    environment: SettingsEnvironment,
    bottomRoom: Dp
) {
    val scrollState = rememberScrollState()
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(bottom = bottomRoom)
        ) {
            category.items.forEach { item -> SettingsItemRow(item, environment) }
        }
        DotScrollbar(state = scrollState)
    }
}

/**
 * A row's description: one line at most, so every row has the same height and its icon and switch stay centred on it.
 * A longer explanation goes in a [SettingsItem.Note] under the row instead.
 */
@Composable
private fun OneLine(text: String) {
    Text(text, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

@Composable
private fun SettingsItemRow(item: SettingsItem, environment: SettingsEnvironment) {
    val transparent = ListItemDefaults.colors(containerColor = Color.Transparent)
    when (item) {
        is SettingsItem.Toggle -> {
            val checked = with(item) { environment.isChecked() }
            ListItem(
                headlineContent = { Text(item.title) },
                supportingContent = item.summary?.let { summary -> { OneLine(summary) } },
                leadingContent = item.icon?.let { icon -> { Icon(icon, contentDescription = null) } },
                trailingContent = { Switch(checked = checked, onCheckedChange = null) },
                colors = transparent,
                modifier = Modifier.clickable { with(item) { environment.onCheckedChange(!checked) } }
            )
        }
        is SettingsItem.Action -> {
            val context = LocalContext.current
            ListItem(
                headlineContent = { Text(item.title) },
                supportingContent = item.summary?.let { summary -> { OneLine(summary) } },
                leadingContent = item.icon?.let { icon -> { Icon(icon, contentDescription = null) } },
                colors = transparent,
                modifier = Modifier.clickable { with(item) { environment.onClick(context) } }
            )
        }
        is SettingsItem.Note -> Text(
            text = item.text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
        )
        is SettingsItem.Header -> SectionTitle(item.title)
        is SettingsItem.Custom -> Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            with(item) { environment.content() }
        }
    }
}
