package com.amazecc.app.shared.domain.overhaul

import androidx.compose.runtime.Immutable
import com.amazecc.app.shared.domain.Attendance
import com.amazecc.app.shared.domain.EmbeddedCourseHalf
import com.amazecc.app.shared.domain.FreeClassroomBlockSummary
import com.amazecc.app.shared.domain.FreeClassroomPeriodOption
import com.amazecc.app.shared.domain.FreeClassroomRoomRow
import kotlinx.datetime.LocalDate

/**
 * Heavily-typed immutable state models for AmazeCC's M3 Expressive overhaul.
 * Every screen accepts an immutable state instance and emits user action events.
 * Zero references to Android SDK, Ktor, or singletons.
 */

// ==========================================
// 1. HOME SCREEN IMMUTABLE STATE
// ==========================================

data class M3EUserIdentityState(
    val name: String,
    val regNumber: String,
    val programme: String,
    val school: String,
    val campus: String,
    val avatarUrl: String? = null
)

data class M3EWeekDay(
    val date: LocalDate,
    val dayName: String,     // "Mon", "Tue"
    val dayNumber: Int,      // 29
    val isToday: Boolean,
    val isSelected: Boolean,
    val hasClasses: Boolean,
    val isHoliday: Boolean,
    val examCount: Int = 0,
    val holidayName: String? = null,
    /** An unfinished assignment is due on this date (super-calendar Moodle source). */
    val hasDeadline: Boolean = false,
    /** Published day-order override for this date (e.g. Saturday follows Monday's timetable). */
    val detectedDayOrder: com.amazecc.app.shared.utils.AttendanceDay? = null
)

data class M3EQuickStatsState(
    val attendancePercentage: Double,
    val cgpa: Double,
    val creditsEarned: Int,
    val safeBunksAvailable: Int,
    val attendanceStatusText: String,
    /**
     * Live effective target from AppState (custom > bus 85 > 75). Null only when the host
     * has not threaded it yet; the UI then recovers it from [attendanceStatusText].
     */
    val attendanceTargetPercentage: Float? = null
)

data class M3EClassTimelineItem(
    val courseCode: String,
    val courseTitle: String,
    val slot: String,
    val venue: String,
    val faculty: String,
    val startTime: String,
    val endTime: String,
    val isHappeningNow: Boolean = false,
    val isCompleted: Boolean = false,
    /**
     * Real attendance for the component this class meets as (lab half for a lab slot, theory half
     * for a theory slot). Null when VTOP published none, so the home card hides its badge instead
     * of inventing a number. Built by the host from `M3EHomeAttendance.percentForSlot`.
     */
    val attendancePercentage: Double? = null
)

data class M3EInsightSlide(
    val title: String,
    val subtitle: String,
    val badge: String,
    val actionText: String? = null,
    val priorityColorHex: String? = null
)

/** A real exam paper on the selected home day, shown in place of regular classes. */
data class M3EHomeExamItem(
    val courseCode: String,
    val courseTitle: String,
    val examSeries: String,
    val examTime: String,
    val venue: String,
    val seatNo: String
)

data class M3EHomeState(
    val identity: M3EUserIdentityState,
    val weekDays: List<M3EWeekDay>,
    val selectedDayDate: LocalDate,
    val quickStats: M3EQuickStatsState,
    val todayClasses: List<M3EClassTimelineItem>,
    val todayExams: List<M3EHomeExamItem> = emptyList(),
    val insights: List<M3EInsightSlide>,
    /** Real name of the selected semester from AppState.semesterMap / domain.Semester.name. */
    val semesterName: String = "",
    val isRefreshing: Boolean = false,
    val errorMessage: String? = null
) {
    /** One target for the headline label, its tone and every per-class attendance badge. */
    val attendanceTargetPercentage: Float? get() = quickStats.attendanceTargetPercentage
}

sealed interface M3EHomeAction {
    data class SelectDay(val date: LocalDate) : M3EHomeAction
    data object RefreshData : M3EHomeAction
    data class ClickCourse(val courseCode: String) : M3EHomeAction
    data object OpenAttendanceHub : M3EHomeAction
    data object OpenTimetableHub : M3EHomeAction
    data object OpenAcademicsHub : M3EHomeAction
    data object DismissError : M3EHomeAction
    /** Home's "View Full Timetable" button opens the timetable bottom sheet. */
    data object OpenTimetableSheet : M3EHomeAction
}

// ==========================================
// 2. ATTENDANCE SCREEN IMMUTABLE STATE
// ==========================================

enum class M3ECardTone { GOOD, WARNING, CRITICAL, NEUTRAL }

enum class M3EStatTone { PRIMARY, SECONDARY, TERTIARY, GOOD, WARNING, CRITICAL, NEUTRAL }

data class M3ECourseAttendanceCardState(
    val courseCode: String,
    val courseTitle: String,
    val badge: String, // Web parity: "Theory" / "Lab" / "Embedded" (TH/LO/EMB were the old tokens)
    val attendedClasses: Int,
    val totalClasses: Int,
    val percentage: Double,
    val tone: M3ECardTone,
    val faculty: String,
    val slot: String,
    val bunkSummary: String,
    val isEmbedded: Boolean = false,
    val theoryPct: Double? = null,
    val labPct: Double? = null
)

data class M3EBunkSummary(
    val totalBunkableClasses: Int,
    val criticalCoursesCount: Int,
    val warningCoursesCount: Int,
    val targetPercentage: Float = 75f
)

enum class AttendanceFilter { ALL, SAFE, AT_RISK, EMBEDDED }

data class M3EAttendanceState(
    val semesterName: String,
    val overallPercentage: Double,
    val bunkSummary: M3EBunkSummary,
    val courses: List<M3ECourseAttendanceCardState>,
    val activeFilter: AttendanceFilter = AttendanceFilter.ALL,
    val searchQuery: String = "",
    val isLoading: Boolean = false
)

sealed interface M3EAttendanceAction {
    data class SetFilter(val filter: AttendanceFilter) : M3EAttendanceAction
    data class UpdateSearch(val query: String) : M3EAttendanceAction
    data class CourseClicked(val courseCode: String) : M3EAttendanceAction
    data object Refresh : M3EAttendanceAction
    /** D-138: header entry point into the separate attendance (bunk) predictor. */
    data object OpenPredictor : M3EAttendanceAction
}

// ==========================================
// 3. NAVIGATION CHROME IMMUTABLE STATE
// ==========================================

enum class M3EDestination(val label: String) {
    HOME("Home"),
    ATTENDANCE("Attendance"),
    ACADEMICS("Academics"),
    MORE("More")
}

data class M3ENavigationState(
    val currentDestination: M3EDestination,
    val unreadNotificationsCount: Int = 0
)

// ==========================================
// 4. TIMETABLE IMMUTABLE STATE & ACTIONS
// ==========================================

enum class TimetableViewMode(val label: String) {
    SCHEDULE("Day Schedule"),
    VERTICAL("Vertical Grid"),
    HORIZONTAL("Horizontal Grid")
}

data class M3ETimetableSlot(
    val id: String,
    val slot: String,
    val courseCode: String,
    val courseTitle: String,
    val venue: String,
    val faculty: String,
    val startTime: String,
    val endTime: String,
    val isHappeningNow: Boolean = false,
    val attendanceTone: M3ECardTone = M3ECardTone.NEUTRAL,
    val attendancePercentage: Double? = null,
    val isLab: Boolean = false
)

data class M3ETimetableDay(
    val dayName: String,
    val shortName: String,
    val dayNumber: Int,
    val isToday: Boolean,
    val isSelected: Boolean,
    val slots: List<M3ETimetableSlot> = emptyList(),
    val freeHoursCount: Int = 0
)

data class M3ETimetableState(
    val semesterName: String,
    val selectedDayIndex: Int = 0,
    val days: List<M3ETimetableDay>,
    val viewMode: TimetableViewMode = TimetableViewMode.SCHEDULE,
    val totalCredits: Int = 0,
    val totalCourses: Int = 0,
    val isLoading: Boolean = false,
    /** Compact density for the dense grids; opt-in so the default stays readable. */
    val denseGrid: Boolean = false
)

sealed interface M3ETimetableAction {
    data class SelectDay(val dayIndex: Int) : M3ETimetableAction
    data class SetViewMode(val mode: TimetableViewMode) : M3ETimetableAction
    data class SlotClicked(val slot: M3ETimetableSlot) : M3ETimetableAction
    data object Refresh : M3ETimetableAction
    data class SetDenseGrid(val enabled: Boolean) : M3ETimetableAction
}

// ==========================================
// 5. ACADEMICS & MARKS IMMUTABLE STATE & ACTIONS
// ==========================================

