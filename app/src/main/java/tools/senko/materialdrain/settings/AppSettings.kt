package tools.senko.materialdrain.settings

import tools.senko.materialdrain.ui.VibrationStrength
import android.content.Context
import android.content.SharedPreferences
import tools.senko.materialdrain.documents.HostDocumentsProvider
import androidx.biometric.BiometricManager
import tools.senko.materialdrain.auth.LOCK_AUTHENTICATORS
import android.provider.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import androidx.core.content.edit
import tools.senko.materialdrain.Screen
import tools.senko.materialdrain.files.SortableField
import tools.senko.materialdrain.provider.api.FileList
import tools.senko.materialdrain.provider.api.StorageNode
import tools.senko.materialdrain.ui.theme.DEFAULT_THEME_COLOR
import tools.senko.materialdrain.ui.theme.ThemeMode
import tools.senko.materialdrain.ui.theme.ThemeSettings

private const val PREFS_NAME = "pixeldrain_prefs"
private const val HIDE_SEARCH_INDEX_PREF = "hide_search_index"
private const val REDUCE_ANIMATIONS_PREF = "reduce_animations"
private const val BLURRED_BACKDROP_PREF = "blurred_backdrop"
private const val LOOP_VIDEOS_PREF = "loop_videos"
private const val THEME_MODE_PREF = "theme_mode"
private const val WALLPAPER_COLORS_PREF = "theme_wallpaper_colors"
private const val THEME_COLOR_PREF = "theme_color"
private const val BLACK_BACKGROUND_PREF = "theme_black_background"
private const val SWIPE_TABS_PREF = "swipe_between_tabs"
private const val GESTURE_NAVIGATION_PREF = "gesture_navigation"
private const val SORT_FIELD_PREF = "files_sort_field"
private const val SORT_ASCENDING_PREF = "files_sort_ascending"
private const val TEXT_WRAP_PREF = "text_wrap"
private const val DATA_SAVER_PREF = "data_saver"
private const val DATA_SAVER_MOBILE_ONLY_PREF = "data_saver_mobile_only"
private const val HOLD_THUMBNAILS_PREF = "data_saver_hold_thumbnails"
private const val HOLD_PREVIEWS_PREF = "data_saver_hold_previews"
private const val HOLD_CONFIG_UPDATES_PREF = "data_saver_hold_config_updates"
private const val HOLD_HOST_CHECKS_PREF = "data_saver_hold_host_checks"
private const val LAST_SCREEN_PREF = "last_screen"
private const val LAST_TAB_PREF = "last_tab"
private const val FILESYSTEM_PATH_PREFIX = "filesystem_path_"
private const val VIEW_LOOK_PREFIX = "view_look_"
private const val OPENED_LIST_ID_PREF = "opened_list_id"
private const val OPENED_LIST_TITLE_PREF = "opened_list_title"
private const val OPENED_LIST_COUNT_PREF = "opened_list_count"
private const val OPENED_LIST_CAN_EDIT_PREF = "opened_list_can_edit"
private const val OPENED_LIST_HOST_PREF = "opened_list_host"
private const val OPENED_FILE_ID_PREF = "opened_file_id"
private const val OPENED_FILE_PATH_PREF = "opened_file_path"
private const val OPENED_FILE_HOST_PREF = "opened_file_host"
private const val BIOMETRIC_LOCK_PREF = "biometric_lock"
private const val SEARCH_RESULT_LIMIT_PREF = "search_result_limit"
private const val UPLOAD_IN_MODIFIED_ORDER_PREF = "upload_in_modified_order"
private const val CONFIRM_DOWNLOAD_TAP_PREF = "confirm_download_tap"
private const val ASKED_NOTIFICATION_PERMISSION_PREF = "asked_notification_permission"
private const val FOLDERS_FIRST_PREF = "folders_first"
private const val BAR_LABELS_PREF = "bar_labels"
private const val COMPACT_ROWS_PREF = "compact_rows"
private const val GRID_VIEW_PREF = "grid_view"
private const val RELATIVE_DATES_PREF = "relative_dates"
private const val SWIPE_FILES_PREF = "swipe_between_files"
private const val START_SCREEN_PREF = "start_screen"
private const val CONFIRM_EXIT_PREF = "confirm_exit"
private const val AUTOPLAY_PREF = "autoplay"
private const val RESUME_VIDEOS_PREF = "resume_videos"
private const val SEEK_STEP_PREF = "seek_step_seconds"
private const val DEFAULT_SPEED_PREF = "default_speed"
private const val PLAYER_GESTURES_PREF = "player_gestures"
private const val BACKGROUND_AUDIO_PREF = "background_audio"
private const val TINT_PLAYER_PREF = "tint_player"
private const val COMPACT_PLAYER_PREF = "compact_player"
private const val UNDO_CLOSE_SONG_PREF = "undo_close_song"
private const val VIBRATION_PREF = "vibration"
private const val VIBRATION_STRENGTH_PREF = "vibration_strength"
private const val VIBRATE_PLAYER_PREF = "vibrate_player"
private const val VIBRATE_NAV_SWIPE_PREF = "vibrate_nav_swipe"
private const val COPY_LINK_PREF = "copy_link_after_upload"
private const val OPEN_AFTER_DOWNLOAD_PREF = "open_after_download"
private const val DOWNLOAD_SUBFOLDER_PREF = "download_subfolder"
private const val DOCUMENTS_PROVIDER_PREF = "documents_provider"
private const val BLOCK_SCREENSHOTS_PREF = "block_screenshots"
private const val LOCK_TIMEOUT_PREF = "lock_timeout_seconds"

