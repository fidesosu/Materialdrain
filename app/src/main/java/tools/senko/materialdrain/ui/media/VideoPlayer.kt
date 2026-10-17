package tools.senko.materialdrain.ui.media

import tools.senko.materialdrain.ui.theme.AppShapes
import tools.senko.materialdrain.ui.theme.DarkTheme
import tools.senko.materialdrain.ui.components.floatingCardBorder
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.style.TextOverflow
import tools.senko.materialdrain.ui.preview.PreviewSamples
import tools.senko.materialdrain.ui.LocalPlayerSettings
import tools.senko.materialdrain.util.VideoPositions
import tools.senko.materialdrain.ui.icons.automirrored.filled.OpenInNew
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlin.math.roundToInt
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
import androidx.compose.material.icons.Icons
import tools.senko.materialdrain.ui.icons.automirrored.filled.ArrowBack
import tools.senko.materialdrain.ui.icons.automirrored.filled.KeyboardArrowRight
import tools.senko.materialdrain.ui.icons.filled.BrokenImage
import tools.senko.materialdrain.ui.icons.filled.Pause
import tools.senko.materialdrain.ui.icons.filled.PlayArrow
import tools.senko.materialdrain.ui.icons.filled.Repeat
import tools.senko.materialdrain.ui.icons.filled.MoreVert
import tools.senko.materialdrain.ui.icons.filled.Speed
import tools.senko.materialdrain.ui.icons.filled.Replay
import tools.senko.materialdrain.ui.icons.filled.Videocam
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
import tools.senko.materialdrain.ui.preview.PreviewTheme
import tools.senko.materialdrain.ui.preview.ThemePreviews
import tools.senko.materialdrain.ui.preview.ScreenPreviews
import tools.senko.materialdrain.ui.components.AppMenuPreview

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
                    val bitmap = rememberDecodedBitmap(thumbnailSource)
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap,
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
                    color = MediaScrim.Play,
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
    onSpeedChange: (Float) -> Unit,
    onOpenElsewhere: () -> Unit
) {
    // The controls are at the bottom of the screen: above the button, lined up with its end
    AppMenu(expanded = expanded, onDismiss = onDismiss, side = MenuSide.Above, edge = MenuEdge.End) {
        VideoSettingsEntries(speedPageOpen, onSpeedPageOpenChange, loopEnabled, onLoopChange, speed, onSpeedChange, onOpenElsewhere)
    }
}

/** The entries of [VideoSettingsMenu]: Loop, Speed and Open in another app, or the speeds. */
@Composable
private fun VideoSettingsEntries(
    speedPageOpen: Boolean,
    onSpeedPageOpenChange: (Boolean) -> Unit,
    loopEnabled: Boolean,
    onLoopChange: (Boolean) -> Unit,
    speed: Float,
    onSpeedChange: (Float) -> Unit,
    onOpenElsewhere: () -> Unit = {}
) {
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
            AppMenuDivider()
            // A player of its own may play what this one can't, or cast it
            AppMenuItem(
                text = "Open in another app",
                leadingIcon = Icons.AutoMirrored.Filled.OpenInNew,
                onClick = onOpenElsewhere
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
/** Which way the double taps skipped, and how far in all: taps one after another the same way add up. */
private data class SeekFeedback(val forward: Boolean, val seconds: Int)

/**
 * This feedback with one more skip of [stepMs] [forward] or back, or a new one when this one went the other way (or there
 * is none).
 */
private fun SeekFeedback?.plusSkip(forward: Boolean, stepMs: Long): SeekFeedback =
    SeekFeedback(forward, (if (this?.forward == forward) seconds else 0) + (stepMs / 1000).toInt())

/** What a swipe on the video changes (see PlayerSettings.gestures), and to how much of it, 0 to 1. */
private data class GestureLevel(val brightness: Boolean, val level: Float)

/** Hands the video to another app (a player of its own, or one that casts it), which may play what this one can't. */
private fun openVideoElsewhere(context: Context, uri: Uri) {
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, "video/*")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    try {
        context.startActivity(Intent.createChooser(intent, "Open the video with"))
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, "No other app on this device can play videos.", Toast.LENGTH_LONG).show()
    }
}

