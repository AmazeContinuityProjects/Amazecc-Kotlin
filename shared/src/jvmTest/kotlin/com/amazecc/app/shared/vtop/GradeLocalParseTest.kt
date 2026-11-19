package com.amazecc.app.shared.vtop

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Headless coverage for the LOCAL grade-history bridge. The WebView script emits JSON; these
 * tests feed that JSON straight into [VtopGradeHistoryResult.parse] — no DOM, no session, no
 * Android. Column order and validation mirror
 * `amazecc-api-hono/src/routes/remote/grades.ts` and `fetchAllGradesHistory.ts`.
 */
class GradeLocalParseTest {

    /** Minimal stand-in for the object `VtopScripts.fetchGradeHistorySummary` stringifies. */
    private fun payload(
        courses: String = "[]",
        cgpa: String = "{\"creditsRegistered\":null,\"creditsEarned\":null,\"cgpa\":null,\"grades\":null}",
        baskets: String = "[]",
        ok: Boolean = true,
        error: String? = null
    ): String = buildString {
        append("{\"ok\":").append(ok)
        if (error != null) append(",\"error\":\"").append(error).append("\"")
        append(",\"courses\":").append(courses)
        append(",\"cgpa\":").append(cgpa)
        append(",\"baskets\":").append(baskets)
        append("}")
    }

    private fun course(
        code: String,
        title: String = "Programming",
        type: String = "Theory Only",
        credits: String = "4",
        grade: String = "A",
        examMonth: String = "MAY-2026",
        resultDeclared: String = "10-JUN-2026",
        distributionType: String = "Regular"
    ) = """{"code":"$code","title":"$title","type":"$type","credits":"$credits","grade":"$grade","examMonth":"$examMonth","resultDeclared":"$resultDeclared","distributionType":"$distributionType"}"""

    @Test
    fun mixedRowsKeepOnlyValidCoursesWithEveryColumnMapped() {
        val raw = payload(
            courses = """
            [
              ${course("BCSE101", "Programming and Data Structures", "Theory Only", "4", "A", "MAY-2026", "10-JUN-2026", "Regular")},
              ${course("BCSE102L", "Programming Lab", "Lab Only", "2.0", "S", "MAY-2026", "10-JUN-2026", "Lab")},
              ${course("BCSE103", "Bad credits", "Theory Only", "4.0.1", "A", "", "", "")},
              ${course("BCSE104", "Bad grade", "Theory Only", "3", "X", "", "", "")},
              ${course("", "Code-less row", "Theory Only", "3", "A", "", "", "")}
            ]
            """.trimIndent()
        )

        val parsed = VtopGradeHistoryResult.parse(raw)
        assertTrue(parsed.ok)
        assertEquals(2, parsed.courses.size, "only the two real rows survive")

        val first = parsed.courses[0]
        assertEquals("BCSE101", first.code)
        assertEquals("Programming and Data Structures", first.title)
        assertEquals("Theory Only", first.type)
        assertEquals("4", first.credits)
        assertEquals("A", first.grade)
        assertEquals("MAY-2026", first.examMonth)
        assertEquals("10-JUN-2026", first.resultDeclared)
        assertEquals("Regular", first.distributionType)

        // Decimal credits and lower-case letters are legitimate VTOP spellings.
        assertEquals("2.0", parsed.courses[1].credits)
        assertEquals("S", parsed.courses[1].grade)
    }

    @Test
    fun codeLessRowsAreRejected() {
        val parsed = VtopGradeHistoryResult.parse(
            payload(courses = "[${course("   ", "Whitespace code")}]")
        )
        assertTrue(parsed.ok)
        assertTrue(parsed.courses.isEmpty(), "a trimmed-empty code must not reach the store")
    }

