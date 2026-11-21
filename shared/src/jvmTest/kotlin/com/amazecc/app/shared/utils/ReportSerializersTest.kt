package com.amazecc.app.shared.utils

import com.amazecc.app.shared.domain.overhaul.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * D-152 critic fixes: web v1 ISO timestamp, UNKNOWN OD honesty, UTF-8 preservation, markup
 * escaping and per-section gating in JSON / HTML / PDF. Fully deterministic (fixed epoch).
 */
class ReportSerializersTest {

    private val attendanceCourse = M3EReportAttendanceCourse(
        code = "CSE101", title = "Data Structures — •", type = "Theory",
        faculty = "Dr <&> Silva", slotVenue = "A1/R101", attended = 45, total = 50,
        percentage = 90.0
    )

    private val assessment = M3EReportAssessment(
        title = "Quiz <1>&\"'", status = "Published", scored = 18.0, maximum = 20.0,
        weightagePercent = 25.0, weightedScore = 22.5
    )

    // UNKNOWN OD hours: both null. Claimed hours are real.
    private val odSubject = M3EReportOdSubject(
        code = "CSE101", claimedHours = 8, validHours = null, wastedHours = null,
        officialMatchPct = 0.0
    )

    private val subject = M3EReportSubject(
        code = "CSE101", title = "Data Structures — •", type = "Theory", credits = 4,
        classId = "CLS-1", faculty = "Dr <&> Silva", department = "CSE", slot = "A1", venue = "R101",
        attendance = attendanceCourse, publishedGrade = "A", assessmentAverage = 90.0,
        assessments = listOf(assessment), od = odSubject,
        prediction = M3EReportPrediction(
            currentPace = "On track", bestGrade = "S", currentStandingPct = 90.0,
            maxPossiblePct = 98.0, classAveragePct = 82.0, classSize = 60
        )
    )

