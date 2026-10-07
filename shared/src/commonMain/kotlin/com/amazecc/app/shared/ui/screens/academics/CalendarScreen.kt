package com.amazecc.app.shared.ui.screens.academics

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amazecc.app.shared.api.AmazeClient
import com.amazecc.app.shared.domain.DayEvent
import com.amazecc.app.shared.domain.DayModel
import com.amazecc.app.shared.domain.DayType
import com.amazecc.app.shared.domain.EventKind
import com.amazecc.app.shared.domain.Exam
import com.amazecc.app.shared.domain.MonthModel
import com.amazecc.app.shared.domain.Projections
import com.amazecc.app.shared.domain.activeMonthIndex
import com.amazecc.app.shared.domain.buildEnrichedCalendars
import com.amazecc.app.shared.domain.calendarSources
import com.amazecc.app.shared.domain.isExamDay
import com.amazecc.app.shared.domain.kindLabel
import com.amazecc.app.shared.repository.SettingsManager
import com.amazecc.app.shared.state.AcademicData
import com.amazecc.app.shared.state.AppState
import com.amazecc.app.shared.state.Screen
import com.amazecc.app.shared.theme.AmazeColors
import com.amazecc.app.shared.theme.AmazeTheme
import com.amazecc.app.shared.ui.components.BOTTOM_NAV_PADDING
import com.amazecc.app.shared.ui.components.AmazeCard
import com.amazecc.app.shared.ui.components.ExamEventCard
import com.amazecc.app.shared.ui.components.MoodleLoginModal
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextOverflow
import com.amazecc.app.shared.ui.components.bouncySpring
import com.amazecc.app.shared.utils.ExamUtils
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.toLocalDateTime

data class ConsolidatedEvent(
    val title: String,
    val type: String,
    val timeOrLocation: String = "",
    val color: Color,
    val startDay: Int = 0,
    val endDay: Int = 0,
    val exam: Exam? = null,
    val examType: String = "",
    val subtitle: String = "",
    /** The projection's own classification. Nothing downstream sniffs [title] or [type] for it. */
    val kind: EventKind = EventKind.EVENT,
)

/** The chips across the top of the list. They filter rows; the grid tints the day itself. */
private data class CalendarEventFilters(
    val classes: Boolean,
    val exams: Boolean,
    val holidays: Boolean,
    val od: Boolean,
    val tasks: Boolean,
)

/**
 * Whether one classified event survives the chip row.
 *
 * A non-instructional day is filtered with the holidays, because that is what the card it used to
 * collapse into was - VTOP files "No Instructional Day" under the same entries it files holidays
 * under, and the user who hides holidays wants that gone too.
 *
 * Moodle deadlines used to be gated by the Classes chip while task deadlines had their own; the two
 * are the same kind with and without a [DayEvent.taskId], so the split stays where it was.
 */
private fun passesFilter(
    event: DayEvent,
    dayType: DayType,
    filters: CalendarEventFilters,
): Boolean = when (event.kind) {
    EventKind.EXAM, EventKind.MILESTONE -> filters.exams
    EventKind.HOLIDAY -> filters.holidays
    EventKind.OD -> filters.od
    EventKind.CLASS -> filters.classes
    EventKind.WORKING ->
        if (dayType == DayType.NON_INSTRUCTIONAL) filters.holidays else filters.classes
    EventKind.ASSIGNMENT -> if (event.taskId != null) filters.tasks else filters.classes
    // EventHub registrations have no chip of their own: Node shows them and offers no way to hide
    // them either, and a signup the user made is the one thing on the day they definitely want.
    EventKind.EVENT -> true
}

/** The badge text on a card. [kindLabel] except for the two flavours of assignment. */
private fun displayType(event: DayEvent): String = when {
    event.kind == EventKind.ASSIGNMENT && event.taskId != null -> "Task"
    event.kind == EventKind.ASSIGNMENT -> "Moodle"
    else -> kindLabel(event.kind)
}

private fun colorFor(event: DayEvent, colors: AmazeColors): Color = when (event.kind) {
    EventKind.EXAM, EventKind.MILESTONE -> colors.chart1
    EventKind.HOLIDAY -> colors.danger
    EventKind.CLASS -> colors.success
    EventKind.WORKING -> colors.success
    EventKind.OD -> colors.accent
    EventKind.ASSIGNMENT -> if (event.taskId != null) colors.warning else colors.chart3
    EventKind.EVENT -> colors.accent
}

