package com.amazecc.app.shared.domain

import com.amazecc.app.shared.domain.overhaul.M3EReportAssessment
import com.amazecc.app.shared.domain.overhaul.M3EReportAttendance
import com.amazecc.app.shared.domain.overhaul.M3EReportAttendanceCourse
import com.amazecc.app.shared.domain.overhaul.M3EReportAttendanceEntry
import com.amazecc.app.shared.domain.overhaul.M3EReportAttendanceMonth
import com.amazecc.app.shared.domain.overhaul.M3EReportCalendarDay
import com.amazecc.app.shared.domain.overhaul.M3EReportCalendarMonth
import com.amazecc.app.shared.domain.overhaul.M3EReportCurriculumRow
import com.amazecc.app.shared.domain.overhaul.M3EReportData
import com.amazecc.app.shared.domain.overhaul.M3EReportEvent
import com.amazecc.app.shared.domain.overhaul.M3EReportExam
import com.amazecc.app.shared.domain.overhaul.M3EReportFinance
import com.amazecc.app.shared.domain.overhaul.M3EReportGradeCourse
import com.amazecc.app.shared.domain.overhaul.M3EReportIdentity
import com.amazecc.app.shared.domain.overhaul.M3EReportMoodleItem
import com.amazecc.app.shared.domain.overhaul.M3EReportOdSubject
import com.amazecc.app.shared.domain.overhaul.M3EReportOdUsage
import com.amazecc.app.shared.domain.overhaul.M3EReportOfficialOd
import com.amazecc.app.shared.domain.overhaul.M3EReportPrediction
import com.amazecc.app.shared.domain.overhaul.M3EReportReceipt
import com.amazecc.app.shared.domain.overhaul.M3EReportSections
import com.amazecc.app.shared.domain.overhaul.M3EReportSemester
import com.amazecc.app.shared.domain.overhaul.M3EReportServices
import com.amazecc.app.shared.domain.overhaul.M3EReportSubject
import com.amazecc.app.shared.domain.overhaul.M3EReportSummary
import com.amazecc.app.shared.domain.overhaul.M3EReportWalletEntry
import com.amazecc.app.shared.model.AllGradesRes
import com.amazecc.app.shared.model.CurriculumCategory
import com.amazecc.app.shared.model.CurriculumRes
import com.amazecc.app.shared.model.OdDetailsRes
import com.amazecc.app.shared.model.OdRecordItem
import com.amazecc.app.shared.model.StudentIdentity
import com.amazecc.app.shared.ui.screens.m3e.M3EGradeComputations
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * D-151 — the single, synchronous transition from the app's real caches to the full web-parity
 * student report snapshot. All three serializers (JSON / HTML / PDF) consume the one value this
 * function returns; none of them re-derives a figure.
 *
 * Rules this file follows, all of them load-bearing for parity with
 * `AmazeCC/src/components/custom/reports/StudentReportPage.tsx`:
 *
 *  - **Nothing is invented.** No source means an empty list, an empty string, zero only where the
 *    model's type is a non-null number and the codebase already uses zero as "no data", or null
 *    where the model allows it (published GPA, class average, prediction, and — crucially — OD
 *    valid/wasted hours, which are UNKNOWN rather than zero when their source is absent). No
 *    plausible-looking fallback numbers, no synthetic calendar dates, no fake official OD rows.
 *  - **One formula per fact.** Attendance comes from [Projections.summariseAttendance], earned
 *    credits from [Projections.creditsEarned], OD hours from the web-exact
 *    [deriveOdDaysFromSnapshot], grade rows from [GradeHistory.semesterRows], and the grade
 *    prediction from the canonical [computeCoursePrediction]. This file only maps shapes.
 *  - **Reporting semester vs active semester.** The headline attendance and the datewise register
 *    use [Projections.currentSemesterAttendance] (the semester that actually has attendance),
 *    while identity, marks and the current GPA use [Projections.activeSemester].
 *  - **Embedded theory/lab pairs share one dossier**, keyed on [EmbeddedLinkCode.baseCode].
 *
 * Fully pure and deterministic: every input arrives through a parameter. Official VTOP OD records
 * are threaded in through [officialOd] by the host; there is no global/singleton side channel. No
 * coroutine is launched, no I/O is performed, no clock is read, and no Composable is touched.
 */
object ReportDataAssembler {

