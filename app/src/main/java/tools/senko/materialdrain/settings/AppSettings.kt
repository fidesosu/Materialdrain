package tools.senko.materialdrain.settings

import android.content.Context
import androidx.biometric.BiometricManager
import tools.senko.materialdrain.auth.LOCK_AUTHENTICATORS
import android.provider.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import androidx.core.content.edit
import tools.senko.materialdrain.files.SortableField
import tools.senko.materialdrain.navmenu.NavFabPosition
import tools.senko.materialdrain.navmenu.NavMenuPreview
import tools.senko.materialdrain.provider.api.StorageNode

private const val PREFS_NAME = "pixeldrain_prefs"
private const val HIDE_SEARCH_INDEX_PREF = "hide_search_index"
private const val REDUCE_ANIMATIONS_PREF = "reduce_animations"
private const val BLURRED_BACKDROP_PREF = "blurred_backdrop"
private const val LOOP_VIDEOS_PREF = "loop_videos"
private const val NAV_PROTOTYPE_PREF = "dev_nav_prototype"
private const val NAV_MENU_PREVIEW_PREF = "dev_nav_menu_preview"
private const val NAV_FAB_POSITION_PREF = "nav_fab_position"
private const val FILES_SORT_FIELD_PREF = "files_sort_field"
private const val FILES_SORT_ASCENDING_PREF = "files_sort_ascending"
private const val BIOMETRIC_LOCK_PREF = "biometric_lock"

/** Pixeldrain keeps the paths of the files of a filesystem in this file, it is re-created when removed. */
const val SEARCH_INDEX_FILE_NAME = ".search_index.gz"

const val SEARCH_INDEX_DELETE_WARNING =
    "$SEARCH_INDEX_FILE_NAME is used by Pixeldrain itself to store the paths of your files. " +
        "Almost nobody has a reason to delete it, and Pixeldrain will re-create it after a while anyway."

fun StorageNode.isSearchIndex(): Boolean = !isDirectory && name == SEARCH_INDEX_FILE_NAME

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

    /** The field the Files screen is sorted by, kept across restarts. */
    var filesSortField: SortableField
        get() = enumPref(FILES_SORT_FIELD_PREF, SortableField.NAME)
        set(value) = prefs.edit { putString(FILES_SORT_FIELD_PREF, value.name) }

    /** The direction of the sorting of the Files screen, kept across restarts. */
    var filesSortAscending: Boolean
        get() = prefs.getBoolean(FILES_SORT_ASCENDING_PREF, true)
        set(value) = prefs.edit { putBoolean(FILES_SORT_ASCENDING_PREF, value) }

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

    private inline fun <reified T : Enum<T>> enumPref(key: String, default: T): T =
        prefs.getString(key, null)?.let { name -> enumValues<T>().firstOrNull { it.name == name } } ?: default

    /** True when animations are turned off in the Android settings ("Remove animations" / animator scale 0). */
    fun systemAnimationsDisabled(): Boolean =
        Settings.Global.getFloat(appContext.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
}