/** The choices for [AppSettings.searchResultLimit]; 0 is no limit. */
val SEARCH_RESULT_LIMITS = listOf(0, 200, 50, 20)

/**
 * The screens the app can open on (see [AppSettings.startScreen]), besides the one it was last on: Upload last, as only
 * a host with nothing else to upload to has it (see rememberHostTabs).
 */
val START_SCREENS = listOf(Screen.Files, Screen.Lists, Screen.Filesystem, Screen.Upload)

/** The choices for [AppSettings.seekStepSeconds]. */
val SEEK_STEPS = listOf(5, 10, 15, 30)

/** The choices for [AppSettings.lockTimeoutSeconds]; 0 asks every time the app comes back. */
val LOCK_TIMEOUTS = listOf(0, 30, 60, 300)

/** Pixeldrain keeps the paths of the files of a filesystem in this file, it is re-created when removed. */
const val SEARCH_INDEX_FILE_NAME = ".search_index.gz"

const val SEARCH_INDEX_DELETE_WARNING =
    "Pixeldrain uses this file for your file paths and recreates it anyway."

fun StorageNode.isSearchIndex(): Boolean = !isDirectory && name == SEARCH_INDEX_FILE_NAME

/**
 * How a file list is laid out: rows, a grid of big pictures, or Smart, either one picked by what's in it (the grid for
 * pictures and videos, see BrowserScreen's smartLayoutIsGrid). Whether rows are compact is chosen apart, whichever.
 */
enum class ListLayout { LIST, GRID, SMART }

/**
 * A folder's (or a screen's, or an open list's) own look, chosen in the View page of the ⋮ menu at the top: what it sets
 * is used there over the Look and feel's defaults ([AppSettings.gridView], [AppSettings.compactRows]); null keeps the
 * default. A Filesystem folder's look can be for its [subfolders] too, all the way down: the ones without a look of their
 * own have the nearest such folder's above them (see BrowserScreen's inheritedPath).
 */
data class ViewLook(val layout: ListLayout? = null, val compact: Boolean? = null, val subfolders: Boolean = false) {
    val isEmpty: Boolean get() = layout == null && compact == null

    /** As it's kept in the preferences: the layout's name (or -), then 1, 0 or - for compact, then r if for [subfolders]. */
    fun encode(): String = "${layout?.name ?: "-"};${compact?.let { if (it) "1" else "0" } ?: "-"}" + if (subfolders) ";r" else ""

    companion object {
        fun decode(text: String): ViewLook {
            val (layout, compact, subfolders) = (text.split(';') + listOf("-", "-", "-")).take(3)
            return ViewLook(
                layout = ListLayout.entries.firstOrNull { it.name == layout },
                compact = when (compact) { "1" -> true; "0" -> false; else -> null },
                subfolders = subfolders == "r"
            )
        }
    }
}

/** The list open on the Lists screen when the app was closed, with the host it belongs to. */
data class SavedOpenedList(val hostId: String, val list: FileList)

/** The file open in the details when the app was closed, with the host it belongs to. Only files with an id are kept. */
data class SavedOpenedFile(val hostId: String, val id: String, val path: String)

class AppSettings(context: Context) {

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val hideSearchIndexPref = booleanSetting(HIDE_SEARCH_INDEX_PREF, true)
    /** Hides the search index file from the filesystem, on by default. */
    val hideSearchIndex: StateFlow<Boolean> = hideSearchIndexPref.flow

