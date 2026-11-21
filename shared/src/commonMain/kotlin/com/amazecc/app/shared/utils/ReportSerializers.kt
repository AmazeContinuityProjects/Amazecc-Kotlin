package com.amazecc.app.shared.utils

import com.amazecc.app.shared.domain.overhaul.*
import com.amazecc.app.shared.pdf.PdfEngine
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * D-152 — section-aware, web-parity serializers for the full [M3EReportData] snapshot.
 *
 * All four formats consume the same snapshot and honour every [M3EReportSections] flag:
 *  * [json] emits the `amazecc.student-report.v1` shape with a hand-written, UTF-8-preserving
 *    string escaper (the snapshot types are plain data classes, not `@Serializable`).
 *  * [html] emits a standalone UTF-8 HTML5 document with inline CSS, cover, TOC anchors and real
 *    `<table>` elements; every interpolated value goes through [escapeHtml].
 *  * [pdfContentLines] emits deterministic plain-text lines for [PdfEngine.buildTextPdf].
 *  * [shareText] emits the same text unfolded as UTF-8 for the OS share sheet.
 *
 * **Encoding limitation:** the pinned PDF writer is Helvetica/WinAnsi and its literal-string
 * escaper folds every character outside printable ASCII to `?`. Consequently the PDF path applies
 * [PdfEngine.sanitizeWinAnsi] punctuation folding and transliterates the remaining non-ASCII
 * characters to `?` — **only here**. HTML, JSON and [shareText] stay fully UTF-8; no folding is
 * applied to them.
 */
object ReportSerializers {

    private const val SCHEMA = "amazecc.student-report.v1"

    /** Required disclosure, reproduced verbatim in every format. */
    private const val DISCLAIMER =
        "not an official institutional transcript or certificate. Missing source details are not inferred."

    // trimIndent() is a function call, so this cannot be a `const val`.
    private val HTML_CSS = """
        body { font-family: system-ui, -apple-system, sans-serif; margin: 24px; line-height: 1.5; color: #1a1a1a; }
        h1 { font-size: 1.7rem; margin: 0 0 12px; }
        h2 { font-size: 1.3rem; margin-top: 32px; border-bottom: 1px solid #ccc; padding-bottom: 4px; }
        h3 { font-size: 1.05rem; margin-top: 20px; }
        .cover { background: #f7f9fc; border: 1px solid #dde3ec; border-radius: 8px; padding: 16px 20px; }
        .cover p { margin: 4px 0; }
        .meta { color: #555; font-size: 0.9rem; }
        .disclaimer { color: #8a3b12; font-size: 0.9rem; }
        .toc ol { padding-left: 20px; }
        table { border-collapse: collapse; width: 100%; margin: 12px 0; }
        th, td { border: 1px solid #ccc; padding: 7px 9px; text-align: left; vertical-align: top; }
        th { background: #eef2f7; }
        footer { margin-top: 32px; border-top: 1px solid #ccc; padding-top: 12px; color: #555; font-size: 0.85rem; }
    """.trimIndent()

    // ---- Public API ------------------------------------------------------------------------

    /** The web-parity JSON snapshot. Every data block is gated by its section flag. */
    fun json(data: M3EReportData): String {
        val s = data.sections
        val top = mutableListOf<Pair<String, String>>()
        top += "schema" to jq(SCHEMA)
        // Web parity: the schema emits new Date(collectedAt).toISOString(), not epoch millis.
        top += "generatedAt" to jq(isoUtc(data.generatedAt))
        // Identity is the report subject (the cover), so it is always present.
        top += "student" to studentJson("  ", data.identity)

        val current = mutableListOf<Pair<String, String>>()
        if (s.overview) current += "summary" to summaryJson("    ", data.summary)
        if (s.attendance) current += "attendance" to attendanceJson("    ", data.attendance)
        if (s.subjects) {
            current += "assessments" to assessmentsJson("    ", data)
            current += "subjects" to jArray("    ", data.subjects.map { subjectJson("      ", it) })
        }
        if (s.exams) current += "schedule" to jArray("    ", data.exams.map { examJson("      ", it) })
        if (s.od) current += "odUsage" to odUsageJson("    ", data.odUsage)
        if (current.isNotEmpty()) top += "currentSemester" to jObject("  ", *current.toTypedArray())

        if (s.history) top += "history" to jArray("  ", data.semesters.map { semesterJson("    ", it) })
        if (s.curriculum) top += "curriculum" to jArray("  ", data.curriculum.map { curriculumJson("    ", it) })
        // The academic calendar has no dedicated flag; it belongs to the overview block.
        if (s.overview) top += "academicCalendar" to jArray("  ", data.calendar.map { monthJson("    ", it) })
        if (s.od) {
            top += "officialOdRecords" to jArray("  ", data.odUsage.official.map { officialOdJson("    ", it) })
        }
        if (s.finance) top += "finance" to financeJson("  ", data.finance)
        if (s.services) top += "services" to servicesJson("  ", data.services)
        if (s.events) top += "events" to jArray("  ", data.events.map { eventJson("    ", it) })

        return jObject("", *top.toTypedArray()) + "\n"
    }

