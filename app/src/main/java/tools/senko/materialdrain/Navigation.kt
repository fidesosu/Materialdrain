package tools.senko.materialdrain

import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import tools.senko.materialdrain.provider.api.HostScreen
import tools.senko.materialdrain.provider.api.ScreenConfig
import tools.senko.materialdrain.ui.preview.PreviewTheme
import tools.senko.materialdrain.ui.preview.ThemePreviews
import tools.senko.materialdrain.ui.icons.symbols.Folder
import tools.senko.materialdrain.ui.icons.symbols.HardDrive
import tools.senko.materialdrain.ui.icons.symbols.Lists
import tools.senko.materialdrain.ui.icons.symbols.Settings
import tools.senko.materialdrain.ui.icons.symbols.Symbols
import tools.senko.materialdrain.ui.icons.symbols.Upload
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.ui.platform.LocalConfiguration
import tools.senko.materialdrain.ui.components.AppMenu
import tools.senko.materialdrain.ui.components.AppMenuItem
import tools.senko.materialdrain.ui.components.MenuSide
import tools.senko.materialdrain.ui.icons.filled.Apps
import tools.senko.materialdrain.ui.icons.filled.Cloud
import tools.senko.materialdrain.ui.icons.filled.CloudSync
import tools.senko.materialdrain.ui.icons.filled.Code
import tools.senko.materialdrain.ui.icons.filled.Description
import tools.senko.materialdrain.ui.icons.filled.Dns
import tools.senko.materialdrain.ui.icons.filled.Folder
import tools.senko.materialdrain.ui.icons.filled.FolderCopy
import tools.senko.materialdrain.ui.icons.filled.FolderShared
import tools.senko.materialdrain.ui.icons.filled.FolderZip
import tools.senko.materialdrain.ui.icons.filled.Image
import tools.senko.materialdrain.ui.icons.filled.Key
import tools.senko.materialdrain.ui.icons.filled.Movie
import tools.senko.materialdrain.ui.icons.filled.MusicNote
import tools.senko.materialdrain.ui.icons.filled.PhotoCamera
import tools.senko.materialdrain.ui.icons.filled.Storage

/** The screens of the app. [icon] is null for the one that isn't reached from the navigation, a file's details. */
enum class Screen(val title: String, private val iconOf: (() -> ImageVector)?) {
    Upload("Upload", { Symbols.Upload }),
    Files("Files", { Symbols.Folder }),
    Filesystem("Filesystem", { Symbols.HardDrive }),
    FileDetail("File Details", null),
    Lists("Lists", { Symbols.Lists }),
    Settings("Settings", { Symbols.Settings });

    // Looked up when asked for, so the enum doesn't build its icons when it's first touched
    val icon: ImageVector? get() = iconOf?.invoke()
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

    /** Its own icon from the config (see [TabIcons]), or the screen's. */
    val icon: ImageVector get() = config?.icon?.let { TabIcons[it.trim().lowercase()] } ?: requireNotNull(screen.icon)

    /** The group the config puts it in, in the navigation button's menu; null for the row of the tabs without one. */
    val section: String? get() = config?.section?.trim()?.takeIf { it.isNotEmpty() }
}

/** The icons a config can give a tab (ScreenConfig.icon), by name. */
val TabIcons: Map<String, ImageVector> by lazy {
    mapOf(
        "upload" to Symbols.Upload,
        "files" to Symbols.Folder,
        "folder" to Icons.Filled.Folder,
        "drive" to Symbols.HardDrive,
        "storage" to Icons.Filled.Storage,
        "server" to Icons.Filled.Dns,
        "cloud" to Icons.Filled.Cloud,
        "sync" to Icons.Filled.CloudSync,
        "list" to Symbols.Lists,
        "photo" to Icons.Filled.PhotoCamera,
        "image" to Icons.Filled.Image,
        "video" to Icons.Filled.Movie,
        "music" to Icons.Filled.MusicNote,
        "document" to Icons.Filled.Description,
        "code" to Icons.Filled.Code,
        "archive" to Icons.Filled.FolderZip,
        "backup" to Icons.Filled.FolderCopy,
        "shared" to Icons.Filled.FolderShared,
        "key" to Icons.Filled.Key
    )
}

/** How many tabs the bottom bar shows; with more, the last place is More, a menu of the rest. */
private const val MAX_BAR_TABS = 5

/** Whether [tab] is the one shown. */
private fun Tab.isCurrent(currentTab: Tab?): Boolean = currentTab?.id == id && currentTab.screen == screen
data class FabDetails(
    /** The button of one screen is a different button than that of another screen, they only share the widget. */
    val screen: Screen,
    val icon: ImageVector,
    val text: String?,
    val onClick: () -> Unit,
    val isExtended: Boolean,
    val yOffset: androidx.compose.ui.unit.Dp = 0.dp,
    /** Other texts this button shows, its text area is as wide as the widest of them so it never changes size. */
    val reserveTextWidthFor: List<String> = emptyList(),
    /** The icon makes a full turn each time this value increases. */
    val iconSpinTrigger: Int = 0
)
/**
 * The tabs at the bottom of the screen. Five fit: with more, the fifth place is More, which opens a menu of the rest (and
 * shows the one of them that's open). [alwaysShowLabels] off shows only the chosen tab's name (Look and feel).
 */
