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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.EventAvailable
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.amazecc.app.shared.domain.ExamSeries as DomainExamSeries
import com.amazecc.app.shared.domain.overhaul.ExamSeries
import com.amazecc.app.shared.domain.overhaul.ExamStatus
import com.amazecc.app.shared.domain.overhaul.M3EExamEntry
import com.amazecc.app.shared.domain.overhaul.M3EExamScheduleAction
import com.amazecc.app.shared.domain.overhaul.M3EExamScheduleState
import com.amazecc.app.shared.domain.overhaul.M3EExamSeriesGroup
import com.amazecc.app.shared.model.ExamScheduleRes
import com.amazecc.app.shared.ui.m3e.components.M3EConnectedButtonGroup
import com.amazecc.app.shared.ui.m3e.components.M3ESurfaceCard
import com.amazecc.app.shared.ui.m3e.components.M3EWavyLinearProgress
import com.amazecc.app.shared.ui.m3e.components.SurfaceContainerLevel
import com.amazecc.app.shared.ui.m3e.theme.AmazeM3ETheme
import com.amazecc.app.shared.ui.m3e.theme.M3ETheme
import com.amazecc.app.shared.utils.ExamUtils
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * Material 3 Expressive Exam Schedule & Seating hub.
 * 100% stateless: accepts [M3EExamScheduleState] and emits [M3EExamScheduleAction].
 *
 * One hero (the next-exam countdown card), a data-driven series filter, a grouped timeline with
 * semantic status tones, and explicit loading / empty / error states. Layout spacing comes from
 * `LazyColumn` — content inside [M3ESurfaceCard] never re-adds the card's own 16.dp padding.
 */
@Composable
fun M3EExamScheduleScreen(
    state: M3EExamScheduleState,
    onAction: (M3EExamScheduleAction) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = M3ETheme.colors
    // Legacy hosts (the bridge) do not populate groupedEntries yet — derive the same groups here
    // so their rows keep rendering until App.kt points at M3EExamScheduleHost.
    val groups = state.groupedEntries.ifEmpty {
        M3EExamScheduleComputations.groupBySeries(state.filteredExams)
    }

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
            // ── Header ────────────────────────────────────────────────────────────────
            item(key = "exam_header") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "Exam Schedules",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = colors.onSurface
                        )
                        Text(
                            text = state.semesterName,
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceVariant
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        IconButton(
                            onClick = { onAction(M3EExamScheduleAction.Refresh) },
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(colors.surfaceContainerHigh)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Refresh,
                                contentDescription = "Refresh exam schedule",
                                tint = colors.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
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
                                tint = colors.onPrimaryContainer,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }

            // ── Loading / error ───────────────────────────────────────────────────────
            if (state.isLoading) {
                item(key = "exam_loading") {
                    M3ESurfaceCard(
                        containerLevel = SurfaceContainerLevel.LOW,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(
                                text = "Syncing exam schedule…",
                                style = MaterialTheme.typography.titleMedium,
                                color = colors.onSurface
                            )
                            // M3E's signature wavy determinate indicator. No real fraction exists
                            // during a fetch, so this is a gentle moving sweep, not a false percent.
                            M3EWavyLinearProgress(progress = 0.35f)
                            Text(
                                text = "Pulling the latest CAT / FAT timetable from VTOP.",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.onSurfaceVariant
                            )
                        }
                    }
                }
            } else if (state.errorMessage != null) {
                item(key = "exam_error") {
                    M3ESurfaceCard(
                        containerLevel = SurfaceContainerLevel.LOW,
                        borderColor = colors.error,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.ErrorOutline,
                                    contentDescription = null,
                                    tint = colors.error,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = state.errorMessage.orEmpty(),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = colors.error
                                )
                            }
                            Button(onClick = { onAction(M3EExamScheduleAction.Refresh) }) {
                                Text("Try again")
                            }
                        }
                    }
                }
            } else {
                // ── One hero: next exam countdown ────────────────────────────────────
                state.nextUpcomingExam?.let { nextExam ->
                    item(key = "next_exam_hero") {
                        M3EExamNextExamHero(
                            exam = nextExam,
                            onClick = { onAction(M3EExamScheduleAction.ExamClicked(nextExam)) }
                        )
                    }
                }

                // ── Calm status summary ─────────────────────────────────────────────
                item(key = "exam_summary") {
                    M3EExamStatusSummary(counts = state.statusCounts)
                }

                // ── Data-driven series filter ───────────────────────────────────────
                if (state.availableSeries.size > 1) {
                    item(key = "series_selector") {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "Exam series",
                                style = MaterialTheme.typography.labelMedium,
                                color = colors.onSurfaceVariant
                            )
                            M3EConnectedButtonGroup(
                                items = state.availableSeries,
                                selectedItem = state.selectedSeries,
                                onItemSelected = { onAction(M3EExamScheduleAction.SelectSeries(it)) },
                                labelProvider = { it.label }
                            )
                        }
                    }
                }

                // ── Grouped timeline ────────────────────────────────────────────────
                if (state.filteredExams.isEmpty()) {
                    item(key = "empty_exams") {
                        M3ESurfaceCard(
                            containerLevel = SurfaceContainerLevel.LOW,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
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
                                    style = MaterialTheme.typography.titleMedium,
                                    color = colors.onSurface
                                )
                                Text(
                                    text = "CAT, FAT and lab timetables appear here once VTOP syncs them.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.onSurfaceVariant
                                )
                            }
                        }
                    }
                } else {
                    groups.forEach { group ->
                        item(key = "exam_group_${group.series.name}") {
                            M3EExamSeriesSection(
                                group = group,
                                hallTicketUrl = state.hallTicketUrl,
                                onAction = onAction
                            )
                        }
                    }
                }
            }

            // Bottom spacing (twice nav bar height: 140dp)
            item(key = "bottom_spacer") {
                Spacer(Modifier.height(140.dp))
            }
        }
    }
}

