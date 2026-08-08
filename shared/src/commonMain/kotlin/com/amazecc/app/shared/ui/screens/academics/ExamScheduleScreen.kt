package com.amazecc.app.shared.ui.screens.academics

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amazecc.app.shared.state.AppState
import com.amazecc.app.shared.theme.AmazeTheme
import com.amazecc.app.shared.ui.components.BOTTOM_NAV_PADDING
import com.amazecc.app.shared.ui.components.AmazeCard
import com.amazecc.app.shared.ui.components.ScreenHeader
import com.amazecc.app.shared.ui.components.HeaderSpacer

@Composable
fun ExamScheduleScreen() {
    val colors = AmazeTheme.colors
    val semesterMap by AppState.semesterMap.collectAsState()
    val allExams by AppState.allSemesterExams.collectAsState()
    val examData by AppState.examSchedule.collectAsState()
    val selectedSemId by AppState.selectedExamSemester.collectAsState()
    val isAppLoading by AppState.isLoading.collectAsState()

    val schedule = examData?.schedule ?: emptyMap()

    val availableSemesters = remember(allExams) {
        val filtered = AppState.semesterIDs.filter {
            val res = allExams[it]
            res != null && res.schedule.isNotEmpty()
        }
        if (filtered.isNotEmpty()) filtered else AppState.semesterIDs
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        ScreenHeader(
            title = "Exam Schedule",
            description = "Upcoming exams, seating, and venue",
            showBackButton = true,
            showSyncButton = true,
            onRefresh = AppState::refreshExamSchedule
        )

        HeaderSpacer()

        if (availableSemesters.size > 1) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                availableSemesters.forEach { id ->
                    val semName = semesterMap[id]?.let { full ->
                        if (id.endsWith("1")) "FS ${full.take(4).takeLast(2)}" else "WS ${full.take(4).takeLast(2)}"
                    } ?: id.takeLast(4)
                    val isActive = id == selectedSemId
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(AmazeTheme.radius.small))
                            .background(if (isActive) colors.accent else colors.surface)
                            .border(if (isActive) 0.dp else 1.dp, colors.border, RoundedCornerShape(AmazeTheme.radius.small))
                            .clickable { AppState.selectExamSemester(id) }
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Text(
                            semName,
                            style = AmazeTheme.typography.smallLabel.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = if (isActive) Color.White else colors.textSecondary
                            )
                        )
                    }
                }
            }
        }

        if (isAppLoading && schedule.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = colors.accent, modifier = Modifier.size(32.dp))
            }
            return
        }

        if (schedule.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Rounded.EventBusy, null, tint = colors.textMuted, modifier = Modifier.size(48.dp))
                    Spacer(Modifier.height(AmazeTheme.spacing.sm))
                    Text("No exams scheduled", color = colors.textSecondary)
                }
            }
            return
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = BOTTOM_NAV_PADDING)
        ) {

            schedule.forEach { (type, exams) ->
                item {
                    Text(
                        text = type.uppercase(),
                        style = AmazeTheme.typography.subheading.copy(fontWeight = FontWeight.Bold, color = colors.accent),
                        modifier = Modifier.padding(vertical = 12.dp, horizontal = 4.dp)
                    )
                }

                items(exams, key = { "${it.courseCode}-${it.examDate}-${it.slot}" }) { exam ->
                    AmazeCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                        backgroundColor = colors.surface
                    ) {
                        Column(modifier = Modifier.padding(4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(42.dp)
                                        .clip(RoundedCornerShape(AmazeTheme.radius.small))
                                        .background(colors.accent.copy(alpha = 0.12f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.AutoMirrored.Rounded.MenuBook, contentDescription = null, tint = colors.accent)
                                }
                                Spacer(modifier = Modifier.width(AmazeTheme.spacing.sm))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = exam.courseCode,
                                        style = AmazeTheme.typography.smallLabel.copy(color = colors.accent, fontWeight = FontWeight.Bold)
                                    )
                                    Text(
                                        text = exam.courseTitle,
                                        style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(AmazeTheme.spacing.sm))
                            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(colors.border.copy(alpha = 0.5f)))
                            Spacer(modifier = Modifier.height(AmazeTheme.spacing.sm))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                ExamDetailItem(icon = Icons.Rounded.CalendarToday, label = "Date", value = exam.examDate, color = colors.textPrimary)
                                ExamDetailItem(icon = Icons.Rounded.AccessTime, label = "Time", value = exam.examTime, color = colors.textPrimary)
                            }
                            Spacer(modifier = Modifier.height(AmazeTheme.spacing.sm))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                ExamDetailItem(icon = Icons.Rounded.Place, label = "Venue", value = exam.venue, color = colors.chart3)
                                ExamDetailItem(icon = Icons.Rounded.EventSeat, label = "Seat", value = exam.seatNo, color = colors.chart1)
                            }
                        }
                    }
                }
            }
            item { Spacer(modifier = Modifier.height(88.dp)) }
        }
    }
}

@Composable
private fun ExamDetailItem(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String, color: Color) {
    val colors = AmazeTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = colors.textMuted, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(AmazeTheme.spacing.xs))
        Column {
            Text(label, style = AmazeTheme.typography.caption.copy(color = colors.textMuted))
            Text(value.ifBlank { "TBD" }, style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.SemiBold, color = color))
        }
    }
}
