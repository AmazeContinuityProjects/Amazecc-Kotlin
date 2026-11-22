package com.amazecc.app.shared.ui.screens.m3e

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Assessment
import androidx.compose.material.icons.rounded.QuestionAnswer
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material.icons.rounded.Book
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.MeetingRoom
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.TrendingUp
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.key
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amazecc.app.shared.domain.EmbeddedCourseHalf
import com.amazecc.app.shared.domain.overhaul.CourseDetailTab
import com.amazecc.app.shared.domain.overhaul.M3EAssessmentItem
import com.amazecc.app.shared.domain.overhaul.M3ECardTone
import com.amazecc.app.shared.domain.overhaul.M3ECourseDetailAction
import com.amazecc.app.shared.domain.overhaul.M3ECourseDetailState
import com.amazecc.app.shared.ui.components.AppBackHandler
import com.amazecc.app.shared.ui.m3e.components.M3EConnectedButtonGroup
import com.amazecc.app.shared.ui.m3e.components.M3ESurfaceCard
import com.amazecc.app.shared.ui.m3e.components.SettingsGroupHeader
import com.amazecc.app.shared.ui.m3e.components.SettingsMenuIsland
import com.amazecc.app.shared.ui.m3e.components.SettingsMenuRow
import com.amazecc.app.shared.ui.m3e.components.M3EWavyLinearProgress
import com.amazecc.app.shared.ui.m3e.components.SurfaceContainerLevel
import com.amazecc.app.shared.ui.m3e.theme.AmazeM3ETheme
import com.amazecc.app.shared.ui.m3e.theme.M3ETheme
import com.amazecc.app.shared.utils.toFixed
import org.jetbrains.compose.ui.tooling.preview.Preview

private enum class CourseSection(val title: String) {
    ATTENDANCE_LOG("Attendance Log"),
    THEORY_LOG("Theory Log"),
    LAB_LOG("Lab Log"),
    ATTENDANCE_FORECAST("Attendance Forecast"),
    THEORY_FORECAST("Theory Attendance Forecast"),
    LAB_FORECAST("Lab Attendance Forecast"),
    THEORY_ASSESSMENTS("Theory Assessments"),
    LAB_ASSESSMENTS("Lab Assessments"),
    GRADES("Grades"),
    REVIEW("Review Course Details"),
    MATERIALS("VTOP Course Page"),
}

