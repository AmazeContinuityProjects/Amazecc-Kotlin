package com.amazecc.app.shared.ui.screens.m3e

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.amazecc.app.shared.ui.components.AppBackHandler
import com.amazecc.app.shared.api.AmazeClient
import com.amazecc.app.shared.api.SyllabusResult
import com.amazecc.app.shared.domain.AttendancePredictor
import com.amazecc.app.shared.domain.CalendarDay
import com.amazecc.app.shared.domain.CalendarMonth
import com.amazecc.app.shared.domain.ClassStats
import com.amazecc.app.shared.domain.DayLike
import com.amazecc.app.shared.domain.PredictorCourse
import com.amazecc.app.shared.domain.PredictorDayEvent
import com.amazecc.app.shared.domain.ScheduleInput
import com.amazecc.app.shared.domain.asCurveStats
import com.amazecc.app.shared.domain.buildMilestoneDates
import com.amazecc.app.shared.model.batchClassStats
import com.amazecc.app.shared.model.singleClassStats
import com.amazecc.app.shared.utils.ExportImportManager
import com.amazecc.app.shared.utils.currentAppIconTheme
import com.amazecc.app.shared.utils.rememberFileImporter
import com.amazecc.app.shared.utils.setAppIconTheme
import com.amazecc.app.shared.utils.rememberFileSaver
import com.amazecc.app.shared.vtop.VtopSource
import com.amazecc.app.shared.config.SlotMap
import com.amazecc.app.shared.domain.Course
import com.amazecc.app.shared.domain.splitSlots
import com.amazecc.app.shared.utils.AttendanceTimetable
import com.amazecc.app.shared.utils.SlotInfo
import com.amazecc.app.shared.utils.dialPhoneNumber
import com.amazecc.app.shared.utils.openExternalUrl
import com.amazecc.app.shared.model.ExamScheduleRes
import com.amazecc.app.shared.domain.overhaul.AcademicCourseFilter
import com.amazecc.app.shared.domain.overhaul.AttendanceFilter
import com.amazecc.app.shared.domain.overhaul.CourseDetailTab
import com.amazecc.app.shared.domain.overhaul.CurriculumBasket
import com.amazecc.app.shared.domain.overhaul.ExamSeries
import com.amazecc.app.shared.domain.overhaul.M3EAcademicCourse
import com.amazecc.app.shared.domain.overhaul.M3EAcademicSummary
import com.amazecc.app.shared.domain.overhaul.M3EAcademicsAction
import com.amazecc.app.shared.domain.overhaul.M3EAcademicsState
import com.amazecc.app.shared.domain.overhaul.M3EAssessmentItem
import com.amazecc.app.shared.domain.overhaul.M3EAttendanceAction
import com.amazecc.app.shared.domain.overhaul.M3EAttendancePredictorAction
import com.amazecc.app.shared.domain.overhaul.M3EAttendancePredictorState
import com.amazecc.app.shared.domain.overhaul.M3EAttendanceState
import com.amazecc.app.shared.domain.overhaul.M3EBunkSummary
import com.amazecc.app.shared.domain.overhaul.M3EBusRoute
import com.amazecc.app.shared.domain.overhaul.M3ECabShareAction
import com.amazecc.app.shared.domain.overhaul.M3ECabShareFilter
import com.amazecc.app.shared.domain.overhaul.M3ECabShareHub
import com.amazecc.app.shared.domain.overhaul.M3ECabShareRide
import com.amazecc.app.shared.domain.overhaul.M3ECabShareState
import com.amazecc.app.shared.domain.overhaul.M3ECardTone
import com.amazecc.app.shared.domain.overhaul.M3EClassTimelineItem
import com.amazecc.app.shared.domain.overhaul.M3EClassroomBuilding
import com.amazecc.app.shared.domain.overhaul.M3EClassroomSlot
import com.amazecc.app.shared.domain.overhaul.M3EClubCategory
import com.amazecc.app.shared.domain.overhaul.M3EClubHubAction
import com.amazecc.app.shared.domain.overhaul.M3EClubHubState
import com.amazecc.app.shared.domain.overhaul.M3EClubItem
import com.amazecc.app.shared.domain.overhaul.M3EComputations
import com.amazecc.app.shared.domain.overhaul.M3ECourseAttendanceCardState
import com.amazecc.app.shared.domain.overhaul.M3ECourseDetailAction
import com.amazecc.app.shared.domain.overhaul.M3ECourseDetailState
import com.amazecc.app.shared.domain.overhaul.M3ECoursePredictionItem
import com.amazecc.app.shared.domain.overhaul.M3ECurriculumAction
import com.amazecc.app.shared.domain.overhaul.M3EEventCategory
import com.amazecc.app.shared.domain.overhaul.M3EEventHubAction
import com.amazecc.app.shared.domain.overhaul.M3EEventHubState
import com.amazecc.app.shared.domain.overhaul.M3EEventItem
import com.amazecc.app.shared.domain.overhaul.M3EEventPreviewUi
import com.amazecc.app.shared.domain.overhaul.M3EEventRegistrationUi
import com.amazecc.app.shared.domain.overhaul.M3ERegisteredEventItem
import com.amazecc.app.shared.domain.overhaul.M3EExamScheduleAction
import com.amazecc.app.shared.domain.overhaul.M3EExamScheduleState
import com.amazecc.app.shared.domain.overhaul.M3EHomeAttendance
import com.amazecc.app.shared.domain.overhaul.M3EHomeWeekDays
import com.amazecc.app.shared.state.AppDataStore
import com.amazecc.app.shared.domain.overhaul.M3EFacultyAction
import com.amazecc.app.shared.domain.overhaul.M3EFacultyMember
import com.amazecc.app.shared.domain.overhaul.M3EFacultyState
import com.amazecc.app.shared.domain.overhaul.M3EFreeClassroomsAction
import com.amazecc.app.shared.domain.overhaul.M3EFreeClassroomsState
import com.amazecc.app.shared.domain.overhaul.M3EGenderPreference
import com.amazecc.app.shared.domain.overhaul.M3EGradeScreen
import com.amazecc.app.shared.domain.overhaul.M3EGradesHistoryAction
import com.amazecc.app.shared.domain.overhaul.M3EGradesHistoryState
import com.amazecc.app.shared.domain.overhaul.M3EHomeAction
import com.amazecc.app.shared.domain.overhaul.M3EHomeState
import com.amazecc.app.shared.domain.overhaul.M3EHostelAction
import com.amazecc.app.shared.domain.overhaul.M3EHostelDetails
import com.amazecc.app.shared.domain.overhaul.M3EHostelState
import com.amazecc.app.shared.domain.overhaul.M3EHostelTab
import com.amazecc.app.shared.domain.overhaul.M3ELaundrySlot
import com.amazecc.app.shared.domain.overhaul.M3EMessMealItem
import com.amazecc.app.shared.domain.overhaul.M3ELeaveRecord
import com.amazecc.app.shared.domain.overhaul.M3EMarksPredictorAction
import com.amazecc.app.shared.domain.overhaul.M3EMarksPredictorState
import com.amazecc.app.shared.domain.overhaul.M3EMoreHubAction
import com.amazecc.app.shared.domain.overhaul.M3EMoreHubState
import com.amazecc.app.shared.domain.overhaul.M3EPalettePreset
import com.amazecc.app.shared.domain.overhaul.M3EPredictorCalendarDay
import com.amazecc.app.shared.domain.overhaul.M3EProfileAction
import com.amazecc.app.shared.domain.overhaul.M3EProfileState
import com.amazecc.app.shared.domain.overhaul.M3EQuickStatsState
import com.amazecc.app.shared.domain.overhaul.M3ESettingsAction
import com.amazecc.app.shared.domain.overhaul.M3ESettingsState
import com.amazecc.app.shared.domain.overhaul.M3ESubScreen
import com.amazecc.app.shared.domain.overhaul.M3ETaskItem
import com.amazecc.app.shared.domain.overhaul.M3ETaskKind
import com.amazecc.app.shared.domain.overhaul.M3ETaskStatus
import com.amazecc.app.shared.domain.overhaul.M3ETasksAction
import com.amazecc.app.shared.domain.overhaul.M3ETasksFilter
import com.amazecc.app.shared.domain.overhaul.M3ETasksState
import com.amazecc.app.shared.domain.overhaul.M3EThemeMode
import com.amazecc.app.shared.domain.overhaul.M3ETransportAction
import com.amazecc.app.shared.domain.overhaul.M3ETransportFilter
import com.amazecc.app.shared.domain.overhaul.M3ETransportState
import com.amazecc.app.shared.domain.overhaul.M3EUserIdentityState
import com.amazecc.app.shared.model.CabShareHub
import com.amazecc.app.shared.model.CabShareTrip
import com.amazecc.app.shared.repository.SettingsManager
import com.amazecc.app.shared.state.AppState
import com.amazecc.app.shared.state.Screen
import com.amazecc.app.shared.state.UserStore
import com.amazecc.app.shared.theme.AppTheme
import com.amazecc.app.shared.ui.m3e.theme.AmazeM3ETheme
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Bridges AppState and DomainSnapshot into reactive, immutable M3E State models.
 * Provides zero-argument composables for App.kt and MainTabPager navigation routing.
 */
object M3EStateBridge {

    @Composable
    fun isDarkThemeActive(): Boolean {
        val currentTheme by AppState.theme.collectAsState()
        val systemDark = isSystemInDarkTheme()
        return when (currentTheme) {
            AppTheme.DARK, AppTheme.AMOLED -> true
            AppTheme.LIGHT -> false
            AppTheme.SYSTEM -> systemDark
        }
    }
}

/**
 * Single source of truth for the attendance target used by every M3E attendance surface.
 * Collecting both flows keeps the value live when a bus subscription or a custom target changes;
 * [AppState.effectiveAttendanceTarget] itself remains the authority (custom > bus 85 > 75).
 */
@Composable
private fun rememberEffectiveAttendanceTarget(): Float {
    val isBusSubscriber by AppState.isBusSubscriber.collectAsState()
    val customTarget by AppState.customAttendanceTarget.collectAsState()
    return remember(isBusSubscriber, customTarget) {
        AppState.effectiveAttendanceTarget(isBusSubscriber)
    }
}

