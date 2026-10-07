package com.amazecc.app.shared.ui.screens.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Coffee
import androidx.compose.material.icons.automirrored.rounded.FormatListBulleted
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.MeetingRoom
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.amazecc.app.shared.domain.Projections
import com.amazecc.app.shared.model.ExamItem
import com.amazecc.app.shared.repository.SessionManager
import com.amazecc.app.shared.repository.SettingsManager
import com.amazecc.app.shared.state.AcademicDerivers
import com.amazecc.app.shared.state.AppState
import com.amazecc.app.shared.state.HomePillStyle
import com.amazecc.app.shared.state.Screen
import com.amazecc.app.shared.state.SemesterData
import com.amazecc.app.shared.state.SyncEngine
import com.amazecc.app.shared.state.UserStore
import com.amazecc.app.shared.theme.AmazeColors
import com.amazecc.app.shared.theme.AmazeTheme
import com.amazecc.app.shared.ui.components.BOTTOM_NAV_PADDING
import com.amazecc.app.shared.ui.components.UpdateDialog
import com.amazecc.app.shared.ui.design.*
import com.amazecc.app.shared.utils.seatLocationDisplay
import com.amazecc.app.shared.utils.sessionDisplay
import com.amazecc.app.shared.utils.toFixed
import com.amazecc.app.shared.utils.toImageBitmap
import io.ktor.util.decodeBase64Bytes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock

/**
 * The home screen.
 *
 * A port of the web app's `SimplifiedMobileHome.tsx` — the screen the web build
 * calls its default home, and the one the old widget dashboard was replaced by.
 * The layout, the tokens and the four day-viewport states come from that file;
 * the data does not, because this app stores academics as a typed
 * [com.amazecc.app.shared.state.SemesterData] tree rather than as the
 * loosely-typed blobs the web reads.
 *
 * ## The shape of it
 *
 * 1. A greeting bar with the avatar, sync and search.
 * 2. Two tiles: pinned overall attendance, and a rotating insight card.
 * 3. The week strip, and below it whatever today (or the selected day) is:
 *    an exam, a holiday, a free day, or the sessions themselves.
 * 4. Today's tasks, when the user has chosen to see them here.
 * 5. Two actions: the full timetable, and free classrooms.
 *
 * ## What is different from the web, and why
 *
 * * **No offline badge.** The web's sync button turns amber when the browser
 *   reports no connection. This app has no connection probe — it has a sync
 *   engine with per-module progress, surfaced by the sync popup — so an
 *   "Offline" state here would be a guess. The button shows syncing instead,
 *   which is a fact.
 * * **No per-class task badges.** The web links a task to a session by matching
 *   its due date against the session's time window. Nothing in this app's task
 *   model carries that association, and inferring one from a date would put
 *   badges on sessions they do not belong to. The Tasks section below carries
 *   the same information without the guess.
 *
 * Moodle deadlines *are* read, through the app's existing deadline parser rather
 * than a second one — see [nextMoodleDeadline].
 */