    fun assemble(
        snapshot: DomainSnapshot,
        allGrades: AllGradesRes?,
        identity: StudentIdentity?,
        sections: M3EReportSections = M3EReportSections(),
        generatedAt: Long,
        officialOd: OdDetailsRes? = null,
    ): M3EReportData {
        // A stable "now" derived from the caller's timestamp keeps month-label fallbacks
        // deterministic and avoids reading the system clock.
        val anchorDate = runCatching {
            Instant.fromEpochMilliseconds(generatedAt).toLocalDateTime(TimeZone.UTC).date
        }.getOrElse { LocalDate(1970, 1, 1) }

        val activeSemester = Projections.activeSemester(snapshot)
        val attendanceRows = Projections.currentSemesterAttendance(snapshot)
        val attendanceSummary = Projections.summariseAttendance(
            attendanceRows.map { it.attendedClasses to it.totalClasses },
        )
        val attendanceMonths = buildAttendanceMonths(attendanceRows)
        val odUsage = buildOdUsage(snapshot, officialOd)
        val passedCodes = passedCourseCodes(snapshot, allGrades)

        return M3EReportData(
            generatedAt = generatedAt,
            identity = buildIdentity(snapshot, activeSemester, identity),
            sections = sections,
            summary = buildSummary(
                snapshot, allGrades, activeSemester, attendanceSummary, attendanceMonths, passedCodes,
            ),
            semesters = buildSemesters(snapshot, allGrades),
            curriculum = buildCurriculum(snapshot, allGrades, passedCodes),
            calendar = buildCalendar(snapshot.schedule.calendar, anchorDate),
            attendance = M3EReportAttendance(
                courses = attendanceRows.map(::toAttendanceCourse),
                months = attendanceMonths,
            ),
            odUsage = odUsage,
            exams = buildExams(snapshot),
            subjects = buildSubjects(snapshot, allGrades, odUsage.perSubject),
            events = buildEvents(snapshot),
            finance = buildFinance(snapshot),
            services = buildServices(snapshot),
        )
    }

    // ── scalar helpers ──────────────────────────────────────────────────────────

    /** VTOP publishes every number as text; a blank or unparseable cell is never silently a value. */
    private fun num(value: String?): Double? = M3EGradeComputations.num(value)

    private fun asInt(value: String?): Int = num(value)?.toInt() ?: 0

    private fun progress(earned: Int, required: Int): Double =
        if (required <= 0) 0.0
        else M3EGradeComputations.round2(
            (earned.toDouble() / required.toDouble() * 100.0).coerceAtMost(100.0),
        )

    // ── identity & summary ──────────────────────────────────────────────────────

    private fun buildIdentity(
        snapshot: DomainSnapshot,
        activeSemester: Semester?,
        identity: StudentIdentity?,
    ): M3EReportIdentity = M3EReportIdentity(
        name = identity?.name.orEmpty(),
        registrationNumber = identity?.regNo.orEmpty(),
        programme = identity?.program.orEmpty(),
        school = identity?.school.orEmpty(),
        campus = identity?.campus.orEmpty(),
        proctorName = identity?.proctor?.name,
        proctorEmail = identity?.proctor?.email,
        photoBase64 = identity?.photoBase64,
        semesterName = activeSemester?.name
            ?: activeSemester?.id?.let(GradeHistory::semesterName).orEmpty(),
    )

    private fun buildSummary(
        snapshot: DomainSnapshot,
        allGrades: AllGradesRes?,
        activeSemester: Semester?,
        attendance: Projections.AttendanceSummary,
        attendanceMonths: List<M3EReportAttendanceMonth>,
        passedCodes: Set<String>,
    ): M3EReportSummary {
        val cgpaSource = snapshot.academics.publishedCgpa ?: allGrades?.overall?.cgpa
        // Earned credits: canonical Projections formula over every semester's merged courses —
        // never the report host's hand-rolled sum and never the CGPA block's convenience text.
        val allCourses = snapshot.academics.semesters.keys
            .flatMap { Projections.mergedCourses(snapshot, it) }
        val earnedCredits = Projections.creditsEarned(
            allCourses.map { it.credits to (it.grade != null) },
        ).toInt()

        return M3EReportSummary(
            cgpa = num(cgpaSource?.cgpa) ?: 0.0,
            currentGpa = num(activeSemester?.gpa),
            attendancePct = M3EGradeComputations.round2(attendance.percentage.toDouble()),
            classesAttended = attendance.attended,
            classesTotal = attendance.total,
            daysWithoutAbsence = attendanceMonths.sumOf { it.daysWithoutAbsence },
            daysRecorded = attendanceMonths.sumOf { it.daysRecorded },
            classesMissed = (attendance.total - attendance.attended).coerceAtLeast(0),
            creditsEarned = earnedCredits,
            // Registered credits are a degree-wide published fact from the CGPA summary.
            creditsRegistered = asInt(cgpaSource?.creditsRegistered),
            arrearsCount = arrearsCount(snapshot, allGrades, passedCodes),
        )
    }

    /**
     * Arrears are counted across the whole grade history, not just this term's Fs. A
     * `(semesterId, baseCode)` set de-duplicates the snapshot and the richer all-grades payload
     * when both carry the same row.
     */
    private fun arrearsCount(
        snapshot: DomainSnapshot,
        allGrades: AllGradesRes?,
        passedCodes: Set<String>,
    ): Int {
        val failed = mutableSetOf<Pair<String, String>>()
        snapshot.academics.semesters.forEach { (semesterId, semester) ->
            semester.courses.values.forEach { course ->
                val letter = course.grade?.letter?.trim()?.uppercase().orEmpty()
                if (letter == "F") failed += semesterId to EmbeddedLinkCode.baseCode(course.code)
            }
        }
        allGrades?.allSemesters?.forEach { (semesterId, result) ->
            result?.grades.orEmpty().forEach { grade ->
                if (grade.grade.trim().uppercase() == "F") {
                    failed += semesterId to EmbeddedLinkCode.baseCode(grade.courseCode)
                }
            }
        }
        // A later pass in the same code clears the arrear; the grade payload is authoritative
        // for what was finally awarded, so only count Fs that never got a pass anywhere.
        return failed.count { (_, code) -> code !in passedCodes }
    }

