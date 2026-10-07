package tools.senko.materialdrain

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import tools.senko.materialdrain.R
import tools.senko.materialdrain.provider.api.HostScreen

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
    currentScreen: Screen,
    navBarOrder: List<Screen>,
    labelFor: (Screen) -> String = { it.title },
    onScreenSelected: (Screen) -> Unit
) {
    NavigationBar {
        navBarOrder.forEach { screen ->
            screen.iconResId?.let { // Ensure iconResId is not null before using
                val label = labelFor(screen)
                NavigationBarItem(
                    icon = { Icon(painterResource(id = it), contentDescription = label) },
                    label = { Text(label) },
                    selected = currentScreen == screen,
                    onClick = { onScreenSelected(screen) }
                )
            }
        }
    }
}
