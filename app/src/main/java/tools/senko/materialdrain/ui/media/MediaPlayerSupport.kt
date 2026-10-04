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
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import tools.senko.materialdrain.AppContainer
import tools.senko.materialdrain.util.ExoPlayerCache

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
internal fun createMediaPlayer(context: Context, uri: Uri, apiKey: String?, isVideo: Boolean): ExoPlayer {
    val appContext = context.applicationContext
    val isRemote = uri.scheme == "http" || uri.scheme == "https"

    val dataSourceFactory: DataSource.Factory = if (isRemote) {
        val okHttpFactory = OkHttpDataSource.Factory(AppContainer.get(appContext as Application).okHttpClient)
        val headers = HostRequestAuth.headersFor(uri.toString())
        if (headers.isNotEmpty()) okHttpFactory.setDefaultRequestProperties(headers)
        CacheDataSource.Factory()
            .setCache(ExoPlayerCache.getInstance(appContext))
            .setUpstreamDataSourceFactory(okHttpFactory)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    } else {
        DefaultDataSource.Factory(appContext)
    }

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
        .apply { setMediaItem(MediaItem.fromUri(uri)) }
}

/**
 * A player which lives as long as the composable: it pauses when the app goes to the background and is
 * released afterwards. With [prepareNow] false nothing is loaded before the user presses play
 * (see [PlayerUiState.togglePlayPause]), so a preview which is never played costs no data.
 */
@Composable
fun rememberManagedPlayer(uri: Uri, apiKey: String?, isVideo: Boolean, prepareNow: Boolean): ExoPlayer {
    val context = LocalContext.current
    val player = remember(uri, apiKey) {
        createMediaPlayer(context, uri, apiKey, isVideo).apply {
            if (prepareNow) {
                prepare()
                playWhenReady = true
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
    var firstFrameRendered by mutableStateOf(false)

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

// Shared by the controls of the video and audio players
internal const val SEEK_STEP_MS = 10_000L
internal val PlaybackSpeeds = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)
internal fun speedLabel(speed: Float) = speed.toString().removeSuffix(".0") + "×"
