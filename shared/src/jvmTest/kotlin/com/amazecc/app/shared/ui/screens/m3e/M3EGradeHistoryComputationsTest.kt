package com.amazecc.app.shared.ui.screens.m3e

import com.amazecc.app.shared.domain.overhaul.M3EGradeCourseRow
import com.amazecc.app.shared.domain.overhaul.M3EGradePointTone
import com.amazecc.app.shared.domain.overhaul.M3EGradeTone
import com.amazecc.app.shared.domain.overhaul.M3EGradeTermRow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class M3EGradeHistoryComputationsTest {

    private fun row(code: String, grade: String, score: Double?, type: String = "Theory") =
        M3EGradeCourseRow(code, code, "${score ?: "—"}%", score, grade, M3EGradeComputations.toneForGrade(grade), false, type)

    @Test
    fun gradePointsAndBadgeFollowTheTenPointScale() {
        assertEquals(10, M3EGradeComputations.gradePoints("S"))
        assertEquals(9, M3EGradeComputations.gradePoints(" a "))
        assertEquals(0, M3EGradeComputations.gradePoints("F"))
        assertNull(M3EGradeComputations.gradePoints("N"))
        assertEquals("10 / 10", M3EGradeComputations.gradePointsLabel("S"))
        assertEquals("—", M3EGradeComputations.gradePointsLabel(""))
    }

    @Test
    fun gradeToneUsesTheSixSemanticHuesAndGreysUngraded() {
        assertEquals(M3EGradeTone.AMBER, M3EGradeComputations.toneForGrade("S"))
        assertEquals(M3EGradeTone.GREEN, M3EGradeComputations.toneForGrade("A"))
        assertEquals(M3EGradeTone.BLUE, M3EGradeComputations.toneForGrade("B"))
        assertEquals(M3EGradeTone.CYAN, M3EGradeComputations.toneForGrade("C"))
        assertEquals(M3EGradeTone.VIOLET, M3EGradeComputations.toneForGrade("D"))
        assertEquals(M3EGradeTone.RED, M3EGradeComputations.toneForGrade("E"))
        assertEquals(M3EGradeTone.RED, M3EGradeComputations.toneForGrade("F"))
        assertEquals(M3EGradeTone.GREY, M3EGradeComputations.toneForGrade("N"))
        assertEquals(M3EGradeTone.GREY, M3EGradeComputations.toneForGrade(null))
    }

    @Test
    fun gpaBarToneMatchesTheThresholds() {
        assertNull(M3EGradeComputations.pointTone(0.0))
        assertEquals(M3EGradePointTone.WARNING, M3EGradeComputations.pointTone(7.9))
        assertEquals(M3EGradePointTone.PRIMARY, M3EGradeComputations.pointTone(8.0))
        assertEquals(M3EGradePointTone.SUCCESS, M3EGradeComputations.pointTone(9.0))
        assertEquals(M3EGradeTone.GREEN, M3EGradeComputations.insightTone(9.35))
        assertEquals(M3EGradeTone.BLUE, M3EGradeComputations.insightTone(8.1))
        assertEquals(M3EGradeTone.AMBER, M3EGradeComputations.insightTone(7.0))
    }

    @Test
    fun distributionExcludesUngradedLettersAndComputesShares() {
        val rows = listOf(row("A", "A", 80.0), row("B", "S", 95.0), row("C", "A", 82.0), row("D", "N", null))
        val distribution = M3EGradeComputations.distribution(rows)
        assertEquals(listOf("S", "A"), distribution.map { it.grade })
        assertEquals(1, distribution.first { it.grade == "S" }.count)
        assertEquals(2, distribution.first { it.grade == "A" }.count)
        assertEquals(33.33, distribution.first { it.grade == "S" }.share)
        assertEquals(66.67, distribution.first { it.grade == "A" }.share)
    }

    @Test
    fun coursesSortBestGradeFirstAndTiesByScore() {
        val rows = listOf(
            row("low", "B", 90.0),
            row("best", "S", 88.0),
            row("tieHigh", "A", 95.0),
            row("tieLow", "A", 80.0),
            row("ungraded", "N", null)
        )
        assertEquals(
            listOf("best", "tieHigh", "tieLow", "low", "ungraded"),
            M3EGradeComputations.sortByGrade(rows).map { it.courseCode }
        )
    }

    @Test
    fun typeAveragesIgnoreUnscoredCourses() {
        val rows = listOf(
            row("t1", "A", 80.0, "Theory"),
            row("t2", "B", 90.0, "Theory"),
            row("l1", "A", 60.0, "Lab"),
            row("l2", "N", null, "Lab")
        )
        val averages = M3EGradeComputations.typeAverages(rows)
        assertEquals(listOf("Theory", "Lab"), averages.map { it.type })
        assertEquals(85.0, averages.first().avg)
        assertEquals(2, averages.first().count)
        assertEquals(60.0, averages[1].avg)
        assertEquals(1, averages[1].count)
    }

    @Test
    fun termScoresCarryTheScoreAndScoredCount() {
        val rows = listOf(
            M3EGradeTermRow("t1", "Fall 2025-26", 9.1, 3, 2, 88.5, M3EGradePointTone.SUCCESS),
            M3EGradeTermRow("t2", "Winter 2025-26", 0.0, 0, 0, 0.0, null)
        )
        val scores = M3EGradeComputations.termScores(rows)
        assertEquals(2, scores.size)
        assertEquals("Fall 2025-26", scores.first().label)
        assertEquals(88.5, scores.first().avgScore)
        assertEquals(0, scores[1].scoredCount)
    }

    @Test
    fun numericCellsNeverInventValues() {
        assertEquals("—", M3EGradeComputations.numOrDash(null))
        assertEquals("—", M3EGradeComputations.numOrDash(""))
        assertEquals("92.5", M3EGradeComputations.numOrDash(" 92.50 "))
        assertEquals("90", M3EGradeComputations.numOrDash("90"))
        assertNull(M3EGradeComputations.num("not-a-number"))
        assertEquals(true, M3EGradeComputations.isEmbedded("Embedded Theory"))
        assertEquals(false, M3EGradeComputations.isEmbedded("Theory Only"))
    }
}
