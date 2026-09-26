package tools.senko.materialdrain.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import tools.senko.materialdrain.ui.LocalReduceMotion

private const val BLANK = -1
private const val LETTERS = 26
private const val DIGITS = 10
private const val FIRST_PRINTABLE = 33
private const val LAST_PRINTABLE = 126

/** Steps of a character which appears, disappears or changes into another kind of character. */
private const val LEAD_STEPS = 3

/** How far (in reel spacings) from the middle a character is still drawn, ghosts fade out towards it. */
private const val GHOST_REACH = 1.25f

/**
 * All the settings of the [OdometerText] animation.
 *
 * The characters roll like the wheels of an odometer: a wheel accelerates, runs at its top speed and slows
 * down until it stands still on the new character. The top speed follows from the distance which has to be
 * rolled, the time the roll takes and the shares spent accelerating and decelerating:
 * `topSpeed = distance / (rollTime * (1 - (acceleration + deceleration) / 2))`. A larger acceleration or
 * deceleration share makes a smoother start/stop and thus a higher top speed. [maxSpeedCharsPerSecond] limits
 * the top speed directly, a roll which would be faster then takes longer than [durationMillis].
 *
 * @param durationMillis how long the transition takes at most, see [leftToRightSpread]
 * @param leftToRightSpread how the wheels start, from left to right: the last wheel which has to change starts this
 * share of [durationMillis] later than the first one, the ones in between spread evenly. 0 lets all wheels start
 * at the same moment. The wheels get less time to roll when they start later, so the transition doesn't get longer.
 * @param minCharDurationFraction the time of a wheel with a very short way to go, as fraction of the time of the
 * wheel with the longest way to go; it makes such a wheel arrive a little earlier than the others
 * @param ghostFadeMillis time the neighbouring characters need to fade in before the roll and to fade out after it
 * @param ghostAlpha opacity of the neighbouring ("ghost") characters
 * @param accelerationFraction share of the roll time which is spent speeding up
 * @param decelerationFraction share of the roll time which is spent slowing down
 * @param maxSpeedCharsPerSecond optional limit of the top speed, in characters per second
 * @param maxStepsPerChar limit of steps for wheels whose characters are far apart in the code table
 * @param reelSpacing distance between two characters on a wheel, in font sizes
 */
data class OdometerSpec(
    val durationMillis: Int = 450,
    val leftToRightSpread: Float = 0.4f,
    val minCharDurationFraction: Float = 0.75f,
    val ghostFadeMillis: Int = 90,
    val ghostAlpha: Float = 0.35f,
    val accelerationFraction: Float = 0.4f,
    val decelerationFraction: Float = 0.4f,
    val maxSpeedCharsPerSecond: Float? = null,
    val maxStepsPerChar: Int = 26,
    val reelSpacing: Float = 1.15f
)

// --- The route of a single character ---

/** [path] lists what a character shows after its start character (as last element its target). */
internal class Route(val path: IntArray, val direction: Int)

private fun letterIndex(code: Int): Int? = when (code) {
    in 'a'.code..'z'.code -> code - 'a'.code
    in 'A'.code..'Z'.code -> code - 'A'.code
    else -> null
}

private fun isUpperCase(code: Int) = code in 'A'.code..'Z'.code
private fun digitIndex(code: Int): Int? = if (code in '0'.code..'9'.code) code - '0'.code else null
private fun letterCode(index: Int, upper: Boolean) = (if (upper) 'A'.code else 'a'.code) + Math.floorMod(index, LETTERS)
private fun digitCode(index: Int) = '0'.code + Math.floorMod(index, DIGITS)

/**
 * The character before ([delta] = -1) or after (+1) [code] in its own order: the alphabet, the digits or the
 * code table for symbols. Blanks and spaces have no neighbours.
 */
internal fun neighbour(code: Int, delta: Int): Int {
    letterIndex(code)?.let { return letterCode(it + delta, isUpperCase(code)) }
    digitIndex(code)?.let { return digitCode(it + delta) }
    if (code == BLANK || code == ' '.code) return BLANK
    val next = code + delta
    return if (next in FIRST_PRINTABLE..LAST_PRINTABLE) next else BLANK
}

