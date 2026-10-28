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
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import tools.senko.materialdrain.ui.HapticKind
import tools.senko.materialdrain.ui.HapticPlace
import tools.senko.materialdrain.ui.LocalAppHaptics
import tools.senko.materialdrain.ui.LocalReduceMotion
import androidx.compose.ui.res.stringResource
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import tools.senko.materialdrain.R

/** The screens of the app. [icon] is null for the one that isn't reached from the navigation, a file's details. */
enum class Screen(@StringRes val titleRes: Int, private val iconOf: (() -> ImageVector)?) {
    Upload(R.string.upload_upload, { Symbols.Upload }),
    Files(R.string.settings_files, { Symbols.Folder }),
    Filesystem(R.string.files_filesystem, { Symbols.HardDrive }),
    FileDetail(R.string.screen_file_details, null),
    Lists(R.string.screen_lists, { Symbols.Lists }),
    Settings(R.string.browser_settings, { Symbols.Settings });

    // Looked up when asked for, so the enum doesn't build its icons when it's first touched
    val icon: ImageVector? get() = iconOf?.invoke()
}

/** The screen's name in the app's language. */
val Screen.title: String
    @Composable get() = stringResource(titleRes)

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
    val title: String
        @Composable get() = config?.name?.takeIf { it.isNotBlank() } ?: screen.title

    /** Its own icon from the config (see [TabIcons]), or the screen's. */
    val icon: ImageVector get() = config?.icon?.let { TabIcons[it.trim().lowercase()] } ?: requireNotNull(screen.icon)
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
                        Icon(openInMore?.icon ?: Icons.Filled.Apps, contentDescription = stringResource(R.string.app_more_tabs))
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
                label = { Text(openInMore?.title ?: stringResource(R.string.app_more), maxLines = 1, overflow = TextOverflow.Ellipsis) },
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

/** How tall the [TabStrip] is, over the system's navigation bar: its dots, and room to touch them. */
private val StripHeight = 18.dp

/** A dot of the [TabStrip], and the one of the tab shown, as wide as [ShownDotWidth]. */
private val DotSize = 6.dp
private val ShownDotWidth = 20.dp

/**
 * How far apart the dots' middles are, the same for all of them, and so the room each has to be tapped in: the shown
 * one's wider dot fits in it, so it grows and shrinks from its middle without moving the others.
 */
private val DotPitch = 20.dp

/** How long a dot takes to fade to or from the accent colour. */
private const val DOT_FADE_MILLIS = 150

/** How far a drag along the [TabStrip] goes for each tab it moves by. */
private val StripStep = 32.dp

/**
 * The tabs with swipe navigation (Look and feel, Navigation), at the bottom of the screen: no bar, only a thin row with a
 * dot for each tab, the one shown wider and in the accent colour, which says where this tab is among them and how many
 * are on either side of it; the screen's title says which it is. The tabs are changed with a swipe sideways across the
 * page (see sidewaysSwipe), or along this row, a tab for every [StripStep] the finger goes, with a light vibration each;
 * a tapped dot goes to its tab too. It keeps clear of the system's navigation bar.
 */
@Composable
fun TabStrip(currentTab: Tab?, tabs: List<Tab>, onTabSelected: (Tab) -> Unit, modifier: Modifier = Modifier) {
    val currentIndex = tabs.indexOfFirst { it.isCurrent(currentTab) }
    val haptics = LocalAppHaptics.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val latestTabs by rememberUpdatedState(tabs)
    val latestIndex by rememberUpdatedState(currentIndex)
    val select by rememberUpdatedState(onTabSelected)
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .height(StripHeight)
            .pointerInput(rtl) {
                val step = StripStep.toPx()
                var pulled = 0f
                var from = 0
                var at = 0
                detectHorizontalDragGestures(
                    onDragStart = {
                        pulled = 0f
                        from = latestIndex.coerceAtLeast(0)
                        at = from
                    },
                    onHorizontalDrag = { change, amount ->
                        change.consume()
                        // Along the row: to the right is to the tabs after this one (to the left, right to left)
                        pulled += if (rtl) -amount else amount
                        val target = (from + (pulled / step).toInt()).coerceIn(0, latestTabs.lastIndex)
                        if (target != at) {
                            at = target
                            haptics.perform(HapticKind.THRESHOLD, HapticPlace.NAV_SWIPE)
                            select(latestTabs[target])
                        }
                    }
                )
            }
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            tabs.forEachIndexed { index, tab ->
                StripDot(tab, selected = index == currentIndex, onClick = { select(tab) })
            }
        }
    }
}

