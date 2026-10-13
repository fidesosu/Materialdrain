package tools.senko.materialdrain.preferences

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DataSaverOn
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import tools.senko.materialdrain.navmenu.NavMenuPreview
import tools.senko.materialdrain.settings.SEARCH_INDEX_FILE_NAME
import tools.senko.materialdrain.settings.SEARCH_RESULT_LIMITS
import tools.senko.materialdrain.transfer.CHANNEL_TRANSFER_COMPLETE
import tools.senko.materialdrain.transfer.CHANNEL_TRANSFER_FAILED
import tools.senko.materialdrain.transfer.CHANNEL_TRANSFER_PROGRESS

/**
 * Everything the settings screen shows, in the order it's shown. It is the one place to edit: to add a setting, add an
 * item to the `items` of a category (or to the `children` of the setting it belongs to); to add a category, add a
 * [SettingsCategory] to this list. Nothing else needs to change.
 *
 * The settings go no deeper than one page: each category is a tile on the settings page which opens a page with all of
 * its settings, a setting's own settings indented under it rather than on a page further in.
 */
val SettingsCatalog: List<SettingsCategory> = listOf(

    SettingsCategory(
        id = ACCOUNT_CATEGORY_ID,
        title = "Account",
        summary = "Your pixeldrain account, usage and billing",
        icon = Icons.Filled.AccountCircle,
        tone = CategoryTone.PRIMARY,
        // The account itself at the top of the settings, see AccountTile
        tile = { onOpen -> AccountTile(onOpen) },
        // One page of its own, see AccountPage; the API key has its own Save button there
        items = listOf(SettingsItem.Custom { AccountPage() })
    ),

    SettingsCategory(
        id = HOSTS_CATEGORY_ID,
        title = "Hosts",
        summary = "Custom hosts and their configs",
        icon = Icons.Filled.Dns,
        tone = CategoryTone.TERTIARY,
        status = {
            val count = providerSettingsViewModel.uiState.collectAsState().value.providers.size
            if (count == 0) null else if (count == 1) "1 host" else "$count hosts"
        },
        items = listOf(SettingsItem.Custom { ProviderHostsSection() })
    ),

    SettingsCategory(
        id = "data_saver",
        title = "Data saver",
        summary = "Load things only when you ask",
        icon = Icons.Filled.DataSaverOn,
        tone = CategoryTone.PRIMARY,
        status = {
            when {
                !appSettings.dataSaver.collectAsState().value -> null
                appSettings.dataSaverMobileOnly.collectAsState().value -> "On mobile data"
                else -> "On"
            }
        },
        items = listOf(
            SettingsItem.Toggle(
                title = "Data saver",
                summary = "What loads by itself waits to be asked for",
                info = "Nothing is turned off, it only waits until you ask for it: a thumbnail loads when its tile is " +
                    "tapped (or all of them with Thumbnails above the list), a preview with its Load preview button, the " +
                    "configs with Check all for updates on the Hosts page, and the hosts with the refresh in the host " +
                    "switcher. Choose below what waits.",
                isChecked = { appSettings.dataSaver.collectAsState().value },
                onCheckedChange = { appSettings.setDataSaver(it) },
                children = listOf(
                    SettingsItem.Choice(
                        title = "Applies",
                        info = "On mobile data, the data saver only applies while the network is metered: mobile data, " +
                            "and Wi-Fi which Android was told is metered (such as a phone's hotspot). On other Wi-Fi " +
                            "everything loads as usual.",
                        options = listOf("Always" to false, "On mobile data" to true),
                        selected = { appSettings.dataSaverMobileOnly.collectAsState().value },
                        onSelect = { appSettings.setDataSaverMobileOnly(it) }
                    ),
                    SettingsItem.Toggle(
                        title = "Thumbnails",
                        summary = "Tap a tile, or Thumbnails above the list",
                        isChecked = { appSettings.holdThumbnails.collectAsState().value },
                        onCheckedChange = { appSettings.setHoldThumbnails(it) }
                    ),
                    SettingsItem.Toggle(
                        title = "Previews",
                        summary = "Load preview in a file's details",
                        info = "Pictures, videos, songs, text and the contents of archives. A video or song still only " +
                            "streams once it's played.",
                        isChecked = { appSettings.holdPreviews.collectAsState().value },
                        onCheckedChange = { appSettings.setHoldPreviews(it) }
                    ),
                    SettingsItem.Toggle(
                        title = "Config updates",
                        summary = "Check all for updates, on the Hosts page",
                        info = "Configs with automatic updates are otherwise checked once a day, when the app starts.",
                        isChecked = { appSettings.holdConfigUpdates.collectAsState().value },
                        onCheckedChange = { appSettings.setHoldConfigUpdates(it) }
                    ),
                    SettingsItem.Toggle(
                        title = "Host checks",
                        summary = "The refresh in the host switcher",
                        info = "The host switcher otherwise checks every few minutes whether each host answers, with one " +
                            "small request per host.",
                        isChecked = { appSettings.holdHostChecks.collectAsState().value },
                        onCheckedChange = { appSettings.setHoldHostChecks(it) }
                    )
                )
            )
        )
    ),

    SettingsCategory(
        id = "look",
        title = "Look and feel",
        summary = "Previews and animations",
        icon = Icons.Filled.Palette,
        tone = CategoryTone.SECONDARY,
        items = listOf(
            SettingsItem.Toggle(
                title = "Blurred backdrop",
                summary = "The thumbnail, blurred, behind previews",
                info = "The file's thumbnail, blurred, fills the space around a fullscreen image or video, and is there " +
                    "while a preview is loading. Off, the background is black.",
                isChecked = { appSettings.blurredBackdrop.collectAsState().value },
                onCheckedChange = { appSettings.setBlurredBackdrop(it) }
            ),
            SettingsItem.Toggle(
                title = "Wrap long lines",
                summary = "In text previews",
                info = "Text previews (code, logs, configs) break long lines onto the next line. Off, the lines stay as " +
                    "they are and can be scrolled sideways.",
                isChecked = { appSettings.textWrap.collectAsState().value },
                onCheckedChange = { appSettings.setTextWrap(it) }
            ),
            SettingsItem.Toggle(
                title = "Reduce animations",
                summary = "Screens only fade",
                info = "Leaves out sliding, scaling, spinning and rolling text, and scrolls without animation. Animations " +
                    "are also reduced when they are turned off in the Android settings.",
                isChecked = { appSettings.reduceAnimations.collectAsState().value },
                onCheckedChange = { appSettings.setReduceAnimations(it) }
            )
        )
    ),

    SettingsCategory(
        id = "files",
        title = "Files",
        summary = "Your filesystem, uploads and search",
        icon = Icons.Filled.Folder,
        tone = CategoryTone.PRIMARY,
        items = listOf(
            SettingsItem.Toggle(
                title = "Hide $SEARCH_INDEX_FILE_NAME",
                summary = "Pixeldrain's own index of your files",
                info = "Pixeldrain uses this file itself to store the paths of your files and re-creates it when it is " +
                    "removed. Hiding it keeps you from deleting it by accident.",
                isChecked = { appSettings.hideSearchIndex.collectAsState().value },
                onCheckedChange = { appSettings.setHideSearchIndex(it) }
            ),
            SettingsItem.Toggle(
                title = "Upload by last change",
                summary = "Oldest first, one file at a time",
                info = "Several files are uploaded oldest first, by when they were last changed on this device, and one " +
                    "after another, so they arrive at the host (and get their upload dates) in that order. Slower for " +
                    "many small files, which otherwise go a few at once.",
                isChecked = { appSettings.uploadInModifiedOrder.collectAsState().value },
                onCheckedChange = { appSettings.setUploadInModifiedOrder(it) }
            ),
            SettingsItem.Choice(
                title = "Most search results",
                summary = "When searching the filesystem",
                info = "Searching the filesystem looks through the open folder and every folder inside it, so a big tree " +
                    "can match thousands of files. All of them are shown unless you set a limit, which can help on " +
                    "slower devices.",
                options = SEARCH_RESULT_LIMITS.map { (if (it == 0) "No limit" else "$it") to it },
                selected = { appSettings.searchResultLimit.collectAsState().value },
                onSelect = { appSettings.setSearchResultLimit(it) }
            )
        )
    ),

    SettingsCategory(
        id = "notifications",
        title = "Notifications",
        summary = "Progress of uploads and downloads",
        icon = Icons.Filled.Notifications,
        tone = CategoryTone.TERTIARY,
        items = listOf(
            SettingsItem.Action(
                title = "Notification settings",
                summary = "All notifications of this app",
                info = "Uploads and downloads keep running while the app is in the background and show their progress in " +
                    "a notification. Each kind of notification has its own settings in Android, where you can change " +
                    "its importance, sound, vibration and lock screen visibility.",
                external = true,
                onClick = { context -> openNotificationSettings(context, null) },
                children = listOf(
                    SettingsItem.Action(
                        title = "Transfer progress",
                        summary = "While uploading or downloading",
                        external = true,
                        onClick = { context -> openNotificationSettings(context, CHANNEL_TRANSFER_PROGRESS) }
                    ),
                    SettingsItem.Action(
                        title = "Completed transfers",
                        summary = "When one finished in the background",
                        external = true,
                        onClick = { context -> openNotificationSettings(context, CHANNEL_TRANSFER_COMPLETE) }
                    ),
                    SettingsItem.Action(
                        title = "Failed transfers",
                        summary = "When one failed in the background",
                        external = true,
                        onClick = { context -> openNotificationSettings(context, CHANNEL_TRANSFER_FAILED) }
                    )
                )
            )
        )
    ),

    SettingsCategory(
        id = "security",
        title = "Privacy and security",
        summary = "Locking the app",
        icon = Icons.Filled.Lock,
        tone = CategoryTone.SECONDARY,
        items = listOf(
            SettingsItem.Toggle(
                title = "Lock with fingerprint or face",
                summary = "When the app is opened again",
                info = "Asks for your fingerprint, face or screen lock each time the app is opened again, after it was in " +
                    "the background for a while. Needs a fingerprint, face or screen lock set up on this device.",
                isChecked = { appSettings.biometricLock.collectAsState().value },
                onCheckedChange = { if (appSettings.biometricLockAvailable()) appSettings.setBiometricLock(it) }
            )
        )
    ),

    SettingsCategory(
        id = "developer",
        title = "Developer",
        summary = "Prototypes and previews",
        icon = Icons.Filled.Code,
        tone = CategoryTone.NEUTRAL,
        items = listOf(
            SettingsItem.Toggle(
                title = "FAB navigation prototype",
                summary = "A navigation button instead of the bar",
                info = "Replaces the bottom bar with a navigation button. Swipe the button sideways to move it left, " +
                    "center or right. While this is on, the Upload and Save Settings buttons are hidden.",
                isChecked = { appSettings.navPrototype.collectAsState().value },
                onCheckedChange = { appSettings.setNavPrototype(it) },
                children = listOf(
                    SettingsItem.Choice(
                        title = "Menu of",
                        info = "Mock providers only show how their menu looks, their items don't open anything.",
                        options = NavMenuPreview.entries.map { it.label to it },
                        selected = { appSettings.navMenuPreview.collectAsState().value },
                        onSelect = { appSettings.setNavMenuPreview(it) }
                    )
                )
            )
        )
    ),

    SettingsCategory(
        id = "about",
        title = "About",
        summary = "Version and links",
        icon = Icons.Filled.Info,
        tone = CategoryTone.PRIMARY,
        status = {
            val context = LocalContext.current
            remember { readAbout(context).versionName }
        },
        items = listOf(
            SettingsItem.Custom { AboutSection() },
            SettingsItem.Header("Links"),
            SettingsItem.Action(
                title = "Website",
                summary = "Downloads of every version",
                icon = Icons.Filled.Language,
                external = true,
                onClick = { context -> openLink(context, "https://materialdrain.senko.tools") }
            ),
            SettingsItem.Action(
                title = "Documentation",
                summary = "How the app works, and adding hosts",
                icon = Icons.AutoMirrored.Filled.MenuBook,
                external = true,
                onClick = { context -> openLink(context, "https://materialdrain.senko.tools/docs/") }
            ),
            SettingsItem.Action(
                title = "Changes",
                summary = "What each release changed",
                icon = Icons.Filled.NewReleases,
                external = true,
                onClick = { context -> openLink(context, "https://github.com/fidesosu/Materialdrain/releases") }
            ),
            SettingsItem.Action(
                title = "Source code",
                summary = "On GitHub",
                icon = Icons.Filled.Code,
                external = true,
                onClick = { context -> openLink(context, "https://github.com/fidesosu/Materialdrain") }
            ),
            SettingsItem.Action(
                title = "Report a problem",
                summary = "Opens a new issue on GitHub",
                icon = Icons.Filled.BugReport,
                external = true,
                onClick = { context -> openLink(context, "https://github.com/fidesosu/Materialdrain/issues/new") }
            )
        )
    )
)

/** Opens [url] in the browser; nothing happens when the device has none. */
internal fun openLink(context: Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) {
        // No browser on this device
    }
}

const val ACCOUNT_CATEGORY_ID = "account"

/** The category with the custom hosts, which the host switcher opens (see ProviderHostsSection). */
const val HOSTS_CATEGORY_ID = "advanced"

fun settingsCategory(id: String?): SettingsCategory? = SettingsCatalog.firstOrNull { it.id == id }

/** Opens the Android notification settings of the app, or of a single notification channel. */
private fun openNotificationSettings(context: Context, channelId: String?) {
    val intent = if (channelId == null) {
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
    } else {
        Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_CHANNEL_ID, channelId)
    }
    intent.putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        // No settings screen available on this device
    }
}
