package tools.senko.materialdrain.navmenu

import androidx.compose.material.icons.Icons
import androidx.compose.ui.graphics.vector.ImageVector
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

// The made-up menus of the navigation button (Developer settings, "Navigation button's menu"): only to see how its
// window copes with a host that has dozens of screens. Their items open nothing.

private fun mock(label: String, icon: ImageVector) = NavMenuItem(id = "truenas:$label", label = label, icon = icon)

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
