package tools.senko.materialdrain.preferences

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import tools.senko.materialdrain.settings.AppSettings

/**
 * What the items of the settings can use. Add to it when a setting needs something else; it is the receiver
 * of every lambda in [SettingsItem], so an item just uses `appSettings`, `authViewModel`, ...
 */
class SettingsEnvironment(
    val appSettings: AppSettings,
    val authViewModel: AuthViewModel,
    val apiKeyInput: String,
    val onApiKeyInputChange: (String) -> Unit
)

/** One entry of a settings category. The kinds of entries are rendered by `SettingsCategoryPage`. */
sealed interface SettingsItem {

    /** A switch. [isChecked] runs while composing, so it can collect a flow to stay up to date. */
    class Toggle(
        val title: String,
        val summary: String? = null,
        val icon: ImageVector? = null,
        val isChecked: @Composable SettingsEnvironment.() -> Boolean,
        val onCheckedChange: SettingsEnvironment.(Boolean) -> Unit
    ) : SettingsItem

    /** A row which does something when it is tapped. */
    class Action(
        val title: String,
        val summary: String? = null,
        val icon: ImageVector? = null,
        val onClick: SettingsEnvironment.(context: Context) -> Unit
    ) : SettingsItem

    /** A short piece of explanation. */
    class Note(val text: String) : SettingsItem

    /** A small title above a group of items. */
    class Header(val title: String) : SettingsItem

    /** Anything else, such as a form. */
    class Custom(val content: @Composable SettingsEnvironment.() -> Unit) : SettingsItem
}

/**
 * A page of the settings which is opened from the list of categories.
 *
 * @param hasSaveButton the settings of this category are saved with the Save button (the FAB) instead of at once
 */
class SettingsCategory(
    val id: String,
    val title: String,
    val summary: String,
    val icon: ImageVector,
    val items: List<SettingsItem>,
    val hasSaveButton: Boolean = false
)