enum class AcademicCourseFilter(val label: String) {
    ALL("All"),
    THEORY("Theory"),
    LAB("Lab"),
    EMBEDDED("Embedded")
}

data class M3EAssessmentItem(
    val title: String,
    val maxMarks: Double,
    val scoredMarks: Double,
    val weightagePercent: Double,
    val weightageMarks: Double,
    val status: String = ""
)

data class M3EAcademicCourse(
    val courseCode: String,
    val courseTitle: String,
    val credits: Int?,
    val courseType: String,
    val faculty: String,
    val grade: String? = null,
    val internalScored: Double = 0.0,
    val internalMax: Double = 0.0,
    val assessments: List<M3EAssessmentItem> = emptyList(),
    val attendancePct: Double? = null,
    /** Semester this course belongs to, for the unified hub's cross-semester grouping. */
    val semesterLabel: String = "",
    val semesterId: String = ""
)

data class M3EAcademicSummary(
    val cgpa: Double,
    val creditsEarned: Int,
    val creditsRegistered: Int,
    val rankOrStanding: String? = null
)

data class M3EAcademicsState(
    val semesterName: String,
    val availableSemesters: List<String> = emptyList(),
    val summary: M3EAcademicSummary,
    val courses: List<M3EAcademicCourse>,
    val selectedFilter: AcademicCourseFilter = AcademicCourseFilter.ALL,
    val searchQuery: String = "",
    val expandedCourseCode: String? = null,
    /** Unified hub mode: group courses across semesters instead of the selected one only. */
    val showAllSemesters: Boolean = false,
    val pastSemestersSynced: Boolean = false,
    val isLoading: Boolean = false,
    val expandedCourseSemesterId: String? = null
)

sealed interface M3EAcademicsAction {
    data class SelectSemester(val semester: String) : M3EAcademicsAction
    data class SetFilter(val filter: AcademicCourseFilter) : M3EAcademicsAction
    data class UpdateSearch(val query: String) : M3EAcademicsAction
    data class ToggleExpandCourse(val courseCode: String, val semesterId: String = "") : M3EAcademicsAction
    data object Refresh : M3EAcademicsAction
    data object ToggleAllSemesters : M3EAcademicsAction
    data object RefreshPastSemesters : M3EAcademicsAction
}

// ==========================================
// 6. COURSE DETAIL IMMUTABLE STATE & ACTIONS
// ==========================================

enum class CourseDetailTab(val label: String) {
    OVERVIEW("Overview"),
    MARKS("Marks"),
    SYLLABUS("Syllabus"),
    PREDICTOR("Predictor")
}

data class M3ECourseSyllabusUnit(
    val unitNumber: Int,
    val title: String,
    val description: String,
    val hours: Int = 0
)

data class M3ECourseGradeTerm(
    val semester: String,
    val grade: String,
    val total: String?,
)

data class M3ECourseDetailState(
    val courseCode: String,
    val courseTitle: String,
    val credits: Int?,
    val courseType: String,
    val slot: String,
    val venue: String,
    val facultyName: String,
    val facultyEmail: String? = null,
    val facultyCabin: String? = null,
    val facultyDesignation: String? = null,
    val attendedClasses: Int = 0,
    val totalClasses: Int = 0,
    val attendancePct: Double = 0.0,
    val bunkMarginSummary: String = "",
    val tone: M3ECardTone = M3ECardTone.NEUTRAL,
    val activeTab: CourseDetailTab = CourseDetailTab.OVERVIEW,
    val internalScored: Double = 0.0,
    val internalMax: Double = 0.0,
    val classAverage: Double? = null,
    val classMax: Double? = null,
    val assessments: List<M3EAssessmentItem> = emptyList(),
    val syllabusUnits: List<M3ECourseSyllabusUnit> = emptyList(),
    val targetGrade: String = "A",
    val requiredFinalScore: Double? = null,
    /** Raw theory/lab components of an embedded course; empty for a standalone course. */
    val halves: List<EmbeddedCourseHalf> = emptyList(),
    /** The live effective attendance target from the host, not a hardcoded per-course guess. */
    val attendanceTarget: Float = 75f,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val semesterId: String? = null,
    val isCourseAvailable: Boolean = true,
    val publishedGrade: String? = null,
    val pendingTaskCount: Int? = null,
    val standaloneAttendance: Attendance? = null,
    val courseSlots: List<String> = emptyList(),
    val gradeHistory: List<M3ECourseGradeTerm> = emptyList(),
)

sealed interface M3ECourseDetailAction {
    data class SelectTab(val tab: CourseDetailTab) : M3ECourseDetailAction
    data class SetTargetGrade(val grade: String) : M3ECourseDetailAction
    data object DownloadSyllabus : M3ECourseDetailAction
    data object OpenQBank : M3ECourseDetailAction
    data object Refresh : M3ECourseDetailAction
    data object Back : M3ECourseDetailAction
}

// ==========================================
// 7. FACULTY DIRECTORY IMMUTABLE STATE & ACTIONS
// ==========================================

data class M3EFacultyTimetableSlot(
    val slotCode: String,       // e.g. "A1", "B2+TB2", "L31+L32"
    val courseCode: String,     // e.g. "CSE3001"
    val courseTitle: String,    // e.g. "Internet and Web Programming"
    val roomNumber: String,     // e.g. "SJT-512"
    val dayOfWeek: String       // e.g. "MON", "TUE", "WED", "THU", "FRI", "SAT"
)

data class M3EFacultyMember(
    val id: String,
    val name: String,
    val designation: String,
    val school: String,
    val cabin: String,
    val email: String,
    val intercom: String? = null,
    val rating: Double? = null,
    val coursesTaught: List<String> = emptyList(),
    val isMyFaculty: Boolean = false,
    val timetable: List<M3EFacultyTimetableSlot> = emptyList(),
    val isCurrentlyTeaching: Boolean = false,
    val currentSlotInfo: String? = null  // "Now: CSE3001 in SJT-512"
)

