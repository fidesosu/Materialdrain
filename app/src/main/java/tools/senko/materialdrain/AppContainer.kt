package tools.senko.materialdrain

import android.app.Application
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import tools.senko.materialdrain.api.PixeldrainCoreApi
import tools.senko.materialdrain.api.PixeldrainCoreApiImpl
import tools.senko.materialdrain.api.PixeldrainFilesystemApi
import tools.senko.materialdrain.api.PixeldrainFilesystemApiImpl
import tools.senko.materialdrain.api.PixeldrainHttpClient
import tools.senko.materialdrain.api.PixeldrainUserApi
import tools.senko.materialdrain.api.PixeldrainUserApiImpl
import tools.senko.materialdrain.auth.SessionManager
import tools.senko.materialdrain.settings.AppSettings
import tools.senko.materialdrain.transfer.TransferRegistry

/**
 * Manual dependency container, shared by the whole process so that the ViewModels and the UI always
 * see the same HTTP clients and the same [SessionManager].
 */
class AppContainer private constructor(application: Application) {
    private val httpClient = PixeldrainHttpClient()

    val coreApi: PixeldrainCoreApi = PixeldrainCoreApiImpl(httpClient)
    val filesystemApi: PixeldrainFilesystemApi = PixeldrainFilesystemApiImpl(httpClient)
    val userApi: PixeldrainUserApi = PixeldrainUserApiImpl(httpClient)
    val sessionManager = SessionManager(application)
    val transferRegistry = TransferRegistry(application)
    val appSettings = AppSettings(application)

    /** The client of the API, media players stream over it too so they reuse its connections. */
    val okHttpClient get() = httpClient.okHttpClient

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
