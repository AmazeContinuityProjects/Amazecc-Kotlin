package com.amazecc.app.shared.model

import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant


@Serializable
data class BasicRes(
    val success: Boolean,
    val message: String? = null
)

@Serializable
data class LoginResponse(
    val success: Boolean,
    val message: String,
    val cookies: String? = null,
    val csrf: String? = null,
    val authorizedID: String? = null,
    val clubToken: String? = null,
    val error: String? = null
)

@Serializable
data class TimetableRes(
    val success: Boolean = true,
    val semesterId: String? = null,
    val error: String? = null,
    val message: String? = null,
    /** Raw timetable rows scraped from VTOP (courseCode/classId/slotVenue/facultyDetails). */
    val courseInfo: List<com.amazecc.app.shared.state.TimetableCourseInfo>? = null,
    /** Processed slots (day × slot × course with resolved time) — filled by the store sanitizer. */
    val slots: List<com.amazecc.app.shared.state.TimetableSlot> = emptyList()
)

@Serializable
data class CGPAResult(
    val creditsEarned: String? = null,
    val cgpa: String? = null,
    // D-019: the web marks.d.ts CGPA contract also publishes these two cells.
    @Serializable(with = FlexibleStringSerializer::class)
    val creditsRequired: String? = null,
    @Serializable(with = FlexibleStringSerializer::class)
    val nonGradedRequirement: String? = null
)

@Serializable
data class MarksRes(
    val success: Boolean = true,
    @SerialName("marks") val marksKey: List<MarksCourseItem> = emptyList(),
    val courses: List<MarksCourseItem> = emptyList(),
    val cgpa: CGPAResult? = null,
    val error: String? = null,
    val message: String? = null
) {
    val marks: List<MarksCourseItem> get() = if (marksKey.isNotEmpty()) marksKey else courses
}

val MarksRes?.displayCgpa: Double get() = this?.cgpa?.cgpa?.toDoubleOrNull() ?: 0.0
val MarksRes?.displayCreditsEarned: Double get() = this?.cgpa?.creditsEarned?.toDoubleOrNull() ?: 0.0

@Serializable
data class HostelInfo(
    val gender: String? = null,
    val isHosteller: Boolean = false,
    val blockName: String? = null,
    val roomNo: String? = null,
    val messInfo: String? = null
)

@Serializable
data class HostelDetails(
    val success: Boolean = true,
    val hostelInfo: HostelInfo? = null,
    val leaveHistory: List<LeaveItem> = emptyList(),
    val error: String? = null,
    val message: String? = null
)

@Serializable
data class ExamScheduleRes(
    val success: Boolean = true,
    @kotlinx.serialization.SerialName("Schedule") val rawScheduleUpper: Map<String, List<ExamItem>>? = null,
    @kotlinx.serialization.SerialName("schedule") val rawScheduleLower: Map<String, List<ExamItem>>? = null,
    val error: String? = null,
    val message: String? = null
) {
    val schedule: Map<String, List<ExamItem>>
        get() = rawScheduleUpper ?: rawScheduleLower ?: emptyMap()
}

@Serializable
data class CalendarRes(
    val success: Boolean = true,
    val months: List<CalendarMonth> = emptyList(),
    val error: String? = null,
    val message: String? = null
)

@Serializable
data class NamedCalendar(
    val name: String,
    val months: List<CalendarMonth> = emptyList()
)

@Serializable
data class CalendarsListRes(
    val success: Boolean = true,
    val calendars: List<NamedCalendar> = emptyList(),
    val error: String? = null,
    val message: String? = null
)

@Serializable
data class QcmTable(
    val caption: String = "",
    @Contextual
    val rows: List<JsonElement> = emptyList()
)

@Serializable
data class QcmViewRes(
    val success: Boolean = true,
    @Contextual
    val data: JsonElement? = null,
    /** Typed tables deciphered from [data] — filled by the store sanitizer (raw [data] is dropped at the store boundary). */
    val tables: List<com.amazecc.app.shared.state.StoredQcmTable> = emptyList(),
    /** Semester ids QCM was fetched for, from the stage-1 semesterSubId select.
     *  QCM is two-stage: stage 1 returns only that select, and stage 2 (getStudentLoginForQcm,
     *  which needs semSubId) holds the rows. The API emits `{value, text}` option objects;
     *  the legacy scraper emits bare ids. Both decode to this `List<String>` (D-098). */
    @Serializable(with = FlexibleStringListSerializer::class)
    val semesters: List<String> = emptyList(),
    val message: String? = null,
    val error: String? = null
)

