package com.amazecc.app.shared.ui.screens.academics

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amazecc.app.shared.api.AmazeClient
import com.amazecc.app.shared.api.SyllabusDownload
import com.amazecc.app.shared.config.SlotMap
import com.amazecc.app.shared.repository.SettingsManager
import com.amazecc.app.shared.model.*
import com.amazecc.app.shared.state.AppState
import com.amazecc.app.shared.state.Screen
import com.amazecc.app.shared.theme.AmazeTheme
import com.amazecc.app.shared.ui.components.BOTTOM_NAV_PADDING
import com.amazecc.app.shared.ui.strings.Strings
import com.amazecc.app.shared.ui.components.AmazeCard
import com.amazecc.app.shared.ui.components.AmazeButton
import com.amazecc.app.shared.ui.components.AmazeBadge
import com.amazecc.app.shared.ui.components.BadgeVariant
import com.amazecc.app.shared.ui.components.ButtonVariant
import com.amazecc.app.shared.ui.components.ScreenHeader
import com.amazecc.app.shared.ui.components.HeaderSpacer
import com.amazecc.app.shared.ui.components.bouncySpring
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.automirrored.rounded.Assignment
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.graphics.graphicsLayer
import com.amazecc.app.shared.utils.parseViewLink
import com.amazecc.app.shared.utils.rememberFileSaver
import com.amazecc.app.shared.utils.ParsedFaculty
import com.amazecc.app.shared.ui.screens.FacultyDetailScreen
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.seconds
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.datetime.*
import kotlinx.serialization.json.*

private val GradeColors: Map<String, Color?> = mapOf(
    "S" to null, "A" to null,
    "B" to null, "C" to null,
    "D" to null, "E" to null,
    "F" to null
)

private val GradeBoundariesAbsolute = listOf(
    "S" to 90, "A" to 80, "B" to 70, "C" to 60, "D" to 50, "E" to 40, "F" to 0
)

private fun formatSemesterName(id: String): String {
    if (!id.uppercase().startsWith("CH") || id.length != 10) return id
    val y1 = id.substring(2, 6)
    val y2 = id.substring(6, 8)
    val term = id.substring(8, 10)
    val tName = when (term) { "01" -> "Fall"; "05" -> "Winter"; "07" -> "Summer"; else -> "Term $term" }
    return "$tName $y1-$y2"
}

private fun predictedGrade(pct: Double): String = when {
    pct >= 90 -> "S"; pct >= 80 -> "A"; pct >= 70 -> "B"; pct >= 60 -> "C"
    pct >= 50 -> "D"; pct >= 40 -> "E"; else -> "F"
}

private fun healthStatus(attPct: Double, predGrade: String, isPast: Boolean, colors: com.amazecc.app.shared.theme.AmazeColors): Triple<String, Color, Color> {
    if (isPast) return Triple("Completed", colors.textMuted, colors.textMuted.copy(alpha = 0.12f))
    if (attPct < 75 || predGrade == "F") return Triple("Critical", colors.danger, colors.danger.copy(alpha = 0.12f))
    if (attPct < 80 || predGrade in listOf("D", "E")) return Triple("Watch", colors.warning, colors.warning.copy(alpha = 0.12f))
    return Triple("Healthy", colors.success, colors.success.copy(alpha = 0.12f))
}

data class CourseGroup(
    val courseCode: String,
    val courseTitle: String,
    val semesterSubId: String,
    val semesterName: String,
    val theory: MarksCourseItem? = null,
    val lab: MarksCourseItem? = null,
    val theoryAtt: AttendanceItem? = null,
    val labAtt: AttendanceItem? = null,
    val grade: GradeItem? = null
)

@Composable
fun CourseDetailScreen(onBack: () -> Unit) {
    val colors = AmazeTheme.colors
    val courseCode = AppState.selectedCourseCode.value ?: ""
    val semesterId = AppState.selectedCourseSemester.value
    val allSemesterMarks by AppState.allSemesterMarks.collectAsState()
    val allSemesterAttendance by AppState.allSemesterAttendance.collectAsState()
    val attendanceRes by AppState.attendance.collectAsState()
    val marksRes by AppState.marks.collectAsState()
    val allGrades by AppState.allGrades.collectAsState()
    val timetable by AppState.timetable.collectAsState()
    val calendar by AppState.calendar.collectAsState()

    val currentSemesterId = attendanceRes?.semesterId ?: AppState.DEFAULT_SEMESTER_ID
    val mainSemesterId = semesterId ?: currentSemesterId

    val group = remember(courseCode, mainSemesterId, allSemesterMarks, allSemesterAttendance, marksRes, attendanceRes) {
        findCourseGroup(courseCode, mainSemesterId, allSemesterMarks, allSemesterAttendance, marksRes, attendanceRes, timetable)
    }

    val isEmbedded = (group?.theory != null && group?.lab != null) || (group?.theoryAtt != null && group?.labAtt != null)
    val coroutineScope = rememberCoroutineScope()

    var facultyView by remember { mutableStateOf<FacultyProfile?>(null) }
    var facultyLoading by remember { mutableStateOf(false) }

    val onViewFaculty: (ParsedFaculty) -> Unit = { parsed ->
        if (!facultyLoading) {
            coroutineScope.launch {
                facultyLoading = true
                val dir = AmazeClient.searchFacultyDirectory(parsed.name, parsed.id, parsed.school)
                facultyLoading = false
                facultyView = FacultyProfile(
                    id = parsed.id ?: dir?.id ?: "",
                    name = parsed.name.ifBlank { dir?.name ?: parsed.name },
                    designation = dir?.designation ?: "",
                    imageUrl = dir?.imageUrl ?: "",
                    profileUrl = dir?.profileUrl ?: "",
                    email = dir?.email ?: "",
                    employeeId = dir?.employeeId ?: parsed.id ?: "",
                    intercom = dir?.intercom ?: ""
                )
            }
        }
    }

    val theoryAtt = group?.theoryAtt
    val labAtt = group?.labAtt
    val mainAtt = theoryAtt ?: labAtt

    var innerTab by remember { mutableStateOf("overview") }
    val tabs = listOf("overview", "grades", "marks", "attendance", "plan", "qbank", "tasks")
    val tabLabels = mapOf(
        "overview" to "Overview", "grades" to "Grade History",
        "marks" to "Marks", "attendance" to "Attendance",
        "plan" to "Course Plan", "qbank" to "QBank",
        "tasks" to "Tasks"
    )

    if (group == null) {
        Box(modifier = Modifier.fillMaxSize().background(colors.background), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Rounded.SearchOff, null, tint = colors.textMuted, modifier = Modifier.size(48.dp))
                Spacer(Modifier.height(AmazeTheme.spacing.sm))
                Text("Course not found", color = colors.textSecondary)
                Spacer(Modifier.height(AmazeTheme.spacing.sm))
                AmazeButton("Go Back", onClick = onBack)
            }
        }
        return
    }

    val fv = facultyView
    if (fv != null) {
        FacultyDetailScreen(faculty = fv, onBack = { facultyView = null })
        return
    }

    val isPastSemester = group.semesterSubId != currentSemesterId

    val qcmViewRes by AppState.qcmView.collectAsState()
    val qcmTables = extractQcmTables(qcmViewRes?.data)
    val qcmLoading = AppState.isLoading.collectAsState().value

    val tabIcons = mapOf(
        "overview" to Icons.Rounded.Dashboard,
        "grades" to Icons.Rounded.History,
        "marks" to Icons.Rounded.Assessment,
        "attendance" to Icons.Rounded.CheckCircle,
        "plan" to Icons.AutoMirrored.Rounded.MenuBook,
        "qbank" to Icons.Rounded.Folder,
        "tasks" to Icons.AutoMirrored.Rounded.Assignment
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        ScreenHeader(
            title = group.courseCode,
            description = group.courseTitle,
            showBackButton = true,
            showSyncButton = false,
            enabledScreens = setOf(Screen.COURSE_DETAIL)
        )

        Column(modifier = Modifier.fillMaxSize()) {
            HeaderSpacer()

            // Expressive Tab Bar Capsule
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                tabs.forEach { tab ->
                    val selected = innerTab == tab
                    val icon = tabIcons[tab] ?: Icons.Rounded.Circle

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
                            .background(if (selected) colors.accent else colors.surface)
                            .border(1.dp, if (selected) colors.accent else colors.border.copy(alpha = 0.6f), CircleShape)
                            .clickable(
                                interactionSource = interactionSource,
                                indication = null,
                                onClick = {
                                    innerTab = tab
                                    if (tab == "overview" && qcmTables.isEmpty() && !qcmLoading) AppState.refreshQcmView()
                                }
                            )
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                icon,
                                null,
                                tint = if (selected) Color.White else colors.textSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(AmazeTheme.spacing.xs))
                            Text(
                                tabLabels[tab] ?: tab,
                                color = if (selected) Color.White else colors.textPrimary,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                fontSize = AmazeTheme.fontSize.sm,
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                when (innerTab) {
                    "overview" -> OverviewTab(group, theoryAtt, labAtt, mainAtt, isEmbedded, isPastSemester, qcmTables, qcmLoading, { AppState.refreshQcmView() }, facultyLoading, onViewFaculty, colors)
                    "grades" -> GradeHistoryTab(courseCode, allGrades, group, colors)
                    "marks" -> MarksTab(group, isEmbedded, allGrades, mainSemesterId, colors)
                    "attendance" -> AttendanceTab(courseCode, group, theoryAtt, labAtt, mainAtt, isEmbedded, isPastSemester, calendar, colors)
                    "plan" -> CoursePlanTab(courseCode, group.theory, group.lab, mainAtt, colors)
                    "qbank" -> QBankTab(courseCode, colors)
                    "tasks" -> {
                        val taskCodes = buildList {
                            add(courseCode)
                            group?.theory?.courseCode?.let { if (it != courseCode) add(it) }
                            group?.lab?.courseCode?.let { if (it != courseCode) add(it) }
                        }
                        CourseTasksTab(taskCodes, group.courseTitle, colors)
                    }
                }
            }
        }
    }
}

