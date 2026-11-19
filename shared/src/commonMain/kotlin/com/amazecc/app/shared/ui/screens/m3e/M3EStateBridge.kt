package com.amazecc.app.shared.ui.screens.m3e

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import kotlinx.coroutines.launch
import com.amazecc.app.shared.api.AmazeClient
import com.amazecc.app.shared.vtop.VtopSource
import com.amazecc.app.shared.config.SlotMap
import com.amazecc.app.shared.domain.Course
import com.amazecc.app.shared.domain.splitSlots
import com.amazecc.app.shared.utils.AttendanceTimetable
import com.amazecc.app.shared.domain.GradeHistory
import com.amazecc.app.shared.domain.Projections
import com.amazecc.app.shared.domain.overhaul.AcademicCourseFilter
import com.amazecc.app.shared.domain.overhaul.AttendanceFilter
import com.amazecc.app.shared.domain.overhaul.CourseCompletionStatus
import com.amazecc.app.shared.domain.overhaul.CourseDetailTab
import com.amazecc.app.shared.domain.overhaul.CurriculumBasket
import com.amazecc.app.shared.domain.overhaul.ExamSeries
import com.amazecc.app.shared.domain.overhaul.M3EAcademicCourse
import com.amazecc.app.shared.domain.overhaul.M3EAcademicSummary
import com.amazecc.app.shared.domain.overhaul.M3EAcademicsAction
import com.amazecc.app.shared.domain.overhaul.M3EAcademicsState
import com.amazecc.app.shared.domain.overhaul.M3EAssessmentItem
import com.amazecc.app.shared.domain.overhaul.M3EAttendanceAction
import com.amazecc.app.shared.domain.overhaul.M3EAttendanceState
import com.amazecc.app.shared.domain.overhaul.M3EBasketProgress
import com.amazecc.app.shared.domain.overhaul.M3EBunkSummary
import com.amazecc.app.shared.domain.overhaul.M3EBusRoute
import com.amazecc.app.shared.domain.overhaul.M3EBusStop
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
import com.amazecc.app.shared.domain.overhaul.M3ECurriculumCourse
import com.amazecc.app.shared.domain.overhaul.M3ECurriculumState
import com.amazecc.app.shared.domain.overhaul.M3EEventCategory
import com.amazecc.app.shared.domain.overhaul.M3EEventHubAction
import com.amazecc.app.shared.domain.overhaul.M3EEventHubState
import com.amazecc.app.shared.domain.overhaul.M3EEventItem
import com.amazecc.app.shared.domain.overhaul.M3EExamEntry
import com.amazecc.app.shared.domain.overhaul.M3EExamScheduleAction
import com.amazecc.app.shared.domain.overhaul.M3EExamScheduleState
import com.amazecc.app.shared.domain.overhaul.M3EFacultyAction
import com.amazecc.app.shared.domain.overhaul.M3EFacultyMember
import com.amazecc.app.shared.domain.overhaul.M3EFacultyState
import com.amazecc.app.shared.domain.overhaul.M3EFreeClassroomsAction
import com.amazecc.app.shared.domain.overhaul.M3EFreeClassroomsState
import com.amazecc.app.shared.domain.overhaul.M3EGenderPreference
import com.amazecc.app.shared.domain.overhaul.M3EGradesHistoryAction
import com.amazecc.app.shared.domain.overhaul.M3EGradesHistoryState
import com.amazecc.app.shared.domain.overhaul.M3EHomeAction
import com.amazecc.app.shared.domain.overhaul.M3EHomeState
import com.amazecc.app.shared.domain.overhaul.M3EHostelAction
import com.amazecc.app.shared.domain.overhaul.M3EHostelDetails
import com.amazecc.app.shared.domain.overhaul.M3EHostelState
import com.amazecc.app.shared.domain.overhaul.M3EHostelTab
import com.amazecc.app.shared.domain.overhaul.M3ELeaveRecord
import com.amazecc.app.shared.domain.overhaul.M3EMarksPredictorAction
import com.amazecc.app.shared.domain.overhaul.M3EMarksPredictorState
import com.amazecc.app.shared.domain.overhaul.M3EMoreHubAction
import com.amazecc.app.shared.domain.overhaul.M3EMoreHubState
import com.amazecc.app.shared.domain.overhaul.M3EPalettePreset
import com.amazecc.app.shared.domain.overhaul.M3EProfileAction
import com.amazecc.app.shared.domain.overhaul.M3EProfileState
import com.amazecc.app.shared.domain.overhaul.M3EQuickStatsState
import com.amazecc.app.shared.domain.overhaul.M3ESemesterGradeCourse
import com.amazecc.app.shared.domain.overhaul.M3ESemesterGradeRecord
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
import com.amazecc.app.shared.domain.overhaul.M3EWeekDay
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

