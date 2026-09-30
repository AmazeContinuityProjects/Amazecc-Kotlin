package com.amazecc.app.shared.state

import com.amazecc.app.shared.model.AssessmentItem
import com.amazecc.app.shared.model.AttendanceItem
import com.amazecc.app.shared.model.AttendanceLog
import com.amazecc.app.shared.model.AttendanceRes
import com.amazecc.app.shared.model.MarksCourseItem
import com.amazecc.app.shared.model.MarksRes
import com.amazecc.app.shared.vtop.VtopComponent
import com.amazecc.app.shared.vtop.VtopCourseCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Covers the bare-code keying and the credit-weighted ETH/ELA merge.
 *
 * These exist because of a real bug: attendance, marks and timetable each spelled the same
 * course differently, so the embedded theory and lab halves never joined and the lab's marks
 * were written to a key nothing displayed.
 */
class EmbeddedCourseMergeTest {

    // ── Code normalisation ──────────────────────────────────────────────────

    @Test
    fun theSameCourseSpelledThreeWaysCollapsesToOneKey() {
        val forms = listOf("BACSE106", "BACSE106L", "BACSE106 (L)", "BACSE106(L)", "BACSE106 - Embedded Lab")
        val keys = forms.map { VtopCourseCode.base(it) }.toSet()
        assertEquals(1, keys.size, "expected one key from $forms, got $keys")
        assertEquals("BACSE106", keys.first())
    }

    @Test
    fun theCodeIsPulledOutOfACellContainingATitle() {
        assertEquals("BACSE106", VtopCourseCode.base("BACSE106 - Programming"))
        assertEquals("18CSC301L", VtopCourseCode.base("18CSC301L - Embedded Lab Architecture"))
    }

    @Test
    fun caseAndSpacingDoNotMatter() {
        assertTrue(VtopCourseCode.sameCourse(" bcse106 ", "BCSE106"))
        assertTrue(VtopCourseCode.sameCourse("BCSE106(L)", "bcse106"))
    }

    @Test
    fun differentCoursesDoNotCollide() {
        assertFalse(VtopCourseCode.sameCourse("BACSE106", "BACSE107"))
    }

    @Test
    fun blankInputStaysBlank() {
        assertEquals("", VtopCourseCode.base(""))
        assertEquals("", VtopCourseCode.base(null))
    }

    // ── Component classification: ETH/ELA merge, TO/LO stand alone ───────────

    @Test
    fun embeddedWordingIsClassified() {
        assertEquals(VtopComponent.ETH, VtopCourseCode.componentOf("BACSE106", typeHint = "Embedded Theory"))
        assertEquals(
            VtopComponent.ELA,
            VtopCourseCode.componentOf("BACSE106", typeHint = "Embedded Lab Architecture")
        )
    }

    @Test
    fun onlyWordingIsClassifiedAsStandalone() {
        assertEquals(VtopComponent.TO, VtopCourseCode.componentOf("BCSE101", typeHint = "Theory Only"))
        assertEquals(VtopComponent.LO, VtopCourseCode.componentOf("BCSE102", typeHint = "Lab Only"))
        assertFalse(VtopComponent.TO.isEmbedded)
        assertFalse(VtopComponent.LO.isEmbedded)
        assertTrue(VtopComponent.ETH.isEmbedded)
        assertTrue(VtopComponent.ELA.isEmbedded)
    }

    @Test
    fun embeddedIsNotConfusedWithStandalone() {
        // "Embedded Lab" also contains "Lab" — a generic lab pattern would misread every
        // embedded course as a standalone Lab Only.
        val c = VtopCourseCode.componentOf("BACSE106", typeHint = "Embedded Lab")
        assertEquals(VtopComponent.ELA, c)
        assertTrue(c.isEmbedded)
        assertTrue(c.isLab)
    }

    @Test
    fun slotCodesAreTheLastResort() {
        assertEquals(VtopComponent.LO, VtopCourseCode.componentOf("BCSE101", slot = "L31+L32"))
        // An explicit label always beats the slot.
        assertEquals(
            VtopComponent.ETH,
            VtopCourseCode.componentOf("BACSE106", typeHint = "Embedded Theory", slot = "L31+L32")
        )
    }

