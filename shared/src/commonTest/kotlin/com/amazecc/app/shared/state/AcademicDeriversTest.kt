package com.amazecc.app.shared.state

import com.amazecc.app.shared.model.AssessmentItem
import com.amazecc.app.shared.model.AttendanceLog
// Members of `object AcademicDerivers`, so they need importing even inside the same package.
import com.amazecc.app.shared.state.AcademicDerivers.isLabCourse
import com.amazecc.app.shared.state.AcademicDerivers.toAttendanceItem
import com.amazecc.app.shared.state.AcademicDerivers.toGradeItem
import com.amazecc.app.shared.state.AcademicDerivers.toMarksCourseItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AcademicDeriversTest {

    private fun course(
        code: String,
        title: String = "Course $code",
        slots: List<String> = listOf("A1"),
        venue: String? = null,
        faculty: String? = null,
        classId: String? = null,
        category: String? = null,
        type: String = "",
        attendance: StoredAttendance? = null
    ) = StoredCourse(
        courseCode = code,
        courseTitle = title,
        courseType = type,
        category = category,
        credits = "4",
        classId = classId,
        slots = slots,
        venue = venue,
        faculty = faculty,
        attendance = attendance
    )

    // ── buildWeeklyTimetable ──

    @Test
    fun weeklyTimetableResolvesDayTimeAndCarriesCourseFields() {
        val sem = SemesterData(
            semesterId = "SEM1",
            courses = mapOf(
                "18MAB101T" to course(
                    code = "18MAB101T",
                    slots = listOf("A1"),
                    venue = "AB1-101",
                    faculty = "Dr. X",
                    classId = "C123",
                    category = "PC",
                    attendance = StoredAttendance(attendedClasses = 34, totalClasses = 40, attendancePercentage = "85")
                )
            )
        )
        val slots = AcademicDerivers.buildWeeklyTimetable(sem)
        // "A1" is a period on more than one day: A1 is Monday 8:00 and Wednesday 8:55 (see
        // AmazeCC/src/data/campus/chennai.json, which maps period -> day -> slot id). A course on
        // A1 therefore appears on every day A1 is taught, not on one guessed day.
        val a1 = slots.filter { it.slotName == "A1" }
        assertEquals(setOf("MON", "WED"), a1.mapNotNull { it.day }.toSet())
        val monday = a1.single { it.day == "MON" }
        assertEquals("A1", monday.slotName)
        assertEquals("8:00-8:50", monday.time)
        assertEquals("18MAB101T", monday.courseCode)
        assertEquals("AB1-101", monday.venue)
        assertEquals("Dr. X", monday.faculty)
        assertEquals("C123", monday.classId)
        assertEquals("PC", monday.category)
        assertEquals(85.0, monday.attendancePercentage)
    }

    @Test
    fun weeklyTimetableSortedByDayAndTime() {
        val sem = SemesterData(
            semesterId = "SEM1",
            courses = mapOf(
                "18CSC301J" to course(code = "18CSC301J", slots = listOf("A1", "F1"))
            )
        )
        val slots = AcademicDerivers.buildWeeklyTimetable(sem)
        // Both A1 and F1 are taught on two days each (MON and WED), so the course yields one
        // entry per (slot, day) pair - 4 in total. Sorting is by day, then time, then slot name.
        assertEquals(4, slots.size)
        assertEquals(listOf("MON", "MON", "WED", "WED"), slots.mapNotNull { it.day })
        assertEquals(listOf("A1", "F1", "A1", "F1"), slots.map { it.slotName })
        assertEquals("8:00-8:50", slots[0].time, "MON A1")
        assertEquals("8:55-9:45", slots[1].time, "MON F1")
        assertEquals("8:55-9:45", slots[2].time, "WED A1")
    }

    @Test
    fun weeklyTimetableSkipsUnknownSlots() {
        val sem = SemesterData(
            semesterId = "SEM1",
            courses = mapOf("18CSC301J" to course(code = "18CSC301J", slots = listOf("ZZ9")))
        )
        assertTrue(AcademicDerivers.buildWeeklyTimetable(sem).isEmpty())
    }

    // ── computeODHours ──

    @Test
    fun odHoursLabCountsDoubleTheoryCountsSingle() {
        val sem = SemesterData(
            semesterId = "SEM1",
            courses = mapOf(
                "18MAB101T" to course(
                    code = "18MAB101T",
                    slots = listOf("A1"),
                    attendance = StoredAttendance(
                        logs = listOf(
                            AttendanceLog("2026-08-01", "on duty"),
                            AttendanceLog("2026-08-02", "present"),
                            AttendanceLog("2026-08-03", "absent")
                        )
                    )
                ),
                "18CSC301L" to course(
                    code = "18CSC301L",
                    slots = listOf("L1"),
                    attendance = StoredAttendance(
                        logs = listOf(
                            AttendanceLog("2026-08-01", "od"),
                            AttendanceLog("2026-08-02", "on duty"),
                            AttendanceLog("2026-08-03", "onduty")
                        )
                    )
                )
            )
        )
        // theory: 1 on-duty log × 1h; lab: 3 od logs × 2h
        assertEquals(7, AcademicDerivers.computeODHours(sem))
    }

    @Test
    fun odHoursIgnoreNonOdStatuses() {
        val sem = SemesterData(
            semesterId = "SEM1",
            courses = mapOf(
                "18MAB101T" to course(
                    code = "18MAB101T",
                    attendance = StoredAttendance(
                        logs = listOf(
                            AttendanceLog("2026-08-01", "present"),
                            AttendanceLog("2026-08-02", "absent"),
                            AttendanceLog("2026-08-03", "leave")
                        )
                    )
                )
            )
        )
        assertEquals(0, AcademicDerivers.computeODHours(sem))
    }

    // ── resolveCurrentSemester ──

    @Test
    fun resolveCurrentSemesterPicksMostAttendanceBearingSemester() {
        val academic = AcademicData(
            semesters = mapOf(
                "SEM1" to SemesterData(
                    semesterId = "SEM1",
                    courses = mapOf(
                        "A" to course("A", attendance = StoredAttendance()),
                        "B" to course("B", attendance = StoredAttendance())
                    )
                ),
                "SEM2" to SemesterData(
                    semesterId = "SEM2",
                    courses = mapOf("C" to course("C", attendance = StoredAttendance()))
                )
            )
        )
        assertEquals("SEM1", AcademicDerivers.resolveCurrentSemester(academic)?.semesterId)
    }

    @Test
    fun resolveCurrentSemesterTieBreaksBySemesterId() {
        val academic = AcademicData(
            semesters = mapOf(
                "SEM1" to SemesterData(semesterId = "SEM1", courses = mapOf("A" to course("A", attendance = StoredAttendance()))),
                "SEM2" to SemesterData(semesterId = "SEM2", courses = mapOf("B" to course("B", attendance = StoredAttendance())))
            )
        )
        assertEquals("SEM2", AcademicDerivers.resolveCurrentSemester(academic)?.semesterId)
    }

    @Test
    fun resolveCurrentSemesterEmptyReturnsNull() {
        assertNull(AcademicDerivers.resolveCurrentSemester(AcademicData()))
    }

    // ── isLabCourse ──

    @Test
    fun isLabCourseDetection() {
        assertTrue(course("18CSC301L", type = "Lab Only").isLabCourse())
        assertTrue(course("18CSC301L", slots = listOf("L1")).isLabCourse())
        assertTrue(course("18CSC301L(L)").isLabCourse())
        assertFalse(course("18MAB101T").isLabCourse())
    }

    // ── toAttendanceItem ──

    @Test
    fun toAttendanceItemMapsStoredCourse() {
        val stored = course(
            code = "18MAB101T",
            slots = listOf("A1", "A2"),
            venue = "AB1-101",
            faculty = null,
            attendance = StoredAttendance(attendedClasses = 10, totalClasses = 12, attendancePercentage = "83")
        )
        val item = stored.toAttendanceItem()
        assertEquals("18MAB101T", item.courseCode)
        assertEquals("A1+A2", item.slotName)
        assertEquals("AB1-101", item.slotVenue)
        assertEquals("", item.faculty)
        assertEquals("83", item.attendancePercentage)
        assertEquals(10, item.attendedClasses)
        assertEquals(12, item.totalClasses)
        assertEquals("4", item.credits)
    }

    // ── toMarksCourseItem / toGradeItem ──

    @Test
    fun toMarksCourseItemMapsStoredCourse() {
        val stored = course(code = "18MAB101T", classId = "C123", faculty = "Dr. X", slots = listOf("A1"))
            .copy(
                courseSystem = "F",
                marks = StoredMarks(classNbr = "C123", assessments = listOf(AssessmentItem(title = "CAT1", scoredMark = "45")))
            )
        val item = stored.toMarksCourseItem()
        assertEquals("C123", item.classNbr)
        assertEquals("A1", item.slot)
        assertEquals("F", item.courseSystem)
        assertEquals(1, item.assessments.size)
    }

    @Test
    fun toGradeItemMapsStoredCourse() {
        val stored = course(code = "18MAB101T").copy(
            grade = StoredGrade(grandTotal = "92", grade = "A")
        )
        val item = stored.toGradeItem()
        assertEquals("92", item.grandTotal)
        assertEquals("A", item.grade)
        val noGrade = course("18CSC301J").toGradeItem()
        assertEquals("", noGrade.grade)
    }

    // ── splitSlotVenue ──

    @Test
    fun splitSlotVenuePipeAndDashForms() {
        assertEquals("A1" to "AB3-305", AcademicDerivers.splitSlotVenue("A1 | AB3-305"))
        assertEquals("A1" to "AB3-305", AcademicDerivers.splitSlotVenue("A1 - AB3-305"))
    }

    @Test
    fun splitSlotVenueUnresolvableFallsBackToFullString() {
        assertEquals(null to "XYZ | AB3-305", AcademicDerivers.splitSlotVenue("XYZ | AB3-305"))
        assertEquals(null to null, AcademicDerivers.splitSlotVenue(null))
        assertEquals(null to null, AcademicDerivers.splitSlotVenue("  "))
    }

    // ── course type helpers ──

    @Test
    fun courseTypeAndCleanCode() {
        assertEquals("Lab Only", AcademicDerivers.courseTypeOf("18CSC301L(L)"))
        assertEquals("Theory Only", AcademicDerivers.courseTypeOf("18MAB101T(T)"))
        assertNull(AcademicDerivers.courseTypeOf("18MAB101T"))
        assertEquals("18CSC301L", AcademicDerivers.cleanCourseCode(" 18CSC301L (L) "))
        assertEquals("18MAB101T", AcademicDerivers.cleanCourseCode("18MAB101T(T)"))
    }

    @Test
    fun percentOfParsesOnlyValidNumbers() {
        assertEquals(85.0, AcademicDerivers.percentOf(course("A", attendance = StoredAttendance(attendancePercentage = "85"))))
        assertNull(AcademicDerivers.percentOf(course("A", attendance = StoredAttendance(attendancePercentage = "n/a"))))
        assertNull(AcademicDerivers.percentOf(course("A")))
    }

    // ── OD hours ──────────────────────────────────────────────────────────────

    @Test
    fun odHoursCountsALabWhoseCodeEndsInSuffixEvenWhenTheSlotIsNotLabShaped() {
        // The two OD counters used to disagree right here. The widget one read only `slotName`,
        // so this course was worth one hour in a widget and two on the home screen — and since
        // OD hours are what buys a bunk, the same student saw two answers to one question.
        val code = "18CSC301(L)"
        val logs = listOf(AttendanceLog("15-Dec-2025", "On Duty"))

        val sem = SemesterData(
            semesterId = "CH20262701",
            semesterName = "Fall Semester 2025-26",
            courses = mapOf(code to course(code = code, slots = listOf("A1"), attendance = StoredAttendance(logs = logs))),
        )
        val asRow = sem.courses.getValue(code).toAttendanceItem()

        assertEquals(2, AcademicDerivers.computeODHours(sem))
        // Both entry points, over the same course, must now be the same number.
        assertEquals(
            AcademicDerivers.computeODHours(sem),
            com.amazecc.app.shared.utils.WidgetDataUtils.computeODHours(listOf(asRow)),
        )
    }

    @Test
    fun odHoursWeighsTheoryAtOneHourAndLabAtTwo() {
        val theory = "18MAB101T"
        val lab = "18CSC301L"
        val logs = listOf(
            AttendanceLog("15-Dec-2025", "On Duty"),
            AttendanceLog("16-Dec-2025", "on duty"),
            AttendanceLog("17-Dec-2025", " od "),
        )

        val sem = SemesterData(
            semesterId = "CH20262701",
            semesterName = "Fall Semester 2025-26",
            courses = mapOf(
                theory to course(code = theory, slots = listOf("A1"), attendance = StoredAttendance(logs = logs)),
                lab to course(code = lab, slots = listOf("L31"), attendance = StoredAttendance(logs = logs)),
            ),
        )
        // Three theory sessions (3h) plus three lab sessions (6h).
        assertEquals(9, AcademicDerivers.computeODHours(sem))
    }

    @Test
    fun odHoursIgnoresStatusesOutsideTheVtopVocabulary() {
        val code = "18CSC301L"
        val sem = SemesterData(
            semesterId = "CH20262701",
            semesterName = "Fall Semester 2025-26",
            courses = mapOf(
                code to course(
                    code = code,
                    slots = listOf("L31"),
                    attendance = StoredAttendance(
                        logs = listOf(
                            AttendanceLog("15-Dec-2025", "Absent"),
                            AttendanceLog("16-Dec-2025", "partial od"),
                            AttendanceLog("17-Dec-2025", "sectional holiday"),
                        )
                    ),
                )
            ),
        )
        assertEquals(0, AcademicDerivers.computeODHours(sem))
    }
}
