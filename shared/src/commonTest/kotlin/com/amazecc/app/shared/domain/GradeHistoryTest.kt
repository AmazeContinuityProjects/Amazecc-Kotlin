package com.amazecc.app.shared.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The port of `AmazeCC/src/__tests__/gradeHistory.test.ts`.
 *
 * Three of the cases below are regressions for defects that shipped on the inline derivations this
 * file replaces: the semester label that called summer "Winter", the trend that assumed every
 * course is out of 100, and the average that included courses with no score as zeroes.
 */
class GradeHistoryTest {

    private val fall25 = "CH20252601"
    private val winter25 = "CH20252605"
    private val summer25 = "CH20252607"
    private val fall26 = "CH20262701"

    private fun course(
        code: String = "CSE1001",
        type: String = "Theory",
        grandTotal: String? = "88",
        letter: String? = "A",
    ) = Course(
        code = code,
        title = "Algorithms",
        type = type,
        grade = Grade(letter = letter, grandTotal = grandTotal),
    )

    /** The same row as [course], but the flattened view a distribution actually reads. */
    private fun grade(
        code: String = "CSE1001",
        type: String = "Theory",
        grandTotal: String = "88",
        letter: String = "A",
    ) = CourseGrade(
        courseCode = code,
        courseTitle = "Algorithms",
        courseType = type,
        grandTotal = grandTotal,
        grade = letter,
        details = null,
        range = null,
    )

    private fun semester(gpa: String?, vararg courses: Pair<String, Course>) = Semester(
        id = "",
        gpa = gpa,
        courses = courses.toMap(),
    )

    private fun payload(): DomainSnapshot = DomainSnapshot(
        academics = Academics(
            semesters = linkedMapOf(
                // Winter first on purpose: the map's key order must not decide the output order.
                winter25 to semester("8.10", "CSE1001" to course()),
                fall25 to semester(
                    "9.20",
                    "CSE1001" to course(grandTotal = "95", letter = "S"),
                    "MAT1001" to course(code = "MAT1001", grandTotal = "72", letter = "C"),
                ),
            ),
        ),
    )

    private fun withSemester(id: String, gpa: String?, vararg courses: Pair<String, Course>) =
        DomainSnapshot(
            academics = Academics(semesters = mapOf(id to semester(gpa, *courses))),
        )

    // -- semesterRows ------------------------------------------------------

    @Test
    fun `returns terms oldest first, regardless of the key order`() {
        assertEquals(listOf(fall25, winter25), GradeHistory.semesterRows(payload()).map { it.id })
    }

    @Test
    fun `names each term with the shared semester formatter`() {
        // The bug this pins: the screen derived its own label as
        // `id.endsWith("1") ? Fall : Winter`, which labelled **summer** "Winter".
        val rows = GradeHistory.semesterRows(
            payload().plusSemester(summer25, semester("8.50", "CSE1001" to course()))
        )
        assertEquals(
            listOf("Fall 2025-26", "Winter 2025-26", "Summer 2025-26"),
            rows.map { it.label },
        )
    }

    @Test
    fun `the formatter hands back an id it does not recognise`() {
        assertEquals("nonsense", GradeHistory.semesterName("nonsense"))
        assertEquals("", GradeHistory.semesterName(""))
        assertEquals("CH1", GradeHistory.semesterName("CH1"))
        assertEquals("CH202526012", GradeHistory.semesterName("CH202526012"))
    }

    @Test
    fun `the formatter names an unknown term rather than hiding it`() {
        assertEquals("Term 09 2025-26", GradeHistory.semesterName("CH20252609"))
        assertEquals("Term 99 2025-26", GradeHistory.semesterName("CH20252699"))
    }

    @Test
    fun `keeps a term with no grades rather than dropping it`() {
        // A term the college has not published yet is information, not noise.
        val rows = GradeHistory.semesterRows(payload().plusSemester(fall26, semester(null)))
        assertEquals(listOf(fall25, winter25, fall26), rows.map { it.id })
        assertTrue(rows[2].courses.isEmpty())
        assertEquals(0.0, rows[2].gpa)
    }

