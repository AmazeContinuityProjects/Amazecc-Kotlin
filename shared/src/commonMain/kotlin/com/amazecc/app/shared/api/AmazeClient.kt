package com.amazecc.app.shared.api

import com.amazecc.app.shared.state.AmazeNetworkGatePlugin

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
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.Serializable
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.builtins.ListSerializer
import com.amazecc.app.shared.utils.AnalyzeCalendar
import com.amazecc.app.shared.utils.FacultyUtils
import com.amazecc.app.shared.utils.UpdateConfig
import com.amazecc.app.shared.utils.DemoData
import com.amazecc.app.shared.domain.CourseAttendance
import com.amazecc.app.shared.domain.ClassStats
import com.amazecc.app.shared.domain.Projections
import com.amazecc.app.shared.state.AppDataStore
import com.amazecc.app.shared.state.AppState
import com.amazecc.app.shared.vtop.VtopGradeHistoryResult
import com.amazecc.app.shared.config.SlotMap
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/** Legacy Next.js API host. Hono mounts no Koha router (D-094), so Koha calls probe this host
 * directly; a 4xx from it is an authoritative answer and is never re-asked elsewhere. */
private const val LEGACY_API_BASE_URL = "https://api.amazecc.com"

/** One host's outcome for a Koha probe. `httpStatus == 0` means a transport/timeout exception.
 * Kept internal so headless JVM tests can drive failover ordering without touching the network. */
internal data class KohaProbe<T>(val httpStatus: Int, val value: T?, val error: String?)

/** 5xx statuses the web registry fails over on (`FAIL_STATUSES` in `src/lib/apis/registry.ts`). */
internal val KOHA_FAILOVER_STATUSES: Set<Int> = setOf(500, 502, 503, 504)

/**
 * D-014: only a transport failure (status 0) or a 5xx justifies trying another host.
 *
 * A 401/4xx is an answer — re-asking it elsewhere burns VTOP login attempts — and a 2xx whose
 * body reports `success:false` is likewise an answer. [reportedSuccess] is therefore deliberately
 * *not* a failover trigger; it is kept in the signature so callers/tests can state the payload's
 * usability at the decision point.
 */
@Suppress("UNUSED_PARAMETER")
internal fun kohaShouldFailOver(httpStatus: Int, reportedSuccess: Boolean): Boolean =
    httpStatus == 0 || httpStatus in KOHA_FAILOVER_STATUSES

/** Prefer the server's `error`, then its `message`, then a stable local fallback. */
internal fun kohaErrorMessage(error: String?, message: String?): String =
    error?.takeIf { it.isNotBlank() }
        ?: message?.takeIf { it.isNotBlank() }
        ?: "Koha request failed"

/**
 * Resolve the semester a calendar fetch should use. A blank-but-non-null requested id must
 * NOT win the chain — that was why LOCAL calendars looked permanently empty. Pure so the
 * precedence can be pinned by a headless test.
 */
internal fun effectiveSemester(
    requested: String?,
    session: String?,
    selected: String?,
    firstAcademic: String?
): String? =
    requested?.takeIf { it.isNotBlank() }
        ?: session?.takeIf { it.isNotBlank() }
        ?: selected?.takeIf { it.isNotBlank() }
        ?: firstAcademic?.takeIf { it.isNotBlank() }

/**
 * Try each host in order and return the first 2xx with a usable decoded value.
 *
 * D-014: only 5xx / transport failures move to the next host. A 4xx (including 401) and an
 * unusable 2xx are authoritative answers — we stop and hand their error to [fallback] rather
 * than burning another VTOP request elsewhere. When every tried host fails, [fallback] receives
 * the last real error message so the UI can render why the request failed instead of a blank
 * screen.
 */
internal suspend fun <T> kohaWithFailover(
    hosts: List<String>,
    fetch: suspend (String) -> KohaProbe<T>,
    isUsable: (T) -> Boolean,
    fallback: (String?) -> T
): T {
    var lastError: String? = null
    for (host in hosts) {
        val probe = try { fetch(host) } catch (_: Exception) { null }
        if (probe == null) {
            // No response at all: a transport failure, not an answer.
            lastError = kohaErrorMessage(null, null)
            continue
        }
        val value = probe.value
        if (probe.httpStatus in 200..299 && value != null && isUsable(value)) return value
        lastError = kohaErrorMessage(probe.error, null)
        if (!kohaShouldFailOver(probe.httpStatus, value?.let(isUsable) ?: false)) break
    }
    return fallback(lastError)
}

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
    // D-107: `marksRes` is an error string when the upstream marks scrape fails.
    @Serializable(with = MarksResOrStringSerializer::class)
    val marksRes: MarksRes? = null,
    val semester: String? = null,
    val semesterId: String? = null,
    val attendance: List<AttendanceItem> = emptyList(),
    val error: String? = null,
    val message: String? = null
)

/**
 * D-154: the combined `POST /api/ffcs` `section=all` wire contract. Only the feedback
 * slice is consumed here; every other slice stays an opaque [JsonObject] so this decoder
 * does not depend on (or duplicate) the other FFCS parsers. `section=feedback` answers
 * with the status object at the top level instead — both shapes are handled by
 * [extractFeedbackStatus].
 */
@Serializable
private data class FfcsAllResponse(
    val success: Boolean = true,
    val registrationSchedule: JsonObject? = null,
    val feedbackStatus: JsonObject? = null,
    val feedback: JsonObject? = null,
    val wishlist: JsonObject? = null,
    val schedule: JsonObject? = null,
    val qcmView: JsonObject? = null
)

/** Lenient JSON for the feedback slice only, so unrelated FFCS fields are ignored. */
private val ffcsStatusJson = Json { ignoreUnknownKeys = true }

/**
 * D-154: the single rule that turns any `POST /api/ffcs` body into a [FeedbackStatusRes].
 * Mirrors the web `extractDetailedFeedbackStatus`: a `feedbackStatus` member wins when
 * present (including an explicit null), otherwise the whole body *is* the status. A
 * missing, corrupt or unexpected shape yields null — no status is ever fabricated.
 * kotlinx.serialization preserves non-ASCII semester labels / feedback rows verbatim.
 */
internal fun extractFeedbackStatus(body: JsonElement): FeedbackStatusRes? {
    if (body !is JsonObject) return null
    // Read the member directly rather than gating on a full FfcsAllResponse decode: unrelated
    // FFCS slices may be arrays/scalars on the wire, and must not hide a valid status slice.
    // An explicit `feedbackStatus: null` is JsonNull here — present, so it wins and yields null.
    val candidate = body["feedbackStatus"] ?: body
    if (candidate !is JsonObject) return null
    return runCatching {
        ffcsStatusJson.decodeFromJsonElement(FeedbackStatusRes.serializer(), candidate)
    }.getOrNull()
}

/** Slice of `POST /api/ffcs` used by the feedback-status wire path. */
@Serializable(with = FfcsFeedbackResponseSerializer::class)
private data class FfcsFeedbackResponse(
    val success: Boolean = true,
    val feedbackStatus: FeedbackStatusRes? = null,
    val error: String? = null
)

/**
 * D-154: decode [FfcsFeedbackResponse] from the whole FFCS body so [extractFeedbackStatus]
 * is the only extraction rule. Callers keep their typed `success` / `feedbackStatus`
 * accessors; the fetch path in [AmazeClient.getFeedbackStatus] is otherwise untouched.
 */
private object FfcsFeedbackResponseSerializer : KSerializer<FfcsFeedbackResponse> {
    override val descriptor: SerialDescriptor = JsonObject.serializer().descriptor

    override fun deserialize(decoder: Decoder): FfcsFeedbackResponse {
        val body = (decoder as? JsonDecoder)?.runCatching { decodeJsonElement() }?.getOrNull()
        if (body !is JsonObject) return FfcsFeedbackResponse(success = false)
        val envelope = runCatching {
            ffcsStatusJson.decodeFromJsonElement(FfcsAllResponse.serializer(), body)
        }.getOrNull()
        val success = envelope?.success
            ?: (body["success"] as? JsonPrimitive)?.booleanOrNull
            ?: true
        val error = (body["error"] as? JsonPrimitive)?.content
            ?: (body["message"] as? JsonPrimitive)?.content
        return FfcsFeedbackResponse(
            success = success,
            feedbackStatus = extractFeedbackStatus(body),
            error = error
        )
    }

    override fun serialize(encoder: Encoder, value: FfcsFeedbackResponse) {
        if (encoder !is JsonEncoder) return
        val status = value.feedbackStatus?.let {
            runCatching { ffcsStatusJson.encodeToJsonElement(FeedbackStatusRes.serializer(), it) }.getOrNull()
        }
        encoder.encodeJsonElement(buildJsonObject {
            put("success", value.success)
            value.error?.let { put("error", it) }
            if (status != null) put("feedbackStatus", status)
        })
    }
}

/**
 * Pure mapping from one LOCAL `StudentGradeHistory` scrape to the server's `AllGradesRes`
 * shape. Kept as a top-level function so it can be unit-tested without a WebView/session
 * (it is the seam `getAllGrades()` uses for both the LOCAL branch and the REMOTE fallback).
 *
 * Every value is copied from the scrape: the published CGPA and baskets go to `overall`
 * verbatim, and each course becomes a `GradeItem` with its real code/title/credits/grade.
 * Nothing is reconstructed — a missing GPA or total stays null/empty.
 */
fun buildAllGradesFromGradeHistory(
    history: VtopGradeHistoryResult,
    semesterId: String
): AllGradesRes {
    val grades = history.courses.map { c ->
        GradeItem(
            courseCode = c.code,
            courseTitle = c.title,
            courseType = c.type,
            credits = c.credits,
            grade = c.grade,
            grandTotal = "",
            examMonth = c.examMonth,
            resultDeclared = c.resultDeclared,
            distributionType = c.distributionType
        )
    }
    val semester = SemesterGradeResult(
        // Per-semester GPA and grand totals come from the semester grade view, not this
        // page; leave them unset rather than invent a number. The published CGPA lives in
        // `overall.cgpa` exactly as the server returns it.
        creditsEarned = history.cgpa.creditsEarned,
        grades = grades
    )
    return AllGradesRes(
        success = true,
        semesters = mapOf(semesterId to semester),
        overall = OverallGrades(
            cgpa = history.cgpa,
            curriculum = history.baskets
        )
    )
}

/**
 * Folds the per-semester `doStudentGradeView` / `getGradeViewDetails` sweep into the base
 * `AllGradesRes` built from `StudentGradeHistory`. Pure so the join can be unit-tested without a
 * WebView.
 *
 * The detail sweep is authoritative for per-semester GPA, grand totals, `courseId`, assessment
 * breakdown and grade ranges. The history page remains authoritative for the published CGPA,
 * the flat curriculum baskets and the exam-month/result/distribution fields it alone carries —
 * all of which stay untouched in `overall` and are carried over per course by code. Nothing is
 * invented: a field absent on both sides stays null/empty.
 */
fun mergeGradeDetails(
    base: AllGradesRes,
    details: Map<String, SemesterGradeResult>
): AllGradesRes {
    if (details.isEmpty()) return base

    val merged = base.allSemesters.toMutableMap()
    details.forEach { (semesterId, detailed) ->
        val prior = merged[semesterId]
        if (prior == null) {
            merged[semesterId] = detailed
            return@forEach
        }
        val priorByCode = prior.grades.associateBy { it.courseCode }
        merged[semesterId] = SemesterGradeResult(
            gpa = detailed.gpa ?: prior.gpa,
            creditsEarned = prior.creditsEarned ?: detailed.creditsEarned,
            examMonth = prior.examMonth ?: detailed.examMonth,
            resultDeclared = prior.resultDeclared ?: detailed.resultDeclared,
            distributionType = prior.distributionType ?: detailed.distributionType,
            courseId = prior.courseId ?: detailed.courseId,
            effectiveCourseType = prior.effectiveCourseType ?: detailed.effectiveCourseType,
            grades = detailed.grades.map { grade ->
                val historical = priorByCode[grade.courseCode]
                if (historical == null) grade else grade.copy(
                    credits = grade.credits ?: historical.credits,
                    courseTitle = grade.courseTitle.ifBlank { historical.courseTitle },
                    courseType = grade.courseType.ifBlank { historical.courseType },
                    examMonth = grade.examMonth ?: historical.examMonth,
                    resultDeclared = grade.resultDeclared ?: historical.resultDeclared,
                    distributionType = grade.distributionType ?: historical.distributionType
                )
            }
        )
    }

    // Preserve whichever map the caller used so `allSemesters` keeps the same precedence.
    return when {
        base.semesters != null -> base.copy(semesters = merged)
        base.grades != null -> base.copy(grades = merged)
        else -> base.copy(semesters = merged)
    }
}

/** Explicit outcome of a guarded (Amaze ID) request — no silent `null` for a 401. */
sealed class GuardedResult<out T> {
    data class Success<T>(val value: T) : GuardedResult<T>()
    data object AuthRequired : GuardedResult<Nothing>()
    data class Failure(val message: String) : GuardedResult<Nothing>()
}

/** Result of a hub fetch that can fail because the Amaze ID sign-in is missing (D-104). */
sealed class CabHubsResult {
    data class Hubs(val hubs: List<CabShareHub>) : CabHubsResult()
    data class Error(val message: String) : CabHubsResult()
}

/** Stable marker callers/UI can use to surface "Sign in with Amaze ID". */
const val AMAZE_AUTH_REQUIRED = "AMAZE_AUTH_REQUIRED"

/**
 * Pure HTTP contract descriptor shared by the live request helpers and the JVM contract tests.
 * It records the path, method, body/query fields and whether an Amaze ID token is required, so
 * endpoint drift is caught without a network or a mocking engine.
 */
internal data class HttpContract(
    val path: String,
    val method: String,
    val bodyFields: Map<String, String> = emptyMap(),
    val query: Map<String, String> = emptyMap(),
    val authRequired: Boolean = false
)

internal fun attendanceContract(semesterId: String?) = HttpContract(
    path = "/api/attendance",
    method = "POST",
    bodyFields = buildMap { semesterId?.let { put("semesterId", it) } }
)

internal fun ffcsContract(section: String, semesterId: String? = null) = HttpContract(
    path = "/api/ffcs",
    method = "POST",
    bodyFields = buildMap {
        put("section", section)
        semesterId?.let { put("semesterId", it) }
    }
)

internal fun bulkContract(names: List<String>) = HttpContract(
    path = "/api/bulk",
    method = "POST",
    bodyFields = mapOf("endpoints" to names.joinToString(",")),
    authRequired = false // VTOP session credentials, not Amaze ID
)

internal fun cabshareHubsContract() = HttpContract(
    path = "/api/cabshare/hubs",
    method = "GET",
    authRequired = true
)

internal fun cabshareGuardedContract(path: String, method: String) = HttpContract(
    path = path,
    method = method,
    authRequired = true
)

/** `/api/services/curves` body contract: `classId`/`classIds`, never `courseCode` (D-108). */
internal fun curvesParams(classId: String, semesterId: String? = null): Map<String, String> = buildMap {
    put("classId", classId)
    semesterId?.let { put("semesterId", it) }
}

/**
 * Pure header contract for Amaze ID auth (D-011). A DPoP-bound token (non-null `confirmation`)
 * uses the `DPoP` Authorization scheme; otherwise `Bearer`. Matches the API's
 * `accessToken.confirmation !== null` check in `lib/amazeId.ts`.
 */
internal fun amazeAuthHeaders(
    token: String?,
    dpopHeader: String?,
    confirmation: String?,
    dpopEnabled: Boolean = true
): Map<String, String> {
    val usableToken = token?.takeIf { it.isNotBlank() } ?: return emptyMap()
    val scheme = if (dpopEnabled && !confirmation.isNullOrBlank()) "DPoP" else "Bearer"
    return buildMap {
        put(HttpHeaders.Authorization, "$scheme $usableToken")
        if (dpopEnabled) {
            dpopHeader?.takeIf { it.isNotBlank() }?.let { put("DPoP", it) }
        }
    }
}

/** Attach the stored Amaze ID token(s) to a guarded request (D-011). */
internal fun HttpRequestBuilder.attachAmazeIdAuth() {
    amazeAuthHeaders(
        token = SessionManager.amazeAccessToken.value,
        dpopHeader = SessionManager.amazeDpopHeader.value,
        confirmation = SessionManager.amazeTokenConfirmation.value
    ).forEach { (name, value) -> header(name, value) }
}

/** Raise the explicit auth-required signal on a guarded 401 (D-011). */
internal fun markAmazeAuthRequired(status: HttpStatusCode) {
    if (status.value == 401) SessionManager.markAmazeAuthRequired()
}

/**
 * Pure mapping from the timetable columns merged into `attRes.attendance` by
 * `POST /api/attendance` into the app's `TimetableRes` (D-090). The API no longer exposes a
 * `/api/timetable` route; those columns ride along with attendance instead.
 */
internal fun timetableFromAttendance(semesterId: String?, attendance: List<AttendanceItem>): TimetableRes =
    TimetableRes(
        success = true,
        semesterId = semesterId,
        courseInfo = attendance.mapIndexed { index, a ->
            com.amazecc.app.shared.state.TimetableCourseInfo(
                slNo = a.slNo ?: (index + 1).toString(),
                course = a.courseTitle,
                courseCode = a.courseCode,
                LTPJC = a.credits.orEmpty(),
                category = a.category.orEmpty(),
                classId = a.classId.orEmpty(),
                slotVenue = a.slotVenue.orEmpty(),
                facultyDetails = a.faculty
            )
        },
        message = "Derived from /api/attendance"
    )