@Composable
fun M3ECourseDetailScreen(
    state: M3ECourseDetailState,
    onAction: (M3ECourseDetailAction) -> Unit,
    modifier: Modifier = Modifier,
    handleSystemBack: Boolean = true,
    onCreateTask: ((M3ETaskDraft) -> Unit)? = null,
    materialsContent: (@Composable () -> Unit)? = null,
) {
    key(state.semesterId, state.courseCode) {
        var showTaskSheet by rememberSaveable { mutableStateOf(false) }
        var sectionName by rememberSaveable { mutableStateOf<String?>(null) }
        val section = sectionName?.let(CourseSection::valueOf)
        val isOverview = section == null && state.activeTab == CourseDetailTab.OVERVIEW
        val canOpenSections = state.isCourseAvailable
        val goBack: () -> Unit = {
            when {
                showTaskSheet -> showTaskSheet = false
                !canOpenSections || isOverview -> onAction(M3ECourseDetailAction.Back)
                section != null -> sectionName = null
                else -> onAction(M3ECourseDetailAction.SelectTab(CourseDetailTab.OVERVIEW))
            }
        }
        AppBackHandler(
            enabled = handleSystemBack && canOpenSections && (!isOverview || showTaskSheet),
            onBack = goBack
        )

        val colors = M3ETheme.colors
        val overviewScroll = rememberLazyListState()
        val sectionScroll = key(section, state.activeTab) { rememberLazyListState() }
        val headerTitle = if (!canOpenSections || isOverview) state.courseCode else {
            section?.title ?: when (state.activeTab) {
                CourseDetailTab.PREDICTOR -> "Grade Predictor"
                else -> state.activeTab.label
            }
        }

        Column(
            modifier = modifier
                .fillMaxSize()
                .background(colors.surface)
        ) {
            CourseDetailHeader(
                state = state,
                title = headerTitle,
                isOverview = isOverview && canOpenSections,
                onBack = goBack
            )
            LazyColumn(
                state = if (isOverview) overviewScroll else sectionScroll,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 140.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (!canOpenSections) {
                    item {
                        Text(
                            text = "Course details are unavailable for this semester.",
                            color = colors.onSurfaceVariant
                        )
                    }
                } else if (isOverview) {
                    item(key = "course_title") {
                        Text(
                            text = state.courseTitle,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.onSurface
                        )
                    }
                    item(key = "attendance_summary") { CourseAttendanceSummary(state) }
                    item(key = "marks_summary") { CourseMarksSummary(state) }
                    item(key = "course_sections") {
                        CourseSectionsMenu(
                            state = state,
                            onOpenSection = { sectionName = it.name },
                            onOpenTab = { onAction(M3ECourseDetailAction.SelectTab(it)) },
                            onOpenQBank = { onAction(M3ECourseDetailAction.OpenQBank) },
                            showMaterials = materialsContent != null,
                            onAddTask = if (onCreateTask != null) ({ showTaskSheet = true }) else null
                        )
                    }
                } else {
                    item(key = section?.name ?: state.activeTab.name) {
                        when (section) {
                            CourseSection.ATTENDANCE_LOG, CourseSection.THEORY_LOG, CourseSection.LAB_LOG,
                            CourseSection.ATTENDANCE_FORECAST, CourseSection.THEORY_FORECAST, CourseSection.LAB_FORECAST,
                            CourseSection.THEORY_ASSESSMENTS, CourseSection.LAB_ASSESSMENTS ->
                                CourseComponentSection(state, section)
                            CourseSection.GRADES ->
                                M3ECourseGradesPanel(publishedGrade = state.publishedGrade, history = state.gradeHistory)
                            CourseSection.REVIEW -> CourseReviewSection(
                                state = state,
                                onDownload = { onAction(M3ECourseDetailAction.DownloadSyllabus) }
                            )
                            CourseSection.MATERIALS -> materialsContent?.invoke()
                            null -> when (state.activeTab) {
                                CourseDetailTab.OVERVIEW -> Unit
                                CourseDetailTab.MARKS -> CourseMarksSection(state)
                                CourseDetailTab.SYLLABUS -> CourseSyllabusSection(
                                    state, onDownload = { onAction(M3ECourseDetailAction.DownloadSyllabus) }
                                )
                                CourseDetailTab.PREDICTOR -> CoursePredictorSection(
                                    state, onSelectGrade = { onAction(M3ECourseDetailAction.SetTargetGrade(it)) }
                                )
                            }
                        }
                    }
                }
            }
        }
        if (canOpenSections && showTaskSheet && onCreateTask != null) {
            M3ETaskCreateSheet(
                initialDraft = M3ETaskDraft(courseCode = state.courseCode, courseTitle = state.courseTitle),
                onDismiss = { showTaskSheet = false },
                onSave = { draft ->
                    onCreateTask(draft)
                    showTaskSheet = false
                }
            )
        }
    }
}

@Composable
private fun CourseDetailHeader(
    state: M3ECourseDetailState,
    title: String,
    isOverview: Boolean,
    onBack: () -> Unit
) {
    val colors = M3ETheme.colors
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(colors.surfaceContainerLow)
        ) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = colors.onSurface)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = if (isOverview) colors.primary else colors.onSurface
            )
            if (isOverview) {
                Text(
                    text = "${state.courseType} · Slot ${state.slot}",
                    fontSize = 12.sp,
                    color = colors.onSurfaceVariant
                )
            } else if (state.isCourseAvailable) {
                Text(state.courseCode, fontSize = 12.sp, color = colors.onSurfaceVariant)
            }
        }
        if (isOverview) {
            state.credits?.let { credits ->
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(colors.primaryContainer)
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "$credits Credits",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.onPrimaryContainer
                    )
                }
            }
        }
    }
}

