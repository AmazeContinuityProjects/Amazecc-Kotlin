package com.amazecc.app.shared.state

import com.amazecc.app.shared.domain.DomainSnapshot
import com.amazecc.app.shared.domain.VtopIngestor
import com.amazecc.app.shared.model.ApaarIdRes
import com.amazecc.app.shared.model.ArrearResponse
import com.amazecc.app.shared.model.AssessmentItem
import com.amazecc.app.shared.model.AttendanceLog
import com.amazecc.app.shared.model.BusesRes
import com.amazecc.app.shared.model.CabShareHub
import com.amazecc.app.shared.model.CabShareUser
import com.amazecc.app.shared.model.CalendarDay
import com.amazecc.app.shared.model.CalendarEvent
import com.amazecc.app.shared.model.CalendarMonth
import com.amazecc.app.shared.model.CalendarRes
import com.amazecc.app.shared.model.CalendarsListRes
import com.amazecc.app.shared.model.CategoryDetail
import com.amazecc.app.shared.model.CircularItem
import com.amazecc.app.shared.model.CircularsRes
import com.amazecc.app.shared.model.ClubsRes
import com.amazecc.app.shared.model.CurriculumBasket
import com.amazecc.app.shared.model.CurriculumBasketItem
import com.amazecc.app.shared.model.CurriculumCategory
import com.amazecc.app.shared.model.CurriculumRes
import com.amazecc.app.shared.model.DayboarderRes
import com.amazecc.app.shared.model.EventHubRegisteredEventsRes
import com.amazecc.app.shared.model.EventHubRes
import com.amazecc.app.shared.model.ExamItem
import com.amazecc.app.shared.model.FfcsRegistrationInfo
import com.amazecc.app.shared.model.GradeBreakdown
import com.amazecc.app.shared.model.GradeRange
import com.amazecc.app.shared.model.HostelDetails
import com.amazecc.app.shared.model.HomeworkTask
import com.amazecc.app.shared.model.LMSAssignment
import com.amazecc.app.shared.model.LMSRes
import com.amazecc.app.shared.model.LaundryRes
import com.amazecc.app.shared.model.LaundrySlotItem
import com.amazecc.app.shared.model.LibraryRes
import com.amazecc.app.shared.model.MessMenuDay
import com.amazecc.app.shared.model.MessMenuRes
import com.amazecc.app.shared.model.MoodleRes
import com.amazecc.app.shared.model.NamedCalendar
import com.amazecc.app.shared.model.PaymentItem
import com.amazecc.app.shared.model.PaymentsRes
import com.amazecc.app.shared.model.QcmViewRes
import com.amazecc.app.shared.model.TransportDataRes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * [DomainSnapshot] has to become the persisted format, so the step before that can be trusted is
 * proving the conversion **loses nothing**.
 *
 * `AppDataSnapshot` carries 23 top-level fields (plus the `schemaVersion` marker). `fromLegacy`
 * originally mapped 13 of them and silently defaulted the rest — which, promoted to the storage
 * format, would have quietly deleted a student's payments, laundry schedule, tasks, clubs and
 * cab-share data on upgrade.
 *
 * So rather than eyeball the mapping, every field here is filled with a distinct sentinel and the
 * whole snapshot is round-tripped. Adding a field to [AppDataSnapshot] without giving it a home in
 * [DomainSnapshot] makes this fail, which is the point: the migration is the one place where a
 * missing field is unrecoverable.
 */
class SnapshotRoundTripTest {

    // ── sentinels ──────────────────────────────────────────────────────────────

