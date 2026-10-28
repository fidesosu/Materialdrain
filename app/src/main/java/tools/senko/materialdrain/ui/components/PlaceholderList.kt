package tools.senko.materialdrain.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import tools.senko.materialdrain.ui.ListLook
import tools.senko.materialdrain.ui.LocalListLook
import tools.senko.materialdrain.ui.LocalReduceMotion
import tools.senko.materialdrain.ui.preview.PreviewTheme
import tools.senko.materialdrain.ui.preview.ThemePreviews
import androidx.compose.ui.res.stringResource
import tools.senko.materialdrain.R

/** How long the shine takes to cross the placeholders. */
private const val SHINE_MS = 1_300

/** The widths of the name and the details of each placeholder row, of the row's width: different, but the same each time. */
private val NAME_WIDTHS = floatArrayOf(0.62f, 0.44f, 0.71f, 0.52f, 0.38f, 0.66f, 0.48f, 0.57f)
private val DETAIL_WIDTHS = floatArrayOf(0.34f, 0.26f, 0.4f, 0.3f, 0.22f, 0.36f, 0.28f, 0.32f)

/**
 * What a list shows while its files load, before it has any: rows (or tiles, in the grid) of the shape of the files to
 * come, as the list is set to look (Look and feel: compact rows, the grid), with pills where their names and details go,
 * and a soft shine going over them. Without the shine when animations are reduced. As many as fill the screen.
 */
@Composable
fun PlaceholderList(modifier: Modifier = Modifier) {
    val look = LocalListLook.current
    val brush = placeholderBrush()
    val loading = stringResource(R.string.common_loading)
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .semantics { contentDescription = loading }
    ) {
        if (look.grid) {
            // As many tiles to a row as the grid has (about 120dp each, two at the least)
            val columns = (maxWidth / 120.dp).toInt().coerceAtLeast(2)
            val tile = (maxWidth - 6.dp - 14.dp) / columns
            val rows = (maxHeight / (tile + 44.dp)).toInt() + 1
            Column(modifier = Modifier.padding(start = 6.dp, end = 14.dp)) {
                repeat(rows) { row ->
                    Row {
                        repeat(columns) { column -> PlaceholderTile(row * columns + column, brush, Modifier.weight(1f)) }
                    }
                }
            }
        } else {
            val rowHeight = if (look.compact) 56.dp else 72.dp
            val rows = (maxHeight / rowHeight).toInt() + 1
            Column {
                repeat(rows) { index ->
                    PlaceholderRow(index, look.compact, brush)
                    HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                }
            }
        }
    }
}

/** A file row's shape (see FileListItem): its picture, and pills for its name and its details. */
@Composable
private fun PlaceholderRow(index: Int, compact: Boolean, brush: Brush) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = if (compact) 56.dp else 72.dp)
            .padding(start = 16.dp, end = 16.dp, top = if (compact) 4.dp else 8.dp, bottom = if (compact) 4.dp else 8.dp)
    ) {
        val icon = if (compact) 40.dp else 56.dp
        Box(Modifier.size(icon).clip(RoundedCornerShape(icon / 4)).background(brush))
        Spacer(Modifier.width(if (compact) 12.dp else 16.dp))
        BoxWithConstraints(modifier = Modifier.weight(1f)) {
            val width = maxWidth
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill(width * NAME_WIDTHS[index % NAME_WIDTHS.size], 14.dp, brush)
                Pill(width * DETAIL_WIDTHS[index % DETAIL_WIDTHS.size], 10.dp, brush)
            }
        }
    }
}

/** A grid tile's shape (see FileGridItem): its picture, as big as the tile is wide, and a pill for its name. */
@Composable
private fun PlaceholderTile(index: Int, brush: Brush, modifier: Modifier) {
    Column(modifier = modifier.padding(6.dp)) {
        Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(12.dp)).background(brush))
        BoxWithConstraints(modifier = Modifier.fillMaxWidth().padding(top = 10.dp, start = 2.dp, bottom = 12.dp)) {
            val width = maxWidth
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Pill(width * (NAME_WIDTHS[index % NAME_WIDTHS.size] + 0.2f).coerceAtMost(0.95f), 12.dp, brush)
                Pill(width * DETAIL_WIDTHS[index % DETAIL_WIDTHS.size], 9.dp, brush)
            }
        }
    }
}

@Composable
private fun Pill(width: Dp, height: Dp, brush: Brush, shape: Shape = CircleShape) {
    Box(Modifier.width(width).height(height).clip(shape).background(brush))
}

/**
 * The placeholders' colour: the theme's container colour, with a lighter band going across it, or just the colour while
 * animations are reduced. One brush for all of them, so the band goes across the whole list at once.
 */
@Composable
private fun placeholderBrush(): Brush {
    val base = MaterialTheme.colorScheme.surfaceContainerHighest
    val shine = MaterialTheme.colorScheme.surfaceContainerLow
    if (LocalReduceMotion.current) return Brush.linearGradient(listOf(base, base))
    val width = with(LocalDensity.current) { 1_200.dp.toPx() }
    val progress by rememberInfiniteTransition(label = "placeholder").animateFloat(
        initialValue = -1f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(tween(SHINE_MS, easing = LinearEasing), RepeatMode.Restart),
        label = "shine"
    )
    val start = width * progress / 2
    return Brush.linearGradient(
        colors = listOf(base, shine, base),
        start = Offset(start - width / 4, 0f),
        end = Offset(start + width / 4, width / 4)
    )
}

// ---- Previews ----

@ThemePreviews
@Composable
private fun PlaceholderListPreview() = PreviewTheme {
    Box(Modifier.height(400.dp)) { PlaceholderList() }
}

@ThemePreviews
@Composable
private fun PlaceholderListCompactPreview() = PreviewTheme {
    CompositionLocalProvider(LocalListLook provides ListLook(compact = true)) {
        Box(Modifier.height(400.dp)) { PlaceholderList() }
    }
}

@ThemePreviews
@Composable
private fun PlaceholderGridPreview() = PreviewTheme {
    CompositionLocalProvider(LocalListLook provides ListLook(grid = true)) {
        Box(Modifier.height(400.dp)) { PlaceholderList() }
    }
}

@ThemePreviews
@Composable
private fun PlaceholderReducedMotionPreview() = PreviewTheme {
    CompositionLocalProvider(LocalReduceMotion provides true) {
        Box(Modifier.height(300.dp)) { PlaceholderList() }
    }
}