    fun setHideSearchIndex(hide: Boolean) = hideSearchIndexPref.set(hide)

    private val reduceAnimationsPref = booleanSetting(REDUCE_ANIMATIONS_PREF, false)
    /** The user's own choice to reduce animations, see [systemAnimationsDisabled] for the system's. */
    val reduceAnimations: StateFlow<Boolean> = reduceAnimationsPref.flow

    fun setReduceAnimations(reduce: Boolean) = reduceAnimationsPref.set(reduce)

    private val blurredBackdropPref = booleanSetting(BLURRED_BACKDROP_PREF, true)
    /** Shows the thumbnail of a file, blurred, behind its preview. On by default. */
    val blurredBackdrop: StateFlow<Boolean> = blurredBackdropPref.flow

    fun setBlurredBackdrop(enabled: Boolean) = blurredBackdropPref.set(enabled)

    private val _theme = MutableStateFlow(
        ThemeSettings(
            mode = enumPref(THEME_MODE_PREF, ThemeMode.SYSTEM),
            wallpaperColors = prefs.getBoolean(WALLPAPER_COLORS_PREF, true),
            color = prefs.getInt(THEME_COLOR_PREF, DEFAULT_THEME_COLOR),
            blackBackground = prefs.getBoolean(BLACK_BACKGROUND_PREF, false)
        )
    )
    /** How the app is coloured: light or dark, from the wallpaper or a chosen colour, black or not (Look and feel). */
    val theme: StateFlow<ThemeSettings> = _theme.asStateFlow()

    fun setThemeMode(mode: ThemeMode) {
        prefs.edit { putString(THEME_MODE_PREF, mode.name) }
        _theme.value = _theme.value.copy(mode = mode)
    }

    fun setWallpaperColors(enabled: Boolean) {
        prefs.edit { putBoolean(WALLPAPER_COLORS_PREF, enabled) }
        _theme.value = _theme.value.copy(wallpaperColors = enabled)
    }

    fun setThemeColor(color: Int) {
        prefs.edit { putInt(THEME_COLOR_PREF, color) }
        _theme.value = _theme.value.copy(color = color)
    }

    fun setBlackBackground(enabled: Boolean) {
        prefs.edit { putBoolean(BLACK_BACKGROUND_PREF, enabled) }
        _theme.value = _theme.value.copy(blackBackground = enabled)
    }

    private val swipeBetweenTabsPref = booleanSetting(SWIPE_TABS_PREF, false)
    /** A swipe sideways across a tab goes to the tab beside it. Off by default. */
    val swipeBetweenTabs: StateFlow<Boolean> = swipeBetweenTabsPref.flow

    fun setSwipeBetweenTabs(enabled: Boolean) = swipeBetweenTabsPref.set(enabled)

    private val uploadInModifiedOrderPref = booleanSetting(UPLOAD_IN_MODIFIED_ORDER_PREF, false)
    /**
     * Uploads several files in the order they were last changed on the device, oldest first, one after another, so they
     * reach the host (and get their upload dates) in that order. Off by default: then several go at once, which is
     * faster.
     */
    val uploadInModifiedOrder: StateFlow<Boolean> = uploadInModifiedOrderPref.flow

    fun setUploadInModifiedOrder(enabled: Boolean) = uploadInModifiedOrderPref.set(enabled)

    private val confirmDownloadTapPref = booleanSetting(CONFIRM_DOWNLOAD_TAP_PREF, true)
    /** The Download button in a file's details asks to be tapped again before it starts; off, one tap starts it. */
    val confirmDownloadTap: StateFlow<Boolean> = confirmDownloadTapPref.flow

    fun setConfirmDownloadTap(enabled: Boolean) = confirmDownloadTapPref.set(enabled)

    private val loopVideosPref = booleanSetting(LOOP_VIDEOS_PREF, false)
    /** Whether videos start over when they end. One choice for all videos, set with the loop button of the player. */
    val loopVideos: StateFlow<Boolean> = loopVideosPref.flow

    fun setLoopVideos(loop: Boolean) = loopVideosPref.set(loop)

    private val gestureNavigationPref = booleanSetting(GESTURE_NAVIGATION_PREF, false)
    /**
     * Swipes between the tabs, with only the thin tab strip at the bottom (see TabStrip), instead of the bottom bar (Look
     * and feel, Navigation). Off by default.
     */
    val gestureNavigation: StateFlow<Boolean> = gestureNavigationPref.flow

