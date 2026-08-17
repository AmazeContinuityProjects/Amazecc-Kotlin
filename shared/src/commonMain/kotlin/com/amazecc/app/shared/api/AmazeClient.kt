package com.amazecc.app.shared.api

import com.amazecc.app.shared.model.*
import com.amazecc.app.shared.repository.SessionManager
import com.amazecc.app.shared.repository.SettingsManager
import com.russhwolf.settings.set
import kotlinx.datetime.Clock
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.async

import io.ktor.client.*
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.client.call.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.utils.io.readRemaining
import io.ktor.utils.io.core.readFully
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import com.amazecc.app.shared.utils.AnalyzeCalendar
import com.amazecc.app.shared.utils.FacultyUtils
import com.amazecc.app.shared.utils.UpdateConfig
import com.amazecc.app.shared.utils.DemoData

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
    val baseUrl = "https://api.amazecc.com"
    private var useMockData = false // Toggle for offline testing


    fun setUseMockData(enable: Boolean) {
        useMockData = enable
    }

    private val jsonConfig = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    private val httpClient = HttpClient {
        install(ContentNegotiation) {
            json(jsonConfig)
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 30_000
            connectTimeoutMillis = 15_000
            socketTimeoutMillis = 30_000
        }
    }

    suspend fun login(username: String, password: String): LoginResponse {
if (useMockData) return DemoData.get("login", LoginResponse.serializer()) ?: LoginResponse(success = true, message = "Demo login successful")

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

    private suspend inline fun <reified T> postAuthorizedBody(endpoint: String, body: String): T? {
        val cookies = SessionManager.cookies.value ?: return null
        val authorizedID = SessionManager.authorizedID.value ?: return null
        val csrf = SessionManager.csrf.value ?: return null
        val response: HttpResponse = httpClient.post("$baseUrl/api/$endpoint") {
            contentType(ContentType.Application.Json)
            setBody(buildJsonObject {
                put("cookies", cookies)
                put("authorizedID", authorizedID)
                put("csrf", csrf)
                val parsed = Json.parseToJsonElement(body).jsonObject
                parsed.forEach { (k, v) -> put(k, v) }
            })
        }
        return if (response.status == HttpStatusCode.OK) {
            jsonConfig.decodeFromString<T>(response.bodyAsText())
        } else null
    }

    suspend fun getAcademicData(semesterId: String? = null): AcademicSyncResult {
        if (useMockData) {
            val attendance = DemoData.get("attendance", AttendanceRes.serializer())
            val marks = DemoData.get("marks", MarksRes.serializer())
            return AcademicSyncResult(
                attendance = attendance ?: AttendanceRes(success = false, error = "Demo data missing"),
                marks = marks
            )
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
if (useMockData) return DemoData.get("timetable", TimetableRes.serializer()) ?: TimetableRes()
        return try {
            val params = if (semesterId != null) mapOf("semesterId" to semesterId) else emptyMap()
            postAuthorized<TimetableRes>("timetable", params) ?: TimetableRes(success = false, message = "Empty response")
        } catch (e: Exception) {
            TimetableRes(success = false, message = e.message, error = e.toString())
        }
    }

    suspend fun getMarks(semesterId: String? = null): MarksRes {
if (useMockData) return DemoData.get("marks", MarksRes.serializer()) ?: MarksRes()
        return try {
            val params = if (semesterId != null) mapOf("semesterId" to semesterId) else emptyMap()
            postAuthorized<MarksRes>("marks", params) ?: MarksRes(success = false, message = "Empty response")
        } catch (e: Exception) {
            MarksRes(success = false, message = e.message, error = e.toString())
        }
    }

    suspend fun getAllGrades(): AllGradesRes {
if (useMockData) return DemoData.get("allGrades", AllGradesRes.serializer()) ?: AllGradesRes()
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
if (useMockData) return DemoData.get("hostel", HostelDetails.serializer()) ?: HostelDetails()
        return try {
            postAuthorized<HostelDetails>("hostel") ?: HostelDetails(success = false, message = "Empty response")
        } catch (e: Exception) {
            HostelDetails(success = false, message = e.message, error = e.toString())
        }
    }

    private const val UNMESSIFY_BASE = "https://kanishka-developer.github.io/unmessify/json/en"

    private fun messGenderLetter(gender: String?): String =
        if (gender.equals("FEMALE", true)) "W" else "M"

    private fun messTypeLetter(messType: String?): String = when {
        messType?.contains("NON", ignoreCase = true) == true -> "N"
        messType?.contains("SPECIAL", ignoreCase = true) == true -> "S"
        else -> "V"
    }

    suspend fun getMessMenu(gender: String?, messType: String?): MessMenuRes {
        if (useMockData) return DemoData.get("messMenu", MessMenuRes.serializer()) ?: MessMenuRes()
        return try {
            val url = "$UNMESSIFY_BASE/VITC-${messGenderLetter(gender)}-${messTypeLetter(messType)}.json"
            httpClient.get(url).body()
        } catch (e: Exception) {
            MessMenuRes()
        }
    }

    suspend fun getLaundrySchedule(gender: String?, blockPrefix: String): LaundryRes {
        if (useMockData) return DemoData.get("laundry", LaundryRes.serializer()) ?: LaundryRes()
        return try {
            val block = blockPrefix.uppercase()
            val file = when (block) {
                "C" -> if (gender.equals("FEMALE", true)) "VITC-CG-L.json" else "VITC-CB-L.json"
                else -> "VITC-$block-L.json"
            }
            httpClient.get("$UNMESSIFY_BASE/$file").body()
        } catch (e: Exception) {
            LaundryRes()
        }
    }

    suspend fun getHostelCounselling(): ArrearResponse {
        if (useMockData) return DemoData.get("hostelCounselling", ArrearResponse.serializer()) ?: ArrearResponse()
        return try {
            postAuthorized<ArrearResponse>("hostel-counselling")
                ?: ArrearResponse(success = false, message = "Empty response")
        } catch (e: Exception) {
            ArrearResponse(success = false, message = e.message, error = e.toString())
        }
    }

    suspend fun getExamSchedule(semesterId: String? = null): ExamScheduleRes {
if (useMockData) return DemoData.get("examSchedule", ExamScheduleRes.serializer()) ?: ExamScheduleRes()
        return try {
            val params = mutableMapOf<String, String>()
            if (semesterId != null) params["semesterId"] = semesterId
            postAuthorized<ExamScheduleRes>("schedule", params) ?: ExamScheduleRes(success = false, message = "Empty response")
        } catch (e: Exception) {
            ExamScheduleRes(success = false, message = e.message, error = e.toString())
        }
    }

    suspend fun getCurriculum(semesterId: String? = null): CurriculumRes {
if (useMockData) return DemoData.get("curriculum", CurriculumRes.serializer()) ?: CurriculumRes()
        return try {
            val params = mutableMapOf<String, String>()
            if (semesterId != null) params["semesterId"] = semesterId
            postAuthorized<CurriculumRes>("curriculum", params) ?: CurriculumRes(success = false, message = "Empty response")
        } catch (e: Exception) {
            CurriculumRes(success = false, message = e.message, error = e.toString())
        }
    }

    suspend fun getCalendar(type: String = "ALL", semesterId: String? = null): CalendarRes {
if (useMockData) return DemoData.get("calendar", CalendarRes.serializer()) ?: CalendarRes()
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
if (useMockData) return DemoData.get("calendars", CalendarsListRes.serializer()) ?: CalendarsListRes()
        return coroutineScope {
            val results = calendarTypes.map { (type, name) ->
                async { getCalendar(type, semesterId) to name }
            }.map { it.await() }

            val calendars = mutableListOf<NamedCalendar>()
            for ((res, name) in results) {
                if (!res.success || res.months.isEmpty()) continue
                calendars.add(NamedCalendar(name = name, months = res.months))
            }

            if (calendars.isNotEmpty()) {
                CalendarsListRes(success = true, calendars = calendars)
            } else {
                CalendarsListRes(success = false, message = results.firstOrNull()?.first?.message ?: "Empty response")
            }
        }
    }

    val calendarTypes = listOf(
        "ALL" to "General Semester",
        "ALL02" to "General Flexible",
        "ALL03" to "General Freshers",
        "ALL05" to "General LAW",
        "ALL06" to "Flexible Freshers",
        "ALL08" to "Cohort LAW",
        "ALL11" to "Flexible Research",
        "WEI" to "Weekend Intra Semester"
    )

    suspend fun getPayments(): PaymentsRes {
if (useMockData) return DemoData.get("payments", PaymentsRes.serializer()) ?: PaymentsRes()
        return try {
            val duesResp = postAuthorized<JsonObject>("payments")
            val receiptsResp = postAuthorized<JsonObject>("payment-receipts")
            val walletResp = postAuthorized<JsonObject>("wallet")

            val paymentsList = mutableListOf<PaymentItem>()

            val hasDues = duesResp?.get("hasDues")?.let {
                try { it.jsonPrimitive.booleanOrNull ?: it.jsonPrimitive.content.toBooleanStrictOrNull() } catch (_: Exception) { null }
            }

            if (hasDues == true) {
                paymentsList.add(PaymentItem(
                    billingId = "due-pending",
                    description = duesResp.get("message")?.let { try { it.jsonPrimitive.content.takeIf { s -> s.isNotBlank() } } catch(_: Exception) { null } } ?: "Pending Dues",
                    amount = "Check VTOP",
                    dueDate = "-",
                    status = "UNPAID"
                ))
            }

            val receiptsArray = receiptsResp?.get("receipts")?.let { try { it.jsonArray } catch(_: Exception) { null } }
            receiptsArray?.forEach { r ->
                val obj = try { r.jsonObject } catch(_: Exception) { null }
                if (obj != null) {
                    paymentsList.add(PaymentItem(
                        billingId = obj["receiptNumber"]?.let { try { it.jsonPrimitive.content } catch(_: Exception) { null } } ?: "rec",
                        description = "Fee Payment",
                        amount = obj["amount"]?.let { try { it.jsonPrimitive.content } catch(_: Exception) { null } } ?: "-",
                        dueDate = "-",
                        status = "PAID",
                        paymentDate = obj["date"]?.let { try { it.jsonPrimitive.content } catch(_: Exception) { null } },
                        receiptNo = obj["receiptNumber"]?.let { try { it.jsonPrimitive.content } catch(_: Exception) { null } }
                    ))
                }
            }

            val walletLedger = walletResp?.get("ledgerINR")?.let { try { it.jsonArray } catch(_: Exception) { null } }
            val balance = if (walletLedger != null && walletLedger.size > 0) {
                try { walletLedger[0].jsonObject["bookBalanceAmount"]?.jsonPrimitive?.content } catch(_: Exception) { null }
            } else null

            PaymentsRes(
                success = true,
                payments = paymentsList,
                walletBalance = balance,
                message = if (hasDues == false) duesResp.get("message")?.let { try { it.jsonPrimitive.content } catch(_: Exception) { null } } else null
            )
        } catch (e: Exception) {
            PaymentsRes(success = false, message = e.message, error = e.toString())
        }
    }

    suspend fun getLibrary(libUsername: String? = null, libPassword: String? = null): LibraryRes {
if (useMockData) return DemoData.get("library", LibraryRes.serializer()) ?: LibraryRes()
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

    suspend fun searchLibrary(query: String, index: String = "kw", offset: Int = 0): KohaSearchRes {
if (useMockData) return DemoData.get("librarySearch", LibraryRes.serializer())?.let { mock ->
            KohaSearchRes(
                success = mock.success,
                books = mock.searchResults.map { KohaBook(biblionumber = it.bookId, title = it.title, author = it.author ?: "") },
                total = mock.total,
                error = mock.error,
                message = mock.message
            )
        } ?: KohaSearchRes()
        return try {
            val response: HttpResponse = httpClient.get("$baseUrl/api/koha/search?q=${query.encodeURLParameter()}&idx=${index.encodeURLParameter()}&offset=$offset&count=20")
            jsonConfig.decodeFromString(response.bodyAsText())
        } catch (e: Exception) {
            KohaSearchRes(success = false, message = e.message, error = e.toString())
        }
    }

    suspend fun getKohaDetail(biblionumber: String): KohaDetailRes {
        if (useMockData) return KohaDetailRes(success = false, error = "No mock data for detail")
        return try {
            val response: HttpResponse = httpClient.get("$baseUrl/api/koha/detail?biblionumber=${biblionumber.encodeURLParameter()}")
            jsonConfig.decodeFromString(response.bodyAsText())
        } catch (e: Exception) {
            KohaDetailRes(success = false, message = e.message, error = e.toString())
        }
    }
    
    suspend fun renewLibraryBook(bookId: String): BasicRes {
if (useMockData) return DemoData.get("libraryRenew", BasicRes.serializer()) ?: BasicRes(success = true, message = "Book renewed successfully")
        return try {
            val creds = com.amazecc.app.shared.repository.SettingsManager.getLibraryCredentials()
            if (creds == null) return BasicRes(success = false, message = "Library credentials not found.")
            val response: HttpResponse = httpClient.post("$baseUrl/api/koha/renew") {
                contentType(io.ktor.http.ContentType.Application.Json)
                setBody(mapOf("username" to creds.first, "password" to creds.second, "bookId" to bookId))
            }
            if (response.status.value in 200..299) {
                jsonConfig.decodeFromString(response.bodyAsText())
            } else {
                BasicRes(success = false, message = "HTTP ${response.status}")
            }
        } catch (e: Exception) {
            BasicRes(success = false, message = e.message)
        }
    }
    suspend fun getTransportData(): TransportDataRes {
if (useMockData) return DemoData.get("transport", TransportDataRes.serializer()) ?: TransportDataRes()
        return try {
            postAuthorized<TransportDataRes>("transport") ?: TransportDataRes(success = false, message = "Empty response")
        } catch (e: Exception) {
            TransportDataRes(success = false, message = e.message, error = e.toString())
        }
    }

    suspend fun getBuses(): BusesRes {
if (useMockData) return DemoData.get("buses", BusesRes.serializer()) ?: BusesRes()
        return try {
            val response: HttpResponse = httpClient.get("$baseUrl/api/buses")
            if (response.status == HttpStatusCode.OK) {
                jsonConfig.decodeFromString(response.bodyAsText())
            } else {
                BusesRes(success = false, message = "Server returned status ${response.status}", error = "HTTP ${response.status}")
            }
        } catch (e: Exception) {
            BusesRes(success = false, message = "Network error: ${e.message}", error = e.toString())
        }
    }

    suspend fun submitTransportRegistration(request: TransportRegRequest): TransportRegSubmitRes {
if (useMockData) return DemoData.get("transportRegister", TransportRegSubmitRes.serializer()) ?: TransportRegSubmitRes()
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


    suspend fun cabShareAuth(username: String, password: String, phoneNumber: String): CabShareAuthRes {
if (useMockData) return DemoData.get("cabShareAuth", CabShareAuthRes.serializer()) ?: CabShareAuthRes()
        return try {
            val params = buildJsonObject {
                put("phone_number", phoneNumber)
                put("username", username)
                put("password", password)
            }
            postAuthorizedBody<CabShareAuthRes>("cabshare/auth", params.toString())
                ?: CabShareAuthRes(success = false, error = "Empty response")
        } catch (e: Exception) {
            CabShareAuthRes(success = false, error = e.message)
        }
    }

    suspend fun getCabHubs(): List<CabShareHub> {
        return fallbackCabHubs
    }

    suspend fun searchCabShareTrips(fromHubId: Int?, toHubId: Int?, date: String): CabShareTripsRes {
if (useMockData) return DemoData.get("cabShareSearch", CabShareTripsRes.serializer()) ?: CabShareTripsRes()
        return try {
            val params = mutableMapOf<String, String>()
            fromHubId?.let { params["from_hub_id"] = it.toString() }
            toHubId?.let { params["hub_id"] = it.toString() }
            params["date"] = date
            params["reg_number"] = SessionManager.authorizedID.value ?: ""
            postAuthorized<CabShareTripsRes>("cabshare/trips", params)
                ?: CabShareTripsRes(success = false, error = "Empty response")
        } catch (e: Exception) {
            CabShareTripsRes(success = false, error = e.message)
        }
    }

    suspend fun createCabShareTrip(
        fromHubId: Int, toHubId: Int, date: String, time: String,
        tolerance: Double, seats: Int, gender: String, notes: String
    ): CabActionRes {
if (useMockData) return DemoData.get("cabShareCreate", CabActionRes.serializer()) ?: CabActionRes()
        return try {
            val body = buildJsonObject {
                put("reg_number", SessionManager.authorizedID.value ?: "")
                put("from_hub_id", fromHubId)
                put("hub_id", toHubId)
                put("travel_date", date)
                put("preferred_time", time)
                put("tolerance_hours", tolerance)
                put("gender_preference", gender)
                put("notes", notes)
                put("seat_options", buildJsonObject {
                    put("requested", seats)
                })
            }
            postAuthorizedBody<CabActionRes>("cabshare/trips", body.toString())
                ?: CabActionRes(success = false, message = "Empty response")
        } catch (e: Exception) {
            CabActionRes(success = false, message = e.message, error = e.toString())
        }
    }

    suspend fun getMyCabShareTrips(regNumber: String): CabShareTripsRes {
if (useMockData) return DemoData.get("cabShareMyTrips", CabShareTripsRes.serializer()) ?: CabShareTripsRes()
        return try {
            postAuthorized<CabShareTripsRes>("cabshare/trips/me?reg_number=$regNumber")
                ?: CabShareTripsRes(success = false, error = "Empty response")
        } catch (e: Exception) {
            CabShareTripsRes(success = false, error = e.message)
        }
    }

    suspend fun requestCabShareJoin(regNumber: String, tripId: Long): CabActionRes {
        return try {
            val body = buildJsonObject {
                put("reg_number", regNumber)
                put("trip_id", tripId)
                put("action", "request")
            }
            postAuthorizedBody<CabActionRes>("cabshare/match", body.toString())
                ?: CabActionRes(success = false, message = "Empty response")
        } catch (e: Exception) {
            CabActionRes(success = false, message = e.message)
        }
    }

    suspend fun cabShareMatchAction(regNumber: String, matchId: Long, action: String): CabActionRes {
        return try {
            val body = buildJsonObject {
                put("reg_number", regNumber)
                put("match_id", matchId)
                put("action", action)
            }
            postAuthorizedBody<CabActionRes>("cabshare/match", body.toString())
                ?: CabActionRes(success = false, message = "Empty response")
        } catch (e: Exception) {
            CabActionRes(success = false, message = e.message)
        }
    }

    suspend fun getLMSAssignments(): LMSRes {
if (useMockData) return DemoData.get("lms", LMSRes.serializer()) ?: LMSRes()
        return try {
            postAuthorized<LMSRes>("lms-data") ?: LMSRes(success = false, message = "Empty response")
        } catch (e: Exception) {
            LMSRes(success = false, message = e.message, error = e.toString())
        }
    }
    suspend fun getQBankQuestions(courseCode: String): QBankRes {
if (useMockData) return DemoData.get("qbankQuestions", QBankRes.serializer()) ?: QBankRes()
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

    suspend fun getQBankPapers(courseCode: String): QBankPapersRes {
if (useMockData) return DemoData.get("qbankPapers", QBankPapersRes.serializer()) ?: QBankPapersRes()
        return try {
            val response: HttpResponse = httpClient.get("$baseUrl/api/qbank/papers?course=${courseCode.encodeURLParameter()}")
            if (response.status.value in 200..299) {
                response.body()
            } else {
                QBankPapersRes(success = false, message = "HTTP ${response.status}")
            }
        } catch (e: Exception) {
            QBankPapersRes(success = false, message = e.message, error = e.toString())
        }
    }

    suspend fun getQcmView(): QcmViewRes {
if (useMockData) return DemoData.get("qcmView", QcmViewRes.serializer()) ?: QcmViewRes()
        return try {
            postAuthorized<QcmViewRes>("qcm-view") ?: QcmViewRes(success = false, message = "Empty response")
        } catch (e: Exception) {
            QcmViewRes(success = false, message = e.message, error = e.toString())
        }
    }

    suspend fun getEvents(): EventHubRes {
if (useMockData) return DemoData.get("events", EventHubRes.serializer()) ?: EventHubRes()
        return try {
            val response = httpClient.get("$baseUrl/api/events") {
                contentType(ContentType.Application.Json)
            }
            if (response.status == HttpStatusCode.OK) {
                val body = response.bodyAsText()
                val element = jsonConfig.decodeFromString<JsonElement>(body)
                val eventsList = if (element is JsonArray) {
                    jsonConfig.decodeFromJsonElement<List<EventHubEvent>>(element)
                } else if (element.jsonObject["events"] is JsonArray) {
                    jsonConfig.decodeFromJsonElement<List<EventHubEvent>>(element.jsonObject["events"] as JsonArray)
                } else {
                    emptyList()
                }
                EventHubRes(success = true, events = eventsList)
            } else {
                EventHubRes(success = false, message = "Server returned ${response.status}")
            }
        } catch (e: Exception) {
            EventHubRes(success = false, message = "Network error: ${e.message}", error = e.toString())
        }
    }

    suspend fun getEventPreview(eid: String): EventHubPreview? {
        return try {
            val jsessionid = SessionManager.clubToken.value
            val creds = com.amazecc.app.shared.repository.SettingsManager.getCredentials()
            val response = httpClient.post("$baseUrl/api/events/preview") {
                contentType(ContentType.Application.Json)
                setBody(buildJsonObject {
                    put("eid", eid)
                    if (jsessionid != null) put("jsessionid", jsessionid)
                    if (creds != null) {
                        put("username", creds.first)
                        put("password", creds.second)
                    }
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
            val jsessionid = SessionManager.clubToken.value
            val creds = com.amazecc.app.shared.repository.SettingsManager.getCredentials()
            val response = httpClient.post("$baseUrl/api/events/register") {
                contentType(ContentType.Application.Json)
                setBody(buildJsonObject {
                    put("eid", eid)
                    if (jsessionid != null) put("jsessionid", jsessionid)
                    if (creds != null) {
                        put("username", creds.first)
                        put("password", creds.second)
                    }
                })
            }
            if (response.status == HttpStatusCode.OK) {
                jsonConfig.decodeFromString<EventHubRegisterRes>(response.bodyAsText())
            } else null
        } catch (e: Exception) {
            null
        }
    }

    suspend fun eventLogin(): String? {
        val creds = com.amazecc.app.shared.repository.SettingsManager.getCredentials() ?: return null
        return try {
            val response = httpClient.post("$baseUrl/api/events/login") {
                contentType(ContentType.Application.Json)
                setBody(buildJsonObject {
                    put("username", creds.first)
                    put("password", creds.second)
                })
            }
            if (response.status == HttpStatusCode.OK) {
                val json = jsonConfig.decodeFromString<JsonElement>(response.bodyAsText()).jsonObject
                val jsessionid = json["jsessionid"]?.jsonPrimitive?.content
                if (jsessionid != null) {
                    SessionManager.saveEventHubSession(jsessionid)
                }
                jsessionid
            } else null
        } catch (e: Exception) {
            null
        }
    }

    suspend fun getEventsProfile(): EventHubRegisteredEventsRes {
if (useMockData) return DemoData.get("eventsProfile", EventHubRegisteredEventsRes.serializer()) ?: EventHubRegisteredEventsRes()
        return try {
            val creds = com.amazecc.app.shared.repository.SettingsManager.getCredentials()
            val extraParams = mutableMapOf<String, String>()
            val jsessionid = SessionManager.clubToken.value
            if (jsessionid != null) extraParams["jsessionid"] = jsessionid
            if (creds != null) {
                extraParams["username"] = creds.first
                extraParams["password"] = creds.second
            }
            postAuthorized<EventHubRegisteredEventsRes>("events/profile", extraParams)
                ?: EventHubRegisteredEventsRes(success = false, message = "Empty response")
        } catch (e: Exception) {
            EventHubRegisteredEventsRes(success = false, message = "Network error: ${e.message}", error = e.toString())
        }
    }

    suspend fun getClubsDetails(): ClubsRes {
if (useMockData) return DemoData.get("clubs", ClubsRes.serializer()) ?: ClubsRes()
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

    suspend fun getImageBytes(url: String): ByteArray? {
        return try {
            val response: HttpResponse = httpClient.get(url) {
                val token = SessionManager.clubToken.value
                if (!token.isNullOrEmpty() && url.contains("eventhubcc.vit.ac.in")) {
                    header(HttpHeaders.Cookie, "JSESSIONID=$token")
                }
            }
            if (response.status == HttpStatusCode.OK) {
                response.body<ByteArray>()
            } else null
        } catch (e: Exception) {
            null
        }
    }

    suspend fun getStudentProfile(): StudentProfileRes {
if (useMockData) return DemoData.get("studentProfile", StudentProfileRes.serializer()) ?: StudentProfileRes()
        return try {
            postAuthorized<StudentProfileRes>("student") ?: StudentProfileRes(success = false, message = "Empty response")
        } catch (e: Exception) {
            StudentProfileRes(success = false, message = e.message, error = e.toString())
        }
    }

    suspend fun getProfileImages(): ProfileImagesRes {
if (useMockData) return DemoData.get("profileImages", ProfileImagesRes.serializer()) ?: ProfileImagesRes()
        return try {
            postAuthorized<ProfileImagesRes>("profile-images") ?: ProfileImagesRes(success = false)
        } catch (e: Exception) {
            ProfileImagesRes(success = false, error = e.toString())
        }
    }

    suspend fun getCredentials(): CredentialsRes {
        if (useMockData) return DemoData.get("credentials", CredentialsRes.serializer()) ?: CredentialsRes(success = true)
        return try {
            postAuthorized<CredentialsRes>("credentials") ?: CredentialsRes(success = false)
        } catch (e: Exception) {
            CredentialsRes(success = false, error = e.toString())
        }
    }

    suspend fun getEptSchedule(): EptScheduleRes {
        if (useMockData) return DemoData.get("eptSchedule", EptScheduleRes.serializer()) ?: EptScheduleRes(success = true)
        return try {
            postAuthorized<EptScheduleRes>("ept-schedule") ?: EptScheduleRes(success = false)
        } catch (e: Exception) { EptScheduleRes(success = false, error = e.toString()) }
    }

    suspend fun getRegistrationSchedule(): RegistrationScheduleRes {
        if (useMockData) return DemoData.get("registrationSchedule", RegistrationScheduleRes.serializer()) ?: RegistrationScheduleRes(success = true)
        return try {
            postAuthorized<RegistrationScheduleRes>("registration-schedule") ?: RegistrationScheduleRes(success = false)
        } catch (e: Exception) { RegistrationScheduleRes(success = false, error = e.toString()) }
    }

    suspend fun getUniversityDay(): UniversityDayRes {
        if (useMockData) return DemoData.get("universityDay", UniversityDayRes.serializer()) ?: UniversityDayRes(success = true)
        return try {
            postAuthorized<UniversityDayRes>("university-day") ?: UniversityDayRes(success = false)
        } catch (e: Exception) { UniversityDayRes(success = false, error = e.toString()) }
    }

    suspend fun getBankInfo(): BankInfoRes {
        if (useMockData) return DemoData.get("bankInfo", BankInfoRes.serializer()) ?: BankInfoRes(success = true)
        return try {
            postAuthorized<BankInfoRes>("bank-info") ?: BankInfoRes(success = false)
        } catch (e: Exception) { BankInfoRes(success = false, error = e.toString()) }
    }

    suspend fun getDayboarderInfo(): DayboarderRes {
        if (useMockData) return DemoData.get("dayboarder", DayboarderRes.serializer()) ?: DayboarderRes(success = true)
        return try {
            postAuthorized<DayboarderRes>("dayboarder") ?: DayboarderRes(success = false)
        } catch (e: Exception) { DayboarderRes(success = false, error = e.toString()) }
    }

    suspend fun getApaarId(): ApaarIdRes {
        if (useMockData) return DemoData.get("apaarId", ApaarIdRes.serializer()) ?: ApaarIdRes(success = true)
        return try {
            postAuthorized<ApaarIdRes>("apaarid") ?: ApaarIdRes(success = false)
        } catch (e: Exception) { ApaarIdRes(success = false, error = e.toString()) }
    }

    /**
     * Consolidated identity fetch — student + profile-images + credentials +
     * apaar + bank in a single request. Demo mode reports failure so the caller
     * falls back to the per-endpoint demo sweep.
     */
    suspend fun getMe(): MeRes {
        if (useMockData) return MeRes(success = false, error = "demo mode")
        return try {
            postAuthorized<MeRes>("me") ?: MeRes(success = false, error = "Empty response")
        } catch (e: Exception) { MeRes(success = false, error = e.toString()) }
    }

    suspend fun getCirculars(): CircularsRes {
if (useMockData) return DemoData.get("circulars", CircularsRes.serializer()) ?: CircularsRes()
        return postAuthorized<CircularsRes>("circulars") ?: CircularsRes(success = false, message = "Empty response")
    }


    // â”€â”€ Phase 3 endpoints â”€â”€

    suspend fun postQBankPaper(courseCode: String, title: String, link: String, type: String): QBankSubmitRes? {
if (useMockData) return DemoData.get("qbankUpload", QBankSubmitRes.serializer()) ?: QBankSubmitRes(true)
        return postAuthorized("qbank/upload", mapOf(
            "courseCode" to courseCode,
            "title" to title,
            "link" to link,
            "type" to type
        ))
    }

    suspend fun getQBankCourses(): QBankCoursesRes {
if (useMockData) return DemoData.get("qbankCourses", QBankCoursesRes.serializer()) ?: QBankCoursesRes()
        return try {
            // The API exposes a GET-only endpoint returning { data: [{ code, title }] }.
            // The demo payload uses { courses: [{ courseCode, courseTitle }] } — accept both.
            val response: HttpResponse = httpClient.get("$baseUrl/api/qbank/courses")
            if (response.status == HttpStatusCode.OK) {
                val element = jsonConfig.decodeFromString<JsonElement>(response.bodyAsText())
                val json = element.jsonObject
                val courses = when {
                    json["data"] is JsonArray ->
                        jsonConfig.decodeFromJsonElement<List<QBankCourseRaw>>(json["data"] as JsonArray)
                            .map { QBankCourse(courseCode = it.code, courseTitle = it.title) }
                    json["courses"] is JsonArray ->
                        jsonConfig.decodeFromJsonElement<List<QBankCourse>>(json["courses"] as JsonArray)
                    else -> emptyList()
                }
                QBankCoursesRes(success = true, courses = courses)
            } else {
                QBankCoursesRes(success = false, message = "HTTP ${response.status}")
            }
        } catch (e: Exception) {
            QBankCoursesRes(success = false, message = e.message, error = e.toString())
        }
    }

    suspend fun getFacultySchools(): FacultySchoolsRes {
if (useMockData) return DemoData.get("facultySchools", FacultySchoolsRes.serializer()) ?: FacultySchoolsRes()
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
if (useMockData) return DemoData.get("facultyScrape", FacultyScrapeRes.serializer()) ?: FacultyScrapeRes()
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

    suspend fun getFacultyProfile(employeeId: String): FacultyProfile? {
        return try {
            val response: HttpResponse = httpClient.get("https://directorycc.vit.ac.in/api/faculty/$employeeId")
            if (response.status == HttpStatusCode.OK) {
                val p: DirectoryCCProfile = jsonConfig.decodeFromString(response.bodyAsText())
                FacultyProfile(
                    id = p.employeeId,
                    name = p.name,
                    designation = p.designation,
                    email = p.email,
                    employeeId = p.employeeId,
                    intercom = p.intercom
                )
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun directorySchoolIds(schoolHint: String?): Set<String> {
        if (schoolHint.isNullOrBlank()) return emptySet()
        return when (schoolHint.trim().uppercase()) {
            "SCOPE" -> setOf("scope")
            "SELECT" -> setOf("select")
            "SENSE" -> setOf("sense")
            "SMEC" -> setOf("smec")
            "SCE" -> setOf("sce")
            "SSL" -> setOf("SSL")
            "SBST" -> setOf("SBST")
            "VSMART", "V-SMART", "SMART" -> setOf("V-SMART")
            "VFIT", "VFSI" -> setOf("vfsi")
            "VITSOL", "VSL" -> setOf("vsl")
            "SAS" -> setOf("SASP", "SASM", "SASC")
            else -> emptySet() // unknown / university-wide: search all schools
        }
    }

    /**
     * Looks up a faculty member's details from the directorycc.vit.ac.in directory.
     * 1. Tries the VTOP employee id directly.
     * 2. Falls back to a roster search (scraped school lists) matching the given name exactly or 95%+.
     * The name used for matching is the VTOP/FFCS name, not the directory name.
     */
    suspend fun searchFacultyDirectory(name: String, idHint: String? = null, schoolHint: String? = null): FacultyProfile? {
        if (useMockData) return null

        val directId = idHint?.takeIf { it.isNotBlank() && it.all { c -> c.isDigit() } }
        if (directId != null) {
            val direct = getFacultyProfile(directId)
            if (direct != null && direct.name.isNotBlank()) return direct
        }

        val targetIds = directorySchoolIds(schoolHint)
        val schoolsRes = getFacultySchools()
        for (school in schoolsRes.schools) {
            if (targetIds.isNotEmpty() && school.id !in targetIds) continue
            val roster = postFacultyScrape(school.id)
            if (!roster.success) continue
            for (f in roster.faculties) {
                if (FacultyUtils.nameSimilarity(name, f.name) >= 0.95) {
                    val detail = if (f.employeeId.isNotBlank()) getFacultyProfile(f.employeeId) ?: f else f
                    return detail
                }
            }
        }
        return null
    }

    suspend fun getCourseOptionChange(): ArrearResponse {
if (useMockData) return DemoData.get("courseOptionChange", ArrearResponse.serializer()) ?: ArrearResponse()
        return postAuthorized<ArrearResponse>("course-option-change") ?: ArrearResponse(success = false, message = "Empty response")
    }

    suspend fun getExcRegistration(): ArrearResponse {
if (useMockData) return DemoData.get("excRegistration", ArrearResponse.serializer()) ?: ArrearResponse()
        return postAuthorized<ArrearResponse>("exc-registration") ?: ArrearResponse(success = false, message = "Empty response")
    }

    suspend fun getMinorHonour(): ArrearResponse {
if (useMockData) return DemoData.get("minorHonour", ArrearResponse.serializer()) ?: ArrearResponse()
        return postAuthorized<ArrearResponse>("minor-honour") ?: ArrearResponse(success = false, message = "Empty response")
    }

    suspend fun getCourseCompletion(): ArrearResponse {
if (useMockData) return DemoData.get("courseCompletion", ArrearResponse.serializer()) ?: ArrearResponse()
        return postAuthorized<ArrearResponse>("course-completion") ?: ArrearResponse(success = false, message = "Empty response")
    }

    suspend fun getProjects(): ArrearResponse {
if (useMockData) return DemoData.get("projects", ArrearResponse.serializer()) ?: ArrearResponse()
        return postAuthorized<ArrearResponse>("project") ?: ArrearResponse(success = false, message = "Empty response")
    }

    suspend fun getWishlist(): ArrearResponse {
if (useMockData) return DemoData.get("wishlist", ArrearResponse.serializer()) ?: ArrearResponse()
        return postAuthorized<ArrearResponse>("wishlist") ?: ArrearResponse(success = false, message = "Empty response")
    }

    suspend fun getFeedbackStatus(semesterId: String? = null): FeedbackStatusRes {
        if (useMockData) return DemoData.get("feedback", FeedbackStatusRes.serializer()) ?: FeedbackStatusRes()
        return try {
            val params = if (semesterId != null) mapOf("semesterId" to semesterId) else emptyMap()
            postAuthorized<FeedbackStatusRes>("feedback-status", params) ?: FeedbackStatusRes(success = false, error = "Empty response")
        } catch (e: Exception) {
            FeedbackStatusRes(success = false, error = e.message)
        }
    }

    suspend fun getGrades(semesterId: String? = null): SemesterGradesRes {
        if (useMockData) return DemoData.get("grades", SemesterGradesRes.serializer()) ?: SemesterGradesRes()
        return try {
            val params = if (semesterId != null) mapOf("semesterId" to semesterId) else emptyMap()
            postAuthorized<SemesterGradesRes>("grades", params) ?: SemesterGradesRes(success = false, error = "Empty response")
        } catch (e: Exception) {
            SemesterGradesRes(success = false, error = e.message)
        }
    }

    suspend fun getBonafide(): ArrearResponse {
if (useMockData) return DemoData.get("bonafide", ArrearResponse.serializer()) ?: ArrearResponse()
        return postAuthorized<ArrearResponse>("bonafide") ?: ArrearResponse(success = false, message = "Empty response")
    }

    suspend fun getETranscript(): ArrearResponse {
if (useMockData) return DemoData.get("eTranscript", ArrearResponse.serializer()) ?: ArrearResponse()
        return postAuthorized<ArrearResponse>("e-transcript") ?: ArrearResponse(success = false, message = "Empty response")
    }

    suspend fun getAdditionalLearning(): ArrearResponse {
if (useMockData) return DemoData.get("additionalLearning", ArrearResponse.serializer()) ?: ArrearResponse()
        return postAuthorized<ArrearResponse>("additional-learning") ?: ArrearResponse(success = false, message = "Empty response")
    }

    suspend fun getClubFeed(): FeedRes {
        return try {
            val response: HttpResponse = httpClient.get("$baseUrl/api/club-admin/feed")
            if (response.status == HttpStatusCode.OK) {
                jsonConfig.decodeFromString<FeedRes>(response.bodyAsText())
            } else {
                FeedRes(success = false, error = "HTTP ${response.status}")
            }
        } catch (e: Exception) {
            FeedRes(success = false, error = e.toString())
        }
    }

    /**
     * Fetches the syllabus PDF for a course.
     *
     * VTOP sessions rot quickly, so if the session is older than [SESSION_MAX_AGE_MS]
     * the client silently re-logs-in with the stored credentials before downloading.
     * [onProgress] reports download progress in 0f..1f.
     */
    suspend fun getSyllabusPdf(
        courseCode: String,
        onProgress: (Float) -> Unit = {}
    ): SyllabusResult {
        var cookies = SessionManager.cookies.value
        var authorizedID = SessionManager.authorizedID.value
        var csrf = SessionManager.csrf.value
        if (cookies == null || authorizedID == null || csrf == null || SessionManager.isSessionStale(SESSION_MAX_AGE_MS)) {
            if (refreshSession()) {
                cookies = SessionManager.cookies.value
                authorizedID = SessionManager.authorizedID.value
                csrf = SessionManager.csrf.value
            }
        }
        if (cookies == null || authorizedID == null || csrf == null) {
            return SyllabusResult(error = "Not signed in - refresh in Settings to reconnect")
        }
        return try {
            val response: HttpResponse = httpClient.post("$baseUrl/api/curriculum/syllabus") {
                contentType(ContentType.Application.Json)
                setBody(buildJsonObject {
                    put("cookies", cookies)
                    put("authorizedID", authorizedID)
                    put("csrf", csrf)
                    put("courseCode", normalizeCourseCode(courseCode))
                })
            }
            if (response.status == HttpStatusCode.OK) {
                val contentType = response.headers["Content-Type"].orEmpty().lowercase()
                if (contentType.contains("json")) {
                    val body = response.bodyAsText().trim().take(200)
                    SyllabusResult(error = "Server error: ${body.ifBlank { "empty response" }}")
                } else {
                    val extension = parseFileExtension(
                        response.headers["Content-Disposition"],
                        contentType
                    )
                    val bytes = response.readBytesWithProgress(onProgress)
                    SyllabusResult(download = SyllabusDownload(bytes, extension))
                }
            } else {
                val code = response.status.value
                SyllabusResult(
                    error = if (code == 404) {
                        "Syllabus not available for this course"
                    } else {
                        "Server error (HTTP $code) - try again later"
                    }
                )
            }
        } catch (e: Exception) {
            SyllabusResult(error = "Network error: ${e.message}")
        }
    }

    /** Re-logs-in to VTOP with the stored credentials and refreshes the session. */
    private suspend fun refreshSession(): Boolean {
        val credentials = SettingsManager.getCredentials() ?: return false
        val response = login(credentials.first, credentials.second)
        if (response.success && response.cookies != null && response.csrf != null && response.authorizedID != null) {
            SessionManager.saveSession(response.cookies, response.csrf, response.authorizedID, response.clubToken)
            return true
        }
        return false
    }

    private suspend fun HttpResponse.readBytesWithProgress(onProgress: (Float) -> Unit): ByteArray {
        val channel = bodyAsChannel()
        val total = contentLength()
        var received = 0L
        val chunks = mutableListOf<ByteArray>()
        while (true) {
            val packet = channel.readRemaining(16 * 1024L)
            if (packet.remaining == 0L) break
            val size = packet.remaining.toInt()
            val chunk = ByteArray(size)
            packet.readFully(chunk)
            chunks.add(chunk)
            received += size
            if (total != null && total > 0L) {
                onProgress((received.toFloat() / total).coerceIn(0f, 1f))
            }
        }
        onProgress(1f)
        val out = ByteArray(received.toInt())
        var offset = 0
        for (chunk in chunks) {
            chunk.copyInto(out, offset)
            offset += chunk.size
        }
        return out
    }

    suspend fun checkForUpdate(): GitHubRelease {
        val url = "https://api.github.com/repos/${UpdateConfig.GITHUB_OWNER}/${UpdateConfig.GITHUB_REPO}/releases/latest"
        return httpClient.get(url).body()
    }

    const val SESSION_MAX_AGE_MS = 5 * 60_000L
}

data class SyllabusDownload(
    val bytes: ByteArray,
    val extension: String
)

data class SyllabusResult(
    val download: SyllabusDownload? = null,
    val error: String? = null
)

private fun normalizeCourseCode(code: String): String {
    var normalized = code.replace(Regex("\\([LPT]\\)$"), "").trim()
    if (normalized.length > 4 && normalized.lastOrNull() in listOf('L', 'T', 'P')) {
        normalized = normalized.dropLast(1)
    }
    return normalized
}

private fun parseFileExtension(contentDisposition: String?, contentType: String?): String {
    val fromDisposition = contentDisposition
        ?.substringAfter("filename=", "")
        ?.substringBefore(';')
        ?.trim(' ', '"', '\'')
        ?.substringAfterLast('.')
        ?.substringBefore('?')
        ?.lowercase()
    if (!fromDisposition.isNullOrBlank() && fromDisposition.length <= 6 && fromDisposition.all { it.isLetterOrDigit() }) {
        return fromDisposition
    }
    val ct = contentType?.lowercase() ?: ""
    return when {
        ct.contains("pdf") -> "pdf"
        ct.contains("zip") -> "zip"
        ct.contains("wordprocessingml") -> "docx"
        ct.contains("msword") -> "doc"
        ct.contains("spreadsheetml") -> "xlsx"
        ct.contains("excel") -> "xls"
        else -> "bin"
    }
}



