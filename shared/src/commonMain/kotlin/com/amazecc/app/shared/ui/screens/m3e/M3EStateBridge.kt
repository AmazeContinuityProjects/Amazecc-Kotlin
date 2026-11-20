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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.amazecc.app.shared.api.AmazeClient
import com.amazecc.app.shared.api.SyllabusResult
import com.amazecc.app.shared.domain.ClassStats
import com.amazecc.app.shared.domain.asCurveStats
import com.amazecc.app.shared.model.batchClassStats
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
    val allGrades by AppState.allGrades.collectAsState()
    val selectedSemId by AppState.selectedSemester.collectAsState()
    val semesterMap by AppState.semesterMap.collectAsState()
    val isLoading by AppState.isLoading.collectAsState()

    var selectedFilter by remember { mutableStateOf(AcademicCourseFilter.ALL) }
    var searchQuery by remember { mutableStateOf("") }
    var expandedCourseCode by remember { mutableStateOf<String?>(null) }

    val currentSem = domain.academics.semesters[selectedSemId]
    val rawCourses = remember(domain, selectedSemId) { com.amazecc.app.shared.domain.Projections.mergedCourses(domain, selectedSemId) }

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
                }
            },
            modifier = modifier,
            onOpenCourse = { code -> AppState.openCourseDetail(code) }
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
    val identityState by UserStore.identity.collectAsState()
    val attendanceTarget = rememberEffectiveAttendanceTarget()
    val scope = rememberCoroutineScope()
    val saveFile = rememberFileSaver()
    var syllabusError by remember { mutableStateOf<String?>(null) }

    var activeTab by remember { mutableStateOf(CourseDetailTab.OVERVIEW) }
    var targetGrade by remember { mutableStateOf("A") }

    val course = remember(domain, selectedSemId, selectedCode) {
        val sem = domain.academics.semesters[selectedSemId]
        sem?.courses?.get(selectedCode ?: "") ?: sem?.courses?.values?.firstOrNull()
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
                maxMarks = m.maxMark.toDoubleOrNull() ?: 50.0,
                scoredMarks = m.score.toDoubleOrNull() ?: 0.0,
                weightagePercent = m.weightagePercent.toDoubleOrNull() ?: 0.0,
                weightageMarks = m.weightage.toDoubleOrNull() ?: 0.0,
                status = m.status
            )
        }.orEmpty()
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
        slot = course?.slots?.firstOrNull() ?: "A1",
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
        internalScored = internalAssessments.sumOf { it.weightageMarks },
        internalMax = 60.0,
        assessments = internalAssessments,
        targetGrade = targetGrade,
        errorMessage = syllabusError
    )

    AmazeM3ETheme(isDark = isDark) {
        M3ECourseDetailScreen(
            state = state,
            onAction = { action ->
                when (action) {
                    is M3ECourseDetailAction.SelectTab -> activeTab = action.tab
                    is M3ECourseDetailAction.SetTargetGrade -> targetGrade = action.grade
                    is M3ECourseDetailAction.DownloadSyllabus -> scope.launch {
                        syllabusError = null
                        val result = AmazeClient.getSyllabusPdf(syllabusCourseCode)
                        val file = result.download
                        syllabusError = when {
                            file == null -> result.error ?: "No syllabus is available for this course"
                            file.bytes.isEmpty() -> "The syllabus file is empty"
                            !saveFile(
                                "${syllabusCourseCode}_syllabus.${file.extension.ifBlank { "pdf" }}",
                                file.bytes
                            ) -> "Could not save the syllabus file"
                            else -> null
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
                else M3EPredictorDerivation.requiredFatPercentage(scored, max, grade, input.classStats)
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
    // full Profile host (residence, bus, contacts, acknowledgement docs, identity warm-up).
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
        homePillStyle = AppState.homePillStyle.value
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
                            M3ESubScreen.LIBRARIES -> AppState.navigateTo(Screen.LIBRARIES)
                            M3ESubScreen.COURSE_DETAIL -> AppState.navigateTo(Screen.COURSE_DETAIL)
                            M3ESubScreen.COURSE_ATTENDANCE -> AppState.navigateTo(Screen.COURSE_ATTENDANCE)
                            M3ESubScreen.COURSE_DASHBOARD -> AppState.navigateTo(Screen.COURSE_DASHBOARD)
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