    @Test
    fun `survives a snapshot holding no terms, and a null entry`() {
        assertTrue(GradeHistory.semesterRows(DomainSnapshot()).isEmpty())
        assertNull(GradeHistory.toSemesterRow(fall25, null))
    }

    @Test
    fun `averages only the courses that are actually scored`() {
        val rows = GradeHistory.semesterRows(
            withSemester(
                winter25, "8.00",
                "A" to course(code = "A", grandTotal = "90"),
                "B" to course(code = "B", grandTotal = "", letter = ""),
                "C" to course(code = "C", grandTotal = null, letter = "N"),
            ),
        )
        val winter = rows.single()
        assertEquals(1, winter.scored.size)
        assertEquals(90.0, winter.avgScore)
        // Two courses, three rows in `courses` - the unscored ones are still listed.
        assertEquals(3, winter.courses.size)
    }

    @Test
    fun `reports zero for an average when nothing is scored`() {
        val rows = GradeHistory.semesterRows(
            withSemester(winter25, "8.00", "A" to course(code = "A", grandTotal = "")),
        )
        assertEquals(0.0, rows.single().avgScore)
    }

    @Test
    fun `rounds to two decimals so a page never renders a repeating tail`() {
        val rows = GradeHistory.semesterRows(
            withSemester(
                winter25, "8.00",
                "A" to course(code = "A", grandTotal = "70"),
                "B" to course(code = "B", grandTotal = "72.5"),
            ),
        )
        assertEquals(71.25, rows.single().avgScore)
    }

    @Test
    fun `averages letter grades as points, ignoring ungraded courses`() {
        val rows = GradeHistory.semesterRows(
            withSemester(
                fall25, "9.00",
                "A" to course(code = "A", letter = "S"),
                "B" to course(code = "B", letter = "C"),
                "C" to course(code = "C", letter = "N"),
            ),
        )
        // (10 + 7) / 2, not / 3 - an `N` is the absence of a grade, not a zero.
        assertEquals(8.5, rows.single().avgPoints)
    }

    // -- latestSemester / bestTerm ----------------------------------------

    @Test
    fun `opens on the newest term that has courses in it`() {
        val rows = GradeHistory.semesterRows(payload().plusSemester(fall26, semester(null)))
        assertEquals(winter25, GradeHistory.latestSemester(rows)?.id)
    }

    @Test
    fun `falls back to the newest term when none has courses`() {
        val rows = GradeHistory.semesterRows(payload().plusSemester(fall26, semester(null)))
            .map { it.copy(courses = emptyList(), scored = emptyList()) }
        assertEquals(fall26, GradeHistory.latestSemester(rows)?.id)
        assertNull(GradeHistory.latestSemester(emptyList()))
    }

    @Test
    fun `picks the highest GPA, ignoring terms that have none`() {
        val rows = GradeHistory.semesterRows(payload().plusSemester(fall26, semester(null)))
        val best = GradeHistory.bestTerm(rows)
        assertEquals(fall25, best?.id)
        assertEquals(9.2, best?.gpa)
    }

    @Test
    fun `returns null rather than inventing a best term`() {
        assertNull(GradeHistory.bestTerm(emptyList()))
        assertNull(GradeHistory.bestTerm(GradeHistory.semesterRows(withSemester(fall25, null))))
    }

    // -- cumulativeGpa -----------------------------------------------------

    @Test
    fun `prefers the figure VTOP published`() {
        val c = GradeHistory.cumulativeGpa(
            GradeHistory.semesterRows(payload()),
            cgpa = "8.75",
            creditsEarned = "96",
            creditsRequired = "160",
        )
        assertEquals(GradeHistory.Cumulative(8.75, 96.0, 160.0, GradeHistory.CumulativeSource.VTOP), c)
    }

    @Test
    fun `falls back to a mean of the terms, and says that is what it did`() {
        // Unweighted, so it is NOT the real CGPA - a 3-credit term counts the same as a 24-credit
        // one. The source is the point.
        val c = GradeHistory.cumulativeGpa(
            GradeHistory.semesterRows(payload()),
            cgpa = "",
            creditsRequired = "160",
        )
        assertEquals(GradeHistory.CumulativeSource.DERIVED, c.source)
        assertEquals(8.65, c.value) // (9.20 + 8.10) / 2
        assertEquals(160.0, c.creditsRequired)
    }

