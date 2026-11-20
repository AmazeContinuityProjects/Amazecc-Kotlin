package com.amazecc.app.shared.vtop

import com.amazecc.app.shared.api.buildAllGradesFromGradeHistory
import com.amazecc.app.shared.api.mergeGradeDetails
import com.amazecc.app.shared.model.CgpaSummary
import com.amazecc.app.shared.model.GradeBreakdown
import com.amazecc.app.shared.model.GradeCurriculumBasket
import com.amazecc.app.shared.model.GradeHistoryCourse
import com.amazecc.app.shared.model.GradeItem
import com.amazecc.app.shared.model.GradeRange
import com.amazecc.app.shared.model.SemesterGradeResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Pure seams for the per-semester grade-detail scrape. No WebView, no session, no live login —
 * the parsers consume the exact JSON the WebView scripts emit.
 */
class GradeDetailTest {

    @Test
    fun semesterIdsFollowAdmissionYearAndReferenceOrder() {
        val ids = VtopGradeDetailScraper.semesterIdsFor("25BLC1081", 2026)

        assertEquals(6, ids.size)
        assertEquals(listOf("CH20252601", "CH20252607", "CH20252605"), ids.take(3))
        // Reference uses the following academic-year suffix: 2026 -> CH202627xx.
        assertEquals(listOf("CH20262701", "CH20262707", "CH20262705"), ids.takeLast(3))
    }

    @Test
    fun unknownAdmissionYearFallsBackRatherThanInventingIds() {
        assertEquals("CH20242501", VtopGradeDetailScraper.semesterIdsFor("", 2026).first())
    }

    @Test
    fun semesterViewParserKeepsOnlyRealGradeRows() {
        val raw = """
            {"ok":true,"gpa":"9.5","grades":[
              {"slNo":"1","courseCode":"BACHY101","courseTitle":"Environmental Sciences","courseType":"Online Course","l":"0","p":"0","j":"0","c":"2","gradingType":"AG","grandTotal":"89","grade":"A","courseId":"CH_BACHY101_00100"},
              {"slNo":"2","courseCode":"BACSE101","courseTitle":"Problem Solving Using Python","courseType":"Lab Only","l":"0","p":"2","j":"0","c":"2","gradingType":"AG","grandTotal":"99","grade":"S","courseId":null},
              {"slNo":"3","courseCode":"","grade":"A","courseId":"GHOST"}
            ]}
        """.trimIndent()

        val parsed = VtopGradeDetailScraper.parseSemesterGradeView(raw)!!

        assertEquals("9.5", parsed.gpa)
        assertEquals(2, parsed.grades.size, "code-less and hidden rows must fail closed")
        assertEquals("BACHY101", parsed.grades[0].courseCode)
        assertEquals("2", parsed.grades[0].c, "credits come verbatim from the C column")
        assertEquals("CH_BACHY101_00100", parsed.grades[0].courseId)
        assertNull(parsed.grades[1].courseId, "no courseId means no detail fetch, never a guess")
    }

    @Test
    fun errorPayloadsAreNotTurnedIntoEmptySemesters() {
        assertNull(VtopGradeDetailScraper.parseSemesterGradeView("""{"ok":false,"error":"not authorized"}"""))
        assertNull(VtopGradeDetailScraper.parseSemesterGradeView("not json"))
    }

    @Test
    fun detailParserPreservesGroupingAndDropsHeaderRows() {
        val raw = """
            {"ok":true,
             "range":{"S":"90","A":"80","B":"70","C":"60","D":"50","E":"40","F":"0","N":""},
             "details":[
               {"slNo":"1","component":"CAT-I","maxMark":"50","weightagePercent":"15","status":"Present","scoredMark":"43.0","weightageMark":"12.9","type":"Theory"},
               {"slNo":"","component":"","maxMark":"","type":"Lab"}
             ]}
        """.trimIndent()

        val parsed = VtopGradeDetailScraper.parseGradeDetail(raw)!!

        assertEquals("90", parsed.range?.S)
        assertEquals("", parsed.range?.N)
        assertEquals(1, parsed.details!!.size)
        assertEquals("CAT-I", parsed.details!![0].component)
        assertEquals("Theory", parsed.details!![0].type)
    }

    @Test
    fun mergeEnrichesSemestersWithoutLosingPublishedFacts() {
        val history = VtopGradeHistoryResult(
            ok = true,
            courses = listOf(
                GradeHistoryCourse(
                    code = "BACHY101",
                    title = "Environmental Sciences",
                    type = "Online Course",
                    credits = "2",
                    grade = "A",
                    examMonth = "Nov-2025"
                )
            ),
            cgpa = CgpaSummary(cgpa = "8.74"),
            baskets = listOf(GradeCurriculumBasket("University Core", "60", "31"))
        )
        val base = buildAllGradesFromGradeHistory(history, "CH20252601")
        val rich = GradeItem(
            courseCode = "BACHY101",
            courseTitle = "Environmental Sciences",
            courseType = "Online Course",
            credits = "2",
            grandTotal = "89",
            grade = "A",
            courseId = "CH_BACHY101_00100",
            details = listOf(GradeBreakdown(component = "CAT-I", maxMark = "50", type = "Theory")),
            range = GradeRange(S = "90", A = "80", B = "70", C = "60", D = "50", E = "40", F = "0")
        )
        val details = mapOf("CH20252601" to SemesterGradeResult(gpa = "9.5", grades = listOf(rich)))

        val merged = mergeGradeDetails(base, details)
        val semester = merged.allSemesters["CH20252601"]!!
        val grade = semester.grades.single()

        assertEquals("9.5", semester.gpa, "per-term GPA comes from the semester view")
        assertEquals("89", grade.grandTotal)
        assertEquals("CH_BACHY101_00100", grade.courseId)
        assertEquals("Theory", grade.details!!.single().type)
        assertEquals("Nov-2025", grade.examMonth, "history-only fields survive the join")
        assertEquals("8.74", merged.publishedCgpa, "published CGPA is untouched")
        assertEquals("University Core", merged.overall!!.curriculum.single().basketTitle)
    }

    @Test
    fun emptySweepReturnsTheBaseUnchanged() {
        val base = buildAllGradesFromGradeHistory(VtopGradeHistoryResult(ok = true), "CH20252601")
        assertSame(base, mergeGradeDetails(base, emptyMap()))
    }

    @Test
    fun webViewScriptsCarryTheRealColumnContract() {
        val semester = VtopScripts.fetchSemesterGradeView("/p", "body")
        assertTrue(semester.contains("getGradeViewDetails"))
        assertTrue(semester.contains("GPA"))
        assertTrue(semester.contains("display: none"), "hidden embedded halves must be rejected")

        val detail = VtopScripts.fetchGradeViewDetails("/p", "body")
        assertTrue(detail.contains("Range of Grades"))
        assertTrue(detail.contains("Mark Title"))
        assertTrue(detail.contains("'Theory'"))
        assertTrue(detail.contains("'Lab'"))
        assertTrue(detail.contains("td, output"), "assessment cells are td + output at even indices")
    }
}
