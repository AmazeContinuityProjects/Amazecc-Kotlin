package com.amazecc.app.shared.ffcs

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * D-182 — contract tests for the FFCS export ladder. Every assertion uses real timetable/course
 * data: no test merely checks that "something" was produced, and the empty cases assert that no
 * fake rows were invented.
 */
class FfcsExportsTest {

    private fun course(
        code: String,
        title: String = "Course $code",
        slots: List<String> = listOf("A1", "TA1"),
        venue: String = "AB3-101",
        faculty: String = "Dr. Ada",
    ) = AddedCourse(
        id = "c-$code",
        code = code,
        title = title,
        slots = slots,
        faculty = faculty,
        venue = venue,
        credits = "4",
        type = "TH",
        color = "#2563EB",
    )

    private fun timetable(name: String, courses: List<AddedCourse>) = TimetableState(
        id = "tt-1",
        name = name,
        courses = courses,
        metrics = TimetableMetrics(halfDays = 2, gaps = 1),
    )

    // ---- PDF ---------------------------------------------------------------------------------

    @Test
    fun pdf_has_valid_structure_and_real_course_rows() {
        val pdf = buildFfcsPdf(
            listOf(timetable("Winter plan", listOf(course("CSE1001")))),
            "Winter FFCS Dossier",
        )
        val s = assertValidPdf(pdf)

        assertTrue(s.contains("(Timetable 1: Winter plan) Tj"))
        assertTrue(s.contains("(SUMMARY: 1 courses, 2 half-days, 1 gaps) Tj"))
        assertTrue(
            s.contains("(CSE1001 | Course CSE1001 | TH | 4 | A1+TA1 | AB3-101 | Dr. Ada) Tj"),
            "real course row with slot/venue/faculty must be drawn",
        )
    }

    @Test
    fun pdf_is_deterministic_and_empty_input_has_honest_copy() {
        val data = listOf(timetable("Plan", listOf(course("MAT2005", faculty = "Dr. Noether"))))
        val a = buildFfcsPdf(data, "FFCS")
        val b = buildFfcsPdf(data, "FFCS")
        assertTrue(a.contentEquals(b), "same input must yield the same PDF bytes")

        val empty = buildFfcsPdf(emptyList(), "FFCS").decodeToString()
        assertTrue(empty.contains("(No timetables to export.) Tj"))
        assertFalse(empty.contains("SUMMARY:"), "no fake summary row for empty input")
    }

    /** Minimal structural check: header, xref/trailer, font/page objects and startxref target. */
    private fun assertValidPdf(pdf: ByteArray): String {
        val s = pdf.decodeToString()
        assertTrue(s.startsWith("%PDF-1.4\n"), "missing PDF header magic")
        assertTrue(s.endsWith("%%EOF\n"), "missing EOF marker")
        assertTrue(s.contains("/Type /Catalog") && s.contains("/Type /Pages"))
        assertTrue(s.contains("/BaseFont /Helvetica") && s.contains("stream\nBT\n/F1 11 Tf"))

        val start = s.lastIndexOf("startxref\n")
        assertTrue(start >= 0, "missing startxref")
        val valueStart = start + "startxref\n".length
        val valueEnd = s.indexOf('\n', valueStart)
        val offset = s.substring(valueStart, valueEnd).trim().toInt()
        assertTrue(s.startsWith("xref\n", offset), "startxref must point at the xref table")
        assertTrue(s.contains("trailer\n<< /Size "), "missing trailer /Size")
        return s
    }

    // ---- JSON --------------------------------------------------------------------------------

    @Test
    fun json_has_schema_marker_real_data_and_correct_escaping() {
        val tricky = "Caf\u00e9 \"quoted\" <b>&</b> \\ path\nnext"
        val data = listOf(
            timetable(
                "Plan <1>",
                listOf(course("CSE3001", title = tricky, faculty = "Dr. O'Brien")),
            )
        )
        val json = buildFfcsJson(data)

        assertTrue(json.contains("\"schema\": \"${FfcsExportJson.SCHEMA}\""))
        assertTrue(json.contains("\"count\": 1"))
        assertTrue(json.contains("\"code\": \"CSE3001\""))
        assertTrue(json.contains("\"slots\": [\"A1\", \"TA1\"]"))
        assertTrue(json.contains("\"faculty\": \"Dr. O'Brien\""))
        assertTrue(json.contains("Caf\u00e9"), "UTF-8 must be preserved, not folded to '?'")
        assertTrue(json.contains("Caf\u00e9 \\\"quoted\\\" <b>&</b> \\\\ path\\nnext"))
        assertTrue(json.contains("\"name\": \"Plan <1>\""), "HTML markup stays verbatim in JSON")
        assertEquals(json, buildFfcsJson(data), "JSON must be deterministic")
        assertEquals(json, json.encodeToByteArray().decodeToString(), "UTF-8 round-trip")
    }

