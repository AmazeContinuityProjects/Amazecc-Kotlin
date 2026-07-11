@file:Suppress("unused", "UNUSED_VARIABLE", "UNUSED_PARAMETER", "UNUSED_IMPORT")
package com.amazecc.app.shared.ui.screens.academics

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amazecc.app.shared.model.AttendanceItem
import com.amazecc.app.shared.state.AppState
import com.amazecc.app.shared.theme.AmazeTheme
import com.amazecc.app.shared.ui.components.AmazeCard
import com.amazecc.app.shared.ui.components.ScreenHeader

@Composable
fun AttendanceScreen() {
    val colors = AmazeTheme.colors
    var activeSubTab by remember { mutableStateOf("Timeline") }
    val tabs = listOf("Timeline", "Predictor", "Timetable Grid")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        ScreenHeader(
            title = "Attendance Hub",
            description = "Track your attendance, view timelines and predict shortfalls",
            showBackButton = true,
            showSyncButton = true
        )

        TabRow(
            selectedTabIndex = tabs.indexOf(activeSubTab),
            containerColor = colors.background,
            contentColor = colors.accent,
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[tabs.indexOf(activeSubTab)]),
                    color = colors.accent
                )
            }
        ) {
            tabs.forEach { tab ->
                Tab(
                    selected = activeSubTab == tab,
                    onClick = { activeSubTab = tab },
                    text = {
                        Text(
                            text = tab,
                            style = AmazeTheme.typography.body.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        )
                    },
                    selectedContentColor = colors.accent,
                    unselectedContentColor = colors.textSecondary
                )
            }
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .padding(16.dp)
        ) {
            when (activeSubTab) {
                "Timeline" -> AttendanceTimelineScreen()
                "Predictor" -> OverallPredictorScreen()
                "Timetable Grid" -> TimetableGridScreen()
            }
        }
    }
}

@Composable
fun AttendanceTimelineScreen() {
    val colors = AmazeTheme.colors
    val attendanceRes by AppState.attendance.collectAsState()
    val courses = attendanceRes?.attendance ?: emptyList()
    
    val days = listOf("MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN")
    var selectedDay by remember { mutableStateOf("MON") }

    // Simulated Skip States
    var simulatedSkips by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }

    Column(modifier = Modifier.fillMaxSize()) {
        // Horizontal Day Selector
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(days) { day ->
                val isSelected = selectedDay == day
                Box(
                    modifier = Modifier
                        .background(
                            if (isSelected) colors.accent else colors.surface,
                            RoundedCornerShape(12.dp)
                        )
                        .border(1.dp, if (isSelected) colors.accent else colors.border, RoundedCornerShape(12.dp))
                        .clickable { selectedDay = day }
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = day,
                        style = AmazeTheme.typography.body.copy(
                            color = if (isSelected) Color.White else colors.textPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (courses.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No attendance data available.", color = colors.textSecondary)
            }
        } else {
            // Compute real-time global attendance
            var totalAttended = 0
            var totalClasses = 0
            courses.forEach { course ->
                val code = course.courseCode
                val skipCount = simulatedSkips[code] ?: 0
                val attended = (course.attendedClasses ?: 0) - skipCount
                val total = course.totalClasses ?: 0
                totalAttended += attended
                totalClasses += total
            }
            val totalPerc = if (totalClasses > 0) (totalAttended.toFloat() / totalClasses) * 100f else 0f
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Simulated Overall:",
                    style = AmazeTheme.typography.subheading.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary)
                )
                Text(
                    "%",
                    style = AmazeTheme.typography.subheading.copy(
                        fontWeight = FontWeight.Bold, 
                        color = if (totalPerc >= 75f) colors.success else colors.danger
                    )
                )
            }
            Spacer(modifier = Modifier.height(12.dp))

            // Timetable mock logic: For simplicity, we just filter courses that MIGHT have slots on that day,
            // or just display a mocked layout showing free gaps & lunch.
            // In a real port, we map slotMap or TimeTable API. 
            // Here, we just display the available courses as if they are on the selected day.
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxSize()) {
                
                // Mock free period
                item {
                    FreePeriodBlock(title = "Morning Prep", time = "08:00 AM - 08:30 AM")
                }

                items(courses) { course ->
                    val skipCount = simulatedSkips[course.courseCode] ?: 0
                    val attended = (course.attendedClasses ?: 0) - skipCount
                    val total = course.totalClasses ?: 0
                    val perc = if (total > 0) (attended.toFloat() / total) * 100f else 0f
                    
                    ClassBlock(
                        course = course,
                        simulatedAttended = attended,
                        simulatedTotal = total,
                        simulatedPerc = perc,
                        onSkipChange = { change ->
                            val newSkip = (skipCount + change).coerceAtLeast(0).coerceAtMost(course.attendedClasses ?: 0)
                            simulatedSkips = simulatedSkips.toMutableMap().apply { put(course.courseCode, newSkip) }
                        }
                    )
                }

                item {
                    FreePeriodBlock(title = "Lunch Break", time = "01:20 PM - 02:00 PM")
                }
                
                item {
                    FreePeriodBlock(title = "Free Period", time = "03:00 PM - 04:00 PM")
                }
            }
        }
    }
}