    /** Base codes with a published, recognised, non-arrear grade anywhere in the history. */
    private fun passedCourseCodes(snapshot: DomainSnapshot, allGrades: AllGradesRes?): Set<String> {
        val passed = mutableSetOf<String>()
        snapshot.academics.semesters.values.forEach { semester ->
            semester.courses.values.forEach { course ->
                val letter = course.grade?.letter?.trim()?.uppercase().orEmpty()
                if (letter != "F" && GradeHistory.isGradedLetter(letter)) {
                    passed += EmbeddedLinkCode.baseCode(course.code)
                }
            }
        }
        allGrades?.allSemesters?.values?.forEach { result ->
            result?.grades.orEmpty().forEach { grade ->
                val letter = grade.grade.trim().uppercase()
                if (letter != "F" && GradeHistory.isGradedLetter(letter)) {
                    passed += EmbeddedLinkCode.baseCode(grade.courseCode)
                }
            }
        }
        return passed
    }

    // ── grade history ───────────────────────────────────────────────────────────

    private fun creditsByBaseCode(snapshot: DomainSnapshot, allGrades: AllGradesRes?): Map<String, Int> {
        val credits = mutableMapOf<String, Int>()
        // Read the MERGED roster: an embedded theory/lab pair's combined credits (e.g. 3 + 1 = 4)
        // are authoritative, and a raw half must not shadow them with its own smaller value.
        snapshot.academics.semesters.keys.forEach { semesterId ->
            Projections.mergedCourses(snapshot, semesterId).forEach { course ->
                val value = asInt(course.credits)
                if (value > 0) credits.putIfAbsent(EmbeddedLinkCode.baseCode(course.code), value)
            }
        }
        allGrades?.allSemesters?.values?.forEach { result ->
            result?.grades.orEmpty().forEach { grade ->
                val value = asInt(grade.credits)
                if (value > 0) credits.putIfAbsent(EmbeddedLinkCode.baseCode(grade.courseCode), value)
            }
        }
        return credits
    }

    private fun buildSemesters(snapshot: DomainSnapshot, allGrades: AllGradesRes?): List<M3EReportSemester> {
        val credits = creditsByBaseCode(snapshot, allGrades)
        val rows = GradeHistory.semesterRows(snapshot)
            .filter { it.courses.isNotEmpty() }
            .sortedByDescending { GradeHistory.semesterSortKey(it.id) }
            .map { row ->
                M3EReportSemester(
                    semesterId = row.id,
                    name = row.label,
                    gpa = row.gpa.takeIf { it > 0.0 },
                    courses = row.courses.map { course ->
                        gradeCourse(
                            code = course.courseCode,
                            title = course.courseTitle,
                            type = course.courseType,
                            credits = credits[EmbeddedLinkCode.baseCode(course.courseCode)] ?: 0,
                            grandTotal = num(course.grandTotal),
                            grade = course.grade,
                            details = course.details,
                        )
                    },
                )
            }

        // The all-grades payload can hold terms the snapshot has not ingested yet. Include them
        // so the report is the full history, but never duplicate a term the snapshot already owns.
        val known = snapshot.academics.semesters.keys + rows.map { it.semesterId }.toSet()
        val extra = allGrades?.allSemesters.orEmpty().mapNotNull { (semesterId, result) ->
            val grades = result?.grades.orEmpty().filter { it.courseCode.isNotBlank() }
            if (semesterId in known || grades.isEmpty()) return@mapNotNull null
            M3EReportSemester(
                semesterId = semesterId,
                name = GradeHistory.semesterName(semesterId),
                gpa = num(result?.gpa),
                courses = grades.map { grade ->
                    gradeCourse(
                        code = grade.courseCode,
                        title = grade.courseTitle,
                        type = grade.courseType,
                        credits = asInt(grade.credits),
                        grandTotal = num(grade.grandTotal),
                        grade = grade.grade,
                        // Canonical DTO -> domain seam; keeps each component's status/score/max.
                        details = M3EGradeComputations.gradeComponents(grade.details),
                    )
                },
            )
        }
        return (rows + extra).sortedByDescending { GradeHistory.semesterSortKey(it.semesterId) }
    }

    private fun gradeCourse(
        code: String,
        title: String,
        type: String,
        credits: Int,
        grandTotal: Double?,
        grade: String,
        details: List<GradeComponent>?,
    ): M3EReportGradeCourse = M3EReportGradeCourse(
        code = code,
        title = title,
        type = type,
        credits = credits,
        grandTotal = grandTotal,
        grade = grade,
        components = details.orEmpty().map { component ->
            M3EReportAssessment(
                title = component.name,
                status = component.status,
                scored = num(component.score) ?: 0.0,
                maximum = num(component.maxMark) ?: 0.0,
                weightagePercent = num(component.weightagePercent) ?: 0.0,
                weightedScore = num(component.weightage) ?: 0.0,
            )
        },
    )

    // ── curriculum ──────────────────────────────────────────────────────────────

