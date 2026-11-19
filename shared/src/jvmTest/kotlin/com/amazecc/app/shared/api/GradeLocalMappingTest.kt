package com.amazecc.app.shared.api

import com.amazecc.app.shared.model.CgpaSummary
import com.amazecc.app.shared.model.GradeCounts
import com.amazecc.app.shared.model.GradeCurriculumBasket
import com.amazecc.app.shared.model.GradeHistoryCourse
import com.amazecc.app.shared.state.AppSanitizers
import com.amazecc.app.shared.vtop.VtopGradeHistoryResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The seam [buildAllGradesFromGradeHistory] that `AmazeClient.getAllGrades()` uses for LOCAL
 * mode and the REMOTE-failure fallback. Exercises the exact regression that made every
 * LOCAL grade disappear: code-less GradeItems are dropped by the store sanitizer.
 */
class GradeLocalMappingTest {

    private fun history(): VtopGradeHistoryResult = VtopGradeHistoryResult(
        ok = true,
        courses = listOf(
            GradeHistoryCourse(
                code = "BCSE101",
                title = "Programming and Data Structures",
                type = "Theory Only",
                credits = "4",
                grade = "A",
                examMonth = "MAY-2026",
                resultDeclared = "10-JUN-2026",
                distributionType = "Regular"
            ),
            GradeHistoryCourse(
                code = "BCSE102L",
                title = "Programming Lab",
                type = "Lab Only",
                credits = "2",
                grade = "S"
            )
        ),
        cgpa = CgpaSummary(
            creditsRegistered = "160",
            creditsEarned = "158",
            cgpa = "8.74",
            grades = GradeCounts(S = 12, A = 30, B = 8)
        ),
        baskets = listOf(
            GradeCurriculumBasket("University Core", "20", "20"),
            GradeCurriculumBasket("Open Elective", "8", "6")
        )
    )

    @Test
    fun localHistoryMapsIntoOverallAndSemesters() {
        val result = buildAllGradesFromGradeHistory(history(), "CH20262701")

        assertTrue(result.success)
        val overall = assertNotNull(result.overall)
        assertEquals("8.74", overall.cgpa?.cgpa)
        assertEquals("158", overall.cgpa?.creditsEarned)
        assertEquals("160", overall.cgpa?.creditsRegistered)
        assertEquals(12, overall.cgpa?.grades?.S)
        assertEquals(30, overall.cgpa?.grades?.A)
        assertEquals(2, overall.curriculum.size)
        assertEquals("Open Elective", overall.curriculum[1].basketTitle)

        val semesters = assertNotNull(result.semesters)
        val semester = assertNotNull(semesters["CH20262701"])
        assertEquals("158", semester.creditsEarned)
        assertNull(semester.gpa, "per-semester GPA is not on this page — never fabricated")
        assertEquals(2, semester.grades.size)

        val first = semester.grades[0]
        assertEquals("BCSE101", first.courseCode)
        assertEquals("Programming and Data Structures", first.courseTitle)
        assertEquals("4", first.credits)
        assertEquals("A", first.grade)
        assertEquals("MAY-2026", first.examMonth)
        assertEquals("Regular", first.distributionType)
        assertEquals("", first.grandTotal)
    }

    @Test
    fun publishedCgpaIsExposedByTheResponseHelper() {
        assertEquals("8.74", buildAllGradesFromGradeHistory(history(), "CH20262701").publishedCgpa)
    }

    @Test
    fun mappedGradesSurviveTheStoreSanitizer() {
        val result = buildAllGradesFromGradeHistory(history(), "CH20262701")
        val cleaned = assertNotNull(AppSanitizers.sanitizeAllGrades(result))

        val semester = assertNotNull(cleaned.allSemesters["CH20262701"])
        assertEquals(2, semester.grades.size, "real course codes must not be dropped")
        assertEquals("BCSE102L", semester.grades[1].courseCode)
        // The published overall block is preserved verbatim through sanitisation.
        assertEquals("8.74", cleaned.publishedCgpa)
        assertEquals(2, cleaned.overall?.curriculum?.size)
    }

    @Test
    fun codeLessCourseStillFailsTheSanitizerGuard() {
        // Documents the old defect: without a real code the row is discarded. The parser now
        // rejects such rows up front, so this is a guard on the downstream contract.
        val bad = buildAllGradesFromGradeHistory(
            history().copy(
                courses = listOf(GradeHistoryCourse(code = "", title = "Ghost", credits = "3", grade = "A"))
            ),
            "CH20262701"
        )
        val cleaned = assertNotNull(AppSanitizers.sanitizeAllGrades(bad))
        assertEquals(0, cleaned.allSemesters["CH20262701"]?.grades?.size)
    }
}
