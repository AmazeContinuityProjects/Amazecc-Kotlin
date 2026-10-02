package com.amazecc.app.shared.domain

import com.amazecc.app.shared.model.AssessmentItem
import com.amazecc.app.shared.model.CurriculumBasket
import com.amazecc.app.shared.model.CurriculumBasketItem
import com.amazecc.app.shared.model.CurriculumCategory
import com.amazecc.app.shared.model.CurriculumRes
import com.amazecc.app.shared.model.CategoryDetail
import com.amazecc.app.shared.model.ExamItem
import com.amazecc.app.shared.state.StoredAttendance
import com.amazecc.app.shared.state.StoredCourse
import com.amazecc.app.shared.state.StoredMarks
import com.amazecc.app.shared.state.SemesterData
import com.amazecc.app.shared.state.AcademicData
import com.amazecc.app.shared.state.AppDataSnapshot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The ingestor and projections, tested on the JVM.
 *
 * These used to be impossible to run: `commonTest` had no runnable host, so nothing in this
 * package was ever compiled. Now that `jvm()` exists, these run with `:shared:jvmTest`.
 */
class ProjectionsTest {

    // The real VTOP course-type strings, captured from attendance__CH20262701.html.
    private fun legacyWithEmbeddedPair(): AppDataSnapshot = AppDataSnapshot(
        academic = AcademicData(
            semesters = mapOf(
                "CH20262701" to SemesterData(
                    semesterId = "CH20262701",
                    semesterName = "Fall Semester 2026-27",
                    courses = mapOf(
                        "BACSE106" to StoredCourse(
                            courseCode = "BACSE106",
                            courseTitle = "Operating Systems",
                            courseType = "Embedded Theory",
                            faculty = "50863 KARTHIK R CPS",
                            slots = listOf("C1", "TC1"),
                            attendance = StoredAttendance(26, 28, "93", emptyList()),
                            marks = StoredMarks(
                                assessments = listOf(
                                    AssessmentItem(title = "Quiz 1", maxMark = "20", scoredMark = "18", weightageMark = "9.0", weightagePercent = "10")
                                ),
                                totalMark = 9.0,
                                maxMark = 10.0,
                                mergedFrom = "Embedded Theory",
                            ),
                        ),
                        "BACSE102" to StoredCourse(
                            courseCode = "BACSE102",
                            courseTitle = "Problem Solving Using Java",
                            courseType = "Lab Only",
                            faculty = "52282 SHEENA CHRISTABEL PRAVIN SENSE",
                            attendance = StoredAttendance(32, 36, "89", emptyList()),
                        ),
                    ),
                )
            )
        ),
        curriculum = CurriculumRes(
            success = true,
            categories = listOf(CurriculumCategory("CORE", "Core", 20, 24)),
            details = listOf(
                CategoryDetail(
                    code = "CORE",
                    name = "Core",
                    baskets = listOf(
                        CurriculumBasket(
                            title = "Courses",
                            credits = 5,
                            items = listOf(
                                CurriculumBasketItem("BACSE106", "Operating Systems", 3),
                                CurriculumBasketItem("BACSE102", "Problem Solving Using Java", 2),
                            ),
                        )
                    ),
                )
            ),
        ),
    )

    @Test
    fun `ingest keeps one entry per bare course code`() {
        val domain = VtopIngestor.fromLegacy(legacyWithEmbeddedPair())
        val sem = domain.academics.semesters.getValue("CH20262701")
        // Embedded halves collapse to the bare code, so no (L)/(T) suffixed duplicates.
        assertEquals(setOf("BACSE106", "BACSE102"), sem.courses.keys)
    }

    @Test
    fun `curriculum is authoritative for credits`() {
        val domain = VtopIngestor.fromLegacy(legacyWithEmbeddedPair())
        val sem = domain.academics.semesters.getValue("CH20262701")
        // BACSE106 has no credits on the StoredCourse; curriculum supplies 3.
        assertEquals("3", sem.courses.getValue("BACSE106").credits)
        assertEquals("2", sem.courses.getValue("BACSE102").credits)
    }

    @Test
    fun `ingest is idempotent`() {
        val once = VtopIngestor.fromLegacy(legacyWithEmbeddedPair())
        val twice = VtopIngestor.fromLegacy(legacyWithEmbeddedPair())
        assertEquals(once, twice)
    }