/**
 * The shortest way round a ring (the alphabet, the digits) from [from] to [to]. Distances above [maxSteps]
 * are sampled with a stride. Elements are made by [codeOf], which has to produce the target for [to].
 */
private fun ringRoute(from: Int, to: Int, size: Int, maxSteps: Int, codeOf: (Int) -> Int): Route {
    val forward = Math.floorMod(to - from, size)
    val backward = size - forward
    val distance = min(forward, backward)
    val direction = if (forward <= backward) 1 else -1
    val steps = min(distance, maxSteps).coerceAtLeast(1)
    val path = IntArray(steps) { k -> codeOf(from + direction * (distance * (k + 1).toFloat() / steps).roundToInt()) }
    return Route(path, direction)
}

/** A character which arrives from something else: it runs up to its final value through its predecessors. */
private fun leadIn(target: Int, maxSteps: Int, codeOf: (Int) -> Int): Route {
    val steps = min(LEAD_STEPS, maxSteps).coerceAtLeast(1)
    return Route(IntArray(steps) { k -> codeOf(target - (steps - 1 - k)) }, 1)
}

/** A character which leaves (for a blank, a space or a symbol): it runs on through its successors first. */
private fun leadOut(start: Int, maxSteps: Int, targetCode: Int, codeOf: (Int) -> Int): Route {
    val steps = min(LEAD_STEPS, maxSteps).coerceAtLeast(1)
    return Route(IntArray(steps) { k -> if (k == steps - 1) targetCode else codeOf(start + 1 + k) }, 1)
}

/** Between spaces and symbols there is no alphabet, walk over the code points which lie in between. */
private fun linearRoute(from: Int, to: Int, maxSteps: Int): Route {
    val start = if (from == BLANK) ' '.code else from
    val end = if (to == BLANK) ' '.code else to
    val distance = end - start
    val steps = min(abs(distance), maxSteps).coerceAtLeast(1)
    val path = IntArray(steps) { k ->
        if (k == steps - 1) to
        else (start + (distance * (k + 1).toFloat() / steps).roundToInt()).coerceIn(FIRST_PRINTABLE, LAST_PRINTABLE)
    }
    return Route(path, if (distance >= 0) 1 else -1)
}

/**
 * The route a character takes from [from] to [to]. It only passes through characters of the kind it is going
 * to or coming from: letters go along the alphabet, digits along the digits, both the short way round, so
 * there are no symbols on the way unless the texts themselves have symbols.
 */
internal fun buildRoute(from: Int, to: Int, maxSteps: Int): Route {
    val fromLetter = letterIndex(from)
    val toLetter = letterIndex(to)
    val fromDigit = digitIndex(from)
    val toDigit = digitIndex(to)
    return when {
        fromLetter != null && toLetter != null -> {
            val upper = isUpperCase(to)
            ringRoute(fromLetter, toLetter, LETTERS, maxSteps) { letterCode(it, upper) }.also { it.path[it.path.lastIndex] = to }
        }
        fromDigit != null && toDigit != null ->
            ringRoute(fromDigit, toDigit, DIGITS, maxSteps) { digitCode(it) }.also { it.path[it.path.lastIndex] = to }
        toLetter != null -> leadIn(toLetter, maxSteps) { letterCode(it, isUpperCase(to)) }
        toDigit != null -> leadIn(toDigit, maxSteps) { digitCode(it) }
        fromLetter != null -> leadOut(fromLetter, maxSteps, to) { letterCode(it, isUpperCase(from)) }
        fromDigit != null -> leadOut(fromDigit, maxSteps, to) { digitCode(it) }
        else -> linearRoute(from, to, maxSteps)
    }
}

internal fun buildPath(from: Int, to: Int, maxSteps: Int): IntArray = buildRoute(from, to, maxSteps).path

// --- The wheels ---

/**
 * One character position: a wheel with the characters of the route in their natural order (ascending downwards),
 * one ghost neighbour above and one below. [startIndex] and [targetIndex] point into [strip]; the wheel rolls up
 * when the target lies below the start (values increase) and down when it lies above.
 */
