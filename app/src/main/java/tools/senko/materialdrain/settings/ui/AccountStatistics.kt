package tools.senko.materialdrain.settings.ui

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import tools.senko.materialdrain.provider.pixeldrain.internal.TimeSeries
import tools.senko.materialdrain.ui.LocalReduceMotion
import tools.senko.materialdrain.ui.components.NumberOdometer
import tools.senko.materialdrain.ui.components.OdometerText
import tools.senko.materialdrain.util.formatSize
import tools.senko.materialdrain.util.parseDateTime
import java.text.NumberFormat
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.math.floor
import kotlinx.coroutines.delay
import androidx.compose.ui.res.stringResource
import tools.senko.materialdrain.R

/** The account's usage over time: which statistic and how far back are picked above the chart. */
@Composable
internal fun StatisticsCard(state: AccountUiState, viewModel: AccountViewModel) {
    AccountCard(title = stringResource(R.string.settings_statistics)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp)
        ) {
            UsageStatistic.entries.forEach { statistic ->
                FilterChip(selected = state.statistic == statistic, onClick = { viewModel.setStatistic(statistic) }, label = { Text(statistic.label) })
            }
        }
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
            StatsRange.entries.forEachIndexed { index, range ->
                SegmentedButton(
                    selected = state.range == range,
                    onClick = { viewModel.setRange(range) },
                    shape = SegmentedButtonDefaults.itemShape(index, StatsRange.entries.size)
                ) { Text(range.label) }
            }
        }
        Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            // The chart keeps its size whatever it shows: loading, a failure, or the bars
            when (val series = state.series) {
                Loadable.Loading -> UsageChart(null, state.statistic, state.range, loading = true)
                is Loadable.Failed -> UsageChart(null, state.statistic, state.range, loading = state.seriesLoading, message = series.message)
                is Loadable.Loaded -> UsageChart(series.data, state.seriesStatistic, state.seriesRange, loading = state.seriesLoading)
            }
        }
        if (state.statistic == UsageStatistic.TRANSFER_PAID) {
            CardNote(stringResource(R.string.settings_full_speed_downloads_of_your))
        }
    }
}

/** How long the next chart may take before the chart shows that it's loading. */
private const val LOADING_SHOWN_AFTER_MILLIS = 300L

/** How long the bars take to change into the next chart's. */
private const val MORPH_MILLIS = 450

/**
 * The bars on their way from one chart to the next. [from] and [to] are the heights of each chart's bars, as a fraction
 * of its own peak; they needn't be as many. Each new bar grows from (or shrinks to) the height the old chart had where
 * it stands, so a change of the range reshapes the bars instead of swapping them.
 */
private class BarMorph {
    var from by mutableStateOf(FloatArray(0))
    var to by mutableStateOf(FloatArray(0))

    /** The heights of [to]'s bars at [progress] of the way there. */
    fun at(progress: Float): FloatArray = FloatArray(to.size) { i ->
        val old = from.sampleAt((i + 0.5f) / to.size)
        old + (to[i] - old) * progress
    }
}

/** The height at [position] (0 at the left edge, 1 at the right) of bars with these heights, between their centres. */
private fun FloatArray.sampleAt(position: Float): Float {
    if (isEmpty()) return 0f
    val exact = position * size - 0.5f
    val below = floor(exact).toInt()
    val within = exact - below
    val a = this[below.coerceIn(0, lastIndex)]
    val b = this[(below + 1).coerceIn(0, lastIndex)]
    return a + (b - a) * within
}

/**
 * One bar per time slot of [series], from its start at the left. Tapping or dragging over the bars shows the one under
 * the finger above the chart; otherwise it shows the total. Without a [series] (still loading, or [message] says why
 * not) the same room is kept, so nothing under it moves when the bars come.
 *
 * @param loading another chart is on its way: these bars stay, dimmed, until they change into it
 */
