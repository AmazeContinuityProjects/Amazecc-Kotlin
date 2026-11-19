package com.amazecc.app.shared.vtop

import com.amazecc.app.shared.model.AssessmentItem
import com.amazecc.app.shared.model.ApaarIdRes
import com.amazecc.app.shared.model.AttendanceItem
import com.amazecc.app.shared.model.AttendanceLog
import com.amazecc.app.shared.model.AttendanceRes
import com.amazecc.app.shared.model.BankInfoRes
import com.amazecc.app.shared.model.CGPAResult
import com.amazecc.app.shared.model.CircularItem
import com.amazecc.app.shared.model.CircularDownloadResult
import com.amazecc.app.shared.model.CircularsRes
import com.amazecc.app.shared.model.CategoryDetail
import com.amazecc.app.shared.model.CurriculumBasket
import com.amazecc.app.shared.model.CurriculumCategory
import com.amazecc.app.shared.model.CurriculumRes
import com.amazecc.app.shared.model.CredentialsRes
import com.amazecc.app.shared.model.ExamItem
import com.amazecc.app.shared.model.ExamScheduleRes
import com.amazecc.app.shared.model.ProfileImagesCredential
import com.amazecc.app.shared.model.ProfileImagesRank
import com.amazecc.app.shared.model.DayboarderRes
import com.amazecc.app.shared.model.EptScheduleRes
import com.amazecc.app.shared.model.HostelDetails
import com.amazecc.app.shared.model.HostelInfo
import com.amazecc.app.shared.model.LeaveItem
import com.amazecc.app.shared.model.MarksCourseItem
import com.amazecc.app.shared.model.MarksRes
import com.amazecc.app.shared.model.PaymentItem
import com.amazecc.app.shared.model.PaymentsRes
import com.amazecc.app.shared.model.ProfileImagesCredentials
import com.amazecc.app.shared.model.ProfileImagesHodDean
import com.amazecc.app.shared.model.ProfileImagesProctor
import com.amazecc.app.shared.model.ProfileImagesRes
import com.amazecc.app.shared.model.QcmViewRes
import com.amazecc.app.shared.model.RegistrationScheduleRes
import com.amazecc.app.shared.model.SemesterGradesRes
import com.amazecc.app.shared.model.StudentProfile
import com.amazecc.app.shared.model.StudentProfileRes
import com.amazecc.app.shared.model.TimetableRes
import com.amazecc.app.shared.model.UniversityDayRes
import com.amazecc.app.shared.state.AcademicMerge
import com.amazecc.app.shared.state.TimetableCourseInfo
import com.amazecc.app.shared.state.AppDataStore
import com.amazecc.app.shared.state.UserStore
import com.amazecc.app.shared.state.IdentitySource
import com.amazecc.app.shared.state.IdentityExtractor
import com.amazecc.app.shared.model.AllGradesRes
import com.amazecc.app.shared.model.GradeItem
import com.amazecc.app.shared.model.SemesterGradeResult
import com.amazecc.app.shared.model.EffectiveGradeCourse
import com.amazecc.app.shared.model.FeedbackSemester
import com.amazecc.app.shared.model.FeedbackStatusRes
import com.amazecc.app.shared.model.FeedbackTableRow
import com.amazecc.app.shared.model.OdDetailsRes
import com.amazecc.app.shared.model.OdRecordItem
import com.amazecc.app.shared.model.TransportDataRes
import com.amazecc.app.shared.repository.SettingsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.put
import kotlin.io.encoding.Base64

/** One course as VTOP's course-info table describes it. */
data class VtopCourse(
    val slNo: String,
    val course: String,
    /** Bare course code, e.g. `BCSE101`. Embedded theory and lab share it. */
    val courseCode: String,
    /** "Lab Only" / "Theory Only" / null — which half of an embedded course this row is. */
    val component: String?,
    val ltpjc: String,
    val category: String,
    val classId: String,
    val slotVenue: String,
    val facultyDetails: String
) {
    /** The 5th whitespace-separated token of LTPJC is the credit count. */
    val credits: String? get() = ltpjc.split(" ").getOrNull(4)

    val isLab: Boolean get() = component == "Lab Only"

    fun toCourseInfo(): TimetableCourseInfo = TimetableCourseInfo(
        slNo = slNo,
        course = course,
        courseCode = courseCode,
        LTPJC = ltpjc,
        category = category,
        classId = classId,
        slotVenue = slotVenue,
        facultyDetails = facultyDetails
    )
}

/**
 * On-device VTOP data access.
 *
 * Replaces the `hono.amazecc.com` proxy for endpoints that scrape VTOP. Extraction happens in
 * the WebView because `commonMain` has no HTML parser; shaping into the app's existing DTOs
 * happens here.
 *
 * Column indices are reproduced verbatim from AmazeCC-API's parsers:
 *  - `src/lib/fetchTimeTable.ts` — 0 slNo, 2 course, 3 LTPJC, 4 category, 6 classId,
 *    7 slotVenue, 8 facultyDetails
 *  - `src/app/api/attendance/route.ts` — 1 course, 2 title, 3 type, 4 slot, 5 faculty,
 *    9 attended, 10 total, 11 percentage, 13 detail link `onclick`
 *
 * VTOP's markup is positional and shifts between pages, so treat these as coupled to the page
 * they came from.
 */
object VtopDataSource {

    private const val PATH_TIME_TABLE = "/vtop/processViewTimeTable"
    private const val PATH_ATTENDANCE = "/vtop/processViewStudentAttendance"
    private const val PATH_ATTENDANCE_DETAIL = "/vtop/processViewAttendanceDetail"
    private const val PATH_MARKS = "/vtop/examinations/doStudentMarkView"
    private const val PATH_CGPA = "/vtop/get/dashboard/current/cgpa/credits"
    private const val PATH_GRADE_HISTORY = "/vtop/examinations/examGradeView/StudentGradeHistory"
    private const val PATH_STUDENT_PROFILE = "/vtop/studentsRecord/StudentProfileAllView"
    private const val PATH_HOSTEL_LEAVE_LANDING = "/vtop/hostels/student/leave/1"
    private const val PATH_HOSTEL_LEAVE_HISTORY = "/vtop/hostels/student/leave/6"
    private const val PATH_HOSTEL_LEAVE_APPLIED = "/vtop/hostels/student/leave/4"
    private const val PATH_PAYMENTS = "/vtop/p2p/Payments"
    private const val PATH_RECEIPTS = "/vtop/p2p/getReceiptsApplno"
    private const val PATH_WALLET = "/vtop/finance/getStudentWallet"

    // Group A
    private const val PATH_APAAR = "/vtop/apaarid/upload"
    private const val PATH_EPT = "/vtop/compre/eptScheduleShow"
    private const val PATH_REGISTRATION = "/vtop/examinations/hostelDetails"
    private const val PATH_UNIVERSITY_DAY = "/vtop/event/uday/certificates"
    private const val PATH_DAYBOARDER = "/vtop/admissions/dayboarderForMenu"

    // Group B
    private const val PATH_BANK_INFO = "/vtop/studentBankInformation/BankInfoStudent"
    private const val PATH_PROCTOR = "/vtop/proctor/viewProctorDetails"