@Composable
private fun OverviewTab(
    group: CourseGroup,
    theoryAtt: AttendanceItem?,
    labAtt: AttendanceItem?,
    mainAtt: AttendanceItem?,
    isEmbedded: Boolean,
    isPastSemester: Boolean,
    qcmTables: List<QcmTable>,
    qcmLoading: Boolean,
    refreshQcm: () -> Unit,
    facultyLoading: Boolean,
    onViewFaculty: (ParsedFaculty) -> Unit,
    colors: com.amazecc.app.shared.theme.AmazeColors
) {
    val moodleAssignments = remember(group) {
        AppState.getMoodleAssignmentsForCourse(group.courseCode)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = BOTTOM_NAV_PADDING)
    ) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (isEmbedded) {
                    Box(Modifier.weight(1f)) { CircularAttendCard("Theory", theoryAtt, colors.accent, colors) }
                    Box(Modifier.weight(1f)) { CircularAttendCard("Lab", labAtt, colors.success, colors) }
                } else {
                    Box(Modifier.weight(1f)) { CircularAttendCard("Attendance", mainAtt, colors.accent, colors) }
                }

                val attItem = if (isEmbedded) theoryAtt else mainAtt
                val attPct = attItem?.attendancePercentage?.replace("%", "")?.trim()?.toDoubleOrNull() ?: 0.0
                val grade = predictedGrade(0.0)
                val (healthLabel, healthColor, healthBg) = healthStatus(attPct, grade, isPastSemester, colors)

                AmazeCard(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text("Status", fontSize = AmazeTheme.fontSize.micro, color = colors.textMuted, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(AmazeTheme.spacing.sm))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(AmazeTheme.radius.xs))
                                .background(healthBg)
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(healthLabel, color = healthColor, fontWeight = FontWeight.Bold, fontSize = AmazeTheme.fontSize.sm)
                        }
                        if (isEmbedded && theoryAtt != null && labAtt != null) {
                            Spacer(Modifier.height(AmazeTheme.spacing.sm))
                            Text("T: ${theoryAtt.attendedClasses}/${theoryAtt.totalClasses}", fontSize = AmazeTheme.fontSize.micro, color = colors.accent)
                            Text("L: ${labAtt.attendedClasses}/${labAtt.totalClasses}", fontSize = AmazeTheme.fontSize.micro, color = colors.success)
                        } else if (mainAtt != null) {
                            Spacer(Modifier.height(AmazeTheme.spacing.sm))
                            Text("${mainAtt.attendedClasses}/${mainAtt.totalClasses} classes", fontSize = AmazeTheme.fontSize.micro, color = colors.textSecondary)
                        }
                    }
                }
            }
        }

        item {
            AmazeCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    Text("Course Details", style = AmazeTheme.typography.subheading.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
                    Spacer(Modifier.height(12.dp))
                    val mainCourse = group.theory ?: group.lab
                    val rawFaculty = mainCourse?.faculty?.ifBlank { null }
                        ?: group.theoryAtt?.faculty?.ifBlank { null }
                        ?: group.labAtt?.faculty?.ifBlank { null }
                        ?: mainAtt?.faculty?.ifBlank { null }
                    val parsedFac = if (!rawFaculty.isNullOrBlank()) com.amazecc.app.shared.utils.FacultyUtils.parseFaculty(rawFaculty) else null

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            MetricTile("Type", if (isEmbedded) "Embedded" else (mainCourse?.courseType ?: "-"), colors, Modifier.weight(1f))
                            MetricTile("Slot", mainCourse?.slot ?: "-", colors, Modifier.weight(1f))
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            MetricTile("System", mainCourse?.courseSystem ?: "-", colors, Modifier.weight(1f))
                            MetricTile("Credits", mainAtt?.credits ?: "-", colors, Modifier.weight(1f))
                        }
                        if (parsedFac != null) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(AmazeTheme.radius.small))
                                    .background(colors.surface)
                                    .border(1.dp, colors.border.copy(alpha = 0.5f), RoundedCornerShape(AmazeTheme.radius.small))
                                    .padding(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Faculty", style = AmazeTheme.typography.smallLabel.copy(color = colors.textMuted, fontSize = AmazeTheme.fontSize.micro))
                                        Spacer(Modifier.height(2.dp))
                                        Text(parsedFac.name, style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary, fontSize = AmazeTheme.fontSize.base))
                                    }
                                    if (!parsedFac.school.isNullOrBlank()) {
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(AmazeTheme.radius.xs))
                                                .background(colors.accent.copy(alpha = 0.12f))
                                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                        ) {
                                            Text(parsedFac.school, style = AmazeTheme.typography.smallLabel.copy(color = colors.accent, fontWeight = FontWeight.Bold, fontSize = AmazeTheme.fontSize.micro))
                                        }
                                    }
                                }
                            }
                            Spacer(Modifier.height(AmazeTheme.spacing.sm))
                            AmazeButton(
                                text = if (facultyLoading) "Finding faculty..." else "View Faculty & Free Slots",
                                onClick = { onViewFaculty(parsedFac) },
                                modifier = Modifier.fillMaxWidth(),
                                variant = ButtonVariant.SECONDARY
                            )
                        }
                    }

                    if (isEmbedded) {
                        Spacer(Modifier.height(AmazeTheme.spacing.sm))
                        Box(
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(AmazeTheme.radius.small)).background(colors.accent.copy(alpha = 0.1f)).padding(12.dp)
                        ) {
                            Column {
                                Text("Components", style = AmazeTheme.typography.smallLabel.copy(fontWeight = FontWeight.Bold, color = colors.accent))
                                Spacer(Modifier.height(AmazeTheme.spacing.xs))
                                group.theory?.let { Text("${it.courseType} — Class #${it.classNbr.takeLast(4)}", fontSize = AmazeTheme.fontSize.sm, color = colors.textPrimary, fontWeight = FontWeight.Medium) }
                                group.lab?.let { Text("${it.courseType} — Class #${it.classNbr.takeLast(4)}", fontSize = AmazeTheme.fontSize.sm, color = colors.textPrimary, fontWeight = FontWeight.Medium) }
                            }
                        }
                    }
                }
            }
        }

        item {
            val assessments = (group.theory?.assessments ?: emptyList()) + (group.lab?.assessments ?: emptyList())
            if (assessments.isNotEmpty()) {
                AmazeCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text("Marks Snapshot", style = AmazeTheme.typography.subheading.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
                        Spacer(Modifier.height(AmazeTheme.spacing.sm))
                        val totalWeighted = assessments.sumOf { it.weightageMark.toDoubleOrNull() ?: 0.0 }
                        val totalWeightPct = assessments.sumOf { it.weightagePercent.toDoubleOrNull() ?: 0.0 }
                        val pct = if (totalWeightPct > 0) (totalWeighted / totalWeightPct * 100).toInt() else 0
                        Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
                            StatItem("Scored", "${totalWeighted.toInt()}", colors.accent, colors)
                            StatItem("Weight", "${totalWeightPct.toInt()}%", colors.info, colors)
                            StatItem("Projected", "$pct%", if (pct >= 70) colors.success else colors.warning, colors)
                        }
                        Spacer(Modifier.height(AmazeTheme.spacing.sm))
                        LinearProgressIndicator(
                            progress = { if (totalWeightPct > 0) (totalWeighted / totalWeightPct).toFloat() else 0f },
                            modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(AmazeTheme.radius.xs)),
                            color = colors.accent,
                            trackColor = colors.border,
                        )
                    }
                }
            }
        }

        if (moodleAssignments.isNotEmpty()) {
            item {
                AmazeCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Moodle Assignments", style = AmazeTheme.typography.subheading.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary), modifier = Modifier.weight(1f))
                            Text("${moodleAssignments.size}", style = AmazeTheme.typography.smallLabel.copy(color = colors.textMuted))
                        }
                        Spacer(Modifier.height(AmazeTheme.spacing.sm))
                        moodleAssignments.forEach { assignment ->
                            val dueColor = try {
                                val dueDate = assignment.due.split(" ").firstOrNull() ?: ""
                                if (assignment.done) colors.success else if (dueDate.isNotEmpty()) colors.warning else colors.textMuted
                            } catch (_: Exception) { colors.textMuted }
                            Box(
                                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(AmazeTheme.radius.small)).background(colors.surface).padding(12.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier.size(8.dp).clip(CircleShape).background(if (assignment.done) colors.success else colors.warning)
                                    )
                                    Spacer(Modifier.width(AmazeTheme.spacing.sm))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(assignment.taskTitle, style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Medium, color = colors.textPrimary))
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Rounded.Schedule, null, tint = dueColor, modifier = Modifier.size(12.dp))
                                            Spacer(Modifier.width(AmazeTheme.spacing.xs))
                                            Text(assignment.due, style = AmazeTheme.typography.caption.copy(color = colors.textSecondary))
                                        }
                                    }
                                    if (assignment.done) {
                                        Icon(Icons.Rounded.CheckCircle, null, tint = colors.success, modifier = Modifier.size(18.dp))
                                    } else {
                                        Icon(Icons.Rounded.Warning, null, tint = colors.warning, modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                            Spacer(Modifier.height(AmazeTheme.spacing.xs))
                        }
                    }
                }
            }
        }

        item {
            AmazeCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Quality Circle Meeting (QCM)", style = AmazeTheme.typography.subheading.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary), modifier = Modifier.weight(1f))
                        if (qcmTables.isEmpty() && !qcmLoading) {
                            AmazeButton("Load", onClick = refreshQcm, variant = ButtonVariant.SECONDARY, modifier = Modifier.height(32.dp))
                        }
                    }
                    Spacer(Modifier.height(AmazeTheme.spacing.sm))
                    when {
                        qcmLoading -> CircularProgressIndicator(color = colors.accent, strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
                        qcmTables.isEmpty() -> Text("No QCM data available", color = colors.textMuted, fontSize = AmazeTheme.fontSize.sm)
                        else -> qcmTables.forEach { table ->
                            table.rows.forEach { rowJson ->
                                val obj = rowJson.jsonObject
                                val qcmNo = obj["qcmNo"]?.jsonPrimitive?.contentOrNull ?: obj["QCM No"]?.jsonPrimitive?.contentOrNull
                                val action = obj["actionTaken"]?.jsonPrimitive?.contentOrNull ?: obj["Action Taken"]?.jsonPrimitive?.contentOrNull
                                val suggestions = obj["suggestions"]?.jsonPrimitive?.contentOrNull ?: obj["Suggestions"]?.jsonPrimitive?.contentOrNull
                                val facultyReply = obj["facultyReply"]?.jsonPrimitive?.contentOrNull ?: obj["Faculty Reply"]?.jsonPrimitive?.contentOrNull
                                Box(
                                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(AmazeTheme.radius.small)).background(colors.surface).padding(12.dp)
                                ) {
                                    Column {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text("QCM ${qcmNo ?: ""}", fontWeight = FontWeight.Bold, color = colors.textSecondary, fontSize = AmazeTheme.fontSize.xs, modifier = Modifier.weight(1f))
                                            action?.let { AmazeBadge(it, variant = BadgeVariant.INFO) }
                                        }
                                        Spacer(Modifier.height(AmazeTheme.spacing.xs))
                                        suggestions?.let { Text(it, color = colors.textPrimary, fontSize = AmazeTheme.fontSize.sm) }
                                        facultyReply?.let {
                                            Spacer(Modifier.height(AmazeTheme.spacing.xs))
                                            Box(modifier = Modifier.fillMaxWidth().padding(start = 8.dp)) {
                                                Column {
                                                    Text("Faculty Reply", fontWeight = FontWeight.Bold, color = colors.success, fontSize = AmazeTheme.fontSize.micro)
                                                    Text(it, color = colors.textSecondary, fontSize = AmazeTheme.fontSize.sm)
                                                }
                                            }
                                        }
                                    }
                                }
                                Spacer(Modifier.height(AmazeTheme.spacing.xs))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CircularAttendCard(label: String, att: AttendanceItem?, accent: Color, colors: com.amazecc.app.shared.theme.AmazeColors) {
    val pct = att?.attendancePercentage?.replace("%", "")?.trim()?.toDoubleOrNull() ?: 0.0
    val animatedPct by animateFloatAsState(targetValue = (pct / 100f).toFloat(), animationSpec = tween(1000))
    AmazeCard(modifier = Modifier.fillMaxWidth()) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(64.dp)) {
                Canvas(modifier = Modifier.size(64.dp)) {
                    drawArc(color = Color.LightGray.copy(alpha = 0.3f), startAngle = -90f, sweepAngle = 360f, useCenter = false, style = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round))
                    drawArc(color = accent, startAngle = -90f, sweepAngle = 360f * animatedPct, useCenter = false, style = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round))
                }
                Text("${pct.toInt()}%", fontWeight = FontWeight.Black, fontSize = AmazeTheme.fontSize.lg, color = accent)
            }
            Spacer(Modifier.height(AmazeTheme.spacing.xs))
            Text(label, fontSize = AmazeTheme.fontSize.micro, color = colors.textSecondary, fontWeight = FontWeight.Bold)
            if (att != null) {
                Text("${att.attendedClasses}/${att.totalClasses}", fontSize = AmazeTheme.fontSize.micro, color = colors.textMuted)
            }
        }
    }
}