    @Test
    fun `reports nothing when there is no figure and no terms`() {
        assertEquals(
            GradeHistory.Cumulative(0.0, 0.0, 0.0, GradeHistory.CumulativeSource.NONE),
            GradeHistory.cumulativeGpa(emptyList()),
        )
    }

    @Test
    fun `ignores a published zero in favour of a real figure`() {
        // `cgpa = "0"` is what an unsynced payload looks like, and it must not win.
        val c = GradeHistory.cumulativeGpa(GradeHistory.semesterRows(payload()), cgpa = "0")
        assertEquals(GradeHistory.CumulativeSource.DERIVED, c.source)
    }

    // -- gradeDistribution -------------------------------------------------

    @Test
    fun `counts letters and orders them best to worst`() {
        val rows = GradeHistory.gradeDistribution(
            listOf(
                grade(letter = "B"),
                grade(letter = "S"),
                grade(letter = "A"),
                grade(letter = "A"),
                grade(letter = "C"),
            ),
        )
        assertEquals(listOf("S", "A", "B", "C"), rows.map { it.grade })
        assertEquals(listOf(1, 2, 1, 1), rows.map { it.count })
        assertEquals(40.0, rows.first { it.grade == "A" }.share)
    }

    @Test
    fun `excludes ungraded and unknown courses from the share`() {
        // A term with `N`s in it must not make an `S` look like a minority, and `N` is not a grade
        // the student received - it is the absence of one.
        val rows = GradeHistory.gradeDistribution(
            listOf(
                grade(letter = "S"),
                grade(letter = "N"),
                grade(letter = ""),
                grade(letter = "P"),
            ),
        )
        assertEquals(1, rows.size)
        assertEquals("S", rows[0].grade)
        assertEquals(1, rows[0].count)
        assertEquals(100.0, rows[0].share)
    }

    @Test
    fun `is empty when no course carries a graded letter`() {
        assertTrue(GradeHistory.gradeDistribution(emptyList()).isEmpty())
        assertTrue(
            GradeHistory.gradeDistribution(listOf(grade(letter = ""), grade(letter = "N"))).isEmpty(),
        )
    }

    @Test
    fun `a letter outside the ladder is dropped, not bucketed`() {
        val rows = GradeHistory.gradeDistribution(listOf(grade(letter = "Z"), grade(letter = "A")))
        assertEquals(listOf("A"), rows.map { it.grade })
    }

    // -- averageByType -----------------------------------------------------

    @Test
    fun `keeps theory and lab apart, which is the point of the split`() {
        val rows = GradeHistory.averageByType(
            listOf(
                grade(type = "Theory", grandTotal = "90"),
                grade(code = "X", type = "Theory", grandTotal = "80"),
                grade(code = "Y", type = "Lab", grandTotal = "60"),
            ),
        )
        assertEquals(listOf("Theory", "Lab"), rows.map { it.type })
        assertEquals(85.0, rows[0].avg)
        assertEquals(2, rows[0].count)
        assertEquals(60.0, rows[1].avg)
        assertEquals(1, rows[1].count)
    }

    @Test
    fun `buckets a course with no type as Other`() {
        assertEquals("Other", GradeHistory.averageByType(listOf(grade(type = ""))).single().type)
    }

    @Test
    fun `omits a type with nothing scored instead of showing zero`() {
        assertTrue(GradeHistory.averageByType(listOf(grade(grandTotal = ""))).isEmpty())
    }

    // -- gradePoints / isGradedLetter -------------------------------------

    @Test
    fun `maps the 10-point scale`() {
        assertEquals(10, GradeHistory.gradePoints("S"))
        assertEquals(9, GradeHistory.gradePoints("A"))
        assertEquals(5, GradeHistory.gradePoints("E"))
        assertEquals(0, GradeHistory.gradePoints("F"))
        assertEquals(0, GradeHistory.gradePoints("N"))
    }

    @Test
    fun `is case and whitespace tolerant`() {
        assertEquals(9, GradeHistory.gradePoints(" a "))
        assertTrue(GradeHistory.isGradedLetter("  s "))
        assertFalse(GradeHistory.isGradedLetter("N"))
        assertFalse(GradeHistory.isGradedLetter(null))
    }