    // Group C
    private const val PATH_EXAM_SCHEDULE = "/vtop/examinations/doSearchExamScheduleForStudent"
    private const val PATH_CIRCULARS = "/vtop/admissions/costCentreCircularsViewPageController"
    private const val PATH_CALENDAR = "/vtop/processViewCalendar"
    private const val PATH_QCM_LOGIN = "/vtop/academics/common/QCMStudentLogin"
    private const val PATH_QCM_DETAIL = "/vtop/getStudentLoginForQcm"

    // Group D
    private const val PATH_CURRICULUM = "/vtop/academics/common/Curriculum"
    private const val PATH_CURRICULUM_CATEGORY = "/vtop/academics/common/curriculumCategoryView"

    // Group E - Parity with amazecc-api-hono /api/remote/*
    private const val PATH_TRANSPORT_REG = "/vtop/transport/transportRegistration"
    private const val PATH_TRANSPORT_TRACK = "/vtop/transport/trackMyBus"
    private const val PATH_OD_WARMUP = "/vtop/academics/common/getStudentOdDetails"
    private const val PATH_OD_SEARCH = "/vtop/searchStudentOdDetails"
    private const val PATH_FEEDBACK_STATUS = "/vtop/processViewFeedBackStatus"
    private const val PATH_WISHLIST = "/vtop/academics/registration/wishlistRegPage"
    private const val PATH_HOD_DEAN = "/vtop/hrms/viewHodDeanDetails"
    private const val PATH_CONTACT = "/vtop/hrms/contactDetails"
    private const val PATH_ACKNOWLEDGEMENT = "/vtop/admissions/AcknowledgmentView"
    private const val PATH_COURSE_PAGE = "/vtop/academics/common/StudentCoursePage"
    private const val PATH_COURSE_PAGE_DETAILS = "/vtop/getCourseDetailsForCoursePage"
    private const val PATH_SYLLABUS_DOWNLOAD = "/vtop/courseSyllabusDownload1"
    private const val PATH_CURRICULUM_DOWNLOAD = "/vtop/academics/curriculDown"
    private const val PATH_CIRCULAR_DOWNLOAD = "/vtop/admissions/viewStatusWiseCostCentreCircularContent"
    private const val PATH_LIBRARY_PAYMENTS = "/vtop/finance/libraryPayments"
    private const val PATH_SEMESTER_GRADES = "/vtop/examinations/examGradeView/doStudentGradeView"

    /** VTOP wants `DD-MMM-YYYY`, e.g. `01-JUL-2026`. */
    private val MONTH_ABBR = listOf(
        "JAN", "FEB", "MAR", "APR", "MAY", "JUN", "JUL", "AUG", "SEP", "OCT", "NOV", "DEC"
    )

    /**
     * The months a semester's calendar spans, derived from the semester code alone.
     * `CH20262701` → `takeLast(2) == "01"`, `drop(2).take(4) == "2026"`.
     */
    fun calendarMonths(semesterId: String): List<Pair<Int, Int>> {
        val semCode = semesterId.takeLast(2)
        val startYear = semesterId.drop(2).take(4).toIntOrNull() ?: 2024
        val nextYear = startYear + 1
        return when (semCode) {
            "01" -> listOf(7 to startYear, 8 to startYear, 9 to startYear, 10 to startYear, 11 to startYear)
            "05" -> listOf(12 to startYear, 1 to nextYear, 2 to nextYear, 3 to nextYear, 4 to nextYear)
            else -> listOf(5 to nextYear, 6 to nextYear, 7 to nextYear)
        }
    }

    /**
     * `processViewCalendar` coerces `classGroupId` by semester: a `05` semester falls back to
     * `ALL` unless the caller already picked an `05` group, and `07` is always `ALL`.
     */
    fun calendarClassGroup(semesterId: String, requested: String): String = when {
        semesterId.endsWith("05") && requested !in setOf("ALL", "ALL02", "ALL05") -> "ALL"
        semesterId.endsWith("07") -> "ALL"
        else -> requested
    }

    /**
     * Raw `[{month, days}]` for a semester's whole calendar. Returned unprocessed so callers can
     * feed it to `AnalyzeCalendar` exactly as they do the REMOTE payload.
     */
    suspend fun fetchCalendarRaw(semesterId: String, classGroupId: String = "ALL"): JsonElement? {
        val body = sessionBody() ?: return null
        val dates = calendarMonths(semesterId).map { (m, y) -> "01-${MONTH_ABBR[m - 1]}-$y" }
        val res = evaluate(
            VtopScripts.fetchCalendar(
                PATH_CALENDAR, body, semesterId, calendarClassGroup(semesterId, classGroupId), dates
            )
        )
        val root = res.rawObject()
        if (!(root["ok"] as? JsonPrimitive)?.content.equals("true", ignoreCase = true)) return null
        return root["calendars"]
    }

    private fun now(): Long = Clock.System.now().toEpochMilliseconds()

    /** Body for the `verifyMenu` family. */
    private fun sessionBody(): String? = VtopSession.verifyMenuParams(now())

    /** The `leave/4` and `leave/6` bodies differ only by their control field. */
    private fun leaveBody(control: String, form: String, extra: String = ""): String? {
        val authorizedID = VtopSession.authorizedID.value?.takeIf { it.isNotBlank() } ?: return null
        val csrf = VtopSession.csrf.value?.takeIf { it.isNotBlank() } ?: return null
        return "history=&authorizedID=$authorizedID&_csrf=$csrf&form=$form" +
            "&control=$control&x=${now()}$extra"
    }

    /**
     * Guarantees a valid, unexpired VTOP session before performing any headless scraping.
     * Delegates to [com.amazecc.app.shared.api.AmazeClient.ensureSession] to ensure
     * consistent token persistence, session lifetime management, and UserStore synchronization.
     *
     * Defaults to [forceFresh] = true, so a direct caller always gets a real VTOP login rather than
     * a cached-fresh session. The per-module scrape helpers ([rows] / [evaluate]) instead pass
     * `forceFresh = false` because the sweep already logged in once — they only verify the tokens
     * that gate established. The one retry path on an expired session keeps the default true so it
     * genuinely re-logs in.
     */
    suspend fun ensureSession(
        forceFresh: Boolean = true,
        captchaHandler: CaptchaHandler = CaptchaHandler { challenge -> Vtop.awaitUiCaptcha(challenge) }
    ): Boolean {
        return com.amazecc.app.shared.api.AmazeClient.ensureSession(forceFresh, captchaHandler)
    }

