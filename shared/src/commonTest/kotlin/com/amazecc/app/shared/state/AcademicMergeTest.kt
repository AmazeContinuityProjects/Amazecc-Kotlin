package com.amazecc.app.shared.state

import com.amazecc.app.shared.model.AllGradesRes
import com.amazecc.app.shared.model.AssessmentItem
import com.amazecc.app.shared.model.AttendanceItem
import com.amazecc.app.shared.model.AttendanceRes
import com.amazecc.app.shared.model.ExamItem
import com.amazecc.app.shared.model.ExamScheduleRes
import com.amazecc.app.shared.model.GradeBreakdown
import com.amazecc.app.shared.model.GradeItem
import com.amazecc.app.shared.model.MarksCourseItem
import com.amazecc.app.shared.model.MarksRes
import com.amazecc.app.shared.model.SemesterGradeResult
import com.amazecc.app.shared.model.TimetableRes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.assertSame

class AcademicMergeTest {

    private fun attendanceRes(
        semesterId: String? = "SEM1",
        items: List<AttendanceItem> = emptyList()
    ) = AttendanceRes(semesterId = semesterId, attendance = items)

    private fun attItem(
        code: String = "18MAB101T",
        title: String = "Calculus",
        slot: String = "A1",
        percent: String = "85"
    ) = AttendanceItem(
        courseCode = code,
        courseTitle = title,
        courseType = "Theory",
        slotName = slot,
        faculty = "Dr. X",
        attendedClasses = 34,
        totalClasses = 40,
        attendancePercentage = percent,
        credits = "4",
        slotVenue = "AB1-101",
        category = "PC"
    )

    private val marksRes = MarksRes(
        success = true,
        courses = listOf(
            MarksCourseItem(
                classNbr = "C123",
                courseCode = "18MAB101T",
                courseTitle = "Calculus",
                courseType = "Theory",
                courseSystem = "F",
                faculty = "Dr. X",
                slot = "A1",
                assessments = listOf(AssessmentItem(title = "CAT1", maxMark = "50", scoredMark = "45"))
            )
        )
    )

    private val gradeItem = GradeItem(
        courseCode = "18MAB101T",
        courseTitle = "Calculus",
        courseType = "Theory",
        grandTotal = "92",
        grade = "A",
        details = listOf(GradeBreakdown(component = "CAT1", scoredMark = "45", maxMark = "50")),
        range = null
    )

    // ── attendance replaces only its own domain ──

    @Test
    fun upsertAttendanceCreatesSemesterAndCourse() {
        val out = AcademicMerge.upsertAttendance(AcademicData(), "SEM1", attendanceRes(items = listOf(attItem())))
        val sem = out.semesters["SEM1"]
        assertNotNull(sem)
        val course = sem.courses["18MAB101T"]
        assertNotNull(course)
        assertEquals("Calculus", course.courseTitle)
        assertEquals(listOf("A1"), course.slots)
        assertEquals("AB1-101", course.venue)
        assertEquals(85.0, course.attendance?.attendancePercentage?.toDoubleOrNull())
        assertEquals(34, course.attendance?.attendedClasses)
    }

    @Test
    fun upsertAttendanceDoesNotClobberGrade() {
        val base = AcademicMerge.upsertGrades(AcademicData(), "SEM1", "8.5", listOf(gradeItem))
        val out = AcademicMerge.upsertAttendance(base, "SEM1", attendanceRes(items = listOf(attItem())))
        val course = out.semesters.getValue("SEM1").courses.getValue("18MAB101T")
        assertEquals("A", course.grade?.grade)
        assertNotNull(course.attendance)
    }

    @Test
    fun upsertAttendanceClearsAttendanceForAbsentCourses() {
        var academic = AcademicMerge.upsertAttendance(AcademicData(), "SEM1", attendanceRes(items = listOf(attItem())))
        academic = AcademicMerge.upsertAttendance(
            academic, "SEM1", attendanceRes(items = listOf(attItem(code = "18CSC301J", title = "DBMS")))
        )
        val sem = academic.semesters.getValue("SEM1")
        assertNotNull(sem.courses["18CSC301J"]?.attendance)
        assertNull(sem.courses["18MAB101T"]?.attendance)
    }

    // ── marks replaces only its own domain ──

    @Test
    fun upsertMarksReplacesMarksAndKeepsAttendance() {
        val base = AcademicMerge.upsertAttendance(AcademicData(), "SEM1", attendanceRes(items = listOf(attItem())))
        val out = AcademicMerge.upsertMarks(base, "SEM1", marksRes)
        val course = out.semesters.getValue("SEM1").courses.getValue("18MAB101T")
        assertEquals("C123", course.marks?.classNbr)
        assertEquals(1, course.marks?.assessments?.size)
        assertNotNull(course.attendance)
    }

    @Test
    fun upsertMarksWithEmptyPayloadClearsMarks() {
        val base = AcademicMerge.upsertMarks(AcademicData(), "SEM1", marksRes)
        val out = AcademicMerge.upsertMarks(base, "SEM1", MarksRes())
        assertNull(out.semesters.getValue("SEM1").courses.getValue("18MAB101T").marks)
    }

    // ── grades ──

    @Test
    fun upsertGradesSetsGpaAndGradeWithoutTouchingAttendance() {
        val base = AcademicMerge.upsertAttendance(AcademicData(), "SEM1", attendanceRes(items = listOf(attItem())))
        val out = AcademicMerge.upsertGrades(base, "SEM1", "8.5", listOf(gradeItem))
        val sem = out.semesters.getValue("SEM1")
        assertEquals("8.5", sem.gpa)
        assertEquals("A", sem.courses.getValue("18MAB101T").grade?.grade)
        assertNotNull(sem.courses.getValue("18MAB101T").attendance)
    }

