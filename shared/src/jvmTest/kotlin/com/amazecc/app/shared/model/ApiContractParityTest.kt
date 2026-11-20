package com.amazecc.app.shared.model

import com.amazecc.app.shared.state.AcademicDerivers
import com.amazecc.app.shared.state.StoredAttendance
import com.amazecc.app.shared.state.StoredCourse
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Guards the Kotlin DTO mirrors against the authoritative TS contracts under
 * ../amazecc-api-hono/src/types/data (attendance.ts, marks.ts, ...). Payloads below are written
 * to those declarations, including the nullable and number cases plain serialization rejects.
 */
class ApiContractParityTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `attendance contract fields survive decode`() {
        val payload = """
            {
              "semesterId": "CH20262701",
              "attendance": [
                {
                  "slNo": "1",
                  "courseCode": "BACSE102",
                  "courseTitle": "Problem Solving Using Java",
                  "courseType": null,
                  "slotName": "L31+L32",
                  "faculty": null,
                  "registrationDate": "08-07-2026 08:50",
                  "attendanceDate": "09-07-2026",
                  "attendedClasses": 32,
                  "totalClasses": 36,
                  "attendancePercentage": null,
                  "viewLink": "View",
                  "classId": "CSE1001",
                  "slotVenue": "SJT-301"
                }
              ],
              "error": null
            }
        """.trimIndent()

        val res = json.decodeFromString<AttendanceRes>(payload)
        val item = res.attendance!!.single()

        assertEquals("1", item.slNo)
        assertEquals("08-07-2026 08:50", item.registrationDate)
        assertEquals("09-07-2026", item.attendanceDate)
        assertEquals("CSE1001", item.classId)
        assertEquals("SJT-301", item.slotVenue)
        assertEquals("", item.courseType)   // TS null -> UI-safe empty string
        assertEquals("", item.faculty)
        assertEquals("", item.attendancePercentage)
        assertEquals(32, item.attendedClasses)
        assertEquals(36, item.totalClasses)
    }

    @Test
    fun `marks contract with numeric credits and contract-only fields decodes`() {
        val payload = """
            {
              "success": true,
              "marks": [
                {
                  "slNo": "1",
                  "classNbr": "12345",
                  "courseCode": "BACS3001",
                  "credits": 4,
                  "courseTitle": "Operating Systems",
                  "courseType": "Theory Only",
                  "courseSystem": "2022",
                  "faculty": "DR XYZ",
                  "slot": "A1+TA1",
                  "courseMode": "Regular",
                  "assessments": [
                    {
                      "slNo": "1",
                      "title": "CAT1",
                      "maxMark": "50",
                      "weightagePercent": "20",
                      "status": "Completed",
                      "scoredMark": "42",
                      "weightageMark": "16.8"
                    }
                  ]
                }
              ]
            }
        """.trimIndent()

        val course = json.decodeFromString<MarksRes>(payload).marks.single()

        assertEquals("4", course.credits)   // marks.ts: number; Kotlin: String?
        assertEquals("1", course.slNo)
        assertEquals("Regular", course.courseMode)
        assertEquals("1", course.assessments.single().slNo)
        assertEquals("42", course.assessments.single().scoredMark)
    }

    @Test
    fun `marks contract with string credits still decodes`() {
        val payload = """
            { "marks": [ { "courseCode": "BACS3001", "credits": "4" } ] }
        """.trimIndent()

        assertEquals("4", json.decodeFromString<MarksRes>(payload).marks.single().credits)
    }

    @Test
    fun `course percentage parser strips percent suffix and falls back to counts`() {
        val printed = StoredCourse(
            attendance = StoredAttendance(
                attendedClasses = 32, totalClasses = 36, attendancePercentage = "89.50%"
            )
        )
        assertEquals(89.5, AcademicDerivers.percentOf(printed))

        val fromCounts = StoredCourse(
            attendance = StoredAttendance(attendedClasses = 32, totalClasses = 36)
        )
        assertEquals(88.89, AcademicDerivers.percentOf(fromCounts))
    }

    @Test
    fun `explicit null counts map to UI-safe zero`() {
        val payload = """
            {
              "attendance": [
                {
                  "courseCode": "BACSE102",
                  "attendanceDate": null,
                  "attendedClasses": null,
                  "totalClasses": null
                }
              ]
            }
        """.trimIndent()

        val item = json.decodeFromString<AttendanceRes>(payload).attendance!!.single()
        assertEquals(0, item.attendedClasses)
        assertEquals(0, item.totalClasses)
        assertEquals(null, item.attendanceDate)
    }
}