@Composable
fun ClassBlock(
    course: AttendanceItem,
    simulatedAttended: Int,
    simulatedTotal: Int,
    simulatedPerc: Float,
    onSkipChange: (Int) -> Unit
) {
    val colors = AmazeTheme.colors
    val isCritical = simulatedPerc < 75f

    AmazeCard(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(
                modifier = Modifier
                    .width(60.dp)
                    .padding(end = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    course.slotName.split("+").firstOrNull() ?: "-", 
                    style = AmazeTheme.typography.subheading.copy(fontWeight = FontWeight.Bold, color = colors.accent)
                )
            }
            
            Box(modifier = Modifier.width(2.dp).height(60.dp).background(colors.border))
            
            Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                Text(
                    course.courseTitle,
                    style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "Venue: ",
                    style = AmazeTheme.typography.caption.copy(color = colors.textSecondary)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "Att:  /  (%)",
                        style = AmazeTheme.typography.smallLabel.copy(
                            color = if (isCritical) colors.danger else colors.success,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        IconButton(onClick = { onSkipChange(-1) }, modifier = Modifier.size(24.dp).background(colors.surface, CircleShape)) {
                            Icon(Icons.Rounded.KeyboardArrowUp, contentDescription = "Restore", tint = colors.success, modifier = Modifier.size(16.dp))
                        }
                        IconButton(onClick = { onSkipChange(1) }, modifier = Modifier.size(24.dp).background(colors.surface, CircleShape)) {
                            Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Skip", tint = colors.danger, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FreePeriodBlock(title: String, time: String) {
    val colors = AmazeTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface, RoundedCornerShape(12.dp))
            .border(1.dp, colors.border, RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Rounded.Info, contentDescription = null, tint = colors.textMuted, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = " ()",
            style = AmazeTheme.typography.caption.copy(fontWeight = FontWeight.Medium, color = colors.textMuted)
        )
    }
}

@Composable
fun OverallPredictorScreen() {
    val colors = AmazeTheme.colors
    var selectedMode by remember { mutableStateOf("CAT1") }
    
    Column(modifier = Modifier.fillMaxSize()) {
        Text("Select Cutoff Target", style = AmazeTheme.typography.subheading.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
        Spacer(modifier = Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            val modes = listOf("CAT1", "CAT2", "LID")
            modes.forEach { mode ->
                val isSelected = selectedMode == mode
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(if (isSelected) colors.accent else colors.surface, RoundedCornerShape(8.dp))
                        .clickable { selectedMode = mode }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(mode, color = if (isSelected) Color.White else colors.textPrimary, fontWeight = FontWeight.Bold)
                }
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        AmazeCard(modifier = Modifier.fillMaxWidth()) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text("Predicted Overall Attendance", style = AmazeTheme.typography.body.copy(color = colors.textSecondary))
                Spacer(modifier = Modifier.height(8.dp))
                Text("84.5%", style = AmazeTheme.typography.heading.copy(color = colors.accent, fontWeight = FontWeight.Black, fontSize = 48.sp))
                Spacer(modifier = Modifier.height(8.dp))
                Text("Assuming 100% presence from today until .", style = AmazeTheme.typography.caption.copy(color = colors.textMuted))
            }
        }
    }
}

@Composable
fun TimetableGridScreen() {
    val colors = AmazeTheme.colors
    val attendanceRes by AppState.attendance.collectAsState()
    val courses = attendanceRes?.attendance ?: emptyList()
    
    LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            AmazeCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    "Weekly Matrix View", 
                    style = AmazeTheme.typography.subheading.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Matrix view is optimized for large screens or horizontal orientation. Here are your listed courses:",
                    style = AmazeTheme.typography.caption.copy(color = colors.textSecondary)
                )
            }
        }
        
        item {
            Text("Course Reference", style = AmazeTheme.typography.subheading.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
        }
        
        items(courses) { course ->
            AmazeCard(modifier = Modifier.fillMaxWidth()) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .background(colors.accent, RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(course.courseCode, style = AmazeTheme.typography.smallLabel.copy(color = Color.White, fontWeight = FontWeight.Bold))
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(course.courseTitle, style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Faculty: ", style = AmazeTheme.typography.caption.copy(color = colors.textSecondary))
                    Text("Slots:  | Venue: ", style = AmazeTheme.typography.caption.copy(color = colors.textPrimary, fontWeight = FontWeight.Medium))
                }
            }
        }
    }
}
