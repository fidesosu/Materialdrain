package tools.senko.materialdrain.provider

import tools.senko.materialdrain.provider.api.Credentials
import tools.senko.materialdrain.provider.api.GenericRestConfig
import tools.senko.materialdrain.provider.api.S3Config
import tools.senko.materialdrain.provider.api.StorageProvider
import tools.senko.materialdrain.provider.api.WebDavConfig
import tools.senko.materialdrain.provider.genericrest.GenericRestStorageProvider
import tools.senko.materialdrain.provider.pixeldrain.PixeldrainStorageProvider
import tools.senko.materialdrain.provider.s3.S3StorageProvider
import tools.senko.materialdrain.provider.webdav.WebDavStorageProvider

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
    fun build(stored: StoredProvider): StorageProvider {
        return when (val config = stored.config) {
            is GenericRestConfig -> GenericRestStorageProvider(
                id = stored.id,
                config = config,
                credentials = { ownOrAccountCredentials(stored.id, config) },
                onLoginToken = { configStore.saveLoginToken(stored.id, it) }
            )
            is WebDavConfig -> WebDavStorageProvider(stored.id, config, credentials = { configStore.credentials(stored.id) })
            is S3Config -> S3StorageProvider(stored.id, config, credentials = { configStore.credentials(stored.id) })
        }
    }

    /**
     * The host's own sign-in or API key. A host with `account_fallback` and none of its own uses the account's login
     * instead, so Pixeldrain works from the Account settings alone.
     */
    private fun ownOrAccountCredentials(id: String, config: GenericRestConfig): Credentials {
        val own = configStore.credentials(id)
        val hasOwn = own.apiKey.isNotBlank() || own.loginToken != null || own.hasPassword
        return if (config.accountFallback && !hasOwn) accountCredentials() else own
    }

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