@Composable
private fun StatItem(label: String, value: String, color: Color, colors: com.amazecc.app.shared.theme.AmazeColors) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontWeight = FontWeight.Black, fontSize = AmazeTheme.fontSize.lg, color = color)
        Text(label, fontSize = AmazeTheme.fontSize.micro, color = colors.textMuted)
    }
}

@Composable
private fun DetailRow(label: String, value: String, colors: com.amazecc.app.shared.theme.AmazeColors) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = colors.textMuted, fontSize = AmazeTheme.fontSize.sm)
        Text(value, fontWeight = FontWeight.SemiBold, color = colors.textPrimary, fontSize = AmazeTheme.fontSize.sm)
    }
}

@Composable
private fun MetricTile(label: String, value: String, colors: com.amazecc.app.shared.theme.AmazeColors, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(AmazeTheme.radius.small))
            .background(colors.surface)
            .border(1.dp, colors.border.copy(alpha = 0.5f), RoundedCornerShape(AmazeTheme.radius.small))
            .padding(10.dp)
    ) {
        Column {
            Text(label, style = AmazeTheme.typography.smallLabel.copy(color = colors.textMuted, fontSize = AmazeTheme.fontSize.micro))
            Spacer(Modifier.height(2.dp))
            Text(value, style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary, fontSize = AmazeTheme.fontSize.base))
        }
    }
}

