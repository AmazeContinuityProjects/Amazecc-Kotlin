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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amazecc.app.shared.api.AmazeClient
import com.amazecc.app.shared.repository.SettingsManager
import com.amazecc.app.shared.state.AppState
import com.amazecc.app.shared.theme.AmazeTheme
import com.amazecc.app.shared.ui.components.BOTTOM_NAV_PADDING
import com.amazecc.app.shared.ui.components.AmazeCard
import com.amazecc.app.shared.ui.components.MoodleLoginModal
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextOverflow
import com.amazecc.app.shared.ui.components.bouncySpring
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
    val endDay: Int = 0
)

// Helper: parse "YYYY-MM-DD" or "DD-MM-YYYY" -> Triple(day, month, year)
private fun parseExamDateParts(dateStr: String): Triple<Int, Int, Int> {
    val cleanDate = dateStr.trim().split(" ", "T").first() // strip time part
    val parts = cleanDate.split("-", "/")
    if (parts.size >= 3) {
        return try {
            if (parts[0].length == 4) { // YYYY-MM-DD
                Triple(parts[2].toInt(), parts[1].toInt(), parts[0].toInt())
            } else { // DD-MM-YYYY
                Triple(parts[0].toInt(), parts[1].toInt(), parts[2].toInt())
            }
        } catch (_: Exception) { Triple(0, 0, 0) }
    }
    return Triple(0, 0, 0)
}

// Helper: parse "July 2026" or "Jul 2026" -> Pair(monthNumber, year)
private fun parseMonthString(monthStr: String): Pair<Int, Int> {
    val parts = monthStr.trim().split(" ")
    val mNum = when (parts.firstOrNull()?.lowercase()?.take(3)) {
        "jan" -> 1; "feb" -> 2; "mar" -> 3; "apr" -> 4
        "may" -> 5; "jun" -> 6; "jul" -> 7; "aug" -> 8
        "sep" -> 9; "oct" -> 10; "nov" -> 11; "dec" -> 12
        else -> 1
    }
    val yr = parts.lastOrNull()?.toIntOrNull() ?: 2026
    return Pair(mNum, yr)
}

// Helper: display only the month part (strip year if present)
private fun monthDisplayName(monthStr: String): String {
    val parts = monthStr.trim().split(" ")
    return if (parts.size >= 2 && parts.last().length == 4 && parts.last().all { it.isDigit() }) {
        parts.dropLast(1).joinToString(" ")
    } else {
        monthStr
    }
}

