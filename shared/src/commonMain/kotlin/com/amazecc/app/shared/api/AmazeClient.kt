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
    var baseUrl: String
        get() = SettingsManager.getString(SettingsManager.KEY_HONO_API_URL, "https://hono.amazecc.com").ifBlank { "https://hono.amazecc.com" }
        set(value) {
            SettingsManager.setString(SettingsManager.KEY_HONO_API_URL, value)
        }
    private var useMockData = false // Toggle for offline testing

    /**
     * Where VTOP-backed data is fetched from. `LOCAL` uses the on-device WebView engine,
     * `REMOTE` proxies through [baseUrl].
     */
    val vtopSource: com.amazecc.app.shared.vtop.VtopSource
        get() = com.amazecc.app.shared.vtop.VtopSource
            .fromName(SettingsManager.getVtopSourceName())

    fun setVtopSource(source: com.amazecc.app.shared.vtop.VtopSource) {
        SettingsManager.setVtopSourceName(source.name)
    }

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

    suspend fun pingHealth(): Pair<Boolean, Long> {
        return try {
            val start = Clock.System.now().toEpochMilliseconds()
            val response: HttpResponse = httpClient.get("$baseUrl/api/health")
            val elapsed = Clock.System.now().toEpochMilliseconds() - start
            Pair(response.status.value in 200..299, elapsed)
        } catch (_: Exception) {
            Pair(false, -1L)
        }
    }

    suspend fun login(
        username: String,
        password: String,
        captchaHandler: com.amazecc.app.shared.vtop.CaptchaHandler = com.amazecc.app.shared.vtop.CaptchaHandler { challenge ->
            com.amazecc.app.shared.vtop.Vtop.awaitUiCaptcha(challenge)
        }
    ): LoginResponse {
        if (useMockData) return DemoData.get("login", LoginResponse.serializer()) ?: LoginResponse(success = true, message = "Demo login successful")

        if (vtopSource == com.amazecc.app.shared.vtop.VtopSource.OFFLINE_CACHE) {
            val regNo = com.amazecc.app.shared.state.UserStore.identity.value.regNo.takeIf { !it.isNullOrBlank() } ?: username
            return LoginResponse(
                success = true,
                message = "Offline Cache Login",
                authorizedID = regNo,
                cookies = "offline_cache",
                csrf = "offline_cache"
            )
        }

        if (vtopSource == com.amazecc.app.shared.vtop.VtopSource.LOCAL) {
            return loginLocal(username, password, captchaHandler)
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

    /**
     * On-device VTOP login.
     *
     * Reaches VTOP directly because the server cannot: VTOP accepts connections only from
     * Indian IP space and this API is served from Singapore. Returns the same [LoginResponse]
     * shape the remote path does, so callers are unchanged. The cookie field carries
     * [SessionManager.LOCAL_SESSION_MARKER] because the real `JSESSIONID` stays in the WebView.
     */
    internal suspend fun loginLocal(
        username: String,
        password: String,
        captchaHandler: com.amazecc.app.shared.vtop.CaptchaHandler = com.amazecc.app.shared.vtop.CaptchaHandler { challenge ->
            com.amazecc.app.shared.vtop.Vtop.awaitUiCaptcha(challenge)
        },
        forceFresh: Boolean = false
    ): LoginResponse {
        if (!com.amazecc.app.shared.vtop.Vtop.isSupported) {
            return LoginResponse(
                success = false,
                message = "On-device VTOP is not supported on this platform. Switch the VTOP source to Remote in Settings.",
                error = "unsupported_platform"
            )
        }
        if (captchaHandler == null) {
            return LoginResponse(
                success = false,
                message = "A captcha handler is required for on-device login.",
                error = "missing_captcha_handler"
            )
        }

        return when (val result = com.amazecc.app.shared.vtop.Vtop.login(username, password, captchaHandler, forceFresh = forceFresh)) {
            is com.amazecc.app.shared.vtop.LoginResult.Success -> LoginResponse(
                success = true,
                message = "Login successful!",
                cookies = SessionManager.LOCAL_SESSION_MARKER,
                csrf = result.csrf,
                authorizedID = result.authorizedID,
                clubToken = null
            )
            is com.amazecc.app.shared.vtop.LoginResult.InvalidCaptcha ->
                LoginResponse(false, "Invalid Captcha", error = "invalid_captcha")
            is com.amazecc.app.shared.vtop.LoginResult.InvalidCredentials ->
                LoginResponse(false, "Invalid Username / Password", error = "invalid_credentials")
            is com.amazecc.app.shared.vtop.LoginResult.AccountLocked ->
                LoginResponse(false, "Account is locked. Contact the VTOP administrator.", error = "account_locked")
            is com.amazecc.app.shared.vtop.LoginResult.MaxAttempts ->
                LoginResponse(false, "Maximum login attempts reached. Try again later.", error = "max_attempts")
            is com.amazecc.app.shared.vtop.LoginResult.Failure ->
                LoginResponse(false, result.message, error = "vtop_failure")
            else ->
                LoginResponse(false, "Login failed for an unknown reason.", error = "unknown")
        }
    }

    // Helper for POST requests carrying cookies, authorizedID, and csrf
    private suspend inline fun <reified T> postAuthorized(endpoint: String, extraParams: Map<String, String> = emptyMap()): T? {
        // In LOCAL mode the real JSESSIONID lives in the WebView cookie jar and was never
        // meant to leave the device. There is nothing valid to send.
        if (vtopSource != com.amazecc.app.shared.vtop.VtopSource.REMOTE) return null
        if (!SessionManager.hasRemoteCookies) return null
        val cookies = SessionManager.cookies.value ?: return null
        val authorizedID = SessionManager.authorizedID.value ?: return null
        val csrf = SessionManager.csrf.value ?: return null

        return try {
            val response: HttpResponse = httpClient.post("$baseUrl/api/$endpoint") {
                contentType(ContentType.Application.Json)
                setBody(buildJsonObject {
                    put("cookies", cookies)
                    put("authorizedID", authorizedID)
                    put("csrf", csrf)
                    extraParams.forEach { (k, v) -> put(k, v) }
                })
            }
            if (response.status == HttpStatusCode.OK) {
                jsonConfig.decodeFromString<T>(response.bodyAsText())
            } else {
                null
            }
        } catch (e: Exception) {
            // DNS resolution failure (e.g. UnknownHostException for hono.amazecc.com), timeout, or offline
            null
        }
    }

    private suspend inline fun <reified T> postAuthorizedBody(endpoint: String, body: String): T? {
        if (vtopSource != com.amazecc.app.shared.vtop.VtopSource.REMOTE) return null
        if (!SessionManager.hasRemoteCookies) return null
        val cookies = SessionManager.cookies.value ?: return null
        val authorizedID = SessionManager.authorizedID.value ?: return null
        val csrf = SessionManager.csrf.value ?: return null
        return try {
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
            if (response.status == HttpStatusCode.OK) {
                jsonConfig.decodeFromString<T>(response.bodyAsText())
            } else null
        } catch (e: Exception) {
            null
        }
    }


    // ── Offline-First Local Cache Fallbacks ──────────────────────────────────

    fun fallbackCachedTimetable(semesterId: String?): TimetableRes? {
        val academic = com.amazecc.app.shared.state.AppDataStore.academic.value
        val sem = (if (semesterId != null) academic.semesters[semesterId] else null)
            ?: academic.semesters.values.firstOrNull()
            ?: return null
        if (sem.courses.isEmpty()) return null
        val courses = sem.courses.values.mapIndexed { index, c ->
            com.amazecc.app.shared.state.TimetableCourseInfo(
                slNo = (index + 1).toString(),
                course = c.courseTitle,
                courseCode = c.courseCode,
                LTPJC = c.credits.orEmpty(),
                category = c.category.orEmpty(),
                classId = c.classId.orEmpty(),
                slotVenue = c.venue.orEmpty(),
                facultyDetails = c.faculty.orEmpty()
            )
        }
        com.amazecc.app.shared.state.AppState.markServedFromCache("Timetable")
        return TimetableRes(
            success = true,
            semesterId = sem.semesterId,
            courseInfo = courses,
            message = "Served from local cache"
        )
    }

    fun fallbackCachedMarks(semesterId: String?): MarksRes? {
        val academic = com.amazecc.app.shared.state.AppDataStore.academic.value
        val sem = (if (semesterId != null) academic.semesters[semesterId] else null)
            ?: academic.semesters.values.firstOrNull()
            ?: return null
        val marksCourses = sem.courses.values.mapNotNull { c ->
            val m = c.marks ?: return@mapNotNull null
            MarksCourseItem(
                classNbr = m.classNbr.orEmpty(),
                courseCode = c.courseCode,
                courseTitle = c.courseTitle,
                courseType = c.courseType,
                faculty = c.faculty.orEmpty(),
                slot = c.slots.joinToString("+"),
                credits = c.credits.orEmpty(),
                assessments = m.assessments
            )
        }
        if (marksCourses.isEmpty()) return null
        com.amazecc.app.shared.state.AppState.markServedFromCache("Marks")
        return MarksRes(
            success = true,
            courses = marksCourses,
            cgpa = sem.gpa?.let { CGPAResult(cgpa = it) },
            message = "Served from local cache"
        )
    }

    fun fallbackCachedAttendance(semesterId: String?): AttendanceRes? {
        val academic = com.amazecc.app.shared.state.AppDataStore.academic.value
        val sem = (if (semesterId != null) academic.semesters[semesterId] else null)
            ?: academic.semesters.values.firstOrNull()
            ?: return null
        val items = sem.courses.values.mapNotNull { c ->
            val att = c.attendance ?: return@mapNotNull null
            AttendanceItem(
                courseCode = c.courseCode,
                courseTitle = c.courseTitle,
                courseType = c.courseType,
                slotName = c.slots.joinToString("+"),
                faculty = c.faculty.orEmpty(),
                attendedClasses = att.attendedClasses,
                totalClasses = att.totalClasses,
                attendancePercentage = att.attendancePercentage,
                logs = att.logs,
                credits = c.credits,
                slotVenue = c.venue,
                category = c.category
            )
        }
        if (items.isEmpty()) return null
        com.amazecc.app.shared.state.AppState.markServedFromCache("Attendance")
        return AttendanceRes(
            success = true,
            semesterId = sem.semesterId,
            attendance = items,
            message = "Served from local cache"
        )
    }

    fun fallbackCachedExamSchedule(semesterId: String?): ExamScheduleRes? {
        val academic = com.amazecc.app.shared.state.AppDataStore.academic.value
        val sem = (if (semesterId != null) academic.semesters[semesterId] else null)
            ?: academic.semesters.values.firstOrNull()
            ?: return null
        if (sem.exams.isEmpty()) return null
        com.amazecc.app.shared.state.AppState.markServedFromCache("Exam Schedule")
        return ExamScheduleRes(
            success = true,
            rawScheduleUpper = mapOf("EXAM" to sem.exams),
            message = "Served from local cache"
        )
    }

    fun fallbackCachedStudentProfile(): StudentProfileRes? {
        val identity = com.amazecc.app.shared.state.UserStore.identity.value
        if (identity.regNo.isNullOrBlank() && identity.name.isNullOrBlank()) return null
        val profile = StudentProfile(
            regNo = identity.regNo.orEmpty(),
            name = identity.name.orEmpty(),
            email = identity.email.orEmpty(),
            mobile = identity.mobile.orEmpty(),
            program = identity.program.orEmpty(),
            campus = identity.campus.orEmpty(),
            advisorName = identity.proctor?.name
        )
        com.amazecc.app.shared.state.AppState.markServedFromCache("Profile")
        return StudentProfileRes(
            success = true,
            data = profile,
            message = "Served from local cache"
        )
    }

    fun fallbackCachedAllGrades(): AllGradesRes {
        val academic = com.amazecc.app.shared.state.AppDataStore.academic.value
        val semesterId = com.amazecc.app.shared.vtop.VtopSession.semesterSubId.value.orEmpty()
        val sem = (if (semesterId.isNotBlank()) academic.semesters[semesterId] else null)
            ?: academic.semesters.values.firstOrNull()
        val gradeRows = sem?.courses?.values?.mapNotNull { c ->
            val g = c.grade ?: return@mapNotNull null
            val gradeLetter = g.grade ?: return@mapNotNull null
            com.amazecc.app.shared.model.GradeItem(
                courseTitle = c.courseTitle,
                courseType = c.courseType,
                grade = gradeLetter,
                grandTotal = ""
            )
        }.orEmpty()
        com.amazecc.app.shared.state.AppState.markServedFromCache("Grade History")
        return AllGradesRes(
            success = true,
            grades = mapOf(semesterId to com.amazecc.app.shared.model.SemesterGradeResult(grades = gradeRows)),
            message = "Served from local cache"
        )
    }

    fun fallbackCachedGrades(semesterId: String?): SemesterGradesRes? {
        val academic = com.amazecc.app.shared.state.AppDataStore.academic.value
        val sem = (if (semesterId != null) academic.semesters[semesterId] else null)
            ?: academic.semesters.values.firstOrNull()
            ?: return null
        val effectiveRows = sem.courses.values.mapNotNull { c ->
            val g = c.grade ?: return@mapNotNull null
            com.amazecc.app.shared.model.EffectiveGradeCourse(
                basketTitle = "${c.courseCode} - ${c.courseTitle}",
                courseType = c.courseType,
                creditsEarned = c.credits,
                grade = g.grade
            )
        }
        if (effectiveRows.isEmpty()) return null
        com.amazecc.app.shared.state.AppState.markServedFromCache("Grades")
        return SemesterGradesRes(
            success = true,
            effectiveGrades = effectiveRows
        )
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
        val semester = semesterId ?: com.amazecc.app.shared.vtop.VtopSession.semesterSubId.value
        if (vtopSource == com.amazecc.app.shared.vtop.VtopSource.OFFLINE_CACHE) {
            val cachedAtt = fallbackCachedAttendance(semesterId)
                ?: AttendanceRes(success = true, semesterId = semesterId ?: "", message = "Served from local cache")
            val cachedMarks = fallbackCachedMarks(semesterId)
            return AcademicSyncResult(attendance = cachedAtt, marks = cachedMarks)
        }
        if (vtopSource == com.amazecc.app.shared.vtop.VtopSource.LOCAL) {
            return try {
                if (semester == null) {
                    val cachedAtt = fallbackCachedAttendance(semesterId)
                    val cachedMarks = fallbackCachedMarks(semesterId)
                    return if (cachedAtt != null) AcademicSyncResult(cachedAtt, cachedMarks)
                    else AcademicSyncResult(AttendanceRes(success = false, error = "No semester selected"))
                }
                val att = com.amazecc.app.shared.vtop.VtopDataSource.fetchAttendance(semester)
                val marks = com.amazecc.app.shared.vtop.VtopDataSource.fetchMarks(semester)
                if (att.success && att.attendance?.isNotEmpty() == true) {
                    AcademicSyncResult(attendance = att, marks = marks ?: fallbackCachedMarks(semester))
                } else {
                    val cachedAtt = fallbackCachedAttendance(semester)
                    val cachedMarks = marks ?: fallbackCachedMarks(semester)
                    if (cachedAtt != null) AcademicSyncResult(attendance = cachedAtt, marks = cachedMarks)
                    else AcademicSyncResult(attendance = att, marks = marks)
                }
            } catch (e: Exception) {
                val cachedAtt = fallbackCachedAttendance(semesterId)
                val cachedMarks = fallbackCachedMarks(semesterId)
                if (cachedAtt != null) AcademicSyncResult(attendance = cachedAtt, marks = cachedMarks)
                else AcademicSyncResult(AttendanceRes(success = false, error = e.message ?: e.toString()))
            }
        }

        // REMOTE mode: try remote, fall back to local scraper, then local cache
        try {
            val params = if (semesterId != null) mapOf("semesterId" to semesterId) else emptyMap()
            val response = postAuthorized<AttendanceSyncResponse>("attendance", params)
            if (response != null) {
                val attendance = response.attRes ?: AttendanceRes(
                    success = response.success,
                    semesterId = response.semesterId,
                    attendance = response.attendance,
                    error = response.error,
                    message = response.message
                )
                if (attendance.success && attendance.attendance?.isNotEmpty() == true) {
                    return AcademicSyncResult(
                        attendance = attendance.copy(success = attendance.success && attendance.error == null),
                        marks = response.marksRes ?: fallbackCachedMarks(semesterId)
                    )
                }
            }
        } catch (_: Exception) {}

        // Fall back to on-device scraper
        if (com.amazecc.app.shared.vtop.vtopEngineSupported && com.amazecc.app.shared.vtop.VtopSession.hasSession && semester != null) {
            try {
                val att = com.amazecc.app.shared.vtop.VtopDataSource.fetchAttendance(semester)
                val marks = com.amazecc.app.shared.vtop.VtopDataSource.fetchMarks(semester)
                if (att.success && att.attendance?.isNotEmpty() == true) {
                    return AcademicSyncResult(attendance = att, marks = marks ?: fallbackCachedMarks(semester))
                }
            } catch (_: Exception) {}
        }

        // Fall back to local cache
        val cachedAtt = fallbackCachedAttendance(semesterId)
        val cachedMarks = fallbackCachedMarks(semesterId)
        return if (cachedAtt != null) AcademicSyncResult(cachedAtt, cachedMarks)
        else AcademicSyncResult(AttendanceRes(success = false, message = "Attendance unavailable"))
    }

    suspend fun getTimetable(semesterId: String? = null): TimetableRes {
        if (useMockData) return DemoData.get("timetable", TimetableRes.serializer()) ?: TimetableRes()
        val semester = semesterId ?: com.amazecc.app.shared.vtop.VtopSession.semesterSubId.value

        if (vtopSource == com.amazecc.app.shared.vtop.VtopSource.OFFLINE_CACHE) {
            return fallbackCachedTimetable(semesterId)
                ?: TimetableRes(success = true, semesterId = semesterId ?: "", message = "Served from local cache")
        }

        if (vtopSource == com.amazecc.app.shared.vtop.VtopSource.LOCAL) {
            return try {
                if (semester == null) return fallbackCachedTimetable(semesterId) ?: TimetableRes(success = false, error = "No semester selected")
                val res = com.amazecc.app.shared.vtop.VtopDataSource.fetchTimetable(semester)
                if (res.success && res.courseInfo?.isNotEmpty() == true) res
                else fallbackCachedTimetable(semester) ?: res
            } catch (e: Exception) {
                fallbackCachedTimetable(semesterId) ?: TimetableRes(success = false, error = e.message ?: e.toString())
            }
        }

        // REMOTE mode: try remote, fall back to on-device scraper, then local cache
        try {
            val params = if (semesterId != null) mapOf("semesterId" to semesterId) else emptyMap()
            val res = postAuthorized<TimetableRes>("timetable", params)
            if (res != null && res.success && res.courseInfo?.isNotEmpty() == true) return res
        } catch (_: Exception) {}

        if (com.amazecc.app.shared.vtop.vtopEngineSupported && com.amazecc.app.shared.vtop.VtopSession.hasSession && semester != null) {
            try {
                val localRes = com.amazecc.app.shared.vtop.VtopDataSource.fetchTimetable(semester)
                if (localRes.success && localRes.courseInfo?.isNotEmpty() == true) return localRes
            } catch (_: Exception) {}
        }

        return fallbackCachedTimetable(semesterId) ?: TimetableRes(success = false, message = "Empty response")
    }

    suspend fun getMarks(semesterId: String? = null): MarksRes {
        if (useMockData) return DemoData.get("marks", MarksRes.serializer()) ?: MarksRes()
        val semester = semesterId ?: com.amazecc.app.shared.vtop.VtopSession.semesterSubId.value

        if (vtopSource == com.amazecc.app.shared.vtop.VtopSource.OFFLINE_CACHE) {
            return fallbackCachedMarks(semesterId)
                ?: MarksRes(success = true, message = "Served from local cache")
        }

        if (vtopSource == com.amazecc.app.shared.vtop.VtopSource.LOCAL) {
            return try {
                if (semester == null) return fallbackCachedMarks(semesterId) ?: MarksRes(success = false, error = "No semester selected")
                val res = com.amazecc.app.shared.vtop.VtopDataSource.fetchMarks(semester)
                if (res != null && res.success && res.courses?.isNotEmpty() == true) res
                else fallbackCachedMarks(semester) ?: res ?: MarksRes(success = false, error = "VTOP marks unavailable")
            } catch (e: Exception) {
                fallbackCachedMarks(semesterId) ?: MarksRes(success = false, error = e.message ?: e.toString())
            }
        }

        // REMOTE mode: try remote, fall back to on-device scraper, then local cache
        try {
            val params = if (semesterId != null) mapOf("semesterId" to semesterId) else emptyMap()
            val res = postAuthorized<MarksRes>("marks", params)
            if (res != null && res.success && res.courses?.isNotEmpty() == true) return res
        } catch (_: Exception) {}

        if (com.amazecc.app.shared.vtop.vtopEngineSupported && com.amazecc.app.shared.vtop.VtopSession.hasSession && semester != null) {
            try {
                val localRes = com.amazecc.app.shared.vtop.VtopDataSource.fetchMarks(semester)
                if (localRes != null && localRes.success && localRes.courses?.isNotEmpty() == true) return localRes
            } catch (_: Exception) {}
        }

        return fallbackCachedMarks(semesterId) ?: MarksRes(success = false, message = "Empty response")
    }

    suspend fun getAllGrades(): AllGradesRes {
        if (useMockData) return DemoData.get("allGrades", AllGradesRes.serializer()) ?: AllGradesRes()
        if (vtopSource == com.amazecc.app.shared.vtop.VtopSource.OFFLINE_CACHE) {
            return fallbackCachedAllGrades()
        }
        if (vtopSource == com.amazecc.app.shared.vtop.VtopSource.LOCAL) {
            return try {
                val fetched = com.amazecc.app.shared.vtop.VtopDataSource.fetchGrades()
                if (fetched != null) {
                    val semesterId = com.amazecc.app.shared.vtop.VtopSession.semesterSubId.value ?: ""
                    val rows = fetched.effectiveGrades.orEmpty().mapNotNull { row ->
                        val title = row.basketTitle?.trim().orEmpty()
                        val grade = row.grade?.trim().orEmpty()
                        if (title.isEmpty() && grade.isEmpty()) return@mapNotNull null
                        com.amazecc.app.shared.model.GradeItem(
                            courseTitle = title,
                            courseType = row.courseType.orEmpty(),
                            grade = grade,
                            grandTotal = ""
                        )
                    }
                    if (rows.isNotEmpty()) {
                        AllGradesRes(
                            success = true,
                            grades = mapOf(semesterId to com.amazecc.app.shared.model.SemesterGradeResult(grades = rows))
                        )
                    } else fallbackCachedAllGrades()
                } else fallbackCachedAllGrades()
            } catch (e: Exception) {
                fallbackCachedAllGrades()
            }
        }
        return try {
            val remote = postAuthorized<AllGradesRes>("all-grades")
            if (remote != null && remote.success) {
                remote
            } else {
                // Remote failed or returned empty: fallback to local scraper if possible, then cache
                try {
                    val fetched = com.amazecc.app.shared.vtop.VtopDataSource.fetchGrades()
                    if (fetched != null) {
                        val semesterId = com.amazecc.app.shared.vtop.VtopSession.semesterSubId.value ?: ""
                        val rows = fetched.effectiveGrades.orEmpty().mapNotNull { row ->
                            val title = row.basketTitle?.trim().orEmpty()
                            val grade = row.grade?.trim().orEmpty()
                            if (title.isEmpty() && grade.isEmpty()) return@mapNotNull null
                            com.amazecc.app.shared.model.GradeItem(
                                courseTitle = title,
                                courseType = row.courseType.orEmpty(),
                                grade = grade,
                                grandTotal = ""
                            )
                        }
                        if (rows.isNotEmpty()) {
                            AllGradesRes(
                                success = true,
                                grades = mapOf(semesterId to com.amazecc.app.shared.model.SemesterGradeResult(grades = rows))
                            )
                        } else fallbackCachedAllGrades()
                    } else fallbackCachedAllGrades()
                } catch (_: Exception) {
                    fallbackCachedAllGrades()
                }
            }
        } catch (e: Exception) {
            // UnknownHostException / Network error: graceful fallback to local cache
            fallbackCachedAllGrades()
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
        if (vtopSource == com.amazecc.app.shared.vtop.VtopSource.OFFLINE_CACHE) {
            return com.amazecc.app.shared.state.AppDataStore.hostelDetails.value
                ?: HostelDetails(success = true, message = "Served from local cache")
        }
        if (vtopSource == com.amazecc.app.shared.vtop.VtopSource.LOCAL) {
            return try {
                com.amazecc.app.shared.vtop.VtopDataSource.fetchHostel()
                    ?: HostelDetails(success = false, error = "VTOP hostel details unavailable")
            } catch (e: Exception) {
                HostelDetails(success = false, error = e.message ?: e.toString())
            }
        }
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
        val semester = semesterId ?: com.amazecc.app.shared.vtop.VtopSession.semesterSubId.value

        if (vtopSource == com.amazecc.app.shared.vtop.VtopSource.OFFLINE_CACHE) {
            return fallbackCachedExamSchedule(semesterId)
                ?: ExamScheduleRes(success = true, message = "Served from local cache")
        }

        if (vtopSource == com.amazecc.app.shared.vtop.VtopSource.LOCAL && semester != null) {
            return try {
                val res = com.amazecc.app.shared.vtop.VtopDataSource.fetchExamSchedule(semester)
                if (res != null && res.success && res.schedule.isNotEmpty()) res
                else fallbackCachedExamSchedule(semester) ?: res ?: ExamScheduleRes(success = false, error = "VTOP exam schedule unavailable")
            } catch (e: Exception) {
                fallbackCachedExamSchedule(semesterId) ?: ExamScheduleRes(success = false, error = e.toString())
            }
        }

        try {
            val params = mutableMapOf<String, String>()
            if (semesterId != null) params["semesterId"] = semesterId
            val res = postAuthorized<ExamScheduleRes>("schedule", params)
            if (res != null && res.success && res.schedule.isNotEmpty()) return res
        } catch (_: Exception) {}

        if (com.amazecc.app.shared.vtop.vtopEngineSupported && com.amazecc.app.shared.vtop.VtopSession.hasSession && semester != null) {
            try {
                val localRes = com.amazecc.app.shared.vtop.VtopDataSource.fetchExamSchedule(semester)
                if (localRes != null && localRes.success && localRes.schedule.isNotEmpty()) return localRes
            } catch (_: Exception) {}
        }

        return fallbackCachedExamSchedule(semesterId) ?: ExamScheduleRes(success = false, message = "Empty response")
    }

    suspend fun getCurriculum(semesterId: String? = null): CurriculumRes {
if (useMockData) return DemoData.get("curriculum", CurriculumRes.serializer()) ?: CurriculumRes()
        if (vtopSource == com.amazecc.app.shared.vtop.VtopSource.OFFLINE_CACHE) {
            return com.amazecc.app.shared.state.AppDataStore.curriculum.value
                ?: CurriculumRes(success = true, message = "Served from local cache")
        }
        if (vtopSource == com.amazecc.app.shared.vtop.VtopSource.LOCAL) {
            return try {
                com.amazecc.app.shared.vtop.VtopDataSource.fetchCurriculum()
                    ?: CurriculumRes(success = false, error = "VTOP curriculum unavailable")
            } catch (e: Exception) {
                CurriculumRes(success = false, error = e.toString())
            }
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
if (useMockData) return DemoData.get("calendar", CalendarRes.serializer()) ?: CalendarRes()
        if (vtopSource == com.amazecc.app.shared.vtop.VtopSource.OFFLINE_CACHE) {
            return com.amazecc.app.shared.state.AppDataStore.calendar.value
                ?: CalendarRes(success = true, message = "Served from local cache")
        }
        return try {
            val rawJson = if (vtopSource == com.amazecc.app.shared.vtop.VtopSource.LOCAL && semesterId != null) {
                // Same analysis path as REMOTE — only the transport differs.
                com.amazecc.app.shared.vtop.VtopDataSource.fetchCalendarRaw(semesterId, type)
            } else {
                val params = mutableMapOf("type" to type)
                if (semesterId != null) params["semesterId"] = semesterId
                postAuthorized<JsonElement>("calendar", params)
            }
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
        if (vtopSource == com.amazecc.app.shared.vtop.VtopSource.OFFLINE_CACHE) {
            return com.amazecc.app.shared.state.AppDataStore.calendarsList.value
                ?: CalendarsListRes(success = true, message = "Served from local cache")
        }
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
        if (vtopSource == com.amazecc.app.shared.vtop.VtopSource.OFFLINE_CACHE) {
            return com.amazecc.app.shared.state.AppDataStore.payments.value
                ?: PaymentsRes(success = true, message = "Served from local cache")
        }
        if (vtopSource == com.amazecc.app.shared.vtop.VtopSource.LOCAL) {
            return try {
                com.amazecc.app.shared.vtop.VtopDataSource.fetchPayments()
                    ?: PaymentsRes(success = false, error = "VTOP payments unavailable")
            } catch (e: Exception) {
                PaymentsRes(success = false, error = e.message ?: e.toString())
            }
        }
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
        if (vtopSource == com.amazecc.app.shared.vtop.VtopSource.LOCAL) {
            return try {
                com.amazecc.app.shared.vtop.VtopDataSource.fetchTransport()
                    ?: TransportDataRes(success = false, message = "VTOP transport unavailable")
            } catch (e: Exception) {
                TransportDataRes(success = false, message = e.message, error = e.toString())
            }
        }
        return try {
            val res = postAuthorized<TransportDataRes>("transport")
            if (res != null && res.success) res
            else {
                if (com.amazecc.app.shared.vtop.vtopEngineSupported && com.amazecc.app.shared.vtop.VtopSession.hasSession) {
                    try {
                        val local = com.amazecc.app.shared.vtop.VtopDataSource.fetchTransport()
                        if (local != null && local.success) return local
                    } catch (_: Exception) {}
                }
                res ?: TransportDataRes(success = false, message = "Empty response")
            }
        } catch (e: Exception) {
            if (com.amazecc.app.shared.vtop.vtopEngineSupported && com.amazecc.app.shared.vtop.VtopSession.hasSession) {
                try {
                    val local = com.amazecc.app.shared.vtop.VtopDataSource.fetchTransport()
                    if (local != null && local.success) return local
                } catch (_: Exception) {}
            }
            TransportDataRes(success = false, message = e.message, error = e.toString())
        }
    }

    suspend fun getOdDetails(semesterId: String): com.amazecc.app.shared.model.OdDetailsRes {
        if (vtopSource == com.amazecc.app.shared.vtop.VtopSource.LOCAL) {
            return try {
                com.amazecc.app.shared.vtop.VtopDataSource.fetchOdDetails(semesterId)
                    ?: com.amazecc.app.shared.model.OdDetailsRes(success = false, error = "VTOP OD details unavailable")
            } catch (e: Exception) {
                com.amazecc.app.shared.model.OdDetailsRes(success = false, error = e.message ?: e.toString())
            }
        }
        return try {
            val res = postAuthorized<com.amazecc.app.shared.model.OdDetailsRes>("od", mapOf("semesterId" to semesterId))
            if (res != null && res.success) res
            else {
                if (com.amazecc.app.shared.vtop.vtopEngineSupported && com.amazecc.app.shared.vtop.VtopSession.hasSession) {
                    try {
                        val local = com.amazecc.app.shared.vtop.VtopDataSource.fetchOdDetails(semesterId)
                        if (local != null && local.success) return local
                    } catch (_: Exception) {}
                }
                res ?: com.amazecc.app.shared.model.OdDetailsRes(success = false, error = "Empty response")
            }
        } catch (e: Exception) {
            if (com.amazecc.app.shared.vtop.vtopEngineSupported && com.amazecc.app.shared.vtop.VtopSession.hasSession) {
                try {
                    val local = com.amazecc.app.shared.vtop.VtopDataSource.fetchOdDetails(semesterId)
                    if (local != null && local.success) return local
                } catch (_: Exception) {}
            }
            com.amazecc.app.shared.model.OdDetailsRes(success = false, error = e.message ?: e.toString())
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
        if (vtopSource == com.amazecc.app.shared.vtop.VtopSource.OFFLINE_CACHE) {
            return com.amazecc.app.shared.state.AppDataStore.lms.value
                ?: LMSRes(success = true, message = "Served from local cache")
        }
        if (vtopSource == com.amazecc.app.shared.vtop.VtopSource.LOCAL) {
            // Moodle has its own login, so its credentials are stored separately from VTOP's.
            val creds = com.amazecc.app.shared.repository.SettingsManager.getMoodleCredentials()
                ?: return LMSRes(
                    success = false,
                    message = "Moodle credentials not set",
                )
            return try {
                com.amazecc.app.shared.vtop.VtopLms.fetchAssignments(creds.first, creds.second)
                    ?: LMSRes(success = false, message = "Moodle unavailable")
            } catch (e: Exception) {
                LMSRes(success = false, message = "Network error: ${e.message}", error = e.toString())
            }
        }
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
        if (vtopSource == com.amazecc.app.shared.vtop.VtopSource.OFFLINE_CACHE) {
            return com.amazecc.app.shared.state.AppDataStore.qcmView.value
                ?: QcmViewRes(success = true)
        }
        if (vtopSource == com.amazecc.app.shared.vtop.VtopSource.LOCAL) {
            return try {
                com.amazecc.app.shared.vtop.VtopDataSource.fetchQcmView()
                    ?: QcmViewRes(success = false, error = "VTOP QCM view unavailable")
            } catch (e: Exception) {
                QcmViewRes(success = false, error = e.toString())
            }
        }
        return try {
            postAuthorized<QcmViewRes>("qcm") ?: QcmViewRes(success = false, message = "Empty response")
        } catch (e: Exception) {
            QcmViewRes(success = false, message = e.message, error = e.toString())
        }
    }

    suspend fun getEvents(): EventHubRes {
if (useMockData) return DemoData.get("events", EventHubRes.serializer()) ?: EventHubRes()
        if (vtopSource == com.amazecc.app.shared.vtop.VtopSource.LOCAL) {
            // EventHub is public, so there are no credentials to check here.
            return try {
                val local = com.amazecc.app.shared.vtop.VtopEventHub.fetchEvents()
                    ?: return EventHubRes(success = false, message = "EventHub unavailable")
                EventHubRes(success = true, events = local)
            } catch (e: Exception) {
                EventHubRes(success = false, message = "Network error: ${e.message}", error = e.toString())
            }
        }
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
        val creds = com.amazecc.app.shared.repository.SettingsManager.getCredentials()
        if (vtopSource == com.amazecc.app.shared.vtop.VtopSource.LOCAL && creds != null) {
            return try {
                com.amazecc.app.shared.vtop.VtopEventHub.fetchEventsProfile(creds.first, creds.second)
                    ?: EventHubRegisteredEventsRes(success = false, error = "EventHub unavailable")
            } catch (e: Exception) {
                EventHubRegisteredEventsRes(success = false, message = "Network error: ${e.message}", error = e.toString())
            }
        }
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

    suspend fun getClubs(): ClubsRes = getClubsDetails()

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

        if (vtopSource == com.amazecc.app.shared.vtop.VtopSource.OFFLINE_CACHE) {
            return fallbackCachedStudentProfile()
                ?: StudentProfileRes(success = true, message = "Served from local cache")
        }

        if (vtopSource == com.amazecc.app.shared.vtop.VtopSource.LOCAL) {
            return try {
                val res = com.amazecc.app.shared.vtop.VtopDataSource.fetchStudentProfile()
                if (res != null && res.success && res.data != null) res
                else fallbackCachedStudentProfile() ?: res ?: StudentProfileRes(success = false, error = "VTOP student profile unavailable")
            } catch (e: Exception) {
                fallbackCachedStudentProfile() ?: StudentProfileRes(success = false, error = e.toString())
            }
        }

        try {
            val res = postAuthorized<StudentProfileRes>("student")
            if (res != null && res.success && res.data != null) return res
        } catch (_: Exception) {}

        if (com.amazecc.app.shared.vtop.vtopEngineSupported && com.amazecc.app.shared.vtop.VtopSession.hasSession) {
            try {
                val localRes = com.amazecc.app.shared.vtop.VtopDataSource.fetchStudentProfile()
                if (localRes != null && localRes.success && localRes.data != null) return localRes
            } catch (_: Exception) {}
        }

        return fallbackCachedStudentProfile() ?: StudentProfileRes(success = false, message = "Empty response")
    }

    suspend fun getProfileImages(): ProfileImagesRes {
if (useMockData) return DemoData.get("profileImages", ProfileImagesRes.serializer()) ?: ProfileImagesRes()
        if (vtopSource == com.amazecc.app.shared.vtop.VtopSource.OFFLINE_CACHE) return ProfileImagesRes(success = true)
        if (vtopSource == com.amazecc.app.shared.vtop.VtopSource.LOCAL) {
            return try {
                com.amazecc.app.shared.vtop.VtopDataSource.fetchProfileImages()
                    ?: ProfileImagesRes(success = false, error = "VTOP proctor details unavailable")
            } catch (e: Exception) {
                ProfileImagesRes(success = false, error = e.toString())
            }
        }
        return try {
            postAuthorized<ProfileImagesRes>("profile-images") ?: ProfileImagesRes(success = false)
        } catch (e: Exception) {
            ProfileImagesRes(success = false, error = e.toString())
        }
    }

    suspend fun getCredentials(): CredentialsRes {
        if (useMockData) return DemoData.get("credentials", CredentialsRes.serializer()) ?: CredentialsRes(success = true)
        if (vtopSource == com.amazecc.app.shared.vtop.VtopSource.OFFLINE_CACHE) return CredentialsRes(success = true)
        if (vtopSource == com.amazecc.app.shared.vtop.VtopSource.LOCAL) {
            return try {
                com.amazecc.app.shared.vtop.VtopDataSource.fetchCredentials()
                    ?: CredentialsRes(success = false, error = "VTOP credentials unavailable")
            } catch (e: Exception) {
                CredentialsRes(success = false, error = e.toString())
            }
        }
        return try {
            postAuthorized<CredentialsRes>("credentials") ?: CredentialsRes(success = false)
        } catch (e: Exception) {
            CredentialsRes(success = false, error = e.toString())
        }
    }

    suspend fun getEptSchedule(): EptScheduleRes {
        if (useMockData) return DemoData.get("eptSchedule", EptScheduleRes.serializer()) ?: EptScheduleRes(success = true)
        if (vtopSource == com.amazecc.app.shared.vtop.VtopSource.OFFLINE_CACHE) return EptScheduleRes(success = true)
        if (vtopSource == com.amazecc.app.shared.vtop.VtopSource.LOCAL) {
            return try {
                com.amazecc.app.shared.vtop.VtopDataSource.fetchEptSchedule()
                    ?: EptScheduleRes(success = false, error = "VTOP EPT schedule unavailable")
            } catch (e: Exception) {
                EptScheduleRes(success = false, error = e.toString())
            }
        }
        return try {
            postAuthorized<EptScheduleRes>("ept-schedule") ?: EptScheduleRes(success = false)
        } catch (e: Exception) { EptScheduleRes(success = false, error = e.toString()) }
    }

    suspend fun getRegistrationSchedule(): RegistrationScheduleRes {
        if (useMockData) return DemoData.get("registrationSchedule", RegistrationScheduleRes.serializer()) ?: RegistrationScheduleRes(success = true)
        if (vtopSource == com.amazecc.app.shared.vtop.VtopSource.OFFLINE_CACHE) return RegistrationScheduleRes(success = true)
        if (vtopSource == com.amazecc.app.shared.vtop.VtopSource.LOCAL) {
            return try {
                com.amazecc.app.shared.vtop.VtopDataSource.fetchRegistrationSchedule()
                    ?: RegistrationScheduleRes(success = false, error = "VTOP registration schedule unavailable")
            } catch (e: Exception) {
                RegistrationScheduleRes(success = false, error = e.toString())
            }
        }
        return try {
            postAuthorized<RegistrationScheduleRes>("registration-schedule") ?: RegistrationScheduleRes(success = false)
        } catch (e: Exception) { RegistrationScheduleRes(success = false, error = e.toString()) }
    }

    suspend fun getUniversityDay(): UniversityDayRes {
        if (useMockData) return DemoData.get("universityDay", UniversityDayRes.serializer()) ?: UniversityDayRes(success = true)
        if (vtopSource == com.amazecc.app.shared.vtop.VtopSource.OFFLINE_CACHE) return UniversityDayRes(success = true)
        if (vtopSource == com.amazecc.app.shared.vtop.VtopSource.LOCAL) {
            return try {
                com.amazecc.app.shared.vtop.VtopDataSource.fetchUniversityDay()
                    ?: UniversityDayRes(success = false, error = "VTOP university day details unavailable")
            } catch (e: Exception) {
                UniversityDayRes(success = false, error = e.toString())
            }
        }
        return try {
            postAuthorized<UniversityDayRes>("university-day") ?: UniversityDayRes(success = false)
        } catch (e: Exception) { UniversityDayRes(success = false, error = e.toString()) }
    }

    suspend fun getBankInfo(): BankInfoRes {
        if (useMockData) return DemoData.get("bankInfo", BankInfoRes.serializer()) ?: BankInfoRes(success = true)
        if (vtopSource == com.amazecc.app.shared.vtop.VtopSource.OFFLINE_CACHE) return BankInfoRes(success = true)
        if (vtopSource == com.amazecc.app.shared.vtop.VtopSource.LOCAL) {
            return try {
                com.amazecc.app.shared.vtop.VtopDataSource.fetchBankInfo()
                    ?: BankInfoRes(success = false, error = "VTOP bank info unavailable")
            } catch (e: Exception) {
                BankInfoRes(success = false, error = e.toString())
            }
        }
        return try {
            postAuthorized<BankInfoRes>("bank-info") ?: BankInfoRes(success = false)
        } catch (e: Exception) { BankInfoRes(success = false, error = e.toString()) }
    }

    suspend fun getDayboarderInfo(): DayboarderRes {
        if (useMockData) return DemoData.get("dayboarder", DayboarderRes.serializer()) ?: DayboarderRes(success = true)
        if (vtopSource == com.amazecc.app.shared.vtop.VtopSource.OFFLINE_CACHE) return DayboarderRes(success = true)
        if (vtopSource == com.amazecc.app.shared.vtop.VtopSource.LOCAL) {
            return try {
                com.amazecc.app.shared.vtop.VtopDataSource.fetchDayboarder()
                    ?: DayboarderRes(success = false, error = "VTOP dayboarder details unavailable")
            } catch (e: Exception) {
                DayboarderRes(success = false, error = e.toString())
            }
        }
        return try {
            postAuthorized<DayboarderRes>("dayboarder") ?: DayboarderRes(success = false)
        } catch (e: Exception) { DayboarderRes(success = false, error = e.toString()) }
    }

    suspend fun getApaarId(): ApaarIdRes {
        if (useMockData) return DemoData.get("apaarId", ApaarIdRes.serializer()) ?: ApaarIdRes(success = true)
        if (vtopSource == com.amazecc.app.shared.vtop.VtopSource.OFFLINE_CACHE) return ApaarIdRes(success = true)
        if (vtopSource == com.amazecc.app.shared.vtop.VtopSource.LOCAL) {
            return try {
                com.amazecc.app.shared.vtop.VtopDataSource.fetchApaarId()
                    ?: ApaarIdRes(success = false, error = "VTOP APAAR details unavailable")
            } catch (e: Exception) {
                ApaarIdRes(success = false, error = e.toString())
            }
        }
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
        if (vtopSource == com.amazecc.app.shared.vtop.VtopSource.OFFLINE_CACHE) {
            return com.amazecc.app.shared.state.AppDataStore.circulars.value
                ?: CircularsRes(success = true, message = "Served from local cache")
        }
        if (vtopSource == com.amazecc.app.shared.vtop.VtopSource.LOCAL) {
            return try {
                com.amazecc.app.shared.vtop.VtopDataSource.fetchCirculars()
                    ?: CircularsRes(success = false, error = "VTOP circulars unavailable")
            } catch (e: Exception) {
                CircularsRes(success = false, error = e.toString())
            }
        }
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
        if (vtopSource == com.amazecc.app.shared.vtop.VtopSource.LOCAL && semesterId != null) {
            return try {
                com.amazecc.app.shared.vtop.VtopDataSource.fetchFeedbackStatus(semesterId)
                    ?: FeedbackStatusRes(success = false, error = "VTOP feedback status unavailable")
            } catch (e: Exception) {
                FeedbackStatusRes(success = false, error = e.message ?: e.toString())
            }
        }
        return try {
            val params = if (semesterId != null) mapOf("semesterId" to semesterId) else emptyMap()
            val res = postAuthorized<FeedbackStatusRes>("feedback-status", params)
            if (res != null && res.success) res
            else {
                if (com.amazecc.app.shared.vtop.vtopEngineSupported && com.amazecc.app.shared.vtop.VtopSession.hasSession && semesterId != null) {
                    try {
                        val local = com.amazecc.app.shared.vtop.VtopDataSource.fetchFeedbackStatus(semesterId)
                        if (local != null && local.success) return local
                    } catch (_: Exception) {}
                }
                res ?: FeedbackStatusRes(success = false, error = "Empty response")
            }
        } catch (e: Exception) {
            if (com.amazecc.app.shared.vtop.vtopEngineSupported && com.amazecc.app.shared.vtop.VtopSession.hasSession && semesterId != null) {
                try {
                    val local = com.amazecc.app.shared.vtop.VtopDataSource.fetchFeedbackStatus(semesterId)
                    if (local != null && local.success) return local
                } catch (_: Exception) {}
            }
            FeedbackStatusRes(success = false, error = e.message)
        }
    }

    suspend fun getGrades(semesterId: String? = null): SemesterGradesRes {
        if (useMockData) return DemoData.get("grades", SemesterGradesRes.serializer()) ?: SemesterGradesRes()
        if (vtopSource == com.amazecc.app.shared.vtop.VtopSource.LOCAL) {
            return try {
                com.amazecc.app.shared.vtop.VtopDataSource.fetchGrades()
                    ?: fallbackCachedGrades(semesterId)
                    ?: SemesterGradesRes(success = false, error = "VTOP grades unavailable")
            } catch (e: Exception) {
                fallbackCachedGrades(semesterId) ?: SemesterGradesRes(success = false, error = e.message ?: e.toString())
            }
        }
        return try {
            val params = if (semesterId != null) mapOf("semesterId" to semesterId) else emptyMap()
            val remote = postAuthorized<SemesterGradesRes>("grades", params)
            if (remote != null && remote.success) remote
            else {
                com.amazecc.app.shared.vtop.VtopDataSource.fetchGrades()
                    ?: fallbackCachedGrades(semesterId)
                    ?: SemesterGradesRes(success = false, error = "Empty response")
            }
        } catch (e: Exception) {
            try {
                com.amazecc.app.shared.vtop.VtopDataSource.fetchGrades()
                    ?: fallbackCachedGrades(semesterId)
                    ?: SemesterGradesRes(success = false, error = e.message)
            } catch (_: Exception) {
                fallbackCachedGrades(semesterId) ?: SemesterGradesRes(success = false, error = e.message)
            }
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

    /**
     * Test seam: when non-null, replaces the real VTOP login inside [ensureSession].
     * Receives the effective `forceFresh` flag so tests can assert the sync gate forces a real
     * login. Null in production. Set/cleared only by auth-gate tests.
     */
    internal var sessionLoginOverride: (suspend (forceFresh: Boolean) -> Boolean)? = null

    /**
     * Guarantees that [com.amazecc.app.shared.vtop.VtopSession] and [SessionManager] hold valid,
     * fresh authentication tokens before any sync or scraping operation begins.
     *
     * In [com.amazecc.app.shared.vtop.VtopSource.OFFLINE_CACHE], always returns true.
     *
     * In [com.amazecc.app.shared.vtop.VtopSource.LOCAL]:
     * 1. When [forceFresh] is true (the sync-gate default), a cached-fresh session is **never**
     *    accepted. Cached tokens are discarded, the WebView `JSESSIONID` is dropped and a real
     *    VTOP login runs: prelogin/setup -> login (with [captchaHandler]) -> open page -> authorizedID.
     * 2. When [forceFresh] is false (per-module verification only), a still-fresh session is reused;
     *    otherwise silent restoration via [com.amazecc.app.shared.vtop.Vtop.restoreSessionIfPossible()]
     *    is attempted before falling back to [loginLocal] with stored credentials.
     * 3. Upon successful authentication, tokens are captured into
     *    [com.amazecc.app.shared.vtop.VtopSession], marked active, persisted via
     *    [SessionManager.saveSession] and `SettingsManager`, and the identity is merged into [UserStore].
     *
     * In [com.amazecc.app.shared.vtop.VtopSource.REMOTE]:
     * A cached session is accepted only when not [forceFresh]; otherwise [refreshSession] performs a
     * fresh login through the Hono backend (which runs the canonical VTOP flow server-side).
     *
     * @param forceFresh If true, always performs a real VTOP login and never accepts a cached-fresh
     *   session. Sync sweeps pass true; per-module token checks pass false.
     * @param captchaHandler Handler to resolve captcha challenges. Defaults to [com.amazecc.app.shared.vtop.Vtop.awaitUiCaptcha].
     * @param verifyOnly When true, confirm the cached session is still valid and return immediately —
     *   no cookie clearing, no WebView, no re-login. This is the background-sweep path: a
     *   BroadcastReceiver has no Activity to host the VTOP WebView, so a forced interactive login
     *   cannot run there and silently attempting one would either throw or hang on the captcha.
     * @return true if an authenticated, unexpired session is ready; false otherwise.
     */
    suspend fun ensureSession(
        forceFresh: Boolean = false,
        captchaHandler: com.amazecc.app.shared.vtop.CaptchaHandler = com.amazecc.app.shared.vtop.CaptchaHandler { challenge ->
            com.amazecc.app.shared.vtop.Vtop.awaitUiCaptcha(challenge)
        },
        verifyOnly: Boolean = false
    ): Boolean {
        if (vtopSource == com.amazecc.app.shared.vtop.VtopSource.OFFLINE_CACHE) return true

        // Pure verification first: deliberately ahead of the test seam so a background sweep can be
        // proven not to enter any login path. Never clears cookies or touches the WebView.
        if (verifyOnly) {
            return if (vtopSource == com.amazecc.app.shared.vtop.VtopSource.LOCAL) {
                com.amazecc.app.shared.vtop.VtopSession.isValid
            } else {
                SessionManager.isLoggedIn && !SessionManager.isSessionStale(
                    com.amazecc.app.shared.vtop.VtopSession.SESSION_REFRESH_THRESHOLD_MS
                )
            }
        }

        // Test seam runs before any cached-session short-circuit so tests can prove the login path
        // is reached even when a fresh session is already present.
        sessionLoginOverride?.let { return it(forceFresh) }

        // 1. If not forcing fresh and session is still fresh and valid, return true immediately
        if (!forceFresh) {
            if (vtopSource == com.amazecc.app.shared.vtop.VtopSource.LOCAL) {
                if (com.amazecc.app.shared.vtop.VtopSession.hasSession && !com.amazecc.app.shared.vtop.VtopSession.isExpired) {
                    return true
                }
            } else {
                if (SessionManager.isLoggedIn && !SessionManager.isSessionStale(com.amazecc.app.shared.vtop.VtopSession.SESSION_REFRESH_THRESHOLD_MS)) {
                    return true
                }
            }
        }

        // 2. Local mode session re-establishment
        if (vtopSource == com.amazecc.app.shared.vtop.VtopSource.LOCAL) {
            // Step A: Attempt silent re-hydration from WebView cookies
            if (!forceFresh && com.amazecc.app.shared.vtop.Vtop.restoreSessionIfPossible() && !com.amazecc.app.shared.vtop.VtopSession.isExpired) {
                com.amazecc.app.shared.vtop.VtopSession.markActive()
                val csrf = com.amazecc.app.shared.vtop.VtopSession.csrf.value
                val id = com.amazecc.app.shared.vtop.VtopSession.authorizedID.value
                if (!csrf.isNullOrBlank() && !id.isNullOrBlank()) {
                    SessionManager.saveSession(
                        cookies = SessionManager.LOCAL_SESSION_MARKER,
                        csrf = csrf,
                        authorizedID = id,
                        clubToken = null
                    )
                    SettingsManager.setString(SettingsManager.SESSION_COOKIES, SessionManager.LOCAL_SESSION_MARKER)
                    SettingsManager.setString(SettingsManager.SESSION_CSRF, csrf)
                    SettingsManager.setString(SettingsManager.SESSION_AUTHORIZED_ID, id)
                    com.amazecc.app.shared.state.UserStore.merge(
                        com.amazecc.app.shared.state.IdentityExtractor.fromSession(id),
                        com.amazecc.app.shared.state.IdentitySource.SESSION
                    )
                    return true
                }
            }

            // Step B: Auto-login with stored credentials. When forcing fresh, discard the cached
            // in-memory tokens first so only tokens captured by THIS login can satisfy the gate.
            if (forceFresh) com.amazecc.app.shared.vtop.VtopSession.clear()
            val creds = SettingsManager.getCredentials() ?: return false
            if (creds.first.isBlank() || creds.second.isBlank()) return false

            val loginRes = loginLocal(creds.first, creds.second, captchaHandler, forceFresh = forceFresh)
            if (loginRes.success && !loginRes.csrf.isNullOrBlank() && !loginRes.authorizedID.isNullOrBlank()) {
                com.amazecc.app.shared.vtop.VtopSession.markActive()
                SessionManager.saveSession(
                    cookies = SessionManager.LOCAL_SESSION_MARKER,
                    csrf = loginRes.csrf,
                    authorizedID = loginRes.authorizedID,
                    clubToken = loginRes.clubToken
                )
                SettingsManager.setString(SettingsManager.SESSION_COOKIES, SessionManager.LOCAL_SESSION_MARKER)
                SettingsManager.setString(SettingsManager.SESSION_CSRF, loginRes.csrf)
                SettingsManager.setString(SettingsManager.SESSION_AUTHORIZED_ID, loginRes.authorizedID)
                loginRes.clubToken?.let { SettingsManager.setString(SettingsManager.SESSION_CLUB_TOKEN, it) }
                com.amazecc.app.shared.state.UserStore.merge(
                    com.amazecc.app.shared.state.IdentityExtractor.fromSession(loginRes.authorizedID),
                    com.amazecc.app.shared.state.IdentitySource.SESSION
                )
                return true
            }
            return false
        }

        // 3. Remote mode: perform full login refresh via Hono backend
        return refreshSession()
    }

    /** Re-logs-in to VTOP with the stored credentials and refreshes the session. */
    private suspend fun refreshSession(): Boolean {
        val credentials = SettingsManager.getCredentials() ?: return false
        if (credentials.first.isBlank() || credentials.second.isBlank()) return false
        val response = login(credentials.first, credentials.second)
        if (response.success && response.cookies != null && response.csrf != null && response.authorizedID != null) {
            SessionManager.saveSession(response.cookies, response.csrf, response.authorizedID, response.clubToken)
            SettingsManager.setString(SettingsManager.SESSION_COOKIES, response.cookies)
            SettingsManager.setString(SettingsManager.SESSION_CSRF, response.csrf)
            SettingsManager.setString(SettingsManager.SESSION_AUTHORIZED_ID, response.authorizedID)
            response.clubToken?.let { SettingsManager.setString(SettingsManager.SESSION_CLUB_TOKEN, it) }
            com.amazecc.app.shared.state.UserStore.merge(
                com.amazecc.app.shared.state.IdentityExtractor.fromSession(response.authorizedID),
                com.amazecc.app.shared.state.IdentitySource.SESSION
            )
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



