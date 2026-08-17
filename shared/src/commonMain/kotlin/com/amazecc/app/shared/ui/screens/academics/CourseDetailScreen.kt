package com.amazecc.app.shared.ui.screens.academics

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amazecc.app.shared.api.AmazeClient
import com.amazecc.app.shared.api.SyllabusDownload
import com.amazecc.app.shared.api.SyllabusResult
import com.amazecc.app.shared.config.SlotMap
import com.amazecc.app.shared.repository.SettingsManager
import com.amazecc.app.shared.model.*
import com.amazecc.app.shared.state.AcademicData
import com.amazecc.app.shared.state.AcademicDerivers
import com.amazecc.app.shared.state.AcademicDerivers.toGradeItem
import com.amazecc.app.shared.state.AppState
import com.amazecc.app.shared.state.Screen
import com.amazecc.app.shared.theme.AmazeTheme
import com.amazecc.app.shared.ui.components.AppBackHandler
import com.amazecc.app.shared.ui.components.BOTTOM_NAV_PADDING
import com.amazecc.app.shared.ui.components.AmazeCard
import com.amazecc.app.shared.ui.components.AmazeButton
import com.amazecc.app.shared.ui.components.AmazeBadge
import com.amazecc.app.shared.ui.components.BadgeVariant
import com.amazecc.app.shared.ui.components.ButtonVariant
import com.amazecc.app.shared.ui.components.ScreenHeader
import com.amazecc.app.shared.ui.components.HeaderSpacer
import com.amazecc.app.shared.ui.components.bouncySpring
import com.amazecc.app.shared.ui.components.HeroCard
import com.amazecc.app.shared.ui.components.HeroChip
import com.amazecc.app.shared.ui.components.HeroPalette
import com.amazecc.app.shared.ui.components.HeroPanel
import com.amazecc.app.shared.ui.components.HeroStat
import com.amazecc.app.shared.utils.toFixed
import com.amazecc.app.shared.ui.components.QBankCourseWorkspace
import com.amazecc.app.shared.ui.screens.settings.SettingsGroupCard
import com.amazecc.app.shared.ui.screens.settings.SettingsRow
import com.amazecc.app.shared.ui.screens.settings.SettingsRowDivider
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

private val GradeBoundariesAbsolute = listOf(
    "S" to 90, "A" to 80, "B" to 70, "C" to 60, "D" to 50, "E" to 40, "F" to 0
)

private fun gradeColor(g: String, colors: com.amazecc.app.shared.theme.AmazeColors): Color = when (g.uppercase()) {
    "S" -> colors.success
    "A" -> colors.accent
    "B" -> colors.warning
    "C" -> colors.chart1
    "D" -> colors.chart3
    "E" -> colors.chart4
    "F" -> colors.danger
    else -> colors.textMuted
}

private enum class GradingMode(val label: String) {
    ABSOLUTE("Absolute"),
    RELATIVE("Relative")
}

private data class CourseGrading(val mode: GradingMode, val reason: String)

private fun courseGrading(group: CourseGroup, theoryMarks: MarksCourseItem?, labMarks: MarksCourseItem?): CourseGrading {
    val type = (theoryMarks?.courseType ?: labMarks?.courseType ?: "").lowercase()
    return when {
        theoryMarks == null && labMarks != null -> CourseGrading(GradingMode.ABSOLUTE, "Lab-only courses use absolute grading.")
        type.contains("project") -> CourseGrading(GradingMode.ABSOLUTE, "Project courses use absolute grading.")
        group.courseCode.contains("STS", ignoreCase = true) -> CourseGrading(GradingMode.ABSOLUTE, "STS skill courses use absolute grading.")
        theoryMarks != null && labMarks != null -> CourseGrading(GradingMode.RELATIVE, "Embedded courses use relative grading — final boundaries are set against the class average.")
        else -> CourseGrading(GradingMode.RELATIVE, "Theory-only courses use relative grading — final boundaries are set against the class average.")
    }
}

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

private enum class CourseSubPage(
    val title: String,
    val description: String,
    val icon: ImageVector
) {
    GRADES("Grade History", "Grades across semesters", Icons.Rounded.History),
    MARKS("Marks", "Assessment-wise marks & grade insights", Icons.Rounded.Assessment),
    ATTENDANCE("Attendance", "Daily records & predictor", Icons.Rounded.CheckCircle),
    PLAN("Course Plan", "Syllabus, QCM & assessments", Icons.AutoMirrored.Rounded.MenuBook),
    QBANK("QBank", "Question bank workspace", Icons.Rounded.Folder),
    TASKS("Tasks", "Course tasks & reminders", Icons.AutoMirrored.Rounded.Assignment),
    FACULTY("Faculty", "Faculty profile & contact", Icons.Rounded.Person),
    FREE_SLOTS("Free Slots", "Weekly availability schedule", Icons.Rounded.CalendarMonth)
}

@Composable
private fun CourseSubPageTint(sub: CourseSubPage, colors: com.amazecc.app.shared.theme.AmazeColors): Color = when (sub) {
    CourseSubPage.GRADES -> colors.chart4
    CourseSubPage.MARKS -> colors.chart1
    CourseSubPage.ATTENDANCE -> colors.success
    CourseSubPage.PLAN -> colors.chart3
    CourseSubPage.QBANK -> colors.info
    CourseSubPage.TASKS -> colors.warning
    CourseSubPage.FACULTY -> colors.accent
    CourseSubPage.FREE_SLOTS -> colors.chart2
}

