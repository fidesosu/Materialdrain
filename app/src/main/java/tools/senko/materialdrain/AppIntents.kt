package tools.senko.materialdrain

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat

/** Opens one of the app's screens: the launcher's shortcuts and the Quick Settings tile start the app with it. */
const val ACTION_OPEN_SCREEN = "tools.senko.materialdrain.action.OPEN_SCREEN"

/** The [Screen]'s name, for [ACTION_OPEN_SCREEN]. */
const val EXTRA_SCREEN = "screen"

/** Something the app was asked to open from outside, waiting until it's shown (and unlocked), see AppContainer.pendingOpen. */
sealed interface PendingOpen {
    /** A screen, from a shortcut or the tile */
    data class ToScreen(val screen: Screen) : PendingOpen

    /** A pixeldrain.com/u/… link: the file's details, on the built-in Pixeldrain */
    data class PixeldrainFile(val id: String) : PendingOpen

    /** A pixeldrain.com/l/… link: the list, on the built-in Pixeldrain */
    data class PixeldrainList(val id: String) : PendingOpen

    /** Text shared from another app (without a Pixeldrain link in it): to be uploaded as text */
    data class Text(val text: String) : PendingOpen
}

/**
 * Files shared from another app on their way into a folder of the Filesystem tab [tabId] of the host [hostId], see
 * AppContainer.filesystemShare: the tab shows a bar to upload them into the folder it's in (see SharedUploadBar).
 */
data class FilesystemShare(val uris: List<Uri>, val hostId: String, val tabId: String)

private val PIXELDRAIN_HOSTS = setOf("pixeldrain.com", "www.pixeldrain.com", "pixeldrain.net", "www.pixeldrain.net")

private val PIXELDRAIN_LINK = Regex("""https?://(?:www\.)?pixeldrain\.(?:com|net)/([ul])/([A-Za-z0-9]+)""")

/** What a link to [uri] opens: a Pixeldrain file or list, or null for any other address. */
fun pixeldrainLinkOf(uri: Uri): PendingOpen? {
    if (uri.host?.lowercase() !in PIXELDRAIN_HOSTS) return null
    val segments = uri.pathSegments
    val id = segments.getOrNull(1)?.takeIf { it.isNotBlank() } ?: return null
    return when (segments.firstOrNull()) {
        "u" -> PendingOpen.PixeldrainFile(id)
        "l" -> PendingOpen.PixeldrainList(id)
        else -> null
    }
}

/** The first Pixeldrain file or list link in [text], e.g. a message shared from a chat app, or null for none. */
fun pixeldrainLinkIn(text: String): PendingOpen? = PIXELDRAIN_LINK.find(text)?.let { match ->
    val (kind, id) = match.destructured
    if (kind == "u") PendingOpen.PixeldrainFile(id) else PendingOpen.PixeldrainList(id)
}

/** What [intent] asks the app to open, other than files to upload (those are AppContainer.pendingShares); null for nothing. */
fun pendingOpenOf(intent: Intent?): PendingOpen? = when (intent?.action) {
    ACTION_OPEN_SCREEN -> intent.getStringExtra(EXTRA_SCREEN)?.let { name -> Screen.entries.firstOrNull { it.name == name } }
        ?.let { PendingOpen.ToScreen(it) }
    Intent.ACTION_VIEW -> intent.data?.let(::pixeldrainLinkOf)
    // Shared text: a link in it is opened, any other text is uploaded as text. Shared files are handled on their own
    Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)?.takeIf { it.isNotBlank() && !intent.hasExtra(Intent.EXTRA_STREAM) }
        ?.let { text -> pixeldrainLinkIn(text) ?: PendingOpen.Text(text) }
    else -> null
}

/** The intent which opens [screen], for a shortcut or the tile. */
fun openScreenIntent(context: Context, screen: Screen): Intent =
    Intent(context, MainActivity::class.java)
        .setAction(ACTION_OPEN_SCREEN)
        .putExtra(EXTRA_SCREEN, screen.name)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)

/**
 * The shortcuts of the app's launcher icon (long-press it): Upload, Files and Filesystem. Made in code rather than in
 * XML, which would have to name the app's package as it's written there, and the debug build's differs.
 */
fun installLauncherShortcuts(context: Context) {
    // The app's own icon on each: the tabs' glyphs are light grey, made for the dark notification shade
    val shortcuts = listOf("upload" to Screen.Upload, "files" to Screen.Files, "filesystem" to Screen.Filesystem)
        .mapIndexed { rank, (id, screen) ->
            ShortcutInfoCompat.Builder(context, id)
                .setShortLabel(screen.title)
                .setIcon(IconCompat.createWithResource(context, R.mipmap.ic_launcher))
                .setIntent(openScreenIntent(context, screen))
                .setRank(rank)
                .build()
        }
    runCatching { ShortcutManagerCompat.setDynamicShortcuts(context, shortcuts) }
}
