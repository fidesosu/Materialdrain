package tools.senko.materialdrain.ui.media

import tools.senko.materialdrain.ui.theme.AppShapes
import tools.senko.materialdrain.ui.theme.DarkTheme
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
import android.content.res.Resources
import android.content.Intent
import android.media.AudioManager
import android.provider.Settings
import android.widget.Toast
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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import android.app.Activity
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.media3.common.C
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import tools.senko.materialdrain.ui.icons.filled.Audiotrack
import tools.senko.materialdrain.ui.icons.filled.Crop
import tools.senko.materialdrain.ui.icons.filled.ScreenRotation
import tools.senko.materialdrain.ui.icons.filled.Subtitles
import java.util.Locale
import kotlin.math.abs
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil3.imageLoader
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
import androidx.compose.ui.res.stringResource
import tools.senko.materialdrain.R
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.text.font.FontWeight
import tools.senko.materialdrain.ui.components.FadeText
import tools.senko.materialdrain.ui.icons.filled.FastForward
import androidx.compose.ui.zIndex
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

private const val CONTROLS_HIDE_DELAY_MS = 3_000L
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
                        Icon(Icons.Filled.BrokenImage, contentDescription = stringResource(R.string.media_video_thumbnail_error), modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.error)
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
                null -> Icon(Icons.Filled.Videocam, contentDescription = stringResource(R.string.media_video_thumbnail_unavailable), modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                else -> {
                    Icon(Icons.Filled.BrokenImage, contentDescription = stringResource(R.string.media_video_thumbnail_unsupported), modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.error)
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
                    Icon(Icons.Filled.PlayArrow, contentDescription = stringResource(R.string.media_play_video), tint = Color.White, modifier = Modifier.padding(12.dp))
                }
            }
        }
    }
}
/** The pages of the video player's settings menu (see [VideoSettingsEntries]), besides its first. */
private enum class VideoMenuPage { Speed, Subtitles, Audio }

/** A track the player can pick, for the Subtitles and Audio pages: its [group], its [index] in it, and its [label]. */
private class TrackChoice(val group: Tracks.Group, val index: Int, val label: String, val selected: Boolean)

/**
 * The tracks of [type] (C.TRACK_TYPE_TEXT, C.TRACK_TYPE_AUDIO) the player has and can play, named for the menu: by
 * their own label, else their language, else their number.
 */
@androidx.annotation.OptIn(UnstableApi::class)
private fun Tracks.choicesOf(type: Int, res: Resources): List<TrackChoice> {
    var number = 0
    return groups.filter { it.type == type }.flatMap { group ->
        (0 until group.length).filter { group.isTrackSupported(it) }.map { index ->
            number++
            val format = group.getTrackFormat(index)
            val language = format.language?.takeIf { it.isNotBlank() && it != C.LANGUAGE_UNDETERMINED }
                ?.let { Locale.forLanguageTag(it).displayName }
            TrackChoice(group, index, format.label ?: language ?: res.getString(R.string.media_track, number), group.isTrackSelected(index))
        }
    }
}

/** Plays [choice] of the tracks of [type]; null turns that type off (the subtitles' Off). */
@androidx.annotation.OptIn(UnstableApi::class)
private fun Player.selectTrack(type: Int, choice: TrackChoice?) {
    trackSelectionParameters = trackSelectionParameters.buildUpon().apply {
        setTrackTypeDisabled(type, choice == null)
        if (choice != null) setOverrideForType(TrackSelectionOverride(choice.group.mediaTrackGroup, choice.index))
    }.build()
}

/** What the video player's settings menu shows and changes (see [VideoSettingsEntries]). */
private class VideoMenuChoices(
    val loop: Boolean,
    val onLoop: (Boolean) -> Unit,
    val speed: Float,
    val onSpeed: (Float) -> Unit,
    val subtitles: List<TrackChoice> = emptyList(),
    val onSubtitle: (TrackChoice?) -> Unit = {},
    val audio: List<TrackChoice> = emptyList(),
    val onAudio: (TrackChoice) -> Unit = {},
    val onOpenElsewhere: () -> Unit = {}
)

/**
 * The settings menu of the video player: Loop, Speed, Subtitles and Audio (where the video has subtitles, or more than one
 * sound track), each with its own page, and Open in another app. It sits in the box of the button which opens it, at the
 * top of the screen, and opens down from it. Choosing on a page leaves the menu open, so the change can be seen.
 */
