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
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.EventAvailable
import androidx.compose.material.icons.rounded.LocationOn
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
import com.amazecc.app.shared.domain.overhaul.ExamSeries
import com.amazecc.app.shared.domain.overhaul.M3EExamEntry
import com.amazecc.app.shared.domain.overhaul.M3EExamScheduleAction
import com.amazecc.app.shared.domain.overhaul.M3EExamScheduleState
import com.amazecc.app.shared.ui.m3e.components.M3EConnectedButtonGroup
import com.amazecc.app.shared.ui.m3e.components.M3ESurfaceCard
import com.amazecc.app.shared.ui.m3e.components.SurfaceContainerLevel
import com.amazecc.app.shared.ui.m3e.theme.AmazeM3ETheme
import com.amazecc.app.shared.ui.m3e.theme.M3ETheme
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * Material 3 Expressive Exam Schedules & Seating Hub.
 * 100% Stateless: accepts [M3EExamScheduleState] and emits [M3EExamScheduleAction].
 * Features Next Exam Countdown Hero, Series filtering, and Venue/Seat allocation cards.
 */
@Composable
fun M3EExamScheduleScreen(
    state: M3EExamScheduleState,
    onAction: (M3EExamScheduleAction) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = M3ETheme.colors

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
            // Header
            item(key = "exam_header") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "Exam Schedules",
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.onSurface
                        )
                        Text(
                            text = state.semesterName,
                            fontSize = 13.sp,
                            color = colors.onSurfaceVariant
                        )
                    }

                    IconButton(
                        onClick = { onAction(M3EExamScheduleAction.ExportCalendar) },
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(colors.primaryContainer)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.EventAvailable,
                            contentDescription = "Export to Calendar",
                            tint = colors.onPrimaryContainer
                        )
                    }
                }
            }

            // Next Upcoming Exam Hero Card
            state.nextUpcomingExam?.let { nextExam ->
                item(key = "next_exam_hero") {
                    M3ESurfaceCard(
                        containerLevel = SurfaceContainerLevel.HIGH,
                        cornerRadius = 24.dp,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { onAction(M3EExamScheduleAction.ExamClicked(nextExam)) }
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .background(colors.primaryContainer)
                                        .padding(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = "UPCOMING EXAM",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = colors.onPrimaryContainer
                                    )
                                }

                                Text(
                                    text = if (nextExam.daysRemaining == 0) "Today" else "In ${nextExam.daysRemaining} days",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.primary
                                )
                            }

                            Text(
                                text = "${nextExam.courseCode} • ${nextExam.courseTitle}",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.onSurface
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.LocationOn,
                                        contentDescription = null,
                                        tint = colors.onSurfaceVariant,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Text(
                                        text = "${nextExam.venue} (Seat ${nextExam.seatNumber})",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = colors.onSurface
                                    )
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.AccessTime,
                                        contentDescription = null,
                                        tint = colors.onSurfaceVariant,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Text(
                                        text = nextExam.examTime,
                                        fontSize = 13.sp,
                                        color = colors.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Series Selector (All, CAT-1, CAT-2, FAT)
            item(key = "series_selector") {
                M3EConnectedButtonGroup(
                    items = ExamSeries.entries,
                    selectedItem = state.selectedSeries,
                    onItemSelected = { onAction(M3EExamScheduleAction.SelectSeries(it)) },
                    labelProvider = { it.label }
                )
            }

            // Exams List
            if (state.filteredExams.isEmpty()) {
                item(key = "empty_exams") {
                    M3ESurfaceCard(
                        containerLevel = SurfaceContainerLevel.LOW,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.CalendarToday,
                                contentDescription = null,
                                tint = colors.onSurfaceVariant,
                                modifier = Modifier.size(40.dp)
                            )
                            Text(
                                text = "No exams scheduled for ${state.selectedSeries.label}",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = colors.onSurface
                            )
                        }
                    }
                }
            } else {
                items(
                    items = state.filteredExams,
                    key = { it.id }
                ) { exam ->
                    M3EExamCard(
                        exam = exam,
                        onClick = { onAction(M3EExamScheduleAction.ExamClicked(exam)) }
                    )
                }
            }

            // Bottom Spacing (twice nav bar height: 140dp)
            item(key = "bottom_spacer") {
                Spacer(Modifier.height(140.dp))
            }
        }
    }
}

