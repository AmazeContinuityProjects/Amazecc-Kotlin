package com.amazecc.app.shared.domain.overhaul

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
    val holidayName: String? = null
)

data class M3EQuickStatsState(
    val attendancePercentage: Double,
    val cgpa: Double,
    val creditsEarned: Int,
    val safeBunksAvailable: Int,
    val attendanceStatusText: String
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
    val isCompleted: Boolean = false
)

data class M3EInsightSlide(
    val title: String,
    val subtitle: String,
    val badge: String,
    val actionText: String? = null,
    val priorityColorHex: String? = null
)

data class M3EHomeState(
    val identity: M3EUserIdentityState,
    val weekDays: List<M3EWeekDay>,
    val selectedDayDate: LocalDate,
    val quickStats: M3EQuickStatsState,
    val todayClasses: List<M3EClassTimelineItem>,
    val insights: List<M3EInsightSlide>,
    val isRefreshing: Boolean = false,
    val errorMessage: String? = null
)

sealed interface M3EHomeAction {
    data class SelectDay(val date: LocalDate) : M3EHomeAction
    data object RefreshData : M3EHomeAction
    data class ClickCourse(val courseCode: String) : M3EHomeAction
    data object OpenAttendanceHub : M3EHomeAction
    data object OpenTimetableHub : M3EHomeAction
    data object OpenAcademicsHub : M3EHomeAction
    data object DismissError : M3EHomeAction
}

// ==========================================
// 2. ATTENDANCE SCREEN IMMUTABLE STATE
// ==========================================

enum class M3ECardTone { GOOD, WARNING, CRITICAL, NEUTRAL }

data class M3ECourseAttendanceCardState(
    val courseCode: String,
    val courseTitle: String,
    val badge: String, // "TH", "LO", "EMB"
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
}

// ==========================================
// 3. NAVIGATION CHROME IMMUTABLE STATE
// ==========================================

enum class M3EDestination(val label: String) {
    HOME("Home"),
    ATTENDANCE("Attendance"),
    TIMETABLE("Timetable"),
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
    GRID("Slot Matrix")
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
    val isLoading: Boolean = false
)

sealed interface M3ETimetableAction {
    data class SelectDay(val dayIndex: Int) : M3ETimetableAction
    data class SetViewMode(val mode: TimetableViewMode) : M3ETimetableAction
    data class SlotClicked(val slot: M3ETimetableSlot) : M3ETimetableAction
    data object Refresh : M3ETimetableAction
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
    val credits: Int,
    val courseType: String,
    val faculty: String,
    val grade: String? = null,
    val internalScored: Double = 0.0,
    val internalMax: Double = 60.0,
    val assessments: List<M3EAssessmentItem> = emptyList(),
    val attendancePct: Double? = null
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
    val isLoading: Boolean = false
)

sealed interface M3EAcademicsAction {
    data class SelectSemester(val semester: String) : M3EAcademicsAction
    data class SetFilter(val filter: AcademicCourseFilter) : M3EAcademicsAction
    data class UpdateSearch(val query: String) : M3EAcademicsAction
    data class ToggleExpandCourse(val courseCode: String) : M3EAcademicsAction
    data object Refresh : M3EAcademicsAction
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

data class M3ECourseDetailState(
    val courseCode: String,
    val courseTitle: String,
    val credits: Int,
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
    val internalMax: Double = 60.0,
    val classAverage: Double? = null,
    val classMax: Double? = null,
    val assessments: List<M3EAssessmentItem> = emptyList(),
    val syllabusUnits: List<M3ECourseSyllabusUnit> = emptyList(),
    val targetGrade: String = "A",
    val requiredFinalScore: Double? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

sealed interface M3ECourseDetailAction {
    data class SelectTab(val tab: CourseDetailTab) : M3ECourseDetailAction
    data class SetTargetGrade(val grade: String) : M3ECourseDetailAction
    data object DownloadSyllabus : M3ECourseDetailAction
    data object Refresh : M3ECourseDetailAction
    data object Back : M3ECourseDetailAction
}

// ==========================================
// 7. FACULTY DIRECTORY IMMUTABLE STATE & ACTIONS
// ==========================================

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
    val isMyFaculty: Boolean = false
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
    data class ViewSchedule(val facultyId: String) : M3EFacultyAction
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
    val errorMessage: String? = null
)

sealed interface M3ECurriculumAction {
    data class SelectBasket(val basket: CurriculumBasket) : M3ECurriculumAction
    data class UpdateSearch(val query: String) : M3ECurriculumAction
    data object DownloadCurriculum : M3ECurriculumAction
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
    val isHappeningSoon: Boolean = false
)

data class M3EExamScheduleState(
    val semesterName: String,
    val selectedSeries: ExamSeries = ExamSeries.ALL,
    val nextUpcomingExam: M3EExamEntry? = null,
    val exams: List<M3EExamEntry>,
    val filteredExams: List<M3EExamEntry>,
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

sealed interface M3EExamScheduleAction {
    data class SelectSeries(val series: ExamSeries) : M3EExamScheduleAction
    data class ExamClicked(val exam: M3EExamEntry) : M3EExamScheduleAction
    data object ExportCalendar : M3EExamScheduleAction
    data object Refresh : M3EExamScheduleAction
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
    val errorMessage: String? = null
)

sealed interface M3EGradesHistoryAction {
    data class ToggleExpandSemester(val semesterId: String) : M3EGradesHistoryAction
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
    val isLoading: Boolean = false
)

sealed interface M3EStudentReportAction {
    data object ExportPdfReport : M3EStudentReportAction
    data object ExportJsonData : M3EStudentReportAction
    data object ShareReport : M3EStudentReportAction
    data object Refresh : M3EStudentReportAction
}
