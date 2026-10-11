package tools.senko.materialdrain

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import tools.senko.materialdrain.files.FileInfoViewModel
import tools.senko.materialdrain.filesystem.FilesystemViewModel
import tools.senko.materialdrain.lists.ListViewModel
import tools.senko.materialdrain.preferences.AccountViewModel
import tools.senko.materialdrain.preferences.AuthViewModel
import tools.senko.materialdrain.preferences.ProviderSettingsViewModel
import tools.senko.materialdrain.upload.UploadViewModel

class ViewModelFactory(
    private val application: Application,
    private val container: AppContainer
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(UploadViewModel::class.java)) {
            return UploadViewModel(application, container.providerRegistry, container.providerConfigStore, container.sessionManager, container.transferRegistry, container.appSettings) as T
        }
        if (modelClass.isAssignableFrom(FileInfoViewModel::class.java)) {
            return FileInfoViewModel(application, container.providerRegistry, container.providerConfigStore, container.sessionManager, container.downloadManager, container.appSettings, container.uiMessages, container.contentChanges) as T
        }
        if (modelClass.isAssignableFrom(FilesystemViewModel::class.java)) {
            return FilesystemViewModel(application, container.providerRegistry, container.providerConfigStore, container.sessionManager, container.transferRegistry, container.appSettings, container.uiMessages, container.contentChanges) as T
        }
        if (modelClass.isAssignableFrom(ListViewModel::class.java)) {
            return ListViewModel(container.providerRegistry, container.providerConfigStore, container.sessionManager, container.appSettings, container.contentChanges) as T
        }
        if (modelClass.isAssignableFrom(AuthViewModel::class.java)) {
            return AuthViewModel(container.userApi, container.sessionManager) as T
        }
        if (modelClass.isAssignableFrom(AccountViewModel::class.java)) {
            return AccountViewModel(container.userApi, container.sessionManager, container.uiMessages) as T
        }
        if (modelClass.isAssignableFrom(ProviderSettingsViewModel::class.java)) {
            return ProviderSettingsViewModel(container.providerConfigStore, container.providerRegistry, container.providerUpdater) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: " + modelClass.name)
    }
}