    private val fixture = M3EReportData(
        generatedAt = EPOCH,
        identity = M3EReportIdentity(
            name = "José — •", registrationNumber = "21BCE1042", programme = "B.Tech — CSE",
            school = "<b>&\"'</b>", campus = "Main • Campus", semesterName = "Fall 2025"
        ),
        sections = M3EReportSections(),
        summary = M3EReportSummary(
            cgpa = 8.75, currentGpa = 9.0, attendancePct = 91.5,
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
                category = "Core", basket = "PC", code = "CSE101", title = "Data Structures — •",
                credits = 4, type = "Theory", required = 4, earned = 4, progressPct = 100.0
            )
        ),
        calendar = listOf(
            M3EReportCalendarMonth(
                key = "2025-09", label = "September 2025 — •",
                days = listOf(
                    M3EReportCalendarDay(date = 9, weekday = "Tue", events = listOf("Orientation <intro>"))
                )
            )
        ),
        attendance = M3EReportAttendance(
            courses = listOf(attendanceCourse),
            months = listOf(
                M3EReportAttendanceMonth(
                    key = "2025-09", label = "September 2025", daysRecorded = 20,
                    daysWithoutAbsence = 18, presentSessions = 45, absentSessions = 5, odSessions = 2,
                    calendar = listOf(M3EReportCalendarDay(9, "Tue", listOf("Class"))),
                    entries = listOf(
                        M3EReportAttendanceEntry(
                            date = "2025-09-09", courseCode = "CSE101",
                            classLabel = "L1 — •", status = "Present"
                        )
                    )
                )
            )
        ),
        odUsage = M3EReportOdUsage(
            claimedHours = 8, validHours = null, wastedHours = null,
            perSubject = listOf(odSubject),
            official = listOf(
                M3EReportOfficialOd(
                    date = "2025-09-09", type = "OD", reason = "Event — •",
                    basis = "Official", time = "10:00", remarks = "Approved <ok>"
                )
            )
        ),
        exams = listOf(
            M3EReportExam(
                series = "Mid", courseCode = "CSE101", courseTitle = "Data Structures — •",
                date = "2025-10-01", session = "FN", time = "09:00", venue = "R101", seat = "A1"
            )
        ),
        subjects = listOf(subject),
        events = listOf(
            M3EReportEvent(
                name = "Tech Fest — •", date = "2025-10-10", time = "10:00",
                venue = "Ground <main>", paymentStatus = "Paid"
            )
        ),
        finance = M3EReportFinance(
            hasDues = false, duesMessage = "No dues — •",
            receipts = listOf(M3EReportReceipt(number = "R1", date = "2025-09-01", amount = "1000")),
            wallet = listOf(
                M3EReportWalletEntry(
                    date = "2025-09-01", receiptNumber = "R1", type = "Credit",
                    amount = "1000", refundAmount = "0", balance = "1000"
                )
            )
        ),
        services = M3EReportServices(
            transport = listOf("Bus" to "Route <A>"),
            library = listOf("Books" to "12 issued — •"),
            moodle = listOf(
                M3EReportMoodleItem(
                    course = "CSE101", title = "Assignment <1>", dueDate = "2025-10-01", status = "Open"
                )
            ),
            hostel = listOf("Block" to "H1")
        )
    )

    // ---- JSON -----------------------------------------------------------------------------

    @Test
    fun json_uses_web_v1_schema_and_quoted_iso_timestamp() {
        val json = ReportSerializers.json(fixture)
        assertTrue(json.contains("\"schema\": \"amazecc.student-report.v1\""), json.take(160))
        assertTrue(json.contains("\"generatedAt\": \"2001-09-09T01:46:40Z\""), json.take(160))
        assertFalse(json.contains("\"generatedAt\": $EPOCH"), "raw epoch millis must not be emitted")
    }

    @Test
    fun json_preserves_utf8_and_escapes_markup() {
        val json = ReportSerializers.json(fixture)
        assertTrue(json.contains("José — •"))
        assertTrue(json.contains("—"))
        assertTrue(json.contains("•"))
        assertFalse(json.contains("?"), "JSON must never '?'-fold non-ASCII")
        // The quote inside school is JSON-escaped; <, >, & and ' are legal verbatim in JSON strings.
        assertTrue(json.contains("\"school\": \"<b>&\\\"'</b>\""), json.take(400))
    }

    @Test
    fun json_unknown_od_hours_are_null_not_zero() {
        val json = ReportSerializers.json(fixture)
        assertTrue(json.contains("\"claimedHours\": 8"))
        assertTrue(json.contains("\"validHours\": null"))
        assertTrue(json.contains("\"wastedHours\": null"))
        assertFalse(json.contains("\"validHours\": 0"))
        assertFalse(json.contains("\"wastedHours\": 0"))
    }

    @Test
    fun json_every_section_flag_gates_its_block() {
        val markers = mapOf(
            "overview" to "\"summary\"",
            "curriculum" to "\"curriculum\"",
            "subjects" to "\"subjects\"",
            "history" to "\"history\"",
            "attendance" to "\"months\"", // currentSemester.attendance is the only "months" block
            "od" to "\"odUsage\"",
            "exams" to "\"schedule\"",
            // A bare "events" key also appears inside calendar days, so use the fixture's
            // unique registered-event name as the marker for the top-level events block.
            "events" to "Tech Fest",
            "finance" to "\"finance\"",
            "services" to "\"services\""
        )
        for ((key, _) in fixture.sections.keys()) {
            val marker = markers.getValue(key)
            val off = ReportSerializers.json(fixture.copy(sections = withSection(key, false)))
            assertFalse(off.contains(marker), "JSON $key block must disappear when disabled")
            val on = ReportSerializers.json(fixture.copy(sections = withSection(key, true)))
            assertTrue(on.contains(marker), "JSON $key block must appear when enabled")
        }
    }

    // ---- HTML -----------------------------------------------------------------------------

    @Test
    fun html_is_standalone_utf8_with_disclaimer_and_real_tables() {
        val html = ReportSerializers.html(fixture)
        assertTrue(html.startsWith("<!DOCTYPE html>"))
        assertTrue(html.contains("<meta charset=\"UTF-8\">"))
        assertTrue(
            html.contains(
                "not an official institutional transcript or certificate. " +
                    "Missing source details are not inferred."
            )
        )
        assertTrue(html.contains("José — •"))
        assertFalse(html.contains("<script>"), "raw markup must never be injected")
        assertFalse(html.contains("<b>"), "fixture <b> must be escaped")
        assertTrue(html.contains("&lt;b&gt;"))
        assertTrue(Regex("<table>").findAll(html).count() >= 11, "one real table per section")
        assertTrue(html.contains("<footer>"))
    }

    @Test
    fun html_every_section_flag_gates_its_section() {
        val ids = mapOf(
            "overview" to "overview", "curriculum" to "curriculum", "subjects" to "subjects",
            "history" to "history", "attendance" to "attendance", "od" to "od",
            "exams" to "exams", "events" to "events", "finance" to "finance",
            "services" to "services"
        )
        for ((key, _) in fixture.sections.keys()) {
            val marker = "id=\"${ids.getValue(key)}\""
            val off = ReportSerializers.html(fixture.copy(sections = withSection(key, false)))
            assertFalse(off.contains(marker), "HTML $key section must disappear when disabled")
            val on = ReportSerializers.html(fixture.copy(sections = withSection(key, true)))
            assertTrue(on.contains(marker), "HTML $key section must appear when enabled")
        }
    }

    @Test
    fun html_unknown_od_is_explicit_never_zero() {
        val html = ReportSerializers.html(fixture)
        assertTrue(html.contains("unknown"))
        assertFalse(html.contains("valid 0h"))
        assertFalse(html.contains("wasted 0h"))
    }

    // ---- PDF ------------------------------------------------------------------------------

    @Test
    fun pdf_lines_are_ascii_with_toc_disclaimer_and_footer() {
        val lines = ReportSerializers.pdfContentLines(fixture)
        assertTrue(lines.isNotEmpty())
        assertTrue(lines.all { line -> line.all { it.code in 32..126 } }, "PDF lines must be ASCII-only")
        assertTrue(lines.any { it.contains("not an official institutional transcript or certificate") })
        assertTrue(lines.any { it == "CONTENTS" })
        assertTrue(lines.last().startsWith("Generated by AmazeCC."))
    }

    @Test
    fun pdf_every_section_flag_gates_its_header() {
        val headers = mapOf(
            "overview" to "ACADEMIC SUMMARY", "curriculum" to "CURRICULUM",
            "subjects" to "SUBJECT DOSSIERS", "history" to "GRADE HISTORY",
            "attendance" to "ATTENDANCE REGISTER", "od" to "OD USAGE",
            "exams" to "EXAM SCHEDULE", "events" to "EVENTS",
            "finance" to "FINANCE", "services" to "STUDENT SERVICES"
        )
        for ((key, _) in fixture.sections.keys()) {
            val header = headers.getValue(key)
            val off = ReportSerializers.pdfContentLines(fixture.copy(sections = withSection(key, false)))
            assertFalse(off.any { it == header }, "PDF $key header must disappear when disabled")
            val on = ReportSerializers.pdfContentLines(fixture.copy(sections = withSection(key, true)))
            assertTrue(on.any { it == header }, "PDF $key header must appear when enabled")
        }
    }

    @Test
    fun pdf_unknown_od_never_becomes_zero() {
        val lines = ReportSerializers.pdfContentLines(fixture)
        assertTrue(lines.any { it.contains("Claimed 8h, valid unknown, wasted unknown") })
        assertFalse(lines.any { it.contains("valid 0h") })
        assertFalse(lines.any { it.contains("wasted 0h") })
    }

    // ---- Share text (UTF-8) ---------------------------------------------------------------

    @Test
    fun share_text_preserves_utf8_and_includes_toc_disclaimer_and_footer() {
        val text = ReportSerializers.shareText(fixture)
        // Same fixture tokens that the JSON/HTML tests use, kept verbatim (no '?' folding).
        assertTrue(text.contains("José"), "share text must keep 'José' verbatim")
        assertTrue(text.contains("—"), "share text must keep the em dash verbatim")
        assertTrue(text.contains("•"), "share text must keep the bullet verbatim")
        assertTrue(text.contains("José — •"))
        assertFalse(text.contains("?"), "share text must never '?'-fold non-ASCII")
        // Structural parity with the PDF text layout.
        assertTrue(text.contains("not an official institutional transcript or certificate"))
        assertTrue(text.lines().any { it == "CONTENTS" })
        assertTrue(text.lines().any { it == "ACADEMIC SUMMARY" })
        assertTrue(text.lines().any { it.startsWith("Generated by AmazeCC.") })
        assertTrue(text.endsWith("\n"), "share text must end with a trailing newline")
    }

    @Test
    fun share_text_every_section_flag_gates_its_header() {
        val headers = mapOf(
            "overview" to "ACADEMIC SUMMARY", "curriculum" to "CURRICULUM",
            "subjects" to "SUBJECT DOSSIERS", "history" to "GRADE HISTORY",
            "attendance" to "ATTENDANCE REGISTER", "od" to "OD USAGE",
            "exams" to "EXAM SCHEDULE", "events" to "EVENTS",
            "finance" to "FINANCE", "services" to "STUDENT SERVICES"
        )
        for ((key, _) in fixture.sections.keys()) {
            val header = headers.getValue(key)
            // Exact-line matching: TOC entries are numbered ("1. HEADER"), so only the real
            // section header (and its block beneath it) can satisfy this assertion.
            val off = ReportSerializers.shareText(fixture.copy(sections = withSection(key, false)))
            assertFalse(off.lines().any { it == header }, "share text $key header must disappear when disabled")
            val on = ReportSerializers.shareText(fixture.copy(sections = withSection(key, true)))
            assertTrue(on.lines().any { it == header }, "share text $key header must appear when enabled")
        }
    }

    @Test
    fun pdf_lines_stay_ascii_after_share_text_refactor() {
        val shared = ReportSerializers.shareText(fixture)
        val lines = ReportSerializers.pdfContentLines(fixture)
        assertTrue(shared.contains("José"), "precondition: share text keeps Unicode")
        assertTrue(lines.isNotEmpty())
        assertTrue(lines.all { line -> line.all { it.code in 32..126 } }, "PDF lines must stay ASCII-only")
        assertFalse(lines.any { it.contains("José") || it.contains("—") || it.contains("•") })
    }

    // ---- Filename -------------------------------------------------------------------------

    @Test
    fun filename_uses_utc_date_and_sanitised_extension() {
        assertEquals(
            "AmazeCC-Student-Report-2001-09-09.pdf",
            ReportSerializers.filename("PDF", EPOCH)
        )
        assertEquals(
            "AmazeCC-Student-Report-2001-09-09.html",
            ReportSerializers.filename("html", EPOCH)
        )
        assertEquals(
            "AmazeCC-Student-Report-2001-09-09.txt",
            ReportSerializers.filename("!!!", EPOCH)
        )
    }

    // ---- Helpers --------------------------------------------------------------------------

    private fun withSection(key: String, enabled: Boolean): M3EReportSections {
        val base = fixture.sections
        return when (key) {
            "overview" -> base.copy(overview = enabled)
            "curriculum" -> base.copy(curriculum = enabled)
            "subjects" -> base.copy(subjects = enabled)
            "history" -> base.copy(history = enabled)
            "attendance" -> base.copy(attendance = enabled)
            "od" -> base.copy(od = enabled)
            "exams" -> base.copy(exams = enabled)
            "events" -> base.copy(events = enabled)
            "finance" -> base.copy(finance = enabled)
            "services" -> base.copy(services = enabled)
            else -> error("Unknown section key: $key")
        }
    }

    private companion object {
        // 2001-09-09T01:46:40Z — fixed so the UTC date is 2001-09-09 regardless of device TZ.
        const val EPOCH = 1_000_000_000_000L
    }
}