    @Test
    fun `attendance projection reads the real VTOP course type vocabulary`() {
        val domain = VtopIngestor.fromLegacy(legacyWithEmbeddedPair())
        val rows = Projections.attendance(domain, "CH20262701")

        val theory = rows.single { it.courseCode == "BACSE106" }
        val lab = rows.single { it.courseCode == "BACSE102" }

        // "Embedded Theory" is not a lab; "Lab Only" is. Getting this backwards is what broke
        // the ETH/ELA merge, so it is pinned here.
        assertEquals(false, theory.isLab, "Embedded Theory must not be treated as a lab")
        assertEquals(true, lab.isLab, "Lab Only must be treated as a lab")

        assertEquals(26, theory.attended)
        assertEquals(28, theory.total)
        assertEquals("93", theory.percentage)
        assertEquals(89.0, lab.percentValue)
    }

    @Test
    fun `marks projection exposes the credit-weighted total rather than a raw sum`() {
        val domain = VtopIngestor.fromLegacy(legacyWithEmbeddedPair())
        val row = Projections.marks(domain, "CH20262701").single { it.courseCode == "BACSE106" }
        assertTrue(row.isMerged)
        assertEquals("9", row.total, "10.0 should render as 10-style trimmed number, not '9.0'")
        assertEquals("Embedded Theory", row.mergedFrom)
    }

    @Test
    fun `courses projection is sorted by code`() {
        val domain = VtopIngestor.fromLegacy(legacyWithEmbeddedPair())
        val rows = Projections.courses(domain, "CH20262701")
        assertEquals(listOf("BACSE102", "BACSE106"), rows.map { it.code })
    }

    @Test
    fun `missing semester yields empty projections instead of throwing`() {
        val domain = VtopIngestor.fromLegacy(legacyWithEmbeddedPair())
        assertTrue(Projections.courses(domain, "NOPE").isEmpty())
        assertTrue(Projections.attendance(domain, "NOPE").isEmpty())
        assertTrue(Projections.marks(domain, "NOPE").isEmpty())
        assertTrue(Projections.exams(domain, "NOPE").isEmpty())
        assertNull(Projections.semester(domain, "NOPE"))
    }

    @Test
    fun `active semester falls back to the highest id`() {
        val domain = VtopIngestor.fromLegacy(legacyWithEmbeddedPair())
        assertNotNull(Projections.activeSemester(domain))
    }

    @Test
    fun `exams are filtered to the requested semester and inherit course type`() {
        val legacy = legacyWithEmbeddedPair()
        val withExam = legacy.copy(
            academic = legacy.academic.copy(
                semesters = legacy.academic.semesters +
                    ("CH20262701" to legacy.academic.semesters.getValue("CH20262701").copy(
                        exams = listOf(
                            ExamItem(
                                courseCode = "BACSE102",
                                courseTitle = "Problem Solving Using Java",
                                slot = "L31",
                                examDate = "09-07-2026",
                                examSession = "FN",
                                venue = "AB1-305",
                                seatNo = "17",
                            )
                        )
                    ))
            )
        )
        val rows = Projections.exams(VtopIngestor.fromLegacy(withExam), "CH20262701")
        val row = rows.single()
        assertEquals("BACSE102", row.courseCode)
        assertTrue(row.isLab, "BACSE102 is Lab Only, so its exam row must be marked as a lab")
        // seatLocation is empty for this exam, so the seat is just the number rather than
        // " / 17" - the projection drops blank halves.
        assertEquals("17", row.seat)
    }

    // ── the app's one formula per fact ────────────────────────────────────────

    @Test
    fun `headline attendance is the unweighted sum, never an average of percentages`() {
        // A near-perfect one-class course and a terrible forty-class one. Averaging the two
        // percentages gives 54.5%; summing the counts gives 10/100 = 10%. The web app shipped
        // the averaging version on one of its five copies, so the difference is pinned here.
        val summary = Projections.summariseAttendance(listOf(1 to 1, 9 to 99), targetPct = 75f)
        assertEquals(10, summary.attended)
        assertEquals(100, summary.total)
        assertEquals(10f, summary.percentage)
        assertEquals(Projections.AttendanceStatus.CRITICAL, summary.status)
    }

