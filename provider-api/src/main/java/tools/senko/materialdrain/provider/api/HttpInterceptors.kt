package tools.senko.materialdrain.provider.api

import okhttp3.Interceptor
import okhttp3.OkHttpClient

/**
 * Interceptors added to every HTTP client the hosts and the app make. Always empty in the app: the screenshot tests
 * (app/src/androidTest, see FakePixeldrain there) answer every request with made-up data through it, set before the
 * first client is built.
 */
object HttpInterceptors {
    @Volatile
    var all: List<Interceptor> = emptyList()
}

/** Adds [HttpInterceptors.all]; every client builder calls it, so none reaches the network in the screenshot tests. */
fun OkHttpClient.Builder.addHttpInterceptors(): OkHttpClient.Builder = apply { HttpInterceptors.all.forEach { addInterceptor(it) } }