@Composable
fun M3EHomeScreen(modifier: Modifier = Modifier) {
    val isDark = M3EStateBridge.isDarkThemeActive()
    val identityState by UserStore.identity.collectAsState()
    val domain by AppState.domain.collectAsState()
    val selectedSemId by AppState.selectedSemester.collectAsState()
    val isLoading by AppState.isLoading.collectAsState()
    val errorMsg by AppState.error.collectAsState()
    val semesterMap by AppState.semesterMap.collectAsState()
    val calendarRes by AppState.calendar.collectAsState()
    val allGrades by AppState.allGrades.collectAsState()
    val attendanceTarget = rememberEffectiveAttendanceTarget()

    val todayDate = remember {
        val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        LocalDate(now.year, now.monthNumber, now.dayOfMonth)
    }
    var selectedDate by remember { mutableStateOf(todayDate) }

    val identity = remember(identityState) {
        M3EUserIdentityState(
            name = identityState.name ?: "Student",
            regNumber = identityState.regNo ?: "",
            programme = identityState.program ?: "B.Tech",
            school = identityState.proctor?.school ?: "SCOPE",
            campus = identityState.campus ?: "Vellore Campus",
            avatarUrl = identityState.photoBase64
        )
    }

    val currentSem = domain.academics.semesters[selectedSemId]
    val courses = remember(domain, selectedSemId) { com.amazecc.app.shared.domain.Projections.mergedCourses(domain, selectedSemId) }

    val quickStats = remember(currentSem, courses, allGrades, attendanceTarget) {
        val totalClasses = courses.sumOf { it.attendance?.total ?: 0 }
        val attendedClasses = courses.sumOf { it.attendance?.attended ?: 0 }
        val attPct = if (totalClasses > 0) (attendedClasses.toDouble() / totalClasses) * 100.0 else 0.0
        val targetFraction = attendanceTarget / 100.0
        // The published CGPA from /api/remote/grades is authoritative; the semester GPA is
        // only a fallback when the grade history has not been fetched yet.
        val cgpa = allGrades?.publishedCgpa?.toDoubleOrNull()
            ?: currentSem?.gpa?.trim()?.toDoubleOrNull()
            ?: 0.0
        val credits = courses.sumOf { it.credits?.toIntOrNull() ?: 0 }
        val safeBunks = courses.sumOf { course ->
            val att = course.attendance
            if (att != null && att.total > 0 && targetFraction > 0.0) {
                kotlin.math.floor(
                    (att.attended - targetFraction * att.total) / targetFraction
                ).toInt().coerceAtLeast(0)
            } else 0
        }
        M3EQuickStatsState(
            attendancePercentage = attPct,
            cgpa = cgpa,
            creditsEarned = credits,
            safeBunksAvailable = safeBunks,
            attendanceStatusText = if (totalClasses == 0) {
                "Attendance not available"
            } else if (attPct >= attendanceTarget) {
                "Safe margin across courses (target ${attendanceTarget.toInt()}%)"
            } else {
                "Attendance below ${attendanceTarget.toInt()}% target"
            }
        )
    }

    // D-128: the home strip is a Monday-first calendar week built from the same
    // super-calendar model the web home uses (published calendar holidays / day
    // order, CAT+FAT exams, unfinished-task deadlines) — not a today±3 window.
    // Collect academic + tasks as state so a late sync refreshes exam/deadline dots.
    val academicState by AppDataStore.academic.collectAsState()
    val homeworkTasks by AppState.tasks.collectAsState()
    val weekDays = remember(
        todayDate, selectedDate, courses, calendarRes, selectedSemId, academicState, homeworkTasks
    ) {
        M3EHomeWeekDays.build(
            weekOffset = 0,
            today = todayDate,
            exams = academicState.semesters[selectedSemId]?.exams.orEmpty(),
            calendarMonths = calendarRes?.months.orEmpty(),
            deadlineDates = com.amazecc.app.shared.ui.screens.home.deadlineDates(homeworkTasks),
            courses = courses,
            calendarRes = calendarRes,
            selectedDate = selectedDate
        )
    }

    val todayClasses = remember(courses, selectedDate, calendarRes, weekDays) {
        // D-132: honour a published day-order override (e.g. Saturday follows Monday's
        // timetable) so the timetable and the week strip agree on what a day means.
        val attendanceDay = weekDays.firstOrNull { it.date == selectedDate }?.detectedDayOrder
            ?: AttendanceTimetable.getAttendanceDayForDate(selectedDate, calendarRes)
        val dayKey = attendanceDay.name // "MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN"

        data class RawSlotEvent(
            val course: Course,
            val slotToken: String,
            val startMins: Int,
            val endMins: Int
        )

        val rawEvents = mutableListOf<RawSlotEvent>()
        courses.forEach { course ->
            val tokens = course.slots.flatMap { splitSlots(it) }.distinct()
            tokens.forEach { slotToken ->
                val timeStr = SlotMap.getTime(dayKey, slotToken)
                if (timeStr != null && timeStr.contains("-")) {
                    val parts = timeStr.split("-")
                    if (parts.size == 2) {
                        val start = SlotMap.toMinutes(parts[0])
                        val end = SlotMap.toMinutes(parts[1])
                        rawEvents.add(RawSlotEvent(course, slotToken, start, end))
                    }
                }
            }
        }

        rawEvents.sortWith(compareBy({ it.startMins }, { it.slotToken }))

        data class MergedSlotEvent(
            val course: Course,
            val slots: MutableList<String>,
            var startMins: Int,
            var endMins: Int
        )

        val merged = mutableListOf<MergedSlotEvent>()
        for (current in rawEvents) {
            val prev = merged.lastOrNull()
            // D-129: only merge adjacent slots of the SAME component. An embedded
            // course carries theory and lab slots on one Course; gluing them would
            // keep the first half's attendance and silently drop the other half's.
            if (prev != null && prev.course.code == current.course.code &&
                M3EHomeAttendance.canMergeAdjacentSlots(current.course, prev.slots.last(), current.slotToken) &&
                (current.startMins - prev.endMins) in 0..5
            ) {
                prev.slots.add(current.slotToken)
                prev.endMins = current.endMins
            } else {
                merged.add(MergedSlotEvent(current.course, mutableListOf(current.slotToken), current.startMins, current.endMins))
            }
        }

        val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        val isSelectedDayToday = selectedDate == todayDate
        val currentMinutes = now.hour * 60 + now.minute

        merged.map { event ->
            val isHappeningNow = isSelectedDayToday && (currentMinutes in event.startMins..event.endMins)
            M3EClassTimelineItem(
                courseCode = event.course.code,
                courseTitle = event.course.title,
                slot = event.slots.joinToString("+"),
                venue = event.course.venue?.takeIf { it.isNotBlank() } ?: "SJT",
                faculty = event.course.faculty?.takeIf { it.isNotBlank() } ?: "Faculty",
                startTime = SlotMap.minutesToTimeStr(event.startMins),
                endTime = SlotMap.minutesToTimeStr(event.endMins),
                isHappeningNow = isHappeningNow,
                attendancePercentage = com.amazecc.app.shared.domain.overhaul.M3EHomeAttendance.percentForSlot(
                    event.course, event.slots.firstOrNull()
                )
            )
        }
    }

    // D-132: real papers on the selected date, shown in place of regular classes.
    val todayExams = remember(academicState, selectedSemId, selectedDate) {
        com.amazecc.app.shared.utils.ExamUtils.examsForDate(
            academicState.semesters[selectedSemId]?.exams.orEmpty(),
            selectedDate
        ).map { exam ->
            com.amazecc.app.shared.domain.overhaul.M3EHomeExamItem(
                courseCode = exam.courseCode,
                courseTitle = exam.courseTitle,
                examSeries = exam.examSession,
                examTime = exam.examTime,
                venue = exam.venue,
                seatNo = exam.seatNo
            )
        }
    }

    val homeState = M3EHomeState(
        identity = identity,
        weekDays = weekDays,
        selectedDayDate = selectedDate,
        quickStats = quickStats,
        todayClasses = todayClasses,
        todayExams = todayExams,
        insights = emptyList(),
        isRefreshing = isLoading,
        errorMessage = errorMsg,
        semesterName = semesterMap[selectedSemId] ?: currentSem?.name ?: selectedSemId
    )

    // D-160: the home "View Timetable" button opens the full timetable as a modal sheet.
    // Local UI state only — no screen navigation, so the bottom nav stays mounted behind it.
    var showTimetableSheet by remember { mutableStateOf(false) }

    // The sheet's LIFO guard requires its id on the overlay stack; without this every
    // dismissal route (X / swipe / backdrop / Escape / system back) is a no-op.
    LaunchedEffect(showTimetableSheet) {
        if (showTimetableSheet) AppState.pushOverlay(HOME_TIMETABLE_OVERLAY_ID)
        else AppState.popOverlay(HOME_TIMETABLE_OVERLAY_ID)
    }
    DisposableEffect(Unit) {
        onDispose { AppState.popOverlay(HOME_TIMETABLE_OVERLAY_ID) }
    }

    AmazeM3ETheme(isDark = isDark) {
        Box(modifier = modifier.fillMaxSize()) {
            M3EHomeScreen(
                state = homeState,
                onAction = { action ->
                    when (action) {
                        is M3EHomeAction.SelectDay -> selectedDate = action.date
                        is M3EHomeAction.RefreshData -> AppState.loadAllData()
                        is M3EHomeAction.ClickCourse -> {
                            AppState.openCourseDetail(action.courseCode)
                        }
                        is M3EHomeAction.OpenAttendanceHub -> AppState.navigateTo(Screen.ATTENDANCE)
                        // Kept for the other entry points (bottom nav / More hub / App Library).
                        is M3EHomeAction.OpenTimetableHub -> AppState.navigateTo(Screen.CALENDAR)
                        is M3EHomeAction.OpenTimetableSheet -> showTimetableSheet = true
                        is M3EHomeAction.OpenAcademicsHub -> AppState.navigateTo(Screen.ACADEMICS)
                        is M3EHomeAction.DismissError -> AppState.dismissError()
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
            if (showTimetableSheet) {
                M3ETimetableHost(sheetMode = true, onClose = { showTimetableSheet = false })
            }
        }
    }
}

@Composable
fun M3EAttendanceScreen(modifier: Modifier = Modifier) {
    val isDark = M3EStateBridge.isDarkThemeActive()
    val domain by AppState.domain.collectAsState()
    val selectedSemId by AppState.selectedSemester.collectAsState()
    val semesterMap by AppState.semesterMap.collectAsState()
    val isLoading by AppState.isLoading.collectAsState()
    val attendanceTarget = rememberEffectiveAttendanceTarget()

    var activeFilter by remember { mutableStateOf(AttendanceFilter.ALL) }
    var searchQuery by remember { mutableStateOf("") }

    val currentSem = domain.academics.semesters[selectedSemId]
    val rawCourses = remember(domain, selectedSemId) { com.amazecc.app.shared.domain.Projections.mergedCourses(domain, selectedSemId) }

    val allCards = remember(rawCourses, attendanceTarget) {
        val targetFraction = attendanceTarget / 100.0
        rawCourses.map { c ->
            val att = c.attendance
            val total = att?.total ?: 0
            val attended = att?.attended ?: 0
            val hasData = total > 0
            val pct = if (hasData) (attended.toDouble() / total) * 100.0 else 0.0
            val bunkMargin = when {
                !hasData -> "Not available"
                pct >= attendanceTarget && targetFraction > 0.0 -> {
                    val bunk = kotlin.math.floor(
                        (attended - targetFraction * total) / targetFraction
                    ).toInt().coerceAtLeast(0)
                    if (bunk > 0) "Can bunk $bunk more classes" else "On the limit"
                }
                targetFraction >= 1.0 -> "Target already at 100%"
                else -> {
                    val deficit = targetFraction * total - attended
                    val needed = kotlin.math.ceil(deficit / (1.0 - targetFraction)).toInt().coerceAtLeast(1)
                    "Attend next $needed classes"
                }
            }
            M3ECourseAttendanceCardState(
                courseCode = c.code,
                courseTitle = c.title,
                // Web parity: tokens are "Theory" / "Lab" / "Embedded"
                // (web AttendanceCalendarView.tsx:403, DesktopCourseDetail.tsx:125).
                badge = when {
                    c.type.contains("Embedded", ignoreCase = true) -> "Embedded"
                    c.type.contains("Lab", ignoreCase = true) -> "Lab"
                    else -> "Theory"
                },
                attendedClasses = attended,
                totalClasses = total,
                percentage = pct,
                tone = if (hasData) M3EComputations.toneForPercentage(pct, attendanceTarget) else M3ECardTone.NEUTRAL,
                faculty = c.faculty ?: "",
                slot = c.slots.firstOrNull() ?: "",
                bunkSummary = bunkMargin,
                isEmbedded = c.type.contains("Embedded", ignoreCase = true)
            )
        }
    }

    val filteredCards = remember(allCards, activeFilter, searchQuery) {
        allCards.filter { card ->
            val matchesFilter = when (activeFilter) {
                AttendanceFilter.ALL -> true
                AttendanceFilter.SAFE -> card.tone == M3ECardTone.GOOD
                AttendanceFilter.AT_RISK -> card.tone != M3ECardTone.GOOD
                AttendanceFilter.EMBEDDED -> card.isEmbedded
            }
            val matchesQuery = searchQuery.isBlank() ||
                card.courseCode.contains(searchQuery, ignoreCase = true) ||
                card.courseTitle.contains(searchQuery, ignoreCase = true) ||
                card.faculty.contains(searchQuery, ignoreCase = true)
            matchesFilter && matchesQuery
        }
    }

    val bunkSummary = remember(allCards, attendanceTarget) {
        val targetFraction = attendanceTarget / 100.0
        val totalBunk = allCards.sumOf { card ->
            val att = card.attendedClasses
            val tot = card.totalClasses
            if (tot > 0 && card.percentage >= attendanceTarget && targetFraction > 0.0) {
                kotlin.math.floor((att - targetFraction * tot) / targetFraction).toInt().coerceAtLeast(0)
            } else 0
        }
        val crit = allCards.count { it.percentage < attendanceTarget && it.totalClasses > 0 }
        // The web bands are Safe >= target+5, Warning >= target, Critical below target.
        val warn = allCards.count {
            it.totalClasses > 0 && it.percentage >= attendanceTarget && it.percentage < attendanceTarget + 5.0
        }
        M3EBunkSummary(
            totalBunkableClasses = totalBunk,
            criticalCoursesCount = crit,
            warningCoursesCount = warn,
            targetPercentage = attendanceTarget
        )
    }

    val overallPct = remember(allCards) {
        val tot = allCards.sumOf { it.totalClasses }
        val att = allCards.sumOf { it.attendedClasses }
        if (tot > 0) (att.toDouble() / tot) * 100.0 else 0.0
    }

    val state = M3EAttendanceState(
        semesterName = semesterMap[selectedSemId] ?: selectedSemId,
        overallPercentage = (overallPct * 10).toInt() / 10.0,
        bunkSummary = bunkSummary,
        courses = filteredCards,
        activeFilter = activeFilter,
        searchQuery = searchQuery,
        isLoading = isLoading
    )

    AmazeM3ETheme(isDark = isDark) {
        M3EAttendanceScreen(
            state = state,
            onAction = { action ->
                when (action) {
                    is M3EAttendanceAction.SetFilter -> activeFilter = action.filter
                    is M3EAttendanceAction.UpdateSearch -> searchQuery = action.query
                    is M3EAttendanceAction.CourseClicked -> {
                        AppState.openCourseDetail(action.courseCode)
                    }
                    is M3EAttendanceAction.OpenPredictor -> AppState.navigateTo(Screen.ATTENDANCE_PREDICTOR)
                    is M3EAttendanceAction.Refresh -> AppState.loadAllData()
                }
            },
            modifier = modifier
        )
    }
}

// ── D-138: attendance (bunk) predictor ─────────────────────────────────────
// Small pure helpers shared by the host's derived state. They read only the published
// calendar so a month's year/order never depends on parsing a display string alone.

private fun predictorMonthYear(month: CalendarMonth): Int =
    month.label.split(" ").lastOrNull()?.toIntOrNull() ?: 0

private fun predictorMonthNumber(month: CalendarMonth): Int =
    AttendanceTimetable.parseMonthNumber(month.label) ?: 0

/** The same "instructional day, not a holiday" rule the rest of the app uses for working days. */
private fun isPredictorWorkingDay(day: CalendarDay): Boolean {
    val isWorking = day.events.any {
        val type = it.type.lowercase()
        val text = it.text.lowercase()
        type == "instructional day" || text.contains("instructional day") ||
            text.contains("working") || text.contains("order") ||
            AttendanceTimetable.getDayOrderLabelFromText(it.text) != null
    }
    val isHoliday = day.events.any {
        val type = it.type.lowercase()
        val text = it.text.lowercase()
        type.contains("holiday") || text.contains("holiday") ||
            text.contains("pooja") || text.contains("vacation")
    }
    return isWorking && !isHoliday
}

@Composable
fun M3EAttendancePredictorHost(modifier: Modifier = Modifier) {
    val isDark = M3EStateBridge.isDarkThemeActive()
    val domain by AppState.domain.collectAsState()
    val selectedSemId by AppState.selectedSemester.collectAsState()
    val calendarRes by AppState.calendar.collectAsState()
    val isLoading by AppState.isLoading.collectAsState()
    val attendanceTarget = rememberEffectiveAttendanceTarget()
    // The canonical domain calendar is the same payload AppDataStore.calendar exposes, and it is
    // the type buildMilestoneDates accepts. Keep the raw CalendarRes only for day-order overrides.
    val calendarMonths = remember(domain) { domain.schedule.calendar }

    val todayDate = remember {
        val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        LocalDate(now.year, now.monthNumber, now.dayOfMonth)
    }
    val todayMillis = remember { Clock.System.now().toEpochMilliseconds() }

    // Real milestone dates from the published calendar (CAT-1, CAT-2, LID lab/theory).
    val milestoneDates = remember(calendarMonths) { buildMilestoneDates(calendarMonths) }

    var targetPercent by remember { mutableStateOf(attendanceTarget.toInt().coerceIn(1, 100)) }
    // Keep the persisted setting authoritative: a change in Settings flows back into this host.
    LaunchedEffect(attendanceTarget) {
        targetPercent = attendanceTarget.toInt().coerceIn(1, 100)
    }

    val initialMode = remember(milestoneDates, todayMillis) {
        when {
            (milestoneDates.cat1Date ?: 0L) > todayMillis -> "CAT1"
            (milestoneDates.cat2Date ?: 0L) > todayMillis -> "CAT2"
            else -> "LID"
        }
    }
    var mode by remember(initialMode) { mutableStateOf(initialMode) }

    // Simulation state is intentionally process-local, matching the web predictor.
    var dayStates by remember { mutableStateOf<Map<Long, Int>>(emptyMap()) }
    var courseSkips by remember { mutableStateOf<Map<String, Set<Long>>>(emptyMap()) }
    var searchQuery by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf("ALL") }
    var expandedCourseCode by remember { mutableStateOf<String?>(null) }

    val monthOptions = remember(calendarMonths) {
        calendarMonths.mapNotNull { mo ->
            val year = predictorMonthYear(mo)
            val monthNumber = predictorMonthNumber(mo)
            if (year > 0 && monthNumber in 1..12) mo else null
        }.sortedWith(compareBy({ predictorMonthYear(it) }, { predictorMonthNumber(it) }))
    }
    // Real timetable day cards, keyed by the weekday token the predictor consumes.
    val slotMapTyped = remember {
        SlotMap.map.mapValues { (_, inner) -> inner.mapValues { (_, time) -> SlotInfo(time) } }
    }
    val dayCardsMap = remember(domain) {
        val attendance = com.amazecc.app.shared.domain.Projections.currentSemesterAttendance(domain)
        AttendanceTimetable.buildAttendanceDayCardsMap(
            attendance = attendance.map { item ->
                val shortType = when (item.courseType.lowercase()) {
                    "embedded theory" -> "ETH"
                    "embedded lab" -> "ELA"
                    "theory only" -> "TO"
                    "lab only" -> "LO"
                    "soft skill" -> "SS"
                    else -> item.courseType
                }
                mapOf(
                    "courseCode" to item.courseCode,
                    "courseTitle" to item.courseTitle,
                    "courseType" to shortType,
                    "faculty" to item.faculty,
                    "slotName" to item.slotName,
                    "attendancePercentage" to item.attendancePercentage,
                    "venue" to (item.slotVenue ?: "")
                )
            },
            slotMap = slotMapTyped
        ).mapKeys { it.key.name }.mapValues { (_, cards) ->
            cards.map { card ->
                mapOf(
                    "courseCode" to (card.courseCode ?: ""),
                    "courseTitle" to (card.courseTitle ?: ""),
                    "courseType" to (card.courseType ?: ""),
                    "slotName" to (card.slotName ?: "")
                )
            }
        }
    }

    val courseInputs = remember(domain) {
        com.amazecc.app.shared.domain.Projections.currentSemesterAttendance(domain)
            .filter { AttendancePredictor.isProjectableCourse(it.courseCode, it.slotName, it.totalClasses) }
            .map { course ->
                PredictorCourse(
                    courseCode = course.courseCode,
                    courseTitle = course.courseTitle,
                    courseType = course.courseType,
                    slotName = course.slotName,
                    faculty = course.faculty,
                    attendedClasses = course.attendedClasses,
                    totalClasses = course.totalClasses
                )
            }
    }

    // Real exams are attached to their day so the predictor's effective-weekday scan sees them.
    val exams = remember(domain, selectedSemId) {
        com.amazecc.app.shared.domain.Projections.semesterExams(domain, selectedSemId)
    }
    val examEventsByDayKey = remember(exams) {
        exams.mapNotNull { exam ->
            val parts = exam.date.split("-")
            if (parts.size != 3) return@mapNotNull null
            val year = parts[0].toIntOrNull() ?: return@mapNotNull null
            val monthNumber = parts[1].toIntOrNull() ?: return@mapNotNull null
            val day = parts[2].toIntOrNull() ?: return@mapNotNull null
            val timestamp = AttendancePredictor.makeTimestamp(year, monthNumber - 1, day)
            AttendancePredictor.dayKey(timestamp) to PredictorDayEvent(
                title = exam.courseTitle,
                detail = exam.courseCode
            )
        }.groupBy({ it.first }, { it.second })
    }

    // Every real working day of the published calendar, with its effective (possibly overridden) weekday.
    val workingDays = remember(monthOptions, examEventsByDayKey, calendarRes) {
        monthOptions.flatMap { mo ->
            val year = predictorMonthYear(mo)
            val monthNumber = predictorMonthNumber(mo)
            mo.days.filter { isPredictorWorkingDay(it) }.mapNotNull { calendarDay ->
                val localDate = LocalDate(year, monthNumber, calendarDay.date)
                val timestamp = AttendancePredictor.makeTimestamp(year, monthNumber - 1, calendarDay.date)
                val effective = AttendanceTimetable.getAttendanceDayForDate(localDate, calendarRes).name
                val events = calendarDay.events.map { event ->
                    PredictorDayEvent(text = event.text, category = event.category, detail = event.type)
                } + examEventsByDayKey[AttendancePredictor.dayKey(timestamp)].orEmpty()
                DayLike(date = timestamp, weekday = effective, events = events)
            }
        }.sortedBy { it.date }
    }
    val effectiveMap = remember(workingDays) { AttendancePredictor.buildEffectiveDayMap(workingDays) }

    val courseResults = remember(
        courseInputs, mode, workingDays, effectiveMap, dayCardsMap, dayStates,
        courseSkips, milestoneDates, targetPercent
    ) {
        courseInputs.map { course ->
            val isLab = AttendancePredictor.isLabCourse(course.courseCode, course.courseType)
            AttendancePredictor.projectCourse(
                ScheduleInput(
                    course = course,
                    dayCardsMap = dayCardsMap,
                    workingDays = workingDays,
                    effectiveMap = effectiveMap,
                    dateStates = dayStates,
                    skips = AttendancePredictor.skipSetFor(courseSkips, course.courseCode),
                    ceiling = AttendancePredictor.courseCeiling(mode, isLab, milestoneDates),
                    lockDate = AttendancePredictor.courseLockDate(mode, isLab, milestoneDates),
                    threshold = targetPercent.toDouble()
                )
            )
        }
    }
    val summary = remember(courseResults, targetPercent) {
        AttendancePredictor.summarise(courseResults, targetPercent.toDouble())
    }

    // ALL real working days of the published calendar, bounded by the active milestone. The
    // stateless calendar derives its month switcher from the labels here, and the per-subject
    // skipper lists every candidate meeting date from this same list — so this must not be
    // narrowed to one month. Class counts come from the real timetable day cards.
    val calendarDays = remember(
        monthOptions, calendarRes, dayCardsMap, dayStates, todayDate, mode, milestoneDates
    ) {
        val ceiling = AttendancePredictor.gridCeiling(mode, milestoneDates)
            ?.let { AttendancePredictor.startOfDay(it) }
        monthOptions.flatMap { month ->
            val year = predictorMonthYear(month)
            val monthNumber = predictorMonthNumber(month)
            month.days.filter { isPredictorWorkingDay(it) }.mapNotNull { calendarDay ->
                val localDate = LocalDate(year, monthNumber, calendarDay.date)
                val timestamp = AttendancePredictor.makeTimestamp(year, monthNumber - 1, calendarDay.date)
                val key = AttendancePredictor.startOfDay(timestamp)
                if (ceiling != null && key > ceiling) return@mapNotNull null
                val effective = AttendanceTimetable.getAttendanceDayForDate(localDate, calendarRes).name
                M3EPredictorCalendarDay(
                    date = key,
                    dayNumber = calendarDay.date,
                    monthLabel = month.label,
                    weekdayShort = localDate.dayOfWeek.name.take(3),
                    effectiveWeekday = effective,
                    classCount = dayCardsMap[effective]?.size ?: 0,
                    isToday = localDate == todayDate,
                    state = dayStates[key] ?: 0
                )
            }
        }
    }
    val monthLabels = remember(calendarDays) { calendarDays.map { it.monthLabel }.distinct() }
    // Stable key: calendarDays is rebuilt on every simulation tick, but the label list is not
    // a new *selection* unless its contents change, so the chosen month must survive a toggle.
    val monthKey = monthLabels.joinToString("|")
    val todayMonthLabel = remember(monthOptions, todayDate) {
        monthOptions.firstOrNull {
            predictorMonthYear(it) == todayDate.year && predictorMonthNumber(it) == todayDate.monthNumber
        }?.label
    }
    val initialMonthIndex = remember(monthKey, todayMonthLabel) {
        val index = if (todayMonthLabel == null) -1 else monthLabels.indexOf(todayMonthLabel)
        if (index >= 0) index else 0
    }
    var monthIndex by remember(monthKey) { mutableStateOf(initialMonthIndex) }

    val state = M3EAttendancePredictorState(
        targetPercent = targetPercent,
        mode = mode,
        milestoneDates = milestoneDates,
        courseResults = courseResults,
        calendarDays = calendarDays,
        monthIndex = monthIndex,
        dayStates = dayStates,
        courseSkips = courseSkips,
        summary = summary,
        searchQuery = searchQuery,
        filter = filter,
        expandedCourseCode = expandedCourseCode,
        isRefreshing = isLoading
    )

    AmazeM3ETheme(isDark = isDark) {
        M3EAttendancePredictorScreen(
            state = state,
            onAction = { action ->
                when (action) {
                    is M3EAttendancePredictorAction.SetTarget -> {
                        val next = action.percent.coerceIn(1, 100)
                        targetPercent = next
                        AppState.setCustomAttendanceTarget(next.toFloat())
                    }
                    is M3EAttendancePredictorAction.SetMode -> mode = action.mode.uppercase()
                    is M3EAttendancePredictorAction.ToggleDayState -> {
                        val key = AttendancePredictor.startOfDay(action.date)
                        dayStates = dayStates.toMutableMap().apply {
                            this[key] = ((this[key] ?: 0) + 1) % 3
                        }.toMap()
                    }
                    is M3EAttendancePredictorAction.ToggleCourseSkip -> {
                        courseSkips = AttendancePredictor.toggleCourseSkip(
                            skips = courseSkips,
                            courseCode = action.courseCode,
                            timestamp = AttendancePredictor.startOfDay(action.date)
                        )
                    }
                    is M3EAttendancePredictorAction.AttendAll -> {
                        dayStates = emptyMap()
                        courseSkips = emptyMap()
                    }
                    is M3EAttendancePredictorAction.SkipFridays -> {
                        // Merge into existing simulation state (a prior manual absence on a
                        // Monday must survive), and match both a published Friday day-order
                        // and a real calendar Friday like the web's handleBunkFridays.
                        val merged = dayStates.toMutableMap()
                        calendarDays.forEach { day ->
                            val calendarFriday = kotlinx.datetime.Instant.fromEpochMilliseconds(day.date)
                                .toLocalDateTime(TimeZone.currentSystemDefault())
                                .date.dayOfWeek == kotlinx.datetime.DayOfWeek.FRIDAY
                            if (day.effectiveWeekday == "FRI" || calendarFriday) {
                                merged[AttendancePredictor.startOfDay(day.date)] = 1
                            }
                        }
                        dayStates = merged
                    }
                    is M3EAttendancePredictorAction.ResetAll -> {
                        dayStates = emptyMap()
                        courseSkips = emptyMap()
                        searchQuery = ""
                        filter = "ALL"
                        expandedCourseCode = null
                    }
                    is M3EAttendancePredictorAction.SetMonthIndex -> {
                        monthIndex = action.index.coerceIn(0, maxOf(0, monthLabels.lastIndex))
                    }
                    is M3EAttendancePredictorAction.UpdateSearch -> searchQuery = action.query
                    is M3EAttendancePredictorAction.SetFilter -> filter = action.filter.uppercase()
                    is M3EAttendancePredictorAction.ToggleCourseExpanded -> {
                        expandedCourseCode = if (expandedCourseCode == action.code) null else action.code
                    }
                    is M3EAttendancePredictorAction.NavigateBack -> {
                        if (!AppState.navigateBack()) AppState.navigateTo(Screen.ATTENDANCE)
                    }
                }
            },
            modifier = modifier
        )
    }
}

@Composable
fun M3EAcademicsScreen(modifier: Modifier = Modifier) {
    val isDark = M3EStateBridge.isDarkThemeActive()
    val domain by AppState.domain.collectAsState()
    val allGrades by AppState.allGrades.collectAsState()
    val selectedSemId by AppState.selectedSemester.collectAsState()
    val semesterMap by AppState.semesterMap.collectAsState()
    val isLoading by AppState.isLoading.collectAsState()

    var selectedFilter by remember { mutableStateOf(AcademicCourseFilter.ALL) }
    var searchQuery by remember { mutableStateOf("") }
    var expandedCourseCode by remember { mutableStateOf<String?>(null) }
    // D-140: the unified hub can span semesters, replacing the separate course dashboard.
    var showAllSemesters by remember { mutableStateOf(false) }
    val pastSemestersSynced by AppState.pastSemestersSynced.collectAsState()

    val currentSem = domain.academics.semesters[selectedSemId]
    val academicCourses = remember(domain, selectedSemId, showAllSemesters, semesterMap) {
        val semesterIds = if (showAllSemesters) {
            domain.academics.semesters.keys.sortedDescending()
        } else {
            listOf(selectedSemId)
        }
        semesterIds.flatMap { semId ->
            com.amazecc.app.shared.domain.Projections.mergedCourses(domain, semId).map { c ->
            val att = c.attendance
            val total = att?.total ?: 0
            val attended = att?.attended ?: 0
            val pct = if (total > 0) (attended.toDouble() / total) * 100.0 else null
            val internalMarks = c.marks?.assessments?.map { m ->
                M3EAssessmentItem(
                    title = m.name,
                    maxMarks = m.maxMark.toDoubleOrNull() ?: 0.0,
                    scoredMarks = m.score.toDoubleOrNull() ?: 0.0,
                    weightagePercent = m.weightagePercent.toDoubleOrNull() ?: 0.0,
                    weightageMarks = m.weightage.toDoubleOrNull() ?: 0.0,
                    status = m.status
                )
            } ?: emptyList()
            // D-140: use the same real weighted-derivation the course page uses, instead
            // of the old internalMax = 60.0 hardcode that made hub and detail disagree.
            val derivedInternal = M3EPredictorDerivation.internalMarks(internalMarks)
            M3EAcademicCourse(
                courseCode = c.code,
                courseTitle = c.title,
                credits = c.credits?.toIntOrNull() ?: 3,
                courseType = c.type,
                faculty = c.faculty ?: "",
                grade = c.grade?.letter,
                internalScored = derivedInternal?.first ?: 0.0,
                internalMax = derivedInternal?.second ?: 0.0,
                assessments = internalMarks,
                attendancePct = pct,
                semesterLabel = semesterMap[semId] ?: semId
            )
            }
        }
    }

    val filtered = remember(academicCourses, selectedFilter, searchQuery) {
        academicCourses.filter { course ->
            val matchesFilter = when (selectedFilter) {
                AcademicCourseFilter.ALL -> true
                AcademicCourseFilter.THEORY -> course.courseType.contains("Theory", ignoreCase = true)
                AcademicCourseFilter.LAB -> course.courseType.contains("Lab", ignoreCase = true)
                AcademicCourseFilter.EMBEDDED -> course.courseType.contains("Embedded", ignoreCase = true)
            }
            val matchesQuery = searchQuery.isBlank() ||
                course.courseCode.contains(searchQuery, ignoreCase = true) ||
                course.courseTitle.contains(searchQuery, ignoreCase = true) ||
                course.faculty.contains(searchQuery, ignoreCase = true)
            matchesFilter && matchesQuery
        }
    }

    val summary = remember(currentSem, academicCourses, allGrades) {
        val cgpa = allGrades?.publishedCgpa?.toDoubleOrNull()
            ?: currentSem?.gpa?.trim()?.toDoubleOrNull()
            ?: 0.0
        val earned = academicCourses.filter { it.grade != null && it.grade != "F" }.sumOf { it.credits }
        val reg = academicCourses.sumOf { it.credits }
        M3EAcademicSummary(
            cgpa = cgpa,
            creditsEarned = earned,
            creditsRegistered = reg,
            rankOrStanding = "Good Standing"
        )
    }

    val state = M3EAcademicsState(
        semesterName = semesterMap[selectedSemId] ?: selectedSemId,
        availableSemesters = semesterMap.values.toList(),
        summary = summary,
        courses = filtered,
        selectedFilter = selectedFilter,
        searchQuery = searchQuery,
        expandedCourseCode = expandedCourseCode,
        showAllSemesters = showAllSemesters,
        pastSemestersSynced = pastSemestersSynced,
        isLoading = isLoading
    )

    AmazeM3ETheme(isDark = isDark) {
        M3EAcademicsScreen(
            state = state,
            onAction = { action ->
                when (action) {
                    // The state carries human-readable semester names; AppState needs the id.
                    is M3EAcademicsAction.SelectSemester -> {
                        semesterMap.entries.firstOrNull { it.value == action.semester }?.key
                            ?.let { AppState.selectSemester(it) }
                    }
                    is M3EAcademicsAction.SetFilter -> selectedFilter = action.filter
                    is M3EAcademicsAction.UpdateSearch -> searchQuery = action.query
                    is M3EAcademicsAction.ToggleExpandCourse -> {
                        expandedCourseCode = if (expandedCourseCode == action.courseCode) null else action.courseCode
                    }
                    is M3EAcademicsAction.Refresh -> AppState.loadAllData()
                    is M3EAcademicsAction.ToggleAllSemesters -> showAllSemesters = !showAllSemesters
                    is M3EAcademicsAction.RefreshPastSemesters -> AppState.refreshPastSemesters()
                }
            },
            modifier = modifier,
            onOpenCourse = { code ->
                // In All-Semesters mode resolve WHICH semester owns this course so the detail
                // host cannot fall back to the globally selected semester and open a different
                // course with the same code.
                val ownerSemester = if (showAllSemesters) {
                    domain.academics.semesters.entries
                        .firstOrNull { (_, sem) -> sem.courses.containsKey(code) }?.key
                } else {
                    selectedSemId
                }
                AppState.openCourseDetail(code, ownerSemester)
            },
            onOpenExplorer = { key ->
                when (key) {
                    "marks_predictor" -> AppState.navigateTo(Screen.GPA_PREDICTOR)
                    "grades" -> AppState.navigateTo(Screen.GRADES)
                    "curriculum" -> AppState.navigateTo(Screen.CURRICULUM)
                }
            }
        )
    }
}

@Composable
fun M3ECourseDetailScreen(
    onBack: () -> Unit = { if (!AppState.navigateBack()) AppState.navigateBackTo(Screen.ACADEMICS) },
    modifier: Modifier = Modifier
) {
    val isDark = M3EStateBridge.isDarkThemeActive()
    val domain by AppState.domain.collectAsState()
    val selectedCode by AppState.selectedCourseCode.collectAsState()
    val selectedSemId by AppState.selectedSemester.collectAsState()
    // D-140: a course opened from a past semester must resolve in THAT semester, not the
    // globally selected one; openCourseDetail(code, semesterId) stores this explicitly.
    val courseSemesterId by AppState.selectedCourseSemester.collectAsState()
    val detailSemId = courseSemesterId ?: selectedSemId
    val identityState by UserStore.identity.collectAsState()
    val attendanceTarget = rememberEffectiveAttendanceTarget()
    val scope = rememberCoroutineScope()
    val saveFile = rememberFileSaver()
    // One nullable status line feeds the syllabus section. It carries both download failures and
    // the honest post-save note; the stateless screen presents it neutrally.
    var syllabusStatus by remember { mutableStateOf<String?>(null) }

    var activeTab by remember { mutableStateOf(CourseDetailTab.OVERVIEW) }
    var targetGrade by remember { mutableStateOf("A") }

    val course = remember(domain, detailSemId, selectedCode) {
        val sem = domain.academics.semesters[detailSemId]
        sem?.courses?.get(selectedCode ?: "") ?: sem?.courses?.values?.firstOrNull()
    }

    // Real cohort curve for this one class when the service has one. With no curve the predictor
    // derivation falls back to the published absolute grade ladder; it never invents a cohort.
    val curveClassId = remember(course) { course?.marks?.classNbr ?: course?.classId }
    var courseCurve by remember { mutableStateOf<ClassStats?>(null) }
    LaunchedEffect(curveClassId) {
        courseCurve = null
        if (curveClassId.isNullOrBlank()) return@LaunchedEffect
        courseCurve = runCatching { AmazeClient.getServiceCurvesByClassIds(listOf(curveClassId)) }
            .getOrNull()
            ?.let { envelope ->
                (envelope.batchClassStats()[curveClassId] ?: envelope.singleClassStats())?.asCurveStats()
            }
    }

    val att = course?.attendance
    val total = att?.total ?: 0
    val attended = att?.attended ?: 0
    val hasAttendance = total > 0
    val pct = if (hasAttendance) (attended.toDouble() / total) * 100.0 else 0.0

    val internalAssessments = remember(course) {
        course?.marks?.assessments?.map { m ->
            M3EAssessmentItem(
                title = m.name,
                // 0.0 (rather than a guessed 50) when VTOP omitted the maximum: the derivation
                // then skips that component instead of inventing a denominator.
                maxMarks = m.maxMark.toDoubleOrNull() ?: 0.0,
                scoredMarks = m.score.toDoubleOrNull() ?: 0.0,
                weightagePercent = m.weightagePercent.toDoubleOrNull() ?: 0.0,
                weightageMarks = m.weightage.toDoubleOrNull() ?: 0.0,
                status = m.status
            )
        }.orEmpty()
    }

    // Real internal marks from the published assessment weightages; null when no component has
    // usable numbers, so the UI says "Not available" instead of a confident-looking 0.
    val internalMarks = remember(internalAssessments) {
        M3EPredictorDerivation.internalMarks(internalAssessments)
    }
    val internalScoredForPrediction = internalMarks?.first ?: 0.0
    val internalMaxForPrediction = internalMarks?.second ?: 0.0

    // Recomputed whenever the target grade changes (D-117): the grade selector is a real control.
    // Real grading system + course type gate the cohort curve; without them the derivation
    // deliberately keeps the absolute ladder even when a curve was fetched.
    val requiredFinalScore = remember(
        internalScoredForPrediction, internalMaxForPrediction, targetGrade, courseCurve, course
    ) {
        if (internalMarks != null && internalMaxForPrediction > 0.0) {
            M3EPredictorDerivation.requiredFatPercentage(
                internalScored = internalScoredForPrediction,
                internalMax = internalMaxForPrediction,
                targetGrade = targetGrade,
                classStats = courseCurve,
                courseSystem = course?.courseSystem,
                courseType = course?.type
            )
        } else null
    }

    val syllabusCourseCode = course?.code ?: selectedCode.orEmpty()
    // Only surface a real e-mail: the identity payload carries the proctor's address, so show
    // it here only when this course is actually taught by that proctor. Never invent one.
    val facultyEmail = identityState.proctor?.email?.takeIf { email ->
        email.isNotBlank() &&
            course?.faculty?.equals(identityState.proctor?.name, ignoreCase = true) == true
    }
    val state = M3ECourseDetailState(
        courseCode = syllabusCourseCode,
        courseTitle = course?.title ?: "Course Detail",
        credits = course?.credits?.toIntOrNull() ?: 3,
        courseType = course?.type ?: "Theory",
        slot = course?.slots?.firstOrNull()?.takeIf { it.isNotBlank() } ?: "Not published",
        venue = course?.venue ?: "SJT",
        facultyName = course?.faculty ?: "Faculty",
        facultyEmail = facultyEmail,
        facultyCabin = identityState.proctor?.cabin?.takeIf {
            it.isNotBlank() &&
                course?.faculty?.equals(identityState.proctor?.name, ignoreCase = true) == true
        },
        attendedClasses = attended,
        totalClasses = total,
        attendancePct = pct,
        bunkMarginSummary = when {
            !hasAttendance -> "Attendance not available"
            pct >= attendanceTarget -> "Safe margin (target ${attendanceTarget.toInt()}%)"
            else -> "Below ${attendanceTarget.toInt()}% target"
        },
        tone = if (hasAttendance) M3EComputations.toneForPercentage(pct, attendanceTarget) else M3ECardTone.NEUTRAL,
        activeTab = activeTab,
        internalScored = internalScoredForPrediction,
        internalMax = internalMaxForPrediction,
        assessments = internalAssessments,
        targetGrade = targetGrade,
        requiredFinalScore = requiredFinalScore,
        halves = course?.halves.orEmpty(),
        attendanceTarget = attendanceTarget,
        // No structured per-course syllabus-unit payload exists in the app state: the syllabus
        // endpoint streams a PDF only. Never fabricate modules — the screen shows "Not available".
        syllabusUnits = emptyList(),
        errorMessage = syllabusStatus
    )

    AmazeM3ETheme(isDark = isDark) {
        M3ECourseDetailScreen(
            state = state,
            onAction = { action ->
                when (action) {
                    is M3ECourseDetailAction.SelectTab -> activeTab = action.tab
                    is M3ECourseDetailAction.SetTargetGrade -> targetGrade = action.grade
                    is M3ECourseDetailAction.DownloadSyllabus -> scope.launch {
                        syllabusStatus = null
                        val result = AmazeClient.getSyllabusPdf(syllabusCourseCode)
                        val file = result.download
                        syllabusStatus = when {
                            file == null -> result.error ?: "No syllabus is available for this course"
                            file.bytes.isEmpty() -> "The syllabus file is empty"
                            !saveFile(
                                "${syllabusCourseCode}_syllabus.${file.extension.ifBlank { "pdf" }}",
                                file.bytes
                            ) -> "Could not save the syllabus file"
                            else -> "Syllabus PDF saved. Structured module data is not published for this course."
                        }
                    }
                    is M3ECourseDetailAction.Refresh -> AppState.loadAllData()
                    is M3ECourseDetailAction.Back -> onBack()
                }
            },
            modifier = modifier
        )
    }
}

@Composable
fun M3EFacultyScreen(modifier: Modifier = Modifier) {
    val isDark = M3EStateBridge.isDarkThemeActive()
    val domain by AppState.domain.collectAsState()
    val identityState by UserStore.identity.collectAsState()
    val isLoading by AppState.isLoading.collectAsState()
    val clipboard = LocalClipboardManager.current

    var searchQuery by remember { mutableStateOf("") }
    var selectedSchool by remember { mutableStateOf("All") }
    var expandedFacultyId by remember { mutableStateOf<String?>(null) }

    val facultyList = remember(domain, identityState) {
        val list = mutableListOf<M3EFacultyMember>()
        val proctor = identityState.proctor
        if (proctor?.name != null) {
            list.add(
                M3EFacultyMember(
                    id = "proctor",
                    name = proctor.name,
                    designation = proctor.designation ?: "Professor",
                    school = proctor.school ?: "SCOPE",
                    cabin = proctor.cabin ?: "SJT",
                    email = proctor.email ?: "",
                    intercom = proctor.intercom,
                    isMyFaculty = true
                )
            )
        }
        domain.academics.semesters.values.flatMap { it.courses.values }.forEach { c ->
            val facName = c.faculty
            if (!facName.isNullOrBlank() && list.none { it.name == facName }) {
                list.add(
                    M3EFacultyMember(
                        id = facName,
                        name = facName,
                        designation = "Course Instructor",
                        school = "SCOPE",
                        cabin = c.venue ?: "Cabin",
                        email = "",
                        coursesTaught = listOf(c.code),
                        isMyFaculty = true
                    )
                )
            }
        }
        list
    }

    val filtered = remember(facultyList, searchQuery, selectedSchool) {
        facultyList.filter { f ->
            val matchSchool = selectedSchool == "All" || f.school.equals(selectedSchool, ignoreCase = true)
            val matchQuery = searchQuery.isBlank() ||
                f.name.contains(searchQuery, ignoreCase = true) ||
                f.cabin.contains(searchQuery, ignoreCase = true) ||
                f.school.contains(searchQuery, ignoreCase = true)
            matchSchool && matchQuery
        }
    }

    val state = M3EFacultyState(
        searchQuery = searchQuery,
        selectedSchool = selectedSchool,
        facultyList = facultyList,
        filteredFaculty = filtered,
        expandedFacultyId = expandedFacultyId,
        isLoading = isLoading
    )

    AmazeM3ETheme(isDark = isDark) {
        M3EFacultyScreen(
            state = state,
            onAction = { action ->
                when (action) {
                    is M3EFacultyAction.UpdateSearch -> searchQuery = action.query
                    is M3EFacultyAction.SelectSchool -> selectedSchool = action.school
                    is M3EFacultyAction.ToggleExpand -> {
                        expandedFacultyId = if (expandedFacultyId == action.facultyId) null else action.facultyId
                    }
                    is M3EFacultyAction.CopyEmail -> {
                        if (action.email.isNotBlank()) {
                            runCatching { clipboard.setText(AnnotatedString(action.email)) }
                        }
                    }
                    is M3EFacultyAction.ToggleShowTimetable -> {
                        expandedFacultyId = if (expandedFacultyId == action.facultyId) null else action.facultyId
                    }
                    is M3EFacultyAction.Refresh -> AppState.loadAllData()
                }
            },
            modifier = modifier
        )
    }
}

@Composable
fun M3ECurriculumScreen(modifier: Modifier = Modifier) {
    val isDark = M3EStateBridge.isDarkThemeActive()
    val curriculumRes by AppState.curriculum.collectAsState()
    val domain by AppState.domain.collectAsState()
    val allGrades by AppState.allGrades.collectAsState()
    val identity by UserStore.identity.collectAsState()
    val isLoading by AppState.isLoading.collectAsState()

    var selectedBasket by remember { mutableStateOf(CurriculumBasket.ALL) }
    var searchQuery by remember { mutableStateOf("") }
    var downloadStatus by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val saveFile = rememberFileSaver()

    // Every displayed figure comes from the real curriculum/grade/attendance payload. The pure
    // builder lives beside the stateless screen so it can be exercised without a store.
    val state = remember(
        curriculumRes, domain, allGrades, identity.program, selectedBasket, searchQuery,
        isLoading, downloadStatus
    ) {
        buildCurriculumState(
            curriculumRes = curriculumRes,
            snapshot = domain,
            publishedCreditsEarned = allGrades?.overall?.cgpa?.creditsEarned,
            programmeName = identity.program?.takeIf { it.isNotBlank() } ?: curriculumRes?.title,
            selectedBasket = selectedBasket,
            searchQuery = searchQuery,
            isLoading = isLoading,
            downloadStatus = downloadStatus,
        )
    }

    // The status banner is transient; its lifetime follows the host that owns the value.
    if (downloadStatus != null) {
        LaunchedEffect(downloadStatus) {
            delay(4_000)
            downloadStatus = null
        }
    }

    AmazeM3ETheme(isDark = isDark) {
        M3ECurriculumScreen(
            state = state,
            onAction = { action ->
                when (action) {
                    is M3ECurriculumAction.SelectBasket -> selectedBasket = action.basket
                    is M3ECurriculumAction.UpdateSearch -> searchQuery = action.query
                    is M3ECurriculumAction.DownloadCurriculum -> scope.launch {
                        val bytes = AmazeClient.downloadCurriculum()
                        val saved = bytes != null && bytes.isNotEmpty() &&
                            saveFile("curriculum.pdf", bytes)
                        downloadStatus = when {
                            saved -> "Curriculum saved to Downloads"
                            bytes == null -> "Could not download curriculum"
                            else -> "Could not save curriculum file"
                        }
                    }
                    is M3ECurriculumAction.DownloadSyllabus -> scope.launch {
                        val result = AmazeClient.getSyllabusPdf(action.courseCode)
                        val file = result.download
                        val saved = file != null && file.bytes.isNotEmpty() &&
                            saveFile(
                                "${action.courseCode}_syllabus.${file.extension.ifBlank { "pdf" }}",
                                file.bytes
                            )
                        downloadStatus = when {
                            saved -> "${action.courseCode} syllabus saved to Downloads"
                            file == null -> result.error ?: "Could not download syllabus"
                            else -> "Could not save syllabus file"
                        }
                    }
                    is M3ECurriculumAction.Refresh -> AppState.loadAllData()
                }
            },
            modifier = modifier
        )
    }
}

@Composable
fun M3EMarksPredictorScreen(modifier: Modifier = Modifier) {
    val isDark = M3EStateBridge.isDarkThemeActive()
    val domain by AppState.domain.collectAsState()
    val selectedSemId by AppState.selectedSemester.collectAsState()
    val isLoading by AppState.isLoading.collectAsState()

    val currentSem = domain.academics.semesters[selectedSemId]
    val rawCourses = remember(domain, selectedSemId) { com.amazecc.app.shared.domain.Projections.mergedCourses(domain, selectedSemId) }

    // Real cohort curves (D-012). No class data -> the derivation keeps the absolute ladder.
    var curveStats by remember { mutableStateOf<Map<String, ClassStats>>(emptyMap()) }
    val curveClassIds = remember(rawCourses) {
        rawCourses.mapNotNull { c -> c.marks?.classNbr ?: c.classId }.distinct().take(100)
    }
    LaunchedEffect(curveClassIds) {
        if (curveClassIds.isEmpty()) return@LaunchedEffect
        val envelope = AmazeClient.getServiceCurvesByClassIds(curveClassIds)
        curveStats = envelope?.batchClassStats()
            ?.mapValues { it.value.asCurveStats() }
            ?.filterValues { it != null }
            ?.mapValues { it.value!! }
            ?: emptyMap()
    }

    var selectedCourseCode by remember { mutableStateOf<String?>(null) }
    // Only explicit user overrides live here. Reset returns to the grade VTOP actually published,
    // never to a made-up default letter.
    val gradesMap = remember { mutableStateMapOf<String, String>() }

    // Copy real marks out of the snapshot; the pure derivation owns every displayed number.
    val predictorInputs = remember(rawCourses, curveStats) {
        rawCourses.map { c ->
            M3EPredictorDerivation.CourseInput(
                code = c.code,
                classStats = curveStats[c.marks?.classNbr ?: c.classId],
                // D-117: real VTOP grading system / course type are required to unlock a curve;
                // omitting them silently disabled every curve at runtime.
                courseSystem = c.courseSystem,
                courseType = c.type,
                title = c.title,
                credits = c.credits?.toIntOrNull() ?: 3,
                publishedGrade = c.grade?.letter,
                assessments = c.marks?.assessments.orEmpty().map { m ->
                    M3EAssessmentItem(
                        title = m.name,
                        maxMarks = m.maxMark.toDoubleOrNull() ?: 0.0,
                        scoredMarks = m.score.toDoubleOrNull() ?: 0.0,
                        weightagePercent = m.weightagePercent.toDoubleOrNull() ?: 0.0,
                        weightageMarks = m.weightage.toDoubleOrNull() ?: 0.0,
                        status = m.status
                    )
                }
            )
        }
    }
    val selectedGrades = remember(predictorInputs,gradesMap) {
        predictorInputs.associate { input ->
            input.code to (gradesMap[input.code] ?: input.publishedGrade.orEmpty())
        }
    }
    val courseItems = remember(predictorInputs, selectedGrades) {
        predictorInputs.map { input ->
            val grade = selectedGrades[input.code].orEmpty()
            val internal = M3EPredictorDerivation.internalMarks(input.assessments)
            val requiredFat = internal?.let { (scored, max) ->
                if (grade.isBlank()) null
                else M3EPredictorDerivation.requiredFatPercentage(
                    internalScored = scored,
                    internalMax = max,
                    targetGrade = grade,
                    classStats = input.classStats,
                    courseSystem = input.courseSystem,
                    courseType = input.courseType
                )
            }
            M3ECoursePredictionItem(
                courseCode = input.code,
                courseTitle = input.title,
                credits = input.credits,
                internalScored = internal?.first ?: 0.0,
                internalMax = internal?.second ?: 0.0,
                selectedGrade = grade,
                requiredFatMark = requiredFat,
                tone = M3EPredictorDerivation.toneFor(requiredFat)
            )
        }
    }
    val predictedGpa = remember(predictorInputs, selectedGrades) {
        M3EPredictorDerivation.predictGpa(predictorInputs, selectedGrades)
    }

    val state = M3EMarksPredictorState(
        predictedGpa = predictedGpa ?: 0.0,
        totalCreditsSimulated = predictorInputs.sumOf { it.credits },
        courses = courseItems,
        selectedCourseCode = selectedCourseCode,
        isLoading = isLoading,
        errorMessage = when {
            isLoading -> null
            predictorInputs.isEmpty() -> "No marks data is available for the selected semester."
            predictedGpa == null -> "Not enough published grades to predict a GPA. Choose a target grade for at least one course."
            else -> null
        }
    )

    AmazeM3ETheme(isDark = isDark) {
        M3EMarksPredictorScreen(
            state = state,
            onAction = { action ->
                when (action) {
                    is M3EMarksPredictorAction.SetCourseGrade -> gradesMap[action.courseCode] = action.grade
                    is M3EMarksPredictorAction.SelectCourse -> selectedCourseCode = action.courseCode
                    is M3EMarksPredictorAction.ResetAll -> gradesMap.clear()
                    is M3EMarksPredictorAction.Refresh -> AppState.loadAllData()
                }
            },
            modifier = modifier
        )
    }
}

@Composable
fun M3EExamScheduleScreen(modifier: Modifier = Modifier) {
    val isDark = M3EStateBridge.isDarkThemeActive()
    val selectedSemId by AppState.selectedSemester.collectAsState()
    val semesterMap by AppState.semesterMap.collectAsState()
    val isLoading by AppState.isLoading.collectAsState()

    var selectedSeries by remember { mutableStateOf(ExamSeries.ALL) }
    var scheduleRes by remember { mutableStateOf<ExamScheduleRes?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var isFetching by remember { mutableStateOf(false) }
    var refreshTick by remember { mutableStateOf(0) }

    // Legacy bridge: read the cache populated by SyncModule.EXAM_SCHEDULE on entry; fetch only
    // when the user presses Refresh. (App.kt routes to M3EExamScheduleHost, which does the same.)
    LaunchedEffect(selectedSemId) {
        loadError = null
        scheduleRes = AmazeClient.fallbackCachedExamSchedule(selectedSemId)
    }
    LaunchedEffect(refreshTick) {
        if (refreshTick == 0) return@LaunchedEffect
        isFetching = true
        loadError = null
        try {
            scheduleRes = AmazeClient.getExamSchedule(selectedSemId)
        } catch (e: Exception) {
            loadError = e.message ?: "Unable to load exam schedule"
            scheduleRes = null
        }
        isFetching = false
    }

    val examEntries = remember(scheduleRes) {
        scheduleRes?.let { mapExamScheduleResToEntries(it) }.orEmpty()
    }

    val filtered = remember(examEntries, selectedSeries) {
        if (selectedSeries == ExamSeries.ALL) examEntries
        else examEntries.filter { it.examSeries == selectedSeries }
    }

    val state = M3EExamScheduleState(
        semesterName = semesterMap[selectedSemId] ?: selectedSemId,
        selectedSeries = selectedSeries,
        nextUpcomingExam = filtered.firstOrNull(),
        exams = examEntries,
        filteredExams = filtered,
        isLoading = isFetching || isLoading,
        errorMessage = loadError?.takeIf { scheduleRes == null }
    )

    AmazeM3ETheme(isDark = isDark) {
        M3EExamScheduleScreen(
            state = state,
            onAction = { action ->
                when (action) {
                    is M3EExamScheduleAction.SelectSeries -> selectedSeries = action.series
                    is M3EExamScheduleAction.ExamClicked -> {}
                    is M3EExamScheduleAction.ExportCalendar -> {}
                    is M3EExamScheduleAction.Refresh -> {
                        AppState.selectExamSemester(selectedSemId)
                        refreshTick++
                    }
                    else -> Unit // additional actions are handled by the dedicated host
                }
            },
            modifier = modifier
        )
    }
}

/** One entry of the grade-history host's local, screen-scoped subpage stack. */
private data class M3EGradeSubpage(
    val screen: M3EGradeScreen,
    val courseCode: String? = null
)

@Composable
fun M3EGradesHistoryScreen(modifier: Modifier = Modifier) {
    val isDark = M3EStateBridge.isDarkThemeActive()
    val domain by AppState.domain.collectAsState()
    val allGrades by AppState.allGrades.collectAsState()
    val isLoading by AppState.isLoading.collectAsState()

    // Navigation lives here, not in the stateless screen. The web app's `useSubpageStack`
    // has one child level (insights or a course); its back button always resets to the
    // overview because neither child owns the other, so clearing this stack is the port.
    var stack by remember { mutableStateOf<List<M3EGradeSubpage>>(emptyList()) }
    val current = stack.lastOrNull()
    val screen = current?.screen ?: M3EGradeScreen.OVERVIEW
    val selectedCourseCode = current?.courseCode
    var selectedTermId by remember { mutableStateOf<String?>(null) }

    // Real data only: the /api/remote/grades payload is authoritative for terms, courses,
    // grades and the published CGPA; the domain snapshot is its cached mirror/fallback.
    val state = remember(domain, allGrades, screen, selectedCourseCode, selectedTermId, isLoading) {
        buildGradesHistoryState(
            snapshot = domain,
            allGrades = allGrades,
            screen = screen,
            selectedCourseCode = selectedCourseCode,
            selectedTermId = selectedTermId,
            isLoading = isLoading
        )
    }

    AmazeM3ETheme(isDark = isDark) {
        M3EGradesHistoryScreen(
            state = state,
            onAction = { action ->
                when (action) {
                    is M3EGradesHistoryAction.SelectTerm -> selectedTermId = action.termId
                    is M3EGradesHistoryAction.ToggleExpandSemester -> selectedTermId = action.semesterId
                    is M3EGradesHistoryAction.OpenInsights -> {
                        stack = listOf(M3EGradeSubpage(M3EGradeScreen.INSIGHTS))
                    }
                    is M3EGradesHistoryAction.OpenCourse -> {
                        stack = listOf(M3EGradeSubpage(M3EGradeScreen.COURSE, action.courseCode))
                    }
                    M3EGradesHistoryAction.BackToOverview -> {
                        // Web behaviour: back from either child resets to the overview and
                        // keeps the term the reader last selected.
                        stack = emptyList()
                    }
                    is M3EGradesHistoryAction.Refresh -> AppState.refreshGrades()
                }
            },
            modifier = modifier
        )
    }
}

@Composable
fun M3EHostelScreen(modifier: Modifier = Modifier) {
    val isDark = M3EStateBridge.isDarkThemeActive()
    val hostelRes by AppState.hostelDetails.collectAsState()
    val messMenuRes by AppState.messMenu.collectAsState()
    val laundryRes by AppState.laundrySchedule.collectAsState()
    val isLoading by AppState.isLoading.collectAsState()

    var selectedTab by remember { mutableStateOf(M3EHostelTab.MESS_MENU) }

    val hostelInfo = hostelRes?.hostelInfo
    val details = remember(hostelInfo) {
        // VTOP's "Mess Information" value is the real mess type/preference
        // (NON VEG, FOOD PARK, ...), so prefer it over the historic literal.
        val realMess = hostelInfo?.messInfo?.trim()?.takeIf {
            it.isNotBlank() && !it.equals("NOT ALLOTED", ignoreCase = true)
        }
        M3EHostelDetails(
            blockName = hostelInfo?.blockName?.trim()?.takeIf {
                it.isNotBlank() && !it.equals("NOT ALLOTED", ignoreCase = true)
            } ?: "Not available",
            roomNumber = hostelInfo?.roomNo?.trim()?.takeIf {
                it.isNotBlank() && !it.equals("NOT ALLOTED", ignoreCase = true)
            } ?: "Not available",
            // HostelDetails carries no room designation, bed, caterer or warden fields.
            // Never show plausible-looking placeholders — absence is the honest answer.
            roomType = "Not available",
            bedNumber = "Not available",
            messType = realMess ?: "Not available",
            messCaterer = "Not available",
            wardenName = "Not available",
            wardenPhone = ""
        )
    }

    // Scraped mess rows are numbered strings ("1. Idly 2. Vada ..."); split them into
    // individual dishes without inventing any content. An unnumbered row stays intact.
    fun mealDishes(raw: String?): List<String> {
        val value = raw?.trim().orEmpty()
        if (value.isBlank()) return emptyList()
        val numbered = value.split(Regex("""(?:^|\s)\d+\.\s*"""))
            .map { it.trim() }
            .filter { it.isNotBlank() }
        return if (numbered.isEmpty()) listOf(value) else numbered
    }

    val todayMessMenu = remember(messMenuRes) {
        val todayName = Clock.System.now()
            .toLocalDateTime(TimeZone.currentSystemDefault())
            .dayOfWeek.name.uppercase()
        val todayToken = todayName.take(3)
        val day = messMenuRes?.list?.firstOrNull { row ->
            val label = row.Day?.trim()?.uppercase().orEmpty()
            label == todayName || label == todayToken || label.startsWith(todayToken)
        }
        if (day == null) {
            emptyList()
        } else {
            val dayLabel = day.Day?.trim().orEmpty()
            listOf(
                M3EMessMealItem("Breakfast", dayLabel, mealDishes(day.Breakfast)),
                M3EMessMealItem("Lunch", dayLabel, mealDishes(day.Lunch)),
                M3EMessMealItem("Snacks", dayLabel, mealDishes(day.Snacks)),
                M3EMessMealItem("Dinner", dayLabel, mealDishes(day.Dinner))
            ).filter { it.items.isNotEmpty() }
        }
    }

    val laundrySchedule = remember(laundryRes) {
        laundryRes?.list.orEmpty()
            .filter { !it.Date.isNullOrBlank() || !it.RoomNumber.isNullOrBlank() }
            .map { slot ->
                M3ELaundrySlot(
                    date = slot.Date?.trim().orEmpty(),
                    roomNumber = slot.RoomNumber?.trim().orEmpty()
                )
            }
    }

    val leaves = remember(hostelRes) {
        hostelRes?.leaveHistory?.mapIndexed { idx, l ->
            M3ELeaveRecord(
                leaveId = "$idx",
                visitPlace = l.visitPlace ?: "",
                reason = l.reason ?: "",
                leaveType = l.leaveType ?: "Home",
                fromDate = l.from ?: "",
                toDate = l.to ?: "",
                status = l.status ?: "APPROVED"
            )
        }.orEmpty()
    }

    val state = M3EHostelState(
        hostelDetails = details,
        selectedTab = selectedTab,
        todayMessMenu = todayMessMenu,
        laundrySchedule = laundrySchedule,
        leaves = leaves,
        isLoading = isLoading
    )

    AmazeM3ETheme(isDark = isDark) {
        M3EHostelScreen(
            state = state,
            onAction = { action ->
                when (action) {
                    is M3EHostelAction.SelectTab -> selectedTab = action.tab
                    is M3EHostelAction.ApplyNewLeave -> Unit // no apply-leave endpoint; CTA disabled in the screen
                    is M3EHostelAction.CallWarden -> {
                        details.wardenPhone?.takeIf { it.isNotBlank() }?.let { dialPhoneNumber(it) }
                    }
                    is M3EHostelAction.Refresh -> AppState.loadAllData()
                }
            },
            modifier = modifier
        )
    }
}

@Composable
fun M3ETransportScreen(modifier: Modifier = Modifier) {
    val isDark = M3EStateBridge.isDarkThemeActive()
    val busesRes by AppState.buses.collectAsState()
    val transportData by AppState.transportData.collectAsState()
    val isLoading by AppState.isLoading.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var activeFilter by remember { mutableStateOf(M3ETransportFilter.ALL) }
    var expandedRouteId by remember { mutableStateOf<Int?>(null) }

    // Data comes from the central Sync All sweep (SyncModule.TRANSPORT); page entry must not
    // fire a network fetch. The Refresh action remains the only user-initiated fetch.

    val routes = remember(busesRes) { mapBusesToRoutes(busesRes) }

    val filtered = remember(routes, searchQuery, activeFilter) {
        routes.filter { route ->
            val matchFilter = matchesTransportFilter(route, activeFilter)
            val matchQuery = searchQuery.isBlank() ||
                route.routeNumber.contains(searchQuery, ignoreCase = true) ||
                route.routeName.contains(searchQuery, ignoreCase = true) ||
                route.driverName.contains(searchQuery, ignoreCase = true) ||
                route.stops.any { it.stopName.contains(searchQuery, ignoreCase = true) }
            matchFilter && matchQuery
        }
    }

    val state = M3ETransportState(
        routes = routes,
        filteredRoutes = filtered,
        searchQuery = searchQuery,
        activeFilter = activeFilter,
        expandedRouteId = expandedRouteId,
        registration = transportRegistrationFrom(transportData),
        isLoading = isLoading
    )

    AmazeM3ETheme(isDark = isDark) {
        M3ETransportScreen(
            state = state,
            onAction = { action ->
                when (action) {
                    is M3ETransportAction.SearchQueryChanged -> searchQuery = action.query
                    is M3ETransportAction.SelectFilter -> activeFilter = action.filter
                    is M3ETransportAction.ToggleExpandRoute -> {
                        expandedRouteId = if (expandedRouteId == action.routeId) null else action.routeId
                    }
                    // Platform intents: dial the route's real driver number and open the
                    // route's WhatsApp group URL. Blank/absent data is a no-op (buttons are gated
                    // in the screen too), so we never fire an empty dialer or browser intent.
                    is M3ETransportAction.CallDriver -> {
                        if (action.phoneNumber.isNotBlank()) dialPhoneNumber(action.phoneNumber)
                    }
                    is M3ETransportAction.OpenWhatsappGroup -> {
                        if (action.link.isNotBlank()) openExternalUrl(action.link)
                    }
                    is M3ETransportAction.OpenBusTracking -> {
                        if (action.url.isNotBlank()) openExternalUrl(action.url)
                    }
                    is M3ETransportAction.Refresh -> AppState.refreshTransport()
                }
            },
            modifier = modifier
        )
    }
}

@Composable
fun M3ECabShareScreen(modifier: Modifier = Modifier) {
    val isDark = M3EStateBridge.isDarkThemeActive()
    val cabHubs by AppState.cabHubs.collectAsState()
    val isLoading by AppState.cabLoading.collectAsState()
    val identityState by UserStore.identity.collectAsState()

    var trips by remember { mutableStateOf<List<CabShareTrip>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }
    var activeFilter by remember { mutableStateOf(M3ECabShareFilter.ALL) }
    var isCreateDialogOpen by remember { mutableStateOf(false) }
    var cabNotice by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        AppState.fetchCabHubs()
        AppState.cabRefreshMyTripsNew { myTrips, joinedTrips ->
            trips = (myTrips + joinedTrips).distinctBy { it.trip_id }
        }
    }

    val hubs = remember(cabHubs) {
        cabHubs.map { M3ECabShareHub(it.hub_id.toString(), it.hub_name, "Hub #${it.hub_id}") }
    }

    val rides = remember(trips, identityState.regNo) {
        trips.map { t ->
            val isMine = t.reg_number == identityState.regNo
            M3ECabShareRide(
                tripId = t.trip_id.toString(),
                creatorName = when {
                    t.owner_name.isNotBlank() -> t.owner_name
                    t.name.isNotBlank() -> t.name
                    else -> "Student"
                },
                creatorRegNo = t.reg_number,
                hubName = when {
                    t.hub_name.isNotBlank() -> t.hub_name
                    t.from_hub_name.isNotBlank() -> t.from_hub_name
                    else -> "Campus"
                },
                travelDate = t.travel_date,
                preferredTime = t.preferred_time,
                toleranceHours = t.tolerance_hours.toInt().coerceAtLeast(1),
                availableSeats = (4 - t.requests.size).coerceAtLeast(1),
                totalSeats = 4,
                genderPreference = M3EGenderPreference.ANY,
                notes = t.notes.takeIf { it.isNotBlank() },
                creatorPhone = t.owner_phone.takeIf { it.isNotBlank() },
                isUserCreator = isMine,
                isJoined = t.match_status != null,
                status = t.status.uppercase()
            )
        }
    }

    val filtered = remember(rides, searchQuery, activeFilter) {
        rides.filter { r ->
            val matchFilter = when (activeFilter) {
                M3ECabShareFilter.ALL -> true
                M3ECabShareFilter.AIRPORT -> r.hubName.contains("Airport", ignoreCase = true)
                M3ECabShareFilter.RAILWAY -> r.hubName.contains("Station", ignoreCase = true) || r.hubName.contains("Katpadi", ignoreCase = true)
                M3ECabShareFilter.MY_TRIPS -> r.isUserCreator || r.isJoined
            }
            val matchQuery = searchQuery.isBlank() ||
                r.hubName.contains(searchQuery, ignoreCase = true) ||
                r.creatorName.contains(searchQuery, ignoreCase = true)
            matchFilter && matchQuery
        }
    }

    val state = M3ECabShareState(
        hubs = hubs,
        rides = filtered,
        activeFilter = activeFilter,
        searchQuery = searchQuery,
        isCreateDialogOpen = isCreateDialogOpen,
        isLoading = isLoading,
        errorMessage = cabNotice
    )

    AmazeM3ETheme(isDark = isDark) {
        M3ECabShareScreen(
            state = state,
            onAction = { action ->
                when (action) {
                    is M3ECabShareAction.SelectFilter -> activeFilter = action.filter
                    is M3ECabShareAction.SearchQueryChanged -> searchQuery = action.query
                    is M3ECabShareAction.OpenCreateDialog -> isCreateDialogOpen = true
                    is M3ECabShareAction.CloseCreateDialog -> isCreateDialogOpen = false
                    is M3ECabShareAction.JoinTrip -> {
                        action.tripId.toLongOrNull()?.let { id ->
                            AppState.cabRequestJoinNew(id)
                        }
                    }
                    is M3ECabShareAction.ContactCreator -> {
                        if (action.phoneNumber.isNotBlank()) dialPhoneNumber(action.phoneNumber)
                        else cabNotice = "This creator has not shared a contact number."
                    }
                    is M3ECabShareAction.Refresh -> {
                        AppState.fetchCabHubs()
                        AppState.cabRefreshMyTripsNew { myTrips, joinedTrips ->
                            trips = (myTrips + joinedTrips).distinctBy { it.trip_id }
                        }
                    }
                }
            },
            modifier = modifier,
            onCreateRide = { draft ->
                val fromId = draft.fromHubId.toIntOrNull()
                val toId = draft.toHubId.toIntOrNull()
                if (fromId == null || toId == null) {
                    cabNotice = "Choose a valid pick-up and drop hub."
                } else {
                    cabNotice = null
                    AppState.cabCreateTripNew(
                        fromHubId = fromId,
                        toHubId = toId,
                        date = draft.date.trim(),
                        time = draft.time.trim(),
                        tolerance = draft.tolerance.toDoubleOrNull() ?: 1.0,
                        seats = draft.seats.toIntOrNull()?.coerceIn(1, 6) ?: 3,
                        gender = draft.gender,
                        notes = draft.notes.trim(),
                        onResult = { success, message ->
                            cabNotice = message
                            if (success) {
                                isCreateDialogOpen = false
                                AppState.cabRefreshMyTripsNew { myTrips, joinedTrips ->
                                    trips = (myTrips + joinedTrips).distinctBy { it.trip_id }
                                }
                            }
                        }
                    )
                }
            }
        )
    }
}

@Composable
fun M3EFreeClassroomsScreen(
    onBack: () -> Unit = { if (!AppState.navigateBack()) AppState.navigateBackTo(Screen.ACADEMICS) },
    modifier: Modifier = Modifier
) {
    val isDark = M3EStateBridge.isDarkThemeActive()
    var searchQuery by remember { mutableStateOf("") }
    var selectedBuilding by remember { mutableStateOf(M3EClassroomBuilding.ALL) }
    var onlyAvailableNow by remember { mutableStateOf(true) }

    val sampleRooms = remember {
        listOf(
            M3EClassroomSlot("SJT 101", "SJT", 1, 60, true, "Available", true, "11:00 AM"),
            M3EClassroomSlot("SJT 204", "SJT", 2, 70, true, "Available", true, "01:30 PM"),
            M3EClassroomSlot("TT 412", "TT", 4, 65, false, "Occupied", false, null),
            M3EClassroomSlot("MB 202", "MB", 2, 80, true, "Available", true, "04:00 PM")
        )
    }

    val filtered = remember(sampleRooms, searchQuery, selectedBuilding, onlyAvailableNow) {
        sampleRooms.filter { room ->
            val matchBuilding = selectedBuilding == M3EClassroomBuilding.ALL || room.buildingCode == selectedBuilding.code
            val matchAvail = !onlyAvailableNow || room.isCurrentlyAvailable
            val matchQuery = searchQuery.isBlank() || room.roomNumber.contains(searchQuery, ignoreCase = true)
            matchBuilding && matchAvail && matchQuery
        }
    }

    val state = M3EFreeClassroomsState(
        classrooms = sampleRooms,
        filteredClassrooms = filtered,
        selectedBuilding = selectedBuilding,
        onlyAvailableNow = onlyAvailableNow,
        searchQuery = searchQuery,
        isLoading = false
    )

    AmazeM3ETheme(isDark = isDark) {
        M3EFreeClassroomsScreen(
            state = state,
            onAction = { action ->
                when (action) {
                    is M3EFreeClassroomsAction.SelectBuilding -> selectedBuilding = action.building
                    is M3EFreeClassroomsAction.ToggleOnlyAvailable -> onlyAvailableNow = action.onlyAvailable
                    is M3EFreeClassroomsAction.SearchQueryChanged -> searchQuery = action.query
                    is M3EFreeClassroomsAction.Refresh -> {}
                    // This legacy overload is not routed; the live host handles the richer
                    // day/period/block actions. Keep the statement exhaustive without faking work.
                    else -> Unit
                }
            },
            modifier = modifier
        )
    }
}

@Composable
fun M3EEventHubScreen(modifier: Modifier = Modifier) {
    val isDark = M3EStateBridge.isDarkThemeActive()
    val eventsRes by AppState.events.collectAsState()
    val registeredRes by AppState.registeredEvents.collectAsState()
    val isLoading by AppState.isLoading.collectAsState()
    val errorMessage by AppState.error.collectAsState()
    val previewRes by AppState.eventPreview.collectAsState()
    val registrationRes by AppState.eventRegistration.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf(M3EEventCategory.ALL) }
    // Which click opened the preview dialog; guards against a stale preview from a prior event.
    var previewEventId by remember { mutableStateOf<String?>(null) }
    // Set on a register tap so a stale registration result is not shown on entry.
    var isRegistrationStatusVisible by remember { mutableStateOf(false) }

    // A confirmed registration changes the real registration list (and its receipt URLs).
    LaunchedEffect(registrationRes) {
        if (registrationRes?.status?.equals("success", ignoreCase = true) == true) {
            AppState.refreshEventsProfile()
        }
    }

    val allEvents = remember(eventsRes, registeredRes) {
        val regIds = registeredRes?.events?.map { it.eid }.orEmpty().toSet()
        eventsRes?.events?.map { e ->
            val category = when (e.type.trim().uppercase()) {
                "TECHNICAL", "TECH" -> M3EEventCategory.TECHNICAL
                "CULTURAL" -> M3EEventCategory.CULTURAL
                "WORKSHOP" -> M3EEventCategory.WORKSHOPS
                "HACKATHON" -> M3EEventCategory.HACKATHONS
                "SPORTS" -> M3EEventCategory.SPORTS
                else -> M3EEventCategory.ALL
            }
            M3EEventItem(
                id = e.eid,
                title = e.title,
                // The event feed has no fest-name field — leave blank instead of inventing one.
                festName = "",
                category = category,
                date = e.date,
                time = e.time ?: "",
                venue = e.location,
                price = if (e.price == "0" || e.price.isBlank()) "Free" else "₹${e.price}",
                isFree = e.price == "0" || e.price.isBlank(),
                isRegistered = e.eid in regIds
            )
        }.orEmpty()
    }

    val registeredItems = remember(registeredRes) {
        registeredRes?.events.orEmpty().map { r ->
            M3ERegisteredEventItem(
                eid = r.eid,
                title = r.title,
                venue = r.location,
                date = r.date,
                time = r.time,
                paymentStatus = r.paymentStatus,
                receiptUrl = r.receiptUrl,
                certificateUrl = r.certificateUrl,
                payNowUrl = r.payNowUrl
            )
        }
    }

    val preview = remember(previewRes, previewEventId, allEvents) {
        val p = previewRes
        if (p != null && previewEventId == p.eid) {
            M3EEventPreviewUi(
                eid = p.eid,
                title = allEvents.firstOrNull { it.id == p.eid }?.title.orEmpty(),
                imageUrl = p.imageSrc,
                description = p.description,
                metaDetails = p.metaDetails.orEmpty()
            )
        } else {
            null
        }
    }

    val registrationResult = remember(registrationRes, isRegistrationStatusVisible) {
        val r = registrationRes
        if (isRegistrationStatusVisible && r != null) {
            M3EEventRegistrationUi(
                status = r.status,
                message = r.message,
                paymentUrl = r.url
            )
        } else {
            null
        }
    }

    val filtered = remember(allEvents, searchQuery, selectedCategory) {
        allEvents.filter { e ->
            val matchCategory = when (selectedCategory) {
                M3EEventCategory.ALL -> true
                M3EEventCategory.MY_REGISTRATIONS -> e.isRegistered
                else -> e.category == selectedCategory
            }
            val matchQuery = searchQuery.isBlank() ||
                e.title.contains(searchQuery, ignoreCase = true) ||
                e.venue.contains(searchQuery, ignoreCase = true) ||
                e.festName.contains(searchQuery, ignoreCase = true)
            matchCategory && matchQuery
        }
    }

    val state = M3EEventHubState(
        events = allEvents,
        filteredEvents = filtered,
        selectedCategory = selectedCategory,
        searchQuery = searchQuery,
        registeredCount = registeredItems.size,
        isLoading = isLoading,
        errorMessage = errorMessage,
        registeredEvents = registeredItems,
        preview = preview,
        registrationResult = registrationResult
    )

    AmazeM3ETheme(isDark = isDark) {
        M3EEventHubScreen(
            state = state,
            onAction = { action ->
                when (action) {
                    is M3EEventHubAction.SelectCategory -> selectedCategory = action.category
                    is M3EEventHubAction.SearchQueryChanged -> searchQuery = action.query
                    is M3EEventHubAction.RegisterEvent -> {
                        isRegistrationStatusVisible = true
                        AppState.registerForEvent(action.eventId)
                    }
                    is M3EEventHubAction.EventClicked -> {
                        previewEventId = action.eventId
                        AppState.refreshEventPreview(action.eventId)
                    }
                    is M3EEventHubAction.DismissPreview -> previewEventId = null
                    is M3EEventHubAction.DismissRegistrationStatus -> isRegistrationStatusVisible = false
                    is M3EEventHubAction.OpenExternalLink -> openExternalUrl(action.url)
                    is M3EEventHubAction.Refresh -> AppState.syncEventsAndClubs()
                }
            },
            modifier = modifier
        )
    }
}

@Composable
fun M3EClubHubScreen(modifier: Modifier = Modifier) {
    val isDark = M3EStateBridge.isDarkThemeActive()
    val clubsRes by AppState.clubs.collectAsState()
    val isLoading by AppState.isLoading.collectAsState()
    val errorMessage by AppState.error.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf(M3EClubCategory.ALL) }
    var expandedClubId by remember { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }

    val allClubs = remember(clubsRes) {
        clubsRes?.clubs?.map { c ->
            // ClubItem exposes only real fields. Absent values stay blank/0 so the
            // stateless screen omits those rows instead of inventing data.
            M3EClubItem(
                id = c.id.orEmpty(),
                name = c.name ?: "Club",
                category = M3EClubCategory.ALL,
                type = "",
                studentLead = "",
                facultyCoordinator = "",
                memberCount = 0,
                isRecruiting = false,
                instagramHandle = c.instagram,
                description = c.description.orEmpty(),
                // ClubItem has no dedicated recruitment-link field yet; the club's own
                // website is the only real external link available in the current contract.
                recruitmentLink = c.recruitmentLink?.takeIf { it.isNotBlank() }
                    ?: c.website?.takeIf { it.isNotBlank() }
            )
        }.orEmpty()
    }

    val filtered = remember(allClubs, searchQuery, selectedCategory) {
        allClubs.filter { club ->
            val matchCategory = when (selectedCategory) {
                M3EClubCategory.ALL -> true
                M3EClubCategory.RECRUITING -> club.isRecruiting || !club.recruitmentLink.isNullOrBlank()
                else -> club.category == selectedCategory
            }
            val matchQuery = searchQuery.isBlank() ||
                club.name.contains(searchQuery, ignoreCase = true) ||
                club.studentLead.contains(searchQuery, ignoreCase = true)
            matchCategory && matchQuery
        }
    }

    val state = M3EClubHubState(
        clubs = allClubs,
        filteredClubs = filtered,
        selectedCategory = selectedCategory,
        searchQuery = searchQuery,
        expandedClubId = expandedClubId,
        isLoading = isLoading,
        errorMessage = errorMessage,
        notice = notice
    )

    AmazeM3ETheme(isDark = isDark) {
        M3EClubHubScreen(
            state = state,
            onAction = { action ->
                when (action) {
                    is M3EClubHubAction.SelectCategory -> selectedCategory = action.category
                    is M3EClubHubAction.SearchQueryChanged -> searchQuery = action.query
                    is M3EClubHubAction.ToggleExpandClub -> {
                        expandedClubId = if (expandedClubId == action.clubId) null else action.clubId
                    }
                    is M3EClubHubAction.ApplyClub -> {
                        val link = clubsRes?.clubs?.firstOrNull { it.id == action.clubId }
                            ?.website?.takeIf { it.isNotBlank() }
                        if (!link.isNullOrBlank()) {
                            openExternalUrl(link)
                        } else {
                            notice = "No application link is available for this club yet. Check the club's own page."
                        }
                    }
                    is M3EClubHubAction.OpenLink -> openExternalUrl(action.url)
                    is M3EClubHubAction.DismissNotice -> notice = null
                    is M3EClubHubAction.Refresh -> AppState.syncEventsAndClubs()
                }
            },
            modifier = modifier
        )
    }
}

