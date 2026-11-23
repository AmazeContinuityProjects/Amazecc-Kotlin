package com.amazecc.app.shared.domain

import com.amazecc.app.shared.model.AllGradesRes
import com.amazecc.app.shared.model.BookItem
import com.amazecc.app.shared.model.CategoryDetail
import com.amazecc.app.shared.model.CgpaSummary
import com.amazecc.app.shared.model.CurriculumBasket
import com.amazecc.app.shared.model.CurriculumBasketItem
import com.amazecc.app.shared.model.CurriculumCategory
import com.amazecc.app.shared.model.CurriculumRes
import com.amazecc.app.shared.model.DuesSection
import com.amazecc.app.shared.model.EventHubRegisteredEvent
import com.amazecc.app.shared.model.EventHubRegisteredEventsRes
import com.amazecc.app.shared.model.GradeCurriculumBasket
import com.amazecc.app.shared.model.GradeItem
import com.amazecc.app.shared.model.LedgerEntry
import com.amazecc.app.shared.model.LibraryRes
import com.amazecc.app.shared.model.MoodleAssignment
import com.amazecc.app.shared.model.MoodleRes
import com.amazecc.app.shared.model.OdDetailsRes
import com.amazecc.app.shared.model.OdRecordItem
import com.amazecc.app.shared.model.OverallGrades
import com.amazecc.app.shared.model.PaymentsRes
import com.amazecc.app.shared.model.ReceiptItem
import com.amazecc.app.shared.model.SemesterGradeResult
import com.amazecc.app.shared.model.StudentIdentity
import com.amazecc.app.shared.model.Official
import com.amazecc.app.shared.model.TransportDataRes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * D-151 / critic-fix regression tests.
 *
 * Every input is a hand-built but VTOP-shaped value: no Clock, no store, no network. The
 * assembler must be a pure shape mapping over these values, and must never turn an absent source
 * into a plausible-looking number.
 */
class ReportDataAssemblerTest {

    private companion object {
        const val GENERATED_AT = 1_762_700_000_000L // fixed: 2026-ish, never Clock.now()
        const val CURRENT = "CH20252601"
        const val PAST = "CH20252501"
        const val ALL_GRADES_ONLY = "CH20242505"
    }

    private data class Fixture(
        val snapshot: DomainSnapshot,
        val allGrades: AllGradesRes,
        val identity: StudentIdentity,
    )

