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
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Icon
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
import com.amazecc.app.shared.ui.m3e.theme.M3ETheme

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
            // 1. Header
            item(key = "header") {
                Column {
                    Text(
                        text = "Attendance Hub",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.onBackground
                    )
                    Text(
                        text = "${state.semesterName} · Overall ${state.overallPercentage}%",
                        fontSize = 13.sp,
                        color = colors.primary,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // 2. Bunk Summary Banner
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

            // 4. Course Cards
            if (state.courses.isEmpty()) {
                item(key = "empty_state") {
                    M3ESurfaceCard(
                        modifier = Modifier.fillMaxWidth(),
                        containerLevel = SurfaceContainerLevel.LOW
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
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
            } else {
                items(
                    items = state.courses,
                    key = { it.courseCode }
                ) { course ->
                    M3ECourseAttendanceCard(
                        course = course,
                        onClick = { onAction(M3EAttendanceAction.CourseClicked(course.courseCode)) }
                    )
                }
            }

            // Bottom Spacing for floating toolbar
            item(key = "bottom_spacer") {
                Spacer(Modifier.height(72.dp))
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
    onClick: () -> Unit
) {
    val colors = M3ETheme.colors

    val statusColor = when (course.tone) {
        M3ECardTone.GOOD -> colors.success
        M3ECardTone.WARNING -> colors.warning
        M3ECardTone.CRITICAL -> colors.error
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

                // Headline Attendance %
                Text(
                    text = "${course.percentage}%",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = statusColor
                )
            }

            // Course Title
            Text(
                text = course.courseTitle,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.onSurface
            )

            // Signature M3E Wavy Linear Progress Bar!
            M3EWavyLinearProgress(
                progress = (course.percentage / 100.0).toFloat(),
                activeColor = statusColor,
                trackColor = colors.surfaceContainerHighest
            )

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