    fun setGestureNavigation(enabled: Boolean) = gestureNavigationPref.set(enabled)

    private val sortFieldPref = enumSetting(SORT_FIELD_PREF, SortableField.NAME)
    /** The field every sortable file list in the app is sorted by (Files, Filesystem, and an opened list's files share
     * one order, like a standing preference rather than something tied to one screen). Kept across restarts. */
    val sortField: StateFlow<SortableField> = sortFieldPref.flow

    private val sortAscendingPref = booleanSetting(SORT_ASCENDING_PREF, true)
    /** The direction of [sortField], kept across restarts. */
    val sortAscending: StateFlow<Boolean> = sortAscendingPref.flow

    fun setSortField(field: SortableField) = sortFieldPref.set(field)

    fun setSortAscending(ascending: Boolean) = sortAscendingPref.set(ascending)

    fun toggleSortDirection() = setSortAscending(!sortAscendingPref.value)

    /** Choosing the field already sorted on flips its direction; choosing another field starts it ascending. */
    fun changeSortOrder(field: SortableField) {
        if (field == sortFieldPref.value) toggleSortDirection() else { setSortField(field); setSortAscending(true) }
    }

    private val foldersFirstPref = booleanSetting(FOLDERS_FIRST_PREF, true)
    /** Folders come before files in the filesystem, whatever the list is sorted by. On by default. */
    val foldersFirst: StateFlow<Boolean> = foldersFirstPref.flow

    fun setFoldersFirst(enabled: Boolean) = foldersFirstPref.set(enabled)

    // ---- Look and feel ----

    private val barLabelsPref = booleanSetting(BAR_LABELS_PREF, true)
    /** Every tab of the bottom bar has its name under its icon; off, only the chosen one has. */
    val barLabels: StateFlow<Boolean> = barLabelsPref.flow

    fun setBarLabels(always: Boolean) = barLabelsPref.set(always)

    private val compactRowsPref = booleanSetting(COMPACT_ROWS_PREF, false)
    /**
     * The rows of the file lists are lower, with smaller pictures, so more of them fit. Off by default. Only the default:
     * a folder with its own look (see [viewLooks]) keeps that.
     */
    val compactRows: StateFlow<Boolean> = compactRowsPref.flow

    fun setCompactRows(enabled: Boolean) = compactRowsPref.set(enabled)

    private val gridViewPref = booleanSetting(GRID_VIEW_PREF, false)
    /** The file lists are a grid of big pictures instead of rows. Off by default. Only the default, as [compactRows]. */
    val gridView: StateFlow<Boolean> = gridViewPref.flow

    fun setGridView(enabled: Boolean) = gridViewPref.set(enabled)

    private val viewLooksState = MutableStateFlow(
        prefs.all.mapNotNull { (key, value) ->
            if (key.startsWith(VIEW_LOOK_PREFIX) && value is String) key.removePrefix(VIEW_LOOK_PREFIX) to ViewLook.decode(value) else null
        }.toMap()
    )
    /**
     * The folders' and screens' own looks (see [ViewLook]), by the key of what they're the look of: the host, the tab and
     * the folder or list (see BrowserScreen's viewKey).
     */
    val viewLooks: StateFlow<Map<String, ViewLook>> = viewLooksState.asStateFlow()

    /** Gives what [key] stands for its own [look]; null (or one that sets nothing) goes back to the defaults. */
    fun setViewLook(key: String, look: ViewLook?) {
        val kept = look?.takeUnless { it.isEmpty }
        prefs.edit { if (kept == null) remove(VIEW_LOOK_PREFIX + key) else putString(VIEW_LOOK_PREFIX + key, kept.encode()) }
        viewLooksState.value = if (kept == null) viewLooksState.value - key else viewLooksState.value + (key to kept)
    }

    private val relativeDatesPref = booleanSetting(RELATIVE_DATES_PREF, true)
    /** The file lists say how long ago a file changed ("3 days ago"); off, the date and time itself. */
    val relativeDates: StateFlow<Boolean> = relativeDatesPref.flow

    fun setRelativeDates(enabled: Boolean) = relativeDatesPref.set(enabled)

    // ---- Navigation ----

    private val swipeBetweenFilesPref = booleanSetting(SWIPE_FILES_PREF, false)
    /** A swipe sideways across a file's details goes to the file beside it in the list it was opened from. Off by default. */
    val swipeBetweenFiles: StateFlow<Boolean> = swipeBetweenFilesPref.flow