    @Test
    fun labelsRoundTripThroughTheStore() {
        assertEquals(VtopComponent.ETH, VtopCourseCode.fromTypeLabel("ETH"))
        assertEquals(VtopComponent.ELA, VtopCourseCode.fromTypeLabel("ETH + ELA"))
        assertEquals(VtopComponent.TO, VtopCourseCode.fromTypeLabel("Theory Only"))
        assertEquals(VtopComponent.LO, VtopCourseCode.fromTypeLabel("Lab Only"))
        assertEquals(VtopComponent.UNKNOWN, VtopCourseCode.fromTypeLabel(null))
    }

    // ── Credit-weighted marks ───────────────────────────────────────────────

    private fun marks(code: String, type: String, credits: String, marks: List<Pair<String, String>>) =
        MarksCourseItem(
            classNbr = "X",
            courseCode = code,
            courseTitle = "Programming",
            courseType = type,
            credits = credits,
            component = type,
            assessments = marks.map { (title, wm) ->
                AssessmentItem(
                    title = title,
                    maxMark = "100",
                    weightagePercent = "50",
                    weightageMark = wm,
                    scoredMark = wm
                )
            }
        )

    @Test
    fun embeddedPairIsCombinedByCreditWeightNotSummed() {
        val academic = AcademicMerge.upsertMarks(
            AcademicData(),
            "S1",
            MarksRes(
                courses = listOf(
                    marks("BACSE106", "ETH", "3", listOf("CIA-1" to "40", "CIA-2" to "45")),
                    marks("BACSE106", "ELA", "2", listOf("Lab CIA" to "90"))
                )
            )
        )
        val course = academic.semesters.getValue("S1").courses.getValue("BACSE106")
        val stored = course.marks

        assertNotNull(stored)
        // theory total 85 (3cr), lab total 90 (2cr) -> (85*3 + 90*2) / 5 = 87
        assertEquals(87.0, stored.totalMark!!, 0.001)
        // A plain sum would have been 175.
        assertTrue(stored.totalMark!! < 100, "weighted total must stay out of 100")
    }

    @Test
    fun equalCreditsFallBackToTheMean() {
        val academic = AcademicMerge.upsertMarks(
            AcademicData(),
            "S1",
            MarksRes(
                courses = listOf(
                    marks("BACSE106", "ETH", "3", listOf("CIA" to "60")),
                    marks("BACSE106", "ELA", "3", listOf("Lab" to "80"))
                )
            )
        )
        val stored = academic.semesters.getValue("S1").courses.getValue("BACSE106").marks
        assertEquals(70.0, stored!!.totalMark!!, 0.001)
    }

    @Test
    fun bothAssessmentsSurviveTheMerge() {
        val academic = AcademicMerge.upsertMarks(
            AcademicData(),
            "S1",
            MarksRes(
                courses = listOf(
                    marks("BACSE106", "ETH", "3", listOf("CIA-1" to "40")),
                    marks("BACSE106", "ELA", "2", listOf("Lab CIA" to "90"))
                )
            )
        )
        val stored = academic.semesters.getValue("S1").courses.getValue("BACSE106").marks!!
        val titles = stored.assessments.map { it.title }
        assertTrue("CIA-1" in titles, "theory assessment lost")
        assertTrue("Lab CIA" in titles, "lab assessment lost — this was the reported bug")
    }

    @Test
    fun standaloneCourseIsNotDoubled() {
        val academic = AcademicMerge.upsertMarks(
            AcademicData(),
            "S1",
            MarksRes(courses = listOf(marks("BCSE101", "Theory Only", "3", listOf("CIA" to "72"))))
        )
        val stored = academic.semesters.getValue("S1").courses.getValue("BCSE101").marks!!
        assertEquals(72.0, stored.totalMark!!, 0.001)
        assertNull(stored.mergedFrom)
    }

