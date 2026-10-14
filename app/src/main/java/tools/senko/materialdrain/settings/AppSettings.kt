package tools.senko.materialdrain.settings

import android.content.Context
import androidx.biometric.BiometricManager
import tools.senko.materialdrain.auth.LOCK_AUTHENTICATORS
import android.provider.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import androidx.core.content.edit
import tools.senko.materialdrain.Screen
import tools.senko.materialdrain.files.SortableField
import tools.senko.materialdrain.navmenu.NavFabPosition
import tools.senko.materialdrain.navmenu.NavMenuPreview
import tools.senko.materialdrain.provider.api.FileList
import tools.senko.materialdrain.provider.api.StorageNode

private const val PREFS_NAME = "pixeldrain_prefs"
private const val HIDE_SEARCH_INDEX_PREF = "hide_search_index"
private const val REDUCE_ANIMATIONS_PREF = "reduce_animations"
private const val BLURRED_BACKDROP_PREF = "blurred_backdrop"
private const val LOOP_VIDEOS_PREF = "loop_videos"
private const val NAV_PROTOTYPE_PREF = "dev_nav_prototype"
private const val NAV_MENU_PREVIEW_PREF = "dev_nav_menu_preview"
private const val NAV_FAB_POSITION_PREF = "nav_fab_position"
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
private const val ASKED_NOTIFICATION_PERMISSION_PREF = "asked_notification_permission"

/** The choices for [AppSettings.searchResultLimit]; 0 is no limit. */
val SEARCH_RESULT_LIMITS = listOf(0, 200, 50, 20)

/** Pixeldrain keeps the paths of the files of a filesystem in this file, it is re-created when removed. */
const val SEARCH_INDEX_FILE_NAME = ".search_index.gz"

const val SEARCH_INDEX_DELETE_WARNING =
    "$SEARCH_INDEX_FILE_NAME is used by Pixeldrain itself to store the paths of your files. " +
        "Almost nobody has a reason to delete it, and Pixeldrain will re-create it after a while anyway."

fun StorageNode.isSearchIndex(): Boolean = !isDirectory && name == SEARCH_INDEX_FILE_NAME

/** The list open on the Lists screen when the app was closed, with the host it belongs to. */
data class SavedOpenedList(val hostId: String, val list: FileList)

/** The file open in the details when the app was closed, with the host it belongs to. Only files with an id are kept. */
data class SavedOpenedFile(val hostId: String, val id: String, val path: String)

class AppSettings(context: Context) {

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _hideSearchIndex = MutableStateFlow(prefs.getBoolean(HIDE_SEARCH_INDEX_PREF, true))
    /** Hides the search index file from the filesystem, on by default. */
    val hideSearchIndex: StateFlow<Boolean> = _hideSearchIndex.asStateFlow()

    fun setHideSearchIndex(hide: Boolean) {
        prefs.edit { putBoolean(HIDE_SEARCH_INDEX_PREF, hide) }
        _hideSearchIndex.value = hide
    }

    private val _reduceAnimations = MutableStateFlow(prefs.getBoolean(REDUCE_ANIMATIONS_PREF, false))
    /** The user's own choice to reduce animations, see [systemAnimationsDisabled] for the system's. */
    val reduceAnimations: StateFlow<Boolean> = _reduceAnimations.asStateFlow()

    fun setReduceAnimations(reduce: Boolean) {
        prefs.edit { putBoolean(REDUCE_ANIMATIONS_PREF, reduce) }
        _reduceAnimations.value = reduce
    }

    private val _blurredBackdrop = MutableStateFlow(prefs.getBoolean(BLURRED_BACKDROP_PREF, true))
    /** Shows the thumbnail of a file, blurred, behind its preview. On by default. */
    val blurredBackdrop: StateFlow<Boolean> = _blurredBackdrop.asStateFlow()

    fun setBlurredBackdrop(enabled: Boolean) {
        prefs.edit { putBoolean(BLURRED_BACKDROP_PREF, enabled) }
        _blurredBackdrop.value = enabled
    }

    private val _uploadInModifiedOrder = MutableStateFlow(prefs.getBoolean(UPLOAD_IN_MODIFIED_ORDER_PREF, false))
    /**
     * Uploads several files in the order they were last changed on the device, oldest first, one after another, so they
     * reach the host (and get their upload dates) in that order. Off by default: then several go at once, which is
     * faster.
     */
    val uploadInModifiedOrder: StateFlow<Boolean> = _uploadInModifiedOrder.asStateFlow()

