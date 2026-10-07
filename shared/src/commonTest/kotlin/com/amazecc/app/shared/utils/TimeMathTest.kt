package com.amazecc.app.shared.utils

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TimeMathTest {

    // ── toMinutes: bare VIT slot time, the afternoon heuristic ──

    @Test
    fun slotTimesAssumeAfternoonBetweenOneAndSeven() {
        assertEquals(540, TimeMath.toMinutes("9:00"))
        assertEquals(480, TimeMath.toMinutes("8:00"))
        assertEquals(660, TimeMath.toMinutes("11:00"))
        assertEquals(720, TimeMath.toMinutes("12:00"))
        // 1-7 is the afternoon block VIT writes without a meridian anywhere.
        assertEquals(1140, TimeMath.toMinutes("7:00"))
        assertEquals(780, TimeMath.toMinutes("13:00"))
        assertEquals(0, TimeMath.toMinutes(null))
        assertEquals(0, TimeMath.toMinutes(""))
        assertEquals(0, TimeMath.toMinutes("   "))
    }

    @Test
    fun slotTimesTrimBeforeParsing() {
        // "9:00 - 9:50".split("-")[1] is " 9:50" - untrimmed, " 9".toIntOrNull() is null and the
        // hour collapses to 0, so the range would end at minute 50 instead of 590.
        assertEquals(540, TimeMath.toMinutes(" 9:00"))
        assertEquals(590, TimeMath.toMinutes(" 9:50"))
        assertEquals(540, TimeMath.toMinutes("9:00 "))
    }

    // ── toMilitaryMinutes: strict 24-hour HH:mm ──

    @Test
    fun militaryTimesNeverApplyTheAfternoonHeuristic() {
        assertEquals(420, TimeMath.toMilitaryMinutes("07:00"))
        assertEquals(1110, TimeMath.toMilitaryMinutes("18:30"))
        assertEquals(0, TimeMath.toMilitaryMinutes("00:00"))
        assertEquals(1439, TimeMath.toMilitaryMinutes("23:59"))
    }

    @Test
    fun militaryTimesRejectWhatIsNot24HourClock() {
        assertNull(TimeMath.toMilitaryMinutes("6:00 PM"))
        assertNull(TimeMath.toMilitaryMinutes("24:00"))
        assertNull(TimeMath.toMilitaryMinutes("12:60"))
        assertNull(TimeMath.toMilitaryMinutes("-1:00"))
        assertNull(TimeMath.toMilitaryMinutes(""))
        assertNull(TimeMath.toMilitaryMinutes(null))
        assertNull(TimeMath.toMilitaryMinutes("garbage"))
        assertNull(TimeMath.toMilitaryMinutes("9:00 - 9:50"))
    }

    // ── toClockMinutes: explicit AM/PM, Node's clockMinutes ──

    @Test
    fun clockTimesApplyTheMeridianRule() {
        assertEquals(1080, TimeMath.toClockMinutes("6:00 PM"))
        assertEquals(360, TimeMath.toClockMinutes("6:00 AM"))
        assertEquals(720, TimeMath.toClockMinutes("12:00 PM"))
        assertEquals(0, TimeMath.toClockMinutes("12:00 AM"))
        assertEquals(30, TimeMath.toClockMinutes("12:30 AM"))
        assertEquals(840, TimeMath.toClockMinutes("2:00 pm"))
        assertEquals(555, TimeMath.toClockMinutes("09:15 AM"))
    }

    @Test
    fun clockTimesRequireAMeridian() {
        assertNull(TimeMath.toClockMinutes("07:00"))
        assertNull(TimeMath.toClockMinutes("18:00"))
        assertNull(TimeMath.toClockMinutes("13:15"))
        assertNull(TimeMath.toClockMinutes(""))
        assertNull(TimeMath.toClockMinutes(null))
        assertNull(TimeMath.toClockMinutes("no time here"))
    }

    /**
     * The reason this is three functions and not one: the same digits mean different hours
     * depending on which format the caller was handed. Merging them would pick one meaning
     * and silently corrupt the other two inputs.
     */
    @Test
    fun theSameDigitsParseDifferentlyPerFormat() {
        assertEquals(1140, TimeMath.toMinutes("07:00"))     // VIT slot  -> 19:00
        assertEquals(420, TimeMath.toMilitaryMinutes("07:00")!!) // 24-hour -> 07:00
        assertNull(TimeMath.toClockMinutes("07:00"))         // no meridian, rejected

        assertEquals(1080, TimeMath.toMinutes("6:00 PM"))    // meridian ignored, 18:00
        assertNull(TimeMath.toMilitaryMinutes("6:00 PM"))
        assertEquals(1080, TimeMath.toClockMinutes("6:00 PM")!!)
    }

    // ── toRange ──

    @Test
    fun rangesSplitEitherSpacing() {
        assertEquals(540 to 590, TimeMath.toRange("9:00 - 9:50"))
        assertEquals(540 to 590, TimeMath.toRange("9:00-9:50"))
        assertEquals(480 to 530, TimeMath.toRange(" 8:00 - 8:50 "))
        assertEquals(0 to 0, TimeMath.toRange(null))
        assertEquals(0 to 0, TimeMath.toRange(""))
        assertEquals(540 to 0, TimeMath.toRange("9:00"))
    }

    // ── nowMinutes / formatting ──

    @Test
    fun nowMinutesStaysInsideADay() {
        val now = TimeMath.nowMinutes()
        assertTrue(now in 0..1439, "nowMinutes() was $now")
    }

    @Test
    fun formattingRoundTripsThroughMinutes() {
        assertEquals("9:00 AM", TimeMath.minutesToTimeStr(540))
        assertEquals("12:00 PM", TimeMath.minutesToTimeStr(720))
        assertEquals("12:00 AM", TimeMath.minutesToTimeStr(0))
        assertEquals("11:59 PM", TimeMath.minutesToTimeStr(1439))
        assertEquals("6:05 PM", TimeMath.minutesToTimeStr(18 * 60 + 5))
    }

    @Test
    fun durationFormattingSkipsZeroParts() {
        // Pluralisation is ">1", so 1 hr is singular and 1 min is singular, but 5 mins is not.
        assertEquals("1 hr 5 mins", TimeMath.formatDuration(65))
        assertEquals("45 mins", TimeMath.formatDuration(45))
        assertEquals("2 hrs", TimeMath.formatDuration(120))
        assertEquals("1 hr 1 min", TimeMath.formatDuration(61))
        assertEquals("", TimeMath.formatDuration(0))
    }
}