@Serializable
data class MoodleAssignment(
    val name: String,
    /**
     * The due date as Moodle *renders* it, scraped from the activity page's
     * date block — e.g. `"Monday, 28 September 2026, 23:59:00"`.
     *
     * Display-only, and deliberately not parsed. It is localised prose: it
     * changes with the account's Moodle language, with the site's phrasing, and
     * with whether the course sets a due time at all. Anything that needs the
     * actual date should read [year]/[monthNumber]/[dayOfMonth], which arrive as
     * numbers on the same payload, or [NotificationsUtils.deadlineDate] if it is
     * holding an ISO string from some other source.
     */
    val due: String,
    val done: Boolean = false,
    val url: String? = null,
    val teachers: List<String> = emptyList(),
    val hidden: Boolean = false,
    /** Calendar year of the deadline. 0 when the payload did not carry one. */
    val year: Int = 0,
    /** Calendar month, 1-12. 0 when absent. */
    val monthNumber: Int = 0,
    /** Day of month. 0 when absent. */
    val dayOfMonth: Int = 0,
    // D-091: POST /api/lms-data returns the bare scraper array with `day`/`month`, while the
    // on-device scraper naming uses `dayOfMonth`/`monthNumber`. Accept both spellings.
    @SerialName("day") val day: Int? = null,
    @SerialName("month") val month: Int? = null
) {
    /** Effective calendar month from either wire spelling. */
    val effectiveMonthNumber: Int get() = monthNumber.takeIf { it in 1..12 } ?: month ?: 0
    /** Effective day-of-month from either wire spelling. */
    val effectiveDayOfMonth: Int get() = dayOfMonth.takeIf { it in 1..31 } ?: day ?: 0
    /**
     * The deadline as a real date, or null when the payload carried no usable
     * numbers and [due] is not in a machine-readable form.
     *
     * The numbers are validated rather than trusted: they come from the same
     * scrape as everything else, and `LocalDate` throws on an impossible day.
     */
    val dueDate: kotlinx.datetime.LocalDate?
        get() = if (year in 2000..2100 && effectiveMonthNumber in 1..12 && effectiveDayOfMonth in 1..31) {
            runCatching { kotlinx.datetime.LocalDate(year, effectiveMonthNumber, effectiveDayOfMonth) }.getOrNull()
        } else {
            null
        }

    val courseCode: String get() {
        val parts = name.split("/")
        val first = parts.firstOrNull()?.trim() ?: return ""
        val code = first.split("-").firstOrNull()?.trim() ?: first
        val match = Regex("[A-Za-z]+\\d+").find(code)
        return match?.value ?: code.take(20)
    }

    val courseTitle: String get() {
        val parts = name.split("/")
        return if (parts.size >= 2) parts[1].trim() else name
    }

    val taskTitle: String get() {
        val parts = name.split("/")
        return if (parts.size >= 3) parts.drop(2).joinToString("/").trim() else name
    }
}

@Serializable
data class MoodleRes(
    val success: Boolean = true,
    val error: String? = null,
    val message: String? = null,
    val data: List<MoodleAssignment> = emptyList()
)

@Serializable
data class PaymentItem(
    val billingId: String,
    val description: String,
    val amount: String,
    val dueDate: String? = null,
    val status: String,
    val paymentDate: String? = null,
    val receiptNo: String? = null
)

@Serializable
data class PaymentsRes(
    val success: Boolean = true,
    val payments: List<PaymentItem> = emptyList(),
    val walletBalance: String? = null,
    val error: String? = null,
    val message: String? = null,
    // D-022: typed, lossless projections of the finance sections. Additive so every existing
    // consumer of `payments`/`walletBalance` keeps working.
    val dues: DuesSection? = null,
    val receipts: List<ReceiptItem> = emptyList(),
    val ledgerINR: List<LedgerEntry> = emptyList(),
    val ledgerUSD: List<LedgerEntry> = emptyList()
)

