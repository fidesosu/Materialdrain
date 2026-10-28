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
import tools.senko.materialdrain.ui.icons.filled.Refresh
import tools.senko.materialdrain.AppContainer
import tools.senko.materialdrain.update.CHANNEL_APP_UPDATES
import tools.senko.materialdrain.update.UpdateWorker
import android.app.Application
import tools.senko.materialdrain.ui.icons.filled.NewReleases
import tools.senko.materialdrain.ui.icons.filled.Notifications
import tools.senko.materialdrain.ui.icons.filled.Palette
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import tools.senko.materialdrain.ui.theme.ThemeMode
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
import android.content.res.Resources
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.runtime.Composable
import tools.senko.materialdrain.R

/** The speeds offered to start media at (Media, Start at speed). */
private val START_SPEEDS = listOf(0.75f, 1f, 1.25f, 1.5f, 2f)

/**
 * Everything the settings screen shows, in the order it's shown. It is the one place to edit: to add a setting, add an
 * item to the `items` of a category (or to the `children` of the setting it belongs to); to add a category, add a
 * [SettingsCategory] to this list. Nothing else needs to change.
 *
 * The settings go no deeper than one page: each category is a tile on the settings page which opens a page with all of
 * its settings, a setting's own settings indented under it rather than on a page further in.
 *
 * Its texts are the app's string resources, read from [resources]: see [settingsCatalog], which makes it again when the
 * language changes.
 */