/**
 * One tab of the [TabStrip]: a dot, wider and in the accent colour when it's the one shown ([selected]). Its place is as
 * wide whichever it is ([DotPitch]), so the dot widens and narrows from its middle, whichever way the tabs went, and
 * fades between the colours.
 */
@Composable
private fun StripDot(tab: Tab, selected: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val reduceMotion = LocalReduceMotion.current
    val width by animateDpAsState(
        if (selected) ShownDotWidth else DotSize,
        if (reduceMotion) snap() else spring(),
        label = "stripDotWidth"
    )
    val color by animateColorAsState(
        if (selected) colors.primary else colors.onSurfaceVariant.copy(alpha = 0.35f),
        if (reduceMotion) snap() else tween(DOT_FADE_MILLIS),
        label = "stripDotColor"
    )
    val title = tab.title
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(width = DotPitch, height = StripHeight)
            .clickable(
                interactionSource = null,
                indication = null,
                role = Role.Tab,
                onClickLabel = title,
                onClick = onClick
            )
            .semantics { this.selected = selected; contentDescription = title }
    ) {
        Box(
            modifier = Modifier
                .size(width = width, height = DotSize)
                .clip(CircleShape)
                .background(color)
        )
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
        val tabs = listOf(Screen.Files, Screen.Lists, Screen.Filesystem).map { Tab(it) }
        Column {
            BottomNavigationBar(currentTab = tabs[0], tabs = tabs, onTabSelected = {})
            BottomNavigationBar(currentTab = tabs[0], tabs = tabs, onTabSelected = {}, alwaysShowLabels = false)
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

/** The swipe navigation's strip with the tabs of the built-in Pixeldrain, Files shown; and with a big host's seven. */
@ThemePreviews
@Composable
private fun TabStripPreview() {
    PreviewTheme {
        val tabs = listOf(Screen.Files, Screen.Lists, Screen.Filesystem).map { Tab(it) }
        Column {
            TabStrip(currentTab = tabs[0], tabs = tabs, onTabSelected = {})
            TabStrip(currentTab = ManyTabs[2], tabs = ManyTabs, onTabSelected = {})
        }
    }
}

/** Made-up tabs of a big host: Filesystem tabs with their own names and icons. */
private val ManyTabs = listOf(
    Tab(Screen.Upload),
    Tab(Screen.Filesystem, ScreenConfig(HostScreen.FILESYSTEM, id = "photos", name = "Photos", icon = "photo")),
    Tab(Screen.Filesystem, ScreenConfig(HostScreen.FILESYSTEM, id = "music", name = "Music", icon = "music")),
    Tab(Screen.Filesystem, ScreenConfig(HostScreen.FILESYSTEM, id = "videos", name = "Videos", icon = "video")),
    Tab(Screen.Filesystem, ScreenConfig(HostScreen.FILESYSTEM, id = "backups", name = "Backups", icon = "backup")),
    Tab(Screen.Filesystem, ScreenConfig(HostScreen.FILESYSTEM, id = "docs", name = "Documents", icon = "document")),
    Tab(Screen.Filesystem, ScreenConfig(HostScreen.FILESYSTEM, id = "shared", name = "Shared", icon = "shared"))
)
