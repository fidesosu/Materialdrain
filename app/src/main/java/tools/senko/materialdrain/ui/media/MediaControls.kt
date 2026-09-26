package tools.senko.materialdrain.ui.media

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import tools.senko.materialdrain.ui.LocalReduceMotion

/** Buttons of the media players are rectangles with slightly rounded corners, no pills and no circles. */
internal val MediaControlShape = RoundedCornerShape(6.dp)

private val ControlContainer = Color.White.copy(alpha = 0.14f)

/** The container gets lighter when the mouse is over the button and lighter still while it is pressed. */
@Composable
private fun rememberControlColor(base: Color, interaction: MutableInteractionSource, enabled: Boolean): Color {
    val hovered by interaction.collectIsHoveredAsState()
    val pressed by interaction.collectIsPressedAsState()
    return when {
        !enabled -> base.copy(alpha = base.alpha * 0.5f)
        pressed -> base.copy(alpha = (base.alpha + 0.24f).coerceAtMost(1f))
        hovered -> base.copy(alpha = (base.alpha + 0.12f).coerceAtMost(1f))
        else -> base
    }
}

/** A square button with an icon. */
@Composable
fun MediaIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    size: Dp = 44.dp,
    container: Color = ControlContainer,
    contentColor: Color = Color.White
) {
    val interaction = remember { MutableInteractionSource() }
    val background = rememberControlColor(container, interaction, enabled)
    Box(
        modifier = modifier
            .size(size)
            .clip(MediaControlShape)
            .background(background)
            .clickable(
                interactionSource = interaction,
                indication = ripple(color = Color.White),
                enabled = enabled,
                role = Role.Button,
                onClickLabel = contentDescription,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = contentDescription, tint = if (enabled) contentColor else contentColor.copy(alpha = 0.5f))
    }
}

/** A button with a short text, such as the playback speed. */
@Composable
fun MediaTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    container: Color = ControlContainer,
    contentColor: Color = Color.White
) {
    val interaction = remember { MutableInteractionSource() }
    val background = rememberControlColor(container, interaction, true)
    Box(
        modifier = modifier
            .heightIn(min = 44.dp)
            .widthIn(min = 44.dp)
            .clip(MediaControlShape)
            .background(background)
            .clickable(
                interactionSource = interaction,
                indication = ripple(color = Color.White),
                role = Role.Button,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = contentColor, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium)
    }
}

/**
 * The seek bar: a thin track which gets thicker (and whose rectangular handle gets taller) while it is pressed
 * or hovered. Tapping seeks there, dragging scrubs.
 *
 * @param onSeekStarted called when the user starts scrubbing, so that the position of the player isn't shown meanwhile
 * @param onSeekPreview called with the position while the user scrubs
 * @param onSeekFinished called when the user lets go, this is when the player should seek
 */
@Composable
fun MediaSeekBar(
    positionMs: Long,
    durationMs: Long,
    bufferedMs: Long,
    onSeekStarted: () -> Unit,
    onSeekPreview: (Long) -> Unit,
    onSeekFinished: (Long) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    activeColor: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = Color.White.copy(alpha = 0.25f),
    bufferedColor: Color = Color.White.copy(alpha = 0.4f)
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    var pressed by remember { mutableStateOf(false) }
    val active = pressed || hovered
    val reduceMotion = LocalReduceMotion.current

    val trackHeight by animateDpAsState(if (active) 6.dp else 3.dp, if (reduceMotion) snap() else tween(120), label = "seekTrack")
    val handleHeight by animateDpAsState(if (active) 24.dp else 14.dp, if (reduceMotion) snap() else tween(120), label = "seekHandle")

    val currentDuration by rememberUpdatedState(durationMs)
    val currentEnabled by rememberUpdatedState(enabled)
    val currentStarted by rememberUpdatedState(onSeekStarted)
    val currentPreview by rememberUpdatedState(onSeekPreview)
    val currentFinished by rememberUpdatedState(onSeekFinished)

    val fraction = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    val bufferedFraction = if (durationMs > 0) (bufferedMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(32.dp)
            .hoverable(interaction)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    if (!currentEnabled || currentDuration <= 0L) return@awaitEachGesture
                    fun positionAt(x: Float) = (x / size.width).coerceIn(0f, 1f) * currentDuration
                    pressed = true
                    currentStarted()
                    var target = positionAt(down.position.x).toLong()
                    currentPreview(target)
                    down.consume()
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (change.changedToUp()) {
                            change.consume()
                            break
                        }
                        target = positionAt(change.position.x).toLong()
                        currentPreview(target)
                        change.consume()
                    }
                    pressed = false
                    currentFinished(target)
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxWidth().height(32.dp)) {
            val trackPx = trackHeight.toPx()
            val centerY = size.height / 2f
            val corner = CornerRadius(1.dp.toPx())
            val top = centerY - trackPx / 2f
            drawRoundRect(trackColor, Offset(0f, top), Size(size.width, trackPx), corner)
            drawRoundRect(bufferedColor, Offset(0f, top), Size(size.width * bufferedFraction, trackPx), corner)
            val played = size.width * fraction
            drawRoundRect(activeColor, Offset(0f, top), Size(played, trackPx), corner)
            // The handle is a thin rectangle
            val handleWidth = 4.dp.toPx()
            val handleTop = centerY - handleHeight.toPx() / 2f
            val handleLeft = (played - handleWidth / 2f).coerceIn(0f, size.width - handleWidth)
            drawRoundRect(activeColor, Offset(handleLeft, handleTop), Size(handleWidth, handleHeight.toPx()), corner)
        }
    }
}

/** Media cards and buttons are rectangles with slightly rounded corners. */
internal val MediaCardShape = RoundedCornerShape(8.dp)
