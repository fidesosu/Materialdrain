package tools.senko.materialdrain.preferences

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.material.icons.Icons
import tools.senko.materialdrain.ui.icons.automirrored.filled.MenuBook
import tools.senko.materialdrain.ui.icons.filled.AccountCircle
import tools.senko.materialdrain.ui.icons.filled.BugReport
import tools.senko.materialdrain.ui.icons.filled.Code
import tools.senko.materialdrain.ui.icons.filled.DataSaverOn
import tools.senko.materialdrain.ui.icons.filled.Dns
import tools.senko.materialdrain.ui.icons.filled.Folder
import tools.senko.materialdrain.ui.icons.filled.Info
import tools.senko.materialdrain.ui.icons.filled.Language
import tools.senko.materialdrain.ui.icons.filled.Lock
import tools.senko.materialdrain.ui.icons.filled.NewReleases
import tools.senko.materialdrain.ui.icons.filled.Notifications
import tools.senko.materialdrain.ui.icons.filled.Palette
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import tools.senko.materialdrain.navmenu.NavMenuPreview
import tools.senko.materialdrain.ui.theme.ThemeMode
import tools.senko.materialdrain.ui.theme.WallpaperColorsAvailable
import tools.senko.materialdrain.settings.SEARCH_INDEX_FILE_NAME
import tools.senko.materialdrain.settings.SEARCH_RESULT_LIMITS
import tools.senko.materialdrain.settings.START_SCREENS
import tools.senko.materialdrain.settings.SEEK_STEPS
import tools.senko.materialdrain.settings.LOCK_TIMEOUTS
import tools.senko.materialdrain.Screen
import tools.senko.materialdrain.ui.icons.filled.Movie
import tools.senko.materialdrain.transfer.CHANNEL_TRANSFER_COMPLETE
import tools.senko.materialdrain.transfer.CHANNEL_TRANSFER_FAILED
import tools.senko.materialdrain.transfer.CHANNEL_TRANSFER_PROGRESS