    private suspend fun rows(
        path: String,
        body: String,
        selector: String,
        tableSelector: String? = null,
        tableIndex: Int = 0,
        captures: List<VtopScripts.Capture> = emptyList(),
        headerRowsToSkip: Int = 0
    ): VtopRows = withContext(Dispatchers.Default) {
        // Verify only: the sweep's gate already performed the single fresh login.
        ensureSession(forceFresh = false)
        var currentBody = body
        if (VtopSession.csrf.value != null && currentBody.contains("_csrf=")) {
            currentBody = currentBody.replace(Regex("_csrf=[^&]*"), "_csrf=${VtopSession.csrf.value}")
        }
        if (VtopSession.authorizedID.value != null && currentBody.contains("authorizedID=")) {
            currentBody = currentBody.replace(Regex("authorizedID=[^&]*"), "authorizedID=${VtopSession.authorizedID.value}")
        }

        var result = VtopRows.parse(
            Vtop.engineInstance().evaluate(
                VtopScripts.fetchRows(
                    path = path,
                    body = currentBody,
                    selector = selector,
                    tableSelector = tableSelector,
                    tableIndex = tableIndex,
                    captures = captures,
                    headerRowsToSkip = headerRowsToSkip
                ),
                timeoutMs = 30_000
            )
        )

        // If the table is empty or VTOP timed out, try re-logging in and retrying once. This is
        // the one per-module path that deliberately forces a genuinely fresh login.
        if (result.rows.isEmpty() && (VtopSession.isExpired || !VtopSession.hasSession)) {
            VtopSession.clear()
            if (ensureSession(forceFresh = true)) {
                var retryBody = body
                if (VtopSession.csrf.value != null && retryBody.contains("_csrf=")) {
                    retryBody = retryBody.replace(Regex("_csrf=[^&]*"), "_csrf=${VtopSession.csrf.value}")
                }
                if (VtopSession.authorizedID.value != null && retryBody.contains("authorizedID=")) {
                    retryBody = retryBody.replace(Regex("authorizedID=[^&]*"), "authorizedID=${VtopSession.authorizedID.value}")
                }
                result = VtopRows.parse(
                    Vtop.engineInstance().evaluate(
                        VtopScripts.fetchRows(
                            path = path,
                            body = retryBody,
                            selector = selector,
                            tableSelector = tableSelector,
                            tableIndex = tableIndex,
                            captures = captures,
                            headerRowsToSkip = headerRowsToSkip
                        ),
                        timeoutMs = 30_000
                    )
                )
            }
        }
        result
    }

    private suspend fun evaluate(script: String): String = withContext(Dispatchers.Default) {
        // Verify only: the sweep's gate already performed the single fresh login.
        ensureSession(forceFresh = false)
        Vtop.engineInstance().evaluate(script, timeoutMs = 30_000)
    }

    private fun semesterBody(semesterId: String): String? {
        val authorizedID = VtopSession.authorizedID.value?.takeIf { it.isNotBlank() } ?: return null
        val csrf = VtopSession.csrf.value?.takeIf { it.isNotBlank() } ?: return null
        return "authorizedID=$authorizedID&semesterSubId=$semesterId&_csrf=$csrf&x=${now()}"
    }

    // ── Timetable ───────────────────────────────────────────────────────────

    /** Course list from `processViewTimeTable`. Aligned with amazecc-api-hono `fetchTimeTable.ts`. */
    suspend fun fetchCourses(semesterId: String): List<VtopCourse> {
        val body = semesterBody(semesterId) ?: return emptyList()
        val result = rows(PATH_TIME_TABLE, body, "table.table tbody tr")
        if (!result.ok) return emptyList()

        return result.rows.mapIndexedNotNull { i, row ->
            if (row.size < 9) return@mapIndexedNotNull null
            val courseCell = result.cell(i, 2)
            if (courseCell.isBlank()) return@mapIndexedNotNull null
            val slotVenue = result.cell(i, 7)
            val category = result.cell(i, 4)
            val isLab = slotVenue.trim().startsWith("L")
            val baseCode = courseCell.split(" ").firstOrNull()?.trim() ?: courseCell.trim()
            val honoCourseCode = if (isLab) "$baseCode(L)" else "$baseCode(T)"

            VtopCourse(
                slNo = result.cell(i, 0),
                course = courseCell,
                courseCode = honoCourseCode,
                component = VtopCourseCode.typeLabelOf(
                    rawCode = courseCell,
                    typeHint = category,
                    slot = slotVenue
                ),
                ltpjc = result.cell(i, 3),
                category = category,
                classId = result.cell(i, 6),
                slotVenue = slotVenue,
                facultyDetails = result.cell(i, 8)
            )
        }
    }

    suspend fun fetchTimetable(semesterId: String): TimetableRes {
        if (!VtopSession.hasSession) {
            return TimetableRes(success = false, error = "No VTOP session")
        }
        val courses = fetchCourses(semesterId)
        val res = TimetableRes(
            success = courses.isNotEmpty(),
            semesterId = semesterId,
            courseInfo = courses.map { it.toCourseInfo() },
            message = if (courses.isEmpty()) "No courses returned by VTOP" else null
        )
        if (res.success && res.courseInfo?.isNotEmpty() == true) {
            AppDataStore.upsertTimetable(semesterId, res)
        }
        return res
    }

    // ── Attendance ──────────────────────────────────────────────────────────

    private data class RawAttendance(
        val courseCode: String,
        val courseTitle: String,
        val courseType: String,
        val slotName: String,
        val faculty: String,
        val attended: Int,
        val total: Int,
        val percentage: String,
        val viewLink: String?
    )

    private suspend fun fetchRawAttendance(semesterId: String): List<RawAttendance> {
        val body = semesterBody(semesterId) ?: return emptyList()
        val result = rows(
            PATH_ATTENDANCE,
            body,
            "#getStudentDetails table tbody tr",
            captures = listOf(VtopScripts.Capture(selector = "a", name = "onclick"))
        )
        if (!result.ok) return emptyList()

        return result.rows.mapIndexedNotNull { i, row ->
            // The server skips rows with fewer than 10 cells.
            if (row.size < 10) return@mapIndexedNotNull null
            val slotName = result.cell(i, 4)
            val title = result.cell(i, 2)
            val rawCode = result.cell(i, 1).trim()
            val isLab = slotName.trim().startsWith("L")
            val honoCode = if (isLab) "$rawCode(L)" else "$rawCode(T)"

            RawAttendance(
                courseCode = honoCode,
                courseTitle = title,
                courseType = VtopCourseCode.typeLabelOf(
                    rawCode = rawCode,
                    typeHint = result.cell(i, 3),
                    slot = slotName,
                    title = title
                ).orEmpty(),
                slotName = slotName,
                faculty = result.cell(i, 5),
                attended = result.cell(i, 9).toIntOrNull() ?: 0,
                total = result.cell(i, 10).toIntOrNull() ?: 0,
                percentage = result.cell(i, 11),
                viewLink = result.capture(i, 0)
            )
        }
    }

    /** Daily history for one course from `processViewAttendanceDetail` (columns 1 and 4). */
    private suspend fun fetchAttendanceDetail(classId: String, slotName: String): List<AttendanceLog> {
        val authorizedID = VtopSession.authorizedID.value ?: return emptyList()
        val csrf = VtopSession.csrf.value ?: return emptyList()
        val body = "_csrf=$csrf&authorizedID=$authorizedID&x=${now()}&classId=$classId&slotName=$slotName"

        val result = rows(PATH_ATTENDANCE_DETAIL, body, "table.table tr")
        if (!result.ok) return emptyList()

        // Row 0 is the header; the server skips it.
        return result.rows.drop(1).mapIndexedNotNull { j, row ->
            if (row.size < 5) return@mapIndexedNotNull null
            val i = j + 1
            AttendanceLog(date = result.cell(i, 1), status = result.cell(i, 4))
        }
    }

    private val DETAIL_CALL = Regex("processViewAttendanceDetail\\('([^']*)','([^']*)'\\)")