internal class OdometerSlot(
    val start: Int,
    val target: Int,
    val strip: IntArray,
    val startIndex: Int,
    val targetIndex: Int,
    /** How long the wheel takes, from its own start on. */
    val durationMillis: Float,
    val fadeMillis: Float,
    /** How long the wheel waits before it starts, for the left to right order. */
    val delayMillis: Float = 0f
)

/** A character to draw, [offset] is its distance from the middle in reel spacings (positive = below). */
internal class OdometerGlyph(val code: Int, val offset: Float)

internal class OdometerColumn(
    val glyphs: List<OdometerGlyph>,
    val startCode: Int,
    val targetCode: Int,
    /** How far the wheel has come, 0 at the start and 1 at the target. */
    val progress: Float,
    /** 0 when the ghost characters are invisible, 1 when they are fully faded in. */
    val ghostVisibility: Float
)

internal class OdometerPlan(
    private val slots: List<OdometerSlot>,
    val totalMillis: Float,
    private val spec: OdometerSpec
) {
    /** The state of every wheel [elapsedMillis] after the start of the transition. */
    fun columnsAt(elapsedMillis: Float): List<OdometerColumn> = slots.map { slot -> columnAt(slot, elapsedMillis) }

    /** The text which is in the middle of the wheels, for continuing a transition which was interrupted. */
    fun centerTextAt(elapsedMillis: Float): String {
        val codes = slots.map { slot ->
            val t = elapsedMillis - slot.delayMillis
            when {
                slot.durationMillis <= 0f || t >= slot.durationMillis -> slot.target
                t <= 0f -> slot.start
                else -> slot.strip[position(slot, t).first.roundToInt().coerceIn(0, slot.strip.lastIndex)]
            }
        }
        return codes.dropLastWhile { it == BLANK }.joinToString("") { (if (it == BLANK) ' ' else it.toChar()).toString() }
    }

    private fun columnAt(slot: OdometerSlot, elapsedMillis: Float): OdometerColumn {
        val t = elapsedMillis - slot.delayMillis
        if (slot.durationMillis <= 0f || t >= slot.durationMillis) {
            // A position which lost its character has nothing left to show
            val settled = if (slot.target == BLANK) emptyList() else listOf(OdometerGlyph(slot.target, 0f))
            return OdometerColumn(settled, slot.start, slot.target, 1f, 0f)
        }
        if (t <= 0f) {
            // Not its turn yet
            val waiting = if (slot.start == BLANK) emptyList() else listOf(OdometerGlyph(slot.start, 0f))
            return OdometerColumn(waiting, slot.start, slot.target, 0f, 0f)
        }
        val (pos, progress, visibility) = position(slot, t)
        val glyphs = slot.strip.indices.mapNotNull { k ->
            val offset = k - pos
            if (abs(offset) < GHOST_REACH && slot.strip[k] != BLANK) OdometerGlyph(slot.strip[k], offset) else null
        }
        return OdometerColumn(glyphs, slot.start, slot.target, progress, visibility)
    }

    private fun position(slot: OdometerSlot, elapsedMillis: Float): Triple<Float, Float, Float> {
        val duration = slot.durationMillis
        val fade = slot.fadeMillis
        val visibility = when {
            elapsedMillis < fade -> elapsedMillis / fade
            elapsedMillis > duration - fade -> (duration - elapsedMillis) / fade
            else -> 1f
        }.coerceIn(0f, 1f)
        val rollTime = duration - 2 * fade
        val u = if (rollTime <= 0f) 1f else ((elapsedMillis - fade) / rollTime).coerceIn(0f, 1f)
        val progress = rollProfile(u, spec.accelerationFraction, spec.decelerationFraction)
        return Triple(slot.startIndex + (slot.targetIndex - slot.startIndex) * progress, progress, visibility)
    }
}

/**
 * How far a wheel has rolled, from 0 to 1, after the share [u] of its roll time: constant acceleration during the
 * first [acceleration] share, the top speed, then constant deceleration during the last [deceleration] share.
 */
internal fun rollProfile(u: Float, acceleration: Float, deceleration: Float): Float {
    val shares = acceleration + deceleration
    val a = if (shares > 1f) acceleration / shares else acceleration
    val b = if (shares > 1f) deceleration / shares else deceleration
    val topSpeed = 1f / (1f - (a + b) / 2f)
    return when {
        a > 0f && u < a -> topSpeed * u * u / (2f * a)
        u <= 1f - b -> topSpeed * (a / 2f + (u - a))
        else -> 1f - topSpeed * (1f - u) * (1f - u) / (2f * b)
    }.coerceIn(0f, 1f)
}