@Composable
private fun GradeHistoryTab(courseCode: String, allGrades: AllGradesRes?, group: CourseGroup, colors: com.amazecc.app.shared.theme.AmazeColors) {
    val semesterMap by AppState.semesterMap.collectAsState()
    val gradeItems = remember(allGrades) {
        val items = mutableListOf<Pair<String, GradeItem?>>()
        allGrades?.grades?.forEach { (semId, semResult) ->
            semResult?.grades?.forEach { grade ->
                if (grade.courseCode.replace(Regex("\\([LPT]\\)$"), "").trim() == courseCode) {
                    items.add(semId to grade)
                }
            }
        }
        items.sortedByDescending { it.first }
    }

    if (gradeItems.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Rounded.History, null, tint = colors.textMuted, modifier = Modifier.size(40.dp))
                Spacer(Modifier.height(AmazeTheme.spacing.sm))
                Text("No grade history available", color = colors.textSecondary)
            }
        }
        return
    }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp), contentPadding = PaddingValues(bottom = BOTTOM_NAV_PADDING)) {
        itemsIndexed(gradeItems) { index, (semId, grade) ->
            val semName = semesterMap[semId] ?: formatSemesterName(semId)
            Row(modifier = Modifier.fillMaxWidth()) {
                if (index % 2 == 0) {
                    TimelineCard(semName, grade, colors, Modifier.weight(1f))
                    Spacer(Modifier.width(AmazeTheme.spacing.sm))
                } else {
                    Spacer(Modifier.width(AmazeTheme.spacing.sm))
                    TimelineCard(semName, grade, colors, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun TimelineCard(semName: String, grade: GradeItem?, colors: com.amazecc.app.shared.theme.AmazeColors, modifier: Modifier = Modifier) {
    AmazeCard(modifier = modifier) {
        Column {
            Text(semName, fontWeight = FontWeight.Bold, color = colors.textPrimary, fontSize = AmazeTheme.fontSize.sm)

            if (grade != null) {
                Spacer(Modifier.height(AmazeTheme.spacing.sm))
                val gc = GradeColors[grade.grade.uppercase()] ?: colors.textPrimary
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier.size(40.dp).clip(CircleShape).background(gc.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(grade.grade, fontWeight = FontWeight.Black, fontSize = AmazeTheme.fontSize.xl, color = gc)
                    }
                    Spacer(Modifier.width(AmazeTheme.spacing.sm))
                    Column {
                        Text("Total: ${grade.grandTotal}", fontSize = AmazeTheme.fontSize.sm, color = colors.textSecondary)
                        Text(grade.courseType, fontSize = AmazeTheme.fontSize.micro, color = colors.textMuted)
                    }
                }

                grade.range?.let { range ->
                    Spacer(Modifier.height(AmazeTheme.spacing.sm))
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                        listOf("S" to range.S, "A" to range.A, "B" to range.B, "C" to range.C, "D" to range.D, "E" to range.E, "F" to range.F).forEach { (g, r) ->
                            val gColor = GradeColors[g] ?: colors.textMuted
                            Box(
                                modifier = Modifier.weight(1f).clip(RoundedCornerShape(AmazeTheme.radius.xs)).background(gColor.copy(alpha = 0.1f)).padding(vertical = 4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(g, fontWeight = FontWeight.Black, fontSize = AmazeTheme.fontSize.micro, color = gColor)
                                    Text(r, fontSize = AmazeTheme.fontSize.micro, color = colors.textMuted)
                                }
                            }
                        }
                    }
                }

                grade.details?.let { details ->
                    Spacer(Modifier.height(AmazeTheme.spacing.sm))
                    details.take(4).forEach { comp ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(comp.component, fontSize = AmazeTheme.fontSize.micro, color = colors.textSecondary, modifier = Modifier.weight(1f))
                            Text("${comp.scoredMark}/${comp.maxMark}", fontSize = AmazeTheme.fontSize.micro, color = colors.textPrimary, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GradeViewCard(grade: GradeItem, label: String, colors: com.amazecc.app.shared.theme.AmazeColors) {
    val gc = GradeColors[grade.grade.uppercase()] ?: colors.textPrimary
    AmazeCard(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(48.dp).clip(CircleShape).background(gc.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Text(grade.grade, fontWeight = FontWeight.Black, fontSize = AmazeTheme.fontSize.xl, color = gc)
            }
            Spacer(Modifier.width(AmazeTheme.spacing.sm))
            Column(modifier = Modifier.weight(1f)) {
                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text("Grade for $label", fontWeight = FontWeight.Bold, color = colors.textPrimary, fontSize = AmazeTheme.fontSize.md)
                    Text("Total: ${grade.grandTotal}", fontSize = AmazeTheme.fontSize.sm, color = colors.textSecondary)
                }
                Spacer(Modifier.height(AmazeTheme.spacing.xs))
                grade.range?.let { range ->
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                        listOf("S" to range.S, "A" to range.A, "B" to range.B, "C" to range.C, "D" to range.D, "E" to range.E, "F" to range.F).forEach { (g, r) ->
                            val gColor = GradeColors[g] ?: colors.textMuted
                            Box(
                                modifier = Modifier.weight(1f).clip(RoundedCornerShape(AmazeTheme.radius.xs)).background(gColor.copy(alpha = 0.1f)).padding(vertical = 4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(g, fontWeight = FontWeight.Black, fontSize = AmazeTheme.fontSize.micro, color = gColor)
                                    Text(r, fontSize = AmazeTheme.fontSize.micro, color = colors.textMuted)
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
private fun MarksTab(
    group: CourseGroup,
    isEmbedded: Boolean,
    allGrades: AllGradesRes?,
    semesterId: String,
    colors: com.amazecc.app.shared.theme.AmazeColors
) {
    val theoryMarks = group.theory
    val labMarks = group.lab
    val allAssessments = remember(theoryMarks, labMarks) {
        val list = mutableListOf<Pair<String, List<AssessmentItem>>>()
        if (theoryMarks != null) list.add("Theory" to theoryMarks.assessments)
        if (labMarks != null) list.add("Lab" to labMarks.assessments)
        if (!isEmbedded && theoryMarks != null && labMarks == null) { list.clear(); list.add("Assessments" to theoryMarks.assessments) }
        list
    }

    val isRelative = theoryMarks?.courseSystem == "ACE" && (theoryMarks.courseType in listOf("Theory Only", "Embedded Theory", "Embedded Lab", "Embedded"))

    val allAsms = (theoryMarks?.assessments ?: emptyList()) + (labMarks?.assessments ?: emptyList())

    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp), contentPadding = PaddingValues(bottom = BOTTOM_NAV_PADDING)) {
        if (allAsms.isNotEmpty()) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    val totalWeighted = allAsms.sumOf { it.weightageMark.toDoubleOrNull() ?: 0.0 }
                    val totalWeightPct = allAsms.sumOf { it.weightagePercent.toDoubleOrNull() ?: 0.0 }
                    val projected = if (totalWeightPct > 0) (totalWeighted / totalWeightPct * 100).toInt() else 0
                    val maxPossible = 100 - (totalWeightPct - totalWeighted).toInt()
                    val maxScore = totalWeighted.toInt()

                    StatBox("Course Type", if (isEmbedded) "Embedded" else (theoryMarks?.courseType ?: "-"), colors, Modifier.weight(1f))
                    StatBox("Score", "$maxScore/${totalWeightPct.toInt()}", colors, Modifier.weight(1f))
                    StatBox("Projected", "$projected%", colors, Modifier.weight(1f))
                    StatBox("Max", "$maxPossible%", colors, Modifier.weight(1f))
                }
            }
        }

        allAssessments.forEach { (label, asms) ->
            if (asms.isEmpty()) {
                if (group.grade != null) {
                    item {
                        GradeViewCard(group.grade!!, label, colors)
                    }
                } else {
                    item {
                        AmazeCard(modifier = Modifier.fillMaxWidth()) {
                            Text("No assessments", color = colors.textMuted)
                        }
                    }
                }
            } else {
                item {
                    Text(label, style = AmazeTheme.typography.subheading.copy(fontWeight = FontWeight.Bold, color = colors.accent))
                }
                items(asms, key = { "${it.title}-${it.maxMark}" }) { asm ->
                    ExpandableAssessmentCard(asm, label, isRelative, colors)
                }
            }
        }

        if (allAsms.isNotEmpty()) {
            item {
                TargetGradeCalculator(theoryMarks, labMarks, isRelative, colors)
            }
        }
    }
}

@Composable
private fun ExpandableAssessmentCard(asm: AssessmentItem, typeLabel: String, isRelative: Boolean, colors: com.amazecc.app.shared.theme.AmazeColors) {
    var expanded by remember { mutableStateOf(false) }
    val pct = if (asm.maxMark.toDoubleOrNull() != null && asm.maxMark.toDouble() > 0)
        ((asm.scoredMark.toDoubleOrNull() ?: 0.0) / asm.maxMark.toDouble()) * 100 else 0.0
    val isTheory = typeLabel == "Theory"
    val accentColor = if (isTheory) colors.accent else colors.success
    val shortenedTitle = com.amazecc.app.shared.ui.components.shortenAssessmentName(asm.title)

    AmazeCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = { expanded = !expanded }
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(4.dp).clip(RoundedCornerShape(AmazeTheme.radius.xs)).background(accentColor))
                Spacer(Modifier.width(AmazeTheme.spacing.sm))
                Text(shortenedTitle, style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary), modifier = Modifier.weight(1f))
            }
            Spacer(Modifier.height(AmazeTheme.spacing.xs))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("${asm.scoredMark} / ${asm.maxMark}", fontWeight = FontWeight.Bold, color = colors.textPrimary, fontSize = AmazeTheme.fontSize.md)
                }
                Box(
                    modifier = Modifier.fillMaxWidth(0.5f).height(4.dp).clip(RoundedCornerShape(AmazeTheme.radius.xs)).background(colors.border)
                ) {
                    Box(
                        modifier = Modifier.fillMaxWidth().fillMaxHeight().clip(RoundedCornerShape(AmazeTheme.radius.xs))
                            .background(accentColor)
                    )
                }
                Spacer(Modifier.width(AmazeTheme.spacing.sm))
                Text("${asm.weightageMark} / ${asm.weightagePercent}%", fontWeight = FontWeight.Bold, color = accentColor, fontSize = AmazeTheme.fontSize.xs)
            }

            AnimatedVisibility(visible = expanded, enter = fadeIn(), exit = fadeOut()) {
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    Spacer(Modifier.height(AmazeTheme.spacing.sm))
                    Box(
                        modifier = Modifier.fillMaxWidth().height(1.dp).background(colors.border)
                    )
                    Spacer(Modifier.height(AmazeTheme.spacing.sm))

                    if (isRelative) {
                        Text("Relative Grading (ACE)", fontWeight = FontWeight.Bold, fontSize = AmazeTheme.fontSize.xs, color = colors.textSecondary)
                        Text("Class statistics required for actual grade boundaries.", fontSize = AmazeTheme.fontSize.micro, color = colors.textMuted)
                    }

                    Text("Grade Placement Preview", fontWeight = FontWeight.Bold, fontSize = AmazeTheme.fontSize.micro, color = colors.textMuted)
                    Spacer(Modifier.height(AmazeTheme.spacing.xs))
                    val gradePlacement = predictedGrade(pct)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                        GradeBoundariesAbsolute.take(4).forEach { (g, bound) ->
                            val gColor = GradeColors[g] ?: colors.textSecondary
                            val isCurrent = g == gradePlacement
                            Box(
                                modifier = Modifier.weight(1f)
                                    .clip(RoundedCornerShape(AmazeTheme.radius.xs))
                                    .background(if (isCurrent) gColor.copy(alpha = 0.2f) else colors.surface)
                                    .border(if (isCurrent) 1.dp else 0.dp, if (isCurrent) gColor else Color.Transparent, RoundedCornerShape(AmazeTheme.radius.xs))
                                    .padding(vertical = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(g, style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Black, color = gColor))
                                    Text("≥${bound}%", fontSize = AmazeTheme.fontSize.micro, color = colors.textMuted)
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(AmazeTheme.spacing.sm))
                    Text("Hypothetical Placement: Grade $gradePlacement", fontWeight = FontWeight.Bold, fontSize = AmazeTheme.fontSize.xs, color = colors.accent, modifier = Modifier.align(Alignment.CenterHorizontally))
                }
            }
        }
    }
}

@Composable
private fun TargetGradeCalculator(
    theoryMarks: MarksCourseItem?,
    labMarks: MarksCourseItem?,
    isRelative: Boolean,
    colors: com.amazecc.app.shared.theme.AmazeColors
) {
    var targetGrade by remember { mutableStateOf("A") }
    val allAsm = (theoryMarks?.assessments ?: emptyList()) + (labMarks?.assessments ?: emptyList())
    val totalWeighted = allAsm.sumOf { it.weightageMark.toDoubleOrNull() ?: 0.0 }
    val totalWeightPct = allAsm.sumOf { it.weightagePercent.toDoubleOrNull() ?: 0.0 }
    val remainingPct = 100.0 - totalWeightPct

    val targetBound = GradeBoundariesAbsolute.find { it.first == targetGrade }?.second ?: 70
    val needPoints = (targetBound.toDouble() / 100.0 * 100.0) - totalWeighted
    val maxAchievable = totalWeighted + remainingPct

    AmazeCard(modifier = Modifier.fillMaxWidth()) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Grade Insights", style = AmazeTheme.typography.subheading.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
                Spacer(Modifier.width(AmazeTheme.spacing.sm))
                AmazeBadge("BETA", variant = BadgeVariant.INFO)
            }
            Spacer(Modifier.height(AmazeTheme.spacing.sm))

            if (isRelative) {
                Text("Relative (ACE) Grading — Boundaries shift with class average", fontSize = AmazeTheme.fontSize.xs, color = colors.textMuted)
            } else {
                Text("Absolute Grading Enforced", fontSize = AmazeTheme.fontSize.xs, color = colors.success, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(AmazeTheme.spacing.xs))
                Box(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(AmazeTheme.radius.xs)).background(colors.success.copy(alpha = 0.08f)).padding(8.dp)
                ) {
                    Text("Fixed grade boundaries: S≥90, A≥80, B≥70, C≥60, D≥50, E≥40", fontSize = AmazeTheme.fontSize.micro, color = colors.success)
                }
            }

            Spacer(Modifier.height(AmazeTheme.spacing.sm))
            Text("Target Grade", fontWeight = FontWeight.Bold, fontSize = AmazeTheme.fontSize.sm, color = colors.textSecondary)
            Spacer(Modifier.height(AmazeTheme.spacing.xs))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf("S", "A", "B", "C", "D", "E").forEach { g ->
                    val sel = targetGrade == g
                    val gColor = GradeColors[g] ?: colors.textPrimary
                    Box(
                        modifier = Modifier.weight(1f).clip(RoundedCornerShape(AmazeTheme.radius.xs))
                            .background(if (sel) gColor else colors.surface)
                            .clickable { targetGrade = g }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(g, style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Black, color = if (sel) Color.White else gColor))
                    }
                }
            }

            Spacer(Modifier.height(AmazeTheme.spacing.sm))
            when {
                needPoints <= 0 -> Text("Target Achieved! 🎯", fontWeight = FontWeight.Bold, fontSize = AmazeTheme.fontSize.md, color = colors.success)
                needPoints > remainingPct -> Text("Impossible to achieve — need ${(needPoints * 10).toInt() / 10.0}pts but only ${remainingPct.toInt()}pts remaining", fontWeight = FontWeight.Bold, fontSize = AmazeTheme.fontSize.sm, color = colors.danger)
                else -> Text("Need ${(needPoints * 10).toInt() / 10.0} more weightage points out of ${remainingPct.toInt()} remaining", fontWeight = FontWeight.Bold, fontSize = AmazeTheme.fontSize.sm, color = colors.accent)
            }

            if (remainingPct > 0 && needPoints > 0 && needPoints <= remainingPct) {
                Spacer(Modifier.height(AmazeTheme.spacing.xs))
                LinearProgressIndicator(
                    progress = { (totalWeighted / 100.0).toFloat() },
                    modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(AmazeTheme.radius.xs)),
                    color = colors.success,
                    trackColor = colors.border,
                )
            }

            Spacer(Modifier.height(AmazeTheme.spacing.sm))
            Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
                GradeBoundariesAbsolute.forEach { (g, bound) ->
                    val gc = GradeColors[g] ?: colors.textMuted
                    Text(g, fontSize = AmazeTheme.fontSize.micro, fontWeight = FontWeight.Bold, color = gc)
                }
            }
            Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
                GradeBoundariesAbsolute.forEach { (_, bound) ->
                    Text("≥${bound}%", fontSize = AmazeTheme.fontSize.micro, color = colors.textMuted)
                }
            }
        }
    }
}