/** `MarksRes` from the `marksRes` slice of `/api/attendance` (D-090). */
internal fun marksFromAttendance(marks: MarksRes?): MarksRes =
    marks?.takeIf { it.marks.isNotEmpty() }
        ?: MarksRes(success = false, message = "Marks unavailable from attendance response")

/**
 * The bare `Assignment[]` returned by `POST /api/lms-data` is the Moodle scraper shape, not
 * `LMSRes`. Map it into the app's LMS contract so the sync path can use it (D-091).
 */
internal fun lmsFromMoodle(res: MoodleRes): LMSRes = LMSRes(
    success = res.success,
    assignments = res.data.map { a ->
        LMSAssignment(
            name = a.name,
            courseCode = a.courseCode,
            courseTitle = a.courseTitle,
            assignmentTitle = a.taskTitle,
            due = a.due,
            done = a.done,
            day = a.effectiveDayOfMonth.takeIf { it != 0 },
            month = a.effectiveMonthNumber.takeIf { it != 0 },
            year = a.year.takeIf { it != 0 },
            url = a.url,
            teachers = a.teachers
        )
    },
    error = res.error,
    message = res.message
)

/**
 * `/api/grades` returns the full `{overall, semesters}` shape, not the legacy
 * `{effectiveGrades}` one. Map the requested semester's rows into the existing
 * `SemesterGradesRes` contract (D-099).
 */
internal fun semesterGradesFromAll(semesterId: String?, all: AllGradesRes): SemesterGradesRes {
    val semesters = all.allSemesters
    val chosen = (semesterId?.let { semesters[it] } ?: semesters.values.firstOrNull())
    val rows = chosen?.grades.orEmpty().mapNotNull { g ->
        g?.let { grade ->
            EffectiveGradeCourse(
                basketTitle = grade.courseTitle.ifBlank { grade.courseCode },
                courseType = grade.courseType,
                creditsEarned = grade.credits,
                grade = grade.grade,
                distributionType = grade.distributionType,
                courseCode = grade.courseCode,
                courseTitle = grade.courseTitle,
                examMonth = grade.examMonth,
                resultDeclared = grade.resultDeclared
            )
        }
    }
    return SemesterGradesRes(
        success = all.success && rows.isNotEmpty(),
        error = all.error,
        effectiveGrades = rows
    )
}

private fun rowsToJson(rows: List<KeyValueRow>): JsonObject = buildJsonObject {
    rows.forEach { put(it.label, it.value) }
}

private fun tablesToJson(tables: List<VtopTable>): JsonArray = buildJsonArray {
    tables.forEach { table ->
        add(buildJsonObject {
            put("caption", table.caption.orEmpty())
            put("headers", buildJsonArray { table.headers.forEach { add(JsonPrimitive(it)) } })
            put("rows", buildJsonArray {
                table.rows.forEach { row ->
                    add(buildJsonArray { row.forEach { cell -> add(JsonPrimitive(cell)) } })
                }
            })
        })
    }
}

/**
 * D-101: the per-endpoint identity routes (`student`, `profile-images`, `credentials`, ...)
 * are not mounted — all of their data is folded into `POST /api/whoami`, already decoded by
 * [getMe]. These pure projectors keep the public function signatures while sourcing the same
 * identity from the consolidated payload.
 */
internal fun studentProfileFromIdentity(id: StudentIdentity): StudentProfile = StudentProfile(
    applicationNumberWire = id.regNo.orEmpty(),
    name = id.name.orEmpty(),
    email = id.email.orEmpty(),
    mobile = id.mobile.orEmpty(),
    program = id.program.orEmpty(),
    campus = id.campus.orEmpty(),
    batch = id.batch.orEmpty(),
    section = id.section,
    advisorName = id.advisorName,
    bloodGroup = id.bloodGroup,
    photoBase64 = id.photoBase64,
    nativeLanguage = id.nativeLanguage,
    nationality = id.nationality,
    community = id.community,
    religion = id.religion,
    caste = id.caste,
    physicallyChallenged = id.physicallyChallenged,
    aadharNumber = id.aadharNumber,
    dob = id.dob,
    gender = id.gender,
    isHosteller = id.isHosteller,
    nativeState = id.nativeState,
    currentAddress = rowsToJson(id.currentAddress),
    permanentAddress = rowsToJson(id.permanentAddress),
    father = rowsToJson(id.father),
    mother = rowsToJson(id.mother),
    guardian = id.guardian,
    proctor = id.proctor?.let {
        ProfileProctor(
            facultyId = it.facultyId, name = it.name, designation = it.designation,
            school = it.school, cabin = it.cabin, department = it.department,
            email = it.email, intercom = it.intercom, mobile = it.phone
        )
    }
)

internal fun profileImagesFromIdentity(id: StudentIdentity): ProfileImagesRes = ProfileImagesRes(
    success = true,
    student = ProfileImagesStudent(photoBase64 = id.photoBase64),
    proctor = id.proctor?.let {
        ProfileImagesProctor(
            title = "Proctor", photoBase64 = it.photoBase64,
            details = buildMap {
                it.name?.let { v -> put("name", v) }
                it.designation?.let { v -> put("designation", v) }
                it.email?.let { v -> put("email", v) }
            }
        )
    },
    hodDean = ProfileImagesHodDean(
        title = "HOD / Dean",
        people = id.hodDean.map { official ->
            ProfileImagesHodDeanPerson(
                role = official.role.orEmpty(),
                details = buildMap { official.name?.let { v -> put("name", v) } },
                photoBase64 = official.photoBase64
            )
        }
    )
)

internal fun credentialsFromIdentity(id: StudentIdentity): CredentialsRes = CredentialsRes(
    success = true,
    credentials = id.credentials.map {
        ProfileImagesCredential(
            account = it.account, username = it.username, url = it.url,
            defaultCredentials = it.password, venueDate = it.venueDate,
            seatLocation = it.seatLocation
        )
    },
    ranks = id.ranks.map { ProfileImagesRank(name = it.name, rank = it.rank) }
)

internal fun eptScheduleFromIdentity(id: StudentIdentity): EptScheduleRes = EptScheduleRes(
    success = true,
    tables = tablesToJson(id.eptTables)
)

internal fun registrationScheduleFromIdentity(id: StudentIdentity): RegistrationScheduleRes = RegistrationScheduleRes(
    success = true,
    keyValuePairs = rowsToJson(id.registrationFields),
    tables = tablesToJson(id.registrationTables)
)

internal fun universityDayFromIdentity(id: StudentIdentity): UniversityDayRes = UniversityDayRes(
    success = true,
    title = id.universityDayTitle.orEmpty(),
    keyValuePairs = rowsToJson(id.universityDayFields),
    tables = tablesToJson(id.universityDayTables)
)

internal fun bankInfoFromIdentity(id: StudentIdentity): BankInfoRes = BankInfoRes(
    success = true,
    bankDetails = buildJsonObject {
        id.bank?.name?.let { put("name", it) }
        id.bank?.branch?.let { put("branch", it) }
        id.bank?.address?.let { put("address", it) }
    },
    fields = rowsToJson(id.bank?.fields.orEmpty())
)

internal fun dayboarderFromIdentity(id: StudentIdentity): DayboarderRes = DayboarderRes(
    success = true,
    fields = rowsToJson(id.dayboarder?.fields.orEmpty())
)

internal fun apaarIdFromIdentity(id: StudentIdentity): ApaarIdRes = ApaarIdRes(
    success = true,
    hasApaar = id.apaar?.hasApaar ?: false,
    formFields = rowsToJson(id.apaar?.fields.orEmpty()),
    tables = tablesToJson(id.apaar?.tables.orEmpty())
)

/** Convert a `{ headers, rows }` table array into the app's ArrearResponse contract. */
private fun arrearFromTableArray(tables: JsonArray): ArrearResponse {
    val wrapped = buildJsonObject {
        put("success", true)
        put("tables", tables)
    }
    return receiptResponseFromJson(wrapped)
        ?: ArrearResponse(success = true, message = "Bulk result received")
}

/**
 * Decode one `POST /api/bulk` / `/api/ffcs section=wishlist` proxy result.
 *
 * The VTOP semester proxies (course-completion, wishlist, ...) return
 * `{ success, semesters: { <id>: { tables, keyValuePairs, messages } } }`, **not** a flat
 * `{ tables }`. Reading only `tables` silently dropped every row (D-100). Flatten each
 * semester's tables/keyValuePairs/messages into one ArrearResponse so no data is lost.
 */