private fun staticSlot(code: Int) = OdometerSlot(code, code, intArrayOf(code), 0, 0, 0f, 0f)

internal fun staticOdometerPlan(text: String, spec: OdometerSpec) =
    OdometerPlan(text.map { staticSlot(it.code) }, 0f, spec)

/** Works out how every character position gets from [from] to [to], see [OdometerSpec] for the timing. */
internal fun planOdometer(from: String, to: String, spec: OdometerSpec): OdometerPlan {
    val positions = max(from.length, to.length)
    val starts = IntArray(positions) { from.getOrNull(it)?.code ?: BLANK }
    val targets = IntArray(positions) { to.getOrNull(it)?.code ?: BLANK }
    val routes = Array(positions) { i ->
        if (starts[i] == targets[i]) null else buildRoute(starts[i], targets[i], spec.maxStepsPerChar)
    }
    val mostSteps = routes.maxOfOrNull { it?.path?.size ?: 0 } ?: 0
    if (mostSteps == 0) return staticOdometerPlan(to, spec)

    // The wheels start from left to right: the last one which has to change starts `spread` of the duration
    // after the first one, so all of them get the rest of the duration to roll
    val changing = routes.count { it != null }
    // A single wheel has nothing to start after, it gets the whole duration
    val spread = if (changing > 1) spec.leftToRightSpread.coerceIn(0f, 0.9f) else 0f
    val total0 = spec.durationMillis.toFloat()
    val slowest = total0 * (1f - spread)
    val fastest = slowest * spec.minCharDurationFraction.coerceIn(0f, 1f)
    val shares = spec.accelerationFraction + spec.decelerationFraction
    val speedFactor = 1f - min(shares, 1f) / 2f // top speed = distance / (rollTime * speedFactor)

    var total = 0f
    var rank = 0
    val slots = List(positions) { i ->
        val route = routes[i] ?: return@List staticSlot(targets[i])
        val delay = if (changing > 1) total0 * spread * rank / (changing - 1) else 0f
        rank++
        val steps = route.path.size
        val allotted = fastest + (slowest - fastest) * steps / mostSteps
        val fade = min(spec.ghostFadeMillis.toFloat(), allotted * 0.2f)
        var rollTime = allotted - 2 * fade
        spec.maxSpeedCharsPerSecond?.let { cap ->
            rollTime = max(rollTime, steps / (cap * speedFactor) * 1000f)
        }
        val duration = rollTime + 2 * fade
        total = max(total, delay + duration)

        // The wheel in its natural order, with a neighbour on each side
        val route0 = intArrayOf(starts[i]) + route.path
        val ascending = if (route.direction >= 0) route0 else route0.reversedArray()
        val strip = intArrayOf(neighbour(ascending.first(), -1)) + ascending + intArrayOf(neighbour(ascending.last(), 1))
        val startIndex = if (route.direction >= 0) 1 else route0.size
        val targetIndex = if (route.direction >= 0) route0.size else 1
        OdometerSlot(starts[i], targets[i], strip, startIndex, targetIndex, duration, fade, delay)
    }
    return OdometerPlan(slots, total, spec)
}

// --- The component ---

/**
 * Text which changes like an odometer: every character position is a wheel which, when the text changes, first
 * shows its neighbouring characters as faded "ghosts" above and below, then rolls (up when the values increase,
 * down when they decrease) along the alphabet, the digits or the code table to the new character, accelerating
 * and slowing down, and finally lets the ghosts fade out. The wheels start one after the other from left to
 * right (see [OdometerSpec.leftToRightSpread]) and characters which don't change stay as they are. Only use it where the same text element changes its text, it animates
 * every change of [text] after the first composition.
 *
 * The timing and the looks of the roll are set with [spec].
 *
 * @param fixedWidth reserves the width of the widest of [text] and [widthReferenceTexts], so whatever the text
 * is doing, the space it takes (and thus the size of the container around it) stays the same
 */
