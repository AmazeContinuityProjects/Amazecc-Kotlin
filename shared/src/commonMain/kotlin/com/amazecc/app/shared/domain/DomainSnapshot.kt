package com.amazecc.app.shared.domain

import com.amazecc.app.shared.model.ApaarIdRes
import com.amazecc.app.shared.model.ArrearResponse
import com.amazecc.app.shared.model.BusesRes
import com.amazecc.app.shared.model.CabShareHub
import com.amazecc.app.shared.model.CabShareUser
import com.amazecc.app.shared.model.CalendarsListRes
import com.amazecc.app.shared.model.CircularsRes
import com.amazecc.app.shared.model.ClubsRes
import com.amazecc.app.shared.model.CurriculumRes
import com.amazecc.app.shared.model.DayboarderRes
import com.amazecc.app.shared.model.EventHubRegisteredEventsRes
import com.amazecc.app.shared.model.EventHubRes
import com.amazecc.app.shared.model.FfcsRegistrationInfo
import com.amazecc.app.shared.model.GradeRange
import com.amazecc.app.shared.model.HostelDetails
import com.amazecc.app.shared.model.HomeworkTask
import com.amazecc.app.shared.model.LMSRes
import com.amazecc.app.shared.model.LaundryRes
import com.amazecc.app.shared.model.LibraryRes
import com.amazecc.app.shared.model.MessMenuRes
import com.amazecc.app.shared.model.MoodleRes
import com.amazecc.app.shared.model.PaymentsRes
import com.amazecc.app.shared.model.QcmViewRes
import com.amazecc.app.shared.model.TransportDataRes
import com.amazecc.app.shared.state.AppDataSnapshot
import com.amazecc.app.shared.state.StoredCourse
import kotlinx.serialization.Serializable

/**
 * The canonical, persisted shape of everything the app knows.
 *
 * Replaces [AppDataSnapshot] as the storage format (`schemaVersion` 3). Two rules make the
 * difference between this and the thing it replaces:
 *
 *  1. **Grouped by concern, not by module.** A screen that needs "what classes do I have today"
 *     reads [schedule] and [academics] without knowing whether attendance came from a sweep or a
 *     manual refresh. The old snapshot's flat field-per-module layout is what made every
 *     transplanted screen grow its own merging logic.
 *
 *  2. **No transport type in the academics/spine groups.** Attendance, marks, grades, exams and
 *     curriculum are modelled here rather than kept as `*Res` DTOs, so a parser fix lands once
 *     instead of rippling into screens.
 *
 * Groups still carrying a transport type are marked `PENDING` and list what is outstanding. That
 * is deliberate: copying twenty DTO shapes blind would only re-create the god object under a new
 * name, and every one of them would need verifying against a real page anyway. Each group gets
 * migrated when its screen is ready to consume it.
 *
 * Identity is deliberately *not* here - it lives in `UserStore` because it is login-scoped rather
 * than sync-scoped, and merging the two would put a nullable copy in every projection.
 *
 * See `docs/sep-30-2026/data-translation-layer-design.md`.
 */
@Serializable
data class DomainSnapshot(
    val schemaVersion: Int = SCHEMA_VERSION,
    val academics: Academics = Academics(),
    val schedule: Schedule = Schedule(),
    val campus: Campus = Campus(),
    val engagement: Engagement = Engagement(),
    val services: Services = Services(),
) {
    companion object {
        const val SCHEMA_VERSION = 3
    }
}

// ── academics: the spine ──────────────────────────────────────────────────────

/** Semesters and the courses inside them. The one place academic facts live. */
@Serializable
data class Academics(
    val semesters: Map<String, Semester> = emptyMap(),
    /** Semester id the user last selected, so a cold start restores the same view. */
    val selectedSemesterId: String? = null,
    /**
     * The official programme structure, kept verbatim.
     *
     * Credits derived from it are folded into [Course.credits], but the raw response is retained
     * because it carries the basket/category grouping the curriculum screen renders and that no
     * per-course field can express.
     */
    val curriculum: CurriculumRes? = null,
)

