package tools.senko.materialdrain.ui.media

import android.os.Build
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import tools.senko.materialdrain.R
import tools.senko.materialdrain.ui.LocalReduceMotion
import tools.senko.materialdrain.ui.icons.automirrored.filled.KeyboardArrowLeft
import tools.senko.materialdrain.ui.icons.automirrored.filled.KeyboardArrowRight
import tools.senko.materialdrain.ui.icons.automirrored.filled.VolumeDown
import tools.senko.materialdrain.ui.icons.automirrored.filled.VolumeOff
import tools.senko.materialdrain.ui.icons.automirrored.filled.VolumeUp
import tools.senko.materialdrain.ui.icons.filled.BrightnessHigh
import tools.senko.materialdrain.ui.icons.filled.BrightnessLow
import tools.senko.materialdrain.ui.icons.filled.BrightnessMedium

// The video player's controls are white and nothing else: icons on the video itself rather than on discs, each over a
// soft dark shadow of itself so it reads over a light picture as over a dark one (see Shadowed)

/** The colour of the controls over a video. */
internal val VideoControlColor = Color.White

/** Numbers that keep their width as they count (a time, a percentage), so what's around them doesn't move. */
internal fun TextStyle.tabular() = copy(fontFeatureSettings = "tnum")

/**
 * [content] in [VideoControlColor] over a dark shadow of itself, so it stays clear over any picture: a soft blur from
 * Android 12 on, a copy just below it before (Android 10 and 11 can't blur what's drawn).
 */
@Composable
internal fun Shadowed(modifier: Modifier = Modifier, content: @Composable (tint: Color) -> Unit) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Box(
            modifier = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Modifier.graphicsLayer {
                    val radius = 4.dp.toPx()
                    renderEffect = BlurEffect(radius, radius, TileMode.Decal)
                    alpha = 0.9f
                }
            } else {
                Modifier.offset(1.dp, 1.dp).graphicsLayer { alpha = 0.6f }
            },
            contentAlignment = Alignment.Center
        ) { content(Color.Black) }
        content(VideoControlColor)
    }
}

/**
 * A soft dark glow behind controls in the middle of the video (the play buttons, a level), fading out from their middle,
 * for where the video is light all around them. [widthFactor] makes it narrower than it is high.
 */
internal fun Modifier.softScrim(widthFactor: Float = 1f): Modifier = drawBehind {
    val radius = size.maxDimension * 0.75f
    // Narrower than it's high by [widthFactor], for something tall and thin
    scale(widthFactor, 1f, center) {
        drawCircle(Brush.radialGradient(listOf(Color.Black.copy(alpha = 0.32f), Color.Transparent), center = center, radius = radius), radius = radius)
    }
}

/** An icon over the video, with its shadow. */
@Composable
internal fun ShadowedIcon(imageVector: ImageVector, size: Dp, modifier: Modifier = Modifier) {
    Shadowed(modifier) { tint -> Icon(imageVector, contentDescription = null, tint = tint, modifier = Modifier.size(size)) }
}

/**
 * A button over the video: only what's on it ([content], usually a [ShadowedIcon]), its [touch] area round and as big
 * as a button. [selected] shows faintly behind it, for a setting that's on.
 */
@Composable
internal fun VideoIconButton(
    onClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    touch: Dp = 48.dp,
    selected: Boolean = false,
    content: @Composable () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(touch)
            .clip(CircleShape)
            .then(if (selected) Modifier.background(VideoControlColor.copy(alpha = 0.2f)) else Modifier)
            .clickable(interactionSource = interaction, indication = ripple(color = VideoControlColor), role = Role.Button, onClick = onClick)
            .semantics { this.contentDescription = contentDescription }
    ) { content() }
}

/** Three chevrons lighting up one after the other, the way a double tap skips ([forward] or back). */
@Composable
internal fun SkipChevrons(forward: Boolean, modifier: Modifier = Modifier) {
    val reduceMotion = LocalReduceMotion.current
    val phase by rememberInfiniteTransition(label = "skipChevrons").animateFloat(
        initialValue = 0f,
        targetValue = 3f,
        animationSpec = infiniteRepeatable(tween(CHEVRONS_CYCLE_MS, easing = LinearEasing), RepeatMode.Restart),
        label = "skipChevronPhase"
    )
    Row(modifier, horizontalArrangement = Arrangement.spacedBy((-10).dp)) {
        repeat(3) { i ->
            // The lit one goes the way of the skip
            val position = if (forward) i else 2 - i
            Shadowed(Modifier.graphicsLayer { alpha = if (reduceMotion || phase.toInt() == position) 1f else 0.35f }) { tint ->
                Icon(
                    if (forward) Icons.AutoMirrored.Filled.KeyboardArrowRight else Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }
}

/**
 * How much brightness or volume there is while a swipe changes it: its icon (which changes with the level), a thin bar
 * filled up to [level], and the percentage. [description] is what a screen reader says of it.
 */
@Composable
internal fun LevelBar(brightness: Boolean, level: Float, description: String, modifier: Modifier = Modifier) {
    val icon = when {
        brightness && level < 0.34f -> Icons.Filled.BrightnessLow
        brightness && level < 0.67f -> Icons.Filled.BrightnessMedium
        brightness -> Icons.Filled.BrightnessHigh
        level <= 0f -> Icons.AutoMirrored.Filled.VolumeOff
        level < 0.5f -> Icons.AutoMirrored.Filled.VolumeDown
        else -> Icons.AutoMirrored.Filled.VolumeUp
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier.softScrim(widthFactor = 0.3f).clearAndSetSemantics { contentDescription = description }
    ) {
        ShadowedIcon(icon, 28.dp)
        Shadowed { tint ->
            Box(
                contentAlignment = Alignment.BottomCenter,
                modifier = Modifier
                    .width(LEVEL_BAR_WIDTH)
                    .height(LEVEL_BAR_HEIGHT)
                    .clip(RoundedCornerShape(50))
                    .background(tint.copy(alpha = if (tint == VideoControlColor) 0.3f else 0.4f))
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(LEVEL_BAR_HEIGHT * level.coerceIn(0f, 1f))
                        .clip(RoundedCornerShape(50))
                        .background(tint)
                )
            }
        }
        Shadowed { tint ->
            Text(
                stringResource(R.string.media_percent, (level * 100).toInt()),
                color = tint,
                style = MaterialTheme.typography.labelLarge.tabular(),
                fontWeight = FontWeight.Medium
            )
        }
    }
}

private const val CHEVRONS_CYCLE_MS = 600
private val LEVEL_BAR_WIDTH = 4.dp
private val LEVEL_BAR_HEIGHT = 120.dp