    private fun v2() = AppDataSnapshot(
        schemaVersion = 2,
        academic = AcademicData(
            semesters = mapOf(
                "SEM1" to SemesterData(
                    semesterId = "SEM1",
                    semesterName = "Fall Semester 2026-27",
                    gpa = "8.75",
                    courses = mapOf(
                        "BACSE102" to StoredCourse(
                            courseCode = "BACSE102",
                            courseTitle = "Problem Solving Using Java",
                            courseType = "Theory Only",
                            category = "University Core Courses",
                            credits = "3",
                            classId = "CH2026270102069",
                            slots = listOf("L31", "L32"),
                            venue = "AB1-607B",
                            faculty = "SHEENA CHRISTABEL PRAVIN",
                            courseSystem = "LAB",
                            attendance = StoredAttendance(
                                attendedClasses = 22,
                                totalClasses = 25,
                                attendancePercentage = "88",
                                logs = listOf(
                                    AttendanceLog("2026-09-01", "Present"),
                                    AttendanceLog("2026-09-02", "Absent"),
                                ),
                            ),
                            marks = StoredMarks(
                                classNbr = "CH2026270102069",
                                assessments = listOf(
                                    AssessmentItem(
                                        title = "Quiz 1",
                                        maxMark = "20",
                                        weightagePercent = "10",
                                        status = "Present",
                                        scoredMark = "18",
                                        weightageMark = "9",
                                        component = "ETH",
                                    )
                                ),
                                totalMark = 42.5,
                                maxMark = 50.0,
                                mergedFrom = "ETH",
                            ),
                            grade = StoredGrade(
                                grandTotal = "88",
                                grade = "A",
                                details = listOf(
                                    GradeBreakdown(
                                        component = "Internal",
                                        maxMark = "50",
                                        weightagePercent = "40",
                                        status = "Present",
                                        scoredMark = "44",
                                        weightageMark = "17.6",
                                    )
                                ),
                                range = GradeRange("90", "80", "70", "60", "50", "40", "0"),
                            ),
                        )
                    ),
                    exams = listOf(
                        ExamItem(
                            courseCode = "BACSE102",
                            courseTitle = "Problem Solving Using Java",
                            classId = "CH2026270102069",
                            slot = "L31+L32",
                            examDate = "2026-11-20",
                            examSession = "FN",
                            reportingTime = "08:45 - 09:45",
                            examTime = "09:45 - 11:00",
                            venue = "AB1-607B",
                            seatLocation = "AB1-605",
                            seatNo = "17",
                        )
                    ),
                )
            )
        ),
        hostelDetails = HostelDetails(),
        messMenu = MessMenuRes(list = listOf(MessMenuDay(Day = "MON", Lunch = "Biryani"))),
        laundrySchedule = LaundryRes(list = listOf(LaundrySlotItem(Date = "2026-10-11", RoomNumber = "B204"))),
        hostelCounselling = ArrearResponse(success = true),
        calendar = CalendarRes(
            success = true,
            months = listOf(
                CalendarMonth(
                    month = "October 2026",
                    days = listOf(
                        CalendarDay(
                            date = 4,
                            events = listOf(
                                CalendarEvent(type = "due", text = "Digital Assignment 2", color = "#f00", category = "assignment")
                            ),
                        )
                    ),
                )
            ),
        ),
        calendarsList = CalendarsListRes(
            success = true,
            calendars = listOf(NamedCalendar(name = "Academic")),
        ),
        qcmView = QcmViewRes(
            success = true,
            tables = listOf(
                StoredQcmTable(
                    caption = "Fall Semester 2026-27 - CHN",
                    rows = listOf(StoredQcmRow(qcmNo = "1", action = "Faculty and HOD")),
                )
            ),
            semesters = listOf("CH20262701"),
        ),
        curriculum = CurriculumRes(
            success = true,
            categories = listOf(CurriculumCategory(code = "core", name = "Core")),
            details = listOf(
                CategoryDetail(
                    code = "core",
                    name = "Core",
                    baskets = listOf(
                        CurriculumBasket(
                            title = "Basket 1",
                            items = listOf(
                                CurriculumBasketItem(code = "BACSE102", name = "Problem Solving Using Java", credits = 3)
                            ),
                        )
                    ),
                )
            ),
        ),
        payments = PaymentsRes(
            success = true,
            walletBalance = "1200",
            payments = listOf(PaymentItem(billingId = "b1", description = "Hostel", amount = "45000", status = "Paid", paymentDate = "2026-08-01")),
        ),
        library = LibraryRes(success = true),
        transportData = TransportDataRes(success = true),
        buses = BusesRes(success = true),
        lms = LMSRes(
            success = true,
            assignments = listOf(LMSAssignment(name = "BACSE102/Course/Quiz")),
        ),
        events = EventHubRes(success = true),
        registeredEvents = EventHubRegisteredEventsRes(success = true),
        clubs = ClubsRes(success = true),
        circulars = CircularsRes(success = true, circulars = listOf(CircularItem(id = "c1", title = "Notice"))),
        moodleData = MoodleRes(success = true),
        cabShareUser = CabShareUser(reg_number = "25BLC1081", name = "SUGEETH J S A"),
        cabHubs = listOf(CabShareHub(hub_id = 2, hub_name = "Chennai Airport")),
        ffcsRegistration = FfcsRegistrationInfo(userName = "SUGEETHJSA", fromTime = "08:00", toTime = "09:00"),
        tasks = listOf(HomeworkTask(id = "t1", courseCode = "BACSE102", courseTitle = "Java", title = "Lab 1", dueDate = "2026-10-10", createdAt = "2026-09-01T00:00:00Z")),
    )