@Composable
private fun StatBox(label: String, value: String, colors: com.amazecc.app.shared.theme.AmazeColors, modifier: Modifier = Modifier) {
    AmazeCard(modifier = modifier) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Text(value, fontWeight = FontWeight.Black, fontSize = AmazeTheme.fontSize.lg, color = colors.accent)
            Text(label, fontSize = AmazeTheme.fontSize.micro, color = colors.textMuted)
        }
    }
}

@Composable
private fun AttendanceTab(
    courseCode: String,
    group: CourseGroup,
    theoryAtt: AttendanceItem?,
    labAtt: AttendanceItem?,
    mainAtt: AttendanceItem?,
    isEmbedded: Boolean,
    isPastSemester: Boolean,
    calendar: CalendarRes?,
    colors: com.amazecc.app.shared.theme.AmazeColors
) {
    var scope by remember(isEmbedded) { mutableStateOf(if (isEmbedded) "theory" else "all") }
    val activeAtt = when (scope) {
        "theory" -> theoryAtt ?: mainAtt
        "lab" -> labAtt ?: mainAtt
        else -> mainAtt ?: theoryAtt ?: labAtt
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        if (isEmbedded) {
            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("theory" to "Theory Component", "lab" to "Lab Component").forEach { (key, label) ->
                    val sel = scope == key
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(AmazeTheme.radius.medium))
                            .background(if (sel) colors.accent else colors.surface)
                            .border(1.dp, if (sel) colors.accent else colors.border, RoundedCornerShape(AmazeTheme.radius.medium))
                            .clickable { scope = key }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(label, color = if (sel) colors.background else colors.textSecondary, fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium, fontSize = AmazeTheme.fontSize.sm)
                    }
                }
            }
        }

        if (activeAtt != null) {
            Spacer(Modifier.height(8.dp))
            EmbeddedCourseAttendanceView(course = activeAtt)
        } else {
            Box(modifier = Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                Text("No attendance data available for this course", color = colors.textMuted)
            }
        }
    }
}

@Composable
private fun StatusInsightCard(attPct: Double, totalClasses: Int, attendedClasses: Int, isPast: Boolean, colors: com.amazecc.app.shared.theme.AmazeColors) {
    if (isPast) return

    val needed = if (attPct < 75.0) {
        val need = ((75.0 / 100.0 * (totalClasses + 1)) - attendedClasses).toInt().coerceAtLeast(0)
        Triple("Critical", "You need to attend $need more classes consecutively to reach 75%", colors.danger)
    } else if (attPct < 80.0) {
        val canMiss = (attendedClasses - (80.0 / 100.0 * (totalClasses + 1))).toInt().coerceAtLeast(0)
        Triple("On the Edge", "You cannot afford to miss many classes. Safe to miss: $canMiss", colors.warning)
    } else {
        val canMiss = (attendedClasses - (75.0 / 100.0 * (totalClasses + 1))).toInt().coerceAtLeast(0)
        Triple("Safe Margin", "You can safely miss up to $canMiss classes.", colors.success)
    }

    AmazeCard(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val icon = when (needed.first) {
                "Critical" -> Icons.Rounded.Dangerous
                "On the Edge" -> Icons.Rounded.Warning
                else -> Icons.Rounded.CheckCircle
            }
            Icon(icon, null, tint = needed.third, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(AmazeTheme.spacing.sm))
            Column(Modifier.weight(1f)) {
                Text(needed.first, style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = needed.third))
                Text(needed.second, color = colors.textSecondary, fontSize = AmazeTheme.fontSize.xs)
            }
        }
    }
}