/** Semantic status colour, always from the M3E palette — never a hard-coded hex. */
@Composable
private fun statusColor(status: ExamStatus): Color = when (status) {
    ExamStatus.UPCOMING -> M3ETheme.colors.success
    ExamStatus.TODAY -> M3ETheme.colors.warning
    ExamStatus.COMPLETED -> M3ETheme.colors.onSurfaceVariant
}

@Composable
private fun seriesColor(series: ExamSeries): Color = when (series) {
    ExamSeries.FAT -> M3ETheme.colors.tertiary
    ExamSeries.CAT1, ExamSeries.CAT2 -> M3ETheme.colors.secondary
    ExamSeries.ALL -> M3ETheme.colors.primary
}

/** Legacy-safe countdown: prefer the computed label, fall back to whole days for old states. */
private fun countdownFor(exam: M3EExamEntry): String {
    if (exam.countdownLabel.isNotBlank()) return exam.countdownLabel
    if (exam.status == ExamStatus.COMPLETED) return "Completed"
    return if (exam.daysRemaining == 0) "Today" else "In ${exam.daysRemaining} days"
}

/** Venue + seat on one line; the derived hall location is shown only when it exists. */
private fun venueSeatFor(exam: M3EExamEntry): String {
    val venue = exam.venueDisplay.ifBlank { exam.venue }
    val location = exam.seatLocation
    val seat = if (location.isBlank()) exam.seatNumber else "${exam.seatNumber} ($location)"
    return "$venue | Seat: $seat"
}

