package com.amazecc.app.shared.vtop

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Covers the response parsing for marks, grades, hostel and payments.
 *
 * These are the extractors with the most page-specific assumptions — nested tables, positional
 * table indices, list-group summaries, and a "message present means no dues" inversion. All of
 * them fail silently into wrong data rather than errors, so the parse layer is worth pinning.
 */
class VtopPortedModuleParseTest {

    // ── Marks: nested table ─────────────────────────────────────────────────

    private val marksJson = """
        {"ok":true,"status":200,"courses":[
          {"cells":["1","CS2026CS01","BCSE101L","Programming","Theory Only","LAB","Dr X","L31+2","Core"],
           "assessments":[["1","CIA-1","30","40","Present","28","11.2"],
                          ["2","CIA-2","30","40","Present","26","10.4"]]},
          {"cells":["2","CS2026CS02","BCSE102","Networks","Core Theory","CORE","Dr Y","A1+1","Elective"],
           "assessments":[["1","Final","100","100","Absent","-","-"]]}
        ]}
    """.trimIndent()

    @Test
    fun marksParseKeepsBothCourses() {
        val parsed = VtopMarksResult.parse(marksJson)
        assertTrue(parsed.ok)
        assertEquals(2, parsed.courses.size)
        assertEquals("BCSE101L", parsed.courses[0].cells[2])
        assertEquals(2, parsed.courses[0].assessments.size)
        assertEquals(1, parsed.courses[1].assessments.size)
    }

    @Test
    fun assessmentCellsArePositionalAndSevenWide() {
        val course = VtopMarksResult.parse(marksJson).courses[0]
        val a = course.assessments[0]
        assertEquals(7, a.size)
        assertEquals("CIA-1", a[1], "index 0 is the server's slNo, which the DTO drops")
        assertEquals("30", a[2], "maxMark")
        assertEquals("40", a[3], "weightagePercent")
        assertEquals("Present", a[4], "status")
        assertEquals("28", a[5], "scoredMark")
        assertEquals("11.2", a[6], "weightageMark")
    }

    @Test
    fun marksParseSurvivesGarbage() {
        assertFalse(VtopMarksResult.parse("").ok)
        assertFalse(VtopMarksResult.parse("<html>").ok)
        assertFalse(VtopMarksResult.parse("""{"ok":false,"error":"not authorized"}""").ok)
    }

    @Test
    fun marksErrorPropagates() {
        val parsed = VtopMarksResult.parse("""{"ok":false,"error":"not authorized"}""")
        assertFalse(parsed.ok)
        assertEquals("not authorized", parsed.error)
    }

    // ── CGPA: list group, not a table ──────────────────────────────────────

    @Test
    fun cgpaSummaryIsMatchedByLabelSubstring() {
        val json = """
            {"ok":true,"cgpa":{
              "creditsRequired":"180","creditsEarned":"96","cgpa":"8.72",
              "nonGradedRequirement":"4"}}
        """.trimIndent()
        val parsed = VtopCgpaSummary.parse(json)
        assertNotNull(parsed)
        assertEquals("180", parsed.creditsRequired)
        assertEquals("96", parsed.creditsEarned)
        assertEquals("8.72", parsed.cgpa)
        assertEquals("4", parsed.nonGradedRequirement)
    }

    @Test
    fun cgpaSummaryToleratesMissingFields() {
        val parsed = VtopCgpaSummary.parse("""{"ok":true,"cgpa":{}}""")
        assertNotNull(parsed)
        assertNull(parsed.cgpa)
        assertNull(parsed.creditsEarned)
    }

    @Test
    fun cgpaSummaryReturnsNullOnFailure() {
        assertNull(VtopCgpaSummary.parse("""{"ok":false}"""))
        assertNull(VtopCgpaSummary.parse("garbage"))
    }

    // ── Payments: message-present means NO dues ────────────────────────────

    @Test
    fun greenMessageMeansNoDues() {
        val json = """{"ok":true,"title":"Payments","message":"No Dues","hasDues":false}"""
        val parsed = VtopPaymentStatus.parse(json)
        assertTrue(parsed.ok)
        assertTrue(!parsed.hasDues)
        assertEquals("No Dues", parsed.message)
    }

    @Test
    fun tableWithoutMessageMeansDues() {
        val json = """{"ok":true,"title":"Payments","message":"","hasDues":true}"""
        assertTrue(VtopPaymentStatus.parse(json).hasDues)
    }

    @Test
    fun paymentStatusDefaultsAreSafe() {
        val parsed = VtopPaymentStatus.parse("""{"ok":true}""")
        assertTrue(parsed.ok)
        assertEquals("", parsed.title)
        assertEquals("", parsed.message)
        assertFalse(parsed.hasDues)
    }

    // ── Receipts / wallet: header row skipped, captures read ───────────────

