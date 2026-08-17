package com.amazecc.app.shared.state

import com.amazecc.app.shared.model.AllGradesRes
import com.amazecc.app.shared.model.ArrearResponse
import com.amazecc.app.shared.model.AssessmentItem
import com.amazecc.app.shared.model.AttendanceLog
import com.amazecc.app.shared.model.BusesRes
import com.amazecc.app.shared.model.CabShareHub
import com.amazecc.app.shared.model.CabShareUser
import com.amazecc.app.shared.model.CalendarRes
import com.amazecc.app.shared.model.CalendarsListRes
import com.amazecc.app.shared.model.CircularsRes
import com.amazecc.app.shared.model.ClubsRes
import com.amazecc.app.shared.model.CurriculumRes
import com.amazecc.app.shared.model.EventHubRegisteredEventsRes
import com.amazecc.app.shared.model.EventHubRes
import com.amazecc.app.shared.model.ExamItem
import com.amazecc.app.shared.model.FfcsRegistrationInfo
import com.amazecc.app.shared.model.GradeBreakdown
import com.amazecc.app.shared.model.GradeRange
import com.amazecc.app.shared.model.HomeworkTask
import com.amazecc.app.shared.model.HostelDetails
import com.amazecc.app.shared.model.LMSRes
import com.amazecc.app.shared.model.LaundryRes
import com.amazecc.app.shared.model.LibraryRes
import com.amazecc.app.shared.model.MessMenuRes
import com.amazecc.app.shared.model.MoodleRes
import com.amazecc.app.shared.model.PaymentsRes
import com.amazecc.app.shared.model.QcmViewRes
import com.amazecc.app.shared.model.TransportDataRes
import kotlinx.serialization.Serializable

/**
 * Sanitised, deciphered models stored in [AppDataStore].
 *
 * These are the "processed" shapes the UI consumes. Raw transport payloads
 * (JsonElement blobs, scraped table rows) never reach these models — the
 * store sanitizers transform them at the store boundary.
 */

/** One row of the VTOP timetable's course-info table (raw transport shape). */
@Serializable
data class TimetableCourseInfo(
    val slNo: String? = null,
    val course: String? = null,
    val courseCode: String? = null,
    val LTPJC: String? = null,
    val category: String? = null,
    val classId: String? = null,
    val slotVenue: String? = null,
    val facultyDetails: String? = null
)

/**
 * One occurrence of a course on the weekly timetable: a specific slot on a
 * specific day with its resolved time range. Derived from [TimetableCourseInfo]
 * (falling back to attendance slot names), merged with the attendance
 * percentage of the course.
 */
@Serializable
data class TimetableSlot(
    val day: String? = null,
    val slotName: String? = null,
    val time: String? = null,
    val courseCode: String? = null,
    val courseTitle: String? = null,
    val courseType: String? = null,
    val venue: String? = null,
    val faculty: String? = null,
    val classId: String? = null,
    val category: String? = null,
    val attendancePercentage: Double? = null
)

/** One QCM row with the known fields typed (raw key variants normalised). */
@Serializable
data class StoredQcmRow(
    val qcmNo: String? = null,
    val action: String? = null,
    val suggestions: String? = null,
    val facultyReply: String? = null
)

/** A QCM table deciphered from the raw `data` JsonElement payload. */
@Serializable
data class StoredQcmTable(
    val caption: String? = null,
    val rows: List<StoredQcmRow> = emptyList()
)

// ── Unified academic schema (v2) ──
// One StoredCourse per (semester, courseCode). See docs/features/schemas/.

/** All academic data, one semester map. */
@Serializable
data class AcademicData(
    val semesters: Map<String, SemesterData> = emptyMap()
)

@Serializable
data class SemesterData(
    val semesterId: String = "",
    val semesterName: String? = null,
    val gpa: String? = null,
    val courses: Map<String, StoredCourse> = emptyMap(),
    val exams: List<ExamItem> = emptyList()
)

/** One course in one semester — the single source of truth. */
@Serializable
data class StoredCourse(
    val courseCode: String = "",
    val courseTitle: String = "",
    val courseType: String = "",
    val category: String? = null,
    val credits: String? = null,
    val classId: String? = null,
    val slots: List<String> = emptyList(),
    val venue: String? = null,
    val faculty: String? = null,
    val courseSystem: String? = null,
    val attendance: StoredAttendance? = null,
    val marks: StoredMarks? = null,
    val grade: StoredGrade? = null
)

@Serializable
data class StoredAttendance(
    val attendedClasses: Int = 0,
    val totalClasses: Int = 0,
    val attendancePercentage: String = "",
    val logs: List<AttendanceLog> = emptyList()
)

@Serializable
data class StoredMarks(
    val classNbr: String? = null,
    val assessments: List<AssessmentItem> = emptyList()
)

@Serializable
data class StoredGrade(
    val grandTotal: String? = null,
    val grade: String? = null,
    val details: List<GradeBreakdown>? = null,
    val range: GradeRange? = null
)

/**
 * The single encrypted snapshot of all academic/campus data, persisted under
 * [com.amazecc.app.shared.repository.SettingsManager.CACHE_APP_DATA].
 *
 * Every field holds SANITISED, deciphered data (see [AppSanitizers]) — no raw
 * JSON, no placeholders. Transport responses are fed to [AppDataStore] setters
 * which sanitize at the store boundary. Identity lives separately in UserStore.
 */
@Serializable
data class AppDataSnapshot(
    /** 2 = unified academic schema; 1 = legacy (see SnapshotMigrator). Detection by presence of `academic`. */
    val schemaVersion: Int = 2,
    val academic: AcademicData = AcademicData(),
    val hostelDetails: HostelDetails? = null,
    val messMenu: MessMenuRes? = null,
    val laundrySchedule: LaundryRes? = null,
    val hostelCounselling: ArrearResponse? = null,
    val calendar: CalendarRes? = null,
    val calendarsList: CalendarsListRes? = null,
    val qcmView: QcmViewRes? = null,
    val curriculum: CurriculumRes? = null,
    val payments: PaymentsRes? = null,
    val library: LibraryRes? = null,
    val transportData: TransportDataRes? = null,
    val buses: BusesRes? = null,
    val lms: LMSRes? = null,
    val events: EventHubRes? = null,
    val registeredEvents: EventHubRegisteredEventsRes? = null,
    val clubs: ClubsRes? = null,
    val circulars: CircularsRes? = null,
    val moodleData: MoodleRes? = null,
    val cabShareUser: CabShareUser? = null,
    val cabHubs: List<CabShareHub> = emptyList(),
    val ffcsRegistration: FfcsRegistrationInfo? = null,
    val tasks: List<HomeworkTask> = emptyList()
)
