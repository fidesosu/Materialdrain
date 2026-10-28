package tools.senko.materialdrain

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import tools.senko.materialdrain.provider.PIXELDRAIN_PROVIDER_ID
import tools.senko.materialdrain.provider.api.ProviderCapability
import tools.senko.materialdrain.provider.api.ProviderConfig
import tools.senko.materialdrain.provider.api.resolveScreens

/**
 * The tabs of the active host, in the order the bottom bar and the tab strip show them. [config] is the host's
 * config, null for the built-in Pixeldrain.
 */
internal class HostTabs(val tabs: List<Tab>, val config: ProviderConfig?) {
    /**
     * A config with no screens to show (it chose none, or only ones the host can't back): nothing to fall back to, so the
     * current screen shows a message instead (see MaterialdrainScreen's main content).
     */
    val noScreensConfigured: Boolean get() = config != null && tabs.isEmpty()

    /** The tab of [screen]'s kind that's shown for it: the one last chosen ([chosenId]), else the first of that kind. */
    fun tabOf(screen: Screen, chosenId: String?): Tab? =
        tabs.firstOrNull { it.screen == screen && it.id == chosenId } ?: tabs.firstOrNull { it.screen == screen }
}

/**
 * The tabs follow what the active host offers: its config decides which of Files, Lists and Filesystem exist. Worked
 * out again when a config changes ([configChanges], see ProviderConfigStore.changes).
 */
@Composable
internal fun rememberHostTabs(appContainer: AppContainer, activeHostId: String, configChanges: Int): HostTabs {
    val activeCapabilities = remember(configChanges) {
        appContainer.providerRegistry.resolve(appContainer.providerConfigStore.activeProviderId.value).capabilities
    }
    // The built-in Pixeldrain host isn't config-driven, so it keeps today's capability-based navigation. Every other
    // host is a config, whose "screens" field picks what shows for it (or, left out, everything the host can back)
    val activeConfig = remember(configChanges) {
        if (activeHostId == PIXELDRAIN_PROVIDER_ID) {
            null
        } else {
            appContainer.providerConfigStore.providers.value.firstOrNull { it.id == activeHostId }?.config
        }
    }
    // The config's screens in its own order, so a config can put them in whichever order makes sense for that host
    // (the tabs follow it), without those the host can't back (see resolveScreens)
    val configScreens = remember(activeCapabilities, activeConfig) {
        activeConfig?.let { resolveScreens(it.screens, activeCapabilities) }
    }
    // Each tab with its entry in the config, for its own name, root and the actions it takes away (see ScreenConfig). Not
    // config-driven (the built-in Pixeldrain) means one tab of each kind the host can back, with nothing taken away
    return remember(activeCapabilities, configScreens, activeConfig) {
        val tabs = configScreens?.map { Tab(it.screen.toScreen(), it) } ?: buildList {
            if (ProviderCapability.ENUMERATE in activeCapabilities) add(Tab(Screen.Files))
            if (ProviderCapability.LISTS in activeCapabilities) add(Tab(Screen.Lists))
            if (ProviderCapability.BROWSE in activeCapabilities) add(Tab(Screen.Filesystem))
        }
        // The Upload tab only on a host with nowhere else to upload to: the Files and Filesystem screens upload from their
        // ⋮ menu, and take shared files (see SharedUploadBar)
        val uploadsElsewhere = tabs.any { it.screen == Screen.Files || it.screen == Screen.Filesystem }
        HostTabs(tabs.filter { it.screen != Screen.Upload || !uploadsElsewhere }, activeConfig)
    }
}
