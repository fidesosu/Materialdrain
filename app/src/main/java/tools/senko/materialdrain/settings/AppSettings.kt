package tools.senko.materialdrain.settings

import android.content.Context
import android.provider.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import androidx.core.content.edit
import tools.senko.materialdrain.api.FilesystemEntry

private const val PREFS_NAME = "pixeldrain_prefs"
private const val HIDE_SEARCH_INDEX_PREF = "hide_search_index"
private const val REDUCE_ANIMATIONS_PREF = "reduce_animations"
private const val BLURRED_BACKDROP_PREF = "blurred_backdrop"
private const val LOOP_VIDEOS_PREF = "loop_videos"

/** Pixeldrain keeps the paths of the files of a filesystem in this file, it is re-created when removed. */
const val SEARCH_INDEX_FILE_NAME = ".search_index.gz"

const val SEARCH_INDEX_DELETE_WARNING =
    "$SEARCH_INDEX_FILE_NAME is used by Pixeldrain itself to store the paths of your files. " +
        "Almost nobody has a reason to delete it, and Pixeldrain will re-create it after a while anyway."

fun FilesystemEntry.isSearchIndex(): Boolean = type == "file" && name == SEARCH_INDEX_FILE_NAME

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

    /** True when animations are turned off in the Android settings ("Remove animations" / animator scale 0). */
    fun systemAnimationsDisabled(): Boolean =
        Settings.Global.getFloat(appContext.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
}
