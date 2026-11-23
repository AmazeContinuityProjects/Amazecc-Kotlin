package com.amazecc.app.shared.ui.screens.m3e

import com.amazecc.app.shared.domain.Attendance
import com.amazecc.app.shared.domain.Course
import com.amazecc.app.shared.domain.EmbeddedCourseHalf
import com.amazecc.app.shared.domain.overhaul.M3ECardTone
import com.amazecc.app.shared.model.AttendanceItem
import com.amazecc.app.shared.ui.components.BunkMood
import com.amazecc.app.shared.ui.components.bunkMood
import com.amazecc.app.shared.ui.components.bunkStatsOf
import com.amazecc.app.shared.ui.screens.academics.courseAttendanceDisplay
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * D-189: `EmbeddedLinkMerge.combinedAttendance` leaves the printed percentage blank on a two-half
 * course. Every caller used to parse that blank with `?: 0.0`, which produced a real-looking 0%,
 * a CRITICAL tone and a blended predictor. These pin the honest behaviour of the pure derivations
 * behind the four fixed caller files.
 */
class D189BlankPercentageTest {

    private fun mergedCourse(theoryPct: String = "93", labPct: String = "89") = Course(
        code = "BACSE106",
        title = "Operating Systems",
        type = "Embedded Theory and Lab",
        attendance = Attendance(attended = 42, total = 46, percentage = ""),
        halves = listOf(
            EmbeddedCourseHalf(
                code = "BACSE106",
                type = "Embedded Theory",
                half = "T",
                slots = listOf("C1", "TC1"),
                attendance = Attendance(attended = 26, total = 28, percentage = theoryPct),
            ),
            EmbeddedCourseHalf(
                code = "BACSE106(L)",
                type = "Embedded Lab",
                half = "L",
                slots = listOf("L55", "L56"),
                attendance = Attendance(attended = 16, total = 18, percentage = labPct),
            ),
        ),
        isEmbeddedMerged = true,
    )

    // ── CourseAttendanceScreen ───────────────────────────────────────────────

    @Test
    fun blankPercentageIsAbsenceNotARealZeroOrPredictor() {
        val blank = courseAttendanceDisplay("")
        assertNull(blank.currentPct, "no fabricated 0%")
        assertFalse(blank.predictorAvailable, "no confidence-inspiring prediction from blank data")
        assertEquals("NOT PUBLISHED", blank.statusLabel)

        val missing = courseAttendanceDisplay(null)
        assertNull(missing.currentPct)
        assertFalse(missing.predictorAvailable)

        // A published zero is a different fact and must stay a DANGER 0%.
        val realZero = courseAttendanceDisplay("0")
        assertEquals(0.0, realZero.currentPct)
        assertTrue(realZero.predictorAvailable)
        assertEquals("DANGER", realZero.statusLabel)

        val published = courseAttendanceDisplay(" 89.3 % ")
        assertEquals(89.3, published.currentPct)
        assertEquals("SAFE", published.statusLabel)
    }

    // ── M3EStateBridge: attendance + academics hubs ──────────────────────────

    @Test
    fun embeddedHubShowsBothHalvesAndNeverBlendsTheCounts() {
        val course = mergedCourse()
        val halves = embeddedHalvesPct(course)

        assertEquals(93.0, halves.theory)
        assertEquals(89.0, halves.lab)
        assertTrue(halves.anyPublished)
        assertEquals("Theory 93% · Lab 89%", halves.summary)

        // 42/46 would be 91.3% — a fake single figure for two different classes.
        assertNull(hubAttendancePct(course))
    }

    @Test
    fun attendanceHubCardCarriesBothHalvesAndNeverCountsACriticalPair() {
        val card = attendanceCardStateOf(mergedCourse(), 75f)

        assertEquals("Embedded", card.badge)
        assertEquals(93.0, card.theoryPct)
        assertEquals(89.0, card.labPct)
        assertEquals("Theory 93% · Lab 89%", card.bunkSummary)
        assertEquals(M3ECardTone.NEUTRAL, card.tone, "a pair can never be toned from one blended %")
        assertTrue(card.isMergedPair, "only a genuinely merged pair renders two halves")
        assertFalse(card.isAtRisk, "a pair has no single below-target figure")
        assertFalse(card.bunkSummary.contains("91"), "42/46 is not one class's attendance")
    }

    @Test
    fun loneEmbeddedHalfKeepsItsOwnPublishedPercentageAndStaysAtRisk() {
        val lone = Course(
            code = "BACSE106",
            title = "Operating Systems Theory",
            type = "Embedded Theory",
            attendance = Attendance(attended = 25, total = 40, percentage = "62.5"),
            isEmbeddedMerged = false,
        )
        val card = attendanceCardStateOf(lone, 75f)

        assertTrue(card.isEmbedded, "badge/filter still treat the lone half as Embedded")
        assertFalse(card.isMergedPair, "a lone half is not a two-half merged pair")
        assertEquals(62.5, card.percentage, 1e-9)
        assertEquals(M3ECardTone.CRITICAL, card.tone)
        assertTrue(card.isAtRisk, "a real low figure must not be hidden from At Risk")
        assertNull(card.theoryPct)
        assertNull(card.labPct)
    }

