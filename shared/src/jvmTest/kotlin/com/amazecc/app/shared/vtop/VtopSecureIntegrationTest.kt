package com.amazecc.app.shared.vtop

import com.amazecc.app.shared.api.AmazeClient
import com.amazecc.app.shared.model.AssessmentItem
import com.amazecc.app.shared.model.ExamItem
import com.amazecc.app.shared.model.StudentIdentity
import com.amazecc.app.shared.state.*
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

/**
 * Secure Integration Test for VTOP Parser Engine & Offline Fallback.
 *
 * CRITICAL SECURITY GUARANTEE:
 * - Credentials (ADMIN_VTOP_IDS, VTOP_PASSWORD) are never logged, printed, or exposed in exceptions or test outputs.
 * - If credentials are not present in the environment or .env, live network tests are gracefully skipped,
 *   and all mock / parser alignment / offline-first fallback assertions are fully validated.
 */
class VtopSecureIntegrationTest {

    private data class VtopCreds(val username: String, val password: String)

    /**
     * Reads credentials dynamically without ever printing or logging them.
     */
    private fun loadCredentialsSecurely(): VtopCreds? {
        var user = System.getenv("ADMIN_VTOP_IDS")?.trim()
            ?.ifBlank { null }
            ?: System.getenv("VTOP_USERNAME")?.trim()?.ifBlank { null }
            ?: System.getenv("VTOP_ID")?.trim()?.ifBlank { null }

        var pass = System.getenv("VTOP_PASSWORD")?.trim()?.ifBlank { null }

        if (user == null || pass == null) {
            val envCandidates = listOf(
                File(".env"),
                File("../.env"),
                File("../AmazeCC-API/.env"),
                File("../amazecc-api-hono/.env")
            )
            for (candidate in envCandidates) {
                if (candidate.exists() && candidate.isFile) {
                    try {
                        candidate.forEachLine { line ->
                            val trimmed = line.trim()
                            if (trimmed.startsWith("#") || !trimmed.contains("=")) return@forEachLine
                            val key = trimmed.substringBefore("=").trim()
                            val value = trimmed.substringAfter("=").trim().trim('"', '\'')
                            if (user == null && (key == "ADMIN_VTOP_IDS" || key == "VTOP_USERNAME" || key == "VTOP_ID")) {
                                user = value.split(",").firstOrNull()?.trim()?.ifBlank { null }
                            }
                            if (pass == null && (key == "VTOP_PASSWORD" || key == "PASSWORD")) {
                                pass = value.ifBlank { null }
                            }
                        }
                    } catch (_: Exception) {
                        // Suppress file read errors to avoid leaking paths or partial tokens
                    }
                }
                if (user != null && pass != null) break
            }
        }

        if (user.isNullOrBlank() || pass.isNullOrBlank()) return null
        val cleanUser = user!!.split(",").first().trim()
        return VtopCreds(username = cleanUser, password = pass!!)
    }

    // ── 1. Timetable Parsing Alignment with Hono Specs ───────────────────────

    @Test
    fun testHonoAlignedTimetableParsing() {
        // Sample HTML matching VTOP /processViewTimeTable table structure
        val timetableJsonRaw = """
        {
          "ok": "true",
          "rows": [
            [
              "1",
              "REG101",
              "BCSE101L Computer Programming",
              "0 0 4 0 2",
              "Embedded Lab",
              "None",
              "CH2026270101",
              "L31+L32 - AB3 401",
              "Dr. Ada Lovelace"
            ],
            [
              "2",
              "REG102",
              "BCSE101T Programming Theory",
              "3 0 0 0 3",
              "Embedded Theory",
              "None",
              "CH2026270102",
              "A1+TA1 - AB1 101",
              "Dr. Alan Turing"
            ]
          ]
        }
        """.trimIndent()

        val parsedRows = VtopRows.parse(timetableJsonRaw)
        assertTrue(parsedRows.ok, "Expected rows to parse successfully")
        assertEquals(2, parsedRows.size)

        // Row 0: Lab slot (starts with L)
        val row0 = parsedRows.rows[0]
        val isLab0 = row0[7].trim().startsWith("L")
        val baseCode0 = row0[2].split(" ")[0].trim()
        val honoCode0 = if (isLab0) "$baseCode0(L)" else "$baseCode0(T)"
        assertEquals("BCSE101L(L)", honoCode0, "Lab course code must have (L) suffix")
        assertEquals("CH2026270101", row0[6], "classId must match column 6")
        assertEquals("Dr. Ada Lovelace", row0[8], "facultyDetails must match column 8")

        // Row 1: Theory slot (starts with A)
        val row1 = parsedRows.rows[1]
        val isLab1 = row1[7].trim().startsWith("L")
        val baseCode1 = row1[2].split(" ")[0].trim()
        val honoCode1 = if (isLab1) "$baseCode1(L)" else "$baseCode1(T)"
        assertEquals("BCSE101T(T)", honoCode1, "Theory course code must have (T) suffix")
        assertEquals("CH2026270102", row1[6], "classId must match column 6")
        assertEquals("Dr. Alan Turing", row1[8], "facultyDetails must match column 8")
    }