    /**
     * Attendance merged with the timetable, plus per-course daily history.
     *
     * Reproduces `mergeAttendanceWithTimetable`: the timetable is authoritative for which
     * courses appear and in what order, attendance rows are matched on the code with the
     * `(L)`/`(T)` suffix stripped, and the venue is narrowed to the last `ABC-123` token.
     */
    suspend fun fetchAttendance(semesterId: String, includeDetail: Boolean = true): AttendanceRes {
        if (!VtopSession.hasSession) return AttendanceRes(success = false, error = "No VTOP session")

        val courses = fetchCourses(semesterId)
        val raw = fetchRawAttendance(semesterId)
        // The trailing letter is anchored to the number (`\d+[A-Z]?`, not `\d+\s*[A-Z]?`).
        // With a space allowed, "AB1-12 LT1-34" matched "AB1-12 L" first and the next match
        // started at "T1-34", so venues came out truncated - a real defect, not a test artefact.
        val venuePattern = Regex("[A-Z]+\\d*\\s*-\\s*\\d+[A-Z]?")

        val merged = courses.map { course ->
            // `sameCourse` compares *base* codes, which deliberately ignores the component - right
            // for grouping a card, wrong here. An embedded course has two timetable rows and two
            // raw rows sharing one base code, so a base-code match sent both timetable rows to the
            // same first raw row and the lab's attendance was dropped before the merge ever saw it.
            // The halves are told apart by their own lab-ness instead.
            val courseIsLab = AcademicMerge.isLabRow(course.category, course.slotVenue)
            val match = raw.firstOrNull { candidate ->
                VtopCourseCode.sameCourse(candidate.courseCode, course.courseCode) &&
                    AcademicMerge.isLabRow(candidate.courseType, candidate.slotName) == courseIsLab
            }
            val cleanedVenue = venuePattern
                .findAll(course.slotVenue.replace(Regex("\\s+"), " ").trim())
                .lastOrNull()?.value

            val item = if (match != null) {
                AttendanceItem(
                    courseCode = match.courseCode,
                    courseTitle = match.courseTitle,
                    courseType = match.courseType,
                    slotName = match.slotName,
                    faculty = match.faculty,
                    attendedClasses = match.attended,
                    totalClasses = match.total,
                    attendancePercentage = match.percentage,
                    credits = course.credits,
                    slotVenue = cleanedVenue,
                    category = course.category.ifBlank { null }
                )
            } else {
                AttendanceItem(
                    courseCode = course.courseCode,
                    courseTitle = course.course,
                    courseType = "",
                    slotName = "NILL",
                    faculty = course.facultyDetails,
                    attendedClasses = 0,
                    totalClasses = 0,
                    attendancePercentage = "",
                    credits = course.credits,
                    slotVenue = cleanedVenue,
                    category = course.category.ifBlank { null }
                )
            }
            item to match?.viewLink
        }

        if (!includeDetail) {
            return AttendanceRes(
                success = merged.isNotEmpty(),
                semesterId = semesterId,
                attendance = merged.map { it.first }
            )
        }

        // Sequential rather than the server's concurrency of 3: the WebView script is
        // synchronous, so overlapping requests would interleave on the same JS thread.
        val detailed = merged.map { (item, viewLink) ->
            val link = viewLink?.takeIf { it.isNotBlank() } ?: return@map item
            val parsed = DETAIL_CALL.find(link) ?: return@map item
            val (classId, slotName) = parsed.destructured
            val logs = runCatching { fetchAttendanceDetail(classId, slotName) }.getOrDefault(emptyList())
            item.copy(logs = logs)
        }

        val res = AttendanceRes(
            success = merged.isNotEmpty(),
            semesterId = semesterId,
            attendance = detailed,
            message = if (merged.isEmpty()) "No attendance rows returned by VTOP" else null
        )
        if (res.success && res.attendance?.isNotEmpty() == true) {
            AppDataStore.upsertAttendance(semesterId, res)
        }
        return res
    }

    // ── Marks ──────────────────────────────────────────────────────────────

    /**
     * Marks from `examinations/doStudentMarkView`, plus the CGPA summary.
     *
     * Column indices from `src/lib/marks.ts`: 1 classNbr, 2 courseCode, 3 title, 4 type,
     * 5 system, 6 faculty, 7 slot, 8 mode. Courses with no assessment rows are dropped, as the
     * server does. Credits come from the timetable's LTPJC rather than the marks table.
     */
    suspend fun fetchMarks(semesterId: String): MarksRes? {
        if (!VtopSession.hasSession) return null
        val body = semesterBody(semesterId) ?: return null

        val creditMap = mutableMapOf<String, String>()
        fetchCourses(semesterId).forEach { c ->
            val credits = c.credits ?: "0"
            creditMap[c.courseCode.trim()] = credits
            creditMap[VtopCourseCode.base(c.courseCode)] = credits
            val base = c.course.split(" ").firstOrNull()?.trim() ?: ""
            if (base.isNotEmpty()) {
                creditMap["$base(T)"] = credits
                creditMap["$base(L)"] = credits
            }
        }

        val parsed = VtopMarksResult.parse(
            evaluate(VtopScripts.fetchMarks(PATH_MARKS, body))
        )
        if (!parsed.ok) return null

        val courses = parsed.courses.mapNotNull { raw ->
            val cells = raw.cells
            if (cells.size < 9) return@mapNotNull null
            if (raw.assessments.isEmpty()) return@mapNotNull null

            val courseTitle = cells[3].trim()
            val slot = cells[7].trim()
            val typeHint = cells[4].trim()
            val code = VtopCourseCode.canonical(
                rawCode = cells[2],
                typeHint = typeHint,
                slot = slot,
                title = courseTitle
            )
            val component = VtopCourseCode.typeLabelOf(
                rawCode = cells[2],
                typeHint = typeHint,
                slot = slot,
                title = courseTitle
            )
            val isLab = slot.startsWith("L")
            val primaryKey = "${cells[2].trim()}(${if (isLab) "L" else "T"})"
            val credits = creditMap[primaryKey] ?: creditMap[code] ?: creditMap[VtopCourseCode.base(cells[2])] ?: "0"

            MarksCourseItem(
                classNbr = cells[1].trim(),
                courseCode = code,
                courseTitle = courseTitle,
                courseType = component ?: typeHint,
                courseSystem = cells[5].trim(),
                faculty = cells[6].replace(Regex("\\s+"), " ").trim(),
                slot = slot,
                credits = credits,
                component = component,
                assessments = raw.assessments.map { a ->
                    AssessmentItem(
                        title = a.getOrElse(1) { "" }.trim(),
                        maxMark = a.getOrElse(2) { "" }.trim(),
                        weightagePercent = a.getOrElse(3) { "" }.trim(),
                        status = a.getOrElse(4) { "" }.trim(),
                        scoredMark = a.getOrElse(5) { "" }.trim(),
                        weightageMark = a.getOrElse(6) { "" }.trim(),
                        component = component
                    )
                }
            )
        }

        val summary = VtopCgpaSummary.parse(
            evaluate(VtopScripts.fetchCgpaSummary(PATH_CGPA, sessionBody() ?: return null))
        )

        val res = MarksRes(
            success = courses.isNotEmpty(),
            courses = courses,
            cgpa = summary?.let {
                CGPAResult(creditsEarned = it.creditsEarned, cgpa = it.cgpa)
            },
            message = if (courses.isEmpty()) "No marks returned by VTOP" else null
        )
        if (res.success && res.courses?.isNotEmpty() == true) {
            AppDataStore.upsertMarks(semesterId, res)
        }
        return res
    }