    @Test
    fun json_empty_input_has_no_fabricated_timetables() {
        val json = buildFfcsJson(emptyList())
        assertTrue(json.contains("\"count\": 0"))
        assertTrue(json.contains("\"timetables\": []"))
        assertFalse(json.contains("\"code\":"))
    }

    // ---- Clash report ------------------------------------------------------------------------

    @Test
    fun clash_report_uses_171_mm_columns_and_real_pairs() {
        assertEquals(171, FfcsExportClashReport.CLASH_REPORT_COLUMNS.sum())
        assertEquals(8, FfcsExportClashReport.CLASH_REPORT_HEADERS.size)

        val cse = course("CSE3001", title = "Data Structures", venue = "SJT-101")
        val mat = course("MAT2005", title = "Linear Algebra", venue = "AB2-04")
        val clashes = listOf(
            FfcsClash(
                course = cse,
                clashesWith = listOf(
                    FfcsClashEntry(
                        code = "MAT2005",
                        meetings = listOf(FfcsClashMeeting("mon", "10:00")),
                    ),
                    FfcsClashEntry(
                        code = "PHY1002",
                        meetings = listOf(
                            FfcsClashMeeting("wed", "10:45"),
                            FfcsClashMeeting("tue", "08:00"),
                        ),
                    ),
                ),
            ),
            FfcsClash(
                course = mat,
                clashesWith = listOf(
                    FfcsClashEntry("CSE3001", listOf(FfcsClashMeeting("Mon", "10:00"))),
                ),
            ),
        )
        val report = buildFfcsClashReport(clashes)

        assertTrue(report.contains("FFCS CLASH REPORT"))
        assertTrue(report.contains("3 clashes detected"))
        assertTrue(report.contains("CODE") && report.contains("CLASHES WITH"))
        assertTrue(report.contains("CSE3001") && report.contains("MAT2005 (Mon 10:00)"))
        assertTrue(report.contains("PHY1002 (Wed 10:45, Tue 08:00)"))
        assertTrue(report.contains("SJT-101") && report.contains("Linear Algebra"))
        assertEquals(report, buildFfcsClashReport(clashes), "report must be a pure function of its input")
    }

    @Test
    fun clash_report_empty_input_has_verdict_and_no_fake_rows() {
        val report = buildFfcsClashReport(emptyList())
        // String.lines() keeps the trailing empty element after the final '\n', so assert on the
        // content itself rather than a brittle line count: the ruler must be the last non-blank
        // line (nothing follows it) and no clash-cell row marker may appear anywhere.
        val content = report.lines().filter { it.isNotBlank() }
        assertTrue(report.contains("No clashes - all courses fit"))
        assertFalse(report.contains("clashes detected"), "no fabricated clash count")
        assertFalse(report.contains("CSE3001"), "no fabricated course code")
        assertTrue(
            content.last().all { it == '-' || it == ' ' },
            "the column ruler must be the last content line — any clash row after it is fabricated",
        )
        assertTrue(
            content.none { it.contains(" (") },
            "no 'CODE (Mon 10:00)' clash-cell rows may be emitted for empty input",
        )
    }

    // ---- HTML dossier ------------------------------------------------------------------------

    @Test
    fun dossier_html_is_standalone_escaped_and_built_from_real_cells() {
        val html = buildFfcsDossierHtml(
            timetable(
                "Plan <b>",
                listOf(course("CSE1001", title = "<script>alert('x')</script> & more")),
            )
        )

        assertTrue(html.startsWith("<!DOCTYPE html>"))
        assertTrue(html.contains("<meta charset=\"UTF-8\">"))
        assertTrue(html.contains("<table>") && html.contains("<th>CODE</th>"))
        assertTrue(html.contains("<td>CSE1001</td>"))
        assertTrue(html.contains("&lt;script&gt;alert(&#39;x&#39;)&lt;/script&gt; &amp; more"))
        assertFalse(html.contains("<script>"), "caller markup must never become executable HTML")
        assertFalse(html.contains("@page"), "standalone dossier has no print-only CSS")
    }

    // ---- Filename ----------------------------------------------------------------------------

    @Test
    fun filename_uses_real_utc_date_and_ascii_safe_extension() {
        val epoch = java.time.Instant.parse("2026-01-02T12:34:56Z").toEpochMilli()
        assertEquals("AmazeCC-FFCS-2026-01-02.pdf", ffcsExportFileName("pdf", epoch))
        assertEquals("AmazeCC-FFCS-2026-01-02.pdf", ffcsExportFileName("P#DF/../", epoch))
        assertEquals("AmazeCC-FFCS-2026-01-02.txt", ffcsExportFileName("!!!", epoch))
    }
}
