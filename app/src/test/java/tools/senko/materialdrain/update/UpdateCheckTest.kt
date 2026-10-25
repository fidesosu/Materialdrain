package tools.senko.materialdrain.update

import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import tools.senko.materialdrain.UiMessages
import tools.senko.materialdrain.settings.AppSettings

/**
 * Which release the app offers, and what it says the release brings: the newest of the stable release and the
 * development build, only when it's newer than the installed build, with the changes the installed build doesn't have.
 * GitHub's releases are served by a MockWebServer.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class UpdateCheckTest {

    private val installed = InstalledBuild("1.6.2", versionCode = 1000, commitTime = 5000, updatable = true, isDev = false)

    private fun release(code: Long, name: String = "1.6.$code", changes: List<UpdateChange> = emptyList()) =
        UpdateInfo(name, code, apk = "Materialdrain-$name.apk", sha256 = "00", changes = changes)

    // ---- Which release ----

    @Test
    fun aNewerStableReleaseIsOffered() {
        val update = chooseUpdate(installed, release(1001), null, "https://x/releases")!!
        assertEquals(1001, update.info.versionCode)
        assertEquals(false, update.isDev)
        assertEquals("https://x/releases/download/v1.6.1001/Materialdrain-1.6.1001.apk", update.apkUrl)
    }

    @Test
    fun theSameOrAnOlderVersionIsNot() {
        assertNull(chooseUpdate(installed, release(1000), null))
        assertNull(chooseUpdate(installed, release(999), release(998)))
    }

    @Test
    fun theNewerOfStableAndDevIsOffered() {
        val dev = chooseUpdate(installed, release(1001), release(1002, "1.6.3-dev.4"), "https://x/releases")!!
        assertTrue(dev.isDev)
        assertEquals("https://x/releases/download/dev-build/Materialdrain-1.6.3-dev.4.apk", dev.apkUrl)
        // A stable release made after the development build is newer than it
        val stable = chooseUpdate(installed, release(1003), release(1002, "1.6.3-dev.4"))!!
        assertEquals(false, stable.isDev)
    }

    @Test
    fun onlyTheChangesTheInstalledBuildDoesntHaveAreListedNewestFirst() {
        val changes = listOf(UpdateChange(4000, "old"), UpdateChange(6000, "newer"), UpdateChange(5000, "same commit"), UpdateChange(7000, "newest"))
        val update = chooseUpdate(installed, release(1001, changes = changes), null)!!
        assertEquals(listOf("newest", "newer"), update.changes.map { it.text })
    }

    // ---- Against GitHub ----

    private lateinit var server: MockWebServer
    private lateinit var settings: AppSettings
    private lateinit var updates: AppUpdates

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
        val context = ApplicationProvider.getApplicationContext<android.app.Application>()
        settings = AppSettings(context)
        updates = AppUpdates(context, settings, UiMessages(), holdBackground = { false }, releasesUrl = server.url("/releases").toString().trimEnd('/'), installed = installed)
    }

    @After
    fun tearDown() = server.shutdown()

    private fun serve(vararg responses: Pair<String, UpdateInfo?>) {
        val byPath = responses.toMap()
        server.dispatcher = object : okhttp3.mockwebserver.Dispatcher() {
            override fun dispatch(request: okhttp3.mockwebserver.RecordedRequest): MockResponse {
                val path = request.path!!
                if (path !in byPath) return MockResponse().setResponseCode(404)
                val info = byPath[path] ?: return MockResponse().setResponseCode(404)
                return MockResponse().setBody(Json.encodeToString(info))
            }
        }
    }

    private val stablePath = "/releases/latest/download/update.json"
    private val devPath = "/releases/download/dev-build/update.json"

    @Test
    fun aReleaseWithoutUpdateJsonIsUpToDate() = runBlocking {
        serve()
        assertEquals(UpdateResult.UpToDate, updates.check())
    }

    @Test
    fun devBuildsAreLookedForOnlyWhenAsked() = runBlocking {
        val code = installed.versionCode
        serve(stablePath to release(code - 1), devPath to release(code + 1, "1.6.9-dev.1"))
        settings.setIncludeDevUpdates(false)
        assertEquals(UpdateResult.UpToDate, updates.check())
        assertTrue(server.takeRequestPaths().none { it == devPath })
        settings.setIncludeDevUpdates(true)
        val result = updates.check()
        assertTrue(result is UpdateResult.Available && result.update.isDev)
    }

    @Test
    fun aSkippedVersionIsntOfferedOnOpeningButANewerOneIs() = runBlocking {
        val code = installed.versionCode
        settings.setIncludeDevUpdates(false)
        settings.setCheckUpdatesOnOpening(true)
        serve(stablePath to release(code + 1))
        settings.skippedUpdateCode = code + 1
        settings.lastUpdateCheck = 0
        updates.checkOnOpening()
        assertNull(updates.offered.value)

        serve(stablePath to release(code + 2))
        settings.lastUpdateCheck = 0
        updates.checkOnOpening()
        assertEquals(code + 2, updates.offered.value?.info?.versionCode)
    }

    @Test
    fun theCheckOnOpeningWaitsBetweenChecksAndForItsSetting() = runBlocking {
        val code = installed.versionCode
        settings.setIncludeDevUpdates(false)
        serve(stablePath to release(code + 1))
        settings.setCheckUpdatesOnOpening(false)
        settings.lastUpdateCheck = 0
        updates.checkOnOpening()
        assertEquals(0, server.requestCount)

        settings.setCheckUpdatesOnOpening(true)
        settings.lastUpdateCheck = System.currentTimeMillis()
        updates.checkOnOpening()
        assertEquals(0, server.requestCount)
    }

    @Test
    fun aDownloadWhichIsntTheReleasesApkIsRefused() = runBlocking {
        val update = AvailableUpdate(release(installed.versionCode + 1), false, server.url("/apk").toString(), emptyList())
        server.dispatcher = object : okhttp3.mockwebserver.Dispatcher() {
            override fun dispatch(request: okhttp3.mockwebserver.RecordedRequest) = MockResponse().setBody("not the APK")
        }
        updates.install(update)
        val deadline = System.currentTimeMillis() + 10_000
        while (updates.download.value !is UpdateDownload.Failed && System.currentTimeMillis() < deadline) Thread.sleep(20)
        val failed = updates.download.value
        assertTrue("$failed", failed is UpdateDownload.Failed && "isn't the one the release describes" in failed.message)
    }

    private fun MockWebServer.takeRequestPaths(): List<String> =
        generateSequence { takeRequest(10, java.util.concurrent.TimeUnit.MILLISECONDS)?.path }.toList()
}