    private fun buildCurriculum(
        snapshot: DomainSnapshot,
        allGrades: AllGradesRes?,
        passedCodes: Set<String>,
    ): List<M3EReportCurriculumRow> {
        val rows = mutableListOf<M3EReportCurriculumRow>()
        val cgpa = snapshot.academics.publishedCgpa ?: allGrades?.overall?.cgpa
        if (cgpa != null) {
            val earned = asInt(cgpa.creditsEarned)
            val required = asInt(cgpa.creditsRegistered)
            rows += M3EReportCurriculumRow(
                category = "Overall", basket = "CGPA summary", code = "", title = "Credits",
                credits = earned, type = "Summary",
                required = required, earned = earned, progressPct = progress(earned, required),
            )
        }
        allGrades?.overall?.curriculum.orEmpty().forEach { basket ->
            val required = asInt(basket.creditsRequired)
            val earned = asInt(basket.creditsEarned)
            rows += M3EReportCurriculumRow(
                category = "Curriculum", basket = basket.basketTitle.orEmpty(),
                code = "", title = basket.basketTitle.orEmpty(), credits = earned, type = "Basket",
                required = required, earned = earned, progressPct = progress(earned, required),
            )
        }

        val curriculum: CurriculumRes? = snapshot.academics.curriculum
        curriculum?.categories.orEmpty().forEach { category: CurriculumCategory ->
            rows += M3EReportCurriculumRow(
                category = category.name, basket = "", code = category.code,
                title = category.name, credits = category.credits, type = "Category",
                required = category.maxCredits, earned = category.credits,
                progressPct = progress(category.credits, category.maxCredits),
            )
        }
        curriculum?.details.orEmpty().forEach { detail ->
            detail.baskets.forEach { basket ->
                basket.items.forEach { item ->
                    val base = EmbeddedLinkCode.baseCode(item.code).ifBlank { item.code.trim() }
                    val earned = if (base in passedCodes) item.credits else 0
                    rows += M3EReportCurriculumRow(
                        category = detail.name, basket = basket.title, code = item.code,
                        title = item.name, credits = item.credits, type = item.type.orEmpty(),
                        required = basket.credits, earned = earned,
                        progressPct = progress(earned, basket.credits),
                    )
                }
            }
        }
        return rows
    }

    // ── academic calendar ───────────────────────────────────────────────────────

    private fun buildCalendar(
        months: List<CalendarMonth>,
        anchorDate: LocalDate,
    ): List<M3EReportCalendarMonth> = months.map { month ->
        val parsed = if (Regex("\\d{4}").containsMatchIn(month.label)) {
            parseCalendarMonth(month.label, now = anchorDate)
        } else {
            null
        }
        // ParsedMonth.monthIndex is 0-based, matching kotlinx.datetime.LocalDate's monthNumber-1.
        val key = parsed?.let { "%04d-%02d".format(it.year, it.monthIndex + 1) }
            ?: month.label.ifBlank { "unknown" }
        M3EReportCalendarMonth(
            key = key,
            label = month.label,
            days = month.days.map { day ->
                val date = parsed?.let {
                    runCatching { LocalDate(it.year, it.monthIndex + 1, day.date) }.getOrNull()
                }
                M3EReportCalendarDay(
                    date = day.date,
                    weekday = date?.let(::weekdayShort).orEmpty(),
                    events = day.events.mapNotNull { event ->
                        listOf(event.text, event.category).firstOrNull { it.isNotBlank() }
                    },
                )
            },
        )
    }

    // ── attendance & datewise register ──────────────────────────────────────────

    private fun toAttendanceCourse(row: CourseAttendance): M3EReportAttendanceCourse =
        M3EReportAttendanceCourse(
            code = row.courseCode,
            title = row.courseTitle,
            type = row.courseType,
            faculty = row.faculty,
            slotVenue = row.slotVenue.orEmpty(),
            attended = row.attendedClasses,
            total = row.totalClasses,
            percentage = if (row.totalClasses > 0) {
                M3EGradeComputations.round2(row.attendedClasses.toDouble() / row.totalClasses * 100.0)
            } else {
                0.0
            },
        )

    private data class RawLog(
        val dateRaw: String,
        val date: LocalDate?,
        val courseCode: String,
        val classLabel: String,
        val status: String,
    ) {
        /** Web `dateText`: a real parsed date rendered as `DD Mon YYYY`, else the raw cell. */
        val dateText: String
            get() = date?.let {
                "${it.dayOfMonth.toString().padStart(2, '0')} " +
                    monthLabel(it.monthNumber - 1, it.year, short = true)
            } ?: dateRaw.ifBlank { "Date not supplied" }
    }

    private data class DayCounts(
        val present: Int,
        val absent: Int,
        val od: Int,
        val other: Int,
        val logs: List<RawLog>,
    )

