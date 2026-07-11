package com.amazecc.app.shared.api

import com.amazecc.app.shared.model.*
import com.amazecc.app.shared.repository.SessionManager
import io.ktor.client.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.Serializable

@Serializable
data class LoginRequest(val username: String, val password: String)

data class AcademicSyncResult(
    val attendance: AttendanceRes,
    val marks: MarksRes? = null
)

@Serializable
private data class AttendanceSyncResponse(
    val success: Boolean = true,
    val attRes: AttendanceRes? = null,
    val marksRes: MarksRes? = null,
    val semester: String? = null,
    val semesterId: String? = null,
    val attendance: List<AttendanceItem> = emptyList(),
    val error: String? = null,
    val message: String? = null
)

object AmazeClient {
    private var baseUrl = "https://api.amazecc.com"
    private var useMockData = false // Toggle for offline testing

    fun setBaseUrl(url: String) {
        baseUrl = url
    }

    fun setUseMockData(enable: Boolean) {
        useMockData = enable
    }

    private val jsonConfig = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        prettyPrint = true
        isLenient = true
    }

    private val httpClient = HttpClient {
        install(ContentNegotiation) {
            json(jsonConfig)
        }
    }

    suspend fun login(username: String, password: String): LoginResponse {
        if (useMockData || username.lowercase() == "demo" || username.uppercase() == "DEMO123") {
            return LoginResponse(
                success = true,
                message = "Login successful (Demo Mode)!",
                cookies = "vtop_session_cookie=demo_session_123; csrf_token=demo_csrf_abc",
                csrf = "demo_csrf_abc",
                authorizedID = "DEMO123"
            )
        }

        return try {
            val response: HttpResponse = httpClient.post("$baseUrl/api/login") {
                contentType(ContentType.Application.Json)
                setBody(LoginRequest(username, password))
            }
            if (response.status == HttpStatusCode.OK) {
                jsonConfig.decodeFromString(response.bodyAsText())
            } else {
                LoginResponse(success = false, message = "Server returned status ${response.status}", error = "HTTP ${response.status}")
            }
        } catch (e: Exception) {
            LoginResponse(success = false, message = "Network error: ${e.message}", error = e.toString())
        }
    }

    // Helper for POST requests carrying cookies, authorizedID, and csrf
    private suspend inline fun <reified T> postAuthorized(endpoint: String, extraParams: Map<String, String> = emptyMap()): T? {
        val cookies = SessionManager.cookies.value ?: return null
        val authorizedID = SessionManager.authorizedID.value ?: return null
        val csrf = SessionManager.csrf.value ?: return null

        val response: HttpResponse = httpClient.post("$baseUrl/api/$endpoint") {
            contentType(ContentType.Application.Json)
            setBody(buildJsonObject {
                put("cookies", cookies)
                put("authorizedID", authorizedID)
                put("csrf", csrf)
                extraParams.forEach { (k, v) -> put(k, v) }
            })
        }
        return if (response.status == HttpStatusCode.OK) {
            jsonConfig.decodeFromString<T>(response.bodyAsText())
        } else {
            null
        }
    }

    suspend fun getAttendance(semesterId: String? = null): AttendanceRes {
        return getAcademicData(semesterId).attendance
    }

    suspend fun getAcademicData(semesterId: String? = null): AcademicSyncResult {
        if (useMockData || SessionManager.authorizedID.value == "DEMO123") {
            val attendance = AttendanceRes(
                success = true,
                semesterId = semesterId ?: "CH20252601",
                attendance = listOf(
                    AttendanceItem(slNo = "1", courseCode = "CSE1001", courseTitle = "Software Engineering", courseType = "Theory", slotName = "A1+TA1", faculty = "Dr. Amit Kumar", attendedClasses = 26, totalClasses = 30, attendancePercentage = "86", slotVenue = "SJT-402", credits = "3", category = "PC"),
                    AttendanceItem(slNo = "2", courseCode = "CSE2002", courseTitle = "Database Management Systems", courseType = "Theory", slotName = "B1+TB1", faculty = "Dr. Rajeev Sen", attendedClasses = 14, totalClasses = 20, attendancePercentage = "70", slotVenue = "SJT-503", credits = "4", category = "PC"),
                    AttendanceItem(slNo = "3", courseCode = "CSE3001", courseTitle = "Artificial Intelligence", courseType = "Embedded Lab", slotName = "L1+L2", faculty = "Prof. Priya Nair", attendedClasses = 10, totalClasses = 10, attendancePercentage = "100", slotVenue = "TT-204", credits = "4", category = "PE"),
                    AttendanceItem(slNo = "4", courseCode = "MAT2001", courseTitle = "Differential Equations", courseType = "Theory", slotName = "C1+TC1", faculty = "Dr. Sarah John", attendedClasses = 16, totalClasses = 24, attendancePercentage = "66", slotVenue = "SJT-612", credits = "3", category = "UC")
                )
            )
            return AcademicSyncResult(attendance = attendance, marks = getMarks(semesterId))
        }
        return try {
            val params = if (semesterId != null) mapOf("semesterId" to semesterId) else emptyMap()
            val response = postAuthorized<AttendanceSyncResponse>("attendance", params)
            if (response == null) {
                AcademicSyncResult(AttendanceRes(success = false, message = "Empty response"))
            } else {
                val attendance = response.attRes ?: AttendanceRes(
                    success = response.success,
                    semester = response.semester,
                    semesterId = response.semesterId,
                    attendance = response.attendance,
                    error = response.error,
                    message = response.message
                )
                AcademicSyncResult(
                    attendance = attendance.copy(success = attendance.success && attendance.error == null),
                    marks = response.marksRes
                )
            }
        } catch (e: Exception) {
            AcademicSyncResult(AttendanceRes(success = false, message = e.message, error = e.toString()))
        }
    }

    suspend fun getTimetable(semesterId: String? = null): TimetableRes {
        if (useMockData || SessionManager.authorizedID.value == "DEMO123") {
            return TimetableRes(
                success = true,
                semesterId = semesterId ?: "CH20252601",
                courseInfo = listOf(
                    CourseItem(slNo = "1", course = "CSE1001 - Software Engineering", courseCode = "CSE1001(T)", LTPJC = "3 0 0 3 0", category = "PC", classId = "1024", slotVenue = "A1+TA1 / SJT-402", facultyDetails = "Dr. Amit Kumar"),
                    CourseItem(slNo = "2", course = "CSE2002 - Database Management Systems", courseCode = "CSE2002(T)", LTPJC = "3 0 2 4 0", category = "PC", classId = "1056", slotVenue = "B1+TB1 / SJT-503", facultyDetails = "Dr. Rajeev Sen"),
                    CourseItem(slNo = "3", course = "CSE3001 - Artificial Intelligence Lab", courseCode = "CSE3001(L)", LTPJC = "0 0 4 2 0", category = "PE", classId = "1188", slotVenue = "L1+L2 / TT-204", facultyDetails = "Prof. Priya Nair")
                )
            )
        }
        return try {
            val params = if (semesterId != null) mapOf("semesterId" to semesterId) else emptyMap()
            postAuthorized<TimetableRes>("timetable", params) ?: TimetableRes(success = false, message = "Empty response")
        } catch (e: Exception) {
            TimetableRes(success = false, message = e.message, error = e.toString())
        }
    }

    suspend fun getMarks(semesterId: String? = null): MarksRes {
        if (useMockData || SessionManager.authorizedID.value == "DEMO123") {
            return MarksRes(
                success = true,
                marks = listOf(
                    MarksCourseItem(
                        slNo = "1", classNbr = "1024", courseCode = "CSE1001", credits = 3.0, courseTitle = "Software Engineering", courseType = "Theory", faculty = "Dr. Amit Kumar", slot = "A1",
                        assessments = listOf(
                            AssessmentItem("1", "Continuous Assessment Test 1", "50", "15", "Completed", "42", "12.6"),
                            AssessmentItem("2", "Continuous Assessment Test 2", "50", "15", "Completed", "45", "13.5"),
                            AssessmentItem("3", "Digital Assignment 1", "10", "10", "Completed", "9", "9.0")
                        )
                    ),
                    MarksCourseItem(
                        slNo = "2", classNbr = "1056", courseCode = "CSE2002", credits = 4.0, courseTitle = "Database Management Systems", courseType = "Theory", faculty = "Dr. Rajeev Sen", slot = "B1",
                        assessments = listOf(
                            AssessmentItem("1", "Continuous Assessment Test 1", "50", "15", "Completed", "35", "10.5"),
                            AssessmentItem("2", "Continuous Assessment Test 2", "50", "15", "Completed", "38", "11.4")
                        )
                    )
                ),
                cgpa = CGPAResult("120", "84", "8.54", "Completed")
            )
        }
        return try {
            val params = if (semesterId != null) mapOf("semesterId" to semesterId) else emptyMap()
            postAuthorized<MarksRes>("marks", params) ?: MarksRes(success = false, message = "Empty response")
        } catch (e: Exception) {
            MarksRes(success = false, message = e.message, error = e.toString())
        }
    }

    suspend fun getAllGrades(): AllGradesRes {
        if (useMockData || SessionManager.authorizedID.value == "DEMO123") {
            return AllGradesRes(
                success = true,
                grades = mapOf(
                    "CH20242501" to SemesterGradeResult(
                        gpa = "8.42",
                        grades = listOf(
                            GradeItem("1", "CSE1001", "Software Engineering", "Theory", "86", "A"),
                            GradeItem("2", "MAT2001", "Differential Equations", "Theory", "74", "B")
                        )
                    ),
                    "CH20242505" to SemesterGradeResult(
                        gpa = "8.75",
                        grades = listOf(
                            GradeItem("1", "CSE2002", "Database Management Systems", "Theory", "92", "S"),
                            GradeItem("2", "CSE3001", "Artificial Intelligence", "Theory", "88", "A")
                        )
                    )
                )
            )
        }
        return try {
            postAuthorized<AllGradesRes>("all-grades") ?: AllGradesRes(success = false, message = "Empty response")
        } catch (e: Exception) {
            AllGradesRes(success = false, message = e.message, error = e.toString())
        }
    }

    suspend fun getHostelDetails(): HostelDetails {
        if (useMockData || SessionManager.authorizedID.value == "DEMO123") {
            return HostelDetails(
                success = true,
                gender = "MALE",
                isHosteller = true,
                blockName = "Q-Block",
                roomNo = "612",
                messInfo = "Special Veg Mess (Caterer: CRCL)"
            )
        }
        return try {
            postAuthorized<HostelDetails>("hostel") ?: HostelDetails(success = false, message = "Empty response")
        } catch (e: Exception) {
            HostelDetails(success = false, message = e.message, error = e.toString())
        }
    }

    suspend fun getHostelLeaves(): HostelLeaveRes {
        if (useMockData || SessionManager.authorizedID.value == "DEMO123") {
            return HostelLeaveRes(
                success = true,
                leaves = listOf(
                    LeaveItem("LV-9810", "Home (Delhi)", "Family function", "Home Leave", "2026-07-15", "2026-07-20", "APPROVED", "Ensure return by due time"),
                    LeaveItem("LV-9541", "Local (Vellore)", "Shopping", "Outing", "2026-06-28 10:00 AM", "2026-06-28 06:00 PM", "COMPLETED", "Returned on time")
                )
            )
        }
        return try {
            postAuthorized<HostelLeaveRes>("hostel-leave") ?: HostelLeaveRes(success = false, message = "Empty response")
        } catch (e: Exception) {
            HostelLeaveRes(success = false, message = e.message, error = e.toString())
        }
    }

    suspend fun getExamSchedule(): ExamScheduleRes {
        if (useMockData || SessionManager.authorizedID.value == "DEMO123") {
            return ExamScheduleRes(
                success = true,
                schedule = mapOf(
                    "Fall Semester 2025-26" to listOf(
                        ExamItem("CSE1001", "Software Engineering", "1024", "A1", "2026-09-12", "FN", "08:30 AM", "09:00 AM - 12:00 PM", "SJT-401", "Row 3, Seat A", "A-32"),
                        ExamItem("CSE2002", "Database Management Systems", "1056", "B1", "2026-09-14", "AN", "01:30 PM", "02:00 PM - 05:00 PM", "TT-102", "Row 1, Seat C", "C-08")
                    )
                )
            )
        }
        return try {
            postAuthorized<ExamScheduleRes>("arrear-schedule") ?: ExamScheduleRes(success = false, message = "Empty response")
        } catch (e: Exception) {
            ExamScheduleRes(success = false, message = e.message, error = e.toString())
        }
    }

    suspend fun getCalendar(): CalendarRes {
        if (useMockData || SessionManager.authorizedID.value == "DEMO123") {
            return CalendarRes(
                success = true,
                months = listOf(
                    CalendarMonth(
                        month = "July 2026",
                        days = listOf(
                            CalendarDay(1, listOf(CalendarEvent("Instructional Day", "Day Order: Monday (Unit Test starting)"))),
                            CalendarDay(15, listOf(CalendarEvent("Holiday", "College Foundation Day", "#ef4444"))),
                            CalendarDay(20, listOf(CalendarEvent("Other", "Course Registration starts")))
                        )
                    )
                )
            )
        }
        return try {
            postAuthorized<CalendarRes>("calendar", mapOf("type" to "ALL")) ?: CalendarRes(success = false, message = "Empty response")
        } catch (e: Exception) {
            CalendarRes(success = false, message = e.message, error = e.toString())
        }
    }

    suspend fun getPayments(): PaymentsRes {
        if (useMockData || SessionManager.authorizedID.value == "DEMO123") {
            return PaymentsRes(
                success = true,
                payments = listOf(
                    PaymentItem("BILL-4412", "Academic Tuition Fees 2026-27", "Rs. 1,98,000", "2026-06-15", "PAID", "2026-06-10", "REC-99120"),
                    PaymentItem("BILL-4501", "Hostel & Mess Booking Q-Block", "Rs. 1,12,000", "2026-06-30", "PAID", "2026-06-25", "REC-99881")
                ),
                walletBalance = "Rs. 2,450.00"
            )
        }
        return try {
            postAuthorized<PaymentsRes>("payments") ?: PaymentsRes(success = false, message = "Empty response")
        } catch (e: Exception) {
            PaymentsRes(success = false, message = e.message, error = e.toString())
        }
    }

    suspend fun getLibrary(): LibraryRes {
        if (useMockData || SessionManager.authorizedID.value == "DEMO123") {
            return LibraryRes(
                success = true,
                booksIssued = listOf(
                    BookItem("BK-90123", "Introduction to Algorithms", "Thomas H. Cormen", "2026-06-10", "2026-06-25", "Rs. 0.00"),
                    BookItem("BK-90224", "Database System Concepts", "Abraham Silberschatz", "2026-07-01", "2026-07-16", "Rs. 0.00")
                )
            )
        }
        return try {
            postAuthorized<LibraryRes>("library-due") ?: LibraryRes(success = false, message = "Empty response")
        } catch (e: Exception) {
            LibraryRes(success = false, message = e.message, error = e.toString())
        }
    }

    suspend fun searchLibrary(query: String): LibraryRes {
        if (useMockData || SessionManager.authorizedID.value == "DEMO123") {
            return LibraryRes(
                success = true,
                searchResults = listOf(
                    BookItem("BK-100", "Computer Networking: A Top-Down Approach", "James F. Kurose"),
                    BookItem("BK-200", "Design Patterns: Elements of Reusable Object-Oriented Software", "Erich Gamma")
                )
            )
        }
        return try {
            val response: HttpResponse = httpClient.get("$baseUrl/api/koha/search?q=${query.encodeURLParameter()}")
            jsonConfig.decodeFromString(response.bodyAsText())
        } catch (e: Exception) {
            LibraryRes(success = false, message = e.message, error = e.toString())
        }
    }

    suspend fun getTransport(): TransportRes {
        if (useMockData || SessionManager.authorizedID.value == "DEMO123") {
            return TransportRes(
                success = true,
                buses = listOf(
                    BusItem("R-01", "Katpadi Junction - VIT Campus", "08:15 AM", "K. Raman", "+91 9843210982"),
                    BusItem("R-12", "Vellore New Bus Stand - VIT Campus", "08:00 AM", "S. Kumar", "+91 9442190831")
                ),
                dayBoarderStatus = "APPROVED (Bus Pass Active)"
            )
        }
        return try {
            postAuthorized<TransportRes>("transport") ?: TransportRes(success = false, message = "Empty response")
        } catch (e: Exception) {
            TransportRes(success = false, message = e.message, error = e.toString())
        }
    }

    suspend fun getLMSAssignments(): LMSRes {
        if (useMockData || SessionManager.authorizedID.value == "DEMO123") {
            return LMSRes(
                success = true,
                assignments = listOf(
                    LMSAssignment("ASM-102", "CSE1001", "Design Pattern Implementation", "10", "2026-07-15", "Pending"),
                    LMSAssignment("ASM-098", "CSE2002", "SQL Queries Lab Assignment", "10", "2026-07-08", "Submitted", "9.5")
                )
            )
        }
        return try {
            postAuthorized<LMSRes>("lms-data") ?: LMSRes(success = false, message = "Empty response")
        } catch (e: Exception) {
            LMSRes(success = false, message = e.message, error = e.toString())
        }
    }
    suspend fun getQBankQuestions(courseCode: String): QBankRes {
        if (useMockData || SessionManager.authorizedID.value == "DEMO123") {
            return QBankRes(
                success = true,
                data = listOf(
                    QBankQuestion(
                        question_id = "Q-1",
                        question_text = "What is encapsulation?",
                        question_type = "Descriptive",
                        marks = 5,
                        topic_name = "OOPs",
                        exam_semester = "Fall 2025"
                    )
                )
            )
        }
        return try {
            val response: HttpResponse = httpClient.get("$baseUrl/api/qbank/questions?course=${courseCode.encodeURLParameter()}")
            if (response.status == HttpStatusCode.OK) {
                jsonConfig.decodeFromString(response.bodyAsText())
            } else {
                QBankRes(success = false, message = "HTTP ${response.status}")
            }
        } catch (e: Exception) {
            QBankRes(success = false, message = e.message, error = e.toString())
        }
    }

    suspend fun getEventsProfile(): EventHubRes {
        if (useMockData || SessionManager.authorizedID.value == "DEMO123") {
            return EventHubRes(
                success = true,
                events = listOf(
                    EventHubEvent(
                        eid = "EV-901",
                        title = "Hackathon 2026",
                        type = "Technical",
                        date = "2026-08-15",
                        location = "Anna Auditorium",
                        price = "Free"
                    )
                )
            )
        }
        return try {
            val response: HttpResponse = httpClient.get("$baseUrl/api/events/profile") // Depending on actual API it might need postAuthorized
            if (response.status == HttpStatusCode.OK) {
                jsonConfig.decodeFromString(response.bodyAsText())
            } else {
                EventHubRes(success = false, message = "HTTP ${response.status}")
            }
        } catch (e: Exception) {
            EventHubRes(success = false, message = e.message, error = e.toString())
        }
    }

    suspend fun getClubsDetails(): ClubsRes {
        if (useMockData || SessionManager.authorizedID.value == "DEMO123") {
            return ClubsRes(
                success = true,
                clubs = listOf(
                    ClubItem(
                        id = "CL-1",
                        name = "Computer Society of India (CSI)",
                        description = "Technical Club"
                    )
                )
            )
        }
        return try {
            val response: HttpResponse = httpClient.get("$baseUrl/api/clubs/details")
            if (response.status == HttpStatusCode.OK) {
                jsonConfig.decodeFromString(response.bodyAsText())
            } else {
                ClubsRes(success = false, message = "HTTP ${response.status}")
            }
        } catch (e: Exception) {
            ClubsRes(success = false, message = e.message, error = e.toString())
        }
    }
}