@Composable
private fun M3EExamNextExamHero(
    exam: M3EExamEntry,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = M3ETheme.colors
    M3ESurfaceCard(
        containerLevel = SurfaceContainerLevel.HIGH,
        cornerRadius = 28.dp,
        modifier = modifier.fillMaxWidth(),
        onClick = onClick
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(colors.primaryContainer)
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "UPCOMING EXAM",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = colors.onPrimaryContainer
                    )
                }
                Text(
                    text = countdownFor(exam),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = statusColor(exam.status)
                )
            }

            Text(
                text = "${exam.courseCode} • ${exam.courseTitle}",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = colors.onSurface
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Rounded.LocationOn,
                    contentDescription = null,
                    tint = colors.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = venueSeatFor(exam),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.onSurface
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Rounded.AccessTime,
                    contentDescription = null,
                    tint = colors.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = exam.examTime.ifBlank { "Time TBA" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun M3EExamStatusSummary(counts: com.amazecc.app.shared.domain.overhaul.M3EExamStatusCounts) {
    val colors = M3ETheme.colors
    M3ESurfaceCard(
        containerLevel = SurfaceContainerLevel.LOW,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            SummaryCell("Upcoming", counts.upcoming, statusColor(ExamStatus.UPCOMING))
            SummaryCell("Today", counts.today, statusColor(ExamStatus.TODAY))
            SummaryCell("Completed", counts.completed, statusColor(ExamStatus.COMPLETED))
        }
    }
}

@Composable
private fun SummaryCell(label: String, count: Int, color: Color) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = M3ETheme.colors.onSurfaceVariant
        )
    }
}

@Composable
private fun M3EExamSeriesSection(
    group: M3EExamSeriesGroup,
    hallTicketUrl: String?,
    onAction: (M3EExamScheduleAction) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = M3ETheme.colors
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
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
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(seriesColor(group.series))
                )
                Text(
                    text = group.label,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = colors.onSurface
                )
            }
            Text(
                text = "${group.entries.size} papers",
                style = MaterialTheme.typography.labelMedium,
                color = colors.onSurfaceVariant
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            group.entries.forEach { exam ->
                M3EExamRowCard(
                    exam = exam,
                    hallTicketUrl = hallTicketUrl,
                    onClick = { onAction(M3EExamScheduleAction.ExamClicked(exam)) },
                    onOpenHallTicket = { url -> onAction(M3EExamScheduleAction.OpenHallTicket(url)) }
                )
            }
        }
    }
}

@Composable
private fun M3EExamRowCard(
    exam: M3EExamEntry,
    hallTicketUrl: String?,
    onClick: () -> Unit,
    onOpenHallTicket: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = M3ETheme.colors
    val tone = statusColor(exam.status)

    M3ESurfaceCard(
        containerLevel = SurfaceContainerLevel.LOW,
        cornerRadius = 20.dp,
        onClick = onClick,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // Course identity + semantic status badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = exam.courseCode,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = colors.primary
                    )
                    Text(
                        text = exam.courseTitle,
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.onSurface
                    )
                }
                Spacer(Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(tone.copy(alpha = 0.16f))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = exam.status.label,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = tone
                    )
                }
            }

            // Date / session / slot banner
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(colors.primaryContainer)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "${exam.date} • ${exam.session}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = colors.onPrimaryContainer
                    )
                }
                Text(
                    text = "Slot ${exam.slot}",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = colors.primary
                )
            }

            // Detail rows
            M3EExamDetailRow(Icons.Rounded.AccessTime, "Reporting", exam.reportingTime.ifBlank { "TBA" })
            M3EExamDetailRow(Icons.Rounded.AccessTime, "Exam time", exam.examTime.ifBlank { "TBA" })
            M3EExamDetailRow(
                Icons.Rounded.LocationOn,
                "Venue",
                exam.venueDisplay.ifBlank { exam.venue }.ifBlank { "TBA" }
            )
            M3EExamDetailRow(
                Icons.Rounded.CalendarMonth,
                "Seat",
                if (exam.seatLocation.isBlank()) exam.seatNumber.ifBlank { "TBA" }
                else "${exam.seatNumber.ifBlank { "TBA" }} · ${exam.seatLocation}"
            )

            // Hall ticket — only when the payload actually exposes a link.
            if (hallTicketUrl != null) {
                Button(onClick = { onOpenHallTicket(hallTicketUrl) }) {
                    Icon(
                        imageVector = Icons.Rounded.OpenInNew,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("Hall ticket")
                }
            }
        }
    }
}

