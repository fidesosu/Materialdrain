package tools.senko.materialdrain.update

import android.content.Context
import android.content.pm.ApplicationInfo
import androidx.core.content.FileProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import tools.senko.materialdrain.BuildConfig
import tools.senko.materialdrain.UiMessages
import tools.senko.materialdrain.files.FILE_PROVIDER_SUFFIX
import tools.senko.materialdrain.files.OpenableDownload
import tools.senko.materialdrain.files.openDownloadedFile
import tools.senko.materialdrain.settings.AppSettings
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/** Where the releases are: GitHub's download links, which need no API (and have no rate limit). */
const val RELEASES_URL = "https://github.com/fidesosu/Materialdrain/releases"

/** The check on opening runs at most this often. */
private const val CHECK_INTERVAL_MILLIS = 6L * 60 * 60 * 1000

private const val MAX_UPDATE_JSON_BYTES = 512L * 1024
private const val APK_MIME_TYPE = "application/vnd.android.package-archive"

/** One change a release brings: the first paragraph of a commit's message, and the commit's time. */
@Serializable
data class UpdateChange(val time: Long, val text: String)

/** A release, as its update.json describes it (written by .github/scripts/update_json.py). */
@Serializable
data class UpdateInfo(
    val versionName: String,
    val versionCode: Long,
    val commitTime: Long = 0,
    val apk: String,
    val sha256: String,
    val size: Long = 0,
    val changes: List<UpdateChange> = emptyList()
)

/** A release newer than the installed build: [changes] are those the installed build doesn't have yet, newest first. */
data class AvailableUpdate(
    val info: UpdateInfo,
    val isDev: Boolean,
    val apkUrl: String,
    val changes: List<UpdateChange>
)

sealed interface UpdateResult {
    data object UpToDate : UpdateResult
    data class Available(val update: AvailableUpdate) : UpdateResult
    data class Failed(val message: String) : UpdateResult
}

/** How the download of an update goes, shown in its sheet. */
sealed interface UpdateDownload {
    data object None : UpdateDownload
    /** [progress] from 0 to 1, or null while the size isn't known. */
    data class Running(val progress: Float?) : UpdateDownload
    data class Failed(val message: String) : UpdateDownload
}

/** The build that's installed: what a release has to be newer than. */
data class InstalledBuild(
    val versionName: String,
    val versionCode: Long,
    val commitTime: Long,
    /** Signed with the release key, so a release installs over it. Not a debug build (or a local one, "-local") */
    val updatable: Boolean,
    val isDev: Boolean
)

/** This app as it's installed. */
fun installedBuild(context: Context): InstalledBuild {
    val info = runCatching { context.packageManager.getPackageInfo(context.packageName, 0) }.getOrNull()
    val name = info?.versionName.orEmpty()
    val debuggable = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
    return InstalledBuild(
        versionName = name,
        versionCode = info?.longVersionCode ?: 0L,
        commitTime = BuildConfig.COMMIT_TIME,
        updatable = !debuggable && !name.endsWith("-local"),
        isDev = "-dev." in name
    )
}

/**
 * Picks the release to offer: the newest of [stable] and [dev] (when looked for), by versionCode, which only goes up
 * across stable and dev builds (see app/build.gradle.kts). Null when neither is newer than [installed].
 */
fun chooseUpdate(installed: InstalledBuild, stable: UpdateInfo?, dev: UpdateInfo?, releasesUrl: String = RELEASES_URL): AvailableUpdate? {
    val newest = listOfNotNull(stable?.let { it to false }, dev?.let { it to true })
        .filter { (info, _) -> info.versionCode > installed.versionCode }
        .maxByOrNull { (info, _) -> info.versionCode }
        ?: return null
    val (info, isDev) = newest
    val tag = if (isDev) "dev-build" else "v${info.versionName}"
    return AvailableUpdate(
        info = info,
        isDev = isDev,
        apkUrl = "$releasesUrl/download/$tag/${info.apk}",
        // What came after the installed build's commit: everything it gains, over several releases too
        changes = info.changes.filter { it.time > installed.commitTime }.sortedByDescending { it.time }
    )
}

/**
 * Finds newer versions of the app on its GitHub releases, and installs one: downloads the APK, checks it is the one the
 * release describes, and hands it to Android's installer (which also refuses one that isn't signed like this app).
 *
 * The check on opening ([checkOnOpening], Settings → About) offers an update in [offered], which App.kt shows as
 * UpdateSheet; "Check for updates" does the same, or says the app is up to date.
 */