    fun setSwipeBetweenFiles(enabled: Boolean) = swipeBetweenFilesPref.set(enabled)

    private val startScreenPref = Pref(prefs.getString(START_SCREEN_PREF, null)?.let { name -> START_SCREENS.firstOrNull { it.name == name } }) {
        if (it == null) remove(START_SCREEN_PREF) else putString(START_SCREEN_PREF, it.name)
    }
    /** The screen the app opens on, null for the one it was last on (the default). One of [START_SCREENS]. */
    val startScreen: StateFlow<Screen?> = startScreenPref.flow

    fun setStartScreen(screen: Screen?) = startScreenPref.set(screen)

    private val confirmExitPref = booleanSetting(CONFIRM_EXIT_PREF, true)
    /** Back at the top of the app asks to be pressed again before it closes the app. On by default. */
    val confirmExit: StateFlow<Boolean> = confirmExitPref.flow

    fun setConfirmExit(enabled: Boolean) = confirmExitPref.set(enabled)

    // ---- Media ----

    private val autoplayPref = booleanSetting(AUTOPLAY_PREF, true)
    /** A video starts playing as soon as the player opens; off, it waits for play. On by default. */
    val autoplay: StateFlow<Boolean> = autoplayPref.flow

    fun setAutoplay(enabled: Boolean) = autoplayPref.set(enabled)

    private val resumeVideosPref = booleanSetting(RESUME_VIDEOS_PREF, true)
    /** A video opened again goes on from where it was left (see VideoPositions). On by default. */
    val resumeVideos: StateFlow<Boolean> = resumeVideosPref.flow

    fun setResumeVideos(enabled: Boolean) = resumeVideosPref.set(enabled)

    private val seekStepSecondsPref = intSetting(SEEK_STEP_PREF, 10)
    /** How far the skip buttons and a double tap go, in seconds: one of [SEEK_STEPS]. */
    val seekStepSeconds: StateFlow<Int> = seekStepSecondsPref.flow

    fun setSeekStepSeconds(seconds: Int) = seekStepSecondsPref.set(seconds)

    private val defaultSpeedPref = Pref(prefs.getFloat(DEFAULT_SPEED_PREF, 1f)) { putFloat(DEFAULT_SPEED_PREF, it) }
    /** The speed videos and songs start at, 1 for their own. */
    val defaultSpeed: StateFlow<Float> = defaultSpeedPref.flow

    fun setDefaultSpeed(speed: Float) = defaultSpeedPref.set(speed)

    private val playerGesturesPref = booleanSetting(PLAYER_GESTURES_PREF, false)
    /** In the video player, a swipe up or down on the left changes the brightness, on the right the volume. Off by default. */
    val playerGestures: StateFlow<Boolean> = playerGesturesPref.flow

    fun setPlayerGestures(enabled: Boolean) = playerGesturesPref.set(enabled)

    private val backgroundAudioPref = booleanSetting(BACKGROUND_AUDIO_PREF, false)
    /**
     * A song goes on playing after its details are left and the app is closed, with its controls in the notification and
     * on the lock screen (see PlaybackService). Off by default: it stops with its details, as it always did.
     */
    val backgroundAudio: StateFlow<Boolean> = backgroundAudioPref.flow

    fun setBackgroundAudio(enabled: Boolean) = backgroundAudioPref.set(enabled)

    private val tintPlayerPref = booleanSetting(TINT_PLAYER_PREF, false)
    /** The miniplayer of a background song takes the colour of its cover, fading into its own. Off by default. */
    val tintPlayer: StateFlow<Boolean> = tintPlayerPref.flow

    fun setTintPlayer(enabled: Boolean) = tintPlayerPref.set(enabled)

    private val compactPlayerPref = booleanSetting(COMPACT_PLAYER_PREF, false)
    /**
     * The song player opened from the miniplayer is a card about a third of the screen high, which the miniplayer grows
     * into, instead of the whole screen. Off by default.
     */
    val compactPlayer: StateFlow<Boolean> = compactPlayerPref.flow

    fun setCompactPlayer(enabled: Boolean) = compactPlayerPref.set(enabled)

    private val undoCloseSongPref = booleanSetting(UNDO_CLOSE_SONG_PREF, false)
    /** Swiping the miniplayer down offers an Undo on the snackbar, which puts the song and its queue back. Off by default. */
    val undoCloseSong: StateFlow<Boolean> = undoCloseSongPref.flow

    fun setUndoCloseSong(enabled: Boolean) = undoCloseSongPref.set(enabled)

