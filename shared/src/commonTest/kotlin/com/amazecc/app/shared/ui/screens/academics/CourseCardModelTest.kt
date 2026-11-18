package com.amazecc.app.shared.ui.screens.academics

import com.amazecc.app.shared.domain.CourseAttendance
import com.amazecc.app.shared.domain.CourseGrade
import com.amazecc.app.shared.domain.CourseGroup
import com.amazecc.app.shared.domain.CourseMarks
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The course-dashboard card's derivations, tested against the CH20262701 shapes.
 *
 * The embedded branch had never run in this app before the halves stopped being merged at ingest,
 * so these pin what it decides: a pair shows two rows with its own attendance each, and a course
 * whose lab half has no marks row still shows that half rather than falling back to a single row.
 */
class CourseCardModelTest {

    private fun att(
        pct: String,
        slot: String = "",
        faculty: String = "50863 KARTHIK R CPS"
    ) = CourseAttendance(
        courseCode = "BACSE106",
        courseTitle = "Operating Systems",
        courseType = "Embedded Theory",
        slotName = slot,
        slotVenue = null,
        faculty = faculty,
        credits = "3",
        category = null,
        attendedClasses = 26,
        totalClasses = 28,
        attendancePercentage = pct,
        logs = emptyList()
    )

    private fun marks(type: String, slot: String) = CourseMarks(
        classNbr = "CH2026270102163",
        courseCode = "BACSE106",
        courseTitle = "Operating Systems",
        courseType = type,
        courseSystem = "ACE",
        faculty = "KARTHIK R",
        slot = slot,
        credits = "3",
        component = null,
        totalMark = 44.0,
        maxMark = 50.0,
        assessments = emptyList()
    )

    // ── An ordinary course ───────────────────────────────────────────────────

    @Test
    fun aTheoryOnlyCourseIsOneRowWithOneAttendance() {
        val group = CourseGroup(
            courseCode = "BAHUM251",
            courseTitle = "Indian Constitution",
            semesterSubId = "CH20262701",
            semesterName = "Fall",
            theory = marks("Theory Only", "G1+TG1").copy(courseCode = "BAHUM251"),
            theoryAtt = att("94").copy(courseCode = "BAHUM251", courseType = "Theory Only"),
            grade = CourseGrade("BAHUM251", "Indian Constitution", "Theory Only", "88", "B+", null, null)
        )
        val card = group.cardModel()

        assertFalse(card.isEmbedded)
        assertEquals("TH", card.badge)
        assertEquals(94.0, card.headlinePct)
        assertTrue(card.headlineVisible)
        assertTrue(card.halves.isEmpty(), "a lone half draws no rows")
    }

    @Test
    fun aLabOnlyCourseIsBadgedLO() {
        val group = CourseGroup(
            courseCode = "BACSE102",
            courseTitle = "Problem Solving Using Java",
            semesterSubId = "CH20262701",
            semesterName = "Fall",
            labAtt = att("89", "L31+L32+L37+L38").copy(
                courseCode = "BACSE102",
                courseType = "Lab Only"
            )
        )
        val card = group.cardModel()

        assertFalse(card.isEmbedded, "a Lab Only course has no counterpart, so it is not a pair")
        assertEquals("LO", card.badge)
        assertEquals(89.0, card.headlinePct)
    }

    // ── A pair ───────────────────────────────────────────────────────────────

    @Test
    fun anEmbeddedCourseShowsTwoRowsWithItsOwnAttendanceEach() {
        val card = embeddedCard().cardModel()

        assertTrue(card.isEmbedded)
        assertEquals("EMB", card.badge)
        assertEquals(2, card.halves.size)
        assertEquals(CourseHalf.THEORY, card.halves[0].half)
        assertEquals(CourseHalf.LAB, card.halves[1].half)
        // The pair's own numbers, not a sum: 26/28 is 93% and 16/18 is 89%.
        assertEquals(93.0, card.halves[0].attendancePct)
        assertEquals(89.0, card.halves[1].attendancePct)
        assertFalse(card.headlineVisible, "an embedded card shows per-half, never one chip")
    }

