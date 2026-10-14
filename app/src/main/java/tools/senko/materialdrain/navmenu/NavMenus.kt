package tools.senko.materialdrain.navmenu

import androidx.annotation.DrawableRes
import androidx.compose.material.icons.Icons
import tools.senko.materialdrain.ui.icons.filled.Apps
import tools.senko.materialdrain.ui.icons.filled.BarChart
import tools.senko.materialdrain.ui.icons.filled.Build
import tools.senko.materialdrain.ui.icons.filled.Cloud
import tools.senko.materialdrain.ui.icons.filled.CloudSync
import tools.senko.materialdrain.ui.icons.filled.Dashboard
import tools.senko.materialdrain.ui.icons.filled.Description
import tools.senko.materialdrain.ui.icons.filled.DeviceHub
import tools.senko.materialdrain.ui.icons.filled.Dns
import tools.senko.materialdrain.ui.icons.filled.Explore
import tools.senko.materialdrain.ui.icons.filled.Folder
import tools.senko.materialdrain.ui.icons.filled.FolderShared
import tools.senko.materialdrain.ui.icons.filled.Group
import tools.senko.materialdrain.ui.icons.filled.Key
import tools.senko.materialdrain.ui.icons.filled.Notifications
import tools.senko.materialdrain.ui.icons.filled.Person
import tools.senko.materialdrain.ui.icons.filled.PhotoCamera
import tools.senko.materialdrain.ui.icons.filled.PowerSettingsNew
import tools.senko.materialdrain.ui.icons.filled.Router
import tools.senko.materialdrain.ui.icons.filled.Schedule
import tools.senko.materialdrain.ui.icons.filled.SdStorage
import tools.senko.materialdrain.ui.icons.filled.SettingsEthernet
import tools.senko.materialdrain.ui.icons.filled.Share
import tools.senko.materialdrain.ui.icons.filled.Storage
import tools.senko.materialdrain.ui.icons.filled.Sync
import tools.senko.materialdrain.ui.icons.filled.SyncAlt
import tools.senko.materialdrain.ui.icons.filled.SystemUpdate
import tools.senko.materialdrain.ui.icons.filled.Terminal
import tools.senko.materialdrain.ui.icons.filled.Tune
import tools.senko.materialdrain.ui.icons.filled.VerifiedUser
import tools.senko.materialdrain.ui.icons.filled.VpnKey
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.painterResource
import tools.senko.materialdrain.Tab

enum class NavFabPosition(val bias: Float) { START(-1f), CENTER(0f), END(1f) }

/** Which menu the FAB navigation prototype shows (Developer settings): the active host's tabs, or a mock of a big one. */
enum class NavMenuPreview(val label: String) { PIXELDRAIN("This host"), TRUENAS_MOCK("TrueNAS (mock)") }

sealed interface NavIcon {
    data class Res(@param:DrawableRes val id: Int) : NavIcon
    data class Vector(val image: ImageVector) : NavIcon
}

@Composable
fun NavIcon.painter(): Painter = when (this) {
    is NavIcon.Res -> painterResource(id)
    is NavIcon.Vector -> rememberVectorPainter(image)
}

/** [tab] is null for mock destinations, which only exist to preview how a bigger menu looks. */
data class NavMenuItem(val id: String, val label: String, val icon: NavIcon, val tab: Tab? = null)

data class NavMenuSection(val title: String, val items: List<NavMenuItem>)

/**
 * [sections] are in top-to-bottom reading order. The window is bottom-anchored, so the last section sits
 * right above [pinned], the row closest to the FAB (and the thumb), and earlier sections are a scroll away.
 */
data class NavMenu(val pinned: List<NavMenuItem>, val sections: List<NavMenuSection> = emptyList())

private fun Tab.menuItem() = NavMenuItem(id = "${screen.name}:$id", label = title, icon = NavIcon.Res(requireNotNull(screen.iconResId)), tab = this)

/**
 * The tabs of the active host, as its bottom bar has them. Settings isn't a tile: it's the gear at the top right of the
 * screen, out of the thumb zone because it's rarely used.
 */
private fun tabsNavMenu(tabs: List<Tab>) = NavMenu(pinned = tabs.map { it.menuItem() })

private fun mock(label: String, icon: ImageVector) = NavMenuItem(id = "truenas:$label", label = label, icon = NavIcon.Vector(icon))

/** Roughly TrueNAS SCALE's sidebar, only to see how the window copes with a provider that has dozens of screens. */
val TrueNasMockNavMenu = NavMenu(
    pinned = listOf(
        mock("Dashboard", Icons.Filled.Dashboard),
        mock("Datasets", Icons.Filled.Folder),
        mock("SMB", Icons.Filled.FolderShared),
        mock("Alerts", Icons.Filled.Notifications)
    ),
    sections = listOf(
        NavMenuSection(
            "System", listOf(
                mock("General", Icons.Filled.Tune),
                mock("Services", Icons.Filled.DeviceHub),
                mock("Updates", Icons.Filled.SystemUpdate),
                mock("Shell", Icons.Filled.Terminal),
                mock("Boot", Icons.Filled.PowerSettingsNew),
                mock("Audit", Icons.Filled.Description)
            )
        ),
        NavMenuSection(
            "Credentials", listOf(
                mock("Users", Icons.Filled.Person),
                mock("Groups", Icons.Filled.Group),
                mock("Certificates", Icons.Filled.VerifiedUser),
                mock("SSH Keys", Icons.Filled.Key)
            )
        ),
        NavMenuSection(
            "Network", listOf(
                mock("Interfaces", Icons.Filled.SettingsEthernet),
                mock("Routes", Icons.Filled.Router),
                mock("VPN", Icons.Filled.VpnKey)
            )
        ),
        NavMenuSection(
            "Apps", listOf(
                mock("Installed", Icons.Filled.Apps),
                mock("Discover", Icons.Filled.Explore),
                mock("Reporting", Icons.Filled.BarChart)
            )
        ),
        NavMenuSection(
            "Data Protection", listOf(
                mock("Snapshot Tasks", Icons.Filled.Schedule),
                mock("Replication", Icons.Filled.Sync),
                mock("Cloud Sync", Icons.Filled.CloudSync),
                mock("Rsync", Icons.Filled.SyncAlt),
                mock("Scrub", Icons.Filled.Build)
            )
        ),
        NavMenuSection(
            "Sharing", listOf(
                mock("SMB", Icons.Filled.FolderShared),
                mock("NFS", Icons.Filled.Share),
                mock("iSCSI", Icons.Filled.Dns),
                mock("WebDAV", Icons.Filled.Cloud)
            )
        ),
        NavMenuSection(
            "Storage", listOf(
                mock("Pools", Icons.Filled.Storage),
                mock("Datasets", Icons.Filled.Folder),
                mock("Disks", Icons.Filled.SdStorage),
                mock("Snapshots", Icons.Filled.PhotoCamera)
            )
        )
    )
)

fun NavMenuPreview.menu(tabs: List<Tab>): NavMenu = when (this) {
    NavMenuPreview.PIXELDRAIN -> tabsNavMenu(tabs)
    NavMenuPreview.TRUENAS_MOCK -> TrueNasMockNavMenu
}