    // ---- Feedback ----

    private val vibrationPref = booleanSetting(VIBRATION_PREF, true)
    /** The app's vibrations (see AppHaptics), where each of the ones below is on. On by default. */
    val vibration: StateFlow<Boolean> = vibrationPref.flow

    fun setVibration(enabled: Boolean) = vibrationPref.set(enabled)

    private val vibrationStrengthPref = enumSetting(VIBRATION_STRENGTH_PREF, VibrationStrength.LIGHT)
    /** How strong the vibrations are. Light by default. */
    val vibrationStrength: StateFlow<VibrationStrength> = vibrationStrengthPref.flow

    fun setVibrationStrength(strength: VibrationStrength) = vibrationStrengthPref.set(strength)

    private val vibratePlayerPref = booleanSetting(VIBRATE_PLAYER_PREF, true)
    /** The miniplayer's swipes vibrate when letting go would open, skip or close, and again when the finger goes back. */
    val vibratePlayer: StateFlow<Boolean> = vibratePlayerPref.flow

    fun setVibratePlayer(enabled: Boolean) = vibratePlayerPref.set(enabled)

    private val vibrateNavSwipePref = booleanSetting(VIBRATE_NAV_SWIPE_PREF, true)
    /** With swipe navigation, a swipe going to another tab vibrates lightly (see TabStrip). */
    val vibrateNavSwipe: StateFlow<Boolean> = vibrateNavSwipePref.flow

    fun setVibrateNavSwipe(enabled: Boolean) = vibrateNavSwipePref.set(enabled)

    // ---- Files and transfers ----

    private val copyLinkAfterUploadPref = booleanSetting(COPY_LINK_PREF, false)
    /** When an upload is done, the links of the uploaded files are copied (for hosts which have links). Off by default. */
    val copyLinkAfterUpload: StateFlow<Boolean> = copyLinkAfterUploadPref.flow

    fun setCopyLinkAfterUpload(enabled: Boolean) = copyLinkAfterUploadPref.set(enabled)

    private val openAfterDownloadPref = booleanSetting(OPEN_AFTER_DOWNLOAD_PREF, false)
    /** A file downloaded on its own (not one of several) is opened when it's done. Off by default. */
    val openAfterDownload: StateFlow<Boolean> = openAfterDownloadPref.flow

    fun setOpenAfterDownload(enabled: Boolean) = openAfterDownloadPref.set(enabled)

    private val downloadSubfolderPref = booleanSetting(DOWNLOAD_SUBFOLDER_PREF, false)
    /** Downloads go into a folder of their own, Download/Materialdrain, instead of Download itself. Off by default. */
    val downloadSubfolder: StateFlow<Boolean> = downloadSubfolderPref.flow

    fun setDownloadSubfolder(enabled: Boolean) = downloadSubfolderPref.set(enabled)

    private val documentsProviderPref = booleanSetting(DOCUMENTS_PROVIDER_PREF, false)
    /**
     * The hosts which have folders show in the system's file picker (see HostDocumentsProvider), so other apps can open
     * their files. Off by default, and never while the fingerprint lock is on: the picker doesn't ask for it.
     */
    val documentsProvider: StateFlow<Boolean> = documentsProviderPref.flow

    fun setDocumentsProvider(enabled: Boolean) {
        documentsProviderPref.set(enabled)
        HostDocumentsProvider.notifyRootsChanged(appContext)
    }

    // ---- Privacy and security ----

    private val blockScreenshotsPref = booleanSetting(BLOCK_SCREENSHOTS_PREF, false)
    /**
     * Screenshots and screen recordings of the app come out black, and the recent apps show it blank (FLAG_SECURE). That
     * keeps adb's screencap out too. Off by default.
     */
    val blockScreenshots: StateFlow<Boolean> = blockScreenshotsPref.flow

    fun setBlockScreenshots(enabled: Boolean) = blockScreenshotsPref.set(enabled)

    private val lockTimeoutSecondsPref = intSetting(LOCK_TIMEOUT_PREF, 30)
    /** How long the app may be away before the lock asks again, in seconds: one of [LOCK_TIMEOUTS], 0 for every time. */
    val lockTimeoutSeconds: StateFlow<Int> = lockTimeoutSecondsPref.flow

    fun setLockTimeoutSeconds(seconds: Int) = lockTimeoutSecondsPref.set(seconds)

    private val biometricLockPref = booleanSetting(BIOMETRIC_LOCK_PREF, false)
    /** Asks for the fingerprint, face or screen lock when the app is opened again, see AppLock. Off by default. */
    val biometricLock: StateFlow<Boolean> = biometricLockPref.flow

