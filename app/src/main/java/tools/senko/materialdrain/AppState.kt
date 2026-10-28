package tools.senko.materialdrain

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.offset
import androidx.lifecycle.viewmodel.compose.viewModel
import tools.senko.materialdrain.filesystem.FilesystemViewModel
import tools.senko.materialdrain.files.key
import tools.senko.materialdrain.ui.LocalPlayerSettings
import tools.senko.materialdrain.ui.PlayerSettings
import tools.senko.materialdrain.ui.LocalListLook
import tools.senko.materialdrain.ui.ListLook
import tools.senko.materialdrain.settings.AppSettings

/** The Media settings, as the players read them (see LocalPlayerSettings). */
@Composable
internal fun rememberPlayerSettings(settings: AppSettings): PlayerSettings {
    val autoplay by settings.autoplay.collectAsState()
    val resume by settings.resumeVideos.collectAsState()
    val step by settings.seekStepSeconds.collectAsState()
    val speed by settings.defaultSpeed.collectAsState()
    val gestures by settings.playerGestures.collectAsState()
    val background by settings.backgroundAudio.collectAsState()
    val tint by settings.tintPlayer.collectAsState()
    val compact by settings.compactPlayer.collectAsState()
    val undoClose by settings.undoCloseSong.collectAsState()
    return remember(autoplay, resume, step, speed, gestures, background, tint, compact, undoClose) {
        PlayerSettings(autoplay, resume, step * 1000L, speed, gestures, background, tint, compact, undoClose)
    }
}

/** The Look and feel settings of the file lists (see LocalListLook). */
@Composable
internal fun rememberListLook(settings: AppSettings): ListLook {
    val compact by settings.compactRows.collectAsState()
    val grid by settings.gridView.collectAsState()
    val relative by settings.relativeDates.collectAsState()
    return remember(compact, grid, relative) { ListLook(compact, grid, relative) }
}

/** What the main content shows: a [screen], and for a tab of the host [hostId], which of its tabs. */
internal data class Shown(val screen: Screen, val tab: Tab?, val hostId: String) {
    /** The page, for what it tells the app around it (see screenActions). */
    val key: String get() = "$hostId:$screen:${tab?.id}"
}

/**
 * The browser of the Filesystem [tab] on the host [hostId]: one per tab (and root), so each keeps its own folder and
 * listing, and like the other ViewModels which own transfers it outlives the activity. Without a tab, the host's usual one.
 */
@Composable
internal fun filesystemViewModelFor(
    hostId: String,
    tab: Tab?,
    owner: androidx.lifecycle.ViewModelStoreOwner,
    factory: ViewModelFactory
): FilesystemViewModel {
    val tabId = tab?.id ?: FilesystemViewModel.DEFAULT_TAB_ID
    val root = tab?.config?.rootFolder.orEmpty()
    val browser: FilesystemViewModel = viewModel(key = "filesystem:$hostId:$tabId:$root", viewModelStoreOwner = owner, factory = factory)
    browser.bind(hostId, tabId, root)
    return browser
}

/** The scroll position of each Filesystem tab, by host and tab, kept across visits and when the activity is made again. */
internal class TabScrollStates(initial: Map<String, Pair<Int, Int>> = emptyMap()) {
    private val states = HashMap<String, androidx.compose.foundation.lazy.LazyListState>().apply {
        initial.forEach { (key, position) -> put(key, androidx.compose.foundation.lazy.LazyListState(position.first, position.second)) }
    }

    fun of(key: String) = states.getOrPut(key) { androidx.compose.foundation.lazy.LazyListState() }

    companion object {
        val Saver = androidx.compose.runtime.saveable.Saver<TabScrollStates, ArrayList<Any>>(
            save = { scrolls ->
                ArrayList(scrolls.states.flatMap { (key, state) -> listOf(key, state.firstVisibleItemIndex, state.firstVisibleItemScrollOffset) })
            },
            restore = { saved ->
                TabScrollStates(saved.chunked(3).associate { (key, index, offset) -> key as String to ((index as Int) to (offset as Int)) })
            }
        )
    }
}

/** A value computed from [calc]: a reader recomposes only when that value changes, not on every change of the state it reads. */
@Composable
internal fun <T> derivedFrom(calc: () -> T): State<T> = remember { derivedStateOf(calc) }