/**
 * The exam card behind a paper, matched back onto the projection.
 *
 * [buildEnrichedCalendars] classifies the paper and carries its course and date but not the
 * [Exam] row itself, and the card taps through to the schedule. Matching on course and date is the
 * same key `examEventsFor` de-duplicated on, so the paper that produced the event is the one found.
 */
private fun examFor(event: DayEvent, day: DayModel, exams: List<Exam>): Exam? {
    if (event.kind != EventKind.EXAM) return null
    val code = event.courseCode.orEmpty()
    if (code.isEmpty()) return null
    return exams.firstOrNull { exam ->
        exam.courseCode == code && ExamUtils.parseExamDateToLocalDate(exam.date) == day.fullDate
    }
}

/** One classified event, in the shape this screen renders. */
private fun DayEvent.toConsolidatedEvent(
    day: DayModel,
    colors: AmazeColors,
    exams: List<Exam>,
): ConsolidatedEvent = ConsolidatedEvent(
    title = title,
    type = displayType(this),
    timeOrLocation = detail.orEmpty(),
    color = colorFor(this, colors),
    startDay = day.date,
    endDay = day.date,
    exam = examFor(this, day, exams),
    subtitle = if (kind == EventKind.HOLIDAY) detail.orEmpty() else "",
    kind = kind,
)

/**
 * Group contiguous papers into one range row, and give every other event a day label.
 *
 * The split between "row" and "not a row" comes from [EventKind], not from reading the title:
 * [EventKind.WORKING] is the day's type - the grid tints it and the list has nothing to add - and
 * [EventKind.CLASS] is ordinary attendance, which a month-wide list would turn into a wall of rows
 * (the day detail below keeps them, which is where Node puts them too).
 */
private fun getConsolidatedEventsForDisplay(
    activeMonthEvents: Map<Int, List<ConsolidatedEvent>>,
    selectedDay: Int?,
    monthName: String,
    examColor: Color,
): List<Pair<String, ConsolidatedEvent>> {
    if (selectedDay != null) {
        return (activeMonthEvents[selectedDay] ?: emptyList())
            .filter { it.kind != EventKind.CLASS }
            .map { "" to it }
    }

    val allDaysSorted = activeMonthEvents.keys.sorted()
    if (allDaysSorted.isEmpty()) return emptyList()

    val nonExamEvents = mutableListOf<Pair<String, ConsolidatedEvent>>()
    val examEventsByDay = mutableMapOf<Int, MutableList<ConsolidatedEvent>>()

    allDaysSorted.forEach { dayNum ->
        val dayLabel = "$monthName $dayNum"
        var firstForDay = true
        (activeMonthEvents[dayNum] ?: emptyList()).forEach { ev ->
            when {
                ev.kind == EventKind.CLASS -> Unit
                ev.kind == EventKind.WORKING -> Unit
                ev.kind == EventKind.EXAM ->
                    examEventsByDay.getOrPut(dayNum) { mutableListOf() }.add(ev)
                else -> {
                    nonExamEvents.add((if (firstForDay) dayLabel else "") to ev)
                    firstForDay = false
                }
            }
        }
    }

    val cat1Regex = Regex("""(?i)\b(cat\s*[-_]?\s*(1|i)|continuous\s+assessment\s+test\s*[-_]?\s*(1|i))\b""")
    val cat2Regex = Regex("""(?i)\b(cat\s*[-_]?\s*(2|ii)|continuous\s+assessment\s+test\s*[-_]?\s*(2|ii))\b""")
    val cat3Regex = Regex("""(?i)\b(cat\s*[-_]?\s*(3|iii)|continuous\s+assessment\s+test\s*[-_]?\s*(3|iii))\b""")
    val fatRegex = Regex("""(?i)\b(fat|final\s+assessment\s+test|term\s+end|semester\s+end)\b""")
    val labRegex = Regex("""(?i)\b(lab|practical)\b""")
    val midTermRegex = Regex("""(?i)\b(mid\s*[-_]?\s*term)\b""")

    fun getExamGroupName(title: String): String {
        val cleaned = title.trim()
        return when {
            cat1Regex.containsMatchIn(cleaned) -> "CAT-1 Exam"
            cat2Regex.containsMatchIn(cleaned) -> "CAT-2 Exam"
            cat3Regex.containsMatchIn(cleaned) -> "CAT-3 Exam"
            labRegex.containsMatchIn(cleaned) -> "Lab Exam"
            fatRegex.containsMatchIn(cleaned) -> "FAT Exam"
            midTermRegex.containsMatchIn(cleaned) -> "Mid-Term Exam"
            else -> cleaned.split("/", "-", "(").firstOrNull()?.trim() ?: cleaned
        }
    }

    val examGroups = mutableMapOf<String, MutableList<Int>>()
    examEventsByDay.forEach { (day, evList) ->
        evList.forEach { ev ->
            val group = getExamGroupName(ev.title)
            examGroups.getOrPut(group) { mutableListOf() }.add(day)
        }
    }

    val processedExamRanges = mutableListOf<Pair<String, ConsolidatedEvent>>()

    examGroups.forEach { (groupName, daysList) ->
        val sortedDays = daysList.distinct().sorted()
        if (sortedDays.isEmpty()) return@forEach

        var rangeStart = sortedDays.first()
        var prevDay = sortedDays.first()

        for (i in 1..sortedDays.size) {
            val currDay = sortedDays.getOrNull(i)
            if (currDay != null && (currDay == prevDay + 1 || currDay == prevDay + 2 || currDay == prevDay + 3)) {
                prevDay = currDay
            } else {
                val rangeLabel = if (rangeStart == prevDay) {
                    "$monthName $rangeStart"
                } else {
                    "$monthName $rangeStart – $monthName $prevDay"
                }

                val titleText = if (rangeStart == prevDay) groupName else "$groupName ($rangeLabel)"
                val singleExamEv = if (rangeStart == prevDay) examEventsByDay[rangeStart]?.firstOrNull() else null
                processedExamRanges.add(
                    rangeLabel to ConsolidatedEvent(
                        title = titleText,
                        type = "Exam",
                        timeOrLocation = if (rangeStart == prevDay) "Exam Day" else "${prevDay - rangeStart + 1} Days Exam Period",
                        color = examColor,
                        startDay = rangeStart,
                        endDay = prevDay,
                        exam = singleExamEv?.exam,
                        examType = singleExamEv?.examType ?: ""
                    )
                )
                if (currDay != null) {
                    rangeStart = currDay
                    prevDay = currDay
                }
            }
        }
    }

    return (nonExamEvents + processedExamRanges).sortedBy { (_, ev) -> ev.startDay }
}