@Composable
private fun CourseAttendanceSummary(state: M3ECourseDetailState) {
    val colors = M3ETheme.colors
    if (state.totalClasses <= 0) {
        M3ESurfaceCard(containerLevel = SurfaceContainerLevel.LOW, modifier = Modifier.fillMaxWidth()) {
            Text("Attendance unavailable", color = colors.onSurfaceVariant)
        }
        return
    }
    M3ESurfaceCard(
        containerLevel = SurfaceContainerLevel.HIGH,
        cornerRadius = 24.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Attendance",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.onSurface
                )
                Text(
                    text = state.attendancePct.toFixed(2, "%"),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Black,
                    color = when (state.tone) {
                        M3ECardTone.GOOD -> colors.success
                        M3ECardTone.WARNING -> colors.warning
                        M3ECardTone.CRITICAL -> colors.error
                        M3ECardTone.NEUTRAL -> colors.primary
                        else -> colors.primary
                    }
                )
            }

            M3EWavyLinearProgress(
                progress = (state.attendancePct / 100.0).toFloat(),
                label = "Course attendance",
                activeColor = when (state.tone) {
                    M3ECardTone.GOOD -> colors.success
                    M3ECardTone.WARNING -> colors.warning
                    M3ECardTone.CRITICAL -> colors.error
                    M3ECardTone.NEUTRAL -> colors.primary
                    else -> colors.primary
                }
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${state.attendedClasses}/${state.totalClasses} classes attended",
                    fontSize = 12.sp,
                    color = colors.onSurfaceVariant
                )
                Text(
                    text = state.bunkMarginSummary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.primary
                )
            }
        }
    }
}

@Composable
private fun CourseMarksSummary(state: M3ECourseDetailState) {
    val colors = M3ETheme.colors
    M3ESurfaceCard(containerLevel = SurfaceContainerLevel.LOW, modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Internal Marks", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = colors.onSurface)
            Text(
                text = if (state.internalMax > 0.0) {
                    "${state.internalScored.toFixed(2)} / ${state.internalMax.toFixed(2)}"
                } else {
                    "Marks unavailable"
                },
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = colors.primary
            )
        }
    }
}

@Composable
private fun CourseReviewSection(
    state: M3ECourseDetailState,
    onDownload: () -> Unit,
) {
    val colors = M3ETheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // Faculty Info Card
        M3ESurfaceCard(
            containerLevel = SurfaceContainerLevel.LOW,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Person,
                        contentDescription = null,
                        tint = colors.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Faculty Details",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.onSurface
                    )
                }

                Text(
                    text = state.facultyName,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.onSurface
                )

                state.facultyDesignation?.let {
                    Text(
                        text = it,
                        fontSize = 12.sp,
                        color = colors.onSurfaceVariant
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    state.facultyCabin?.let { cabin ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.MeetingRoom,
                                contentDescription = null,
                                tint = colors.onSurfaceVariant,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "Cabin: $cabin",
                                fontSize = 12.sp,
                                color = colors.onSurfaceVariant
                            )
                        }
                    }

                    state.facultyEmail?.let { email ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Email,
                                contentDescription = null,
                                tint = colors.onSurfaceVariant,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = email,
                                fontSize = 12.sp,
                                color = colors.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        // Venue & Timing Card
        M3ESurfaceCard(
            containerLevel = SurfaceContainerLevel.LOW,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.LocationOn,
                        contentDescription = null,
                        tint = colors.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = state.venue,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = colors.onSurface
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Schedule,
                        contentDescription = null,
                        tint = colors.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "Slot ${state.slot}",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.primary
                    )
                }
            }
        }

        M3ECourseReviewAdditions(state.semesterId.orEmpty(), state.courseCode, onDownload, state.errorMessage)
    }
}

