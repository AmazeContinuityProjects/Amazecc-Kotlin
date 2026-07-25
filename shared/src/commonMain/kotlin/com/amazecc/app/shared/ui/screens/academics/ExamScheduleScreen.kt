package com.amazecc.app.shared.ui.screens.academics

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import com.amazecc.app.shared.ui.components.AmazeCard
import com.amazecc.app.shared.ui.components.ScreenHeader
import com.amazecc.app.shared.ui.components.HeaderSpacer

@Composable
fun ExamScheduleScreen() {
    val colors = AmazeTheme.colors
    val examData by AppState.examSchedule.collectAsState()
    
    val schedule = examData?.schedule ?: emptyMap()
    val isAppLoading by AppState.isLoading.collectAsState()

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
            onRefresh = AppState::refreshCurrentSemester
        )

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
                    Spacer(Modifier.height(12.dp))
                    Text("No exams scheduled", color = colors.textSecondary)
                }
            }
            return
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
        ) {
            item { HeaderSpacer() }
            
            schedule.forEach { (type, exams) ->
                item {
                    Text(
                        text = type.uppercase(),
                        style = AmazeTheme.typography.subheading.copy(fontWeight = FontWeight.Bold, color = colors.accent),
                        modifier = Modifier.padding(vertical = 12.dp, horizontal = 4.dp)
                    )
                }
                
                items(exams) { exam ->
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
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(colors.accent.copy(alpha = 0.12f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Rounded.MenuBook, contentDescription = null, tint = colors.accent)
                                }
                                Spacer(modifier = Modifier.width(12.dp))
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
                            
                            Spacer(modifier = Modifier.height(12.dp))
                            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(colors.border.copy(alpha = 0.5f)))
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                ExamDetailItem(icon = Icons.Rounded.CalendarToday, label = "Date", value = exam.examDate, color = colors.textPrimary)
                                ExamDetailItem(icon = Icons.Rounded.AccessTime, label = "Time", value = exam.examTime, color = colors.textPrimary)
                            }
                            Spacer(modifier = Modifier.height(12.dp))
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
        Spacer(modifier = Modifier.width(6.dp))
        Column {
            Text(label, style = AmazeTheme.typography.caption.copy(color = colors.textMuted, fontSize = 10.sp))
            Text(value.ifBlank { "TBD" }, style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.SemiBold, color = color, fontSize = 13.sp))
        }
    }
}
