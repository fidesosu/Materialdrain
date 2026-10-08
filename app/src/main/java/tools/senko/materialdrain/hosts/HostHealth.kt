package tools.senko.materialdrain.hosts

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import tools.senko.materialdrain.provider.PIXELDRAIN_PROVIDER_ID
import tools.senko.materialdrain.provider.ProviderConfigStore
import tools.senko.materialdrain.provider.api.GenericRestConfig
import tools.senko.materialdrain.provider.api.ProviderConfig
import tools.senko.materialdrain.provider.api.S3Config
import tools.senko.materialdrain.provider.api.SmbConfig
import tools.senko.materialdrain.provider.api.WebDavConfig
import java.net.ConnectException
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLException

/**
 * Whether a host answers, as far as the last check knows. [UNKNOWN] until a host has been checked, or when there's no
 * address to check; [OFFLINE] when the device itself has no network, which says nothing about the host.
 */
enum class Reachability { UNKNOWN, REACHABLE, UNREACHABLE, OFFLINE }

/**
 * The last check of a host. While a new check runs ([checking]), the result of the one before stays, so the switcher
 * doesn't flicker back to "unknown" every few minutes.
 *
 * @param latencyMillis how long the host took to answer
 * @param message why it's unknown or unreachable, e.g. "Address not found"
 * @param target the address that was checked: a check of an address that has since changed isn't current any more
 */
data class HostCheck(
    val reachability: Reachability = Reachability.UNKNOWN,
    val checking: Boolean = false,
    val latencyMillis: Long? = null,
    val message: String? = null,
    val checkedAtMillis: Long = 0,
    val target: String? = null
)

/** A check made this recently is still current, and isn't made again unless asked to. */
private const val FRESH_MILLIS = 30_000L

/** How long a host gets to answer. */
private const val PROBE_TIMEOUT_SECONDS = 5L

/**
 * Keeps whether each host answers, for the host switcher. Hosts are checked ahead of time, so the switcher knows before
 * it's opened: every host as soon as the device has a network (which is also when the app starts), again whenever the
 * network changes, and every few minutes while the app is in front (see App.kt).
 *
 * A check only asks whether the host's address answers: one request to it (for SMB, a connection to its port), without
 * any login, so it's quick and can be made often. Any answer counts, even an error page or "sign in first": a sign-in
 * problem isn't an unreachable host. Whether the login works is the host's "Test connection" in the settings.
 */
class HostHealth(
    context: Context,
    private val configStore: ProviderConfigStore,
    private val scope: CoroutineScope,
) {
    private val _states = MutableStateFlow<Map<String, HostCheck>>(emptyMap())
    val states: StateFlow<Map<String, HostCheck>> = _states.asStateFlow()

    /** Its own client: no logins added to the requests, no redirects followed (an answer is an answer), short timeouts. */
    private val client = OkHttpClient.Builder()
        .connectTimeout(PROBE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(PROBE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .callTimeout(PROBE_TIMEOUT_SECONDS * 2, TimeUnit.SECONDS)
        .followRedirects(false)
        .followSslRedirects(false)
        .retryOnConnectionFailure(false)
        .build()

    private val connectivity = context.getSystemService(ConnectivityManager::class.java)
    private val inFlight = ConcurrentHashMap.newKeySet<String>()

    init {
        // Called right away when there's a network already, which is the first check after the app starts
        connectivity?.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) = checkAll(force = true)
            override fun onLost(network: Network) {
                if (connectivity.activeNetwork == null) markOffline()
            }
        })
    }

    private fun hostIds(): List<String> = listOf(PIXELDRAIN_PROVIDER_ID) + configStore.providers.value.map { it.id }

    /** Checks every host; with [force], also those checked a moment ago. */
    fun checkAll(force: Boolean = false) = check(hostIds(), force)

    /** Checks the given hosts in the background; a host checked within the last half minute is left as it is, unless [force]. */
    fun check(hostIds: Collection<String>, force: Boolean = false) {
        if (connectivity != null && connectivity.activeNetwork == null) {
            markOffline()
            return
        }
        val now = System.currentTimeMillis()
        hostIds.forEach { id ->
            val target = targetOf(id)
            val last = _states.value[id]
            val current = last != null && last.target == target?.label && last.reachability != Reachability.OFFLINE &&
                now - last.checkedAtMillis < FRESH_MILLIS
            if ((current && !force) || !inFlight.add(id)) return@forEach
            _states.update { it + (id to (it[id] ?: HostCheck()).copy(checking = true)) }
            scope.launch {
                val result = try {
                    probe(target)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    HostCheck(Reachability.UNREACHABLE, message = e.message)
                } finally {
                    inFlight.remove(id)
                }
                _states.update { it + (id to result.copy(checking = false, checkedAtMillis = System.currentTimeMillis(), target = target?.label)) }
            }
        }
    }

    private fun markOffline() {
        _states.update { states ->
            hostIds().associateWith { id ->
                (states[id] ?: HostCheck()).copy(reachability = Reachability.OFFLINE, checking = false, latencyMillis = null, message = "This device is offline")
            }
        }
    }

    /** What is checked for a host: an address to send a request to, or (SMB) a port to connect to. */
    private sealed interface Target {
        val label: String
        data class Http(val url: String) : Target { override val label get() = url }
        data class Tcp(val host: String, val port: Int) : Target { override val label get() = "$host:$port" }
    }

    private fun targetOf(id: String): Target? {
        if (id == PIXELDRAIN_PROVIDER_ID) return Target.Http("https://pixeldrain.com/")
        val config: ProviderConfig = configStore.get(id)?.config ?: return null
        return when (config) {
            is GenericRestConfig -> originOf(config.baseUrl)
            is WebDavConfig -> originOf(config.baseUrl)
            is S3Config -> originOf(config.endpoint)
            is SmbConfig -> config.host.trim().takeIf { it.isNotEmpty() }?.let { Target.Tcp(it, config.port) }
        }
    }

    /** The scheme, host and port of [url]; {placeholders} in its path (e.g. a WebDAV {username}) don't matter here. */
    private fun originOf(url: String): Target? {
        val parsed = url.trim().replace(Regex("\\{[^}]*\\}"), "x").toHttpUrlOrNull() ?: return null
        return Target.Http("${parsed.scheme}://${parsed.host}:${parsed.port}/")
    }

    private fun probe(target: Target?): HostCheck {
        target ?: return HostCheck(Reachability.UNKNOWN, message = "No address set in its config")
        val started = System.nanoTime()
        return try {
            when (target) {
                is Target.Http -> client.newCall(Request.Builder().url(target.url).head().build()).execute().close()
                is Target.Tcp -> Socket().use { it.connect(InetSocketAddress(target.host, target.port), (PROBE_TIMEOUT_SECONDS * 1000).toInt()) }
            }
            HostCheck(Reachability.REACHABLE, latencyMillis = (System.nanoTime() - started) / 1_000_000)
        } catch (e: UnknownHostException) {
            HostCheck(Reachability.UNREACHABLE, message = "Address not found")
        } catch (e: SocketTimeoutException) {
            HostCheck(Reachability.UNREACHABLE, message = "Not answering")
        } catch (e: ConnectException) {
            HostCheck(Reachability.UNREACHABLE, message = "Refused the connection")
        } catch (e: SSLException) {
            // It answered, but not in a way the app can use
            HostCheck(Reachability.UNREACHABLE, message = "Its certificate isn't trusted")
        } catch (e: java.io.InterruptedIOException) {
            HostCheck(Reachability.UNREACHABLE, message = "Not answering")
        }
    }
}
