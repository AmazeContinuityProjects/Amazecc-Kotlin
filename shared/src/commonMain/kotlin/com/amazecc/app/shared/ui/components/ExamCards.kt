package com.amazecc.app.shared.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.EventSeat
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.amazecc.app.shared.model.ExamItem
import com.amazecc.app.shared.state.AppState
import com.amazecc.app.shared.theme.AmazeColors
import com.amazecc.app.shared.theme.AmazeTheme
import com.amazecc.app.shared.utils.ExamUtils
import com.amazecc.app.shared.utils.examDateParsed
import com.amazecc.app.shared.utils.seatLocationDisplay
import com.amazecc.app.shared.utils.sessionDisplay
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/** Human status for an exam: "PAST", "TODAY", "IN 2d", or "" (no valid date). */
internal fun examStatusText(exam: ExamItem, now: Instant = Clock.System.now()): String {
    val hours = ExamUtils.hoursUntilExam(exam, now) ?: return ""
    return when {
        hours < 0 -> "PAST"
        hours <= 24 -> "TODAY"
        hours < 24 * 14 -> "IN ${(hours / 24).toInt()}d"
        else -> ""
    }
}

@Composable
internal fun ExamDetailRow(icon: ImageVector, label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    val colors = AmazeTheme.colors
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = colors.textMuted, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(AmazeTheme.spacing.xs))
        Column {
            Text(label, style = AmazeTheme.typography.caption.copy(color = colors.textMuted))
            Text(
                value.ifBlank { "TBD" },
                style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.SemiBold, color = color),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
internal fun ExamStatusChip(text: String, color: Color) {
    if (text.isBlank()) return
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(AmazeTheme.radius.xs))
            .background(color.copy(alpha = 0.14f))
            .border(1.dp, color.copy(alpha = 0.4f), RoundedCornerShape(AmazeTheme.radius.xs))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(
            text = text,
            style = AmazeTheme.typography.smallLabel.copy(
                fontWeight = FontWeight.Black,
                color = color,
                fontSize = AmazeTheme.fontSize.micro
            )
        )
    }
}

/** Full exam detail card — used by the Calendar event list. */
@Composable
fun ExamEventCard(
    exam: ExamItem,
    examType: String = "",
    colors: AmazeColors = AmazeTheme.colors,
    onClick: () -> Unit = {}
) {
    val status = remember(exam) { examStatusText(exam) }
    val typeLabel = examType.uppercase().ifBlank { "EXAM" }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        AmazeCard(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(AmazeTheme.radius.small))
                            .background(colors.chart1.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Rounded.EventSeat, contentDescription = null, tint = colors.chart1, modifier = Modifier.size(20.dp))
                    }
                    Spacer(modifier = Modifier.width(AmazeTheme.spacing.sm))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = exam.courseCode,
                                style = AmazeTheme.typography.smallLabel.copy(color = colors.chart1, fontWeight = FontWeight.Black)
                            )
                            if (exam.slot.isNotBlank() && exam.slot != "-") {
                                Spacer(modifier = Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(AmazeTheme.radius.xs))
                                        .background(colors.accentSurface.copy(alpha = 0.5f))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = exam.slot,
                                        style = AmazeTheme.typography.smallLabel.copy(
                                            color = colors.accent,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = AmazeTheme.fontSize.micro
                                        )
                                    )
                                }
                            }
                        }
                        Text(
                            text = exam.courseTitle,
                            style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    ExamStatusChip(text = status, color = if (status == "TODAY") colors.success else if (status == "PAST") colors.textMuted else colors.chart1)
                }

                Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(colors.border.copy(alpha = 0.5f)))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    ExamDetailRow(icon = Icons.Rounded.CalendarToday, label = "Date", value = exam.examDate, color = colors.textPrimary)
                    ExamDetailRow(icon = Icons.Rounded.AccessTime, label = "Time", value = exam.examTime, color = colors.textPrimary)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    ExamDetailRow(icon = Icons.Rounded.Info, label = "Session", value = exam.sessionDisplay, color = colors.chart2)
                    ExamDetailRow(icon = Icons.Rounded.Schedule, label = "Reporting", value = exam.reportingTime, color = colors.chart4)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    ExamDetailRow(icon = Icons.Rounded.Place, label = "Venue", value = exam.venue, color = colors.chart3)
                    ExamDetailRow(
                        icon = Icons.Rounded.EventSeat,
                        label = "Seat",
                        value = "${exam.seatLocationDisplay} · #${exam.seatNo.ifBlank { "-" }}",
                        color = colors.chart1
                    )
                }
                if (typeLabel.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(AmazeTheme.radius.xs))
                            .background(colors.chart1.copy(alpha = 0.1f))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = typeLabel,
                            style = AmazeTheme.typography.smallLabel.copy(
                                color = colors.chart1,
                                fontWeight = FontWeight.Bold,
                                fontSize = AmazeTheme.fontSize.micro
                            )
                        )
                    }
                }
            }
        }
    }
}

