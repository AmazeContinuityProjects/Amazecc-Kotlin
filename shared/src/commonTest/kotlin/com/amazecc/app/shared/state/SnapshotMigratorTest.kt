package com.amazecc.app.shared.state

import com.amazecc.app.shared.model.AttendanceItem
import com.amazecc.app.shared.model.AttendanceRes
import com.amazecc.app.shared.model.ExamItem
import com.amazecc.app.shared.model.ExamScheduleRes
import com.amazecc.app.shared.model.MarksCourseItem
import com.amazecc.app.shared.model.MarksRes
import com.amazecc.app.shared.model.SemesterGradeResult
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SnapshotMigratorTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun attItem(code: String) = AttendanceItem(
        courseCode = code,
        courseTitle = "Course $code",
        courseType = "Theory",
        slotName = "A1",
        attendancePercentage = "80"
    )

    private fun attRes(semesterId: String?, code: String) =
        AttendanceRes(semesterId = semesterId, attendance = listOf(attItem(code)))

    private fun marksRes(code: String) = MarksRes(
        success = true,
        courses = listOf(MarksCourseItem(courseCode = code, courseTitle = "Course $code", slot = "A1"))
    )

    private fun examRes(code: String) = ExamScheduleRes(
        rawScheduleUpper = mapOf(code to listOf(ExamItem(courseCode = code, courseTitle = "Course $code", examDate = "2026-12-01")))
    )

    // ── per-semester fields land in their own semester ──

    @Test
    fun v1PerSemesterFieldsLandInMatchingSemesters() {
        val legacy = LegacyAppDataSnapshot(
            allSemesterAttendance = mapOf("SEM1" to attRes("SEM1", "18MAB101T")),
            allSemesterMarks = mapOf("SEM2" to marksRes("18CSC301J")),
            allSemesterExams = mapOf("SEM3" to examRes("18CSC302J")),
            allGrades = com.amazecc.app.shared.model.AllGradesRes(
                grades = mapOf("SEM4" to SemesterGradeResult(gpa = "9.2", grades = emptyList()))
            )
        )
        val v2 = SnapshotMigrator.toV2(legacy)
        assertEquals(4, v2.academic.semesters.size)
        assertNotNull(v2.academic.semesters["SEM1"]?.courses?.get("18MAB101T")?.attendance)
        assertNotNull(v2.academic.semesters["SEM2"]?.courses?.get("18CSC301J")?.marks)
        assertEquals(1, v2.academic.semesters["SEM3"]?.exams?.size)
        assertEquals("9.2", v2.academic.semesters["SEM4"]?.gpa)
    }

    // ── rule 1: mirror byte-equal to an allSemester* entry is skipped ──

    @Test
    fun mirrorEqualToPerSemesterEntryIsSkipped() {
        val dup = attRes("SEM1", "18MAB101T")
        val legacy = LegacyAppDataSnapshot(
            allSemesterAttendance = mapOf("SEM1" to dup),
            attendance = dup
        )
        val v2 = SnapshotMigrator.toV2(legacy)
        assertEquals(1, v2.academic.semesters.size)
        assertEquals(setOf("SEM1"), v2.academic.semesters.keys)
    }

    // ── rule 2: mirror with recoverable semesterId lands there ──

    @Test
    fun mirrorWithRecoverableSemesterIdLandsInThatSemester() {
        val mirror = attRes("SEM2", "18CSC301J").copy(
            attendance = listOf(attItem("18CSC301J").copy(attendancePercentage = "95"))
        )
        val legacy = LegacyAppDataSnapshot(
            allSemesterAttendance = mapOf(
                "SEM1" to attRes("SEM1", "18MAB101T"),
                "SEM2" to attRes("SEM2", "18CSC301J")
            ),
            attendance = mirror // not byte-equal to any entry, carries its own id
        )
        val v2 = SnapshotMigrator.toV2(legacy)
        assertEquals(setOf("SEM1", "SEM2"), v2.academic.semesters.keys)
        assertEquals("95", v2.academic.semesters["SEM2"]?.courses?.get("18CSC301J")?.attendance?.attendancePercentage)
        assertNull(v2.academic.semesters[SnapshotMigrator.FALLBACK_SEMESTER])
    }

    // ── rule 3: exactly one semester holding the domain → mirror lands there ──

    @Test
    fun mirrorWithoutSemesterIdLandsInOnlySemesterWithThatDomain() {
        val legacy = LegacyAppDataSnapshot(
            allSemesterMarks = mapOf("SEM2" to marksRes("18CSC301J")),
            marks = marksRes("18CSC302J") // no allSemesterMarks entry equal to it; no semesterId; exactly one sem has marks
        )
        val v2 = SnapshotMigrator.toV2(legacy)
        val marksSem = v2.academic.semesters.values.first { it.courses.values.any { c -> c.marks != null } }
        assertEquals("SEM2", marksSem.semesterId)
        assertNotNull(marksSem.courses["18CSC302J"]?.marks)
        assertNull(v2.academic.semesters[SnapshotMigrator.FALLBACK_SEMESTER])
    }

    // ── rule 4: ambiguous mirror → __v1_mirror fallback ──

    @Test
    fun ambiguousMirrorLandsInFallbackSemester() {
        val legacy = LegacyAppDataSnapshot(
            allSemesterAttendance = mapOf(
                "SEM1" to attRes("SEM1", "18MAB101T"),
                "SEM2" to attRes("SEM2", "18CSC301J")
            ),
            marks = marksRes("18CSC302J") // no id, two sems with data → ambiguous
        )
        val v2 = SnapshotMigrator.toV2(legacy)
        val fallback = v2.academic.semesters[SnapshotMigrator.FALLBACK_SEMESTER]
        assertNotNull(fallback)
        assertNotNull(fallback.courses["18CSC302J"]?.marks)
    }

    // ── null per-semester entries are skipped ──

    @Test
    fun nullPerSemesterEntriesAreSkipped() {
        val legacy = LegacyAppDataSnapshot(
            allSemesterAttendance = mapOf("SEM1" to null, "SEM2" to attRes("SEM2", "18CSC301J")),
            allSemesterMarks = mapOf("SEM1" to marksRes("18MAB101T"))
        )
        val v2 = SnapshotMigrator.toV2(legacy)
        assertEquals(setOf("SEM1", "SEM2"), v2.academic.semesters.keys)
        assertNull(v2.academic.semesters["SEM1"]?.courses?.get("18CSC301J"))
    }

    // ── empty legacy → empty v2 ──

    @Test
    fun emptyLegacyProducesEmptyAcademic() {
        val v2 = SnapshotMigrator.toV2(LegacyAppDataSnapshot())
        assertEquals(2, v2.schemaVersion)
        assertTrue(v2.academic.semesters.isEmpty())
    }

    // ── v2 round-trip through JSON ──

    @Test
    fun v2RoundTripThroughJson() {
        val original = AppDataSnapshot(
            schemaVersion = 2,
            academic = AcademicData(
                semesters = mapOf(
                    "SEM1" to SemesterData(
                        semesterId = "SEM1",
                        semesterName = "Semester 1",
                        gpa = "8.5",
                        courses = mapOf(
                            "18MAB101T" to StoredCourse(
                                courseCode = "18MAB101T",
                                courseTitle = "Calculus",
                                courseType = "Theory",
                                credits = "4",
                                slots = listOf("A1"),
                                venue = "AB1-101",
                                attendance = StoredAttendance(
                                    attendedClasses = 34,
                                    totalClasses = 40,
                                    attendancePercentage = "85"
                                )
                            )
                        ),
                        exams = listOf(ExamItem(courseCode = "18MAB101T", examDate = "2026-12-01"))
                    )
                )
            )
        )
        val decoded = json.decodeFromString<AppDataSnapshot>(json.encodeToString(original))
        assertEquals(original, decoded)
    }
}