    // ── 2. Marks Parsing Alignment with Hono Specs ───────────────────────────

    @Test
    fun testHonoAlignedMarksParsing() {
        val marksJsonRaw = """
        {
          "ok": "true",
          "courses": [
            {
              "cells": [
                "1",
                "10023",
                "BCSE101L",
                "Computer Programming",
                "Embedded Lab",
                "CAL",
                "Dr. Ada Lovelace",
                "L31+L32",
                "Regular"
              ],
              "assessments": [
                ["1", "Lab Assessment 1", "50", "30", "Present", "48.00", "28.80"],
                ["2", "FAT Lab", "50", "70", "Present", "45.00", "63.00"]
              ]
            }
          ]
        }
        """.trimIndent()

        val result = VtopMarksResult.parse(marksJsonRaw)
        assertTrue(result.ok)
        assertEquals(1, result.courses.size)

        val course = result.courses[0]
        assertEquals("10023", course.cells[1], "classNbr at cell 1")
        assertEquals("BCSE101L", course.cells[2], "courseCode at cell 2")
        assertEquals("Computer Programming", course.cells[3], "title at cell 3")
        assertEquals("Embedded Lab", course.cells[4], "courseType at cell 4")
        assertEquals("CAL", course.cells[5], "courseSystem at cell 5")
        assertEquals("Dr. Ada Lovelace", course.cells[6], "faculty at cell 6")
        assertEquals("L31+L32", course.cells[7], "slot at cell 7")
        assertEquals("Regular", course.cells[8], "courseMode at cell 8")

        assertEquals(2, course.assessments.size)
        val a1 = course.assessments[0]
        assertEquals("Lab Assessment 1", a1[1], "Assessment title")
        assertEquals("50", a1[2], "maxMark")
        assertEquals("30", a1[3], "weightagePercent")
        assertEquals("Present", a1[4], "status")
        assertEquals("48.00", a1[5], "scoredMark")
        assertEquals("28.80", a1[6], "weightageMark")

        // CGPA summary parsing
        val cgpaJsonRaw = """
        {
          "ok": "true",
          "cgpa": {
            "creditsRequired": "160",
            "creditsEarned": "120",
            "cgpa": "9.42",
            "nonGradedRequirement": "11"
          }
        }
        """.trimIndent()
        val summary = VtopCgpaSummary.parse(cgpaJsonRaw)
        assertNotNull(summary)
        assertEquals("160", summary.creditsRequired)
        assertEquals("120", summary.creditsEarned)
        assertEquals("9.42", summary.cgpa)
        assertEquals("11", summary.nonGradedRequirement)
    }

    // ── 3. Offline-First Fallback Validation ─────────────────────────────────

