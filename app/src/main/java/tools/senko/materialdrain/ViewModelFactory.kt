package tools.senko.materialdrain

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import tools.senko.materialdrain.files.FileInfoViewModel
import tools.senko.materialdrain.filesystem.FilesystemViewModel
import tools.senko.materialdrain.lists.ListViewModel
import tools.senko.materialdrain.preferences.AuthViewModel
import tools.senko.materialdrain.upload.UploadViewModel

class ViewModelFactory(
    private val application: Application,
    private val container: AppContainer
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(UploadViewModel::class.java)) {
            return UploadViewModel(application, container.coreApi, container.sessionManager, container.transferRegistry) as T
        }
        if (modelClass.isAssignableFrom(FileInfoViewModel::class.java)) {
            return FileInfoViewModel(application, container.coreApi, container.userApi, container.filesystemApi, container.sessionManager, container.transferRegistry) as T
        }
        if (modelClass.isAssignableFrom(FilesystemViewModel::class.java)) {
            return FilesystemViewModel(application, container.filesystemApi, container.sessionManager, container.transferRegistry, container.appSettings) as T
        }
        if (modelClass.isAssignableFrom(ListViewModel::class.java)) {
            return ListViewModel(container.coreApi, container.userApi, container.sessionManager) as T
        }
        if (modelClass.isAssignableFrom(AuthViewModel::class.java)) {
            return AuthViewModel(container.userApi, container.sessionManager) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: " + modelClass.name)
    }
}