@Composable
fun SimplifiedHomeScreen() {
    val colors = AmazeTheme.colors
    val palette = rememberHomePalette(colors)
    val type = rememberHomeType()
    val tones = rememberHomeTones(colors)
    val homeShape = rememberHomeShape()

    // ── State ──
    val academic by AppState.academic.collectAsState()
    val domain by AppState.domain.collectAsState()
    val selectedSemester by AppState.selectedSemester.collectAsState()
    val calendar by AppState.calendar.collectAsState()
    val calendarsList by AppState.calendarsList.collectAsState()
    val tasks by AppState.tasks.collectAsState()
    val identity by UserStore.identity.collectAsState()
    val authorizedID by SessionManager.authorizedID.collectAsState()
    val isLoading by AppState.isLoading.collectAsState()
    val isBusSubscriber by AppState.isBusSubscriber.collectAsState()
    val customTarget by AppState.customAttendanceTarget.collectAsState()
    val cgpaHidden by AppState.cgpaHidden.collectAsState()
    val homePillStyle by AppState.homePillStyle.collectAsState()
    val homeTasksInline by AppState.homeTasksInline.collectAsState()
    val updateStatus by AppState.updateStatus.collectAsState()

    var weekOffset by remember { mutableIntStateOf(0) }
    var weekSlideDirection by remember { mutableIntStateOf(1) }
    var today by remember { mutableStateOf(homeToday()) }
    var nowMinutes by remember { mutableIntStateOf(homeNowMinutes()) }
    var selectedDay by remember { mutableStateOf(homeTodayAttendanceDay(homeToday())) }
    var showTimetableSheet by remember { mutableStateOf(false) }

    // 30-second ticker. The live progress bar and the "in 12 mins" labels are
    // minute-resolution, and a per-second ticker would recompose the whole page
    // for no visible gain. It also re-reads the date, so the "today" ring and
    // the live clock stay right across a midnight rollover.
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000L)
            val realToday = homeToday()
            if (realToday != today) today = realToday
            nowMinutes = homeNowMinutes()
        }
    }

    LaunchedEffect(Unit) { AppState.checkForUpdate() }

    // ── Derived data ──
    val sem = remember(academic, selectedSemester) {
        val fromAppState = academic.semesters[selectedSemester]
        fromAppState?.takeIf { it.courses.isNotEmpty() } ?: AcademicDerivers.resolveCurrentSemester(academic)
    }
    // The semester attendance is read for is the one resolved above, which is not always
    // [selectedSemester]: [AcademicDerivers.resolveCurrentSemester] is the fallback when the
    // selection names a semester whose course list is empty.
    val courses = remember(domain, sem) {
        sem?.let { Projections.semesterAttendance(domain, it.semesterId) }.orEmpty()
    }
    val timetable = remember(sem) { buildHomeTimetable(sem) }
    val classCounts = remember(timetable) { timetable.mapValues { it.value.size } }

    val targetPct = customTarget ?: if (isBusSubscriber) 85f else 75f
    val attendance = remember(courses, targetPct) {
        summariseHomeAttendance(courses.map { it.attendedClasses to it.totalClasses }, targetPct)
    }

    val calendarMonths = remember(calendarsList, calendar) {
        resolveHomeCalendarMonths(calendarsList, calendar, SettingsManager.getPreferredCalendar())
    }
    val deadlines = remember(tasks) { deadlineDates(tasks) }
    val exams = remember(sem) { sem?.exams.orEmpty() }

    val weekDays = remember(weekOffset, today, exams, calendarMonths, deadlines) {
        buildHomeWeekDays(weekOffset, today, exams, calendarMonths, deadlines)
    }

    val selectedDayMeta = weekDays.firstOrNull { it.dayCode == selectedDay }
    // The academic calendar can move a day's classes onto another column, and
    // when it does the strip's tint and the list below have to agree on which.
    val effectiveDay = selectedDayMeta?.detectedDayOrder ?: selectedDay
    val selectedDayClasses = timetable[effectiveDay].orEmpty()
    val isTodayView = selectedDayMeta?.date == today
    val todayDayCode = homeTodayAttendanceDay(today)

    val isExamDay = !selectedDayMeta?.exams.isNullOrEmpty()
    val isHolidayOrOff = selectedDayMeta?.holidayInfo != null && !isExamDay

    val goToWeek: (Int, Int) -> Unit = { next, direction ->
        weekSlideDirection = direction
        weekOffset = next
    }

    // ── Insight slides ──
    val criticalCount = remember(courses, targetPct) {
        courses.count { it.totalClasses > 0 && it.attendancePercentage.toFloatOrNull()?.let { p -> p < targetPct } == true }
    }
    val odHours = remember(sem) { sem?.let { AcademicDerivers.computeODHours(it) } ?: 0 }
    val nextExam = remember(exams) { nextUpcomingExam(exams, Clock.System.now()) }
    val moodle by AppState.moodleData.collectAsState()
    val nextDeadline = remember(moodle, today) { nextMoodleDeadline(moodle?.data.orEmpty(), today) }

    val slides: List<HomeInsightSlide> = remember(cgpaHidden, sem, criticalCount, odHours, targetPct, nextExam, nextDeadline) {
        buildList<HomeInsightSlide> {
            if (!cgpaHidden) {
                add(
                    HomeInsightSlide(
                        id = "cgpa",
                        label = "CGPA",
                        value = sem?.gpa?.toDoubleOrNull()?.toFixed(2) ?: "—",
                        sub = "VTOP verified grade",
                        badge = "Academic",
                        tone = HomeTone.NEUTRAL,
                        onClick = { AppState.openAttendanceView("Predictor") }
                    )
                )
            }
            add(
                HomeInsightSlide(
                    id = "credits",
                    label = "Credits",
                    value = creditsOf(sem),
                    sub = "Degree curriculum earned",
                    badge = "Degree",
                    tone = HomeTone.NEUTRAL,
                    onClick = { AppState.navigateTo(Screen.CURRICULUM) }
                )
            )
            if (criticalCount > 0) {
                add(
                    HomeInsightSlide(
                        id = "alerts",
                        label = "Shortage Alert",
                        value = "$criticalCount low",
                        sub = "Courses below ${targetPct.toInt()}%",
                        badge = "Warning",
                        tone = HomeTone.DANGER,
                        onClick = { AppState.openAttendanceView("Timetable") }
                    )
                )
            } else {
                add(
                    HomeInsightSlide(
                        id = "od",
                        label = "On-Duty",
                        value = "$odHours hrs",
                        sub = "Approved on-duty total",
                        badge = "OD",
                        tone = HomeTone.SUCCESS,
                        onClick = { AppState.navigateTo(Screen.OD_TRACKER) }
                    )
                )
            }
            if (nextExam != null) {
                add(
                    HomeInsightSlide(
                        id = "exam",
                        label = "Next Exam",
                        value = nextExam.courseCode,
                        sub = "${nextExam.examDate} • ${nextExam.examTime.substringBefore('-').trim()}",
                        badge = "Exam",
                        tone = HomeTone.SUCCESS,
                        onClick = { AppState.navigateTo(Screen.EXAM_SCHEDULE) }
                    )
                )
            }
            if (nextDeadline != null) {
                val daysLeft = (nextDeadline.date.toEpochDays() - today.toEpochDays()).coerceAtLeast(0)
                val dueLabel = when (daysLeft) {
                    0 -> "Due today"
                    1 -> "Due tomorrow"
                    else -> "Due in $daysLeft days"
                }
                add(
                    HomeInsightSlide(
                        id = "deadline",
                        label = "Assignment Due",
                        value = nextDeadline.title.take(14),
                        sub = dueLabel,
                        badge = nextDeadline.courseCode.ifBlank { "Moodle" },
                        tone = HomeTone.WARNING,
                        onClick = { AppState.navigateTo(Screen.MOODLE) }
                    )
                )
            }
        }
    }

    val carousel = rememberHomeCarousel(slides.size)
    HomeCarouselAutoplay(carousel, slides.size)

    // Derived out here rather than inside the list, because `LazyListScope` is
    // not a composable scope and a `remember` cannot be declared in it.
    val taskRows = remember(homeTasksInline, tasks, selectedDayMeta?.date, today) {
        if (homeTasksInline) emptyList() else homeTasksForDay(tasks, selectedDayMeta?.date ?: today)
    }

    if (updateStatus is AppState.UpdateStatus.Available) {
        val status = updateStatus as AppState.UpdateStatus.Available
        UpdateDialog(
            release = status.release,
            currentVersion = status.currentVersion,
            onDismiss = { AppState.dismissUpdateDialog() },
            onDownload = { AppState.dismissUpdateDialog() }
        )
    }

    if (showTimetableSheet) {
        HomeTimetableOverlay(
            rows = remember(sem) { buildHomeTimetableMatrix(sem) },
            onDismiss = { showTimetableSheet = false }
        )
    }

    // ── Page ──
    val nameToDisplay = remember(identity.displayName, authorizedID) {
        val first = identity.displayName.split(" ").firstOrNull()?.takeIf { it.isNotBlank() }
            ?: identity.displayName
        if (first == authorizedID) "Student" else first
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.background)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = AmazeTheme.spacing.pageHorizontal,
                end = AmazeTheme.spacing.pageHorizontal,
                top = 12.dp,
                bottom = BOTTOM_NAV_PADDING + 16.dp
            ),
            verticalArrangement = Arrangement.spacedBy(AmazeTheme.spacing.sectionGap)
        ) {
            item(key = "top_bar") {
                HomeTopBar(
                    name = nameToDisplay,
                    initials = identity.initials,
                    photoBase64 = identity.photoBase64,
                    nowMinutes = nowMinutes,
                    isSpinning = isLoading,
                    onSync = {
                        SyncEngine.setShowSyncDialog(true, minimized = true)
                        AppState.loadAllData()
                    },
                    onSearch = { AppState.openCommandPalette() }
                )
            }

            item(key = "stats") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(HomeSpace.row)
                ) {
                    HomeAttendanceTile(
                        summary = attendance,
                        modifier = Modifier.weight(1f),
                        onClick = { AppState.openAttendanceView("Predictor") }
                    )
                    HomeInsightCarousel(
                        slides = slides,
                        state = carousel,
                        modifier = Modifier.weight(1f),
                        onSlideClick = { it.onClick() }
                    )
                }
            }

            item(key = "timetable") {
                Column(verticalArrangement = Arrangement.spacedBy(HomeSpace.icon)) {
                    HomeWeekHeaderRow(
                        weekLabel = homeWeekHeader(weekDays, today),
                        isTodaySelected = weekOffset == 0 && selectedDay == todayDayCode,
                        pillStyle = homePillStyle,
                        showPillToggle = !isExamDay && !isHolidayOrOff,
                        onPrevWeek = { goToWeek(weekOffset - 1, -1) },
                        onNextWeek = { goToWeek(weekOffset + 1, 1) },
                        onBackToToday = {
                            if (weekOffset != 0) goToWeek(0, if (weekOffset < 0) 1 else -1)
                            selectedDay = todayDayCode
                        },
                        onPillStyleChange = AppState::setHomePillStyle
                    )

                    HomeWeekStrip(
                        days = weekDays,
                        selectedDay = selectedDay,
                        classCounts = classCounts,
                        onSelectDay = { selectedDay = it },
                        onSwipeNext = { goToWeek(weekOffset + 1, 1) },
                        onSwipePrev = { goToWeek(weekOffset - 1, -1) }
                    )

                    HomeSelectedDayHeader(
                        label = selectedDayMeta?.longLabel().orEmpty(),
                        statusLine = when {
                            isExamDay -> {
                                val count = selectedDayMeta?.exams?.size ?: 0
                                "$count ${if (count == 1) "exam" else "exams"} scheduled"
                            }
                            isHolidayOrOff -> "Academic holiday / non-instructional"
                            else -> {
                                val count = selectedDayClasses.size
                                "$count ${if (count == 1) "session" else "sessions"} scheduled"
                            }
                        },
                        onOpenCalendar = { AppState.navigateTo(Screen.CALENDAR) }
                    )

                    if (selectedDayMeta?.orderInfo != null && !isExamDay && !isHolidayOrOff) {
                        HomeDayOrderBanner(
                            text = selectedDayMeta.orderInfo.orEmpty(),
                            onOpenCalendar = { AppState.navigateTo(Screen.CALENDAR) }
                        )
                    }

                    when {
                        isExamDay -> HomeExamDayList(
                            exams = selectedDayMeta?.exams.orEmpty(),
                            onOpenHallDetails = { AppState.navigateTo(Screen.EXAM_SCHEDULE) }
                        )
                        isHolidayOrOff -> HomeEmptyPanel(
                            icon = Icons.Rounded.WbSunny,
                            title = "No classes today",
                            description = "${selectedDayMeta?.holidayInfo.orEmpty()} • Non-instructional day as per the official academic calendar.",
                            tint = colors.warningText,
                            action = {
                                HomeGhostButton(
                                    text = "View Academic Calendar",
                                    icon = Icons.Rounded.CalendarMonth,
                                    onClick = { AppState.navigateTo(Screen.CALENDAR) }
                                )
                            }
                        )
                        selectedDayClasses.isEmpty() -> HomeEmptyPanel(
                            icon = Icons.Rounded.Coffee,
                            title = "No classes on ${selectedDay.name}",
                            description = "No lectures or lab sessions are scheduled for this day. Enjoy your free time or check free classrooms.",
                            tint = colors.accent,
                            action = {
                                HomeGhostButton(
                                    text = "Free Classrooms",
                                    icon = Icons.Rounded.MeetingRoom,
                                    tint = colors.successText,
                                    onClick = { AppState.navigateTo(Screen.FREE_CLASSROOMS) }
                                )
                            }
                        )
                        else -> Column(verticalArrangement = Arrangement.spacedBy(HomeSpace.row)) {
                            selectedDayClasses.forEach { card ->
                                HomeClassCard(
                                    card = card,
                                    style = homePillStyle,
                                    progress = homeClassProgress(card, isTodayView, nowMinutes),
                                    bunk = homeBunk(card, targetPct),
                                    targetPct = targetPct,
                                    onClick = { AppState.openCourseDetail(card.courseCode) }
                                )
                            }
                        }
                    }
                }
            }

            if (!homeTasksInline) {
                if (taskRows.isNotEmpty()) {
                    item(key = "tasks") {
                        HomeTasksSection(
                            tasks = taskRows,
                            onOpenAll = { AppState.navigateTo(Screen.TASKS) },
                            onOpenTask = { AppState.navigateTo(Screen.TASKS) }
                        )
                    }
                }
            }

            item(key = "quick_tools") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(HomeSpace.between)
                ) {
                    HomeToolButton(
                        text = "Full Timetable",
                        icon = Icons.Rounded.CalendarMonth,
                        tone = HomeTone.NEUTRAL,
                        iconTint = colors.accent,
                        modifier = Modifier.weight(1f),
                        onClick = { showTimetableSheet = true }
                    )
                    HomeToolButton(
                        text = "Free Classrooms",
                        icon = Icons.Rounded.MeetingRoom,
                        tone = HomeTone.SUCCESS,
                        modifier = Modifier.weight(1f),
                        onClick = { AppState.navigateTo(Screen.FREE_CLASSROOMS) }
                    )
                }
            }
        }
    }
}