    /** A standalone, inline-CSS, UTF-8 HTML5 report with cover, TOC and real tables. */
    fun html(data: M3EReportData): String {
        val s = data.sections
        val generated = dateLabel(data.generatedAt)
        val toc = linkedMapOf<String, String>()
        if (s.overview) {
            toc["overview"] = "Academic Summary"
            toc["calendar"] = "Academic Calendar"
        }
        if (s.curriculum) toc["curriculum"] = "Curriculum"
        if (s.subjects) toc["subjects"] = "Subject Dossiers"
        if (s.history) toc["history"] = "Grade History"
        if (s.attendance) toc["attendance"] = "Attendance Register"
        if (s.od) toc["od"] = "OD Usage"
        if (s.exams) toc["exams"] = "Exam Schedule"
        if (s.events) toc["events"] = "Events"
        if (s.finance) toc["finance"] = "Finance"
        if (s.services) toc["services"] = "Student Services"

        return buildString {
            append("<!DOCTYPE html>\n<html lang=\"en\">\n<head>\n")
            append("<meta charset=\"UTF-8\">\n")
            append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n")
            append("<title>AmazeCC Student Academic Report</title>\n")
            append("<style>\n").append(HTML_CSS).append("\n</style>\n</head>\n<body>\n")

            append("<header class=\"cover\">\n")
            append("<h1>AmazeCC Student Academic Report</h1>\n")
            append("<p><strong>Student:</strong> ").append(e(data.identity.name)).append("</p>\n")
            append("<p><strong>Registration number:</strong> ").append(e(data.identity.registrationNumber)).append("</p>\n")
            append("<p><strong>Programme:</strong> ").append(e(data.identity.programme)).append("</p>\n")
            append("<p><strong>School:</strong> ").append(e(data.identity.school)).append("</p>\n")
            append("<p><strong>Campus:</strong> ").append(e(data.identity.campus)).append("</p>\n")
            if (!data.identity.proctorName.isNullOrBlank()) {
                append("<p><strong>Proctor:</strong> ").append(e(data.identity.proctorName)).append("</p>\n")
            }
            if (!data.identity.proctorEmail.isNullOrBlank()) {
                append("<p><strong>Proctor email:</strong> ").append(e(data.identity.proctorEmail)).append("</p>\n")
            }
            if (data.identity.semesterName.isNotBlank()) {
                append("<p><strong>Semester:</strong> ").append(e(data.identity.semesterName)).append("</p>\n")
            }
            append("<p class=\"meta\">Generated: ")
                .append(e("$generated (epoch ${data.generatedAt})")).append("</p>\n")
            append("<p class=\"disclaimer\">This report is ").append(e(DISCLAIMER)).append("</p>\n")
            append("</header>\n")

            append("<nav class=\"toc\"><h2>Contents</h2><ol>\n")
            toc.forEach { (id, label) ->
                append("<li><a href=\"#").append(e(id)).append("\">").append(e(label)).append("</a></li>\n")
            }
            append("</ol></nav>\n")

            if (s.overview) {
                append("<section id=\"overview\">\n<h2>Academic Summary</h2>\n")
                append(
                    table(
                        listOf("Metric", "Value"),
                        listOf(
                            listOf("CGPA", num(data.summary.cgpa)),
                            listOf("Current semester GPA", num(data.summary.currentGpa)),
                            listOf("Attendance %", num(data.summary.attendancePct)),
                            listOf("Classes attended", data.summary.classesAttended.toString()),
                            listOf("Classes total", data.summary.classesTotal.toString()),
                            listOf("Classes missed", data.summary.classesMissed.toString()),
                            listOf("Days without absence", data.summary.daysWithoutAbsence.toString()),
                            listOf("Days recorded", data.summary.daysRecorded.toString()),
                            listOf("Credits earned", data.summary.creditsEarned.toString()),
                            listOf("Credits registered", data.summary.creditsRegistered.toString()),
                            listOf("Arrears count", data.summary.arrearsCount.toString())
                        )
                    )
                )
                append("<h3>Semester GPA</h3>\n")
                append(
                    table(
                        listOf("Semester", "GPA"),
                        listOf(
                            listOf(data.identity.semesterName.ifBlank { "-" }, num(data.summary.currentGpa)),
                            listOf("Cumulative (CGPA)", num(data.summary.cgpa))
                        )
                    )
                )
                append("</section>\n")

                append("<section id=\"calendar\">\n<h2>Academic Calendar</h2>\n")
                append(
                    table(
                        listOf("Month", "Date", "Weekday", "Events"),
                        data.calendar.flatMap { m ->
                            m.days.map { d ->
                                listOf(m.label, d.date.toString(), d.weekday, d.events.joinToString(", "))
                            }
                        }
                    )
                )
                append("</section>\n")
            }

            if (s.curriculum) {
                append("<section id=\"curriculum\">\n<h2>Curriculum</h2>\n")
                append(
                    table(
                        listOf("Category", "Basket", "Code", "Course", "Credits", "Type", "Required", "Earned", "Progress %"),
                        data.curriculum.map {
                            listOf(
                                it.category, it.basket, it.code, it.title, it.credits.toString(), it.type,
                                it.required.toString(), it.earned.toString(), num(it.progressPct)
                            )
                        }
                    )
                )
                append("</section>\n")
            }

            if (s.subjects) {
                append("<section id=\"subjects\">\n<h2>Subject Dossiers</h2>\n")
                data.subjects.forEach { sub ->
                    append("<h3>").append(e("${sub.code} - ${sub.title}")).append("</h3>\n")
                    append(
                        table(
                            listOf("Field", "Value"),
                            listOf(
                                listOf("Code", sub.code),
                                listOf("Title", sub.title),
                                listOf("Type", sub.type),
                                listOf("Credits", sub.credits.toString()),
                                listOf("Class ID", sub.classId),
                                listOf("Faculty", sub.faculty),
                                listOf("Department", sub.department),
                                listOf("Slot", sub.slot),
                                listOf("Venue", sub.venue),
                                listOf(
                                    "Attendance",
                                    "${sub.attendance.attended}/${sub.attendance.total} (${num(sub.attendance.percentage)}%)"
                                ),
                                listOf("Published grade", sub.publishedGrade ?: "-"),
                                listOf("Assessment average", num(sub.assessmentAverage)),
                                listOf(
                                    "OD hours",
                                    sub.od?.let {
                                        "claimed ${it.claimedHours}h, valid ${odHours(it.validHours)}, " +
                                            "wasted ${odHours(it.wastedHours)}"
                                    } ?: "-"
                                ),
                                listOf(
                                    "Prediction",
                                    sub.prediction?.let { "${it.currentPace} / best ${it.bestGrade}" } ?: "-"
                                )
                            )
                        )
                    )
                    if (sub.assessments.isNotEmpty()) {
                        append(
                            table(
                                listOf("Assessment", "Status", "Scored", "Maximum", "Weightage %", "Weighted score"),
                                sub.assessments.map {
                                    listOf(
                                        it.title, it.status, num(it.scored), num(it.maximum),
                                        num(it.weightagePercent), num(it.weightedScore)
                                    )
                                }
                            )
                        )
                    }
                }
                append("</section>\n")
            }

            if (s.history) {
                append("<section id=\"history\">\n<h2>Grade History</h2>\n")
                append(
                    table(
                        listOf("Semester", "Code", "Course", "Type", "Credits", "Grand total", "Grade"),
                        data.semesters.flatMap { sem ->
                            sem.courses.map {
                                listOf(
                                    sem.name, it.code, it.title, it.type, it.credits.toString(),
                                    num(it.grandTotal), it.grade
                                )
                            }
                        }
                    )
                )
                append("</section>\n")
            }

            if (s.attendance) {
                append("<section id=\"attendance\">\n<h2>Attendance Register</h2>\n")
                append(
                    table(
                        listOf("Code", "Title", "Type", "Faculty", "Slot / Venue", "Attended", "Total", "%"),
                        data.attendance.courses.map {
                            listOf(
                                it.code, it.title, it.type, it.faculty, it.slotVenue,
                                it.attended.toString(), it.total.toString(), num(it.percentage)
                            )
                        }
                    )
                )
                append(
                    table(
                        listOf("Month", "Days recorded", "Days without absence", "Present", "Absent", "OD"),
                        data.attendance.months.map {
                            listOf(
                                it.label, it.daysRecorded.toString(), it.daysWithoutAbsence.toString(),
                                it.presentSessions.toString(), it.absentSessions.toString(), it.odSessions.toString()
                            )
                        }
                    )
                )
                append(
                    table(
                        listOf("Month", "Date", "Course", "Class", "Status"),
                        data.attendance.months.flatMap { m ->
                            m.entries.map { en ->
                                listOf(m.label, en.date, en.courseCode, en.classLabel, en.status)
                            }
                        }
                    )
                )
                append("</section>\n")
            }

            if (s.od) {
                append("<section id=\"od\">\n<h2>OD Usage</h2>\n")
                append(
                    table(
                        listOf("Claimed hours", "Valid hours", "Wasted hours"),
                        listOf(
                            listOf(
                                data.odUsage.claimedHours.toString(),
                                odHours(data.odUsage.validHours),
                                odHours(data.odUsage.wastedHours)
                            )
                        )
                    )
                )
                append(
                    table(
                        listOf("Code", "Claimed", "Valid", "Wasted", "Official match %"),
                        data.odUsage.perSubject.map {
                            listOf(
                                it.code, it.claimedHours.toString(), odHours(it.validHours),
                                odHours(it.wastedHours), num(it.officialMatchPct)
                            )
                        }
                    )
                )
                append(
                    table(
                        listOf("Date", "Type", "Reason", "Basis", "Time", "Remarks"),
                        data.odUsage.official.map {
                            listOf(it.date, it.type, it.reason, it.basis, it.time, it.remarks)
                        }
                    )
                )
                append("</section>\n")
            }

            if (s.exams) {
                append("<section id=\"exams\">\n<h2>Exam Schedule</h2>\n")
                append(
                    table(
                        listOf("Series", "Course code", "Course title", "Date", "Session", "Time", "Venue", "Seat"),
                        data.exams.map {
                            listOf(
                                it.series, it.courseCode, it.courseTitle, it.date, it.session,
                                it.time, it.venue, it.seat
                            )
                        }
                    )
                )
                append("</section>\n")
            }

            if (s.events) {
                append("<section id=\"events\">\n<h2>Events</h2>\n")
                append(
                    table(
                        listOf("Event", "Date", "Time", "Venue", "Payment status"),
                        data.events.map { listOf(it.name, it.date, it.time, it.venue, it.paymentStatus) }
                    )
                )
                append("</section>\n")
            }

            if (s.finance) {
                append("<section id=\"finance\">\n<h2>Finance</h2>\n")
                append(
                    table(
                        listOf("Dues", "Message"),
                        listOf(
                            listOf(
                                if (data.finance.hasDues) "Yes" else "No",
                                data.finance.duesMessage
                            )
                        )
                    )
                )
                append(
                    table(
                        listOf("Receipt no.", "Date", "Amount"),
                        data.finance.receipts.map { listOf(it.number, it.date, it.amount) }
                    )
                )
                append(
                    table(
                        listOf("Date", "Receipt no.", "Type", "Amount", "Refund", "Balance"),
                        data.finance.wallet.map {
                            listOf(it.date, it.receiptNumber, it.type, it.amount, it.refundAmount, it.balance)
                        }
                    )
                )
                append("</section>\n")
            }

            if (s.services) {
                append("<section id=\"services\">\n<h2>Student Services</h2>\n")
                append("<h3>Transport</h3>\n")
                append(table(listOf("Label", "Value"), data.services.transport.map { listOf(it.first, it.second) }))
                append("<h3>Library</h3>\n")
                append(table(listOf("Label", "Value"), data.services.library.map { listOf(it.first, it.second) }))
                append("<h3>Moodle</h3>\n")
                append(
                    table(
                        listOf("Course", "Title", "Due date", "Status"),
                        data.services.moodle.map { listOf(it.course, it.title, it.dueDate, it.status) }
                    )
                )
                append("<h3>Hostel</h3>\n")
                append(table(listOf("Label", "Value"), data.services.hostel.map { listOf(it.first, it.second) }))
                append("</section>\n")
            }

            append("<footer>Generated by AmazeCC on ").append(e(generated))
                .append(". This report is ").append(e(DISCLAIMER)).append("</footer>\n")
            append("</body>\n</html>\n")
        }
    }

