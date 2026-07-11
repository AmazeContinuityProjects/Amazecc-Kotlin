package com.amazecc.app.shared.ui.screens.academics

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.ui.graphics.graphicsLayer
import com.amazecc.app.shared.api.AmazeClient
import com.amazecc.app.shared.model.*
import com.amazecc.app.shared.repository.SessionManager
import com.amazecc.app.shared.state.AppState
import com.amazecc.app.shared.state.Screen
import com.amazecc.app.shared.theme.AccentTheme
import com.amazecc.app.shared.theme.AmazeTheme
import com.amazecc.app.shared.theme.AppTheme
import com.amazecc.app.shared.ui.components.*
import kotlinx.coroutines.launch
import kotlinx.serialization.json.decodeFromJsonElement


@Composable
fun AttendanceScreen() = AcademicsScreen(initialTab = "Attendance")

@Composable
fun MarksGradesScreen() = AcademicsScreen(initialTab = "Marks & GPA")


@Composable
fun AcademicsScreen(initialTab: String = "Attendance") {
    val colors = AmazeTheme.colors
    var activeSubTab by remember(initialTab) { mutableStateOf(initialTab) }
    val tabs = listOf("Attendance", "Marks & GPA", "Schedule", "Calendar", "Question Bank")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        ScreenHeader(
            title = "Academics Hub",
            description = "Track classes, grades & schedules",
            showBackButton = false,
            showSyncButton = true
        )

        // Sub-Tab Navigation row
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
                "Attendance" -> AttendanceSubScreen()
                "Marks & GPA" -> MarksSubScreen()
                "Schedule" -> TimetableSubScreen()
                "Calendar" -> CalendarSubScreen()
                "Question Bank" -> QBankSubScreen()
            }
        }
    }
}

// ── 2. UNIFIED SERVICES SCREEN (TABS: PAYMENTS, LIBRARY, TRANSPORT, LMS) ──


@Composable
fun AttendanceSubScreen() {
    val colors = AmazeTheme.colors
    val attendanceRes by AppState.attendance.collectAsState()
    val courses = attendanceRes?.attendance ?: emptyList()

    if (courses.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No attendance data found. Tap refresh to sync.", color = colors.textSecondary)
        }
    } else {
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(courses) { course ->
                AttendanceCardItem(course)
            }
        }
    }
}

@Composable
fun AttendanceCardItem(course: AttendanceItem) {
    val colors = AmazeTheme.colors
    val percentage = course.attendancePercentage?.toDoubleOrNull()
    val badgeVariant = when {
        percentage == null -> BadgeVariant.INFO
        percentage >= 85.0 -> BadgeVariant.SUCCESS
        percentage >= 75.0 -> BadgeVariant.WARNING
        else -> BadgeVariant.DANGER
    }

    AmazeCard(modifier = Modifier.fillMaxWidth()) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(course.courseCode, style = AmazeTheme.typography.smallLabel.copy(color = colors.textMuted))
                    Text(course.courseTitle, style = AmazeTheme.typography.subheading.copy(color = colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp))
                    Text("Faculty: ${course.faculty ?: "—"}", style = AmazeTheme.typography.caption.copy(color = colors.textSecondary))
                }
                AmazeBadge(
                    text = course.attendancePercentage?.let { "$it%" } ?: "—",
                    variant = badgeVariant
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = colors.border)
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Classes: ${course.attendedClasses ?: 0}/${course.totalClasses ?: 0}",
                    style = AmazeTheme.typography.caption.copy(color = colors.textSecondary)
                )
                Text(
                    text = "Slot / Venue: ${course.slotName} / ${course.slotVenue ?: "—"}",
                    style = AmazeTheme.typography.caption.copy(color = colors.accent, fontWeight = FontWeight.Bold)
                )
            }
        }
    }
}