@Composable
fun M3ETasksScreen(modifier: Modifier = Modifier) {
    val isDark = M3EStateBridge.isDarkThemeActive()
    val homeworkTasks by AppState.tasks.collectAsState()
    val isLoading by AppState.isLoading.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var activeFilter by remember { mutableStateOf(M3ETasksFilter.ALL) }
    var isCreateDialogOpen by remember { mutableStateOf(false) }

    val taskItems = remember(homeworkTasks) {
        val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
        homeworkTasks.map { t ->
            val due = runCatching { LocalDate.parse(t.dueDate) }.getOrNull()
            // The local model has no in-progress flag; only report what it actually stores.
            val status = if (t.completed) M3ETaskStatus.DONE else M3ETaskStatus.PENDING
            val kind = when (t.type.trim().lowercase()) {
                "assignment", "lms_auto" -> M3ETaskKind.DIGITAL_ASSIGNMENT
                "quiz", "test", "exam" -> M3ETaskKind.TEST
                "study" -> M3ETaskKind.STUDY
                "reminder" -> M3ETaskKind.REMINDER
                else -> M3ETaskKind.HOMEWORK
            }
            M3ETaskItem(
                id = t.id,
                title = t.title,
                kind = kind,
                status = status,
                courseCode = t.courseCode,
                courseTitle = t.courseTitle,
                dueDate = t.dueDate,
                slotName = t.dueTime.takeIf { it.isNotBlank() },
                isOverdue = !t.completed && due != null && due < today,
                notes = t.description.takeIf { it.isNotBlank() }
            )
        }
    }

    val filtered = remember(taskItems, searchQuery, activeFilter) {
        taskItems.filter { task ->
            val matchFilter = when (activeFilter) {
                M3ETasksFilter.ALL -> true
                M3ETasksFilter.PENDING -> task.status == M3ETaskStatus.PENDING
                M3ETasksFilter.IN_PROGRESS -> task.status == M3ETaskStatus.IN_PROGRESS
                M3ETasksFilter.DONE -> task.status == M3ETaskStatus.DONE
                M3ETasksFilter.ASSIGNMENTS -> task.kind == M3ETaskKind.DIGITAL_ASSIGNMENT
            }
            val matchQuery = searchQuery.isBlank() ||
                task.title.contains(searchQuery, ignoreCase = true) ||
                task.courseCode.contains(searchQuery, ignoreCase = true)
            matchFilter && matchQuery
        }
    }

    val state = M3ETasksState(
        tasks = taskItems,
        filteredTasks = filtered,
        activeFilter = activeFilter,
        searchQuery = searchQuery,
        pendingCount = taskItems.count { it.status == M3ETaskStatus.PENDING },
        completedCount = taskItems.count { it.status == M3ETaskStatus.DONE },
        isCreateDialogOpen = isCreateDialogOpen,
        isLoading = isLoading
    )

    AmazeM3ETheme(isDark = isDark) {
        M3ETasksScreen(
            state = state,
            onAction = { action ->
                when (action) {
                    is M3ETasksAction.SelectFilter -> activeFilter = action.filter
                    is M3ETasksAction.SearchQueryChanged -> searchQuery = action.query
                    is M3ETasksAction.ToggleTaskStatus -> AppState.toggleTaskCompleted(action.taskId)
                    is M3ETasksAction.DeleteTask -> AppState.deleteTask(action.taskId)
                    is M3ETasksAction.OpenCreateDialog -> isCreateDialogOpen = true
                    is M3ETasksAction.CloseCreateDialog -> isCreateDialogOpen = false
                    is M3ETasksAction.Refresh -> AppState.loadAllData()
                }
            },
            modifier = modifier,
            onCreateTask = { draft ->
                val now = Clock.System.now().toString()
                AppState.addTask(
                    com.amazecc.app.shared.model.HomeworkTask(
                        id = "local-${Clock.System.now().toEpochMilliseconds()}",
                        courseCode = draft.courseCode.trim(),
                        courseTitle = draft.courseTitle.trim(),
                        title = draft.title.trim(),
                        description = draft.description.trim(),
                        dueDate = draft.dueDate.trim(),
                        dueTime = draft.dueTime.ifBlank { "23:59" },
                        type = draft.type,
                        priority = draft.priority,
                        estimatedMinutes = draft.estimatedMinutes.toIntOrNull() ?: 0,
                        reminderAt = draft.reminderAt.ifBlank { null },
                        reminderRepeat = draft.reminderRepeat,
                        showOnCalendar = draft.showOnCalendar,
                        showOnTimetable = draft.showOnTimetable,
                        createdAt = now
                    )
                )
                isCreateDialogOpen = false
            }
        )
    }
}