    @Test
    fun upsertGradesAllSemesters() {
        val res = AllGradesRes(
            grades = mapOf(
                "SEM1" to SemesterGradeResult(gpa = "8.5", grades = listOf(gradeItem)),
                "SEM2" to SemesterGradeResult(gpa = "9.0", grades = listOf(gradeItem.copy(courseCode = "18CSC301J")))
            )
        )
        val out = AcademicMerge.upsertGrades(AcademicData(), res)
        assertEquals("8.5", out.semesters.getValue("SEM1").gpa)
        assertEquals("9.0", out.semesters.getValue("SEM2").gpa)
        assertEquals("18CSC301J", out.semesters.getValue("SEM2").courses.keys.first())
    }

    @Test
    fun upsertGradesAllSemestersSkipsNullSemester() {
        val res = AllGradesRes(grades = mapOf("SEM1" to null, "SEM2" to SemesterGradeResult(gpa = "9.0")))
        val out = AcademicMerge.upsertGrades(AcademicData(), res)
        assertNull(out.semesters["SEM1"])
        assertNotNull(out.semesters["SEM2"])
    }

    // ── exams replace the semester's list ──

    @Test
    fun upsertExamsFlattensGroupHeadersAndReplaces() {
        val res = ExamScheduleRes(
            rawScheduleUpper = mapOf(
                "18MAB101T" to listOf(ExamItem(courseCode = "18MAB101T", courseTitle = "Calculus", examDate = "2026-12-01")),
                "18CSC301J" to listOf(ExamItem(courseCode = "18CSC301J", courseTitle = "DBMS", examDate = "2026-12-03"))
            )
        )
        val out = AcademicMerge.upsertExams(AcademicData(), "SEM1", res)
        assertEquals(2, out.semesters.getValue("SEM1").exams.size)
        assertEquals("2026-12-01", out.semesters.getValue("SEM1").exams[0].examDate)
        val out2 = AcademicMerge.upsertExams(out, "SEM1", ExamScheduleRes())
        assertTrue(out2.semesters.getValue("SEM1").exams.isEmpty())
    }

    // ── timetable identity merge ──

    @Test
    fun upsertTimetableFillsFieldsWithoutClobberingAttendance() {
        val base = AcademicMerge.upsertAttendance(AcademicData(), "SEM1", attendanceRes(items = listOf(attItem())))
        val res = TimetableRes(
            success = true,
            courseInfo = listOf(
                TimetableCourseInfo(
                    slNo = "1",
                    course = "Calculus",
                    courseCode = "18MAB101T",
                    LTPJC = "4-0-0-0-8",
                    category = "PC",
                    classId = "C123",
                    slotVenue = "A1 | AB1-101",
                    facultyDetails = "Dr. X - MATH"
                )
            )
        )
        val out = AcademicMerge.upsertTimetable(base, "SEM1", res)
        val course = out.semesters.getValue("SEM1").courses.getValue("18MAB101T")
        assertEquals(listOf("A1"), course.slots)
        assertEquals("AB1-101", course.venue)
        assertEquals("C123", course.classId)
        assertEquals("4-0-0-0-8", course.credits)
        assertEquals("Dr. X (MATH)", course.faculty)
        assertNotNull(course.attendance)
    }

    @Test
    fun upsertTimetableUnionsSlotsWithoutDuplicates() {
        val base = AcademicMerge.upsertAttendance(AcademicData(), "SEM1", attendanceRes(items = listOf(attItem(slot = "A1+A2"))))
        val res = TimetableRes(
            success = true,
            courseInfo = listOf(
                TimetableCourseInfo(courseCode = "18MAB101T", slotVenue = "A2+A1 | AB1-101")
            )
        )
        val out = AcademicMerge.upsertTimetable(base, "SEM1", res)
        assertEquals(listOf("A1", "A2"), out.semesters.getValue("SEM1").courses.getValue("18MAB101T").slots)
    }

    @Test
    fun upsertTimetableDropsInvalidCourseCodes() {
        val res = TimetableRes(
            success = true,
            courseInfo = listOf(
                TimetableCourseInfo(courseCode = "(T)", course = "Garbage"),
                TimetableCourseInfo(courseCode = "", course = "No code"),
                TimetableCourseInfo(courseCode = "18CSC301J", course = "DBMS")
            )
        )
        val out = AcademicMerge.upsertTimetable(AcademicData(), "SEM1", res)
        assertEquals(setOf("18CSC301J"), out.semesters.getValue("SEM1").courses.keys)
    }

    @Test
    fun upsertTimetableCreatesCourseWhenAbsent() {
        val res = TimetableRes(
            success = true,
            courseInfo = listOf(
                TimetableCourseInfo(courseCode = "18CSC301J", course = "DBMS", slotVenue = "F1 | AB2-204")
            )
        )
        val out = AcademicMerge.upsertTimetable(AcademicData(), "SEM1", res)
        val course = out.semesters.getValue("SEM1").courses.getValue("18CSC301J")
        assertEquals("DBMS", course.courseTitle)
        assertEquals(listOf("F1"), course.slots)
    }

    // ── updateSemester identity ──

    @Test
    fun updateSemesterReturnsSameInstanceWhenUnchanged() {
        val academic = AcademicData(semesters = mapOf("SEM1" to SemesterData(semesterId = "SEM1")))
        val out = AcademicMerge.updateSemester(academic, "SEM1") { it }
        assertSame(academic, out)
    }
}