    @Test
    fun embeddedCreditsAreSummedOnTheCourse() {
        val academic = AcademicMerge.upsertMarks(
            AcademicData(),
            "S1",
            MarksRes(
                courses = listOf(
                    marks("BACSE106", "ETH", "3", listOf("CIA" to "60")),
                    marks("BACSE106", "ELA", "2", listOf("Lab" to "80"))
                )
            )
        )
        assertEquals("5", academic.semesters.getValue("S1").courses.getValue("BACSE106").credits)
    }

    // ── Attendance folds by summing class counts ────────────────────────────

    @Test
    fun embeddedAttendanceIsSummedAndRecomputed() {
        fun att(type: String, attended: Int, total: Int) = AttendanceItem(
            courseCode = "BACSE106",
            courseTitle = "Programming",
            courseType = type,
            attendedClasses = attended,
            totalClasses = total,
            attendancePercentage = "0"
        )
        val academic = AcademicMerge.upsertAttendance(
            AcademicData(),
            "S1",
            AttendanceRes(
                attendance = listOf(
                    att("ETH", 45, 50),
                    att("ELA", 18, 20)
                )
            )
        )
        val a = academic.semesters.getValue("S1").courses.getValue("BACSE106").attendance!!
        assertEquals(63, a.attendedClasses)
        assertEquals(70, a.totalClasses)
        assertEquals(90.0, a.attendancePercentage.trimEnd('%').toDouble(), 0.01)
    }

    @Test
    fun attendanceFromBothHalvesIsKeptWithoutDuplicates() {
        fun att(type: String, date: String) = AttendanceItem(
            courseCode = "BACSE106",
            courseType = type,
            attendedClasses = 1,
            totalClasses = 1,
            logs = listOf(AttendanceLog(date, "Present"))
        )
        val academic = AcademicMerge.upsertAttendance(
            AcademicData(),
            "S1",
            AttendanceRes(attendance = listOf(att("ETH", "01-Jan"), att("ELA", "01-Jan"), att("ELA", "02-Jan")))
        )
        val a = academic.semesters.getValue("S1").courses.getValue("BACSE106").attendance!!
        assertEquals(3, a.attendedClasses)
        assertEquals(2, a.logs.size, "same-day rows from both halves should not double up")
    }

    // ── One key, not two ────────────────────────────────────────────────────

    @Test
    fun bothHalvesLandOnASingleKey() {
        val academic = AcademicMerge.upsertMarks(
            AcademicData(),
            "S1",
            MarksRes(
                courses = listOf(
                    marks("BACSE106", "ETH", "3", listOf("CIA" to "60")),
                    marks("BACSE106", "ELA", "2", listOf("Lab" to "80"))
                )
            )
        )
        val keys = academic.semesters.getValue("S1").courses.keys
        assertEquals(1, keys.size, "expected one entry, got $keys")
        assertEquals("BACSE106", keys.first())
    }

    @Test
    fun legacySuffixedRowsAreMigratedOntoTheBareKey() {
        val legacy = StoredCourse(courseCode = "BACSE106(T)").let { t ->
            val lab = StoredCourse(
                courseCode = "BACSE106(L)",
                marks = StoredMarks(
                    totalMark = 90.0,
                    maxMark = 100.0,
                    assessments = listOf(AssessmentItem(title = "Lab CIA", weightageMark = "90"))
                ),
                credits = "2"
            )
            val theory = t.copy(
                credits = "3",
                marks = StoredMarks(
                    totalMark = 85.0,
                    maxMark = 100.0,
                    assessments = listOf(AssessmentItem(title = "CIA", weightageMark = "85"))
                )
            )
            AcademicData(semesters = mapOf("S1" to SemesterData("S1", courses = mapOf(
                "BACSE106(T)" to theory,
                "BACSE106(L)" to lab
            ))))
        }
        val snapshot = AppDataSnapshot(academic = legacy)
        val normalised = AcademicMerge.normalizeEmbeddedKeys(snapshot)
        val course = normalised.academic.semesters.getValue("S1").courses.getValue("BACSE106")
        assertEquals(87.0, course.marks!!.totalMark!!, 0.001)
        assertEquals(2, course.marks!!.assessments.size)
    }
}