    private val receiptsJson = """
        {"ok":true,"rows":[
          ["Receipt No","Date","Amount","Campus"],
          ["R001","02-Jan-2026","12500","CHN"],
          ["R002","11-Feb-2026","5000","CHN"]],
         "captures":[["doDuplicateReceipt('K1')","A1","B1"],["doDuplicateReceipt('K2')","A2","B2"]],
         "keyValuePairs":{}}
    """.trimIndent()

    @Test
    fun receiptRowZeroIsTheHeader() {
        val parsed = VtopRows.parse(receiptsJson)
        assertEquals(3, parsed.size)
        assertEquals("Receipt No", parsed.cell(0, 0))
        // Kotlin side skips index 0 via headerRowsToSkip
        assertEquals("R001", parsed.cell(1, 0))
    }

    @Test
    fun receiptCapturesLineUpPerRow() {
        val parsed = VtopRows.parse(receiptsJson)
        assertEquals("doDuplicateReceipt('K1')", parsed.capture(1, 0))
        assertEquals("A1", parsed.capture(1, 1))
        assertEquals("B1", parsed.capture(1, 2))
        assertEquals("doDuplicateReceipt('K2')", parsed.capture(2, 0))
    }

    @Test
    fun walletBalanceComesFromTheSixthColumn() {
        // parseWallet reads cells[5] as bookBalanceAmount
        val json = """{"ok":true,"rows":[["h1","h2","h3","h4","h5","h6","h7"],
          ["01-Jan-2026","R001","Top Up","5000","0","3250.50","-"]],"captures":[],"keyValuePairs":{}}"""
        val parsed = VtopRows.parse(json)
        assertEquals("3250.50", parsed.cell(1, 5))
    }

    // ── Hostel label matching and mess normalisation ────────────────────────

    @Test
    fun messCodeIsExpanded() {
        fun normalise(value: String): String {
            var mess = value.split(" ").firstOrNull().orEmpty().ifBlank { "NOT ALLOTED" }
            if (mess.length > 7) {
                mess = when (mess) {
                    "NON" -> "NON VEG"
                    "FOOD" -> "FOOD PARK"
                    else -> "NOT ALLOTED"
                }
            }
            return mess
        }
        assertEquals("NON VEG", normalise("NON"))
        assertEquals("FOOD PARK", normalise("FOOD"))
        assertEquals("NOT ALLOTED", normalise("SOMETHING LONG"))
        assertEquals("MESS1", normalise("MESS1"))
    }

    @Test
    fun keyValuePairsFeedTheLabelMatching() {
        val json = """
            {"ok":true,"rows":[],"captures":[],
             "keyValuePairs":{"GENDER":"MALE","HOSTELLER":"HOSTELLER",
                              "Block Name":"AB1 BLOCK","Room No":"214",
                              "Mess Information":"NON VEG MESS"}}
        """.trimIndent()
        val pairs = VtopRows.parse(json).keyValuePairs
        assertTrue(pairs.keys.any { it.contains("GENDER") })
        assertEquals("HOSTELLER", pairs.entries.first { it.key.contains("HOSTELLER") }.value)
        assertEquals("AB1", pairs.entries.first { it.key.contains("Block Name") }.value.split(" ").first())
    }

    // ── Grades: positional table index ──────────────────────────────────────

    @Test
    fun gradesReadFromNinthColumn() {
        // grades route reads tds[2], [3], [4], [5], [8]
        val row = listOf("0", "1", "Basket", "Core", "3", "A+", "6", "7", "Elective", "9")
        assertEquals("Basket", row[2])
        assertEquals("Core", row[3])
        assertEquals("3", row[4])
        assertEquals("A+", row[5])
        assertEquals("Elective", row[8])
    }

    @Test
    fun shortGradeRowIsSkipped() {
        val parsed = VtopRows.parse("""{"ok":true,"rows":[["a","b"]],"captures":[],"keyValuePairs":{}}""")
        assertEquals(2, parsed.cell(0, 0).length)
        assertEquals("", parsed.cell(0, 8), "a 2-wide row has no 9th column")
    }

    // ── Script shape guards ────────────────────────────────────────────────

    @Test
    fun fetchRowsEncodesTheTableIndex() {
        val script = VtopScripts.fetchRows(
            path = "/vtop/x",
            body = "a=1",
            selector = "tr",
            tableSelector = "#fixedTableContainer table",
            tableIndex = 5,
            captures = listOf(VtopScripts.Capture("button", "onclick"))
        )
        assertTrue(script.contains("#fixedTableContainer table"))
        assertTrue(script.contains("button"))
        assertTrue(script.contains("/vtop/x"))
    }

    @Test
    fun marksScriptReadsOutputElements() {
        val script = VtopScripts.fetchMarks("/vtop/examinations/doStudentMarkView", "a=1")
        assertTrue(script.contains("customTable-level1"), "nested assessment table")
        assertTrue(script.contains("output"), "assessment values live in <output> elements")
    }

    @Test
    fun paymentStatusScriptLooksForTheGreenFont() {
        val script = VtopScripts.fetchPaymentStatus("/vtop/p2p/Payments", "a=1")
        assertTrue(script.contains("font[color='green']"))
    }
}