/**
 * One semester.
 *
 * [courses] is keyed by bare course code (`BACSE106`), which is what makes an embedded
 * ETH/ELA pair collapse to a single entry instead of appearing twice.
 */
@Serializable
data class Semester(
    val id: String = "",
    val name: String? = null,
    val gpa: String? = null,
    val courses: Map<String, Course> = emptyMap(),
)

/**
 * One course in one semester - the grain the whole academic section agrees on.
 *
 * Attendance, marks and grade hang off the course rather than sitting in a parallel collection,
 * so "this course's attendance" cannot disagree with "this course's credits".
 */
@Serializable
data class Course(
    val code: String = "",
    val title: String = "",
    /**
     * The real VTOP vocabulary, verbatim: `Embedded Theory`, `Embedded Lab`, `Theory Only`,
     * `Lab Only`, `Soft Skill`. Normalising this earlier is what silently broke the credit-weighted
     * merge - see `docs/sep-30-2026/vtop-ground-truth-audit.md` §3.
     */
    val type: String = "",
    val category: String? = null,
    /** From curriculum when available (authoritative), else timetable. Null when neither has it. */
    val credits: String? = null,
    val classId: String? = null,
    val slots: List<String> = emptyList(),
    val venue: String? = null,
    val faculty: String? = null,
    /** "LAB"/"THEORY"/"STUDIO" - the component half this course runs as, when it is embedded. */
    val courseSystem: String? = null,
    val attendance: Attendance? = null,
    val marks: Marks? = null,
    val grade: Grade? = null,
    /** Curriculum baskets this course belongs to, for the curriculum screen. */
    val baskets: List<BasketRef> = emptyList(),
)

@Serializable
data class BasketRef(
    val categoryCode: String = "",
    val categoryName: String = "",
    val basketTitle: String = "",
)

@Serializable
data class Attendance(
    val attended: Int = 0,
    val total: Int = 0,
    /** VTOP sends this pre-rendered ("89"); kept as text so we never disagree with the page. */
    val percentage: String = "",
    val logs: List<AttendanceDay> = emptyList(),
)

@Serializable
data class AttendanceDay(
    val date: String = "",
    val status: String = "",
)

@Serializable
data class Marks(
    val classNbr: String? = null,
    val assessments: List<Assessment> = emptyList(),
    /**
     * Credit-weighted total, or null for a course that is not an embedded pair.
     * Computed once during ingest by [com.amazecc.app.shared.state.AcademicMerge] - never by a
     * screen, which is what previously let a 3-credit theory half and 2-credit lab half be summed
     * naively.
     */
    val total: Double? = null,
    val maxTotal: Double? = null,
    val mergedFrom: String? = null,
)

@Serializable
data class Assessment(
    val name: String = "",
    val score: String = "",
    val maxMark: String = "",
    /** The weighted contribution VTOP computed, e.g. "9.5". */
    val weightage: String = "",
    val weightagePercent: String = "",
    val status: String = "",
    /** "ETH" / "ELA" - which half of an embedded pair this assessment belongs to. */
    val component: String? = null,
)

@Serializable
data class Grade(
    val letter: String? = null,
    val grandTotal: String? = null,
    val details: List<GradeComponent> = emptyList(),
    val range: GradeBands? = null,
)

@Serializable
data class GradeComponent(
    val name: String = "",
    val score: String = "",
    val maxMark: String = "",
    val weightagePercent: String = "",
    /** "Present" / "Absent" / "Fail" - present on the page, so keeping it avoids a re-fetch. */
    val status: String = "",
    /** The weighted contribution, e.g. "9.5". Distinct from [score], the raw mark. */
    val weightage: String = "",
)

/**
 * The letter-grade bands for one course.
 *
 * Kept structured rather than pre-rendered: a screen that wants a single "S 90 / A 80" line can
 * join it, but a screen that wants to test a total against a band cannot do that from a string.
 */