    // ── Grades ─────────────────────────────────────────────────────────────

    /**
     * Effective grades from `examinations/examGradeView/StudentGradeHistory`.
     *
     * The parser reaches the *second* table inside `#fixedTableContainer` and reads columns
     * 2/3/4/5/8 (`src/app/api/grades/route.ts:81-93`).
     */
    suspend fun fetchGrades(): SemesterGradesRes? {
        if (!VtopSession.hasSession) return null
        val body = sessionBody() ?: return null
        val result = rows(
            PATH_GRADE_HISTORY,
            body,
            "tr.tableContent",
            tableSelector = "#fixedTableContainer table",
            tableIndex = 1
        )
        if (!result.ok) return null

        val grades = result.rows.mapNotNull { row ->
            if (row.size < 9) return@mapNotNull null
            EffectiveGradeCourse(
                basketTitle = row[2].trim(),
                courseType = row[3].trim(),
                creditsEarned = row[4].trim(),
                grade = row[5].trim(),
                distributionType = row[8].trim()
            )
        }

        val res = SemesterGradesRes(
            success = grades.isNotEmpty(),
            effectiveGrades = grades,
            error = if (grades.isEmpty()) "No grades returned by VTOP" else null
        )
        if (res.success && grades.isNotEmpty()) {
            val semesterId = VtopSession.semesterSubId.value ?: ""
            val rows = grades.mapNotNull { row ->
                val title = row.basketTitle?.trim().orEmpty()
                val grade = row.grade?.trim().orEmpty()
                if (title.isEmpty() && grade.isEmpty()) return@mapNotNull null
                GradeItem(
                    courseTitle = title,
                    courseType = row.courseType.orEmpty(),
                    grade = grade,
                    grandTotal = ""
                )
            }
            if (rows.isNotEmpty()) {
                AppDataStore.upsertGrades(AllGradesRes(success = true, grades = mapOf(semesterId to SemesterGradeResult(grades = rows))))
            }
        }
        return res
    }

    // ── Hostel ─────────────────────────────────────────────────────────────

    /**
     * Hostel profile and leave history.
     *
     * Four POSTs, in the server's order: the profile page, a warm-up of `leave/1`, then the
     * `leave/6` history and `leave/4` applied tables. History rows are merged by leave id, so
     * an applied leave supersedes its history entry.
     */
    suspend fun fetchHostel(): HostelDetails? {
        if (!VtopSession.hasSession) return null
        val session = sessionBody() ?: return null
        val historyBody = leaveBody(control = "history", form = "undefined") ?: return null
        val appliedBody = leaveBody(control = "status", form = "undefined", extra = "&status=")
            ?: return null

        val profile = rows(PATH_STUDENT_PROFILE, session, "table tr")
        // Warm-up; the server issues this before the history calls and VTOP needs the session.
        rows(PATH_HOSTEL_LEAVE_LANDING, session, "table tr")

        val history = rows(
            PATH_HOSTEL_LEAVE_HISTORY,
            historyBody,
            "#LeaveHistoryTable tbody tr"
        )
        val applied = rows(
            PATH_HOSTEL_LEAVE_APPLIED,
            appliedBody,
            "#LeaveAppliedTable tbody tr"
        )

        var gender: String? = null
        var isHosteller = false
        var blockName: String? = null
        var roomNo: String? = null
        var messInfo: String? = null

        // Matches the server's label-substring test over two-column rows.
        profile.keyValuePairs.forEach { (label, value) ->
            when {
                label.contains("GENDER") -> gender = value
                label.contains("HOSTELLER") -> isHosteller = value == "HOSTELLER"
                label.contains("Block Name") -> blockName = value.split(" ").firstOrNull().orEmpty()
                    .ifBlank { "NOT ALLOTED" }
                label.contains("Room No") -> roomNo = value
                label.contains("Mess Information") -> {
                    var mess = value.split(" ").firstOrNull().orEmpty().ifBlank { "NOT ALLOTED" }
                    // VTOP sends the short form ("NON", "FOOD") and the app displays the expanded
                    // one. The old guard was `length > 7`, which made the expansion unreachable -
                    // "NON" is 3 characters and "FOOD" is 4, so neither could ever be rewritten.
                    // The known codes are matched first, then a long unrecognised value is treated
                    // as no allocation, and anything else is passed through untouched.
                    mess = when {
                        mess.equals("NON", true) -> "NON VEG"
                        mess.equals("FOOD", true) -> "FOOD PARK"
                        mess.length > 7 -> "NOT ALLOTED"
                        else -> mess
                    }
                    messInfo = mess
                }
            }
        }

        val leaves = LinkedHashMap<String, LeaveItem>()
        history.rows.forEachIndexed { i, row ->
            if (row.size < 8) return@forEachIndexed
            val id = history.cell(i, 1)
            leaves[id] = LeaveItem(
                visitPlace = history.cell(i, 2),
                reason = history.cell(i, 3),
                leaveType = history.cell(i, 4),
                from = history.cell(i, 5),
                to = history.cell(i, 6),
                status = history.cell(i, 7)
            )
        }
        applied.rows.forEachIndexed { i, row ->
            if (row.size < 9) return@forEachIndexed
            val id = applied.cell(i, 2)
            leaves[id] = LeaveItem(
                visitPlace = applied.cell(i, 3),
                reason = applied.cell(i, 4),
                leaveType = applied.cell(i, 5),
                from = applied.cell(i, 6),
                to = applied.cell(i, 7),
                status = applied.cell(i, 8)
            )
        }

        val res = HostelDetails(
            success = true,
            hostelInfo = if (gender == null && blockName == null && roomNo == null) {
                null
            } else {
                HostelInfo(
                    gender = gender,
                    isHosteller = isHosteller,
                    blockName = blockName,
                    roomNo = roomNo,
                    messInfo = messInfo
                )
            },
            leaveHistory = leaves.values.toList()
        )
        AppDataStore.setHostelDetails(res)
        return res
    }

    // ── Payments ───────────────────────────────────────────────────────────