class AppUpdates(
    context: Context,
    private val settings: AppSettings,
    private val messages: UiMessages,
    /** The data saver holds the check on opening (the button still checks) */
    private val holdBackground: () -> Boolean,
    private val releasesUrl: String = RELEASES_URL,
    /** What's installed (a test gives one of its own) */
    val installed: InstalledBuild = installedBuild(context),
    // A client of its own: nothing of the hosts' (their logins, their interceptors) goes to GitHub
    private val client: OkHttpClient = OkHttpClient.Builder().callTimeout(30, TimeUnit.SECONDS).build()
) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json = Json { ignoreUnknownKeys = true }
    private val updatesDir = File(appContext.cacheDir, "shared/updates")

    private val _offered = MutableStateFlow<AvailableUpdate?>(null)
    /** The update the sheet shows, until it's closed. */
    val offered: StateFlow<AvailableUpdate?> = _offered.asStateFlow()

    private val _checking = MutableStateFlow(false)
    val checking: StateFlow<Boolean> = _checking.asStateFlow()

    private val _download = MutableStateFlow<UpdateDownload>(UpdateDownload.None)
    val download: StateFlow<UpdateDownload> = _download.asStateFlow()
    private var downloadJob: Job? = null

    /** Looks for a newer version, and returns what it found. */
    suspend fun check(): UpdateResult = withContext(Dispatchers.IO) {
        try {
            // An update downloaded before is installed by now, or not wanted any more
            updatesDir.listFiles()?.forEach { it.delete() }
            val stable = fetch("$releasesUrl/latest/download/update.json")
            val dev = if (settings.includeDevUpdates.value) fetch("$releasesUrl/download/dev-build/update.json") else null
            settings.lastUpdateCheck = System.currentTimeMillis()
            chooseUpdate(installed, stable, dev, releasesUrl)?.let { UpdateResult.Available(it) } ?: UpdateResult.UpToDate
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            UpdateResult.Failed(e.message ?: "Couldn't reach GitHub.")
        }
    }

    /** A release's update.json, or null when it has none (a release from before the app looked for updates). */
    private fun fetch(url: String): UpdateInfo? {
        client.newCall(Request.Builder().url(url).build()).execute().use { response ->
            if (response.code == 404) return null
            if (!response.isSuccessful) throw IOException("GitHub answered HTTP ${response.code}.")
            val body = response.body
            if (body.contentLength() > MAX_UPDATE_JSON_BYTES) throw IOException("The release's description is too large.")
            return json.decodeFromString<UpdateInfo>(body.string())
        }
    }

    /**
     * The check when the app opens: when it's turned on, not more often than every 6 hours, and not with the data saver
     * holding it. A version the user skipped isn't offered again.
     */
    suspend fun checkOnOpening() {
        if (!installed.updatable || !settings.checkUpdatesOnOpening.value || holdBackground()) return
        if (System.currentTimeMillis() - settings.lastUpdateCheck < CHECK_INTERVAL_MILLIS) return
        val result = check()
        if (result is UpdateResult.Available && result.update.info.versionCode != settings.skippedUpdateCode) {
            _offered.value = result.update
        }
    }

    /** "Check for updates": offers the update (skipped or not), or says why there's none. */
    fun checkNow() {
        if (_checking.value) return
        _checking.value = true
        scope.launch {
            try {
                when (val result = check()) {
                    is UpdateResult.Available -> _offered.value = result.update
                    UpdateResult.UpToDate -> messages.show("Materialdrain is up to date.")
                    is UpdateResult.Failed -> messages.show("Couldn't look for updates: ${result.message}", long = true)
                }
            } finally {
                _checking.value = false
            }
        }
    }

    /** Closes the sheet ("Later"); the next check offers it again. */
    fun dismiss() {
        downloadJob?.cancel()
        _download.value = UpdateDownload.None
        _offered.value = null
    }

    /** Closes the sheet, and the check on opening doesn't offer this version again (a newer one, it does). */
    fun skip(update: AvailableUpdate) {
        settings.skippedUpdateCode = update.info.versionCode
        dismiss()
    }

    /** Downloads [update] and opens the installer with it. */
    fun install(update: AvailableUpdate) {
        if (_download.value is UpdateDownload.Running) return
        _download.value = UpdateDownload.Running(null)
        downloadJob = scope.launch {
            try {
                val apk = downloadApk(update)
                val uri = FileProvider.getUriForFile(appContext, appContext.packageName + FILE_PROVIDER_SUFFIX, apk)
                _download.value = UpdateDownload.None
                withContext(Dispatchers.Main) { openDownloadedFile(appContext, OpenableDownload(uri, APK_MIME_TYPE)) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _download.value = UpdateDownload.Failed(e.message ?: "The download failed.")
            }
        }
    }

    private fun downloadApk(update: AvailableUpdate): File {
        updatesDir.mkdirs()
        val target = File(updatesDir, update.info.apk.substringAfterLast('/'))
        val partial = File(updatesDir, target.name + ".part")
        val digest = MessageDigest.getInstance("SHA-256")
        client.newBuilder().callTimeout(0, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).build()
            .newCall(Request.Builder().url(update.apkUrl).build()).execute().use { response ->
                if (!response.isSuccessful) throw IOException("GitHub answered HTTP ${response.code}.")
                val total = response.body.contentLength().takeIf { it > 0 } ?: update.info.size.takeIf { it > 0 }
                response.body.byteStream().use { input ->
                    partial.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var done = 0L
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                            digest.update(buffer, 0, read)
                            done += read
                            _download.value = UpdateDownload.Running(total?.let { (done.toFloat() / it).coerceIn(0f, 1f) })
                        }
                    }
                }
            }
        val sha256 = digest.digest().joinToString("") { "%02x".format(it) }
        if (!sha256.equals(update.info.sha256, ignoreCase = true)) {
            partial.delete()
            throw IOException("The downloaded file isn't the one the release describes. Try again.")
        }
        // The installer would say so too, less clearly: another app, or not newer than this one
        val archive = appContext.packageManager.getPackageArchiveInfo(partial.path, 0)
        if (archive?.packageName != appContext.packageName || archive.longVersionCode <= installed.versionCode) {
            partial.delete()
            throw IOException("The downloaded file isn't a newer Materialdrain.")
        }
        target.delete()
        if (!partial.renameTo(target)) throw IOException("Couldn't save the update.")
        return target
    }
}
