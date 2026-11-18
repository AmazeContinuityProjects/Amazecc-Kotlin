package com.amazecc.app.shared.state

import com.amazecc.app.shared.model.AssessmentItem
import com.amazecc.app.shared.model.AttendanceItem
import com.amazecc.app.shared.model.AttendanceLog
import com.amazecc.app.shared.model.AttendanceRes
import com.amazecc.app.shared.model.MarksCourseItem
import com.amazecc.app.shared.model.MarksRes
import com.amazecc.app.shared.vtop.VtopComponent
import com.amazecc.app.shared.vtop.VtopCourseCode
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
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
        // The four strings below are the ones VTOP actually sends, read off
        // marks__CH20262701.html and attendance__CH20262701.html:
        //   Embedded Theory x4, Embedded Lab x1, Theory Only x2, Lab Only x1, Soft Skill x1
        // ETH/ELA are this port's own canonical labels, and the inverse map has to accept both.
        assertEquals(VtopComponent.ETH, VtopCourseCode.fromTypeLabel("Embedded Theory"))
        assertEquals(VtopComponent.ELA, VtopCourseCode.fromTypeLabel("Embedded Lab"))
        assertEquals(VtopComponent.TO, VtopCourseCode.fromTypeLabel("Theory Only"))
        assertEquals(VtopComponent.LO, VtopCourseCode.fromTypeLabel("Lab Only"))
        assertEquals(VtopComponent.UNKNOWN, VtopCourseCode.fromTypeLabel("Soft Skill"))

        // Internal labels round-trip too, so a row that already carries one is stable.
        assertEquals(VtopComponent.ETH, VtopCourseCode.fromTypeLabel("ETH"))
        assertEquals(VtopComponent.ELA, VtopCourseCode.fromTypeLabel("ELA"))
        assertEquals(VtopComponent.TO, VtopCourseCode.fromTypeLabel("Theory Only"))
        assertEquals(VtopComponent.LO, VtopCourseCode.fromTypeLabel("Lab Only"))
        assertEquals(VtopComponent.UNKNOWN, VtopCourseCode.fromTypeLabel(null))
    }

    // ── Marks stay on their own half ────────────────────────────────────────
    //
    // These five used to assert the opposite: one row per course with the pair's marks combined
    // by credit weight. Node never combines them - `CourseDetailSubpage.tsx:174-186` keeps
    // `theory` and `lab` as two objects and reads each one's own numbers - and combining here is
    // what made `CourseGroup.lab` permanently null, so `isEmbedded` was permanently false and the
    // lab half of every embedded course was invisible. See `EmbeddedPairSplitTest`.

    // The `slot` matters as much as the type: VTOP pairs the two halves by slot prefix on the
    // marks page, and the real rows carry `F1+TF1` / `L55+L56`. An ETH/ELA pair with no slots
    // would be indistinguishable from two rows of the same course.
    private fun marks(
        code: String,
        type: String,
        credits: String,
        marks: List<Pair<String, String>>,
        slot: String = if (type == "ELA") "L55+L56" else "C1+TC1"
    ) = MarksCourseItem(
        classNbr = "X",
        courseCode = code,
        courseTitle = "Programming",
        courseType = type,
        slot = slot,
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
    fun eachHalfKeepsItsOwnMarkRatherThanACreditWeightedAverage() {
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
        val courses = academic.semesters.getValue("S1").courses
        assertEquals(2, courses.size, "one row per half, got ${courses.keys}")
        // Theory 85 and lab 90 are each out of 100; the weighted average of 87 was a number
        // VTOP never reported, and reporting it hid the two marks it was built from.
        assertEquals(85.0, assertNotNull(courses["BACSE106"]).marks!!.totalMark!!, 0.001)
        assertEquals(90.0, assertNotNull(courses["BACSE106(L)"]).marks!!.totalMark!!, 0.001)
    }

    @Test
    fun eachHalfKeepsItsOwnCredits() {
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
        val courses = academic.semesters.getValue("S1").courses
        // The predictor prints "3T + 2L", which needs them separate; a single summed "5" cannot
        // tell a 3+2 embedded pair from a 5-credit theory course.
        assertEquals("3", assertNotNull(courses["BACSE106"]).credits)
        assertEquals("2", assertNotNull(courses["BACSE106(L)"]).credits)
    }

    @Test
    fun bothAssessmentsSurviveOnTheirOwnHalves() {
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
        val courses = academic.semesters.getValue("S1").courses
        val theoryTitles = assertNotNull(courses["BACSE106"]).marks!!.assessments.map { it.title }
        val labTitles = assertNotNull(courses["BACSE106(L)"]).marks!!.assessments.map { it.title }
        assertEquals(listOf("CIA-1"), theoryTitles, "theory assessment lost")
        assertEquals(listOf("Lab CIA"), labTitles, "lab assessment lost - this was the reported bug")
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

    // ── Attendance stays on its own half ────────────────────────────────────
    //
    // 45/50 and 18/20 are different classes in different rooms. Adding them to 63/70 was a true
    // statement about the pair and a useless one per half: the card could only ever show one
    // percentage, and the lab's own 90% was indistinguishable from the pair's.

    @Test
    fun embeddedAttendanceStaysOnEachHalf() {
        fun att(type: String, attended: Int, total: Int) = AttendanceItem(
            courseCode = "BACSE106",
            courseTitle = "Programming",
            courseType = type,
            slotName = if (type == "ELA") "L11+L12" else "F1+TF1",
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
        val courses = academic.semesters.getValue("S1").courses
        assertEquals(2, courses.size, "one row per half, got ${courses.keys}")
        val theory = assertNotNull(courses["BACSE106"]).attendance!!
        assertEquals(45, theory.attendedClasses)
        assertEquals(50, theory.totalClasses)
        val lab = assertNotNull(courses["BACSE106(L)"]).attendance!!
        assertEquals(18, lab.attendedClasses)
        assertEquals(20, lab.totalClasses)
    }

    @Test
    fun twoReportsOfTheSameHalfStillAdd() {
        // Summing across halves was wrong; summing two reports of one half is right - they are the
        // same classes counted twice, once by the summary page and once by the detail page.
        fun att(type: String, slot: String, attended: Int, total: Int) = AttendanceItem(
            courseCode = "BACSE106",
            courseType = type,
            slotName = slot,
            attendedClasses = attended,
            totalClasses = total,
            attendancePercentage = "0"
        )
        val academic = AcademicMerge.upsertAttendance(
            AcademicData(),
            "S1",
            AttendanceRes(
                attendance = listOf(
                    att("ETH", "F1+TF1", 45, 50),
                    att("Embedded Theory", "F1", 5, 5)
                )
            )
        )
        val a = academic.semesters.getValue("S1").courses.getValue("BACSE106").attendance!!
        assertEquals(50, a.attendedClasses)
        assertEquals(55, a.totalClasses)
    }

    @Test
    fun attendanceFromBothHalvesIsKeptWithoutDuplicates() {
        // Attendance logs do NOT survive as `logs` on the incoming item: `sanitizeAttendance`
        // rebuilds them from `viewLinkRaw`, which is the real per-day detail payload. Setting
        // `logs` directly is silently discarded, so this has to go in as raw JSON.
        fun att(type: String, vararg days: String) = AttendanceItem(
            courseCode = "BACSE106",
            courseType = type,
            slotName = if (type == "ELA") "L11+L12" else "F1+TF1",
            attendedClasses = 1,
            totalClasses = 1,
            viewLinkRaw = buildJsonArray {
                days.forEach { add(buildJsonObject { put("date", it); put("status", "Present") }) }
            }
        )
        val academic = AcademicMerge.upsertAttendance(
            AcademicData(),
            "S1",
            AttendanceRes(
                attendance = listOf(
                    att("ETH", "01-Jan"),
                    att("ELA", "01-Jan"),
                    att("ELA", "02-Jan")
                )
            )
        )
        val courses = academic.semesters.getValue("S1").courses
        // Each half keeps only its own day, even though they share the first: a theory class and a
        // lab class on the same date are two separate events.
        assertEquals(listOf("01-Jan"), assertNotNull(courses["BACSE106"]).attendance!!.logs.map { it.date })
        assertEquals(listOf("01-Jan", "02-Jan"), assertNotNull(courses["BACSE106(L)"]).attendance!!.logs.map { it.date })
    }

    // ── Two keys, one course ────────────────────────────────────────────────

    @Test
    fun bothHalvesLandOnTwoKeysThatPairToOneCourse() {
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
        assertEquals(setOf("BACSE106", "BACSE106(L)"), keys)
        // ...and the reader folds them back together, which is what makes `isEmbedded` true.
        assertEquals("BACSE106", VtopCourseCode.base(keys.last()))
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
        val courses = normalised.academic.semesters.getValue("S1").courses
        // The "(T)" row normalises onto the bare key and the "(L)" row keeps its marker: two rows
        // before, two rows after. The migration used to collapse them into one averaged row.
        assertEquals(setOf("BACSE106", "BACSE106(L)"), courses.keys)
        assertEquals(85.0, assertNotNull(courses["BACSE106"]).marks!!.totalMark!!, 0.001)
        assertEquals(90.0, assertNotNull(courses["BACSE106(L)"]).marks!!.totalMark!!, 0.001)
    }

    @Test
    fun aSuffixedCodeThatVTOPWroteIsNormalisedOntoTheLabKey() {
        // VTOP writes embedded labs as `BACSE106L` in some tables and `BACSE106 (L)` in others.
        // Both must land on `BACSE106(L)` or the reader cannot pair them.
        listOf("BACSE106L", "BACSE106 (L)", "BACSE106(L)", "BACSE106 - Embedded Lab").forEach { raw ->
            assertEquals("BACSE106(L)", AcademicMerge.canonicalHalfKey(raw), "from $raw")
        }
        assertEquals("BACSE106", AcademicMerge.canonicalHalfKey("BACSE106"))
        assertEquals("BACSE106", AcademicMerge.canonicalHalfKey("BACSE106(T)"))
    }
}