    private fun buildAttendanceMonths(rows: List<CourseAttendance>): List<M3EReportAttendanceMonth> {
        val logs = rows.flatMap { row ->
            row.logs.map { day ->
                RawLog(
                    dateRaw = day.date,
                    date = parseDayDate(day.date),
                    courseCode = row.courseCode,
                    classLabel = row.courseType,
                    status = day.status,
                )
            }
        }
        return logs.groupBy { log ->
            log.date?.let { "%04d-%02d".format(it.year, it.monthNumber) } ?: "unknown"
        }.entries
            .sortedByDescending { it.key }
            .map { (key, monthLogs) ->
                val byDate = LinkedHashMap<String, MutableList<RawLog>>()
                monthLogs
                    .sortedWith(
                        compareBy<RawLog> { it.date?.toEpochDays() ?: Long.MAX_VALUE }
                            .thenBy { it.courseCode },
                    )
                    .forEach { byDate.getOrPut(it.dateText) { mutableListOf() } += it }

                val counts = byDate.mapValues { (_, dayLogs) ->
                    var present = 0
                    var absent = 0
                    var od = 0
                    var other = 0
                    dayLogs.forEach { log ->
                        val status = log.status.lowercase()
                        when {
                            status.contains("present") -> present++
                            status.contains("absent") -> absent++
                            // Web attendanceByMonth: a "duty" substring or the bare "od".
                            status.contains("duty") || status == "od" -> od++
                            else -> other++
                        }
                    }
                    DayCounts(present, absent, od, other, dayLogs)
                }

                val label = monthLogs.firstOrNull()?.date?.let {
                    monthLabel(it.monthNumber - 1, it.year)
                } ?: "Date not supplied"

                M3EReportAttendanceMonth(
                    key = key,
                    label = label,
                    daysRecorded = counts.size,
                    daysWithoutAbsence = counts.values.count {
                        it.present > 0 && it.absent == 0 && it.other == 0
                    },
                    presentSessions = counts.values.sumOf { it.present },
                    absentSessions = counts.values.sumOf { it.absent },
                    odSessions = counts.values.sumOf { it.od },
                    calendar = counts.map { (dateText, c) ->
                        val date = c.logs.firstOrNull()?.date
                        val events = buildList {
                            if (c.present > 0) add("${c.present} present")
                            if (c.absent > 0) add("${c.absent} absent")
                            if (c.od > 0) add("${c.od} OD")
                        }
                        M3EReportCalendarDay(
                            date = date?.dayOfMonth ?: 0,
                            weekday = date?.let(::weekdayShort).orEmpty(),
                            events = events.ifEmpty { listOf(dateText) },
                        )
                    },
                    entries = counts.values.flatMap { c ->
                        c.logs.map { log ->
                            M3EReportAttendanceEntry(
                                date = log.dateText,
                                courseCode = log.courseCode,
                                classLabel = log.classLabel,
                                status = log.status,
                            )
                        }
                    },
                )
            }
    }

    // ── OD usage ────────────────────────────────────────────────────────────────

    private data class FlatOd(val code: String, val date: String, val hours: Int)

    private fun isOfficiallyCovered(date: String, records: List<OdRecordItem>): Boolean {
        val day = parseDayDate(date) ?: return false
        return records.any { record ->
            val parts = record.date.split(Regex("\\s+to\\s+", RegexOption.IGNORE_CASE))
            val from = parseDayDate(parts.firstOrNull().orEmpty())
            val to = parseDayDate(parts.getOrNull(1) ?: parts.firstOrNull().orEmpty())
            from != null && to != null && day >= from && day <= to
        }
    }

    private fun buildOdUsage(snapshot: DomainSnapshot, officialOd: OdDetailsRes?): M3EReportOdUsage {
        // Web-exact derivation: only "On Duty", a slot beginning with L is 2h, all rows kept.
        val flat = deriveOdDaysFromSnapshot(snapshot).flatMap { day ->
            day.entries.map { entry ->
                FlatOd(EmbeddedLinkCode.baseCode(entry.courseCode), day.date, entry.hours)
            }
        }
        // The ONLY source of official OD records is the caller's parameter. Null means "no cache
        // was supplied", which is not the same claim as "no official records exist".
        val officialRecords = officialOd?.records.orEmpty()
        val bySubject = flat.groupBy { it.code }
        val perSubject = bySubject.map { (code, entries) ->
            val dates = entries.map { it.date }.toSet()
            val matchedDates = dates.count { isOfficiallyCovered(it, officialRecords) }
            val claimed = entries.sumOf { it.hours }
            // A matching official record is the only real evidence an OD period was valid.
            // With no official payload supplied, validity is unknown — never claimed == valid.
            val matchedHours = entries.filter { isOfficiallyCovered(it.date, officialRecords) }
                .sumOf { it.hours }
            M3EReportOdSubject(
                code = code,
                claimedHours = claimed,
                validHours = if (officialOd == null) null else matchedHours,
                // No wasted/recovered tracker reaches this function; unknown, not zero.
                wastedHours = null,
                officialMatchPct = progress(matchedDates, dates.size),
            )
        }.sortedBy { it.code }

        return M3EReportOdUsage(
            claimedHours = perSubject.sumOf { it.claimedHours },
            // Null when no component is known; otherwise the sum of the known ones.
            validHours = if (officialOd == null) null else perSubject.sumOf { it.validHours ?: 0 },
            wastedHours = null,
            perSubject = perSubject,
            official = officialRecords.map { record ->
                M3EReportOfficialOd(
                    date = record.date,
                    type = record.type,
                    reason = record.reason,
                    basis = record.basis,
                    time = record.time,
                    remarks = record.remarks,
                )
            },
        )
    }

    // ── exams ───────────────────────────────────────────────────────────────────