// ── Top bar ──

@Composable
private fun HomeTopBar(
    name: String,
    initials: String,
    photoBase64: String?,
    nowMinutes: Int,
    isSpinning: Boolean,
    onSync: () -> Unit,
    onSearch: () -> Unit
) {
    val colors = AmazeTheme.colors
    val palette = rememberHomePalette(colors)
    val type = rememberHomeType()
    val homeShape = rememberHomeShape()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = HomeSpace.xs),
        verticalAlignment = Alignment.Top
    ) {
        Column(modifier = Modifier.weight(1f)) {
            // The avatar, or the app mark when there is no photo or the user has
            // not synced one. 40dp, 16dp radius — the same `rounded-2xl` the web
            // uses, and the app's own [homeShape.control] step.
            Box(
                modifier = Modifier
                    .size(HomeSpace.avatar)
                    .clip(RoundedCornerShape(homeShape.control))
                    .background(palette.surfaceRaised)
                    .border(1.dp, palette.borderMuted, RoundedCornerShape(homeShape.control)),
                contentAlignment = Alignment.Center
            ) {
                if (photoBase64.isNullOrBlank()) {
                    Text(
                        text = initials,
                        style = type.rowTitle,
                        color = colors.accent
                    )
                } else {
                    HomeAvatarImage(
                        photoBase64 = photoBase64,
                        initials = initials,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(homeShape.control))
                    )
                }
            }

            Spacer(Modifier.height(HomeSpace.row))

            Text(
                text = "${homeGreeting(nowMinutes)},",
                style = type.eyebrow,
                color = colors.textMuted,
                maxLines = 1
            )
            Spacer(Modifier.height(HomeSpace.xs))
            Text(
                text = name,
                style = type.pageTitle,
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(Modifier.width(HomeSpace.sm))

        Row(
            horizontalArrangement = Arrangement.spacedBy(HomeSpace.sm),
            modifier = Modifier.padding(top = 2.dp)
        ) {
            HomeIconButton(
                icon = Icons.Rounded.Refresh,
                contentDescription = "Sync data from VTOP",
                onClick = onSync,
                enabled = !isSpinning
            )
            HomeIconButton(
                icon = Icons.Rounded.Search,
                contentDescription = "Search",
                onClick = onSearch
            )
        }
    }
}