@Serializable
data class BookItem(
    val bookId: String,
    val title: String,
    val author: String? = null,
    val dueDate: String? = null,
    @Serializable(with = FlexibleStringSerializer::class)
    val fineAmount: String? = null,
    /** ISBN from the VTOP library-dues table (used as [bookId] when no other id exists). */
    val isbn: String? = null,
    /** Raw "days overdue" text from the VTOP library-dues table, e.g. `"12"`. */
    @Serializable(with = FlexibleStringSerializer::class)
    val daysOverdue: String? = null
)

@Serializable
data class LibraryRes(
    val success: Boolean = true,
    val booksIssued: List<BookItem> = emptyList(),
    val searchResults: List<BookItem> = emptyList(),
    val total: Int = 0,
    /** D-017: `POST /api/finance?section=library` nests dues under `library.books`. */
    @SerialName("library") val libraryDues: LibraryDues? = null,
    val error: String? = null,
    val message: String? = null
) {
    /** Books from whichever shape the payload used, without duplicating rows. */
    val effectiveBooks: List<BookItem> get() = booksIssued.ifEmpty { libraryDues?.booksIssued.orEmpty() }
}

@Serializable
data class KohaBook(
    val biblionumber: String = "",
    val title: String = "",
    val author: String = "",
    val publisher: String = "",
    val isbn: String = "",
    val coverUrl: String = ""
)

@Serializable
data class KohaSearchRes(
    val success: Boolean = true,
    val books: List<KohaBook> = emptyList(),
    val total: Int = 0,
    val query: String? = null,
    val error: String? = null,
    val message: String? = null
)

@Serializable
data class KohaHolding(
    val itemId: String = "",
    val barcode: String = "",
    val currentLibrary: String = "",
    val homeLibrary: String = "",
    val shelvingLocation: String = "",
    val callNumber: String = "",
    val status: String = "",
    val dateDue: String? = null,
    val notes: String = ""
)

@Serializable
data class KohaDetailBook(
    val biblionumber: String = "",
    val title: String = "",
    val author: String = "",
    val publisher: String = "",
    val edition: String = "",
    val description: String = "",
    val isbn: String = "",
    val subjects: List<String> = emptyList(),
    val ddc: String = "",
    val summary: String = "",
    val holdings: List<KohaHolding> = emptyList(),
    val coverUrl: String = ""
)

@Serializable
data class KohaDetailRes(
    val success: Boolean = true,
    val book: KohaDetailBook? = null,
    val error: String? = null,
    val message: String? = null
)

@Serializable
data class BusStop(
    val stopOrder: Int,
    val stopName: String,
    val pickupTime: String? = null
)

@Serializable
data class BusPlacement(
    val dispersalTime: String = "",
    val zone: String = ""
)

@Serializable
data class BusRoute(
    val id: String,
    val type: String,
    val route: String,
    val boardingPoints: List<String> = emptyList(),
    /** Evening dispersal batches from the public /api/buses payload (e.g. 5PM / 6PM). */
    val placements: List<BusPlacement> = emptyList(),
    val driverPhone: String,
    val driverName: String,
    val whatsappGroup: String,
    val busLocation: String,
    val supervisorName: String? = null,
    val supervisorPhone: String? = null,
    val driverInchargeName: String? = null,
    val driverInchargePhone: String? = null,
    val stops: List<BusStop> = emptyList()
)

@Serializable
data class BusesRes(
    val success: Boolean = true,
    val buses: List<BusRoute> = emptyList(),
    val error: String? = null,
    val message: String? = null
)

@Serializable
data class TransportDataRes(
    val success: Boolean = true,
    val hasRegistration: Boolean = false,
    val registerNumber: String? = null,
    val name: String? = null,
    val branch: String? = null,
    // D-021: registration detail cells published by /api/transport.
    val programme: String? = null,
    val fpReference: String? = null,
    val paymentStatus: String? = null,
    val pageCsrf: String? = null,
    val routeSelected: String? = null,
    val busRouteId: String? = null,
    val qrCode: String? = null,
    /** Live GPS share link from `/vtop/transport/trackMyBus`; null when unavailable. */
    val busUrl: String? = null,
    val error: String? = null,
    val message: String? = null
)

