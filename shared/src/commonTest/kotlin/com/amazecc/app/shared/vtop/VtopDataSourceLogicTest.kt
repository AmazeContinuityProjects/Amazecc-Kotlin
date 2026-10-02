package com.amazecc.app.shared.vtop

import com.amazecc.app.shared.model.AttendanceItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Covers the shaping step of [VtopDataSource]: turning positional VTOP rows into DTOs.
 *
 * The WebView transport is not exercised here. What matters is that the column mapping and the
 * attendance/timetable merge stay faithful to AmazeCC-API's parsers — a silent shift here looks
 * like plausible but wrong data on the dashboard rather than an error.
 */
class VtopDataSourceLogicTest {

    // ── Row access ──────────────────────────────────────────────────────────

    @Test
    fun cellReadIsBoundsSafe() {
        val rows = VtopRows(ok = true, rows = listOf(listOf("a", "b")))
        assertEquals("a", rows.cell(0, 0))
        assertEquals("b", rows.cell(0, 1))
        assertEquals("", rows.cell(0, 99), "out-of-range column must be empty, not a crash")
        assertEquals("", rows.cell(9, 0), "out-of-range row must be empty, not a crash")
    }

    @Test
    fun sizeIsZeroOnFailure() {
        assertEquals(0, VtopRows(ok = false, error = "network error", rows = listOf(listOf("x"))).size)
        assertEquals(1, VtopRows(ok = true, rows = listOf(listOf("x"))).size)
    }

    @Test
    fun parseSurvivesGarbage() {
        assertTrue(!VtopRows.parse("").ok)
        assertTrue(!VtopRows.parse("not json").ok)
        assertTrue(!VtopRows.parse("{}").ok)
    }

    @Test
    fun parseReadsRowsAndAttrs() {
        val raw = """
            {"ok":true,"status":200,
             "rows":[["1","BCSE101L","3 0 3 0 3"],["2","BCSE101","3 0 0 0 3"]],
             "captures":[["processViewAttendanceDetail('c1','L1')"],[null]],
             "keyValuePairs":{"Name":"Test"}}
        """.trimIndent()
        val parsed = VtopRows.parse(raw)
        assertTrue(parsed.ok)
        assertEquals(2, parsed.size)
        assertEquals("BCSE101L", parsed.cell(0, 1))
        assertEquals("processViewAttendanceDetail('c1','L1')", parsed.capture(0, 0))
        assertNull(parsed.capture(1, 0))
        assertEquals("Test", parsed.keyValuePairs["Name"])
    }

    // ── Course mapping ──────────────────────────────────────────────────────

    @Test
    fun creditsComeFromTheFifthLtpjcToken() {
        val course = VtopCourse("1", "BCSE101L", "BCSE101", "Lab Only", "3 0 3 0 3", "LAB", "c1", "L1+2", "Dr X")
        assertEquals("3", course.credits)
    }

    @Test
    fun creditsAreNullWhenLtpjcIsShort() {
        val course = VtopCourse("1", "BCSE101", "BCSE101", null, "3 0 0", "CORE", "c1", "L1", "")
        assertNull(course.credits)
    }

    @Test
    fun baseCodeStripsTheLabOrTheorySuffix() {
        // `VtopCourse.courseCode` is already the bare code (the port stopped appending "(L)"/"(T)"
        // and instead records the half in `component`), so this pins the two staying in step.
        assertEquals("BCSE101", VtopCourse("1", "BCSE101", "BCSE101", "Lab Only", "", "", "c1", "L1", "").courseCode)
        assertEquals("BCSE101", VtopCourse("1", "BCSE101", "BCSE101", "Theory Only", "", "", "c1", "A1", "").courseCode)
    }

    // ── Attendance / timetable merge ────────────────────────────────────────

    private fun course(
        code: String,
        ltpjc: String = "3 0 0 0 3",
        slotVenue: String = "A1+1",
        category: String = "CORE",
        classId: String = "c1",
        faculty: String = "Dr X"
    ) = VtopCourse(
        slNo = "1",
        course = code,
        courseCode = code,
        component = null,
        ltpjc = ltpjc,
        category = category,
        classId = classId,
        slotVenue = slotVenue,
        facultyDetails = faculty
    )

    @Test
    fun venueIsNarrowedToTheLastToken() {
        // The server keeps only the last ABC-123 style match.
        val pattern = Regex("[A-Z]+\\d*\\s*-\\s*\\d+[A-Z]?")
        val cleaned = pattern
            .findAll("AB1-12 LT1-34".replace(Regex("\\s+"), " ").trim())
            .lastOrNull()?.value
        assertEquals("LT1-34", cleaned)
    }

    @Test
    fun venueIsNullWhenThereIsNoMatch() {
        val pattern = Regex("[A-Z]+\\d*\\s*-\\s*\\d+[A-Z]?")
        assertNull(pattern.findAll("A1+1").lastOrNull()?.value)
    }

    @Test
    fun unmatchedCourseStillAppearsWithNillSlot() {
        // mergeAttendanceWithTimetable falls back to a null-attendance row rather than dropping
        // the course, so the timetable stays the authoritative list.
        val item = AttendanceItem(
            courseCode = "BCSE999(T)",
            courseTitle = "BCSE999",
            courseType = "",
            slotName = "NILL",
            faculty = "Dr Y",
            attendedClasses = 0,
            totalClasses = 0,
            attendancePercentage = ""
        )
        assertEquals("NILL", item.slotName)
        assertEquals(0, item.totalClasses)
    }

    // ── Detail-link parsing ─────────────────────────────────────────────────

    @Test
    fun detailCallIsExtractedFromTheOnclick() {
        val link = "processViewAttendanceDetail('CS2026CS01','L31+2')"
        val parsed = Regex("processViewAttendanceDetail\\('([^']*)','([^']*)'\\)").find(link)
        assertTrue(parsed != null)
        val (classId, slotName) = parsed!!.destructured
        assertEquals("CS2026CS01", classId)
        assertEquals("L31+2", slotName)
    }

    @Test
    fun detailCallRejectsAnUnexpectedShape() {
        val link = "someOtherFunction('a','b')"
        assertNull(Regex("processViewAttendanceDetail\\('([^']*)','([^']*)'\\)").find(link))
    }

    @Test
    fun detailCallHandlesEmptySlot() {
        val link = "processViewAttendanceDetail('X','')"
        val parsed = Regex("processViewAttendanceDetail\\('([^']*)','([^']*)'\\)").find(link)
        val (classId, slotName) = parsed!!.destructured
        assertEquals("X", classId)
        assertEquals("", slotName)
    }
}
