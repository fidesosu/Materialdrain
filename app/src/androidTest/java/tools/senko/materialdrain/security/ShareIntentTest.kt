package tools.senko.materialdrain.security

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.StrictMode
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import tools.senko.materialdrain.MainActivity
import tools.senko.materialdrain.screenshots.AppDriver
import tools.senko.materialdrain.screenshots.Screenshots
import java.io.File

/**
 * S7: a "share" from another app names the files the app is to read, which it then reads with its own rights. One that
 * names a file of the app's own (its settings, its logins) must not be taken, or the app could be made to upload it.
 */
@RunWith(AndroidJUnit4::class)
class ShareIntentTest {

    private val app = AppDriver()
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @After
    fun tearDown() {
        app.container.pendingShares.value = emptyList()
        app.reset()
    }

    @Test
    fun aShareOfTheAppsOwnFileIsNotTaken() {
        val secret = File(context.filesDir, "secret-probe.txt").apply { writeText("private to the app") }
        val uri = Uri.fromFile(secret)
        app.start()
        app.tapTab("Files")
        app.waitText("Sunset over the bay")
        app.container.pendingShares.value = emptyList()

        // As an app would which doesn't mind sending a file:// address (an old target SDK, or its checks turned off)
        val policy = StrictMode.getVmPolicy()
        StrictMode.setVmPolicy(StrictMode.VmPolicy.Builder().build())
        try {
            context.startActivity(
                Intent(Intent.ACTION_SEND)
                    .setClassName(context, MainActivity::class.java.name)
                    .setType("text/plain")
                    .putExtra(Intent.EXTRA_STREAM, uri)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } finally {
            StrictMode.setVmPolicy(policy)
        }
        val deadline = System.currentTimeMillis() + 10_000
        while (app.container.pendingShares.value.isEmpty() && System.currentTimeMillis() < deadline) Thread.sleep(200)
        Thread.sleep(1000)
        Screenshots.capture("security/s7-share-of-own-file")

        val taken = uri in app.container.pendingShares.value
        val readable = runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() } }.getOrNull()
        assertFalse("The app took a share of its own private file ($uri), which reads as \"$readable\"", taken)
    }
}
