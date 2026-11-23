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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.TrendingUp
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amazecc.app.shared.domain.overhaul.AttendanceFilter
import com.amazecc.app.shared.domain.overhaul.M3EAttendanceAction
import com.amazecc.app.shared.domain.overhaul.M3EAttendanceState
import com.amazecc.app.shared.domain.overhaul.M3ECardTone
import com.amazecc.app.shared.domain.overhaul.M3ECourseAttendanceCardState
import com.amazecc.app.shared.ui.m3e.components.M3EConnectedButtonGroup
import com.amazecc.app.shared.ui.m3e.components.M3ESurfaceCard
import com.amazecc.app.shared.ui.m3e.components.M3EWavyLinearProgress
import com.amazecc.app.shared.ui.m3e.components.SurfaceContainerLevel
import com.amazecc.app.shared.ui.m3e.theme.AmazeM3ETheme
import com.amazecc.app.shared.ui.m3e.theme.M3ETheme
import com.amazecc.app.shared.utils.toFixed
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * Material 3 Expressive Attendance Hub.
 * 100% Stateless: accepts [M3EAttendanceState] and emits [M3EAttendanceAction].
 * Features signature M3E wavy squiggly progress indicators and connected button filters.
 */
@Composable
fun M3EAttendanceScreen(
    state: M3EAttendanceState,
    onAction: (M3EAttendanceAction) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = M3ETheme.colors

    // D-189 follow-up: only real WARNING/CRITICAL figures are "at risk". A merged pair has no single
    // percentage, and a course with no published figure is NEUTRAL, so neither pads this view. A lone
    // Embedded Theory/Lab half with a real low figure keeps its own tone and stays here. The
    // predicate lives on the state model so the stateless screen and the host bridge agree.
    val visibleCourses = if (state.activeFilter == AttendanceFilter.AT_RISK) {
        state.courses.filter { it.isAtRisk }
    } else {
        state.courses
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Header — D-138: a real, expressive entry point into the separate bunk predictor.
            item(key = "header") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Attendance Hub",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.onBackground
                        )
                        Text(
                            text = "${state.semesterName} · Overall ${state.overallPercentage.toFixed(2)}%",
                            fontSize = 13.sp,
                            color = colors.primary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Button(
                        onClick = { onAction(M3EAttendanceAction.OpenPredictor) },
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.primary,
                            contentColor = colors.onPrimary
                        ),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.TrendingUp,
                            contentDescription = "Open attendance predictor",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "Predictor",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // 2. Search — the host already filters on this query; emit it so the field works.
            item(key = "attendance_search") {
                OutlinedTextField(
                    value = state.searchQuery,
                    onValueChange = { onAction(M3EAttendanceAction.UpdateSearch(it)) },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = {
                        Text(
                            text = "Search course code, title or faculty",
                            fontSize = 14.sp,
                            color = colors.onSurfaceVariant
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.Search,
                            contentDescription = null,
                            tint = colors.primary
                        )
                    },
                    trailingIcon = if (state.searchQuery.isBlank()) null else {
                        {
                            IconButton(
                                onClick = { onAction(M3EAttendanceAction.UpdateSearch("")) }
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = "Clear search",
                                    tint = colors.onSurfaceVariant
                                )
                            }
                        }
                    },
                    singleLine = true,
                    shape = CircleShape,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = colors.onSurface,
                        unfocusedTextColor = colors.onSurface,
                        focusedContainerColor = colors.surfaceContainerLow,
                        unfocusedContainerColor = colors.surfaceContainerLowest,
                        focusedBorderColor = colors.primary,
                        unfocusedBorderColor = colors.outlineVariant,
                        focusedPlaceholderColor = colors.onSurfaceVariant,
                        unfocusedPlaceholderColor = colors.onSurfaceVariant
                    )
                )
            }

            // 3. Bunk Summary Banner
            item(key = "bunk_summary") {
                M3EBunkSummaryCard(summary = state.bunkSummary)
            }

            // 3. Connected Button Group Filter
            item(key = "filters") {
                M3EConnectedButtonGroup(
                    items = AttendanceFilter.entries,
                    selectedItem = state.activeFilter,
                    onItemSelected = { onAction(M3EAttendanceAction.SetFilter(it)) },
                    labelProvider = {
                        when (it) {
                            AttendanceFilter.ALL -> "All"
                            AttendanceFilter.SAFE -> "Safe"
                            AttendanceFilter.AT_RISK -> "At Risk"
                            AttendanceFilter.EMBEDDED -> "Embedded"
                        }
                    }
                )
            }

            // 4. Course Cards — never claim "no matches" while the first load is still running.
            when {
                state.isLoading -> {
                    item(key = "loading_skeleton") {
                        M3EAttendanceLoadingSkeleton()
                    }
                }
                visibleCourses.isEmpty() -> {
                    item(key = "empty_state") {
                        M3ESurfaceCard(
                            modifier = Modifier.fillMaxWidth(),
                            containerLevel = SurfaceContainerLevel.LOW
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "No courses match the active filter.",
                                    color = colors.onSurfaceVariant,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }
                }
                else -> {
                    items(
                        items = visibleCourses,
                        key = { it.courseCode }
                    ) { course ->
                        M3ECourseAttendanceCard(
                            course = course,
                            targetPercentage = state.bunkSummary.targetPercentage,
                            onClick = { onAction(M3EAttendanceAction.CourseClicked(course.courseCode)) }
                        )
                    }
                }
            }

            // Bottom Spacing for floating toolbar (twice nav bar height: 140dp)
            item(key = "bottom_spacer") {
                Spacer(Modifier.height(140.dp))
            }
        }
    }
}