@Serializable
data class TransportRegRequest(
    val routeNo: String,
    val semester: String,
    val studentName: String,
    val studentPhone: String
)

@Serializable
data class TransportRegSubmitRes(
    val success: Boolean = true,
    val message: String? = null,
    val error: String? = null,
    val registrationId: String? = null
)

/**
 * One Moodle calendar event, as `lms.vit.ac.in` actually serves it.
 *
 * This shape is the scraper's real output, verified against a live login
 * (`AmazeCC-API/scripts/lms-dump.mjs`). It previously declared
 * `{assignmentId, courseCode, title, maxMarks, dueDate, status, score}` — a shape **no route ever
 * produced**; nothing in the server emits `maxMarks`, `status` or `score`, so that DTO could not
 * deserialise the response and silently yielded all-default objects.
 *
 * [name] is the composite `"<code>/<course title>/<assignment>"` the scraper builds, kept because
 * it is what grouping and de-duplication keys on. The three parts are also carried separately.
 */
@Serializable
data class LMSAssignment(
    /** `BAMAT209_FALL26-27/Mathematical Foundations for Computation(BAMAT209)/Digital_Assignment_2` */
    val name: String = "",
    /** Breadcrumb text, e.g. `BAMAT209_FALL26-27`. */
    val courseCode: String = "",
    /** Breadcrumb `title` attribute, e.g. `Mathematical Foundations for Computation(BAMAT209)`. */
    val courseTitle: String = "",
    /** `h1.h2` on the event page, e.g. `Digital_Assignment_2`. */
    val assignmentTitle: String = "",
    /** Verbatim Moodle text, e.g. `Sunday, 4 October 2026, 12:00 AM`. Not an ISO date. */
    val due: String = "",
    val done: Boolean = false,
    val day: Int? = null,
    val month: Int? = null,
    val year: Int? = null,
    val url: String? = null,
    val teachers: List<String> = emptyList(),
) {
    /**
     * The bare course code, e.g. `BAMAT209` out of `BAMAT209_FALL26-27`.
     *
     * VTOP spells course codes bare too, so this is what lets an LMS assignment line up with a
     * `StoredCourse` in the academics snapshot.
     */
    val shortCourseCode: String
        get() = courseCode.substringBefore('_').ifBlank { name.substringBefore('/') }.trim()

    /** "Mathematical Foundations for Computation(BAMAT209)" -> "Mathematical Foundations for Computation". */
    val readableCourseTitle: String
        get() = courseTitle.substringBeforeLast('(').trim().ifBlank { courseTitle.trim() }

    /** Stable id, so re-syncing does not duplicate rows in the task list. */
    val stableId: String
        get() = url ?: "$shortCourseCode/$assignmentTitle".trim('/')

    /**
     * The due moment, built from the calendar cell's numeric date plus the clock time Moodle puts
     * in [due].
     *
     * The prose form is "Sunday, 4 October 2026, 12:00 AM" — note Moodle writes midnight as
     * `12:00 AM`, so it has to be normalised like any other meridiem time or the reminder fires a
     * day early. The calendar cell is the reliable source for the date; the text supplies the time.
     */
    fun dueInstant(
        tz: kotlinx.datetime.TimeZone = kotlinx.datetime.TimeZone.currentSystemDefault()
    ): kotlinx.datetime.Instant? {
        val y = year ?: return null
        val m = month ?: return null
        val d = day ?: return null

        var hour = 0
        var minute = 0
        Regex("""(\d{1,2}):(\d{2})\s*(AM|PM)?""", RegexOption.IGNORE_CASE).find(due)?.let { mt ->
            var h = mt.groupValues[1].toIntOrNull() ?: 0
            val min = mt.groupValues[2].toIntOrNull() ?: 0
            val meridian = mt.groupValues[3].uppercase()
            if (h !in 1..12 || min !in 0..59) return@let
            if (meridian == "PM" && h != 12) h += 12
            if (meridian == "AM" && h == 12) h = 0
            hour = h
            minute = min
        }

        if (y !in 1900..2100 || m !in 1..12 || d !in 1..31) return null
        return try {
            kotlinx.datetime.LocalDateTime(y, m, d, hour, minute, 0, 0).toInstant(tz)
        } catch (_: Exception) {
            null
        }
    }

    /** The due date alone, for surfaces that only need the day. */
    fun dueDateOrNull(): String? =
        (year?.takeIf { it in 1900..2100 })?.let { y ->
            val m = month ?: return@let null
            val d = day ?: return@let null
            if (m !in 1..12 || d !in 1..31) null else "%04d-%02d-%02d".format(y, m, d)
        }
}

