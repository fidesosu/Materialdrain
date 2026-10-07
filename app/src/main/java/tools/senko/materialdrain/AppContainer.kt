package tools.senko.materialdrain

import android.app.Application
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import tools.senko.materialdrain.auth.SessionManager
import tools.senko.materialdrain.hosts.HostHealth
import tools.senko.materialdrain.provider.ProviderConfigStore
import tools.senko.materialdrain.provider.api.Credentials
import tools.senko.materialdrain.provider.importBundledPixeldrainConfig
import tools.senko.materialdrain.ui.media.HostRequestAuth
import tools.senko.materialdrain.provider.ProviderRegistry
import tools.senko.materialdrain.provider.ProviderUpdater
import tools.senko.materialdrain.provider.pixeldrain.PixeldrainStorageProvider
import tools.senko.materialdrain.provider.pixeldrain.internal.PixeldrainUserApi
import tools.senko.materialdrain.settings.AppSettings
import tools.senko.materialdrain.transfer.TransferRegistry

/**
 * Manual dependency container, shared by the whole process so that the ViewModels and the UI always
 * see the same HTTP clients and the same [SessionManager].
 */
class AppContainer private constructor(application: Application) {
    val sessionManager = SessionManager(application)

    /** Pixeldrain stays the hardcoded default. This is the one place its HTTP client is created. */
    val pixeldrainProvider = PixeldrainStorageProvider(apiKeyProvider = { sessionManager.currentApiKey() })

    // Login is the one thing that stays Pixeldrain-only (it's the account of the built-in host), everything else
    // goes through the active StorageProvider
    val userApi: PixeldrainUserApi = pixeldrainProvider.userApi

    val transferRegistry = TransferRegistry(application)
    val appSettings = AppSettings(application)

    /** Custom hosts configured in Advanced settings (Nextcloud/WebDAV, S3, or a generic REST config). */
    val providerConfigStore = ProviderConfigStore(application)
    val providerRegistry = ProviderRegistry(providerConfigStore, pixeldrainProvider) { Credentials(apiKey = sessionManager.currentApiKey()) }

    /** The client of the API, media players stream over it too so they reuse its connections. */
    val okHttpClient get() = pixeldrainProvider.okHttpClient

    /** Checks and applies updates of configs that have an update URL. */
    val providerUpdater = ProviderUpdater(
        store = providerConfigStore,
        baseClient = okHttpClient,
        appVersion = application.packageManager.getPackageInfo(application.packageName, 0).longVersionCode.toInt()
    )

    /** Work which belongs to the process rather than to a screen. */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Locks the app behind a fingerprint, face or screen lock, when the setting is on. */
    val appLock = tools.senko.materialdrain.auth.AppLock(appSettings)

    /** Files another app shared with Materialdrain, waiting for the upload screen to take them. */
    val pendingShares = kotlinx.coroutines.flow.MutableStateFlow<List<android.net.Uri>>(emptyList())

    /** Whether each host answers, for the dot in the host switcher. */
    val hostHealth = HostHealth(providerRegistry, appScope)

    init {
        importBundledPixeldrainConfig(application, providerConfigStore)
        // Thumbnails of files on SMB shares are made on this device, and kept here between runs
        tools.senko.materialdrain.provider.smb.SmbContentServer.cacheDir = java.io.File(application.cacheDir, "smb-thumbnails")
        HostRequestAuth.headersFor = { url ->
            providerRegistry.resolve(providerConfigStore.activeProviderId.value).requestHeaders(url)
        }
        // Auto-update happens at app start only (at most once a day per config), there's no background job
        appScope.launch { providerUpdater.runAutoUpdates() }
    }

    /**
     * Owner for the ViewModels which run uploads and downloads. Being tied to the process instead of the
     * activity, they (and the transfers in their scope) survive the activity being closed while a
     * transfer is running in the background.
     */
    val transferViewModelStoreOwner: ViewModelStoreOwner = object : ViewModelStoreOwner {
        override val viewModelStore = ViewModelStore()
    }

    companion object {
        @Volatile
        private var instance: AppContainer? = null

        fun get(application: Application): AppContainer =
            instance ?: synchronized(this) {
                instance ?: AppContainer(application).also { instance = it }
            }
    }
}