    fun setUploadInModifiedOrder(enabled: Boolean) {
        prefs.edit { putBoolean(UPLOAD_IN_MODIFIED_ORDER_PREF, enabled) }
        _uploadInModifiedOrder.value = enabled
    }

    private val _loopVideos = MutableStateFlow(prefs.getBoolean(LOOP_VIDEOS_PREF, false))
    /** Whether videos start over when they end. One choice for all videos, set with the loop button of the player. */
    val loopVideos: StateFlow<Boolean> = _loopVideos.asStateFlow()

    fun setLoopVideos(loop: Boolean) {
        prefs.edit { putBoolean(LOOP_VIDEOS_PREF, loop) }
        _loopVideos.value = loop
    }

    private val _navPrototype = MutableStateFlow(prefs.getBoolean(NAV_PROTOTYPE_PREF, false))
    /** Developer setting: the FAB navigation prototype replaces the drawer. Off by default. */
    val navPrototype: StateFlow<Boolean> = _navPrototype.asStateFlow()

    fun setNavPrototype(enabled: Boolean) {
        prefs.edit { putBoolean(NAV_PROTOTYPE_PREF, enabled) }
        _navPrototype.value = enabled
    }

    private val _navMenuPreview = MutableStateFlow(enumPref(NAV_MENU_PREVIEW_PREF, NavMenuPreview.PIXELDRAIN))
    /** Developer setting: which provider's menu the FAB navigation prototype shows. */
    val navMenuPreview: StateFlow<NavMenuPreview> = _navMenuPreview.asStateFlow()

    fun setNavMenuPreview(preview: NavMenuPreview) {
        prefs.edit { putString(NAV_MENU_PREVIEW_PREF, preview.name) }
        _navMenuPreview.value = preview
    }

    private val _navFabPosition = MutableStateFlow(enumPref(NAV_FAB_POSITION_PREF, NavFabPosition.END))
    /** Where the navigation FAB sits, changed by swiping the FAB sideways. */
    val navFabPosition: StateFlow<NavFabPosition> = _navFabPosition.asStateFlow()

    fun setNavFabPosition(position: NavFabPosition) {
        prefs.edit { putString(NAV_FAB_POSITION_PREF, position.name) }
        _navFabPosition.value = position
    }

    private val _sortField = MutableStateFlow(enumPref(SORT_FIELD_PREF, SortableField.NAME))
    /** The field every sortable file list in the app is sorted by (Files, Filesystem, and an opened list's files share
     * one order, like a standing preference rather than something tied to one screen). Kept across restarts. */
    val sortField: StateFlow<SortableField> = _sortField.asStateFlow()

    private val _sortAscending = MutableStateFlow(prefs.getBoolean(SORT_ASCENDING_PREF, true))
    /** The direction of [sortField], kept across restarts. */
    val sortAscending: StateFlow<Boolean> = _sortAscending.asStateFlow()

    fun setSortField(field: SortableField) {
        prefs.edit { putString(SORT_FIELD_PREF, field.name) }
        _sortField.value = field
    }

    fun setSortAscending(ascending: Boolean) {
        prefs.edit { putBoolean(SORT_ASCENDING_PREF, ascending) }
        _sortAscending.value = ascending
    }

    fun toggleSortDirection() = setSortAscending(!_sortAscending.value)

    /** Choosing the field already sorted on flips its direction; choosing another field starts it ascending. */
    fun changeSortOrder(field: SortableField) {
        if (field == _sortField.value) toggleSortDirection() else { setSortField(field); setSortAscending(true) }
    }

    private val _biometricLock = MutableStateFlow(prefs.getBoolean(BIOMETRIC_LOCK_PREF, false))
    /** Asks for the fingerprint, face or screen lock when the app is opened again, see AppLock. Off by default. */
    val biometricLock: StateFlow<Boolean> = _biometricLock.asStateFlow()

    fun setBiometricLock(enabled: Boolean) {
        prefs.edit { putBoolean(BIOMETRIC_LOCK_PREF, enabled) }
        _biometricLock.value = enabled
    }

    /** Whether this device has a fingerprint, face or screen lock to ask for. */
    fun biometricLockAvailable(): Boolean =
        BiometricManager.from(appContext).canAuthenticate(LOCK_AUTHENTICATORS) == BiometricManager.BIOMETRIC_SUCCESS

