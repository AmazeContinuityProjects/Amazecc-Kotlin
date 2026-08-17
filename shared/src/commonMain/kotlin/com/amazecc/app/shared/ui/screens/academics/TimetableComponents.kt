package com.amazecc.app.shared.ui.screens.academics

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amazecc.app.shared.config.SlotMap
import com.amazecc.app.shared.model.AttendanceItem
import com.amazecc.app.shared.state.AcademicDerivers.embeddedComponentLabel
import com.amazecc.app.shared.state.AppState
import com.amazecc.app.shared.theme.AmazeTheme
import com.amazecc.app.shared.ui.components.AmazeCard

@Composable
fun TimetableCard(code: String, title: String, faculty: String, venue: String, slotCode: String, onClick: () -> Unit = {}) {
    val colors = AmazeTheme.colors
    AmazeCard(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(44.dp).clip(CircleShape).background(colors.accent.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Text(slotCode, style = AmazeTheme.typography.caption.copy(fontWeight = FontWeight.Bold, color = colors.accent))
            }
            Spacer(modifier = Modifier.width(AmazeTheme.spacing.md))
            Column(modifier = Modifier.weight(1f)) {
                Text(code, style = AmazeTheme.typography.smallLabel.copy(color = colors.textMuted))
                Text(title, style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
                Text("Faculty: $faculty", style = AmazeTheme.typography.caption.copy(color = colors.textSecondary))
                Text("Venue: $venue", style = AmazeTheme.typography.caption.copy(fontWeight = FontWeight.Bold, color = colors.accent))
            }
        }
    }
}

@Composable
fun TimetableDialog(
    attendanceCourses: List<AttendanceItem>,
    onDismiss: () -> Unit
) {
    val colors = AmazeTheme.colors
    val days = listOf("MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN")
    var selectedDay by remember { mutableStateOf("MON") }

    fun slotCodes(name: String) = name.uppercase().split('+').map { it.trim() }

    val dayCourses = remember(selectedDay, attendanceCourses) {
        attendanceCourses.filter { course ->
            slotCodes(course.slotName).any { SlotMap.map[selectedDay]?.containsKey(it) == true }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .heightIn(max = 600.dp)
                .clickable(enabled = false) {},
            shape = RoundedCornerShape(AmazeTheme.radius.large),
            color = colors.background,
            tonalElevation = 4.dp
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Weekly Timetable", style = AmazeTheme.typography.subheading.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary))
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Rounded.Close, null, tint = colors.textSecondary)
                    }
                }

                Spacer(Modifier.height(AmazeTheme.spacing.sm))

                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(days, key = { it }) { day ->
                        val isSelected = selectedDay == day
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(AmazeTheme.radius.small))
                                .background(if (isSelected) colors.accent else colors.surface)
                                .clickable { selectedDay = day }
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Text(
                                day,
                                color = if (isSelected) Color.White else colors.textPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = AmazeTheme.fontSize.base
                            )
                        }
                    }
                }

                Spacer(Modifier.height(AmazeTheme.spacing.sm))

                if (dayCourses.isEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text("No classes on $selectedDay", color = colors.textMuted)
                    }
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(dayCourses, key = { it.slotName + it.courseCode }) { course ->
                            val time = slotCodes(course.slotName).firstNotNullOfOrNull { SlotMap.map[selectedDay]?.get(it) } ?: "—"
                            AmazeCard(
                                modifier = Modifier.fillMaxWidth(),
                                onClick = { AppState.openCourseDetail(course.courseCode) }
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier.size(40.dp).clip(RoundedCornerShape(AmazeTheme.radius.small)).background(colors.accent.copy(alpha = 0.15f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(course.slotName.take(3), fontWeight = FontWeight.Bold, fontSize = AmazeTheme.fontSize.xs, color = colors.accent)
                                    }
                                    Spacer(Modifier.width(AmazeTheme.spacing.sm))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(course.courseCode, style = AmazeTheme.typography.smallLabel.copy(color = colors.textMuted))
                                            embeddedComponentLabel(course.courseCode)?.let { label ->
                                                Spacer(Modifier.width(6.dp))
                                                Box(modifier = Modifier.clip(RoundedCornerShape(AmazeTheme.radius.xs)).background(colors.accent.copy(alpha = 0.1f)).padding(horizontal = 6.dp, vertical = 1.dp)) {
                                                    Text(label, style = AmazeTheme.typography.smallLabel.copy(color = colors.accent, fontWeight = FontWeight.Bold, fontSize = AmazeTheme.fontSize.micro))
                                                }
                                            }
                                        }
                                        Text(course.courseTitle, fontWeight = FontWeight.Bold, color = colors.textPrimary, maxLines = 1)
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(time, fontWeight = FontWeight.Bold, fontSize = AmazeTheme.fontSize.sm, color = colors.accent)
                                        Text(course.faculty, fontSize = AmazeTheme.fontSize.micro, color = colors.textSecondary)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}