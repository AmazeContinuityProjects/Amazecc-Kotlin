package com.amazecc.app.shared.ui.screens.m3e

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.MeetingRoom
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.TrendingUp
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.max
import kotlin.math.min
import com.amazecc.app.shared.domain.EmbeddedCourseHalf
import com.amazecc.app.shared.domain.EmbeddedLinkCode
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
                                    state = state,
                                    onAction = onAction
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
    // D-189 G2: an embedded course is two separate classes with two real attendances. Its merged
    // Course.attendance only sums counts; showing it as one percentage would invent a blend.
    val pair = embeddedPair(state.halves)
    if (pair != null) {
        EmbeddedCourseAttendanceCard(state, pair)
        return
    }
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

/**
 * D-189 G2: overview attendance for an embedded pair. Every figure is read from the half's own
 * published attendance via the same projection the component menu and panels use; a half with no
 * published attendance says so honestly. No aggregate is computed, so no blended percentage can
 * reach this card. This is a display of the two existing halves, not a new per-row control.
 */
@Composable
private fun EmbeddedCourseAttendanceCard(
    state: M3ECourseDetailState,
    pair: Pair<EmbeddedCourseHalf, EmbeddedCourseHalf>,
) {
    val colors = M3ETheme.colors
    val (theory, lab) = pair
    M3ESurfaceCard(
        containerLevel = SurfaceContainerLevel.HIGH,
        cornerRadius = 24.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = "Attendance",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = colors.onSurface
            )
            listOf(theory, lab).forEach { half ->
                val label = m3eComponentLabel(half)
                // Same helper as Theory Log / Lab Log subtitles, split only for display hierarchy.
                val componentText = componentAttendanceText(half)
                val hasCounts = " · " in componentText
                val headline = if (hasCounts) componentText.substringBefore(" · ") else componentText
                val counts = if (hasCounts) componentText.substringAfter(" · ") else ""
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "$label attendance",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.onSurfaceVariant
                        )
                        Text(
                            text = headline,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Black,
                            color = colors.primary
                        )
                    }
                    if (counts.isNotBlank()) {
                        Text(
                            text = counts,
                            fontSize = 12.sp,
                            color = colors.onSurfaceVariant
                        )
                        Text(
                            text = componentMarginText(half, state.attendanceTarget.toDouble()),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.primary
                        )
                    }
                }
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
        if (state.components.size > 1) {
            Text(
                text = "Assessments Breakdown by Component",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = colors.onSurface
            )

            state.components.forEach { component ->
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Component Header with Title + Credits and Subtotal
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = component.componentTitle,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.primary
                            )
                            component.credits?.let { cr ->
                                Box(
                                    modifier = Modifier
                                        .background(
                                            colors.surfaceContainerLow,
                                            RoundedCornerShape(4.dp)
                                        )
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "${if (cr % 1.0 == 0.0) cr.toInt() else cr} Cr",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = colors.onSurfaceVariant
                                    )
                                }
                            }
                        }
                        Text(
                            text = "${component.scoredMarks.toFixed(2)} / ${component.maxMarks.toFixed(2)}",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.primary
                        )
                    }

                    if (component.assessments.isEmpty()) {
                        Text(
                            text = "No assessments published yet for this component.",
                            fontSize = 12.sp,
                            color = colors.onSurfaceVariant,
                            modifier = Modifier.padding(start = 4.dp)
                        )
                    } else {
                        component.assessments.forEach { assessment ->
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
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Assessments Breakdown",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.onSurface
                )
                if (state.internalMax > 0.0) {
                    Text(
                        text = "Total: ${state.internalScored.toFixed(2)} / ${state.internalMax.toFixed(2)}",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.primary
                    )
                }
            }

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
internal fun CoursePredictorSection(
    state: M3ECourseDetailState,
    onAction: (M3ECourseDetailAction) -> Unit
) {
    val colors = M3ETheme.colors
    val grades = listOf("S", "A", "B", "C", "D")

    val theoryHalf = state.halves.firstOrNull { it.half == "T" || !EmbeddedLinkCode.isLabRow(it.type, it.slots.joinToString("+")) }
    val labHalf = state.halves.firstOrNull { it.half == "L" || EmbeddedLinkCode.isLabRow(it.type, it.slots.joinToString("+")) }
    val theoryCredits = theoryHalf?.credits?.toDoubleOrNull() ?: 3.0
    val labCredits = labHalf?.credits?.toDoubleOrNull() ?: 1.0

    var editingAssessment by remember { mutableStateOf<M3EAssessmentItem?>(null) }
    var editingLabFat by remember { mutableStateOf(false) }

    val solver = remember(state.targetGrade, state.assessments, state.labFatMark, state.classStats) {
        M3EPredictorDerivation.solveCourseFat(
            targetGrade = state.targetGrade,
            assessments = state.assessments,
            theoryCredits = theoryCredits,
            labCredits = labCredits,
            labFatScored = state.labFatMark,
            classStats = state.classStats,
            courseType = state.courseType
        )
    }

    val gradeLadderNeeds = remember(state.assessments, state.labFatMark, state.classStats) {
        grades.map { g ->
            val res = M3EPredictorDerivation.solveCourseFat(
                targetGrade = g,
                assessments = state.assessments,
                theoryCredits = theoryCredits,
                labCredits = labCredits,
                labFatScored = state.labFatMark,
                classStats = state.classStats,
                courseType = state.courseType
            )
            g to res
        }
    }

    val hasSimulations = state.simulatedAssessments.isNotEmpty() || state.labFatMark != null

    val metrics = remember(state.assessments, theoryCredits, labCredits, state.classStats, state.courseType) {
        M3EPredictorDerivation.calculateCoursePredictionMetrics(
            assessments = state.assessments,
            theoryCredits = theoryCredits,
            labCredits = labCredits,
            classStats = state.classStats,
            courseSystem = null,
            courseType = state.courseType
        )
    }

    val isCurved = state.classStats != null && state.classStats.count >= 5 &&
        com.amazecc.app.shared.domain.isRelativeGrading("ACE", state.courseType)

    val gradeCutoffs = remember(state.classStats, state.courseType) {
        listOf("S", "A", "B", "C", "D", "E").map { g ->
            val fallback = when (g) {
                "S" -> 90.0; "A" -> 80.0; "B" -> 70.0; "C" -> 60.0; "D" -> 55.0; else -> 50.0
            }
            g to (M3EPredictorDerivation.cutoffFor(g, state.classStats, null, state.courseType) ?: fallback)
        }
    }

    fun gradeColor(grade: String?) = when (M3EPredictorDerivation.toneForGrade(grade)) {
        M3ECardTone.GOOD -> colors.success
        M3ECardTone.WARNING -> colors.warning
        M3ECardTone.CRITICAL -> colors.error
        M3ECardTone.NEUTRAL -> colors.primary
    }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // Reset simulations banner
        if (hasSimulations) {
            M3ESurfaceCard(
                containerLevel = SurfaceContainerLevel.LOW,
                cornerRadius = 12.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Edit,
                            contentDescription = null,
                            tint = colors.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "What-if simulation active: ${state.simulatedAssessments.size + (if (state.labFatMark != null) 1 else 0)} marks modified",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.primary
                        )
                    }
                    TextButton(onClick = { onAction(M3ECourseDetailAction.ResetSimulations) }) {
                        Icon(Icons.Rounded.RestartAlt, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Reset", fontSize = 12.sp)
                    }
                }
            }
        }

        // 0. Headline Stat Pair: Predicted Grade/Mark + Maximisable Grade/Mark
        val scoredPct = metrics.currentScoredScaled.coerceIn(0.0, 100.0)
        val lostPct = metrics.pointsLostScaled.coerceIn(0.0, 100.0 - scoredPct)
        val pendingPct = (100.0 - scoredPct - lostPct).coerceIn(0.0, 100.0)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Left: Predicted Grade & Pace Mark
            M3ESurfaceCard(
                containerLevel = SurfaceContainerLevel.HIGH,
                cornerRadius = 18.dp,
                modifier = Modifier.weight(1f)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "PREDICTED",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.onSurfaceVariant,
                            letterSpacing = 0.8.sp
                        )
                        if (isCurved) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(colors.primaryContainer)
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "Curved",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.onPrimaryContainer
                                )
                            }
                        }
                    }
                    Text(
                        text = metrics.optimalGrade ?: "—",
                        fontSize = 34.sp,
                        fontWeight = FontWeight.Black,
                        color = gradeColor(metrics.optimalGrade)
                    )
                    Text(
                        text = metrics.pacePercentage?.let {
                            "${it.toFixed(1)}% pace · ${pendingPct.toFixed(1)} pending"
                        } ?: "No marks entered",
                        fontSize = 11.sp,
                        color = colors.onSurfaceVariant
                    )
                }
            }

            // Right: Maximisable Grade & Max Mark
            M3ESurfaceCard(
                containerLevel = SurfaceContainerLevel.HIGH,
                cornerRadius = 18.dp,
                modifier = Modifier.weight(1f)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "MAXIMISABLE",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.onSurfaceVariant,
                        letterSpacing = 0.8.sp
                    )
                    Text(
                        text = metrics.maximisableGrade,
                        fontSize = 34.sp,
                        fontWeight = FontWeight.Black,
                        color = gradeColor(metrics.maximisableGrade)
                    )
                    Text(
                        text = "Max ${metrics.maxPossibleScaled.toFixed(1)} / 100 · -${metrics.pointsLostScaled.toFixed(1)} lost",
                        fontSize = 11.sp,
                        color = colors.onSurfaceVariant
                    )
                }
            }
        }

        // Honesty line
        Row(
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(horizontal = 2.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.Warning,
                contentDescription = null,
                tint = colors.warning,
                modifier = Modifier.size(14.dp).padding(top = 2.dp)
            )
            Text(
                text = "Just a prediction. Based on marks entered, not an official result.",
                fontSize = 11.sp,
                color = colors.warning,
                fontWeight = FontWeight.Medium
            )
        }

        // Segmented Progress Bar (Locked / Pending / Lost)
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Locked ${scoredPct.toFixed(1)}%",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.primary
                )
                Text(
                    text = "Pending ${pendingPct.toFixed(1)}%",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.tertiary
                )
                Text(
                    text = "Lost ${lostPct.toFixed(1)}%",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.error
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(colors.surfaceContainerHighest)
            ) {
                if (scoredPct > 0.0) {
                    Box(
                        modifier = Modifier
                            .weight(scoredPct.toFloat().coerceAtLeast(0.01f))
                            .height(10.dp)
                            .background(colors.primary)
                    )
                }
                if (pendingPct > 0.0) {
                    Box(
                        modifier = Modifier
                            .weight(pendingPct.toFloat().coerceAtLeast(0.01f))
                            .height(10.dp)
                            .background(colors.tertiary.copy(alpha = 0.7f))
                    )
                }
                if (lostPct > 0.0) {
                    Box(
                        modifier = Modifier
                            .weight(lostPct.toFloat().coerceAtLeast(0.01f))
                            .height(10.dp)
                            .background(colors.error)
                    )
                }
            }
        }

        // 1. Target Grade & FAT Solver
        Text(
            text = "Target Grade & FAT Solver",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = colors.onSurface
        )

        M3EConnectedButtonGroup(
            items = grades,
            selectedItem = state.targetGrade,
            onItemSelected = { onAction(M3ECourseDetailAction.SetTargetGrade(it)) }
        )

        M3ESurfaceCard(
            containerLevel = SurfaceContainerLevel.HIGH,
            cornerRadius = 20.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (state.assessments.none { !it.component.equals("Lab", ignoreCase = true) })
                            "Required in Lab Final Exam (FAT):"
                        else
                            "Required in Theory Final Exam (FAT):",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = colors.onSurfaceVariant
                    )

                    val feasibilityToneColor = when (solver.feasibility) {
                        "secured" -> colors.success
                        "comfortable" -> colors.primary
                        "achievable" -> colors.primary
                        "tough" -> colors.warning
                        else -> colors.error
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(feasibilityToneColor.copy(alpha = 0.15f))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = solver.feasibilityLabel,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = feasibilityToneColor
                        )
                    }
                }

                val activeFatMax = if (state.assessments.none { !it.component.equals("Lab", ignoreCase = true) }) 50 else 100
                Text(
                    text = if (solver.feasibility == "impossible") {
                        "Out of reach"
                    } else if (solver.feasibility == "secured") {
                        "0.0 / $activeFatMax"
                    } else {
                        "${solver.requiredFatRaw.toFixed(1)} / $activeFatMax"
                    },
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Black,
                    color = colors.primary
                )

                Text(
                    text = solver.feasibilityMessage,
                    fontSize = 12.sp,
                    color = colors.onSurfaceVariant
                )

                if (state.labFatMark != null) {
                    Text(
                        text = "Includes simulated Lab FAT (${state.labFatMark.toFixed(1)} / 50.0).",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.primary
                    )
                }

                Text(
                    text = "VIT Regulation: Minimum 40% in Theory FAT alone required to pass the course (§9.4 a).",
                    fontSize = 11.sp,
                    color = colors.onSurfaceVariant
                )

                // Quick table for all grades
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    gradeLadderNeeds.forEach { (g, req) ->
                        val isSelected = g == state.targetGrade
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) colors.primaryContainer else colors.surfaceContainerHighest)
                                .padding(vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = g,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) colors.onPrimaryContainer else colors.onSurface
                                )
                                Text(
                                    text = if (req.feasibility == "impossible") "—" else "${req.requiredFatRaw.toFixed(0)}",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Black,
                                    color = if (isSelected) colors.onPrimaryContainer else colors.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }

        // 2. UniCC Class Cohort Statistics & Grade Boundaries Card
        M3ESurfaceCard(
            containerLevel = SurfaceContainerLevel.LOW,
            cornerRadius = 16.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
                            imageVector = Icons.Rounded.Insights,
                            contentDescription = null,
                            tint = colors.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Class Cohort Statistics",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.onSurface
                        )
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(colors.primaryContainer)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = if (isCurved) "Curved · UniCC" else "UniCC API",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.onPrimaryContainer
                        )
                    }
                }

                if (state.classStats != null && state.classStats.count > 0) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Class Mean", fontSize = 11.sp, color = colors.onSurfaceVariant)
                            Text(
                                "${state.classStats.mean.toFixed(2)} / ${state.classStats.scale.toFixed(0)}",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.onSurface
                            )
                        }
                        Column {
                            Text("Std Dev (σ)", fontSize = 11.sp, color = colors.onSurfaceVariant)
                            Text(
                                state.classStats.sd.toFixed(2),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.onSurface
                            )
                        }
                        Column {
                            Text("Datapoints", fontSize = 11.sp, color = colors.onSurfaceVariant)
                            Text(
                                "${state.classStats.count} students",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.onSurface
                            )
                        }
                    }

                    val diff = state.internalScored - state.classStats.mean
                    val isAbove = diff >= 0
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(colors.surfaceContainerHigh)
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = if (isAbove) {
                                "+${diff.toFixed(2)} marks above class mean"
                            } else {
                                "${diff.toFixed(2)} marks below class mean"
                            },
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isAbove) colors.success else colors.error
                        )
                    }

                    if (state.classStats.count in 1..30) {
                        Text(
                            text = "⚠️ Low data sample (${state.classStats.count} classmates). Grade cutoffs and predictions are experimental.",
                            fontSize = 11.sp,
                            color = colors.onSurfaceVariant
                        )
                    }
                } else {
                    Text(
                        text = "Class cohort data not yet synced on UniCC for this class. Falling back to the VIT standard absolute grading ladder.",
                        fontSize = 12.sp,
                        color = colors.onSurfaceVariant
                    )
                }

                // Grade Boundaries / Predicted Grade Cutoffs
                Text(
                    text = "Predicted Grade Cutoffs (Total / 100)",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    gradeCutoffs.forEach { (g, cutoff) ->
                        val isPredicted = g == metrics.optimalGrade
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isPredicted) colors.primaryContainer else colors.surfaceContainerHigh)
                                .padding(vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = g,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isPredicted) colors.onPrimaryContainer else gradeColor(g)
                                )
                                Text(
                                    text = "≥${cutoff.toFixed(0)}",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isPredicted) colors.onPrimaryContainer else colors.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }

        // 3. Assessment Regimen & What-If Mark Simulator
        Text(
            text = "Assessments & What-If Simulation",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = colors.onSurface
        )

        Text(
            text = "Tap on any assessment to edit or simulate its score and see the FAT requirement recalculate live.",
            fontSize = 12.sp,
            color = colors.onSurfaceVariant
        )

        if (state.components.size > 1) {
            state.components.forEach { comp ->
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = comp.componentTitle,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.primary
                        )
                        Text(
                            text = "${comp.scoredMarks.toFixed(2)} / ${comp.maxMarks.toFixed(2)}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.onSurfaceVariant
                        )
                    }

                    comp.assessments.forEach { asm ->
                        M3EAssessmentSimulatorCard(
                            assessment = asm,
                            isSimulated = state.simulatedAssessments.containsKey(asm.title),
                            onEdit = { editingAssessment = asm }
                        )
                    }

                    // Lab FAT simulation card for ELA component (fallback if Lab FAT not already in assessments)
                    if (comp.componentCode == "ELA" && comp.assessments.none {
                        it.title.contains("FAT", ignoreCase = true) || it.title.contains("Final Assessment", ignoreCase = true)
                    }) {
                        M3ESurfaceCard(
                            containerLevel = SurfaceContainerLevel.LOW,
                            cornerRadius = 10.dp,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = "Lab Final Assessment (Lab FAT)",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = colors.onSurface
                                        )
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(colors.primaryContainer)
                                                .padding(horizontal = 4.dp, vertical = 1.dp)
                                        ) {
                                            Text(
                                                text = if (state.labFatMark != null) "Simulated" else "FAT",
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = colors.onPrimaryContainer
                                            )
                                        }
                                    }
                                    Text(
                                        text = "Weightage: 40.00% · Max: 50.0",
                                        fontSize = 11.sp,
                                        color = colors.onSurfaceVariant
                                    )
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = state.labFatMark?.let { "${it.toFixed(1)} / 50.0" } ?: "Not set",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (state.labFatMark != null) colors.primary else colors.onSurfaceVariant
                                    )
                                    IconButton(
                                        onClick = { editingLabFat = true },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Edit,
                                            contentDescription = "Edit Lab FAT",
                                            tint = colors.primary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else {
            state.assessments.forEach { asm ->
                M3EAssessmentSimulatorCard(
                    assessment = asm,
                    isSimulated = state.simulatedAssessments.containsKey(asm.title),
                    onEdit = { editingAssessment = asm }
                )
            }
        }
    }

    // Mark edit dialog
    editingAssessment?.let { asm ->
        M3EMarkEditDialog(
            title = asm.title,
            maxMarks = asm.maxMarks,
            currentScore = asm.scoredMarks,
            onDismiss = { editingAssessment = null },
            onSave = { newScore ->
                onAction(M3ECourseDetailAction.SetAssessmentMark(asm.title, newScore))
                editingAssessment = null
            },
            onReset = {
                onAction(M3ECourseDetailAction.SetAssessmentMark(asm.title, null))
                editingAssessment = null
            }
        )
    }

    if (editingLabFat) {
        M3EMarkEditDialog(
            title = "Lab Final Assessment (Lab FAT)",
            maxMarks = 50.0,
            currentScore = state.labFatMark ?: 40.0,
            onDismiss = { editingLabFat = false },
            onSave = { newScore ->
                onAction(M3ECourseDetailAction.SetLabFatMark(newScore))
                editingLabFat = false
            },
            onReset = {
                onAction(M3ECourseDetailAction.SetLabFatMark(null))
                editingLabFat = false
            }
        )
    }
}