    private val _searchResultLimit = MutableStateFlow(prefs.getInt(SEARCH_RESULT_LIMIT_PREF, 0))
    /**
     * How many matches a search shows at most, 0 for all of them (the default). For slower devices: a search through a big
     * folder tree can match thousands of files, and only the cards on screen are drawn, but each match still costs a little.
     */
    val searchResultLimit: StateFlow<Int> = _searchResultLimit.asStateFlow()

    fun setSearchResultLimit(limit: Int) {
        prefs.edit { putInt(SEARCH_RESULT_LIMIT_PREF, limit) }
        _searchResultLimit.value = limit
    }

    private val _textWrap = MutableStateFlow(prefs.getBoolean(TEXT_WRAP_PREF, true))
    /** Whether long lines of a text preview wrap onto the next line, or scroll sideways. On by default. */
    val textWrap: StateFlow<Boolean> = _textWrap.asStateFlow()

    fun setTextWrap(wrap: Boolean) {
        prefs.edit { putBoolean(TEXT_WRAP_PREF, wrap) }
        _textWrap.value = wrap
    }

    // ---- Data saver (see DataSaver, which works out when it applies) ----

    private val _dataSaver = MutableStateFlow(prefs.getBoolean(DATA_SAVER_PREF, false))
    /** What the app would load by itself is only loaded when asked for. Off by default. */
    val dataSaver: StateFlow<Boolean> = _dataSaver.asStateFlow()

    fun setDataSaver(enabled: Boolean) {
        prefs.edit { putBoolean(DATA_SAVER_PREF, enabled) }
        _dataSaver.value = enabled
    }

    private val _dataSaverMobileOnly = MutableStateFlow(prefs.getBoolean(DATA_SAVER_MOBILE_ONLY_PREF, false))
    /** The data saver only applies on mobile data (a metered network), not on Wi-Fi. Off by default: it always applies. */
    val dataSaverMobileOnly: StateFlow<Boolean> = _dataSaverMobileOnly.asStateFlow()

    fun setDataSaverMobileOnly(mobileOnly: Boolean) {
        prefs.edit { putBoolean(DATA_SAVER_MOBILE_ONLY_PREF, mobileOnly) }
        _dataSaverMobileOnly.value = mobileOnly
    }

    private val _holdThumbnails = MutableStateFlow(prefs.getBoolean(HOLD_THUMBNAILS_PREF, true))
    /** With the data saver, the thumbnails in the lists wait to be asked for. */
    val holdThumbnails: StateFlow<Boolean> = _holdThumbnails.asStateFlow()

    fun setHoldThumbnails(hold: Boolean) {
        prefs.edit { putBoolean(HOLD_THUMBNAILS_PREF, hold) }
        _holdThumbnails.value = hold
    }

    private val _holdPreviews = MutableStateFlow(prefs.getBoolean(HOLD_PREVIEWS_PREF, true))
    /** With the data saver, a file's preview (picture, video, song, text, PDF, archive) waits to be asked for. */
    val holdPreviews: StateFlow<Boolean> = _holdPreviews.asStateFlow()

    fun setHoldPreviews(hold: Boolean) {
        prefs.edit { putBoolean(HOLD_PREVIEWS_PREF, hold) }
        _holdPreviews.value = hold
    }

    private val _holdConfigUpdates = MutableStateFlow(prefs.getBoolean(HOLD_CONFIG_UPDATES_PREF, true))
    /** With the data saver, the configs aren't checked for updates when the app starts, only from the hosts' page. */
    val holdConfigUpdates: StateFlow<Boolean> = _holdConfigUpdates.asStateFlow()

    fun setHoldConfigUpdates(hold: Boolean) {
        prefs.edit { putBoolean(HOLD_CONFIG_UPDATES_PREF, hold) }
        _holdConfigUpdates.value = hold
    }

    private val _holdHostChecks = MutableStateFlow(prefs.getBoolean(HOLD_HOST_CHECKS_PREF, true))
    /** With the data saver, the hosts aren't checked in the background, only from the host switcher's refresh. */
    val holdHostChecks: StateFlow<Boolean> = _holdHostChecks.asStateFlow()

    fun setHoldHostChecks(hold: Boolean) {
        prefs.edit { putBoolean(HOLD_HOST_CHECKS_PREF, hold) }
        _holdHostChecks.value = hold
    }

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
        get() = enumPref(LAST_SCREEN_PREF, Screen.Upload)
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

    /** True when animations are turned off in the Android settings ("Remove animations" / animator scale 0). */
    fun systemAnimationsDisabled(): Boolean =
        Settings.Global.getFloat(appContext.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
}