/** "Good morning" / afternoon / evening, from minutes since midnight. */
internal fun homeGreeting(nowMinutes: Int): String {
    val hour = nowMinutes / 60
    return when {
        hour < 12 -> "Good morning"
        hour < 17 -> "Good afternoon"
        else -> "Good evening"
    }
}

/**
 * Credits earned, or in progress when the current semester is not yet graded.
 *
 * Mirrors the old metric card exactly, because it is the same figure: a student
 * who has read "24" on the classic dashboard must read "24" here.
 */
internal fun creditsOf(sem: SemesterData?): String {
    val courses = sem?.courses?.values.orEmpty()
    if (courses.isEmpty()) return "—"
    val ongoing = courses.filter { it.grade == null }
        .sumOf { it.credits?.trim()?.toDoubleOrNull()?.toInt() ?: 0 }
    val earned = courses.filter { it.grade != null }
        .sumOf { it.credits?.trim()?.toDoubleOrNull()?.toInt() ?: 0 }
    val value = if (ongoing > 0) ongoing else earned
    return if (value > 0) "$value cr" else "—"
}

/**
 * Decodes the synced profile photo off the main thread.
 *
 * Falls back to [initials] for as long as the decode is in flight, and forever
 * if it fails, so the top bar never flashes an empty box.
 */
@Composable
private fun HomeAvatarImage(
    photoBase64: String,
    initials: String,
    modifier: Modifier = Modifier
) {
    val colors = AmazeTheme.colors
    val type = rememberHomeType()
    var bitmap by remember(photoBase64) { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }

    LaunchedEffect(photoBase64) {
        bitmap = withContext(Dispatchers.Default) {
            try {
                photoBase64.substringAfter("base64,")
                    .replace("\n", "").replace("\r", "").replace(" ", "")
                    .decodeBase64Bytes()
                    .toImageBitmap()
            } catch (_: Exception) {
                null
            }
        }
    }

    val decoded = bitmap
    if (decoded == null) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text(text = initials, style = type.rowTitle, color = colors.accent)
        }
    } else {
        androidx.compose.foundation.Image(
            bitmap = decoded,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier
        )
    }
}

// ── Attendance tile ──