internal fun arrearFromBulkResult(element: JsonElement): ArrearResponse {
    val obj = element as? JsonObject
        ?: return ArrearResponse(success = false, message = "Malformed bulk result")
    val semesters = obj["semesters"] as? JsonObject
    if (semesters != null && semesters.isNotEmpty()) {
        val tables = JsonArray(semesters.values.mapNotNull { semEl ->
            (semEl as? JsonObject)?.get("tables") as? JsonArray
        }.flatMap { it.toList() })
        val keyValuePairs = mutableListOf<KeyValuePair>()
        val messages = mutableListOf<ApiMessage>()
        semesters.values.forEach { semEl ->
            val sem = semEl as? JsonObject ?: return@forEach
            (sem["keyValuePairs"] as? JsonObject)?.forEach { (key, value) ->
                val text = runCatching { (value as? JsonPrimitive)?.content }.getOrNull()
                if (!text.isNullOrBlank()) keyValuePairs += KeyValuePair(key.trim(), text.trim())
            }
            (sem["messages"] as? JsonArray)?.forEach { msg ->
                val text = runCatching { (msg as? JsonPrimitive)?.content }.getOrNull()
                if (!text.isNullOrBlank()) messages += ApiMessage(text.trim())
            }
        }
        val base = arrearFromTableArray(tables)
        return base.copy(
            success = true,
            keyValuePairs = base.keyValuePairs + keyValuePairs,
            messages = base.messages + messages
        )
    }
    return arrearFromTableArray(obj["tables"] as? JsonArray ?: JsonArray(emptyList()))
}

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

    /**
     * Switch the API mount (web parity — the Primary/Backup picker).
     * Built-in targets take their URL from [com.amazecc.app.shared.domain.ApiTarget.url]
     * so the picker and the client cannot drift; CUSTOM keeps the typed URL.
     */
    fun setApiTarget(target: com.amazecc.app.shared.domain.ApiTarget) {
        target.url?.let { baseUrl = it }
        // Persist CUSTOM too: mountedPost reads getApiTargetName() to decide whether to inject the
        // custom URL. Skipping it made the CUSTOM picker option a no-op for mount-routed ops.
        SettingsManager.setApiTargetName(target.name)
    }

    fun setUseMockData(enable: Boolean) {
        useMockData = enable
    }

    private val jsonConfig = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    private val httpClient = HttpClient {
        // Every request passes through the sync-engine gate (visibility + cancellation + logs).
        install(AmazeNetworkGatePlugin)
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

        // D-109: every real VTOP credential login funnels through this single public choke point,
        // so this is the one place that can guarantee a successful sign-in never leaves
        // SettingsManager.getCredentials() null. Callers no longer have to remember to persist.
        // `loginResponseOverride` is a test-only seam that still exercises the persistence step.
        val response = loginResponseOverride?.invoke(username, password) ?: when {
            vtopSource == com.amazecc.app.shared.vtop.VtopSource.LOCAL ->
                loginLocal(username, password, captchaHandler)
            else -> try {
                val httpResponse: HttpResponse = httpClient.post("$baseUrl/api/login") {
                    contentType(ContentType.Application.Json)
                    setBody(LoginRequest(username, password))
                }
                if (httpResponse.status == HttpStatusCode.OK) {
                    jsonConfig.decodeFromString<LoginResponse>(httpResponse.bodyAsText())
                } else {
                    LoginResponse(success = false, message = "Server returned status ${httpResponse.status}", error = "HTTP ${httpResponse.status}")
                }
            } catch (e: Exception) {
                LoginResponse(success = false, message = "Network error: ${e.message}", error = e.toString())
            }
        }

        if (response.success) persistVtopCredentialsOnSuccess(username, password)
        return response
    }

    /**
     * D-109 — a successful VTOP credential login is not complete until the credentials are
     * persisted. Both the sweep gate ([ensureSession] via `refreshSession`/`loginLocal`) and
     * per-module re-auth read `SettingsManager.getCredentials()`; a sign-in that returned
     * success but skipped persistence left that lookup null and reported "Your VTOP session has
     * expired" on every module. Mock/demo and offline-cache logins are deliberately excluded —
     * they are not real credential sign-ins. Passwords go through [SettingsManager.saveCredentials],
     * which keeps the existing `Encryption.encryptOrPlain` protection (never plain text).
     */
    private fun persistVtopCredentialsOnSuccess(username: String, password: String) {
        if (useMockData || vtopSource == com.amazecc.app.shared.vtop.VtopSource.OFFLINE_CACHE) return
        if (username.isBlank() || password.isBlank()) return
        SettingsManager.saveCredentials(username.trim(), password)
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
            is com.amazecc.app.shared.vtop.LoginResult.Success -> {
                // D-109: mirror the public login() guarantee for the direct ensureSession path.
                persistVtopCredentialsOnSuccess(username, password)
                LoginResponse(
                    success = true,
                    message = "Login successful!",
                    cookies = SessionManager.LOCAL_SESSION_MARKER,
                    csrf = result.csrf,
                    authorizedID = result.authorizedID,
                    clubToken = null
                )
            }
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
    private suspend inline fun <reified T> postAuthorized(endpoint: String, extraParams: Map<String, String> = emptyMap()): T? =
        postAuthorizedRaw<T>(endpoint) { builder -> extraParams.forEach { (k, v) -> builder.put(k, v) } }

    /**
     * Internal variant of [postAuthorized] whose extra fields are real JSON values — the social
     * protocol sends `discoverable` as a JSON boolean, and Hono reads `typeof body.discoverable
     * !== "boolean"`. Same transport and error semantics as [postAuthorized].
     */
    internal suspend inline fun <reified T> postAuthorizedValues(endpoint: String, extraParams: Map<String, JsonElement>): T? =
        postAuthorizedRaw<T>(endpoint) { builder -> extraParams.forEach { (k, v) -> builder.put(k, v) } }

    /**
     * [pathPrefix] selects the API mount family while every guard/credential/decode rule stays
     * identical. The default preserves the legacy `/api/` mount used by all existing call sites;
     * the canonical `/api/remote/` mount is reached through [postAuthorizedRemote].
     */
    private suspend inline fun <reified T> postAuthorizedRaw(
        endpoint: String,
        pathPrefix: String = "/api/",
        extra: (JsonObjectBuilder) -> Unit = {},
    ): T? {
        // In LOCAL mode the real JSESSIONID lives in the WebView cookie jar and was never
        // meant to leave the device. There is nothing valid to send.
        if (vtopSource != com.amazecc.app.shared.vtop.VtopSource.REMOTE) return null
        if (!SessionManager.hasRemoteCookies) return null
        val cookies = SessionManager.cookies.value ?: return null
        val authorizedID = SessionManager.authorizedID.value ?: return null
        val csrf = SessionManager.csrf.value ?: return null

        return try {
            val response: HttpResponse = httpClient.post("$baseUrl$pathPrefix$endpoint") {
                contentType(ContentType.Application.Json)
                attachAmazeIdAuth()
                setBody(buildJsonObject {
                    put("cookies", cookies)
                    put("authorizedID", authorizedID)
                    put("csrf", csrf)
                    extra(this)
                })
            }
            markAmazeAuthRequired(response.status)
            if (response.status == HttpStatusCode.OK) {
                runCatching { jsonConfig.decodeFromString<T>(response.bodyAsText()) }.getOrNull()
            } else {
                null
            }
        } catch (e: Exception) {
            // DNS resolution failure (e.g. UnknownHostException for hono.amazecc.com), timeout, or offline
            null
        }
    }

    /** D-161: authorized POST against the canonical Hono `/api/remote/` mount. */
    private suspend inline fun <reified T> postAuthorizedRemote(endpoint: String): T? =
        postAuthorizedRaw<T>(endpoint, extra = {}, pathPrefix = "/api/remote/")

    /**
     * Raw GET for the public social rosters (`GET /api/social/ffcs-match`): unlike
     * [postAuthorized] the route is reachable without a VTOP session, so null here means
     * "server unreachable or non-2xx", never "not authorized". Decodes the JSON body or
     * null, with the same transport/error semantics as the other helpers.
     */
    internal suspend fun socialGetRaw(path: String, query: Map<String, String> = emptyMap()): JsonElement? {
        return try {
            val response: HttpResponse = httpClient.get("$baseUrl/api/$path") {
                attachAmazeIdAuth()
                query.forEach { (k, v) -> parameter(k, v) }
            }
            markAmazeAuthRequired(response.status)
            if (response.status != HttpStatusCode.OK) return null
            response.bodyAsText().takeIf { it.isNotBlank() }?.let { jsonConfig.decodeFromString<JsonElement>(it) }
        } catch (_: Exception) {
            null
        }
    }

    /** One social HTTP outcome. A non-2xx keeps its status *and* body — the typed client maps
     * `{ success: false, error, detail }` itself instead of receiving a collapsed `null`. */
    internal data class SocialHttpResponse(val status: Int, val body: String?)

    /** Testable description of a social request: no VTOP cookies, credentials only in [body]. */
    internal data class SocialRequestDescriptor(
        val path: String,
        val url: String,
        val method: String,
        val query: Map<String, String>,
        val body: JsonObject,
        val headers: Map<String, String>
    )

    /** Error codes where a 401 really means "Amaze ID", not a VTOP-session 401 from the route. */
    private val amazeIdSocialErrorCodes = setOf("amazeid_signin_required", "amazeid_session_expired")

    /**
     * Test seam for the D-193 social transport. Null in production; when set, the live HTTP call
     * is replaced by the captured [SocialRequestDescriptor] so request bodies, auth headers and
     * error mapping can be asserted offline.
     */
    internal var socialRequestOverride: (suspend (SocialRequestDescriptor) -> SocialHttpResponse)? = null

    /**
     * D-193: the transport for every `/api/social/...` call.
     *
     * Unlike [postAuthorizedRaw] the API boundary here is the stored Amaze ID token (Bearer, or
     * DPoP when the token has a `confirmation`), attached by [amazeAuthHeaders]. It therefore
     * works in `LOCAL`, `REMOTE` and `OFFLINE_CACHE` VTOP modes and never demands REMOTE VTOP
     * cookies. VTOP credentials are call-site JSON body fields (publish / server retry only)
     * and are never placed in the URL.
     */
    internal suspend fun socialRequest(
        path: String,
        method: String = "GET",
        query: Map<String, String> = emptyMap(),
        body: JsonObject = JsonObject(emptyMap())
    ): SocialHttpResponse {
        val authHeaders = amazeAuthHeaders(
            token = SessionManager.amazeAccessToken.value,
            dpopHeader = SessionManager.amazeDpopHeader.value,
            confirmation = SessionManager.amazeTokenConfirmation.value
        )
        val descriptor = SocialRequestDescriptor(
            path = path,
            url = "$baseUrl/api/$path",
            method = method,
            query = query,
            body = body,
            headers = authHeaders
        )
        socialRequestOverride?.let { return it(descriptor) }
        return try {
            val response: HttpResponse = httpClient.request(descriptor.url) {
                this.method = if (method.equals("POST", ignoreCase = true)) HttpMethod.Post else HttpMethod.Get
                authHeaders.forEach { (name, value) -> header(name, value) }
                query.forEach { (k, v) -> parameter(k, v) }
                if (this@AmazeClient.socialRequestMethodIsPost(method)) {
                    contentType(ContentType.Application.Json)
                    setBody(body.toString())
                }
            }
            val text = response.bodyAsText()
            // A route-level 401 can also mean "VTOP session required" (ffcs-match resolves the
            // caller from either identity). Only mark the global Amaze ID signal when the body
            // does not name a different, typed cause.
            val code = runCatching { (jsonConfig.parseToJsonElement(text) as? JsonObject)?.get("error") }
                .getOrNull() as? JsonPrimitive
            val codeText = code?.content
            if (response.status.value == 401 &&
                (codeText == null || codeText in amazeIdSocialErrorCodes)
            ) markAmazeAuthRequired(response.status)
            SocialHttpResponse(response.status.value, text)
        } catch (_: Exception) {
            SocialHttpResponse(0, null)
        }
    }

    /** Kept as a tiny helper so the Ktor request lambda never has to capture `method` twice. */
    private fun socialRequestMethodIsPost(method: String): Boolean = method.equals("POST", ignoreCase = true)

    private suspend inline fun <reified T> postAuthorizedBody(endpoint: String, body: String): T? {
        if (vtopSource != com.amazecc.app.shared.vtop.VtopSource.REMOTE) return null
        if (!SessionManager.hasRemoteCookies) return null
        val cookies = SessionManager.cookies.value ?: return null
        val authorizedID = SessionManager.authorizedID.value ?: return null
        val csrf = SessionManager.csrf.value ?: return null
        return try {
            val response: HttpResponse = httpClient.post("$baseUrl/api/$endpoint") {
                contentType(ContentType.Application.Json)
                attachAmazeIdAuth()
                setBody(buildJsonObject {
                    put("cookies", cookies)
                    put("authorizedID", authorizedID)
                    put("csrf", csrf)
                    val parsed = Json.parseToJsonElement(body).jsonObject
                    parsed.forEach { (k, v) -> put(k, v) }
                })
            }
            markAmazeAuthRequired(response.status)
            if (response.status == HttpStatusCode.OK) {
                runCatching { jsonConfig.decodeFromString<T>(response.bodyAsText()) }.getOrNull()
            } else null
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Raw-binary counterpart of [postAuthorized], for proxy routes that stream a VTOP file
     * rather than a JSON envelope (e.g. `POST /api/curriculum/download`). A 200 carrying JSON is
     * the proxy's error envelope, so it is rejected; empty bodies fail closed as null.
     */
    private suspend fun postAuthorizedBytes(endpoint: String): ByteArray? {
        if (vtopSource != com.amazecc.app.shared.vtop.VtopSource.REMOTE) return null
        if (!SessionManager.hasRemoteCookies) return null
        val cookies = SessionManager.cookies.value ?: return null
        val authorizedID = SessionManager.authorizedID.value ?: return null
        val csrf = SessionManager.csrf.value ?: return null
        return try {
            val response: HttpResponse = httpClient.post("$baseUrl/api/$endpoint") {
                contentType(ContentType.Application.Json)
                attachAmazeIdAuth()
                setBody(buildJsonObject {
                    put("cookies", cookies)
                    put("authorizedID", authorizedID)
                    put("csrf", csrf)
                })
            }
            markAmazeAuthRequired(response.status)
            if (response.status != HttpStatusCode.OK) return null
            if (response.headers["Content-Type"].orEmpty().contains("json", ignoreCase = true)) return null
            response.body<ByteArray>().takeIf { it.isNotEmpty() }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Amaze ID-guarded request helper (D-011/D-102). Returns an explicit [GuardedResult] so a
     * missing token or a 401 can never masquerade as "no data" — callers can surface a
     * "Sign in with Amaze ID" action instead of showing an empty screen.
     */
    private suspend inline fun <reified T> amazeIdRequest(
        path: String,
        method: HttpMethod = HttpMethod.Get,
        bodyJson: String? = null,
        query: Map<String, String> = emptyMap()
    ): GuardedResult<T> {
        if (!SessionManager.hasAmazeIdAuth) return GuardedResult.AuthRequired
        return try {
            val response: HttpResponse = httpClient.request("$baseUrl$path") {
                this.method = method
                attachAmazeIdAuth()
                query.forEach { (k, v) -> parameter(k, v) }
                if (bodyJson != null) {
                    contentType(ContentType.Application.Json)
                    setBody(bodyJson)
                }
            }
            markAmazeAuthRequired(response.status)
            when (response.status.value) {
                401 -> GuardedResult.AuthRequired
                in 200..299 -> {
                    val text = response.bodyAsText()
                    if (text.isBlank()) GuardedResult.Failure("Empty response")
                    else runCatching { jsonConfig.decodeFromString<T>(text) }
                        .map { GuardedResult.Success(it) }
                        .getOrElse { GuardedResult.Failure(it.message ?: "Malformed response") }
                }
                else -> GuardedResult.Failure("HTTP ${response.status.value}")
            }
        } catch (e: Exception) {
            GuardedResult.Failure(e.message ?: "Network error")
        }
    }

    /** Expose the explicit auth signal to callers without changing their result types. */
    fun isAmazeIdSignInRequired(): Boolean = SessionManager.amazeAuthRequired.value

    /** Consume the signal after a guarded call completed successfully (D-193). */
    fun clearAmazeIdSignInRequired() = SessionManager.clearAmazeAuthRequired()

    /** GET a Koha route from the primary host, then the legacy host. HTTP status is checked
     * before decoding so a 404 body can never be mistaken for a successful payload. */
    private suspend inline fun <reified T> kohaGet(
        relativeUrl: String,
        noinline isUsable: (T) -> Boolean,
        crossinline errorOf: (T) -> String?,
        noinline fallback: (String?) -> T
    ): T = kohaWithFailover(
        // D-094: the Hono API mounts no koha router. Probe the legacy host first so we do not
        // burn a guaranteed 404 on every catalog search / detail / renew call.
        hosts = listOf(LEGACY_API_BASE_URL, baseUrl),
        fetch = { host ->
            try {
                val response: HttpResponse = httpClient.get(host + relativeUrl) {
                    // D-011: /api/koha/patron* is guarded by Amaze ID.
                    attachAmazeIdAuth()
                }
                val status = response.status.value
                if (status !in 200..299) {
                    KohaProbe(status, null, "HTTP $status")
                } else {
                    val decoded = runCatching { jsonConfig.decodeFromString<T>(response.bodyAsText()) }.getOrNull()
                    KohaProbe(status, decoded, decoded?.let(errorOf))
                }
            } catch (e: Exception) {
                KohaProbe(0, null, e.message)
            }
        },
        isUsable = isUsable,
        fallback = fallback
    )

    /** POST variant of [kohaGet] used by the renew route. The body is encoded eagerly so Ktor's
     * content negotiation does not have to guess a serializer for an erased map type. */
    private suspend inline fun <reified T> kohaPost(
        relativePath: String,
        bodyJson: String,
        noinline isUsable: (T) -> Boolean,
        crossinline errorOf: (T) -> String?,
        noinline fallback: (String?) -> T
    ): T = kohaWithFailover(
        // D-094: legacy host first — Hono has no koha router.
        hosts = listOf(LEGACY_API_BASE_URL, baseUrl),
        fetch = { host ->
            try {
                val response: HttpResponse = httpClient.post(host + relativePath) {
                    contentType(ContentType.Application.Json)
                    attachAmazeIdAuth()
                    setBody(bodyJson)
                }
                val status = response.status.value
                if (status !in 200..299) {
                    KohaProbe(status, null, "HTTP $status")
                } else {
                    val decoded = runCatching { jsonConfig.decodeFromString<T>(response.bodyAsText()) }.getOrNull()
                    KohaProbe(status, decoded, decoded?.let(errorOf))
                }
            } catch (e: Exception) {
                KohaProbe(0, null, e.message)
            }
        },
        isUsable = isUsable,
        fallback = fallback
    )


    // ── Wired M3E data paths ─────────────────────────────────────────────────

    /**
     * Free classrooms — an OFFLINE DERIVATION, not a network fetch.
     *
     * Neither AmazeCC-API nor amazecc-api-hono exposes a room-availability route
     * (the web reference computes this from the FFCS report in `freeClassrooms.ts`),
     * so there is deliberately no `/api/free-classrooms` and no VTOP scraper.
     * REMOTE and LOCAL both converge on the domain snapshot, which the existing
     * sync populates from whichever VTOP source is active; OFFLINE_CACHE reuses
     * the last derived result.
     *
     * Three documented data quirks are handled here:
     *  - `"8:00 AM"` and `"08:00 AM"` are the same period (keyed by minutes),
     *  - venues carry trailing whitespace (trimmed before grouping),
     *  - law courses book `A+TA+TAA` where everyone else books `A1+TA1+TAA1`
     *    (slot ids are canonicalised across both spellings).
     */
    suspend fun getFreeClassrooms(): FreeClassroomsRes {
        if (useMockData) return DemoData.get("freeClassrooms", FreeClassroomsRes.serializer())
            ?: DemoData.freeClassroomsDemo()
        if (vtopSource == com.amazecc.app.shared.vtop.VtopSource.OFFLINE_CACHE) {
            AppState.markServedFromCache("Free Classrooms")
            return AppDataStore.freeClassrooms.value ?: FreeClassroomsRes(
                success = true, message = "Served from local cache"
            )
        }

        val courses = Projections.currentSemesterAttendance(AppState.domain.value)
        if (courses.isEmpty()) {
            return AppDataStore.freeClassrooms.value ?: FreeClassroomsRes(
                success = true, rooms = emptyList(), message = "No timetable data to derive free classrooms"
            )
        }

        val result = deriveFreeClassroomsForCourses(
            courses,
            Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        )
        AppDataStore.setFreeClassrooms(result)
        return result
    }

    /**
     * Pure, clock-injected core of [getFreeClassrooms], shared with the JVM tests.
     *
     * D-191 follow-up: when the clock is outside a real teaching period (weekend, lunch, before the
     * first class or after the last one) there is NO current period. A room is then not advertised
     * as free: [FreeClassroomRoom.isCurrentlyAvailable] stays false and [FreeClassroomRoom.availabilityStatus]
     * stays blank. The old `"unknown"` status plus `isCurrentlyAvailable = true` claimed a fact the
     * derivation did not have and made every cached room look bookable at midnight.
     */
    internal fun deriveFreeClassroomsForCourses(
        courses: List<CourseAttendance>,
        now: LocalDateTime
    ): FreeClassroomsRes {
        // A weekend is not a teaching day. Never silently remap it to Monday: doing so
        // fabricated a "free now" verdict from Monday's timetable on a Sunday.
        val day = now.dayOfWeek.name.take(3).uppercase()
        val minutesNow = now.hour * 60 + now.minute
        val slotsByDay = if (day in SlotMap.days) SlotMap.map[day].orEmpty() else emptyMap<String, String>()

        // Periods are grouped by the *normalised* time range, so a theory slot and
        // the lab slot that overlaps it share one period and one slot set.
        val periods = slotsByDay.entries
            .groupBy { entry -> periodKeyOf(entry.value) }
            .map { (key, entries) ->
                val parts = key.split("-")
                PeriodInfo(
                    key = key,
                    start = parts.getOrElse(0) { "0" }.toIntOrNull() ?: 0,
                    end = parts.getOrElse(1) { "0" }.toIntOrNull() ?: 0,
                    slots = entries.map { canonicalSlot(it.key) }.toSet()
                )
            }
            .sortedBy { it.start }

        // Weekends have slot rows in the schema, but no live teaching day. There is no
        // "free right now" verdict on a Saturday or Sunday, even inside those clock windows.
        val isTeachingDay = day in SlotMap.weekdays
        val current = if (isTeachingDay) {
            periods.firstOrNull { minutesNow >= it.start && minutesNow < it.end }
        } else null

        val rooms = LinkedHashMap<String, RoomDerivationState>()
        for (course in courses) {
            val rawVenue = course.slotVenue.orEmpty()
            val code = rawVenue.trim().uppercase()
            if (code.isEmpty() || code in NOT_A_ROOM || code.contains("ONLINE")) continue
            val block = code.substringBefore("-", "").trim().ifEmpty { "Other" }
            val state = rooms.getOrPut(code) {
                RoomDerivationState(
                    // D-191: only the venue facts we actually have. Floor is not guessed from the
                    // room code, and capacity/AC are not invented (they stay at the model defaults,
                    // which are outside this derivation). Availability is derived below from real
                    // slot occupancy, never defaulted here.
                    info = FreeClassroomRoom(
                        roomNumber = code,
                        buildingCode = block
                    ),
                    occupied = mutableSetOf()
                )
            }
            val courseSlots = course.slotName.split("+").map { canonicalSlot(it) }.toSet()
            for (period in periods) {
                if (courseSlots.any { it in period.slots }) state.occupied.add(period.key)
            }
        }

        val resultRooms = rooms.values.map { state ->
            // No current period means no availability fact at all — never a free verdict.
            val available = current?.let { it.key !in state.occupied } ?: false
            val next = periods.firstOrNull { p ->
                (current == null || p.start >= current.end) && p.key in state.occupied
            }
            state.info.copy(
                availabilityStatus = when {
                    current == null -> ""
                    available -> "free"
                    else -> "occupied"
                },
                isCurrentlyAvailable = available,
                nextOccupiedTime = next?.let { formatClock(it.start) }
            )
        }.sortedBy { it.roomNumber }

        return FreeClassroomsRes(
            success = true,
            rooms = resultRooms,
            day = day,
            period = current?.let { "${formatClock(it.start)} - ${formatClock(it.end)}" } ?: "Outside class hours"
        )
    }

    private val NOT_A_ROOM = setOf("NIL", "N/A", "UNK-UNK", "-", "")

    /** The law timetable's alternate spellings of two `S` slots. */
    private val LAW_S_SLOTS = mapOf("S11" to "TEE", "S15" to "TFF")

    /** `"08:00 AM"`, `"8:00 AM"` and SlotMap's `"8:00"` all parse to the same minute. */
    private fun minutesOf(time: String): Int? {
        val match = Regex("^(\\d{1,2}):(\\d{2})\\s*(AM|PM)?$", RegexOption.IGNORE_CASE)
            .matchEntire(time.trim()) ?: return null
        var hour = match.groupValues[1].toIntOrNull() ?: return null
        val minute = match.groupValues[2].toIntOrNull() ?: return null
        when (match.groupValues[3].uppercase()) {
            "PM" -> if (hour != 12) hour += 12
            "AM" -> if (hour == 12) hour = 0
            "" -> Unit // SlotMap's 24-hour form
            else -> return null
        }
        return hour * 60 + minute
    }

    private fun periodKeyOf(range: String): String {
        val parts = range.split("-", limit = 2)
        val start = minutesOf(parts[0]) ?: return range.trim().lowercase()
        val end = minutesOf(parts.getOrElse(1) { "" }) ?: return range.trim().lowercase()
        return "$start-$end"
    }

    /** Collapse `A1`/`A`, `TC1`/`TC`, `S11`/`TEE`, `S15`/`TFF` onto one id. */
    private fun canonicalSlot(raw: String): String {
        var id = raw.trim().uppercase()
        LAW_S_SLOTS[id]?.let { id = it }
        when (id) {
            "TEE" -> id = "S11"
            "TFF" -> id = "S15"
        }
        val stripped = Regex("^([A-Z]+)1$").matchEntire(id)
        if (stripped != null && stripped.groupValues[1] !in setOf("S", "L")) {
            return stripped.groupValues[1]
        }
        return id
    }

    private fun formatClock(minutes: Int): String {
        val hour24 = (minutes / 60) % 24
        val suffix = if (hour24 < 12) "AM" else "PM"
        val hour12 = if (hour24 % 12 == 0) 12 else hour24 % 12
        return "$hour12:${(minutes % 60).toString().padStart(2, '0')} $suffix"
    }

    private data class PeriodInfo(val key: String, val start: Int, val end: Int, val slots: Set<String>)

    private data class RoomDerivationState(val info: FreeClassroomRoom, val occupied: MutableSet<String>)

    /**
     * Documents — the flattened `/api/circulars` folder tree plus the static
     * official-document entries. There is no `/api/documents` endpoint.
     */
    suspend fun getDocuments(): List<DocumentItem> {
        if (useMockData) return DemoData.get("documents", ListSerializer(DocumentItem.serializer()))
            ?: DemoData.documentsDemo()
        val circulars = getCirculars()
        val items = mutableListOf<DocumentItem>()
        flattenCirculars(circulars.circulars, items)
        items += staticDocuments()
        AppDataStore.setDocuments(items)
        return items
    }

    /** `CircularItem` is a folder tree: `name`/`children` or a leaf `id`/`title`. */
    private fun flattenCirculars(nodes: List<CircularItem>, out: MutableList<DocumentItem>) {
        for (node in nodes) {
            if (!node.children.isNullOrEmpty()) {
                flattenCirculars(node.children, out)
                continue
            }
            val title = node.title ?: node.name ?: continue
            out.add(DocumentItem(id = node.id ?: node.name.orEmpty(), title = title))
        }
    }

    private fun staticDocuments(): List<DocumentItem> = listOf(
        DocumentItem(
            id = "bonafide",
            title = "Bonafide Certificate",
            description = "Certificate of enrolment — request through VTOP Other Services."
        ),
        DocumentItem(
            id = "e-transcript",
            title = "E-Transcript",
            description = "Official electronic transcript — request through VTOP Examinations."
        ),
        DocumentItem(
            id = "additional-learning",
            title = "Additional Learning",
            description = "Additional learning / course-completion records from VTOP."
        )
    )

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

    fun fallbackCachedExamSchedule(semesterId: String?, markOffline: Boolean = false): ExamScheduleRes? {
        val academic = com.amazecc.app.shared.state.AppDataStore.academic.value
        val sem = (if (semesterId != null) academic.semesters[semesterId] else null)
            ?: academic.semesters.values.firstOrNull()
            ?: return null
        if (sem.exams.isEmpty()) return null
        if (markOffline) {
            com.amazecc.app.shared.state.AppState.markServedFromCache("Exam Schedule")
        }
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
            applicationNumberWire = identity.regNo.orEmpty(),
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
        val cachedAll = com.amazecc.app.shared.state.AppDataStore.allGrades.value
        return AllGradesRes(
            success = true,
            grades = cachedAll?.grades?.takeIf { it.isNotEmpty() }
                ?: mapOf(semesterId to com.amazecc.app.shared.model.SemesterGradeResult(grades = gradeRows)),
            // Preserve the published CGPA through the cache fallback; otherwise the UI silently
            // drops back to semester SGPA after a transient fetch failure.
            overall = cachedAll?.overall
                ?: academic.publishedCgpa?.let { com.amazecc.app.shared.model.OverallGrades(cgpa = it) },
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
            val params = attendanceContract(semesterId).bodyFields
            val response = postAuthorized<AttendanceSyncResponse>("attendance", params)
            if (response != null) {
                val attendance = response.attRes ?: AttendanceRes(
                    success = response.success,
                    semesterId = response.semesterId ?: response.attRes?.semester,
                    attendance = response.attendance,
                    error = response.error,
                    message = response.message
                )
                if (attendance.success && attendance.attendance?.isNotEmpty() == true) {
                    return AcademicSyncResult(
                        attendance = attendance.copy(success = attendance.success && attendance.error == null),
                        // D-107: marksRes is already null-tolerant (string-on-failure).
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
        else AcademicSyncResult(AttendanceRes(success = false, error = "Attendance unavailable", message = "Attendance unavailable"))
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

        // REMOTE mode: D-090 — there is no /api/timetable route. The timetable columns are
        // merged into attRes.attendance by POST /api/attendance; derive the timetable from it.
        try {
            val params = attendanceContract(semesterId).bodyFields
            val response = postAuthorized<AttendanceSyncResponse>("attendance", params)
            val items = response?.attRes?.attendance.orEmpty()
            if (items.isNotEmpty()) {
                val derived = timetableFromAttendance(
                    semesterId ?: response?.attRes?.semester,
                    items
                )
                return derived
            }
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

        // REMOTE mode: D-090 — there is no /api/marks route. Marks are embedded in the
        // attendance response as `marksRes` (a string on upstream failure; null-tolerant DTO).
        try {
            val params = attendanceContract(semesterId).bodyFields
            val response = postAuthorized<AttendanceSyncResponse>("attendance", params)
            val remoteMarks = response?.marksRes
            if (remoteMarks != null && remoteMarks.marks.isNotEmpty()) return remoteMarks
        } catch (_: Exception) {}

        if (com.amazecc.app.shared.vtop.vtopEngineSupported && com.amazecc.app.shared.vtop.VtopSession.hasSession && semester != null) {
            try {
                val localRes = com.amazecc.app.shared.vtop.VtopDataSource.fetchMarks(semester)
                if (localRes != null && localRes.success && localRes.courses?.isNotEmpty() == true) return localRes
            } catch (_: Exception) {}
        }

        return fallbackCachedMarks(semesterId) ?: MarksRes(success = false, message = "Empty response")
    }

    /** LOCAL (and REMOTE-failure) path: one scrape → full `AllGradesRes`, then seed the cache. */
    private suspend fun localAllGrades(): AllGradesRes = try {
        val history = com.amazecc.app.shared.vtop.VtopDataSource.fetchGradeHistory()
        if (history == null) {
            fallbackCachedAllGrades()
        } else {
            val hasData = history.courses.isNotEmpty() ||
                history.cgpa.cgpa != null || history.baskets.isNotEmpty()
            if (!hasData) {
                fallbackCachedAllGrades()
            } else {
                val semesterId = com.amazecc.app.shared.vtop.VtopSession.semesterSubId.value.orEmpty()
                val result = buildAllGradesFromGradeHistory(history, semesterId)
                // The history page has no per-semester GPA, grand totals, courseId, assessment
                // breakdown or grade ranges. Walk the semester grade views to fill those in so
                // LOCAL UI is as rich as REMOTE. A failed/empty sweep leaves the base untouched.
                val detailed = try {
                    com.amazecc.app.shared.vtop.VtopDataSource.fetchGradeDetails()
                } catch (_: Exception) {
                    emptyMap()
                }
                val enriched = mergeGradeDetails(result, detailed)
                AppDataStore.upsertGrades(enriched)
                enriched
            }
        }
    } catch (e: Exception) {
        fallbackCachedAllGrades()
    }

    suspend fun getAllGrades(): AllGradesRes {
        if (useMockData) return DemoData.get("allGrades", AllGradesRes.serializer()) ?: AllGradesRes()
        if (vtopSource == com.amazecc.app.shared.vtop.VtopSource.OFFLINE_CACHE) {
            return fallbackCachedAllGrades()
        }
        if (vtopSource == com.amazecc.app.shared.vtop.VtopSource.LOCAL) {
            return localAllGrades()
        }
        return try {
            // D-105: /api/all-grades is not mounted — only /api/grades carries the full
            // {success, overall:{cgpa,curriculum}, semesters:{...}} shape.
            val remote = postAuthorized<AllGradesRes>("grades")
            if (remote != null && remote.success && remote.allSemesters.isNotEmpty()) remote
            else remote?.takeIf { it.success } ?: localAllGrades()
        } catch (e: Exception) {
            // UnknownHostException / Network error: graceful fallback to local scrape, then cache
            localAllGrades()
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
                    ?: com.amazecc.app.shared.state.AppDataStore.hostelDetails.value
                    ?: HostelDetails(success = false, error = "VTOP hostel details unavailable")
            } catch (e: Exception) {
                com.amazecc.app.shared.state.AppDataStore.hostelDetails.value
                    ?: HostelDetails(success = false, error = e.message ?: e.toString())
            }
        }
        return try {
            val res = postAuthorized<HostelDetails>("hostel")
            if (res != null && res.success) {
                com.amazecc.app.shared.state.AppDataStore.setHostelDetails(res)
                res
            } else {
                com.amazecc.app.shared.state.AppDataStore.hostelDetails.value ?: res ?: HostelDetails(success = false, message = "Empty response")
            }
        } catch (e: Exception) {
            com.amazecc.app.shared.state.AppDataStore.hostelDetails.value
                ?: HostelDetails(success = false, message = e.message, error = e.toString())
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
            val res = httpClient.get(url).body<MessMenuRes>()
            com.amazecc.app.shared.state.AppDataStore.setMessMenu(res)
            res
        } catch (e: Exception) {
            com.amazecc.app.shared.state.AppDataStore.messMenu.value ?: MessMenuRes()
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
            val res = httpClient.get("$UNMESSIFY_BASE/$file").body<LaundryRes>()
            com.amazecc.app.shared.state.AppDataStore.setLaundrySchedule(res)
            res
        } catch (e: Exception) {
            com.amazecc.app.shared.state.AppDataStore.laundrySchedule.value ?: LaundryRes()
        }
    }

    /** D-100: whitelisted VTOP proxies now live under POST /api/bulk. */
    private suspend fun bulkResults(vararg names: String): Map<String, JsonElement> {
        if (vtopSource != com.amazecc.app.shared.vtop.VtopSource.REMOTE) return emptyMap()
        val contract = bulkContract(names.toList())
        require(contract.path == "/api/bulk" && contract.method == "POST")
        val request = buildJsonObject {
            put("endpoints", buildJsonArray { names.forEach { add(JsonPrimitive(it)) } })
        }.toString()
        return postAuthorizedBody<BulkResponse>(contract.path.removePrefix("/api/"), request)?.results.orEmpty()
    }

    suspend fun getHostelCounselling(): ArrearResponse {
        if (useMockData) return DemoData.get("hostelCounselling", ArrearResponse.serializer()) ?: ArrearResponse()
        return try {
            val element = bulkResults("hostel-counselling")["hostel-counselling"]
            if (element != null) arrearFromBulkResult(element)
            else ArrearResponse(success = false, message = "Hostel counselling unavailable")
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
                val res = com.amazecc.app.shared.vtop.VtopExamScraper.fetchExamSchedule(semester)
                    ?: com.amazecc.app.shared.vtop.VtopDataSource.fetchExamSchedule(semester)
                if (res != null && res.success && res.schedule.isNotEmpty()) res
                else fallbackCachedExamSchedule(semester, markOffline = true) ?: res ?: ExamScheduleRes(success = false, error = "VTOP exam schedule unavailable")
            } catch (e: Exception) {
                fallbackCachedExamSchedule(semesterId, markOffline = true) ?: ExamScheduleRes(success = false, error = e.toString())
            }
        }

        try {
            if (semesterId != null) {
                // Canonical route (AmazeCC-API / AmazeCC): /api/ffcs with section=schedule.
                val ffcs = postAuthorized<JsonObject>(
                    "ffcs",
                    ffcsContract("schedule", semesterId).bodyFields
                )
                val scheduleElement = runCatching { ffcs?.get("schedule")?.jsonObject?.get("Schedule") }.getOrNull()
                    ?: runCatching { ffcs?.get("Schedule") }.getOrNull()
                if (scheduleElement != null) {
                    val parsed = Json.decodeFromJsonElement<Map<String, List<com.amazecc.app.shared.model.ExamItem>>>(scheduleElement)
                    if (parsed.isNotEmpty()) return ExamScheduleRes(success = true, rawScheduleUpper = parsed)
                }
            }
            // D-105: /api/schedule is not mounted; /api/ffcs section=schedule above is canonical.
        } catch (_: Exception) {}

        if (com.amazecc.app.shared.vtop.vtopEngineSupported && com.amazecc.app.shared.vtop.VtopSession.hasSession && semester != null) {
            try {
                val localRes = com.amazecc.app.shared.vtop.VtopExamScraper.fetchExamSchedule(semester)
                    ?: com.amazecc.app.shared.vtop.VtopDataSource.fetchExamSchedule(semester)
                if (localRes != null && localRes.success && localRes.schedule.isNotEmpty()) return localRes
            } catch (_: Exception) {}
        }

        return fallbackCachedExamSchedule(semesterId, markOffline = true) ?: ExamScheduleRes(success = false, message = "Empty response")
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
                    ?: com.amazecc.app.shared.state.AppDataStore.curriculum.value
                    ?: CurriculumRes(success = false, error = "VTOP curriculum unavailable")
            } catch (e: Exception) {
                com.amazecc.app.shared.state.AppDataStore.curriculum.value
                    ?: CurriculumRes(success = false, error = e.toString())
            }
        }
        return try {
            val params = mutableMapOf<String, String>()
            if (semesterId != null) params["semesterId"] = semesterId
            val res = postAuthorized<CurriculumRes>("curriculum", params)
            if (res != null && res.success) {
                com.amazecc.app.shared.state.AppDataStore.setCurriculum(res)
                res
            } else {
                com.amazecc.app.shared.state.AppDataStore.curriculum.value ?: res ?: CurriculumRes(success = false, message = "Empty response")
            }
        } catch (e: Exception) {
            com.amazecc.app.shared.state.AppDataStore.curriculum.value
                ?: CurriculumRes(success = false, message = e.message, error = e.toString())
        }
    }

    /**
     * Downloads the curriculum PDF/ZIP published by VTOP's `academics/curriculDown` endpoint.
     *
     * REMOTE: `POST /api/curriculum/download`, which streams the upstream bytes through the Hono
     * proxy. LOCAL: the on-device scraper walks the same VTOP path with the on-device session.
     * OFFLINE_CACHE / mock data: null, because no binary is ever cached or fabricated.
     *
     * Failure at any stage yields null — no placeholder bytes are manufactured.
     */
    suspend fun downloadCurriculum(): ByteArray? {
        if (useMockData) return null
        if (vtopSource == com.amazecc.app.shared.vtop.VtopSource.OFFLINE_CACHE) return null
        if (vtopSource == com.amazecc.app.shared.vtop.VtopSource.LOCAL) {
            return try {
                com.amazecc.app.shared.vtop.VtopDataSource.fetchCurriculumDownload()
            } catch (_: Exception) {
                null
            }
        }

        // REMOTE — mirror downloadCircular's session handling before the raw-binary POST.
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
        if (!SessionManager.hasRemoteCookies || cookies == null || authorizedID == null || csrf == null) return null
        return postAuthorizedBytes("curriculum/download")
    }

    suspend fun getCalendar(
        type: String = "ALL",
        semesterId: String? = null,
        allowUnscopedCache: Boolean = true,
    ): CalendarRes {
if (useMockData) return DemoData.get("calendar", CalendarRes.serializer()) ?: CalendarRes()
        // A blank-but-non-null semester id wins the Elvis chain and bypasses every fallback,
        // making every LOCAL fetch fall through to postAuthorized (a no-op outside REMOTE).
        // Treat blanks as absent so the session / selected-semester fallbacks can run.
        val sem = effectiveSemester(
            requested = semesterId,
            session = com.amazecc.app.shared.vtop.VtopSession.semesterSubId.value,
            selected = com.amazecc.app.shared.state.AppState.selectedSemester.value,
            firstAcademic = com.amazecc.app.shared.state.AppDataStore.academic.value.semesters.keys
                .firstOrNull()
        )
        fun unscopedCalendar() = com.amazecc.app.shared.state.AppDataStore.calendar.value
            ?.takeIf { allowUnscopedCache && it.months.isNotEmpty() }
        if (vtopSource == com.amazecc.app.shared.vtop.VtopSource.OFFLINE_CACHE) {
            return unscopedCalendar()
                ?: CalendarRes(success = true, message = "Served from local cache")
        }
        // The dedicated headless parser mirrors the reference API's month derivation and
        // sets explicit year/month on every month, so the grid starts and ends on real dates.
        if (vtopSource == com.amazecc.app.shared.vtop.VtopSource.LOCAL && sem != null) {
            val scraped = com.amazecc.app.shared.vtop.VtopCalendarScraper.fetchCalendar(sem, type)
            if (scraped != null && scraped.months.isNotEmpty()) return scraped
        }
        return try {
            val rawJson = if (vtopSource == com.amazecc.app.shared.vtop.VtopSource.LOCAL && sem != null) {
                // Legacy raw path kept as a fallback when the dedicated parser fails.
                com.amazecc.app.shared.vtop.VtopDataSource.fetchCalendarRaw(sem, type)
            } else {
                val params = mutableMapOf("type" to type)
                if (sem != null) params["semesterId"] = sem
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
            // Remote unavailable → headless scraper is the local source of truth, then cache.
            if (sem != null && com.amazecc.app.shared.vtop.vtopEngineSupported) {
                com.amazecc.app.shared.vtop.VtopCalendarScraper.fetchCalendar(sem, type)
                    ?.takeIf { it.months.isNotEmpty() }?.let { return it }
            }
            unscopedCalendar()
                ?: CalendarRes(success = false, message = "Empty response")
        } catch (e: Exception) {
            unscopedCalendar()
                ?: CalendarRes(success = false, message = e.message, error = e.toString())
        }
    }

    suspend fun getCalendars(semesterId: String? = null): CalendarsListRes {
if (useMockData) return DemoData.get("calendars", CalendarsListRes.serializer()) ?: CalendarsListRes()
        val cachedSingle = com.amazecc.app.shared.state.AppDataStore.calendar.value
            ?.takeIf { it.months.isNotEmpty() }
        fun fromSingle() = cachedSingle?.let {
            CalendarsListRes(success = true, calendars = listOf(NamedCalendar("General Semester", it.months)))
        }
        if (vtopSource == com.amazecc.app.shared.vtop.VtopSource.OFFLINE_CACHE) {
            return com.amazecc.app.shared.state.AppDataStore.calendarsList.value
                ?.takeIf { it.calendars.isNotEmpty() }
                ?: fromSingle()
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
                fromSingle()
                    ?: CalendarsListRes(success = false, message = results.firstOrNull()?.first?.message ?: "Empty response")
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
                    ?: com.amazecc.app.shared.state.AppDataStore.payments.value
                    ?: PaymentsRes(success = false, error = "VTOP payments unavailable")
            } catch (e: Exception) {
                com.amazecc.app.shared.state.AppDataStore.payments.value
                    ?: PaymentsRes(success = false, error = e.message ?: e.toString())
            }
        }
        return try {
            // D-022: decode the whole finance response once with typed DTOs so receipts,
            // refunds, invoices and the USD ledger are never dropped by an ad-hoc projection.
            val finance = postAuthorized<FinanceResponse>("finance", mapOf("section" to "all"))
                ?: return com.amazecc.app.shared.state.AppDataStore.payments.value
                    ?: PaymentsRes(success = false, message = "Empty response")
            val paymentsList = mutableListOf<PaymentItem>()

            if (finance.dues?.hasDues == true) {
                paymentsList.add(PaymentItem(
                    billingId = "due-pending",
                    description = finance.dues.message?.takeIf { it.isNotBlank() } ?: "Pending Dues",
                    amount = "Check VTOP",
                    dueDate = "-",
                    status = "UNPAID"
                ))
            }

            finance.receipts?.receipts.orEmpty().forEach { r ->
                paymentsList.add(PaymentItem(
                    billingId = r.receiptNumber.ifBlank { r.applNo ?: "rec" },
                    description = "Fee Payment",
                    amount = r.amount ?: "-",
                    dueDate = "-",
                    status = "PAID",
                    paymentDate = r.date,
                    receiptNo = r.receiptNumber.ifBlank { null }
                ))
            }

            finance.wallet?.ledgerINR.orEmpty().forEach { e ->
                paymentsList.add(PaymentItem(
                    billingId = e.receiptNumber ?: "ledger",
                    description = listOfNotNull(e.transactionType, e.particulars).joinToString(" - ").ifBlank { "Wallet Transaction" },
                    amount = e.amount ?: e.refundAmount ?: "-",
                    dueDate = e.transactionDate,
                    status = if (e.refundAmount != null) "REFUNDED" else "PAID",
                    paymentDate = e.transactionDate,
                    receiptNo = e.receiptNumber
                ))
            }

            val balance = finance.wallet?.ledgerINR?.lastOrNull()?.bookBalanceAmount
            val paymentsRes = PaymentsRes(
                success = true,
                payments = paymentsList,
                walletBalance = balance,
                dues = finance.dues,
                receipts = finance.receipts?.receipts.orEmpty(),
                ledgerINR = finance.wallet?.ledgerINR.orEmpty(),
                ledgerUSD = finance.wallet?.ledgerUSD.orEmpty(),
                message = if (finance.dues?.hasDues == false) finance.dues.message else null,
                error = finance.error
            )
            com.amazecc.app.shared.state.AppDataStore.setPayments(paymentsRes)
            paymentsRes
        } catch (e: Exception) {
            com.amazecc.app.shared.state.AppDataStore.payments.value
                ?: PaymentsRes(success = false, message = e.message, error = e.toString())
        }
    }

    suspend fun getLibrary(libUsername: String? = null, libPassword: String? = null): LibraryRes {
        if (useMockData) return DemoData.get("library", LibraryRes.serializer()) ?: LibraryRes()
        if (vtopSource == com.amazecc.app.shared.vtop.VtopSource.OFFLINE_CACHE) {
            return com.amazecc.app.shared.state.AppDataStore.library.value
                ?: LibraryRes(success = true, message = "Served from local cache")
        }
        // VTOP dues come from the live session and need no Koha credentials. The reference
        // API exposes the same page as POST /api/finance {section=library}.
        if (vtopSource == com.amazecc.app.shared.vtop.VtopSource.LOCAL &&
            com.amazecc.app.shared.vtop.vtopEngineSupported &&
            com.amazecc.app.shared.vtop.VtopSession.hasSession
        ) {
            val local = com.amazecc.app.shared.vtop.VtopDataSource.fetchLibrary()
            if (local != null) {
                com.amazecc.app.shared.state.AppDataStore.setLibrary(local)
                return local
            }
        }
        val creds = if (libUsername != null && libPassword != null) {
            mapOf("libUsername" to libUsername, "libPassword" to libPassword)
        } else {
            val saved = com.amazecc.app.shared.repository.SettingsManager.getLibraryCredentials()
            if (saved != null) mapOf("libUsername" to saved.first, "libPassword" to saved.second) else emptyMap()
        }
        if (creds.isEmpty()) {
            return com.amazecc.app.shared.state.AppDataStore.library.value?.takeIf { it.effectiveBooks.isNotEmpty() }
                ?: LibraryRes(success = false, message = "Library login required", error = "NO_LIB_CREDS")
        }
        return try {
            val params = creds + ("section" to "library")
            // D-017/D-105: /api/finance?section=library is canonical; /api/library-due is not
            // mounted. The library dues are nested under `library.books`, so lift them into the
            // flat `booksIssued` list the UI already renders.
            val res = postAuthorized<LibraryRes>("finance", params)
                ?: return com.amazecc.app.shared.state.AppDataStore.library.value
                    ?: LibraryRes(success = false, message = "Empty response")
            val normalised = if (res.booksIssued.isEmpty() && res.libraryDues?.books?.isNotEmpty() == true) {
                res.copy(booksIssued = res.libraryDues.booksIssued)
            } else res
            com.amazecc.app.shared.state.AppDataStore.setLibrary(normalised)
            normalised
        } catch (e: Exception) {
            com.amazecc.app.shared.state.AppDataStore.library.value
                ?: LibraryRes(success = false, message = e.message, error = e.toString())
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
        return kohaGet<KohaSearchRes>(
            relativeUrl = "/api/koha/search?q=${query.encodeURLParameter()}&idx=${index.encodeURLParameter()}&offset=$offset&count=20",
            isUsable = { it.success },
            errorOf = { it.error ?: it.message },
            fallback = { err ->
                val message = kohaErrorMessage(err, null)
                KohaSearchRes(success = false, message = message, error = err)
            }
        )
    }

    suspend fun getKohaDetail(biblionumber: String): KohaDetailRes {
        if (useMockData) return KohaDetailRes(success = false, error = "No mock data for detail")
        return kohaGet<KohaDetailRes>(
            relativeUrl = "/api/koha/detail?biblionumber=${biblionumber.encodeURLParameter()}",
            isUsable = { it.success && it.book != null },
            errorOf = { it.error ?: it.message },
            fallback = { err ->
                val message = kohaErrorMessage(err, null)
                KohaDetailRes(success = false, message = message, error = err)
            }
        )
    }
    
    suspend fun renewLibraryBook(bookId: String): BasicRes {
if (useMockData) return DemoData.get("libraryRenew", BasicRes.serializer()) ?: BasicRes(success = true, message = "Book renewed successfully")
        val creds = com.amazecc.app.shared.repository.SettingsManager.getLibraryCredentials()
            ?: return BasicRes(success = false, message = "Library credentials not found.")
        val body = jsonConfig.encodeToString(
            JsonObject.serializer(),
            buildJsonObject {
                put("username", creds.first)
                put("password", creds.second)
                put("bookId", bookId)
            }
        )
        return kohaPost<BasicRes>(
            relativePath = "/api/koha/renew",
            bodyJson = body,
            isUsable = { it.success },
            errorOf = { it.message },
            fallback = { err -> BasicRes(success = false, message = kohaErrorMessage(err, null)) }
        )
    }
    suspend fun getTransportData(): TransportDataRes {
        if (useMockData) return DemoData.get("transport", TransportDataRes.serializer()) ?: TransportDataRes()
        if (vtopSource == com.amazecc.app.shared.vtop.VtopSource.OFFLINE_CACHE) {
            AppState.markServedFromCache("Transport Data")
            return AppDataStore.transportData.value
                ?: TransportDataRes(success = true, message = "Served from local cache")
        }
        if (vtopSource == com.amazecc.app.shared.vtop.VtopSource.LOCAL) {
            return try {
                com.amazecc.app.shared.vtop.VtopDataSource.fetchTransport()
                    ?: com.amazecc.app.shared.state.AppDataStore.transportData.value
                    ?: TransportDataRes(success = false, message = "VTOP transport unavailable")
            } catch (e: Exception) {
                com.amazecc.app.shared.state.AppDataStore.transportData.value
                    ?: TransportDataRes(success = false, message = e.message, error = e.toString())
            }
        }
        return try {
            val res = postAuthorized<TransportDataRes>("transport")
            // /api/transport can return HTTP 200 with hasRegistration=false and an
            // `error` field; that is a failure, not usable data.
            if (res != null && res.success && res.error.isNullOrBlank()) {
                com.amazecc.app.shared.state.AppDataStore.setTransportData(res)
                res
            } else {
                if (com.amazecc.app.shared.vtop.vtopEngineSupported && com.amazecc.app.shared.vtop.VtopSession.hasSession) {
                    try {
                        val local = com.amazecc.app.shared.vtop.VtopDataSource.fetchTransport()
                        if (local != null && local.success) return local
                    } catch (_: Exception) {}
                }
                com.amazecc.app.shared.state.AppDataStore.transportData.value ?: res ?: TransportDataRes(success = false, message = "Empty response")
            }
        } catch (e: Exception) {
            if (com.amazecc.app.shared.vtop.vtopEngineSupported && com.amazecc.app.shared.vtop.VtopSession.hasSession) {
                try {
                    val local = com.amazecc.app.shared.vtop.VtopDataSource.fetchTransport()
                    if (local != null && local.success) return local
                } catch (_: Exception) {}
            }
            com.amazecc.app.shared.state.AppDataStore.transportData.value
                ?: TransportDataRes(success = false, message = e.message, error = e.toString())
        }
    }

    suspend fun getOdDetails(semesterId: String): com.amazecc.app.shared.model.OdDetailsRes {
        if (useMockData) return DemoData.get("od", com.amazecc.app.shared.model.OdDetailsRes.serializer())
            ?: DemoData.odDetailsDemo(semesterId)
        if (vtopSource == com.amazecc.app.shared.vtop.VtopSource.OFFLINE_CACHE) {
            AppState.markServedFromCache("OD Details")
            return AppDataStore.odDetails.value
                ?: com.amazecc.app.shared.model.OdDetailsRes(
                    success = true, semesterId = semesterId, note = "Served from local cache"
                )
        }
        if (vtopSource == com.amazecc.app.shared.vtop.VtopSource.LOCAL) {
            return try {
                com.amazecc.app.shared.vtop.VtopDataSource.fetchOdDetails(semesterId)
                    ?.also { AppDataStore.setOdDetails(it) }
                    ?: com.amazecc.app.shared.model.OdDetailsRes(success = false, error = "VTOP OD details unavailable")
            } catch (e: Exception) {
                com.amazecc.app.shared.model.OdDetailsRes(success = false, error = e.message ?: e.toString())
            }
        }
        return try {
            val res = postAuthorized<com.amazecc.app.shared.model.OdDetailsRes>("od", mapOf("semesterId" to semesterId))
            if (res != null && res.success) {
                AppDataStore.setOdDetails(res)
                res
            } else {
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

    // ── D-192: public bus routes (web cache contract) ──────────────────────────────

    /** Same tolerance as [jsonConfig], exposed so the envelope is testable without HTTP. */
    internal val busesJson = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    internal fun decodeBuses(text: String): BusesRes? =
        runCatching { busesJson.decodeFromString<BusesRes>(text) }.getOrNull()

    /**
     * The cache/Hono envelope can fail with only `message` and no `error`. A null `error` is
     * therefore NOT proof of a successful fetch.
     */
    internal fun busesSucceeded(res: BusesRes): Boolean =
        res.success && res.error == null && res.message.isNullOrBlank()

    internal fun busesFailureMessage(res: BusesRes): String =
        res.error?.takeIf { it.isNotBlank() }
            ?: res.message?.takeIf { it.isNotBlank() }
            ?: "Bus routes unavailable"

    /** Never overwrite a good cached route list with an empty `success = false` envelope. */
    internal fun busesFailure(cached: BusesRes?, fetched: BusesRes?): BusesRes {
        val buses = cached?.takeIf { it.success && it.buses.isNotEmpty() }?.buses.orEmpty()
        val reason = if (fetched == null) "Bus routes could not be reached" else busesFailureMessage(fetched)
        return BusesRes(
            success = false,
            buses = buses,
            message = if (buses.isEmpty()) reason else "$reason (showing last cached routes)",
        )
    }

    suspend fun getBuses(): BusesRes {
        if (useMockData) return DemoData.get("buses", BusesRes.serializer()) ?: BusesRes()
        val cached = AppDataStore.buses.value
        // Web order: public cache GET first, then the unauthenticated Hono/legacy POST ops.
        // `body` is only used by the POST fallbacks; it is deliberately empty (no credentials).
        val fetched = mountedPost(
            op = "buses",
            auth = com.amazecc.app.shared.domain.ApiAuth.NONE,
            body = { JsonObject(emptyMap()) },
            decode = { decodeBuses(it) },
            preferCache = true,
        )
        if (fetched != null && busesSucceeded(fetched)) return fetched
        // Failure (or malformed payload): keep the last good list and report why. AppState's
        // BUSES sync predicate will not persist this result, so the cache stays intact.
        val failure = busesFailure(cached, fetched)
        if (failure.buses.isNotEmpty()) AppState.markServedFromCache("Buses")
        return failure
    }

    suspend fun submitTransportRegistration(request: TransportRegRequest): TransportRegSubmitRes {
if (useMockData) return DemoData.get("transportRegister", TransportRegSubmitRes.serializer()) ?: TransportRegSubmitRes()
        // D-097: /api/transport is read-only; /api/transport/register does not exist. A write
        // cannot succeed against the current API, so report that explicitly instead of 404ing.
        return TransportRegSubmitRes(
            success = false,
            message = "Transport registration must be completed on VTOP; the API is read-only"
        )
    }

    /**
     * D-102: `/api/cabshare/auth` is POST and authenticates the caller by Amaze ID bearer/DPoP
     * only — the VTOP username/password/registration body is ignored by the API. The parameters
     * remain in the signature for source compatibility but are not sent.
     */
    suspend fun cabShareAuth(username: String, password: String, phoneNumber: String): CabShareAuthRes {
if (useMockData) return DemoData.get("cabShareAuth", CabShareAuthRes.serializer()) ?: CabShareAuthRes()
        return when (val result = amazeIdRequest<CabShareAuthRes>("/api/cabshare/auth", HttpMethod.Post)) {
            is GuardedResult.Success -> result.value
            GuardedResult.AuthRequired -> CabShareAuthRes(success = false, error = AMAZE_AUTH_REQUIRED)
            is GuardedResult.Failure -> CabShareAuthRes(success = false, error = result.message)
        }
    }

    /**
     * D-104: `GET /api/cabshare/hubs` sits behind the `/api/cabshare-routes` Amaze ID guard. Route it
     * through the auth helper so a 401 produces an explicit [GuardedResult.AuthRequired] rather
     * than a silent fallback to the bundled hubs.
     */
    suspend fun getCabHubsGuarded(): GuardedResult<List<CabShareHub>> {
        return when (val result = amazeIdRequest<CabShareHubsRes>(
            cabshareHubsContract().path,
            HttpMethod.Get
        )) {
            is GuardedResult.Success -> GuardedResult.Success(
                result.value.hubs.takeIf { it.isNotEmpty() } ?: fallbackCabHubs
            )
            GuardedResult.AuthRequired -> GuardedResult.AuthRequired
            is GuardedResult.Failure -> GuardedResult.Failure(result.message)
        }
    }

    /** Explicit result for callers that need the AMAZE_AUTH_REQUIRED marker as a value. */
    suspend fun getCabHubsResult(): CabHubsResult = when (val result = getCabHubsGuarded()) {
        is GuardedResult.Success -> CabHubsResult.Hubs(result.value)
        GuardedResult.AuthRequired -> CabHubsResult.Error(AMAZE_AUTH_REQUIRED)
        is GuardedResult.Failure -> CabHubsResult.Error(result.message)
    }

    /** Source-compatible wrapper; auth failures are also flagged in `SessionManager.amazeAuthRequired`. */
    suspend fun getCabHubs(): List<CabShareHub> = when (val result = getCabHubsResult()) {
        is CabHubsResult.Hubs -> result.hubs
        is CabHubsResult.Error -> emptyList()
    }

    /** D-102: trip search is a GET; a POST to `/trips` would CREATE a trip. */
    suspend fun searchCabShareTrips(fromHubId: Int?, toHubId: Int?, date: String): CabShareTripsRes {
if (useMockData) return DemoData.get("cabShareSearch", CabShareTripsRes.serializer()) ?: CabShareTripsRes()
        val params = mutableMapOf<String, String>()
        // The API has one destination `hub_id`; keep the legacy `from_hub_id` only as a
        // fallback when no destination was supplied.
        (toHubId ?: fromHubId)?.let { params["hub_id"] = it.toString() }
        if (date.isNotBlank()) params["date"] = date
        return when (val result = amazeIdRequest<CabShareTripsRes>("/api/cabshare/trips", HttpMethod.Get, query = params)) {
            is GuardedResult.Success -> result.value
            GuardedResult.AuthRequired -> CabShareTripsRes(success = false, error = AMAZE_AUTH_REQUIRED)
            is GuardedResult.Failure -> CabShareTripsRes(success = false, error = result.message)
        }
    }

    suspend fun createCabShareTrip(
        fromHubId: Int, toHubId: Int, date: String, time: String,
        tolerance: Double, seats: Int, gender: String, notes: String
    ): CabActionRes {
if (useMockData) return DemoData.get("cabShareCreate", CabActionRes.serializer()) ?: CabActionRes()
        val body = buildJsonObject {
            put("hub_id", toHubId.takeIf { it != 0 } ?: fromHubId)
            put("travel_date", date)
            put("preferred_time", time)
            put("tolerance_hours", tolerance)
            put("gender_preference", gender)
            put("notes", notes)
            put("seat_options", buildJsonObject { put("requested", seats) })
        }.toString()
        return when (val result = amazeIdRequest<CabActionRes>("/api/cabshare/trips", HttpMethod.Post, bodyJson = body)) {
            is GuardedResult.Success -> result.value
            GuardedResult.AuthRequired -> CabActionRes(success = false, message = AMAZE_AUTH_REQUIRED)
            is GuardedResult.Failure -> CabActionRes(success = false, message = result.message, error = result.message)
        }
    }

    /** D-102: `GET /trips/me` has no query parameters — identity comes from the bearer token. */
    suspend fun getMyCabShareTrips(regNumber: String): CabShareTripsRes {
if (useMockData) return DemoData.get("cabShareMyTrips", CabShareTripsRes.serializer()) ?: CabShareTripsRes()
        return when (val result = amazeIdRequest<CabShareTripsRes>("/api/cabshare/trips/me")) {
            is GuardedResult.Success -> result.value
            GuardedResult.AuthRequired -> CabShareTripsRes(success = false, error = AMAZE_AUTH_REQUIRED)
            is GuardedResult.Failure -> CabShareTripsRes(success = false, error = result.message)
        }
    }

    suspend fun requestCabShareJoin(regNumber: String, tripId: Long): CabActionRes {
        val body = buildJsonObject {
            put("trip_id", tripId)
            put("action", "request")
        }.toString()
        return when (val result = amazeIdRequest<CabActionRes>("/api/cabshare/match", HttpMethod.Post, bodyJson = body)) {
            is GuardedResult.Success -> result.value
            GuardedResult.AuthRequired -> CabActionRes(success = false, message = AMAZE_AUTH_REQUIRED)
            is GuardedResult.Failure -> CabActionRes(success = false, message = result.message, error = result.message)
        }
    }

    suspend fun cabShareMatchAction(regNumber: String, matchId: Long, action: String): CabActionRes {
        val body = buildJsonObject {
            put("match_id", matchId)
            put("action", action)
        }.toString()
        return when (val result = amazeIdRequest<CabActionRes>("/api/cabshare/match", HttpMethod.Post, bodyJson = body)) {
            is GuardedResult.Success -> result.value
            GuardedResult.AuthRequired -> CabActionRes(success = false, message = AMAZE_AUTH_REQUIRED)
            is GuardedResult.Failure -> CabActionRes(success = false, message = result.message, error = result.message)
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
        // D-091: /api/lms-data authenticates with Moodle {username, pass} (NOT VTOP cookies)
        // and returns a bare Assignment[]. Reuse fetchMoodleData, which already does both.
        val moodleCreds = com.amazecc.app.shared.repository.SettingsManager.getMoodleCredentials()
            ?: return LMSRes(success = false, message = "Moodle credentials not set")
        return try {
            lmsFromMoodle(fetchMoodleData(moodleCreds.first, moodleCreds.second))
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
        // D-098: no /api/qcm route. QCM lives under `POST /api/ffcs section=qcm`, whose
        // `qcmView` slice has `{success, semesters:[{value,text}], data}` — QcmViewRes now
        // accepts both the object and legacy string forms of `semesters`.
        return try {
            val ffcs = postAuthorized<JsonObject>("ffcs", ffcsContract("qcm").bodyFields)
            val qcmElement = ffcs?.get("qcmView")
            if (qcmElement != null) {
                jsonConfig.decodeFromJsonElement<QcmViewRes>(qcmElement)
            } else {
                com.amazecc.app.shared.state.AppDataStore.qcmView.value
                    ?: QcmViewRes(success = false, message = "QCM unavailable")
            }
        } catch (e: Exception) {
            QcmViewRes(success = false, message = e.message, error = e.toString())
        }
    }

    suspend fun getEvents(): EventHubRes {
if (useMockData) return DemoData.get("events", EventHubRes.serializer()) ?: EventHubRes()
        if (vtopSource == com.amazecc.app.shared.vtop.VtopSource.OFFLINE_CACHE) {
            AppState.markServedFromCache("Events")
            return AppDataStore.events.value
                ?: EventHubRes(success = true, message = "Served from local cache")
        }
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
        // D-092: the Hono API mounts no events router. Serve the last cached list with an
        // explicit status rather than hitting a guaranteed 404. Full port needs a legacy host.
        AppState.markServedFromCache("Events")
        return AppDataStore.events.value
            ?: EventHubRes(success = false, message = "Event Hub is available in VTOP Local mode only")
    }

    suspend fun getEventPreview(eid: String): EventHubPreview? {
        if (useMockData) return null
        // Previews are fetched live; there is no persisted preview cache to serve offline.
        if (vtopSource == com.amazecc.app.shared.vtop.VtopSource.OFFLINE_CACHE) return null
        if (vtopSource == com.amazecc.app.shared.vtop.VtopSource.LOCAL) {
            return try {
                com.amazecc.app.shared.vtop.VtopEventHub.fetchEventPreview(eid)
            } catch (e: Exception) {
                null
            }
        }
        // D-092: /api/events/preview is not mounted. Previews stay a Local-mode feature.
        return null
    }

    suspend fun registerForEvent(eid: String): EventHubRegisterRes? {
        if (useMockData) return null
        // Registration is a write; OFFLINE_CACHE deliberately does not fake a result.
        if (vtopSource == com.amazecc.app.shared.vtop.VtopSource.OFFLINE_CACHE) return null
        if (vtopSource == com.amazecc.app.shared.vtop.VtopSource.LOCAL) {
            return try {
                com.amazecc.app.shared.vtop.VtopEventHub.registerForEvent(eid)
            } catch (e: Exception) {
                null
            }
        }
        // D-092: /api/events/register is not mounted. Registration stays a Local-mode feature.
        return null
    }

    /** Payment hand-off and receipt/certificate downloads, pinned to Event Hub by the on-device client. */
    suspend fun getEventPayNow(url: String): EventHubRegisterRes? {
        if (useMockData) return null
        if (vtopSource == com.amazecc.app.shared.vtop.VtopSource.LOCAL ||
            vtopSource == com.amazecc.app.shared.vtop.VtopSource.REMOTE
        ) {
            return try {
                com.amazecc.app.shared.vtop.VtopEventHub.buildPaymentLoginHtml(url)
            } catch (e: Exception) {
                null
            }
        }
        return null
    }

    suspend fun downloadEventFile(url: String): ByteArray? {
        if (useMockData) return null
        return try {
            com.amazecc.app.shared.vtop.VtopEventHub.downloadEventFile(url)
        } catch (e: Exception) {
            null
        }
    }

    suspend fun eventLogin(): String? {
        // LOCAL authenticates inside VtopEventHub per call; OFFLINE_CACHE has no session at all.
        // Neither mode should make a Hono request just to obtain an Event Hub cookie.
        if (vtopSource != com.amazecc.app.shared.vtop.VtopSource.REMOTE) return null
        // D-092: /api/events/login is not mounted. Event Hub login stays a Local-mode feature.
        return null
    }

    suspend fun getEventsProfile(): EventHubRegisteredEventsRes {
if (useMockData) return DemoData.get("eventsProfile", EventHubRegisteredEventsRes.serializer()) ?: EventHubRegisteredEventsRes()
        if (vtopSource == com.amazecc.app.shared.vtop.VtopSource.OFFLINE_CACHE) {
            AppState.markServedFromCache("Registered Events")
            return AppDataStore.registeredEvents.value
                ?: EventHubRegisteredEventsRes(success = true, message = "Served from local cache")
        }
        val creds = com.amazecc.app.shared.repository.SettingsManager.getCredentials()
        if (vtopSource == com.amazecc.app.shared.vtop.VtopSource.LOCAL && creds != null) {
            return try {
                com.amazecc.app.shared.vtop.VtopEventHub.fetchEventsProfile(creds.first, creds.second)
                    ?: EventHubRegisteredEventsRes(success = false, error = "EventHub unavailable")
            } catch (e: Exception) {
                EventHubRegisteredEventsRes(success = false, message = "Network error: ${e.message}", error = e.toString())
            }
        }
        // D-092: /api/events/profile is not mounted. Serve the cached registrations only.
        AppState.markServedFromCache("Registered Events")
        return AppDataStore.registeredEvents.value
            ?: EventHubRegisteredEventsRes(success = false, message = "Event Hub is available in VTOP Local mode only")
    }

    suspend fun getClubs(): ClubsRes = getClubsDetails()

    suspend fun getClubsDetails(): ClubsRes {
if (useMockData) return DemoData.get("clubs", ClubsRes.serializer()) ?: ClubsRes()
        // D-093: /api/clubs/details is not mounted. The only live club endpoints
        // (/api/club-admin/details, /api/admin/clubs) are per-club/admin-only, so there is no
        // public list to repoint to. Serve the cached list and say so explicitly.
        AppState.markServedFromCache("Clubs")
        return ClubsRes(success = false, message = "Club directory is unavailable on the current API")
    }

    private val imageByteCache = mutableMapOf<String, ByteArray>()

    suspend fun getImageBytes(url: String): ByteArray? {
        imageByteCache[url]?.let { return it }
        return try {
            val response: HttpResponse = httpClient.get(url) {
                val token = SessionManager.clubToken.value
                if (!token.isNullOrEmpty() && url.contains("eventhubcc.vit.ac.in")) {
                    header(HttpHeaders.Cookie, "JSESSIONID=$token")
                }
            }
            if (response.status == HttpStatusCode.OK) {
                val bytes = response.body<ByteArray>()
                if (bytes.isNotEmpty()) {
                    if (imageByteCache.size > 200) {
                        imageByteCache.keys.firstOrNull()?.let { imageByteCache.remove(it) }
                    }
                    imageByteCache[url] = bytes
                }
                bytes
            } else null
        } catch (e: Exception) {
            null
        }
    }

    /** D-101: one consolidated whoami fetch, reused by the per-slice identity functions. */
    private suspend fun remoteIdentity(): StudentIdentity {
        val obj = postAuthorized<JsonObject>("whoami") ?: return StudentIdentity()
        return runCatching { com.amazecc.app.shared.state.IdentityExtractor.fromWhoami(obj) }
            .getOrDefault(StudentIdentity())
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

        // D-101: /api/student is not mounted; the profile is part of /api/whoami.
        try {
            val id = remoteIdentity()
            if (id.hasIdentity) return StudentProfileRes(success = true, data = studentProfileFromIdentity(id))
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
        // D-101: folded into /api/whoami.
        return try {
            val id = remoteIdentity()
            if (id.hasIdentity) profileImagesFromIdentity(id)
            else ProfileImagesRes(success = false, error = "Profile images unavailable")
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
        // D-101: folded into /api/whoami.
        return try {
            val id = remoteIdentity()
            if (id.hasIdentity || id.credentials.isNotEmpty()) credentialsFromIdentity(id)
            else CredentialsRes(success = false, error = "Credentials unavailable")
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
        // D-101: folded into /api/whoami.
        return try {
            eptScheduleFromIdentity(remoteIdentity())
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
            // D-100: /api/registration-schedule is not mounted; the proxy is in /api/bulk.
            val element = bulkResults("registration-schedule")["registration-schedule"]
            if (element is JsonObject) {
                jsonConfig.decodeFromJsonElement<RegistrationScheduleRes>(element)
            } else {
                RegistrationScheduleRes(success = false, error = "Registration schedule unavailable")
            }
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
        // D-101: folded into /api/whoami.
        return try {
            universityDayFromIdentity(remoteIdentity())
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
        // D-101: folded into /api/whoami.
        return try {
            bankInfoFromIdentity(remoteIdentity())
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
        // D-101: folded into /api/whoami.
        return try {
            dayboarderFromIdentity(remoteIdentity())
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
        // D-101: folded into /api/whoami.
        return try {
            apaarIdFromIdentity(remoteIdentity())
        } catch (e: Exception) { ApaarIdRes(success = false, error = e.toString()) }
    }

    /**
     * Consolidated student-profile fetch across all three VTOP sources.
     *
     * The canonical route is `POST /api/remote/whoami` (legacy alias
     * `/api/whoami`, which is what [postAuthorized] reaches with endpoint
     * "whoami"). The response is FLAT — the identity is spread at the top level
     * rather than nested under an `identity` key — so it is deciphered with
     * [com.amazecc.app.shared.state.IdentityExtractor.fromWhoami].
     *
     *  - REMOTE: one authorized POST, mapped via the extractor.
     *  - OFFLINE_CACHE: restore the encrypted cached identity via UserStore.
     *  - LOCAL: warm the on-device scraper, merge its folded fragments, and fall
     *    back to the cached identity if the session cannot serve them.
     *  - Demo: report failure so the caller keeps the per-endpoint demo sweep.
     */
    suspend fun getMe(): MeRes = getMeWith(
        remoteFetch = { postAuthorized<JsonObject>("whoami") },
        restoreCache = {
            com.amazecc.app.shared.state.UserStore.loadFromCache()
            com.amazecc.app.shared.state.UserStore.identity.value
        },
        cachedIdentity = { com.amazecc.app.shared.state.UserStore.identity.value },
        localFetch = { fetchWhoamiLocally() }
    )

    /**
     * Test seam for [getMe]: every I/O path is injected so headless JVM tests can
     * exercise the flat-payload mapping and each source branch without a network,
     * WebView session or encrypted cache.
     */
    suspend fun getMeWith(
        remoteFetch: suspend () -> JsonObject?,
        restoreCache: () -> StudentIdentity,
        cachedIdentity: () -> StudentIdentity,
        localFetch: suspend () -> StudentIdentity?
    ): MeRes {
        if (useMockData) return MeRes(success = false, error = "demo mode")
        return when (vtopSource) {
            com.amazecc.app.shared.vtop.VtopSource.OFFLINE_CACHE -> {
                val restored = restoreCache()
                if (restored.hasIdentity) MeRes(success = true, identity = restored)
                else MeRes(success = false, error = "No cached identity")
            }
            com.amazecc.app.shared.vtop.VtopSource.LOCAL -> {
                val local = try { localFetch() } catch (e: Exception) { null }
                if (local != null && local.hasIdentity) {
                    MeRes(success = true, identity = local)
                } else {
                    val cached = cachedIdentity()
                    if (cached.hasIdentity) {
                        MeRes(success = true, identity = cached, error = "Local scrape unavailable; served cached identity")
                    } else {
                        MeRes(success = false, error = "Local identity unavailable")
                    }
                }
            }
            else -> {
                val obj = try { remoteFetch() } catch (e: Exception) { null }
                    ?: return MeRes(success = false, error = "Empty response")
                val res = try { jsonConfig.decodeFromJsonElement<WhoamiRes>(obj) } catch (e: Exception) { null }
                    ?: return MeRes(success = false, error = "Malformed whoami response")
                val identity = com.amazecc.app.shared.state.IdentityExtractor.fromWhoami(obj)
                // HTTP 200 may still report success=false with partial fetchErrors.
                // Folded fields that did arrive are still usable, so surface them.
                val ok = identity.hasIdentity || res.success
                val warning = res.error
                    ?: res.fetchErrors?.takeIf { it.isNotEmpty() }
                        ?.let { "Partial whoami data: ${it.keys.sorted().joinToString(", ")}" }
                MeRes(success = ok, identity = identity, error = if (ok) warning else (warning ?: "Empty whoami"))
            }
        }
    }

    /**
     * LOCAL implementation of the consolidated sweep. Warms the on-device scraper
     * once, then folds each per-page scrape into one fragment via the same
     * extractors and tiers used by the REMOTE identity's sources. Returns null
     * when no session/page is available so [getMeWith] can use the cached copy.
     */
    private suspend fun fetchWhoamiLocally(): StudentIdentity? {
        if (!com.amazecc.app.shared.vtop.vtopEngineSupported ||
            !com.amazecc.app.shared.vtop.VtopSession.hasSession
        ) return null
        try { com.amazecc.app.shared.vtop.VtopDataSource.fetchWhoami() } catch (_: Exception) {}

        val ex = com.amazecc.app.shared.state.IdentityExtractor
        val sources = mutableMapOf<String, Int>()
        // Seed the accumulator with the identity we already know (store + session) so a
        // partial local scrape keeps previously known fields instead of sealing a thin
        // regNo-only identity at the ME tier. Fresh scraped fields still overwrite: the
        // per-path source bookkeeping starts empty, so every scraper tier can write.
        var acc = com.amazecc.app.shared.state.UserStore.identity.value
        com.amazecc.app.shared.vtop.VtopSession.authorizedID.value
            ?.takeIf { it.isNotBlank() }
            ?.let { id ->
                acc = com.amazecc.app.shared.state.mergeIdentity(
                    acc,
                    ex.fromSession(id),
                    com.amazecc.app.shared.state.IdentitySource.SESSION.order,
                    sources
                )
            }
        fun absorb(fragment: StudentIdentity, order: Int) {
            acc = com.amazecc.app.shared.state.mergeIdentity(acc, fragment, order, sources)
        }
        try {
            com.amazecc.app.shared.vtop.VtopDataSource.fetchStudentProfile()
                ?.let { absorb(ex.fromStudentProfile(it), com.amazecc.app.shared.state.IdentitySource.STUDENT.order) }
        } catch (_: Exception) {}
        try {
            com.amazecc.app.shared.vtop.VtopDataSource.fetchProfileImages()
                ?.let { absorb(ex.fromProfileImages(it), com.amazecc.app.shared.state.IdentitySource.PROFILE_IMAGES.order) }
        } catch (_: Exception) {}
        try {
            com.amazecc.app.shared.vtop.VtopDataSource.fetchCredentials()
                ?.let { absorb(ex.fromCredentials(it), com.amazecc.app.shared.state.IdentitySource.CREDENTIALS.order) }
        } catch (_: Exception) {}
        try {
            com.amazecc.app.shared.vtop.VtopDataSource.fetchBankInfo()
                ?.let { absorb(ex.fromBankInfo(it), com.amazecc.app.shared.state.IdentitySource.BANK.order) }
        } catch (_: Exception) {}
        try {
            com.amazecc.app.shared.vtop.VtopDataSource.fetchApaarId()
                ?.let { absorb(ex.fromApaarId(it), com.amazecc.app.shared.state.IdentitySource.APAAR.order) }
        } catch (_: Exception) {}
        try {
            com.amazecc.app.shared.vtop.VtopDataSource.fetchDayboarder()
                ?.let { absorb(ex.fromDayboarder(it), com.amazecc.app.shared.state.IdentitySource.RECORDS.order) }
        } catch (_: Exception) {}
        try {
            com.amazecc.app.shared.vtop.VtopDataSource.fetchEptSchedule()
                ?.let { absorb(ex.fromEptSchedule(it), com.amazecc.app.shared.state.IdentitySource.RECORDS.order) }
        } catch (_: Exception) {}

        if (acc.residenceStatus == null) {
            val derived = ex.deriveResidenceStatus(acc.isHosteller, acc.dayboarder?.fields.orEmpty())
            acc = acc.copy(
                residenceStatus = derived,
                isHosteller = when (derived) {
                    RESIDENCE_HOSTELLER -> true
                    RESIDENCE_DAYSCHOLAR -> false
                    else -> acc.isHosteller
                }
            )
        }
        return acc.takeIf { it.hasIdentity }
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
                    ?: com.amazecc.app.shared.state.AppDataStore.circulars.value
                    ?: CircularsRes(success = false, error = "VTOP circulars unavailable")
            } catch (e: Exception) {
                com.amazecc.app.shared.state.AppDataStore.circulars.value
                    ?: CircularsRes(success = false, error = e.toString())
            }
        }
        return try {
            val res = postAuthorizedRemote<CircularsRes>("circulars")
            if (res != null && res.success) {
                com.amazecc.app.shared.state.AppDataStore.setCirculars(res)
                res
            } else {
                com.amazecc.app.shared.state.AppDataStore.circulars.value ?: res ?: CircularsRes(success = false, message = "Empty response")
            }
        } catch (e: Exception) {
            com.amazecc.app.shared.state.AppDataStore.circulars.value
                ?: CircularsRes(success = false, message = e.message, error = e.toString())
        }
    }

    // ── Document / payment detail actions ──────────────────────────────────

    /**
     * Downloads the binary behind a circular (or any document whose [circularId] is the
     * `viewCertificate` id VTOP emitted in the circular tree).
     *
     * REMOTE: `POST /api/remote/circulars/download` with the authorized session, returning the raw PDF
     * with its `Content-Type` / `Content-Disposition` headers intact.
     * LOCAL: the on-device scraper walks the same `PATH_CIRCULAR_DOWNLOAD` VTOP path.
     * OFFLINE_CACHE: there is no cached binary, so this returns null rather than inventing one.
     *
     * Failure at any stage yields null; no placeholder bytes are ever manufactured.
     */
    suspend fun downloadCircular(circularId: String): CircularDownloadResult? {
        if (circularId.isBlank()) return null
        if (useMockData) return null
        if (vtopSource == com.amazecc.app.shared.vtop.VtopSource.OFFLINE_CACHE) return null

        if (vtopSource == com.amazecc.app.shared.vtop.VtopSource.LOCAL) {
            return try {
                com.amazecc.app.shared.vtop.VtopDataSource.downloadCircular(circularId)
            } catch (_: Exception) {
                null
            }
        }

        // REMOTE — mirror getSyllabusPdf's session handling and raw-binary read.
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
        if (!SessionManager.hasRemoteCookies || cookies == null || authorizedID == null || csrf == null) return null

        return try {
            val response: HttpResponse = httpClient.post("$baseUrl/api/remote/circulars/download") {
                contentType(ContentType.Application.Json)
                setBody(buildJsonObject {
                    put("cookies", cookies)
                    put("authorizedID", authorizedID)
                    put("csrf", csrf)
                    put("circularId", circularId)
                })
            }
            if (response.status != HttpStatusCode.OK) return null
            val contentTypeHeader = response.headers["Content-Type"].orEmpty()
            // A 200 carrying JSON is the proxy's error envelope, not a document.
            if (contentTypeHeader.contains("json", ignoreCase = true)) return null
            val bytes = response.body<ByteArray>()
            if (bytes.isEmpty()) return null
            val mimeType = contentTypeHeader.substringBefore(';').trim()
                .ifBlank { "application/pdf" }
            val fileName = circularDownloadFileName(response.headers["Content-Disposition"])
                ?: "$circularId.pdf"
            CircularDownloadResult(bytes = bytes, fileName = fileName, mimeType = mimeType)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Detail tables for one payment receipt.
     *
     * REMOTE: `POST /api/finance` with `section="receipt"` and the receipt's [applNo]; the route
     * returns `{ success, tables: [{ headers, rows: [{header: value}] }] }` which is normalised into
     * the app's [ArrearResponse] shape (rows aligned to their headers).
     * LOCAL: there is no dedicated on-device receipt-detail scraper — the payments scraper only
     * produces the receipts *list* — so this returns null rather than fabricating table cells.
     * OFFLINE_CACHE: null; no receipt detail is cached as a binary/derived view.
     */
    suspend fun getReceiptDetail(applNo: String): ArrearResponse? {
        if (applNo.isBlank()) return null
        if (useMockData) return null
        if (vtopSource == com.amazecc.app.shared.vtop.VtopSource.OFFLINE_CACHE) return null
        if (vtopSource == com.amazecc.app.shared.vtop.VtopSource.LOCAL) return null

        val raw = postAuthorized<JsonObject>(
            "finance",
            mapOf("section" to "receipt", "applNo" to applNo)
        ) ?: return null
        return receiptResponseFromJson(raw)
    }


    // â”€â”€ Phase 3 endpoints â”€â”€

    suspend fun postQBankPaper(courseCode: String, title: String, link: String, type: String): QBankSubmitRes? {
if (useMockData) return DemoData.get("qbankUpload", QBankSubmitRes.serializer()) ?: QBankSubmitRes(true)
        // D-103: the guarded upload route expects `fileUrl` and optional `paperType`, not
        // `link`/`type`. postAuthorized now also attaches the Amaze ID bearer (D-011).
        return postAuthorized("qbank/upload", mapOf(
            "courseCode" to courseCode,
            "title" to title,
            "fileUrl" to link,
            "paperType" to type
        ))
    }

    suspend fun getQBankCourses(): QBankCoursesRes {
if (useMockData) return DemoData.get("qbankCourses", QBankCoursesRes.serializer()) ?: QBankCoursesRes()
        return try {
            // D-027: decode both `{data:[{code,title}]}` (API) and `{courses:[...]}` (demo) via
            // the tolerant DTO instead of hand-picking keys.
            val response: HttpResponse = httpClient.get("$baseUrl/api/qbank/courses")
            if (response.status.value in 200..299) {
                val decoded = jsonConfig.decodeFromString<QBankCoursesRes>(response.bodyAsText())
                decoded.copy(courses = decoded.effectiveCourses)
            } else {
                QBankCoursesRes(success = false, message = "HTTP ${response.status}")
            }
        } catch (e: Exception) {
            QBankCoursesRes(success = false, message = e.message, error = e.toString())
        }
    }

    suspend fun getFacultySchools(): FacultySchoolsRes {
if (useMockData) return DemoData.get("facultySchools", FacultySchoolsRes.serializer()) ?: FacultySchoolsRes()
        // D-096: live only under /api/services/faculty-routes.
        return try {
            val response: HttpResponse = httpClient.get("$baseUrl/api/services/faculty/schools")
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
        // D-096: live only under /api/services/faculty-routes.
        return try {
            val response: HttpResponse = httpClient.post("$baseUrl/api/services/faculty/scrape") {
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

    data class VitfacCatalogEntry(
        val id: String,
        val employeeId: String,
        val name: String,
        val designation: String = "",
        val dept: String = "",
        val wPct: Int? = null,
        val reviews: Int = 0,
        val email: String = "",
        val intercom: String = "",
        val imageUrl: String? = null
    )

    private var cachedVitfacCatalog: List<VitfacCatalogEntry>? = null

    fun getFacultyCdnUrl(employeeId: String?): String? {
        val trimmed = employeeId?.trim().orEmpty()
        if (trimmed.isBlank()) return null
        return "https://cdn.amazecc.com/faculty/$trimmed.webp"
    }

    data class VitfacLoreTag(val text: String, val count: Int)
    data class VitfacCourseStat(
        val code: String,
        val name: String = "",
        val reviews: Int = 0,
        val wPct: Int = 0,
        val lecture: Double? = null,
        val da: Double? = null,
        val vibe: Double? = null,
        val greenLore: List<VitfacLoreTag> = emptyList(),
        val redLore: List<VitfacLoreTag> = emptyList()
    )
    data class VitfacProfileDetail(
        val employeeId: String,
        val b64Id: String = "",
        val name: String,
        val designation: String = "",
        val dept: String = "",
        val wPct: Int?,
        val reviews: Int,
        val wCount: Int = 0,
        val lCount: Int = 0,
        val lecture: Double? = null,
        val da: Double? = null,
        val vibe: Double? = null,
        val greenLore: List<VitfacLoreTag> = emptyList(),
        val redLore: List<VitfacLoreTag> = emptyList(),
        val knownCourses: List<String> = emptyList(),
        val courseStats: List<VitfacCourseStat> = emptyList(),
        val globalReviews: Int? = null,
        val globalWPct: Int? = null
    )

    private const val VITFAC_CATALOG_CACHE_KEY = "cache_vitfac_catalog_v1"

    private fun parseVitfacCatalogJson(body: String): List<VitfacCatalogEntry> {
        if (body.isBlank()) return emptyList()
        return try {
            val root = jsonConfig.parseToJsonElement(body)
            val arr = when (root) {
                is JsonArray -> root
                is JsonObject -> {
                    val dataEl = root["data"]
                    when (dataEl) {
                        is JsonArray -> dataEl
                        is JsonObject -> (dataEl["faculty"] as? JsonArray) ?: JsonArray(emptyList())
                        else -> (root["faculty"] as? JsonArray) ?: JsonArray(emptyList())
                    }
                }
                else -> JsonArray(emptyList())
            }
            arr.mapNotNull { el ->
                val obj = el as? JsonObject ?: return@mapNotNull null
                fun str(key: String): String =
                    (obj[key] as? JsonPrimitive)?.takeUnless { it.content == "null" && !it.isString }?.content?.trim().orEmpty()
                val name = str("name")
                if (name.isBlank()) return@mapNotNull null
                val empId = str("employeeId")
                val id = str("id").ifBlank { empId }
                val rawImg = str("image_url").ifBlank { str("imageUrl") }.ifBlank { null }
                val cdnImg = getFacultyCdnUrl(empId) ?: rawImg
                VitfacCatalogEntry(
                    id = id,
                    employeeId = empId,
                    name = name,
                    designation = str("designation"),
                    dept = str("dept"),
                    wPct = str("wPct").toDoubleOrNull()?.toInt(),
                    reviews = str("reviews").toIntOrNull() ?: 0,
                    email = str("email"),
                    intercom = str("intercom"),
                    imageUrl = cdnImg
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun getCachedVitfacCatalog(): List<VitfacCatalogEntry> {
        cachedVitfacCatalog?.let { if (it.isNotEmpty()) return it }
        val raw = SettingsManager.getString(VITFAC_CATALOG_CACHE_KEY, "")
        val parsed = parseVitfacCatalogJson(raw)
        if (parsed.isNotEmpty()) cachedVitfacCatalog = parsed
        return parsed
    }

    suspend fun getVitfacCatalog(forceRefresh: Boolean = false): List<VitfacCatalogEntry> {
        if (!forceRefresh) {
            val cached = getCachedVitfacCatalog()
            if (cached.isNotEmpty()) return cached
        }
        return try {
            val response: HttpResponse = httpClient.get("https://cache.amazecc.com/faculty")
            if (response.status != HttpStatusCode.OK) return getCachedVitfacCatalog()
            val body = response.bodyAsText()
            val parsed = parseVitfacCatalogJson(body)
            if (parsed.isNotEmpty()) {
                cachedVitfacCatalog = parsed
                SettingsManager.setString(VITFAC_CATALOG_CACHE_KEY, body)
            }
            parsed
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            getCachedVitfacCatalog()
        }
    }

    @OptIn(kotlin.io.encoding.ExperimentalEncodingApi::class)
    suspend fun getVitfacProfile(employeeId: String): VitfacProfileDetail? {
        val trimmed = employeeId.trim()
        if (trimmed.isBlank()) return null
        val b64 = kotlin.io.encoding.Base64.encode(trimmed.encodeToByteArray())
        return try {
            val response: HttpResponse = httpClient.get("https://cache.amazecc.com/profile/$b64")
            if (response.status != HttpStatusCode.OK) return null
            val root = jsonConfig.parseToJsonElement(response.bodyAsText()) as? JsonObject ?: return null
            val data = root["data"] as? JsonObject ?: root
            val prof = data["prof"] as? JsonObject ?: data
            fun str(obj: JsonObject?, key: String): String =
                (obj?.get(key) as? JsonPrimitive)?.takeUnless { it.content == "null" && !it.isString }?.content?.trim().orEmpty()
            val metricsObj = prof["metrics"] as? JsonObject
            val loreObj = prof["lore"] as? JsonObject
            fun parseLore(loreObj: JsonObject?, key: String): List<VitfacLoreTag> {
                val arr = loreObj?.get(key) as? JsonArray ?: return emptyList()
                return arr.mapNotNull { item ->
                    val o = item as? JsonObject ?: return@mapNotNull null
                    val text = str(o, "text")
                    if (text.isBlank()) return@mapNotNull null
                    VitfacLoreTag(text = text, count = str(o, "count").toIntOrNull() ?: 1)
                }
            }
            val courseStatsObj = data["courseStats"] as? JsonObject
            val globalObj = courseStatsObj?.get("global") as? JsonObject
            val globalMetrics = globalObj?.get("metrics") as? JsonObject ?: metricsObj
            val globalLore = globalObj?.get("lore") as? JsonObject ?: loreObj

            val perCourses = (courseStatsObj?.get("courses") as? JsonArray)?.mapNotNull { el ->
                val cObj = el as? JsonObject ?: return@mapNotNull null
                val code = str(cObj, "code")
                if (code.isBlank()) return@mapNotNull null
                val cm = cObj["metrics"] as? JsonObject
                val cl = cObj["lore"] as? JsonObject
                VitfacCourseStat(
                    code = code,
                    name = str(cObj, "name"),
                    reviews = str(cObj, "reviews").toIntOrNull() ?: 0,
                    wPct = str(cObj, "wPct").toDoubleOrNull()?.toInt() ?: 0,
                    lecture = str(cm, "lecture").toDoubleOrNull(),
                    da = str(cm, "da").toDoubleOrNull(),
                    vibe = str(cm, "vibe").toDoubleOrNull(),
                    greenLore = parseLore(cl, "green"),
                    redLore = parseLore(cl, "red")
                )
            }.orEmpty()

            val knownCourses = (prof["courses"] as? JsonArray)?.mapNotNull {
                (it as? JsonPrimitive)?.content?.trim()?.takeIf { s -> s.isNotBlank() && s != "null" }
            }.orEmpty()

            VitfacProfileDetail(
                employeeId = trimmed,
                b64Id = str(prof, "id").ifBlank { b64 },
                name = str(prof, "name"),
                designation = str(prof, "designation"),
                dept = str(prof, "dept"),
                wPct = str(prof, "wPct").toDoubleOrNull()?.toInt(),
                reviews = str(prof, "reviews").toIntOrNull() ?: 0,
                wCount = str(prof, "wCount").toIntOrNull() ?: 0,
                lCount = str(prof, "lCount").toIntOrNull() ?: 0,
                lecture = str(globalMetrics, "lecture").toDoubleOrNull(),
                da = str(globalMetrics, "da").toDoubleOrNull(),
                vibe = str(globalMetrics, "vibe").toDoubleOrNull(),
                greenLore = parseLore(globalLore, "green"),
                redLore = parseLore(globalLore, "red"),
                knownCourses = knownCourses,
                courseStats = perCourses,
                globalReviews = str(globalObj, "reviews").toIntOrNull(),
                globalWPct = str(globalObj, "wPct").toDoubleOrNull()?.toInt()
            )
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            null
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

    suspend fun getProjects(): ArrearResponse {
if (useMockData) return DemoData.get("projects", ArrearResponse.serializer()) ?: ArrearResponse()
        // D-100: no /api/project route; the whitelisted bulk proxies are hostel-counselling,
        // registration-schedule, exc-registration and course-completion only.
        return ArrearResponse(success = false, message = "Project details are unavailable on the current API")
    }

    suspend fun getWishlist(): ArrearResponse {
if (useMockData) return DemoData.get("wishlist", ArrearResponse.serializer()) ?: ArrearResponse()
        // D-100: wishlist lives under POST /api/ffcs section=wishlist.
        return try {
            val ffcs = postAuthorized<JsonObject>("ffcs", ffcsContract("wishlist").bodyFields)
            val element = ffcs?.get("wishlist")
            if (element is JsonObject) arrearFromBulkResult(element)
            else ArrearResponse(success = false, message = "Wishlist unavailable")
        } catch (e: Exception) {
            ArrearResponse(success = false, message = e.message, error = e.toString())
        }
    }

    suspend fun getFeedbackStatus(semesterId: String? = null): FeedbackStatusRes {
        if (useMockData) return DemoData.get("feedback", FeedbackStatusRes.serializer()) ?: FeedbackStatusRes()
        if (vtopSource == com.amazecc.app.shared.vtop.VtopSource.OFFLINE_CACHE) {
            AppState.markServedFromCache("Feedback Status")
            return AppDataStore.feedbackStatus.value
                ?: FeedbackStatusRes(success = true)
        }
        if (vtopSource == com.amazecc.app.shared.vtop.VtopSource.LOCAL && semesterId != null) {
            return try {
                com.amazecc.app.shared.vtop.VtopDataSource.fetchFeedbackStatus(semesterId)
                    ?.also { AppDataStore.setFeedbackStatus(it) }
                    ?: FeedbackStatusRes(success = false, error = "VTOP feedback status unavailable")
            } catch (e: Exception) {
                FeedbackStatusRes(success = false, error = e.message ?: e.toString())
            }
        }
        return try {
            // D-105: canonical route is POST /api/ffcs with section=feedback;
            // /api/feedback-status is not mounted.
            if (semesterId != null) {
                val ffcs = postAuthorized<FfcsFeedbackResponse>(
                    "ffcs", ffcsContract("feedback", semesterId).bodyFields
                )
                val detailed = ffcs?.feedbackStatus
                if (ffcs?.success == true && detailed != null && detailed.success) {
                    AppDataStore.setFeedbackStatus(detailed)
                    return detailed
                }
            }
            if (com.amazecc.app.shared.vtop.vtopEngineSupported && com.amazecc.app.shared.vtop.VtopSession.hasSession && semesterId != null) {
                try {
                    val local = com.amazecc.app.shared.vtop.VtopDataSource.fetchFeedbackStatus(semesterId)
                    if (local != null && local.success) return local
                } catch (_: Exception) {}
            }
            FeedbackStatusRes(success = false, error = "Feedback status unavailable")
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
            // D-099: /api/grades returns {success, overall, semesters}, not {effectiveGrades}.
            // Decode the canonical shape and project the requested semester into the existing
            // SemesterGradesRes contract so callers keep compiling.
            val params = if (semesterId != null) mapOf("semesterId" to semesterId) else emptyMap()
            val all = postAuthorized<AllGradesRes>("grades", params)
            val converted = all?.let { semesterGradesFromAll(semesterId, it) }
            if (converted != null && converted.success) converted
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
        // D-100: no mounted route; request through VTOP Other Services.
        return ArrearResponse(success = false, message = "Bonafide is available through VTOP Other Services")
    }

    suspend fun getETranscript(): ArrearResponse {
if (useMockData) return DemoData.get("eTranscript", ArrearResponse.serializer()) ?: ArrearResponse()
        // D-100: no mounted route; request through VTOP Examinations.
        return ArrearResponse(success = false, message = "E-Transcript is available through VTOP Examinations")
    }

    suspend fun getAdditionalLearning(): ArrearResponse {
if (useMockData) return DemoData.get("additionalLearning", ArrearResponse.serializer()) ?: ArrearResponse()
        // D-100: closest whitelisted proxy is `course-completion` in /api/bulk.
        return try {
            val element = bulkResults("course-completion")["course-completion"]
            if (element != null) arrearFromBulkResult(element)
            else ArrearResponse(success = false, message = "Additional learning unavailable")
        } catch (e: Exception) {
            ArrearResponse(success = false, message = e.message, error = e.toString())
        }
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
     * Test seam for D-109: when non-null, [login] returns this response instead of performing a
     * network/local VTOP login, while still running the credential-persistence step on success.
     * Lets a JVM test drive the "login response -> credentials available" contract offline.
     * Null in production. Set/cleared only by login-persistence tests.
     */
    internal var loginResponseOverride: (suspend (username: String, password: String) -> LoginResponse)? = null

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

    // ── Declarative mount routing (D-015) ────────────────────────────────────────

    /**
     * Resolve the ordered mounts for one operation and try each in turn.
     *
     * Only a 5xx or a transport failure moves to the next mount — a 4xx (including 401) is a
     * final answer, the same rule [kohaWithFailover] uses (D-014). [auth] both selects the
     * credential strategy (VTOP body credentials vs Amaze ID Authorization header) and filters
     * which mounts are eligible via [com.amazecc.app.shared.domain.ApiMounts.resolve].
     */
    internal suspend fun <T> mountedPost(
        op: String,
        auth: com.amazecc.app.shared.domain.ApiAuth,
        body: () -> JsonObject?,
        decode: (String) -> T?,
        /** D-192: public cache routes must keep the web's cache-first order; other ops unchanged. */
        preferCache: Boolean = false,
    ): T? {
        val target = com.amazecc.app.shared.domain.ApiTarget.fromName(SettingsManager.getApiTargetName())
        val candidates = com.amazecc.app.shared.domain.ApiMounts.resolve(
            op = op,
            auth = auth,
            preferredId = if (preferCache) null else target.mountId,
            customBaseUrl = if (!preferCache && target == com.amazecc.app.shared.domain.ApiTarget.CUSTOM) baseUrl else null,
        )
        for (candidate in candidates) {
            // No VTOP session means this auth strategy cannot be honoured by any mount.
            val payload = if (candidate.method == "GET") null else (body() ?: continue)
            val status = try {
                val response: HttpResponse = httpClient.request(candidate.url) {
                    method = if (candidate.method == "GET") HttpMethod.Get else HttpMethod.Post
                    contentType(ContentType.Application.Json)
                    if (auth == com.amazecc.app.shared.domain.ApiAuth.AMAZE_ID) attachAmazeIdAuth()
                    if (payload != null) setBody(payload.toString())
                }
                val code = response.status.value
                if (code == 401) markAmazeAuthRequired(response.status)
                if (code in 200..299) {
                    val text = response.bodyAsText()
                    if (text.isNotBlank()) decode(text)?.let { return it }
                }
                code
            } catch (_: Exception) {
                0
            }
            if (!kohaShouldFailOver(status, false)) break
        }
        return null
    }

    /** VTOP credentials in the body for service ops that use the classic session contract. */
    private fun vtopServiceBody(extra: JsonObjectBuilder.() -> Unit): JsonObject? {
        if (vtopSource != com.amazecc.app.shared.vtop.VtopSource.REMOTE) return null
        if (!SessionManager.hasRemoteCookies) return null
        val cookies = SessionManager.cookies.value ?: return null
        val authorizedID = SessionManager.authorizedID.value ?: return null
        val csrf = SessionManager.csrf.value ?: return null
        return buildJsonObject {
            put("cookies", cookies)
            put("authorizedID", authorizedID)
            put("csrf", csrf)
            extra()
        }
    }

    // ── D-108 service endpoints, now typed through the mount list (D-012/D-015) ──

    suspend fun getServiceEnroll(semesterId: String? = null): JsonObject? {
        if (useMockData || vtopSource != com.amazecc.app.shared.vtop.VtopSource.REMOTE) return null
        return mountedPost(
            op = "enroll",
            auth = com.amazecc.app.shared.domain.ApiAuth.VTOP,
            body = { vtopServiceBody { semesterId?.let { put("semesterId", it) } } },
            decode = { runCatching { jsonConfig.decodeFromString<JsonObject>(it) }.getOrNull() },
        )
    }

    /**
     * D-108: `/api/services/curves` filters by `classId` (or batch `classIds`) — it has no
     * `courseCode` field. The legacy parameter name is kept for source compatibility but is sent
     * as `classId` so the request is actually accepted.
     */
    suspend fun getServiceCurves(courseCode: String, semesterId: String? = null): CurvesEnvelopeDto? {
        if (useMockData || vtopSource != com.amazecc.app.shared.vtop.VtopSource.REMOTE) return null
        val useAmazeId = SessionManager.hasAmazeIdAuth
        return mountedPost(
            op = "curves",
            auth = if (useAmazeId) com.amazecc.app.shared.domain.ApiAuth.AMAZE_ID
                   else com.amazecc.app.shared.domain.ApiAuth.VTOP,
            body = authBody@ {
                val creds = (if (useAmazeId) JsonObject(emptyMap()) else vtopServiceBody { })
                    ?: return@authBody null
                buildJsonObject {
                    creds.forEach { (k, v) -> put(k, v) }
                    put("classId", courseCode)
                    semesterId?.let { put("semesterId", it) }
                }
            },
            decode = { runCatching { jsonConfig.decodeFromString<CurvesEnvelopeDto>(it) }.getOrNull() },
        )
    }

    /** Batch variant using the API's `classIds: string[]` field (max 100 ids server-side). */
    suspend fun getServiceCurvesByClassIds(
        classIds: List<String>,
        semesterId: String? = null,
    ): CurvesEnvelopeDto? {
        if (useMockData || vtopSource != com.amazecc.app.shared.vtop.VtopSource.REMOTE) return null
        val useAmazeId = SessionManager.hasAmazeIdAuth
        return mountedPost(
            op = "curves",
            auth = if (useAmazeId) com.amazecc.app.shared.domain.ApiAuth.AMAZE_ID
                   else com.amazecc.app.shared.domain.ApiAuth.VTOP,
            body = authBody@ {
                val creds = (if (useAmazeId) JsonObject(emptyMap()) else vtopServiceBody { })
                    ?: return@authBody null
                buildJsonObject {
                    creds.forEach { (k, v) -> put(k, v) }
                    put("classIds", buildJsonArray { classIds.take(100).forEach { add(JsonPrimitive(it)) } })
                    semesterId?.let { put("semesterId", it) }
                }
            },
            decode = { runCatching { jsonConfig.decodeFromString<CurvesEnvelopeDto>(it) }.getOrNull() },
        )
    }

    /** `POST /api/services/marks/tokens` — the server-held HMAC presence tokens. */
    suspend fun getMarksTokens(semesterId: String? = null): MarksTokensEnvelopeDto? {
        if (useMockData || vtopSource != com.amazecc.app.shared.vtop.VtopSource.REMOTE) return null
        return mountedPost(
            op = "marks/tokens",
            auth = com.amazecc.app.shared.domain.ApiAuth.VTOP,
            body = { vtopServiceBody { semesterId?.let { put("semesterId", it) } } },
            decode = { runCatching { jsonConfig.decodeFromString<MarksTokensEnvelopeDto>(it) }.getOrNull() },
        )
    }

    suspend fun getCoursePage(courseCode: String, semesterId: String? = null): JsonObject? {
        if (useMockData || vtopSource != com.amazecc.app.shared.vtop.VtopSource.REMOTE) return null
        return try {
            val params = buildMap {
                put("courseCode", courseCode)
                semesterId?.let { put("semesterId", it) }
            }
            postAuthorized<JsonObject>("course-page", params)
        } catch (_: Exception) { null }
    }

    suspend fun getVitolData(section: String): JsonObject? {
        if (useMockData || vtopSource != com.amazecc.app.shared.vtop.VtopSource.REMOTE) return null
        return try {
            postAuthorized<JsonObject>("vitol-data", mapOf("section" to section))
        } catch (_: Exception) { null }
    }

    suspend fun checkForUpdate(): GitHubRelease {
        val url = "https://api.github.com/repos/${UpdateConfig.GITHUB_OWNER}/${UpdateConfig.GITHUB_REPO}/releases/latest"
        return httpClient.get(url).body()
    }

    /**
     * Pulls cohort statistics directly from the UniCC public backend API:
     * `https://api-unicc.arya22.dev/api/attendance/marks?classId=$classId`
     *
     * Returns real class statistics: mean (out of 60.0), standard deviation, and student contributor count.
     */
    suspend fun fetchUniCcClassStats(classId: String): ClassStats? {
        val id = classId.trim()
        if (id.isBlank()) return null
        return try {
            val url = "https://api-unicc.arya22.dev/api/attendance/marks?classId=${id.encodeURLParameter()}"
            val response: HttpResponse = httpClient.get(url)
            if (response.status == HttpStatusCode.OK) {
                val text = response.bodyAsText()
                val dto = jsonConfig.decodeFromString<UniCcClassStatsDto>(text)
                val mean = dto.mean
                if (mean != null && mean.isFinite()) {
                    val count = dto.count ?: 0
                    val sd = dto.sd ?: dto.variance?.let { kotlin.math.sqrt(it) } ?: 0.0
                    ClassStats(count = count, mean = mean, sd = sd, scale = 60.0)
                } else null
            } else null
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Batch concurrent fetch of cohort statistics from UniCC API for multiple class numbers.
     */
    suspend fun fetchUniCcClassStatsBatch(classIds: List<String>): Map<String, ClassStats> = coroutineScope {
        val uniqueIds = classIds.map { it.trim() }.filter { it.isNotBlank() }.distinct()
        if (uniqueIds.isEmpty()) return@coroutineScope emptyMap()
        val deferred = uniqueIds.map { id ->
            async { id to fetchUniCcClassStats(id) }
        }
        deferred.mapNotNull { d ->
            val (id, stats) = d.await()
            if (stats != null) id to stats else null
        }.toMap()
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

/**
 * Pulls the download's file name out of a `Content-Disposition` header.
 *
 * Honours both the quoted `filename="..."` form and the RFC 5987 `filename*=UTF-8''...` form,
 * decoding percent escapes as UTF-8. Returns null when the header carries no usable name so the
 * caller can fall back to `<circularId>.pdf`.
 */
internal fun circularDownloadFileName(contentDisposition: String?): String? {
    if (contentDisposition.isNullOrBlank()) return null

    val encoded = Regex("filename\\*\\s*=\\s*UTF-8''([^;]+)", RegexOption.IGNORE_CASE)
        .find(contentDisposition)?.groupValues?.getOrNull(1)
    if (!encoded.isNullOrBlank()) {
        return percentDecodeUtf8(encoded.trim().trim('"')).takeIf { it.isNotBlank() }
    }

    val quoted = Regex("filename\\s*=\\s*\"([^\"]*)\"", RegexOption.IGNORE_CASE)
        .find(contentDisposition)?.groupValues?.getOrNull(1)
    if (!quoted.isNullOrBlank()) return quoted.trim()

    val plain = Regex("filename\\s*=\\s*([^;]+)", RegexOption.IGNORE_CASE)
        .find(contentDisposition)?.groupValues?.getOrNull(1)
    return plain?.trim()?.trim('"')?.takeIf { it.isNotBlank() }
}

/** Percent-decodes a header value into a UTF-8 string (no platform dependency). */
internal fun percentDecodeUtf8(value: String): String {
    val bytes = mutableListOf<Byte>()
    var i = 0
    while (i < value.length) {
        val c = value[i]
        if (c == '%' && i + 2 < value.length) {
            val hex = value.substring(i + 1, i + 3)
            val byte = hex.toIntOrNull(16)
            if (byte != null) {
                bytes.add(byte.toByte())
                i += 3
                continue
            }
        }
        c.toString().toByteArray().forEach { bytes.add(it) }
        i++
    }
    return bytes.toByteArray().decodeToString()
}

/**
 * Normalises the `{ success, tables }` shape returned by `POST /api/finance?section=receipt`.
 *
 * The API emits each row as a `{ header: value }` object; the app's [ApiTable] stores rows as
 * `List<String>` aligned to [ApiTable.headers], so the values are re-ordered by header here.
 * Returns null unless `success` is true, and drops empty tables — no cells are invented.
 */
internal fun receiptResponseFromJson(raw: JsonObject): ArrearResponse? {
    val success = runCatching { raw["success"]?.jsonPrimitive?.booleanOrNull }.getOrNull() ?: false
    if (!success) return null

    val tablesArray = raw["tables"] as? JsonArray ?: return null
    val tables = tablesArray.mapNotNull { element ->
        val obj = element as? JsonObject ?: return@mapNotNull null
        val headers = (obj["headers"] as? JsonArray).orEmpty()
            .mapNotNull { runCatching { (it as? JsonPrimitive)?.content?.trim() }.getOrNull() }
            .filter { it.isNotEmpty() }
        val rowsArray = obj["rows"] as? JsonArray ?: return@mapNotNull null
        val rows = rowsArray.mapNotNull { rowElement ->
            val rowObj = rowElement as? JsonObject ?: return@mapNotNull null
            val cells = if (headers.isNotEmpty()) {
                headers.map { header ->
                    runCatching { rowObj[header]?.jsonPrimitive?.content?.trim() }.getOrNull().orEmpty()
                }
            } else {
                rowObj.values.mapNotNull {
                    runCatching { (it as? JsonPrimitive)?.content?.trim() }.getOrNull()
                }
            }
            cells.takeIf { it.any { cell -> cell.isNotBlank() } }
        }
        if (rows.isEmpty()) return@mapNotNull null
        ApiTable(
            headers = headers,
            rows = rows,
            title = runCatching { (obj["title"] as? JsonPrimitive)?.content }.getOrNull(),
            caption = runCatching { (obj["caption"] as? JsonPrimitive)?.content }.getOrNull()
        )
    }
    return ArrearResponse(success = true, tables = tables)
}