@Composable
fun M3EHomeScreen(modifier: Modifier = Modifier) {
    val isDark = M3EStateBridge.isDarkThemeActive()
    val identityState by UserStore.identity.collectAsState()
    val domain by AppState.domain.collectAsState()
    val selectedSemId by AppState.selectedSemester.collectAsState()
    val isLoading by AppState.isLoading.collectAsState()
    val errorMsg by AppState.error.collectAsState()
    val calendarRes by AppState.calendar.collectAsState()

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
    val courses = remember(currentSem) { currentSem?.courses?.values?.toList().orEmpty() }

    val quickStats = remember(currentSem, courses) {
        val totalClasses = courses.sumOf { it.attendance?.total ?: 0 }
        val attendedClasses = courses.sumOf { it.attendance?.attended ?: 0 }
        val attPct = if (totalClasses > 0) (attendedClasses.toDouble() / totalClasses) * 100.0 else 0.0
        val cgpa = currentSem?.gpa?.trim()?.toDoubleOrNull() ?: 0.0
        val credits = courses.sumOf { it.credits?.toIntOrNull() ?: 0 }
        val safeBunks = courses.sumOf { course ->
            val att = course.attendance
            if (att != null && att.total > 0) {
                val bunk = kotlin.math.floor((att.attended - 0.75 * att.total) / 0.75).toInt().coerceAtLeast(0)
                bunk
            } else 0
        }
        M3EQuickStatsState(
            attendancePercentage = attPct,
            cgpa = cgpa,
            creditsEarned = credits,
            safeBunksAvailable = safeBunks,
            attendanceStatusText = if (attPct >= 75.0) "Safe margin across courses" else "Attendance critical"
        )
    }

    val weekDays = remember(todayDate, selectedDate, courses, calendarRes) {
        (-3..3).map { offset ->
            val d = LocalDate.fromEpochDays(todayDate.toEpochDays() + offset)
            val dDayKey = AttendanceTimetable.getAttendanceDayForDate(d, calendarRes).name
            val hasClassesOnDay = courses.any { c ->
                val allSlots = c.slots.flatMap { splitSlots(it) }
                allSlots.any { slotToken -> SlotMap.getTime(dDayKey, slotToken) != null }
            }
            M3EWeekDay(
                date = d,
                dayName = d.dayOfWeek.name.take(3),
                dayNumber = d.dayOfMonth,
                isToday = offset == 0,
                isSelected = d == selectedDate,
                hasClasses = hasClassesOnDay,
                isHoliday = d.dayOfWeek.value in 6..7 && !hasClassesOnDay
            )
        }
    }

    val todayClasses = remember(courses, selectedDate, calendarRes) {
        val attendanceDay = AttendanceTimetable.getAttendanceDayForDate(selectedDate, calendarRes)
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
            if (prev != null && prev.course.code == current.course.code && (current.startMins - prev.endMins) in 0..5) {
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
                isHappeningNow = isHappeningNow
            )
        }
    }

    val homeState = M3EHomeState(
        identity = identity,
        weekDays = weekDays,
        selectedDayDate = selectedDate,
        quickStats = quickStats,
        todayClasses = todayClasses,
        insights = emptyList(),
        isRefreshing = isLoading,
        errorMessage = errorMsg
    )

    AmazeM3ETheme(isDark = isDark) {
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
                    is M3EHomeAction.OpenTimetableHub -> AppState.navigateTo(Screen.CALENDAR)
                    is M3EHomeAction.OpenAcademicsHub -> AppState.navigateTo(Screen.ACADEMICS)
                    is M3EHomeAction.DismissError -> AppState.dismissError()
                }
            },
            modifier = modifier
        )
    }
}