@Composable
private fun CourseSectionsMenu(
    state: M3ECourseDetailState,
    onOpenSection: (CourseSection) -> Unit,
    onOpenTab: (CourseDetailTab) -> Unit,
    onOpenQBank: () -> Unit,
    showMaterials: Boolean,
    onAddTask: (() -> Unit)?
) {
    SettingsGroupHeader(title = "Course sections")
    SettingsMenuIsland {
        val pair = embeddedPair(state.halves)
        if (pair != null) {
            val (theory, lab) = pair
            SettingsMenuRow(
                icon = Icons.Rounded.Assessment,
                title = CourseSection.THEORY_LOG.title,
                subtitle = componentAttendanceText(theory),
                onClick = { onOpenSection(CourseSection.THEORY_LOG) }
            )
            SettingsMenuRow(
                icon = Icons.Rounded.Assessment,
                title = CourseSection.LAB_LOG.title,
                subtitle = componentAttendanceText(lab),
                onClick = { onOpenSection(CourseSection.LAB_LOG) }
            )
            if (theory.attendance != null) {
                CourseForecastRow(CourseSection.THEORY_FORECAST, onOpenSection)
            }
            if (lab.attendance != null) {
                CourseForecastRow(CourseSection.LAB_FORECAST, onOpenSection)
            }
            SettingsMenuRow(
                icon = Icons.Rounded.Book,
                title = CourseSection.THEORY_ASSESSMENTS.title,
                subtitle = "${theory.marks?.assessments?.size ?: 0} assessments published",
                onClick = { onOpenSection(CourseSection.THEORY_ASSESSMENTS) }
            )
            SettingsMenuRow(
                icon = Icons.Rounded.Book,
                title = CourseSection.LAB_ASSESSMENTS.title,
                subtitle = "${lab.marks?.assessments?.size ?: 0} assessments published",
                onClick = { onOpenSection(CourseSection.LAB_ASSESSMENTS) }
            )
        } else {
            standaloneAttendanceHalf(state)?.let { half ->
                SettingsMenuRow(
                    icon = Icons.Rounded.Assessment,
                    title = CourseSection.ATTENDANCE_LOG.title,
                    subtitle = componentAttendanceText(half),
                    onClick = { onOpenSection(CourseSection.ATTENDANCE_LOG) }
                )
                CourseForecastRow(CourseSection.ATTENDANCE_FORECAST, onOpenSection)
            }
        }
        SettingsMenuRow(
            icon = Icons.Rounded.Book,
            title = "Marks",
            subtitle = "Marks Breakdown",
            onClick = { onOpenTab(CourseDetailTab.MARKS) }
        )
        SettingsMenuRow(
            icon = Icons.Rounded.TrendingUp,
            title = "Grade Predictor",
            subtitle = "Simulate the final exam score for a target grade",
            onClick = { onOpenTab(CourseDetailTab.PREDICTOR) }
        )
        SettingsMenuRow(
            icon = Icons.Rounded.Assessment,
            title = CourseSection.GRADES.title,
            subtitle = "Published final grade",
            value = state.publishedGrade,
            onClick = { onOpenSection(CourseSection.GRADES) }
        )
        SettingsMenuRow(
            icon = Icons.Rounded.Person,
            title = CourseSection.REVIEW.title,
            subtitle = "Faculty, venue and official syllabus",
            onClick = { onOpenSection(CourseSection.REVIEW) }
        )
        if (showMaterials) {
            SettingsMenuRow(
                icon = Icons.Rounded.Download,
                title = CourseSection.MATERIALS.title,
                subtitle = "Lecture plan and files uploaded by faculty",
                onClick = { onOpenSection(CourseSection.MATERIALS) }
            )
        }
        val pendingTasks = state.pendingTaskCount
        SettingsMenuRow(
            icon = Icons.Rounded.QuestionAnswer,
            title = "QBank",
            subtitle = "Papers and extracted questions",
            onClick = onOpenQBank,
            showDivider = pendingTasks != null && onAddTask != null
        )
        if (pendingTasks != null && onAddTask != null) {
            CourseTasksRow(pendingTasks, onAddTask)
        }
    }
}

@Composable
private fun CourseForecastRow(section: CourseSection, onOpenSection: (CourseSection) -> Unit) {
    SettingsMenuRow(
        icon = Icons.Rounded.TrendingUp,
        title = section.title,
        subtitle = "Simulate attendance for upcoming classes",
        onClick = { onOpenSection(section) }
    )
}

@Composable
private fun CourseTasksRow(pendingTasks: Int, onAddTask: () -> Unit) {
    val colors = M3ETheme.colors
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(colors.surfaceContainerHigh),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Rounded.TaskAlt, contentDescription = null, tint = colors.primary, modifier = Modifier.size(20.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text("Tasks & Homework", fontWeight = FontWeight.SemiBold, color = colors.onSurface)
            Text("$pendingTasks pending", fontSize = 12.sp, color = colors.onSurfaceVariant)
        }
        TextButton(onClick = onAddTask) { Text("Add Task") }
    }
}

private fun standaloneAttendanceHalf(state: M3ECourseDetailState): EmbeddedCourseHalf? =
    state.standaloneAttendance?.let { attendance ->
        EmbeddedCourseHalf(
            code = state.courseCode,
            title = state.courseTitle,
            type = state.courseType,
            slots = state.courseSlots,
            attendance = attendance
        )
    }