@Composable
fun CourseDetailScreen(onBack: () -> Unit) {
    val colors = AmazeTheme.colors
    val courseCode = AppState.selectedCourseCode.value ?: ""
    val semesterId = AppState.selectedCourseSemester.value
    val academic by AppState.academic.collectAsState()
    val selectedSemester by AppState.selectedSemester.collectAsState()
    val calendar by AppState.calendar.collectAsState()

    val currentSemesterId = selectedSemester
    val mainSemesterId = semesterId ?: currentSemesterId

    val group = remember(courseCode, mainSemesterId, academic, selectedSemester) {
        findCourseGroup(courseCode, mainSemesterId, academic, selectedSemester)
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

    var subPage by remember { mutableStateOf<CourseSubPage?>(null) }

    AppBackHandler(enabled = subPage != null || facultyView != null) {
        if (facultyView != null) {
            facultyView = null
        } else {
            subPage = null
        }
    }

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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        ScreenHeader(
            title = subPage?.title ?: group.courseCode,
            description = subPage?.description ?: group.courseTitle,
            showBackButton = true,
            showSyncButton = false,
            onBackOverride = if (subPage != null) { { subPage = null } } else null,
            enabledScreens = setOf(Screen.COURSE_DETAIL)
        )

        Column(modifier = Modifier.fillMaxSize()) {
            HeaderSpacer()

            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                AnimatedContent(
                    targetState = subPage,
                    transitionSpec = {
                        if (targetState == null) {
                            (slideInHorizontally { -it / 3 } + fadeIn()) togetherWith
                                    (slideOutHorizontally { it / 3 } + fadeOut())
                        } else {
                            (slideInHorizontally { it / 3 } + fadeIn()) togetherWith
                                    (slideOutHorizontally { -it / 3 } + fadeOut())
                        }
                    },
                    label = "courseSubNav"
                ) { sub ->
                    if (sub == null) {
                        CourseOverviewPage(
                            group = group,
                            theoryAtt = theoryAtt,
                            labAtt = labAtt,
                            mainAtt = mainAtt,
                            isEmbedded = isEmbedded,
                            isPastSemester = isPastSemester,
                            facultyLoading = facultyLoading,
                            onViewFaculty = onViewFaculty,
                            colors = colors,
                            onOpenSubPage = { subPage = it }
                        )
                    } else {
                        when (sub) {
                            CourseSubPage.GRADES -> GradeHistoryTab(courseCode, academic, group, colors)
                            CourseSubPage.MARKS -> MarksTab(group, isEmbedded, colors)
                            CourseSubPage.ATTENDANCE -> AttendanceTab(courseCode, group, theoryAtt, labAtt, mainAtt, isEmbedded, isPastSemester, calendar, colors)
                            CourseSubPage.PLAN -> CoursePlanTab(group, colors)
                            CourseSubPage.QBANK -> QBankCourseWorkspace(
                                courseCode = courseCode,
                                courseTitle = group.courseTitle,
                                embedded = true,
                                onExit = { subPage = null }
                            )
                            CourseSubPage.TASKS -> {
                                val taskCodes = buildList {
                                    add(courseCode)
                                    group.theory?.courseCode?.let { if (it != courseCode) add(it) }
                                    group.lab?.courseCode?.let { if (it != courseCode) add(it) }
                                }
                                CourseTasksTab(taskCodes, group.courseTitle, colors)
                            }
                            CourseSubPage.FACULTY -> FacultyTab(group, colors)
                            CourseSubPage.FREE_SLOTS -> FreeSlotsTab(group, colors)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CourseOverviewPage(
    group: CourseGroup,
    theoryAtt: AttendanceItem?,
    labAtt: AttendanceItem?,
    mainAtt: AttendanceItem?,
    isEmbedded: Boolean,
    isPastSemester: Boolean,
    facultyLoading: Boolean,
    onViewFaculty: (ParsedFaculty) -> Unit,
    colors: com.amazecc.app.shared.theme.AmazeColors,
    onOpenSubPage: (CourseSubPage) -> Unit
) {
    val moodleAssignments = remember(group) {
        AppState.getMoodleAssignmentsForCourse(group.courseCode)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(bottom = BOTTOM_NAV_PADDING),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Attendance hero — arcs for every component + marks / grade
        AttendanceHeroCard(group, theoryAtt, labAtt, mainAtt, isEmbedded, isPastSemester, colors)

        // 2. Menu — replaces the pill tab bar
        SettingsGroupCard {
            CourseSubPage.entries.forEachIndexed { index, sub ->
                SettingsRow(
                    icon = sub.icon,
                    title = sub.title,
                    subtitle = sub.description,
                    tint = CourseSubPageTint(sub, colors),
                    onClick = { onOpenSubPage(sub) }
                )
                if (index < CourseSubPage.entries.lastIndex) SettingsRowDivider()
            }
        }

        // 3. Grouped info cards
        CourseDetailsInfoCard(group, theoryAtt, labAtt, mainAtt, isEmbedded, facultyLoading, onViewFaculty, colors)

        if (moodleAssignments.isNotEmpty()) {
            MoodleAssignmentsCard(moodleAssignments, colors)
        }
    }
}

@Composable
private fun AttendanceHeroCard(
    group: CourseGroup,
    theoryAtt: AttendanceItem?,
    labAtt: AttendanceItem?,
    mainAtt: AttendanceItem?,
    isEmbedded: Boolean,
    isPastSemester: Boolean,
    colors: com.amazecc.app.shared.theme.AmazeColors
) {
    val attItem = if (isEmbedded) theoryAtt else mainAtt
    val attPct = attItem?.attendancePercentage?.replace("%", "")?.trim()?.toDoubleOrNull() ?: 0.0
    val (healthLabel, _, _) = healthStatus(attPct, predictedGrade(0.0), isPastSemester, colors)

    val assessments = (group.theory?.assessments ?: emptyList()) + (group.lab?.assessments ?: emptyList())
    val totalWeighted = assessments.sumOf { it.weightageMark.toDoubleOrNull() ?: 0.0 }
    val totalWeightPct = assessments.sumOf { it.weightagePercent.toDoubleOrNull() ?: 0.0 }
    val projectedPct = if (totalWeightPct > 0) (totalWeighted / totalWeightPct * 100).toInt() else 0

    HeroCard(colors = colors, modifier = Modifier.fillMaxWidth()) { p ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.CheckCircle, null, tint = p.text, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                "Attendance",
                color = p.textSecondary,
                style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold)
            )
            Spacer(Modifier.weight(1f))
            HeroChip(text = healthLabel, p = p)
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            if (isEmbedded) {
                HeroArc("Theory", theoryAtt, p, Modifier.weight(1f))
                HeroArc("Lab", labAtt, p, Modifier.weight(1f))
            } else {
                HeroArc("Attendance", mainAtt, p, Modifier.weight(1f))
            }
        }

        HeroPanel(p = p, modifier = Modifier.fillMaxWidth()) {
            when {
                assessments.isNotEmpty() -> {
                    Text(
                        "Marks Earned",
                        color = p.textSecondary,
                        fontWeight = FontWeight.Bold,
                        fontSize = AmazeTheme.fontSize.sm
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        HeroStat("Scored", "${totalWeighted.toInt()}", p.text)
                        HeroStat("Weight", "${totalWeightPct.toInt()}%", p.textSecondary)
                        HeroStat("Projected", "$projectedPct%", p.textSecondary)
                    }
                    LinearProgressIndicator(
                        progress = { if (totalWeightPct > 0) (totalWeighted / totalWeightPct).toFloat() else 0f },
                        modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(AmazeTheme.radius.xs)),
                        color = p.progress,
                        trackColor = p.progressTrack
                    )
                }
                group.grade != null -> {
                    val gradeItem = group.grade!!
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier.size(52.dp).clip(CircleShape).background(p.iconBg),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(gradeItem.grade, color = p.text, fontWeight = FontWeight.Black, fontSize = AmazeTheme.fontSize.xl)
                        }
                        Spacer(Modifier.width(AmazeTheme.spacing.md))
                        Column {
                            Text("Grade Published", color = p.textSecondary, fontWeight = FontWeight.Bold, fontSize = AmazeTheme.fontSize.sm)
                            Text("Total: ${gradeItem.grandTotal}%", color = p.text, fontWeight = FontWeight.Bold, fontSize = AmazeTheme.fontSize.md)
                        }
                    }
                }
                else -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Schedule, null, tint = p.textSecondary, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Marks not published yet",
                            color = p.textSecondary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = AmazeTheme.fontSize.sm
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HeroArc(label: String, att: AttendanceItem?, p: HeroPalette, modifier: Modifier = Modifier) {
    val pct = att?.attendancePercentage?.replace("%", "")?.trim()?.toDoubleOrNull() ?: 0.0
    val animatedPct by animateFloatAsState(targetValue = (pct / 100f).toFloat(), animationSpec = tween(1000))
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(64.dp)) {
            Canvas(modifier = Modifier.size(64.dp)) {
                drawArc(color = p.progressTrack, startAngle = -90f, sweepAngle = 360f, useCenter = false, style = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round))
                drawArc(color = p.progress, startAngle = -90f, sweepAngle = 360f * animatedPct, useCenter = false, style = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round))
            }
            Text("${pct.toInt()}%", fontWeight = FontWeight.Black, fontSize = AmazeTheme.fontSize.lg, color = p.text)
        }
        Spacer(Modifier.height(AmazeTheme.spacing.xs))
        Text(label, fontSize = AmazeTheme.fontSize.micro, color = p.textSecondary, fontWeight = FontWeight.Bold)
        if (att != null) {
            Text("${att.attendedClasses}/${att.totalClasses}", fontSize = AmazeTheme.fontSize.micro, color = p.statLabel)
        }
    }
}

