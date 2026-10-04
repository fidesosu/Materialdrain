package tools.senko.materialdrain.provider.api

import android.util.Log

/**
 * The app's diagnostic log. Every line is tagged "MD/<category>", so `adb logcat -s MD` shows just these,
 * or filter logcat by "MD/" (Files, Lists, Filesystem, Http, Auth, Provider, Config).
 *
 * Never log a credential: callers log lengths or kinds ("api key, 40 chars"), not the values.
 */
object ProviderLog {
    private const val PREFIX = "MD/"

    fun d(category: String, message: String) = Log.d(PREFIX + category, message)

    fun i(category: String, message: String) = Log.i(PREFIX + category, message)

    fun w(category: String, message: String, throwable: Throwable? = null) = Log.w(PREFIX + category, message, throwable)

    fun e(category: String, message: String, throwable: Throwable? = null) = Log.e(PREFIX + category, message, throwable)

    /** The start of a response body, for an error report: enough to read the server's message, not a whole page. */
    fun snippet(text: String, max: Int = 300): String =
        text.replace('\n', ' ').take(max) + if (text.length > max) "…(${text.length} chars)" else ""
}