private fun buildSettingsCatalog(resources: Resources): List<SettingsCategory> = listOf(

    SettingsCategory(
        id = ACCOUNT_CATEGORY_ID,
        title = resources.getString(R.string.settings_account),
        summary = resources.getString(R.string.settings_pixeldrain_account_and_billing),
        icon = Icons.Filled.AccountCircle,
        tone = CategoryTone.PRIMARY,
        // The account itself at the top of the settings, see AccountTile
        tile = { onOpen -> AccountTile(onOpen) },
        // One page of its own, see AccountPage; the API key has its own Save button there
        items = listOf(SettingsItem.Custom { AccountPage() })
    ),

    SettingsCategory(
        id = HOSTS_CATEGORY_ID,
        title = resources.getString(R.string.app_hosts),
        summary = resources.getString(R.string.settings_custom_hosts),
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
        title = resources.getString(R.string.settings_data_saver),
        summary = resources.getString(R.string.settings_load_only_on_request),
        icon = Icons.Filled.DataSaverOn,
        tone = CategoryTone.PRIMARY,
        status = {
            when {
                !appSettings.dataSaver.collectAsState().value -> null
                appSettings.dataSaverMobileOnly.collectAsState().value -> resources.getString(R.string.settings_on_mobile_data)
                else -> resources.getString(R.string.settings_on)
            }
        },
        items = listOf(
            SettingsItem.Toggle(
                title = resources.getString(R.string.settings_data_saver),
                summary = resources.getString(R.string.settings_load_things_only_when_tapped),
                info = resources.getString(R.string.settings_thumbnails_previews_config_updates_and),
                isChecked = { appSettings.dataSaver.collectAsState().value },
                onCheckedChange = { appSettings.setDataSaver(it) },
                children = listOf(
                    SettingsItem.Choice(
                        title = resources.getString(R.string.settings_applies),
                        info = resources.getString(R.string.settings_only_on_metered_networks_mobile),
                        options = listOf(resources.getString(R.string.settings_always) to false, resources.getString(R.string.settings_on_mobile_data) to true),
                        selected = { appSettings.dataSaverMobileOnly.collectAsState().value },
                        onSelect = { appSettings.setDataSaverMobileOnly(it) }
                    ),
                    SettingsItem.Toggle(
                        title = resources.getString(R.string.browser_thumbnails),
                        summary = resources.getString(R.string.settings_tap_a_tile_to_load),
                        isChecked = { appSettings.holdThumbnails.collectAsState().value },
                        onCheckedChange = { appSettings.setHoldThumbnails(it) }
                    ),
                    SettingsItem.Toggle(
                        title = resources.getString(R.string.settings_previews),
                        summary = resources.getString(R.string.settings_tap_load_preview_to_see),
                        isChecked = { appSettings.holdPreviews.collectAsState().value },
                        onCheckedChange = { appSettings.setHoldPreviews(it) }
                    ),
                    SettingsItem.Toggle(
                        title = resources.getString(R.string.settings_config_updates),
                        summary = resources.getString(R.string.settings_only_with_check_all_for),
                        info = resources.getString(R.string.settings_otherwise_checked_once_a_day),
                        isChecked = { appSettings.holdConfigUpdates.collectAsState().value },
                        onCheckedChange = { appSettings.setHoldConfigUpdates(it) }
                    ),
                    SettingsItem.Toggle(
                        title = resources.getString(R.string.settings_host_checks),
                        summary = resources.getString(R.string.settings_only_with_refresh_in_the),
                        info = resources.getString(R.string.settings_otherwise_checked_every_few_minutes),
                        isChecked = { appSettings.holdHostChecks.collectAsState().value },
                        onCheckedChange = { appSettings.setHoldHostChecks(it) }
                    )
                )
            )
        )
    ),

    SettingsCategory(
        id = "look",
        title = resources.getString(R.string.settings_look_and_feel),
        summary = resources.getString(R.string.settings_theme_navigation_lists),
        icon = Icons.Filled.Palette,
        tone = CategoryTone.SECONDARY,
        items = listOfNotNull(
            SettingsItem.Header(resources.getString(R.string.settings_theme)),
            SettingsItem.Choice(
                title = resources.getString(R.string.settings_light_or_dark),
                options = ThemeMode.entries.map { resources.getString(it.labelRes) to it },
                selected = { appSettings.theme.collectAsState().value.mode },
                onSelect = { appSettings.setThemeMode(it) }
            ),
            SettingsItem.Toggle(
                title = resources.getString(R.string.settings_black_background),
                summary = resources.getString(R.string.settings_in_the_dark_theme),
                info = resources.getString(R.string.settings_saves_power_on_oled_screens),
                isChecked = { appSettings.theme.collectAsState().value.blackBackground },
                onCheckedChange = { appSettings.setBlackBackground(it) }
            ),
            SettingsItem.Toggle(
                title = resources.getString(R.string.settings_wallpaper_colours),
                summary = resources.getString(R.string.settings_colours_from_your_wallpaper),
                isChecked = { appSettings.theme.collectAsState().value.wallpaperColors },
                onCheckedChange = { appSettings.setWallpaperColors(it) }
            ),
            SettingsItem.Custom {
                val theme = appSettings.theme.collectAsState().value
                ThemeColorPicker(selected = theme.color, enabled = theme.usesColor, onSelect = { appSettings.setThemeColor(it) })
            },
            SettingsItem.Header(resources.getString(R.string.settings_navigation)),
            SettingsItem.Choice(
                title = resources.getString(R.string.settings_navigation),
                info = resources.getString(R.string.settings_swipes_swipe_sideways_to_change),
                options = listOf(resources.getString(R.string.settings_bottom_bar) to false, resources.getString(R.string.settings_swipes) to true),
                selected = { appSettings.gestureNavigation.collectAsState().value },
                onSelect = { appSettings.setGestureNavigation(it) }
            ),
            SettingsItem.Toggle(
                title = resources.getString(R.string.settings_tab_labels),
                summary = resources.getString(R.string.settings_on_every_tab_not_only),
                isChecked = { appSettings.barLabels.collectAsState().value },
                onCheckedChange = { appSettings.setBarLabels(it) }
            ),
            SettingsItem.Choice(
                title = resources.getString(R.string.settings_open_on),
                options = listOf<Pair<String, Screen?>>(resources.getString(R.string.settings_last) to null) + START_SCREENS.map { resources.getString(it.titleRes) to it },
                selected = { appSettings.startScreen.collectAsState().value },
                onSelect = { appSettings.setStartScreen(it) }
            ),
            SettingsItem.Toggle(
                title = resources.getString(R.string.settings_press_back_twice_to_close),
                isChecked = { appSettings.confirmExit.collectAsState().value },
                onCheckedChange = { appSettings.setConfirmExit(it) }
            ),
            SettingsItem.Toggle(
                title = resources.getString(R.string.settings_swipe_between_files),
                summary = resources.getString(R.string.settings_in_file_details_and_fullscreen),
                isChecked = { appSettings.swipeBetweenFiles.collectAsState().value },
                onCheckedChange = { appSettings.setSwipeBetweenFiles(it) }
            ),
            SettingsItem.Toggle(
                title = resources.getString(R.string.settings_swipe_between_tabs),
                info = resources.getString(R.string.settings_always_on_with_swipe_navigation),
                isChecked = { appSettings.swipeBetweenTabs.collectAsState().value },
                onCheckedChange = { appSettings.setSwipeBetweenTabs(it) }
            ),
            SettingsItem.Header(resources.getString(R.string.settings_file_lists)),
            SettingsItem.Toggle(
                title = resources.getString(R.string.browser_grid),
                summary = resources.getString(R.string.settings_tiles_instead_of_rows),
                info = resources.getString(R.string.settings_the_default_change_one_folder),
                isChecked = { appSettings.gridView.collectAsState().value },
                onCheckedChange = { appSettings.setGridView(it) }
            ),
            SettingsItem.Toggle(
                title = resources.getString(R.string.browser_compact_rows),
                summary = resources.getString(R.string.settings_smaller_rows),
                info = resources.getString(R.string.settings_the_default_change_one_folder),
                isChecked = { appSettings.compactRows.collectAsState().value },
                onCheckedChange = { appSettings.setCompactRows(it) }
            ),
            SettingsItem.Toggle(
                title = resources.getString(R.string.settings_dates_as_time_ago),
                summary = resources.getString(R.string.settings_3_days_ago_instead_of),
                isChecked = { appSettings.relativeDates.collectAsState().value },
                onCheckedChange = { appSettings.setRelativeDates(it) }
            ),
            SettingsItem.Header(resources.getString(R.string.settings_previews_and_animations)),
            SettingsItem.Toggle(
                title = resources.getString(R.string.settings_blurred_backdrop),
                summary = resources.getString(R.string.settings_blurred_thumbnail_behind_previews),
                info = resources.getString(R.string.settings_off_black_background),
                isChecked = { appSettings.blurredBackdrop.collectAsState().value },
                onCheckedChange = { appSettings.setBlurredBackdrop(it) }
            ),
            SettingsItem.Toggle(
                title = resources.getString(R.string.common_wrap_long_lines),
                summary = resources.getString(R.string.settings_in_text_previews),
                isChecked = { appSettings.textWrap.collectAsState().value },
                onCheckedChange = { appSettings.setTextWrap(it) }
            ),
            SettingsItem.Toggle(
                title = resources.getString(R.string.settings_reduce_animations),
                summary = resources.getString(R.string.settings_screens_only_fade),
                info = resources.getString(R.string.settings_also_follows_android_s_animation),
                isChecked = { appSettings.reduceAnimations.collectAsState().value },
                onCheckedChange = { appSettings.setReduceAnimations(it) }
            )
        )
    ),

    SettingsCategory(
        id = "media",
        title = resources.getString(R.string.settings_media),
        summary = resources.getString(R.string.settings_videos_and_songs),
        icon = Icons.Filled.Movie,
        tone = CategoryTone.TERTIARY,
        items = listOf(
            SettingsItem.Toggle(
                title = resources.getString(R.string.settings_autoplay_videos),
                isChecked = { appSettings.autoplay.collectAsState().value },
                onCheckedChange = { appSettings.setAutoplay(it) }
            ),
            SettingsItem.Toggle(
                title = resources.getString(R.string.settings_resume_videos),
                info = resources.getString(R.string.settings_remembers_the_last_200_videos),
                isChecked = { appSettings.resumeVideos.collectAsState().value },
                onCheckedChange = { appSettings.setResumeVideos(it) }
            ),
            SettingsItem.Choice(
                title = resources.getString(R.string.settings_skip_by),
                summary = resources.getString(R.string.settings_the_skip_buttons_and_a),
                options = SEEK_STEPS.map { "$it s" to it },
                selected = { appSettings.seekStepSeconds.collectAsState().value },
                onSelect = { appSettings.setSeekStepSeconds(it) }
            ),
            SettingsItem.Choice(
                title = resources.getString(R.string.settings_default_speed),
                options = START_SPEEDS.map { speed -> (speed.toString().removeSuffix(".0") + "×") to speed },
                selected = { appSettings.defaultSpeed.collectAsState().value },
                onSelect = { appSettings.setDefaultSpeed(it) }
            ),
            SettingsItem.Toggle(
                title = resources.getString(R.string.settings_brightness_and_volume_swipes),
                summary = resources.getString(R.string.settings_in_the_video_player),
                info = resources.getString(R.string.settings_left_side_brightness_right_side),
                isChecked = { appSettings.playerGestures.collectAsState().value },
                onCheckedChange = { appSettings.setPlayerGestures(it) }
            ),
            SettingsItem.Toggle(
                title = resources.getString(R.string.settings_play_songs_in_the_background),
                summary = resources.getString(R.string.settings_with_controls_in_the_notification),
                info = resources.getString(R.string.settings_off_songs_stop_when_you),
                isChecked = { appSettings.backgroundAudio.collectAsState().value },
                onCheckedChange = { appSettings.setBackgroundAudio(it) }
            ),
            SettingsItem.Toggle(
                title = resources.getString(R.string.settings_tint_the_player),
                summary = resources.getString(R.string.settings_use_the_cover_s_colour),
                isChecked = { appSettings.tintPlayer.collectAsState().value },
                onCheckedChange = { appSettings.setTintPlayer(it) }
            ),
            SettingsItem.Toggle(
                title = resources.getString(R.string.settings_compact_player),
                summary = resources.getString(R.string.settings_opens_as_a_card_not),
                isChecked = { appSettings.compactPlayer.collectAsState().value },
                onCheckedChange = { appSettings.setCompactPlayer(it) }
            ),
            SettingsItem.Toggle(
                title = resources.getString(R.string.settings_undo_closing_a_song),
                summary = resources.getString(R.string.settings_after_swiping_the_miniplayer_away),
                isChecked = { appSettings.undoCloseSong.collectAsState().value },
                onCheckedChange = { appSettings.setUndoCloseSong(it) }
            )
        )
    ),

    SettingsCategory(
        id = "feedback",
        title = resources.getString(R.string.settings_feedback),
        summary = resources.getString(R.string.settings_vibration),
        icon = Icons.Filled.Vibration,
        tone = CategoryTone.SECONDARY,
        status = {
            if (!appSettings.vibration.collectAsState().value) resources.getString(R.string.media_off)
            else resources.getString(appSettings.vibrationStrength.collectAsState().value.labelRes)
        },
        items = listOf(
            SettingsItem.Toggle(
                title = resources.getString(R.string.settings_vibration),
                summary = resources.getString(R.string.settings_when_a_swipe_would_do),
                info = resources.getString(R.string.settings_also_follows_android_s_touch),
                isChecked = { appSettings.vibration.collectAsState().value },
                onCheckedChange = { appSettings.setVibration(it) },
                children = listOf(
                    SettingsItem.Choice(
                        title = resources.getString(R.string.settings_strength),
                        info = resources.getString(R.string.settings_some_phones_only_tell_light),
                        options = VibrationStrength.entries.map { resources.getString(it.labelRes) to it },
                        selected = { appSettings.vibrationStrength.collectAsState().value },
                        onSelect = { appSettings.setVibrationStrength(it) }
                    ),
                    SettingsItem.Action(
                        title = resources.getString(R.string.settings_try_it),
                        onClick = { context ->
                            AppHaptics.vibratorOf(context)?.let { AppHaptics.vibrate(it, HapticKind.THRESHOLD, appSettings.vibrationStrength.value) }
                        }
                    ),
                    SettingsItem.Toggle(
                        title = resources.getString(R.string.settings_song_player_swipes),
                        summary = resources.getString(R.string.settings_when_letting_go_would_open),
                        isChecked = { appSettings.vibratePlayer.collectAsState().value },
                        onCheckedChange = { appSettings.setVibratePlayer(it) }
                    ),
                    SettingsItem.Toggle(
                        title = resources.getString(R.string.settings_navigation_swipes),
                        summary = resources.getString(R.string.settings_with_swipe_navigation),
                        isChecked = { appSettings.vibrateNavSwipe.collectAsState().value },
                        onCheckedChange = { appSettings.setVibrateNavSwipe(it) }
                    )
                )
            )
        )
    ),

    SettingsCategory(
        id = "files",
        title = resources.getString(R.string.settings_files),
        summary = resources.getString(R.string.settings_uploads_downloads_search),
        icon = Icons.Filled.Folder,
        tone = CategoryTone.PRIMARY,
        items = listOf(
            SettingsItem.Toggle(
                title = resources.getString(R.string.settings_hide, SEARCH_INDEX_FILE_NAME),
                summary = resources.getString(R.string.settings_pixeldrain_s_own_index_of),
                info = resources.getString(R.string.settings_pixeldrain_needs_it_and_recreates),
                isChecked = { appSettings.hideSearchIndex.collectAsState().value },
                onCheckedChange = { appSettings.setHideSearchIndex(it) }
            ),
            SettingsItem.Toggle(
                title = resources.getString(R.string.settings_folders_first),
                isChecked = { appSettings.foldersFirst.collectAsState().value },
                onCheckedChange = { appSettings.setFoldersFirst(it) }
            ),
            SettingsItem.Toggle(
                title = resources.getString(R.string.settings_copy_the_link_after_uploading),
                info = resources.getString(R.string.settings_one_link_per_line_only),
                isChecked = { appSettings.copyLinkAfterUpload.collectAsState().value },
                onCheckedChange = { appSettings.setCopyLinkAfterUpload(it) }
            ),
            SettingsItem.Toggle(
                title = resources.getString(R.string.settings_open_downloads_when_done),
                summary = resources.getString(R.string.settings_single_files_only),
                isChecked = { appSettings.openAfterDownload.collectAsState().value },
                onCheckedChange = { appSettings.setOpenAfterDownload(it) }
            ),
            SettingsItem.Choice(
                title = resources.getString(R.string.settings_download_into),
                options = listOf(resources.getString(R.string.browser_download) to false, "Download/Materialdrain" to true),
                selected = { appSettings.downloadSubfolder.collectAsState().value },
                onSelect = { appSettings.setDownloadSubfolder(it) }
            ),
            SettingsItem.Toggle(
                title = resources.getString(R.string.settings_show_hosts_in_the_file),
                summary = resources.getString(R.string.settings_let_other_apps_open_your),
                info = resources.getString(R.string.settings_read_only_hidden_while_the),
                isChecked = { appSettings.documentsProvider.collectAsState().value },
                onCheckedChange = { appSettings.setDocumentsProvider(it) }
            ),
            SettingsItem.Toggle(
                title = resources.getString(R.string.settings_upload_by_last_change),
                summary = resources.getString(R.string.settings_oldest_first_one_file_at),
                info = resources.getString(R.string.settings_keeps_upload_dates_in_order),
                isChecked = { appSettings.uploadInModifiedOrder.collectAsState().value },
                onCheckedChange = { appSettings.setUploadInModifiedOrder(it) }
            ),
            SettingsItem.Toggle(
                title = resources.getString(R.string.settings_tap_twice_to_download),
                summary = resources.getString(R.string.settings_in_a_file_s_details),
                isChecked = { appSettings.confirmDownloadTap.collectAsState().value },
                onCheckedChange = { appSettings.setConfirmDownloadTap(it) }
            ),
            SettingsItem.Choice(
                title = resources.getString(R.string.settings_search_result_limit),
                info = resources.getString(R.string.settings_lower_it_if_search_is),
                options = SEARCH_RESULT_LIMITS.map { (if (it == 0) resources.getString(R.string.settings_no_limit) else "$it") to it },
                selected = { appSettings.searchResultLimit.collectAsState().value },
                onSelect = { appSettings.setSearchResultLimit(it) }
            )
        )
    ),

    SettingsCategory(
        id = "notifications",
        title = resources.getString(R.string.settings_notifications),
        summary = resources.getString(R.string.upload_transfer_progress),
        icon = Icons.Filled.Notifications,
        tone = CategoryTone.TERTIARY,
        items = listOf(
            SettingsItem.Action(
                title = resources.getString(R.string.settings_notification_settings),
                external = true,
                onClick = { context -> openNotificationSettings(context, null) },
                children = listOf(
                    SettingsItem.Action(
                        title = resources.getString(R.string.upload_transfer_progress),
                        external = true,
                        onClick = { context -> openNotificationSettings(context, CHANNEL_TRANSFER_PROGRESS) }
                    ),
                    SettingsItem.Action(
                        title = resources.getString(R.string.upload_completed_transfers),
                        summary = resources.getString(R.string.settings_finished_in_the_background),
                        external = true,
                        onClick = { context -> openNotificationSettings(context, CHANNEL_TRANSFER_COMPLETE) }
                    ),
                    SettingsItem.Action(
                        title = resources.getString(R.string.update_app_updates),
                        summary = resources.getString(R.string.settings_from_the_daily_check),
                        external = true,
                        onClick = { context ->
                            UpdateWorker.ensureChannel(context)
                            openNotificationSettings(context, CHANNEL_APP_UPDATES)
                        }
                    ),
                    SettingsItem.Action(
                        title = resources.getString(R.string.upload_failed_transfers),
                        summary = resources.getString(R.string.settings_failed_in_the_background),
                        external = true,
                        onClick = { context -> openNotificationSettings(context, CHANNEL_TRANSFER_FAILED) }
                    )
                )
            )
        )
    ),

    SettingsCategory(
        id = "security",
        title = resources.getString(R.string.settings_privacy_and_security),
        summary = resources.getString(R.string.settings_app_lock_screenshots),
        icon = Icons.Filled.Lock,
        tone = CategoryTone.SECONDARY,
        items = listOf(
            SettingsItem.Toggle(
                title = resources.getString(R.string.settings_lock_with_fingerprint_or_face),
                info = (if (Build.VERSION.SDK_INT >= 33) resources.getString(R.string.settings_needs_a_screen_lock_on)
                else resources.getString(R.string.settings_needs_a_screen_lock_on_2)) +
                    resources.getString(R.string.settings_the_device_s_lock_screen),
                isChecked = { appSettings.biometricLock.collectAsState().value },
                // Turned on only where there's something to unlock with; off always, whatever the device has
                onCheckedChange = { if (!it || appSettings.biometricLockAvailable()) appSettings.setBiometricLock(it) },
                children = listOf(
                    SettingsItem.Choice(
                        title = resources.getString(R.string.settings_lock_after),
                        options = LOCK_TIMEOUTS.map { seconds ->
                            val label = when {
                                seconds == 0 -> resources.getString(R.string.settings_always)
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
                title = resources.getString(R.string.settings_block_screenshots),
                summary = resources.getString(R.string.settings_and_hide_the_app_in),
                isChecked = { appSettings.blockScreenshots.collectAsState().value },
                onCheckedChange = { appSettings.setBlockScreenshots(it) }
            )
        )
    ),

    SettingsCategory(
        id = "about",
        title = resources.getString(R.string.settings_about),
        summary = resources.getString(R.string.settings_version_and_links),
        icon = Icons.Filled.Info,
        tone = CategoryTone.PRIMARY,
        status = {
            val context = LocalContext.current
            remember { readAbout(context).versionName }
        },
        items = listOf(
            SettingsItem.Custom { AboutSection() },
            SettingsItem.Header(resources.getString(R.string.settings_updates)),
            SettingsItem.Action(
                title = resources.getString(R.string.settings_check_for_updates),
                summary = resources.getString(R.string.settings_and_see_what_s_new),
                icon = Icons.Filled.Refresh,
                onClick = { context -> AppContainer.get(context.applicationContext as Application).appUpdates.checkNow() }
            ),
            SettingsItem.Toggle(
                title = resources.getString(R.string.settings_check_when_the_app_opens),
                summary = resources.getString(R.string.settings_shows_what_a_new_version),
                info = resources.getString(R.string.settings_looks_on_github_where_the),
                isChecked = { appSettings.checkUpdatesOnOpening.collectAsState().value },
                onCheckedChange = { appSettings.setCheckUpdatesOnOpening(it) }
            ),
            SettingsItem.Toggle(
                title = resources.getString(R.string.settings_also_check_daily_in_the),
                summary = resources.getString(R.string.settings_a_notification_when_a_new),
                info = resources.getString(R.string.settings_once_a_day_with_any),
                isChecked = { appSettings.checkUpdatesInBackground.collectAsState().value },
                onCheckedChange = { appSettings.setCheckUpdatesInBackground(it) }
            ),
            // Not under the switch above: "Check for updates" follows it too
            SettingsItem.Toggle(
                title = resources.getString(R.string.settings_include_development_builds),
                summary = resources.getString(R.string.settings_newer_and_may_have_bugs),
                info = resources.getString(R.string.settings_development_builds_have_the_newest),
                isChecked = { appSettings.includeDevUpdates.collectAsState().value },
                onCheckedChange = { appSettings.setIncludeDevUpdates(it) }
            ),
            SettingsItem.Toggle(
                title = resources.getString(R.string.settings_show_what_s_new_after),
                summary = resources.getString(R.string.settings_once_when_the_app_first),
                isChecked = { appSettings.showWhatsNew.collectAsState().value },
                onCheckedChange = { appSettings.setShowWhatsNew(it) }
            ),
            SettingsItem.Header(resources.getString(R.string.upload_links)),
            SettingsItem.Action(
                title = resources.getString(R.string.settings_website),
                summary = resources.getString(R.string.settings_all_versions),
                icon = Icons.Filled.Language,
                external = true,
                onClick = { context -> openLink(context, "https://materialdrain.senko.tools") }
            ),
            SettingsItem.Action(
                title = resources.getString(R.string.settings_documentation),
                summary = resources.getString(R.string.settings_guides_and_adding_hosts),
                icon = Icons.AutoMirrored.Filled.MenuBook,
                external = true,
                onClick = { context -> openLink(context, "https://materialdrain.senko.tools/docs/") }
            ),
            SettingsItem.Action(
                title = resources.getString(R.string.settings_changes),
                summary = resources.getString(R.string.settings_release_notes),
                icon = Icons.Filled.NewReleases,
                external = true,
                onClick = { context -> openLink(context, "https://github.com/fidesosu/Materialdrain/releases") }
            ),
            SettingsItem.Action(
                title = resources.getString(R.string.settings_source_code),
                summary = resources.getString(R.string.settings_on_github),
                icon = Icons.Filled.Code,
                external = true,
                onClick = { context -> openLink(context, "https://github.com/fidesosu/Materialdrain") }
            ),
            SettingsItem.Action(
                title = resources.getString(R.string.settings_report_a_problem),
                summary = resources.getString(R.string.settings_on_github),
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

/** The settings (see [buildSettingsCatalog]) in the app's language: made once, and again when the configuration changes. */
@Composable
fun settingsCatalog(): List<SettingsCategory> {
    val resources = LocalResources.current
    val configuration = LocalConfiguration.current
    return remember(configuration) { buildSettingsCatalog(resources) }
}

@Composable
fun settingsCategory(id: String?): SettingsCategory? = settingsCatalog().firstOrNull { it.id == id }

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
