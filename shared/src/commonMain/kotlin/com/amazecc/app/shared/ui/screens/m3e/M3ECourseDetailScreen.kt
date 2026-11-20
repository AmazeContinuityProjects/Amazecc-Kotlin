package com.amazecc.app.shared.ui.screens.m3e

import androidx.compose.animation.AnimatedContent
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Assessment
import androidx.compose.material.icons.rounded.Book
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.MeetingRoom
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amazecc.app.shared.domain.overhaul.CourseDetailTab
import com.amazecc.app.shared.domain.overhaul.M3EAssessmentItem
import com.amazecc.app.shared.domain.overhaul.M3ECardTone
import com.amazecc.app.shared.domain.overhaul.M3ECourseDetailAction
import com.amazecc.app.shared.domain.overhaul.M3ECourseDetailState
import com.amazecc.app.shared.ui.m3e.components.M3EConnectedButtonGroup
import com.amazecc.app.shared.ui.m3e.components.M3ESurfaceCard
import com.amazecc.app.shared.ui.m3e.components.M3EWavyLinearProgress
import com.amazecc.app.shared.ui.m3e.components.SurfaceContainerLevel
import com.amazecc.app.shared.ui.m3e.theme.AmazeM3ETheme
import com.amazecc.app.shared.ui.m3e.theme.M3ETheme
import com.amazecc.app.shared.utils.toFixed
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * Material 3 Expressive Course Detail Hub.
 * 100% Stateless: accepts [M3ECourseDetailState] and emits [M3ECourseDetailAction].
 * Features Multi-tab view: Overview, Marks Breakdown, Syllabus Units, and What-if Grade Predictor.
 */
@Composable
fun M3ECourseDetailScreen(
    state: M3ECourseDetailState,
    onAction: (M3ECourseDetailAction) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = M3ETheme.colors
    // D-114: M3ETheme.motion.* is composable and reads LocalMotionReduced at the call site.
    // Compute it here (composable scope) and capture the value; the transitionSpec lambda below
    // is NOT composable and may only return the already-computed transform.
    val tabSwitchTransform = M3ETheme.motion.tabSwitchTransform()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.surface)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header: Back Button + Code + Credits
            item(key = "header_row") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        IconButton(
                            onClick = { onAction(M3ECourseDetailAction.Back) },
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(colors.surfaceContainerLow)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = "Back",
                                tint = colors.onSurface
                            )
                        }

                        Column {
                            Text(
                                text = state.courseCode,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.primary
                            )
                            Text(
                                text = "${state.courseType} • Slot ${state.slot}",
                                fontSize = 12.sp,
                                color = colors.onSurfaceVariant
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(colors.primaryContainer)
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "${state.credits} Credits",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.onPrimaryContainer
                        )
                    }
                }
            }

            // Course Title Hero
            item(key = "title_hero") {
                Text(
                    text = state.courseTitle,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.onSurface
                )
            }

            // Tab Switcher
            item(key = "tabs_switcher") {
                M3EConnectedButtonGroup(
                    items = CourseDetailTab.entries,
                    selectedItem = state.activeTab,
                    onItemSelected = { onAction(M3ECourseDetailAction.SelectTab(it)) },
                    labelProvider = { it.label }
                )
            }

            // Active Tab Content
            item(key = "tab_content_${state.activeTab}") {
                AnimatedContent(
                    targetState = state.activeTab,
                    // Captured from the composable scope above so the reduce-motion decision is
                    // read from LocalMotionReduced; a raw fadeIn/fadeOut would bypass it (D-117).
                    transitionSpec = { tabSwitchTransform }
                ) { tab ->
                    when (tab) {
                        CourseDetailTab.OVERVIEW -> CourseOverviewSection(state = state)
                        CourseDetailTab.MARKS -> CourseMarksSection(state = state)
                        CourseDetailTab.SYLLABUS -> CourseSyllabusSection(
                            state = state,
                            onDownload = { onAction(M3ECourseDetailAction.DownloadSyllabus) }
                        )
                        CourseDetailTab.PREDICTOR -> CoursePredictorSection(
                            state = state,
                            onSelectGrade = { onAction(M3ECourseDetailAction.SetTargetGrade(it)) }
                        )
                    }
                }
            }

            // Bottom Spacing for floating navigation bar clearance (140dp)
            item(key = "bottom_spacer") {
                Spacer(Modifier.height(140.dp))
            }
        }
    }
}

@Composable
private fun CourseOverviewSection(state: M3ECourseDetailState) {
    val colors = M3ETheme.colors

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // Attendance Overview Card
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
    }
}

@Composable
private fun CourseMarksSection(state: M3ECourseDetailState) {
    val colors = M3ETheme.colors

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
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