    @Test
    fun `returns null for a letter it does not know`() {
        assertNull(GradeHistory.gradePoints("Z"))
        assertNull(GradeHistory.gradePoints(null))
        assertNull(GradeHistory.gradePoints(""))
    }

    // -- weightedTotal -----------------------------------------------------

    @Test
    fun `sums the weightage, not the raw marks`() {
        // A 40/50 worth 60% contributes 24, not 40.
        val total = GradeHistory.weightedTotal(
            listOf(
                GradeComponent(name = "Quiz 1", score = "40", weightage = "24"),
                GradeComponent(name = "Quiz 2", score = "30", weightage = "26"),
            ),
        )
        assertEquals(50.0, total)
    }

    @Test
    fun `distinguishes summing to nothing from having no usable breakdown`() {
        assertNull(GradeHistory.weightedTotal(null))
        assertNull(GradeHistory.weightedTotal(emptyList()))
        assertNull(GradeHistory.weightedTotal(listOf(GradeComponent(name = "Quiz 1", weightage = ""))))
        assertEquals(0.0, GradeHistory.weightedTotal(listOf(GradeComponent(name = "Quiz 1", weightage = "0"))))
    }

    @Test
    fun `skips the assessments that carry no weightage and uses the rest`() {
        val total = GradeHistory.weightedTotal(
            listOf(
                GradeComponent(name = "Quiz 1", weightage = "24"),
                GradeComponent(name = "Lab", weightage = ""),
            ),
        )
        assertEquals(24.0, total)
    }

    @Test
    fun `weightedTotalDiffers is quiet about rounding noise`() {
        assertFalse(
            GradeHistory.weightedTotalDiffers(
                grandTotal = "86",
                details = listOf(GradeComponent(name = "Quiz 1", weightage = "86.2")),
            ),
        )
    }

    @Test
    fun `weightedTotalDiffers flags a real disagreement`() {
        assertTrue(
            GradeHistory.weightedTotalDiffers(
                grandTotal = "88",
                details = listOf(GradeComponent(name = "Quiz 1", weightage = "72")),
            ),
        )
    }

    @Test
    fun `weightedTotalDiffers cannot fire without both figures`() {
        assertFalse(GradeHistory.weightedTotalDiffers(grandTotal = "88", details = null))
        assertFalse(
            GradeHistory.weightedTotalDiffers(
                grandTotal = "",
                details = listOf(GradeComponent(name = "Quiz 1", weightage = "88")),
            ),
        )
    }

    // -- isEmbeddedCourse --------------------------------------------------

    @Test
    fun `recognises the combined and half types VTOP publishes`() {
        assertTrue(GradeHistory.isEmbeddedCourse("Embedded Theory and Lab"))
        assertTrue(GradeHistory.isEmbeddedCourse("Embedded Theory"))
        assertTrue(GradeHistory.isEmbeddedCourse("Embedded Lab"))
    }

    @Test
    fun `does not claim theory-only, lab-only, project or online courses`() {
        assertFalse(GradeHistory.isEmbeddedCourse("Theory Only"))
        assertFalse(GradeHistory.isEmbeddedCourse("Lab Only"))
        assertFalse(GradeHistory.isEmbeddedCourse("Project"))
        assertFalse(GradeHistory.isEmbeddedCourse("Online Course"))
        assertFalse(GradeHistory.isEmbeddedCourse(""))
        assertFalse(GradeHistory.isEmbeddedCourse(null))
    }

    // -- semesterSortKey ---------------------------------------------------

    @Test
    fun `sorts a real id chronologically and anything else lexicographically`() {
        assertEquals("20252601", GradeHistory.semesterSortKey("CH20252601"))
        assertEquals("zzz", GradeHistory.semesterSortKey("zzz"))
        // A malformed CH id is not dropped - it sorts as itself.
        assertEquals("CH1", GradeHistory.semesterSortKey("CH1"))
    }
}

/** A copy of [snapshot] with one more semester appended. */
private fun DomainSnapshot.plusSemester(id: String, semester: Semester): DomainSnapshot =
    copy(academics = academics.copy(semesters = academics.semesters + (id to semester)))