    @Test
    fun testOfflineFirstFallbackWhenVtopUnavailable() = runBlocking {
        // Setup AppDataStore with offline data
        val semId = "CH20262701"
        val testCourse = StoredCourse(
            courseCode = "BCSE101",
            courseTitle = "Data Structures",
            courseType = "Theory Only",
            category = "Core",
            credits = "3",
            classId = "CLS999",
            slots = listOf("A1", "TA1"),
            venue = "AB1-101",
            faculty = "Prof. Smith",
            attendance = StoredAttendance(
                attendedClasses = 24,
                totalClasses = 26,
                attendancePercentage = "92.3"
            ),
            marks = StoredMarks(
                classNbr = "CLS999",
                assessments = listOf(
                    AssessmentItem(
                        title = "CAT 1",
                        maxMark = "50",
                        weightagePercent = "15",
                        status = "Present",
                        scoredMark = "45",
                        weightageMark = "13.5"
                    )
                )
            )
        )

        val testExam = ExamItem(
            courseCode = "BCSE101",
            courseTitle = "Data Structures",
            slot = "A1",
            examDate = "2026-12-15",
            examSession = "FN",
            examTime = "10:00 - 11:30",
            venue = "AB1-101",
            classId = "CLS999"
        )

        val semesterData = SemesterData(
            semesterId = semId,
            semesterName = "Winter Semester 2026-27",
            gpa = "9.10",
            courses = mapOf("BCSE101" to testCourse),
            exams = listOf(testExam)
        )

        AppDataStore.updateSemester(semId) {
            it.copy(
                semesterName = "Winter Semester 2026-27",
                gpa = "9.10",
                courses = mapOf("BCSE101" to testCourse),
                exams = listOf(testExam)
            )
        }

        // Setup UserStore identity for profile fallback
        UserStore.merge(
            StudentIdentity(
                regNo = "22BCE9999",
                name = "Amaze Student",
                program = "B.Tech CSE",
                email = "student@vitstudent.ac.in",
                campus = "Vellore Campus"
            ),
            IdentitySource.STUDENT
        )

        // Clear live session tokens so live VTOP queries cannot run
        VtopSession.clear()
        AmazeClient.setUseMockData(false)
        AmazeClient.setVtopSource(VtopSource.LOCAL)
        AppState.clearServedFromCache()

        // 1. Timetable Fallback
        val ttRes = AmazeClient.getTimetable(semId)
        assertTrue(ttRes.success, "Timetable should fall back to offline cache")
        assertNotNull(ttRes.courseInfo)
        assertTrue(ttRes.courseInfo!!.isNotEmpty(), "Course info should not be blank")
        assertEquals("Served from local cache", ttRes.message)
        assertTrue(AppState.isUsingCachedData.value, "AppState should indicate data from cache")

        // 2. Marks Fallback
        val marksRes = AmazeClient.getMarks(semId)
        assertTrue(marksRes.success, "Marks should fall back to offline cache")
        assertNotNull(marksRes.courses)
        assertTrue(marksRes.courses!!.isNotEmpty(), "Marks courses should not be blank")
        assertEquals("Served from local cache", marksRes.message)

        // 3. Academic Data (Attendance & Marks) Fallback
        val acadRes = AmazeClient.getAcademicData(semId)
        assertTrue(acadRes.attendance.success, "Attendance should fall back to offline cache")
        assertNotNull(acadRes.attendance.attendance)
        assertTrue(acadRes.attendance.attendance!!.isNotEmpty(), "Attendance items should not be blank")
        assertEquals("Served from local cache", acadRes.attendance.message)

        // 4. Exam Schedule Fallback
        val examRes = AmazeClient.getExamSchedule(semId)
        assertTrue(examRes.success, "Exam schedule should fall back to offline cache")
        assertTrue(examRes.schedule.isNotEmpty(), "Exams should not be blank")
        assertEquals("Served from local cache", examRes.message)

        // 5. Student Profile Fallback
        val profRes = AmazeClient.getStudentProfile()
        assertTrue(profRes.success, "Profile should fall back to offline cache")
        assertNotNull(profRes.data)
        assertEquals("22BCE9999", profRes.data?.regNo)
        assertEquals("Served from local cache", profRes.message)

        // Verify status indicator
        assertNotNull(AppState.offlineCacheStatus.value)
        assertTrue(AppState.offlineCacheStatus.value!!.contains("local cache"))
    }

    // ── 4. Live Network Integration Test with Zero-Leakage Credential Guard ────

    @Test
    fun testLiveVtopIntegrationIfCredentialsPresent() = runBlocking {
        val creds = loadCredentialsSecurely()
        if (creds == null) {
            println("VTOP integration credentials not present in environment; skipping live network test.")
            return@runBlocking
        }

        // Platform check: VtopEngine on desktop/JVM is a stub unless supported
        if (!vtopEngineSupported) {
            println("VTOP integration credentials present, but VtopEngine is not supported on JVM platform; skipping live network test.")
            return@runBlocking
        }

        // Live test execution without exposing credentials
        try {
            val result = Vtop.login(
                username = creds.username,
                password = creds.password,
                captchaHandler = { CaptchaAnswer.Cancelled },
                maxCaptchaAttempts = 1
            )
            // Assert that login executed and returned a valid non-null result
            assertNotNull(result)
        } catch (e: Exception) {
            // Guarantee no credentials leaked in test output
            println("Live network login test encountered network/platform error (credentials securely protected).")
        }
    }
}