/**
 * The pinned attendance tile.
 *
 * A tile is a measurement that does not change under the reader, which is why
 * this is a fixed card beside the carousel rather than one of its slides. The
 * status pill carries the same three bands as
 * [com.amazecc.app.shared.ui.components.BunkOMeterCard], so the tile and the
 * bunk meter never describe the same course differently.
 */
@Composable
private fun HomeAttendanceTile(
    summary: HomeAttendanceSummary,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = AmazeTheme.colors
    val tones = rememberHomeTones(colors)
    val type = rememberHomeType()
    val tone = tones[summary.tone] ?: tones.getValue(HomeTone.NEUTRAL)

    HomeTileSurface(
        modifier = modifier.height(HomeSpace.tileShort),
        onClick = onClick,
        contentPadding = PaddingValues(HomeSpace.icon)
    ) {
        HomeTileBody(spacing = 0.dp) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "ATTENDANCE",
                    style = type.tileLabel,
                    color = colors.textMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Spacer(Modifier.width(HomeSpace.xs))
                HomeChip(text = summary.label, tone = summary.tone)
            }

            Spacer(Modifier.weight(1f))

            Text(
                text = if (summary.percentage > 0f) "${summary.percentage.toInt()}%" else "—",
                style = type.hero,
                color = tone.ink,
                maxLines = 1
            )

            Spacer(Modifier.weight(1f))

            Text(
                text = "${summary.attended} of ${summary.total} attended",
                style = type.subline,
                color = colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// ── Week header ──

/**
 * The month range, the week chevrons, the "Today" chip, and the pill-style
 * toggle.
 *
 * The "Today" chip is gated on "you are not on today" rather than on the week
 * having been paged. Picking a different day on the current week is by far the
 * more common way to wander off today, and gating on the week alone hid the only
 * control that undid it — leaving no way home at all. It resets the week *and*
 * the day together, because a bare day reset on a browsed week would select a
 * weekday the visible week does not contain.
 */
@Composable
private fun HomeWeekHeaderRow(
    weekLabel: String,
    isTodaySelected: Boolean,
    pillStyle: HomePillStyle,
    showPillToggle: Boolean,
    onPrevWeek: () -> Unit,
    onNextWeek: () -> Unit,
    onBackToToday: () -> Unit,
    onPillStyleChange: (HomePillStyle) -> Unit
) {
    val colors = AmazeTheme.colors
    val palette = rememberHomePalette(colors)
    val type = rememberHomeType()
    val homeShape = rememberHomeShape()
    val tones = rememberHomeTones(colors)
    val accentTone = tones.getValue(HomeTone.ACCENT)
    val chevronShape = RoundedCornerShape(homeShape.chip)

    Column(verticalArrangement = Arrangement.spacedBy(HomeSpace.sm)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Rounded.CalendarMonth,
                contentDescription = null,
                tint = colors.accent,
                modifier = Modifier.size(HomeSpace.icon)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = weekLabel,
                style = type.sectionTitle,
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )

            Spacer(Modifier.width(HomeSpace.sm))

            // The strip is swipeable, but a chevron is the affordance that needs
            // no gesture, so these stay.
            HomeChevronButton(Icons.Rounded.ChevronLeft, "Previous week", onPrevWeek, chevronShape)
            Spacer(Modifier.width(HomeSpace.xs))
            HomeChevronButton(Icons.Rounded.ChevronRight, "Next week", onNextWeek, chevronShape)

            if (!isTodaySelected) {
                Spacer(Modifier.width(HomeSpace.xs))
                Box(
                    modifier = Modifier
                        .homePressable(
                            shape = RoundedCornerShape(homeShape.chip),
                            onClick = onBackToToday,
                            pressedScale = HomePress.ICON_PRESSED
                        )
                        .clip(RoundedCornerShape(homeShape.chip))
                        .background(accentTone.wash)
                        .border(1.dp, accentTone.border, RoundedCornerShape(homeShape.chip))
                        .padding(horizontal = HomeSpace.sm, vertical = 2.dp)
                ) {
                    Text(
                        text = "Today",
                        style = type.micro,
                        color = accentTone.ink,
                        maxLines = 1
                    )
                }
            }
        }

        if (showPillToggle) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                HomePillStyleToggle(
                    selected = pillStyle,
                    onSelect = onPillStyleChange
                )
            }
        }
    }
}

@Composable
private fun HomeChevronButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    onClick: () -> Unit,
    shape: androidx.compose.ui.graphics.Shape
) {
    val colors = AmazeTheme.colors
    val palette = rememberHomePalette(colors)

    Box(
        modifier = Modifier
            .homePressable(
                shape = shape,
                onClick = onClick,
                pressedScale = HomePress.ICON_PRESSED
            )
            .clip(shape)
            .background(palette.surfaceRaised)
            .border(1.dp, palette.borderMuted, shape)
            .padding(HomeSpace.xs),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = colors.textSecondary,
            modifier = Modifier.size(HomeSpace.iconLg)
        )
    }
}