@Composable
private fun BoxScope.VideoSettingsMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    page: VideoMenuPage?,
    onPageChange: (VideoMenuPage?) -> Unit,
    choices: VideoMenuChoices
) {
    AppMenu(expanded = expanded, onDismiss = onDismiss, side = MenuSide.Below, edge = MenuEdge.End) {
        VideoSettingsEntries(page, onPageChange, choices)
    }
}

/** The entries of [VideoSettingsMenu]: its first page ([page] null), or the speeds, the subtitles or the sound tracks. */
@Composable
private fun VideoSettingsEntries(page: VideoMenuPage?, onPageChange: (VideoMenuPage?) -> Unit, choices: VideoMenuChoices) {
    // The page shown on the second level, kept while going back so it slides out as it was
    var secondPage by remember { mutableStateOf(page ?: VideoMenuPage.Speed) }
    fun open(to: VideoMenuPage) {
        secondPage = to
        onPageChange(to)
    }
    val back: @Composable (String) -> Unit = { name ->
        AppMenuItem(text = name, leadingIcon = Icons.AutoMirrored.Filled.ArrowBack, onClick = { onPageChange(null) })
        AppMenuDivider()
    }
    // Each page is measured even while another is shown, so the menu keeps one width
    val speedPage: @Composable ColumnScope.() -> Unit = {
        back(stringResource(R.string.media_speed))
        PlaybackSpeeds.forEach { option ->
            AppMenuItem(text = speedLabel(option), active = option == choices.speed, onClick = { choices.onSpeed(option) })
        }
    }
    val subtitlesPage: @Composable ColumnScope.() -> Unit = {
        if (choices.subtitles.isNotEmpty()) {
            back(stringResource(R.string.media_subtitles))
            AppMenuItem(text = stringResource(R.string.media_off), active = choices.subtitles.none { it.selected }, onClick = { choices.onSubtitle(null) })
            choices.subtitles.forEach { track ->
                AppMenuItem(text = track.label, active = track.selected, onClick = { choices.onSubtitle(track) })
            }
        }
    }
    val audioPage: @Composable ColumnScope.() -> Unit = {
        if (choices.audio.size > 1) {
            back(stringResource(R.string.media_audio))
            choices.audio.forEach { track ->
                AppMenuItem(text = track.label, active = track.selected, onClick = { choices.onAudio(track) })
            }
        }
    }
    val pages = mapOf(VideoMenuPage.Speed to speedPage, VideoMenuPage.Subtitles to subtitlesPage, VideoMenuPage.Audio to audioPage)
    AppMenuPages(
        showSecondary = page != null,
        primary = {
            AppMenuItem(
                text = stringResource(R.string.media_loop),
                active = choices.loop,
                leadingIcon = Icons.Filled.Repeat,
                onClick = { choices.onLoop(!choices.loop) }
            )
            AppMenuItem(
                text = stringResource(R.string.media_speed),
                leadingIcon = Icons.Filled.Speed,
                trailingText = speedLabel(choices.speed),
                trailingIcon = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                onClick = { open(VideoMenuPage.Speed) }
            )
            if (choices.subtitles.isNotEmpty()) {
                AppMenuItem(
                    text = stringResource(R.string.media_subtitles),
                    leadingIcon = Icons.Filled.Subtitles,
                    trailingText = choices.subtitles.firstOrNull { it.selected }?.label ?: stringResource(R.string.media_off),
                    trailingIcon = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    onClick = { open(VideoMenuPage.Subtitles) }
                )
            }
            if (choices.audio.size > 1) {
                AppMenuItem(
                    text = stringResource(R.string.media_audio),
                    leadingIcon = Icons.Filled.Audiotrack,
                    trailingText = choices.audio.firstOrNull { it.selected }?.label,
                    trailingIcon = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    onClick = { open(VideoMenuPage.Audio) }
                )
            }
            AppMenuDivider()
            // A player of its own may play what this one can't, or cast it
            AppMenuItem(
                text = stringResource(R.string.media_open_in_another_app),
                leadingIcon = Icons.AutoMirrored.Filled.OpenInNew,
                onClick = choices.onOpenElsewhere
            )
        },
        secondary = { pages.getValue(secondPage)() },
        otherPages = { pages.filterKeys { it != secondPage }.values.forEach { it() } }
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
        context.startActivity(Intent.createChooser(intent, context.getString(R.string.settings_open_the_video_with)))
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, context.getString(R.string.settings_no_other_app_on_this), Toast.LENGTH_LONG).show()
    }
}

