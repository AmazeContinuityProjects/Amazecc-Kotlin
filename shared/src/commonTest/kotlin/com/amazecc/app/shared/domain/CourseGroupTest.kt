package com.amazecc.app.shared.domain

import com.amazecc.app.shared.model.AssessmentItem
import com.amazecc.app.shared.state.AcademicData
import com.amazecc.app.shared.state.AppDataSnapshot
import com.amazecc.app.shared.state.SemesterData
import com.amazecc.app.shared.state.StoredAttendance
import com.amazecc.app.shared.state.StoredCourse
import com.amazecc.app.shared.state.StoredMarks
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The course-grouping projection the academic spine binds to.
 *
 * These replace `CourseDashboard.buildSemesterGroups` / `CourseDetailScreen.findCourseGroup`,
 * which could not be tested at all because they read `AppState.semesterMap` and the store
 * directly. Every case here pins behaviour the two screens depend on and that used to differ
 * between them.
 */
class CourseGroupTest {

    // ---- fixtures -------------------------------------------------------------

    /**
     * The real VTOP course-type strings, captured from attendance__CH20262701.html - the same
     * shape `ProjectionsTest` uses, so both suites read against one ground truth.
     */
    private fun legacySnapshot(): AppDataSnapshot = AppDataSnapshot(
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
                            venue = "AB1-101",
                            credits = "3",
                            classId = "CH2026270102069",
                            attendance = StoredAttendance(26, 28, "93", emptyList()),
                            marks = StoredMarks(
                                assessments = listOf(
                                    AssessmentItem(
                                        title = "Quiz 1",
                                        maxMark = "20",
                                        scoredMark = "18",
                                        weightageMark = "9.0",
                                        weightagePercent = "10",
                                    )
                                ),
                                totalMark = 9.0,
                                maxMark = 10.0,
                                mergedFrom = "ETH",
                            ),
                        ),
                        // A standalone lab half: theory is null, lab carries it.
                        "BACSE102" to StoredCourse(
                            courseCode = "BACSE102",
                            courseTitle = "Problem Solving Using Java",
                            courseType = "Lab Only",
                            faculty = "52282 SHEENA CHRISTABEL PRAVIN SENSE",
                            slots = listOf("L31"),
                            attendance = StoredAttendance(32, 36, "89", emptyList()),
                        ),
                        // Blank codes are filtered out by the builder.
                        "   " to StoredCourse(courseCode = "   ", courseTitle = "Ghost"),
                    ),
                ),
                "CH20252601" to SemesterData(
                    semesterId = "CH20252601",
                    semesterName = "Summer Semester 2025-26",
                    courses = mapOf(
                        "BAMAT204" to StoredCourse(
                            courseCode = "BAMAT204",
                            courseTitle = "Discrete Mathematics",
                            courseType = "Theory Only",
                        ),
                    ),
                ),
            )
        )
    )

    /**
     * Two rows for one base code - the legacy shape the builder's pairing branch exists for.
     *
     * A current store keys by bare code so this cannot occur on its own; the branch still has to
     * fold a snapshot that predates the normalisation, so it is exercised directly.
     */
    private fun twoRowSnapshot(): DomainSnapshot = DomainSnapshot(
        academics = Academics(
            semesters = mapOf(
                "S1" to Semester(
                    id = "S1",
                    name = "Fall Semester 2026-27",
                    courses = mapOf(
                        "BACSE101" to Course(
                            code = "BACSE101",
                            title = "Programming",
                            type = "Theory Only",
                            slots = listOf("C1"),
                        ),
                        "BACSE101(L)" to Course(
                            code = "BACSE101(L)",
                            title = "Programming Lab",
                            type = "Lab Only",
                            slots = listOf("L31"),
                        ),
                    ),
                )
            )
        )
    )

    private fun domain() = VtopIngestor.fromLegacy(legacySnapshot())

    // ---- buildCourseGroups ---------------------------------------------------

    @Test
    fun `one group per base course code per semester`() {
        val groups = buildCourseGroups(domain())

        // The blank code is dropped, so exactly the two real courses of CH20262701 survive
        // alongside the single course of CH20252601.
        assertEquals(3, groups.size)
        assertEquals(
            setOf("BACSE106", "BACSE102", "BAMAT204"),
            groups.map { it.courseCode }.toSet(),
        )
    }

    @Test
    fun `a lab-only course lands in the lab slot, not the theory slot`() {
        val groups = buildCourseGroups(domain())
        val lab = groups.single { it.courseCode == "BACSE102" }

        assertNull(lab.theory, "a Lab Only course must not be filed as theory")
        assertNotNull(lab.lab)
        assertFalse(lab.isEmbedded)
        assertEquals("Problem Solving Using Java", lab.courseTitle)
    }

    @Test
    fun `an embedded pair splits into theory and lab halves`() {
        val groups = buildCourseGroups(twoRowSnapshot())

        assertEquals(1, groups.size, "both rows share a base code, so they are one card")
        val group = groups.single()

        assertTrue(group.isEmbedded, "theory and lab are both present")
        assertEquals("Theory Only", group.theory?.courseType)
        assertEquals("Lab Only", group.lab?.courseType)
        assertEquals("Theory Only", group.theoryAtt?.courseType)
        assertEquals("Lab Only", group.labAtt?.courseType)
        // The theory half titles the card, matching what the dashboard renders.
        assertEquals("Programming", group.courseTitle)
        assertEquals("BACSE101", group.courseCode)
    }

    @Test
    fun `the current semester shows marks instead of a grade`() {
        val snapshot = domain()

        val current = buildCourseGroups(snapshot, selectedSemester = "CH20262701")
        // Only the semester the filter is on drops its grade - the others are still "previous".
        current.filter { it.semesterSubId == "CH20262701" }
            .forEach { assertNull(it.grade, "the selected semester must carry no grade") }
        assertTrue(
            current.filter { it.semesterSubId != "CH20262701" }.all { it.grade != null },
            "a semester the filter is not on keeps its grade",
        )

        val all = buildCourseGroups(snapshot, selectedSemester = "All")
        // "All" selects nothing, so every semester is a "previous" one and carries a grade.
        assertEquals(3, all.count { it.grade != null })
    }

    @Test
    fun `semester name prefers the snapshot, then the lookup, then the id`() {
        val snapshot = domain()
        val names = mapOf("CH20252601" to "Summer 2025-26 (lookup)")

        val groups = buildCourseGroups(snapshot, semesterNames = names)

        // The snapshot already carries a name for both semesters, so the lookup never wins.
        assertEquals("Fall Semester 2026-27", groups.first { it.courseCode == "BACSE106" }.semesterName)

        val noNames = buildCourseGroups(snapshot, semesterNames = emptyMap())
        assertEquals("Fall Semester 2026-27", noNames.first { it.courseCode == "BACSE106" }.semesterName)

        // A semester with neither name nor lookup falls back to its id.
        val nameless = DomainSnapshot(
            academics = Academics(
                semesters = mapOf(
                    "S9" to Semester(id = "S9", courses = mapOf("X100" to Course(code = "X100")))
                )
            )
        )
        assertEquals("S9", buildCourseGroups(nameless).single().semesterName)
    }

    @Test
    fun `attendance and marks fields are read from the snapshot`() {
        val group = buildCourseGroups(domain()).single { it.courseCode == "BACSE106" }

        val att = assertNotNull(group.theoryAtt)
        assertEquals(26, att.attendedClasses)
        assertEquals(28, att.totalClasses)
        assertEquals("93", att.attendancePercentage)
        assertEquals("C1+TC1", att.slotName)
        assertEquals("AB1-101", att.slotVenue)
        assertEquals("50863 KARTHIK R CPS", att.faculty)

        val marks = assertNotNull(group.theory)
        assertEquals("CH2026270102069", marks.classNbr)
        assertEquals("Embedded Theory", marks.courseType)
        assertEquals(9.0, marks.totalMark)
        assertEquals(10.0, marks.maxMark)
        assertEquals("Quiz 1", marks.assessments.single().name)
    }

    // ---- findCourseGroup -----------------------------------------------------

    @Test
    fun `findCourseGroup prefers the requested semester`() {
        val snapshot = domain()

        val group = assertNotNull(findCourseGroup("BAMAT204", "CH20252601", snapshot))
        assertEquals("CH20252601", group.semesterSubId)
    }

    @Test
    fun `findCourseGroup falls back to a semester that still holds the course`() {
        val snapshot = domain()

        // Asked for a semester the course is not in; the page still has to render.
        val group = assertNotNull(findCourseGroup("BAMAT204", "CH20262701", snapshot))
        assertEquals("CH20252601", group.semesterSubId)
    }

    @Test
    fun `findCourseGroup normalises a stale suffixed key`() {
        val snapshot = domain()

        val group = assertNotNull(findCourseGroup("BACSE101(L)", "S1", twoRowSnapshot()))
        assertEquals("BACSE101", group.courseCode)
        assertTrue(group.isEmbedded)

        // And an unknown code resolves to nothing rather than the wrong course.
        assertNull(findCourseGroup("ZZZZ999", "CH20262701", snapshot))
    }

    @Test
    fun `findCourseGroup honours the current-semester grade rule`() {
        val snapshot = domain()

        assertNull(findCourseGroup("BAMAT204", "CH20252601", snapshot, selectedSemester = "CH20252601")?.grade)
        assertNotNull(findCourseGroup("BAMAT204", "CH20252601", snapshot, selectedSemester = "All")?.grade)
    }

    // ---- gradeHistory --------------------------------------------------------

    @Test
    fun `gradeHistory lists matching semesters newest first`() {
        val items = gradeHistory(domain(), "BACSE106")

        assertEquals(listOf("CH20262701"), items.map { it.first })
        // The shim always produced a value, blank or not; the trend line treats blank as missing.
        assertEquals("", assertNotNull(items.single().second).grade)

        val none = gradeHistory(domain(), "ZZZZ999")
        assertTrue(none.isEmpty())
    }

    @Test
    fun `gradeHistory groups by base code so a suffixed row still matches`() {
        val items = gradeHistory(twoRowSnapshot(), "BACSE101")

        // Both rows share the base code, so both are reported.
        assertEquals(2, items.size)
    }
}
