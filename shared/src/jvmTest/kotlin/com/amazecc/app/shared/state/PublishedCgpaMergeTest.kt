package com.amazecc.app.shared.state

import com.amazecc.app.shared.api.buildAllGradesFromGradeHistory
import com.amazecc.app.shared.model.AllGradesRes
import com.amazecc.app.shared.model.CgpaSummary
import com.amazecc.app.shared.model.GradeCounts
import com.amazecc.app.shared.model.GradeCurriculumBasket
import com.amazecc.app.shared.model.GradeItem
import com.amazecc.app.shared.model.OverallGrades
import com.amazecc.app.shared.model.SemesterGradeResult
import com.amazecc.app.shared.vtop.VtopGradeHistoryResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The published CGPA is a degree-wide fact from grade history's `overall.cgpa` block. It must be
 * carried into the academic snapshot whole — an SGPA from any one semester is not a substitute.
 */
class PublishedCgpaMergeTest {

    private fun localHistory() = VtopGradeHistoryResult(
        ok = true,
        courses = listOf(
            com.amazecc.app.shared.model.GradeHistoryCourse(
                code = "BCSE101",
                title = "Programming and Data Structures",
                type = "Theory Only",
                credits = "4",
                grade = "A"
            )
        ),
        cgpa = CgpaSummary(
            creditsRegistered = "160",
            creditsEarned = "158",
            cgpa = "8.74",
            grades = GradeCounts(S = 12, A = 30, B = 8)
        ),
        baskets = listOf(GradeCurriculumBasket("University Core", "20", "20"))
    )

    @Test
    fun localGradeResponseMapsWholePublishedCgpaIntoSnapshot() {
        val res = buildAllGradesFromGradeHistory(localHistory(), "CH20262701")

        val snapshot = AcademicMerge.upsertGrades(AcademicData(), res)

        assertEquals(
            CgpaSummary(
                creditsRegistered = "160",
                creditsEarned = "158",
                cgpa = "8.74",
                grades = GradeCounts(S = 12, A = 30, B = 8)
            ),
            snapshot.publishedCgpa,
            "every subfield of overall.cgpa survives the merge"
        )
        // The existing per-semester merge must still happen alongside it.
        assertEquals(
            listOf("BCSE101"),
            snapshot.semesters["CH20262701"]?.courses?.keys?.toList()
        )
    }

    @Test
    fun remoteGradeResponseMapsPublishedCgpaAndKeepsSemesterGpa() {
        val res = AllGradesRes(
            semesters = mapOf(
                "CH20262701" to SemesterGradeResult(
                    gpa = "9.10",
                    grades = listOf(GradeItem(courseCode = "BCSE101", grade = "S"))
                )
            ),
            overall = OverallGrades(
                cgpa = CgpaSummary(cgpa = "8.91", creditsEarned = "160", creditsRegistered = "162"),
                curriculum = listOf(GradeCurriculumBasket("Core", "20", "20"))
            )
        )

        val snapshot = AcademicMerge.upsertGrades(AcademicData(), res)

        assertEquals("8.91", snapshot.publishedCgpa?.cgpa)
        assertEquals("160", snapshot.publishedCgpa?.creditsEarned)
        assertEquals("162", snapshot.publishedCgpa?.creditsRegistered)
        assertEquals("9.10", snapshot.semesters["CH20262701"]?.gpa, "SGPA stays on its semester")
    }

    @Test
    fun absentPublishOverallNeverFabricatesOrOverwrites() {
        val withoutOverall = AllGradesRes(
            grades = mapOf("CH20262701" to SemesterGradeResult(gpa = "7.50", grades = emptyList()))
        )

        val empty = AcademicMerge.upsertGrades(AcademicData(), withoutOverall)
        assertNull(empty.publishedCgpa, "no overall block means no published CGPA, not a zero")

        val published = CgpaSummary(cgpa = "8.74", creditsEarned = "158")
        val already = AcademicData(publishedCgpa = published)
        val kept = AcademicMerge.upsertGrades(already, withoutOverall)
        assertEquals(published, kept.publishedCgpa, "an overall-less response must not erase it")
    }
}
