package com.amazecc.app.shared.ui.screens.m3e

import com.amazecc.app.shared.domain.Academics
import com.amazecc.app.shared.domain.DomainSnapshot
import com.amazecc.app.shared.domain.Grade
import com.amazecc.app.shared.domain.GradeComponent
import com.amazecc.app.shared.domain.overhaul.M3EGradeBreakdownRow
import com.amazecc.app.shared.domain.overhaul.M3EGradeScreen
import com.amazecc.app.shared.model.AllGradesRes
import com.amazecc.app.shared.model.GradeBreakdown
import com.amazecc.app.shared.model.GradeItem
import com.amazecc.app.shared.model.GradeRange
import com.amazecc.app.shared.model.SemesterGradeResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Headless checks for the grade-history host's published-data wiring. */
class GradeHistoryUiStateTest {

    private fun range() = GradeRange("90", "80", "70", "60", "50", "40", "0")

    private fun breakdown(): List<GradeBreakdown> = listOf(
        GradeBreakdown("CAT 1", "20", "40", "Present", "18", "8.0"),
        GradeBreakdown("FAT", "30", "60", "Present", "28", "18.0"),
    )

    private fun published() = AllGradesRes(
        semesters = mapOf(
            "CH20252601" to SemesterGradeResult(
                gpa = "9.0",
                grades = listOf(
                    GradeItem(
                        courseCode = "BAEEE101", courseTitle = "Basic Engineering",
                        courseType = "Embedded Theory", credits = "4.0",
                        grandTotal = "85", grade = "A",
                        details = breakdown(), range = range(),
                    ),
                ),
            ),
        ),
    )

    /** The hidden theory/lab halves carry no credits in grades.html:201-259. */
    private fun snapshotWithHiddenHalves() = DomainSnapshot(
        academics = Academics(
            semesters = mapOf(
                "CH20252601" to com.amazecc.app.shared.domain.Semester(
                    id = "CH20252601",
                    courses = mapOf(
                        "BAEEE101" to com.amazecc.app.shared.domain.Course(
                            code = "BAEEE101", credits = "4.0",
                            grade = Grade(letter = "A", grandTotal = "85"),
                        ),
                        "BAEEE101(ETH)" to com.amazecc.app.shared.domain.Course(
                            code = "BAEEE101(ETH)", credits = "",
                        ),
                        "BAEEE101(ELA)" to com.amazecc.app.shared.domain.Course(
                            code = "BAEEE101(ELA)", credits = "",
                        ),
                    ),
                ),
            ),
        ),
    )

    @Test
    fun attemptedCreditsUseTheAggregateRowAndNeverDoubleCountHiddenHalves() {
        val state = buildGradesHistoryState(
            snapshot = snapshotWithHiddenHalves(), allGrades = published(),
        )
        assertEquals(4, state.totalCreditsAttempted)
        val record = state.semesters.single { it.semesterId == "CH20252601" }
        assertEquals(4, record.courses.single { it.courseCode == "BAEEE101" }.credits)
    }

    @Test
    fun creditsFallBackToTheAggregateWhenTheMarksSnapshotIsMissing() {
        val state = buildGradesHistoryState(snapshot = DomainSnapshot(), allGrades = published())
        assertEquals(4, state.totalCreditsAttempted)
    }

    @Test
    fun courseSubpageCarriesRealGrandTotalGradeScaleCreditsAndReconciliation() {
        val state = buildGradesHistoryState(
            snapshot = snapshotWithHiddenHalves(), allGrades = published(),
            screen = M3EGradeScreen.COURSE, selectedCourseCode = "BAEEE101",
        )
        val detail = assertNotNull(state.courseDetail)
        assertEquals("85%", detail.overallText)
        assertEquals("9 / 10", detail.pointsLabel)
        assertEquals("4.0 cr", detail.creditsLabel)
        assertEquals("A", detail.grade)
        assertEquals(2, detail.breakdown.size)
        assertEquals("S", detail.gradeScale.first().grade)
        assertTrue(detail.gradeScale.first { it.grade == "A" }.isYours)
        // 8.0 + 18.0 = 26.0, not the published 85 — the note explains the gap.
        assertNotNull(detail.reconciliationText)
        assertTrue(detail.gradeScale.any { it.isYours })
    }

    @Test
    fun weightedReconciliationIsSilentWhenTheWeightsAgree() {
        val note = M3EGradeComputations.reconciliationNote(
            grandTotal = "26",
            breakdown = breakdown(),
        )
        assertNull(note)
    }

    @Test
    fun groupedAssessmentsFollowTheVTOPMarkTitleOrder() {
        val rows = listOf(
            M3EGradeBreakdownRow("Lab CAT", "20", "8", "20", 20.0, "Lab"),
            M3EGradeBreakdownRow("Theory CAT", "40", "18", "20", 40.0, "Theory"),
            M3EGradeBreakdownRow("Component 2", "20", "9", "20", 20.0, "Component 2"),
            M3EGradeBreakdownRow("Component 1", "20", "9", "20", 20.0, "Component 1"),
        )
        val groups = assertNotNull(M3EGradeComputations.groupAssessmentsByType(rows))
        assertEquals(listOf("Theory", "Lab", "Component 1", "Component 2"), groups.map { it.type })
    }

    @Test
    fun untaggedBreakdownKeepsTheFlatList() {
        val rows = listOf(M3EGradeBreakdownRow("CAT 1", "40", "18", "20", 40.0))
        assertNull(M3EGradeComputations.groupAssessmentsByType(rows))
    }

    @Test
    fun gradeComponentsKeepTheWeightedContributionSeparateFromTheRawScore() {
        val components = assertNotNull(M3EGradeComputations.gradeComponents(breakdown()))
        val first: GradeComponent = components.first()
        assertEquals("CAT 1", first.name)
        assertEquals("18", first.score)
        assertEquals("8.0", first.weightage)
        assertEquals(26.0, com.amazecc.app.shared.domain.GradeHistory.weightedTotal(components))
    }
}