/**
 * The video full screen, in the app's dark colours whichever theme the app is in (see DarkTheme): the controls are
 * floating cards like the app's own, with the theme's colour on the play button and the seek bar. [title] is the file's
 * name, at the top beside the back button.
 */
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
internal fun FullscreenVideoPlayer(videoUri: Uri, thumbnailUrl: String?, apiKey: String?, onBack: () -> Unit, title: String? = null) {
    DarkTheme {
        VideoPlayerContent(videoUri, thumbnailUrl, apiKey, onBack, title)
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
private fun VideoPlayerContent(videoUri: Uri, thumbnailUrl: String?, apiKey: String?, onBack: () -> Unit, title: String?) {
    val context = LocalContext.current
    val settings = LocalPlayerSettings.current
    val inPreview = LocalInspectionMode.current
    // Where this video was left, to go on from there (the Media settings' "Resume videos")
    val positions = remember { VideoPositions(context) }
    val positionKey = videoUri.toString()
    val startPosition = remember(positionKey) { if (settings.resume && !inPreview) positions.positionOf(positionKey) else 0L }
    val player = rememberManagedPlayer(
        videoUri, apiKey, isVideo = true, prepareNow = true,
        autoplay = settings.autoplay, speed = settings.defaultSpeed, startPositionMs = startPosition
    )
    val state = rememberPlayerUiState(player)
    val stepMs = settings.seekStepMs
    val currentStepMs by rememberUpdatedState(stepMs)
    // Kept when the player closes, and when the app goes to the background (where it may be closed by the system). Before
    // the player is released: this effect comes after the player's own, so it's disposed of first
    val lifecycleOwner = LocalLifecycleOwner.current
    val resume = settings.resume && !inPreview
    DisposableEffect(player, lifecycleOwner, resume) {
        fun save() { if (resume) positions.save(positionKey, player.currentPosition, player.duration) }
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) save() }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            save()
        }
    }
    val colors = MaterialTheme.colorScheme
    // The cards of the controls, see-through like the app's floating cards, and the buttons on them
    val panelColor = colors.surfaceContainer.copy(alpha = 0.82f)
    val quietContainer = colors.onSurface.copy(alpha = 0.10f)

    var controlsVisible by remember { mutableStateOf(true) }
    var scrubbing by remember { mutableStateOf(false) }
    var scrubPositionMs by remember { mutableLongStateOf(0L) }
    var interactions by remember { mutableIntStateOf(0) }
    var seekFeedback by remember { mutableStateOf<SeekFeedback?>(null) }
    // The last feedback, shown while it fades out once it's gone
    var lastFeedback by remember { mutableStateOf(SeekFeedback(forward = true, seconds = 0)) }
    var settingsMenuOpen by remember { mutableStateOf(false) }
    var speedPageOpen by remember { mutableStateOf(false) }
    // While a swipe changes the brightness or the volume, how much of it there is (see PlayerSettings.gestures)
    var gestureLevel by remember { mutableStateOf<GestureLevel?>(null) }

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
    // Each skip changes the feedback (its seconds add up), so each one keeps it a while longer
    LaunchedEffect(seekFeedback) {
        seekFeedback?.let { lastFeedback = it }
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
            Box(modifier = Modifier.fillMaxSize().background(MediaScrim.Backdrop))
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

        // Tap to show or hide the controls, double tap on the sides to skip, in the middle to play or pause. With the
        // gestures on, a swipe up or down changes the brightness (left half) or the volume (right half); a swipe is moved,
        // so the tap underneath it doesn't count
        val window = (LocalView.current.parent as? DialogWindowProvider)?.window
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(if (settings.gestures) Modifier.pointerInput(window) {
                    val audio = context.getSystemService(AudioManager::class.java)
                    val maxVolume = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
                    var brightness = false
                    var level = 0f
                    detectVerticalDragGestures(
                        onDragStart = { start ->
                            brightness = start.x < size.width / 2f
                            level = if (brightness) {
                                // -1 is the system's own brightness, which this window starts from
                                window?.attributes?.screenBrightness?.takeIf { it >= 0f }
                                    ?: (Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, 128) / 255f)
                            } else {
                                audio.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / maxVolume
                            }
                        },
                        onDragEnd = { gestureLevel = null },
                        onDragCancel = { gestureLevel = null },
                        onVerticalDrag = { change, dragAmount ->
                            change.consume()
                            // A swipe across most of the height goes from nothing to all
                            level = (level - dragAmount / (size.height * 0.8f)).coerceIn(0f, 1f)
                            if (brightness) {
                                window?.let { w -> w.attributes = w.attributes.apply { screenBrightness = level.coerceAtLeast(0.01f) } }
                            } else {
                                audio.setStreamVolume(AudioManager.STREAM_MUSIC, (level * maxVolume).roundToInt(), 0)
                            }
                            gestureLevel = GestureLevel(brightness, level)
                        }
                    )
                } else Modifier)
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
                                    state.seekBy(-currentStepMs)
                                    seekFeedback = seekFeedback.plusSkip(forward = false, currentStepMs)
                                }
                                up.position.x > size.width * 2f / 3f -> {
                                    state.seekBy(currentStepMs)
                                    seekFeedback = seekFeedback.plusSkip(forward = true, currentStepMs)
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

        // A half oval against the side that was double tapped (a third of the width, as the taps are told apart), in
        // the theme's colour, with how far it went
        val reduceMotion = LocalReduceMotion.current
        val feedback = seekFeedback ?: lastFeedback
        val feedbackForward = feedback.forward
        AnimatedVisibility(
            visible = seekFeedback != null,
            enter = fadeIn(tween(if (reduceMotion) 0 else 100)),
            exit = fadeOut(tween(if (reduceMotion) 0 else 250)),
            modifier = Modifier
                .align(if (feedbackForward) Alignment.CenterEnd else Alignment.CenterStart)
                .fillMaxHeight()
                .fillMaxWidth(1f / 3f)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        colors.primary.copy(alpha = 0.18f),
                        if (feedbackForward) RoundedCornerShape(topStartPercent = 50, bottomStartPercent = 50)
                        else RoundedCornerShape(topEndPercent = 50, bottomEndPercent = 50)
                    )
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    SkipGlyph(feedbackForward, (stepMs / 1000).toInt(), colors.onSurface, size = 32.dp)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "${if (feedbackForward) "+" else "−"} ${feedback.seconds} s",
                        color = colors.onSurface,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
        }

        gestureLevel?.let { gesture ->
            Surface(
                shape = AppShapes.Large,
                color = colors.surfaceContainer.copy(alpha = 0.82f),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(top = 72.dp)
            ) {
                Text(
                    "${if (gesture.brightness) "Brightness" else "Volume"} ${(gesture.level * 100).roundToInt()}%",
                    color = colors.onSurface,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }

        if ((state.isBuffering || (!state.firstFrameRendered && state.error == null)) && state.error == null) {
            LinearProgressIndicator(
                modifier = Modifier.align(Alignment.Center).width(120.dp),
                color = colors.primary,
                trackColor = colors.primary.copy(alpha = 0.24f)
            )
        }

        state.error?.let { error ->
            Surface(
                shape = AppShapes.Card,
                color = colors.errorContainer,
                contentColor = colors.onErrorContainer,
                modifier = Modifier.align(Alignment.Center).padding(24.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(error.userMessage(), style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.height(12.dp))
                    MediaTextButton(
                        "Try again",
                        onClick = { state.retry() },
                        container = colors.onErrorContainer.copy(alpha = 0.12f),
                        contentColor = colors.onErrorContainer
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(tween(if (reduceMotion) 0 else 150)),
            exit = fadeOut(tween(if (reduceMotion) 0 else 150)),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // The preview goes under the status and navigation bars (see FullScreenPreview): the controls keep clear.
                // A shade from the top, so the name reads on a light video
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .background(Brush.verticalGradient(listOf(MediaScrim.Top, Color.Transparent)))
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 28.dp)
                    ) {
                        MediaIconButton(
                            icon = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            onClick = onBack,
                            container = panelColor,
                            contentColor = colors.onSurface
                        )
                        title?.let {
                            Spacer(Modifier.width(12.dp))
                            Text(
                                it,
                                color = Color.White,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                if (!state.isPlaying && !state.isBuffering && state.error == null) {
                    MediaIconButton(
                        icon = if (state.isEnded) Icons.Filled.Replay else Icons.Filled.PlayArrow,
                        contentDescription = if (state.isEnded) "Replay" else "Play",
                        onClick = { state.togglePlayPause(); interactions++ },
                        modifier = Modifier.align(Alignment.Center),
                        size = 64.dp,
                        container = colors.primary,
                        contentColor = colors.onPrimary
                    )
                }

                Surface(
                    shape = AppShapes.Card,
                    color = panelColor,
                    border = floatingCardBorder(),
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
                            Text(text = timeText, color = colors.onSurface, style = timeStyle, maxLines = 1, softWrap = false)
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
                            },
                            trackColor = colors.onSurface.copy(alpha = 0.18f),
                            bufferedColor = colors.onSurface.copy(alpha = 0.32f)
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                                if (timeBelow) {
                                    Text(text = timeText, color = colors.onSurface, style = timeStyle, maxLines = 1, softWrap = false)
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                MediaSkipButton(
                                    forward = false, stepMs = stepMs, onClick = { state.seekBy(-stepMs); interactions++ },
                                    container = quietContainer, contentColor = colors.onSurface
                                )
                                MediaIconButton(
                                    icon = if (state.playWhenReady && !state.isEnded) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                    contentDescription = if (state.playWhenReady && !state.isEnded) "Pause" else "Play",
                                    onClick = { state.togglePlayPause(); interactions++ },
                                    container = colors.primary,
                                    contentColor = colors.onPrimary
                                )
                                MediaSkipButton(
                                    forward = true, stepMs = stepMs, onClick = { state.seekBy(stepMs); interactions++ },
                                    container = quietContainer, contentColor = colors.onSurface
                                )
                            }
                            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                                MediaIconButton(
                                    Icons.Filled.MoreVert, "Playback settings", { speedPageOpen = false; settingsMenuOpen = true },
                                    container = quietContainer, contentColor = colors.onSurface
                                )
                                VideoSettingsMenu(
                                    expanded = settingsMenuOpen,
                                    onDismiss = { settingsMenuOpen = false },
                                    speedPageOpen = speedPageOpen,
                                    onSpeedPageOpenChange = { speedPageOpen = it },
                                    loopEnabled = videoLoop.enabled,
                                    onLoopChange = { videoLoop.onChange(it); interactions++ },
                                    speed = state.speed,
                                    onSpeedChange = { state.changeSpeed(it); interactions++ },
                                    onOpenElsewhere = {
                                        settingsMenuOpen = false
                                        player.pause()
                                        openVideoElsewhere(context, videoUri)
                                    }
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

// ---- Previews ----

/** Without a thumbnail; with one, it's drawn as a picture is (see LayeredPreviewImage), which the preview can't load. */
@ThemePreviews
@Composable
private fun InlineVideoPreviewPreview() {
    PreviewTheme(padding = 16.dp) {
        InlineVideoPreview(thumbnailSource = null, contentDescription = "Video", apiKey = null, onFullScreenClick = {})
    }
}

/** Paused with its controls shown (the preview's player loads nothing, see rememberManagedPlayer). */
@ScreenPreviews
@Composable
private fun FullscreenVideoPlayerPreview() {
    PreviewTheme {
        FullscreenVideoPlayer(videoUri = Uri.EMPTY, thumbnailUrl = null, apiKey = null, onBack = {}, title = PreviewSamples.video.name)
    }
}

/** The playback settings: Loop and Speed, and the speeds. */
@ThemePreviews
@Composable
private fun VideoSettingsMenuPreview() {
    PreviewTheme(padding = 8.dp) {
        Row {
            AppMenuPreview { VideoSettingsEntries(false, {}, loopEnabled = true, onLoopChange = {}, speed = 1.5f, onSpeedChange = {}) }
            AppMenuPreview { VideoSettingsEntries(true, {}, loopEnabled = true, onLoopChange = {}, speed = 1.5f, onSpeedChange = {}) }
        }
    }
}
