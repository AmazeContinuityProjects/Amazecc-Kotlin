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
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.Serializable
import com.amazecc.app.shared.utils.AnalyzeCalendar

@Serializable
data class LoginRequest(val username: String, val password: String)

@Serializable
data class MoodleLoginRequest(val username: String, val pass: String)

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
    var baseUrl = "https://api.amazecc.com"
    private var useMockData = false // Toggle for offline testing


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

    @Suppress("unused")
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
                        slNo = "1", classNbr = "1024", courseCode = "CSE1001", credits = "3.0", courseTitle = "Software Engineering", courseType = "Theory", courseSystem = "CBCS", courseMode = "Regular", faculty = "Dr. Amit Kumar", slot = "A1",
                        assessments = listOf(
                            AssessmentItem("1", "Continuous Assessment Test 1", "50", "15", "Completed", "42", "12.6"),
                            AssessmentItem("2", "Continuous Assessment Test 2", "50", "15", "Completed", "45", "13.5"),
                            AssessmentItem("3", "Digital Assignment 1", "10", "10", "Completed", "9", "9.0")
                        )
                    ),
                    MarksCourseItem(
                        slNo = "2", classNbr = "1056", courseCode = "CSE2002", credits = "4.0", courseTitle = "Database Management Systems", courseType = "Theory", courseSystem = "CBCS", courseMode = "Regular", faculty = "Dr. Rajeev Sen", slot = "B1",
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

    suspend fun fetchMoodleData(username: String, pass: String): MoodleRes {
        return try {
            val response: HttpResponse = httpClient.post("$baseUrl/api/lms-data") {
                contentType(ContentType.Application.Json)
                setBody(MoodleLoginRequest(username, pass))
            }
            if (response.status == HttpStatusCode.OK) {
                // The API might just return the array of assignments directly instead of MoodleRes
                // Wait, let's look at the React code: `const moodleData = await moodleRes.json();`
                // And then `mergedData = moodleData.map(...)`.
                // It means it returns an Array, not a wrapper object!
                // I'll read it as List<MoodleAssignment>
                val assignments: List<MoodleAssignment> = jsonConfig.decodeFromString(response.bodyAsText())
                MoodleRes(success = true, data = assignments)
            } else {
                MoodleRes(success = false, error = "HTTP ${response.status}", message = "Server returned status ${response.status}")
            }
        } catch (e: Exception) {
            MoodleRes(success = false, message = "Network error: ${e.message}", error = e.toString())
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

    suspend fun getCurriculum(semesterId: String? = null): CurriculumRes {
        if (useMockData || SessionManager.authorizedID.value == "DEMO123") {
            return CurriculumRes(success = true, title = "Curriculum Overview") // return empty mock for now
        }
        return try {
            val params = mutableMapOf<String, String>()
            if (semesterId != null) params["semesterId"] = semesterId
            postAuthorized<CurriculumRes>("curriculum", params) ?: CurriculumRes(success = false, message = "Empty response")
        } catch (e: Exception) {
            CurriculumRes(success = false, message = e.message, error = e.toString())
        }
    }

    suspend fun getCalendar(type: String = "ALL", semesterId: String? = null): CalendarRes {
        if (useMockData || SessionManager.authorizedID.value == "DEMO123") {
            val extraMonths = if (type != "ALL") listOf(
                CalendarMonth(
                    month = "August 2026",
                    days = listOf(
                        CalendarDay(5, listOf(CalendarEvent("Instructional Day", "Type: $type"))),
                        CalendarDay(15, listOf(CalendarEvent("Holiday", "Independence Day", "#ef4444")))
                    )
                )
            ) else emptyList()
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
                ) + extraMonths
            )
        }
        return try {
            val params = mutableMapOf("type" to type)
            if (semesterId != null) params["semesterId"] = semesterId
            val rawJson = postAuthorized<JsonElement>("calendar", params)
            if (rawJson != null) {
                val analysis = AnalyzeCalendar.analyzeAllCalendars(rawJson)
                if (analysis.results.isNotEmpty()) {
                    val months = analysis.results.map { res ->
                        CalendarMonth(
                            month = "${res.month} ${res.year}",
                            days = res.days.map { day ->
                                CalendarDay(
                                    date = day.date,
                                    events = day.events.mapNotNull { evElem ->
                                        val ev = evElem.jsonObject
                                        val text = ev["text"]?.jsonPrimitive?.content ?: return@mapNotNull null
                                        val evType = ev["type"]?.jsonPrimitive?.content ?: day.type
                                        CalendarEvent(type = evType, text = text)
                                    }
                                )
                            }
                        )
                    }
                    return CalendarRes(success = true, months = months)
                }
            }
            CalendarRes(success = false, message = "Empty response")
        } catch (e: Exception) {
            CalendarRes(success = false, message = e.message, error = e.toString())
        }
    }

    suspend fun getCalendars(semesterId: String? = null): CalendarsListRes {
        if (useMockData || SessionManager.authorizedID.value == "DEMO123") {
            return CalendarsListRes(
                success = true,
                calendars = listOf(
                    NamedCalendar(
                        name = "Academic Calendar",
                        months = listOf(
                            CalendarMonth(month = "July 2026", days = listOf(
                                CalendarDay(1, listOf(CalendarEvent("Instructional Day", "Day Order: Monday (Unit Test starting)"))),
                                CalendarDay(15, listOf(CalendarEvent("Holiday", "College Foundation Day", "#ef4444"))),
                                CalendarDay(20, listOf(CalendarEvent("Other", "Course Registration starts")))
                            )),
                            CalendarMonth(month = "August 2026", days = listOf(
                                CalendarDay(15, listOf(CalendarEvent("Holiday", "Independence Day", "#ef4444"))),
                                CalendarDay(30, listOf(CalendarEvent("Other", "Last day for course withdrawal")))
                            ))
                        )
                    ),
                    NamedCalendar(
                        name = "Examination Calendar",
                        months = listOf(
                            CalendarMonth(month = "September 2026", days = listOf(
                                CalendarDay(12, listOf(CalendarEvent("Exam", "CAT 1 begins"))),
                                CalendarDay(18, listOf(CalendarEvent("Exam", "CAT 1 ends")))
                            )),
                            CalendarMonth(month = "November 2026", days = listOf(
                                CalendarDay(10, listOf(CalendarEvent("Exam", "FAT begins"))),
                                CalendarDay(25, listOf(CalendarEvent("Exam", "FAT ends")))
                            ))
                        )
                    ),
                    NamedCalendar(
                        name = "Semester Calendar",
                        months = listOf(
                            CalendarMonth(month = "July 2026", days = listOf(
                                CalendarDay(1, listOf(CalendarEvent("Instructional Day", "Semester starts"))),
                                CalendarDay(31, listOf(CalendarEvent("Other", "Last day for registration")))
                            )),
                            CalendarMonth(month = "December 2026", days = listOf(
                                CalendarDay(15, listOf(CalendarEvent("Other", "Semester ends")))
                            ))
                        )
                    ),
                    NamedCalendar(
                        name = "Freshers Calendar",
                        months = listOf(
                            CalendarMonth(month = "July 2026", days = listOf(
                                CalendarDay(5, listOf(CalendarEvent("Other", "Freshers orientation"))),
                                CalendarDay(10, listOf(CalendarEvent("Other", "Campus tour")))
                            ))
                        )
                    )
                )
            )
        }
        try {
            val params = mutableMapOf("type" to "ALL")
            if (semesterId != null) params["semesterId"] = semesterId
            val rawJson = postAuthorized<JsonElement>("calendar", params)
            if (rawJson != null) {
                val analysis = AnalyzeCalendar.analyzeAllCalendars(rawJson)
                if (analysis.results.isNotEmpty()) {
                    val namedCalendars = analysis.results.groupBy { it.year }.map { (year, results) ->
                        NamedCalendar(
                            name = "Academic Calendar $year",
                            months = results.map { res ->
                                CalendarMonth(
                                    month = "${res.month} ${res.year}",
                                    days = res.days.map { day ->
                                        CalendarDay(
                                            date = day.date,
                                            events = day.events.mapNotNull { evElem ->
                                                val ev = evElem.jsonObject
                                                val text = ev["text"]?.jsonPrimitive?.content ?: return@mapNotNull null
                                                val evType = ev["type"]?.jsonPrimitive?.content ?: day.type
                                                CalendarEvent(type = evType, text = text)
                                            }
                                        )
                                    }
                                )
                            }
                        )
                    }
                    return CalendarsListRes(success = true, calendars = namedCalendars)
                }
            }
        } catch (_: Exception) { /* fall through */ }
        
        // Fallback: use old getCalendar() and wrap it as a single NamedCalendar
        val old = getCalendar("ALL", semesterId)
        return if (old.success && old.months.isNotEmpty()) {
            CalendarsListRes(
                success = true,
                calendars = listOf(NamedCalendar(name = "Academic Calendar", months = old.months))
            )
        } else {
            CalendarsListRes(success = false, message = old.message ?: "Empty response")
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
            val duesResp = postAuthorized<kotlinx.serialization.json.JsonObject>("payments")
            val receiptsResp = postAuthorized<kotlinx.serialization.json.JsonObject>("payment-receipts")
            val walletResp = postAuthorized<kotlinx.serialization.json.JsonObject>("wallet")

            val paymentsList = mutableListOf<PaymentItem>()

            if (duesResp?.get("hasDues")?.jsonPrimitive?.content?.toBooleanStrictOrNull() == true || duesResp?.get("hasDues")?.jsonPrimitive?.booleanOrNull == true) {
                paymentsList.add(PaymentItem(
                    billingId = "due-pending",
                    description = duesResp?.get("message")?.jsonPrimitive?.content?.takeIf { it.isNotBlank() } ?: "Pending Dues",
                    amount = "Check VTOP",
                    dueDate = "-",
                    status = "UNPAID"
                ))
            }

            val receiptsArray = receiptsResp?.get("receipts")?.jsonArray
            receiptsArray?.forEach { r ->
                val obj = r.jsonObject
                paymentsList.add(PaymentItem(
                    billingId = obj["receiptNumber"]?.jsonPrimitive?.content ?: "rec",
                    description = "Fee Payment",
                    amount = obj["amount"]?.jsonPrimitive?.content ?: "-",
                    dueDate = "-",
                    status = "PAID",
                    paymentDate = obj["date"]?.jsonPrimitive?.content,
                    receiptNo = obj["receiptNumber"]?.jsonPrimitive?.content
                ))
            }

            val walletLedger = walletResp?.get("ledgerINR")?.jsonArray
            val balance = if (walletLedger != null && walletLedger.size > 0) {
                walletLedger[0].jsonObject["bookBalanceAmount"]?.jsonPrimitive?.content
            } else null

            val hasDuesVal = duesResp?.get("hasDues")?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: duesResp?.get("hasDues")?.jsonPrimitive?.booleanOrNull
            PaymentsRes(
                success = true,
                payments = paymentsList,
                walletBalance = balance,
                message = if (hasDuesVal == false) duesResp?.get("message")?.jsonPrimitive?.content else null
            )
        } catch (e: Exception) {
            PaymentsRes(success = false, message = e.message, error = e.toString())
        }
    }

    suspend fun getLibrary(libUsername: String? = null, libPassword: String? = null): LibraryRes {
        if (useMockData || SessionManager.authorizedID.value == "DEMO123") {
            return LibraryRes(
                success = true,
                booksIssued = listOf(
                    BookItem("BK-90123", "Introduction to Algorithms", "Thomas H. Cormen", "2026-06-10", "2026-06-25", "Rs. 0.00"),
                    BookItem("BK-90224", "Database System Concepts", "Abraham Silberschatz", "2026-07-01", "2026-07-16", "Rs. 0.00")
                )
            )
        }
        val creds = if (libUsername != null && libPassword != null) {
            mapOf("libUsername" to libUsername, "libPassword" to libPassword)
        } else {
            val saved = com.amazecc.app.shared.repository.SettingsManager.getLibraryCredentials()
            if (saved != null) mapOf("libUsername" to saved.first, "libPassword" to saved.second) else emptyMap()
        }
        if (creds.isEmpty()) {
            return LibraryRes(success = false, message = "Library login required", error = "NO_LIB_CREDS")
        }
        return try {
            postAuthorized<LibraryRes>("library-due", creds) ?: LibraryRes(success = false, message = "Empty response")
        } catch (e: Exception) {
            LibraryRes(success = false, message = e.message, error = e.toString())
        }
    }

    suspend fun searchLibrary(query: String, index: String = "kw", offset: Int = 0): LibraryRes {
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
            val response: HttpResponse = httpClient.get("$baseUrl/api/koha/search?q=${query.encodeURLParameter()}&idx=${index.encodeURLParameter()}&offset=$offset&count=20")
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

    suspend fun getTransportRoutes(): TransportRoutesRes {
        if (useMockData || SessionManager.authorizedID.value == "DEMO123") {
            return TransportRoutesRes(
                success = true,
                routes = listOf(
                    BusRouteDetail(
                        routeNo = "R-01",
                        routeName = "Katpadi Junction - VIT Campus",
                        departureTime = "08:15 AM",
                        stops = listOf(
                            BusStopDetail("Katpadi Junction", "7:30 AM", 1, "\u20B915"),
                            BusStopDetail("Gandhi Nagar", "7:45 AM", 2, "\u20B910"),
                            BusStopDetail("VIT Main Gate", "8:00 AM", 3, "\u20B98"),
                            BusStopDetail("Academic Block", "8:15 AM", 4, "\u20B95")
                        ),
                        driverName = "K. Raman",
                        driverPhone = "+91 9843210982",
                        busType = "AC"
                    ),
                    BusRouteDetail(
                        routeNo = "R-12",
                        routeName = "Vellore New Bus Stand - VIT Campus",
                        departureTime = "08:00 AM",
                        stops = listOf(
                            BusStopDetail("Vellore New Bus Stand", "7:15 AM", 1, "\u20B910"),
                            BusStopDetail("Library Junction", "7:30 AM", 2, "\u20B98"),
                            BusStopDetail("South Gate", "7:50 AM", 3, "\u20B96"),
                            BusStopDetail("VIT Main Campus", "8:10 AM", 4, "\u20B95")
                        ),
                        driverName = "S. Kumar",
                        driverPhone = "+91 9442190831",
                        busType = "Non-AC"
                    ),
                    BusRouteDetail(
                        routeNo = "R-07",
                        routeName = "Ranipet - VIT Campus",
                        departureTime = "07:45 AM",
                        stops = listOf(
                            BusStopDetail("Ranipet Bus Stand", "6:45 AM", 1, "\u20B920"),
                            BusStopDetail("Walajah Road", "7:05 AM", 2, "\u20B915"),
                            BusStopDetail("VIT Campus", "7:45 AM", 3, "\u20B98")
                        ),
                        driverName = "M. Rajesh",
                        driverPhone = "+91 9876543210",
                        supervisorName = "A. Venkat",
                        supervisorPhone = "+91 8765432109",
                        busType = "AC"
                    ),
                    BusRouteDetail(
                        routeNo = "R-21",
                        routeName = "Katpadi Railway Station - VIT",
                        departureTime = "08:30 AM",
                        stops = listOf(
                            BusStopDetail("Katpadi Railway Station", "7:45 AM", 1, "\u20B912"),
                            BusStopDetail("Kannan Koil", "8:00 AM", 2, "\u20B98"),
                            BusStopDetail("VIT Main Gate", "8:20 AM", 3, "\u20B95"),
                            BusStopDetail("Academic Block", "8:30 AM", 4, "\u20B95")
                        ),
                        driverName = "P. Selvam",
                        driverPhone = "+91 9988776655",
                        busType = "Non-AC"
                    )
                )
            )
        }
        return try {
            postAuthorized<TransportRoutesRes>("transport/routes") ?: TransportRoutesRes(success = false, message = "Empty response")
        } catch (e: Exception) {
            TransportRoutesRes(success = false, message = e.message, error = e.toString())
        }
    }

    suspend fun submitTransportRegistration(request: TransportRegRequest): TransportRegSubmitRes {
        if (useMockData || SessionManager.authorizedID.value == "DEMO123") {
            return TransportRegSubmitRes(success = true, message = "Transport pass application submitted!", registrationId = "REG-${(1000..9999).random()}")
        }
        return try {
            val params = mapOf(
                "routeNo" to request.routeNo,
                "semester" to request.semester,
                "studentName" to request.studentName,
                "studentPhone" to request.studentPhone
            )
            postAuthorized<TransportRegSubmitRes>("transport/register", params)
                ?: TransportRegSubmitRes(success = false, message = "Empty response")
        } catch (e: Exception) {
            TransportRegSubmitRes(success = false, message = e.message, error = e.toString())
        }
    }

    suspend fun getTransportPass(): TransportPassRes {
        if (useMockData || SessionManager.authorizedID.value == "DEMO123") {
            return TransportPassRes(
                success = true,
                status = "active",
                dayBoarderStatus = "APPROVED (Bus Pass Active)",
                routeNo = "R-12",
                routeName = "Vellore New Bus Stand - VIT Campus",
                validUntil = "Dec 2026",
                studentName = "John Doe",
                studentPhone = "+91 9876543210",
                registrations = listOf(
                    TransportRegItem("1", "Fall 2025", "R-12", "Vellore New Bus Stand - VIT Campus", "Approved", "2025-07-20"),
                    TransportRegItem("2", "Spring 2025", "R-01", "Katpadi Junction - VIT Campus", "Expired", "2025-01-10")
                )
            )
        }
        return try {
            postAuthorized<TransportPassRes>("transport/pass") ?: TransportPassRes(success = false, message = "Empty response")
        } catch (e: Exception) {
            TransportPassRes(success = false, message = e.message, error = e.toString())
        }
    }

    suspend fun searchCabTrips(from: String, to: String, date: String): CabTripsRes {
        if (useMockData || SessionManager.authorizedID.value == "DEMO123") {
            return CabTripsRes(
                success = true,
                trips = listOf(
                    CabTrip(id = "CT-101", from = from, to = to, date = date, time = "2:00 PM", seatsTotal = 4, seatsAvailable = 2, fare = "â‚¹250", driverName = "S. Rajan", driverPhone = "+91 9876543210", driverRating = "4.8", vehicleModel = "Toyota Etios", vehicleColor = "White", vehiclePlate = "TN 01 AB 1234"),
                    CabTrip(id = "CT-102", from = from, to = to, date = date, time = "3:30 PM", seatsTotal = 4, seatsAvailable = 3, fare = "â‚¹200", driverName = "Priya K.", driverPhone = "+91 9876543211", driverRating = "4.9", vehicleModel = "Honda City", vehicleColor = "Blue", vehiclePlate = "TN 22 CD 5678"),
                    CabTrip(id = "CT-103", from = from, to = to, date = date, time = "5:00 PM", seatsTotal = 4, seatsAvailable = 1, fare = "â‚¹300", driverName = "Arun M.", driverPhone = "+91 9876543212", driverRating = "4.7", vehicleModel = "Maruti Swift", vehicleColor = "Silver", vehiclePlate = "TN 07 EF 9012"),
                    CabTrip(id = "CT-104", from = from, to = to, date = date, time = "6:15 PM", seatsTotal = 4, seatsAvailable = 4, fare = "â‚¹180", driverName = "Deepa R.", driverPhone = "+91 9876543213", driverRating = "4.6", vehicleModel = "Hyundai i10", vehicleColor = "Red", vehiclePlate = "TN 11 GH 3456")
                )
            )
        }
        return try {
            val params = mapOf("from" to from, "to" to to, "date" to date)
            postAuthorized<CabTripsRes>("cab/search", params) ?: CabTripsRes(success = false, message = "Empty response")
        } catch (e: Exception) {
            CabTripsRes(success = false, message = e.message, error = e.toString())
        }
    }

    suspend fun createCabTrip(request: CabCreateTripRequest): CabActionRes {
        if (useMockData || SessionManager.authorizedID.value == "DEMO123") {
            return CabActionRes(success = true, message = "Trip created!", tripId = "CT-${(1000..9999).random()}")
        }
        return try {
            val params = mapOf(
                "from" to request.from, "to" to request.to, "date" to request.date,
                "time" to request.time, "seats" to request.seats.toString(), "fare" to request.fare,
                "vehicleModel" to (request.vehicleModel ?: ""),
                "vehicleColor" to (request.vehicleColor ?: ""),
                "vehiclePlate" to (request.vehiclePlate ?: "")
            )
            postAuthorized<CabActionRes>("cab/create", params) ?: CabActionRes(success = false, message = "Empty response")
        } catch (e: Exception) {
            CabActionRes(success = false, message = e.message, error = e.toString())
        }
    }

    suspend fun getMyCabTrips(): CabTripsRes {
        if (useMockData || SessionManager.authorizedID.value == "DEMO123") {
            return CabTripsRes(
                success = true,
                trips = listOf(
                    CabTrip(id = "CT-201", from = "VIT Chennai", to = "Chennai Airport", date = "2026-07-15", time = "2:00 PM", seatsTotal = 4, seatsAvailable = 2, fare = "â‚¹250", driverName = "You", vehicleModel = "Toyota Etios", vehicleColor = "White", vehiclePlate = "TN 01 AB 1234", status = "Scheduled", isOwnTrip = true),
                    CabTrip(id = "CT-202", from = "Railway Station", to = "VIT Chennai", date = "2026-07-10", time = "10:00 AM", seatsTotal = 3, seatsAvailable = 0, fare = "â‚¹150", driverName = "You", vehicleModel = "Honda City", vehicleColor = "Blue", vehiclePlate = "TN 22 CD 5678", status = "Completed", isOwnTrip = true)
                )
            )
        }
        return try {
            postAuthorized<CabTripsRes>("cab/my-trips") ?: CabTripsRes(success = false, message = "Empty response")
        } catch (e: Exception) {
            CabTripsRes(success = false, message = e.message, error = e.toString())
        }
    }

    suspend fun requestJoinTrip(tripId: String, seats: Int): CabActionRes {
        if (useMockData || SessionManager.authorizedID.value == "DEMO123") {
            return CabActionRes(success = true, message = "Request sent!", tripId = tripId)
        }
        return try {
            val params = mapOf("tripId" to tripId, "seats" to seats.toString())
            postAuthorized<CabActionRes>("cab/join", params) ?: CabActionRes(success = false, message = "Empty response")
        } catch (e: Exception) {
            CabActionRes(success = false, message = e.message, error = e.toString())
        }
    }

    suspend fun getCabJoinRequests(tripId: String): CabJoinRequestsRes {
        if (useMockData || SessionManager.authorizedID.value == "DEMO123") {
            return CabJoinRequestsRes(
                success = true,
                requests = listOf(
                    CabJoinRequest(id = "REQ-001", tripId = tripId, requesterName = "Vikram S.", seats = 2, status = "Pending"),
                    CabJoinRequest(id = "REQ-002", tripId = tripId, requesterName = "Neha P.", seats = 1, status = "Pending")
                )
            )
        }
        return try {
            val params = mapOf("tripId" to tripId)
            postAuthorized<CabJoinRequestsRes>("cab/requests", params) ?: CabJoinRequestsRes(success = false, message = "Empty response")
        } catch (e: Exception) {
            CabJoinRequestsRes(success = false, message = e.message, error = e.toString())
        }
    }

    suspend fun acceptCabJoinRequest(tripId: String, requestId: String): CabActionRes {
        if (useMockData || SessionManager.authorizedID.value == "DEMO123") {
            return CabActionRes(success = true, message = "Request accepted!")
        }
        return try {
            val params = mapOf("tripId" to tripId, "requestId" to requestId)
            postAuthorized<CabActionRes>("cab/accept", params) ?: CabActionRes(success = false, message = "Empty response")
        } catch (e: Exception) {
            CabActionRes(success = false, message = e.message, error = e.toString())
        }
    }

    suspend fun rejectCabJoinRequest(tripId: String, requestId: String): CabActionRes {
        if (useMockData || SessionManager.authorizedID.value == "DEMO123") {
            return CabActionRes(success = true, message = "Request rejected")
        }
        return try {
            val params = mapOf("tripId" to tripId, "requestId" to requestId)
            postAuthorized<CabActionRes>("cab/reject", params) ?: CabActionRes(success = false, message = "Empty response")
        } catch (e: Exception) {
            CabActionRes(success = false, message = e.message, error = e.toString())
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

    suspend fun getQcmView(): QcmViewRes {
        if (useMockData || SessionManager.authorizedID.value == "DEMO123") {
            return QcmViewRes(
                success = true,
                data = listOf(
                    QcmTable("QCM 1", listOf(
                        buildJsonObject { put("qcmNo", "1"); put("actionTaken", "Resolved"); put("suggestions", "Improve lab equipment"); put("facultyReply", "Noted") }
                    )),
                    QcmTable("QCM 2", listOf(
                        buildJsonObject { put("qcmNo", "2"); put("actionTaken", "In Progress"); put("suggestions", "More practice sessions"); put("facultyReply", "Will schedule") }
                    ))
                )
            )
        }
        return try {
            postAuthorized<QcmViewRes>("qcm-view") ?: QcmViewRes(success = false, message = "Empty response")
        } catch (e: Exception) {
            QcmViewRes(success = false, message = e.message, error = e.toString())
        }
    }

    suspend fun getEvents(): EventHubRes {
        if (useMockData || SessionManager.authorizedID.value == "DEMO123") {
            return EventHubRes(
                success = true,
                events = listOf(
                    EventHubEvent(eid = "E001", title = "RoboWars 2026", eligibility = "All", type = "Technical", date = "2026-08-20", location = "SJT Ground", price = "Free", time = "10:00 AM"),
                    EventHubEvent(eid = "E002", title = "Code Sprint", eligibility = "All", type = "Technical", date = "2026-09-05", location = "Anna Auditorium", price = "Free", time = "09:00 AM")
                )
            )
        }
        return try {
            val response: HttpResponse = httpClient.get("$baseUrl/api/events")
            if (response.status == HttpStatusCode.OK) {
                val element = jsonConfig.decodeFromString<JsonElement>(response.bodyAsText())
                val eventsList = if (element is JsonArray) {
                    jsonConfig.decodeFromJsonElement<List<EventHubEvent>>(element)
                } else if (element.jsonObject["events"] is JsonArray) {
                    jsonConfig.decodeFromJsonElement<List<EventHubEvent>>(element.jsonObject["events"]!!)
                } else {
                    emptyList()
                }
                EventHubRes(success = true, events = eventsList)
            } else {
                EventHubRes(success = false, message = "HTTP ${response.status}", error = "HTTP ${response.status}")
            }
        } catch (e: Exception) {
            EventHubRes(success = false, message = "Network error: ${e.message}", error = e.toString())
        }
    }

    suspend fun getEventPreview(eid: String): EventHubPreview? {
        return try {
            val response = httpClient.post("$baseUrl/api/events/preview") {
                contentType(ContentType.Application.Json)
                setBody(buildJsonObject {
                    put("eid", eid)
                    put("username", "")
                    put("password", "")
                })
            }
            if (response.status == HttpStatusCode.OK) {
                jsonConfig.decodeFromString<EventHubPreview>(response.bodyAsText())
            } else null
        } catch (e: Exception) {
            null
        }
    }

    suspend fun registerForEvent(eid: String): EventHubRegisterRes? {
        return try {
            val response = httpClient.post("$baseUrl/api/events/register") {
                contentType(ContentType.Application.Json)
                setBody(buildJsonObject {
                    put("eid", eid)
                    put("username", "")
                    put("password", "")
                })
            }
            if (response.status == HttpStatusCode.OK) {
                jsonConfig.decodeFromString<EventHubRegisterRes>(response.bodyAsText())
            } else null
        } catch (e: Exception) {
            null
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
                        price = "Free",
                        eligibility = "All"
                    )
                )
            )
        }
        return try {
            val response: HttpResponse = httpClient.post("$baseUrl/api/events/profile") {
                contentType(ContentType.Application.Json)
                setBody(buildJsonObject {
                    put("jsessionid", SessionManager.clubToken.value ?: "")
                })
            }
            if (response.status == HttpStatusCode.OK) {
                val element = jsonConfig.decodeFromString<JsonElement>(response.bodyAsText())
                val eventsList = if (element is JsonArray) {
                    jsonConfig.decodeFromJsonElement<List<EventHubEvent>>(element)
                } else if (element.jsonObject["events"] is JsonArray) {
                    jsonConfig.decodeFromJsonElement<List<EventHubEvent>>(element.jsonObject["events"]!!)
                } else {
                    emptyList()
                }
                EventHubRes(success = true, events = eventsList)
            } else {
                EventHubRes(success = false, message = "HTTP ${response.status}", error = "Server returned status ${response.status}")
            }
        } catch (e: Exception) {
            EventHubRes(success = false, message = "Network error: ${e.message}", error = e.toString())
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
                val element = jsonConfig.decodeFromString<JsonElement>(response.bodyAsText())
                if (element is JsonArray) {
                    val clubsList = jsonConfig.decodeFromJsonElement<List<ClubItem>>(element)
                    ClubsRes(success = true, clubs = clubsList)
                } else {
                    jsonConfig.decodeFromJsonElement<ClubsRes>(element)
                }
            } else {
                ClubsRes(success = false, message = "HTTP ${response.status}", error = "Server returned status ${response.status}")
            }
        } catch (e: Exception) {
            ClubsRes(success = false, message = "Network error: ${e.message}", error = e.toString())
        }
    }

    suspend fun getStudentProfile(): StudentProfileRes {
        if (useMockData || SessionManager.authorizedID.value == "DEMO123") {
            return StudentProfileRes(
                success = true,
                data = StudentProfile(
                    regNo = "23BCE1234",
                    name = "Alexander Pierce",
                    email = "alex.pierce2023@vitstudent.ac.in",
                    mobile = "+91 98765 43210",
                    program = "B.Tech CSE (Specialisation in AI & ML)",
                    campus = "VIT Chennai",
                    batch = "2023-2027",
                    section = "A",
                    advisorName = "Dr. Rajesh Kumar",
                    bloodGroup = "O+"
                )
            )
        }
        return try {
            postAuthorized<StudentProfileRes>("student") ?: StudentProfileRes(success = false, message = "Empty response")
        } catch (e: Exception) {
            StudentProfileRes(success = false, message = e.message, error = e.toString())
        }
    }

    @Suppress("unused")
    suspend fun getProfileImages(): ProfileImagesRes {
        if (useMockData || SessionManager.authorizedID.value == "DEMO123") {
            return ProfileImagesRes(success = true)
        }
        return try {
            postAuthorized<ProfileImagesRes>("profile-images") ?: ProfileImagesRes(success = false)
        } catch (e: Exception) {
            ProfileImagesRes(success = false, error = e.toString())
        }
    }

    suspend fun getArrearSchedule(): ArrearResponse {
        if (useMockData || SessionManager.authorizedID.value == "DEMO123") {
            return ArrearResponse(
                keyValuePairs = listOf(
                    KeyValuePair("Registered Credits", "22.0"),
                    KeyValuePair("Eligible Arrears", "2"),
                    KeyValuePair("Exam Fee", "Rs. 2,400")
                ),
                tables = listOf(
                    ApiTable(
                        title = "Arrear Schedule",
                        headers = listOf("Course Code", "Course Title", "Date", "Time", "Venue"),
                        rows = listOf(
                            listOf("MAT2001", "Statistics for Engineers", "2026-07-20", "10:00 AM", "SJT-201"),
                            listOf("PHY1701", "Engineering Physics", "2026-07-22", "2:00 PM", "SJT-305")
                        )
                    )
                )
            )
        }
        return postAuthorized<ArrearResponse>("arrear-schedule") ?: ArrearResponse(success = false, message = "Empty response")
    }

    suspend fun getArrearDetails(): ArrearResponse {
        if (useMockData || SessionManager.authorizedID.value == "DEMO123") {
            return ArrearResponse(
                tables = listOf(
                    ApiTable(
                        title = "Arrear Details",
                        headers = listOf("Course Code", "Course Title", "Credits", "Course Type", "Status"),
                        rows = listOf(
                            listOf("MAT2001", "Statistics for Engineers", "4", "Theory", "Registered"),
                            listOf("PHY1701", "Engineering Physics", "3", "Theory", "Registered")
                        )
                    )
                )
            )
        }
        return postAuthorized<ArrearResponse>("arrear-details") ?: ArrearResponse(success = false, message = "Empty response")
    }

    suspend fun getArrearGrade(): ArrearResponse {
        if (useMockData || SessionManager.authorizedID.value == "DEMO123") {
            return ArrearResponse(
                tables = listOf(
                    ApiTable(
                        title = "Arrear Grades",
                        headers = listOf("Course Code", "Course Title", "Grade", "Credits", "Result"),
                        rows = listOf(
                            listOf("MAT2001", "Statistics for Engineers", "B", "4", "PASS"),
                            listOf("PHY1701", "Engineering Physics", "", "3", "Awaited")
                        )
                    )
                )
            )
        }
        return postAuthorized<ArrearResponse>("arrear-grade") ?: ArrearResponse(success = false, message = "Empty response")
    }

    suspend fun getMakeupExam(): ArrearResponse {
        if (useMockData || SessionManager.authorizedID.value == "DEMO123") {
            return ArrearResponse(
                keyValuePairs = listOf(
                    KeyValuePair("Eligibility Status", "Eligible"),
                    KeyValuePair("Courses Eligible", "2"),
                    KeyValuePair("Last Date to Apply", "2026-07-25")
                ),
                tables = listOf(
                    ApiTable(
                        title = "Makeup Exam Eligibility",
                        headers = listOf("Course Code", "Course Title", "Credits", "Eligibility"),
                        rows = listOf(
                            listOf("MAT2001", "Statistics for Engineers", "4", "Eligible"),
                            listOf("PHY1701", "Engineering Physics", "3", "Eligible")
                        )
                    )
                )
            )
        }
        return postAuthorized<ArrearResponse>("makeup-exam") ?: ArrearResponse(success = false, message = "Empty response")
    }

    suspend fun getMakeupSchedule(): ArrearResponse {
        if (useMockData || SessionManager.authorizedID.value == "DEMO123") {
            return ArrearResponse(
                tables = listOf(
                    ApiTable(
                        title = "Makeup Schedule",
                        headers = listOf("Course Code", "Course Title", "Date", "Time", "Venue"),
                        rows = listOf(
                            listOf("MAT2001", "Statistics for Engineers", "2026-07-28", "10:00 AM", "SJT-101"),
                            listOf("PHY1701", "Engineering Physics", "2026-07-30", "2:00 PM", "SJT-204")
                        )
                    )
                )
            )
        }
        return postAuthorized<ArrearResponse>("makeup-schedule") ?: ArrearResponse(success = false, message = "Empty response")
    }

    suspend fun getCompreInfo(): ArrearResponse {
        if (useMockData || SessionManager.authorizedID.value == "DEMO123") {
            return ArrearResponse(
                keyValuePairs = listOf(
                    KeyValuePair("Total Eligible Courses", "6"),
                    KeyValuePair("Comprehensive Exam Date", "2026-08-15"),
                    KeyValuePair("Result Declaration", "2026-08-30")
                ),
                tables = listOf(
                    ApiTable(
                        title = "Comprehensive Exam Info",
                        headers = listOf("Course Code", "Course Title", "Credits", "Compre Status"),
                        rows = listOf(
                            listOf("MAT2001", "Statistics for Engineers", "4", "Scheduled"),
                            listOf("PHY1701", "Engineering Physics", "3", "Scheduled"),
                            listOf("CSE1001", "Problem Solving", "3", "Completed"),
                            listOf("ENG1001", "Technical English", "2", "Completed")
                        )
                    )
                )
            )
        }
        return postAuthorized<ArrearResponse>("compre-info") ?: ArrearResponse(success = false, message = "Empty response")
    }

    suspend fun getCirculars(): CircularsRes {
        if (useMockData || SessionManager.authorizedID.value == "DEMO123") {
            return CircularsRes(
                success = true,
                circulars = listOf(
                    CircularFolder("Academic Calendar", listOf(
                        CircularItem("CIR-001", "Revised Academic Calendar for 2026-27"),
                        CircularItem("CIR-002", "Holiday List for Upcoming Semester")
                    )),
                    CircularFolder("Examinations", listOf(
                        CircularItem("CIR-003", "CAT I Examination Schedule"),
                        CircularItem("CIR-004", "Makeup Exam Application Notice")
                    )),
                    CircularFolder("General", listOf(
                        CircularItem("CIR-005", "Hostel Fee Payment Deadline"),
                        CircularItem("CIR-006", "Transport Route Changes Effective Aug 1")
                    ))
                )
            )
        }
        return postAuthorized<CircularsRes>("circulars") ?: CircularsRes(success = false, message = "Empty response")
    }

    @Suppress("unused")
    suspend fun getVitol(): VitolRes {
        if (useMockData || SessionManager.authorizedID.value == "DEMO123") {
            return VitolRes(
                success = true,
                message = "VITOL fetched (Demo)",
                data = VitolData(balance = "500.00", limit = "2000.00", consumed = "1500.00", message = "Active")
            )
        }
        return postAuthorized<VitolRes>("vitol") ?: VitolRes(success = false, message = "Empty response")
    }

    // â”€â”€ Phase 3 endpoints â”€â”€

    suspend fun getQBankCourses(): QBankCoursesRes {
        if (useMockData || SessionManager.authorizedID.value == "DEMO123") {
            return QBankCoursesRes(success = true, courses = listOf(
                QBankCourse("CSE1001", "Software Engineering"),
                QBankCourse("CSE2002", "Database Management Systems"),
                QBankCourse("CSE3001", "Artificial Intelligence"),
                QBankCourse("MAT2001", "Differential Equations"),
                QBankCourse("PHY1701", "Engineering Physics")
            ))
        }
        return postAuthorized<QBankCoursesRes>("qbank/courses") ?: QBankCoursesRes(success = false, message = "Empty response")
    }

    suspend fun getFacultySchools(): FacultySchoolsRes {
        if (useMockData || SessionManager.authorizedID.value == "DEMO123") {
            return FacultySchoolsRes(success = true, schools = listOf(
                FacultySchool("SCOPE", "SCOPE"),
                FacultySchool("SENSE", "SENSE"),
                FacultySchool("SCE", "SCE"),
                FacultySchool("SMEC", "SMEC"),
                FacultySchool("SASM", "SAS MATHS"),
                FacultySchool("SASP", "SAS PHYSICS"),
                FacultySchool("SASC", "SAS CHEMISTRY"),
                FacultySchool("SSL", "SSL"),
                FacultySchool("SBST", "SBST"),
                FacultySchool("SELECT", "SELECT"),
                FacultySchool("V-SMART", "V-SMART"),
                FacultySchool("VFSI", "VFIT"),
                FacultySchool("VSL", "VITSOL")
            ))
        }
        return try {
            val response: HttpResponse = httpClient.get("$baseUrl/api/faculty/schools")
            if (response.status == HttpStatusCode.OK) {
                jsonConfig.decodeFromString(response.bodyAsText())
            } else {
                FacultySchoolsRes(success = false, error = "HTTP ${response.status}")
            }
        } catch (e: Exception) {
            FacultySchoolsRes(success = false, error = e.message)
        }
    }

    suspend fun postFacultyScrape(schoolId: String): FacultyScrapeRes {
        if (useMockData || SessionManager.authorizedID.value == "DEMO123") {
            val facultyMap = mapOf(
                "SCOPE" to listOf(
                    FacultyProfile("50300", "Dr. Viswanathan V", "Professor and Dean", imageUrl = "https://chennai.vit.ac.in/wp-content/uploads/2020/08/50300-Viswanathan-V.jpg", profileUrl = "https://chennai.vit.ac.in/member/dr-viswanathan-v/", email = "viswanathan.v@vit.ac.in", employeeId = "50300", intercom = "044 3993 1130"),
                    FacultyProfile("50443", "Dr. Nithyanandam P", "Professor and Associate Dean", email = "nithyanandam.p@vit.ac.in", employeeId = "50443", intercom = "044 3993 1396"),
                    FacultyProfile("50438", "Dr. Suganya G", "Professor and Associate Dean", email = "suganya.g@vit.ac.in", employeeId = "50438", intercom = "044 3993 1399")
                )
            )
            return FacultyScrapeRes(success = true, faculties = facultyMap[schoolId] ?: emptyList())
        }
        return try {
            val response: HttpResponse = httpClient.post("$baseUrl/api/faculty/scrape") {
                contentType(ContentType.Application.Json)
                setBody(buildJsonObject { put("schoolId", schoolId) })
            }
            if (response.status == HttpStatusCode.OK) {
                jsonConfig.decodeFromString(response.bodyAsText())
            } else {
                FacultyScrapeRes(success = false, error = "HTTP ${response.status}")
            }
        } catch (e: Exception) {
            FacultyScrapeRes(success = false, error = e.message)
        }
    }

    suspend fun getCourseOptionChange(): ArrearResponse {
        if (useMockData || SessionManager.authorizedID.value == "DEMO123") {
            return ArrearResponse(
                tables = listOf(ApiTable(title = "Course Option Change", headers = listOf("Course Code", "Course Title", "Status", "Last Date"), rows = listOf(
                    listOf("CSE1001", "Software Engineering", "Open", "2026-07-20"),
                    listOf("MAT2001", "Differential Equations", "Closed", "2026-06-30")
                )))
            )
        }
        return postAuthorized<ArrearResponse>("course-option-change") ?: ArrearResponse(success = false, message = "Empty response")
    }

    suspend fun getExcRegistration(): ArrearResponse {
        if (useMockData || SessionManager.authorizedID.value == "DEMO123") {
            return ArrearResponse(
                keyValuePairs = listOf(KeyValuePair("Eligible Credits", "22"), KeyValuePair("Applied Credits", "18")),
                tables = listOf(ApiTable(title = "EXC Registration", headers = listOf("Course Code", "Course Title", "Credits", "Status"), rows = listOf(
                    listOf("CSE4001", "Machine Learning", "4", "Approved"),
                    listOf("CSE4002", "Cloud Computing", "3", "Pending")
                )))
            )
        }
        return postAuthorized<ArrearResponse>("exc-registration") ?: ArrearResponse(success = false, message = "Empty response")
    }

    suspend fun getMinorHonour(): ArrearResponse {
        if (useMockData || SessionManager.authorizedID.value == "DEMO123") {
            return ArrearResponse(
                tables = listOf(ApiTable(title = "Minor / Honour Courses", headers = listOf("Course Code", "Course Title", "Type", "Status"), rows = listOf(
                    listOf("MNC1001", "Data Science Minor", "Minor", "Enrolled"),
                    listOf("HON2001", "Advanced Algorithms", "Honour", "Completed")
                )))
            )
        }
        return postAuthorized<ArrearResponse>("minor-honour") ?: ArrearResponse(success = false, message = "Empty response")
    }

    suspend fun getCourseCompletion(): ArrearResponse {
        if (useMockData || SessionManager.authorizedID.value == "DEMO123") {
            return ArrearResponse(
                keyValuePairs = listOf(KeyValuePair("Total Credits Required", "160"), KeyValuePair("Credits Completed", "84")),
                tables = listOf(ApiTable(title = "Course Completion Status", headers = listOf("Category", "Required", "Completed", "Status"), rows = listOf(
                    listOf("University Core", "48", "36", "In Progress"),
                    listOf("Program Core", "52", "30", "In Progress"),
                    listOf("Program Elective", "24", "8", "In Progress"),
                    listOf("Open Elective", "12", "4", "In Progress")
                )))
            )
        }
        return postAuthorized<ArrearResponse>("course-completion") ?: ArrearResponse(success = false, message = "Empty response")
    }

    suspend fun getProjects(): ArrearResponse {
        if (useMockData || SessionManager.authorizedID.value == "DEMO123") {
            return ArrearResponse(
                tables = listOf(ApiTable(title = "Projects", headers = listOf("Project Code", "Title", "Guide", "Status"), rows = listOf(
                    listOf("PJ-001", "AI Chatbot for Education", "Dr. Amit Kumar", "In Progress"),
                    listOf("PJ-002", "Blockchain-based Voting", "Dr. Rajeev Sen", "Completed")
                )))
            )
        }
        return postAuthorized<ArrearResponse>("project") ?: ArrearResponse(success = false, message = "Empty response")
    }

    suspend fun getWishlist(): ArrearResponse {
        if (useMockData || SessionManager.authorizedID.value == "DEMO123") {
            return ArrearResponse(
                tables = listOf(ApiTable(title = "Course Wishlist", headers = listOf("Course Code", "Course Title", "Priority", "Semester"), rows = listOf(
                    listOf("CSE4003", "Natural Language Processing", "High", "Fall 2026-27"),
                    listOf("CSE4004", "Computer Vision", "Medium", "Fall 2026-27")
                )))
            )
        }
        return postAuthorized<ArrearResponse>("wishlist") ?: ArrearResponse(success = false, message = "Empty response")
    }

    suspend fun getFeedbackStatus(): ArrearResponse {
        if (useMockData || SessionManager.authorizedID.value == "DEMO123") {
            return ArrearResponse(
                keyValuePairs = listOf(KeyValuePair("Total Feedbacks", "6"), KeyValuePair("Pending", "2"), KeyValuePair("Submitted", "4")),
                tables = listOf(ApiTable(title = "Feedback Status", headers = listOf("Course Code", "Course Title", "Status", "Due Date"), rows = listOf(
                    listOf("CSE1001", "Software Engineering", "Submitted", "2026-07-10"),
                    listOf("CSE2002", "Database Management Systems", "Pending", "2026-07-15"),
                    listOf("MAT2001", "Differential Equations", "Submitted", "2026-07-08")
                )))
            )
        }
        return postAuthorized<ArrearResponse>("feedback") ?: ArrearResponse(success = false, message = "Empty response")
    }

    suspend fun getBonafide(): ArrearResponse {
        if (useMockData || SessionManager.authorizedID.value == "DEMO123") {
            return ArrearResponse(
                tables = listOf(ApiTable(title = "Bonafide Certificates", headers = listOf("Request ID", "Purpose", "Status", "Issued Date"), rows = listOf(
                    listOf("BNF-001", "Bank Loan", "Issued", "2026-06-20"),
                    listOf("BNF-002", "Passport Application", "Processing", "â€”")
                )))
            )
        }
        return postAuthorized<ArrearResponse>("bonafide") ?: ArrearResponse(success = false, message = "Empty response")
    }

    suspend fun getETranscript(): ArrearResponse {
        if (useMockData || SessionManager.authorizedID.value == "DEMO123") {
            return ArrearResponse(
                keyValuePairs = listOf(KeyValuePair("Available Transcripts", "4"), KeyValuePair("Pending Requests", "1")),
                tables = listOf(ApiTable(title = "E-Transcripts", headers = listOf("Transcript ID", "Semester", "Type", "Status"), rows = listOf(
                    listOf("TR-101", "Fall 2025-26", "Provisional", "Downloaded"),
                    listOf("TR-102", "Winter 2025-26", "Consolidated", "Available"),
                    listOf("TR-103", "Fall 2026-27", "Provisional", "Requested")
                )))
            )
        }
        return postAuthorized<ArrearResponse>("e-transcript") ?: ArrearResponse(success = false, message = "Empty response")
    }

    suspend fun getAdditionalLearning(): ArrearResponse {
        if (useMockData || SessionManager.authorizedID.value == "DEMO123") {
            return ArrearResponse(
                tables = listOf(ApiTable(title = "Additional Learning", headers = listOf("Course Code", "Course Title", "Platform", "Progress"), rows = listOf(
                    listOf("AL-001", "Python for Data Science", "Coursera", "80%"),
                    listOf("AL-002", "Web Development", "NPTEL", "45%")
                )))
            )
        }
        return postAuthorized<ArrearResponse>("additional-learning") ?: ArrearResponse(success = false, message = "Empty response")
    }

    @Suppress("unused")
    suspend fun getFFCSReport(): ByteArray? {
        return try {
            val response: HttpResponse = httpClient.get("https://amazecc.vit.ac.in/ffcs/ffcsReport.csv")
            if (response.status == HttpStatusCode.OK) {
                response.readBytes()
            } else null
        } catch (_: Exception) {
            null
        }
    }
}