    // ── the property ───────────────────────────────────────────────────────────

    @Test
    fun everyTopLevelFieldSurvivesTheRoundTrip() {
        val original = v2()
        val restored = VtopIngestor.toLegacy(VtopIngestor.fromLegacy(original))

        assertNotNull(restored.academic.semesters["SEM1"], "academics")
        assertNotNull(restored.hostelDetails, "hostelDetails")
        assertNotNull(restored.messMenu, "messMenu")
        assertNotNull(restored.laundrySchedule, "laundrySchedule")
        assertNotNull(restored.hostelCounselling, "hostelCounselling")
        assertNotNull(restored.calendar, "calendar")
        assertNotNull(restored.calendarsList, "calendarsList")
        assertNotNull(restored.qcmView, "qcmView")
        assertNotNull(restored.curriculum, "curriculum")
        assertNotNull(restored.payments, "payments")
        assertNotNull(restored.library, "library")
        assertNotNull(restored.transportData, "transportData")
        assertNotNull(restored.buses, "buses")
        assertNotNull(restored.lms, "lms")
        assertNotNull(restored.events, "events")
        assertNotNull(restored.registeredEvents, "registeredEvents")
        assertNotNull(restored.clubs, "clubs")
        assertNotNull(restored.circulars, "circulars")
        assertNotNull(restored.moodleData, "moodleData")
        assertNotNull(restored.cabShareUser, "cabShareUser")
        assertEquals(1, restored.cabHubs.size, "cabHubs")
        assertNotNull(restored.ffcsRegistration, "ffcsRegistration")
        assertEquals(1, restored.tasks.size, "tasks")
    }

    @Test
    fun nestedAcademicDetailSurvivesFieldByField() {
        val restored = VtopIngestor.toLegacy(VtopIngestor.fromLegacy(v2()))
        val sem = assertNotNull(restored.academic.semesters["SEM1"])
        assertEquals("Fall Semester 2026-27", sem.semesterName)
        assertEquals("8.75", sem.gpa)

        val c = assertNotNull(sem.courses["BACSE102"])
        assertEquals("Problem Solving Using Java", c.courseTitle)
        assertEquals("Theory Only", c.courseType)
        assertEquals("University Core Courses", c.category)
        assertEquals("CH2026270102069", c.classId)
        assertEquals(listOf("L31", "L32"), c.slots)
        assertEquals("AB1-607B", c.venue)
        assertEquals("SHEENA CHRISTABEL PRAVIN", c.faculty)
        // Dropped by the first version of the mapper, and nothing noticed.
        assertEquals("LAB", c.courseSystem)

        val a = assertNotNull(c.attendance)
        assertEquals(22, a.attendedClasses)
        assertEquals(25, a.totalClasses)
        assertEquals("88", a.attendancePercentage)
        assertEquals(2, a.logs.size)
        assertEquals("2026-09-02", a.logs[1].date)

        val m = assertNotNull(c.marks)
        assertEquals("CH2026270102069", m.classNbr)
        assertEquals(42.5, m.totalMark)
        assertEquals(50.0, m.maxMark)
        assertEquals("ETH", m.mergedFrom)
        val asm = m.assessments.single()
        assertEquals("Quiz 1", asm.title)
        assertEquals("18", asm.scoredMark)
        assertEquals("20", asm.maxMark)
        assertEquals("9", asm.weightageMark)
        assertEquals("10", asm.weightagePercent)
        assertEquals("Present", asm.status)
        assertEquals("ETH", asm.component)

        val g = assertNotNull(c.grade)
        assertEquals("A", g.grade)
        assertEquals("88", g.grandTotal)
        val det = assertNotNull(g.details).single()
        assertEquals("Internal", det.component)
        assertEquals("44", det.scoredMark)
        assertEquals("17.6", det.weightageMark)
        assertEquals("40", det.weightagePercent)
        assertEquals("Present", det.status)
        // Structured bands, not the pre-rendered string the first mapper produced.
        assertEquals("90", assertNotNull(g.range).S)
        assertEquals("80", g.range.A)
        assertEquals("0", g.range.F)
    }

    @Test
    fun examDetailSurvivesIncludingClassIdAndSlot() {
        val restored = VtopIngestor.toLegacy(VtopIngestor.fromLegacy(v2()))
        val e = restored.academic.semesters["SEM1"]!!.exams.single()
        assertEquals("CH2026270102069", e.classId)
        assertEquals("L31+L32", e.slot)
        assertEquals("2026-11-20", e.examDate)
        assertEquals("08:45 - 09:45", e.reportingTime)
        assertEquals("09:45 - 11:00", e.examTime)
        assertEquals("AB1-605", e.seatLocation)
        assertEquals("17", e.seatNo)
    }

