package tools.senko.materialdrain

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge // Import for enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import tools.senko.materialdrain.auth.AppLockOverlay
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen // Added import
import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import android.view.WindowManager
import tools.senko.materialdrain.ui.theme.MaterialdrainTheme
import tools.senko.materialdrain.ui.theme.isDarkTheme

/** How long after the first back press at the top level a second one still closes the app. */
private const val EXIT_CONFIRM_WINDOW_MS = 2000L

/** The navigation bar's scrims where the system draws one (3-button navigation), as enableEdgeToEdge has them. */
private const val LIGHT_NAVIGATION_SCRIM = 0xE6FFFFFF.toInt()
private const val DARK_NAVIGATION_SCRIM = 0x801B1B1B.toInt()

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val container = AppContainer.get(application)
        // Before the first frame, so not even that one can be captured (see the settings below for changes)
        applyScreenshotBlock(container.appSettings.blockScreenshots.value, container.appSettings.biometricLock.value)
        // Added before any screen's handler, so it only gets the back press when no screen takes it (the newest
        // enabled handler goes first). The first press at the top level says so, a second one closes the app; with
        // the confirmation turned off (Look and feel), the first one does
        var lastBackPressMs = 0L
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (!container.appSettings.confirmExit.value) {
                    finish()
                    return
                }
                val now = SystemClock.uptimeMillis()
                if (now - lastBackPressMs < EXIT_CONFIRM_WINDOW_MS) {
                    finish()
                } else {
                    lastBackPressMs = now
                    Toast.makeText(this@MainActivity, "Press back again to exit", Toast.LENGTH_SHORT).show()
                }
            }
        })
        // A fresh start may be a share or a link; a recreated activity (e.g. after rotation) must not take them again
        if (savedInstanceState == null) {
            handleIntent(intent)
            installLauncherShortcuts(this)
        }
        setContent {
            // No stretch effect when overscrolling, anywhere in the app (dialogs and popups included)
            val theme by AppContainer.get(application).appSettings.theme.collectAsState()
            val dark = isDarkTheme(theme)
            // The icons of the status and navigation bars follow the app's theme rather than the system's, which can
            // differ when the theme is set to light or dark
            LaunchedEffect(dark) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT) { dark },
                    navigationBarStyle = SystemBarStyle.auto(LIGHT_NAVIGATION_SCRIM, DARK_NAVIGATION_SCRIM) { dark }
                )
            }
            val blockScreenshots by container.appSettings.blockScreenshots.collectAsState()
            val lockOn by container.appSettings.biometricLock.collectAsState()
            LaunchedEffect(blockScreenshots, lockOn) { applyScreenshotBlock(blockScreenshots, lockOn) }
            val locked by container.appLock.locked.collectAsState()
            // Where the app is while it's locked: its screens are kept (what's open, scrolled to...), not drawn
            val screens = rememberSaveableStateHolder()
            CompositionLocalProvider(LocalOverscrollFactory provides null) {
                MaterialdrainTheme(theme, darkTheme = dark) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        // While locked, the app isn't there at all, only the lock: nothing of it is drawn or read (by
                        // accessibility services too), its dialogs and menus (windows of their own, which the lock
                        // can't cover) are closed, and nothing is loaded from the host until it's unlocked
                        if (!locked) screens.SaveableStateProvider("app") { MaterialdrainScreen() }
                        AppLockOverlay(this@MainActivity, container.appLock)
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    /**
     * Takes the files of a "share" from another app, to be uploaded, and what else the app was asked to open: a Pixeldrain
     * link, a shortcut's screen, shared text (see PendingOpen). Other intents are ignored.
     */
    private fun handleIntent(intent: Intent?) {
        val container = AppContainer.get(application)
        pendingOpenOf(intent)?.let { container.pendingOpen.value = it }
        val uris: List<Uri> = when (intent?.action) {
            Intent.ACTION_SEND -> listOfNotNull(sharedStream(intent))
            Intent.ACTION_SEND_MULTIPLE -> sharedStreams(intent)
            else -> return
        }
        // Only files of other apps: a share naming one of this app's own (file:// in its data, or its own providers)
        // would have it read, and upload, its private files
        val shared = uris.filter { it.scheme == ContentResolver.SCHEME_CONTENT && it.authority?.startsWith(packageName) != true }
        if (shared.isNotEmpty()) container.pendingShares.value = shared
    }

    /**
     * Screenshots, screen recordings (adb's too) and the recent apps' picture of the app show nothing while [block] is on
     * (Privacy and security). Its dialogs and menus take the flag from this window.
     *
     * With the app lock on ([lock]), the recent apps don't show it either: their picture is taken as the app leaves, before
     * it locks. Android 13 and up can leave out just that picture; before, only the same flag as [block] can.
     */
    private fun applyScreenshotBlock(block: Boolean, lock: Boolean) {
        val secure = block || (lock && Build.VERSION.SDK_INT < 33)
        if (secure) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        if (Build.VERSION.SDK_INT >= 33) setRecentsScreenshotEnabled(!lock)
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
