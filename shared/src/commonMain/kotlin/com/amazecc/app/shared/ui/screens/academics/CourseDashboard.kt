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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amazecc.app.shared.model.*
import com.amazecc.app.shared.state.AppState
import com.amazecc.app.shared.ui.components.BOTTOM_NAV_PADDING
import com.amazecc.app.shared.theme.AmazeTheme
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.graphicsLayer
import com.amazecc.app.shared.ui.components.bouncySpring
import com.amazecc.app.shared.ui.components.ScreenHeader
import com.amazecc.app.shared.ui.components.HeaderSpacer

@Composable
fun CourseDashboardScreen(onBack: () -> Unit) {
    val colors = AmazeTheme.colors
    val semesterMap by AppState.semesterMap.collectAsState()
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

    val semesterIds = listOf("All") + semesterGroups.map { it.semesterSubId }.distinct()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        ScreenHeader(
            title = "Course Dashboard",
            description = "All courses across semesters",
            showBackButton = true,
            showSyncButton = true,
            onRefresh = AppState::refreshAllAcademic,
            onBackOverride = onBack
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(bottom = BOTTOM_NAV_PADDING),
            verticalArrangement = Arrangement.spacedBy(AmazeTheme.spacing.sm)
        ) {
            item {
                HeaderSpacer()
            }

            // Search bar item
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(AmazeTheme.radius.medium))
                        .background(colors.surface)
                        .border(1.dp, colors.border.copy(alpha = 0.6f), RoundedCornerShape(AmazeTheme.radius.medium))
                        .padding(horizontal = 14.dp, vertical = 4.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Search, null, tint = colors.accent, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(AmazeTheme.spacing.sm))
                        TextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Search courses...", color = colors.textMuted, fontSize = AmazeTheme.fontSize.base) },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                focusedTextColor = colors.textPrimary,
                                unfocusedTextColor = colors.textPrimary
                            )
                        )
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Rounded.Close, null, tint = colors.textMuted, modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }
                Spacer(Modifier.height(AmazeTheme.spacing.sm))
            }

            // Semester filter chips item
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    semesterIds.forEach { semId ->
                        val isSelected = selectedSemester == semId
                        val label = if (semId == "All") "All Semesters" else semesterMap[semId]?.let {
                            val parts = it.split(" ")
                            if (parts.size >= 2) "${parts[0]} ${parts[1].take(4)}" else it.take(20)
                        } ?: semId.take(10)

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
                                .border(1.dp, if (isSelected) colors.accent else colors.border.copy(alpha = 0.6f), CircleShape)
                                .clickable(
                                    interactionSource = interactionSource,
                                    indication = null,
                                    onClick = { selectedSemester = semId }
                                )
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                label,
                                color = if (isSelected) Color.White else colors.textPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = AmazeTheme.fontSize.sm,
                                maxLines = 1
                            )
                        }
                    }
                }
                Spacer(Modifier.height(AmazeTheme.spacing.sm))
            }

            item {
                val isLoading by AppState.isLoading.collectAsState()
                val pastSynced by AppState.pastSemestersSynced.collectAsState()
                if (!isLoading) {
                    Box(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(AmazeTheme.radius.small))
                            .background(if (pastSynced) colors.surface else colors.accent.copy(alpha = 0.08f))
                            .clickable { AppState.refreshPastSemesters() }
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                if (pastSynced) Icons.Rounded.CheckCircle else Icons.Rounded.HistoryToggleOff,
                                null, tint = if (pastSynced) colors.success else colors.accent,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(AmazeTheme.spacing.sm))
                            Text(
                                if (pastSynced) "Past semesters loaded. Tap to refresh data"
                                else "Load past semester attendance & marks",
                                color = if (pastSynced) colors.textSecondary else colors.accent,
                                fontSize = AmazeTheme.fontSize.base, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f)
                            )
                            Icon(Icons.Rounded.Refresh, null, tint = if (pastSynced) colors.textMuted else colors.accent, modifier = Modifier.size(18.dp))
                        }
                    }
                    Spacer(Modifier.height(AmazeTheme.spacing.sm))
                }
            }

            if (filteredGroups.keys.isEmpty()) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().height(260.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Rounded.SearchOff, null, tint = colors.textMuted, modifier = Modifier.size(54.dp))
                            Spacer(Modifier.height(AmazeTheme.spacing.sm))
                            Text("No courses found", color = colors.textMuted, fontSize = AmazeTheme.fontSize.md, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            } else {
                filteredGroups.forEach { (semId, courses) ->
                    item {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 4.dp)
                        ) {
                            Text(
                                text = courses.firstOrNull()?.semesterName ?: semesterMap[semId] ?: semId,
                                style = AmazeTheme.typography.subheading.copy(fontWeight = FontWeight.Bold, color = colors.accent),
                                modifier = Modifier.weight(1f)
                            )
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(AmazeTheme.radius.xs))
                                    .background(colors.accent.copy(alpha = 0.1f))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text("${courses.size} courses", fontSize = AmazeTheme.fontSize.xs, color = colors.accent, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    items(courses, key = { it.courseCode }) { course ->
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

data class CleanedTitle(
    val cleanTitle: String,
    val typeBadgeOverride: String? = null
)

fun cleanCourseTitle(rawTitle: String): CleanedTitle {
    var title = rawTitle.trim()
    var badge: String? = null

    if (title.contains("(Project)", ignoreCase = true)) {
        title = title.replace(Regex("(?i)\\(\\s*Project\\s*\\)"), "").trim()
        badge = "PJ"
    } else if (title.contains("(Online Course)", ignoreCase = true) || title.contains("(Online)", ignoreCase = true)) {
        title = title.replace(Regex("(?i)\\(\\s*Online\\s*(Course)?\\s*\\)"), "").trim()
        badge = "OC"
    } else if (title.contains("(Theory)", ignoreCase = true)) {
        title = title.replace(Regex("(?i)\\(\\s*Theory\\s*\\)"), "").trim()
        badge = "TH"
    } else if (title.contains("(Lab)", ignoreCase = true)) {
        title = title.replace(Regex("(?i)\\(\\s*Lab\\s*\\)"), "").trim()
        badge = "LO"
    }

    title = title.replace(Regex("\\s+"), " ").trim()
    return CleanedTitle(cleanTitle = title, typeBadgeOverride = badge)
}

@Composable
private fun CourseDetailCard(course: CourseGroup, onClick: () -> Unit) {
    val colors = AmazeTheme.colors

    val theoryAtt = course.theoryAtt
    val labAtt = course.labAtt
    val isEmbedded = (course.theory != null && course.lab != null) || (course.theoryAtt != null && course.labAtt != null)

    val cleaned = cleanCourseTitle(course.courseTitle)
    val displayTitle = cleaned.cleanTitle

    fun attColor(pct: Double) = when {
        pct >= 85.0 -> colors.success
        pct >= 75.0 -> colors.warning
        else -> colors.danger
    }

    val defaultLabel = if (course.theory?.courseType?.contains("Lab", ignoreCase = true) == true || course.lab?.courseType?.contains("Lab", ignoreCase = true) == true) "LO" else "TH"
    val label = if (isEmbedded) (cleaned.typeBadgeOverride ?: "EMB") else (cleaned.typeBadgeOverride ?: defaultLabel)
    val badgeColor = when (label) {
        "PJ" -> Color(0xFF8B5CF6)
        "OC" -> Color(0xFF06B6D4)
        "LO" -> colors.success
        "EMB" -> colors.chart3
        else -> colors.accent
    }

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = bouncySpring()
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(AmazeTheme.radius.large))
            .background(colors.surface)
            .border(1.dp, colors.border.copy(alpha = 0.6f), RoundedCornerShape(AmazeTheme.radius.large))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(14.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(AmazeTheme.radius.xs))
                            .background(badgeColor.copy(alpha = 0.15f))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = label,
                            style = AmazeTheme.typography.smallLabel.copy(
                                color = badgeColor,
                                fontWeight = FontWeight.Black,
                                fontSize = AmazeTheme.fontSize.xs
                            )
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = course.courseCode,
                        style = AmazeTheme.typography.smallLabel.copy(
                            color = colors.textMuted,
                            fontWeight = FontWeight.Bold,
                            fontSize = AmazeTheme.fontSize.sm
                        )
                    )
                }

                if (!isEmbedded) {
                    val attPct = (theoryAtt?.attendancePercentage ?: labAtt?.attendancePercentage ?: "0").replace("%", "").trim().toDoubleOrNull() ?: 0.0
                    if (attPct > 0) {
                        val c = attColor(attPct)
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(c.copy(alpha = 0.12f))
                                .border(1.dp, c.copy(alpha = 0.3f), CircleShape)
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "${attPct.toInt()}%",
                                style = AmazeTheme.typography.caption.copy(color = c, fontWeight = FontWeight.Black, fontSize = AmazeTheme.fontSize.xs)
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = displayTitle,
                    style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(end = 8.dp)
                )
                Icon(Icons.Rounded.ChevronRight, null, tint = colors.textMuted, modifier = Modifier.size(20.dp))
            }

            val rawFaculty = course.theory?.faculty?.ifBlank { null } ?: course.lab?.faculty?.ifBlank { null }
                    ?: theoryAtt?.faculty?.ifBlank { null } ?: labAtt?.faculty?.ifBlank { null }

            if (!isEmbedded && !rawFaculty.isNullOrBlank()) {
                val parsedFac = com.amazecc.app.shared.utils.FacultyUtils.parseFaculty(rawFaculty)
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = parsedFac.name,
                        style = AmazeTheme.typography.caption.copy(color = colors.textSecondary, fontSize = AmazeTheme.fontSize.xs),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (!parsedFac.school.isNullOrBlank()) {
                        Spacer(Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(AmazeTheme.radius.xs))
                                .background(colors.accent.copy(alpha = 0.1f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = parsedFac.school,
                                style = AmazeTheme.typography.smallLabel.copy(color = colors.accent, fontWeight = FontWeight.Bold, fontSize = AmazeTheme.fontSize.micro)
                            )
                        }
                    }
                }
            }

            if (isEmbedded) {
                Spacer(Modifier.height(10.dp))
                Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(colors.border.copy(alpha = 0.4f)))
                Spacer(Modifier.height(8.dp))
                embeddedRow(course.theory, theoryAtt, "TH", "Theory", colors.chart2, colors)
                Spacer(Modifier.height(6.dp))
                embeddedRow(course.lab, labAtt, "LO", "Lab", colors.chart4, colors)
            }
        }
    }
}

