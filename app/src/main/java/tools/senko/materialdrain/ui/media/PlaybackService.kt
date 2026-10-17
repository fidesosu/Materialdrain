package tools.senko.materialdrain.ui.media

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.graphics.drawable.toBitmap
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.BitmapLoader
import coil.imageLoader
import coil.request.SuccessResult
import com.google.common.util.concurrent.SettableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import tools.senko.materialdrain.ui.icons.filled.MusicNote
import androidx.compose.material.icons.Icons
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

/**
 * The player of songs which go on in the background (the Media setting "Play songs in the background"): it outlives the
 * song's details and the app's screen, with its controls in the notification and on the lock screen, which Media3 makes
 * for it. One song at a time, of any host (see createPlayer). The song player in a file's details reaches it through a
 * MediaController (see [rememberPlaybackController]); its item's id is the song's address.
 */
@androidx.annotation.OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {
    private var session: MediaSession? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()
        val player = createPlayer(this, isVideo = false)
        session = MediaSession.Builder(this, player)
            .setBitmapLoader(CoverBitmapLoader(this, scope))
            .setCallback(object : MediaSession.Callback {
                // The service is exported for the system's media controls, which only play, pause and seek: only the
                // app itself can choose what plays, so another app can't have it load an address of its choosing
                override fun onConnect(session: MediaSession, controller: MediaSession.ControllerInfo): MediaSession.ConnectionResult {
                    if (controller.packageName == packageName) return super.onConnect(session, controller)
                    val commands = MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS.buildUpon()
                        .remove(Player.COMMAND_SET_MEDIA_ITEM)
                        .remove(Player.COMMAND_CHANGE_MEDIA_ITEMS)
                        .build()
                    return MediaSession.ConnectionResult.AcceptedResultBuilder(session).setAvailablePlayerCommands(commands).build()
                }

                // An item sent by a controller may come without its address: it's its id
                override fun onAddMediaItems(
                    mediaSession: MediaSession,
                    controller: MediaSession.ControllerInfo,
                    mediaItems: MutableList<MediaItem>
                ): ListenableFuture<MutableList<MediaItem>> = Futures.immediateFuture(
                    mediaItems.map { item -> if (item.localConfiguration != null) item else item.buildUpon().setUri(item.mediaId).build() }
                        .toMutableList()
                )
            })
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    // Swiped away from the recent apps: a song that plays goes on, otherwise there's nothing to keep the player for
    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = session?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0 || player.playbackState == Player.STATE_ENDED) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        scope.cancel()
        session?.run {
            player.release()
            release()
        }
        session = null
        super.onDestroy()
    }
}

/**
 * A controller of [PlaybackService]'s player, null until it's connected (a moment). Letting it go (leaving the details)
 * doesn't stop the song: the service plays on.
 */
@Composable
internal fun rememberPlaybackController(): MediaController? {
    val context = LocalContext.current
    var controller by remember { mutableStateOf<MediaController?>(null) }
    DisposableEffect(Unit) {
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener(
            { if (!future.isCancelled) controller = runCatching { future.get() }.getOrNull() },
            ContextCompat.getMainExecutor(context)
        )
        onDispose {
            controller = null
            MediaController.releaseFuture(future)
        }
    }
    return controller
}

/** The size of a song's picture in the notification, in pixels. */
private const val ART_PX = 512

/** The extra of a song's metadata with the picture shown when its cover can't be loaded (see [rememberPlaceholderArt]). */
internal const val EXTRA_FALLBACK_ART = "tools.senko.materialdrain.FALLBACK_ART"

/**
 * Loads the pictures of the notification and the lock screen through the app's image loader, which reads what Media3's
 * own can't: the `audiocover://` covers and video frames (see MediaCovers), and a host's pictures that need its login.
 * A song's cover that can't be loaded, or one without any, shows its [EXTRA_FALLBACK_ART] instead.
 */
@UnstableApi
private class CoverBitmapLoader(private val context: Context, private val scope: CoroutineScope) : BitmapLoader {
    override fun supportsMimeType(mimeType: String): Boolean = true

    override fun decodeBitmap(data: ByteArray): ListenableFuture<Bitmap> = load(ByteBuffer.wrap(data), fallback = null)

    override fun loadBitmap(uri: Uri): ListenableFuture<Bitmap> = load(uri.toString(), fallback = null)

    override fun loadBitmapFromMetadata(metadata: MediaMetadata): ListenableFuture<Bitmap>? {
        val fallback = metadata.extras?.getByteArray(EXTRA_FALLBACK_ART)
        val source: Any = metadata.artworkData?.let { ByteBuffer.wrap(it) } ?: metadata.artworkUri?.toString()
            ?: fallback?.let { ByteBuffer.wrap(it) } ?: return null
        return load(source, fallback)
    }

    private fun load(source: Any, fallback: ByteArray?): ListenableFuture<Bitmap> {
        val future = SettableFuture.create<Bitmap>()
        scope.launch {
            val bitmap = runCatching { decode(source) }.getOrNull() ?: fallback?.let { runCatching { decode(ByteBuffer.wrap(it)) }.getOrNull() }
            if (bitmap != null) future.set(bitmap) else future.setException(IllegalArgumentException("No picture in $source"))
        }
        return future
    }

    // Not a hardware bitmap: the notification copies it to another process
    private suspend fun decode(source: Any): Bitmap? {
        val request = imageRequest(context, source) {
            size(ART_PX)
            allowHardware(false)
        }
        return (context.imageLoader.execute(request) as? SuccessResult)?.drawable?.toBitmap()
    }
}

/**
 * The picture of a song without a cover, as a PNG: its file tile (see FileIcon), the music note on the theme's colour,
 * for the notification and the lock screen.
 */
@Composable
internal fun rememberPlaceholderArt(): ByteArray {
    val painter = rememberVectorPainter(Icons.Filled.MusicNote)
    val container = MaterialTheme.colorScheme.tertiaryContainer
    val content = MaterialTheme.colorScheme.onTertiaryContainer
    return remember(painter, container, content) {
        val image = ImageBitmap(ART_PX, ART_PX)
        val size = Size(ART_PX.toFloat(), ART_PX.toFloat())
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(image), size) {
            drawRect(container)
            // As big as on the tile
            val glyph = ART_PX * 0.46f
            translate((ART_PX - glyph) / 2f, (ART_PX - glyph) / 2f) {
                with(painter) { draw(Size(glyph, glyph), colorFilter = ColorFilter.tint(content)) }
            }
        }
        ByteArrayOutputStream().also { image.asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
    }
}
