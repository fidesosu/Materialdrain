package tools.senko.materialdrain.screenshots

import android.app.Application
import android.content.Context
import androidx.test.runner.AndroidJUnitRunner
import tools.senko.materialdrain.provider.api.HttpInterceptors

/**
 * The tests' runner: the made-up host (FakePixeldrain) is in place before anything of the app exists. The app's HTTP
 * clients take the interceptors there are when they're built, and the system can start the app's process, and build
 * them, before a test does (the file picker asking HostDocumentsProvider for its hosts); they'd reach the real hosts then.
 */
class FakeHostRunner : AndroidJUnitRunner() {
    override fun newApplication(cl: ClassLoader?, className: String?, context: Context?): Application {
        HttpInterceptors.all = listOf(AppDriver.sharedFake)
        return super.newApplication(cl, className, context)
    }
}