@Serializable
data class LMSRes(
    val success: Boolean = true,
    val assignments: List<LMSAssignment> = emptyList(),
    val error: String? = null,
    val message: String? = null
)

@Serializable
data class QBankQuestion(
    val question_id: String = "",
    val question_text: String = "",
    val question_type: String = "",
    val options: Map<String, String>? = null,
    val correct_answer: String? = null,
    val marks: Int? = null,
    val topic_name: String? = null,
    val exam_semester: String? = null,
    // D-023: paper provenance joined from `papers_archive` by the public questions route.
    @SerialName("source_type") val source_type: String? = null,
    @Serializable(with = FlexibleStringSerializer::class)
    @SerialName("exam_year") val exam_year: String? = null,
    @SerialName("image_url") val image_url: String? = null
)

@Serializable
data class QBankRes(
    val success: Boolean = true,
    val data: List<QBankQuestion> = emptyList(),
    val error: String? = null,
    val message: String? = null
)

/**
 * One approved paper from `GET /api/qbank/papers` (D-016/D-106). The API column names are
 * `file_url` / `source_type` / `source_id` etc.; the old DTO used the renamed `link` / `type`
 * and declared all three fields non-null, so any missing column threw `MissingFieldException` and
 * the whole papers list collapsed. Keep `link`/`type` as derived UI-facing properties.
 */
@Serializable
data class QBankPaper(
    @SerialName("source_id") val sourceId: String = "",
    val title: String = "",
    @SerialName("file_url") val fileUrl: String = "",
    @SerialName("source_type") val sourceType: String = "",
    @SerialName("exam_semester") val examSemester: String? = null,
    @Serializable(with = FlexibleStringSerializer::class)
    @SerialName("exam_year") val examYear: String? = null,
    @SerialName("course_code") val courseCode: String? = null,
    @SerialName("link") private val legacyLink: String? = null,
    @SerialName("type") private val legacyType: String? = null
) {
    val link: String get() = fileUrl.ifBlank { legacyLink.orEmpty() }
    val type: String get() = sourceType.ifBlank { legacyType.orEmpty() }
}

@Serializable
data class QBankPapersRes(
    val success: Boolean = true,
    val data: List<QBankPaper> = emptyList(),
    val error: String? = null,
    val message: String? = null
)


@Serializable
data class EventHubRes(
    val success: Boolean = true,
    val events: List<EventHubEvent> = emptyList(),
    val error: String? = null,
    val message: String? = null
)

@Serializable
data class ClubItem(
    @SerialName("club_id") val id: String? = null,
    @SerialName("club_name") val name: String? = null,
    @SerialName("description") val description: String? = null,
    @SerialName("logo_url") val logoUrl: String? = null,
    val website: String? = null,
    val instagram: String? = null,
    val whatsapp: String? = null,
    @SerialName("recruitment_link") val recruitmentLink: String? = null,
    @SerialName("event_id") val eventId: String? = null
)

@Serializable
data class ClubsRes(
    val success: Boolean = true,
    val clubs: List<ClubItem> = emptyList(),
    val error: String? = null,
    val message: String? = null
)

@Serializable
data class FeedLink(val title: String = "", val url: String = "")

@Serializable
data class FeedPost(
    val id: String = "",
    @SerialName("club_id") val clubId: String = "",
    @SerialName("created_at") val createdAt: String = "",
    val content: String = "",
    @SerialName("image_urls") val imageUrls: List<String> = emptyList(),
    @SerialName("event_id") val eventId: String? = null,
    @SerialName("has_promoted") val hasPromoted: Boolean = false,
    @SerialName("promote_count") val promoteCount: Int = 0,
    val links: List<FeedLink>? = null
)