@Composable
fun OdometerText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
    spec: OdometerSpec = OdometerSpec(),
    /** Without animation the text changes at once. */
    animate: Boolean = !LocalReduceMotion.current,
    fixedWidth: Boolean = false,
    widthReferenceTexts: List<String> = emptyList()
) {
    var plan by remember { mutableStateOf(staticOdometerPlan(text, spec)) }
    var elapsedMillis by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(text) {
        // Continues from what is currently in the middle of the wheels, so an interrupted roll doesn't jump
        val from = plan.centerTextAt(elapsedMillis)
        val next = if (animate) planOdometer(from, text, spec) else staticOdometerPlan(text, spec)
        if (next.totalMillis > 0f) {
            plan = next
            elapsedMillis = 0f
            val startNanos = withFrameNanos { it }
            while (true) {
                val elapsed = (withFrameNanos { it } - startNanos) / 1_000_000f
                if (elapsed >= next.totalMillis) break
                elapsedMillis = elapsed
            }
        }
        plan = staticOdometerPlan(text, spec)
        elapsedMillis = 0f
    }

    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val resolvedColor = color.takeOrElse { style.color.takeOrElse { LocalContentColor.current } }
    val glyphs = remember(style, textMeasurer) { GlyphCache(textMeasurer, style) }

    val reservedWidth = if (fixedWidth) {
        remember(text, widthReferenceTexts, style, density) {
            with(density) { (widthReferenceTexts + text).maxOf { textMeasurer.measure(it, style).size.width }.toDp() }
        }
    } else null

    val columns = plan.columnsAt(elapsedMillis)
    val columnWidths = columns.map { column ->
        val startWidth = glyphs.width(column.startCode)
        val targetWidth = glyphs.width(column.targetCode)
        startWidth + (targetWidth - startWidth) * column.progress
    }
    val contentWidth = columnWidths.sum()
    val lineHeight = glyphs.lineHeight
    val spacing = with(density) { (if (style.fontSize.isSp) style.fontSize.toPx() else 16.sp.toPx()) } * spec.reelSpacing

    Box(
        modifier = modifier
            .then(if (reservedWidth != null) Modifier.width(reservedWidth) else Modifier)
            // Screen readers should read the text itself, not the characters it is passing through
            .clearAndSetSemantics { contentDescription = text },
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier
                .then(if (reservedWidth != null) Modifier.wrapContentWidth(unbounded = true) else Modifier)
                .size(with(density) { contentWidth.toDp() }, with(density) { lineHeight.toDp() })
        ) {
            var x = 0f
            columns.forEachIndexed { index, column ->
                val columnWidth = columnWidths[index]
                column.glyphs.forEach { glyph ->
                    val layout = glyphs.layout(glyph.code) ?: return@forEach
                    val alpha = glyphAlpha(abs(glyph.offset), spec.ghostAlpha * column.ghostVisibility)
                    if (alpha > 0f) {
                        drawText(
                            textLayoutResult = layout,
                            color = resolvedColor,
                            alpha = alpha,
                            topLeft = Offset(
                                x + (columnWidth - layout.size.width) / 2f,
                                (size.height - layout.size.height) / 2f + glyph.offset * spacing
                            )
                        )
                    }
                }
                x += columnWidth
            }
        }
    }
}

/** The character in the middle is fully visible, the ghost characters next to it have [ghostAlpha]. */
internal fun glyphAlpha(distance: Float, ghostAlpha: Float): Float = when {
    distance <= 1f -> 1f - (1f - ghostAlpha) * distance
    distance < GHOST_REACH -> ghostAlpha * (1f - (distance - 1f) / (GHOST_REACH - 1f))
    else -> 0f
}

/** Measures each character once. */
private class GlyphCache(private val measurer: TextMeasurer, private val style: TextStyle) {
    private val layouts = HashMap<Int, TextLayoutResult>()

    fun layout(code: Int): TextLayoutResult? {
        if (code == BLANK) return null
        return layouts.getOrPut(code) { measurer.measure(code.toChar().toString(), style) }
    }

    fun width(code: Int): Float = layout(code)?.size?.width?.toFloat() ?: 0f

    val lineHeight: Float = measurer.measure("A", style).size.height.toFloat()
}
