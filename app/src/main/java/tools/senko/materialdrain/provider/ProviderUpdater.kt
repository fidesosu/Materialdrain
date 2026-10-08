package tools.senko.materialdrain.provider

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.ResponseBody
import tools.senko.materialdrain.provider.api.ConfigUpdates
import tools.senko.materialdrain.provider.api.SensitiveChange
import tools.senko.materialdrain.provider.api.UpdateCheckResult
import java.util.concurrent.TimeUnit

private const val MAX_CONFIG_BYTES = 256L * 1024
private const val AUTO_UPDATE_INTERVAL_MILLIS = 24L * 60 * 60 * 1000

sealed interface CheckOutcome {
    data object UpToDate : CheckOutcome
    data class UpdateAvailable(val version: Int, val sensitiveChanges: List<SensitiveChange>) : CheckOutcome
    data class NeedsNewerApp(val minAppVersion: Int) : CheckOutcome
    data class Failed(val message: String) : CheckOutcome
    data object NoUpdateUrl : CheckOutcome
}

/**
 * Checks configs' update URLs and applies updates. The rule it enforces (see [ConfigUpdates.sensitiveChanges]):
 * an update may change what requests look like, but never where credentials go without the user approving it.
 * So auto-update only ever applies updates without sensitive changes; the rest wait for the user.
 *
 * Auto-update runs when the app starts, at most once a day per config — there is no background job.
 */
class ProviderUpdater(
    private val store: ProviderConfigStore,
    baseClient: OkHttpClient,
    private val appVersion: Int
) {
    private val client = baseClient.newBuilder().callTimeout(20, TimeUnit.SECONDS).build()

    suspend fun check(id: String): CheckOutcome = withContext(Dispatchers.IO) {
        val stored = store.get(id) ?: return@withContext CheckOutcome.Failed("This host no longer exists.")
        val url = stored.updateUrl ?: return@withContext CheckOutcome.NoUpdateUrl
        if (!ConfigUpdates.isHttps(url)) return@withContext CheckOutcome.Failed("The update URL has to use https.")
        val now = System.currentTimeMillis()

        try {
            val request = Request.Builder().url(url.trim())
                .apply { stored.etag?.let { header("If-None-Match", it) } }
                .build()
            client.newCall(request).execute().use { response ->
                // Unchanged since the last fetch: whatever that fetch found still stands
                if (response.code == 304) {
                    store.recordCheck(id, stored.pendingUpdate, etag = null, checkedAtMillis = now)
                    return@withContext stored.pendingUpdate?.config?.let { pending ->
                        CheckOutcome.UpdateAvailable(pending.meta?.version ?: 0, ConfigUpdates.sensitiveChanges(stored.config, pending))
                    } ?: CheckOutcome.UpToDate
                }
                if (!response.isSuccessful) return@withContext CheckOutcome.Failed("The update URL answered HTTP ${response.code}.")

                val text = readLimited(response.body)
                    ?: return@withContext CheckOutcome.Failed("The file at the update URL is larger than 256 KB.")
                val etag = response.header("ETag")

                when (val result = ConfigUpdates.evaluate(stored.config, text, appVersion)) {
                    UpdateCheckResult.UpToDate -> {
                        store.recordCheck(id, pendingUpdate = null, etag = etag, checkedAtMillis = now)
                        CheckOutcome.UpToDate
                    }
                    is UpdateCheckResult.Available -> {
                        val pending = ConfigSource(result.update, text.trim())
                        store.recordCheck(id, pendingUpdate = pending, etag = etag, checkedAtMillis = now)
                        CheckOutcome.UpdateAvailable(result.update.meta?.version ?: 0, result.sensitiveChanges)
                    }
                    // No ETag kept for these: once the app (or the file) is fixed, the next check must re-read it
                    is UpdateCheckResult.NeedsNewerApp -> {
                        store.recordCheck(id, pendingUpdate = null, etag = null, checkedAtMillis = now)
                        CheckOutcome.NeedsNewerApp(result.minAppVersion)
                    }
                    is UpdateCheckResult.Invalid -> {
                        store.recordCheck(id, pendingUpdate = stored.pendingUpdate, etag = null, checkedAtMillis = now)
                        CheckOutcome.Failed(result.reason)
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            CheckOutcome.Failed(e.message ?: "The update URL couldn't be reached.")
        }
    }

    /** Sensitive changes of the pending update against the config in use, empty when nothing is pending. */
    fun pendingSensitiveChanges(id: String): List<SensitiveChange> {
        val stored = store.get(id) ?: return emptyList()
        val pending = stored.pendingUpdate ?: return emptyList()
        return ConfigUpdates.sensitiveChanges(stored.config, pending.config)
    }

    /**
     * Applies the pending update. If it sends credentials somewhere new (another host, or another kind of host),
     * the saved token is cleared, so it's never sent to the new place without being entered again.
     */
    fun applyPending(id: String) {
        val stored = store.get(id) ?: return
        val pending = stored.pendingUpdate ?: return
        val clearSecret = ConfigUpdates.sensitiveChanges(stored.config, pending.config).any {
            it.kind == SensitiveChange.Kind.DESTINATION || it.kind == SensitiveChange.Kind.KIND
        }
        store.applyUpdate(id, pending, clearSecret)
    }

    /** Checks the configs with auto-update on (at most once a day each) and applies updates that need no approval. */
    suspend fun runAutoUpdates() {
        val now = System.currentTimeMillis()
        store.providers.value
            .filter { it.autoUpdate && it.updateUrl != null && now - it.lastCheckedMillis >= AUTO_UPDATE_INTERVAL_MILLIS }
            .forEach { stored ->
                val outcome = check(stored.id)
                if (outcome is CheckOutcome.UpdateAvailable && outcome.sensitiveChanges.isEmpty()) applyPending(stored.id)
            }
    }

    private fun readLimited(body: ResponseBody): String? {
        val source = body.source()
        // request() is true when at least that many bytes arrived, i.e. the file is over the limit
        if (source.request(MAX_CONFIG_BYTES + 1)) return null
        return source.buffer.readUtf8()
    }
}