@Composable
private fun embeddedRow(
    item: MarksCourseItem?,
    att: AttendanceItem?,
    label: String,
    componentName: String,
    accent: Color,
    colors: com.amazecc.app.shared.theme.AmazeColors
) {
    val pct = att?.attendancePercentage?.replace("%", "")?.trim()?.toDoubleOrNull() ?: 0.0
    val c = when {
        pct >= 85.0 -> colors.chart1
        pct >= 75.0 -> colors.chart3
        else -> colors.chart5
    }
    val rawFaculty = item?.faculty?.ifBlank { null } ?: att?.faculty?.ifBlank { null }
    val slot = item?.slot ?: att?.slotName

    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(36.dp).clip(RoundedCornerShape(AmazeTheme.radius.small)).background(accent.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
            Text(label, style = AmazeTheme.typography.smallLabel.copy(color = accent, fontWeight = FontWeight.Black))
        }
        Spacer(Modifier.width(AmazeTheme.spacing.sm))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (slot.isNullOrBlank()) componentName else "$componentName ($slot)",
                style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.SemiBold, color = colors.textPrimary)
            )
            if (!rawFaculty.isNullOrBlank()) {
                val parsedFac = com.amazecc.app.shared.utils.FacultyUtils.parseFaculty(rawFaculty)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(parsedFac.name, style = AmazeTheme.typography.smallLabel.copy(color = colors.textSecondary))
                    if (!parsedFac.school.isNullOrBlank()) {
                        Spacer(Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(AmazeTheme.radius.xs))
                                .background(colors.accent.copy(alpha = 0.1f))
                                .padding(horizontal = 5.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = parsedFac.school,
                                style = AmazeTheme.typography.smallLabel.copy(color = colors.accent, fontWeight = FontWeight.Bold, fontSize = AmazeTheme.fontSize.micro)
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.width(AmazeTheme.spacing.sm))
        if (pct > 0) {
            Box(modifier = Modifier.clip(RoundedCornerShape(AmazeTheme.radius.xs)).background(c.copy(alpha = 0.12f)).border(1.dp, c.copy(alpha = 0.3f), RoundedCornerShape(AmazeTheme.radius.xs)).padding(horizontal = 10.dp, vertical = 6.dp), contentAlignment = Alignment.Center) {
                Text("${pct.toInt()}%", style = AmazeTheme.typography.caption.copy(color = c, fontWeight = FontWeight.Black))
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
    val currentSemId = attendanceRes?.semesterId ?: AppState.DEFAULT_SEMESTER_ID
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

    // Add grades-only semesters (with any available attendance/marks)
    allGrades?.grades?.forEach { (semId, semResult) ->
        if (semId == "curriculum" || semId == "effectiveGrades" || semId == currentSemId) return@forEach
        if (allGroups.any { it.semesterSubId == semId }) return@forEach
        // Check if this semester has attendance/marks data that wasn't caught by the marks loop
        val attList = allSemesterAttendance[semId]?.attendance ?: emptyList()
        val semMarks = allSemesterMarks[semId]?.marks ?: emptyList()
        if (semMarks.isNotEmpty() || attList.isNotEmpty()) {
            val groups = buildSemesterMap(semMarks, attList, semId)
            groups.values.forEach { group ->
                val key = group.courseCode + "_" + semId
                if (key !in seenCodes) {
                    seenCodes.add(key)
                    allGroups.add(group)
                }
            }
        } else {
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
                            semesterName = AppState.semesterMap.value[semId] ?: semId,
                            theory = MarksCourseItem(courseCode = grade.courseCode, courseTitle = grade.courseTitle, courseType = grade.courseType),
                            grade = grade
                        )
                    )
                }
            }
        }
    }

    return allGroups
}

private fun buildSemesterMap(marks: List<MarksCourseItem>, attendance: List<AttendanceItem>, semId: String): Map<String, CourseGroup> {
    val map = mutableMapOf<String, CourseGroup>()
    val semName = AppState.semesterMap.value[semId] ?: semId

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
        val key = a.courseCode.replace(Regex("\\([LPT]\\)$"), "").trim()
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
