package com.amazecc.app.shared.ui.screens.academics

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import com.amazecc.app.shared.ui.components.AmazeCard
import com.amazecc.app.shared.ui.components.MoodleLoginModal
import com.amazecc.app.shared.ui.components.ScreenHeader
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.toLocalDateTime

data class ConsolidatedEvent(
    val title: String,
    val type: String,
    val timeOrLocation: String = "",
    val color: Color
)

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
    // If last token is a 4-digit year, drop it
    return if (parts.size >= 2 && parts.last().length == 4 && parts.last().all { it.isDigit() }) {
        parts.dropLast(1).joinToString(" ")
    } else {
        monthStr
    }
}

@Composable
fun CalendarScreen(@Suppress("UNUSED_PARAMETER") onBack: () -> Unit, showHeader: Boolean = true, autoFetch: Boolean = true) {
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

    val activeMonthEvents = remember(activeMonth, moodleData, examData) {
        val map = mutableMapOf<Int, MutableList<ConsolidatedEvent>>()
        if (activeMonth == null) return@remember map

        val (monthNum, yearNum) = parseMonthString(activeMonth.month)

        activeMonth.days.forEach { day ->
            val list = map.getOrPut(day.date) { mutableListOf() }
            day.events.forEach { ev ->
                val type = if (ev.type.isNotBlank()) ev.type else "Event"
                val col = try {
                    ev.color?.let { Color(it.removePrefix("#").toLong(16) or 0xFF000000) }
                } catch (_: Exception) { null } ?: (if (ev.text.contains("Holiday", true)) Color.Red else colors.accent)
                list.add(ConsolidatedEvent(ev.text, type, ev.category ?: "", col))
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
                        val list = map.getOrPut(dNum) { mutableListOf() }
                        val nameParts = m.name.split("/")
                        val taskName = if (nameParts.size >= 3) nameParts.drop(2).joinToString("/") else m.name
                        list.add(ConsolidatedEvent(taskName, "Moodle", "Due", colors.chart3))
                    }
                }
            } catch (e: Exception) { println("AmazeCC: CalendarScreen moodleEvents — ${e.message}") }
        }

        examData?.schedule?.forEach { (type, exams) ->
            exams.forEach { ex ->
                try {
                    val parts = ex.examDate.split("-")
                    if (parts.size >= 3) {
                        val dNum = parts[0].toInt()
                        val list = map.getOrPut(dNum) { mutableListOf() }
                        list.add(ConsolidatedEvent("${ex.courseCode} ($type)", "Exam", "${ex.examTime} · ${ex.venue}", colors.warning))
                    }
                } catch (e: Exception) { println("AmazeCC: CalendarScreen examEvents — ${e.message}") }
            }
        }

        map
    }

    var selectedDay by remember { mutableStateOf<Int?>(null) }
    val listState = rememberLazyListState()

    Column(modifier = Modifier.fillMaxSize()) {
        if (showHeader) {
            ScreenHeader(
                title = "Academic Calendar",
                description = "Schedule, exams & assignments",
                showBackButton = true,
                showSyncButton = true,
                onRefresh = { AppState.refreshCalendarsList() }
            )
        }

        when {
            loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = colors.accent, strokeWidth = 3.dp, modifier = Modifier.size(32.dp))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Loading calendars…", color = colors.textMuted)
                    }
                }
            }
            errorMsg != null -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                        Icon(Icons.Rounded.ErrorOutline, null, tint = colors.danger, modifier = Modifier.size(40.dp))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(errorMsg, color = colors.danger, textAlign = TextAlign.Center)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Pull to refresh or tap sync", color = colors.textMuted, fontSize = 13.sp)
                    }
                }
            }
            else -> {
                // Single unified LazyColumn — calendar + events scroll together
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 88.dp)
                ) {
                    // ── Month selector ──
                    item {
                        if (allMonths.isNotEmpty()) {
                            LazyRow(
                                modifier = Modifier.fillMaxWidth().padding(vertical = spacing.sm),
                                horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                                contentPadding = PaddingValues(horizontal = spacing.pageHorizontal)
                            ) {
                                items(allMonths.indices.toList()) { idx ->
                                    val month = allMonths[idx]
                                    val isSelected = selectedMonthIdx == idx
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(radius.extraLarge))
                                            .background(if (isSelected) colors.accent else colors.surface)
                                            .clickable { selectedMonthIdx = idx; selectedDay = null }
                                            .padding(horizontal = spacing.md, vertical = spacing.sm)
                                    ) {
                                        Text(
                                            text = monthDisplayName(month.month),
                                            style = AmazeTheme.typography.body.copy(
                                                color = if (isSelected) colors.background else colors.textPrimary,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                            )
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
                                modifier = Modifier.fillMaxWidth().padding(horizontal = spacing.pageHorizontal, vertical = spacing.xs),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat").forEach { d ->
                                    Text(
                                        d, modifier = Modifier.weight(1f),
                                        textAlign = TextAlign.Center,
                                        style = AmazeTheme.typography.smallLabel.copy(color = colors.textMuted)
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
                                                val isToday = dayNumber == now.dayOfMonth &&
                                                        monthNumber == now.monthNumber &&
                                                        gridYearNum == todayYearNum
                                                val isSelected = selectedDay == dayNumber
                                                val d = dayNumber
                                                Box(
                                                    modifier = Modifier
                                                        .weight(1f).aspectRatio(1f).padding(3.dp)
                                                        .clip(RoundedCornerShape(radius.xs))
                                                        .background(
                                                            when {
                                                                isSelected -> colors.accent.copy(alpha = 0.25f)
                                                                isToday -> colors.accent.copy(alpha = 0.1f)
                                                                else -> Color.Transparent
                                                            }
                                                        )
                                                        .clickable { selectedDay = if (selectedDay == d) null else d },
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                        Text(
                                                            dayNumber.toString(),
                                                            style = AmazeTheme.typography.body.copy(
                                                                color = when {
                                                                    isSelected -> colors.accent
                                                                    isToday -> colors.accent
                                                                    else -> colors.textPrimary
                                                                },
                                                                fontWeight = if (isToday || isSelected || dayEvents.isNotEmpty()) FontWeight.Bold else FontWeight.Normal
                                                            )
                                                        )
                                                        if (dayEvents.isNotEmpty()) {
                                                            Row(
                                                                horizontalArrangement = Arrangement.spacedBy(2.dp),
                                                                modifier = Modifier.padding(top = 2.dp)
                                                            ) {
                                                                dayEvents.take(3).forEach { ev ->
                                                                    Box(modifier = Modifier.size(4.dp).clip(CircleShape).background(ev.color))
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
                        val dayLabel = if (selectedDay != null) {
                            "${monthDisplayName(activeMonth?.month ?: "")} $selectedDay"
                        } else {
                            monthDisplayName(activeMonth?.month ?: "")
                        }
                        Text(
                            text = if (selectedDay != null) dayLabel else "All events — $dayLabel",
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                            style = AmazeTheme.typography.subheading.copy(
                                fontWeight = FontWeight.Bold,
                                color = colors.textPrimary
                            )
                        )
                    }

                    // ── Events list ──
                    val eventsToShow: List<Pair<String, ConsolidatedEvent>> = if (selectedDay != null) {
                        (activeMonthEvents[selectedDay] ?: emptyList()).map { "" to it }
                    } else {
                        activeMonthEvents.keys.sorted().flatMap { dayNum ->
                            val dayLabel = "${monthDisplayName(activeMonth?.month ?: "")} $dayNum"
                            (activeMonthEvents[dayNum] ?: emptyList()).mapIndexed { i, ev ->
                                (if (i == 0) dayLabel else "") to ev
                            }
                        }
                    }

                    if (eventsToShow.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Rounded.CalendarToday, null, tint = colors.textMuted, modifier = Modifier.size(36.dp))
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text("No events${if (selectedDay != null) " on this day" else " this month"}", color = colors.textMuted)
                                }
                            }
                        }
                    } else {
                        items(eventsToShow) { (dateLabel, ev) ->
                            if (dateLabel.isNotEmpty()) {
                                Text(
                                    text = dateLabel,
                                    modifier = Modifier.padding(horizontal = spacing.pageHorizontal, vertical = spacing.xs),
                                    style = AmazeTheme.typography.body.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = colors.textSecondary
                                    )
                                )
                            }
                            AmazeCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp)) {
                                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Box(modifier = Modifier.width(4.dp).height(36.dp).background(ev.color, RoundedCornerShape(2.dp)))
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(ev.title, style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.SemiBold, color = colors.textPrimary))
                                        if (ev.timeOrLocation.isNotBlank()) {
                                            Text(ev.timeOrLocation, style = AmazeTheme.typography.caption.copy(color = colors.textSecondary))
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    CalendarBadge(ev.type, ev.color.copy(alpha = 0.18f), ev.color)
                                }
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
            .clip(RoundedCornerShape(AmazeTheme.radius.xs))
            .background(backgroundColor)
            .padding(horizontal = AmazeTheme.spacing.xs, vertical = AmazeTheme.spacing.xs)
    ) {
        Text(text = text, style = AmazeTheme.typography.smallLabel.copy(fontWeight = FontWeight.Bold, color = textColor))
    }
}