/** Compact banner listing a day's exams — used by Daily Planner and Timetable Grid. */
@Composable
fun ExamDayBanner(
    exams: List<ExamItem>,
    modifier: Modifier = Modifier,
    colors: AmazeColors = AmazeTheme.colors
) {
    if (exams.isEmpty()) return
    val shown = exams.take(2)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AmazeTheme.radius.medium))
            .background(colors.chart1.copy(alpha = 0.08f))
            .border(1.dp, colors.chart1.copy(alpha = 0.35f), RoundedCornerShape(AmazeTheme.radius.medium))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.EventSeat, contentDescription = null, tint = colors.chart1, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(AmazeTheme.spacing.sm))
            Text(
                text = "EXAM DAY",
                style = AmazeTheme.typography.smallLabel.copy(
                    color = colors.chart1,
                    fontWeight = FontWeight.Black,
                    fontSize = AmazeTheme.fontSize.xs
                )
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = "${exams.size} exam${if (exams.size != 1) "s" else ""}",
                style = AmazeTheme.typography.smallLabel.copy(
                    color = colors.chart1,
                    fontWeight = FontWeight.Bold,
                    fontSize = AmazeTheme.fontSize.micro
                )
            )
        }
        shown.forEach { exam ->
            Column {
                Text(
                    text = "${exam.courseCode} · ${exam.courseTitle}",
                    style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = buildString {
                        append(exam.examTime.ifBlank { exam.reportingTime.ifBlank { "Time TBD" } })
                        if (exam.venue.isNotBlank()) append(" · ").append(exam.venue)
                        if (exam.sessionDisplay != "TBD") append(" · Session ").append(exam.sessionDisplay)
                        append(" · Seat ").append(exam.seatLocationDisplay)
                        if (exam.seatNo.isNotBlank()) append(" #").append(exam.seatNo)
                    },
                    style = AmazeTheme.typography.caption.copy(color = colors.textSecondary),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (exams.size > 2) {
            Text(
                text = "+${exams.size - 2} more",
                style = AmazeTheme.typography.smallLabel.copy(
                    color = colors.accent,
                    fontWeight = FontWeight.Bold
                )
            )
        }
    }
}

/** Exams for the semester selected in the Exam Schedule dropdown, fallback to all semesters. */
@Composable
fun rememberSelectedSemesterExams(): List<ExamItem> {
    val academic by AppState.academic.collectAsState()
    val selectedId by AppState.selectedExamSemester.collectAsState()
    return remember(academic, selectedId) {
        val fromSelected = academic.semesters[selectedId]?.exams.orEmpty()
        if (fromSelected.isNotEmpty()) fromSelected
        else academic.semesters.values.flatMap { it.exams }
    }
}

/** Exams mapped by their calendar date (parsed from each exam's examDate). */
internal fun rememberExamDateMap(exams: List<ExamItem>): Map<kotlinx.datetime.LocalDate, List<ExamItem>> =
    exams.groupBy { it.examDateParsed ?: kotlinx.datetime.LocalDate(1970, 1, 1) }.let { map ->
        map.toMap()
    }