@Composable
private fun CourseDetailsInfoCard(
    group: CourseGroup,
    theoryAtt: AttendanceItem?,
    labAtt: AttendanceItem?,
    mainAtt: AttendanceItem?,
    isEmbedded: Boolean,
    facultyLoading: Boolean,
    onViewFaculty: (ParsedFaculty) -> Unit,
    colors: com.amazecc.app.shared.theme.AmazeColors
) {
    AmazeCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text("Course Details", style = AmazeTheme.typography.subheading.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
            Spacer(Modifier.height(12.dp))
            val mainCourse = group.theory ?: group.lab
            val rawFaculty = mainCourse?.faculty?.ifBlank { null }
                ?: theoryAtt?.faculty?.ifBlank { null }
                ?: labAtt?.faculty?.ifBlank { null }
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

@Composable
private fun MoodleAssignmentsCard(
    moodleAssignments: List<MoodleAssignment>,
    colors: com.amazecc.app.shared.theme.AmazeColors
) {
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

@Composable
private fun QcmCard(
    qcmTables: List<com.amazecc.app.shared.state.StoredQcmTable>,
    qcmLoading: Boolean,
    refreshQcm: () -> Unit,
    colors: com.amazecc.app.shared.theme.AmazeColors
) {
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
                    table.rows.forEach { row ->
                        val qcmNo = row.qcmNo
                        val action = row.action
                        val suggestions = row.suggestions
                        val facultyReply = row.facultyReply
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
private fun GradeHistoryTab(courseCode: String, academic: AcademicData, group: CourseGroup, colors: com.amazecc.app.shared.theme.AmazeColors) {
    val semesterMap by AppState.semesterMap.collectAsState()
    val cleanCode = courseCode.replace(Regex("\\([LPT]\\)$"), "").trim()
    val gradeItems = remember(academic, cleanCode) {
        val items = mutableListOf<Pair<String, GradeItem?>>()
        academic.semesters.forEach { (semId, sem) ->
            sem.courses.values.forEach { course ->
                if (course.courseCode.replace(Regex("\\([LPT]\\)$"), "").trim() == cleanCode) {
                    items.add(semId to course.toGradeItem())
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

    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = BOTTOM_NAV_PADDING)) {
        itemsIndexed(gradeItems) { index, (semId, grade) ->
            val semName = semesterMap[semId] ?: formatSemesterName(semId)
            val prevTotal = gradeItems.getOrNull(index + 1)?.second?.grandTotal?.toDoubleOrNull()
            val trendDiff = if (prevTotal != null) (grade?.grandTotal?.toDoubleOrNull() ?: 0.0) - prevTotal else null
            GradeHistoryCard(semName, grade, trendDiff, colors)
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun GradeHistoryCard(semName: String, grade: GradeItem?, trendDiff: Double?, colors: com.amazecc.app.shared.theme.AmazeColors, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    val gc = gradeColor(grade?.grade ?: "", colors)
    AmazeCard(modifier = modifier.fillMaxWidth(), onClick = { expanded = !expanded }) {
        Column {
            Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(44.dp).clip(CircleShape).background(gc.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(grade?.grade ?: "-", fontWeight = FontWeight.Black, fontSize = AmazeTheme.fontSize.xl, color = gc)
                }
                Spacer(Modifier.width(AmazeTheme.spacing.sm))
                Column(modifier = Modifier.weight(1f)) {
                    Text(semName, fontWeight = FontWeight.Bold, color = colors.textPrimary, fontSize = AmazeTheme.fontSize.sm)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Total: ${grade?.grandTotal ?: "-"}", fontSize = AmazeTheme.fontSize.sm, color = colors.textSecondary)
                        if (!grade?.courseType.isNullOrBlank()) {
                            Text(grade!!.courseType, fontSize = AmazeTheme.fontSize.micro, color = colors.textMuted)
                        }
                    }
                }
                if (trendDiff != null) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            when {
                                trendDiff > 0 -> Icons.Rounded.TrendingUp
                                trendDiff < 0 -> Icons.Rounded.TrendingDown
                                else -> Icons.Rounded.TrendingFlat
                            },
                            null,
                            tint = when {
                                trendDiff > 0 -> colors.success
                                trendDiff < 0 -> colors.danger
                                else -> colors.textMuted
                            },
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            "${if (trendDiff > 0) "+" else ""}${trendDiff.toFixed(1)}",
                            fontSize = AmazeTheme.fontSize.micro,
                            color = when {
                                trendDiff > 0 -> colors.success
                                trendDiff < 0 -> colors.danger
                                else -> colors.textMuted
                            },
                            fontWeight = FontWeight.Bold
                        )
                    }
                } else {
                    Icon(Icons.Rounded.TrendingFlat, null, tint = colors.textMuted, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(AmazeTheme.spacing.xs))
                Icon(
                    if (expanded) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
                    null,
                    tint = colors.textMuted,
                    modifier = Modifier.size(20.dp)
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 12.dp)) {
                    grade?.range?.let { range ->
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                            listOf("S" to range.S, "A" to range.A, "B" to range.B, "C" to range.C, "D" to range.D, "E" to range.E, "F" to range.F).forEach { (g, r) ->
                                val gColor = gradeColor(g, colors)
                                Box(
                                    modifier = Modifier.clip(RoundedCornerShape(AmazeTheme.radius.xs)).background(gColor.copy(alpha = 0.1f)).padding(horizontal = 10.dp, vertical = 4.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(g, fontWeight = FontWeight.Black, fontSize = AmazeTheme.fontSize.micro, color = gColor)
                                        Text(r, fontSize = AmazeTheme.fontSize.micro, color = colors.textMuted)
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(AmazeTheme.spacing.sm))
                    }

                    grade?.details?.let { details ->
                        Text("Component Breakdown", fontWeight = FontWeight.Bold, color = colors.textPrimary, fontSize = AmazeTheme.fontSize.sm)
                        Spacer(Modifier.height(AmazeTheme.spacing.xs))
                        details.forEach { comp ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    comp.component,
                                    fontSize = AmazeTheme.fontSize.micro,
                                    color = colors.textSecondary,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                comp.weightagePercent.ifBlank { null }?.let { w ->
                                    Text("$w%", fontSize = AmazeTheme.fontSize.micro, color = colors.textMuted)
                                    Spacer(Modifier.width(6.dp))
                                }
                                Text("${comp.scoredMark}/${comp.maxMark}", fontSize = AmazeTheme.fontSize.micro, color = colors.textPrimary, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun GradeViewCard(grade: GradeItem, label: String, colors: com.amazecc.app.shared.theme.AmazeColors) {
    val gc = gradeColor(grade.grade, colors)
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
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                        listOf("S" to range.S, "A" to range.A, "B" to range.B, "C" to range.C, "D" to range.D, "E" to range.E, "F" to range.F).forEach { (g, r) ->
                            val gColor = gradeColor(g, colors)
                            Box(
                                modifier = Modifier.clip(RoundedCornerShape(AmazeTheme.radius.xs)).background(gColor.copy(alpha = 0.1f)).padding(horizontal = 10.dp, vertical = 4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
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
    colors: com.amazecc.app.shared.theme.AmazeColors
) {
    val theoryMarks = group.theory
    val labMarks = group.lab
    val singleComponent = theoryMarks != null && labMarks != null && theoryMarks === labMarks
    val grading = remember(theoryMarks, labMarks, group.courseCode) { courseGrading(group, theoryMarks, labMarks) }

    val allAssessments = remember(theoryMarks, labMarks, singleComponent) {
        val theoryAsms = theoryMarks?.assessments ?: emptyList()
        val list = mutableListOf<Pair<String, List<AssessmentItem>>>()
        if (singleComponent) {
            list.add(theoryMarks!!.courseType.ifBlank { "Assessments" } to theoryAsms)
        } else {
            if (theoryMarks != null) list.add("Theory" to theoryAsms)
            if (labMarks != null) list.add("Lab" to labMarks.assessments)
            if (!isEmbedded && theoryMarks != null && labMarks == null) {
                list.clear()
                list.add("Assessments" to theoryAsms)
            }
        }
        list
    }

    val allAsms = remember(theoryMarks, labMarks, singleComponent) {
        if (singleComponent) theoryMarks?.assessments ?: emptyList()
        else (theoryMarks?.assessments ?: emptyList()) + (labMarks?.assessments ?: emptyList())
    }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp), contentPadding = PaddingValues(bottom = BOTTOM_NAV_PADDING)) {
        if (allAsms.isNotEmpty()) {
            item {
                MarksHeroCard(group, allAsms, grading, colors)
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(if (label == "Theory") colors.accent else colors.success))
                        Spacer(modifier = Modifier.width(AmazeTheme.spacing.sm))
                        Text(label, style = AmazeTheme.typography.subheading.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary), modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${asms.size} assessments", style = AmazeTheme.typography.caption.copy(color = colors.textMuted), maxLines = 1)
                    }
                }
                items(asms, key = { "${it.title}-${it.maxMark}" }) { asm ->
                    ExpandableAssessmentCard(asm, label, grading, colors)
                }
            }
        }

        if (allAsms.isNotEmpty()) {
            item {
                TargetGradeCalculator(theoryMarks, labMarks, grading, colors)
            }
        }
    }
}

@Composable
private fun MarksHeroCard(
    group: CourseGroup,
    allAsms: List<AssessmentItem>,
    grading: CourseGrading,
    colors: com.amazecc.app.shared.theme.AmazeColors
) {
    val modeTint = if (grading.mode == GradingMode.ABSOLUTE) colors.success else colors.warning

    val totalWeighted = allAsms.sumOf { it.weightageMark.toDoubleOrNull() ?: 0.0 }
    val totalWeightPct = allAsms.sumOf { it.weightagePercent.toDoubleOrNull() ?: 0.0 }
    val projectedPct = if (totalWeightPct > 0) (totalWeighted / totalWeightPct * 100).toInt() else 0

    HeroCard(colors = colors, modifier = Modifier.fillMaxWidth()) { p ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Assessment, null, tint = p.text, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Marks", color = p.textSecondary, style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold))
            Spacer(Modifier.weight(1f))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(AmazeTheme.radius.xs))
                    .background(p.chipBg)
                    .border(1.dp, p.chipBorder, RoundedCornerShape(AmazeTheme.radius.xs))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(modeTint))
                    Spacer(Modifier.width(6.dp))
                    Text("${grading.mode.label} Grading", color = p.text, fontWeight = FontWeight.Bold, fontSize = AmazeTheme.fontSize.micro)
                }
            }
        }

        when {
            allAsms.isNotEmpty() -> {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Marks Earned", color = p.textSecondary, fontWeight = FontWeight.Bold, fontSize = AmazeTheme.fontSize.sm)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        HeroStat("Scored", "${totalWeighted.toInt()}", p.text)
                        HeroStat("Weight", "${totalWeightPct.toInt()}%", p.textSecondary)
                        HeroStat("Projected", "$projectedPct%", p.textSecondary)
                    }
                    LinearProgressIndicator(
                        progress = { if (totalWeightPct > 0) (totalWeighted / totalWeightPct).toFloat() else 0f },
                        modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(AmazeTheme.radius.xs)),
                        color = p.progress,
                        trackColor = p.progressTrack
                    )
                }
            }
            group.grade != null -> {
                val gradeItem = group.grade!!
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier.size(52.dp).clip(CircleShape).background(p.iconBg),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(gradeItem.grade, color = p.text, fontWeight = FontWeight.Black, fontSize = AmazeTheme.fontSize.xl)
                    }
                    Spacer(Modifier.width(AmazeTheme.spacing.md))
                    Column {
                        Text("Grade Published", color = p.textSecondary, fontWeight = FontWeight.Bold, fontSize = AmazeTheme.fontSize.sm)
                        Text("Total: ${gradeItem.grandTotal}%", color = p.text, fontWeight = FontWeight.Bold, fontSize = AmazeTheme.fontSize.md)
                    }
                }
            }
            else -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Schedule, null, tint = p.textSecondary, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Marks not published yet", color = p.textSecondary, fontWeight = FontWeight.SemiBold, fontSize = AmazeTheme.fontSize.sm)
                }
            }
        }
    }
}

@Composable
private fun ExpandableAssessmentCard(asm: AssessmentItem, typeLabel: String, grading: CourseGrading, colors: com.amazecc.app.shared.theme.AmazeColors) {
    var expanded by remember { mutableStateOf(false) }
    val maxMark = asm.maxMark.toDoubleOrNull() ?: 0.0
    val scored = asm.scoredMark.toDoubleOrNull() ?: 0.0
    val pct = if (maxMark > 0) scored / maxMark * 100 else 0.0
    val isTheory = typeLabel == "Theory"
    val accentColor = if (isTheory) colors.accent else colors.success
    val shortenedTitle = com.amazecc.app.shared.ui.components.shortenAssessmentName(asm.title)
    val done = asm.status.contains("complet", ignoreCase = true)

    AmazeCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = { expanded = !expanded }
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(shortenedTitle, style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary), modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (asm.status.isNotBlank()) {
                    Spacer(Modifier.width(AmazeTheme.spacing.sm))
                    Text(asm.status, style = AmazeTheme.typography.smallLabel.copy(fontWeight = FontWeight.SemiBold, color = if (done) colors.success else colors.warning, fontSize = AmazeTheme.fontSize.micro))
                }
                Spacer(Modifier.width(AmazeTheme.spacing.sm))
                Icon(if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null, tint = colors.textMuted, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.height(AmazeTheme.spacing.xs))
            Text("${asm.scoredMark} / ${asm.maxMark}", fontWeight = FontWeight.Bold, color = colors.textPrimary, fontSize = AmazeTheme.fontSize.md)
            Text("${pct.toInt()}% scored · ${asm.weightageMark} of ${asm.weightagePercent}% weightage", style = AmazeTheme.typography.caption.copy(color = colors.textMuted), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(AmazeTheme.spacing.sm))
            Box(
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(AmazeTheme.radius.xs)).background(colors.border)
            ) {
                Box(
                    modifier = Modifier.fillMaxWidth((pct / 100).toFloat().coerceIn(0f, 1f)).fillMaxHeight().clip(RoundedCornerShape(AmazeTheme.radius.xs)).background(accentColor)
                )
            }

            AnimatedVisibility(visible = expanded, enter = fadeIn(), exit = fadeOut()) {
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(colors.border))
                    Spacer(Modifier.height(AmazeTheme.spacing.sm))

                    if (grading.mode == GradingMode.RELATIVE) {
                        Box(
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(AmazeTheme.radius.small)).background(colors.warning.copy(alpha = 0.08f)).border(1.dp, colors.warning.copy(alpha = 0.25f), RoundedCornerShape(AmazeTheme.radius.small)).padding(10.dp)
                        ) {
                            Column {
                                Text("Relative Grading", fontWeight = FontWeight.Bold, fontSize = AmazeTheme.fontSize.xs, color = colors.warning)
                                Text("Final grade boundaries are set against the class average — no fixed cutoffs.", fontSize = AmazeTheme.fontSize.micro, color = colors.textMuted)
                            }
                        }
                    } else {
                        Text("Grade Placement Preview", fontWeight = FontWeight.Bold, fontSize = AmazeTheme.fontSize.micro, color = colors.textMuted)
                        Spacer(Modifier.height(AmazeTheme.spacing.xs))
                        val gradePlacement = predictedGrade(pct)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                            GradeBoundariesAbsolute.forEach { (g, bound) ->
                                val gColor = gradeColor(g, colors)
                                val isCurrent = g == gradePlacement
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(AmazeTheme.radius.xs))
                                        .background(if (isCurrent) gColor.copy(alpha = 0.18f) else colors.surface)
                                        .border(if (isCurrent) 1.dp else 0.dp, if (isCurrent) gColor else Color.Transparent, RoundedCornerShape(AmazeTheme.radius.xs))
                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("$g ≥${bound}%", style = AmazeTheme.typography.smallLabel.copy(fontWeight = FontWeight.Bold, color = gColor), maxLines = 1)
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
}

