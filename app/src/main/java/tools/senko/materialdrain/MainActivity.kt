package tools.senko.materialdrain

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge // Import for enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import tools.senko.materialdrain.auth.AppLockOverlay
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen // Added import
import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.tooling.preview.Preview
import tools.senko.materialdrain.ui.theme.MaterialdrainTheme

/** How long after the first back press at the top level a second one still closes the app. */
private const val EXIT_CONFIRM_WINDOW_MS = 2000L

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // Added before any screen's handler, so it only gets the back press when no screen takes it (the newest
        // enabled handler goes first). The first press at the top level says so, a second one closes the app.
        var lastBackPressMs = 0L
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val now = SystemClock.uptimeMillis()
                if (now - lastBackPressMs < EXIT_CONFIRM_WINDOW_MS) {
                    finish()
                } else {
                    lastBackPressMs = now
                    Toast.makeText(this@MainActivity, "Press back again to exit", Toast.LENGTH_SHORT).show()
                }
            }
        })
        // A fresh start may be a share; a recreated activity (e.g. after rotation) must not upload the same files again
        if (savedInstanceState == null) handleShare(intent)
        setContent {
            // No stretch effect when overscrolling, anywhere in the app (dialogs and popups included)
            CompositionLocalProvider(LocalOverscrollFactory provides null) {
                MaterialdrainTheme {
                    Box(modifier = Modifier.fillMaxSize()) {
                        MaterialdrainScreen()
                        AppLockOverlay(this@MainActivity, AppContainer.get(application).appLock)
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleShare(intent)
    }

    /** Takes the files of a "share" from another app, to be uploaded. Other intents are ignored. */
    private fun handleShare(intent: Intent?) {
        val uris: List<Uri> = when (intent?.action) {
            Intent.ACTION_SEND -> listOfNotNull(sharedStream(intent))
            Intent.ACTION_SEND_MULTIPLE -> sharedStreams(intent)
            else -> return
        }
        if (uris.isNotEmpty()) AppContainer.get(application).pendingShares.value = uris
    }

    @Suppress("DEPRECATION")
    private fun sharedStream(intent: Intent): Uri? =
        if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        else intent.getParcelableExtra(Intent.EXTRA_STREAM)

    @Suppress("DEPRECATION")
    private fun sharedStreams(intent: Intent): List<Uri> =
        (if (Build.VERSION.SDK_INT >= 33) intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
        else intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM)).orEmpty()

    // Finished transfers only need a notification when the user is not looking at the app
    override fun onStart() {
        super.onStart()
        AppContainer.get(application).transferRegistry.appInForeground = true
        AppContainer.get(application).appLock.onStarted()
    }

    override fun onStop() {
        AppContainer.get(application).transferRegistry.appInForeground = false
        AppContainer.get(application).appLock.onStopped()
        super.onStop()
    }
}

@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    MaterialdrainTheme {
        MaterialdrainScreen()
    }
}
