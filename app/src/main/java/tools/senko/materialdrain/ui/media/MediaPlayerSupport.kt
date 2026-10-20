package tools.senko.materialdrain.ui.media

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import tools.senko.materialdrain.AppContainer
import tools.senko.materialdrain.util.ExoPlayerCache
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.media3.common.SimpleBasePlayer
import android.os.Looper
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

/**
 * The headers the active host needs for one of its URLs, installed by [AppContainer]. Images, thumbnails and media
 * requests all ask it, so the login of whichever host is active reaches them.
 */
object HostRequestAuth {
    @Volatile
    var headersFor: (url: String) -> Map<String, String> = { emptyMap() }
}

// Playback starts as soon as a second of media is buffered (the default waits for two and a half),
// and buffering goes on for up to fifty seconds ahead.
private const val MIN_BUFFER_MS = 15_000
private const val MAX_BUFFER_MS = 50_000
private const val BUFFER_FOR_PLAYBACK_MS = 1_000
private const val BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS = 2_500

/**
 * Creates a player for [uri]: local files are read directly, everything else is streamed over the OkHttp client of
 * the API (one connection pool, HTTP/2) and cached on disk. The player is not prepared yet.
 */
@androidx.annotation.OptIn(UnstableApi::class)
internal fun createMediaPlayer(context: Context, uri: Uri, isVideo: Boolean): ExoPlayer =
    createPlayer(context, isVideo).apply { setMediaItem(MediaItem.fromUri(uri)) }

/**
 * A player without media yet, which plays local files directly and streams the rest over the OkHttp client of the API
 * (one connection pool, HTTP/2), cached on disk. The login a URL needs (see [HostRequestAuth]) is added to each request
 * when it's made, so one player can play the files of any host: the one of the background songs (PlaybackService) does.
 */
@androidx.annotation.OptIn(UnstableApi::class)
internal fun createPlayer(context: Context, isVideo: Boolean): ExoPlayer {
    val appContext = context.applicationContext

    val okHttpFactory = OkHttpDataSource.Factory(AppContainer.get(appContext as Application).okHttpClient)
    val withLogin = ResolvingDataSource.Factory(okHttpFactory) { spec ->
        val headers = HostRequestAuth.headersFor(spec.uri.toString())
        if (headers.isEmpty()) spec else spec.withAdditionalHeaders(headers)
    }
    val remote = CacheDataSource.Factory()
        .setCache(ExoPlayerCache.getInstance(appContext))
        .setUpstreamDataSourceFactory(withLogin)
        .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    // Files and content URIs are read directly; the rest (http, https) goes through [remote]
    val dataSourceFactory: DataSource.Factory = DefaultDataSource.Factory(appContext, remote)

    val loadControl = DefaultLoadControl.Builder()
        .setBufferDurationsMs(MIN_BUFFER_MS, MAX_BUFFER_MS, BUFFER_FOR_PLAYBACK_MS, BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS)
        .setPrioritizeTimeOverSizeThresholds(true)
        .build()

    return ExoPlayer.Builder(appContext)
        .setMediaSourceFactory(ProgressiveMediaSource.Factory(dataSourceFactory))
        .setLoadControl(loadControl)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(if (isVideo) C.AUDIO_CONTENT_TYPE_MOVIE else C.AUDIO_CONTENT_TYPE_MUSIC)
                .build(),
            /* handleAudioFocus = */ true
        )
        .setHandleAudioBecomingNoisy(true)
        .build()
}

/**
 * A player which lives as long as the composable: it pauses when the app goes to the background and is
 * released afterwards. With [prepareNow] false nothing is loaded before the user presses play
 * (see [PlayerUiState.togglePlayPause]), so a preview which is never played costs no data. A prepared one plays at once
 * when [autoplay] (see the Media settings). It starts at [speed].
 */
@Composable
fun rememberManagedPlayer(
    uri: Uri,
    apiKey: String?,
    isVideo: Boolean,
    prepareNow: Boolean,
    autoplay: Boolean = true,
    speed: Float = 1f,
    /** Where it starts, e.g. where the video was left (see VideoPositions); 0 for the start */
    startPositionMs: Long = 0L
): Player {
    val context = LocalContext.current
    // A preview (in Android Studio) has no app around it to stream with, and plays nothing anyway
    val inPreview = LocalInspectionMode.current
    val player = remember<Player>(uri, apiKey) {
        if (inPreview) return@remember PreviewPlayer()
        createMediaPlayer(context, uri, isVideo).apply {
            if (speed != 1f) playbackParameters = PlaybackParameters(speed)
            if (startPositionMs > 0) seekTo(startPositionMs)
            if (prepareNow) {
                prepare()
                playWhenReady = autoplay
            }
        }
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(player, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) player.pause()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            player.release()
        }
    }
    return player
}