@Composable
private fun TargetGradeCalculator(
    theoryMarks: MarksCourseItem?,
    labMarks: MarksCourseItem?,
    grading: CourseGrading,
    colors: com.amazecc.app.shared.theme.AmazeColors
) {
    var targetGrade by remember { mutableStateOf("A") }
    val singleComponent = theoryMarks != null && labMarks != null && theoryMarks === labMarks
    val allAsm = remember(theoryMarks, labMarks, singleComponent) {
        if (singleComponent) theoryMarks?.assessments ?: emptyList()
        else (theoryMarks?.assessments ?: emptyList()) + (labMarks?.assessments ?: emptyList())
    }
    val totalWeighted = allAsm.sumOf { it.weightageMark.toDoubleOrNull() ?: 0.0 }
    val totalWeightPct = allAsm.sumOf { it.weightagePercent.toDoubleOrNull() ?: 0.0 }
    val remainingPct = 100.0 - totalWeightPct

    val targetBound = GradeBoundariesAbsolute.find { it.first == targetGrade }?.second ?: 70
    val needPoints = (targetBound.toDouble() / 100.0 * 100.0) - totalWeighted
    val isAbsolute = grading.mode == GradingMode.ABSOLUTE
    val modeTint = if (isAbsolute) colors.success else colors.warning

    AmazeCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Grade Insights", style = AmazeTheme.typography.subheading.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
                Spacer(Modifier.width(AmazeTheme.spacing.sm))
                AmazeBadge("BETA", variant = BadgeVariant.INFO)
            }
            Spacer(Modifier.height(AmazeTheme.spacing.md))

            Box(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(AmazeTheme.radius.small)).background(modeTint.copy(alpha = 0.08f)).border(1.dp, modeTint.copy(alpha = 0.3f), RoundedCornerShape(AmazeTheme.radius.small)).padding(12.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(modeTint))
                        Spacer(Modifier.width(AmazeTheme.spacing.sm))
                        Text(if (isAbsolute) "Absolute Grading Enforced" else "Relative Grading (Class-Average)", fontWeight = FontWeight.Bold, fontSize = AmazeTheme.fontSize.xs, color = modeTint)
                    }
                    Text(grading.reason, fontSize = AmazeTheme.fontSize.micro, color = colors.textMuted)
                    if (isAbsolute) {
                        Spacer(Modifier.height(AmazeTheme.spacing.xs))
                        Text("Fixed boundaries: S≥90 · A≥80 · B≥70 · C≥60 · D≥50 · E≥40", fontSize = AmazeTheme.fontSize.micro, color = modeTint, fontWeight = FontWeight.Medium)
                    }
                }
            }

            Spacer(Modifier.height(AmazeTheme.spacing.md))
            Text("Target Grade", fontWeight = FontWeight.Bold, fontSize = AmazeTheme.fontSize.sm, color = colors.textSecondary)
            Spacer(Modifier.height(AmazeTheme.spacing.xs))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                listOf("S", "A", "B", "C", "D", "E").forEach { g ->
                    val sel = targetGrade == g
                    val gColor = gradeColor(g, colors)
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(AmazeTheme.radius.xs))
                            .background(if (sel) gColor else colors.surface)
                            .border(if (sel) 0.dp else 1.dp, colors.border, RoundedCornerShape(AmazeTheme.radius.xs))
                            .clickable { targetGrade = g }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(g, style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Black), color = if (sel) Color.White else gColor)
                    }
                }
            }

            Spacer(Modifier.height(AmazeTheme.spacing.md))
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

            if (isAbsolute) {
                Spacer(Modifier.height(AmazeTheme.spacing.md))
                Text("Grade Boundaries", fontWeight = FontWeight.Bold, fontSize = AmazeTheme.fontSize.sm, color = colors.textSecondary)
                Spacer(Modifier.height(AmazeTheme.spacing.xs))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    GradeBoundariesAbsolute.forEach { (g, bound) ->
                        val gc = gradeColor(g, colors)
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(AmazeTheme.radius.xs))
                                .background(gc.copy(alpha = 0.1f))
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("$g ≥${bound}%", fontSize = AmazeTheme.fontSize.micro, fontWeight = FontWeight.Bold, color = gc, maxLines = 1)
                        }
                    }
                }
            }
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
    group: CourseGroup,
    colors: com.amazecc.app.shared.theme.AmazeColors
) {
    val courseCode = group.courseCode
    val theory = group.theory
    val lab = group.lab
    val mainAtt = group.theoryAtt ?: group.labAtt

    var syllabusResult by remember { mutableStateOf<SyllabusResult?>(null) }
    var syllabusLoading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val saveFile = rememberFileSaver()

    LaunchedEffect(courseCode) {
        syllabusLoading = true
        syllabusResult = AmazeClient.getSyllabusPdf(courseCode)
        syllabusLoading = false
    }

    val allAssessments = remember(theory, lab) {
        (theory?.assessments ?: emptyList()) + (lab?.assessments ?: emptyList())
    }
    val totalWeighted = allAssessments.sumOf { it.weightageMark.toDoubleOrNull() ?: 0.0 }
    val totalWeightPct = allAssessments.sumOf { it.weightagePercent.toDoubleOrNull() ?: 0.0 }
    val projectedPct = if (totalWeightPct > 0) (totalWeighted / totalWeightPct * 100).toInt() else 0

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = BOTTOM_NAV_PADDING)
    ) {
        // 1. Course Overview Card (from CourseDetailsInfoCard)
        item {
            AmazeCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Course Details", style = AmazeTheme.typography.subheading.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
                            Text(courseCode, fontSize = AmazeTheme.fontSize.xs, color = colors.textMuted)
                        }
                        val sb = syllabusResult?.download
                        val se = syllabusResult?.error
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
                    Spacer(Modifier.height(12.dp))

                    val mainCourse = theory ?: lab
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MetricTile("Type", mainCourse?.courseType ?: "-", colors, Modifier.weight(1f))
                        MetricTile("Slot", mainCourse?.slot ?: "-", colors, Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MetricTile("System", mainCourse?.courseSystem ?: "-", colors, Modifier.weight(1f))
                        MetricTile("Credits", mainAtt?.credits ?: "-", colors, Modifier.weight(1f))
                    }

                    if (theory != null && lab != null) {
                        Spacer(Modifier.height(AmazeTheme.spacing.sm))
                        Box(
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(AmazeTheme.radius.small)).background(colors.accent.copy(alpha = 0.1f)).padding(12.dp)
                        ) {
                            Column {
                                Text("Components", style = AmazeTheme.typography.smallLabel.copy(fontWeight = FontWeight.Bold, color = colors.accent))
                                Spacer(Modifier.height(AmazeTheme.spacing.xs))
                                theory.let { Text("${it.courseType} — Class #${it.classNbr.takeLast(4)}", fontSize = AmazeTheme.fontSize.sm, color = colors.textPrimary, fontWeight = FontWeight.Medium) }
                                lab.let { Text("${it.courseType} — Class #${it.classNbr.takeLast(4)}", fontSize = AmazeTheme.fontSize.sm, color = colors.textPrimary, fontWeight = FontWeight.Medium) }
                            }
                        }
                    }

                    val rawFaculty = mainCourse?.faculty?.ifBlank { null }
                        ?: mainAtt?.faculty?.ifBlank { null }
                    val parsedFac = if (!rawFaculty.isNullOrBlank()) com.amazecc.app.shared.utils.FacultyUtils.parseFaculty(rawFaculty) else null
                    if (parsedFac != null) {
                        Spacer(Modifier.height(AmazeTheme.spacing.sm))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(AmazeTheme.radius.small))
                                .background(colors.surface)
                                .border(1.dp, colors.border.copy(alpha = 0.5f), RoundedCornerShape(AmazeTheme.radius.small))
                                .padding(12.dp)
                        ) {
                            Column {
                                Text("Faculty", style = AmazeTheme.typography.smallLabel.copy(color = colors.textMuted, fontSize = AmazeTheme.fontSize.micro))
                                Spacer(Modifier.height(2.dp))
                                Text(parsedFac.name, style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary, fontSize = AmazeTheme.fontSize.base))
                            }
                        }
                    }
                }
            }
        }

        // 2. Assessment Timeline (from MarksTab summary)
        if (allAssessments.isNotEmpty()) {
            item {
                AmazeCard(modifier = Modifier.fillMaxWidth()) {
                    Column {
                        Text("Assessment Overview", style = AmazeTheme.typography.subheading.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
                        Spacer(Modifier.height(AmazeTheme.spacing.sm))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            MetricTile("Score", "${totalWeighted.toInt()}/${totalWeightPct.toInt()}", colors, Modifier.weight(1f))
                            MetricTile("Projected", "$projectedPct%", colors, Modifier.weight(1f))
                            MetricTile("Assessments", "${allAssessments.size}", colors, Modifier.weight(1f))
                        }
                        Spacer(Modifier.height(AmazeTheme.spacing.sm))
                        allAssessments.take(5).forEach { asm ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    com.amazecc.app.shared.ui.components.shortenAssessmentName(asm.title),
                                    style = AmazeTheme.typography.caption.copy(color = colors.textPrimary),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    "${asm.scoredMark}/${asm.maxMark} (${asm.weightageMark}/${asm.weightagePercent}%)",
                                    style = AmazeTheme.typography.caption.copy(color = colors.textSecondary, fontSize = AmazeTheme.fontSize.micro)
                                )
                            }
                        }
                        if (allAssessments.size > 5) {
                            Spacer(Modifier.height(AmazeTheme.spacing.xs))
                            Text("+${allAssessments.size - 5} more — see Marks tab", style = AmazeTheme.typography.caption.copy(color = colors.textMuted, fontSize = AmazeTheme.fontSize.micro))
                        }
                    }
                }
            }
        }

        // 3. Weekly Schedule (existing)
        item {
            var showSchedule by remember { mutableStateOf(false) }
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

    }
}

