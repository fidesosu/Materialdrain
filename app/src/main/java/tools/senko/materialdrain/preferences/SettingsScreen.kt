package tools.senko.materialdrain.preferences

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import tools.senko.materialdrain.settings.AppSettings
import tools.senko.materialdrain.ui.LocalReduceMotion

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

    AnimatedContent(
        targetState = settingsCategory(categoryId),
        transitionSpec = {
            val opening = targetState != null
            val transition: ContentTransform = when {
                reduceMotion -> fadeIn(tween(100)) togetherWith fadeOut(tween(100))
                opening -> (slideInHorizontally(tween(250)) { it / 4 } + fadeIn(tween(250))) togetherWith
                    (slideOutHorizontally(tween(250)) { -it / 4 } + fadeOut(tween(150)))
                else -> (slideInHorizontally(tween(250)) { -it / 4 } + fadeIn(tween(250))) togetherWith
                    (slideOutHorizontally(tween(250)) { it / 4 } + fadeOut(tween(150)))
            }
            transition.using(SizeTransform(clip = false) { _, _ -> snap() })
        },
        label = "settingsCategory",
        modifier = Modifier.fillMaxSize()
    ) { category ->
        if (category == null) {
            SettingsCategoryList(onCategoryClick = { onCategoryChange(it.id) })
        } else {
            SettingsCategoryPage(category, environment, fabHeight, isFabVisible)
        }
    }
}

/** The list of categories: the standard Material list, an icon, a title and a summary per row. */
@Composable
private fun SettingsCategoryList(onCategoryClick: (SettingsCategory) -> Unit) {
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        SettingsCatalog.forEach { category ->
            ListItem(
                headlineContent = { Text(category.title) },
                supportingContent = { Text(category.summary) },
                leadingContent = { Icon(category.icon, contentDescription = null) },
                trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) },
                modifier = Modifier.clickable { onCategoryClick(category) }
            )
        }
    }
}

/** One category: every item of the catalog rendered according to its kind. */
@Composable
private fun SettingsCategoryPage(
    category: SettingsCategory,
    environment: SettingsEnvironment,
    fabHeight: Dp,
    isFabVisible: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = if (isFabVisible) fabHeight + 16.dp else 16.dp)
    ) {
        category.items.forEach { item -> SettingsItemRow(item, environment) }
    }
}

@Composable
private fun SettingsItemRow(item: SettingsItem, environment: SettingsEnvironment) {
    val transparent = ListItemDefaults.colors(containerColor = Color.Transparent)
    when (item) {
        is SettingsItem.Toggle -> {
            val checked = with(item) { environment.isChecked() }
            ListItem(
                headlineContent = { Text(item.title) },
                supportingContent = item.summary?.let { summary -> { Text(summary) } },
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
                supportingContent = item.summary?.let { summary -> { Text(summary) } },
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
        is SettingsItem.Header -> Text(
            text = item.title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp)
        )
        is SettingsItem.Custom -> Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            with(item) { environment.content() }
        }
    }
}