@Composable
private fun M3EExamCard(
    exam: M3EExamEntry,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = M3ETheme.colors

    M3ESurfaceCard(
        containerLevel = SurfaceContainerLevel.LOW,
        onClick = onClick,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // Date & Slot Banner Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(colors.primaryContainer)
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "${exam.date} • ${exam.session}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.onPrimaryContainer
                        )
                    }

                    Text(
                        text = "Slot ${exam.slot}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.primary
                    )
                }

                Text(
                    text = exam.examSeries.label,
                    fontSize = 11.sp,
                    color = colors.onSurfaceVariant,
                    fontWeight = FontWeight.Medium
                )
            }

            // Course Code & Title
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = exam.courseCode,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.primary
                )
                Text(
                    text = exam.courseTitle,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.onSurface
                )
            }

            // Venue, Seat & Timings
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.LocationOn,
                        contentDescription = null,
                        tint = colors.onSurfaceVariant,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "${exam.venue} | Seat: ${exam.seatNumber}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = colors.onSurface
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AccessTime,
                        contentDescription = null,
                        tint = colors.onSurfaceVariant,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = exam.examTime,
                        fontSize = 12.sp,
                        color = colors.onSurfaceVariant
                    )
                }
            }
        }
    }
}

private fun createMockExamState() = M3EExamScheduleState(
    semesterName = "Winter Semester 2025-26",
    selectedSeries = ExamSeries.ALL,
    nextUpcomingExam = M3EExamEntry(
        id = "exam_1",
        courseCode = "CSE3002",
        courseTitle = "Internet and Web Programming",
        examSeries = ExamSeries.CAT1,
        date = "12-Oct-2026",
        day = "Monday",
        session = "FN",
        slot = "B1+TB1",
        reportingTime = "09:00 AM",
        examTime = "09:30 AM - 11:15 AM",
        venue = "SJT 412",
        seatNumber = "B-14",
        daysRemaining = 2,
        isHappeningSoon = true
    ),
    exams = listOf(
        M3EExamEntry(
            id = "exam_1",
            courseCode = "CSE3002",
            courseTitle = "Internet and Web Programming",
            examSeries = ExamSeries.CAT1,
            date = "12-Oct-2026",
            day = "Monday",
            session = "FN",
            slot = "B1+TB1",
            reportingTime = "09:00 AM",
            examTime = "09:30 AM - 11:15 AM",
            venue = "SJT 412",
            seatNumber = "B-14",
            daysRemaining = 2,
            isHappeningSoon = true
        )
    ),
    filteredExams = listOf(
        M3EExamEntry(
            id = "exam_1",
            courseCode = "CSE3002",
            courseTitle = "Internet and Web Programming",
            examSeries = ExamSeries.CAT1,
            date = "12-Oct-2026",
            day = "Monday",
            session = "FN",
            slot = "B1+TB1",
            reportingTime = "09:00 AM",
            examTime = "09:30 AM - 11:15 AM",
            venue = "SJT 412",
            seatNumber = "B-14",
            daysRemaining = 2,
            isHappeningSoon = true
        )
    )
)

@Preview
@Composable
private fun M3EExamScheduleScreenPreview() {
    AmazeM3ETheme(isDark = false) {
        M3EExamScheduleScreen(state = createMockExamState(), onAction = {})
    }
}

@Preview
@Composable
private fun M3EExamScheduleScreenDarkPreview() {
    AmazeM3ETheme(isDark = true) {
        M3EExamScheduleScreen(state = createMockExamState(), onAction = {})
    }
}