    /**
     * Dues, receipts and wallet balance, combined into the shape [PaymentsRes] expects.
     *
     * Reproduces `AmazeClient.getPayments`: a pending-dues row when the dues page reports them,
     * one row per receipt, and the first INR ledger row's balance. The server's three
     * endpoints are three POSTs here.
     */
    suspend fun fetchPayments(): PaymentsRes? {
        if (!VtopSession.hasSession) return null
        val body = sessionBody() ?: return null

        val status = VtopPaymentStatus.parse(
            evaluate(VtopScripts.fetchPaymentStatus(PATH_PAYMENTS, body))
        )
        if (!status.ok) return null

        val receipts = rows(
            PATH_RECEIPTS,
            body,
            "tr",
            tableSelector = "table.table-bordered",
            tableIndex = 0,
            captures = listOf(
                VtopScripts.Capture(selector = "button", name = "onclick"),
                VtopScripts.Capture(selector = "input[name='applno']", name = "value", value = true),
                VtopScripts.Capture(selector = "input[name='regno']", name = "value", value = true)
            ),
            headerRowsToSkip = 1
        )

        val wallet = rows(
            PATH_WALLET,
            body,
            "tr",
            tableSelector = "table.table-bordered",
            tableIndex = 0,
            headerRowsToSkip = 1
        )

        val payments = buildList {
            if (status.hasDues) {
                add(
                    PaymentItem(
                        billingId = "due-pending",
                        description = status.message.ifBlank { "Pending Dues" },
                        amount = "Check VTOP",
                        dueDate = "-",
                        status = "UNPAID"
                    )
                )
            }
            receipts.rows.forEachIndexed { i, _ ->
                if (receipts.rows[i].size < 4) return@forEachIndexed
                val receiptNumber = receipts.cell(i, 0)
                add(
                    PaymentItem(
                        billingId = receiptNumber.ifBlank { "rec" },
                        description = "Fee Payment",
                        amount = receipts.cell(i, 2),
                        dueDate = "-",
                        status = "PAID",
                        paymentDate = receipts.cell(i, 1),
                        receiptNo = receiptNumber.ifBlank { null }
                    )
                )
            }
        }

        val balance = wallet.rows.firstOrNull()
            ?.let { wallet.cell(0, 5) }
            ?.takeIf { it.isNotBlank() }

        val res = PaymentsRes(
            success = true,
            payments = payments,
            walletBalance = balance
        )
        AppDataStore.setPayments(res)
        return res
    }

    // ── Group A — generic page parse ───────────────────────────────────────
    // APAAR, EPT, Registration Schedule, University Day and Dayboarder are all
    // "a form plus a couple of tables" with no bespoke column mapping, so they share one parser:
    // a port of AmazeCC-API's `parseVtopHtml`. See docs/sep-30-2026/modules/group-a-generic-page-parse.md

    private suspend fun parsePage(path: String): VtopPage? {
        val body = sessionBody() ?: return null
        return VtopPage.parse(evaluate(VtopScripts.parsePage(path, body))).takeIf { it.ok }
    }

    /** `{caption, headers, rows:[{header: value}]}` — the shape the server's DTOs expect. */
    private fun List<VtopPageTable>.toJsonArray(): JsonArray = JsonArray(
        map { table ->
            buildJsonObject {
                if (table.caption.isNotBlank()) put("caption", table.caption)
                put("headers", JsonArray(table.headers.map { JsonPrimitive(it) }))
                put("rows", JsonArray(table.rows.map { row ->
                    buildJsonObject {
                        table.headers.forEachIndexed { i, h -> if (h.isNotBlank()) put(h, row.getOrElse(i) { "" }) }
                    }
                }))
            }
        }
    )

    private fun Map<String, String>.toJsonObject(): JsonObject = buildJsonObject {
        forEach { (k, v) -> put(k, v) }
    }

    suspend fun fetchApaarId(): ApaarIdRes? {
        val page = parsePage(PATH_APAAR) ?: return null
        val res = ApaarIdRes(
            success = true,
            hasApaar = page.hasApaar,
            formFields = page.formFields.toJsonObject(),
            keyValuePairs = page.keyValuePairs.toJsonObject(),
            tables = page.tables.toJsonArray()
        )
        UserStore.merge(IdentityExtractor.fromApaarId(res), IdentitySource.APAAR)
        return res
    }

    suspend fun fetchEptSchedule(): EptScheduleRes? {
        val page = parsePage(PATH_EPT) ?: return null
        return EptScheduleRes(success = true, tables = page.tables.toJsonArray())
    }

    suspend fun fetchRegistrationSchedule(): RegistrationScheduleRes? {
        val page = parsePage(PATH_REGISTRATION) ?: return null
        return RegistrationScheduleRes(
            success = true,
            selectOptions = page.selectOptions.toOptionsJson(),
            tables = page.tables.toJsonArray(),
            keyValuePairs = page.keyValuePairs.toJsonObject(),
            formFields = page.formFields.toJsonObject(),
            hiddenFields = page.hiddenFields.toJsonObject(),
            messages = page.messages.toJsonObject()
        )
    }

    suspend fun fetchUniversityDay(): UniversityDayRes? {
        val page = parsePage(PATH_UNIVERSITY_DAY) ?: return null
        return UniversityDayRes(
            success = true,
            title = page.title,
            selectOptions = page.selectOptions.toOptionsJson(),
            tables = page.tables.toJsonArray(),
            keyValuePairs = page.keyValuePairs.toJsonObject(),
            formFields = page.formFields.toJsonObject(),
            hiddenFields = page.hiddenFields.toJsonObject(),
            messages = page.messages.toJsonObject()
        )
    }

    suspend fun fetchDayboarder(): DayboarderRes? {
        val page = parsePage(PATH_DAYBOARDER) ?: return null
        return DayboarderRes(
            success = true,
            fields = page.formFields.toJsonObject()
        )
    }

    private fun Map<String, List<VtopOption>>.toOptionsJson(): JsonObject = buildJsonObject {
        forEach { (name, options) ->
            put(
                name,
                JsonArray(options.map { o ->
                    buildJsonObject {
                        put("value", o.value)
                        put("text", o.text)
                        put("selected", o.selected)
                    }
                })
            )
        }
    }

    // ── Group B — key/value label scan ──────────────────────────────────────
    // The JS emits the DTO field names directly, so these are straight decodes of the
    // wrapper object rather than of a VtopPage.

    private val json = Json { ignoreUnknownKeys = true; isLenient = true; explicitNulls = false }

    private inline fun <reified T> decode(raw: String): T? = try {
        json.decodeFromString<T>(raw.ifBlank { "{}" })
    } catch (_: Exception) {
        null
    }

    suspend fun fetchStudentProfile(): StudentProfileRes? {
        val body = sessionBody() ?: return null
        val res = evaluate(VtopScripts.fetchStudentProfile(PATH_STUDENT_PROFILE, body))
        val page = VtopPage.parse(res)
        if (!page.ok) return null
        val profile = res.decodeAt("profile", StudentProfile.serializer())
        val output = StudentProfileRes(success = true, data = profile)
        if (output.success && output.data != null) {
            UserStore.merge(IdentityExtractor.fromStudentProfile(output), IdentitySource.STUDENT)
        }
        return output
    }

    suspend fun fetchBankInfo(): BankInfoRes? {
        val body = sessionBody() ?: return null
        val res = evaluate(VtopScripts.fetchBankInfo(PATH_BANK_INFO, body))
        val page = VtopPage.parse(res)
        if (!page.ok) return null
        // Fields carry nested option arrays, so read the raw object rather than VtopPage's
        // string-only formFields map.
        val fields = (res.rawObject()["fields"] as? JsonObject)?.toMap()
        val output = BankInfoRes(
            success = true,
            bankDetails = res.rawObject()["bankDetails"] ?: JsonNull,
            fields = fields
        )
        UserStore.merge(IdentityExtractor.fromBankInfo(output), IdentitySource.BANK)
        return output
    }