    @Test
    fun `a course with no held classes contributes nothing rather than dragging the figure down`() {
        // Under the averaging version this reads as 100/2 = 50%. It is a full class held.
        val summary = Projections.summariseAttendance(listOf(9 to 10, 0 to 0), targetPct = 75f)
        assertEquals(90f, summary.percentage)
        assertEquals(10, summary.total)
        assertTrue(summary.hasData)
    }

    @Test
    fun `no held classes reads as zero with an N-A status, not as 0 percent attendance`() {
        val summary = Projections.summariseAttendance(emptyList(), targetPct = 75f)
        assertEquals(0f, summary.percentage)
        assertEquals(0, summary.attended)
        assertEquals(0, summary.total)
        assertEquals(Projections.AttendanceStatus.NOT_APPLICABLE, summary.status)
        assertTrue(!summary.hasData)
    }

    @Test
    fun `attendance status bands are five points wide at the target`() {
        // 75 target -> Safe at 80 and above, Warning from 75 to just under 80, Critical below.
        assertEquals(
            Projections.AttendanceStatus.SAFE,
            Projections.attendanceStatus(80f, 100, 75f),
        )
        assertEquals(
            Projections.AttendanceStatus.WARNING,
            Projections.attendanceStatus(79.9f, 100, 75f),
        )
        assertEquals(
            Projections.AttendanceStatus.WARNING,
            Projections.attendanceStatus(75f, 100, 75f),
        )
        assertEquals(
            Projections.AttendanceStatus.CRITICAL,
            Projections.attendanceStatus(74.9f, 100, 75f),
        )
        // No classes held beats every band, whatever the target says.
        assertEquals(
            Projections.AttendanceStatus.NOT_APPLICABLE,
            Projections.attendanceStatus(100f, 0, 75f),
        )
    }

    @Test
    fun `attendance percentage is computed in double precision`() {
        // 1/3 of 100 is 33.333... A float multiply-then-divide drifts enough to show as 33.34
        // in a widget that rounds; the double intermediate keeps the truncation honest.
        val summary = Projections.summariseAttendance(listOf(1 to 3), targetPct = 75f)
        assertEquals(33.333332f, summary.percentage)
    }

    @Test
    fun `domain attendance summary matches the same figure computed by hand`() {
        val domain = VtopIngestor.fromLegacy(legacyWithEmbeddedPair())
        // 26/28 + 32/36 = 58/64 = 90.625%, at a target of 75 -> Safe.
        val summary = Projections.attendanceSummary(domain, "CH20262701", targetPct = 75f)
        assertEquals(58, summary.attended)
        assertEquals(64, summary.total)
        assertEquals(90.625f, summary.percentage)
        assertEquals(Projections.AttendanceStatus.SAFE, summary.status)
    }

    @Test
    fun `credits earned counts only courses with a posted grade`() {
        val earned = Projections.creditsEarned(
            listOf(
                "3" to true,      // counts
                "4" to false,     // no grade yet
                "2.5" to true,    // counts, fractional
                null to true,     // unparseable
                "  " to true,     // blank
                "abc" to true,    // not a number
            )
        )
        assertEquals(5.5, earned)
    }

    @Test
    fun `od status vocabulary accepts VTOP's three spellings and nothing else`() {
        assertTrue(Projections.isOdStatus("On Duty"))
        assertTrue(Projections.isOdStatus(" od "))
        assertTrue(Projections.isOdStatus("ONDUTY"))
        // These must not count: inflating OD hours silently moves a student's bunk allowance.
        assertFalse(Projections.isOdStatus("partial od"))
        assertFalse(Projections.isOdStatus("sectional holiday"))
        assertFalse(Projections.isOdStatus("Absent"))
        assertFalse(Projections.isOdStatus(""))
    }

    @Test
    fun `a lab od session is two hours and a theory session is one`() {
        assertEquals(6, Projections.odHours(listOf(3 to true)))
        assertEquals(3, Projections.odHours(listOf(3 to false)))
        assertEquals(8, Projections.odHours(listOf(2 to true, 4 to false)))
        assertEquals(0, Projections.odHours(emptyList()))
    }
}