    private fun buildExams(snapshot: DomainSnapshot): List<M3EReportExam> =
        snapshot.schedule.exams
            .sortedBy { it.date }
            .map { exam ->
                val rawSeries = exam.session.ifBlank { exam.slot }
                M3EReportExam(
                    series = if (rawSeries.isBlank()) "" else CalendarClassifier.prettySeriesName(rawSeries),
                    courseCode = exam.courseCode,
                    courseTitle = exam.courseTitle,
                    date = exam.date,
                    session = exam.session,
                    time = exam.reportingTime.ifBlank { exam.time },
                    venue = exam.venue,
                    seat = listOf(exam.seatLocation, exam.seatNo)
                        .filter { it.isNotBlank() }.joinToString(" / "),
                )
            }

    // ── subject dossiers ────────────────────────────────────────────────────────

    private class SubjectAccum(
        val code: String,
        var title: String,
        var type: String,
        var credits: Int,
        var classId: String,
        var faculty: String,
        var slot: String,
        var venue: String,
        var attended: Int,
        var total: Int,
        val assessments: MutableList<Assessment> = mutableListOf(),
        var publishedGrade: String? = null,
        /**
         * D-189: the merged [credits] is the pair's TOTAL (e.g. 3 + 1 = 4). The predictor needs
         * each component's OWN credits, read from [Course.halves] when the accumulator is built.
         * Null means the half published no credit figure — never invent one for it.
         */
        var theoryCredits: Double? = null,
        var labCredits: Double? = null,
        var isEmbeddedMerged: Boolean = false,
    )

    private fun buildSubjects(
        snapshot: DomainSnapshot,
        allGrades: AllGradesRes?,
        odRows: List<M3EReportOdSubject>,
    ): List<M3EReportSubject> {
        val accs = LinkedHashMap<String, SubjectAccum>()
        // Subject dossiers cover the reporting semester only — the same semester the attendance
        // register uses. Iterating every semester would turn a past grade-only course into an
        // empty dossier with fabricated zero attendance, which the report must never claim.
        val semester = Projections.reportingSemester(snapshot)
        (semester?.let { Projections.mergedCourses(snapshot, it.id) }.orEmpty())
            .forEach { course ->
                val code = EmbeddedLinkCode.baseCode(course.code).ifBlank { course.code.trim() }
                if (code.isBlank()) return@forEach
                val acc = accs.getOrPut(code) {
                    SubjectAccum(
                        code = code,
                        title = course.title,
                        type = course.type,
                        credits = asInt(course.credits),
                        classId = course.classId.orEmpty(),
                        faculty = course.faculty.orEmpty(),
                        slot = course.slots.joinToString("+"),
                        venue = course.venue.orEmpty(),
                        attended = 0,
                        total = 0,
                    )
                }
                if (acc.title.isBlank()) acc.title = course.title
                if (acc.type.isBlank()) acc.type = course.type
                if (acc.credits == 0) acc.credits = asInt(course.credits)
                if (acc.classId.isBlank()) acc.classId = course.classId.orEmpty()
                if (acc.faculty.isBlank()) acc.faculty = course.faculty.orEmpty()
                if (acc.slot.isBlank()) acc.slot = course.slots.joinToString("+")
                if (acc.venue.isBlank()) acc.venue = course.venue.orEmpty()
                // D-189: capture the REAL per-half credits before the pair is reduced to a total.
                // [Course.halves] is populated by [EmbeddedLinkMerge] for an embedded pair; each
                // half keeps its original credit string, which is what the web predictor weights.
                if (course.isEmbeddedMerged) acc.isEmbeddedMerged = true
                course.halves.forEach { half ->
                    val credit = half.credits?.let(::num)?.takeIf { it > 0.0 } ?: return@forEach
                    if (halfIsLab(half)) {
                        if (acc.labCredits == null) acc.labCredits = credit
                    } else if (acc.theoryCredits == null) {
                        acc.theoryCredits = credit
                    }
                }
                acc.attended += course.attendance?.attended ?: 0
                acc.total += course.attendance?.total ?: 0
                course.marks?.assessments?.let { acc.assessments.addAll(it) }
                course.grade?.letter?.takeIf { it.isNotBlank() }?.let { acc.publishedGrade = it }
            }

        // Published grades enrich an existing dossier; they never invent a subject with no data.
        allGrades?.allSemesters?.values?.forEach { result ->
            result?.grades.orEmpty().forEach { grade ->
                val code = EmbeddedLinkCode.baseCode(grade.courseCode)
                val acc = accs[code] ?: return@forEach
                if (acc.title.isBlank()) acc.title = grade.courseTitle
                if (acc.type.isBlank()) acc.type = grade.courseType
                if (acc.credits == 0) acc.credits = asInt(grade.credits)
                if (acc.publishedGrade == null) {
                    acc.publishedGrade = grade.grade.takeIf { it.isNotBlank() }
                }
            }
        }

        val odByCode = odRows.associateBy { it.code }
        return accs.values.sortedBy { it.code }.map { acc ->
            val assessments = acc.assessments.map { assessment ->
                M3EReportAssessment(
                    title = assessment.name,
                    status = assessment.status,
                    scored = num(assessment.score) ?: 0.0,
                    maximum = num(assessment.maxMark) ?: 0.0,
                    weightagePercent = num(assessment.weightagePercent) ?: num(assessment.weightage) ?: 0.0,
                    weightedScore = num(assessment.weightage) ?: 0.0,
                )
            }
            val usable = acc.assessments.mapNotNull { assessment ->
                val max = num(assessment.maxMark)
                val score = num(assessment.score)
                if (max != null && max > 0.0 && score != null) score to max else null
            }
            val assessmentAverage = if (usable.isEmpty()) {
                null
            } else {
                M3EGradeComputations.round2(
                    usable.sumOf { (score, max) -> score / max * 100.0 } / usable.size,
                )
            }

            M3EReportSubject(
                code = acc.code,
                title = acc.title,
                type = acc.type,
                credits = acc.credits,
                classId = acc.classId,
                faculty = acc.faculty,
                department = acc.faculty.substringAfterLast(" - ", "").takeIf { acc.faculty.contains(" - ") }.orEmpty(),
                slot = acc.slot,
                venue = acc.venue,
                attendance = M3EReportAttendanceCourse(
                    code = acc.code,
                    title = acc.title,
                    type = acc.type,
                    faculty = acc.faculty,
                    slotVenue = acc.venue,
                    attended = acc.attended,
                    total = acc.total,
                    percentage = if (acc.total > 0) {
                        M3EGradeComputations.round2(acc.attended.toDouble() / acc.total * 100.0)
                    } else {
                        0.0
                    },
                ),
                publishedGrade = acc.publishedGrade,
                assessmentAverage = assessmentAverage,
                assessments = assessments,
                od = odByCode[acc.code],
                prediction = predictionFor(acc),
            )
        }
    }