    @Test
    fun standaloneNoDataCardHasNoRealFigureAndIsNotAtRisk() {
        val card = attendanceCardStateOf(
            Course(
                code = "BAHUM251",
                title = "Indian Constitution",
                type = "Theory Only",
                attendance = Attendance(),
            ),
            75f,
        )

        assertEquals(0, card.totalClasses)
        assertEquals(0.0, card.percentage, 1e-9)
        assertEquals(M3ECardTone.NEUTRAL, card.tone)
        assertFalse(card.isAtRisk)
        assertFalse(card.isMergedPair)
    }

    @Test
    fun publishedZeroIsARealFigureAndStaysAtRisk() {
        val card = attendanceCardStateOf(
            Course(
                code = "MAT1001",
                title = "Real Published Zero",
                type = "Theory Only",
                attendance = Attendance(attended = 0, total = 20, percentage = "0"),
            ),
            75f,
        )

        assertEquals(20, card.totalClasses)
        assertEquals(0.0, card.percentage, 1e-9)
        assertEquals(M3ECardTone.CRITICAL, card.tone)
        assertTrue(card.isAtRisk, "a genuine published 0% is a real at-risk fact")
    }

    @Test
    fun attendanceHubCardWithUnpublishedHalvesSaysSo() {
        val card = attendanceCardStateOf(mergedCourse(theoryPct = "", labPct = ""), 75f)

        assertNull(card.theoryPct)
        assertNull(card.labPct)
        assertEquals("Attendance not published", card.bunkSummary)
        assertEquals(M3ECardTone.NEUTRAL, card.tone)
    }

    @Test
    fun embeddedHubWithNoPublishedHalvesReportsHonestAbsence() {
        val course = mergedCourse(theoryPct = "", labPct = "")
        val halves = embeddedHalvesPct(course)

        assertNull(halves.theory)
        assertNull(halves.lab)
        assertFalse(halves.anyPublished)
        assertEquals("Attendance not published", halves.summary)
        assertNull(hubAttendancePct(course))
    }

    @Test
    fun standaloneCourseKeepsItsCountDerivedPercentageUnchanged() {
        val standalone = Course(
            code = "BAHUM251",
            title = "Indian Constitution",
            type = "Theory Only",
            attendance = Attendance(attended = 24, total = 27, percentage = "88.89"),
        )
        // The hubs still derive from counts for a lone course, exactly as before D-189.
        assertEquals(24.0 / 27.0 * 100.0, hubAttendancePct(standalone)!!, 1e-9)

        val noData = standalone.copy(attendance = Attendance())
        assertNull(hubAttendancePct(noData))

        val card = attendanceCardStateOf(standalone, 75f)
        assertEquals(24.0 / 27.0 * 100.0, card.percentage, 1e-9)
        assertEquals(M3ECardTone.GOOD, card.tone)
        assertNull(card.theoryPct)
        assertNull(card.labPct)
    }

    // ── BunkOMeter ───────────────────────────────────────────────────────────

    private fun blankPctCourse() = AttendanceItem(
        courseCode = "BACSE106",
        courseTitle = "Operating Systems",
        courseType = "Embedded Theory and Lab",
        attendedClasses = 42,
        totalClasses = 46,
        attendancePercentage = "",
    )

    @Test
    fun blankPercentageNeverBecomesCriticalOrABunkableMargin() {
        val stats = bunkStatsOf(listOf(blankPctCourse()), 75f)

        assertEquals(0, stats.criticalCount)
        assertEquals(0, stats.warningCount)
        assertEquals(0, stats.totalBunkable)
        assertEquals(1, stats.unavailableCount)
        assertEquals(BunkMood.UNAVAILABLE, bunkMood(stats))
    }

    @Test
    fun aPublishedZeroIsStillCriticalAndMixedListsKeepRealStatus() {
        val realZero = bunkStatsOf(listOf(blankPctCourse().copy(attendancePercentage = "0")), 75f)
        assertEquals(1, realZero.criticalCount)
        assertEquals(0, realZero.unavailableCount)
        assertEquals(BunkMood.CRITICAL, bunkMood(realZero))

        val mixed = bunkStatsOf(
            listOf(
                blankPctCourse(),
                blankPctCourse().copy(courseCode = "MAT2001", attendancePercentage = "50"),
            ),
            75f,
        )
        assertEquals(1, mixed.criticalCount)
        assertEquals(1, mixed.unavailableCount)
        assertEquals(BunkMood.CRITICAL, bunkMood(mixed), "real critical courses are not hidden")
    }
}