@Composable
private fun CourseComponentSection(state: M3ECourseDetailState, section: CourseSection) {
    val pair = embeddedPair(state.halves)
    val half = when (section) {
        CourseSection.ATTENDANCE_LOG, CourseSection.ATTENDANCE_FORECAST -> standaloneAttendanceHalf(state)
        CourseSection.THEORY_LOG, CourseSection.THEORY_FORECAST, CourseSection.THEORY_ASSESSMENTS -> pair?.first
        CourseSection.LAB_LOG, CourseSection.LAB_FORECAST, CourseSection.LAB_ASSESSMENTS -> pair?.second
        CourseSection.GRADES, CourseSection.REVIEW, CourseSection.MATERIALS -> null
    }
    if (half == null) {
        Text("Component details are unavailable.", color = M3ETheme.colors.onSurfaceVariant)
    } else if (section == CourseSection.THEORY_ASSESSMENTS || section == CourseSection.LAB_ASSESSMENTS) {
        M3EComponentPredictorPanel(half = half)
    } else if (section == CourseSection.ATTENDANCE_FORECAST ||
        section == CourseSection.THEORY_FORECAST || section == CourseSection.LAB_FORECAST
    ) {
        M3ECourseAttendanceForecast(state.semesterId, half, state.attendanceTarget.toDouble())
    } else {
        M3EComponentLogPanel(half = half, attendanceTargetPercent = state.attendanceTarget.toDouble())
    }
}