@Serializable
data class GradeBands(
    val s: String = "",
    val a: String = "",
    val b: String = "",
    val c: String = "",
    val d: String = "",
    val e: String = "",
    val f: String = "",
)

// ── schedule ──────────────────────────────────────────────────────────────────

@Serializable
data class Schedule(
    val exams: List<Exam> = emptyList(),
    val calendar: List<CalendarMonth> = emptyList(),
    /** Every course calendar the user can pick between, not just the selected one. */
    val calendarsList: CalendarsListRes? = null,
)

@Serializable
data class Exam(
    val courseCode: String = "",
    val courseTitle: String = "",
    /** The VTOP class number ("CH2026270102069"), needed to tie an exam to its course section. */
    val classId: String = "",
    val slot: String = "",
    val date: String = "",
    val session: String = "",
    val reportingTime: String = "",
    val time: String = "",
    val venue: String = "",
    val seatLocation: String = "",
    val seatNo: String = "",
    /**
     * The semester this exam belongs to - the bucket key for [Projections.exams].
     *
     * Named for what it is. VTOP also prints a *section* header ("End Semester Practical") above
     * each block, and conflating the two is how an exam list silently renders empty: the projection
     * filters on this value, so it must be the semester id and nothing else.
     */
    val semesterId: String = "",
)

@Serializable
data class CalendarMonth(
    val label: String = "",
    val days: List<CalendarDay> = emptyList(),
)

@Serializable
data class CalendarDay(
    val date: Int = 0,
    val events: List<CalendarEventItem> = emptyList(),
)

@Serializable
data class CalendarEventItem(
    val type: String = "",
    val text: String = "",
    val color: String? = null,
    val category: String = "",
)

// ── not yet migrated ──────────────────────────────────────────────────────────
//
// Each group below still stores a transport DTO verbatim. That is intentional and temporary: the
// persisted format had to become lossless *before* it could change shape, so every field the old
// snapshot carried has a home here even where the home is still a `*Res`. Each DTO is replaced by a
// domain type as its screen is transplanted, so no projection ever has to see a `*Res`.
//
// The field lists below are complete against `AppDataSnapshot`. `SnapshotRoundTripTest` populates
// every one with a distinct sentinel and asserts the whole set survives, so adding a field to the
// old snapshot without adding it here fails the build rather than dropping data on upgrade.

/**
 * PENDING: hostel, mess, laundry, transport, library, fees, cab share and FFCS.
 *
 * Outstanding: replace each with a domain type as its screen is transplanted.
 */
@Serializable
data class Campus(
    val messMenu: MessMenuRes? = null,
    val hostel: HostelDetails? = null,
    val laundry: LaundryRes? = null,
    val counselling: ArrearResponse? = null,
    val library: LibraryRes? = null,
    val payments: PaymentsRes? = null,
    val transport: TransportDataRes? = null,
    val buses: BusesRes? = null,
    val cabShareUser: CabShareUser? = null,
    val cabHubs: List<CabShareHub> = emptyList(),
    val ffcsRegistration: FfcsRegistrationInfo? = null,
)

/** PENDING: events, registered events, clubs and circulars. */
@Serializable
data class Engagement(
    val events: EventHubRes? = null,
    val registeredEvents: EventHubRegisteredEventsRes? = null,
    val clubs: ClubsRes? = null,
    val circulars: CircularsRes? = null,
)

/**
 * PENDING: LMS, Moodle, QCM, tasks, dayboarder and APAAR.
 *
 * `lms` was on this list because its DTO and the server's payload disagreed. That is now resolved -
 * `LMSAssignment` matches a live Moodle login, and `VtopLms` scrapes it on-device. See
 * `docs/sep-30-2026/vtop-ground-truth-audit.md` §13.
 */
@Serializable
data class Services(
    val lms: LMSRes? = null,
    val moodle: MoodleRes? = null,
    val qcm: QcmViewRes? = null,
    /** User-authored and LMS-imported homework. */
    val tasks: List<HomeworkTask> = emptyList(),
    val dayboarder: DayboarderRes? = null,
    val apaar: ApaarIdRes? = null,
)
