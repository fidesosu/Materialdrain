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
    val accountViewModel: AccountViewModel,
    val apiKeyInput: String,
    val onApiKeyInputChange: (String) -> Unit,
    val providerSettingsViewModel: ProviderSettingsViewModel
)

/**
 * One entry of a settings category, rendered by its kind (see SettingsScreen). [info] is a longer explanation, folded
 * behind the row's ⓘ. [children] are the settings which belong to this one: shown under it, indented on a tree line,
 * and (under a switch) dimmed and out of reach while it's off.
 */
sealed interface SettingsItem {

    /** A switch. [isChecked] runs while composing, so it can collect a flow to stay up to date. */
    class Toggle(
        val title: String,
        val summary: String? = null,
        val info: String? = null,
        val isChecked: @Composable SettingsEnvironment.() -> Boolean,
        val onCheckedChange: SettingsEnvironment.(Boolean) -> Unit,
        val children: List<SettingsItem> = emptyList()
    ) : SettingsItem

    /** One of a few [options] (their labels and values), chosen with buttons side by side in the row: never a menu. */
    class Choice<T>(
        val title: String,
        val summary: String? = null,
        val info: String? = null,
        val options: List<Pair<String, T>>,
        val selected: @Composable SettingsEnvironment.() -> T,
        val onSelect: SettingsEnvironment.(T) -> Unit
    ) : SettingsItem

    /** A row which does something when it is tapped; [external] when that leaves the app (marked with ↗). */
    class Action(
        val title: String,
        val summary: String? = null,
        val info: String? = null,
        val icon: ImageVector? = null,
        val external: Boolean = false,
        val onClick: SettingsEnvironment.(context: Context) -> Unit,
        val children: List<SettingsItem> = emptyList()
    ) : SettingsItem

    /** A short piece of explanation for the whole card. */
    class Note(val text: String) : SettingsItem

    /** A small title above a group of items. */
    class Header(val title: String) : SettingsItem

    /** Anything else, such as a form. */
    class Custom(val content: @Composable SettingsEnvironment.() -> Unit) : SettingsItem
}

/** Which of the theme's colours a category's icon tile has, so the tiles next to each other are told apart. */
enum class CategoryTone { PRIMARY, SECONDARY, TERTIARY, NEUTRAL }

/**
 * A category of the settings: a tile on the settings page, with its icon on a tile of its [tone], which opens a page with
 * its [items] (the deepest the settings go). [tile] draws the tile instead when it has a look of its own (the account);
 * [status] is a short live text at its end, such as how many hosts there are.
 *
 * @param hasSaveButton the settings of this category are saved with the Save button (the FAB) instead of at once
 */
class SettingsCategory(
    val id: String,
    val title: String,
    val summary: String,
    val icon: ImageVector,
    val tone: CategoryTone,
    val items: List<SettingsItem>,
    val tile: (@Composable SettingsEnvironment.(onOpen: () -> Unit) -> Unit)? = null,
    val status: (@Composable SettingsEnvironment.() -> String?)? = null,
    val hasSaveButton: Boolean = false
)