@Composable
fun BottomNavigationBar(
    currentTab: Tab?,
    tabs: List<Tab>,
    onTabSelected: (Tab) -> Unit,
    modifier: Modifier = Modifier,
    alwaysShowLabels: Boolean = true
) {
    val shown = if (tabs.size > MAX_BAR_TABS) tabs.take(MAX_BAR_TABS - 1) else tabs
    val more = tabs.drop(shown.size)
    NavigationBar(modifier = modifier) {
        shown.forEach { tab ->
            NavigationBarItem(
                icon = { Icon(tab.icon, contentDescription = tab.title) },
                label = { Text(tab.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                alwaysShowLabel = alwaysShowLabels,
                selected = tab.isCurrent(currentTab),
                onClick = { onTabSelected(tab) }
            )
        }
        if (more.isNotEmpty()) {
            var open by remember { mutableStateOf(false) }
            val openInMore = more.firstOrNull { it.isCurrent(currentTab) }
            NavigationBarItem(
                icon = {
                    Box {
                        Icon(openInMore?.icon ?: Icons.Filled.Apps, contentDescription = "More tabs")
                        // Above the bar, as the bar is at the bottom
                        AppMenu(expanded = open, onDismiss = { open = false }, side = MenuSide.Above) {
                            more.forEach { tab ->
                                AppMenuItem(
                                    text = tab.title,
                                    leadingIcon = tab.icon,
                                    active = tab.isCurrent(currentTab),
                                    onClick = { open = false; onTabSelected(tab) }
                                )
                            }
                        }
                    }
                },
                label = { Text(openInMore?.title ?: "More", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                alwaysShowLabel = alwaysShowLabels,
                selected = openInMore != null,
                onClick = { open = true }
            )
        }
    }
}

/**
 * The tabs at the side of the screen, instead of the bottom bar, where the screen is wide (a tablet, a phone on its side):
 * a bar along the bottom would stretch its few tabs far apart. It scrolls when there are more tabs than fit.
 */
@Composable
fun AppNavigationRail(
    currentTab: Tab?,
    tabs: List<Tab>,
    onTabSelected: (Tab) -> Unit,
    modifier: Modifier = Modifier,
    alwaysShowLabels: Boolean = true
) {
    NavigationRail(modifier = modifier) {
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.verticalScroll(rememberScrollState()).padding(vertical = 8.dp)
        ) {
            tabs.forEach { tab ->
                NavigationRailItem(
                    icon = { Icon(tab.icon, contentDescription = tab.title) },
                    label = { Text(tab.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    alwaysShowLabel = alwaysShowLabels,
                    selected = tab.isCurrent(currentTab),
                    onClick = { onTabSelected(tab) }
                )
            }
        }
    }
}

/** Whether the screen is wide enough for the tabs to go at its side (see [AppNavigationRail]). */
@Composable
fun isWideScreen(): Boolean = LocalConfiguration.current.screenWidthDp >= 600

// ---- Previews ----

/** The bottom bar with the tabs of the built-in Pixeldrain, Files chosen; and with the chosen tab's name alone. */
@ThemePreviews
@Composable
private fun BottomNavigationBarPreview() {
    PreviewTheme {
        val tabs = listOf(Screen.Upload, Screen.Files, Screen.Lists, Screen.Filesystem).map { Tab(it) }
        Column {
            BottomNavigationBar(currentTab = tabs[1], tabs = tabs, onTabSelected = {})
            BottomNavigationBar(currentTab = tabs[1], tabs = tabs, onTabSelected = {}, alwaysShowLabels = false)
        }
    }
}

/** A host with seven tabs, some with icons of their own: four, and More with the rest. */
@ThemePreviews
@Composable
private fun BottomNavigationBarMorePreview() {
    PreviewTheme {
        BottomNavigationBar(currentTab = ManyTabs[0], tabs = ManyTabs, onTabSelected = {})
    }
}

/** The tabs at the side, as a wide screen has them. */
@ThemePreviews
@Composable
private fun AppNavigationRailPreview() {
    PreviewTheme {
        AppNavigationRail(currentTab = ManyTabs[1], tabs = ManyTabs.take(4), onTabSelected = {}, modifier = Modifier.height(420.dp))
    }
}

/** Made-up tabs of a big host: Filesystem tabs with their own names, icons and sections. */
private val ManyTabs = listOf(
    Tab(Screen.Upload),
    Tab(Screen.Filesystem, ScreenConfig(HostScreen.FILESYSTEM, id = "photos", name = "Photos", icon = "photo")),
    Tab(Screen.Filesystem, ScreenConfig(HostScreen.FILESYSTEM, id = "music", name = "Music", icon = "music", section = "Media")),
    Tab(Screen.Filesystem, ScreenConfig(HostScreen.FILESYSTEM, id = "videos", name = "Videos", icon = "video", section = "Media")),
    Tab(Screen.Filesystem, ScreenConfig(HostScreen.FILESYSTEM, id = "backups", name = "Backups", icon = "backup", section = "Admin")),
    Tab(Screen.Filesystem, ScreenConfig(HostScreen.FILESYSTEM, id = "docs", name = "Documents", icon = "document")),
    Tab(Screen.Filesystem, ScreenConfig(HostScreen.FILESYSTEM, id = "shared", name = "Shared", icon = "shared", section = "Admin"))
)
