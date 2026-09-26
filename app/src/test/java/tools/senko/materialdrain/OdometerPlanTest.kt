package tools.senko.materialdrain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import tools.senko.materialdrain.ui.components.OdometerSpec
import tools.senko.materialdrain.ui.components.buildPath
import tools.senko.materialdrain.ui.components.buildRoute
import tools.senko.materialdrain.ui.components.glyphAlpha
import tools.senko.materialdrain.ui.components.neighbour
import tools.senko.materialdrain.ui.components.planOdometer
import tools.senko.materialdrain.ui.components.rollProfile

class OdometerPlanTest {

    private val spec = OdometerSpec()

    /** Every wheel starts at the same moment, which makes the timing of a single wheel easier to reason about. */
    private val simultaneous = spec.copy(leftToRightSpread = 0f)
    private val duration = spec.durationMillis.toFloat()

    private fun path(from: Char, to: Char) = String(buildPath(from.code, to.code, 26).map { it.toChar() }.toCharArray())

    // --- The route of a single character ---

    @Test
    fun lettersGoAlongTheAlphabetTheShortWayRound() {
        assertEquals("bcd", path('a', 'd'))
        assertEquals("cba", path('d', 'a'))
        assertEquals("z", path('a', 'z'))
        assertEquals("zab", path('y', 'b'))
    }

    @Test
    fun lettersOfADifferentCaseStillOnlyPassThroughLetters() {
        assertEquals("tuvwxyza", path('S', 'a'))
        assertEquals("BCD", path('a', 'D'))
    }

    @Test
    fun digitsGoRoundTheDigits() {
        assertEquals("01", path('9', '1'))
        assertEquals("234", path('1', '4'))
    }

    @Test
    fun aCharacterWhichAppearsRunsUpToItsFinalValueAndOneWhichDisappearsRunsOn() {
        assertEquals("ghi", path(' ', 'i'))
        assertEquals("yza", path(' ', 'a'))
        assertEquals("QRS", path(' ', 'S'))
        assertEquals("tu ", path('s', ' '))
    }

    @Test
    fun theDirectionFollowsTheOrderOfTheValues() {
        assertEquals(1, buildRoute('a'.code, 'd'.code, 26).direction)
        assertEquals(-1, buildRoute('d'.code, 'a'.code, 26).direction)
        assertEquals(1, buildRoute('y'.code, 'b'.code, 26).direction) // across the end of the alphabet
        assertEquals(-1, buildRoute('b'.code, 'y'.code, 26).direction)
    }

    @Test
    fun neighboursRespectTheOrderOfTheirOwnKind() {
        assertEquals('z'.code, neighbour('a'.code, -1))
        assertEquals('b'.code, neighbour('a'.code, 1))
        assertEquals('B'.code, neighbour('A'.code, 1))
        assertEquals('9'.code, neighbour('0'.code, -1))
        assertEquals('!'.code, neighbour('"'.code, -1))
        assertEquals(-1, neighbour(' '.code, 1)) // a space has no neighbours, a symbol must not show up next to it
        assertEquals(-1, neighbour(-1, -1))
    }

    // --- The wheels ---

    @Test
    fun theGhostsAreTheNeighboursOfTheCharacterAndAppearBeforeTheRoll() {
        val plan = planOdometer("a", "d", spec)
        // Before anything happens only the character itself is there
        val atRest = plan.columnsAt(0f).single()
        assertEquals(0f, atRest.ghostVisibility, 0f)
        // After the fade-in the neighbours above (z) and below (b) are there, the wheel has not moved yet
        val ghostsIn = plan.columnsAt(spec.ghostFadeMillis.toFloat()).single()
        assertEquals(1f, ghostsIn.ghostVisibility, 0.001f)
        val shown = ghostsIn.glyphs.associate { it.code.toChar() to it.offset }
        assertEquals(0f, shown.getValue('a'), 0.001f)
        assertEquals(-1f, shown.getValue('z'), 0.001f)
        assertEquals(1f, shown.getValue('b'), 0.001f)
    }

