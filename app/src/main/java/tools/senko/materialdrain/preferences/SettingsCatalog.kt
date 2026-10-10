package tools.senko.materialdrain.preferences

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Upload
import androidx.compose.runtime.collectAsState
import tools.senko.materialdrain.settings.SEARCH_INDEX_FILE_NAME
import tools.senko.materialdrain.transfer.CHANNEL_TRANSFER_COMPLETE
import tools.senko.materialdrain.transfer.CHANNEL_TRANSFER_FAILED
import tools.senko.materialdrain.transfer.CHANNEL_TRANSFER_PROGRESS

/**
 * Everything the settings screen shows. It is the one place to edit: to add a setting, add an item to the
 * `items` of a category; to add a category, add a [SettingsCategory] to this list (in the order in which they
 * should appear). Nothing else needs to change.
 */
val SettingsCatalog: List<SettingsCategory> = listOf(

    SettingsCategory(
        id = ACCOUNT_CATEGORY_ID,
        title = "Account",
        summary = "Login and API key",
        icon = Icons.Filled.AccountCircle,
        group = SettingsGroup.ACCOUNT,
        hasSaveButton = true, // the API key is saved with the button
        items = listOf(
            SettingsItem.Header("Account login"),
            SettingsItem.Custom { LoginSection() },
            SettingsItem.Header("API key"),
            SettingsItem.Custom { ApiKeySection() }
        )
    ),

    SettingsCategory(
        id = "filesystem",
        title = "Filesystem",
        summary = "What is shown in your filesystem",
        icon = Icons.Filled.Folder,
        group = SettingsGroup.FILES,
        items = listOf(
            SettingsItem.Toggle(
                title = "Hide $SEARCH_INDEX_FILE_NAME",
                summary = "Pixeldrain's own index of your files",
                isChecked = { appSettings.hideSearchIndex.collectAsState().value },
                onCheckedChange = { appSettings.setHideSearchIndex(it) }
            ),
            SettingsItem.Note(
                "Pixeldrain uses this file itself to store the paths of your files and re-creates it when it is removed. " +
                    "Hiding it keeps you from deleting it by accident."
            )
        )
    ),

    SettingsCategory(
        id = "uploads",
        title = "Uploads",
        summary = "The order of several files",
        icon = Icons.Filled.Upload,
        group = SettingsGroup.FILES,
        items = listOf(
            SettingsItem.Toggle(
                title = "Upload by last change",
                summary = "Oldest first, one file at a time",
                isChecked = { appSettings.uploadInModifiedOrder.collectAsState().value },
                onCheckedChange = { appSettings.setUploadInModifiedOrder(it) }
            ),
            SettingsItem.Note(
                "Several files are uploaded oldest first, by when they were last changed on this device, and one after " +
                    "another, so they arrive at the host (and get their upload dates) in that order. Slower for many " +
                    "small files, which otherwise go a few at once."
            )
        )
    ),

    SettingsCategory(
        id = "search",
        title = "Search",
        summary = "How many results are shown",
        icon = Icons.Filled.Search,
        group = SettingsGroup.FILES,
        items = listOf(
            SettingsItem.Note(
                "Searching the filesystem looks through the open folder and every folder inside it, so a big tree can " +
                    "match thousands of files. All of them are shown unless you set a limit here, which can help on " +
                    "slower devices."
            ),
            SettingsItem.Header("Most results shown"),
            SettingsItem.Custom { SearchResultLimitSection() }
        )
    ),

    SettingsCategory(
        id = "notifications",
        title = "Notifications",
        summary = "Progress of uploads and downloads",
        icon = Icons.Filled.Notifications,
        group = SettingsGroup.APP,
        items = listOf(
            SettingsItem.Note(
                "Uploads and downloads keep running while the app is in the background and show their progress in a " +
                    "notification. Each kind of notification has its own settings in Android, where you can change its " +
                    "importance, sound, vibration and lock screen visibility."
            ),
            SettingsItem.Action(
                title = "Notification settings",
                summary = "All notifications of this app",
                onClick = { context -> openNotificationSettings(context, null) }
            ),
            SettingsItem.Header("Kinds of notifications"),
            SettingsItem.Action(
                title = "Transfer progress",
                summary = "While uploading or downloading",
                onClick = { context -> openNotificationSettings(context, CHANNEL_TRANSFER_PROGRESS) }
            ),
            SettingsItem.Action(
                title = "Completed transfers",
                summary = "When one finished in the background",
                onClick = { context -> openNotificationSettings(context, CHANNEL_TRANSFER_COMPLETE) }
            ),
            SettingsItem.Action(
                title = "Failed transfers",
                summary = "When one failed in the background",
                onClick = { context -> openNotificationSettings(context, CHANNEL_TRANSFER_FAILED) }
            )
        )
    ),

    SettingsCategory(
        id = "previews",
        title = "Previews",
        summary = "Images and videos",
        icon = Icons.Filled.Image,
        group = SettingsGroup.VIEWING,
        items = listOf(
            SettingsItem.Toggle(
                title = "Blurred backdrop",
                summary = "The thumbnail, blurred, behind previews",
                isChecked = { appSettings.blurredBackdrop.collectAsState().value },
                onCheckedChange = { appSettings.setBlurredBackdrop(it) }
            ),
            SettingsItem.Note(
                "The file's thumbnail, blurred, fills the space around a fullscreen image or video, and is there while a " +
                    "preview is loading. Off, the background is black."
            ),
            SettingsItem.Toggle(
                title = "Wrap long lines",
                summary = "In text previews",
                isChecked = { appSettings.textWrap.collectAsState().value },
                onCheckedChange = { appSettings.setTextWrap(it) }
            ),
            SettingsItem.Note(
                "Text previews (code, logs, configs) break long lines onto the next line. Off, the lines stay as they are " +
                    "and can be scrolled sideways."
            )
        )
    ),

    SettingsCategory(
        id = "accessibility",
        title = "Accessibility",
        summary = "Animations",
        icon = Icons.Filled.Accessibility,
        group = SettingsGroup.VIEWING,
        items = listOf(
            SettingsItem.Toggle(
                title = "Reduce animations",
                summary = "Screens only fade",
                isChecked = { appSettings.reduceAnimations.collectAsState().value },
                onCheckedChange = { appSettings.setReduceAnimations(it) }
            ),
            SettingsItem.Note(
                "Leaves out sliding, scaling, spinning and rolling text, and scrolls without animation. Animations are " +
                    "also reduced when they are turned off in the Android settings."
            )
        )
    ),

    SettingsCategory(
        id = "security",
        title = "Security",
        summary = "Locking the app",
        icon = Icons.Filled.Lock,
        group = SettingsGroup.APP,
        items = listOf(
            SettingsItem.Toggle(
                title = "Lock with fingerprint or face",
                summary = "When the app is opened again",
                isChecked = { appSettings.biometricLock.collectAsState().value },
                onCheckedChange = { if (appSettings.biometricLockAvailable()) appSettings.setBiometricLock(it) }
            ),
            SettingsItem.Note(
                "Asks for your fingerprint, face or screen lock each time the app is opened again, after it was in the " +
                    "background for a while. Needs a fingerprint, face or screen lock set up on this device."
            )
        )
    ),

    SettingsCategory(
        id = HOSTS_CATEGORY_ID,
        title = "Hosts",
        summary = "Custom hosts and their configs",
        icon = Icons.Filled.Dns,
        group = SettingsGroup.ACCOUNT,
        items = listOf(
            SettingsItem.Header("Custom hosts"),
            SettingsItem.Custom { ProviderHostsSection() }
        )
    ),

    SettingsCategory(
        id = "developer",
        title = "Developer",
        summary = "Prototypes and previews",
        icon = Icons.Filled.Code,
        group = SettingsGroup.OTHER,
        items = listOf(
            SettingsItem.Toggle(
                title = "FAB navigation prototype",
                summary = "A navigation button instead of the bar",
                isChecked = { appSettings.navPrototype.collectAsState().value },
                onCheckedChange = { appSettings.setNavPrototype(it) }
            ),
            SettingsItem.Note(
                "Replaces the bottom bar with a navigation button. Swipe the button sideways to move it left, center or " +
                    "right. While this is on, the Upload and Save Settings buttons are hidden."
            ),
            SettingsItem.Header("Preview navigation as"),
            SettingsItem.Custom { NavMenuPreviewSection() },
            SettingsItem.Note("Mock providers only show how their menu looks, their items don't open anything.")
        )
    ),

    SettingsCategory(
        id = "about",
        title = "About",
        summary = "Version and links",
        icon = Icons.Filled.Info,
        group = SettingsGroup.OTHER,
        items = listOf(
            SettingsItem.Custom { AboutSection() },
            SettingsItem.Header("Links"),
            SettingsItem.Action(
                title = "Website",
                summary = "Downloads of every version",
                icon = Icons.Filled.Language,
                onClick = { context -> openLink(context, "https://materialdrain.senko.tools") }
            ),
            SettingsItem.Action(
                title = "Documentation",
                summary = "How the app works, and adding hosts",
                icon = Icons.AutoMirrored.Filled.MenuBook,
                onClick = { context -> openLink(context, "https://materialdrain.senko.tools/docs/") }
            ),
            SettingsItem.Action(
                title = "Changes",
                summary = "What each release changed",
                icon = Icons.Filled.NewReleases,
                onClick = { context -> openLink(context, "https://github.com/fidesosu/Materialdrain/releases") }
            ),
            SettingsItem.Action(
                title = "Source code",
                summary = "On GitHub",
                icon = Icons.Filled.Code,
                onClick = { context -> openLink(context, "https://github.com/fidesosu/Materialdrain") }
            ),
            SettingsItem.Action(
                title = "Report a problem",
                summary = "Opens a new issue on GitHub",
                icon = Icons.Filled.BugReport,
                onClick = { context -> openLink(context, "https://github.com/fidesosu/Materialdrain/issues/new") }
            )
        )
    )
)

/** Opens [url] in the browser; nothing happens when the device has none. */
private fun openLink(context: Context, url: String) {
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