@Composable
private fun UsageChart(series: TimeSeries?, statistic: UsageStatistic, range: StatsRange, loading: Boolean, message: String? = null) {
    val amounts = series?.amounts.orEmpty()
    val colors = MaterialTheme.colorScheme
    val reduceMotion = LocalReduceMotion.current
    var selected by remember(series) { mutableStateOf<Int?>(null) }
    val format: (Long) -> String = { if (statistic.inBytes) formatSize(it) else NumberFormat.getIntegerInstance().format(it) }
    val total = amounts.sum()
    val peak = amounts.maxOrNull() ?: 0L
    val slotFormat = if (range.intervalMinutes < 1440) {
        DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
    } else {
        DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
    }.withZone(ZoneId.systemDefault())
    val dayFormat = DateTimeFormatter.ofPattern(stringResource(R.string.settings_d_mmm)).withZone(ZoneId.systemDefault())
    fun dateOf(index: Int, formatter: DateTimeFormatter) =
        series?.timestamps?.getOrNull(index)?.let(::parseDateTime)?.let(formatter::format).orEmpty()

    // The bars move from the chart before to this one
    val morph = remember { BarMorph() }
    var progress by remember { mutableFloatStateOf(1f) }
    LaunchedEffect(series) {
        if (series == null) return@LaunchedEffect
        val heights = FloatArray(amounts.size) { i -> if (peak > 0) amounts[i].toFloat() / peak else 0f }
        // From where the bars are now, also when the last change hadn't finished. The new heights and the progress back
        // at the start are set together, so no frame shows the one without the other
        morph.from = morph.at(progress)
        morph.to = heights
        progress = if (reduceMotion) 1f else 0f
        if (!reduceMotion) animate(0f, 1f, animationSpec = tween(MORPH_MILLIS, easing = FastOutSlowInEasing)) { value, _ -> progress = value }
    }
    // Dimmed while the next chart loads, but only when that takes a moment: a quick answer would only make it blink
    var showLoading by remember { mutableStateOf(false) }
    LaunchedEffect(loading) {
        if (loading) {
            delay(LOADING_SHOWN_AFTER_MILLIS)
            showLoading = true
        } else {
            showLoading = false
        }
    }
    val barAlpha by animateFloatAsState(if (showLoading) 0.4f else 1f, label = "chartLoading")

    Column {
        // What the chart shows: the bar under the finger, or the whole range
        val index = selected
        Row(verticalAlignment = Alignment.Bottom) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    if (index != null) dateOf(index, slotFormat) else "${statistic.label} in ${range.label}",
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.onSurfaceVariant
                )
                OdometerText(
                    if (series == null) "–" else format(if (index != null) amounts.getOrElse(index) { 0L } else total),
                    style = MaterialTheme.typography.headlineSmall,
                    spec = NumberOdometer
                )
            }
            Text(
                if (peak > 0) stringResource(R.string.settings_peak, format(peak)) else "",
                style = MaterialTheme.typography.labelMedium,
                color = colors.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(8.dp))
        val barColor = colors.primary
        val dimColor = colors.primary.copy(alpha = 0.35f)
        val gridColor = colors.outlineVariant
        fun indexAt(x: Float, width: Float) = (x / width * amounts.size).toInt().coerceIn(0, amounts.lastIndex)
        val statisticLabel = statistic.label
        val rangeLabel = range.label
        val description = if (series == null) "$statisticLabel in $rangeLabel" else
            stringResource(R.string.settings_in_in_total_at_most, statisticLabel, rangeLabel, format(total), format(peak))
        Box(modifier = Modifier.fillMaxWidth().height(140.dp)) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .semantics { contentDescription = description }
                    .pointerInput(amounts) {
                        if (amounts.isEmpty()) return@pointerInput
                        detectTapGestures { offset ->
                            val tapped = indexAt(offset.x, size.width.toFloat())
                            selected = if (selected == tapped) null else tapped
                        }
                    }
                    .pointerInput(amounts) {
                        if (amounts.isEmpty()) return@pointerInput
                        detectHorizontalDragGestures(
                            onDragStart = { selected = indexAt(it.x, size.width.toFloat()) },
                            onHorizontalDrag = { change, _ -> selected = indexAt(change.position.x, size.width.toFloat()) }
                        )
                    }
            ) {
                // The peak's line, quiet, and the baseline
                drawLine(gridColor, Offset(0f, 0f), Offset(size.width, 0f), strokeWidth = 1f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)))
                drawLine(gridColor, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 1.dp.toPx())
                val heights = morph.at(progress)
                if (heights.isEmpty()) return@Canvas
                val gap = if (heights.size > 60) 1.dp.toPx() else 2.dp.toPx()
                val slot = size.width / heights.size
                val barWidth = (slot - gap).coerceAtLeast(1f)
                val radius = minOf(4.dp.toPx(), barWidth / 2)
                heights.forEachIndexed { i, fraction ->
                    if (fraction <= 0f) return@forEachIndexed
                    val height = (fraction * size.height).coerceAtLeast(2.dp.toPx())
                    val left = i * slot + gap / 2
                    // Rounded at the top, square on the baseline
                    val path = Path().apply {
                        addRoundRect(
                            RoundRect(
                                left = left, top = size.height - height, right = left + barWidth, bottom = size.height,
                                topLeftCornerRadius = CornerRadius(radius), topRightCornerRadius = CornerRadius(radius),
                                bottomLeftCornerRadius = CornerRadius.Zero, bottomRightCornerRadius = CornerRadius.Zero
                            )
                        )
                    }
                    drawPath(path, if (selected == null || selected == i) barColor else dimColor, alpha = barAlpha)
                }
                selected?.let { i ->
                    // A thin line through the picked slot, also when its bar is empty
                    val x = i * slot + slot / 2
                    drawLine(barColor.copy(alpha = 0.5f), Offset(x, 0f), Offset(x, size.height), strokeWidth = 1.dp.toPx())
                }
            }
            when {
                message != null -> Text(
                    message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.error,
                    modifier = Modifier.align(Alignment.Center).padding(horizontal = 16.dp)
                )
                series == null -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center).size(24.dp), strokeWidth = 2.dp)
                peak == 0L && !loading -> Text(
                    stringResource(R.string.settings_nothing_in_these_days_yet),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
            // Loading the next chart: a thin bar along the top, over the bars rather than above them
            if (showLoading && series != null) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth().height(2.dp).align(Alignment.TopCenter))
            }
        }
        Spacer(Modifier.height(4.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(dateOf(0, dayFormat), style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant, modifier = Modifier.weight(1f))
            Text(dateOf(amounts.lastIndex, dayFormat), style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
        }
    }
}

// ---- Previews ----
// What's here is drawn by the account page's previews, with a made-up account: SettingsAccountPreview (signed in, every
// card) and SettingsAccountSignedOutPreview in SettingsScreen.kt, and the account's tile in SettingsHomePreview.