    @Test
    fun theWheelRollsUpwardsForIncreasingAndDownwardsForDecreasingValues() {
        val up = planOdometer("a", "d", spec)
        val down = planOdometer("d", "a", spec)
        val middle = duration / 2f
        // Rolling up: the characters below move towards the middle, so the offset of "d" shrinks
        val upOffsetEarly = up.columnsAt(spec.ghostFadeMillis + 40f).single().glyphs.firstOrNull { it.code == 'b'.code }?.offset
        assertTrue("b is coming from below: $upOffsetEarly", upOffsetEarly != null && upOffsetEarly < 1f && upOffsetEarly > -1f)
        // Rolling down: the characters above move towards the middle
        val downGlyph = down.columnsAt(spec.ghostFadeMillis + 40f).single().glyphs.firstOrNull { it.code == 'c'.code }
        assertTrue("c is coming from above: ${downGlyph?.offset}", downGlyph != null && downGlyph.offset < 1f)
        assertTrue(middle > 0f)
    }

    @Test
    fun theWheelEndsOnTheTargetAndTheGhostsAreGone() {
        val plan = planOdometer("a", "d", spec)
        val end = plan.columnsAt(plan.totalMillis).single()
        assertEquals(listOf('d'.code), end.glyphs.map { it.code })
        assertEquals(0f, end.glyphs.single().offset, 0f)
        assertEquals(0f, end.ghostVisibility, 0f)
    }

    @Test
    fun theGhostsFadeOutWhileTheWheelStandsStill() {
        val plan = planOdometer("a", "d", spec)
        val slotDuration = plan.totalMillis
        val fade = spec.ghostFadeMillis.toFloat()
        val before = plan.columnsAt(slotDuration - fade - 1f).single()
        val fading = plan.columnsAt(slotDuration - fade / 2f).single()
        assertEquals(1f, before.ghostVisibility, 0.001f)
        assertEquals(0.5f, fading.ghostVisibility, 0.05f)
        // The wheel is at the target by then
        assertEquals(1f, fading.progress, 0.001f)
    }

    @Test
    fun theRollAcceleratesAndDecelerates() {
        val n = 100
        val progress = (0..n).map { rollProfile(it / n.toFloat(), 0.4f, 0.4f) }
        assertEquals(0f, progress.first(), 0f)
        assertEquals(1f, progress.last(), 0.0001f)
        // Never going back
        assertTrue(progress.zipWithNext().all { (a, b) -> b >= a })
        // The speed grows, is constant in the middle and falls again
        val speeds = progress.zipWithNext { a, b -> b - a }
        assertTrue("starts slower than the middle", speeds[2] < speeds[50])
        assertTrue("ends slower than the middle", speeds[97] < speeds[50])
        assertEquals(speeds[45], speeds[55], 0.0001f)
    }

    @Test
    fun aLongerAccelerationMeansAHigherTopSpeed() {
        val gentle = rollProfile(0.51f, 0.5f, 0.5f) - rollProfile(0.5f, 0.5f, 0.5f)
        val abrupt = rollProfile(0.51f, 0.1f, 0.1f) - rollProfile(0.5f, 0.1f, 0.1f)
        assertTrue("$gentle should be faster than $abrupt", gentle > abrupt)
    }

    @Test
    fun theTopSpeedCanBeLimited() {
        val limited = spec.copy(maxSpeedCharsPerSecond = 20f)
        val free = planOdometer("a", "n", spec) // 13 characters to roll
        val capped = planOdometer("a", "n", limited)
        assertTrue("capped ${capped.totalMillis} vs ${free.totalMillis}", capped.totalMillis > free.totalMillis)
        // 13 characters at 20 per second at the very least
        assertTrue(capped.totalMillis >= 13 / 20f * 1000f)
    }

    // --- The transition of a text ---

    @Test
    fun theWholeTransitionTakesTheConfiguredDurationAtMost() {
        assertEquals(duration, planOdometer("aa", "ah", simultaneous).totalMillis, 0.001f)
        assertEquals(duration, planOdometer("Save Settings", "Settings saved", simultaneous).totalMillis, 0.001f)
        // Starting one after the other doesn't make it longer
        for ((from, to) in listOf("aa" to "ah", "Save Settings" to "Settings saved", "Upload" to "Filter", "" to "Hello")) {
            val total = planOdometer(from, to, spec).totalMillis
            assertTrue("$from -> $to takes $total ms", total > 0f && total <= duration + 0.001f)
        }
    }