/** Compact 2-line or spacious detailed, as a two-segment control. */
@Composable
private fun HomePillStyleToggle(
    selected: HomePillStyle,
    onSelect: (HomePillStyle) -> Unit
) {
    val colors = AmazeTheme.colors
    val palette = rememberHomePalette(colors)
    val type = rememberHomeType()
    val homeShape = rememberHomeShape()
    val shape = RoundedCornerShape(homeShape.chip)

    Row(
        modifier = Modifier
            .clip(shape)
            .background(palette.surfaceRaised)
            .border(1.dp, palette.borderMuted, shape)
            .padding(2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        HomePillStyle.entries.forEach { option ->
            val active = option == selected
            val segmentShape = RoundedCornerShape(homeShape.chip)
            Box(
                modifier = Modifier
                    .homePressable(
                        shape = segmentShape,
                        onClick = { onSelect(option) },
                        pressedScale = HomePress.ICON_PRESSED
                    )
                    .clip(segmentShape)
                    .then(if (active) Modifier.background(palette.surfaceWash) else Modifier)
                    .padding(horizontal = HomeSpace.sm, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (option == HomePillStyle.COMPACT) {
                            Icons.AutoMirrored.Rounded.FormatListBulleted
                        } else {
                            Icons.Rounded.Layers
                        },
                        contentDescription = null,
                        tint = if (active) colors.textPrimary else colors.textMuted,
                        modifier = Modifier.size(HomeSpace.iconLg)
                    )
                    Spacer(Modifier.width(HomeSpace.xs))
                    Text(
                        text = if (option == HomePillStyle.COMPACT) "Compact" else "Detailed",
                        style = type.micro,
                        color = if (active) colors.textPrimary else colors.textMuted,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

// ── Selected day header ──

@Composable
private fun HomeSelectedDayHeader(
    label: String,
    statusLine: String,
    onOpenCalendar: () -> Unit
) {
    val colors = AmazeTheme.colors
    val type = rememberHomeType()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = HomeSpace.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = type.dayTitle,
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = statusLine,
                style = type.subline,
                color = colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(HomeSpace.sm))
        HomeGhostButton(
            text = "Calendar",
            icon = Icons.Rounded.CalendarMonth,
            onClick = onOpenCalendar
        )
    }
}

/** The "the calendar reordered this day" banner. */
@Composable
private fun HomeDayOrderBanner(
    text: String,
    onOpenCalendar: () -> Unit
) {
    val colors = AmazeTheme.colors
    val tones = rememberHomeTones(colors)
    val type = rememberHomeType()
    val tone = tones.getValue(HomeTone.ACCENT)
    val shape = RoundedCornerShape(AmazeTheme.radius.medium)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(tone.wash)
            .border(1.dp, tone.border, shape)
            .padding(HomeSpace.row),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Rounded.Star,
            contentDescription = null,
            tint = colors.accent,
            modifier = Modifier.size(HomeSpace.icon)
        )
        Spacer(Modifier.width(HomeSpace.sm))
        Text(
            text = "$text (applied)",
            style = type.rowTitleSmall,
            color = tone.ink,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(HomeSpace.sm))
        HomeChip(text = "Calendar auto", tone = HomeTone.ACCENT)
    }
}

// ── Exam day ──

/**
 * The exam-day viewport.
 *
 * A red wash rather than the plain tile, because on this day the exam is the
 * whole story: there are no regular lectures to reconcile it against, and the
 * sub-header above already says the day has no classes.
 */
@Composable
private fun HomeExamDayList(
    exams: List<ExamItem>,
    onOpenHallDetails: () -> Unit
) {
    val colors = AmazeTheme.colors
    val type = rememberHomeType()
    val tones = rememberHomeTones(colors)
    val danger = tones.getValue(HomeTone.DANGER)
    val homeShape = rememberHomeShape()
    val shape = RoundedCornerShape(homeShape.emptyState)

    Column(verticalArrangement = Arrangement.spacedBy(HomeSpace.xs + HomeSpace.xs)) {
        exams.forEach { exam ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(shape)
                    .background(
                        Brush.linearGradient(
                            listOf(
                                colors.danger.copy(alpha = HomeAlpha.WASH),
                                paletteWash(colors),
                                colors.warning.copy(alpha = HomeAlpha.WASH)
                            )
                        )
                    )
                    .border(1.dp, danger.border.copy(alpha = HomeAlpha.RING), shape)
                    .padding(HomeSpace.card),
                verticalArrangement = Arrangement.spacedBy(HomeSpace.row)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    HomeSolidChip(
                        text = "Exam",
                        tint = colors.danger
                    )
                    Spacer(Modifier.width(HomeSpace.sm))
                    Text(
                        text = exam.sessionDisplay,
                        style = type.rowTitleSmall,
                        color = danger.ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "Slot ${exam.slot.ifBlank { "—" }}",
                        style = type.meta,
                        color = colors.textMuted,
                        maxLines = 1
                    )
                }

                Column {
                    Text(
                        text = exam.courseTitle.ifBlank { "Course examination" },
                        style = type.examTitle,
                        color = colors.textPrimary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(HomeSpace.xs))
                    Text(
                        text = exam.courseCode,
                        style = type.rowTitleSmall,
                        color = colors.textSecondary,
                        maxLines = 1
                    )
                }

                androidx.compose.material3.HorizontalDivider(color = danger.border.copy(alpha = HomeAlpha.HAIRLINE))

                Column(verticalArrangement = Arrangement.spacedBy(HomeSpace.sm)) {
                    HomeExamFact(
                        icon = Icons.Rounded.Schedule,
                        text = exam.examTime.ifBlank { "Morning session" },
                        sub = exam.reportingTime.takeIf { it.isNotBlank() }?.let { "Reporting: $it" }
                    )
                    HomeExamFact(
                        icon = Icons.Rounded.LocationOn,
                        text = "Venue: ${exam.venue.ifBlank { "Hall assigned" }}",
                        sub = exam.seatNo.takeIf { it.isNotBlank() }
                            ?.let { "Seat: No. $it (${exam.seatLocationDisplay})" }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "No regular lectures on exam day",
                        style = type.meta,
                        color = danger.ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(HomeSpace.sm))
                    HomeGhostButton(
                        text = "Hall details",
                        onClick = onOpenHallDetails,
                        tint = colors.dangerText
                    )
                }
            }
        }
    }
}

private fun paletteWash(colors: com.amazecc.app.shared.theme.AmazeColors): Color =
    colors.surface.copy(alpha = if (colors.isDark) HomeAlpha.TILE_DARK else HomeAlpha.TILE_LIGHT)