@Composable
private fun PredictorSection(course: AttendanceItem, calendar: CalendarRes?, colors: com.amazecc.app.shared.theme.AmazeColors) {
    var mode by remember { mutableStateOf("CAT1") }
    var skipDates by remember { mutableStateOf<Set<Int>>(emptySet()) }

    val calendarMonths = calendar?.months ?: emptyList()
    val keyDates = remember(calendarMonths) {
        val map = mutableMapOf<String, Triple<Int, Int, Int>>()
        val monthIndex = mapOf("jan" to 1, "feb" to 2, "mar" to 3, "apr" to 4, "may" to 5, "jun" to 6, "jul" to 7, "aug" to 8, "sep" to 9, "oct" to 10, "nov" to 11, "dec" to 12)
        for (month in calendarMonths) {
            val m = month.month.lowercase().take(3)
            val monthNum = monthIndex[m] ?: continue
            val y = month.month.split(" ").lastOrNull()?.toIntOrNull() ?: continue
            for (day in month.days) {
                day.events.forEach { ev ->
                    val t = ev.text.lowercase()
                    when {
                        "cat i" in t || "cat 1" in t || "continuous assessment test - i" in t || "cat-i" in t -> map["CAT1"] = Triple(y, monthNum, day.date)
                        "cat ii" in t || "cat 2" in t || "continuous assessment test - ii" in t || "cat-ii" in t -> map["CAT2"] = Triple(y, monthNum, day.date)
                        "lid for laboratory" in t || ("last instructional day" in t && "laboratory" in t) -> map["LID_LAB"] = Triple(y, monthNum, day.date)
                        "lid for theory" in t || ("last instructional day" in t && "theory" in t) -> map["LID_TH"] = Triple(y, monthNum, day.date)
                    }
                }
            }
        }
        map
    }

    val isLab = course.courseType.contains("Lab", ignoreCase = true) || course.courseType.contains("Embedded", ignoreCase = true)

    val cutoff = when (mode) {
        "CAT1" -> keyDates["CAT1"]
        "CAT2" -> keyDates["CAT2"]
        "LID" -> if (isLab) keyDates["LID_LAB"] else keyDates["LID_TH"]
        else -> null
    }

    val courseDays = remember(course) {
        val slots = course.slotName.uppercase().split("+").map { it.trim() }.toSet()
        SlotMap.map.filterValues { daySlots -> daySlots.keys.any { it in slots } }.keys.toList()
    }

    val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
    val today = now.year * 10000 + now.monthNumber * 100 + now.dayOfMonth
    val allWorkingDays = remember(calendarMonths) {
        val results = mutableListOf<Triple<Int, Int, Int>>()
        val monthIndex = mapOf("jan" to 1, "feb" to 2, "mar" to 3, "apr" to 4, "may" to 5, "jun" to 6, "jul" to 7, "aug" to 8, "sep" to 9, "oct" to 10, "nov" to 11, "dec" to 12)
        for (month in calendarMonths) {
            val m = month.month.lowercase().take(3); val monthNum = monthIndex[m] ?: continue
            val y = month.month.split(" ").lastOrNull()?.toIntOrNull() ?: continue
            for (day in month.days) {
                val isWorking = day.events.any { it.type.lowercase() == "instructional day" || it.type.lowercase().contains("working") }
                val isHoliday = day.events.any { it.type.lowercase().contains("holiday") }
                if (isWorking && !isHoliday) results.add(Triple(y, monthNum, day.date))
            }
        }
        results
    }

    val futureClassDates = remember(allWorkingDays, courseDays, cutoff) {
        allWorkingDays.filter { (y, m, d) ->
            val dv = y * 10000 + m * 100 + d
            if (dv < today) return@filter false
            if (cutoff != null) {
                val cv = cutoff.first * 10000 + cutoff.second * 100 + cutoff.third
                if (dv > cv) return@filter false
            }
            val dt = try { LocalDate(y, m, d) } catch (_: Exception) { return@filter false }
            val abbr = when (dt.dayOfWeek) {
                DayOfWeek.MONDAY -> "MON"; DayOfWeek.TUESDAY -> "TUE"; DayOfWeek.WEDNESDAY -> "WED"
                DayOfWeek.THURSDAY -> "THU"; DayOfWeek.FRIDAY -> "FRI"; DayOfWeek.SATURDAY -> "SAT"
                else -> ""
            }
            abbr in courseDays
        }
    }

    val multiplier = if (isLab) 2 else 1
    val futureCount = futureClassDates.size * multiplier
    val skipCount = skipDates.size * multiplier

    val predictedAttended = course.attendedClasses + (futureCount - skipCount)
    val predictedTotal = course.totalClasses + futureCount
    val predictedPct = if (predictedTotal > 0) predictedAttended.toDouble() / predictedTotal * 100 else 0.0

    AmazeCard(modifier = Modifier.fillMaxWidth()) {
        Column {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("CAT1", "CAT2", "LID").forEach { m ->
                    val sel = mode == m
                    Box(modifier = Modifier.weight(1f).clip(RoundedCornerShape(AmazeTheme.radius.xs)).background(if (sel) colors.accent else colors.surface).clickable { mode = m }.padding(horizontal = 12.dp, vertical = 10.dp), contentAlignment = Alignment.Center) {
                        Text(m, color = if (sel) Color.White else colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = AmazeTheme.fontSize.xs)
                    }
                }
            }

            Spacer(Modifier.height(AmazeTheme.spacing.md))
            Text("Future classes before ${mode}: ${futureClassDates.size}", fontWeight = FontWeight.Bold, color = colors.textPrimary, fontSize = AmazeTheme.fontSize.sm)
            Spacer(Modifier.height(AmazeTheme.spacing.sm))

            futureClassDates.take(10).forEach { (y, m, d) ->
                val key = y * 10000 + m * 100 + d
                val skipped = key in skipDates
                Box(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(AmazeTheme.radius.xs)).background(if (skipped) colors.danger.copy(alpha = 0.1f) else colors.surface)
                        .clickable { skipDates = if (key in skipDates) skipDates - key else skipDates + key }.padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("$m/$d", fontSize = AmazeTheme.fontSize.sm, color = colors.textPrimary, modifier = Modifier.weight(1f))
                        if (skipped) Text("SKIP", color = colors.danger, fontSize = AmazeTheme.fontSize.micro, fontWeight = FontWeight.Bold)
                        else Text("ATTEND", color = colors.success, fontSize = AmazeTheme.fontSize.micro, fontWeight = FontWeight.Bold)
                    }
                }
            }

            if (futureClassDates.size > 10) {
                Text("+${futureClassDates.size - 10} more...", fontSize = AmazeTheme.fontSize.micro, color = colors.textMuted)
            }

            Spacer(Modifier.height(AmazeTheme.spacing.sm))
            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(colors.border))
            Spacer(Modifier.height(AmazeTheme.spacing.sm))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("Projected", fontSize = AmazeTheme.fontSize.xs, color = colors.textMuted)
                    Text("$predictedAttended / $predictedTotal", style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
                }
                Text("${predictedPct.toInt()}%", fontWeight = FontWeight.Black, fontSize = AmazeTheme.fontSize.x2l, color = when { predictedPct >= 85 -> colors.success; predictedPct >= 75 -> colors.warning; else -> colors.danger })
            }
        }
    }
}

@Composable
private fun StatChip(label: String, value: String, color: Color, colors: com.amazecc.app.shared.theme.AmazeColors) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontWeight = FontWeight.Bold, fontSize = AmazeTheme.fontSize.xl, color = color)
        Text(label, fontSize = AmazeTheme.fontSize.micro, color = colors.textSecondary)
    }
}