@Composable
fun M3EProfileScreen(modifier: Modifier = Modifier) {
    // The tab pager and the direct destination must behave identically: both use the
    // full Profile host (residence, bus, contacts menu card, identity warm-up).
    // Contacts and acknowledgements now live on their own destinations (D-110).
    M3EProfileHost(modifier)
}

@Composable
fun M3ESettingsScreen(modifier: Modifier = Modifier) {
    val isDark = M3EStateBridge.isDarkThemeActive()
    val currentTheme by AppState.theme.collectAsState()
    val currentAccent by AppState.accent.collectAsState()
    val scope = rememberCoroutineScope()

    var classAlerts by remember { mutableStateOf(SettingsManager.isNotifClassRemindersEnabled()) }
    var attendanceAlerts by remember { mutableStateOf(SettingsManager.isNotifAssignmentRemindersEnabled()) }
    var examReminders by remember { mutableStateOf(SettingsManager.isNotifExamRemindersEnabled()) }
    // Never show a fabricated health result before the user actually runs a check.
    var diagnosticMsg by remember { mutableStateOf<String?>("Not checked") }
    var isDiagnosticsRunning by remember { mutableStateOf(false) }
    var vtopSource by remember { mutableStateOf(AmazeClient.vtopSource) }
    var selectedSyncMode by remember { mutableStateOf(SettingsManager.getString("selected_sync_mode", "balanced").ifBlank { "balanced" }) }
    var honoApiUrl by remember { mutableStateOf(AmazeClient.baseUrl) }
    var cacheSize by remember { mutableStateOf(SettingsManager.getFormattedCacheSize()) }

    var selectedPalettePreset by remember(currentAccent) {
        val initial = when (currentAccent) {
            com.amazecc.app.shared.theme.AccentTheme.OCEAN -> M3EPalettePreset.OCEAN_MIDNIGHT
            com.amazecc.app.shared.theme.AccentTheme.FOREST, com.amazecc.app.shared.theme.AccentTheme.VERDANT -> M3EPalettePreset.EMERALD_MINT
            com.amazecc.app.shared.theme.AccentTheme.LAVENDER -> M3EPalettePreset.PURPLE_DUSK
            com.amazecc.app.shared.theme.AccentTheme.SUNSET -> M3EPalettePreset.AMBER_SUNSET
            com.amazecc.app.shared.theme.AccentTheme.CUSTOM -> M3EPalettePreset.CRIMSON_VELVET
        }
        mutableStateOf(initial)
    }

    val selectedThemeMode = when (currentTheme) {
        AppTheme.AMOLED, AppTheme.DARK -> M3EThemeMode.DARK_OLED
        AppTheme.LIGHT -> M3EThemeMode.LIGHT
        AppTheme.SYSTEM -> M3EThemeMode.SYSTEM_DEFAULT
    }

    // D-059 — migrated settings depth (web parity — ProfilePage).
    var settingsSearchQuery by remember { mutableStateOf("") }
    // Collected live: the Alt+P keyboard shortcut calls AppState.togglePrivacyMode()
    // (App.kt), so the Settings toggle must reflect that state, not a local copy.
    val privacyModeEnabled by AppState.privacyMode.collectAsState()
    var pomodoroFocus by remember { mutableStateOf(SettingsManager.getPomodoroFocusMinutes()) }
    var pomodoroBreak by remember { mutableStateOf(SettingsManager.getPomodoroBreakMinutes()) }
    var pomodoroRounds by remember { mutableStateOf(SettingsManager.getPomodoroRounds()) }
    var classReminderLead by remember { mutableStateOf(SettingsManager.getNotifOffsetMinutes()) }
    var quietHoursEnabled by remember { mutableStateOf(SettingsManager.isQuietHoursEnabled()) }
    var quietHoursStart by remember { mutableStateOf(SettingsManager.getQuietHoursStart()) }
    var quietHoursEnd by remember { mutableStateOf(SettingsManager.getQuietHoursEnd()) }
    var apiTargetName by remember { mutableStateOf(SettingsManager.getApiTargetName()) }
    // Reflect the component that is actually enabled, so the control agrees
    // with the launcher after a restart or a change made outside Settings.
    var appIconTheme by remember { mutableStateOf(currentAppIconTheme()) }
    var visibleSettingsSections = com.amazecc.app.shared.domain.overhaul.settingsSectionsForQuery(settingsSearchQuery)
    // Master-detail settings navigation: null = grouped menu list, otherwise a section id.
    var openSettingsMenu by remember { mutableStateOf<String?>(null) }
    val navBarStyle by AppState.navBarStyle.collectAsState()
    // System back must first return from a detail screen to the grouped menu;
    // only App.kt's global handler should ever navigate away from Settings.
    AppBackHandler(enabled = openSettingsMenu != null) { openSettingsMenu = null }
    val saveFile = rememberFileSaver()
    val importFile = rememberFileImporter { text ->
        if (text == null) return@rememberFileImporter
        val result = runCatching { ExportImportManager.importFromJson(text) }.getOrElse {
            diagnosticMsg = "Backup restore failed: ${it.message ?: "Invalid file"}"
            return@rememberFileImporter
        }
        result.fold(
            onSuccess = { counts ->
                diagnosticMsg = "Restored • ${counts.settingsImported} settings, ${counts.tasksImported} tasks"
            },
            onFailure = {
                diagnosticMsg = "Backup restore failed: ${it.message ?: "Not an AmazeCC backup file"}"
            }
        )
    }

    val state = M3ESettingsState(
        selectedThemeMode = selectedThemeMode,
        selectedPalettePreset = selectedPalettePreset,
        honoApiBaseUrl = honoApiUrl,
        cacheSizeText = cacheSize,
        isDiagnosticsRunning = isDiagnosticsRunning,
        diagnosticStatus = diagnosticMsg,
        classAlertsEnabled = classAlerts,
        attendanceAlertsEnabled = attendanceAlerts,
        examRemindersEnabled = examReminders,
        vtopSource = vtopSource,
        selectedSyncMode = selectedSyncMode,
        settingsSearchQuery = settingsSearchQuery,
        visibleSettingsSections = visibleSettingsSections,
        privacyModeEnabled = privacyModeEnabled,
        pomodoroFocusMinutes = pomodoroFocus,
        pomodoroBreakMinutes = pomodoroBreak,
        pomodoroRounds = pomodoroRounds,
        classReminderLeadMinutes = classReminderLead,
        quietHoursEnabled = quietHoursEnabled,
        quietHoursStart = quietHoursStart,
        quietHoursEnd = quietHoursEnd,
        apiTargetName = apiTargetName,
        appIconTheme = appIconTheme,
        homePillStyle = AppState.homePillStyle.value,
        openSettingsMenu = openSettingsMenu,
        navBarStyle = navBarStyle
    )

    AmazeM3ETheme(isDark = isDark) {
        M3ESettingsScreen(
            state = state,
            onAction = { action ->
                when (action) {
                    is M3ESettingsAction.SelectThemeMode -> {
                        val newTheme = when (action.mode) {
                            M3EThemeMode.DARK_OLED -> AppTheme.DARK
                            M3EThemeMode.LIGHT -> AppTheme.LIGHT
                            M3EThemeMode.SYSTEM_DEFAULT -> AppTheme.SYSTEM
                        }
                        AppState.changeTheme(newTheme)
                    }
                    is M3ESettingsAction.SelectPalettePreset -> {
                        selectedPalettePreset = action.preset
                        val accent = when (action.preset) {
                            M3EPalettePreset.OCEAN_MIDNIGHT -> com.amazecc.app.shared.theme.AccentTheme.OCEAN
                            M3EPalettePreset.CRIMSON_VELVET -> {
                                AppState.setCustomAccent(androidx.compose.ui.graphics.Color(0xFFBA1A1A))
                                com.amazecc.app.shared.theme.AccentTheme.CUSTOM
                            }
                            M3EPalettePreset.EMERALD_MINT -> com.amazecc.app.shared.theme.AccentTheme.FOREST
                            M3EPalettePreset.AMBER_SUNSET -> com.amazecc.app.shared.theme.AccentTheme.SUNSET
                            M3EPalettePreset.PURPLE_DUSK -> com.amazecc.app.shared.theme.AccentTheme.LAVENDER
                        }
                        if (accent != com.amazecc.app.shared.theme.AccentTheme.CUSTOM) {
                            AppState.changeAccent(accent)
                        }
                    }
                    is M3ESettingsAction.UpdateHonoApiUrl -> {
                        honoApiUrl = action.url
                        AmazeClient.baseUrl = action.url
                        diagnosticMsg = "API URL updated to ${action.url}"
                    }
                    is M3ESettingsAction.ClearCache -> {
                        SettingsManager.clearLocalCache()
                        cacheSize = SettingsManager.getFormattedCacheSize()
                        diagnosticMsg = "Cache purged successfully ($cacheSize)"
                    }
                    is M3ESettingsAction.RunDiagnostics -> {
                        isDiagnosticsRunning = true
                        diagnosticMsg = "Running health check on $honoApiUrl..."
                        scope.launch {
                            val (healthy, pingMs) = AmazeClient.pingHealth()
                            isDiagnosticsRunning = false
                            diagnosticMsg = if (healthy) {
                                "Health check passed • Backend operational (${pingMs}ms)"
                            } else {
                                val hasCache = com.amazecc.app.shared.state.AppDataStore.academic.value.semesters.isNotEmpty()
                                if (hasCache) {
                                    "Hono API unreachable • Offline cache active ($cacheSize)"
                                } else {
                                    "Hono API unreachable • No local cache available"
                                }
                            }
                        }
                    }
                    is M3ESettingsAction.OpenGithubRepo -> openExternalUrl(
                        "https://github.com/${com.amazecc.app.shared.utils.UpdateConfig.GITHUB_OWNER}/" +
                            com.amazecc.app.shared.utils.UpdateConfig.GITHUB_REPO
                    )
                    is M3ESettingsAction.ToggleClassAlerts -> {
                        classAlerts = action.enabled
                        SettingsManager.setNotifClassRemindersEnabled(action.enabled)
                        AppState.rescheduleNotifications()
                    }
                    is M3ESettingsAction.ToggleAttendanceAlerts -> {
                        attendanceAlerts = action.enabled
                        SettingsManager.setNotifAssignmentRemindersEnabled(action.enabled)
                        AppState.rescheduleNotifications()
                    }
                    is M3ESettingsAction.ToggleExamReminders -> {
                        examReminders = action.enabled
                        SettingsManager.setNotifExamRemindersEnabled(action.enabled)
                        AppState.rescheduleNotifications()
                    }
                    is M3ESettingsAction.SendTestNotification -> {
                        diagnosticMsg = "Dispatching test notification..."
                        scope.launch {
                            val result = AppState.sendTestNotification()
                            diagnosticMsg = result
                        }
                    }
                    is M3ESettingsAction.SetVtopSource -> {
                        AmazeClient.setVtopSource(action.source)
                        vtopSource = action.source
                        diagnosticMsg = "VTOP source set to ${action.source.displayName}"
                    }
                    is M3ESettingsAction.SelectSyncMode -> {
                        selectedSyncMode = action.mode
                        SettingsManager.setString("selected_sync_mode", action.mode)
                    }
                    is M3ESettingsAction.TriggerManualSync -> {
                        diagnosticMsg = "Sync triggered for ${action.mode.replaceFirstChar { it.uppercase() }} mode..."
                        AppState.loadMode(action.mode, force = true)
                    }
                    // D-059 — migrated settings depth actions.
                    is M3ESettingsAction.UpdateSettingsSearch -> {
                        settingsSearchQuery = action.query
                        visibleSettingsSections = com.amazecc.app.shared.domain.overhaul.settingsSectionsForQuery(action.query)
                    }
                    is M3ESettingsAction.TogglePrivacyMode -> {
                        // AppState persists the value and keeps the Alt+P path in sync.
                        AppState.setPrivacyMode(action.enabled)
                    }
                    is M3ESettingsAction.UpdatePomodoroFocusMinutes -> {
                        pomodoroFocus = action.minutes.coerceIn(5, 120)
                        SettingsManager.setPomodoroFocusMinutes(pomodoroFocus)
                    }
                    is M3ESettingsAction.UpdatePomodoroBreakMinutes -> {
                        pomodoroBreak = action.minutes.coerceIn(1, 60)
                        SettingsManager.setPomodoroBreakMinutes(pomodoroBreak)
                    }
                    is M3ESettingsAction.UpdatePomodoroRounds -> {
                        pomodoroRounds = action.rounds.coerceIn(1, 16)
                        SettingsManager.setPomodoroRounds(pomodoroRounds)
                    }
                    is M3ESettingsAction.UpdateClassReminderLeadMinutes -> {
                        classReminderLead = action.minutes.coerceIn(5, 60)
                        SettingsManager.setNotifOffsetMinutes(classReminderLead)
                    }
                    is M3ESettingsAction.ToggleQuietHours -> {
                        quietHoursEnabled = action.enabled
                        SettingsManager.setQuietHoursEnabled(action.enabled)
                    }
                    is M3ESettingsAction.UpdateQuietHoursStart -> {
                        quietHoursStart = action.time
                        SettingsManager.setQuietHoursStart(action.time)
                    }
                    is M3ESettingsAction.UpdateQuietHoursEnd -> {
                        quietHoursEnd = action.time
                        SettingsManager.setQuietHoursEnd(action.time)
                    }
                    is M3ESettingsAction.SelectAppIconTheme -> {
                        if (setAppIconTheme(action.theme)) {
                            appIconTheme = action.theme
                            SettingsManager.setAppIconName(action.theme.name)
                            diagnosticMsg = "App icon set to ${action.theme.label}"
                        } else {
                            diagnosticMsg = "App icon themes are unsupported on this platform"
                        }
                    }
                    is M3ESettingsAction.SelectApiTarget -> {
                        AmazeClient.setApiTarget(action.target)
                        apiTargetName = action.target.name
                        honoApiUrl = AmazeClient.baseUrl
                        diagnosticMsg = "API target: ${action.target.label}"
                    }
                    is M3ESettingsAction.SelectHomePillStyle -> AppState.setHomePillStyle(action.style)
                    M3ESettingsAction.ExportSettings -> {
                        scope.launch {
                            val backupName = ExportImportManager.backupFileName()
                            val saved = saveFile(backupName, ExportImportManager.buildBackupJson(false).toByteArray())
                            diagnosticMsg = if (saved) "Backup saved to Downloads ($backupName)" else "Backup export failed"
                        }
                    }
                    M3ESettingsAction.ImportSettings -> importFile()
                    M3ESettingsAction.OpenShortcutsHelp -> AppState.openShortcutsHelp()
                    is M3ESettingsAction.OpenSettingsMenu -> openSettingsMenu = action.id
                    M3ESettingsAction.CloseSettingsMenu -> openSettingsMenu = null
                    is M3ESettingsAction.SelectNavBarStyle -> AppState.setNavBarStyle(action.style)
                }
            },
            modifier = modifier
        )
    }
}