/**
 * The player of a preview in Android Studio (see [rememberManagedPlayer]): paused at 1:14 of a 3:33 media, which it never
 * loads. Whatever it's asked to do (play, seek, change the speed) it leaves as it is.
 */
@androidx.annotation.OptIn(UnstableApi::class)
private class PreviewPlayer : SimpleBasePlayer(Looper.getMainLooper()) {
    private val state = State.Builder()
        .setAvailableCommands(Player.Commands.Builder().addAll(Player.COMMAND_GET_CURRENT_MEDIA_ITEM, Player.COMMAND_GET_TIMELINE).build())
        .setPlaylist(listOf(MediaItemData.Builder("preview").setDurationUs(213_000_000L).build()))
        .setContentPositionMs(74_000L)
        .setContentBufferedPositionMs { 120_000L }
        .build()

    override fun getState(): State = state

    override fun handleRelease(): ListenableFuture<*> = Futures.immediateVoidFuture()
}

/** A player for previews in Android Studio, see [PreviewPlayer]. */
internal fun previewPlayer(): Player = PreviewPlayer()

/** What the controls of a player show, kept up to date from the player. */
@Stable
class PlayerUiState(val player: Player) {
    var isPlaying by mutableStateOf(player.isPlaying)
    var playbackState by mutableIntStateOf(player.playbackState)
    var playWhenReady by mutableStateOf(player.playWhenReady)
    var positionMs by mutableLongStateOf(0L)
    var durationMs by mutableLongStateOf(0L)
    var bufferedMs by mutableLongStateOf(0L)
    var speed by mutableFloatStateOf(1f)
    var error by mutableStateOf<PlaybackException?>(null)
    // The preview's player shows no video, but is as one that has (see PreviewPlayer): paused, not loading
    var firstFrameRendered by mutableStateOf(player is PreviewPlayer)

    val isBuffering: Boolean get() = playWhenReady && playbackState == Player.STATE_BUFFERING
    val isEnded: Boolean get() = playbackState == Player.STATE_ENDED

    fun refresh() {
        positionMs = player.currentPosition.coerceAtLeast(0L)
        bufferedMs = player.bufferedPosition.coerceAtLeast(0L)
        val duration = player.duration
        durationMs = if (duration == C.TIME_UNSET) 0L else duration.coerceAtLeast(0L)
    }

    /** Plays, pauses, replays a finished media and loads media which wasn't loaded yet. */
    fun togglePlayPause() {
        when {
            isEnded -> {
                player.seekTo(0)
                player.play()
            }
            player.playWhenReady -> player.pause()
            else -> {
                if (player.playbackState == Player.STATE_IDLE) player.prepare()
                player.play()
            }
        }
    }

    fun seekTo(positionMs: Long) {
        val limit = if (durationMs > 0) durationMs else Long.MAX_VALUE
        player.seekTo(positionMs.coerceIn(0L, limit))
        refresh()
    }

    fun seekBy(deltaMs: Long) = seekTo(player.currentPosition + deltaMs)

    fun changeSpeed(newSpeed: Float) {
        player.playbackParameters = PlaybackParameters(newSpeed)
    }

    fun retry() {
        error = null
        player.prepare()
        player.play()
    }
}

@Composable
fun rememberPlayerUiState(player: Player): PlayerUiState {
    val state = remember(player) { PlayerUiState(player) }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) { state.isPlaying = isPlaying }
            override fun onPlaybackStateChanged(playbackState: Int) {
                state.playbackState = playbackState
                state.refresh()
            }
            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) { state.playWhenReady = playWhenReady }
            override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) { state.speed = playbackParameters.speed }
            override fun onPlayerError(error: PlaybackException) { state.error = error }
            override fun onRenderedFirstFrame() { state.firstFrameRendered = true }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    // The position is not announced by the player, it has to be asked for
    LaunchedEffect(player) {
        while (isActive) {
            state.refresh()
            delay(200)
        }
    }
    return state
}

/** "the media can't be played" in words which mean something to the user. */
fun PlaybackException.userMessage(): String = when (errorCode) {
    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> "Couldn't reach Pixeldrain. Check your connection."
    PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS -> "Pixeldrain refused to send this file (it may be rate limited)."
    PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND -> "The file was not found."
    PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
    PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
    PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED -> "This file can't be played on this device."
    else -> "Playback failed."
}

// Shared by the controls of the video and audio players; how far they skip is a setting (PlayerSettings.seekStepMs)
internal val PlaybackSpeeds = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)
internal fun speedLabel(speed: Float) = speed.toString().removeSuffix(".0") + "×"