@Serializable
data class FeedRes(
    val success: Boolean = true,
    val feed: List<FeedPost> = emptyList(),
    val error: String? = null,
    val message: String? = null
)

@Serializable
data class QBankSubmitRes(val success: Boolean, val message: String? = null)

@Serializable
data class QBankCourse(val courseCode: String, val courseTitle: String)

@Serializable
data class QBankCourseRaw(val code: String = "", val title: String = "")

@Serializable
data class QBankCoursesRes(
    val success: Boolean = true,
    @SerialName("courses") val courses: List<QBankCourse> = emptyList(),
    /** D-027: the live API returns `{ data: [{code, title}] }`; demo payloads use `courses`. */
    @SerialName("data") val apiData: List<QBankCourseRaw> = emptyList(),
    val error: String? = null,
    val message: String? = null
) {
    /** Normalised courses from either wire shape. */
    val effectiveCourses: List<QBankCourse> get() = courses.ifEmpty {
        apiData.map { QBankCourse(courseCode = it.code, courseTitle = it.title) }
    }
}

@Serializable
data class StudentProfile(
    // D-024: the API accepts `registerNo` / `registerNumber` / `registrationNumber` as aliases
    // of the legacy `applicationNumber`. All four are wire fields; `regNo` below is the single
    // UI-facing value, preferring the canonical registration number when present.
    @SerialName("applicationNumber") val applicationNumberWire: String = "",
    @SerialName("registerNo") private val registerNoWire: String? = null,
    @SerialName("registerNumber") private val registerNumberWire: String? = null,
    @SerialName("registrationNumber") private val registrationNumberWire: String? = null,
    val name: String = "",
    val email: String = "",
    @SerialName("mobileNumber") val mobile: String = "",
    @SerialName("appliedDegree") val program: String = "",
    val campus: String = "",
    val batch: String = "",
    val section: String? = null,
    val advisorName: String? = null,
    val bloodGroup: String? = null,
    @SerialName("image") val photoBase64: String? = null,
    val nativeLanguage: String? = null,
    val nationality: String? = null,
    val community: String? = null,
    val religion: String? = null,
    val caste: String? = null,
    val physicallyChallenged: String? = null,
    val aadharNumber: String? = null,
    val dob: String? = null,
    val gender: String? = null,
    val isHosteller: Boolean = false,
    val nativeState: String? = null,
    val currentAddress: Map<String, JsonElement> = emptyMap(),
    val permanentAddress: Map<String, JsonElement> = emptyMap(),
    val father: Map<String, JsonElement> = emptyMap(),
    val mother: Map<String, JsonElement> = emptyMap(),
    val guardian: String? = null,
    val proctor: ProfileProctor? = null
) {
    val regNo: String get() = listOf(
        registerNoWire, registerNumberWire, registrationNumberWire, applicationNumberWire
    ).firstOrNull { !it.isNullOrBlank() }?.trim().orEmpty()
}

@Serializable
data class ProfileProctor(
    val facultyId: String? = null,
    val name: String? = null,
    val designation: String? = null,
    val school: String? = null,
    val cabin: String? = null,
    val department: String? = null,
    val email: String? = null,
    val intercom: String? = null,
    val mobile: String? = null
)

@Serializable
data class StudentProfileRes(
    val success: Boolean = true,
    @SerialName("profile")
    val data: StudentProfile? = null,
    val message: String? = null,
    val error: String? = null
)

@Serializable
data class FacultySchool(
    val id: String,
    val school_name: String
)

@Serializable
data class FacultySchoolsRes(
    val success: Boolean = true,
    val schools: List<FacultySchool> = emptyList(),
    val error: String? = null
)

@Serializable
data class FacultyProfile(
    val id: String,
    val name: String,
    val designation: String,
    val imageUrl: String = "",
    val profileUrl: String = "",
    val email: String = "",
    val employeeId: String = "",
    val intercom: String = ""
)

