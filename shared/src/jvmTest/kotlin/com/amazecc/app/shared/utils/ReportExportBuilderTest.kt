package com.amazecc.app.shared.utils

import com.amazecc.app.shared.domain.overhaul.M3EReportAssessment
import com.amazecc.app.shared.domain.overhaul.M3EReportAttendance
import com.amazecc.app.shared.domain.overhaul.M3EReportAttendanceCourse
import com.amazecc.app.shared.domain.overhaul.M3EReportCurriculumRow
import com.amazecc.app.shared.domain.overhaul.M3EReportData
import com.amazecc.app.shared.domain.overhaul.M3EReportFinance
import com.amazecc.app.shared.domain.overhaul.M3EReportGradeCourse
import com.amazecc.app.shared.domain.overhaul.M3EReportIdentity
import com.amazecc.app.shared.domain.overhaul.M3EReportOdUsage
import com.amazecc.app.shared.domain.overhaul.M3EReportSections
import com.amazecc.app.shared.domain.overhaul.M3EReportSemester
import com.amazecc.app.shared.domain.overhaul.M3EReportServices
import com.amazecc.app.shared.domain.overhaul.M3EReportSubject
import com.amazecc.app.shared.domain.overhaul.M3EReportSummary
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * D-157b — the export builder is a pure function of one assembled snapshot. A fixed epoch keeps
 * every filename/date assertion deterministic; the fixture deliberately carries non-ASCII text
 * and markup so UTF-8 preservation and escaping are checked through the bytes, not a mock.
 */
class ReportExportBuilderTest {

    private val attendanceCourse = M3EReportAttendanceCourse(
        code = "CSE101", title = "Data Structures — •", type = "Theory",
        faculty = "Dr <&> Rao", slotVenue = "A1/R101", attended = 45, total = 50,
        percentage = 90.0
    )

    private val assessment = M3EReportAssessment(
        title = "Quiz <1>&\"'", status = "Published", scored = 18.0, maximum = 20.0,
        weightagePercent = 25.0, weightedScore = 22.5
    )

    private val fixture = M3EReportData(
        generatedAt = EPOCH,
        identity = M3EReportIdentity(
            name = "José — •", registrationNumber = "21BCE1042", programme = "B.Tech — CSE",
            school = "<b>&\"'</b>", campus = "Main • Campus",
            proctorName = "Dr Proctor", proctorEmail = "proctor@example.com",
            semesterName = "Fall 2025"
        ),
        sections = M3EReportSections(),
        summary = M3EReportSummary(
            cgpa = 8.75, currentGpa = 9.0, attendancePct = 90.0,
            classesAttended = 45, classesTotal = 50, daysWithoutAbsence = 18, daysRecorded = 20,
            classesMissed = 5, creditsEarned = 120, creditsRegistered = 140, arrearsCount = 0
        ),
        semesters = listOf(
            M3EReportSemester(
                semesterId = "2025-F", name = "Fall 2025", gpa = 9.0,
                courses = listOf(
                    M3EReportGradeCourse(
                        code = "CSE101", title = "Data Structures — •", type = "Theory", credits = 4,
                        grandTotal = 88.0, grade = "A", components = listOf(assessment)
                    )
                )
            )
        ),
        curriculum = listOf(
            M3EReportCurriculumRow(
                category = "Core", basket = "PC", code = "CSE101",
                title = "Data Structures — •", credits = 4, type = "Theory",
                required = 4, earned = 4, progressPct = 100.0
            )
        ),
        calendar = emptyList(),
        attendance = M3EReportAttendance(courses = listOf(attendanceCourse), months = emptyList()),
        odUsage = M3EReportOdUsage(8, null, null, emptyList(), emptyList()),
        exams = emptyList(),
        subjects = listOf(
            M3EReportSubject(
                code = "CSE101", title = "Data Structures — •", type = "Theory", credits = 4,
                classId = "CLS-1", faculty = "Dr <&> Rao", department = "CSE", slot = "A1",
                venue = "R101", attendance = attendanceCourse, publishedGrade = "A",
                assessmentAverage = 90.0, assessments = listOf(assessment),
                od = null, prediction = null
            )
        ),
        events = emptyList(),
        finance = M3EReportFinance(false, "", emptyList(), emptyList()),
        services = M3EReportServices(emptyList(), emptyList(), emptyList(), emptyList())
    )

    @Test
    fun pdf_bytes_start_with_pdf_magic_and_use_the_real_dated_filename() {
        val file = ReportExportBuilder.build(fixture, ReportExportFormat.PDF)
        assertTrue(file.bytes.decodeToString().startsWith("%PDF"))
        assertEquals("AmazeCC-Student-Report-2001-09-09.pdf", file.filename)
    }

    @Test
    fun json_has_schema_quoted_iso_timestamp_and_verbatim_utf8_name() {
        val file = ReportExportBuilder.build(fixture, ReportExportFormat.JSON)
        val json = file.bytes.decodeToString()
        assertTrue(json.contains("\"schema\": \"amazecc.student-report.v1\""), json.take(160))
        assertTrue(json.contains("\"generatedAt\": \"2001-09-09T01:46:40Z\""), json.take(160))
        assertTrue(json.contains("José — •"), "non-ASCII name must appear verbatim")
        assertFalse(json.contains("?"), "JSON must never fold Unicode to '?'")
        assertEquals("AmazeCC-Student-Report-2001-09-09.json", file.filename)
    }

    @Test
    fun html_is_standalone_utf8_with_tables_disclaimer_and_escaped_markup() {
        val file = ReportExportBuilder.build(fixture, ReportExportFormat.HTML)
        val html = file.bytes.decodeToString()
        assertTrue(html.startsWith("<!DOCTYPE"))
        assertTrue(html.contains("<meta charset=\"UTF-8\">"))
        assertTrue(html.contains("<table>"), "real table markup, not a <pre> dump")
        assertTrue(
            html.contains(
                "not an official institutional transcript or certificate. " +
                    "Missing source details are not inferred."
            )
        )
        assertTrue(html.contains("José — •"))
        assertTrue(html.contains("&lt;b&gt;"), "fixture markup must be HTML-escaped")
        assertFalse(html.contains("<b>"))
        assertEquals("AmazeCC-Student-Report-2001-09-09.html", file.filename)
    }

    @Test
    fun share_text_keeps_utf8_and_carries_section_headers() {
        val text = ReportExportBuilder.shareText(fixture)
        assertTrue(text.contains("José — •"))
        assertTrue(text.contains("AMAZECC STUDENT ACADEMIC REPORT"))
        assertTrue(text.contains("ACADEMIC SUMMARY"))
        assertTrue(text.contains("SUBJECT DOSSIERS"))
        assertTrue(text.contains("GRADE HISTORY"))
        assertFalse(text.contains("?"), "share text must not '?'-fold Unicode")
    }

    @Test
    fun toggling_a_section_flag_changes_the_builder_output() {
        val allSections = ReportExportBuilder.build(fixture, ReportExportFormat.JSON)
            .bytes.decodeToString()
        val withoutHistory = ReportExportBuilder.build(
            fixture.copy(sections = fixture.sections.copy(history = false)),
            ReportExportFormat.JSON,
        ).bytes.decodeToString()
        // "history" is an unambiguous JSON key from the history flag (no other block emits it).
        assertTrue(allSections.contains("\"history\": ["))
        assertFalse(withoutHistory.contains("\"history\": ["))
    }

    private companion object {
        // 2001-09-09T01:46:40Z — fixed so the UTC date in every filename is device-TZ independent.
        const val EPOCH = 1_000_000_000_000L
    }
}
