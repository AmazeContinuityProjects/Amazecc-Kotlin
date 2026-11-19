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

enum class M3EStatTone { PRIMARY, SECONDARY, TERTIARY, GOOD, WARNING, CRITICAL, NEUTRAL }

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
    val qrCode: String? = null
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

data class M3EFreeClassroomsState(
    val classrooms: List<M3EClassroomSlot> = emptyList(),
    val filteredClassrooms: List<M3EClassroomSlot> = emptyList(),
    val selectedBuilding: M3EClassroomBuilding = M3EClassroomBuilding.ALL,
    val onlyAvailableNow: Boolean = true,
    val searchQuery: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

sealed interface M3EFreeClassroomsAction {
    data class SelectBuilding(val building: M3EClassroomBuilding) : M3EFreeClassroomsAction
    data class ToggleOnlyAvailable(val onlyAvailable: Boolean) : M3EFreeClassroomsAction
    data class SearchQueryChanged(val query: String) : M3EFreeClassroomsAction
    data object Refresh : M3EFreeClassroomsAction
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

data class M3EEventHubState(
    val events: List<M3EEventItem> = emptyList(),
    val filteredEvents: List<M3EEventItem> = emptyList(),
    val selectedCategory: M3EEventCategory = M3EEventCategory.ALL,
    val searchQuery: String = "",
    val registeredCount: Int = 0,
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

sealed interface M3EEventHubAction {
    data class SelectCategory(val category: M3EEventCategory) : M3EEventHubAction
    data class SearchQueryChanged(val query: String) : M3EEventHubAction
    data class RegisterEvent(val eventId: String) : M3EEventHubAction
    data class EventClicked(val eventId: String) : M3EEventHubAction
    data object Refresh : M3EEventHubAction
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
    val type: String, // "CHAPTER" | "CLUB"
    val studentLead: String,
    val facultyCoordinator: String,
    val memberCount: Int,
    val isRecruiting: Boolean = false,
    val recruitmentDeadline: String? = null,
    val instagramHandle: String? = null,
    val description: String = ""
)

data class M3EClubHubState(
    val clubs: List<M3EClubItem> = emptyList(),
    val filteredClubs: List<M3EClubItem> = emptyList(),
    val selectedCategory: M3EClubCategory = M3EClubCategory.ALL,
    val searchQuery: String = "",
    val expandedClubId: String? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

sealed interface M3EClubHubAction {
    data class SelectCategory(val category: M3EClubCategory) : M3EClubHubAction
    data class SearchQueryChanged(val query: String) : M3EClubHubAction
    data class ToggleExpandClub(val clubId: String) : M3EClubHubAction
    data class ApplyClub(val clubId: String) : M3EClubHubAction
    data object Refresh : M3EClubHubAction
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

data class M3EMentorItem(
    val role: String,
    val name: String,
    val designation: String? = null,
    val department: String? = null,
    val email: String? = null,
    val phone: String? = null,
    val cabin: String? = null,
    val photoBase64: String? = null
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
    val personalRecords: List<M3EFieldItem> = emptyList()
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

data class M3ESettingsState(
    val selectedThemeMode: M3EThemeMode = M3EThemeMode.DARK_OLED,
    val selectedPalettePreset: M3EPalettePreset = M3EPalettePreset.OCEAN_MIDNIGHT,
    val honoApiBaseUrl: String = "https://hono.amazecc.com",
    val cacheSizeText: String = "0 B",
    val isDiagnosticsRunning: Boolean = false,
    val diagnosticStatus: String? = null,
    val appVersion: String = "2.4.0 (M3E Overhaul)",
    val classAlertsEnabled: Boolean = true,
    val attendanceAlertsEnabled: Boolean = true,
    val examRemindersEnabled: Boolean = true,
    val vtopSource: com.amazecc.app.shared.vtop.VtopSource = com.amazecc.app.shared.vtop.VtopSource.REMOTE,
    val selectedSyncMode: String = "balanced"
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
}

// ==========================================
// 22. MORE HUB NAVIGATION IMMUTABLE STATE & ACTIONS
// ==========================================

enum class M3ESubScreen(val label: String, val category: String, val description: String = "") {
    COURSE_DETAIL("Course Detail", "Academics", "Course syllabi, class IDs and details"),
    FACULTY("Faculty Directory", "Academics", "Find cabins, emails and school departments"),
    CURRICULUM("Degree Curriculum", "Academics", "Credit baskets, completion audit and non-graded credits"),
    MARKS_PREDICTOR("FAT Marks Predictor", "Academics", "Internal assessment targets and grade boundaries"),
    ATTENDANCE("Live Attendance", "Academics", "Track course-wise attendance, bunk buffer & leaves"),
    LIBRARIES("Library Catalog", "Academics", "Search books, digital catalogue & dues"),
    EXAM_SCHEDULE("Exam Schedules & Seating", "Academics", "Venue, timing, seat numbers and hall tickets"),
    GRADES_HISTORY("Grades History", "Academics", "Semester-wise grades, GPA and credits earned"),
    CALENDAR("Academic Calendar", "Academics", "Instructional days, holidays & exam dates"),
    FFCS_PLANNER("FFCS Planner", "Academics", "Simulate and generate clash-free schedules"),
    MOODLE("Moodle LMS", "Academics", "Access course materials, files and assignments"),
    OD_TRACKER("OD Tracker", "Academics", "Track on-duty hours, lab & theory approvals"),
    FEEDBACK_STATUS("Feedback Status", "Academics", "VTOP faculty feedback completion status"),
    COURSE_MANAGEMENT("Course Management", "Academics", "Option changes, EXC registration & minors"),
    ARREARS("Arrear & Remedial", "Academics", "Arrear registrations, schedule & grades"),
    QBANK("Question Bank", "Academics", "Previous CAT and FAT exam question papers"),

    HOSTEL("Hostel & Mess", "Campus Life", "Room details, block info and monthly mess menus"),
    TRANSPORT("Bus Routes & Transit", "Campus Life", "Shuttle timings, pickup points and routes"),
    CAB_SHARE("Cab Share Pool", "Campus Life", "Split rides to airport, railway stations and city"),
    FREE_CLASSROOMS("Free Classrooms", "Campus Life", "Locate empty lecture halls and labs right now"),
    PAYMENTS("Fee Payments & Receipts", "Campus Life", "Online payments and historical receipts"),
    CIRCULARS("Official Circulars", "Campus Life", "Academic announcements & notifications"),
    DOCUMENTS("Documents & Transcripts", "Campus Life", "Bonafide, transcripts and certificates"),

    EVENT_HUB("Event Hub", "Student Hub", "Campus fests, hackathons and tech symposiums"),
    CLUB_HUB("Club Directory", "Student Hub", "Student chapters, tech teams and community clubs"),
    TASKS("Tasks & Assignments", "Student Hub", "Deadlines, focus sessions and homework tracker"),
    SOCIAL("Friends & Schedules", "Student Hub", "Compare timetables and find mutual free hours"),
    FRESHER_WELCOME("Fresher Guide", "Student Hub", "Orientation, maps and essential campus guide"),

    PROFILE("Digital Student ID", "Account & System", "Proctor details, registration number and credentials"),
    SETTINGS("Settings & Engine", "Account & System", "Appearance, notifications, Hono API & sync engine")
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