@Composable
private fun HomeExamFact(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    sub: String?
) {
    val colors = AmazeTheme.colors
    val type = rememberHomeType()

    Row(verticalAlignment = Alignment.Top) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = colors.danger,
            modifier = Modifier.size(HomeSpace.icon)
        )
        Spacer(Modifier.width(HomeSpace.sm))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = text,
                style = type.rowTitleSmall,
                color = colors.textPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (sub != null) {
                Text(
                    text = sub,
                    style = type.meta,
                    color = colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

// ── Class card ──

/**
 * One session, in either the two-line compact form or the spacious detailed
 * one.
 *
 * The two are genuinely different information densities, not two styles of the
 * same card: compact is for scanning "when and where am I", detailed adds the
 * faculty and the bunkable subtext. A live session gets a progress wash behind
 * its content and a rule beneath it; a finished one is dimmed rather than hidden,
 * because "what did I have today" is a real question at 6pm.
 */
@Composable
private fun HomeClassCard(
    card: HomeClassCard,
    style: HomePillStyle,
    progress: HomeClassProgress,
    bunk: HomeBunk,
    targetPct: Float,
    onClick: () -> Unit
) {
    val colors = AmazeTheme.colors
    val palette = rememberHomePalette(colors)
    val tones = rememberHomeTones(colors)
    val type = rememberHomeType()
    val homeShape = rememberHomeShape()
    val bunkTone = tones[bunk.tone] ?: tones.getValue(HomeTone.NEUTRAL)

    val isLive = progress.state == HomeClassState.LIVE
    val isCompleted = progress.state == HomeClassState.COMPLETED
    val isUpcoming = progress.state == HomeClassState.UPCOMING
    val detailed = style == HomePillStyle.DETAILED

    val cardShape = RoundedCornerShape(if (detailed) homeShape.tile else homeShape.card)

    val fill = when {
        isLive -> tones.getValue(HomeTone.ACCENT).wash
        isCompleted -> colors.surface.copy(alpha = if (colors.isDark) 0.4f else 0.7f)
        else -> paletteWash(colors)
    }
    val edge = when {
        isLive -> colors.accent
        isCompleted -> palette.borderMuted
        else -> palette.borderMuted
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(cardShape)
            .then(
                Modifier.homePressable(
                    shape = cardShape,
                    onClick = onClick,
                    pressedScale = HomePress.TILE_PRESSED
                )
            )
            .graphicsLayer { alpha = if (isCompleted) 0.75f else 1f }
            .background(fill)
            .border(1.dp, edge, cardShape)
    ) {
        if (detailed) {
            Column(
                modifier = Modifier.padding(HomeSpace.card),
                verticalArrangement = Arrangement.spacedBy(HomeSpace.row)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    HomeChip(text = card.slotName, tone = HomeTone.ACCENT)
                    Spacer(Modifier.width(HomeSpace.sm))
                    if (isLive) {
                        HomeSolidChip(text = "Live ${progress.minutesLeft}m", tint = colors.success)
                    } else if (isUpcoming) {
                        HomeChip(
                            text = if (progress.minutesUntilStart <= 60) "In ${progress.minutesUntilStart}m" else "Upcoming",
                            tone = HomeTone.NEUTRAL,
                            filled = false
                        )
                    } else if (isCompleted) {
                        HomeChip(text = "Done", tone = HomeTone.NEUTRAL, filled = false)
                    }
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = "${card.attended}/${card.total}",
                        style = type.meta,
                        color = colors.textMuted,
                        maxLines = 1
                    )
                    Spacer(Modifier.width(HomeSpace.sm))
                    HomeChip(text = "${card.percentage.toInt()}%", tone = bunk.tone)
                }

                Column {
                    Text(
                        text = card.courseTitle,
                        style = type.pillTitle,
                        color = colors.textPrimary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(HomeSpace.xs))
                    Text(
                        text = "${card.courseCode} • ${card.faculty.ifBlank { "Faculty assigned" }}",
                        style = type.subline,
                        color = colors.textMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                androidx.compose.material3.HorizontalDivider(color = palette.borderMuted.copy(alpha = HomeAlpha.DIVIDER))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    HomeMetaBit(Icons.Rounded.Schedule, card.time, colors.accent)
                    Spacer(Modifier.width(HomeSpace.row))
                    HomeMetaBit(Icons.Rounded.LocationOn, card.venue, colors.textMuted)
                    Spacer(Modifier.weight(1f))
                    BunkDot(bunk)
                    Spacer(Modifier.width(HomeSpace.sm))
                    Text(
                        text = bunk.text,
                        style = type.rowTitleSmall,
                        color = bunkTone.ink,
                        maxLines = 1
                    )
                }
            }
        } else {
            Row(
                modifier = Modifier.padding(
                    start = HomeSpace.icon,
                    end = HomeSpace.row,
                    top = HomeSpace.row,
                    bottom = HomeSpace.row
                ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        HomeChip(text = card.slotName, tone = HomeTone.ACCENT)
                        Spacer(Modifier.width(HomeSpace.sm))
                        Text(
                            text = card.courseTitle,
                            style = type.rowTitle,
                            color = colors.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (isLive) {
                            Spacer(Modifier.width(HomeSpace.sm))
                            HomeSolidChip(text = "${progress.minutesLeft}m", tint = colors.success)
                        } else if (isCompleted) {
                            Spacer(Modifier.width(HomeSpace.sm))
                            HomeChip(text = "Done", tone = HomeTone.NEUTRAL, filled = false)
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        HomeMetaBit(Icons.Rounded.Schedule, card.time, colors.accent, small = true)
                        Spacer(Modifier.width(HomeSpace.sm))
                        HomeMetaBit(Icons.Rounded.LocationOn, card.venue, colors.textMuted, small = true)
                        Spacer(Modifier.width(HomeSpace.sm))
                        BunkDot(bunk, small = true)
                        Spacer(Modifier.width(HomeSpace.xs))
                        Text(
                            text = bunk.text,
                            style = type.meta,
                            color = bunkTone.ink,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(Modifier.width(HomeSpace.sm))

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "${card.percentage.toInt()}%",
                        style = type.pillValue,
                        color = bunkTone.ink,
                        maxLines = 1
                    )
                    Text(
                        text = "${card.attended}/${card.total}",
                        style = type.micro,
                        color = colors.textMuted,
                        maxLines = 1
                    )
                }
            }
        }

        if (isLive) {
            HomeProgressRule(progressPct = progress.progressPct)
        }
    }
}

@Composable
private fun HomeMetaBit(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    tint: Color,
    small: Boolean = false
) {
    val colors = AmazeTheme.colors
    val type = rememberHomeType()
    val size = if (small) 12.dp else HomeSpace.iconLg

    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(size)
        )
        Spacer(Modifier.width(HomeSpace.xs))
        Text(
            text = text,
            style = if (small) type.meta else type.rowTitleSmall,
            color = colors.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** The 6px status pip that carries the bunk tone. */
@Composable
private fun BunkDot(bunk: HomeBunk, small: Boolean = false) {
    val tones = rememberHomeTones(AmazeTheme.colors)
    val tone = tones[bunk.tone] ?: tones.getValue(HomeTone.NEUTRAL)
    Box(
        modifier = Modifier
            .size(if (small) 6.dp else HomeSpace.between)
            .clip(CircleShape)
            .background(tone.dot)
    )
}

/** The 1.5dp live-progress rule at the foot of a running session's card. */
@Composable
private fun HomeProgressRule(progressPct: Float) {
    val colors = AmazeTheme.colors
    val widthFraction by animateFloatAsState(
        targetValue = (progressPct / 100f).coerceIn(0f, 1f),
        animationSpec = HomeMotion.progress()
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.5.dp)
            .background(colors.border.copy(alpha = 0.6f))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(widthFraction)
                .fillMaxHeight()
                .background(Brush.horizontalGradient(listOf(colors.accent, colors.success)))
        )
    }
}

// ── Tasks ──

@Composable
private fun HomeTasksSection(
    tasks: List<HomeTaskRow>,
    onOpenAll: () -> Unit,
    onOpenTask: () -> Unit
) {
    val colors = AmazeTheme.colors
    val type = rememberHomeType()

    Column(verticalArrangement = Arrangement.spacedBy(HomeSpace.between)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Rounded.Schedule,
                contentDescription = null,
                tint = colors.accent,
                modifier = Modifier.size(HomeSpace.icon)
            )
            Spacer(Modifier.width(HomeSpace.sm))
            Text(
                text = "Today's Tasks",
                style = type.sectionTitle,
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            Spacer(Modifier.width(HomeSpace.sm))
            HomeChip(text = tasks.size.toString(), tone = HomeTone.NEUTRAL)
            Spacer(Modifier.width(HomeSpace.sm))
            Text(
                text = "View all",
                style = type.rowTitleSmall,
                color = colors.accent,
                maxLines = 1,
                modifier = Modifier.homePressable(
                    shape = RoundedCornerShape(AmazeTheme.radius.xs),
                    onClick = onOpenAll,
                    pressedScale = HomePress.ICON_PRESSED
                )
            )
        }

        HomeListShell {
            tasks.forEachIndexed { index, task ->
                HomeListRow(
                    onClick = onOpenTask,
                    showDivider = index != tasks.lastIndex
                ) {
                    HomeChip(text = task.kind.label, tone = task.kind.tone)
                    Spacer(Modifier.width(HomeSpace.row))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = task.title,
                            style = type.rowTitleSmall,
                            color = colors.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (task.courseCode.isNotBlank()) {
                            Text(
                                text = task.courseCode,
                                style = type.meta,
                                color = colors.textMuted,
                                maxLines = 1
                            )
                        }
                    }
                    Icon(
                        imageVector = Icons.Rounded.ChevronRight,
                        contentDescription = null,
                        tint = colors.textMuted,
                        modifier = Modifier.size(HomeSpace.icon)
                    )
                }
            }
        }
    }
}

