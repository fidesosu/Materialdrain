package tools.senko.materialdrain.preferences

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
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
        items = listOf(
            SettingsItem.Toggle(
                title = "Hide $SEARCH_INDEX_FILE_NAME",
                summary = "Pixeldrain uses this file itself to store the paths of your files and re-creates it when it is " +
                    "removed. Hiding it keeps you from deleting it by accident.",
                isChecked = { appSettings.hideSearchIndex.collectAsState().value },
                onCheckedChange = { appSettings.setHideSearchIndex(it) }
            )
        )
    ),

    SettingsCategory(
        id = "uploads",
        title = "Uploads",
        summary = "The order several files are uploaded in",
        icon = Icons.Filled.Upload,
        items = listOf(
            SettingsItem.Toggle(
                title = "Upload in the order the files were changed",
                summary = "Several files are uploaded oldest first, by when they were last changed on this device, and " +
                    "one after another, so they arrive at the host (and get their upload dates) in that order. Slower " +
                    "for many small files, which otherwise go a few at once.",
                isChecked = { appSettings.uploadInModifiedOrder.collectAsState().value },
                onCheckedChange = { appSettings.setUploadInModifiedOrder(it) }
            )
        )
    ),

    SettingsCategory(
        id = "search",
        title = "Search",
        summary = "How many results are shown",
        icon = Icons.Filled.Search,
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
                summary = "Shown while something is uploading or downloading",
                onClick = { context -> openNotificationSettings(context, CHANNEL_TRANSFER_PROGRESS) }
            ),
            SettingsItem.Action(
                title = "Completed transfers",
                summary = "Shown when a transfer finished in the background",
                onClick = { context -> openNotificationSettings(context, CHANNEL_TRANSFER_COMPLETE) }
            ),
            SettingsItem.Action(
                title = "Failed transfers",
                summary = "Shown when a transfer failed in the background",
                onClick = { context -> openNotificationSettings(context, CHANNEL_TRANSFER_FAILED) }
            )
        )
    ),

    SettingsCategory(
        id = "previews",
        title = "Previews",
        summary = "Images and videos",
        icon = Icons.Filled.Image,
        items = listOf(
            SettingsItem.Toggle(
                title = "Blurred backdrop",
                summary = "Shows the thumbnail of a file, blurred, behind its preview: it fills the space around a " +
                    "fullscreen image and is there while a preview is loading.",
                isChecked = { appSettings.blurredBackdrop.collectAsState().value },
                onCheckedChange = { appSettings.setBlurredBackdrop(it) }
            ),
            SettingsItem.Toggle(
                title = "Wrap long lines",
                summary = "Text previews (code, logs, configs) break long lines onto the next line. Off, the lines stay " +
                    "as they are and can be scrolled sideways.",
                isChecked = { appSettings.textWrap.collectAsState().value },
                onCheckedChange = { appSettings.setTextWrap(it) }
            )
        )
    ),

    SettingsCategory(
        id = "accessibility",
        title = "Accessibility",
        summary = "Animations",
        icon = Icons.Filled.Accessibility,
        items = listOf(
            SettingsItem.Toggle(
                title = "Reduce animations",
                summary = "Leaves out sliding, scaling, spinning and rolling text, and scrolls without animation. " +
                    "Screens then only fade.",
                isChecked = { appSettings.reduceAnimations.collectAsState().value },
                onCheckedChange = { appSettings.setReduceAnimations(it) }
            ),
            SettingsItem.Note("Animations are also reduced when they are turned off in the Android settings.")
        )
    ),

    SettingsCategory(
        id = "security",
        title = "Security",
        summary = "Locking the app",
        icon = Icons.Filled.Lock,
        items = listOf(
            SettingsItem.Toggle(
                title = "Lock with fingerprint or face",
                summary = "Asks for your fingerprint, face or screen lock each time the app is opened again, after it " +
                    "was in the background for a while. Needs a fingerprint, face or screen lock set up on this device.",
                isChecked = { appSettings.biometricLock.collectAsState().value },
                onCheckedChange = { if (appSettings.biometricLockAvailable()) appSettings.setBiometricLock(it) }
            )
        )
    ),

    SettingsCategory(
        id = HOSTS_CATEGORY_ID,
        title = "Advanced",
        summary = "Custom host settings",
        icon = Icons.Filled.Tune,
        items = listOf(
            SettingsItem.Header("Custom host settings"),
            SettingsItem.Custom { ProviderHostsSection() }
        )
    ),

    SettingsCategory(
        id = "developer",
        title = "Developer",
        summary = "Prototypes and previews",
        icon = Icons.Filled.Code,
        items = listOf(
            SettingsItem.Toggle(
                title = "FAB navigation prototype",
                summary = "Replaces the drawer with a navigation button at the bottom. Swipe the button sideways to " +
                    "move it left, center or right. While this is on, the Upload and Save Settings buttons are hidden.",
                isChecked = { appSettings.navPrototype.collectAsState().value },
                onCheckedChange = { appSettings.setNavPrototype(it) }
            ),
            SettingsItem.Header("Preview navigation as"),
            SettingsItem.Custom { NavMenuPreviewSection() },
            SettingsItem.Note("Mock providers only show how their menu looks, their items don't open anything.")
        )
    )
)

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
