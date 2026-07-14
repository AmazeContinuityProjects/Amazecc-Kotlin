package com.amazecc.app.shared.ui.screens.academics

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import com.amazecc.app.shared.model.CalendarMonth
import com.amazecc.app.shared.state.AppState
import com.amazecc.app.shared.theme.AmazeTheme
import com.amazecc.app.shared.ui.components.AmazeCard
import com.amazecc.app.shared.ui.components.MoodleLoginModal
import com.amazecc.app.shared.ui.components.ScreenHeader
import com.amazecc.app.shared.utils.IcsUtils
import com.amazecc.app.shared.utils.ShareIcsButton

data class ConsolidatedEvent(
    val title: String,
    val type: String, // "Exam", "Moodle", "Holiday", "Class", "Missed"
    val timeOrLocation: String = "",
    val color: Color
)

@Composable
fun CalendarScreen(onBack: () -> Unit) {
    val colors = AmazeTheme.colors
    val calendarRes by AppState.calendar.collectAsState()
    val moodleData by AppState.moodleData.collectAsState()
    val examData by AppState.examSchedule.collectAsState()

    var showMoodleModal by remember { mutableStateOf(false) }
    var viewMode by remember { mutableStateOf("Grid") } // "Grid" or "List"

    if (showMoodleModal) {
        MoodleLoginModal(
            onDismiss = { showMoodleModal = false },
            onLogin = { user, pass ->
                val res = AmazeClient.fetchMoodleData(user, pass)
                if (res.success) {
                    AppState.updateMoodleData(res)
                    true
                } else false
            }
        )
    }

    // For now, defaulting today's month/year
    val todayMonthStr = "Jul"
    val todayYearNum = 2026
    
    val allMonths = calendarRes?.months ?: emptyList()
    var selectedMonthIdx by remember { mutableStateOf(0) }

    // If there are months, try to select current month initially
    LaunchedEffect(allMonths) {
        if (allMonths.isNotEmpty() && selectedMonthIdx == 0) {
            val idx = allMonths.indexOfFirst { it.month.lowercase().startsWith(todayMonthStr.lowercase()) }
            if (idx != -1) selectedMonthIdx = idx
        }
    }

    val activeMonth = allMonths.getOrNull(selectedMonthIdx)

    // Build consolidated events for active month
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

        // 1. Calendar API Events
        activeMonth.days.forEach { day ->
            val list = map.getOrPut(day.date) { mutableListOf() }
            day.events.forEach { ev ->
                val col = if (ev.text.contains("Holiday", true)) Color.Red else colors.accent
                list.add(ConsolidatedEvent(ev.text, "Holiday", ev.category ?: "", col))
            }
        }

        // 2. Moodle
        moodleData?.data?.forEach { m ->
            try {
                // simple date parse if format is "YYYY-MM-DD" or similar
                val parts = m.due.split("-", "T", " ")
                if (parts.size >= 3) {
                    val y = parts[0].toInt(); val mNum = parts[1].toInt(); val dNum = parts[2].substring(0, 2).toInt()
                    if (y == yearNum && mNum == monthNum && !m.done && !m.hidden) {
                        val list = map.getOrPut(dNum) { mutableListOf() }
                        list.add(ConsolidatedEvent(m.name, "Moodle", "Due: ", Color(0xFF9C27B0)))
                    }
                }
            } catch (e: Exception) {}
        }

        // 3. Exams
        examData?.schedule?.forEach { (type, exams) ->
            exams.forEach { ex ->
                try {
                    val parts = ex.examDate.split("-")
                    if (parts.size >= 3) {
                        val dNum = parts[0].toInt()
                        // Month format might be "Oct" or "10"
                        val list = map.getOrPut(dNum) { mutableListOf() }
                        list.add(ConsolidatedEvent("${ex.courseCode} ($type)", "Exam", "${ex.examTime} - ${ex.venue}", Color(0xFFFF9800)))
                    }
                } catch (e: Exception) {}
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
            
            showSyncButton = false
        )

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
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
                AmazeCard(
                    modifier = Modifier.clickable { /* TODO ICS Download logic using FileSystem KMP if needed */ },
                    backgroundColor = colors.surface
                ) {
                    Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Download, contentDescription = null, tint = colors.textPrimary, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Export ICS", color = colors.textPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // View Toggle
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

        if (allMonths.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Loading calendar...", color = colors.textMuted)
            }
        } else {
            // Month Selector
            LazyRow(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
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
                // Grid View
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    // Days of week header
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        listOf("S", "M", "T", "W", "T", "F", "S").forEach { d ->
                            Text(d, modifier = Modifier.weight(1f), textAlign = TextAlign.Center, color = colors.textSecondary, fontWeight = FontWeight.Bold)
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))

                    // Simplified Grid - just laying out 31 days. (In a real app, calculate offset based on 1st day of month)
                    // For now, assuming starting on 0 for simplicity since API only gives date ints, not actual weekday offsets, unless calculated
                    val daysInMonth = activeMonth?.days?.maxOfOrNull { it.date } ?: 31
                    // Let's assume month starts on Tuesday (offset 2) for placeholder. Or calculate:
                    val monthStr = activeMonth?.month ?: ""
                    
                    var currentDay = 1
                    val totalCells = 35
                    
                    for (row in 0..4) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            for (col in 0..6) {
                                if (currentDay <= daysInMonth) {
                                    val dayEvents = activeMonthEvents[currentDay] ?: emptyList()
                                    val d = currentDay
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .aspectRatio(1f)
                                            .padding(2.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (selectedDay == currentDay) colors.accent.copy(alpha = 0.2f) else colors.surface)
                                            .clickable { selectedDay = d },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text(
                                                text = currentDay.toString(),
                                                color = colors.textPrimary,
                                                fontWeight = if (dayEvents.isNotEmpty()) FontWeight.Bold else FontWeight.Normal
                                            )
                                            if (dayEvents.isNotEmpty()) {
                                                Row(horizontalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.padding(top = 2.dp)) {
                                                    dayEvents.take(3).forEach { ev ->
                                                        Box(modifier = Modifier.size(4.dp).clip(CircleShape).background(ev.color))
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    currentDay++
                                } else {
                                    Spacer(modifier = Modifier.weight(1f).aspectRatio(1f).padding(2.dp))
                                }
                            }
                        }
                    }
                }
                
                // Selected Day Events
                if (selectedDay != null) {
                    val events = activeMonthEvents[selectedDay!!] ?: emptyList()
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "${activeMonth?.month?.split(" ")?.firstOrNull() ?: ""} $selectedDay",
                        modifier = Modifier.padding(horizontal = 16.dp),
                        style = AmazeTheme.typography.subheading.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary)
                    )
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
                // List View
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
                                Text(
                                    text = "${activeMonth?.month?.split(" ")?.firstOrNull() ?: ""} $dayNum",
                                    style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary),
                                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                                )
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
        
        if (calendarRes != null) {
            val icsString = remember(calendarRes) { IcsUtils.generateIcs(calendarRes!!) }
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
        Text(
            text = text,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = textColor
        )
    }
}


