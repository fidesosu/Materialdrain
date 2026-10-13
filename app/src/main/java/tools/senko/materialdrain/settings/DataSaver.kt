package tools.senko.materialdrain.settings

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.compose.runtime.compositionLocalOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

/**
 * The data saver: what the app would load by itself (thumbnails, previews, config updates, host checks) waits until it's
 * asked for. Nothing is turned off, it only becomes manual: a thumbnail loads when its tile is tapped (or all of them with
 * the list's "Load thumbnails"), a preview with its "Load preview" button, the configs from "Check all for updates" and
 * the hosts from the switcher's refresh.
 *
 * Each `hold…` says whether that one is held back right now: the data saver is on, the setting for it is on, and (when
 * it only applies on mobile data) the network is metered.
 */
class DataSaver(context: Context, settings: AppSettings, private val scope: CoroutineScope) {

    private val connectivity = context.getSystemService(ConnectivityManager::class.java)

    private val metered = MutableStateFlow(connectivity?.isActiveNetworkMetered ?: false)

    init {
        connectivity?.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                metered.value = !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
            }
            override fun onLost(network: Network) {
                metered.value = connectivity.isActiveNetworkMetered
            }
        })
    }

    /** Whether the data saver applies right now. */
    val active: StateFlow<Boolean> = combine(settings.dataSaver, settings.dataSaverMobileOnly, metered) { on, mobileOnly, isMetered ->
        on && (!mobileOnly || isMetered)
    }.stateIn(scope, SharingStarted.Eagerly, settings.dataSaver.value && (!settings.dataSaverMobileOnly.value || metered.value))

    private fun holding(setting: StateFlow<Boolean>): StateFlow<Boolean> =
        combine(active, setting) { isActive, hold -> isActive && hold }.stateIn(scope, SharingStarted.Eagerly, active.value && setting.value)

    val holdThumbnails: StateFlow<Boolean> = holding(settings.holdThumbnails)
    val holdPreviews: StateFlow<Boolean> = holding(settings.holdPreviews)
    val holdConfigUpdates: StateFlow<Boolean> = holding(settings.holdConfigUpdates)
    val holdHostChecks: StateFlow<Boolean> = holding(settings.holdHostChecks)

    private val _allThumbnailsAsked = MutableStateFlow(false)
    /** The thumbnails were asked for with "Load thumbnails": they load as usual until the app is closed. */
    val allThumbnailsAsked: StateFlow<Boolean> = _allThumbnailsAsked.asStateFlow()

    fun askAllThumbnails() {
        _allThumbnailsAsked.value = true
    }

    private val _askedThumbnails = MutableStateFlow<Set<String>>(emptySet())
    /** The thumbnails asked for one by one (a tapped tile), by their address, until the app is closed. */
    val askedThumbnails: StateFlow<Set<String>> = _askedThumbnails.asStateFlow()

    fun askThumbnail(url: String) {
        _askedThumbnails.update { it + url }
    }
}

/**
 * What the data saver holds back from the screens, for the composables that load pictures by themselves (FileIcon), so
 * they needn't be handed it through every screen. [holdThumbnails] is false once they were all asked for;
 * [loadThumbnail] asks for one of them, [loadAllThumbnails] for all of them.
 */
data class DataSaverState(
    val holdThumbnails: Boolean = false,
    val askedThumbnails: Set<String> = emptySet(),
    val loadThumbnail: (String) -> Unit = {},
    val loadAllThumbnails: () -> Unit = {}
) {
    /** Whether the thumbnail at [url] is shown: not held back, or asked for. */
    fun showsThumbnail(url: String): Boolean = !holdThumbnails || url in askedThumbnails
}

/** Changes as thumbnails are asked for, so only what reads it is recomposed (not static). */
val LocalDataSaver = compositionLocalOf { DataSaverState() }