    @Test
    fun calendarAndListsSurvive() {
        val restored = VtopIngestor.toLegacy(VtopIngestor.fromLegacy(v2()))
        val month = assertNotNull(restored.calendar).months.single()
        assertEquals("October 2026", month.month)
        val day = month.days.single()
        assertEquals(4, day.date)
        val ev = day.events.single()
        assertEquals("due", ev.type)
        assertEquals("Digital Assignment 2", ev.text)
        assertEquals("#f00", ev.color)
        assertEquals("assignment", ev.category)
        assertEquals("Academic", assertNotNull(restored.calendarsList).calendars.single().name)
    }

    @Test
    fun domainExamIsKeyedBySemesterIdNotSectionHeader() {
        // Projections.exams filters on this, so getting it wrong renders an empty list.
        val domain = VtopIngestor.fromLegacy(v2())
        val exam = domain.schedule.exams.single()
        assertEquals("SEM1", exam.semesterId)
        assertEquals("BACSE102", exam.courseCode)
    }

    @Test
    fun qcmSemestersAndCircularsSurvive() {
        val restored = VtopIngestor.toLegacy(VtopIngestor.fromLegacy(v2()))
        val qcm = assertNotNull(restored.qcmView)
        assertEquals(listOf("CH20262701"), qcm.semesters)
        assertEquals("1", qcm.tables.single().rows.single().qcmNo)
        assertEquals("Notice", assertNotNull(restored.circulars).circulars.single().title)
    }

    // ── the two mapping bugs found while doing this ────────────────────────────

    @Test
    fun selectedSemesterIsTheNewestNotTheLexicographicallyGreatest() {
        // Semester ids are CH20262701 / CH20252601 / CH20222323, so the lexicographically greatest
        // is the *oldest*. Picking it opened the app on a semester from years ago.
        val legacy = v2().copy(
            academic = AcademicData(
                semesters = mapOf(
                    "CH20262701" to SemesterData(semesterId = "CH20262701"),
                    "CH20252601" to SemesterData(semesterId = "CH20252601"),
                    "CH20222323" to SemesterData(semesterId = "CH20222323"),
                )
            )
        )
        assertEquals("CH20262701", VtopIngestor.fromLegacy(legacy).academics.selectedSemesterId)
    }

    @Test
    fun anExplicitlySelectedSemesterWins() {
        val legacy = v2().copy(
            academic = AcademicData(
                semesters = mapOf(
                    "CH20262701" to SemesterData(semesterId = "CH20262701"),
                    "CH20252601" to SemesterData(semesterId = "CH20252601"),
                )
            )
        )
        val domain = VtopIngestor.fromLegacy(legacy, selectedSemesterId = "CH20252601")
        assertEquals("CH20252601", domain.academics.selectedSemesterId)
    }

    @Test
    fun creditsPreferCurriculumOverTheStoredValue() {
        // Curriculum is the official structure; the timetable's LTPJC parses unreliably.
        val legacy = v2().copy(
            academic = AcademicData(
                semesters = mapOf(
                    "SEM1" to SemesterData(
                        semesterId = "SEM1",
                        courses = mapOf(
                            "BACSE102" to StoredCourse(courseCode = "BACSE102", credits = "9")
                        ),
                    )
                )
            )
        )
        val domain = VtopIngestor.fromLegacy(legacy)
        assertEquals("3", domain.academics.semesters["SEM1"]!!.courses["BACSE102"]!!.credits)
    }

    @Test
    fun aCurriculumCodeWithNoMatchFallsBackToTheStoredCredits() {
        val legacy = v2().copy(
            academic = AcademicData(
                semesters = mapOf(
                    "SEM1" to SemesterData(
                        semesterId = "SEM1",
                        courses = mapOf("ZZZZ999" to StoredCourse(courseCode = "ZZZZ999", credits = "4")),
                    )
                )
            )
        )
        assertEquals("4", VtopIngestor.fromLegacy(legacy).academics.semesters["SEM1"]!!.courses["ZZZZ999"]!!.credits)
    }

    @Test
    fun emptySnapshotStaysEmpty() {
        val domain = VtopIngestor.fromLegacy(AppDataSnapshot())
        assertTrue(domain.academics.semesters.isEmpty())
        assertTrue(domain.schedule.exams.isEmpty())
        assertEquals(DomainSnapshot.SCHEMA_VERSION, domain.schemaVersion)
    }
}