@Composable
private fun M3EExamDetailRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String) {
    val colors = M3ETheme.colors
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = colors.onSurfaceVariant,
            modifier = Modifier.size(16.dp)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = colors.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = colors.onSurface
        )
    }
}

/**
 * Map one raw VTOP exam-series key (the key of `ExamScheduleRes.schedule`, e.g. `"CAT II"`,
 * `"CAT2"`, `"FAT"`, `"End Semester"`) onto the M3E filter enum. Uses the app's shared
 * canonicaliser so the many spellings VTOP emits all land on the same bucket.
 *
 * Unknown spellings deliberately map to [ExamSeries.ALL] rather than guessing FAT: a paper whose
 * series we could not read must not silently appear under a specific filter it may not belong to.
 */
internal fun examSeriesFor(rawType: String): ExamSeries {
    val canonical = DomainExamSeries.canonicalSeriesName(rawType)
    return when {
        canonical.contains("cat2") -> ExamSeries.CAT2
        canonical.contains("cat1") -> ExamSeries.CAT1
        // VTOP emits "MT" as a real exam-type key (mid-term); keep it out of ALL.
        canonical.contains("midsem") || canonical.contains("midterm") || canonical == "mt" ||
            rawType.trim().equals("MT", ignoreCase = true) -> ExamSeries.CAT2
        canonical.contains("fat") || canonical.contains("final") || canonical.contains("end") -> ExamSeries.FAT
        canonical.contains("cat") -> ExamSeries.CAT1
        else -> ExamSeries.ALL
    }
}

/**
 * Pure, headless-testable mapping of the real `ExamScheduleRes` payload into stateless screen rows.
 *
 * Kept verbatim for the legacy bridge and its wiring tests. The new host uses the richer
 * [M3EExamScheduleComputations.buildEntries] instead, which adds status/countdown/seat fields.
 */
internal fun mapExamScheduleResToEntries(
    res: ExamScheduleRes,
    now: Instant = Clock.System.now()
): List<M3EExamEntry> {
    val today = now.toLocalDateTime(TimeZone.currentSystemDefault()).date
    return res.schedule.entries
        .flatMap { (seriesKey, items) ->
            val series = examSeriesFor(seriesKey)
            items.mapIndexed { idx, item ->
                val parsed = ExamUtils.parseExamDateToLocalDate(item.examDate)
                val dayName = parsed?.dayOfWeek?.name
                    ?.lowercase()
                    ?.replaceFirstChar { it.uppercase() }
                    .orEmpty()
                val daysRemaining = parsed?.let {
                    maxOf(0, it.toEpochDays() - today.toEpochDays())
                } ?: 0
                val seat = listOf(item.seatNo, item.seatLocation)
                    .filter { it.isNotBlank() }
                    .joinToString(" / ")
                M3EExamEntry(
                    id = "$seriesKey-${item.courseCode}-${item.examDate}-$idx",
                    courseCode = item.courseCode,
                    courseTitle = item.courseTitle,
                    examSeries = series,
                    date = item.examDate,
                    day = dayName,
                    session = item.examSession,
                    slot = item.slot,
                    reportingTime = item.reportingTime,
                    examTime = item.examTime,
                    venue = item.venue,
                    seatNumber = seat,
                    daysRemaining = daysRemaining,
                    isHappeningSoon = daysRemaining <= 3
                )
            }
        }
        .sortedBy { ExamUtils.parseExamDateToLocalDate(it.date) ?: LocalDate(9999, 12, 31) }
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