    /**
     * Which side of an embedded pair a stored half belongs to. The merge already classified it
     * into [EmbeddedCourseHalf.half]; fall back to the one canonical classifier when a caller
     * supplied the halves itself without that marker.
     */
    private fun halfIsLab(half: EmbeddedCourseHalf): Boolean =
        when (half.half.trim().uppercase()) {
            "L" -> true
            "T" -> false
            else -> EmbeddedLinkCode.componentOf(
                rawCode = half.code,
                typeHint = half.type,
                slot = half.slots.joinToString("+"),
                title = half.title,
            ).isLab
        }

    /** Real marks only: no usable assessment numbers means no prediction, never a guessed grade. */
    private fun predictionFor(acc: SubjectAccum): M3EReportPrediction? {
        val predictors = acc.assessments.mapNotNull { assessment ->
            val max = num(assessment.maxMark) ?: return@mapNotNull null
            if (max <= 0.0) return@mapNotNull null
            val weight = num(assessment.weightagePercent) ?: num(assessment.weightage) ?: return@mapNotNull null
            if (weight <= 0.0) return@mapNotNull null
            val component = when {
                assessment.component?.contains("lab", ignoreCase = true) == true ||
                    assessment.component?.contains("ELA", ignoreCase = true) == true -> PredictorComponent.LAB
                else -> PredictorComponent.THEORY
            }
            PredictorAssessment(
                id = assessment.name,
                title = assessment.name,
                maxMark = max,
                weightagePercent = weight,
                scoredMark = num(assessment.score),
                status = assessment.status,
                component = component,
            )
        }
        if (predictors.isEmpty()) return null

        val hasTheory = predictors.any { it.component == PredictorComponent.THEORY }
        val hasLab = predictors.any { it.component == PredictorComponent.LAB }
        val isEmbedded = acc.isEmbeddedMerged || (hasTheory && hasLab) ||
            EmbeddedLinkCode.componentOf(acc.code, acc.type).isEmbedded

        val theoryCredits: Double
        val labCredits: Double
        if (isEmbedded) {
            // D-189: an embedded pair is weighted with EACH HALF'S OWN credits — 3+1, 2+2, 2+1,
            // 1+1 all get their real split. A missing half credit is UNKNOWN, not 1.0 and not the
            // merged total, so skip the prediction rather than fabricate a weighted grade.
            theoryCredits = acc.theoryCredits?.takeIf { it > 0.0 } ?: return null
            labCredits = acc.labCredits?.takeIf { it > 0.0 } ?: return null
        } else {
            // A single component: the engine weights every present component at 1 regardless of
            // the figures below, so these numbers cannot change the verdict. Keep the prior
            // mapping for continuity instead of inventing a new rule for a course with no halves.
            theoryCredits = acc.credits.coerceAtLeast(1).toDouble()
            labCredits = 1.0
        }

        val result = computeCoursePrediction(
            courseCode = acc.code,
            courseTitle = acc.title,
            courseType = acc.type,
            theoryAssessments = predictors.filter { it.component == PredictorComponent.THEORY },
            labAssessments = predictors.filter { it.component == PredictorComponent.LAB },
            theoryCredits = theoryCredits,
            labCredits = labCredits,
            // No grading system or cohort curve reaches this snapshot; the canonical engine then
            // uses the absolute ladder. Class average/size stay null/0 — never invented.
            courseSystem = null,
            classStats = null,
        )
        return M3EReportPrediction(
            currentPace = result.optimalGrade?.letter.orEmpty(),
            bestGrade = result.maximisableGrade.letter,
            currentStandingPct = result.currentStandingPct,
            maxPossiblePct = result.maxPossibleScaled,
            classAveragePct = null,
            classSize = 0,
        )
    }

    // ── events, finance, services ───────────────────────────────────────────────

