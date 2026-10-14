package tools.senko.materialdrain

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import tools.senko.materialdrain.R
import tools.senko.materialdrain.provider.api.HostScreen
import tools.senko.materialdrain.provider.api.ScreenConfig

// Define the screens in the app
enum class Screen(val title: String, @DrawableRes val iconResId: Int?) {
    Upload("Upload", R.drawable.icon_upload),
    Files("Files", R.drawable.icon_folder_outlined),
    Filesystem("Filesystem", R.drawable.icon_hard_drive_outlined),
    FileDetail("File Details", null), // Using null for now, direct usage for its icon
    Lists("Lists", R.drawable.icon_list),
    Settings("Settings", R.drawable.icon_settings_outlined)
}

/** [HostScreen] is provider-api's own (it can't reference [Screen], which lives in the app module); this is the
 * one place the two are matched up. */
fun HostScreen.toScreen(): Screen = when (this) {
    HostScreen.UPLOAD -> Screen.Upload
    HostScreen.FILES -> Screen.Files
    HostScreen.LISTS -> Screen.Lists
    HostScreen.FILESYSTEM -> Screen.Filesystem
}

/**
 * One tab of the active host: a screen of [screen]'s kind, set up by its entry in the host's config ([config]; null for
 * the built-in Pixeldrain, which has none). A host can have several tabs of one kind (two Filesystem tabs with their own
 * roots), told apart by [id], see ScreenConfig.tabId.
 */
data class Tab(val screen: Screen, val config: ScreenConfig? = null) {
    val id: String get() = config?.tabId ?: screen.name.lowercase()

    /** Its own name from the config, or the screen's. */
    val title: String get() = config?.name?.takeIf { it.isNotBlank() } ?: screen.title
}
data class FabDetails(
    /** The button of one screen is a different button than that of another screen, they only share the widget. */
    val screen: Screen,
    @DrawableRes val iconResId: Int,
    val text: String?,
    val onClick: () -> Unit,
    val isExtended: Boolean,
    val yOffset: androidx.compose.ui.unit.Dp = 0.dp,
    /** Other texts this button shows, its text area is as wide as the widest of them so it never changes size. */
    val reserveTextWidthFor: List<String> = emptyList(),
    /** The icon makes a full turn each time this value increases. */
    val iconSpinTrigger: Int = 0
)
@Composable
fun BottomNavigationBar(
    currentTab: Tab?,
    tabs: List<Tab>,
    onTabSelected: (Tab) -> Unit
) {
    NavigationBar {
        tabs.forEach { tab ->
            tab.screen.iconResId?.let { // Ensure iconResId is not null before using
                NavigationBarItem(
                    icon = { Icon(painterResource(id = it), contentDescription = tab.title) },
                    label = { Text(tab.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    selected = currentTab?.id == tab.id && currentTab.screen == tab.screen,
                    onClick = { onTabSelected(tab) }
                )
            }
        }
    }
}