@Composable
fun MarksSubScreen() {
    val colors = AmazeTheme.colors
    val marksRes by AppState.marks.collectAsState()
    val allGradesRes by AppState.allGrades.collectAsState()
    
    val courses = marksRes?.marks ?: emptyList()
    val gpaRecords = allGradesRes?.grades ?: emptyMap()

    var activeViewTab by remember { mutableStateOf("Internal Marks") }
    
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AmazeButton(
                text = "Internal Marks",
                onClick = { activeViewTab = "Internal Marks" },
                variant = if (activeViewTab == "Internal Marks") ButtonVariant.PRIMARY else ButtonVariant.SECONDARY,
                modifier = Modifier.weight(1f)
            )
            AmazeButton(
                text = "Grade History",
                onClick = { activeViewTab = "Grade History" },
                variant = if (activeViewTab == "Grade History") ButtonVariant.PRIMARY else ButtonVariant.SECONDARY,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (activeViewTab == "Internal Marks") {
            if (courses.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    Text("No internal marks records.", color = colors.textSecondary)
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(courses) { course ->
                        AmazeCard(modifier = Modifier.fillMaxWidth()) {
                            Column {
                                Text(course.courseCode, style = AmazeTheme.typography.smallLabel.copy(color = colors.textMuted))
                                Text(course.courseTitle, style = AmazeTheme.typography.subheading.copy(color = colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp))
                                Text("Faculty: ${course.faculty}", style = AmazeTheme.typography.caption.copy(color = colors.textSecondary))
                                
                                Spacer(modifier = Modifier.height(12.dp))
                                HorizontalDivider(color = colors.border)
                                Spacer(modifier = Modifier.height(12.dp))

                                course.assessments.forEach { assess ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(assess.title, style = AmazeTheme.typography.body.copy(fontSize = 14.sp, color = colors.textPrimary))
                                            Text("Weightage: ${assess.weightagePercent}%", style = AmazeTheme.typography.caption.copy(color = colors.textMuted))
                                        }
                                        Text("${assess.scoredMark} / ${assess.maxMark}", style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else {
            if (gpaRecords.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    Text("No GPA & grade history records.", color = colors.textSecondary)
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    gpaRecords.forEach { (semId, semResult) ->
                        item {
                            AmazeCard(modifier = Modifier.fillMaxWidth()) {
                                Column {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(semId, style = AmazeTheme.typography.subheading.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
                                        Text("GPA: ${semResult?.gpa ?: "-"}", style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Black, color = colors.accent))
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    semResult?.grades?.forEach { grade ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 4.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text("${grade.courseCode} - ${grade.courseTitle}", style = AmazeTheme.typography.caption.copy(color = colors.textSecondary), modifier = Modifier.weight(1f))
                                            Text("Grade: ${grade.grade}", style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
                                        }
                                    }
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
fun TimetableSubScreen() {
    val colors = AmazeTheme.colors
    val timetableRes by AppState.timetable.collectAsState()
    val attendanceRes by AppState.attendance.collectAsState()
    
    val timetableCourses = timetableRes?.courseInfo ?: emptyList()
    val attendanceCourses = attendanceRes?.attendance ?: emptyList()

    var scale by remember { mutableStateOf(1f) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTransformGestures { _, _, zoom, _ ->
                    scale = (scale * zoom).coerceIn(0.5f, 3f)
                }
            }
            .graphicsLayer(
                scaleX = scale,
                scaleY = scale
            )
    ) {
        if (timetableCourses.isEmpty() && attendanceCourses.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No timetable data found. Tap refresh to sync.", color = colors.textSecondary)
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                if (timetableCourses.isNotEmpty()) {
                    items(timetableCourses) { course ->
                        AmazeCard(modifier = Modifier.fillMaxWidth()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background(colors.accent.copy(alpha = 0.1f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(course.slNo, style = AmazeTheme.typography.caption.copy(fontWeight = FontWeight.Bold, color = colors.accent))
                                }
                                Spacer(modifier = Modifier.width(16.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(course.courseCode, style = AmazeTheme.typography.smallLabel.copy(color = colors.textMuted))
                                    Text(course.course, style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
                                    Text("Faculty: ${course.facultyDetails ?: "—"}", style = AmazeTheme.typography.caption.copy(color = colors.textSecondary))
                                    Text("Slot / Venue: ${course.slotVenue ?: "—"}", style = AmazeTheme.typography.caption.copy(fontWeight = FontWeight.Bold, color = colors.accent))
                                }
                            }
                        }
                    }
                } else {
                    items(attendanceCourses) { course ->
                        AmazeCard(modifier = Modifier.fillMaxWidth()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background(colors.accent.copy(alpha = 0.1f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(course.slotName, style = AmazeTheme.typography.caption.copy(fontWeight = FontWeight.Bold, color = colors.accent))
                                }
                                Spacer(modifier = Modifier.width(16.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(course.courseCode, style = AmazeTheme.typography.smallLabel.copy(color = colors.textMuted))
                                    Text(course.courseTitle, style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
                                    Text("Faculty: ${course.faculty ?: "—"}", style = AmazeTheme.typography.caption.copy(color = colors.textSecondary))
                                    Text("Venue: ${course.slotVenue ?: "—"}", style = AmazeTheme.typography.caption.copy(fontWeight = FontWeight.Bold, color = colors.accent))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── 3. HOSTEL SCREEN (Bed / Room portal) ──


@Composable
fun CalendarScreen() = AcademicsScreen(initialTab = "Calendar")

@Composable
fun QBankScreen() = AcademicsScreen(initialTab = "Question Bank")


@Composable
fun CalendarSubScreen() {
    val colors = AmazeTheme.colors
    val calendarRes by AppState.calendar.collectAsState()
    val months = calendarRes?.months ?: emptyList()

    if (months.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No calendar events found.", color = colors.textSecondary)
        }
    } else {
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(months) { monthData ->
                AmazeCard(modifier = Modifier.fillMaxWidth()) {
                    Column {
                        Text(monthData.month, style = AmazeTheme.typography.subheading.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        monthData.days.forEach { dayData ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .background(colors.accent.copy(alpha = 0.1f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(dayData.date.toString(), style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.accent))
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    dayData.events.forEach { event ->
                                        Text(event.type, style = AmazeTheme.typography.smallLabel.copy(color = colors.textMuted))
                                        Text(event.text, style = AmazeTheme.typography.body.copy(color = colors.textPrimary))
                                    }
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
fun QBankSubScreen() {
    val colors = AmazeTheme.colors
    // Minimal mock UI since QBank requires course search
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Rounded.Search, contentDescription = null, modifier = Modifier.size(64.dp), tint = colors.textMuted)
            Spacer(modifier = Modifier.height(16.dp))
            Text("Search for a course to view previous papers", color = colors.textSecondary)
            Spacer(modifier = Modifier.height(8.dp))
            AmazeButton(text = "Search Course", onClick = { /* TODO */ })
        }
    }
}