@Composable
private fun CourseMarksSection(state: M3ECourseDetailState) {
    val colors = M3ETheme.colors

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // Embedded pairs publish one credit-weighted total on one ladder — never a per-half grade.
        if (embeddedPair(state.halves) != null) {
            Text(
                text = "Theory and lab credits are combined into one weighted score on a single grading ladder.",
                fontSize = 12.sp,
                color = colors.onSurfaceVariant
            )
        }

        // Internal Marks Hero Card
        M3ESurfaceCard(
            containerLevel = SurfaceContainerLevel.HIGH,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Total Internal Score",
                        fontSize = 12.sp,
                        color = colors.onSurfaceVariant
                    )
                    Text(
                        text = if (state.internalMax > 0.0) {
                            "${state.internalScored.toFixed(2)} / ${state.internalMax.toFixed(2)}"
                        } else "Not available",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Black,
                        color = colors.primary
                    )
                }

                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    state.classAverage?.let { avg ->
                        Text(
                            text = "Class Avg: ${avg.toFixed(2)}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.onSurfaceVariant
                        )
                    }
                    state.classMax?.let { max ->
                        Text(
                            text = "Class Max: ${max.toFixed(2)}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.success
                        )
                    }
                }
            }
        }

        // Assessments List
        Text(
            text = "Assessments Breakdown",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = colors.onSurface
        )

        if (state.assessments.isEmpty()) {
            Text(
                text = "No assessments released yet.",
                fontSize = 13.sp,
                color = colors.onSurfaceVariant
            )
        } else {
            state.assessments.forEach { assessment ->
                M3ESurfaceCard(
                    containerLevel = SurfaceContainerLevel.LOW,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = assessment.title,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = colors.onSurface
                            )
                            Text(
                                text = "Weightage: ${assessment.weightagePercent.toFixed(2)}%",
                                fontSize = 11.sp,
                                color = colors.onSurfaceVariant
                            )
                        }

                        Text(
                            text = "${assessment.scoredMarks.toFixed(2)} / ${assessment.maxMarks.toFixed(2)}",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.primary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CourseSyllabusSection(
    state: M3ECourseDetailState,
    onDownload: () -> Unit
) {
    val colors = M3ETheme.colors

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // Download Button
        ElevatedButton(
            onClick = onDownload,
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.elevatedButtonColors(
                containerColor = colors.primaryContainer,
                contentColor = colors.onPrimaryContainer
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = Icons.Rounded.Download,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "Download Official Syllabus (PDF)",
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
            )
        }

        // Real download outcome: the button is not a dead control, and the message never claims
        // modules will appear when the app has no structured module data to show.
        state.errorMessage?.let { message ->
            Text(
                text = message,
                fontSize = 12.sp,
                color = colors.onSurfaceVariant
            )
        }

        Text(
            text = "Course Modules",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = colors.onSurface
        )

        if (state.syllabusUnits.isEmpty()) {
            Text(
                text = "Not available — structured syllabus modules are not published for this course. Use the PDF above for the full outline.",
                fontSize = 13.sp,
                color = colors.onSurfaceVariant
            )
        } else {
            state.syllabusUnits.forEach { unit ->
                M3ESurfaceCard(
                    containerLevel = SurfaceContainerLevel.LOW,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Module ${unit.unitNumber}: ${unit.title}",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.onSurface
                            )
                            if (unit.hours > 0) {
                                Text(
                                    text = "${unit.hours} hrs",
                                    fontSize = 11.sp,
                                    color = colors.primary
                                )
                            }
                        }
                        Text(
                            text = unit.description,
                            fontSize = 12.sp,
                            color = colors.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CoursePredictorSection(
    state: M3ECourseDetailState,
    onSelectGrade: (String) -> Unit
) {
    val colors = M3ETheme.colors
    val grades = listOf("S", "A", "B", "C", "D")

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            text = "Select Desired Target Grade",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = colors.onSurface
        )

        M3EConnectedButtonGroup(
            items = grades,
            selectedItem = state.targetGrade,
            onItemSelected = onSelectGrade
        )

        M3ESurfaceCard(
            containerLevel = SurfaceContainerLevel.HIGH,
            cornerRadius = 24.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Required Final Exam (FAT) Score",
                    fontSize = 13.sp,
                    color = colors.onSurfaceVariant
                )

                Text(
                    text = state.requiredFinalScore?.let { "${it.toFixed(2)} / 100" } ?: "Not available",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Black,
                    color = colors.primary
                )

                Text(
                    text = when {
                        state.internalMax <= 0.0 ->
                            "Internal marks are not published for this course."
                        state.requiredFinalScore == null ->
                            "Could not compute a requirement for grade ${state.targetGrade} from the available data."
                        else ->
                            "Based on current internals (${state.internalScored.toFixed(2)}/${state.internalMax.toFixed(2)}) for target grade ${state.targetGrade}."
                    },
                    fontSize = 11.sp,
                    color = colors.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * SAMPLE-ONLY preview data for the IDE canvas — not a runtime state and never derived from a
 * real payload. Numbers below are illustrative only; `requiredFinalScore` is intentionally left
 * null and `syllabusUnits` empty so the preview shows the same honest "Not available" states the
 * runtime shows when no real syllabus/FAT data exists.
 */
private fun createMockCourseDetailState() = M3ECourseDetailState(
    courseCode = "CSE3002",
    courseTitle = "Internet and Web Programming",
    credits = 4,
    courseType = "ETH",
    slot = "B1+TB1",
    venue = "SJT 412",
    facultyName = "Prof. Sundar",
    facultyEmail = "sundar@vit.ac.in",
    facultyCabin = "SJT 412-A",
    facultyDesignation = "Associate Professor Grade 1",
    attendedClasses = 28,
    totalClasses = 30,
    attendancePct = 93.3,
    bunkMarginSummary = "Can bunk 6 more classes",
    tone = M3ECardTone.GOOD,
    internalScored = 46.5,
    internalMax = 50.0,
    classAverage = 38.2,
    classMax = 49.0,
    targetGrade = "S",
    // No fabricated FAT requirement — the preview uses the same null = "Not available" path.
    assessments = listOf(
        M3EAssessmentItem(
            title = "Continuous Assessment 1",
            maxMarks = 50.0,
            scoredMarks = 46.0,
            weightagePercent = 15.0,
            weightageMarks = 13.8
        ),
        M3EAssessmentItem(
            title = "Continuous Assessment 2",
            maxMarks = 50.0,
            scoredMarks = 48.0,
            weightagePercent = 15.0,
            weightageMarks = 14.4
        )
    ),
    // Runtime has no structured syllabus payload; keep the preview honest.
    syllabusUnits = emptyList()
)

@Preview
@Composable
private fun M3ECourseDetailScreenPreview() {
    AmazeM3ETheme(isDark = false) {
        M3ECourseDetailScreen(state = createMockCourseDetailState(), onAction = {})
    }
}

@Preview
@Composable
private fun M3ECourseDetailScreenDarkPreview() {
    AmazeM3ETheme(isDark = true) {
        M3ECourseDetailScreen(state = createMockCourseDetailState(), onAction = {})
    }
}
