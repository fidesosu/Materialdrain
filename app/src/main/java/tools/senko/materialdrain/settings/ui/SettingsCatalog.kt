package tools.senko.materialdrain.settings.ui

import tools.senko.materialdrain.ui.AppHaptics
import tools.senko.materialdrain.ui.HapticKind
import tools.senko.materialdrain.ui.VibrationStrength
import tools.senko.materialdrain.ui.icons.filled.Vibration
import android.content.ActivityNotFoundException
import android.content.Context
import android.os.Build
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
        summary = "Pixeldrain account and billing",
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
        summary = "Custom hosts",
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
        summary = "Load only on request",
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
                summary = "Load things only when tapped",
                info = "Thumbnails, previews, config updates and host checks wait until you tap them. Choose which below.",
                isChecked = { appSettings.dataSaver.collectAsState().value },
                onCheckedChange = { appSettings.setDataSaver(it) },
                children = listOf(
                    SettingsItem.Choice(
                        title = "Applies",
                        info = "Only on metered networks: mobile data and hotspots.",
                        options = listOf("Always" to false, "On mobile data" to true),
                        selected = { appSettings.dataSaverMobileOnly.collectAsState().value },
                        onSelect = { appSettings.setDataSaverMobileOnly(it) }
                    ),
                    SettingsItem.Toggle(
                        title = "Thumbnails",
                        summary = "Tap a tile to load it",
                        isChecked = { appSettings.holdThumbnails.collectAsState().value },
                        onCheckedChange = { appSettings.setHoldThumbnails(it) }
                    ),
                    SettingsItem.Toggle(
                        title = "Previews",
                        summary = "Tap Load preview to see one",
                        isChecked = { appSettings.holdPreviews.collectAsState().value },
                        onCheckedChange = { appSettings.setHoldPreviews(it) }
                    ),
                    SettingsItem.Toggle(
                        title = "Config updates",
                        summary = "Only with Check all for updates",
                        info = "Otherwise checked once a day.",
                        isChecked = { appSettings.holdConfigUpdates.collectAsState().value },
                        onCheckedChange = { appSettings.setHoldConfigUpdates(it) }
                    ),
                    SettingsItem.Toggle(
                        title = "Host checks",
                        summary = "Only with refresh in the host switcher",
                        info = "Otherwise checked every few minutes.",
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
        summary = "Theme, navigation, lists",
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
                info = "Saves power on OLED screens.",
                isChecked = { appSettings.theme.collectAsState().value.blackBackground },
                onCheckedChange = { appSettings.setBlackBackground(it) }
            ),
            // Only where the system has the wallpaper's colours to give (Android 12 and up)
            if (WallpaperColorsAvailable) SettingsItem.Toggle(
                title = "Wallpaper colours",
                summary = "Colours from your wallpaper",
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
                info = "Swipes: swipe sideways to change tabs. A thin strip at the bottom shows where you are.",
                options = listOf("Bottom bar" to false, "Swipes" to true),
                selected = { appSettings.gestureNavigation.collectAsState().value },
                onSelect = { appSettings.setGestureNavigation(it) }
            ),
            SettingsItem.Toggle(
                title = "Tab labels",
                summary = "On every tab, not only the open one",
                isChecked = { appSettings.barLabels.collectAsState().value },
                onCheckedChange = { appSettings.setBarLabels(it) }
            ),
            SettingsItem.Choice(
                title = "Open on",
                options = listOf<Pair<String, Screen?>>("Last" to null) + START_SCREENS.map { it.title to it },
                selected = { appSettings.startScreen.collectAsState().value },
                onSelect = { appSettings.setStartScreen(it) }
            ),
            SettingsItem.Toggle(
                title = "Press back twice to close",
                isChecked = { appSettings.confirmExit.collectAsState().value },
                onCheckedChange = { appSettings.setConfirmExit(it) }
            ),
            SettingsItem.Toggle(
                title = "Swipe between files",
                summary = "In file details and fullscreen",
                isChecked = { appSettings.swipeBetweenFiles.collectAsState().value },
                onCheckedChange = { appSettings.setSwipeBetweenFiles(it) }
            ),
            SettingsItem.Toggle(
                title = "Swipe between tabs",
                info = "Always on with swipe navigation.",
                isChecked = { appSettings.swipeBetweenTabs.collectAsState().value },
                onCheckedChange = { appSettings.setSwipeBetweenTabs(it) }
            ),
            SettingsItem.Header("File lists"),
            SettingsItem.Toggle(
                title = "Grid",
                summary = "Tiles instead of rows",
                info = "The default. Change one folder with View in the ⋮ menu.",
                isChecked = { appSettings.gridView.collectAsState().value },
                onCheckedChange = { appSettings.setGridView(it) }
            ),
            SettingsItem.Toggle(
                title = "Compact rows",
                summary = "Smaller rows",
                info = "The default. Change one folder with View in the ⋮ menu.",
                isChecked = { appSettings.compactRows.collectAsState().value },
                onCheckedChange = { appSettings.setCompactRows(it) }
            ),
            SettingsItem.Toggle(
                title = "Dates as time ago",
                summary = "\"3 days ago\" instead of the date",
                isChecked = { appSettings.relativeDates.collectAsState().value },
                onCheckedChange = { appSettings.setRelativeDates(it) }
            ),
            SettingsItem.Header("Previews and animations"),
            SettingsItem.Toggle(
                title = "Blurred backdrop",
                summary = "Blurred thumbnail behind previews",
                info = "Off: black background.",
                isChecked = { appSettings.blurredBackdrop.collectAsState().value },
                onCheckedChange = { appSettings.setBlurredBackdrop(it) }
            ),
            SettingsItem.Toggle(
                title = "Wrap long lines",
                summary = "In text previews",
                isChecked = { appSettings.textWrap.collectAsState().value },
                onCheckedChange = { appSettings.setTextWrap(it) }
            ),
            SettingsItem.Toggle(
                title = "Reduce animations",
                summary = "Screens only fade",
                info = "Also follows Android's animation setting.",
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
                title = "Autoplay videos",
                isChecked = { appSettings.autoplay.collectAsState().value },
                onCheckedChange = { appSettings.setAutoplay(it) }
            ),
            SettingsItem.Toggle(
                title = "Resume videos",
                info = "Remembers the last 200 videos on this device.",
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
                title = "Default speed",
                options = START_SPEEDS.map { speed -> (speed.toString().removeSuffix(".0") + "×") to speed },
                selected = { appSettings.defaultSpeed.collectAsState().value },
                onSelect = { appSettings.setDefaultSpeed(it) }
            ),
            SettingsItem.Toggle(
                title = "Brightness and volume swipes",
                summary = "In the video player",
                info = "Left side: brightness. Right side: volume.",
                isChecked = { appSettings.playerGestures.collectAsState().value },
                onCheckedChange = { appSettings.setPlayerGestures(it) }
            ),
            SettingsItem.Toggle(
                title = "Play songs in the background",
                summary = "With controls in the notification",
                info = "Off: songs stop when you leave them.",
                isChecked = { appSettings.backgroundAudio.collectAsState().value },
                onCheckedChange = { appSettings.setBackgroundAudio(it) }
            ),
            SettingsItem.Toggle(
                title = "Tint the player",
                summary = "Use the cover's colour",
                isChecked = { appSettings.tintPlayer.collectAsState().value },
                onCheckedChange = { appSettings.setTintPlayer(it) }
            ),
            SettingsItem.Toggle(
                title = "Compact player",
                summary = "Opens as a card, not fullscreen",
                isChecked = { appSettings.compactPlayer.collectAsState().value },
                onCheckedChange = { appSettings.setCompactPlayer(it) }
            ),
            SettingsItem.Toggle(
                title = "Undo closing a song",
                summary = "After swiping the miniplayer away",
                isChecked = { appSettings.undoCloseSong.collectAsState().value },
                onCheckedChange = { appSettings.setUndoCloseSong(it) }
            )
        )
    ),

    SettingsCategory(
        id = "feedback",
        title = "Feedback",
        summary = "Vibration",
        icon = Icons.Filled.Vibration,
        tone = CategoryTone.SECONDARY,
        status = {
            if (!appSettings.vibration.collectAsState().value) "Off"
            else appSettings.vibrationStrength.collectAsState().value.label
        },
        items = listOf(
            SettingsItem.Toggle(
                title = "Vibration",
                summary = "When a swipe would do something",
                info = "Also follows Android's touch feedback setting.",
                isChecked = { appSettings.vibration.collectAsState().value },
                onCheckedChange = { appSettings.setVibration(it) },
                children = listOf(
                    SettingsItem.Choice(
                        title = "Strength",
                        info = "Some phones only tell Light apart from the rest.",
                        options = VibrationStrength.entries.map { it.label to it },
                        selected = { appSettings.vibrationStrength.collectAsState().value },
                        onSelect = { appSettings.setVibrationStrength(it) }
                    ),
                    SettingsItem.Action(
                        title = "Try it",
                        onClick = { context ->
                            AppHaptics.vibratorOf(context)?.let { AppHaptics.vibrate(it, HapticKind.THRESHOLD, appSettings.vibrationStrength.value) }
                        }
                    ),
                    SettingsItem.Toggle(
                        title = "Song player swipes",
                        summary = "When letting go would open, skip or close",
                        isChecked = { appSettings.vibratePlayer.collectAsState().value },
                        onCheckedChange = { appSettings.setVibratePlayer(it) }
                    ),
                    SettingsItem.Toggle(
                        title = "Navigation swipes",
                        summary = "With swipe navigation",
                        isChecked = { appSettings.vibrateNavSwipe.collectAsState().value },
                        onCheckedChange = { appSettings.setVibrateNavSwipe(it) }
                    )
                )
            )
        )
    ),

    SettingsCategory(
        id = "files",
        title = "Files",
        summary = "Uploads, downloads, search",
        icon = Icons.Filled.Folder,
        tone = CategoryTone.PRIMARY,
        items = listOf(
            SettingsItem.Toggle(
                title = "Hide $SEARCH_INDEX_FILE_NAME",
                summary = "Pixeldrain's own index of your files",
                info = "Pixeldrain needs it and recreates it if deleted.",
                isChecked = { appSettings.hideSearchIndex.collectAsState().value },
                onCheckedChange = { appSettings.setHideSearchIndex(it) }
            ),
            SettingsItem.Toggle(
                title = "Folders first",
                isChecked = { appSettings.foldersFirst.collectAsState().value },
                onCheckedChange = { appSettings.setFoldersFirst(it) }
            ),
            SettingsItem.Toggle(
                title = "Copy the link after uploading",
                info = "One link per line. Only for hosts with links.",
                isChecked = { appSettings.copyLinkAfterUpload.collectAsState().value },
                onCheckedChange = { appSettings.setCopyLinkAfterUpload(it) }
            ),
            SettingsItem.Toggle(
                title = "Open downloads when done",
                summary = "Single files only",
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
                summary = "Let other apps open your hosts' files",
                info = "Read-only. Hidden while the app lock is on.",
                isChecked = { appSettings.documentsProvider.collectAsState().value },
                onCheckedChange = { appSettings.setDocumentsProvider(it) }
            ),
            SettingsItem.Toggle(
                title = "Upload by last change",
                summary = "Oldest first, one file at a time",
                info = "Keeps upload dates in order. Slower for many small files.",
                isChecked = { appSettings.uploadInModifiedOrder.collectAsState().value },
                onCheckedChange = { appSettings.setUploadInModifiedOrder(it) }
            ),
            SettingsItem.Toggle(
                title = "Tap twice to download",
                summary = "In a file's details",
                isChecked = { appSettings.confirmDownloadTap.collectAsState().value },
                onCheckedChange = { appSettings.setConfirmDownloadTap(it) }
            ),
            SettingsItem.Choice(
                title = "Search result limit",
                info = "Lower it if search is slow.",
                options = SEARCH_RESULT_LIMITS.map { (if (it == 0) "No limit" else "$it") to it },
                selected = { appSettings.searchResultLimit.collectAsState().value },
                onSelect = { appSettings.setSearchResultLimit(it) }
            )
        )
    ),

    SettingsCategory(
        id = "notifications",
        title = "Notifications",
        summary = "Transfer progress",
        icon = Icons.Filled.Notifications,
        tone = CategoryTone.TERTIARY,
        items = listOf(
            SettingsItem.Action(
                title = "Notification settings",
                external = true,
                onClick = { context -> openNotificationSettings(context, null) },
                children = listOf(
                    SettingsItem.Action(
                        title = "Transfer progress",
                        external = true,
                        onClick = { context -> openNotificationSettings(context, CHANNEL_TRANSFER_PROGRESS) }
                    ),
                    SettingsItem.Action(
                        title = "Completed transfers",
                        summary = "Finished in the background",
                        external = true,
                        onClick = { context -> openNotificationSettings(context, CHANNEL_TRANSFER_COMPLETE) }
                    ),
                    SettingsItem.Action(
                        title = "Failed transfers",
                        summary = "Failed in the background",
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
        summary = "App lock, screenshots",
        icon = Icons.Filled.Lock,
        tone = CategoryTone.SECONDARY,
        items = listOf(
            SettingsItem.Toggle(
                title = "Lock with fingerprint or face",
                info = if (Build.VERSION.SDK_INT >= 33) "Needs a screen lock on this device. The recent apps don't show Materialdrain while it's on."
                else "Needs a screen lock on this device. While it's on, screenshots of Materialdrain come out black, which keeps it out of the recent apps too.",
                isChecked = { appSettings.biometricLock.collectAsState().value },
                // Turned on only where there's something to unlock with; off always, whatever the device has
                onCheckedChange = { if (!it || appSettings.biometricLockAvailable()) appSettings.setBiometricLock(it) },
                children = listOf(
                    SettingsItem.Choice(
                        title = "Lock after",
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
                isChecked = { appSettings.blockScreenshots.collectAsState().value },
                onCheckedChange = { appSettings.setBlockScreenshots(it) }
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
                summary = "All versions",
                icon = Icons.Filled.Language,
                external = true,
                onClick = { context -> openLink(context, "https://materialdrain.senko.tools") }
            ),
            SettingsItem.Action(
                title = "Documentation",
                summary = "Guides and adding hosts",
                icon = Icons.AutoMirrored.Filled.MenuBook,
                external = true,
                onClick = { context -> openLink(context, "https://materialdrain.senko.tools/docs/") }
            ),
            SettingsItem.Action(
                title = "Changes",
                summary = "Release notes",
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
                summary = "On GitHub",
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
