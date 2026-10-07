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

    /** The ground-truth semester plus one real VTOP exam row. */
    private fun withExamSnapshot(): AppDataSnapshot {
        val legacy = legacyWithEmbeddedPair()
        return legacy.copy(
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
    }

    @Test
    fun `exams are filtered to the requested semester and inherit course type`() {
        val rows = Projections.exams(VtopIngestor.fromLegacy(withExamSnapshot()), "CH20262701")
        val row = rows.single()
        assertEquals("BACSE102", row.courseCode)
        assertTrue(row.isLab, "BACSE102 is Lab Only, so its exam row must be marked as a lab")
        // seatLocation is empty for this exam, so the seat is just the number rather than
        // " / 17" - the projection drops blank halves.
        assertEquals("17", row.seat)
    }

    @Test
    fun `semesterExams hands back raw rows, keeping date, session and time apart`() {
        val domain = VtopIngestor.fromLegacy(withExamSnapshot())

        val exam = Projections.semesterExams(domain, "CH20262701").single()
        // Raw, not display-formatted: the display projection would render "FN" as "Forenoon" and
        // merge time into reporting time, and the week grid needs to split them again itself.
        assertEquals("09-07-2026", exam.date)
        assertEquals("FN", exam.session)
        assertEquals("", exam.time)
        assertEquals("CH20262701", exam.semesterId)

        assertTrue(Projections.semesterExams(domain, "CH19999999").isEmpty())
    }

    /**
     * Two semesters: one has published an exam, the other has courses but no schedule. The two
     * selection rules disagree about exactly that second case, so they are asserted side by side.
     */
    private fun twoSemesterExamSnapshot(): AppDataSnapshot {
        val legacy = withExamSnapshot()
        return legacy.copy(
            academic = legacy.academic.copy(
                semesters = legacy.academic.semesters +
                    ("CH20252601" to SemesterData(
                        semesterId = "CH20252601",
                        semesterName = "Fall Semester 2025-26",
                        courses = mapOf(
                            "BPHY101" to StoredCourse(
                                courseCode = "BPHY101",
                                courseTitle = "Physics for Engineers",
                                courseType = "Embedded Theory",
                                faculty = "10001 SOME FACULTY",
                                attendance = StoredAttendance(10, 12, "83", emptyList()),
                            ),
                        ),
                    ))
            )
        )
    }

    @Test
    fun `the two exam selection rules disagree only for a semester that published no exams`() {
        val domain = VtopIngestor.fromLegacy(twoSemesterExamSnapshot())

        // They agree where there is data...
        assertEquals(1, Projections.selectedSemesterExams(domain, "CH20262701").size)
        assertEquals(1, Projections.examsForKnownSemester(domain, "CH20262701").size)
        // ...and where the id names no semester at all.
        assertEquals(1, Projections.selectedSemesterExams(domain, "NOPE").size)
        assertEquals(1, Projections.examsForKnownSemester(domain, "NOPE").size)

        // They disagree only here: the semester exists but has published nothing. The dropdown
        // shows every exam rather than an empty screen; the calendar shows none rather than
        // pretending other terms are this term's.
        assertEquals(1, Projections.selectedSemesterExams(domain, "CH20252601").size)
        assertTrue(Projections.examsForKnownSemester(domain, "CH20252601").isEmpty())
    }

    @Test
    fun `semesterIdsWithExams offers only the semesters that published a schedule`() {
        val domain = VtopIngestor.fromLegacy(twoSemesterExamSnapshot())
        assertEquals(listOf("CH20262701"), Projections.semesterIdsWithExams(domain))
        assertTrue(Projections.semesterIdsWithExams(VtopIngestor.fromLegacy(AppDataSnapshot())).isEmpty())
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

    // ── margin: bunkable and classes-to-target ──

    @Test
    fun `bunkable is the surplus that keeps you exactly on the target`() {
        assertEquals(10, Projections.bunkableClasses(30, 30, 75f))
        assertEquals(2, Projections.bunkableClasses(9, 10, 75f))
        // On the line already: one more miss drops below 75, so nothing is spare.
        assertEquals(0, Projections.bunkableClasses(8, 10, 75f))
        // Below target: never a negative allowance.
        assertEquals(0, Projections.bunkableClasses(7, 10, 75f))
        assertEquals(0, Projections.bunkableClasses(0, 10, 75f))
    }

    @Test
    fun `bunking the reported count lands on the target and one more breaks it`() {
        // Every case here is at or above 75%: below target the allowance is 0 by design,
        // so "lands on the target" would not hold and is asserted separately instead.
        for ((attended, total) in listOf(9 to 12, 10 to 12, 11 to 12, 30 to 30, 76 to 99, 51 to 68)) {
            val n = assertNotNull(Projections.bunkableClasses(attended, total, 75f), "$attended/$total")
            assertTrue(
                attended.toDouble() / (total + n) >= 0.75,
                "$attended/$total bunkable=$n leaves ${attended.toDouble() / (total + n)} below 75%",
            )
            assertTrue(
                attended.toDouble() / (total + n + 1) < 0.75,
                "$attended/$total bunkable=$n allows one more than it should",
            )
        }
    }

    @Test
    fun `a lab halves the bunkable count`() {
        // Five spare hours is two-and-a-half lab sessions; Node floors the halving too.
        assertEquals(5, Projections.bunkableClasses(16, 16, 75f))
        assertEquals(2, Projections.bunkableClasses(16, 16, 75f, isLab = true))
        // 11/12 leaves 2 spare hours -> 1 lab session.
        assertEquals(2, Projections.bunkableClasses(11, 12, 75f))
        assertEquals(1, Projections.bunkableClasses(11, 12, 75f, isLab = true))
        // 10/12 leaves only 1 spare hour, which is half a lab session -> 0.
        assertEquals(0, Projections.bunkableClasses(10, 12, 75f, isLab = true))
    }

    @Test
    fun `bunkable is null when there is nothing held or no usable target`() {
        assertNull(Projections.bunkableClasses(10, 0, 75f))
        assertNull(Projections.bunkableClasses(10, -3, 75f))
        assertNull(Projections.bunkableClasses(10, 10, 0f))
    }

    @Test
    fun `classesToTarget counts missed classes in the denominator too`() {
        // 7/10 is 70%. Attending 2 more gives 9/12 = 75% - not 7/10 + 2/10 = 76.7%,
        // because the two misses also sit in the denominator.
        assertEquals(2, Projections.classesToTarget(7, 10, 75f))
        assertEquals(20, Projections.classesToTarget(10, 20, 75f))
        assertEquals(6, Projections.classesToTarget(6, 10, 75f))
    }

    @Test
    fun `classesToTarget returns null once the target is met`() {
        assertNull(Projections.classesToTarget(8, 10, 75f))  // 80%
        assertNull(Projections.classesToTarget(9, 12, 75f))  // exactly 75%
        assertNull(Projections.classesToTarget(10, 10, 75f))
        assertNull(Projections.classesToTarget(10, 0, 75f))
        assertNull(Projections.classesToTarget(10, 10, 0f))
        assertNull(Projections.classesToTarget(10, 10, 100f)) // would divide by zero
    }

    @Test
    fun `attending the reported count reaches the target and one fewer does not`() {
        for ((attended, total) in listOf(7 to 10, 10 to 20, 6 to 10, 1 to 8, 55 to 74)) {
            val n = assertNotNull(Projections.classesToTarget(attended, total, 75f), "$attended/$total")
            assertTrue(
                (attended + n).toDouble() / (total + n) >= 0.75,
                "$attended/$total needs=$n but lands below 75%",
            )
            if (n > 0) {
                assertTrue(
                    (attended + n - 1).toDouble() / (total + n - 1) < 0.75,
                    "$attended/$total needs=$n but one fewer would already suffice",
                )
            }
        }
    }

    @Test
    fun `classesToTarget halves the deficit for a lab`() {
        // Six hours short is three lab sessions, not six.
        assertEquals(6, Projections.classesToTarget(6, 10, 75f))
        assertEquals(3, Projections.classesToTarget(6, 10, 75f, isLab = true))
        assertEquals(1, Projections.classesToTarget(7, 10, 75f, isLab = true))
    }

    // ---- currentSemesterAttendance ------------------------------------------

    /** A semester whose courses carry attendance, plus ones that do not. */
    private fun semester(
        id: String,
        withAttendance: List<String>,
        withoutAttendance: List<String> = emptyList(),
    ): Pair<String, SemesterData> = id to SemesterData(
        semesterId = id,
        semesterName = "Semester $id",
        courses = (withAttendance + withoutAttendance).associate { code ->
            code to StoredCourse(
                courseCode = code,
                courseTitle = "Course $code",
                courseType = "Theory Only",
                slots = listOf("A1"),
                attendance = if (code in withAttendance) StoredAttendance(10, 12, "83", emptyList()) else null,
            )
        },
    )

    @Test
    fun `currentSemesterAttendance reports from the semester holding the data, not the selection`() {
        val legacy = AppDataSnapshot(
            academic = AcademicData(
                semesters = mapOf(
                    // Older id, but the two courses that report attendance live here.
                    semester("CH20252601", listOf("BACSE106", "BACSE102")),
                    // Selected, and newer, but only one course reports - and one never will.
                    semester("CH20262701", listOf("BACSE101"), listOf("BACSE109")),
                ),
            ),
        )
        val domain = VtopIngestor.fromLegacy(legacy, selectedSemesterId = "CH20262701")

        // The two views disagree by design: the list the predictor renders from follows the data.
        assertEquals("CH20262701", assertNotNull(Projections.activeSemester(domain)).id)
        assertEquals(
            listOf("BACSE102", "BACSE106"),
            Projections.currentSemesterAttendance(domain).map { it.courseCode }.sorted(),
        )
    }

    @Test
    fun `currentSemesterAttendance breaks a tie on semester id`() {
        val legacy = AppDataSnapshot(
            academic = AcademicData(
                semesters = mapOf(
                    semester("CH20252601", listOf("BACSE106")),
                    semester("CH20262701", listOf("BACSE101")),
                ),
            ),
        )
        val domain = VtopIngestor.fromLegacy(legacy, selectedSemesterId = "CH20252601")

        // Equal counts, so the higher id wins - even though the user picked the lower one.
        assertEquals(
            listOf("BACSE101"),
            Projections.currentSemesterAttendance(domain).map { it.courseCode },
        )
    }

    @Test
    fun `currentSemesterAttendance is empty when no semester has attendance`() {
        val legacy = AppDataSnapshot(
            academic = AcademicData(
                semesters = mapOf(semester("CH20262701", withAttendance = emptyList(), withoutAttendance = listOf("BACSE101"))),
            ),
        )
        assertEquals(emptyList(), Projections.currentSemesterAttendance(VtopIngestor.fromLegacy(legacy)))
    }

    @Test
    fun `currentSemesterAttendance still lists a course with no attendance yet`() {
        val legacy = AppDataSnapshot(
            academic = AcademicData(
                semesters = mapOf(semester("CH20262701", listOf("BACSE106"), listOf("BACSE109"))),
            ),
        )
        val course = Projections.currentSemesterAttendance(VtopIngestor.fromLegacy(legacy))
            .single { it.courseCode == "BACSE109" }

        // The predictor needs the whole roster to know which days this course meets, so a
        // course that has never reported comes through with zeroes rather than being dropped.
        assertEquals(0, course.totalClasses)
        assertEquals(0, course.attendedClasses)
        assertEquals("", course.attendancePercentage)
    }
}