    /** Deterministic plain-text lines for [PdfEngine.buildTextPdf] (ASCII-only, see class KDoc). */
    fun pdfContentLines(data: M3EReportData): List<String> = fullTextLines(data).map(::ascii)

    /**
     * UTF-8 plain-text report for the OS share sheet. Unlike [pdfContentLines], which must fold
     * every non-ASCII character to `?` for the pinned WinAnsi PDF writer, this keeps Unicode
     * verbatim — the share sheet carries UTF-8 and has no such limitation.
     */
    fun shareText(data: M3EReportData): String = fullTextLines(data).joinToString("\n") + "\n"

    /** Unfolded (UTF-8) text lines shared by [pdfContentLines] and [shareText]. */
    private fun fullTextLines(data: M3EReportData): List<String> {
        val s = data.sections
        val generated = dateLabel(data.generatedAt)
        val sm = data.summary
        val id = data.identity

        val preamble = mutableListOf<String>()
        preamble += "AMAZECC STUDENT ACADEMIC REPORT"
        preamble += "Generated: $generated (epoch ${data.generatedAt})"
        preamble += "Student: ${id.name}"
        preamble += "Registration: ${id.registrationNumber}"
        preamble += "Programme: ${id.programme}"
        preamble += "School: ${id.school} | Campus: ${id.campus}"
        if (!id.proctorName.isNullOrBlank()) preamble += "Proctor: ${id.proctorName}"
        if (!id.proctorEmail.isNullOrBlank()) preamble += "Proctor email: ${id.proctorEmail}"
        if (id.semesterName.isNotBlank()) preamble += "Semester: ${id.semesterName}"
        preamble += "DISCLAIMER: This report is $DISCLAIMER"

        val sections = mutableListOf<Pair<String, List<String>>>()

        if (s.overview) {
            sections += "ACADEMIC SUMMARY" to listOf(
                "CGPA: ${num(sm.cgpa)}",
                "Current semester GPA: ${num(sm.currentGpa)}",
                "Attendance: ${num(sm.attendancePct)}% (${sm.classesAttended}/${sm.classesTotal} classes, ${sm.classesMissed} missed)",
                "Days: ${sm.daysWithoutAbsence}/${sm.daysRecorded} without absence",
                "Credits: ${sm.creditsEarned} earned / ${sm.creditsRegistered} registered",
                "Arrears: ${sm.arrearsCount}",
                "Semester GPA: ${id.semesterName.ifBlank { "-" }} -> ${num(sm.currentGpa)}"
            )
            sections += "ACADEMIC CALENDAR" to data.calendar.flatMap { m ->
                listOf("${m.label}:") + m.days.map { d ->
                    "  ${d.date} ${d.weekday}: ${d.events.joinToString(", ").ifBlank { "-" }}"
                }
            }
        }

        if (s.curriculum) {
            sections += "CURRICULUM" to data.curriculum.map {
                "${it.category} | ${it.basket} | ${it.code} | ${it.title} | ${it.credits} cr | " +
                    "${it.type} | required ${it.required} | earned ${it.earned} | ${num(it.progressPct)}%"
            }
        }

        if (s.attendance) {
            val lines = mutableListOf<String>()
            data.attendance.courses.forEach {
                lines += "${it.code} ${it.title} [${it.type}] ${it.attended}/${it.total} " +
                    "(${num(it.percentage)}%) - ${it.faculty} - ${it.slotVenue}"
            }
            data.attendance.months.forEach { m ->
                lines += "Month ${m.label}: days ${m.daysWithoutAbsence}/${m.daysRecorded}, " +
                    "present ${m.presentSessions}, absent ${m.absentSessions}, OD ${m.odSessions}"
                m.entries.forEach { en ->
                    lines += "  ${m.label} ${en.date} ${en.courseCode} ${en.classLabel} ${en.status}"
                }
            }
            sections += "ATTENDANCE REGISTER" to lines
        }

        if (s.od) {
            val lines = mutableListOf<String>()
            lines += "Claimed ${data.odUsage.claimedHours}h, valid ${odHours(data.odUsage.validHours)}, " +
                "wasted ${odHours(data.odUsage.wastedHours)}"
            data.odUsage.perSubject.forEach {
                lines += "${it.code}: claimed ${it.claimedHours}h, valid ${odHours(it.validHours)}, " +
                    "wasted ${odHours(it.wastedHours)}, official match ${num(it.officialMatchPct)}%"
            }
            data.odUsage.official.forEach {
                lines += "${it.date} ${it.type} ${it.reason} (${it.basis}, ${it.time}) ${it.remarks}"
            }
            sections += "OD USAGE" to lines
        }

        if (s.exams) {
            sections += "EXAM SCHEDULE" to data.exams.map {
                "${it.series}: ${it.courseCode} ${it.courseTitle} | ${it.date} ${it.session} ${it.time} | " +
                    "${it.venue} | seat ${it.seat}"
            }
        }

        if (s.subjects) {
            val lines = mutableListOf<String>()
            data.subjects.forEach { sub ->
                lines += "--- ${sub.code} ${sub.title} (${sub.type}, ${sub.credits} cr) ---"
                lines += "  Class: ${sub.classId} | Faculty: ${sub.faculty} | Department: ${sub.department}"
                lines += "  Slot: ${sub.slot} | Venue: ${sub.venue}"
                lines += "  Attendance: ${sub.attendance.attended}/${sub.attendance.total} " +
                    "(${num(sub.attendance.percentage)}%)"
                lines += "  Published grade: ${sub.publishedGrade ?: "-"}"
                lines += "  Assessment average: ${num(sub.assessmentAverage)}"
                sub.assessments.forEach { a ->
                    lines += "    ${a.title}: ${num(a.scored)}/${num(a.maximum)} (${a.status}), " +
                        "weightage ${num(a.weightagePercent)}%, weighted ${num(a.weightedScore)}"
                }
                sub.od?.let {
                    lines += "  OD: claimed ${it.claimedHours}h, valid ${odHours(it.validHours)}, " +
                        "wasted ${odHours(it.wastedHours)}, match ${num(it.officialMatchPct)}%"
                }
                sub.prediction?.let {
                    lines += "  Prediction: pace ${it.currentPace}, best ${it.bestGrade}, " +
                        "standing ${num(it.currentStandingPct)}%, max ${num(it.maxPossiblePct)}%, " +
                        "class avg ${num(it.classAveragePct)}%, size ${it.classSize}"
                }
            }
            sections += "SUBJECT DOSSIERS" to lines
        }

        if (s.history) {
            val lines = mutableListOf<String>()
            data.semesters.forEach { sem ->
                lines += "Semester ${sem.name} [${sem.semesterId}] GPA ${num(sem.gpa)}"
                sem.courses.forEach { c ->
                    lines += "  ${c.code} ${c.title} (${c.type}) ${c.credits} cr | " +
                        "grand total ${num(c.grandTotal)} | grade ${c.grade}"
                    c.components.forEach { a ->
                        lines += "    ${a.title}: ${num(a.scored)}/${num(a.maximum)} (${a.status})"
                    }
                }
            }
            sections += "GRADE HISTORY" to lines
        }

        if (s.events) {
            sections += "EVENTS" to data.events.map {
                "${it.name} | ${it.date} ${it.time} | ${it.venue} | payment ${it.paymentStatus}"
            }
        }

        if (s.finance) {
            val lines = mutableListOf<String>()
            lines += "Dues: ${if (data.finance.hasDues) "Yes" else "No"} - ${data.finance.duesMessage}"
            data.finance.receipts.forEach {
                lines += "Receipt ${it.number} | ${it.date} | ${it.amount}"
            }
            data.finance.wallet.forEach {
                lines += "Wallet ${it.date} | ${it.receiptNumber} | ${it.type} | ${it.amount} | " +
                    "refund ${it.refundAmount} | balance ${it.balance}"
            }
            sections += "FINANCE" to lines
        }

        if (s.services) {
            val lines = mutableListOf<String>()
            data.services.transport.forEach { lines += "Transport: ${it.first} - ${it.second}" }
            data.services.library.forEach { lines += "Library: ${it.first} - ${it.second}" }
            data.services.moodle.forEach {
                lines += "Moodle ${it.course}: ${it.title} (due ${it.dueDate}) - ${it.status}"
            }
            data.services.hostel.forEach { lines += "Hostel: ${it.first} - ${it.second}" }
            sections += "STUDENT SERVICES" to lines
        }

        val final = mutableListOf<String>()
        final += preamble
        final += ""
        final += "CONTENTS"
        sections.forEachIndexed { index, (header, _) -> final += "${index + 1}. $header" }
        final += ""
        sections.forEach { (header, lines) ->
            final += header
            final += "-".repeat(header.length)
            final += lines
            final += ""
        }
        final += "Generated by AmazeCC. $generated. Not an official institutional transcript."
        return final
    }