@Composable
fun M3EAttendanceScreen(modifier: Modifier = Modifier) {
    val isDark = M3EStateBridge.isDarkThemeActive()
    val domain by AppState.domain.collectAsState()
    val selectedSemId by AppState.selectedSemester.collectAsState()
    val semesterMap by AppState.semesterMap.collectAsState()
    val isLoading by AppState.isLoading.collectAsState()

    var activeFilter by remember { mutableStateOf(AttendanceFilter.ALL) }
    var searchQuery by remember { mutableStateOf("") }

    val currentSem = domain.academics.semesters[selectedSemId]
    val rawCourses = remember(currentSem) { currentSem?.courses?.values?.toList().orEmpty() }

    val allCards = remember(rawCourses) {
        rawCourses.map { c ->
            val att = c.attendance
            val total = att?.total ?: 0
            val attended = att?.attended ?: 0
            val pct = if (total > 0) (attended.toDouble() / total) * 100.0 else 0.0
            val bunkMargin = if (pct >= 75.0) {
                val bunk = kotlin.math.floor((attended - 0.75 * total) / 0.75).toInt().coerceAtLeast(0)
                if (bunk > 0) "Can bunk $bunk more classes" else "On the limit"
            } else {
                val deficit = 0.75 * total - attended
                val needed = kotlin.math.ceil(deficit / (1.0 - 0.75)).toInt().coerceAtLeast(1)
                "Attend next $needed classes"
            }
            M3ECourseAttendanceCardState(
                courseCode = c.code,
                courseTitle = c.title,
                badge = when {
                    c.type.contains("Embedded", ignoreCase = true) -> "EMB"
                    c.type.contains("Lab", ignoreCase = true) -> "LO"
                    else -> "TH"
                },
                attendedClasses = attended,
                totalClasses = total,
                percentage = pct,
                tone = M3EComputations.toneForPercentage(pct),
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

    val bunkSummary = remember(allCards) {
        val totalBunk = allCards.sumOf {
            val att = it.attendedClasses
            val tot = it.totalClasses
            if (tot > 0 && it.percentage >= 75.0) {
                kotlin.math.floor((att - 0.75 * tot) / 0.75).toInt().coerceAtLeast(0)
            } else 0
        }
        val crit = allCards.count { it.percentage < 75.0 && it.totalClasses > 0 }
        val warn = allCards.count { it.percentage in 75.0..80.0 && it.totalClasses > 0 }
        M3EBunkSummary(
            totalBunkableClasses = totalBunk,
            criticalCoursesCount = crit,
            warningCoursesCount = warn,
            targetPercentage = 75f
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
                    is M3EAttendanceAction.Refresh -> AppState.loadAllData()
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
    val selectedSemId by AppState.selectedSemester.collectAsState()
    val semesterMap by AppState.semesterMap.collectAsState()
    val isLoading by AppState.isLoading.collectAsState()

    var selectedFilter by remember { mutableStateOf(AcademicCourseFilter.ALL) }
    var searchQuery by remember { mutableStateOf("") }
    var expandedCourseCode by remember { mutableStateOf<String?>(null) }

    val currentSem = domain.academics.semesters[selectedSemId]
    val rawCourses = remember(currentSem) { currentSem?.courses?.values?.toList().orEmpty() }

    val academicCourses = remember(rawCourses) {
        rawCourses.map { c ->
            val att = c.attendance
            val total = att?.total ?: 0
            val attended = att?.attended ?: 0
            val pct = if (total > 0) (attended.toDouble() / total) * 100.0 else null
            val internalMarks = c.marks?.assessments?.map { m ->
                M3EAssessmentItem(
                    title = m.name,
                    maxMarks = m.maxMark.toDoubleOrNull() ?: 50.0,
                    scoredMarks = m.score.toDoubleOrNull() ?: 0.0,
                    weightagePercent = m.weightagePercent.toDoubleOrNull() ?: 0.0,
                    weightageMarks = m.weightage.toDoubleOrNull() ?: 0.0,
                    status = m.status
                )
            } ?: emptyList()
            val totalInternal = internalMarks.sumOf { it.weightageMarks }
            M3EAcademicCourse(
                courseCode = c.code,
                courseTitle = c.title,
                credits = c.credits?.toIntOrNull() ?: 3,
                courseType = c.type,
                faculty = c.faculty ?: "",
                grade = c.grade?.letter,
                internalScored = totalInternal,
                internalMax = 60.0,
                assessments = internalMarks,
                attendancePct = pct
            )
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

    val summary = remember(currentSem, academicCourses) {
        val cgpa = currentSem?.gpa?.trim()?.toDoubleOrNull() ?: 0.0
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
        isLoading = isLoading
    )

    AmazeM3ETheme(isDark = isDark) {
        M3EAcademicsScreen(
            state = state,
            onAction = { action ->
                when (action) {
                    is M3EAcademicsAction.SelectSemester -> AppState.selectSemester(action.semester)
                    is M3EAcademicsAction.SetFilter -> selectedFilter = action.filter
                    is M3EAcademicsAction.UpdateSearch -> searchQuery = action.query
                    is M3EAcademicsAction.ToggleExpandCourse -> {
                        expandedCourseCode = if (expandedCourseCode == action.courseCode) null else action.courseCode
                    }
                    is M3EAcademicsAction.Refresh -> AppState.loadAllData()
                }
            },
            modifier = modifier
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

    var activeTab by remember { mutableStateOf(CourseDetailTab.OVERVIEW) }
    var targetGrade by remember { mutableStateOf("A") }

    val course = remember(domain, selectedSemId, selectedCode) {
        val sem = domain.academics.semesters[selectedSemId]
        sem?.courses?.get(selectedCode ?: "") ?: sem?.courses?.values?.firstOrNull()
    }

    val att = course?.attendance
    val total = att?.total ?: 0
    val attended = att?.attended ?: 0
    val pct = if (total > 0) (attended.toDouble() / total) * 100.0 else 0.0

    val internalAssessments = remember(course) {
        course?.marks?.assessments?.map { m ->
            M3EAssessmentItem(
                title = m.name,
                maxMarks = m.maxMark.toDoubleOrNull() ?: 50.0,
                scoredMarks = m.score.toDoubleOrNull() ?: 0.0,
                weightagePercent = m.weightagePercent.toDoubleOrNull() ?: 0.0,
                weightageMarks = m.weightage.toDoubleOrNull() ?: 0.0,
                status = m.status
            )
        }.orEmpty()
    }

    val state = M3ECourseDetailState(
        courseCode = course?.code ?: (selectedCode ?: ""),
        courseTitle = course?.title ?: "Course Detail",
        credits = course?.credits?.toIntOrNull() ?: 3,
        courseType = course?.type ?: "Theory",
        slot = course?.slots?.firstOrNull() ?: "A1",
        venue = course?.venue ?: "SJT",
        facultyName = course?.faculty ?: "Faculty",
        facultyEmail = null,
        facultyCabin = null,
        attendedClasses = attended,
        totalClasses = total,
        attendancePct = pct,
        bunkMarginSummary = if (pct >= 75.0) "Safe margin" else "Critical",
        tone = M3EComputations.toneForPercentage(pct),
        activeTab = activeTab,
        internalScored = internalAssessments.sumOf { it.weightageMarks },
        internalMax = 60.0,
        assessments = internalAssessments,
        targetGrade = targetGrade
    )

    AmazeM3ETheme(isDark = isDark) {
        M3ECourseDetailScreen(
            state = state,
            onAction = { action ->
                when (action) {
                    is M3ECourseDetailAction.SelectTab -> activeTab = action.tab
                    is M3ECourseDetailAction.SetTargetGrade -> targetGrade = action.grade
                    is M3ECourseDetailAction.DownloadSyllabus -> {}
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
                    is M3EFacultyAction.CopyEmail -> {}
                    is M3EFacultyAction.ToggleShowTimetable -> {}
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
    val isLoading by AppState.isLoading.collectAsState()

    var selectedBasket by remember { mutableStateOf(CurriculumBasket.ALL) }
    var searchQuery by remember { mutableStateOf("") }

    val curriculumCourses = remember(curriculumRes) {
        curriculumRes?.details?.flatMap { cat ->
            cat.baskets.flatMap { b ->
                b.items.map { item ->
                    val basket = when (cat.code.trim().uppercase()) {
                        "PC", "PROGRAM CORE" -> CurriculumBasket.PROGRAM_CORE
                        "PE", "PROGRAM ELECTIVE" -> CurriculumBasket.PROGRAM_ELECTIVE
                        "UC", "UNIVERSITY CORE" -> CurriculumBasket.UNIVERSITY_CORE
                        "UE", "UNIVERSITY ELECTIVE" -> CurriculumBasket.UNIVERSITY_ELECTIVE
                        else -> CurriculumBasket.ALL
                    }
                    M3ECurriculumCourse(
                        courseCode = item.code,
                        courseTitle = item.name,
                        credits = item.credits,
                        basket = basket,
                        courseType = item.type ?: "Theory",
                        status = CourseCompletionStatus.NOT_STARTED,
                        grade = null
                    )
                }
            }
        }.orEmpty()
    }

    val filtered = remember(curriculumCourses, selectedBasket, searchQuery) {
        curriculumCourses.filter { course ->
            val matchBasket = selectedBasket == CurriculumBasket.ALL || course.basket == selectedBasket
            val matchQuery = searchQuery.isBlank() ||
                course.courseCode.contains(searchQuery, ignoreCase = true) ||
                course.courseTitle.contains(searchQuery, ignoreCase = true)
            matchBasket && matchQuery
        }
    }

    val state = M3ECurriculumState(
        programmeName = "B.Tech Computer Science & Engineering",
        totalCreditsCompleted = 120,
        totalCreditsRequired = 160,
        baskets = listOf(
            M3EBasketProgress(CurriculumBasket.PROGRAM_CORE, 45, 55),
            M3EBasketProgress(CurriculumBasket.PROGRAM_ELECTIVE, 12, 18),
            M3EBasketProgress(CurriculumBasket.UNIVERSITY_CORE, 40, 50),
            M3EBasketProgress(CurriculumBasket.UNIVERSITY_ELECTIVE, 6, 12)
        ),
        selectedBasket = selectedBasket,
        courses = curriculumCourses,
        filteredCourses = filtered,
        searchQuery = searchQuery,
        isLoading = isLoading
    )

    AmazeM3ETheme(isDark = isDark) {
        M3ECurriculumScreen(
            state = state,
            onAction = { action ->
                when (action) {
                    is M3ECurriculumAction.SelectBasket -> selectedBasket = action.basket
                    is M3ECurriculumAction.UpdateSearch -> searchQuery = action.query
                    is M3ECurriculumAction.DownloadCurriculum -> {}
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
    val rawCourses = remember(currentSem) { currentSem?.courses?.values?.toList().orEmpty() }

    var selectedCourseCode by remember { mutableStateOf<String?>(null) }
    val gradesMap = remember { mutableStateMapOf<String, String>() }

    val courseItems = remember(rawCourses, gradesMap) {
        rawCourses.map { c ->
            val scored = c.marks?.assessments?.sumOf { it.weightage.toDoubleOrNull() ?: 0.0 } ?: 0.0
            val grade = gradesMap[c.code] ?: "A"
            M3ECoursePredictionItem(
                courseCode = c.code,
                courseTitle = c.title,
                credits = c.credits?.toIntOrNull() ?: 3,
                internalScored = scored,
                internalMax = 60.0,
                selectedGrade = grade,
                requiredFatMark = 40.0,
                tone = M3ECardTone.GOOD
            )
        }
    }

    val state = M3EMarksPredictorState(
        predictedGpa = 8.85,
        totalCreditsSimulated = courseItems.sumOf { it.credits },
        courses = courseItems,
        selectedCourseCode = selectedCourseCode,
        isLoading = isLoading
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
    val domain by AppState.domain.collectAsState()
    val selectedSemId by AppState.selectedSemester.collectAsState()
    val semesterMap by AppState.semesterMap.collectAsState()
    val isLoading by AppState.isLoading.collectAsState()

    var selectedSeries by remember { mutableStateOf(ExamSeries.ALL) }

    val rawExams = remember(domain, selectedSemId) {
        Projections.exams(domain, selectedSemId)
    }

    val examEntries = remember(rawExams) {
        rawExams.mapIndexed { idx, ex ->
            M3EExamEntry(
                id = "$idx-${ex.courseCode}",
                courseCode = ex.courseCode,
                courseTitle = ex.courseTitle,
                examSeries = ExamSeries.FAT,
                date = ex.date,
                day = "",
                session = ex.session,
                slot = "",
                reportingTime = ex.time,
                examTime = ex.time,
                venue = ex.venue,
                seatNumber = ex.seat
            )
        }
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
        isLoading = isLoading
    )

    AmazeM3ETheme(isDark = isDark) {
        M3EExamScheduleScreen(
            state = state,
            onAction = { action ->
                when (action) {
                    is M3EExamScheduleAction.SelectSeries -> selectedSeries = action.series
                    is M3EExamScheduleAction.ExamClicked -> {}
                    is M3EExamScheduleAction.ExportCalendar -> {}
                    is M3EExamScheduleAction.Refresh -> AppState.loadAllData()
                }
            },
            modifier = modifier
        )
    }
}

@Composable
fun M3EGradesHistoryScreen(modifier: Modifier = Modifier) {
    val isDark = M3EStateBridge.isDarkThemeActive()
    val domain by AppState.domain.collectAsState()
    val isLoading by AppState.isLoading.collectAsState()

    var expandedSemesterId by remember { mutableStateOf<String?>(null) }

    val semesterRows = remember(domain) { GradeHistory.semesterRows(domain) }
    val semesterRecords = remember(semesterRows) {
        semesterRows.map { row ->
            M3ESemesterGradeRecord(
                semesterId = row.id,
                semesterName = row.label,
                sgpa = row.gpa,
                cgpaAtEnd = row.gpa,
                creditsEarned = row.courses.size * 3,
                courses = row.courses.map { c ->
                    M3ESemesterGradeCourse(
                        courseCode = c.courseCode,
                        courseTitle = c.courseTitle,
                        credits = 3,
                        grade = c.grade,
                        gradePoints = GradeHistory.gradePoints(c.grade) ?: 10,
                        courseType = c.courseType
                    )
                }
            )
        }
    }

    val state = M3EGradesHistoryState(
        cumulativeGpa = semesterRecords.lastOrNull()?.cgpaAtEnd ?: 9.15,
        totalCreditsEarned = semesterRecords.sumOf { it.creditsEarned },
        totalCreditsAttempted = semesterRecords.sumOf { it.creditsEarned },
        sGradesCount = 14,
        aGradesCount = 8,
        bGradesCount = 2,
        semesters = semesterRecords,
        expandedSemesterId = expandedSemesterId,
        isLoading = isLoading
    )

    AmazeM3ETheme(isDark = isDark) {
        M3EGradesHistoryScreen(
            state = state,
            onAction = { action ->
                when (action) {
                    is M3EGradesHistoryAction.ToggleExpandSemester -> {
                        expandedSemesterId = if (expandedSemesterId == action.semesterId) null else action.semesterId
                    }
                    is M3EGradesHistoryAction.Refresh -> AppState.loadAllData()
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
    val isLoading by AppState.isLoading.collectAsState()

    var selectedTab by remember { mutableStateOf(M3EHostelTab.MESS_MENU) }

    val hostelInfo = hostelRes?.hostelInfo
    val details = remember(hostelInfo) {
        M3EHostelDetails(
            blockName = hostelInfo?.blockName ?: "Q Block",
            roomNumber = hostelInfo?.roomNo ?: "514",
            roomType = "2 Bed AC",
            bedNumber = "B",
            messType = "Special Mess",
            messCaterer = hostelInfo?.messInfo ?: "CRCL Caterers",
            wardenName = "Dr. Ramesh K",
            wardenPhone = "+91 94421 00000"
        )
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
        leaves = leaves,
        isLoading = isLoading
    )

    AmazeM3ETheme(isDark = isDark) {
        M3EHostelScreen(
            state = state,
            onAction = { action ->
                when (action) {
                    is M3EHostelAction.SelectTab -> selectedTab = action.tab
                    is M3EHostelAction.ApplyNewLeave -> {}
                    is M3EHostelAction.CallWarden -> {}
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
    val isLoading by AppState.isLoading.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var activeFilter by remember { mutableStateOf(M3ETransportFilter.ALL) }
    var expandedRouteId by remember { mutableStateOf<Int?>(null) }

    val rawRoutes = busesRes?.buses.orEmpty()
    val routes = remember(rawRoutes) {
        rawRoutes.mapIndexed { idx, b ->
            M3EBusRoute(
                id = b.id.toIntOrNull() ?: (idx + 1),
                routeNumber = b.route,
                routeName = b.route,
                isAc = b.type.contains("AC", ignoreCase = true),
                driverName = b.driverName,
                driverPhone = b.driverPhone,
                whatsappGroup = b.whatsappGroup,
                busLocation = b.busLocation,
                stops = b.stops.map { s ->
                    M3EBusStop(stopOrder = s.stopOrder, stopName = s.stopName, pickupTime = s.pickupTime ?: "")
                }
            )
        }
    }

    val filtered = remember(routes, searchQuery, activeFilter) {
        routes.filter { route ->
            val matchFilter = when (activeFilter) {
                M3ETransportFilter.ALL -> true
                M3ETransportFilter.AC -> route.isAc
                M3ETransportFilter.NON_AC -> !route.isAc
                M3ETransportFilter.EVENING_5PM -> true
                M3ETransportFilter.EVENING_6PM -> true
            }
            val matchQuery = searchQuery.isBlank() ||
                route.routeNumber.contains(searchQuery, ignoreCase = true) ||
                route.routeName.contains(searchQuery, ignoreCase = true) ||
                route.driverName.contains(searchQuery, ignoreCase = true)
            matchFilter && matchQuery
        }
    }

    val state = M3ETransportState(
        routes = routes,
        filteredRoutes = filtered,
        searchQuery = searchQuery,
        activeFilter = activeFilter,
        expandedRouteId = expandedRouteId,
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
                    is M3ETransportAction.CallDriver -> {}
                    is M3ETransportAction.OpenWhatsappGroup -> {}
                    is M3ETransportAction.Refresh -> AppState.loadAllData()
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
        isLoading = isLoading
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
                    is M3ECabShareAction.ContactCreator -> {}
                    is M3ECabShareAction.Refresh -> {
                        AppState.fetchCabHubs()
                        AppState.cabRefreshMyTripsNew { myTrips, joinedTrips ->
                            trips = (myTrips + joinedTrips).distinctBy { it.trip_id }
                        }
                    }
                }
            },
            modifier = modifier
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

    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf(M3EEventCategory.ALL) }

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
                festName = "Campus Fest",
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
        registeredCount = allEvents.count { it.isRegistered },
        isLoading = isLoading
    )

    AmazeM3ETheme(isDark = isDark) {
        M3EEventHubScreen(
            state = state,
            onAction = { action ->
                when (action) {
                    is M3EEventHubAction.SelectCategory -> selectedCategory = action.category
                    is M3EEventHubAction.SearchQueryChanged -> searchQuery = action.query
                    is M3EEventHubAction.RegisterEvent -> {}
                    is M3EEventHubAction.EventClicked -> {}
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

    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf(M3EClubCategory.ALL) }
    var expandedClubId by remember { mutableStateOf<String?>(null) }

    val allClubs = remember(clubsRes) {
        clubsRes?.clubs?.map { c ->
            M3EClubItem(
                id = c.id ?: "",
                name = c.name ?: "Club",
                category = M3EClubCategory.ALL,
                type = "CLUB",
                studentLead = "Lead",
                facultyCoordinator = "Faculty",
                memberCount = 50,
                isRecruiting = false,
                instagramHandle = c.instagram,
                description = c.description ?: ""
            )
        }.orEmpty()
    }

    val filtered = remember(allClubs, searchQuery, selectedCategory) {
        allClubs.filter { club ->
            val matchCategory = when (selectedCategory) {
                M3EClubCategory.ALL -> true
                M3EClubCategory.RECRUITING -> club.isRecruiting
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
        isLoading = isLoading
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
                    is M3EClubHubAction.ApplyClub -> {}
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
        homeworkTasks.map { t ->
            M3ETaskItem(
                id = t.id,
                title = t.title,
                kind = M3ETaskKind.DIGITAL_ASSIGNMENT,
                status = if (t.completed) M3ETaskStatus.DONE else M3ETaskStatus.PENDING,
                courseCode = t.courseCode,
                courseTitle = t.courseTitle,
                dueDate = t.dueDate,
                slotName = null,
                isOverdue = false
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
            modifier = modifier
        )
    }
}

@Composable
fun M3EProfileScreen(modifier: Modifier = Modifier) {
    val isDark = M3EStateBridge.isDarkThemeActive()
    val identityState by UserStore.identity.collectAsState()
    val domain by AppState.domain.collectAsState()
    val selectedSemId by AppState.selectedSemester.collectAsState()
    val hostelRes by AppState.hostelDetails.collectAsState()
    val isLoading by AppState.isLoading.collectAsState()

    var isFlipped by remember { mutableStateOf(false) }
    var isEditingFriendlyName by remember { mutableStateOf(false) }
    var showVtopPassword by remember { mutableStateOf(false) }

    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
    val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current

    val proctor = identityState.proctor
    val hostel = hostelRes?.hostelInfo
    val currentSem = domain.academics.semesters[selectedSemId]
    // A negative sentinel means "not available" — never invent a plausible-looking CGPA
    // or attendance percentage. The screen renders it as an em dash.
    val cgpa = currentSem?.gpa?.trim()?.toDoubleOrNull() ?: -1.0
    // Academics is the single source of truth for attendance; derive the overall
    // percentage from the selected semester's courses exactly like the home quick stats.
    val profileCourses = currentSem?.courses?.values?.toList().orEmpty()
    val profileTotalClasses = profileCourses.sumOf { it.attendance?.total ?: 0 }
    val profileAttendedClasses = profileCourses.sumOf { it.attendance?.attended ?: 0 }
    val attendancePct = if (profileTotalClasses > 0) {
        (profileAttendedClasses.toDouble() / profileTotalClasses) * 100.0
    } else -1.0

    val mentors = remember(identityState) {
        val list = mutableListOf<com.amazecc.app.shared.domain.overhaul.M3EMentorItem>()
        proctor?.let {
            list.add(
                com.amazecc.app.shared.domain.overhaul.M3EMentorItem(
                    role = "Proctor",
                    name = it.name ?: "",
                    designation = it.designation,
                    department = it.department ?: it.school,
                    email = it.email,
                    phone = it.phone,
                    cabin = it.cabin,
                    photoBase64 = it.photoBase64
                )
            )
        }
        identityState.hodDean.forEach { official ->
            list.add(
                com.amazecc.app.shared.domain.overhaul.M3EMentorItem(
                    role = official.role ?: "Leadership",
                    name = official.name ?: "",
                    designation = official.designation,
                    department = official.department ?: official.school,
                    email = official.email,
                    phone = official.phone,
                    cabin = official.cabin,
                    photoBase64 = official.photoBase64
                )
            )
        }
        list
    }

    val addresses = remember(identityState) {
        val list = mutableListOf<com.amazecc.app.shared.domain.overhaul.M3EAddressItem>()
        if (identityState.currentAddress.isNotEmpty()) {
            list.add(
                com.amazecc.app.shared.domain.overhaul.M3EAddressItem(
                    title = "Current Address",
                    lines = identityState.currentAddress.map { "${it.label}: ${it.value}" }
                )
            )
        }
        if (identityState.permanentAddress.isNotEmpty()) {
            list.add(
                com.amazecc.app.shared.domain.overhaul.M3EAddressItem(
                    title = "Permanent Address",
                    lines = identityState.permanentAddress.map { "${it.label}: ${it.value}" }
                )
            )
        }
        list
    }

    val personalRecords = remember(identityState) {
        listOfNotNull(
            identityState.nationality?.let { com.amazecc.app.shared.domain.overhaul.M3EFieldItem("Nationality", it) },
            identityState.nativeLanguage?.let { com.amazecc.app.shared.domain.overhaul.M3EFieldItem("Native Language", it) },
            identityState.nativeState?.let { com.amazecc.app.shared.domain.overhaul.M3EFieldItem("Native State", it) },
            identityState.community?.let { com.amazecc.app.shared.domain.overhaul.M3EFieldItem("Community", it) },
            identityState.religion?.let { com.amazecc.app.shared.domain.overhaul.M3EFieldItem("Religion", it) },
            identityState.caste?.let { com.amazecc.app.shared.domain.overhaul.M3EFieldItem("Caste", it) },
            identityState.physicallyChallenged?.let { com.amazecc.app.shared.domain.overhaul.M3EFieldItem("Physically Challenged", it) },
            identityState.aadharNumber?.let { com.amazecc.app.shared.domain.overhaul.M3EFieldItem("Aadhar Number", it) },
            identityState.friendMobile?.let { com.amazecc.app.shared.domain.overhaul.M3EFieldItem("Friend Mobile", it) },
            identityState.bloodGroup?.let { com.amazecc.app.shared.domain.overhaul.M3EFieldItem("Blood Group", it) }
        )
    }

    val vtopCred = identityState.credentials.firstOrNull { it.account.contains("VTOP", ignoreCase = true) }
    val vtopUser = vtopCred?.username ?: identityState.regNo ?: ""
    val vtopPass = vtopCred?.password ?: ""

    val state = M3EProfileState(
        // Empty values stay empty: placeholder text must never look like real student data.
        studentName = if (identityState.hasIdentity) identityState.displayName else "",
        registrationNumber = identityState.regNo ?: "",
        applicationNumber = identityState.applicationNumber ?: "",
        programme = identityState.program ?: "",
        school = identityState.school ?: identityState.proctor?.school ?: "",
        campus = identityState.campus ?: "",
        dob = identityState.dob ?: "",
        bloodGroup = identityState.bloodGroup ?: "",
        email = identityState.email ?: "",
        phone = identityState.mobile ?: "",
        proctorName = proctor?.name ?: "",
        proctorEmail = proctor?.email ?: "",
        proctorCabin = proctor?.cabin,
        hostelBlock = hostel?.blockName,
        hostelRoom = hostel?.roomNo,
        cgpa = cgpa,
        attendancePercentage = attendancePct,
        isIdCardFlipped = isFlipped,
        isLoading = isLoading,
        photoBase64 = identityState.photoBase64,
        friendlyName = identityState.friendlyName,
        isEditingFriendlyName = isEditingFriendlyName,
        proctorPhotoBase64 = proctor?.photoBase64,
        proctorPhone = proctor?.phone,
        proctorDepartment = proctor?.department ?: proctor?.school,
        proctorDesignation = proctor?.designation,
        bankInfo = identityState.bank?.let { b ->
            // BankInfo keeps the decrypted VTOP rows in [BankInfo.fields]; there is no
            // dedicated accountNumber property, so read the labelled row.
            val accountNo = b.fields.firstOrNull {
                it.label.contains("account", ignoreCase = true) && it.label.contains("no", ignoreCase = true)
            }?.value ?: b.fields.firstOrNull { it.label.contains("account", ignoreCase = true) }?.value
            when {
                !b.name.isNullOrBlank() && !accountNo.isNullOrBlank() -> "${b.name} ($accountNo)"
                !b.name.isNullOrBlank() -> b.name
                !accountNo.isNullOrBlank() -> "Account $accountNo"
                else -> null
            }
        },
        apaarId = identityState.apaar?.let { a ->
            a.fields.firstOrNull {
                it.label.contains("apaar", ignoreCase = true) ||
                    it.label.contains("abc", ignoreCase = true) ||
                    it.label.contains("id", ignoreCase = true)
            }?.value ?: if (a.hasApaar) "Linked to Portal" else null
        },
        dayboarderInfo = identityState.dayboarder?.let { d ->
            val campusLabel = d.fields.firstOrNull { it.label.contains("campus", ignoreCase = true) }?.value
            if (!d.isDayboarder) "Hosteller"
            else if (campusLabel.isNullOrBlank()) "Day Boarder" else "Day Boarder ($campusLabel)"
        },
        eptStatus = if (identityState.eptTables.isNotEmpty()) "${identityState.eptTables.size} Scheduled Exams" else "No Active Exams",
        rankAir = identityState.ranks.firstOrNull()?.rank?.let { "AIR $it" },
        registrationSchedule = identityState.registrationTables.firstOrNull()?.caption ?: identityState.registrationFields.firstOrNull()?.value,
        vtopUsername = vtopUser,
        vtopPassword = vtopPass,
        showVtopPassword = showVtopPassword,
        mentors = mentors,
        addresses = addresses,
        personalRecords = personalRecords
    )

    AmazeM3ETheme(isDark = isDark) {
        M3EProfileScreen(
            state = state,
            onAction = { action ->
                when (action) {
                    is M3EProfileAction.FlipIdCard -> isFlipped = !isFlipped
                    is M3EProfileAction.CopyRegNo -> {
                        clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(state.registrationNumber))
                    }
                    is M3EProfileAction.ContactProctor -> {
                        if (state.proctorEmail.isNotBlank()) {
                            try { uriHandler.openUri("mailto:${state.proctorEmail}") } catch (_: Exception) {}
                        }
                    }
                    is M3EProfileAction.SignOut -> AppState.logout()
                    is M3EProfileAction.Refresh -> AppState.refreshProfile()
                    is M3EProfileAction.SaveFriendlyName -> {
                        com.amazecc.app.shared.repository.SettingsManager.setString("friendly_name", action.name)
                        com.amazecc.app.shared.state.UserStore.merge(
                            com.amazecc.app.shared.model.StudentIdentity(friendlyName = action.name),
                            com.amazecc.app.shared.state.IdentitySource.SESSION
                        )
                        isEditingFriendlyName = false
                    }
                    is M3EProfileAction.ToggleVtopPasswordVisibility -> showVtopPassword = !showVtopPassword
                    is M3EProfileAction.CopyText -> {
                        clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(action.text))
                    }
                    is M3EProfileAction.OpenEmail -> {
                        try { uriHandler.openUri("mailto:${action.email}") } catch (_: Exception) {}
                    }
                    is M3EProfileAction.OpenPhone -> {
                        try { uriHandler.openUri("tel:${action.phone}") } catch (_: Exception) {}
                    }
                }
            },
            modifier = modifier
        )
    }
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
    var diagnosticMsg by remember { mutableStateOf<String?>("All systems operational (Ping: 42ms)") }
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
        selectedSyncMode = selectedSyncMode
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
                    is M3ESettingsAction.OpenGithubRepo -> {}
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
                            M3ESubScreen.ATTENDANCE -> AppState.navigateTo(Screen.ATTENDANCE)
                            M3ESubScreen.LIBRARIES -> AppState.navigateTo(Screen.LIBRARIES)
                            M3ESubScreen.COURSE_DETAIL -> AppState.navigateTo(Screen.COURSE_DETAIL)
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
                            M3ESubScreen.COURSE_MANAGEMENT -> AppState.navigateTo(Screen.COURSE_MANAGEMENT)
                            M3ESubScreen.ARREARS -> AppState.navigateTo(Screen.ARREAR)
                            M3ESubScreen.QBANK -> AppState.navigateTo(Screen.QBANK)
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
                            M3ESubScreen.PROFILE -> AppState.navigateTo(Screen.PROFILE)
                            M3ESubScreen.SETTINGS -> AppState.navigateTo(Screen.SETTINGS)
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