@Composable
fun M3EMoreHubScreen(modifier: Modifier = Modifier) {
    val isDark = M3EStateBridge.isDarkThemeActive()
    var searchQuery by remember { mutableStateOf("") }

    val state = M3EMoreHubState(
        searchQuery = searchQuery,
        activeSubScreen = null
    )

    AmazeM3ETheme(isDark = isDark) {
        M3EMoreHubScreen(
            state = state,
            onAction = { action ->
                when (action) {
                    is M3EMoreHubAction.SearchQueryChanged -> searchQuery = action.query
                    is M3EMoreHubAction.OpenSubScreen -> {
                        when (action.screen) {
                            M3ESubScreen.ACADEMICS_HUB -> AppState.navigateTo(Screen.ACADEMICS)
                            M3ESubScreen.ATTENDANCE -> AppState.navigateTo(Screen.ATTENDANCE)
                            M3ESubScreen.ATTENDANCE_PREDICTOR -> AppState.navigateTo(Screen.ATTENDANCE_PREDICTOR)
                            M3ESubScreen.LIBRARIES -> AppState.navigateTo(Screen.LIBRARIES)
                            // D-140: both legacy tiles now open the single unified hub. The old
                            // COURSE_DETAIL tile had no course code and fell back to an arbitrary
                            // course; routing it to the hub avoids that broken deep link.
                            M3ESubScreen.COURSE_DETAIL -> AppState.navigateTo(Screen.ACADEMICS)
                            M3ESubScreen.COURSE_ATTENDANCE -> AppState.navigateTo(Screen.COURSE_ATTENDANCE)
                            M3ESubScreen.COURSE_DASHBOARD -> AppState.navigateTo(Screen.ACADEMICS)
                            M3ESubScreen.FACULTY -> AppState.navigateTo(Screen.FACULTY_INFO)
                            M3ESubScreen.CURRICULUM -> AppState.navigateTo(Screen.CURRICULUM)
                            M3ESubScreen.MARKS_PREDICTOR -> AppState.navigateTo(Screen.GPA_PREDICTOR)
                            M3ESubScreen.EXAM_SCHEDULE -> AppState.navigateTo(Screen.EXAM_SCHEDULE)
                            M3ESubScreen.GRADES_HISTORY -> AppState.navigateTo(Screen.GRADES)
                            M3ESubScreen.CALENDAR -> AppState.navigateTo(Screen.CALENDAR)
                            M3ESubScreen.FFCS_PLANNER -> AppState.navigateTo(Screen.FFCS_PLANNER)
                            M3ESubScreen.MOODLE -> AppState.navigateTo(Screen.MOODLE)
                            M3ESubScreen.OD_TRACKER -> AppState.navigateTo(Screen.OD_TRACKER)
                            M3ESubScreen.FEEDBACK_STATUS -> AppState.navigateTo(Screen.FEEDBACK_STATUS)
                            M3ESubScreen.QBANK -> AppState.navigateTo(Screen.QBANK)
                            // D-153: the report tile opens the normal, nav-bar-visible route.
                            M3ESubScreen.STUDENT_REPORT -> AppState.navigateTo(Screen.STUDENT_REPORT)
                            M3ESubScreen.HOSTEL -> AppState.navigateTo(Screen.HOSTEL)
                            M3ESubScreen.TRANSPORT -> AppState.navigateTo(Screen.TRANSPORT)
                            M3ESubScreen.CAB_SHARE -> AppState.navigateTo(Screen.CABSHARE)
                            M3ESubScreen.FREE_CLASSROOMS -> AppState.navigateTo(Screen.FREE_CLASSROOMS)
                            M3ESubScreen.PAYMENTS -> AppState.navigateTo(Screen.PAYMENTS)
                            M3ESubScreen.CIRCULARS -> AppState.navigateTo(Screen.CIRCULARS)
                            M3ESubScreen.DOCUMENTS -> AppState.navigateTo(Screen.DOCUMENTS)
                            M3ESubScreen.EVENT_HUB -> AppState.navigateTo(Screen.EVENTS)
                            M3ESubScreen.CLUB_HUB -> AppState.navigateTo(Screen.CLUB_HUB)
                            M3ESubScreen.TASKS -> AppState.navigateTo(Screen.TASKS)
                            M3ESubScreen.SOCIAL -> AppState.navigateTo(Screen.SOCIAL)
                            M3ESubScreen.FRESHER_WELCOME -> AppState.navigateTo(Screen.FRESHER_WELCOME)
                            M3ESubScreen.HALL_OF_FAME -> AppState.navigateTo(Screen.HALL_OF_FAME)
                            M3ESubScreen.PROFILE -> AppState.navigateTo(Screen.PROFILE)
                            M3ESubScreen.SETTINGS -> AppState.navigateTo(Screen.SETTINGS)
                            M3ESubScreen.ABOUT -> AppState.navigateTo(Screen.ABOUT)
                            M3ESubScreen.CHANGELOG -> AppState.navigateTo(Screen.CHANGELOG)
                        }
                    }
                    is M3EMoreHubAction.BackToHub -> {
                        if (AppState.canNavigateBack()) {
                            AppState.navigateBack()
                        } else {
                            AppState.navigateTo(Screen.MORE)
                        }
                    }
                }
            },
            modifier = modifier
        )
    }
}
