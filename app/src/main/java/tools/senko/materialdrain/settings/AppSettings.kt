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
private const val LAST_SCREEN_PREF = "last_screen"
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

    private val _textWrap = MutableStateFlow(prefs.getBoolean(TEXT_WRAP_PREF, true))
    /** Whether long lines of a text preview wrap onto the next line, or scroll sideways. On by default. */
    val textWrap: StateFlow<Boolean> = _textWrap.asStateFlow()

    fun setTextWrap(wrap: Boolean) {
        prefs.edit { putBoolean(TEXT_WRAP_PREF, wrap) }
        _textWrap.value = wrap
    }

    /** The folder last open on the Filesystem screen of [hostId], null when none is saved. */
    fun filesystemPath(hostId: String): String? = prefs.getString(FILESYSTEM_PATH_PREFIX + hostId, null)

    fun setFilesystemPath(hostId: String, path: String) = prefs.edit { putString(FILESYSTEM_PATH_PREFIX + hostId, path) }

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

    private inline fun <reified T : Enum<T>> enumPref(key: String, default: T): T =
        prefs.getString(key, null)?.let { name -> enumValues<T>().firstOrNull { it.name == name } } ?: default

    /** True when animations are turned off in the Android settings ("Remove animations" / animator scale 0). */
    fun systemAnimationsDisabled(): Boolean =
        Settings.Global.getFloat(appContext.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
}