/** The activity this is in (through the dialog's own context), whose orientation the rotate button turns. */
private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/** How the touches on the video began (see VideoPlayerContent's gestures). */
private enum class TouchStart { Tap, Hold, Drag, Pinch, Gone }

/** How far a pinch zooms the video in, at most. */
private const val MAX_VIDEO_ZOOM = 4f

/** Below this, a pinch lets go of the zoom altogether. */
private const val ZOOM_SNAP_BACK = 1.05f

/** How long a double tap's ripple takes to spread. */
private const val SEEK_RIPPLE_MS = 500

/** How long the controls take to fade in or out. */
private const val CONTROLS_FADE_MS = 180

/** How much the video is dimmed while the controls are over it, so they read over a light picture. */
private const val CONTROLS_DIM = 0.3f

/** How fast the video plays while it's held down. */
private const val HOLD_SPEED = 2f

/** How far a swipe across the whole width of the video seeks, at most: less for a shorter video, all of it then. */
private const val SWIPE_SEEK_SPAN_MS = 180_000L

/** [pan] kept to what a video zoomed by [zoom] has beyond the edges of [size], so it never shows past its own edge. */
private fun clampPan(pan: Offset, zoom: Float, size: IntSize): Offset {
    val maxX = size.width * (zoom - 1f) / 2f
    val maxY = size.height * (zoom - 1f) / 2f
    return Offset(pan.x.coerceIn(-maxX, maxX), pan.y.coerceIn(-maxY, maxY))
}

/**
 * The video full screen, in the app's dark colours whichever theme the app is in (see DarkTheme). The controls come and
 * go with a tap: the name and the buttons for the screen along the top, the big round ones for playing in the middle,
 * the seek bar along the bottom, each on a shade from its edge so it reads on a light video. [title] is the file's name.
 */
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
internal fun FullscreenVideoPlayer(videoUri: Uri, thumbnailUrl: String?, apiKey: String?, onBack: () -> Unit, title: String? = null) {
    DarkTheme {
        VideoPlayerContent(videoUri, thumbnailUrl, apiKey, onBack, title)
    }
}

