package tools.senko.materialdrain.ui.media

import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.imageLoader
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds
import tools.senko.materialdrain.ui.LocalBlurredBackdrop
import tools.senko.materialdrain.ui.LocalReduceMotion
import tools.senko.materialdrain.ui.LocalVideoLoop
import tools.senko.materialdrain.ui.components.AppMenu
import tools.senko.materialdrain.ui.components.AppMenuDivider
import tools.senko.materialdrain.ui.components.AppMenuItem
import tools.senko.materialdrain.ui.components.AppMenuPages
import tools.senko.materialdrain.ui.components.MenuEdge
import tools.senko.materialdrain.ui.components.MenuSide
import tools.senko.materialdrain.util.formatDurationMillis

private const val CONTROLS_HIDE_DELAY_MS = 3_000L
/** Width of the three buttons in the middle of the video controls: 3 buttons of 44 dp with 8 dp between them. */
private val CENTER_CONTROLS_WIDTH = 148.dp
@Composable
fun InlineVideoPreview(
    thumbnailSource: Any?, // Can be ByteArray, String (URL), Uri, etc.
    contentDescription: String,
    apiKey: String?,
    modifier: Modifier = Modifier,
    /** The space around the card; the file details leave none above it, so it starts right under the top bar. */
    outerPadding: PaddingValues = PaddingValues(vertical = 8.dp),
    onFullScreenClick: () -> Unit
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .padding(outerPadding),
        shape = MediaCardShape
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            when (thumbnailSource) {
                is ByteArray -> {
                    val bitmap = remember(thumbnailSource) {
                        try { BitmapFactory.decodeByteArray(thumbnailSource, 0, thumbnailSource.size) } catch (e: Exception) { Log.e(TAG_COIL, "Error decoding ByteArray to Bitmap", e); null }
                    }
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = contentDescription,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Icon(Icons.Filled.BrokenImage, contentDescription = "Error displaying video thumbnail", modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.error)
                    }
                }
                is String, is Uri -> LayeredPreviewImage(
                    fullSource = thumbnailSource,
                    thumbnailSource = null,
                    apiKey = apiKey,
                    contentDescription = contentDescription,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                null -> Icon(Icons.Filled.Videocam, contentDescription = "Video thumbnail unavailable", modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                else -> {
                    Icon(Icons.Filled.BrokenImage, contentDescription = "Unsupported thumbnail type", modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.error)
                    Log.w(TAG_COIL, "Unsupported thumbnailSource type: ${thumbnailSource::class.java.name}")
                }
            }
            // Tapping opens the player, the play button says so
            Box(modifier = Modifier.fillMaxSize().clickable(onClick = onFullScreenClick), contentAlignment = Alignment.Center) {
                Surface(
                    shape = MediaControlShape,
                    color = Color.Black.copy(alpha = 0.55f),
                    modifier = Modifier.size(56.dp)
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = "Play video", tint = Color.White, modifier = Modifier.padding(12.dp))
                }
            }
        }
    }
}
/**
 * The settings menu of the video player, root/ Loop, Speed/ (the speeds). It sits in the box of the button which opens
 * it, and grows up out of that button. Choosing loop or a speed leaves the menu open.
 */
