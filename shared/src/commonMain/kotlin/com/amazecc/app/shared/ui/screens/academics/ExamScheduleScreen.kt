package com.amazecc.app.shared.ui.screens.academics

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.amazecc.app.shared.model.ExamItem
import com.amazecc.app.shared.state.AppState
import com.amazecc.app.shared.theme.AmazeColors
import com.amazecc.app.shared.theme.AmazeTheme
import com.amazecc.app.shared.ui.components.BOTTOM_NAV_PADDING
import com.amazecc.app.shared.ui.components.AmazeCard
import com.amazecc.app.shared.ui.components.ExamStatusChip
import com.amazecc.app.shared.ui.components.HeaderSpacer
import com.amazecc.app.shared.ui.components.examStatusText
import com.amazecc.app.shared.ui.components.HeroCard
import com.amazecc.app.shared.ui.components.HeroChip
import com.amazecc.app.shared.ui.components.HeroPanel
import com.amazecc.app.shared.ui.components.HeroStat
import com.amazecc.app.shared.utils.ExamUtils
import com.amazecc.app.shared.utils.seatLocationDisplay
import com.amazecc.app.shared.utils.sessionDisplay
import kotlinx.datetime.Clock

@Composable
fun ExamScheduleScreen() {
    val colors = AmazeTheme.colors
    val semesterMap by AppState.semesterMap.collectAsState()
    val academic by AppState.academic.collectAsState()
    val selectedSemId by AppState.selectedExamSemester.collectAsState()
    val isAppLoading by AppState.isLoading.collectAsState()

    val availableSemesters = remember(academic) {
        val withExams = academic.semesters.filterValues { it.exams.isNotEmpty() }.keys.toList()
        if (withExams.isNotEmpty()) withExams else AppState.semesterIDs
    }

    val selectedExams = academic.semesters[selectedSemId]?.exams.orEmpty()
    val schedule = remember(selectedExams) {
        if (selectedExams.isEmpty()) emptyMap() else mapOf("Exams" to selectedExams)
    }
    val allExamsFlat = schedule.values.flatten()
    val now = Clock.System.now()
    val nextExam = remember(allExamsFlat, now) {
        allExamsFlat
            .mapNotNull { exam -> ExamUtils.hoursUntilExam(exam, now)?.let { exam to it } }
            .filter { it.second >= 0 }
            .minByOrNull { it.second }
            ?.first
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
    ) {

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
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = BOTTOM_NAV_PADDING),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {

            item {
                ExamHeroCard(schedule = schedule, colors = colors)
            }

            schedule.entries.forEachIndexed { typeIndex, (type, exams) ->
                item {
                    ExamGroupCard(
                        type = type,
                        exams = exams,
                        tint = examTypeTint(typeIndex, colors),
                        nextExamCode = nextExam?.courseCode,
                        colors = colors
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(88.dp)) }
        }
    }
}

@Composable
private fun ExamHeroCard(schedule: Map<String, List<ExamItem>>, colors: AmazeColors) {
    val all = schedule.values.flatten()
    if (all.isEmpty()) return

    val now = Clock.System.now()
    val hoursList = all.map { exam -> exam to ExamUtils.hoursUntilExam(exam, now) }
    val total = all.size
    val completed = hoursList.count { (_, h) -> h != null && h < 0 }
    val next = hoursList.filter { (_, h) -> h != null && h!! >= 0 }.minByOrNull { (_, h) -> h!! }
    val fraction by animateFloatAsState(
        targetValue = if (total > 0) completed / total.toFloat() else 0f,
        animationSpec = tween(800)
    )
    val upcoming = total - completed

    HeroCard(colors = colors, modifier = Modifier.fillMaxWidth()) { p ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.EventSeat, null, tint = p.text, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                "Exam Schedule",
                color = p.textSecondary,
                style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold)
            )
            Spacer(Modifier.weight(1f))
            HeroChip(text = "$upcoming UPCOMING", p = p)
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            HeroStat("Total", "$total", p.text)
            HeroStat("Completed", "$completed", p.text)
            next?.let { (_, hours) ->
                HeroStat("Next in", countdownShort(hours), p.text)
            }
        }

        LinearProgressIndicator(
            progress = { fraction },
            modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(AmazeTheme.radius.xs)),
            color = p.progress,
            trackColor = p.progressTrack
        )

        next?.let { (exam, _) ->
            HeroPanel(p = p, modifier = Modifier.fillMaxWidth()) {
                Text(
                    "NEXT EXAM",
                    color = p.textSecondary,
                    fontWeight = FontWeight.Bold,
                    fontSize = AmazeTheme.fontSize.sm
                )
                Text(
                    "${exam.courseCode} · ${exam.courseTitle}",
                    color = p.text,
                    fontWeight = FontWeight.Bold,
                    fontSize = AmazeTheme.fontSize.sm,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    buildString {
                        append(exam.examDate.ifBlank { "Date TBD" })
                        if (exam.examTime.isNotBlank()) append(" · ").append(exam.examTime)
                        if (exam.sessionDisplay != "TBD") append(" · ").append(exam.sessionDisplay)
                    },
                    color = p.textSecondary,
                    fontSize = AmazeTheme.fontSize.micro
                )
            }
        }
    }
}