    /** `AmazeCC-Student-Report-YYYY-MM-DD.<ext>` from a real epoch millis and a sanitised extension. */
    fun filename(extension: String, generatedAt: Long): String {
        val ext = buildString(extension.length) {
            extension.lowercase().forEach { ch ->
                if (ch in 'a'..'z' || ch in '0'..'9') append(ch)
            }
        }.ifEmpty { "txt" }
        return "AmazeCC-Student-Report-${dateLabel(generatedAt)}.$ext"
    }

    // ---- JSON helpers ----------------------------------------------------------------------

    /** Correct JSON string escaper: quotes, backslash and control chars; UTF-8 is preserved. */
    private fun jq(value: String): String = buildString(value.length + 2) {
        append('"')
        value.forEach { ch ->
            when (ch) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\b' -> append("\\b")
                '\u000C' -> append("\\f")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (ch.code < 0x20) {
                    append("\\u").append(ch.code.toString(16).padStart(4, '0'))
                } else {
                    append(ch) // non-ASCII is emitted verbatim (UTF-8, never '?'-folded)
                }
            }
        }
        append('"')
    }

    private fun jqNullable(value: String?): String = value?.let(::jq) ?: "null"

    /** JSON has no NaN/Infinity literals; non-finite values are represented as 0. */
    private fun jn(value: Double): String = if (value.isFinite()) value.toString() else "0"
    private fun jnq(value: Double?): String = value?.let(::jn) ?: "null"
    private fun jiq(value: Int?): String = value?.toString() ?: "null"
    private fun jb(value: Boolean): String = if (value) "true" else "false"

    /** Web schema v1 timestamp: `new Date(collectedAt).toISOString()` — UTC, quoted by the caller. */
    private fun isoUtc(epochMillis: Long): String = Instant.fromEpochMilliseconds(epochMillis).toString()

    private fun jObject(indent: String, vararg fields: Pair<String, String>): String = buildString {
        append("{\n")
        fields.forEachIndexed { index, (key, value) ->
            append(indent).append("  ").append(jq(key)).append(": ").append(value)
            if (index < fields.lastIndex) append(',')
            append('\n')
        }
        append(indent).append('}')
    }

    private fun jArray(indent: String, items: List<String>): String = buildString {
        if (items.isEmpty()) {
            append("[]")
        } else {
            append("[\n")
            items.forEachIndexed { index, item ->
                append(indent).append("  ").append(item)
                if (index < items.lastIndex) append(',')
                append('\n')
            }
            append(indent).append(']')
        }
    }

    private fun studentJson(indent: String, i: M3EReportIdentity): String = jObject(
        indent,
        "name" to jq(i.name),
        "registrationNumber" to jq(i.registrationNumber),
        "programme" to jq(i.programme),
        "school" to jq(i.school),
        "campus" to jq(i.campus),
        "proctorName" to jqNullable(i.proctorName),
        "proctorEmail" to jqNullable(i.proctorEmail),
        "photoBase64" to jqNullable(i.photoBase64),
        "semesterName" to jq(i.semesterName)
    )

    private fun summaryJson(indent: String, s: M3EReportSummary): String = jObject(
        indent,
        "cgpa" to jn(s.cgpa),
        "currentGpa" to jnq(s.currentGpa),
        "attendancePct" to jn(s.attendancePct),
        "classesAttended" to s.classesAttended.toString(),
        "classesTotal" to s.classesTotal.toString(),
        "daysWithoutAbsence" to s.daysWithoutAbsence.toString(),
        "daysRecorded" to s.daysRecorded.toString(),
        "classesMissed" to s.classesMissed.toString(),
        "creditsEarned" to s.creditsEarned.toString(),
        "creditsRegistered" to s.creditsRegistered.toString(),
        "arrearsCount" to s.arrearsCount.toString()
    )

    private fun attendanceJson(indent: String, a: M3EReportAttendance): String = jObject(
        indent,
        "courses" to jArray("$indent  ", a.courses.map { attendanceCourseJson("$indent    ", it) }),
        "months" to jArray("$indent  ", a.months.map { attendanceMonthJson("$indent    ", it) })
    )

    private fun attendanceCourseJson(indent: String, c: M3EReportAttendanceCourse): String = jObject(
        indent,
        "code" to jq(c.code),
        "title" to jq(c.title),
        "type" to jq(c.type),
        "faculty" to jq(c.faculty),
        "slotVenue" to jq(c.slotVenue),
        "attended" to c.attended.toString(),
        "total" to c.total.toString(),
        "percentage" to jn(c.percentage)
    )

    private fun attendanceMonthJson(indent: String, m: M3EReportAttendanceMonth): String = jObject(
        indent,
        "key" to jq(m.key),
        "label" to jq(m.label),
        "daysRecorded" to m.daysRecorded.toString(),
        "daysWithoutAbsence" to m.daysWithoutAbsence.toString(),
        "presentSessions" to m.presentSessions.toString(),
        "absentSessions" to m.absentSessions.toString(),
        "odSessions" to m.odSessions.toString(),
        "calendar" to jArray("$indent  ", m.calendar.map { calendarDayJson("$indent    ", it) }),
        "entries" to jArray("$indent  ", m.entries.map { attendanceEntryJson("$indent    ", it) })
    )

    private fun attendanceEntryJson(indent: String, e: M3EReportAttendanceEntry): String = jObject(
        indent,
        "date" to jq(e.date),
        "courseCode" to jq(e.courseCode),
        "classLabel" to jq(e.classLabel),
        "status" to jq(e.status)
    )

    /** Flattened current-semester assessments; falls back to subject assessments when empty. */
    private fun assessmentsJson(indent: String, data: M3EReportData): String {
        val current = data.semesters.firstOrNull { it.name == data.identity.semesterName }
            ?: data.semesters.firstOrNull()
        val items = mutableListOf<String>()
        current?.courses?.forEach { course ->
            course.components.forEach { a ->
                items += assessmentWithContextJson("$indent  ", course.code, course.title, a)
            }
        }
        if (items.isEmpty()) {
            data.subjects.forEach { sub ->
                sub.assessments.forEach { a ->
                    items += assessmentWithContextJson("$indent  ", sub.code, sub.title, a)
                }
            }
        }
        return jArray(indent, items)
    }

    private fun assessmentWithContextJson(
        indent: String,
        code: String,
        courseTitle: String,
        a: M3EReportAssessment
    ): String = jObject(
        indent,
        "code" to jq(code),
        "courseTitle" to jq(courseTitle),
        "title" to jq(a.title),
        "status" to jq(a.status),
        "scored" to jn(a.scored),
        "maximum" to jn(a.maximum),
        "weightagePercent" to jn(a.weightagePercent),
        "weightedScore" to jn(a.weightedScore)
    )

    private fun assessmentJson(indent: String, a: M3EReportAssessment): String = jObject(
        indent,
        "title" to jq(a.title),
        "status" to jq(a.status),
        "scored" to jn(a.scored),
        "maximum" to jn(a.maximum),
        "weightagePercent" to jn(a.weightagePercent),
        "weightedScore" to jn(a.weightedScore)
    )

    private fun subjectJson(indent: String, s: M3EReportSubject): String = jObject(
        indent,
        "code" to jq(s.code),
        "title" to jq(s.title),
        "type" to jq(s.type),
        "credits" to s.credits.toString(),
        "classId" to jq(s.classId),
        "faculty" to jq(s.faculty),
        "department" to jq(s.department),
        "slot" to jq(s.slot),
        "venue" to jq(s.venue),
        "attendance" to attendanceCourseJson("$indent  ", s.attendance),
        "publishedGrade" to jqNullable(s.publishedGrade),
        "assessmentAverage" to jnq(s.assessmentAverage),
        "assessments" to jArray("$indent  ", s.assessments.map { assessmentJson("$indent    ", it) }),
        "od" to (s.od?.let { odSubjectJson("$indent  ", it) } ?: "null"),
        "prediction" to (s.prediction?.let { predictionJson("$indent  ", it) } ?: "null")
    )

    private fun predictionJson(indent: String, p: M3EReportPrediction): String = jObject(
        indent,
        "currentPace" to jq(p.currentPace),
        "bestGrade" to jq(p.bestGrade),
        "currentStandingPct" to jnq(p.currentStandingPct),
        "maxPossiblePct" to jnq(p.maxPossiblePct),
        "classAveragePct" to jnq(p.classAveragePct),
        "classSize" to p.classSize.toString()
    )

    private fun semesterJson(indent: String, s: M3EReportSemester): String = jObject(
        indent,
        "semesterId" to jq(s.semesterId),
        "name" to jq(s.name),
        "gpa" to jnq(s.gpa),
        "courses" to jArray("$indent  ", s.courses.map { gradeCourseJson("$indent    ", it) })
    )

    private fun gradeCourseJson(indent: String, c: M3EReportGradeCourse): String = jObject(
        indent,
        "code" to jq(c.code),
        "title" to jq(c.title),
        "type" to jq(c.type),
        "credits" to c.credits.toString(),
        "grandTotal" to jnq(c.grandTotal),
        "grade" to jq(c.grade),
        "components" to jArray("$indent  ", c.components.map { assessmentJson("$indent    ", it) })
    )

    private fun curriculumJson(indent: String, c: M3EReportCurriculumRow): String = jObject(
        indent,
        "category" to jq(c.category),
        "basket" to jq(c.basket),
        "code" to jq(c.code),
        "title" to jq(c.title),
        "credits" to c.credits.toString(),
        "type" to jq(c.type),
        "required" to c.required.toString(),
        "earned" to c.earned.toString(),
        "progressPct" to jn(c.progressPct)
    )

    private fun monthJson(indent: String, m: M3EReportCalendarMonth): String = jObject(
        indent,
        "key" to jq(m.key),
        "label" to jq(m.label),
        "days" to jArray("$indent  ", m.days.map { calendarDayJson("$indent    ", it) })
    )

    private fun calendarDayJson(indent: String, d: M3EReportCalendarDay): String = jObject(
        indent,
        "date" to d.date.toString(),
        "weekday" to jq(d.weekday),
        "events" to jArray("$indent  ", d.events.map(::jq))
    )

    private fun odUsageJson(indent: String, o: M3EReportOdUsage): String = jObject(
        indent,
        "claimedHours" to o.claimedHours.toString(),
        // UNKNOWN (no tracker) is JSON null here — never 0 and never claimedHours.
        "validHours" to jiq(o.validHours),
        "wastedHours" to jiq(o.wastedHours),
        "perSubject" to jArray("$indent  ", o.perSubject.map { odSubjectJson("$indent    ", it) }),
        "official" to jArray("$indent  ", o.official.map { officialOdJson("$indent    ", it) })
    )

    private fun odSubjectJson(indent: String, o: M3EReportOdSubject): String = jObject(
        indent,
        "code" to jq(o.code),
        "claimedHours" to o.claimedHours.toString(),
        "validHours" to jiq(o.validHours),
        "wastedHours" to jiq(o.wastedHours),
        "officialMatchPct" to jn(o.officialMatchPct)
    )

    private fun officialOdJson(indent: String, o: M3EReportOfficialOd): String = jObject(
        indent,
        "date" to jq(o.date),
        "type" to jq(o.type),
        "reason" to jq(o.reason),
        "basis" to jq(o.basis),
        "time" to jq(o.time),
        "remarks" to jq(o.remarks)
    )

    private fun examJson(indent: String, e: M3EReportExam): String = jObject(
        indent,
        "series" to jq(e.series),
        "courseCode" to jq(e.courseCode),
        "courseTitle" to jq(e.courseTitle),
        "date" to jq(e.date),
        "session" to jq(e.session),
        "time" to jq(e.time),
        "venue" to jq(e.venue),
        "seat" to jq(e.seat)
    )

    private fun eventJson(indent: String, e: M3EReportEvent): String = jObject(
        indent,
        "name" to jq(e.name),
        "date" to jq(e.date),
        "time" to jq(e.time),
        "venue" to jq(e.venue),
        "paymentStatus" to jq(e.paymentStatus)
    )

    private fun financeJson(indent: String, f: M3EReportFinance): String = jObject(
        indent,
        "hasDues" to jb(f.hasDues),
        "duesMessage" to jq(f.duesMessage),
        "receipts" to jArray("$indent  ", f.receipts.map { receiptJson("$indent    ", it) }),
        "wallet" to jArray("$indent  ", f.wallet.map { walletJson("$indent    ", it) })
    )

    private fun receiptJson(indent: String, r: M3EReportReceipt): String = jObject(
        indent,
        "number" to jq(r.number),
        "date" to jq(r.date),
        "amount" to jq(r.amount)
    )

    private fun walletJson(indent: String, w: M3EReportWalletEntry): String = jObject(
        indent,
        "date" to jq(w.date),
        "receiptNumber" to jq(w.receiptNumber),
        "type" to jq(w.type),
        "amount" to jq(w.amount),
        "refundAmount" to jq(w.refundAmount),
        "balance" to jq(w.balance)
    )

    private fun servicesJson(indent: String, s: M3EReportServices): String = jObject(
        indent,
        "transport" to jArray("$indent  ", s.transport.map { pairJson("$indent    ", it) }),
        "library" to jArray("$indent  ", s.library.map { pairJson("$indent    ", it) }),
        "moodle" to jArray("$indent  ", s.moodle.map { moodleJson("$indent    ", it) }),
        "hostel" to jArray("$indent  ", s.hostel.map { pairJson("$indent    ", it) })
    )

    private fun pairJson(indent: String, p: Pair<String, String>): String = jObject(
        indent,
        "label" to jq(p.first),
        "value" to jq(p.second)
    )

    private fun moodleJson(indent: String, m: M3EReportMoodleItem): String = jObject(
        indent,
        "course" to jq(m.course),
        "title" to jq(m.title),
        "dueDate" to jq(m.dueDate),
        "status" to jq(m.status)
    )

    // ---- HTML / PDF helpers -----------------------------------------------------------------

    /** Escapes every interpolated value through the shared HtmlExport escaper. */
    private fun e(value: String?): String = escapeHtml(value ?: "")

    /** Stable nullable-double display: no fabricated zero for a genuinely absent value. */
    private fun num(value: Double?): String = value?.let {
        if (it.isFinite()) it.toString() else "-"
    } ?: "-"

    /** Nullable OD hours in text formats: UNKNOWN is the word "unknown", never 0 or claimedHours. */
    private fun odHours(value: Int?): String = value?.let { "${it}h" } ?: "unknown"

    private fun table(headers: List<String>, rows: List<List<String>>): String = buildString {
        append("<table>\n<thead><tr>\n")
        headers.forEach { append("<th>").append(escapeHtml(it)).append("</th>\n") }
        append("</tr></thead>\n<tbody>\n")
        rows.forEach { row ->
            append("<tr>\n")
            row.forEach { cell -> append("<td>").append(escapeHtml(cell)).append("</td>\n") }
            append("</tr>\n")
        }
        append("</tbody>\n</table>\n")
    }

    /** Punctuation fold (sanitizeWinAnsi) then '?' for the rest: ASCII-only for the pinned writer. */
    private fun ascii(raw: String): String {
        val folded = PdfEngine.sanitizeWinAnsi(raw.replace('\n', ' ').replace('\r', ' ').replace('\t', ' '))
        return buildString(folded.length) {
            folded.forEach { ch -> append(if (ch.code in 32..126) ch else '?') }
        }
    }

    /** UTC calendar date (`toISOString().slice(0,10)` web parity), zero-padded `YYYY-MM-DD`. */
    private fun dateLabel(generatedAt: Long): String =
        Instant.fromEpochMilliseconds(generatedAt)
            .toLocalDateTime(TimeZone.UTC)
            .date.toString()
}
