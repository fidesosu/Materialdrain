package tools.senko.materialdrain.navmenu

import androidx.annotation.DrawableRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DeviceHub
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderShared
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SdStorage
import androidx.compose.material.icons.filled.SettingsEthernet
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.SyncAlt
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.painterResource
import tools.senko.materialdrain.Screen

enum class NavFabPosition(val bias: Float) { START(-1f), CENTER(0f), END(1f) }

/** Which provider's menu the FAB navigation prototype shows (Developer settings). */
enum class NavMenuPreview(val label: String) { PIXELDRAIN("Pixeldrain"), TRUENAS_MOCK("TrueNAS (mock)") }

sealed interface NavIcon {
    data class Res(@param:DrawableRes val id: Int) : NavIcon
    data class Vector(val image: ImageVector) : NavIcon
}

@Composable
fun NavIcon.painter(): Painter = when (this) {
    is NavIcon.Res -> painterResource(id)
    is NavIcon.Vector -> rememberVectorPainter(image)
}

/** [screen] is null for mock destinations, which only exist to preview how a bigger menu looks. */
data class NavMenuItem(val id: String, val label: String, val icon: NavIcon, val screen: Screen? = null)

data class NavMenuSection(val title: String, val items: List<NavMenuItem>)

/**
 * [sections] are in top-to-bottom reading order. The window is bottom-anchored, so the last section sits
 * right above [pinned], the row closest to the FAB (and the thumb), and earlier sections are a scroll away.
 */
data class NavMenu(val pinned: List<NavMenuItem>, val sections: List<NavMenuSection> = emptyList())

private fun Screen.menuItem() = NavMenuItem(id = name, label = title, icon = NavIcon.Res(requireNotNull(iconResId)), screen = this)

/** Settings isn't a tile: it's the gear at the top right of the screen, out of the thumb zone because it's rarely used. */
val PixeldrainNavMenu = NavMenu(
    pinned = listOf(Screen.Upload, Screen.Files, Screen.Lists, Screen.Filesystem).map { it.menuItem() }
)

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

fun NavMenuPreview.menu(): NavMenu = when (this) {
    NavMenuPreview.PIXELDRAIN -> PixeldrainNavMenu
    NavMenuPreview.TRUENAS_MOCK -> TrueNasMockNavMenu
}