@Composable
private fun M3EAssessmentSimulatorCard(
    assessment: M3EAssessmentItem,
    isSimulated: Boolean,
    onEdit: () -> Unit
) {
    val colors = M3ETheme.colors
    M3ESurfaceCard(
        containerLevel = SurfaceContainerLevel.LOW,
        cornerRadius = 10.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = assessment.title,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.onSurface
                    )
                    if (isSimulated) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(colors.primaryContainer)
                                .padding(horizontal = 4.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = "Simulated",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.onPrimaryContainer
                            )
                        }
                    }
                }
                Text(
                    text = "Weightage: ${assessment.weightagePercent.toFixed(2)}%",
                    fontSize = 11.sp,
                    color = colors.onSurfaceVariant
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "${assessment.scoredMarks.toFixed(2)} / ${assessment.maxMarks.toFixed(2)}",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isSimulated) colors.primary else colors.onSurface
                )
                IconButton(
                    onClick = onEdit,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Edit,
                        contentDescription = "Simulate mark",
                        tint = colors.primary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun M3EMarkEditDialog(
    title: String,
    maxMarks: Double,
    currentScore: Double,
    onDismiss: () -> Unit,
    onSave: (Double) -> Unit,
    onReset: () -> Unit
) {
    val colors = M3ETheme.colors
    var scoreInput by remember { mutableStateOf(currentScore.toFixed(1)) }
    val max = if (maxMarks > 0) maxMarks else 100.0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = "Simulate Mark", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(text = title, fontSize = 13.sp, color = colors.onSurfaceVariant)
                Text(text = "Max Mark: ${max.toFixed(1)}", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)

                OutlinedTextField(
                    value = scoreInput,
                    onValueChange = { scoreInput = it },
                    label = { Text("Scored Mark") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text(text = "Quick Presets:", fontSize = 11.sp, color = colors.onSurfaceVariant)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(100, 90, 80, 75, 60).forEach { pct ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(6.dp))
                                .background(colors.surfaceContainerHighest)
                                .clickable {
                                    scoreInput = ((pct / 100.0) * max).toFixed(1)
                                }
                                .padding(vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = "$pct%", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val parsed = scoreInput.toDoubleOrNull() ?: currentScore
                    onSave(min(max, max(0.0, parsed)))
                }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onReset) {
                    Text("Reset to VTOP")
                }
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        }
    )
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
