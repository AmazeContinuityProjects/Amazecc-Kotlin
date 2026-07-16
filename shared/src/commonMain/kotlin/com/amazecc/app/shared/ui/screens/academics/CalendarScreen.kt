package com.amazecc.app.shared.ui.screens.academics

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material.icons.automirrored.rounded.ViewList
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
import com.amazecc.app.shared.model.CalendarRes
import com.amazecc.app.shared.model.NamedCalendar
import com.amazecc.app.shared.repository.SettingsManager
import com.amazecc.app.shared.state.AppState
import com.amazecc.app.shared.theme.AmazeTheme
import com.amazecc.app.shared.ui.components.AmazeCard
import com.amazecc.app.shared.ui.components.MoodleLoginModal
import com.amazecc.app.shared.ui.components.ScreenHeader
import com.amazecc.app.shared.utils.IcsUtils
import com.amazecc.app.shared.utils.ShareIcsButton
import kotlinx.datetime.Clock
import kotlinx.datetime.DayOfWeek
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

@Composable
fun CalendarScreen(@Suppress("UNUSED_PARAMETER") onBack: () -> Unit) {
    val colors = AmazeTheme.colors
    val moodleData by AppState.moodleData.collectAsState()
    val examData by AppState.examSchedule.collectAsState()
    val selectedSemester by AppState.selectedSemester.collectAsState()

    var showMoodleModal by remember { mutableStateOf(false) }
    var viewMode by remember { mutableStateOf("Grid") }

    // ── Calendar list from API ──
    var loading by remember { mutableStateOf(true) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var calendars by remember { mutableStateOf<List<NamedCalendar>>(emptyList()) }
    var selectedCalIdx by remember { mutableStateOf(0) }
    var refreshTrigger by remember { mutableStateOf(0) }

    LaunchedEffect(selectedSemester, refreshTrigger) {
        loading = true
        errorMsg = null
        try {
            val res = AmazeClient.getCalendars(semesterId = selectedSemester)
            if (res.success && res.calendars.isNotEmpty()) {
                calendars = res.calendars
            } else {
                errorMsg = res.message ?: "No calendars available"
            }
        } catch (_: Exception) {
            errorMsg = "Network error"
        }
        loading = false
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
        val dt = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        dt
    }
    val todayMonthStr = remember(now) {
        listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")[now.monthNumber - 1]
    }
    val todayYearNum = now.year

    val activeCalendar = calendars.getOrNull(selectedCalIdx)
    val allMonths = activeCalendar?.months ?: emptyList()

    var selectedMonthIdx by remember { mutableStateOf(0) }
    LaunchedEffect(allMonths) {
        if (allMonths.isNotEmpty() && selectedMonthIdx == 0) {
            val idx = allMonths.indexOfFirst { it.month.lowercase().startsWith(todayMonthStr.lowercase()) }
            if (idx != -1) selectedMonthIdx = idx
        }
    }

    val activeMonth = allMonths.getOrNull(selectedMonthIdx)

    val activeMonthEvents = remember(activeMonth, moodleData, examData) {
        val map = mutableMapOf<Int, MutableList<ConsolidatedEvent>>()
        if (activeMonth == null) return@remember map

        val monthStr = activeMonth.month.lowercase()
        val monthNum = when {
            monthStr.startsWith("jan") -> 1; monthStr.startsWith("feb") -> 2
            monthStr.startsWith("mar") -> 3; monthStr.startsWith("apr") -> 4
            monthStr.startsWith("may") -> 5; monthStr.startsWith("jun") -> 6
            monthStr.startsWith("jul") -> 7; monthStr.startsWith("aug") -> 8
            monthStr.startsWith("sep") -> 9; monthStr.startsWith("oct") -> 10
            monthStr.startsWith("nov") -> 11; monthStr.startsWith("dec") -> 12
            else -> 1
        }
        val yearNum = monthStr.split(" ").lastOrNull()?.toIntOrNull() ?: todayYearNum

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
                    val y = parts[0].toInt(); val mNum = parts[1].toInt(); val dNum = parts[2].substring(0, 2).toInt()
                    if (y == yearNum && mNum == monthNum && !m.done && !m.hidden) {
                        val list = map.getOrPut(dNum) { mutableListOf() }
                        list.add(ConsolidatedEvent(m.name, "Moodle", "Due: ", Color(0xFF9C27B0)))
                    }
                }
            } catch (_: Exception) {}
        }

        examData?.schedule?.forEach { (type, exams) ->
            exams.forEach { ex ->
                try {
                    val parts = ex.examDate.split("-")
                    if (parts.size >= 3) {
                        val dNum = parts[0].toInt()
                        val list = map.getOrPut(dNum) { mutableListOf() }
                        list.add(ConsolidatedEvent("${ex.courseCode} ($type)", "Exam", "${ex.examTime} - ${ex.venue}", Color(0xFFFF9800)))
                    }
                } catch (_: Exception) {}
            }
        }

        map
    }

    var selectedDay by remember { mutableStateOf<Int?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        ScreenHeader(
            title = "Academic Calendar",
            description = "Track schedule, exams, & LMS",
            showBackButton = true,
            showSyncButton = true,
            onRefresh = { refreshTrigger++ }
        )

        // ── Calendar name tabs from API ──
        if (calendars.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                calendars.forEachIndexed { idx, cal ->
                    val isSelected = selectedCalIdx == idx
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isSelected) colors.accent else colors.surface)
                            .clickable { selectedCalIdx = idx; selectedDay = null; selectedMonthIdx = 0 }
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = cal.name,
                            style = AmazeTheme.typography.body.copy(fontSize = 13.sp)
                                .copy(color = if (isSelected) colors.background else colors.textSecondary, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                        )
                    }
                }
            }
        }

        // ── Action buttons row ──
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AmazeCard(
                    modifier = Modifier.clickable { showMoodleModal = true },
                    backgroundColor = colors.accent.copy(alpha = 0.1f)
                ) {
                    Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.CloudSync, contentDescription = null, tint = colors.accent, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Sync LMS", color = colors.accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
                AmazeCard(modifier = Modifier.clickable { }, backgroundColor = colors.surface) {
                    Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Download, contentDescription = null, tint = colors.textPrimary, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Export ICS", color = colors.textPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Row(
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(colors.surface),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .clickable { viewMode = "Grid" }
                        .background(if (viewMode == "Grid") colors.accent else Color.Transparent)
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Rounded.GridView, contentDescription = null, modifier = Modifier.size(18.dp), tint = if (viewMode == "Grid") colors.surface else colors.textSecondary)
                }
                Box(
                    modifier = Modifier
                        .clickable { viewMode = "List" }
                        .background(if (viewMode == "List") colors.accent else Color.Transparent)
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.AutoMirrored.Rounded.ViewList, contentDescription = null, modifier = Modifier.size(18.dp), tint = if (viewMode == "List") colors.surface else colors.textSecondary)
                }
            }
        }

        // ── Content ──
        when {
            loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = colors.accent, strokeWidth = 3.dp, modifier = Modifier.size(32.dp))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Loading calendars...", color = colors.textMuted)
                    }
                }
            }
            errorMsg != null -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Rounded.ErrorOutline, contentDescription = null, tint = colors.danger, modifier = Modifier.size(36.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(errorMsg!!, color = colors.danger)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Pull to refresh or tap sync", color = colors.textSecondary)
                    }
                }
            }
            allMonths.isEmpty() -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No events for ${activeCalendar?.name ?: ""}.", color = colors.textMuted)
                }
            }
            else -> {
                // Month selector
                LazyRow(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp)
                ) {
                    items(allMonths.indices.toList()) { idx ->
                        val month = allMonths[idx]
                        val isSelected = selectedMonthIdx == idx
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (isSelected) colors.accent else colors.surface)
                                .clickable { selectedMonthIdx = idx; selectedDay = null }
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = month.month,
                                color = if (isSelected) colors.surface else colors.textPrimary,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }

                if (viewMode == "Grid") {
                    // Grid calendar
                    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            listOf("S", "M", "T", "W", "T", "F", "S").forEach { d ->
                                Text(d, modifier = Modifier.weight(1f), textAlign = TextAlign.Center, color = colors.textSecondary, fontWeight = FontWeight.Bold)
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))

                        val daysInMonth = activeMonth?.days?.maxOfOrNull { it.date } ?: 31
                        val monthParts = activeMonth?.month?.split(" ") ?: emptyList()
                        val monthNumber = when (monthParts.firstOrNull()?.lowercase()?.take(3)) {
                            "jan" -> 1; "feb" -> 2; "mar" -> 3; "apr" -> 4
                            "may" -> 5; "jun" -> 6; "jul" -> 7; "aug" -> 8
                            "sep" -> 9; "oct" -> 10; "nov" -> 11; "dec" -> 12
                            else -> 1
                        }
                        val gridYearNum = monthParts.lastOrNull()?.toIntOrNull() ?: now.year
                        val startCol = if (gridYearNum > 0)
                            LocalDate(gridYearNum, monthNumber, 1).dayOfWeek.isoDayNumber % 7
                        else 0
                        var currentDay = 1
                        val totalRows = (daysInMonth + startCol + 6) / 7
                        for (row in 0 until totalRows) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                for (col in 0..6) {
                                    val dayNumber = if (row == 0 && col < startCol) 0 else currentDay
                                    if (dayNumber in 1..daysInMonth) {
                                        val dayEvents = activeMonthEvents[dayNumber] ?: emptyList()
                                        val d = dayNumber
                                        Box(
                                            modifier = Modifier
                                                .weight(1f).aspectRatio(1f).padding(2.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(if (selectedDay == dayNumber) colors.accent.copy(alpha = 0.2f) else colors.surface)
                                                .clickable { selectedDay = d },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                Text(dayNumber.toString(), color = colors.textPrimary, fontWeight = if (dayEvents.isNotEmpty()) FontWeight.Bold else FontWeight.Normal)
                                                if (dayEvents.isNotEmpty()) {
                                                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.padding(top = 2.dp)) {
                                                        dayEvents.take(3).forEach { ev -> Box(modifier = Modifier.size(4.dp).clip(CircleShape).background(ev.color)) }
                                                    }
                                                }
                                            }
                                        }
                                        if (row > 0 || col >= startCol) currentDay++
                                    } else {
                                        Spacer(modifier = Modifier.weight(1f).aspectRatio(1f).padding(2.dp))
                                    }
                                }
                            }
                        }
                    }

                    if (selectedDay != null) {
                        val events = activeMonthEvents[selectedDay!!] ?: emptyList()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("${activeMonth?.month?.split(" ")?.firstOrNull() ?: ""} $selectedDay",
                            modifier = Modifier.padding(horizontal = 16.dp),
                            style = AmazeTheme.typography.subheading.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
                        Spacer(modifier = Modifier.height(8.dp))
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (events.isEmpty()) {
                                item { Text("No events.", color = colors.textMuted) }
                            } else {
                                items(events) { ev ->
                                    AmazeCard(modifier = Modifier.fillMaxWidth()) {
                                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Box(modifier = Modifier.width(4.dp).height(32.dp).background(ev.color, RoundedCornerShape(2.dp)))
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(ev.title, style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
                                                if (ev.timeOrLocation.isNotBlank()) {
                                                    Text(ev.timeOrLocation, style = AmazeTheme.typography.caption.copy(color = colors.textSecondary))
                                                }
                                            }
                                            CalendarBadge(ev.type, ev.color.copy(alpha = 0.2f), ev.color)
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // List view
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        val daysWithEvents = activeMonthEvents.keys.sorted()
                        if (daysWithEvents.isEmpty()) {
                            item {
                                Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                                    Text("No events this month.", color = colors.textMuted)
                                }
                            }
                        } else {
                            daysWithEvents.forEach { dayNum ->
                                val events = activeMonthEvents[dayNum]!!
                                item {
                                    Text("${activeMonth?.month?.split(" ")?.firstOrNull() ?: ""} $dayNum",
                                        style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary),
                                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
                                }
                                items(events) { ev ->
                                    AmazeCard(modifier = Modifier.fillMaxWidth()) {
                                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Box(modifier = Modifier.width(4.dp).height(32.dp).background(ev.color, RoundedCornerShape(2.dp)))
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(ev.title, style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
                                                if (ev.timeOrLocation.isNotBlank()) {
                                                    Text(ev.timeOrLocation, style = AmazeTheme.typography.caption.copy(color = colors.textSecondary))
                                                }
                                            }
                                            CalendarBadge(ev.type, ev.color.copy(alpha = 0.2f), ev.color)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (allMonths.isNotEmpty()) {
            val icsString = remember(activeCalendar) { IcsUtils.generateIcs(CalendarRes(success = true, months = activeCalendar?.months ?: emptyList())) }
            ShareIcsButton(icsContent = icsString)
        }
    }
}

@Composable
private fun CalendarBadge(text: String, backgroundColor: Color, textColor: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(backgroundColor)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(text = text, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = textColor)
    }
}
