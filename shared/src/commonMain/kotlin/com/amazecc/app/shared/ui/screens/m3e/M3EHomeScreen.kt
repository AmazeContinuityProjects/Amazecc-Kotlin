package com.amazecc.app.shared.ui.screens.m3e

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import com.amazecc.app.shared.ui.components.BOTTOM_NAV_PADDING
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amazecc.app.shared.domain.overhaul.M3EClassTimelineItem
import com.amazecc.app.shared.domain.overhaul.M3EHomeAction
import com.amazecc.app.shared.domain.overhaul.M3EHomeState
import com.amazecc.app.shared.domain.overhaul.M3EQuickStatsState
import com.amazecc.app.shared.domain.overhaul.M3EStatTone
import com.amazecc.app.shared.domain.overhaul.M3EUserIdentityState
import com.amazecc.app.shared.domain.overhaul.M3EWeekDay
import com.amazecc.app.shared.ui.m3e.components.M3EInsightCarouselCard
import com.amazecc.app.shared.ui.m3e.components.M3EStatCard
import com.amazecc.app.shared.ui.m3e.components.M3EStatItem
import com.amazecc.app.shared.ui.m3e.components.M3ESurfaceCard
import com.amazecc.app.shared.ui.m3e.components.M3EWeekStrip
import com.amazecc.app.shared.ui.m3e.components.SurfaceContainerLevel
import com.amazecc.app.shared.ui.m3e.theme.AmazeM3ETheme
import com.amazecc.app.shared.ui.m3e.theme.M3ETheme
import com.amazecc.app.shared.utils.toFixed
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.ui.tooling.preview.Preview

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
    // Read once so the smart cast survives inside the LazyColumn's lambdas.
    val errorMessage = state.errorMessage

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
                    semesterName = state.semesterName,
                    onRefresh = { onAction(M3EHomeAction.RefreshData) },
                    isRefreshing = state.isRefreshing
                )
            }

            // 1b. Backend failure banner — real error text, retry and dismiss both wired.
            if (!errorMessage.isNullOrBlank()) {
                item(key = "error_banner") {
                    M3EHomeErrorBanner(
                        message = errorMessage,
                        onRetry = { onAction(M3EHomeAction.RefreshData) },
                        onDismiss = { onAction(M3EHomeAction.DismissError) }
                    )
                }
            }

            // 2. Expressive Dual Stat Cards: Constant Attendance Card + Insight Carousel Card (Exactly two cards side-by-side)
            item(key = "home_stat_cards") {
                val attendanceStat = remember(state.quickStats) {
                    M3EStatItem(
                        id = "stat_attendance",
                        label = "Attendance",
                        value = state.quickStats.attendancePercentage.toFixed(2, "%"),
                        // Always state the threshold next to the headline number so the
                        // percentage has an explicit meaning instead of a bare value.
                        sub = listOfNotNull(
                            "75% safety target",
                            state.quickStats.attendanceStatusText.takeIf { it.isNotBlank() }
                        ).joinToString(" · "),
                        tone = if (state.quickStats.attendancePercentage >= 75.0) M3EStatTone.GOOD else M3EStatTone.CRITICAL,
                        icon = Icons.Rounded.CheckCircle,
                        onClick = { onAction(M3EHomeAction.OpenAttendanceHub) }
                    )
                }

                val insightItems = remember(state.quickStats) {
                    listOf(
                        M3EStatItem(
                            id = "stat_cgpa",
                            label = "Cumulative GPA",
                            value = state.quickStats.cgpa.toFixed(2),
                            sub = "Academic standing",
                            tone = M3EStatTone.PRIMARY,
                            icon = Icons.Rounded.School,
                            onClick = { onAction(M3EHomeAction.OpenAcademicsHub) }
                        ),
                        M3EStatItem(
                            id = "stat_safe_bunks",
                            label = "Safe Bunks",
                            value = "${state.quickStats.safeBunksAvailable}",
                            sub = if (state.quickStats.safeBunksAvailable > 0) {
                                "${state.quickStats.safeBunksAvailable} bunks available"
                            } else {
                                "No bunks left"
                            },
                            tone = if (state.quickStats.safeBunksAvailable > 0) M3EStatTone.TERTIARY else M3EStatTone.WARNING,
                            icon = Icons.Rounded.Schedule,
                            onClick = { onAction(M3EHomeAction.OpenAttendanceHub) }
                        ),
                        M3EStatItem(
                            id = "stat_credits",
                            label = "Credits Earned",
                            value = "${state.quickStats.creditsEarned}",
                            sub = "${state.quickStats.creditsEarned} Credits",
                            tone = M3EStatTone.SECONDARY,
                            icon = Icons.Rounded.Insights,
                            onClick = { onAction(M3EHomeAction.OpenAcademicsHub) }
                        )
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    M3EStatCard(
                        stat = attendanceStat,
                        modifier = Modifier
                            .weight(1f)
                            .height(136.dp)
                    )
                    M3EInsightCarouselCard(
                        items = insightItems,
                        modifier = Modifier
                            .weight(1f)
                            .height(136.dp)
                    )
                }
            }

            // 3. Week Strip
            item(key = "week_strip") {
                M3EWeekStrip(
                    days = state.weekDays,
                    onDaySelected = { date -> onAction(M3EHomeAction.SelectDay(date)) }
                )
            }

            // 4. Section Title: Schedule
            item(key = "schedule_header") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val isSelectedToday = state.selectedDayDate == state.weekDays.firstOrNull { it.isToday }?.date
                    val headerText = if (isSelectedToday) {
                        "Today's Classes"
                    } else {
                        val selDay = state.weekDays.firstOrNull { it.isSelected }
                        val dayName = selDay?.dayName ?: state.selectedDayDate.dayOfWeek.name.take(3)
                        val monthName = state.selectedDayDate.month.name.lowercase().replaceFirstChar { it.uppercase() }.take(3)
                        "$dayName's Classes (${state.selectedDayDate.dayOfMonth} $monthName)"
                    }
                    Text(
                        text = headerText,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.onBackground
                    )
                    TextButton(
                        onClick = { onAction(M3EHomeAction.OpenTimetableHub) },
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) {
                        Text(
                            text = "View Timetable",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.primary
                        )
                    }
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
                            val isSelectedToday = state.selectedDayDate == state.weekDays.firstOrNull { it.isToday }?.date
                            Text(
                                text = if (isSelectedToday) {
                                    "No classes scheduled for today."
                                } else {
                                    "No classes scheduled for this day."
                                },
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
                    key = { "${it.courseCode}_${it.slot}_${it.startTime}" }
                ) { classItem ->
                    M3EClassTimelineCard(
                        item = classItem,
                        onClick = { onAction(M3EHomeAction.ClickCourse(classItem.courseCode)) }
                    )
                }
            }

            // Bottom spacing for the floating navigation (named, inset-aware token)
            item(key = "bottom_spacer") {
                Spacer(Modifier.height(BOTTOM_NAV_PADDING))
            }
        }
    }
}