    fun setBiometricLock(enabled: Boolean) {
        biometricLockPref.set(enabled)
        // The file picker shows no hosts while the app is locked
        HostDocumentsProvider.notifyRootsChanged(appContext)
    }

    /** What the lock can ask for on this device: BIOMETRIC_SUCCESS, or why not (see BiometricManager.canAuthenticate). */
    fun biometricLockStatus(): Int = BiometricManager.from(appContext).canAuthenticate(LOCK_AUTHENTICATORS)

    /** Whether this device has a fingerprint, face or screen lock to ask for. */
    fun biometricLockAvailable(): Boolean = biometricLockStatus() == BiometricManager.BIOMETRIC_SUCCESS

    private val searchResultLimitPref = intSetting(SEARCH_RESULT_LIMIT_PREF, 0)
    /**
     * How many matches a search shows at most, 0 for all of them (the default). For slower devices: a search through a big
     * folder tree can match thousands of files, and only the cards on screen are drawn, but each match still costs a little.
     */
    val searchResultLimit: StateFlow<Int> = searchResultLimitPref.flow

    fun setSearchResultLimit(limit: Int) = searchResultLimitPref.set(limit)

    private val textWrapPref = booleanSetting(TEXT_WRAP_PREF, true)
    /** Whether long lines of a text preview wrap onto the next line, or scroll sideways. On by default. */
    val textWrap: StateFlow<Boolean> = textWrapPref.flow

    fun setTextWrap(wrap: Boolean) = textWrapPref.set(wrap)

    // ---- Data saver (see DataSaver, which works out when it applies) ----

    private val dataSaverPref = booleanSetting(DATA_SAVER_PREF, false)
    /** What the app would load by itself is only loaded when asked for. Off by default. */
    val dataSaver: StateFlow<Boolean> = dataSaverPref.flow

    fun setDataSaver(enabled: Boolean) = dataSaverPref.set(enabled)

    private val dataSaverMobileOnlyPref = booleanSetting(DATA_SAVER_MOBILE_ONLY_PREF, false)
    /** The data saver only applies on mobile data (a metered network), not on Wi-Fi. Off by default: it always applies. */
    val dataSaverMobileOnly: StateFlow<Boolean> = dataSaverMobileOnlyPref.flow

    fun setDataSaverMobileOnly(mobileOnly: Boolean) = dataSaverMobileOnlyPref.set(mobileOnly)

    private val holdThumbnailsPref = booleanSetting(HOLD_THUMBNAILS_PREF, true)
    /** With the data saver, the thumbnails in the lists wait to be asked for. */
    val holdThumbnails: StateFlow<Boolean> = holdThumbnailsPref.flow

    fun setHoldThumbnails(hold: Boolean) = holdThumbnailsPref.set(hold)

    private val holdPreviewsPref = booleanSetting(HOLD_PREVIEWS_PREF, true)
    /** With the data saver, a file's preview (picture, video, song, text, PDF, archive) waits to be asked for. */
    val holdPreviews: StateFlow<Boolean> = holdPreviewsPref.flow

    fun setHoldPreviews(hold: Boolean) = holdPreviewsPref.set(hold)

    private val holdConfigUpdatesPref = booleanSetting(HOLD_CONFIG_UPDATES_PREF, true)
    /** With the data saver, the configs aren't checked for updates when the app starts, only from the hosts' page. */
    val holdConfigUpdates: StateFlow<Boolean> = holdConfigUpdatesPref.flow

    fun setHoldConfigUpdates(hold: Boolean) = holdConfigUpdatesPref.set(hold)

    private val holdHostChecksPref = booleanSetting(HOLD_HOST_CHECKS_PREF, true)
    /** With the data saver, the hosts aren't checked in the background, only from the host switcher's refresh. */
    val holdHostChecks: StateFlow<Boolean> = holdHostChecksPref.flow

    fun setHoldHostChecks(hold: Boolean) = holdHostChecksPref.set(hold)

    /**
     * The folder last open on a Filesystem tab, null when none is saved. [tabKey] is the host's id for its usual tab, and
     * the host's and the tab's for one of several (see FilesystemViewModel.bind), so each tab opens where it was left.
     */
    fun filesystemPath(tabKey: String): String? = prefs.getString(FILESYSTEM_PATH_PREFIX + tabKey, null)

