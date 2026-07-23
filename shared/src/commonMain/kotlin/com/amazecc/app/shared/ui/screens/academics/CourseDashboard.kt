package com.amazecc.app.shared.ui.screens.academics

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amazecc.app.shared.model.*
import com.amazecc.app.shared.state.AppState
import com.amazecc.app.shared.theme.AmazeTheme
import com.amazecc.app.shared.ui.components.ScreenHeader

@Composable
fun CourseDashboardScreen(@Suppress("UNUSED_PARAMETER") onBack: () -> Unit) {
    val colors = AmazeTheme.colors
    val allSemesterMarks by AppState.allSemesterMarks.collectAsState()
    val allSemesterAttendance by AppState.allSemesterAttendance.collectAsState()
    val attendanceRes by AppState.attendance.collectAsState()
    val marksRes by AppState.marks.collectAsState()
    val allGrades by AppState.allGrades.collectAsState()
    val timetable by AppState.timetable.collectAsState()

    var selectedSemester by remember { mutableStateOf("All") }
    var searchQuery by remember { mutableStateOf("") }

    val semesterGroups = remember(allSemesterMarks, allSemesterAttendance, marksRes, attendanceRes, allGrades, timetable) {
        buildSemesterGroups(allSemesterMarks, allSemesterAttendance, marksRes, attendanceRes, allGrades, timetable)
    }

    val filteredGroups = remember(semesterGroups, selectedSemester, searchQuery) {
        semesterGroups.filter { group ->
            if (group.courseCode.isBlank()) return@filter false
            val semMatch = selectedSemester == "All" || group.semesterSubId == selectedSemester
            val search = searchQuery.lowercase()
            val searchMatch = search.isEmpty() ||
                    group.courseCode.lowercase().contains(search) ||
                    group.courseTitle.lowercase().contains(search)
            semMatch && searchMatch
        }.groupBy { it.semesterSubId }
    }

    val semesterIds = listOf("All") + AppState.semesterIDs.filter { id ->
        semesterGroups.any { it.semesterSubId == id }
    }

    Column(modifier = Modifier.fillMaxSize().background(colors.background)) {
        ScreenHeader(
            title = "Course Dashboard",
            description = "All courses across semesters",
            showBackButton = true,
            showSyncButton = true,
            onRefresh = AppState::refreshAllAcademic
        )

        // Search bar
        Box(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                .clip(RoundedCornerShape(12.dp)).background(colors.surface).padding(horizontal = 12.dp, vertical = 4.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Search, null, tint = colors.textMuted, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                TextField(
                    value = searchQuery, onValueChange = { searchQuery = it },
                    placeholder = { Text("Search by code or title...", color = colors.textMuted, fontSize = 13.sp) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    colors = TextFieldDefaults.colors(focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent, focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent, focusedTextColor = colors.textPrimary, unfocusedTextColor = colors.textPrimary)
                )
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(Icons.Rounded.Close, null, tint = colors.textMuted, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }

        // Semester filter chips
        Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            semesterIds.forEach { semId ->
                val isSelected = selectedSemester == semId
                val label = if (semId == "All") "All Semesters" else AppState.semesterMap[semId]?.let {
                    val parts = it.split(" ")
                    if (parts.size >= 2) "${parts[0]} ${parts[1].take(4)}" else it.take(20)
                } ?: semId.take(10)
                Box(
                    modifier = Modifier.clip(RoundedCornerShape(8.dp))
                        .background(if (isSelected) colors.accent else colors.surface)
                        .clickable { selectedSemester = semId }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(label, color = if (isSelected) colors.background else colors.textSecondary, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal, fontSize = 12.sp, maxLines = 1)
                }
            }
        }

        if (filteredGroups.keys.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Rounded.SearchOff, null, tint = colors.textMuted, modifier = Modifier.size(48.dp))
                    Spacer(Modifier.height(8.dp))
                    Text("No courses found", color = colors.textMuted)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                contentPadding = PaddingValues(bottom = 88.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                filteredGroups.forEach { (semId, courses) ->
                    item {
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = courses.firstOrNull()?.semesterName ?: AppState.semesterMap[semId] ?: semId,
                                style = AmazeTheme.typography.subheading.copy(fontWeight = FontWeight.Bold, color = colors.accent),
                                modifier = Modifier.weight(1f)
                            )
                            Text("${courses.size} courses", fontSize = 11.sp, color = colors.textMuted)
                        }
                    }
                    items(courses) { course ->
                        CourseDetailCard(
                            course = course,
                            onClick = { AppState.openCourseDetail(course.courseCode, course.semesterSubId) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CourseDetailCard(course: CourseGroup, onClick: () -> Unit) {
    val colors = AmazeTheme.colors

    val theoryAtt = course.theoryAtt
    val labAtt = course.labAtt
    val isEmbedded = (course.theory != null && course.lab != null) || (course.theoryAtt != null && course.labAtt != null)

    fun attColor(pct: Double) = when {
        pct >= 85.0 -> colors.chart1
        pct >= 75.0 -> colors.chart3
        else -> colors.chart5
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(colors.surface)
            .border(1.dp, colors.border, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(start = 16.dp, end = 8.dp, top = 14.dp, bottom = 14.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!isEmbedded) {
                    val attPct = (theoryAtt?.attendancePercentage ?: labAtt?.attendancePercentage ?: "0").toDoubleOrNull() ?: 0.0
                    val label = if (course.theory?.courseType == "Lab Only" || course.lab?.courseType == "Lab Only") "LO" else "TH"
                    val iconColor = if (label == "TH") colors.chart2 else colors.chart4
                    Box(modifier = Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).background(iconColor.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                        Text(label, style = AmazeTheme.typography.subheading.copy(color = iconColor, fontWeight = FontWeight.Black, fontSize = 16.sp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                        Text(course.courseCode, style = AmazeTheme.typography.smallLabel.copy(color = colors.textSecondary))
                        Text(course.courseTitle, style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
                    }
                    if (attPct > 0) {
                        val c = attColor(attPct)
                        Box(modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(c.copy(alpha = 0.12f)).border(1.dp, c.copy(alpha = 0.3f), RoundedCornerShape(10.dp)).padding(horizontal = 12.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
                            Text("${attPct.toInt()}%", style = AmazeTheme.typography.body.copy(color = c, fontWeight = FontWeight.Black, fontSize = 15.sp))
                        }
                    }
                } else {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(course.courseCode, style = AmazeTheme.typography.smallLabel.copy(color = colors.textSecondary))
                        Text(course.courseTitle, style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
                    }
                }
                Spacer(Modifier.width(4.dp))
                Icon(Icons.Rounded.ChevronRight, null, tint = colors.textMuted, modifier = Modifier.size(20.dp))
            }
            if (isEmbedded) {
                Spacer(Modifier.height(12.dp))
                Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(colors.border.copy(alpha = 0.5f)))
                Spacer(Modifier.height(10.dp))
                embeddedRow(course.theory, theoryAtt, "TH", colors.chart2, colors)
                Spacer(Modifier.height(8.dp))
                embeddedRow(course.lab, labAtt, "LO", colors.chart4, colors)
            }
        }
    }
}

@Composable
private fun embeddedRow(item: MarksCourseItem?, att: AttendanceItem?, label: String, accent: Color, colors: com.amazecc.app.shared.theme.AmazeColors) {
    val pct = att?.attendancePercentage?.toDoubleOrNull() ?: 0.0
    val c = when {
        pct >= 85.0 -> colors.chart1
        pct >= 75.0 -> colors.chart3
        else -> colors.chart5
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(accent.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
            Text(label, style = AmazeTheme.typography.smallLabel.copy(color = accent, fontWeight = FontWeight.Black, fontSize = 13.sp))
        }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(item?.courseTitle ?: att?.courseTitle ?: "", style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Medium, color = colors.textPrimary, fontSize = 13.sp))
            if (item?.faculty?.isNotBlank() == true) {
                Text(item.faculty, style = AmazeTheme.typography.smallLabel.copy(color = colors.textSecondary, fontSize = 10.sp))
            }
        }
        Spacer(Modifier.width(8.dp))
        if (pct > 0) {
            Box(modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(c.copy(alpha = 0.12f)).border(1.dp, c.copy(alpha = 0.3f), RoundedCornerShape(8.dp)).padding(horizontal = 10.dp, vertical = 6.dp), contentAlignment = Alignment.Center) {
                Text("${pct.toInt()}%", style = AmazeTheme.typography.caption.copy(color = c, fontWeight = FontWeight.Black, fontSize = 13.sp))
            }
        }
    }
}

private fun buildSemesterGroups(
    allSemesterMarks: Map<String, MarksRes>,
    allSemesterAttendance: Map<String, AttendanceRes?>,
    marksRes: MarksRes?,
    attendanceRes: AttendanceRes?,
    allGrades: AllGradesRes?,
    timetable: TimetableRes?
): List<CourseGroup> {
    val seenCodes = mutableSetOf<String>()
    val allGroups = mutableListOf<CourseGroup>()

    // Process current semester
    val currentSemId = attendanceRes?.semesterId ?: "CH20262701"
    val currentMarks = marksRes?.marks ?: allSemesterMarks[currentSemId]?.marks ?: emptyList()
    val currentAtt = attendanceRes?.attendance ?: allSemesterAttendance[currentSemId]?.attendance ?: emptyList()

    val currentGroups = buildSemesterMap(currentMarks, currentAtt, currentSemId)
    currentGroups.values.forEach { group ->
        val key = group.courseCode + "_" + group.semesterSubId
        seenCodes.add(key)
        allGroups.add(group)
    }

    // Process past semesters
    for ((semId, marks) in allSemesterMarks) {
        if (semId == currentSemId) continue
        val attList = allSemesterAttendance[semId]?.attendance ?: emptyList()
        val groups = buildSemesterMap(marks.marks, attList, semId)
        groups.values.forEach { group ->
            val key = group.courseCode + "_" + group.semesterSubId
            if (key !in seenCodes) {
                seenCodes.add(key)
                allGroups.add(group)
            }
        }
    }

    // Add grades-only semesters
    allGrades?.grades?.forEach { (semId, semResult) ->
        if (semId == "curriculum" || semId == "effectiveGrades" || semId == currentSemId) return@forEach
        if (allGroups.any { it.semesterSubId == semId }) return@forEach
        semResult?.grades?.forEach { grade ->
            if (grade.courseCode.isBlank()) return@forEach
            val cleanCode = grade.courseCode.replace(Regex("\\([LPT]\\)$"), "").trim()
            val key = cleanCode + "_" + semId
            if (key !in seenCodes) {
                seenCodes.add(key)
                allGroups.add(
                    CourseGroup(
                        courseCode = cleanCode,
                        courseTitle = grade.courseTitle,
                        semesterSubId = semId,
                        semesterName = AppState.semesterMap[semId] ?: semId,
                        theory = MarksCourseItem(courseCode = grade.courseCode, courseTitle = grade.courseTitle, courseType = grade.courseType)
                    )
                )
            }
        }
    }

    return allGroups
}

private fun buildSemesterMap(marks: List<MarksCourseItem>, attendance: List<AttendanceItem>, semId: String): Map<String, CourseGroup> {
    val map = mutableMapOf<String, CourseGroup>()
    val semName = AppState.semesterMap[semId] ?: semId

    marks.forEach { c ->
        if (c.courseCode.isBlank()) return@forEach
        val isLab = c.courseType.lowercase().contains("lab")
        val key = c.courseCode.replace(Regex("\\([LPT]\\)$"), "").trim()
        val existing = map[key]
        if (existing == null) {
            map[key] = CourseGroup(key, c.courseTitle, semId, semName, theory = if (!isLab) c else null, lab = if (isLab) c else null)
        } else {
            if (isLab) map[key] = existing.copy(lab = c)
            else map[key] = existing.copy(theory = c)
        }
    }

    attendance.forEach { a ->
        if (a.courseCode.isBlank()) return@forEach
        val isLab = a.courseType.lowercase().contains("lab") || a.slotName.lowercase().startsWith("l")
        val rawCode = a.courseCode.replace(Regex("\\([LPT]\\)$"), "").trim()
        val key = if (rawCode.contains(" ")) rawCode.split(" ")[0] else rawCode
        val existing = map[key]
        if (existing == null) {
            map[key] = CourseGroup(key, a.courseTitle, semId, semName, theoryAtt = if (!isLab) a else null, labAtt = if (isLab) a else null)
        } else {
            if (isLab) map[key] = existing.copy(labAtt = a)
            else map[key] = existing.copy(theoryAtt = a, courseTitle = a.courseTitle)
        }
    }

    return map
}