    @Test
    fun theWheelsStartFromLeftToRight() {
        val plan = planOdometer("aaaa", "dddd", spec)
        fun startOf(column: Int): Float {
            var t = 0f
            while (plan.columnsAt(t)[column].ghostVisibility == 0f && t < plan.totalMillis) t += 1f
            return t
        }
        val starts = (0..3).map { startOf(it) }
        assertTrue("each wheel starts after its left neighbour: $starts", starts.zipWithNext().all { (a, b) -> b > a })
        assertEquals("the first wheel starts right away", 1f, starts.first(), 1f)
        // The last wheel starts the configured share of the duration later
        assertEquals(duration * spec.leftToRightSpread, starts.last(), 3f)
        // While the last wheels wait they still show the old character
        val early = plan.columnsAt(20f)
        assertEquals('a'.code, early[3].glyphs.single().code)
        assertEquals(0f, early[3].glyphs.single().offset, 0f)
    }

    @Test
    fun withoutSpreadAllWheelsStartTogether() {
        val plan = planOdometer("aaaa", "dddd", simultaneous)
        val early = plan.columnsAt(spec.ghostFadeMillis / 2f)
        assertTrue(early.all { it.ghostVisibility > 0f })
    }

    @Test
    fun theSpreadIsOnlyOverTheWheelsWhichChange() {
        // The first and last character stay, the two in between change: the first of those starts at once
        val plan = planOdometer("SaXe", "SbYe", spec)
        assertTrue(plan.columnsAt(1f)[1].ghostVisibility > 0f)
        assertEquals(0f, plan.columnsAt(1f)[2].ghostVisibility, 0f)
    }

    @Test
    fun aWheelWithAShortWayToGoArrivesALittleBeforeTheOthers() {
        // The first wheel has 1 step to make, the second 7
        val plan = planOdometer("aa", "bh", simultaneous)
        // A wheel is settled when its ghosts are gone again
        val early = duration * spec.minCharDurationFraction
        val firstWheelDone = early + (duration - early) / 7f
        assertTrue("not before the minimum time", plan.columnsAt(early - 10f)[0].ghostVisibility > 0f)
        assertEquals(0f, plan.columnsAt(firstWheelDone + 5f)[0].ghostVisibility, 0f)
        assertTrue("the other wheel is still busy", plan.columnsAt(firstWheelDone + 5f)[1].ghostVisibility > 0f)
        assertEquals(0f, plan.columnsAt(duration)[1].ghostVisibility, 0f)
    }

    @Test
    fun charactersWhichDoNotChangeStayAsTheyAre() {
        val plan = planOdometer("Save", "Sale", spec)
        for (t in listOf(0f, 100f, 250f, 400f)) {
            val columns = plan.columnsAt(t)
            for (i in listOf(0, 1, 3)) {
                assertEquals(1, columns[i].glyphs.size)
                assertEquals(0f, columns[i].glyphs.single().offset, 0f)
            }
        }
    }

    @Test
    fun noSymbolAppearsWhenTheTextsHaveNoSymbols() {
        val texts = listOf("Save Settings", "Settings saved", "Upload", "Filter", "New List", "Ready 42", "Done 1", "")
        for (from in texts) for (to in texts) {
            val plan = planOdometer(from, to, spec)
            var t = 0f
            while (t <= plan.totalMillis) {
                for (column in plan.columnsAt(t)) for (glyph in column.glyphs) {
                    val c = glyph.code.toChar()
                    assertTrue("\"$from\" -> \"$to\" shows '$c' at $t ms", c.isLetterOrDigit() || c == ' ')
                }
                t += 10f
            }
        }
    }

    @Test
    fun theTextInTheMiddleIsTheOldOneAtTheStartAndTheNewOneAtTheEnd() {
        for ((from, to) in listOf("Save Settings" to "Settings saved", "" to "Hello", "Hello" to "", "50%" to "100%", "a-b" to "c+d")) {
            val plan = planOdometer(from, to, spec)
            assertEquals("$from -> $to at the start", from, plan.centerTextAt(0f))
            assertEquals("$from -> $to at the end", to, plan.centerTextAt(plan.totalMillis))
        }
    }

    @Test
    fun identicalTextsNeedNoTransition() {
        assertEquals(0f, planOdometer("Same", "Same", spec).totalMillis, 0f)
    }

    @Test
    fun ghostsAreFadedAndTheMiddleIsNot() {
        assertEquals(1f, glyphAlpha(0f, 0.35f), 0f)
        assertEquals(0.35f, glyphAlpha(1f, 0.35f), 0.0001f)
        assertEquals(0f, glyphAlpha(1.25f, 0.35f), 0.0001f)
        // With invisible ghosts only the middle is drawn
        assertEquals(0f, glyphAlpha(1f, 0f), 0f)
    }
}
