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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.School
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amazecc.app.shared.domain.overhaul.M3EClassTimelineItem
import com.amazecc.app.shared.domain.overhaul.M3EHomeAction
import com.amazecc.app.shared.domain.overhaul.M3EHomeState
import com.amazecc.app.shared.ui.m3e.components.M3ESurfaceCard
import com.amazecc.app.shared.ui.m3e.components.M3EWeekStrip
import com.amazecc.app.shared.ui.m3e.components.SurfaceContainerLevel
import com.amazecc.app.shared.ui.m3e.theme.M3ETheme

/**
 * Modern Material 3 Expressive Home Screen for AmazeCC.
 * 100% Stateless: accepts [M3EHomeState] and emits [M3EHomeAction].
 * Implements the ../AmazeCC web app interface natively in M3E.
 */
@Composable
fun M3EHomeScreen(
    state: M3EHomeState,
    onAction: (M3EHomeAction) -> Unit,
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
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // 1. Header (Greeting + Student Identity + Refresh)
            item(key = "header") {
                M3EHomeHeader(
                    name = state.identity.name,
                    regNumber = state.identity.regNumber,
                    campus = state.identity.campus,
                    onRefresh = { onAction(M3EHomeAction.RefreshData) }
                )
            }

            // 2. Week Strip
            item(key = "week_strip") {
                M3EWeekStrip(
                    days = state.weekDays,
                    onDaySelected = { date -> onAction(M3EHomeAction.SelectDay(date)) }
                )
            }

            // 3. Quick Stats Grid
            item(key = "quick_stats") {
                M3EQuickStatsRow(
                    stats = state.quickStats,
                    onAttendanceClick = { onAction(M3EHomeAction.OpenAttendanceHub) },
                    onAcademicsClick = { onAction(M3EHomeAction.OpenAcademicsHub) }
                )
            }

            // 4. Section Title: Today's Schedule
            item(key = "schedule_header") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Today's Classes",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.onBackground
                    )
                    Text(
                        text = "View Timetable",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.primary,
                        modifier = Modifier.clickable { onAction(M3EHomeAction.OpenTimetableHub) }
                    )
                }
            }

            // 5. Classes Timeline
            if (state.todayClasses.isEmpty()) {
                item(key = "empty_classes") {
                    M3ESurfaceCard(
                        modifier = Modifier.fillMaxWidth(),
                        containerLevel = SurfaceContainerLevel.LOW
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "No classes scheduled for today! 🎉",
                                color = colors.onSurfaceVariant,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            } else {
                items(
                    items = state.todayClasses,
                    key = { "${it.courseCode}_${it.slot}" }
                ) { classItem ->
                    M3EClassTimelineCard(
                        item = classItem,
                        onClick = { onAction(M3EHomeAction.ClickCourse(classItem.courseCode)) }
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
private fun M3EHomeHeader(
    name: String,
    regNumber: String,
    campus: String,
    onRefresh: () -> Unit
) {
    val colors = M3ETheme.colors

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = "Welcome back,",
                fontSize = 13.sp,
                color = colors.onSurfaceVariant,
                fontWeight = FontWeight.Normal
            )
            Text(
                text = name.ifBlank { "Student" },
                fontSize = 22.sp,
                color = colors.onBackground,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "$regNumber · $campus Campus",
                fontSize = 12.sp,
                color = colors.primary,
                fontWeight = FontWeight.Medium
            )
        }

        IconButton(
            onClick = onRefresh,
            modifier = Modifier
                .clip(CircleShape)
                .background(colors.surfaceContainerHigh)
        ) {
            Icon(
                imageVector = Icons.Rounded.Refresh,
                contentDescription = "Refresh",
                tint = colors.onSurface
            )
        }
    }
}

@Composable
private fun M3EQuickStatsRow(
    stats: com.amazecc.app.shared.domain.overhaul.M3EQuickStatsState,
    onAttendanceClick: () -> Unit,
    onAcademicsClick: () -> Unit
) {
    val colors = M3ETheme.colors

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Attendance Stat Tile
        M3ESurfaceCard(
            modifier = Modifier.weight(1f),
            containerLevel = SurfaceContainerLevel.STANDARD,
            onClick = onAttendanceClick
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.CheckCircle,
                        contentDescription = null,
                        tint = if (stats.attendancePercentage >= 75.0) colors.success else colors.error,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "Attendance",
                        fontSize = 12.sp,
                        color = colors.onSurfaceVariant,
                        fontWeight = FontWeight.Medium
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "${stats.attendancePercentage}%",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.onSurface
                )
                Text(
                    text = stats.attendanceStatusText,
                    fontSize = 11.sp,
                    color = colors.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }

        // CGPA / Academics Stat Tile
        M3ESurfaceCard(
            modifier = Modifier.weight(1f),
            containerLevel = SurfaceContainerLevel.STANDARD,
            onClick = onAcademicsClick
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.School,
                        contentDescription = null,
                        tint = colors.secondary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "Academics",
                        fontSize = 12.sp,
                        color = colors.onSurfaceVariant,
                        fontWeight = FontWeight.Medium
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "${stats.cgpa}",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.onSurface
                )
                Text(
                    text = "${stats.creditsEarned} Credits Earned",
                    fontSize = 11.sp,
                    color = colors.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun M3EClassTimelineCard(
    item: M3EClassTimelineItem,
    onClick: () -> Unit
) {
    val colors = M3ETheme.colors

    M3ESurfaceCard(
        modifier = Modifier.fillMaxWidth(),
        containerLevel = if (item.isHappeningNow) SurfaceContainerLevel.HIGH else SurfaceContainerLevel.STANDARD,
        borderColor = if (item.isHappeningNow) colors.primary else null,
        onClick = onClick
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(colors.primaryContainer)
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = item.slot,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.onPrimaryContainer
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = item.courseCode,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.primary
                    )
                }

                if (item.isHappeningNow) {
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(colors.successContainer)
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "NOW",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.success
                        )
                    }
                }
            }

            Text(
                text = item.courseTitle,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.onSurface
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.Schedule,
                        contentDescription = null,
                        tint = colors.onSurfaceVariant,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "${item.startTime} - ${item.endTime}",
                        fontSize = 12.sp,
                        color = colors.onSurfaceVariant
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.LocationOn,
                        contentDescription = null,
                        tint = colors.onSurfaceVariant,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = item.venue,
                        fontSize = 12.sp,
                        color = colors.onSurfaceVariant,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}