data class M3EFacultyState(
    val searchQuery: String = "",
    val selectedSchool: String = "All",
    val availableSchools: List<String> = listOf("All", "SCOPE", "SITE", "SENSE", "SELECT", "SMEC", "SSL", "SAS"),
    val facultyList: List<M3EFacultyMember>,
    val filteredFaculty: List<M3EFacultyMember>,
    val expandedFacultyId: String? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

sealed interface M3EFacultyAction {
    data class UpdateSearch(val query: String) : M3EFacultyAction
    data class SelectSchool(val school: String) : M3EFacultyAction
    data class ToggleExpand(val facultyId: String) : M3EFacultyAction
    data class CopyEmail(val email: String) : M3EFacultyAction
    data class ToggleShowTimetable(val facultyId: String) : M3EFacultyAction
    data object Refresh : M3EFacultyAction
}

// ==========================================
// 8. CURRICULUM IMMUTABLE STATE & ACTIONS
// ==========================================

enum class CurriculumBasket(val label: String, val code: String) {
    ALL("All", "ALL"),
    PROGRAM_CORE("Program Core", "PC"),
    PROGRAM_ELECTIVE("Program Elective", "PE"),
    UNIVERSITY_CORE("University Core", "UC"),
    UNIVERSITY_ELECTIVE("University Elective", "UE")
}

enum class CourseCompletionStatus(val label: String) {
    COMPLETED("Completed"),
    IN_PROGRESS("In Progress"),
    NOT_STARTED("Not Started")
}

data class M3ECurriculumCourse(
    val courseCode: String,
    val courseTitle: String,
    val credits: Int,
    val basket: CurriculumBasket,
    val courseType: String,
    val status: CourseCompletionStatus,
    val grade: String? = null
)

data class M3EBasketProgress(
    val basket: CurriculumBasket,
    val creditsCompleted: Int,
    val creditsRequired: Int
)

/**
 * One real credit-category row, built by `domain.buildCategoryRows` from the `/curriculum`
 * payload. Kept alongside the coarse basket chips so the elective/special baskets (Foreign
 * Language, HSM Elective, Extracurricular Activities) stay visible instead of being folded into
 * "All" and lost.
 */
data class M3ECurriculumCategory(
    val key: String,
    val code: String?,
    val title: String,
    val earned: Float,
    val required: Float,
    val ongoing: Float,
    val isSpecial: Boolean,
    val courseCount: Int
)

data class M3ECurriculumState(
    val programmeName: String,
    val totalCreditsCompleted: Int,
    val totalCreditsRequired: Int,
    val baskets: List<M3EBasketProgress>,
    val selectedBasket: CurriculumBasket = CurriculumBasket.ALL,
    val courses: List<M3ECurriculumCourse>,
    val filteredCourses: List<M3ECurriculumCourse>,
    val searchQuery: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    /** Data-driven category rows; empty until the curriculum payload has been synced. */
    val categoryRows: List<M3ECurriculumCategory> = emptyList(),
    /** "Ready" or the "N sem" estimate derived from remaining credits. */
    val expectedLabel: String? = null,
    /** Result of the last syllabus/curriculum save, surfaced once by the stateless screen. */
    val downloadStatus: String? = null
)

sealed interface M3ECurriculumAction {
    data class SelectBasket(val basket: CurriculumBasket) : M3ECurriculumAction
    data class UpdateSearch(val query: String) : M3ECurriculumAction
    data object DownloadCurriculum : M3ECurriculumAction
    /** Per-course syllabus download, from the web page's per-item SyllabusButton. */
    data class DownloadSyllabus(val courseCode: String) : M3ECurriculumAction
    data object Refresh : M3ECurriculumAction
}

// ==========================================
// 9. MARKS PREDICTOR IMMUTABLE STATE & ACTIONS
// ==========================================

data class M3ECoursePredictionItem(
    val courseCode: String,
    val courseTitle: String,
    val credits: Int,
    val internalScored: Double,
    val internalMax: Double,
    val selectedGrade: String,
    val requiredFatMark: Double?,
    val tone: M3ECardTone
)

data class M3EMarksPredictorState(
    val predictedGpa: Double,
    val totalCreditsSimulated: Int,
    val courses: List<M3ECoursePredictionItem>,
    val selectedCourseCode: String? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

sealed interface M3EMarksPredictorAction {
    data class SetCourseGrade(val courseCode: String, val grade: String) : M3EMarksPredictorAction
    data class SelectCourse(val courseCode: String) : M3EMarksPredictorAction
    data object ResetAll : M3EMarksPredictorAction
    data object Refresh : M3EMarksPredictorAction
}

// ==========================================
// 10. EXAM SCHEDULE IMMUTABLE STATE & ACTIONS
// ==========================================

enum class ExamSeries(val label: String) {
    ALL("All Exams"),
    CAT1("CAT-1"),
    CAT2("CAT-2"),
    FAT("FAT / Final")
}

/**
 * Time-of-day aware status of a paper. Ported from the web app's `ExamState`: an exam whose end
 * time has passed is "completed" even on its own calendar day, so a 12:30 PM CAT is done by 3 PM
 * rather than at midnight.
 */
enum class ExamStatus(val label: String) {
    UPCOMING("Upcoming"),
    TODAY("Today"),
    COMPLETED("Completed")
}

/** Counts behind the schedule summary strip. */
data class M3EExamStatusCounts(
    val upcoming: Int = 0,
    val today: Int = 0,
    val completed: Int = 0
)

/** One series section of the grouped exam timeline. */
data class M3EExamSeriesGroup(
    val series: ExamSeries,
    val label: String,
    val entries: List<M3EExamEntry>
)

data class M3EExamEntry(
    val id: String,
    val courseCode: String,
    val courseTitle: String,
    val examSeries: ExamSeries,
    val date: String,
    val day: String,
    val session: String,
    val slot: String,
    val reportingTime: String,
    val examTime: String,
    val venue: String,
    val seatNumber: String,
    val daysRemaining: Int = 0,
    val isHappeningSoon: Boolean = false,
    // Derived display data. Defaults keep every legacy constructor (bridge, mock previews,
    // existing tests) compiling unchanged; the host fills these through M3EExamScheduleComputations.
    val status: ExamStatus = ExamStatus.UPCOMING,
    val countdownLabel: String = "",
    val seatLocation: String = "",
    val venueDisplay: String = ""
)

data class M3EExamScheduleState(
    val semesterName: String,
    val selectedSeries: ExamSeries = ExamSeries.ALL,
    val nextUpcomingExam: M3EExamEntry? = null,
    val exams: List<M3EExamEntry>,
    val filteredExams: List<M3EExamEntry>,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    // --- richer M3E UX ---
    /** Timeline sections, already filtered and sorted by the host. */
    val groupedEntries: List<M3EExamSeriesGroup> = emptyList(),
    /** Data-driven filter options from the `schedule` map keys; ALL is always first. */
    val availableSeries: List<ExamSeries> = ExamSeries.entries.toList(),
    /** External hall ticket link. Null today (the VTOP payload exposes no such field). */
    val hallTicketUrl: String? = null,
    val statusCounts: M3EExamStatusCounts = M3EExamStatusCounts()
)

sealed interface M3EExamScheduleAction {
    data class SelectSeries(val series: ExamSeries) : M3EExamScheduleAction
    data class ExamClicked(val exam: M3EExamEntry) : M3EExamScheduleAction
    data object ExportCalendar : M3EExamScheduleAction
    data object Refresh : M3EExamScheduleAction
    /** Emitted only when the payload actually exposes a hall-ticket URL. */
    data class OpenHallTicket(val url: String) : M3EExamScheduleAction
}

// ==========================================
// 11. GRADES HISTORY IMMUTABLE STATE & ACTIONS
// ==========================================

data class M3ESemesterGradeCourse(
    val courseCode: String,
    val courseTitle: String,
    val credits: Int,
    val grade: String,
    val gradePoints: Int,
    val courseType: String
)

data class M3ESemesterGradeRecord(
    val semesterId: String,
    val semesterName: String,
    val sgpa: Double,
    val cgpaAtEnd: Double,
    val creditsEarned: Int,
    val courses: List<M3ESemesterGradeCourse>
)

/** Which of the three pages of the grade-history flow is currently on screen. */
enum class M3EGradeScreen { OVERVIEW, INSIGHTS, COURSE }

/**
 * Semantic grade hue, mirroring the six tones the web app's `ToneBadge` uses:
 * S amber, A green, B blue, C cyan, D violet, E/F red, N/P grey.
 */
enum class M3EGradeTone { AMBER, GREEN, BLUE, CYAN, VIOLET, RED, GREY }

/** Mini GPA-bar hue: no bar at zero, success >= 9, primary >= 8, warning otherwise. */
enum class M3EGradePointTone { NONE, SUCCESS, PRIMARY, WARNING }

@androidx.compose.runtime.Immutable
data class M3EGradeCourseRow(
    val courseCode: String,
    val courseTitle: String,
    /** Display-ready, e.g. "92.5%" or "—%". */
    val grandTotal: String,
    /** Parsed score, null when VTOP published none. */
    val score: Double?,
    val grade: String,
    val tone: M3EGradeTone,
    val isEmbedded: Boolean,
    val courseType: String = ""
)

@androidx.compose.runtime.Immutable
data class M3EGradeTermOption(
    val termId: String,
    val label: String,
    val shortLabel: String = label
)

@androidx.compose.runtime.Immutable
data class M3EGradeTermRow(
    val termId: String,
    val label: String,
    val gpa: Double,
    val courseCount: Int,
    val scoredCount: Int,
    val avgScore: Double,
    val pointTone: M3EGradePointTone?
)

@androidx.compose.runtime.Immutable
data class M3EGradeDistributionRow(
    val grade: String,
    val count: Int,
    /** Percentage of graded courses, 0-100. */
    val share: Double,
    val tone: M3EGradeTone
)

@androidx.compose.runtime.Immutable
data class M3EGradeTypeAverage(
    val type: String,
    val avg: Double,
    val count: Int
)

@androidx.compose.runtime.Immutable
data class M3EGradeTermScore(
    val termId: String,
    val label: String,
    val avgScore: Double,
    val scoredCount: Int
)

@androidx.compose.runtime.Immutable
data class M3EGradeInsightSlide(
    val id: String,
    val label: String,
    val value: String,
    val sub: String,
    val badge: String,
    val tone: M3EGradeTone
)

@androidx.compose.runtime.Immutable
data class M3EGradeBreakdownRow(
    val component: String,
    val weightagePercent: String,
    val scoredMark: String,
    val maxMark: String,
    /** Parsed weightage, used only for heaviest-first ordering. */
    val weightage: Double = 0.0,
    /**
     * The "Mark Title" table VTOP tagged the row with — "Theory", "Lab", "Component N".
     * Null on the frozen legacy API, in which case the flat breakdown is shown instead.
     */
    val type: String? = null
)

/** One VTOP "Mark Title" table and the assessments published under it. */
@androidx.compose.runtime.Immutable
data class M3EGradeAssessmentGroup(
    val type: String,
    val assessments: List<M3EGradeBreakdownRow>
)

@androidx.compose.runtime.Immutable
data class M3EGradeScaleRow(
    val grade: String,
    val range: String,
    val tone: M3EGradeTone,
    val isYours: Boolean
)

@androidx.compose.runtime.Immutable
data class M3EGradeEmbeddedHalf(
    val label: String,
    val scoreText: String,
    val creditsLabel: String?,
    val assessmentCount: Int,
    val assessments: List<M3EGradeBreakdownRow>
)

@androidx.compose.runtime.Immutable
data class M3EGradeCourseDetail(
    val courseCode: String,
    val courseTitle: String,
    val courseType: String,
    /** Display-ready "<grandTotal>%". */
    val overallText: String,
    val grade: String,
    /** "<points>/10" or "—". */
    val pointsLabel: String,
    val assessmentCount: Int,
    val breakdown: List<M3EGradeBreakdownRow>,
    val gradeScale: List<M3EGradeScaleRow>,
    val embeddedHalves: List<M3EGradeEmbeddedHalf>,
    val isEmbedded: Boolean,
    /** Aggregate credits from the effective-grade row (e.g. "4.0 cr"), never a hidden half. */
    val creditsLabel: String? = null,
    /** Non-null only when VTOP tagged the breakdown rows with their source table. */
    val assessmentGroups: List<M3EGradeAssessmentGroup>? = null,
    /** Shown when the assessment weights do not add up to the published grand total. */
    val reconciliationText: String? = null
)

/**
 * Immutable grade-history state. The first block is the ingestor's historical projection;
 * the second block is the derived, page-ready view consumed by the three M3E pages.
 */
@androidx.compose.runtime.Immutable
data class M3EGradesHistoryState(
    val cumulativeGpa: Double,
    val totalCreditsEarned: Int,
    val totalCreditsAttempted: Int,
    val sGradesCount: Int = 0,
    val aGradesCount: Int = 0,
    val bGradesCount: Int = 0,
    val semesters: List<M3ESemesterGradeRecord>,
    val expandedSemesterId: String? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    // Page-ready view (all values derived from the real grade payload by the host).
    val screen: M3EGradeScreen = M3EGradeScreen.OVERVIEW,
    val selectedCourseCode: String? = null,
    val selectedTermId: String? = null,
    val cumulativeValue: String = "—",
    val cumulativeBadge: String? = null,
    val cumulativeSubtitle: String = "Mean of terms, not weighted",
    val termOptions: List<M3EGradeTermOption> = emptyList(),
    val courseRows: List<M3EGradeCourseRow> = emptyList(),
    val termRows: List<M3EGradeTermRow> = emptyList(),
    val insightSlides: List<M3EGradeInsightSlide> = emptyList(),
    val distributionRows: List<M3EGradeDistributionRow> = emptyList(),
    val typeAverages: List<M3EGradeTypeAverage> = emptyList(),
    val termScores: List<M3EGradeTermScore> = emptyList(),
    val courseDetail: M3EGradeCourseDetail? = null
)

sealed interface M3EGradesHistoryAction {
    /** Kept for older callers; the host treats it as a term selection. */
    data class ToggleExpandSemester(val semesterId: String) : M3EGradesHistoryAction
    data class SelectTerm(val termId: String) : M3EGradesHistoryAction
    data object OpenInsights : M3EGradesHistoryAction
    data class OpenCourse(val courseCode: String) : M3EGradesHistoryAction
    data object BackToOverview : M3EGradesHistoryAction
    data object Refresh : M3EGradesHistoryAction
}

// ==========================================
// 12. STUDENT REPORT IMMUTABLE STATE & ACTIONS
// ==========================================

data class M3EStudentReportState(
    val studentName: String,
    val registrationNumber: String,
    val programme: String,
    val school: String,
    val campus: String,
    val proctorName: String? = null,
    val proctorEmail: String? = null,
    val cgpa: Double,
    val overallAttendancePct: Double,
    val completedCredits: Int,
    val totalRequiredCredits: Int,
    val hasArrears: Boolean = false,
    val arrearsCount: Int = 0,
    val isExportingPdf: Boolean = false,
    val exportSuccessMessage: String? = null,
    val isLoading: Boolean = false,
    val sections: M3EReportSections = M3EReportSections()
)

sealed interface M3EStudentReportAction {
    data object ExportPdfReport : M3EStudentReportAction
    data object ExportJsonData : M3EStudentReportAction
    data object ExportHtmlReport : M3EStudentReportAction
    data object ShareReport : M3EStudentReportAction
    data object Refresh : M3EStudentReportAction
    data class ToggleSection(val key: String, val enabled: Boolean) : M3EStudentReportAction
}

// ==========================================
// 13. HOSTEL & MESS IMMUTABLE STATE & ACTIONS
// ==========================================

data class M3EHostelDetails(
    val blockName: String,
    val roomNumber: String,
    val roomType: String,
    val bedNumber: String,
    val messType: String,
    val messCaterer: String,
    val wardenName: String? = null,
    val wardenPhone: String? = null
)

data class M3EMessMealItem(
    val mealName: String,
    val timing: String,
    val items: List<String>,
    val isCurrent: Boolean = false
)

/** One hostel laundry slot, mapped from the real [com.amazecc.app.shared.model.LaundryRes] rows. */
data class M3ELaundrySlot(
    val date: String,
    val roomNumber: String
)

data class M3ELeaveRecord(
    val leaveId: String,
    val visitPlace: String,
    val reason: String,
    val leaveType: String,
    val fromDate: String,
    val toDate: String,
    val status: String,
    val remarks: String? = null
)

enum class M3EHostelTab(val label: String) {
    MESS_MENU("Mess Menu"),
    LEAVE_HISTORY("Leave History"),
    ROOM_INFO("Room & Warden")
}

data class M3EHostelState(
    val hostelDetails: M3EHostelDetails,
    val selectedTab: M3EHostelTab = M3EHostelTab.MESS_MENU,
    val todayMessMenu: List<M3EMessMealItem> = emptyList(),
    val laundrySchedule: List<M3ELaundrySlot> = emptyList(),
    val leaves: List<M3ELeaveRecord> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

sealed interface M3EHostelAction {
    data class SelectTab(val tab: M3EHostelTab) : M3EHostelAction
    data object ApplyNewLeave : M3EHostelAction
    data object CallWarden : M3EHostelAction
    data object Refresh : M3EHostelAction
}

// ==========================================
// 14. TRANSPORT & BUS ROUTES IMMUTABLE STATE & ACTIONS
// ==========================================

data class M3EBusStop(
    val stopOrder: Int,
    val stopName: String,
    val pickupTime: String
)

data class M3EBusPlacement(
    val dispersalTime: String,
    val zone: String
)

data class M3EBusRoute(
    val id: Int,
    val routeNumber: String,
    val routeName: String,
    val isAc: Boolean,
    val driverName: String,
    val driverPhone: String,
    val whatsappGroup: String? = null,
    val busLocation: String? = null,
    val stops: List<M3EBusStop> = emptyList(),
    val placements: List<M3EBusPlacement> = emptyList()
)

enum class M3ETransportFilter(val label: String) {
    ALL("All Routes"),
    AC("AC Buses"),
    NON_AC("Non-AC"),
    EVENING_5PM("5:00 PM Dispersal"),
    EVENING_6PM("6:00 PM Dispersal")
}

/**
 * The student's real VTOP bus-pass registration (`TransportDataRes` from `/api/transport`),
 * surfaced on the transit hub so the screen reflects the account's actual registration
 * instead of a static placeholder.
 */
data class M3ETransportRegistration(
    val hasRegistration: Boolean = false,
    val registerNumber: String? = null,
    val name: String? = null,
    val branch: String? = null,
    val routeSelected: String? = null,
    val busRouteId: String? = null,
    val qrCode: String? = null,
    /** Live GPS share link from `/vtop/transport/trackMyBus`; null when unavailable. */
    val busUrl: String? = null
)

data class M3ETransportState(
    val routes: List<M3EBusRoute> = emptyList(),
    val filteredRoutes: List<M3EBusRoute> = emptyList(),
    val searchQuery: String = "",
    val activeFilter: M3ETransportFilter = M3ETransportFilter.ALL,
    val expandedRouteId: Int? = null,
    val registration: M3ETransportRegistration? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

sealed interface M3ETransportAction {
    data class SearchQueryChanged(val query: String) : M3ETransportAction
    data class SelectFilter(val filter: M3ETransportFilter) : M3ETransportAction
    data class ToggleExpandRoute(val routeId: Int) : M3ETransportAction
    data class CallDriver(val phoneNumber: String) : M3ETransportAction
    data class OpenWhatsappGroup(val link: String) : M3ETransportAction
    data class OpenBusTracking(val url: String) : M3ETransportAction
    data object Refresh : M3ETransportAction
}

// ==========================================
// 15. CAB SHARE BOARD IMMUTABLE STATE & ACTIONS
// ==========================================

data class M3ECabShareHub(
    val id: String,
    val name: String,
    val subtitle: String
)

enum class M3EGenderPreference(val label: String) {
    ANY("Any Gender"),
    MALE_ONLY("Boys Only"),
    FEMALE_ONLY("Girls Only")
}

data class M3ECabShareRide(
    val tripId: String,
    val creatorName: String,
    val creatorRegNo: String,
    val hubName: String,
    val travelDate: String,
    val preferredTime: String,
    val toleranceHours: Int,
    val availableSeats: Int,
    val totalSeats: Int,
    val genderPreference: M3EGenderPreference,
    val notes: String? = null,
    val creatorPhone: String? = null,
    val isUserCreator: Boolean = false,
    val isJoined: Boolean = false,
    val status: String = "OPEN"
)

enum class M3ECabShareFilter(val label: String) {
    ALL("All Trips"),
    AIRPORT("Airports"),
    RAILWAY("Railway"),
    MY_TRIPS("My Trips")
}

data class M3ECabShareState(
    val hubs: List<M3ECabShareHub> = emptyList(),
    val rides: List<M3ECabShareRide> = emptyList(),
    val activeFilter: M3ECabShareFilter = M3ECabShareFilter.ALL,
    val searchQuery: String = "",
    val isCreateDialogOpen: Boolean = false,
    val isJoiningTripId: String? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

sealed interface M3ECabShareAction {
    data class SelectFilter(val filter: M3ECabShareFilter) : M3ECabShareAction
    data class SearchQueryChanged(val query: String) : M3ECabShareAction
    data object OpenCreateDialog : M3ECabShareAction
    data object CloseCreateDialog : M3ECabShareAction
    data class JoinTrip(val tripId: String) : M3ECabShareAction
    data class ContactCreator(val phoneNumber: String) : M3ECabShareAction
    data object Refresh : M3ECabShareAction
}

// ==========================================
// 16. FREE CLASSROOMS FINDER IMMUTABLE STATE & ACTIONS
// ==========================================

enum class M3EClassroomBuilding(val label: String, val code: String) {
    ALL("All Buildings", "ALL"),
    SJT("Silver Jubilee Tower", "SJT"),
    TT("Technology Tower", "TT"),
    MB("Main Building", "MB"),
    SMV("SMV Building", "SMV"),
    GDN("GDN Block", "GDN"),
    PRP("PRP Block", "PRP")
}

data class M3EClassroomSlot(
    val roomNumber: String,
    val buildingCode: String,
    val floor: Int,
    val capacity: Int,
    val isAirConditioned: Boolean,
    val availabilityStatus: String,
    val isCurrentlyAvailable: Boolean,
    val nextOccupiedTime: String? = null
)

/** Web parity: the Rooms segmented control (All / Rooms / Labs). */
enum class M3EFreeClassroomKindFilter(val label: String) {
    ALL("All"),
    THEORY("Rooms"),
    LAB("Labs")
}

data class M3EFreeClassroomsState(
    val classrooms: List<M3EClassroomSlot> = emptyList(),
    val filteredClassrooms: List<M3EClassroomSlot> = emptyList(),
    val selectedBuilding: M3EClassroomBuilding = M3EClassroomBuilding.ALL,
    val onlyAvailableNow: Boolean = true,
    val searchQuery: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    // ── D-162: real day/period/block derivation (all defaults keep legacy constructors) ──
    /** Day key from [FreeClassroomDay]: MON..SAT. */
    val selectedDay: String = "MON",
    /** Key of [FreeClassroomPeriodOption] currently selected. */
    val selectedPeriodKey: String = "p1",
    /** Period windows, from the canonical timetable grid. */
    val availablePeriodOptions: List<FreeClassroomPeriodOption> = emptyList(),
    val kindFilter: M3EFreeClassroomKindFilter = M3EFreeClassroomKindFilter.ALL,
    /** "All" or a real block name derived from snapshot venues. */
    val blockFilter: String = "All",
    /** Real block counts for the selected day/period; the block is the unit, not the room. */
    val blockSummaries: List<FreeClassroomBlockSummary> = emptyList(),
    /** Venue code of the room whose schedule inspector sheet is open. */
    val selectedRoom: String? = null,
    /** Human label for the real current time, e.g. "Now · Mon 08:00". Blank when unavailable. */
    val nowLabel: String = "",
    /** Real current day/period targets for the "Now" affordance; blank hides it. */
    val nowDayKey: String = "",
    val nowPeriodKey: String = ""
) {
    /** All real rooms behind the current answer, flattened for the inspector lookup. */
    val allDerivedRooms: List<FreeClassroomRoomRow> get() = blockSummaries.flatMap { it.rooms }
}

sealed interface M3EFreeClassroomsAction {
    data class SelectBuilding(val building: M3EClassroomBuilding) : M3EFreeClassroomsAction
    data class ToggleOnlyAvailable(val onlyAvailable: Boolean) : M3EFreeClassroomsAction
    data class SearchQueryChanged(val query: String) : M3EFreeClassroomsAction
    data object Refresh : M3EFreeClassroomsAction
    /** Select a weekday (MON..SAT) for the "When" section. */
    data class SelectDay(val day: String) : M3EFreeClassroomsAction
    /** Select a period window by its key. */
    data class SelectPeriod(val periodKey: String) : M3EFreeClassroomsAction
    /** Move -1 / +1 period from the current selection; clamped by the host. */
    data class StepPeriod(val delta: Int) : M3EFreeClassroomsAction
    data class SetKindFilter(val filter: M3EFreeClassroomKindFilter) : M3EFreeClassroomsAction
    data class SetBlockFilter(val block: String) : M3EFreeClassroomsAction
    /** Open the room inspector for a real venue code. */
    data class SelectRoom(val roomCode: String) : M3EFreeClassroomsAction
    /** Close (or toggle) the room inspector sheet. */
    data object ToggleRoomInspector : M3EFreeClassroomsAction
}

// ==========================================
// 17. EVENT HUB IMMUTABLE STATE & ACTIONS
// ==========================================

enum class M3EEventCategory(val label: String) {
    ALL("All Events"),
    TECHNICAL("Technical"),
    CULTURAL("Cultural"),
    WORKSHOPS("Workshops"),
    HACKATHONS("Hackathons"),
    SPORTS("Sports"),
    MY_REGISTRATIONS("Registered")
}

data class M3EEventItem(
    val id: String,
    val title: String,
    val festName: String,
    val category: M3EEventCategory,
    val date: String,
    val time: String,
    val venue: String,
    val price: String,
    val isFree: Boolean = true,
    val isRegistered: Boolean = false,
    val certificateEligible: Boolean = true,
    val orderId: String? = null
)

/** One real registration from the event-hub profile payload. */
data class M3ERegisteredEventItem(
    val eid: String,
    val title: String,
    val venue: String,
    val date: String? = null,
    val time: String? = null,
    val paymentStatus: String? = null,
    val receiptUrl: String? = null,
    val certificateUrl: String? = null,
    val payNowUrl: String? = null
)

/** Lazy-loaded event preview shown in the event details dialog. */
data class M3EEventPreviewUi(
    val eid: String,
    val title: String = "",
    val imageUrl: String? = null,
    val description: String? = null,
    val metaDetails: Map<String, String> = emptyMap()
)

/** Result of a registration attempt shown in the status dialog. */
data class M3EEventRegistrationUi(
    val status: String,
    val message: String? = null,
    val paymentUrl: String? = null
)

data class M3EEventHubState(
    val events: List<M3EEventItem> = emptyList(),
    val filteredEvents: List<M3EEventItem> = emptyList(),
    val selectedCategory: M3EEventCategory = M3EEventCategory.ALL,
    val searchQuery: String = "",
    val registeredCount: Int = 0,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val registeredEvents: List<M3ERegisteredEventItem> = emptyList(),
    val preview: M3EEventPreviewUi? = null,
    val registrationResult: M3EEventRegistrationUi? = null
)

sealed interface M3EEventHubAction {
    data class SelectCategory(val category: M3EEventCategory) : M3EEventHubAction
    data class SearchQueryChanged(val query: String) : M3EEventHubAction
    data class RegisterEvent(val eventId: String) : M3EEventHubAction
    data class EventClicked(val eventId: String) : M3EEventHubAction
    data object Refresh : M3EEventHubAction
    data object DismissPreview : M3EEventHubAction
    data object DismissRegistrationStatus : M3EEventHubAction
    data class OpenExternalLink(val url: String) : M3EEventHubAction
}

// ==========================================
// 18. CLUB HUB IMMUTABLE STATE & ACTIONS
// ==========================================

enum class M3EClubCategory(val label: String) {
    ALL("All Clubs"),
    TECHNICAL("Tech Chapters"),
    CULTURAL("Cultural & Arts"),
    SOCIAL("Social & Service"),
    SPORTS("Sports Teams"),
    RECRUITING("Recruiting Now")
}

data class M3EClubItem(
    val id: String,
    val name: String,
    val category: M3EClubCategory,
    val type: String, // "CHAPTER" | "CLUB"; blank when the source has no value
    val studentLead: String, // blank when the source has no value
    val facultyCoordinator: String, // blank when the source has no value
    val memberCount: Int, // 0 when the source has no value
    val isRecruiting: Boolean = false,
    val recruitmentDeadline: String? = null,
    val instagramHandle: String? = null,
    val description: String = "",
    val recruitmentLink: String? = null
)

data class M3EClubHubState(
    val clubs: List<M3EClubItem> = emptyList(),
    val filteredClubs: List<M3EClubItem> = emptyList(),
    val selectedCategory: M3EClubCategory = M3EClubCategory.ALL,
    val searchQuery: String = "",
    val expandedClubId: String? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val notice: String? = null
)

sealed interface M3EClubHubAction {
    data class SelectCategory(val category: M3EClubCategory) : M3EClubHubAction
    data class SearchQueryChanged(val query: String) : M3EClubHubAction
    data class ToggleExpandClub(val clubId: String) : M3EClubHubAction
    data class ApplyClub(val clubId: String) : M3EClubHubAction
    data object Refresh : M3EClubHubAction
    data class OpenLink(val url: String) : M3EClubHubAction
    data object DismissNotice : M3EClubHubAction
}

// ==========================================
// 19. TASKS & ASSIGNMENTS IMMUTABLE STATE & ACTIONS
// ==========================================

enum class M3ETaskKind(val label: String) {
    DIGITAL_ASSIGNMENT("Digital Assignment"),
    HOMEWORK("Homework"),
    STUDY("Study Session"),
    TEST("Quiz / CAT Prep"),
    REMINDER("Reminder")
}

enum class M3ETaskStatus(val label: String) {
    PENDING("To Do"),
    IN_PROGRESS("In Progress"),
    DONE("Completed")
}

enum class M3ETasksFilter(val label: String) {
    ALL("All Tasks"),
    PENDING("To Do"),
    IN_PROGRESS("In Progress"),
    ASSIGNMENTS("Assignments"),
    DONE("Completed")
}

data class M3ETaskItem(
    val id: String,
    val title: String,
    val kind: M3ETaskKind,
    val status: M3ETaskStatus,
    val courseCode: String,
    val courseTitle: String,
    val dueDate: String? = null,
    val slotName: String? = null,
    val isOverdue: Boolean = false,
    val moodleUrl: String? = null,
    val notes: String? = null
)

data class M3ETasksState(
    val tasks: List<M3ETaskItem> = emptyList(),
    val filteredTasks: List<M3ETaskItem> = emptyList(),
    val activeFilter: M3ETasksFilter = M3ETasksFilter.ALL,
    val searchQuery: String = "",
    val pendingCount: Int = 0,
    val completedCount: Int = 0,
    val isCreateDialogOpen: Boolean = false,
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

sealed interface M3ETasksAction {
    data class SelectFilter(val filter: M3ETasksFilter) : M3ETasksAction
    data class SearchQueryChanged(val query: String) : M3ETasksAction
    data class ToggleTaskStatus(val taskId: String) : M3ETasksAction
    data class DeleteTask(val taskId: String) : M3ETasksAction
    data object OpenCreateDialog : M3ETasksAction
    data object CloseCreateDialog : M3ETasksAction
    data object Refresh : M3ETasksAction
}

// ==========================================
// 20. PROFILE & DIGITAL ID IMMUTABLE STATE & ACTIONS
// ==========================================

/** Typed residence derived from the whoami payload, with the onboarding choice as fallback. */
enum class M3EResidenceStatus { HOSTELLER, DAYSCHOLAR, UNKNOWN }

/** One `contact` fragment from whoami: an office, its description and an optional email. */
data class M3EContactItem(
    val office: String,
    val description: String,
    val email: String? = null
)

/** One document inside whoami `acknowledgement.documents`. */
data class M3EAcknowledgementDocument(
    val serialNo: String,
    val documentName: String,
    val status: String
)

data class M3EMentorItem(
    val role: String,
    val name: String,
    val designation: String? = null,
    val department: String? = null,
    val email: String? = null,
    val phone: String? = null,
    val cabin: String? = null,
    val photoBase64: String? = null,
    /** Unrecognized label/value rows carried by the whoami official fragment. */
    val extras: List<M3EFieldItem> = emptyList()
)

data class M3EAddressItem(
    val title: String,
    val lines: List<String> = emptyList()
)

data class M3EFieldItem(
    val label: String,
    val value: String
)

data class M3EProfileState(
    val studentName: String,
    val registrationNumber: String,
    val applicationNumber: String,
    val programme: String,
    val school: String,
    val campus: String,
    val dob: String,
    val bloodGroup: String,
    val email: String,
    val phone: String,
    val proctorName: String,
    val proctorEmail: String,
    val proctorCabin: String? = null,
    val hostelBlock: String? = null,
    val hostelRoom: String? = null,
    val cgpa: Double,
    val attendancePercentage: Double,
    val isIdCardFlipped: Boolean = false,
    val isLoading: Boolean = false,
    // Enhanced Parity Fields
    val photoBase64: String? = null,
    val friendlyName: String? = null,
    val isEditingFriendlyName: Boolean = false,
    val proctorPhotoBase64: String? = null,
    val proctorPhone: String? = null,
    val proctorDepartment: String? = null,
    val proctorDesignation: String? = null,
    val bankInfo: String? = null,
    val apaarId: String? = null,
    val dayboarderInfo: String? = null,
    val eptStatus: String? = null,
    val rankAir: String? = null,
    val registrationSchedule: String? = null,
    val vtopUsername: String? = null,
    val vtopPassword: String? = null,
    val showVtopPassword: Boolean = false,
    val mentors: List<M3EMentorItem> = emptyList(),
    val addresses: List<M3EAddressItem> = emptyList(),
    val personalRecords: List<M3EFieldItem> = emptyList(),
    // Consolidated identity (whoami) display models — all default to "absent", never fabricated.
    val residence: M3EResidenceStatus = M3EResidenceStatus.UNKNOWN,
    val contacts: List<M3EContactItem> = emptyList(),
    val acknowledgementDocuments: List<M3EAcknowledgementDocument> = emptyList(),
    /** Mirrors AppState.isBusSubscriber; only meaningful for a dayscholar. */
    val isRegisteredForBus: Boolean = false
)

sealed interface M3EProfileAction {
    data object FlipIdCard : M3EProfileAction
    data object CopyRegNo : M3EProfileAction
    data object ContactProctor : M3EProfileAction
    data object SignOut : M3EProfileAction
    data object Refresh : M3EProfileAction
    data class SaveFriendlyName(val name: String) : M3EProfileAction
    data object ToggleVtopPasswordVisibility : M3EProfileAction
    data class CopyText(val text: String, val label: String) : M3EProfileAction
    data class OpenEmail(val email: String) : M3EProfileAction
    data class OpenPhone(val phone: String) : M3EProfileAction
    /** Residence segmented-control selection; the host persists it via AppState. */
    data class SetResidence(val residence: M3EResidenceStatus) : M3EProfileAction
    /** "Registered for University Transport / Bus" checkbox (dayscholar only). */
    data class SetRegisteredForBus(val enabled: Boolean) : M3EProfileAction
}

// ==========================================
// 21. SETTINGS & PREFERENCES IMMUTABLE STATE & ACTIONS
// ==========================================

enum class M3EThemeMode(val label: String) {
    DARK_OLED("Midnight OLED"),
    LIGHT("Classic Light"),
    SYSTEM_DEFAULT("Follow System")
}

enum class M3EPalettePreset(val label: String) {
    OCEAN_MIDNIGHT("Ocean Blue"),
    CRIMSON_VELVET("Crimson"),
    EMERALD_MINT("Emerald"),
    AMBER_SUNSET("Amber"),
    PURPLE_DUSK("Purple Dusk")
}

@Immutable
data class M3ESettingsSection(
    val id: String,
    val title: String,
    val subtitle: String = "",
    val searchHints: List<String> = emptyList()
) {
    fun matchesQuery(query: String): Boolean {
        if (query.isBlank()) return true
        val q = query.lowercase()
        return title.lowercase().contains(q) ||
            subtitle.lowercase().contains(q) ||
            searchHints.any { it.lowercase().contains(q) }
    }
}

fun allSettingsSections(): List<M3ESettingsSection> = listOf(
    M3ESettingsSection(
        id = "privacy", title = "Privacy",
        subtitle = "Masks fee amounts in Fee Payments; app-wide blur is a separate workstream",
        searchHints = listOf("privacy mode", "blur", "mask", "grades attendance marks fees")
    ),
    M3ESettingsSection(
        id = "pomodoro", title = "Pomodoro",
        subtitle = "Pomodoro focus & study session defaults",
        searchHints = listOf("pomodoro", "focus", "break", "rounds", "study session")
    ),
    M3ESettingsSection(
        id = "theme", title = "Appearance & Theme",
        subtitle = "Theme mode, palette, timetable pill style, privacy mode",
        searchHints = listOf("theme", "palette", "accent", "pill style", "timetable density", "classic zinc glass")
    ),
    M3ESettingsSection(
        id = "storage", title = "Storage & Cache",
        subtitle = "Clear caches, purge data, cache size",
        searchHints = listOf("cache", "storage", "purge", "clear")
    ),
    M3ESettingsSection(
        id = "api_target", title = "API Target",
        subtitle = "Primary, Backup or Custom API endpoint",
        searchHints = listOf("api", "backend", "endpoint", "url", "target", "mount")
    ),
    M3ESettingsSection(
        id = "notifications", title = "Notifications & Reminders",
        subtitle = "Class schedule alerts, class-reminder lead, quiet hours",
        searchHints = listOf("notification", "class reminder", "lead", "quiet hours", "test notification")
    ),
    M3ESettingsSection(
        id = "app_icon", title = "Application Icon",
        subtitle = "Choose the launcher icon displayed on this device",
        searchHints = listOf("app icon", "launcher", "theme", "default", "fire")
    ),
    M3ESettingsSection(
        id = "export_import", title = "Backup / Export Settings",
        subtitle = "Save application preferences to a backup file; restore with “Restore / Import Settings”",
        searchHints = listOf("backup", "export", "import", "restore", "settings file", "json")
    ),
    M3ESettingsSection(
        id = "keyboard", title = "Keyboard Shortcuts",
        subtitle = "Physical-keyboard shortcuts bound in the app",
        searchHints = listOf("keyboard", "shortcuts", "hotkey", "command palette")
    )
)

fun settingsSectionsForQuery(query: String): List<M3ESettingsSection> =
    allSettingsSections().filter { it.matchesQuery(query) }

data class M3ESettingsState(
    val selectedThemeMode: M3EThemeMode = M3EThemeMode.DARK_OLED,
    val selectedPalettePreset: M3EPalettePreset = M3EPalettePreset.OCEAN_MIDNIGHT,
    val honoApiBaseUrl: String = "https://hono.amazecc.com",
    val cacheSizeText: String = "0 B",
    val isDiagnosticsRunning: Boolean = false,
    val diagnosticStatus: String? = null,
    val classAlertsEnabled: Boolean = true,
    val attendanceAlertsEnabled: Boolean = true,
    val examRemindersEnabled: Boolean = true,
    val vtopSource: com.amazecc.app.shared.vtop.VtopSource = com.amazecc.app.shared.vtop.VtopSource.REMOTE,
    val selectedSyncMode: String = "balanced",
    // D-059 — settings depth, migrated from onboarding / web ProfilePage.
    val settingsSearchQuery: String = "",
    val visibleSettingsSections: List<M3ESettingsSection> = emptyList(),
    val privacyModeEnabled: Boolean = false,
    val pomodoroFocusMinutes: Int = 25,
    val pomodoroBreakMinutes: Int = 5,
    val pomodoroRounds: Int = 4,
    val classReminderLeadMinutes: Int = 15,
    val quietHoursEnabled: Boolean = false,
    val quietHoursStart: String = "22:00",
    val quietHoursEnd: String = "07:00",
    val apiTargetName: String = "PRIMARY",
    val appIconTheme: com.amazecc.app.shared.utils.AppIconTheme =
        com.amazecc.app.shared.utils.AppIconTheme.DEFAULT,
    val homePillStyle: com.amazecc.app.shared.state.HomePillStyle =
        com.amazecc.app.shared.state.HomePillStyle.COMPACT,
    /** Non-null when a settings menu is open; its id is an [allSettingsSections] id. */
    val openSettingsMenu: String? = null,
    /** User-selectable nav treatment: "expanding" or "labelled_docked". */
    val navBarStyle: String = "expanding"
)

sealed interface M3ESettingsAction {
    data class SelectThemeMode(val mode: M3EThemeMode) : M3ESettingsAction
    data class SelectPalettePreset(val preset: M3EPalettePreset) : M3ESettingsAction
    data class UpdateHonoApiUrl(val url: String) : M3ESettingsAction
    data object ClearCache : M3ESettingsAction
    data object RunDiagnostics : M3ESettingsAction
    data object OpenGithubRepo : M3ESettingsAction
    data class ToggleClassAlerts(val enabled: Boolean) : M3ESettingsAction
    data class ToggleAttendanceAlerts(val enabled: Boolean) : M3ESettingsAction
    data class ToggleExamReminders(val enabled: Boolean) : M3ESettingsAction
    data object SendTestNotification : M3ESettingsAction
    data class SetVtopSource(val source: com.amazecc.app.shared.vtop.VtopSource) : M3ESettingsAction
    data class SelectSyncMode(val mode: String) : M3ESettingsAction
    data class TriggerManualSync(val mode: String) : M3ESettingsAction
    // D-059 — migrated settings depth.
    data class UpdateSettingsSearch(val query: String) : M3ESettingsAction
    data class TogglePrivacyMode(val enabled: Boolean) : M3ESettingsAction
    data class UpdatePomodoroFocusMinutes(val minutes: Int) : M3ESettingsAction
    data class UpdatePomodoroBreakMinutes(val minutes: Int) : M3ESettingsAction
    data class UpdatePomodoroRounds(val rounds: Int) : M3ESettingsAction
    data class UpdateClassReminderLeadMinutes(val minutes: Int) : M3ESettingsAction
    data class ToggleQuietHours(val enabled: Boolean) : M3ESettingsAction
    data class UpdateQuietHoursStart(val time: String) : M3ESettingsAction
    data class UpdateQuietHoursEnd(val time: String) : M3ESettingsAction
    data class SelectApiTarget(val target: com.amazecc.app.shared.domain.ApiTarget) : M3ESettingsAction
    data class SelectAppIconTheme(val theme: com.amazecc.app.shared.utils.AppIconTheme) : M3ESettingsAction
    data class SelectHomePillStyle(val style: com.amazecc.app.shared.state.HomePillStyle) : M3ESettingsAction
    data object ExportSettings : M3ESettingsAction
    data object ImportSettings : M3ESettingsAction
    /** Opens the app's existing ShortcutsHelpSheet (same sheet as the `?` shortcut). */
    data object OpenShortcutsHelp : M3ESettingsAction
    /** Opens a settings detail screen for the given section id (master-detail nav). */
    data class OpenSettingsMenu(val id: String) : M3ESettingsAction
    /** Returns from a settings detail screen to the grouped menu list. */
    data object CloseSettingsMenu : M3ESettingsAction
    /** Switches the compact nav bar between expanding and labelled-docked styles. */
    data class SelectNavBarStyle(val style: String) : M3ESettingsAction
}

// ==========================================
// 22. MORE HUB NAVIGATION IMMUTABLE STATE & ACTIONS
// ==========================================

enum class M3ESubScreen(val label: String, val category: String, val description: String = "") {
    ACADEMICS_HUB("Academics Hub", "Academics", "Academic overview, credits and quick stats"),
    COURSE_DETAIL("Course Detail", "Academics", "Course syllabi, class IDs and details"),
    COURSE_ATTENDANCE("Course Attendance", "Academics", "Per-course attendance and bunk predictor"),
    COURSE_DASHBOARD("Course Dashboard", "Academics", "Deep per-course attendance, marks and assessments"),
    FACULTY("Faculty Directory", "Academics", "Find cabins, emails and school departments"),
    CURRICULUM("Degree Curriculum", "Academics", "Credit baskets, completion audit and non-graded credits"),
    MARKS_PREDICTOR("FAT Marks Predictor", "Academics", "Internal assessment targets and grade boundaries"),
    ATTENDANCE("Live Attendance", "Academics", "Track course-wise attendance, bunk buffer & leaves"),
    ATTENDANCE_PREDICTOR("Attendance Predictor", "Academics", "Simulate bunks and project attendance to a target"),
    LIBRARIES("Library Catalog", "Academics", "Search books, digital catalogue & dues"),
    EXAM_SCHEDULE("Exam Schedules & Seating", "Academics", "Venue, timing, seat numbers and hall tickets"),
    GRADES_HISTORY("Grades History", "Academics", "Semester-wise grades, GPA and credits earned"),
    CALENDAR("Academic Calendar", "Academics", "Instructional days, holidays & exam dates"),
    FFCS_PLANNER("FFCS Planner", "Academics", "Simulate and generate clash-free schedules"),
    MOODLE("Moodle LMS", "Academics", "Access course materials, files and assignments"),
    OD_TRACKER("OD Tracker", "Academics", "Track on-duty hours, lab & theory approvals"),
    FEEDBACK_STATUS("Feedback Status", "Academics", "VTOP faculty feedback completion status"),
    QBANK("Question Bank", "Academics", "Previous CAT and FAT exam question papers"),
    // D-153: reachable More-hub route to the normal Screen.STUDENT_REPORT (ACADEMICS nav tab).
    STUDENT_REPORT("Academic Report", "Academics", "Student profile, performance, credits and real exports"),

    HOSTEL("Hostel & Mess", "Campus Life", "Room details, block info and monthly mess menus"),
    TRANSPORT("Bus Routes & Transit", "Campus Life", "Shuttle timings, pickup points and routes"),
    CAB_SHARE("Cab Share Pool", "Campus Life", "Split rides to airport, railway stations and city"),
    FREE_CLASSROOMS("Free Classrooms", "Campus Life", "Locate empty lecture halls and labs right now"),
    PAYMENTS("Fee Payments & Receipts", "Campus Life", "Online payments and historical receipts"),
    CIRCULARS("Official Circulars", "Campus Life", "Academic announcements & notifications"),
    DOCUMENTS("Documents & Transcripts", "Campus Life", "Bonafide, transcripts and certificates"),
    // FFCS-CAMPUS-01: three real campus utilities promoted into the More hub's Campus Life group.
    DOCUMENT_SCANNER("Document Scanner", "Campus Life", "Scan, crop and save documents as PDF"),
    GEOTAG_PHOTO("Geotag Photos", "Campus Life", "Camera photos stamped with their real GPS location"),
    FRIEND_FINDER("Friend Finder", "Campus Life", "Find classmates in your registered classes"),

    EVENT_HUB("Event Hub", "Student Hub", "Campus fests, hackathons and tech symposiums"),
    CLUB_HUB("Club Directory", "Student Hub", "Student chapters, tech teams and community clubs"),
    TASKS("Tasks & Assignments", "Student Hub", "Deadlines, focus sessions and homework tracker"),
    SOCIAL("Friends & Schedules", "Student Hub", "Compare timetables and find mutual free hours"),
    FRESHER_WELCOME("Fresher Guide", "Student Hub", "Orientation, maps and essential campus guide"),
    HALL_OF_FAME("Hall of Fame", "Student Hub", "Top performers and campus achievers"),

    PROFILE("Digital Student ID", "Account & System", "Proctor details, registration number and credentials"),
    SETTINGS("Settings & Engine", "Account & System", "Appearance, notifications, Hono API & sync engine"),
    ABOUT("About AmazeCC", "Account & System", "About the project, credits and open-source info"),
    CHANGELOG("Changelog", "Account & System", "What changed in recent app releases")
}

data class M3EMoreHubState(
    val searchQuery: String = "",
    val activeSubScreen: M3ESubScreen? = null
)

sealed interface M3EMoreHubAction {
    data class SearchQueryChanged(val query: String) : M3EMoreHubAction
    data class OpenSubScreen(val screen: M3ESubScreen) : M3EMoreHubAction
    data object BackToHub : M3EMoreHubAction
}

// ==========================================
// 23. LIBRARY (KOHA CATALOG DETAIL) IMMUTABLE MODELS
// ==========================================

/** One real Koha holding row, mapped from `KohaHolding` by the library host. */
data class M3EKohaHolding(
    val barcode: String = "",
    val location: String = "",
    val callNumber: String = "",
    val status: String = "",
    val dueDate: String? = null,
    val notes: String = ""
)

/** Immutable Koha detail projection consumed by the library detail dialog. */
data class M3EKohaBookDetail(
    val biblionumber: String,
    val title: String,
    val author: String = "",
    val publisher: String = "",
    val edition: String = "",
    val isbn: String = "",
    val description: String = "",
    val summary: String = "",
    val subjects: List<String> = emptyList(),
    val ddc: String = "",
    val holdings: List<M3EKohaHolding> = emptyList()
)

// ==========================================
// 24. ATTENDANCE (BUNK) PREDICTOR IMMUTABLE STATE & ACTIONS
// ==========================================
// Distinct from the marks/GPA predictor in section 9: this one projects future classes
// against an attendance target. The host owns all mutable simulation state; the stateless
// screen only renders this contract and emits these actions.

data class M3EPredictorCalendarDay(
    val date: Long,
    val dayNumber: Int,
    val monthLabel: String,
    val weekdayShort: String,
    val effectiveWeekday: String,
    val classCount: Int,
    val isToday: Boolean,
    val state: Int   // 0 present, 1 absent, 2 off
)

data class M3EAttendancePredictorState(
    val targetPercent: Int = 75,
    val mode: String = "CAT1",                 // CAT1 | CAT2 | LID | ALL
    val milestoneDates: com.amazecc.app.shared.domain.MilestoneDates = com.amazecc.app.shared.domain.MilestoneDates(),
    val courseResults: List<com.amazecc.app.shared.domain.ScheduleResult> = emptyList(),
    val calendarDays: List<M3EPredictorCalendarDay> = emptyList(),
    val monthIndex: Int = 0,
    val dayStates: Map<Long, Int> = emptyMap(),
    val courseSkips: Map<String, Set<Long>> = emptyMap(),
    val summary: com.amazecc.app.shared.domain.OverallSummary =
        com.amazecc.app.shared.domain.AttendancePredictor.summarise(emptyList(), 75.0),
    val searchQuery: String = "",
    val filter: String = "ALL",                // ALL | SAFE | AT_RISK | THEORY | LAB
    val expandedCourseCode: String? = null,
    val isRefreshing: Boolean = false
)

sealed interface M3EAttendancePredictorAction {
    data class SetTarget(val percent: Int) : M3EAttendancePredictorAction
    data class SetMode(val mode: String) : M3EAttendancePredictorAction
    data class ToggleDayState(val date: Long) : M3EAttendancePredictorAction
    data class ToggleCourseSkip(val courseCode: String, val date: Long) : M3EAttendancePredictorAction
    data object AttendAll : M3EAttendancePredictorAction
    data object SkipFridays : M3EAttendancePredictorAction
    data object ResetAll : M3EAttendancePredictorAction
    data class SetMonthIndex(val index: Int) : M3EAttendancePredictorAction
    data class UpdateSearch(val query: String) : M3EAttendancePredictorAction
    data class SetFilter(val filter: String) : M3EAttendancePredictorAction
    data class ToggleCourseExpanded(val code: String?) : M3EAttendancePredictorAction
    data object NavigateBack : M3EAttendancePredictorAction
}

// ==========================================
// 25. FFCS RUNTIME CAPABILITIES IMMUTABLE STATE & ACTIONS (D-184)
// ==========================================
// These are UI-facing projections only. The host maps them to the typed D-180/D-181
// engine values; nothing here fabricates a ranking profile, faculty preference or
// drop outcome. Every value reflects a real user choice or a real generation result.

/** The six web ranking profiles, in canonical order. Wire values match `RankingMode`. */
enum class M3EFfcsRankingMode(val label: String, val wire: String) {
    BALANCED("Balanced", "Balanced"),
    BEST_FACULTY("Best Faculty", "Best Faculty"),
    HALF_DAYS("Half Days", "Half Days"),
    MINIMIZE_GAPS("Minimize Gaps", "Minimize Gaps"),
    EARLY_FINISH("Early Finish", "Early Finish"),
    LATE_START("Late Start", "Late Start")
}

/** One of the eight ordered ranking factors, with its engine wire value. */
enum class M3EFfcsRankingFactor(val label: String, val wire: String) {
    GAPS("Gaps", "gaps"),
    HALF_DAYS("Half days", "halfdays"),
    SOCIAL("Social", "social"),
    FACULTY("Faculty", "faculty"),
    EARLY_FINISH("Early finish", "earlyFinish"),
    LATE_START("Late start", "lateStart"),
    BALANCED_LOAD("Balanced load", "balancedLoad"),
    TIME("Time", "time")
}

/** One course's ordered faculty preference, best first. Empty list = no preference. */
data class M3EFfcsFacultyPriorityUi(
    val code: String,
    val orderedFaculty: List<String> = emptyList()
)

/** A real blocked offering key (`FACULTY|SLOT|ROOM`) as shown in the planner. */
data class M3EFfcsBlockedSlotUi(
    val key: String,
    val label: String
)

/** Whether the last real generation attempt was a retryable failure. */
data class M3EFfcsRetryUi(
    val enabled: Boolean = false,
    val canRetry: Boolean = false,
    val droppedCodes: List<String> = emptyList()
)