@Composable
private fun ExamGroupCard(
    type: String,
    exams: List<ExamItem>,
    tint: Color,
    nextExamCode: String?,
    colors: AmazeColors
) {
    val sorted = remember(exams) { ExamUtils.sortedExamDays(exams) }

    AmazeCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(0.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier.size(36.dp).clip(CircleShape).background(tint.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.AutoMirrored.Rounded.MenuBook, null, tint = tint, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(AmazeTheme.spacing.sm))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        type.uppercase(),
                        style = AmazeTheme.typography.subheading.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        "${exams.size} exam${if (exams.size != 1) "s" else ""}",
                        style = AmazeTheme.typography.caption.copy(color = colors.textMuted)
                    )
                }
            }

            sorted.forEachIndexed { index, exam ->
                if (index > 0) {
                    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(colors.border.copy(alpha = 0.5f)))
                }
                ExamRow(
                    exam = exam,
                    tint = tint,
                    isNext = exam.courseCode == nextExamCode,
                    colors = colors
                )
            }
        }
    }
}

@Composable
private fun ExamRow(exam: ExamItem, tint: Color, isNext: Boolean, colors: AmazeColors) {
    var expanded by remember { mutableStateOf(false) }
    val hours = remember(exam) { ExamUtils.hoursUntilExam(exam) }
    val status = examStatusText(exam)
    val iconTint = when {
        status == "TODAY" -> colors.success
        status == "PAST" -> colors.textMuted
        else -> tint
    }
    val countdown = countdownText(hours)
    val countdownColor = if (hours != null && hours in 0.0..24.0) colors.success else colors.textMuted

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded }
            .background(if (isNext && status != "PAST") colors.accent.copy(alpha = 0.05f) else Color.Transparent)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(40.dp).clip(CircleShape).background(iconTint.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.AutoMirrored.Rounded.MenuBook, null, tint = iconTint, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(AmazeTheme.spacing.sm))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        exam.courseCode,
                        style = AmazeTheme.typography.smallLabel.copy(color = iconTint, fontWeight = FontWeight.Bold)
                    )
                    if (exam.slot.isNotBlank() && exam.slot != "-") {
                        Spacer(Modifier.width(6.dp))
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
                if (!countdown.isNullOrBlank()) {
                    Text(
                        countdown,
                        style = AmazeTheme.typography.caption.copy(color = countdownColor, fontWeight = FontWeight.SemiBold)
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            ExamStatusChip(
                text = status,
                color = if (status == "TODAY") colors.success else if (status == "PAST") colors.textMuted else colors.chart1
            )
            Spacer(Modifier.width(6.dp))
            Icon(
                if (expanded) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
                null,
                tint = colors.textMuted,
                modifier = Modifier.size(20.dp)
            )
        }

        AnimatedVisibility(visible = expanded) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MetricTile("Date", exam.examDate, colors, Modifier.weight(1f))
                    MetricTile("Time", exam.examTime, colors, Modifier.weight(1f))
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MetricTile("Session", exam.sessionDisplay, colors, Modifier.weight(1f))
                    MetricTile("Reporting", exam.reportingTime, colors, Modifier.weight(1f))
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MetricTile("Venue", exam.venue, colors, Modifier.weight(1f))
                    MetricTile(
                        "Seat",
                        buildString {
                            append(exam.seatLocationDisplay)
                            if (exam.seatNo.isNotBlank()) append(" · #").append(exam.seatNo)
                        },
                        colors,
                        Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun MetricTile(label: String, value: String, colors: AmazeColors, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(AmazeTheme.radius.small))
            .background(colors.surface)
            .border(1.dp, colors.border.copy(alpha = 0.5f), RoundedCornerShape(AmazeTheme.radius.small))
            .padding(10.dp)
    ) {
        Column {
            Text(label, style = AmazeTheme.typography.smallLabel.copy(color = colors.textMuted, fontSize = AmazeTheme.fontSize.micro))
            Spacer(Modifier.height(2.dp))
            Text(
                value.ifBlank { "TBD" },
                style = AmazeTheme.typography.body.copy(fontWeight = FontWeight.Bold, color = colors.textPrimary, fontSize = AmazeTheme.fontSize.base),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun examTypeTint(index: Int, colors: AmazeColors): Color = when (index % 6) {
    0 -> colors.chart1
    1 -> colors.chart2
    2 -> colors.chart3
    3 -> colors.chart4
    4 -> colors.success
    else -> colors.warning
}

private fun countdownText(hours: Double?): String? {
    if (hours == null) return null
    if (hours < 0) return "Completed"
    val h = hours.toInt()
    return when {
        h < 1 -> "Starting in ${(hours * 60).toInt()}m"
        h < 24 -> "Starts in ${h}h"
        else -> "Starts in ${h / 24}d ${h % 24}h"
    }
}

private fun countdownShort(hours: Double?): String {
    if (hours == null) return "—"
    if (hours < 0) return "Done"
    val h = hours.toInt()
    return when {
        h < 1 -> "${(hours * 60).toInt()}m"
        h < 24 -> "${h}h"
        else -> "${h / 24}d"
    }
}