@Composable
private fun M3EAttendanceLoadingSkeleton() {
    val colors = M3ETheme.colors

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Loading courses…",
            fontSize = 13.sp,
            color = colors.onSurfaceVariant
        )
        repeat(3) {
            M3ESurfaceCard(
                modifier = Modifier.fillMaxWidth(),
                containerLevel = SurfaceContainerLevel.LOW
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.45f)
                            .height(16.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(colors.surfaceContainerHighest)
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(18.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(colors.surfaceContainerHigh)
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.72f)
                            .height(14.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(colors.surfaceContainerHighest)
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(colors.surfaceContainerHigh)
                    )
                }
            }
        }
    }
}

@Composable
private fun M3EBunkSummaryCard(
    summary: com.amazecc.app.shared.domain.overhaul.M3EBunkSummary
) {
    val colors = M3ETheme.colors

    M3ESurfaceCard(
        modifier = Modifier.fillMaxWidth(),
        containerLevel = SurfaceContainerLevel.STANDARD,
        borderColor = if (summary.criticalCoursesCount > 0) colors.error else colors.primary
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (summary.criticalCoursesCount > 0) Icons.Rounded.Warning else Icons.Rounded.CheckCircle,
                        contentDescription = null,
                        tint = if (summary.criticalCoursesCount > 0) colors.error else colors.success,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (summary.criticalCoursesCount > 0) {
                            "${summary.criticalCoursesCount} Course${if (summary.criticalCoursesCount > 1) "s" else ""} Below ${summary.targetPercentage.toInt()}%"
                        } else {
                            "Safe to Bunk ~${summary.totalBunkableClasses} Classes"
                        },
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.onSurface
                    )
                }
            }

            Text(
                text = if (summary.criticalCoursesCount > 0) {
                    "Critical courses require immediate attendance to regain eligibility."
                } else {
                    "All courses maintain safe margins above institute minimum."
                },
                fontSize = 12.sp,
                color = colors.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun M3ECourseAttendanceCard(
    course: M3ECourseAttendanceCardState,
    targetPercentage: Float = 75f,
    onClick: () -> Unit
) {
    val colors = M3ETheme.colors

    // D-189 follow-up: only a genuinely merged pair carries a 0.0 sentinel in `percentage` (the sum
    // of two different classes, not one attendance figure), with its real figures in the half fields.
    // A lone Embedded Theory/Lab half is NOT a pair — it has one real published percentage and must
    // render it (and stay eligible for At Risk) just like any standalone course.
    val isPair = course.isMergedPair
    // A standalone course with no published figure must not fabricate a 0.00% headline or a 0% bar.
    // totalClasses > 0 means a real published figure, including a genuine 0% (attended = 0).
    val hasPublishedFigure = course.totalClasses > 0

    val statusColor = when (course.tone) {
        M3ECardTone.GOOD -> colors.success
        M3ECardTone.WARNING -> colors.warning
        M3ECardTone.CRITICAL -> colors.error
        M3ECardTone.NEUTRAL -> colors.onSurfaceVariant
        else -> colors.onSurfaceVariant
    }

    M3ESurfaceCard(
        modifier = Modifier.fillMaxWidth(),
        containerLevel = SurfaceContainerLevel.STANDARD,
        onClick = onClick
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // Course Code, Type Badge & Percentage
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(colors.surfaceContainerHighest)
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = course.badge,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.primary
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = course.courseCode,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.primary
                    )
                }

                // Headline Attendance % — a pair gets its two halves' own figures, never the
                // 0.00% sentinel. Standalone cards keep their single published percentage.
                if (isPair) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        val theory = course.theoryPct
                        val lab = course.labPct
                        if (theory == null && lab == null) {
                            Text(
                                text = "Attendance not published",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = statusColor
                            )
                        } else {
                            theory?.let {
                                Text(
                                    text = "Theory ${it.toFixed(2, "%")}",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = statusColor
                                )
                            }
                            lab?.let {
                                Text(
                                    text = "Lab ${it.toFixed(2, "%")}",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = statusColor
                                )
                            }
                        }
                    }
                } else if (hasPublishedFigure) {
                    Text(
                        text = course.percentage.toFixed(2, "%"),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = statusColor
                    )
                } else {
                    // No published figure: honest absence, never a fabricated 0.00%.
                    Text(
                        text = "Attendance not published",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = statusColor
                    )
                }
            }

            // Course Title
            Text(
                text = course.courseTitle,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.onSurface
            )

            // Signature M3E Wavy Linear Progress Bar! A pair shows one bar per published half;
            // an unpublished half gets no bar at all (a 0% bar would be a fabricated figure).
            if (isPair) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    course.theoryPct?.let { theory ->
                        M3EWavyLinearProgress(
                            progress = (theory / 100.0).toFloat(),
                            activeColor = statusColor,
                            trackColor = colors.surfaceContainerHighest,
                            label = "Theory attendance",
                            safetyThreshold = targetPercentage / 100f
                        )
                    }
                    course.labPct?.let { lab ->
                        M3EWavyLinearProgress(
                            progress = (lab / 100.0).toFloat(),
                            activeColor = statusColor,
                            trackColor = colors.surfaceContainerHighest,
                            label = "Lab attendance",
                            safetyThreshold = targetPercentage / 100f
                        )
                    }
                }
            } else if (hasPublishedFigure) {
                M3EWavyLinearProgress(
                    progress = (course.percentage / 100.0).toFloat(),
                    activeColor = statusColor,
                    trackColor = colors.surfaceContainerHighest,
                    label = "Attendance",
                    safetyThreshold = targetPercentage / 100f
                )
            }
            // No published figure: deliberately no bar — a 0% bar would be a fabricated figure.

            // Classes Count & Bunk Summary
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${course.attendedClasses}/${course.totalClasses} Attended",
                    fontSize = 12.sp,
                    color = colors.onSurfaceVariant
                )
                Text(
                    text = course.bunkSummary,
                    fontSize = 12.sp,
                    color = statusColor,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

private fun createMockAttendanceState() = M3EAttendanceState(
    semesterName = "Winter Semester 2025-26",
    overallPercentage = 84.6,
    bunkSummary = com.amazecc.app.shared.domain.overhaul.M3EBunkSummary(
        totalBunkableClasses = 5,
        criticalCoursesCount = 1,
        warningCoursesCount = 0
    ),
    courses = listOf(
        M3ECourseAttendanceCardState(
            courseCode = "CSE3002",
            courseTitle = "Internet and Web Programming",
            badge = "Embedded",
            attendedClasses = 28,
            totalClasses = 30,
            percentage = 0.0, // sentinel only — a pair is two classes, never one blended figure
            tone = M3ECardTone.NEUTRAL,
            faculty = "Prof. Sundar",
            slot = "B1+TB1",
            bunkSummary = "Theory 93% · Lab 89%",
            isEmbedded = true,
            theoryPct = 93.3,
            labPct = 89.0,
            isMergedPair = true
        ),
        M3ECourseAttendanceCardState(
            courseCode = "MAT2001",
            courseTitle = "Complex Variables & Linear Algebra",
            badge = "Theory",
            attendedClasses = 22,
            totalClasses = 30,
            percentage = 73.3,
            tone = M3ECardTone.CRITICAL,
            faculty = "Dr. Anita",
            slot = "D1+TD1",
            bunkSummary = "Need to attend next 2 classes to hit 75%"
        )
    ),
    activeFilter = AttendanceFilter.ALL
)

@Preview
@Composable
private fun M3EAttendanceScreenPreview() {
    AmazeM3ETheme(isDark = false) {
        M3EAttendanceScreen(state = createMockAttendanceState(), onAction = {})
    }
}

@Preview
@Composable
private fun M3EAttendanceScreenDarkPreview() {
    AmazeM3ETheme(isDark = true) {
        M3EAttendanceScreen(state = createMockAttendanceState(), onAction = {})
    }
}