@Composable
private fun M3EHomeHeader(
    name: String,
    semesterName: String,
    onRefresh: () -> Unit,
    isRefreshing: Boolean = false
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
            // Only the real selected-semester name; never a reg-no/campus fallback.
            if (semesterName.isNotBlank()) {
                Text(
                    text = semesterName,
                    fontSize = 12.sp,
                    color = colors.primary,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        IconButton(
            onClick = onRefresh,
            enabled = !isRefreshing,
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
private fun M3EHomeErrorBanner(
    message: String,
    onRetry: () -> Unit,
    onDismiss: () -> Unit
) {
    val colors = M3ETheme.colors

    M3ESurfaceCard(
        modifier = Modifier.fillMaxWidth(),
        containerLevel = SurfaceContainerLevel.HIGH,
        borderColor = colors.error
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Rounded.Warning,
                contentDescription = null,
                tint = colors.error,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Couldn't load your latest data",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.onSurface
                )
                Text(
                    text = message,
                    fontSize = 12.sp,
                    color = colors.onSurfaceVariant
                )
            }
            TextButton(onClick = onRetry) {
                Text(text = "Retry", fontWeight = FontWeight.SemiBold, color = colors.error)
            }
            IconButton(onClick = onDismiss, modifier = Modifier.size(48.dp)) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "Dismiss error",
                    tint = colors.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun M3EQuickStatsRow(
    stats: M3EQuickStatsState,
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
                    text = stats.attendancePercentage.toFixed(2, "%"),
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
                    text = stats.cgpa.toFixed(2),
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

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Real component attendance, hidden entirely when the source published none.
                    item.attendancePercentage?.let { M3EClassAttendanceBadge(it) }
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

private fun formatClassAttendance(percentage: Double): String =
    if (percentage % 1.0 == 0.0) "${percentage.toInt()}%" else "${percentage.toFixed(1)}%"

@Composable
private fun M3EClassAttendanceBadge(percentage: Double) {
    val colors = M3ETheme.colors
    val text = formatClassAttendance(percentage)
    // Same 75% line the home headline stat already uses, so the two never disagree.
    val isSafe = percentage >= 75.0
    val tone = if (isSafe) colors.success else colors.error
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(CircleShape)
            .background(if (isSafe) colors.successContainer else colors.errorContainer)
            .padding(horizontal = 8.dp, vertical = 2.dp)
            .semantics { contentDescription = "Attendance $text" }
    ) {
        Icon(
            imageVector = Icons.Rounded.Insights,
            contentDescription = null,
            tint = tone,
            modifier = Modifier.size(12.dp)
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = text,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = tone
        )
    }
}

// ==========================================
// IDE PREVIEWS
// ==========================================

private fun createMockHomeState() = M3EHomeState(
    identity = M3EUserIdentityState(
        name = "Aravind Kumar",
        regNumber = "21BCE1042",
        programme = "B.Tech CSE",
        school = "SCOPE",
        campus = "Vellore"
    ),
    weekDays = listOf(
        M3EWeekDay(
            date = LocalDate(2026, 9, 29),
            dayName = "Tue",
            dayNumber = 29,
            isToday = true,
            isSelected = true,
            hasClasses = true,
            isHoliday = false
        ),
        M3EWeekDay(
            date = LocalDate(2026, 9, 30),
            dayName = "Wed",
            dayNumber = 30,
            isToday = false,
            isSelected = false,
            hasClasses = true,
            isHoliday = false
        )
    ),
    selectedDayDate = LocalDate(2026, 9, 29),
    quickStats = M3EQuickStatsState(
        attendancePercentage = 88.5,
        cgpa = 9.12,
        creditsEarned = 120,
        safeBunksAvailable = 6,
        attendanceStatusText = "Safe margin across all courses"
    ),
    todayClasses = listOf(
        M3EClassTimelineItem(
            courseCode = "CSE3002",
            courseTitle = "Internet and Web Programming",
            slot = "B1+TB1",
            venue = "SJT 412",
            faculty = "Prof. Sundar",
            startTime = "09:00",
            endTime = "09:50",
            isHappeningNow = true,
            attendancePercentage = 88.5
        )
    ),
    insights = emptyList(),
    semesterName = "Fall Sem 2025-2026"
)

@Preview
@Composable
private fun M3EHomeScreenPreview() {
    AmazeM3ETheme(isDark = false) {
        M3EHomeScreen(state = createMockHomeState(), onAction = {})
    }
}

@Preview
@Composable
private fun M3EHomeScreenDarkPreview() {
    AmazeM3ETheme(isDark = true) {
        M3EHomeScreen(state = createMockHomeState(), onAction = {})
    }
}