    private fun fixture(): Fixture {
        val theoryLog = AttendanceDay("2026-10-05", "On Duty")
        val labLog = AttendanceDay("2026-10-05", "On Duty")

        val current = Semester(
            id = CURRENT,
            name = "Fall 2025-26",
            gpa = "8.5",
            courses = listOf(
                Course(
                    code = "CSE1001",
                    title = "Database Systems",
                    type = "Embedded Theory",
                    credits = "3",
                    classId = "1001",
                    faculty = "Dr Ada - CSE",
                    slots = listOf("A1"),
                    attendance = Attendance(attended = 8, total = 10, percentage = "80", logs = listOf(theoryLog)),
                    marks = Marks(
                        assessments = listOf(
                            Assessment(
                                name = "CAT 1", score = "18", maxMark = "20", weightage = "9",
                                weightagePercent = "15", status = "Present", component = "ETH",
                            ),
                        ),
                    ),
                    grade = Grade(
                        letter = "S", grandTotal = "92",
                        details = listOf(
                            GradeComponent(
                                type = "Theory", name = "CAT 1", score = "18", maxMark = "20",
                                weightagePercent = "15", status = "Present", weightage = "9",
                            ),
                        ),
                    ),
                ),
                Course(
                    code = "CSE1001(L)",
                    title = "Database Systems Lab",
                    type = "Embedded Lab",
                    credits = "1",
                    classId = "1002",
                    slots = listOf("L31"),
                    attendance = Attendance(attended = 4, total = 5, percentage = "80", logs = listOf(labLog)),
                    marks = Marks(
                        assessments = listOf(
                            Assessment(
                                name = "Lab Record", score = "9", maxMark = "10", weightage = "4.5",
                                weightagePercent = "5", status = "Present", component = "ELA",
                            ),
                        ),
                    ),
                ),
                Course(
                    code = "MAT1001",
                    title = "Mathematics",
                    type = "Theory Only",
                    credits = "4",
                    faculty = "Dr Noether - MATH",
                    slots = listOf("B1"),
                    attendance = Attendance(
                        attended = 9, total = 10, percentage = "90",
                        logs = listOf(
                            AttendanceDay("2026-10-06", "Present"),
                            AttendanceDay("2026-10-07", "Absent"),
                        ),
                    ),
                    marks = Marks(
                        assessments = listOf(
                            Assessment(
                                name = "CAT 1", score = "16", maxMark = "20", weightage = "8",
                                weightagePercent = "15", status = "Present",
                            ),
                        ),
                    ),
                    grade = Grade(letter = "A", grandTotal = "85"),
                ),
            ).associateBy { it.code },
        )

        val past = Semester(
            id = PAST,
            name = "Fall 2024-25",
            gpa = "5.0",
            // No credits published: this F must not inflate the canonical earned-credits sum.
            courses = mapOf(
                "PHY1001" to Course(
                    code = "PHY1001", title = "Physics", type = "Theory Only",
                    grade = Grade(letter = "F", grandTotal = "40"),
                ),
            ),
        )

        val snapshot = DomainSnapshot(
            academics = Academics(
                semesters = mapOf(CURRENT to current, PAST to past),
                selectedSemesterId = CURRENT,
                publishedCgpa = CgpaSummary(
                    creditsRegistered = "20", creditsEarned = "12", cgpa = "8.2",
                ),
                curriculum = CurriculumRes(
                    success = true,
                    title = "B.Tech Computer Science",
                    totalCredits = 20,
                    categories = listOf(
                        CurriculumCategory(code = "PC", name = "Program Core", credits = 8, maxCredits = 12),
                    ),
                    details = listOf(
                        CategoryDetail(
                            code = "PC", name = "Program Core",
                            baskets = listOf(
                                CurriculumBasket(
                                    title = "Theory", credits = 12,
                                    items = listOf(
                                        CurriculumBasketItem(
                                            code = "CSE1001", name = "Database Systems", credits = 4,
                                            type = "Embedded Theory and Lab",
                                        ),
                                        CurriculumBasketItem(
                                            code = "MAT1001", name = "Mathematics", credits = 4,
                                            type = "Theory Only",
                                        ),
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
            schedule = Schedule(
                exams = listOf(
                    Exam(
                        courseCode = "CSE1001", courseTitle = "Database Systems",
                        date = "2026-11-10", session = "CAT II", reportingTime = "09:00 AM",
                        time = "09:00 AM - 12:00 PM", venue = "SJT-301",
                        seatLocation = "R1", seatNo = "12", semesterId = CURRENT,
                    ),
                ),
                calendar = listOf(
                    CalendarMonth(
                        label = "October 2026",
                        days = listOf(
                            CalendarDay(
                                date = 5,
                                events = listOf(
                                    CalendarEventItem(type = "holiday", text = "Gandhi Jayanthi", category = "Holiday"),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
            campus = Campus(
                payments = PaymentsRes(
                    dues = DuesSection(hasDues = true, message = "Outstanding fee"),
                    receipts = listOf(ReceiptItem(receiptNumber = "R1", date = "2026-09-01", amount = "1000")),
                    ledgerINR = listOf(
                        LedgerEntry(
                            transactionDate = "2026-09-01", receiptNumber = "R1",
                            transactionType = "Credit", amount = "1000", refundAmount = "0",
                            bookBalanceAmount = "1000",
                        ),
                    ),
                ),
                library = LibraryRes(
                    booksIssued = listOf(BookItem(bookId = "B1", title = "Algorithms", fineAmount = "0")),
                ),
                transport = TransportDataRes(
                    registerNumber = "21BCE1001", name = "Aarav Sharma", routeSelected = "Route 5",
                    pageCsrf = "SECRET-CSRF",
                ),
            ),
            engagement = Engagement(
                registeredEvents = EventHubRegisteredEventsRes(
                    events = listOf(
                        EventHubRegisteredEvent(
                            eid = "E1", title = "Hackathon", location = "Auditorium",
                            date = "2026-10-20", time = "10:00", paymentStatus = "Paid",
                        ),
                    ),
                ),
            ),
            services = Services(
                moodle = MoodleRes(
                    data = listOf(
                        MoodleAssignment(
                            name = "CSE1001/Database Systems/Digital Assignment 1",
                            due = "Monday, 5 October 2026",
                        ),
                    ),
                ),
            ),
        )

        val allGrades = AllGradesRes(
            semesters = mapOf(
                // Already in the snapshot: must not produce a duplicate semester row.
                PAST to SemesterGradeResult(
                    gpa = "5.0",
                    grades = listOf(GradeItem(courseCode = "PHY1001", courseTitle = "Physics", grade = "F")),
                ),
                // Only in all-grades: full history must still include it, current-first.
                ALL_GRADES_ONLY to SemesterGradeResult(
                    gpa = "4.0",
                    grades = listOf(GradeItem(courseCode = "CHE1001", courseTitle = "Chemistry", grade = "F")),
                ),
            ),
            overall = OverallGrades(
                cgpa = CgpaSummary(creditsRegistered = "20", creditsEarned = "12", cgpa = "8.2"),
                curriculum = listOf(
                    GradeCurriculumBasket("Program Core", "12", "8"),
                    // A zero requirement must yield 0.0, never a divide-by-zero NaN/Infinity.
                    GradeCurriculumBasket("Elective", "0", "0"),
                ),
            ),
        )

        val identity = StudentIdentity(
            regNo = "21BCE1001", name = "Aarav Sharma", program = "B.Tech CSE",
            school = "SCOPE", campus = "Chennai",
            proctor = Official(name = "Dr Rao", email = "rao@vit.ac.in"),
        )
        return Fixture(snapshot, allGrades, identity)
    }

    private fun assemble(
        f: Fixture = fixture(),
        officialOd: OdDetailsRes? = null,
    ) = ReportDataAssembler.assemble(
        snapshot = f.snapshot,
        allGrades = f.allGrades,
        identity = f.identity,
        generatedAt = GENERATED_AT,
        officialOd = officialOd,
    )

    // ── summary / canonical formulas ─────────────────────────────────────────────

    @Test
    fun summaryUsesCanonicalAttendanceAndCreditsNotHandRolledSums() {
        val report = assemble()
        val s = report.summary

        // Unweighted sum of VTOP attended/total across merged courses: 12/15 + 9/10 = 21/25.
        // Averaging the two printed percentages would have given 85.0 — that is not parity.
        assertEquals(84.0, s.attendancePct, 0.0001)
        assertEquals(21, s.classesAttended)
        assertEquals(25, s.classesTotal)
        assertEquals(4, s.classesMissed)

        // Earned credits come from Projections.creditsEarned over graded merged courses (4 + 4),
        // NOT the CgpaSummary convenience text (12) and NOT the F course (no published credits).
        assertEquals(8, s.creditsEarned)
        assertEquals(20, s.creditsRegistered)
        assertEquals(8.2, s.cgpa, 0.0001)
        assertEquals(8.5, s.currentGpa!!, 0.0001)

        // Distinct real log dates: 05 (OD), 06 (Present), 07 (Absent). Only 06 is absence-free.
        assertEquals(3, s.daysRecorded)
        assertEquals(1, s.daysWithoutAbsence)

        // F grades from the snapshot AND from the all-grades-only semester count as arrears.
        assertEquals(2, s.arrearsCount)
    }

    @Test
    fun identityComesFromTheRealWhoamiFields() {
        val report = assemble()
        assertEquals("Aarav Sharma", report.identity.name)
        assertEquals("21BCE1001", report.identity.registrationNumber)
        assertEquals("B.Tech CSE", report.identity.programme)
        assertEquals("SCOPE", report.identity.school)
        assertEquals("Chennai", report.identity.campus)
        assertEquals("Dr Rao", report.identity.proctorName)
        assertEquals("rao@vit.ac.in", report.identity.proctorEmail)
        assertEquals("Fall 2025-26", report.identity.semesterName)
    }

    // ── derived sections ─────────────────────────────────────────────────────────

    @Test
    fun semestersAreNotEmptyCurrentFirstAndNeverDuplicated() {
        val report = assemble()
        assertEquals(
            listOf(CURRENT, PAST, ALL_GRADES_ONLY),
            report.semesters.map { it.semesterId },
        )
        val current = report.semesters.first()
        assertEquals("Fall 2025-26", current.name)
        assertEquals(8.5, current.gpa!!, 0.0001)
        assertEquals(setOf("CSE1001", "MAT1001"), current.courses.map { it.code }.toSet())
        val embedded = current.courses.single { it.code == "CSE1001" }
        assertEquals(4, embedded.credits)
        assertEquals(92.0, embedded.grandTotal!!, 0.0001)
        assertEquals("S", embedded.grade)
        assertEquals("CAT 1", embedded.components.single().title)
        assertEquals(18.0, embedded.components.single().scored, 0.0001)
        assertEquals(20.0, embedded.components.single().maximum, 0.0001)
        assertEquals("Present", embedded.components.single().status)
        // A published F from the all-grades-only term stays in the history.
        assertEquals("F", report.semesters.last().courses.single().grade)
    }

    @Test
    fun curriculumFlattensEverySourceAndGuardsZeroRequirement() {
        val rows = assemble().curriculum
        assertEquals(6, rows.size)
        val overall = rows.first { it.category == "Overall" }
        assertEquals(12, overall.earned)
        assertEquals(20, overall.required)
        assertEquals(60.0, overall.progressPct, 0.0001)

        val zero = rows.single { it.basket == "Elective" }
        assertEquals(0, zero.required)
        assertEquals(0.0, zero.progressPct, 0.0001)
        assertTrue(zero.progressPct.isFinite())

        val basket = rows.single { it.basket == "Program Core" && it.category == "Curriculum" }
        assertEquals(12, basket.required)
        assertEquals(8, basket.earned)
        assertEquals(66.67, basket.progressPct, 0.0001)

        val item = rows.single { it.code == "CSE1001" }
        assertEquals(4, item.earned) // real pass in the grade history
        assertEquals(12, item.required)
        assertEquals(33.33, item.progressPct, 0.0001)
    }

    @Test
    fun calendarAttendanceMonthsExamsAndSubjectsUseOnlyFixtureRows() {
        val report = assemble()

        val month = report.calendar.single()
        assertEquals("2026-10", month.key)
        assertEquals("October 2026", month.label)
        val day = month.days.single()
        assertEquals(5, day.date)
        assertEquals("Mon", day.weekday) // 2026-10-05 is a Monday
        assertEquals(listOf("Gandhi Jayanthi"), day.events)

        val register = report.attendance
        assertEquals(2, register.courses.size)
        assertEquals(12, register.courses.single { it.code == "CSE1001" }.attended)
        assertEquals(15, register.courses.single { it.code == "CSE1001" }.total)
        assertEquals(80.0, register.courses.single { it.code == "CSE1001" }.percentage, 0.0001)
        val october = register.months.single()
        assertEquals("2026-10", october.key)
        assertEquals(3, october.daysRecorded)
        assertEquals(1, october.daysWithoutAbsence)
        assertEquals(1, october.presentSessions)
        assertEquals(1, october.absentSessions)
        assertEquals(1, october.odSessions)
        assertEquals(3, october.entries.size)

        val exam = report.exams.single()
        assertEquals("CAT II", exam.series)
        assertEquals("CSE1001", exam.courseCode)
        assertEquals("2026-11-10", exam.date)
        assertEquals("09:00 AM", exam.time)
        assertEquals("SJT-301", exam.venue)
        assertEquals("R1 / 12", exam.seat)

        // Two embedded halves become ONE dossier (not three subjects), carrying both assessments.
        assertEquals(2, report.subjects.size)
        val embedded = report.subjects.single { it.code == "CSE1001" }
        assertEquals(12, embedded.attendance.attended)
        assertEquals(15, embedded.attendance.total)
        assertEquals(2, embedded.assessments.size)
        assertEquals("S", embedded.publishedGrade)
        // 18/20 and 9/10 are both 90% — the mean of real ratios, never a weight mix-up.
        assertEquals(90.0, embedded.assessmentAverage!!, 0.0001)
        assertNotNull(embedded.prediction)
        val maths = report.subjects.single { it.code == "MAT1001" }
        assertEquals("A", maths.publishedGrade)
        assertEquals(1, maths.assessments.size)
    }

    @Test
    fun eventsFinanceAndServicesAreMappedFromRealDtos() {
        val report = assemble()
        val event = report.events.single()
        assertEquals("Hackathon", event.name)
        assertEquals("Paid", event.paymentStatus)
        assertEquals("Auditorium", event.venue)

        assertTrue(report.finance.hasDues)
        assertEquals("Outstanding fee", report.finance.duesMessage)
        assertEquals("R1", report.finance.receipts.single().number)
        assertEquals("1000", report.finance.receipts.single().amount)
        assertEquals("Credit", report.finance.wallet.single().type)

        assertTrue(report.services.transport.any { it.first == "Register number" && it.second == "21BCE1001" })
        assertFalse(report.services.transport.any { it.first.contains("csrf", ignoreCase = true) })
        assertFalse(report.services.transport.any { it.second.contains("SECRET") })
        assertEquals("1", report.services.library.single { it.first == "Books issued" }.second)
        assertEquals(1, report.services.moodle.size)
        assertEquals("Database Systems", report.services.moodle.single().course)
    }

    // ── OD honesty ───────────────────────────────────────────────────────────────

    @Test
    fun withoutOfficialOdAllValidAndWastedHoursAreUnknown() {
        val report = assemble(officialOd = null)

        assertTrue(report.odUsage.official.isEmpty())
        assertNull(report.odUsage.validHours)
        assertNull(report.odUsage.wastedHours)

        // Claims are still real (two halves, same date: 1h theory + 2h lab).
        assertEquals(3, report.odUsage.claimedHours)
        val od = report.odUsage.perSubject.single()
        assertEquals("CSE1001", od.code)
        assertEquals(3, od.claimedHours)
        assertNull(od.validHours)
        assertNull(od.wastedHours)
        assertNull(report.subjects.single { it.code == "CSE1001" }.od!!.validHours)
    }

    @Test
    fun officialOdMakesValidHoursKnownAndOnlyMatchingDatesCount() {
        val official = OdDetailsRes(
            success = true,
            records = listOf(
                OdRecordItem(
                    date = "2026-10-05", type = "On Duty", reason = "Seminar",
                    basis = "Conference", time = "09:00 - 11:00", remarks = "Approved",
                ),
            ),
        )
        val matched = assemble(officialOd = official)
        assertEquals(1, matched.odUsage.official.size)
        assertEquals("Seminar", matched.odUsage.official.single().reason)
        assertEquals(3, matched.odUsage.claimedHours)
        assertEquals(3, matched.odUsage.validHours)
        assertNull(matched.odUsage.wastedHours)
        val od = matched.odUsage.perSubject.single()
        assertEquals(3, od.validHours)
        assertNull(od.wastedHours)
        assertEquals(100.0, od.officialMatchPct, 0.0001)

        // Supplied cache with no matching date means a KNOWN zero, not an unknown null.
        val unmatched = assemble(
            officialOd = official.copy(
                records = listOf(OdRecordItem(date = "2026-10-09", type = "On Duty", reason = "Other")),
            ),
        )
        assertEquals(0, unmatched.odUsage.validHours)
        assertEquals(0.0, unmatched.odUsage.perSubject.single().officialMatchPct, 0.0001)
        assertNull(unmatched.odUsage.wastedHours)
    }

    @Test
    fun odRulesUseExactStatusRawSlotLabHoursAndBothHalvesOnOneDate() {
        val snapshot = DomainSnapshot(
            academics = Academics(
                semesters = mapOf(
                    "S" to Semester(
                        id = "S",
                        courses = listOf(
                            Course(
                                code = "T1", title = "Theory", type = "Theory Only", slots = listOf("A1"),
                                attendance = Attendance(
                                    logs = listOf(
                                        AttendanceDay("2026-10-05", "On Duty"),
                                        AttendanceDay("2026-10-06", "on duty"), // wrong case: not OD
                                        AttendanceDay("2026-10-07", "Partial OD"), // half day: not OD
                                    ),
                                ),
                            ),
                            Course(
                                code = "T1(L)", title = "Lab", type = "Lab Only", slots = listOf("L31"),
                                attendance = Attendance(logs = listOf(AttendanceDay("2026-10-05", "On Duty"))),
                            ),
                        ).associateBy { it.code },
                    ),
                ),
            ),
        )

        val report = ReportDataAssembler.assemble(
            snapshot = snapshot, allGrades = null, identity = null, generatedAt = GENERATED_AT,
        )

        // Only the two exact "On Duty" logs on 05 count; the lab slot begins with L (2h).
        val od = report.odUsage.perSubject.single()
        assertEquals("T1", od.code)
        assertEquals(3, od.claimedHours)
        assertNull(od.validHours)
        assertNull(od.wastedHours)
        assertEquals(3, report.odUsage.claimedHours)

        // The canonical derivation agrees: 1h theory + 2h lab on the same date.
        val day = deriveOdDaysFromSnapshot(snapshot).single()
        assertEquals("2026-10-05", day.date)
        assertEquals(2, day.entries.size)
        assertEquals(setOf("TH" to 1, "LAB" to 2), day.entries.map { it.type to it.hours }.toSet())
    }

    // ── absent sources ───────────────────────────────────────────────────────────

    @Test
    fun emptySourcesProduceEmptyListsNullsAndZerosNotPlausibleValues() {
        val report = ReportDataAssembler.assemble(
            snapshot = DomainSnapshot(),
            allGrades = null,
            identity = null,
            generatedAt = GENERATED_AT,
        )

        assertEquals("", report.identity.name)
        assertEquals("", report.identity.registrationNumber)
        assertNull(report.identity.proctorName)

        val s = report.summary
        assertEquals(0.0, s.cgpa, 0.0001)
        assertNull(s.currentGpa)
        assertEquals(0.0, s.attendancePct, 0.0001)
        assertEquals(0, s.classesAttended)
        assertEquals(0, s.classesTotal)
        assertEquals(0, s.classesMissed)
        assertEquals(0, s.daysRecorded)
        assertEquals(0, s.daysWithoutAbsence)
        assertEquals(0, s.creditsEarned)
        assertEquals(0, s.creditsRegistered)
        assertEquals(0, s.arrearsCount)

        assertTrue(report.semesters.isEmpty())
        assertTrue(report.curriculum.isEmpty())
        assertTrue(report.calendar.isEmpty())
        assertTrue(report.exams.isEmpty())
        assertTrue(report.subjects.isEmpty())
        assertTrue(report.events.isEmpty())
        assertTrue(report.attendance.courses.isEmpty())
        assertTrue(report.attendance.months.isEmpty())
        assertTrue(report.odUsage.perSubject.isEmpty())
        assertTrue(report.odUsage.official.isEmpty())
        assertEquals(0, report.odUsage.claimedHours)
        assertNull(report.odUsage.validHours)
        assertNull(report.odUsage.wastedHours)
        assertFalse(report.finance.hasDues)
        assertTrue(report.finance.receipts.isEmpty())
        assertTrue(report.finance.wallet.isEmpty())
        assertTrue(report.services.transport.isEmpty())
        assertTrue(report.services.library.isEmpty())
        assertTrue(report.services.moodle.isEmpty())
        assertTrue(report.services.hostel.isEmpty())
    }

    @Test
    fun marksLessSubjectHasNoInventedPredictionOrAssessmentAverage() {
        val snapshot = DomainSnapshot(
            academics = Academics(
                semesters = mapOf(
                    "S" to Semester(
                        id = "S",
                        courses = mapOf(
                            "ENG1001" to Course(
                                code = "ENG1001", title = "Communication", type = "Theory Only",
                                credits = "2", attendance = Attendance(attended = 3, total = 4, percentage = "75"),
                            ),
                        ),
                    ),
                ),
            ),
        )
        val report = ReportDataAssembler.assemble(
            snapshot = snapshot, allGrades = null, identity = null, generatedAt = GENERATED_AT,
        )
        val subject = report.subjects.single()
        assertEquals("ENG1001", subject.code)
        assertTrue(subject.assessments.isEmpty())
        assertNull(subject.assessmentAverage)
        assertNull(subject.prediction)
        assertNull(subject.publishedGrade)
        assertNull(subject.od)
    }

    // ── D-189: per-half credit weighting for embedded pairs ───────────────────────

    @Test
    fun embeddedPairPredictionUsesEachHalfOwnCreditsNotMergedTotal() {
        // A real 2 (theory) + 1 (lab) pair must be weighted 2:1. The merged accumulator's
        // `credits` is the pair's TOTAL (3); the old code fed 3.0 + a hardcoded 1.0 to the
        // predictor, silently turning a 2+1 pair into a 3+1 pair.
        val snapshot = DomainSnapshot(
            academics = Academics(
                semesters = mapOf(
                    "S" to Semester(
                        id = "S",
                        courses = listOf(
                            Course(
                                code = "CSE2002", title = "Signals and Systems",
                                type = "Embedded Theory", credits = "2", slots = listOf("A1"),
                                attendance = Attendance(attended = 8, total = 10, percentage = "80"),
                                marks = Marks(
                                    assessments = listOf(
                                        Assessment(
                                            name = "CAT 1", score = "16", maxMark = "20",
                                            weightage = "10", weightagePercent = "20",
                                            status = "Present", component = "ETH",
                                        ),
                                    ),
                                ),
                            ),
                            Course(
                                code = "CSE2002(L)", title = "Signals and Systems Lab",
                                type = "Embedded Lab", credits = "1", slots = listOf("L31"),
                                attendance = Attendance(attended = 4, total = 5, percentage = "80"),
                                marks = Marks(
                                    assessments = listOf(
                                        Assessment(
                                            name = "Lab Record", score = "10", maxMark = "10",
                                            weightage = "10", weightagePercent = "20",
                                            status = "Present", component = "ELA",
                                        ),
                                    ),
                                ),
                            ),
                        ).associateBy { it.code },
                    ),
                ),
            ),
        )
        val prediction = ReportDataAssembler.assemble(
            snapshot = snapshot, allGrades = null, identity = null, generatedAt = GENERATED_AT,
        ).subjects.single().prediction
        assertNotNull(prediction)
        val p = prediction!!

        // Rebuild the exact predictors the assembler derived, then compare with the canonical
        // engine called with the REAL half credits (2.0 / 1.0).
        val theory = PredictorAssessment(
            id = "CAT 1", title = "CAT 1", maxMark = 20.0, weightagePercent = 20.0,
            scoredMark = 16.0, status = "Present", component = PredictorComponent.THEORY,
        )
        val lab = PredictorAssessment(
            id = "Lab Record", title = "Lab Record", maxMark = 10.0, weightagePercent = 20.0,
            scoredMark = 10.0, status = "Present", component = PredictorComponent.LAB,
        )
        val correct = computeCoursePrediction(
            courseCode = "CSE2002", courseTitle = "Signals and Systems",
            courseType = "Embedded Theory and Lab",
            theoryAssessments = listOf(theory), labAssessments = listOf(lab),
            theoryCredits = 2.0, labCredits = 1.0,
        )
        assertEquals(correct.currentStandingPct, p.currentStandingPct!!, 0.0001)
        assertEquals(correct.maxPossibleScaled, p.maxPossiblePct!!, 0.0001)

        // The bug's 3.0 + 1.0 input yields a different pace, so the assertion above is a real
        // regression guard rather than a tautology.
        val oldBug = computeCoursePrediction(
            courseCode = "CSE2002", courseTitle = "Signals and Systems",
            courseType = "Embedded Theory and Lab",
            theoryAssessments = listOf(theory), labAssessments = listOf(lab),
            theoryCredits = 3.0, labCredits = 1.0,
        )
        assertTrue(kotlin.math.abs(oldBug.currentStandingPct - p.currentStandingPct!!) > 0.5)
    }

    @Test
    fun embeddedPairWithoutHalfCreditsHasNoPrediction() {
        // When a half publishes no credits its weight is UNKNOWN. The assembler must not fall back
        // to 1.0 or to the merged total — skipping the prediction is the honest outcome.
        val snapshot = DomainSnapshot(
            academics = Academics(
                semesters = mapOf(
                    "S" to Semester(
                        id = "S",
                        courses = listOf(
                            Course(
                                code = "CSE2003", title = "Signals and Systems",
                                type = "Embedded Theory", credits = "2", slots = listOf("A1"),
                                attendance = Attendance(attended = 8, total = 10, percentage = "80"),
                                marks = Marks(
                                    assessments = listOf(
                                        Assessment(
                                            name = "CAT 1", score = "16", maxMark = "20",
                                            weightage = "10", weightagePercent = "20",
                                            status = "Present", component = "ETH",
                                        ),
                                    ),
                                ),
                            ),
                            Course(
                                code = "CSE2003(L)", title = "Signals and Systems Lab",
                                type = "Embedded Lab", credits = null, slots = listOf("L31"),
                                attendance = Attendance(attended = 4, total = 5, percentage = "80"),
                                marks = Marks(
                                    assessments = listOf(
                                        Assessment(
                                            name = "Lab Record", score = "10", maxMark = "10",
                                            weightage = "10", weightagePercent = "20",
                                            status = "Present", component = "ELA",
                                        ),
                                    ),
                                ),
                            ),
                        ).associateBy { it.code },
                    ),
                ),
            ),
        )
        val subject = ReportDataAssembler.assemble(
            snapshot = snapshot, allGrades = null, identity = null, generatedAt = GENERATED_AT,
        ).subjects.single()
        // The two halves still merge into one dossier carrying both real assessments...
        assertEquals(2, subject.assessments.size)
        // ...but an unknown half credit must not produce a fabricated weighted grade.
        assertNull(subject.prediction)
    }
}