    suspend fun fetchProfileImages(): ProfileImagesRes? {
        val body = sessionBody() ?: return null
        val res = evaluate(VtopScripts.fetchProfileImages(PATH_PROCTOR, body))
        val page = VtopPage.parse(res)
        if (!page.ok) return null
        val output = ProfileImagesRes(
            success = true,
            proctor = res.decodeAt("proctor", ProfileImagesProctor.serializer()),
            hodDean = res.decodeAt("hodDean", ProfileImagesHodDean.serializer()),
            credentials = res.decodeAt("credentials", ProfileImagesCredentials.serializer())
        )
        UserStore.merge(IdentityExtractor.fromProfileImages(output), IdentitySource.PROFILE_IMAGES)
        return output
    }

    /** The parsed JSON object, or an empty one if the payload is not a JSON object. */
    private fun String.rawObject(): JsonObject =
        (try { json.parseToJsonElement(ifBlank { "{}" }) } catch (_: Exception) { null }) as? JsonObject ?: JsonObject(emptyMap())

    private fun <T> String.decodeAt(key: String, serializer: KSerializer<T>): T? {
        val el = rawObject()[key] ?: return null
        return try { json.decodeFromJsonElement(serializer, el) } catch (_: Exception) { null }
    }

    // ── Group C — table extractors ──────────────────────────────────────────

    suspend fun fetchExamSchedule(semesterId: String): ExamScheduleRes? {
        val body = sessionBody() ?: return null
        val res = evaluate(VtopScripts.fetchExamSchedule(PATH_EXAM_SCHEDULE, body, semesterId))
        val root = res.rawObject()
        val schedule = root["Schedule"]?.jsonObject?.mapValues { (_, v) ->
            (v as? JsonArray)?.mapNotNull { row ->
                try { json.decodeFromJsonElement(ExamItem.serializer(), row) } catch (_: Exception) { null }
            } ?: emptyList()
        } ?: emptyMap()
        val output = ExamScheduleRes(success = true, rawScheduleUpper = schedule)
        AppDataStore.upsertExams(semesterId, output)
        return output
    }

    suspend fun fetchCirculars(): CircularsRes? {
        val body = sessionBody() ?: return null
        val res = evaluate(VtopScripts.fetchCirculars(PATH_CIRCULARS, body))
        val page = VtopPage.parse(res)
        if (!page.ok) return null
        val root = res.rawObject()
        val output = CircularsRes(success = true, circulars = root.decodeList("circulars", circularItems) ?: emptyList())
        AppDataStore.setCirculars(output)
        return output
    }

    /**
     * Downloads a circular's binary from [PATH_CIRCULAR_DOWNLOAD] using the validated on-device
     * session. The WebView script returns the raw bytes base64-encoded (plus the response headers);
     * anything short of a 2xx with non-empty bytes is a failure and yields null — no data is
     * fabricated. Called by `AmazeClient.downloadCircular` when the source is LOCAL.
     */
    suspend fun downloadCircular(circularId: String): CircularDownloadResult? {
        if (circularId.isBlank()) return null
        if (!VtopSession.hasSession) return null
        val csrf = VtopSession.csrf.value?.takeIf { it.isNotBlank() } ?: return null
        val authorizedID = VtopSession.authorizedID.value?.takeIf { it.isNotBlank() } ?: return null

        val raw = evaluate(
            VtopScripts.downloadCircular(PATH_CIRCULAR_DOWNLOAD, csrf, authorizedID, circularId)
        )
        val root = raw.rawObject()
        if (!(root["ok"] as? JsonPrimitive)?.content.equals("true", ignoreCase = true)) return null

        val encoded = (root["base64"] as? JsonPrimitive)?.content ?: return null
        val bytes = try {
            Base64.Default.decode(encoded)
        } catch (_: Exception) {
            return null
        }
        if (bytes.isEmpty()) return null

        val mimeType = (root["contentType"] as? JsonPrimitive)?.content
            ?.substringBefore(';')?.trim()?.takeIf { it.isNotBlank() }
            ?: "application/pdf"
        val fileName = (root["fileName"] as? JsonPrimitive)?.content?.trim()?.takeIf { it.isNotBlank() }
            ?: "$circularId.pdf"
        return CircularDownloadResult(bytes = bytes, fileName = fileName, mimeType = mimeType)
    }

    suspend fun fetchCredentials(): CredentialsRes? {        val body = sessionBody() ?: return null
        val res = evaluate(VtopScripts.fetchCredentials(PATH_PROCTOR, body))
        val page = VtopPage.parse(res)
        if (!page.ok) return null
        val root = res.rawObject()
        val output = CredentialsRes(
            success = true,
            title = (root["title"] as? JsonPrimitive)?.content.orEmpty(),
            credentials = root.decodeList("credentials", credentialItems) ?: emptyList(),
            ranks = root.decodeList("ranks", rankItems) ?: emptyList()
        )
        UserStore.merge(IdentityExtractor.fromCredentials(output), IdentitySource.CREDENTIALS)
        return output
    }

    private inline fun <reified T> JsonObject.decodeList(
        key: String,
        serializer: KSerializer<List<T>>
    ): List<T>? {
        val el = this[key] ?: return null
        return try { json.decodeFromJsonElement(serializer, el) } catch (_: Exception) { null }
    }

    private val circularItems = ListSerializer(CircularItem.serializer())
    private val credentialItems = ListSerializer(ProfileImagesCredential.serializer())
    private val rankItems = ListSerializer(ProfileImagesRank.serializer())

    /**
     * QCM_VIEW.
     *
     * AmazeCC-API's `api/qcm/route.ts` is a single POST to `QCMStudentLogin` followed by
     * `parseVtopHtml(resp.data)` — the very same generic parser Group A ports. So there is
     * nothing QCM-specific here; the whole module is [VtopScripts.parsePage] on a different path.
     *
     * Only [QcmViewRes.data] is carried, which `AppSanitizers.sanitizeQcmView` already knows how
     * to turn into typed tables, so LOCAL and REMOTE converge on the same stored shape.
     *
     * Note: `docs/sep-30-2026/modules/group-c-table-extractors.md` describes a second stage
     * (`getStudentLoginForQcm`, one request per semester). No such call exists anywhere in
     * AmazeCC-API, so that stage is not ported — matching the server, which never made it.
     */
    suspend fun fetchQcmView(): QcmViewRes? {
        val body = sessionBody() ?: return null
        val stage1 = VtopPage.parse(evaluate(VtopScripts.parsePage(PATH_QCM_LOGIN, body)))
        if (!stage1.ok) return null

        // Stage 1 is only a form; the semester <select> is the payload. Empty means QCM is off
        // for this account, which is a successful "no data" rather than a failure.
        val options = stage1.selectOptions["semesterSubId"].orEmpty()
            .filter { it.value.isNotBlank() }
        if (options.isEmpty()) return QcmViewRes(success = true, data = JsonNull)
        val semesterIds = options.map { it.value }

        val qcmBody = sessionBody() ?: return null
        val tables = mutableListOf<JsonObject>()
        // Sequential, like every other fan-out here: the WebView script is synchronous.
        for (option in options) {
            val semesterId = option.value
            val raw = evaluate(VtopScripts.fetchQcmForSemester(PATH_QCM_DETAIL, qcmBody, semesterId))
            val obj = raw.rawObject()
            if ((obj["ok"] as? JsonPrimitive)?.content != "true") continue
            val rows = obj["rows"] as? JsonArray ?: continue
            // Older semesters answer 200 with the header row and no data. Emitting an empty table
            // would show a blank section per semester, so skip them.
            if (rows.isEmpty()) continue
            tables += buildJsonObject {
                // Prefer the readable option text ("Fall Semester 2026-27 - CHN") over the bare id.
                put("caption", option.text.ifBlank { semesterId })
                put("semester", semesterId)
                put("semesterId", semesterId)
                put("rows", rows)
            }
        }

        val qcmOutput = QcmViewRes(
            success = true,
            data = JsonArray(tables),
            semesters = semesterIds,
        )
        AppDataStore.setQcmView(qcmOutput)
        return qcmOutput
    }

