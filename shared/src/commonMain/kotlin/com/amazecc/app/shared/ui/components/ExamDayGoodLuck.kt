package com.amazecc.app.shared.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.EventSeat
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amazecc.app.shared.model.ExamItem
import com.amazecc.app.shared.theme.AmazeTheme
import com.amazecc.app.shared.utils.ExamUtils
import com.amazecc.app.shared.utils.examDateParsed
import com.amazecc.app.shared.utils.seatLocationDisplay
import com.amazecc.app.shared.utils.sessionDisplay
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

/**
 * Full-page "Good Luck!" state for an exam day — replaces the class timetable.
 * Shows every exam of the day with countdown + seating details, plus an option
 * to preview the (exam-suppressed) timetable underneath.
 */
@Composable
fun ExamDayGoodLuck(
    exams: List<ExamItem>,
    onToggleTimetable: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = AmazeTheme.colors

    var now by remember { mutableStateOf(Clock.System.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(60_000L)
            now = Clock.System.now()
        }
    }

    val sorted = remember(exams) {
        exams.sortedBy { ExamUtils.examStartMinutes(it) ?: 0 }
    }
    val allDone = remember(sorted, now) {
        sorted.isNotEmpty() && sorted.all { isExamFinished(it, now) }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Hero
        HeroCard(colors = colors, modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(24.dp), spacing = 0.dp) { p ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(p.iconBg),
                    contentAlignment = Alignment.Center
                ) {
                    Text("🎉", fontSize = 30.sp)
                }
                Spacer(Modifier.height(AmazeTheme.spacing.md))
                Text(
                    if (allDone) "Great Job!" else "Good Luck!",
                    color = p.text,
                    fontWeight = FontWeight.Black,
                    fontSize = AmazeTheme.fontSize.x2l
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    if (allDone) {
                        "You've completed all ${sorted.size} exam${if (sorted.size != 1) "s" else ""} today. Hope it went well!"
                    } else {
                        "You have ${sorted.size} exam${if (sorted.size != 1) "s" else ""} today. You've got this!"
                    },
                    color = p.textSecondary,
                    style = AmazeTheme.typography.body,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(AmazeTheme.spacing.md))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(AmazeTheme.radius.small))
                        .background(p.chipBg)
                        .border(1.dp, p.chipBorder, RoundedCornerShape(AmazeTheme.radius.small))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.CalendarToday, null, tint = p.text, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            examDayLabel(sorted.first().examDate),
                            color = p.text,
                            fontWeight = FontWeight.Bold,
                            fontSize = AmazeTheme.fontSize.sm
                        )
                    }
                }
            }
        }

        // Exam cards
        sorted.forEachIndexed { index, exam ->
            ExamGoodLuckCard(exam = exam, now = now, isLast = index == sorted.lastIndex)
        }

        // Preview timetable toggle
        AmazeButton(
            text = "Preview Timetable",
            icon = Icons.Rounded.Visibility,
            onClick = onToggleTimetable,
            variant = ButtonVariant.SECONDARY,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(BOTTOM_NAV_PADDING))
    }
}

@Composable
private fun ExamGoodLuckCard(exam: ExamItem, now: Instant, isLast: Boolean) {
    val colors = AmazeTheme.colors
    val status = remember(exam, now) { examGoodLuckStatus(exam, now, colors) }

    AmazeCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(AmazeTheme.radius.small))
                        .background(colors.chart1.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.School, null, tint = colors.chart1, modifier = Modifier.size(22.dp))
                }
                Spacer(modifier = Modifier.width(AmazeTheme.spacing.sm))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            exam.courseCode,
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
                                    exam.slot,
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
                        exam.courseTitle,
                        style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                ExamStatusChip(text = status.first, color = status.second)
            }

            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(colors.border.copy(alpha = 0.5f)))

            ExamDetailRow(icon = Icons.Rounded.AccessTime, label = "Time", value = exam.examTime, color = colors.textPrimary)
            ExamDetailRow(icon = Icons.Rounded.Schedule, label = "Reporting", value = exam.reportingTime, color = colors.chart4)
            ExamDetailRow(icon = Icons.Rounded.Schedule, label = "Session", value = exam.sessionDisplay, color = colors.chart2)
            ExamDetailRow(icon = Icons.Rounded.Place, label = "Venue", value = exam.venue, color = colors.chart3)
            ExamDetailRow(
                icon = Icons.Rounded.EventSeat,
                label = "Seat",
                value = "${exam.seatLocationDisplay} · #${exam.seatNo.ifBlank { "-" }}",
                color = colors.chart1
            )
        }
    }
}

private fun examDayLabel(rawDate: String): String {
    val date = com.amazecc.app.shared.utils.ExamUtils.parseExamDateToLocalDate(rawDate) ?: return rawDate
    val day = when (date.dayOfWeek) {
        kotlinx.datetime.DayOfWeek.MONDAY -> "Mon"; kotlinx.datetime.DayOfWeek.TUESDAY -> "Tue"
        kotlinx.datetime.DayOfWeek.WEDNESDAY -> "Wed"; kotlinx.datetime.DayOfWeek.THURSDAY -> "Thu"
        kotlinx.datetime.DayOfWeek.FRIDAY -> "Fri"; kotlinx.datetime.DayOfWeek.SATURDAY -> "Sat"
        else -> "Sun"
    }
    val month = when (date.monthNumber) {
        1 -> "Jan"; 2 -> "Feb"; 3 -> "Mar"; 4 -> "Apr"; 5 -> "May"; 6 -> "Jun"
        7 -> "Jul"; 8 -> "Aug"; 9 -> "Sep"; 10 -> "Oct"; 11 -> "Nov"; else -> "Dec"
    }
    return "$day, ${date.dayOfMonth} $month ${date.year}"
}

private fun examEndInstant(exam: ExamItem): Instant? {
    val date = exam.examDateParsed ?: return null
    val range = ExamUtils.parseExamTimeRange(exam.examTime) ?: return null
    return try {
        LocalDateTime(date.year, date.monthNumber, date.dayOfMonth, range.second / 60, range.second % 60, 0, 0)
            .toInstant(TimeZone.currentSystemDefault())
    } catch (_: Exception) {
        null
    }
}

private fun isExamFinished(exam: ExamItem, now: Instant): Boolean {
    val end = examEndInstant(exam) ?: return false
    return now >= end
}

/** Pair(label, color): "Starts in 2h 15m" / "In Progress" / "Done". */
private fun examGoodLuckStatus(exam: ExamItem, now: Instant, colors: com.amazecc.app.shared.theme.AmazeColors): Pair<String, Color> {
    val start = ExamUtils.examStartInstant(exam) ?: return "" to colors.textMuted
    val end = examEndInstant(exam)

    return when {
        end != null && now >= end -> "Done" to colors.success
        now < start -> {
            val minutes = ((start - now).inWholeMilliseconds / 60_000L).toInt()
            val label = if (minutes >= 60) "In ${minutes / 60}h ${minutes % 60}m" else "In ${minutes}m"
            label to colors.chart1
        }
        else -> "In Progress" to colors.accent
    }
}