@Composable
fun CalendarScreen(onBack: () -> Unit, showHeader: Boolean = true, autoFetch: Boolean = true) {
    val colors = AmazeTheme.colors
    val radius = AmazeTheme.radius
    val spacing = AmazeTheme.spacing
    val moodleData by AppState.moodleData.collectAsState()
    val domain by AppState.domain.collectAsState()
    val selectedSemester by AppState.selectedSemester.collectAsState()
    val registeredEvents by AppState.registeredEvents.collectAsState()
    val tasks by AppState.tasks.collectAsState()
    val isAppLoading by AppState.isLoading.collectAsState()

    var showMoodleModal by remember { mutableStateOf(false) }
    var selectedCalIdx by remember { mutableStateOf(0) }

    // The picker lives on the snapshot, not beside it: `Schedule.calendarsList` is every course
    // calendar the user can choose between, kept verbatim as received.
    val calendarList = domain.schedule.calendarsList
    val calendars = calendarList?.calendars ?: emptyList()
    val loading = isAppLoading && calendarList == null
    val errorMsg: String? = if (!loading && calendars.isEmpty() && calendarList != null)
        (calendarList.message ?: "No calendars available") else null

    // If nothing cached yet, trigger a fetch automatically once (only when autoFetch is enabled)
    LaunchedEffect(selectedSemester, calendarList) {
        if (autoFetch && calendarList == null) {
            AppState.refreshCalendarsList()
        } else {
            // Restore saved preference
            val saved = SettingsManager.getPreferredCalendar()
            if (saved != null) {
                val idx = calendars.indexOfFirst { it.name == saved }
                if (idx != -1) selectedCalIdx = idx
            }
        }
    }

    if (showMoodleModal) {
        MoodleLoginModal(
            onDismiss = { showMoodleModal = false },
            onLogin = { user, pass ->
                try {
                    val res = AmazeClient.fetchMoodleData(user, pass)
                    if (res.success) {
                        AppState.updateMoodleData(res)
                        SettingsManager.saveMoodleCredentials(user, pass)
                        true
                    } else false
                } catch (_: Exception) { false }
            }
        )
    }

    val now = remember {
        Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
    }
    val todayYearNum = now.year

    val activeCalendar = calendars.getOrNull(selectedCalIdx)

    /**
     * The one port of `buildEnrichedCalendars`.
     *
     * The grid, the cell tints and the list all read these months; nothing below re-derives an
     * event from a raw payload, which is the whole reason the old day panel and its grid could
     * disagree about what a day contained.
     */
    val allMonths: List<MonthModel> = remember(activeCalendar, domain, moodleData, tasks, selectedSemester) {
        buildEnrichedCalendars(
            calendarSources(
                calendar = activeCalendar,
                snapshot = domain,
                semesterId = selectedSemester,
                moodle = moodleData?.data.orEmpty(),
                tasks = tasks,
                registeredEvents = registeredEvents?.events.orEmpty(),
                // `SettingsManager.getODTrackerState()` is raw JSON the OD tracker screen owns;
                // wiring it here is part of that screen's move to `odRecordsFrom`. Until then an
                // OD row reads "On-Duty" instead of "wasted"/"recovered".
                odTracker = emptyMap(),
            ),
            now = now.date,
        )
    }

    var selectedMonthIdx by remember { mutableStateOf(0) }
    LaunchedEffect(allMonths) {
        if (allMonths.isNotEmpty() && selectedMonthIdx == 0) {
            selectedMonthIdx = activeMonthIndex(allMonths, now.date)
        }
    }

    val activeMonth = allMonths.getOrNull(selectedMonthIdx)
    val dayModels: Map<Int, DayModel> = remember(activeMonth) {
        activeMonth?.days.orEmpty().associateBy { it.date }
    }

    var filterHolidays by remember { mutableStateOf(true) }
    var filterExams by remember { mutableStateOf(true) }
    var filterODs by remember { mutableStateOf(true) }
    var filterClasses by remember { mutableStateOf(true) }
    var filterTasks by remember { mutableStateOf(true) }

    val selectedExams = remember(domain, selectedSemester) {
        Projections.examsForKnownSemester(domain, selectedSemester)
    }

    /**
     * The selected month in the shape this screen renders: one entry per day that has something
     * left after the chips, so an empty day never becomes an empty row group.
     */
    val activeMonthEvents: Map<Int, List<ConsolidatedEvent>> = remember(
        activeMonth, selectedExams, colors,
        filterHolidays, filterExams, filterODs, filterClasses, filterTasks,
    ) {
        val filters = CalendarEventFilters(
            classes = filterClasses,
            exams = filterExams,
            holidays = filterHolidays,
            od = filterODs,
            tasks = filterTasks,
        )
        activeMonth?.days.orEmpty()
            .mapNotNull { day ->
                val visible = day.events.filter { passesFilter(it, day.dayType, filters) }
                if (visible.isEmpty()) null
                else day.date to visible.map { it.toConsolidatedEvent(day, colors, selectedExams) }
            }
            .toMap()
    }

    var selectedDay by remember { mutableStateOf<Int?>(null) }
    val listState = rememberLazyListState()

    val eventsToShow = remember(activeMonthEvents, selectedDay, activeMonth) {
        getConsolidatedEventsForDisplay(
            activeMonthEvents = activeMonthEvents,
            selectedDay = selectedDay,
            monthName = activeMonth?.label.orEmpty(),
            examColor = colors.chart1,
        )
    }
    Box(modifier = Modifier.fillMaxSize()) {
        when {
            loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = colors.accent, strokeWidth = 3.dp, modifier = Modifier.size(32.dp))
                        Spacer(modifier = Modifier.height(AmazeTheme.spacing.sm))
                        Text("Loading calendars…", color = colors.textMuted)
                    }
                }
            }
            errorMsg != null -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                        Icon(Icons.Rounded.ErrorOutline, null, tint = colors.danger, modifier = Modifier.size(40.dp))
                        Spacer(modifier = Modifier.height(AmazeTheme.spacing.sm))
                        Text(errorMsg, color = colors.danger, textAlign = TextAlign.Center)
                        Spacer(modifier = Modifier.height(AmazeTheme.spacing.sm))
                        Text("Pull to refresh or tap sync", color = colors.textMuted, fontSize = AmazeTheme.fontSize.base)
                    }
                }
            }
            else -> {
                // Single unified LazyColumn — calendar + events scroll together
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = BOTTOM_NAV_PADDING)
                ) {
                    if (showHeader) {
                        item {
                            com.amazecc.app.shared.ui.components.HeaderSpacer()
                        }
                    }
                    // ── Month selector ──
                    item {
                        if (allMonths.isNotEmpty()) {
                            LazyRow(
                                modifier = Modifier.fillMaxWidth().padding(vertical = spacing.sm),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                contentPadding = PaddingValues(horizontal = spacing.pageHorizontal)
                            ) {
                                items(allMonths.indices.toList(), key = { it }) { idx ->
                                    val month = allMonths[idx]
                                    val isSelected = selectedMonthIdx == idx
                                    val interactionSource = remember { MutableInteractionSource() }
                                    val isPressed by interactionSource.collectIsPressedAsState()
                                    val scale by animateFloatAsState(
                                        targetValue = if (isPressed) 0.94f else 1f,
                                        animationSpec = bouncySpring()
                                    )

                                    Box(
                                        modifier = Modifier
                                            .graphicsLayer {
                                                scaleX = scale
                                                scaleY = scale
                                            }
                                            .clip(CircleShape)
                                            .background(if (isSelected) colors.accent else colors.surface)
                                            .border(
                                                1.dp,
                                                if (isSelected) colors.accent else colors.border,
                                                CircleShape
                                            )
                                            .clickable(
                                                interactionSource = interactionSource,
                                                indication = null,
                                                onClick = { selectedMonthIdx = idx; selectedDay = null }
                                            )
                                            .padding(horizontal = 16.dp, vertical = 8.dp)
                                    ) {
                                        Text(
                                            text = month.shortLabel.uppercase(),
                                            style = AmazeTheme.typography.smallLabel.copy(
                                                color = if (isSelected) colors.background else colors.textPrimary,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = AmazeTheme.fontSize.xs
                                            ),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // ── Grid header (day labels) ──
                    item {
                        if (activeMonth != null) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = spacing.pageHorizontal, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                listOf("SUN", "MON", "TUE", "WED", "THU", "FRI", "SAT").forEach { d ->
                                    Text(
                                        d, modifier = Modifier.weight(1f),
                                        textAlign = TextAlign.Center,
                                        style = AmazeTheme.typography.smallLabel.copy(
                                            color = colors.textMuted,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = AmazeTheme.fontSize.micro
                                        )
                                    )
                                }
                            }
                        }
                    }

                    // ── Calendar grid ──
                    item {
                        if (activeMonth != null) {
                            val monthNumber = activeMonth.monthIndex + 1
                            val gridYearNum = activeMonth.year
                            val daysInMonth = activeMonth.summary.total
                            val startCol = if (gridYearNum > 0)
                                LocalDate(gridYearNum, monthNumber, 1).dayOfWeek.isoDayNumber % 7
                            else 0
                            val totalRows = (daysInMonth + startCol + 6) / 7
                            var currentDay = 1

                            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = spacing.sm)) {
                                for (row in 0 until totalRows) {
                                    Row(modifier = Modifier.fillMaxWidth()) {
                                        for (col in 0..6) {
                                            val isBlank = row == 0 && col < startCol
                                            val dayNumber = if (isBlank) 0 else currentDay
                                            if (!isBlank && dayNumber in 1..daysInMonth) {
                                                val dayModel = dayModels[dayNumber]
                                                val dayEvents = activeMonthEvents[dayNumber] ?: emptyList()
                                                // The day's own type decides what it is; the chip decides whether the user wants to see it.
                                                val hasExam = filterExams && dayModel?.let { isExamDay(it) } == true
                                                val hasHoliday = filterHolidays && dayModel?.dayType == DayType.HOLIDAY
                                                val hasNoInstructional = filterHolidays && dayModel?.dayType == DayType.NON_INSTRUCTIONAL
                                                val hasWorkingDay = filterClasses &&
                                                        (dayModel?.dayType == DayType.INSTRUCTIONAL || dayModel?.dayType == DayType.SEMIHOLIDAY)
                                                val isToday = dayNumber == now.dayOfMonth &&
                                                        monthNumber == now.monthNumber &&
                                                        gridYearNum == todayYearNum
                                                val isSelected = selectedDay == dayNumber
                                                val d = dayNumber

                                                val interactionSource = remember { MutableInteractionSource() }
                                                val isPressed by interactionSource.collectIsPressedAsState()
                                                val cellScale by animateFloatAsState(
                                                    targetValue = if (isPressed) 0.90f else 1f,
                                                    animationSpec = bouncySpring()
                                                )

                                                Box(
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .aspectRatio(1f)
                                                        .padding(3.dp)
                                                        .graphicsLayer {
                                                            scaleX = cellScale
                                                            scaleY = cellScale
                                                        }
                                                        .clip(CircleShape)
.background(
                                                             when {
                                                                 isSelected -> colors.accent
                                                                 isToday && hasExam -> colors.chart1.copy(alpha = 0.22f)
                                                                 isToday -> colors.accent.copy(alpha = 0.18f)
                                                                 hasExam -> colors.chart1.copy(alpha = 0.22f)
                                                                 hasHoliday -> colors.danger.copy(alpha = 0.18f)
                                                                 hasNoInstructional -> colors.danger.copy(alpha = 0.18f)
                                                                 hasWorkingDay -> colors.success.copy(alpha = 0.18f)
                                                                 dayEvents.isNotEmpty() -> colors.surface
                                                                 else -> Color.Transparent
                                                             }
                                                         )
                                                         .border(
                                                             1.dp,
                                                             when {
                                                                 isSelected -> colors.accent
                                                                 isToday && hasExam -> colors.accent
                                                                 isToday -> colors.accent
                                                                 hasExam -> colors.chart1.copy(alpha = 0.85f)
                                                                 hasHoliday -> colors.danger.copy(alpha = 0.5f)
                                                                 hasNoInstructional -> colors.danger.copy(alpha = 0.5f)
                                                                 hasWorkingDay -> colors.success.copy(alpha = 0.5f)
                                                                 dayEvents.isNotEmpty() -> colors.border
                                                                 else -> Color.Transparent
                                                             },
                                                             CircleShape
                                                         )
                                                        .clickable(
                                                            interactionSource = interactionSource,
                                                            indication = null,
                                                            onClick = { selectedDay = if (selectedDay == d) null else d }
                                                        ),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                        Text(
                                                            dayNumber.toString(),
                                                            style = AmazeTheme.typography.body.copy(
                                                                color = when {
                                                                    isSelected -> colors.background
                                                                    isToday -> colors.accent
                                                                    hasExam -> colors.chart1
                                                                    hasHoliday -> colors.danger
                                                                    hasWorkingDay -> colors.success
                                                                    else -> colors.textPrimary
                                                                },
                                                                fontWeight = if (isToday || isSelected || dayEvents.isNotEmpty()) FontWeight.Bold else FontWeight.Medium,
                                                                fontSize = AmazeTheme.fontSize.base
                                                            )
                                                        )
                                                        // The same rule `dayMarkers` applies to a
                                                        // cell: a class and the day's own type are
                                                        // the background state, not news.
                                                        val nonWorkingDayEvents = dayEvents.filter {
                                                            it.kind != EventKind.CLASS && it.kind != EventKind.WORKING
                                                        }
                                                        if (nonWorkingDayEvents.isNotEmpty()) {
                                                            Row(
                                                                horizontalArrangement = Arrangement.spacedBy(2.dp),
                                                                modifier = Modifier.padding(top = 2.dp)
                                                            ) {
                                                                nonWorkingDayEvents.take(3).forEach { ev ->
                                                                    Box(
                                                                        modifier = Modifier
                                                                            .size(4.dp)
                                                                            .clip(CircleShape)
                                                                            .background(if (isSelected) colors.background else ev.color)
                                                                    )
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                                if (row > 0 || col >= startCol) currentDay++
                                            } else {
                                                Box(modifier = Modifier.weight(1f).aspectRatio(1f))
                                                if (isBlank) { /* no increment */ } else { currentDay++ }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // ── Event type filters ──
                    item {
                        LazyRow(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(horizontal = spacing.pageHorizontal)
                        ) {
                            val filters = listOf(
                                "Classes" to filterClasses,
                                "Exams" to filterExams,
                                "Holidays" to filterHolidays,
                                "ODs" to filterODs,
                                "Tasks" to filterTasks
                            )
                            items(filters, key = { it.first }) { (label, isActive) ->
                                val interactionSource = remember { MutableInteractionSource() }
                                val isPressed by interactionSource.collectIsPressedAsState()
                                val scale by animateFloatAsState(
                                    targetValue = if (isPressed) 0.94f else 1f,
                                    animationSpec = bouncySpring()
                                )

                                Box(
                                    modifier = Modifier
                                        .graphicsLayer { scaleX = scale; scaleY = scale }
                                        .clip(CircleShape)
                                        .background(if (isActive) colors.accent else colors.surface)
                                        .border(1.dp, if (isActive) colors.accent else colors.border, CircleShape)
                                        .clickable(
                                            interactionSource = interactionSource,
                                            indication = null,
                                            onClick = {
                                                when (label) {
                                                    "Classes" -> filterClasses = !filterClasses
                                                    "Exams" -> filterExams = !filterExams
                                                    "Holidays" -> filterHolidays = !filterHolidays
                                                    "ODs" -> filterODs = !filterODs
                                                    "Tasks" -> filterTasks = !filterTasks
                                                }
                                            }
                                        )
                                        .padding(horizontal = 14.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = label,
                                        style = AmazeTheme.typography.smallLabel.copy(
                                            color = if (isActive) colors.background else colors.textPrimary,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = AmazeTheme.fontSize.sm
                                        )
                                    )
                                }
                            }
                        }
                    }

                    // ── Divider ──
                    item { HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp), color = colors.border.copy(alpha = 0.5f)) }

                    // ── Events section header ──
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val activeMonthName = activeMonth?.label.orEmpty()
                            val titleText = if (selectedDay != null) {
                                "$activeMonthName $selectedDay"
                            } else {
                                "ALL EVENTS — $activeMonthName"
                            }
                            Text(
                                text = titleText.uppercase(),
                                style = AmazeTheme.typography.smallLabel.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = colors.accent,
                                    fontSize = AmazeTheme.fontSize.xs
                                )
                            )

                            if (selectedDay != null) {
                                Box(
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .background(colors.surface)
                                        .border(1.dp, colors.border, CircleShape)
                                        .clickable { selectedDay = null }
                                        .padding(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        "SHOW ALL",
                                        style = AmazeTheme.typography.smallLabel.copy(
                                            color = colors.textSecondary,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = AmazeTheme.fontSize.micro
                                        )
                                    )
                                }
                            }
                        }
                    }

                    // ── Events list ──
                    if (eventsToShow.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 24.dp)
                                    .clip(RoundedCornerShape(AmazeTheme.radius.medium))
                                    .background(colors.surface)
                                    .border(1.dp, colors.border, RoundedCornerShape(AmazeTheme.radius.medium))
                                    .padding(24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Box(
                                        modifier = Modifier
                                            .size(48.dp)
                                            .clip(CircleShape)
                                            .background(colors.textMuted.copy(alpha = 0.12f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Rounded.CalendarToday, null, tint = colors.textMuted, modifier = Modifier.size(24.dp))
                                    }
                                    Spacer(modifier = Modifier.height(AmazeTheme.spacing.sm))
                                    Text(
                                        "No events scheduled${if (selectedDay != null) " for this date" else " for this month"}",
                                        color = colors.textSecondary,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = AmazeTheme.fontSize.base
                                    )
                                }
                            }
                        }
                    } else {
                        itemsIndexed(eventsToShow, key = { idx, pair -> "${pair.first}-${pair.second.title}-${pair.second.type}-$idx" }) { _, pair ->
                            val dateLabel = pair.first
                            val ev = pair.second
                            if (dateLabel.isNotEmpty()) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 8.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .clip(CircleShape)
                                            .background(colors.accent.copy(alpha = 0.12f))
                                            .padding(horizontal = 10.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = dateLabel.uppercase(),
                                            style = AmazeTheme.typography.smallLabel.copy(
                                                fontWeight = FontWeight.Bold,
                                                color = colors.accent,
                                                fontSize = AmazeTheme.fontSize.micro
                                            )
                                        )
                                    }
                                }
                            }
                            if (ev.exam != null) {
                                ExamEventCard(
                                    exam = ev.exam,
                                    examType = ev.examType,
                                    colors = colors,
                                    onClick = { AppState.navigateTo(Screen.EXAM_SCHEDULE) }
                                )
                            } else {
                                BouncyEventCard(ev = ev, colors = colors)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CalendarDetailRow(icon: ImageVector, label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    val colors = AmazeTheme.colors
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = colors.textMuted, modifier = Modifier.size(15.dp))
        Spacer(modifier = Modifier.width(AmazeTheme.spacing.xs))
        Column {
            Text(label, style = AmazeTheme.typography.caption.copy(color = colors.textMuted, fontSize = AmazeTheme.fontSize.micro))
            Text(
                value.ifBlank { "N/A" },
                style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.SemiBold, color = color, fontSize = AmazeTheme.fontSize.sm),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun BouncyEventCard(ev: ConsolidatedEvent, colors: com.amazecc.app.shared.theme.AmazeColors) {
    var expanded by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = bouncySpring()
    )

    val iconVector = when {
        ev.kind == EventKind.EXAM || ev.kind == EventKind.MILESTONE -> Icons.Rounded.EventSeat
        ev.kind == EventKind.HOLIDAY -> Icons.Rounded.Celebration
        ev.type == "Moodle" -> Icons.AutoMirrored.Rounded.MenuBook
        ev.type == "Task" -> Icons.Rounded.TaskAlt
        else -> Icons.Rounded.CalendarToday
    }

    val typeLabel = ev.type.uppercase().ifBlank { "EVENT" }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 3.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
    ) {
        AmazeCard(
            modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded },
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Header Row (Icon Badge + Title + Status Chip)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(AmazeTheme.radius.small))
                            .background(ev.color.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(iconVector, contentDescription = null, tint = ev.color, modifier = Modifier.size(20.dp))
                    }
                    Spacer(modifier = Modifier.width(AmazeTheme.spacing.sm))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = ev.title,
                            style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (ev.subtitle.isNotBlank()) {
                            Text(
                                text = ev.subtitle,
                                style = AmazeTheme.typography.caption.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (ev.kind == EventKind.HOLIDAY) colors.danger else colors.accent,
                                    fontSize = AmazeTheme.fontSize.xs
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    CalendarBadge(typeLabel, ev.color.copy(alpha = 0.14f), ev.color)
                }

                HorizontalDivider(color = colors.border.copy(alpha = 0.4f))

                // Detail Rows (Exam Schedule Card Style)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val dateRangeText = if (ev.startDay > 0 && ev.endDay > ev.startDay) {
                        "Day ${ev.startDay} – Day ${ev.endDay}"
                    } else if (ev.startDay > 0) {
                        "Day ${ev.startDay}"
                    } else "Date Event"

                    CalendarDetailRow(
                        icon = Icons.Rounded.CalendarToday,
                        label = "Schedule",
                        value = dateRangeText,
                        color = colors.textPrimary,
                        modifier = Modifier.weight(1f)
                    )

                    if (ev.timeOrLocation.isNotBlank()) {
                        CalendarDetailRow(
                            icon = Icons.Rounded.AccessTime,
                            label = "Info / Follows",
                            value = ev.timeOrLocation,
                            color = ev.color,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Icon(
                        if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                        contentDescription = null,
                        tint = colors.textMuted,
                        modifier = Modifier.size(20.dp)
                    )
                }

                if (expanded) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(AmazeTheme.radius.xs))
                            .background(colors.accentSurface.copy(alpha = 0.3f))
                            .border(1.dp, colors.border.copy(alpha = 0.5f), RoundedCornerShape(AmazeTheme.radius.xs))
                            .padding(10.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                "Event Details",
                                style = AmazeTheme.typography.smallLabel.copy(color = colors.accent, fontWeight = FontWeight.Bold, fontSize = AmazeTheme.fontSize.micro)
                            )
                            Text(
                                ev.title,
                                style = AmazeTheme.typography.body.copy(color = colors.textPrimary, fontSize = AmazeTheme.fontSize.sm)
                            )
                            if (ev.timeOrLocation.isNotBlank()) {
                                Text(
                                    "Note: ${ev.timeOrLocation}",
                                    style = AmazeTheme.typography.caption.copy(color = colors.textSecondary)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CalendarBadge(text: String, backgroundColor: Color, textColor: Color) {
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(backgroundColor)
            .border(1.dp, textColor.copy(alpha = 0.3f), CircleShape)
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(
            text = text.uppercase(),
            style = AmazeTheme.typography.smallLabel.copy(fontWeight = FontWeight.Bold, color = textColor)
        )
    }
}