    // ── Group D — curriculum ────────────────────────────────────────────────

    /**
     * Curriculum is the only genuinely two-stage module: stage 1 returns the category list *and*
     * a page-scoped csrf, and stage 2 must be replayed once per category using that page csrf
     * rather than the login one.
     *
     * Stage 2 runs sequentially. The server fanned these out with `Promise.all`, which the
     * synchronous WebView script cannot do — overlapping requests would interleave on one JS
     * thread — so expect this to be slower than the hosted version.
     */
    suspend fun fetchCurriculum(): CurriculumRes? {
        val body = sessionBody() ?: return null
        val stage1 = evaluate(VtopScripts.fetchCurriculumCategories(PATH_CURRICULUM, body))
        val root1 = stage1.rawObject()
        if (!(root1["ok"] as? JsonPrimitive)?.content.equals("true", ignoreCase = true)) return null

        val categories = root1.decodeList("categories", curriculumCategories) ?: emptyList()
        val pageCsrf = (root1["pageCsrf"] as? JsonPrimitive)?.content.orEmpty()

        val details = if (pageCsrf.isBlank()) {
            categories.map { CategoryDetail(code = it.code, name = it.name) }
        } else {
            categories.map { cat ->
                val res = evaluate(
                    VtopScripts.fetchCurriculumCategory(PATH_CURRICULUM_CATEGORY, pageCsrf, cat.code)
                )
                val baskets = res.decodeAt("baskets", curriculumBaskets) ?: emptyList()
                CategoryDetail(code = cat.code, name = cat.name, baskets = baskets)
            }
        }

        val curriculumOutput = CurriculumRes(
            success = true,
            title = (root1["title"] as? JsonPrimitive)?.content.orEmpty(),
            totalCredits = (root1["totalCredits"] as? JsonPrimitive)?.content?.toIntOrNull() ?: 0,
            categories = categories,
            details = details
        )
        AppDataStore.setCurriculum(curriculumOutput)
        return curriculumOutput
    }

    // ── Group E — Remote Parity Additions ────────────────────────────────────

    suspend fun fetchTransport(): TransportDataRes? {
        if (!VtopSession.hasSession) return null
        val session = sessionBody() ?: return null
        val page = parsePage(PATH_TRANSPORT_REG) ?: return null
        val busRouteId = page.hiddenFields["busRouteId"]
            ?: page.formFields["busRouteId"]
            ?: ""

        var regNo: String? = null
        var name: String? = null
        var branch: String? = null
        var routeSelected: String? = null

        page.tables.firstOrNull()?.rows?.forEach { row ->
            val label = row.getOrNull(0)?.trim().orEmpty()
            val value = row.getOrNull(1)?.trim().orEmpty()
            when {
                label.contains("Register Number", ignoreCase = true) -> regNo = value
                label.contains("Name of the Student", ignoreCase = true) -> name = value
                label.contains("Branch", ignoreCase = true) -> branch = value
                label.contains("Route Selected", ignoreCase = true) -> routeSelected = value
            }
        }
        page.keyValuePairs.forEach { (k, v) ->
            when {
                k.contains("Register Number", ignoreCase = true) && regNo == null -> regNo = v
                k.contains("Name of the Student", ignoreCase = true) && name == null -> name = v
                k.contains("Branch", ignoreCase = true) && branch == null -> branch = v
                k.contains("Route Selected", ignoreCase = true) && routeSelected == null -> routeSelected = v
            }
        }

        val res = TransportDataRes(
            success = true,
            hasRegistration = busRouteId.isNotBlank() || regNo != null,
            registerNumber = regNo,
            name = name,
            branch = branch,
            routeSelected = routeSelected,
            busRouteId = busRouteId.ifBlank { null }
        )
        AppDataStore.setTransportData(res)
        return res
    }

    suspend fun fetchOdDetails(semesterId: String): OdDetailsRes? {
        if (!VtopSession.hasSession) return null
        val session = sessionBody() ?: return null
        evaluate(VtopScripts.parsePage(PATH_OD_WARMUP, session))

        val semBody = semesterBody(semesterId) ?: return null
        val result = rows(
            PATH_OD_SEARCH,
            semBody,
            "#example1 tbody tr",
            headerRowsToSkip = 0
        )
        if (!result.ok) return null
        val records = result.rows.mapNotNull { row ->
            if (row.size < 7) return@mapNotNull null
            OdRecordItem(
                slNo = row.getOrElse(0) { "" }.trim(),
                type = row.getOrElse(1) { "" }.trim(),
                reason = row.getOrElse(2) { "" }.trim(),
                basis = row.getOrElse(3) { "" }.trim(),
                date = row.getOrElse(4) { "" }.trim(),
                time = row.getOrElse(5) { "" }.trim(),
                remarks = row.getOrElse(6) { "" }.trim()
            )
        }
        val output = OdDetailsRes(
            success = true,
            semesterId = semesterId,
            totalCount = records.size,
            records = records
        )
        AppDataStore.setOdDetails(output)
        return output
    }

    suspend fun fetchFeedbackStatus(semesterId: String): FeedbackStatusRes? {
        if (!VtopSession.hasSession) return null
        val semBody = semesterBody(semesterId) ?: return null
        val page = VtopPage.parse(evaluate(VtopScripts.parsePage(PATH_FEEDBACK_STATUS, semBody)))
        if (!page.ok) return null
        val tableRows = page.tables.firstOrNull()?.rows?.mapNotNull { r ->
            if (r.isEmpty()) return@mapNotNull null
            FeedbackTableRow(
                feedbackType = r.getOrNull(0)?.trim(),
                midSemester = r.getOrNull(1)?.trim(),
                teeSemester = r.getOrNull(2)?.trim()
            )
        }
        val output = FeedbackStatusRes(
            success = true,
            semesters = listOf(FeedbackSemester(text = semesterId, value = semesterId, selected = true)),
            feedbackTable = tableRows
        )
        AppDataStore.setFeedbackStatus(output)
        return output
    }

    suspend fun fetchCoursePage(): VtopPage? {
        if (!VtopSession.hasSession) return null
        val body = sessionBody() ?: return null
        return parsePage(PATH_COURSE_PAGE)
    }

    suspend fun fetchWhoami(): Boolean {
        if (!VtopSession.hasSession) return false
        val student = fetchStudentProfile()
        val profileImages = fetchProfileImages()
        val bank = fetchBankInfo()
        val dayboarder = fetchDayboarder()
        val apaar = fetchApaarId()
        val ept = fetchEptSchedule()
        return student != null || profileImages != null
    }

    private val curriculumCategories = ListSerializer(CurriculumCategory.serializer())
    private val curriculumBaskets = ListSerializer(CurriculumBasket.serializer())
}