@Composable
private fun BoxScope.VideoSettingsMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    speedPageOpen: Boolean,
    onSpeedPageOpenChange: (Boolean) -> Unit,
    loopEnabled: Boolean,
    onLoopChange: (Boolean) -> Unit,
    speed: Float,
    onSpeedChange: (Float) -> Unit
) {
    // The controls are at the bottom of the screen: above the button, lined up with its end
    AppMenu(expanded = expanded, onDismiss = onDismiss, side = MenuSide.Above, edge = MenuEdge.End) {
        AppMenuPages(
            showSecondary = speedPageOpen,
            primary = {
                AppMenuItem(
                    text = "Loop",
                    active = loopEnabled,
                    leadingIcon = Icons.Filled.Repeat,
                    onClick = { onLoopChange(!loopEnabled) }
                )
                AppMenuItem(
                    text = "Speed",
                    leadingIcon = Icons.Filled.Speed,
                    trailingText = speedLabel(speed),
                    trailingIcon = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    onClick = { onSpeedPageOpenChange(true) }
                )
            },
            secondary = {
                AppMenuItem(
                    text = "Speed",
                    leadingIcon = Icons.AutoMirrored.Filled.ArrowBack,
                    onClick = { onSpeedPageOpenChange(false) }
                )
                AppMenuDivider()
                PlaybackSpeeds.forEach { option ->
                    AppMenuItem(
                        text = speedLabel(option),
                        active = option == speed,
                        onClick = { onSpeedChange(option) }
                    )
                }
            }
        )
    }
}
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
internal fun FullscreenVideoPlayer(videoUri: Uri, thumbnailUrl: String?, apiKey: String?, onBack: () -> Unit) {
    val player = rememberManagedPlayer(videoUri, apiKey, isVideo = true, prepareNow = true)
    val state = rememberPlayerUiState(player)
    val context = LocalContext.current

    var controlsVisible by remember { mutableStateOf(true) }
    var scrubbing by remember { mutableStateOf(false) }
    var scrubPositionMs by remember { mutableLongStateOf(0L) }
    var interactions by remember { mutableIntStateOf(0) }
    var seekFeedback by remember { mutableStateOf<String?>(null) }
    var settingsMenuOpen by remember { mutableStateOf(false) }
    var speedPageOpen by remember { mutableStateOf(false) }

    // One setting for all videos: when it is on, the player starts over by itself when the video ends
    val videoLoop = LocalVideoLoop.current
    LaunchedEffect(player, videoLoop.enabled) {
        player.repeatMode = if (videoLoop.enabled) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
    }

    // The controls disappear while the video plays and nothing happens, and stay when something needs them
    val keepControls = !state.isPlaying || scrubbing || settingsMenuOpen || state.error != null
    LaunchedEffect(controlsVisible, keepControls, interactions) {
        if (controlsVisible && !keepControls) {
            delay(CONTROLS_HIDE_DELAY_MS.milliseconds)
            controlsVisible = false
        }
    }
    LaunchedEffect(seekFeedback) {
        if (seekFeedback != null) {
            delay(700.milliseconds)
            seekFeedback = null
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        // Like a fullscreen image: with the blurred backdrop setting on, the thumbnail blurred behind the video fills
        // what the fitted video leaves free (it shows around the video, not through it), darkened so the video and the
        // controls stand out; with it off, the background stays black
        if (thumbnailUrl != null && LocalBlurredBackdrop.current) {
            BlurredBackdrop(thumbnailUrl, apiKey, context.imageLoader, Modifier.fillMaxSize())
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)))
        }

        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    setShutterBackgroundColor(Color.Transparent.toArgb())
                    keepScreenOn = true
                    this.player = player
                }
            },
            update = { it.player = player },
            modifier = Modifier.fillMaxSize()
        )

        // Something to look at while the video is loading: the colours of its thumbnail (whose framing is not
        // that of the video, so it isn't shown sharp)
        if (!state.firstFrameRendered && thumbnailUrl != null) {
            BlurredBackdrop(thumbnailUrl, apiKey, context.imageLoader, Modifier.fillMaxSize())
        }

        // Tap to show or hide the controls, double tap on the sides to skip, in the middle to play or pause
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    // detectTapGestures only reports a single tap after the time in which a second tap could still
                    // follow, which made showing the controls feel slow. Here a tap is acted on right away, and a
                    // second tap shortly after it (near it) is the double tap.
                    var lastTapUptime = 0L
                    var lastTapPosition = Offset.Zero
                    awaitEachGesture {
                        awaitFirstDown()
                        val up = waitForUpOrCancellation() ?: return@awaitEachGesture
                        val isDoubleTap = up.uptimeMillis - lastTapUptime <= viewConfiguration.doubleTapTimeoutMillis &&
                            (up.position - lastTapPosition).getDistance() < viewConfiguration.touchSlop * 8
                        if (isDoubleTap) {
                            lastTapUptime = 0L
                            when {
                                up.position.x < size.width / 3f -> {
                                    state.seekBy(-SEEK_STEP_MS)
                                    seekFeedback = "− 10 s"
                                }
                                up.position.x > size.width * 2f / 3f -> {
                                    state.seekBy(SEEK_STEP_MS)
                                    seekFeedback = "+ 10 s"
                                }
                                else -> state.togglePlayPause()
                            }
                        } else {
                            lastTapUptime = up.uptimeMillis
                            lastTapPosition = up.position
                            controlsVisible = !controlsVisible
                        }
                        interactions++
                    }
                }
        )

        seekFeedback?.let { text ->
            Surface(
                shape = MediaControlShape,
                color = Color.Black.copy(alpha = 0.6f),
                modifier = Modifier.align(Alignment.Center)
            ) {
                Text(text, color = Color.White, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            }
        }

        if ((state.isBuffering || (!state.firstFrameRendered && state.error == null)) && state.error == null) {
            LinearProgressIndicator(
                modifier = Modifier.align(Alignment.Center).width(120.dp),
                color = Color.White,
                trackColor = Color.White.copy(alpha = 0.25f)
            )
        }

        state.error?.let { error ->
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = Color.Black.copy(alpha = 0.75f),
                modifier = Modifier.align(Alignment.Center).padding(24.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(error.userMessage(), color = Color.White, style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.height(12.dp))
                    MediaTextButton("Try again", onClick = { state.retry() })
                }
            }
        }

        val reduceMotion = LocalReduceMotion.current
        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(tween(if (reduceMotion) 0 else 150)),
            exit = fadeOut(tween(if (reduceMotion) 0 else 150)),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // The preview goes under the status and navigation bars (see FullScreenPreview): the controls keep clear
                MediaIconButton(
                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    onClick = onBack,
                    modifier = Modifier.align(Alignment.TopStart).windowInsetsPadding(WindowInsets.safeDrawing).padding(16.dp),
                    container = Color.Black.copy(alpha = 0.5f)
                )

                if (!state.isPlaying && !state.isBuffering && state.error == null) {
                    MediaIconButton(
                        icon = if (state.isEnded) Icons.Filled.Replay else Icons.Filled.PlayArrow,
                        contentDescription = if (state.isEnded) "Replay" else "Play",
                        onClick = { state.togglePlayPause(); interactions++ },
                        modifier = Modifier.align(Alignment.Center),
                        size = 64.dp,
                        container = Color.Black.copy(alpha = 0.55f)
                    )
                }

                Surface(
                    shape = MediaCardShape,
                    color = Color.Black.copy(alpha = 0.6f),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))
                        .fillMaxWidth()
                        .padding(12.dp)
                ) {
                    BoxWithConstraints {
                    val shownPosition = if (scrubbing) scrubPositionMs else state.positionMs

                    // The time goes below the seek bar, left of the buttons, when it fits there on one line (a slot
                    // is what is left of the row on each side of the centered buttons); if not it goes above the bar.
                    val timeStyle = MaterialTheme.typography.labelMedium
                    val textMeasurer = rememberTextMeasurer()
                    val density = LocalDensity.current
                    val duration = formatDurationMillis(state.durationMs)
                    val widestTimeWidth = with(density) { textMeasurer.measure("$duration / $duration", timeStyle).size.width.toDp() }
                    val timeSlotWidth = (maxWidth - 24.dp - CENTER_CONTROLS_WIDTH) / 2
                    val timeBelow = widestTimeWidth + 4.dp <= timeSlotWidth
                    val timeText = "${formatDurationMillis(shownPosition)} / $duration"

                    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                        if (!timeBelow) {
                            Text(text = timeText, color = Color.White, style = timeStyle, maxLines = 1, softWrap = false)
                            Spacer(Modifier.height(2.dp))
                        }
                        MediaSeekBar(
                            positionMs = shownPosition,
                            durationMs = state.durationMs,
                            bufferedMs = state.bufferedMs,
                            onSeekStarted = { scrubbing = true; interactions++ },
                            onSeekPreview = { scrubPositionMs = it },
                            onSeekFinished = {
                                state.seekTo(it)
                                scrubbing = false
                                interactions++
                            }
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                                if (timeBelow) {
                                    Text(text = timeText, color = Color.White, style = timeStyle, maxLines = 1, softWrap = false)
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                MediaIconButton(Icons.Filled.Replay10, "Back 10 seconds", { state.seekBy(-SEEK_STEP_MS); interactions++ })
                                MediaIconButton(
                                    icon = if (state.playWhenReady && !state.isEnded) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                    contentDescription = if (state.playWhenReady && !state.isEnded) "Pause" else "Play",
                                    onClick = { state.togglePlayPause(); interactions++ },
                                    container = Color.White.copy(alpha = 0.28f)
                                )
                                MediaIconButton(Icons.Filled.Forward10, "Forward 10 seconds", { state.seekBy(SEEK_STEP_MS); interactions++ })
                            }
                            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                                MediaIconButton(Icons.Filled.MoreVert, "Playback settings", { speedPageOpen = false; settingsMenuOpen = true })
                                VideoSettingsMenu(
                                    expanded = settingsMenuOpen,
                                    onDismiss = { settingsMenuOpen = false },
                                    speedPageOpen = speedPageOpen,
                                    onSpeedPageOpenChange = { speedPageOpen = it },
                                    loopEnabled = videoLoop.enabled,
                                    onLoopChange = { videoLoop.onChange(it); interactions++ },
                                    speed = state.speed,
                                    onSpeedChange = { state.changeSpeed(it); interactions++ }
                                )
                            }
                        }
                    }
                    }
                }
            }
        }
    }
}