@Serializable
data class FacultyScrapeRes(
    val success: Boolean = true,
    val faculties: List<FacultyProfile> = emptyList(),
    val error: String? = null
)

@Serializable
data class DirectoryCCProfile(
    val employeeId: String = "",
    val name: String = "",
    val designation: String = "",
    val email: String = "",
    val intercom: String = ""
)

@Serializable
data class CabShareUser(
    val reg_number: String,
    val name: String = "",
    val phone_number: String = ""
)

@Serializable
data class CabShareHub(
    val hub_id: Int,
    val hub_name: String
)

/** `GET /api/cabshare/hubs` envelope. */
@Serializable
data class CabShareHubsRes(
    val success: Boolean = true,
    val hubs: List<CabShareHub> = emptyList(),
    val error: String? = null
)

val fallbackCabHubs = listOf(
    CabShareHub(1, "VIT Chennai"),
    CabShareHub(2, "Chennai Airport"),
    CabShareHub(3, "Chennai Central Railway Station"),
    CabShareHub(4, "Tambaram Railway Station"),
    CabShareHub(5, "Chengalpattu Railway Station"),
    CabShareHub(6, "Koyambedu Bus Stand"),
    CabShareHub(7, "Kelambakkam"),
    CabShareHub(8, "Sholinganallur"),
    CabShareHub(9, "T Nagar"),
    CabShareHub(10, "Guindy"),
    CabShareHub(11, "OMR"),
    CabShareHub(12, "Perungudi"),
    CabShareHub(13, "Thoraipakkam"),
    CabShareHub(14, "Velachery")
)

@Serializable
data class CabShareTrip(
    val trip_id: Long = 0,
    val reg_number: String = "",
    val name: String = "",
    val owner_name: String = "",
    val owner_phone: String = "",
    val from_hub_name: String = "",
    val hub_id: Int? = null,
    val hub_name: String = "",
    val travel_date: String = "",
    val preferred_time: String = "",
    val tolerance_hours: Double = 1.0,
    val notes: String = "",
    val status: String = "active",
    val match_status: String? = null,
    val requests: List<CabShareMatchRequest> = emptyList()
)

@Serializable
data class CabShareMatchRequest(
    val match_id: Long = 0,
    val name: String = "",
    val phone_number: String = "",
    val status: String = "pending"
)

@Serializable
data class CabShareTripsRes(
    val success: Boolean = true,
    val trips: List<CabShareTrip> = emptyList(),
    val my_trips: List<CabShareTrip> = emptyList(),
    val joined_trips: List<CabShareTrip> = emptyList(),
    val error: String? = null,
    val message: String? = null
)

@Serializable
data class CabShareAuthRes(
    val success: Boolean = true,
    val user: CabShareUser? = null,
    val error: String? = null,
    val message: String? = null
)

@Serializable
data class CabActionRes(
    val success: Boolean = true,
    val message: String? = null,
    val error: String? = null,
    val tripId: String? = null
)


@Serializable
data class ProfileImagesCredential(
    val account: String = "",
    val username: String = "",
    val url: String? = null,
    val defaultCredentials: String = "",
    val venueDate: String = "",
    val seatLocation: String = ""
)

@Serializable
data class ProfileImagesRank(
    val name: String = "",
    val rank: String = ""
)

@Serializable
data class ProfileImagesCredentials(
    val title: String = "",
    val credentials: List<ProfileImagesCredential> = emptyList(),
    val ranks: List<ProfileImagesRank> = emptyList()
)

// Dedicated /credentials endpoint — linked account logins, exam venue/seat & ranks
@Serializable
data class CredentialsRes(
    val success: Boolean = true,
    val title: String = "",
    val credentials: List<ProfileImagesCredential> = emptyList(),
    val ranks: List<ProfileImagesRank> = emptyList(),
    val error: String? = null
)

@Serializable
data class ProfileImagesProctor(
    val title: String = "",
    val photoBase64: String? = null,
    val details: Map<String, String> = emptyMap()
)

@Serializable
data class ProfileImagesHodDeanPerson(
    val role: String = "",
    val details: Map<String, String> = emptyMap(),
    val photoBase64: String? = null
)