    private fun buildEvents(snapshot: DomainSnapshot): List<M3EReportEvent> {
        val registered = snapshot.engagement.registeredEvents?.events.orEmpty().map { event ->
            M3EReportEvent(
                name = event.title.ifBlank { event.eid },
                date = event.date.orEmpty(),
                time = event.time.orEmpty(),
                venue = event.location,
                paymentStatus = event.paymentStatus.orEmpty(),
            )
        }
        if (registered.isNotEmpty()) return registered
        return snapshot.engagement.events?.events.orEmpty().map { event ->
            M3EReportEvent(
                name = event.title.ifBlank { event.eid },
                date = event.date,
                time = event.time.orEmpty(),
                venue = event.location,
                paymentStatus = "",
            )
        }
    }

    private fun buildFinance(snapshot: DomainSnapshot): M3EReportFinance {
        val payments = snapshot.campus.payments
        return M3EReportFinance(
            hasDues = payments?.dues?.hasDues ?: false,
            duesMessage = payments?.dues?.message.orEmpty(),
            receipts = payments?.receipts.orEmpty().map { receipt ->
                M3EReportReceipt(
                    number = receipt.receiptNumber,
                    date = receipt.date.orEmpty(),
                    amount = receipt.amount.orEmpty(),
                )
            },
            wallet = payments?.ledgerINR.orEmpty().map { entry ->
                M3EReportWalletEntry(
                    date = entry.transactionDate.orEmpty(),
                    receiptNumber = entry.receiptNumber.orEmpty(),
                    type = entry.transactionType.orEmpty(),
                    amount = entry.amount.orEmpty(),
                    refundAmount = entry.refundAmount.orEmpty(),
                    balance = entry.bookBalanceAmount.orEmpty(),
                )
            },
        )
    }

    /** Drop secrets and empty cells from a key/value dump before it reaches a report. */
    private fun sanitisedPairs(vararg pairs: Pair<String, String?>): List<Pair<String, String>> =
        pairs.mapNotNull { (key, value) ->
            val lowered = key.lowercase()
            if (lowered.contains("csrf") || lowered.contains("token") || lowered.contains("password")) {
                return@mapNotNull null
            }
            val clean = value?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            key to clean
        }

    private fun buildServices(snapshot: DomainSnapshot): M3EReportServices {
        val transport = snapshot.campus.transport
        val transportRows = if (transport == null) {
            emptyList()
        } else {
            sanitisedPairs(
                "Registration" to transport.hasRegistration.toString(),
                "Register number" to transport.registerNumber,
                "Name" to transport.name,
                "Branch" to transport.branch,
                "Programme" to transport.programme,
                "FP reference" to transport.fpReference,
                "Payment status" to transport.paymentStatus,
                "Route selected" to transport.routeSelected,
                "Bus route id" to transport.busRouteId,
                "Bus tracking" to transport.busUrl,
                // pageCsrf is deliberately not passed in.
            )
        }

        val library = snapshot.campus.library
        val libraryRows = buildList {
            addAll(
                sanitisedPairs(
                    "Books issued" to library?.effectiveBooks?.size?.toString(),
                    "Total" to library?.total?.takeIf { it > 0 }?.toString(),
                ),
            )
            library?.effectiveBooks.orEmpty().forEachIndexed { index, book ->
                addAll(
                    sanitisedPairs(
                        "Book ${index + 1}" to listOf(
                            book.title, book.author, book.dueDate, book.fineAmount,
                        ).filter { !it.isNullOrBlank() }.joinToString(" · ").takeIf { it.isNotBlank() },
                    ),
                )
            }
            library?.libraryDues?.keyValuePairs.orEmpty().forEach { (key, value) ->
                addAll(sanitisedPairs(key to value))
            }
        }

        val moodleRows = snapshot.services.moodle?.data.orEmpty().let { assignments ->
            if (assignments.isNotEmpty()) {
                assignments.map { item ->
                    M3EReportMoodleItem(
                        course = item.courseTitle.ifBlank { item.courseCode },
                        title = item.taskTitle.ifBlank { item.name },
                        dueDate = item.due,
                        status = if (item.done) "Done" else "Pending",
                    )
                }
            } else {
                snapshot.services.lms?.assignments.orEmpty().map { item ->
                    M3EReportMoodleItem(
                        course = item.readableCourseTitle.ifBlank { item.courseCode },
                        title = item.assignmentTitle.ifBlank { item.name },
                        dueDate = item.due,
                        status = if (item.done) "Done" else "Pending",
                    )
                }
            }
        }

        val hostelInfo = snapshot.campus.hostel?.hostelInfo
        val hostelRows = buildList {
            addAll(
                sanitisedPairs(
                    "Block" to hostelInfo?.blockName,
                    "Room" to hostelInfo?.roomNo,
                    "Mess" to hostelInfo?.messInfo,
                    "Gender" to hostelInfo?.gender,
                    "Residence" to hostelInfo?.isHosteller?.let { if (it) "Hosteller" else "Dayscholar" },
                    "Leave records" to snapshot.campus.hostel?.leaveHistory?.size?.takeIf { it > 0 }?.toString(),
                ),
            )
        }

        return M3EReportServices(
            transport = transportRows,
            library = libraryRows,
            moodle = moodleRows,
            hostel = hostelRows,
        )
    }
}