// Helper: consolidate contiguous exam events into single range events
private fun getConsolidatedEventsForDisplay(
    activeMonthEvents: Map<Int, List<ConsolidatedEvent>>,
    selectedDay: Int?,
    monthName: String,
    examColor: Color
): List<Pair<String, ConsolidatedEvent>> {
    if (selectedDay != null) {
        return (activeMonthEvents[selectedDay] ?: emptyList()).map { "" to it }
    }

    val allDaysSorted = activeMonthEvents.keys.sorted()
    if (allDaysSorted.isEmpty()) return emptyList()

    val nonExamEvents = mutableListOf<Pair<String, ConsolidatedEvent>>()
    val examEventsByDay = mutableMapOf<Int, MutableList<ConsolidatedEvent>>()

    allDaysSorted.forEach { dayNum ->
        val dayLabel = "$monthName $dayNum"
        val events = activeMonthEvents[dayNum] ?: emptyList()
        var firstForDay = true
        events.forEachIndexed { i, ev ->
            val t = ev.title.lowercase()
            val isExam = ev.type.equals("Exam", ignoreCase = true) ||
                    t.contains("cat") || t.contains("fat") || t.contains("exam") || t.contains("assessment")

            if (isExam) {
                examEventsByDay.getOrPut(dayNum) { mutableListOf() }.add(ev)
            } else {
                nonExamEvents.add((if (firstForDay) dayLabel else "") to ev)
                firstForDay = false
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
                processedExamRanges.add(
                    rangeLabel to ConsolidatedEvent(
                        title = titleText,
                        type = "Exam",
                        timeOrLocation = if (rangeStart == prevDay) "Exam Day" else "${prevDay - rangeStart + 1} Days Exam Period",
                        color = examColor,
                        startDay = rangeStart,
                        endDay = prevDay
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
    val examData by AppState.examSchedule.collectAsState()
    val selectedSemester by AppState.selectedSemester.collectAsState()
    // Use cached calendarsList from AppState — survives app closes/opens
    val calendarsListRes by AppState.calendarsList.collectAsState()
    val isAppLoading by AppState.isLoading.collectAsState()

    var showMoodleModal by remember { mutableStateOf(false) }
    var selectedCalIdx by remember { mutableStateOf(0) }

    // If nothing cached yet, trigger a fetch automatically once (only when autoFetch is enabled)
    LaunchedEffect(selectedSemester, calendarsListRes) {
        if (autoFetch && calendarsListRes == null) {
            AppState.refreshCalendarsList()
        } else {
            // Restore saved preference
            val saved = SettingsManager.getPreferredCalendar()
            if (saved != null) {
                val idx = calendarsListRes?.calendars?.indexOfFirst { it.name == saved } ?: -1
                if (idx != -1) selectedCalIdx = idx
            }
        }
    }

    val calendars = calendarsListRes?.calendars ?: emptyList()
    val loading = isAppLoading && calendarsListRes == null
    val errorMsg: String? = if (!loading && calendars.isEmpty() && calendarsListRes != null)
        (calendarsListRes?.message ?: "No calendars available") else null

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
    val todayMonthStr = remember(now) {
        listOf("Jan","Feb","Mar","Apr","May","Jun","Jul","Aug","Sep","Oct","Nov","Dec")[now.monthNumber - 1]
    }
    val todayYearNum = now.year

    val activeCalendar = calendars.getOrNull(selectedCalIdx)
    val allMonths = activeCalendar?.months ?: emptyList()

    var selectedMonthIdx by remember { mutableStateOf(0) }
    LaunchedEffect(allMonths) {
        if (allMonths.isNotEmpty() && selectedMonthIdx == 0) {
            val idx = allMonths.indexOfFirst { monthDisplayName(it.month).lowercase().startsWith(todayMonthStr.lowercase()) }
            if (idx != -1) selectedMonthIdx = idx
        }
    }

    val activeMonth = allMonths.getOrNull(selectedMonthIdx)

    var filterHolidays by remember { mutableStateOf(true) }
    var filterExams by remember { mutableStateOf(true) }
    var filterODs by remember { mutableStateOf(true) }
    var filterClasses by remember { mutableStateOf(true) }

    val activeMonthEvents = remember(activeMonth, moodleData, examData, filterHolidays, filterExams, filterODs, filterClasses) {
        val map = mutableMapOf<Int, MutableList<ConsolidatedEvent>>()
        if (activeMonth == null) return@remember map

        val (monthNum, yearNum) = parseMonthString(activeMonth.month)

        activeMonth.days.forEach { day ->
            val list = map.getOrPut(day.date) { mutableListOf() }
            day.events.forEach { ev ->
                val type = if (ev.type.isNotBlank()) ev.type else "Event"
                
                val dayOrderLabel = com.amazecc.app.shared.utils.AttendanceTimetable.getDayOrderLabelFromText(ev.text)
                    ?: com.amazecc.app.shared.utils.AttendanceTimetable.getDayOrderLabelFromText(ev.category)
                    ?: com.amazecc.app.shared.utils.AttendanceTimetable.getDayOrderLabelFromText(type)

                val isHoliday = ev.text.contains("Holiday", true) || ev.text.contains("Vacation", true) || ev.text.contains("Pooja", true)
                val isOD = ev.text.contains("OD", true) || ev.text.contains("On Duty", true) || ev.text.contains("OnDuty", true) || type.contains("OD", true)
                val isClass = ev.text.contains("Instructional Day", true) || type.contains("Instructional", true) || ev.text.contains("Working Day", true) || dayOrderLabel != null
                val isExam = ev.text.contains("CAT", true) || ev.text.contains("FAT", true) || ev.text.contains("Exam", true) || type.contains("Exam", true)
                
                if (isHoliday && !filterHolidays) return@forEach
                if (isOD && !filterODs) return@forEach
                if (isClass && !isExam && !filterClasses) return@forEach
                if (isExam && !filterExams) return@forEach

                val titleText = if (dayOrderLabel != null && isClass && !isExam && !isHoliday) {
                    "Instructional Day ($dayOrderLabel)"
                } else ev.text
                
                val col = try {
                    ev.color?.let { Color(it.removePrefix("#").toLong(16) or 0xFF000000) }
                } catch (_: Exception) { null } ?: (
                    when {
                        isExam -> colors.chart1
                        isHoliday -> colors.danger
                        isClass && dayOrderLabel != null -> colors.success
                        else -> colors.accent
                    }
                )
                val categoryText = if (dayOrderLabel != null) "Follows $dayOrderLabel" else (ev.category ?: "")
                list.add(ConsolidatedEvent(titleText, if (isExam) "Exam" else type, categoryText, col, startDay = day.date, endDay = day.date))
            }
        }

        moodleData?.data?.forEach { m ->
            try {
                val parts = m.due.split("-", "T", " ")
                if (parts.size >= 3) {
                    val y = parts[0].toInt()
                    val mNum = parts[1].toInt()
                    val dNum = parts[2].substring(0, 2).toInt()
                    if (y == yearNum && mNum == monthNum && !m.done && !m.hidden) {
                        if (filterClasses) {
                            val list = map.getOrPut(dNum) { mutableListOf() }
                            val nameParts = m.name.split("/")
                            val taskName = if (nameParts.size >= 3) nameParts.drop(2).joinToString("/") else m.name
                            list.add(ConsolidatedEvent(taskName, "Moodle", "Due", colors.chart3, startDay = dNum, endDay = dNum))
                        }
                    }
                }
            } catch (e: Exception) { println("AmazeCC: CalendarScreen moodleEvents — ${e.message}") }
        }

        examData?.schedule?.forEach { (type, exams) ->
            exams.forEach { ex ->
                try {
                    val (exDay, exMonth, exYear) = parseExamDateParts(ex.examDate)
                    if (exYear == yearNum && exMonth == monthNum) {
                        if (filterExams) {
                            val list = map.getOrPut(exDay) { mutableListOf() }
                            list.add(ConsolidatedEvent("${ex.courseCode} ($type)", "Exam", "${ex.examTime} · ${ex.venue}", colors.chart1, startDay = exDay, endDay = exDay))
                        }
                    }
                } catch (e: Exception) { println("AmazeCC: CalendarScreen examEvents — ${e.message}") }
            }
        }

        map
    }

    var selectedDay by remember { mutableStateOf<Int?>(null) }
    val listState = rememberLazyListState()

    val eventsToShow = remember(activeMonthEvents, selectedDay, activeMonth) {
        val monthName = monthDisplayName(activeMonth?.month ?: "")
        val rawList = getConsolidatedEventsForDisplay(activeMonthEvents, selectedDay, monthName, colors.chart1)
        rawList.filter { (_, ev) ->
            val text = ev.title.lowercase()
            val timeLoc = ev.timeOrLocation.lowercase()
            val hasDayOrder = text.contains("order") || timeLoc.contains("order")
            val isPlainWorkingDay = (text == "instructional day" || text == "working day" || text == "instructional") &&
                    !text.contains("holiday") && !text.contains("exam") && !text.contains("cat") && !text.contains("fat") && !text.contains("od") && !hasDayOrder
            !isPlainWorkingDay
        }
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
                    item {
                        com.amazecc.app.shared.ui.components.HeaderSpacer()
                    }
                    // ── Filters ──
                    item {
                        LazyRow(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(horizontal = spacing.pageHorizontal)
                        ) {
                            val filters = listOf(
                                "Classes" to filterClasses,
                                "Exams" to filterExams,
                                "Holidays" to filterHolidays,
                                "ODs" to filterODs
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
                    // ── Calendar type selector ──
                    item {
                        if (calendars.isNotEmpty()) {
                            LazyRow(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                contentPadding = PaddingValues(horizontal = spacing.pageHorizontal)
                            ) {
                                items(calendars.indices.toList(), key = { it }) { idx ->
                                    val cal = calendars[idx]
                                    val isSelected = selectedCalIdx == idx
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
                                            .background(if (isSelected) colors.accent else colors.surface)
                                            .border(1.dp, if (isSelected) colors.accent else colors.border, CircleShape)
                                            .clickable(
                                                interactionSource = interactionSource,
                                                indication = null,
                                                onClick = {
                                                    selectedCalIdx = idx
                                                    SettingsManager.savePreferredCalendar(cal.name)
                                                }
                                            )
                                            .padding(horizontal = 14.dp, vertical = 6.dp)
                                    ) {
                                        Text(
                                            text = cal.name,
                                            style = AmazeTheme.typography.smallLabel.copy(
                                                color = if (isSelected) colors.background else colors.textPrimary,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = AmazeTheme.fontSize.sm
                                            )
                                        )
                                    }
                                }
                            }
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
                                            text = monthDisplayName(month.month).uppercase(),
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
                            val (monthNumber, gridYearNum) = parseMonthString(activeMonth.month)
                            val daysInMonth = activeMonth.days.maxOfOrNull { it.date } ?: 31
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
                                                val dayEvents = activeMonthEvents[dayNumber] ?: emptyList()
                                                val hasExam = dayEvents.any { ev ->
                                                    val t = ev.title.lowercase()
                                                    val type = ev.type.lowercase()
                                                    t.contains("cat") || t.contains("fat") || t.contains("exam") || t.contains("assessment") || type.contains("exam")
                                                }
                                                val hasHoliday = dayEvents.any { it.title.contains("Holiday", true) || it.title.contains("Vacation", true) || it.type.contains("Holiday", true) }
                                                val hasWorkingDay = dayEvents.any { it.title.contains("Instructional Day", true) || it.title.contains("Working Day", true) || it.type.contains("Instructional", true) }
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
                                                        val nonWorkingDayEvents = dayEvents.filter { ev ->
                                                            val t = ev.title.lowercase()
                                                            !(t.contains("instructional day") || t.contains("working day") || t == "instructional")
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
                            val activeMonthName = monthDisplayName(activeMonth?.month ?: "")
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
                                    .padding(horizontal = 16.dp, vertical = 24.dp)
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
                                        .padding(horizontal = 16.dp, vertical = 8.dp),
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
                            BouncyEventCard(ev = ev, colors = colors)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BouncyEventCard(ev: ConsolidatedEvent, colors: com.amazecc.app.shared.theme.AmazeColors) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = bouncySpring()
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 3.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
    ) {
        AmazeCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp, horizontal = 12.dp)
            ) {
                Text(
                    ev.title,
                    style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (ev.type.isNotBlank() || ev.timeOrLocation.isNotBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (ev.type.isNotBlank()) {
                            CalendarBadge(ev.type, ev.color.copy(alpha = 0.16f), ev.color)
                        }
                        if (ev.timeOrLocation.isNotBlank()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Rounded.AccessTime,
                                    contentDescription = null,
                                    tint = colors.textMuted,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    ev.timeOrLocation,
                                    style = AmazeTheme.typography.caption.copy(color = colors.textSecondary),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
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