@Serializable
data class ProfileImagesStudent(
    val photoBase64: String? = null
)

@Serializable
data class ProfileImagesHodDean(
    val title: String = "",
    val people: List<ProfileImagesHodDeanPerson> = emptyList()
)

@Serializable
data class ProfileImagesRes(
    val success: Boolean = true,
    val proctor: ProfileImagesProctor? = null,
    val hodDean: ProfileImagesHodDean? = null,
    val credentials: ProfileImagesCredentials? = null,
    val student: ProfileImagesStudent? = null,
    val profile: ProfileImagesStudent? = null,
    val studentPhoto: String? = null,
    val error: String? = null
)


@Serializable
data class CurriculumCategory(
    val code: String = "",
    val name: String = "",
    val credits: Int = 0,
    val maxCredits: Int = 0
)

@Serializable
data class CurriculumBasketItem(
    val code: String = "",
    val name: String = "",
    val credits: Int = 0,
    val type: String? = null
)

@Serializable
data class CurriculumBasket(
    val title: String = "",
    val credits: Int = 0,
    val items: List<CurriculumBasketItem> = emptyList()
)

@Serializable
data class CategoryDetail(
    val code: String = "",
    val name: String = "",
    val baskets: List<CurriculumBasket> = emptyList()
)

@Serializable
data class CurriculumRes(
    val success: Boolean = false,
    val message: String? = null,
    val error: String? = null,
    val title: String = "",
    val totalCredits: Int = 0,
    val categories: List<CurriculumCategory> = emptyList(),
    val details: List<CategoryDetail> = emptyList()
)

@Serializable
data class EptScheduleRes(
    val success: Boolean = true,
    @Contextual
    val tables: List<JsonElement>? = null,
    val error: String? = null
)

@Serializable
data class RegistrationScheduleRes(
    val success: Boolean = true,
    @Contextual
    val selectOptions: Map<String, JsonElement>? = null,
    @Contextual
    val tables: List<JsonElement>? = null,
    @Contextual
    val keyValuePairs: Map<String, JsonElement>? = null,
    @Contextual
    val formFields: Map<String, JsonElement>? = null,
    @Contextual
    val hiddenFields: Map<String, JsonElement>? = null,
    @Contextual
    val messages: Map<String, JsonElement>? = null,
    val error: String? = null
)

// FFCS course-registration slot parsed from the /registration-schedule response
@Serializable
data class FfcsRegistrationInfo(
    val userName: String = "",
    val date: String = "",
    val fromTime: String = "",
    val toTime: String = ""
)

@Serializable
data class UniversityDayRes(
    val success: Boolean = true,
    val title: String = "",
    @Contextual
    val selectOptions: Map<String, JsonElement>? = null,
    @Contextual
    val tables: List<JsonElement>? = null,
    @Contextual
    val keyValuePairs: Map<String, JsonElement>? = null,
    @Contextual
    val formFields: Map<String, JsonElement>? = null,
    @Contextual
    val hiddenFields: Map<String, JsonElement>? = null,
    @Contextual
    val messages: Map<String, JsonElement>? = null,
    val error: String? = null
)

@Serializable
data class BankInfoRes(
    val success: Boolean = true,
    @Contextual
    val bankDetails: JsonElement? = null,
    @Contextual
    val fields: Map<String, JsonElement>? = null,
    val error: String? = null
)

@Serializable
data class DayboarderRes(
    val success: Boolean = true,
    @Contextual
    val fields: Map<String, JsonElement>? = null,
    val error: String? = null
)

/** Response of `POST /api/bulk`: `{ success, results: { <proxy name>: <parsed VTOP html> } }`. */
@Serializable
data class BulkResponse(
    val success: Boolean = true,
    val error: String? = null,
    val results: Map<String, JsonElement> = emptyMap()
)

@Serializable
data class ApaarIdRes(
    val success: Boolean = true,
    val hasApaar: Boolean = false,
    @Contextual
    val formFields: Map<String, JsonElement>? = null,
    @Contextual
    val keyValuePairs: Map<String, JsonElement>? = null,
    @Contextual
    val tables: List<JsonElement>? = null,
    val error: String? = null
)