/**
 * What [FullscreenVideoPlayer] shows. The touches on the video:
 * - a tap shows or hides the controls; a double tap on the sides skips back or on, in the middle plays or pauses;
 * - held down, it plays at [HOLD_SPEED] until it's let go;
 * - a pinch zooms in (up to [MAX_VIDEO_ZOOM]), and a drag moves the zoomed video about;
 * - with the gestures on (Media settings), a swipe up or down changes the brightness (left half) or the volume (right
 *   half), and a swipe sideways seeks, as far as the finger goes ([SWIPE_SEEK_SPAN_MS] across the whole width). With them
 *   off, a swipe sideways is left to what's around the player: the swipe to the file beside this one.
 */
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
private fun VideoPlayerContent(videoUri: Uri, thumbnailUrl: String?, apiKey: String?, onBack: () -> Unit, title: String?) {
    val context = LocalContext.current
    // The whole screen for the video: the status bar goes away (a swipe from the top shows it for a moment), and comes back
    // when the player closes. On the preview's own window when it has one, else the app's
    val view = LocalView.current
    DisposableEffect(view) {
        val window = (view.parent as? DialogWindowProvider)?.window ?: context.findActivity()?.window
        val bars = window?.let { WindowCompat.getInsetsController(it, view) }
        bars?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        bars?.hide(WindowInsetsCompat.Type.statusBars())
        onDispose { bars?.show(WindowInsetsCompat.Type.statusBars()) }
    }
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

    var controlsVisible by remember { mutableStateOf(true) }
    var scrubbing by remember { mutableStateOf(false) }
    var scrubPositionMs by remember { mutableLongStateOf(0L) }
    var interactions by remember { mutableIntStateOf(0) }
    var seekFeedback by remember { mutableStateOf<SeekFeedback?>(null) }
    // The last feedback, shown while it fades out once it's gone
    var lastFeedback by remember { mutableStateOf(SeekFeedback(forward = true, seconds = 0)) }
    // Where the last double tap was, which its ripple spreads from
    var feedbackOrigin by remember { mutableStateOf(Offset.Zero) }
    var settingsMenuOpen by remember { mutableStateOf(false) }
    var menuPage by remember { mutableStateOf<VideoMenuPage?>(null) }
    // While a swipe changes the brightness or the volume, how much of it there is (see PlayerSettings.gestures)
    var gestureLevel by remember { mutableStateOf<GestureLevel?>(null) }
    // While a swipe sideways seeks: from where, and to where it goes when it's let go
    var swipeSeek by remember { mutableStateOf<Pair<Long, Long>?>(null) }
    // Held down, playing faster until it's let go
    var holding by remember { mutableStateOf(false) }
    // Fill: the video covers the whole screen, cut where it's longer than the screen, instead of fitting in it
    var fill by rememberSaveable { mutableStateOf(false) }
    // Pinched in: how far, and how far the zoomed video is moved from the middle
    var zoom by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }

    // One setting for all videos: when it is on, the player starts over by itself when the video ends
    val videoLoop = LocalVideoLoop.current
    LaunchedEffect(player, videoLoop.enabled) {
        player.repeatMode = if (videoLoop.enabled) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
    }

    // The video's subtitles and sound tracks, for the settings menu
    var tracks by remember(player) { mutableStateOf(player.currentTracks) }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onTracksChanged(newTracks: Tracks) { tracks = newTracks }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }
    val res = LocalResources.current
    val subtitleChoices = remember(tracks) { tracks.choicesOf(C.TRACK_TYPE_TEXT, res) }
    val audioChoices = remember(tracks) { tracks.choicesOf(C.TRACK_TYPE_AUDIO, res) }

    // The rotate button turns the screen the other way and keeps it there (either way up), until the player closes, when
    // the app has its own orientation again
    val activity = remember(context) { context.findActivity() }
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    DisposableEffect(activity) {
        val original = activity?.requestedOrientation
        onDispose { if (original != null) activity?.requestedOrientation = original }
    }
    val rotate = {
        activity?.requestedOrientation =
            if (landscape) ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT else ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
    }

    // The controls disappear while the video plays and nothing happens, and stay when something needs them
    val keepControls = !state.isPlaying || scrubbing || settingsMenuOpen || state.error != null || swipeSeek != null
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
            update = {
                it.player = player
                it.resizeMode = if (fill) AspectRatioFrameLayout.RESIZE_MODE_ZOOM else AspectRatioFrameLayout.RESIZE_MODE_FIT
            },
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = zoom
                    scaleY = zoom
                    translationX = pan.x
                    translationY = pan.y
                }
        )

        // Something to look at while the video is loading: the colours of its thumbnail (whose framing is not
        // that of the video, so it isn't shown sharp)
        if (!state.firstFrameRendered && thumbnailUrl != null) {
            BlurredBackdrop(thumbnailUrl, apiKey, context.imageLoader, Modifier.fillMaxSize())
        }

        // The touches on the video (see above). A tap is acted on right away, rather than after the time in which a
        // second one could still follow as detectTapGestures does, which made showing the controls feel slow; a second
        // tap shortly after it, near it, is the double tap
        val window = (LocalView.current.parent as? DialogWindowProvider)?.window
        val gestures = settings.gestures
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(window, gestures) {
                    val audio = context.getSystemService(AudioManager::class.java)
                    val maxVolume = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
                    var lastTapUptime = 0L
                    var lastTapPosition = Offset.Zero
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        var total = Offset.Zero
                        var up: PointerInputChange? = null
                        // Until the finger moves, lets go, is joined by a second one, or stays down
                        val start = withTimeoutOrNull<TouchStart>(viewConfiguration.longPressTimeoutMillis) {
                            while (true) {
                                val event = awaitPointerEvent()
                                if (event.changes.count { it.pressed } > 1) return@withTimeoutOrNull TouchStart.Pinch
                                val change = event.changes.firstOrNull { it.id == down.id } ?: return@withTimeoutOrNull TouchStart.Gone
                                if (!change.pressed) {
                                    up = change
                                    return@withTimeoutOrNull TouchStart.Tap
                                }
                                total += change.positionChange()
                                if (total.getDistance() > viewConfiguration.touchSlop) return@withTimeoutOrNull TouchStart.Drag
                            }
                            @Suppress("UNREACHABLE_CODE")
                            TouchStart.Gone
                        } ?: TouchStart.Hold
                        when {
                            start == TouchStart.Tap -> {
                                val tap = up ?: return@awaitEachGesture
                                val isDoubleTap = tap.uptimeMillis - lastTapUptime <= viewConfiguration.doubleTapTimeoutMillis &&
                                    (tap.position - lastTapPosition).getDistance() < viewConfiguration.touchSlop * 8
                                if (isDoubleTap) {
                                    lastTapUptime = 0L
                                    feedbackOrigin = tap.position
                                    when {
                                        tap.position.x < size.width / 3f -> {
                                            state.seekBy(-currentStepMs)
                                            seekFeedback = seekFeedback.plusSkip(forward = false, currentStepMs)
                                        }
                                        tap.position.x > size.width * 2f / 3f -> {
                                            state.seekBy(currentStepMs)
                                            seekFeedback = seekFeedback.plusSkip(forward = true, currentStepMs)
                                        }
                                        else -> state.togglePlayPause()
                                    }
                                } else {
                                    lastTapUptime = tap.uptimeMillis
                                    lastTapPosition = tap.position
                                    controlsVisible = !controlsVisible
                                }
                                interactions++
                            }
                            start == TouchStart.Hold -> {
                                // Faster while it plays; held on a paused video, nothing
                                val before = player.playbackParameters.speed
                                val faster = player.isPlaying
                                if (faster) {
                                    holding = true
                                    player.setPlaybackSpeed(HOLD_SPEED)
                                }
                                do {
                                    val event = awaitPointerEvent()
                                    event.changes.forEach { it.consume() }
                                } while (event.changes.any { it.pressed })
                                if (faster) {
                                    player.setPlaybackSpeed(before)
                                    holding = false
                                }
                            }
                            start == TouchStart.Pinch || (start == TouchStart.Drag && zoom > 1f) -> {
                                // Zoomed, or pinched in: the video goes with the fingers, and grows and shrinks with them
                                do {
                                    val event = awaitPointerEvent()
                                    zoom = (zoom * event.calculateZoom()).coerceIn(1f, MAX_VIDEO_ZOOM)
                                    pan = clampPan(pan + event.calculatePan(), zoom, size)
                                    event.changes.forEach { it.consume() }
                                } while (event.changes.any { it.pressed })
                                if (zoom < ZOOM_SNAP_BACK) {
                                    zoom = 1f
                                    pan = Offset.Zero
                                }
                            }
                            start == TouchStart.Drag && gestures && abs(total.x) > abs(total.y) -> {
                                // Seeks as far as the finger goes, from where the video was; there once it's let go
                                val from = player.currentPosition
                                val duration = player.duration.takeIf { it > 0 } ?: return@awaitEachGesture
                                val msPerPx = minOf(duration, SWIPE_SEEK_SPAN_MS).toFloat() / size.width
                                var dragged = total.x
                                swipeSeek = from to from
                                do {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                    dragged += change.positionChange().x
                                    change.consume()
                                    swipeSeek = from to (from + (dragged * msPerPx).toLong()).coerceIn(0L, duration)
                                } while (change.pressed)
                                swipeSeek?.let { (_, to) -> state.seekTo(to) }
                                swipeSeek = null
                                interactions++
                            }
                            start == TouchStart.Drag && gestures -> {
                                val brightness = down.position.x < size.width / 2f
                                var level = if (brightness) {
                                    // -1 is the system's own brightness, which this window starts from
                                    window?.attributes?.screenBrightness?.takeIf { it >= 0f }
                                        ?: (Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, 128) / 255f)
                                } else {
                                    audio.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / maxVolume
                                }
                                var dy = total.y
                                do {
                                    // A swipe across most of the height goes from nothing to all
                                    level = (level - dy / (size.height * 0.8f)).coerceIn(0f, 1f)
                                    if (brightness) {
                                        window?.let { w -> w.attributes = w.attributes.apply { screenBrightness = level.coerceAtLeast(0.01f) } }
                                    } else {
                                        audio.setStreamVolume(AudioManager.STREAM_MUSIC, (level * maxVolume).roundToInt(), 0)
                                    }
                                    gestureLevel = GestureLevel(brightness, level)
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                    dy = change.positionChange().y
                                    change.consume()
                                } while (change.pressed)
                                gestureLevel = null
                            }
                            // A drag otherwise is left to what's around the player (the swipe to the file beside this one)
                            else -> Unit
                        }
                    }
                }
        )

        // A half oval against the side that was double tapped (a third of the width, as the taps are told apart): a ripple
        // spreading from where it was tapped, chevrons running the way it skips, and how far it went
        val reduceMotion = LocalReduceMotion.current
        val feedback = seekFeedback ?: lastFeedback
        val feedbackForward = feedback.forward
        val ripple = remember { Animatable(1f) }
        LaunchedEffect(seekFeedback) {
            if (seekFeedback != null && !reduceMotion) {
                ripple.snapTo(0f)
                ripple.animateTo(1f, tween(SEEK_RIPPLE_MS))
            }
        }
        AnimatedVisibility(
            visible = seekFeedback != null,
            enter = fadeIn(tween(if (reduceMotion) 0 else 100)),
            exit = fadeOut(tween(if (reduceMotion) 0 else 250)),
            modifier = Modifier
                .zIndex(1f)
                .align(if (feedbackForward) Alignment.CenterEnd else Alignment.CenterStart)
                .fillMaxHeight()
                .fillMaxWidth(1f / 3f)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(
                        if (feedbackForward) RoundedCornerShape(topStartPercent = 50, bottomStartPercent = 50)
                        else RoundedCornerShape(topEndPercent = 50, bottomEndPercent = 50)
                    )
                    .background(VideoControlColor.copy(alpha = 0.12f))
                    .drawBehind {
                        // The tap was on the whole player, a third of which this is
                        val origin = Offset(if (feedbackForward) feedbackOrigin.x - size.width * 2f else feedbackOrigin.x, feedbackOrigin.y)
                        val p = ripple.value
                        if (p < 1f) drawCircle(VideoControlColor.copy(alpha = 0.22f * (1f - p)), radius = size.maxDimension * p, center = origin)
                    }
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    SkipChevrons(feedbackForward)
                    Spacer(Modifier.height(6.dp))
                    Shadowed { tint ->
                        FadeText(
                            stringResource(if (feedbackForward) R.string.media_skipped_forward else R.string.media_skipped_back, feedback.seconds),
                            style = MaterialTheme.typography.titleMedium.tabular(),
                            color = tint
                        )
                    }
                }
            }
        }

        // What a gesture is doing. The brightness or the volume: a bar on the side swiped
        gestureLevel?.let { level ->
            LevelBar(
                brightness = level.brightness,
                level = level.level,
                description = stringResource(if (level.brightness) R.string.media_brightness_level else R.string.media_volume_level, (level.level * 100).roundToInt()),
                // Over the controls' dim, as are the other gestures' (drawn after them)
                modifier = Modifier
                    .zIndex(1f)
                    .align(if (level.brightness) Alignment.CenterStart else Alignment.CenterEnd)
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(horizontal = 40.dp)
            )
        }
        // Where a swipe sideways or the seek bar goes: the time, large in the middle, and how far that is from here
        val seekingTo = swipeSeek?.second ?: scrubPositionMs.takeIf { scrubbing }
        seekingTo?.let { to ->
            val from = swipeSeek?.first ?: state.positionMs
            val seconds = (to - from) / 1000
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.zIndex(1f).align(Alignment.Center).softScrim()) {
                Shadowed { tint ->
                    Text(formatDurationMillis(to), color = tint, style = MaterialTheme.typography.displaySmall.tabular(), fontWeight = FontWeight.Medium)
                }
                Shadowed { tint ->
                    Text(
                        stringResource(if (seconds >= 0) R.string.media_skipped_forward else R.string.media_skipped_back, abs(seconds)),
                        color = tint,
                        style = MaterialTheme.typography.titleMedium.tabular()
                    )
                }
            }
        }
        // Held down, faster: near the top
        if (holding) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .zIndex(1f)
                    .align(Alignment.TopCenter)
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(top = 72.dp)
                    .clip(AppShapes.Large)
                    .background(Color.Black.copy(alpha = 0.45f))
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                val pulse by rememberInfiniteTransition(label = "holdPulse").animateFloat(
                    1f, if (reduceMotion) 1f else 0.4f, infiniteRepeatable(tween(500), RepeatMode.Reverse), label = "holdPulseAlpha"
                )
                Icon(Icons.Filled.FastForward, contentDescription = null, tint = VideoControlColor, modifier = Modifier.size(20.dp).graphicsLayer { alpha = pulse })
                Text(stringResource(R.string.media_hold_speed, speedLabel(HOLD_SPEED)), color = VideoControlColor, style = MaterialTheme.typography.titleSmall)
            }
        }

        // Loading: a ring around the play button, which stays
        val loading = (state.isBuffering || !state.firstFrameRendered) && state.error == null

        state.error?.let { error ->
            Surface(
                shape = AppShapes.Card,
                color = Color.Black.copy(alpha = 0.75f),
                contentColor = VideoControlColor,
                modifier = Modifier.align(Alignment.Center).padding(24.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(error.userMessage(), style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.height(12.dp))
                    MediaTextButton(
                        stringResource(R.string.media_try_again),
                        onClick = { state.retry() },
                        container = VideoControlColor.copy(alpha = 0.16f),
                        contentColor = VideoControlColor
                    )
                }
            }
        }

        // The controls fade in and out together, over a light dim of the video; with reduced animations at once
        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(tween(if (reduceMotion) 0 else CONTROLS_FADE_MS)),
            exit = fadeOut(tween(if (reduceMotion) 0 else CONTROLS_FADE_MS)),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = CONTROLS_DIM))) {
                // The preview goes under the status and navigation bars (see FullScreenPreview): the controls keep clear.
                // Along the top: back, the name, and the buttons for how the video is shown
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.7f), Color.Transparent)))
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier
                            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                            .padding(start = 8.dp, end = 8.dp, top = 12.dp, bottom = 36.dp)
                    ) {
                        VideoIconButton(onClick = onBack, contentDescription = stringResource(R.string.files_back)) {
                            ShadowedIcon(Icons.AutoMirrored.Filled.ArrowBack, 24.dp)
                        }
                        Shadowed(Modifier.weight(1f).padding(start = 4.dp)) { tint ->
                            Text(
                                title.orEmpty(),
                                color = tint,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        // Fill the screen or fit in it; either lets go of a pinch's zoom
                        VideoIconButton(
                            onClick = {
                                fill = !fill
                                zoom = 1f
                                pan = Offset.Zero
                                interactions++
                            },
                            contentDescription = if (fill) stringResource(R.string.media_fit_the_video_in_the) else stringResource(R.string.media_fill_the_screen_with_the),
                            selected = fill
                        ) {
                            ShadowedIcon(Icons.Filled.Crop, 24.dp)
                        }
                        if (activity != null) {
                            VideoIconButton(
                                onClick = { rotate(); interactions++ },
                                contentDescription = if (landscape) stringResource(R.string.media_turn_to_portrait) else stringResource(R.string.media_turn_to_landscape)
                            ) {
                                ShadowedIcon(Icons.Filled.ScreenRotation, 24.dp)
                            }
                        }
                        Box {
                            VideoIconButton(
                                onClick = { menuPage = null; settingsMenuOpen = true },
                                contentDescription = stringResource(R.string.media_playback_settings)
                            ) {
                                ShadowedIcon(Icons.Filled.MoreVert, 24.dp)
                            }
                            // Neutral, as the rest of the player: what's on shows in white rather than the theme's colour
                            val scheme = MaterialTheme.colorScheme
                            MaterialTheme(
                                colorScheme = scheme.copy(
                                    primary = scheme.onSurface,
                                    onPrimary = scheme.surface,
                                    primaryContainer = scheme.surfaceContainerHighest,
                                    onPrimaryContainer = scheme.onSurface,
                                    secondaryContainer = scheme.surfaceContainerHighest,
                                    onSecondaryContainer = scheme.onSurface
                                )
                            ) {
                                VideoSettingsMenu(
                                    expanded = settingsMenuOpen,
                                    onDismiss = { settingsMenuOpen = false },
                                    page = menuPage,
                                    onPageChange = { menuPage = it },
                                    choices = VideoMenuChoices(
                                        loop = videoLoop.enabled,
                                        onLoop = { videoLoop.onChange(it); interactions++ },
                                        speed = state.speed,
                                        onSpeed = { state.changeSpeed(it); interactions++ },
                                        subtitles = subtitleChoices,
                                        onSubtitle = { player.selectTrack(C.TRACK_TYPE_TEXT, it); interactions++ },
                                        audio = audioChoices,
                                        onAudio = { player.selectTrack(C.TRACK_TYPE_AUDIO, it); interactions++ },
                                        onOpenElsewhere = {
                                            settingsMenuOpen = false
                                            player.pause()
                                            openVideoElsewhere(context, videoUri)
                                        }
                                    )
                                )
                            }
                        }
                    }
                }

                // In the middle: back a step, play or pause (or play again at the end), on a step. Not over the error's card
                if (state.error == null) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(40.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .softScrim()
                    ) {
                        val seconds = (stepMs / 1000).toInt()
                        VideoIconButton(
                            onClick = { state.seekBy(-stepMs); interactions++ },
                            contentDescription = stringResource(R.string.media_back_seconds, seconds),
                            touch = 64.dp
                        ) {
                            Shadowed { tint -> SkipGlyph(forward = false, seconds = seconds, tint = tint, size = 36.dp) }
                        }
                        val playing = state.playWhenReady && !state.isEnded
                        val playIcon = when {
                            state.isEnded -> Icons.Filled.Replay
                            playing -> Icons.Filled.Pause
                            else -> Icons.Filled.PlayArrow
                        }
                        Box(contentAlignment = Alignment.Center) {
                            // Loading: a ring around it, the button staying where it is
                            if (loading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(84.dp),
                                    color = VideoControlColor,
                                    trackColor = Color.Transparent,
                                    strokeWidth = 3.dp
                                )
                            }
                            VideoIconButton(
                                onClick = { state.togglePlayPause(); interactions++ },
                                contentDescription = when {
                                    state.isEnded -> stringResource(R.string.media_replay)
                                    playing -> stringResource(R.string.media_pause)
                                    else -> stringResource(R.string.media_play)
                                },
                                touch = 80.dp
                            ) {
                                // Play, pause and replay turn into each other
                                AnimatedContent(
                                    targetState = playIcon,
                                    transitionSpec = {
                                        if (reduceMotion) fadeIn(tween(0)) togetherWith fadeOut(tween(0))
                                        else (fadeIn(tween(150)) + scaleIn(tween(200), initialScale = 0.7f)) togetherWith (fadeOut(tween(120)) + scaleOut(tween(150), targetScale = 0.7f))
                                    },
                                    label = "playIcon"
                                ) { icon -> ShadowedIcon(icon, 56.dp) }
                            }
                        }
                        VideoIconButton(
                            onClick = { state.seekBy(stepMs); interactions++ },
                            contentDescription = stringResource(R.string.media_forward_seconds, seconds),
                            touch = 64.dp
                        ) {
                            Shadowed { tint -> SkipGlyph(forward = true, seconds = seconds, tint = tint, size = 36.dp) }
                        }
                    }
                }

                // Along the bottom: the seek bar the whole width, where the video is and how long it is under it
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f))))
                ) {
                    Column(
                        modifier = Modifier
                            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))
                            .padding(start = 16.dp, end = 16.dp, top = 36.dp, bottom = 12.dp)
                    ) {
                        val shownPosition = when {
                            scrubbing -> scrubPositionMs
                            else -> swipeSeek?.second ?: state.positionMs
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
                            activeColor = VideoControlColor,
                            trackColor = VideoControlColor.copy(alpha = 0.25f),
                            bufferedColor = VideoControlColor.copy(alpha = 0.45f)
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val timeStyle = MaterialTheme.typography.labelMedium.tabular()
                            Shadowed(Modifier.weight(1f)) { tint ->
                                Text(formatDurationMillis(shownPosition), color = tint, style = timeStyle, modifier = Modifier.fillMaxWidth())
                            }
                            // The speed, when it isn't the usual one
                            if (state.speed != 1f) {
                                Shadowed(Modifier.padding(end = 12.dp)) { tint ->
                                    Text(speedLabel(state.speed), color = tint, style = timeStyle, fontWeight = FontWeight.Bold)
                                }
                            }
                            Shadowed { tint ->
                                Text(if (state.durationMs > 0) formatDurationMillis(state.durationMs) else "--:--", color = tint, style = timeStyle)
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

/** The playback settings: Loop and Speed (a video without subtitles or other sound tracks), and the speeds. */
@ThemePreviews
@Composable
private fun VideoSettingsMenuPreview() {
    PreviewTheme(padding = 8.dp) {
        val choices = VideoMenuChoices(loop = true, onLoop = {}, speed = 1.5f, onSpeed = {})
        Row {
            AppMenuPreview { VideoSettingsEntries(page = null, onPageChange = {}, choices = choices) }
            AppMenuPreview { VideoSettingsEntries(page = VideoMenuPage.Speed, onPageChange = {}, choices = choices) }
        }
    }
}
