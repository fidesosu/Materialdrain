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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import tools.senko.materialdrain.ui.icons.filled.MusicNote
import androidx.compose.material.icons.Icons
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import android.content.Intent
import android.os.Bundle
import androidx.media3.common.ForwardingPlayer
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ShuffleOrder.DefaultShuffleOrder
import androidx.media3.session.SessionCommand
import androidx.media3.common.FlagSet
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.IntentFilter
import tools.senko.materialdrain.AppContainer
import androidx.media3.session.SessionError
import androidx.media3.session.SessionResult
import kotlin.random.Random
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
import tools.senko.materialdrain.R

/**
 * The player of songs which go on in the background (the Media setting "Play songs in the background"): it outlives the
 * song's details and the app's screen, with its controls in the notification and on the lock screen, which Media3 makes
 * for it. One song at a time, of any host (see createPlayer). The song player in a file's details reaches it through a
 * MediaController (see [rememberPlaybackController]); its item's id is the song's address.
 */
@androidx.annotation.OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {
    private var session: MediaSession? = null
    private var player: LockScreenPrivatePlayer? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /**
     * Whether the song is kept from the device's lock screen now: while the app lock is on, from the screen going off
     * until the device is unlocked. What plays is then "Materialdrain" to everything that shows it (the lock screen's
     * player, which reads the session whatever the notification says, and the notification), and the system's controls
     * can't read the queue; they still play, pause and skip.
     */
    private var hiddenFromLockScreen = false
    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val lockOn = AppContainer.get(application).appSettings.biometricLock.value
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    unlockWatch?.cancel()
                    hiddenFromLockScreen = lockOn
                    player?.hidden = hiddenFromLockScreen
                    applyLockScreenPrivacy()
                }
                Intent.ACTION_USER_PRESENT -> showOnUnlock()
                // On: the lock screen, until the device is unlocked
                else -> watchForUnlock()
            }
        }
    }

    /**
     * While the screen is on and the song hidden: whether the device is unlocked yet, every half second. The broadcast of
     * it can come seconds late (or not to this app), and listening to the lock itself is only for the system's apps.
     */
    private var unlockWatch: Job? = null

    private fun watchForUnlock() {
        unlockWatch?.cancel()
        if (!hiddenFromLockScreen) return
        val keyguard = getSystemService(KeyguardManager::class.java)
        unlockWatch = scope.launch {
            while (keyguard.isKeyguardLocked) delay(UNLOCK_CHECK_MS)
            showOnUnlock()
        }
    }

    private fun showOnUnlock() {
        unlockWatch?.cancel()
        if (!hiddenFromLockScreen) return
        hiddenFromLockScreen = false
        player?.hidden = false
        applyLockScreenPrivacy()
    }

    /** What the system's controls may do: play, pause and seek, never choose what plays, nor read the queue while it's hidden. */
    private fun systemPlayerCommands(): Player.Commands =
        MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS.buildUpon()
            .remove(Player.COMMAND_SET_MEDIA_ITEM)
            .remove(Player.COMMAND_CHANGE_MEDIA_ITEMS)
            .apply { if (hiddenFromLockScreen) remove(Player.COMMAND_GET_TIMELINE) }
            .build()

    private fun applyLockScreenPrivacy() {
        val session = session ?: return
        session.connectedControllers.filter { it.packageName != packageName }.forEach {
            session.setAvailableCommands(it, MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS, systemPlayerCommands())
        }
    }

    override fun onCreate() {
        super.onCreate()
        val exoPlayer = createPlayer(this, isVideo = false)
        val player = LockScreenPrivatePlayer(ReshufflingPlayer(exoPlayer), getString(R.string.app_name), getString(R.string.media_playing)).also { player = it }
        session = MediaSession.Builder(this, player)
            .setBitmapLoader(CoverBitmapLoader(this, scope))
            .setCallback(object : MediaSession.Callback {
                // The service is exported for the system's media controls (the notification, the lock screen, headsets
                // through the system), which only play, pause and seek. No other app connects: each song's id is its
                // address, which is the file itself on some hosts, and none may choose what plays either. Only the app
                // itself sets the queue and the order it's shuffled in (see moveInPlayOrder)
                override fun onConnect(session: MediaSession, controller: MediaSession.ControllerInfo): MediaSession.ConnectionResult {
                    if (controller.packageName == packageName) {
                        return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                            .setAvailableSessionCommands(
                                MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon().add(SetShuffleOrderCommand).build()
                            )
                            .build()
                    }
                    if (!controller.isTrusted && !session.isMediaNotificationController(controller)) {
                        return MediaSession.ConnectionResult.reject()
                    }
                    return MediaSession.ConnectionResult.AcceptedResultBuilder(session).setAvailablePlayerCommands(systemPlayerCommands()).build()
                }

                override fun onCustomCommand(
                    session: MediaSession,
                    controller: MediaSession.ControllerInfo,
                    customCommand: SessionCommand,
                    args: Bundle
                ): ListenableFuture<SessionResult> {
                    if (customCommand.customAction != SetShuffleOrderCommand.customAction) return super.onCustomCommand(session, controller, customCommand, args)
                    val order = args.getIntArray(EXTRA_SHUFFLE_ORDER)
                    // Only a whole order of the queue as it is now: one sent before a change to it no longer fits
                    val fits = order != null && order.size == exoPlayer.mediaItemCount && order.sorted() == List(order.size) { it }
                    if (!fits) return Futures.immediateFuture(SessionResult(SessionError.ERROR_BAD_VALUE))
                    exoPlayer.setShuffleOrder(DefaultShuffleOrder(order, Random.nextLong()))
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
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
        val screen = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        ContextCompat.registerReceiver(this, screenReceiver, screen, ContextCompat.RECEIVER_NOT_EXPORTED)
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
        unregisterReceiver(screenReceiver)
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
 * The player as the session shows it: while [hidden], what plays has no name of its own ("Materialdrain", "Playing") and
 * no cover, for the device's lock screen (see PlaybackService.hiddenFromLockScreen). Everything that shows it is told when
 * that changes, as for a song's metadata changing.
 */
@UnstableApi
private class LockScreenPrivatePlayer(player: Player, hiddenTitle: String, hiddenArtist: String) : ForwardingPlayer(player) {
    private val listeners = mutableListOf<Player.Listener>()

    var hidden = false
        set(value) {
            if (field == value) return
            field = value
            val metadata = mediaMetadata
            val events = Player.Events(FlagSet.Builder().add(Player.EVENT_MEDIA_METADATA_CHANGED).build())
            listeners.toList().forEach {
                it.onMediaMetadataChanged(metadata)
                it.onEvents(this, events)
            }
        }

    override fun addListener(listener: Player.Listener) {
        listeners += listener
        super.addListener(listener)
    }

    override fun removeListener(listener: Player.Listener) {
        listeners -= listener
        super.removeListener(listener)
    }

    override fun getMediaMetadata(): MediaMetadata = if (hidden) HIDDEN_METADATA else super.getMediaMetadata()

    override fun getCurrentMediaItem(): MediaItem? =
        super.getCurrentMediaItem()?.let { if (hidden) it.buildUpon().setMediaMetadata(HIDDEN_METADATA).build() else it }

    /** What plays while it's hidden: the app's name, and that something plays */
    private val HIDDEN_METADATA: MediaMetadata = MediaMetadata.Builder().setTitle(hiddenTitle).setArtist(hiddenArtist).build()
}

/**
 * The background player, which shuffles the queue anew each time shuffle is turned on, with the song playing first: a
 * player's own shuffled order is made once for the queue, so turning shuffle off and on again gave the same one, and the
 * song playing could be anywhere in it (the songs before it counting as played).
 */
@UnstableApi
private class ReshufflingPlayer(private val exoPlayer: ExoPlayer) : ForwardingPlayer(exoPlayer) {
    override fun setShuffleModeEnabled(shuffleModeEnabled: Boolean) {
        if (shuffleModeEnabled && !exoPlayer.shuffleModeEnabled) exoPlayer.reshuffle()
        super.setShuffleModeEnabled(shuffleModeEnabled)
    }
}

/** A new random order of the queue, starting with the song playing. */
@UnstableApi
private fun ExoPlayer.reshuffle() {
    val count = mediaItemCount
    if (count == 0) return
    val current = currentMediaItemIndex.coerceIn(0, count - 1)
    val order = intArrayOf(current) + (0 until count).filter { it != current }.shuffled().toIntArray()
    setShuffleOrder(DefaultShuffleOrder(order, Random.nextLong()))
}

/**
 * The app's command to [PlaybackService] for the order the queue plays in while it's shuffled, its indexes in
 * [EXTRA_SHUFFLE_ORDER] (see moveInPlayOrder): the order of a queue rearranged while shuffled.
 */
internal val SetShuffleOrderCommand = SessionCommand("tools.senko.materialdrain.SET_SHUFFLE_ORDER", Bundle.EMPTY)

internal const val EXTRA_SHUFFLE_ORDER = "tools.senko.materialdrain.SHUFFLE_ORDER"

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

/** How often the song hidden on the lock screen looks whether the device is unlocked yet (see PlaybackService.unlockWatch). */
private const val UNLOCK_CHECK_MS = 500L

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
