package tools.senko.materialdrain.provider

import tools.senko.materialdrain.provider.api.Credentials
import tools.senko.materialdrain.provider.api.StorageProvider
import tools.senko.materialdrain.provider.pixeldrain.PixeldrainStorageProvider

/**
 * Resolves configured hosts (from [configStore]) into [StorageProvider] instances. Pixeldrain is always
 * available as [pixeldrainProvider] and never goes through [ProviderConfigStore] — every other kind is
 * only built from what the user configured in Advanced settings.
 */
class ProviderRegistry(
    private val configStore: ProviderConfigStore,
    private val pixeldrainProvider: PixeldrainStorageProvider,
    /** The account's own login (Settings → Account), used by hosts which fall back to it. */
    private val accountCredentials: () -> Credentials
) {
    /** Each kind of host builds its own provider, see [HostType.build]. */
    fun build(stored: StoredProvider): StorageProvider =
        hostTypeOf(stored.config).build(stored.id, configStore, accountCredentials)

    // Built providers are reused: the screens ask for one per row, and building one creates an HTTP client.
    // Everything is dropped when the hosts or their credentials change (see ProviderConfigStore.changes).
    private val built = HashMap<String, StorageProvider>()
    private var builtForVersion = -1

    /** Falls back to Pixeldrain when [id] doesn't match a configured host (deleted, or the built-in id). */
    @Synchronized
    fun resolve(id: String): StorageProvider {
        if (id == PIXELDRAIN_PROVIDER_ID) return pixeldrainProvider
        val version = configStore.changes.value
        if (version != builtForVersion) {
            built.clear()
            builtForVersion = version
        }
        built[id]?.let { return it }
        val stored = configStore.providers.value.firstOrNull { it.id == id } ?: return pixeldrainProvider
        return build(stored).also { built[id] = it }
    }
}