    @Test
    fun eachRowNamesItsOwnSlot() {
        val halves = embeddedCard().cardModel().halves
        assertEquals("Theory (C1+TC1)", halves[0].label)
        assertEquals("Lab (L55+L56)", halves[1].label)
    }

    @Test
    fun theTheoryAndLabRowsAreTonedIndependently() {
        // 93% is GOOD and 89% is GOOD here; drop the lab below 75 and the two must diverge, which
        // is the whole point of showing them separately.
        val card = embeddedCard(labPct = "70").cardModel()
        assertEquals(CardTone.GOOD, card.halves[0].tone)
        assertEquals(CardTone.LOW, card.halves[1].tone)
    }

    @Test
    fun aLabHalfWithNoMarksRowStillDrawsItsRow() {
        // Exactly CH20262701's BACSE105: attendance knows about both halves, marks published only
        // the theory row. The lab row must appear with its attendance and no slot.
        val group = CourseGroup(
            courseCode = "BACSE105",
            courseTitle = "Data Structures and Algorithms",
            semesterSubId = "CH20262701",
            semesterName = "Fall",
            theory = marks("Embedded Theory", "F1+TF1").copy(courseCode = "BACSE105"),
            theoryAtt = att("89", "F1+TF1").copy(courseCode = "BACSE105", courseType = "Embedded Theory"),
            labAtt = att("90", "L11+L12").copy(courseCode = "BACSE105(L)", courseType = "Embedded Lab")
        )
        val card = group.cardModel()

        assertTrue(card.isEmbedded)
        assertEquals(2, card.halves.size)
        assertEquals("Lab (L11+L12)", card.halves[1].label, "attendance still supplies the slot")
        assertEquals(90.0, card.halves[1].attendancePct)
    }

    @Test
    fun anEmbeddedCourseHidesTheSingleHeadlineAndFaculty() {
        val card = embeddedCard().cardModel()
        assertFalse(card.headlineVisible)
        // The faculty is still resolved, but the card draws it per row rather than once.
        // `parseFaculty` normalises VTOP's shouted caps into a readable name.
        assertEquals("Karthik R", card.faculty?.name)
    }

    // ── The bands ────────────────────────────────────────────────────────────

    @Test
    fun theDashboardBandsAreTheInstitutesFixedLines() {
        assertEquals(CardTone.GOOD, cardTone(100.0))
        assertEquals(CardTone.GOOD, cardTone(85.0))
        assertEquals(CardTone.MID, cardTone(84.9))
        assertEquals(CardTone.MID, cardTone(75.0))
        assertEquals(CardTone.LOW, cardTone(74.9))
        assertEquals(CardTone.LOW, cardTone(0.0))
    }

    @Test
    fun vtopsOwnPercentageTextIsParsedWithOrWithoutItsPercentSign() {
        assertEquals(93.0, attendancePctOf("93"))
        assertEquals(93.0, attendancePctOf("93%"))
        assertEquals(89.3, attendancePctOf(" 89.3 % "))
        // Unparseable reads as "nothing to show", never as a real zero.
        assertEquals(0.0, attendancePctOf(""))
        assertEquals(0.0, attendancePctOf(null))
        assertEquals(0.0, attendancePctOf("-"))
    }

    @Test
    fun aCourseNobodyHasSatShowsNoAttendanceChip() {
        val group = CourseGroup(
            courseCode = "BASTS101",
            courseTitle = "Soft Skill",
            semesterSubId = "CH20262701",
            semesterName = "Fall",
            theoryAtt = att("", "B1+TB1").copy(courseCode = "BASTS101", courseType = "Soft Skill")
        )
        val card = group.cardModel()
        assertFalse(card.headlineVisible, "0% must not be painted as a real zero")
    }

    // ── fixtures ─────────────────────────────────────────────────────────────

    private fun embeddedCard(labPct: String = "89") = CourseGroup(
        courseCode = "BACSE106",
        courseTitle = "Operating Systems",
        semesterSubId = "CH20262701",
        semesterName = "Fall",
        theory = marks("Embedded Theory", "C1+TC1"),
        lab = marks("Embedded Lab", "L55+L56"),
        theoryAtt = att("93", "C1+TC1").copy(courseType = "Embedded Theory"),
        labAtt = att(labPct, "L55+L56").copy(courseType = "Embedded Lab")
    )
}