    @Test
    fun publishedCgpaAndGradeCountsAreParsed() {
        val parsed = VtopGradeHistoryResult.parse(
            payload(
                cgpa = """{"creditsRegistered":"160","creditsEarned":"158","cgpa":"8.74","grades":{"S":12,"A":30,"B":8,"C":2,"D":1,"E":0,"F":1,"N":4}}"""
            )
        )
        val cgpa = parsed.cgpa
        assertEquals("160", cgpa.creditsRegistered)
        assertEquals("158", cgpa.creditsEarned)
        assertEquals("8.74", cgpa.cgpa)
        val counts = assertNotNull(cgpa.grades)
        assertEquals(12, counts.S)
        assertEquals(30, counts.A)
        assertEquals(8, counts.B)
        assertEquals(2, counts.C)
        assertEquals(1, counts.D)
        assertEquals(0, counts.E)
        assertEquals(1, counts.F)
        assertEquals(4, counts.N)
    }

    @Test
    fun nonNumericCgpaCellsAreDroppedAndMissingCountsBecomeZero() {
        val parsed = VtopGradeHistoryResult.parse(
            payload(
                cgpa = """{"creditsRegistered":"160.5.1","creditsEarned":"--","cgpa":"N/A","grades":{}}"""
            )
        )
        assertNull(parsed.cgpa.creditsRegistered)
        assertNull(parsed.cgpa.creditsEarned)
        assertNull(parsed.cgpa.cgpa)
        val counts = assertNotNull(parsed.cgpa.grades)
        assertEquals(0, counts.S)
        assertEquals(0, counts.N)
    }

    @Test
    fun basketsFromBothSourceTablesArePreservedInOrder() {
        val parsed = VtopGradeHistoryResult.parse(
            payload(
                baskets = """
                [
                  {"basketTitle":"University Core","creditsRequired":"20","creditsEarned":"20"},
                  {"basketTitle":"Program Core","creditsRequired":"80","creditsEarned":"76"},
                  {"basketTitle":"Open Elective","creditsRequired":"8","creditsEarned":"6"}
                ]
                """.trimIndent()
            )
        )
        assertEquals(3, parsed.baskets.size)
        assertEquals("University Core", parsed.baskets[0].basketTitle)
        assertEquals("20", parsed.baskets[0].creditsRequired)
        assertEquals("20", parsed.baskets[0].creditsEarned)
        // eq(6) rows use cells 0/2/3; the parser only sees the already-remapped JSON.
        assertEquals("Open Elective", parsed.baskets[2].basketTitle)
        assertEquals("8", parsed.baskets[2].creditsRequired)
        assertEquals("6", parsed.baskets[2].creditsEarned)
    }

    @Test
    fun failedFetchIsNotTreatedAsUsableData() {
        val parsed = VtopGradeHistoryResult.parse(payload(ok = false, error = "not authorized"))
        assertFalse(parsed.ok)
        assertEquals("not authorized", parsed.error)
        assertTrue(parsed.courses.isEmpty())
    }

    @Test
    fun unparseablePayloadFailsClosed() {
        val parsed = VtopGradeHistoryResult.parse("<html>not json</html>")
        assertFalse(parsed.ok)
    }

    @Test
    fun generatedScriptTargetsThePublishedTablesAndEnforcesTheGuards() {
        val script = VtopScripts.fetchGradeHistorySummary(
            "/vtop/examinations/examGradeView/StudentGradeHistory",
            "authorizedID=21BCS001&_csrf=abc"
        )
        assertTrue("'#fixedTableContainer table'" in script)
        assertTrue("'tr.tableContent'" in script)
        assertTrue("'table.table.table-hover.table-bordered tbody tr'" in script)
        assertTrue("CREDITS_NUMERIC.test(credits)" in script)
        assertTrue("GRADE_LETTER.test(grade)" in script)
        // eq(5) reads cells 0/1/2; eq(6) reads 0/2/3 — the asymmetry the old code missed.
        assertTrue("cell(c5, 1)" in script && "cell(c5, 2)" in script)
        assertTrue("cell(c6, 2)" in script && "cell(c6, 3)" in script)
        // Credentials are injected as escaped JS string literals, never raw.
        assertTrue("authorizedID=21BCS001" in script)
        assertTrue("xhr.open('POST'" in script)
        assertTrue("session timed out" in script, "auth guard is present")
    }
}