/** The speeds offered to start media at (Media, Start at speed). */
private val START_SPEEDS = listOf(0.75f, 1f, 1.25f, 1.5f, 2f)

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
                        info = "Pictures, videos, songs, text, PDFs and the contents of archives. A video or song still only " +
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
        summary = "Theme, navigation, previews and animations",
        icon = Icons.Filled.Palette,
        tone = CategoryTone.SECONDARY,
        items = listOfNotNull(
            SettingsItem.Header("Theme"),
            SettingsItem.Choice(
                title = "Light or dark",
                options = ThemeMode.entries.map { it.label to it },
                selected = { appSettings.theme.collectAsState().value.mode },
                onSelect = { appSettings.setThemeMode(it) }
            ),
            SettingsItem.Toggle(
                title = "Black background",
                summary = "In the dark theme",
                info = "The dark theme's background is black, and its cards darker, which saves power on an OLED screen.",
                isChecked = { appSettings.theme.collectAsState().value.blackBackground },
                onCheckedChange = { appSettings.setBlackBackground(it) }
            ),
            // Only where the system has the wallpaper's colours to give (Android 12 and up)
            if (WallpaperColorsAvailable) SettingsItem.Toggle(
                title = "Wallpaper colours",
                summary = "The app's colours come from your wallpaper",
                info = "Off, they are made from the colour chosen below instead.",
                isChecked = { appSettings.theme.collectAsState().value.wallpaperColors },
                onCheckedChange = { appSettings.setWallpaperColors(it) }
            ) else null,
            SettingsItem.Custom {
                val theme = appSettings.theme.collectAsState().value
                ThemeColorPicker(selected = theme.color, enabled = theme.usesColor, onSelect = { appSettings.setThemeColor(it) })
            },
            SettingsItem.Header("Navigation"),
            SettingsItem.Choice(
                title = "Navigation",
                info = "The bottom bar has a tab for each screen. The floating button opens a small window of them and can " +
                    "be swiped to the left, the middle or the right; it's still being tried out. Where the screen is wide " +
                    "(a tablet, a phone on its side) the bar's tabs go along the side.",
                options = listOf("Bottom bar" to false, "Floating button" to true),
                selected = { appSettings.navPrototype.collectAsState().value },
                onSelect = { appSettings.setNavPrototype(it) }
            ),
            SettingsItem.Toggle(
                title = "Every tab's name",
                summary = "Under its icon in the bar",
                info = "Off, only the chosen tab has its name under its icon, the others only their icons.",
                isChecked = { appSettings.barLabels.collectAsState().value },
                onCheckedChange = { appSettings.setBarLabels(it) }
            ),
            SettingsItem.Choice(
                title = "Open on",
                summary = "The screen the app starts on",
                options = listOf<Pair<String, Screen?>>("Last" to null) + START_SCREENS.map { it.title to it },
                selected = { appSettings.startScreen.collectAsState().value },
                onSelect = { appSettings.setStartScreen(it) }
            ),
            SettingsItem.Toggle(
                title = "Press back twice to close",
                summary = "So one press doesn't close the app",
                isChecked = { appSettings.confirmExit.collectAsState().value },
                onCheckedChange = { appSettings.setConfirmExit(it) }
            ),
            SettingsItem.Toggle(
                title = "Swipe between files",
                summary = "In a file's details",
                info = "A swipe to the left in a file's details opens the next file of the list it was opened from, a swipe " +
                    "to the right the one before. What scrolls sideways (a wide text, a zoomed picture) scrolls first.",
                isChecked = { appSettings.swipeBetweenFiles.collectAsState().value },
                onCheckedChange = { appSettings.setSwipeBetweenFiles(it) }
            ),
            SettingsItem.Toggle(
                title = "Swipe between tabs",
                summary = "Swipe sideways to go to the next tab",
                info = "A swipe to the left goes to the tab to the right, as the tabs lie in the bottom bar, and a swipe " +
                    "to the right to the one to the left. A swipe that starts at the very edge of the screen is the " +
                    "system's back gesture, and what scrolls sideways (the folder path, a wide text) scrolls first.",
                isChecked = { appSettings.swipeBetweenTabs.collectAsState().value },
                onCheckedChange = { appSettings.setSwipeBetweenTabs(it) }
            ),
            SettingsItem.Header("File lists"),
            SettingsItem.Toggle(
                title = "Grid",
                summary = "Big pictures instead of rows",
                isChecked = { appSettings.gridView.collectAsState().value },
                onCheckedChange = { appSettings.setGridView(it) }
            ),
            SettingsItem.Toggle(
                title = "Compact rows",
                summary = "Lower rows, so more of them fit",
                isChecked = { appSettings.compactRows.collectAsState().value },
                onCheckedChange = { appSettings.setCompactRows(it) }
            ),
            SettingsItem.Toggle(
                title = "Dates as time ago",
                summary = "\"3 days ago\" instead of the date",
                info = "Off, the lists show the date and time a file was changed (or uploaded) instead.",
                isChecked = { appSettings.relativeDates.collectAsState().value },
                onCheckedChange = { appSettings.setRelativeDates(it) }
            ),
            SettingsItem.Header("Previews and animations"),
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
        id = "media",
        title = "Media",
        summary = "Videos and songs",
        icon = Icons.Filled.Movie,
        tone = CategoryTone.TERTIARY,
        items = listOf(
            SettingsItem.Toggle(
                title = "Play videos at once",
                summary = "When the player opens",
                isChecked = { appSettings.autoplay.collectAsState().value },
                onCheckedChange = { appSettings.setAutoplay(it) }
            ),
            SettingsItem.Toggle(
                title = "Resume videos",
                summary = "From where they were left",
                info = "A video opened again goes on from where it was stopped. Kept for the last 200 videos, on this " +
                    "device only. One watched to its end starts from the beginning again.",
                isChecked = { appSettings.resumeVideos.collectAsState().value },
                onCheckedChange = { appSettings.setResumeVideos(it) }
            ),
            SettingsItem.Choice(
                title = "Skip by",
                summary = "The skip buttons and a double tap",
                options = SEEK_STEPS.map { "$it s" to it },
                selected = { appSettings.seekStepSeconds.collectAsState().value },
                onSelect = { appSettings.setSeekStepSeconds(it) }
            ),
            SettingsItem.Choice(
                title = "Start at speed",
                summary = "Videos and songs",
                options = START_SPEEDS.map { speed -> (speed.toString().removeSuffix(".0") + "×") to speed },
                selected = { appSettings.defaultSpeed.collectAsState().value },
                onSelect = { appSettings.setDefaultSpeed(it) }
            ),
            SettingsItem.Toggle(
                title = "Brightness and volume swipes",
                summary = "Up and down in the video player",
                info = "In the video player, a swipe up or down on the left half changes the brightness of the screen (for " +
                    "the video only), on the right half the volume.",
                isChecked = { appSettings.playerGestures.collectAsState().value },
                onCheckedChange = { appSettings.setPlayerGestures(it) }
            ),
            SettingsItem.Toggle(
                title = "Play songs in the background",
                summary = "With controls in the notification",
                info = "A song goes on playing after you leave its details or the app, with its controls in the " +
                    "notification and on the lock screen. Off, it stops with its details.",
                isChecked = { appSettings.backgroundAudio.collectAsState().value },
                onCheckedChange = { appSettings.setBackgroundAudio(it) }
            )
        )
    ),

    SettingsCategory(
        id = "files",
        title = "Files",
        summary = "Your filesystem, uploads, downloads and search",
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
                title = "Folders first",
                summary = "In the filesystem, however it's sorted",
                isChecked = { appSettings.foldersFirst.collectAsState().value },
                onCheckedChange = { appSettings.setFoldersFirst(it) }
            ),
            SettingsItem.Toggle(
                title = "Copy the link after uploading",
                summary = "Of each file uploaded",
                info = "When an upload is done, the links of the uploaded files are copied, one per line, ready to paste. " +
                    "Only for hosts whose files have links, such as Pixeldrain.",
                isChecked = { appSettings.copyLinkAfterUpload.collectAsState().value },
                onCheckedChange = { appSettings.setCopyLinkAfterUpload(it) }
            ),
            SettingsItem.Toggle(
                title = "Open downloads when done",
                summary = "A file downloaded on its own",
                info = "A file is opened with the app for its kind as soon as it's downloaded. Not the files of a download " +
                    "of several.",
                isChecked = { appSettings.openAfterDownload.collectAsState().value },
                onCheckedChange = { appSettings.setOpenAfterDownload(it) }
            ),
            SettingsItem.Choice(
                title = "Download into",
                options = listOf("Download" to false, "Download/Materialdrain" to true),
                selected = { appSettings.downloadSubfolder.collectAsState().value },
                onSelect = { appSettings.setDownloadSubfolder(it) }
            ),
            SettingsItem.Toggle(
                title = "Show hosts in the file picker",
                summary = "So other apps can open their files",
                info = "The hosts with folders show in Android's file picker and Files app, where other apps can open (not " +
                    "change) their files. A file is fetched whole when it's opened. Not while the app is locked with " +
                    "your fingerprint, which the picker doesn't ask for.",
                isChecked = { appSettings.documentsProvider.collectAsState().value },
                onCheckedChange = { appSettings.setDocumentsProvider(it) }
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
            SettingsItem.Toggle(
                title = "Tap twice to download",
                summary = "In a file's details",
                info = "The Download button in a file's details asks to be tapped again before it starts, so a slip of " +
                    "the finger doesn't start a download of what may be gigabytes. Off, one tap starts it.",
                isChecked = { appSettings.confirmDownloadTap.collectAsState().value },
                onCheckedChange = { appSettings.setConfirmDownloadTap(it) }
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
        summary = "Locking the app, screenshots",
        icon = Icons.Filled.Lock,
        tone = CategoryTone.SECONDARY,
        items = listOf(
            SettingsItem.Toggle(
                title = "Lock with fingerprint or face",
                summary = "When the app is opened again",
                info = "Asks for your fingerprint, face or screen lock each time the app is opened again, after it was in " +
                    "the background for a while. Needs a fingerprint, face or screen lock set up on this device.",
                isChecked = { appSettings.biometricLock.collectAsState().value },
                onCheckedChange = { if (appSettings.biometricLockAvailable()) appSettings.setBiometricLock(it) },
                children = listOf(
                    SettingsItem.Choice(
                        title = "Ask again after",
                        summary = "Away from the app",
                        options = LOCK_TIMEOUTS.map { seconds ->
                            val label = when {
                                seconds == 0 -> "Always"
                                seconds < 60 -> "$seconds s"
                                else -> "${seconds / 60} min"
                            }
                            label to seconds
                        },
                        selected = { appSettings.lockTimeoutSeconds.collectAsState().value },
                        onSelect = { appSettings.setLockTimeoutSeconds(it) }
                    )
                )
            ),
            SettingsItem.Toggle(
                title = "Block screenshots",
                summary = "And hide the app in recent apps",
                info = "Screenshots and screen recordings of the app come out black, adb's included, and the recent apps " +
                    "show it blank.",
                isChecked = { appSettings.blockScreenshots.collectAsState().value },
                onCheckedChange = { appSettings.setBlockScreenshots(it) }
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
            SettingsItem.Choice(
                title = "Navigation button's menu",
                summary = "When the floating button is on",
                info = "The floating button (Look and feel, Navigation) shows this host's tabs, or the menu of a made-up " +
                    "host with dozens of screens, to see how a big one looks. The made-up items don't open anything.",
                options = NavMenuPreview.entries.map { it.label to it },
                selected = { appSettings.navMenuPreview.collectAsState().value },
                onSelect = { appSettings.setNavMenuPreview(it) }
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