// ── Tool buttons ──

/**
 * One of the two actions under the timetable.
 *
 * [tone] picks the whole button — wash, border, ink — and [iconTint] lets the
 * icon break from it. The timetable action is the case that needs the second
 * knob: it is a neutral surface with an accent icon, so it reads as a neutral
 * control that happens to point somewhere, while the free-classrooms action is
 * tinted green and reads as its own thing.
 */
@Composable
private fun HomeToolButton(
    text: String,
    icon: ImageVector,
    tone: HomeTone,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconTint: Color? = null
) {
    val colors = AmazeTheme.colors
    val tones = rememberHomeTones(colors)
    val type = rememberHomeType()
    val homeShape = rememberHomeShape()
    val t = tones[tone] ?: tones.getValue(HomeTone.NEUTRAL)
    val shape = RoundedCornerShape(homeShape.card)

    Row(
        modifier = modifier
            .homePressable(shape = shape, onClick = onClick, pressedScale = HomePress.TILE_PRESSED)
            .clip(shape)
            .background(t.wash)
            .border(1.dp, t.border, shape)
            .padding(vertical = HomeSpace.row, horizontal = HomeSpace.sm),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint ?: t.dot,
            modifier = Modifier.size(HomeSpace.icon)
        )
        Spacer(Modifier.width(HomeSpace.sm))
        Text(
            text = text,
            style = type.rowTitleSmall,
            color = t.ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