    fun setFilesystemPath(tabKey: String, path: String) = prefs.edit { putString(FILESYSTEM_PATH_PREFIX + tabKey, path) }

    /** The list open on the Lists screen, kept across restarts. Set to null when the list is closed. */
    var openedList: SavedOpenedList?
        get() {
            val id = prefs.getString(OPENED_LIST_ID_PREF, null) ?: return null
            val host = prefs.getString(OPENED_LIST_HOST_PREF, null) ?: return null
            val list = FileList(
                id = id,
                title = prefs.getString(OPENED_LIST_TITLE_PREF, null).orEmpty(),
                fileCount = prefs.getInt(OPENED_LIST_COUNT_PREF, 0),
                canEdit = prefs.getBoolean(OPENED_LIST_CAN_EDIT_PREF, false)
            )
            return SavedOpenedList(host, list)
        }
        set(value) = prefs.edit {
            if (value == null) {
                remove(OPENED_LIST_ID_PREF)
                remove(OPENED_LIST_HOST_PREF)
            } else {
                putString(OPENED_LIST_ID_PREF, value.list.id)
                putString(OPENED_LIST_HOST_PREF, value.hostId)
                putString(OPENED_LIST_TITLE_PREF, value.list.title)
                putInt(OPENED_LIST_COUNT_PREF, value.list.fileCount)
                putBoolean(OPENED_LIST_CAN_EDIT_PREF, value.list.canEdit)
            }
        }

    /** The file open in the details, kept across restarts. Set to null when the details are closed. */
    var openedFile: SavedOpenedFile?
        get() {
            val id = prefs.getString(OPENED_FILE_ID_PREF, null) ?: return null
            val host = prefs.getString(OPENED_FILE_HOST_PREF, null) ?: return null
            return SavedOpenedFile(host, id, prefs.getString(OPENED_FILE_PATH_PREF, null).orEmpty())
        }
        set(value) = prefs.edit {
            if (value == null) {
                remove(OPENED_FILE_ID_PREF)
                remove(OPENED_FILE_HOST_PREF)
                remove(OPENED_FILE_PATH_PREF)
            } else {
                putString(OPENED_FILE_ID_PREF, value.id)
                putString(OPENED_FILE_HOST_PREF, value.hostId)
                putString(OPENED_FILE_PATH_PREF, value.path)
            }
        }

    /** The screen the app was last on, so it opens there again. Kept across restarts. */
    var lastScreen: Screen
        get() = enumPref(LAST_SCREEN_PREF, Screen.Files)
        set(value) = prefs.edit { putString(LAST_SCREEN_PREF, value.name) }

    /** The tab the app was last on (see ScreenConfig.tabId), for a host with several tabs of one kind; null for none yet. */
    var lastTabId: String?
        get() = prefs.getString(LAST_TAB_PREF, null)
        set(value) = prefs.edit { if (value == null) remove(LAST_TAB_PREF) else putString(LAST_TAB_PREF, value) }

    /** Whether the app has asked for the notification permission (Android 13+) already: it asks only once. */
    var askedNotificationPermission: Boolean
        get() = prefs.getBoolean(ASKED_NOTIFICATION_PERMISSION_PREF, false)
        set(value) = prefs.edit { putBoolean(ASKED_NOTIFICATION_PERMISSION_PREF, value) }

    private inline fun <reified T : Enum<T>> enumPref(key: String, default: T): T =
        prefs.getString(key, null)?.let { name -> enumValues<T>().firstOrNull { it.name == name } } ?: default

    /** A setting kept in [prefs], with the flow the app follows it by; [write] puts a new value into the preferences. */
    private inner class Pref<T>(initial: T, private val write: SharedPreferences.Editor.(T) -> Unit) {
        private val state = MutableStateFlow(initial)
        val flow: StateFlow<T> = state.asStateFlow()
        val value: T get() = state.value

        fun set(value: T) {
            prefs.edit { write(value) }
            state.value = value
        }
    }

    private fun booleanSetting(key: String, default: Boolean) = Pref(prefs.getBoolean(key, default)) { putBoolean(key, it) }

    private fun intSetting(key: String, default: Int) = Pref(prefs.getInt(key, default)) { putInt(key, it) }

    private inline fun <reified T : Enum<T>> enumSetting(key: String, default: T) = Pref(enumPref(key, default)) { putString(key, it.name) }

    /** True when animations are turned off in the Android settings ("Remove animations" / animator scale 0). */
    fun systemAnimationsDisabled(): Boolean =
        Settings.Global.getFloat(appContext.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
}