@Composable
private fun CoursePlanTab(
    courseCode: String,
    theory: MarksCourseItem?,
    lab: MarksCourseItem?,
    mainAtt: AttendanceItem?,
    colors: com.amazecc.app.shared.theme.AmazeColors
) {
    var syllabusFile by remember { mutableStateOf<SyllabusDownload?>(null) }
    var syllabusLoading by remember { mutableStateOf(false) }
    var syllabusError by remember { mutableStateOf<String?>(null) }
    var showSchedule by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val saveFile = rememberFileSaver()
    val launchScope = scope

    LaunchedEffect(courseCode) {
        syllabusLoading = true
        try {
            val download = AmazeClient.getSyllabusPdf(courseCode)
            syllabusFile = download
            if (download == null) syllabusError = "No syllabus available"
        } catch (e: Exception) { syllabusError = e.message }
        syllabusLoading = false
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = BOTTOM_NAV_PADDING)
    ) {
        item {
            AmazeCard(modifier = Modifier.fillMaxWidth()) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Course Syllabus", style = AmazeTheme.typography.subheading.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
                            Text(courseCode, fontSize = AmazeTheme.fontSize.xs, color = colors.textMuted)
                        }
                        val sb = syllabusFile
                        val se = syllabusError
                        when {
                            syllabusLoading -> CircularProgressIndicator(color = colors.accent, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                            sb != null -> {
                                var downloadMsg by remember { mutableStateOf<String?>(null) }
                                IconButton(onClick = {
                                    scope.launch {
                                        val saved = saveFile("${courseCode}_syllabus.${sb.extension}", sb.bytes)
                                        downloadMsg = if (saved) "Saved!" else "Failed to save"
                                        delay(2.seconds)
                                        downloadMsg = null
                                    }
                                }) {
                                    Icon(Icons.Rounded.Download, "Download Syllabus", tint = colors.accent)
                                }
                                downloadMsg?.let {
                                    Text(it, fontSize = AmazeTheme.fontSize.micro, color = if (it == "Saved!") colors.success else colors.danger)
                                }
                            }
                            se != null -> Text(se, fontSize = AmazeTheme.fontSize.micro, color = colors.danger)
                        }
                    }
                    Spacer(Modifier.height(AmazeTheme.spacing.sm))
                    Box(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(AmazeTheme.radius.small)).background(colors.accent.copy(alpha = 0.08f)).padding(12.dp)
                    ) {
                        Column {
                            Text("Course Info", fontWeight = FontWeight.Bold, fontSize = AmazeTheme.fontSize.xs, color = colors.accent)
                            Spacer(Modifier.height(AmazeTheme.spacing.xs))
                            theory?.let { Text("Theory: ${it.courseType} — ${it.slot}", fontSize = AmazeTheme.fontSize.xs, color = colors.textSecondary) }
                            lab?.let { Text("Lab: ${lab.courseType} — ${lab.slot}", fontSize = AmazeTheme.fontSize.xs, color = colors.textSecondary) }
                            mainAtt?.let { Text("Slot: ${it.slotName}", fontSize = AmazeTheme.fontSize.xs, color = colors.textSecondary) }
                        }
                    }
                }
            }
        }

        item {
            AmazeCard(modifier = Modifier.fillMaxWidth()) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Weekly Schedule", style = AmazeTheme.typography.subheading.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary), modifier = Modifier.weight(1f))
                        AmazeButton(
                            if (showSchedule) "Hide" else "Show",
                            onClick = { showSchedule = !showSchedule },
                            variant = ButtonVariant.SECONDARY,
                            modifier = Modifier.height(32.dp)
                        )
                    }

                    if (showSchedule) {
                        Spacer(Modifier.height(AmazeTheme.spacing.sm))
                        val days = listOf("MON", "TUE", "WED", "THU", "FRI", "SAT")
                        days.forEach { day ->
                            Row(
                                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(AmazeTheme.radius.xs)).background(colors.surface).padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(day, fontWeight = FontWeight.Bold, fontSize = AmazeTheme.fontSize.micro, color = colors.accent, modifier = Modifier.width(40.dp))
                                Text(mainAtt?.slotName?.take(4) ?: "-", fontSize = AmazeTheme.fontSize.micro, color = colors.textSecondary)
                                Spacer(Modifier.width(AmazeTheme.spacing.sm))
                                Text(mainAtt?.slotVenue ?: "-", fontSize = AmazeTheme.fontSize.micro, color = colors.textMuted)
                            }
                            Spacer(Modifier.height(AmazeTheme.spacing.xs))
                        }
                    }
                }
            }
        }

        item {
            AmazeCard(modifier = Modifier.fillMaxWidth()) {
                Column {
                    Text("Quick Actions", style = AmazeTheme.typography.subheading.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
                    Spacer(Modifier.height(AmazeTheme.spacing.sm))
                    val sb = syllabusFile
                    if (sb != null) {
                        AmazeButton(
                            "Download Syllabus",
                            onClick = {
                                scope.launch {
                                    saveFile("${courseCode}_syllabus.${sb.extension}", sb.bytes)
                                }
                            },
                            icon = Icons.Rounded.PictureAsPdf,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun QBankTab(courseCode: String, colors: com.amazecc.app.shared.theme.AmazeColors) {
    val scope = rememberCoroutineScope()
    val semesterMap by AppState.semesterMap.collectAsState()
    var qbTab by remember { mutableStateOf("questions") }
    var questions by remember { mutableStateOf<List<QBankQuestion>>(emptyList()) }
    var questionsLoading by remember { mutableStateOf(true) }
    var questionsError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(courseCode) {
        questionsLoading = true
        try {
            val res = AmazeClient.getQBankQuestions(courseCode)
            if (res.success) questions = res.data else questionsError = res.message
        } catch (e: Exception) { questionsError = e.message }
        questionsLoading = false
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("papers" to "Papers", "questions" to "Questions").forEach { (key, label) ->
                val sel = qbTab == key
                Box(
                    modifier = Modifier.clip(RoundedCornerShape(AmazeTheme.radius.xs)).background(if (sel) colors.accent else colors.surface)
                        .clickable { qbTab = key }.padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text(label, color = if (sel) colors.background else colors.textSecondary, fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal, fontSize = AmazeTheme.fontSize.sm)
                }
            }
        }

        when (qbTab) {
            "papers" -> {
                var showUpload by remember { mutableStateOf(false) }
                var paperLink by remember { mutableStateOf("") }
                var paperTitle by remember { mutableStateOf("") }
                var paperType by remember { mutableStateOf("CAT 1") }
                var uploadStatus by remember { mutableStateOf<String?>(null) }
                var uploading by remember { mutableStateOf(false) }

                LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = BOTTOM_NAV_PADDING)) {
                    item {
                        AmazeButton(
                            if (showUpload) Strings.cancel else "Upload Paper",
                            onClick = { showUpload = !showUpload },
                            icon = if (!showUpload) Icons.Rounded.UploadFile else null,
                            variant = if (showUpload) ButtonVariant.GHOST else ButtonVariant.PRIMARY,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    if (showUpload) {
                        item {
                            AmazeCard(modifier = Modifier.fillMaxWidth()) {
                                Column {
                                    Text("Share a Paper", style = AmazeTheme.typography.subheading.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
                                    Spacer(Modifier.height(AmazeTheme.spacing.sm))
                                    OutlinedTextField(
                                        value = paperTitle, onValueChange = { paperTitle = it },
                                        label = { Text("Title") },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(AmazeTheme.radius.small),
                                        singleLine = true,
                                        colors = OutlinedTextFieldDefaults.colors(focusedContainerColor = colors.surface, unfocusedContainerColor = colors.surface, focusedBorderColor = colors.accent, unfocusedBorderColor = colors.border, focusedTextColor = colors.textPrimary, unfocusedTextColor = colors.textPrimary)
                                    )
                                    Spacer(Modifier.height(AmazeTheme.spacing.sm))
                                    OutlinedTextField(
                                        value = paperLink, onValueChange = { paperLink = it },
                                        label = { Text("Link (GDrive, Dropbox...)") },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(AmazeTheme.radius.small),
                                        singleLine = true,
                                        colors = OutlinedTextFieldDefaults.colors(focusedContainerColor = colors.surface, unfocusedContainerColor = colors.surface, focusedBorderColor = colors.accent, unfocusedBorderColor = colors.border, focusedTextColor = colors.textPrimary, unfocusedTextColor = colors.textPrimary)
                                    )
                                    Spacer(Modifier.height(AmazeTheme.spacing.sm))
                                    Text("Paper Type", fontSize = AmazeTheme.fontSize.xs, color = colors.textSecondary)
                                    Spacer(Modifier.height(AmazeTheme.spacing.xs))
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        listOf("CAT 1", "CAT 2", "FAT", "Quiz", "Assignment").forEach { t ->
                                            val sel = paperType == t
                                            Box(
                                                modifier = Modifier.weight(1f).clip(RoundedCornerShape(AmazeTheme.radius.xs))
                                                    .background(if (sel) colors.accent else colors.surface)
                                                    .clickable { paperType = t }.padding(vertical = 6.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(t, color = if (sel) Color.White else colors.textPrimary, fontSize = AmazeTheme.fontSize.micro, fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal)
                                            }
                                        }
                                    }
                                    Spacer(Modifier.height(AmazeTheme.spacing.sm))
                                    AmazeButton(
                                        if (uploading) "Uploading..." else Strings.submit,
                                            onClick = {
                                                uploading = true
                                                uploadStatus = null
                                                scope.launch {
                                                    try {
                                                        val res = AmazeClient.postQBankPaper(courseCode, paperTitle, paperLink, paperType)
                                                        uploadStatus = if (res?.success == true) "Paper uploaded!" else res?.message ?: "Upload failed"
                                                    } catch (e: Exception) { uploadStatus = "Error: ${e.message}" }
                                                    uploading = false
                                                }
                                            },
                                        enabled = paperTitle.isNotBlank() && paperLink.isNotBlank() && !uploading,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    uploadStatus?.let {
                                        Spacer(Modifier.height(AmazeTheme.spacing.sm))
                                        val isSuccess = it.contains("uploaded", ignoreCase = true) || it.contains("success", ignoreCase = true)
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                if (isSuccess) Icons.Rounded.CheckCircle else Icons.Rounded.Error,
                                                null, tint = if (isSuccess) colors.success else colors.danger, modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(Modifier.width(AmazeTheme.spacing.xs))
                                            Text(it, color = if (isSuccess) colors.success else colors.danger, fontSize = AmazeTheme.fontSize.xs)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    item {
                        Text("No papers available yet.", color = colors.textMuted, fontSize = AmazeTheme.fontSize.sm)
                    }
                }
            }
            "questions" -> {
                Box(modifier = Modifier.fillMaxSize()) {
                    val qe = questionsError
                    when {
                        questionsLoading -> CircularProgressIndicator(color = colors.accent, modifier = Modifier.align(Alignment.Center))
                        qe != null -> Text(qe, color = colors.danger, modifier = Modifier.align(Alignment.Center))
                        questions.isEmpty() -> Text("No questions available for $courseCode", color = colors.textMuted, modifier = Modifier.align(Alignment.Center))
                        else -> LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = BOTTOM_NAV_PADDING)) {
                            items(questions) { q ->
                                AmazeCard(
                                    modifier = Modifier.fillMaxWidth(),
                                    onClick = { AppState.openQBankCourse(courseCode) }
                                ) {
                                    Column {
                                        Text(q.question_text, style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
                                        q.topic_name?.let {
                                            Spacer(Modifier.height(AmazeTheme.spacing.xs))
                                            Text("Topic: $it", color = colors.textSecondary, fontSize = AmazeTheme.fontSize.sm)
                                        }
                                        Spacer(Modifier.height(AmazeTheme.spacing.xs))
                                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            AmazeBadge(q.question_type, variant = BadgeVariant.INFO)
                                            q.exam_semester?.let { AmazeBadge(it, variant = BadgeVariant.INFO) }
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

private fun findCourseGroup(
    courseCode: String,
    semesterId: String,
    allSemesterMarks: Map<String, MarksRes>,
    allSemesterAttendance: Map<String, AttendanceRes?>,
    marksRes: MarksRes?,
    attendanceRes: AttendanceRes?,
    timetable: TimetableRes?
): CourseGroup? {
    val cleanCode = courseCode.replace(Regex("\\([LPT]\\)$"), "").trim()

    val currentMarksCourses = marksRes?.marks ?: allSemesterMarks[semesterId]?.marks ?: emptyList()
    val currentAttList = attendanceRes?.attendance ?: allSemesterAttendance[semesterId]?.attendance ?: emptyList()

    val theoryM = currentMarksCourses.find { it.courseCode.replace(Regex("\\([LPT]\\)$"), "").trim() == cleanCode && !it.courseType.lowercase().contains("lab") }
    val labM = currentMarksCourses.find { it.courseCode.replace(Regex("\\([LPT]\\)$"), "").trim() == cleanCode && it.courseType.lowercase().contains("lab") }
    val theoryA = currentAttList.find { it.courseCode.replace(Regex("\\([LPT]\\)$"), "").trim() == cleanCode && !it.courseType.lowercase().contains("lab") }
    val labA = currentAttList.find { it.courseCode.replace(Regex("\\([LPT]\\)$"), "").trim() == cleanCode && it.courseType.lowercase().contains("lab") }

    if (theoryM != null || labM != null || theoryA != null || labA != null) {
        val semName = AppState.semesterMap.value[semesterId] ?: semesterId
        return CourseGroup(cleanCode, (theoryM?.courseTitle ?: labM?.courseTitle ?: theoryA?.courseTitle ?: labA?.courseTitle ?: cleanCode), semesterId, semName, theoryM, labM, theoryA, labA)
    }

    for ((semId, marks) in allSemesterMarks) {
        val attList = allSemesterAttendance[semId]?.attendance ?: emptyList()
        val tM = marks.marks.find { it.courseCode.replace(Regex("\\([LPT]\\)$"), "").trim() == cleanCode && !it.courseType.lowercase().contains("lab") }
        val lM = marks.marks.find { it.courseCode.replace(Regex("\\([LPT]\\)$"), "").trim() == cleanCode && it.courseType.lowercase().contains("lab") }
        val tA = attList.find { it.courseCode.replace(Regex("\\([LPT]\\)$"), "").trim() == cleanCode && !it.courseType.lowercase().contains("lab") }
        val lA = attList.find { it.courseCode.replace(Regex("\\([LPT]\\)$"), "").trim() == cleanCode && it.courseType.lowercase().contains("lab") }
        if (tM != null || lM != null || tA != null || lA != null) {
            val semName = AppState.semesterMap.value[semId] ?: semId
            return CourseGroup(cleanCode, (tM?.courseTitle ?: lM?.courseTitle ?: tA?.courseTitle ?: lA?.courseTitle ?: cleanCode), semId, semName, tM, lM, tA, lA)
        }
    }

    // Check allSemesterAttendance for semesters not in allSemesterMarks
    for ((semId, att) in allSemesterAttendance) {
        if (semId in allSemesterMarks) continue
        val attList = att?.attendance ?: emptyList()
        val tA = attList.find { it.courseCode.replace(Regex("\\([LPT]\\)$"), "").trim() == cleanCode && !it.courseType.lowercase().contains("lab") }
        val lA = attList.find { it.courseCode.replace(Regex("\\([LPT]\\)$"), "").trim() == cleanCode && it.courseType.lowercase().contains("lab") }
        if (tA != null || lA != null) {
            val semName = AppState.semesterMap.value[semId] ?: semId
            return CourseGroup(cleanCode, (tA?.courseTitle ?: lA?.courseTitle ?: cleanCode), semId, semName, theoryAtt = tA, labAtt = lA)
        }
    }

    val allGradesRes = AppState.allGrades.value
    val gradesMap = allGradesRes?.grades
    if (gradesMap != null) {
        for ((semId, semResult) in gradesMap) {
            if (semId == "curriculum" || semId == "effectiveGrades") continue
            semResult?.grades?.forEach { grade ->
                val gCleanCode = grade.courseCode.replace(Regex("\\([LPT]\\)$"), "").trim()
                if (gCleanCode == cleanCode) {
                    val semName = AppState.semesterMap.value[semId] ?: semId
                    // Check if this semester has marks/attendance available in the maps
                    val semMarks = allSemesterMarks[semId]?.marks ?: emptyList()
                    val semAtt = allSemesterAttendance[semId]?.attendance ?: emptyList()
                    val tM = semMarks.find { it.courseCode.replace(Regex("\\([LPT]\\)$"), "").trim() == cleanCode && !it.courseType.lowercase().contains("lab") }
                    val lM = semMarks.find { it.courseCode.replace(Regex("\\([LPT]\\)$"), "").trim() == cleanCode && it.courseType.lowercase().contains("lab") }
                    val tA = semAtt.find { it.courseCode.replace(Regex("\\([LPT]\\)$"), "").trim() == cleanCode && !it.courseType.lowercase().contains("lab") }
                    val lA = semAtt.find { it.courseCode.replace(Regex("\\([LPT]\\)$"), "").trim() == cleanCode && it.courseType.lowercase().contains("lab") }
                    return CourseGroup(
                        courseCode = cleanCode,
                        courseTitle = grade.courseTitle,
                        semesterSubId = semId,
                        semesterName = semName,
                        theory = tM ?: MarksCourseItem(courseCode = gCleanCode, courseTitle = grade.courseTitle, courseType = grade.courseType),
                        lab = lM,
                        theoryAtt = tA,
                        labAtt = lA,
                        grade = grade
                    )
                }
            }
        }
    }

    return null
}

private fun extractQcmTables(data: JsonElement?): List<QcmTable> {
    if (data == null) return emptyList()
    return when (data) {
        is JsonArray -> data.map { element ->
            val obj = element.jsonObject
            QcmTable(
                caption = obj["caption"]?.jsonPrimitive?.contentOrNull ?: "",
                rows = (obj["rows"]?.jsonArray?.toList() ?: emptyList())
            )
        }

        is JsonObject -> data.values.flatMap { value ->
            val rows = value.jsonObject["rows"]?.jsonArray?.toList() ?: emptyList()
            if (rows.isNotEmpty()) listOf(QcmTable(rows = rows)) else emptyList()
        }

        else -> emptyList()
    }
}