@Composable
private fun FacultyTab(
    group: CourseGroup,
    colors: com.amazecc.app.shared.theme.AmazeColors
) {
    val theory = group.theory
    val lab = group.lab
    val mainAtt = group.theoryAtt ?: group.labAtt

    val rawFaculty = theory?.faculty?.ifBlank { null }
        ?: lab?.faculty?.ifBlank { null }
        ?: mainAtt?.faculty?.ifBlank { null }
    val parsedFac = if (!rawFaculty.isNullOrBlank()) com.amazecc.app.shared.utils.FacultyUtils.parseFaculty(rawFaculty) else null

    if (parsedFac == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Rounded.PersonSearch, null, tint = colors.textMuted, modifier = Modifier.size(48.dp))
                Spacer(Modifier.height(AmazeTheme.spacing.sm))
                Text("No faculty information available", color = colors.textSecondary)
            }
        }
        return
    }

    var facultyLoading by remember { mutableStateOf(false) }
    var facultyProfile by remember { mutableStateOf<FacultyProfile?>(null) }
    val qcmViewRes by AppState.qcmView.collectAsState()
    val qcmTables = remember(qcmViewRes) { qcmViewRes?.tables ?: emptyList() }
    val qcmLoading = AppState.isLoading.collectAsState().value

    LaunchedEffect(parsedFac.name, parsedFac.id) {
        facultyLoading = true
        val dir = AmazeClient.searchFacultyDirectory(parsedFac.name, parsedFac.id, parsedFac.school)
        facultyLoading = false
        facultyProfile = FacultyProfile(
            id = parsedFac.id ?: dir?.id ?: "",
            name = parsedFac.name.ifBlank { dir?.name ?: parsedFac.name },
            designation = dir?.designation ?: "",
            imageUrl = dir?.imageUrl ?: "",
            profileUrl = dir?.profileUrl ?: "",
            email = dir?.email ?: "",
            employeeId = dir?.employeeId ?: parsedFac.id ?: "",
            intercom = dir?.intercom ?: ""
        )
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
                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .clip(CircleShape)
                                .background(colors.accent.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                parsedFac.name.take(2).uppercase(),
                                style = AmazeTheme.typography.subheading.copy(fontWeight = FontWeight.Black, color = colors.accent)
                            )
                        }
                        Spacer(Modifier.width(AmazeTheme.spacing.sm))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(parsedFac.name, style = AmazeTheme.typography.subheading.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
                            if (facultyLoading) {
                                Text("Loading profile...", style = AmazeTheme.typography.caption.copy(color = colors.textMuted))
                            } else {
                                val fp = facultyProfile
                                Text(fp?.designation?.ifBlank { "-" } ?: "-", style = AmazeTheme.typography.caption.copy(color = colors.textSecondary))
                            }
                        }
                    }
                }
            }
        }

        val fp = facultyProfile
        item {
            AmazeCard(modifier = Modifier.fillMaxWidth()) {
                Column {
                    Text("Contact Details", style = AmazeTheme.typography.subheading.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
                    Spacer(Modifier.height(AmazeTheme.spacing.sm))
                    DetailRow("Email", fp?.email ?: "-", colors)
                    DetailRow("Employee ID", fp?.employeeId ?: parsedFac.id ?: "-", colors)
                    DetailRow("Intercom", fp?.intercom ?: "-", colors)
                    DetailRow("School", parsedFac.school ?: "-", colors)
                }
            }
        }

        item {
            AmazeCard(modifier = Modifier.fillMaxWidth()) {
                Column {
                    Text("Free Time at a Glance", style = AmazeTheme.typography.subheading.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
                    Spacer(Modifier.height(AmazeTheme.spacing.sm))
                    if (fp == null && facultyLoading) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(color = colors.accent, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(AmazeTheme.spacing.sm))
                            Text("Loading schedule...", color = colors.textMuted, fontSize = AmazeTheme.fontSize.sm)
                        }
                    } else {
                        val profile = fp ?: FacultyProfile(id = parsedFac.id ?: "", name = parsedFac.name, designation = "", employeeId = parsedFac.id ?: "")
                        val schedule = remember(profile) { com.amazecc.app.shared.utils.FacultyFreeSlotsUtil.getFacultySchedule(profile) }
                        if (schedule.freeSlots.isEmpty()) {
                            Text("No free slots found", color = colors.textMuted, fontSize = AmazeTheme.fontSize.sm)
                        } else {
                            schedule.freeSlots.forEach { (day, free) ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(day, style = AmazeTheme.typography.caption.copy(fontWeight = FontWeight.Bold, color = colors.accent), modifier = Modifier.width(44.dp))
                                    Text(
                                        free.joinToString(", "),
                                        style = AmazeTheme.typography.caption.copy(color = colors.textSecondary),
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            AmazeButton(
                text = "Open Full Schedule",
                onClick = {
                    AppState.navigateTo(Screen.FACULTY_INFO)
                },
                icon = Icons.Rounded.CalendarMonth,
                modifier = Modifier.fillMaxWidth(),
                variant = ButtonVariant.SECONDARY
            )
        }

        item {
            QcmCard(qcmTables, qcmLoading, { AppState.refreshQcmView() }, colors)
        }
    }
}

@Composable
private fun FreeSlotsTab(
    group: CourseGroup,
    colors: com.amazecc.app.shared.theme.AmazeColors
) {
    val theory = group.theory
    val lab = group.lab
    val mainAtt = group.theoryAtt ?: group.labAtt

    val rawFaculty = theory?.faculty?.ifBlank { null }
        ?: lab?.faculty?.ifBlank { null }
        ?: mainAtt?.faculty?.ifBlank { null }
    val parsedFac = if (!rawFaculty.isNullOrBlank()) com.amazecc.app.shared.utils.FacultyUtils.parseFaculty(rawFaculty) else null

    if (parsedFac == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Rounded.CalendarMonth, null, tint = colors.textMuted, modifier = Modifier.size(48.dp))
                Spacer(Modifier.height(AmazeTheme.spacing.sm))
                Text("No faculty information available", color = colors.textSecondary)
            }
        }
        return
    }

    var facultyProfile by remember { mutableStateOf<FacultyProfile?>(null) }

    LaunchedEffect(parsedFac.name, parsedFac.id) {
        val dir = AmazeClient.searchFacultyDirectory(parsedFac.name, parsedFac.id, parsedFac.school)
        facultyProfile = FacultyProfile(
            id = parsedFac.id ?: dir?.id ?: "",
            name = parsedFac.name.ifBlank { dir?.name ?: parsedFac.name },
            designation = dir?.designation ?: "",
            imageUrl = dir?.imageUrl ?: "",
            profileUrl = dir?.profileUrl ?: "",
            email = dir?.email ?: "",
            employeeId = dir?.employeeId ?: parsedFac.id ?: "",
            intercom = dir?.intercom ?: ""
        )
    }

    val profile = facultyProfile ?: FacultyProfile(id = parsedFac.id ?: "", name = parsedFac.name, designation = "", employeeId = parsedFac.id ?: "")
    val schedule = remember(profile) { com.amazecc.app.shared.utils.FacultyFreeSlotsUtil.getFacultySchedule(profile) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = BOTTOM_NAV_PADDING)
    ) {
        item {
            Text("Weekly Free Slots — ${parsedFac.name}", style = AmazeTheme.typography.subheading.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
            Text("Based on the FFCS report for this semester", style = AmazeTheme.typography.caption.copy(color = colors.textSecondary))
        }

        item(key = "schedule_grid") {
            val weekDays = listOf("MON", "TUE", "WED", "THU", "FRI")
            val timePeriods = remember { com.amazecc.app.shared.utils.FacultyFreeSlotsUtil.getAllTimePeriods() }
            val dayLabels = mapOf("MON" to "Mon", "TUE" to "Tue", "WED" to "Wed", "THU" to "Thu", "FRI" to "Fri")
            val freeColor = colors.success
            val occupiedSlotKeys = remember(schedule) {
                schedule.occupiedSlots.map { "${it.day}:${it.timeRange}" }.toSet()
            }
            val occupiedSlotByKey = remember(schedule) {
                schedule.occupiedSlots.associateBy { "${it.day}:${it.timeRange}" }
            }

            AmazeCard(modifier = Modifier.fillMaxWidth()) {
                Column {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Box(modifier = Modifier.width(52.dp))
                        weekDays.forEach { day ->
                            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                Text(dayLabels[day] ?: day, style = AmazeTheme.typography.caption.copy(color = colors.accent, fontWeight = FontWeight.Bold))
                            }
                        }
                    }
                    Spacer(Modifier.height(AmazeTheme.spacing.xs))

                    timePeriods.forEach { time ->
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
                            Box(modifier = Modifier.width(52.dp), contentAlignment = Alignment.CenterStart) {
                                Text(time, style = AmazeTheme.typography.caption.copy(color = colors.textMuted))
                            }
                            weekDays.forEach { day ->
                                val isOccupied = occupiedSlotKeys.contains("$day:$time")
                                val slot = if (isOccupied) occupiedSlotByKey["$day:$time"] else null
                                val cellColor = if (slot != null) colors.danger.copy(alpha = 0.18f) else freeColor.copy(alpha = 0.12f)
                                val borderColor = if (slot != null) colors.danger.copy(alpha = 0.35f) else freeColor.copy(alpha = 0.25f)
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(1.5.dp)
                                        .height(18.dp)
                                        .clip(RoundedCornerShape(AmazeTheme.radius.xs))
                                        .background(cellColor)
                                        .border(0.5.dp, borderColor, RoundedCornerShape(AmazeTheme.radius.xs)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (slot != null) {
                                        Text(slot.slotCode, style = AmazeTheme.typography.smallLabel.copy(color = colors.danger, fontWeight = FontWeight.Bold))
                                    }
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(AmazeTheme.spacing.sm))
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(10.dp).clip(RoundedCornerShape(AmazeTheme.radius.xs)).background(freeColor.copy(alpha = 0.2f)).border(0.5.dp, freeColor.copy(alpha = 0.25f), RoundedCornerShape(AmazeTheme.radius.xs)))
                            Spacer(Modifier.width(AmazeTheme.spacing.xs))
                            Text("Free", style = AmazeTheme.typography.caption.copy(color = colors.textSecondary))
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(10.dp).clip(RoundedCornerShape(AmazeTheme.radius.xs)).background(colors.danger.copy(alpha = 0.2f)).border(0.5.dp, colors.danger.copy(alpha = 0.35f), RoundedCornerShape(AmazeTheme.radius.xs)))
                            Spacer(Modifier.width(AmazeTheme.spacing.xs))
                            Text("Occupied", style = AmazeTheme.typography.caption.copy(color = colors.textSecondary))
                        }
                    }
                }
            }
        }

        if (schedule.freeSlots.isNotEmpty()) {
            item {
                Text("Free Slots by Day", style = AmazeTheme.typography.subheading.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
            }
            schedule.freeSlots.forEach { (day, free) ->
                item(key = "free_$day") {
                    AmazeCard(modifier = Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(AmazeTheme.radius.xs))
                                    .background(colors.success.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(day.take(2), style = AmazeTheme.typography.caption.copy(fontWeight = FontWeight.Bold, color = colors.success))
                            }
                            Spacer(Modifier.width(AmazeTheme.spacing.sm))
                            Column(Modifier.weight(1f)) {
                                Text(day, style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
                                Text(free.joinToString(", "), style = AmazeTheme.typography.caption.copy(color = colors.textSecondary))
                            }
                        }
                    }
                }
            }
        }

        item {
            AmazeButton(
                text = "Open Full Faculty Schedule",
                onClick = {
                    AppState.navigateTo(Screen.FACULTY_INFO)
                },
                icon = Icons.Rounded.ArrowForward,
                modifier = Modifier.fillMaxWidth(),
                variant = ButtonVariant.SECONDARY
            )
        }
    }
}
